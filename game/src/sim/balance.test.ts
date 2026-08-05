import { describe, expect, it } from "vitest";
import { resolveBattle, ArenaUnit } from "./autobattle";
import { UNITS } from "../content/units";

// Equal-budget armies, so this measures the unit rather than what it costs.
// The board layout is fixed and the fights are seeded, so the whole matrix is
// deterministic — a balance change shows up as a moved number, not as noise.
const ROSTER = ["militia", "spearman", "pikeman", "twohand", "shieldbearer", "berserker",
  "archer", "crossbow", "longbow", "skirmisher", "javelin",
  "horseman", "knight", "cataphract", "handcannon", "battlemage", "catapult", "bombard"];
const BUDGET = 600;
const SEEDS = [1, 2, 3];
const ROWS = [4, 5, 3, 6, 2, 7, 1, 8, 0, 9];

const costOf = (t: string) => {
  const c = UNITS[t].cost;
  return c.food + c.wood + c.gold;
};

/** Field roughly `BUDGET` worth of one unit type, in ranks on its own half. */
function army(type: string, side: 1 | -1): ArenaUnit[] {
  const n = Math.max(1, Math.min(12, Math.round(BUDGET / costOf(type))));
  return Array.from({ length: n }, (_, i) => ({
    type, star: 1, items: [],
    col: side === -1 ? 4 - Math.floor(i / ROWS.length) : 5 + Math.floor(i / ROWS.length),
    row: ROWS[i % ROWS.length],
  }));
}

/** How often `a` beats `b` head to head, 0..1. */
function winRate(a: string, b: string): number {
  let wins = 0;
  for (const seed of SEEDS) if (resolveBattle(army(a, -1), army(b, 1), seed).winner === "A") wins++;
  return wins / SEEDS.length;
}

/** Win rate for every unit against the whole roster. */
function matrix(): Map<string, number> {
  const wins = new Map<string, number>();
  const games = new Map<string, number>();
  for (const t of ROSTER) { wins.set(t, 0); games.set(t, 0); }
  for (const a of ROSTER) {
    for (const b of ROSTER) {
      if (a === b) continue;
      for (const seed of SEEDS) {
        const r = resolveBattle(army(a, -1), army(b, 1), seed);
        games.set(a, games.get(a)! + 1);
        games.set(b, games.get(b)! + 1);
        if (r.winner === "A") wins.set(a, wins.get(a)! + 1);
        else if (r.winner === "B") wins.set(b, wins.get(b)! + 1);
      }
    }
  }
  const rate = new Map<string, number>();
  for (const t of ROSTER) rate.set(t, wins.get(t)! / games.get(t)!);
  return rate;
}

describe("Unit balance", () => {
  it("has no unbeatable unit and no useless one", () => {
    const rate = matrix();
    const sorted = [...rate.entries()].sort((x, y) => y[1] - x[1]);
    const report = sorted.map(([t, r]) => `${t} ${(r * 100).toFixed(0)}%`).join(", ");

    // This measures pure comp vs pure comp, and nothing in the sim kites, so
    // melee structurally clusters high and ranged low. The bound is therefore
    // "nothing is unbeatable and nothing is dead weight", not "everything is
    // equal" — chasing parity here would mean distorting unit costs to satisfy
    // a metric that mixed, screened armies don't reflect.
    //
    // Before this pass: Two-Handed Swordsman 100% (the best army HP *and* the
    // best army DPS in its bracket, beating even its counters) and Archer 0.9%
    // (its +3 vs infantry was noise beside the +25 the spear line gets vs
    // cavalry). A 99-point spread.
    expect(sorted[0][1], report).toBeLessThan(0.82);
    expect(sorted[sorted.length - 1][1], report).toBeGreaterThan(0.18);
    // And the table as a whole stays inside a sane band.
    expect(sorted[0][1] - sorted[sorted.length - 1][1], report).toBeLessThan(0.65);
  }, 120000);

  it("keeps the counter triangle pointing the right way", () => {
    // Spears stop cavalry, cavalry runs down archers, skirmishers out-trade
    // archers. Each leg is checked against the unit it is meant to answer.
    expect(winRate("spearman", "knight")).toBeGreaterThan(0.5);
    expect(winRate("pikeman", "knight")).toBeGreaterThan(0.5);
    expect(winRate("knight", "archer")).toBeGreaterThan(0.5);
    expect(winRate("horseman", "crossbow")).toBeGreaterThan(0.5);
    expect(winRate("skirmisher", "archer")).toBeGreaterThan(0.5);
  }, 60000);

  it("archers answer infantry from behind a screen, not in a brawl", () => {
    // The README's "archers melt infantry" is a claim about a *line*, not a
    // duel. Nothing in this sim kites — units close and trade — so massed
    // archers lose a head-on fight with massed infantry no matter how the
    // bonus is tuned, and pushing the bonus far enough to change that also
    // lets one archer beat one swordsman, which is worse. What the bonus
    // genuinely buys is this: put a screen in front and the archers win.
    const screened = [
      ...army("archer", -1).slice(0, 6).map((u) => ({ ...u, col: 2 })),
      { type: "spearman", star: 1, items: [] as string[], col: 4, row: 4 },
      { type: "spearman", star: 1, items: [] as string[], col: 4, row: 5 },
      { type: "spearman", star: 1, items: [] as string[], col: 4, row: 3 },
    ];
    const naked = army("archer", -1).slice(0, 9);
    const infantry = army("militia", 1).slice(0, 8);
    let screenedWins = 0, nakedWins = 0;
    for (const seed of [1, 2, 3, 4, 5]) {
      if (resolveBattle(screened, infantry, seed).winner === "A") screenedWins++;
      if (resolveBattle(naked, infantry, seed).winner === "A") nakedWins++;
    }
    expect(screenedWins).toBeGreaterThan(nakedWins);
  }, 60000);
});
