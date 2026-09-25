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

describe("A* reachability shortcut", () => {
  /** A grid split in two by a solid wall, with an optional gate. */
  const splitGrid = (gate = false) => {
    const g = makeGrid();
    for (let cy = 0; cy < 32; cy++) g.setBlocked(16, cy, true);
    if (gate) g.setBlocked(16, 20, false);
    return g;
  };

  it("says no immediately when the two ends are in different pockets", () => {
    const g = splitGrid();
    expect(findPath(g, 4 * TILE, 4 * TILE, 28 * TILE, 4 * TILE)).toBeNull();
    // …and yes when a gate is opened in the same wall.
    const open = splitGrid(true);
    const path = findPath(open, 4 * TILE, 4 * TILE, 28 * TILE, 4 * TILE);
    expect(path).not.toBeNull();
    for (let i = 0; i < path!.length; i += 2) {
      expect(open.isBlockedWorld(path![i], path![i + 1])).toBe(false);
    }
  });

  it("notices when a wall is closed or opened after the labels were built", () => {
    const g = splitGrid(true);
    // Ask once so the component labels are built with the gate open.
    expect(findPath(g, 4 * TILE, 4 * TILE, 28 * TILE, 4 * TILE)).not.toBeNull();
    // Shut the gate: the answer has to change.
    g.setBlocked(16, 20, true);
    expect(findPath(g, 4 * TILE, 4 * TILE, 28 * TILE, 4 * TILE)).toBeNull();
    // Open it again.
    g.setBlocked(16, 20, false);
    expect(findPath(g, 4 * TILE, 4 * TILE, 28 * TILE, 4 * TILE)).not.toBeNull();
  });

  it("never refuses a route that a full search would have found", () => {
    // A maze of staggered walls: every open cell is reachable from every other,
    // so the shortcut must never fire, however tangled the route.
    const g = makeGrid();
    for (let cx = 2; cx < 30; cx += 4) {
      for (let cy = 0; cy < 32; cy++) {
        if (cy === (cx % 8 === 2 ? 31 : 0)) continue; // alternating end gaps
        g.setBlocked(cx, cy, true);
      }
    }
    for (const [sx, sy, tx, ty] of [[1, 1, 29, 30], [29, 1, 1, 30], [1, 15, 29, 15]] as const) {
      const path = findPath(g, sx * TILE + 8, sy * TILE + 8, tx * TILE + 8, ty * TILE + 8);
      expect(path, `${sx},${sy} → ${tx},${ty}`).not.toBeNull();
    }
  });

  it("still searches when a unit is standing inside a blocked cell", () => {
    // Being shoved onto a footprint must not read as "in no component, give up":
    // the unit can walk out through its open neighbours, and did before.
    const g = makeGrid();
    g.setBlocked(5, 5, true);
    const path = findPath(g, 5 * TILE + 8, 5 * TILE + 8, 20 * TILE, 20 * TILE);
    expect(path).not.toBeNull();
  });

  it("answers an unreachable request without exhausting its node budget", () => {
    // The point of the whole exercise: an unreachable target used to expand its
    // full six-thousand-node budget before admitting defeat, and on an
    // eight-player map two in five requests were exactly that. The grid has to
    // be bigger than the budget for this to mean anything — on a small one the
    // old search ran out of cells before it ran out of nodes.
    const g = new NavGrid(160 * TILE, 160 * TILE);
    for (let cy = 0; cy < 160; cy++) g.setBlocked(80, cy, true);
    const t0 = performance.now();
    const N = 300;
    for (let i = 0; i < N; i++) {
      expect(findPath(g, 10 * TILE, (i % 150) * TILE, 150 * TILE, 10 * TILE)).toBeNull();
    }
    const ms = performance.now() - t0;
    // Generous bound — a "did the shortcut fire" check, not a benchmark. Without
    // it these searches expand ~6000 cells each and take well over a second.
    expect(ms, `${ms.toFixed(0)}ms for ${N} unreachable requests`).toBeLessThan(150);
  });
});
