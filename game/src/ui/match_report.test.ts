import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { ui } from "./ui";
import {
  REPORT_TABS, drawReportKey, drawReportTab, isStandings, matchFormat, playerName, standings,
} from "./match_report";
import { World } from "../sim/world";
import { Kind, Team } from "../sim/types";
import { generateMap } from "../maps/generator";
import { emptyMatchReport, matchReport, MatchReport, PlayerReport } from "../sim/metrics";

/**
 * The end-of-match report used to fold every match into "you against them".
 * In a four-way free-for-all that added three rivals into one "Opponent" and
 * called it 1v3; in a 2v2 it hid whether you or your ally carried the game.
 */

function match(alliances: number[]): World {
  const n = alliances.length;
  const w = new World(5);
  w.init(generateMap("open_plains", 5, n, false, alliances), Array(n).fill({}), Array(n).fill(1), alliances);
  // Give every realm a different, recognisable ledger.
  for (let t = 0; t < n; t++) {
    const s = w.player(t as Team).stats;
    s.unitsKilled = 10 * (t + 1);
    s.gathered = 1000 * (t + 1);
    s.trainedByType = { spearman: t + 1, archer: 2 * t + 1, villager: 12 };
  }
  return w;
}

/** A report with `n` realms and plausible numbers, without running a match. */
function synthetic(alliances: number[], me = 0): MatchReport {
  const w = match(alliances);
  return matchReport(w, me as Team, "Highlands");
}

/** Every string drawn while `fn` runs, with where it was drawn. */
function drawn(fn: () => void): { s: string; y: number }[] {
  const out: { s: string; y: number }[] = [];
  const orig = ui.text.bind(ui);
  ui.text = ((s: string, x: number, y: number, o?: Parameters<typeof ui.text>[3]) => {
    out.push({ s, y: ui.ctx.getTransform().f + y });
    orig(s, x, y, o);
  }) as typeof ui.text;
  try { fn(); } finally { ui.text = orig; }
  return out;
}

function begin(W = 1600, H = 900) {
  const canvas = createCanvas(W, H);
  ui.begin(canvas.getContext("2d") as unknown as CanvasRenderingContext2D,
    { mx: 0, my: 0, clicked: false, rightClicked: false, alt: false });
}

describe("Every realm has its own line in the report", () => {
  it("reports each player of a free-for-all separately", () => {
    const r = synthetic([0, 1, 2, 3]);
    expect(r.players).toHaveLength(4);
    expect(r.players!.map((p) => p.unitsKilled)).toEqual([10, 20, 30, 40]);
    expect(r.players!.map((p) => p.relation)).toEqual(["you", "enemy", "enemy", "enemy"]);
    // The alliance totals are still there, and still agree.
    expect(r.foe.unitsKilled).toBe(20 + 30 + 40);
    expect(matchFormat(r)).toBe("4-player free-for-all");
  });

  it("knows who is on your side in a team game", () => {
    const r = synthetic([0, 0, 0, 0, 1, 1, 1, 1], 1);
    expect(r.players!.map((p) => p.relation)).toEqual(["ally", "you", "ally", "ally", "enemy", "enemy", "enemy", "enemy"]);
    expect(matchFormat(r)).toBe("4v4");
    expect(matchFormat(synthetic([0, 0, 1, 1]))).toBe("2v2");
    expect(matchFormat(synthetic([0, 1, 2, 3, 4, 5, 6, 7]))).toBe("8-player free-for-all");
    expect(matchFormat(synthetic([0, 1]))).toBe("1v1");
  });

  it("remembers when each realm fell", () => {
    const w = match([0, 1, 2, 3]);
    for (let i = 0; i < 20 * 5; i++) w.tick();
    for (const e of w.entities) if (e.team === 2 && (e.kind === Kind.Building || e.kind === Kind.Unit)) e.alive = false;
    const at = w.time;
    for (let i = 0; i < 20 * 3; i++) w.tick();
    const p = w.player(2 as Team);
    expect(p.defeated).toBe(true);
    expect(p.defeatedAt).toBeGreaterThanOrEqual(at);
    expect(p.defeatedAt).toBeLessThan(at + 3);
    expect(w.player(1 as Team).defeatedAt, "a standing realm has no time of defeat").toBe(-1);
    const r = matchReport(w, Team.Player, "x");
    expect(r.players![2].defeatedAt).toBe(p.defeatedAt);
  });
});

describe("Standings order", () => {
  const p = (team: number, over: Partial<PlayerReport>): PlayerReport => ({
    ...emptyMatchReport().you, team: team as Team, group: team, relation: team === 0 ? "you" : "enemy",
    horde: false, won: false, defeated: false, defeatedAt: -1, ...over,
  });

  it("puts the winner first and ranks the fallen by how long they lasted", () => {
    const { groups, ffa } = standings([
      p(0, { score: 900, defeated: true, defeatedAt: 300 }),
      p(1, { score: 100, won: true }),
      p(2, { score: 500, defeated: true, defeatedAt: 420 }),
      p(3, { score: 800, defeated: true, defeatedAt: 120 }),
    ]);
    expect(ffa).toBe(true);
    // Score doesn't decide who finished ahead: outlasting someone does.
    expect(groups.map((g) => g.members[0].team)).toEqual([1, 2, 0, 3]);
  });

  it("keeps allies together, best side first", () => {
    const { groups, ffa } = standings([
      p(0, { group: 0, score: 300 }), p(1, { group: 1, score: 900, won: true }),
      p(2, { group: 0, score: 700 }), p(3, { group: 1, score: 200, won: true }),
    ]);
    expect(ffa).toBe(false);
    expect(groups.map((g) => g.members.map((m) => m.team))).toEqual([[1, 3], [2, 0]]);
  });
});

describe("The report screen shows everyone, and fits", () => {
  // The report's body rect on the post-match screen at two window sizes.
  const RECTS = [
    { name: "1600×900", x: 144, y: 182, w: 908, h: 618 },
    { name: "1280×760", x: 48, y: 182, w: 780, h: 478 },
  ];

  for (const [label, alliances] of [
    ["4-player free-for-all", [0, 1, 2, 3]],
    ["2v2", [0, 0, 1, 1]],
    ["8-player free-for-all", [0, 1, 2, 3, 4, 5, 6, 7]],
    ["4v4", [0, 0, 0, 0, 1, 1, 1, 1]],
    ["8v8", [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1]],
  ] as const) {
    it(`names every realm, on every tab, inside the panel — ${label}`, () => {
      const r = synthetic([...alliances]);
      expect(isStandings(r)).toBe(true);
      const names = r.players!.map(playerName);
      for (const rect of RECTS) {
        // Feedback is sentences about you, not a table of every realm.
        for (const tab of REPORT_TABS.filter((t) => t.id !== "feedback")) {
          begin();
          const texts = drawn(() => drawReportTab(tab.id, rect.x, rect.y, rect.w, rect.h, r));
          for (const n of names) {
            const at = texts.find((t) => t.s === n);
            expect(at, `${n} missing from ${tab.label} at ${rect.name}`).toBeDefined();
            expect(at!.y, `${n} drawn below the panel on ${tab.label} at ${rect.name}`).toBeLessThanOrEqual(rect.y + rect.h);
          }
        }
      }
    });
  }

  it("still draws a duel as a duel, with both realms named in the key", () => {
    const r = synthetic([0, 1]);
    expect(isStandings(r)).toBe(false);
    begin();
    const texts = drawn(() => drawReportKey(1000, 140, r)).map((t) => t.s);
    expect(texts).toEqual([`${playerName(r.players![0])} (you)`, playerName(r.players![1])]);
  });

  it("opens a report saved before per-player stats existed", () => {
    const r = synthetic([0, 1, 2, 3]);
    delete r.players;
    expect(isStandings(r)).toBe(false);
    expect(matchFormat(r)).toBe("1v3");
    for (const tab of REPORT_TABS) {
      begin();
      expect(() => drawReportTab(tab.id, 48, 182, 780, 478, r)).not.toThrow();
    }
  });
});

describe("The feedback tab", () => {
  it("says what went well and what cost you, inside the panel", async () => {
    const { matchFeedback } = await import("../meta/feedback");
    const r = emptyMatchReport();
    r.durationSec = 1200;
    Object.assign(r.you, { teams: [0], gathered: 20000, unitsKilled: 40, unitsLost: 10, tcSeconds: 1200, idleTcTime: 60, buildingsRazed: 4, firstAttackAt: 300, killsAttacking: 12, killsByUnit: { archer: 30 } });
    Object.assign(r.foe, { teams: [1], gathered: 10000, unitsKilled: 10, unitsLost: 40, killsDefending: 4, lostByType: { villager: 10 } });
    const fb = matchFeedback(r);
    expect(fb.good.join(" ")).toMatch(/out-gathered/);
    expect(fb.good.join(" ")).toMatch(/early attack at 5:00 paid off/);
    expect(fb.bad).toEqual([]);
    const bad = emptyMatchReport();
    bad.durationSec = 1200;
    Object.assign(bad.you, { teams: [0], gathered: 8000, unitsKilled: 5, unitsLost: 30, tcSeconds: 1200, idleTcTime: 600, lostByType: { villager: 12 }, buildingsLost: 5 });
    Object.assign(bad.foe, { teams: [1], gathered: 16000, unitsKilled: 30, unitsLost: 5, firstAttackAt: 360, killsAttacking: 15 });
    const fb2 = matchFeedback(bad);
    expect(fb2.bad.length).toBeGreaterThanOrEqual(3);
    expect(fb2.bad.join(" ")).toMatch(/Their attack at 6:00 hurt/);
    for (const rect of [{ w: 780, h: 478 }, { w: 420, h: 400 }]) {
      begin();
      const texts = drawn(() => drawReportTab("feedback", 20, 20, rect.w, rect.h, bad));
      expect(texts.length).toBeGreaterThan(4);
      for (const t of texts) expect(t.y).toBeLessThanOrEqual(20 + rect.h);
    }
  });
});
