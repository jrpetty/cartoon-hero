## pathfinding/astar.ts — A* on the nav grid for single-unit pathing. Returns a
## list of world-space waypoints (flattened [x0,y0,x1,y1,...]) or null if
## unreachable. Includes a simple line-of-sight smoothing pass so paths don't
## hug every cell corner.
##
## Bit-for-bit with the TS build: g-scores live in a float32 store exactly as the
## TS Float32Array does (a relaxation compares a float64 candidate against a
## float32-rounded stored value), the heap's comparisons and swaps are the same,
## and the neighbour order is the same — so the same equally-short path wins.
class_name AStar

# Neighbour offsets, flattened. The order matters: it decides the order equal-
# cost nodes enter the heap, which decides which of several equally short paths
# a unit takes. Keep it as it is unless you mean to change every path.
const DX := [1, -1, 0, 0, 1, 1, -1, -1]
const DY := [0, 0, 1, -1, 1, -1, 1, -1]
const DC := [1.0, 1.0, 1.0, 1.0, 1.4142, 1.4142, 1.4142, 1.4142]

# Scratch state, reused across every search, stamped with a generation counter:
# an entry counts as "set this search" only when its stamp matches, so starting
# a search is O(1) instead of O(cells). The buffers only ever grow.
static var gScore := PackedFloat32Array()
static var gStamp := PackedInt64Array()
static var cameFrom := PackedInt32Array()
static var closedStamp := PackedInt64Array()
static var heapF := PackedFloat64Array()
static var heapI := PackedInt32Array()
static var heapLen := 0
static var generation := 0


static func ensureCapacity(n: int) -> void:
	if gScore.size() >= n:
		return
	gScore = PackedFloat32Array()
	gScore.resize(n)
	gScore.fill(0.0)
	gStamp = PackedInt64Array()
	gStamp.resize(n)
	gStamp.fill(0)
	cameFrom = PackedInt32Array()
	cameFrom.resize(n)
	cameFrom.fill(0)
	closedStamp = PackedInt64Array()
	closedStamp.resize(n)
	closedStamp.fill(0)
	generation = 0 # fresh (zeroed) stamps, so restart the counter


## The open list is *not* bounded by the number of cells: a cell is pushed again
## every time a cheaper route to it is found, so it grows on demand.
static func growHeap() -> void:
	var cap := maxi(1024, heapF.size() * 2)
	heapF.resize(cap)
	heapI.resize(cap)


static func heapPush(i: int, f: float) -> void:
	if heapLen >= heapF.size():
		growHeap()
	var c := heapLen
	heapLen += 1
	heapF[c] = f
	heapI[c] = i
	while c > 0:
		var p := (c - 1) >> 1
		if heapF[p] <= heapF[c]:
			break
		var tf := heapF[p]
		var ti := heapI[p]
		heapF[p] = heapF[c]
		heapI[p] = heapI[c]
		heapF[c] = tf
		heapI[c] = ti
		c = p


static func heapPop() -> int:
	var top := heapI[0]
	heapLen -= 1
	var lastF := heapF[heapLen]
	var lastI := heapI[heapLen]
	if heapLen > 0:
		heapF[0] = lastF
		heapI[0] = lastI
		var p := 0
		while true:
			var l := 2 * p + 1
			var r := 2 * p + 2
			var s := p
			if l < heapLen and heapF[l] < heapF[s]:
				s = l
			if r < heapLen and heapF[r] < heapF[s]:
				s = r
			if s == p:
				break
			var tf := heapF[p]
			var ti := heapI[p]
			heapF[p] = heapF[s]
			heapI[p] = heapI[s]
			heapF[s] = tf
			heapI[s] = ti
			p = s
	return top


## Returns an Array of floats [x0,y0,x1,y1,...] or null.
static func findPath(grid: NavGrid, sx: float, sy: float, tx: float, ty: float, maxNodes: int = 6000):
	var scx := grid.worldToCellX(sx)
	var scy := grid.worldToCellY(sy)
	var tcx := grid.worldToCellX(tx)
	var tcy := grid.worldToCellY(ty)

	if not grid.inBounds(scx, scy):
		return null
	# If we're sitting on a blocked cell (shoved onto a footprint, spawned tight),
	# step out to the nearest open cell so the search has somewhere to begin.
	if grid.isBlocked(scx, scy):
		var o: Array = grid.nearestOpenWorld(sx, sy)
		scx = grid.worldToCellX(o[0])
		scy = grid.worldToCellY(o[1])
	# If the goal cell is blocked, retarget to the nearest open cell.
	if grid.isBlocked(tcx, tcy):
		var gx := tcx
		var gy := tcy
		var o: Array = grid.nearestOpenWorld(tx, ty)
		tcx = grid.worldToCellX(o[0])
		tcy = grid.worldToCellY(o[1])
		# ...but one we can actually get to. The nearest open cell to a building
		# can be a gap sealed in by its neighbours, and aiming a villager at it
		# made every trip home "provably unreachable". Only when the usual choice
		# is unreachable do we look further, so every path that already worked is
		# unchanged.
		if grid.provablyUnreachable(scx, scy, tcx, tcy):
			var alt = grid.nearestOpenCellInRegion(gx, gy, grid.regionAt(scx, scy))
			if alt != null:
				tcx = alt[0]
				tcy = alt[1]
	if scx == tcx and scy == tcy:
		return [tx, ty]
	# Nothing to search for if the two ends are in different pockets of the map.
	if grid.provablyUnreachable(scx, scy, tcx, tcy):
		return null

	var cols := grid.cols
	var rows := grid.rows
	var blocked := grid.blocked
	if Native.available():
		var flat := Native.astar_find(blocked, cols, rows, grid.TILE, scx, scy, tcx, tcy, maxNodes, tx, ty)
		if flat.is_empty():
			return null
		return Array(flat)
	var n := cols * rows
	ensureCapacity(n)
	# Stamps live in a Uint32Array in TS, so the counter has to stay inside it.
	if generation >= 0xfffffffe:
		gStamp.fill(0)
		closedStamp.fill(0)
		generation = 0
	generation += 1
	var gen := generation
	heapLen = 0

	var start := scy * cols + scx
	gScore[start] = 0.0
	gStamp[start] = gen
	cameFrom[start] = -1 # the chain terminator; nothing else needs clearing
	if true:
		var dx := absi(scx - tcx)
		var dy := absi(scy - tcy)
		heapPush(start, float(dx + dy) + (1.4142 - 2.0) * mini(dx, dy))
	var goal := tcy * cols + tcx
	var expanded := 0

	while heapLen > 0:
		var cur := heapPop()
		if cur == goal:
			return reconstruct(grid, cur, tx, ty)
		if closedStamp[cur] == gen:
			continue
		closedStamp[cur] = gen
		expanded += 1
		if expanded > maxNodes:
			break
		var ccx := cur % cols
		var ccy := cur / cols
		var gCur := gScore[cur]
		for k in 8:
			var nx: int = ccx + DX[k]
			var ny: int = ccy + DY[k]
			if nx < 0 or ny < 0 or nx >= cols or ny >= rows:
				continue
			var ni := ny * cols + nx
			if blocked[ni] == 1:
				continue
			# Prevent diagonal corner cutting through two blocked orthogonals.
			if k >= 4:
				var sideX := nx < 0 or nx >= cols or ccy < 0 or ccy >= rows or blocked[ccy * cols + nx] == 1
				var sideY := ccx < 0 or ccx >= cols or ny < 0 or ny >= rows or blocked[ny * cols + ccx] == 1
				if sideX and sideY:
					continue
			if closedStamp[ni] == gen:
				continue
			var ng: float = gCur + DC[k]
			if gStamp[ni] != gen or ng < gScore[ni]:
				gScore[ni] = ng
				gStamp[ni] = gen
				cameFrom[ni] = cur
				var dx := absi(nx - tcx)
				var dy := absi(ny - tcy)
				# Group the heuristic exactly as the TS does: floating-point
				# addition is not associative.
				var hn := float(dx + dy) + (1.4142 - 2.0) * mini(dx, dy)
				heapPush(ni, ng + hn)
	return null


static func reconstruct(grid: NavGrid, end: int, tx: float, ty: float) -> Array:
	var cells: Array = []
	var c := end
	while c != -1:
		cells.append(c)
		c = cameFrom[c]
	cells.reverse()
	return reconstructCells(grid, cells, tx, ty)


## Cells (start..goal) to smoothed world waypoints.
static func reconstructCells(grid: NavGrid, cells, tx: float, ty: float) -> Array:
	# Convert to world coords, then smooth via line-of-sight.
	var pts: Array = []
	for ci in cells:
		var cx: int = ci % grid.cols
		var cy: int = ci / grid.cols
		pts.append(grid.cellCenterX(cx))
		pts.append(grid.cellCenterY(cy))
	# Replace last point with the actual requested target for precision.
	if pts.size() >= 2:
		pts[pts.size() - 2] = tx
		pts[pts.size() - 1] = ty
	return smooth(grid, pts)


## Drop intermediate waypoints that have clear line of sight.
static func smooth(grid: NavGrid, pts: Array) -> Array:
	if pts.size() <= 4:
		return pts
	var out: Array = [pts[0], pts[1]]
	var anchorX: float = pts[0]
	var anchorY: float = pts[1]
	var i := 2
	while i < pts.size() - 2:
		var nextX: float = pts[i + 2]
		var nextY: float = pts[i + 3]
		if not lineClear(grid, anchorX, anchorY, nextX, nextY):
			out.append(pts[i])
			out.append(pts[i + 1])
			anchorX = pts[i]
			anchorY = pts[i + 1]
		i += 2
	out.append(pts[pts.size() - 2])
	out.append(pts[pts.size() - 1])
	return out


## Clear line of sight between two world points, keeping a perpendicular
## `clearance` band free so smoothed paths don't shave building corners.
static func lineClear(grid: NavGrid, x0: float, y0: float, x1: float, y1: float, clearance: float = 11.0) -> bool:
	var dx := x1 - x0
	var dy := y1 - y0
	var len := JS.hypot(dx, dy)
	if len == 0.0:
		len = 1.0
	var steps := int(ceil(len / 12.0))
	# Unit perpendicular, scaled to the clearance we want on either side.
	var px := (-dy / len) * clearance
	var py := (dx / len) * clearance
	for i in range(0, steps + 1):
		var t := float(i) / steps
		var cx := x0 + dx * t
		var cy := y0 + dy * t
		if grid.isBlockedWorld(cx, cy):
			return false
		if grid.isBlockedWorld(cx + px, cy + py):
			return false
		if grid.isBlockedWorld(cx - px, cy - py):
			return false
	return true
