## render/terrain.ts — terrain pre-rendering. The full map is painted once into
## an offscreen texture (at half resolution to save memory — the soft scale-up
## reads as painterly), then blitted per frame. Also builds the minimap base
## image.
##
## Style notes: tile colors are blended with smooth value-noise (no per-tile
## checkerboard), biome boundaries are painted as organic blobs, water gets a
## sandy shoreline and depth shading, and the ground is dressed with clustered
## tufts, flowers and pebbles plus large soft light patches.
##
## Godot port (class_name TerrainArt: `Terrain` is the Warband board's):
## - The bake paints into a GpuLayer (an offscreen viewport) instead of a
##   canvas, in the same order and with the same RNG draws, so it is the same
##   picture. The grass base is one nearest-filtered texture of cell colours
##   (the TS's one fillRect per cell), and the per-pixel relief/water pass runs
##   as a fragment shader over the cell fields — the TS's pixel loop, line for
##   line, on the GPU.
## - Ground detail (drawGroundDetail) is the same marks, built once per map
##   into meshes, chunk by chunk, and drawn every frame for free; the grass
##   sway is a vertex shader over blade tips. See GroundDetail below.
class_name TerrainArt

const Terrain = TerrainKinds.Terrain

const TERRAIN_SCALE := 0.5

## The scale the cache for *this* map is baked at.
##
## Half-scale is right for a normal map, but the cache is one canvas spanning
## the whole world, and a 320-cell custom map is 10,240 world units across —
## 5,120 pixels at half scale, which is past Safari's 4,096-pixel canvas limit
## and would hand back a blank texture rather than an error. So large maps are
## baked coarser. Nothing is lost: at the zoom you view a map that size from,
## the extra resolution was sub-pixel anyway.
const MAX_CACHE_PX := 3072.0


static func terrainCacheScale(map: Dictionary) -> float:
	var longest := maxf(float(map.worldW), float(map.worldH))
	return minf(TERRAIN_SCALE, MAX_CACHE_PX / maxf(1.0, longest))


## Smooth 2D value noise on a coarse lattice, bilinear + smoothstep (TS
## makeNoise, which returns a closure; here an object with `at`).
class ValueNoise:
	var g: PackedFloat32Array
	var gw := 0
	var step := 1.0

	func _init(rng: RNG, cols: int, rows: int, step_: float) -> void:
		step = step_
		gw = int(ceil(cols / step)) + 2
		var gh := int(ceil(rows / step)) + 2
		g = PackedFloat32Array()
		g.resize(gw * gh)
		for i in g.size():
			g[i] = rng.range(0, 1)

	func at(cx: float, cy: float) -> float:
		var fx := cx / step
		var fy := cy / step
		var x0 := int(floor(fx))
		var y0 := int(floor(fy))
		var tx := fx - x0
		var ty := fy - y0
		tx = tx * tx * (3 - 2 * tx)
		ty = ty * ty * (3 - 2 * ty)
		var a: float = g[y0 * gw + x0]
		var b: float = g[y0 * gw + x0 + 1]
		var c: float = g[(y0 + 1) * gw + x0]
		var d: float = g[(y0 + 1) * gw + x0 + 1]
		return a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty


static func makeNoise(rng: RNG, cols: int, rows: int, step: float) -> ValueNoise:
	return ValueNoise.new(rng, cols, rows, step)


## The baked cache: a texture (RID) of the whole map at `scale` pixels per
## world unit, and the offscreen layer that holds it.
class TerrainCache extends RefCounted:
	var layer: GpuLayer
	var tex := RID()
	var width := 0
	var height := 0
	var scale := 0.5
	## Where the painted water is (the bake's wetAt), for tests.
	var shore := PackedFloat32Array()


static func buildTerrainCache(map: Dictionary) -> TerrainCache:
	var scale := terrainCacheScale(map)
	var W := int(ceil(float(map.worldW) * scale))
	var H := int(ceil(float(map.worldH) * scale))
	var PAL: Dictionary = C.PAL
	var cols: int = map.cols
	var rows: int = map.rows
	var terr: PackedByteArray = map.terrain
	var layer := GpuLayer.new(W, H, false)
	var out := TerrainCache.new()
	out.layer = layer
	out.tex = layer.tex
	out.width = W
	out.height = H
	out.scale = scale
	var rng := RNG.new(int(map.seed) ^ 0xbeef)
	var TILE: float = C.TILE
	var t := TILE * scale

	# Two octaves of smooth noise drive all ground shading — variation flows
	# ACROSS tiles instead of changing at every tile edge (no checkerboard).
	var n1 := makeNoise(rng, cols, rows, 7)
	var n2 := makeNoise(rng, cols, rows, 2.3)
	var shadeOf := PackedFloat32Array()
	shadeOf.resize(cols * rows)
	for cy in rows:
		for cx in cols:
			shadeOf[cy * cols + cx] = n1.at(cx, cy) * 0.72 + n2.at(cx, cy) * 0.28 - 0.5

	# --- 1. Grass base, noise-shaded ----------------------------------------
	# One fillRect per cell in the TS; here those colours as a cols×rows image,
	# drawn nearest-filtered over the same rects.
	var base := Image.create(cols, rows, false, Image.FORMAT_RGBA8)
	for cy in rows:
		for cx in cols:
			base.set_pixel(cx, cy, Palette.shade(PAL.grass, shadeOf[cy * cols + cx] * 0.13))
	var baseTex := ImageTexture.create_from_image(base)
	layer.keep(baseTex)
	var baseItem := layer.item()
	RenderingServer.canvas_item_set_default_texture_filter(baseItem, RenderingServer.CANVAS_ITEM_TEXTURE_FILTER_NEAREST)
	RenderingServer.canvas_item_add_texture_rect(baseItem, Rect2(0, 0, cols * t, rows * t), baseTex.get_rid())
	# The TS's last row/column of rects reach one pixel past the grid.
	RenderingServer.canvas_item_add_texture_rect_region(baseItem, Rect2(cols * t, 0, 1, rows * t), baseTex.get_rid(), Rect2(cols - 1, 0, 1, rows))
	RenderingServer.canvas_item_add_texture_rect_region(baseItem, Rect2(0, rows * t, cols * t + 1, 1), baseTex.get_rid(), Rect2(0, rows - 1, cols, 1))

	# --- 2. Biome layers painted as organic blobs ---------------------------
	# Interior cells fill solid; boundary cells grow 2-3 round lobes so edges
	# wander naturally instead of stair-stepping.
	var ctxA := Ctx.new()
	ctxA.begin(layer.item(), W, H)
	layer.keep(ctxA)
	var biomes := [
		[Terrain.GrassDark, PAL.grassDark, 0.12],
		[Terrain.Dirt, PAL.dirt, 0.14],
		[Terrain.Sand, PAL.sand, 0.1],
		[Terrain.Snow, "#dfe7f0", 0.08],
		[Terrain.Marsh, "#4d5a3c", 0.16],
		[Terrain.Shallow, "#4a7f96", 0.1],
		# Woodland floor: darker, mottled ground under the trees, so a wood
		# reads as a place rather than a scatter of trunks on a lawn.
		[Terrain.Forest, "#2f4429", 0.2],
		# High ground gets a lit crown and a shaded skirt, which is the cheapest
		# way to say "this is raised" on a flat top-down map.
		[Terrain.Hill, "#6d8a4c", 0.12],
	]
	for bm in biomes:
		var ter: int = bm[0]
		var col = bm[1]
		var k: float = bm[2]
		for cy in rows:
			for cx in cols:
				if _at(terr, cols, rows, cx, cy) != ter:
					continue
				var interior := _at(terr, cols, rows, cx - 1, cy) == ter and _at(terr, cols, rows, cx + 1, cy) == ter \
					and _at(terr, cols, rows, cx, cy - 1) == ter and _at(terr, cols, rows, cx, cy + 1) == ter
				ctxA.fillStyle = Palette.shade(col, shadeOf[cy * cols + cx] * k)
				if interior:
					ctxA.fillRect(cx * t - 1, cy * t - 1, t + 2, t + 2)
				else:
					var mx := cx * t + t / 2
					var my := cy * t + t / 2
					ctxA.beginPath()
					ctxA.arc(mx, my, t * 0.66, 0, PI * 2)
					var ax := mx + rng.range(-t * 0.45, t * 0.45)
					var ay := my + rng.range(-t * 0.45, t * 0.45)
					ctxA.moveTo(ax + t * 0.5, ay)
					ctxA.arc(ax, ay, t * 0.5, 0, PI * 2)
					var bx := mx + rng.range(-t * 0.45, t * 0.45)
					var by := my + rng.range(-t * 0.45, t * 0.45)
					ctxA.moveTo(bx + t * 0.42, by)
					ctxA.arc(bx, by, t * 0.42, 0, PI * 2)
					ctxA.fill()

	# Relief and water are painted per pixel from smooth fields — see
	# paintReliefAndWater below. It used to be a circle per hill cell and a
	# square per water cell, which is exactly the tile grid those were meant to
	# hide: hills read as a quilt of discs and every river as a staircase.
	var shore := paintReliefAndWater(layer, map, t, W, H)
	out.shore = shore
	var wetAt := func(px: float, py: float) -> bool:
		return sampleField(shore, cols, rows, px / t, py / t) >= 0.46

	var ctx := Ctx.new()
	ctx.begin(layer.item(), W, H)
	layer.keep(ctx)

	# Mountains: a shaded mass with a lit north face, drawn per cell so a ridge
	# reads as one range instead of a row of identical lumps.
	for cy in rows:
		for cx in cols:
			if _at(terr, cols, rows, cx, cy) != Terrain.Rock:
				continue
			# Not every cell gets a peak, and no two peaks are the same size or
			# sit on the same spot in their cell. One identical cone per cell on
			# a regular grid read as wallpaper, not a mountain range — and the
			# hillshading underneath now carries the mass, so the peaks only
			# have to punctuate it.
			var interiorRock := _at(terr, cols, rows, cx - 1, cy) == Terrain.Rock and _at(terr, cols, rows, cx + 1, cy) == Terrain.Rock \
				and _at(terr, cols, rows, cx, cy - 1) == Terrain.Rock and _at(terr, cols, rows, cx, cy + 1) == Terrain.Rock
			if interiorRock and rng.bool_(0.35):
				continue
			var k := rng.range(0.72, 1.18)
			var mx := cx * t + t / 2 + rng.range(-t * 0.28, t * 0.28)
			var my := cy * t + t / 2 + rng.range(-t * 0.22, t * 0.22)
			ctx.save()
			ctx.translate(mx, my)
			ctx.scale(k, k)
			ctx.translate(-mx, -my)
			ctx.fillStyle = "rgba(10,10,12,0.5)"
			ctx.beginPath(); ctx.ellipse(mx, my + t * 0.3, t * 0.72, t * 0.42, 0, 0, PI * 2); ctx.fill()
			var g := ctx.createLinearGradient(mx, my - t * 0.7, mx, my + t * 0.6)
			g.addColorStop(0, Palette.shade("#9aa0a8", rng.range(-0.05, 0.08)))
			g.addColorStop(1, "#3c4046")
			ctx.fillStyle = g
			ctx.beginPath()
			ctx.moveTo(mx - t * 0.72, my + t * 0.5)
			ctx.lineTo(mx - t * 0.4, my - t * 0.45 + rng.range(-t * 0.12, t * 0.12))
			ctx.lineTo(mx + t * 0.05, my - t * 0.78 + rng.range(-t * 0.14, t * 0.14))
			ctx.lineTo(mx + t * 0.45, my - t * 0.3 + rng.range(-t * 0.12, t * 0.12))
			ctx.lineTo(mx + t * 0.72, my + t * 0.5)
			ctx.closePath(); ctx.fill()
			# Snowline / lit edge.
			ctx.fillStyle = "rgba(232,238,246,0.5)"
			ctx.beginPath()
			ctx.moveTo(mx + t * 0.05, my - t * 0.78)
			ctx.lineTo(mx - t * 0.16, my - t * 0.42)
			ctx.lineTo(mx + t * 0.24, my - t * 0.4)
			ctx.closePath(); ctx.fill()
			ctx.restore()

	# --- 3/4. Shore and water are painted per pixel above. Only the static
	# wave strokes remain here; the live glints animate on top at runtime.
	for cy in rows:
		for cx in cols:
			if not _isWater(terr, cols, rows, cx, cy):
				continue
			var deep := _isWater(terr, cols, rows, cx - 1, cy) and _isWater(terr, cols, rows, cx + 1, cy) \
				and _isWater(terr, cols, rows, cx, cy - 1) and _isWater(terr, cols, rows, cx, cy + 1)
			if not deep or not rng.bool_(0.2):
				continue
			ctx.strokeStyle = Palette.withAlpha("#dff2ff", 0.2)
			ctx.lineWidth = 1
			var wx := cx * t + rng.range(t * 0.2, t * 0.8)
			var wy := cy * t + rng.range(t * 0.2, t * 0.8)
			ctx.beginPath()
			ctx.arc(wx, wy, rng.range(2, 4), PI * 0.15, PI * 0.85)
			ctx.stroke()

	# --- 5. Large soft light/shadow patches break up the flatness -----------
	var patches := 16
	for i in patches:
		var px := rng.range(0, W)
		var py := rng.range(0, H)
		var pr := rng.range(t * 8, t * 18)
		var light := rng.bool_(0.5)
		var g := ctx.createRadialGradient(px, py, 0, px, py, pr)
		g.addColorStop(0, Palette.withAlpha("#ffffff" if light else "#1c3a14", 0.05 if light else 0.07))
		g.addColorStop(1, Palette.withAlpha("#ffffff", 0))
		ctx.fillStyle = g
		ctx.beginPath()
		ctx.arc(px, py, pr, 0, PI * 2)
		ctx.fill()

	# --- 6. Ground dressing: tuft clusters, flowers, pebbles ----------------
	var clusters := int(floor(float(cols * rows) / 26.0))
	var blooms := ["#f4f0ff", "#ffd9e8", "#f7e07a", "#e8907a"]
	for i in clusters:
		var cx := rng.int_(0, cols - 1)
		var cy := rng.int_(0, rows - 1)
		var ter := _at(terr, cols, rows, cx, cy)
		if ter == Terrain.Water:
			continue
		var baseX := cx * t + rng.range(0, t)
		var baseY := cy * t + rng.range(0, t)
		if ter == Terrain.Dirt or ter == Terrain.Sand:
			# Pebble cluster.
			var n := rng.int_(2, 5)
			for k in n:
				var px := baseX + rng.range(-t * 0.8, t * 0.8)
				var py := baseY + rng.range(-t * 0.8, t * 0.8)
				if wetAt.call(px, py):
					continue
				ctx.fillStyle = Palette.shade(PAL.stone if rng.bool_(0.3) else PAL.dirtDark, rng.range(-0.12, 0.12))
				ctx.beginPath()
				ctx.ellipse(px, py, rng.range(0.9, 2.1), rng.range(0.7, 1.5), rng.range(0, 3), 0, PI * 2)
				ctx.fill()
				ctx.fillStyle = Palette.withAlpha("#ffffff", 0.18)
				ctx.beginPath()
				ctx.arc(px - 0.4, py - 0.5, 0.5, 0, PI * 2)
				ctx.fill()
		elif rng.bool_(0.3):
			# Flower cluster — tiny colored blooms with bright centres.
			var bloom: String = blooms[rng.int_(0, 3)]
			var n := rng.int_(2, 5)
			for k in n:
				var px := baseX + rng.range(-t * 0.7, t * 0.7)
				var py := baseY + rng.range(-t * 0.7, t * 0.7)
				if wetAt.call(px, py):
					continue
				ctx.fillStyle = bloom
				for p2 in 4:
					var a := (p2 / 4.0) * PI * 2 + rng.range(0, 0.6)
					ctx.beginPath()
					ctx.arc(px + cos(a) * 1.1, py + sin(a) * 1.1, 0.9, 0, PI * 2)
					ctx.fill()
				ctx.fillStyle = "#f2c14b"
				ctx.beginPath()
				ctx.arc(px, py, 0.7, 0, PI * 2)
				ctx.fill()
		else:
			# Grass tuft cluster — small bent blades, varied greens.
			var n := rng.int_(3, 7)
			for k in n:
				var px := baseX + rng.range(-t * 0.9, t * 0.9)
				var py := baseY + rng.range(-t * 0.9, t * 0.9)
				# Clusters scatter up to a cell from where they were seeded, which
				# used to plant grass in the shallows of every river.
				if wetAt.call(px, py):
					continue
				ctx.strokeStyle = Palette.shade(PAL.grassShade if rng.bool_() else PAL.grassDark, rng.range(-0.06, 0.14))
				ctx.lineWidth = 1
				var h := rng.range(2.2, 5)
				var lean := rng.range(-2, 2)
				ctx.beginPath()
				ctx.moveTo(px, py)
				ctx.quadraticCurveTo(px + lean * 0.4, py - h * 0.6, px + lean, py - h)
				ctx.stroke()

	layer.render()
	return out


static func _at(terr: PackedByteArray, cols: int, rows: int, cx: int, cy: int) -> int:
	if cx >= 0 and cy >= 0 and cx < cols and cy < rows:
		return terr[cy * cols + cx]
	return Terrain.Grass


static func _isWater(terr: PackedByteArray, cols: int, rows: int, cx: int, cy: int) -> bool:
	return _at(terr, cols, rows, cx, cy) == Terrain.Water


## Small terrain image for the minimap background: a `size`×`size` layer
## (texture `layer.tex`), one nearest-filtered cell colour per cell.
static func buildMinimapBase(map: Dictionary, size: int) -> GpuLayer:
	var PAL: Dictionary = C.PAL
	var cols: int = map.cols
	var rows: int = map.rows
	var terr: PackedByteArray = map.terrain
	var rng := RNG.new(int(map.seed) ^ 0xfeed)
	var noise := makeNoise(rng, cols, rows, 6)
	var img := Image.create(cols, rows, false, Image.FORMAT_RGBA8)
	for cy in rows:
		for cx in cols:
			var ter: int = terr[cy * cols + cx]
			var base
			match ter:
				Terrain.Water: base = PAL.water
				Terrain.Dirt: base = PAL.dirt
				Terrain.Sand: base = PAL.sand
				Terrain.Rock: base = "#6a6f76"
				Terrain.Hill: base = "#7f9c5b"
				Terrain.Forest: base = "#2f4429"
				Terrain.Marsh: base = "#4d5a3c"
				Terrain.Snow: base = "#dfe7f0"
				Terrain.Shallow: base = "#4a7f96"
				Terrain.GrassDark: base = PAL.grassDark
				_: base = PAL.grass
			img.set_pixel(cx, cy, Palette.shade(base, (noise.at(cx, cy) - 0.5) * 0.12))
	var layer := GpuLayer.new(size, size, false)
	var tex := ImageTexture.create_from_image(img)
	layer.keep(tex)
	var it := layer.item()
	RenderingServer.canvas_item_set_default_texture_filter(it, RenderingServer.CANVAS_ITEM_TEXTURE_FILTER_NEAREST)
	RenderingServer.canvas_item_add_texture_rect(it, Rect2(0, 0, size, size), tex.get_rid())
	layer.render()
	return layer


# ------------------------------------------------------ relief and water --

static func smoothstep(a: float, b: float, x: float) -> float:
	var tt := clampf((x - a) / (b - a), 0.0, 1.0)
	return tt * tt * (3 - 2 * tt)


## Bilinear sample of a per-cell field at cell-space (u, v), where a cell's
## centre sits at (cx + 0.5, cy + 0.5). Clamped at the map edge.
static func sampleField(f: PackedFloat32Array, cols: int, rows: int, u: float, v: float) -> float:
	var x := maxf(0.0, minf(cols - 1.001, u - 0.5))
	var y := maxf(0.0, minf(rows - 1.001, v - 0.5))
	var x0 := int(x)
	var y0 := int(y)
	var fx := x - x0
	var fy := y - y0
	var i := y0 * cols + x0
	var a: float = f[i]
	var b: float = f[i + 1]
	var c: float = f[i + cols]
	var d: float = f[i + cols + 1]
	return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


## The smooth fields the relief/water pass works from, per cell (TS
## paintReliefAndWater's first half). Returns {shore, beach, depth, height,
## lit, shallow, busy}.
static func reliefFields(map: Dictionary) -> Dictionary:
	var cols: int = map.cols
	var rows: int = map.rows
	var n := cols * rows
	var terr: PackedByteArray = map.terrain
	var raw := PackedFloat32Array()  # 1 = water
	raw.resize(n)
	var elev := PackedFloat32Array()  # height: hills, then mountains higher
	elev.resize(n)
	var shallow := PackedByteArray()
	shallow.resize(n)
	for i in n:
		var ter: int = terr[i]
		if ter == Terrain.Water:
			raw[i] = 1
		if ter == Terrain.Shallow:
			shallow[i] = 1
		elev[i] = 1.0 if ter == Terrain.Hill else (1.7 if ter == Terrain.Rock else 0.0)
	var scratch := PackedFloat32Array()
	scratch.resize(n)
	# The shoreline: a smooth field, pinned so every cell centre keeps the side
	# of 0.5 the sim puts it on. Bilinear interpolation passes exactly through
	# the cell centres, so the pinned field can round a coast as much as the
	# blur likes without ever drying a water cell or flooding a land one — a
	# lone pond stays a pond, a one-cell channel stays open, a one-cell island
	# stays an island. Pinning to 0.6 / 0.4 rather than 0.5 keeps them visible.
	var shore := raw.duplicate()
	FogBlur.blurMask(shore, scratch, cols, rows, 1)
	FogBlur.blurMask(shore, scratch, cols, rows, 1)
	# How far sand reaches inland comes from the same blur taken one step
	# further and left unpinned, so a beach rounds off instead of following the
	# waterline into every corner — a sandbar's grassy middle used to come out
	# as a star.
	var beach := shore.duplicate()
	FogBlur.blurMask(beach, scratch, cols, rows, 1)
	for i in n:
		shore[i] = maxf(shore[i], 0.6) if raw[i] != 0.0 else minf(shore[i], 0.4)
	var depth := raw.duplicate()
	FogBlur.blurMask(depth, scratch, cols, rows, 2)
	FogBlur.blurMask(depth, scratch, cols, rows, 2)
	var height := elev.duplicate()
	FogBlur.blurMask(height, scratch, cols, rows, 1)
	FogBlur.blurMask(height, scratch, cols, rows, 1)

	# Light from the north-west and a little above: the convention every map
	# reader expects, and the one that makes hills read as raised, not sunken.
	#
	# The shade is worked out once per *cell*, from central differences of the
	# height field, and only then interpolated across the pixels. Taking the
	# slope per pixel from a bilinear height field looks equivalent and isn't:
	# bilinear height has a slope that jumps at every cell boundary, and the
	# first version of this painted each hill as a stack of flat facets — the
	# tile grid again, just in shading instead of colour.
	var Lx := -0.55
	var Ly := -0.68
	var Lz := 0.48
	var Ln := sqrt(Lx * Lx + Ly * Ly + Lz * Lz)
	var lx := Lx / Ln
	var ly := Ly / Ln
	var lz := Lz / Ln
	var RELIEF := 1.6  # how steep a hill's flank reads
	var lit := PackedFloat32Array()
	lit.resize(n)
	for cy in rows:
		for cx in cols:
			var i := cy * cols + cx
			var l: float = height[cy * cols + maxi(0, cx - 1)]
			var rr: float = height[cy * cols + mini(cols - 1, cx + 1)]
			var u: float = height[maxi(0, cy - 1) * cols + cx]
			var dd: float = height[mini(rows - 1, cy + 1) * cols + cx]
			var nx := -(rr - l) * 0.5 * RELIEF
			var ny := -(dd - u) * 0.5 * RELIEF
			lit[i] = (nx * lx + ny * ly + lz) / sqrt(nx * nx + ny * ny + 1.0) - lz

	# Cells worth touching at all: near water, or anywhere the light changes.
	# A pixel's value is interpolated from its own cell and the ring around
	# it, so a cell counts if anything in that ring does — gating on the cell
	# alone left a hard edge wherever a hill's shadow reached one cell further
	# than its height did.
	var RELIEF_NEAR := 1
	var SHORE_NEAR := 2
	var busy := PackedByteArray()
	busy.resize(n)
	for cy in rows:
		for cx in cols:
			var m := 0
			for dy in range(-1, 2):
				for dx in range(-1, 2):
					var nx := cx + dx
					var ny := cy + dy
					if nx < 0 or ny < 0 or nx >= cols or ny >= rows:
						continue
					var j := ny * cols + nx
					if height[j] > 0.001 or lit[j] != 0.0:
						m |= RELIEF_NEAR
					if shore[j] > 0.001 or beach[j] > 0.001:
						m |= SHORE_NEAR
			busy[cy * cols + cx] = m
	return {"shore": shore, "beach": beach, "depth": depth, "height": height, "lit": lit, "shallow": shallow, "busy": busy}


## Water, shoreline and hillshading, painted per pixel from smooth fields.
##
## Runs once when the map loads, so it can afford to be thorough — nothing here
## costs anything per frame. It replaces a square per water cell and a circle
## per hill cell, which between them drew the tile grid the rest of the painter
## works hard to hide: every river a staircase of two-tone blue blocks, every
## hill a quilt of discs.
##
## **The outline of the water has to agree with the sim.** Water is impassable,
## so a picture that shows land where the pathfinder sees water is a lie with
## consequences. A plain blur would round the coast beautifully and also erase
## any channel one cell wide or any single-cell pond. The outline therefore
## comes from bilinear interpolation of the *raw* cells — marching-squares
## style, which keeps every water cell and a one-cell channel exactly one cell
## wide, but turns a staircase into a straight diagonal — nudged by a light blur
## for roundness. The heavier blur is used only for things that cannot change
## topology: how deep the water looks and how the ground is lit.
##
## **Hills are lit, not tinted.** High ground becomes a height field, blurred
## so it rolls, and each pixel is shaded by how its slope faces a light from
## the north-west. That is the standard cartographic hillshade, and it is what
## makes a ridge read as a ridge instead of a patch of darker grass.
##
## Godot: the fields are computed here per cell, uploaded as two float
## textures, and the TS's per-pixel loop is RELIEF_SHADER, run once over the
## whole cache as one quad laid over the ground (the TS's overlay blit).
## Returns the pinned shore field (for wetAt).
static func paintReliefAndWater(layer: GpuLayer, map: Dictionary, t: float, W: int, H: int) -> PackedFloat32Array:
	var cols: int = map.cols
	var rows: int = map.rows
	var n := cols * rows
	var f := reliefFields(map)
	var shore: PackedFloat32Array = f.shore
	var beach: PackedFloat32Array = f.beach
	var depth: PackedFloat32Array = f.depth
	var height: PackedFloat32Array = f.height
	var lit: PackedFloat32Array = f.lit
	var shallow: PackedByteArray = f.shallow
	var busy: PackedByteArray = f.busy
	var a := PackedFloat32Array()
	a.resize(n * 4)
	var b := PackedFloat32Array()
	b.resize(n * 4)
	for i in n:
		var o := i * 4
		a[o] = shore[i]
		a[o + 1] = beach[i]
		a[o + 2] = depth[i]
		a[o + 3] = lit[i]
		b[o] = height[i]
		b[o + 1] = float(shallow[i])
		b[o + 2] = 1.0 if busy[i] & 1 else 0.0
		b[o + 3] = 1.0 if busy[i] & 2 else 0.0
	var ta := ImageTexture.create_from_image(Image.create_from_data(cols, rows, false, Image.FORMAT_RGBAF, a.to_byte_array()))
	var tb := ImageTexture.create_from_image(Image.create_from_data(cols, rows, false, Image.FORMAT_RGBAF, b.to_byte_array()))
	var PAL: Dictionary = C.PAL
	var mat := ShaderMaterial.new()
	mat.shader = _relief_shader()
	mat.set_shader_parameter("fieldA", ta)
	mat.set_shader_parameter("fieldB", tb)
	mat.set_shader_parameter("size_px", Vector2(W, H))
	mat.set_shader_parameter("cell_px", t)
	mat.set_shader_parameter("grid", Vector2i(cols, rows))
	mat.set_shader_parameter("waterEdge", _rgb255(PAL.waterEdge))
	mat.set_shader_parameter("water", _rgb255(PAL.water))
	mat.set_shader_parameter("waterDeep", _rgb255(PAL.waterDeep))
	mat.set_shader_parameter("sand", _rgb255(PAL.sand))
	layer.keep(ta)
	layer.keep(tb)
	layer.keep(mat)
	var it := layer.item()
	RenderingServer.canvas_item_set_material(it, mat.get_rid())
	RenderingServer.canvas_item_add_texture_rect(it, Rect2(0, 0, W, H), ta.get_rid())
	return shore


static func _rgb255(hex: String) -> Vector3:
	var c: Color = Ctx.parse_color(hex)
	return Vector3(c.r8, c.g8, c.b8)


static var _relief: Shader = null


static func _relief_shader() -> Shader:
	if _relief == null:
		_relief = Shader.new()
		_relief.code = RELIEF_SHADER
	return _relief


## TS paintReliefAndWater's pixel loop. Colours are 0..255 as in the TS and
## divided at the end; the fields are fetched per texel and interpolated with
## the TS's own clamped bilinear weights.
const RELIEF_SHADER := """
shader_type canvas_item;
uniform sampler2D fieldA : filter_nearest; // shore, beach, depth, lit
uniform sampler2D fieldB : filter_nearest; // height, shallow, busy relief, busy shore
uniform vec2 size_px;
uniform float cell_px;
uniform ivec2 grid;
uniform vec3 waterEdge;
uniform vec3 water;
uniform vec3 waterDeep;
uniform vec3 sand;

float sstep(float a, float b, float x) {
	float t = clamp((x - a) / (b - a), 0.0, 1.0);
	return t * t * (3.0 - 2.0 * t);
}

int owner(int p, int n) {
	// The cell whose pixel span [floor(c*t), floor((c+1)*t)) holds pixel p.
	int c = int(floor(float(p) / cell_px));
	if (floor(float(c + 1) * cell_px) <= float(p)) c += 1;
	if (c > 0 && floor(float(c) * cell_px) > float(p)) c -= 1;
	return c;
}

void fragment() {
	vec2 pc = UV * size_px; // pixel centre: (px + 0.5, py + 0.5)
	int px = int(floor(pc.x));
	int py = int(floor(pc.y));
	int cols = grid.x;
	int rows = grid.y;
	int cx = owner(px, cols);
	int cy = owner(py, rows);
	COLOR = vec4(0.0);
	if (cx >= 0 && cy >= 0 && cx < cols && cy < rows) {
		vec4 own = texelFetch(fieldB, ivec2(cx, cy), 0);
		bool relief = own.z > 0.5;
		bool near = own.w > 0.5;
		bool ownShallow = own.y > 0.5;
		if (relief || near) {
			float x = clamp(pc.x / cell_px - 0.5, 0.0, float(cols) - 1.001);
			float y = clamp(pc.y / cell_px - 0.5, 0.0, float(rows) - 1.001);
			int x0 = int(x);
			int y0 = int(y);
			float fx = x - float(x0);
			float fy = y - float(y0);
			float w00 = (1.0 - fx) * (1.0 - fy), w10 = fx * (1.0 - fy), w01 = (1.0 - fx) * fy, w11 = fx * fy;
			vec4 A = texelFetch(fieldA, ivec2(x0, y0), 0) * w00 + texelFetch(fieldA, ivec2(x0 + 1, y0), 0) * w10
				+ texelFetch(fieldA, ivec2(x0, y0 + 1), 0) * w01 + texelFetch(fieldA, ivec2(x0 + 1, y0 + 1), 0) * w11;
			float hc = texelFetch(fieldB, ivec2(x0, y0), 0).x * w00 + texelFetch(fieldB, ivec2(x0 + 1, y0), 0).x * w10
				+ texelFetch(fieldB, ivec2(x0, y0 + 1), 0).x * w01 + texelFetch(fieldB, ivec2(x0 + 1, y0 + 1), 0).x * w11;
			// --- water and shore ---
			float c = near ? A.x : 0.0;
			if (c >= 0.5) {
				float deep = sstep(0.45, 0.95, A.z);
				// edge -> body -> deep, so a lake has a lit rim and a dark heart.
				vec3 w2 = deep < 0.5 ? mix(waterEdge, water, deep / 0.5) : mix(water, waterDeep, (deep - 0.5) / 0.5);
				// A thin line of foam where water meets land.
				float foam = 1.0 - sstep(0.5, 0.58, c);
				vec3 o = w2 + (vec3(232.0, 244.0, 246.0) - w2) * foam * 0.55;
				COLOR = vec4(clamp(o, 0.0, 255.0) / 255.0, 1.0);
			} else {
				// Paint laid over the ground, accumulated: the result is
				// ground * keep + ar.
				float keep = 1.0;
				vec3 ar = vec3(0.0);
				// --- hillshade ---
				if (relief) {
					float s = A.w;
					if (s > 0.001) {
						// A sunlit flank mixes toward warm light rather than being
						// scaled up: multiplying grass by 1.3 makes it neon, not bright.
						float a = min(0.4, s * 0.8);
						ar = ar * (1.0 - a) + vec3(250.0, 236.0, 188.0) * a; keep *= 1.0 - a;
					} else if (s < -0.001) {
						// Shade darkens and cools, as it does outdoors — a pure
						// brightness change reads as dirt, not slope.
						float a = min(0.8, -s * 1.1);
						ar = ar * (1.0 - a) + vec3(16.0, 26.0, 44.0) * a; keep *= 1.0 - a;
					}
					if (hc > 0.002) {
						// Tops a touch drier and lighter, so the crest of a range stands out.
						float a = min(1.0, hc) * 0.05;
						ar = ar * (1.0 - a) + vec3(235.0, 230.0, 190.0) * a; keep *= 1.0 - a;
					}
				}
				// Sand, strongest right at the waterline, fading inland over about
				// a cell, and darker where it's wet in the last stretch.
				if (near && !ownShallow) {
					float bc = A.y;
					float a = max(sstep(0.3, 0.5, c), sstep(0.1, 0.4, bc)) * 0.88;
					if (a > 0.0) {
						ar = ar * (1.0 - a) + sand * a; keep *= 1.0 - a;
						float wet = sstep(0.4, 0.5, c) * 0.18;
						if (wet > 0.0) { ar *= 1.0 - wet; keep *= 1.0 - wet; }
					}
				}
				float alpha = 1.0 - keep;
				if (alpha > 0.002) {
					COLOR = vec4(clamp(ar / alpha, 0.0, 255.0) / 255.0, alpha);
				}
			}
		}
	}
}
"""


# --------------------------------------------------------- ground detail --

## Cheap deterministic hash per cell, so detail never shimmers between frames.
static func cellHash(cx: int, cy: int, n: int) -> float:
	var v := JS.i32(cx * 374761393 + cy * 668265263 + n * 2246822519)
	v = JS.imul(v ^ JS.ushr(v, 13), 1274126177)
	return float(JS.u32(v ^ JS.ushr(v, 16))) / 4294967296.0


## Crisp ground detail, drawn in world space over the blitted cache.
##
## The terrain cache is baked at half resolution, which is right for the zoom
## you fight at but turns to soft green mush the moment you lean in — at 1.6×
## the cache is magnified over three times and every tuft and pebble baked into
## it is a smear. Rather than bake a bigger texture (the cache already has to
## stay under Safari's canvas limit on the largest maps), the close-up detail is
## drawn fresh each frame for the handful of cells actually on screen.
##
## It fades in with zoom so nothing pops, and is skipped entirely when zoomed
## out — where it would be sub-pixel noise costing thousands of draws a frame.
##
## This is the TS function, line for line, drawing through a Ctx. The
## renderer draws the same marks from GroundDetail's prebuilt meshes instead
## (below); this live form is the reference they are checked against.
static func drawGroundDetail(ctx: Ctx, map: Dictionary, vx0: float, vy0: float, vx1: float, vy1: float, zoom: float, time: float) -> void:
	# Detail exists to rescue the close-up view: the cache is baked at half
	# resolution, so it only turns to mush once you lean in past about 0.85.
	# Below that the marks would be sub-pixel noise costing real time — and the
	# cost is *worse* zoomed out, because a wider view holds more cells, which is
	# exactly backwards from where the detail is wanted.
	var strength := minf(1, (zoom - 0.85) / 0.45)
	if strength <= 0.02:
		return
	var TILE: float = C.TILE
	var cols: int = map.cols
	var rows: int = map.rows
	var terr: PackedByteArray = map.terrain
	var c0x := maxi(0, int(floor(vx0 / TILE)))
	var c0y := maxi(0, int(floor(vy0 / TILE)))
	var c1x := mini(cols - 1, int(ceil(vx1 / TILE)))
	var c1y := mini(rows - 1, int(ceil(vy1 / TILE)))
	if c1x < c0x or c1y < c0y:
		return
	if (c1x - c0x + 1) * (c1y - c0y + 1) > 3000:
		return

	var PAL: Dictionary = C.PAL
	ctx.save()
	ctx.globalAlpha = strength
	ctx.lineCap = "round"
	ctx.lineWidth = 1.3

	var GRASS_TONES := [PAL.grassShade, PAL.grassDark, "#84bd57"]

	for cy in range(c0y, c1y + 1):
		for cx in range(c0x, c1x + 1):
			var t: int = terr[cy * cols + cx]
			var ox := cx * TILE
			var oy := cy * TILE
			match t:
				Terrain.Hill:
					# The relief itself is baked into the cache as hillshading;
					# this only adds the dry upland grass on top. Dry upland
					# tufts — straight, short, and fewer than on meadow.
					ctx.strokeStyle = "rgba(147,173,99,0.6)"
					ctx.beginPath()
					for i in 3:
						var px := ox + cellHash(cx, cy, i) * TILE
						var py := oy + cellHash(cx, cy, i + 7) * TILE
						ctx.moveTo(px, py)
						ctx.lineTo(px + 1, py - 3.5)
					ctx.stroke()
				Terrain.Grass, Terrain.GrassDark:
					# One path per cell, three tones interleaved: enough variation
					# to read as meadow without a stroke call per blade.
					var sway := sin(time * 0.7 + cx * 0.4 + cy * 0.3) * 1.1
					for tone in 3:
						ctx.strokeStyle = Palette.withAlpha(GRASS_TONES[tone], 0.72)
						ctx.beginPath()
						for i in range(tone, 4, 3):
							var px := ox + cellHash(cx, cy, i) * TILE
							var py := oy + cellHash(cx, cy, i + 7) * TILE
							var hgt := 3 + cellHash(cx, cy, i + 13) * 3.5
							ctx.moveTo(px, py)
							ctx.lineTo(px + sway, py - hgt)
						ctx.stroke()
				Terrain.Forest:
					# Crowns are *lighter* than the forest floor the cache paints.
					# Dark on dark was why the first attempt at this was invisible
					# even though it was drawing: a wood from above is lit tops
					# over shadowed gaps, not a uniform dark disc.
					for i in 3:
						var px := ox + 7 + cellHash(cx, cy, i) * (TILE - 14)
						var py := oy + 7 + cellHash(cx, cy, i + 3) * (TILE - 14)
						var r := 6.5 + cellHash(cx, cy, i + 11) * 3
						ctx.fillStyle = "rgba(22,36,15,0.72)"
						ctx.beginPath(); ctx.arc(px + 1, py + 2.5, r, 0, PI * 2); ctx.fill()
						ctx.fillStyle = Palette.withAlpha(Palette.shade(PAL.foliage3, cellHash(cx, cy, i + 5) * 0.2 - 0.04), 0.96)
						ctx.beginPath(); ctx.arc(px, py - 1, r * 0.86, 0, PI * 2); ctx.fill()
						ctx.fillStyle = "rgba(143,199,106,0.4)"
						ctx.beginPath(); ctx.arc(px - r * 0.32, py - r * 0.45, r * 0.3, 0, PI * 2); ctx.fill()
				Terrain.Rock:
					ctx.fillStyle = "rgba(181,175,163,0.5)"
					ctx.beginPath()
					for i in 3:
						var px := ox + 4 + cellHash(cx, cy, i) * (TILE - 8)
						var py := oy + 4 + cellHash(cx, cy, i + 7) * (TILE - 8)
						var r := 2 + cellHash(cx, cy, i + 13) * 3
						ctx.moveTo(px - r, py + r * 0.6)
						ctx.lineTo(px - r * 0.3, py - r)
						ctx.lineTo(px + r, py + r * 0.2)
						ctx.closePath()
					ctx.fill()
				Terrain.Dirt, Terrain.Sand, Terrain.Snow:
					ctx.fillStyle = "rgba(255,255,255,0.45)" if t == Terrain.Snow \
						else ("rgba(181,175,163,0.4)" if t == Terrain.Sand else "rgba(148,119,77,0.45)")
					ctx.beginPath()
					for i in 2:
						var px := ox + cellHash(cx, cy, i) * TILE
						var py := oy + cellHash(cx, cy, i + 7) * TILE
						var r := 0.9 + cellHash(cx, cy, i + 13) * 1.3
						ctx.moveTo(px + r, py)
						ctx.arc(px, py, r, 0, PI * 2)
					ctx.fill()
				Terrain.Marsh:
					var sway := sin(time * 1.1 + cx * 0.5) * 2
					ctx.strokeStyle = "rgba(109,122,74,0.55)"
					ctx.lineWidth = 1
					ctx.beginPath()
					for i in 3:
						var px := ox + cellHash(cx, cy, i) * TILE
						var py := oy + cellHash(cx, cy, i + 7) * TILE
						var hgt := 5 + cellHash(cx, cy, i + 13) * 4
						ctx.moveTo(px, py)
						ctx.lineTo(px + sway, py - hgt)
					ctx.stroke()
					ctx.lineWidth = 1.3
				_:
					pass # water and shallows have their own glints
	ctx.restore()


## The ground detail of one map, built into meshes a chunk at a time and
## drawn as retained canvas items (TS drawGroundDetail, per frame, made free).
##
## The same marks as drawGroundDetail — the same cells, hashes, sizes and
## colours — but each blade is one quad and each disc one quad, antialiased in
## the fragment shader from screen-space derivatives (a stroked blade through
## Ctx is ~30 vertices with its round caps and fringe; a filled circle ~60):
## a view of meadow is then thousands of triangles, not hundreds of thousands.
## The sway is the vertex shader shearing each blade's tip by the TS's own
## formula (the blade's cell is recovered from its root); the zoom fade
## (`strength`) is the parent item's alpha. Chunks are built the first time
## they come into view, a few per frame, so a map load doesn't pay for the
## whole map.
class GroundDetail extends RefCounted:
	const CHUNK := 12  ## cells per chunk side
	var map: Dictionary
	var root := RID()  ## parent item: world-space transform, strength as alpha
	var chunks := {}  ## Vector2i -> [blade item, disc item] (RIDs; invalid when empty)
	var meshes: Array = []
	var bladeMat: ShaderMaterial
	var discMat: ShaderMaterial
	var ccols := 0
	var crows := 0
	## Max chunks built per frame.
	var budget := 3

	func _init(m: Dictionary) -> void:
		map = m
		root = RenderingServer.canvas_item_create()
		bladeMat = ShaderMaterial.new()
		bladeMat.shader = TerrainArt._shader("blade", BLADE_SHADER)
		bladeMat.set_shader_parameter("tile", float(C.TILE))
		discMat = ShaderMaterial.new()
		discMat.shader = TerrainArt._shader("disc", DISC_SHADER)
		ccols = int(ceil(float(map.cols) / CHUNK))
		crows = int(ceil(float(map.rows) / CHUNK))

	## Make sure the chunks under this view exist; returns how many are still
	## missing after this frame's budget.
	func ensure(vx0: float, vy0: float, vx1: float, vy1: float) -> int:
		var TILE: float = C.TILE
		var k0x := maxi(0, int(floor(vx0 / TILE / CHUNK)))
		var k0y := maxi(0, int(floor(vy0 / TILE / CHUNK)))
		var k1x := mini(ccols - 1, int(floor(vx1 / TILE / CHUNK)))
		var k1y := mini(crows - 1, int(floor(vy1 / TILE / CHUNK)))
		var made := 0
		var missing := 0
		for ky in range(k0y, k1y + 1):
			for kx in range(k0x, k1x + 1):
				var key := Vector2i(kx, ky)
				if chunks.has(key):
					continue
				if made >= budget:
					missing += 1
					continue
				_build(key)
				made += 1
		return missing

	func _build(key: Vector2i) -> void:
		var c0x := key.x * CHUNK
		var c0y := key.y * CHUNK
		var b := DetailMesh.new()
		b.build(map, c0x, c0y, mini(int(map.cols) - 1, c0x + CHUNK - 1), mini(int(map.rows) - 1, c0y + CHUNK - 1))
		var items: Array = [RID(), RID()]
		var bm := b.blades.to_mesh()
		if bm != null:
			meshes.append(bm)
			items[0] = _item(bm, bladeMat)
		var dm := b.discs.to_mesh()
		if dm != null:
			meshes.append(dm)
			items[1] = _item(dm, discMat)
		chunks[key] = items

	func _item(m: ArrayMesh, mat: ShaderMaterial) -> RID:
		var it := RenderingServer.canvas_item_create()
		RenderingServer.canvas_item_set_parent(it, root)
		RenderingServer.canvas_item_set_material(it, mat.get_rid())
		RenderingServer.canvas_item_add_mesh(it, m.get_rid())
		return it

	func set_time(time: float) -> void:
		bladeMat.set_shader_parameter("time", time)

	func free_all() -> void:
		for k in chunks:
			for it in chunks[k]:
				if (it as RID).is_valid():
					RenderingServer.free_rid(it)
		chunks.clear()
		meshes.clear()
		if root.is_valid():
			RenderingServer.free_rid(root)
		root = RID()

	func _notification(what: int) -> void:
		if what == NOTIFICATION_PREDELETE:
			# (Methods can't be called this late; free the RIDs inline.)
			for k in chunks:
				for it in chunks[k]:
					if (it as RID).is_valid():
						RenderingServer.free_rid(it)
			chunks.clear()
			if root.is_valid():
				RenderingServer.free_rid(root)


## Geometry under construction: vertices, colours, UVs, indices.
class MeshParts extends RefCounted:
	var verts := PackedVector2Array()
	var cols := PackedColorArray()
	var uvs := PackedVector2Array()
	var idx := PackedInt32Array()

	func quad(p: Array, uv: Array, col: Color) -> void:
		var b := verts.size()
		for i in 4:
			verts.append(p[i])
			uvs.append(uv[i])
			cols.append(col)
		idx.append_array([b, b + 1, b + 2, b, b + 2, b + 3])

	func tri(a: Vector2, bb: Vector2, c: Vector2, col: Color) -> void:
		var b := verts.size()
		for v in [a, bb, c]:
			verts.append(v)
			uvs.append(Vector2.ZERO)
			cols.append(col)
		idx.append_array([b, b + 1, b + 2])

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


## drawGroundDetail's marks for a block of cells, as blade and disc quads.
class DetailMesh extends RefCounted:
	var blades := MeshParts.new()
	var discs := MeshParts.new()
	const AA := 0.75  ## world units of fringe either side

	## A stroked line from root to tip, `w` wide; kind 0 grass (sways), 1
	## marsh (sways its own way), 2 hill (still). uv = (across + 10·kind, how
	## far up from the root in world units).
	func blade(rx: float, ry: float, tx: float, ty: float, w: float, kind: int, col: Color) -> void:
		var hw := w / 2
		var k := (hw + AA) / hw
		var off := hw + AA
		var up := ry - ty
		var ko := 10.0 * kind
		blades.quad(
			[Vector2(rx - off, ry), Vector2(rx + off, ry), Vector2(tx + off, ty), Vector2(tx - off, ty)],
			[Vector2(ko - k, 0), Vector2(ko + k, 0), Vector2(ko + k, up), Vector2(ko - k, up)], col)

	## A filled circle.
	func disc(cx: float, cy: float, r: float, col: Color) -> void:
		var e := r + AA * 2
		var k := e / r
		discs.quad(
			[Vector2(cx - e, cy - e), Vector2(cx + e, cy - e), Vector2(cx + e, cy + e), Vector2(cx - e, cy + e)],
			[Vector2(-k, -k), Vector2(k, -k), Vector2(k, k), Vector2(-k, k)], col)

	func build(map: Dictionary, c0x: int, c0y: int, c1x: int, c1y: int) -> void:
		var TILE: float = C.TILE
		var PAL: Dictionary = C.PAL
		var cols: int = map.cols
		var terr: PackedByteArray = map.terrain
		var grass := [Palette.withAlpha(PAL.grassShade, 0.72), Palette.withAlpha(PAL.grassDark, 0.72), Palette.withAlpha("#84bd57", 0.72)]
		var hill: Color = Ctx.parse_color("rgba(147,173,99,0.6)")
		var marsh: Color = Ctx.parse_color("rgba(109,122,74,0.55)")
		var shadowC: Color = Ctx.parse_color("rgba(22,36,15,0.72)")
		var hiC: Color = Ctx.parse_color("rgba(143,199,106,0.4)")
		var rockC: Color = Ctx.parse_color("rgba(181,175,163,0.5)")
		var snowC: Color = Ctx.parse_color("rgba(255,255,255,0.45)")
		var sandC: Color = Ctx.parse_color("rgba(181,175,163,0.4)")
		var dirtC: Color = Ctx.parse_color("rgba(148,119,77,0.45)")
		for cy in range(c0y, c1y + 1):
			for cx in range(c0x, c1x + 1):
				var t: int = terr[cy * cols + cx]
				var ox := cx * TILE
				var oy := cy * TILE
				match t:
					Terrain.Hill:
						for i in 3:
							var px := ox + TerrainArt.cellHash(cx, cy, i) * TILE
							var py := oy + TerrainArt.cellHash(cx, cy, i + 7) * TILE
							blade(px, py, px + 1, py - 3.5, 1.3, 2, hill)
					Terrain.Grass, Terrain.GrassDark:
						for tone in 3:
							for i in range(tone, 4, 3):
								var px := ox + TerrainArt.cellHash(cx, cy, i) * TILE
								var py := oy + TerrainArt.cellHash(cx, cy, i + 7) * TILE
								var hgt := 3 + TerrainArt.cellHash(cx, cy, i + 13) * 3.5
								blade(px, py, px, py - hgt, 1.3, 0, grass[tone])
					Terrain.Forest:
						for i in 3:
							var px := ox + 7 + TerrainArt.cellHash(cx, cy, i) * (TILE - 14)
							var py := oy + 7 + TerrainArt.cellHash(cx, cy, i + 3) * (TILE - 14)
							var r := 6.5 + TerrainArt.cellHash(cx, cy, i + 11) * 3
							disc(px + 1, py + 2.5, r, shadowC)
							disc(px, py - 1, r * 0.86, Palette.withAlpha(Palette.shade(PAL.foliage3, TerrainArt.cellHash(cx, cy, i + 5) * 0.2 - 0.04), 0.96))
							disc(px - r * 0.32, py - r * 0.45, r * 0.3, hiC)
					Terrain.Rock:
						for i in 3:
							var px := ox + 4 + TerrainArt.cellHash(cx, cy, i) * (TILE - 8)
							var py := oy + 4 + TerrainArt.cellHash(cx, cy, i + 7) * (TILE - 8)
							var r := 2 + TerrainArt.cellHash(cx, cy, i + 13) * 3
							discs.tri(Vector2(px - r, py + r * 0.6), Vector2(px - r * 0.3, py - r), Vector2(px + r, py + r * 0.2), rockC)
					Terrain.Dirt, Terrain.Sand, Terrain.Snow:
						var col: Color = snowC if t == Terrain.Snow else (sandC if t == Terrain.Sand else dirtC)
						for i in 2:
							var px := ox + TerrainArt.cellHash(cx, cy, i) * TILE
							var py := oy + TerrainArt.cellHash(cx, cy, i + 7) * TILE
							var r := 0.9 + TerrainArt.cellHash(cx, cy, i + 13) * 1.3
							disc(px, py, r, col)
					Terrain.Marsh:
						for i in 3:
							var px := ox + TerrainArt.cellHash(cx, cy, i) * TILE
							var py := oy + TerrainArt.cellHash(cx, cy, i + 7) * TILE
							var hgt := 5 + TerrainArt.cellHash(cx, cy, i + 13) * 4
							blade(px, py, px, py - hgt, 1.0, 1, marsh)
					_:
						pass # water and shallows have their own glints


static var _shaders := {}


static func _shader(key: String, code: String) -> Shader:
	if not _shaders.has(key):
		var sh := Shader.new()
		sh.code = code
		_shaders[key] = sh
	return _shaders[key]


## A blade: across-coordinate antialiasing (|a| = 1 at the stroke's edge), and
## the sway — grass tip.x += sin(time·0.7 + cx·0.4 + cy·0.3)·1.1, a reed's
## sin(time·1.1 + cx·0.5)·2 (drawGroundDetail's) — for the tip vertices,
## with the blade's cell found from its root.
const BLADE_SHADER := """
shader_type canvas_item;
uniform float time;
uniform float tile;
varying float across;
void vertex() {
	float kind = floor(UV.x / 10.0 + 0.5);
	across = UV.x - kind * 10.0;
	float up = UV.y;
	if (up > 0.0001 && kind < 1.5) {
		float hw = kind > 0.5 ? 0.5 : 0.65;
		float rx = VERTEX.x - across * hw;
		float ry = VERTEX.y + up;
		float cx = floor(rx / tile);
		float cy = floor(ry / tile);
		VERTEX.x += kind > 0.5 ? sin(time * 1.1 + cx * 0.5) * 2.0 : sin(time * 0.7 + cx * 0.4 + cy * 0.3) * 1.1;
	}
}
void fragment() {
	float fw = max(fwidth(across), 1e-5);
	COLOR.a *= clamp((1.0 - abs(across)) / fw + 0.5, 0.0, 1.0);
}
"""

## A disc: radial antialiasing (|uv| = 1 at the rim); plain triangles carry
## uv = 0 and fill solid.
const DISC_SHADER := """
shader_type canvas_item;
void fragment() {
	float d = length(UV);
	float fw = fwidth(d);
	if (fw > 1e-6) COLOR.a *= clamp((1.0 - d) / fw + 0.5, 0.0, 1.0);
}
"""
