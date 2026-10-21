extends TestCase
# Ported from game/src/sim/warband.test.ts: the statistical checks that the
# lobby difficulty and the final challenge are tuned — each plays dozens of
# whole runs. They are deterministic (and every one of those runs is a run the
# parity test proves matches the TS build), so the three longest only run when
# asked for, with WARBAND_SLOW=1 in the environment:
#
#   WARBAND_SLOW=1 godot/tests/run.sh test_warband_balance

func slow() -> bool:
	return OS.get_environment("WARBAND_SLOW") == "1"


func mean(xs: Array) -> float:
	var s := 0.0
	for x in xs:
		s += x
	return s / xs.size()


## Play a whole run with a fixed level of care, and report the placement.
func playRun(seed: int, skill: String, diff: String = "") -> int:
	var run := Warband.WarbandRun.new(seed, null)
	if diff != "":
		run.difficulty = WarbandDifficulty.difficultyById(diff)
	var guard := 0
	while run.phase != "over" and guard < 400:
		guard += 1
		if run.phase == "augment":
			run.pickAugment(0)
			continue
		if run.phase == "draft":
			run.takeCarousel(0)
			continue
		if run.phase == "shop":
			if skill == "average":
				if run.gold >= 12:
					run.buyXp()
				run.buy(0)
				run.buy(1)
			if skill == "good":
				while run.gold >= 20 and run.level < 9:
					run.buyXp()
				for s in 5:
					run.buy(s)
				if run.gold > 30:
					run.reroll()
					for s in 5:
						run.buy(s)
			run.fight()
		elif run.phase == "result":
			run.next()
	return run.placement()


func test_punishes_doing_nothing_and_rewards_playing_well() -> void:
	if not slow():
		return
	var seeds := [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
	var idle: Array = seeds.map(func(s): return playRun(s, "idle"))
	var average: Array = seeds.map(func(s): return playRun(s, "average"))
	var good: Array = seeds.map(func(s): return playRun(s, "good"))
	check(mean(good) < mean(average), "good %s vs average %s" % [mean(good), mean(average)])
	check(mean(average) < mean(idle))
	eq(idle.max(), 8)
	eq(idle.count(1), 0)
	check(mean(average) > 3)
	check(average.count(1) <= 4)
	check(mean(good) < 3)
	check(good.count(1) > 1)


func test_thins_the_lobby_through_lost_fights() -> void:
	var run := Warband.WarbandRun.new(77, null)
	var guard := 0
	while run.phase != "over" and guard < 400:
		guard += 1
		if run.phase == "augment":
			run.pickAugment(0)
			continue
		if run.phase == "draft":
			run.takeCarousel(0)
			continue
		if run.phase == "shop":
			while run.gold >= 20 and run.level < 9:
				run.buyXp()
			for s in 5:
				run.buy(s)
			run.fight()
		elif run.phase == "result":
			run.next()
	eq(run.phase, "over")
	var dead := run.opponents.filter(func(o): return not o.alive)
	check(dead.size() > 0)
	for o in dead:
		eq(o.life, 0)
	check(run.standings()[0].alive)


## How strong the whole lobby's boards are, star-weighted.
func lobbyPower(run: Warband.WarbandRun) -> float:
	var n := 0.0
	for id in 7:
		var s = run.scout(id)
		if s == null:
			continue
		n += s.level
		for u in s.board:
			n += pow(2, int(u.get("star", 1)) - 1)
	return n


func runTo(diff: String, round: int, seed: int) -> Warband.WarbandRun:
	var run := Warband.WarbandRun.new(seed, null)
	run.difficulty = WarbandDifficulty.difficultyById(diff)
	while run.round < round:
		if run.phase == "augment":
			run.pickAugment(0)
		if run.phase == "draft":
			run.takeCarousel(0)
		if run.phase == "shop":
			run.fight()
		if run.phase == "result":
			run.next()
		else:
			break
	return run


func test_scales_how_hard_the_lobby_plays() -> void:
	var squire := 0.0
	var conqueror := 0.0
	for seed in [11, 23, 37, 51, 67]:
		squire += lobbyPower(runTo("squire", 14, seed))
		conqueror += lobbyPower(runTo("conqueror", 14, seed))
	check(conqueror > squire, "conqueror %s vs squire %s" % [conqueror, squire])


func test_squire_is_forgiving_but_not_a_formality() -> void:
	if not slow():
		return
	var seeds := [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
	var avgSquire := mean(seeds.map(func(s): return playRun(s, "average", "squire")))
	var avgVeteran := mean(seeds.map(func(s): return playRun(s, "average", "veteran")))
	check(avgSquire < avgVeteran, "squire %s vs veteran %s" % [avgSquire, avgVeteran])
	var goodWins: int = seeds.map(func(s): return playRun(s, "good", "squire")).count(1)
	check(goodWins < 8, "good player took %d/%d Squire runs" % [goodWins, seeds.size()])


func bossPlayOut(seed: int) -> Warband.WarbandRun:
	var run := Warband.WarbandRun.new(seed, null)
	var guard := 0
	while run.phase != "over" and guard < 90:
		guard += 1
		if run.phase == "augment":
			run.pickAugment(0)
		if run.phase == "draft":
			run.takeCarousel(0)
		if run.phase == "shop":
			for k in 5:
				run.buy(k)
			if run.gold >= 8:
				run.buyXp()
			run.fight()
		if run.phase == "result":
			run.next()
	return run


func test_sends_the_crown_out_and_ends_the_run() -> void:
	if not slow():
		return
	var sawBoss := false
	for seed in [37, 74, 111, 148, 185, 222, 259, 296, 333, 370, 407, 444]:
		var run := bossPlayOut(seed)
		eq(run.phase, "over")
		if not run.bossRound:
			continue
		sawBoss = true
		eq(run.pendingCamp.id, "crown")
		if run.life > 0:
			eq(run.outcome, "win")
		check(run.bossCleared is bool)
		eq(run.history[run.history.size() - 1].foe, "The Iron Crown")
	check(sawBoss)


func test_the_crown_is_a_real_fight() -> void:
	if not slow():
		return
	var reached := 0
	var cleared := 0
	for s in range(1, 45):
		var run := bossPlayOut(s * 37)
		if not run.bossRound:
			continue
		reached += 1
		if run.bossCleared:
			cleared += 1
	check(reached > 4)
	check(cleared > 0)
	check(cleared < reached)
