## A Ctx that rasterises into an Image on the CPU, for tests: the TS suite's
## pixel checks (they render to @napi-rs/canvas and read the pixels back) can
## then run headless. Fills and strokes only — the geometry Ctx would hand the
## GPU, scan-converted at pixel centres and blended source-over; text and
## images are skipped. No edge antialiasing (aa is off).
extends Ctx

var img: Image
var w := 0
var h := 0


func _init(width: int, height: int) -> void:
	super()
	w = width
	h = height
	img = Image.create(w, h, false, Image.FORMAT_RGBA8)
	aa = false
	shadows = false
	radial_textures = false # the shader path never reaches _emit
	geo_cache = false # nor does a geometry-cache hit
	canvas.width = w
	canvas.height = h


func _emit(verts: PackedVector2Array, cols: PackedColorArray, idx: PackedInt32Array) -> void:
	var uniform := cols.size() == 1
	var clip := Rect2(0, 0, w, h)
	if _clip_on:
		clip = clip.intersection(_clip_rect)
	for t in range(0, idx.size(), 3):
		var a := verts[idx[t]]
		var b := verts[idx[t + 1]]
		var c := verts[idx[t + 2]]
		var area := (b - a).cross(c - a)
		if absf(area) < 1e-9:
			continue
		var ca: Color = cols[0] if uniform else cols[idx[t]]
		var cb: Color = cols[0] if uniform else cols[idx[t + 1]]
		var cc: Color = cols[0] if uniform else cols[idx[t + 2]]
		var x0 := maxi(int(floor(minf(a.x, minf(b.x, c.x)))), int(clip.position.x))
		var x1 := mini(int(ceil(maxf(a.x, maxf(b.x, c.x)))), int(clip.end.x))
		var y0 := maxi(int(floor(minf(a.y, minf(b.y, c.y)))), int(clip.position.y))
		var y1 := mini(int(ceil(maxf(a.y, maxf(b.y, c.y)))), int(clip.end.y))
		for y in range(y0, y1):
			for x in range(x0, x1):
				var p := Vector2(x + 0.5, y + 0.5)
				var w0 := (b - p).cross(c - p) / area
				var w1 := (c - p).cross(a - p) / area
				var w2 := 1.0 - w0 - w1
				if w0 < 0.0 or w1 < 0.0 or w2 < 0.0:
					continue
				var col := ca if uniform else ca * w0 + cb * w1 + cc * w2
				if col.a <= 0.0:
					continue
				img.set_pixel(x, y, img.get_pixel(x, y).blend(col))


func _draw_text(_text: String, _x: float, _y: float, _max_width: float, _outline: bool) -> void:
	pass


func _blit(_tex: RID, _src: Rect2, _dst: Rect2, _modulate: Color, _premul: bool) -> void:
	pass


## The pixel at (x, y) as 0..255 channels, like ImageData.
func rgb(x: int, y: int) -> Vector3i:
	var c := img.get_pixel(x, y)
	return Vector3i(c.r8, c.g8, c.b8)
