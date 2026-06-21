import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
import { ui } from "./ui";
import { setMouseDown } from "./screens";
import { WarbandScreen } from "./warband_screen";
import { WarbandRun } from "../sim/warband";

const W = 1280, H = 760;
const boardX = 232, boardY = 92;
const boardW = W - boardX - 16;
const boardH = (H - 132) - 66 - 16 - boardY;
const cellW = boardW / 10, cellH = boardH / 10;
const cellCenter = (c: number, r: number) => [boardX + (c + 0.5) * cellW, boardY + (r + 0.5) * cellH] as const;

describe("Warband screen placement", () => {
  const setup = () => {
    const ctx = createCanvas(W, H).getContext("2d") as unknown as CanvasRenderingContext2D;
    const s = new WarbandScreen();
    const run = new WarbandRun(11);
    run.gold = 400; run.level = 6;
    for (const t of ["knight", "archer", "spearman", "militia", "horseman", "crossbow"]) {
      run.shop = [t, t, t, t, t]; run.buy(0); run.buy(1);
    }
    let t = 0;
    const frame = (mx: number, my: number, down: boolean, clicked: boolean) => {
      t += 1 / 30; setMouseDown(down); ui.begin(ctx, { mx, my, clicked, rightClicked: false, alt: false }); s.draw(W, H, t, run);
    };
    return { s, run, frame };
  };

  it("drags a board unit onto the sell box to sell it", () => {
    const { run, frame } = setup();
    run.place(run.deployment()[0].index, 2, 3);
    frame(0, 0, false, false); // settle (resolves auto-placement)
    const before = run.pieces.length;
    const [ux, uy] = cellCenter(2, 3);
    const [sx, sy] = [boardX + boardW - 156 / 2 - 14, boardY + boardH - 66 / 2 - 14];
    frame(ux, uy, true, false);   // press → grab
    frame(sx, sy, true, false);   // drag over the sell box
    frame(sx, sy, false, true);   // release → sell
    expect(run.pieces.length).toBe(before - 1);
  });

  it("click-to-place moves a unit to an empty cell", () => {
    const { run, frame } = setup();
    run.place(run.deployment()[0].index, 2, 3);
    frame(0, 0, false, false);
    const idx = run.deployment().find((d) => d.col === 2 && d.row === 3)!.index;
    const [ux, uy] = cellCenter(2, 3);
    const [ex, ey] = cellCenter(0, 9); // an empty cell
    frame(ux, uy, true, false);   // press on the unit
    frame(ux, uy, false, true);   // release on it → picked up (armed)
    frame(ex, ey, false, true);   // click an empty cell → place
    const moved = run.pieces[idx];
    expect(moved.col).toBe(0); expect(moved.row).toBe(9);
  });
});

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
