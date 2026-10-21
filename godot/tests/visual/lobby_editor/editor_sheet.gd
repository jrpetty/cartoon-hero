## The map editor, drawn by the port, in the same states as
## game/scripts/godot-visual-editor.mjs draws the TS editor (editor_scenes.json).
##
##   xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
##     --path godot res://tests/visual/lobby_editor/editor_sheet.tscn -- <out dir> [scene ...]
extends Node

const W := 1600
const H := 900

var spec: Dictionary
var scenes: Array = []
var out_dir := "tests/visual/lobby_editor/out"
var _vp: SubViewport
var _canvas: Node2D
var _ctx := Ctx.new()
var _i := 0
var _frames := 0
var _screen: EditorScreen


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	if args.size() > 0:
		out_dir = args[0]
	var only := args.slice(1)
	spec = JSON.parse_string(FileAccess.get_file_as_string("res://tests/visual/lobby_editor/editor_scenes.json"))
	for s in spec.scenes:
		if only.is_empty() or only.has(s.name):
			scenes.append(s)
	_vp = SubViewport.new()
	_vp.size = Vector2i(W, H)
	_vp.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	add_child(_vp)
	_canvas = Node2D.new()
	_vp.add_child(_canvas)
	_canvas.draw.connect(_on_draw)
	_setup(0)


func _setup(i: int) -> void:
	_i = i
	_frames = 0
	if i >= scenes.size():
		get_tree().quit()
		return
	var scene: Dictionary = scenes[i]
	Storage.use_memory_only()
	for m in spec.maps:
		var cm := CustomMaps.customFromGenerated(m.preset, int(m.seed), int(m.players), m.name)
		cm.desc = m.desc
		if m.publish:
			CustomMaps.publishCustomMap(cm, m.author)
		else:
			CustomMaps.saveCustomMap(cm)
	_screen = EditorScreen.new()
	_screen.author = "Aldric"
	if scene.has("open"):
		var o: Dictionary = scene.open
		var m: Dictionary
		if o.has("blank"):
			m = CustomMaps.newCustomMap(o.name, int(o.blank), o.biome)
		else:
			m = CustomMaps.customFromGenerated(o.preset, int(o.seed), int(o.players), o.name)
		if o.get("publish", false):
			CustomMaps.publishCustomMap(m, "Aldric")
		_screen.open(m)
		if scene.has("nomad"):
			m.nomad = scene.nomad
	for k in scene.get("set", {}):
		var v = scene.set[k]
		if k in ["rollSeed", "scriptSeed", "brush", "paint"]:
			v = int(v)
		_screen.set(k, v)
	if scene.has("copy"):
		var c: Array = scene.copy
		_screen.copyRegion(int(c[0]), int(c[1]), int(c[2]), int(c[3]))
	_canvas.queue_redraw()


func _on_draw() -> void:
	var scene: Dictionary = scenes[_i]
	_ctx.begin(_canvas, W, H)
	var t0 := Time.get_ticks_usec()
	for f in int(scene.get("frames", 1)):
		UI.ui.begin(_ctx, {"mx": scene.mouse[0], "my": scene.mouse[1], "clicked": false, "rightClicked": false})
		_screen.draw(W, H, 0.25, false)
		UI.ui.flushTooltip(W, H)
	print("paint %s: %.2f ms" % [scene.name, (Time.get_ticks_usec() - t0) / 1000.0 / int(scene.get("frames", 1))])
	_ctx.end()


func _process(_dt: float) -> void:
	if _i >= scenes.size():
		return
	_frames += 1
	if _frames == 4:
		var out := out_dir
		if not out.begins_with("/"):
			out = ProjectSettings.globalize_path("res://").path_join(out)
		var path := out.path_join("godot_editor_%s.png" % scenes[_i].name)
		_vp.get_texture().get_image().save_png(path)
		print("wrote ", path)
		_setup(_i + 1)
