## render/renderer.ts — frame rendering: terrain blit, y-sorted entities
## (fog-filtered), particles, fog-of-war overlay, command markers, placement
## ghost and drag box.
##
## Godot port. The drawing is the TS's, call for call, with three structural
## differences, all for speed:
##
## - **It draws from a Snapshot, not the live World.** `snapshot()` copies
##   what a frame needs (the on-screen entities' drawing fields, banners, the
##   fog when it is due, the team colours and factions) at a tick boundary,
##   and `render()` reads only that. The simulation can therefore run its next
##   ticks on a worker thread while the main thread draws (see main.gd,
##   "Threading"), and a frame can be re-drawn — camera moving, particles
##   flying — while a slow tick is still in flight. Field names on the
##   snapshot are the World's, so `render()` reads like the TS.
## - The per-pixel and per-cell canvas work moved to the GPU: the terrain is a
##   baked texture (TerrainArt), the fog is uploaded raw when vision changes
##   and blurred by a shader into a tiny offscreen layer, the night layer and
##   the vignette are offscreen/shader layers, and the ground detail is
##   captured once into meshes (TerrainArt.GroundDetail).
## - Two contexts: `ctx` is the world (camera space) and `octx` the
##   screen-space overlays drawn over it (night, vignette, drag box); the
##   HUD has its own. They are separate canvas layers, drawn in that order.
class_name Renderer
extends RefCounted

## How dark ground you have scouted but cannot currently see is drawn.
##
## Was a flat 110 of near-black, which crushed remembered woodland and
## buildings into a colourless grey mush — everything under it read as the
## same smudge. Lighter, and tinted very slightly blue, so memory reads as dusk
## rather than dirt and a treeline is still recognisably a treeline.
const FOG_SEEN_ALPHA := 88

## How far past the view (world units, at zoom 1) the snapshot reaches, so a
## frame re-drawn while a tick is in flight still has what the camera pans to.
const SNAPSHOT_MARGIN := 240.0


## Battlefield decals: corpses, stuck arrows and scorch marks linger after the
## action and fade. Presentation-only (kept out of the deterministic sim).
class FxDecal extends RefCounted:
	var kind := "corpse"  ## "corpse" | "arrow" | "scorch"
	var x := 0.0
	var y := 0.0
	var angle := 0.0
	var team := 0
	var size := 0.0
	var age := 0.0
	var life := 0.0


class Floater extends RefCounted:
	var x := 0.0
	var y := 0.0
	var vy := 0.0
	var text := ""
	var color := ""
	var age := 0.0
	var life := 0.0
	var size := 13.0


## What render() reads: the World's fields it needs, copied at a tick
## boundary. Entities are proxies (Entity objects holding the drawing fields
## of the real ones), y-sorted as the TS sorts its drawables.
class Snapshot extends RefCounted:
	var valid := false
	var entities: Array = []  ## proxies, sorted by (y, id)
	var curX := PackedFloat64Array()  ## sim positions (proxy x/y get the interpolated ones)
	var curY := PackedFloat64Array()
	var ghosted := PackedByteArray()  ## a remembered enemy building (drawn faded)
	var farmIdle := PackedByteArray()  ## your finished farm with nobody working it
	var banners: Array = []  ## {x, y, radius, color}
	var mode := "conquest"
	var hillX := 0.0
	var hillY := 0.0
	var hillR := 0.0
	var revealAll := false
	var numTeams := 2
	var worldW := 0.0
	var worldH := 0.0
	var fogCols := 0
	var fogRows := 0
	var factions: Array = []
	var colors: Array = []  ## per team: the drawing colours (relation-based in team games), or null
	var map: Dictionary = {}
	## Pool of proxy objects, reused between snapshots.
	var pool: Array = []


var ctx: LayerCtx  ## the world layer
var octx: LayerCtx  ## screen-space overlays over the world
var terrainCache: TerrainArt.TerrainCache = null
## The scale this map's cache was baked at — large maps are baked coarser.
var terrainScale := 0.5
var detail: TerrainArt.GroundDetail = null
var fogLayer: GpuLayer = null
var fogItem := RID()
var fogTex: ImageTexture = null
var fogMat: ShaderMaterial = null
var fogDirtyTimer := 0.0
var fogTeam := -1
var shakeX := 0.0
var shakeY := 0.0
var shakeAmp := 0.0
var decals: Array = []
var floaters: Array = []
var dayPhase := 0.18 ## 0..1 around the day/night cycle
var aggressiveLod := false ## "Reduce effects" → simplify units sooner
var waterCells := PackedFloat64Array() ## [x0,y0,x1,y1,...] water cell centres
var vignetteItem := RID()
var fxItem := RID()  ## particles, batched
var decalItem := RID()  ## decals, batched
var vignetteMat: ShaderMaterial = null
var vignetteSize := Vector2i.ZERO
var nightLayer := Nightlight.NightLayer.new()
var lights: Array = []
var snap := Snapshot.new()
var _factionResolver: Callable
var _colorResolver: Callable
## Lines from builders to a site just placed, fading out (set by the App).
var builderLines: Array = []
var ghostEntity: Entity = null
## Per-frame costs (ms), for the performance overlay.
var lastSnapshotMs := 0.0
var lastRenderMs := 0.0
## Where render()'s time went last frame, by phase (ms), for profiling.
var phaseMs := {}
var _pt := 0


func _init(world_ctx: LayerCtx = null, overlay_ctx: LayerCtx = null) -> void:
	ctx = world_ctx if world_ctx != null else LayerCtx.new()
	octx = overlay_ctx if overlay_ctx != null else LayerCtx.new()
	_factionResolver = func(team: int):
		return snap.factions[team] if team >= 0 and team < snap.numTeams and team < snap.factions.size() else null
	_colorResolver = func(team: int) -> Dictionary:
		if team >= 0 and team < snap.colors.size() and snap.colors[team] != null:
			return snap.colors[team]
		return Palette.teamColor(team)


## True when it is visually night — gameplay layers can read this later.
var isNight: bool:
	get:
		return dayPhase > 0.55 and dayPhase < 0.95


func addCorpse(x: float, y: float, team: int, big: bool = false) -> void:
	var d := FxDecal.new()
	d.kind = "corpse"
	d.x = x
	d.y = y
	d.angle = randf() * PI * 2
	d.team = team
	d.size = 16 if big else 9
	d.life = 14 if big else 9
	decals.append(d)
	if decals.size() > 240:
		decals.pop_front()


func addStuckArrow(x: float, y: float, angle: float) -> void:
	var d := FxDecal.new()
	d.kind = "arrow"
	d.x = x
	d.y = y
	d.angle = angle
	d.size = 6
	d.life = 6
	decals.append(d)
	if decals.size() > 240:
		decals.pop_front()


func addScorch(x: float, y: float, size: float) -> void:
	var d := FxDecal.new()
	d.kind = "scorch"
	d.x = x
	d.y = y
	d.size = size
	d.life = 22
	decals.append(d)
	if decals.size() > 240:
		decals.pop_front()


func addFloater(x: float, y: float, text: String, color: String, size: float = 13) -> void:
	var f := Floater.new()
	f.x = x + (randf() - 0.5) * 10
	f.y = y
	f.vy = -26
	f.text = text
	f.color = color
	f.life = 0.9
	f.size = size
	floaters.append(f)
	if floaters.size() > 80:
		floaters.pop_front()


func clearFx() -> void:
	decals.clear()
	floaters.clear()


func addShake(amp: float) -> void:
	shakeAmp = minf(10, shakeAmp + amp)


func prepare(map: Dictionary) -> void:
	terrainCache = TerrainArt.buildTerrainCache(map)
	terrainScale = TerrainArt.terrainCacheScale(map)
	if detail != null:
		ctx.forget(detail.root)
	detail = TerrainArt.GroundDetail.new(map)
	fogLayer = null
	fogTex = null
	fogTeam = -1
	fogDirtyTimer = 0
	snap = Snapshot.new()
	snap.map = map
	# Water cell centres (world coords) for the animated glint pass.
	var TILE: float = C.TILE
	waterCells = PackedFloat64Array()
	var cols: int = map.cols
	var rows: int = map.rows
	var terr: PackedByteArray = map.terrain
	for cy in rows:
		for cx in cols:
			if terr[cy * cols + cx] == TerrainKinds.Terrain.Water:
				waterCells.append(cx * TILE + TILE / 2)
				waterCells.append(cy * TILE + TILE / 2)


# --------------------------------------------------------------- snapshot --

## Copy what the next frames draw out of the world. Call at a tick boundary
## (the simulation not running): the world isn't touched again until the next
## snapshot. `W`/`H` is the canvas size the camera views.
func snapshot(world: World, cam: Camera, viewTeam: int, W: float, H: float) -> void:
	var t0 := Time.get_ticks_usec()
	var s := snap
	s.valid = true
	s.mode = world.mode
	s.hillX = world.hillX
	s.hillY = world.hillY
	s.hillR = world.hillR
	s.revealAll = world.revealAll
	s.numTeams = world.numTeams
	s.worldW = world.worldW
	s.worldH = world.worldH
	s.fogCols = world.fogCols
	s.fogRows = world.fogRows
	s.map = world.map
	# Each team is drawn in its faction's style.
	s.factions.resize(world.numTeams)
	s.colors.resize(world.numTeams)
	var diplo := not world.revealAll and world.hasTeamAlliances()
	var PAL: Dictionary = C.PAL
	for team in world.numTeams:
		s.factions[team] = world.player(team).faction
		# Spectators see absolute per-realm colours (every side distinct);
		# players see units coloured by relation to themselves (you/ally/enemy).
		if diplo:
			var rel := world.relationTo(viewTeam, team)
			s.colors[team] = PAL.diplomacy.self if rel == "self" else (PAL.diplomacy.ally if rel == "ally" \
				else PAL.diplomacy.enemies[world.enemyIndexOf(viewTeam, team) % PAL.diplomacy.enemies.size()])
		else:
			s.colors[team] = null
	s.banners.clear()
	for b in world.banners:
		s.banners.append({"x": b.x, "y": b.y, "radius": float(b.power.radius), "color": b.power.color})

	# The entities in (and a margin around) the view, visible to the viewer.
	var m := SNAPSHOT_MARGIN / cam.zoom + 80
	var vx0 := cam.x - W / 2 / cam.zoom - m
	var vy0 := cam.y - H / 2 / cam.zoom - m
	var vx1 := cam.x + W / 2 / cam.zoom + m
	var vy1 := cam.y + H / 2 / cam.zoom + m
	var picked: Array = []
	for e: Entity in world.entities:
		if not e.alive:
			continue
		if e.x < vx0 or e.x > vx1 or e.y < vy0 or e.y > vy1:
			continue
		if not world.visibleTo(viewTeam, e):
			continue
		picked.append(e)
	picked.sort_custom(func(a: Entity, b: Entity) -> bool: return a.y < b.y or (a.y == b.y and a.id < b.id))
	var n := picked.size()
	while s.pool.size() < n:
		s.pool.append(Entity.new())
	s.entities.resize(n)
	s.curX.resize(n)
	s.curY.resize(n)
	s.ghosted.resize(n)
	s.farmIdle.resize(n)
	for i in n:
		var e: Entity = picked[i]
		var p: Entity = s.pool[i]
		_copy(e, p)
		s.entities[i] = p
		s.curX[i] = e.x
		s.curY[i] = e.y
		s.ghosted[i] = 1 if e.team != viewTeam and e.kind == T.Kind.Building and world.fogAt(viewTeam, e.x, e.y) != World.FOG_VISIBLE else 0
		s.farmIdle[i] = 1 if e.type == "farm" and e.team == viewTeam and e.buildState == T.BuildState.Done and not world.farmWorked(e) else 0

	# The fog, when it is due (TS: re-read every 0.12s), straight to the GPU.
	if not world.revealAll and (fogDirtyTimer <= 0 or fogTeam != viewTeam):
		fogDirtyTimer = 0.12
		fogTeam = viewTeam
		_uploadFog(world.fog[viewTeam], world.fogCols, world.fogRows)
	lastSnapshotMs = (Time.get_ticks_usec() - t0) / 1000.0


## The drawing fields of an entity (everything Draw, the health bars, the
## selection rings and the lights read).
static func _copy(e: Entity, p: Entity) -> void:
	p.id = e.id
	p.kind = e.kind
	p.team = e.team
	p.type = e.type
	p.alive = e.alive
	p.x = e.x
	p.y = e.y
	p.prevX = e.prevX
	p.prevY = e.prevY
	p.radius = e.radius
	p.facing = e.facing
	p.hp = e.hp
	p.maxHp = e.maxHp
	p.vx = e.vx
	p.vy = e.vy
	p.attackCooldown = e.attackCooldown
	p.attackInterval = e.attackInterval
	p.carry = e.carry
	p.carryKind = e.carryKind
	p.amount = e.amount
	p.buildState = e.buildState
	p.buildProgress = e.buildProgress
	p.gateOpen = e.gateOpen
	p.projElapsed = e.projElapsed
	p.projDuration = e.projDuration
	p.abilityActive = e.abilityActive
	p.slowTimer = e.slowTimer
	p.rallyTimer = e.rallyTimer
	p.heroLevel = e.heroLevel
	p.veterancy = e.veterancy
	p.variantRarity = e.variantRarity
	p.animPhase = e.animPhase
	p.hitFlash = e.hitFlash
	p.chargeRun = e.chargeRun
	p.lastDamageTime = e.lastDamageTime
	p.selected = e.selected
	p.rallyX = e.rallyX
	p.rallyY = e.rallyY
	p.tier = e.tier


## Mark the snapshot's selection flags again (selection changes between
## snapshots, and the rings should follow the click at once).
func refreshSelection(world: World) -> void:
	for p: Entity in snap.entities:
		var e = world.byId.get(p.id)
		if e != null:
			p.selected = e.selected


func _uploadFog(fog: PackedByteArray, cols: int, rows: int) -> void:
	if cols <= 0 or rows <= 0:
		return
	var img := Image.create_from_data(cols, rows, false, Image.FORMAT_R8, fog)
	if fogLayer == null or fogLayer.w != cols or fogLayer.h != rows or fogTex == null:
		fogLayer = GpuLayer.new(cols, rows, true)
		fogTex = ImageTexture.create_from_image(img)
		if fogMat == null:
			fogMat = ShaderMaterial.new()
			var sh := Shader.new()
			sh.code = FOG_BLUR_SHADER
			fogMat.shader = sh
		fogMat.set_shader_parameter("radius", FogBlur.FOG_BLUR_RADIUS)
		fogMat.set_shader_parameter("fog", fogTex)
		fogMat.set_shader_parameter("seen", FOG_SEEN_ALPHA / 255.0)
		fogItem = fogLayer.item()
		RenderingServer.canvas_item_set_material(fogItem, fogMat.get_rid())
		RenderingServer.canvas_item_add_texture_rect(fogItem, Rect2(0, 0, cols, rows), fogTex.get_rid())
	else:
		fogTex.update(img)
	fogLayer.render()


## The fog mask, as the TS builds it on the CPU every 0.12s: each cell's alpha
## (unseen 255, explored FOG_SEEN_ALPHA, visible 0), box-blurred by
## FOG_BLUR_RADIUS (two separable passes, clamped at the edges — i.e. the mean
## of the clamped (2r+1)² neighbourhood), in the colour (10, 9, 14). One
## fragment per cell; the world pass then upscales it bilinearly.
const FOG_BLUR_SHADER := """
shader_type canvas_item;
uniform int radius;
uniform float seen;
uniform sampler2D fog : filter_nearest;
float alphaAt(ivec2 p, ivec2 size) {
	ivec2 q = clamp(p, ivec2(0), size - ivec2(1));
	float v = texelFetch(fog, q, 0).r * 255.0;
	return v < 0.5 ? 1.0 : (v < 1.5 ? seen : 0.0);
}
void fragment() {
	ivec2 size = textureSize(fog, 0);
	ivec2 c = ivec2(floor(UV * vec2(size)));
	float sum = 0.0;
	for (int dy = -radius; dy <= radius; dy++) {
		for (int dx = -radius; dx <= radius; dx++) {
			sum += alphaAt(c + ivec2(dx, dy), size);
		}
	}
	float n = float((2 * radius + 1) * (2 * radius + 1));
	COLOR = vec4(10.0 / 255.0, 9.0 / 255.0, 14.0 / 255.0, sum / n);
}
"""


# ----------------------------------------------------------------- ghost --

## The placement preview: the building itself, see-through, on a tile grid,
## outlined green or red; the resources a drop-off building would serve; and
## a line from each villager who'd build it.
func drawGhost(ctx: Ctx, ghost, time: float) -> void:
	for l in builderLines:
		var a := maxf(0, 1 - l.age / 1.6)
		ctx.strokeStyle = Palette.withAlpha("#9fe08a", 0.7 * a)
		ctx.lineWidth = 2
		ctx.setLineDash([6, 6])
		ctx.beginPath(); ctx.moveTo(l.x0, l.y0); ctx.lineTo(l.x1, l.y1); ctx.stroke()
	ctx.setLineDash([])
	if ghost == null:
		return
	var def = C.BUILDINGS.get(ghost.type)
	if def == null:
		return
	var TILE: float = C.TILE
	var tiles: float = def.tiles
	var half := (tiles * TILE) / 2
	var sx := GridSnap.snapBuilding(ghost.x, tiles)
	var sy := GridSnap.snapBuilding(ghost.y, tiles)
	var good := "#7df27d"
	var bad := "#f25d4a"

	# Coverage ring and highlighted resources.
	if ghost.get("coverage") != null:
		var c: Dictionary = ghost.coverage
		ctx.strokeStyle = Palette.withAlpha("#ffe9b0", 0.35)
		ctx.lineWidth = 2
		ctx.setLineDash([10, 8])
		ctx.beginPath(); ctx.arc(sx, sy, c.radius, 0, PI * 2); ctx.stroke()
		ctx.setLineDash([])
		for nd in c.nodes:
			ctx.strokeStyle = Palette.withAlpha("#ffe36a", 0.85)
			ctx.lineWidth = 2
			ctx.beginPath(); ctx.arc(nd.x, nd.y, maxf(8, nd.r + 3), 0, PI * 2); ctx.stroke()
	# Builders walking over.
	for b in ghost.get("builders", []):
		ctx.strokeStyle = Palette.withAlpha(good if ghost.valid else bad, 0.45)
		ctx.lineWidth = 1.5
		ctx.setLineDash([4, 6])
		ctx.beginPath(); ctx.moveTo(b.x, b.y); ctx.lineTo(sx, sy); ctx.stroke()
	ctx.setLineDash([])

	var cell := func(x: float, y: float, valid: bool, fill: bool) -> void:
		var cx := GridSnap.snapBuilding(x, tiles)
		var cy := GridSnap.snapBuilding(y, tiles)
		if fill:
			ctx.fillStyle = Palette.withAlpha(good, 0.22) if valid else Palette.withAlpha(bad, 0.3)
			ctx.fillRect(cx - half, cy - half, half * 2, half * 2)
		ctx.strokeStyle = good if valid else bad
		ctx.lineWidth = 2
		ctx.strokeRect(cx - half, cy - half, half * 2, half * 2)
	var line = ghost.get("line")
	if line != null and line.size() > 0:
		for pt in line:
			cell.call(pt.x, pt.y, pt.valid, true)
		return
	# A faint tile grid around the footprint, for lining things up.
	var R := half + TILE * 3
	ctx.strokeStyle = "rgba(255,255,255,0.09)"
	ctx.lineWidth = 1
	ctx.beginPath()
	var gx := JS.round((sx - R) / TILE) * TILE
	while gx <= sx + R:
		ctx.moveTo(gx, sy - R)
		ctx.lineTo(gx, sy + R)
		gx += TILE
	var gy := JS.round((sy - R) / TILE) * TILE
	while gy <= sy + R:
		ctx.moveTo(sx - R, gy)
		ctx.lineTo(sx + R, gy)
		gy += TILE
	ctx.stroke()
	# The building itself, see-through.
	if ghostEntity == null:
		ghostEntity = World.makeEntity()
	var e := ghostEntity
	e.kind = T.Kind.Building
	e.type = ghost.type
	e.team = ghost.get("team", 0)
	e.x = sx
	e.y = sy
	e.radius = half
	e.hp = 1
	e.maxHp = 1
	e.buildState = T.BuildState.Done
	e.buildProgress = 1
	e.productionQueue = []
	ctx.save()
	ctx.globalAlpha = 0.55 if ghost.valid else 0.35
	Draw.drawBuilding(ctx, e, time, ghost.get("team", 0))
	ctx.restore()
	cell.call(ghost.x, ghost.y, ghost.valid, not ghost.valid)


# ------------------------------------------------------------ decals, text --

## The decals, each shape as one textured quad on a retained item (`ctx`
## must be a LayerCtx; with a plain Ctx they draw as the TS does, shape by
## shape). Same shapes, colours and fades as the TS: a corpse is three
## ellipses, an arrow a stroke, a scorch an ellipse.
func drawDecals(ctx: Ctx, dt: float, vx0: float, vy0: float, vx1: float, vy1: float) -> void:
	var PAL: Dictionary = C.PAL
	var batch := ctx is LayerCtx
	if batch:
		if not decalItem.is_valid():
			decalItem = _fxItem()
		RenderingServer.canvas_item_clear(decalItem)
	var disc := _discTex().get_rid()
	var i := decals.size() - 1
	while i >= 0:
		var d: FxDecal = decals[i]
		d.age += dt
		if d.age >= d.life:
			decals.remove_at(i)
			i -= 1
			continue
		i -= 1
		if d.x < vx0 or d.x > vx1 or d.y < vy0 or d.y > vy1:
			continue
		var fade := minf(1, (d.life - d.age) / 2.5)
		if not batch:
			_drawDecalShapes(ctx, d, fade)
			continue
		if d.kind == "corpse":
			var tc := Palette.teamColor(d.team)
			_ellipse(disc, d.x, d.y + 2, d.size, d.size * 0.45, 0, Color(26 / 255.0, 18 / 255.0, 12 / 255.0, 0.5 * fade))
			_ellipse(disc, d.x, d.y, d.size * 0.7, d.size * 0.42, d.angle, Palette.withAlpha(tc.dark, 0.8 * fade))
			_ellipse(disc, d.x + cos(d.angle) * d.size * 0.5, d.y + sin(d.angle) * d.size * 0.3, d.size * 0.9, d.size * 0.4, d.angle, Palette.withAlpha(PAL.blood, 0.35 * fade))
		elif d.kind == "arrow":
			RenderingServer.canvas_item_add_set_transform(decalItem, Transform2D.IDENTITY)
			RenderingServer.canvas_item_add_line(decalItem, Vector2(d.x, d.y),
				Vector2(d.x + cos(d.angle) * d.size, d.y + sin(d.angle) * d.size - 5), Color(90 / 255.0, 70 / 255.0, 45 / 255.0, 0.9 * fade), 1.4, true)
		else:
			_ellipse(disc, d.x, d.y, d.size, d.size * 0.6, 0, Color(18 / 255.0, 14 / 255.0, 10 / 255.0, 0.5 * fade))
	if batch:
		(ctx as LayerCtx).external(decalItem)


## One decal, through the Ctx, exactly as the TS draws it.
func _drawDecalShapes(ctx: Ctx, d: FxDecal, fade: float) -> void:
	var PAL: Dictionary = C.PAL
	ctx.globalAlpha = fade
	if d.kind == "corpse":
		ctx.fillStyle = "rgba(26, 18, 12, 0.5)"
		ctx.beginPath()
		ctx.ellipse(d.x, d.y + 2, d.size, d.size * 0.45, 0, 0, PI * 2)
		ctx.fill()
		var tc := Palette.teamColor(d.team)
		ctx.fillStyle = Palette.withAlpha(tc.dark, 0.8)
		ctx.beginPath()
		ctx.ellipse(d.x, d.y, d.size * 0.7, d.size * 0.42, d.angle, 0, PI * 2)
		ctx.fill()
		ctx.fillStyle = Palette.withAlpha(PAL.blood, 0.35)
		ctx.beginPath()
		ctx.ellipse(d.x + cos(d.angle) * d.size * 0.5, d.y + sin(d.angle) * d.size * 0.3, d.size * 0.9, d.size * 0.4, d.angle, 0, PI * 2)
		ctx.fill()
	elif d.kind == "arrow":
		ctx.strokeStyle = "rgba(90, 70, 45, 0.9)"
		ctx.lineWidth = 1.4
		ctx.beginPath()
		ctx.moveTo(d.x, d.y)
		ctx.lineTo(d.x + cos(d.angle) * d.size, d.y + sin(d.angle) * d.size - 5)
		ctx.stroke()
	else:
		ctx.fillStyle = "rgba(18, 14, 10, 0.5)"
		ctx.beginPath()
		ctx.ellipse(d.x, d.y, d.size, d.size * 0.6, 0, 0, PI * 2)
		ctx.fill()
	ctx.globalAlpha = 1


func _ellipse(disc: RID, x: float, y: float, rx: float, ry: float, rot: float, col: Color) -> void:
	RenderingServer.canvas_item_add_set_transform(decalItem, Transform2D(rot, Vector2(rx, ry), 0.0, Vector2(x, y)))
	RenderingServer.canvas_item_add_texture_rect(decalItem, Rect2(-1, -1, 2, 2), disc, false, col)


## A retained item for batched effects: textures filtered with mipmaps, so a
## disc shrunk to a few pixels stays a soft disc.
func _fxItem() -> RID:
	var it := RenderingServer.canvas_item_create()
	RenderingServer.canvas_item_set_default_texture_filter(it, RenderingServer.CANVAS_ITEM_TEXTURE_FILTER_LINEAR_WITH_MIPMAPS)
	return it


static var _disc: ImageTexture = null
static var _glow: ImageTexture = null


## A white antialiased disc filling its square (with mipmaps).
static func _discTex() -> ImageTexture:
	if _disc == null:
		var n := 64
		var img := Image.create(n, n, false, Image.FORMAT_RGBA8)
		var r := n / 2.0
		for y in n:
			for x in n:
				var d := Vector2(x + 0.5 - r, y + 0.5 - r).length()
				img.set_pixel(x, y, Color(1, 1, 1, clampf(r - d, 0.0, 1.0)))
		img.generate_mipmaps()
		_disc = ImageTexture.create_from_image(img)
	return _disc


## A white gaussian blob (σ = a third of its radius), for glows.
static func _glowTex() -> ImageTexture:
	if _glow == null:
		var n := 64
		var img := Image.create(n, n, false, Image.FORMAT_RGBA8)
		var r := n / 2.0
		var sig := r / 3.0
		for y in n:
			for x in n:
				var d := Vector2(x + 0.5 - r, y + 0.5 - r).length()
				img.set_pixel(x, y, Color(1, 1, 1, exp(-d * d / (2 * sig * sig)) if d < r else 0.0))
		img.generate_mipmaps()
		_glow = ImageTexture.create_from_image(img)
	return _glow


static var _colorCache := {}


static func _color(c) -> Color:
	if c is Color:
		return c
	var k = _colorCache.get(c)
	if k == null:
		var p = Ctx.parse_color(c)
		k = p if p != null else Color.WHITE
		_colorCache[c] = k
	return k


func drawFloaters(ctx: Ctx, dt: float) -> void:
	ctx.textAlign = "center"
	var i := floaters.size() - 1
	while i >= 0:
		var f: Floater = floaters[i]
		f.age += dt
		if f.age >= f.life:
			floaters.remove_at(i)
			i -= 1
			continue
		i -= 1
		f.y += f.vy * dt
		var t := f.age / f.life
		ctx.globalAlpha = 1 - t * t
		ctx.font = "bold %spx \"Trebuchet MS\", sans-serif" % JS.num(f.size)
		ctx.fillStyle = "rgba(0,0,0,0.6)"
		ctx.fillText(f.text, f.x + 1, f.y + 1)
		ctx.fillStyle = f.color
		ctx.fillText(f.text, f.x, f.y)
	ctx.globalAlpha = 1
	ctx.textAlign = "left"


# ----------------------------------------------------------------- render --

## Draw a frame from the snapshot. `markers` are {x, y, age, kind}; `ghost`
## a GhostPlacement Dictionary or null; `dragBox` {active, x0, y0, x1, y1};
## `rallyFrom` an Array of {x, y, rallyX, rallyY}. `alpha` is how far the
## frame sits between the last two sim ticks (0..1).
func render(world: Snapshot, cam: Camera, particles: Particles, dt: float, time: float, viewTeam: int,
		markers: Array, ghost, dragBox: Dictionary, rallyFrom: Array, hoveredId: int = -1, alpha: float = 1.0) -> void:
	var t0 := Time.get_ticks_usec()
	_pt = t0
	var W: float = ctx.canvas.width
	var H: float = ctx.canvas.height
	var PAL: Dictionary = C.PAL
	var TILE: float = C.TILE
	Draw.setDrawDayPhase(dayPhase)
	Draw.setFactionResolver(_factionResolver)
	Draw.setTeamColorResolver(_colorResolver)

	# Screen shake decay.
	shakeAmp *= pow(0.0015, dt)
	if shakeAmp < 0.05:
		shakeAmp = 0
	shakeX = (randf() - 0.5) * shakeAmp * 2
	shakeY = (randf() - 0.5) * shakeAmp * 2

	ctx.fillStyle = "#0d0b08"
	if not world.valid:
		ctx.fillRect(0, 0, W, H)
		return
	# The backdrop shows only off the edge of the map: fill just that, not a
	# whole screen the terrain then covers (a full-screen pass saved).
	var mx0 := (0.0 - cam.x) * cam.zoom + W / 2 + shakeX
	var my0 := (0.0 - cam.y) * cam.zoom + H / 2 + shakeY
	var mx1 := (world.worldW - cam.x) * cam.zoom + W / 2 + shakeX
	var my1 := (world.worldH - cam.y) * cam.zoom + H / 2 + shakeY
	if terrainCache == null or mx0 > 0 or my0 > 0 or mx1 < W or my1 < H:
		if terrainCache == null:
			ctx.fillRect(0, 0, W, H)
		else:
			if my0 > 0:
				ctx.fillRect(0, 0, W, my0 + 1)
			if my1 < H:
				ctx.fillRect(0, my1 - 1, W, H - my1 + 1)
			if mx0 > 0:
				ctx.fillRect(0, 0, mx0 + 1, H)
			if mx1 < W:
				ctx.fillRect(mx1 - 1, 0, W - mx1 + 1, H)

	ctx.save()
	ctx.translate(W / 2 + shakeX, H / 2 + shakeY)
	ctx.scale(cam.zoom, cam.zoom)
	ctx.translate(-cam.x, -cam.y)

	# Visible world rect for culling.
	var pad := 80.0
	var vx0 := cam.x - W / 2 / cam.zoom - pad
	var vy0 := cam.y - H / 2 / cam.zoom - pad
	var vx1 := cam.x + W / 2 / cam.zoom + pad
	var vy1 := cam.y + H / 2 / cam.zoom + pad

	# Terrain — blit only the slice under the camera, not the whole map.
	if terrainCache != null:
		var cx0 := maxf(0, vx0)
		var cy0 := maxf(0, vy0)
		var cx1 := minf(world.worldW, vx1)
		var cy1 := minf(world.worldH, vy1)
		if cx1 > cx0 and cy1 > cy0:
			ctx.blit(terrainCache.tex,
				Rect2(cx0 * terrainScale, cy0 * terrainScale, (cx1 - cx0) * terrainScale, (cy1 - cy0) * terrainScale),
				Rect2(cx0, cy0, cx1 - cx0, cy1 - cy0))

	# Crisp close-up detail over the blitted cache, which is baked at half
	# resolution and turns to mush the moment you lean in.
	#
	# Skipped whenever detail is already being shed — "Reduce effects", or the
	# adaptive LOD having decided frames are slipping. Ground detail is the
	# most expendable thing on screen and the only part of the frame whose cost
	# scales with how much *map* is visible, so it is the right first thing to
	# drop and it should never be what makes a weak machine stutter.
	if not aggressiveLod and detail != null:
		var strength := minf(1, (cam.zoom - 0.85) / 0.45)
		var cells := (ceilf(vx1 / TILE) - floorf(vx0 / TILE) + 1) * (ceilf(vy1 / TILE) - floorf(vy0 / TILE) + 1)
		if strength > 0.02 and cells <= 3000:
			detail.ensure(vx0, vy0, vx1, vy1)
			detail.set_time(time)
			ctx.external(detail.root, Color(1, 1, 1, strength))

	# Ground decals (corpses, arrows, scorch) sit on the terrain, under units.
	_phase("ground")
	drawDecals(ctx, dt, vx0, vy0, vx1, vy1)
	_phase("decals")

	# Living water: drifting glints on visible water cells. Each cell twinkles
	# on its own phase; cheap because it's culled to the viewport.
	var wc := waterCells
	for i in range(0, wc.size(), 2):
		var x := wc[i]
		var y := wc[i + 1]
		if x < vx0 or x > vx1 or y < vy0 or y > vy1:
			continue
		var phase := x * 0.137 + y * 0.211
		var s := sin(time * 1.7 + phase)
		if s <= 0.55:
			continue
		var a := (s - 0.55) * 0.8
		var jx := sin(phase * 3.1) * TILE * 0.3 + sin(time * 0.6 + phase) * 3
		var jy := cos(phase * 2.3) * TILE * 0.3
		ctx.strokeStyle = Palette.withAlpha("#eaf7ff", a)
		ctx.lineWidth = 1.2
		ctx.beginPath()
		ctx.arc(x + jx, y + jy, 2.2 + s * 1.6, PI * 0.1, PI * 0.9)
		ctx.stroke()

	# Commander banners — radius aura on the ground + a waving standard.
	for b in world.banners:
		if b.x < vx0 - b.radius or b.x > vx1 + b.radius:
			continue
		var col = b.color
		var pulse := 0.5 + sin(time * 2 + b.x) * 0.2
		# radius ring
		ctx.strokeStyle = Palette.withAlpha(col, 0.4 * pulse)
		ctx.lineWidth = 2
		ctx.setLineDash([8, 8])
		ctx.beginPath()
		ctx.arc(b.x, b.y, b.radius, 0, PI * 2)
		ctx.stroke()
		ctx.setLineDash([])
		# soft fill
		var g := ctx.createRadialGradient(b.x, b.y, 1, b.x, b.y, b.radius)
		g.addColorStop(0, Palette.withAlpha(col, 0.07))
		g.addColorStop(1, Palette.withAlpha(col, 0))
		ctx.fillStyle = g
		ctx.beginPath()
		ctx.arc(b.x, b.y, b.radius, 0, PI * 2)
		ctx.fill()
		# pole + waving flag
		ctx.strokeStyle = "#5a4632"
		ctx.lineWidth = 2.5
		ctx.beginPath()
		ctx.moveTo(b.x, b.y)
		ctx.lineTo(b.x, b.y - 26)
		ctx.stroke()
		ctx.fillStyle = col
		var w := 5 + sin(time * 6 + b.y) * 1.5
		ctx.beginPath()
		ctx.moveTo(b.x, b.y - 26)
		ctx.lineTo(b.x + 16, b.y - 23 + w * 0.4)
		ctx.lineTo(b.x, b.y - 16)
		ctx.fill()
		ctx.fillStyle = "#ffe07a"
		ctx.beginPath()
		ctx.arc(b.x, b.y - 27, 2, 0, PI * 2)
		ctx.fill()

	# King of the Hill: the central control zone.
	if world.mode == "koth":
		ctx.save()
		ctx.strokeStyle = Palette.withAlpha("#ffe07a", 0.85)
		ctx.lineWidth = 3
		ctx.setLineDash([10, 8])
		ctx.beginPath()
		ctx.arc(world.hillX, world.hillY, world.hillR, 0, PI * 2)
		ctx.stroke()
		ctx.setLineDash([])
		ctx.fillStyle = Palette.withAlpha("#ffe07a", 0.07)
		ctx.fill()
		ctx.restore()

	_phase("water+banners")
	# The drawable entities (y-sorted when the snapshot was taken), culled to
	# this frame's view, and tweened toward this frame's fraction between sim
	# ticks so motion is smooth at 60fps over the 20Hz sim. The proxies take
	# the interpolated positions; the simulation's own are untouched.
	var drawables: Array = []
	var ghostFlags: Array = []
	var idleFarm: Array = []
	var ents := world.entities
	for i in ents.size():
		var cx: float = world.curX[i]
		var cy: float = world.curY[i]
		if cx < vx0 or cx > vx1 or cy < vy0 or cy > vy1:
			continue
		var e: Entity = ents[i]
		if alpha < 1:
			e.x = e.prevX + (cx - e.prevX) * alpha
			e.y = e.prevY + (cy - e.prevY) * alpha
		else:
			e.x = cx
			e.y = cy
		drawables.append(e)
		ghostFlags.append(world.ghosted[i] == 1)
		idleFarm.append(world.farmIdle[i] == 1)
	# Level-of-detail: when zoomed out or there's a big crowd on screen, draw
	# units as cheap blobs (skip auras/chevrons/weapons) — the detail is
	# sub-pixel anyway and this is what keeps a high-pop 8v8 smooth. "Reduce
	# effects" makes it kick in sooner for weaker machines.
	var zoomCut := 0.82 if aggressiveLod else 0.62
	var crowdCut := 160 if aggressiveLod else 340
	var lod := 1 if cam.zoom < zoomCut or drawables.size() > crowdCut else 0

	_phase("cull")
	# Selection rings under everything else.
	for e: Entity in drawables:
		if e.selected:
			Draw.drawSelectionRing(ctx, e, e.team == viewTeam)
	# Your finished farms with nobody working them: a small pulsing marker, so
	# an idle field is as easy to spot as an idle villager.
	if not world.revealAll:
		var pulse := 0.55 + 0.45 * sin(time * 3)
		for i in drawables.size():
			if not idleFarm[i]:
				continue
			var e: Entity = drawables[i]
			ctx.fillStyle = Color(1.0, 200.0 / 255.0, 90.0 / 255.0, 0.75 * pulse)
			ctx.strokeStyle = "rgba(40, 24, 8, 0.8)"
			ctx.lineWidth = 1.5
			ctx.beginPath(); ctx.arc(e.x, e.y - 4, 5, 0, PI * 2); ctx.fill(); ctx.stroke()
			ctx.beginPath(); ctx.moveTo(e.x - 6, e.y + 8); ctx.quadraticCurveTo(e.x, e.y - 1, e.x + 6, e.y + 8); ctx.fill(); ctx.stroke()

	# Rally line from a selected production building.
	# Every selected production building's rally, not just the first.
	for rf in rallyFrom:
		if rf.rallyX < 0:
			continue
		ctx.strokeStyle = Palette.withAlpha("#7df27d", 0.5)
		ctx.lineWidth = 1.5
		ctx.setLineDash([6, 6])
		ctx.beginPath()
		ctx.moveTo(rf.x, rf.y)
		ctx.lineTo(rf.rallyX, rf.rallyY)
		ctx.stroke()
		ctx.setLineDash([])
		ctx.fillStyle = Palette.withAlpha("#7df27d", 0.8)
		ctx.beginPath()
		ctx.arc(rf.rallyX, rf.rallyY, 5, 0, PI * 2)
		ctx.fill()

	_phase("rings+rally")
	# Lights are gathered while the entities are at their drawn (interpolated)
	# positions, so a lantern never trails the unit carrying it.
	lights.clear()
	var lighting := DayNight.nightAmount(dayPhase) > 0.01
	var lanterns := lighting and not aggressiveLod and not world.revealAll

	for i in drawables.size():
		var e: Entity = drawables[i]
		var ghosted: bool = ghostFlags[i]
		if ghosted:
			ctx.globalAlpha = 0.45
		match e.kind:
			T.Kind.Resource: Draw.drawResource(ctx, e, time)
			T.Kind.Building: Draw.drawBuilding(ctx, e, time, viewTeam)
			T.Kind.Unit: Draw.drawUnit(ctx, e, time, lod)
			T.Kind.Projectile: Draw.drawProjectile(ctx, e)
		if ghosted:
			ctx.globalAlpha = 1
		# A remembered enemy building is a memory, not a light source.
		if lighting and not ghosted and e.kind != T.Kind.Resource and e.kind != T.Kind.Projectile:
			var li := Nightlight.lightFor(e, time, lanterns and e.team == viewTeam)
			if li != null:
				lights.append(li)

	_phase("entities")
	# Health bars for hovered, selected or recently-damaged entities. Zoomed
	# out (lod), only the selected get one — the rest are too small to read.
	for e: Entity in drawables:
		if e.kind != T.Kind.Unit and e.kind != T.Kind.Building:
			continue
		var show := e.selected or e.id == hoveredId or (lod == 0 and e.hp < e.maxHp and time - e.lastDamageTime < 6)
		if not show:
			continue
		if e.kind == T.Kind.Building and e.buildState != T.BuildState.Done:
			continue
		Draw.drawHealthBar(ctx, e)

	_phase("healthbars")
	# Particles. Each a disc, and a glowing one its canvas shadow (blur 8 px:
	# the disc blurred by a gaussian of σ = 4 px) — one or two textured quads
	# on a retained item rather than tessellated circles and shadow layers.
	if not fxItem.is_valid():
		fxItem = _fxItem()
	RenderingServer.canvas_item_clear(fxItem)
	var disc := _discTex().get_rid()
	var glow := _glowTex().get_rid()
	var sigW := 4.0 / cam.zoom  # the shadow's σ, in world units
	for p: Particles.Particle in particles.pool:
		if not p.active:
			continue
		if p.x < vx0 or p.x > vx1 or p.y < vy0 or p.y > vy1:
			continue
		var lifeFrac := p.life / p.maxLife if p.maxLife > 0 else 0.0
		var a := clampf(lifeFrac, 0, 1) if p.fade else 1.0
		var rawSize := p.size * lifeFrac if p.shrink else p.size
		var size := maxf(0.4, rawSize) if is_finite(rawSize) else 0.4
		var col := _color(p.color)
		col.a *= a
		if p.glow:
			# A blurred disc is close to a gaussian of σ' = √(σ² + r²/4),
			# peaking at 1 − e^(−r²/2σ²).
			var sg := sqrt(sigW * sigW + size * size / 4.0)
			var ext := sg * 3.0
			var g := col
			g.a *= 1.0 - exp(-size * size / (2.0 * sigW * sigW))
			RenderingServer.canvas_item_add_texture_rect(fxItem, Rect2(p.x - ext, p.y - ext, ext * 2, ext * 2), glow, false, g)
		RenderingServer.canvas_item_add_texture_rect(fxItem, Rect2(p.x - size, p.y - size, size * 2, size * 2), disc, false, col)
	ctx.external(fxItem)

	_phase("particles")
	# Floating combat numbers above the fray.
	drawFloaters(ctx, dt)

	# Command markers (shrinking rings).
	for m in markers:
		var t: float = m.age / 0.7
		if t > 1:
			continue
		var r := 16 * (1 - t) + 4
		ctx.strokeStyle = Palette.withAlpha("#f25d4a" if m.kind == "attack" else "#7df27d", 1 - t)
		ctx.lineWidth = 2
		ctx.beginPath()
		ctx.arc(m.x, m.y, r, 0, PI * 2)
		ctx.stroke()

	_phase("floaters+markers")
	# Fog of war overlay (skipped entirely for spectators — they see all).
	fogDirtyTimer -= dt
	if not world.revealAll and fogLayer != null:
		# Same viewport culling as terrain — only upscale the visible fog slice.
		var fox := world.fogCols / world.worldW
		var foy := world.fogRows / world.worldH
		var cx0 := maxf(0, vx0)
		var cy0 := maxf(0, vy0)
		var cx1 := minf(world.worldW, vx1)
		var cy1 := minf(world.worldH, vy1)
		if cx1 > cx0 and cy1 > cy0:
			ctx.blit_premul(fogLayer.tex,
				Rect2(cx0 * fox, cy0 * foy, (cx1 - cx0) * fox, (cy1 - cy0) * foy),
				Rect2(cx0, cy0, cx1 - cx0, cy1 - cy0))

	# Building placement: drawn over the fog, so a site in the dark is as
	# readable as one in the open.
	if ghost != null or not builderLines.is_empty():
		drawGhost(ctx, ghost, time)

	ctx.restore()

	_phase("fog+ghost")
	# ---- screen-space overlays ----
	# Day/night sky over the whole battlefield (screen space, under HUD). By
	# day it is a plain tint; at night the lights gathered above cut through it.
	var tint := DayNight.skyTint(dayPhase)
	var flat := Color(0, 0, 0, 0)  # a plain tint, composited in the vignette's pass
	if tint[3] > 0.003:
		var night := DayNight.nightAmount(dayPhase)
		if night > 0.01 and not lights.is_empty():
			var ox := W / 2 + shakeX
			var oy := H / 2 + shakeY
			var z := cam.zoom
			var camx := cam.x
			var camy := cam.y
			nightLayer.draw(octx, W, H, tint, night, lights, cam.zoom,
				func(x: float, y: float) -> Vector2: return Vector2((x - camx) * z + ox, (y - camy) * z + oy))
		else:
			flat = Color8(int(tint[0]), int(tint[1]), int(tint[2]), int(clampf(tint[3], 0, 1) * 255.0 + 0.5))

	# Cinematic vignette — a radial falloff to 30% dark at the corners (the TS
	# caches it per canvas size; here it is a shader quad, rebuilt on resize).
	# The day's plain tint (a full-screen fill under it in the TS) is laid in
	# the same pass: tint, then vignette over it, as the two blends would.
	_vignette(W, H)
	vignetteMat.set_shader_parameter("tint", flat)
	octx.external(vignetteItem)

	# Drag selection box (screen space).
	if dragBox.active:
		octx.strokeStyle = Palette.withAlpha("#7df27d", 0.9)
		octx.fillStyle = Palette.withAlpha("#7df27d", 0.08)
		octx.lineWidth = 1.5
		var x := minf(dragBox.x0, dragBox.x1)
		var y := minf(dragBox.y0, dragBox.y1)
		var w := absf(dragBox.x1 - dragBox.x0)
		var h := absf(dragBox.y1 - dragBox.y0)
		octx.fillRect(x, y, w, h)
		octx.strokeRect(x, y, w, h)
	_phase("overlays")
	lastRenderMs = (Time.get_ticks_usec() - t0) / 1000.0


func _vignette(W: float, H: float) -> void:
	var sz := Vector2i(int(W), int(H))
	if vignetteItem.is_valid() and sz == vignetteSize:
		return
	vignetteSize = sz
	if not vignetteItem.is_valid():
		vignetteItem = RenderingServer.canvas_item_create()
		vignetteMat = ShaderMaterial.new()
		var sh := Shader.new()
		sh.code = VIGNETTE_SHADER
		vignetteMat.shader = sh
		RenderingServer.canvas_item_set_material(vignetteItem, vignetteMat.get_rid())
	vignetteMat.set_shader_parameter("size_px", Vector2(W, H))
	vignetteMat.set_shader_parameter("r0", minf(W, H) * 0.42)
	vignetteMat.set_shader_parameter("r1", sqrt(W * W + H * H) * 0.62)
	RenderingServer.canvas_item_clear(vignetteItem)
	RenderingServer.canvas_item_add_rect(vignetteItem, Rect2(0, 0, W, H), Color.WHITE)


## createRadialGradient(W/2, H/2, min(W,H)·0.42, W/2, H/2, hypot(W,H)·0.62)
## from rgba(8,6,3,0) to rgba(8,6,3,0.30).
const VIGNETTE_SHADER := """
shader_type canvas_item;
render_mode blend_premul_alpha;
uniform vec2 size_px;
uniform float r0;
uniform float r1;
uniform vec4 tint;
varying vec2 pos;
void vertex() { pos = VERTEX; }
void fragment() {
	float d = length(pos - size_px * 0.5);
	float t = clamp((d - r0) / (r1 - r0), 0.0, 1.0);
	float va = 0.30 * t;
	vec3 vc = vec3(8.0, 6.0, 3.0) / 255.0;
	// tint over the scene, then the vignette over that, as one premultiplied
	// source: rgb = tint·a1·(1 − a2) + vc·a2, a = 1 − (1 − a1)(1 − a2).
	vec3 rgb = tint.rgb * tint.a * (1.0 - va) + vc * va;
	COLOR = vec4(rgb, 1.0 - (1.0 - tint.a) * (1.0 - va));
}
"""


## Free everything this renderer holds on the RenderingServer, now (the
## game calls it on the way out, while the server is still up).
func free_gpu() -> void:
	for it in [vignetteItem, fxItem, decalItem]:
		if (it as RID).is_valid():
			ctx.forget(it)
			octx.forget(it)
			RenderingServer.free_rid(it)
	vignetteItem = RID()
	fxItem = RID()
	decalItem = RID()
	if terrainCache != null and terrainCache.layer != null:
		terrainCache.layer.free_layer()
	terrainCache = null
	if detail != null:
		ctx.forget(detail.root)
		detail.free_all()
	detail = null
	if fogLayer != null:
		fogLayer.free_layer()
	fogLayer = null
	if nightLayer.layer != null:
		nightLayer.layer.free_layer()
		nightLayer.layer = null


func _phase(name: String) -> void:
	var t := Time.get_ticks_usec()
	phaseMs[name] = (t - _pt) / 1000.0
	_pt = t
