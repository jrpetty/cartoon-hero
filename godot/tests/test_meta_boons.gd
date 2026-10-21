extends TestCase
# Port of game/src/meta/boons_meta.test.ts: the battle plan — Valor, every boon
# owned at base rarity, one equipped per category with an unlock age, and the
# Warband Cache that only grants the advanced tiers.

func _init() -> void:
	Storage.use_memory_only()


func test_starts_with_valor_every_boon_at_base_rarity_and_a_default_age_order() -> void:
	var p := Profile.new()
	check(p.data.valor > 0, "valor")
	# Everyone owns every boon at Common (rarity 0) by default.
	eq(p.data.boons.size(), C.BOON_IDS.size(), "boons")
	for id in C.BOON_IDS:
		eq(p.bestBoonRarity(id), 0, id)
	eq(p.data.boonOrder.size(), 3, "order size")
	# Default: one of each category, distinct ages.
	var distinct := {}
	for c in p.data.boonOrder:
		distinct[c] = true
	eq(distinct.size(), 3, "distinct")


func test_equips_any_boon_by_default_and_the_cache_only_grants_advanced_tiers() -> void:
	var p := Profile.new()
	# No grant needed — base is owned, so it equips straight away.
	p.equipBoon("whetstones", true)
	check(p.equippedBoonPlan().any(func(b): return b.id == "whetstones" and b.rarity == 0), "equipped at base")
	# Caches never roll Common.
	for s in 30:
		check(BoonCache.parseBoonKey(BoonCache.rollBoonCache({}, RNG.new(s)).key).rarity >= 1, "seed %d" % s)


func test_equips_one_boon_per_category_and_assigns_each_an_unlock_age() -> void:
	var p := Profile.new()
	p.grantBoon(BoonCache.boonKey("whetstones", 3))  # offensive
	p.grantBoon(BoonCache.boonKey("iron_discipline", 1))  # defensive
	p.grantBoon(BoonCache.boonKey("bountiful_fields", 2))  # supportive
	p.equipBoon("whetstones", true)
	p.equipBoon("iron_discipline", true)
	p.equipBoon("bountiful_fields", true)
	# Put the eco boon first (start), attack at age II, defense at age III.
	p.setBoonAge("supportive", 0)
	p.setBoonAge("offensive", 1)
	p.setBoonAge("defensive", 2)
	var plan := p.equippedBoonPlan()
	var has := func(id: String, rarity: int, age: int) -> bool:
		return plan.any(func(b): return b.id == id and b.rarity == rarity and b.age == age)
	check(has.call("bountiful_fields", 2, 0), "eco boon at the start")
	check(has.call("whetstones", 3, 1), "attack at age II")
	check(has.call("iron_discipline", 1, 2), "defense at age III")


func test_setBoonAge_keeps_the_order_a_permutation() -> void:
	var p := Profile.new()
	p.setBoonAge("supportive", 0)  # whatever was at age 0 moves to supportive's old slot
	var distinct := {}
	for c in p.data.boonOrder:
		distinct[c] = true
	eq(distinct.size(), 3, "still one per age")
	eq(p.boonAgeFor("supportive"), 0)


func test_uses_the_best_owned_rarity_of_an_equipped_boon() -> void:
	var p := Profile.new()
	p.grantBoon(BoonCache.boonKey("whetstones", 1))
	p.grantBoon(BoonCache.boonKey("whetstones", 4))
	p.equipBoon("whetstones", true)
	eq(p.bestBoonRarity("whetstones"), 4)
	check(p.equippedBoonPlan().any(func(b): return b.id == "whetstones" and b.rarity == 4), "plan uses rarity 4")


func test_warband_cache_rolls_a_valid_boon_and_duplicates_refund_valor() -> void:
	var owned := {}
	var roll := BoonCache.rollBoonCache(owned, RNG.new(7))
	eq(roll.key, BoonCache.boonKey(roll.boonId, roll.rarity))
	eq(roll.duplicate, false)
	owned[roll.key] = true
	var dup := BoonCache.rollBoonCache(owned, RNG.new(7))
	eq(dup.duplicate, true)
	check(dup.refund > 0, "refund")


func test_spends_and_earns_valor_correctly() -> void:
	var p := Profile.new()
	p.data.valor = BoonCache.BOON_CACHE_COST
	eq(p.spendValor(BoonCache.BOON_CACHE_COST), true)
	eq(p.data.valor, 0)
	eq(p.spendValor(1), false)
	p.addValor(50)
	eq(p.data.valor, 50)


func test_parseBoonKey_reads_like_parseInt() -> void:
	var k := BoonCache.parseBoonKey("war:path:3")
	eq(k.boonId, "war:path")
	eq(k.rarity, 3)
	eq(BoonCache.parseBoonKey("whetstones:x").rarity, 0, "NaN -> 0")
	eq(BoonCache.parseBoonKey("whetstones:2x").rarity, 2, "leading digits only")
