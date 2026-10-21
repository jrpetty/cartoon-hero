extends TestCase
# Ported from game/src/ui/codex_render.test.ts, plus the tabs and Records.
#
# The Codex has to show everything the game now has — four ages, nine Oaths,
# six factions and thirty-nine units — without breaking or running off the
# screen. Every tab, every unit, at a small and a large window.

const U := preload("res://tests/ui_test_helpers.gd")


func frame(c: CodexScreen, W: int, H: int, wheel: float = 0.0, mx: float = 200, my: float = -1, clicked: bool = false):
	U.begin(U.ctx(W, H), mx, H / 2.0 if my < 0 else my, clicked, wheel)
	return c.draw(W, H, 1, Profile.new())


func test_draws_every_tab_and_every_unit() -> void:
	U.fresh_store()
	for size in [[1280, 760], [1600, 900]]:
		var c := CodexScreen.new()
		for tab in ["units", "buildings", "tech", "records"]:
			c.tab = tab
			frame(c, size[0], size[1])  # a script error fails the test
		c.tab = "units"
		for id in C.UNITS:
			c.selUnit = id
			frame(c, size[0], size[1])
		c.tab = "buildings"
		for id in C.BUILDINGS:
			c.selBuilding = id
			frame(c, size[0], size[1])


func test_scrolls_the_unit_list_to_its_end() -> void:
	U.fresh_store()
	var c := CodexScreen.new()
	c.tab = "units"
	for i in 20:
		frame(c, 1280, 760, 400)
	check(c.unitScroll > 0, "the unit list scrolled")
	var listH := 760 - 124 - 84
	eq(c.unitScroll, maxf(0, 12 + C.UNITS.size() * 38 - listH + 12), "to its end, and no further")


func test_scrolls_the_tech_page_and_measures_it() -> void:
	U.fresh_store()
	var c := CodexScreen.new()
	c.tab = "tech"
	frame(c, 1280, 760)
	check(c.techContentH > 760, "four ages, nine Oaths, six factions and the research are more than a screen")
	for i in 30:
		frame(c, 1280, 760, 400)
	check(c.techScroll > 0 and c.techScroll <= c.techContentH, "scrolled within the page")


func test_tabs_and_unit_rows_are_clickable() -> void:
	U.fresh_store()
	var rec := U.install()
	var c := CodexScreen.new()
	frame(c, 1600, 900)
	var b = rec.find_button("Buildings")
	frame(c, 1600, 900, 0, b.x + b.w / 2, b.y + b.h / 2, true)
	eq(c.tab, "buildings")
	b = rec.find_button("Units")
	frame(c, 1600, 900, 0, b.x + b.w / 2, b.y + b.h / 2, true)
	eq(c.tab, "units")
	# The third row of the list is the third unit.
	rec.clear()
	frame(c, 1600, 900)
	var rows: Array = rec.buttons.filter(func(x): return x.label == "" and x.h == 34)
	var third = rows[2]
	frame(c, 1600, 900, 0, third.x + 30, third.y + 17, true)
	eq(c.selUnit, C.UNITS.keys()[2])
	var back = rec.find_button("⟵ Back")
	eq(frame(c, 1600, 900, 0, back.x + 10, back.y + 10, true), "back")
	U.uninstall()


func test_records_list_opens_a_kept_report_and_comes_back() -> void:
	U.fresh_store()
	var r := Metrics.emptyMatchReport()
	r.mapName = "Highlands"
	r.durationSec = 600.0
	r.you.teams = [0]
	r.foe.teams = [1]
	History.recordMatch(History.summarise(r, {"won": true, "mode": "conquest", "difficulty": "knight", "players": 2, "at": 1_790_000_000_000.0}))
	var rec := U.install()
	var c := CodexScreen.new()
	c.tab = "records"
	frame(c, 1600, 900)
	check(rec.find_text("WIN") != null, "the row")
	var rep = rec.find_button("Report")
	check(rep != null, "a kept report has a button")
	frame(c, 1600, 900, 0, rep.x + 5, rep.y + 5, true)
	check(c.openReport != null, "opened")
	rec.clear()
	frame(c, 1600, 900)
	check(rec.find_text("Victory — Highlands") != null, "the report's title")
	var eco = rec.find_button("Economy")
	frame(c, 1600, 900, 0, eco.x + 5, eco.y + 5, true)
	eq(c.reportTab, "economy")
	rec.clear()
	frame(c, 1600, 900)
	var back = rec.find_button("‹ Back to records")
	frame(c, 1600, 900, 0, back.x + 5, back.y + 5, true)
	eq(c.openReport, null, "back to the list")
	U.uninstall()


func test_formats_costs_as_the_ts() -> void:
	eq(CodexScreen.fmtCost({"food": 60.0, "wood": 0.0, "gold": 75.0}), "🍖60  🪙75")
	eq(CodexScreen.fmtCost({"food": 0.0, "wood": 0.0, "gold": 0.0}), "Free")
	eq(CodexScreen.mmssOf(754.9), "12:34")
