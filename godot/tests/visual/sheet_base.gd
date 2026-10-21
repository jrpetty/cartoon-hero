## Base for the visual sheets: draw once through Ctx into an offscreen
## viewport the size of the sheet, wait for it to render, save it as a PNG,
## and quit. Needs a real renderer (not --headless), e.g.
##
##   xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
##     --path godot res://tests/visual/ctx_shapes.tscn -- tests/visual/out/godot_ctx_shapes.png
##
## Paths after `--` are relative to the godot/ folder.
extends Node

var ctx := Ctx.new()
var sheet_size := Vector2i(640, 480)
var out_path := "tests/visual/out/sheet.png"
var _vp: SubViewport
var _canvas: Node2D
var _frames := 0
var _painted := false


## Override to set `sheet_size` / `out_path` from the command line before
## the viewport is made.
func setup(_args: PackedStringArray) -> void:
	pass


## Override: draw the sheet.
func paint(_c: Ctx) -> void:
	pass


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	if args.size() > 0:
		out_path = args[args.size() - 1]
	setup(args)
	_vp = SubViewport.new()
	_vp.size = sheet_size
	_vp.transparent_bg = false
	_vp.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	add_child(_vp)
	_canvas = Node2D.new()
	_vp.add_child(_canvas)
	_canvas.draw.connect(_on_draw)
	_canvas.queue_redraw()


func _on_draw() -> void:
	if _painted:
		return
	_painted = true
	ctx.begin(_canvas, sheet_size.x, sheet_size.y)
	var t0 := Time.get_ticks_usec()
	paint(ctx)
	print("paint: %.2f ms" % ((Time.get_ticks_usec() - t0) / 1000.0))
	ctx.end()


func _process(_dt: float) -> void:
	_frames += 1
	if _frames == 5:
		var out := out_path
		if not out.begins_with("/"):
			out = ProjectSettings.globalize_path("res://").path_join(out)
		var img := _vp.get_texture().get_image()
		img.save_png(out)
		print("wrote ", out)
		get_tree().quit()
