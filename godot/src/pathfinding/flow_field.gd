## pathfinding/flowfield.ts — flow field for group movement. We run a
## Dijkstra/BFS from the goal across the whole reachable grid to get an
## integration (distance) field, then derive a per-cell direction pointing
## "downhill" toward the goal. A whole selection shares one field, so large
## armies flow around obstacles cleanly instead of each unit computing its own
## A* path (cheaper and visually better).
##
## The direction store is float32, as in the TS build (Float32Array): a
## diagonal step samples as 0.70710676…, not 0.70710678…, and units moving along
## it land exactly where the browser build's do.
class_name FlowField
extends RefCounted

const NEI := [
	[1, 0, 1.0],
	[-1, 0, 1.0],
	[0, 1, 1.0],
	[0, -1, 1.0],
	[1, 1, 1.4142],
	[1, -1, 1.4142],
	[-1, 1, 1.4142],
	[-1, -1, 1.4142],
]

var cost: PackedFloat64Array ## integration field (distance-to-goal)
var dirX: PackedFloat32Array
var dirY: PackedFloat32Array
var goalX: float
var goalY: float
var grid: NavGrid


func _init(g: NavGrid, goalWorldX: float, goalWorldY: float) -> void:
	grid = g
	var n := grid.cols * grid.rows
	cost = PackedFloat64Array()
	cost.resize(n)
	cost.fill(INF)
	dirX = PackedFloat32Array()
	dirX.resize(n)
	dirX.fill(0.0)
	dirY = PackedFloat32Array()
	dirY.resize(n)
	dirY.fill(0.0)
	goalX = goalWorldX
	goalY = goalWorldY
	build(goalWorldX, goalWorldY)


func build(gx: float, gy: float) -> void:
	var gcx := grid.worldToCellX(gx)
	var gcy := grid.worldToCellY(gy)
	if grid.isBlocked(gcx, gcy):
		var o: Array = grid.nearestOpenWorld(gx, gy)
		gcx = grid.worldToCellX(o[0])
		gcy = grid.worldToCellY(o[1])
	if not grid.inBounds(gcx, gcy):
		return

	var cols := grid.cols
	var rows := grid.rows
	# Dijkstra with a binary heap and a closed set. The closed set is load-
	# bearing: without it, float round-up in the cost store can make a
	# relaxation look like an improvement forever and grow the heap unboundedly.
	var closed := PackedByteArray()
	closed.resize(cols * rows)
	closed.fill(0)
	var start := grid.idx(gcx, gcy)
	cost[start] = 0.0
	# The heap of {c, i} objects as two parallel arrays; the comparisons and the
	# swaps are exactly the TS ones, so the pop order is the same.
	var hc := PackedFloat64Array([0.0])
	var hi := PackedInt32Array([start])

	while hc.size() > 0:
		# pop
		var c := hc[0]
		var i := hi[0]
		var last := hc.size() - 1
		var lastC := hc[last]
		var lastI := hi[last]
		hc.resize(last)
		hi.resize(last)
		if last > 0:
			hc[0] = lastC
			hi[0] = lastI
			var p := 0
			while true:
				var l := 2 * p + 1
				var r := 2 * p + 2
				var s := p
				if l < last and hc[l] < hc[s]:
					s = l
				if r < last and hc[r] < hc[s]:
					s = r
				if s == p:
					break
				var tc := hc[p]
				var ti := hi[p]
				hc[p] = hc[s]
				hi[p] = hi[s]
				hc[s] = tc
				hi[s] = ti
				p = s
		if closed[i] != 0:
			continue
		closed[i] = 1
		if c > cost[i]:
			continue
		var cx := i % cols
		var cy := i / cols
		for nb in NEI:
			var dx: int = nb[0]
			var dy: int = nb[1]
			var w: float = nb[2]
			var nx := cx + dx
			var ny := cy + dy
			if grid.isBlocked(nx, ny):
				continue
			if dx != 0 and dy != 0:
				if grid.isBlocked(cx + dx, cy) and grid.isBlocked(cx, cy + dy):
					continue
			var ni := ny * cols + nx
			if closed[ni] != 0:
				continue
			var nc := c + w
			if nc < cost[ni]:
				cost[ni] = nc
				# push
				hc.push_back(nc)
				hi.push_back(ni)
				var ci := hc.size() - 1
				while ci > 0:
					var pp := (ci - 1) >> 1
					if hc[pp] <= hc[ci]:
						break
					var tc2 := hc[pp]
					var ti2 := hi[pp]
					hc[pp] = hc[ci]
					hi[pp] = hi[ci]
					hc[ci] = tc2
					hi[ci] = ti2
					ci = pp

	# Derive directions: point toward the lowest-cost neighbor.
	for cy in rows:
		for cx in cols:
			var i := cy * cols + cx
			if cost[i] == INF:
				continue
			var best := cost[i]
			var bx := 0
			var by := 0
			for nb in NEI:
				var dx: int = nb[0]
				var dy: int = nb[1]
				var nx := cx + dx
				var ny := cy + dy
				if nx < 0 or ny < 0 or nx >= cols or ny >= rows:
					continue
				# Never point diagonally through a blocked corner — followers would
				# clip into the wall.
				if dx != 0 and dy != 0 and (grid.isBlocked(cx + dx, cy) or grid.isBlocked(cx, cy + dy)):
					continue
				var nc := cost[ny * cols + nx]
				if nc < best:
					best = nc
					bx = dx
					by = dy
			var len := JS.hypot(bx, by)
			if len == 0.0:
				len = 1.0
			dirX[i] = bx / len
			dirY[i] = by / len


## Sample the field at a world position; returns a unit direction [x, y]
## ([0, 0] if at goal/unreachable).
func sample(wx: float, wy: float) -> Array:
	var cx := grid.worldToCellX(wx)
	var cy := grid.worldToCellY(wy)
	if not grid.inBounds(cx, cy):
		return [0.0, 0.0]
	var i := grid.idx(cx, cy)
	return [dirX[i], dirY[i]]


func reached(wx: float, wy: float) -> bool:
	var cx := grid.worldToCellX(wx)
	var cy := grid.worldToCellY(wy)
	if not grid.inBounds(cx, cy):
		return false
	return cost[grid.idx(cx, cy)] < 1.5
