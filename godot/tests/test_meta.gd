extends TestCase
# Port of game/src/meta/meta.test.ts: chest odds, the level curve and match
# rewards, and the profile's collection and currencies.

const H := preload("res://tests/meta_helpers.gd")


func _init() -> void:
	Storage.use_memory_only()


# ---- Chest rolls ----

func test_chest_respects_rarity_weighting_over_many_samples() -> void:
	var rng := RNG.new(2024)
	var chest: Dictionary = Chests.CHESTS[0]  # unbiased Footman's chest
	var counts := []
	counts.resize(C.RARITIES.size())
	counts.fill(0)
	var owned := {}
	for i in 20000:
		var r := Chests.rollChest(chest, owned, rng)
		counts[r.rarity] += 1
	# Commons dominate, each tier rarer than the one before.
	for i in range(1, counts.size()):
		check(counts[i] < counts[i - 1], "tier %d rarer than tier %d (%d vs %d)" % [i, i - 1, counts[i], counts[i - 1]])
	check(counts[0] / 20000.0 > 0.5, "commons dominate")
	check(counts[5] / 20000.0 < 0.01, "mythics are rare")


func test_premium_chest_skews_rarer_than_the_basic_chest() -> void:
	var rngA := RNG.new(7)
	var rngB := RNG.new(7)
	var owned := {}
	var basicHigh := 0
	var royalHigh := 0
	for i in 8000:
		if Chests.rollChest(Chests.CHESTS[0], owned, rngA).rarity >= 3:
			basicHigh += 1
		if Chests.rollChest(Chests.CHESTS[2], owned, rngB).rarity >= 3:
			royalHigh += 1
	check(royalHigh > basicHigh * 2, "royal %d vs basic %d" % [royalHigh, basicHigh])


func test_chest_flags_duplicates_and_pays_a_refund() -> void:
	var rng := RNG.new(5)
	var owned := {}
	# Own everything: every roll must be a duplicate with a positive refund.
	for id in Catalog.COLLECTIBLE_UNIT_IDS:
		for r in C.RARITIES:
			owned[Catalog.variantKey(id, r.index)] = true
	var res := Chests.rollChest(Chests.CHESTS[1], owned, rng)
	eq(res.duplicate, true, "duplicate")
	check(res.refund > 0, "refund")
	check(res.variant != null and res.variant.key == Catalog.variantKey(res.variant.unitId, res.rarity), "variant found in the catalog")


# ---- Progression ----

func test_level_curve_is_monotonically_increasing() -> void:
	for l in range(1, 50):
		check(Progression.xpForLevel(l + 1) > Progression.xpForLevel(l), "level %d" % l)


func test_levelFromXp_inverts_totalXpForLevel() -> void:
	for l in [1, 2, 5, 10, 25]:
		var info := Progression.levelFromXp(Progression.totalXpForLevel(l))
		eq(info.level, l, "level")
		eq(info.into, 0, "into")


func test_wins_on_harder_difficulties_reward_more() -> void:
	var base := {"durationSec": 600, "unitsKilled": 30, "buildingsRazed": 5, "fairMode": false}
	var easy := Progression.computeRewards(H.with(base, {"win": true, "difficulty": "squire"}))
	var hard := Progression.computeRewards(H.with(base, {"win": true, "difficulty": "warlord"}))
	check(hard.xp > easy.xp, "xp")
	check(hard.renown > easy.renown, "renown")


func test_fair_mode_pays_a_premium() -> void:
	var base := {"durationSec": 600, "unitsKilled": 30, "buildingsRazed": 5, "difficulty": "knight", "win": true}
	var normal := Progression.computeRewards(H.with(base, {"fairMode": false}))
	var fair := Progression.computeRewards(H.with(base, {"fairMode": true}))
	check(fair.renown > normal.renown, "fair renown")


# ---- Profile ----

func test_profile_starts_with_all_commons_owned_and_equipped() -> void:
	var p := Profile.new()
	for id in Catalog.COLLECTIBLE_UNIT_IDS:
		eq(p.owns(Catalog.variantKey(id, 0)), true, id)
		eq(p.data.loadout[id], 0, id)


func test_profile_grants_equips_and_resolves_best_rarity() -> void:
	var p := Profile.new()
	eq(p.grant(Catalog.variantKey("knight", 3)), true)
	eq(p.grant(Catalog.variantKey("knight", 3)), false, "duplicate")
	eq(p.bestOwnedRarity("knight"), 3)
	p.equip("knight", 3)
	eq(p.data.loadout.knight, 3)
	# Can't equip what you don't own.
	p.equip("knight", 5)
	eq(p.data.loadout.knight, 3)


func test_fair_mode_loadout_flattens_everything_to_common() -> void:
	var p := Profile.new()
	p.grant(Catalog.variantKey("archer", 4))
	p.equip("archer", 4)
	eq(p.matchLoadout(false).archer, 4)
	eq(p.matchLoadout(true).archer, 0)


func test_xp_grants_levels_and_level_up_renown() -> void:
	var p := Profile.new()
	var renownBefore: float = p.data.renown
	var res := p.addXp(Progression.xpForLevel(1) + 10)
	eq(res.levelsGained, 1)
	eq(p.level, 2)
	check(p.data.renown > renownBefore, "renown grew")


func test_renown_spend_respects_balance() -> void:
	var p := Profile.new()
	p.data.renown = 100
	eq(p.spendRenown(150), false)
	eq(p.spendRenown(80), true)
	eq(p.data.renown, 20)


# ---- Beyond the TS file: the port keeps the save format ----

func test_profile_saves_and_reloads_the_same_data() -> void:
	var p := Profile.new()
	p.grant(Catalog.variantKey("knight", 2))
	p.equip("knight", 2)
	p.recordResult(true)
	p.recordResult(true)
	p.recordResult(false)
	p.save()
	var again := Profile.load()
	check(H.deep_eq(again.data, p.data), "reloaded profile matches")
	eq(again.data.stats.bestStreak, 2)
	eq(again.data.stats.streak, 0)
	check(again.owns(Catalog.variantKey("knight", 2)), "owned set restored")


func test_profile_reads_a_save_written_by_the_ts_build() -> void:
	# An older TS save: no boons, valor, achievements or factions yet.
	Storage.setItem(Profile.STORAGE_KEY, '{"name":"Old Hand","totalXp":1234,"renown":800,"owned":["villager:0","knight:4"],"loadout":{"villager":0,"knight":4},"stats":{"wins":3,"losses":1,"played":4,"bestStreak":2,"streak":1},"openedChests":2,"commanders":["marshal"],"commander":"marshal","commanderReveal":""}')
	var p := Profile.load()
	eq(p.data.name, "Old Hand")
	eq(p.data.loadout.knight, 4)
	eq(p.bestOwnedRarity("knight"), 4)
	eq(p.data.valor, 150, "valor back-filled")
	eq(p.data.boons.size(), C.BOON_IDS.size(), "base boons back-filled")
	check(p.data.unlockedFactions is Array and p.data.unlockedFactions.is_empty(), "factions back-filled")
	eq(p.data.commander, "marshal")
	check(p.owns(Catalog.variantKey("militia", 0)), "new commons granted")
	# The migrated profile was saved, as JSON the TS build reads back.
	var raw: String = Storage.getItem(Profile.STORAGE_KEY)
	check(raw.contains('"totalXp":1234,'), "whole numbers stay whole: " + raw.substr(0, 80))
	check(raw.begins_with('{"name":"Old Hand"'), "keys keep their order")


func test_constants_match_the_ts_exports() -> void:
	eq(Profile.FACTION_PRICE, C.FACTION_PRICE, "FACTION_PRICE")
	eq(BoonCache.BOON_CACHE_COST, C.BOON_CACHE_COST, "BOON_CACHE_COST")
	eq(History.HISTORY_LIMIT, C.HISTORY_LIMIT, "HISTORY_LIMIT")
	eq(History.FULL_REPORT_KEEP, C.FULL_REPORT_KEEP, "FULL_REPORT_KEEP")
	eq(Career.LOG_LIMIT, C.LOG_LIMIT, "LOG_LIMIT")
	eq(Playstyle.MIN_GAMES, C.MIN_GAMES, "MIN_GAMES")
	eq(Achievements.ACHIEVEMENTS.size(), C.ACHIEVEMENTS.size(), "every achievement has its test")
	eq(Achievements.CHALLENGE_POOL.size(), C.CHALLENGE_POOL.size(), "every challenge has its test")
	eq(Playstyle.ARCHETYPES.size(), C.ARCHETYPES.size(), "every archetype has its functions")
	eq(Rarity.rarityByIndex(-3).id, "common")
	eq(Rarity.rarityByIndex(99).id, "mythic")
