extends TestCase
# ai/personalities.test.ts — difficulty tiers, focus-fire priority, population
# growth, settling, camps and farms, offence and research.


func duel(seed: int, preset: String = "open_plains") -> World:
	var w := World.new(seed)
	w.init(AITestUtil.mapOf(preset, seed, 2), [{}, {}], [1.0, 1.0])
	return w


func step(w: World, ais: Array) -> void:
	w.tick()
	for ai: SkirmishAI in ais:
		ai.update(C.SIM_DT)
	w.drainEvents()


func countType(blds: Array, t: String) -> int:
	var n := 0
	for e: Entity in blds:
		if e.type == t:
			n += 1
	return n


# ------------------------------------------------------------ Difficulty tiers --

func test_has_five_tiers_all_fully_specified_conqueror_the_hardest() -> void:
	eq(C.DIFFICULTY_IDS, ["squire", "knight", "lord", "warlord", "conqueror"], "ids")
	for id in C.DIFFICULTY_IDS:
		var d = C.DIFFICULTIES.get(id)
		check(d != null, "%s missing" % id)
		check(d != null and typeof(d.focusFire) == TYPE_BOOL, "%s.focusFire is not a boolean" % id)
	var c: Dictionary = C.DIFFICULTIES.conqueror
	var w: Dictionary = C.DIFFICULTIES.warlord
	# Hardest by the metrics that matter: economy, reaction speed, no mistakes.
	check(c.econMult >= w.econMult, "econMult")
	check(c.reactionSec <= w.reactionSec, "reactionSec")
	eq(c.executionError, 0, "executionError")
	eq(c.focusFire, true, "conqueror focusFire")
	# Only the sharper tiers micro their army.
	eq(C.DIFFICULTIES.squire.focusFire, false, "squire focusFire")
	eq(C.DIFFICULTIES.knight.focusFire, false, "knight focusFire")
	eq(C.DIFFICULTIES.lord.focusFire, true, "lord focusFire")


# ------------------------------------------------------ Focus-fire target priority --

func test_kills_siege_then_champion_monk_ranged_cavalry_infantry() -> void:
	var siege := SkirmishAI.armyTargetPriority("catapult")
	var hero := SkirmishAI.armyTargetPriority("hero")
	var monk := SkirmishAI.armyTargetPriority("monk")
	var ranged := SkirmishAI.armyTargetPriority("archer")
	var cav := SkirmishAI.armyTargetPriority("knight")
	var inf := SkirmishAI.armyTargetPriority("militia")
	check(siege > hero, "siege > hero")
	check(hero > monk, "hero > monk")
	check(monk > ranged, "monk > ranged")
	check(ranged > inf, "ranged > infantry")
	check(siege > cav, "siege > cavalry")
	# Unknown type falls back to a low value.
	check(SkirmishAI.armyTargetPriority("nonsense") < siege, "unknown type")


# --------------------------------------------------------- AI population growth --

func test_a_house_provides_10_population() -> void:
	eq(C.BUILDINGS.house.popProvided, 10, "house popProvided")


func test_in_a_nomad_start_the_ai_settles_near_wood_gold_and_food() -> void:
	var w := World.new(3)
	w.init(AITestUtil.mapOf("open_plains", 3, 2), [{}, {}], [1.0, 1.0], [0, 1], null, true) # nomad: no starting TC
	var ai := SkirmishAI.new(w, T.Team.Enemy, C.DIFFICULTIES.lord)
	var tc: Entity = null
	var i := 0
	while i < int(C.SIM_HZ) * 120 and tc == null:
		step(w, [ai])
		for b: Entity in w.entitiesOf(T.Team.Enemy, T.Kind.Building):
			if b.type == "town_center":
				tc = b
				break
		i += 1
	check(tc != null, "never founded a Town Center")
	if tc == null:
		return
	var nearTiles := func(type: String) -> float:
		var m := INF
		for e: Entity in w.entities:
			if e.alive and e.kind == T.Kind.Resource and e.type == type and e.amount > 0:
				m = minf(m, JS.hypot(e.x - tc.x, e.y - tc.y))
		return m / C.TILE
	# It scouted a site within reach of all three core resources, not just where
	# the villagers happened to stand.
	check(nearTiles.call("tree") <= 14, "tree %s tiles away" % nearTiles.call("tree"))
	check(nearTiles.call("gold_mine") <= 14, "gold %s tiles away" % nearTiles.call("gold_mine"))
	check(nearTiles.call("berries") <= 14, "berries %s tiles away" % nearTiles.call("berries"))


func test_secures_resources_with_camps_and_transitions_to_a_farm_economy() -> void:
	# Both sides get a brain: against an empty seat the AI wins by minute six
	# and stops thinking, so the test would measure a finished game. The TS
	# plays thirteen minutes; the camps, the Mill and the fields are all down by
	# minute six in the TS build (4 lumber camps, a Mill, 6 farms) and still
	# there at thirteen, so the port asks at minute eight.
	var w := duel(42)
	var ai := SkirmishAI.new(w, T.Team.Enemy, C.DIFFICULTIES.warlord)
	var foe := SkirmishAI.new(w, T.Team.Player, C.DIFFICULTIES.warlord)
	for i in int(C.SIM_HZ) * 60 * 8:
		step(w, [ai, foe])
		if w.winner != null:
			break
	var blds := w.entitiesOf(T.Team.Enemy, T.Kind.Building)
	# Went out onto the map to secure wood/gold…
	check(countType(blds, "lumber_camp") >= 1, "lumber camps %d" % countType(blds, "lumber_camp"))
	check(countType(blds, "mill") >= 1, "mills %d" % countType(blds, "mill"))
	# …and transitioned onto farms for a sustainable late-game food supply.
	check(countType(blds, "farm") >= 3, "farms %d" % countType(blds, "farm"))


func test_does_not_stay_pop_blocked() -> void:
	var w := duel(99)
	var ai := SkirmishAI.new(w, T.Team.Enemy, C.DIFFICULTIES.knight)
	for i in int(C.SIM_HZ) * 180:
		step(w, [ai])
	var p := w.player(T.Team.Enemy)
	# Grew well past the starting cap of 10 (the bug: stuck at 10).
	check(p.popCap >= 30, "popCap %s" % p.popCap)
	# And isn't sitting jammed against the cap — it keeps headroom to produce.
	check(p.popCap - p.popUsed > 2, "headroom %s" % (p.popCap - p.popUsed))


# ----------------------------------------------------- Conqueror AI plays a real game --

func test_conqueror_mounts_an_offensive_and_runs_the_focus_fire_path() -> void:
	var seed := 1337
	var map := AITestUtil.mapOf("open_plains", seed, 2)
	var w := World.new(seed)
	w.init(map, [{}, {}], [1.0, 1.0])
	var hard := SkirmishAI.new(w, T.Team.Enemy, C.DIFFICULTIES.conqueror)
	# Stage a ready army so the wave (and its focus-fire micro) triggers promptly.
	var es: Dictionary = map.starts[T.Team.Enemy]
	for i in 20:
		w.spawnUnit(T.Team.Enemy, "militia", es.x + (i % 5) * 16 - 32, es.y + floori(i / 5.0) * 16)
	var enemyAttacked := false
	for i in int(C.SIM_HZ) * 150:
		step(w, [hard])
		if not enemyAttacked:
			for e: Entity in w.entitiesOf(T.Team.Enemy, T.Kind.Unit):
				if e.type != "villager" and (e.order.kind == T.OrderKind.Attack or e.order.kind == T.OrderKind.AttackMove):
					enemyAttacked = true
					break
		if w.winner != null:
			break
	check(enemyAttacked, "never launched a wave")
	check(not w.player(T.Team.Enemy).defeated, "the Conqueror was defeated")


# ------------------------------------------------------------------ AI research --

func test_researches_beyond_the_blacksmith_economy_techs_included() -> void:
	# Both seats get a brain: against an empty one the AI wins in minutes and
	# stops thinking. The TS plays fourteen minutes; its research list is
	# complete by minute eight (forging, bow_saw, loom, fletching, gold_mining in
	# the TS build) and unchanged at fourteen, so the port asks at minute nine.
	var w := duel(7)
	var ai := SkirmishAI.new(w, T.Team.Enemy, C.DIFFICULTIES.warlord)
	var foe := SkirmishAI.new(w, T.Team.Player, C.DIFFICULTIES.warlord)
	for i in int(C.SIM_HZ) * 60 * 9:
		step(w, [ai, foe])
		if w.winner != null:
			break
	var got: Array = w.player(T.Team.Enemy).upgrades.keys()
	check(got.size() > 0, "researched nothing")
	# It used to only ever look at the Blacksmith. At least one must now come
	# from somewhere else.
	var elsewhere := got.filter(func(id): return C.UPGRADES.has(id) and C.UPGRADES[id].researchedAt != "blacksmith")
	check(elsewhere.size() > 0, "only researched: %s" % ", ".join(got))
	# And it never researches something its age doesn't allow.
	for id in got:
		check(w.player(T.Team.Enemy).age >= C.UPGRADES[id].age, "%s above its age" % id)
