## render/fogblur.ts — softening the fog frontier.
##
## Fog is one value per nav cell and only ever takes three of them — unseen,
## explored, visible. Upscaling that straight to the screen ramps between cell
## *centres*, so every diagonal frontier (which is most of them) arrives as a
## staircase. Blurring the mask first turns that edge into the soft falloff a
## scouted horizon should have.
##
## Its own function, and separable, for two reasons: a box blur done as two
## one-dimensional passes is O(n·r) instead of O(n·r²), and a frontier is far
## easier to make assertions about here than anywhere downstream of the
## compositor, where terrain noise swamps it.
##
## Godot port: the live fog of war does this blur on the GPU (see
## Renderer._fog_blur_shader — the same separable box, radius FOG_BLUR_RADIUS,
## clamped at the edges). This CPU version is what the terrain bake uses for its
## smooth fields, and what the tests check.
class_name FogBlur

## How far the fog edge is smeared, in cells either side of the true frontier.
const FOG_BLUR_RADIUS := 2


## Separable box blur over a `cols`×`rows` grid, in place on `a`.
##
## `scratch` must be the same length as `a`; it is the intermediate buffer for
## the horizontal pass and holds nothing meaningful afterwards. Both are passed
## in rather than allocated so a caller allocates nothing per call. Packed
## arrays are values in GDScript, so the blurred grid is also returned — use
## the return value (`a = FogBlur.blurMask(a, s, ...)`).
static func blurMask(a: PackedFloat32Array, scratch: PackedFloat32Array, cols: int, rows: int, radius: int) -> PackedFloat32Array:
	if radius <= 0:
		return a
	var inv := 1.0 / (radius * 2 + 1)
	if scratch.size() != a.size():
		scratch.resize(a.size())
	var cm := cols - 1
	var rm := rows - 1
	for y in rows:
		var row := y * cols
		for x in cols:
			var sum := 0.0
			for k in range(-radius, radius + 1):
				sum += a[row + clampi(x + k, 0, cm)]
			scratch[row + x] = sum * inv
	for x in cols:
		for y in rows:
			var sum := 0.0
			for k in range(-radius, radius + 1):
				sum += scratch[clampi(y + k, 0, rm) * cols + x]
			a[y * cols + x] = sum * inv
	return a
