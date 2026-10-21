extends TestCase
# Ported from game/src/sim/terrain.test.ts: Warband board terrain (mirrored
# ground, seed codes, boulders/rises/mires in the fight) and the shop lock.


func five(t) -> Array:
	return [t, t, t, t, t]


func test_terrain_is_mirrored() -> void:
	for seed in [1, 7, 99, 12345]:
		var feats := Terrain.rollTerrain(seed, 6)
		check(feats.size() > 0)
		for f in feats:
			var twin = null
			for t in feats:
				if t.col == Terrain.MIRROR_COL(f.col) and t.row == f.row:
					twin = t
					break
			check(twin != null, "%s at %d,%d has no mirror" % [f.kind, f.col, f.row])
			if twin != null:
				eq(twin.kind, f.kind)
		for f in feats:
			check(not (f.col == 4 or f.col == 5), "front rank kept clear")
			check(f.row >= 0 and f.row <= 9)


func _key(s: int) -> String:
	return "|".join(Terrain.rollTerrain(s, 8).map(func(f): return "%s%d,%d" % [f.kind, f.col, f.row]))


func test_terrain_is_stable_and_varies() -> void:
	eq(_key(42), _key(42))
	check(_key(42) != _key(43))
	eq(Terrain.rollTerrain(42, 1), [])


func test_seed_codes_round_trip() -> void:
	var re := RegEx.create_from_string("^[0-9A-Z]{6}$")
	for seed in [1, 999, 123456, 999999999]:
		var code := Terrain.terrainCode(seed)
		check(re.search(code) != null, code)
		eq(Terrain.terrainCode(seed), code)
	# parseInt(code, 36)
	var code := Terrain.terrainCode(12345)
	var n := 0
	for ch in code:
		n = n * 36 + "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".find(ch)
	eq(Terrain.parseTerrainCode(code), n)
	eq(Terrain.parseTerrainCode("not a code!"), null)


func test_boulders_take_a_cell_off_the_board() -> void:
	var run := Warband.WarbandRun.new(21, null)
	run.gold = 200
	run.level = 5
	run.shop = ["knight", "archer", "spearman", "militia", "raider"]
	for i in 5:
		run.buy(i)
	run.terrain = [{"kind": "boulder", "col": 2, "row": 2}, {"kind": "boulder", "col": 7, "row": 2}]
	var idx: int = run.deployment()[0].index
	check(not run.place(idx, 2, 2))
	check(run.place(idx, 2, 3))
	var board := Warband.placeByStyle([{"type": "spearman", "star": 1, "items": []}, {"type": "militia", "star": 1, "items": []}], 1, [{"kind": "boulder", "col": 5, "row": 4}])
	check(not board.any(func(u): return u.col == 5 and u.row == 4))


func test_a_new_boulder_shoves_a_unit_off() -> void:
	var run := Warband.WarbandRun.new(22, null)
	run.gold = 200
	run.level = 4
	run.shop = five("militia")
	run.buy(0)
	var d = run.deployment()[0]
	run.place(d.index, 1, 1)
	var at = run.deployment().filter(func(x): return x.index == d.index)[0]
	check(at.col == 1 and at.row == 1)
	run.terrain = [{"kind": "boulder", "col": 1, "row": 1}]
	var after = run.deployment().filter(func(x): return x.index == d.index)[0]
	check("%d,%d" % [after.col, after.row] != "1,1", "shoved off the rock")


func test_rise_extends_reach_mire_slows() -> void:
	var archers := [{"type": "archer", "star": 1, "items": [], "col": 1, "row": 4}]
	var foe := [{"type": "spearman", "star": 1, "items": [], "col": 7, "row": 4}]
	var rise := [{"kind": "rise", "col": 1, "row": 4}, {"kind": "rise", "col": 8, "row": 4}]
	var flat := Autobattle.resolveBattle(archers, foe, 3, 40.0, null, {})
	var high := Autobattle.resolveBattle(archers, foe, 3, 40.0, null, {"terrain": rise})
	check(high.powerA >= flat.powerA)
	check(Terrain.TERRAIN.rise.rangePct > 0)
	check(Terrain.TERRAIN.mire.speedPct < 0)
	eq(Terrain.TERRAIN.boulder.blocks, true)
	check(Terrain.isBlocked([{"kind": "boulder", "col": 3, "row": 3}], 3, 3))
	eq(Terrain.terrainAt([{"kind": "mire", "col": 3, "row": 3}], 3, 3).kind, "mire")


func test_every_round_has_its_own_ground() -> void:
	var run := Warband.WarbandRun.new(31, null)
	eq(run.terrain.size(), 0)
	check(RegEx.create_from_string("^[0-9A-Z]{6}$").search(run.terrainCode()) != null)
	var seen := {}
	var guard := 0
	while run.round < 8 and run.phase != "over" and guard < 60:
		guard += 1
		if run.phase == "augment":
			run.pickAugment(0)
			continue
		if run.phase == "draft":
			run.takeCarousel(0)
			continue
		if run.phase == "shop":
			seen[run.terrainCode()] = true
			run.fight()
		if run.phase == "result":
			run.next()
	check(seen.size() > 1)


# ---- the shop lock -----------------------------------------------------------------

func test_lock_holds_the_shop_through_the_round() -> void:
	var run := Warband.WarbandRun.new(41, null)
	run.gold = 50
	var held := run.shop.duplicate()
	check(not run.shopLocked)
	check(run.toggleShopLock())
	check(run.shopLocked)
	run.fight()
	if run.phase == "result":
		run.next()
	if run.phase == "augment":
		run.pickAugment(0)
	if run.phase == "draft":
		run.takeCarousel(0)
	eq(run.shop, held)
	check(not run.shopLocked)


func test_rerolling_releases_the_lock() -> void:
	var run := Warband.WarbandRun.new(42, null)
	run.gold = 50
	run.toggleShopLock()
	check(run.shopLocked)
	run.reroll()
	check(not run.shopLocked)


func test_lock_only_in_the_shop() -> void:
	var run := Warband.WarbandRun.new(43, null)
	run.fight()
	if run.phase != "shop":
		check(not run.toggleShopLock())
