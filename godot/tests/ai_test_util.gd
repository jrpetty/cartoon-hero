## Shared helpers for the skirmish-AI tests: matches set up the way the TS
## fixture scripts set them up (game/scripts/godot_parity.ts and
## game/scripts/godot_ai_parity.ts), the AI digest those scripts record every
## tick, and a brain's state as they snapshot it.
class_name AITestUtil


## The match's map: from maps.json when it is recorded there, else generated
## (MapGen is parity-checked against the TS generator in test_maps_parity).
static func mapOf(preset: String, seed: int, players: int, nomad := false, alliances = null) -> Dictionary:
	if not nomad and alliances == null:
		var f := FileAccess.open("res://tests/fixtures/maps.json", FileAccess.READ)
		var maps = JSON.parse_string(f.get_as_text())
		for entry in maps:
			if entry.id == preset and int(entry.seed) == seed and int(entry.n) == players:
				return SimTestUtil.toMapData(entry.map)
	return MapGen.generateMap(preset, seed, players, nomad, alliances)


static func _ints(a) -> Variant:
	if a == null:
		return null
	var out: Array = []
	for v in a:
		out.append(int(v))
	return out


## A scenario from game/scripts/godot_ai_parity.ts (`AIScenario`), built
## exactly as its `setup()` builds it. Returns {"w": World, "ais": [SkirmishAI]}.
static func setup(s: Dictionary) -> Dictionary:
	var players := int(s.players)
	var seed := int(s.seed)
	var nomad: bool = s.get("nomad", false) == true
	var alliances = _ints(s.get("alliances"))
	var map := mapOf(s.preset, seed, players, nomad, alliances)
	var w := World.new(seed)
	var diffs: Array = s.diffs
	var loadouts: Array = []
	var econ: Array = []
	for t in players:
		loadouts.append({})
		var d = diffs[t]
		econ.append(float(C.DIFFICULTIES[d].econMult) if s.get("econFromDiff", false) == true and d != null else 1.0)
	var mode: String = s.get("mode") if s.get("mode") != null else "conquest"
	w.init(map, loadouts, econ, alliances, s.get("commanders"), nomad, null, mode, s.get("factions"))
	if s.get("ford") != null:
		var half := int(s.ford)
		var midC := floori(w.terrainCols / 2.0)
		for cy in w.terrainRows:
			for dx in range(-half, half + 1):
				w.terrain[cy * w.terrainCols + midC + dx] = TerrainKinds.Terrain.Shallow
				w.grid.setBlocked(midC + dx, cy, false)
	var ais: Array = []
	var adapt: Dictionary = s.get("adapt") if s.get("adapt") != null else {}
	var blind: Array = _ints(s.get("blind")) if s.get("blind") != null else []
	for t in players:
		var d = diffs[t]
		if d == null:
			continue
		var ai := SkirmishAI.new(w, t, C.DIFFICULTIES[d])
		var a = adapt.get(str(t))
		if a != null:
			ai.adapt(a)
		if blind.has(t):
			ai.readsGround = false
		ais.append(ai)
	return {"w": w, "ais": ais}


static func _fnvMix(h: int, n: int) -> int:
	for i in 4:
		h = JS.u32(h ^ (JS.ushr(n, i * 8) & 0xff))
		h = JS.u32(JS.imul(h, 16777619))
	return h


## godot_ai_parity.ts `aiChecksum`: every brain's RNG state and a few decisions.
static func aiChecksum(w: World, ais: Array) -> int:
	var h := 2166136261
	h = _fnvMix(h, JS.u32(w.rng.state))
	for ai: SkirmishAI in ais:
		h = _fnvMix(h, JS.u32(ai.rng.state))
		h = _fnvMix(h, (1 if ai.attacking else 0) | (2 if ai.savingForAge else 0) | (4 if ai.wallsPlanned else 0))
		h = _fnvMix(h, ai.scoutId)
		h = _fnvMix(h, ai.lastFocusId)
		h = _fnvMix(h, ai.seenWalls)
		h = _fnvMix(h, int(JS.round(ai.lastWaveTime * 1000.0)))
	return JS.u32(h)


## godot_ai_parity.ts `aiState`.
static func aiState(ai: SkirmishAI) -> Dictionary:
	var oc := {}
	for k in ai.oathChoice:
		oc[str(k)] = ai.oathChoice[k]
	return {
		"team": ai.team, "style": ai.style, "timer": ai.timer, "gameTime": ai.gameTime, "rng": JS.u32(ai.rng.state),
		"seen": ai.seen.duplicate(), "seenWalls": ai.seenWalls, "attacking": ai.attacking, "savingForAge": ai.savingForAge,
		"saveStartedAt": ai.saveStartedAt, "saveGaveUpAt": ai.saveGaveUpAt, "ageReadySince": ai.ageReadySince,
		"lastWaveTime": ai.lastWaveTime, "lastHarassTime": ai.lastHarassTime, "lastBridgeTime": ai.lastBridgeTime,
		"lastTradeTime": ai.lastTradeTime, "lastScoutTime": ai.lastScoutTime, "lastRetreatTime": ai.lastRetreatTime,
		"lastAllyHelpTime": ai.lastAllyHelpTime, "lastPressTime": ai.lastPressTime, "lastCalloutTime": ai.lastCalloutTime,
		"scoutId": ai.scoutId, "lastFocusId": ai.lastFocusId, "wallsPlanned": ai.wallsPlanned,
		"everBuilt": ai.everBuilt.keys(), "oathChoice": oc,
	}


static func _same(a, b) -> bool:
	if (a is float or a is int) and (b is float or b is int):
		return absf(float(a) - float(b)) <= 1e-9 * maxf(1.0, absf(float(a)))
	if a is Dictionary and b is Dictionary:
		if a.size() != b.size():
			return false
		for k in a:
			if not b.has(k) or not _same(a[k], b[k]):
				return false
		return true
	if a is Array and b is Array:
		if a.size() != b.size():
			return false
		for i in a.size():
			if not _same(a[i], b[i]):
				return false
		return true
	return a == b


## Field-by-field differences between recorded and live brain states.
static func diffAIStates(want: Array, got: Array, limit := 20) -> Array:
	var out: Array = []
	for i in mini(want.size(), got.size()):
		for k in want[i]:
			if not _same(want[i][k], got[i].get(k)):
				out.append("AI %d %s: TS %s, Godot %s" % [i, k, str(want[i][k]), str(got[i].get(k))])
	return out.slice(0, limit)


## Replays an ai_<name>.json scenario, comparing the world checksum and the AI
## digest after every tick; failures go to `failures`. Returns {"matched",
## "ticks", "tickMs", "aiMs"} and, with `samples`, the per-tick microseconds
## spent in world.tick() and in the AIs' update() ("tickUs", "aiUs" arrays).
static func runScenario(fx: Dictionary, name: String, failures: Array, samples := false) -> Dictionary:
	var built := AITestUtil.setup(fx)
	var w: World = built.w
	var ais: Array = built.ais
	var sums: Array = fx.checksums
	var aiSums: Array = fx.aiSums
	var snaps := {}
	for s in fx.snapshots:
		snaps[int(s.tick)] = s
	var result := {"matched": -1, "ticks": 0, "tickMs": 0.0, "aiMs": 0.0}
	var tickSamples := PackedInt64Array()
	var aiSamples := PackedInt64Array()
	if Commands.worldChecksum(w) != int(sums[0]) or AITestUtil.aiChecksum(w, ais) != int(aiSums[0]):
		failures.append("%s: checksum differs after setup" % name)
		for line in SimTestUtil.diffSnapshots(snaps[0], SimTestUtil.snapshot(w)):
			failures.append("    " + line)
		for line in AITestUtil.diffAIStates(snaps[0].ais, ais.map(AITestUtil.aiState)):
			failures.append("    " + line)
		return result
	result.matched = 0
	var tickUs := 0
	var aiUs := 0
	var dt: float = C.SIM_DT
	var n := sums.size() - 1
	for i in n:
		var t0 := Time.get_ticks_usec()
		w.tick()
		var t1 := Time.get_ticks_usec()
		for ai: SkirmishAI in ais:
			ai.update(dt)
		var t2 := Time.get_ticks_usec()
		aiUs += t2 - t1
		tickUs += t1 - t0
		if samples:
			tickSamples.append(t1 - t0)
			aiSamples.append(t2 - t1)
		w.drainEvents()
		result.ticks = i + 1
		var got := Commands.worldChecksum(w)
		var gotAI := AITestUtil.aiChecksum(w, ais)
		if got != int(sums[i + 1]) or gotAI != int(aiSums[i + 1]):
			failures.append("%s: diverged at tick %d (world TS %d / Godot %d, AI digest TS %d / Godot %d)" % [
				name, i + 1, int(sums[i + 1]), got, int(aiSums[i + 1]), gotAI])
			var next := -1
			for k in snaps:
				if k >= i + 1 and (next < 0 or k < next):
					next = k
			if next >= 0:
				var j := i + 1
				while j < next and w.winner == null:
					w.tick()
					for ai: SkirmishAI in ais:
						ai.update(dt)
					w.drainEvents()
					j += 1
				failures.append("    first differences at snapshot tick %d:" % next)
				for line in AITestUtil.diffAIStates(snaps[next].ais, ais.map(AITestUtil.aiState)):
					failures.append("    " + line)
				for line in SimTestUtil.diffSnapshots(snaps[next], SimTestUtil.snapshot(w)):
					failures.append("    " + line)
			break
		result.matched = i + 1
		if snaps.has(i + 1):
			var d := AITestUtil.diffAIStates(snaps[i + 1].ais, ais.map(AITestUtil.aiState), 5)
			d.append_array(SimTestUtil.diffSnapshots(snaps[i + 1], SimTestUtil.snapshot(w), 5))
			if d.size() > 0:
				failures.append("%s: snapshot at tick %d differs although the checksums matched" % [name, i + 1])
				for line in d:
					failures.append("    " + line)
		if w.winner != null:
			break
	if samples:
		result.tickUs = tickSamples
		result.aiUs = aiSamples
	result.tickMs = tickUs / 1000.0
	result.aiMs = aiUs / 1000.0
	var nt := maxi(1, result.ticks)
	print("    %s: matched %d/%d ticks; world.tick() %.3f ms/tick, AI update() %.3f ms/tick (%d entities at the end, winner %s)" % [
		name, result.matched, n, result.tickMs / nt, result.aiMs / nt, w.entities.size(), str(w.winner)])
	return result
