extends TestCase
# How long tick() takes in a busy state: two armies of 100 meeting in the
# middle of the map, on top of both economies. Reports ms/tick; asserts only a
# generous ceiling so a slow machine doesn't fail it, that the fight was real,
# and that it is the same fight the TS build plays (checksum taken from it).

const MIX := [["militia", 20], ["spearman", 15], ["archer", 20], ["knight", 15], ["skirmisher", 10], ["crossbow", 10], ["catapult", 5], ["monk", 5]]


func busyWorld() -> World:
	var w := SimTestUtil.build({"seed": 7})
	for t in [0, 1]:
		var x0: float = 1200.0 if t == 0 else 2000.0
		var k := 0
		for pair in MIX:
			for j in pair[1]:
				w.spawnUnit(t, pair[0], x0 + (k % 10) * 26, 1400 + (k / 10) * 26)
				k += 1
	for t in [0, 1]:
		var ids := w.entitiesOf(t, T.Kind.Unit).filter(func(e): return e.type != "villager").map(func(e): return e.id)
		w.issueFormationMove(ids, 2100.0 if t == 0 else 1300.0, 1530.0, false, true)
	# Keep the economies busy too.
	for t in [0, 1]:
		var tc := SimTestUtil.tcOf(w, t)
		var tree = null
		var best := INF
		for e in w.entities:
			if e.alive and e.type == "tree":
				var d := JS.hypot(e.x - tc.x, e.y - tc.y)
				if d < best:
					best = d
					tree = e
		w.issueGather(w.entitiesOf(t, T.Kind.Unit).filter(func(e): return e.type == "villager").map(func(e): return e.id), tree.id)
	return w


func test_busy_tick_time() -> void:
	var w := busyWorld()
	var ticks := 600
	var us := 0
	var worst := 0
	for i in ticks:
		var t0 := Time.get_ticks_usec()
		w.tick()
		var dt := Time.get_ticks_usec() - t0
		us += dt
		worst = maxi(worst, dt)
		w.drainEvents()
	var units := w.entities.filter(func(e): return e.alive and e.kind == T.Kind.Unit).size()
	var kills: float = w.player(0).stats.unitsKilled + w.player(1).stats.unitsKilled
	print("    busy: %.2f ms/tick average, %.2f ms worst, over %d ticks (210 units at the start, %d at the end, %d kills)" % [
		us / 1000.0 / ticks, worst / 1000.0, ticks, units, kills])
	check(kills > 20, "the armies never fought")
	# The same 600 ticks in the TS build end on this checksum (189 kills).
	eq(Commands.worldChecksum(w), 2022921085, "the battle played out differently from the TS build")
	check(us / 1000.0 / ticks < 100.0, "far too slow")
