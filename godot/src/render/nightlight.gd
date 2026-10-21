## render/nightlight.ts — night lighting: darkness with holes in it.
##
## Night used to be one flat navy rectangle over the whole battlefield. Every
## warm thing the building art drew for the dark — lit windows, hearth glow, a
## watchfire's pool of light — was painted *underneath* that rectangle and came
## out as dim as the grass beside it, so the promised "town comes alive at
## night" was a slightly bluer screen. The fix is the standard 2D one: draw the
## darkness into its own layer, cut soft holes where the lights are, tint the
## holes warm, and lay the layer over the scene. Lit ground then shows its true
## colour and everything between the lights stays night.
##
## The layer is a quarter of the screen's resolution. Darkness and light pools
## are all soft falloff with nothing sharp in them, so upscaling costs nothing
## visible and it keeps the pass to one small fill, a handful of sprite blits
## and one full-screen blit — about what the vignette already costs.
##
## Godot port: the layer is a GpuLayer (an offscreen viewport, premultiplied),
## and the blobs are textured quads straight onto its canvas items: the hole
## cutting is a multiply blend that scales colour and alpha together
## (destination-out on premultiplied pixels), the firelight an ordinary blend.
class_name Nightlight

## Buildings that are lived in and light their windows after dark.
const LIT_BUILDINGS := {
	"town_center": true, "house": true, "mill": true, "barracks": true, "archery_range": true, "stable": true,
	"blacksmith": true, "market": true, "castle": true, "watch_tower": true, "siege_workshop": true,
}


## A pool of light (TS `Light`): world-space centre and radius, and how much of
## the darkness it clears at its centre (0..1).
class Light extends RefCounted:
	var x := 0.0
	var y := 0.0
	var r := 0.0
	var s := 0.0

	func _init(x_: float = 0.0, y_: float = 0.0, r_: float = 0.0, s_: float = 0.0) -> void:
		x = x_
		y = y_
		r = r_
		s = s_


## The light an entity gives off at night, or null. Pure, so the choice of
## what glows is testable without a canvas.
##
## `own` is whether the viewer controls it. Your own units carry a faint
## lantern so an army stays findable in the dark; nobody else's do, because a
## light that followed enemy units around would make night *easier* to scout,
## not harder.
static func lightFor(e: Entity, time: float, own: bool) -> Light:
	if e.kind == T.Kind.Building:
		if e.buildState != T.BuildState.Done:
			return null
		var half := e.radius
		var flick := 0.92 + sin(time * 9 + e.id * 2.3) * 0.05 + sin(time * 13.7 + e.id) * 0.03
		if e.type == "watchfire":
			return Light.new(e.x, e.y - half * 0.3, half * 7.5 * flick, 0.95)
		var burning := e.hp < e.maxHp * 0.5
		if LIT_BUILDINGS.has(e.type):
			var big := e.type == "town_center" or e.type == "castle"
			return Light.new(e.x, e.y + half * 0.2,
				half * (3.0 if big else 2.7) * (1.25 if burning else 1.0),
				(0.85 if burning else 0.62) * flick)
		# A wall or farm on fire still lights up the night.
		if burning:
			return Light.new(e.x, e.y, half * 2.4, 0.7 * flick)
		return null
	if e.kind == T.Kind.Unit and own:
		return Light.new(e.x, e.y, 34, 0.32)
	return null


class NightLayer extends RefCounted:
	const DOWNSCALE := 4
	var layer: GpuLayer = null
	var tintItem := RID()
	var holeItem := RID()
	var warmItem := RID()
	static var _blob: ImageTexture = null
	static var _warm: ImageTexture = null
	static var _hole_mat: ShaderMaterial = null

	## A 64×64 soft disc in `rgb` (TS sprite()): a smooth shoulder rather than
	## a linear ramp — a linear falloff has a visible cone tip in the middle of
	## every light.
	static func sprite(rgb: Color) -> ImageTexture:
		var img := Image.create(64, 64, false, Image.FORMAT_RGBA8)
		var stops := [[0.0, 1.0], [0.35, 0.85], [0.7, 0.3], [1.0, 0.0]]
		for py in 64:
			for px in 64:
				var d := Vector2(px + 0.5 - 32, py + 0.5 - 32).length() / 32.0
				var a := 0.0
				if d <= 0.0:
					a = 1.0
				elif d < 1.0:
					for i in range(1, stops.size()):
						if d <= stops[i][0]:
							var k: float = (d - stops[i - 1][0]) / (stops[i][0] - stops[i - 1][0])
							a = lerpf(stops[i - 1][1], stops[i][1], k)
							break
				img.set_pixel(px, py, Color(rgb.r, rgb.g, rgb.b, a))
		return ImageTexture.create_from_image(img)

	static func _hole_material() -> ShaderMaterial:
		if _hole_mat == null:
			var sh := Shader.new()
			# destination-out on premultiplied pixels: dst *= 1 - a, colour and
			# alpha alike.
			sh.code = "shader_type canvas_item;\nrender_mode blend_mul;\nvoid fragment() { float k = 1.0 - COLOR.a; COLOR = vec4(k, k, k, k); }\n"
			_hole_mat = ShaderMaterial.new()
			_hole_mat.shader = sh
		return _hole_mat

	## Composite the night over the frame. `tint` is the sky colour (rgba, a in
	## 0..1), `night` how far into the night we are (0..1), and each light's
	## position is in world space; `toScreen` maps it with the camera (a
	## Callable(x, y) -> Vector2).
	func draw(ctx: Ctx, W: float, H: float, tint: Array, night: float, lights: Array, zoom: float, toScreen: Callable) -> void:
		var k := DOWNSCALE
		var lw := maxi(1, int(ceil(W / k)))
		var lh := maxi(1, int(ceil(H / k)))
		if layer == null or layer.w != lw or layer.h != lh:
			layer = GpuLayer.new(lw, lh, true)
			tintItem = layer.item()
			holeItem = layer.item()
			warmItem = layer.item()
			RenderingServer.canvas_item_set_material(holeItem, _hole_material().get_rid())
		if _blob == null:
			_blob = sprite(Color(1, 1, 1))
		if _warm == null:
			_warm = sprite(Color8(255, 176, 84))
		RenderingServer.canvas_item_clear(tintItem)
		RenderingServer.canvas_item_clear(holeItem)
		RenderingServer.canvas_item_clear(warmItem)
		RenderingServer.canvas_item_add_rect(tintItem, Rect2(0, 0, lw, lh),
			Color8(int(tint[0]), int(tint[1]), int(tint[2]), int(clampf(float(tint[3]), 0, 1) * 255.0 + 0.5)))

		if night > 0.01 and not lights.is_empty():
			var blob := _blob.get_rid()
			var warm := _warm.get_rid()
			for li: Light in lights:
				var sp: Vector2 = toScreen.call(li.x, li.y)
				var rad := (li.r * zoom) / k
				var x := sp.x / k
				var y := sp.y / k
				if x + rad < 0 or y + rad < 0 or x - rad > lw or y - rad > lh:
					continue
				# Cut the hole...
				RenderingServer.canvas_item_add_texture_rect(holeItem, Rect2(x - rad, y - rad, rad * 2, rad * 2), blob,
					false, Color(1, 1, 1, minf(1, li.s * night)))
				# ...and fill it with firelight, a little tighter than the hole
				# so the warm core sits inside the cleared ground.
				var r := rad * 0.8
				RenderingServer.canvas_item_add_texture_rect(warmItem, Rect2(x - r, y - r, r * 2, r * 2), warm,
					false, Color(1, 1, 1, minf(1, li.s * night * 0.3)))
		layer.render()
		ctx.blit_premul(layer.tex, Rect2(0, 0, lw, lh), Rect2(0, 0, lw * k, lh * k))
