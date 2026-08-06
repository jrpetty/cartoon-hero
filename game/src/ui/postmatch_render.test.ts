import { describe, expect, it, beforeAll } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { ui } from "./ui";
import { PostMatchScreen, setMouseDown } from "./screens";
import { Profile } from "../meta/profile";
import { computeRewards } from "../meta/progression";
import { emptyMatchReport, MatchReport } from "../sim/metrics";

/** A report with plausible numbers on both sides, for the render smoke test. */
function sampleReport(): MatchReport {
  const r = emptyMatchReport();
  r.mapName = "Highlands";
  r.durationSec = 240;
  Object.assign(r.you, {
    teams: [0], score: 4200, gathered: 3200, gatheredBy: { food: 1400, wood: 1200, gold: 600 },
    spent: 2900, spentOn: { units: 1700, buildings: 800, tech: 400 }, banked: 300,
    unitsKilled: 30, unitsLost: 12, buildingsRazed: 4, buildingsLost: 1,
    damageDealt: 8400, damageTaken: 6100, peakArmy: 24, peakVillagers: 32, idleVillagerTime: 41,
    age: 2, upgrades: 6,
    trainedByType: { militia: 12, archer: 9, knight: 5 },
    lostByType: { militia: 7, archer: 4 },
    killedByType: { spearman: 11, archer: 8, villager: 6 },
    builtByType: { house: 6, barracks: 2 },
  });
  Object.assign(r.foe, {
    teams: [1], score: 3600, gathered: 2600, gatheredBy: { food: 1100, wood: 1000, gold: 500 },
    spent: 2100, spentOn: { units: 1300, buildings: 600, tech: 200 }, banked: 500,
    unitsKilled: 18, unitsLost: 26, buildingsRazed: 2, buildingsLost: 5,
    damageDealt: 6100, damageTaken: 8400, peakArmy: 19, peakVillagers: 27, idleVillagerTime: 120,
    age: 2, upgrades: 3,
    trainedByType: { spearman: 14, archer: 10 },
    lostByType: { spearman: 11, archer: 8, villager: 6 },
    killedByType: { militia: 7, archer: 4 },
    builtByType: { house: 5, barracks: 1 },
  });
  return r;
}

// Runtime smoke: the post-match graph only draws after a match, which the
// menu-only boot-check never reaches. Render it against a real canvas context to
// prove the chart + tabs don't throw.
beforeAll(() => {
  const store: Record<string, string> = {};
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
  };
});

describe("Post-match graph renders", () => {
  it("draws the progression chart + metric tabs without throwing", () => {
    const W = 1280;
    const H = 760;
    const canvas = createCanvas(W, H);
    const ctx = canvas.getContext("2d") as unknown as CanvasRenderingContext2D;
    setMouseDown(false);
    ui.begin(ctx, { mx: 0, my: 0, clicked: false, rightClicked: false, alt: false });

    const n = 12;
    const ts = Array.from({ length: n }, (_, i) => i * 20);
    const ramp = (k: number) => Array.from({ length: n }, (_, i) => i * k + 5);
    const graph = {
      ts,
      mine: { score: ramp(40), military: ramp(12), economy: ramp(120) },
      foe: { score: ramp(35), military: ramp(10), economy: ramp(110) },
    };
    const rewards = computeRewards({ win: true, durationSec: 240, unitsKilled: 30, buildingsRazed: 4, difficulty: "knight", fairMode: false });
    const screen = new PostMatchScreen();

    const report = sampleReport();
    const draw = () => screen.draw(W, H, 1.0, 0.016, true, report, rewards, Profile.load(), 0, 0, graph);

    expect(() => draw()).not.toThrow();
    // Render every metric tab path too.
    for (const _ of ["score", "military", "economy"]) {
      ui.begin(ctx, { mx: 0, my: 0, clicked: false, rightClicked: false, alt: false });
      expect(() => draw()).not.toThrow();
    }
    // Every report tab, at two window sizes — the report is the tallest thing
    // on the screen and the one most likely to overflow a short window.
    for (const tab of ["overview", "economy", "military", "units"] as const) {
      (screen as unknown as { reportTab: string }).reportTab = tab;
      for (const [w, h] of [[1280, 760], [1600, 900], [1100, 680]] as const) {
        ui.begin(ctx, { mx: 0, my: 0, clicked: false, rightClicked: false, alt: false });
        expect(() => screen.draw(w, h, 1.0, 0.016, true, report, rewards, Profile.load(), 0, 0, graph), tab).not.toThrow();
      }
    }
    // An empty report (a match that ended immediately) and a null graph must
    // also be safe — every one of those ratios divides by something.
    ui.begin(ctx, { mx: 0, my: 0, clicked: false, rightClicked: false, alt: false });
    expect(() => screen.draw(W, H, 1.0, 0.016, false, emptyMatchReport(), rewards, Profile.load(), 0, 0, null)).not.toThrow();
  });
});
