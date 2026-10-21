extends TestCase
# The skirmish AI matches the TypeScript build tick for tick: AI-vs-AI matches
# set up exactly as game/scripts/godot_parity.ts does (generateMap, World(seed),
# init with empty loadouts and econ 1, one SkirmishAI per team built in team
# order; each tick: world.tick(), every AI update(SIM_DT), drainEvents()),
# compared by worldChecksum after every tick. On a divergence the next recorded
# snapshot is diffed entity by entity, which says *what* went wrong.
#
# The ai_*.json fixtures (game/scripts/godot_ai_parity.ts) cover the rest of
# the brain — eight seats, alliances, Regicide, Survival, nomad starts,
# factions and commanders, a rival's adaptation, a blind AI, a ford to bridge —
# and also record an AI digest every tick (each brain's RNG state and key
# decisions), which catches a different choice before the world shows it.
# (The eight-seat, twelve-minute ai_highlands8 match is test_ai_perf, which
# times it as well.)


## Replays an AI fixture; returns {"matched": last tick that matched, "ticks":
## run, "tickMs": time in world.tick(), "aiMs": time in the AIs' update()}.
func runAIScenario(name: String) -> Dictionary:
	var fx = fixture("sim_%s.json" % name)
	if fx == null:
		return {}
	var players := int(fx.players)
	var seed := int(fx.seed)
	var map := AITestUtil.mapOf(fx.preset, seed, players)
	var w := World.new(seed)
	var loadouts: Array = []
	var econ: Array = []
	for i in players:
		loadouts.append({})
		econ.append(1.0)
	w.init(map, loadouts, econ)
	var ais: Array = []
	for t in players:
		ais.append(SkirmishAI.new(w, t, C.DIFFICULTIES[fx.ai]))
	var sums: Array = fx.checksums
	var snaps := {}
	for s in fx.snapshots:
		snaps[int(s.tick)] = s
	var result := {"matched": -1, "ticks": 0, "tickMs": 0.0, "aiMs": 0.0}
	if Commands.worldChecksum(w) != int(sums[0]):
		failures.append("%s: checksum differs after init" % name)
		return result
	result.matched = 0
	var ticks := int(fx.ticks)
	var tickUs := 0
	var aiUs := 0
	var dt: float = C.SIM_DT
	for i in ticks:
		var t0 := Time.get_ticks_usec()
		w.tick()
		var t1 := Time.get_ticks_usec()
		for ai: SkirmishAI in ais:
			ai.update(dt)
		aiUs += Time.get_ticks_usec() - t1
		tickUs += t1 - t0
		w.drainEvents()
		result.ticks = i + 1
		if i + 1 >= sums.size():
			break
		var got := Commands.worldChecksum(w)
		if got != int(sums[i + 1]):
			failures.append("%s: checksum diverged at tick %d (TS %d, Godot %d)" % [name, i + 1, int(sums[i + 1]), got])
			var next := -1
			for k in snaps:
				if k >= i + 1 and (next < 0 or k < next):
					next = k
			if next >= 0:
				var j := i + 1
				while j < next:
					w.tick()
					for ai: SkirmishAI in ais:
						ai.update(dt)
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
	result.tickMs = tickUs / 1000.0
	result.aiMs = aiUs / 1000.0
	var n := maxi(1, result.ticks)
	print("    %s: matched %d/%d ticks; world.tick() %.3f ms/tick, AI update() %.3f ms/tick (%d entities at the end)" % [
		name, result.matched, sums.size() - 1, result.tickMs / n, result.aiMs / n, w.entities.size()])
	return result


func test_ai2_knight_duel_matches_ts_every_tick() -> void:
	var fx = fixture("sim_ai2.json")
	if fx == null:
		return
	var r := runAIScenario("ai2")
	eq(r.get("matched", -1), (fx.checksums as Array).size() - 1, "ai2: last matching tick")


func test_ai4_lord_ffa_matches_ts_every_tick() -> void:
	var fx = fixture("sim_ai4.json")
	if fx == null:
		return
	var r := runAIScenario("ai4")
	eq(r.get("matched", -1), (fx.checksums as Array).size() - 1, "ai4: last matching tick")


func _scenario(name: String) -> void:
	var fx = fixture("ai_%s.json" % name)
	if fx == null:
		return
	var r := AITestUtil.runScenario(fx, name, failures)
	eq(r.get("matched", -1), (fx.checksums as Array).size() - 1, "%s: last matching tick" % name)


func test_teams_2v2_factions_commanders_match_ts_every_tick() -> void:
	_scenario("teams")


func test_regicide_matches_ts_every_tick() -> void:
	_scenario("regicide")


func test_nomad_start_matches_ts_every_tick() -> void:
	_scenario("nomad")


func test_rival_adaptation_and_blind_ai_match_ts_every_tick() -> void:
	_scenario("rival")


func test_survival_allies_match_ts_every_tick() -> void:
	_scenario("survival")


func test_ford_and_bridges_match_ts_every_tick() -> void:
	_scenario("ford")
