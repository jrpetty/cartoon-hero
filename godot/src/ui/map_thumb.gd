## ui/map_thumb.ts — a map at a glance.
##
## The editor's library and the lobby's battlefield picker both want the same
## thing — a few dozen pixels that say what a map *is* before you commit to it —
## so the swatch table and the drawing live here rather than being duplicated
## and drifting apart.
##
## Deliberately not the real terrain renderer: that bakes a texture the size of
## the map and is far too expensive for a list of cards redrawn every frame.
## This samples on a stride and fills flat rectangles.
##
## Godot-only, under the drawing: the sampled grid (and the resource specks)
## are thousands of rectangles, which a canvas fills in a blink and Ctx does
## not, so they are filled once into a texture — supersampled, the same
## coverage the canvas's antialiased rectangles give — and that is drawn while
## the map stays the same. The spawn dots and the frame are drawn as ever.
class_name MapThumb
extends RefCounted

const Terrain = TerrainKinds.Terrain

## Flat colour per terrain — the editor palette, shared so the two agree.
const MAP_SWATCH := {
	Terrain.Grass: "#6f9c4a", Terrain.GrassDark: "#557f3c", Terrain.Dirt: "#8a6f47",
	Terrain.Sand: "#cbb383", Terrain.Snow: "#dfe7f0", Terrain.Hill: "#7f9c5b",
	Terrain.Forest: "#2f4429", Terrain.Marsh: "#4d5a3c", Terrain.Shallow: "#4a7f96",
	Terrain.Water: "#2f6a86", Terrain.Rock: "#6a6f76",
}

## Baked thumbnails by what they show (see `_baked_texture`); a handful of maps
## on screen at once, so a small cache is plenty.
static var _baked := {}
const _BAKE_LIMIT := 64
## Supersampling for the bake: coverage within a quarter pixel.
const _SS := 4


## Draw `m` (a CustomMap Dictionary) into a `size`-square box at (x,y).
## opts: { spawns?: draw a numbered dot per spawn point (off in tight rows
## where it'd be mush), resources?: draw resource nodes (off by default — on a
## small thumbnail it reads as noise) }.
##
## Spawn dots are the reason this is worth more than a coloured blob: where the
## seats are, and how far apart, is most of what tells you whether a map is a
## knife fight or a long game — and it is exactly what a name and a cell count
## cannot say.
static func drawMapThumbnail(ctx: Ctx, x: float, y: float, size: float, m: Dictionary, opts: Dictionary = {}) -> void:
	var cols := int(m.cols)
	var scale: float = size / (cols * C.TILE)
	var resources: bool = opts.get("resources", false)
	if size > 0:
		# The sampled grid and the resource specks, as one baked image (the
		# TS's fillRect loops, filled once — see the header).
		var tex := _baked_texture(m, size, resources, ctx.device_scale())
		var extent := float(tex.get_meta("extent"))
		ctx.drawImage(tex, x, y, extent, extent)
	if opts.get("spawns", false):
		var teams: Array = C.PAL.teams
		var i := 0
		for s in m.spawns:
			var sx: float = x + s.x * scale
			var sy: float = y + s.y * scale
			var col = teams[i % teams.size()].main
			ctx.fillStyle = col
			ctx.beginPath(); ctx.arc(sx, sy, maxf(2, size * 0.045), 0, PI * 2); ctx.fill()
			ctx.strokeStyle = "rgba(0,0,0,0.65)"; ctx.lineWidth = 1
			ctx.beginPath(); ctx.arc(sx, sy, maxf(2, size * 0.045), 0, PI * 2); ctx.stroke()
			i += 1
	ctx.strokeStyle = "rgba(255,255,255,0.14)"
	ctx.lineWidth = 1
	ctx.strokeRect(x + 0.5, y + 0.5, size - 1, size - 1)


## The TS's two fill loops, into a texture, cached on everything that changes
## the picture.
static func _baked_texture(m: Dictionary, size: float, resources: bool, ds: float) -> ImageTexture:
	var level := maxf(1.0, ceilf(ds * 2.0) / 2.0)  # device pixels per unit, in half steps
	var res = m.get("resources", []) if resources else []
	var key := "%d|%d|%s|%s|%d|%d" % [int(m.cols), int(m.rows), JS.num(size), JS.num(level), hash(m.terrain), hash(res) if resources else 0]
	var tex = _baked.get(key)
	if tex != null:
		return tex
	tex = ImageTexture.create_from_image(bakeImage(m, size, resources, level))
	tex.set_meta("extent", bakeExtent(size))
	if _baked.size() >= _BAKE_LIMIT:
		_baked.clear()
	_baked[key] = tex
	return tex


## The square the bake covers, in user units from the box corner: cells are
## ceil(px) wide, so the last row and column can reach a pixel or so past the
## box, as on the canvas.
static func bakeExtent(size: float) -> float:
	return ceilf(size) + 2.0


## The sampled terrain cells, then (if asked) a speck per resource node, filled
## at `level` pixels per unit — supersampled and box-filtered down, which is
## the coverage the canvas's antialiased rectangles have.
static func bakeImage(m: Dictionary, size: float, resources: bool, level: float = 1.0) -> Image:
	var cols := int(m.cols)
	var rows := int(m.rows)
	var terrain = m.terrain
	var step := maxi(1, floori(cols / size))
	var px := size / ceilf(float(cols) / step)
	var extent := bakeExtent(size)
	var target := int(ceilf(extent * level))
	var n := target * _SS
	var k := n / extent
	var img := Image.create(n, n, false, Image.FORMAT_RGBA8)
	var colors := {}
	for t in MAP_SWATCH:
		colors[t] = Color(MAP_SWATCH[t])
	var fallback := Color("#6f9c4a")
	var cell := ceilf(px)
	var ry := 0
	var cy := 0
	while cy < rows:
		var rx := 0
		var cx := 0
		var y0 := roundi(ry * px * k)
		var y1 := roundi((ry * px + cell) * k)
		while cx < cols:
			var x0 := roundi(rx * px * k)
			var x1 := roundi((rx * px + cell) * k)
			img.fill_rect(Rect2i(x0, y0, x1 - x0, y1 - y0), colors.get(int(terrain[cy * cols + cx]), fallback))
			cx += step
			rx += 1
		cy += step
		ry += 1
	if resources:
		var scale: float = size / (cols * C.TILE)
		var gold := Color("#e0b83a")
		var berries := Color("#c8425a")
		var tree := Color("#2f6b32")
		var sz := roundi(1.5 * k)
		for r in m.resources:
			var col := gold if r.type == "gold_mine" else (berries if r.type == "berries" else tree)
			img.fill_rect(Rect2i(roundi((r.x * scale - 0.5) * k), roundi((r.y * scale - 0.5) * k), sz, sz), col)
	# Down to device pixels by halving: each step averages 2×2 exactly, so the
	# result is the box-filtered coverage.
	var w := n
	while w > target:
		w /= 2
		img.resize(w, w, Image.INTERPOLATE_BILINEAR)
	return img
