import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { ui } from "./ui";
import { WarbandScreen } from "./warband_screen";
import { WarbandRun } from "../sim/warband";

describe("Warband screen renders", () => {
  it("draws shop, result and game-over phases without throwing", () => {
    const ctx = createCanvas(1280, 760).getContext("2d") as unknown as CanvasRenderingContext2D;
    const begin = () => ui.begin(ctx, { mx: 0, my: 0, clicked: false, rightClicked: false, alt: false });
    const s = new WarbandScreen();
    const run = new WarbandRun(5);
    run.gold = 80;
    run.shop = ["militia", "archer", "knight", "catapult", "hero"];
    run.buy(0); run.buy(1); run.buy(2);
    run.level = 3;

    begin();
    expect(() => s.draw(1280, 760, 1, run)).not.toThrow(); // shop phase

    run.fight();
    begin();
    expect(() => s.draw(1280, 760, 1, run)).not.toThrow(); // result (or over)

    // Force the game-over overlay (both win + loss variants).
    const r = run as unknown as { phase: string; outcome: string };
    r.phase = "over"; r.outcome = "loss";
    begin();
    expect(() => s.draw(1280, 760, 1, run)).not.toThrow();
    r.outcome = "win";
    begin();
    expect(() => s.draw(1280, 760, 1, run)).not.toThrow();
  });
});
