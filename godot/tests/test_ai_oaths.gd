extends TestCase
# ai/oaths_ai.test.ts — the AI and the late game.
#
# Measured before any of this: across nine AI-vs-AI duels, not one AI reached
# the third age in thirty minutes — most sat in the second with a thousand
# gold banked and no food. Three things were wrong, and none was about Oaths:
# villagers walked to the nearest berry bush on the whole map before trying a
# farm; several were sent to the same farm, which only one can work; and a
# Town Centre hemmed in by its own buildings could leave its nearest open cell
# sealed off, so food-carriers heading home stood still for the rest of the
# match. With those fixed, the same nine seeds reach the Empire Age.


## Two Lords on `preset`; `onMinute(w, m)` after each minute; `until(w)`
## (optional) ends the match early.
func play(preset: String, seed: int, minutes: int, onMinute = null, until = null) -> World:
	var w := World.new(seed)
	w.init(AITestUtil.mapOf(preset, seed, 2), [{}, {}], [1.0, 1.0])
	var ais := [
		SkirmishAI.new(w, 0, C.DIFFICULTIES.lord),
		SkirmishAI.new(w, 1, C.DIFFICULTIES.lord),
	]
	var hz := int(C.SIM_HZ)
	for i in range(1, hz * 60 * minutes + 1):
		w.tick()
		for a: SkirmishAI in ais:
			a.update(C.SIM_DT)
		w.drainEvents()
		if i % (hz * 60) == 0 and onMinute != null:
			onMinute.call(w, i / (hz * 60))
		if w.winner != null:
			break
		if until != null and until.call(w):
			break
	return w


func test_never_lets_food_income_stall_while_it_is_still_standing() -> void:
	# Highlands seed 6: before the fixes, one side gathered no food at all from
	# minute seven to minute nineteen.
	var food := [[], []]
	var w := play("highlands", 6, 16, func(ww: World, _m: int) -> void:
		for t in [0, 1]:
			food[t].append(ww.player(t).stats.gatheredBy.food))
	for t in [0, 1]:
		if w.player(t).defeated:
			continue
		var m := 6
		while m + 3 < food[t].size():
			var got: float = food[t][m + 3] - food[t][m]
			check(got > 250, "team %d gathered %s food between minutes %d and %d" % [t, got, m + 1, m + 4])
			m += 1


func test_swears_a_real_oath_at_every_advance_and_reaches_the_empire_age() -> void:
	# The TS plays twenty-four minutes and checks at the end. Every check below
	# already holds the moment the first side reaches the Empire Age (minute
	# nineteen in the TS build: the Oaths sworn match the ages, and each martial
	# Oath's unit has been fielded), so the match stops there.
	var w := play("riverlands", 8, 24, null, func(ww: World) -> bool:
		return maxi(ww.player(0).age, ww.player(1).age) == 3)
	var ages := [w.player(0).age, w.player(1).age]
	eq(maxi(ages[0], ages[1]), 3, "neither AI reached the Empire Age")
	for t in [0, 1]:
		var p := w.player(t)
		eq(p.oaths.size(), p.age, "team %d advanced without swearing" % t)
		for i in p.oaths.size():
			var o: String = p.oaths[i]
			var od = C.OATHS.get(o)
			eq(od.age if od != null else null, i + 1, "%s sworn at the wrong age" % o)
		# A martial Oath's unit actually gets fielded.
		for o: String in p.oaths:
			var unit = C.OATHS[o].get("unit")
			if unit == null or str(unit) == "" or C.UNITS[unit].age > 2:
				continue
			check(float(p.stats.trainedByType.get(unit, 0.0)) > 0, "swore %s and never trained a %s" % [o, unit])
