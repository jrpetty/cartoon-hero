extends TestCase
# Ported from game/src/ui/match_report.test.ts.
#
# The end-of-match report used to fold every match into "you against them".
# In a four-way free-for-all that added three rivals into one "Opponent" and
# called it 1v3; in a 2v2 it hid whether you or your ally carried the game.

const U := preload("res://tests/ui_test_helpers.gd")


func match(alliances: Array) -> World:
	var n := alliances.size()
	var w := World.new(5)
	var loadouts: Array = []
	var econ: Array = []
	for i in n:
		loadouts.append({})
		econ.append(1.0)
	w.init(MapGen.generateMap("open_plains", 5, n, false, alliances), loadouts, econ, alliances)
	# Give every realm a different, recognisable ledger.
	for t in n:
		var s: Dictionary = w.player(t).stats
		s.unitsKilled = 10.0 * (t + 1)
		s.gathered = 1000.0 * (t + 1)
		s.trainedByType = {"spearman": float(t + 1), "archer": float(2 * t + 1), "villager": 12.0}
	return w


## A report with `n` realms and plausible numbers, without running a match.
func synthetic(alliances: Array, me: int = 0) -> Dictionary:
	return Metrics.matchReport(match(alliances), me, "Highlands")


func test_reports_each_player_of_a_free_for_all_separately() -> void:
	var r := synthetic([0, 1, 2, 3])
	eq(r.players.size(), 4)
	eq(r.players.map(func(p): return int(p.unitsKilled)), [10, 20, 30, 40], "kills per realm")
	eq(r.players.map(func(p): return p.relation), ["you", "enemy", "enemy", "enemy"], "relations")
	# The alliance totals are still there, and still agree.
	eq(r.foe.unitsKilled, 20 + 30 + 40)
	eq(MatchReport.matchFormat(r), "4-player free-for-all")


func test_knows_who_is_on_your_side_in_a_team_game() -> void:
	var r := synthetic([0, 0, 0, 0, 1, 1, 1, 1], 1)
	eq(r.players.map(func(p): return p.relation), ["ally", "you", "ally", "ally", "enemy", "enemy", "enemy", "enemy"], "relations")
	eq(MatchReport.matchFormat(r), "4v4")
	eq(MatchReport.matchFormat(synthetic([0, 0, 1, 1])), "2v2")
	eq(MatchReport.matchFormat(synthetic([0, 1, 2, 3, 4, 5, 6, 7])), "8-player free-for-all")
	eq(MatchReport.matchFormat(synthetic([0, 1])), "1v1")


func test_remembers_when_each_realm_fell() -> void:
	var w := match([0, 1, 2, 3])
	for i in 20 * 5:
		w.tick()
	for e in w.entities:
		if e.team == 2 and (e.kind == T.Kind.Building or e.kind == T.Kind.Unit):
			e.alive = false
	var at := w.time
	for i in 20 * 3:
		w.tick()
	var p = w.player(2)
	eq(p.defeated, true, "defeated")
	check(p.defeatedAt >= at and p.defeatedAt < at + 3, "fell when it lost everything (%s vs %s)" % [p.defeatedAt, at])
	eq(w.player(1).defeatedAt, -1, "a standing realm has no time of defeat")
	var r := Metrics.matchReport(w, T.Team.Player, "x")
	eq(r.players[2].defeatedAt, p.defeatedAt)


func p(team: int, over: Dictionary) -> Dictionary:
	var d: Dictionary = Metrics.emptyMatchReport().you
	d.merge({"team": team, "group": team, "relation": "you" if team == 0 else "enemy", "horde": false, "won": false, "defeated": false, "defeatedAt": -1.0}, true)
	d.merge(over, true)
	return d


func test_puts_the_winner_first_and_ranks_the_fallen_by_how_long_they_lasted() -> void:
	var st := MatchReport.standings([
		p(0, {"score": 900.0, "defeated": true, "defeatedAt": 300.0}),
		p(1, {"score": 100.0, "won": true}),
		p(2, {"score": 500.0, "defeated": true, "defeatedAt": 420.0}),
		p(3, {"score": 800.0, "defeated": true, "defeatedAt": 120.0}),
	])
	eq(st.ffa, true)
	# Score doesn't decide who finished ahead: outlasting someone does.
	eq(st.groups.map(func(g): return g.members[0].team), [1, 2, 0, 3], "order")


func test_keeps_allies_together_best_side_first() -> void:
	var st := MatchReport.standings([
		p(0, {"group": 0, "score": 300.0}), p(1, {"group": 1, "score": 900.0, "won": true}),
		p(2, {"group": 0, "score": 700.0}), p(3, {"group": 1, "score": 200.0, "won": true}),
	])
	eq(st.ffa, false)
	eq(st.groups.map(func(g): return g.members.map(func(m): return m.team)), [[1, 3], [2, 0]], "groups")


# The report's body rect on the post-match screen at two window sizes.
const RECTS := [
	{"name": "1600×900", "x": 144, "y": 182, "w": 908, "h": 618},
	{"name": "1280×760", "x": 48, "y": 182, "w": 780, "h": 478},
]


func names_every_realm(label: String, alliances: Array) -> void:
	var r := synthetic(alliances)
	check(MatchReport.isStandings(r), "%s is standings" % label)
	var names: Array = r.players.map(func(pp): return MatchReport.playerName(pp))
	var rec := U.install()
	var c := U.ctx()
	for rect in RECTS:
		# Feedback is sentences about you, not a table of every realm.
		for tab in MatchReport.REPORT_TABS:
			if tab.id == "feedback":
				continue
			U.begin(c)
			rec.clear()
			MatchReport.drawReportTab(tab.id, rect.x, rect.y, rect.w, rect.h, r)
			for n in names:
				var at = rec.find_text(n)
				check(at != null, "%s missing from %s at %s (%s)" % [n, tab.label, rect.name, label])
				if at != null:
					check(at.y <= rect.y + rect.h, "%s drawn below the panel on %s at %s (%s)" % [n, tab.label, rect.name, label])
	U.uninstall()


func test_names_every_realm_on_every_tab_inside_the_panel_ffa4() -> void:
	names_every_realm("4-player free-for-all", [0, 1, 2, 3])


func test_names_every_realm_on_every_tab_inside_the_panel_2v2() -> void:
	names_every_realm("2v2", [0, 0, 1, 1])


func test_names_every_realm_on_every_tab_inside_the_panel_ffa8() -> void:
	names_every_realm("8-player free-for-all", [0, 1, 2, 3, 4, 5, 6, 7])


func test_names_every_realm_on_every_tab_inside_the_panel_4v4() -> void:
	names_every_realm("4v4", [0, 0, 0, 0, 1, 1, 1, 1])


func test_names_every_realm_on_every_tab_inside_the_panel_8v8() -> void:
	names_every_realm("8v8", [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1])


func test_still_draws_a_duel_as_a_duel_with_both_realms_named_in_the_key() -> void:
	var r := synthetic([0, 1])
	eq(MatchReport.isStandings(r), false)
	var rec := U.install()
	U.begin(U.ctx())
	MatchReport.drawReportKey(1000, 140, r)
	eq(rec.texts.map(func(t): return t.s), ["%s (you)" % MatchReport.playerName(r.players[0]), MatchReport.playerName(r.players[1])], "key")
	U.uninstall()


func test_opens_a_report_saved_before_per_player_stats_existed() -> void:
	var r := synthetic([0, 1, 2, 3])
	r.erase("players")
	eq(MatchReport.isStandings(r), false)
	eq(MatchReport.matchFormat(r), "1v3")
	var c := U.ctx()
	for tab in MatchReport.REPORT_TABS:
		U.begin(c)
		MatchReport.drawReportTab(tab.id, 48, 182, 780, 478, r)  # a script error fails the test


func test_says_what_went_well_and_what_cost_you_inside_the_panel() -> void:
	var r := Metrics.emptyMatchReport()
	r.durationSec = 1200.0
	r.you.merge({"teams": [0], "gathered": 20000.0, "unitsKilled": 40.0, "unitsLost": 10.0, "tcSeconds": 1200.0, "idleTcTime": 60.0, "buildingsRazed": 4.0, "firstAttackAt": 300.0, "killsAttacking": 12.0, "killsByUnit": {"archer": 30.0}}, true)
	r.foe.merge({"teams": [1], "gathered": 10000.0, "unitsKilled": 10.0, "unitsLost": 40.0, "killsDefending": 4.0, "lostByType": {"villager": 10.0}}, true)
	var fb := Feedback.matchFeedback(r)
	check(" ".join(PackedStringArray(fb.good)).contains("out-gathered"), "out-gathered")
	check(" ".join(PackedStringArray(fb.good)).contains("early attack at 5:00 paid off"), "early attack")
	eq(fb.bad.size(), 0, "nothing bad")
	var bad := Metrics.emptyMatchReport()
	bad.durationSec = 1200.0
	bad.you.merge({"teams": [0], "gathered": 8000.0, "unitsKilled": 5.0, "unitsLost": 30.0, "tcSeconds": 1200.0, "idleTcTime": 600.0, "lostByType": {"villager": 12.0}, "buildingsLost": 5.0}, true)
	bad.foe.merge({"teams": [1], "gathered": 16000.0, "unitsKilled": 30.0, "unitsLost": 5.0, "firstAttackAt": 360.0, "killsAttacking": 15.0}, true)
	var fb2 := Feedback.matchFeedback(bad)
	check(fb2.bad.size() >= 3, "at least three costs")
	check(" ".join(PackedStringArray(fb2.bad)).contains("Their attack at 6:00 hurt"), "their attack")
	var rec := U.install()
	for rect in [{"w": 780, "h": 478}, {"w": 420, "h": 400}]:
		U.begin(U.ctx())
		rec.clear()
		MatchReport.drawReportTab("feedback", 20, 20, rect.w, rect.h, bad)
		check(rec.texts.size() > 4, "sentences drawn")
		for t in rec.texts:
			check(t.y <= 20 + rect.h, "'%s' inside the panel at %d wide" % [t.s, rect.w])
	U.uninstall()


func test_feedback_wraps_inside_its_column() -> void:
	# The wrapper is the report's own: every line it returns fits.
	var c := U.ctx()
	U.begin(c)
	var lines := MatchReport.wrap_("Your economy out-gathered theirs by a wide margin all match long, and it paid for everything.", 180, 13)
	check(lines.size() >= 2, "wrapped")
	c.font = "13px \"Trebuchet MS\", sans-serif"
	for l in lines:
		check(c.measureText(l).width <= 180 or not l.contains(" "), "'%s' fits" % l)


func test_number_formats_match_the_ts() -> void:
	eq(MatchReport.secs(59.6), "60s")
	eq(MatchReport.secs(185), "3m 5s")
	eq(MatchReport.mmss(754), "12:34")
	eq(MatchReport.ordinal(1), "1st")
	eq(MatchReport.ordinal(12), "12th")
	eq(MatchReport.ordinal(23), "23rd")
	eq(MatchReport.idleShare(30, 120), "25%")
	eq(MatchReport.idleShare(30, 0), "—")
	eq(MatchReport.num(13200), "13,200")
