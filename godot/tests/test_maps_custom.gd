extends TestCase
# Port of game/src/maps/custom.test.ts, map_pool.test.ts and the parts of
# custom_e2e.test.ts that don't need a running World: the custom map format,
# validation, conversion to MapData, the library and the map pool.

const Terrain = TerrainKinds.Terrain
const TILE := 32.0


func _cell(cx: int, cy: int) -> Dictionary:
	return {"x": cx * TILE + TILE / 2.0, "y": cy * TILE + TILE / 2.0}


func _res(type: String, at: Dictionary, amount: float) -> Dictionary:
	return {"type": type, "x": at.x, "y": at.y, "amount": amount}


## A small, legal two-player map: clear ground, wood and food by each start.
func playable() -> Dictionary:
	var m := CustomMaps.newCustomMap("Test Field", 48)
	m.maxPlayers = 2
	m.spawns = [_cell(10, 10), _cell(37, 37)]
	for s in [[10, 10], [37, 37]]:
		for i in 6:
			m.resources.append(_res("tree", _cell(s[0] + 5, s[1] + i - 2), 160))
			m.resources.append(_res("berries", _cell(s[0] - 4, s[1] + i - 2), 250))
		m.resources.append(_res("gold_mine", _cell(s[0] + 3, s[1] + 5), 1000))
	return m


func _errors(issues: Array) -> Array:
	return issues.filter(func(i): return i.level == "error")


func _has(issues: Array, level: String, text: String) -> bool:
	return issues.any(func(i): return i.level == level and String(i.text).contains(text))


func _b64(s: String) -> String:
	return Marshalls.utf8_to_base64(s)


# ---- Custom map format ----

func test_survives_a_round_trip_through_a_pasteable_string() -> void:
	var m := playable()
	m.terrain[100] = Terrain.Rock
	m.terrain[101] = Terrain.Forest
	m.terrain[102] = Terrain.Hill
	m.modes = ["conquest", "koth"]
	m.nomad = "optional"
	m.minPlayers = 2
	m.maxPlayers = 4
	var back = CustomMaps.deserialiseMap(CustomMaps.serialiseMap(m))
	check(back != null, "decoded")
	if back == null:
		return
	eq(back.name, m.name, "name")
	eq(back.cols, m.cols, "cols")
	check(back.terrain == m.terrain, "terrain survives")
	eq(back.modes, ["conquest", "koth"], "modes")
	eq(back.nomad, "optional", "nomad")
	eq(back.minPlayers, 2, "minPlayers")
	eq(back.maxPlayers, 4, "maxPlayers")
	eq(back.spawns, m.spawns, "spawns")
	eq(back.resources.size(), m.resources.size(), "resources")


func test_run_length_encodes_so_a_big_empty_map_is_still_a_short_string() -> void:
	var big := CustomMaps.newCustomMap("Empty", 200) # 40,000 cells
	var code := CustomMaps.serialiseMap(big)
	check(code.length() < 600, "code is %d chars" % code.length())
	eq(CustomMaps.deserialiseMap(code).terrain.size(), 40000, "terrain size")


func test_refuses_anything_it_doesnt_recognise_instead_of_throwing() -> void:
	for bad in ["", "hello", "BBMAP1:not-base64!!", "BBMAP1:" + _b64("{}"), _b64("{}")]:
		check(CustomMaps.deserialiseMap(bad) == null, "refused %s" % bad)


func test_clamps_a_terrain_value_that_isnt_one() -> void:
	# An out-of-range terrain id would index off the end of the lookup tables
	# the movement path reads every tick.
	var m := CustomMaps.newCustomMap("Odd", 20)
	var payload: Dictionary = JSON.parse_string(Marshalls.base64_to_utf8(CustomMaps.serialiseMap(m).split(":")[1]))
	payload.t = "99x%d" % (20 * 20)
	var forged := "BBMAP1:" + _b64(JSON.stringify(payload))
	var back = CustomMaps.deserialiseMap(forged)
	check(back != null, "decoded")
	if back != null:
		for t in back.terrain:
			if t > 10:
				failures.append("terrain %d survived" % t)
				break


func test_keeps_a_name_with_characters_outside_latin1_intact() -> void:
	var m := CustomMaps.newCustomMap("Ardennes — Frühling ⚔", 24)
	eq(CustomMaps.deserialiseMap(CustomMaps.serialiseMap(m)).name, "Ardennes — Frühling ⚔", "name")


# ---- Custom map validation ----

func test_passes_a_map_that_is_actually_playable() -> void:
	eq(_errors(CustomMaps.validateMap(playable())), [], "errors")


func test_insists_on_a_spawn_for_every_seat() -> void:
	var m := playable()
	m.maxPlayers = 4 # only two spawns placed
	check(_has(CustomMaps.validateMap(m), "error", "spawn point"), "missing-spawn error")


func test_refuses_a_spawn_with_no_room_for_a_town_centre() -> void:
	var m := playable()
	var cx := floori(m.spawns[0].x / TILE)
	var cy := floori(m.spawns[0].y / TILE)
	m.terrain[cy * m.cols + cx + 1] = Terrain.Rock
	check(_has(CustomMaps.validateMap(m), "error", "Town Centre"), "no-room error")


func test_catches_a_spawn_walled_off_behind_mountains() -> void:
	var m := playable()
	# Ring the first spawn in solid rock.
	var cx := floori(m.spawns[0].x / TILE)
	var cy := floori(m.spawns[0].y / TILE)
	for a in range(0, 360, 3):
		var r := 6
		var px := cx + JS.roundi(JSMath.cos((a * PI) / 180.0) * r)
		var py := cy + JS.roundi(JSMath.sin((a * PI) / 180.0) * r)
		if px >= 0 and py >= 0 and px < m.cols and py < m.rows:
			m.terrain[py * m.cols + px] = Terrain.Rock
	check(_has(CustomMaps.validateMap(m), "error", "walled off"), "walled-off error")


func test_notices_a_map_with_no_wood_at_all() -> void:
	var m := playable()
	m.resources = m.resources.filter(func(r): return r.type != "tree")
	check(_has(CustomMaps.validateMap(m), "error", "no wood"), "no-wood error")


func test_warns_when_one_seat_is_much_richer_than_another() -> void:
	var m := playable()
	# Strip the second start bare.
	var s1: Dictionary = m.spawns[1]
	m.resources = m.resources.filter(func(r): return JS.hypot(r.x - s1.x, r.y - s1.y) / TILE > 14)
	check(_has(CustomMaps.validateMap(m), "warning", "not equal"), "fairness warning")


func test_requires_open_edges_for_a_survival_map() -> void:
	var m := playable()
	m.modes = ["survival"]
	check(not CustomMaps.hasErrors(CustomMaps.validateMap(m)), "open map is fine")
	for cx in m.cols:
		m.terrain[cx] = Terrain.Rock
		m.terrain[(m.rows - 1) * m.cols + cx] = Terrain.Rock
	for cy in m.rows:
		m.terrain[cy * m.cols] = Terrain.Rock
		m.terrain[cy * m.cols + m.cols - 1] = Terrain.Rock
	check(_has(CustomMaps.validateMap(m), "error", "Survival"), "sealed-edge error")


func test_requires_a_reachable_centre_for_king_of_the_hill() -> void:
	var m := playable()
	m.modes = ["koth"]
	check(not CustomMaps.hasErrors(CustomMaps.validateMap(m)), "open centre is fine")
	m.terrain[floori(m.rows / 2.0) * m.cols + floori(m.cols / 2.0)] = Terrain.Water
	check(_has(CustomMaps.validateMap(m), "error", "King of the Hill"), "koth error")


func test_stops_asking_for_spawn_points_once_a_map_is_always_nomad() -> void:
	var m := playable()
	m.spawns = []
	m.maxPlayers = 6
	check(CustomMaps.hasErrors(CustomMaps.validateMap(m)), "needs spawns")
	m.nomad = "forced"
	eq(_errors(CustomMaps.validateMap(m)), [], "forced nomad needs none")


# ---- Custom map to a match ----

func test_seats_players_on_the_authored_spawns_in_order() -> void:
	var m := playable()
	var md := CustomMaps.toMapData(m, 7, 2)
	eq(md.starts, m.spawns, "starts")
	eq(md.nomad, false, "nomad")
	eq(md.custom, true, "custom")
	eq(md.keys(), ["name", "worldW", "worldH", "cols", "rows", "terrain", "blockedCells", "resources", "starts", "startResources", "seed", "biome", "custom", "modes", "nomad"], "MapData keys")


func test_derives_blocked_cells_from_the_ground_and_only_from_the_ground() -> void:
	var m := playable()
	m.terrain[500] = Terrain.Rock
	m.terrain[501] = Terrain.Water
	m.terrain[502] = Terrain.Forest # passable — must NOT be blocked
	var md := CustomMaps.toMapData(m, 1, 2)
	var blocked := {}
	for c in md.blockedCells:
		blocked[c[1] * m.cols + c[0]] = true
	check(blocked.has(500), "rock blocked")
	check(blocked.has(501), "water blocked")
	check(not blocked.has(502), "forest passable")
	for i in blocked:
		eq(TerrainKinds.TERRAIN_BLOCKS[md.terrain[i]], 1, "blocked cell is a wall")


func test_never_lets_a_match_edit_the_authors_copy_of_the_map() -> void:
	var m := playable()
	var md := CustomMaps.toMapData(m, 1, 2)
	md.terrain[0] = Terrain.Rock
	check(m.terrain[0] != Terrain.Rock, "author's copy untouched")


func test_ignores_spawn_points_in_a_nomad_game_and_is_still_replayable() -> void:
	var m := playable()
	m.nomad = "optional"
	var a := CustomMaps.toMapData(m, 4242, 2, true)
	var b := CustomMaps.toMapData(m, 4242, 2, true)
	eq(a.starts, b.starts, "same seed, same landing sites")
	check(a.starts != m.spawns, "and not the authored ones")
	eq(a.nomad, true, "nomad")
	check(CustomMaps.toMapData(m, 99, 2, true).starts != a.starts, "a different seed lands elsewhere")


func test_ignores_the_nomad_request_on_a_map_that_forbids_it() -> void:
	var m := playable()
	m.nomad = "off"
	eq(CustomMaps.toMapData(m, 1, 2, true).starts, m.spawns, "authored starts")


func test_is_always_nomad_when_the_map_says_so() -> void:
	var m := playable()
	m.nomad = "forced"
	check(CustomMaps.toMapData(m, 1, 2, false).starts != m.spawns, "random starts")
	eq(CustomMaps.toMapData(m, 1, 2, false).nomad, true, "nomad")


func test_only_ever_lands_a_nomad_start_on_ground_a_town_centre_fits_on() -> void:
	var m := playable()
	m.nomad = "forced"
	# Scatter mountains over most of the map, leaving corners open.
	for cy in range(8, m.rows - 8):
		for cx in range(8, m.cols - 8):
			if (cx * 7 + cy * 13) % 11 == 0:
				m.terrain[cy * m.cols + cx] = Terrain.Rock
	for seed in [1, 2, 3, 4, 5]:
		var md := CustomMaps.toMapData(m, seed, 4, true)
		eq(md.starts.size(), 4, "start count")
		for s in md.starts:
			var cx := floori(s.x / TILE)
			var cy := floori(s.y / TILE)
			eq(TerrainKinds.TERRAIN_BLOCKS[m.terrain[cy * m.cols + cx]], 0, "start on open ground")


func test_always_returns_as_many_starts_as_there_are_players() -> void:
	var m := playable()
	m.nomad = "forced"
	for players in [1, 2, 4, 8]:
		eq(CustomMaps.toMapData(m, 3, players, true).starts.size(), players, "%d players" % players)


# ---- Custom map library ----

func test_saves_lists_finds_and_deletes() -> void:
	Storage.use_memory_only()
	var m := playable()
	m.name = "Library Test"
	CustomMaps.saveCustomMap(m)
	check(CustomMaps.listCustomMaps().any(func(x): return x.id == m.id), "listed")
	var found = CustomMaps.findCustomMap(m.id)
	check(found != null, "found")
	if found != null:
		eq(found.name, "Library Test", "name")
		check(found.terrain == m.terrain, "terrain")
	CustomMaps.deleteCustomMap(m.id)
	check(CustomMaps.findCustomMap(m.id) == null, "deleted")


func test_overwrites_rather_than_duplicating_when_the_same_map_is_saved_twice() -> void:
	Storage.use_memory_only()
	var m := playable()
	CustomMaps.saveCustomMap(m)
	m.name = "Renamed"
	CustomMaps.saveCustomMap(m)
	var all := CustomMaps.listCustomMaps().filter(func(x): return x.id == m.id)
	eq(all.size(), 1, "one entry")
	eq(all[0].name, "Renamed", "renamed")
	CustomMaps.deleteCustomMap(m.id)


func test_skips_bad_library_entries_instead_of_throwing() -> void:
	Storage.use_memory_only()
	Storage.setItem(CustomMaps.STORE_KEY, "not json")
	eq(CustomMaps.listCustomMaps(), [], "garbage store")
	var good := playable()
	Storage.setItem(CustomMaps.STORE_KEY, CustomMaps.jsonStringify(["nobar", "id|m0.0|BBMAP1:xx", CustomMaps.entryOf(good)]))
	var got := CustomMaps.listCustomMaps()
	eq(got.size(), 1, "only the good entry")


# ---- Which matches a map allows ----

func test_takes_any_mode_when_the_author_named_none() -> void:
	var m := playable()
	m.modes = []
	check(CustomMaps.mapSupports(m, "conquest", 2), "conquest")
	check(CustomMaps.mapSupports(m, "survival", 2), "survival")


func test_honours_a_1v1_only_map() -> void:
	var m := playable()
	m.modes = ["conquest"]
	m.minPlayers = 2
	m.maxPlayers = 2
	check(CustomMaps.mapSupports(m, "conquest", 2), "1v1 conquest")
	check(not CustomMaps.mapSupports(m, "conquest", 4), "not 4 players")
	check(not CustomMaps.mapSupports(m, "koth", 2), "not koth")


# ---- Map sizes ----

func test_runs_from_a_duel_arena_to_something_enormous() -> void:
	var cols: Array = C.MAP_SIZES.map(func(s): return s.cols)
	check(cols[0] <= 40, "very, very small")
	check(cols[cols.size() - 1] >= 320, "and hugeee")
	for i in range(1, cols.size()):
		check(cols[i] > cols[i - 1], "strictly increasing")
	for s in C.MAP_SIZES:
		check(String(s.label).length() > 0, "label")
		check(String(s.desc).length() > 10, "desc")


func test_builds_validates_serialises_and_converts_at_every_size() -> void:
	for size in C.MAP_SIZES:
		var n := int(size.cols)
		var m := CustomMaps.newCustomMap("%s test" % size.label, n)
		eq(m.terrain.size(), n * n, size.label)
		# A round trip has to survive even the biggest terrain array.
		var back = CustomMaps.deserialiseMap(CustomMaps.serialiseMap(m))
		check(back != null, size.label + " decodes")
		if back != null:
			eq(back.cols, n, size.label)
			eq(back.terrain.size(), n * n, size.label)
		# And it converts to something the sim would accept.
		m.nomad = "forced"
		var md := CustomMaps.toMapData(m, 5, 2, true)
		eq(md.starts.size(), 2, size.label)
		eq(md.worldW, n * TILE, size.label)
		CustomMaps.validateMap(m)


func test_never_ties_a_player_count_to_a_maps_size() -> void:
	# A Duel map may seat eight; a Colossal one may seat two.
	var tiny := CustomMaps.newCustomMap("Duel", int(C.MAP_SIZES[0].cols))
	var huge := CustomMaps.newCustomMap("Colossal", int(C.MAP_SIZES[C.MAP_SIZES.size() - 1].cols))
	for m in [tiny, huge]:
		eq(m.minPlayers, 1, "min")
		eq(m.maxPlayers, C.MAX_SEATS, "max")
		m.minPlayers = 8
		m.maxPlayers = 8
		eq(CustomMaps.deserialiseMap(CustomMaps.serialiseMap(m)).maxPlayers, 8, "8 seats")
		m.minPlayers = 1
		m.maxPlayers = 1
		eq(CustomMaps.deserialiseMap(CustomMaps.serialiseMap(m)).maxPlayers, 1, "1 seat")


func test_opens_a_new_map_wide_open_rather_than_deciding_for_the_creator() -> void:
	var m := CustomMaps.newCustomMap("Fresh", 96)
	eq(m.modes, [], "any mode")
	eq(m.minPlayers, 1, "min")
	eq(m.maxPlayers, C.MAX_SEATS, "max")
	eq(m.nomad, "off", "nomad")


# ---- Publishing a map (map_pool.test.ts) ----

func ready(name: String = "Crossing") -> Dictionary:
	var m := CustomMaps.customFromGenerated("open_plains", 7, 2, name)
	m.maxPlayers = 2
	return m


func test_starts_as_a_draft_outside_the_pool() -> void:
	Storage.use_memory_only()
	var m := ready()
	CustomMaps.saveCustomMap(m)
	check(not CustomMaps._truthy(CustomMaps.findCustomMap(m.id).published), "draft")
	eq(CustomMaps.mapPool("conquest", 2), [], "empty pool")


func test_puts_it_in_the_pool_and_saving_again_keeps_it_there() -> void:
	Storage.use_memory_only()
	var m := ready()
	CustomMaps.saveCustomMap(m)
	eq(CustomMaps.publishCustomMap(m, "Jarl Petty"), [], "published")
	eq(CustomMaps.mapPool("conquest", 2).map(func(x): return x.id), [m.id], "in the pool")
	var again: Dictionary = CustomMaps.findCustomMap(m.id)
	again.name = "Crossing II"
	CustomMaps.saveCustomMap(again)
	check(CustomMaps._truthy(CustomMaps.findCustomMap(m.id).published), "still published")
	eq(CustomMaps.findCustomMap(m.id).author, "Jarl Petty", "author")


func test_refuses_a_map_with_errors() -> void:
	Storage.use_memory_only()
	var m := CustomMaps.newCustomMap("Empty", 64) # no spawns, no resources
	check(CustomMaps.publishCustomMap(m).size() > 0, "refused")
	eq(CustomMaps.mapPool("conquest", 2), [], "empty pool")


func test_only_offers_a_map_to_matches_it_allows() -> void:
	Storage.use_memory_only()
	var m := ready()
	CustomMaps.publishCustomMap(m)
	eq(CustomMaps.mapPool("conquest", 2).size(), 1, "offered to two")
	eq(CustomMaps.mapPool("conquest", 4).size(), 0, "a 2-seat map offered to four")


func test_can_be_taken_back_out() -> void:
	Storage.use_memory_only()
	var m := ready()
	CustomMaps.publishCustomMap(m)
	CustomMaps.unpublishCustomMap(m)
	eq(CustomMaps.mapPool("conquest", 2), [], "out of the pool")
	eq(CustomMaps.listCustomMaps().size(), 1, "still in the library")


func test_keeps_maps_saved_before_publishing_existed_in_the_pool() -> void:
	Storage.use_memory_only()
	var m := ready()
	Storage.setItem("bb_custom_maps", CustomMaps.jsonStringify(["%s|%s" % [m.id, CustomMaps.serialiseMap(m)]]))
	eq(CustomMaps.mapPool("conquest", 2).map(func(x): return x.id), [m.id], "legacy entry is published")


func test_imports_someone_elses_map_as_a_draft_with_their_name_on_it() -> void:
	var m := ready()
	m.author = "Ragnhild"
	m.published = JS.now_ms()
	var got = CustomMaps.deserialiseMap(CustomMaps.serialiseMap(m))
	check(not CustomMaps._truthy(got.published), "a draft")
	eq(got.author, "Ragnhild", "author")


# ---- The Random battlefield ----

func test_rolls_only_presets_while_the_pool_is_empty() -> void:
	for s in 50:
		eq(CustomMaps.rollRandomMap(s, [], C.PRESETS.size()), "random", "seed %d" % s)


func test_lands_on_published_maps_too_the_same_way_for_the_same_seed() -> void:
	var pool := [{"id": "custom_a"}, {"id": "custom_b"}]
	var got := {}
	for s in 400:
		var r := CustomMaps.rollRandomMap(s, pool, C.PRESETS.size())
		eq(CustomMaps.rollRandomMap(s, pool, C.PRESETS.size()), r, "repeatable")
		got[r] = true
	var keys := got.keys()
	keys.sort()
	eq(keys, ["custom_a", "custom_b", "random"], "every outcome")


# ---- A map made in the editor (custom_e2e.test.ts, the parts without a World) ----

## Author a real 1v1 map the way the editor would: symmetric, with geography.
func authored() -> Dictionary:
	var N := 96
	var m := CustomMaps.newCustomMap("The Ford", N, "temperate")
	var set_ := func(cx: int, cy: int, t: int) -> void:
		if cx < 0 or cy < 0 or cx >= N or cy >= N:
			return
		m.terrain[cy * N + cx] = t
		m.terrain[(N - 1 - cy) * N + (N - 1 - cx)] = t # 180° symmetry, as the editor does
	# A river down the middle with one ford.
	for cy in N:
		if absf(cy - N / 2.0) < 6:
			continue
		for w in range(-3, 4):
			set_.call(N / 2 + w, cy, Terrain.Water)
	# A mountain spur off each side, and a hill worth holding by the ford.
	for i in 14:
		set_.call(20 + i, 30, Terrain.Rock)
	for dy in range(-4, 5):
		for dx in range(-4, 5):
			if dx * dx + dy * dy <= 16:
				set_.call(JS.roundi(N / 2.0) + dx - 8, JS.roundi(N / 2.0) + dy, Terrain.Hill)
	# Woods around each base.
	for i in 40:
		var a := (i / 40.0) * PI * 2.0
		set_.call(20 + JS.roundi(JSMath.cos(a) * 9), 20 + JS.roundi(JSMath.sin(a) * 9), Terrain.Forest)
	m.spawns = [_cell(20, 20), _cell(N - 21, N - 21)]
	m.minPlayers = 2
	m.maxPlayers = 2
	m.modes = ["conquest"]
	for s in [[20, 20], [N - 21, N - 21]]:
		for i in 8:
			m.resources.append(_res("tree", _cell(s[0] + 7, s[1] - 4 + i), 160))
		for i in 6:
			m.resources.append(_res("berries", _cell(s[0] - 6, s[1] - 3 + i), 250))
		m.resources.append(_res("gold_mine", _cell(s[0] + 4, s[1] + 7), 1000))
		m.resources.append(_res("gold_mine", _cell(s[0] + 5, s[1] + 8), 1000))
	return m


func test_an_editor_made_map_validates_clean() -> void:
	var issues := CustomMaps.validateMap(authored())
	eq(_errors(issues), [], JSON.stringify(issues))


func test_an_editor_made_map_saves_reloads_by_id_and_survives_a_share_code() -> void:
	Storage.use_memory_only()
	var m := authored()
	CustomMaps.saveCustomMap(m)
	var back = CustomMaps.findCustomMap(m.id)
	check(back != null, "reloaded")
	if back != null:
		check(back.terrain == m.terrain, "terrain")
	var shared = CustomMaps.deserialiseMap(CustomMaps.serialiseMap(m))
	check(shared.terrain == m.terrain, "shared terrain")
	eq(shared.spawns, m.spawns, "shared spawns")


func test_custom_from_generated_keeps_the_generators_seats() -> void:
	var m := CustomMaps.customFromGenerated("riverlands", 23, 4)
	var g := MapGen.generateMap("riverlands", 23, 4)
	eq(m.name, "Riverlands 23", "default name")
	eq(m.spawns, g.starts, "spawns")
	eq(m.minPlayers, 2, "min")
	eq(m.maxPlayers, 4, "max")
	eq(m.desc, "Rolled from Riverlands, seed 23.", "desc")
	check(m.terrain == g.terrain, "terrain")
