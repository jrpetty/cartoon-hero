extends TestCase
# Ported from game/src/ui/postmatch_render.test.ts, plus the screen's buttons.
#
# The post-match graph only draws after a match, which a menu-only check never
# reaches: render it, every tab, at three sizes, and an empty report too.

const U := preload("res://tests/ui_test_helpers.gd")


## A report with plausible numbers on both sides.
static func sampleReport() -> Dictionary:
	var r := Metrics.emptyMatchReport()
	r.mapName = "Highlands"
	r.durationSec = 240.0
	r.you.merge({
		"teams": [0], "score": 4200.0, "gathered": 3200.0, "gatheredBy": {"food": 1400.0, "wood": 1200.0, "gold": 600.0},
		"spent": 2900.0, "spentOn": {"units": 1700.0, "buildings": 800.0, "tech": 400.0}, "banked": 300.0,
		"unitsKilled": 30.0, "unitsLost": 12.0, "buildingsRazed": 4.0, "buildingsLost": 1.0,
		"damageDealt": 8400.0, "damageTaken": 6100.0, "peakArmy": 24.0, "peakVillagers": 32.0, "idleVillagerTime": 41.0,
		"age": 2, "upgrades": 6,
		"trainedByType": {"militia": 12.0, "archer": 9.0, "knight": 5.0},
		"lostByType": {"militia": 7.0, "archer": 4.0},
		"killedByType": {"spearman": 11.0, "archer": 8.0, "villager": 6.0},
		"builtByType": {"house": 6.0, "barracks": 2.0},
	}, true)
	r.foe.merge({
		"teams": [1], "score": 3600.0, "gathered": 2600.0, "gatheredBy": {"food": 1100.0, "wood": 1000.0, "gold": 500.0},
		"spent": 2100.0, "spentOn": {"units": 1300.0, "buildings": 600.0, "tech": 200.0}, "banked": 500.0,
		"unitsKilled": 18.0, "unitsLost": 26.0, "buildingsRazed": 2.0, "buildingsLost": 5.0,
		"damageDealt": 6100.0, "damageTaken": 8400.0, "peakArmy": 19.0, "peakVillagers": 27.0, "idleVillagerTime": 120.0,
		"age": 2, "upgrades": 3,
		"trainedByType": {"spearman": 14.0, "archer": 10.0},
		"lostByType": {"spearman": 11.0, "archer": 8.0, "villager": 6.0},
		"killedByType": {"militia": 7.0, "archer": 4.0},
		"builtByType": {"house": 5.0, "barracks": 1.0},
	}, true)
	return r


static func graph() -> Dictionary:
	var n := 12
	var ts: Array = []
	for i in n:
		ts.append(i * 20.0)
	var ramp := func(k: float) -> Array:
		var a: Array = []
		for i in n:
			a.append(i * k + 5)
		return a
	return {
		"ts": ts,
		"mine": {"score": ramp.call(40.0), "military": ramp.call(12.0), "economy": ramp.call(120.0)},
		"foe": {"score": ramp.call(35.0), "military": ramp.call(10.0), "economy": ramp.call(110.0)},
	}


static func rewards() -> Dictionary:
	return Progression.computeRewards({"win": true, "durationSec": 240.0, "unitsKilled": 30.0, "buildingsRazed": 4.0, "difficulty": "knight", "fairMode": false})


func test_draws_the_progression_chart_and_every_tab_without_errors() -> void:
	U.fresh_store()
	Screens.setMouseDown(false)
	var screen := Screens.PostMatchScreen.new()
	var report := sampleReport()
	var g := graph()
	var rw := rewards()
	for m in ["score", "military", "economy"]:
		screen.graphMetric = m
		U.begin(U.ctx(1280, 760))
		screen.draw(1280, 760, 1.0, 0.016, true, report, rw, Profile.load(), 0, 0, g)
	# Every report tab, at three window sizes — the report is the tallest thing
	# on the screen and the one most likely to overflow a short window.
	for tab in ["overview", "feedback", "economy", "military", "units"]:
		screen.reportTab = tab
		for size in [[1280, 760], [1600, 900], [1100, 680]]:
			U.begin(U.ctx(size[0], size[1]))
			screen.draw(size[0], size[1], 1.0, 0.016, true, report, rw, Profile.load(), 0, 0, g)
	# An empty report (a match that ended immediately) and a null graph must
	# also be safe — every one of those ratios divides by something.
	U.begin(U.ctx(1280, 760))
	screen.draw(1280, 760, 1.0, 0.016, false, Metrics.emptyMatchReport(), rw, Profile.load(), 0, 0, null)


func test_tabs_graph_metric_and_continue() -> void:
	U.fresh_store()
	var rec := U.install()
	var screen := Screens.PostMatchScreen.new()
	var report := sampleReport()
	var g := graph()
	var rw := rewards()
	var c := U.ctx()
	var step := func(mx: float, my: float, clicked: bool):
		rec.clear()
		U.begin(c, mx, my, clicked)
		return screen.draw(1600, 900, 1.0, 0.016, true, report, rw, Profile.load(), 0, 0, g)
	step.call(-99, -99, false)
	var mil = rec.find_button("Military")
	step.call(mil.x + 5, mil.y + 5, true)
	eq(screen.reportTab, "military", "report tab")
	step.call(-99, -99, false)
	var eco = rec.find_button("Eco")
	step.call(eco.x + 5, eco.y + 5, true)
	eq(screen.graphMetric, "economy", "graph metric")
	step.call(-99, -99, false)
	var cont = rec.find_button("Continue")
	eq(step.call(cont.x + 5, cont.y + 5, true), "continue")
	screen.reset()
	eq(screen.reportTab, "overview", "reset for the next match")
	U.uninstall()


func test_shows_spoils_the_level_bar_awards_and_the_rival() -> void:
	U.fresh_store()
	var rec := U.install()
	var screen := Screens.PostMatchScreen.new()
	screen.rivalLines = ["Morwen the Patient flees with a scar."]
	var rw := rewards()
	var awards := [{"id": "a", "name": "Siege Breaker", "valor": 60.0, "weekly": false}]
	U.begin(U.ctx())
	screen.draw(1600, 900, 1.0, 2.0, true, sampleReport(), rw, Profile.load(), 0, 1, graph(), awards)
	check(rec.find_text("Morwen the Patient flees with a scar.") != null, "the rival's fate")
	check(rec.find_text("Spoils of War") != null, "spoils")
	check(rec.find_text("+%s XP  +%s ✦  +%s ⚔" % [JS.num(rw.xp), JS.num(rw.renown), JS.num(rw.valor)]) != null, "the total")
	check(rec.find_text("Siege Breaker") != null, "the award")
	check(rec.find_text("LEVEL UP! +1") != null, "the level-up, once the bar has filled")
	check(rec.find_text("VICTORY") != null, "the result")
	check(rec.find_text("Highlands  ·  1v1  ·  4:00") != null, "no players: the format comes from the sides")
	U.uninstall()


func test_subtitle_names_map_format_and_length() -> void:
	var r := sampleReport()
	eq(MatchReport.reportSubtitle(r), "Highlands  ·  1v1  ·  4:00")
