extends TestCase
# Ported from game/src/ui/setup_screen.test.ts, faction_setup.test.ts and the
# Skirmish-list case of game/src/maps/map_pool.test.ts, plus the roster.
#
# The Skirmish setup screen has to stay usable at any window height. It lays
# its panels out by accumulating `y` downward, so how tall it ends up depends
# on how many battlefield cards exist; a single custom map once put "To
# Battle!" below the bottom of the screen with no way to reach it.

const U := preload("res://tests/ui_test_helpers.gd")
const FOOTER := 76
const TOP := 104


class Harness:
	var W: int
	var H: int
	var s := Screens.SetupScreen.new()
	var profile := Profile.new()
	var c: Ctx
	var colW: float
	var x0: float
	var footerY: float

	func _init(w: int, h: int) -> void:
		W = w
		H = h
		c = U.ctx(w, h)
		# The screen's outer frame: two columns inside it, the action bar under it.
		colW = minf(W - 64, 1440)
		x0 = JS.round(W / 2.0 - colW / 2)
		footerY = H - FOOTER + 16

	func frame(mx: float = -99, my: float = -99, clicked: bool = false, wheel: float = 0.0):
		U.begin(c, mx, my, clicked, wheel)
		return s.draw(W, H, 1, profile)


func before() -> void:
	U.fresh_store()


func test_starts_spectates_and_goes_back_at_every_size() -> void:
	before()
	for size in [[1920, 1010], [1920, 700], [1280, 620], [1024, 560]]:
		var h := Harness.new(size[0], size[1])
		h.frame() # lay out once so the scroll clamp has a content height
		var mid := h.footerY + 22
		eq(h.frame(h.x0 + h.colW - 110, mid, true), "start", "To Battle! at %s" % str(size))
		eq(h.frame(h.x0 + h.colW - 295, mid, true), "spectate", "Watch at %s" % str(size))
		eq(h.frame(h.x0 + 65, mid, true), "back", "Back at %s" % str(size))


func test_is_genuinely_taller_than_the_window_so_the_fix_is_load_bearing() -> void:
	before()
	# Two columns fit a tall window now, so check the short ones.
	for size in [[1920, 700], [1280, 620], [1024, 560]]:
		var h := Harness.new(size[0], size[1])
		h.frame()
		check(h.s.contentH > size[1] - TOP - FOOTER, "the setup content fits %s, so nothing above is proving anything" % str(size))


func test_scrolling_moves_the_content_and_takes_hit_testing_with_it() -> void:
	before()
	# The half of the fix that is easy to get wrong: the context is translated,
	# so the pointer has to move by the same amount or every widget below the
	# fold responds to a click somewhere else entirely.
	var h := Harness.new(1920, 700)
	h.frame()
	var cardX := h.x0 + 16 + 60
	h.s.scroll = 0
	h.frame(cardX, 200, true)
	var atTop: String = h.s.config.presetId
	# Scroll down two card rows and click the same point on screen.
	h.frame(cardX, 300, false, 400)
	check(h.s.scroll > 0, "the wheel did not scroll anything")
	h.frame(cardX, 200, true)
	check(h.s.config.presetId != atTop, "the same screen point selected the same card after scrolling")


func test_never_scrolls_past_the_end_or_above_the_start() -> void:
	before()
	var h := Harness.new(1920, 700)
	h.frame()
	for i in 20:
		h.frame(900, 300, false, 400)
	var viewH := 700 - TOP - FOOTER
	check(h.s.scroll <= maxf(0, h.s.contentH - viewH) + 1, "clamped at the end")
	for i in 40:
		h.frame(900, 300, false, -400)
	eq(h.s.scroll, 0, "back at the top")


func test_ignores_the_wheel_when_the_pointer_is_over_the_pinned_bar() -> void:
	before()
	# Otherwise the content slides about while you are aiming at "To Battle!".
	var h := Harness.new(1920, 700)
	h.frame()
	h.frame(900, 300, false, 400)
	var before_ := h.s.scroll
	h.frame(900, h.footerY + 22, false, 400)
	eq(h.s.scroll, before_)


func test_renders_at_every_size() -> void:
	before()
	for size in [[640, 480], [1920, 1080], [2560, 1440], [900, 400]]:
		var h := Harness.new(size[0], size[1])
		h.frame()
		h.frame(500, 300)  # a script error fails the test


# ----------------------------------------------------------- the roster --

func button_at(rec, label: String, row_y: float = -1):
	for b in rec.buttons:
		if b.label == label and (row_y < 0 or absf(b.y - row_y) < 30):
			return b
	return null


func test_team_presets_fill_blocks_and_a_seat_can_be_moved_by_hand() -> void:
	before()
	var rec := U.install()
	var h := Harness.new(1920, 1400)
	h.s.config.players = 8
	h.frame()
	eq(Teams.formatLabel(h.s.config.teams), "8-player free-for-all", "starts free-for-all")
	var two = rec.find_button("2 teams")
	h.frame(two.x + two.w / 2, two.y + two.h / 2, true)
	eq(h.s.config.teams, [1, 1, 1, 1, 2, 2, 2, 2], "2 teams")
	rec.clear()
	h.frame()
	var three = rec.find_button("3 teams")
	h.frame(three.x + three.w / 2, three.y + three.h / 2, true)
	eq(h.s.config.teams, [1, 1, 1, 2, 2, 2, 3, 3], "3 teams")
	# Seat 8's "–" button: fight alone.
	rec.clear()
	h.frame()
	var alone: Array = rec.buttons.filter(func(b): return b.label == "–" and b.w < 40 and b.opts.get("tooltip", [""])[0] == "Alone")
	eq(alone.size(), 8, "one 'alone' button per seat")
	var last = alone[7]
	h.frame(last.x + last.w / 2, last.y + last.h / 2, true)
	eq(h.s.config.teams[7], 0, "seat 8 alone")
	eq(h.s.teamPreset, "custom", "a hand edit makes the layout custom")
	# Adding a seat keeps the custom teams and adds a solo seat.
	h.s.config.players = 7
	h.frame()
	eq(h.s.config.teams.size(), 7, "resized")
	eq(h.s.config.teams.slice(0, 3), [1, 1, 1], "kept")
	U.uninstall()


func test_seat_count_buttons_and_survival_puts_everyone_together() -> void:
	before()
	var rec := U.install()
	var h := Harness.new(1920, 1400)
	h.frame()
	var plus = rec.find_button("+")
	h.frame(plus.x + plus.w / 2, plus.y + plus.h / 2, true)
	eq(h.s.config.players, 3, "a seat added")
	h.frame()
	eq(h.s.config.teams.size(), 3, "a team slot per seat")
	h.s.config.mode = "survival"
	h.frame()
	eq(h.s.config.teams, [1, 1, 1], "survival: one side")
	eq(Teams.teamsValid(h.s.config.teams), false, "and so no sides to fight")
	# One side is not a match: the start buttons are off.
	var start = rec.find_button("⚔  To Battle!")
	rec.clear()
	h.frame()
	start = rec.find_button("⚔  To Battle!")
	eq(start.opts.get("disabled"), true, "To Battle! disabled")
	U.uninstall()


func test_a_bot_seat_cycles_its_faction_and_skill() -> void:
	before()
	var rec := U.install()
	var h := Harness.new(1920, 1400)
	h.frame()
	var fac = button_at(rec, "Random")  # the seat's faction, not the Random battlefield card
	h.frame(fac.x + fac.w / 2, fac.y + fac.h / 2, true)
	eq(h.s.config.aiFactions[1], C.FACTION_IDS[0], "Random → the first faction")
	rec.clear()
	h.frame()
	var skill = button_at(rec, C.DIFFICULTIES.knight.name, fac.y)
	h.frame(skill.x + skill.w / 2, skill.y + skill.h / 2, true)
	var ids: Array = C.DIFFICULTY_IDS
	eq(h.s.config.aiDifficulties[1], ids[(ids.find("knight") + 1) % ids.size()], "the next personality")
	U.uninstall()


# ---------------------------------------------------- choosing a faction --

## Draw a frame (optionally clicking), returning each faction card's rect.
func cards_frame(h: Harness, rec, click = null) -> Dictionary:
	rec.clear()
	if click != null:
		h.frame(click.x, click.y, true)
	else:
		h.frame()
	var cards := {}
	for b in rec.buttons:
		var tip = b.opts.get("tooltip")
		if b.label == "" and tip is Array and not tip.is_empty():
			for id in C.FACTIONS:
				if C.FACTIONS[id].name == tip[0]:
					cards[id] = b
	return cards


func test_offers_all_six_and_remembers_the_choice() -> void:
	before()
	var rec := U.install()
	var h := Harness.new(1600, 900)
	h.profile.chooseFirstFaction("kingdom")
	h.profile.data.renown = 5000.0
	h.profile.unlockFaction("khanate")
	cards_frame(h, rec)
	# Scroll to the bottom so the faction panel is in view (it clamps).
	h.s.scroll = 1e6
	cards_frame(h, rec)
	var cards := cards_frame(h, rec)
	var keys := cards.keys()
	keys.sort()
	var want: Array = C.FACTIONS.keys()
	want.sort()
	eq(keys, want, "all six cards")
	var k = cards.khanate
	check(k.y + k.h / 2 < 900 - 80, "the faction cards are off screen even scrolled down")
	cards_frame(h, rec, {"x": k.x + k.w / 2, "y": k.y + k.h / 2})
	eq(h.s.config.faction, "khanate")
	eq(h.profile.data.faction, "khanate")
	U.uninstall()


func test_wont_let_you_pick_a_faction_you_havent_unlocked() -> void:
	before()
	var rec := U.install()
	var h := Harness.new(1600, 900)
	h.profile.chooseFirstFaction("legion")
	cards_frame(h, rec)
	h.s.scroll = 1e6
	cards_frame(h, rec)
	var cards := cards_frame(h, rec)
	var k = cards.khanate
	rec.clear()
	var action = h.frame(k.x + k.w / 2, k.y + k.h / 2, true)
	eq(h.s.config.faction, "legion")
	eq(h.s.bookFocus, "khanate", "a locked card opens the Factions book on it")
	# The action bar is drawn after the panels, so the same frame asks for it.
	eq(action, "factions", "the screen asks for the book")
	eq(h.frame(), null, "once")
	U.uninstall()


func test_cycles_the_commander_and_remembers_it_per_faction() -> void:
	before()
	var rec := U.install()
	var h := Harness.new(1600, 1600)
	h.profile.chooseFirstFaction("norse")
	h.profile.data.commanders = ["steward", "marshal"]
	h.profile.selectCommander("steward")
	h.frame()
	eq(h.s.config.commander, "steward")
	var next = rec.find_button("›")
	h.frame(next.x + next.w / 2, next.y + next.h / 2, true)
	eq(h.s.config.commander, "marshal", "cycled")
	eq(h.profile.data.commander, "marshal", "the profile follows")
	eq(h.profile.data.factionCommanders.norse, "marshal", "paired with the faction")
	U.uninstall()


func test_shows_published_maps_and_not_drafts() -> void:
	before()
	var draft := CustomMaps.customFromGenerated("open_plains", 3, 2, "Draft Field")
	CustomMaps.saveCustomMap(draft)
	var pub := CustomMaps.customFromGenerated("highlands", 5, 2, "Published Field")
	check(CustomMaps.publishCustomMap(pub).is_empty(), "published")
	var rec := U.install()
	var h := Harness.new(1600, 900)
	h.frame()
	var names: Array = []
	for b in rec.buttons:
		var tip = b.opts.get("tooltip")
		if tip is Array and not tip.is_empty():
			names.append(tip[0])
	check(names.has("Published Field"), "the published map is a card")
	check(not names.has("Draft Field"), "the draft is not")
	check(rec.find_text("1 draft map in the Map Editor — publish it to play here.") != null, "the draft is mentioned")
	U.uninstall()
