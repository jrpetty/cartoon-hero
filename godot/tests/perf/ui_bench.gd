## Quick UI drawing benchmark: widgets and the main menu, CPU ms per frame.
##   godot --headless --path godot res://tests/perf/ui_bench.tscn
extends Node

var ctx := Ctx.new()
var rid: RID


func _bench(label: String, n: int, fn: Callable) -> void:
	fn.call()
	var best := 1e9
	for r in 3:
		var t0 := Time.get_ticks_usec()
		for i in n:
			fn.call()
		best = minf(best, (Time.get_ticks_usec() - t0) / 1000.0 / n)
	print("%-36s %8.3f ms (best of 3)" % [label, best])


func _ready() -> void:
	Storage.use_memory_only()
	audio.muted = true
	rid = RenderingServer.canvas_item_create()
	var ui := UI.ui
	var input := {"mx": -99, "my": -99, "clicked": false, "rightClicked": false}
	var frame := func(): ctx.begin(rid, 1600, 900); ui.begin(ctx, input)
	_bench("100x ui.button", 5, func():
		frame.call()
		for i in 100:
			ui.button("Button %d" % i, 10 + (i % 10) * 150, 10 + (i / 10) * 40, 140, 30, {"size": 13}))
	_bench("100x ui.panel", 5, func():
		frame.call()
		for i in 100:
			ui.panel(10 + (i % 10) * 150, 10 + (i / 10) * 40, 140, 30))
	_bench("100x roundRect fill", 5, func():
		frame.call()
		for i in 100:
			ctx.fillStyle = "#3c3427"; ctx.beginPath(); ctx.roundRect(10, 10 + i * 8, 140, 30, 7); ctx.fill())
	_bench("100x ui.text", 5, func():
		frame.call()
		for i in 100:
			ui.text("Some text of a usual length %d" % i, 10, 10 + i * 8, {"size": 13}))
	var p := Profile.load()
	var menu = Screens.MenuScreen.new()
	_bench("screen: menu", 5, func():
		frame.call()
		menu.draw(1600, 900, 1.0, p))
	get_tree().quit()
