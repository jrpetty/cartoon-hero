extends TestCase
# Port of game/src/maps/maps.test.ts: map generation and geography.

const Terrain = TerrainKinds.Terrain


# Flood-fill over cells that aren't water/cliff (blockedCells). Resources (trees
# etc.) are removable and units path around them, so the real "is this map
# winnable" guarantee is that every base connects over open ground + fords.
func allStartsConnected(presetId: String, seed: int, players: int = 2) -> bool:
	var map := MapGen.generateMap(presetId, seed, players)
	var cols: int = map.cols
	var rows: int = map.rows
	var blocked := PackedByteArray()
	blocked.resize(cols * rows)
	for c in map.blockedCells:
		blocked[c[1] * cols + c[0]] = 1
	var TILE := float(C.TILE)
	var s0x := floori(map.starts[0].x / TILE)
	var s0y := floori(map.starts[0].y / TILE)
	var seen := PackedByteArray()
	seen.resize(cols * rows)
	var stack: Array = [[s0x, s0y]]
	seen[s0y * cols + s0x] = 1
	while stack.size():
		var cur: Array = stack.pop_back()
		for d in [[1, 0], [-1, 0], [0, 1], [0, -1]]:
			var nx: int = cur[0] + d[0]
			var ny: int = cur[1] + d[1]
			if nx < 0 or ny < 0 or nx >= cols or ny >= rows:
				continue
			var i := ny * cols + nx
			if seen[i] or blocked[i]:
				continue
			seen[i] = 1
			stack.append([nx, ny])
	for s in map.starts:
		if seen[floori(s.y / TILE) * cols + floori(s.x / TILE)] != 1:
			return false
	return true


func _count(map: Dictionary, t: int) -> int:
	var n := 0
	for x in map.terrain:
		if x == t:
			n += 1
	return n


# ---- Map generation ----

func test_every_preset_generates_starts_resources_and_terrain() -> void:
	for p in C.PRESETS:
		var map := MapGen.generateMap(p.id, 1234, 2)
		eq(map.name, p.name, "name")
		eq(map.starts.size(), 2, "starts")
		check(map.resources.size() > 20, "%s has resources" % p.id)
		eq(map.terrain.size(), map.cols * map.rows, "terrain size")


func test_random_resolves_to_a_real_preset_deterministically() -> void:
	var a := MapGen.generateMap("random", 4242, 2)
	var b := MapGen.generateMap("random", 4242, 2)
	eq(a.name, b.name, "same seed → same roll")
	check(C.PRESETS.any(func(p): return p.name == a.name), "a real preset")


func test_keeps_every_base_reachable_on_all_presets() -> void:
	for p in C.PRESETS:
		for seed in [1, 7, 42, 99, 2026]:
			check(allStartsConnected(p.id, seed, 2), "%s seed %d: a base is walled off" % [p.id, seed])
	# 4-player islands too — every corner must still connect.
	for seed in [3, 21, 808]:
		check(allStartsConnected("islands", seed, 4), "islands 4p seed %d" % seed)


func test_places_up_to_8_players_evenly_each_with_their_own_resources() -> void:
	for N in [2, 3, 5, 6, 8]:
		var map := MapGen.generateMap("highlands", 123 + N, N)
		eq(map.starts.size(), N, "start count")
		# Even spacing: every start sits ~the same distance from the centre.
		var midx: float = map.worldW / 2.0
		var midy: float = map.worldH / 2.0
		var rMin := INF
		var rMax := -INF
		for s in map.starts:
			var r := JS.hypot(s.x - midx, s.y - midy)
			rMin = minf(rMin, r)
			rMax = maxf(rMax, r)
		check(rMax - rMin < map.worldW * 0.06, "%d players: all on one ring" % N)
		# Every realm has its own wood, food and gold within reach (fair start).
		var TILES := 14 * 32
		for s in map.starts:
			for type in ["tree", "berries", "gold_mine"]:
				var near: bool = map.resources.any(func(r): return r.type == type and JS.hypot(r.x - s.x, r.y - s.y) < TILES)
				check(near, "%d players: a start has no %s nearby" % [N, type])


func test_seats_teammates_next_to_each_other_in_an_even_teams_game() -> void:
	# 4v4: alliance id = team % 2.
	var N := 8
	var alliances: Array = []
	for t in N:
		alliances.append(t % 2)
	var map := MapGen.generateMap("open_plains", 909, N, false, alliances)
	for t in N:
		# A teammate should sit at the nearest-base distance (allies hold one arc;
		# boundary realms tie an ally and an enemy, so "among the nearest" is the
		# right property rather than "strictly the single nearest").
		var nearest := INF
		var nearestAlly := INF
		for o in N:
			if o == t:
				continue
			var d := JS.hypot(map.starts[t].x - map.starts[o].x, map.starts[t].y - map.starts[o].y)
			nearest = minf(nearest, d)
			if alliances[o] == alliances[t]:
				nearestAlly = minf(nearestAlly, d)
		check(nearestAlly < nearest + map.worldW * 0.02, "team %d sits away from its allies" % t)


func test_keeps_8_bases_reachable_even_on_water_maps() -> void:
	for id in ["islands", "riverlands", "highlands"]:
		for seed in [11, 222]:
			check(allStartsConnected(id, seed, 8), "%s 8p seed %d" % [id, seed])


func test_islands_actually_carries_water() -> void:
	var map := MapGen.generateMap("islands", 55, 2)
	check(_count(map, Terrain.Water) > 50, "it's a watery map")
	check(_count(map, Terrain.Sand) > 0, "...with carved land bridges")


# ---- Map geography ----

func test_gives_a_map_hard_barriers_high_ground_and_walkable_woods() -> void:
	var map := MapGen.generateMap("highlands", 4242, 4)
	# Mountains and lakes are the hard barriers; hills and forest are the
	# ground you can cross but would rather think about first.
	check(_count(map, Terrain.Rock) > 20, "no mountains")
	check(_count(map, Terrain.Hill) > 20, "no high ground")
	check(_count(map, Terrain.Forest) > 40, "no walkable woodland")
	# Blocked cells and the terrain map must agree about what is a wall.
	var blocked := {}
	for c in map.blockedCells:
		blocked[c[1] * map.cols + c[0]] = true
	var bad := 0
	for i in map.terrain.size():
		var t: int = map.terrain[i]
		if (t == Terrain.Rock or t == Terrain.Water) and not blocked.has(i):
			bad += 1
		if blocked.has(i) and not (t == Terrain.Rock or t == Terrain.Water):
			bad += 1
	eq(bad, 0, "cells where terrain and blockedCells disagree")


func _hillDensity(presetId: String, seed: int, players: int) -> float:
	var map := MapGen.generateMap(presetId, seed, players)
	return float(_count(map, Terrain.Hill)) / map.terrain.size()


func test_puts_enough_high_ground_on_a_map_for_it_to_be_worth_fighting_over() -> void:
	# A proportion, not a count. The count check above passed happily while
	# Highlands — the preset that asks for the most hills — was 4% high ground.
	check(_hillDensity("highlands", 4242, 4) > 0.07, "Highlands is meant to be hill country")
	check(_hillDensity("riverlands", 4242, 4) > 0.03, "riverlands hills")
	# Open Plains is deliberately bare — see the preset for why.
	eq(_hillDensity("open_plains", 4242, 4), 0.0, "Open Plains must stay flat")


func test_scales_high_ground_with_the_map_rather_than_to_a_flat_count() -> void:
	var small := MapGen.generateMap("highlands", 7, 2)
	var big := MapGen.generateMap("highlands", 7, 8)
	check(big.terrain.size() > small.terrain.size(), "the 8-player map should be larger")
	var ds: float = float(_count(small, Terrain.Hill)) / small.terrain.size()
	var db: float = float(_count(big, Terrain.Hill)) / big.terrain.size()
	check(db > ds * 0.6, "high ground thinned out as the map grew")


func test_keeps_every_start_on_clear_buildable_ground() -> void:
	var BUILDABLE := TerrainKinds.TERRAIN_BUILDABLE
	for preset in ["highlands", "gauntlet", "riverlands"]:
		var map := MapGen.generateMap(preset, 77, 4)
		for s in map.starts:
			var cx := floori(s.x / C.TILE)
			var cy := floori(s.y / C.TILE)
			for dy in range(-3, 4):
				for dx in range(-3, 4):
					var t: int = map.terrain[(cy + dy) * map.cols + (cx + dx)]
					if BUILDABLE[t] != 1:
						failures.append("%s: unbuildable ground by a start" % preset)
						return


func test_never_walls_a_start_off_from_the_middle_of_the_map() -> void:
	# Ridges and lakes are meant to shape an approach, not seal a player in.
	for preset in ["highlands", "gauntlet", "islands", "riverlands"]:
		for seed in [1, 2, 3]:
			var map := MapGen.generateMap(preset, seed, 4)
			var grid := CustomMaps.NavRegions.new(map.cols, map.rows)
			for c in map.blockedCells:
				grid.setBlocked(c[0], c[1], true)
			var midc := floori(map.cols / 2.0)
			var midr := floori(map.rows / 2.0)
			for s in map.starts:
				var sx := floori(s.x / C.TILE)
				var sy := floori(s.y / C.TILE)
				check(not grid.provablyUnreachable(sx, sy, midc, midr), "%s seed %d: a start is cut off from the centre" % [preset, seed])


func test_keeps_the_auto_battlers_arena_flat() -> void:
	# Warband Tactics borrows a generated map as a substrate and has its own
	# mirrored terrain on top; the geography passes share an RNG stream with the
	# resource scatter, so growing hills here would move every tree in the arena.
	var map := MapGen.generateMap("open_plains", 1234, 2)
	var ok := [Terrain.Grass, Terrain.GrassDark, Terrain.Dirt, Terrain.Water, Terrain.Sand]
	for t in map.terrain:
		if not ok.has(t):
			failures.append("open_plains grew terrain %d" % t)
			return


# ---- The MapData contract ----

func test_map_data_has_the_ts_shape() -> void:
	var map := MapGen.generateMap("riverlands", 5, 3)
	eq(map.keys(), ["name", "worldW", "worldH", "cols", "rows", "terrain", "blockedCells", "resources", "starts", "startResources", "seed", "biome", "nomad"], "keys in TS order")
	check(map.terrain is PackedByteArray, "terrain is a PackedByteArray")
	check(typeof(map.cols) == TYPE_INT and typeof(map.rows) == TYPE_INT, "cols/rows are ints")
	for c in map.blockedCells:
		check(typeof(c[0]) == TYPE_INT and typeof(c[1]) == TYPE_INT, "blocked cells are int pairs")
		break
	eq(map.resources[0].keys(), ["type", "x", "y", "amount"], "ResourceSpawn keys")
	eq(map.starts[0].keys(), ["x", "y"], "start keys")
	eq(map.startResources.keys(), ["food", "wood", "gold"], "startResources keys")
	eq(MapGen.PRESETS.size(), C.PRESETS.size(), "MapGen.PRESETS reads C.PRESETS")
	# Nomad maps scatter the starts.
	var nm := MapGen.generateMap("open_plains", 99, 4, true)
	eq(nm.nomad, true, "nomad flag")
	eq(nm.starts.size(), 4, "nomad starts")
