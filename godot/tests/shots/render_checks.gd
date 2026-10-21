## The pixel checks the TS suite makes on its canvases, made on what the GPU
## actually rendered (they need a real renderer, so they run under Xvfb, not
## in the headless suite):
##
##   xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
##     --path godot res://tests/shots/harness.tscn -- render
##
## render/relief.test.ts (the baked terrain: water where the sim says water,
## the smallest features kept, beaches, a rounded coast, nothing dry in the
## river, hills lit from the north-west, deterministic), render/fog.test.ts
## (the unexplored map hidden, your base lit) and render/nightlight.test.ts's
## night layer (lighter at a light, still night elsewhere, nothing by day).
extends RefCounted

const Terrain = TerrainKinds.Terrain
const N := 40

var h
var fails := 0
var passes := 0


func _init(harness) -> void:
	h = harness


func ok(cond: bool, msg: String) -> void:
	if cond:
		passes += 1
	else:
		fails += 1
		print("FAIL: ", msg)


func run() -> void:
	await relief()
	await fog()
	await night()
	print("render checks: %d passed, %d failed" % [passes, fails])


# ---------------------------------------------------------------- relief --

func bed(paint: Callable) -> Dictionary:
	var base := MapGen.generateMap("open_plains", 3, 2)
	var t := PackedByteArray()
	t.resize(N * N)
	t.fill(Terrain.Grass)
	paint.call(t)
	var m := base.duplicate()
	m.cols = N
	m.rows = N
	m.worldW = N * C.TILE
	m.worldH = N * C.TILE
	m.terrain = t
	m.resources = []
	m.blockedCells = []
	m.starts = []
	return m


func bake(map: Dictionary) -> Dictionary:
	var cache := TerrainArt.buildTerrainCache(map)
	await h.frames(2)
	await RenderingServer.frame_post_draw
	var img: Image = cache.layer.image()
	var t: float = C.TILE * cache.scale
	return {"img": img, "t": t, "cache": cache}


static func px(b: Dictionary, x: float, y: float) -> Color:
	return (b.img as Image).get_pixel(clampi(int(x), 0, b.img.get_width() - 1), clampi(int(y), 0, b.img.get_height() - 1))


static func centre(b: Dictionary, cx: int, cy: int) -> Color:
	return px(b, (cx + 0.5) * b.t, (cy + 0.5) * b.t)


## Every blue in the water ramp, foam included, has far more blue than red.
static func watery(c: Color) -> bool:
	return c.b8 > c.r8 + 12


func relief() -> void:
	var mixed := bed(func(t: PackedByteArray) -> void:
		for y in range(4, 12):
			for x in range(4, 12):
				t[y * N + x] = Terrain.Water
		t[8 * N + 8] = Terrain.Grass # island in the lake
		t[5 * N + 30] = Terrain.Water # lone pond
		for y in range(14, 36):
			t[y * N + 20] = Terrain.Water # one-cell channel
		for y in range(22, 32):
			for x in range(27, 35):
				t[y * N + x] = Terrain.Hill)
	var shot: Dictionary = await bake(mixed)
	var wrong: Array = []
	for cy in N:
		for cx in N:
			var isWater: bool = mixed.terrain[cy * N + cx] == Terrain.Water
			if watery(centre(shot, cx, cy)) != isWater:
				wrong.append("%d,%d" % [cx, cy])
	ok(wrong.is_empty(), "relief: the picture disagrees with the pathfinder at %s" % str(wrong.slice(0, 8)))
	ok(watery(centre(shot, 30, 5)), "relief: a lone pond dried up")
	ok(not watery(centre(shot, 8, 8)), "relief: a one-cell island was flooded")
	var closed := []
	for y in range(14, 36):
		if not watery(centre(shot, 20, y)):
			closed.append(y)
	ok(closed.is_empty(), "relief: the channel is closed at rows %s" % str(closed))
	var grass := centre(shot, 2, 36)
	var shore := centre(shot, 3, 7)
	ok(shore.r8 - grass.r8 > 30, "relief: no sand at the waterline (%d vs %d)" % [shore.r8, grass.r8])
	var inland := centre(shot, 16, 2)
	ok(absi(inland.r8 - grass.r8) < 20, "relief: sand far from any water")
	var lum := func(c: Color) -> float: return c.r8 * 0.3 + c.g8 * 0.59 + c.b8 * 0.11
	ok(lum.call(centre(shot, 27, 22)) - lum.call(centre(shot, 34, 31)) > 15, "relief: the sunlit flank is not lighter than the shaded one")
	var again: Dictionary = await bake(mixed)
	ok(again.img.get_data() == shot.img.get_data(), "relief: not deterministic")

	var stair := bed(func(t: PackedByteArray) -> void:
		for y in N:
			for x in N:
				if x + y < 24:
					t[y * N + x] = Terrain.Water)
	var s2: Dictionary = await bake(stair)
	var worst := 0
	var prev := -1
	var w: int = s2.img.get_width()
	for y in range(int(6 * s2.t), int(18 * s2.t)):
		var x := 0
		while x < w and watery(px(s2, x, y)):
			x += 1
		if prev >= 0:
			worst = maxi(worst, absi(x - prev))
		prev = x
	ok(worst < s2.t * 0.5, "relief: the coastline jumps %dpx between rows — a staircase" % worst)
	var broken: Array = []
	for y in range(int(2 * s2.t), int(20 * s2.t)):
		var first := 0
		while first < w and watery(px(s2, first, y)):
			first += 1
		var wet := 0
		for x in w:
			if watery(px(s2, x, y)):
				wet += 1
		if wet != first:
			broken.append(y)
	ok(broken.is_empty(), "relief: something dry was drawn in the water on rows %s" % str(broken.slice(0, 8)))


# ------------------------------------------------------------------- fog --

## Render one frame of the world through a Renderer into an offscreen
## viewport, and read it back.
func frameOf(world: World, cam: Camera, W: int, H: int, setup: Callable = Callable()) -> Image:
	var vp := SubViewport.new()
	vp.size = Vector2i(W, H)
	vp.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	h.add_child(vp)
	var wl := Node2D.new()
	var ol := Node2D.new()
	vp.add_child(wl)
	vp.add_child(ol)
	var r := Renderer.new()
	r.prepare(world.map)
	if setup.is_valid():
		setup.call(r)
	for i in 4:
		r.ctx.begin(wl, W, H)
		r.octx.begin(ol, W, H)
		r.snapshot(world, cam, 0, W, H)
		r.render(r.snap, cam, Particles.new(), 1.0 / 60, 1, 0, [], null, {"active": false, "x0": 0, "y0": 0, "x1": 0, "y1": 0}, [], -1, 1)
		await h.frames(1)
	await RenderingServer.frame_post_draw
	var img := vp.get_texture().get_image()
	vp.queue_free()
	return img


static func meanLum(img: Image, x0: int, y0: int, w: int, hh: int) -> float:
	var s := 0.0
	for y in range(y0, y0 + hh):
		for x in range(x0, x0 + w):
			var c := img.get_pixel(x, y)
			s += (c.r8 + c.g8 + c.b8) / 3.0
	return s / (w * hh)


func fog() -> void:
	var W := 900
	var H := 520
	var map := MapGen.generateMap("open_plains", 6, 2)
	var world := World.new(6)
	world.init(map, [{}, {}], [1.0, 1.0], [0, 1])
	# One tick so vision is computed; the rest of the map stays unexplored.
	world.tick()
	world.drainEvents()
	var cam := Camera.new()
	cam.setWorld(map.worldW, map.worldH)
	cam.setViewport(W, H)
	cam.zoom = 0.6
	cam.centerOn(map.starts[0].x, map.starts[0].y)
	var img: Image = await frameOf(world, cam, W, H)
	ok(meanLum(img, 0, 0, 24, 24) < 26, "fog: unexplored ground is not dark (%.1f)" % meanLum(img, 0, 0, 24, 24))
	ok(meanLum(img, W / 2 - 8, H / 2 - 8, 16, 16) > 40, "fog: your own base is in the dark (%.1f)" % meanLum(img, W / 2 - 8, H / 2 - 8, 16, 16))
	img.save_png(h.outDir.path_join("render_check_fog.png"))


# ----------------------------------------------------------------- night --

func nightFrame(lights: Array, night: float) -> Image:
	var W := 400
	var H := 240
	var vp := SubViewport.new()
	vp.size = Vector2i(W, H)
	vp.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	h.add_child(vp)
	var n := Node2D.new()
	vp.add_child(n)
	var ctx := LayerCtx.new()
	var layer := Nightlight.NightLayer.new()
	var ls: Array = lights.map(func(l): return Nightlight.Light.new(l[0], l[1], l[2], l[3]))
	for i in 3:
		ctx.begin(n, W, H)
		ctx.fillStyle = "#6da944"
		ctx.fillRect(0, 0, W, H)
		layer.draw(ctx, W, H, DayNight.skyTint(0.75), night, ls, 1, func(x: float, y: float) -> Vector2: return Vector2(x, y))
		await h.frames(1)
	await RenderingServer.frame_post_draw
	var img := vp.get_texture().get_image()
	vp.queue_free()
	return img


static func lumAt(img: Image, x: int, y: int) -> float:
	var c := img.get_pixel(x, y)
	return c.r8 * 0.3 + c.g8 * 0.59 + c.b8 * 0.11


func night() -> void:
	var lit: Image = await nightFrame([[100.0, 120.0, 60.0, 0.8]], 1.0)
	ok(lumAt(lit, 100, 120) - lumAt(lit, 320, 120) > 30, "night: the light made no difference (%.1f vs %.1f)" % [lumAt(lit, 100, 120), lumAt(lit, 320, 120)])
	var dark: Image = await nightFrame([], 1.0)
	ok(absf(lumAt(lit, 320, 120) - lumAt(dark, 320, 120)) < 2, "night: the dark changed away from the lights")
	ok(lumAt(dark, 200, 120) < 110, "night: the night isn't dark (%.1f)" % lumAt(dark, 200, 120))
	var dayLit: Image = await nightFrame([[100.0, 120.0, 60.0, 0.8]], 0.0)
	var dayDark: Image = await nightFrame([], 0.0)
	ok(absf(lumAt(dayLit, 100, 120) - lumAt(dayDark, 100, 120)) < 2, "night: a light cut the dark before nightfall")
	lit.save_png(h.outDir.path_join("render_check_night.png"))
