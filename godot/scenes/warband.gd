## Warband Tactics on its own, until the main loop (main.ts) is ported: the
## same per-frame wiring main.ts does for the "warband" state — GameInput's
## clicks and drag-releases become the frame's click, keys go to the screen's
## handleKey first, `UI.ui.begin` then `WarbandScreen.draw(W, H, time, run)` —
## and "exit" starts a fresh run (there is no menu to go back to yet).
##
##   godot --path godot res://scenes/warband.tscn
extends Node2D

var game_input := GameInput.new()
var ctx := Ctx.new()
var screen := WarbandScreen.new()
var run := Warband.WarbandRun.new()
var time := 0.0
var frameClick = null ## {x, y} or null
var frameRight = null


func _ready() -> void:
	game_input.onLeftClick = func(x, y):
		audio.resume()
		frameClick = {"x": x, "y": y}
	game_input.onLeftDouble = func(x, y):
		frameClick = {"x": x, "y": y}
	game_input.onRightClick = func(x, y):
		frameRight = {"x": x, "y": y}
	# Outside a match, screens are tap/drag UIs (no box-select), so a drag's
	# release also counts as a click at the end point — that's what makes
	# drag-and-drop in Warband Tactics resolve on release.
	game_input.onDragEnd = func(box):
		frameClick = {"x": box.x1, "y": box.y1}
	game_input.onKeyDown = func(key):
		screen.handleKey(key)


func _input(event: InputEvent) -> void:
	game_input.handleEvent(event)


func _process(dt: float) -> void:
	game_input.update()
	time += minf(dt, 0.1)
	queue_redraw()


func _draw() -> void:
	var size := get_viewport_rect().size
	ctx.begin(self)
	UI.ui.begin(ctx, {
		"mx": game_input.mx, "my": game_input.my,
		"clicked": frameClick != null, "rightClicked": frameRight != null,
		"alt": game_input.alt, "leftHeld": game_input.leftDown, "rightHeld": game_input.rightDown,
		"ctrlHeld": game_input.ctrl, "shiftHeld": game_input.shift,
	})
	if screen.draw(size.x, size.y, time, run) == "exit":
		run = Warband.WarbandRun.new()
		screen = WarbandScreen.new()
		audio.play("ui")
	UI.ui.flushTooltip(size.x, size.y)
	ctx.end()
	frameClick = null
	frameRight = null
