## Base for the visual sheets: draw once through Ctx, wait for the frame to
## render, save the viewport to the PNG named after `--` on the command line,
## and quit. Run with a real renderer (not --headless), e.g.
##
##   xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
##     --path godot res://tests/visual/ctx_shapes.tscn -- tests/visual/godot_ctx_shapes.png
extends Node2D

var ctx := Ctx.new()
var _frames := 0
var sheet_size := Vector2i(640, 480)


func _ready() -> void:
	get_window().size = sheet_size
	get_viewport().size = sheet_size
	RenderingServer.set_default_clear_color(Color(0, 0, 0, 1))


## Override: draw the sheet.
func paint(_c: Ctx) -> void:
	pass


func _draw() -> void:
	ctx.begin(self, sheet_size.x, sheet_size.y)
	var t0 := Time.get_ticks_usec()
	paint(ctx)
	print("paint: %.2f ms" % ((Time.get_ticks_usec() - t0) / 1000.0))
	ctx.end()


func _process(_dt: float) -> void:
	_frames += 1
	if _frames == 4:
		var args := OS.get_cmdline_user_args()
		var out: String = args[0] if args.size() > 0 else "user://sheet.png"
		if not out.begins_with("res://") and not out.begins_with("user://") and not out.begins_with("/"):
			out = ProjectSettings.globalize_path("res://").path_join(out)
		var img := get_viewport().get_texture().get_image()
		img.save_png(out)
		print("wrote ", out)
		get_tree().quit()
