import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as unknown as { document: unknown }).document = {
  createElement: () => createCanvas(1, 1),
};
import { drawGroundDetail } from "./terrain";
import { generateMap } from "../maps/generator";
import { Terrain } from "../maps/terrain_kinds";
import { TILE } from "../content/balance";

/**
 * Close-up ground detail.
 *
 * The terrain cache is baked at half resolution, which is right for the zoom
 * you fight at and turns to soft green mush the moment you lean in. Rather than
 * bake a bigger texture — the cache already has to stay under Safari's canvas
 * limit on the largest maps — the close-up marks are drawn fresh for the cells
 * actually on screen.
 */

function bed(terrain: Terrain, cols = 24) {
  const map = generateMap("open_plains", 3, 2);
  const m = {
    ...map,
    cols,
    rows: cols,
    terrain: new Uint8Array(cols * cols).fill(terrain),
  };
  const px = cols * TILE;
  const canvas = createCanvas(px, px) as unknown as Shot;
  const ctx = canvas.getContext("2d");
  ctx.fillStyle = "#4a4a4a";
  ctx.fillRect(0, 0, px, px);
  return { m, canvas, ctx, px };
}

/** How many pixels differ from the flat bed we painted underneath. */
interface Shot { width: number; height: number; getContext(t: "2d"): CanvasRenderingContext2D }

function marks(canvas: Shot): number {
  const ctx = canvas.getContext("2d");
  const d = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
  let n = 0;
  for (let i = 0; i < d.length; i += 4) {
    if (Math.abs(d[i] - 74) > 6 || Math.abs(d[i + 1] - 74) > 6 || Math.abs(d[i + 2] - 74) > 6) n++;
  }
  return n;
}

describe("Ground detail earns its place", () => {
  it("draws nothing when zoomed out, where it would be sub-pixel noise", () => {
    const { m, canvas, ctx, px } = bed(Terrain.Grass);
    drawGroundDetail(ctx, m, 0, 0, px, px, 0.5, 0);
    expect(marks(canvas), "spent time drawing detail nobody can see").toBe(0);
  });

  it("draws when zoomed in, where the cache has gone soft", () => {
    const { m, canvas, ctx, px } = bed(Terrain.Grass);
    drawGroundDetail(ctx, m, 0, 0, px, px, 1.5, 0);
    expect(marks(canvas)).toBeGreaterThan(200);
  });

  it("bails out rather than drop a frame when the viewport holds a whole map", () => {
    // The cap is on cells actually in view, so the map has to be big enough to
    // exceed it — a small one clamps to its own bounds and never gets close.
    const cols = 80; // 6400 cells, past the 3000 ceiling
    const map = generateMap("open_plains", 3, 2);
    const m = { ...map, cols, rows: cols, terrain: new Uint8Array(cols * cols).fill(Terrain.Grass) };
    const px = 256;
    const canvas = createCanvas(px, px) as unknown as Shot;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#4a4a4a";
    ctx.fillRect(0, 0, px, px);
    drawGroundDetail(ctx, m, 0, 0, cols * TILE, cols * TILE, 1.5, 0);
    expect(marks(canvas), "drew detail for thousands of cells").toBe(0);
  });

  it("is stable frame to frame, so the ground does not crawl", () => {
    // The marks are hashed off the cell, not a running random — ground that
    // shimmered between frames would be far worse than ground with no detail.
    const a = bed(Terrain.Rock);
    const b = bed(Terrain.Rock);
    drawGroundDetail(a.ctx, a.m, 0, 0, a.px, a.px, 1.5, 0);
    drawGroundDetail(b.ctx, b.m, 0, 0, b.px, b.px, 1.5, 0);
    const da = a.canvas.getContext("2d").getImageData(0, 0, a.px, a.px).data;
    const db = b.canvas.getContext("2d").getImageData(0, 0, b.px, b.px).data;
    let diff = 0;
    for (let i = 0; i < da.length; i++) if (da[i] !== db[i]) diff++;
    expect(diff, "the ground shimmers between frames").toBe(0);
  });

  it("gives every ground type something, and water nothing", () => {
    // Water and shallows have their own glints; everything else should read as
    // itself up close rather than as flat colour.
    for (const t of [
      Terrain.Grass, Terrain.GrassDark, Terrain.Hill, Terrain.Forest,
      Terrain.Rock, Terrain.Dirt, Terrain.Sand, Terrain.Snow, Terrain.Marsh,
    ]) {
      const { m, canvas, ctx, px } = bed(t);
      drawGroundDetail(ctx, m, 0, 0, px, px, 1.5, 0);
      expect(marks(canvas), `terrain ${t} got no detail`).toBeGreaterThan(100);
    }
    for (const t of [Terrain.Water, Terrain.Shallow]) {
      const { m, canvas, ctx, px } = bed(t);
      drawGroundDetail(ctx, m, 0, 0, px, px, 1.5, 0);
      expect(marks(canvas), `terrain ${t} should be left to its own glints`).toBe(0);
    }
  });
});

describe("Hills read as height", () => {
  it("lights the crest and shadows the foot", () => {
    // High ground is worth 20% range here, so it is the one landform a player
    // most needs to pick out — and the cache paints it as a darker circle,
    // which from above is indistinguishable from a shadow.
    const cols = 12;
    const map = generateMap("open_plains", 3, 2);
    const terrain = new Uint8Array(cols * cols).fill(Terrain.Grass);
    // A single band of hill across the middle, so it has a top and a bottom.
    for (let cx = 0; cx < cols; cx++) {
      for (let cy = 5; cy <= 7; cy++) terrain[cy * cols + cx] = Terrain.Hill;
    }
    const m = { ...map, cols, rows: cols, terrain };
    const px = cols * TILE;
    const canvas = createCanvas(px, px) as unknown as Shot;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#4a4a4a";
    ctx.fillRect(0, 0, px, px);
    drawGroundDetail(ctx, m, 0, 0, px, px, 1.5, 0);
    const d = ctx.getImageData(0, 0, px, px).data;
    const bright = (x: number, y: number) => {
      const o = (y * px + x) * 4;
      return (d[o] + d[o + 1] + d[o + 2]) / 3;
    };
    // Sample a column clear of the tufts: the crest row against the foot row.
    let crest = 0, foot = 0;
    for (let x = 0; x < px; x++) {
      crest += bright(x, 5 * TILE + 1);
      foot += bright(x, 8 * TILE - 2);
    }
    expect(crest / px, "the crest is not lit").toBeGreaterThan(74);
    expect(foot / px, "the foot is not shadowed").toBeLessThan(74);
  });
});
