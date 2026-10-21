extends TestCase
# ui/editor.test.ts — the map editor: symmetry, the library, painting, undo,
# typing, the tools, the script panel, descriptions and generated starts.

const Terrain = TerrainKinds.Terrain
const W := 1600.0
const H := 900.0


func _init() -> void:
	Storage.use_memory_only()


# ---------------------------------------------------------------- symmetry --

func test_symmetry_paints_one_cell_when_free() -> void:
	eq(Symmetry.symmetryCells("none", 3, 4, 20, 20), [[3, 4]])


func test_symmetry_mirrors_across_each_axis_and_both_at_once() -> void:
	eq(Symmetry.symmetryCells("mirrorX", 3, 4, 20, 20), [[3, 4], [16, 4]], "mirrorX")
	eq(Symmetry.symmetryCells("mirrorY", 3, 4, 20, 20), [[3, 4], [3, 15]], "mirrorY")
	eq(Symmetry.symmetryCells("rot180", 3, 4, 20, 20), [[3, 4], [16, 15]], "rot180")
	eq(Symmetry.symmetryCells("quad", 3, 4, 20, 20), [[3, 4], [16, 4], [3, 15], [16, 15]], "quad")


func test_symmetry_puts_one_cell_in_each_slice_for_radial() -> void:
	# Inside the circle that fits the square: rotating any of these stays on
	# the map. A corner would not, which is geometry rather than a bug.
	for pair in [["radial3", 3], ["radial6", 6], ["radial8", 8]]:
		var cells := Symmetry.symmetryCells(pair[0], 20, 8, 41, 41)
		eq(cells.size(), pair[1], pair[0])
		var o := 20
		var r0 := JS.hypot(20 - o, 8 - o)
		for c in cells:
			check(absf(JS.hypot(c[0] - o, c[1] - o) - r0) < 1.2, "%s %d,%d" % [pair[0], c[0], c[1]])


func test_symmetry_drops_copies_that_rotate_off_the_map() -> void:
	var cells := Symmetry.symmetryCells("radial8", 1, 1, 41, 41)
	for c in cells:
		check(c[0] >= 0 and c[0] < 41 and c[1] >= 0 and c[1] < 41, "on the map")
	check(cells.size() < 8, "fewer copies")


func test_symmetry_never_returns_a_duplicate_or_a_cell_off_the_map() -> void:
	for sym in ["mirrorX", "mirrorY", "rot180", "quad", "radial3", "radial6", "radial8"]:
		for p in [[0, 0], [19, 19], [10, 10], [0, 19]]:
			var seen := {}
			for c in Symmetry.symmetryCells(sym, p[0], p[1], 20, 20):
				check(c[0] >= 0 and c[1] >= 0 and c[0] < 20 and c[1] < 20, "%s %d,%d" % [sym, c[0], c[1]])
				var k := "%d,%d" % [c[0], c[1]]
				check(not seen.has(k), "dup " + k)
				seen[k] = true


func test_symmetry_keeps_the_centre_cell_to_itself() -> void:
	eq(Symmetry.symmetryCells("radial8", 10, 10, 21, 21), [[10, 10]])


# ------------------------------------------------------------------ editor --

class Harness:
	var s := EditorScreen.new()
	var ctx := Ctx.new()
	var t := 0.0

	func frame(o: Dictionary = {}):
		t += 1.0 / 60
		UI.ui.begin(ctx, {
			"mx": o.get("mx", -99.0), "my": o.get("my", -99.0), "clicked": o.get("clicked", false), "rightClicked": false, "alt": false,
			"leftHeld": o.get("down", false), "rightHeld": o.get("right", false), "ctrlHeld": false, "shiftHeld": false, "wheel": o.get("wheel", 0.0),
		})
		return s.draw(W, H, t, o.get("down", false))


static func colW() -> float:
	return minf(980, W - 80)


static func x0() -> float:
	return JS.round(W / 2 - colW() / 2)


## Click "Create" — it sits at the right of the New Map panel.
static func create(h: Harness) -> void:
	h.frame({"mx": x0() + colW() - 80, "my": 122 + 82, "clicked": true})
	h.frame()


static func count(terrain: PackedByteArray, t: int) -> int:
	var n := 0
	for v in terrain:
		if v == t:
			n += 1
	return n


func test_opens_the_library_creates_a_map_and_paints_with_symmetry() -> void:
	var h := Harness.new()
	h.frame()
	eq(h.s.map, null, "starts in the library")
	create(h)
	var m = h.s.map
	check(m != null, "a map was created")
	if m == null:
		return
	eq(m.cols, 128, "default size")
	var before: PackedByteArray = (m.terrain as PackedByteArray).duplicate()
	h.s.paint = Terrain.Rock
	var cx := 214 + (W - 214 - 300) / 2 - 120
	var cy := 44 + (H - 96) / 2 - 60
	h.frame({"mx": cx, "my": cy, "down": true})
	h.frame({"mx": cx, "my": cy, "down": true})
	var after: PackedByteArray = h.s.map.terrain
	var changed := 0
	for i in after.size():
		if after[i] != before[i]:
			changed += 1
	check(changed > 0, "nothing was painted")
	# 180° symmetry means the stroke landed in two places, not one.
	var cols: int = m.cols
	var rows: int = m.rows
	for i in after.size():
		if after[i] == Terrain.Rock:
			var px := i % cols
			var py := i / cols
			var mirror := (rows - 1 - py) * cols + (cols - 1 - px)
			if after[mirror] != Terrain.Rock:
				check(false, "the mirrored half was not painted")
				break


func test_undoes_a_stroke_and_redoes_it() -> void:
	var h := Harness.new()
	create(h)
	h.s.paint = Terrain.Water
	var cx := 214 + (W - 214 - 300) / 2
	var cy := 44 + (H - 96) / 2
	h.frame({"mx": cx, "my": cy, "down": true})
	h.frame({"mx": -99, "my": -99})  # release, closing the stroke
	var painted := count(h.s.map.terrain, Terrain.Water)
	check(painted > 0, "painted")
	h.s.undoStep()
	eq(count(h.s.map.terrain, Terrain.Water), 0, "undone")
	h.s.redoStep()
	eq(count(h.s.map.terrain, Terrain.Water), painted, "redone")


func test_takes_the_spawn_tool_away_on_an_always_nomad_map() -> void:
	var h := Harness.new()
	create(h)
	h.s.tool = "spawn"
	h.s.map.nomad = "forced"
	h.frame()
	h.s.placeSpawn(10, 10)
	eq(h.s.map.spawns.size(), 0, "a spawn was placed on an always-nomad map")


func test_types_a_name_a_character_at_a_time() -> void:
	var h := Harness.new()
	create(h)
	h.s.editing = "name"
	for ch in "Ford":
		check(h.s.handleKey(ch), "typed " + ch)
	eq(h.s.map.name, "New MapFord")
	check(h.s.handleKey("Backspace"), "backspace")
	eq(h.s.map.name, "New MapFor")
	check(h.s.handleKey("Enter"), "enter")
	check(not h.s.handleKey("x"), "no longer typing")


func test_saves_on_close_so_a_map_is_never_lost_by_leaving() -> void:
	var h := Harness.new()
	create(h)
	var id: String = h.s.map.id
	h.frame({"mx": W - 118 + 39, "my": 22, "clicked": true})  # Close
	eq(h.s.map, null, "closed")
	check(CustomMaps.listCustomMaps().any(func(m): return m.id == id), "saved")


func test_renders_every_tool_without_throwing() -> void:
	var h := Harness.new()
	create(h)
	for tool in ["terrain", "path", "scatter", "region", "resource", "spawn", "erase"]:
		h.s.tool = tool
		h.frame({"mx": 100, "my": 400})
	check(h.s.map != null, "still editing")


func test_script_panel_renders_folded_and_open() -> void:
	var h := Harness.new()
	eq(h.s.showScript, false, "folded by default")
	h.frame({"mx": 10, "my": 10})
	h.s.showScript = true
	h.frame({"mx": 10, "my": 10})


func test_script_panel_renders_a_failed_script_with_the_bad_line_marked() -> void:
	var h := Harness.new()
	h.s.showScript = true
	h.s.scriptBuf = "size 128\nblob nonsense count 3"
	h.s.scriptMsgs = [{"line": 2, "text": "wants a terrain", "bad": true}]
	h.frame({"mx": 10, "my": 10})


func test_script_treats_enter_as_a_newline() -> void:
	var s := EditorScreen.new()
	s.editing = "script"
	s.scriptBuf = "size 64"
	s.handleKey("Enter")
	eq(s.scriptBuf, "size 64\n")
	eq(s.editing, "script", "Enter closed the field instead of adding a line")


func test_other_fields_treat_enter_as_done() -> void:
	var s := EditorScreen.new()
	s.editing = "name"
	s.handleKey("Enter")
	eq(s.editing, null)


func test_generate_and_edit_opens_the_scripted_map() -> void:
	var h := Harness.new()
	h.s.showScript = true
	h.frame()
	# "Generate & edit" sits at the bottom right of the script panel.
	var y := 122.0 + (92 + ceili(C.MAP_SIZES.size() / 5.0) * 30 + 42) + 16 + 94 + 94
	var shH := minf(360, H - y - 120)
	h.frame({"mx": x0() + colW() - 170 + 75, "my": y + shH - 46 + 14, "clicked": true})
	check(h.s.map != null, "the script's map is open: %s" % str(h.s.scriptMsgs))


# ------------------------------------------------------------- the tools --

static func editing() -> EditorScreen:
	var s := EditorScreen.new()
	s.map = CustomMaps.newCustomMap("T", 64, "temperate")
	s.sym = "none"
	return s


func test_path_lays_a_gap_free_run() -> void:
	var s := editing()
	var map: Dictionary = s.map
	s.paint = Terrain.Water
	s.brush = 1
	s.strokePath(4, 4, 40, 22)
	check(count(map.terrain, Terrain.Water) > 30, "painted a run")
	for i in 37:
		var t := i / 36.0
		var cx := JS.roundi(4 + 36 * t)
		var cy := JS.roundi(4 + 18 * t)
		eq(map.terrain[cy * map.cols + cx], Terrain.Water, "hole at %d,%d" % [cx, cy])


func test_path_zero_length_is_a_single_dab() -> void:
	var s := editing()
	s.paint = Terrain.Rock
	s.brush = 1
	s.strokePath(10, 10, 10, 10)
	eq(s.map.terrain[10 * s.map.cols + 10], Terrain.Rock)


func test_path_width_comes_from_the_brush() -> void:
	var wide := editing()
	wide.paint = Terrain.Dirt
	wide.brush = 4
	wide.strokePath(10, 30, 50, 30)
	var thin := editing()
	thin.paint = Terrain.Dirt
	thin.brush = 1
	thin.strokePath(10, 30, 50, 30)
	check(count(wide.map.terrain, Terrain.Dirt) > count(thin.map.terrain, Terrain.Dirt) * 2, "wider")


func test_scatter_sprinkles_many_nodes_from_one_dab() -> void:
	var s := editing()
	s.resource = "tree"
	s.brush = 8
	s.scatterDensity = 0.4
	s.scatterAt(30, 30)
	check(s.map.resources.size() > 10, "many nodes: %d" % s.map.resources.size())


func test_scatter_thickens_with_density() -> void:
	var sparse := editing()
	sparse.resource = "tree"; sparse.brush = 8; sparse.scatterDensity = 0.1
	sparse.scatterAt(30, 30)
	var dense := editing()
	dense.resource = "tree"; dense.brush = 8; dense.scatterDensity = 0.7
	dense.scatterAt(30, 30)
	check(dense.map.resources.size() > sparse.map.resources.size(), "denser")


func test_scatter_does_not_thicken_on_a_second_pass() -> void:
	var s := editing()
	s.resource = "tree"; s.brush = 6; s.scatterDensity = 0.4
	s.scatterAt(30, 30)
	var once: int = s.map.resources.size()
	s.scatterAt(30, 30)
	s.scatterAt(30, 30)
	eq(s.map.resources.size(), once)


func test_scatter_never_stacks_two_nodes_on_one_cell() -> void:
	var s := editing()
	s.resource = "tree"; s.brush = 10; s.scatterDensity = 0.8
	s.scatterAt(30, 30)
	s.resource = "gold_mine"
	s.scatterAt(30, 30)
	var cells := {}
	for r in s.map.resources:
		cells["%d,%d" % [floori(r.x / 32), floori(r.y / 32)]] = true
	eq(cells.size(), s.map.resources.size())


func test_scatter_rerolls_to_a_different_pattern() -> void:
	var a := editing()
	a.resource = "tree"; a.brush = 8; a.scatterDensity = 0.4
	a.scatterAt(30, 30)
	var b := editing()
	b.resource = "tree"; b.brush = 8; b.scatterDensity = 0.4
	b.scatterSeed = 999
	b.scatterAt(30, 30)
	var key := func(m):
		var ks: Array = m.resources.map(func(r): return "%s,%s" % [r.x, r.y])
		ks.sort()
		return "|".join(ks)
	check(key.call(a.map) != key.call(b.map), "different pattern")


## The scatter hash and what each tool does to a map, pinned against the
## browser build (godot/tests/fixtures/editor.json, from
## game/scripts/godot_parity_editor.ts).
func test_scatter_roll_matches_the_browser() -> void:
	var fx = exactFixture("editor.json")
	if fx == null:
		return
	var s := EditorScreen.new()
	for row in fx.rolls:
		s.scatterSeed = int(row.seed)
		near(s.scatterRoll(int(row.cx), int(row.cy)), float(row.v), 0.0, "seed %d at %d,%d" % [int(row.seed), int(row.cx), int(row.cy)])


static func fnv(bytes: PackedByteArray) -> int:
	var h := 2166136261
	for b in bytes:
		h ^= b & 0xff
		h = JS.u32(JS.imul(h, 16777619))
	return h


func _same_map(s: EditorScreen, want: Dictionary, label: String) -> void:
	eq(fnv(s.map.terrain), int(want.terrainHash), label + " terrain")
	var res: Array = s.map.resources.map(func(r): return [r.type, r.x, r.y, r.amount])
	eq(res.size(), want.resources.size(), label + " resource count")
	if res.size() == want.resources.size():
		for i in res.size():
			for k in 4:
				if not (res[i][k] == want.resources[i][k] or (typeof(res[i][k]) != TYPE_STRING and float(res[i][k]) == float(want.resources[i][k]))):
					check(false, "%s resource %d: %s vs %s" % [label, i, str(res[i]), str(want.resources[i])])
					return
	eq(s.map.spawns.map(func(p): return [p.x, p.y]), want.spawns, label + " spawns")


func test_tools_match_the_browser() -> void:
	var fx = exactFixture("editor.json")
	if fx == null:
		return
	for sym in ["none", "mirrorX", "mirrorY", "rot180", "quad", "radial3", "radial6", "radial8"]:
		var a := editing()
		a.sym = sym
		a.paint = Terrain.Rock; a.brush = 4; a.paintAt(20, 13)
		a.paint = Terrain.Water; a.brush = 2; a.strokePath(3, 9, 41, 30)
		a.resource = "tree"; a.brush = 7; a.scatterDensity = 0.35; a.scatterSeed = 12345; a.scatterAt(18, 40)
		a.resource = "gold_mine"; a.brush = 1; a.placeResource(10, 50)
		a.placeSpawn(12, 12)
		a.brush = 2; a.eraseAt(17, 40)
		_same_map(a, fx.cases[sym], sym)
	var r := editing()
	r.paint = Terrain.Rock; r.brush = 1
	r.strokePath(5, 5, 9, 5); r.strokePath(5, 5, 5, 8)
	r.resource = "berries"; r.brush = 3; r.scatterDensity = 0.6; r.scatterAt(7, 6)
	r.copyRegion(9, 8, 4, 4)
	r.clipFlipX = true; r.pasteRegion(40, 30)
	r.clipFlipY = true; r.pasteRegion(60, 60)
	_same_map(r, fx.cases.region, "region")


func test_region_lifts_and_stamps() -> void:
	var s := editing()
	var map: Dictionary = s.map
	s.paint = Terrain.Rock
	s.brush = 1
	for cy in range(5, 10):
		for cx in range(5, 10):
			s.strokePath(cx, cy, cx, cy)
	s.resource = "gold_mine"
	s.brush = 2; s.scatterDensity = 0.8
	s.scatterAt(7, 7)
	var nodesBefore: int = s.map.resources.size()
	s.copyRegion(5, 5, 9, 9)
	check(s.clip != null, "lifted")
	eq(s.clip.w, 5, "w")
	eq(s.clip.h, 5, "h")
	s.pasteRegion(40, 40)
	for cy in range(40, 45):
		for cx in range(40, 45):
			eq(s.map.terrain[cy * map.cols + cx], Terrain.Rock, "%d,%d" % [cx, cy])
	check(s.map.resources.size() > nodesBefore, "the stamp brought nothing with it")


func test_region_mirrors_a_stamp() -> void:
	var s := editing()
	s.paint = Terrain.Rock
	s.brush = 1
	s.strokePath(5, 5, 9, 5)
	s.strokePath(5, 5, 5, 7)
	s.copyRegion(5, 5, 9, 7)
	s.clipFlipX = true
	s.pasteRegion(40, 40)
	eq(s.map.terrain[42 * s.map.cols + 44], Terrain.Rock, "upright moved right")
	check(s.map.terrain[42 * s.map.cols + 40] != Terrain.Rock, "not on the left")


func test_region_clears_what_was_already_there() -> void:
	var s := editing()
	s.resource = "tree"; s.brush = 4; s.scatterDensity = 0.8
	s.scatterAt(42, 42)
	check(s.map.resources.size() > 0, "had nodes")
	s.copyRegion(2, 2, 8, 8)
	s.pasteRegion(39, 39)
	var inside: Array = s.map.resources.filter(func(r):
		var cx := floori(r.x / 32)
		var cy := floori(r.y / 32)
		return cx >= 39 and cx <= 45 and cy >= 39 and cy <= 45)
	eq(inside.size(), 0, "old nodes survived under the stamp")


func test_region_clips_at_the_map_edge() -> void:
	var s := editing()
	s.paint = Terrain.Rock
	s.brush = 1
	for cx in range(5, 10):
		s.strokePath(cx, 5, cx, 5)
	s.copyRegion(5, 5, 9, 5)
	s.pasteRegion(s.map.cols - 2, s.map.rows - 2)
	check(s.map.terrain[(s.map.rows - 2) * s.map.cols + 0] != Terrain.Rock, "nothing wrapped")


# --------------------------------------------------------- descriptions --

func test_description_survives_a_share_code() -> void:
	var m := CustomMaps.newCustomMap("Described", 48, "temperate")
	m.desc = "A narrow pass with one gold in the middle."
	var back = CustomMaps.deserialiseMap(CustomMaps.serialiseMap(m))
	eq(back.desc if back != null else null, m.desc)


func test_description_defaults_to_empty() -> void:
	eq(CustomMaps.newCustomMap("X", 40).desc, "")


# ------------------------------------------------- generated starting maps --

func test_generated_arrives_painted_stocked_and_seated() -> void:
	var m := CustomMaps.customFromGenerated("highlands", 42, 4)
	check(m.cols > 0, "cols")
	eq(m.spawns.size(), 4, "no seats came with it")
	check(m.resources.size() > 10, "no resources came with it")
	var distinct := {}
	for t in m.terrain:
		distinct[t] = true
	check(distinct.size() > 2, "the ground is all one thing")


func test_generated_same_seed_same_map() -> void:
	var a := CustomMaps.customFromGenerated("highlands", 7, 2)
	var b := CustomMaps.customFromGenerated("highlands", 7, 2)
	eq(b.terrain, a.terrain, "terrain")
	eq(b.spawns, a.spawns, "spawns")


func test_generated_validates_clean() -> void:
	var m := CustomMaps.customFromGenerated("highlands", 5, 2)
	var issues := CustomMaps.validateMap(m)
	check(not CustomMaps.hasErrors(issues), "; ".join(issues.map(func(i): return i.text)))


func test_generated_round_trips_through_a_share_code() -> void:
	var m := CustomMaps.customFromGenerated("riverlands", 9, 2)
	var back = CustomMaps.deserialiseMap(CustomMaps.serialiseMap(m))
	check(back != null, "decoded")
	if back != null:
		eq(back.terrain, m.terrain, "terrain")
		eq(back.spawns.size(), m.spawns.size(), "spawns")


# ------------------------------------------------------------- the library --

func test_publish_from_the_editor_puts_the_map_in_the_pool() -> void:
	var h := Harness.new()
	h.s.author = "Aldric"
	h.s.open(CustomMaps.customFromGenerated("highlands", 5, 2))
	h.frame()
	check(not CustomMaps.hasErrors(h.s.issues), "clean")
	h.frame({"mx": W - 426 + 59, "my": 22, "clicked": true})  # Publish
	check(CustomMaps.isPublished(h.s.map), "published")
	eq(h.s.map.get("author"), "Aldric", "credited")
	var found = CustomMaps.findCustomMap(h.s.map.id)
	check(found != null and CustomMaps.isPublished(found), "in the library, published")


func test_test_map_is_refused_while_there_are_errors() -> void:
	var h := Harness.new()
	create(h)
	h.s.map.spawns = []
	h.s.map.minPlayers = 2
	h.s.dirty = true
	h.frame()
	var errs := CustomMaps.hasErrors(h.s.issues)
	var a = h.frame({"mx": W - 216 + 46, "my": 22, "clicked": true})
	if errs:
		eq(a, null, "test refused")
	else:
		check(a != null and a.kind == "test", "test offered")


func test_handle_paste_types_a_whole_code() -> void:
	var s := EditorScreen.new()
	s.editing = "import"
	var code := CustomMaps.serialiseMap(CustomMaps.newCustomMap("Pasted", 40))
	s.handlePaste(code)
	eq(s.importBuf, code)


## A fixture read with the browser's numbers (Godot's JSON reader is an ulp
## out on about one double in ten; see test_net_wire_json).
func exactFixture(name: String):
	var f := FileAccess.open("res://tests/fixtures/" + name, FileAccess.READ)
	if f == null:
		failures.append("missing fixture " + name)
		return null
	return WireJSON.parse(f.get_as_text())


## How long a frame of the editor takes on a stocked generated map (the
## canvas draws every visible cell, node and seat each frame).
func test_an_editor_frame_on_a_real_map() -> void:
	var h := Harness.new()
	h.s.open(CustomMaps.customFromGenerated("highlands", 41, 4))
	h.s.sym = "quad"
	h.frame({"mx": 700, "my": 330})
	var t0 := Time.get_ticks_usec()
	for i in 10:
		h.frame({"mx": 700, "my": 330})
	var ms := (Time.get_ticks_usec() - t0) / 10000.0
	print("      (editor frame: %.2f ms, %d resources)" % [ms, h.s.map.resources.size()])
	check(ms < 60.0, "an editor frame took %.2f ms" % ms)
