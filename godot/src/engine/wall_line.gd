## engine/wallline.ts — cells a drag-painted wall should occupy, from the start
## cursor cell to the end cursor cell. Uses an integer grid line-walk
## (Bresenham) so the run is gap-free and reaches the exact endpoint.
##
## A WallCell is a Dictionary `{"x": float, "y": float}` (world-space centre).
class_name WallLine


static func wallLinePoints(wx0: float, wy0: float, wx1: float, wy1: float, tile: float, maxCells: int = 256) -> Array:
	# floor, not round: the cell the cursor is *in*, not the nearest boundary. A
	# wall run should start and end on the cells the player actually dragged
	# between, and rounding pushed both ends half a cell along the drag.
	var x0 := int(floor(wx0 / tile))
	var y0 := int(floor(wy0 / tile))
	var x1 := int(floor(wx1 / tile))
	var y1 := int(floor(wy1 / tile))
	var dx := absi(x1 - x0)
	var dy := absi(y1 - y0)
	var sx := 1 if x0 < x1 else -1
	var sy := 1 if y0 < y1 else -1
	var err := dx - dy
	var out: Array = []
	for guard in range(0, maxCells + 1):
		out.append({"x": x0 * tile + tile / 2.0, "y": y0 * tile + tile / 2.0})
		if x0 == x1 and y0 == y1:
			break
		var e2 := 2 * err
		if e2 > -dy:
			err -= dy
			x0 += sx
		if e2 < dx:
			err += dx
			y0 += sy
	return out


## Snapped centres filling the dragged rectangle with a block of `tiles`-wide
## buildings, in reading order.
##
## Each building is spaced one tile clear of its neighbour so villagers can still
## walk between them and so the whole block doesn't become an accidental wall.
## Reading order matters: builders are handed out round-robin along this list.
static func blockPoints(wx0: float, wy0: float, wx1: float, wy1: float, tile: float, tiles: float, maxCells: int = 64) -> Array:
	var pitch := (tiles + 1.0) * tile # footprint plus a one-tile walking gap
	# The same snap the sim uses, so the preview and the placement cannot drift apart.
	var ax := GridSnap.snapBuilding(minf(wx0, wx1), tiles)
	var ay := GridSnap.snapBuilding(minf(wy0, wy1), tiles)
	var bx := GridSnap.snapBuilding(maxf(wx0, wx1), tiles)
	var by := GridSnap.snapBuilding(maxf(wy0, wy1), tiles)
	var cols := maxi(1, int(floor((bx - ax) / pitch)) + 1)
	var rows := maxi(1, int(floor((by - ay) / pitch)) + 1)
	var out: Array = []
	for r in rows:
		for c in cols:
			if out.size() >= maxCells:
				return out
			out.append({"x": ax + c * pitch, "y": ay + r * pitch})
	return out
