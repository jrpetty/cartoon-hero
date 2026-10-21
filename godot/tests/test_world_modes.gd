extends TestCase
# Ported from modes.test.ts, nomad_modes.test.ts, trade.test.ts,
# bridge.test.ts, conceal.test.ts (the parts without the skirmish AI) and
# siege.test.ts (a representative subset — every breach is a full fight).

const P := 0
const E := 1
const TILE := 32.0


func run(w: World, seconds: float) -> void:
	for i in int(ceil(seconds * w.SIM_HZ)):
		w.tick()
		w.drainEvents()


# ---- Regicide / Survival / King of the Hill ----

func test_regicide_slaying_the_enemy_king_wins() -> void:
	var w := SimTestUtil.build({"seed": 11, "mode": "regicide"})
	var kings := w.entities.filter(func(e): return e.alive and e.type == "king")
	eq(kings.size(), 2)
	var enemyKing = kings.filter(func(k): return k.team == E)[0]
	w.dealDamage(P, enemyKing, 99999, "test")
	run(w, 2)
	eq(w.player(E).defeated, true)
	eq(w.winner, P)


func test_regicide_losing_your_own_king_loses() -> void:
	var w := SimTestUtil.build({"seed": 12, "mode": "regicide"})
	var myKing = SimTestUtil.find(w, func(e): return e.alive and e.type == "king" and e.team == P)
	w.dealDamage(E, myKing, 99999, "test")
	run(w, 2)
	eq(w.player(P).defeated, true)
	eq(w.winner, E)


func test_survival_sets_up_a_baseless_horde_and_spawns_waves() -> void:
	var w := SimTestUtil.build({"seed": 13, "map": ["crossroads", 99, 3], "alliances": [0, 0, 1], "mode": "survival"})
	eq(w.hordeTeam, 2)
	eq(w.countOf(2, "town_center"), 0, "horde has no base")
	eq(w.countOf(P, "town_center"), 1)
	run(w, 50)
	check(w.survivalWave >= 1, "a wave came")
	check(w.entitiesOf(2, T.Kind.Unit).size() > 0, "the wave is on the field")


func test_survival_clearing_all_waves_wins_being_wiped_out_loses() -> void:
	var w := SimTestUtil.build({"seed": 14, "econ": [1.0, 0.0], "alliances": [0, 1], "mode": "survival"})
	w.survivalWon = true
	run(w, 2)
	eq(w.winner, P)
	var w2 := SimTestUtil.build({"seed": 15, "econ": [1.0, 0.0], "alliances": [0, 1], "mode": "survival"})
	for e in w2.entitiesOf(P):
		w2.dealDamage(E, e, 99999, "test")
	run(w2, 2)
	eq(w2.player(P).defeated, true)


func test_koth_uncontested_holding_wins() -> void:
	var w := SimTestUtil.build({"seed": 16, "mode": "koth"})
	w.kothGoal = 4
	w.spawnUnit(P, "militia", w.hillX, w.hillY)
	run(w, 5)
	eq(w.winner, P)


func test_koth_an_enemy_on_the_hill_pauses_the_timer() -> void:
	var w := SimTestUtil.build({"seed": 17, "mode": "koth"})
	w.kothGoal = 3
	w.spawnUnit(P, "militia", w.hillX, w.hillY)
	var foe := w.spawnUnit(E, "militia", w.hillX + 10, w.hillY)
	run(w, 5)
	eq(w.winner, null, "contested")
	foe.alive = false
	run(w, 4)
	eq(w.winner, P)


# ---- Nomad + modes ----

func test_nomad_regicide_kill_their_king_to_win() -> void:
	var w := SimTestUtil.build({"seed": 31, "nomad": true, "mode": "regicide"})
	eq(w.countOf(P, "town_center"), 0)
	check(w.entitiesOf(P, T.Kind.Unit).filter(func(e): return e.type == "villager").size() > 0)
	eq(w.entities.filter(func(e): return e.alive and e.type == "king").size(), 2)
	var enemyKing = SimTestUtil.find(w, func(e): return e.alive and e.type == "king" and e.team == E)
	w.dealDamage(P, enemyKing, 99999, "test")
	run(w, 2)
	eq(w.winner, P)


func test_nomad_regicide_buildings_dont_decide_it() -> void:
	var w := SimTestUtil.build({"seed": 32, "nomad": true, "mode": "regicide"})
	for e in w.entitiesOf(E):
		if e.type != "king":
			w.dealDamage(P, e, 99999, "test")
	run(w, 2)
	eq(w.player(E).defeated, false)
	eq(w.winner, null)


func test_nomad_survival_waves_seek_the_villagers() -> void:
	var w := SimTestUtil.build({"seed": 33, "map": ["crossroads", 99, 3], "alliances": [0, 0, 1], "nomad": true, "mode": "survival"})
	eq(w.hordeTeam, 2)
	eq(w.countOf(P, "town_center"), 0)
	check(w.entitiesOf(P, T.Kind.Unit).size() > 0)
	run(w, 50)
	check(w.survivalWave >= 1)
	check(w.entitiesOf(2, T.Kind.Unit).size() > 0, "a wave is on the field")


func test_nomad_koth_holding_still_wins() -> void:
	var w := SimTestUtil.build({"seed": 34, "nomad": true, "mode": "koth"})
	w.kothGoal = 4
	w.spawnUnit(P, "militia", w.hillX, w.hillY)
	run(w, 5)
	eq(w.winner, P)


# ---- Trade carts ----

## A world with a Market at each end of a long line.
func trading(gapTiles: int = 40) -> Array:
	var w := SimTestUtil.build({"seed": 9, "alliances": [0, 1]})
	var p = w.player(P)
	p.age = 1
	p.resources = {"food": 5000.0, "wood": 5000.0, "gold": 5000.0}
	var cy := w.worldH / 2.0
	var home := w.spawnBuilding(P, "market", 400, cy, true)
	var far := w.spawnBuilding(P, "market", 400 + gapTiles * TILE, cy, true)
	return [w, p, home, far]


func test_a_cart_puts_itself_on_a_route() -> void:
	var t := trading()
	var w: World = t[0]
	var home: Entity = t[2]
	var cart := w.spawnUnit(P, "trade_cart", home.x + 30, home.y)
	eq(w.assignTradeRoute([cart.id]), true)
	eq(cart.order.kind, T.OrderKind.Trade)
	eq(cart.tradeHomeId, home.id)


func test_a_cart_picks_the_furthest_market() -> void:
	var t := trading()
	var w: World = t[0]
	var home: Entity = t[2]
	var cy := w.worldH / 2.0
	w.spawnBuilding(P, "market", home.x + 6 * TILE, cy, true)
	var far := w.spawnBuilding(P, "market", home.x + 60 * TILE, cy, true)
	var cart := w.spawnUnit(P, "trade_cart", home.x + 30, cy)
	w.assignTradeRoute([cart.id])
	eq(cart.order.target, far.id)


func test_refuses_a_route_with_only_one_market() -> void:
	var w := SimTestUtil.build({"seed": 9, "alliances": [0, 1]})
	var only := w.spawnBuilding(P, "market", 400, w.worldH / 2.0, true)
	var cart := w.spawnUnit(P, "trade_cart", only.x + 30, only.y)
	eq(w.assignTradeRoute([cart.id]), false)
	check(cart.order.kind != T.OrderKind.Trade)


func test_a_cart_earns_gold_on_arrival_and_turns_around() -> void:
	var t := trading(24)
	var w: World = t[0]
	var p = t[1]
	var home: Entity = t[2]
	var cart := w.spawnUnit(P, "trade_cart", home.x + 30, home.y)
	w.assignTradeRoute([cart.id])
	var before: float = p.resources.gold
	run(w, 90)
	check(p.resources.gold > before, "the cart never paid out")
	eq(cart.order.kind, T.OrderKind.Trade, "still trading")
	check(p.stats.gatheredBy.gold > 0, "trade gold counts as gathered")


func test_a_long_route_pays_more_than_a_short_one() -> void:
	var earned := func(gap: int) -> float:
		var t := trading(gap)
		var w: World = t[0]
		var p = t[1]
		var home: Entity = t[2]
		var cart := w.spawnUnit(P, "trade_cart", home.x + 30, home.y)
		w.assignTradeRoute([cart.id])
		var before: float = p.resources.gold
		run(w, 240)
		return p.resources.gold - before
	var short: float = earned.call(8)
	var long: float = earned.call(50)
	check(long > short, "short %s, long %s" % [short, long])


func test_a_cart_goes_idle_when_its_market_falls() -> void:
	var t := trading(24)
	var w: World = t[0]
	var home: Entity = t[2]
	var far: Entity = t[3]
	var cart := w.spawnUnit(P, "trade_cart", home.x + 30, home.y)
	w.assignTradeRoute([cart.id])
	run(w, 5)
	w.kill(far, E)
	run(w, 5)
	check(cart.order.kind != T.OrderKind.Trade, "kept trading with a razed Market")


func test_a_cart_is_unarmed() -> void:
	var t := trading()
	var w: World = t[0]
	var home: Entity = t[2]
	eq(w.spawnUnit(P, "trade_cart", home.x + 30, home.y).attack, 0)


# ---- A Market with prices that move ----

func test_market_pays_less_for_each_sale_and_charges_more_for_each_purchase() -> void:
	var t := trading()
	var w: World = t[0]
	var first: float = w.marketQuote(P, "wood").sell
	for i in 6:
		w.marketTrade(P, "sell_wood")
	check(w.marketQuote(P, "wood").sell < first, "sales slide")
	var firstBuy: float = w.marketQuote(P, "food").buy
	for i in 6:
		w.marketTrade(P, "buy_food")
	check(w.marketQuote(P, "food").buy > firstBuy, "purchases climb")


func test_market_moves_wood_and_food_independently_within_a_band() -> void:
	var t := trading()
	var w: World = t[0]
	for i in 6:
		w.marketTrade(P, "sell_wood")
	check(w.marketPrice(P, "wood") < 1)
	eq(w.marketPrice(P, "food"), 1)
	for i in 200:
		w.marketTrade(P, "sell_wood")
	check(w.marketPrice(P, "wood") >= 0.6, "band")
	check(w.marketQuote(P, "wood").sell > 0)


func test_market_drifts_back_toward_par() -> void:
	var t := trading()
	var w: World = t[0]
	for i in 8:
		w.marketTrade(P, "sell_wood")
	var depressed := w.marketPrice(P, "wood")
	check(depressed < 1)
	run(w, 180)
	var recovered := w.marketPrice(P, "wood")
	check(recovered > depressed, "%s -> %s" % [depressed, recovered])
	check(recovered <= 1)


func test_market_refuses_what_you_cannot_afford_or_without_a_market() -> void:
	var t := trading()
	var w: World = t[0]
	var p = t[1]
	p.resources = {"food": 0.0, "wood": 0.0, "gold": 0.0}
	eq(w.marketTrade(P, "sell_wood"), false)
	eq(w.marketTrade(P, "buy_food"), false)
	var w2 := SimTestUtil.build({"seed": 9, "alliances": [0, 1]})
	eq(w2.marketTrade(P, "sell_wood"), false)


func test_a_market_round_trip_never_makes_money() -> void:
	var t := trading()
	var w: World = t[0]
	var p = t[1]
	for steps in [0, 4, 10]:
		p.marketPressure = {"wood": 0.0, "food": 0.0}
		for i in steps:
			w.marketTrade(P, "sell_wood")
		var q := w.marketQuote(P, "wood")
		check(q.buy > q.sell, "at pressure step %d: sell %s, buy %s" % [steps, q.sell, q.buy])


# ---- Bridges ----

## A world with a band of shallows down the middle.
func forded(seed: int = 11, halfWidth: int = 2) -> Array:
	var w := SimTestUtil.build({"seed": seed, "alliances": [0, 1]})
	var t := PackedByteArray()
	t.resize(w.terrain.size())
	t.fill(0)
	w.terrain = t
	var midC := w.terrainCols / 2
	for cy in w.terrainRows:
		for dx in range(-halfWidth, halfWidth + 1):
			w.terrain[cy * w.terrainCols + midC + dx] = w.TERRAIN_SHALLOW
			w.grid.setBlocked(midC + dx, cy, false)
	return [w, midC * TILE + TILE / 2.0, (w.terrainRows / 2) * TILE]


func finish(w: World, b: Entity) -> void:
	b.buildState = 0
	w.onBuildingCompleted(b, {"onlyOnShallows": true, "popProvided": 0.0, "id": "bridge"}, true)


func test_bridges_go_on_shallows_only_and_fully() -> void:
	var f := forded()
	var w: World = f[0]
	eq(w.canPlace(P, "bridge", f[1], f[2]), true, "on shallows")
	eq(w.canPlace(P, "bridge", 400, f[2]), false, "on dry land")
	eq(w.canPlace(P, "bridge", f[1] + 3 * TILE, f[2]), false, "half on the bank")
	eq(w.canPlace(P, "house", f[1], f[2]), false, "a house in the ford")
	eq(w.canPlace(P, "house", 400, f[2]), true, "a house on grass")


func test_a_bridge_does_not_block_and_carries_traffic_only_once_finished() -> void:
	var f := forded()
	var w: World = f[0]
	var p = w.player(P)
	var before: float = p.resources.wood
	var b = w.placeBuilding(P, "bridge", f[1], f[2])
	check(b != null, "placed")
	check(p.resources.wood < before, "costs wood")
	eq(b.kind, T.Kind.Building)
	check(b.maxHp > 0)
	eq(w.grid.isBlockedWorld(b.x, b.y), false, "walkable")
	eq(w.bridgedAt(b.x, b.y), false, "an unfinished bridge already carried traffic")
	finish(w, b)
	eq(w.bridgedAt(b.x, b.y), true)
	w.kill(b, E)
	eq(b.alive, false)
	eq(w.bridgedAt(b.x, b.y), false, "the decking outlived the bridge")


func test_overlapping_bridges_keep_the_crossing_when_one_is_lost() -> void:
	var f := forded()
	var w: World = f[0]
	w.player(P).resources.wood = 5000.0
	var a = w.placeBuilding(P, "bridge", f[1], f[2])
	finish(w, a)
	var ghost := Entity.new()
	ghost.x = a.x
	ghost.y = a.y
	ghost.type = "bridge"
	w.stampBridge(ghost, true)
	w.kill(a, E)
	eq(w.bridgedAt(a.x, a.y), true, "one loss opened a hole in a standing crossing")


func test_a_bridge_makes_the_crossing_quicker() -> void:
	var cross := func(withBridge: bool) -> float:
		var f := forded(11, 2)
		var w: World = f[0]
		var fordX: float = f[1]
		var midY: float = f[2]
		w.player(P).resources.wood = 5000.0
		if withBridge:
			for dy in range(-8, 9):
				var b = w.placeBuilding(P, "bridge", fordX, midY + dy * 2 * TILE)
				if b != null:
					finish(w, b)
		var u := w.spawnUnit(P, "knight", fordX - 6 * TILE, midY)
		var goal := fordX + 6 * TILE
		w.issueMove([u.id], goal, midY)
		for i in 20 * 40:
			w.tick()
			w.drainEvents()
			if u.x >= goal - 20:
				return i
		return INF
	var bridged: float = cross.call(true)
	var waded: float = cross.call(false)
	check(bridged < INF, "the army never got across the bridge")
	check(bridged < waded, "the bridge saved no time at all (%s vs %s)" % [bridged, waded])


func test_a_bridge_is_walkable_like_a_farm() -> void:
	var f := forded()
	var w: World = f[0]
	var b = w.placeBuilding(P, "bridge", f[1], f[2])
	var u := w.spawnUnit(P, "knight", b.x - 140, b.y)
	w.issueMove([u.id], b.x + 140, b.y)
	run(w, 20)
	check(u.x > b.x, "a unit could not walk over a bridge")


func test_lakes_take_no_bridge() -> void:
	var w := SimTestUtil.build({"seed": 3, "alliances": [0, 1]})
	var t := PackedByteArray()
	t.resize(w.terrain.size())
	t.fill(0)
	w.terrain = t
	var midC := w.terrainCols / 2
	for cy in w.terrainRows:
		for dx in range(-2, 3):
			w.terrain[cy * w.terrainCols + midC + dx] = C.Terrain["Water"]
	eq(w.canPlace(P, "bridge", midC * TILE + TILE / 2.0, w.worldH / 2.0), false)


# ---- Hiding in the woods ----

## A world that is all grass except a block of woodland around the middle.
func woods(seed: int = 21) -> Array:
	var w := SimTestUtil.build({"seed": seed, "alliances": [0, 1]})
	var t := PackedByteArray()
	t.resize(w.terrain.size())
	t.fill(0)
	w.terrain = t
	var mid := w.terrainCols / 2
	for dy in range(-6, 7):
		for dx in range(-6, 7):
			w.terrain[(mid + dy) * w.terrainCols + (mid + dx)] = w.TERRAIN_FOREST
			w.grid.setBlocked(mid + dx, mid + dy, false)
	return [w, mid * TILE + TILE / 2.0, mid * TILE + TILE / 2.0]


func settle(w: World) -> void:
	for i in 6:
		w.tick()
		w.drainEvents()


func test_a_unit_in_forest_is_hidden_from_a_distant_enemy_but_not_its_own_side() -> void:
	var f := woods()
	var w: World = f[0]
	var hider := w.spawnUnit(P, "archer", f[1], f[2])
	w.spawnUnit(E, "knight", f[1] + 900, f[2])
	settle(w)
	eq(w.visibleTo(E, hider), false, "the ambush was visible from 900 away")
	eq(w.visibleTo(P, hider), true)


func test_something_close_enough_gives_it_away() -> void:
	var f := woods()
	var w: World = f[0]
	var hider := w.spawnUnit(P, "archer", f[1], f[2])
	w.spawnUnit(E, "scout", f[1] + C.CONCEAL_RANGE * 0.5, f[2])
	settle(w)
	eq(w.visibleTo(E, hider), true, "walked into the wood and saw nothing")


func test_open_ground_and_buildings_are_not_hidden() -> void:
	var f := woods()
	var w: World = f[0]
	var inTheOpen := w.spawnUnit(P, "archer", f[1] + 600, f[2])
	w.spawnUnit(E, "knight", f[1] + 700, f[2])
	settle(w)
	eq(w.visibleTo(E, inTheOpen), true)
	eq(inTheOpen.spottedBy, -1, "in the open is seen by everyone")
	var b := w.spawnBuilding(P, "house", f[1], f[2], true)
	w.spawnUnit(E, "scout", f[1] + 80, f[2])
	settle(w)
	eq(w.visibleTo(E, b), true)


func test_an_ambusher_gives_itself_away_by_attacking() -> void:
	var f := woods()
	var w: World = f[0]
	var hider := w.spawnUnit(P, "archer", f[1], f[2])
	var victim := w.spawnUnit(E, "villager", f[1] + 60, f[2])
	w.issueAttack([hider.id], victim.id)
	run(w, 6)
	check(hider.attackCooldown > 0, "never actually attacked")
	eq(w.visibleTo(E, hider), true, "shot from cover and stayed hidden")


func test_concealment_stops_targeting_and_is_recomputed_as_units_move() -> void:
	var f := woods()
	var w: World = f[0]
	var hider := w.spawnUnit(P, "spearman", f[1], f[2])
	var archer := w.spawnUnit(E, "archer", f[1] + 190, f[2])
	run(w, 4)
	eq(w.visibleTo(E, hider), false, "close enough to spot")
	check(archer.order.target != hider.id, "acquired a target it could not see")
	var f2 := woods()
	var w2: World = f2[0]
	var open := w2.spawnUnit(P, "spearman", f2[1] + 600, f2[2])
	var archer2 := w2.spawnUnit(E, "archer", f2[1] + 720, f2[2])
	run(w2, 4)
	eq(archer2.order.kind, T.OrderKind.Attack, "ignored a unit standing in the open")
	eq(archer2.order.target, open.id)
	var f3 := woods()
	var w3: World = f3[0]
	var h3 := w3.spawnUnit(P, "archer", f3[1], f3[2])
	w3.spawnUnit(E, "knight", f3[1] + 900, f3[2])
	settle(w3)
	eq(w3.visibleTo(E, h3), false)
	w3.issueMove([h3.id], f3[1] + 700, f3[2])
	run(w3, 30)
	check(w3.terrainAt(h3.x, h3.y) != w3.TERRAIN_FOREST, "never left the wood")
	eq(h3.spottedBy, -1, "still concealed out in the open")


# ---- Siege against walls ----

func breachTime(type: String, wallType: String, budget: float = 900.0) -> float:
	var w := SimTestUtil.build({"seed": 5, "alliances": [0, 1]})
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	var wall := w.spawnBuilding(E, wallType, cx, cy, true)
	var cost: Dictionary = C.UNITS[type].cost
	var n := maxi(1, floori(budget / (cost.food + cost.wood + cost.gold)))
	var ids: Array = []
	for i in n:
		ids.append(w.spawnUnit(P, type, cx - 200, cy - 60 + i * 22).id)
	w.issueAttack(ids, wall.id)
	for t in w.SIM_HZ * 400:
		w.tick()
		w.drainEvents()
		if not wall.alive:
			return float(t) / w.SIM_HZ
	return INF


func test_a_ram_breaks_stone_far_faster_than_the_same_gold_of_infantry() -> void:
	var ram := breachTime("ram", "stone_wall")
	var militia := breachTime("militia", "stone_wall")
	check(ram < INF, "the ram never broke through")
	check(militia / ram > 2.2, "ram %.1fs vs militia %.1fs" % [ram, militia])
	check(militia < 150, "infantry cannot break a stone wall at all")


func test_palisades_are_a_speed_bump() -> void:
	check(breachTime("militia", "palisade") < 20)


func test_walls_carry_real_armour_in_the_world() -> void:
	check(C.BUILDINGS.stone_wall.armor > 15)
	check(C.BUILDINGS.palisade.armor > 3)
	check(C.BUILDINGS.gate.armor < C.BUILDINGS.stone_wall.armor)
	for id in ["house", "barracks", "town_center", "farm"]:
		eq(C.BUILDINGS[id].get("armor", 1), 1, "%s quietly gained wall armour" % id)
	var w := SimTestUtil.build({"seed": 5, "alliances": [0, 1]})
	var wall := w.spawnBuilding(P, "stone_wall", w.worldW / 2.0, w.worldH / 2.0, true)
	var house := w.spawnBuilding(P, "house", w.worldW / 2.0 + 200, w.worldH / 2.0, true)
	eq(wall.armor, C.BUILDINGS.stone_wall.armor)
	eq(house.armor, 1)
