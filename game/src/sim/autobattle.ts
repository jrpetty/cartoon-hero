// The deterministic heart of the auto-battler ("Warband Tactics") mode: take two
// army compositions, simulate the fight headlessly on a small arena, and report
// who won and what survived. The existing combat sim does all the work — units
// auto-acquire and fight on their own — so a TFT-style round is just two stacks
// dropped in and ticked to a conclusion. Pure + deterministic (seeded), so it's
// fully testable and replay-safe.

import { World } from "./world";
import { Kind, Stance, Team, EntityId } from "./types";
import { generateMap } from "../maps/generator";
import { UNITS } from "../content/units";
import { SIM_HZ } from "../content/balance";
import { activeTraits, applyBuff, traitsOf, ActiveTrait } from "./traits";
import { applyItems } from "./items";

export interface UnitStack {
  type: string;
  count: number;
  star?: number; // 1..3 — TFT-style upgrade; scales HP & attack
}

/** A single placed unit (player boards expand to these — carries items + a cell). */
export interface ArenaUnit {
  type: string;
  star?: number;
  items?: string[];
  col?: number; // board column 0..9 (0..4 = player half, 5..9 = enemy half)
  row?: number; // board row 0..9
}

// The placement board is a 10×10 grid; each side owns a 5×10 half.
export const GRID_COLS = 10;
export const GRID_ROWS = 10;
export const GRID_CELL = 40; // world units per cell

/** Centre of board cell (col,row) in world space, around the arena centre. */
export function cellToWorld(cx: number, cy: number, col: number, row: number): { x: number; y: number } {
  const left = cx - (GRID_COLS / 2) * GRID_CELL;
  const top = cy - (GRID_ROWS / 2) * GRID_CELL;
  return { x: left + (col + 0.5) * GRID_CELL, y: top + (row + 0.5) * GRID_CELL };
}

/** Expand stacks (count>1) and pass through single units into a flat unit list. */
function normalize(list: (UnitStack | ArenaUnit)[]): ArenaUnit[] {
  const out: ArenaUnit[] = [];
  for (const u of list) {
    const n = (u as UnitStack).count ?? 1;
    const a = u as ArenaUnit;
    for (let k = 0; k < n; k++) out.push({ type: u.type, star: u.star, items: a.items, col: a.col, row: a.row });
  }
  return out;
}

export interface BattleResult {
  winner: "A" | "B" | "draw";
  survivorsA: number;
  survivorsB: number;
  /** Total star-weighted unit count left standing (drives life damage). */
  powerA: number;
  powerB: number;
  ticks: number;
}

const STAR_MULT = [1, 1, 1.8, 3.2]; // index by star (1..3)

type PosFn = (i: number, n: number, cx: number, cy: number, side: number) => { x: number; y: number };

function spawnArmy(w: World, units: ArenaUnit[], team: Team, side: number, posFn?: PosFn): EntityId[] {
  const cx = w.worldW / 2;
  const cy = w.worldH / 2;
  const n = units.length;
  const ids: EntityId[] = [];
  // Active synergies for this side's composition (by distinct deployed types).
  const traits = activeTraits([...new Set(units.map((u) => u.type))]);
  const buffByType = new Map<string, ActiveTrait[]>();
  units.forEach((au, i) => {
    if (!UNITS[au.type]) return;
    const star = Math.max(1, Math.min(3, au.star ?? 1));
    const mult = STAR_MULT[star];
    let myTraits = buffByType.get(au.type);
    if (!myTraits) {
      const mine = new Set(traitsOf(au.type).map((t) => t.id));
      myTraits = traits.filter((at) => mine.has(at.trait.id) && at.tier);
      buffByType.set(au.type, myTraits);
    }
    const pos = (au.col != null && au.row != null)
      ? cellToWorld(cx, cy, au.col, au.row) // explicit board placement wins
      : posFn
        ? posFn(i, n, cx, cy, side)
        : { x: cx + side * 170 + (i % 4) * 16 * side, y: cy - 50 + Math.floor(i / 4) * 18 };
    const u = w.spawnUnit(team, au.type, pos.x, pos.y);
    if (mult !== 1) {
      u.maxHp = Math.round(u.maxHp * mult);
      u.hp = u.maxHp;
      u.attack = Math.round(u.attack * mult);
    }
    for (const at of myTraits) if (at.tier) applyBuff(u, at.tier.buff);
    if (au.items?.length) applyItems(u, au.items);
    u.stance = Stance.Aggressive; // hunt — no economy, just fight
    u.variantRarity = star - 1; // a visual tier glow if rendered
    ids.push(u.id);
  });
  return ids;
}

const alivePower = (w: World, team: Team): { count: number; power: number } => {
  let count = 0;
  let power = 0;
  for (const e of w.entities) {
    if (!e.alive || e.team !== team || e.kind !== Kind.Unit || e.type === "villager") continue;
    count++;
    power += 1 + (e.variantRarity ?? 0); // star-weighted
  }
  return { count, power };
};

export function resolveBattle(
  a: (UnitStack | ArenaUnit)[], b: (UnitStack | ArenaUnit)[], seed = 1, maxSeconds = 40,
): BattleResult {
  const map = generateMap("open_plains", seed, 2);
  const w = new World(seed);
  w.init(map, [{}, {}], [1, 1], [0, 1]); // two hostile sides
  const cx = w.worldW / 2;
  const cy = w.worldH / 2;

  const idsA = spawnArmy(w, normalize(a), Team.Player, -1);
  const idsB = spawnArmy(w, normalize(b), Team.Enemy, 1);
  // March both lines into the centre so they actually clash.
  w.issueFormationMove(idsA, cx + 30, cy, false, true);
  w.issueFormationMove(idsB, cx - 30, cy, false, true);

  const maxTicks = SIM_HZ * maxSeconds;
  let t = 0;
  for (; t < maxTicks; t++) {
    w.tick();
    w.drainEvents();
    if (t % 10 === 0) {
      if (alivePower(w, Team.Player).count === 0 || alivePower(w, Team.Enemy).count === 0) break;
    }
  }

  const A = alivePower(w, Team.Player);
  const B = alivePower(w, Team.Enemy);
  let winner: "A" | "B" | "draw";
  if (A.count > 0 && B.count === 0) winner = "A";
  else if (B.count > 0 && A.count === 0) winner = "B";
  else if (A.power === B.power) winner = "draw";
  else winner = A.power > B.power ? "A" : "B";

  return { winner, survivorsA: A.count, survivorsB: B.count, powerA: A.power, powerB: B.power, ticks: t };
}

// Lays a warband out in a clean 2-D grid on its own half of the arena (a
// TFT-style board): one unit per cell, generously spaced so nothing overlaps,
// the front rank nearest the centre line. Side −1 (player) sits left, side +1
// (enemy) right, both facing the middle.
const ROW_GAP = 48;
const COL_GAP = 48;
const FRONT_GAP = 66; // centre line → front rank
const BOARD_LAYOUT: PosFn = (i, n, cx, cy, side) => {
  const rows = Math.min(4, Math.max(1, Math.ceil(n / 3))); // up to 4 ranks deep
  const row = i % rows;
  const col = Math.floor(i / rows);
  return {
    x: cx + side * (FRONT_GAP + col * COL_GAP),
    y: cy - ((rows - 1) / 2) * ROW_GAP + row * ROW_GAP,
  };
};

/**
 * A watchable version of {@link resolveBattle}: same deterministic setup, but
 * the world is held so a UI can render it and advance it tick-by-tick. The
 * outcome is identical regardless of how the steps are batched (tick() is pure).
 */
export class LiveBattle {
  readonly world: World;
  readonly cx: number;
  readonly cy: number;
  ticks = 0;
  started = false;
  done = false;
  private idsA: EntityId[];
  private idsB: EntityId[];
  private maxTicks: number;
  private _result: BattleResult | null = null;

  constructor(a: (UnitStack | ArenaUnit)[], b: (UnitStack | ArenaUnit)[], seed = 1, maxSeconds = 30) {
    const map = generateMap("open_plains", seed, 2);
    this.world = new World(seed);
    this.world.init(map, [{}, {}], [1, 1], [0, 1]);
    this.cx = this.world.worldW / 2;
    this.cy = this.world.worldH / 2;
    this.idsA = spawnArmy(this.world, normalize(a), Team.Player, -1, BOARD_LAYOUT);
    this.idsB = spawnArmy(this.world, normalize(b), Team.Enemy, 1, BOARD_LAYOUT);
    this.maxTicks = SIM_HZ * maxSeconds;
  }

  /**
   * The fight begins: each unit attack-moves straight across to the centre line
   * at its own rank height, so opposing ranks meet head-on in a spread front
   * line instead of funnelling into one overlapping pile.
   */
  begin(): void {
    if (this.started) return;
    this.started = true;
    for (const id of this.idsA) { const e = this.world.byId.get(id); if (e) this.world.issueMove([id], this.cx + 24, e.y, false, true); }
    for (const id of this.idsB) { const e = this.world.byId.get(id); if (e) this.world.issueMove([id], this.cx - 24, e.y, false, true); }
  }

  /** Advance the sim a few ticks; stops automatically when one side is wiped. */
  step(ticks = 1): void {
    if (!this.started || this.done) return;
    for (let k = 0; k < ticks && !this.done; k++) {
      this.world.tick();
      this.world.drainEvents();
      this.ticks++;
      if (this.ticks % 5 === 0 || this.ticks >= this.maxTicks) {
        const a = alivePower(this.world, Team.Player).count;
        const bb = alivePower(this.world, Team.Enemy).count;
        if (a === 0 || bb === 0 || this.ticks >= this.maxTicks) this.finish();
      }
    }
  }

  private finish(): void {
    if (this._result) return;
    const A = alivePower(this.world, Team.Player);
    const B = alivePower(this.world, Team.Enemy);
    let winner: "A" | "B" | "draw";
    if (A.count > 0 && B.count === 0) winner = "A";
    else if (B.count > 0 && A.count === 0) winner = "B";
    else if (A.power === B.power) winner = "draw";
    else winner = A.power > B.power ? "A" : "B";
    this._result = { winner, survivorsA: A.count, survivorsB: B.count, powerA: A.power, powerB: B.power, ticks: this.ticks };
    this.done = true;
  }

  /** The battle result, forcing a verdict if it hasn't ended on its own yet. */
  result(): BattleResult {
    if (!this._result) this.finish();
    return this._result!;
  }
}
