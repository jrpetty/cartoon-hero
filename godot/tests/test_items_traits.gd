extends TestCase
# Ported from game/src/sim/items.test.ts and traits.test.ts: Warband relics,
# components and their fusion recipes, armour scaling, and the synergies.


func five(t) -> Array:
	return [t, t, t, t, t]


func unit() -> Dictionary:
	return {"maxHp": 100, "hp": 100, "attack": 10, "armor": 1, "speed": 80}


# ---- relics -----------------------------------------------------------------------

func test_apply_items_stacks_buffs() -> void:
	var u := unit()
	Items.applyItems(u, ["whetstone", "greatmail"])
	eq(u.attack, 13)
	eq(u.armor, 7)


func test_an_itemized_carry_sweeps_a_plain_unit() -> void:
	var buffed := [{"type": "knight", "star": 1, "items": ["giantsbelt", "warbanner", "whetstone"]}]
	var plain := [{"type": "knight", "star": 1}]
	var wins := 0
	for seed in [1, 2, 3, 4, 5]:
		if Autobattle.resolveBattle(buffed, plain, seed).winner == "A":
			wins += 1
	eq(wins, 5)


func test_equipping_is_capped_at_three() -> void:
	var run := Warband.WarbandRun.new(3, null)
	run.gold = 50
	run.shop = five("militia")
	run.buy(0)
	run.itemStash = ["whetstone", "greatmail", "warhorn", "giantsbelt"]
	check(run.equipItem(0, 0))
	check(run.equipItem(0, 0))
	check(run.equipItem(0, 0))
	check(not run.equipItem(0, 0), "4th rejected")
	eq(run.pieces[0].items.size(), 3)
	eq(run.itemStash.size(), 1)


func test_a_star_up_inherits_relics() -> void:
	var run := Warband.WarbandRun.new(4, null)
	run.gold = 99
	run.shop = five("archer")
	run.buy(0)
	run.itemStash = ["whetstone"]
	run.equipItem(0, 0)
	run.shop = five("archer")
	run.buy(0)
	run.itemStash = ["greatmail"]
	run.equipItem(0, run.pieces.size() - 1)
	run.shop = five("archer")
	run.buy(0)
	var two := run.pieces.filter(func(p): return p.star == 2)
	check(two.size() == 1)
	if two.size():
		check(two[0].items.size() >= 2)


func test_items_have_a_buff_and_a_label() -> void:
	for id in C.ITEMS:
		var it: Dictionary = C.ITEMS[id]
		check(String(it.short).length() > 0)
		check(it.buff.size() > 0)


# ---- components -------------------------------------------------------------------

func test_every_component_pair_makes_a_distinct_relic() -> void:
	var seen := {}
	for a in C.COMPONENT_IDS:
		for b in C.COMPONENT_IDS:
			var made = Items.fusionOf(a, b)
			check(made != null, "%s+%s" % [a, b])
			check(C.ITEMS.has(made), "%s+%s → %s" % [a, b, made])
			seen[made] = true
	eq(C.RECIPES.size(), 15)
	eq(seen.size(), 15)
	eq(Items.fusionOf("blade", "plate"), Items.fusionOf("plate", "blade"))
	eq(Items.fusionOf("whetstone", "blade"), null)


func test_fuses_the_first_loose_pair() -> void:
	var items := ["blade", "sigil"]
	eq(Items.fuseComponents(items), "whetstone")
	eq(items, ["whetstone"])
	var one := ["blade"]
	eq(Items.fuseComponents(one), null)
	eq(one, ["blade"])
	var mixed := ["giantsbelt", "plate", "plate"]
	eq(Items.fuseComponents(mixed), "greatmail")
	eq(mixed, ["giantsbelt", "greatmail"])


func test_a_second_component_forges_in_place() -> void:
	var run := Warband.WarbandRun.new(31, null)
	run.gold = 50
	run.shop = five("militia")
	run.buy(0)
	run.itemStash = ["hide", "hide"]
	check(run.equipItem(0, 0))
	eq(run.pieces[0].items, ["hide"])
	eq(run.lastFusion, null)
	check(run.equipItem(0, 0))
	eq(run.pieces[0].items, ["giantsbelt"])
	check(TestWarbandUtil.same(run.lastFusion, {"item": "giantsbelt", "type": "militia"}))
	eq(run.itemStash.size(), 0)


func test_components_buff_on_their_own() -> void:
	var u := unit()
	Items.applyItems(u, ["blade", "plate"])
	eq(u.attack, 16)
	eq(u.armor, 5)
	var fused := unit()
	Items.applyItems(fused, [Items.fusionOf("blade", "plate")])
	check(fused.attack + fused.armor > 0)


func test_lookup_helpers() -> void:
	check(Items.isComponent("blade"))
	check(not Items.isComponent("whetstone"))
	check(is_same(Items.itemDef("blade"), C.COMPONENTS.blade))
	check(is_same(Items.itemDef("whetstone"), C.ITEMS.whetstone))
	eq(Items.itemDef("nope"), null)
	for id in C.COMPONENT_IDS:
		eq(Items.buildsInto(id).size(), 5)
	eq(Items.buildsInto("whetstone"), [])
	for id in C.COMPONENTS:
		var c: Dictionary = C.COMPONENTS[id]
		eq(c.component, true)
		check(c.buff.size() > 0)


func test_a_run_drops_components_that_fuse() -> void:
	var run := Warband.WarbandRun.new(41, null)
	run.gold = 200
	run.level = 6
	for ty in ["knight", "archer", "spearman"]:
		run.shop = five(ty)
		run.buy(0)
		run.buy(1)
	var sawComponent := false
	var r := 0
	while r < 16 and run.phase != "over":
		r += 1
		if run.phase == "augment":
			run.pickAugment(0)
			continue
		if run.phase == "draft":
			run.takeCarousel(0)
			continue
		if run.itemStash.any(func(id): return Items.isComponent(id)):
			sawComponent = true
		while run.itemStash.size() and run.phase == "shop":
			var target := -1
			for i in run.pieces.size():
				if run.pieces[i].items.size() < 3:
					target = i
					break
			if target < 0 or not run.equipItem(0, target):
				break
		if run.phase == "shop":
			run.fight()
		if run.phase == "result":
			run.next()
	check(sawComponent)
	var forged: Array = []
	for p in run.pieces:
		for id in p.items:
			if not Items.isComponent(id):
				forged.append(id)
	check(forged.size() > 0)
	for id in forged:
		check(C.ITEMS.has(id))


# ---- armour scaling -------------------------------------------------------------------

func test_armour_blunts_but_never_walls_off() -> void:
	var w := World.new(7)
	w.init(MapGen.generateMap("open_plains", 7), [{}, {}], [1.0, 1.0])
	var target := w.spawnUnit(T.Team.Enemy, "militia", 1200, 1200)
	target.armor = 60
	target.pierceArmor = 60
	var before := target.hp
	w.dealDamage(T.Team.Player, target, 30, "twohand")
	var dealt := before - target.hp
	check(dealt > 1)
	near(dealt, 30 * 0.2, 1e-5)
	var normal := w.spawnUnit(T.Team.Enemy, "militia", 1300, 1200)
	normal.armor = 4
	normal.pierceArmor = 4
	var h0 := normal.hp
	w.dealDamage(T.Team.Player, normal, 30, "twohand")
	eq(h0 - normal.hp, 26)


func test_armour_is_worth_taking_but_not_an_auto_win() -> void:
	var ROWS := [4, 5, 3, 6, 2, 7, 1, 8, 0, 9]
	var side := func(items: Array, s: int) -> Array:
		var out: Array = []
		for i in 5:
			out.append({"type": "twohand", "star": 2, "items": items,
				"col": (4 - floori(i / float(ROWS.size()))) if s == -1 else (5 + floori(i / float(ROWS.size()))),
				"row": ROWS[i % ROWS.size()]})
		return out
	var armour := ["greatmail", "towershield", "dancersmail"]
	var damage := ["whetstone", "bloodaxe", "warbanner"]
	var seeds := [1, 2, 3, 4, 5]
	var vsBare := 0
	var vsDamage := 0
	for s in seeds:
		if Autobattle.resolveBattle(side.call(armour, -1), side.call([], 1), s).winner == "A":
			vsBare += 1
		if Autobattle.resolveBattle(side.call(armour, -1), side.call(damage, 1), s).winner == "A":
			vsDamage += 1
	eq(vsBare, seeds.size())
	check(vsDamage < seeds.size())


# ---- synergies ----------------------------------------------------------------------

func _find(arr: Array, id: String):
	for a in arr:
		if a.trait.id == id:
			return a
	return null


func test_activates_with_enough_distinct_types() -> void:
	eq(Traits.activeTraits(["militia"]).size(), 0)
	var two := Traits.activeTraits(["archer", "skirmisher"])
	var marks = _find(two, "marksmen")
	check(marks != null)
	eq(marks.count, 2)
	check(String(marks.tier.label).contains("18%"))


func test_escalates_to_a_higher_tier() -> void:
	var marks = _find(Traits.activeTraits(["archer", "skirmisher", "crossbow", "handcannon"]), "marksmen")
	eq(marks.count, 4)
	check(String(marks.tier.label).contains("45%"))


func test_counts_overlapping_traits_independently() -> void:
	var ids: Array = Traits.traitsOf("spearman").map(func(t): return t.id)
	ids.sort()
	eq(ids, ["footmen", "pikes"])
	var act := Traits.activeTraits(["spearman", "pikeman"])
	eq(_find(act, "footmen").count, 2)
	eq(_find(act, "pikes").count, 2)


func test_applies_flat_and_percent_buffs() -> void:
	var u := unit()
	Traits.applyBuff(u, {"armor": 3, "atkPct": 50, "hpPct": 20, "speedPct": 10})
	eq(u.armor, 4)
	eq(u.attack, 15)
	eq(u.maxHp, 120)
	eq(u.hp, 120)
	eq(u.speed, 88)


func test_a_synergized_warband_beats_a_flat_one() -> void:
	var synced := [{"type": "archer", "count": 1}, {"type": "skirmisher", "count": 1}, {"type": "crossbow", "count": 1}, {"type": "handcannon", "count": 1}]
	var flat := [{"type": "archer", "count": 4}]
	var wins := 0
	for seed in [1, 2, 3, 4, 5, 6, 7]:
		if Autobattle.resolveBattle(synced, flat, seed).winner == "A":
			wins += 1
	check(wins >= 5, "synergy won %d/7" % wins)
