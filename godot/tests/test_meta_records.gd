extends TestCase
# Port of game/src/meta/records.test.ts: the match history (keeping, reading,
# thinning reports, surviving full storage), achievements and the weekly
# challenge rotation.

const H := preload("res://tests/meta_helpers.gd")
const KEY := "banner_and_blade_history_v1"


func _init() -> void:
	Storage.use_memory_only()
	History.storage_override = null


static func rec(over := {}) -> Dictionary:
	return H.with({
		"at": 1_700_000_000_000.0,
		"won": true,
		"mapName": "Highlands",
		"mode": "conquest",
		"difficulty": "knight",
		"players": 2,
		"durationSec": 900,
		"score": 1000,
		"foeScore": 800,
		"gathered": 5000,
		"spent": 4000,
		"unitsKilled": 30,
		"unitsLost": 20,
		"buildingsRazed": 4,
		"buildingsLost": 2,
		"peakArmy": 20,
		"peakVillagers": 30,
		"age": 2,
		"idleVillagerTime": 40,
		"idleTcTime": 60,
		"tcSeconds": 600,
		"favouriteUnit": "knight",
	}, over)


# ---- Keeping a match ----

func test_stores_a_game_and_reads_it_back() -> void:
	History.clearHistory()
	History.recordMatch(rec({"mapName": "Riverlands"}))
	var rows := History.listHistory()
	eq(rows.size(), 1)
	eq(rows[0].mapName, "Riverlands")


func test_keeps_the_newest_twenty_and_drops_the_rest() -> void:
	History.clearHistory()
	for i in History.HISTORY_LIMIT + 8:
		History.recordMatch(rec({"at": 1_700_000_000_000.0 + i * 1000, "mapName": "M%d" % i}))
	var rows := History.listHistory()
	eq(rows.size(), History.HISTORY_LIMIT)
	# Newest first, and the oldest eight are gone.
	eq(rows[0].mapName, "M%d" % (History.HISTORY_LIMIT + 7))
	check(not rows.any(func(r): return r.mapName == "M0"), "M0 dropped")


func test_survives_a_corrupt_store_rather_than_taking_the_menu_down_with_it() -> void:
	Storage.setItem(KEY, "{not json")
	eq(History.listHistory(), [])
	Storage.setItem(KEY, '[{"junk":true},null,3]')
	eq(History.listHistory(), [])


func test_boils_a_full_report_down_to_what_a_trend_needs() -> void:
	var report := H.emptyMatchReport()
	report.mapName = "Gauntlet"
	report.durationSec = 1234
	report.you.unitsKilled = 41
	report.you.trainedByType = {"villager": 60, "knight": 12, "archer": 5}
	var r := History.summarise(report, {"won": true, "mode": "koth", "difficulty": "duke", "players": 4, "at": 12345})
	eq(r.mapName, "Gauntlet")
	eq(r.unitsKilled, 41)
	eq(r.difficulty, "duke")
	# Villagers are trained most in nearly every game, and "you built
	# villagers" says nothing about how anyone plays.
	eq(r.favouriteUnit, "knight")


func test_reports_no_favourite_when_nothing_but_villagers_was_trained() -> void:
	var report := H.emptyMatchReport()
	report.you.trainedByType = {"villager": 40}
	var r := History.summarise(report, {"won": false, "mode": "conquest", "difficulty": "squire", "players": 2, "at": 1})
	eq(r.favouriteUnit, "")


# ---- Reading the record ----

func test_says_nothing_rather_than_dividing_by_zero_on_an_empty_history() -> void:
	var s := History.summariseHistory([])
	eq(s.played, 0)
	eq(s.winRate, 0)
	eq(s.killRatio, 0)


func test_counts_the_best_streak_in_the_order_the_games_were_played() -> void:
	# Stored newest-first, so a summary that trusted the array order would
	# read every streak backwards.
	var rows := [
		rec({"at": 5, "won": false}),
		rec({"at": 4, "won": true}),
		rec({"at": 3, "won": true}),
		rec({"at": 2, "won": true}),
		rec({"at": 1, "won": false}),
	]
	eq(History.summariseHistory(rows).bestStreak, 3)


func test_gives_kills_per_loss_and_does_not_divide_by_zero_on_a_flawless_run() -> void:
	eq(History.summariseHistory([rec({"unitsKilled": 40, "unitsLost": 10})]).killRatio, 4)
	eq(History.summariseHistory([rec({"unitsKilled": 7, "unitsLost": 0})]).killRatio, 7)


func test_ignores_games_where_nothing_stood_when_averaging_idle_time() -> void:
	var s := History.summariseHistory([
		rec({"idleTcTime": 300, "tcSeconds": 600}),
		rec({"idleTcTime": 0, "tcSeconds": 0}),  # a match that ended before the TC did anything
	])
	near(s.avgTcIdleShare, 0.5, 1e-5)


# ---- Achievements ----

func test_pays_for_a_win_once_and_only_once() -> void:
	var first := Achievements.evaluateAwards(rec(), {"earned": [], "challengesDone": []})
	check(first.any(func(a): return a.id == "first_blood"), "first blood paid")
	var again := Achievements.evaluateAwards(rec(), {"earned": ["first_blood"], "challengesDone": []})
	check(not again.any(func(a): return a.id == "first_blood"), "not paid twice")


func test_never_pays_out_for_losing() -> void:
	# Every achievement is a thing to do *while winning*. A flawless loss
	# earning "Not One Villager" would be a strange kind of consolation.
	var lost := rec({
		"won": false, "unitsLost": 0, "buildingsLost": 0, "durationSec": 60,
		"difficulty": "conqueror", "age": 3, "unitsKilled": 500, "players": 8,
		"peakVillagers": 99, "buildingsRazed": 99, "idleTcTime": 0,
	})
	eq(Achievements.evaluateAwards(lost, {"earned": [], "challengesDone": []}), [])


func test_recognises_a_flawless_win() -> void:
	var flawless := rec({"unitsLost": 0, "buildingsLost": 0})
	var ids := Achievements.evaluateAwards(flawless, {"earned": [], "challengesDone": []}).map(func(a): return a.id)
	check(ids.has("flawless_eco"), "flawless_eco")


func test_does_not_award_ten_to_one_when_nothing_was_lost_at_all() -> void:
	# That case is already Not One Villager; a ratio against zero is not a ratio.
	var t: Callable = Achievements.ACHIEVEMENTS_BY_ID.ten_to_one.test
	eq(t.call(rec({"unitsKilled": 50, "unitsLost": 0})), false)
	eq(t.call(rec({"unitsKilled": 50, "unitsLost": 5})), true)


func test_has_no_duplicate_ids_since_an_id_is_what_marks_one_as_paid() -> void:
	var ids := {}
	for a in Achievements.ACHIEVEMENTS:
		ids[a.id] = true
	eq(ids.size(), Achievements.ACHIEVEMENTS.size())
	var cids := {}
	for c in Achievements.CHALLENGE_POOL:
		cids[c.id] = true
	eq(cids.size(), Achievements.CHALLENGE_POOL.size())


func test_pays_something_for_every_achievement_so_none_is_a_booby_prize() -> void:
	for a in Achievements.ACHIEVEMENTS + Achievements.CHALLENGE_POOL:
		check(a.valor > 0, a.id + " valor")
		check(a.name.length() > 0, a.id + " name")
		check(a.desc.length() > 0, a.id + " desc")


# ---- Weekly challenges ----

func test_offers_three_at_a_time() -> void:
	eq(Achievements.challengesForWeek(0).size(), Achievements.CHALLENGES_PER_WEEK)


func test_shares_nothing_between_consecutive_weeks() -> void:
	# A rotation that felt random would keep re-offering the same two, which is
	# worse than a rotation you can see the shape of.
	for w in 40:
		var a := {}
		for c in Achievements.challengesForWeek(w):
			a[c.id] = true
		for c in Achievements.challengesForWeek(w + 1):
			check(not a.has(c.id), "week %d repeats %s" % [w, c.id])


func test_offers_three_distinct_challenges_within_one_week() -> void:
	for w in 40:
		var ids := {}
		var week := Achievements.challengesForWeek(w)
		for c in week:
			ids[c.id] = true
		eq(ids.size(), week.size(), "week %d" % w)


func test_visits_the_whole_pool_rather_than_favouring_a_few() -> void:
	var seen := {}
	for w in 40:
		for c in Achievements.challengesForWeek(w):
			seen[c.id] = true
	eq(seen.size(), Achievements.CHALLENGE_POOL.size())


func test_can_be_earned_again_in_a_later_week() -> void:
	# The difference between a challenge and an achievement: the key carries
	# the week, so last week's tick doesn't cancel this week's.
	var week := Achievements.weekIndex(rec().at)
	var target: Dictionary = Achievements.challengesForWeek(week)[0]
	var m := rec({"favouriteUnit": "knight", "durationSec": 300, "unitsLost": 1, "players": 8, "spent": 4900, "buildingsLost": 12, "peakArmy": 90, "difficulty": "conqueror"})
	var done := ["%s:%s" % [JS.num(week - 1), target.id]]
	var got := Achievements.evaluateAwards(m, {"earned": [], "challengesDone": done})
	# Whatever this week's set contains, an entry earned last week must not
	# block it.
	check(got.filter(func(a): return a.weekly).size() > 0, "a weekly award")


func test_keys_a_week_off_the_timestamp_not_the_wall_clock() -> void:
	eq(Achievements.weekIndex(0), 0)
	eq(Achievements.weekIndex(7 * 24 * 60 * 60 * 1000), 1)
	eq(Achievements.weekIndex(7 * 24 * 60 * 60 * 1000 - 1), 0)


func test_profile_claims_awards_once_and_pays_their_valor() -> void:
	# Profile.claimAwards is what the post-match screen calls with these.
	var p := Profile.new()
	var valor: float = p.data.valor
	var awards := Achievements.evaluateAwards(rec(), p.awardState())
	var fresh := p.claimAwards(awards)
	eq(fresh.size(), awards.size())
	eq(p.data.valor, valor + awards.reduce(func(n, a): return n + a.valor, 0.0))
	eq(p.claimAwards(awards), [], "a second claim pays nothing")
	eq(Achievements.evaluateAwards(rec(), p.awardState()).size(), 0, "nothing left to earn from the same game")


# ---- Keeping the report itself ----

func test_stores_the_whole_report_alongside_the_summary() -> void:
	History.clearHistory()
	var report := H.emptyMatchReport()
	report.mapName = "Riverlands"
	report.you.trainedByType = {"villager": 20, "knight": 9}
	History.recordMatch(History.summarise(report, {"won": true, "mode": "conquest", "difficulty": "duke", "players": 2, "at": 5}))
	var back: Dictionary = History.listHistory()[0]
	check(back.get("report") != null, "the report was dropped on the way in")
	eq(back.report.mapName, "Riverlands")
	eq(back.report.you.trainedByType.knight, 9)


func test_thins_older_games_back_to_their_summary_as_they_age() -> void:
	# Twenty full reports with four per-type maps a side is a few hundred
	# kilobytes of localStorage for something nobody scrolls back to. The
	# summary is what a trend needs; the report is what re-reading one game
	# needs, and only the newest few are still that game.
	History.clearHistory()
	for i in History.FULL_REPORT_KEEP + 4:
		var report := H.emptyMatchReport()
		report.mapName = "M%d" % i
		History.recordMatch(History.summarise(report, {"won": true, "mode": "conquest", "difficulty": "knight", "players": 2, "at": 100 + i}))
	var rows := History.listHistory()
	var withReport := rows.filter(func(r): return r.get("report") != null)
	eq(withReport.size(), History.FULL_REPORT_KEEP)
	# …and it is the newest few that kept theirs.
	eq(withReport.map(func(r): return r.mapName), rows.slice(0, History.FULL_REPORT_KEEP).map(func(r): return r.mapName))


func test_keeps_the_summary_when_the_report_is_gone_so_a_row_never_becomes_blank() -> void:
	History.clearHistory()
	for i in History.FULL_REPORT_KEEP + 2:
		var report := H.emptyMatchReport()
		report.you.unitsKilled = 10 + i
		History.recordMatch(History.summarise(report, {"won": i % 2 == 0, "mode": "koth", "difficulty": "lord", "players": 4, "at": 200 + i}))
	var rows := History.listHistory()
	var oldest: Dictionary = rows[rows.size() - 1]
	check(not oldest.has("report"), "report gone")
	eq(oldest.unitsKilled, 10)
	eq(oldest.difficulty, "lord")


func test_falls_back_to_summaries_rather_than_losing_the_game_when_storage_is_full() -> void:
	# A report is what blew the budget, so dropping reports and keeping the
	# record beats dropping the record.
	var store := H.FakeStore.new()
	History.storage_override = store
	History.clearHistory()
	store.fail_next = 1
	var report := H.emptyMatchReport()
	report.mapName = "Crowded"
	History.recordMatch(History.summarise(report, {"won": true, "mode": "conquest", "difficulty": "knight", "players": 2, "at": 9}))
	var rows := History.listHistory()
	History.storage_override = null
	eq(rows.size(), 1, "the whole record was lost rather than just the report")
	eq(rows[0].mapName, "Crowded")
	check(not rows[0].has("report"), "report dropped")
