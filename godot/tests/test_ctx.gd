extends TestCase
# Ctx (render/ctx.gd): colour and font parsing, path tessellation, strokes,
# transforms, state, gradients, clips and text — the canvas semantics the
# drawing code relies on. Geometry is checked by capturing what Ctx emits
# and measuring the triangles' area.


## A Ctx that keeps the triangles it would have drawn.
class Capture extends Ctx:
	var tris: Array = []  ## [a, b, c] Vector2 triples
	var colors: Array = []

	func _emit(verts: PackedVector2Array, cols: PackedColorArray, idx: PackedInt32Array) -> void:
		for i in range(0, idx.size(), 3):
			tris.append([verts[idx[i]], verts[idx[i + 1]], verts[idx[i + 2]]])
		colors.append(cols)

	func area() -> float:
		var a := 0.0
		for t in tris:
			a += absf((t[1] - t[0]).cross(t[2] - t[0])) * 0.5
		return a

	func clear_capture() -> void:
		tris.clear()
		colors.clear()


static func cap() -> Capture:
	var c := Capture.new()
	c.aa = false
	return c


# ------------------------------------------------------------------ colours --

func test_parse_hex_colors() -> void:
	eq(Ctx.parse_color("#fff"), Color(1, 1, 1, 1), "#fff")
	eq(Ctx.parse_color("#3a6fd8"), Color8(0x3a, 0x6f, 0xd8), "#rrggbb")
	eq(Ctx.parse_color("#FF000080"), Color8(255, 0, 0, 128), "#rrggbbaa")
	eq(Ctx.parse_color("#f008"), Color8(255, 0, 0, 136), "#rgba")
	check(Ctx.parse_color("#ggg") == null, "bad hex is not a colour")
	check(Ctx.parse_color("#12345") == null, "5 digits is not a colour")


func test_parse_functional_colors() -> void:
	eq(Ctx.parse_color("rgb(255, 0, 0)"), Color(1, 0, 0, 1), "rgb()")
	eq(Ctx.parse_color("rgba(0,0,255,0.5)"), Color(0, 0, 1, 0.5), "rgba()")
	eq(Ctx.parse_color("rgba(20, 24, 12, 0.25)"), Color(20 / 255.0, 24 / 255.0, 12 / 255.0, 0.25), "rgba with spaces")
	eq(Ctx.parse_color("rgb(100% 0% 0% / 50%)"), Color(1, 0, 0, 0.5), "space syntax + percentages")
	eq(Ctx.parse_color("rgba(0,0,0,7)").a, 1.0, "alpha clamps")
	var g = Ctx.parse_color("hsl(120, 100%, 50%)")
	check(g != null and g.is_equal_approx(Color(0, 1, 0)), "hsl green: %s" % [g])
	var b = Ctx.parse_color("hsla(240,100%,50%,0.25)")
	check(b != null and b.is_equal_approx(Color(0, 0, 1, 0.25)), "hsla blue")
	var gr = Ctx.parse_color("hsl(0, 0%, 50%)")
	check(gr != null and gr.is_equal_approx(Color(0.5, 0.5, 0.5)), "hsl grey")


func test_parse_named_colors() -> void:
	eq(Ctx.parse_color("red"), Color(1, 0, 0), "red")
	eq(Ctx.parse_color("RebeccaPurple"), Color.html("663399"), "case-insensitive")
	eq(Ctx.parse_color("transparent"), Color(0, 0, 0, 0), "transparent")
	check(Ctx.parse_color("notacolor") == null, "unknown name")
	check(Ctx.parse_color("") == null, "empty")


func test_invalid_style_keeps_previous() -> void:
	var c := cap()
	c.fillStyle = "#123456"
	c.fillStyle = "nonsense"
	eq(c.fillStyle, "#123456", "invalid fillStyle ignored")
	c.lineWidth = -3
	eq(c.lineWidth, 1.0, "negative lineWidth ignored")
	c.globalAlpha = 2
	eq(c.globalAlpha, 1.0, "out-of-range alpha ignored")
	c.fillStyle = Color(0.5, 0.5, 0.5)
	check(c.fillStyle is Color, "a Color is accepted as a style")


# -------------------------------------------------------------------- fonts --

func test_parse_fonts() -> void:
	var a = Ctx._parse_font("bold 13px 'Trebuchet MS', sans-serif")
	eq(a[0], 13.0, "size")
	eq(Array(a[1]), ["Trebuchet MS", "sans-serif"], "families, quotes stripped")
	eq(a[2], 700, "bold")
	eq(a[3], false, "not italic")
	var b = Ctx._parse_font("italic 16px Georgia, serif")
	eq(b[3], true, "italic")
	eq(b[2], 400, "normal weight")
	eq(Array(b[1]), ["Georgia", "serif"], "families")
	var c = Ctx._parse_font("12.5px \"Trebuchet MS\", sans-serif")
	eq(c[0], 12.5, "fractional size")
	eq(Array(c[1])[0], "Trebuchet MS", "double quotes stripped")
	var d = Ctx._parse_font("600 9pt ui-monospace, SFMono-Regular, monospace")
	near(d[0], 12.0, 1e-9, "pt to px")
	eq(d[2], 600, "numeric weight")
	check(Ctx._parse_font("bold") == null, "no size → invalid")
	check(Ctx._parse_font("13px") == null, "no family → invalid")


func test_font_objects_are_cached() -> void:
	var c := cap()
	c.font = "bold 13px 'Trebuchet MS', sans-serif"
	var f1 := Ctx.font_for(c._font_spec)
	c.font = "bold 20px 'Trebuchet MS', sans-serif"
	var f2 := Ctx.font_for(c._font_spec)
	check(f1 == f2, "same family/weight/style shares one font across sizes")
	c.font = "13px 'Trebuchet MS', sans-serif"
	check(Ctx.font_for(c._font_spec) != f1, "a different weight is a different font")
	c.font = "bad font"
	eq(c.font, "13px 'Trebuchet MS', sans-serif", "invalid font ignored")


# ------------------------------------------------------------- tessellation --

func test_fill_rect_and_concave_polygon() -> void:
	var c := cap()
	c.fillRect(0, 0, 10, 10)
	near(c.area(), 100, 1e-4, "fillRect area")
	c.clear_capture()
	# An L shape: 10x10 minus a 5x5 corner.
	c.beginPath()
	c.moveTo(0, 0); c.lineTo(10, 0); c.lineTo(10, 5); c.lineTo(5, 5); c.lineTo(5, 10); c.lineTo(0, 10); c.closePath()
	c.fill()
	near(c.area(), 75, 1e-3, "concave polygon area")


func test_fill_circle_and_round_rect() -> void:
	var c := cap()
	c.beginPath()
	c.arc(0, 0, 20, 0, TAU)
	c.fill()
	near(c.area(), PI * 400, PI * 400 * 0.01, "circle area within 1%")
	c.clear_capture()
	c.beginPath()
	c.roundRect(0, 0, 40, 20, 5)
	c.fill()
	near(c.area(), 800 - (4 - PI) * 25, 2.0, "rounded rect area")
	c.clear_capture()
	c.beginPath()
	c.roundRect(0, 0, 20, 10, [10, 10, 0, 0])
	c.fill()
	# Top corners of radius 10 on a 20×10 box: exactly a half-disc.
	near(c.area(), PI * 50, 1.5, "per-corner radii")
	c.clear_capture()
	c.beginPath()
	c.roundRect(0, 0, 20, 10, 50)
	c.fill()
	near(c.area(), 100 + PI * 25, 1.5, "oversized radii scale down to a stadium")


func test_arc_to_rounded_rect() -> void:
	# The warband screen's rounded cards: four arcTo corners.
	var c := cap()
	var x := 10.0
	var y := 20.0
	var w := 60.0
	var h := 40.0
	var r := 8.0
	c.beginPath()
	c.moveTo(x + r, y)
	c.arcTo(x + w, y, x + w, y + h, r)
	c.arcTo(x + w, y + h, x, y + h, r)
	c.arcTo(x, y + h, x, y, r)
	c.arcTo(x, y, x + w, y, r)
	c.closePath()
	c.fill()
	near(c.area(), w * h - (4 - PI) * r * r, 2.0, "arcTo rounded rect area")
	var mn := Vector2(INF, INF)
	var mx := Vector2(-INF, -INF)
	for t in c.tris:
		for v in t:
			mn = mn.min(v)
			mx = mx.max(v)
	check(mn.is_equal_approx(Vector2(x, y)) and mx.is_equal_approx(Vector2(x + w, y + h)), "corners stay inside the box: %s %s" % [mn, mx])


func test_nonzero_overlapping_subpaths() -> void:
	var c := cap()
	c.fillStyle = "rgba(0,0,0,0.5)"  # translucent: forces the winding-rule path
	c.beginPath()
	c.rect(0, 0, 10, 10)
	c.rect(5, 5, 10, 10)
	c.fill()
	near(c.area(), 175, 1e-3, "same winding: union")
	c.clear_capture()
	c.beginPath()
	c.rect(0, 0, 10, 10)
	c.rect(5, 5, 10, 10)
	c.fill("evenodd")
	near(c.area(), 150, 1e-3, "even-odd: overlap is a hole")
	c.clear_capture()
	# A square with a reversed square inside: nonzero leaves a hole.
	c.beginPath()
	c.moveTo(0, 0); c.lineTo(20, 0); c.lineTo(20, 20); c.lineTo(0, 20); c.closePath()
	c.moveTo(5, 5); c.lineTo(5, 15); c.lineTo(15, 15); c.lineTo(15, 5); c.closePath()
	c.fill()
	near(c.area(), 300, 1e-3, "opposite winding: hole")


func test_self_intersecting_star() -> void:
	var star := func(c: Ctx) -> void:
		c.beginPath()
		for i in 5:
			var a := -PI / 2 + i * 4 * PI / 5
			c.lineTo(cos(a) * 30, sin(a) * 30)
		c.closePath()
	var nz := cap()
	nz.fillStyle = "rgba(0,0,0,0.5)"
	star.call(nz)
	nz.fill()
	var eo := cap()
	star.call(eo)
	eo.fill("evenodd")
	# The pentagram's inner pentagon: circumradius 30·sin(18°)/sin(126°).
	var ri := 30.0 * sin(deg_to_rad(18)) / sin(deg_to_rad(126))
	var pentagon := 2.5 * ri * ri * sin(deg_to_rad(72))
	near(nz.area() - eo.area(), pentagon, 0.5, "nonzero fills the centre, even-odd doesn't")
	check(nz.area() > 1000, "star has area")


func test_joined_arcs_fill_as_one_shape() -> void:
	# The berry bush: three arcs in one path (joined by straight lines),
	# overlapping — nonzero fills the union once.
	var c := cap()
	c.fillStyle = "rgba(0,0,0,0.5)"
	c.beginPath()
	c.arc(0, 0, 10, 0, TAU)
	c.arc(30, 0, 10, 0, TAU)
	c.fill()
	near(c.area(), 2 * PI * 100, 8.0, "two separate discs (plus the hairline join)")


# ------------------------------------------------------------------ strokes --

func test_stroke_caps() -> void:
	var c := cap()
	c.lineWidth = 2
	c.beginPath(); c.moveTo(0, 0); c.lineTo(10, 0); c.stroke()
	near(c.area(), 20, 1e-3, "butt cap")
	c.clear_capture()
	c.lineCap = "square"
	c.beginPath(); c.moveTo(0, 0); c.lineTo(10, 0); c.stroke()
	near(c.area(), 24, 1e-3, "square cap")
	c.clear_capture()
	c.lineCap = "round"
	c.beginPath(); c.moveTo(0, 0); c.lineTo(10, 0); c.stroke()
	near(c.area(), 20 + PI, 0.15, "round caps")


func test_stroke_joins_and_closed_paths() -> void:
	var c := cap()
	c.lineWidth = 2
	c.strokeRect(0, 0, 10, 10)
	near(c.area(), 12 * 12 - 8 * 8, 1e-3, "mitred square outline")
	c.clear_capture()
	c.lineJoin = "bevel"
	c.strokeRect(0, 0, 10, 10)
	near(c.area(), 80 - 4 * 0.5, 1e-3, "bevelled corners cut half a unit square each")
	c.clear_capture()
	c.lineJoin = "round"
	c.strokeRect(0, 0, 10, 10)
	near(c.area(), 80 - 4 * (1 - PI / 4), 0.25, "round corners")


func test_dashes() -> void:
	var c := cap()
	c.setLineDash([5, 5])
	eq(c.getLineDash(), [5.0, 5.0], "getLineDash")
	var parts := c._apply_dash([PackedVector2Array([Vector2(0, 0), Vector2(20, 0)])], [false])
	eq(parts.size(), 2, "two dashes on a 20px line")
	near((parts[0] as PackedVector2Array)[1].x, 5.0, 1e-4, "first dash ends at 5")
	c.setLineDash([3])
	eq(c.getLineDash(), [3.0, 3.0], "odd lists repeat")
	c.lineDashOffset = 2
	var p2 := c._apply_dash([PackedVector2Array([Vector2(0, 0), Vector2(10, 0)])], [false])
	near((p2[0] as PackedVector2Array)[1].x, 1.0, 1e-4, "offset shifts the pattern")
	c.setLineDash([])
	eq(c.getLineDash(), [], "cleared")


# ----------------------------------------------------- transforms and state --

func test_transforms_apply_when_points_are_added() -> void:
	var c := cap()
	c.translate(10, 0)
	c.rotate(PI / 2)
	var p := c.getTransform() * Vector2(1, 0)
	check(p.is_equal_approx(Vector2(10, 1)), "translate then rotate: %s" % [p])
	c.resetTransform()
	c.save()
	c.scale(2, 2)
	c.beginPath()
	c.rect(0, 0, 5, 5)  # added under the scale...
	c.restore()
	c.fill()  # ...filled after it's gone: still 10x10
	near(c.area(), 100, 1e-3, "path keeps the transform it was built under")
	c.setTransform(1, 0, 0, 1, 7, 8)
	check((c.getTransform() * Vector2.ZERO).is_equal_approx(Vector2(7, 8)), "setTransform")


func test_save_restore() -> void:
	var c := cap()
	c.fillStyle = "#ff0000"
	c.lineWidth = 3
	c.font = "bold 20px Georgia, serif"
	c.save()
	c.fillStyle = "#00ff00"
	c.lineWidth = 7
	c.font = "10px sans-serif"
	c.translate(5, 5)
	c.globalAlpha = 0.5
	c.restore()
	eq(c.fillStyle, "#ff0000", "fillStyle")
	eq(c._fill_paint, Color(1, 0, 0), "resolved paint")
	eq(c.lineWidth, 3.0, "lineWidth")
	eq(c.font, "bold 20px Georgia, serif", "font")
	eq(c.globalAlpha, 1.0, "globalAlpha")
	check(c.getTransform() == Transform2D.IDENTITY, "transform")
	c.restore()  # an extra restore is a no-op
	eq(c.lineWidth, 3.0, "unbalanced restore ignored")


func test_global_alpha_multiplies() -> void:
	var c := cap()
	c.fillStyle = "rgba(255,0,0,0.5)"
	c.globalAlpha = 0.5
	c.fillRect(0, 0, 1, 1)
	near((c.colors[0] as PackedColorArray)[0].a, 0.25, 1e-6, "alpha × globalAlpha")


# ---------------------------------------------------------------- gradients --

func test_linear_gradient() -> void:
	var c := cap()
	var g := c.createLinearGradient(0, 0, 10, 0)
	g.addColorStop(0, "#000000")
	g.addColorStop(1, "#ffffff")
	near(g.t_at(Vector2(5, 3)), 0.5, 1e-9, "t across")
	check(g.at(0.5).is_equal_approx(Color(0.5, 0.5, 0.5)), "midpoint colour")
	check(g.at(-1).is_equal_approx(Color.BLACK) and g.at(2).is_equal_approx(Color.WHITE), "clamps")
	g.addColorStop(0.5, "#ff0000")
	check(g.at(0.5).is_equal_approx(Color.RED), "stops keep order")
	# Filling past the gradient's ends and across its middle stop splits the
	# triangles at t = 0, 0.5 and 1 so the colours come out right.
	c.fillStyle = g
	c.fillRect(-5, 0, 20, 4)
	var cols: PackedColorArray = c.colors[0]
	var reds := 0
	for col in cols:
		if col.is_equal_approx(Color.RED):
			reds += 1
	check(reds >= 2, "vertices placed on the middle stop (%d)" % reds)
	near(c.area(), 80, 1e-3, "split triangles still cover the rect")


func test_radial_gradient() -> void:
	var c := cap()
	var g := c.createRadialGradient(0, 0, 0, 0, 0, 10)
	g.addColorStop(0, "#ffffff")
	g.addColorStop(1, "#000000")
	near(g.t_at(Vector2(5, 0)), 0.5, 1e-9, "concentric t is the radius ratio")
	near(g.t_at(Vector2(0, 10)), 1.0, 1e-9, "rim")
	c.fillStyle = g
	c.beginPath(); c.arc(0, 0, 10, 0, TAU); c.fill()
	var brightest := 0.0
	for col in (c.colors[0] as PackedColorArray):
		brightest = maxf(brightest, col.r)
	check(brightest > 0.7, "refined: interior vertices near the centre are bright (%.2f)" % brightest)


# -------------------------------------------------------------- clip / text --

func test_clip_state() -> void:
	var c := cap()
	c.save()
	c.beginPath(); c.rect(10, 10, 50, 40); c.clip()
	check(c._clip_on and c._clip_rect == Rect2(10, 10, 50, 40), "rect clip is exact")
	check(c._clip_poly.is_empty(), "no polygon for a rectangle")
	c.beginPath(); c.moveTo(0, 0); c.lineTo(40, 0); c.lineTo(0, 40); c.closePath(); c.clip()
	check(not c._clip_poly.is_empty(), "a triangle clips by polygon")
	check(c._clip_rect == Rect2(10, 10, 30, 30), "nested clips intersect: %s" % [c._clip_rect])
	# Fills are cut to the polygon: the triangle inside the rect is
	# (10,10)-(30,10)-(10,30).
	c.fillRect(0, 0, 100, 100)
	near(c.area(), 200, 1e-3, "fill cut to the clip polygon")
	c.restore()
	check(not c._clip_on, "restore drops the clip")


func test_measure_text_and_alignment() -> void:
	var c := cap()
	c.font = "12px sans-serif"
	var w1 := c.measureText("measure me").width
	var w2 := c.measureText("measure me measure me").width
	check(w1 > 30 and w1 < 120, "plausible width %.1f" % w1)
	near(w2, w1 * 2 + c.measureText(" ").width, 2.0, "widths add up")
	c.font = "24px sans-serif"
	near(c.measureText("measure me").width, w1 * 2, w1 * 0.15, "width scales with size")
	c.font = "12px sans-serif"
	c.scale(2, 2)
	near(c.measureText("measure me").width, w1, w1 * 0.08, "measureText is in user units")


func test_measure_mode_bounds() -> void:
	var c := Ctx.new()
	c.measure_only = true
	c.begin(RID())
	c.beginPath(); c.arc(50, 50, 10, 0, TAU); c.fill()
	c.lineWidth = 4
	c.beginPath(); c.moveTo(0, 0); c.lineTo(20, 0); c.stroke()
	check(c.bounds_valid, "bounds grew")
	check(c.bounds.has_point(Vector2(59, 50)) and c.bounds.has_point(Vector2(0, -1.9)), "covers the shapes: %s" % [c.bounds])


# ------------------------------------------------------------------ palette --

func test_palette_matches_ts() -> void:
	# Values from render/palette.ts.
	eq(Palette.shade("#3a6fd8", 0.3), Color8(117, 154, 228), "shade lighter")
	eq(Palette.shade("#3a6fd8", -0.25), Color8(44, 83, 162), "shade darker")
	eq(Palette.shade("rgb(100, 50, 25)", 0.0), Color8(100, 50, 25), "shade of rgb()")
	eq(Palette.shade(Palette.shade("#808080", 0.5), -0.5), Color8(96, 96, 96), "shade of a shade")
	eq(Palette.withAlpha("#3a6fd8", 0.5), Color8(58, 111, 216, 128) if false else Color(58 / 255.0, 111 / 255.0, 216 / 255.0, 0.5), "withAlpha")
	eq(Palette.teamColor(1).main, "#d8403a", "team 1")
	eq(Palette.teamColor(16).main, "#3a6fd8", "neutral falls back to team 0")
	Palette.setColorblindTeams(true)
	eq(Palette.teamColor(0).main, "#1f6fe0", "colourblind palette")
	eq(Palette.teamColor(0).light, Palette.shade("#1f6fe0", 0.42), "derived light")
	Palette.setColorblindTeams(false)
	eq(Palette.teamColor(0).main, "#3a6fd8", "back to normal")
