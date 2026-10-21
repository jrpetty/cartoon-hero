extends TestCase
# Ported from building_place.test.ts, placement_qol.test.ts, multitrain.test.ts,
# ageup.test.ts, bugfixes.test.ts and the world half of gridsnap.test.ts:
# where buildings land, why a spot is refused, queues, age requirements.

const P := 0
const TILE := 32.0


func richWorld(seed: int) -> World:
	var w := SimTestUtil.build({"seed": seed})
	var p = w.player(P)
	p.resources.wood = 99999.0
	p.resources.food = 99999.0
	p.resources.gold = 99999.0
	return w


## A spot near the map centre that the engine reports as placeable.
func clearSpot(w: World, type: String) -> Array:
	for r in 40:
		var cx := w.worldW / 2.0 + (r % 8) * TILE * 3 + 7
		var cy := w.worldH / 2.0 + (r / 8) * TILE * 3 + 5
		if w.canPlace(P, type, cx, cy):
			return [cx, cy]
	return []


# ---- building_place.test.ts ----

func test_places_an_odd_footprint_building_exactly_on_the_snapped_tile() -> void:
	var w := richWorld(123)
	var spot := clearSpot(w, "town_center")
	check(spot.size() == 2, "no spot")
	var expX := JS.round(spot[0] / TILE) * TILE + TILE / 2.0
	var expY := JS.round(spot[1] / TILE) * TILE + TILE / 2.0
	# The single snap floors to the cursor's cell; the TS test's expectation
	# reads it as round(), which agrees for these spots (+7 / +5 into a cell).
	var e = w.placeBuilding(P, "town_center", spot[0], spot[1])
	check(e != null, "placed")
	eq(e.x, GridSnap.snapBuilding(spot[0], 3))
	eq(e.y, GridSnap.snapBuilding(spot[1], 3))
	eq(e.x, expX)
	eq(e.y, expY)


# ---- placement_qol.test.ts ----

func test_names_the_reason_a_building_wont_go_there() -> void:
	var w := SimTestUtil.build({"seed": 12, "commanders": ["", ""]})
	var tc := SimTestUtil.tcOf(w)
	var p = w.player(P)
	var castle = null
	for id in C.BUILDINGS:
		if C.BUILDINGS[id].age >= 2:
			castle = C.BUILDINGS[id]
			break
	check(str(w.placementProblem(P, castle.id, tc.x + 300, tc.y)).begins_with("Needs the "), "age")
	var withReq = null
	for id in C.BUILDINGS:
		var b: Dictionary = C.BUILDINGS[id]
		if b.get("requires") != null and b.age <= 1:
			withReq = b
			break
	if withReq != null:
		p.age = int(withReq.age)
		check(str(w.placementProblem(P, withReq.id, tc.x + 300, tc.y)).begins_with("Needs a "), "requires")
		p.age = 0
	p.resources = {"food": 0.0, "wood": 3.0, "gold": 0.0}
	var money = w.placementProblem(P, "house", tc.x + 300, tc.y)
	check(RegEx.create_from_string("^Need \\d+ more wood$").search(str(money)) != null, "money: %s" % money)
	p.resources = {"food": 9999.0, "wood": 9999.0, "gold": 9999.0}
	check(str(w.placementProblem(P, "house", tc.x, tc.y)).begins_with("Blocked"), "on top of the Town Center")
	w.tick() # units join the spatial index on the first tick
	var vil: Entity = w.entitiesOf(P, T.Kind.Unit)[0]
	var why := str(w.placementProblem(P, "house", vil.x, vil.y))
	check(why == "Units are standing there" or why.begins_with("Blocked"), "units: %s" % why)
	var farm = null
	var r := 180
	while farm == null and r < 700:
		for a in 12:
			farm = w.placeBuilding(P, "farm", tc.x + cos(a / 2.0) * r, tc.y + sin(a / 2.0) * r)
			if farm != null:
				break
		r += 40
	check(farm != null, "farm")
	eq(w.placementProblem(P, "house", farm.x, farm.y), "Overlaps a farm")


func test_agrees_with_the_sim_at_every_spot() -> void:
	var w := SimTestUtil.build({"seed": 33, "commanders": ["", ""]})
	var p = w.player(P)
	p.resources = {"food": 5000.0, "wood": 5000.0, "gold": 5000.0}
	var tc := SimTestUtil.tcOf(w)
	var agreed := 0
	for i in 400:
		var x := tc.x + ((i * 97) % 900) - 450
		var y := tc.y + ((i * 61) % 900) - 450
		var type: String = ["house", "barracks", "lumber_camp", "farm", "palisade"][i % 5]
		var problem = w.placementProblem(P, type, x, y)
		var placed = w.placeBuilding(P, type, x, y)
		if (placed != null) != (problem == null):
			failures.append("%s at %s,%s: said \"%s\"" % [type, x, y, problem])
		agreed += 1
	eq(agreed, 400)


func test_cancel_refunds_and_the_next_item_starts_fresh() -> void:
	var w := SimTestUtil.build({"seed": 12, "commanders": ["", ""]})
	var tc := SimTestUtil.tcOf(w)
	var p = w.player(P)
	p.resources = {"food": 1000.0, "wood": 1000.0, "gold": 1000.0}
	for i in 3:
		w.trainUnit(P, tc.id, "villager")
	var before: Dictionary = p.resources.duplicate()
	for i in 20:
		w.tick()
	Commands.applyCommand(w, {"t": "cancel", "team": P, "buildingId": tc.id, "index": 0})
	eq(tc.productionQueue.size(), 2)
	eq(p.resources.food, before.food + C.UNITS.villager.cost.food)
	check(tc.productionTime > C.UNITS.villager.buildTime * 0.9, "the next one starts from the beginning")
	Commands.applyCommand(w, {"t": "cancel", "team": P, "buildingId": tc.id, "index": 1})
	eq(tc.productionQueue.size(), 1)


func test_wont_cancel_someone_elses() -> void:
	var w := SimTestUtil.build({"seed": 12})
	var tc := SimTestUtil.tcOf(w)
	w.player(P).resources.food = 500.0
	w.trainUnit(P, tc.id, "villager")
	Commands.applyCommand(w, {"t": "cancel", "team": 1, "buildingId": tc.id, "index": 0})
	eq(tc.productionQueue.size(), 1)


func test_can_call_off_an_age_advance_and_its_oath() -> void:
	var w := SimTestUtil.build({"seed": 12})
	var tc := SimTestUtil.tcOf(w)
	var p = w.player(P)
	p.resources = {"food": 5000.0, "wood": 5000.0, "gold": 5000.0}
	var next: Dictionary = C.AGES[1]
	for id in (next.requiresAny as Array).slice(0, int(next.requiresCount)):
		SimTestUtil.placeNear(w, P, id, tc.x, tc.y, 200, 700, 40, 12)
	eq(w.research(P, tc.id, "age:sword"), true, "research")
	var spent: Dictionary = p.resources.duplicate()
	w.cancelProduction(P, tc.id, tc.productionQueue.find("a:age"))
	eq(p.pendingOath, null)
	eq(p.resources.food, spent.food + next.cost.food)


func test_tells_the_player_when_a_finished_unit_had_no_room() -> void:
	var w := SimTestUtil.build({"seed": 12})
	var tc := SimTestUtil.tcOf(w)
	var p = w.player(P)
	p.resources.food = 1000.0
	w.trainUnit(P, tc.id, "villager")
	p.popCap = p.popUsed # full up while it trains
	var saw := false
	var i := 0
	while i < 20 * 40 and not saw:
		w.tick()
		saw = w.drainEvents().any(func(e): return e.kind == "popcap" and e.team == P)
		i += 1
	check(saw, "popcap event")


# ---- multitrain.test.ts ----

func test_queues_a_unit_into_every_selected_stable() -> void:
	var w := SimTestUtil.build({"seed": 1})
	var p = w.player(P)
	p.age = 2
	p.resources = {"food": 9999.0, "wood": 9999.0, "gold": 9999.0}
	var stables: Array = []
	for i in 3:
		stables.append(w.spawnBuilding(P, "stable", 800 + i * 70, 800, true))
	for s in stables:
		Commands.applyCommand(w, {"t": "train", "team": P, "buildingId": s.id, "unit": "horseman"})
	var combined := 0
	for s in stables:
		eq(s.productionQueue.filter(func(q): return q == "u:horseman").size(), 1)
		combined += s.productionQueue.size()
	eq(combined, 3)


func test_wont_queue_into_a_building_you_dont_own() -> void:
	var w := SimTestUtil.build({"seed": 2})
	w.player(1).age = 2
	var enemyStable := w.spawnBuilding(1, "stable", 1200, 1200, true)
	Commands.applyCommand(w, {"t": "train", "team": P, "buildingId": enemyStable.id, "unit": "horseman"})
	eq(enemyStable.productionQueue.size(), 0)


# ---- ageup.test.ts ----

func _build(w: World, type: String) -> void:
	var start: Dictionary = w.map.starts[P]
	w.player(P).resources = {"food": 9999.0, "wood": 9999.0, "gold": 9999.0}
	var b := SimTestUtil.placeNear(w, P, type, start.x, start.y, 140, 520, 36, 16)
	check(b != null, "no spot for " + type)


func test_banner_needs_any_2_of_a_set() -> void:
	eq(C.AGES[1].requiresCount, 2)
	check(C.AGES[1].requiresAny.size() >= 3)
	var w := SimTestUtil.build({"seed": 1234})
	eq(w.ageRequirementMet(P, 1), false)
	_build(w, "barracks")
	eq(w.ageRequirementMet(P, 1), false, "1/2")
	_build(w, "mill")
	eq(w.ageRequirementMet(P, 1), true, "2/2")


func test_a_pure_economy_path_can_reach_banner() -> void:
	var w := SimTestUtil.build({"seed": 1234})
	_build(w, "mill")
	_build(w, "lumber_camp")
	eq(w.ageRequirementMet(P, 1), true)
	w.player(P).resources.food = 999.0
	eq(w.research(P, SimTestUtil.tcOf(w).id, "age"), true)


func test_research_is_blocked_until_the_requirement_is_met() -> void:
	var w := SimTestUtil.build({"seed": 1234})
	w.player(P).resources.food = 999.0
	var tc := SimTestUtil.tcOf(w)
	eq(w.research(P, tc.id, "age"), false)
	_build(w, "barracks")
	_build(w, "mining_camp")
	eq(w.research(P, tc.id, "age"), true)


# ---- bugfixes.test.ts ----

func test_no_friendly_fire() -> void:
	var allied := SimTestUtil.build({"seed": 1, "alliances": [0, 0]})
	var a := allied.spawnUnit(P, "militia", 800, 800)
	var ally := allied.spawnUnit(1, "militia", 820, 800)
	allied.issueAttack([a.id], ally.id)
	check(a.order.kind != T.OrderKind.Attack, "attacked an ally")
	var war := SimTestUtil.build({"seed": 1, "alliances": [0, 1]})
	var b := war.spawnUnit(P, "militia", 800, 800)
	var foe := war.spawnUnit(1, "militia", 820, 800)
	war.issueAttack([b.id], foe.id)
	eq(b.order.kind, T.OrderKind.Attack)


func test_a_second_town_center_cannot_queue_a_concurrent_age_up() -> void:
	var w := SimTestUtil.build({"seed": 2})
	var p = w.player(P)
	p.resources = {"food": 9999.0, "wood": 9999.0, "gold": 9999.0}
	w.spawnBuilding(P, "barracks", 700, 900, true)
	w.spawnBuilding(P, "mill", 760, 900, true)
	var tc1 := SimTestUtil.tcOf(w)
	var tc2 := w.spawnBuilding(P, "town_center", 1100, 1100, true)
	var foodBefore: float = p.resources.food
	eq(w.research(P, tc1.id, "age"), true)
	eq(w.research(P, tc2.id, "age"), false, "already aging")
	eq(p.resources.food, foodBefore - C.AGES[1].cost.food, "charged once")


func test_queued_age_up_uses_the_real_advance_time() -> void:
	var w := SimTestUtil.build({"seed": 3})
	eq(w.itemTime("a:age", 0), C.AGES[1].advanceTime)
	eq(w.itemTime("a:age", 1), C.AGES[2].advanceTime)


func test_pop_blocked_refund_matches_the_discounted_cost() -> void:
	var w := SimTestUtil.build({"seed": 4})
	var p = w.player(P)
	p.boon.unitCostMult = 0.5
	p.age = 2
	p.resources = {"food": 2000.0, "wood": 2000.0, "gold": 2000.0}
	var stable := w.spawnBuilding(P, "stable", 800, 800, true)
	var before: Dictionary = p.resources.duplicate()
	eq(w.trainUnit(P, stable.id, "horseman"), true)
	p.popUsed = p.popCap
	var i := 0
	while i < 3000 and stable.productionQueue.size() > 0:
		w.tick()
		i += 1
	eq(stable.productionQueue.size(), 0, "resolved")
	eq(p.resources.food, before.food)
	eq(p.resources.wood, before.wood)
	eq(p.resources.gold, before.gold)


# ---- gridsnap.test.ts: what actually gets built ----

func clearWorld() -> World:
	var w := SimTestUtil.build({"seed": 5, "alliances": [0, 1]})
	var p = w.player(P)
	p.resources = {"food": 9e5, "wood": 9e5, "gold": 9e5}
	p.age = 2
	for cy in range(18, 62):
		for cx in range(18, 62):
			w.grid.setBlocked(cx, cy, false)
	# Make the cleared block buildable ground too (the fixture map has water).
	for cy in range(18, 62):
		for cx in range(18, 62):
			w.terrain[cy * w.terrainCols + cx] = 0
	return w


func test_covers_the_cell_the_cursor_was_in_for_every_footprint() -> void:
	var w := clearWorld()
	for type in ["watch_tower", "house", "barracks", "town_center"]:
		var tiles: float = C.BUILDINGS[type].tiles
		for i in 6:
			var cx := 24 + i * 4
			var cy := 30
			for f in [[0.05, 0.05], [0.5, 0.5], [0.95, 0.95], [0.95, 0.05]]:
				var b = w.placeBuilding(P, type, (cx + f[0]) * TILE, (cy + f[1]) * TILE)
				if b == null:
					failures.append("%s could not be placed at %d,%d" % [type, cx, cy])
					continue
				var o := GridSnap.footprintOrigin(b.x, b.y, tiles)
				var covers: bool = cx >= o[0] and cx < o[0] + tiles and cy >= o[1] and cy < o[1] + tiles
				check(covers, "%s cursor %d,%d landed on %d.." % [type, cx, cy, o[0]])
				b.alive = false
				w.grid.stampFootprint(b.x, b.y, tiles, false)


func test_lands_exactly_where_the_ghost_said() -> void:
	var w := clearWorld()
	for type in ["watch_tower", "house", "town_center"]:
		var tiles: float = C.BUILDINGS[type].tiles
		var wx := 30.8 * TILE
		var wy := 41.3 * TILE
		var gx := GridSnap.snapBuilding(wx, tiles)
		var gy := GridSnap.snapBuilding(wy, tiles)
		var b = w.placeBuilding(P, type, gx, gy)
		check(b != null, "%s refused the ghost's own coordinates" % type)
		if b != null:
			eq(b.x, gx)
			eq(b.y, gy)
			b.alive = false
			w.grid.stampFootprint(b.x, b.y, tiles, false)


func test_blocks_exactly_the_cells_it_occupies() -> void:
	var w := clearWorld()
	var b = w.placeBuilding(P, "barracks", 30.6 * TILE, 30.6 * TILE)
	var tiles := int(C.BUILDINGS.barracks.tiles)
	var o := GridSnap.footprintOrigin(b.x, b.y, tiles)
	for y in range(-1, tiles + 1):
		for x in range(-1, tiles + 1):
			var inside := x >= 0 and x < tiles and y >= 0 and y < tiles
			eq(w.grid.isBlocked(o[0] + x, o[1] + y), inside, "cell %d,%d" % [o[0] + x, o[1] + y])
