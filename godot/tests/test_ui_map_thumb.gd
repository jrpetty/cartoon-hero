extends TestCase
# ui/map_thumb.ts: a map at a glance — terrain swatches sampled on a stride,
# a dot per spawn in its seat's colour, resources only when asked. The terrain
# and resources are baked to an image under the drawing (thousands of
# rectangles); the spawn dots and the frame are drawn on top.

const SoftCanvas := preload("res://tests/soft_canvas.gd")
const Terrain = TerrainKinds.Terrain


func close(a: Color, b: Color, msg: String, tol: float = 0.02) -> void:
	check(absf(a.r - b.r) < tol and absf(a.g - b.g) < tol and absf(a.b - b.b) < tol, "%s: %s vs %s" % [msg, a.to_html(false), b.to_html(false)])


func lake_map() -> Dictionary:
	var m := CustomMaps.newCustomMap("Test", 64)
	var cols := int(m.cols)
	var rows := int(m.rows)
	# A lake across the right half.
	for y in rows:
		for x in range(cols / 2, cols):
			m.terrain[y * cols + x] = Terrain.Water
	m.spawns = [{"x": 8 * C.TILE, "y": 8 * C.TILE}, {"x": 56 * C.TILE, "y": 56 * C.TILE}]
	m.resources = [{"type": "gold_mine", "x": 20 * C.TILE, "y": 40 * C.TILE}]
	return m


func test_bakes_the_sampled_terrain_and_resources_only_when_asked() -> void:
	var m := lake_map()
	var img := MapThumb.bakeImage(m, 64, false)
	eq(img.get_width(), int(MapThumb.bakeExtent(64)), "one pixel per unit at scale 1")
	close(img.get_pixel(12, 32), Color(MapThumb.MAP_SWATCH[Terrain.Grass]), "grass on the left")
	close(img.get_pixel(52, 32), Color(MapThumb.MAP_SWATCH[Terrain.Water]), "water on the right")
	close(img.get_pixel(20, 40), Color(MapThumb.MAP_SWATCH[Terrain.Grass]), "no resources unless asked")
	check(img.get_pixel(65, 65).a == 0.0, "nothing past the last cell")
	var with_res := MapThumb.bakeImage(m, 64, true)
	close(with_res.get_pixel(20, 40), Color("#e0b83a"), "a gold mine when asked", 0.15)
	# Twice the device scale, twice the pixels.
	eq(MapThumb.bakeImage(m, 64, false, 2.0).get_width(), int(MapThumb.bakeExtent(64)) * 2, "baked at the device scale")


func test_a_cell_edge_between_pixels_is_blended_as_coverage() -> void:
	# 64 cells into 48 pixels: three cells to four pixels, so edges fall between
	# pixels and come out as a mix — as the canvas antialiases them.
	var m := CustomMaps.newCustomMap("Mixed", 64)
	for y in int(m.rows):
		for x in range(int(m.cols)):
			if x % 2 == 1:
				m.terrain[y * int(m.cols) + x] = Terrain.Water
	var img := MapThumb.bakeImage(m, 48, false)
	var grass := Color(MapThumb.MAP_SWATCH[Terrain.Grass])
	var water := Color(MapThumb.MAP_SWATCH[Terrain.Water])
	var mixed := 0
	for x in 40:
		var c := img.get_pixel(x, 20)
		if not c.is_equal_approx(grass) and not c.is_equal_approx(water):
			mixed += 1
	check(mixed > 5, "blended edges (%d)" % mixed)


func test_draws_the_spawn_dots_and_frame_over_the_bake() -> void:
	var m := lake_map()
	var c := SoftCanvas.new(80, 80)  # fills and strokes only: the bake is an image
	MapThumb.drawMapThumbnail(c, 8, 8, 64, m, {"spawns": true})
	close(c.img.get_pixel(16, 16), Color(C.PAL.teams[0].main), "seat 1's dot")
	close(c.img.get_pixel(64, 64), Color(C.PAL.teams[1].main), "seat 2's dot")
	var c2 := SoftCanvas.new(80, 80)
	MapThumb.drawMapThumbnail(c2, 8, 8, 64, m)
	check(c2.img.get_pixel(16, 16).a == 0.0, "no spawn dots unless asked")


func test_caches_the_bake_until_the_map_changes() -> void:
	var m := lake_map()
	var a := MapThumb._baked_texture(m, 54, true, 1.0)
	check(MapThumb._baked_texture(m, 54, true, 1.0) == a, "the same map, the same texture")
	check(MapThumb._baked_texture(m, 54, false, 1.0) != a, "resources are part of the picture")
	m.terrain[5] = Terrain.Rock
	check(MapThumb._baked_texture(m, 54, true, 1.0) != a, "an edited map is baked again")
