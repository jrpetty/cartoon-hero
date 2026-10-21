## engine/gridsnap.ts — where a building actually goes, given where the cursor is.
##
## One function, used by placement, validation, the ghost preview and the drag
## helpers alike. Two properties matter:
##
##   1. **The building lands on the cell you pointed at.** Odd footprints centre
##      on the cursor's cell; even footprints centre on the nearest tile corner.
##   2. **Snapping twice is the same as snapping once** (idempotent), so it no
##      longer matters whether a coordinate has already been through here.
class_name GridSnap


## Snap one axis of a building's centre.
##
## Odd footprints (1x1, 3x3) centre on a tile *centre*, so the building covers
## the cell the cursor is in. Even footprints (2x2) centre on a tile *corner*,
## because a 2x2 block cannot be centred on a single cell — which half of the
## cell the cursor is in decides which way it extends, and it covers the cursor's
## cell either way.
static func snapBuilding(v: float, tiles: float) -> float:
	var TILE: float = C.TILE
	if int(tiles) % 2 == 1:
		return floor(v / TILE) * TILE + TILE / 2.0
	return JS.round(v / TILE) * TILE


## The top-left cell of a `tiles`-square footprint centred at (`sx`,`sy`).
static func footprintOrigin(sx: float, sy: float, tiles: float) -> Array:
	var TILE: float = C.TILE
	return [
		int(floor((sx - (tiles * TILE) / 2.0) / TILE)),
		int(floor((sy - (tiles * TILE) / 2.0) / TILE)),
	]
