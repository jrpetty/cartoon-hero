## The scenarios the harness drives (tests/shots/harness.gd). The "shots" set
## is mirrored step for step by game/scripts/godot-shots.mjs in the TS build,
## so the two can be compared picture for picture: same config, same seed,
## same scripted orders on the same ticks, same camera.
extends RefCounted

var h  ## the harness
var app


func _init(harness) -> void:
	h = harness
	app = harness.app


func run(name: String, args: Array) -> void:
	match name:
		"shots": await shots()
		"perf": await perf(args)
		"input": await inputTest()
		"thread": await threadCheck()
		"gpuprobe": await gpuprobe()
		_: push_error("unknown scenario " + name)


## The fixed match every comparison shot uses.
static func baseConfig(players: int = 2) -> Dictionary:
	var teams: Array = []
	var aif: Array = [""]
	var facs := ["legion", "norse", "khanate", "shogunate", "ascendancy", "kingdom", "legion"]
	for i in players:
		teams.append(0)
		if i > 0:
			aif.append(facs[(i - 1) % facs.size()])
	return {
		"presetId": "open_plains", "seed": 4242, "difficulty": "knight", "aiDifficulties": [], "fairMode": true,
		"players": players, "teams": teams, "commander": "drillmaster", "faction": "kingdom", "aiFactions": aif,
		"nomad": false, "mode": "conquest",
	}


## Run the world n ticks by hand (the match is paused so nothing else moves it).
func ticks(n: int) -> void:
	app._joinTask()
	for i in n:
		app.world.tick()


func startPaused(cfg: Dictionary) -> void:
	app.startMatch(cfg)
	app.ais = []  # no brains: both builds play exactly the scripted orders
	app.paused = true
	app.camera.zoom = 1.0


func tcOf(team: int) -> Entity:
	for e: Entity in app.world.entities:
		if e.alive and e.team == team and e.type == "town_center":
			return e
	return null


## Two armies meeting between the bases. (Plain doubles and JS.hypot, so the
## TS mirror computes the very same spawn points.)
func battle() -> void:
	var w: World = app.world
	var mix := [["militia", 6], ["spearman", 5], ["archer", 6], ["knight", 4], ["crossbow", 4]]
	var ax: float = w.map.starts[0].x
	var ay: float = w.map.starts[0].y
	var bxx: float = w.map.starts[1].x
	var byy: float = w.map.starts[1].y
	var mx := (ax + bxx) / 2
	var my := (ay + byy) / 2
	var l := JS.hypot(ax - mx, ay - my)
	var nx := (ax - mx) / l
	var ny := (ay - my) / l
	for t in 2:
		var s := 1.0 if t == 0 else -1.0
		var bx := mx + nx * 260 * s
		var by := my + ny * 260 * s
		var k := 0
		for pair in mix:
			for j in pair[1]:
				w.spawnUnit(t, pair[0], bx + (k % 6) * 22 - 55, by + floorf(k / 6.0) * 22 - 50)
				k += 1
	for t in 2:
		var ids: Array = w.entitiesOf(t, T.Kind.Unit).filter(func(e): return e.type != "villager").map(func(e): return e.id)
		w.issueFormationMove(ids, mx, my, false, true)


func shots() -> void:
	var outs := []
	# 1. The opening.
	startPaused(baseConfig())
	ticks(40)
	var st = app.world.map.starts[0]
	app.camera.centerOn(st.x, st.y)
	await h.frames(30)
	await h.shot("godot_01_start")

	# 2. Selection + HUD: the Town Centre selected (command card), then the villagers.
	var tc := tcOf(0)
	app.select([tc.id])
	await h.frames(10)
	await h.shot("godot_02_select_tc")
	var vils: Array = app.world.entitiesOf(0, T.Kind.Unit).map(func(e): return e.id)
	app.select(vils)
	await h.frames(10)
	await h.shot("godot_03_select_villagers")

	# 3. A battle in the fog.
	app.select([])
	battle()
	ticks(170)
	var w: World = app.world
	var mid := Vector2((w.map.starts[0].x + w.map.starts[1].x) / 2, (w.map.starts[0].y + w.map.starts[1].y) / 2)
	app.camera.centerOn(mid.x, mid.y)
	await h.frames(20)
	await h.shot("godot_04_battle_fog")

	# 4. Production overview + scoreboard over the battle.
	app.world.player(0).resources = {"food": 5000.0, "wood": 5000.0, "gold": 5000.0}
	var bar: Entity = app.world.spawnBuilding(0, "barracks", st.x + 200, st.y - 40, true)
	for i in 3:
		app.world.trainUnit(0, tc.id, "villager")
		app.world.trainUnit(0, bar.id, "militia")
	ticks(30)
	app.showProduction = true
	app.showScoreboard = true
	await h.frames(10)
	await h.shot("godot_05_production_scoreboard")
	app.showProduction = false
	app.showScoreboard = false

	# 5. The Oath picker.
	app.world.spawnBuilding(0, "mill", st.x - 200, st.y + 60, true)
	app.camera.centerOn(st.x, st.y)
	app.hud.oathPicker.open(tc)
	await h.frames(10)
	await h.shot("godot_06_oath_picker")
	app.hud.oathPicker.close()

	# 6. Night at the base.
	var cyc: float = C.DAY_CYCLE
	app.world.time = floor(app.world.time / cyc) * cyc + cyc * 0.74
	ticks(10)
	app.camera.centerOn(st.x, st.y)
	await h.frames(15)
	await h.shot("godot_07_night")

	# 7. Weather (rain) by day.
	app.world.time = floor(app.world.time / cyc) * cyc + cyc * 1.25
	ticks(10)
	app.weather.type = "rain"
	app.camera.centerOn(mid.x, mid.y)
	await h.frames(20)
	await h.shot("godot_08_rain")
	app.weather.type = "clear"

	# 8. The caster view over a 4-realm watch game.
	app.startSpectate(baseConfig(4))
	app.ais = []
	app.paused = true
	ticks(40)
	await h.frames(20)
	await h.shot("godot_09_caster")

	# 9. Post-match.
	startPaused(baseConfig())
	ticks(200)
	app.finishMatch(true)
	await h.frames(20)
	await h.shot("godot_10_postmatch")


# ------------------------------------------------------------------ perf --

## A busy 8-player match: every realm fields an army and they all converge on
## the centre while the economies run; the camera follows the fight. Prints
## frame and sim timings (threaded unless --sync).
func perf(args: Array) -> void:
	var seconds := 30.0
	for i in args.size():
		if args[i] == "--seconds":
			seconds = float(args[i + 1])
	var cfg := baseConfig(8)
	cfg.presetId = "open_plains"
	app.startMatch(cfg)
	app.camera.zoom = 1.0
	var w: World = app.world
	var center := Vector2(w.worldW / 2, w.worldH / 2)
	var mix := [["militia", 8], ["spearman", 6], ["archer", 8], ["knight", 5], ["crossbow", 4], ["skirmisher", 4]]
	for t in w.numTeams:
		var s = w.map.starts[t]
		var dir := (center - Vector2(s.x, s.y)).normalized()
		var base := Vector2(s.x, s.y) + dir * 220
		var k := 0
		for pair in mix:
			for j in pair[1]:
				w.spawnUnit(t, pair[0], base.x + (k % 7) * 20 - 60, base.y + (k / 7) * 20 - 50)
				k += 1
		var ids: Array = w.entitiesOf(t, T.Kind.Unit).filter(func(e): return e.type != "villager").map(func(e): return e.id)
		w.issueFormationMove(ids, center.x + dir.x * 80, center.y + dir.y * 80, false, true)
		# Economies running: every villager on the nearest tree.
		var vil: Array = w.entitiesOf(t, T.Kind.Unit).filter(func(e): return e.type == "villager").map(func(e): return e.id)
		var tree = null
		var best := INF
		for e: Entity in w.entities:
			if e.alive and e.type == "tree":
				var d := Vector2(e.x - s.x, e.y - s.y).length()
				if d < best:
					best = d
					tree = e
		if tree != null:
			w.issueGather(vil, tree.id)
	var units := w.entities.filter(func(e): return e.alive and e.kind == T.Kind.Unit).size()
	print("perf: %d realms, %d units, threaded=%s" % [w.numTeams, units, str(app.threadSim)])
	app.camera.centerOn(center.x, center.y)
	await h.frames(30)
	app.frameLog.clear()
	app.logFrames = true
	var t0 := Time.get_ticks_msec()
	var shotTaken := false
	while Time.get_ticks_msec() - t0 < seconds * 1000.0 and app.world != null:
		await h.frames(1)
		if app.world != null:
			app.camera.centerOn(center.x, center.y)
		if not shotTaken and Time.get_ticks_msec() - t0 > seconds * 500.0:
			shotTaken = true
			await h.shot("godot_perf_8p_%s" % ("threaded" if app.threadSim else "sync"))
	app.logFrames = false
	_report(app.frameLog, w)


## Where a frame's time goes on this machine's (software) GPU: the same busy
## scene, paused, measured with one thing switched off at a time.
func gpuprobe() -> void:
	var cfg := baseConfig(8)
	app.startMatch(cfg)
	var w: World = app.world
	var center := Vector2(w.worldW / 2, w.worldH / 2)
	var mix := [["militia", 8], ["spearman", 6], ["archer", 8], ["knight", 5], ["crossbow", 4], ["skirmisher", 4]]
	for t in w.numTeams:
		var s = w.map.starts[t]
		var dir := (center - Vector2(s.x, s.y)).normalized()
		var k := 0
		for pair in mix:
			for j in pair[1]:
				w.spawnUnit(t, pair[0], center.x + dir.x * -150 + (k % 7) * 20 - 60, center.y + dir.y * -150 + (k / 7) * 20 - 50)
				k += 1
	app.paused = true
	app.camera.centerOn(center.x, center.y)
	await h.frames(40)
	var measure := func(label: String) -> void:
		await h.frames(5)
		var t0 := Time.get_ticks_usec()
		var m0: int = ArtCache.misses
		var p0: int = ArtCache.stats().pages
		for i in 20:
			await h.frames(1)
		var ms := (Time.get_ticks_usec() - t0) / 1000.0 / 20.0
		print("   art cache: %d misses in 20 frames, %d pages" % [ArtCache.misses - m0, ArtCache.stats().pages])
		print("gpuprobe %-28s %7.1f ms/frame   (main-thread CPU %.1f ms; segments world %d, overlay %d, ui %d)" % [label, ms, app.perf.cpuMs,
			app.renderer.ctx._seg_i + 1, app.renderer.octx._seg_i + 1, app.ctx._seg_i + 1])
	await measure.call("full")
	app.visible = false
	await measure.call("nothing drawn (app hidden)")
	app.visible = true
	app.uiLayer.visible = false
	app.overlayLayer.visible = false
	await measure.call("world layer only")
	app.uiLayer.visible = true
	app.overlayLayer.visible = true
	app.renderer.aggressiveLod = true
	await measure.call("no ground detail")
	app.renderer.aggressiveLod = false
	app.renderer.vignetteSize = Vector2i(-1, -1)
	var keepVig = app.renderer.vignetteItem
	await measure.call("full again")
	app.settings.weather = false
	app.world.revealAll = true
	await measure.call("no fog")
	app.world.revealAll = false
	app.spectating = true
	app.caster.clean = true
	await measure.call("no HUD (clean caster)")
	app.spectating = false
	app.caster.clean = false
	app.camera.zoom = 0.4
	await measure.call("zoomed out (LOD blobs)")
	app.camera.zoom = 1.0
	await h.shot("godot_gpuprobe")


func _report(log: Array, w: World) -> void:
	var own := log.filter(func(f): return not f.skipped)
	var dts: Array = log.map(func(f): return f.dt)
	var cpu: Array = log.map(func(f): return f.cpu)
	var col := func(rows: Array, k: String) -> Array: return rows.map(func(f): return f[k])
	var ticks := 0
	var worst := 0.0
	for f in log:
		ticks += f.ticks
		worst = maxf(worst, f.frameWorstTick)
	var seconds := 0.0
	for d in dts:
		seconds += d / 1000.0
	# The longest run of frames the world didn't advance on screen (skipped).
	var stall := 0.0
	var run := 0.0
	for f in log:
		if f.skipped:
			run += f.dt
			stall = maxf(stall, run)
		else:
			run = 0.0
	print("perf mode: %s, %d frames in %.1f s (%.1f fps), %d drawn while a tick was in flight" % [
		"threaded" if app.threadSim else "main-thread sim", log.size(), seconds, log.size() / maxf(0.001, seconds), log.size() - own.size()])
	print("perf frame interval ms: mean %.2f  p50 %.2f  p95 %.2f  p99 %.2f  max %.2f" % [_mean(dts), _pct(dts, 50), _pct(dts, 95), _pct(dts, 99), _pct(dts, 100)])
	print("perf main-thread CPU ms per frame: mean %.2f  p50 %.2f  p95 %.2f  max %.2f" % [_mean(cpu), _pct(cpu, 50), _pct(cpu, 95), _pct(cpu, 100)])
	print("perf   owning frames: snapshot %.2f  world render %.2f  HUD %.2f  events %.2f  input %.2f (means, ms)" % [
		_mean(col.call(own, "snapshot")), _mean(col.call(own, "render")), _mean(col.call(own, "ui")), _mean(col.call(own, "events")), _mean(col.call(own, "input"))])
	var skipCpu: Array = col.call(log.filter(func(f): return f.skipped), "cpu")
	if not skipCpu.is_empty():
		print("perf   frames drawn from the snapshot: CPU mean %.2f ms" % _mean(skipCpu))
	print("perf sim: %d ticks (%.1f/s), %.2f ms/tick smoothed, worst tick %.2f ms, %d units alive at the end" % [
		ticks, ticks / maxf(0.001, seconds), app.perf.tickMs, worst, w.entities.filter(func(e): return e.alive and e.kind == T.Kind.Unit).size()])
	print("perf frames over 33 ms: %d of %d; longest the world stood still on screen: %.0f ms" % [dts.filter(func(d): return d > 33.4).size(), dts.size(), stall])
	var worstFrames := log.duplicate()
	worstFrames.sort_custom(func(a, b): return a.cpu > b.cpu)
	for f in worstFrames.slice(0, 5):
		print("perf   slow frame: cpu %.1f ms (%s) snapshot %.1f render %.1f HUD %.1f events %.1f, %d ticks, worst tick %.1f" % [
			f.cpu, "skipped" if f.skipped else "owning", f.snapshot, f.render, f.ui, f.events, f.ticks, f.frameWorstTick])
		var ph: Dictionary = f.get("phases", {})
		var parts: Array = []
		for k in ph:
			if ph[k] >= 1.0:
				parts.append("%s %.1f" % [k, ph[k]])
		print("perf     render phases: %s  (cache misses %d, fallbacks %d)" % [", ".join(parts), f.get("misses", 0), f.get("fallbacks", 0)])


static func _mean(a: Array) -> float:
	if a.is_empty():
		return 0.0
	var s := 0.0
	for v in a:
		s += v
	return s / a.size()


static func _pct(a: Array, p: float) -> float:
	if a.is_empty():
		return 0.0
	var b := a.duplicate()
	b.sort()
	return b[clampi(int(ceil(p / 100.0 * b.size())) - 1, 0, b.size() - 1)]


# ----------------------------------------------------------------- input --

## Play through simulated input events only: drag-select the villagers,
## right-click a tree (gather), open Build → Economy → House and click to
## place it, select the Town Centre and click its villager button, and check
## the world changed each time.
func inputTest() -> void:
	startPaused(baseConfig())
	app.paused = false
	var w: World = app.world
	var st = w.map.starts[0]
	app.camera.centerOn(st.x, st.y)
	await h.frames(5)
	var fails := 0
	# 1. Box-select the villagers.
	var vils: Array = w.entitiesOf(0, T.Kind.Unit)
	var sx: Array = vils.map(func(e): return app.camera.worldToScreenX(e.x))
	var sy: Array = vils.map(func(e): return app.camera.worldToScreenY(e.y))
	await h.drag(sx.min() - 30, sy.min() - 30, sx.max() + 30, sy.max() + 30)
	await h.frames(2)
	var sel: Array = app.playerSelection()
	print("input: selected %d villagers" % sel.size())
	if sel.size() != vils.size():
		fails += 1
		print("FAIL: drag-select picked %d of %d" % [sel.size(), vils.size()])
	# 2. Right-click a tree: they go gather wood.
	var tree = null
	var best := INF
	for e: Entity in w.entities:
		if e.alive and e.type == "tree" and w.visibleTo(0, e):
			var d := Vector2(e.x - st.x, e.y - st.y).length()
			if d < best:
				best = d
				tree = e
	await h.click(app.camera.worldToScreenX(tree.x), app.camera.worldToScreenY(tree.y), MOUSE_BUTTON_RIGHT)
	await h.frames(2)
	app._joinTask()
	var gathering := vils.filter(func(e): return e.order.kind == T.OrderKind.Gather).size()
	print("input: %d villagers ordered to gather" % gathering)
	if gathering != vils.size():
		fails += 1
		print("FAIL: gather order reached %d of %d" % [gathering, vils.size()])
	var wood0: float = w.player(0).stats.gathered
	# 3. Build a house through the command card: B opens the build menu...
	await h.tap(KEY_B)
	await h.frames(2)
	if not app.hud.buildMenuOpen:
		fails += 1
		print("FAIL: B didn't open the build menu")
	# ...Economy is the first card slot, House the first in Economy.
	var cardW := HUD.CARD_W * (HUD.BTN + HUD.GAP) + HUD.GAP + 8
	var cardH := HUD.CARD_H * (HUD.BTN + HUD.GAP) + HUD.GAP + 8
	var bx: float = app.canvasW - cardW - 10 + 8 + HUD.BTN / 2.0
	var by: float = app.canvasH - cardH - 10 + 8 + HUD.BTN / 2.0
	await h.click(bx, by)
	await h.click(bx, by)
	await h.frames(2)
	if app.placing != "house":
		fails += 1
		print("FAIL: placing is %s, not house" % str(app.placing))
	# Place it a little way from the Town Centre, on clear grass.
	var spot := _clearSpot(w, "house", st)
	app._joinTask()
	var houses0 := w.countOf(0, "house")
	await h.click(app.camera.worldToScreenX(spot.x), app.camera.worldToScreenY(spot.y))
	await h.frames(3)
	app._joinTask()
	var houses1 := w.countOf(0, "house")
	print("input: houses %d -> %d" % [houses0, houses1])
	if houses1 != houses0 + 1:
		fails += 1
		print("FAIL: no house placed")
	# 4. Select the Town Centre by clicking it, then train a villager from the card.
	var tc := tcOf(0)
	await h.click(app.camera.worldToScreenX(tc.x), app.camera.worldToScreenY(tc.y))
	await h.frames(2)
	app._joinTask()
	if app.selection != [tc.id]:
		fails += 1
		print("FAIL: clicking the Town Centre selected %s" % str(app.selection))
	var q0: int = tc.productionQueue.size()
	await h.click(bx, by)
	await h.frames(2)
	app._joinTask()
	print("input: Town Centre queue %d -> %d" % [q0, tc.productionQueue.size()])
	if tc.productionQueue.size() != q0 + 1:
		fails += 1
		print("FAIL: villager not queued")
	# 5. Let it run: wood comes in, the house goes up, the villager appears.
	var pop0: float = w.player(0).popUsed
	for i in 40:
		await h.frames(15)
	app._joinTask()
	print("input: gathered %.0f -> %.0f, pop %s -> %s, tick %d" % [wood0, w.player(0).stats.gathered, pop0, w.player(0).popUsed, w.tickCount])
	if w.player(0).stats.gathered <= wood0:
		fails += 1
		print("FAIL: nothing was gathered")
	await h.shot("godot_input_test")
	print("input test: %s" % ("PASS" if fails == 0 else "FAIL (%d)" % fails))


func _clearSpot(w: World, type: String, st) -> Vector2:
	for r in range(140, 420, 40):
		for a in 16:
			var x: float = st.x + cos(a * TAU / 16) * r
			var y: float = st.y + sin(a * TAU / 16) * r
			if w.placementProblem(0, type, x, y) == null:
				return Vector2(x, y)
	return Vector2(st.x + 200, st.y)


# ---------------------------------------------------------------- thread --

## Threaded vs main-thread stepping, in the real running game: the same
## scripted orders on the same ticks must end on the same checksum.
func threadCheck() -> void:
	var sums := []
	for mode in [true, false]:
		app.threadSim = mode
		startPaused(baseConfig())
		battle()
		app.paused = false
		app.gameSpeed = 4.0
		while app.world.tickCount < 600:
			await h.frames(1)
		app._joinTask()
		app.paused = true
		# Run to exactly tick 600 + a fixed tail, the same in both modes.
		while app.world.tickCount < 700:
			app.world.tick()
		sums.append([app.world.tickCount, Commands.worldChecksum(app.world)])
	print("thread check: threaded %s, main-thread %s -> %s" % [str(sums[0]), str(sums[1]), "SAME" if sums[0] == sums[1] else "DIFFERENT"])
