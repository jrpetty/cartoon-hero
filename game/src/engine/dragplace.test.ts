import { describe, expect, it } from "vitest";
import { blockPoints, wallLinePoints } from "./wallline";

const TILE = 32;

/**
 * What a drag should produce for each kind of building.
 *
 * These mirror the three cases `dragPlacements` routes between in main.ts. The
 * routing itself is one `if`; what is worth pinning is that the three shapes
 * stay distinct, because they were briefly not: for a while *every* building
 * dragged out as a spaced grid, which meant dragging a Gate laid nine gates in
 * formation — a misclick rendered as a feature.
 */
describe("Dragging out a wall", () => {
  it("lays a gap-free run of touching segments", () => {
    // A wall with a hole in it is a gate nobody meant to build.
    const pts = wallLinePoints(0, 0, TILE * 10, 0, TILE);
    expect(pts.length).toBe(11);
    for (let i = 1; i < pts.length; i++) {
      expect(Math.abs(pts[i].x - pts[i - 1].x)).toBe(TILE);
    }
  });

  it("stays gap-free on a steep diagonal", () => {
    // The case a "sample every TILE along the distance" approach under-counts.
    const pts = wallLinePoints(0, 0, TILE * 12, TILE * 5, TILE);
    for (let i = 1; i < pts.length; i++) {
      const dx = Math.abs(pts[i].x - pts[i - 1].x) / TILE;
      const dy = Math.abs(pts[i].y - pts[i - 1].y) / TILE;
      expect(Math.max(dx, dy), "a step skipped a cell").toBeLessThanOrEqual(1);
    }
  });

  it("reaches the exact cell the drag ended on", () => {
    const pts = wallLinePoints(0, 0, TILE * 7, TILE * 3, TILE);
    const last = pts[pts.length - 1];
    // Points are tile *centres* (cx*TILE + TILE/2), so it has to be floor —
    // rounding a centre lands a cell down and to the right.
    expect(Math.floor(last.x / TILE)).toBe(7);
    expect(Math.floor(last.y / TILE)).toBe(3);
  });

  it("gives one segment for a drag that barely moved", () => {
    // A short wall across a gap is still a wall, and should not need a
    // theatrical swipe to produce.
    expect(wallLinePoints(100, 100, 104, 102, TILE)).toHaveLength(1);
  });

  it("scales with the length dragged, which is the whole gesture", () => {
    const short = wallLinePoints(0, 0, TILE * 3, 0, TILE).length;
    const long = wallLinePoints(0, 0, TILE * 30, 0, TILE).length;
    expect(long).toBeGreaterThan(short * 5);
  });
});

describe("Dragging out houses", () => {
  it("spaces them so the block isn't an accidental wall", () => {
    const pts = blockPoints(0, 0, TILE * 9, 0, TILE, 2);
    expect(pts.length).toBeGreaterThan(1);
    for (let i = 1; i < pts.length; i++) {
      expect(pts[i].x - pts[i - 1].x).toBe(3 * TILE);
    }
  });

  it("is a different shape from a wall run over the same drag", () => {
    // The distinction that got lost once. A wall of ten touching segments and a
    // block of houses on a walking pitch are not the same answer.
    const wall = wallLinePoints(0, 0, TILE * 9, 0, TILE);
    const houses = blockPoints(0, 0, TILE * 9, 0, TILE, 2);
    expect(wall.length).toBeGreaterThan(houses.length);
  });
});
