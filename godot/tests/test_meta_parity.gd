extends TestCase
# The meta modules against the TypeScript build: tests/fixtures/meta.json holds
# what the TS progression, chests, boon caches, achievements, history, career,
# playstyle, feedback and rival code returned for a few hundred generated
# inputs (game/scripts/godot_meta_fixtures.ts). The port must return the same
# values and the same sentences.

const H := preload("res://tests/meta_helpers.gd")

static var _fx = null


func _init() -> void:
	Storage.use_memory_only()
	Career.storage_override = null
	History.storage_override = null
	Rival.storage_override = null


func fx() -> Dictionary:
	if _fx == null:
		_fx = fixture("meta.json")
	return _fx


## First difference between what the port returned and what TS returned, or "".
## Numbers agree to 1e-9 relative (Math.log/exp and the two JSON parsers may
## disagree in the last bit); strings, booleans, nulls and keys exactly.
static func diff(got, want, path := "") -> String:
	if (got is float or got is int) and (want is float or want is int):
		var a := float(got)
		var b := float(want)
		if a == b or absf(a - b) <= 1e-9 * maxf(1.0, maxf(absf(a), absf(b))):
			return ""
		return "%s: got %s, want %s" % [path, str(got), str(want)]
	if got is Array and want is Array:
		if got.size() != want.size():
			return "%s: got %d items, want %d (%s vs %s)" % [path, got.size(), want.size(), str(got).left(200), str(want).left(200)]
		for i in got.size():
			var d := diff(got[i], want[i], "%s[%d]" % [path, i])
			if d != "":
				return d
		return ""
	if got is Dictionary and want is Dictionary:
		for k in want:
			if not got.has(k):
				return "%s.%s: missing (want %s)" % [path, k, str(want[k]).left(120)]
		for k in got:
			if not want.has(k):
				return "%s.%s: unexpected (got %s)" % [path, k, str(got[k]).left(120)]
			var d := diff(got[k], want[k], "%s.%s" % [path, k])
			if d != "":
				return d
		return ""
	if typeof(got) == typeof(want) and got == want:
		return ""
	return "%s: got %s, want %s" % [path, str(got).left(300), str(want).left(300)]


func same(got, want, what: String) -> bool:
	var d := diff(got, want, what)
	if d != "":
		failures.append(d)
		return false
	return true


func test_progression() -> void:
	var p: Dictionary = fx().progression
	for i in p.xpForLevel.size():
		same(Progression.xpForLevel(i + 1), p.xpForLevel[i], "xpForLevel(%d)" % (i + 1))
	for i in p.totalXpForLevel.size():
		same(Progression.totalXpForLevel(i + 1), p.totalXpForLevel[i], "totalXpForLevel(%d)" % (i + 1))
	for c in p.levelFromXp:
		same(Progression.levelFromXp(c.xp), c.out, "levelFromXp(%s)" % str(c.xp))
	for c in p.computeRewards:
		same(Progression.computeRewards(c.o), c.out, "computeRewards(%s)" % str(c.o))


func test_chest_rolls() -> void:
	for run in fx().chests:
		var rng := RNG.new(int(run.seed))
		var owned := {}
		for i in run.rolls.size():
			var res := Chests.rollChest(C.CHESTS[i % 3], owned, rng)
			owned[res.variant.key] = true
			if not same({"key": res.variant.key, "rarity": res.rarity, "duplicate": res.duplicate, "refund": res.refund}, run.rolls[i], "chest seed %s roll %d" % [str(run.seed), i]):
				return


func test_boon_cache_rolls() -> void:
	for run in fx().boonCache:
		var rng := RNG.new(int(run.seed))
		var owned := {}
		for i in run.rolls.size():
			var res := BoonCache.rollBoonCache(owned, rng)
			owned[res.key] = true
			if not same(res, run.rolls[i], "boon cache seed %s roll %d" % [str(run.seed), i]):
				return


func test_achievements_and_challenges() -> void:
	var a: Dictionary = fx().achievements
	for w in a.weeks.size():
		same(Achievements.challengesForWeek(w).map(func(c): return c.id), a.weeks[w], "challengesForWeek(%d)" % w)
	for i in a.awards.size():
		var c: Dictionary = a.awards[i]
		same(Achievements.evaluateAwards(c.m, c.state), c.out, "evaluateAwards #%d" % i)


func test_history_summaries() -> void:
	var h: Dictionary = fx().history
	# The same rows the generator summarised: slices of the achievements' records.
	var records: Array = fx().achievements.awards.map(func(c): return c.m)
	var slices := [records.slice(0, 1), records.slice(0, 7), records.slice(10, 30), records]
	for i in slices.size():
		same(History.summariseHistory(slices[i]), h.summaries[i].out, "summariseHistory #%d" % i)
	for i in h.summarise.size():
		var c: Dictionary = h.summarise[i]
		var out := History.summarise(c.report, c.meta)
		out.erase("report")
		same(out, c.out, "summarise #%d" % i)


func test_career_folds_and_readings() -> void:
	var c: Dictionary = fx().career
	for i in c.careers.size():
		var ms: Array = c.careers[i]
		var car := Career.fold(ms)
		var want: Dictionary = c.folds[i]
		same(car, want.career, "fold #%d" % i)
		same({
			"winRate": Career.winRate(car.all), "kd": Career.kd(car.all), "favFaction": Career.favourite(car.byFaction), "favMap": Career.favourite(car.byMap),
			"favUnit": Career.favouriteUnitOf(car.all), "topTrained": Career.topUnit(car, "trained"), "topKilled": Career.topUnit(car, "killed", false),
			"topKills": Career.topUnit(car, "kills"), "effective": Career.mostEffectiveUnit(car, 5), "bestFaction": Career.bestBy(car.byFaction, 2),
			"worstFaction": Career.worstBy(car.byFaction, 2), "worstFoe": Career.worstBy(car.vsFaction, 1),
		}, want.read, "career readings #%d" % i)


func test_career_match_from_reports() -> void:
	var c: Dictionary = fx().career
	for i in c.matches.size():
		var x: Dictionary = c.matches[i]
		var cm := Career.careerMatch(x.report, x.meta)
		same(cm, x.out, "careerMatch #%d" % i)
		same(Career.absorb(Career.emptyCareer(), cm), x.absorbed, "absorb(careerMatch #%d)" % i)


static func profile_view(p: Dictionary) -> Dictionary:
	var v := p.duplicate()
	v.primary = {"id": p.primary.archetype.id, "score": p.primary.score} if p.primary != null else null
	v.secondary = {"id": p.secondary.archetype.id, "score": p.secondary.score} if p.secondary != null else null
	v.scores = p.scores.map(func(s): return {"id": s.archetype.id, "score": s.score})
	return v


func test_playstyle_profiles() -> void:
	var ps: Dictionary = fx().playstyle
	var careers: Array = fx().career.careers
	for i in careers.size():
		same(profile_view(Playstyle.analyse(careers[i])), ps.profiles[i], "analyse(career #%d)" % i)


func test_playstyle_match_styles_archetypes_and_openers() -> void:
	var ps: Dictionary = fx().playstyle
	var careers: Array = fx().career.careers
	var all: Array = []
	for ms in careers:
		all.append_array(ms)
	for i in all.size():
		same(Playstyle.matchStyles(all[i]), ps.styles[i], "matchStyles #%d" % i)
	var withGames := careers.filter(func(ms): return ms.size() > 0).slice(0, 12)
	for i in withGames.size():
		var f := Playstyle.features(withGames[i])
		var got := Playstyle.ARCHETYPES.map(func(a): return {
			"id": a.id, "score": a.score.call(f), "title": a.title.call(f) if a.title != null else null,
			"describe": a.describe.call(f), "evidence": a.evidence.call(f),
		})
		same(got, ps.archetypes[i], "archetypes(career #%d)" % i)
	for i in ps.openers.size():
		same(Playstyle.openerWord(ps.openers[i].o), ps.openers[i].out, "openerWord #%d" % i)


func test_match_feedback() -> void:
	var fb: Array = fx().feedback
	for i in fb.size():
		same(Feedback.matchFeedback(fb[i].r), fb[i].out, "matchFeedback #%d" % i)


func test_rival_runs() -> void:
	const NOW := 1_700_000_000_000.0
	for run in fx().rival:
		var faction: String = run.faction
		var start: Dictionary = run.start
		# A new rival is the same rival in both builds.
		same(Rival.newRival(1, faction, "Has heard of you, and wants your name for its own", 1, NOW), start.current, "newRival(%s)" % faction)
		var st: Dictionary = start.duplicate(true)
		var played: Array = []
		for i in run.steps.size():
			var step: Dictionary = run.steps[i]
			played.append(step.m)
			var mem := Rival.memoryOf(st.current, played)
			var plan := {"mem": mem, "adapt": Rival.adaptationFor(st.current, mem), "taunt": Rival.taunt(st.current, mem)}
			if not same(plan, step.plan, "rival %s step %d plan" % [faction, i]):
				return
			var out := Rival.settleRival(st, step.m, faction, NOW + i * 1000)
			if not same(out, step.out, "rival %s step %d outcome" % [faction, i]):
				return
			if not same(st, step.after, "rival %s step %d state" % [faction, i]):
				return


## The archetype descriptions print numbers with toFixed, which is what showed
## JS.to_fixed rounding x * 10^d in floating point instead of the exact value.
## Expected strings from V8.
func test_js_to_fixed_matches_v8() -> void:
	for c in [[20.15, 0, "20"], [20.15, 1, "20.1"], [20.15, 2, "20.15"], [20.15, 3, "20.150"], [1.505, 0, "2"], [1.505, 1, "1.5"], [1.505, 2, "1.50"], [1.505, 3, "1.505"], [1.005, 0, "1"], [1.005, 1, "1.0"], [1.005, 2, "1.00"], [1.005, 3, "1.005"], [2.675, 0, "3"], [2.675, 1, "2.7"], [2.675, 2, "2.67"], [2.675, 3, "2.675"], [0.125, 0, "0"], [0.125, 1, "0.1"], [0.125, 2, "0.13"], [0.125, 3, "0.125"], [2.5, 0, "3"], [2.5, 1, "2.5"], [2.5, 2, "2.50"], [2.5, 3, "2.500"], [-2.5, 0, "-3"], [-2.5, 1, "-2.5"], [-2.5, 2, "-2.50"], [-2.5, 3, "-2.500"], [0.5, 0, "1"], [0.5, 1, "0.5"], [0.5, 2, "0.50"], [0.5, 3, "0.500"], [-0.0001, 0, "-0"], [-0.0001, 1, "-0.0"], [-0.0001, 2, "-0.00"], [-0.0001, 3, "-0.000"], [1.45, 0, "1"], [1.45, 1, "1.4"], [1.45, 2, "1.45"], [1.45, 3, "1.450"], [8.345, 0, "8"], [8.345, 1, "8.3"], [8.345, 2, "8.35"], [8.345, 3, "8.345"], [1234.5678, 0, "1235"], [1234.5678, 1, "1234.6"], [1234.5678, 2, "1234.57"], [1234.5678, 3, "1234.568"], [0, 0, "0"], [0, 1, "0.0"], [0, 2, "0.00"], [0, 3, "0.000"], [0.05, 0, "0"], [0.05, 1, "0.1"], [0.05, 2, "0.05"], [0.05, 3, "0.050"], [0.15, 0, "0"], [0.15, 1, "0.1"], [0.15, 2, "0.15"], [0.15, 3, "0.150"], [0.25, 0, "0"], [0.25, 1, "0.3"], [0.25, 2, "0.25"], [0.25, 3, "0.250"], [0.35, 0, "0"], [0.35, 1, "0.3"], [0.35, 2, "0.35"], [0.35, 3, "0.350"], [1e-7, 0, "0"], [1e-7, 1, "0.0"], [1e-7, 2, "0.00"], [1e-7, 3, "0.000"], [123456789.125, 0, "123456789"], [123456789.125, 1, "123456789.1"], [123456789.125, 2, "123456789.13"], [123456789.125, 3, "123456789.125"], [3.4, 0, "3"], [3.4, 1, "3.4"], [3.4, 2, "3.40"], [3.4, 3, "3.400"], [1.06, 0, "1"], [1.06, 1, "1.1"], [1.06, 2, "1.06"], [1.06, 3, "1.060"], [1.505, 0, "2"], [1.505, 1, "1.5"], [1.505, 2, "1.50"], [1.505, 3, "1.505"], [99.995, 0, "100"], [99.995, 1, "100.0"], [99.995, 2, "100.00"], [99.995, 3, "99.995"], [0.045, 0, "0"], [0.045, 1, "0.0"], [0.045, 2, "0.04"], [0.045, 3, "0.045"]]:
		eq(JS.to_fixed(c[0], c[1]), c[2], "(%s).toFixed(%d)" % [str(c[0]), c[1]])
