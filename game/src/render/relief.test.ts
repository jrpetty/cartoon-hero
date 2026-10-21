import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as unknown as { document: unknown }).document = {
  createElement: () => createCanvas(1, 1),
};
import { buildTerrainCache, terrainCacheScale } from "./terrain";
import { generateMap } from "../maps/generator";
import type { MapData } from "../maps/generator";
import { Terrain } from "../maps/terrain_kinds";
import { TILE } from "../content/balance";

/**
 * Water, shoreline and hills, painted once at load from smooth fields.
 *
 * Water used to be a two-tone square per cell and hills a disc per cell, which
 * between them drew the tile grid the rest of the terrain painter hides. The
 * replacement rounds every coast — and so has to be pinned down, because water
 * is impassable and a picture that disagrees with the pathfinder about where
 * the water is is a picture that lies.
 */

interface Shot { width: number; height: number; getContext(t: "2d"): CanvasRenderingContext2D }

const N = 40;

function bed(paint: (set: (cx: number, cy: number, t: Terrain) => void) => void): MapData {
  const base = generateMap("open_plains", 3, 2);
  const terrain = new Uint8Array(N * N).fill(Terrain.Grass);
  paint((cx, cy, t) => { terrain[cy * N + cx] = t; });
  return {
    ...base, cols: N, rows: N, worldW: N * TILE, worldH: N * TILE,
    terrain, resources: [], blockedCells: [], starts: [], biome: base.biome,
  };
}

function bake(map: MapData) {
  const canvas = buildTerrainCache(map) as unknown as Shot;
  const t = TILE * terrainCacheScale(map);
  const d = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data;
  const px = (x: number, y: number) => {
    const o = ((y | 0) * canvas.width + (x | 0)) * 4;
    return [d[o], d[o + 1], d[o + 2]] as const;
  };
  const centre = (cx: number, cy: number) => px((cx + 0.5) * t, (cy + 0.5) * t);
  return { canvas, t, d, px, centre };
}

/** Every blue in the water ramp, foam included, has far more blue than red. */
const watery = ([r, , b]: readonly number[]) => b > r + 12;

/** A lake, a lone pond, a one-cell channel, a one-cell island and a hill. */
const MIXED = bed((set) => {
  for (let y = 4; y < 12; y++) for (let x = 4; x < 12; x++) set(x, y, Terrain.Water);
  set(8, 8, Terrain.Grass); // island in the lake
  set(30, 5, Terrain.Water); // lone pond
  for (let y = 14; y < 36; y++) set(20, y, Terrain.Water); // one-cell channel
  for (let y = 22; y < 32; y++) for (let x = 27; x < 35; x++) set(x, y, Terrain.Hill);
});

describe("Water and relief agree with the sim", () => {
  const shot = bake(MIXED);

  it("paints every water cell as water and every land cell as land, at its centre", () => {
    const wrong: string[] = [];
    for (let cy = 0; cy < N; cy++) {
      for (let cx = 0; cx < N; cx++) {
        const isWater = MIXED.terrain[cy * N + cx] === Terrain.Water;
        if (watery(shot.centre(cx, cy)) !== isWater) wrong.push(`${cx},${cy}${isWater ? " (water)" : " (land)"}`);
      }
    }
    expect(wrong, "the picture disagrees with the pathfinder here").toEqual([]);
  });

  it("keeps the smallest features the rounding could erase", () => {
    expect(watery(shot.centre(30, 5)), "a lone pond dried up").toBe(true);
    expect(watery(shot.centre(8, 8)), "a one-cell island was flooded").toBe(false);
    for (let y = 14; y < 36; y++) expect(watery(shot.centre(20, y)), `the channel is closed at row ${y}`).toBe(true);
  });

  it("puts a beach on the land beside water, and none in open country", () => {
    const grass = shot.centre(2, 36);
    const shore = shot.centre(3, 7); // land, next to the lake's west edge
    expect(shore[0] - grass[0], "no sand at the waterline").toBeGreaterThan(30);
    const inland = shot.centre(16, 2);
    expect(Math.abs(inland[0] - grass[0]), "sand far from any water").toBeLessThan(20);
  });

  it("rounds a staircase coast instead of drawing the steps", () => {
    // Water below the diagonal x + y < 24: cell by cell, a staircase.
    const map = bed((set) => {
      for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) if (x + y < 24) set(x, y, Terrain.Water);
    });
    const { canvas, px, t } = bake(map);
    // Walk down the rows in the middle of the coast and find where the water
    // ends on each. A staircase holds still for a whole cell and then jumps a
    // whole cell; a coast moves a little on every row.
    let worst = 0, prev = -1;
    for (let y = Math.floor(6 * t); y < Math.floor(18 * t); y++) {
      let x = 0;
      while (x < canvas.width && watery(px(x, y))) x++;
      if (prev >= 0) worst = Math.max(worst, Math.abs(x - prev));
      prev = x;
    }
    expect(worst, `the coastline jumps ${worst}px between rows — a staircase`).toBeLessThan(t * 0.5);
  });

  it("keeps grass and flowers out of the river", () => {
    // Dressing scatters up to a cell from where it is seeded, and used to plant
    // tufts in the shallows along every bank. On each row the water should be
    // one unbroken run up to the coast.
    const map = bed((set) => {
      for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) if (x + y < 24) set(x, y, Terrain.Water);
    });
    const { canvas, px, t } = bake(map);
    const broken: number[] = [];
    for (let y = Math.floor(2 * t); y < Math.floor(20 * t); y++) {
      let first = 0;
      while (first < canvas.width && watery(px(first, y))) first++;
      let wet = 0;
      for (let x = 0; x < canvas.width; x++) if (watery(px(x, y))) wet++;
      if (wet !== first) broken.push(y);
    }
    expect(broken, "something dry was drawn in the water on these rows").toEqual([]);
  });

  it("lights hills from the north-west", () => {
    // Same ground either side of the hill; only the slope's aspect differs.
    const lum = ([r, g, b]: readonly number[]) => r * 0.3 + g * 0.59 + b * 0.11;
    const nw = lum(shot.centre(27, 22)), se = lum(shot.centre(34, 31));
    expect(nw - se, "the sunlit flank is not lighter than the shaded one").toBeGreaterThan(15);
  });

  it("is deterministic", () => {
    const again = bake(MIXED);
    let diff = 0;
    for (let i = 0; i < shot.d.length; i++) if (shot.d[i] !== again.d[i]) diff++;
    expect(diff).toBe(0);
  });
});
