import { describe, expect, it } from "vitest";
import { wallLinePoints } from "./wallline";

const TILE = 32;
const cell = (p: { x: number; y: number }) => [Math.floor(p.x / TILE), Math.floor(p.y / TILE)] as const;

describe("wallLinePoints", () => {
  it("fills a straight horizontal run end-to-end with no gaps", () => {
    const pts = wallLinePoints(0, 0, 10 * TILE, 0, TILE);
    expect(pts.length).toBe(11); // 0..10 inclusive
    const xs = pts.map((p) => cell(p)[0]);
    expect(xs).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]); // contiguous, reaches the end
  });

  it("reaches the exact endpoint cell on a diagonal (the old bug stopped short)", () => {
    const pts = wallLinePoints(0, 0, 8 * TILE, 5 * TILE, TILE);
    expect(cell(pts[0])).toEqual([0, 0]);
    expect(cell(pts[pts.length - 1])).toEqual([8, 5]); // full length to where you dragged
  });

  it("leaves no gaps — every consecutive cell is adjacent", () => {
    for (const [ex, ey] of [[8, 5], [3, 11], [-7, 4], [12, -2], [0, 9]] as const) {
      const pts = wallLinePoints(0, 0, ex * TILE, ey * TILE, TILE);
      for (let i = 1; i < pts.length; i++) {
        const [ax, ay] = cell(pts[i - 1]);
        const [bx, by] = cell(pts[i]);
        const step = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        expect(step).toBe(1); // never jumps over a cell
      }
      expect(cell(pts[pts.length - 1])).toEqual([ex, ey]); // and always reaches the end
    }
  });

  it("a single click yields one segment", () => {
    const pts = wallLinePoints(5 * TILE, 5 * TILE, 5 * TILE + 3, 5 * TILE - 2, TILE);
    expect(pts.length).toBe(1);
    expect(cell(pts[0])).toEqual([5, 5]);
  });

  it("caps absurdly long drags", () => {
    const pts = wallLinePoints(0, 0, 100000, 0, TILE, 64);
    expect(pts.length).toBeLessThanOrEqual(65);
  });
});
