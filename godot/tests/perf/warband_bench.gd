## Warband screen benchmark: the screen drawn every frame the way the game
## draws it — first a full shop (board, bench, relics, shop cards), then a
## watched fight — printing the CPU time each phase spends in draw() per
## frame, after a warm-up.
##
##   xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
##     --path godot res://tests/perf/warband_bench.tscn -- [--cache] [--frames N]
extends Node2D

var ctx := Ctx.new()
var screen := WarbandScreen.new()
var run: Warband.WarbandRun
var time := 0.0
var frames := 0
var per_phase := 150
var warmup := 30
var shop_us: Array = []
var battle_us: Array = []


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	var use_cache := false
	for i in args.size():
		match args[i]:
			"--cache": use_cache = true
			"--frames": per_phase = int(args[i + 1])
	ArtCache.enabled = use_cache
	DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_DISABLED)
	Engine.max_fps = 0
	get_window().size = Vector2i(1600, 900)
	run = Warband.WarbandRun.new(21, "marshal")
	var guard := 0
	while run.round < 7 and guard < 100:
		guard += 1
		match run.phase:
			"augment": run.pickAugment(0)
			"draft": run.takeCarousel(0)
			"shop":
				for k in 5:
					run.buy(k)
				run.fight()
			"result": run.next()
	run.gold = 60
	run.level = 6
	for ty in ["knight", "archer", "spearman", "longbow", "monk"]:
		run.shop = [ty, null, null, null, null]
		run.buy(0)
	run.shop = ["militia", "scout", "pikeman", "longbow", "hero"]
	run.itemStash = ["blade", "hide", "whetstone", "sigil"]


func _process(dt: float) -> void:
	time += 1.0 / 60.0
	queue_redraw()


func _draw() -> void:
	frames += 1
	if frames == per_phase:
		run.beginFight()
		screen.battle = Autobattle.LiveBattle.new(run.boardUnits(), run.pendingOpp, run.pendingSeed, 30.0, run.sideOpts(), run.fieldOpts())
		screen.battle.begin()
	ctx.begin(self)
	UI.ui.begin(ctx, {"mx": 700.0, "my": 300.0, "clicked": false, "rightClicked": false})
	var t0 := Time.get_ticks_usec()
	screen.draw(1600, 900, time, run)
	var us := Time.get_ticks_usec() - t0
	if frames > warmup and frames < per_phase:
		shop_us.append(us)
	elif frames > per_phase + warmup and run.phase == "battle":
		battle_us.append(us)
	if frames >= per_phase * 2 or (frames > per_phase + warmup and run.phase != "battle"):
		print("shop:   %s" % _stats(shop_us))
		print("battle: %s (%d frames)" % [_stats(battle_us), battle_us.size()])
		get_tree().quit()


static func _stats(xs: Array) -> String:
	if xs.is_empty():
		return "-"
	var s := xs.duplicate()
	s.sort()
	var mean := 0.0
	for x in s:
		mean += x
	mean /= s.size()
	return "draw mean %.1f ms, median %.1f ms, p95 %.1f ms" % [mean / 1000.0, s[s.size() / 2] / 1000.0, s[int(s.size() * 0.95)] / 1000.0]
