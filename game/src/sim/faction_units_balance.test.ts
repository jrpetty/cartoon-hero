import { describe, expect, it } from "vitest";
import { resolveBattle, ArenaUnit } from "./autobattle";
import { UNITS } from "../content/units";

/**
 * The faction and Oath units against the shared army, at equal budget.
 *
 * Each one is meant to be a *choice* — better at its job, worse elsewhere, and
 * priced for it — so none may be unbeatable or dead weight against the roster
 * everyone else fields, and no faction replacement may simply outclass the
 * unit it stands in for.
 */

const ROSTER = ["militia", "spearman", "pikeman", "twohand", "archer", "crossbow", "skirmisher", "javelin",
  "horseman", "knight", "handcannon", "catapult"];
const NEW = ["legionary", "samurai", "horse_archer", "pulse_trooper", "skimmer", "yeoman",
  "sworn_blade", "lancer", "ranger", "halberdier", "royal_guard"];
const BUDGET = 600;
const SEEDS = [1, 2, 3];
const ROWS = [4, 5, 3, 6, 2, 7, 1, 8, 0, 9];
const costOf = (t: string) => { const c = UNITS[t].cost; return c.food + c.wood + c.gold; };

function army(type: string, side: 1 | -1): ArenaUnit[] {
  const n = Math.max(1, Math.min(12, Math.round(BUDGET / costOf(type))));
  return Array.from({ length: n }, (_, i) => ({
    type, star: 1, items: [],
    col: side === -1 ? 4 - Math.floor(i / ROWS.length) : 5 + Math.floor(i / ROWS.length),
    row: ROWS[i % ROWS.length],
  }));
}

/** Share of fights `a` wins, fighting from both sides of the board. */
function rate(a: string, others: string[]): number {
  let wins = 0, games = 0;
  for (const b of others) for (const seed of SEEDS) {
    if (resolveBattle(army(a, -1), army(b, 1), seed).winner === "A") wins++;
    if (resolveBattle(army(b, -1), army(a, 1), seed).winner === "B") wins++;
    games += 2;
  }
  return wins / games;
}

describe("Faction and Oath units", () => {
  it("are neither unbeatable nor dead weight against the shared army", () => {
    const rates = NEW.map((u) => [u, rate(u, ROSTER)] as const).sort((x, y) => y[1] - x[1]);
    const report = rates.map(([u, r]) => `${u} ${(r * 100).toFixed(0)}%`).join(", ");
    console.log("UNITRATES " + report);
    for (const [u, r] of rates) {
      expect(r, `${u} — ${report}`).toBeLessThan(0.85);
      expect(r, `${u} — ${report}`).toBeGreaterThan(0.15);
    }
  }, 180000);

  it("don't simply outclass the unit they replace", () => {
    for (const [own, base] of [["legionary", "militia"], ["samurai", "twohand"], ["pulse_trooper", "crossbow"], ["skimmer", "horseman"]]) {
      const head = rate(own, [base]);
      expect(head, `${own} beats ${base} ${Math.round(head * 100)}% of the time at equal cost`).toBeLessThanOrEqual(5 / 6);
    }
  }, 60000);
});
