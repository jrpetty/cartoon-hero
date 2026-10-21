extends TestCase
# Port of game/src/meta/career.test.ts.
#
# The player's own stats: every match, skirmish or online, summed into a
# career — win rate and average win time per faction, favourite faction, map,
# unit, commander and Oath, record against each enemy faction, and the rest.
#
# Not ported here: "are counted by the simulation in a real fight" and "reads a
# real match" build a match through the real World (sim port), and "The Career
# screen" draws the UI. careerMatch() is covered instead from a hand-built
# MatchReport Dictionary of the shape Metrics.matchReport() returns, below.

const H := preload("res://tests/meta_helpers.gd")

static var t0 := 1_700_000_000_000.0


func _init() -> void:
	Storage.use_memory_only()
	Career.storage_override = null


static func m(over := {}) -> Dictionary:
	t0 += 60_000
	return H.with({
		"at": t0, "kind": "skirmish", "ranked": false, "mode": "conquest", "difficulty": "knight", "won": true,
		"faction": "legion", "commander": "marshal", "oaths": [], "map": "Highlands", "players": 2, "format": "1 v 1", "allies": [], "foes": ["norse"],
		"durationSec": 900, "defeatedAt": -1, "age": 2, "ageTimes": [0, 300, 600], "score": 5000, "gathered": 8000,
		"gatheredBy": {"food": 4000, "wood": 3000, "gold": 1000}, "spent": 7000, "kills": 40, "losses": 20, "razed": 5, "buildingsLost": 1,
		"damageDealt": 9000, "damageTaken": 5000, "peakArmy": 30, "peakVillagers": 40, "idleVillagerTime": 60, "tcIdleShare": 0.1, "upgrades": 6,
		"trained": {"villager": 40, "legionary": 20, "archer": 5}, "killed": {"militia": 30, "archer": 10}, "lost": {"legionary": 15, "archer": 5},
		"built": {"house": 8, "barracks": 2},
	}, over)


static func times(n: int, over: Dictionary) -> Array:
	var out := []
	for i in n:
		out.append(m(over))
	return out


# ---- A career, from matches ----

func test_knows_your_record_and_average_win_time_per_faction() -> void:
	var c := Career.fold([
		m({"faction": "legion", "won": true, "durationSec": 600}),
		m({"faction": "legion", "won": true, "durationSec": 1200}),
		m({"faction": "legion", "won": false, "durationSec": 1500}),
		m({"faction": "norse", "won": true, "durationSec": 480, "foes": ["kingdom"]}),
	])
	eq(c.all.played, 4)
	near(Career.winRate(c.byFaction.legion), 2.0 / 3.0, 1e-9)
	eq(Career.avgWinSecs(c.byFaction.legion), 900)
	eq(c.byFaction.legion.fastestWin, 600)
	eq(c.byFaction.legion.longest, 1500)
	eq(Career.avgWinSecs(c.byFaction.norse), 480)
	eq(c.all.fastestWin, 480)


func test_finds_your_favourite_faction_map_unit_commander_and_oath() -> void:
	var c := Career.fold([
		m({"faction": "khanate", "map": "Riverlands", "commander": "steward", "oaths": ["plough"], "trained": {"horse_archer": 30, "villager": 90}}),
		m({"faction": "khanate", "map": "Riverlands", "commander": "steward", "oaths": ["plough", "bow"], "trained": {"horse_archer": 25}}),
		m({"faction": "legion", "map": "Highlands", "trained": {"legionary": 12}}),
	])
	eq(Career.favourite(c.byFaction), "khanate")
	eq(Career.favourite(c.byMap), "Riverlands")
	eq(Career.favourite(c.byCommander), "steward")
	eq(Career.favourite(c.byOath), "plough")
	eq(Career.topUnit(c, "trained"), "horse_archer", "villagers aren't a favourite")
	eq(Career.favouriteUnitOf(c.byFaction.legion), "legionary")


func test_tracks_your_record_against_each_enemy_faction() -> void:
	var games := times(4, {"foes": ["norse"], "won": false}) + times(4, {"foes": ["kingdom"], "won": true})
	games.append(m({"foes": ["khanate", "khanate"], "won": true}))
	var c := Career.fold(games)
	eq(c.vsFaction.norse.played, 4)
	eq(Career.winRate(c.vsFaction.norse), 0)
	eq(c.vsFaction.khanate.played, 1, "one match, however many of them")
	eq(Career.worstBy(c.vsFaction), "norse")
	eq(Career.bestBy(c.vsFaction), "kingdom")


func test_counts_streaks_in_the_order_games_were_played() -> void:
	var c := Career.fold([m({"won": true}), m({"won": true}), m({"won": true}), m({"won": false}), m({"won": false}), m({"won": true})])
	eq(c.bestStreak, 3)
	eq(c.worstStreak, 2)
	eq(c.streak, 1)


func test_averages_how_long_each_age_takes_over_the_games_that_reached_it() -> void:
	var c := Career.fold([m({"ageTimes": [0, 300]}), m({"ageTimes": [0, 400, 800]}), m({"ageTimes": [0]})])
	eq(c.ageReached[1], 2)
	eq(c.ageTimeSum[1] / c.ageReached[1], 350)
	eq(c.ageReached[2], 1)


func test_keeps_units_trained_lost_and_killed() -> void:
	var c := Career.fold([m(), m()])
	check(H.matches(c.units.legionary, {"trained": 40, "killed": 0, "lost": 30, "games": 2, "wins": 2}), "legionary: %s" % str(c.units.legionary))
	eq(c.units.militia.killed, 60)
	eq(Career.topUnit(c, "killed", false), "militia")
	eq(Career.kd(c.all), 2)


# ---- Per-unit stats ----

func test_credit_kills_damage_and_razes_to_the_unit_that_made_them_and_rank_kd() -> void:
	var c := Career.fold([
		m({"trained": {"knight": 20, "archer": 30}, "lost": {"knight": 5, "archer": 20}, "unitKills": {"knight": 40, "archer": 12}, "unitDamage": {"knight": 9000, "archer": 4000}, "unitRazed": {"knight": 3}}),
		m({"won": false, "trained": {"knight": 10}, "lost": {"knight": 5}, "unitKills": {"knight": 10}}),
	])
	check(H.matches(c.units.knight, {"trained": 30, "lost": 10, "kills": 50, "damage": 9000, "razed": 3, "games": 2, "wins": 1}), "knight: %s" % str(c.units.knight))
	eq(Career.unitKd(c.units.knight), 5)
	near(Career.unitKd(c.units.archer), 0.6, 1e-9)
	eq(Career.mostEffectiveUnit(c), "knight")
	eq(Career.topUnit(c, "kills"), "knight")


# ---- Offline and online together ----

func test_records_both_and_filters_to_either() -> void:
	Career.recordCareer(m({"kind": "skirmish", "won": true}))
	Career.recordCareer(m({"kind": "online", "won": false, "difficulty": ""}))
	Career.recordCareer(m({"kind": "online", "ranked": true, "won": true, "difficulty": ""}))
	eq(Career.careerFor("all").career.all.played, 3)
	eq(Career.careerFor("skirmish").career.all.played, 1)
	eq(Career.careerFor("online").career.all.played, 2)
	eq(Career.careerFor("ranked").career.all.won, 1)
	eq(Career.careerTotals().byKind.ranked.played, 1)


func test_keeps_lifetime_totals_exact_past_the_logs_limit() -> void:
	for i in Career.LOG_LIMIT + 25:
		Career.recordCareer(m({"won": i % 2 == 0}))
	eq(Career.careerLog().size(), Career.LOG_LIMIT)
	eq(Career.careerTotals().all.played, Career.LOG_LIMIT + 25)


func test_survives_full_storage_by_keeping_fewer_recent_matches() -> void:
	var store := H.FakeStore.new()
	store.limit = 20_000
	Career.storage_override = store
	for i in 60:
		Career.recordCareer(m())
	eq(Career.careerTotals().all.played, 60)
	check(Career.careerLog().size() > 0, "some log kept")
	check(Career.careerLog().size() < 60, "the log was trimmed to fit")
	Career.storage_override = null


# ---- careerMatch, from a report of the shape Metrics.matchReport() returns ----

static func player(over: Dictionary) -> Dictionary:
	return H.with(H.emptySide(), H.with({"team": 0, "group": 0, "relation": "enemy", "horde": false, "won": false, "defeated": false, "defeatedAt": -1}, over))


func test_reads_a_report_your_faction_the_enemys_your_units_and_ages() -> void:
	var you := player({"team": 0, "group": 0, "relation": "you", "won": true, "faction": "shogunate", "oaths": ["sword"], "ageTimes": [0, 301.4, 702.6],
		"trainedByType": {"villager": 30, "samurai": 12}, "unitsKilled": 20, "unitsLost": 8, "gathered": 9000.6, "score": 4321.4,
		"tcSeconds": 1000, "idleTcTime": 120, "damageByUnit": {"samurai": 1234.6}, "openerByUnit": {"samurai": 400.4}, "firstAttackAt": 412.2})
	var foe := player({"team": 1, "group": 1, "relation": "enemy", "faction": "khanate", "gathered": 7000, "buildingsRazed": 2, "unitsKilled": 8, "unitsLost": 20,
		"killedByType": {"villager": 3}, "builtByType": {"palisade": 8, "watch_tower": 1}, "firstAttackAt": 333, "age": 2})
	var horde := player({"team": 2, "group": 2, "relation": "enemy", "horde": true, "faction": "norse"})
	var report := {"you": H.emptySide(), "foe": H.emptySide(), "durationSec": 1200.4, "mapName": "Open Plains", "players": [you, foe, horde]}
	var cm := Career.careerMatch(report, {"at": 1, "won": true, "kind": "online", "ranked": true, "mode": "conquest", "difficulty": "knight", "commander": "marshal"})
	eq(cm.faction, "shogunate")
	eq(cm.foes, ["khanate"], "the horde is nobody's faction")
	eq(cm.format, "1 v 1")
	eq(cm.players, 2)
	eq(cm.map, "Open Plains")
	eq(cm.difficulty, "", "no AI difficulty online")
	eq(cm.commander, "", "no commanders online")
	eq(cm.oaths, ["sword"])
	check(H.deep_eq(cm.ageTimes, [0, 301, 703]), "ages rounded: %s" % str(cm.ageTimes))
	eq(cm.durationSec, 1200)
	eq(cm.gathered, 9001)
	eq(cm.score, 4321)
	eq(cm.tcIdleShare, 0.12)
	eq(cm.unitDamage.samurai, 1235)
	eq(cm.opener.samurai, 400)
	eq(cm.firstAttackAt, 412)
	check(H.matches(cm.foe, {"gathered": 7000, "razed": 2, "kills": 8, "losses": 20, "villagerKills": 3, "defenses": 4, "age": 2, "firstAttackAt": 333}), "foe average: %s" % str(cm.foe))
	var c := Career.absorb(Career.emptyCareer(), cm)
	eq(c.byKind.online.played, 1)
	eq(c.byKind.ranked.played, 1)


func test_formats_team_games_and_free_for_alls() -> void:
	var p := func(group: int, rel: String) -> Dictionary: return player({"group": group, "relation": rel})
	eq(Career.formatOf([p.call(0, "you"), p.call(0, "ally"), p.call(1, "enemy"), p.call(1, "enemy")]), "2 v 2")
	eq(Career.formatOf([p.call(0, "you"), p.call(1, "enemy"), p.call(2, "enemy"), p.call(3, "enemy")]), "4-player FFA")
	eq(Career.formatOf([p.call(0, "you"), p.call(0, "ally"), p.call(0, "ally"), p.call(1, "enemy")]), "3 v 1")
	eq(Career.formatOf([]), "")


func test_a_report_without_players_reads_as_a_duel_from_the_side_totals() -> void:
	# Reports saved before `players` existed have only the two sides.
	var report := H.emptyMatchReport()
	report.mapName = "Old"
	report.durationSec = 600
	report.you.trainedByType = {"knight": 4}
	report.you.unitsKilled = 9
	var cm := Career.careerMatch(report, {"at": 5, "won": false, "kind": "skirmish", "mode": "conquest", "difficulty": "lord", "commander": "warden"})
	eq(cm.players, 2)
	eq(cm.faction, "")
	eq(cm.format, "")
	eq(cm.kills, 9)
	eq(cm.difficulty, "lord")
	eq(cm.commander, "warden")
	eq(cm.ranked, false)
	check(not cm.has("foe"), "no enemies, no foe average (left out, as JSON leaves undefined)")
	check(H.deep_eq(cm.ageTimes, [0]), "ageTimes default")


func test_career_log_and_totals_are_the_json_the_ts_build_writes() -> void:
	Career.recordCareer(m({"at": 1_700_000_000_000.0, "tcIdleShare": 0.123456789}))
	var raw: String = Storage.getItem(Career.LOG_KEY)
	check(raw.begins_with('[{"at":1700000000000,"kind":"skirmish"'), "log JSON: " + raw.substr(0, 60))
	check(raw.contains('"tcIdleShare":0.123456789,'), "fractions keep their digits")
	# A log written by the TS build reads back.
	Storage.setItem(Career.LOG_KEY, '[{"at":5,"kind":"online","ranked":false,"mode":"conquest","difficulty":"","won":true,"faction":"legion","commander":"","oaths":[],"map":"M","players":2,"format":"1 v 1","allies":[],"foes":["norse"],"durationSec":600,"defeatedAt":-1,"age":1,"ageTimes":[0,300],"score":1,"gathered":2,"gatheredBy":{"food":1,"wood":1,"gold":0},"spent":1,"kills":3,"losses":1,"razed":0,"buildingsLost":0,"damageDealt":5,"damageTaken":5,"peakArmy":4,"peakVillagers":9,"idleVillagerTime":0,"tcIdleShare":0,"upgrades":0,"trained":{"militia":4},"killed":{},"lost":{},"built":{}}]')
	Storage.removeItem(Career.TOTALS_KEY)
	var c: Dictionary = Career.careerFor("online").career
	eq(c.all.played, 1)
	eq(c.all.kills, 3)
	eq(Career.careerTotals().byFaction.legion.won, 1, "totals rebuilt from the log when missing")
