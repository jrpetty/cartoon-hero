extends TestCase
# Ported from "The Career screen" in game/src/meta/career.test.ts, plus the
# Playstyle tab (keywords, what you do well / what's costing you), the
# filters, the replays list and the text-fitting helper.

const U := preload("res://tests/ui_test_helpers.gd")
const FACTIONS := ["kingdom", "legion", "norse", "shogunate", "khanate", "ascendancy"]


func frame(s: CareerScreen, p: Profile, W: int = 1600, H: int = 900, mx: float = 400, my: float = 300, clicked: bool = false, wheel: float = 0.0):
	U.begin(U.ctx(W, H), mx, my, clicked, wheel)
	var r = s.draw(W, H, 1, p)
	UI.ui.flushTooltip(W, H)
	return r


func record_forty() -> void:
	for i in 40:
		Career.recordCareer(U.career_match({
			"won": i % 3 != 0, "faction": FACTIONS[i % 6], "foes": [FACTIONS[(i + 2) % 6]], "kind": "online" if i % 4 == 0 else "skirmish",
			"ranked": i % 8 == 0, "map": ["Highlands", "Riverlands", "Open Plains"][i % 3], "oaths": ["sword", "lance"] if i % 2 else ["plough"],
			"difficulty": ["squire", "knight", "lord"][i % 3], "durationSec": 400.0 + i * 30,
		}))


func test_draws_every_tab_and_filter_empty_or_full_at_any_size() -> void:
	U.fresh_store()
	var p := Profile.new()
	var s := CareerScreen.new()
	var tabs := ["overview", "style", "factions", "maps", "units", "matches", "replays"]
	for t in tabs:
		s.tab = t
		frame(s, p)  # a script error fails the test
	record_forty()
	for f in ["all", "skirmish", "online", "ranked"]:
		s.filter = f
		s.cache = null
		for t in tabs:
			s.tab = t
			for size in [[1600, 900], [1024, 640]]:
				frame(s, p, size[0], size[1])


func test_an_empty_career_says_so() -> void:
	U.fresh_store()
	var rec := U.install()
	var s := CareerScreen.new()
	frame(s, Profile.new())
	check(rec.find_text("No games yet") != null, "empty")
	U.uninstall()


func test_reads_your_playstyle_with_keywords_and_a_verdict() -> void:
	U.fresh_store()
	# A Man-at-Arms rusher, twelve games of it.
	for i in 12:
		Career.recordCareer(U.career_match({"won": i % 4 != 3, "firstHitAt": 290.0, "firstAttackAt": 300.0, "durationSec": 700.0,
			"trained": {"villager": 22.0, "militia": 26.0, "archer": 3.0}, "faction": "kingdom"}))
	var rec := U.install()
	var s := CareerScreen.new()
	s.tab = "style"
	frame(s, Profile.new())
	var p := s.style()
	check(p.primary != null, "a style was recognised")
	eq(p.title, "Man-at-Arms Rush", "the title")
	check(rec.find_text("Man-at-Arms Rush") != null, "the title is drawn")
	check(rec.find_text("IN A FEW WORDS") != null, "keywords heading")
	for k in p.keywords:
		check(rec.find_text(k) != null, "keyword '%s' drawn" % k)
	check(rec.find_text("YOUR GAMES, HONESTLY") != null, "the verdict heading")
	check(rec.find_text("WHAT YOU DO WELL") != null and rec.find_text("WHAT'S COSTING YOU") != null, "good and bad sides")
	check(rec.find_text("+") != null or p.good.is_empty(), "a good mark per line")
	check(rec.find_text("WHY YOU'RE A MAN-AT-ARMS RUSH") != null or rec.find_text("WHY YOU'RE A RUSHER") != null, "the evidence heading")
	# The overview's one line, and its button, lead here.
	s.tab = "overview"
	rec.clear()
	frame(s, Profile.new())
	var why = rec.find_button("Why? →")
	check(why != null, "the Why? button")
	frame(s, Profile.new(), 1600, 900, why.x + 5, why.y + 5, true)
	eq(s.tab, "style", "Why? opens the Playstyle tab")
	U.uninstall()


func test_filters_and_tabs_are_buttons() -> void:
	U.fresh_store()
	record_forty()
	var rec := U.install()
	var s := CareerScreen.new()
	var p := Profile.new()
	frame(s, p)
	var online = rec.find_button("Online")
	frame(s, p, 1600, 900, online.x + 5, online.y + 5, true)
	eq(s.filter, "online")
	rec.clear()
	frame(s, p)
	var units = rec.find_button("Units")
	frame(s, p, 1600, 900, units.x + 5, units.y + 5, true)
	eq(s.tab, "units")
	rec.clear()
	frame(s, p)
	var back = rec.find_button("⟵  Back")
	eq(frame(s, p, 1600, 900, back.x + 5, back.y + 5, true), "back")
	U.uninstall()


func test_scrolls_a_long_tab_and_clamps() -> void:
	U.fresh_store()
	record_forty()
	var s := CareerScreen.new()
	var p := Profile.new()
	frame(s, p)
	check(s.contentH > 900, "the overview is longer than a window")
	for i in 40:
		frame(s, p, 1600, 900, 800, 400, false, 400)
	var viewH := 900 - 134 - 64
	eq(s.scroll, maxf(0, s.contentH - viewH), "scrolled to the end")
	s.tab = "matches"
	s.scroll = 0
	frame(s, p)
	eq(s.scroll, 0)


func replay(id: String, at: float) -> Dictionary:
	return {"version": 1.0, "id": id, "savedAt": at, "kind": "skirmish", "names": ["You", "AI 2"], "pov": 0.0, "commands": [], "endTick": 2400.0,
		"summary": {"map": "Highlands", "players": 2.0, "durationSec": 120.0, "result": "Victory", "factions": ["legion", "norse"]}}


func test_lists_replays_to_watch_and_deletes_on_the_second_click() -> void:
	U.fresh_store()
	Replay.saveReplay(replay("r1", 1_790_000_000_000.0))
	var rec := U.install()
	var s := CareerScreen.new()
	s.showReplays()
	var p := Profile.new()
	frame(s, p)
	var watch = rec.find_button("▶ Watch")
	check(watch != null, "a Watch button")
	var r = frame(s, p, 1600, 900, watch.x + 5, watch.y + 5, true)
	check(r is Dictionary and r.watch.id == "r1", "Watch hands the replay to the app")
	rec.clear()
	frame(s, p)
	var del = rec.find_button("Delete")
	frame(s, p, 1600, 900, del.x + 5, del.y + 5, true)
	eq(Replay.listReplays().size(), 1, "the first click only arms it")
	rec.clear()
	frame(s, p)
	var sure = rec.find_button("Sure?")
	check(sure != null and sure.opts.get("danger"), "armed, in red")
	frame(s, p, 1600, 900, sure.x + 5, sure.y + 5, true)
	eq(Replay.listReplays().size(), 0, "deleted")
	U.uninstall()


func test_fit_text_shrinks_then_cuts() -> void:
	var rec := U.install()
	var c := U.ctx()
	U.begin(c)
	CareerScreen.fitText("Short", 0, 0, 500, {"size": 20})
	CareerScreen.fitText("A considerably longer line of text than fits", 0, 0, 120, {"size": 20, "bold": true})
	eq(rec.texts[0].s, "Short", "fits as it is")
	var cut: String = rec.texts[1].s
	check(cut.ends_with("…"), "cut with an ellipsis: %s" % cut)
	c.font = "bold 15px 'Trebuchet MS', sans-serif"
	check(c.measureText(cut).width <= 120 + 0.5, "and it fits at 75% size")
	U.uninstall()


func test_formats_like_the_ts() -> void:
	eq(CareerScreen.mmss(0), "—")
	eq(CareerScreen.mmss(754.4), "12:34")
	eq(CareerScreen.mmss(3725), "1h 02m")
	eq(CareerScreen.hours(5400), "1.5 h")
	eq(CareerScreen.hours(1200), "20 min")
	eq(CareerScreen.pct(0.555), "56%")
	eq(CareerScreen.facName("legion"), "The Legion")
	eq(CareerScreen.facName(""), "—")
	eq(CareerScreen.diffName("nobody"), "Nobody")
	eq(CareerScreen.rateColor(0.6, 3), CareerScreen.GOOD)
	eq(CareerScreen.rateColor(0.6, 0), CareerScreen.FAINT)
	# 2026-10-21 12:00 UTC, as an en-US browser writes the date (in local time).
	var d := CareerScreen.localDate(1_792_584_000_000.0)
	eq(CareerScreen.localeDate(1_792_584_000_000.0), "%d/%d/%d" % [d.month, d.day, d.year])


func test_rebuilds_the_career_when_a_match_is_recorded() -> void:
	# The career is rebuilt only when what is stored changes (Godot: re-parsing
	# a long log twice a second is a hitch) — and it must still pick up a game
	# finished while the screen is open, on the next check.
	U.fresh_store()
	record_forty()
	var s := CareerScreen.new()
	var p := Profile.new()
	frame(s, p)
	var played: float = s.data().career.all.played
	var at: float = s.cache.at
	s.cache.checked -= 1000
	frame(s, p)
	eq(s.cache.at, at, "nothing changed: not rebuilt")
	Career.recordCareer(U.career_match())
	s.cache.checked -= 1000
	frame(s, p)
	eq(s.data().career.all.played, played + 1, "the new game counts")
	check(s.cache.at != at or s.cache.raw != [], "rebuilt")
