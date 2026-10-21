extends TestCase
# Ported from savegame.test.ts and replay_file.test.ts (without the skirmish
# AI, which is ported separately — these matches are driven by orders alone),
# the pure parts of sim/terrain.test.ts, and checks of the warband conditions
# and the day/night and content function ports against values taken from the
# TS build.

const P := 0


func before() -> void:
	Storage.use_memory_only()


func match(seed: int) -> World:
	return SimTestUtil.build({"seed": seed, "map": ["highlands", 80808, 8], "players": 2, "alliances": [0, 1]})


## The live loop shape: commands due, world tick, then drain.
func play(w: World, ticks: int, log = null, orders: Array = []) -> void:
	for tick in ticks:
		for o in orders:
			if o.t == tick:
				if log != null:
					log.record(tick, o.c)
				Commands.applyCommand(w, o.c)
		w.tick()
		w.drainEvents()


# ---- savegame ----

func test_two_matches_from_the_same_seed_are_the_same_match() -> void:
	var a := match(7)
	var b := match(7)
	play(a, 600)
	play(b, 600)
	eq(Commands.worldChecksum(b), Commands.worldChecksum(a))
	# (The TS test also checks that another seed differs; there the seed picks
	# a different map and the AI plays it. Here both share the fixture map and
	# no AI runs, so the seed only turns units' facing, which isn't hashed —
	# test_replay_depends_on_the_log covers "different input, different match".)


func test_replay_reproduces_a_match_played_with_orders() -> void:
	var live := match(11)
	var log := Savegame.CommandLog.new()
	var vill = SimTestUtil.find(live, func(e): return e.alive and e.team == P and e.type == "villager")
	var tc := SimTestUtil.tcOf(live)
	var orders := [
		{"t": 5, "c": {"t": "train", "team": P, "buildingId": tc.id, "unit": "villager"}},
		{"t": 40, "c": {"t": "move", "team": P, "ids": [vill.id], "x": tc.x + 300, "y": tc.y + 120, "queue": false, "attackMove": false}},
		{"t": 200, "c": {"t": "place", "team": P, "building": "house", "x": tc.x + 220, "y": tc.y + 160, "builders": [vill.id]}},
		{"t": 600, "c": {"t": "stance", "team": P, "ids": [vill.id], "stance": 1}},
	]
	play(live, 1000, log, orders)
	var expected := Commands.worldChecksum(live)
	var fresh := match(11)
	var res := Savegame.replayTo(fresh, [], log.entries, 1000, expected)
	eq(res.actual, expected, "the replay diverged from the live match")
	eq(res.faithful, true)
	eq(fresh.tickCount, 1000, "lands on the tick it was asked for")
	check(fresh.drainEvents().size() < 64, "drains events as it goes")


func test_replay_reports_a_divergence() -> void:
	var res := Savegame.replayTo(match(11), [], [], 200, 12345)
	eq(res.faithful, false)
	eq(res.expected, 12345)
	check(res.actual != 12345)


func test_replay_depends_on_the_log() -> void:
	var live := match(13)
	var log := Savegame.CommandLog.new()
	var tc := SimTestUtil.tcOf(live)
	var before := live.countOf(P, "villager")
	play(live, 600, log, [
		{"t": 137, "c": {"t": "train", "team": P, "buildingId": tc.id, "unit": "villager"}},
		{"t": 300, "c": {"t": "train", "team": P, "buildingId": tc.id, "unit": "villager"}},
	])
	eq(log.entries[0].t, 137, "stamped with the wrong tick")
	check(live.countOf(P, "villager") > before, "the orders never took effect")
	var expected := Commands.worldChecksum(live)
	eq(Savegame.replayTo(match(13), [], log.entries, 600).actual, expected)
	check(Savegame.replayTo(match(13), [], [], 600).actual != expected, "the replay ignored the order log")
	# A log survives being restored, as a resumed save does.
	var again := Savegame.CommandLog.new()
	again.load(log.entries)
	eq(again.entries.size(), 2)


func test_saves_are_stored_newest_first_and_bounded() -> void:
	before()
	for i in Savegame.SAVE_LIMIT + 2:
		Savegame.writeSave({"version": Savegame.SAVE_FORMAT_VERSION, "savedAt": 1000 + i, "label": "s%d" % i, "setup": {},
			"tick": i, "commands": [], "checksum": 0, "summary": {"mapName": "m", "mode": "conquest", "players": 2, "difficulty": "knight", "elapsed": 0}})
	var saves := Savegame.listSaves()
	eq(saves.size(), Savegame.SAVE_LIMIT)
	eq(saves[0].label, "s%d" % (Savegame.SAVE_LIMIT + 1), "newest first")
	Savegame.deleteSave(saves[0].label)
	eq(Savegame.listSaves().size(), Savegame.SAVE_LIMIT - 1)
	# A save from another rules version is not offered.
	Storage.setItem(Savegame.STORAGE_KEY, JSON.stringify([{"version": 1, "savedAt": 1, "label": "old", "tick": 1, "commands": []}]))
	eq(Savegame.listSaves().size(), 0)


# ---- replay files ----

func record() -> Array:
	var w := SimTestUtil.build({"seed": 55, "commanders": ["", ""]})
	var log: Array = []
	for i in 20 * 30:
		if i % 100 == 0:
			var ids := w.entitiesOf(P, T.Kind.Unit).map(func(e): return e.id)
			var c := {"t": "move", "team": P, "ids": ids, "x": 600 + (i % 700), "y": 700, "queue": false, "attackMove": false}
			log.append({"t": w.tickCount, "c": c})
			Commands.applyCommand(w, c)
		w.tick()
	var rec := {
		"version": 1, "id": "r1", "savedAt": JS.now_ms(), "kind": "skirmish", "setup": {"seed": 55, "presetId": "open_plains"},
		"names": ["Aldric", "Azure · Knight"], "pov": 0, "commands": log, "endTick": w.tickCount,
		"summary": {"map": "Open Plains", "players": 2, "durationSec": 30, "result": "Unfinished", "factions": ["kingdom", "kingdom"]},
		"sim": Savegame.SAVE_FORMAT_VERSION,
	}
	return [rec, Commands.worldChecksum(w)]


func test_replay_files_survive_the_trip_and_play_out_identically() -> void:
	var r := record()
	var rec: Dictionary = r[0]
	var file := Replay.replayFile(rec)
	check(file.name.ends_with(Replay.REPLAY_FILE_EXT))
	check(file.name.contains("Open-Plains"), file.name)
	check(file.text.length() < 20000, "stays small")
	var back := Replay.parseReplayFile(file.text)
	eq(back.ok, true)
	check(not back.has("warning"))
	eq(back.replay.imported, true)
	check(back.replay.id != rec.id)
	var w := SimTestUtil.build({"seed": 55, "commanders": ["", ""]})
	var due := Replay.byTick(back.replay.commands)
	while w.tickCount < int(back.replay.endTick):
		for c in due.get(w.tickCount, []):
			Commands.applyCommand(w, c)
		w.tick()
	eq(Commands.worldChecksum(w), r[1])


func test_replay_files_warn_about_another_version() -> void:
	var rec: Dictionary = record()[0]
	rec.sim = Savegame.SAVE_FORMAT_VERSION + 1
	var res := Replay.parseReplayFile(Replay.replayFile(rec).text)
	check(res.ok and str(res.get("warning", "")).contains("different version"))


func test_replay_files_refuse_what_isnt_a_replay() -> void:
	eq(Replay.parseReplayFile("not json").ok, false)
	eq(Replay.parseReplayFile(JSON.stringify({"hello": "world"})).ok, false)
	var good = JSON.parse_string(Replay.replayFile(record()[0]).text)
	var tamper := func(f: Callable) -> Dictionary:
		var b = good.duplicate(true)
		f.call(b)
		return Replay.parseReplayFile(JSON.stringify(b))
	eq(tamper.call(func(b): b.replay.kind = "hack").ok, false)
	eq(tamper.call(func(b): b.replay.endTick = -5).ok, false)
	eq(tamper.call(func(b): b.replay.commands = [{"t": "x", "c": {}}]).ok, false)
	eq(tamper.call(func(b): b.replay.erase("setup")).ok, false)
	eq(tamper.call(func(b): b.replay.kind = "online").ok, false, "online replays need their network setup")
	var long: Dictionary = tamper.call(func(b): b.replay.names = ["x".repeat(500)])
	eq(long.ok and long.replay.names[0].length() == 32, true)


func test_replays_are_kept_newest_first() -> void:
	before()
	for i in Replay.REPLAY_LIMIT + 3:
		Replay.saveReplay({"version": Replay.REPLAY_VERSION, "id": "r%d" % i, "savedAt": 100 + i, "commands": []})
	var all := Replay.listReplays()
	eq(all.size(), Replay.REPLAY_LIMIT)
	eq(all[0].id, "r%d" % (Replay.REPLAY_LIMIT + 2))
	Replay.deleteReplay(all[0].id)
	eq(Replay.listReplays().size(), Replay.REPLAY_LIMIT - 1)
	check(Replay.replayId().begins_with("r"))


# ---- warband terrain and conditions: the same numbers as the TS build ----

func rollKey(seed: int, round: int) -> String:
	return "|".join(Terrain.rollTerrain(seed, round).map(func(f): return "%s%d,%d" % [f.kind, f.col, f.row]))


func test_board_terrain_rolls_exactly_as_the_ts_build() -> void:
	eq(rollKey(1, 6), "mire0,5|mire9,5|rise3,2|rise6,2|mire2,4|mire7,4")
	eq(rollKey(7, 6), "mire0,9|mire9,9|rise2,4|rise7,4")
	eq(rollKey(42, 6), "mire1,8|mire8,8|boulder0,5|boulder9,5|rise2,8|rise7,8")
	eq(rollKey(99, 6), "mire3,5|mire6,5|boulder0,8|boulder9,8")
	eq(rollKey(12345, 6), "mire1,4|mire8,4|boulder2,3|boulder7,3|mire3,9|mire6,9|mire1,9|mire8,9")
	eq(Terrain.rollTerrain(42, 1), [])


func test_board_terrain_is_mirrored_and_keeps_the_front_rank_clear() -> void:
	for seed in [1, 7, 99, 12345]:
		var feats := Terrain.rollTerrain(seed, 6)
		check(feats.size() > 0)
		for f in feats:
			var twin = null
			for t in feats:
				if t.col == Terrain.MIRROR_COL(f.col) and t.row == f.row:
					twin = t
			check(twin != null and twin.kind == f.kind, "%s at %d,%d has no mirror" % [f.kind, f.col, f.row])
			check(f.col != 4 and f.col != 5, "front rank")
	eq(Terrain.isBlocked([{"kind": "boulder", "col": 3, "row": 3}], 3, 3), true)
	eq(Terrain.terrainAt([{"kind": "mire", "col": 3, "row": 3}], 3, 3).kind, "mire")
	check(Terrain.TERRAIN.rise.rangePct > 0 and Terrain.TERRAIN.mire.speedPct < 0)


func test_terrain_codes_round_trip_like_the_ts_build() -> void:
	eq([1, 999, 123456, 999999999, 4294967295, -1].map(func(s): return Terrain.terrainCode(s)), ["000001", "0000RR", "002N9C", "GJDGXR", "Z141Z3", "Z141Z3"])
	eq(["00007F", "abc", "ZZZZZZ"].map(func(s): return Terrain.parseTerrainCode(s)), [267, 13368, 2176782335])
	eq(Terrain.parseTerrainCode("not a code!"), null)


func test_conditions_roll_and_read_exactly_as_the_ts_build() -> void:
	var rng := RNG.new(2024)
	var ids: Array = []
	for i in 12:
		ids.append(Conditions.rollCondition(rng, i).id)
	eq(ids, ["clear", "clear", "dusk", "gale", "frost", "gale", "mud", "frost", "rain", "clear", "mud", "gale"])
	var lines := Conditions.CONDITIONS.map(func(c): return Conditions.conditionLines(c, func(id): return id.to_upper()))
	eq(lines, [[], ["MARKSMEN: -18% attack"], ["Everyone: -10% speed", "RIDERS: -25% speed"], ["Everyone: -8% HP", "ENGINES: -15% attack"],
		["Everyone: +12% speed", "MARKSMEN: -10% attack"], ["MARKSMEN: -22% attack", "FOOTMEN: +10% HP"], ["RIDERS: +8% attack, +18% speed"],
		["ENGINES: +25% attack", "MARKSMEN: +10% attack"]])
	eq(Conditions.hasEffect(Conditions.CLEAR), false)
	eq(Conditions.conditionById("mud").name, "Deep Mud")


# ---- content function ports ----

func test_day_night_and_content_helpers() -> void:
	eq(DayNight.dayPhase(250), 0.25)
	eq(DayNight.visionMult(0.0), 0.85)
	eq(DayNight.visionMult(0.75), 0.6)
	near(DayNight.visionMult(0.11), 0.925, 1e-12)
	eq(DayNight.isNight(0.7), true)
	eq(DayNight.dayLabel(0.3), "Day")
	eq(Tech.ageShort(2), "Crown")
	eq(Tech.ageShort(9), "—")
	eq(BattleStyles.styleOf("nonsense"), "line")
	eq(Commanders.commanderPerks(C.COMMANDERS.magnate), ["Start +150 food, 150 wood, 100 gold"])
	eq(Commanders.commanderPerks(C.COMMANDERS.banneret), ["Start +50 food", "Power: Rally Banner"])
	eq(Oaths.lines("hearth", 1.5)[0], "Buildings have 45% more HP and mend when left unharmed")
	eq(Boons.detail("field_medic", 2), "1.1 HP/sec out of combat")
	eq(Boons.detail("pavise", 4), "+3 armor to all units")
	var e := Boons.aggregateBoons([{"id": "whetstones", "rarity": 5}, {"id": "unknown", "rarity": 1}])
	near(e.atkMultInfantry, 1.24, 1e-12)
	eq(Factions.factionOf("nope").id, "kingdom")
	eq(Factions.factionForUnit("samurai").id, "shogunate")
	eq(Factions.factionForUnit("militia"), null)
