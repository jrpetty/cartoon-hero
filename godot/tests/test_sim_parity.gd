extends TestCase
# The simulation matches the TypeScript build tick for tick: the same setup as
# game/scripts/godot_parity.ts (same map, World(seed), init, commands applied
# before the tick, tick(), drainEvents()), compared by worldChecksum after
# every tick. On a divergence the next recorded snapshot is diffed entity by
# entity, which says *what* went wrong.
#
# sim_ai2.json / sim_ai4.json need the skirmish AI: they are test_ai_parity.


## Replays a fixture; returns {"matched": last tick that matched, "ticks": run,
## "ms": time spent inside tick()}.
func runScenario(name: String) -> Dictionary:
	var fx = fixture("sim_%s.json" % name)
	if fx == null:
		return {}
	var players := int(fx.players)
	var map := SimTestUtil.mapFor(fx.preset, int(fx.seed), players)
	var w := World.new(int(fx.seed))
	var loadouts: Array = []
	var econ: Array = []
	for i in players:
		loadouts.append({})
		econ.append(1.0)
	w.init(map, loadouts, econ)
	var sums: Array = fx.checksums
	var snaps := {}
	for s in fx.snapshots:
		snaps[int(s.tick)] = s
	var result := {"matched": -1, "ticks": 0, "ms": 0.0}
	if Commands.worldChecksum(w) != int(sums[0]):
		failures.append("%s: checksum differs after init" % name)
		for line in SimTestUtil.diffSnapshots(snaps[0], SimTestUtil.snapshot(w)):
			failures.append("    " + line)
		return result
	result.matched = 0
	var cmds: Array = fx.get("commands", []) if fx.get("commands") != null else []
	var ci := 0
	var ticks := int(fx.ticks)
	var tickUs := 0
	for i in ticks:
		while ci < cmds.size() and int(cmds[ci][0]) == i:
			Commands.applyCommand(w, cmds[ci][1])
			ci += 1
		var t0 := Time.get_ticks_usec()
		w.tick()
		tickUs += Time.get_ticks_usec() - t0
		w.drainEvents()
		result.ticks = i + 1
		if i + 1 >= sums.size():
			break
		var got := Commands.worldChecksum(w)
		if got != int(sums[i + 1]):
			failures.append("%s: checksum diverged at tick %d (TS %d, Godot %d)" % [name, i + 1, int(sums[i + 1]), got])
			# Run on to the next snapshot and say what differs there.
			var next := -1
			for k in snaps:
				if k >= i + 1 and (next < 0 or k < next):
					next = k
			if next >= 0:
				var j := i + 1
				while j < next:
					while ci < cmds.size() and int(cmds[ci][0]) == j:
						Commands.applyCommand(w, cmds[ci][1])
						ci += 1
					w.tick()
					w.drainEvents()
					j += 1
				failures.append("    first differences at snapshot tick %d:" % next)
				for line in SimTestUtil.diffSnapshots(snaps[next], SimTestUtil.snapshot(w)):
					failures.append("    " + line)
			break
		result.matched = i + 1
		if snaps.has(i + 1):
			var d := SimTestUtil.diffSnapshots(snaps[i + 1], SimTestUtil.snapshot(w), 5)
			if d.size() > 0:
				failures.append("%s: snapshot at tick %d differs although the checksum matched" % [name, i + 1])
				for line in d:
					failures.append("    " + line)
		if w.winner != null:
			break
	result.ms = tickUs / 1000.0
	print("    %s: matched %d/%d ticks, tick() %.3f ms/tick (%d entities at the end)" % [
		name, result.matched, sums.size() - 1, result.ms / maxi(1, result.ticks), w.entities.size()])
	return result


func test_idle_matches_ts_every_tick() -> void:
	var r := runScenario("idle")
	eq(r.get("matched", -1), 200, "idle: last matching tick")


func test_commands_match_ts_every_tick() -> void:
	var r := runScenario("commands")
	eq(r.get("matched", -1), 1800, "commands: last matching tick")
