extends TestCase
# main.gd's match loop: the simulation stepping on a worker thread must play
# exactly the match the main thread would. Commands are applied at tick
# boundaries and logged with the tick they land on; a log replayed into a
# fresh world (what a save and a replay are) must end on the live world's
# checksum, threaded or not, and a replay played back threaded and on the
# main thread must end in the same place.
#
# Also: an input-driven play test (simulated Godot input events only).

const DT := 1.0 / 60.0


static func config(seed: int = 4242) -> Dictionary:
	return {
		"presetId": "open_plains", "seed": seed, "difficulty": "knight", "aiDifficulties": [], "fairMode": true,
		"players": 2, "teams": [0, 0], "commander": "drillmaster", "faction": "kingdom", "aiFactions": ["", "legion"],
		"nomad": false, "mode": "conquest",
	}


func makeApp():
	Storage.use_memory_only()
	var app = load("res://scenes/main.tscn").instantiate()
	# The runner is mid-_ready, and the root won't take children then; the
	# runner itself will.
	var root := (Engine.get_main_loop() as SceneTree).root
	root.get_child(root.get_child_count() - 1).add_child(app)
	app.settings.autoLod = false
	# Pointer mid-screen (it starts at 0,0, which edge-scrolls).
	app.input.mx = 800
	app.input.my = 450
	return app


func freeApp(app) -> void:
	app._joinTask()
	app.get_parent().remove_child(app)
	app.free()


static func frames(app, n: int) -> void:
	for i in n:
		app._process(DT)


## A scripted session: orders given between frames (as input would, only when
## the world is ours), at frames, not ticks — so threaded and main-thread runs
## give them on different ticks, and only the logs can say which.
func playScripted(app, threaded: bool) -> void:
	app.threadSim = threaded
	app.startMatch(config())
	app.gameSpeed = 3.0
	var w: World = app.world
	var st = w.map.starts[0]
	var vils: Array = w.entitiesOf(0, T.Kind.Unit).map(func(e): return e.id)
	var tc = null
	for e in w.entities:
		if e.alive and e.team == 0 and e.type == "town_center":
			tc = e
	var tree = null
	var best := INF
	for e in w.entities:
		if e.alive and e.type == "tree":
			var d := JS.hypot(e.x - st.x, e.y - st.y)
			if d < best:
				best = d
				tree = e
	for f in 360:
		frames(app, 1)
		if f == 3 or f == 120 or f == 200 or f == 280:
			app._joinTask()
		if f == 3:
			app.dispatch({"t": "gather", "team": 0, "ids": vils, "node": tree.id, "queue": false})
		elif f == 120:
			app.dispatch({"t": "train", "team": 0, "buildingId": tc.id, "unit": "villager"})
		elif f == 200:
			app.dispatch({"t": "move", "team": 0, "ids": vils.slice(0, 1), "x": st.x + 160, "y": st.y + 40, "queue": false, "attackMove": false, "formation": false})
		elif f == 280:
			app.dispatch({"t": "gather", "team": 0, "ids": vils.slice(0, 1), "node": tree.id, "queue": true})
	app._joinTask()


## Replay the app's command log into a fresh world; true if it ends on the
## live world's checksum.
func faithful(app) -> Dictionary:
	var live: World = app.world
	var tick: int = live.tickCount
	var sum: int = Commands.worldChecksum(live)
	var log: Array = app.cmdLog.entries.duplicate(true)
	var cfg: Dictionary = app.config.duplicate(true)
	var w := World.new(int(cfg.seed))
	app.loadingSave = true
	app.startMatch(cfg)
	app.loadingSave = false
	var res := Savegame.replayTo(app.world, app.ais, log, tick, sum)
	return {"faithful": res.faithful, "tick": tick, "sum": sum, "log": log, "cfg": cfg}


func test_threaded_play_replays_to_the_same_world() -> void:
	var app = makeApp()
	playScripted(app, true)
	check(app.world.tickCount > 100, "the threaded match advanced (%d ticks)" % app.world.tickCount)
	eq(app.cmdLog.entries.size(), 4, "every order logged")
	var r := faithful(app)
	check(r.faithful, "the threaded match's log replays to its checksum")
	freeApp(app)


func test_main_thread_play_replays_to_the_same_world() -> void:
	var app = makeApp()
	playScripted(app, false)
	check(app.world.tickCount > 100, "the match advanced")
	var r := faithful(app)
	check(r.faithful, "the main-thread match's log replays to its checksum")
	freeApp(app)


func test_replay_playback_is_the_same_threaded_or_not() -> void:
	var app = makeApp()
	playScripted(app, true)
	var w: World = app.world
	var rec := {
		"version": 1, "id": "test", "savedAt": 0.0, "kind": "skirmish", "sim": Savegame.SAVE_FORMAT_VERSION,
		"setup": app.config.duplicate(true), "names": ["A", "B"], "pov": 0,
		"commands": app.cmdLog.entries.duplicate(true), "endTick": w.tickCount,
		"summary": {"map": "Open Plains", "players": 2, "durationSec": 0, "result": "Unfinished", "factions": []},
	}
	var liveSum := Commands.worldChecksum(w)
	var sums: Array = []
	for threaded in [true, false]:
		app.threadSim = threaded
		app.startReplay(rec)
		app.gameSpeed = 16.0
		var guard := 0
		while not app.replay.ended and guard < 5000:
			frames(app, 1)
			guard += 1
		app._joinTask()
		check(app.replay.ended, "the replay reached its end (threaded=%s)" % threaded)
		eq(app.world.tickCount, rec.endTick, "stopped on the last recorded tick (threaded=%s)" % threaded)
		sums.append(Commands.worldChecksum(app.world))
	eq(sums[0], liveSum, "threaded playback ends where the match did")
	eq(sums[1], liveSum, "main-thread playback ends where the match did")
	freeApp(app)


func test_frames_drawn_mid_tick_leave_the_world_alone() -> void:
	# While a task is in flight the frame must not touch the World: it draws
	# from the snapshot, and input waits.
	var app = makeApp()
	app.threadSim = true
	app.startMatch(config())
	frames(app, 30)
	var skipped0: int = app.perf.skipped
	var queuedBefore: int = app._queued.size()
	# Force a long task, then send input while it runs.
	app._joinTask()
	app._taskSteps = 40
	app._taskTickCount = 0
	app._task = WorkerThreadPool.add_task(app._tickTask, true)
	var ev := InputEventKey.new()
	ev.keycode = KEY_H
	ev.pressed = true
	app._input(ev)
	check(app._queued.size() == queuedBefore + 1 or not app._taskBusy(), "input waits for the world")
	app._process(DT)
	app._joinTask()
	app._process(DT)
	eq(app._queued.size(), 0, "queued input delivered on the next owning frame")
	check(app.perf.skipped >= skipped0, "frames drawn from the snapshot are counted")
	freeApp(app)


# --------------------------------------------------------------- input --

## Events arrive in window pixels; the game may lay out in a virtual canvas
## (a small window — headless is 64×64 — is scaled up to a 760 short side).
static func win(app, x: float, y: float) -> Vector2:
	return Vector2(x, y) / app.input.canvasScale


static func motion(app, x: float, y: float) -> void:
	var ev := InputEventMouseMotion.new()
	ev.position = win(app, x, y)
	app._input(ev)


static func press(app, x: float, y: float, idx: int, down: bool) -> void:
	var ev := InputEventMouseButton.new()
	ev.position = win(app, x, y)
	ev.button_index = idx
	ev.pressed = down
	app._input(ev)


static func click(app, x: float, y: float, idx: int = MOUSE_BUTTON_LEFT) -> void:
	# Far enough apart in time that two clicks aren't a double-click.
	app.input.lastClickTime = -1000.0
	motion(app, x, y)
	frames(app, 1)
	press(app, x, y, idx, true)
	frames(app, 1)
	press(app, x, y, idx, false)
	frames(app, 2)
	app._joinTask()
	frames(app, 1)
	app._joinTask()


static func keyTap(app, code: Key, unicode: int) -> void:
	for down in [true, false]:
		var ev := InputEventKey.new()
		ev.keycode = code
		ev.physical_keycode = code
		ev.unicode = unicode
		ev.pressed = down
		app._input(ev)
		frames(app, 1)
	app._joinTask()


func test_play_by_input_select_gather_build_train() -> void:
	var app = makeApp()
	app.threadSim = true
	app.startMatch(config())
	var w: World = app.world
	var st = w.map.starts[0]
	app.camera.centerOn(st.x, st.y)
	frames(app, 3)
	app._joinTask()
	var cam: Camera = app.camera
	# 1. Box-select the villagers with a drag.
	var vils: Array = w.entitiesOf(0, T.Kind.Unit)
	var xs: Array = vils.map(func(e): return cam.worldToScreenX(e.x))
	var ys: Array = vils.map(func(e): return cam.worldToScreenY(e.y))
	var x0: float = xs.min() - 30
	var y0: float = ys.min() - 30
	var x1: float = xs.max() + 30
	var y1: float = ys.max() + 30
	motion(app, x0, y0)
	frames(app, 1)
	press(app, x0, y0, MOUSE_BUTTON_LEFT, true)
	for i in range(1, 6):
		motion(app, lerpf(x0, x1, i / 5.0), lerpf(y0, y1, i / 5.0))
		frames(app, 1)
	press(app, x1, y1, MOUSE_BUTTON_LEFT, false)
	frames(app, 2)
	app._joinTask()
	frames(app, 1)
	app._joinTask()
	eq(app.playerSelection().size(), vils.size(), "the drag selected every villager")
	# 2. Right-click a tree: everyone goes for wood.
	var tree = null
	var best := INF
	for e in w.entities:
		if e.alive and e.type == "tree" and w.visibleTo(0, e):
			var d := JS.hypot(e.x - st.x, e.y - st.y)
			if d < best:
				best = d
				tree = e
	click(app, cam.worldToScreenX(tree.x), cam.worldToScreenY(tree.y), MOUSE_BUTTON_RIGHT)
	eq(vils.filter(func(e): return e.order.kind == T.OrderKind.Gather).size(), vils.size(), "a right-click on a tree is a gather order")
	# 3. B opens the build menu; Economy, then House, from the command card.
	keyTap(app, KEY_B, 98)
	check(app.hud.buildMenuOpen, "B opened the build menu")
	var cardW := HUD.CARD_W * (HUD.BTN + HUD.GAP) + HUD.GAP + 8
	var cardH := HUD.CARD_H * (HUD.BTN + HUD.GAP) + HUD.GAP + 8
	var bx: float = app.canvasW - cardW - 10 + 8 + HUD.BTN / 2.0
	var by: float = app.canvasH - cardH - 10 + 8 + HUD.BTN / 2.0
	click(app, bx, by)
	click(app, bx, by)
	eq(app.placing, "house", "Economy → House arms placement")
	# Click clear grass near the Town Centre.
	var spot := Vector2.ZERO
	for r in range(140, 420, 40):
		for a in 16:
			var x: float = st.x + cos(a * TAU / 16) * r
			var y: float = st.y + sin(a * TAU / 16) * r
			if spot == Vector2.ZERO and w.placementProblem(0, "house", x, y) == null:
				spot = Vector2(x, y)
	var houses0 := w.countOf(0, "house")
	click(app, cam.worldToScreenX(spot.x), cam.worldToScreenY(spot.y))
	eq(w.countOf(0, "house"), houses0 + 1, "the click placed a house")
	check(app.placing == null, "placement disarmed after one house")
	# 4. Click the Town Centre, then its first card button (Villager).
	var tc = null
	for e in w.entities:
		if e.alive and e.team == 0 and e.type == "town_center":
			tc = e
	click(app, cam.worldToScreenX(tc.x), cam.worldToScreenY(tc.y))
	eq(app.selection, [tc.id], "clicking the Town Centre selects it")
	var q0: int = tc.productionQueue.size()
	click(app, bx, by)
	eq(tc.productionQueue.size(), q0 + 1, "the card's first button queued a villager")
	eq(tc.productionQueue[q0], "u:villager", "and it is a villager")
	# 5. Run: the villagers (all three were selected, so all three build)
	# raise the house, and the Town Centre turns out a villager.
	var house = null
	for e in w.entities:
		if e.alive and e.team == 0 and e.type == "house":
			house = e
	var units0 := w.entitiesOf(0, T.Kind.Unit).size()
	app.gameSpeed = 3.0
	for i in 900:
		frames(app, 1)
	app._joinTask()
	check(house.buildProgress > 0.0, "the house went up (%.2f)" % house.buildProgress)
	check(w.entitiesOf(0, T.Kind.Unit).size() > units0, "a villager was trained (%d → %d)" % [units0, w.entitiesOf(0, T.Kind.Unit).size()])
	# 6. Every order went through the log (input → dispatch → log).
	check(app.cmdLog.entries.size() >= 3, "orders were logged (%d)" % app.cmdLog.entries.size())
	freeApp(app)


func test_control_groups_and_hotkeys() -> void:
	var app = makeApp()
	app.startMatch(config())
	frames(app, 2)
	app._joinTask()
	var w: World = app.world
	var vils: Array = w.entitiesOf(0, T.Kind.Unit).map(func(e): return e.id)
	app.select(vils)
	# Ctrl+1 sets group 1; Escape-free select-none; 1 recalls it.
	app.input.ctrl = true
	app.handleKey("1")
	app.input.ctrl = false
	eq(app.controlGroups[1].size(), vils.size(), "Ctrl+1 set the group")
	app.select([])
	app.handleKey("1")
	eq(app.selection.size(), vils.size(), "1 selected the group")
	# H: the Town Centre.
	app.handleKey("h")
	eq(w.byId[app.selection[0]].type, "town_center", "H selects the Town Centre")
	# Tab toggles the scoreboard, F3 pauses.
	app.handleKey("Tab")
	check(app.showScoreboard, "Tab opens the scoreboard")
	app.handleKey("F3")
	check(app.paused, "F3 pauses")
	freeApp(app)
