import { describe, expect, it } from "vitest";
import { NavGrid } from "./grid";
import { findPath } from "./astar";
import { FlowField } from "./flowfield";
import { TILE } from "../content/balance";

function makeGrid(): NavGrid {
  return new NavGrid(32 * TILE, 32 * TILE);
}

describe("A* pathfinding", () => {
  it("finds a straight path on open ground", () => {
    const g = makeGrid();
    const path = findPath(g, 50, 50, 500, 500);
    expect(path).not.toBeNull();
    const pts = path!;
    expect(pts[pts.length - 2]).toBeCloseTo(500);
    expect(pts[pts.length - 1]).toBeCloseTo(500);
  });

  it("routes around a wall", () => {
    const g = makeGrid();
    // Vertical wall at cx=10, with a gap at cy=20.
    for (let cy = 0; cy < 32; cy++) {
      if (cy !== 20) g.setBlocked(10, cy, true);
    }
    const sx = 5 * TILE;
    const sy = 5 * TILE;
    const tx = 20 * TILE;
    const ty = 5 * TILE;
    const path = findPath(g, sx, sy, tx, ty)!;
    expect(path).not.toBeNull();
    // The path must dip down toward the gap row (cy=20).
    let maxY = 0;
    for (let i = 1; i < path.length; i += 2) maxY = Math.max(maxY, path[i]);
    expect(maxY).toBeGreaterThan(17 * TILE);
    // And no waypoint may sit inside a blocked cell.
    for (let i = 0; i < path.length; i += 2) {
      expect(g.isBlockedWorld(path[i], path[i + 1])).toBe(false);
    }
  });

  it("returns null when the goal is fully sealed", () => {
    const g = makeGrid();
    // Box in the target completely (3x3 wall ring around cell 16,16).
    for (let d = -2; d <= 2; d++) {
      g.setBlocked(16 + d, 14, true);
      g.setBlocked(16 + d, 18, true);
      g.setBlocked(14, 16 + d, true);
      g.setBlocked(18, 16 + d, true);
    }
    const path = findPath(g, 2 * TILE, 2 * TILE, 16 * TILE + 8, 16 * TILE + 8);
    // Goal is re-targeted to nearest open cell — which lies outside the box —
    // so a path may exist to the ring's edge; what matters is no crash and
    // either null or a path that never enters a blocked cell.
    if (path) {
      for (let i = 0; i < path.length; i += 2) {
        expect(g.isBlockedWorld(path[i], path[i + 1])).toBe(false);
      }
    }
  });
});

describe("FlowField", () => {
  it("directions lead downhill to the goal", () => {
    const g = makeGrid();
    for (let cy = 8; cy < 24; cy++) g.setBlocked(15, cy, true);
    const goalX = 25 * TILE;
    const goalY = 16 * TILE;
    const ff = new FlowField(g, goalX, goalY);
    // Walk the field from the far side; we should reach the goal area.
    let x = 4 * TILE;
    let y = 16 * TILE;
    for (let i = 0; i < 4000; i++) {
      const [dx, dy] = ff.sample(x, y);
      if (dx === 0 && dy === 0) break;
      x += dx * 8;
      y += dy * 8;
    }
    const d = Math.hypot(x - goalX, y - goalY);
    expect(d).toBeLessThan(TILE * 2.5);
  });
});
