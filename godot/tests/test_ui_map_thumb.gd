extends TestCase
# ui/map_thumb.ts: a map at a glance — terrain swatches sampled on a stride,
# a dot per spawn in its seat's colour, resources only when asked.

const SoftCanvas := preload("res://tests/soft_canvas.gd")
const Terrain = TerrainKinds.Terrain


func px(c, x: int, y: int) -> Color:
	return c.img.get_pixel(x, y)


func close(a: Color, b: Color, msg: String) -> void:
	check(absf(a.r - b.r) < 0.02 and absf(a.g - b.g) < 0.02 and absf(a.b - b.b) < 0.02, "%s: %s vs %s" % [msg, a.to_html(false), b.to_html(false)])


func test_paints_terrain_spawns_and_resources() -> void:
	var m := CustomMaps.newCustomMap("Test", 64)
	var cols := int(m.cols)
	var rows := int(m.rows)
	# A lake across the right half.
	for y in rows:
		for x in range(cols / 2, cols):
			m.terrain[y * cols + x] = Terrain.Water
	m.spawns = [{"x": 8 * C.TILE, "y": 8 * C.TILE}, {"x": 56 * C.TILE, "y": 56 * C.TILE}]
	m.resources = [{"type": "gold_mine", "x": 20 * C.TILE, "y": 40 * C.TILE}]
	var c := SoftCanvas.new(80, 80)
	MapThumb.drawMapThumbnail(c, 8, 8, 64, m, {"spawns": true})
	close(px(c, 20, 40), Color(MapThumb.MAP_SWATCH[Terrain.Grass]), "grass on the left")
	close(px(c, 60, 40), Color(MapThumb.MAP_SWATCH[Terrain.Water]), "water on the right")
	close(px(c, 16, 16), Color(C.PAL.teams[0].main), "seat 1's dot")
	close(px(c, 64, 64), Color(C.PAL.teams[1].main), "seat 2's dot")
	close(px(c, 28, 48), Color(MapThumb.MAP_SWATCH[Terrain.Grass]), "no resources unless asked")
	var c2 := SoftCanvas.new(80, 80)
	MapThumb.drawMapThumbnail(c2, 8, 8, 64, m, {"resources": true})
	close(px(c2, 28, 48), Color("#e0b83a"), "a gold mine when asked")
	close(px(c2, 16, 16), Color(MapThumb.MAP_SWATCH[Terrain.Grass]), "no spawn dots unless asked")


func test_samples_a_big_map_on_a_stride() -> void:
	# 200 cells into 50 pixels: every fourth cell, still filling the square.
	var m := CustomMaps.newCustomMap("Big", 200)
	var c := SoftCanvas.new(60, 60)
	MapThumb.drawMapThumbnail(c, 5, 5, 50, m)
	close(px(c, 53, 53), Color(MapThumb.MAP_SWATCH[Terrain.Grass]), "the far corner is filled")
	check(px(c, 57, 57).a == 0.0, "and nothing past it")
