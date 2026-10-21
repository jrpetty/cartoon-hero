## UI widgets drawn by the port, call for call the same as
## game/scripts/godot-visual-ui.mjs (panels, text, every button state, bars,
## slider, scrollbar, a scrolled clip, a tooltip, the menu icons).
extends "res://tests/visual/sheet_base.gd"


func setup(_args: PackedStringArray) -> void:
	sheet_size = Vector2i(760, 540)


static func input(mx: float, my: float, extra: Dictionary = {}) -> Dictionary:
	var d := {"mx": mx, "my": my, "clicked": false, "rightClicked": false}
	d.merge(extra, true)
	return d


func paint(ctx: Ctx) -> void:
	var W := 760
	var H := 540
	var PAL: Dictionary = C.PAL
	var ui := UI.ui
	var bg := ctx.createLinearGradient(0, 0, 0, H)
	bg.addColorStop(0, "#3b5a2c"); bg.addColorStop(1, "#22331a")
	ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H)

	ui.begin(ctx, input(-100, -100))

	# Panels with text.
	ui.panel(16, 16, 350, 210)
	ui.panel(382, 16, 362, 210, {"light": true})
	ui.text("Banner & Blade", 32, 40, {"size": 20, "bold": true, "color": PAL.uiAccent})
	ui.text("Parchment body text, 14px", 32, 70)
	ui.text("Small grey note at 12px", 32, 92, {"size": 12, "color": "#b8a888"})
	ui.text("Centred in the panel", 191, 118, {"align": "center", "size": 13})
	ui.text("right-aligned", 350, 140, {"align": "right", "size": 13, "color": PAL.uiGood})
	ui.text("Georgia, bold 17px", 32, 168, {"size": 17, "bold": true, "font": "Georgia, serif"})
	ui.text("12.5px fractional size", 32, 196, {"size": 12.5, "color": PAL.uiBad})

	# Every button state.
	var bw := 160
	var bh := 34
	ui.begin(ctx, input(-100, -100))
	ui.button("Normal", 398, 32, bw, bh)
	ui.begin(ctx, input(398 + 80, 90))
	ui.button("Hover", 398, 74, bw, bh)
	ui.begin(ctx, input(398 + 80, 132, {"leftHeld": true}))
	ui.button("Pressed", 398, 116, bw, bh)
	ui.begin(ctx, input(-100, -100))
	ui.button("Accent", 570, 32, bw, bh, {"accent": true})
	ui.button("Danger", 570, 74, bw, bh, {"danger": true})
	ui.button("Disabled", 570, 116, bw, bh, {"disabled": true})
	ui.button("Queue", 398, 166, bw, bh, {"badge": "3", "size": 13})
	ui.begin(ctx, input(570 + 80, 182))
	ui.button("Danger hover", 570, 166, bw, bh, {"danger": true, "size": 12.5})

	# Bars, slider, scrollbar.
	ui.begin(ctx, input(-100, -100))
	ui.panel(16, 242, 350, 150)
	ui.bar(32, 262, 300, 10, 0.2, PAL.uiBad)
	ui.bar(32, 282, 300, 10, 0.62, "#e8c33a")
	ui.bar(32, 302, 300, 10, 1.0, PAL.uiGood)
	ui.bar(32, 322, 300, 6, 0.005, PAL.uiAccent)
	ui.slider(40, 352, 280, 0.35, false)
	ui.scrollbar(346, 258, 120, 60, 400)

	# A scrolled, clipped block of text.
	ui.panel(382, 242, 362, 150, {"light": true})
	ui.pushScroll(30, {"x": 398, "y": 256, "w": 330, "h": 120})
	for i in 8:
		ui.text("Scrolled line %d — clipped to the box" % (i + 1), 410, 262 + i * 20, {"size": 13})
	ui.popScroll()

	# A hovered button with a tooltip, drawn last.
	ui.begin(ctx, input(120, 430))
	ui.button("Train Knight", 30, 412, 170, 36, {"accent": true, "tooltip": ["Knight", "Cost: 60 food, 75 gold", "Strong against archers"]})
	ui.flushTooltip(W, H)

	# The menu icons.
	ui.begin(ctx, input(-100, -100))
	var row: Array = [
		func(x, y): MenuIcons.iconSkirmish(ctx, x, y, 56, "#b8483e"),
		func(x, y): MenuIcons.iconMultiplayer(ctx, x, y, 56, 0.7),
		func(x, y): MenuIcons.iconWarband(ctx, x, y, 56),
		func(x, y): MenuIcons.iconArmory(ctx, x, y, 56),
		func(x, y): MenuIcons.iconMap(ctx, x, y, 56),
		func(x, y): MenuIcons.iconCodex(ctx, x, y, 56),
		func(x, y): MenuIcons.iconSettings(ctx, x, y, 56),
		func(x, y): MenuIcons.iconResume(ctx, x, y, 56),
		func(x, y): MenuIcons.iconFactions(ctx, x, y, 56),
		func(x, y): MenuIcons.iconCareer(ctx, x, y, 56),
		func(x, y): MenuIcons.iconNemesis(ctx, x, y, 56),
	]
	for i in row.size():
		(row[i] as Callable).call(270 + (i % 6) * 80, 432 + floori(i / 6.0) * 66)
