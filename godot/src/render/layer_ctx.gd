## A Ctx that can splice externally-owned canvas items into its paint order.
##
## Godot-only. Most of a frame is immediate-mode drawing, rebuilt every frame
## through Ctx exactly as the TS draws it. A few things are cheaper as
## retained canvas items that are filled once (or a few times a second) and
## then just shown: the terrain texture, the ground-detail meshes, the
## minimap's entity dots and fog. `external(item)` puts such an item at the
## current point of the paint order (under the current transform and clip),
## so it composites exactly where the TS would have drawn it.
##
## An external item that is not placed again in the next frame is hidden at
## `begin()`, so a frame never shows a stale retained item.
class_name LayerCtx
extends Ctx

var _externals: Array = []  ## items placed this frame
var _prev_externals: Array = []


func begin(target, width: int = -1, height: int = -1) -> void:
	super.begin(target, width, height)
	for it in _prev_externals:
		if not _externals.has(it):
			RenderingServer.canvas_item_set_visible(it, false)
	_prev_externals = _externals
	_externals = []


## Composite `item` here: after everything drawn so far this frame, before
## everything drawn after. Its own commands are in user space (the current
## transform is applied to it); the current clip rect applies too.
func external(item: RID, modulate: Color = Color.WHITE) -> void:
	if measure_only or not item.is_valid():
		return
	_seg_i += 1
	if _seg_i >= _segs.size():
		# Keep the segment pool aligned with paint order: this slot's pooled
		# segment stays empty.
		var s := RenderingServer.canvas_item_create()
		RenderingServer.canvas_item_set_parent(s, _root)
		_segs.append(s)
	RenderingServer.canvas_item_clear(_segs[_seg_i])
	RenderingServer.canvas_item_set_parent(item, _root)
	RenderingServer.canvas_item_set_draw_index(item, _seg_i)
	RenderingServer.canvas_item_set_transform(item, _xf)
	RenderingServer.canvas_item_set_clip(item, _clip_on)
	RenderingServer.canvas_item_set_custom_rect(item, _clip_on, _clip_rect if _clip_on else Rect2())
	RenderingServer.canvas_item_set_modulate(item, Color(modulate.r, modulate.g, modulate.b, modulate.a * globalAlpha))
	RenderingServer.canvas_item_set_visible(item, true)
	_externals.append(item)
	_seg_dirty = true


## Stop tracking an external item (call before freeing it).
func forget(item: RID) -> void:
	_externals.erase(item)
	_prev_externals.erase(item)


## The device transform in force (user → canvas pixels).
func device_transform() -> Transform2D:
	return _xf
