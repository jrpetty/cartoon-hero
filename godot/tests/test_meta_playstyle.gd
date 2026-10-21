extends TestCase
# Port of game/src/meta/playstyle.test.ts.
#
# Playstyle: players who play a certain way are recognised as playing it —
# a Man-at-Arms rusher, a turtle, a boomer, a demolisher, a raider, a tech
# master, a brawler — from the numbers alone, and the reasons are shown.

const H := preload("res://tests/meta_helpers.gd")
const U := H.UNDEFINED
const FOE := {"gathered": 12000, "razed": 3, "kills": 40, "losses": 40, "peakArmy": 30, "peakVillagers": 38, "villagerKills": 4, "upgrades": 6, "defenses": 2, "age": 2}

static var t := 1_700_000_000_000.0


static func m(over := {}) -> Dictionary:
	t += 60_000
	return H.with({
		"at": t, "kind": "skirmish", "ranked": false, "mode": "conquest", "difficulty": "knight", "won": true, "faction": "legion", "commander": "", "oaths": [],
		"map": "Highlands", "players": 2, "format": "1 v 1", "allies": [], "foes": ["norse"], "durationSec": 1500, "defeatedAt": -1, "age": 2, "ageTimes": [0, 400, 1000],
		"score": 5000, "gathered": 12000, "gatheredBy": {"food": 5000, "wood": 5000, "gold": 2000}, "spent": 11000, "kills": 40, "losses": 40, "razed": 3, "buildingsLost": 3,
		"damageDealt": 9000, "damageTaken": 9000, "peakArmy": 30, "peakVillagers": 38, "idleVillagerTime": 60, "tcIdleShare": 0.1, "upgrades": 6,
		"trained": {"villager": 40, "spearman": 10, "archer": 10, "horseman": 10, "militia": 10}, "killed": {"villager": 4}, "lost": {}, "built": {"house": 8},
		"firstHitAt": 600, "firstKillAt": 620, "firstRazeAt": 900, "spentOn": {"units": 6000, "buildings": 3000, "tech": 2000}, "foe": FOE.duplicate(),
	}, over)


static func many(n: int, over: Dictionary, lossEvery := 4) -> Array:
	var out := []
	for i in n:
		out.append(m(H.with(over, {"won": i % lossEvery != lossEvery - 1})))
	return out


static func pid(p: Dictionary):
	return p.primary.archetype.id if p.primary != null else null


static func score_of(p: Dictionary, id: String) -> float:
	for s in p.scores:
		if s.archetype.id == id:
			return s.score
	return -1.0


# ---- Recognising a style ----

func test_a_man_at_arms_rusher() -> void:
	var p := Playstyle.analyse(many(12, {"firstHitAt": 290, "durationSec": 700, "trained": {"villager": 22, "militia": 26, "archer": 3}}))
	eq(pid(p), "rush")
	eq(p.title, "Man-at-Arms Rush")
	check(p.description.contains("before 7:00"), p.description)


func test_a_turtle_who_plays_for_the_late_game() -> void:
	var p := Playstyle.analyse(many(12, {"firstHitAt": 1300, "durationSec": 2700, "built": {"palisade": 40, "watch_tower": 5, "castle": 1, "gate": 2}, "age": 3}))
	check(pid(p) in ["turtle", "late"], "primary %s" % str(pid(p)))
	check(p.title.contains("Turtle"), p.title)


func test_a_boomer() -> void:
	var p := Playstyle.analyse(many(12, {"firstHitAt": 900, "peakVillagers": 62, "gathered": 21000, "durationSec": 1600}))
	eq(pid(p), "boom")


func test_a_demolisher_who_wins_long() -> void:
	var p := Playstyle.analyse(many(12, {"razed": 13, "durationSec": 2500, "firstHitAt": 800, "trained": {"villager": 40, "ram": 8, "knight": 15}}))
	check(score_of(p, "demolition") > 0.6, "demolition score %s" % score_of(p, "demolition"))
	check(p.title.contains("Siege Grinder"), p.title)


func test_a_raider() -> void:
	var p := Playstyle.analyse(many(12, {"killed": {"villager": 22}, "trained": {"villager": 30, "horseman": 25, "horse_archer": 10}, "firstHitAt": 520}))
	eq(pid(p), "raid")


func test_a_fast_aging_tech_master() -> void:
	var p := Playstyle.analyse(many(12, {"upgrades": 15, "ageTimes": [0, 330, 690], "firstHitAt": 900}))
	eq(pid(p), "tech")
	eq(p.title, "Fast Crown")


func test_an_efficient_brawler() -> void:
	var p := Playstyle.analyse(many(12, {"kills": 150, "losses": 70, "durationSec": 1500, "firstHitAt": 500}))
	eq(pid(p), "brawler")


func test_nobody_in_particular_an_all_rounder() -> void:
	var p := Playstyle.analyse(many(12, {}))
	eq(p.primary, null)
	eq(p.title, "All-Rounder")


func test_waits_for_enough_games() -> void:
	var p := Playstyle.analyse(many(Playstyle.MIN_GAMES - 1, {"firstHitAt": 200, "durationSec": 600}))
	eq(p.enough, false)
	eq(p.confidence, "none")
	eq(p.description, "Play 1 more game and your style will show up here.")


# ---- One match's style ----

func test_follows_the_written_rules() -> void:
	check(Playstyle.matchStyles(m({"firstHitAt": 300})).has("rush"), "rush")
	check(Playstyle.matchStyles(m({"killed": {"villager": 12}})).has("raid"), "raid")
	check(Playstyle.matchStyles(m({"razed": 9})).has("demolition"), "demolition")
	check(Playstyle.matchStyles(m({"built": {"watch_tower": 3, "palisade": 12}, "firstHitAt": 800})).has("turtle"), "turtle")
	check(Playstyle.matchStyles(m({"peakVillagers": 50, "firstHitAt": 800})).has("boom"), "boom")
	check(Playstyle.matchStyles(m({"ageTimes": [0, 300, 800]})).has("fastage"), "fastage")
	check(Playstyle.matchStyles(m({"durationSec": 2400})).has("lategame"), "lategame")
	eq(Playstyle.matchStyles(m()), ["standard"])


# ---- Traits and the reasons behind them ----

func test_names_the_army_the_faction_and_the_trades() -> void:
	var p := Playstyle.analyse(many(12, {"faction": "khanate", "trained": {"villager": 30, "horseman": 30, "horse_archer": 20, "cataphract": 10}, "kills": 120, "losses": 50}))
	var names: Array = p.traits.map(func(x): return x.name)
	check(names.has("Cavalry Commander"), str(names))
	check(names.has("Khanate Specialist"), str(names))
	check(names.has("Efficient Trader"), str(names))


func test_finds_whats_different_about_the_games_you_win() -> void:
	var games := []
	for i in 6:
		games.append(m({"won": true, "peakVillagers": 55}))
	for i in 6:
		games.append(m({"won": false, "peakVillagers": 30}))
	var p := Playstyle.analyse(games)
	eq(p.winKeys[0].label, "Peak villagers")
	check(p.winKeys[0].sentence.contains("more villagers"), p.winKeys[0].sentence)


func test_gives_every_archetype_evidence_it_can_show() -> void:
	var f := Playstyle.features(many(10, {}))
	for a in Playstyle.ARCHETYPES:
		var e: Array = a.evidence.call(f)
		check(e.size() >= 2, a.id)
		for x in e:
			check(x.weight >= 0, a.id + " weight")
		check(a.describe.call(f).length() > 60, a.id + " describe")


func test_copes_with_old_records_that_lack_the_newer_fields() -> void:
	var old := many(8, {"firstHitAt": U, "foe": U, "spentOn": U})
	check(not old[0].has("firstHitAt") and not old[0].has("foe"), "fields removed")
	var p := Playstyle.analyse(old)
	eq(p.games, 8)
	eq(p.features.rushRate, 0)


# ---- Style built from keywords ----

func test_a_rusher_who_opens_with_archers_is_an_archer_rush() -> void:
	var p := Playstyle.analyse(many(12, {"firstAttackAt": 300, "firstHitAt": 300, "durationSec": 700, "opener": {"archer": 900, "militia": 80}, "trained": {"villager": 22, "militia": 20, "archer": 12}}))
	eq(pid(p), "rush")
	eq(p.title, "Archer Rush")
	check(p.keywords.has("Early attacker"), str(p.keywords))
	check(p.keywords.has("Archer opener"), str(p.keywords))


func test_a_mixed_opener_says_so() -> void:
	var p := Playstyle.analyse(many(12, {"firstAttackAt": 300, "durationSec": 700, "opener": {"archer": 300, "militia": 300, "horseman": 300}}))
	eq(p.title, "Mixed Rush")


func test_attacks_early_but_wins_long_early_pressure_late_finish() -> void:
	var p := Playstyle.analyse(many(12, {"firstAttackAt": 330, "durationSec": 2600, "opener": {"horseman": 700}, "age": 3}))
	eq(pid(p), "pressure")
	check(p.title.contains("Late Closer") or p.title.contains("Late Finisher"), p.title)
	check(p.keywords.has("Early attacker") and p.keywords.has("Long games"), str(p.keywords))


func test_defending_at_home_early_is_not_a_rush() -> void:
	# Old fields say "hit something at 4:00"; the new ones say it was at home.
	var p := Playstyle.analyse(many(12, {"firstHitAt": 240, "firstAttackAt": 1100, "firstDefendAt": 240, "durationSec": 1700}))
	eq(p.features.rushRate, 0)
	check(not Playstyle.matchStyles(m({"firstHitAt": 240, "firstAttackAt": -1})).has("rush"), "not a rush")


func test_gives_good_and_bad_feedback_from_the_record() -> void:
	var p := Playstyle.analyse(many(12, {"tcIdleShare": 0.4, "gathered": 15000, "kills": 20, "losses": 50}))
	var good := " ".join(PackedStringArray(p.good))
	var bad := " ".join(PackedStringArray(p.bad))
	check(good.contains("out-gathers"), good)
	check(bad.contains("Town Center"), bad)
	check(bad.contains("lose more units"), bad)


# ---- Beyond the TS file ----

func test_reads_your_style_per_faction_and_the_match_mix() -> void:
	var games := many(6, {"faction": "norse", "firstHitAt": 300}) + many(4, {"faction": "legion", "durationSec": 2400})
	var p := Playstyle.analyse(games)
	eq(p.byFaction[0].faction, "norse")
	eq(p.byFaction[0].style, "rush")
	eq(p.byFaction[0].share, 1)
	eq(p.byFaction[1].style, "lategame")
	eq(p.matchMix[0].style, "rush")
	eq(p.matchMix[0].games, 6)
	eq(p.confidence, "medium", "10 games is medium")


func test_opener_words_and_unit_classes() -> void:
	eq(Playstyle.unitClass("villager"), null)
	eq(Playstyle.unitClass("monk"), "support")
	eq(Playstyle.unitClass("ram"), "siege")
	eq(Playstyle.unitClass("nonsense"), null)
	eq(Playstyle.openerWord({"militia": 10, "archer": 2}).word, "Man-at-Arms")
	eq(Playstyle.openerWord({"archer": 5, "crossbow": 4, "militia": 1}).word, "Archer")
	eq(Playstyle.openerWord({}).word, "")
	eq(Playstyle.mmss(59.6), "0:60", "rounds the seconds like the TS (59.6 -> 0:60)")
	eq(Playstyle.mmss(754), "12:34")
