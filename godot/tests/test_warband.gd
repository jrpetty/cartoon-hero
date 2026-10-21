extends TestCase
# Ported from game/src/sim/warband.test.ts: the Warband Tactics run engine —
# shop, merges, levelling, fights, board placement, the shared pool, bench,
# opponents, augments, camps, commanders, the carousel, battlefield
# conditions, damage and streaks, scouting, synergy progress, run history and
# the ground seed. The long statistical balance checks (dozens of whole runs
# each) live in test_warband_balance.gd.

func run_(seed, commander = Warband.ASK) -> Warband.WarbandRun:
	return Warband.WarbandRun.new(seed, commander)


## Drive a run to the start of a given round, taking the first augment offered.
func advanceTo(run: Warband.WarbandRun, round: int) -> void:
	var guard := 0
	while run.round < round and run.phase != "over" and guard < 200:
		guard += 1
		if run.phase == "augment":
			run.pickAugment(0)
			continue
		if run.phase == "draft":
			run.takeCarousel(0)
			continue
		if run.phase == "shop":
			run.fight()
		if run.phase == "result":
			run.next()


func five(t) -> Array:
	return [t, t, t, t, t]


func count(arr: Array, pred: Callable) -> int:
	return arr.filter(pred).size()


func cellKeys(dep: Array) -> String:
	return "|".join(dep.map(func(d): return "%d,%d" % [d.col, d.row]))


func findDep(run: Warband.WarbandRun, index: int):
	for d in run.deployment():
		if d.index == index:
			return d
	return null


# ---- run engine ---------------------------------------------------------------

func test_starts_with_gold_a_shop_and_seven_opponents() -> void:
	var run := run_(123, null)
	check(run.gold > 0)
	eq(run.shop.size(), 5)
	eq(run.opponents.size(), 7)
	eq(run.phase, "shop")


func test_three_buys_merge_into_two_star_and_three_two_stars_into_three() -> void:
	var run := run_(1, null)
	run.gold = 99
	for i in 9:
		run.shop = five("militia")
		run.buy(0)
	eq(count(run.pieces, func(p): return p.star == 3), 1, "three-stars")
	eq(count(run.pieces, func(p): return p.star == 2), 0, "two-stars")
	eq(count(run.pieces, func(p): return p.star == 1), 0, "one-stars")


func test_buying_xp_raises_the_level() -> void:
	var run := run_(2, null)
	run.gold = 100
	var lvl0 := run.level
	for i in 5:
		run.buyXp()
	check(run.level > lvl0)


func test_higher_level_unlocks_higher_tier_units() -> void:
	var run := run_(5, null)
	run.gold = 1000000
	run.level = 1
	for i in 30:
		run.reroll()
		for t in run.shop:
			if t != null:
				eq(int(Warband.UNIT_TIER[t]), 1, "level-1 shop tier")
	run.level = 9
	var sawHigh := false
	var i := 0
	while i < 80 and not sawHigh:
		run.reroll()
		for t in run.shop:
			if t != null and int(Warband.UNIT_TIER[t]) >= 3:
				sawHigh = true
		i += 1
	check(sawHigh, "no tier 3+ at level 9")


func _lifeSum(run: Warband.WarbandRun) -> int:
	var s := 0
	for o in run.opponents:
		s += o.life
	return s


func test_a_fight_resolves_and_damages_someone() -> void:
	var run := run_(7, null)
	run.gold = 50
	run.shop = five("knight")
	run.buy(0)
	run.buy(1)
	run.buy(2)
	run.level = 3
	var myBefore := run.life
	var foeBefore := _lifeSum(run)
	run.fight()
	check(run.lastResult != null)
	check(run.life <= myBefore)
	check(_lifeSum(run) <= foeBefore)


func test_places_units_on_distinct_cells_and_moves_and_swaps() -> void:
	var run := run_(8, null)
	run.gold = 50
	run.level = 4
	run.shop = ["knight", "archer", "spearman", "militia", "horseman"]
	for i in 4:
		run.buy(i)
	var dep := run.deployment()
	eq(dep.size(), 4)
	var cells := {}
	for d in dep:
		cells["%d,%d" % [d.col, d.row]] = true
		check(d.col >= 0 and d.col <= 4)
	eq(cells.size(), 4)
	check(run.place(dep[0].index, 0, 9))
	var moved = findDep(run, dep[0].index)
	check(moved.col == 0 and moved.row == 9, "moved to 0,9")
	var a = findDep(run, dep[1].index)
	var target = findDep(run, dep[2].index)
	run.place(a.index, target.col, target.row)
	var after = findDep(run, a.index)
	check(after.col == target.col and after.row == target.row, "swapped onto the occupied cell")
	check(not run.place(dep[0].index, 5, 0), "col 5 is the enemy half")
	check(not run.place(dep[0].index, 0, 10))


func test_shared_pool_depletes_and_refunds() -> void:
	var run := run_(2, null)
	run.gold = 99
	var before := run.poolCount("militia")
	run.shop = five("militia")
	run.buy(0)
	run.buy(1)
	eq(run.poolCount("militia"), before - 2)
	var idx := -1
	for i in run.pieces.size():
		if run.pieces[i].type == "militia" and run.pieces[i].star == 1:
			idx = i
			break
	run.sell(idx)
	eq(run.poolCount("militia"), before - 1)


func test_fields_a_reserve_from_the_bench() -> void:
	var run := run_(4, null)
	run.gold = 99
	run.level = 2
	run.shop = ["knight", "archer", "spearman", "militia", "raider"]
	for i in 4:
		run.buy(i)
	eq(run.deployedCount(), 2)
	var deployed := {}
	for d in run.deployment():
		deployed[d.index] = true
	var benchIdx := -1
	for i in run.pieces.size():
		if not deployed.has(i):
			benchIdx = i
			break
	check(benchIdx >= 0)
	check(not run.pieces[benchIdx].deployed)
	check(run.place(benchIdx, 4, 0))
	check(run.pieces[benchIdx].deployed)
	eq(run.deployedCount(), 2)
	check(run.deployment().any(func(d): return d.index == benchIdx))


func test_opponents_grow_and_star_up() -> void:
	var run := run_(3, null)
	var sizes: Array = []
	var sawStarUp := false
	var sawRelic := false
	run.gold = 50
	run.shop = ["spearman", "spearman", "militia", "archer", "knight"]
	run.buy(0)
	run.buy(2)
	run.buy(3)
	var r := 0
	while r < 22 and run.phase != "over":
		r += 1
		if run.phase == "augment":
			run.pickAugment(0)
			continue
		if run.phase == "draft":
			run.takeCarousel(0)
			continue
		if not run.isCreepRound():
			sizes.append(run.pendingOpp.size())
			if run.pendingOpp.any(func(u): return int(u.get("star", 1)) >= 2):
				sawStarUp = true
			if run.pendingOpp.any(func(u): return u.get("items", []).size() > 0):
				sawRelic = true
		run.fight()
		if run.phase == "result":
			run.next()
	check(sizes[0] >= 1)
	check(sizes.max() > sizes[0])
	check(sawStarUp, "no star-up")
	check(sawRelic, "no relic")


func test_full_auto_played_run_terminates() -> void:
	var run := run_(99, null)
	var guard := 0
	while run.phase != "over" and guard < 200:
		guard += 1
		if run.phase == "augment":
			run.pickAugment(0)
		elif run.phase == "draft":
			run.takeCarousel(0)
		elif run.phase == "shop":
			if run.gold >= 8:
				run.buyXp()
			for s in run.shop.size():
				run.buy(s)
			run.fight()
		elif run.phase == "result":
			run.next()
	eq(run.phase, "over")
	check(run.outcome == "win" or run.outcome == "loss")
	check(run.standings()[0].alive)


# ---- augments -----------------------------------------------------------------

func test_offers_three_distinct_augments_and_pauses() -> void:
	var run := run_(11, null)
	advanceTo(run, int(C.AUGMENT_ROUNDS[0]))
	eq(run.round, int(C.AUGMENT_ROUNDS[0]))
	eq(run.phase, "augment")
	eq(run.augmentOffer.size(), 3)
	var ids := {}
	for a in run.augmentOffer:
		ids[a.id] = true
	eq(ids.size(), 3)
	run.gold = 50
	check(not run.reroll())
	check(not run.buyXp())
	var chosen = run.augmentOffer[0]
	check(run.pickAugment(0))
	eq(run.phase, "shop")
	eq(run.augments.map(func(a): return a.id), [chosen.id])
	eq(run.augmentOffer.size(), 0)


func test_never_offers_an_owned_augment() -> void:
	var rng := RNG.new(4)
	var owned: Array = C.AUGMENTS.filter(func(a): return a.tier == "silver").slice(0, 2).map(func(a): return a.id)
	for i in 40:
		var offer := Augments.offerAugments(rng, "silver", owned)
		eq(offer.size(), 3)
		for a in offer:
			check(not owned.has(a.id))


func test_board_slot_augments() -> void:
	var run := run_(12, null)
	run.gold = 200
	run.level = 3
	run.shop = ["knight", "archer", "spearman", "militia", "raider"]
	for i in 5:
		run.buy(i)
	eq(run.deployCount(), 3)
	run.augments.append(Augments.augmentById("legion"))
	eq(run.deployCount(), 5)
	eq(run.deployment().size(), 5)


func test_economy_augments() -> void:
	var run := run_(13, null)
	eq(run.rerollCost(), 2)
	run.augments.append(Augments.augmentById("quartermaster"))
	eq(run.rerollCost(), 1)
	run.gold = 5
	check(run.reroll())
	eq(run.gold, 4)
	var rich := run_(14, null)
	advanceTo(rich, int(C.AUGMENT_ROUNDS[0]))
	rich.augmentOffer = [Augments.augmentById("kingsransom")]
	var before := rich.gold
	rich.pickAugment(0)
	eq(rich.gold, before + 12)
	rich.gold = 100
	var at := rich.round
	rich.fight()
	if rich.phase == "result":
		rich.next()
	if rich.round > at:
		check(rich.gold >= 100 + 5 + 8)


func test_banners_add_virtual_counts_and_buffs_stack() -> void:
	var run := run_(15, null)
	run.gold = 200
	run.level = 2
	run.shop = five("militia")
	run.buy(0)
	check(not run.activeTraits().any(func(t): return t.trait.id == "footmen"))
	run.augments.append(Augments.augmentById("banner_footmen"))
	var foot = run.activeTraits().filter(func(t): return t.trait.id == "footmen")
	check(foot.size() == 1)
	eq(foot[0].count, 3)
	var buff = Augments.combinedBuff([Augments.augmentById("whetted"), Augments.augmentById("blademaster")])
	eq(buff.atkPct, 12 + 26)
	check(TestWarbandUtil.same(Augments.combinedTraitBonus([Augments.augmentById("banner_riders")]), {"riders": 2}))
	check(TestWarbandUtil.same(run.sideOpts().traitBonus, {"footmen": 2}))


func test_augment_schedule() -> void:
	eq(Augments.tierForRound(int(C.AUGMENT_ROUNDS[0])), "silver")
	eq(Augments.tierForRound(int(C.AUGMENT_ROUNDS[1])), "gold")
	eq(Augments.tierForRound(int(C.AUGMENT_ROUNDS[2])), "prismatic")
	eq(Augments.tierForRound(3), null)


# ---- monster camps ------------------------------------------------------------

func test_opens_against_a_camp() -> void:
	var run := run_(21, null)
	eq(run.round, 1)
	check(run.isCreepRound())
	check(run.pendingCamp != null)
	eq(run.pendingFoeName(), Creeps.campForRound(1).name)
	check(run.pendingOpp.size() > 0)
	for u in run.pendingOpp:
		check(u.col >= 5)


func test_camp_rounds_cost_no_player_life() -> void:
	check(Creeps.isCreepRound(1))
	check(Creeps.isCreepRound(6))
	check(not Creeps.isCreepRound(2))
	var run := run_(22, null)
	var camp = run.pendingCamp
	var lifeBefore := run.life
	run.fight()
	check(run.lastResult.creep)
	if not run.lastResult.won:
		eq(lifeBefore - run.life, camp.bite)
		check(camp.bite < 10)
	eq(run.opponents.size(), 7)


func test_beating_a_camp_drops_relics_and_gold() -> void:
	var run := run_(23, null)
	run.gold = 200
	run.level = 9
	for i in 5:
		run.shop = five("hero")
		run.buy(0)
	var camp = run.pendingCamp
	var relicsBefore := run.itemStash.size()
	var goldBefore := run.gold
	run.fight()
	check(run.lastResult.creep)
	check(run.lastResult.won)
	eq(run.itemStash.size(), relicsBefore + camp.relics)
	eq(run.gold, goldBefore + camp.gold)
	eq(run.lastResult.relics, camp.relics)


func test_scales_camps_past_the_list() -> void:
	var late := Creeps.campForRound(101)
	check(late.units.size() > 0)
	check(Creeps.campBoard(late).size() > 0)
	check(late.gold > Creeps.campForRound(1).gold)


# ---- commanders ---------------------------------------------------------------

func test_opens_on_the_commander_choice() -> void:
	var run := run_(51)
	eq(run.phase, "difficulty")
	check(run.pickDifficulty("veteran"))
	eq(run.phase, "commander")
	eq(run.commanderOffer.size(), 3)
	var ids := {}
	for c in run.commanderOffer:
		ids[c.id] = true
	eq(ids.size(), 3)
	check(run.commander == null)
	run.gold = 50
	check(not run.reroll())
	check(not run.buyXp())
	var chosen = run.commanderOffer[0]
	check(run.pickCommander(0))
	eq(run.phase, "shop")
	eq(run.commander.id, chosen.id)
	check(not run.pickCommander(0), "only once")


func test_every_commander_has_an_identity_and_a_lever() -> void:
	var levers := ["gold", "startGold", "interestCap", "freeRerolls", "boardSlots", "shopLevelBonus", "fullRefund", "topTraitBonus", "lossShield", "buff"]
	for c in C.WARBAND_COMMANDERS:
		var id = WarbandCommanders.commanderIdentity(c)
		check(id != null, c.id)
		check(String(id.name).length() > 0)
		check(String(c.perk).length() > 0)
		check(levers.any(func(k): return c.get(k) != null), c.id)
	eq(WarbandCommanders.warbandCommander("nope"), null)


func test_steward_free_reroll() -> void:
	var run := run_(52, "steward")
	eq(run.freeRerollsLeft(), 1)
	eq(run.rerollCost(), 0)
	run.gold = 10
	check(run.reroll())
	eq(run.gold, 10)
	eq(run.rerollCost(), 2)
	check(run.reroll())
	eq(run.gold, 8)
	run.fight()
	if run.phase == "result":
		run.next()
	if run.phase == "augment":
		run.pickAugment(0)
	eq(run.freeRerollsLeft(), 1)


func test_warden_fields_an_extra_unit() -> void:
	var bare := run_(53, null)
	var warden := run_(53, "warden")
	eq(warden.deployCount(), bare.deployCount() + 1)
	warden.gold = 200
	warden.level = 3
	warden.shop = ["knight", "archer", "spearman", "militia", "raider"]
	for i in 5:
		warden.buy(i)
	eq(warden.deployment().size(), 4)


func test_quartermaster_refunds_every_copy() -> void:
	var full := run_(54, "quartermaster")
	var bare := run_(54, null)
	for run in [full, bare]:
		run.gold = 99
		for i in 3:
			run.shop = five("militia")
			run.buy(0)
	var two := -1
	for i in full.pieces.size():
		if full.pieces[i].star == 2:
			two = i
			break
	check(two >= 0)
	var fullBefore := full.gold
	var bareBefore := bare.gold
	full.sell(two)
	var bt := -1
	for i in bare.pieces.size():
		if bare.pieces[i].star == 2:
			bt = i
			break
	bare.sell(bt)
	eq(full.gold - fullBefore, 3)
	eq(bare.gold - bareBefore, 2)


func test_drillmaster_and_magnate() -> void:
	var drill := run_(55, "drillmaster")
	var bare := run_(55, null)
	drill.level = 2
	bare.level = 2
	eq(bare.shopOdds()[1], 0)
	check(drill.shopOdds()[1] > 0)
	var mag := run_(56, "magnate")
	var plain := run_(56, null)
	check(mag.gold - plain.gold >= 10)
	eq(mag.gold, plain.gold + 11)


func test_banneret_lifts_the_largest_synergy() -> void:
	var run := run_(57, "banneret")
	run.gold = 200
	run.level = 4
	for ty in ["archer", "crossbow"]:
		run.shop = five(ty)
		run.buy(0)
	var marks = run.activeTraits().filter(func(t): return t.trait.id == "marksmen")
	eq(marks[0].count if marks.size() else null, 3)
	check(TestWarbandUtil.same(run.sideOpts().traitBonus, {"marksmen": 1}))


func test_warpriest_and_marshal() -> void:
	eq(WarbandCommanders.warbandCommander("warpriest").lossShield, 3)
	var marshal := run_(58, "marshal")
	var buff = marshal.sideOpts().buff
	eq(buff.hpPct, 8)
	eq(buff.armor, 2)
	marshal.augments.append(Augments.augmentById("whetted"))
	var both = marshal.sideOpts().buff
	eq(both.atkPct, 12)
	eq(both.hpPct, 8)


func test_a_commanded_run_terminates() -> void:
	var run := run_(59)
	var guard := 0
	while run.phase != "over" and guard < 200:
		guard += 1
		match run.phase:
			"difficulty": run.pickDifficulty("veteran")
			"commander": run.pickCommander(0)
			"augment": run.pickAugment(0)
			"draft": run.takeCarousel(0)
			"shop":
				if run.gold >= 8:
					run.buyXp()
				for s in run.shop.size():
					run.buy(s)
				run.fight()
			"result": run.next()
	eq(run.phase, "over")
	check(run.commander != null)


# ---- the carousel -------------------------------------------------------------

func test_opens_a_draft_with_components() -> void:
	var run := run_(61, null)
	advanceTo(run, 3)
	eq(run.round, 3)
	check(Carousel.isDraftRound(3))
	eq(run.phase, "draft")
	check(run.carousel.size() > 0)
	for p in run.carousel:
		check(int(Warband.UNIT_TIER[p.type]) > 0)
		check(Items.isComponent(p.item))
		eq(p.star, 1)
	run.gold = 50
	check(not run.reroll())
	check(not run.buyXp())


func test_carousel_unit_arrives_equipped() -> void:
	var run := run_(62, null)
	advanceTo(run, 3)
	var want = run.carousel[0]
	var before := run.pieces.size()
	var pool := run.poolCount(want.type)
	check(run.takeCarousel(0))
	eq(run.phase, "shop")
	check(run.pieces.size() > before - 1)
	check(run.pieces.any(func(p): return p.type == want.type))
	check(run.poolCount(want.type) <= pool - 1)
	eq(run.carousel.size(), 0)
	check(not run.takeCarousel(0))


func test_leader_picks_last() -> void:
	var relead := run_(63, null)
	advanceTo(relead, 3)
	for o in relead.opponents:
		o.life = 20
	relead.life = 100
	relead.openDraft()
	check(relead.draftAhead > 0)
	check(relead.carousel.size() < Carousel.CAROUSEL_SIZE)
	var last := run_(63, null)
	advanceTo(last, 3)
	for o in last.opponents:
		o.life = 100
	last.life = 5
	last.openDraft()
	eq(last.draftAhead, 0)
	eq(last.carousel.size(), Carousel.CAROUSEL_SIZE)


func test_always_leaves_one_option() -> void:
	var run := run_(64, null)
	advanceTo(run, 3)
	for o in run.opponents:
		o.life = 1
		o.alive = true
	run.life = 100
	run.openDraft()
	check(run.carousel.size() >= 1)
	check(run.takeCarousel(0))


func test_carousel_tiers_scale() -> void:
	var rng := RNG.new(9)
	var early: Array = Carousel.rollCarousel(rng, 3).map(func(p): return int(Warband.UNIT_TIER[p.type]))
	var late: Array = Carousel.rollCarousel(rng, 18).map(func(p): return int(Warband.UNIT_TIER[p.type]))
	check(early.max() <= 2)
	check(late.min() >= 4)
	check(Carousel.pickValue({"type": "hero", "star": 1, "item": "blade"}) > Carousel.pickValue({"type": "militia", "star": 1, "item": "blade"}))


func _foeUnits(run: Warband.WarbandRun) -> int:
	var n := 0
	for o in run.opponents:
		var s = run.scout(o.id)
		n += s.board.size() if s != null else 0
	return n


func test_rest_of_the_ring_goes_to_the_field() -> void:
	var run := run_(65, null)
	advanceTo(run, 3)
	var ringSize := run.carousel.size()
	var before := _foeUnits(run)
	run.takeCarousel(0)
	check(ringSize > 1)
	check(_foeUnits(run) >= before)


# ---- battlefield conditions -----------------------------------------------------

func test_opens_clear_and_rolls_real_conditions_later() -> void:
	var run := run_(71, null)
	eq(run.condition.id, "clear")
	check(not Conditions.hasEffect(Conditions.CLEAR))
	var seen := {}
	for s in 30:
		var r2 := run_(700 + s, null)
		advanceTo(r2, 4)
		seen[r2.condition.id] = true
	check(seen.size() > 1)
	for id in seen:
		check(Conditions.conditionById(id) != null)


func test_every_condition_is_well_formed() -> void:
	var traitName := func(id):
		for t in C.TRAITS:
			if t.id == id:
				return t.name
		return id
	for c in Conditions.CONDITIONS:
		check(String(c.name).length() > 0)
		check(String(c.desc).length() > 0)
		check(c.weight > 0)
		check(["clear", "rain", "snow", "overcast"].has(c.weather))
		for id in c.get("traits", {}):
			check(C.TRAITS.any(func(t): return t.id == id), "%s → %s" % [c.id, id])
		if Conditions.hasEffect(c):
			check(Conditions.conditionLines(c, traitName).size() > 0)


func test_rain_bites_marksmen() -> void:
	var rain = Conditions.conditionById("rain")
	var field := {"buff": rain.get("all"), "traitBuffs": rain.get("traits")}
	var archers := [{"type": "archer", "star": 1, "col": 2, "row": 4}, {"type": "archer", "star": 1, "col": 2, "row": 5}]
	var spears := [{"type": "spearman", "star": 1, "col": 7, "row": 4}, {"type": "spearman", "star": 1, "col": 7, "row": 5}]
	var clearWins := 0
	var rainWins := 0
	for seed in [1, 2, 3, 4, 5, 6]:
		if Autobattle.resolveBattle(archers, spears, seed).winner == "A":
			clearWins += 1
		if Autobattle.resolveBattle(archers, spears, seed, 40.0, null, field).winner == "A":
			rainWins += 1
	check(rainWins <= clearWins)


func test_the_same_battlefield_hits_the_enemy() -> void:
	var frost = Conditions.conditionById("frost")
	var field := {"buff": frost.get("all"), "traitBuffs": frost.get("traits")}
	var a := [{"type": "knight", "star": 1, "col": 2, "row": 4}]
	var b := [{"type": "knight", "star": 1, "col": 7, "row": 4}]
	var plain := Autobattle.resolveBattle(a, b, 3)
	var chilled := Autobattle.resolveBattle(a, b, 3, 40.0, null, field)
	eq(chilled.winner, plain.winner)


func test_merges_battlefield_modifiers() -> void:
	var merged = Autobattle.mergeSideOpts(
		{"buff": {"atkPct": 20}, "traitBuffs": {"marksmen": {"atkPct": 10}}},
		{"buff": {"hpPct": -8}, "traitBuffs": {"marksmen": {"atkPct": -18}, "riders": {"speedPct": -25}}})
	check(TestWarbandUtil.same(merged.buff, {"atkPct": 20, "hpPct": -8}))
	check(TestWarbandUtil.same(merged.traitBuffs.marksmen, {"atkPct": -8}))
	check(TestWarbandUtil.same(merged.traitBuffs.riders, {"speedPct": -25}))
	check(TestWarbandUtil.same(Autobattle.mergeSideOpts({"buff": {"atk": 1}}, null), {"buff": {"atk": 1}}))
	check(TestWarbandUtil.same(Autobattle.mergeSideOpts(null, {"buff": {"atk": 2}}), {"buff": {"atk": 2}}))


func test_hands_the_condition_to_the_fight() -> void:
	var run := run_(72, null)
	advanceTo(run, 4)
	var f := run.fieldOpts()
	check(is_same(f.buff, run.condition.get("all")))
	check(is_same(f.traitBuffs, run.condition.get("traits")))


# ---- damage & streaks -----------------------------------------------------------

func test_stage_toll_plus_survivors() -> void:
	eq(Warband.stageOf(1), 1)
	eq(Warband.stageOf(5), 1)
	eq(Warband.stageOf(6), 2)
	eq(Warband.stageOf(11), 3)
	eq(Warband.roundDamage(1, 0), 0)
	eq(Warband.roundDamage(3, 4), 4)
	check(Warband.roundDamage(8, 4) > Warband.roundDamage(3, 4))
	check(Warband.roundDamage(20, 4) > Warband.roundDamage(8, 4))
	eq(Warband.roundDamage(6, 3) - Warband.roundDamage(6, 1), 2)
	eq(Warband.roundDamage(500, 2), Warband.roundDamage(100, 2))


func test_losing_streaks_pay_too() -> void:
	eq(Warband.streakBonus(0), 0)
	eq(Warband.streakBonus(1), 0)
	eq(Warband.streakBonus(2), 1)
	eq(Warband.streakBonus(4), 2)
	eq(Warband.streakBonus(9), 3)
	for n in [0, 1, 2, 3, 4, 5, 9]:
		eq(Warband.streakBonus(-n), Warband.streakBonus(n))


func test_previews_the_worst_case() -> void:
	var bare := run_(81, null)
	advanceTo(bare, 4)
	var power := 0
	for u in bare.pendingOpp:
		power += int(u.get("star", 1))
	eq(bare.worstCaseDamage(), Warband.roundDamage(bare.round, power))
	var priest := run_(81, "warpriest")
	advanceTo(priest, 4)
	check(priest.worstCaseDamage() < bare.worstCaseDamage())
	check(priest.worstCaseDamage() >= 1)


func test_applies_the_formula_on_a_loss() -> void:
	var run := run_(82, null)
	advanceTo(run, 4)
	run.pieces.clear()
	var before := run.life
	run.fight()
	var r = run.lastResult
	if r != null and not r.won and not r.creep:
		eq(before - run.life, r.dmg)
		eq(r.dmg, Warband.roundDamage(run.round, r.dmg - Warband.stageBaseDamage(Warband.stageOf(run.round)) if r.foeLeft > 0 else 0))
		check(run.streak < 0)
		eq(run.streakGold(), Warband.streakBonus(run.streak))


# ---- the bench ------------------------------------------------------------------

func test_nine_bench_slots() -> void:
	var run := run_(83, null)
	eq(Warband.BENCH_SLOTS, 9)
	eq(run.benchSlots(), 9)
	eq(run.benchCount(), 0)
	check(not run.benchFull())


func test_refuses_a_buy_with_board_and_bench_full() -> void:
	var run := run_(84, null)
	run.gold = 500
	run.level = 1
	for t in ["militia", "spearman", "scout", "raider", "archer", "skirmisher", "horseman", "javelin", "pikeman", "knight"]:
		run.shop = five(t)
		run.buy(0)
	eq(run.benchCount(), run.benchSlots())
	check(run.benchFull())
	run.shop = ["crossbow", null, null, null, null]
	check(TestWarbandUtil.same(run.canBuy(0), {"ok": false, "reason": "bench"}))
	var gold := run.gold
	check(not run.buy(0))
	eq(run.gold, gold)


func test_allows_a_merging_buy_with_a_full_bench() -> void:
	var run := run_(85, null)
	run.gold = 500
	run.level = 1
	run.shop = five("militia")
	run.buy(0)
	run.buy(1)
	for t in ["spearman", "scout", "raider", "archer", "skirmisher", "horseman", "javelin", "pikeman"]:
		run.shop = five(t)
		run.buy(0)
	check(run.benchFull())
	run.shop = ["militia", null, null, null, null]
	check(run.canBuy(0).ok)
	check(run.buy(0))
	check(run.pieces.any(func(p): return p.type == "militia" and p.star == 2))


func test_reports_gold_not_bench() -> void:
	var run := run_(86, null)
	run.gold = 0
	run.shop = ["hero", null, null, null, null]
	check(TestWarbandUtil.same(run.canBuy(0), {"ok": false, "reason": "gold"}))


# ---- opponent intelligence -------------------------------------------------------

func _at(board: Array, t: String) -> Dictionary:
	for u in board:
		if u.type == t:
			return u
	return {}


func test_artillery_back_wall_front() -> void:
	var pieces: Array = []
	for t in ["trebuchet", "crossbow", "spearman", "militia", "knight", "scout"]:
		pieces.append({"type": t, "star": 1, "items": []})
	var board := Warband.placeByStyle(pieces, 1)
	eq(_at(board, "spearman").col, 5)
	eq(_at(board, "militia").col, 5)
	eq(_at(board, "trebuchet").col, 8)
	eq(_at(board, "crossbow").col, 8)
	check(_at(board, "trebuchet").col > _at(board, "spearman").col)
	check(_at(board, "crossbow").col > _at(board, "militia").col)
	check([0, 9].has(_at(board, "knight").row))
	eq(cellSet(board).size(), board.size())


func cellSet(board: Array) -> Dictionary:
	var s := {}
	for u in board:
		s["%d,%d" % [u.col, u.row]] = true
	return s


func test_mirrors_the_layout_for_the_player() -> void:
	var board := Warband.placeByStyle([{"type": "trebuchet", "star": 1, "items": []}, {"type": "spearman", "star": 1, "items": []}], -1)
	eq(_at(board, "spearman").col, 4)
	eq(_at(board, "trebuchet").col, 1)
	for u in board:
		check(u.col >= 0 and u.col <= 4)


func test_places_more_than_one_rank() -> void:
	var many: Array = []
	for i in 9:
		many.append({"type": "spearman", "star": 1, "items": []})
	var board := Warband.placeByStyle(many, 1)
	eq(board.size(), 9)
	eq(cellSet(board).size(), 9)


func test_role_sorted_boards_in_a_real_run() -> void:
	var checked := 0
	for seed in [91, 92, 93]:
		var run := run_(seed, null)
		for round in [8, 12, 16]:
			advanceTo(run, round)
			if run.phase == "over":
				break
			for o in run.opponents.filter(func(x): return x.alive):
				var board: Array = run.scout(o.id).board
				var front: Array = board.filter(func(u): return BattleStyles.styleOf(u.type) == "vanguard").map(func(u): return u.col)
				var back: Array = board.filter(func(u): return BattleStyles.styleOf(u.type) == "artillery").map(func(u): return u.col)
				if front.is_empty() or back.is_empty():
					continue
				check(back.min() > front.max(), "seed %d round %d %s" % [seed, round, o.name])
				checked += 1
	check(checked > 0)


func test_drafts_toward_merges_and_synergies() -> void:
	var run := run_(92, null)
	advanceTo(run, 14)
	var sawStarUp := false
	var sawSynergy := false
	for o in run.opponents.filter(func(x): return x.alive):
		var s = run.scout(o.id)
		if s.board.any(func(u): return int(u.get("star", 1)) >= 2):
			sawStarUp = true
		if s.traits.size() > 0:
			sawSynergy = true
	check(sawStarUp)
	check(sawSynergy)


func test_spreads_play_styles() -> void:
	var run := run_(93, null)
	var plays := {}
	for b in run.foeBrains:
		plays[b.play] = true
	check(plays.size() > 1)


func test_attrition_never_eliminates_the_last_opponent() -> void:
	var run := run_(94, null)
	for o in run.opponents.slice(1):
		o.alive = false
		o.life = 0
	run.opponents[0].life = 1
	for i in 200:
		run.thinTheHerd()
	check(run.opponents[0].alive)
	var auto := run_(95, null)
	var guard := 0
	while auto.phase != "over" and guard < 300:
		guard += 1
		if auto.phase == "augment":
			auto.pickAugment(0)
		elif auto.phase == "draft":
			auto.takeCarousel(0)
		elif auto.phase == "shop":
			auto.fight()
		elif auto.phase == "result":
			auto.next()
	eq(auto.phase, "over")
	check(auto.standings()[0].alive)


# ---- scouting ---------------------------------------------------------------------

func test_scouts_a_rival() -> void:
	var run := run_(31, null)
	advanceTo(run, 4)
	var s = run.scout(0)
	check(s != null)
	eq(s.name, run.opponents[0].name)
	eq(s.life, run.opponents[0].life)
	check(s.level >= 1)
	check(s.board.size() > 0)
	for u in s.board:
		check(u.col >= 5)
	check(s.traits is Array)
	eq(run.scout(99), null)


func test_standings_carry_the_scout_id() -> void:
	var run := run_(32, null)
	var rows := run.standings()
	eq(rows.filter(func(r): return r.you)[0].id, -1)
	for r in rows.filter(func(x): return not x.you):
		eq(run.scout(r.id).name, r.name)


# ---- shop comparison --------------------------------------------------------------

func test_names_the_unit_a_buy_would_push_off() -> void:
	var run := run_(61, null)
	run.gold = 300
	run.level = 3
	for t in ["knight", "militia", "scout"]:
		run.shop = five(t)
		run.buy(0)
	eq(run.deployedCount(), 3)
	var weakest = run.weakestDeployed()
	check(weakest != null)
	var order := run.deployment()
	eq(weakest.type, run.pieces[order[order.size() - 1].index].type)
	check(weakest.type != "knight")


func test_nothing_to_compare_on_an_empty_board() -> void:
	eq(run_(62, null).weakestDeployed(), null)


func test_comparison_tracks_the_board() -> void:
	var run := run_(63, null)
	run.gold = 300
	run.level = 2
	run.shop = five("militia")
	run.buy(0)
	eq(run.weakestDeployed().type, "militia")
	run.shop = five("hero")
	run.buy(0)
	eq(run.weakestDeployed().type, "militia")


# ---- synergy progress -------------------------------------------------------------

func _prog(run: Warband.WarbandRun, id: String):
	for p in run.traitProgress():
		if p.trait.id == id:
			return p
	return null


func test_reports_traits_not_yet_switched_on() -> void:
	var run := run_(71, null)
	run.gold = 300
	run.level = 2
	run.shop = five("militia")
	run.buy(0)
	var footmen = _prog(run, "footmen")
	check(footmen != null)
	eq(footmen.count, 1)
	eq(footmen.tier, null)
	check(footmen.next > 1)
	check(not run.activeTraits().any(func(a): return a.trait.id == "footmen"))


func test_marks_a_trait_active_and_points_at_the_next() -> void:
	var run := run_(72, null)
	run.gold = 300
	run.level = 4
	for t in ["militia", "spearman"]:
		run.shop = five(t)
		run.buy(0)
	var footmen = _prog(run, "footmen")
	eq(footmen.count, 2)
	check(footmen.tier != null)
	check(footmen.next == null or footmen.next > footmen.count)


func test_leaves_out_untouched_traits() -> void:
	eq(run_(73, null).traitProgress(), [])


# ---- levelling --------------------------------------------------------------------

func test_reaches_max_level() -> void:
	var run := run_(74, null)
	run.gold = 10000
	var i := 0
	while i < 80 and run.level < 9:
		run.buyXp()
		i += 1
	eq(run.level, 9)


func test_never_a_nan_xp_bar() -> void:
	var run := run_(75, null)
	run.gold = 10000
	for i in 80:
		var xp := run.xpProgress()
		check(not is_nan(float(xp.xp)) and not is_nan(float(xp.need)))
		if not run.buyXp():
			break
	check(run.xpProgress().max)


# ---- run history ------------------------------------------------------------------

func playOut(run: Warband.WarbandRun, rounds: int = 40) -> void:
	var i := 0
	while i < rounds and run.phase != "over":
		i += 1
		if run.phase == "augment":
			run.pickAugment(0)
		if run.phase == "draft":
			run.takeCarousel(0)
		if run.phase == "shop":
			for k in 5:
				run.buy(k)
			if not run.beginFight():
				break
			run.finishFight(Autobattle.resolveBattle(run.boardUnits(), run.pendingOpp, run.pendingSeed, 30.0, run.sideOpts(), run.fieldOpts()))
		if run.phase == "result":
			run.next()


func test_records_one_entry_per_fight() -> void:
	var run := run_(81, null)
	playOut(run)
	eq(run.phase, "over")
	check(run.history.size() > 2)
	for i in range(1, run.history.size()):
		check(run.history[i].round > run.history[i - 1].round)
	eq(run.history[run.history.size() - 1].life, run.life)
	for h in run.history:
		check(String(h.foe).length() > 0)
		check(h.won is bool)


func test_tallies_record_and_spending() -> void:
	var run := run_(82, null)
	playOut(run)
	var t := run.tally
	eq(t.wins + t.losses + t.campsCleared + t.campsLost, run.history.size())
	check(t.goldSpent > 0)
	check(t.unitsBought > 0)
	check(t.bestStreak >= 0)
	check(t.worstStreak <= 0)


func test_history_starts_empty() -> void:
	var run := run_(83, null)
	eq(run.history, [])
	eq(run.tally.wins, 0)
	eq(run.tally.goldSpent, 0)


# ---- lobby difficulty (the quick ones) ----------------------------------------------

func test_offers_four_lobbies_veteran_by_default() -> void:
	var run := run_(91, null)
	eq(run.difficulty.id, "veteran")
	eq(run.difficultyOffer().map(func(d): return d.id), ["squire", "veteran", "warlord", "conqueror"])


func test_opens_on_difficulty_then_commander() -> void:
	var run := run_(92)
	eq(run.phase, "difficulty")
	check(run.pickDifficulty("squire"))
	eq(run.difficulty.id, "squire")
	eq(run.phase, "commander")
	check(not run.pickDifficulty("conqueror"))
	check(run.gold >= 2 + 3)


func test_scales_what_a_defeat_costs() -> void:
	var lifeLostBy := func(diff: String) -> int:
		var run := run_(93, null)
		run.difficulty = WarbandDifficulty.difficultyById(diff)
		var guard := 0
		while run.phase != "over" and run.round < 14 and guard < 40:
			guard += 1
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
		return run.tally.lifeLost
	check(lifeLostBy.call("conqueror") > lifeLostBy.call("squire"))


# ---- the ground seed ----------------------------------------------------------------

func toRound6(run: Warband.WarbandRun) -> void:
	while run.round < 6:
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


func test_replays_a_board_from_its_code() -> void:
	var run := run_(101, null)
	toRound6(run)
	eq(run.phase, "shop")
	check(run.terrain.size() > 0)
	var wanted := run.terrainCode()
	var shape := JSON.stringify(run.terrain)
	run.applyTerrainCode("ZZZZZZ")
	check(run.terrainCode() != wanted)
	check(run.applyTerrainCode(wanted))
	eq(run.terrainCode(), wanted)
	eq(JSON.stringify(run.terrain), shape)


func test_refuses_bad_codes_and_outside_setup() -> void:
	var run := run_(102, null)
	check(not run.applyTerrainCode(""))
	check(not run.applyTerrainCode("hello!"))
	check(not run.applyTerrainCode("TOOLONGCODE"))
	run.phase = "battle"
	check(not run.applyTerrainCode("ABC123"))


func test_lifts_a_unit_off_a_blocked_cell() -> void:
	var run := run_(103, null)
	run.gold = 300
	run.level = 6
	toRound6(run)
	run.gold = 300
	for t in ["militia", "archer", "knight"]:
		run.shop = five(t)
		run.buy(0)
	var fielded := run.deployedCount()
	var moved := false
	var i := 0
	while i < 60 and not moved:
		var before := cellKeys(run.deployment())
		run.applyTerrainCode(TestWarbandUtil.base36(1000 + i * 7919).to_upper().right(6))
		var after := cellKeys(run.deployment())
		for d in run.deployment():
			check(not run.terrain.any(func(f): return f.kind == "boulder" and f.col == d.col and f.row == d.row), "standing on a boulder")
		if before != after:
			moved = true
		i += 1
	eq(run.deployedCount(), fielded)
