extends Node
var ctx := Ctx.new()
func _b(label: String, n: int, fn: Callable) -> void:
	fn.call()
	var best := 1e9
	for r in 5:
		var t0 := Time.get_ticks_usec()
		fn.call()
		best = minf(best, float(Time.get_ticks_usec() - t0))
	print("%-40s %7.2f us each" % [label, best / n])
func _ready() -> void:
	var rid := RenderingServer.canvas_item_create()
	ctx.begin(rid, 1600, 900)
	_b("fillStyle = string", 1000, func():
		for i in 1000: ctx.fillStyle = "#3c3427")
	_b("beginPath", 1000, func():
		for i in 1000: ctx.beginPath())
	_b("beginPath+roundRect", 1000, func():
		for i in 1000:
			ctx.beginPath(); ctx.roundRect(10, 10, 140, 30, 7))
	_b("beginPath+rect", 1000, func():
		for i in 1000:
			ctx.beginPath(); ctx.rect(10, 10, 140, 30))
	_b("beginPath+moveTo+lineTo x3", 1000, func():
		for i in 1000:
			ctx.beginPath(); ctx.moveTo(10, 10); ctx.lineTo(50, 10); ctx.lineTo(50, 40); ctx.lineTo(10, 40))
	_b("roundRect fill (cached)", 1000, func():
		for i in 1000:
			ctx.beginPath(); ctx.roundRect(10, 10, 140, 30, 7); ctx.fill())
	_b("roundRect stroke (cached)", 1000, func():
		for i in 1000:
			ctx.beginPath(); ctx.roundRect(10, 10, 140, 30, 7); ctx.stroke())
	Ctx.geo_cache_enabled = false
	_b("roundRect fill (uncached)", 300, func():
		for i in 300:
			ctx.beginPath(); ctx.roundRect(10, 10, 140, 30, 7); ctx.fill())
	_b("roundRect stroke (uncached)", 300, func():
		for i in 300:
			ctx.beginPath(); ctx.roundRect(10, 10, 140, 30, 7); ctx.stroke())
	_b("arc circle fill (uncached)", 300, func():
		for i in 300:
			ctx.beginPath(); ctx.arc(100, 100, 12, 0, TAU); ctx.fill())
	Ctx.geo_cache_enabled = true
	_b("fillText", 300, func():
		ctx.font = "13px 'Trebuchet MS', sans-serif"
		for i in 300: ctx.fillText("Some text of a usual length", 10, 10))
	_b("save+restore", 1000, func():
		for i in 1000: ctx.save(); ctx.restore())
	_b("translate", 1000, func():
		for i in 1000: ctx.translate(0.5, 0.5))
	get_tree().quit()
