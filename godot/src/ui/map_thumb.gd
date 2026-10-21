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
	var rows := int(m.rows)
	var step := maxi(1, floori(cols / size))
	var px := size / ceilf(float(cols) / step)
	var terrain = m.terrain
	var ry := 0
	var cy := 0
	while cy < rows:
		var rx := 0
		var cx := 0
		while cx < cols:
			ctx.fillStyle = MAP_SWATCH.get(int(terrain[cy * cols + cx]), "#6f9c4a")
			ctx.fillRect(x + rx * px, y + ry * px, ceilf(px), ceilf(px))
			cx += step
			rx += 1
		cy += step
		ry += 1
	var scale: float = size / (cols * C.TILE)
	if opts.get("resources", false):
		for r in m.resources:
			ctx.fillStyle = "#e0b83a" if r.type == "gold_mine" else ("#c8425a" if r.type == "berries" else "#2f6b32")
			ctx.fillRect(x + r.x * scale - 0.5, y + r.y * scale - 0.5, 1.5, 1.5)
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
