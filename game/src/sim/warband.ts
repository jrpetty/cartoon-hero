// "Warband Tactics" — the auto-battler run engine (logic only; the screen draws
// it). A run is: shop for units → they auto-merge into star-ups → fight an
// opponent's warband (resolved by autobattle.resolveBattle) → lose life if you
// lose → last warband standing wins. Deterministic via a seeded RNG so it's
// testable and replay-safe.

import { RNG } from "../engine/rng";
import { UNITS } from "../content/units";
import { resolveBattle, UnitStack, ArenaUnit, BattleResult, SideOpts } from "./autobattle";
import { activeTraits, ActiveTrait, Buff } from "./traits";
import { COMPONENT_IDS, MAX_ITEMS, fuseComponents } from "./items";
import { Augment, offerAugments, tierForRound, combinedBuff, combinedTraitBonus } from "./augments";
import { CreepCamp, isCreepRound, campForRound, campBoard } from "./creeps";
import { WarbandCommander, warbandCommander, offerCommanders } from "./warband_commanders";
import { CarouselPick, isDraftRound, rollCarousel, pickValue } from "./carousel";
import { UNIT_TIER, TIER_UNITS } from "./unit_tiers";
import { styleOf } from "../content/battle_styles";
import { Condition, rollCondition, CLEAR } from "./conditions";

export { UNIT_TIER } from "./unit_tiers"; // re-exported: the screen and tests import it from here


/** Copies of each unit type in the shared lobby pool, by tier (1..5). Cheaper
 *  units are plentiful; carries are scarce — so contesting a comp is real. */
const POOL_SIZE = [0, 29, 22, 16, 12, 10];

/** Per-shop appearance odds (%) of each tier, indexed by player level (1..9). */
const TIER_ODDS: number[][] = [
  [100, 0, 0, 0, 0],
  [100, 0, 0, 0, 0],
  [70, 30, 0, 0, 0],
  [55, 30, 15, 0, 0],
  [45, 33, 20, 2, 0],
  [35, 35, 25, 5, 0],
  [22, 33, 33, 10, 2],
  [16, 24, 35, 20, 5],
  [10, 16, 30, 30, 14],
];
const XP_TO_LEVEL = [0, 0, 2, 6, 12, 22, 38, 60, 90]; // cumulative xp needed for level i (1..9)
const SHOP_SIZE = 5;
const REROLL_COST = 2;
const XP_BUY = 4; // gold → +4 xp
const MAX_LEVEL = 9;

export interface Piece { type: string; star: number; items: string[]; col?: number; row?: number; deployed?: boolean; } // on the board if deployed
export interface Opponent { id: number; name: string; life: number; alive: boolean; }

// Default fill order for auto-placing un-positioned units: front rank first
// (the column nearest the centre line), rows from the middle outward.
const ROW_ORDER = [4, 5, 3, 6, 2, 7, 1, 8, 0, 9];
const PLAYER_CELLS: { c: number; r: number }[] = [];
for (const c of [4, 3, 2, 1, 0]) for (const r of ROW_ORDER) PLAYER_CELLS.push({ c, r });
const ENEMY_CELLS: { c: number; r: number }[] = [];
for (const c of [5, 6, 7, 8, 9]) for (const r of ROW_ORDER) ENEMY_CELLS.push({ c, r });

/** An AI opponent's hidden economy — identical rules to the player's. */
type FoePlay = "economist" | "aggressor" | "roller";
interface FoeBrain {
  gold: number; level: number; xp: number; pieces: Piece[]; rng: RNG; favored: string[];
  /** How this warband plays: bank for interest, rush levels, or roll for stars. */
  play: FoePlay;
}

const NAMES = ["Ironclad", "Crimson", "Verdant", "Stormcrow", "Ashveil", "Goldhand", "Nightfall"];

/** An opponent's "comp identity": a few core types it drafts toward, so its
 *  board concentrates into star-ups and synergies instead of a random spread. */
function pickFavored(rng: RNG): string[] {
  const out: string[] = [];
  const pick = (tier: number) => { const pool = TIER_UNITS[tier]; if (pool.length) out.push(pool[rng.int(0, pool.length - 1)]); };
  pick(1); pick(1); pick(2); pick(3); // two cheap cores to spam-merge + a couple of carries
  return [...new Set(out)];
}

/** Combine any 3 same type+star into one of the next star (shared by all
 *  warbands). Returns each star-up made, so the screen can celebrate it. */
function mergeBoard(pieces: Piece[], stash: string[]): { type: string; star: number }[] {
  const made: { type: string; star: number }[] = [];
  for (let star = 1; star <= 2; star++) {
    let changed = true;
    while (changed) {
      changed = false;
      const counts: Record<string, number> = {};
      for (const p of pieces) if (p.star === star) counts[p.type] = (counts[p.type] ?? 0) + 1;
      for (const [type, n] of Object.entries(counts)) {
        if (n >= 3) {
          let removed = 0;
          const carried: string[] = [];
          for (let i = pieces.length - 1; i >= 0 && removed < 3; i--) {
            if (pieces[i].type === type && pieces[i].star === star) {
              carried.push(...pieces[i].items); // the upgrade inherits the components' relics
              pieces.splice(i, 1);
              removed++;
            }
          }
          pieces.push({ type, star: star + 1, items: carried.slice(0, MAX_ITEMS) });
          stash.push(...carried.slice(MAX_ITEMS)); // overflow relics go back to the stash
          made.push({ type, star: star + 1 });
          changed = true;
        }
      }
    }
  }
  return made;
}

/**
 * Life lost when a warband is beaten, TFT-style: a flat toll that grows with
 * the stage, plus one point per surviving enemy unit weighted by its star
 * (a 3★ hurts three times as much as a 1★).
 *
 * The stage toll is what stops the early game from being decided by a couple of
 * unlucky rounds, and what stops the late game from dragging.
 */
const STAGE_DAMAGE = [0, 0, 2, 5, 8, 11, 15, 20];
export function stageOf(round: number): number { return Math.floor((round - 1) / 5) + 1; }
export function stageBaseDamage(stage: number): number {
  return STAGE_DAMAGE[Math.max(0, Math.min(stage, STAGE_DAMAGE.length - 1))];
}
/** Total life a defeat costs, before any commander shield. */
export function roundDamage(round: number, survivorPower: number): number {
  return stageBaseDamage(stageOf(round)) + survivorPower;
}

/**
 * Gold paid for a run of wins *or* losses. Losing streaks pay too — that's the
 * comeback lever: a warband being beaten every round is quietly banking for a
 * level spike, so the lobby doesn't simply run away from whoever falls behind.
 */
const STREAK_GOLD = [0, 0, 1, 1, 2, 3];
export function streakBonus(streak: number): number {
  return STREAK_GOLD[Math.min(Math.abs(streak), STREAK_GOLD.length - 1)];
}

/** Bench slots — reserves beyond your deployed board, exactly like TFT's nine. */
export const BENCH_SLOTS = 9;

/**
 * Lay a warband out by what each unit *does* in a fight rather than by how
 * strong it is. Vanguards and Line troops take the front rank, Flankers sit
 * just behind them on the outside rows, Artillery goes to the very back, and
 * Infiltrators start at the back too (they leap regardless, so the back rank is
 * the safest place to stage them).
 *
 * `side` is +1 for the enemy half (cols 5..9) and −1 for the player's (0..4).
 * A board that puts a trebuchet on the front line is the single most obvious
 * tell of a dumb opponent, and this is what removes it.
 */
export function placeByStyle(pieces: Piece[], side: 1 | -1): ArenaUnit[] {
  // Depth 0 = closest to the centre line, 4 = furthest back.
  const depthFor: Record<string, number> = {
    vanguard: 0, line: 1, flanker: 1, artillery: 3, infiltrator: 4,
  };
  // Rows fill from the middle outward, except Flankers which want the edges.
  const midRows = [4, 5, 3, 6, 2, 7, 1, 8, 0, 9];
  const edgeRows = [0, 9, 1, 8, 2, 7, 3, 6, 4, 5];
  const used = new Set<string>();
  const out: ArenaUnit[] = [];
  // Front ranks first so the wall is placed before anything behind it.
  const ordered = [...pieces].sort((p, q) => {
    const dp = depthFor[styleOf(p.type)] ?? 1;
    const dq = depthFor[styleOf(q.type)] ?? 1;
    return dp - dq || q.star - p.star;
  });
  for (const p of ordered) {
    const style = styleOf(p.type);
    const wantDepth = depthFor[style] ?? 1;
    const rows = style === "flanker" ? edgeRows : midRows;
    let placed = false;
    // Try the preferred depth, then step backwards, then forwards.
    const depths = [wantDepth];
    for (let d = 1; d < 5; d++) {
      if (wantDepth + d < 5) depths.push(wantDepth + d);
      if (wantDepth - d >= 0) depths.push(wantDepth - d);
    }
    for (const d of depths) {
      const col = side === 1 ? 5 + d : 4 - d;
      for (const row of rows) {
        const key = `${col},${row}`;
        if (used.has(key)) continue;
        used.add(key);
        out.push({ type: p.type, star: p.star, items: p.items, col, row });
        placed = true;
        break;
      }
      if (placed) break;
    }
  }
  return out;
}

export class WarbandRun {
  private rng: RNG;
  round = 0;
  gold = 2;
  level = 1;
  xp = 0;
  streak = 0; // +win streak / −loss streak
  life = 100;
  pieces: Piece[] = []; // bench + board combined; the top `level` deploy
  itemStash: string[] = []; // unequipped relics
  shop: (string | null)[] = [];
  opponents: Opponent[] = [];
  phase: "commander" | "augment" | "draft" | "shop" | "battle" | "result" | "over" = "shop";
  outcome: "win" | "loss" | null = null;
  lastResult: { won: boolean; foe: string; youLeft: number; foeLeft: number; dmg: number; relics: number; gold: number; creep: boolean } | null = null;
  // ---- augments (TFT-style run-defining picks) ----
  augments: Augment[] = [];
  augmentOffer: Augment[] = [];
  // ---- the commander leading this warband ----
  commander: WarbandCommander | null = null;
  commanderOffer: WarbandCommander[] = [];
  private freeRerolls = 0; // reset each round by the commander's perk
  // ---- the round's PvE camp, when this is a monster round ----
  pendingCamp: CreepCamp | null = null;
  /** The weather and ground this round is fought on — it hits both warbands. */
  condition: Condition = CLEAR;
  // ---- the shared draft, on carousel rounds ----
  /** What's still on the ring when your turn comes (rivals pick before you). */
  carousel: CarouselPick[] = [];
  /** How many rivals took their pick ahead of you — you're that far down. */
  draftAhead = 0;
  /** Set whenever pieces merge into a star-up, for the screen's celebration. */
  lastMerge: { type: string; star: number } | null = null;
  /** Set whenever two components fuse into a relic, for the screen's flourish. */
  lastFusion: { item: string; type: string } | null = null;
  // The round's matchup, fixed when the shop opens so the on-board preview shows
  // the real upcoming enemy and the live fight uses the same deterministic seed.
  pendingSeed = 0;
  pendingOpp: ArenaUnit[] = [];
  private pendingFoe: Opponent | null = null;
  private foeBrains: FoeBrain[] = []; // each opponent's hidden economy
  private pool: Record<string, number> = {}; // shared lobby unit pool (you + foes draw from it)

  /**
   * `commander` picks how the run opens:
   *   omitted   → the run starts in the "commander" phase and you choose one
   *   an id     → that commander leads, no pick
   *   null      → no commander at all (used by tests that want bare rules)
   */
  constructor(seed = (Math.random() * 1e9) | 0, commander?: string | null) {
    this.rng = new RNG(seed);
    for (const [type, tier] of Object.entries(UNIT_TIER)) if (UNITS[type]) this.pool[type] = POOL_SIZE[tier] ?? 12;
    this.opponents = NAMES.map((name, id) => ({ id, name, life: 100, alive: true }));
    // Every opponent runs the exact same economy as the player, from the same
    // starting point (2 gold, level 1, empty board), on its own seeded stream.
    const PLAYS: FoePlay[] = ["economist", "aggressor", "roller"];
    this.foeBrains = this.opponents.map((o) => {
      const rng = this.rng.fork(o.id + 1);
      return {
        gold: 2, level: 1, xp: 0, pieces: [], rng,
        favored: pickFavored(rng),
        play: PLAYS[o.id % PLAYS.length], // a spread of styles across the lobby
      };
    });
    if (commander !== undefined && commander !== null) this.applyCommander(warbandCommander(commander) ?? null);
    this.startRound();
    // With no commander named, the run opens on the choice.
    if (commander === undefined) {
      this.commanderOffer = offerCommanders(this.rng);
      this.phase = "commander";
    }
  }

  /** Take a commander for the run, applying its one-off effects. */
  private applyCommander(c: WarbandCommander | null) {
    this.commander = c;
    if (!c) return;
    if (c.startGold) this.gold += c.startGold;
    this.freeRerolls = c.freeRerolls ?? 0;
    this.reconcile(); // an extra board slot takes effect at once
  }

  /** Choose one of the offered commanders and begin the run properly. */
  pickCommander(index: number): boolean {
    if (this.phase !== "commander") return false;
    const c = this.commanderOffer[index];
    if (!c) return false;
    this.applyCommander(c);
    this.commanderOffer = [];
    this.phase = "shop";
    return true;
  }

  // ---- economy / shop -------------------------------------------------------
  // You field exactly your level — levelling up is what unlocks another board
  // slot, so teching is a real tradeoff against buying/rerolling.
  private boardCap(): number {
    return this.level + this.augSum((a) => a.boardSlots) + (this.commander?.boardSlots ?? 0);
  }

  /** How many pieces deploy this round (for the screen's "deploying top N"). */
  deployCount(): number { return this.boardCap(); }

  // ---- augment-modified economy knobs --------------------------------------
  /** Sum a numeric field across every owned augment. */
  private augSum(pick: (a: Augment) => number | undefined): number {
    let n = 0;
    for (const a of this.augments) n += pick(a) ?? 0;
    return n;
  }
  /** What a reroll costs right now — free while the commander's rerolls last. */
  rerollCost(): number {
    if (this.freeRerolls > 0) return 0;
    let c = REROLL_COST;
    for (const a of this.augments) if (a.rerollCost != null) c = Math.min(c, a.rerollCost);
    return c;
  }
  /** Free rerolls left this round (for the shop button's label). */
  freeRerollsLeft(): number { return this.freeRerolls; }
  /** The interest cap — 5 gold unless an augment or commander raises it. */
  private interestCap(): number {
    let cap = 5;
    for (const a of this.augments) if (a.interestCap != null) cap = Math.max(cap, a.interestCap);
    if (this.commander?.interestCap != null) cap = Math.max(cap, this.commander.interestCap);
    return cap;
  }
  /** The level the shop rolls at — a commander can read one level higher. */
  private shopLevel(): number {
    return Math.min(MAX_LEVEL, this.level + (this.commander?.shopLevelBonus ?? 0));
  }
  /** How often a relic is earned (every N rounds; augments speed this up). */
  private relicEvery(): number {
    let n = 3;
    for (const a of this.augments) if (a.relicEvery != null) n = Math.min(n, a.relicEvery);
    return n;
  }
  /** This round's shop tier odds (%), for the level-up tooltip. */
  shopOdds(): number[] { return TIER_ODDS[this.shopLevel() - 1]; }
  /** XP progress toward the next level, for the header bar. */
  xpProgress(): { xp: number; need: number; max: boolean } {
    if (this.level >= MAX_LEVEL) return { xp: 0, need: 0, max: true };
    const from = XP_TO_LEVEL[this.level];
    const to = XP_TO_LEVEL[this.level + 1];
    return { xp: this.xp - from, need: to - from, max: false };
  }
  /** A TFT-style "stage-round" label, e.g. round 7 → "2-2". */
  stageLabel(): string {
    const stage = Math.floor((this.round - 1) / 5) + 1;
    const step = ((this.round - 1) % 5) + 1;
    return `${stage}-${step}`;
  }

  private rollShop() {
    const odds = TIER_ODDS[this.shopLevel() - 1];
    this.shop = [];
    for (let i = 0; i < SHOP_SIZE; i++) this.shop.push(this.rollFromPool(odds, this.rng));
  }

  /** Roll a shop unit from the shared pool: pick a tier by odds, then a type
   *  weighted by how many copies remain (depleted types appear less). */
  private rollFromPool(odds: number[], rng: RNG): string | null {
    let roll = rng.range(0, 100);
    let tier = 1;
    for (let t = 0; t < 5; t++) { if (roll < odds[t]) { tier = t + 1; break; } roll -= odds[t]; }
    const avail = TIER_UNITS[tier].filter((type) => (this.pool[type] ?? 0) > 0);
    if (!avail.length) return null;
    let total = 0; for (const type of avail) total += this.pool[type];
    let pick = rng.range(0, total);
    for (const type of avail) { pick -= this.pool[type]; if (pick < 0) return type; }
    return avail[avail.length - 1];
  }

  /** Copies of a unit type left in the shared pool (for the shop UI / reads). */
  poolCount(type: string): number { return this.pool[type] ?? 0; }

  /** Take a copy from the pool when acquired; refund (star-weighted) on sale. */
  private takeFromPool(type: string) { this.pool[type] = Math.max(0, (this.pool[type] ?? 0) - 1); }
  private refundToPool(type: string, star: number) { this.pool[type] = (this.pool[type] ?? 0) + Math.pow(3, star - 1); }

  reroll(): boolean {
    const cost = this.rerollCost();
    if (this.phase !== "shop" || this.gold < cost) return false;
    if (this.freeRerolls > 0) this.freeRerolls--; else this.gold -= cost;
    this.rollShop();
    return true;
  }

  buyXp(): boolean {
    if (this.phase !== "shop" || this.gold < XP_BUY || this.level >= MAX_LEVEL) return false;
    this.gold -= XP_BUY;
    this.xp += XP_BUY;
    while (this.level < MAX_LEVEL && this.xp >= XP_TO_LEVEL[this.level + 1]) this.level++;
    this.reconcile(); // bigger board → pull up reserves
    return true;
  }

  cost(type: string): number { return UNIT_TIER[type] ?? 1; }

  /** Reserves currently on the bench (everything not deployed). */
  benchCount(): number { return this.pieces.filter((p) => !p.deployed).length; }
  /** Bench capacity — fixed, like TFT's nine slots. */
  benchSlots(): number { return BENCH_SLOTS; }
  /** True when the bench is full and no room remains for a plain buy. */
  benchFull(): boolean { return this.benchCount() >= BENCH_SLOTS; }

  /**
   * Would buying this type immediately merge? Two 1★ copies already held means
   * the third combines rather than taking a slot — so a full bench must never
   * block the buy that would have freed it.
   */
  private wouldMerge(type: string): boolean {
    return this.pieces.filter((p) => p.type === type && p.star === 1).length >= 2;
  }

  /** Whether a shop slot can be bought right now, and why not if it can't. */
  canBuy(slot: number): { ok: boolean; reason?: "gold" | "bench" } {
    if (this.phase !== "shop") return { ok: false };
    const type = this.shop[slot];
    if (!type) return { ok: false };
    if (this.gold < this.cost(type)) return { ok: false, reason: "gold" };
    // A full board *and* a full bench leaves nowhere to put it.
    if (this.benchFull() && this.deployedCount() >= this.boardCap() && !this.wouldMerge(type)) {
      return { ok: false, reason: "bench" };
    }
    return { ok: true };
  }

  buy(slot: number): boolean {
    if (this.phase !== "shop") return false;
    const type = this.shop[slot];
    if (!type) return false;
    const c = this.cost(type);
    if (this.gold < c) return false;
    if (!this.canBuy(slot).ok) return false;
    this.gold -= c;
    this.shop[slot] = null;
    this.takeFromPool(type); // claim the copy from the shared pool
    this.pieces.push({ type, star: 1, items: [] });
    this.merge();
    this.reconcile(); // auto-field it if the board has a free slot
    return true;
  }

  /** Sell a piece back for (tier × star) gold. */
  sell(index: number): boolean {
    if (this.phase !== "shop" || index < 0 || index >= this.pieces.length) return false;
    const p = this.pieces[index];
    // Normally a flat star price; the Quartermaster refunds every copy sunk in.
    this.gold += this.commander?.fullRefund
      ? this.cost(p.type) * Math.pow(3, p.star - 1)
      : this.cost(p.type) * p.star;
    this.refundToPool(p.type, p.star); // its copies return to the shared pool
    this.pieces.splice(index, 1);
    this.reconcile(); // selling a board unit pulls up a reserve
    return true;
  }

  /** Combine any 3 same type+star into one of the next star. */
  private merge() {
    const made = mergeBoard(this.pieces, this.itemStash);
    if (made.length) this.lastMerge = made[made.length - 1]; // the screen clears it after celebrating
  }

  /**
   * Equip a stashed component or relic onto a piece (max 3 per unit). Landing a
   * second loose component on the same unit fuses the pair into its full relic
   * and frees the slot — that's how carries get built.
   */
  equipItem(stashIndex: number, pieceIndex: number): boolean {
    if (this.phase !== "shop") return false;
    if (stashIndex < 0 || stashIndex >= this.itemStash.length) return false;
    const p = this.pieces[pieceIndex];
    if (!p || p.items.length >= MAX_ITEMS) return false;
    p.items.push(this.itemStash.splice(stashIndex, 1)[0]);
    const made = fuseComponents(p.items);
    if (made) this.lastFusion = { item: made, type: p.type };
    return true;
  }

  /** Sort key: strongest pieces first (star, then tier, then relics). */
  private stronger(a: number, b: number): number {
    return this.pieces[b].star - this.pieces[a].star ||
      (UNIT_TIER[this.pieces[b].type] ?? 0) - (UNIT_TIER[this.pieces[a].type] ?? 0) ||
      this.pieces[b].items.length - this.pieces[a].items.length;
  }

  /** Indices of the pieces currently on the board, strongest first. */
  private deployedIndices(): number[] {
    return this.pieces.map((_, i) => i).filter((i) => this.pieces[i].deployed).sort((a, b) => this.stronger(a, b));
  }

  /**
   * Keep the board legal & full: trim past the cap (weakest benched), auto-fill
   * empty slots with your strongest reserves, and give every deployed piece a
   * cell. Manual placements stay put — auto-fill only ever fills empty slots, so
   * benching a unit by swapping a reserve in is never undone.
   */
  private reconcile() {
    const cap = this.boardCap();
    let deployed = this.pieces.map((_, i) => i).filter((i) => this.pieces[i].deployed);
    // Over cap (e.g. after a level is somehow lower): bench the weakest.
    if (deployed.length > cap) {
      deployed.sort((a, b) => this.stronger(b, a)); // weakest first
      for (const i of deployed.slice(0, deployed.length - cap)) { const p = this.pieces[i]; p.deployed = false; p.col = p.row = undefined; }
    }
    // Auto-fill empty slots with the strongest reserves.
    let count = this.pieces.filter((p) => p.deployed).length;
    if (count < cap) {
      const bench = this.pieces.map((_, i) => i).filter((i) => !this.pieces[i].deployed).sort((a, b) => this.stronger(a, b));
      for (const i of bench) { if (count >= cap) break; this.pieces[i].deployed = true; count++; }
    }
    // Assign a cell to every deployed piece that lacks one (front rank first).
    const used = new Set<string>();
    for (const p of this.pieces) if (p.deployed && p.col != null && p.row != null) used.add(`${p.col},${p.row}`);
    let ai = 0;
    for (const i of this.deployedIndices()) {
      const p = this.pieces[i];
      if (p.col == null || p.row == null) {
        while (ai < PLAYER_CELLS.length && used.has(`${PLAYER_CELLS[ai].c},${PLAYER_CELLS[ai].r}`)) ai++;
        const cell = PLAYER_CELLS[Math.min(ai, PLAYER_CELLS.length - 1)];
        p.col = cell.c; p.row = cell.r; used.add(`${cell.c},${cell.r}`); ai++;
      }
    }
  }

  /** Deployed units (with relics + board cell) for the battle resolver. */
  boardUnits(): ArenaUnit[] {
    this.reconcile();
    return this.deployedIndices().map((i) => {
      const p = this.pieces[i];
      return { type: p.type, star: p.star, items: p.items, col: p.col, row: p.row };
    });
  }

  /** The deployed pieces with their board cells (for the placement UI). */
  deployment(): { index: number; type: string; star: number; col: number; row: number }[] {
    this.reconcile();
    return this.deployedIndices().map((i) => {
      const p = this.pieces[i];
      return { index: i, type: p.type, star: p.star, col: p.col!, row: p.row! };
    });
  }

  /** How many pieces are on the board right now (≤ deployCount()). */
  deployedCount(): number { return this.pieces.filter((p) => p.deployed).length; }

  /**
   * Move/field a piece onto a board cell on your half (cols 0..4, rows 0..9).
   * A deployed piece repositions (swapping cells with any occupant). A bench
   * piece is fielded: it swaps in for the occupant, fills a free slot, or — if
   * the board is already full — bumps your weakest deployed unit to the bench.
   */
  place(index: number, col: number, row: number): boolean {
    if (this.phase !== "shop") return false;
    if (col < 0 || col > 4 || row < 0 || row > 9) return false;
    const p = this.pieces[index];
    if (!p) return false;
    const occ = this.pieces.find((q, qi) => qi !== index && q.deployed && q.col === col && q.row === row);
    if (p.deployed) {
      if (occ) { occ.col = p.col; occ.row = p.row; } // swap cells
      p.col = col; p.row = row;
      return true;
    }
    // Fielding a bench unit.
    if (occ) { occ.deployed = false; occ.col = occ.row = undefined; }
    else if (this.deployedCount() >= this.boardCap()) {
      const weakest = this.deployedIndices().pop(); // strongest-first → last is weakest
      if (weakest == null || weakest === index) return false;
      const w = this.pieces[weakest]; w.deployed = false; w.col = w.row = undefined;
    }
    p.deployed = true; p.col = col; p.row = row;
    return true;
  }

  /** The units that actually deploy this round (strongest `level` pieces). */
  boardStacks(): UnitStack[] {
    const deployed = [...this.pieces]
      .sort((a, b) => b.star - a.star || (UNIT_TIER[b.type] ?? 0) - (UNIT_TIER[a.type] ?? 0))
      .slice(0, this.boardCap());
    const byKey = new Map<string, UnitStack>();
    for (const p of deployed) {
      const k = `${p.type}:${p.star}`;
      const s = byKey.get(k);
      if (s) s.count++; else byKey.set(k, { type: p.type, count: 1, star: p.star });
    }
    return [...byKey.values()];
  }

  // ---- round / combat -------------------------------------------------------
  private startRound() {
    this.round++;
    // Income: base + interest (1 per 10 gold, capped) + win/loss-streak bonus,
    // plus whatever the run's augments add.
    const interest = Math.min(this.interestCap(), Math.floor(this.gold / 10));
    const streak = streakBonus(this.streak);
    this.gold += 5 + interest + streak + this.augSum((a) => a.gold) + (this.commander?.gold ?? 0);
    this.freeRerolls = this.commander?.freeRerolls ?? 0; // the perk refreshes every round
    // Augment-granted free XP (a levelling engine).
    const freeXp = this.augSum((a) => a.xp);
    if (freeXp > 0) {
      this.xp += freeXp;
      while (this.level < MAX_LEVEL && this.xp >= XP_TO_LEVEL[this.level + 1]) this.level++;
    }
    // A relic every few rounds — equip it to build a carry.
    if (this.round % this.relicEvery() === 2 % this.relicEvery()) this.grantRelic();
    this.rollShop();
    // Every living opponent runs its economy for the round (same rules as you).
    for (const o of this.opponents) if (o.alive) this.stepFoe(this.foeBrains[o.id]);
    // Lock in this round's matchup + battle seed now, so the board preview and
    // the live fight face the same warband (or camp).
    this.pendingSeed = this.rng.int(1, 1e9);
    this.condition = rollCondition(this.rng, this.round);
    if (isCreepRound(this.round)) {
      // A PvE monster camp: no player life at stake, relics on the table.
      this.pendingCamp = campForRound(this.round);
      this.pendingFoe = null;
      this.pendingOpp = campBoard(this.pendingCamp);
    } else {
      this.pendingCamp = null;
      const foes = this.livingFoes();
      this.pendingFoe = foes.length ? foes[this.round % foes.length] : null;
      this.pendingOpp = this.pendingFoe ? this.foeBoard(this.foeBrains[this.pendingFoe.id]) : [];
    }
    this.reconcile(); // a levelled board pulls up reserves before you see it
    // An augment round pauses everything for the pick; so does a draft round.
    const tier = tierForRound(this.round);
    if (tier && this.augmentOffer.length === 0) {
      this.augmentOffer = offerAugments(this.rng, tier, this.augments.map((a) => a.id));
      this.phase = "augment";
    } else if (isDraftRound(this.round)) {
      this.openDraft();
    } else this.phase = "shop";
  }

  /**
   * Open the carousel. Everyone weaker than you takes their pick first — the
   * greedy best each time — so a leading warband chooses from the remainder.
   */
  private openDraft() {
    const ring = rollCarousel(this.rng, this.round);
    const weaker = this.livingFoes().filter((o) => o.life < this.life);
    this.draftAhead = Math.min(weaker.length, Math.max(0, ring.length - 1)); // always leave one choice
    // The warbands ahead of you take the strongest options.
    const ordered = [...ring].sort((a, b) => pickValue(b) - pickValue(a));
    const taken = new Set<CarouselPick>();
    for (let i = 0; i < this.draftAhead; i++) {
      const pick = ordered[i];
      taken.add(pick);
      // Whoever took it actually gets it, so the lobby scales off the draft too.
      const foe = weaker[i];
      if (foe) {
        const b = this.foeBrains[foe.id];
        this.takeFromPool(pick.type);
        b.pieces.push({ type: pick.type, star: pick.star, items: [pick.item] });
        mergeBoard(b.pieces, []);
        this.equipFoeSpare(b);
      }
    }
    this.carousel = ring.filter((p) => !taken.has(p));
    this.phase = "draft";
  }

  /** Fuse any loose pair an opponent is now holding after a draft pick. */
  private equipFoeSpare(b: FoeBrain) {
    for (const p of b.pieces) fuseComponents(p.items);
  }

  /**
   * Take one of the units left on the carousel. It joins your warband with its
   * component already equipped, then the rest of the field clears the ring.
   */
  takeCarousel(index: number): boolean {
    if (this.phase !== "draft") return false;
    const pick = this.carousel[index];
    if (!pick) return false;
    this.takeFromPool(pick.type);
    this.pieces.push({ type: pick.type, star: pick.star, items: [pick.item] });
    this.merge();
    for (const p of this.pieces) if (fuseComponents(p.items)) this.lastFusion = { item: p.items[p.items.length - 1], type: p.type };
    this.reconcile();
    // Everyone stronger than you sweeps up what's left.
    const rest = this.carousel.filter((_, i) => i !== index);
    const stronger = this.livingFoes().filter((o) => o.life >= this.life);
    rest.sort((a, b) => pickValue(b) - pickValue(a));
    stronger.slice(0, rest.length).forEach((foe, i) => {
      const b = this.foeBrains[foe.id];
      this.takeFromPool(rest[i].type);
      b.pieces.push({ type: rest[i].type, star: rest[i].star, items: [rest[i].item] });
      mergeBoard(b.pieces, []);
      this.equipFoeSpare(b);
    });
    this.carousel = [];
    this.draftAhead = 0;
    this.phase = "shop";
    return true;
  }

  /** True when the carousel is waiting on your pick. */
  isDraftRound(): boolean { return isDraftRound(this.round); }

  /** Add a random component to the stash — two on one unit fuse into a relic. */
  private grantRelic() { this.itemStash.push(COMPONENT_IDS[this.rng.int(0, COMPONENT_IDS.length - 1)]); }

  /**
   * Take one of the three offered augments. Its one-off effects (bounty, life,
   * relics) land immediately; the per-round and combat effects are read live.
   */
  pickAugment(index: number): boolean {
    if (this.phase !== "augment") return false;
    const a = this.augmentOffer[index];
    if (!a) return false;
    this.augments.push(a);
    this.augmentOffer = [];
    if (a.bounty) this.gold += a.bounty;
    if (a.life) this.life = Math.min(100, this.life + a.life);
    for (let i = 0; i < (a.relics ?? 0); i++) this.grantRelic();
    this.reconcile(); // +board slots take effect at once
    this.phase = "shop";
    return true;
  }

  /** Name of the warband (or camp) you'll face this round. */
  pendingFoeName(): string { return this.pendingCamp?.name ?? this.pendingFoe?.name ?? "—"; }

  /** True when this round is a PvE monster camp rather than a player. */
  isCreepRound(): boolean { return this.pendingCamp !== null; }

  /** Warband-wide augment modifiers, for the battle resolver. */
  sideOpts(): SideOpts {
    return { buff: this.warbandBuff(), traitBonus: this.traitBonusMap() };
  }

  /**
   * The worst a defeat this round could cost: the stage toll plus every enemy
   * unit surviving. Shown before you commit, so the fight is an informed bet.
   */
  worstCaseDamage(): number {
    const power = this.pendingOpp.reduce((n, u) => n + (u.star ?? 1), 0);
    return Math.max(1, roundDamage(this.round, power) - (this.commander?.lossShield ?? 0));
  }

  /** Gold your current streak pays at the start of next round. */
  streakGold(): number { return streakBonus(this.streak); }

  /** This round's battlefield modifiers — applied identically to both sides. */
  fieldOpts(): SideOpts {
    return { buff: this.condition.all, traitBuffs: this.condition.traits };
  }

  /** Every run-wide buff (augments + commander) merged into one. */
  private warbandBuff(): Buff | undefined {
    const aug = combinedBuff(this.augments);
    const cmd = this.commander?.buff;
    if (!aug) return cmd;
    if (!cmd) return aug;
    const out: Buff = { ...aug };
    for (const k of ["armor", "atk", "atkPct", "hp", "hpPct", "speedPct"] as const) {
      if (cmd[k]) out[k] = (out[k] ?? 0) + cmd[k]!;
    }
    return out;
  }

  /**
   * Synergy count bonuses: augment banners, plus the Banneret's perk, which
   * finds whichever synergy is currently largest and lifts that one. Resolved
   * live, so it follows your comp as it changes.
   */
  private traitBonusMap(): Record<string, number> | undefined {
    const base = combinedTraitBonus(this.augments) ?? {};
    const top = this.commander?.topTraitBonus ?? 0;
    if (!top) return Object.keys(base).length ? base : undefined;
    const out = { ...base };
    const types = [...new Set(this.boardUnits().map((u) => u.type))];
    let best: ActiveTrait | null = null;
    for (const at of activeTraits(types, base)) if (!best || at.count > best.count) best = at;
    if (best) out[best.trait.id] = (out[best.trait.id] ?? 0) + top;
    return Object.keys(out).length ? out : undefined;
  }

  /**
   * Advance one opponent's economy a round, by the same rules the player plays:
   * identical income + interest, the same shop odds, level/buy/merge — but as a
   * *competent* drafter. It banks gold for interest (the real economy engine),
   * levels toward a curve, concentrates buys into its favoured comp so it hits
   * 2★/3★ star-ups and synergies, rerolls to dig for copies, and equips the
   * relics it earns onto its carry. That lets it scale roughly as fast as you.
   */
  private stepFoe(b: FoeBrain) {
    b.gold += 5 + Math.min(5, Math.floor(b.gold / 10)); // base + interest (same formula)
    if (this.round % 3 === 2) this.equipFoeRelic(b); // a relic every few rounds, like you
    // Personality shapes the three levers every warband pulls: how hard it banks
    // for interest, how fast it techs, and how much it rolls for star-ups.
    const play = b.play;
    const levelPace = play === "aggressor" ? 0.85 : play === "roller" ? 0.62 : 0.72;
    const targetLevel = Math.min(MAX_LEVEL, 1 + Math.floor(this.round * levelPace));
    const reserveCap = play === "economist" ? 50 : play === "roller" ? 20 : 30;
    const reserve = Math.min(reserveCap, Math.max(0, (this.round - 2) * 8));
    let rerolls = (play === "roller" ? 5 : 2) + Math.floor(this.round / 2);
    let guard = 0;
    while (guard++ < 160) {
      // Tech toward the curve when it can afford to and stay above reserve.
      if (b.level < targetLevel && b.gold - XP_BUY >= reserve) {
        b.gold -= XP_BUY; b.xp += XP_BUY;
        while (b.level < MAX_LEVEL && b.xp >= XP_TO_LEVEL[b.level + 1]) b.level++;
        continue;
      }
      const odds = TIER_ODDS[Math.min(b.level, MAX_LEVEL) - 1];
      const needBodies = b.pieces.length < b.level + 1; // fill a board first
      let bought = false;
      for (let s = 0; s < SHOP_SIZE; s++) {
        const type = this.rollFromPool(odds, b.rng); // draws from the shared lobby pool
        if (!type) continue;
        const c = UNIT_TIER[type];
        if (b.gold - c < reserve) continue;
        if (this.buyValue(b, type) <= 0 && !needBodies) continue;
        b.gold -= c; this.takeFromPool(type); b.pieces.push({ type, star: 1, items: [] }); mergeBoard(b.pieces, []);
        bought = true;
      }
      if (!bought) {
        if (rerolls > 0 && b.gold - REROLL_COST >= reserve) { b.gold -= REROLL_COST; rerolls--; continue; }
        break; // nothing worth buying and out of rerolls → bank the rest for interest
      }
    }
    this.trimFoeBench(b);
  }

  /**
   * How much a warband wants a given unit right now. This is what turns the
   * opponents from "buy anything on the list" into drafters: completing a merge
   * is worth the most, then advancing a synergy toward its next breakpoint,
   * then sticking to the comp identity they opened with.
   */
  private buyValue(b: FoeBrain, type: string): number {
    let value = 0;
    const copies = b.pieces.filter((p) => p.type === type && p.star < 3).length;
    if (copies >= 2) value += 10;      // this buy stars something up
    else if (copies === 1) value += 4; // halfway to a star-up
    if (b.favored.includes(type)) value += 3;
    // Does it push a synergy over its next breakpoint?
    const owned = new Set(b.pieces.map((p) => p.type));
    if (!owned.has(type)) {
      const before = activeTraits([...owned]);
      const after = activeTraits([...owned, type]);
      for (const at of after) {
        const was = before.find((x) => x.trait.id === at.trait.id);
        if (!was) value += 5;                              // switched a synergy on
        else if (at.tierIndex > was.tierIndex) value += 6; // pushed it up a tier
      }
    }
    // Late on, a scarce high tier is worth taking on principle.
    if (this.round > 8 && (UNIT_TIER[type] ?? 1) >= 4) value += 2;
    return value;
  }

  /**
   * Sell what a warband will never field. Without this an opponent hoards
   * 1★ chaff it drafted early and never converts it back into gold.
   */
  private trimFoeBench(b: FoeBrain) {
    if (this.round < 6) return; // early on, everything is still a merge in progress
    const keep = b.level + 4; // its board plus a working bench
    if (b.pieces.length <= keep) return;
    const ranked = [...b.pieces].sort((p, q) =>
      q.star - p.star || (UNIT_TIER[q.type] ?? 0) - (UNIT_TIER[p.type] ?? 0) || q.items.length - p.items.length);
    for (const dead of ranked.slice(keep)) {
      // Never sell a copy that is one buy away from a star-up — that was the
      // difference between trimming chaff and dismantling your own board.
      const sameKind = b.pieces.filter((p) => p.type === dead.type && p.star === dead.star).length;
      if (sameKind >= 2) continue;
      if (dead.items.length) continue; // nor anything carrying relics
      const i = b.pieces.indexOf(dead);
      if (i < 0) continue;
      b.pieces.splice(i, 1);
      b.gold += (UNIT_TIER[dead.type] ?? 1) * dead.star;
      this.refundToPool(dead.type, dead.star); // its copies go back to the lobby
    }
  }

  /** Earn + equip a component onto an opponent's strongest unit, fusing pairs
   *  into full relics the same way you do — so foes build carries too. */
  private equipFoeRelic(b: FoeBrain) {
    const id = COMPONENT_IDS[b.rng.int(0, COMPONENT_IDS.length - 1)];
    const target = [...b.pieces]
      .sort((p, q) => q.star - p.star || (UNIT_TIER[q.type] ?? 0) - (UNIT_TIER[p.type] ?? 0))
      .find((p) => p.items.length < MAX_ITEMS);
    if (target) { target.items.push(id); fuseComponents(target.items); }
  }

  /** An opponent's deployed warband — its strongest pieces (with relics) on its half. */
  private foeBoard(b: FoeBrain): ArenaUnit[] {
    const cap = b.level; // same rule the player plays by
    const fielded = [...b.pieces]
      .sort((p, q) => q.star - p.star || (UNIT_TIER[q.type] ?? 0) - (UNIT_TIER[p.type] ?? 0))
      .slice(0, cap);
    return placeByStyle(fielded, 1);
  }

  private livingFoes(): Opponent[] { return this.opponents.filter((o) => o.alive); }

  /** Resolve this round's fight instantly (headless — tests, AI, auto-play). */
  fight(): void {
    if (this.phase !== "shop") return;
    if (!this.pendingFoe && !this.pendingCamp) { this.outcome = "win"; this.phase = "over"; return; }
    this.applyOutcome(resolveBattle(this.boardUnits(), this.pendingOpp, this.pendingSeed, 40, this.sideOpts(), this.fieldOpts()));
  }

  /** Begin the watchable version of the fight (the screen renders + steps it). */
  beginFight(): boolean {
    if (this.phase !== "shop") return false;
    if (!this.pendingFoe && !this.pendingCamp) { this.outcome = "win"; this.phase = "over"; return false; }
    this.phase = "battle";
    return true;
  }

  /** Hand back the live battle's verdict to apply life damage + eliminations. */
  finishFight(res: BattleResult): void {
    if (this.phase !== "battle") return;
    this.applyOutcome(res);
  }

  /** Apply a battle result: damage, streak, off-screen thinning, win/lose check. */
  private applyOutcome(res: BattleResult): void {
    // A PvE camp round: loot on a win, a small bite on a loss, no streak and no
    // player eliminated either way.
    const camp = this.pendingCamp;
    if (camp) {
      const wonCamp = res.winner === "A";
      let relics = 0;
      if (wonCamp) {
        relics = camp.relics;
        for (let i = 0; i < relics; i++) this.grantRelic();
        this.gold += camp.gold;
      } else this.life -= camp.bite;
      this.thinTheHerd();
      this.lastResult = {
        won: wonCamp, foe: camp.name, youLeft: res.survivorsA, foeLeft: res.survivorsB,
        dmg: wonCamp ? 0 : camp.bite, relics, gold: wonCamp ? camp.gold : 0, creep: true,
      };
      if (this.life <= 0) { this.life = 0; this.outcome = "loss"; this.phase = "over"; return; }
      if (this.livingFoes().length === 0) { this.outcome = "win"; this.phase = "over"; return; }
      this.phase = "result";
      return;
    }
    const foe = this.pendingFoe;
    if (!foe) { this.outcome = "win"; this.phase = "over"; return; }
    const won = res.winner === "A";
    // Stage toll + star-weighted survivors; a commander can blunt what you take.
    const raw = roundDamage(this.round, res.powerB);
    const dmg = won ? 0 : Math.max(1, raw - (this.commander?.lossShield ?? 0));
    if (won) {
      foe.life -= roundDamage(this.round, res.powerA);
      if (foe.life <= 0) { foe.life = 0; foe.alive = false; }
      this.streak = Math.max(1, this.streak + 1);
    } else {
      this.life -= dmg;
      this.streak = Math.min(-1, this.streak - 1);
    }
    // The other living foes skirmish among themselves so the lobby thins out too.
    this.thinTheHerd();
    this.lastResult = { won, foe: foe.name, youLeft: res.survivorsA, foeLeft: res.survivorsB, dmg, relics: 0, gold: 0, creep: false };

    if (this.life <= 0) { this.life = 0; this.outcome = "loss"; this.phase = "over"; return; }
    if (this.livingFoes().length === 0) { this.outcome = "win"; this.phase = "over"; return; }
    this.phase = "result";
  }

  /** Off-screen attrition: random living foes lose a little life each round. */
  private thinTheHerd() {
    for (const o of this.livingFoes()) {
      if (this.rng.range(0, 1) < 0.35) {
        o.life -= this.rng.int(4, 10) + Math.floor(this.round / 2);
        // Attrition never decides the run: the last warband standing has to be
        // beaten in the arena, not quietly whittled away off-screen. Without
        // this the lobby can empty on the same round you die, leaving nobody.
        if (o.life <= 0) {
          if (this.livingFoes().length <= 1) { o.life = 1; continue; }
          o.life = 0; o.alive = false;
        }
      }
    }
  }

  /** Advance from the result screen into the next shop phase. */
  next(): void {
    if (this.phase === "result") this.startRound();
  }

  /** Standings: every contestant (you + foes) by life, for the sidebar. */
  standings(): { name: string; life: number; alive: boolean; you: boolean; id: number }[] {
    const all = [
      { name: "You", life: this.life, alive: this.life > 0, you: true, id: -1 },
      ...this.opponents.map((o) => ({ name: o.name, life: o.life, alive: o.alive, you: false, id: o.id })),
    ];
    return all.sort((a, b) => Number(b.alive) - Number(a.alive) || b.life - a.life);
  }

  /** Active synergies on your currently-deployed warband (for the screen). */
  activeTraits(): ActiveTrait[] {
    return activeTraits([...new Set(this.boardUnits().map((u) => u.type))], this.traitBonusMap());
  }

  /**
   * Scout a rival: the warband they'd field right now, plus their level and
   * synergies. Lets you read the lobby and adapt — you can see who's building
   * what before you commit your own gold.
   */
  scout(id: number): { name: string; level: number; life: number; alive: boolean; board: ArenaUnit[]; traits: ActiveTrait[] } | null {
    const o = this.opponents[id];
    const b = this.foeBrains[id];
    if (!o || !b) return null;
    const board = this.foeBoard(b);
    return {
      name: o.name, level: b.level, life: o.life, alive: o.alive, board,
      traits: activeTraits([...new Set(board.map((u) => u.type))]),
    };
  }

  /** Placement (1 = winner) once the run is over. */
  placement(): number {
    return 1 + this.livingFoes().length;
  }
}
