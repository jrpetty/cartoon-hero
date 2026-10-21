extends TestCase
# render/fogblur.test.ts, render/weather.test.ts, and the pure half of
# render/nightlight.test.ts (what gives off light). The night layer's pixels
# are checked on a real renderer by tests/shots (scenario "render").


## A grid split down the middle: unseen on the left, visible on the right.
static func frontier(cols: int, rows: int) -> PackedFloat32Array:
	var a := PackedFloat32Array()
	a.resize(cols * rows)
	for y in rows:
		for x in cols:
			a[y * cols + x] = 255.0 if x < cols / 2.0 else 0.0
	return a


## How many cells along the middle row sit strictly between the two extremes.
static func rampWidth(a: PackedFloat32Array, cols: int, rows: int) -> int:
	var row := (rows / 2) * cols
	var n := 0
	for x in cols:
		var v := a[row + x]
		if v > 1 and v < 254:
			n += 1
	return n


static func scratch(n: int) -> PackedFloat32Array:
	var s := PackedFloat32Array()
	s.resize(n)
	return s


func test_blur_turns_a_hard_edge_into_a_falloff() -> void:
	var a := frontier(32, 32)
	eq(rampWidth(a, 32, 32), 0, "the fixture is not a hard edge")
	FogBlur.blurMask(a, scratch(32 * 32), 32, 32, FogBlur.FOG_BLUR_RADIUS)
	check(rampWidth(a, 32, 32) > 2, "the edge is still a step")


func test_blur_spreads_about_as_far_as_the_radius_asks() -> void:
	var widths: Array = []
	for r in [1, 2, 4]:
		var a := frontier(64, 8)
		FogBlur.blurMask(a, scratch(64 * 8), 64, 8, r)
		widths.append(rampWidth(a, 64, 8))
	check(widths[1] > widths[0], "r2 wider than r1")
	check(widths[2] > widths[1], "r4 wider than r2")


func test_blur_does_nothing_at_radius_zero() -> void:
	var a := frontier(16, 16)
	var before := a.duplicate()
	FogBlur.blurMask(a, scratch(256), 16, 16, 0)
	eq(a, before, "radius 0 changed the mask")


func test_blur_keeps_the_extremes_at_the_extremes() -> void:
	var a := frontier(64, 16)
	FogBlur.blurMask(a, scratch(64 * 16), 64, 16, FogBlur.FOG_BLUR_RADIUS)
	var row := 8 * 64
	near(a[row], 255, 1e-3, "the far dark side lightened")
	near(a[row + 63], 0, 1e-3, "the far clear side darkened")


func test_blur_never_reads_outside_the_grid() -> void:
	var a := PackedFloat32Array()
	a.resize(64)
	a.fill(255)
	a[0] = 0
	FogBlur.blurMask(a, scratch(64), 8, 8, 3)
	for i in a.size():
		check(is_finite(a[i]), "cell %d is not a number" % i)
		check(a[i] <= 255.001 and a[i] >= -0.001, "cell %d out of range" % i)


func test_blur_is_symmetric() -> void:
	var cols := 33
	var a := PackedFloat32Array()
	a.resize(cols * 3)
	var mid := cols / 2
	for y in 3:
		a[y * cols + mid] = 255
	FogBlur.blurMask(a, scratch(cols * 3), cols, 3, 3)
	for k in range(1, 6):
		near(a[cols + mid - k], a[cols + mid + k], 1e-4, "offset %d" % k)


# ---------------------------------------------------------------- weather --

func test_weather_renders_every_type() -> void:
	var w := Weather.new()
	var ctx := Ctx.new()
	for t in ["clear", "rain", "snow", "overcast"]:
		w.type = t
		for i in 4:
			w.render(ctx, 1280, 760, 0.016, 1)
		w.render(ctx, 1280, 760, 0.016, 0.4) # reduce-effects
		if t == "rain":
			eq(w.drops.size(), 96, "rain thinned to 40%")
		if t == "snow":
			eq(w.drops.size(), 68, "snow thinned to 40%")


func test_weather_picks_a_stable_type_from_the_seed() -> void:
	var a := Weather.new()
	a.configure(123, "Highlands")
	var b := Weather.new()
	b.configure(123, "Highlands")
	eq(a.type, b.type, "deterministic")
	check(["clear", "rain", "snow", "overcast"].has(a.type), "a known type")


func test_weather_themes_by_map_name() -> void:
	for seed in [1, 50, 123, 777, 999]:
		var w := Weather.new()
		w.configure(seed, "Highlands")
		check(["snow", "overcast"].has(w.type), "Highlands never rains (seed %d: %s)" % [seed, w.type])


func test_weather_reseeds_when_the_viewport_changes() -> void:
	var w := Weather.new()
	w.type = "rain"
	w.render(Ctx.new(), 1280, 760, 0.016, 1)
	w.render(Ctx.new(), 800, 600, 0.016, 1)
	eq(w.w, 800.0, "re-seeded for the new width")
	for d in w.drops:
		check(d.x <= 800 + 140 * 0.016 + 1, "drops inside the new viewport")


# ------------------------------------------------------------ night light --

static func building(type: String, over: Dictionary = {}) -> Entity:
	var e := World.makeEntity()
	e.kind = T.Kind.Building
	e.type = type
	e.team = T.Team.Player
	e.x = 100
	e.y = 100
	e.radius = 32
	e.hp = 100
	e.maxHp = 100
	e.buildState = T.BuildState.Done
	e.buildProgress = 1
	for k in over:
		e.set(k, over[k])
	return e


static func unit(over: Dictionary = {}) -> Entity:
	var e := World.makeEntity()
	e.kind = T.Kind.Unit
	e.type = "spearman"
	e.team = T.Team.Player
	e.x = 50
	e.y = 50
	e.radius = 8
	e.hp = 10
	e.maxHp = 10
	for k in over:
		e.set(k, over[k])
	return e


func test_lights_lived_in_buildings_and_a_watchfire_most() -> void:
	var house := Nightlight.lightFor(building("house"), 0, false)
	var fire := Nightlight.lightFor(building("watchfire", {"radius": 16}), 0, false)
	check(house != null, "a house is lit")
	check(fire.r > house.r, "a watchfire should throw the widest pool of light")


func test_keeps_building_sites_and_plain_walls_dark() -> void:
	check(Nightlight.lightFor(building("house", {"buildState": T.BuildState.Foundation, "buildProgress": 0.0}), 0, false) == null, "foundation")
	check(Nightlight.lightFor(building("house", {"buildState": T.BuildState.UnderConstruction, "buildProgress": 0.6}), 0, false) == null, "site")
	check(Nightlight.lightFor(building("palisade", {"radius": 16}), 0, false) == null, "wall")


func test_lights_anything_burning() -> void:
	check(Nightlight.lightFor(building("palisade", {"radius": 16, "hp": 20}), 0, false) != null, "a burning wall stayed dark")


func test_lanterns_for_your_units_only() -> void:
	check(Nightlight.lightFor(unit(), 0, true) != null, "own unit lantern")
	check(Nightlight.lightFor(unit({"team": T.Team.Enemy}), 0, false) == null, "a light on enemy units makes night easier to scout")


func test_only_cuts_through_the_dark_at_night() -> void:
	eq(DayNight.nightAmount(0.2), 0.0, "day")
	eq(DayNight.nightAmount(0.46), 0.0, "dusk")
	eq(DayNight.nightAmount(0.75), 1.0, "night")


# ---------------------------------------------------------------- terrain --

func test_terrain_relief_fields_keep_the_coastline() -> void:
	# The pinned shore field keeps every water cell centre on the water side of
	# 0.5 and every land cell on the land side, however it is blurred.
	var map := MapGen.generateMap("riverlands", 7, 2)
	var f := TerrainArt.reliefFields(map)
	var shore: PackedFloat32Array = f.shore
	var terr: PackedByteArray = map.terrain
	var bad := 0
	for i in terr.size():
		var water: bool = terr[i] == TerrainKinds.Terrain.Water
		if water != (shore[i] >= 0.5):
			bad += 1
	eq(bad, 0, "cells on the wrong side of the shoreline")


func test_terrain_cache_scale_caps_big_maps() -> void:
	eq(TerrainArt.terrainCacheScale({"worldW": 3200.0, "worldH": 3200.0}), 0.5, "normal map at half scale")
	near(TerrainArt.terrainCacheScale({"worldW": 10240.0, "worldH": 8000.0}), 3072.0 / 10240.0, 1e-12, "a huge map baked coarser")


func test_cell_hash_matches_the_ts_build() -> void:
	# Values from the TS cellHash (render/terrain.ts).
	near(TerrainArt.cellHash(0, 0, 0), 0.0, 1e-12, "origin")
	eq(TerrainArt.cellHash(12, 34, 2), 0.45360094914212823, "(12, 34, 2)")
	eq(TerrainArt.cellHash(3, 5, 7), 0.12984881177544594, "(3, 5, 7)")
	near(TerrainArt.cellHash(99, 120, 13), 0.005660457536578178, 1e-17, "(99, 120, 13)")


func test_ground_detail_meshes_mark_the_same_cells() -> void:
	# The prebuilt meshes carry one quad per blade or disc (and a triangle per
	# rock), at the cells drawGroundDetail would mark.
	var map := MapGen.generateMap("open_plains", 7, 2)
	var d := TerrainArt.DetailMesh.new()
	d.build(map, 0, 0, 11, 11)
	var blades := 0
	var discs := 0
	var tris := 0
	var terr: PackedByteArray = map.terrain
	for cy in 12:
		for cx in 12:
			match int(terr[cy * map.cols + cx]):
				TerrainKinds.Terrain.Grass, TerrainKinds.Terrain.GrassDark: blades += 4
				TerrainKinds.Terrain.Hill, TerrainKinds.Terrain.Marsh: blades += 3
				TerrainKinds.Terrain.Forest: discs += 9
				TerrainKinds.Terrain.Dirt, TerrainKinds.Terrain.Sand, TerrainKinds.Terrain.Snow: discs += 2
				TerrainKinds.Terrain.Rock: tris += 3
	eq(d.blades.idx.size(), blades * 6, "a quad per blade")
	eq(d.discs.idx.size(), discs * 6 + tris * 3, "a quad per disc, a triangle per rock")
	var tips := 0
	for uv in d.blades.uvs:
		if uv.y > 0.0:
			tips += 1
	eq(tips, blades * 2, "two tip vertices per blade carry how far up they are")
