## Screenshot / scenario harness: runs the real game (scenes/main.tscn) and
## drives it through a named scenario, saving PNGs into tests/shots/.
##
##   xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
##     --path godot res://tests/shots/harness.tscn -- <scenario> [--sync]
##
## Scenarios: smoke, shots (the comparison set), perf (busy 8-player match,
## frame/sim timings), input (an input-driven play test).
extends Node

var app
var scenario := "smoke"
var outDir := ""


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	if args.size() > 0:
		scenario = args[0]
	outDir = ProjectSettings.globalize_path("res://").path_join("tests/shots")
	get_window().size = Vector2i(1920, 1080)
	DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_DISABLED if args.has("--novsync") else DisplayServer.VSYNC_ENABLED)
	Storage.use_memory_only()
	var scene: PackedScene = load("res://scenes/main.tscn")
	app = scene.instantiate()
	add_child(app)
	if args.has("--sync"):
		app.threadSim = false
	# Software rendering is slow enough to trip the adaptive detail; keep it at
	# full for comparisons (the perf scenario turns it back on).
	app.settings.autoLod = false
	await frames(3)
	# No real mouse under Xvfb: put the pointer mid-screen (it starts at 0,0,
	# which edge-scrolls the camera into the corner).
	mouse(960, 540)
	await frames(1)
	match scenario:
		"smoke": await smoke()
		_:
			var sc: GDScript = load("res://tests/shots/scenarios.gd")
			if sc != null and sc.can_instantiate():
				await sc.new(self).run(scenario, args)
			else:
				push_error("scenarios.gd failed to load")
	get_tree().quit()


func frames(n: int) -> void:
	for i in n:
		await get_tree().process_frame


## Simulated input, through Godot's own event pipeline (Input.parse_input_event
## → the game's _input → GameInput), exactly as real devices arrive.
func mouse(x: float, y: float) -> void:
	var ev := InputEventMouseMotion.new()
	ev.position = Vector2(x, y)
	ev.global_position = ev.position
	Input.parse_input_event(ev)


func button(x: float, y: float, idx: int, pressed: bool, double: bool = false) -> void:
	var ev := InputEventMouseButton.new()
	ev.position = Vector2(x, y)
	ev.global_position = ev.position
	ev.button_index = idx
	ev.pressed = pressed
	ev.double_click = double
	Input.parse_input_event(ev)


func click(x: float, y: float, idx: int = MOUSE_BUTTON_LEFT) -> void:
	mouse(x, y)
	await frames(1)
	button(x, y, idx, true)
	await frames(1)
	button(x, y, idx, false)
	await frames(2)


func drag(x0: float, y0: float, x1: float, y1: float) -> void:
	mouse(x0, y0)
	await frames(1)
	button(x0, y0, MOUSE_BUTTON_LEFT, true)
	await frames(1)
	for i in range(1, 6):
		mouse(lerpf(x0, x1, i / 5.0), lerpf(y0, y1, i / 5.0))
		await frames(1)
	button(x1, y1, MOUSE_BUTTON_LEFT, false)
	await frames(2)


func key(keycode: Key, pressed: bool = true, shift: bool = false, ctrl: bool = false) -> void:
	var ev := InputEventKey.new()
	ev.keycode = keycode
	ev.physical_keycode = keycode
	ev.pressed = pressed
	ev.shift_pressed = shift
	ev.ctrl_pressed = ctrl
	if keycode >= KEY_A and keycode <= KEY_Z:
		ev.unicode = (keycode - KEY_A) + (65 if shift else 97)
	elif keycode >= KEY_0 and keycode <= KEY_9:
		ev.unicode = keycode
	Input.parse_input_event(ev)


func tap(keycode: Key, shift: bool = false, ctrl: bool = false) -> void:
	key(keycode, true, shift, ctrl)
	await frames(1)
	key(keycode, false, shift, ctrl)
	await frames(1)


func shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	var img := get_viewport().get_texture().get_image()
	var path := outDir.path_join(name + ".png")
	img.save_png(path)
	print("shot ", path)


func smoke() -> void:
	await frames(5)
	await shot("smoke_menu")
	var cfg: Dictionary = app.defaultConfig()
	cfg.seed = 12345
	app.startMatch(cfg)
	await frames(60)
	print("canvas ", app.canvasW, "x", app.canvasH, " cam ", app.camera.x, ",", app.camera.y, " z ", app.camera.zoom, " world ", app.world.worldW, "x", app.world.worldH)
	print("snap ents ", app.renderer.snap.entities.size(), " valid ", app.renderer.snap.valid, " terrain ", app.renderer.terrainCache.width, "x", app.renderer.terrainCache.height)
	print("start ", app.world.map.starts[0], " tick ", app.world.tickCount)
	await shot("smoke_match")
