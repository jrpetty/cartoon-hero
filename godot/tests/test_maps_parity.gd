extends TestCase
# Generated maps match the TypeScript build exactly: every terrain byte, every
# blocked cell, every resource node (in order), the starts and the metadata.
# Fixtures: tests/fixtures/maps.json, written by game/scripts/godot_parity.ts.

static var _cases = null


func _cases_json() -> Array:
	if _cases == null:
		_cases = fixture("maps.json")
	return _cases if _cases != null else []


## FNV-1a over the terrain bytes, as godot_parity.ts computes `terrainHash`.
static func fnv(bytes: PackedByteArray) -> int:
	var h := 2166136261
	for b in bytes:
		h = h ^ (b & 0xFF)
		h = JS.u32(JS.imul(h, 16777619))
	return h


func _case(id: String) -> void:
	var c = null
	for x in _cases_json():
		if x.id == id:
			c = x
	if c == null:
		failures.append("no fixture case " + id)
		return
	var want: Dictionary = c.map
	var t0 := Time.get_ticks_usec()
	var got := MapGen.generateMap(id, int(c.seed), int(c.n))
	var ms := (Time.get_ticks_usec() - t0) / 1000.0
	print("      generateMap(%s, %d, %d): %.1f ms, %dx%d cells" % [id, int(c.seed), int(c.n), ms, got.cols, got.rows])
	var tag := "%s/%d/%d" % [id, int(c.seed), int(c.n)]

	# Same keys, nothing extra and nothing missing.
	var wantKeys := want.keys().filter(func(k): return k != "terrainHash")
	var gotKeys := got.keys()
	wantKeys.sort()
	gotKeys.sort()
	eq(gotKeys, wantKeys, tag + " keys")

	for k in ["name", "worldW", "worldH", "cols", "rows", "seed", "biome", "nomad"]:
		eq(got.get(k), want.get(k), "%s %s" % [tag, k])
	for k in ["food", "wood", "gold"]:
		eq(got.startResources.get(k), want.startResources.get(k), "%s startResources.%s" % [tag, k])

	# Terrain, byte for byte — report the first differing cell.
	var terrain: PackedByteArray = got.terrain
	var wt: Array = want.terrain
	eq(terrain.size(), wt.size(), tag + " terrain length")
	for i in mini(terrain.size(), wt.size()):
		if terrain[i] != int(wt[i]):
			failures.append("%s terrain differs first at cell %d (%d,%d): got %d, want %d" % [tag, i, i % int(got.cols), i / int(got.cols), terrain[i], int(wt[i])])
			break
	eq(fnv(terrain), int(want.terrainHash), tag + " terrainHash")

	# Blocked cells, in the same (Set insertion) order.
	var bc: Array = got.blockedCells
	var wbc: Array = want.blockedCells
	eq(bc.size(), wbc.size(), tag + " blockedCells count")
	for i in mini(bc.size(), wbc.size()):
		if bc[i][0] != int(wbc[i][0]) or bc[i][1] != int(wbc[i][1]):
			failures.append("%s blockedCells[%d]: got %s, want %s" % [tag, i, str(bc[i]), str(wbc[i])])
			break
		if typeof(bc[i][0]) != TYPE_INT or typeof(bc[i][1]) != TYPE_INT:
			failures.append("%s blockedCells[%d] is not an int pair" % [tag, i])
			break

	# Resources: type, x, y, amount, in order.
	var rs: Array = got.resources
	var wrs: Array = want.resources
	eq(rs.size(), wrs.size(), tag + " resource count")
	for i in mini(rs.size(), wrs.size()):
		var a: Dictionary = rs[i]
		var b: Dictionary = wrs[i]
		if a.type != b.type or float(a.x) != float(b.x) or float(a.y) != float(b.y) or float(a.amount) != float(b.amount):
			failures.append("%s resources[%d]: got %s, want %s" % [tag, i, str(a), str(b)])
			break
		if a.keys() != b.keys():
			failures.append("%s resources[%d] keys: got %s, want %s" % [tag, i, str(a.keys()), str(b.keys())])
			break

	# Starts.
	var st: Array = got.starts
	var wst: Array = want.starts
	eq(st.size(), wst.size(), tag + " start count")
	for i in mini(st.size(), wst.size()):
		eq(st[i].x, wst[i].x, "%s starts[%d].x" % [tag, i])
		eq(st[i].y, wst[i].y, "%s starts[%d].y" % [tag, i])


func test_open_plains() -> void:
	_case("open_plains")


func test_black_forest() -> void:
	_case("black_forest")


func test_riverlands() -> void:
	_case("riverlands")


func test_highlands() -> void:
	_case("highlands")


func test_islands() -> void:
	_case("islands")


func test_crossroads() -> void:
	_case("crossroads")


func test_gauntlet() -> void:
	_case("gauntlet")


func test_every_fixture_case_is_covered() -> void:
	var ids := ["open_plains", "black_forest", "riverlands", "highlands", "islands", "crossroads", "gauntlet"]
	for c in _cases_json():
		check(ids.has(c.id), "fixture case %s has no test" % c.id)
	eq(_cases_json().size(), ids.size(), "fixture case count")
