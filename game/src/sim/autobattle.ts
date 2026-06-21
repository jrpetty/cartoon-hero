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

export interface UnitStack {
  type: string;
  count: number;
  star?: number; // 1..3 — TFT-style upgrade; scales HP & attack
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

function spawnArmy(w: World, stacks: UnitStack[], team: Team, side: number): EntityId[] {
  const cx = w.worldW / 2;
  const cy = w.worldH / 2;
  const ids: EntityId[] = [];
  // Active synergies for this side's composition (by distinct deployed types).
  const traits = activeTraits([...new Set(stacks.map((s) => s.type))]);
  const buffByType = new Map<string, ActiveTrait[]>();
  let i = 0;
  for (const s of stacks) {
    if (!UNITS[s.type]) continue;
    const star = Math.max(1, Math.min(3, s.star ?? 1));
    const mult = STAR_MULT[star];
    // Which active traits buff this unit type.
    let myTraits = buffByType.get(s.type);
    if (!myTraits) {
      const mine = new Set(traitsOf(s.type).map((t) => t.id));
      myTraits = traits.filter((at) => mine.has(at.trait.id) && at.tier);
      buffByType.set(s.type, myTraits);
    }
    for (let k = 0; k < s.count; k++) {
      const x = cx + side * 170 + (i % 4) * 16 * side;
      const y = cy - 50 + Math.floor(i / 4) * 18;
      const u = w.spawnUnit(team, s.type, x, y);
      if (mult !== 1) {
        u.maxHp = Math.round(u.maxHp * mult);
        u.hp = u.maxHp;
        u.attack = Math.round(u.attack * mult);
      }
      for (const at of myTraits) if (at.tier) applyBuff(u, at.tier.buff);
      u.stance = Stance.Aggressive; // hunt — no economy, just fight
      u.variantRarity = star - 1; // a visual tier glow if rendered
      ids.push(u.id);
      i++;
    }
  }
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

export function resolveBattle(a: UnitStack[], b: UnitStack[], seed = 1, maxSeconds = 40): BattleResult {
  const map = generateMap("open_plains", seed, 2);
  const w = new World(seed);
  w.init(map, [{}, {}], [1, 1], [0, 1]); // two hostile sides
  const cx = w.worldW / 2;
  const cy = w.worldH / 2;

  const idsA = spawnArmy(w, a, Team.Player, -1);
  const idsB = spawnArmy(w, b, Team.Enemy, 1);
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
