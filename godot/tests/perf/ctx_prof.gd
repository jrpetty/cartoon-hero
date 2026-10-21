## Ctx micro-benchmarks: where the time in one procedural unit goes.
##   godot --headless --path godot res://tests/perf/ctx_prof.tscn
extends Node

var ctx := Ctx.new()
var rid: RID


func _ready() -> void:
	ArtCache.enabled = false
	rid = RenderingServer.canvas_item_create()
	ctx.begin(rid, 800, 600)
	var e := Entity.new()
	e.type = "militia"
	e.radius = 9
	e.facing = 0.4
	e.animPhase = 0.3
	Draw.setFactionResolver(func(_t): return "kingdom")
	_bench("drawUnit militia (emit)", 200, func(): ctx.begin(rid, 800, 600); Draw.drawUnit(ctx, e, 0.7, 0))
	ctx.measure_only = true
	_bench("drawUnit militia (measure only)", 200, func(): Draw.drawUnit(ctx, e, 0.7, 0))
	ctx.measure_only = false
	var g := func():
		ctx.begin(rid, 800, 600)
		for i in 20:
			ctx.fillStyle = "#3a6fd8"; ctx.beginPath(); ctx.arc(50, 50, 9, 0, TAU); ctx.fill()
	_bench("20x solid circle fill r9", 100, g)
	var g2 := func():
		ctx.begin(rid, 800, 600)
		for i in 20:
			ctx.fillStyle = Draw.grad(ctx, 0, 0, 0, 10, "#ffffff", "#000000"); ctx.beginPath(); ctx.arc(50, 50, 9, 0, TAU); ctx.fill()
	_bench("20x gradient circle fill r9", 100, g2)
	var g3 := func():
		ctx.begin(rid, 800, 600)
		for i in 20:
			ctx.strokeStyle = "rgba(20,16,10,0.42)"; ctx.lineWidth = 1.6; ctx.beginPath(); ctx.arc(50, 50, 9, 0, TAU); ctx.stroke()
	_bench("20x circle outline stroke r9", 100, g3)
	var g4 := func():
		ctx.begin(rid, 800, 600)
		for i in 20:
			Draw.capsule(ctx, 0, 0, 10, 10, 2.2, "#ffffff", "#000000")
	_bench("20x gradient capsule (round caps)", 100, g4)
	var g5 := func():
		ctx.begin(rid, 800, 600)
		for i in 20:
			ctx.fillStyle = "#3a6fd8"; ctx.fillRect(10, 10, 20, 8)
	_bench("20x fillRect", 100, g5)
	var torso := func(c: Ctx):
		var r := 9.0
		c.beginPath()
		c.moveTo(-r * 0.5, r * 0.42)
		c.quadraticCurveTo(-r * 0.6, -r * 0.5, 0, -r * 0.58)
		c.quadraticCurveTo(r * 0.6, -r * 0.5, r * 0.5, r * 0.42)
		c.quadraticCurveTo(0, r * 0.6, -r * 0.5, r * 0.42)
		c.closePath()
	var g8 := func():
		ctx.begin(rid, 800, 600)
		for i in 20:
			ctx.fillStyle = Draw.grad(ctx, 0, -5, 0, 4, "#7da3ec", "#274a90"); torso.call(ctx); ctx.fill()
	_bench("20x torso gradient fill (quadratics)", 100, g8)
	var g9 := func():
		ctx.begin(rid, 800, 600)
		for i in 20:
			torso.call(ctx); ctx.strokeStyle = "rgba(20,16,10,0.42)"; ctx.lineWidth = 1.8; ctx.stroke()
	_bench("20x torso outline stroke", 100, g9)
	var g10 := func():
		ctx.begin(rid, 800, 600)
		for i in 20:
			ctx.strokeStyle = "#ffffff"; ctx.lineWidth = 1.3; ctx.lineCap = "butt"; ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(10, 3); ctx.stroke()
	_bench("20x 2-point solid butt line", 100, g10)
	var g11 := func():
		ctx.begin(rid, 800, 600)
		for i in 20:
			ctx.strokeStyle = "#ffffff"; ctx.lineWidth = 2.2; ctx.lineCap = "round"; ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(10, 3); ctx.stroke()
	_bench("20x 2-point solid round line", 100, g11)
	var g6 := func():
		for i in 20:
			Palette.shade("#3a6fd8", 0.2)
			Palette.withAlpha("#3a6fd8", 0.4)
	_bench("20x shade + withAlpha", 200, g6)
	var g7 := func():
		ctx.begin(rid, 800, 600)
		for i in 20:
			ctx.save(); ctx.translate(3, 4); ctx.restore()
	_bench("20x save/translate/restore", 200, g7)
	# The warm cached path.
	ArtCache.enabled = true
	ctx.begin(rid, 800, 600)
	Draw.drawUnit(ctx, e, 0.7, 0)  # warm it
	_bench("drawUnit militia (cached, warm)", 2000, func(): Draw.drawUnit(ctx, e, 0.7, 0))
	var tc := Draw.tcol(0)
	_bench("  _pose_q + _unit_key", 2000, func(): ArtCache._unit_key(e, tc, ArtCache._pose_q(e, 0.7, false, 0.0), 3, 2, false))
	_bench("  useLook", 2000, func(): Draw.useLook(0, "militia", 7, 0.7))
	_bench("  blit_cell", 2000, func(): ctx.blit_cell(RID(), Rect2(0, 0, 10, 10), Vector2(5, 5), Vector2(3, 3), 1.0))
	var arr: Array = []
	for i in 360:
		var x := Entity.new()
		x.y = randf() * 900
		x.id = i
		arr.append(x)
	_bench("sort 360 by y (JS.sort_by_cmp)", 20, func(): JS.sort_by_cmp(arr.duplicate(), func(a, b): return a.y - b.y if a.y != b.y else a.id - b.id))
	ArtCache.enabled = false
	get_tree().quit()


func _bench(label: String, n: int, f: Callable) -> void:
	f.call()
	var t0 := Time.get_ticks_usec()
	for i in n:
		f.call()
	var us := float(Time.get_ticks_usec() - t0) / n
	print("%-40s %8.1f us" % [label, us])
