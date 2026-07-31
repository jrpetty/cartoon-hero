// "Warband Tactics" — the auto-battler run engine (logic only; the screen draws
// it). A run is: shop for units → they auto-merge into star-ups → fight an
// opponent's warband (resolved by autobattle.resolveBattle) → lose life if you
// lose → last warband standing wins. Deterministic via a seeded RNG so it's
// testable and replay-safe.

import { RNG } from "../engine/rng";
import { UNITS } from "../content/units";
import { resolveBattle, UnitStack, ArenaUnit, BattleResult, SideOpts } from "./autobattle";
import { activeTraits, ActiveTrait } from "./traits";
import { COMPONENT_IDS, MAX_ITEMS, fuseComponents } from "./items";
import { Augment, offerAugments, tierForRound, combinedBuff, combinedTraitBonus } from "./augments";
import { CreepCamp, isCreepRound, campForRound, campBoard } from "./creeps";

/** Shop tier (1 cheap/weak … 5 rare/strong); buying costs the tier in gold. */
export const UNIT_TIER: Record<string, number> = {
  militia: 1, spearman: 1, scout: 1, raider: 1,
  archer: 2, skirmisher: 2, horseman: 2, javelin: 2, pikeman: 2,
  knight: 3, crossbow: 3, twohand: 3,
  handcannon: 4, monk: 4, catapult: 4,
  ram: 5, trebuchet: 5, hero: 5,
};
const TIER_UNITS: string[][] = [[], [], [], [], [], []]; // index by tier
for (const [type, tier] of Object.entries(UNIT_TIER)) if (UNITS[type]) TIER_UNITS[tier].push(type);

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
interface FoeBrain { gold: number; level: number; xp: number; pieces: Piece[]; rng: RNG; favored: string[]; }

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
  phase: "augment" | "shop" | "battle" | "result" | "over" = "shop";
  outcome: "win" | "loss" | null = null;
  lastResult: { won: boolean; foe: string; youLeft: number; foeLeft: number; dmg: number; relics: number; gold: number; creep: boolean } | null = null;
  // ---- augments (TFT-style run-defining picks) ----
  augments: Augment[] = [];
  augmentOffer: Augment[] = [];
  // ---- the round's PvE camp, when this is a monster round ----
  pendingCamp: CreepCamp | null = null;
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

  constructor(seed = (Math.random() * 1e9) | 0) {
    this.rng = new RNG(seed);
    for (const [type, tier] of Object.entries(UNIT_TIER)) if (UNITS[type]) this.pool[type] = POOL_SIZE[tier] ?? 12;
    this.opponents = NAMES.map((name, id) => ({ id, name, life: 100, alive: true }));
    // Every opponent runs the exact same economy as the player, from the same
    // starting point (2 gold, level 1, empty board), on its own seeded stream.
    this.foeBrains = this.opponents.map((o) => {
      const rng = this.rng.fork(o.id + 1);
      return { gold: 2, level: 1, xp: 0, pieces: [], rng, favored: pickFavored(rng) };
    });
    this.startRound();
  }

  // ---- economy / shop -------------------------------------------------------
  // You field exactly your level — levelling up is what unlocks another board
  // slot, so teching is a real tradeoff against buying/rerolling.
  private boardCap(): number { return this.level + this.augSum((a) => a.boardSlots); }

  /** How many pieces deploy this round (for the screen's "deploying top N"). */
  deployCount(): number { return this.boardCap(); }

  // ---- augment-modified economy knobs --------------------------------------
  /** Sum a numeric field across every owned augment. */
  private augSum(pick: (a: Augment) => number | undefined): number {
    let n = 0;
    for (const a of this.augments) n += pick(a) ?? 0;
    return n;
  }
  /** What a reroll costs (augments can discount it). */
  rerollCost(): number {
    let c = REROLL_COST;
    for (const a of this.augments) if (a.rerollCost != null) c = Math.min(c, a.rerollCost);
    return c;
  }
  /** The interest cap — 5 gold unless an augment raises it. */
  private interestCap(): number {
    let cap = 5;
    for (const a of this.augments) if (a.interestCap != null) cap = Math.max(cap, a.interestCap);
    return cap;
  }
  /** How often a relic is earned (every N rounds; augments speed this up). */
  private relicEvery(): number {
    let n = 3;
    for (const a of this.augments) if (a.relicEvery != null) n = Math.min(n, a.relicEvery);
    return n;
  }
  /** This round's shop tier odds (%), for the level-up tooltip. */
  shopOdds(): number[] { return TIER_ODDS[Math.min(this.level, MAX_LEVEL) - 1]; }
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
    const odds = TIER_ODDS[Math.min(this.level, MAX_LEVEL) - 1];
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
    this.gold -= cost;
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

  buy(slot: number): boolean {
    if (this.phase !== "shop") return false;
    const type = this.shop[slot];
    if (!type) return false;
    const c = this.cost(type);
    if (this.gold < c) return false;
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
    this.gold += this.cost(p.type) * p.star;
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
    const streakBonus = Math.min(3, Math.abs(this.streak) >= 2 ? Math.floor(Math.abs(this.streak) / 2) + 1 : 0);
    this.gold += 5 + interest + streakBonus + this.augSum((a) => a.gold);
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
    // An augment round pauses everything for the pick.
    const tier = tierForRound(this.round);
    if (tier && this.augmentOffer.length === 0) {
      this.augmentOffer = offerAugments(this.rng, tier, this.augments.map((a) => a.id));
      this.phase = "augment";
    } else this.phase = "shop";
  }

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
    return { buff: combinedBuff(this.augments), traitBonus: combinedTraitBonus(this.augments) };
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
    const targetLevel = Math.min(MAX_LEVEL, 1 + Math.floor(this.round * 0.7));
    // Bank toward the max-interest breakpoint as the game goes — early rounds it
    // spends freely to build a board; later it keeps gold working for interest
    // and only buys upgrades / rerolls down to the reserve.
    const reserve = Math.min(50, Math.max(0, (this.round - 2) * 8));
    let rerolls = 2 + Math.floor(this.round / 2);
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
        const owned = b.pieces.filter((p) => p.type === type && p.star < 3).length;
        // Buy if it fills out a thin board, belongs to the comp, or completes a merge.
        if (!(needBodies || b.favored.includes(type) || owned >= 1)) continue;
        b.gold -= c; this.takeFromPool(type); b.pieces.push({ type, star: 1, items: [] }); mergeBoard(b.pieces, []);
        bought = true;
      }
      if (!bought) {
        if (rerolls > 0 && b.gold - REROLL_COST >= reserve) { b.gold -= REROLL_COST; rerolls--; continue; }
        break; // nothing worth buying and out of rerolls → bank the rest for interest
      }
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
    return [...b.pieces]
      .sort((p, q) => q.star - p.star || (UNIT_TIER[q.type] ?? 0) - (UNIT_TIER[p.type] ?? 0))
      .slice(0, cap)
      .map((p, i) => {
        const cell = ENEMY_CELLS[Math.min(i, ENEMY_CELLS.length - 1)];
        return { type: p.type, star: p.star, items: p.items, col: cell.c, row: cell.r };
      });
  }

  private livingFoes(): Opponent[] { return this.opponents.filter((o) => o.alive); }

  /** Resolve this round's fight instantly (headless — tests, AI, auto-play). */
  fight(): void {
    if (this.phase !== "shop") return;
    if (!this.pendingFoe && !this.pendingCamp) { this.outcome = "win"; this.phase = "over"; return; }
    this.applyOutcome(resolveBattle(this.boardUnits(), this.pendingOpp, this.pendingSeed, 40, this.sideOpts()));
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
    const dmg = won ? 0 : 3 + res.powerB * 2; // lose → take damage scaled by enemy survivors
    if (won) {
      foe.life -= 3 + res.powerA * 2;
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
        if (o.life <= 0) { o.life = 0; o.alive = false; }
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
    return activeTraits([...new Set(this.boardUnits().map((u) => u.type))], combinedTraitBonus(this.augments));
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
