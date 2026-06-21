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

/** A single placed unit (player boards expand to these — carries items). */
export interface ArenaUnit {
  type: string;
  star?: number;
  items?: string[];
}

/** Expand stacks (count>1) and pass through single units into a flat unit list. */
function normalize(list: (UnitStack | ArenaUnit)[]): ArenaUnit[] {
  const out: ArenaUnit[] = [];
  for (const u of list) {
    const n = (u as UnitStack).count ?? 1;
    for (let k = 0; k < n; k++) out.push({ type: u.type, star: u.star, items: (u as ArenaUnit).items });
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

function spawnArmy(w: World, units: ArenaUnit[], team: Team, side: number): EntityId[] {
  const cx = w.worldW / 2;
  const cy = w.worldH / 2;
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
    const x = cx + side * 170 + (i % 4) * 16 * side;
    const y = cy - 50 + Math.floor(i / 4) * 18;
    const u = w.spawnUnit(team, au.type, x, y);
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
