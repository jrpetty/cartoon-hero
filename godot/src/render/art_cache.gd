## The art cache: procedural art drawn once into texture atlases, then blitted.
##
## Drawing every unit's figure path by path every frame costs about a
## millisecond each in GDScript — a 300-unit battle would be unplayable. But
## the art only depends on a handful of inputs (type, faction, team colour,
## facing, gait, attack phase, a few time-driven wobbles), so each distinct
## pose is drawn once, through the very same Draw functions, into a cell of a
## texture atlas, and blitted from then on. What changes every frame and costs
## little — auras, chevrons, health bars, rarity rings, hit flashes, the bob
## and lunge offsets — stays live.
##
## Units are cached by pose (shared between all units in that pose); buildings
## and resources per instance (their art has position-dependent detail and
## they don't move), with their animations (banners, sails, forge glow,
## watchfire) as phase-exact frames.
##
## Mechanics:
## - Atlas pages are RenderingServer viewports (no scene nodes needed), each
##   with its own canvas and Ctx. A miss draws into the page's canvas and marks
##   the page to render once; pages are parented to the main viewport so they
##   render first, in the same frame, and keep their pixels between updates
##   (clear mode never). When the pages are full the oldest one not used this
##   frame is cleared and reused.
## - A cheap measure-only pass sizes each cell to the pose's actual bounds.
## - Cells are rendered at a level of detail (√2 steps) just above the current
##   device scale, so zooming in stays crisp and zooming out isn't aliased.
## - Pages hold premultiplied alpha (what a transparent 2D viewport renders),
##   so blits use Ctx.blit_premul.
## - At most MAX_RENDERS_PER_FRAME misses are drawn per frame; past that the
##   art is drawn procedurally for the frame (correct, just slower).
class_name ArtCache

static var enabled := true
## No per-frame drawing budget (contact sheets, tools): every miss is drawn.
static var unlimited := false

const PAGE_SIZE := 2048
const MAX_PAGES := 6
const MAX_RENDERS_PER_FRAME := 24
const LEVELS := [0.5, 0.70710678, 1.0, 1.41421356, 2.0, 2.82842712]
const PAD := 2
const FACING_BUCKETS := 32
## Gallop frequency of each mounted unit's horse (draw.ts horse() opts).
const HORSE_HZ := {"knight": 11.0, "horseman": 12.0, "raider": 13.0, "scout": 15.0, "lancer": 13.0, "horse_archer": 12.0, "cataphract": 10.0}


class Page extends RefCounted:
	var vp := RID()
	var canvas := RID()
	var item := RID()
	var tex := RID()
	var ctx := Ctx.new()
	var shelves: Array = []  ## [y, h, x]
	var bottom := 0
	var frame := -1  ## last frame something was drawn into it
	var used := -1  ## last frame something was blitted from it
	var keys: Array = []


class Cell extends RefCounted:
	var page: Page
	var src := Rect2()
	## Where the art's origin sits inside the cell, in cell pixels.
	var origin := Vector2.ZERO
	var level := 1.0


static var _pages: Array = []
static var _cells := {}
static var _frame := -1
static var _renders := 0
static var _rendering := false
static var _measure: Ctx = null
static var hits := 0
static var misses := 0
static var fallbacks := 0
static var stand_ins := 0
## Base keys (shadow + ring) live above every pose key.
const BASE_KEY := 1152921504606846976  # 2^60


static func stats() -> Dictionary:
	return {"hits": hits, "misses": misses, "stand_ins": stand_ins, "fallbacks": fallbacks, "cells": _cells.size(), "pages": _pages.size()}


## Drop everything (after a palette change, say).
static func clear() -> void:
	for p in _pages:
		_free_page(p)
	_pages.clear()
	_cells.clear()


static func _tick() -> void:
	var f := Engine.get_process_frames()
	if f != _frame:
		_frame = f
		_renders = 0


## Past this frame's allowance of new drawings (plus `extra`)?
static func _over_budget(extra: int = 0) -> bool:
	return not unlimited and _renders >= MAX_RENDERS_PER_FRAME + extra


static func _level_for(s: float) -> float:
	var i := _level_index(s)
	return LEVELS[i] if i >= 0 else -1.0


static func _level_index(s: float) -> int:
	for i in LEVELS.size():
		if LEVELS[i] >= s * 0.98:
			return i
	return -1


# ------------------------------------------------------------------ units --

static func drawUnitBody(ctx: Ctx, e: Entity, tc: Dictionary, time: float, ox: float, oy: float, moving: bool, atkFrac: float, lunge: float) -> bool:
	if _rendering or ctx.measure_only:
		return false
	var li := _level_index(ctx.device_scale())
	if li < 0:
		return false
	var lv: float = LEVELS[li]
	_tick()
	var fb := posmod(roundi(e.facing / TAU * FACING_BUCKETS), FACING_BUCKETS)
	var q := _pose_q(e, time, moving, atkFrac)
	var key := _unit_key(e, tc, q, fb, li, moving)
	var cell: Cell = _cells.get(key)
	if cell == null:
		var neutral := false
		if _over_budget():
			# Over budget this frame: the same figure in its neutral pose stands
			# in until there's time to draw this one (drawn now, from a small
			# extra allowance, if it isn't cached yet).
			q = NEUTRAL
			moving = false
			key = _unit_key(e, tc, q, fb, li, false)
			cell = _cells.get(key)
			neutral = true
			if cell == null and _over_budget(STAND_IN_RENDERS):
				# A burst of brand-new figures (a big army scrolling into view):
				# for a frame or two the rest show as their level-of-detail
				# blob rather than stall the frame drawing them all at once.
				fallbacks += 1
				var def: Dictionary = C.UNITS.get(e.type, {})
				return drawUnitLod(ctx, e, tc, def.get("hero", false), true)
		if cell == null:
			cell = _render_pose(key, e, tc, time, moving, q, fb, lv)
			if cell == null:
				return false
		if neutral:
			stand_ins += 1
	else:
		hits += 1
	_blit(ctx, cell, Vector2(ox, oy))
	return true


## Draw a pose at its bucket's centre: the facing, gait, gallop and attack
## phase every unit sharing the key will be shown with.
static func _render_pose(key: int, e: Entity, tc: Dictionary, time: float, moving: bool, q: Vector4i, fb: int, lv: float) -> Cell:
	var keep_facing := e.facing
	var keep_cd := e.attackCooldown
	e.facing = fb * TAU / FACING_BUCKETS
	var atk := _attack_centre(e.type, q.z)
	e.attackCooldown = atk * e.attackInterval
	var lunge := (atk - 0.82) / 0.18 if atk > 0.82 else 0.0
	Draw.gGaitOverride = _step_centre(q.x)
	Draw.gGallopOverride = q.y / 4.0
	var draw_it := func(c: Ctx) -> void: Draw.drawUnitBody(c, e, tc, time, moving, atk, lunge)
	var cell := _render(key, lv, Vector2.ZERO, draw_it)
	Draw.gGaitOverride = null
	Draw.gGallopOverride = null
	e.facing = keep_facing
	e.attackCooldown = keep_cd
	return cell


## A pose's buckets: x = gait step, y = gallop, z = attack phase, w = time
## wobble. Walking: the step in nine levels. Standing: the gentle idle sway in
## three — or none while attacking in place, where the strike is what shows.
static func _pose_q(e: Entity, time: float, moving: bool, atkFrac: float) -> Vector4i:
	var qa := _attack_q(e.type, atkFrac)
	var qs := 0
	if NO_GAIT.has(e.type):
		qs = IDLE_STEP
	elif moving:
		qs = roundi(Draw.gait(e) * 4.0)
	elif qa == 0:
		qs = IDLE_STEP + roundi(Draw.gait(e) / 0.2)
	else:
		qs = IDLE_STEP
	var qg := 0
	if moving and HORSE_HZ.has(e.type):
		qg = roundi(sin(e.animPhase * HORSE_HZ[e.type]) * 4.0)
	return Vector4i(qs, qg, qa, _time_q(e, time))


## Step codes: -4..4 walking (step = code / 4); IDLE_STEP + (-1..1) standing
## (step = ±0.15 or 0 — the idle sway is 0.2 · sin).
const IDLE_STEP := 6
const NEUTRAL := Vector4i(IDLE_STEP, 0, 0, 0)
## Figures that never draw walking legs (riders, engines, the cart).
const NO_GAIT := {
	"knight": true, "horseman": true, "raider": true, "scout": true, "lancer": true, "horse_archer": true,
	"skimmer": true, "catapult": true, "trebuchet": true, "ram": true, "bombard": true, "great_bombard": true,
	"trade_cart": true,
}
const STAND_IN_RENDERS := 16


static func _step_centre(code: int) -> float:
	if code >= IDLE_STEP - 1:
		return (code - IDLE_STEP) * 0.15
	return code / 4.0


static func _attack_centre(type: String, qa: int) -> float:
	if qa == 0:
		return 0.0
	var lo: float = ATTACK_FROM.get(type, 0.55)
	return lo + (qa - 0.5) / 16.0 * (1.0 - lo)


## A pose as one integer: type, faction, team colour, radius, facing bucket,
## gait step, gallop, attack phase, time wobble, cargo, charge, beard, level.
static func _unit_key(e: Entity, tc: Dictionary, q: Vector4i, fb: int, li: int, moving: bool) -> int:
	var qs := q.x
	var qg := q.y
	var qa := q.z
	var qt := q.w
	var carry := 0
	if e.type == "villager" and e.carry > 2 and e.carryKind != null:
		carry = CARRY_IX.get(e.carryKind, 0)
	var charged := 1 if e.type == "lancer" and e.chargeRun >= C.CHARGE_RUN else 0
	var idq := e.id % 4 if Draw.gLook.helm == "nasal" else 0
	var k := _index(_type_ix, e.type) & 63
	k = k * 8 + (_index(_faction_ix, Draw.gFaction) & 7)
	k = k * 128 + (_index(_tc_ix, str(tc.main) + str(tc.light)) & 127)
	k = k * 128 + (roundi(e.radius * 4.0) & 127)
	k = k * 32 + fb
	k = k * 16 + (qs + 8)
	k = k * 16 + (qg + 8)
	k = k * 32 + qa
	k = k * 8 + qt
	k = k * 4 + carry
	k = k * 2 + charged
	k = k * 4 + idq
	k = k * 2 + (1 if moving else 0)
	return k * 8 + li


const CARRY_IX := {"wood": 1, "gold": 2, "food": 3}
static var _type_ix := {}
static var _faction_ix := {}
static var _tc_ix := {}


static func _index(d: Dictionary, k) -> int:
	var i = d.get(k)
	if i == null:
		i = d.size()
		d[k] = i
	return i


## Where in its attack cooldown a unit's art starts to move (draw.ts: melee
## swings from 0.55, bows draw from 0.7, the crossbow's bolt shows from 0.6,
## the hand cannon smokes from 0.4, guns recoil from 0.85, the ram swings from
## 0.8; the longbow, siege engines and the battlemage move through it all).
## Below that every phase looks the same, so it's one bucket; above it, 16.
const ATTACK_FROM := {
	"archer": 0.7, "ranger": 0.7, "horseman": 0.7, "horse_archer": 0.7, "javelin": 0.7, "skirmisher": 0.7,
	"longbow": 0.0, "yeoman": 0.0, "catapult": 0.0, "trebuchet": 0.0, "battlemage": 0.0,
	"crossbow": 0.6, "handcannon": 0.4, "bombard": 0.85, "great_bombard": 0.85, "pulse_trooper": 0.85,
	"ram": 0.8, "monk": 2.0, "trade_cart": 2.0,
}


static func _attack_q(type: String, atkFrac: float) -> int:
	var lo: float = ATTACK_FROM.get(type, 0.55)
	if atkFrac <= lo:
		return 0
	return 1 + mini(15, floori((atkFrac - lo) / (1.0 - lo) * 16.0))


## The time-driven wobbles some figures have (draw.ts), as a phase bucket.
static func _time_q(e: Entity, time: float) -> int:
	var phase := 0.0
	match e.type:
		"hero":
			phase = time * 4
		"king":
			phase = time * 3
		"monk", "battlemage", "skimmer":
			phase = time * 4 + e.id
		"pulse_trooper":
			phase = time * 6 + e.id
		_:
			if Draw.gLook.helm == "visor" and HORSE_HZ.has(e.type):
				phase = (time + e.id) * 4  # the Ascendancy's sleds bob
			else:
				return 0
	return roundi(sin(phase) * 3.0) + 4


## The shadow and team ring under a unit.
static func drawUnitBase(ctx: Ctx, e: Entity, tc: Dictionary) -> bool:
	if _rendering or ctx.measure_only:
		return false
	var li := _level_index(ctx.device_scale())
	if li < 0:
		return false
	var lv: float = LEVELS[li]
	_tick()
	var key := BASE_KEY + roundi(e.radius * 4.0) * 1048576 + _index(_tc_ix, str(tc.main) + str(tc.light)) * 256 + li
	var cell: Cell = _cells.get(key)
	if cell == null:
		if _over_budget():
			fallbacks += 1
			return false
		var r := e.radius
		var draw_it := func(c: Ctx) -> void:
			Draw.shadow(c, 1, r * 0.55, r * 0.95, r * 0.42)
			Draw.teamRing(c, 0, r * 0.55, r, tc)
		cell = _render(key, lv, Vector2.ZERO, draw_it)
		if cell == null:
			return false
	else:
		hits += 1
	_blit(ctx, cell, Vector2(e.x, e.y))
	return true


## The level-of-detail blob (drawn whole: it has no live parts). `urgent`:
## draw it even over the frame's budget (it's tiny, and there's one per
## colour and size).
static func drawUnitLod(ctx: Ctx, e: Entity, tc: Dictionary, hero: bool, urgent: bool = false) -> bool:
	if _rendering or ctx.measure_only:
		return false
	var lv := _level_for(ctx.device_scale())
	if lv < 0.0:
		return false
	_tick()
	var key := "l|%s|%s|%d|%s" % [e.radius, tc.main, 1 if hero else 0, lv]
	var cell: Cell = _cells.get(key)
	if cell == null:
		if _over_budget() and not urgent:
			fallbacks += 1
			return false
		var r := e.radius
		var draw_it := func(c: Ctx) -> void:
			c.fillStyle = Palette.withAlpha("#0a0805", 0.28)
			c.beginPath()
			c.ellipse(0, r * 0.55, r * 0.9, r * 0.4, 0, 0, PI * 2)
			c.fill()
			c.fillStyle = tc.main
			c.beginPath()
			c.ellipse(0, 0, r * 0.92, r * 0.72, 0, 0, PI * 2)
			c.fill()
			if hero:
				c.fillStyle = "#ffd24a"; c.beginPath(); c.arc(0, -r, 2.2, 0, PI * 2); c.fill()
		cell = _render(key, lv, Vector2.ZERO, draw_it)
		if cell == null:
			return false
	else:
		hits += 1
	_blit(ctx, cell, Vector2(e.x, e.y))
	return true


## Draw the standing pose of these unit types for these teams, every facing,
## ahead of time — for a loading screen, so the first look at an army doesn't
## have to draw it. `device_scale` is the camera zoom the game will start at.
## Uses whatever faction / colour resolvers Draw has been given.
static func prewarmUnits(types: Array, teams: Array, device_scale: float = 1.0) -> int:
	var li := _level_index(device_scale)
	if li < 0:
		return 0
	var lv: float = LEVELS[li]
	var n := 0
	var e := Entity.new()
	for team in teams:
		for type in types:
			if not C.UNITS.has(type):
				continue
			e.team = team
			e.type = type
			e.radius = float(C.UNITS[type].radius)
			e.id = 1
			Draw.useLook(team, type, e.id, 0.0)
			var tc := Draw.tcol(team)
			for fb in FACING_BUCKETS:
				var key := _unit_key(e, tc, NEUTRAL, fb, li, false)
				if _cells.has(key):
					continue
				_tick()
				if _render_pose(key, e, tc, 0.0, false, NEUTRAL, fb, lv) != null:
					n += 1
	return n


# -------------------------------------------------------------- buildings --

static func drawBuildingArt(ctx: Ctx, e: Entity, half: float, tc: Dictionary, time: float) -> bool:
	if _rendering or ctx.measure_only:
		return false
	var lv := _level_for(ctx.device_scale())
	if lv < 0.0:
		return false
	_tick()
	var night := 1 if e.type == "watchfire" and Draw.isNight(Draw.gDayPhase) else 0
	var key := "B|%d|%s|%s|%s|%s|%s|%d|%d|%d|%s" % [
		e.id, e.type, Draw.gFaction, tc.main, tc.light, half, 1 if e.gateOpen else 0,
		_building_anim(e, time), night, lv]
	var cell: Cell = _cells.get(key)
	if cell == null:
		if _over_budget():
			fallbacks += 1
			return false
		var draw_it := func(c: Ctx) -> void: Draw.drawBuildingArt(c, e, half, tc, time)
		cell = _render(key, lv, Vector2(e.x, e.y), draw_it)
		if cell == null:
			return false
	else:
		hits += 1
	_blit(ctx, cell, Vector2(e.x, e.y))
	return true


## Animation frame of a building's moving part, phase-exact (draw.ts).
static func _building_anim(e: Entity, time: float) -> int:
	match e.type:
		"town_center", "castle":  # the standard waves: sin(time * 3 + id)
			return posmod(floori((time * 3 + e.id) / TAU * 16), 16)
		"mill":  # four sails turning at time * 0.9: a quarter turn repeats
			return posmod(floori(time * 0.9 / (PI / 2) * 12), 12)
		"blacksmith":  # forge glow: sin(time * 5 + id)
			return posmod(floori((time * 5 + e.id) / TAU * 12), 12)
		"watchfire":  # flicker level × flame sway phase
			var flick := 0.78 + sin(time * 11 + e.x) * 0.12 + sin(time * 17 + e.y) * 0.1
			return roundi(flick * 20) * 16 + posmod(floori(fposmod(time * 9, TAU) / TAU * 12), 12)
		_:
			return 0


static func drawConstruction(ctx: Ctx, e: Entity, half: float, tc: Dictionary, time: float) -> bool:
	if _rendering or ctx.measure_only:
		return false
	var lv := _level_for(ctx.device_scale())
	if lv < 0.0:
		return false
	_tick()
	var qp := roundi(clampf(e.buildProgress, 0.0, 1.0) * 32.0)
	var key := "C|%d|%s|%s|%s|%s|%d|%d|%d|%s" % [
		e.id, e.type, Draw.gFaction, tc.main, half, e.buildState, qp, _building_anim(e, time), lv]
	var cell: Cell = _cells.get(key)
	if cell == null:
		if _over_budget():
			fallbacks += 1
			return false
		var keep := e.buildProgress
		e.buildProgress = qp / 32.0
		var draw_it := func(c: Ctx) -> void: Draw.drawConstruction(c, e, half, tc, time)
		cell = _render(key, lv, Vector2(e.x, e.y), draw_it)
		e.buildProgress = keep
		if cell == null:
			return false
	else:
		hits += 1
	_blit(ctx, cell, Vector2(e.x, e.y))
	return true


# -------------------------------------------------------------- resources --

static func drawResource(ctx: Ctx, e: Entity, time: float) -> bool:
	if _rendering or ctx.measure_only:
		return false
	var lv := _level_for(ctx.device_scale())
	if lv < 0.0:
		return false
	_tick()
	var frac := maxf(0.25, minf(1, e.amount / 125))
	var sway := roundi(sin(time * 0.8 + e.id) * 0.8 * 1.5) if e.type == "tree" else 0
	var key := "R|%d|%s|%d|%d|%s" % [e.id, e.type, roundi(frac * 20.0), sway, lv]
	var cell: Cell = _cells.get(key)
	if cell == null:
		if _over_budget():
			fallbacks += 1
			return false
		var draw_it := func(c: Ctx) -> void: Draw.drawResource(c, e, time)
		cell = _render(key, lv, Vector2(e.x, e.y), draw_it)
		if cell == null:
			return false
	else:
		hits += 1
	_blit(ctx, cell, Vector2(e.x, e.y))
	return true


# ------------------------------------------------------------- machinery --

## Measure, place, and draw one cell. `origin` is the world point the art is
## drawn around (0,0 for figures drawn in local space, the entity's position
## for buildings and resources, whose art uses world coordinates).
static func _render(key, lv: float, origin: Vector2, draw_it: Callable) -> Cell:
	_rendering = true
	# 1. How big is it? (device pixels at this level, relative to the origin)
	if _measure == null:
		_measure = Ctx.new()
		_measure.measure_only = true
	var m := _measure
	m.begin(RID())
	m.bounds_valid = false
	m.scale(lv, lv)
	m.translate(-origin.x, -origin.y)
	draw_it.call(m)
	if not m.bounds_valid:
		_rendering = false
		return null
	var b := m.bounds
	var x0 := floori(b.position.x) - PAD
	var y0 := floori(b.position.y) - PAD
	var w := ceili(b.end.x) + PAD - x0
	var h := ceili(b.end.y) + PAD - y0
	if w > PAGE_SIZE or h > PAGE_SIZE:
		_rendering = false
		return null
	# 2. Where does it go?
	var place := _allocate(w, h)
	if place.is_empty():
		_rendering = false
		return null
	var page: Page = place[0]
	var at: Vector2i = place[1]
	# 3. Draw it there, clipped to its cell.
	if page.frame != _frame:
		page.ctx.begin(page.item, PAGE_SIZE, PAGE_SIZE)
		page.frame = _frame
		RenderingServer.viewport_set_update_mode(page.vp, RenderingServer.VIEWPORT_UPDATE_ONCE)
	var pc := page.ctx
	pc.save()
	pc.beginPath()
	pc.rect(at.x, at.y, w, h)
	pc.clip()
	pc.translate(at.x - x0, at.y - y0)
	pc.scale(lv, lv)
	pc.translate(-origin.x, -origin.y)
	draw_it.call(pc)
	pc.restore()
	pc.beginPath()
	_rendering = false
	_renders += 1
	misses += 1
	var cell := Cell.new()
	cell.page = page
	cell.src = Rect2(at.x, at.y, w, h)
	cell.origin = Vector2(-x0, -y0)
	cell.level = lv
	_cells[key] = cell
	page.keys.append(key)
	return cell


static func _blit(ctx: Ctx, cell: Cell, at: Vector2) -> void:
	cell.page.used = _frame
	ctx.blit_cell(cell.page.tex, cell.src, at, cell.origin, cell.level)


## Shelf packing across pages; reuses the oldest page when all are full.
static func _allocate(w: int, h: int) -> Array:
	for p in _pages:
		var at := _place(p, w, h)
		if at.x >= 0:
			return [p, at]
	if _pages.size() < MAX_PAGES:
		var p := _new_page()
		if p == null:
			return []
		_pages.append(p)
		return [p, _place(p, w, h)]
	# Full: clear the oldest page nobody has drawn from this frame.
	for i in _pages.size():
		var p: Page = _pages[i]
		if p.used == _frame or p.frame == _frame:
			continue
		for k in p.keys:
			_cells.erase(k)
		p.keys.clear()
		p.shelves.clear()
		p.bottom = 0
		RenderingServer.viewport_set_clear_mode(p.vp, RenderingServer.VIEWPORT_CLEAR_ONLY_NEXT_FRAME)
		_pages.remove_at(i)
		_pages.append(p)
		return [p, _place(p, w, h)]
	return []


static func _place(p: Page, w: int, h: int) -> Vector2i:
	for s in p.shelves:
		if h <= s[1] and h * 1.5 >= s[1] and s[2] + w <= PAGE_SIZE:
			var at := Vector2i(s[2], s[0])
			s[2] += w + 1
			return at
	if p.bottom + h > PAGE_SIZE:
		return Vector2i(-1, -1)
	p.shelves.append([p.bottom, h, w + 1])
	var at2 := Vector2i(0, p.bottom)
	p.bottom += h + 1
	return at2


static func _new_page() -> Page:
	var tree := Engine.get_main_loop() as SceneTree
	if tree == null:
		return null
	var p := Page.new()
	p.vp = RenderingServer.viewport_create()
	RenderingServer.viewport_set_size(p.vp, PAGE_SIZE, PAGE_SIZE)
	RenderingServer.viewport_set_transparent_background(p.vp, true)
	RenderingServer.viewport_set_clear_mode(p.vp, RenderingServer.VIEWPORT_CLEAR_ONLY_NEXT_FRAME)
	RenderingServer.viewport_set_update_mode(p.vp, RenderingServer.VIEWPORT_UPDATE_ONCE)
	RenderingServer.viewport_set_parent_viewport(p.vp, tree.root.get_viewport_rid())
	RenderingServer.viewport_set_active(p.vp, true)
	p.canvas = RenderingServer.canvas_create()
	RenderingServer.viewport_attach_canvas(p.vp, p.canvas)
	p.item = RenderingServer.canvas_item_create()
	RenderingServer.canvas_item_set_parent(p.item, p.canvas)
	p.tex = RenderingServer.viewport_get_texture(p.vp)
	return p


static func _free_page(p: Page) -> void:
	p.ctx = null
	RenderingServer.free_rid(p.item)
	RenderingServer.free_rid(p.canvas)
	RenderingServer.free_rid(p.vp)
