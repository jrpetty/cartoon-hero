## How fast does the simulation step on a worker thread, compared with the
## main thread — alone, and with the main thread busy drawing at the same time?
##
##   godot --headless --path godot res://tests/perf/sim_thread_bench.tscn
extends Node

const TICKS := 200


func _ready() -> void:
	await get_tree().process_frame
	var res := {}
	res["main thread"] = _bench(func(w): _run(w), false)
	res["worker, main idle"] = _bench(func(w):
		var id := WorkerThreadPool.add_task(func(): _run(w), true)
		WorkerThreadPool.wait_for_task_completion(id), false)
	res["worker, main drawing"] = _bench(func(w):
		var id := WorkerThreadPool.add_task(func(): _run(w), true)
		var ctx := LayerCtx.new()
		var item := RenderingServer.canvas_item_create()
		while not WorkerThreadPool.is_task_completed(id):
			_busyDraw(ctx, item)
		WorkerThreadPool.wait_for_task_completion(id)
		RenderingServer.free_rid(item), false)
	for k in res:
		print("sim %-22s %.2f ms/tick" % [k, res[k]])
	get_tree().quit()


func _bench(fn: Callable, _x: bool) -> float:
	var w := _busyWorld()
	var t0 := Time.get_ticks_usec()
	fn.call(w)
	return (Time.get_ticks_usec() - t0) / 1000.0 / TICKS


func _run(w: World) -> void:
	for i in TICKS:
		w.tick()


## The main thread's typical work: Ctx fills and text, reading content data.
func _busyDraw(ctx: LayerCtx, item: RID) -> void:
	ctx.begin(item, 1600, 900)
	for i in 60:
		ctx.fillStyle = Palette.withAlpha(C.PAL.uiAccent, 0.5)
		ctx.beginPath()
		ctx.roundRect(10 + i * 3, 10, 80, 30, 6)
		ctx.fill()
		var u: Dictionary = C.UNITS.get("militia")
		ctx.font = "13px sans-serif"
		ctx.fillText(u.name, 20, 20 + i)


func _busyWorld() -> World:
	var w := World.new(7)
	var map := MapGen.generateMap("open_plains", 7, 2)
	w.init(map, [{}, {}], [1.0, 1.0])
	var mix := [["militia", 20], ["spearman", 15], ["archer", 20], ["knight", 15], ["skirmisher", 10], ["crossbow", 10], ["catapult", 5], ["monk", 5]]
	for t in [0, 1]:
		var x0: float = 1200.0 if t == 0 else 2000.0
		var k := 0
		for pair in mix:
			for j in pair[1]:
				w.spawnUnit(t, pair[0], x0 + (k % 10) * 26, 1400 + (k / 10) * 26)
				k += 1
	for t in [0, 1]:
		var ids := w.entitiesOf(t, T.Kind.Unit).filter(func(e): return e.type != "villager").map(func(e): return e.id)
		w.issueFormationMove(ids, 2100.0 if t == 0 else 1300.0, 1530.0, false, true)
	return w
