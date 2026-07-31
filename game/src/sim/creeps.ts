// Warband Tactics monster camps — the PvE rounds. Like TFT's minion/krug/wolf
// rounds, these break up the PvP ladder: no player life is on the line, the
// camp is visible while you set up, and beating it drops relics and gold. They
// are also the run's opening round, so you always start with a fight you can
// see and a relic to build around.

import { ArenaUnit } from "./autobattle";

export interface CreepCamp {
  id: string;
  name: string;
  blurb: string;
  /** Relics dropped for a win. */
  relics: number;
  /** Gold dropped for a win. */
  gold: number;
  /** Life lost if the camp beats you (much gentler than a player round). */
  bite: number;
  units: { type: string; star: number; count: number }[];
}

/** The scripted camps, in the order they appear. */
const CAMPS: CreepCamp[] = [
  {
    id: "wolves", name: "Wolf Pack", blurb: "A lean opening scrap — win it for your first relic.",
    relics: 1, gold: 2, bite: 2,
    units: [{ type: "militia", star: 1, count: 2 }],
  },
  {
    id: "bandits", name: "Bandit Camp", blurb: "Raiders with a bowman covering them.",
    relics: 1, gold: 3, bite: 3,
    units: [{ type: "raider", star: 1, count: 3 }, { type: "archer", star: 1, count: 1 }],
  },
  {
    id: "mercs", name: "Mercenary Band", blurb: "Seasoned swords — bring a real board.",
    relics: 2, gold: 4, bite: 4,
    units: [{ type: "knight", star: 2, count: 2 }, { type: "crossbow", star: 1, count: 2 }],
  },
  {
    id: "guard", name: "Warlord's Guard", blurb: "Elites in full harness. The drop is worth it.",
    relics: 2, gold: 6, bite: 5,
    units: [{ type: "twohand", star: 2, count: 2 }, { type: "handcannon", star: 2, count: 1 }, { type: "knight", star: 2, count: 1 }],
  },
  {
    id: "legion", name: "Ancient Legion", blurb: "A dead empire's honour guard, still holding the line.",
    relics: 3, gold: 8, bite: 6,
    units: [{ type: "hero", star: 2, count: 1 }, { type: "twohand", star: 3, count: 1 }, { type: "pikeman", star: 2, count: 3 }],
  },
];

/** Camp rounds: the opener, then one every fifth round. */
export function isCreepRound(round: number): boolean {
  return round === 1 || round % 5 === 1;
}

/** Which camp a round faces (later rounds repeat the last camp, scaled up). */
export function campForRound(round: number): CreepCamp {
  const idx = Math.max(0, Math.floor((round - 1) / 5));
  if (idx < CAMPS.length) return CAMPS[idx];
  // Past the scripted list, keep the toughest camp and grow it.
  const base = CAMPS[CAMPS.length - 1];
  const extra = idx - CAMPS.length + 1;
  return {
    ...base,
    name: `${base.name} +${extra}`,
    relics: base.relics + Math.floor(extra / 2),
    gold: base.gold + extra * 2,
    bite: base.bite + extra,
    units: base.units.map((u) => ({ ...u, count: u.count + Math.floor(extra / 2) })),
  };
}

// Camps stand on the enemy half, front rank first, rows from the middle out.
const ROW_ORDER = [4, 5, 3, 6, 2, 7, 1, 8, 0, 9];
const CELLS: { c: number; r: number }[] = [];
for (const c of [5, 6, 7, 8, 9]) for (const r of ROW_ORDER) CELLS.push({ c, r });

/** Expand a camp into placed arena units on the enemy half. */
export function campBoard(camp: CreepCamp): ArenaUnit[] {
  const out: ArenaUnit[] = [];
  for (const u of camp.units) {
    for (let k = 0; k < u.count; k++) {
      const cell = CELLS[Math.min(out.length, CELLS.length - 1)];
      out.push({ type: u.type, star: u.star, items: [], col: cell.c, row: cell.r });
    }
  }
  return out;
}
