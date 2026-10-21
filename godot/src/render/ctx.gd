## CanvasRenderingContext2D, for Godot.
##
## The TypeScript game draws everything — world, HUD, menus — immediate-mode
## through the browser's 2D canvas. This class gives the port that same API
## (`fillStyle`, `beginPath`, `arc`, `fill`, `fillText`, gradients,
## `save`/`restore`, transforms, clipping…) on top of the RenderingServer, so the
## drawing code ports line by line. Call `begin(canvas_item)` at the top of a
## CanvasItem's `_draw()` (or with any canvas item RID), then draw exactly as the
## TS does.
##
## How it maps:
## - Path points are transformed to device space as they are added (that is the
##   canvas rule: the transform in force when a point is added is the one that
##   applies), curves are flattened there to a fixed tolerance.
## - `fill()` is nonzero. Convex shapes are fanned; simple polygons go through
##   Geometry2D's triangulator; anything self-intersecting or made of several
##   subpaths goes through a scanline tessellator that honours the winding rule.
## - `stroke()` is built here (Godot's polylines have no caps or joins): butt /
##   round / square caps, miter / round / bevel joins, dashes.
## - The Compatibility renderer has no 2D MSAA, so every fill and stroke carries
##   a one-pixel alpha fringe instead — the same coverage antialiasing the canvas
##   does. `aa = false` turns it off.
## - Gradients become per-vertex colours on the tessellated geometry.
## - Clips: the clip's bounding box clips exactly (each clip state draws into its
##   own child canvas item with a clip rect, so ordering is kept); a clip that is
##   not an axis-aligned rectangle additionally cuts fills and strokes to its
##   polygon on the CPU. Text and images are clipped to the bounding box only.
## - Shadows are approximated with a few dilated, translucent layers.
## - `globalCompositeOperation`: "source-over", "lighter" (additive) and
##   "destination-out" are supported; anything else draws as source-over.
class_name Ctx
extends RefCounted

# =============================================================== helpers ==

## A canvas gradient (createLinearGradient / createRadialGradient).
class CanvasGradient extends RefCounted:
	var radial := false
	var x0 := 0.0
	var y0 := 0.0
	var r0 := 0.0
	var x1 := 0.0
	var y1 := 0.0
	var r1 := 0.0
	var offsets := PackedFloat32Array()
	var colors := PackedColorArray()
	## The measure pass's stand-in: ignores its stops (nothing is painted).
	var frozen := false

	func addColorStop(offset: float, color) -> void:
		if frozen:
			return
		var c = Ctx.parse_color(color) if color is String else color
		if not (c is Color) or offset < 0.0 or offset > 1.0:
			push_error("CanvasGradient.addColorStop: bad stop %s %s" % [offset, color])
			return
		# Stops keep insertion order among equal offsets (later ones after).
		var i := offsets.size()
		while i > 0 and offsets[i - 1] > offset:
			i -= 1
		offsets.insert(i, offset)
		colors.insert(i, c)

	## Colour at gradient parameter t (clamped), straight-alpha interpolation.
	func at(t: float) -> Color:
		var n := offsets.size()
		if n == 0:
			return Color(0, 0, 0, 0)
		if t <= offsets[0]:
			return colors[0]
		if t >= offsets[n - 1]:
			return colors[n - 1]
		for i in range(1, n):
			if t <= offsets[i]:
				var o0 := offsets[i - 1]
				var span := offsets[i] - o0
				if span <= 0.0:
					return colors[i]
				return colors[i - 1].lerp(colors[i], (t - o0) / span)
		return colors[n - 1]

	## Gradient parameter of a point in the gradient's own (user) space.
	func t_at(p: Vector2) -> float:
		if not radial:
			var dx := x1 - x0
			var dy := y1 - y0
			var dd := dx * dx + dy * dy
			if dd <= 0.0:
				return 0.0
			return ((p.x - x0) * dx + (p.y - y0) * dy) / dd
		# Radial: solve for the largest t with |p - c(t)| = r(t), where the
		# circle interpolates from (x0,y0,r0) to (x1,y1,r1). The common case —
		# concentric circles — is the simple ratio.
		var cdx := x1 - x0
		var cdy := y1 - y0
		var dr := r1 - r0
		var px := p.x - x0
		var py := p.y - y0
		if absf(cdx) < 1e-9 and absf(cdy) < 1e-9:
			if absf(dr) < 1e-9:
				return 0.0
			return (sqrt(px * px + py * py) - r0) / dr
		var a := cdx * cdx + cdy * cdy - dr * dr
		var b := px * cdx + py * cdy + r0 * dr
		var c := px * px + py * py - r0 * r0
		if absf(a) < 1e-9:
			return c / (2.0 * b) if absf(b) > 1e-9 else 0.0
		var disc := b * b - a * c
		if disc < 0.0:
			return 0.0
		var sq := sqrt(disc)
		var t := (b + sq) / a
		if r0 + t * dr < 0.0:
			t = (b - sq) / a
		return t

	## A single representative colour (text drawn with a gradient style).
	func first_color() -> Color:
		return colors[0] if colors.size() > 0 else Color(0, 0, 0, 0)


## What measureText returns (only `width` is read by the game).
class TextMetrics extends RefCounted:
	var width := 0.0
	var actualBoundingBoxAscent := 0.0
	var actualBoundingBoxDescent := 0.0
	var fontBoundingBoxAscent := 0.0
	var fontBoundingBoxDescent := 0.0


## `ctx.canvas` — just the size, which is all the game reads from it.
class CanvasInfo extends RefCounted:
	var width := 0
	var height := 0


# ============================================================ attributes ==
# Same names and defaults as CanvasRenderingContext2D.

var fillStyle: Variant:
	get:
		return _fillStyle
	set(v):
		var p = _paint_of(v)
		if p == null:
			return
		_fillStyle = v
		_fill_paint = p

var strokeStyle: Variant:
	get:
		return _strokeStyle
	set(v):
		var p = _paint_of(v)
		if p == null:
			return
		_strokeStyle = v
		_stroke_paint = p

var lineWidth: float:
	get:
		return _lineWidth
	set(v):
		if v > 0.0 and not is_inf(v) and not is_nan(v):
			_lineWidth = v

var lineCap := "butt"
var lineJoin := "miter"
var miterLimit := 10.0

var globalAlpha: float:
	get:
		return _globalAlpha
	set(v):
		if v >= 0.0 and v <= 1.0:
			_globalAlpha = v

var font: String:
	get:
		return _font
	set(v):
		var spec = _font_spec_of(v)
		if spec == null:
			return
		_font = v
		_font_spec = spec

var textAlign := "start"
var textBaseline := "alphabetic"
var direction := "ltr"

var shadowColor: Variant:
	get:
		return _shadowColor
	set(v):
		var c = Ctx.parse_color(v) if v is String else v
		if not (c is Color):
			return
		_shadowColor = v
		_shadow_col = c

var shadowBlur := 0.0
var shadowOffsetX := 0.0
var shadowOffsetY := 0.0
var lineDashOffset := 0.0

var globalCompositeOperation: String:
	get:
		return _composite
	set(v):
		if v != _composite:
			_composite = v
			_seg_dirty = true

var imageSmoothingEnabled := true
var canvas := CanvasInfo.new()

## Edge antialiasing (a 1px alpha fringe on every fill and stroke).
var aa := true
## Shadow approximation on/off (the art cache turns it off for nothing; it is
## here for "reduce effects" style toggles).
var shadows := true
## Curve flattening tolerance in device pixels.
var tolerance := 0.2

## Measure-only mode: nothing is drawn, `bounds` grows to cover what would be.
var measure_only := false
var bounds := Rect2()
var bounds_valid := false

# ================================================================ state ==

var _fillStyle: Variant = "#000000"
var _strokeStyle: Variant = "#000000"
var _lineWidth := 1.0
var _globalAlpha := 1.0
var _font := "10px sans-serif"
var _shadowColor: Variant = "rgba(0, 0, 0, 0)"
var _composite := "source-over"
var _fill_paint: Variant = Color.BLACK
var _stroke_paint: Variant = Color.BLACK
var _shadow_col := Color(0, 0, 0, 0)
var _font_spec: Array = []
var _dash := PackedFloat64Array()

var _xf := Transform2D.IDENTITY
var _scale := 1.0

var _clip_on := false
var _clip_rect := Rect2()
## Non-empty when the clip is not exactly `_clip_rect` (an arbitrary path).
var _clip_poly := PackedVector2Array()

var _stack: Array = []

# Path, in device space.
var _subs: Array = []  ## PackedVector2Array per finished subpath
var _subs_closed: Array = []  ## bool
var _subs_flags: PackedInt32Array = PackedInt32Array()
var _cur := PackedVector2Array()
var _cur_flags := 0
var _cur_arcs := 0
var _cur_start := Vector2.ZERO
var _has_cur := false

const _F_CONVEX := 1  ## the subpath is one convex primitive (rect, circle…)
const _F_COMPLEX := 2  ## several arcs joined: may self-intersect

# Output.
var _root := RID()
var _seg := RID()
var _segs: Array = []  ## pooled child canvas items, one per clip/blend state
var _seg_i := -1
var _seg_dirty := true
var _seg_premul := false

# Shared caches.
static var _color_cache := {}
static var _font_spec_cache := {}
static var _sysfont_cache := {}
static var _fan_cache := {}
static var _ring_cache := {}
static var _strip_cache := {}
static var _arc_cache := {}
static var _add_material := RID()
static var _premul_material := RID()
static var _dest_out_material := RID()
static var _dest_out_shader := RID()


func _init() -> void:
	_font_spec = _font_spec_of(font)


func _notification(what: int) -> void:
	if what == NOTIFICATION_PREDELETE:
		for s in _segs:
			RenderingServer.free_rid(s)
		_segs.clear()


# ========================================================= frame / target ==

## Start drawing a frame into `target` (a CanvasItem — call from its `_draw()` —
## or a canvas item RID). Clears what this context drew there last frame and
## resets the state stack (but not the attributes, as a canvas wouldn't).
func begin(target, width: int = -1, height: int = -1) -> void:
	var rid: RID = target.get_canvas_item() if target is CanvasItem else target
	if rid != _root:
		_root = rid
		for s in _segs:
			RenderingServer.canvas_item_set_parent(s, _root)
	for s in _segs:
		RenderingServer.canvas_item_clear(s)
	_seg_i = -1
	_seg_dirty = true
	_stack.clear()
	_xf = Transform2D.IDENTITY
	_scale = 1.0
	_clip_on = false
	_clip_poly = PackedVector2Array()
	beginPath()
	if width >= 0:
		canvas.width = width
		canvas.height = height
	elif target is CanvasItem:
		var r: Rect2 = target.get_viewport_rect()
		canvas.width = int(r.size.x)
		canvas.height = int(r.size.y)


## Nothing to flush (every call is emitted as it is made); kept so call sites
## read as a bracket.
func end() -> void:
	pass


## Back to the default attributes (what a fresh canvas has).
func reset() -> void:
	fillStyle = "#000000"
	strokeStyle = "#000000"
	lineWidth = 1.0
	lineCap = "butt"
	lineJoin = "miter"
	miterLimit = 10.0
	globalAlpha = 1.0
	font = "10px sans-serif"
	textAlign = "start"
	textBaseline = "alphabetic"
	shadowColor = "rgba(0, 0, 0, 0)"
	shadowBlur = 0.0
	shadowOffsetX = 0.0
	shadowOffsetY = 0.0
	lineDashOffset = 0.0
	_dash = PackedFloat64Array()
	globalCompositeOperation = "source-over"
	_xf = Transform2D.IDENTITY
	_scale = 1.0
	_stack.clear()
	_clip_on = false
	_clip_poly = PackedVector2Array()
	_seg_dirty = true
	beginPath()


## The canvas item the next command goes into. A new one (from the pool)
## whenever the clip, the composite operation or the premultiplied-blit mode
## changes, so each keeps its own clip rect / material and the order holds.
func _segment(premul: bool = false) -> RID:
	if premul != _seg_premul:
		_seg_dirty = true
	if not _seg_dirty:
		return _seg
	_seg_dirty = false
	_seg_premul = premul
	_seg_i += 1
	if _seg_i >= _segs.size():
		var s := RenderingServer.canvas_item_create()
		RenderingServer.canvas_item_set_parent(s, _root)
		_segs.append(s)
	_seg = _segs[_seg_i]
	RenderingServer.canvas_item_clear(_seg)
	RenderingServer.canvas_item_set_draw_index(_seg, _seg_i)
	RenderingServer.canvas_item_set_clip(_seg, _clip_on)
	RenderingServer.canvas_item_set_custom_rect(_seg, _clip_on, _clip_rect if _clip_on else Rect2())
	var mat := RID()
	if premul:
		mat = _get_premul_material()
	elif _composite == "lighter":
		mat = _get_add_material()
	elif _composite == "destination-out":
		mat = _get_dest_out_material()
	RenderingServer.canvas_item_set_material(_seg, mat)
	return _seg


static func _get_premul_material() -> RID:
	if not _premul_material.is_valid():
		var m := CanvasItemMaterial.new()
		m.blend_mode = CanvasItemMaterial.BLEND_MODE_PREMULT_ALPHA
		_keep.append(m)
		_premul_material = m.get_rid()
	return _premul_material


static func _get_add_material() -> RID:
	if not _add_material.is_valid():
		var m := CanvasItemMaterial.new()
		m.blend_mode = CanvasItemMaterial.BLEND_MODE_ADD
		_keep.append(m)
		_add_material = m.get_rid()
	return _add_material


static var _keep: Array = []


static func _get_dest_out_material() -> RID:
	if not _dest_out_material.is_valid():
		var sh := Shader.new()
		sh.code = "shader_type canvas_item;\nrender_mode blend_mul;\nvoid fragment() { COLOR = vec4(1.0, 1.0, 1.0, 1.0 - COLOR.a); }\n"
		var m := ShaderMaterial.new()
		m.shader = sh
		_keep.append(sh)
		_keep.append(m)
		_dest_out_material = m.get_rid()
	return _dest_out_material


# ================================================================ colours ==

## Parse a CSS colour string ("#rgb", "#rrggbb", "#rrggbbaa", "rgb()", "rgba()",
## "hsl()", "hsla()", a named colour, "transparent"). Returns a Color, or null
## when the string isn't a colour. Results are cached.
static func parse_color(s: String) -> Variant:
	var c = _color_cache.get(s)
	if c != null:
		return c
	c = _parse_color_uncached(s)
	if c != null:
		if _color_cache.size() > 8192:
			_color_cache.clear()
		_color_cache[s] = c
	return c


static func _parse_color_uncached(src: String) -> Variant:
	var s := src.strip_edges().to_lower()
	if s.is_empty():
		return null
	if s[0] == "#":
		var h := s.substr(1)
		if not h.is_valid_hex_number():
			return null
		match h.length():
			3, 4:
				var r := ("0x" + h[0]).hex_to_int() * 17
				var g := ("0x" + h[1]).hex_to_int() * 17
				var b := ("0x" + h[2]).hex_to_int() * 17
				var a := ("0x" + h[3]).hex_to_int() * 17 if h.length() == 4 else 255
				return Color8(r, g, b, a)
			6, 8:
				var r := ("0x" + h.substr(0, 2)).hex_to_int()
				var g := ("0x" + h.substr(2, 2)).hex_to_int()
				var b := ("0x" + h.substr(4, 2)).hex_to_int()
				var a := ("0x" + h.substr(6, 2)).hex_to_int() if h.length() == 8 else 255
				return Color8(r, g, b, a)
		return null
	var paren := s.find("(")
	if paren > 0 and s.ends_with(")"):
		var fn := s.substr(0, paren).strip_edges()
		var body := s.substr(paren + 1, s.length() - paren - 2)
		# Commas, spaces and the "/ alpha" form all separate arguments.
		var parts: Array = []
		for tok in body.replace(",", " ").replace("/", " ").split(" ", false):
			parts.append(tok)
		if parts.size() < 3:
			return null
		var alpha := 1.0
		if parts.size() >= 4:
			var at: String = parts[3]
			alpha = at.to_float() / 100.0 if at.ends_with("%") else at.to_float()
		alpha = clampf(alpha, 0.0, 1.0)
		if fn == "rgb" or fn == "rgba":
			var ch := []
			for i in 3:
				var t: String = parts[i]
				var v := t.to_float() * 2.55 if t.ends_with("%") else t.to_float()
				ch.append(clampf(v, 0.0, 255.0) / 255.0)
			return Color(ch[0], ch[1], ch[2], alpha)
		if fn == "hsl" or fn == "hsla":
			var hh: String = parts[0]
			var hue := hh.trim_suffix("deg").to_float()
			if hh.ends_with("turn"):
				hue = hh.trim_suffix("turn").to_float() * 360.0
			elif hh.ends_with("rad"):
				hue = rad_to_deg(hh.trim_suffix("rad").to_float())
			var sat := clampf(String(parts[1]).to_float() / 100.0, 0.0, 1.0)
			var lig := clampf(String(parts[2]).to_float() / 100.0, 0.0, 1.0)
			return _hsl(hue, sat, lig, alpha)
		return null
	if s == "transparent":
		return Color(0, 0, 0, 0)
	var named = _NAMED.get(s)
	if named != null:
		return Color.html(named)
	return null


static func _hsl(h: float, s: float, l: float, a: float) -> Color:
	h = fposmod(h, 360.0) / 360.0
	var q := l * (1.0 + s) if l < 0.5 else l + s - l * s
	var p := 2.0 * l - q
	return Color(_hue(p, q, h + 1.0 / 3.0), _hue(p, q, h), _hue(p, q, h - 1.0 / 3.0), a)


static func _hue(p: float, q: float, t: float) -> float:
	t = fposmod(t, 1.0)
	if t < 1.0 / 6.0:
		return p + (q - p) * 6.0 * t
	if t < 0.5:
		return q
	if t < 2.0 / 3.0:
		return p + (q - p) * (2.0 / 3.0 - t) * 6.0
	return p


static func _paint_of(v) -> Variant:
	if v is String:
		return parse_color(v)
	if v is Color or v is CanvasGradient:
		return v
	if v is StringName:
		return parse_color(String(v))
	return null


# ================================================================== fonts ==

## Parse a CSS font shorthand ("bold 13px 'Trebuchet MS', sans-serif",
## "italic 16px Georgia, serif"). Returns [px, families, weight, italic] or
## null.
static func _font_spec_of(css: String) -> Variant:
	var spec = _font_spec_cache.get(css)
	if spec != null:
		return spec
	spec = _parse_font(css)
	if spec != null:
		_font_spec_cache[css] = spec
	return spec


static func _parse_font(css: String) -> Variant:
	var s := css.strip_edges()
	var weight := 400
	var italic := false
	var px := -1.0
	var rest := ""
	var i := 0
	var toks := s.split(" ", false)
	while i < toks.size():
		var t := toks[i].to_lower()
		if t == "italic" or t == "oblique":
			italic = true
		elif t == "bold" or t == "bolder":
			weight = 700
		elif t == "lighter":
			weight = 300
		elif t == "normal" or t == "small-caps":
			pass
		elif t.is_valid_int() and t.length() == 3:
			weight = t.to_int()
		else:
			# The size, maybe with "/line-height"; everything after is families.
			var sz := t.split("/")[0]
			var num := ""
			var unit := ""
			for ch in sz:
				if (ch >= "0" and ch <= "9") or ch == ".":
					if unit.is_empty():
						num += ch
					else:
						return null
				else:
					unit += ch
			if num.is_empty():
				return null
			px = num.to_float()
			match unit:
				"px", "":
					pass
				"pt":
					px *= 4.0 / 3.0
				"em", "rem":
					px *= 16.0
				_:
					return null
			rest = " ".join(toks.slice(i + 1))
			break
		i += 1
	if px <= 0.0 or rest.strip_edges().is_empty():
		return null
	var fams := PackedStringArray()
	for f in rest.split(","):
		var name := f.strip_edges().trim_prefix("\"").trim_suffix("\"").trim_prefix("'").trim_suffix("'")
		if not name.is_empty():
			fams.append(name)
	return [px, fams, weight, italic]


## The Godot font for a parsed spec: a SystemFont with the CSS families (and
## sensible fallbacks), one object per (families, weight, style).
static func font_for(spec: Array) -> Font:
	var fams: PackedStringArray = spec[1]
	var key := "%s|%d|%s" % [",".join(fams), spec[2], spec[3]]
	var f = _sysfont_cache.get(key)
	if f != null:
		return f
	var names := PackedStringArray()
	var generic := "sans-serif"
	for fam in fams:
		var low := fam.to_lower()
		match low:
			"sans-serif", "system-ui", "ui-sans-serif", "-apple-system":
				generic = "sans-serif"
			"serif", "ui-serif":
				generic = "serif"
			"monospace", "ui-monospace":
				generic = "monospace"
			_:
				names.append(fam)
				if low.contains("mono") or low == "courier new" or low == "consolas" or low == "menlo":
					generic = "monospace"
				elif low == "georgia" or low == "times new roman" or low == "times" or low == "palatino":
					generic = "serif"
	# Where the CSS family isn't installed (Trebuchet MS and Georgia are on
	# Windows and macOS, not on most Linux), fall back to fonts with similar
	# metrics first — Liberation Sans/Serif have Arial/Times widths, close to
	# what the layouts were designed around — then whatever the system has.
	match generic:
		"serif":
			names.append_array(["Liberation Serif", "Tinos", "DejaVu Serif", "serif"])
		"monospace":
			names.append_array(["Liberation Mono", "DejaVu Sans Mono", "monospace"])
		_:
			names.append_array(["Liberation Sans", "Arimo", "DejaVu Sans", "sans-serif"])
	var sf := SystemFont.new()
	sf.font_names = names
	sf.font_weight = spec[2]
	sf.font_italic = spec[3]
	sf.antialiasing = TextServer.FONT_ANTIALIASING_GRAY
	sf.hinting = TextServer.HINTING_LIGHT
	sf.subpixel_positioning = TextServer.SUBPIXEL_POSITIONING_AUTO
	sf.allow_system_fallback = true
	_sysfont_cache[key] = sf
	return sf


# ============================================================ state stack ==

func save() -> void:
	_stack.append([
		_fillStyle, _fill_paint, _strokeStyle, _stroke_paint, _lineWidth, lineCap, lineJoin,
		miterLimit, _globalAlpha, _font, _font_spec, textAlign, textBaseline, _shadowColor,
		_shadow_col, shadowBlur, shadowOffsetX, shadowOffsetY, _dash, lineDashOffset,
		_composite, _xf, _scale, _clip_on, _clip_rect, _clip_poly,
		imageSmoothingEnabled, direction,
	])


func restore() -> void:
	if _stack.is_empty():
		return
	var s: Array = _stack.pop_back()
	if s[23] != _clip_on or s[24] != _clip_rect or s[25] != _clip_poly or s[20] != _composite:
		_seg_dirty = true
	_fillStyle = s[0]
	_fill_paint = s[1]
	_strokeStyle = s[2]
	_stroke_paint = s[3]
	_lineWidth = s[4]
	lineCap = s[5]
	lineJoin = s[6]
	miterLimit = s[7]
	_globalAlpha = s[8]
	_font = s[9]
	_font_spec = s[10]
	textAlign = s[11]
	textBaseline = s[12]
	_shadowColor = s[13]
	_shadow_col = s[14]
	shadowBlur = s[15]
	shadowOffsetX = s[16]
	shadowOffsetY = s[17]
	_dash = s[18]
	lineDashOffset = s[19]
	_composite = s[20]
	_xf = s[21]
	_scale = s[22]
	_clip_on = s[23]
	_clip_rect = s[24]
	_clip_poly = s[25]
	imageSmoothingEnabled = s[26]
	direction = s[27]


# ============================================================= transforms ==

func translate(x: float, y: float) -> void:
	_xf = _xf.translated_local(Vector2(x, y))


func rotate(a: float) -> void:
	_xf = _xf * Transform2D(a, Vector2.ZERO)


func scale(sx: float, sy: float) -> void:
	_xf = _xf.scaled_local(Vector2(sx, sy))
	_scale = sqrt(absf(_xf.determinant()))


## setTransform(a, b, c, d, e, f), or setTransform(Transform2D) for a value
## from getTransform().
func setTransform(a, b = null, c = null, d = null, e = null, f = null) -> void:
	if a is Transform2D:
		_xf = a
	else:
		_xf = Transform2D(Vector2(a, b), Vector2(c, d), Vector2(e, f))
	_scale = sqrt(absf(_xf.determinant()))


func transform(a: float, b: float, c: float, d: float, e: float, f: float) -> void:
	_xf = _xf * Transform2D(Vector2(a, b), Vector2(c, d), Vector2(e, f))
	_scale = sqrt(absf(_xf.determinant()))


func resetTransform() -> void:
	_xf = Transform2D.IDENTITY
	_scale = 1.0


## The current transform (a DOMMatrix in the TS: a=x.x, b=x.y, c=y.x, d=y.y,
## e=origin.x, f=origin.y).
func getTransform() -> Transform2D:
	return _xf


# ================================================================== paths ==

func beginPath() -> void:
	_subs.clear()
	_subs_closed.clear()
	_subs_flags.clear()
	_subs_shape.clear()
	_cur = PackedVector2Array()
	_cur_flags = 0
	_cur_arcs = 0
	_cur_shape = null
	_has_cur = false


func _finish_cur(closed: bool) -> void:
	if _cur.size() > 0:
		_subs.append(_cur)
		_subs_closed.append(closed)
		_subs_flags.append(_cur_flags)
		_subs_shape.append(_cur_shape if _cur_flags & _F_CONVEX else null)
	_cur = PackedVector2Array()
	_cur_flags = 0
	_cur_arcs = 0
	_cur_shape = null


## A whole ellipse (or circle) subpath remembers what it is, so fill and
## stroke can build its antialiased rings straight from the cached unit
## circle instead of walking it point by point:
## [transform at add time, rotation, centre, rx, ry, segments, start angle, is circle, scale].
var _cur_shape = null
var _subs_shape: Array = []


func _add(p: Vector2) -> void:
	var n := _cur.size()
	if n > 0 and _cur[n - 1].distance_squared_to(p) < 1e-10:
		return
	_cur.append(p)


func moveTo(x: float, y: float) -> void:
	_finish_cur(false)
	var p := _xf * Vector2(x, y)
	_cur.append(p)
	_cur_start = p
	_has_cur = true


func lineTo(x: float, y: float) -> void:
	if not _has_cur:
		moveTo(x, y)
		return
	_add(_xf * Vector2(x, y))
	_cur_flags &= ~_F_CONVEX


func closePath() -> void:
	if not _has_cur:
		return
	if _cur.size() > 0:
		_finish_cur(true)
	# The next segment starts a new subpath at the closed one's start.
	_cur.append(_cur_start)


func rect(x: float, y: float, w: float, h: float) -> void:
	_finish_cur(false)
	var a := _xf * Vector2(x, y)
	_cur.append(a)
	_cur.append(_xf * Vector2(x + w, y))
	_cur.append(_xf * Vector2(x + w, y + h))
	_cur.append(_xf * Vector2(x, y + h))
	_cur_flags = _F_CONVEX
	_finish_cur(true)
	_cur.append(a)
	_cur_start = a
	_has_cur = true


## roundRect(x, y, w, h, radii) — radii a number or an array of 1–4 numbers
## (corners tl, tr, br, bl as the canvas spec assigns them).
func roundRect(x: float, y: float, w: float, h: float, radii = 0.0) -> void:
	var tl := 0.0
	var tr := 0.0
	var br := 0.0
	var bl := 0.0
	if radii is Array or radii is PackedFloat64Array or radii is PackedFloat32Array:
		var r: Array = Array(radii)
		match r.size():
			1:
				tl = r[0]; tr = r[0]; br = r[0]; bl = r[0]
			2:
				tl = r[0]; br = r[0]; tr = r[1]; bl = r[1]
			3:
				tl = r[0]; tr = r[1]; bl = r[1]; br = r[2]
			4:
				tl = r[0]; tr = r[1]; br = r[2]; bl = r[3]
			_:
				pass
	else:
		var rv := float(radii)
		tl = rv; tr = rv; br = rv; bl = rv
	tl = maxf(tl, 0.0); tr = maxf(tr, 0.0); br = maxf(br, 0.0); bl = maxf(bl, 0.0)
	# Negative sizes flip the rectangle (and which corner is which).
	if w < 0.0:
		x += w
		w = -w
		var t := tl; tl = tr; tr = t
		t = bl; bl = br; br = t
	if h < 0.0:
		y += h
		h = -h
		var t := tl; tl = bl; bl = t
		t = tr; tr = br; br = t
	# Scale radii down together if any pair overlaps.
	var f := 1.0
	if tl + tr > 0.0: f = minf(f, w / (tl + tr))
	if bl + br > 0.0: f = minf(f, w / (bl + br))
	if tl + bl > 0.0: f = minf(f, h / (tl + bl))
	if tr + br > 0.0: f = minf(f, h / (tr + br))
	if f < 1.0:
		tl *= f; tr *= f; br *= f; bl *= f
	_finish_cur(false)
	var start := _xf * Vector2(x, y)
	if tl + tr + br + bl <= 0.0:
		rect(x, y, w, h)
		return
	_corner(x + w - tr, y + tr, tr, -PI / 2, 0.0, Vector2(x + w, y))
	_corner(x + w - br, y + h - br, br, 0.0, PI / 2, Vector2(x + w, y + h))
	_corner(x + bl, y + h - bl, bl, PI / 2, PI, Vector2(x, y + h))
	_corner(x + tl, y + tl, tl, PI, PI * 1.5, Vector2(x, y))
	_cur_flags = _F_CONVEX
	_finish_cur(true)
	_cur.append(start)
	_cur_start = start
	_has_cur = true


func _corner(cx: float, cy: float, r: float, a0: float, a1: float, sharp: Vector2) -> void:
	if r <= 0.0:
		_add(_xf * sharp)
		return
	var n := _arc_segments(r, a1 - a0)
	var unit := _unit_arc(n, a1 - a0)
	var t := _xf * Transform2D(a0, Vector2(r, r), 0.0, Vector2(cx, cy))
	var pts := t * unit
	for p in pts:
		_add(p)


func _arc_segments(r: float, sweep: float) -> int:
	var rd := r * _scale
	if rd <= tolerance:
		return maxi(1, ceili(absf(sweep) / TAU * 4.0))
	var step := 2.0 * acos(1.0 - tolerance / rd)
	return clampi(ceili(absf(sweep) / step), 1, 256)


## Flattened arcs are drawn as polygons whose vertices sit a touch outside
## the circle — by sqrt(δ / sin δ) for an angular step δ — so each segment
## covers the same area as the true sector instead of being a chord inside it
## (an inscribed 9-gon is 8% short of a small head's area).
static func _area_k(step: float) -> float:
	var d := absf(step)
	if d < 1e-6:
		return 1.0
	return sqrt(d / sin(d)) if d < PI else 1.0


## Points of a unit-circle arc from angle 0 through `sweep`, n segments
## (n + 1 points). Cached: most arcs in the art have constant sweeps.
static func _unit_arc(n: int, sweep: float) -> PackedVector2Array:
	var key := Vector2(n, sweep)
	var pts = _arc_cache.get(key)
	if pts != null:
		return pts
	pts = PackedVector2Array()
	pts.resize(n + 1)
	# Interior points only — the ends meet straight edges (a rounded
	# rect's sides) and must stay put — unless it's the whole circle.
	var f := _area_k(sweep / n)
	var whole := absf(sweep) >= TAU - 1e-9
	for k in n + 1:
		var a := sweep * k / n
		pts[k] = Vector2(cos(a), sin(a)) * (f if whole or (k > 0 and k < n) else 1.0)
	if _arc_cache.size() > 4096:
		_arc_cache.clear()
	_arc_cache[key] = pts
	return pts


## Unit ellipse arc from a0 through sweep, for rx != ry (the start angle can't
## be folded into a rotation there).
static func _unit_arc_from(n: int, a0: float, sweep: float) -> PackedVector2Array:
	var key := Vector3(n, a0, sweep)
	var pts = _arc_cache.get(key)
	if pts != null:
		return pts
	pts = PackedVector2Array()
	pts.resize(n + 1)
	# Interior points only — the ends meet straight edges (a rounded
	# rect's sides) and must stay put — unless it's the whole circle.
	var f := _area_k(sweep / n)
	var whole := absf(sweep) >= TAU - 1e-9
	for k in n + 1:
		var a := a0 + sweep * k / n
		pts[k] = Vector2(cos(a), sin(a)) * (f if whole or (k > 0 and k < n) else 1.0)
	if _arc_cache.size() > 4096:
		_arc_cache.clear()
	_arc_cache[key] = pts
	return pts


static func _sweep(a0: float, a1: float, ccw: bool) -> float:
	if not ccw:
		if a1 - a0 >= TAU:
			return TAU
		var d := fmod(a1 - a0, TAU)
		if d < 0.0:
			d += TAU
		# Equal angles (mod 2π) but not equal values: canvas draws nothing more
		# than the start point unless the raw difference is a full turn.
		return d
	if a0 - a1 >= TAU:
		return -TAU
	var d2 := fmod(a0 - a1, TAU)
	if d2 < 0.0:
		d2 += TAU
	return -d2


func arc(x: float, y: float, r: float, a0: float, a1: float, ccw: bool = false) -> void:
	ellipse(x, y, r, r, 0.0, a0, a1, ccw)


func ellipse(x: float, y: float, rx: float, ry: float, rot: float, a0: float, a1: float, ccw: bool = false) -> void:
	if rx < 0.0 or ry < 0.0:
		push_error("Ctx.ellipse: negative radius")
		return
	var sweep := _sweep(a0, a1, ccw)
	var n := _arc_segments(maxf(rx, ry), sweep) if sweep != 0.0 else 0
	var pts: PackedVector2Array
	if n == 0:
		pts = PackedVector2Array([_xf * (Vector2(x, y) + Vector2(rx * cos(a0), ry * sin(a0)).rotated(rot))])
	elif rx == ry:
		pts = (_xf * Transform2D(rot + a0, Vector2(rx, rx), 0.0, Vector2(x, y))) * _unit_arc(n, sweep)
	else:
		pts = (_xf * Transform2D(rot, Vector2(rx, ry), 0.0, Vector2(x, y))) * _unit_arc_from(n, a0, sweep)
	var fresh := not _has_cur or _cur.size() == 0 or (_cur.size() == 1 and _cur[0].distance_squared_to(pts[0]) < 1e-10)
	if not _has_cur:
		_finish_cur(false)
		_cur_start = pts[0]
		_has_cur = true
	if fresh and _cur_arcs == 0:
		_cur_flags = _F_CONVEX
		if absf(sweep) >= TAU - 1e-9 and n > 2:
			_cur_shape = [_xf, rot + a0 if rx == ry else rot, Vector2(x, y), rx, ry, n, a0, rx == ry, _scale]
	else:
		_cur_flags &= ~_F_CONVEX
		_cur_shape = null
	_cur_arcs += 1
	if _cur_arcs >= 2:
		_cur_flags |= _F_COMPLEX
	var sz := _cur.size()
	if sz > 0 and _cur[sz - 1].distance_squared_to(pts[0]) < 1e-10:
		pts.remove_at(0)
	_cur.append_array(pts)


func arcTo(x1: float, y1: float, x2: float, y2: float, r: float) -> void:
	if not _has_cur:
		moveTo(x1, y1)
	var p0 := _xf.affine_inverse() * _cur[_cur.size() - 1] if _cur.size() > 0 else Vector2(x1, y1)
	var p1 := Vector2(x1, y1)
	var p2 := Vector2(x2, y2)
	var d0 := (p0 - p1)
	var d2 := (p2 - p1)
	if r <= 0.0 or d0.length_squared() < 1e-12 or d2.length_squared() < 1e-12 or absf(d0.normalized().cross(d2.normalized())) < 1e-9:
		lineTo(x1, y1)
		return
	d0 = d0.normalized()
	d2 = d2.normalized()
	var ang := acos(clampf(d0.dot(d2), -1.0, 1.0))
	var dist := r / tan(ang / 2.0)
	var t0 := p1 + d0 * dist
	var t2 := p1 + d2 * dist
	var bis := (d0 + d2).normalized()
	var c := p1 + bis * (r / sin(ang / 2.0))
	var a0 := (t0 - c).angle()
	var a1 := (t2 - c).angle()
	var ccw := d0.cross(d2) < 0.0
	lineTo(t0.x, t0.y)
	var keep := _cur_flags
	arc(c.x, c.y, r, a0, a1, not ccw)
	_cur_flags = keep & ~_F_CONVEX
	_cur_arcs = 0


func quadraticCurveTo(cpx: float, cpy: float, x: float, y: float) -> void:
	if not _has_cur:
		moveTo(cpx, cpy)
	var p0 := _cur[_cur.size() - 1] if _cur.size() > 0 else _xf * Vector2(cpx, cpy)
	var p1 := _xf * Vector2(cpx, cpy)
	var p2 := _xf * Vector2(x, y)
	var dd := (p0 - p1 * 2.0 + p2).length()
	var n := clampi(ceili(sqrt(dd / (4.0 * tolerance))), 1, 64)
	for k in range(1, n + 1):
		var t := float(k) / n
		var u := 1.0 - t
		_add(p0 * (u * u) + p1 * (2.0 * u * t) + p2 * (t * t))
	_cur_flags &= ~_F_CONVEX


func bezierCurveTo(c1x: float, c1y: float, c2x: float, c2y: float, x: float, y: float) -> void:
	if not _has_cur:
		moveTo(c1x, c1y)
	var p0 := _cur[_cur.size() - 1] if _cur.size() > 0 else _xf * Vector2(c1x, c1y)
	var p1 := _xf * Vector2(c1x, c1y)
	var p2 := _xf * Vector2(c2x, c2y)
	var p3 := _xf * Vector2(x, y)
	var dd := maxf((p0 - p1 * 2.0 + p2).length(), (p1 - p2 * 2.0 + p3).length())
	var n := clampi(ceili(sqrt(0.75 * dd / tolerance)), 1, 96)
	for k in range(1, n + 1):
		var t := float(k) / n
		var u := 1.0 - t
		_add(p0 * (u * u * u) + p1 * (3.0 * u * u * t) + p2 * (3.0 * u * t * t) + p3 * (t * t * t))
	_cur_flags &= ~_F_CONVEX


# =============================================================== emitting ==

func _emit(verts: PackedVector2Array, cols: PackedColorArray, idx: PackedInt32Array) -> void:
	if verts.is_empty() or idx.is_empty():
		return
	if measure_only:
		_grow_bounds(verts)
		return
	RenderingServer.canvas_item_add_triangle_array(_segment(), idx, verts, cols)


## Measure mode: grow `bounds` by every point of `polys`, plus a margin.
func _grow_pts(polys: Array, margin: float) -> void:
	var first := true
	var mn := Vector2.ZERO
	var mx := Vector2.ZERO
	for p in polys:
		for v in p:
			if first:
				mn = v
				mx = v
				first = false
			else:
				mn = mn.min(v)
				mx = mx.max(v)
	if first:
		return
	var off := Vector2(shadowOffsetX, shadowOffsetY) if shadows and _shadow_col.a > 0.0 else Vector2.ZERO
	var r := Rect2(mn - Vector2(margin, margin), mx - mn + Vector2(margin, margin) * 2.0)
	if off != Vector2.ZERO:
		r = r.merge(Rect2(r.position + off, r.size))
	if bounds_valid:
		bounds = bounds.merge(r)
	else:
		bounds = r
		bounds_valid = true


## How far a shadow reaches past the shape (device px), for measuring.
func _shadow_reach() -> float:
	if not shadows or _shadow_col.a <= 0.0 or shadowBlur <= 0.0:
		return 0.0
	return shadowBlur * 1.15


func _grow_bounds(verts: PackedVector2Array) -> void:
	var mn := verts[0]
	var mx := verts[0]
	for v in verts:
		mn = mn.min(v)
		mx = mx.max(v)
	var r := Rect2(mn, mx - mn)
	if bounds_valid:
		bounds = bounds.merge(r)
	else:
		bounds = r
		bounds_valid = true


## Colour of a paint with globalAlpha (and an extra factor) applied, for solid
## paints; null for gradients.
func _solid(paint, mult: float) -> Variant:
	if paint is Color:
		var c: Color = paint
		c.a *= globalAlpha * mult
		return c
	return null


func _grad_colors(g: CanvasGradient, verts: PackedVector2Array, mult: float) -> PackedColorArray:
	if not g.radial:
		return _lin_colors(g, verts, mult)[0]
	var inv := _xf.affine_inverse()
	var cols := PackedColorArray()
	cols.resize(verts.size())
	var am := globalAlpha * mult
	for i in verts.size():
		var c := g.at(g.t_at(inv * verts[i]))
		c.a *= am
		cols[i] = c
	return cols


## Colours of a linear gradient at device-space vertices, in one pass: t is an
## affine function of the device position (t = p·k + c), so no per-vertex
## inverse transform. Returns [colours, min t, max t].
func _lin_colors(g: CanvasGradient, verts: PackedVector2Array, mult: float) -> Array:
	var inv := _xf.affine_inverse()
	var d := Vector2(g.x1 - g.x0, g.y1 - g.y0)
	var dd := d.length_squared()
	var gk := Vector2.ZERO
	var gc := 0.0
	if dd > 0.0:
		gk = Vector2(inv.x.dot(d), inv.y.dot(d)) / dd
		gc = (inv.origin - Vector2(g.x0, g.y0)).dot(d) / dd
	var n := verts.size()
	var cols := PackedColorArray()
	cols.resize(n)
	var am := globalAlpha * mult
	var tmin := INF
	var tmax := -INF
	var simple := g.offsets.size() == 2 and g.offsets[0] == 0.0 and g.offsets[1] == 1.0
	var c0 := g.colors[0] if g.colors.size() > 0 else Color(0, 0, 0, 0)
	var c1 := g.colors[1] if g.colors.size() > 1 else c0
	for i in n:
		var t := verts[i].dot(gk) + gc
		tmin = minf(tmin, t)
		tmax = maxf(tmax, t)
		var c := c0.lerp(c1, clampf(t, 0.0, 1.0)) if simple else g.at(t)
		c.a *= am
		cols[i] = c
	return [cols, tmin, tmax]


## Whether a linear gradient over vertices spanning t in [tmin, tmax] needs its
## triangles split (a stop inside the span, or a real stretch of clamping).
static func _needs_split(g: CanvasGradient, tmin: float, tmax: float) -> bool:
	var n := g.offsets.size()
	if n == 0:
		return false
	var first := g.offsets[0]
	var last := g.offsets[n - 1]
	if tmin < first - 0.04 and tmax > first:
		return true
	if tmax > last + 0.04 and tmin < last:
		return true
	for i in range(1, n - 1):
		if g.offsets[i] > tmin and g.offsets[i] < tmax:
			return true
	return false


static func _fan(n: int) -> PackedInt32Array:
	var idx = _fan_cache.get(n)
	if idx != null:
		return idx
	idx = PackedInt32Array()
	idx.resize((n - 2) * 3)
	for i in n - 2:
		idx[i * 3] = 0
		idx[i * 3 + 1] = i + 1
		idx[i * 3 + 2] = i + 2
	_fan_cache[n] = idx
	return idx


## Indices of the AA fringe ring around a closed n-gon whose inner vertices are
## 0..n-1 and outer vertices n..2n-1.
static func _ring(n: int) -> PackedInt32Array:
	var idx = _ring_cache.get(n)
	if idx != null:
		return idx
	idx = PackedInt32Array()
	idx.resize(n * 6)
	for i in n:
		var j := (i + 1) % n
		idx[i * 6] = i
		idx[i * 6 + 1] = j
		idx[i * 6 + 2] = n + j
		idx[i * 6 + 3] = i
		idx[i * 6 + 4] = n + j
		idx[i * 6 + 5] = n + i
	_ring_cache[n] = idx
	return idx


# =================================================================== fill ==

## fill() / fill("nonzero") / fill("evenodd").
func fill(rule: String = "nonzero") -> void:
	var polys: Array = []
	var flags: Array = []
	var shape = null
	for i in _subs.size():
		var p: PackedVector2Array = _subs[i]
		if p.size() >= 3:
			polys.append(p)
			flags.append(_subs_flags[i])
			shape = _subs_shape[i]
	if _cur.size() >= 3:
		polys.append(_cur)
		flags.append(_cur_flags)
		shape = _cur_shape if _cur_flags & _F_CONVEX else null
	if polys.is_empty():
		return
	if measure_only:
		_grow_pts(polys, 0.5 + _shadow_reach())
		return
	# One whole ellipse, nothing unusual in force: build it from the unit circle.
	if polys.size() == 1 and shape != null and aa and _clip_poly.is_empty() and not (_fill_paint is CanvasGradient and _fill_paint.radial) \
			and not (shadows and _shadow_col.a > 0.0 and (shadowBlur > 0.0 or shadowOffsetX != 0.0 or shadowOffsetY != 0.0)):
		_fill_ellipse(shape, _fill_paint)
		return
	for i in polys.size():
		var p: PackedVector2Array = polys[i]
		if p[0].distance_squared_to(p[p.size() - 1]) < 1e-10:
			p = p.slice(0, p.size() - 1)
			polys[i] = p
	_fill_polys(polys, flags, _fill_paint, rule)


func _fill_polys(polys: Array, flags: Array, paint, rule: String) -> void:
	if not _clip_poly.is_empty():
		var cut: Array = []
		var cf: Array = []
		for p in polys:
			for q in Geometry2D.intersect_polygons(p, _clip_poly):
				cut.append(q)
				cf.append(0)
		polys = cut
		flags = cf
		if polys.is_empty():
			return
	if shadows and _shadow_col.a > 0.0 and (shadowBlur > 0.0 or shadowOffsetX != 0.0 or shadowOffsetY != 0.0):
		_shadow_polys(polys)
	var opaque: bool = paint is Color and paint.a >= 1.0 and globalAlpha >= 1.0
	if polys.size() == 1 or (opaque and rule == "nonzero" and _all_simple(flags)):
		var general: Array = []
		for i in polys.size():
			var p: PackedVector2Array = polys[i]
			var f: int = flags[i]
			if f & _F_COMPLEX:
				general.append(p)
				continue
			var idx: PackedInt32Array = _fan(p.size()) if f & _F_CONVEX else Geometry2D.triangulate_polygon(p)
			if idx.is_empty():
				general.append(p)
				continue
			_fill_simple(p, idx, paint)
		if not general.is_empty():
			_fill_general(general, paint, rule)
		return
	_fill_general(polys, paint, rule)


static func _all_simple(flags: Array) -> bool:
	for f in flags:
		if f & _F_COMPLEX:
			return false
	return true


## A simple polygon with its triangulation: shrink half a pixel, fringe
## half a pixel out.
func _fill_simple(p: PackedVector2Array, idx: PackedInt32Array, paint) -> void:
	var n := p.size()
	var radial: bool = paint is CanvasGradient and paint.radial
	if not aa:
		var c = _solid(paint, 1.0)
		if radial:
			var rf := _refine(p, idx, paint)
			p = rf[0]
			idx = rf[1]
		elif c == null:
			var rl := _refine_linear(p, idx, paint)
			p = rl[0]
			idx = rl[1]
		_emit(p, PackedColorArray([c]) if c != null else _grad_colors(paint, p, 1.0), idx)
		return
	var verts := _aa_outline(p)
	_fill_rings(verts, idx, n, paint)


## Fill a polygon given as `verts` = n inner (shrunk) points then n outer
## (grown) points, with `idx` triangulating the inner ones: the core in the
## paint, the fringe ring fading out.
func _fill_rings(verts: PackedVector2Array, idx: PackedInt32Array, n: int, paint) -> void:
	var c = _solid(paint, 1.0)
	if c != null:
		var all := idx.duplicate()
		all.append_array(_ring(n))
		_emit(verts, _two_tone(c, n, n), all)
		return
	var all_idx := idx.duplicate()
	var cols := PackedColorArray()
	if paint.radial:
		# A radial gradient varies inside triangles whose corners all sit on
		# the rim (a circle's fan): subdivide so the colour has somewhere to
		# change. New vertices go after the fringe ring's.
		var rf := _refine(verts, all_idx, paint)
		verts = rf[0]
		all_idx = rf[1]
		cols = _grad_colors(paint, verts, 1.0)
	else:
		var lc := _lin_colors(paint, verts, 1.0)
		cols = lc[0]
		if _needs_split(paint, lc[1], lc[2]):
			var rl := _refine_linear(verts, all_idx, paint)
			verts = rl[0]
			all_idx = rl[1]
			cols = _lin_colors(paint, verts, 1.0)[0]
	for i in range(n, 2 * n):
		cols[i].a = 0.0
	all_idx.append_array(_ring(n))
	_emit(verts, cols, all_idx)


## A whole ellipse: inner and outer rings straight from the unit circle.
func _fill_ellipse(sh: Array, paint) -> void:
	var xf: Transform2D = sh[0]
	var c: Vector2 = sh[2]
	var rx: float = sh[3]
	var ry: float = sh[4]
	var n: int = sh[5]
	var h: float = 0.5 / maxf(sh[8], 1e-6)
	var unit := _unit_ring(n, sh[6], sh[7])
	var verts: PackedVector2Array = (xf * Transform2D(sh[1], Vector2(maxf(rx - h, 0.0), maxf(ry - h, 0.0)), 0.0, c)) * unit
	verts.append_array((xf * Transform2D(sh[1], Vector2(rx + h, ry + h), 0.0, c)) * unit)
	_fill_rings(verts, _fan(n), n, paint)


## n points round the unit circle (a closed ring, no repeated end), starting
## at angle 0 — or, for an ellipse, at a0 (its start angle can't be folded
## into the rotation).
static func _unit_ring(n: int, a0: float, circle: bool) -> PackedVector2Array:
	return _ring_table(n, 0.0 if circle else a0)


static var _ring_tables := {}


static func _ring_table(n: int, a0: float) -> PackedVector2Array:
	var key := Vector2(n, a0)
	var pts = _ring_tables.get(key)
	if pts != null:
		return pts
	pts = PackedVector2Array()
	pts.resize(n)
	var f := _area_k(TAU / n)
	for k in n:
		var a := a0 + TAU * k / n
		pts[k] = Vector2(cos(a), sin(a)) * f
	if _ring_tables.size() > 2048:
		_ring_tables.clear()
	_ring_tables[key] = pts
	return pts


## Linear gradients are linear in position, so per-vertex colours are exact —
## except across a stop (a middle colour, or where the gradient clamps at its
## ends). Split the triangles along those iso-lines so every triangle lies
## between two neighbouring stops.
func _refine_linear(verts: PackedVector2Array, idx: PackedInt32Array, g: CanvasGradient) -> Array:
	var inv := _xf.affine_inverse()
	var nv := verts.size()
	var ts := PackedFloat64Array()
	ts.resize(nv)
	var tmin := INF
	var tmax := -INF
	for i in nv:
		var t := g.t_at(inv * verts[i])
		ts[i] = t
		tmin = minf(tmin, t)
		tmax = maxf(tmax, t)
	var cuts := PackedFloat64Array()
	for o in g.offsets:
		if o > tmin + 1e-6 and o < tmax - 1e-6 and (cuts.is_empty() or o > cuts[cuts.size() - 1] + 1e-6):
			cuts.append(o)
	if cuts.is_empty():
		return [verts, idx]
	var v := PackedVector2Array(verts)
	var cur := idx
	for tc in cuts:
		var nxt := PackedInt32Array()
		var mids := {}
		for k in range(0, cur.size(), 3):
			var tri := [cur[k], cur[k + 1], cur[k + 2]]
			var above := 0
			for q in tri:
				if ts[q] > tc:
					above += 1
			if above == 0 or above == 3:
				nxt.append_array(tri)
				continue
			# The vertex alone on its side of the line, and the other two.
			var lone := -1
			for j in 3:
				var is_above: bool = ts[tri[j]] > tc
				if (above == 1) == is_above:
					lone = j
			var a: int = tri[lone]
			var b: int = tri[(lone + 1) % 3]
			var c: int = tri[(lone + 2) % 3]
			var mab := _iso_mid(v, ts, mids, a, b, tc)
			var mac := _iso_mid(v, ts, mids, a, c, tc)
			nxt.append_array([a, mab, mac, mab, b, c, mab, c, mac])
		cur = nxt
	return [v, cur]


static func _iso_mid(v: PackedVector2Array, ts: PackedFloat64Array, mids: Dictionary, a: int, b: int, tc: float) -> int:
	var key := mini(a, b) * 1048576 + maxi(a, b)
	var m: int = mids.get(key, -1)
	if m >= 0:
		return m
	var ta := ts[a]
	var tb := ts[b]
	var f := (tc - ta) / (tb - ta) if tb != ta else 0.5
	m = v.size()
	v.append(v[a].lerp(v[b], f))
	ts.append(tc)
	mids[key] = m
	return m


## Split triangles (longest edge first) until every edge is short next to the
## radial gradient's span. Midpoints are shared between neighbours.
func _refine(verts: PackedVector2Array, idx: PackedInt32Array, g: CanvasGradient) -> Array:
	var lim := maxf(2.5, absf(g.r1 - g.r0) * _scale * 0.08)
	var lim2 := lim * lim
	var v := PackedVector2Array(verts)
	var out := PackedInt32Array()
	var mids := {}
	var stack: Array = []
	for i in range(0, idx.size(), 3):
		stack.append(Vector3i(idx[i], idx[i + 1], idx[i + 2]))
	var guard := 0
	while not stack.is_empty():
		var t: Vector3i = stack.pop_back()
		var a := v[t.x]
		var b := v[t.y]
		var c := v[t.z]
		var lab := a.distance_squared_to(b)
		var lbc := b.distance_squared_to(c)
		var lca := c.distance_squared_to(a)
		var mx := maxf(lab, maxf(lbc, lca))
		guard += 1
		if mx <= lim2 or guard > 20000:
			out.append_array([t.x, t.y, t.z])
			continue
		# Rotate so the longest edge is (p, q) and r is opposite.
		var p := t.x
		var q := t.y
		var r := t.z
		if mx == lbc:
			p = t.y; q = t.z; r = t.x
		elif mx == lca:
			p = t.z; q = t.x; r = t.y
		var key := mini(p, q) * 1048576 + maxi(p, q)
		var m: int = mids.get(key, -1)
		if m < 0:
			m = v.size()
			v.append((v[p] + v[q]) * 0.5)
			mids[key] = m
		stack.append(Vector3i(p, m, r))
		stack.append(Vector3i(m, q, r))
	return [v, out]


static func _two_tone(c: Color, n_in: int, n_out: int) -> PackedColorArray:
	var cols := PackedColorArray()
	cols.resize(n_in)
	cols.fill(c)
	var o := PackedColorArray()
	o.resize(n_out)
	c.a = 0.0
	o.fill(c)
	cols.append_array(o)
	return cols


## Inner (shrunk 0.5px) then outer (grown 0.5px) copies of a closed polygon.
static func _aa_outline(p: PackedVector2Array) -> PackedVector2Array:
	var n := p.size()
	# Orientation: outward normal is (d.y, -d.x) when the shoelace sum is positive.
	var area := 0.0
	var prev := p[n - 1]
	for q in p:
		area += prev.x * q.y - q.x * prev.y
		prev = q
	var sgn := 0.5 if area > 0.0 else -0.5
	var nrm := PackedVector2Array()
	nrm.resize(n)
	for i in n:
		var d := p[(i + 1) % n] - p[i]
		var l := d.length()
		nrm[i] = Vector2(d.y, -d.x) * (sgn / l) if l > 1e-9 else Vector2.ZERO
	var out := PackedVector2Array()
	out.resize(2 * n)
	var pn := nrm[n - 1]
	for i in n:
		var cn := nrm[i]
		var m := pn + cn
		# pn, cn have length 0.5: the miter offset of length 0.5/cos is
		# (pn + cn) / (1 + û0·û1) where û = 2·n.
		var den := 1.0 + 4.0 * pn.dot(cn)
		if den > 0.1:
			m = m / den
		else:
			m = m.normalized() * 1.0
		if m.length_squared() > 4.0:
			m = m.normalized() * 2.0
		out[i] = p[i] - m
		out[n + i] = p[i] + m
		pn = cn
	return out


## Nonzero (or even-odd) fill of any set of polygons: scanline trapezoids,
## plus an outward fringe on every outline.
func _fill_general(polys: Array, paint, rule: String) -> void:
	var tri := _tessellate(polys, rule == "evenodd")
	var verts: PackedVector2Array = tri[0]
	var idx: PackedInt32Array = tri[1]
	var c = _solid(paint, 1.0)
	if c == null and not verts.is_empty():
		var rf := _refine(verts, idx, paint) if paint.radial else _refine_linear(verts, idx, paint)
		verts = rf[0]
		idx = rf[1]
	if not verts.is_empty():
		_emit(verts, PackedColorArray([c]) if c != null else _grad_colors(paint, verts, 1.0), idx)
	if not aa:
		return
	# Fringe: outer half only (the interior isn't shrunk here).
	for p in polys:
		var n: int = p.size()
		var ring := _aa_outline(p)
		var fv := PackedVector2Array(p)
		fv.append_array(ring.slice(n))
		var cols: PackedColorArray
		if c != null:
			cols = _two_tone(c, n, n)
		else:
			cols = _grad_colors(paint, fv, 1.0)
			for i in range(n, 2 * n):
				cols[i].a = 0.0
		_emit(fv, cols, _ring(n))


## Scanline tessellation honouring the fill rule. Returns [verts, indices].
static func _tessellate(polys: Array, evenodd: bool) -> Array:
	var ex0 := PackedFloat64Array()
	var ey0 := PackedFloat64Array()
	var ex1 := PackedFloat64Array()
	var ey1 := PackedFloat64Array()
	var ew := PackedInt32Array()
	var ys := PackedFloat64Array()
	for p in polys:
		var n: int = p.size()
		for i in n:
			var a: Vector2 = p[i]
			var b: Vector2 = p[(i + 1) % n]
			ys.append(a.y)
			if absf(a.y - b.y) < 1e-9:
				continue
			if a.y < b.y:
				ex0.append(a.x); ey0.append(a.y); ex1.append(b.x); ey1.append(b.y); ew.append(1)
			else:
				ex0.append(b.x); ey0.append(b.y); ex1.append(a.x); ey1.append(a.y); ew.append(-1)
	var m := ew.size()
	# Edge crossings become band boundaries too.
	for i in m:
		for j in range(i + 1, m):
			if ey1[i] <= ey0[j] or ey1[j] <= ey0[i]:
				continue
			var p1 := Vector2(ex0[i], ey0[i])
			var r := Vector2(ex1[i] - ex0[i], ey1[i] - ey0[i])
			var q1 := Vector2(ex0[j], ey0[j])
			var s := Vector2(ex1[j] - ex0[j], ey1[j] - ey0[j])
			var den := r.cross(s)
			if absf(den) < 1e-12:
				continue
			var t := (q1 - p1).cross(s) / den
			var u := (q1 - p1).cross(r) / den
			if t > 1e-9 and t < 1.0 - 1e-9 and u > 1e-9 and u < 1.0 - 1e-9:
				ys.append(p1.y + r.y * t)
	ys.sort()
	var verts := PackedVector2Array()
	var idx := PackedInt32Array()
	var prev_y := -INF
	var uniq := PackedFloat64Array()
	for y in ys:
		if y - prev_y > 1e-7:
			uniq.append(y)
			prev_y = y
	for bi in range(uniq.size() - 1):
		var ya := uniq[bi]
		var yb := uniq[bi + 1]
		var ym := (ya + yb) * 0.5
		var act: Array = []
		for e in m:
			if ey0[e] <= ym and ey1[e] >= ym:
				var k := (ex1[e] - ex0[e]) / (ey1[e] - ey0[e])
				var xa := ex0[e] + (ya - ey0[e]) * k
				var xb := ex0[e] + (yb - ey0[e]) * k
				act.append([xa + xb, xa, xb, ew[e]])
		if act.size() < 2:
			continue
		act.sort_custom(func(p, q): return p[0] < q[0])
		var wind := 0
		var sxa := 0.0
		var sxb := 0.0
		for a in act:
			var before := wind
			wind += a[3]
			var inside_before := (before & 1) != 0 if evenodd else before != 0
			var inside_after := (wind & 1) != 0 if evenodd else wind != 0
			if not inside_before and inside_after:
				sxa = a[1]
				sxb = a[2]
			elif inside_before and not inside_after:
				var base := verts.size()
				verts.append(Vector2(sxa, ya))
				verts.append(Vector2(a[1], ya))
				verts.append(Vector2(a[2], yb))
				verts.append(Vector2(sxb, yb))
				idx.append_array([base, base + 1, base + 2, base, base + 2, base + 3])
	return [verts, idx]


# ================================================================= stroke ==

func stroke() -> void:
	var lines: Array = []
	var closed: Array = []
	var shapes: Array = []
	for i in _subs.size():
		var p: PackedVector2Array = _subs[i]
		if p.size() >= 2:
			lines.append(p)
			closed.append(_subs_closed[i])
			shapes.append(_subs_shape[i])
	if _cur.size() >= 2:
		lines.append(_cur)
		closed.append(false)
		shapes.append(_cur_shape if _cur_flags & _F_CONVEX else null)
	if lines.is_empty():
		return
	if measure_only:
		# Half-width out from the centre line, with room for a miter tip.
		_grow_pts(lines, lineWidth * _scale + 0.5 + _shadow_reach())
		return
	var w := lineWidth * _scale
	# Whole ellipses with nothing unusual in force: rings from the unit circle.
	if aa and _dash.is_empty() and _clip_poly.is_empty() and not (shadows and _shadow_col.a > 0.0 and (shadowBlur > 0.0 or shadowOffsetX != 0.0 or shadowOffsetY != 0.0)) \
			and not (_stroke_paint is CanvasGradient and _stroke_paint.radial):
		var rest: Array = []
		var rest_closed: Array = []
		for i in lines.size():
			if shapes[i] != null:
				_stroke_ellipse(shapes[i], _stroke_paint, w)
			else:
				rest.append(lines[i])
				rest_closed.append(closed[i])
		if not rest.is_empty():
			_stroke_lines(rest, rest_closed, _stroke_paint, w)
		return
	_stroke_lines(lines, closed, _stroke_paint, w)


## Stroke a whole ellipse: four rings (core out/in, fringe out/in) from the
## unit circle, indexed by a cached template.
func _stroke_ellipse(sh: Array, paint, w: float) -> void:
	var alpha_mult := 1.0
	var hw := w * 0.5
	if w < 1.0:
		alpha_mult = w
		hw = 0.5
	var s: float = maxf(sh[8], 1e-6)
	var hc := (hw - 0.5) / s
	var hf := (hw + 0.5) / s
	var xf: Transform2D = sh[0]
	var rot: float = sh[1]
	var c: Vector2 = sh[2]
	var rx: float = sh[3]
	var ry: float = sh[4]
	var n: int = sh[5]
	var unit := _unit_ring(n, sh[6], sh[7])
	var verts: PackedVector2Array = (xf * Transform2D(rot, Vector2(rx + hc, ry + hc), 0.0, c)) * unit
	verts.append_array((xf * Transform2D(rot, Vector2(maxf(rx - hc, 0.0), maxf(ry - hc, 0.0)), 0.0, c)) * unit)
	verts.append_array((xf * Transform2D(rot, Vector2(rx + hf, ry + hf), 0.0, c)) * unit)
	verts.append_array((xf * Transform2D(rot, Vector2(maxf(rx - hf, 0.0), maxf(ry - hf, 0.0)), 0.0, c)) * unit)
	var cols: PackedColorArray
	var sc = _solid(paint, alpha_mult)
	if sc != null:
		cols = _two_tone(sc, 2 * n, 2 * n)
	else:
		cols = _grad_colors(paint, verts, alpha_mult)
		for i in range(2 * n, 4 * n):
			cols[i].a = 0.0
	_emit(verts, cols, _ring_strip(n))


static var _ring_strip_cache := {}


## Indices for four rings of n points laid out ring after ring: the core
## between rings 0 and 1, the fringes between 2–0 and 1–3.
static func _ring_strip(n: int) -> PackedInt32Array:
	var idx = _ring_strip_cache.get(n)
	if idx != null:
		return idx
	idx = PackedInt32Array()
	for i in n:
		var j := (i + 1) % n
		idx.append_array([i, n + i, n + j, i, n + j, j])
		idx.append_array([2 * n + i, i, j, 2 * n + i, j, 2 * n + j])
		idx.append_array([n + i, 3 * n + i, 3 * n + j, n + i, 3 * n + j, n + j])
	_ring_strip_cache[n] = idx
	return idx


func _stroke_lines(lines: Array, closed: Array, paint, w: float) -> void:
	if not _dash.is_empty():
		var dl := _apply_dash(lines, closed)
		lines = dl
		closed = []
		closed.resize(lines.size())
		closed.fill(false)
	if not _clip_poly.is_empty():
		var cut: Array = []
		for i in lines.size():
			var p: PackedVector2Array = lines[i]
			if closed[i]:
				p = PackedVector2Array(p)
				p.append(p[0])
			for q in Geometry2D.intersect_polyline_with_polygon(p, _clip_poly):
				cut.append(q)
		lines = cut
		closed = []
		closed.resize(lines.size())
		closed.fill(false)
	if shadows and _shadow_col.a > 0.0 and (shadowBlur > 0.0 or shadowOffsetX != 0.0 or shadowOffsetY != 0.0):
		_shadow_lines(lines, closed, w)
	for i in lines.size():
		_stroke_line(lines[i], closed[i], paint, w, lineCap, lineJoin)


## One polyline as a strip of cross-sections. Each section is a centre and two
## offset directions (left, right); the core edge sits at half-width − ½px and
## the fringe at half-width + ½px, so the stroke is coverage-antialiased.
func _stroke_line(src: PackedVector2Array, closed: bool, paint, w: float, cap: String, join: String) -> void:
	var p := src
	var n := p.size()
	if closed and n > 2 and p[0].distance_squared_to(p[n - 1]) < 1e-10:
		p = p.slice(0, n - 1)
		n -= 1
	if n < 2:
		return
	var alpha_mult := 1.0
	var hw := w * 0.5
	var hc := hw
	var hf := hw
	if aa:
		if w < 1.0:
			alpha_mult = w
			hw = 0.5
		hc = hw - 0.5
		hf = hw + 0.5
	# Segment directions and left normals.
	var segs := n if closed else n - 1
	var dirs := PackedVector2Array()
	dirs.resize(segs)
	for i in segs:
		var d := p[(i + 1) % n] - p[i]
		var l := d.length()
		dirs[i] = d / l if l > 1e-9 else (dirs[i - 1] if i > 0 else Vector2.RIGHT)
	var C := PackedVector2Array()
	var OL := PackedVector2Array()
	var OR := PackedVector2Array()
	var A := PackedFloat32Array()  # per-section alpha factor (0 for cap fringes)
	var arc_tol := tolerance
	# Start cap.
	if not closed:
		var d0 := dirs[0]
		var n0 := Vector2(-d0.y, d0.x)
		match cap:
			"round":
				var k := _cap_steps(hf, arc_tol)
				var kf := _area_k(PI * 0.5 / k)
				for s in k + 1:
					var phi := PI * 0.5 * (1.0 - float(s) / k)
					var cs := cos(phi)
					var sn := sin(phi)
					var g := kf if s < k else 1.0
					C.append(p[0]); OL.append((n0 * cs - d0 * sn) * g); OR.append((-n0 * cs - d0 * sn) * g); A.append(1.0)
			"square":
				var c0 := p[0] - d0 * hw
				if aa:
					C.append(c0 - d0); OL.append(n0); OR.append(-n0); A.append(0.0)
				C.append(c0); OL.append(n0); OR.append(-n0); A.append(1.0)
			_:
				# Butt: the end fringe straddles the true end (±½px), as the
				# canvas's coverage does; skipped on segments too short for it.
				if aa:
					var inset := 0.5 if p[0].distance_to(p[1]) > 1.5 else 0.0
					C.append(p[0] - d0 * 0.5); OL.append(n0); OR.append(-n0); A.append(0.0)
					C.append(p[0] + d0 * inset); OL.append(n0); OR.append(-n0); A.append(1.0)
				else:
					C.append(p[0]); OL.append(n0); OR.append(-n0); A.append(1.0)
	# Joins.
	var first := 0 if closed else 1
	var last := n if closed else n - 1
	for i in range(first, last):
		var din := dirs[(i - 1 + segs) % segs]
		var dout := dirs[i % segs]
		var nin := Vector2(-din.y, din.x)
		var nout := Vector2(-dout.y, dout.x)
		var dot := nin.dot(nout)
		var pc := p[i % n]
		if dot > 0.9995:
			var mm := (nin + nout) / (1.0 + dot)
			C.append(pc); OL.append(mm); OR.append(-mm); A.append(1.0)
			continue
		var m := (nin + nout) / maxf(1.0 + dot, 1e-6)
		var mlen := m.length()
		# Which side is outside the turn.
		var outer_left := (nin + nout).dot(dout - din) < 0.0
		# Inner side: the miter point, limited so it can't overshoot short segments.
		var lim := 1e9
		var lin := p[i % n].distance_to(p[(i - 1 + n) % n])
		var lout := p[i % n].distance_to(p[(i + 1) % n])
		if hf > 0.0:
			lim = minf(lin, lout) / hf
		var mi := m if mlen <= lim else m * (lim / mlen)
		if join == "miter" and mlen <= miterLimit:
			C.append(pc); OL.append(m); OR.append(-m); A.append(1.0)
			continue
		if join == "round":
			var ang := acos(clampf(dot, -1.0, 1.0))
			var k := maxi(1, ceili(ang / (2.0 * acos(clampf(1.0 - arc_tol / maxf(hf, 0.01), -1.0, 1.0)))))
			var kf := _area_k(ang / k)
			for s in k + 1:
				var t := float(s) / k
				var g := kf if s > 0 and s < k else 1.0
				if outer_left:
					var o := nin.slerp(nout, t) * g
					C.append(pc); OL.append(o); OR.append(-mi); A.append(1.0)
				else:
					var o := (-nin).slerp(-nout, t) * g
					C.append(pc); OL.append(mi); OR.append(o); A.append(1.0)
		else:  # bevel (or a miter past its limit)
			if outer_left:
				C.append(pc); OL.append(nin); OR.append(-mi); A.append(1.0)
				C.append(pc); OL.append(nout); OR.append(-mi); A.append(1.0)
			else:
				C.append(pc); OL.append(mi); OR.append(-nin); A.append(1.0)
				C.append(pc); OL.append(mi); OR.append(-nout); A.append(1.0)
	# End cap.
	if not closed:
		var de := dirs[segs - 1]
		var ne := Vector2(-de.y, de.x)
		var pe := p[n - 1]
		match cap:
			"round":
				var k := _cap_steps(hf, arc_tol)
				var kf := _area_k(PI * 0.5 / k)
				for s in k + 1:
					var phi := PI * 0.5 * float(s) / k
					var cs := cos(phi)
					var sn := sin(phi)
					var g := kf if s > 0 else 1.0
					C.append(pe); OL.append((ne * cs + de * sn) * g); OR.append((-ne * cs + de * sn) * g); A.append(1.0)
			"square":
				var ce := pe + de * hw
				C.append(ce); OL.append(ne); OR.append(-ne); A.append(1.0)
				if aa:
					C.append(ce + de); OL.append(ne); OR.append(-ne); A.append(0.0)
			_:
				if aa:
					var inset := 0.5 if pe.distance_to(p[n - 2]) > 1.5 else 0.0
					C.append(pe - de * inset); OL.append(ne); OR.append(-ne); A.append(1.0)
					C.append(pe + de * 0.5); OL.append(ne); OR.append(-ne); A.append(0.0)
				else:
					C.append(pe); OL.append(ne); OR.append(-ne); A.append(1.0)
	_emit_strip(C, OL, OR, A, closed, hc, hf, paint, alpha_mult)


static func _cap_steps(r: float, tol: float) -> int:
	if r <= tol:
		return 1
	return clampi(ceili(PI * 0.5 / (2.0 * acos(1.0 - tol / r))), 1, 32)


func _emit_strip(C: PackedVector2Array, OL: PackedVector2Array, OR: PackedVector2Array, A: PackedFloat32Array, closed: bool, hc: float, hf: float, paint, alpha_mult: float) -> void:
	var m := C.size()
	if m < 2:
		return
	var per := 4 if aa else 2
	var verts := PackedVector2Array()
	verts.resize(m * per)
	for j in m:
		var c := C[j]
		var l := OL[j]
		var r := OR[j]
		verts[j * per] = c + l * hc
		verts[j * per + 1] = c + r * hc
		if aa:
			verts[j * per + 2] = c + l * hf
			verts[j * per + 3] = c + r * hf
	var idx := _strip_indices(m, closed, aa)
	var cols: PackedColorArray
	var sc = _solid(paint, alpha_mult)
	if sc != null:
		if aa:
			var c0: Color = sc
			c0.a = 0.0
			var pat := PackedColorArray([sc, sc, c0, c0])
			cols = pat
			while cols.size() < m * 4:
				cols.append_array(cols)
			cols.resize(m * 4)
			for j in m:
				if A[j] == 0.0:
					cols[j * 4] = c0
					cols[j * 4 + 1] = c0
		else:
			cols = PackedColorArray([sc])
	else:
		cols = _grad_colors(paint, verts, alpha_mult)
		if aa:
			for j in m:
				cols[j * 4 + 2].a = 0.0
				cols[j * 4 + 3].a = 0.0
				if A[j] == 0.0:
					cols[j * 4].a = 0.0
					cols[j * 4 + 1].a = 0.0
	_emit(verts, cols, idx)


static func _strip_indices(m: int, closed: bool, with_aa: bool) -> PackedInt32Array:
	var key := m * 4 + (2 if closed else 0) + (1 if with_aa else 0)
	var idx = _strip_cache.get(key)
	if idx != null:
		return idx
	idx = PackedInt32Array()
	var per := 4 if with_aa else 2
	var pairs := m if closed else m - 1
	for j in pairs:
		var a := j * per
		var b := ((j + 1) % m) * per
		# core: Lc a, Rc a+1
		idx.append_array([a, a + 1, b + 1, a, b + 1, b])
		if with_aa:
			# left fringe: Lf a+2 .. Lc a
			idx.append_array([a + 2, a, b, a + 2, b, b + 2])
			# right fringe: Rc a+1 .. Rf a+3
			idx.append_array([a + 1, a + 3, b + 3, a + 1, b + 3, b + 1])
	if _strip_cache.size() > 4096:
		_strip_cache.clear()
	_strip_cache[key] = idx
	return idx


func setLineDash(segments: Array) -> void:
	var d := PackedFloat64Array()
	for s in segments:
		var v := float(s)
		if v < 0.0 or is_nan(v) or is_inf(v):
			return
		d.append(v)
	if d.size() % 2 == 1:
		d.append_array(d.duplicate())
	var total := 0.0
	for v in d:
		total += v
	_dash = d if total > 0.0 else PackedFloat64Array()


func getLineDash() -> Array:
	return Array(_dash)


## Split polylines into dashes (pattern lengths are user units).
func _apply_dash(lines: Array, closed: Array) -> Array:
	var pat := PackedFloat64Array()
	var total := 0.0
	for v in _dash:
		pat.append(v * _scale)
		total += v * _scale
	var out: Array = []
	for li in lines.size():
		var p: PackedVector2Array = lines[li]
		if closed[li]:
			p = PackedVector2Array(p)
			p.append(p[0])
		# Phase from lineDashOffset.
		var off := fposmod(lineDashOffset * _scale, total)
		var di := 0
		while off >= pat[di]:
			off -= pat[di]
			di = (di + 1) % pat.size()
		var left := pat[di] - off
		var on := di % 2 == 0
		var cur := PackedVector2Array()
		if on:
			cur.append(p[0])
		for i in range(p.size() - 1):
			var a := p[i]
			var b := p[i + 1]
			var seg := a.distance_to(b)
			var pos := 0.0
			while seg - pos > left:
				pos += left
				var q := a.lerp(b, pos / seg)
				if on:
					cur.append(q)
					if cur.size() >= 2:
						out.append(cur)
					cur = PackedVector2Array()
				else:
					cur = PackedVector2Array([q])
				on = not on
				di = (di + 1) % pat.size()
				left = pat[di]
			left -= seg - pos
			if on:
				cur.append(b)
		if on and cur.size() >= 2:
			out.append(cur)
	return out


# ============================================================ rectangles ==

func fillRect(x: float, y: float, w: float, h: float) -> void:
	if w == 0.0 or h == 0.0:
		return
	var p := PackedVector2Array([_xf * Vector2(x, y), _xf * Vector2(x + w, y), _xf * Vector2(x + w, y + h), _xf * Vector2(x, y + h)])
	if measure_only:
		_grow_pts([p], 0.5 + _shadow_reach())
		return
	if not _clip_poly.is_empty() or (shadows and _shadow_col.a > 0.0 and (shadowBlur > 0.0 or shadowOffsetX != 0.0 or shadowOffsetY != 0.0)):
		_fill_polys([p], [_F_CONVEX], _fill_paint, "nonzero")
		return
	_fill_simple(p, _fan(4), _fill_paint)


func strokeRect(x: float, y: float, w: float, h: float) -> void:
	var p := PackedVector2Array([_xf * Vector2(x, y), _xf * Vector2(x + w, y), _xf * Vector2(x + w, y + h), _xf * Vector2(x, y + h)])
	if measure_only:
		_grow_pts([p], lineWidth * _scale + 0.5)
		return
	_stroke_lines([p], [true], _stroke_paint, lineWidth * _scale)


## Canvas clearRect makes pixels transparent. Over a Godot canvas the closest
## thing is drawing nothing there; when the rect covers the whole target (the
## common use, clearing a frame) begin() already did that, so this is a no-op
## except in "destination-out"-capable layers, where it erases.
func clearRect(x: float, y: float, w: float, h: float) -> void:
	if globalCompositeOperation == "destination-out":
		var keep = _fill_paint
		_fill_paint = Color.WHITE
		fillRect(x, y, w, h)
		_fill_paint = keep


# ================================================================== clip ==

## Clip to the current path (intersected with any clip in force).
func clip(_rule: String = "nonzero") -> void:
	var polys: Array = []
	for i in _subs.size():
		if (_subs[i] as PackedVector2Array).size() >= 3:
			polys.append(_subs[i])
	if _cur.size() >= 3:
		polys.append(_cur)
	var r := Rect2()
	var poly := PackedVector2Array()
	if polys.is_empty():
		r = Rect2(0, 0, 0, 0)
	else:
		var first := true
		for p in polys:
			for v in p:
				if first:
					r = Rect2(v, Vector2.ZERO)
					first = false
				else:
					r = r.expand(v)
		if polys.size() == 1 and not _is_rect(polys[0], r):
			poly = polys[0]
	if _clip_on:
		var both := _clip_rect.intersection(r)
		if not poly.is_empty() or not _clip_poly.is_empty():
			var a := poly if not poly.is_empty() else _rect_poly(r)
			var b := _clip_poly if not _clip_poly.is_empty() else _rect_poly(_clip_rect)
			var inter := Geometry2D.intersect_polygons(a, b)
			poly = inter[0] if inter.size() > 0 else PackedVector2Array([Vector2.ZERO, Vector2.ZERO, Vector2.ZERO])
		r = both
	_clip_on = true
	_clip_rect = r
	_clip_poly = poly
	_seg_dirty = true


static func _is_rect(p: PackedVector2Array, r: Rect2) -> bool:
	if p.size() != 4:
		return false
	for v in p:
		var on_x := absf(v.x - r.position.x) < 1e-4 or absf(v.x - r.end.x) < 1e-4
		var on_y := absf(v.y - r.position.y) < 1e-4 or absf(v.y - r.end.y) < 1e-4
		if not (on_x and on_y):
			return false
	return true


static func _rect_poly(r: Rect2) -> PackedVector2Array:
	return PackedVector2Array([r.position, Vector2(r.end.x, r.position.y), r.end, Vector2(r.position.x, r.end.y)])


# ================================================================ shadows ==

# A Gaussian shadow (sigma = blur / 2) approximated by a few translucent
# copies dilated by increasing amounts, offset in device space as the canvas
# does (shadow offsets and blur ignore the transform).
## `e1`, `e2`: half the shape's smaller and larger dimension (device px).
func _shadow_layers(e1: float, e2: float) -> Array:
	var sigma := shadowBlur * 0.5
	if sigma <= 0.01:
		return [[0.0, 1.0]]
	# Layers grown (and shrunk) by evenly spaced distances d, drawn outermost
	# first; each one's alpha is solved so the composited coverage at distance
	# x from the edge follows the blurred edge, I(x) = ½·erfc(x / σ√2):
	# 0.5 on the edge, ~0.16 one sigma out, ~0.84 one sigma in.
	var n := clampi(roundi(4.5 * sigma / 2.5), 3, 9)
	var step := 4.5 * sigma / n
	# Small shapes spread thin: a blurred a×b box peaks at
	# erf(a/σ√2)·erf(b/σ√2), so a dot doesn't get a full-strength halo.
	var small := (1.0 - _erfc(e1 / (sigma * 1.41421356))) * (1.0 - _erfc(e2 / (sigma * 1.41421356)))
	small = clampf(small, 0.05, 1.0)
	var out: Array = []
	var prev := 0.0
	for k in n:
		var d := 2.25 * sigma - step * (k + 0.5)
		var target := 0.5 * _erfc((d - step * 0.5) / (sigma * 1.41421356))
		var a := (target - prev) / maxf(1e-6, 1.0 - prev)
		prev = target
		out.append([d, clampf(a, 0.0, 1.0) * small])
	return out


static func _erfc(x: float) -> float:
	# Abramowitz–Stegun 7.1.26 (|error| < 1.5e-7).
	var z := absf(x)
	var t := 1.0 / (1.0 + 0.3275911 * z)
	var y := t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
	var r := y * exp(-z * z)
	return r if x >= 0.0 else 2.0 - r


func _shadow_polys(polys: Array) -> void:
	var off := Transform2D(0.0, Vector2(shadowOffsetX, shadowOffsetY))
	var e1 := 0.0
	var e2 := 0.0
	for p in polys:
		var r := Rect2(p[0], Vector2.ZERO)
		for v in p:
			r = r.expand(v)
		e1 = maxf(e1, minf(r.size.x, r.size.y) * 0.5)
		e2 = maxf(e2, maxf(r.size.x, r.size.y) * 0.5)
	var keep_poly := _clip_poly
	var keep_shadow := _shadow_col
	_clip_poly = PackedVector2Array()
	_shadow_col = Color(0, 0, 0, 0)
	for layer in _shadow_layers(e1, e2):
		var d: float = layer[0]
		var col := keep_shadow
		col.a *= layer[1]
		for p in polys:
			var moved: PackedVector2Array = off * p
			var grown: Array = [moved] if absf(d) < 0.05 else Geometry2D.offset_polygon(moved, d, Geometry2D.JOIN_ROUND)
			for g in grown:
				if Geometry2D.is_polygon_clockwise(g) and grown.size() > 1:
					continue  # a hole from the offset; skip
				var idx := Geometry2D.triangulate_polygon(g)
				if not idx.is_empty():
					_fill_simple(g, idx, col)
	_clip_poly = keep_poly
	_shadow_col = keep_shadow


func _shadow_lines(lines: Array, closed: Array, w: float) -> void:
	var off := Transform2D(0.0, Vector2(shadowOffsetX, shadowOffsetY))
	var keep_shadow := _shadow_col
	_shadow_col = Color(0, 0, 0, 0)
	var length := 0.0
	for l in lines:
		var pl: PackedVector2Array = l
		for i in range(pl.size() - 1):
			length += pl[i].distance_to(pl[i + 1])
	for layer in _shadow_layers(w * 0.5, maxf(w * 0.5, length * 0.5)):
		var d: float = layer[0]
		if w + d * 2.0 <= 0.05:
			continue
		var col := keep_shadow
		col.a *= layer[1]
		for i in lines.size():
			_stroke_line(off * (lines[i] as PackedVector2Array), closed[i], col, w + d * 2.0, "round" if d > 0.0 else lineCap, "round" if d > 0.0 else lineJoin)
	_shadow_col = keep_shadow


# =================================================================== text ==

func _text_frame(text: String, x: float, y: float, max_width: float) -> Array:
	var spec := _font_spec
	var px: float = spec[0]
	var f := font_for(spec)
	var fs := clampi(roundi(px * _scale), 1, 1024)
	var k := px / fs
	var w := f.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
	var sx := 1.0
	if max_width > 0.0 and w * k > max_width:
		sx = max_width / (w * k)
	var ox := 0.0
	match textAlign:
		"center":
			ox = -w * 0.5
		"right":
			ox = -w
		"end":
			ox = -w if direction != "rtl" else 0.0
		"start":
			ox = 0.0 if direction != "rtl" else -w
	var asc := f.get_ascent(fs)
	var desc := f.get_descent(fs)
	var oy := 0.0
	match textBaseline:
		"middle":
			oy = (asc - desc) * 0.5
		"top", "hanging":
			oy = asc
		"bottom", "ideographic":
			oy = -desc
	var F := _xf.translated_local(Vector2(x, y)).scaled_local(Vector2(k * sx, k))
	return [f, fs, F, Vector2(ox, oy)]


func _text_color(paint) -> Color:
	var c: Color = paint if paint is Color else (paint as CanvasGradient).first_color()
	c.a *= globalAlpha
	return c


func fillText(text: String, x: float, y: float, max_width: float = -1.0) -> void:
	_draw_text(text, x, y, max_width, false)


func strokeText(text: String, x: float, y: float, max_width: float = -1.0) -> void:
	_draw_text(text, x, y, max_width, true)


func _draw_text(text: String, x: float, y: float, max_width: float, outline: bool) -> void:
	if text.is_empty():
		return
	var fr := _text_frame(text, x, y, max_width)
	var f: Font = fr[0]
	var fs: int = fr[1]
	var F: Transform2D = fr[2]
	var pos: Vector2 = fr[3]
	var col := _text_color(_stroke_paint if outline else _fill_paint)
	if measure_only:
		var w := f.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
		var asc := f.get_ascent(fs)
		_grow_bounds(F * PackedVector2Array([pos + Vector2(0, -asc), pos + Vector2(w, f.get_descent(fs))]))
		return
	var seg := _segment()
	var outline_px := maxi(1, roundi(lineWidth * _scale))
	var plain := absf(F.x.x - 1.0) < 1e-3 and absf(F.y.y - 1.0) < 1e-3 and absf(F.x.y) < 1e-6 and absf(F.y.x) < 1e-6
	if plain:
		var at := F.origin + pos
		if shadows and _shadow_col.a > 0.0 and (shadowBlur > 0.0 or shadowOffsetX != 0.0 or shadowOffsetY != 0.0):
			var sc := _shadow_col
			sc.a *= globalAlpha * (0.6 if shadowBlur > 0.0 else 1.0)
			f.draw_string(seg, at + Vector2(shadowOffsetX, shadowOffsetY), text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, sc)
		if outline:
			f.draw_string_outline(seg, at, text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, outline_px, col)
		else:
			f.draw_string(seg, at, text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, col)
		return
	RenderingServer.canvas_item_add_set_transform(seg, F)
	if outline:
		f.draw_string_outline(seg, pos, text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, outline_px, col)
	else:
		f.draw_string(seg, pos, text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, col)
	RenderingServer.canvas_item_add_set_transform(seg, Transform2D.IDENTITY)


func measureText(text: String) -> TextMetrics:
	var spec := _font_spec
	var px: float = spec[0]
	var f := font_for(spec)
	var fs := clampi(roundi(px * _scale), 1, 1024)
	var k := px / fs
	var m := TextMetrics.new()
	m.width = f.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x * k
	m.fontBoundingBoxAscent = f.get_ascent(fs) * k
	m.fontBoundingBoxDescent = f.get_descent(fs) * k
	m.actualBoundingBoxAscent = m.fontBoundingBoxAscent
	m.actualBoundingBoxDescent = m.fontBoundingBoxDescent
	return m


# ================================================================= images ==

## drawImage(img, dx, dy) / (img, dx, dy, dw, dh) / (img, sx, sy, sw, sh, dx,
## dy, dw, dh). `img` is a Texture2D.
func drawImage(img: Texture2D, a: float, b: float, c = null, d = null, e = null, f = null, g = null, h = null) -> void:
	if img == null:
		return
	var sz := img.get_size()
	var src := Rect2(Vector2.ZERO, sz)
	var dst: Rect2
	if e != null:
		src = Rect2(a, b, c, d)
		dst = Rect2(e, f, g, h)
	elif c != null:
		dst = Rect2(a, b, c, d)
	else:
		dst = Rect2(a, b, sz.x, sz.y)
	blit(img.get_rid(), src, dst)


## Draw a region of a texture (by RID) into a user-space rect.
func blit(tex: RID, src: Rect2, dst: Rect2, modulate: Color = Color.WHITE) -> void:
	modulate.a *= globalAlpha
	_blit(tex, src, dst, modulate, false)


## Blit from a texture holding premultiplied alpha (what a transparent 2D
## viewport renders — the art cache's atlas pages). Consecutive premultiplied
## blits share one segment, so a frame of cached sprites batches.
func blit_premul(tex: RID, src: Rect2, dst: Rect2) -> void:
	var a := globalAlpha
	_blit(tex, src, dst, Color(a, a, a, a), true)


## The uniform scale of the current transform (device pixels per user unit).
func device_scale() -> float:
	return _scale


## Blit an art-cache cell: `src` (atlas pixels, premultiplied) holds art drawn
## at `level` pixels per unit with its origin at `origin` inside the cell; this
## puts that origin at user-space point `at`. When the cell's level is the
## device scale, the origin is snapped to a whole pixel so texels land 1:1.
func blit_cell(tex: RID, src: Rect2, at: Vector2, origin: Vector2, level: float) -> void:
	var a := globalAlpha
	var k := _scale / level
	if measure_only or absf(_xf.x.y) > 1e-9 or absf(_xf.y.x) > 1e-9 or _xf.x.x <= 0.0 or _xf.y.y <= 0.0:
		_blit(tex, src, Rect2(at - origin / level, src.size / level), Color(a, a, a, a), true)
		return
	var p := _xf * at
	if absf(k - 1.0) < 0.01:
		p = p.round()
		k = 1.0
	RenderingServer.canvas_item_add_texture_rect_region(_segment(true), Rect2(p - origin * k, src.size * k), tex, src, Color(a, a, a, a))


func _blit(tex: RID, src: Rect2, dst: Rect2, modulate: Color, premul: bool) -> void:
	if measure_only:
		_grow_bounds(_xf * _rect_poly(dst))
		return
	var seg := _segment(premul)
	if absf(_xf.x.y) < 1e-9 and absf(_xf.y.x) < 1e-9 and _xf.x.x > 0.0 and _xf.y.y > 0.0:
		var p0 := _xf * dst.position
		var p1 := _xf * dst.end
		RenderingServer.canvas_item_add_texture_rect_region(seg, Rect2(p0, p1 - p0), tex, src, modulate)
		return
	RenderingServer.canvas_item_add_set_transform(seg, _xf)
	RenderingServer.canvas_item_add_texture_rect_region(seg, dst, tex, src, modulate)
	RenderingServer.canvas_item_add_set_transform(seg, Transform2D.IDENTITY)


# ============================================================== gradients ==

static var _measure_grad: CanvasGradient = null


func createLinearGradient(x0: float, y0: float, x1: float, y1: float) -> CanvasGradient:
	if measure_only:
		if _measure_grad == null:
			_measure_grad = CanvasGradient.new()
			_measure_grad.addColorStop(0, Color.BLACK)
			_measure_grad.frozen = true
		return _measure_grad
	var g := CanvasGradient.new()
	g.x0 = x0
	g.y0 = y0
	g.x1 = x1
	g.y1 = y1
	return g


func createRadialGradient(x0: float, y0: float, r0: float, x1: float, y1: float, r1: float) -> CanvasGradient:
	var g := CanvasGradient.new()
	g.radial = true
	g.x0 = x0
	g.y0 = y0
	g.r0 = r0
	g.x1 = x1
	g.y1 = y1
	g.r1 = r1
	return g


# ============================================================ named colours ==

const _NAMED := {
	"aliceblue": "f0f8ff", "antiquewhite": "faebd7", "aqua": "00ffff", "aquamarine": "7fffd4",
	"azure": "f0ffff", "beige": "f5f5dc", "bisque": "ffe4c4", "black": "000000",
	"blanchedalmond": "ffebcd", "blue": "0000ff", "blueviolet": "8a2be2", "brown": "a52a2a",
	"burlywood": "deb887", "cadetblue": "5f9ea0", "chartreuse": "7fff00", "chocolate": "d2691e",
	"coral": "ff7f50", "cornflowerblue": "6495ed", "cornsilk": "fff8dc", "crimson": "dc143c",
	"cyan": "00ffff", "darkblue": "00008b", "darkcyan": "008b8b", "darkgoldenrod": "b8860b",
	"darkgray": "a9a9a9", "darkgreen": "006400", "darkgrey": "a9a9a9", "darkkhaki": "bdb76b",
	"darkmagenta": "8b008b", "darkolivegreen": "556b2f", "darkorange": "ff8c00", "darkorchid": "9932cc",
	"darkred": "8b0000", "darksalmon": "e9967a", "darkseagreen": "8fbc8f", "darkslateblue": "483d8b",
	"darkslategray": "2f4f4f", "darkslategrey": "2f4f4f", "darkturquoise": "00ced1", "darkviolet": "9400d3",
	"deeppink": "ff1493", "deepskyblue": "00bfff", "dimgray": "696969", "dimgrey": "696969",
	"dodgerblue": "1e90ff", "firebrick": "b22222", "floralwhite": "fffaf0", "forestgreen": "228b22",
	"fuchsia": "ff00ff", "gainsboro": "dcdcdc", "ghostwhite": "f8f8ff", "gold": "ffd700",
	"goldenrod": "daa520", "gray": "808080", "green": "008000", "greenyellow": "adff2f",
	"grey": "808080", "honeydew": "f0fff0", "hotpink": "ff69b4", "indianred": "cd5c5c",
	"indigo": "4b0082", "ivory": "fffff0", "khaki": "f0e68c", "lavender": "e6e6fa",
	"lavenderblush": "fff0f5", "lawngreen": "7cfc00", "lemonchiffon": "fffacd", "lightblue": "add8e6",
	"lightcoral": "f08080", "lightcyan": "e0ffff", "lightgoldenrodyellow": "fafad2", "lightgray": "d3d3d3",
	"lightgreen": "90ee90", "lightgrey": "d3d3d3", "lightpink": "ffb6c1", "lightsalmon": "ffa07a",
	"lightseagreen": "20b2aa", "lightskyblue": "87cefa", "lightslategray": "778899", "lightslategrey": "778899",
	"lightsteelblue": "b0c4de", "lightyellow": "ffffe0", "lime": "00ff00", "limegreen": "32cd32",
	"linen": "faf0e6", "magenta": "ff00ff", "maroon": "800000", "mediumaquamarine": "66cdaa",
	"mediumblue": "0000cd", "mediumorchid": "ba55d3", "mediumpurple": "9370db", "mediumseagreen": "3cb371",
	"mediumslateblue": "7b68ee", "mediumspringgreen": "00fa9a", "mediumturquoise": "48d1cc", "mediumvioletred": "c71585",
	"midnightblue": "191970", "mintcream": "f5fffa", "mistyrose": "ffe4e1", "moccasin": "ffe4b5",
	"navajowhite": "ffdead", "navy": "000080", "oldlace": "fdf5e6", "olive": "808000",
	"olivedrab": "6b8e23", "orange": "ffa500", "orangered": "ff4500", "orchid": "da70d6",
	"palegoldenrod": "eee8aa", "palegreen": "98fb98", "paleturquoise": "afeeee", "palevioletred": "db7093",
	"papayawhip": "ffefd5", "peachpuff": "ffdab9", "peru": "cd853f", "pink": "ffc0cb",
	"plum": "dda0dd", "powderblue": "b0e0e6", "purple": "800080", "rebeccapurple": "663399",
	"red": "ff0000", "rosybrown": "bc8f8f", "royalblue": "4169e1", "saddlebrown": "8b4513",
	"salmon": "fa8072", "sandybrown": "f4a460", "seagreen": "2e8b57", "seashell": "fff5ee",
	"sienna": "a0522d", "silver": "c0c0c0", "skyblue": "87ceeb", "slateblue": "6a5acd",
	"slategray": "708090", "slategrey": "708090", "snow": "fffafa", "springgreen": "00ff7f",
	"steelblue": "4682b4", "tan": "d2b48c", "teal": "008080", "thistle": "d8bfd8",
	"tomato": "ff6347", "turquoise": "40e0d0", "violet": "ee82ee", "wheat": "f5deb3",
	"white": "ffffff", "whitesmoke": "f5f5f5", "yellow": "ffff00", "yellowgreen": "9acd32",
}
