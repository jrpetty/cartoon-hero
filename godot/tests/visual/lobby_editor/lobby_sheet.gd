## The online lobby, drawn by the port, in the same known states as
## game/scripts/godot-visual-lobby.mjs draws the TS lobby in Chromium: each
## scene in lobby_scenes.json opens a NetLobby, gives it a fake socket, feeds
## it server messages through its own onMessage, and is captured.
##
##   xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
##     --path godot res://tests/visual/lobby_editor/lobby_sheet.tscn -- <out dir> [scene ...]
extends Node

const W := 1100
const H := 900

## Stands in for the WSLink: records what the lobby sends.
class FakeLink:
	var sent: Array = []
	var connected := true
	var onData: Callable
	var onOpen: Callable
	var onClose: Callable
	func send(m) -> void:
		sent.append(m)
	func close() -> void:
		connected = false
	func poll() -> void:
		pass


var spec: Dictionary
var scenes: Array = []
var out_dir := "tests/visual/lobby_editor/out"
var _vp: SubViewport
var _canvas: Node2D
var _ctx := Ctx.new()
var _i := 0
var _frames := 0
var _lobby: NetLobby


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	if args.size() > 0:
		out_dir = args[0]
	var only := args.slice(1)
	spec = JSON.parse_string(FileAccess.get_file_as_string("res://tests/visual/lobby_editor/lobby_scenes.json"))
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
	for k in spec.storage:
		Storage.setItem(k, spec.storage[k])
	for m in spec.maps:
		var cm := CustomMaps.customFromGenerated(m.preset, int(m.seed), int(m.players), m.name)
		cm.desc = m.desc
		CustomMaps.publishCustomMap(cm, m.author)
	_lobby = NetLobby.new()
	_lobby.open(func(_s): pass, spec.opts)
	var fake := FakeLink.new()
	_lobby.ws = fake
	if scene.get("fromHub", false):
		_lobby.fromHub = true
		_lobby.serverUrl = scene.serverUrl
		_lobby.view = "hub"
	for m in scene.messages:
		_lobby.onMessage(fake, JSON.parse_string(JSON.stringify(m)), func(_s): pass)
	if scene.has("set"):
		for k in scene.set:
			_lobby.set(k, scene.set[k])
		if _lobby.view == "hub":
			_lobby.renderHub()
		elif _lobby.view == "room":
			_lobby.render()
	if scene.has("call"):
		_lobby.call(scene.call)
	_canvas.queue_redraw()


func _on_draw() -> void:
	_ctx.begin(_canvas, W, H)
	_ctx.fillStyle = "#22301c"
	_ctx.fillRect(0, 0, W, H)
	UI.ui.begin(_ctx, {"mx": -100, "my": -100, "clicked": false, "rightClicked": false})
	var t0 := Time.get_ticks_usec()
	_lobby.draw(W, H, 0.25)
	print("paint %s: %.2f ms" % [scenes[_i].name, (Time.get_ticks_usec() - t0) / 1000.0])
	_ctx.end()


func _process(_dt: float) -> void:
	if _i >= scenes.size():
		return
	_frames += 1
	if _frames == 4:
		var out := out_dir
		if not out.begins_with("/"):
			out = ProjectSettings.globalize_path("res://").path_join(out)
		var path := out.path_join("godot_lobby_%s.png" % scenes[_i].name)
		_vp.get_texture().get_image().save_png(path)
		print("wrote ", path)
		_setup(_i + 1)
