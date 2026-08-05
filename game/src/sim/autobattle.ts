// The deterministic heart of the auto-battler ("Warband Tactics") mode: take two
// army compositions, simulate the fight headlessly on a small arena, and report
// who won and what survived. The existing combat sim does all the work — units
// auto-acquire and fight on their own — so a TFT-style round is just two stacks
// dropped in and ticked to a conclusion. Pure + deterministic (seeded), so it's
// fully testable and replay-safe.

import { World, WorldEvent } from "./world";
import { Kind, Stance, Team, EntityId } from "./types";
import { generateMap } from "../maps/generator";
import { UNITS } from "../content/units";
import { SIM_HZ, SIM_DT } from "../content/balance";
import { activeTraits, applyBuff, traitsOf, ActiveTrait, Buff } from "./traits";
import { applyItems } from "./items";
import { ABILITIES } from "../content/abilities";
import { styleOf } from "../content/battle_styles";
import { TerrainFeature, terrainAt } from "./terrain";

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

/**
 * Warband-wide modifiers for one side — how a run's augments reach the fight.
 * `buff` hits every unit; `traitBonus` adds virtual synergy counts (banners).
 */
export interface SideOpts {
  buff?: Buff;
  traitBonus?: Record<string, number>;
  /** Buffs applied to units belonging to a trait — how conditions bite. */
  traitBuffs?: Record<string, Buff>;
  /** The round's board terrain, applied by the cell a unit stands on. */
  terrain?: TerrainFeature[];
}

/** Merge a side's own modifiers with the battlefield ones that hit everybody. */
export function mergeSideOpts(own?: SideOpts, field?: SideOpts): SideOpts | undefined {
  if (!field) return own;
  if (!own) return field;
  const buff: Buff = { ...(own.buff ?? {}) };
  for (const k of ["armor", "atk", "atkPct", "hp", "hpPct", "speedPct"] as const) {
    if (field.buff?.[k]) buff[k] = (buff[k] ?? 0) + field.buff[k]!;
  }
  const traitBuffs: Record<string, Buff> = { ...(own.traitBuffs ?? {}) };
  for (const [id, b] of Object.entries(field.traitBuffs ?? {})) {
    const cur: Buff = { ...(traitBuffs[id] ?? {}) };
    for (const k of ["armor", "atk", "atkPct", "hp", "hpPct", "speedPct"] as const) {
      if (b[k]) cur[k] = (cur[k] ?? 0) + b[k]!;
    }
    traitBuffs[id] = cur;
  }
  return {
    buff: Object.keys(buff).length ? buff : undefined,
    traitBonus: own.traitBonus ?? field.traitBonus,
    traitBuffs: Object.keys(traitBuffs).length ? traitBuffs : undefined,
    terrain: own.terrain ?? field.terrain,
  };
}

function spawnArmy(w: World, units: ArenaUnit[], team: Team, side: number, posFn?: PosFn, opts?: SideOpts): EntityId[] {
  const cx = w.worldW / 2;
  const cy = w.worldH / 2;
  const n = units.length;
  const ids: EntityId[] = [];
  // Active synergies for this side's composition (by distinct deployed types).
  const traits = activeTraits([...new Set(units.map((u) => u.type))], opts?.traitBonus);
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
    if (opts?.buff) applyBuff(u, opts.buff); // run-wide augment buffs, last
    if (opts?.traitBuffs) { // battlefield conditions, by trait membership
      for (const t of traitsOf(au.type)) {
        const b = opts.traitBuffs[t.id];
        if (b) applyBuff(u, b);
      }
    }
    // Ground effects, from whichever cell this unit was placed on.
    if (opts?.terrain && au.col != null && au.row != null) {
      const ground = terrainAt(opts.terrain, au.col, au.row);
      if (ground?.rangePct) u.range = Math.round(u.range * (1 + ground.rangePct / 100));
      if (ground?.speedPct) u.speed = Math.round(u.speed * (1 + ground.speedPct / 100));
    }
    u.stance = Stance.Aggressive; // hunt — no economy, just fight
    u.variantRarity = star - 1; // a visual tier glow if rendered
    ids.push(u.id);
  });
  return ids;
}

// ---- opening orders: how each battle style enters the fight -----------------
//
// Depths are world-x offsets from the centre line, measured *toward* the enemy.
// The board is 10 cells of GRID_CELL, so ±180 is the far back rank.
const VANGUARD_DEPTH = 10;  // plant on the line
const LINE_DEPTH = 44;      // push a cell into them
const FLANK_DEPTH = 130;    // come in well past their front
const INFILTRATE_X = 150;   // land among their back ranks

/**
 * Send a side into the fight according to each unit's battle style. Shared by
 * the headless resolver and the watchable battle so both agree exactly.
 *
 * `side` is −1 for the left (player) army and +1 for the right, so the enemy
 * always lies in the `-side` direction.
 *
 * Returns the Infiltrators' jumps as (from → to) pairs so a UI can draw them.
 */
export function openingOrders(
  w: World, ids: EntityId[], side: number, cx: number, cy: number,
): { x0: number; y0: number; x1: number; y1: number; team: Team }[] {
  const dir = -side; // +1 when the enemy is to the right
  const leaps: { x0: number; y0: number; x1: number; y1: number; team: Team }[] = [];
  const halfH = (GRID_ROWS / 2) * GRID_CELL;
  for (const id of ids) {
    const e = w.byId.get(id);
    if (!e || !e.alive) continue;
    switch (styleOf(e.type)) {
      case "artillery":
        // Stands its ground and shoots from where you placed it. Deliberately
        // no creep-into-range: the enemy closes the distance for you, and
        // stepping forward only walks the crew into archer fire.
        break;
      case "vanguard":
        w.issueMove([id], cx + dir * VANGUARD_DEPTH, e.y, false, true);
        break;
      case "flanker": {
        // Sweep to the nearer edge, then drive in behind their front rank.
        const edgeY = e.y < cy ? cy - halfH + GRID_CELL * 0.5 : cy + halfH - GRID_CELL * 0.5;
        w.issueMove([id], e.x + dir * GRID_CELL, edgeY, false, true);
        w.issueMove([id], cx + dir * FLANK_DEPTH, edgeY, true, true); // queued second leg
        break;
      }
      case "infiltrator": {
        // Not a march — they're simply behind you when it starts.
        const x0 = e.x, y0 = e.y;
        e.x = cx + dir * INFILTRATE_X;
        e.y = Math.max(cy - halfH + 8, Math.min(cy + halfH - 8, y0));
        leaps.push({ x0, y0, x1: e.x, y1: e.y, team: e.team });
        // Then fight their way forward, into the backline they landed on.
        w.issueMove([id], cx + dir * 70, e.y, false, true);
        break;
      }
      default:
        w.issueMove([id], cx + dir * LINE_DEPTH, e.y, false, true);
    }
  }
  return leaps;
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
  optsA?: SideOpts, field?: SideOpts,
): BattleResult {
  const map = generateMap("open_plains", seed, 2);
  const w = new World(seed);
  w.init(map, [{}, {}], [1, 1], [0, 1]); // two hostile sides
  const cx = w.worldW / 2;
  const cy = w.worldH / 2;

  const idsA = spawnArmy(w, normalize(a), Team.Player, -1, undefined, mergeSideOpts(optsA, field));
  const idsB = spawnArmy(w, normalize(b), Team.Enemy, 1, undefined, field);
  // Each side enters by its units' battle styles — the same orders the watched
  // fight issues, so headless and live resolve identically.
  openingOrders(w, idsA, -1, cx, cy);
  openingOrders(w, idsB, 1, cx, cy);

  const maxTicks = SIM_HZ * maxSeconds;
  const mana = new Map<EntityId, number>();
  let t = 0;
  for (; t < maxTicks; t++) {
    w.tick();
    chargeAbilities(w, mana);
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
 * Units with a signature ability build "mana" as the fight goes and cast it
 * when full — faster for higher-star units. After a cast the long sim cooldown
 * is cleared so the charge meter (not the cooldown) paces repeat casts.
 *
 * Shared by both resolution paths on purpose. This used to live only on
 * LiveBattle, which meant the headless resolver never cast a single ability:
 * the very same matchup came out differently depending on whether you watched
 * it, and every balance measurement taken through resolveBattle silently
 * excluded abilities altogether.
 */
export function chargeAbilities(world: World, mana: Map<EntityId, number>): void {
  for (const e of world.entities) {
    if (!e.alive || e.kind !== Kind.Unit || !ABILITIES[e.type] || e.type === "villager") continue;
    if (e.abilityActive > 0) continue; // mid-cast: let the buff run before recharging
    const star = (e.variantRarity ?? 0) + 1;
    const chargeTime = Math.max(2.4, 5 - (star - 1) * 1.2); // 1★ 5s · 2★ 3.8s · 3★ 2.6s
    let m = (mana.get(e.id) ?? 0) + SIM_DT / chargeTime;
    if (m >= 1) {
      world.useAbility([e.id]);
      e.abilityCooldown = 0; // let mana, not the 20s+ cooldown, gate the next cast
      m = 0;
    }
    mana.set(e.id, m);
  }
}

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
  /** Combat events from the last step(s) — drained by the UI for hit/death FX. */
  fxEvents: WorldEvent[] = [];
  private idsA: EntityId[];
  private idsB: EntityId[];
  private maxTicks: number;
  private _result: BattleResult | null = null;
  private mana = new Map<EntityId, number>(); // 0..1 ability charge per unit

  constructor(a: (UnitStack | ArenaUnit)[], b: (UnitStack | ArenaUnit)[], seed = 1, maxSeconds = 30,
    optsA?: SideOpts, field?: SideOpts) {
    const map = generateMap("open_plains", seed, 2);
    this.world = new World(seed);
    this.world.init(map, [{}, {}], [1, 1], [0, 1]);
    this.cx = this.world.worldW / 2;
    this.cy = this.world.worldH / 2;
    this.idsA = spawnArmy(this.world, normalize(a), Team.Player, -1, BOARD_LAYOUT, mergeSideOpts(optsA, field));
    this.idsB = spawnArmy(this.world, normalize(b), Team.Enemy, 1, BOARD_LAYOUT, field);
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
    const leaps = [
      ...openingOrders(this.world, this.idsA, -1, this.cx, this.cy),
      ...openingOrders(this.world, this.idsB, 1, this.cx, this.cy),
    ];
    // Surface each Infiltrator's jump so the screen can draw the pounce.
    for (const l of leaps) {
      this.fxEvents.push({ kind: "leap", x: l.x0, y: l.y0, team: l.team, data: `${l.x1},${l.y1}` });
    }
  }

  /** Advance the sim a few ticks; stops automatically when one side is wiped. */
  step(ticks = 1): void {
    if (!this.started || this.done) return;
    for (let k = 0; k < ticks && !this.done; k++) {
      this.world.tick();
      this.tickAbilities(); // charge + auto-cast signature abilities (per tick → deterministic)
      for (const ev of this.world.drainEvents()) { if (this.fxEvents.length < 256) this.fxEvents.push(ev); }
      this.ticks++;
      if (this.ticks % 5 === 0 || this.ticks >= this.maxTicks) {
        const a = alivePower(this.world, Team.Player).count;
        const bb = alivePower(this.world, Team.Enemy).count;
        if (a === 0 || bb === 0 || this.ticks >= this.maxTicks) this.finish();
      }
    }
  }

  private tickAbilities() { chargeAbilities(this.world, this.mana); }

  /** Current ability charge (0..1) for a unit, for the charge-bar render. */
  chargeOf(id: EntityId): number { return this.mana.get(id) ?? 0; }

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
