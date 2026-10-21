import { describe, expect, it } from "vitest";
import { World } from "../sim/world";
import { Team } from "../sim/types";
import { generateMap } from "../maps/generator";
import { TILE } from "../content/balance";
import { BUILDINGS } from "../content/buildings";
import { footprintOrigin, snapBuilding } from "./gridsnap";
import { blockPoints, wallLinePoints } from "./wallline";

/**
 * Where a building goes, given where you pointed.
 *
 * This snap used to be the same expression copied into five places across three
 * modules, and two of them were wrong in ways nothing on screen revealed —
 * because each copy was individually consistent with the ghost, so the building
 * always went where the preview showed. The preview was simply in the wrong
 * place.
 */

const cellOf = (v: number) => Math.floor(v / TILE);

describe("Snapping a building to the grid", () => {
  it("puts an odd footprint on the cell the cursor is in, anywhere in the cell", () => {
    // The everyday bug: `round(v / TILE) * TILE + TILE/2` rounds *up* past the
    // halfway point, so a 1x1 Watch Tower placed with the cursor in the
    // right-hand half of a cell went into the cell next door.
    for (const tiles of [1, 3]) {
      for (const cx of [0, 7, 30, 199]) {
        for (const frac of [0.01, 0.25, 0.5, 0.75, 0.99]) {
          const v = (cx + frac) * TILE;
          expect(cellOf(snapBuilding(v, tiles)), `tiles=${tiles} cell=${cx} frac=${frac}`).toBe(cx);
        }
      }
    }
  });

  it("puts an even footprint on a corner, still covering the cursor's cell", () => {
    // A 2x2 cannot be centred on one cell; which half of the cell the cursor is
    // in decides which way it extends. It must cover that cell either way.
    for (const cx of [4, 30, 88]) {
      for (const frac of [0.01, 0.49, 0.51, 0.99]) {
        const v = (cx + frac) * TILE;
        const sx = snapBuilding(v, 2);
        const [c0] = footprintOrigin(sx, sx, 2);
        expect(cx >= c0 && cx < c0 + 2, `cell=${cx} frac=${frac} -> ${c0}..${c0 + 1}`).toBe(true);
      }
    }
  });

  it("is idempotent — snapping twice is snapping once", () => {
    // The property that makes the whole class of bug impossible. Without it,
    // any coordinate that crossed a module boundary risked being snapped again:
    // `wallLinePoints` returns tile centres, those went into the place command,
    // and every dragged wall run landed a cell down-right of the drag.
    for (const tiles of [1, 2, 3, 4]) {
      for (let i = 0; i < 200; i++) {
        const v = i * 7.3 - 200;
        const once = snapBuilding(v, tiles);
        expect(snapBuilding(once, tiles), `tiles=${tiles} v=${v.toFixed(1)}`).toBe(once);
      }
    }
  });

  it("handles negative coordinates without flipping a cell", () => {
    // floor and round disagree about which way to go below zero, and a map's
    // edge is where a wall most often gets dragged.
    for (const tiles of [1, 2, 3]) {
      const v = -40;
      expect(snapBuilding(snapBuilding(v, tiles), tiles)).toBe(snapBuilding(v, tiles));
    }
  });
});

describe("Every path that snaps agrees with every other", () => {
  it("the drag helpers produce coordinates the sim will not move", () => {
    for (const tiles of [1, 2, 3]) {
      for (let i = 0; i < 40; i++) {
        const v = 1000 + i * 7.3;
        const bp = blockPoints(v, v, v, v, TILE, tiles)[0];
        expect(snapBuilding(bp.x, tiles), `blockPoints tiles=${tiles}`).toBe(bp.x);
        expect(snapBuilding(bp.y, tiles), `blockPoints tiles=${tiles}`).toBe(bp.y);
      }
    }
    for (let i = 0; i < 40; i++) {
      const v = 1000 + i * 7.3;
      const pt = wallLinePoints(v, v, v, v, TILE)[0];
      // Walls are 1x1, so a wall point must survive the sim's snap untouched.
      expect(snapBuilding(pt.x, 1), "wallLinePoints drifted").toBe(pt.x);
      expect(snapBuilding(pt.y, 1), "wallLinePoints drifted").toBe(pt.y);
    }
  });

  it("a dragged wall starts and ends on the cells the player dragged between", () => {
    const from = { cx: 20, cy: 30 }, to = { cx: 27, cy: 30 };
    const pts = wallLinePoints(
      (from.cx + 0.7) * TILE, (from.cy + 0.7) * TILE,
      (to.cx + 0.2) * TILE, (to.cy + 0.2) * TILE, TILE,
    );
    expect(cellOf(pts[0].x)).toBe(from.cx);
    expect(cellOf(pts[0].y)).toBe(from.cy);
    expect(cellOf(pts[pts.length - 1].x)).toBe(to.cx);
    expect(cellOf(pts[pts.length - 1].y)).toBe(to.cy);
  });
});

describe("What actually gets built", () => {
  /** A world with a large cleared area, so only the snap decides placement. */
  function clearWorld() {
    const map = generateMap("open_plains", 5, 2);
    const w = new World(5);
    w.init(map, [{}, {}], [1, 1], [0, 1]);
    const p = w.player(Team.Player);
    p.resources = { food: 9e5, wood: 9e5, gold: 9e5 };
    p.age = 2;
    for (let cy = 18; cy < 62; cy++) {
      for (let cx = 18; cx < 62; cx++) w.grid.setBlocked(cx, cy, false);
    }
    return w;
  }

  it("covers the cell the cursor was in, for every footprint size", () => {
    // The claim a player would make: the block goes where I put it.
    const w = clearWorld();
    for (const type of ["watch_tower", "house", "barracks", "town_center"]) {
      const tiles = BUILDINGS[type].tiles;
      for (let i = 0; i < 6; i++) {
        const cx = 24 + i * 4, cy = 30;
        for (const [fx, fy] of [[0.05, 0.05], [0.5, 0.5], [0.95, 0.95], [0.95, 0.05]]) {
          const b = w.placeBuilding(Team.Player, type, (cx + fx) * TILE, (cy + fy) * TILE);
          expect(b, `${type} could not be placed at ${cx},${cy}`).not.toBeNull();
          const [c0x, c0y] = footprintOrigin(b!.x, b!.y, tiles);
          const covers = cx >= c0x && cx < c0x + tiles && cy >= c0y && cy < c0y + tiles;
          expect(covers,
            `${type} (${tiles}x${tiles}) cursor ${cx},${cy} @${fx},${fy} landed on ${c0x}..${c0x + tiles - 1}`)
            .toBe(true);
          b!.alive = false;
          w.grid.stampFootprint(b!.x, b!.y, tiles, false);
        }
      }
    }
  });

  it("lands exactly where the ghost said, given the ghost's own coordinates", () => {
    // The preview passes the raw cursor position through the same snap. Feeding
    // the sim the snapped result must not move it — which is the double-snap
    // trap that shifted dragged walls.
    const w = clearWorld();
    for (const type of ["watch_tower", "house", "town_center"]) {
      const tiles = BUILDINGS[type].tiles;
      const wx = 30.8 * TILE, wy = 41.3 * TILE;
      const ghostX = snapBuilding(wx, tiles), ghostY = snapBuilding(wy, tiles);
      const b = w.placeBuilding(Team.Player, type, ghostX, ghostY);
      expect(b, `${type} refused the ghost's own coordinates`).not.toBeNull();
      expect(b!.x, `${type} moved away from the ghost`).toBe(ghostX);
      expect(b!.y, `${type} moved away from the ghost`).toBe(ghostY);
      b!.alive = false;
      w.grid.stampFootprint(b!.x, b!.y, tiles, false);
    }
  });

  it("blocks exactly the cells it occupies, no more and no less", () => {
    // A footprint that stamps the wrong cells is the same bug wearing a
    // different hat: units path around ground the building is not on.
    const w = clearWorld();
    const b = w.placeBuilding(Team.Player, "barracks", 30.6 * TILE, 30.6 * TILE)!;
    const tiles = BUILDINGS.barracks.tiles;
    const [c0x, c0y] = footprintOrigin(b.x, b.y, tiles);
    for (let y = -1; y <= tiles; y++) {
      for (let x = -1; x <= tiles; x++) {
        const inside = x >= 0 && x < tiles && y >= 0 && y < tiles;
        expect(w.grid.isBlocked(c0x + x, c0y + y), `cell ${c0x + x},${c0y + y}`).toBe(inside);
      }
    }
  });

  it("agrees with canPlace about where a building would go", () => {
    // canPlace and placeBuilding snap separately; if they ever disagree the
    // ghost turns green on ground the placement then refuses.
    const w = clearWorld();
    for (const type of ["watch_tower", "house", "barracks"]) {
      for (let i = 0; i < 10; i++) {
        const wx = (25 + i * 1.7) * TILE, wy = (35 + i * 0.9) * TILE;
        const allowed = w.canPlace(Team.Player, type, wx, wy);
        const b = w.placeBuilding(Team.Player, type, wx, wy);
        expect(b !== null, `${type} at ${wx},${wy}: canPlace=${allowed}`).toBe(allowed);
        if (b) {
          b.alive = false;
          w.grid.stampFootprint(b.x, b.y, BUILDINGS[type].tiles, false);
        }
      }
    }
  });

  it("never leaves a one-cell gap between two adjacent walls", () => {
    // Walls are the case where being a cell out is most obvious: a run laid
    // beside another must butt against it.
    const w = clearWorld();
    const a = w.placeBuilding(Team.Player, "palisade", 30.5 * TILE, 30.5 * TILE)!;
    const b = w.placeBuilding(Team.Player, "palisade", 31.5 * TILE, 30.5 * TILE)!;
    expect(b.x - a.x, "adjacent wall segments are not adjacent").toBe(TILE);
  });
});
