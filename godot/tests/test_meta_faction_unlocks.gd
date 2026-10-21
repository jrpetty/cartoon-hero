extends TestCase
# Port of game/src/meta/faction_unlocks.test.ts, the "Owning factions" part.
# Factions are owned. A new player picks one free; every other costs
# FACTION_PRICE renown — the War Chest currency. (The file's "first launch"
# tests drive the MenuScreen and FactionBook UI and belong to the UI port.)

func _init() -> void:
	Storage.use_memory_only()


func test_starts_with_none_and_one_free_pick() -> void:
	var p := Profile.new()
	eq(p.needsFirstFaction, true)
	eq(p.chooseFirstFaction("khanate"), true)
	eq(p.ownedFactions(), ["khanate"])
	eq(p.chooseFirstFaction("legion"), false, "a second free pick")
	eq(p.playableFaction(), "khanate")


func test_costs_1500_renown_for_each_after_that() -> void:
	eq(Profile.FACTION_PRICE, 1500)
	var p := Profile.new()
	p.chooseFirstFaction("kingdom")
	p.data.renown = 1499
	eq(p.unlockFaction("legion"), false)
	eq(p.data.renown, 1499)
	p.data.renown = 1600
	eq(p.unlockFaction("legion"), true)
	eq(p.data.renown, 100)
	eq(p.unlockFaction("legion"), false, "buying twice")
	eq(p.data.renown, 100)


func test_wont_select_a_faction_you_dont_own() -> void:
	var p := Profile.new()
	p.chooseFirstFaction("norse")
	p.selectFaction("ascendancy")
	eq(p.data.faction, "norse")


func test_survives_a_reload() -> void:
	var p := Profile.new()
	p.chooseFirstFaction("shogunate")
	p.data.renown = 2000
	p.unlockFaction("legion")
	var again := Profile.load()
	var owned := again.ownedFactions()
	owned.sort()
	eq(owned, ["legion", "shogunate"])


func test_has_a_full_entry_in_the_book_for_every_faction() -> void:
	for id in C.FACTION_IDS:
		var g: Dictionary = C.FACTIONS[id].guide
		check(g.playstyle.length() > 120, id + " playstyle")
		check(g.suits.size() >= 3, id + " suits")
		check(g.tips.size() >= 3, id + " tips")
		check(g.power.all(func(v): return v >= 1 and v <= 5), id + " power")


func test_selecting_a_faction_brings_back_its_commander() -> void:
	var p := Profile.new()
	p.chooseFirstFaction("legion")
	p.data.renown = 5000
	p.unlockFaction("norse")
	var first: String = p.data.commanders[0]
	p.pairCommander("norse", first)
	p.data.commanders.append("zzz_other")
	p.selectCommander("zzz_other")
	eq(p.data.commander, "zzz_other")
	p.selectFaction("norse")
	eq(p.data.commander, first, "the pairing comes back")
