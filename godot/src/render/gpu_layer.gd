## An offscreen layer: a RenderingServer viewport with one canvas, drawn into
## through ordinary canvas items (a Ctx, a shader quad, a texture) and read
## back as a texture. Godot-only — it is what stands in for the TS build's
## offscreen <canvas> elements (the terrain cache, the fog canvas, the night
## layer, the vignette, the minimap base).
##
## The viewport is parented to the main viewport, so it renders *before* it in
## the same frame: draw into it, call `render()`, and blit `tex` this frame.
## It renders only when asked (update mode "once") and keeps its pixels in
## between, so a bake costs nothing per frame.
##
## A transparent layer holds premultiplied alpha (what a 2D viewport renders
## onto a transparent clear): blit it with `Ctx.blit_premul`. An opaque one is
## plain RGB: `Ctx.blit`.
class_name GpuLayer
extends RefCounted

var vp := RID()
var canvas := RID()
var tex := RID()
var w := 0
var h := 0
var transparent := true
var _items: Array = []  ## canvas items made by item(), in draw order
var _keep: Array = []  ## resources the layer's items reference (textures, materials)


func _init(width: int, height: int, transparent_bg: bool = true) -> void:
	w = maxi(1, width)
	h = maxi(1, height)
	transparent = transparent_bg
	vp = RenderingServer.viewport_create()
	RenderingServer.viewport_set_size(vp, w, h)
	RenderingServer.viewport_set_transparent_background(vp, transparent)
	RenderingServer.viewport_set_disable_3d(vp, true)
	RenderingServer.viewport_set_clear_mode(vp, RenderingServer.VIEWPORT_CLEAR_ALWAYS)
	RenderingServer.viewport_set_update_mode(vp, RenderingServer.VIEWPORT_UPDATE_DISABLED)
	var tree := Engine.get_main_loop() as SceneTree
	if tree != null and tree.root != null:
		RenderingServer.viewport_set_parent_viewport(vp, tree.root.get_viewport_rid())
	RenderingServer.viewport_set_active(vp, true)
	canvas = RenderingServer.canvas_create()
	RenderingServer.viewport_attach_canvas(vp, canvas)
	tex = RenderingServer.viewport_get_texture(vp)


## A new canvas item on the layer, drawn after every item made before it.
func item() -> RID:
	var it := RenderingServer.canvas_item_create()
	RenderingServer.canvas_item_set_parent(it, canvas)
	RenderingServer.canvas_item_set_draw_index(it, _items.size())
	_items.append(it)
	return it


## Keep a resource alive as long as the layer (a texture or material an item
## draws with: the RenderingServer holds only its RID).
func keep(r) -> void:
	_keep.append(r)


## Render the layer once, this frame (before the main viewport).
func render() -> void:
	RenderingServer.viewport_set_update_mode(vp, RenderingServer.VIEWPORT_UPDATE_ONCE)


## Read the pixels back (after the layer has rendered): an Image, or null.
func image() -> Image:
	return RenderingServer.texture_2d_get(tex)


func free_layer() -> void:
	# Contexts first (they free their own child items), then the layer's items.
	_keep.clear()
	for it in _items:
		RenderingServer.free_rid(it)
	_items.clear()
	if canvas.is_valid():
		RenderingServer.free_rid(canvas)
	if vp.is_valid():
		RenderingServer.free_rid(vp)
	canvas = RID()
	vp = RID()
	tex = RID()


func _notification(what: int) -> void:
	if what == NOTIFICATION_PREDELETE:
		_keep.clear()
		for it in _items:
			RenderingServer.free_rid(it)
		_items.clear()
		if canvas.is_valid():
			RenderingServer.free_rid(canvas)
		if vp.is_valid():
			RenderingServer.free_rid(vp)
