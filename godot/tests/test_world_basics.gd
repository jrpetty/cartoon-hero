extends TestCase
# Ported from game/src/sim/world.test.ts: setup, determinism, economy,
# construction, combat, nomad start, farms, heroes, fog, victory, rarity and
# abilities. (The TS builds its map with generateMap("open_plains", seed); here
# the recorded Open Plains fixture map stands in, so spots are found the same
# way — by trying a few — rather than assumed.)



func makeWorld(seed: int = 1234) -> World:
	return SimTestUtil.build({"seed": seed})


func run(w: World, seconds: float) -> void:
	SimTestUtil.run(w, seconds)


func villagers(w: World, team: int = 0) -> Array:
	return w.entitiesOf(team, T.Kind.Unit).filter(func(e): return e.type == "villager")


# ---- World setup ----

func test_spawns_both_bases_with_villagers_and_a_town_center() -> void:
	var w := makeWorld()
	for team in [0, 1]:
		eq(w.countOf(team, "town_center"), 1, "town centres")
		eq(w.countOf(team, "villager"), 3, "villagers")
		var p = w.player(team)
		eq(p.popUsed, 3, "popUsed")
		eq(p.popCap, 10, "popCap") # town center provides 10
		eq(p.resources.food, C.START_RESOURCES.food)
		eq(p.resources.wood, C.START_RESOURCES.wood)
		eq(p.resources.gold, C.START_RESOURCES.gold)


func test_is_deterministic_same_seed_same_state_after_30s() -> void:
	var hash := func(w: World) -> int:
		var h := 0
		for e: Entity in w.entities:
			if not e.alive:
				continue
			h = JS.i32(h * 31 + JS.i32(int(e.x * 7919)) + JS.i32(int(e.y * 104729)) + e.id)
		return h
	var a := makeWorld(777)
	var b := makeWorld(777)
	run(a, 30)
	run(b, 30)
	eq(hash.call(a), hash.call(b), "state hash")
	eq(Commands.worldChecksum(a), Commands.worldChecksum(b), "checksum")


# ---- Economy ----

func test_villagers_gather_wood_and_deposit_it() -> void:
	var w := makeWorld()
	var start: float = w.player(0).resources.wood
	var tree = SimTestUtil.find(w, func(e): return e.alive and e.type == "tree")
	w.issueGather(villagers(w).map(func(v): return v.id), tree.id)
	run(w, 120)
	check(w.player(0).resources.wood > start, "wood went up")
	check(w.player(0).stats.gathered > 0, "gathered counted")


func test_trains_a_villager_cost_and_pop() -> void:
	var w := makeWorld()
	var p = w.player(0)
	var tc := SimTestUtil.tcOf(w)
	var foodBefore: float = p.resources.food
	eq(w.trainUnit(0, tc.id, "villager"), true, "trainUnit")
	eq(p.resources.food, foodBefore - C.UNITS.villager.cost.food, "paid")
	run(w, C.UNITS.villager.buildTime + 2)
	eq(w.countOf(0, "villager"), 4, "villagers")
	eq(p.popUsed, 4, "popUsed")


func test_blocks_training_when_population_capped() -> void:
	var w := makeWorld()
	var p = w.player(0)
	p.popUsed = p.popCap
	eq(w.trainUnit(0, SimTestUtil.tcOf(w).id, "villager"), false)


# ---- Construction & placement ----

func test_places_a_house_builds_it_and_pop_cap_rises() -> void:
	var w := makeWorld()
	var p = w.player(0)
	var start: Dictionary = w.map.starts[0]
	var before: float = p.popCap
	var woodBefore: float = p.resources.wood
	var house = null
	for d in [[150, 150], [-150, 150], [150, -150], [-150, -150], [210, 0], [0, -210], [-210, 0]]:
		house = w.placeBuilding(0, "house", start.x + d[0], start.y + d[1])
		if house != null:
			break
	check(house != null, "no spot for a house")
	eq(p.resources.wood, woodBefore - C.BUILDINGS.house.cost.wood, "paid")
	w.issueBuildRepair(villagers(w).map(func(v): return v.id), house.id)
	run(w, 40)
	eq(p.popCap, before + C.BUILDINGS.house.popProvided, "popCap")


func test_rejects_placement_on_top_of_an_existing_building() -> void:
	var w := makeWorld()
	var tc: Entity = w.entitiesOf(0, T.Kind.Building)[0]
	eq(w.placeBuilding(0, "house", tc.x, tc.y), null)


func test_villagers_gather_a_berry_that_sits_next_to_a_mill() -> void:
	var w := makeWorld()
	var p = w.player(0)
	var TILE := 32.0
	var berry := w.spawnResource("berries", 1600, 1600, 150)
	var mill = w.placeBuilding(0, "mill", berry.x + TILE * 3, berry.y)
	if mill == null: # the fixture map has a tree there; a mill a little further out
		mill = SimTestUtil.placeNear(w, 0, "mill", berry.x, berry.y, 96, 300, 32, 16, false)
	check(mill != null, "mill placed")
	mill.buildState = 0
	run(w, 0.1) # settle so the spatial index sees the new nodes
	var at = w.resourceAt(berry.x + 2, berry.y + 2, 0)
	eq(at.id if at != null else -1, berry.id, "resourceAt picks the berry")
	var vill := w.spawnUnit(0, "villager", berry.x, berry.y + TILE * 2)
	var food0: float = p.resources.food
	w.issueGather([vill.id], berry.id)
	run(w, 30)
	check(p.resources.food > food0, "food banked")


# ---- Combat ----

func test_applies_armor_and_never_less_than_1_damage() -> void:
	var w := makeWorld()
	var u := w.spawnUnit(1, "militia", 600, 600)
	var hp := u.hp
	w.dealDamage(0, u, 5, "militia") # 5 raw vs 1 armor => 4
	eq(u.hp, hp - 4)
	w.dealDamage(0, u, 1, "militia") # floor at 1
	eq(u.hp, hp - 5)


func test_the_counter_triangle_holds_in_duels() -> void:
	var w := makeWorld()
	var duel := func(a: String, b: String) -> String:
		var ua := w.spawnUnit(0, a, 1600, 1600)
		var ub := w.spawnUnit(1, b, 1650, 1600)
		w.issueAttack([ua.id], ub.id)
		w.issueAttack([ub.id], ua.id)
		for i in w.SIM_HZ * 60:
			w.tick()
			if not ua.alive or not ub.alive:
				break
		var winner := a if ua.alive else b
		ua.alive = false
		ub.alive = false
		return winner
	eq(duel.call("spearman", "knight"), "spearman")
	eq(duel.call("knight", "archer"), "knight")
	eq(duel.call("militia", "archer"), "militia")
	eq(duel.call("skirmisher", "archer"), "skirmisher")


func test_units_gain_veterancy_from_kills() -> void:
	var w := makeWorld()
	var vet := w.spawnUnit(0, "militia", 1600, 1600)
	var baseHp := vet.maxHp
	var baseAtk := vet.attack
	for n in 6:
		var foe := w.spawnUnit(1, "villager", 1612, 1600)
		foe.maxHp = 5
		foe.hp = 5
		w.issueAttack([vet.id], foe.id)
		var i := 0
		while i < w.SIM_HZ * 4 and foe.alive:
			w.tick()
			i += 1
	eq(vet.vetKills, 6, "vetKills")
	check(vet.veterancy >= 2, "veterancy >= 2")
	check(vet.maxHp > baseHp, "maxHp grew")
	check(vet.attack > baseAtk, "attack grew")


func test_attack_moving_units_fight_back_against_a_ranged_attacker() -> void:
	var w := makeWorld()
	var m := w.spawnUnit(0, "militia", 1000, 1400)
	var archer := w.spawnUnit(1, "archer", 1280, 1400)
	w.issueAttack([archer.id], m.id)
	w.issueFormationMove([m.id], 1700, 1400, false, true)
	var engaged := false
	for i in w.SIM_HZ * 20:
		w.tick()
		if m.order.kind == T.OrderKind.Attack:
			engaged = true
		if not archer.alive:
			break
	check(engaged, "engaged")
	check(not archer.alive, "archer dead")


func test_a_unit_ordered_to_attack_a_building_destroys_it() -> void:
	var w := makeWorld()
	var house = null
	for p in [[1500, 1500], [1500, 1400], [1600, 1500], [1400, 1500], [1500, 1600]]:
		house = w.placeBuilding(1, "house", p[0], p[1])
		if house != null:
			break
	check(house != null, "house placed")
	house.buildState = 0
	house.hp = 60
	var ram := w.spawnUnit(0, "ram", house.x - 100, house.y)
	w.issueAttack([ram.id], house.id)
	run(w, 30)
	check(not house.alive, "house razed")
	eq(w.player(0).stats.buildingsRazed, 1, "buildingsRazed")


# ---- Nomad start ----

func test_nomad_spawns_scattered_villagers_no_town_center_extra_wood() -> void:
	var w := SimTestUtil.build({"seed": 99, "nomad": true})
	eq(w.countOf(0, "town_center"), 0, "no TC")
	var vills := villagers(w)
	check(vills.size() >= 4, "4+ villagers")
	var spread := 0.0
	for v in vills:
		spread = maxf(spread, JS.hypot(v.x - vills[0].x, v.y - vills[0].y))
	check(spread > 40, "spread out")
	check(w.player(0).resources.wood >= 350, "wood for a TC")


# ---- Farms ----

func _farm(w: World) -> Entity:
	for f in [[1500, 1500], [1500, 1400], [1600, 1500], [1400, 1500], [1500, 1600]]:
		var farm = w.placeBuilding(0, "farm", f[0], f[1])
		if farm != null:
			return farm
	return null


func test_only_one_villager_works_a_farm() -> void:
	var w := makeWorld()
	var farm := _farm(w)
	check(farm != null, "farm placed")
	farm.buildState = 0
	var vs: Array = []
	for i in 4:
		vs.append(w.spawnUnit(0, "villager", 1480 + i * 10, 1560).id)
	w.issueGather(vs, farm.id)
	run(w, 12)
	check(vs.has(farm.farmWorker), "nobody claimed the plot")
	var gatherTarget := func(id: int) -> int:
		var e = w.byId.get(id)
		if e.order.kind == T.OrderKind.Return:
			return e.order.queue[0].target if e.order.queue.size() > 0 else -1
		return e.order.target
	var others := vs.filter(func(id): return id != farm.farmWorker)
	eq(others.size(), 3)
	for id in others:
		check(gatherTarget.call(id) != farm.id, "a second villager is working the same farm")


func test_units_can_walk_across_a_farm() -> void:
	var w := makeWorld()
	var farm := _farm(w)
	check(farm != null)
	farm.buildState = 0
	eq(w.grid.isBlockedWorld(farm.x, farm.y), false, "farm tiles passable")
	var u := w.spawnUnit(0, "knight", farm.x - 140, farm.y)
	w.issueMove([u.id], farm.x + 140, farm.y)
	run(w, 12)
	check(u.x > farm.x, "crossed the farm")


func test_reseeds_a_spent_farm_on_the_same_ground_and_charges_wood() -> void:
	var w := makeWorld()
	var p = w.player(0)
	var farm := _farm(w)
	farm.buildState = 0
	farm.amount = 2
	var at := [farm.x, farm.y]
	var v := w.spawnUnit(0, "villager", farm.x + 30, farm.y)
	w.issueGather([v.id], farm.id)
	p.resources.wood = 500
	var woodBefore: float = p.resources.wood
	run(w, 8)
	eq(farm.alive, false, "the spent plot is still standing")
	var fresh = SimTestUtil.find(w, func(e): return e.alive and e.kind == T.Kind.Building and e.type == "farm" and e.id != farm.id)
	check(fresh != null, "nothing was re-seeded")
	if fresh != null:
		check(JS.hypot(fresh.x - at[0], fresh.y - at[1]) < 1, "same ground")
	check(p.resources.wood < woodBefore, "re-seeding was free")


func test_leaves_the_field_bare_when_auto_reseed_is_off() -> void:
	var w := makeWorld()
	var p = w.player(0)
	p.autoReseed = false
	var farm := _farm(w)
	farm.buildState = 0
	farm.amount = 2
	var v := w.spawnUnit(0, "villager", farm.x + 30, farm.y)
	w.issueGather([v.id], farm.id)
	p.resources.wood = 500
	run(w, 8)
	eq(farm.alive, false)
	eq(SimTestUtil.find(w, func(e): return e.alive and e.kind == T.Kind.Building and e.type == "farm"), null, "no farm")
	check(v.order.kind != T.OrderKind.Idle, "the farmer was abandoned")


func test_rejects_placing_another_building_on_top_of_a_farm() -> void:
	var w := makeWorld()
	var farm := _farm(w)
	check(farm != null)
	eq(w.placeBuilding(0, "house", farm.x, farm.y), null)


func test_a_villager_working_a_farm_stands_on_the_plot() -> void:
	var w := makeWorld()
	var farm := _farm(w)
	farm.buildState = 0
	farm.amount = 999999
	var v := w.spawnUnit(0, "villager", farm.x + 50, farm.y + 40)
	w.issueGather([v.id], farm.id)
	run(w, 6)
	check(JS.hypot(v.x - farm.x, v.y - farm.y) < farm.radius, "on the plot")
	check(v.carry > 0, "harvesting")


# ---- Hero leveling ----

func test_hero_levels_modestly_and_does_not_heal_on_level_up() -> void:
	var w := makeWorld()
	var hero := w.spawnUnit(0, "hero", 1000, 1000)
	var baseHp := hero.maxHp
	var baseAtk := hero.attack
	for n in 6:
		var foe := w.spawnUnit(1, "villager", 1012, 1000)
		foe.maxHp = 5
		foe.hp = 5
		w.issueAttack([hero.id], foe.id)
		var i := 0
		while i < w.SIM_HZ * 4 and foe.alive:
			w.tick()
			i += 1
	eq(hero.heroLevel, 1, "heroLevel")
	eq(hero.maxHp, baseHp + 24, "maxHp")
	eq(hero.attack, baseAtk + 2, "attack")
	check(hero.hp < hero.maxHp, "no free heal")


# ---- Fog of war ----

func test_enemy_base_starts_unexplored_and_own_base_is_visible() -> void:
	var w := makeWorld()
	var own: Dictionary = w.map.starts[0]
	var foe: Dictionary = w.map.starts[1]
	eq(w.fogAt(0, own.x, own.y), 2)
	eq(w.fogAt(0, foe.x, foe.y), 0)


func test_reveal_all_shows_the_whole_map() -> void:
	var w := makeWorld()
	var foe: Dictionary = w.map.starts[1]
	eq(w.fogAt(0, foe.x, foe.y), 0)
	w.revealAll = true
	eq(w.fogAt(0, foe.x, foe.y), 2)
	eq(w.visibleTo(0, w.entitiesOf(1, T.Kind.Unit)[0]), true)


func test_a_scout_reveals_terrain_and_it_stays_explored() -> void:
	var w := makeWorld()
	var mx := w.worldW / 2.0
	var my := w.worldH / 2.0
	var scout := w.spawnUnit(0, "knight", mx, my)
	run(w, 1)
	eq(w.fogAt(0, mx, my), 2)
	scout.x = 200
	scout.y = 200
	run(w, 1)
	eq(w.fogAt(0, mx, my), 1)


# ---- Victory ----

func test_a_team_with_villagers_but_no_buildings_is_not_defeated() -> void:
	var w := makeWorld()
	for e in w.entitiesOf(1, T.Kind.Building):
		w.dealDamage(0, e, 999999, "test")
	run(w, 2)
	eq(w.player(1).defeated, false)
	eq(w.winner, null)


func test_declares_a_winner_once_a_side_has_no_buildings_and_no_villagers() -> void:
	var w := makeWorld()
	for e in w.entitiesOf(1):
		if e.kind == T.Kind.Building or (e.kind == T.Kind.Unit and C.UNITS[e.type].get("canBuild", false)):
			w.dealDamage(0, e, 999999, "test")
	run(w, 2)
	eq(w.winner, 0, "winner")
	eq(w.player(1).defeated, true)


func test_units_get_stronger_stats_and_ability_at_higher_rarity() -> void:
	var w := makeWorld()
	w.player(0).loadout["knight"] = 5
	w.player(1).loadout["knight"] = 0
	var hi := w.spawnUnit(0, "knight", 1000, 1000)
	var lo := w.spawnUnit(1, "knight", 1100, 1000)
	check(hi.maxHp > lo.maxHp, "hp")
	check(hi.speed > lo.speed, "speed")
	w.useAbility([hi.id])
	w.useAbility([lo.id])
	check(hi.abilityCooldown < lo.abilityCooldown, "recharges faster")
	check(hi.abilityActive > lo.abilityActive, "lasts longer")


# ---- Unit abilities ----

func test_sanctuary_heals_wounded_allies() -> void:
	var w := makeWorld()
	var monk := w.spawnUnit(0, "monk", 1000, 1000)
	var ally := w.spawnUnit(0, "militia", 1040, 1000)
	w.dealDamage(1, ally, 40, "militia")
	var hurt := ally.hp
	check(hurt < ally.maxHp)
	run(w, 0.2)
	eq(w.useAbility([monk.id]), 1)
	check(ally.hp > hurt, "healed")


func test_caltrops_slows_nearby_enemies_but_not_allies() -> void:
	var w := makeWorld()
	var skirm := w.spawnUnit(0, "skirmisher", 1000, 1000)
	var foe := w.spawnUnit(1, "knight", 1050, 1000)
	var friend := w.spawnUnit(0, "militia", 1050, 1010)
	run(w, 0.2)
	w.useAbility([skirm.id])
	check(foe.slowTimer > 0, "foe slowed")
	eq(friend.slowTimer, 0, "friend untouched")


func test_war_cry_rallies_nearby_allies() -> void:
	var w := makeWorld()
	var leader := w.spawnUnit(0, "militia", 1000, 1000)
	var ally := w.spawnUnit(0, "spearman", 1060, 1000)
	run(w, 0.2)
	w.useAbility([leader.id])
	check(leader.rallyTimer > 0)
	check(ally.rallyTimer > 0)


func test_arrow_volley_strikes_everything_around_the_target() -> void:
	var w := makeWorld()
	var archer := w.spawnUnit(0, "archer", 1000, 1000)
	var a := w.spawnUnit(1, "militia", 1000, 1100)
	var b := w.spawnUnit(1, "militia", 1030, 1110)
	run(w, 0.2)
	w.issueAttack([archer.id], a.id)
	w.useAbility([archer.id])
	check(a.hp < a.maxHp)
	check(b.hp < b.maxHp)


func test_an_ability_cannot_be_used_again_until_off_cooldown() -> void:
	var w := makeWorld()
	var knight := w.spawnUnit(0, "knight", 1000, 1000)
	eq(w.useAbility([knight.id]), 1)
	eq(w.useAbility([knight.id]), 0)
	check(knight.abilityActive > 0)


func test_heroic_cleave_hits_all_foes_and_rallies_allies() -> void:
	var w := makeWorld()
	var hero := w.spawnUnit(0, "hero", 1000, 1000)
	var foeA := w.spawnUnit(1, "militia", 1040, 1000)
	var foeB := w.spawnUnit(1, "militia", 1000, 1050)
	var ally := w.spawnUnit(0, "spearman", 1050, 1010)
	run(w, 0.2)
	eq(w.useAbility([hero.id]), 1)
	check(foeA.hp < foeA.maxHp)
	check(foeB.hp < foeB.maxHp)
	check(ally.rallyTimer > 0)


# ---- Hero units ----

func test_trains_one_champion_per_player() -> void:
	var w := makeWorld()
	var tc := SimTestUtil.tcOf(w)
	w.player(0).resources.food = 999
	w.player(0).resources.gold = 999
	eq(w.trainUnit(0, tc.id, "hero"), true)
	eq(w.trainUnit(0, tc.id, "hero"), false, "already pending")
	run(w, C.UNITS.hero.buildTime + 2)
	eq(w.countOf(0, "hero"), 1)
	eq(w.player(0).heroState, "alive")
	eq(w.trainUnit(0, tc.id, "hero"), false, "already fielded")


func test_hero_levels_up_from_nearby_kills() -> void:
	var w := makeWorld()
	var hero := w.spawnUnit(0, "hero", 1000, 1000)
	var baseHp := hero.maxHp
	var baseAtk := hero.attack
	run(w, 0.2)
	for i in 5:
		var foe := w.spawnUnit(1, "militia", 1000 + i * 8, 1010)
		w.dealDamage(0, foe, 9999, "hero", hero.id)
	eq(hero.heroLevel, 1)
	check(hero.maxHp > baseHp)
	check(hero.attack > baseAtk)


func test_hero_respawns_at_the_town_center() -> void:
	var w := makeWorld()
	var tc := SimTestUtil.tcOf(w)
	w.player(0).resources.food = 999
	w.player(0).resources.gold = 999
	w.trainUnit(0, tc.id, "hero")
	run(w, C.UNITS.hero.buildTime + 2)
	var hero = SimTestUtil.find(w, func(e): return e.alive and e.team == 0 and e.type == "hero")
	w.dealDamage(1, hero, 99999, "militia")
	eq(w.player(0).heroState, "respawning")
	eq(w.countOf(0, "hero"), 0)
	run(w, 67)
	eq(w.player(0).heroState, "alive")
	eq(w.countOf(0, "hero"), 1)
