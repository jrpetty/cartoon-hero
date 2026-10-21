extends TestCase
# Ported from stance.test.ts, abilities.test.ts, upgrades.test.ts,
# boons.test.ts, oaths.test.ts, factions.test.ts (the sim half — the drawing
# checks belong to the renderer port) and idle_time.test.ts.

const P := 0
const E := 1


func run(w: World, secs: float) -> void:
	for i in int(w.SIM_HZ * secs):
		w.tick()


# ---- Stances ----

func mkHostile() -> World:
	return SimTestUtil.build({"seed": 1, "alliances": [0, 1]})


func test_stance_defaults_to_defensive() -> void:
	eq(mkHostile().spawnUnit(P, "militia", 900, 900).stance, T.Stance.Defensive)


func test_passive_holds_fire() -> void:
	var w := mkHostile()
	var me := w.spawnUnit(P, "militia", 1000, 1000)
	w.setStance([me.id], T.Stance.Passive)
	var foe := w.spawnUnit(E, "militia", 1014, 1000)
	var foeHp := foe.hp
	run(w, 4)
	eq(foe.hp, foeHp, "never struck it")
	check(me.order.kind != T.OrderKind.Attack)


func test_stand_ground_fires_in_range_but_never_moves() -> void:
	var w := mkHostile()
	var me := w.spawnUnit(P, "archer", 1000, 1000)
	w.setStance([me.id], T.Stance.StandGround)
	var sx := me.x
	var sy := me.y
	var foe := w.spawnUnit(E, "militia", 1100, 1000)
	var foeHp := foe.hp
	run(w, 3)
	check(foe.hp < foeHp, "shot it")
	check(JS.hypot(me.x - sx, me.y - sy) < 10, "held position")


func test_stand_ground_ignores_a_target_it_cant_reach() -> void:
	var w := mkHostile()
	var me := w.spawnUnit(P, "militia", 1000, 1000)
	w.setStance([me.id], T.Stance.StandGround)
	var sx := me.x
	var foe := w.spawnUnit(E, "militia", 1240, 1000)
	foe.stance = T.Stance.Passive
	run(w, 4)
	check(absf(me.x - sx) < 10, "never advanced")


func test_aggressive_holds_ground_defensive_returns_to_post() -> void:
	var fight := func(stance: int) -> float:
		var w := mkHostile()
		var me := w.spawnUnit(P, "knight", 1000, 1000)
		me.stance = stance
		var foe := w.spawnUnit(E, "militia", 1110, 1000)
		foe.stance = T.Stance.Passive
		run(w, 16)
		check(not foe.alive, "both stances kill it")
		return me.x
	var defX: float = fight.call(T.Stance.Defensive)
	var aggX: float = fight.call(T.Stance.Aggressive)
	check(defX < aggX - 40, "defensive %s vs aggressive %s" % [defX, aggX])


func test_stance_command_respects_ownership() -> void:
	var w := mkHostile()
	var mine := w.spawnUnit(P, "militia", 1000, 1000)
	Commands.applyCommand(w, {"t": "stance", "team": P, "ids": [mine.id], "stance": T.Stance.Aggressive})
	eq(mine.stance, T.Stance.Aggressive)
	var enemy := w.spawnUnit(E, "militia", 1100, 1000)
	Commands.applyCommand(w, {"t": "stance", "team": P, "ids": [enemy.id], "stance": T.Stance.Passive})
	eq(enemy.stance, T.Stance.Defensive, "not ours")


func test_hold_command_also_sets_stand_ground() -> void:
	var w := mkHostile()
	var me := w.spawnUnit(P, "militia", 1000, 1000)
	w.issueHold([me.id])
	eq(me.stance, T.Stance.StandGround)


# ---- Abilities ----

func test_every_shop_unit_has_a_well_formed_ability() -> void:
	for type in C.UNIT_TIER:
		var ab = C.ABILITIES.get(type)
		check(ab != null, "%s has no ability" % type)
		if ab == null:
			continue
		check(ab.cooldown > 0 and ab.duration > 0, type)
		check(str(ab.color).begins_with("#"), type)
		if ab.kind in ["volley", "heal", "cleave"]:
			check(ab.get("amount", 0) > 0 and ab.get("radius", 0) > 0, "%s %s needs amount/radius" % [type, ab.kind])
		if ab.kind in ["guard", "rally", "slow"]:
			check(ab.get("radius", 0) > 0 and ab.get("statusDuration", 0) > 0, "%s %s needs radius/statusDuration" % [type, ab.kind])
		if ab.kind == "buff":
			check(ab.has("speedMult") or ab.has("attackMult") or ab.has("armorBonus") or ab.has("atkIntervalMult") or ab.has("bonusVs"), "%s buff does nothing" % type)


func test_shield_wall_armours_the_line() -> void:
	var w := SimTestUtil.build({"seed": 4242})
	var guard := w.spawnUnit(P, "shieldbearer", 1000, 1000)
	var mate := w.spawnUnit(P, "militia", 1040, 1000)
	var faraway := w.spawnUnit(P, "militia", 1000, 1900)
	w.tick()
	eq(w.useAbility([guard.id]), 1)
	check(mate.guardTimer > 0)
	eq(faraway.guardTimer, 0, "out of radius")
	var victim := w.spawnUnit(E, "militia", 1500, 1500)
	var bare := victim.hp
	w.dealDamage(P, victim, 30, "twohand")
	var withoutGuard := bare - victim.hp
	mate.hp = mate.maxHp
	var before := mate.hp
	w.dealDamage(E, mate, 30, "twohand")
	eq(before - mate.hp, withoutGuard - C.GUARD_ARMOR)


func test_fires_every_ability_and_each_does_something() -> void:
	for type in C.UNIT_TIER:
		var w := SimTestUtil.build({"seed": 4242})
		var caster := w.spawnUnit(P, type, 1200, 1200)
		var ally := w.spawnUnit(P, "militia", 1240, 1200)
		var foe := w.spawnUnit(E, "militia", 1250, 1200)
		ally.hp = floor(ally.maxHp / 2.0)
		w.tick()
		var foeHp := foe.hp
		var allyHp := ally.hp
		eq(w.useAbility([caster.id]), 1, "%s did not fire" % type)
		var ab: Dictionary = C.ABILITIES[type]
		match ab.kind:
			"buff":
				check(caster.abilityActive > 0, type)
			"rally":
				check(ally.rallyTimer > 0, type)
			"guard":
				check(ally.guardTimer > 0, type)
			"slow":
				check(foe.slowTimer > 0, type)
			"heal":
				check(ally.hp > allyHp, type)
			"volley", "cleave":
				check(foe.hp < foeHp, type)
		check(caster.abilityCooldown > 0, type)
		eq(w.useAbility([caster.id]), 0, "%s spammed" % type)


func test_ability_casts_stay_deterministic() -> void:
	var cast := func() -> float:
		var w := SimTestUtil.build({"seed": 4242})
		var c := w.spawnUnit(P, "battlemage", 1200, 1200)
		var f := w.spawnUnit(E, "militia", 1260, 1200)
		w.useAbility([c.id])
		for i in 20:
			w.tick()
		return f.hp
	eq(cast.call(), cast.call())


# ---- Upgrades ----

func test_bloodlines_adds_cavalry_hp() -> void:
	var w := SimTestUtil.build({"seed": 1234})
	var p = w.player(P)
	var k := w.spawnUnit(P, "knight", 1000, 1000)
	var base := k.maxHp
	k.hp = base - 10
	p.upgrades["bloodlines"] = true
	w.refreshTeamUnits(P)
	eq(k.maxHp, base + 20, "standing army refreshed")
	eq(k.hp, JS.round((base + 20) * ((base - 10) / base)), "HP fraction kept")
	eq(w.spawnUnit(P, "knight", 1100, 1000).maxHp, base + 20)


func test_husbandry_makes_cavalry_faster() -> void:
	var w := SimTestUtil.build({"seed": 1234})
	var k := w.spawnUnit(P, "knight", 1000, 1000)
	var before := w.effectiveSpeed(k)
	w.player(P).upgrades["husbandry"] = true
	check(w.effectiveSpeed(k) > before)


func test_long_swords_boosts_only_the_man_at_arms_line() -> void:
	var w := SimTestUtil.build({"seed": 1234})
	var mil := w.spawnUnit(P, "militia", 1000, 1000)
	var spear := w.spawnUnit(P, "spearman", 1100, 1000)
	var dummy := w.spawnUnit(E, "militia", 1050, 1000)
	var milBefore := w.effectiveAttack(mil, C.UNITS.militia, dummy)
	var spearBefore := w.effectiveAttack(spear, C.UNITS.spearman, dummy)
	w.player(P).upgrades["longswords"] = true
	check(w.effectiveAttack(mil, C.UNITS.militia, dummy) > milBefore)
	eq(w.effectiveAttack(spear, C.UNITS.spearman, dummy), spearBefore)


func test_pikes_adds_anti_cavalry_bonus() -> void:
	var w := SimTestUtil.build({"seed": 1234})
	var spear := w.spawnUnit(P, "spearman", 1000, 1000)
	var horse := w.spawnUnit(E, "knight", 1050, 1000)
	var before := w.effectiveAttack(spear, C.UNITS.spearman, horse)
	w.player(P).upgrades["pikes"] = true
	eq(w.effectiveAttack(spear, C.UNITS.spearman, horse), before + 12)


func test_gold_mining_speeds_gold_gathering() -> void:
	# On the fixture map some mines are hemmed in so that a returning villager
	# can never reach the camp (the TS build stalls on them identically), so
	# use the first mine where the setup works at all, then compare.
	var gather := func(mineIndex: int, withUp: bool) -> float:
		var w2 := SimTestUtil.build({"seed": 1234})
		var mines := w2.entities.filter(func(e): return e.alive and e.type == "gold_mine")
		var mine: Entity = mines[mineIndex]
		var camp := w2.spawnBuilding(P, "mining_camp", mine.x + 64, mine.y, true)
		camp.buildState = 0
		if withUp:
			w2.player(P).upgrades["gold_mining"] = true
		var v := w2.spawnUnit(P, "villager", mine.x, mine.y + 24)
		w2.issueGather([v.id], mine.id)
		for i in 20 * 60:
			w2.tick()
		return w2.player(P).resources.gold
	for k in 12:
		var base: float = gather.call(k, false)
		if base > C.START_RESOURCES.gold:
			check(gather.call(k, true) > base, "gold mining made no difference at mine %d" % k)
			return
	failures.append("no workable gold mine on the fixture map")


func test_loom_hardens_villagers_only() -> void:
	var w := SimTestUtil.build({"seed": 1234})
	var base := w.spawnUnit(P, "villager", 1000, 1000).maxHp
	w.player(P).upgrades["loom"] = true
	eq(w.spawnUnit(P, "villager", 1050, 1000).maxHp, base + 15)
	var sBase := w.spawnUnit(E, "spearman", 2000, 2000).maxHp
	eq(w.spawnUnit(P, "spearman", 1100, 1000).maxHp, sBase)


func test_town_watch_widens_your_sight_only() -> void:
	var w := SimTestUtil.build({"seed": 1234})
	var base := w.spawnUnit(P, "scout", 1000, 1000).visionRange
	w.player(P).upgrades["town_watch"] = true
	near(w.spawnUnit(P, "scout", 1050, 1000).visionRange, base * 1.25, 1e-3)
	near(w.spawnUnit(E, "scout", 2000, 2000).visionRange, C.UNITS.scout.visionRange, 1e-3)


func test_caravan_improves_what_the_market_pays() -> void:
	var w := SimTestUtil.build({"seed": 1234})
	var p = w.player(P)
	eq(w.marketTrade(P, "sell_wood"), false, "no market yet")
	w.spawnBuilding(P, "market", 1200, 1200, true)
	p.resources.wood = 1000.0
	p.resources.gold = 0.0
	eq(w.marketQuote(P, "wood").sell, 75)
	p.upgrades["caravan"] = true
	eq(w.marketQuote(P, "wood").sell, 85)
	eq(w.marketTrade(P, "sell_wood"), true)
	eq(p.resources.gold, 85)
	w.spawnBuilding(E, "market", 1600, 1600, true)
	eq(w.marketQuote(E, "wood").sell, 75, "not shared")


func test_cavalier_upgrades_only_its_own_line() -> void:
	var w := SimTestUtil.build({"seed": 1234})
	var knightBase := w.spawnUnit(P, "knight", 1000, 1000).maxHp
	var horseBase := w.spawnUnit(P, "horseman", 1050, 1000).attack
	var p = w.player(P)
	p.upgrades["cavalier"] = true
	p.upgrades["heavy_cav_archer"] = true
	eq(w.spawnUnit(P, "knight", 1100, 1000).maxHp, knightBase + 25)
	eq(w.spawnUnit(P, "horseman", 1150, 1000).attack, horseBase)
	var target := w.spawnUnit(E, "militia", 1400, 1000)
	var hb := w.spawnUnit(P, "horseman", 1200, 1000)
	check(w.effectiveAttack(hb, C.UNITS.horseman, target) >= horseBase + 3)


func test_every_upgrade_is_hosted_by_a_building_that_exists() -> void:
	for id in C.UPGRADES:
		var up: Dictionary = C.UPGRADES[id]
		check(C.BUILDINGS.has(up.researchedAt), "%s -> %s" % [id, up.researchedAt])
		check(up.amount > 0, id)


# ---- Boons ----

func worldWithBoon(boon = null) -> World:
	var plan: Array = []
	if boon != null:
		plan = [{"id": boon[0], "rarity": boon[1], "age": 0}]
	return SimTestUtil.build({"seed": 4242, "boons": [plan, []]})


func test_whetstones_raises_infantry_attack() -> void:
	var b := worldWithBoon().spawnUnit(P, "militia", 1000, 1000)
	var a := worldWithBoon(["whetstones", 2]).spawnUnit(P, "militia", 1000, 1000)
	check(a.attack > b.attack)


func test_iron_discipline_raises_hp_and_pavise_armor() -> void:
	var b := worldWithBoon().spawnUnit(P, "spearman", 1000, 1000)
	check(worldWithBoon(["iron_discipline", 3]).spawnUnit(P, "spearman", 1000, 1000).maxHp > b.maxHp)
	check(worldWithBoon(["pavise", 4]).spawnUnit(P, "spearman", 1000, 1000).armor > b.armor)


func test_a_higher_rarity_boon_is_stronger() -> void:
	var l := worldWithBoon(["whetstones", 0]).spawnUnit(P, "militia", 1000, 1000)
	var h := worldWithBoon(["whetstones", 5]).spawnUnit(P, "militia", 1000, 1000)
	check(h.attack > l.attack)


func test_master_masons_makes_buildings_tougher() -> void:
	var b := worldWithBoon().spawnBuilding(P, "barracks", 1200, 1200, true)
	var m := worldWithBoon(["master_masons", 3]).spawnBuilding(P, "barracks", 1200, 1200, true)
	check(m.maxHp > b.maxHp)


func test_bountiful_fields_speeds_food_gathering() -> void:
	var gather := func(w: World) -> float:
		var berry = SimTestUtil.find(w, func(e): return e.alive and e.type == "berries")
		var mill := w.spawnBuilding(P, "mill", berry.x + 32 * 3, berry.y, true)
		mill.buildState = 0
		var v := w.spawnUnit(P, "villager", berry.x, berry.y + 24)
		w.issueGather([v.id], berry.id)
		for i in 20 * 30:
			w.tick()
		return w.player(P).resources.food
	check(gather.call(worldWithBoon(["bountiful_fields", 4])) > gather.call(worldWithBoon()))


func test_an_age_gated_boon_activates_on_age_up_and_buffs_the_army() -> void:
	var w := SimTestUtil.build({"seed": 4242, "boons": [[{"id": "iron_discipline", "rarity": 3, "age": 1}], []]})
	var u := w.spawnUnit(P, "spearman", 1000, 1000)
	var baseHp := u.maxHp
	u.hp = baseHp - 10
	w.player(P).age = 1
	w.recomputeBoons(P)
	check(u.maxHp > baseHp, "grew")
	check(u.hp < u.maxHp, "no free heal")


func test_boon_free_players_are_unaffected() -> void:
	var w := worldWithBoon()
	var u := w.spawnUnit(P, "knight", 1000, 1000)
	var e := w.spawnUnit(E, "knight", 1100, 1000)
	eq(u.attack, e.attack)
	eq(u.maxHp, e.maxHp)


# ---- Oaths ----

func oathWorld(seed: int = 1234) -> World:
	var w := SimTestUtil.build({"seed": seed})
	w.player(P).resources = {"food": 99999.0, "wood": 99999.0, "gold": 99999.0}
	return w


func oathBuild(w: World, type: String, team: int = P) -> Entity:
	var start: Dictionary = w.map.starts[team]
	var b := SimTestUtil.placeNear(w, team, type, start.x, start.y, 140, 620, 36, 16)
	check(b != null, "no spot for " + type)
	return b


## Advance `team` one age, swearing `oath`, and let the research finish.
func advance(w: World, oath, team: int = P) -> void:
	var p = w.player(team)
	var next: int = p.age + 1
	var age: Dictionary = C.AGES[next]
	for t in (age.requiresAny as Array).slice(0, int(age.requiresCount)):
		if not w.hasBuilding(team, t):
			oathBuild(w, t, team)
	p.resources = {"food": 99999.0, "wood": 99999.0, "gold": 99999.0}
	var tech: String = ("age:" + oath) if oath != null else "age"
	eq(w.research(team, SimTestUtil.tcOf(w, team).id, tech), true, "could not start %s" % age.name)
	run(w, age.advanceTime + 1)
	eq(p.age, next, "advanced")


func test_runs_four_ages() -> void:
	eq(C.AGES.map(func(a): return a.name), ["Hearth Age", "Banner Age", "Crown Age", "Empire Age"])
	eq(C.MAX_AGE, 3)
	for id in ["handcannon", "trebuchet"]:
		eq(C.UNITS[id].age, 3, id)


func test_empire_can_be_reached_through_a_siege_workshop() -> void:
	var w := oathWorld()
	advance(w, "plough")
	advance(w, "shield")
	eq(w.ageRequirementMet(P, 3), false)
	oathBuild(w, "siege_workshop")
	eq(w.ageRequirementMet(P, 3), true)
	advance(w, "engine")
	eq(w.player(P).age, 3)


func test_offers_three_oaths_at_each_advance_and_only_those() -> void:
	for age in [1, 2, 3]:
		eq(Oaths.oathsForAge(age).size(), 3, "age %d" % age)
	eq(Oaths.oathsForAge(0).size(), 0)
	var w := oathWorld()
	oathBuild(w, "barracks")
	oathBuild(w, "mill")
	eq(w.research(P, SimTestUtil.tcOf(w).id, "age:lance"), false, "a later age's Oath")
	eq(w.research(P, SimTestUtil.tcOf(w).id, "age:nonsense"), false)


func test_swears_the_oath_when_the_advance_completes_and_tells_everyone() -> void:
	var w := oathWorld()
	oathBuild(w, "barracks")
	oathBuild(w, "mill")
	w.research(P, SimTestUtil.tcOf(w).id, "age:sword")
	eq(w.player(P).oaths, [], "sworn before the advance finished")
	var seen: Array = []
	for i in int((C.AGES[1].advanceTime + 1) * w.SIM_HZ):
		w.tick()
		for ev in w.drainEvents():
			if ev.kind == "oath":
				seen.append("%d:%s" % [ev.team, ev.data])
	eq(w.player(P).oaths, ["sword"])
	eq(seen, ["0:sword"])


func test_oaths_replay_identically_from_the_command_log() -> void:
	var play := func() -> World:
		var w := oathWorld(77)
		oathBuild(w, "barracks")
		oathBuild(w, "mill")
		Commands.applyCommand(w, {"t": "research", "team": P, "buildingId": SimTestUtil.tcOf(w).id, "tech": "age:hearth"})
		run(w, 40)
		return w
	var a: World = play.call()
	var b: World = play.call()
	eq(a.player(P).oaths, ["hearth"])
	eq(Commands.worldChecksum(a), Commands.worldChecksum(b))


func test_signature_units_belong_to_their_sworn() -> void:
	for id in C.OATHS:
		var o: Dictionary = C.OATHS[id]
		if o.get("unit") == null:
			continue
		eq(C.UNITS[o.unit].get("oath"), id, o.unit)
		check((C.BUILDINGS[C.UNITS[o.unit].trainedAt].trains as Array).has(o.unit), o.unit)
		eq(C.UNITS[o.unit].age, o.age, o.unit)
	var w := oathWorld()
	advance(w, "plough")
	var barracks := oathBuild(w, "barracks")
	eq(w.trainUnit(P, barracks.id, "sworn_blade"), false, "without the Oath")
	var w2 := oathWorld()
	advance(w2, "sword")
	var b2 := oathBuild(w2, "barracks")
	eq(w2.trainUnit(P, b2.id, "sworn_blade"), true, "with the Oath")


func test_plough_fuller_fields_that_replant_for_free() -> void:
	var w := oathWorld()
	advance(w, "plough")
	var farm := oathBuild(w, "farm")
	eq(farm.amount, JS.round(C.FARM_FOOD * 1.3))
	near(w.player(P).oath.villagerTrainMult, 0.9, 1e-9)
	eq(w.player(P).oath.freeReseed, true)


func test_hearth_a_tougher_town_and_buildings_that_mend() -> void:
	var w := oathWorld()
	var house := oathBuild(w, "house")
	var before := house.maxHp
	advance(w, "hearth")
	eq(house.maxHp, JS.round(C.BUILDINGS.house.hp * 1.3), "existing buildings toughened")
	check(house.maxHp > before)
	house.hp = house.maxHp / 2.0
	house.lastDamageTime = w.time
	run(w, 5)
	eq(house.hp, house.maxHp / 2.0, "mended while still under attack")
	run(w, 8)
	check(house.hp > house.maxHp / 2.0, "mended")


func test_an_oath_is_stronger_under_its_favoured_commander() -> void:
	var hp := func(commander: String) -> float:
		var w := oathWorld()
		w.player(P).commander = commander
		var house := oathBuild(w, "house")
		advance(w, "hearth")
		return house.maxHp / C.BUILDINGS.house.hp
	near(hp.call("steward"), 1.3, 0.005)
	near(hp.call("architect"), 1.45, 0.005)


func test_shield_infantry_already_in_the_field_get_the_armour() -> void:
	var w := oathWorld()
	advance(w, "plough")
	var man := w.spawnUnit(P, "militia", 600, 600)
	var armour := man.armor
	advance(w, "shield")
	eq(man.armor, armour + 2)


func test_lance_the_blow_at_the_end_of_a_run_hits_three_times_as_hard() -> void:
	var hit := func(runUp: bool) -> float:
		var w := oathWorld()
		advance(w, "plough")
		advance(w, "lance")
		# Mid-map: at (800, 800) on the fixture map the dummy stands in the Town
		# Centre's range, and its arrow lands before the lance does.
		var lancer := w.spawnUnit(P, "lancer", 1500, 1500)
		var dummy := w.spawnUnit(E, "twohand", 1500 + (C.CHARGE_RUN + 120 if runUp else 20), 1500)
		dummy.maxHp = 100000
		dummy.hp = 100000
		dummy.speed = 0
		w.issueAttack([lancer.id], dummy.id)
		var before := dummy.hp
		var i := 0
		while i < 20 * 12 and dummy.hp == before:
			w.tick()
			i += 1
		return before - dummy.hp
	var charged: float = hit.call(true)
	var standing: float = hit.call(false)
	check(standing > 0, "hit at all")
	check(charged / standing > 2.5, "charged %s vs standing %s" % [charged, standing])


func test_bow_a_ranger_keeps_its_cover_while_it_shoots() -> void:
	var hidden := func(type: String) -> bool:
		var w := oathWorld()
		var cx := 30
		var cy := 30
		for y in range(cy - 3, cy + 4):
			for x in range(cx - 3, cx + 4):
				w.terrain[y * w.terrainCols + x] = w.TERRAIN_FOREST
		var s := w.spawnUnit(P, type, (cx + 0.5) * 32, (cy + 0.5) * 32)
		w.spawnUnit(E, "militia", (cx + 8) * 32, (cy + 0.5) * 32)
		for i in 5:
			s.attackCooldown = 1
			w.tick()
		return (s.spottedBy & (1 << E)) == 0
	eq(hidden.call("ranger"), true, "ranger")
	eq(hidden.call("archer"), false, "archer")


func test_coin_each_market_pays_a_tithe_up_to_three() -> void:
	var w := oathWorld()
	advance(w, "plough")
	advance(w, "shield")
	oathBuild(w, "siege_workshop")
	advance(w, "coin")
	for i in 5:
		oathBuild(w, "market")
	var p = w.player(P)
	var g0: float = p.resources.gold
	run(w, 10)
	near(p.resources.gold - g0, 15, 0.5)


func test_engine_siege_is_tougher_crown_every_unit_is() -> void:
	var w := oathWorld()
	advance(w, "plough")
	advance(w, "shield")
	oathBuild(w, "siege_workshop")
	advance(w, "engine")
	eq(w.spawnUnit(P, "ram", 700, 700).maxHp, JS.round(C.UNITS.ram.hp * 1.3))
	var w2 := oathWorld()
	advance(w2, "plough")
	advance(w2, "shield")
	oathBuild(w2, "castle")
	advance(w2, "crown")
	eq(w2.spawnUnit(P, "knight", 700, 700).maxHp, JS.round(C.UNITS.knight.hp * 1.12))
	near(w2.player(P).oath.vetMult, 1.6, 1e-9)


# ---- Factions ----

func realm(faction: String, opts: Dictionary = {}) -> World:
	var w := SimTestUtil.build({"seed": 99, "loadouts": [opts.get("loadout", {}), {}], "commanders": [opts.get("commander", ""), ""],
		"boons": [opts.boons, []] if opts.has("boons") else null, "factions": [faction, "kingdom"]})
	w.player(P).resources = {"food": 99999.0, "wood": 99999.0, "gold": 99999.0}
	return w


func test_faction_units_are_real_and_on_a_roster() -> void:
	for fid in C.FACTIONS:
		var f: Dictionary = C.FACTIONS[fid]
		for base in f.replaces:
			check(C.UNITS.has(base), "%s replaces unknown %s" % [fid, base])
			eq(C.UNITS[f.replaces[base]].get("role"), base, "%s inherits %s's rarity" % [f.replaces[base], base])
		for u in f.replaces.values() + f.extra:
			check((C.BUILDINGS[C.UNITS[u].trainedAt].trains as Array).has(u), "%s is on no roster" % u)


func test_trains_its_own_units_instead_of_the_ones_they_replace() -> void:
	var legion := realm("legion")
	var barracks := oathBuild(legion, "barracks")
	eq(legion.trainUnit(P, barracks.id, "militia"), false, "the Legion trained a Man-at-Arms")
	eq(legion.trainUnit(P, barracks.id, "legionary"), true)
	var kingdom := realm("kingdom")
	var kb := oathBuild(kingdom, "barracks")
	eq(kingdom.trainUnit(P, kb.id, "legionary"), false, "the Kingdom trained a Legionary")
	eq(kingdom.trainUnit(P, kb.id, "militia"), true)
	check(not Factions.rosterFor("legion", C.BUILDINGS.barracks.trains).has("militia"))
	check(Factions.rosterFor("legion", C.BUILDINGS.barracks.trains).has("legionary"))


func test_faction_bonuses_are_in_force_from_the_first_tick() -> void:
	var k := realm("khanate")
	eq(SimTestUtil.tcOf(k).maxHp, JS.round(C.BUILDINGS.town_center.hp * 0.8))
	var s := realm("shogunate")
	var vil = SimTestUtil.find(s, func(e): return e.team == P and e.type == "villager")
	eq(vil.maxHp, JS.round(C.UNITS.villager.hp * 1.25))


func test_faction_bonuses_change_what_things_cost() -> void:
	var base: float = C.UPGRADES.forging.cost.gold
	eq(realm("kingdom").techCostFor(P, "forging").gold, JS.round(base * 0.75))
	eq(realm("norse").techCostFor(P, "forging").gold, JS.round(base * 1.1))
	eq(realm("legion").unitCostFor(P, "knight").food, JS.round(C.UNITS.knight.cost.food * 1.15))
	var asc := realm("ascendancy")
	eq(asc.unitCostFor(P, "spearman").food, JS.round(C.UNITS.spearman.cost.food * 1.15))
	eq(asc.unitCostFor(P, "villager").food, C.UNITS.villager.cost.food, "villagers aren't soldiers")
	eq(realm("khanate").buildingCostFor(P, "stable").wood, JS.round(C.BUILDINGS.stable.cost.wood * 0.75))


func test_the_khanates_yurts_hold_more() -> void:
	var khan := realm("khanate")
	var before: float = khan.player(P).popCap
	var s: Dictionary = khan.map.starts[P]
	var house = khan.placeBuilding(P, "house", s.x + 200, s.y + 40)
	if house == null:
		house = khan.placeBuilding(P, "house", s.x - 200, s.y - 40)
	if house == null:
		house = SimTestUtil.placeNear(khan, P, "house", s.x, s.y, 160, 500, 32, 16, false)
	var builder = SimTestUtil.find(khan, func(e): return e.team == P and e.type == "villager")
	khan.issueBuildRepair([builder.id], house.id)
	var i := 0
	while i < 20 * 60 and khan.player(P).popCap == before:
		khan.tick()
		i += 1
	eq(khan.player(P).popCap - before, C.BUILDINGS.house.popProvided + 3)


func test_meta_systems_still_apply_to_faction_units() -> void:
	var w := realm("legion", {"loadout": {"militia": 3}})
	var leg := w.spawnUnit(P, "legionary", 700, 700)
	eq(leg.variantRarity, 3)
	eq(leg.maxHp, JS.round(C.UNITS.legionary.hp * C.RARITY_HP_MULT[3]))
	eq(realm("legion", {"loadout": {"militia": 1, "legionary": 4}}).spawnUnit(P, "legionary", 700, 700).variantRarity, 4)
	var m := realm("legion", {"commander": "marshal"}).spawnUnit(P, "legionary", 700, 700)
	eq(m.armor, C.UNITS.legionary.armor + 1)
	eq(m.maxHp, JS.round(C.UNITS.legionary.hp * 1.08))
	var b := realm("legion", {"boons": [{"id": "whetstones", "rarity": 0, "age": 0}]}).spawnUnit(P, "legionary", 700, 700)
	eq(b.attack, JS.round(C.UNITS.legionary.attack * 1.08))


func test_every_faction_and_oath_unit_is_in_the_war_chests() -> void:
	var units := {}
	for v in C.CATALOG:
		units[v.unitId] = true
	for fid in C.FACTIONS:
		var f: Dictionary = C.FACTIONS[fid]
		for u in f.replaces.values() + f.extra:
			check(units.has(u), u)
	for u in ["sworn_blade", "lancer", "ranger", "halberdier", "great_bombard", "royal_guard"]:
		check(units.has(u), u)


# ---- Idle production time ----

func idleBoot() -> World:
	return SimTestUtil.build({"seed": 4242, "alliances": [0, 1]})


func test_counts_a_town_centre_producing_nothing() -> void:
	var w := idleBoot()
	run(w, 10)
	var s: Dictionary = w.player(P).stats
	check(s.tcSeconds > 8)
	eq(s.idleTcTime, s.tcSeconds)


func test_stops_counting_when_something_is_queued_and_resumes_after() -> void:
	var w := idleBoot()
	run(w, 6)
	var before: float = w.player(P).stats.idleTcTime
	w.player(P).resources.food = 500.0
	eq(w.trainUnit(P, SimTestUtil.tcOf(w).id, "villager"), true)
	run(w, 5)
	var during: float = w.player(P).stats.idleTcTime
	check(during - before < 3)
	run(w, 40)
	check(w.player(P).stats.idleTcTime > during + 5)


func test_never_counts_a_building_still_going_up() -> void:
	var w := idleBoot()
	var p = w.player(P)
	p.resources = {"food": 900.0, "wood": 900.0, "gold": 900.0}
	var tc := SimTestUtil.tcOf(w)
	var b := SimTestUtil.placeNear(w, P, "barracks", tc.x, tc.y, 120, 500, 40, 12, false)
	check(b != null)
	check(b.buildState != T.BuildState.Done)
	var before: float = p.stats.productionSeconds
	run(w, 8)
	eq(p.stats.productionSeconds, before)
	eq(p.stats.idleProductionTime, 0)


func test_idle_ledgers_stay_separate_and_bounded() -> void:
	var w := idleBoot()
	run(w, 20)
	for t in [P, E]:
		var s: Dictionary = w.player(t).stats
		check(s.tcSeconds > 0)
		eq(s.productionSeconds, 0)
		check(s.idleTcTime <= s.tcSeconds)
		check(s.idleProductionTime <= s.productionSeconds)
