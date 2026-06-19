import { describe, expect, it, beforeAll } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { ui } from "./ui";
import { PostMatchScreen, setMouseDown } from "./screens";
import { Profile } from "../meta/profile";
import { computeRewards } from "../meta/progression";

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

    const draw = () => screen.draw(
      W, H, 1.0, 0.016, true,
      { unitsKilled: 30, unitsLost: 12, buildingsRazed: 4, buildingsLost: 1, gathered: 3200 },
      { unitsKilled: 18, gathered: 2600, buildingsRazed: 2 },
      240, rewards, Profile.load(), 0, 0, graph,
    );

    expect(() => draw()).not.toThrow();
    // Render every metric tab path too.
    for (const _ of ["score", "military", "economy"]) {
      ui.begin(ctx, { mx: 0, my: 0, clicked: false, rightClicked: false, alt: false });
      expect(() => draw()).not.toThrow();
    }
    // Null graph (a too-short match) must also be safe.
    ui.begin(ctx, { mx: 0, my: 0, clicked: false, rightClicked: false, alt: false });
    expect(() => screen.draw(
      W, H, 1.0, 0.016, false,
      { unitsKilled: 0, unitsLost: 0, buildingsRazed: 0, buildingsLost: 0, gathered: 0 },
      { unitsKilled: 0, gathered: 0, buildingsRazed: 0 },
      5, rewards, Profile.load(), 0, 0, null,
    )).not.toThrow();
  });
});
