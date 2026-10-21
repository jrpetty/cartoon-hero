## Art benchmark: 300 mixed units + 60 buildings drawn every frame, the way the
## renderer draws the world (y-sorted, each through Draw), with the units
## walking, fighting and animating. Prints the CPU time spent drawing per
## frame and the frame time, after a warm-up.
##
##   xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
##     --path godot res://tests/perf/art_bench.tscn -- [--cache] [--frames N] [--zoom Z]
##
## Under Xvfb the GPU is llvmpipe (software), so frame time includes CPU
## rasterisation; the draw time is the GDScript side alone.
extends Node2D

const UNIT_COUNT := 300
const BUILDING_COUNT := 60
const BUILDING_TYPES := [["town_center", 36], ["house", 22], ["mill", 26], ["lumber_camp", 22], ["mining_camp", 22],
	["farm", 26], ["barracks", 28], ["archery_range", 28], ["stable", 28], ["blacksmith", 28], ["market", 28],
	["palisade", 18], ["stone_wall", 20], ["gate", 22], ["watch_tower", 22], ["castle", 38], ["siege_workshop", 28], ["watchfire", 16]]
const FACTIONS := ["kingdom", "legion", "norse", "shogunate", "khanate", "ascendancy"]

var ctx := Ctx.new()
var units: Array = []
var buildings: Array = []
var team_faction := {}
var time := 0.0
var frames := 0
var total_frames := 240
var warmup := 40
var draw_us: Array = []
var frame_us: Array = []
var warm_draw_us: Array = []  ## frames where the cache had nothing new to draw
var sort_us := 0
var _misses_before := 0
var last_tick := 0
var use_cache := false
var zoom := 1.0
## Units in groups of 15 sharing type, team and facing (a battle's
## formations) instead of every unit different (the worst case).
var formation := false
var fighters := {}
var rng := RandomNumberGenerator.new()


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	for i in args.size():
		match args[i]:
			"--cache":
				use_cache = true
			"--frames":
				total_frames = int(args[i + 1])
			"--formation":
				formation = true
			"--zoom":
				zoom = float(args[i + 1])
	ArtCache.enabled = use_cache
	DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_DISABLED)
	Engine.max_fps = 0
	get_window().size = Vector2i(1600, 900)
	rng.seed = 12345
	for t in 4:
		team_faction[t] = FACTIONS[(t * 2 + 1) % FACTIONS.size()] if t > 0 else "kingdom"
	var types: Array = C.UNITS.keys()
	for i in UNIT_COUNT:
		var e := Entity.new()
		e.id = 100 + i
		e.kind = T.Kind.Unit
		var g := i / 15 if formation else i
		e.type = types[g % types.size()]
		e.radius = float(C.UNITS[e.type].radius)
		e.team = g % 4
		e.x = 60 + rng.randf() * 1480
		e.y = 140 + rng.randf() * 720
		e.facing = rng.randf_range(-PI, PI) if not formation else fposmod(g * 2.399, TAU) - PI
		e.animPhase = rng.randf() * 10
		e.attackInterval = 1.0 + rng.randf()
		e.hp = 100
		e.maxHp = 100
		match g % 3:
			0:  # walking
				e.vx = cos(e.facing) * 40
				e.vy = sin(e.facing) * 40
			1:  # fighting
				e.attackCooldown = rng.randf() * e.attackInterval
				fighters[e.id] = true
		units.append(e)
	for i in BUILDING_COUNT:
		var bt: Array = BUILDING_TYPES[i % BUILDING_TYPES.size()]
		var b := Entity.new()
		b.id = 1000 + i
		b.kind = T.Kind.Building
		b.type = bt[0]
		b.radius = bt[1]
		b.team = i % 4
		b.x = 60 + (i % 12) * 130
		b.y = 60 + (i / 12) * 170
		b.hp = 1000
		b.maxHp = 1000
		buildings.append(b)
	last_tick = Time.get_ticks_usec()


func _process(dt: float) -> void:
	var now := Time.get_ticks_usec()
	if frames > warmup:
		frame_us.append(now - last_tick)
	last_tick = now
	frames += 1
	time += 1.0 / 60.0
	for e in units:
		var moving: bool = e.vx != 0.0 or e.vy != 0.0
		e.animPhase += (1.0 / 60.0) * (3.0 if moving else 1.0)
		if fighters.has(e.id):
			e.attackCooldown -= 1.0 / 60.0
			if e.attackCooldown < 0:
				e.attackCooldown += e.attackInterval
	queue_redraw()
	if frames >= total_frames:
		_report()
		get_tree().quit()


func _draw() -> void:
	var t0 := Time.get_ticks_usec()
	ctx.begin(self, 1600, 900)
	Draw.setFactionResolver(func(t): return team_faction.get(t))
	Draw.setTeamColorResolver(null)
	Draw.setDrawDayPhase(0.2)
	ctx.save()
	ctx.translate(800, 450)
	ctx.scale(zoom, zoom)
	ctx.translate(-800, -450)
	# Painter's order (y, then id) — sorted natively as Vector3 keys, which is
	# far cheaper than a GDScript comparator; the order is total, so stability
	# doesn't matter. Timed separately: it's the renderer's work, not the art's.
	var ts := Time.get_ticks_usec()
	var keys: Array = []
	var by_ix: Array = []
	for e in buildings:
		keys.append(Vector3(e.y, e.id, by_ix.size()))
		by_ix.append(e)
	for e in units:
		keys.append(Vector3(e.y, e.id, by_ix.size()))
		by_ix.append(e)
	keys.sort()
	var all: Array = []
	for k in keys:
		all.append(by_ix[int(k.z)])
	sort_us += Time.get_ticks_usec() - ts
	t0 += Time.get_ticks_usec() - ts
	for e in all:
		if e.kind == T.Kind.Building:
			Draw.drawBuilding(ctx, e, time, 0)
		else:
			Draw.drawUnit(ctx, e, time, 0)
	ctx.restore()
	ctx.end()
	if frames > warmup:
		draw_us.append(Time.get_ticks_usec() - t0)
		var st := ArtCache.stats()
		var m: int = st.misses + st.fallbacks
		if use_cache and m == _misses_before:
			warm_draw_us.append(Time.get_ticks_usec() - t0)
		_misses_before = m


func _report() -> void:
	var d := _stats(draw_us)
	var f := _stats(frame_us)
	print("art_bench cache=%s zoom=%.2f formation=%s units=%d buildings=%d frames=%d" % [use_cache, zoom, formation, UNIT_COUNT, BUILDING_COUNT, draw_us.size()])
	print("  draw (GDScript, per frame): mean %.2f ms  median %.2f ms  p95 %.2f ms   (+ y-sort %.2f ms, not included)" % [d[0], d[1], d[2], sort_us / 1000.0 / maxi(1, frames)])
	print("  frame time (incl. llvmpipe raster): mean %.2f ms  median %.2f ms  p95 %.2f ms" % [f[0], f[1], f[2]])
	if use_cache:
		var w := _stats(warm_draw_us)
		print("  draw, warm frames only (%d frames with no cache misses): mean %.2f ms  median %.2f ms  p95 %.2f ms" % [warm_draw_us.size(), w[0], w[1], w[2]])
		print("  cache: ", ArtCache.stats())


static func _stats(a: Array) -> Array:
	if a.is_empty():
		return [0.0, 0.0, 0.0]
	var s := a.duplicate()
	s.sort()
	var sum := 0.0
	for v in s:
		sum += v
	return [sum / s.size() / 1000.0, s[s.size() / 2] / 1000.0, s[int(s.size() * 0.95)] / 1000.0]
