extends TestCase
# Ported from pathfinding/path.test.ts, engine/wallline.test.ts and the pure
# half of engine/gridsnap.test.ts.

const TILE := 32.0


func makeGrid() -> NavGrid:
	return NavGrid.new(32 * TILE, 32 * TILE)


func pathClear(g: NavGrid, path: Array) -> bool:
	var i := 0
	while i < path.size():
		if g.isBlockedWorld(path[i], path[i + 1]):
			return false
		i += 2
	return true


# ---- A* ----

func test_finds_a_straight_path_on_open_ground() -> void:
	var path = AStar.findPath(makeGrid(), 50, 50, 500, 500)
	check(path != null)
	near(path[path.size() - 2], 500, 1e-6)
	near(path[path.size() - 1], 500, 1e-6)


func test_routes_around_a_wall() -> void:
	var g := makeGrid()
	for cy in 32:
		if cy != 20:
			g.setBlocked(10, cy, true)
	var path = AStar.findPath(g, 5 * TILE, 5 * TILE, 20 * TILE, 5 * TILE)
	check(path != null)
	var maxY := 0.0
	var i := 1
	while i < path.size():
		maxY = maxf(maxY, path[i])
		i += 2
	check(maxY > 17 * TILE, "went through the gap")
	check(pathClear(g, path), "no waypoint in a wall")


func test_a_sealed_goal_never_routes_through_walls() -> void:
	var g := makeGrid()
	for d in range(-2, 3):
		g.setBlocked(16 + d, 14, true)
		g.setBlocked(16 + d, 18, true)
		g.setBlocked(14, 16 + d, true)
		g.setBlocked(18, 16 + d, true)
	var path = AStar.findPath(g, 2 * TILE, 2 * TILE, 16 * TILE + 8, 16 * TILE + 8)
	if path != null:
		check(pathClear(g, path))


func test_flow_field_directions_lead_downhill_to_the_goal() -> void:
	var g := makeGrid()
	for cy in range(8, 24):
		g.setBlocked(15, cy, true)
	var goalX := 25 * TILE
	var goalY := 16 * TILE
	var ff := FlowField.new(g, goalX, goalY)
	var x := 4 * TILE
	var y := 16 * TILE
	for i in 4000:
		var d := ff.sample(x, y)
		if d[0] == 0 and d[1] == 0:
			break
		x += d[0] * 8
		y += d[1] * 8
	check(JS.hypot(x - goalX, y - goalY) < TILE * 2.5, "ended %s,%s" % [x, y])


func splitGrid(gate: bool = false) -> NavGrid:
	var g := makeGrid()
	for cy in 32:
		g.setBlocked(16, cy, true)
	if gate:
		g.setBlocked(16, 20, false)
	return g


func test_says_no_immediately_when_the_ends_are_in_different_pockets() -> void:
	eq(AStar.findPath(splitGrid(), 4 * TILE, 4 * TILE, 28 * TILE, 4 * TILE), null)
	var open := splitGrid(true)
	var path = AStar.findPath(open, 4 * TILE, 4 * TILE, 28 * TILE, 4 * TILE)
	check(path != null)
	check(pathClear(open, path))


func test_notices_a_wall_closed_or_opened_after_labels_were_built() -> void:
	var g := splitGrid(true)
	check(AStar.findPath(g, 4 * TILE, 4 * TILE, 28 * TILE, 4 * TILE) != null)
	g.setBlocked(16, 20, true)
	eq(AStar.findPath(g, 4 * TILE, 4 * TILE, 28 * TILE, 4 * TILE), null)
	g.setBlocked(16, 20, false)
	check(AStar.findPath(g, 4 * TILE, 4 * TILE, 28 * TILE, 4 * TILE) != null)


func test_never_refuses_a_route_a_full_search_would_find() -> void:
	var g := makeGrid()
	var cx := 2
	while cx < 30:
		for cy in 32:
			if cy == (31 if cx % 8 == 2 else 0):
				continue
			g.setBlocked(cx, cy, true)
		cx += 4
	for q in [[1, 1, 29, 30], [29, 1, 1, 30], [1, 15, 29, 15]]:
		var path = AStar.findPath(g, q[0] * TILE + 8, q[1] * TILE + 8, q[2] * TILE + 8, q[3] * TILE + 8)
		check(path != null, "%s" % [q])


func test_still_searches_from_inside_a_blocked_cell() -> void:
	var g := makeGrid()
	g.setBlocked(5, 5, true)
	check(AStar.findPath(g, 5 * TILE + 8, 5 * TILE + 8, 20 * TILE, 20 * TILE) != null)


func test_answers_an_unreachable_request_without_exhausting_its_budget() -> void:
	var g := NavGrid.new(160 * TILE, 160 * TILE)
	for cy in 160:
		g.setBlocked(80, cy, true)
	var t0 := Time.get_ticks_msec()
	for i in 300:
		if AStar.findPath(g, 10 * TILE, (i % 150) * TILE, 150 * TILE, 10 * TILE) != null:
			failures.append("found a route through a solid wall")
			break
	var ms := Time.get_ticks_msec() - t0
	check(ms < 1500, "%d ms for 300 unreachable requests" % ms)


func hemmed() -> NavGrid:
	var g := makeGrid()
	for y in range(10, 13):
		for x in range(10, 13):
			g.setBlocked(x, y, true)
	for c in [[9, 9], [9, 8], [10, 8], [11, 8], [12, 8], [13, 8], [13, 9]]:
		g.setBlocked(c[0], c[1], true)
	return g


func centre(c: int) -> float:
	return (c + 0.5) * TILE


func test_walking_to_a_building_hemmed_in_by_others() -> void:
	var g := hemmed()
	var o := g.nearestOpenWorld(centre(11), centre(11))
	check(g.provablyUnreachable(g.worldToCellX(o[0]), g.worldToCellY(o[1]), 11, 20), "the fixture isn't testing the sealed-gap case")
	check(AStar.findPath(g, centre(11), centre(20), centre(11), centre(11)) != null, "could not walk to it")
	var g2 := makeGrid()
	for y in range(10, 13):
		for x in range(10, 13):
			g2.setBlocked(x, y, true)
	var path: Array = AStar.findPath(g2, centre(11), centre(20), centre(11), centre(11))
	var o2 := g2.nearestOpenWorld(centre(11), centre(11))
	check(JS.hypot(path[path.size() - 4] - o2[0], path[path.size() - 3] - o2[1]) < TILE * 1.5, "an already-working path changed")


# ---- gridsnap ----

func cellOf(v: float) -> int:
	return int(floor(v / TILE))


func test_snap_puts_an_odd_footprint_on_the_cursor_cell() -> void:
	for tiles in [1, 3]:
		for cx in [0, 7, 30, 199]:
			for frac in [0.01, 0.25, 0.5, 0.75, 0.99]:
				eq(cellOf(GridSnap.snapBuilding((cx + frac) * TILE, tiles)), cx, "tiles=%d cell=%d frac=%s" % [tiles, cx, frac])


func test_snap_puts_an_even_footprint_on_a_corner_covering_the_cursor() -> void:
	for cx in [4, 30, 88]:
		for frac in [0.01, 0.49, 0.51, 0.99]:
			var sx := GridSnap.snapBuilding((cx + frac) * TILE, 2)
			var c0: int = GridSnap.footprintOrigin(sx, sx, 2)[0]
			check(cx >= c0 and cx < c0 + 2, "cell=%d frac=%s -> %d" % [cx, frac, c0])


func test_snap_is_idempotent() -> void:
	for tiles in [1, 2, 3, 4]:
		for i in 200:
			var v := i * 7.3 - 200
			var once := GridSnap.snapBuilding(v, tiles)
			eq(GridSnap.snapBuilding(once, tiles), once, "tiles=%d v=%s" % [tiles, v])
	for tiles in [1, 2, 3]:
		eq(GridSnap.snapBuilding(GridSnap.snapBuilding(-40, tiles), tiles), GridSnap.snapBuilding(-40, tiles))


func test_the_drag_helpers_produce_coordinates_the_sim_will_not_move() -> void:
	for tiles in [1, 2, 3]:
		for i in 40:
			var v := 1000 + i * 7.3
			var bp: Dictionary = WallLine.blockPoints(v, v, v, v, TILE, tiles)[0]
			eq(GridSnap.snapBuilding(bp.x, tiles), bp.x)
			eq(GridSnap.snapBuilding(bp.y, tiles), bp.y)
	for i in 40:
		var v := 1000 + i * 7.3
		var pt: Dictionary = WallLine.wallLinePoints(v, v, v, v, TILE)[0]
		eq(GridSnap.snapBuilding(pt.x, 1), pt.x, "wallLinePoints drifted")
		eq(GridSnap.snapBuilding(pt.y, 1), pt.y, "wallLinePoints drifted")


# ---- wallline ----

func cellPt(p: Dictionary) -> Array:
	return [int(floor(p.x / TILE)), int(floor(p.y / TILE))]


func test_wall_fills_a_straight_run_end_to_end() -> void:
	var pts := WallLine.wallLinePoints(0, 0, 10 * TILE, 0, TILE)
	eq(pts.size(), 11)
	eq(pts.map(func(p): return cellPt(p)[0]), [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10])


func test_wall_reaches_the_exact_endpoint_with_no_gaps() -> void:
	var pts := WallLine.wallLinePoints(0, 0, 8 * TILE, 5 * TILE, TILE)
	eq(cellPt(pts[0]), [0, 0])
	eq(cellPt(pts[pts.size() - 1]), [8, 5])
	for e in [[8, 5], [3, 11], [-7, 4], [12, -2], [0, 9]]:
		var p2 := WallLine.wallLinePoints(0, 0, e[0] * TILE, e[1] * TILE, TILE)
		for i in range(1, p2.size()):
			var a := cellPt(p2[i - 1])
			var b := cellPt(p2[i])
			eq(maxi(absi(b[0] - a[0]), absi(b[1] - a[1])), 1, "jumped a cell")
		eq(cellPt(p2[p2.size() - 1]), e)


func test_wall_click_and_boundary_and_cap() -> void:
	var one := WallLine.wallLinePoints(5.5 * TILE, 5.5 * TILE, 5.5 * TILE + 3, 5.5 * TILE - 2, TILE)
	eq(one.size(), 1)
	eq(cellPt(one[0]), [5, 5])
	eq(WallLine.wallLinePoints(5 * TILE, 5 * TILE, 5 * TILE, 5 * TILE - 2, TILE).map(cellPt), [[5, 5], [5, 4]])
	check(WallLine.wallLinePoints(0, 0, 100000, 0, TILE, 64).size() <= 65)
	var from := [20, 30]
	var to := [27, 30]
	var pts := WallLine.wallLinePoints((from[0] + 0.7) * TILE, (from[1] + 0.7) * TILE, (to[0] + 0.2) * TILE, (to[1] + 0.2) * TILE, TILE)
	eq(cellPt(pts[0]), from)
	eq(cellPt(pts[pts.size() - 1]), to)


func test_block_points_spacing_order_minimum_snap_and_cap() -> void:
	var pts := WallLine.blockPoints(0, 0, TILE * 9, 0, TILE, 2)
	check(pts.size() > 1)
	for i in range(1, pts.size()):
		eq(pts[i].x - pts[i - 1].x, 3 * TILE)
	var grid := WallLine.blockPoints(0, 0, TILE * 6, TILE * 6, TILE, 2)
	eq(grid.size(), 9)
	for i in range(1, grid.size()):
		var a: Dictionary = grid[i - 1]
		var b: Dictionary = grid[i]
		check(b.y > a.y or (b.y == a.y and b.x > a.x), "reading order")
	eq(WallLine.blockPoints(100, 100, 101, 101, TILE, 2).size(), 1)
	eq(fmod(WallLine.blockPoints(50, 50, 50, 50, TILE, 2)[0].x, TILE), 0)
	eq(absf(fmod(WallLine.blockPoints(50, 50, 50, 50, TILE, 3)[0].x, TILE)), TILE / 2.0)
	eq(WallLine.blockPoints(0, 0, TILE * 400, TILE * 400, TILE, 2, 64).size(), 64)
