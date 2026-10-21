extends TestCase
# render/detail.test.ts — close-up ground detail, drawn through the reference
# drawGroundDetail onto a CPU canvas (tests/soft_canvas.gd).

const SoftCanvas := preload("res://tests/soft_canvas.gd")
const Terrain = TerrainKinds.Terrain


func bed(terrain: int, cols: int = 12) -> Array:
	var map := MapGen.generateMap("open_plains", 3, 2)
	var m := map.duplicate()
	m.cols = cols
	m.rows = cols
	var t := PackedByteArray()
	t.resize(cols * cols)
	t.fill(terrain)
	m.terrain = t
	var px := int(cols * C.TILE)
	var c = SoftCanvas.new(px, px)
	c.fillStyle = "#4a4a4a"
	c.fillRect(0, 0, px, px)
	return [m, c, px]


## How many pixels differ from the flat bed painted underneath.
static func marks(c) -> int:
	var n := 0
	for y in c.h:
		for x in c.w:
			var p: Vector3i = c.rgb(x, y)
			if absi(p.x - 74) > 6 or absi(p.y - 74) > 6 or absi(p.z - 74) > 6:
				n += 1
	return n


func test_draws_nothing_zoomed_out() -> void:
	var b := bed(Terrain.Grass)
	TerrainArt.drawGroundDetail(b[1], b[0], 0, 0, b[2], b[2], 0.5, 0)
	eq(marks(b[1]), 0, "spent time drawing detail nobody can see")


func test_draws_zoomed_in() -> void:
	var b := bed(Terrain.Grass)
	TerrainArt.drawGroundDetail(b[1], b[0], 0, 0, b[2], b[2], 1.5, 0)
	check(marks(b[1]) > 200, "marks drawn (%d)" % marks(b[1]))


func test_bails_out_for_a_whole_map_in_view() -> void:
	var cols := 80 # 6400 cells, past the 3000 ceiling
	var map := MapGen.generateMap("open_plains", 3, 2)
	var m := map.duplicate()
	m.cols = cols
	m.rows = cols
	var t := PackedByteArray()
	t.resize(cols * cols)
	t.fill(Terrain.Grass)
	m.terrain = t
	var c = SoftCanvas.new(128, 128)
	c.fillStyle = "#4a4a4a"
	c.fillRect(0, 0, 128, 128)
	TerrainArt.drawGroundDetail(c, m, 0, 0, cols * C.TILE, cols * C.TILE, 1.5, 0)
	eq(marks(c), 0, "drew detail for thousands of cells")


func test_is_stable_frame_to_frame() -> void:
	var a := bed(Terrain.Rock, 6)
	var b := bed(Terrain.Rock, 6)
	TerrainArt.drawGroundDetail(a[1], a[0], 0, 0, a[2], a[2], 1.5, 0)
	TerrainArt.drawGroundDetail(b[1], b[0], 0, 0, b[2], b[2], 1.5, 0)
	eq(a[1].img.get_data(), b[1].img.get_data(), "the ground shimmers between frames")


func test_every_ground_type_gets_something_and_water_nothing() -> void:
	for t in [Terrain.Grass, Terrain.GrassDark, Terrain.Hill, Terrain.Forest, Terrain.Rock,
			Terrain.Dirt, Terrain.Sand, Terrain.Snow, Terrain.Marsh]:
		var b := bed(t, 6)
		TerrainArt.drawGroundDetail(b[1], b[0], 0, 0, b[2], b[2], 1.5, 0)
		check(marks(b[1]) > 50, "terrain %d got no detail" % t)
	for t in [Terrain.Water, Terrain.Shallow]:
		var b := bed(t, 6)
		TerrainArt.drawGroundDetail(b[1], b[0], 0, 0, b[2], b[2], 1.5, 0)
		eq(marks(b[1]), 0, "terrain %d should be left to its own glints" % t)


func test_the_meshes_cover_the_same_ground() -> void:
	# The prebuilt meshes and the reference drawing put their marks in the same
	# places: every blade root and disc centre lies where the reference drew.
	for t in [Terrain.Grass, Terrain.Forest, Terrain.Dirt, Terrain.Marsh]:
		var b := bed(t, 6)
		TerrainArt.drawGroundDetail(b[1], b[0], 0, 0, b[2], b[2], 2.0, 0)
		var d := TerrainArt.DetailMesh.new()
		d.build(b[0], 0, 0, 5, 5)
		var parts: TerrainArt.MeshParts = d.blades if not d.blades.idx.is_empty() else d.discs
		var miss := 0
		var n := 0
		for q in range(0, parts.verts.size(), 4):
			# The quad's centre (a blade's midpoint, a disc's centre).
			var c := (parts.verts[q] + parts.verts[q + 2]) / 2
			var x := int(c.x)
			var y := int(c.y)
			if x < 0 or y < 0 or x >= b[2] or y >= b[2]:
				continue
			n += 1
			var found := false
			for dy in range(-2, 3):
				for dx in range(-2, 3):
					var xx := clampi(x + dx, 0, b[2] - 1)
					var yy := clampi(y + dy, 0, b[2] - 1)
					var p: Vector3i = b[1].rgb(xx, yy)
					if absi(p.x - 74) > 6 or absi(p.y - 74) > 6 or absi(p.z - 74) > 6:
						found = true
			if not found:
				miss += 1
		check(n > 0, "terrain %d: marks to compare" % t)
		check(miss <= n / 20, "terrain %d: %d of %d mesh marks where the reference drew nothing" % [t, miss, n])
