import { describe, expect, it } from "vitest";
import { generateMap, PRESETS, Terrain } from "./generator";
import { TILE } from "../content/balance";

// Flood-fill over cells that aren't water/cliff (blockedCells). Resources (trees
// etc.) are removable and units path around them, so the real "is this map
// winnable" guarantee is that every base connects over open ground + fords.
function allStartsConnected(presetId: string, seed: number, players = 2): boolean {
  const map = generateMap(presetId, seed, players);
  const blocked = new Set<number>();
  for (const [cx, cy] of map.blockedCells) blocked.add(cy * map.cols + cx);

  const cell = (s: { x: number; y: number }) => [Math.floor(s.x / TILE), Math.floor(s.y / TILE)] as const;
  const [s0x, s0y] = cell(map.starts[0]);
  const seen = new Uint8Array(map.cols * map.rows);
  const stack: [number, number][] = [[s0x, s0y]];
  seen[s0y * map.cols + s0x] = 1;
  while (stack.length) {
    const [cx, cy] = stack.pop()!;
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const nx = cx + dx;
      const ny = cy + dy;
      if (nx < 0 || ny < 0 || nx >= map.cols || ny >= map.rows) continue;
      const i = ny * map.cols + nx;
      if (seen[i] || blocked.has(i)) continue;
      seen[i] = 1;
      stack.push([nx, ny]);
    }
  }
  return map.starts.every((s) => {
    const [cx, cy] = cell(s);
    return seen[cy * map.cols + cx] === 1;
  });
}

describe("Map generation", () => {
  it("every preset generates starts, resources and terrain", () => {
    for (const p of PRESETS) {
      const map = generateMap(p.id, 1234, 2);
      expect(map.name).toBe(p.name);
      expect(map.starts.length).toBe(2);
      expect(map.resources.length).toBeGreaterThan(20);
      expect(map.terrain.length).toBe(map.cols * map.rows);
    }
  });

  it("'random' resolves to a real preset deterministically", () => {
    const a = generateMap("random", 4242, 2);
    const b = generateMap("random", 4242, 2);
    expect(a.name).toBe(b.name); // same seed → same roll
    expect(PRESETS.some((p) => p.name === a.name)).toBe(true);
  });

  it("keeps every base reachable on all presets, including Islands (no base walled off by water)", () => {
    for (const p of PRESETS) {
      for (const seed of [1, 7, 42, 99, 2026]) {
        expect(allStartsConnected(p.id, seed, 2)).toBe(true);
      }
    }
    // 4-player islands too — every corner must still connect.
    for (const seed of [3, 21, 808]) {
      expect(allStartsConnected("islands", seed, 4)).toBe(true);
    }
  });

  it("places up to 8 players evenly, each with their own resources", () => {
    for (const N of [2, 3, 5, 6, 8]) {
      const map = generateMap("highlands", 123 + N, N);
      expect(map.starts.length).toBe(N);
      // Even spacing: every start sits ~the same distance from the centre.
      const mid = { x: map.worldW / 2, y: map.worldH / 2 };
      const radii = map.starts.map((s) => Math.hypot(s.x - mid.x, s.y - mid.y));
      const rMin = Math.min(...radii);
      const rMax = Math.max(...radii);
      expect(rMax - rMin).toBeLessThan(map.worldW * 0.06); // all on one ring
      // Every realm has its own wood, food and gold within reach (fair start).
      const TILES = 14 * 32;
      for (const s of map.starts) {
        const near = (type: string) =>
          map.resources.some((r) => r.type === type && Math.hypot(r.x - s.x, r.y - s.y) < TILES);
        expect(near("tree")).toBe(true);
        expect(near("berries")).toBe(true);
        expect(near("gold_mine")).toBe(true);
      }
    }
  });

  it("seats teammates next to each other in an even-teams game", () => {
    // 4v4: alliance id = team % 2.
    const N = 8;
    const alliances = Array.from({ length: N }, (_, t) => t % 2);
    const map = generateMap("open_plains", 909, N, false, alliances);
    for (let t = 0; t < N; t++) {
      // A teammate should sit at the nearest-base distance (allies hold one arc;
      // boundary realms tie an ally and an enemy, so "among the nearest" is the
      // right property rather than "strictly the single nearest").
      const dists = map.starts.map((s, o) =>
        o === t ? Infinity : Math.hypot(map.starts[t].x - s.x, map.starts[t].y - s.y));
      const nearest = Math.min(...dists);
      const nearestAlly = Math.min(
        ...dists.filter((_, o) => o !== t && alliances[o] === alliances[t]),
      );
      expect(nearestAlly).toBeLessThan(nearest + map.worldW * 0.02);
    }
  });

  it("keeps 8 bases reachable even on water maps", () => {
    for (const id of ["islands", "riverlands", "highlands"]) {
      for (const seed of [11, 222]) {
        expect(allStartsConnected(id, seed, 8)).toBe(true);
      }
    }
  });

  it("Islands actually carries water (fords were carved through it)", () => {
    const map = generateMap("islands", 55, 2);
    let water = 0;
    let sand = 0;
    for (let i = 0; i < map.terrain.length; i++) {
      if (map.terrain[i] === Terrain.Water) water++;
      else if (map.terrain[i] === Terrain.Sand) sand++;
    }
    expect(water).toBeGreaterThan(50); // it's a watery map
    expect(sand).toBeGreaterThan(0); // ...with carved land bridges
  });
});
