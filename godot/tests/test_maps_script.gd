extends TestCase
# Port of game/src/maps/script.test.ts (all but the test that runs a full AI
# match on a scripted map, which needs the World).

const Terrain = TerrainKinds.Terrain
const TILE := 32.0


func ok(src: String, seed: int = 1) -> Dictionary:
	var r := MapScript.runMapScript(src, seed)
	eq(r.errors.map(func(e): return "line %d: %s" % [e.line, e.text]), [], "errors")
	check(r.map != null, "made a map")
	return r.map if r.map != null else CustomMaps.newCustomMap("missing", 40)


func countTerrain(m: Dictionary, t: int) -> int:
	var n := 0
	for x in m.terrain:
		if x == t:
			n += 1
	return n


func _hasWarning(r: Dictionary, text: String) -> bool:
	return r.warnings.any(func(w): return String(w.text).contains(text))


# ---- The script format ----

func test_makes_a_map_from_nothing_at_all() -> void:
	var m := ok("")
	eq(m.cols, 128, "cols")
	eq(m.terrain.size(), 128 * 128, "terrain")


func test_ignores_comments_and_blank_lines() -> void:
	var m := ok("\n      # a comment\n      size 64    # and a trailing one\n\n    ")
	eq(m.cols, 64, "cols")


func test_takes_named_arguments_in_any_order() -> void:
	var a := ok("base grass\nblob rock count 6 radius 3-5", 7)
	var b := ok("base grass\nblob rock radius 3-5 count 6", 7)
	check(a.terrain == b.terrain, "same terrain")


func test_is_deterministic() -> void:
	var a := ok(C.EXAMPLE_SCRIPT, 99)
	var b := ok(C.EXAMPLE_SCRIPT, 99)
	check(a.terrain == b.terrain, "terrain")
	eq(b.resources, a.resources, "resources")
	eq(b.spawns, a.spawns, "spawns")


func test_is_a_family_not_a_map() -> void:
	var a := ok(C.EXAMPLE_SCRIPT, 1)
	var b := ok(C.EXAMPLE_SCRIPT, 2)
	check(a.terrain != b.terrain, "a different seed is a different battlefield")
	# …but the same *character*.
	for m in [a, b]:
		check(countTerrain(m, Terrain.Rock) > 50, "mountains")
		check(countTerrain(m, Terrain.Hill) > 50, "hills")


# ---- Reporting what is wrong ----

func test_names_the_line_and_says_what_it_expected() -> void:
	var r := MapScript.runMapScript("size 128\nblob nonsense count 3\n", 1)
	check(r.map == null, "no map")
	eq(r.errors.size(), 1, "one error")
	eq(r.errors[0].line, 2, "line")
	check(String(r.errors[0].text).contains("nonsense"), "names the word")


func test_rejects_an_unknown_command_and_lists_the_ones_that_exist() -> void:
	var r := MapScript.runMapScript("frobnicate everything", 1)
	check(r.map == null, "no map")
	check(String(r.errors[0].text).contains("blob"), "lists commands")


func test_refuses_a_size_outside_what_the_game_can_play() -> void:
	check(String(MapScript.runMapScript("size 5", 1).errors[0].text).contains("40"), "too small")
	check(String(MapScript.runMapScript("size 9000", 1).errors[0].text).contains("320"), "too big")


func test_refuses_an_unknown_biome_or_symmetry_by_name() -> void:
	check(String(MapScript.runMapScript("biome tropical", 1).errors[0].text).contains("temperate"), "biome")
	check(String(MapScript.runMapScript("symmetry sideways", 1).errors[0].text).contains("rot180"), "symmetry")


func test_warns_rather_than_fails_when_clusters_come_before_seats() -> void:
	var r := MapScript.runMapScript("base grass\ncluster gold count 2 near start", 1)
	check(r.map != null, "made a map")
	check(_hasWarning(r, "spawns"), "warned")


func test_caps_an_absurd_count_instead_of_hanging_and_says_it_did() -> void:
	var r := MapScript.runMapScript("scatter tree count 999999", 1)
	check(r.map != null, "made a map")
	check(_hasWarning(r, "capped"), "warned")


# ---- What the commands do ----

func test_base_fills_the_whole_map() -> void:
	eq(countTerrain(ok("size 64\nbase snow"), Terrain.Snow), 64 * 64, "snow")


func test_blob_paints_more_for_a_bigger_radius() -> void:
	var small := ok("size 96\nbase grass\nblob rock count 10 radius 2", 5)
	var big := ok("size 96\nbase grass\nblob rock count 10 radius 6", 5)
	check(countTerrain(big, Terrain.Rock) > countTerrain(small, Terrain.Rock) * 2, "bigger radius, more rock")


func test_accepts_the_words_an_author_would_actually_reach_for() -> void:
	var m := ok("size 64\nbase grass\nlake lake count 3 radius 4\nrange mountain count 3 radius 4", 3)
	check(countTerrain(m, Terrain.Water) > 0, "water")
	check(countTerrain(m, Terrain.Rock) > 0, "rock")


func test_river_lays_a_crossable_band() -> void:
	var m := ok("size 96\nbase grass\nriver shallow width 3", 4)
	check(countTerrain(m, Terrain.Shallow) > 96, "at least a cell per row of the span")


func test_ring_lays_a_band_at_a_fraction_of_the_way_out() -> void:
	var m := ok("size 96\nbase grass\nring water radius 0.5 width 4")
	check(countTerrain(m, Terrain.Water) > 100, "a band")
	eq(m.terrain[48 * 96 + 48], Terrain.Grass, "the centre is untouched")


func test_seats_players_evenly_around_a_circle() -> void:
	var m := ok("size 128\nseats 4\nspawns ring radius 0.34")
	eq(m.spawns.size(), 4, "four seats")
	var o := (128 * TILE) / 2.0
	var d0 := JS.hypot(m.spawns[0].x - o, m.spawns[0].y - o)
	for s in m.spawns:
		check(absf(JS.hypot(s.x - o, s.y - o) - d0) < TILE * 2, "equidistant")


func test_mirrors_an_explicit_spawn_through_the_symmetry() -> void:
	var m := ok("size 64\nseats 2\nsymmetry rot180\nspawn at 12,12")
	eq(m.spawns.size(), 2, "two spawns")
	var a: Dictionary = m.spawns[0]
	var b: Dictionary = m.spawns[1]
	var o := (64 * TILE) / 2.0
	check(absf((a.x - o) + (b.x - o)) < TILE * 2, "x symmetric")
	check(absf((a.y - o) + (b.y - o)) < TILE * 2, "y symmetric")


func test_gives_every_seat_its_own_clusters_at_the_distance_asked_for() -> void:
	var m := ok("\n      size 160\n      seats 2\n      base grass\n      spawns ring radius 0.3\n      cluster gold count 6 near start radius 20-40 nodes 4\n    ", 11)
	var gold: Array = m.resources.filter(func(r): return r.type == "gold_mine")
	check(gold.size() > 20, "gold placed")
	for g in gold:
		var near: bool = m.spawns.any(func(s):
			var d := JS.hypot(g.x - s.x, g.y - s.y) / TILE
			return d >= 18 and d <= 44)
		check(near, "a cluster landed at the wrong distance from every seat")


func test_never_puts_a_resource_inside_a_wall() -> void:
	var m := ok("\n      size 96\n      base water\n      blob grass count 6 radius 8\n      scatter tree count 400\n    ", 8)
	for r in m.resources:
		var cx := floori(r.x / TILE)
		var cy := floori(r.y / TILE)
		check(m.terrain[cy * m.cols + cx] != Terrain.Water, "a node was placed in a wall")


func test_never_stacks_two_nodes_on_one_cell() -> void:
	var m := ok("size 64\nbase grass\nscatter tree count 900\nscatter gold count 300", 2)
	var cells := {}
	for r in m.resources:
		cells["%d,%d" % [floori(r.x / TILE), floori(r.y / TILE)]] = true
	eq(cells.size(), m.resources.size(), "distinct cells")


func test_marks_a_map_with_no_seats_as_nomad() -> void:
	var r := MapScript.runMapScript("size 64\nbase grass", 1)
	eq(r.map.nomad, "forced", "nomad")
	check(_hasWarning(r, "nomad"), "warned")


# ---- A scripted map is a real map ----

func test_validates_clean() -> void:
	var issues := CustomMaps.validateMap(ok(C.EXAMPLE_SCRIPT, 21))
	check(not CustomMaps.hasErrors(issues), "; ".join(issues.map(func(i): return i.text)))


func test_round_trips_through_a_share_code() -> void:
	var m := ok(C.EXAMPLE_SCRIPT, 5)
	var back = CustomMaps.deserialiseMap(CustomMaps.serialiseMap(m))
	check(back != null, "decoded")
	if back != null:
		check(back.terrain == m.terrain, "terrain")
		eq(back.spawns.size(), m.spawns.size(), "spawns")


func test_keeps_every_seat_reachable_from_every_other() -> void:
	var re := RegEx.create_from_string("(?i)reach|isolat|connect")
	for seed in [1, 2, 3, 4, 5]:
		var issues := CustomMaps.validateMap(ok(C.EXAMPLE_SCRIPT, seed))
		var walled := issues.filter(func(i): return i.level == "error" and re.search(i.text) != null)
		eq(walled.map(func(i): return i.text), [], "seed %d" % seed)


# ---- The worked example ----

func test_the_example_runs_clean_since_it_is_also_the_documentation() -> void:
	var r := MapScript.runMapScript(C.EXAMPLE_SCRIPT, 1)
	eq(r.errors, [], "errors")
	eq(r.warnings.map(func(w): return w.text), [], "warnings")


func test_the_example_produces_a_playable_four_seat_map() -> void:
	var m := ok(C.EXAMPLE_SCRIPT, 1)
	eq(m.spawns.size(), 4, "four seats")
	eq(m.maxPlayers, 4, "maxPlayers")
	check(m.resources.size() > 100, "resources")


# ---- Seats get room to breathe ----

## Cells between a resource and the nearest seat, Chebyshev.
func gapToSeat(m: Dictionary) -> float:
	var mn := INF
	for r in m.resources:
		for s in m.spawns:
			var d := maxi(absi(floori(r.x / TILE) - floori(s.x / TILE)), absi(floori(r.y / TILE) - floori(s.y / TILE)))
			mn = minf(mn, d)
	return mn


func test_never_scatters_a_resource_onto_a_start() -> void:
	var m := ok(C.EXAMPLE_SCRIPT, 33)
	check(m.spawns.size() > 0, "spawns")
	check(gapToSeat(m) > 4, "a resource is sitting on a seat")


func test_holds_across_seeds() -> void:
	for seed in [1, 7, 33, 99, 404]:
		check(gapToSeat(ok(C.EXAMPLE_SCRIPT, seed)) > 4, "seed %d" % seed)


func test_clears_up_afterwards_when_the_script_seats_players_last() -> void:
	var r := MapScript.runMapScript("\n      size 96\n      seats 2\n      base grass\n      scatter tree count 900\n      spawns ring radius 0.3\n    ", 5)
	check(r.map != null, "made a map")
	check(gapToSeat(r.map) > 4, "a late spawns line left nodes on the seats")
	check(_hasWarning(r, "on top of a spawn"), "cleared nodes silently")


func test_leaves_the_authors_deliberately_close_clusters_alone() -> void:
	var m := ok("\n      size 128\n      seats 2\n      base grass\n      spawns ring radius 0.3\n      cluster berries count 1 near start radius 6-8 nodes 6\n    ", 3)
	var berries: Array = m.resources.filter(func(r): return r.type == "berries")
	check(berries.size() > 4, "the starting berries were cleared away")


func test_keeps_both_seats_about_equally_clear() -> void:
	var m := ok(C.EXAMPLE_SCRIPT, 33)
	var counts: Array = m.spawns.map(func(s): return m.resources.filter(func(r): return JS.hypot(r.x - s.x, r.y - s.y) < TILE * 6).size())
	var spread: int = counts.max() - counts.min()
	check(spread <= 6, "nodes within six cells per seat: %s" % str(counts))
