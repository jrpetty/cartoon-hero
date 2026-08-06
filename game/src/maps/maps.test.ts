import { describe, expect, it } from "vitest";
import { generateMap, PRESETS, Terrain } from "./generator";
import { TERRAIN_BUILDABLE } from "./terrain_kinds";
import { NavGrid } from "../pathfinding/grid";
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

describe("Map geography", () => {
  it("gives a map hard barriers, high ground and walkable woods", () => {
    const map = generateMap("highlands", 4242, 4);
    const counts = new Map<number, number>();
    for (const t of map.terrain) counts.set(t, (counts.get(t) ?? 0) + 1);
    const has = (t: Terrain) => (counts.get(t) ?? 0);
    // Mountains and lakes are the hard barriers; hills and forest are the
    // ground you can cross but would rather think about first.
    expect(has(Terrain.Rock), "no mountains").toBeGreaterThan(20);
    expect(has(Terrain.Hill), "no high ground").toBeGreaterThan(20);
    expect(has(Terrain.Forest), "no walkable woodland").toBeGreaterThan(40);
    // Blocked cells and the terrain map must agree about what is a wall.
    const blocked = new Set(map.blockedCells.map(([cx, cy]) => cy * map.cols + cx));
    for (let i = 0; i < map.terrain.length; i++) {
      const t = map.terrain[i];
      if (t === Terrain.Rock || t === Terrain.Water) {
        expect(blocked.has(i), `terrain ${t} at ${i} is not blocked`).toBe(true);
      }
      if (blocked.has(i)) {
        expect([Terrain.Rock, Terrain.Water]).toContain(t);
      }
    }
  });

  it("keeps every start on clear, buildable ground", () => {
    for (const preset of ["highlands", "gauntlet", "riverlands"]) {
      const map = generateMap(preset, 77, 4);
      for (const s of map.starts) {
        const cx = Math.floor(s.x / TILE), cy = Math.floor(s.y / TILE);
        for (let dy = -3; dy <= 3; dy++) {
          for (let dx = -3; dx <= 3; dx++) {
            const t = map.terrain[(cy + dy) * map.cols + (cx + dx)];
            expect(TERRAIN_BUILDABLE[t], `${preset}: unbuildable ground by a start`).toBe(1);
          }
        }
      }
    }
  });

  it("never walls a start off from the middle of the map", () => {
    // Ridges and lakes are meant to shape an approach, not seal a player in.
    for (const preset of ["highlands", "gauntlet", "islands", "riverlands"]) {
      for (const seed of [1, 2, 3]) {
        const map = generateMap(preset, seed, 4);
        const grid = new NavGrid(map.worldW, map.worldH);
        for (const [cx, cy] of map.blockedCells) grid.setBlocked(cx, cy, true);
        const mid: [number, number] = [Math.floor(map.cols / 2), Math.floor(map.rows / 2)];
        for (const s of map.starts) {
          const sc: [number, number] = [Math.floor(s.x / TILE), Math.floor(s.y / TILE)];
          expect(
            grid.provablyUnreachable(sc[0], sc[1], mid[0], mid[1]),
            `${preset} seed ${seed}: a start is cut off from the centre`,
          ).toBe(false);
        }
      }
    }
  });

  it("keeps the auto-battler's arena flat", () => {
    // Warband Tactics borrows a generated map as a substrate and has its own
    // mirrored terrain on top. If the substrate ever grew hills or woodland,
    // every round would quietly inherit modifiers nobody chose — and because
    // the geography passes share an RNG stream with the resource scatter, it
    // would move every tree in the arena too.
    const map = generateMap("open_plains", 1234, 2);
    for (const t of map.terrain) {
      expect([Terrain.Grass, Terrain.GrassDark, Terrain.Dirt, Terrain.Water, Terrain.Sand]).toContain(t);
    }
  });
});
