## pathfinding/grid.ts — navigation grid. Each cell is passable or blocked (by
## buildings, resource nodes, water/cliffs). Units path on this grid; buildings
## stamp footprints.
class_name NavGrid
extends RefCounted

var worldW: float
var worldH: float
var cols: int
var rows: int
var blocked: PackedByteArray ## 1 = impassable terrain/building
var TILE: float

# ---- connected components ------------------------------------------------
# Which cells can reach which. Two cells with different labels have no route
# between them at all, and A* can say so in constant time instead of
# exhausting its node budget to find out. Labels are rebuilt lazily, and only
# after the grid actually changes.

## Component label per cell; -1 for blocked cells. Do not read directly.
var region: PackedInt32Array = PackedInt32Array()
var _hasRegion := false
var regionsDirty := true


func _init(world_w: float, world_h: float) -> void:
	worldW = world_w
	worldH = world_h
	TILE = C.TILE
	cols = int(ceil(worldW / TILE))
	rows = int(ceil(worldH / TILE))
	blocked = PackedByteArray()
	blocked.resize(cols * rows)
	blocked.fill(0)


func idx(cx: int, cy: int) -> int:
	return cy * cols + cx


func inBounds(cx: int, cy: int) -> bool:
	return cx >= 0 and cy >= 0 and cx < cols and cy < rows


func worldToCellX(wx: float) -> int:
	return int(floor(wx / TILE))


func worldToCellY(wy: float) -> int:
	return int(floor(wy / TILE))


func cellCenterX(cx: int) -> float:
	return cx * TILE + TILE / 2.0


func cellCenterY(cy: int) -> float:
	return cy * TILE + TILE / 2.0


func isBlocked(cx: int, cy: int) -> bool:
	if cx < 0 or cy < 0 or cx >= cols or cy >= rows:
		return true
	return blocked[cy * cols + cx] == 1


func isBlockedWorld(wx: float, wy: float) -> bool:
	var cx := int(floor(wx / TILE))
	var cy := int(floor(wy / TILE))
	if cx < 0 or cy < 0 or cx >= cols or cy >= rows:
		return true
	return blocked[cy * cols + cx] == 1


func setBlocked(cx: int, cy: int, v: bool) -> void:
	if not inBounds(cx, cy):
		return
	var i := idx(cx, cy)
	var next := 1 if v else 0
	if blocked[i] == next:
		return
	blocked[i] = next
	regionsDirty = true


## The component a cell belongs to, or -1 if it is blocked or out of bounds.
func regionAt(cx: int, cy: int) -> int:
	if not inBounds(cx, cy):
		return -1
	if regionsDirty or not _hasRegion:
		rebuildRegions()
	return region[idx(cx, cy)]


## True when the two cells provably have no route between them. Deliberately
## *not* the inverse of "connected": if either end sits on a blocked cell the
## answer is "don't know", because a unit shoved onto a footprint can still
## walk out through its open neighbours, and that case has to keep taking the
## full search exactly as it did before.
func provablyUnreachable(ax: int, ay: int, bx: int, by: int) -> bool:
	var ra := regionAt(ax, ay)
	if ra < 0:
		return false
	var rb := regionAt(bx, by)
	if rb < 0:
		return false
	return ra != rb


## Flood-fill every open cell into a component. O(cells), run on demand.
func rebuildRegions() -> void:
	var n := cols * rows
	if not _hasRegion or region.size() != n:
		region = PackedInt32Array()
		region.resize(n)
		_hasRegion = true
	region.fill(-1)
	# One shared queue for the whole pass; each cell is enqueued once.
	var queue := PackedInt32Array()
	queue.resize(n)
	var label := 0
	for seed in n:
		if blocked[seed] == 1 or region[seed] != -1:
			continue
		var head := 0
		var tail := 0
		queue[tail] = seed
		tail += 1
		region[seed] = label
		while head < tail:
			var cur := queue[head]
			head += 1
			var cx := cur % cols
			var cy := cur / cols
			# Four-way only: A* may move diagonally, but never *between* two
			# blocked orthogonals, so a diagonal-only link is not a real route.
			if cx > 0:
				var i := cur - 1
				if blocked[i] == 0 and region[i] == -1:
					region[i] = label
					queue[tail] = i
					tail += 1
			if cx < cols - 1:
				var i := cur + 1
				if blocked[i] == 0 and region[i] == -1:
					region[i] = label
					queue[tail] = i
					tail += 1
			if cy > 0:
				var i := cur - cols
				if blocked[i] == 0 and region[i] == -1:
					region[i] = label
					queue[tail] = i
					tail += 1
			if cy < rows - 1:
				var i := cur + cols
				if blocked[i] == 0 and region[i] == -1:
					region[i] = label
					queue[tail] = i
					tail += 1
		label += 1
	regionsDirty = false


## Stamp a tiles x tiles footprint centered (roughly) at a world position.
func stampFootprint(wx: float, wy: float, tiles: float, v: bool) -> void:
	var t := int(tiles)
	var c0x := worldToCellX(wx - (tiles * TILE) / 2.0)
	var c0y := worldToCellY(wy - (tiles * TILE) / 2.0)
	for y in t:
		for x in t:
			setBlocked(c0x + x, c0y + y, v)


## Is a tiles x tiles footprint fully clear (for placement validation)?
func footprintClear(wx: float, wy: float, tiles: float) -> bool:
	var t := int(tiles)
	var c0x := worldToCellX(wx - (tiles * TILE) / 2.0)
	var c0y := worldToCellY(wy - (tiles * TILE) / 2.0)
	for y in t:
		for x in t:
			if isBlocked(c0x + x, c0y + y):
				return false
	return true


## Find the nearest passable cell to a world point (BFS spiral). Returns [x, y].
func nearestOpenWorld(wx: float, wy: float, maxRadius: int = 12) -> Array:
	var cx := worldToCellX(wx)
	var cy := worldToCellY(wy)
	cx = maxi(0, mini(cols - 1, cx))
	cy = maxi(0, mini(rows - 1, cy))
	if not isBlocked(cx, cy):
		return [cellCenterX(cx), cellCenterY(cy)]
	for r in range(1, maxRadius + 1):
		for dy in range(-r, r + 1):
			for dx in range(-r, r + 1):
				if absi(dx) != r and absi(dy) != r:
					continue
				if not isBlocked(cx + dx, cy + dy):
					return [cellCenterX(cx + dx), cellCenterY(cy + dy)]
	return [wx, wy]


## The nearest open cell to (cx, cy) that is in component `region`, searched
## in the same ring order as `nearestOpenWorld`, or null within `maxRadius`.
func nearestOpenCellInRegion(cx: int, cy: int, reg: int, maxRadius: int = 12):
	for r in range(1, maxRadius + 1):
		for dy in range(-r, r + 1):
			for dx in range(-r, r + 1):
				if absi(dx) != r and absi(dy) != r:
					continue
				if regionAt(cx + dx, cy + dy) == reg:
					return [cx + dx, cy + dy]
	return null
