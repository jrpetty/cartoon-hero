extends TestCase
# The map tooling matches the TypeScript build value for value: the script
# language (every command and symmetry, errors and warnings), share codes
# (byte-identical strings both ways, odd payloads decoded the same), validation
# messages, nomad landing sites, the Random roll and symmetryCells.
# Fixture: tests/fixtures/maps_tools.json, written by
# game/scripts/godot_parity_maps.ts.

static var _fx = null


func _f() -> Dictionary:
	if _fx == null:
		_fx = fixture("maps_tools.json")
	return _fx if _fx != null else {}


static func fnv(bytes: PackedByteArray) -> int:
	var h := 2166136261
	for b in bytes:
		h = h ^ (b & 0xFF)
		h = JS.u32(JS.imul(h, 16777619))
	return h


## Compare a CustomMap with the fixture's deterministic view of one.
func _mapEq(tag: String, m: Dictionary, want: Dictionary) -> void:
	for k in ["name", "desc", "version", "biome", "cols", "rows", "minPlayers", "maxPlayers", "nomad"]:
		eq(m.get(k), want.get(k), "%s %s" % [tag, k])
	var terrain: PackedByteArray = m.terrain
	eq(fnv(terrain), int(want.terrainHash), tag + " terrainHash")
	if want.has("terrain"):
		var wt: Array = want.terrain
		eq(terrain.size(), wt.size(), tag + " terrain size")
		for i in mini(terrain.size(), wt.size()):
			if terrain[i] != int(wt[i]):
				failures.append("%s terrain differs first at %d (%d,%d): got %d want %d" % [tag, i, i % int(m.cols), i / int(m.cols), terrain[i], int(wt[i])])
				break
	var rs: Array = m.resources
	var wrs: Array = want.resources
	eq(rs.size(), wrs.size(), tag + " resource count")
	for i in mini(rs.size(), wrs.size()):
		var a: Dictionary = rs[i]
		var b: Array = wrs[i]
		if a.type != b[0] or float(a.x) != float(b[1]) or float(a.y) != float(b[2]) or float(a.amount) != float(b[3]):
			failures.append("%s resources[%d]: got %s want %s" % [tag, i, str(a), str(b)])
			break
	var sp: Array = m.spawns
	var wsp: Array = want.spawns
	eq(sp.size(), wsp.size(), tag + " spawn count")
	for i in mini(sp.size(), wsp.size()):
		if float(sp[i].x) != float(wsp[i].x) or float(sp[i].y) != float(wsp[i].y):
			failures.append("%s spawns[%d]: got %s want %s" % [tag, i, str(sp[i]), str(wsp[i])])
			break
	eq(m.modes, want.modes, tag + " modes")
	for k in ["food", "wood", "gold"]:
		eq(m.startResources.get(k), want.startResources.get(k), "%s startResources.%s" % [tag, k])


func _issuesEq(tag: String, got: Array, want: Array) -> void:
	eq(got.size(), want.size(), tag + " issue count (got %s)" % str(got))
	for i in mini(got.size(), want.size()):
		eq(got[i].get("line"), want[i].get("line"), "%s [%d].line" % [tag, i])
		eq(got[i].get("level"), want[i].get("level"), "%s [%d].level" % [tag, i])
		eq(got[i].text, want[i].text, "%s [%d].text" % [tag, i])


func test_scripts_match_ts() -> void:
	for s in _f().scripts:
		var t0 := Time.get_ticks_usec()
		var r := MapScript.runMapScript(s.src, int(s.seed))
		var ms := (Time.get_ticks_usec() - t0) / 1000.0
		var tag := "script %s" % s.name
		_issuesEq(tag + " errors", r.errors, s.errors)
		_issuesEq(tag + " warnings", r.warnings, s.warnings)
		if s.map == null:
			check(r.map == null, tag + " should not make a map")
			continue
		if r.map == null:
			failures.append(tag + " made no map")
			continue
		_mapEq(tag, r.map, s.map)
		if String(s.name).begins_with("example"):
			print("      runMapScript(EXAMPLE_SCRIPT, %d): %.1f ms" % [int(s.seed), ms])


func _crossing() -> Dictionary:
	return CustomMaps.customFromGenerated("open_plains", 7, 2, "Crossing")


func _odd() -> Dictionary:
	var odd := CustomMaps.newCustomMap("Ardennes — Frühling ⚔ \"quoted\" \\ back\tslash", 24, "alpine")
	odd.author = "Ragnhild"
	odd.modes = ["koth", "regicide"]
	odd.nomad = "optional"
	odd.terrain[3] = TerrainKinds.Terrain.Rock
	odd.resources.append({"type": "berries", "x": 5 * 32 + 16.0, "y": 6 * 32 + 16.0, "amount": 250.0})
	odd.spawns.append({"x": 2 * 32 + 16.0, "y": 3 * 32 + 16.0})
	return odd


func test_share_codes_are_identical_both_ways() -> void:
	var made := {
		"crossing": _crossing(),
		"scripted": MapScript.runMapScript(C.EXAMPLE_SCRIPT, 5).map,
		"odd": _odd(),
	}
	for c in _f().codes:
		var m: Dictionary = made[c.name]
		_mapEq("code %s (source)" % c.name, m, c.map)
		var code := CustomMaps.serialiseMap(m)
		eq(code.length(), String(c.code).length(), "code %s length" % c.name)
		check(code == c.code, "code %s: Godot wrote a different string than the TS" % c.name)
		# And the TS-written code decodes to the same map here.
		var back = CustomMaps.deserialiseMap(c.code)
		if back == null:
			failures.append("code %s did not decode" % c.name)
			continue
		_mapEq("code %s (decoded)" % c.name, back, c.decoded)
		eq(back.get("author"), c.decoded.author, "code %s author" % c.name)


func test_odd_payloads_decode_like_ts() -> void:
	for d in _f().decoded:
		var got = CustomMaps.deserialiseMap(d.code)
		var tag := "decode %s" % JSON.stringify(String(d.code).left(40))
		if d.map == null:
			check(got == null, tag + " should be refused")
			continue
		if got == null:
			failures.append(tag + " was refused")
			continue
		_mapEq(tag, got, d.map)
		eq(got.published, d.map.published, tag + " published")
		eq(got.get("author"), d.map.author, tag + " author")


func _playable() -> Dictionary:
	var m := CustomMaps.newCustomMap("Test Field", 48)
	var cell := func(cx: int, cy: int) -> Dictionary: return {"x": cx * 32 + 16.0, "y": cy * 32 + 16.0}
	m.maxPlayers = 2
	m.spawns = [cell.call(10, 10), cell.call(37, 37)]
	for s in [[10, 10], [37, 37]]:
		for i in 6:
			var t: Dictionary = cell.call(s[0] + 5, s[1] + i - 2)
			m.resources.append({"type": "tree", "x": t.x, "y": t.y, "amount": 160.0})
			var b: Dictionary = cell.call(s[0] - 4, s[1] + i - 2)
			m.resources.append({"type": "berries", "x": b.x, "y": b.y, "amount": 250.0})
		var g: Dictionary = cell.call(s[0] + 3, s[1] + 5)
		m.resources.append({"type": "gold_mine", "x": g.x, "y": g.y, "amount": 1000.0})
	return m


func test_validation_messages_match_ts() -> void:
	var maps := {}
	for p in C.PRESETS:
		maps["generated_" + p.id] = CustomMaps.customFromGenerated(p.id, 31, 4)
	for s in [1, 2, 3, 4, 5]:
		maps["example_%d" % s] = MapScript.runMapScript(C.EXAMPLE_SCRIPT, s).map
	var v1 := _playable()
	v1.maxPlayers = 4
	v1.modes = ["survival", "regicide"]
	v1.nomad = "forced"
	maps["v1"] = v1
	var v2 := _playable()
	v2.name = "  "
	v2.minPlayers = 3
	v2.maxPlayers = 2
	v2.spawns.append({"x": -40.0, "y": 10.0})
	maps["v2"] = v2
	var v3 := _playable()
	v3.modes = ["koth"]
	for i in 48 * 30:
		v3.terrain[i] = TerrainKinds.Terrain.Rock
	maps["v3"] = v3
	maps["blank"] = CustomMaps.newCustomMap("Blank", 64)
	for v in _f().validations:
		_issuesEq("validate " + v.name, CustomMaps.validateMap(maps[v.name]), v.issues)


func test_landing_sites_match_ts() -> void:
	var nomadMap := _playable()
	nomadMap.nomad = "forced"
	for cy in range(8, nomadMap.rows - 8):
		for cx in range(8, nomadMap.cols - 8):
			if (cx * 7 + cy * 13) % 11 == 0:
				nomadMap.terrain[cy * nomadMap.cols + cx] = TerrainKinds.Terrain.Rock
	var crossing := _crossing()
	crossing.nomad = "optional"
	var scripted: Dictionary = MapScript.runMapScript(C.EXAMPLE_SCRIPT, 5).map
	scripted.nomad = "forced"
	var got := {}
	for seed in [1, 2, 3, 4, 5]:
		got["nomad_%d" % seed] = CustomMaps.toMapData(nomadMap, seed, 4, true).starts
	for n in [1, 2, 4, 8]:
		got["nomad_n%d" % n] = CustomMaps.toMapData(nomadMap, 3, n, true).starts
	got["crossing_nomad"] = CustomMaps.toMapData(crossing, 4242, 2, true).starts
	got["short"] = CustomMaps.toMapData(_playable(), 9, 5).starts
	got["scripted_8"] = CustomMaps.toMapData(scripted, 77, 8).starts
	for t in _f().toMap:
		var a: Array = got[t.name]
		eq(a.size(), t.starts.size(), t.name + " start count")
		for i in mini(a.size(), t.starts.size()):
			eq(a[i].x, t.starts[i].x, "%s starts[%d].x" % [t.name, i])
			eq(a[i].y, t.starts[i].y, "%s starts[%d].y" % [t.name, i])


func test_random_roll_matches_ts() -> void:
	var pool := [{"id": "custom_a"}, {"id": "custom_b"}, {"id": "custom_c"}]
	var rolls: Array = _f().rolls
	for s in rolls.size():
		eq(CustomMaps.rollRandomMap(s * 7919 + 3, pool, C.PRESETS.size()), rolls[s], "roll %d" % s)


func test_symmetry_cells_match_ts() -> void:
	for c in _f().symmetry:
		var got := Symmetry.symmetryCells(c.sym, int(c.cx), int(c.cy), int(c.cols), int(c.rows))
		var want: Array = c.cells
		var same := got.size() == want.size()
		if same:
			for i in got.size():
				if got[i][0] != int(want[i][0]) or got[i][1] != int(want[i][1]):
					same = false
		check(same, "symmetryCells(%s, %d, %d, %d, %d): got %s want %s" % [c.sym, c.cx, c.cy, c.cols, c.rows, str(got), str(want)])
