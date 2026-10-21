## render/draw.ts — procedural entity art. Everything is drawn in world space
## (the renderer applies the camera transform). Style: soft top-down-three-
## quarter "toy soldier" look — chunky silhouettes, team-colored cloth, warm
## shadows.
##
## The art is a line-for-line port. What is Godot-only: `drawUnit` and
## `drawBuilding` serve the figure from ArtCache (a texture atlas of already-
## drawn poses) when `ArtCache.enabled`, and only draw procedurally on a miss
## (or always, with the cache off). See art_cache.gd.
class_name Draw

const LIT_BUILDINGS := {  # render/nightlight.ts LIT_BUILDINGS
	"town_center": true, "house": true, "mill": true, "barracks": true, "archery_range": true, "stable": true,
	"blacksmith": true, "market": true, "castle": true, "watch_tower": true, "siege_workshop": true,
}


## content/daynight.ts isNight
static func isNight(phase: float) -> bool:
	return phase > 0.55 and phase < 0.95


## meta/rarity.ts rarityByIndex
static func rarityByIndex(i: int) -> Dictionary:
	var r: Array = C.RARITIES
	return r[clampi(i, 0, r.size() - 1)]


## Draw a baked sprite centered at the local origin, sized to the radius.
static func blitSprite(ctx: Ctx, sp: Dictionary, radius: float) -> void:
	var w: float = radius * 2.6 * sp.scale
	var tex: Texture2D = sp.img
	var iw: float = tex.get_width() if tex.get_width() > 0 else w
	var ih: float = tex.get_height() if tex.get_height() > 0 else w
	var h := w * (ih / iw)
	# anchorY 1 = feet on the origin, 0.5 = centered.
	ctx.drawImage(tex, -w / 2, -h * sp.anchorY, w, h)


# The renderer pushes the live day-cycle phase here each frame so world-space
# art (watchfire glow, etc.) can react to night without threading it through
# every draw signature. Derived from deterministic sim time upstream.
static var gDayPhase := 0.0


static func setDrawDayPhase(p: float) -> void:
	gDayPhase = p


# Optional viewer-relative colour resolver (diplomacy colouring in team games).
# When set, entities are coloured by their relation to the viewer instead of by
# raw team. Null = use the global per-team colours (1v1 / FFA).
static var gColorResolver = null  ## Callable(team) -> Dictionary, or null


static func setTeamColorResolver(fn) -> void:
	gColorResolver = fn


static func tcol(team: int) -> Dictionary:
	return gColorResolver.call(team) if gColorResolver != null else Palette.teamColor(team)


# Which faction each team is, pushed by the renderer each frame. Drawing an
# entity sets `gLook` to its owner's look, and the shared building and soldier
# parts below redraw themselves in that style (render/faction_art.ts). The
# Kingdom's look is the original art, untouched.
static var gFactionResolver = null  ## Callable(team) -> String or null


static func setFactionResolver(fn) -> void:
	gFactionResolver = fn


static var gLook: Dictionary = {}
static var gStyled := false  ## gLook !== KINGDOM_LOOK
static var gFaction := "kingdom"
static var gTC: Dictionary = {}
static var gType := ""
static var gSeed := 0
static var gTime := 0.0
## What `body` learned about the figure, for `head` to dress it to match.
static var gFigure := {"heavy": false, "soldier": true, "step": 0.0, "seed": 0}
## Units that keep their own headgear whatever their faction.
const OWN_HEADGEAR := {"monk": true, "king": true, "hero": true}


## content/factions.ts factionOf(id).id
static func factionIdOf(id) -> String:
	if id != null and C.FACTIONS.has(id):
		return id
	return C.DEFAULT_FACTION


static func useLook(team: int, type: String, seed: int = 0, time: float = 0.0) -> void:
	var id = gFactionResolver.call(team) if gFactionResolver != null else null
	gFaction = factionIdOf(id)
	gLook = C.FACTIONS[gFaction].look
	gStyled = gFaction != "kingdom"
	gTC = tcol(team)
	gType = type
	gSeed = seed
	gTime = time


static func styled() -> bool:
	return gStyled


static func shadow(ctx: Ctx, x: float, y: float, rx: float, ry: float, alpha: float = 0.25) -> void:
	ctx.fillStyle = Color(20 / 255.0, 24 / 255.0, 12 / 255.0, alpha)  # rgba(20, 24, 12, alpha)
	ctx.beginPath()
	ctx.ellipse(x, y, rx, ry, 0, 0, PI * 2)
	ctx.fill()


# A bold team-coloured ground disc + ring under every unit — the clearest "who
# owns this" cue, readable at a glance even in a big mixed-team brawl.
static func teamRing(ctx: Ctx, x: float, y: float, r: float, tc: Dictionary) -> void:
	var rx := r * 1.15
	var ry := r * 0.52
	# Soft filled disc.
	ctx.fillStyle = Palette.withAlpha(tc.main, 0.42)
	ctx.beginPath()
	ctx.ellipse(x, y, rx, ry, 0, 0, PI * 2)
	ctx.fill()
	# Dark contrast rim so the colour reads on any terrain, then a bright ring.
	ctx.lineWidth = 3
	ctx.strokeStyle = "rgba(8, 8, 6, 0.55)"
	ctx.beginPath()
	ctx.ellipse(x, y, rx + 0.5, ry + 0.5, 0, 0, PI * 2)
	ctx.stroke()
	ctx.lineWidth = 2
	ctx.strokeStyle = tc.light
	ctx.beginPath()
	ctx.ellipse(x, y, rx, ry, 0, 0, PI * 2)
	ctx.stroke()


# ---------------------------------------------------------------- resources --

## Cheap per-entity variation: `v(n)` in the TS (`seed >> n` is a signed
## 32-bit shift there, so a high seed gives a negative value, as here).
static func _v(seed: int, n: int) -> float:
	return ((JS.i32(seed) >> (n * 3)) % 100) / 100.0


static func drawResource(ctx: Ctx, e: Entity, time: float) -> void:
	var PAL: Dictionary = C.PAL
	var frac := maxf(0.25, minf(1, e.amount / 125))
	var seed := JS.u32(e.id * 2654435761)

	if e.type == "tree":
		var sway := sin(time * 0.8 + e.id) * 0.8
		shadow(ctx, e.x + 3, e.y + 5, 11 * frac, 5 * frac)
		# trunk with a little rounded shading
		ctx.fillStyle = grad(ctx, e.x - 2, e.y, e.x + 2, e.y, Palette.shade(PAL.trunk, 0.12), Palette.shade(PAL.trunk, -0.18))
		ctx.fillRect(e.x - 2, e.y - 4, 4, 9)
		# dark canopy underlayer for a crisp silhouette
		ctx.fillStyle = Palette.shade(PAL.foliage1, -0.22)
		ctx.beginPath()
		ctx.arc(e.x + sway * 0.4, e.y - 6, 10 * frac + _v(seed, 2) * 2, 0, PI * 2)
		ctx.fill()
		# layered canopy
		var layers := [
			[9 * frac, 4, PAL.foliage1],
			[7 * frac, 0, PAL.foliage2],
			[5 * frac, -3, PAL.foliage3],
		]
		for l in layers:
			ctx.fillStyle = Palette.shade(l[2], _v(seed, 1) * 0.12 - 0.06)
			ctx.beginPath()
			ctx.arc(e.x + sway * 0.4, e.y - 8 + l[1], l[0] + _v(seed, 2) * 2, 0, PI * 2)
			ctx.fill()
		# sun-side highlight
		ctx.fillStyle = Palette.withAlpha("#dff0c0", 0.3)
		ctx.beginPath()
		ctx.arc(e.x - 3 + sway * 0.4, e.y - 12, 3.5 * frac, 0, PI * 2)
		ctx.fill()
	elif e.type == "gold_mine":
		shadow(ctx, e.x + 2, e.y + 6, 13 * frac + 2, 5)
		# rock pile
		ctx.fillStyle = grad(ctx, e.x, e.y - 9 * frac, e.x, e.y + 7, Palette.shade(PAL.goldRock, 0.12), Palette.shade(PAL.goldRock, -0.1))
		ctx.beginPath()
		ctx.moveTo(e.x - 13 * frac, e.y + 7)
		ctx.lineTo(e.x - 6, e.y - 9 * frac)
		ctx.lineTo(e.x + 3, e.y - 5 * frac - 4)
		ctx.lineTo(e.x + 13 * frac, e.y + 7)
		ctx.closePath()
		ctx.fill()
		ctx.fillStyle = Palette.shade(PAL.goldRock, -0.18)
		ctx.beginPath()
		ctx.moveTo(e.x, e.y + 7)
		ctx.lineTo(e.x + 3, e.y - 5 * frac - 4)
		ctx.lineTo(e.x + 13 * frac, e.y + 7)
		ctx.closePath()
		ctx.fill()
		# gold veins
		ctx.fillStyle = PAL.goldVein
		for i in 4:
			var gx: float = e.x - 8 + _v(seed, i) * 16
			var gy: float = e.y + 4 - _v(seed, i + 3) * 9 * frac
			ctx.beginPath()
			ctx.arc(gx, gy, 1.6 + _v(seed, i + 1), 0, PI * 2)
			ctx.fill()
	elif e.type == "berries":
		shadow(ctx, e.x + 1, e.y + 4, 9 * frac, 4)
		ctx.fillStyle = Palette.shade(PAL.berryBush, _v(seed, 1) * 0.1 - 0.05)
		ctx.beginPath()
		ctx.arc(e.x, e.y - 2, 8 * frac, 0, PI * 2)
		ctx.arc(e.x - 5 * frac, e.y + 1, 5 * frac, 0, PI * 2)
		ctx.arc(e.x + 5 * frac, e.y + 1, 5 * frac, 0, PI * 2)
		ctx.fill()
		ctx.fillStyle = PAL.berry
		for i in 5:
			ctx.beginPath()
			ctx.arc(e.x - 5 + _v(seed, i) * 10, e.y - 4 + _v(seed, i + 2) * 7, 1.4, 0, PI * 2)
			ctx.fill()


# ---------------------------------------------------------------- buildings --

static func drawBuilding(ctx: Ctx, e: Entity, time: float, selectedTeamView: int = 0) -> void:
	var half := e.radius
	var tc := tcol(e.team)
	useLook(e.team, e.type)

	if e.buildState != T.BuildState.Done:
		if not (ArtCache.enabled and ArtCache.drawConstruction(ctx, e, half, tc, time)):
			drawConstruction(ctx, e, half, tc, time)
		return

	if not (ArtCache.enabled and ArtCache.drawBuildingArt(ctx, e, half, tc, time)):
		drawBuildingArt(ctx, e, half, tc, time)
	drawBuildingOverlays(ctx, e, half, time)


## The per-frame parts of a finished building, drawn over its art: lit windows
## after dark, scorching, and the hit flash. (Inline in the TS drawBuilding.)
static func drawBuildingOverlays(ctx: Ctx, e: Entity, half: float, time: float) -> void:
	# After dark, lived-in buildings show warm lit windows. The pool of light
	# around them belongs to the night layer (nightlight.ts), which is what lets
	# it show through the darkness instead of being painted underneath it.
	if isNight(gDayPhase) and LIT_BUILDINGS.has(e.type):
		var flick := 0.8 + sin(time * 6 + e.id * 1.7) * 0.12
		ctx.fillStyle = Palette.withAlpha("#ffd98a", 0.9 * flick)
		var wn := 3 if e.radius > 40 else 2
		for i in wn:
			var wx := e.x + ((i + 0.5) / wn - 0.5) * half * 1.1
			ctx.fillRect(wx - 1.5, e.y + half * 0.18, 3, 4)

	# Damage smoke handled by game-side particles; here add scorched look.
	var dmg := 1 - e.hp / e.maxHp
	if dmg > 0.4:
		ctx.fillStyle = Palette.withAlpha("#1a140c", minf(0.45, (dmg - 0.4) * 0.9))
		ctx.fillRect(e.x - half, e.y - half, half * 2, half * 2)
	if e.hitFlash > 0:
		ctx.fillStyle = Palette.withAlpha("#ffffff", e.hitFlash * 0.35)
		ctx.fillRect(e.x - half, e.y - half, half * 2, half * 2)


## The finished building — also what a construction site rises into.
static func drawBuildingArt(ctx: Ctx, e: Entity, half: float, tc: Dictionary, time: float) -> void:
	shadow(ctx, e.x + half * 0.12, e.y + half * 0.55, half * 1.05, half * 0.45, 0.3)

	var bsprite = Sprites.spriteFor(e.type)
	if bsprite != null:
		ctx.save()
		ctx.translate(e.x, e.y)
		blitSprite(ctx, bsprite, half)
		ctx.restore()
		return
	match e.type:
		"town_center": drawTownCenter(ctx, e, half, tc, time)
		"house": drawHouse(ctx, e, half, tc)
		"mill": drawMill(ctx, e, half, tc, time)
		"lumber_camp": drawLumberCamp(ctx, e, half)
		"mining_camp": drawMiningCamp(ctx, e, half)
		"farm": drawFarm(ctx, e, half)
		"bridge": drawBridge(ctx, e, half)
		"barracks": drawBarracks(ctx, e, half, tc)
		"archery_range": drawArcheryRange(ctx, e, half, tc)
		"stable": drawStable(ctx, e, half, tc)
		"blacksmith": drawBlacksmith(ctx, e, half, tc, time)
		"market": drawMarket(ctx, e, half, tc)
		"palisade": drawPalisade(ctx, e, half)
		"stone_wall": drawStoneWall(ctx, e, half)
		"gate": drawGate(ctx, e, half, tc)
		"watchfire": drawWatchfire(ctx, e, half, time)
		"watch_tower": drawTower(ctx, e, half, tc)
		"castle": drawCastle(ctx, e, half, tc, time)
		"siege_workshop": drawSiegeWorkshop(ctx, e, half)
		_:
			ctx.fillStyle = C.PAL.stone
			ctx.fillRect(e.x - half, e.y - half, half * 2, half * 2)


# Buildings that lie flat on the ground: nothing to scaffold, so they fade in.
const FLAT_BUILDINGS := {"farm": true, "bridge": true}
# Buildings whose art stands well above their footprint.
const TALL_BUILDINGS := {"watch_tower": true, "castle": true, "mill": true, "town_center": true}


## A building site. It used to be a brown square with four posts — the same for
## a house as for a castle, and unchanged from the moment it was placed until
## it popped into being. Now the site shows what it will become: a faint
## outline of the finished building over a staked-out pad, and once work starts
## the real building rising out of the ground behind scaffolding, course by
## course, with the scaffold climbing just ahead of it.
static func drawConstruction(ctx: Ctx, e: Entity, half: float, tc: Dictionary, time: float) -> void:
	var PAL: Dictionary = C.PAL
	var started := e.buildState == T.BuildState.UnderConstruction
	var p := maxf(0, minf(1, e.buildProgress)) if started else 0.0
	var flat := FLAT_BUILDINGS.has(e.type)

	# Trampled pad, a little inset and rounded so it reads as worked ground.
	ctx.fillStyle = Palette.withAlpha(PAL.dirt, 0.85)
	ctx.beginPath()
	ctx.roundRect(e.x - half + 1, e.y - half + 1, half * 2 - 2, half * 2 - 2, minf(6, half * 0.3))
	ctx.fill()

	# What it will be: a ghost of the finished building...
	var saved := ctx.globalAlpha
	ctx.globalAlpha = saved * (0.25 + 0.75 * p if flat else 0.2)
	drawBuildingArt(ctx, e, half, tc, time)
	ctx.globalAlpha = saved

	# ...and, for anything with walls, the part already built, rising from the
	# ground up.
	var top := e.y - half * (2.0 if TALL_BUILDINGS.has(e.type) else 1.4)
	var bottom := e.y + half * 1.05
	var line := bottom - (bottom - top) * p
	if not flat and p > 0:
		ctx.save()
		ctx.beginPath()
		ctx.rect(e.x - half * 1.8, line, half * 3.6, bottom - line + 2)
		ctx.clip()
		drawBuildingArt(ctx, e, half, tc, time)
		ctx.restore()

	# Survey stakes and string around the plot.
	var k := half - 3
	ctx.strokeStyle = Palette.withAlpha("#efe3c4", 0.7)
	ctx.lineWidth = 0.8
	ctx.strokeRect(e.x - k, e.y - k, k * 2, k * 2)
	ctx.fillStyle = PAL.woodDark
	for s in [[-1, -1], [1, -1], [-1, 1], [1, 1]]:
		ctx.fillRect(e.x + s[0] * k - 1.5, e.y + s[1] * k - 4, 3, 6)

	# Scaffolding, climbing just ahead of the walls. Poles and ledgers only —
	# bracing across the whole face turned every site into a net.
	if not flat and started and p < 1:
		var y1 := maxf(top, line - 7)
		var bays := 3 if half > 40 else 2
		var x0 := e.x - half * 0.98
		var x1 := e.x + half * 0.98
		ctx.strokeStyle = Palette.withAlpha(PAL.woodLight, 0.9)
		ctx.lineWidth = 1.5
		ctx.beginPath()
		for i in bays + 1:
			var x := x0 + ((x1 - x0) * i) / bays
			ctx.moveTo(x, bottom)
			ctx.lineTo(x, y1)
		ctx.stroke()
		ctx.strokeStyle = Palette.withAlpha(PAL.woodLight, 0.65)
		ctx.lineWidth = 1.1
		ctx.beginPath()
		var y := bottom - 11
		while y > y1 + 2:
			ctx.moveTo(x0 - 2, y)
			ctx.lineTo(x1 + 2, y)
			y -= 11
		# The working platform at the top, where the builders are.
		ctx.moveTo(x0 - 3, y1 + 1)
		ctx.lineTo(x1 + 3, y1 + 1)
		ctx.stroke()
		# A single brace in the end bays keeps it from reading as a grid.
		ctx.strokeStyle = Palette.withAlpha(PAL.woodDark, 0.6)
		ctx.lineWidth = 1
		var bw := (x1 - x0) / bays
		ctx.beginPath()
		ctx.moveTo(x0, bottom); ctx.lineTo(x0 + bw, y1 + 1)
		ctx.moveTo(x1, bottom); ctx.lineTo(x1 - bw, y1 + 1)
		ctx.stroke()

	# Materials waiting on site, used up as the work goes on.
	var left := 1 - p
	if left > 0.05 and half >= 14:
		var mx := e.x + half * 0.55
		var my := e.y + half * 0.62
		var logs := maxi(1, int(JS.round(3 * left)))
		for i in logs:
			ctx.fillStyle = PAL.wood if i % 2 else Palette.shade(PAL.wood, 0.08)
			ctx.fillRect(mx - 7 + (i % 2) * 2, my - i * 3, 12, 3)
			ctx.fillStyle = PAL.woodLight
			ctx.fillRect(mx + 4 + (i % 2) * 2, my - i * 3, 2, 3)

	if started:
		ctx.strokeStyle = PAL.uiGood
		ctx.lineWidth = 3
		ctx.beginPath()
		ctx.arc(e.x, e.y, half + 6, -PI / 2, -PI / 2 + p * PI * 2)
		ctx.stroke()


static func roofGable(ctx: Ctx, x: float, y: float, w: float, h: float, color) -> void:
	if styled():
		FactionArt.factionRoof(ctx, gLook, x, y + h / 2, w / 2 + 2, y - h / 2, {"main": color, "light": Palette.shade(color, 0.3), "dark": Palette.shade(color, -0.3)})
		return
	ctx.fillStyle = color
	ctx.beginPath()
	ctx.moveTo(x - w / 2 - 2, y + h / 2)
	ctx.lineTo(x, y - h / 2)
	ctx.lineTo(x + w / 2 + 2, y + h / 2)
	ctx.closePath()
	ctx.fill()
	ctx.fillStyle = Palette.withAlpha("#ffffff", 0.12)
	ctx.beginPath()
	ctx.moveTo(x - w / 2 - 2, y + h / 2)
	ctx.lineTo(x, y - h / 2)
	ctx.lineTo(x + w * 0.1, y - h / 2 + 2)
	ctx.lineTo(x - w * 0.35, y + h / 2)
	ctx.closePath()
	ctx.fill()


static func banner(ctx: Ctx, x: float, y: float, tc: Dictionary, time: float, id: int) -> void:
	if styled():
		FactionArt.factionBanner(ctx, gLook, x, y, gTC, time, id)
		return
	ctx.strokeStyle = C.PAL.woodDark
	ctx.lineWidth = 1.5
	ctx.beginPath()
	ctx.moveTo(x, y)
	ctx.lineTo(x, y - 16)
	ctx.stroke()
	var wave := sin(time * 3 + id) * 2
	ctx.fillStyle = tc.main
	ctx.beginPath()
	ctx.moveTo(x, y - 16)
	ctx.quadraticCurveTo(x + 6, y - 15 + wave * 0.4, x + 11, y - 13 + wave)
	ctx.lineTo(x + 9, y - 9 + wave * 0.6)
	ctx.quadraticCurveTo(x + 5, y - 10, x, y - 10)
	ctx.closePath()
	ctx.fill()


# ---- shared shaded-building parts (world space) ----------------------------
## A stone wall block: vertical gradient, mortar courses, soft outline.
static func stoneBlock(ctx: Ctx, x: float, y: float, w: float, h: float, tone = null) -> void:
	if tone == null:
		tone = C.PAL.stone
	# Every faction's masonry is its own — dark forge stone included.
	if styled():
		FactionArt.factionStone(ctx, gLook, x, y, w, h)
		return
	ctx.fillStyle = grad(ctx, x, y, x, y + h, Palette.shade(tone, 0.18), Palette.shade(tone, -0.18))
	ctx.beginPath()
	ctx.roundRect(x, y, w, h, 3)
	ctx.fill()
	softOutline(ctx, 1.4)
	ctx.strokeStyle = "rgba(70,64,56,0.32)"
	ctx.lineWidth = 1
	var yy := y + h * 0.34
	while yy < y + h - 1:
		ctx.beginPath()
		ctx.moveTo(x + 2, yy)
		ctx.lineTo(x + w - 2, yy)
		ctx.stroke()
		yy += h * 0.32


## A timber-framed plaster wall (warm), with corner posts.
static func woodBlock(ctx: Ctx, x: float, y: float, w: float, h: float) -> void:
	if styled():
		FactionArt.factionTimber(ctx, gLook, x, y, w, h)
		return
	ctx.fillStyle = grad(ctx, x, y, x, y + h, "#d8c8a8", "#b49a72")
	ctx.beginPath()
	ctx.roundRect(x, y, w, h, 3)
	ctx.fill()
	softOutline(ctx, 1.4)
	ctx.fillStyle = C.PAL.woodDark  # corner posts + lintel
	ctx.fillRect(x, y, 4, h)
	ctx.fillRect(x + w - 4, y, 4, h)
	ctx.fillRect(x, y, w, 3.5)


static func cornerPosts(ctx: Ctx, x: float, y: float, w: float, h: float) -> void:
	if styled() and (gLook.roof == "yurt" or gLook.roof == "dome" or gLook.roof == "tile"):
		return
	ctx.fillStyle = gLook.frame if styled() else C.PAL.woodDark
	ctx.fillRect(x, y, 4, h)
	ctx.fillRect(x + w - 4, y, 4, h)


## A hipped tiled roof in FULL team colour, shaded with tile rows + ridge.
static func hipRoof(ctx: Ctx, cx: float, eaveY: float, halfW: float, peakY: float, tc: Dictionary) -> void:
	if styled():
		FactionArt.factionRoof(ctx, gLook, cx, eaveY, halfW, peakY, tc)
		return
	var slate = tc.main
	ctx.fillStyle = grad(ctx, cx, peakY, cx, eaveY, Palette.shade(slate, 0.24), Palette.shade(slate, -0.2))
	ctx.beginPath()
	ctx.moveTo(cx - halfW, eaveY)
	ctx.lineTo(cx - halfW * 0.42, peakY)
	ctx.lineTo(cx + halfW * 0.42, peakY)
	ctx.lineTo(cx + halfW, eaveY)
	ctx.closePath()
	ctx.fill()
	softOutline(ctx, 1.6)
	ctx.strokeStyle = "rgba(255,255,255,0.12)"  # tile courses
	ctx.lineWidth = 1
	var r := 0.25
	while r < 1:
		var y := peakY + (eaveY - peakY) * r
		var hw := halfW * 0.42 + (halfW - halfW * 0.42) * r
		ctx.beginPath()
		ctx.moveTo(cx - hw, y)
		ctx.lineTo(cx + hw, y)
		ctx.stroke()
		r += 0.25
	ctx.strokeStyle = Palette.shade(slate, 0.42)  # ridge highlight
	ctx.lineWidth = 2
	ctx.beginPath()
	ctx.moveTo(cx - halfW * 0.42, peakY)
	ctx.lineTo(cx + halfW * 0.42, peakY)
	ctx.stroke()


## An arched timber door.
static func archDoor(ctx: Ctx, cx: float, baseY: float, w: float, h: float) -> void:
	if styled():
		FactionArt.factionDoor(ctx, gLook, cx, baseY, w, h)
		return
	var PAL: Dictionary = C.PAL
	ctx.fillStyle = grad(ctx, cx, baseY - h, cx, baseY, PAL.wood, PAL.woodDark)
	ctx.beginPath()
	ctx.moveTo(cx - w / 2, baseY)
	ctx.lineTo(cx - w / 2, baseY - h * 0.55)
	ctx.quadraticCurveTo(cx, baseY - h, cx + w / 2, baseY - h * 0.55)
	ctx.lineTo(cx + w / 2, baseY)
	ctx.closePath()
	ctx.fill()
	softOutline(ctx, 1.2)
	ctx.strokeStyle = PAL.woodLight
	ctx.lineWidth = 1
	ctx.beginPath()
	ctx.moveTo(cx, baseY)
	ctx.lineTo(cx, baseY - h * 0.65)
	ctx.stroke()


## A small window with a warm-lit pane.
static func litWindow(ctx: Ctx, cx: float, cy: float, s: float) -> void:
	if styled():
		FactionArt.factionWindow(ctx, gLook, cx, cy, s)
		return
	ctx.fillStyle = "rgba(20,16,10,0.65)"
	ctx.fillRect(cx - s, cy - s, s * 2, s * 2)
	ctx.fillStyle = "#ffd98a"
	ctx.fillRect(cx - s + 1, cy - s + 1, s * 2 - 2, s * 2 - 2)
	ctx.strokeStyle = "rgba(20,16,10,0.5)"
	ctx.lineWidth = 0.8
	ctx.beginPath()
	ctx.moveTo(cx, cy - s + 1)
	ctx.lineTo(cx, cy + s - 1)
	ctx.stroke()


## A conical team-colour roof for round towers.
static func coneRoof(ctx: Ctx, cx: float, baseY: float, halfW: float, peakY: float, tc: Dictionary) -> void:
	if styled():
		FactionArt.factionCap(ctx, gLook, cx, baseY, halfW, peakY, tc)
		return
	var c = tc.main
	ctx.fillStyle = grad(ctx, cx, peakY, cx, baseY, Palette.shade(c, 0.24), Palette.shade(c, -0.2))
	ctx.beginPath()
	ctx.moveTo(cx - halfW, baseY)
	ctx.lineTo(cx, peakY)
	ctx.lineTo(cx + halfW, baseY)
	ctx.closePath()
	ctx.fill()
	softOutline(ctx, 1.4)
	ctx.strokeStyle = Palette.shade(c, 0.42)
	ctx.lineWidth = 1.4
	ctx.beginPath()
	ctx.moveTo(cx, peakY)
	ctx.lineTo(cx - halfW * 0.45, baseY)
	ctx.stroke()


static func drawTownCenter(ctx: Ctx, e: Entity, half: float, tc: Dictionary, time: float) -> void:
	# two flanking stone towers with team-colour caps
	for sx in [-1, 1]:
		var tx: float = e.x + sx * half * 0.78
		stoneBlock(ctx, tx - half * 0.24, e.y - half * 0.3, half * 0.48, half * 1.15)
		hipRoof(ctx, tx, e.y - half * 0.3, half * 0.36, e.y - half * 0.78, tc)
	# main hall
	stoneBlock(ctx, e.x - half * 0.62, e.y - half * 0.1, half * 1.24, half * 0.95)
	cornerPosts(ctx, e.x - half * 0.62, e.y - half * 0.1, half * 1.24, half * 0.95)
	litWindow(ctx, e.x - half * 0.32, e.y + half * 0.28, half * 0.1)
	litWindow(ctx, e.x + half * 0.32, e.y + half * 0.28, half * 0.1)
	archDoor(ctx, e.x, e.y + half * 0.85, half * 0.38, half * 0.5)
	hipRoof(ctx, e.x, e.y - half * 0.1, half * 0.82, e.y - half * 0.78, tc)
	banner(ctx, e.x + half * 0.7, e.y - half * 0.5, tc, time, e.id)


static func drawHouse(ctx: Ctx, e: Entity, half: float, tc: Dictionary) -> void:
	woodBlock(ctx, e.x - half * 0.72, e.y - half * 0.1, half * 1.44, half * 0.9)
	litWindow(ctx, e.x - half * 0.28, e.y + half * 0.28, half * 0.1)
	archDoor(ctx, e.x + half * 0.28, e.y + half * 0.8, half * 0.3, half * 0.42)
	hipRoof(ctx, e.x, e.y - half * 0.1, half * 0.78, e.y - half * 0.72, tc)
	# chimney + curling smoke
	ctx.fillStyle = C.PAL.stoneDark
	ctx.fillRect(e.x + half * 0.42, e.y - half * 0.7, half * 0.16, half * 0.4)
	ctx.fillStyle = "rgba(220,220,220,0.45)"
	for i in 3:
		ctx.beginPath()
		ctx.arc(e.x + half * 0.5 + i * 2, e.y - half * 0.85 - i * half * 0.22, half * 0.1 + i * 1.5, 0, PI * 2)
		ctx.fill()


static func drawMill(ctx: Ctx, e: Entity, half: float, tc: Dictionary, time: float) -> void:
	woodBlock(ctx, e.x - half * 0.5, e.y - half * 0.2, half * 1.0, half * 1.0)
	litWindow(ctx, e.x, e.y + half * 0.35, half * 0.1)
	hipRoof(ctx, e.x, e.y - half * 0.2, half * 0.66, e.y - half * 0.62, tc)
	# rotating sail blades
	var a := time * 0.9
	ctx.strokeStyle = C.PAL.woodLight
	ctx.lineWidth = 3
	for i in 4:
		var ang := a + (i * PI) / 2
		ctx.beginPath()
		ctx.moveTo(e.x, e.y - half * 0.55)
		ctx.lineTo(e.x + cos(ang) * half * 0.85, e.y - half * 0.55 + sin(ang) * half * 0.85)
		ctx.stroke()
	ctx.fillStyle = C.PAL.woodDark
	ctx.beginPath()
	ctx.arc(e.x, e.y - half * 0.55, 3, 0, PI * 2)
	ctx.fill()


static func drawLumberCamp(ctx: Ctx, e: Entity, half: float) -> void:
	var PAL: Dictionary = C.PAL
	# timber lean-to (a utility shed — no team roof)
	woodBlock(ctx, e.x - half * 0.9, e.y - half * 0.25, half * 0.95, half * 1.0)
	if styled():
		# A small roof in the faction's form over the shed.
		hipRoof(ctx, e.x - half * 0.42, e.y - half * 0.2, half * 0.62, e.y - half * 0.62, {"main": Palette.shade(gTC.main, -0.15), "light": gTC.light, "dark": gTC.dark})
	else:
		ctx.fillStyle = grad(ctx, e.x, e.y - half * 0.55, e.x, e.y - half * 0.15, Palette.shade(PAL.thatch, 0.1), PAL.thatchDark)
		ctx.beginPath()
		ctx.moveTo(e.x - half * 0.95, e.y - half * 0.15)
		ctx.lineTo(e.x - half * 0.6, e.y - half * 0.55)
		ctx.lineTo(e.x + half * 0.1, e.y - half * 0.55)
		ctx.lineTo(e.x + half * 0.05, e.y - half * 0.15)
		ctx.closePath()
		ctx.fill()
		softOutline(ctx, 1.4)
	# log pile
	ctx.fillStyle = PAL.trunk
	for i in 3:
		for j in 3 - i:
			ctx.beginPath()
			ctx.arc(e.x + half * 0.4 + j * 7 - 7 + i * 3.5, e.y + half * 0.4 - i * 6, 3.6, 0, PI * 2)
			ctx.fill()
	ctx.fillStyle = PAL.woodLight
	for i in 3:
		for j in 3 - i:
			ctx.beginPath()
			ctx.arc(e.x + half * 0.4 + j * 7 - 7 + i * 3.5, e.y + half * 0.4 - i * 6, 1.7, 0, PI * 2)
			ctx.fill()


static func drawMiningCamp(ctx: Ctx, e: Entity, half: float) -> void:
	var PAL: Dictionary = C.PAL
	stoneBlock(ctx, e.x - half * 0.9, e.y - half * 0.2, half * 1.8, half * 1.0, PAL.stoneDark)
	# mine entrance arch
	ctx.fillStyle = "#241c12"
	ctx.beginPath()
	ctx.arc(e.x, e.y + half * 0.35, half * 0.4, PI, 0)
	ctx.fill()
	ctx.strokeStyle = PAL.wood
	ctx.lineWidth = 3
	ctx.beginPath()
	ctx.arc(e.x, e.y + half * 0.35, half * 0.42, PI, 0)
	ctx.stroke()
	# cart of ore
	ctx.fillStyle = PAL.woodDark
	ctx.fillRect(e.x + half * 0.3, e.y - half * 0.1, half * 0.5, half * 0.32)
	ctx.fillStyle = PAL.goldVein
	ctx.beginPath()
	ctx.arc(e.x + half * 0.45, e.y - half * 0.1, 3, 0, PI * 2)
	ctx.arc(e.x + half * 0.6, e.y - half * 0.13, 2.5, 0, PI * 2)
	ctx.fill()


## Timber decking over the shallows. Read flat and low on purpose — a bridge is
## something troops walk *over*, and anything with height to it would read as
## an obstacle in the middle of the water, which is the opposite of what it is.
##
## A laden cart. Deliberately unmilitary — no blade, no shield, a canopy and a
## wheel — because the one thing a player must read instantly is that this is
## money walking across the map with nothing defending it.
static func drawTradeCart(ctx: Ctx, e: Entity, tc: Dictionary) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	# Bed.
	ctx.fillStyle = PAL.woodDark
	ctx.beginPath()
	ctx.roundRect(-r * 0.9, -r * 0.35, r * 1.8, r * 0.8, 2)
	ctx.fill()
	# Canopy, in the owner's colour so allied lines are legible in a melee.
	ctx.fillStyle = tc.main
	ctx.beginPath()
	ctx.moveTo(-r * 0.75, -r * 0.3)
	ctx.quadraticCurveTo(0, -r * 1.25, r * 0.75, -r * 0.3)
	ctx.closePath()
	ctx.fill()
	ctx.strokeStyle = tc.dark
	ctx.lineWidth = 1.2
	ctx.stroke()
	# Wheels.
	ctx.fillStyle = "#4a3823"
	for wx in [-r * 0.5, r * 0.5]:
		ctx.beginPath()
		ctx.arc(wx, r * 0.42, r * 0.3, 0, PI * 2)
		ctx.fill()
	# A hint of cargo.
	ctx.fillStyle = PAL.goldVein
	ctx.beginPath()
	ctx.arc(0, -r * 0.15, r * 0.18, 0, PI * 2)
	ctx.fill()


static func drawBridge(ctx: Ctx, e: Entity, half: float) -> void:
	var x := e.x - half
	var y := e.y - half
	var s := half * 2
	# The deck.
	ctx.fillStyle = C.PAL.woodDark
	ctx.fillRect(x + 1, y + 1, s - 2, s - 2)
	# Planks across the span.
	ctx.strokeStyle = "#9c7b4a"
	ctx.lineWidth = 1.6
	for i in range(1, 7):
		var px := x + (s * i) / 7
		ctx.beginPath()
		ctx.moveTo(px, y + 3)
		ctx.lineTo(px, y + s - 3)
		ctx.stroke()
	# Handrails down both long edges, so the crossing direction is legible.
	ctx.strokeStyle = "#6b4f2c"
	ctx.lineWidth = 2.4
	ctx.beginPath()
	ctx.moveTo(x + 1, y + 2.5); ctx.lineTo(x + s - 1, y + 2.5)
	ctx.moveTo(x + 1, y + s - 2.5); ctx.lineTo(x + s - 1, y + s - 2.5)
	ctx.stroke()


static func drawFarm(ctx: Ctx, e: Entity, half: float) -> void:
	ctx.fillStyle = C.PAL.dirtDark
	ctx.fillRect(e.x - half + 2, e.y - half + 2, half * 2 - 4, half * 2 - 4)
	# crop rows
	ctx.strokeStyle = "#c8b446"
	ctx.lineWidth = 2.5
	for i in 5:
		var ry := e.y - half + 6 + ((half * 2 - 12) * i) / 4
		ctx.beginPath()
		ctx.moveTo(e.x - half + 5, ry)
		ctx.lineTo(e.x + half - 5, ry)
		ctx.stroke()
	ctx.strokeStyle = C.PAL.woodDark
	ctx.lineWidth = 2
	ctx.strokeRect(e.x - half + 2, e.y - half + 2, half * 2 - 4, half * 2 - 4)


static func drawBarracks(ctx: Ctx, e: Entity, half: float, tc: Dictionary) -> void:
	stoneBlock(ctx, e.x - half * 0.78, e.y - half * 0.15, half * 1.56, half * 1.0)
	cornerPosts(ctx, e.x - half * 0.78, e.y - half * 0.15, half * 1.56, half * 1.0)
	hipRoof(ctx, e.x, e.y - half * 0.15, half * 0.95, e.y - half * 0.82, tc)
	# crossed-sword shield emblem on the wall
	ctx.fillStyle = Palette.shade(tc.main, -0.1)
	ctx.beginPath()
	ctx.moveTo(e.x, e.y - half * 0.02)
	ctx.lineTo(e.x + half * 0.2, e.y + half * 0.14)
	ctx.lineTo(e.x + half * 0.15, e.y + half * 0.42)
	ctx.lineTo(e.x, e.y + half * 0.54)
	ctx.lineTo(e.x - half * 0.15, e.y + half * 0.42)
	ctx.lineTo(e.x - half * 0.2, e.y + half * 0.14)
	ctx.closePath()
	ctx.fill()
	softOutline(ctx, 1.2)
	ctx.strokeStyle = C.PAL.steel
	ctx.lineWidth = 2
	ctx.beginPath()
	ctx.moveTo(e.x - half * 0.11, e.y + half * 0.12)
	ctx.lineTo(e.x + half * 0.11, e.y + half * 0.42)
	ctx.moveTo(e.x + half * 0.11, e.y + half * 0.12)
	ctx.lineTo(e.x - half * 0.11, e.y + half * 0.42)
	ctx.stroke()


static func drawArcheryRange(ctx: Ctx, e: Entity, half: float, tc: Dictionary) -> void:
	woodBlock(ctx, e.x - half * 0.9, e.y - half * 0.15, half * 1.1, half * 0.95)
	hipRoof(ctx, e.x - half * 0.35, e.y - half * 0.15, half * 0.66, e.y - half * 0.68, tc)
	# target butt
	var tx := e.x + half * 0.55
	var ty := e.y + half * 0.3
	for rc in [[half * 0.34, "#e6ddc4"], [half * 0.24, "#cc4444"], [half * 0.13, "#e6ddc4"], [half * 0.05, "#cc4444"]]:
		ctx.fillStyle = rc[1]
		ctx.beginPath()
		ctx.arc(tx, ty, rc[0], 0, PI * 2)
		ctx.fill()
	# stuck arrow
	ctx.strokeStyle = C.PAL.woodDark
	ctx.lineWidth = 1.5
	ctx.beginPath()
	ctx.moveTo(tx + 2, ty - 2)
	ctx.lineTo(tx + 9, ty - 9)
	ctx.stroke()


static func drawStable(ctx: Ctx, e: Entity, half: float, tc: Dictionary) -> void:
	woodBlock(ctx, e.x - half * 0.85, e.y - half * 0.1, half * 1.7, half * 0.95)
	hipRoof(ctx, e.x, e.y - half * 0.1, half * 1.0, e.y - half * 0.7, tc)
	# stall openings
	ctx.fillStyle = C.PAL.woodDark
	for i in range(-1, 2):
		ctx.beginPath()
		ctx.arc(e.x + i * half * 0.5, e.y + half * 0.5, half * 0.18, PI, 0)
		ctx.fill()
		ctx.fillRect(e.x + i * half * 0.5 - half * 0.18, e.y + half * 0.5, half * 0.36, half * 0.28)
	# horseshoe emblem
	ctx.strokeStyle = tc.light
	ctx.lineWidth = 2.5
	ctx.beginPath()
	ctx.arc(e.x, e.y + half * 0.12, half * 0.15, PI * 0.15, PI * 0.85, true)
	ctx.stroke()


static func drawBlacksmith(ctx: Ctx, e: Entity, half: float, tc: Dictionary, time: float) -> void:
	var PAL: Dictionary = C.PAL
	stoneBlock(ctx, e.x - half * 0.78, e.y - half * 0.1, half * 1.56, half * 0.95, PAL.stoneDark)
	hipRoof(ctx, e.x, e.y - half * 0.1, half * 0.95, e.y - half * 0.72, tc)
	# glowing forge window
	var glow := 0.6 + sin(time * 5 + e.id) * 0.25
	ctx.fillStyle = Palette.withAlpha(PAL.fire, glow)
	ctx.fillRect(e.x - half * 0.22, e.y + half * 0.16, half * 0.44, half * 0.34)
	ctx.fillStyle = Palette.withAlpha(PAL.fireBright, glow * 0.7)
	ctx.fillRect(e.x - half * 0.13, e.y + half * 0.22, half * 0.26, half * 0.2)
	# chimney
	ctx.fillStyle = PAL.stoneDark
	ctx.fillRect(e.x + half * 0.5, e.y - half * 0.6, half * 0.2, half * 0.5)
	# anvil silhouette
	ctx.fillStyle = "#3c4148"
	ctx.fillRect(e.x - half * 0.66, e.y + half * 0.42, half * 0.3, half * 0.12)
	ctx.fillRect(e.x - half * 0.58, e.y + half * 0.32, half * 0.15, half * 0.12)


static func drawMarket(ctx: Ctx, e: Entity, half: float, tc: Dictionary) -> void:
	var PAL: Dictionary = C.PAL
	woodBlock(ctx, e.x - half * 0.9, e.y - half * 0.05, half * 1.8, half * 0.85)
	# striped awning (team colour)
	var stripes := 6
	for i in stripes:
		ctx.fillStyle = tc.main if i % 2 == 0 else PAL.uiParchment
		var w := (half * 2 - 4) / stripes
		ctx.beginPath()
		ctx.moveTo(e.x - half + 2 + i * w, e.y - half * 0.5)
		ctx.lineTo(e.x - half + 2 + (i + 1) * w, e.y - half * 0.5)
		ctx.lineTo(e.x - half + 2 + (i + 1) * w, e.y - half * 0.1)
		ctx.arc(e.x - half + 2 + (i + 0.5) * w, e.y - half * 0.1, w / 2, 0, PI)
		ctx.closePath()
		ctx.fill()
	# goods: crates and a gold sack
	ctx.fillStyle = PAL.woodDark
	ctx.fillRect(e.x - half * 0.55, e.y + half * 0.35, half * 0.34, half * 0.3)
	ctx.fillStyle = PAL.uiParchmentDark
	ctx.beginPath()
	ctx.arc(e.x + half * 0.4, e.y + half * 0.5, half * 0.18, 0, PI * 2)
	ctx.fill()
	ctx.fillStyle = PAL.goldVein
	ctx.beginPath()
	ctx.arc(e.x + half * 0.4, e.y + half * 0.42, half * 0.07, 0, PI * 2)
	ctx.fill()


static func drawPalisade(ctx: Ctx, e: Entity, half: float) -> void:
	var PAL: Dictionary = C.PAL
	if styled() and gLook.roof == "dome":
		# A barrier fence: pylons with a beam strung between them.
		for px in [-1, 1]:
			ctx.fillStyle = grad(ctx, e.x + px * half * 0.8, e.y - half, e.x + px * half * 0.8, e.y + half, "#f0f4f8", "#9aa2ae")
			ctx.fillRect(e.x + px * half * 0.8 - 2.5, e.y - half * 0.85, 5, half * 1.4)
		ctx.save()
		ctx.shadowColor = gLook.accent; ctx.shadowBlur = 5
		ctx.fillStyle = Palette.withAlpha(gLook.accent, 0.75)
		ctx.fillRect(e.x - half * 0.8, e.y - half * 0.45, half * 1.6, 2)
		ctx.fillRect(e.x - half * 0.8, e.y + half * 0.05, half * 1.6, 2)
		ctx.restore()
		return
	# A run of sharpened logs lashed together.
	ctx.fillStyle = PAL.woodDark
	ctx.fillRect(e.x - half, e.y - 2, half * 2, 5)
	var stakes := 4
	for i in stakes:
		var sx := e.x - half + 3 + (i * (half * 2 - 6)) / (stakes - 1)
		ctx.fillStyle = Palette.shade(PAL.wood, -0.08 if i % 2 else 0.05)
		ctx.beginPath()
		ctx.moveTo(sx - 3, e.y + half * 0.5)
		ctx.lineTo(sx - 3, e.y - half * 0.5)
		ctx.lineTo(sx, e.y - half * 0.9)
		ctx.lineTo(sx + 3, e.y - half * 0.5)
		ctx.lineTo(sx + 3, e.y + half * 0.5)
		ctx.closePath()
		ctx.fill()
		ctx.fillStyle = Palette.withAlpha("#ffffff", 0.1)
		ctx.fillRect(sx - 3, e.y - half * 0.5, 1.5, half)


static func drawStoneWall(ctx: Ctx, e: Entity, half: float) -> void:
	var PAL: Dictionary = C.PAL
	var g := ctx.createLinearGradient(e.x, e.y - half, e.x, e.y + half)
	var base = gLook.stone if styled() else PAL.stone
	g.addColorStop(0, Palette.shade(base, 0.18) if styled() else PAL.stoneLight)
	g.addColorStop(1, Palette.shade(base, -0.25) if styled() else PAL.stoneDark)
	ctx.fillStyle = g
	ctx.fillRect(e.x - half, e.y - half * 0.7, half * 2, half * 1.5)
	# crenellations
	ctx.fillStyle = Palette.shade(base, -0.3) if styled() else PAL.stoneDark
	for i in 3:
		ctx.fillRect(e.x - half + i * (half * 0.72), e.y - half * 0.95, half * 0.5, half * 0.3)
	# The Ascendancy's walls carry a lit strip.
	if styled() and gLook.roof == "dome":
		ctx.fillStyle = Palette.withAlpha(gLook.accent, 0.8)
		ctx.fillRect(e.x - half, e.y - half * 0.2, half * 2, 1.6)
	# mortar lines
	ctx.strokeStyle = Palette.withAlpha(PAL.stoneDark, 0.6)
	ctx.lineWidth = 1
	ctx.beginPath()
	ctx.moveTo(e.x - half, e.y)
	ctx.lineTo(e.x + half, e.y)
	ctx.moveTo(e.x, e.y - half * 0.7)
	ctx.lineTo(e.x, e.y + half * 0.8)
	ctx.stroke()


static func drawGate(ctx: Ctx, e: Entity, half: float, tc: Dictionary) -> void:
	var PAL: Dictionary = C.PAL
	# stone posts
	ctx.fillStyle = Palette.shade(gLook.stone, -0.25) if styled() else PAL.stoneDark
	ctx.fillRect(e.x - half, e.y - half, half * 0.5, half * 2)
	ctx.fillRect(e.x + half * 0.5, e.y - half, half * 0.5, half * 2)
	ctx.fillStyle = PAL.stone
	ctx.fillRect(e.x - half + 1, e.y - half, half * 0.4, half * 0.4)
	ctx.fillRect(e.x + half * 0.55, e.y - half, half * 0.4, half * 0.4)
	# doors
	if e.gateOpen:
		ctx.fillStyle = PAL.woodDark
		ctx.save()
		ctx.translate(e.x - half * 0.5, e.y)
		ctx.rotate(-0.9)
		ctx.fillRect(-half * 0.1, -half * 0.55, half * 0.5, half * 0.5)
		ctx.restore()
		ctx.save()
		ctx.translate(e.x + half * 0.5, e.y)
		ctx.rotate(0.9)
		ctx.fillRect(-half * 0.4, -half * 0.55, half * 0.5, half * 0.5)
		ctx.restore()
	else:
		ctx.fillStyle = PAL.wood
		ctx.fillRect(e.x - half * 0.5, e.y - half * 0.7, half, half * 1.4)
		ctx.strokeStyle = PAL.woodDark
		ctx.lineWidth = 1.5
		for i in range(-1, 2):
			ctx.beginPath()
			ctx.moveTo(e.x + i * half * 0.3, e.y - half * 0.7)
			ctx.lineTo(e.x + i * half * 0.3, e.y + half * 0.7)
			ctx.stroke()
		ctx.beginPath()
		ctx.moveTo(e.x - half * 0.5, e.y)
		ctx.lineTo(e.x + half * 0.5, e.y)
		ctx.stroke()
	# team pennant on the post
	ctx.fillStyle = tc.main
	ctx.fillRect(e.x - half, e.y - half - 1, half * 2, 2.5)


static func drawWatchfire(ctx: Ctx, e: Entity, half: float, time: float) -> void:
	var flick := 0.78 + sin(time * 11 + e.x) * 0.12 + sin(time * 17 + e.y) * 0.1
	var night := isNight(gDayPhase)
	# Warm ground glow — bigger and brighter once the sun is down.
	var glowR := half * (4.6 if night else 2.4) * flick
	var g := ctx.createRadialGradient(e.x, e.y - half * 0.3, 1, e.x, e.y - half * 0.3, glowR)
	g.addColorStop(0, Palette.withAlpha("#ffd27a", 0.5 if night else 0.28))
	g.addColorStop(0.5, Palette.withAlpha("#ff9a3c", 0.22 if night else 0.1))
	g.addColorStop(1, Palette.withAlpha("#ff9a3c", 0))
	ctx.fillStyle = g
	ctx.beginPath()
	ctx.arc(e.x, e.y - half * 0.3, glowR, 0, PI * 2)
	ctx.fill()
	# Three legs of a timber brazier stand.
	ctx.strokeStyle = C.PAL.woodDark
	ctx.lineWidth = 2
	for dx in [-half * 0.55, 0.0, half * 0.55]:
		ctx.beginPath()
		ctx.moveTo(e.x + dx, e.y - half * 0.2)
		ctx.lineTo(e.x + dx * 0.35, e.y + half * 0.7)
		ctx.stroke()
	# Iron bowl.
	ctx.fillStyle = "#3a3026"
	ctx.beginPath()
	ctx.ellipse(e.x, e.y - half * 0.2, half * 0.7, half * 0.32, 0, 0, PI * 2)
	ctx.fill()
	# Flames — stacked teardrops, hot core to cool tip, dancing with the flicker.
	var fh := half * (1.5 + flick * 0.5)
	for f in [["#ff7a1c", 1.0, 0.0], ["#ffb43c", 0.66, 0.18], ["#ffe98a", 0.34, 0.34]]:
		var sc: float = f[1]
		ctx.fillStyle = f[0]
		var sway := sin(time * 9 + f[2] * 10) * half * 0.18
		ctx.beginPath()
		ctx.moveTo(e.x + sway, e.y - half * 0.35 - fh * sc)
		ctx.quadraticCurveTo(e.x - half * 0.6 * sc, e.y - half * 0.35 - fh * sc * 0.35, e.x, e.y - half * 0.3)
		ctx.quadraticCurveTo(e.x + half * 0.6 * sc, e.y - half * 0.35 - fh * sc * 0.35, e.x + sway, e.y - half * 0.35 - fh * sc)
		ctx.fill()


static func drawTower(ctx: Ctx, e: Entity, half: float, tc: Dictionary) -> void:
	var PAL: Dictionary = C.PAL
	if styled():
		# The faction's own masonry, its own cap.
		stoneBlock(ctx, e.x - half * 0.72, e.y - half * 1.05, half * 1.44, half * 1.85)
		ctx.fillStyle = "#241c12"
		ctx.fillRect(e.x - 1.5, e.y - half * 0.4, 3, half * 0.5)
		coneRoof(ctx, e.x, e.y - half * 1.0, half * 0.92, e.y - half * 1.8, tc)
		return
	# tall cylinder reads via horizontal gradient (rounded shading)
	var g := ctx.createLinearGradient(e.x - half, e.y, e.x + half, e.y)
	g.addColorStop(0, PAL.stoneLight)
	g.addColorStop(0.5, PAL.stone)
	g.addColorStop(1, PAL.stoneDark)
	ctx.fillStyle = g
	ctx.beginPath()
	ctx.roundRect(e.x - half * 0.75, e.y - half * 1.05, half * 1.5, half * 1.85, 4)
	ctx.fill()
	softOutline(ctx, 1.4)
	# mortar courses
	ctx.strokeStyle = "rgba(70,64,56,0.3)"
	ctx.lineWidth = 1
	var yy := e.y - half * 0.7
	while yy < e.y + half * 0.7:
		ctx.beginPath()
		ctx.moveTo(e.x - half * 0.72, yy)
		ctx.lineTo(e.x + half * 0.72, yy)
		ctx.stroke()
		yy += half * 0.4
	# arrow slit
	ctx.fillStyle = "#241c12"
	ctx.fillRect(e.x - 1.5, e.y - half * 0.4, 3, half * 0.5)
	# pointed team-colour roof + finial flag
	coneRoof(ctx, e.x, e.y - half * 1.0, half * 0.92, e.y - half * 1.8, tc)
	ctx.strokeStyle = PAL.woodDark
	ctx.lineWidth = 1.5
	ctx.beginPath()
	ctx.moveTo(e.x, e.y - half * 1.8)
	ctx.lineTo(e.x, e.y - half * 2.05)
	ctx.stroke()
	ctx.fillStyle = tc.light
	ctx.beginPath()
	ctx.moveTo(e.x, e.y - half * 2.05)
	ctx.lineTo(e.x + half * 0.4, e.y - half * 1.98)
	ctx.lineTo(e.x, e.y - half * 1.9)
	ctx.fill()


static func drawCastle(ctx: Ctx, e: Entity, half: float, tc: Dictionary, time: float) -> void:
	var PAL: Dictionary = C.PAL
	# four corner towers, each capped with a team-colour cone
	for pp in [[-1, -1], [1, -1], [-1, 1], [1, 1]]:
		var cx: float = e.x + pp[0] * half * 0.72
		var cy: float = e.y + pp[1] * half * 0.72
		var st = gLook.stone if styled() else PAL.stone
		ctx.fillStyle = grad(ctx, cx - half * 0.32, cy, cx + half * 0.32, cy, Palette.shade(st, 0.18), Palette.shade(st, -0.25))
		ctx.beginPath()
		ctx.arc(cx, cy, half * 0.32, 0, PI * 2)
		ctx.fill()
		softOutline(ctx, 1.2)
		coneRoof(ctx, cx, cy - half * 0.22, half * 0.36, cy - half * 0.78, tc)
	# keep
	stoneBlock(ctx, e.x - half * 0.62, e.y - half * 0.55, half * 1.24, half * 1.1)
	# crenellated top edge
	ctx.fillStyle = PAL.stoneDark
	for i in 5:
		ctx.fillRect(e.x - half * 0.62 + i * half * 0.28, e.y - half * 0.66, half * 0.16, half * 0.16)
	# gate
	ctx.fillStyle = "#241c12"
	ctx.beginPath()
	ctx.arc(e.x, e.y + half * 0.45, half * 0.2, PI, 0)
	ctx.fill()
	ctx.fillRect(e.x - half * 0.2, e.y + half * 0.45, half * 0.4, half * 0.2)
	ctx.strokeStyle = PAL.steelDark
	ctx.lineWidth = 1.5
	for i in range(-1, 2):
		ctx.beginPath()
		ctx.moveTo(e.x + i * half * 0.1, e.y + half * 0.28)
		ctx.lineTo(e.x + i * half * 0.1, e.y + half * 0.65)
		ctx.stroke()
	banner(ctx, e.x, e.y - half * 0.7, tc, time, e.id)


static func drawSiegeWorkshop(ctx: Ctx, e: Entity, half: float) -> void:
	var PAL: Dictionary = C.PAL
	# open timber frame shed
	ctx.fillStyle = PAL.dirt
	ctx.fillRect(e.x - half + 4, e.y - half * 0.2, half * 2 - 8, half * 1.1)
	roofGable(ctx, e.x, e.y - half * 0.55, half * 2.05, half * 0.8, PAL.woodDark)
	ctx.fillStyle = PAL.wood
	for px in [-1, 1]:
		ctx.fillRect(e.x + px * (half - 8) - 3, e.y - half * 0.2, 6, half * 1.1)
	# wheel + beam (catapult parts)
	ctx.strokeStyle = PAL.woodLight
	ctx.lineWidth = 2.5
	ctx.beginPath()
	ctx.arc(e.x - half * 0.3, e.y + half * 0.4, half * 0.22, 0, PI * 2)
	ctx.stroke()
	for i in 4:
		var a := (i * PI) / 2 + 0.4
		ctx.beginPath()
		ctx.moveTo(e.x - half * 0.3, e.y + half * 0.4)
		ctx.lineTo(e.x - half * 0.3 + cos(a) * half * 0.22, e.y + half * 0.4 + sin(a) * half * 0.22)
		ctx.stroke()
	ctx.fillStyle = PAL.woodLight
	ctx.fillRect(e.x + half * 0.05, e.y + half * 0.3, half * 0.7, 5)

# -------------------------------------------------------------------- units --

static func drawUnit(ctx: Ctx, e: Entity, time: float, lod: int = 0) -> void:
	var tc := tcol(e.team)
	useLook(e.team, e.type, e.id, time)
	var def: Dictionary = C.UNITS.get(e.type, {})
	var hero: bool = def.get("hero", false)
	# Level-of-detail blob: a single filled body (+ a flag dot for the champion).
	# Used when zoomed out / in big crowds, where the full art is sub-pixel.
	if lod > 0:
		ctx.fillStyle = Palette.withAlpha("#0a0805", 0.28)
		ctx.beginPath()
		ctx.ellipse(e.x, e.y + e.radius * 0.55, e.radius * 0.9, e.radius * 0.4, 0, 0, PI * 2)
		ctx.fill()
		ctx.fillStyle = tc.main
		ctx.beginPath()
		ctx.ellipse(e.x, e.y, e.radius * 0.92, e.radius * 0.72, 0, 0, PI * 2)
		ctx.fill()
		if hero:
			ctx.fillStyle = "#ffd24a"; ctx.beginPath(); ctx.arc(e.x, e.y - e.radius, 2.2, 0, PI * 2); ctx.fill()
		return
	var moving := sqrt(e.vx * e.vx + e.vy * e.vy) > 4
	var bob := sin(e.animPhase * 9) * 1.4 if moving else sin(e.animPhase * 2.4) * 0.4
	# Attack lunge: strongest right after an attack starts cooling down.
	var atkFrac := e.attackCooldown / e.attackInterval if e.attackInterval > 0 else 0.0
	var lunge := (atkFrac - 0.82) / 0.18 if atkFrac > 0.82 else 0.0

	# Rarity aura under high-tier player units.
	if e.variantRarity >= 2:
		var rr := rarityByIndex(e.variantRarity)
		var pulse := 0.5 + sin(time * 3 + e.id) * 0.18
		ctx.fillStyle = Palette.withAlpha(rr.glow if String(rr.glow).begins_with("#") else "#888888", 0.16 * pulse * e.variantRarity)
		ctx.beginPath()
		ctx.ellipse(e.x, e.y + e.radius * 0.5, e.radius * 1.7, e.radius * 0.8, 0, 0, PI * 2)
		ctx.fill()

	# Status aura: the unit's own active ability, or a rally/slow applied to it.
	# A pulsing coloured ring beneath the feet, colour by status (priority order).
	var auraColor = null
	if e.abilityActive > 0 and C.ABILITIES.has(e.type):
		auraColor = C.ABILITIES[e.type].color
	elif e.rallyTimer > 0:
		auraColor = "#ffcf5a"  # War Cry rally
	elif e.slowTimer > 0:
		auraColor = "#8ad6ff"  # Caltrops slow
	if auraColor != null:
		var pulse := 0.62 + sin(time * 9 + e.id) * 0.22
		ctx.strokeStyle = Palette.withAlpha(auraColor, 0.85 * pulse)
		ctx.lineWidth = 2.2
		ctx.beginPath()
		ctx.ellipse(e.x, e.y + e.radius * 0.5, e.radius * 1.5, e.radius * 0.7, 0, 0, PI * 2)
		ctx.stroke()
		ctx.fillStyle = Palette.withAlpha(auraColor, 0.14 * pulse)
		ctx.fill()

	# Heroes wear a permanent golden aura plus level pips above their head.
	if hero:
		var pulse := 0.6 + sin(time * 3 + e.id) * 0.2
		ctx.fillStyle = Palette.withAlpha("#ffd24a", 0.16 * pulse)
		ctx.beginPath()
		ctx.ellipse(e.x, e.y + e.radius * 0.5, e.radius * 1.9, e.radius * 0.95, 0, 0, PI * 2)
		ctx.fill()
		for i in e.heroLevel:
			ctx.fillStyle = "#ffe07a"
			ctx.beginPath()
			ctx.arc(e.x - (e.heroLevel - 1) * 3 + i * 6, e.y - e.radius * 2.1, 2, 0, PI * 2)
			ctx.fill()

	# Veterancy chevrons above seasoned units (gold, one per rank).
	#
	# Drawn twice: a dark stroke underneath, then the gold on top. A 1.4px gold
	# line on its own vanished against pale ground and any bright particle, which
	# meant a rank that was quietly adding HP and attack was invisible exactly
	# when it mattered — in the middle of a fight.
	if e.veterancy > 0 and not hero:
		var cy := e.y - e.radius * 1.7
		var col := "#ffe07a" if e.veterancy >= 3 else ("#ffd24a" if e.veterancy >= 2 else "#e8c98a")
		ctx.lineCap = "round"
		ctx.lineJoin = "round"
		for sw in [["rgba(12,10,6,0.75)", 3.2], [col, 1.6]]:
			ctx.strokeStyle = sw[0]
			ctx.lineWidth = sw[1]
			for i in e.veterancy:
				var cx := e.x - (e.veterancy - 1) * 3.5 + i * 7
				ctx.beginPath()
				ctx.moveTo(cx - 2.6, cy + 1.6)
				ctx.lineTo(cx, cy - 1.6)
				ctx.lineTo(cx + 2.6, cy + 1.6)
				ctx.stroke()
		ctx.lineCap = "butt"
		ctx.lineJoin = "miter"

	if not (ArtCache.enabled and ArtCache.drawUnitBase(ctx, e, tc)):
		shadow(ctx, e.x + 1, e.y + e.radius * 0.55, e.radius * 0.95, e.radius * 0.42)
		teamRing(ctx, e.x, e.y + e.radius * 0.55, e.radius, tc)

	var ox := e.x + cos(e.facing) * lunge * 4
	var oy := e.y - bob + sin(e.facing) * lunge * 4
	if ArtCache.enabled and ArtCache.drawUnitBody(ctx, e, tc, time, ox, oy, moving, atkFrac, lunge):
		drawUnitOverlays(ctx, e, bob)
		return

	ctx.save()
	ctx.translate(ox, oy)
	drawUnitBody(ctx, e, tc, time, moving, atkFrac, lunge)
	ctx.restore()
	drawUnitOverlays(ctx, e, bob)


## The figure itself, in local space around the unit (the switch inside the TS
## drawUnit, split out so the art cache can render it into an atlas cell).
static func drawUnitBody(ctx: Ctx, e: Entity, tc: Dictionary, time: float, moving: bool, atkFrac: float, lunge: float) -> void:
	var usprite = Sprites.spriteFor(e.type)
	if usprite != null:
		blitSprite(ctx, usprite, e.radius)
		return
	match e.type:
		"villager": drawVillager(ctx, e, tc, moving)
		"militia": drawMilitia(ctx, e, tc, lunge)
		"shieldbearer": drawShieldbearer(ctx, e, tc, lunge)
		"berserker": drawBerserker(ctx, e, tc, lunge, time)
		"longbow": drawLongbow(ctx, e, tc, atkFrac)
		"cataphract": drawCataphract(ctx, e, tc, moving, time, lunge)
		"battlemage": drawBattlemage(ctx, e, tc, atkFrac, time)
		"bombard": drawBombard(ctx, e, tc, atkFrac)
		"twohand": drawTwohand(ctx, e, tc, lunge)
		"spearman": drawSpearman(ctx, e, tc, lunge)
		"pikeman": drawPikeman(ctx, e, tc, lunge)
		"scout": drawScout(ctx, e, tc, moving)
		"archer": drawArcher(ctx, e, tc, atkFrac)
		"skirmisher": drawSkirmisher(ctx, e, tc, atkFrac)
		"crossbow": drawCrossbow(ctx, e, tc, atkFrac)
		"knight": drawKnight(ctx, e, tc, moving, time, lunge)
		"horseman": drawHorseman(ctx, e, tc, moving)
		"raider": drawRaider(ctx, e, tc, moving, lunge)
		"javelin": drawJavelin(ctx, e, tc, atkFrac)
		"handcannon": drawHandcannon(ctx, e, tc, atkFrac)
		"catapult": drawCatapult(ctx, e, tc, atkFrac)
		"trebuchet": drawTrebuchet(ctx, e, tc, atkFrac)
		"ram": drawRam(ctx, e, tc, atkFrac)
		"trade_cart": drawTradeCart(ctx, e, tc)
		"hero": drawHero(ctx, e, tc, lunge, time)
		"king": drawKing(ctx, e, tc, lunge, time)
		"monk": drawMonk(ctx, e, tc, time)
		"sworn_blade": drawSwornBlade(ctx, e, tc, lunge)
		"lancer": drawLancer(ctx, e, tc, moving, lunge)
		"ranger": drawRanger(ctx, e, tc, atkFrac)
		"halberdier": drawHalberdier(ctx, e, tc, lunge)
		"great_bombard": drawGreatBombard(ctx, e, atkFrac)
		"royal_guard": drawRoyalGuard(ctx, e, tc, lunge, time)
		"yeoman": drawLongbow(ctx, e, tc, atkFrac)
		"legionary": drawLegionary(ctx, e, tc, lunge)
		"samurai": drawSamurai(ctx, e, tc, lunge)
		"horse_archer": drawHorseArcher(ctx, e, tc, moving, atkFrac)
		"pulse_trooper": drawPulseTrooper(ctx, e, tc, atkFrac, time)
		"skimmer": drawSkimmer(ctx, e, tc, moving, lunge, time)
		_:
			ctx.fillStyle = tc.main
			ctx.beginPath()
			ctx.arc(0, 0, e.radius, 0, PI * 2)
			ctx.fill()


# Rarity trim ring + hit flash, drawn in world space after the body (procedural
# or baked sprite). Shared so the sprite fast-path keeps these cues.
static func drawUnitOverlays(ctx: Ctx, e: Entity, bob: float) -> void:
	if e.variantRarity >= 1:
		var r := rarityByIndex(e.variantRarity)
		ctx.strokeStyle = Palette.withAlpha(r.color, 0.85)
		ctx.lineWidth = 2.0 if e.variantRarity >= 4 else 1.2
		ctx.beginPath()
		ctx.arc(e.x, e.y - bob, e.radius + 2.5, 0, PI * 2)
		ctx.stroke()

	if e.hitFlash > 0:
		ctx.fillStyle = Palette.withAlpha("#ffffff", e.hitFlash * 0.5)
		ctx.beginPath()
		ctx.arc(e.x, e.y - bob, e.radius + 1, 0, PI * 2)
		ctx.fill()


# ============================================================================
# Articulated figure rig. Units are drawn as little upright "toy soldiers" with
# shaded, layered parts and a parametric animation: a continuous walk cycle
# (striding legs + arm/torso bob) and an attack that sweeps the weapon through
# an arc. Everything is oriented at runtime by `facing`, so a single rig covers
# all 360° — no baked sprite angles. Drawing order per unit: legs → off-hand →
# torso → head → weapon, so the figure layers correctly.
# ============================================================================

static func grad(ctx: Ctx, x0: float, y0: float, x1: float, y1: float, c0, c1) -> Ctx.CanvasGradient:
	var g := ctx.createLinearGradient(x0, y0, x1, y1)
	g.addColorStop(0, c0)
	g.addColorStop(1, c1)
	return g


## Stroke the path just filled with a soft dark outline for a crisp read.
static func softOutline(ctx: Ctx, w: float = 1.6) -> void:
	ctx.strokeStyle = "rgba(20,16,10,0.42)"
	ctx.lineWidth = w
	ctx.stroke()


## A shaded round-capped limb between two points.
static func capsule(ctx: Ctx, x0: float, y0: float, x1: float, y1: float, w: float, c0, c1) -> void:
	ctx.strokeStyle = grad(ctx, x0, y0, x1, y1, c0, c1)
	ctx.lineCap = "round"
	ctx.lineWidth = w
	ctx.beginPath()
	ctx.moveTo(x0, y0)
	ctx.lineTo(x1, y1)
	ctx.stroke()


## Walk phase: legs/arms swing strongly when moving, sway gently when idle.
static func gait(e: Entity) -> float:
	var moving := sqrt(e.vx * e.vx + e.vy * e.vy) > 4
	return sin(e.animPhase * 9) if moving else sin(e.animPhase * 2.2) * 0.2


## Two striding legs + boots, drawn behind the torso.
static func drawLegs(ctx: Ctx, r: float, step: float, col) -> void:
	for side in [1, -1]:
		var sw: float = step * side
		var hipX: float = side * r * 0.24
		var footX: float = hipX + sw * r * 0.4
		var lift := maxf(0, sw) * r * 0.16
		capsule(ctx, hipX, r * 0.32, footX, r * 0.8 - lift, r * 0.24, Palette.shade(col, 0.05), Palette.shade(col, -0.3))
		ctx.fillStyle = Palette.shade(C.PAL.leather, -0.1)
		ctx.beginPath()
		ctx.ellipse(footX + r * 0.05, r * 0.8 - lift, r * 0.16, r * 0.09, 0, 0, PI * 2)
		ctx.fill()


## Striding legs + a layered, shaded torso (surcoat + belt + rim light).
## opts: {pauldrons, chestPlate, sash, legCol}
static func body(ctx: Ctx, e: Entity, cloth, clothDark, opts: Dictionary = {}) -> void:
	var r := e.radius
	var def: Dictionary = C.UNITS.get(gType, {})
	gFigure = {
		"heavy": bool(opts.get("chestPlate", false) or opts.get("pauldrons", false)),
		"soldier": not def.get("canGather", false) and not OWN_HEADGEAR.has(gType),
		"step": gait(e), "seed": e.id,
	}
	# Every other faction draws the whole figure its own way.
	if styled() and not OWN_HEADGEAR.has(gType) and FactionFigures.factionBody(ctx, gLook, r, cloth, clothDark, gFigure, gTC):
		return
	drawLegs(ctx, r, gait(e), opts.get("legCol", clothDark))
	# surcoat
	ctx.fillStyle = grad(ctx, 0, -r * 0.55, 0, r * 0.45, Palette.shade(cloth, 0.16), clothDark)
	ctx.beginPath()
	ctx.moveTo(-r * 0.5, r * 0.42)
	ctx.quadraticCurveTo(-r * 0.6, -r * 0.5, 0, -r * 0.58)
	ctx.quadraticCurveTo(r * 0.6, -r * 0.5, r * 0.5, r * 0.42)
	ctx.quadraticCurveTo(0, r * 0.6, -r * 0.5, r * 0.42)
	ctx.closePath()
	ctx.fill()
	softOutline(ctx, 1.8)
	var plate = gLook.armour if styled() else C.PAL.steel
	var plateDark = Palette.shade(gLook.armour, -0.3) if styled() else C.PAL.steelDark
	if opts.get("chestPlate", false):
		ctx.fillStyle = grad(ctx, 0, -r * 0.4, 0, r * 0.3, plate, plateDark)
		ctx.beginPath()
		ctx.arc(0, -r * 0.02, r * 0.42, 0, PI * 2)
		ctx.fill()
		softOutline(ctx, 1.2)
	if opts.get("sash") != null:
		ctx.strokeStyle = opts.sash
		ctx.lineWidth = 2.6
		ctx.beginPath()
		ctx.moveTo(-r * 0.45, -r * 0.35)
		ctx.lineTo(r * 0.42, r * 0.46)
		ctx.stroke()
	# belt
	ctx.fillStyle = Palette.shade(C.PAL.leather, -0.05)
	ctx.fillRect(-r * 0.46, r * 0.24, r * 0.92, r * 0.11)
	# top-left rim light
	ctx.strokeStyle = "rgba(255,255,255,0.32)"
	ctx.lineWidth = 1.5
	ctx.beginPath()
	ctx.moveTo(-r * 0.48, r * 0.18)
	ctx.quadraticCurveTo(-r * 0.56, -r * 0.44, -r * 0.04, -r * 0.55)
	ctx.stroke()
	# steel pauldrons
	if opts.get("pauldrons", false):
		for sx in [-1, 1]:
			ctx.fillStyle = grad(ctx, sx * r * 0.5, -r * 0.5, sx * r * 0.5, -r * 0.2, plate, plateDark)
			ctx.beginPath()
			ctx.ellipse(sx * r * 0.46, -r * 0.32, r * 0.2, r * 0.15, sx * 0.4, 0, PI * 2)
			ctx.fill()
			softOutline(ctx, 1.2)


## Skin head + a shaded helmet variant, optional plume.
## opts: {helm ("open" | "full" | "cap" | "hood" | "bare"), tone, plume, hair}
static func head(ctx: Ctx, r: float, opts: Dictionary = {}) -> void:
	var PAL: Dictionary = C.PAL
	var hy := -r * 0.78
	var helm: String = opts.get("helm", "open")
	var tone = opts.get("tone", PAL.steel)
	ctx.fillStyle = PAL.skin
	ctx.beginPath()
	ctx.arc(0, hy, r * 0.3, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1.1)
	if opts.get("hair") != null and helm == "bare":
		ctx.fillStyle = opts.hair
		ctx.beginPath()
		ctx.arc(0, hy - r * 0.04, r * 0.3, PI * 1.02, PI * 1.98)
		ctx.fill()
	if styled() and not OWN_HEADGEAR.has(gType):
		FactionFigures.factionFace(ctx, gLook, 0, hy, r * 0.3, gFigure)
		# Heavy troops of the Shogunate and Khanate wear the war helm, their
		# lighter troops the hat — whatever the unit's own art asked for.
		var kind := "full" if gFigure.soldier and gFigure.heavy and helm != "bare" else helm
		FactionArt.factionHelm(ctx, gLook, 0, hy, r * 0.3, kind, gTC)
		return
	if helm == "cap" or helm == "open" or helm == "full":
		ctx.fillStyle = grad(ctx, 0, hy - r * 0.3, 0, hy + r * 0.1, Palette.shade(tone, 0.16), Palette.shade(tone, -0.16))
		ctx.beginPath()
		ctx.arc(0, hy - r * 0.04, r * 0.31, PI * 0.95, PI * 2.05)
		ctx.fill()
		softOutline(ctx, 1)
		if helm == "full":
			ctx.fillStyle = Palette.shade(tone, -0.05)
			ctx.beginPath()
			ctx.arc(0, hy, r * 0.31, PI * 1.04, PI * 1.96)
			ctx.fill()
			ctx.strokeStyle = PAL.steelDark
			ctx.lineWidth = 1.5
			ctx.beginPath()
			ctx.moveTo(0, hy - r * 0.1)
			ctx.lineTo(0, hy + r * 0.16)
			ctx.stroke()
		elif helm == "open":
			ctx.strokeStyle = PAL.steelDark  # nasal bar
			ctx.lineWidth = 1.3
			ctx.beginPath()
			ctx.moveTo(0, hy - r * 0.06)
			ctx.lineTo(0, hy + r * 0.12)
			ctx.stroke()
	elif helm == "hood":
		ctx.fillStyle = grad(ctx, 0, hy - r * 0.3, 0, hy + r * 0.2, Palette.shade(tone, 0.1), Palette.shade(tone, -0.2))
		ctx.beginPath()
		ctx.arc(0, hy - r * 0.02, r * 0.36, PI * 0.78, PI * 2.22)
		ctx.fill()
		softOutline(ctx, 1)
	if opts.get("plume") != null:
		ctx.fillStyle = opts.plume
		ctx.beginPath()
		ctx.moveTo(-r * 0.02, hy - r * 0.32)
		ctx.quadraticCurveTo(r * 0.2, hy - r * 0.72, r * 0.05, hy - r * 0.34)
		ctx.quadraticCurveTo(-r * 0.04, hy - r * 0.4, -r * 0.02, hy - r * 0.32)
		ctx.fill()


static func weaponAngleParts(facing: float) -> Vector2:
	return Vector2(cos(facing), sin(facing))


## A melee weapon swing factor (1 = mid-strike, eases to 0 during recovery),
## derived from the attack cooldown which is full right after a blow lands.
static func strikeT(e: Entity) -> float:
	var f := e.attackCooldown / e.attackInterval if e.attackInterval > 0 else 0.0
	return (f - 0.55) / 0.45 if f > 0.55 else 0.0


## Facing rotated through a swing arc as a strike plays out — gives weapons a
## real sweep instead of a static poke, while staying oriented to `facing`.
## Returns (dx, dy, s).
static func swungDir(e: Entity, arc: float) -> Vector3:
	var s := strikeT(e)
	var ang := e.facing + (s - 0.5) * arc
	return Vector3(cos(ang), sin(ang), s)


## Draw a shaded blade from the body out along (dx,dy).
static func blade(ctx: Ctx, r: float, dx: float, dy: float, len: float, hiltCol, len0: float = 0.3) -> void:
	if styled() and FactionFigures.factionBlade(ctx, gLook, r, dx, dy, len, len0):
		return
	var x0 := dx * r * len0
	var y0 := dy * r * len0
	# crossguard
	ctx.strokeStyle = hiltCol
	ctx.lineWidth = r * 0.1
	ctx.beginPath()
	ctx.moveTo(x0 - dy * r * 0.22, y0 + dx * r * 0.22)
	ctx.lineTo(x0 + dy * r * 0.22, y0 - dx * r * 0.22)
	ctx.stroke()
	# blade with a bright edge glint
	ctx.strokeStyle = grad(ctx, x0, y0, dx * r * len, dy * r * len, "#f2f4f8", C.PAL.steelDark)
	ctx.lineWidth = r * 0.13
	ctx.lineCap = "round"
	ctx.beginPath()
	ctx.moveTo(x0, y0)
	ctx.lineTo(dx * r * len, dy * r * len)
	ctx.stroke()
	ctx.strokeStyle = "rgba(255,255,255,0.85)"
	ctx.lineWidth = 1.3
	ctx.beginPath()
	ctx.moveTo(x0 + dx * r * 0.2, y0 + dy * r * 0.2)
	ctx.lineTo(dx * r * len, dy * r * len)
	ctx.stroke()


## A round shield on the off-hand (opposite the weapon), facing-aware.
static func roundShield(ctx: Ctx, r: float, fx: float, fy: float, tc: Dictionary) -> void:
	var sx := -fy * r * 0.7
	var sy := fx * r * 0.7
	if styled():
		FactionArt.factionShield(ctx, gLook, sx, sy, r, tc)
		return
	ctx.fillStyle = grad(ctx, sx, sy - r * 0.4, sx, sy + r * 0.4, C.PAL.steel, C.PAL.steelDark)
	ctx.beginPath()
	ctx.ellipse(sx, sy, r * 0.38, r * 0.46, 0, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1.4)
	ctx.fillStyle = tc.main
	ctx.beginPath()
	ctx.arc(sx, sy, r * 0.15, 0, PI * 2)
	ctx.fill()
	ctx.strokeStyle = "rgba(255,255,255,0.45)"
	ctx.lineWidth = 1.2
	ctx.beginPath()
	ctx.arc(sx, sy, r * 0.27, 0, PI * 2)
	ctx.stroke()


static func drawVillager(ctx: Ctx, e: Entity, tc: Dictionary, moving: bool) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, PAL.leather, Palette.shade(PAL.leather, -0.25), {"sash": tc.main})
	head(ctx, r, {"helm": "bare", "hair": "#5a4326"})
	# tool: hatchet, worked up and down as it gathers (or shouldered when idle)
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	var chop := strikeT(e)
	var len := 1.0 + chop * 0.4
	ctx.strokeStyle = grad(ctx, fx * r * 0.4, fy * r * 0.4, fx * r * len, fy * r * len, PAL.woodLight, PAL.woodDark)
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.12
	ctx.beginPath()
	ctx.moveTo(fx * r * 0.4, fy * r * 0.4)
	ctx.lineTo(fx * r * len, fy * r * len)
	ctx.stroke()
	ctx.fillStyle = PAL.steel
	ctx.beginPath()
	ctx.moveTo(fx * r * len, fy * r * len)
	ctx.lineTo(fx * r * len - fy * r * 0.22, fy * r * len + fx * r * 0.22)
	ctx.lineTo(fx * r * (len + 0.18), fy * r * (len + 0.18))
	ctx.closePath()
	ctx.fill()
	# carry bundle
	if e.carry > 2 and e.carryKind != null and e.carryKind != "":
		var col = PAL.trunk if e.carryKind == "wood" else (PAL.goldVein if e.carryKind == "gold" else PAL.berry)
		ctx.fillStyle = col
		ctx.beginPath()
		ctx.arc(-fx * r * 0.8, -fy * r * 0.8 - r * 0.4, r * 0.4, 0, PI * 2)
		ctx.fill()
		softOutline(ctx, 1.2)


static func drawMilitia(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float) -> void:
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	roundShield(ctx, r, f.x, f.y, tc)  # off-hand (behind torso)
	body(ctx, e, tc.main, tc.dark, {"pauldrons": true})
	head(ctx, r, {"helm": "open", "tone": C.PAL.steel})
	# one-handed sword, sweeping through its strike
	var d := swungDir(e, 1.0)
	blade(ctx, r, d.x, d.y, 1.5 + lunge * 0.4, Palette.shade(tc.dark, -0.1))


static func drawSpearman(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, tc.main, tc.dark)
	head(ctx, r, {"helm": "cap", "tone": PAL.leather})
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	# spear thrusts forward on the strike rather than swinging
	var reach := 2.2 + lunge * 0.8 + strikeT(e) * 0.5
	ctx.strokeStyle = grad(ctx, -fx * r * 0.8, -fy * r * 0.8, fx * r * reach, fy * r * reach, PAL.woodLight, PAL.woodDark)
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.1
	ctx.beginPath()
	ctx.moveTo(-fx * r * 0.8, -fy * r * 0.8)
	ctx.lineTo(fx * r * reach, fy * r * reach)
	ctx.stroke()
	ctx.fillStyle = PAL.steel
	var tipX := fx * r * reach
	var tipY := fy * r * reach
	if styled() and FactionFigures.factionSpearHead(ctx, gLook, tipX, tipY, fx, fy, r):
		return
	ctx.beginPath()
	ctx.moveTo(tipX + fx * 4, tipY + fy * 4)
	ctx.lineTo(tipX - fy * 2.4, tipY + fx * 2.4)
	ctx.lineTo(tipX + fy * 2.4, tipY - fx * 2.4)
	ctx.closePath()
	ctx.fill()
	softOutline(ctx, 1)


## A wall of a man: full helm, heavy pauldrons and a tower shield up front.
static func drawShieldbearer(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, tc.main, tc.dark, {"pauldrons": true, "chestPlate": true})
	head(ctx, r, {"helm": "full", "tone": PAL.steel})
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	# Short stabbing sword, kept close.
	ctx.strokeStyle = PAL.steel
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.16
	ctx.beginPath()
	ctx.moveTo(-fy * r * 0.5, fx * r * 0.5)
	ctx.lineTo(-fy * r * 0.5 + fx * r * (1.1 + lunge * 0.5), fx * r * 0.5 + fy * r * (1.1 + lunge * 0.5))
	ctx.stroke()
	# The shield itself — big, slightly forward, team-coloured with a boss.
	var sx := fx * r * 0.85 + fy * r * 0.45
	var sy := fy * r * 0.85 - fx * r * 0.45
	ctx.fillStyle = grad(ctx, sx, sy - r * 0.7, sx, sy + r * 0.7, Palette.shade(tc.light, 0.1), tc.dark)
	ctx.beginPath()
	ctx.ellipse(sx, sy, r * 0.5, r * 0.82, e.facing, 0, PI * 2)
	ctx.fill()
	ctx.strokeStyle = PAL.steelDark
	ctx.lineWidth = r * 0.1
	ctx.stroke()
	ctx.fillStyle = PAL.steel
	ctx.beginPath()
	ctx.arc(sx, sy, r * 0.17, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1)


## Bare-headed, wild-haired, an axe in each hand. No armour anywhere.
static func drawBerserker(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float, time: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, tc.main, tc.dark, {"sash": tc.light})
	head(ctx, r, {"helm": "bare", "hair": "#b8763a"})
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	var swing := lunge * 0.9 + sin(time * 3 + e.id) * 0.06
	for side in [-1, 1]:
		var hx: float = -fy * r * 0.6 * side
		var hy: float = fx * r * 0.6 * side
		var tx: float = hx + fx * r * (1.25 + swing)
		var ty: float = hy + fy * r * (1.25 + swing)
		ctx.strokeStyle = PAL.woodDark
		ctx.lineCap = "round"
		ctx.lineWidth = r * 0.11
		ctx.beginPath()
		ctx.moveTo(hx, hy)
		ctx.lineTo(tx, ty)
		ctx.stroke()
		# Axe head, angled off the haft.
		ctx.fillStyle = grad(ctx, tx, ty - r * 0.3, tx, ty + r * 0.3, PAL.steel, PAL.steelDark)
		ctx.beginPath()
		ctx.moveTo(tx, ty)
		ctx.lineTo(tx + fx * r * 0.34 - fy * r * 0.36 * side, ty + fy * r * 0.34 + fx * r * 0.36 * side)
		ctx.lineTo(tx - fx * r * 0.12 - fy * r * 0.42 * side, ty - fy * r * 0.12 + fx * r * 0.42 * side)
		ctx.closePath()
		ctx.fill()
	softOutline(ctx, 1)


## A very tall bow, drawn past the ear — the silhouette is the whole point.
static func drawLongbow(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, tc.main, tc.dark, {"sash": tc.light})
	head(ctx, r, {"helm": "cap", "tone": "#4a5c3a"})
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	var draw := maxf(0, 1 - atkFrac)  # full draw just before loosing
	if styled() and FactionFigures.factionBow(ctx, gLook, r, fx, fy, e.facing, draw, true):
		return
	var bx := fx * r * 0.55 - fy * r * 0.2
	var by := fy * r * 0.55 + fx * r * 0.2
	ctx.strokeStyle = PAL.woodLight
	ctx.lineWidth = r * 0.11
	ctx.lineCap = "round"
	ctx.beginPath()
	ctx.ellipse(bx, by, r * 0.34, r * 1.5, e.facing, PI * 0.42, PI * 1.58)
	ctx.stroke()
	# String, pulled back as the shot builds.
	ctx.strokeStyle = "rgba(240,236,220,0.85)"
	ctx.lineWidth = maxf(0.6, r * 0.045)
	var nockX := bx - fx * r * 0.75 * draw
	var nockY := by - fy * r * 0.75 * draw
	ctx.beginPath()
	ctx.moveTo(bx - fy * r * 1.42, by + fx * r * 1.42)
	ctx.lineTo(nockX, nockY)
	ctx.lineTo(bx + fy * r * 1.42, by - fx * r * 1.42)
	ctx.stroke()
	if draw > 0.25:  # the arrow on the string
		ctx.strokeStyle = PAL.woodDark
		ctx.lineWidth = r * 0.07
		ctx.beginPath()
		ctx.moveTo(nockX, nockY)
		ctx.lineTo(nockX + fx * r * 1.5, nockY + fy * r * 1.5)
		ctx.stroke()
	softOutline(ctx, 1)


## Knight silhouette, but the horse is armoured too — heavier and squarer.
static func drawCataphract(ctx: Ctx, e: Entity, tc: Dictionary, moving: bool, time: float, lunge: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	var gallop := sin(e.animPhase * 10) * 1.1 if moving else 0.0
	# Barded horse: a blockier body than the Knight's, in steel over team colour.
	ctx.fillStyle = grad(ctx, 0, r * 0.1, 0, r * 0.95, PAL.steel, PAL.steelDark)
	ctx.beginPath()
	ctx.ellipse(0, r * 0.5 + gallop * 0.1, r * 1.05, r * 0.62, 0, 0, PI * 2)
	ctx.fill()
	ctx.fillStyle = Palette.shade(tc.dark, -0.05)  # caparison skirt
	ctx.beginPath()
	ctx.ellipse(0, r * 0.78, r * 0.98, r * 0.34, 0, 0, PI * 2)
	ctx.fill()
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	# Armoured head of the horse, thrust forward.
	ctx.fillStyle = PAL.steel
	ctx.beginPath()
	ctx.ellipse(fx * r * 0.95, fy * r * 0.95 + r * 0.3, r * 0.34, r * 0.26, e.facing, 0, PI * 2)
	ctx.fill()
	# Rider.
	body(ctx, e, tc.main, tc.dark, {"pauldrons": true, "chestPlate": true})
	head(ctx, r, {"helm": "full", "tone": PAL.steel, "plume": tc.light})
	# Couched lance.
	var reach := 2.4 + lunge * 0.7
	ctx.strokeStyle = grad(ctx, 0, 0, fx * r * reach, fy * r * reach, PAL.woodLight, PAL.woodDark)
	ctx.lineWidth = r * 0.12
	ctx.lineCap = "round"
	ctx.beginPath()
	ctx.moveTo(-fx * r * 0.7, -fy * r * 0.7)
	ctx.lineTo(fx * r * reach, fy * r * reach)
	ctx.stroke()
	ctx.fillStyle = PAL.steel
	ctx.beginPath()
	ctx.moveTo(fx * r * (reach + 0.3), fy * r * (reach + 0.3))
	ctx.lineTo(fx * r * reach - fy * r * 0.22, fy * r * reach + fx * r * 0.22)
	ctx.lineTo(fx * r * reach + fy * r * 0.22, fy * r * reach - fx * r * 0.22)
	ctx.closePath()
	ctx.fill()
	softOutline(ctx, 1)


## Robed, hooded, with a staff whose head kindles as the cast builds.
static func drawBattlemage(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float, time: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, Palette.shade(tc.dark, -0.12), Palette.shade(tc.dark, -0.24), {"legCol": Palette.shade(tc.dark, -0.3)})
	head(ctx, r, {"helm": "hood", "tone": Palette.shade(tc.dark, -0.18)})
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	var charge := maxf(0, 1 - atkFrac)  # brightest just before the cast
	# Staff.
	var topX := -fy * r * 0.62 + fx * r * 0.2
	var topY := fx * r * 0.62 + fy * r * 0.2
	ctx.strokeStyle = grad(ctx, topX, topY - r * 1.4, topX, topY + r * 0.6, PAL.woodLight, PAL.woodDark)
	ctx.lineWidth = r * 0.1
	ctx.lineCap = "round"
	ctx.beginPath()
	ctx.moveTo(topX, topY + r * 0.6)
	ctx.lineTo(topX, topY - r * 1.25)
	ctx.stroke()
	# The ember at its head, breathing and flaring on the cast.
	var glow := 0.35 + charge * 0.65 + sin(time * 4 + e.id) * 0.08
	var gy := topY - r * 1.35
	ctx.save()
	ctx.shadowColor = "#ff9a3a"
	ctx.shadowBlur = r * (1.4 + charge * 2.6)
	ctx.fillStyle = grad(ctx, topX, gy - r * 0.3, topX, gy + r * 0.3, "#ffe7a8", "#e8631a")
	ctx.beginPath()
	ctx.arc(topX, gy, r * (0.2 + glow * 0.18), 0, PI * 2)
	ctx.fill()
	ctx.restore()
	softOutline(ctx, 1)


## A short fat barrel on a two-wheeled carriage, muzzle flaring as it fires.
static func drawBombard(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	var recoil := (atkFrac - 0.85) / 0.15 if atkFrac > 0.85 else 0.0
	# Carriage bed + wheels.
	ctx.fillStyle = grad(ctx, 0, -r * 0.3, 0, r * 0.5, PAL.woodLight, PAL.woodDark)
	ctx.beginPath()
	ctx.ellipse(0, r * 0.28, r * 0.9, r * 0.42, 0, 0, PI * 2)
	ctx.fill()
	ctx.fillStyle = PAL.woodDark
	for side in [-1, 1]:
		ctx.beginPath()
		ctx.ellipse(-fy * r * 0.62 * side, fx * r * 0.62 * side + r * 0.34, r * 0.34, r * 0.34, 0, 0, PI * 2)
		ctx.fill()
	# The barrel, kicked back on the shot.
	var bx := -fx * r * 0.5 * recoil
	var by := -fy * r * 0.5 * recoil - r * 0.15
	ctx.fillStyle = grad(ctx, bx, by - r * 0.35, bx, by + r * 0.35, "#5a5a62", "#2c2c33")
	ctx.beginPath()
	ctx.ellipse(bx + fx * r * 0.42, by + fy * r * 0.42, r * 0.86, r * 0.36, e.facing, 0, PI * 2)
	ctx.fill()
	ctx.fillStyle = "#6e6e78"  # reinforcing band
	ctx.beginPath()
	ctx.ellipse(bx + fx * r * 0.1, by + fy * r * 0.1, r * 0.28, r * 0.4, e.facing, 0, PI * 2)
	ctx.fill()
	if recoil > 0.1:  # muzzle flash
		ctx.save()
		ctx.shadowColor = "#ffb03a"
		ctx.shadowBlur = r * 3
		ctx.fillStyle = "#ffd88a"
		ctx.beginPath()
		ctx.arc(bx + fx * r * 1.45, by + fy * r * 1.45, r * 0.42 * recoil, 0, PI * 2)
		ctx.fill()
		ctx.restore()
	softOutline(ctx, 1)


static func drawArcher(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, tc.main, tc.dark, {"sash": tc.light})
	head(ctx, r, {"helm": "hood", "tone": Palette.shade(tc.dark, -0.05)})
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	# bow: arc perpendicular to facing; draws back as attack readies
	var draw := (atkFrac - 0.7) / 0.3 if atkFrac > 0.7 else 0.0
	if styled() and FactionFigures.factionBow(ctx, gLook, r, fx, fy, e.facing, draw, false):
		return
	ctx.strokeStyle = PAL.woodDark
	ctx.lineWidth = 1.8
	ctx.beginPath()
	ctx.arc(fx * r * 0.7, fy * r * 0.7, r * 0.85, e.facing - 1.25, e.facing + 1.25)
	ctx.stroke()
	# string
	ctx.strokeStyle = "#ddd6c2"
	ctx.lineWidth = 0.8
	var ax := fx * r * 0.7 + cos(e.facing - 1.25) * r * 0.85
	var ay := fy * r * 0.7 + sin(e.facing - 1.25) * r * 0.85
	var bx := fx * r * 0.7 + cos(e.facing + 1.25) * r * 0.85
	var by := fy * r * 0.7 + sin(e.facing + 1.25) * r * 0.85
	var pullX := fx * r * (0.7 - draw * 0.8)
	var pullY := fy * r * (0.7 - draw * 0.8)
	ctx.beginPath()
	ctx.moveTo(ax, ay)
	ctx.lineTo(pullX, pullY)
	ctx.lineTo(bx, by)
	ctx.stroke()
	# quiver
	ctx.fillStyle = PAL.leather
	ctx.fillRect(-fy * r * 0.7 - 2, fx * r * 0.7 - 2, 4, 6)


static func drawSkirmisher(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, PAL.leather, Palette.shade(PAL.leather, -0.25), {"sash": tc.main})
	head(ctx, r, {"helm": "bare", "hair": "#3a2c1c"})
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	# javelin raised overhead when about to throw
	var raise := (atkFrac - 0.7) / 0.3 if atkFrac > 0.7 else 0.0
	ctx.strokeStyle = PAL.woodLight
	ctx.lineWidth = 1.6
	ctx.beginPath()
	ctx.moveTo(-fx * r * (0.6 + raise * 0.5), -fy * r * (0.6 + raise * 0.5) - raise * 3)
	ctx.lineTo(fx * r * 1.7, fy * r * 1.7 - raise * 3)
	ctx.stroke()


## A shaded horse oriented along facing, with galloping legs, mane and tail.
## opts: {coat, scaleX, scaleY, gallopHz, caparison, capAlpha}
static func horse(ctx: Ctx, e: Entity, moving: bool, opts: Dictionary) -> void:
	var r := e.radius
	var sx: float = opts.get("scaleX", 1.2)
	var sy: float = opts.get("scaleY", 0.56)
	var coat = opts.coat
	if styled():
		var h := FactionFigures.factionHorse(gLook, coat, sx)
		coat = h.coat
		sx = h.sx
	var gallop := sin(e.animPhase * float(opts.get("gallopHz", 11))) * 1.3 if moving else 0.0
	ctx.save()
	ctx.rotate(e.facing)
	# The Ascendancy ride sleds, not horses.
	if styled() and FactionFigures.factionMount(ctx, gLook, r, sx, gTime + e.id, gTC, moving):
		ctx.restore()
		return
	# legs (behind the body)
	ctx.strokeStyle = Palette.shade(coat, -0.32)
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.13
	for lx in [-r * sx * 0.55, r * sx * 0.5]:
		for d in [1, -1]:
			ctx.beginPath()
			ctx.moveTo(lx, r * 0.28)
			ctx.lineTo(lx + gallop * 1.6 * d, r * 0.84)
			ctx.stroke()
	# tail
	ctx.strokeStyle = Palette.shade(coat, -0.36)
	ctx.lineWidth = r * 0.12
	ctx.beginPath()
	ctx.moveTo(-r * sx * 0.92, -r * 0.05)
	ctx.quadraticCurveTo(-r * sx * 1.2, r * 0.2 - gallop, -r * sx * 1.05, r * 0.5)
	ctx.stroke()
	# barrel
	ctx.fillStyle = grad(ctx, 0, -r * sy, 0, r * sy, Palette.shade(coat, 0.14), Palette.shade(coat, -0.18))
	ctx.beginPath()
	ctx.ellipse(0, 0, r * sx, r * sy, 0, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1.4)
	# neck + head
	ctx.fillStyle = Palette.shade(coat, 0.04)
	ctx.beginPath()
	ctx.ellipse(r * sx * 0.92, -r * 0.12, r * 0.38, r * 0.22, -0.4, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1.1)
	# mane
	ctx.strokeStyle = Palette.shade(coat, -0.4)
	ctx.lineWidth = r * 0.1
	ctx.beginPath()
	ctx.moveTo(r * sx * 0.5, -r * 0.3)
	ctx.lineTo(r * sx * 0.95, -r * 0.22)
	ctx.stroke()
	# caparison — or the faction's own horse furniture
	if styled():
		FactionFigures.factionTack(ctx, gLook, r, sx, sy, gTC)
	elif opts.get("caparison") != null:
		ctx.fillStyle = Palette.withAlpha(opts.caparison, opts.get("capAlpha", 0.85))
		ctx.beginPath()
		ctx.ellipse(-r * 0.12, 0, r * sx * 0.62, r * sy * 0.95, 0, 0, PI * 2)
		ctx.fill()
	ctx.restore()


## A mounted rider's torso + head, sitting just back of the saddle.
## opts: {coat, coatDark, helm, tone, plume}
static func rider(ctx: Ctx, r: float, opts: Dictionary) -> void:
	var PAL: Dictionary = C.PAL
	if styled():
		FactionFigures.factionRider(ctx, gLook, r, gTC, gSeed, true)
	ctx.fillStyle = grad(ctx, 0, -r * 0.72, 0, -r * 0.1, Palette.shade(opts.coat, 0.15), opts.coatDark)
	ctx.beginPath()
	ctx.ellipse(-r * 0.08, -r * 0.4, r * 0.34, r * 0.44, 0, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1.3)
	ctx.fillStyle = PAL.skin
	ctx.beginPath()
	ctx.arc(-r * 0.08, -r * 0.68, r * 0.22, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1)
	var helm = opts.get("helm")
	if styled():
		FactionFigures.factionRider(ctx, gLook, r, gTC, gSeed, false)
		# Re-draw the face over the torso detail, then dress it.
		ctx.fillStyle = PAL.skin
		ctx.beginPath(); ctx.arc(-r * 0.08, -r * 0.68, r * 0.22, 0, PI * 2); ctx.fill()
		FactionFigures.factionFace(ctx, gLook, -r * 0.08, -r * 0.68, r * 0.22, {"heavy": true, "soldier": true, "step": 0.0, "seed": gSeed})
		FactionArt.factionHelm(ctx, gLook, -r * 0.08, -r * 0.68, r * 0.22, "bare" if helm == "bare" else "full", gTC)
		return
	if helm != null and helm != "bare":
		var tone = opts.get("tone", PAL.steel)
		ctx.fillStyle = grad(ctx, 0, -r * 0.92, 0, -r * 0.5, Palette.shade(tone, 0.15), Palette.shade(tone, -0.15))
		ctx.beginPath()
		ctx.arc(-r * 0.08, -r * 0.7, r * 0.22, PI * 0.95, PI * 2.05)
		ctx.fill()
		softOutline(ctx, 1)
	if opts.get("plume") != null:
		ctx.fillStyle = opts.plume
		ctx.beginPath()
		ctx.moveTo(-r * 0.08, -r * 0.92)
		ctx.quadraticCurveTo(r * 0.08, -r * 1.24, -r * 0.14, -r * 0.9)
		ctx.fill()


static func drawKnight(ctx: Ctx, e: Entity, tc: Dictionary, moving: bool, time: float, lunge: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	horse(ctx, e, moving, {"coat": "#5e4632", "scaleX": 1.25, "scaleY": 0.62, "caparison": tc.main, "capAlpha": 0.88})
	rider(ctx, r, {"coat": tc.main, "coatDark": tc.dark, "helm": "full", "tone": PAL.steel, "plume": tc.light})
	# couched lance from the shoulder, dipping forward on the charge
	var sx := -r * 0.08 + fx * r * 0.2
	var sy := -r * 0.4 + fy * r * 0.2
	var reach := 2.1 + lunge
	ctx.strokeStyle = grad(ctx, sx, sy, fx * r * reach, fy * r * reach, PAL.woodLight, PAL.woodDark)
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.12
	ctx.beginPath()
	ctx.moveTo(sx - fx * r * 0.5, sy - fy * r * 0.5)
	ctx.lineTo(fx * r * reach, fy * r * reach)
	ctx.stroke()
	ctx.fillStyle = PAL.steel  # lance head
	ctx.beginPath()
	ctx.moveTo(fx * r * (reach + 0.2), fy * r * (reach + 0.2))
	ctx.lineTo(fx * r * reach - fy * r * 0.18, fy * r * reach + fx * r * 0.18)
	ctx.lineTo(fx * r * reach + fy * r * 0.18, fy * r * reach - fx * r * 0.18)
	ctx.closePath()
	ctx.fill()


static func drawHorseman(ctx: Ctx, e: Entity, tc: Dictionary, moving: bool) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	var atkFrac := e.attackCooldown / e.attackInterval if e.attackInterval > 0 else 0.0
	horse(ctx, e, moving, {"coat": "#6b5238", "scaleX": 1.18, "scaleY": 0.54, "gallopHz": 12, "caparison": tc.main, "capAlpha": 0.6})
	rider(ctx, r, {"coat": tc.main, "coatDark": tc.dark, "helm": "cap", "tone": PAL.leather})
	# recurve bow aimed forward; string draws back as a shot readies
	var bx := -r * 0.08 + fx * r * 0.5
	var by := -r * 0.42 + fy * r * 0.5
	var draw := (atkFrac - 0.7) / 0.3 if atkFrac > 0.7 else 0.0
	ctx.strokeStyle = PAL.woodDark
	ctx.lineWidth = r * 0.08
	ctx.beginPath()
	ctx.arc(bx, by, r * 0.55, e.facing - 1.15, e.facing + 1.15)
	ctx.stroke()
	ctx.strokeStyle = "#ddd6c2"
	ctx.lineWidth = 0.8
	var ax := bx + cos(e.facing - 1.15) * r * 0.55
	var ay := by + sin(e.facing - 1.15) * r * 0.55
	var cx2 := bx + cos(e.facing + 1.15) * r * 0.55
	var cy2 := by + sin(e.facing + 1.15) * r * 0.55
	ctx.beginPath()
	ctx.moveTo(ax, ay)
	ctx.lineTo(bx - fx * r * draw * 0.7, by - fy * r * draw * 0.7)
	ctx.lineTo(cx2, cy2)
	ctx.stroke()


static func drawRaider(ctx: Ctx, e: Entity, tc: Dictionary, moving: bool, lunge: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	horse(ctx, e, moving, {"coat": "#574033", "scaleX": 1.2, "scaleY": 0.5, "gallopHz": 13})
	rider(ctx, r, {"coat": tc.main, "coatDark": tc.dark, "helm": "cap", "tone": PAL.steelDark})
	# curved sabre sweeping through its strike
	var d := swungDir(e, 1.3)
	var ox := -r * 0.08
	var oy := -r * 0.4
	ctx.strokeStyle = grad(ctx, ox, oy, ox + d.x * r * 1.4, oy + d.y * r * 1.4, "#f2f4f8", PAL.steelDark)
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.12
	ctx.beginPath()
	ctx.arc(ox + d.x * r * (0.7 + lunge * 0.3), oy + d.y * r * (0.7 + lunge * 0.3), r * 0.6, e.facing - 0.7 + d.z * 0.4, e.facing + 0.7 + d.z * 0.4)
	ctx.stroke()


static func drawCrossbow(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, tc.main, tc.dark)
	head(ctx, r, {"helm": "open", "tone": PAL.steelDark})  # kettle helm
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	if styled() and gLook.helm == "visor" and FactionFigures.factionBow(ctx, gLook, r, fx, fy, e.facing, 1.0 if atkFrac > 0.85 else 0.0, true):
		return
	var px := -fy
	var py := fx
	# horizontal stock pointed forward
	ctx.strokeStyle = PAL.woodDark
	ctx.lineWidth = 2.4
	ctx.beginPath()
	ctx.moveTo(fx * r * 0.2, fy * r * 0.2)
	ctx.lineTo(fx * r * 1.5, fy * r * 1.5)
	ctx.stroke()
	# steel prod across the front
	var tipX := fx * r * 1.5
	var tipY := fy * r * 1.5
	ctx.strokeStyle = PAL.steel
	ctx.lineWidth = 2
	ctx.beginPath()
	ctx.moveTo(tipX + px * r * 0.7, tipY + py * r * 0.7)
	ctx.lineTo(tipX - px * r * 0.7, tipY - py * r * 0.7)
	ctx.stroke()
	# loaded bolt glints just before a shot
	if atkFrac > 0.6:
		ctx.strokeStyle = "#e8e2cf"
		ctx.lineWidth = 1.4
		ctx.beginPath()
		ctx.moveTo(fx * r * 0.4, fy * r * 0.4)
		ctx.lineTo(tipX, tipY)
		ctx.stroke()


## A shaded, spoked siege wheel.
static func siegeWheel(ctx: Ctx, wx: float, wy: float, wr: float) -> void:
	ctx.fillStyle = grad(ctx, wx - wr, wy, wx + wr, wy, "#4a3826", "#241910")
	ctx.beginPath()
	ctx.arc(wx, wy, wr, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1.2)
	ctx.strokeStyle = "#1c130a"
	ctx.lineWidth = 1
	for i in 4:
		var a := (i * PI) / 4
		ctx.beginPath()
		ctx.moveTo(wx - cos(a) * wr, wy - sin(a) * wr)
		ctx.lineTo(wx + cos(a) * wr, wy + sin(a) * wr)
		ctx.stroke()
	ctx.fillStyle = "#5a4632"
	ctx.beginPath()
	ctx.arc(wx, wy, wr * 0.26, 0, PI * 2)
	ctx.fill()


static func drawTrebuchet(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	ctx.save()
	ctx.rotate(e.facing)
	siegeWheel(ctx, -r * 0.6, r * 0.55, r * 0.28)
	siegeWheel(ctx, r * 0.6, r * 0.55, r * 0.28)
	# heavy timber base
	ctx.fillStyle = grad(ctx, 0, -r * 0.6, 0, r * 0.6, Palette.shade(PAL.wood, 0.1), PAL.woodDark)
	ctx.beginPath()
	ctx.roundRect(-r * 0.85, -r * 0.55, r * 1.7, r * 1.1, 3)
	ctx.fill()
	softOutline(ctx, 1.4)
	# A-frame uprights
	ctx.strokeStyle = PAL.woodLight
	ctx.lineWidth = r * 0.12
	ctx.lineCap = "round"
	for s in [-1, 1]:
		ctx.beginPath()
		ctx.moveTo(s * r * 0.5, r * 0.4)
		ctx.lineTo(0, -r * 1.1)
		ctx.stroke()
	# throwing beam (counterweight short end, sling long end)
	var beam := 1.1 if atkFrac > 0.85 else (-0.7 + (1 - atkFrac) * 1.8 if atkFrac > 0 else 1.1)
	var px := cos(beam)
	var py := sin(beam)
	ctx.strokeStyle = grad(ctx, px * r * 1.7, -r * 1.1 - py * r * 1.7, -px * r * 0.7, -r * 1.1 + py * r * 0.7, PAL.woodLight, PAL.woodDark)
	ctx.lineWidth = r * 0.14
	ctx.beginPath()
	ctx.moveTo(px * r * 1.7, -r * 1.1 - py * r * 1.7)
	ctx.lineTo(-px * r * 0.7, -r * 1.1 + py * r * 0.7)
	ctx.stroke()
	ctx.fillStyle = "#2c2c30"  # counterweight
	ctx.beginPath()
	ctx.arc(-px * r * 0.7, -r * 1.1 + py * r * 0.7, r * 0.22, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1.2)
	ctx.fillStyle = PAL.leather  # sling pouch
	ctx.beginPath()
	ctx.arc(px * r * 1.7, -r * 1.1 - py * r * 1.7, r * 0.2, 0, PI * 2)
	ctx.fill()
	ctx.fillStyle = tc.main
	ctx.fillRect(-r * 0.85, -r * 0.05, r * 0.32, r * 0.18)
	ctx.restore()


static func drawCatapult(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	ctx.save()
	ctx.rotate(e.facing)
	for w in [[-r * 0.7, -r * 0.62], [-r * 0.7, r * 0.62], [r * 0.7, -r * 0.62], [r * 0.7, r * 0.62]]:
		siegeWheel(ctx, w[0], w[1], r * 0.26)
	# frame
	ctx.fillStyle = grad(ctx, 0, -r * 0.55, 0, r * 0.55, Palette.shade(PAL.wood, 0.1), PAL.woodDark)
	ctx.beginPath()
	ctx.roundRect(-r * 0.9, -r * 0.5, r * 1.8, r, 3)
	ctx.fill()
	softOutline(ctx, 1.4)
	# throwing arm: cocked back to reload, snaps forward on fire
	var armAngle := -0.4 if atkFrac > 0.85 else (0.9 - (0.85 - atkFrac) if atkFrac > 0 else -0.4)
	ctx.strokeStyle = grad(ctx, -r * 0.4, 0, -r * 0.4 + cos(armAngle) * r * 1.2, -sin(armAngle) * r * 1.2, PAL.woodLight, PAL.woodDark)
	ctx.lineWidth = r * 0.14
	ctx.lineCap = "round"
	ctx.beginPath()
	ctx.moveTo(-r * 0.4, 0)
	ctx.lineTo(-r * 0.4 + cos(armAngle) * r * 1.2, -sin(armAngle) * r * 1.2)
	ctx.stroke()
	ctx.fillStyle = PAL.leather  # cup
	ctx.beginPath()
	ctx.arc(-r * 0.4 + cos(armAngle) * r * 1.2, -sin(armAngle) * r * 1.2, r * 0.2, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1.1)
	ctx.fillStyle = tc.main
	ctx.fillRect(r * 0.7, -r * 0.08, r * 0.4, r * 0.18)
	ctx.restore()


static func drawRam(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	ctx.save()
	ctx.rotate(e.facing)
	siegeWheel(ctx, -r * 0.5, r * 0.7, r * 0.22)
	siegeWheel(ctx, r * 0.5, r * 0.7, r * 0.22)
	# swinging log with an iron head (drawn under the roof)
	var swing := (atkFrac - 0.8) / 0.2 if atkFrac > 0.8 else 0.0
	ctx.strokeStyle = grad(ctx, -r * 0.6, 0, r * 1.3, 0, Palette.shade(PAL.trunk, 0.12), Palette.shade(PAL.trunk, -0.2))
	ctx.lineWidth = r * 0.26
	ctx.lineCap = "round"
	ctx.beginPath()
	ctx.moveTo(-r * 0.5, 0)
	ctx.lineTo(r * (1.0 + swing * 0.5), 0)
	ctx.stroke()
	ctx.fillStyle = grad(ctx, r * (1.0 + swing * 0.5), -r * 0.2, r * (1.2 + swing * 0.5), r * 0.2, PAL.steel, PAL.steelDark)
	ctx.beginPath()
	ctx.moveTo(r * (1.0 + swing * 0.5), -r * 0.2)
	ctx.lineTo(r * (1.35 + swing * 0.5), 0)
	ctx.lineTo(r * (1.0 + swing * 0.5), r * 0.2)
	ctx.closePath()
	ctx.fill()
	softOutline(ctx, 1.2)
	# protective gabled roof
	ctx.fillStyle = grad(ctx, 0, -r * 0.75, 0, r * 0.75, Palette.shade(PAL.wood, 0.08), PAL.woodDark)
	ctx.beginPath()
	ctx.moveTo(-r, r * 0.55)
	ctx.lineTo(-r, -r * 0.5)
	ctx.lineTo(-r * 0.6, -r * 0.78)
	ctx.lineTo(r * 0.6, -r * 0.78)
	ctx.lineTo(r, -r * 0.5)
	ctx.lineTo(r, r * 0.55)
	ctx.lineTo(r * 0.6, r * 0.75)
	ctx.lineTo(-r * 0.6, r * 0.75)
	ctx.closePath()
	ctx.fill()
	softOutline(ctx, 1.6)
	ctx.strokeStyle = "rgba(20,16,10,0.3)"  # planks
	ctx.lineWidth = 1
	for i in range(-2, 3):
		ctx.beginPath()
		ctx.moveTo(i * r * 0.35, -r * 0.6)
		ctx.lineTo(i * r * 0.35, r * 0.55)
		ctx.stroke()
	ctx.fillStyle = tc.main  # team ridge
	ctx.fillRect(-r * 0.6, -r * 0.78, r * 1.2, 3)
	ctx.restore()


static func drawHero(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float, time: float) -> void:
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	# flowing team cape behind
	ctx.fillStyle = Palette.shade(tc.dark, -0.08)
	ctx.beginPath()
	ctx.moveTo(-r * 0.5, -r * 0.2)
	ctx.quadraticCurveTo(-r * 1.1, r * 0.6 + sin(time * 4) * 1.5, -r * 0.2, r * 1.0)
	ctx.quadraticCurveTo(r * 0.2, r * 0.5, r * 0.5, -r * 0.2)
	ctx.fill()
	# kite shield on the off-hand (behind the torso)
	var shx := -fy * r * 0.82
	var shy := fx * r * 0.82
	ctx.fillStyle = grad(ctx, shx, shy - r * 0.5, shx, shy + r * 0.5, tc.main, tc.dark)
	ctx.beginPath()
	ctx.ellipse(shx, shy, r * 0.4, r * 0.58, e.facing, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1.4)
	ctx.fillStyle = "#ffd24a"
	ctx.beginPath()
	ctx.arc(shx, shy, r * 0.14, 0, PI * 2)
	ctx.fill()
	body(ctx, e, tc.main, tc.dark, {"pauldrons": true, "chestPlate": true})
	head(ctx, r, {"helm": "full", "tone": C.PAL.steel, "plume": tc.light})
	# gleaming greatsword sweeping through its strike
	var d := swungDir(e, 1.1)
	blade(ctx, r, d.x, d.y, 1.9 + lunge * 0.4, "#ffd24a")


static func drawMonk(ctx: Ctx, e: Entity, tc: Dictionary, time: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, "#cfc4a8", "#a89a78")
	# team stole down the front of the robe
	ctx.fillStyle = tc.main
	ctx.fillRect(-r * 0.08, -r * 0.5, r * 0.16, r * 0.95)
	head(ctx, r, {"helm": "hood", "tone": "#a89a78"})
	# staff with a glowing tip
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	ctx.strokeStyle = grad(ctx, fx * r * 0.5, fy * r * 0.5 + r * 0.5, fx * r * 0.9, fy * r * 0.9 - r * 1.1, PAL.woodLight, PAL.woodDark)
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.09
	ctx.beginPath()
	ctx.moveTo(fx * r * 0.5, fy * r * 0.5 + r * 0.5)
	ctx.lineTo(fx * r * 0.9, fy * r * 0.9 - r * 1.1)
	ctx.stroke()
	var glow := 0.6 + sin(time * 4 + e.id) * 0.3
	ctx.fillStyle = Palette.withAlpha(PAL.heal, glow)
	ctx.beginPath()
	ctx.arc(fx * r * 0.9, fy * r * 0.9 - r * 1.2, r * 0.18, 0, PI * 2)
	ctx.fill()


# Two-Handed Swordsman: heavy plate, full helm, a huge greatsword gripped in
# both gauntlets — no shield (that's what tells it apart from the Militia).
static func drawTwohand(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float) -> void:
	var r := e.radius
	body(ctx, e, tc.main, tc.dark, {"pauldrons": true, "chestPlate": true})
	head(ctx, r, {"helm": "full", "tone": C.PAL.steel})
	var d := swungDir(e, 1.4)  # wide two-handed sweep
	# huge greatsword, both gauntlets on the grip
	ctx.fillStyle = C.PAL.steelDark
	for k in [0.05, -0.3]:
		ctx.beginPath()
		ctx.arc(d.x * r * k, d.y * r * k, r * 0.15, 0, PI * 2)
		ctx.fill()
	blade(ctx, r, d.x, d.y, 2.05 + lunge + d.z * 0.2, Palette.shade(tc.dark, -0.1), -0.5)


# Pikeman: a far longer haft than the Spearman, a small team pennant flying at
# the grip, and a slim leaf-blade — the reach is the read.
# --------------------------------------------------------- faction units --
# Drawn through the shared parts, so each wears its faction's helmet, armour
# and shield automatically; what is here is the weapon and the silhouette.

## The Legion: scutum up, short sword low and forward.
static func drawLegionary(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float) -> void:
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	roundShield(ctx, r, f.x, f.y, tc)
	body(ctx, e, tc.main, tc.dark, {"chestPlate": true})
	head(ctx, r, {"helm": "full"})
	var d := swungDir(e, 0.6)  # a thrust, not a swing
	blade(ctx, r, d.x, d.y, 1.25 + lunge * 0.5, Palette.shade(tc.dark, -0.1))


## The Shogunate: lacquered armour and a long, slightly curved blade.
static func drawSamurai(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float) -> void:
	var r := e.radius
	body(ctx, e, tc.main, tc.dark, {"pauldrons": true, "chestPlate": true})
	head(ctx, r, {"helm": "full"})
	var d := swungDir(e, 1.6)
	var dx := d.x
	var dy := d.y
	var len := 2.1 + lunge * 0.8 + d.z * 0.2
	var x0 := dx * r * 0.25
	var y0 := dy * r * 0.25
	var x1 := dx * r * len
	var y1 := dy * r * len
	# Curved edge: bend the line a touch off the swing.
	var mx := (x0 + x1) / 2 - dy * r * 0.14
	var my := (y0 + y1) / 2 + dx * r * 0.14
	ctx.lineCap = "round"
	ctx.strokeStyle = "#1c1410"  # tsuka
	ctx.lineWidth = r * 0.13
	ctx.beginPath(); ctx.moveTo(-dx * r * 0.15, -dy * r * 0.15); ctx.lineTo(x0, y0); ctx.stroke()
	ctx.fillStyle = "#c8a040"  # tsuba
	ctx.beginPath(); ctx.arc(x0, y0, r * 0.1, 0, PI * 2); ctx.fill()
	ctx.strokeStyle = "#e8eef4"
	ctx.lineWidth = r * 0.085
	ctx.beginPath(); ctx.moveTo(x0, y0); ctx.quadraticCurveTo(mx, my, x1, y1); ctx.stroke()


## The Khanate: a shaggy steppe pony, a rider drawing a recurve bow.
static func drawHorseArcher(ctx: Ctx, e: Entity, tc: Dictionary, moving: bool, atkFrac: float) -> void:
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	horse(ctx, e, moving, {"coat": "#6a4a30", "scaleX": 1.1, "scaleY": 0.56, "caparison": tc.main, "capAlpha": 0.55, "gallopHz": 12})
	rider(ctx, r, {"coat": tc.main, "coatDark": tc.dark, "helm": "open"})
	# Recurve bow held out to the side it shoots across.
	var draw := (atkFrac - 0.7) / 0.3 if atkFrac > 0.7 else 0.0
	var bx := -r * 0.08 + fx * r * 0.45
	var by := -r * 0.45 + fy * r * 0.45
	var px := -fy
	var py := fx
	ctx.strokeStyle = "#5a3a1c"
	ctx.lineWidth = 1.8
	ctx.beginPath()
	ctx.moveTo(bx - px * r * 0.55, by - py * r * 0.55)
	ctx.quadraticCurveTo(bx + fx * r * 0.35, by + fy * r * 0.35, bx + px * r * 0.55, by + py * r * 0.55)
	ctx.stroke()
	ctx.strokeStyle = "#ddd6c2"
	ctx.lineWidth = 0.8
	ctx.beginPath()
	ctx.moveTo(bx - px * r * 0.55, by - py * r * 0.55)
	ctx.lineTo(bx - fx * r * 0.35 * draw, by - fy * r * 0.35 * draw)
	ctx.lineTo(bx + px * r * 0.55, by + py * r * 0.55)
	ctx.stroke()


## The Ascendancy: a white hardsuit and a long energy rifle.
static func drawPulseTrooper(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float, time: float) -> void:
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	body(ctx, e, tc.main, tc.dark, {"chestPlate": true, "pauldrons": true})
	head(ctx, r, {"helm": "full"})
	var len := r * 1.9
	var sx := fx * r * 0.1 - fy * r * 0.18
	var sy := fy * r * 0.1 + fx * r * 0.18
	ctx.lineCap = "round"
	ctx.strokeStyle = "#3a4250"
	ctx.lineWidth = r * 0.22
	ctx.beginPath(); ctx.moveTo(sx - fx * r * 0.3, sy - fy * r * 0.3); ctx.lineTo(sx + fx * len, sy + fy * len); ctx.stroke()
	ctx.strokeStyle = "#dfe4ea"
	ctx.lineWidth = r * 0.1
	ctx.beginPath(); ctx.moveTo(sx, sy); ctx.lineTo(sx + fx * len * 0.7, sy + fy * len * 0.7); ctx.stroke()
	var glow := 0.55 + sin(time * 6 + e.id) * 0.2
	ctx.save()
	ctx.shadowColor = "#3ad8e8"
	ctx.shadowBlur = 5
	ctx.strokeStyle = Palette.withAlpha("#8af0ff", glow)
	ctx.lineWidth = r * 0.06
	ctx.beginPath(); ctx.moveTo(sx + fx * len * 0.3, sy + fy * len * 0.3); ctx.lineTo(sx + fx * len, sy + fy * len); ctx.stroke()
	# The shot: a hard flash at the muzzle.
	if atkFrac > 0.85:
		var k := (atkFrac - 0.85) / 0.15
		ctx.fillStyle = Palette.withAlpha("#bff8ff", k)
		ctx.shadowBlur = 12
		ctx.beginPath(); ctx.arc(sx + fx * (len + 3), sy + fy * (len + 3), r * 0.35 * k, 0, PI * 2); ctx.fill()
	ctx.restore()


## The Ascendancy: a hover-sled — no horse, a hull riding on light.
static func drawSkimmer(ctx: Ctx, e: Entity, tc: Dictionary, moving: bool, lunge: float, time: float) -> void:
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	var bob := sin(time * 4 + e.id) * 1.2
	# Underglow on the ground.
	ctx.save()
	ctx.fillStyle = Palette.withAlpha("#3ad8e8", 0.3 if moving else 0.2)
	ctx.beginPath(); ctx.ellipse(0, r * 0.35, r * 1.2, r * 0.45, 0, 0, PI * 2); ctx.fill()
	ctx.restore()
	ctx.save()
	ctx.translate(0, -r * 0.1 + bob)
	ctx.rotate(e.facing)
	# Hull.
	ctx.fillStyle = grad(ctx, 0, -r * 0.5, 0, r * 0.5, "#ffffff", "#aab2be")
	ctx.beginPath()
	ctx.moveTo(r * 1.35, 0)
	ctx.quadraticCurveTo(r * 0.6, -r * 0.55, -r * 1.0, -r * 0.45)
	ctx.lineTo(-r * 1.15, 0)
	ctx.lineTo(-r * 1.0, r * 0.45)
	ctx.quadraticCurveTo(r * 0.6, r * 0.55, r * 1.35, 0)
	ctx.closePath(); ctx.fill(); softOutline(ctx, 1.2)
	ctx.fillStyle = tc.main  # team stripe down the spine
	ctx.fillRect(-r * 0.9, -r * 0.08, r * 1.8, r * 0.16)
	ctx.fillStyle = "#3ad8e8"  # thrusters
	for sy in [-0.3, 0.3]:
		ctx.beginPath(); ctx.arc(-r * 1.08, sy * r, r * 0.12, 0, PI * 2); ctx.fill()
	ctx.restore()
	ctx.save()
	ctx.translate(0, bob)
	rider(ctx, r * 0.9, {"coat": tc.main, "coatDark": tc.dark, "helm": "full"})
	ctx.restore()
	# A lance of light, couched forward.
	var reach := 1.8 + lunge
	ctx.save()
	ctx.shadowColor = "#3ad8e8"; ctx.shadowBlur = 5
	ctx.strokeStyle = "#bff8ff"
	ctx.lineWidth = r * 0.08
	ctx.lineCap = "round"
	ctx.beginPath(); ctx.moveTo(fx * r * 0.3, -r * 0.35 + fy * r * 0.3 + bob); ctx.lineTo(fx * r * reach, -r * 0.2 + fy * r * reach + bob); ctx.stroke()
	ctx.restore()


# ------------------------------------------------------------ oath units --
# Each Oath's signature unit wears its Oath's colour somewhere prominent, so a
# realm's choice reads on the battlefield, not only in a menu.

## Oath of the Sword: bare-headed, a red oath-band, a blade in each hand.
static func drawSwornBlade(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float) -> void:
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	body(ctx, e, tc.main, tc.dark, {"sash": "#d8574a"})
	head(ctx, r, {"helm": "bare", "hair": "#3a2a1c"})
	# The oath-band tied round the brow, tails flying behind.
	ctx.strokeStyle = "#d8574a"
	ctx.lineWidth = r * 0.14
	ctx.beginPath()
	ctx.arc(0, -r * 0.8, r * 0.3, PI * 1.05, PI * 1.95)
	ctx.stroke()
	ctx.lineWidth = r * 0.09
	ctx.beginPath()
	ctx.moveTo(-fx * r * 0.2, -r * 0.8 - fy * r * 0.2)
	ctx.lineTo(-fx * r * 0.62, -r * 0.62 - fy * r * 0.62)
	ctx.stroke()
	# Off-hand short blade held low, main blade sweeping.
	blade(ctx, r * 0.72, -fy * 0.7 + fx * 0.5, fx * 0.7 + fy * 0.5, 1.4, Palette.shade(tc.dark, -0.1))
	var d := swungDir(e, 1.2)
	blade(ctx, r, d.x, d.y, 1.8 + lunge * 0.5, "#8a2a22")


## Oath of the Lance: barded horse, pennoned lance that drops for the charge.
static func drawLancer(ctx: Ctx, e: Entity, tc: Dictionary, moving: bool, lunge: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	var charged := e.chargeRun >= C.CHARGE_RUN
	horse(ctx, e, moving, {"coat": "#3e2e22", "scaleX": 1.3, "scaleY": 0.64, "caparison": tc.main, "capAlpha": 0.95, "gallopHz": 13})
	# Barding: a steel chanfron on the horse's head.
	ctx.fillStyle = PAL.steel
	ctx.beginPath()
	ctx.ellipse(fx * r * 1.15, fy * r * 1.15 - r * 0.05, r * 0.2, r * 0.13, e.facing, 0, PI * 2)
	ctx.fill()
	rider(ctx, r, {"coat": tc.main, "coatDark": tc.dark, "helm": "full", "tone": PAL.steel, "plume": "#e0a040"})
	# The lance: long, and couched low and forward once the run-up is in.
	var sx := -r * 0.08 + fx * r * 0.2
	var sy := -r * (0.25 if charged else 0.45) + fy * r * 0.2
	var reach := (2.9 if charged else 2.4) + lunge
	ctx.strokeStyle = grad(ctx, sx, sy, fx * r * reach, fy * r * reach, "#e8d4a8", PAL.woodDark)
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.13
	ctx.beginPath()
	ctx.moveTo(sx - fx * r * 0.6, sy - fy * r * 0.6)
	ctx.lineTo(fx * r * reach, fy * r * reach)
	ctx.stroke()
	# Vamplate (hand guard) and head.
	ctx.fillStyle = PAL.steelDark
	ctx.beginPath()
	ctx.arc(sx + fx * r * 0.25, sy + fy * r * 0.25, r * 0.16, 0, PI * 2)
	ctx.fill()
	ctx.fillStyle = PAL.steel
	ctx.beginPath()
	ctx.moveTo(fx * r * (reach + 0.3), fy * r * (reach + 0.3))
	ctx.lineTo(fx * r * reach - fy * r * 0.14, fy * r * reach + fx * r * 0.14)
	ctx.lineTo(fx * r * reach + fy * r * 0.14, fy * r * reach - fx * r * 0.14)
	ctx.closePath()
	ctx.fill()
	# Pennant in the Oath's gold, streaming back.
	var px := fx * r * (reach - 0.5)
	var py := fy * r * (reach - 0.5)
	ctx.fillStyle = "#e0a040"
	ctx.beginPath()
	ctx.moveTo(px, py)
	ctx.lineTo(px - fx * r * 0.6 + fy * r * 0.3, py - fy * r * 0.6 - fx * r * 0.3)
	ctx.lineTo(px - fx * r * 0.35, py - fy * r * 0.35)
	ctx.closePath()
	ctx.fill()


## Oath of the Bow: hooded in woodland green, a tall yew bow, a leaf cloak.
static func drawRanger(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float) -> void:
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	# Cloak behind the body, ragged like leaves.
	ctx.fillStyle = "#3f5a34"
	ctx.beginPath()
	ctx.moveTo(-r * 0.55, -r * 0.55)
	for i in 6:
		var t := i / 5.0
		ctx.lineTo(-r * 0.62 + t * r * 1.24, r * (0.45 + (i % 2) * 0.16))
	ctx.lineTo(r * 0.55, -r * 0.55)
	ctx.closePath()
	ctx.fill()
	body(ctx, e, "#4c6b3c", "#2f4426", {"sash": tc.main})
	head(ctx, r, {"helm": "hood", "tone": "#3f5a34"})
	var draw := (atkFrac - 0.7) / 0.3 if atkFrac > 0.7 else 0.0
	ctx.strokeStyle = "#5a3f22"
	ctx.lineWidth = 2.2
	ctx.beginPath()
	ctx.arc(fx * r * 0.6, fy * r * 0.6, r * 1.1, e.facing - 1.3, e.facing + 1.3)
	ctx.stroke()
	ctx.strokeStyle = "#e4dcc6"
	ctx.lineWidth = 0.8
	var ax := fx * r * 0.6 + cos(e.facing - 1.3) * r * 1.1
	var ay := fy * r * 0.6 + sin(e.facing - 1.3) * r * 1.1
	var bx := fx * r * 0.6 + cos(e.facing + 1.3) * r * 1.1
	var by := fy * r * 0.6 + sin(e.facing + 1.3) * r * 1.1
	ctx.beginPath()
	ctx.moveTo(ax, ay)
	ctx.lineTo(fx * r * (0.6 - draw * 0.9), fy * r * (0.6 - draw * 0.9))
	ctx.lineTo(bx, by)
	ctx.stroke()
	# Green-fletched arrows in the quiver.
	ctx.fillStyle = "#6fbf5a"
	for k in [-1, 1]:
		ctx.fillRect(-fy * r * 0.7 - 1 + k * 1.6, fx * r * 0.7 - 4, 1.6, 4)


## Oath of the Shield: plate and a halberd — axe, spike and hook on one haft.
static func drawHalberdier(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, tc.main, tc.dark, {"chestPlate": true, "sash": "#9aa7b8"})
	head(ctx, r, {"helm": "full", "tone": PAL.steelDark, "plume": "#9aa7b8"})
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	var reach := 2.7 + lunge * 0.6 + strikeT(e) * 0.4
	ctx.strokeStyle = grad(ctx, -fx * r, -fy * r, fx * r * reach, fy * r * reach, PAL.woodLight, PAL.woodDark)
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.11
	ctx.beginPath()
	ctx.moveTo(-fx * r * 0.9, -fy * r * 0.9)
	ctx.lineTo(fx * r * reach, fy * r * reach)
	ctx.stroke()
	var hx := fx * r * (reach - 0.35)
	var hy := fy * r * (reach - 0.35)
	# Axe blade on one side, the hook on the other, the spike on top.
	ctx.fillStyle = PAL.steel
	ctx.beginPath()
	ctx.moveTo(hx + fx * r * 0.28, hy + fy * r * 0.28)
	ctx.quadraticCurveTo(hx - fy * r * 0.75, hy + fx * r * 0.75, hx - fx * r * 0.3 - fy * r * 0.35, hy - fy * r * 0.3 + fx * r * 0.35)
	ctx.lineTo(hx - fx * r * 0.2, hy - fy * r * 0.2)
	ctx.closePath()
	ctx.fill()
	softOutline(ctx, 0.8)
	ctx.beginPath()
	ctx.moveTo(hx, hy)
	ctx.lineTo(hx + fy * r * 0.34 - fx * r * 0.12, hy - fx * r * 0.34 - fy * r * 0.12)
	ctx.lineTo(hx + fy * r * 0.12, hy - fx * r * 0.12)
	ctx.closePath()
	ctx.fill()
	ctx.beginPath()
	ctx.moveTo(fx * r * (reach + 0.45), fy * r * (reach + 0.45))
	ctx.lineTo(fx * r * reach - fy * r * 0.1, fy * r * reach + fx * r * 0.1)
	ctx.lineTo(fx * r * reach + fy * r * 0.1, fy * r * reach - fx * r * 0.1)
	ctx.closePath()
	ctx.fill()


## Oath of the Engine: a bronze monster on a sledge, banded in brass.
static func drawGreatBombard(ctx: Ctx, e: Entity, atkFrac: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	var recoil := (atkFrac - 0.85) / 0.15 if atkFrac > 0.85 else 0.0
	# Timber sledge, no wheels — it is dragged.
	ctx.fillStyle = grad(ctx, 0, -r * 0.3, 0, r * 0.6, PAL.woodLight, PAL.woodDark)
	ctx.save()
	ctx.rotate(e.facing)
	ctx.beginPath()
	ctx.roundRect(-r * 1.0, -r * 0.5, r * 2.0, r * 1.0, r * 0.2)
	ctx.fill()
	softOutline(ctx, 1)
	ctx.restore()
	var bx := -fx * r * 0.45 * recoil
	var by := -fy * r * 0.45 * recoil - r * 0.2
	ctx.fillStyle = grad(ctx, bx, by - r * 0.45, bx, by + r * 0.45, "#b08a4a", "#5a3f1c")
	ctx.beginPath()
	ctx.ellipse(bx + fx * r * 0.35, by + fy * r * 0.35, r * 1.05, r * 0.46, e.facing, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1.2)
	ctx.fillStyle = "#e0c070"
	for t in [-0.35, 0.15, 0.7]:
		ctx.beginPath()
		ctx.ellipse(bx + fx * r * t, by + fy * r * t, r * 0.12, r * 0.5, e.facing, 0, PI * 2)
		ctx.fill()
	# The muzzle: a dark mouth you can see from across the field.
	ctx.fillStyle = "#1e1a14"
	ctx.beginPath()
	ctx.ellipse(bx + fx * r * 1.35, by + fy * r * 1.35, r * 0.18, r * 0.3, e.facing, 0, PI * 2)
	ctx.fill()
	if recoil > 0.1:
		ctx.save()
		ctx.shadowColor = "#ffb03a"
		ctx.shadowBlur = r * 3.5
		ctx.fillStyle = "#ffd88a"
		ctx.beginPath()
		ctx.arc(bx + fx * r * 1.7, by + fy * r * 1.7, r * 0.55 * recoil, 0, PI * 2)
		ctx.fill()
		ctx.restore()


## Oath of the Crown: gilded plate, a royal plume, a gold-hilted greatsword.
static func drawRoyalGuard(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float, time: float) -> void:
	var r := e.radius
	# A short cape in the Oath's purple.
	ctx.fillStyle = "#6e3f8a"
	ctx.beginPath()
	ctx.moveTo(-r * 0.5, -r * 0.5)
	ctx.quadraticCurveTo(0, r * (0.75 + sin(time * 3 + e.id) * 0.04), r * 0.5, -r * 0.5)
	ctx.closePath()
	ctx.fill()
	body(ctx, e, tc.main, tc.dark, {"pauldrons": true, "chestPlate": true})
	# A gold crown device on the breastplate.
	ctx.fillStyle = "#e8c860"
	ctx.beginPath()
	ctx.moveTo(-r * 0.18, -r * 0.2)
	ctx.lineTo(-r * 0.18, -r * 0.36)
	ctx.lineTo(-r * 0.09, -r * 0.27)
	ctx.lineTo(0, -r * 0.4)
	ctx.lineTo(r * 0.09, -r * 0.27)
	ctx.lineTo(r * 0.18, -r * 0.36)
	ctx.lineTo(r * 0.18, -r * 0.2)
	ctx.closePath()
	ctx.fill()
	head(ctx, r, {"helm": "full", "tone": "#d7b456", "plume": "#c890e8"})
	var d := swungDir(e, 1.3)
	blade(ctx, r, d.x, d.y, 2.1 + lunge + d.z * 0.2, "#d7b456", -0.45)


static func drawPikeman(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, tc.main, tc.dark)
	head(ctx, r, {"helm": "open", "tone": PAL.steelDark})
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	var reach := 3.0 + lunge * 0.7 + strikeT(e) * 0.4  # pike (Spearman is ~2.2)
	ctx.strokeStyle = grad(ctx, -fx * r, -fy * r, fx * r * reach, fy * r * reach, PAL.woodLight, PAL.woodDark)
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.09
	ctx.beginPath()
	ctx.moveTo(-fx * r * 1.0, -fy * r * 1.0)
	ctx.lineTo(fx * r * reach, fy * r * reach)
	ctx.stroke()
	# pennant near the grip
	var bx := fx * r * 0.55
	var by := fy * r * 0.55
	ctx.fillStyle = tc.main
	ctx.beginPath()
	ctx.moveTo(bx, by)
	ctx.lineTo(bx + fx * r * 0.55 - fy * r * 0.42, by + fy * r * 0.55 + fx * r * 0.42)
	ctx.lineTo(bx + fx * r * 0.55, by + fy * r * 0.55)
	ctx.closePath()
	ctx.fill()
	# long leaf head
	var tipX := fx * r * reach
	var tipY := fy * r * reach
	if styled() and FactionFigures.factionSpearHead(ctx, gLook, tipX, tipY, fx, fy, r * 1.15):
		return
	ctx.fillStyle = PAL.steel
	ctx.beginPath()
	ctx.moveTo(tipX + fx * r * 0.5, tipY + fy * r * 0.5)
	ctx.lineTo(tipX - fy * r * 0.18, tipY + fx * r * 0.18)
	ctx.lineTo(tipX + fy * r * 0.18, tipY - fx * r * 0.18)
	ctx.closePath()
	ctx.fill()


# Scout: a light, pale, fast pony and an unarmed rider carrying a tall pennant
# mast — reads as a recon unit, not the sabre-swinging Raider.
static func drawScout(ctx: Ctx, e: Entity, tc: Dictionary, moving: bool) -> void:
	var r := e.radius
	horse(ctx, e, moving, {"coat": "#9a8260", "scaleX": 1.15, "scaleY": 0.46, "gallopHz": 15})
	rider(ctx, r, {"coat": tc.main, "coatDark": tc.dark, "helm": "bare"})
	# tall scouting pennant (fixed mast, not a weapon)
	ctx.strokeStyle = C.PAL.woodLight
	ctx.lineWidth = r * 0.06
	ctx.beginPath()
	ctx.moveTo(r * 0.15, -r * 0.3)
	ctx.lineTo(r * 0.15, -r * 1.5)
	ctx.stroke()
	ctx.fillStyle = tc.light
	ctx.beginPath()
	ctx.moveTo(r * 0.15, -r * 1.5)
	ctx.lineTo(r * 0.78, -r * 1.32)
	ctx.lineTo(r * 0.15, -r * 1.12)
	ctx.closePath()
	ctx.fill()
	softOutline(ctx, 1)


# Javelin Thrower: a soldier in team colours with a back-fan of spare javelins,
# winding up an overhand throw — distinct from the leather-clad Skirmisher.
static func drawJavelin(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	# bundle of spare javelins fanned across the back (behind the body)
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	ctx.strokeStyle = PAL.woodDark
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.07
	for o in [-0.28, 0.0, 0.28]:
		var ang: float = e.facing + PI + o
		ctx.beginPath()
		ctx.moveTo(cos(ang) * r * 0.2, sin(ang) * r * 0.2)
		ctx.lineTo(cos(ang) * r * 1.2, sin(ang) * r * 1.2)
		ctx.stroke()
	body(ctx, e, tc.main, tc.dark, {"sash": tc.light})
	head(ctx, r, {"helm": "cap", "tone": PAL.leather})
	# the throwing javelin, cocked overhead then hurled forward
	var raise := (atkFrac - 0.7) / 0.3 if atkFrac > 0.7 else 0.0
	var back := 0.7 + raise * 0.7
	ctx.strokeStyle = grad(ctx, -fx * r * back, -fy * r * back, fx * r * 1.9, fy * r * 1.9, PAL.woodLight, PAL.woodDark)
	ctx.lineWidth = r * 0.08
	ctx.beginPath()
	ctx.moveTo(-fx * r * back, -fy * r * back - raise * 4)
	ctx.lineTo(fx * r * 1.9, fy * r * 1.9 - raise * 4)
	ctx.stroke()
	ctx.fillStyle = PAL.steel
	ctx.beginPath()
	ctx.arc(fx * r * 1.9, fy * r * 1.9 - raise * 4, r * 0.12, 0, PI * 2)
	ctx.fill()


# Hand Cannoneer: a long iron barrel on a wooden stock, with a muzzle flash and
# drifting smoke when it fires — gunpowder, not the Crossbow's steel prod.
static func drawHandcannon(ctx: Ctx, e: Entity, tc: Dictionary, atkFrac: float) -> void:
	var PAL: Dictionary = C.PAL
	var r := e.radius
	body(ctx, e, tc.main, tc.dark)
	head(ctx, r, {"helm": "open", "tone": PAL.steelDark})
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	# wooden stock
	ctx.strokeStyle = grad(ctx, -fx * r * 0.6, -fy * r * 0.6, fx * r * 0.4, fy * r * 0.4, PAL.woodLight, PAL.woodDark)
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.12
	ctx.beginPath()
	ctx.moveTo(-fx * r * 0.6, -fy * r * 0.6)
	ctx.lineTo(fx * r * 0.4, fy * r * 0.4)
	ctx.stroke()
	# iron barrel
	ctx.strokeStyle = grad(ctx, 0, 0, fx * r * 1.7, fy * r * 1.7, "#54585e", "#2a2d31")
	ctx.lineWidth = r * 0.1
	ctx.beginPath()
	ctx.moveTo(-fx * r * 0.3, -fy * r * 0.3)
	ctx.lineTo(fx * r * 1.7, fy * r * 1.7)
	ctx.stroke()
	var mx := fx * r * 1.7
	var my := fy * r * 1.7
	if atkFrac > 0.82:
		var k := (atkFrac - 0.82) / 0.18
		ctx.fillStyle = Palette.withAlpha("#fff0b0", k)
		ctx.beginPath()
		ctx.arc(mx, my, r * 0.5 * k + 1.5, 0, PI * 2)
		ctx.fill()
		ctx.fillStyle = Palette.withAlpha("#ffae3a", k * 0.8)
		ctx.beginPath()
		ctx.arc(mx + fx * 4, my + fy * 4, r * 0.32 * k, 0, PI * 2)
		ctx.fill()
	elif atkFrac > 0.4:
		ctx.fillStyle = Palette.withAlpha("#cfcabd", 0.35 * (atkFrac - 0.4))
		ctx.beginPath()
		ctx.arc(mx + fx * 5, my + fy * 5, r * 0.4, 0, PI * 2)
		ctx.fill()


# King: a robed, bearded monarch under a big jewelled crown, bearing an orbed
# sceptre rather than a war blade — clearly royalty, not the Champion hero.
static func drawKing(ctx: Ctx, e: Entity, tc: Dictionary, lunge: float, time: float) -> void:
	var r := e.radius
	# long royal robe behind
	ctx.fillStyle = grad(ctx, 0, -r * 0.2, 0, r * 1.2, Palette.shade(tc.main, -0.05), Palette.shade(tc.dark, -0.1))
	ctx.beginPath()
	ctx.moveTo(-r * 0.6, -r * 0.1)
	ctx.quadraticCurveTo(-r * 1.2, r * 0.9 + sin(time * 3) * 1.5, 0, r * 1.2)
	ctx.quadraticCurveTo(r * 1.2, r * 0.9 - sin(time * 3) * 1.5, r * 0.6, -r * 0.1)
	ctx.fill()
	body(ctx, e, tc.main, tc.dark)
	# ermine collar + gold medallion
	ctx.fillStyle = "#f0ece0"
	ctx.beginPath()
	ctx.arc(0, r * 0.2, r * 0.5, 0.2, PI - 0.2)
	ctx.fill()
	ctx.fillStyle = "#ffd24a"
	ctx.beginPath()
	ctx.arc(0, 0, r * 0.2, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1.2)
	head(ctx, r, {"helm": "bare", "hair": "#d8d2c4"})
	# grey royal beard
	ctx.fillStyle = "#e2ddd0"
	ctx.beginPath()
	ctx.arc(0, -r * 0.66, r * 0.22, 0.15, PI - 0.15)
	ctx.fill()
	# big jewelled crown
	ctx.fillStyle = grad(ctx, 0, -r * 1.1, 0, -r * 0.78, "#ffe488", "#e0a830")
	ctx.fillRect(-r * 0.5, -r * 1.06, r * 1.0, r * 0.18)
	for i in range(-2, 3):
		ctx.beginPath()
		ctx.moveTo(i * r * 0.24 - r * 0.1, -r * 1.06)
		ctx.lineTo(i * r * 0.24, -r * 1.36)
		ctx.lineTo(i * r * 0.24 + r * 0.1, -r * 1.06)
		ctx.fill()
	ctx.fillStyle = "#d8403a"  # jewels
	for i in range(-1, 2):
		ctx.beginPath()
		ctx.arc(i * r * 0.3, -r * 0.97, r * 0.06, 0, PI * 2)
		ctx.fill()
	# orbed golden sceptre
	var f := weaponAngleParts(e.facing)
	var fx := f.x
	var fy := f.y
	ctx.strokeStyle = grad(ctx, fx * r * 0.3, fy * r * 0.3, fx * r * 1.2, fy * r * 1.2, "#ffe488", "#c8901e")
	ctx.lineCap = "round"
	ctx.lineWidth = r * 0.1
	ctx.beginPath()
	ctx.moveTo(fx * r * 0.3, fy * r * 0.3)
	ctx.lineTo(fx * r * (1.1 + lunge * 0.4), fy * r * (1.1 + lunge * 0.4))
	ctx.stroke()
	ctx.fillStyle = "#ffe07a"
	ctx.beginPath()
	ctx.arc(fx * r * (1.2 + lunge * 0.4), fy * r * (1.2 + lunge * 0.4), r * 0.16, 0, PI * 2)
	ctx.fill()
	softOutline(ctx, 1)


# ----------------------------------------------------------------- projectiles --

static func drawProjectile(ctx: Ctx, e: Entity) -> void:
	var t := minf(1, e.projElapsed / e.projDuration)
	# Ballistic arc height (visual only).
	var arc := sin(t * PI) * (38 if e.type == "rock" else 14)
	var x := e.x
	var y := e.y - arc
	if e.type == "rock":
		shadow(ctx, e.x, e.y + 3, 5, 2.4, 0.3)
		ctx.fillStyle = "#6e675c"
		ctx.beginPath()
		ctx.arc(x, y, 4.6, 0, PI * 2)
		ctx.fill()
		ctx.fillStyle = "#867f72"
		ctx.beginPath()
		ctx.arc(x - 1.2, y - 1.2, 2.2, 0, PI * 2)
		ctx.fill()
	else:
		shadow(ctx, e.x, e.y + 2, 3, 1.2, 0.22)
		ctx.strokeStyle = "#d9cfb4"
		ctx.lineWidth = 1.6
		ctx.beginPath()
		ctx.moveTo(x - cos(e.facing) * 6, y - sin(e.facing) * 6)
		ctx.lineTo(x + cos(e.facing) * 6, y + sin(e.facing) * 6)
		ctx.stroke()
		ctx.fillStyle = C.PAL.steel
		ctx.beginPath()
		ctx.arc(x + cos(e.facing) * 6, y + sin(e.facing) * 6, 1.6, 0, PI * 2)
		ctx.fill()


# -------------------------------------------------------------- decorations --

static func drawSelectionRing(ctx: Ctx, e: Entity, isPlayer: bool) -> void:
	var col := "#7df27d" if isPlayer else "#f2e87d"
	if e.kind == T.Kind.Building:
		var half := e.radius + 4
		ctx.strokeStyle = Palette.withAlpha(col, 0.9)
		ctx.lineWidth = 2
		var c := 9
		for pp in [[-1, -1], [1, -1], [-1, 1], [1, 1]]:
			var px: int = pp[0]
			var py: int = pp[1]
			ctx.beginPath()
			ctx.moveTo(e.x + px * half - px * c, e.y + py * half)
			ctx.lineTo(e.x + px * half, e.y + py * half)
			ctx.lineTo(e.x + px * half, e.y + py * half - py * c)
			ctx.stroke()
	else:
		ctx.strokeStyle = Palette.withAlpha(col, 0.9)
		ctx.lineWidth = 1.8
		ctx.beginPath()
		ctx.ellipse(e.x, e.y + e.radius * 0.45, e.radius + 3.5, (e.radius + 3.5) * 0.5, 0, 0, PI * 2)
		ctx.stroke()


static func drawHealthBar(ctx: Ctx, e: Entity) -> void:
	var PAL: Dictionary = C.PAL
	var frac := maxf(0, e.hp / e.maxHp)
	var w := maxf(30, e.radius * 1.4) if e.kind == T.Kind.Building else 20.0
	var x := e.x - w / 2
	var y := e.y - e.radius - (e.radius * 0.6 + 8 if e.kind == T.Kind.Building else 14.0)
	ctx.fillStyle = "rgba(10, 10, 8, 0.7)"
	ctx.fillRect(x - 1, y - 1, w + 2, 5)
	ctx.fillStyle = PAL.uiGood if frac > 0.6 else ("#e8c33a" if frac > 0.3 else PAL.uiBad)
	ctx.fillRect(x, y, w * frac, 3)
