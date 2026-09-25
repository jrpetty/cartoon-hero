import { describe, expect, it } from "vitest";
import { FOG_BLUR_RADIUS, blurMask } from "./fogblur";

/**
 * The fog frontier, tested where it can actually be seen.
 *
 * An earlier version of this tried to assert on the composited frame: render a
 * match, sample a scanline crossing the frontier, and count how many pixels sat
 * between light and dark. It passed identically with the blur on and off — 224
 * intermediate pixels against 223 — because terrain noise and the vignette
 * swamp the fog edge, so it was measuring the ground, not the fog. The blur is
 * its own function now, and the assertions are on the mask itself.
 */

/** A grid split down the middle: unseen on the left, visible on the right. */
function frontier(cols: number, rows: number) {
  const a = new Float32Array(cols * rows);
  for (let y = 0; y < rows; y++) {
    for (let x = 0; x < cols; x++) a[y * cols + x] = x < cols / 2 ? 255 : 0;
  }
  return a;
}

/** How many cells along the middle row sit strictly between the two extremes. */
function rampWidth(a: Float32Array, cols: number, rows: number): number {
  const row = Math.floor(rows / 2) * cols;
  let n = 0;
  for (let x = 0; x < cols; x++) {
    const v = a[row + x];
    if (v > 1 && v < 254) n++;
  }
  return n;
}

describe("Blurring the fog mask", () => {
  it("turns a hard edge into a falloff", () => {
    const cols = 32, rows = 32;
    const a = frontier(cols, rows);
    expect(rampWidth(a, cols, rows), "the fixture is not a hard edge").toBe(0);
    blurMask(a, new Float32Array(cols * rows), cols, rows, FOG_BLUR_RADIUS);
    expect(rampWidth(a, cols, rows), "the edge is still a step").toBeGreaterThan(2);
  });

  it("spreads the edge about as far as the radius asks", () => {
    // Two passes of a box blur of radius r spread a step over roughly 2r cells;
    // the exact figure matters less than that it tracks the radius, since that
    // is the knob anyone tuning this will reach for.
    const cols = 64, rows = 8;
    const widths = [1, 2, 4].map((r) => {
      const a = frontier(cols, rows);
      blurMask(a, new Float32Array(cols * rows), cols, rows, r);
      return rampWidth(a, cols, rows);
    });
    expect(widths[1]).toBeGreaterThan(widths[0]);
    expect(widths[2]).toBeGreaterThan(widths[1]);
  });

  it("does nothing at radius zero, so the blur can be switched off", () => {
    const cols = 16, rows = 16;
    const a = frontier(cols, rows);
    const before = Array.from(a);
    blurMask(a, new Float32Array(cols * rows), cols, rows, 0);
    expect(Array.from(a)).toEqual(before);
  });

  it("keeps the extremes at the extremes", () => {
    // Unexplored ground must stay fully dark and scouted ground fully clear —
    // a blur that greys out the whole map would leak it.
    const cols = 64, rows = 16;
    const a = frontier(cols, rows);
    blurMask(a, new Float32Array(cols * rows), cols, rows, FOG_BLUR_RADIUS);
    const row = Math.floor(rows / 2) * cols;
    expect(a[row + 0], "the far dark side lightened").toBeCloseTo(255, 3);
    expect(a[row + cols - 1], "the far clear side darkened").toBeCloseTo(0, 3);
  });

  it("never reads outside the grid", () => {
    // The edge of the map is a frontier too, and clamping is what stops the
    // blur wrapping the far side of the world into it.
    const cols = 8, rows = 8;
    const a = new Float32Array(cols * rows).fill(255);
    a[0] = 0;
    blurMask(a, new Float32Array(cols * rows), cols, rows, 3);
    for (let i = 0; i < a.length; i++) {
      expect(Number.isFinite(a[i]), `cell ${i} is not a number`).toBe(true);
      expect(a[i]).toBeLessThanOrEqual(255.001);
      expect(a[i]).toBeGreaterThanOrEqual(-0.001);
    }
  });

  it("is symmetric, so the frontier does not drift to one side", () => {
    const cols = 33, rows = 3;
    const a = new Float32Array(cols * rows);
    const mid = Math.floor(cols / 2);
    for (let y = 0; y < rows; y++) a[y * cols + mid] = 255;
    blurMask(a, new Float32Array(cols * rows), cols, rows, 3);
    const row = cols;
    for (let k = 1; k <= 5; k++) {
      expect(a[row + mid - k]).toBeCloseTo(a[row + mid + k], 5);
    }
  });
});
