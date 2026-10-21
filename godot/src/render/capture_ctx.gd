## A Ctx that keeps the geometry it would hand the GPU instead of drawing it,
## so a drawing that never changes (the ground detail across a whole map) can
## be built once, through the same drawing code, and then drawn as a mesh
## every frame for nothing.
##
## Every vertex also gets a UV: (`uv_x`, `uv_y`), values the caller sets before
## drawing a shape. While `blade` is set, uv.y is instead `uv_y` plus how far up
## the blade the vertex sits (0 at `blade_root`, 1 at `blade_h` above it). The
## ground-detail shader uses them to sway grass blade tips without moving their
## roots.
##
## Godot-only. Fills and strokes only (text and images are ignored).
class_name CaptureCtx
extends Ctx

var verts := PackedVector2Array()
var cols := PackedColorArray()
var uvs := PackedVector2Array()
var idx := PackedInt32Array()
var uv_x := 0.0
var uv_y := 0.0
## A blade being captured: its root y and height (device space). Vertices get
## uv.y = how far up the blade they sit (0 at the root, 1 at the tip).
var blade_root := 0.0
var blade_h := 0.0
var blade := false


func _init() -> void:
	super()


func _emit(v: PackedVector2Array, c: PackedColorArray, ix: PackedInt32Array) -> void:
	if v.is_empty() or ix.is_empty():
		return
	var base := verts.size()
	var uniform := c.size() == 1
	verts.append_array(v)
	if uniform:
		var one: Color = c[0]
		for i in v.size():
			cols.append(one)
	else:
		cols.append_array(c)
	if blade and blade_h > 0.0:
		for p in v:
			uvs.append(Vector2(uv_x, uv_y + clampf((blade_root - p.y) / blade_h, 0.0, 1.0)))
	else:
		for i in v.size():
			uvs.append(Vector2(uv_x, uv_y))
	for k in ix:
		idx.append(base + k)


func _draw_text(_text: String, _x: float, _y: float, _max_width: float, _outline: bool) -> void:
	pass


func is_empty() -> bool:
	return idx.is_empty()


## The captured geometry as a 2D ArrayMesh (or null when nothing was drawn).
func to_mesh() -> ArrayMesh:
	if idx.is_empty():
		return null
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = verts
	arrays[Mesh.ARRAY_COLOR] = cols
	arrays[Mesh.ARRAY_TEX_UV] = uvs
	arrays[Mesh.ARRAY_INDEX] = idx
	var m := ArrayMesh.new()
	m.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays, [], {}, Mesh.ARRAY_FLAG_USE_2D_VERTICES)
	return m


func clear_geometry() -> void:
	verts = PackedVector2Array()
	cols = PackedColorArray()
	uvs = PackedVector2Array()
	idx = PackedInt32Array()
