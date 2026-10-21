// Where a building actually goes, given where the cursor is.
//
// One function, used by placement, validation, the ghost preview and the drag
// helpers alike. It used to be the same expression copied into five places
// across three modules, which is how the two bugs below survived: each copy was
// individually consistent with the ghost, so nothing looked wrong on screen.
//
// Two properties matter, and the old formula had neither:
//
//   1. **The building lands on the cell you pointed at.** The old rule for odd
//      footprints was `round(v / TILE) * TILE + TILE/2`, which rounds *up* past
//      the halfway point of a cell — so a 1x1 Watch Tower placed with the cursor
//      in the right-hand half of a cell went into the cell next door. The ghost
//      agreed with it, so the tower went where the ghost was; the ghost was
//      simply half a cell adrift from the cursor.
//
//   2. **Snapping twice is the same as snapping once.** Feeding the old rule its
//      own output shifted odd footprints a whole cell every time
//      (`round(cx + 0.5) === cx + 1`). placeBuilding carried a comment warning
//      callers to pass raw coordinates for exactly this reason — but
//      `wallLinePoints` returns tile *centres*, and those went straight into the
//      place command, so every dragged wall run was laid one cell down-right of
//      the line the player dragged.
//
// Idempotence is the property that makes the warning unnecessary: it no longer
// matters whether a coordinate has already been through here.

import { TILE } from "../content/balance";

/**
 * Snap one axis of a building's centre.
 *
 * Odd footprints (1x1, 3x3) centre on a tile *centre*, so the building covers
 * the cell the cursor is in. Even footprints (2x2) centre on a tile *corner*,
 * because a 2x2 block cannot be centred on a single cell — which half of the
 * cell the cursor is in decides which way it extends, and it covers the cursor's
 * cell either way.
 */
export function snapBuilding(v: number, tiles: number): number {
  return tiles % 2 === 1
    ? Math.floor(v / TILE) * TILE + TILE / 2
    : Math.round(v / TILE) * TILE;
}

/** The top-left cell of a `tiles`-square footprint centred at (`sx`,`sy`). */
export function footprintOrigin(sx: number, sy: number, tiles: number): [number, number] {
  return [
    Math.floor((sx - (tiles * TILE) / 2) / TILE),
    Math.floor((sy - (tiles * TILE) / 2) / TILE),
  ];
}
