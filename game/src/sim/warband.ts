// "Warband Tactics" — the auto-battler run engine (logic only; the screen draws
// it). A run is: shop for units → they auto-merge into star-ups → fight an
// opponent's warband (resolved by autobattle.resolveBattle) → lose life if you
// lose → last warband standing wins. Deterministic via a seeded RNG so it's
// testable and replay-safe.

import { RNG } from "../engine/rng";
import { UNITS } from "../content/units";
import { resolveBattle, UnitStack } from "./autobattle";
import { activeTraits, ActiveTrait } from "./traits";

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

export interface Piece { type: string; star: number; } // star 1..3
export interface Opponent { id: number; name: string; life: number; alive: boolean; }

const NAMES = ["Ironclad", "Crimson", "Verdant", "Stormcrow", "Ashveil", "Goldhand", "Nightfall"];

export class WarbandRun {
  private rng: RNG;
  round = 0;
  gold = 2;
  level = 1;
  xp = 0;
  streak = 0; // +win streak / −loss streak
  life = 100;
  pieces: Piece[] = []; // bench + board combined; the top `level` deploy
  shop: (string | null)[] = [];
  opponents: Opponent[] = [];
  phase: "shop" | "result" | "over" = "shop";
  outcome: "win" | "loss" | null = null;
  lastResult: { won: boolean; foe: string; youLeft: number; foeLeft: number; dmg: number } | null = null;

  constructor(seed = (Math.random() * 1e9) | 0) {
    this.rng = new RNG(seed);
    this.opponents = NAMES.map((name, id) => ({ id, name, life: 100, alive: true }));
    this.startRound();
  }

  // ---- economy / shop -------------------------------------------------------
  private boardCap(): number { return this.level; }

  private rollShop() {
    const odds = TIER_ODDS[Math.min(this.level, MAX_LEVEL) - 1];
    this.shop = [];
    for (let i = 0; i < SHOP_SIZE; i++) {
      let roll = this.rng.range(0, 100);
      let tier = 1;
      for (let t = 0; t < 5; t++) { if (roll < odds[t]) { tier = t + 1; break; } roll -= odds[t]; }
      const pool = TIER_UNITS[tier];
      this.shop.push(pool.length ? pool[this.rng.int(0, pool.length - 1)] : null);
    }
  }

  reroll(): boolean {
    if (this.phase !== "shop" || this.gold < REROLL_COST) return false;
    this.gold -= REROLL_COST;
    this.rollShop();
    return true;
  }

  buyXp(): boolean {
    if (this.phase !== "shop" || this.gold < XP_BUY || this.level >= MAX_LEVEL) return false;
    this.gold -= XP_BUY;
    this.xp += XP_BUY;
    while (this.level < MAX_LEVEL && this.xp >= XP_TO_LEVEL[this.level + 1]) this.level++;
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
    this.pieces.push({ type, star: 1 });
    this.merge();
    return true;
  }

  /** Sell a piece back for (tier × star) gold. */
  sell(index: number): boolean {
    if (this.phase !== "shop" || index < 0 || index >= this.pieces.length) return false;
    const p = this.pieces[index];
    this.gold += this.cost(p.type) * p.star;
    this.pieces.splice(index, 1);
    return true;
  }

  /** Combine any 3 same type+star into one of the next star. */
  private merge() {
    for (let star = 1; star <= 2; star++) {
      let changed = true;
      while (changed) {
        changed = false;
        const counts: Record<string, number> = {};
        for (const p of this.pieces) if (p.star === star) counts[p.type] = (counts[p.type] ?? 0) + 1;
        for (const [type, n] of Object.entries(counts)) {
          if (n >= 3) {
            let removed = 0;
            for (let i = this.pieces.length - 1; i >= 0 && removed < 3; i--) {
              if (this.pieces[i].type === type && this.pieces[i].star === star) { this.pieces.splice(i, 1); removed++; }
            }
            this.pieces.push({ type, star: star + 1 });
            changed = true;
          }
        }
      }
    }
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
    // Income: base + interest (1 per 10 gold, max 5) + win/loss-streak bonus.
    const interest = Math.min(5, Math.floor(this.gold / 10));
    const streakBonus = Math.min(3, Math.abs(this.streak) >= 2 ? Math.floor(Math.abs(this.streak) / 2) + 1 : 0);
    this.gold += 5 + interest + streakBonus;
    this.rollShop();
    this.phase = "shop";
  }

  /** Build an opponent warband that scales with the round (procedural for now). */
  private opponentStacks(seed: number): UnitStack[] {
    const r = new RNG(seed);
    const budget = 3 + this.round * 2.4; // grows each round
    const lvl = Math.min(MAX_LEVEL, 1 + Math.floor(this.round * 0.8));
    const odds = TIER_ODDS[lvl - 1];
    const stacks: UnitStack[] = [];
    let spent = 0;
    let guard = 0;
    while (spent < budget && guard++ < 40) {
      let roll = r.range(0, 100);
      let tier = 1;
      for (let t = 0; t < 5; t++) { if (roll < odds[t]) { tier = t + 1; break; } roll -= odds[t]; }
      const pool = TIER_UNITS[tier];
      if (!pool.length) continue;
      const type = pool[r.int(0, pool.length - 1)];
      const star = r.range(0, 1) < Math.min(0.4, this.round * 0.05) ? 2 : 1;
      stacks.push({ type, count: 1, star });
      spent += tier * star;
    }
    return stacks.length ? stacks : [{ type: "militia", count: 2, star: 1 }];
  }

  private livingFoes(): Opponent[] { return this.opponents.filter((o) => o.alive); }

  /** Resolve this round's fight, apply life damage and eliminations. */
  fight(): void {
    if (this.phase !== "shop") return;
    const foes = this.livingFoes();
    if (!foes.length) { this.outcome = "win"; this.phase = "over"; return; }
    const foe = foes[this.round % foes.length];
    const seed = this.rng.int(1, 1e9);
    const res = resolveBattle(this.boardStacks(), this.opponentStacks(seed), seed);
    const won = res.winner === "A";
    const dmg = won ? 0 : 3 + res.powerB * 2; // lose → take damage scaled by enemy survivors
    if (won) {
      // Damage the opponent we beat.
      foe.life -= 3 + res.powerA * 2;
      if (foe.life <= 0) { foe.life = 0; foe.alive = false; }
      this.streak = Math.max(1, this.streak + 1);
    } else {
      this.life -= dmg;
      this.streak = Math.min(-1, this.streak - 1);
    }
    // The other living foes skirmish among themselves so the lobby thins out too.
    this.thinTheHerd();
    this.lastResult = { won, foe: foe.name, youLeft: res.survivorsA, foeLeft: res.survivorsB, dmg };

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
  standings(): { name: string; life: number; alive: boolean; you: boolean }[] {
    const all = [
      { name: "You", life: this.life, alive: this.life > 0, you: true },
      ...this.opponents.map((o) => ({ name: o.name, life: o.life, alive: o.alive, you: false })),
    ];
    return all.sort((a, b) => Number(b.alive) - Number(a.alive) || b.life - a.life);
  }

  /** Active synergies on your currently-deployed warband (for the screen). */
  activeTraits(): ActiveTrait[] {
    return activeTraits([...new Set(this.boardStacks().map((s) => s.type))]);
  }

  /** Placement (1 = winner) once the run is over. */
  placement(): number {
    return 1 + this.livingFoes().length;
  }
}
