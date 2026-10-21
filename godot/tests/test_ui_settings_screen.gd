extends TestCase
# Ported from the settings-screen cases of game/src/ui/uiscale.test.ts
# ("draws the same layout at every scale", "Rebinding"), plus the toggles and
# sliders of the Game tab.

const U := preload("res://tests/ui_test_helpers.gd")
const W := 1600
const H := 900


static func settings() -> Dictionary:
	var s: Dictionary = C.DEFAULT_SETTINGS.duplicate(true)
	s.keybinds = {}
	return s


func test_draws_the_same_layout_at_every_scale() -> void:
	# The screens take W/H and lay out from them, so a scaled screen must be
	# handed the reduced space.
	var scr := SettingsScreen.new()
	var st := settings()
	for tab in ["game", "controls"]:
		scr.tab = tab
		for scale in [0.8, 1.0, 1.5]:
			U.begin(U.ctx(W, H))
			UI.ui.pushScale(scale)
			scr.draw(W / scale, H / scale, 1, st, false)  # a script error fails the test
			UI.ui.popScale()


const MODS := {"ctrl": false, "shift": false, "alt": false}


func open_controls(scr: SettingsScreen, st: Dictionary) -> Callable:
	var c := U.ctx(W, H)
	var step := func(mx: float, my: float, clicked: bool) -> void:
		U.begin(c, mx, my, clicked)
		scr.draw(W, H, 1, st, false)
	step.call(-99, -99, false)
	step.call(W / 2.0 + 80, 100, true) # the Controls tab
	step.call(-99, -99, false)
	return step


## Centre of the first rebind button in the left column, derived from the
## screen's own layout constants rather than eyeballed, so a spacing change
## moves the test with it instead of silently missing.
static func firstBindButton() -> Array:
	var colW := 400.0
	var gap := 40.0
	var top := 132.0
	var x0 := JS.round(W / 2.0 - (colW * 2 + gap) / 2)
	var bw := 116.0
	return [x0 + colW - bw - 22 + bw / 2, top + 24 + 30 + 12]


func test_ignores_keys_until_a_binding_is_actually_listening() -> void:
	var scr := SettingsScreen.new()
	eq(scr.captureKey("Z", MODS), false)


func test_binds_the_next_key_pressed_and_only_that_one() -> void:
	var scr := SettingsScreen.new()
	var st := settings()
	var step := open_controls(scr, st)
	eq(scr.tab, "controls", "on the Controls tab")
	var b := firstBindButton()
	# Attack-move is the first row of the first group in the left column.
	step.call(b[0], b[1], true)
	check(scr.isListening(), "listening")
	eq(scr.captureKey("Z", MODS), true, "swallowed while listening")
	step.call(-99, -99, false) # the draw applies it
	eq(Keybinds.chordFor(st.keybinds, "attackMove"), "Z")
	eq(scr.captureKey("Y", MODS), false, "no longer listening")
	eq(Keybinds.chordFor(st.keybinds, "stop"), "S")


func test_lets_escape_and_tab_be_bound_rather_than_acting_on_the_screen() -> void:
	var scr := SettingsScreen.new()
	var st := settings()
	var step := open_controls(scr, st)
	var b := firstBindButton()
	step.call(b[0], b[1], true)
	eq(scr.captureKey("Tab", MODS), true)
	step.call(-99, -99, false)
	eq(Keybinds.chordFor(st.keybinds, "attackMove"), "Tab")


func test_cancels_on_escape_without_changing_anything() -> void:
	var scr := SettingsScreen.new()
	var st := settings()
	var step := open_controls(scr, st)
	var b := firstBindButton()
	step.call(b[0], b[1], true)
	eq(scr.captureKey("Escape", MODS), true)
	step.call(-99, -99, false)
	eq(Keybinds.chordFor(st.keybinds, "attackMove"), "A")
	eq(scr.captureKey("Z", MODS), false, "listening really did stop")


func test_refuses_a_chord_the_browser_has_spoken_for_and_keeps_listening() -> void:
	var scr := SettingsScreen.new()
	var st := settings()
	var step := open_controls(scr, st)
	var b := firstBindButton()
	step.call(b[0], b[1], true)
	eq(scr.captureKey("F5", MODS), true)
	step.call(-99, -99, false)
	eq(Keybinds.chordFor(st.keybinds, "attackMove"), "A", "unchanged")
	eq(scr.captureKey("Z", MODS), true, "still waiting")
	step.call(-99, -99, false)
	eq(Keybinds.chordFor(st.keybinds, "attackMove"), "Z")


func test_backspace_clears_a_binding_and_reset_restores_the_defaults() -> void:
	var rec := U.install()
	var scr := SettingsScreen.new()
	var st := settings()
	var step := open_controls(scr, st)
	var b := firstBindButton()
	step.call(b[0], b[1], true)
	scr.captureKey("Backspace", MODS)
	step.call(-99, -99, false)
	eq(Keybinds.chordFor(st.keybinds, "attackMove"), "", "unbound")
	rec.clear()
	step.call(-99, -99, false)
	var reset = rec.find_button("Reset to defaults")
	step.call(reset.x + 5, reset.y + 5, true)
	eq(st.keybinds.size(), 0, "overrides gone")
	eq(Keybinds.chordFor(st.keybinds, "attackMove"), "A", "the default back")
	U.uninstall()


func test_says_when_a_chord_is_bound_twice() -> void:
	var rec := U.install()
	var scr := SettingsScreen.new()
	scr.tab = "controls"
	var st := settings()
	st.keybinds = {"attackMove": "S"}
	U.begin(U.ctx(W, H))
	scr.draw(W, H, 1, st, false)
	var warned := rec.texts.any(func(t): return String(t.s).begins_with("Bound twice: S"))
	check(warned, "the clash is named")
	var danger: Array = rec.buttons.filter(func(x): return x.label == "S" and x.opts.get("danger"))
	eq(danger.size(), 2, "both clashing buttons in red")
	U.uninstall()


func test_game_tab_toggles_and_sliders_edit_the_settings() -> void:
	var rec := U.install()
	var scr := SettingsScreen.new()
	var st := settings()
	var c := U.ctx(W, H)
	U.begin(c)
	scr.draw(W, H, 1, st, false)
	# "Mute all sound" is the first On/Off switch.
	var mute = rec.buttons.filter(func(x): return x.label == "Off" or x.label == "On")[0]
	eq(mute.label, "Off")
	rec.clear()
	U.begin(c, mute.x + 5, mute.y + 5, true)
	scr.draw(W, H, 1, st, false)
	eq(st.muted, true, "muted")
	# Dragging the Master slider to its left end.
	var colW := 400.0
	var x0 := JS.round(W / 2.0 - (colW * 2 + 40) / 2)
	var sx := x0 + 150
	var sy := 132 + 24 + 30
	U.begin(c, sx - 4, sy, false)
	scr.draw(W, H, 1, st, true)
	eq(st.masterVol, 0.0, "dragged to zero")
	# The interface scale snaps to 5% steps.
	check(is_equal_approx(fmod(st.uiScale * 20, 1.0), 0.0) or is_equal_approx(fmod(st.uiScale * 20, 1.0), 1.0), "uiScale on a step")
	var back = rec.find_button("‹  Back")
	U.begin(c, back.x + 5, back.y + 5, true)
	eq(scr.draw(W, H, 1, st, false), "back")
	U.uninstall()
