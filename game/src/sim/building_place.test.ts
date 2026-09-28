import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team } from "./types";
import { generateMap } from "../maps/generator";
import { TILE } from "../content/balance";

// Regression: placeBuilding used to snap the cursor, then spawnBuilding snapped
// AGAIN — double-snapping odd-footprint buildings a whole tile down-and-right of
// the ghost. A placed building must land exactly where the (single) snap puts it.
describe("Building placement snapping", () => {
  function clearWorld(seed: number) {
    const map = generateMap("open_plains", seed, 2);
    const w = new World(seed);
    w.init(map, [{}, {}], [1, 1]);
    const p = w.player(Team.Player);
    p.resources.wood = 99999;
    p.resources.food = 99999;
    p.resources.gold = 99999;
    return { w, map };
  }

  // Find a spot near the map centre that the engine reports as placeable.
  function clearSpot(w: World, map: { worldW: number; worldH: number }, type: string) {
    for (let r = 0; r < 40; r++) {
      const cx = map.worldW / 2 + (r % 8) * TILE * 3 + 7;
      const cy = map.worldH / 2 + ((r / 8) | 0) * TILE * 3 + 5;
      if (w.canPlace(Team.Player, type, cx, cy)) return { cx, cy };
    }
    return null;
  }

  it("places an odd-footprint building exactly on the single-snapped tile (no drift)", () => {
    const { w, map } = clearWorld(123);
    const spot = clearSpot(w, map, "town_center")!; // 3x3 = odd
    expect(spot).not.toBeNull();
    const tiles = 3;
    const expX = Math.round(spot.cx / TILE) * TILE + (tiles % 2 === 1 ? TILE / 2 : 0);
    const expY = Math.round(spot.cy / TILE) * TILE + (tiles % 2 === 1 ? TILE / 2 : 0);
    const e = w.placeBuilding(Team.Player, "town_center", spot.cx, spot.cy)!;
    expect(e).not.toBeNull();
    expect(e.x).toBe(expX);
    expect(e.y).toBe(expY);
  });

  it("ghost snap (canPlace) and the placed building agree — WYSIWYG", () => {
    const { w, map } = clearWorld(777);
    const spot = clearSpot(w, map, "town_center")!;
    const tiles = 3;
    const snapX = Math.round(spot.cx / TILE) * TILE + (tiles % 2 === 1 ? TILE / 2 : 0);
    const snapY = Math.round(spot.cy / TILE) * TILE + (tiles % 2 === 1 ? TILE / 2 : 0);
    // canPlace validated this snapped footprint; the building must land on it,
    // not a tile away (the old double-snap drifted odd footprints +1 tile).
    const e = w.placeBuilding(Team.Player, "town_center", spot.cx, spot.cy)!;
    expect(e.x).toBe(snapX);
    expect(e.y).toBe(snapY);
  });
});
