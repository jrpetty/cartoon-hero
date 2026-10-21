## ui/settings_screen.ts — consolidated settings, in two tabs: Game (audio,
## accessibility, performance) and Controls (every hotkey, rebindable). Edits
## the Settings object (a Dictionary, meta/settings.gd) in place; the App
## applies it live each frame and persists on exit. Reachable from the main
## menu and the pause menu.
class_name SettingsScreen
extends RefCounted

const SCROLL_MIN := 240.0
const SCROLL_MAX := 960.0
const UI_MIN := 0.8
const UI_MAX := 1.5

# Row pitches. Panel heights are derived from these rather than hard-coded, so
# adding a setting or a control line can't silently overflow its frame.
const ROW := 40       # toggle row
const SLIDER := 30    # slider row
const HEAD := 30      # section heading
const BIND_ROW := 30  # one rebindable action
const PAD := 16       # breathing room at the panel's bottom edge

## Things the keyboard can do that aren't rebindable actions.
const FIXED_CONTROLS := [
	["Left-click / drag", "Select"],
	["Right-click", "Move · attack · gather · build"],
	["Shift", "Queue order · add to selection"],
	["1 – 9", "Select control group (twice: go there)"],
	["Ctrl + 1 – 9", "Assign control group"],
	["Shift + 1 – 9", "Add selection to group"],
	["Alt-click minimap", "Ping"],
	["Mouse wheel", "Zoom"],
	["Screen edge", "Pan camera"],
]

var tab := "game"  ## "game" | "controls"
## The action currently listening for a new chord, if any (an ActionId or null).
var listening = null
## Set by captureKey, consumed on the next draw (which owns the Settings).
var pending = null


## Called by the app before its own hotkey handling. While an action is
## listening, every key belongs to the rebind — otherwise binding Escape or
## Tab would close the screen instead of binding them. Returns true when the
## key was swallowed.
## True while an action is waiting for its new chord.
func isListening() -> bool:
	return listening != null


## `mods`: { ctrl, shift, alt }.
func captureKey(key: String, mods: Dictionary) -> bool:
	if listening == null:
		return false
	if key == "Escape":
		listening = null
		return true # cancel
	var chord := Keybinds.chordOf(key, mods)
	if chord == "":
		return true # a bare modifier: keep listening
	if Keybinds.isReserved(chord):
		return true # the browser needs this one
	pending = chord
	return true


func draw(W: float, H: float, time: float, s: Dictionary, mouseDown: bool) -> Variant:
	var ui := UI.ui
	var ctx := ui.ctx
	var PAL: Dictionary = C.PAL
	ctx.fillStyle = "#171208"
	ctx.fillRect(0, 0, W, H)
	ui.text("Settings", W / 2, 56, {"align": "center", "size": 34, "bold": true, "color": PAL.uiAccent, "font": "Georgia, serif"})

	# Apply a rebind captured since the last frame.
	if pending and listening:
		if pending == "Backspace":
			s.keybinds[listening] = "" # unbind
		else:
			s.keybinds[listening] = pending
		listening = null
		pending = null

	var colW := 400.0
	var gap := 40.0
	var totalW := colW * 2 + gap
	var x0 := JS.round(W / 2 - totalW / 2)
	var top := 132.0
	var drag := ui.clicked or mouseDown

	# ----- tabs -----
	var tabW := 150.0
	var tx := W / 2 - tabW - 4
	if ui.button("Game", tx, 84, tabW, 32, {"accent": tab == "game", "size": 14}):
		tab = "game"
		listening = null
	if ui.button("Controls", tx + tabW + 8, 84, tabW, 32, {"accent": tab == "controls", "size": 14}):
		tab = "controls"

	var panelH := drawGameTab(x0, colW, gap, top, s, drag) if tab == "game" else drawControlsTab(x0, colW, gap, top, s, H)

	# ----- back -----
	var action = null
	var footY := top + panelH + 20
	if ui.button("‹  Back", x0, footY, 140, 44, {"size": 15}):
		listening = null
		action = "back"
	ui.text("Settings save automatically.", x0 + 160, footY + 22, {"size": 12, "color": "#9a917b"})
	return action


# ------------------------------------------------------------- game tab --

func drawGameTab(x0: float, colW: float, gap: float, top: float, s: Dictionary, drag: bool) -> float:
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	var xR := x0 + colW + gap
	var leftH := 24 + HEAD + 3 * SLIDER + ROW + 10 + HEAD + 2 * ROW + 34 + 3 * ROW + PAD
	var rightH := 24 + HEAD + 34 + 4 * ROW + 10 + HEAD + 2 * ROW + 34 + PAD
	var panelH := float(maxi(leftH, rightH))

	# ----- left: audio + gameplay -----
	ui.panel(x0, top, colW, panelH, {"light": true})
	var y := [top + 24]  # boxed: the `vol` closure moves it
	var lx := x0 + 22
	var sx := x0 + 150
	var sw := colW - 172

	ui.text("Audio", lx, y[0], {"size": 15, "bold": true, "color": PAL.uiAccent})
	y[0] += HEAD
	var vol := func(label: String, val: float) -> float:
		ui.text(label, lx, y[0] + 4, {"size": 13})
		ui.text("%s%%" % JS.num(JS.round(val * 100)), x0 + colW - 22, y[0] + 4, {"size": 12, "align": "right", "color": "#bdb49a"})
		var nv := ui.slider(sx, y[0], sw - 36, val, drag)
		y[0] += SLIDER
		return nv
	s.masterVol = vol.call("Master", float(s.masterVol))
	s.sfxVol = vol.call("Effects", float(s.sfxVol))
	s.musicVol = vol.call("Music", float(s.musicVol))
	s.muted = toggle(lx, y[0], colW - 44, "Mute all sound", s.muted); y[0] += ROW

	y[0] += 10
	ui.text("Gameplay", lx, y[0], {"size": 15, "bold": true, "color": PAL.uiAccent})
	y[0] += HEAD
	s.damageNumbers = toggle(lx, y[0], colW - 44, "Floating damage numbers", s.damageNumbers); y[0] += ROW
	s.edgeScroll = toggle(lx, y[0], colW - 44, "Edge-scroll camera", s.edgeScroll); y[0] += ROW
	if true:
		var norm := (float(s.scrollSpeed) - SCROLL_MIN) / (SCROLL_MAX - SCROLL_MIN)
		ui.text("Scroll speed", lx, y[0] + 4, {"size": 13})
		ui.text(JS.num(JS.round(s.scrollSpeed)), x0 + colW - 22, y[0] + 4, {"size": 12, "align": "right", "color": "#bdb49a"})
		var nv := ui.slider(sx, y[0], sw - 36, maxf(0, minf(1, norm)), drag)
		s.scrollSpeed = JS.round(SCROLL_MIN + nv * (SCROLL_MAX - SCROLL_MIN))
		y[0] += 34
	s.weather = toggle(lx, y[0], colW - 44, "Weather effects", s.weather); y[0] += ROW
	s.colorblind = toggle(lx, y[0], colW - 44, "Colorblind team colours", s.colorblind); y[0] += ROW

	# ----- right: interface + performance -----
	ui.panel(xR, top, colW, panelH, {"light": true})
	var ry := top + 24
	var rx := xR + 22
	var rsx := xR + 150

	ui.text("Interface", rx, ry, {"size": 15, "bold": true, "color": PAL.uiAccent})
	ry += HEAD
	if true:
		# Scales the HUD and menus only — the world keeps its apparent size, so
		# turning this up doesn't cost you any view of the battlefield.
		var norm := (float(s.uiScale) - UI_MIN) / (UI_MAX - UI_MIN)
		ui.text("Interface scale", rx, ry + 4, {"size": 13})
		ui.text("%s%%" % JS.num(JS.round(s.uiScale * 100)), xR + colW - 22, ry + 4, {"size": 12, "align": "right", "color": "#bdb49a"})
		var nv := ui.slider(rsx, ry, colW - 208, maxf(0, minf(1, norm)), drag)
		# Quarter steps: a slider that lands on 103% is a slider nobody trusts.
		s.uiScale = JS.round((UI_MIN + nv * (UI_MAX - UI_MIN)) * 20) / 20
		ry += 34
	ui.text("HUD and menus only — the map keeps its size.", rx, ry - 6, {"size": 11, "color": "#8a8278"})
	ry += 14

	y[0] = ry # keep the toggle helper's local naming consistent
	s.showFps = toggle(rx, ry, colW - 44, "FPS counter", s.showFps); ry += ROW
	s.perfOverlay = toggle(rx, ry, colW - 44, "Performance overlay", s.perfOverlay); ry += ROW
	ui.text("Frame time, sim tick, entity count.", rx, ry - 12, {"size": 11, "color": "#8a8278"})
	ry += 4

	ui.text("Performance", rx, ry + 8, {"size": 15, "bold": true, "color": PAL.uiAccent})
	ry += HEAD + 8
	s.reduceEffects = toggle(rx, ry, colW - 44, "Reduce effects", s.reduceEffects); ry += ROW
	s.autoLod = toggle(rx, ry, colW - 44, "Adaptive detail", s.autoLod); ry += ROW
	ui.text("Simplifies units on its own when frames slip,", rx, ry - 12, {"size": 11, "color": "#8a8278"})
	ui.text("and restores them when they recover.", rx, ry + 1, {"size": 11, "color": "#8a8278"})

	return panelH


# --------------------------------------------------------- controls tab --

func drawControlsTab(x0: float, colW: float, gap: float, top: float, s: Dictionary, H: float) -> float:
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	var xR := x0 + colW + gap
	var conflicts := Keybinds.conflictsOf(s.keybinds)
	var rowsOf := func(g: String) -> int: return C.ACTIONS.filter(func(a): return a.group == g).size()
	var groupH := func(g: String) -> float: return HEAD + rowsOf.call(g) * BIND_ROW + 8
	var EXTRAS_H := HEAD + FIXED_CONTROLS.size() * 22 + 8
	# Balance the two columns by drawn height, counting the mouse reference as
	# a block of its own so it can't push a column off the bottom of its panel.
	var left: Array = []
	var right: Array = []
	var lH := 0.0
	var rH := float(EXTRAS_H) # the extras always live in the right column
	for g in C.KEYBIND_GROUPS:
		if lH <= rH:
			left.append(g)
			lH += groupH.call(g)
		else:
			right.append(g)
			rH += groupH.call(g)
	var panelH := maxf(lH, rH) + 24 + PAD

	drawGroups(x0, left, top, colW, panelH, s, conflicts)
	var ry := drawGroups(xR, right, top, colW, panelH, s, conflicts)

	# Fixed controls live under the shorter column.
	ui.text("Mouse & groups", xR + 22, ry, {"size": 15, "bold": true, "color": PAL.uiAccent})
	ry += HEAD - 8
	for fc in FIXED_CONTROLS:
		ui.text(fc[0], xR + 22, ry, {"size": 11.5, "color": "#ffe9b0"})
		ui.text(fc[1], xR + 172, ry, {"size": 11.5, "color": "#cabfa4"})
		ry += 22

	# Conflicts are legal but almost never intended, so say so plainly.
	if conflicts.size():
		var names := ", ".join(PackedStringArray(conflicts.keys().map(func(c): return Keybinds.chordLabel(c))))
		ui.text("Bound twice: %s — the first action listed wins." % names, x0 + 160, top + panelH + 46, {"size": 12, "bold": true, "color": "#e0a878"})
	if ui.button("Reset to defaults", xR + colW - 180, top + panelH + 20, 180, 44, {"size": 14}):
		s.keybinds.clear()
		listening = null
	ui.text("Click a key to rebind · Esc cancels · Backspace clears",
		xR + colW - 196, top + panelH + 42, {"align": "right", "size": 11.5, "color": "#8a8278"})
	return panelH


## The TS `drawGroups` closure: one column of rebindable actions.
func drawGroups(px: float, groups: Array, top: float, colW: float, panelH: float, s: Dictionary, conflicts: Dictionary) -> float:
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	ui.panel(px, top, colW, panelH, {"light": true})
	var y := top + 24
	for g in groups:
		ui.text(g, px + 22, y, {"size": 15, "bold": true, "color": PAL.uiAccent})
		y += HEAD
		for a in C.ACTIONS.filter(func(x): return x.group == g):
			var chord := Keybinds.chordFor(s.keybinds, a.id)
			var clash := chord != "" and conflicts.has(chord)
			var isListening: bool = listening == a.id
			ui.text(a.label, px + 22, y + 13, {"size": 12.5, "color": "#e7ddc4" if a.get("hint") else "#e7ddc4"})
			var bw := 116.0
			var bx := px + colW - bw - 22
			var label := "press a key…" if isListening else Keybinds.chordLabel(chord)
			var tip = null
			if isListening:
				tip = ["Listening", "Esc cancels · Backspace clears the binding"]
			elif clash:
				tip = ["Conflict", "%s is bound to more than one action" % Keybinds.chordLabel(chord)]
				if a.get("hint"):
					tip.append(a.hint)
			elif a.get("hint"):
				tip = [a.label, a.hint]
			var opts := {"accent": isListening, "danger": clash and not isListening, "size": 12}
			if tip != null:
				opts["tooltip"] = tip
			if ui.button(label, bx, y, bw, 24, opts):
				listening = null if isListening else a.id
			y += BIND_ROW
		y += 8
	return y


## A labelled On/Off switch. Returns the (possibly toggled) value.
func toggle(x: float, y: float, w: float, label: String, value) -> bool:
	var ui := UI.ui
	var on := bool(value)
	ui.text(label, x, y + 14, {"size": 13})
	var clicked := ui.button("On" if on else "Off", x + w - 72, y, 72, 28, {"accent": on, "size": 13})
	return not on if clicked else on
