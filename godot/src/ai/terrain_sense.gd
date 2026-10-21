## ai/terrain_sense.ts — what the AI understands about the ground it is
## fighting on.
##
## The map gained mountains, lakes, hills and woods, and for a while only the
## player could read any of it: a hill was worth 20% more range and sight to
## whoever stood on it, and the AI would happily meet an attack in a marsh while
## an empty ridge sat forty metres away. Geography one side understands is worse
## than no geography — it reads as the game cheating in the player's favour.
##
## This is deliberately a small, cheap advisor rather than a planner. It answers
## one question — "of the ground near here, which patch would I rather be
## standing on?" — and the AI asks it at the few moments where the answer
## changes what it does: staging an attack, meeting a raid, and siting a tower.
##
## Port notes: points are Dictionaries `{x, y}` (no Vector2 in the sim); the
## TS `GroundOpts` is a Dictionary with the optional keys `radius`, `from`
## (a point) and `detour`. The terrain tables are TerrainKinds' (Float32 sight
## and speed, read back float32-rounded exactly as the TS Float32Arrays are).
class_name TerrainSense
extends RefCounted

const SHALLOW := TerrainKinds.Terrain.Shallow
const HILL := TerrainKinds.Terrain.Hill


## How much an army wants to be standing on this ground, in arbitrary points
## around zero for open grass.
##
## Sight is weighted about twice as hard as speed because it is the thing that
## decides a fight rather than a chase: a hill both sees the enemy first and
## out-ranges them, while slow ground mostly costs you the option to leave.
static func groundScore(world: World, wx: float, wy: float) -> float:
	var t := world.terrainAt(wx, wy)
	if TerrainKinds.TERRAIN_BLOCKS[t] != 0:
		return -INF
	var sight: float = TerrainKinds.TERRAIN_SIGHT[t]
	var speed: float = TerrainKinds.TERRAIN_SPEED[t]
	return (sight - 1.0) * 100.0 - (1.0 - speed) * 60.0

# There is deliberately no `isFightableGround` predicate here. "Don't get caught
# in a wood" is already carried by the score itself — forest comes out at -52
# and marsh at -34 against open grass at 0 — so every advisor below avoids them
# without a separate yes/no test, and a threshold nothing consults is just
# another number to keep in sync.


## `reachable(nx, ny)` from bestGroundNear: true unless the nav grid proves
## (nx, ny) cannot be walked to from `fromCell` ([cx, cy], or null).
static func _reachable(world: World, fromCell, nx: float, ny: float) -> bool:
	if fromCell == null or world.grid == null:
		return true
	var tile: float = world.TILE
	return not world.grid.provablyUnreachable(
		fromCell[0], fromCell[1], floori(nx / tile), floori(ny / tile))


## The best patch of ground within `radius` of (x,y).
##
## Returns the original point when nothing nearby beats it, so a caller can
## always use the result without checking. Sampling is on a coarse lattice —
## a couple of cells apart — because the answer only needs to be the right
## *hill*, not the right cell, and this runs inside the AI's think step.
##
## opts: `radius` (how far from (x,y) to look, world units; 240), `from`
## (somewhere the chosen ground must be reachable from — usually the army; a
## perfect hill on the far side of a lake is not a staging point), `detour`
## (what crossing the whole search radius is worth giving up, in the points
## `groundScore` returns; 10).
##
## `detour` used to be a raw divisor on the distance, which quietly made the
## whole feature inert: a hill is worth 11 points, so a divisor of 10 meant the
## AI would divert at most 110 world units — three tiles — while searching a
## radius of 260. Tying the penalty to the radius keeps the two scales
## commensurate, so `detour` below a hill's 11 points means "cross the whole
## search area for high ground" and above it means "only take what is close".
static func bestGroundNear(world: World, x: float, y: float, opts: Dictionary = {}) -> Dictionary:
	var radius: float = float(opts.get("radius", 240.0))
	var detour: float = float(opts.get("detour", 10.0))
	var tile: float = world.TILE
	var step := tile * 2.0
	var from = opts.get("from")
	var fromCell = [floori(from.x / tile), floori(from.y / tile)] if from != null else null

	var bestX := x
	var bestY := y
	var best := -INF
	# The point we were asked about is a candidate like any other, and gets the
	# same two questions. It used to be seeded as the incumbent unchecked, which
	# meant asking "is this hill a good place to stand?" while standing on the
	# hill always answered yes — even with a mountain range between it and the army.
	var own := groundScore(world, x, y)
	if is_finite(own) and _reachable(world, fromCell, x, y):
		best = own # distance 0
	var r2 := radius * radius
	var dy := -radius
	while dy <= radius:
		var dx := -radius
		while dx <= radius:
			var d2 := dx * dx + dy * dy
			if d2 > r2:
				dx += step
				continue
			var nx := x + dx
			var ny := y + dy
			if nx < 0.0 or ny < 0.0 or nx >= world.worldW or ny >= world.worldH:
				dx += step
				continue
			var s := groundScore(world, nx, ny) - (sqrt(d2) / radius) * detour
			if s <= best:
				dx += step
				continue
			# Only pay for the reachability check on ground that already won.
			if not _reachable(world, fromCell, nx, ny):
				dx += step
				continue
			best = s
			bestX = nx
			bestY = ny
			dx += step
		dy += step
	# Nothing in range survived both questions — the whole neighbourhood was lake
	# and mountain, or it was all walled off from the army. Widen the search until
	# we find real ground rather than handing back a point inside a wall, which
	# would become a move order the pathfinder has to quietly rescue.
	if not is_finite(best):
		return _nearestStandableGround(world, x, y, fromCell)
	return {"x": bestX, "y": bestY}


## An expanding ring search for any cell an army could actually stand on.
##
## This reads the terrain array rather than only the nav grid on purpose. The two
## normally agree, but the advisor's whole job is answering questions about
## terrain, and a fallback that trusts a different source than the thing it is
## falling back from can hand a lake back as dry land.
static func _nearestStandableGround(world: World, x: float, y: float, fromCell) -> Dictionary:
	var tile: float = world.TILE
	var cx := floori(x / tile)
	var cy := floori(y / tile)
	# Two passes, both bounded. The first wants ground the army can walk to; the
	# second settles for ground that exists. Without the second pass, an army
	# sealed into its own pocket of the map would sweep every cell on every think
	# step and still come back empty — and the bound keeps that sweep off the
	# frame budget either way.
	var MAX_RINGS := 32
	for needReach in [true, false]:
		for r in range(1, MAX_RINGS + 1):
			for dy in range(-r, r + 1):
				for dx in range(-r, r + 1):
					if absi(dx) != r and absi(dy) != r:
						continue # ring only
					var px := cx + dx
					var py := cy + dy
					if px < 0 or py < 0 or px >= world.terrainCols or py >= world.terrainRows:
						continue
					var wx := px * tile + tile / 2.0
					var wy := py * tile + tile / 2.0
					if not is_finite(groundScore(world, wx, wy)):
						continue
					if world.grid != null and world.grid.isBlocked(px, py):
						continue
					if needReach and not _reachable(world, fromCell, wx, wy):
						continue
					return {"x": wx, "y": wy}
	return {"x": x, "y": y}


## A place to gather before hitting a target: good ground, on our side of the
## approach rather than in the enemy's lap.
##
## The staging point sits back along the line from the target toward the army,
## so a wave collects itself and takes the high ground *outside* the base
## instead of trickling in one unit at a time — which is what an army walking
## straight at a Town Centre actually does.
static func stagingPoint(world: World, target: Dictionary, army: Dictionary, standoff: float = 420.0) -> Dictionary:
	var dx: float = army.x - target.x
	var dy: float = army.y - target.y
	var d := JS.hypot(dx, dy)
	if d == 0.0 or is_nan(d):
		d = 1.0
	# Already close enough that staging would mean walking backwards.
	if d < standoff * 1.2:
		return bestGroundNear(world, army.x, army.y, {"radius": 220.0, "from": army, "detour": 8.0})
	var sx: float = target.x + (dx / d) * standoff
	var sy: float = target.y + (dy / d) * standoff
	# detour 8 against a hill's 11: an army forming up will cross the whole search
	# area to gather on high ground, which is the one moment it has time to.
	return bestGroundNear(world, sx, sy, {"radius": 320.0, "from": army, "detour": 8.0})


## Somewhere a flanking force can actually get to. The old flank was a blind
## ±420 offset, which on a map with lakes and ridges regularly sent half the
## army into a mountain to stand around looking at it.
static func flankPoint(world: World, target: Dictionary, army: Dictionary, side: int, reach: float = 420.0) -> Dictionary:
	var dx: float = target.x - army.x
	var dy: float = target.y - army.y
	var d := JS.hypot(dx, dy)
	if d == 0.0 or is_nan(d):
		d = 1.0
	# Perpendicular to the approach, so a flank comes in from the side rather
	# than from a random compass direction.
	var px := (-dy / d) * reach * side
	var py := (dx / d) * reach * side
	# A flank is a position, not a picnic spot: detour 16 keeps it roughly where
	# it belongs and only shifts it for good ground that is on the way anyway.
	return bestGroundNear(world, target.x + px, target.y + py, {"radius": 300.0, "from": army, "detour": 16.0})


## Where to put something that shoots. A tower on a hill is worth 20% more range
## for nothing, which over a whole game is the cheapest defensive upgrade there
## is — and the AI was picking its tower site by arithmetic on a straight line.
## `radius` defaults to 3 * TILE.
static func bestTowerSite(world: World, x: float, y: float, radius: float = -1.0) -> Dictionary:
	var tile: float = world.TILE
	if radius < 0.0:
		radius = 3.0 * tile
	var step := tile
	var bestX := x
	var bestY := y
	var best := -INF
	var dy := -radius
	while dy <= radius:
		var dx := -radius
		while dx <= radius:
			var nx := x + dx
			var ny := y + dy
			if nx < 0.0 or ny < 0.0 or nx >= world.worldW or ny >= world.worldH:
				dx += step
				continue
			var t := world.terrainAt(nx, ny)
			# A tower has to be buildable where it stands; a rise is, a wood is not.
			# Checking this here rather than leaving it to placeBuilding matters
			# because the caller only gets one retry — hand it a site it cannot build
			# on and it silently falls back to the arithmetic answer we replaced.
			if TerrainKinds.TERRAIN_BLOCKS[t] != 0 or TerrainKinds.TERRAIN_BUILDABLE[t] == 0:
				dx += step
				continue
			var sight: float = TerrainKinds.TERRAIN_SIGHT[t]
			var s := (sight - 1.0) * 100.0 - JS.hypot(dx, dy) / 20.0
			if s > best:
				best = s
				bestX = nx
				bestY = ny
			dx += step
		dy += step
	return {"x": bestX, "y": bestY}


## Where the line from A to B wades through shallows, if it does.
##
## Bridges arrived and only the player could see the point of them: the AI would
## march an army through a ford at half speed, every wave, past a crossing it
## could have paid a hundred wood for once. That is the same mistake as not
## knowing hills exist, and worse — a bridge is a *thing the AI can build*, so
## the gap shows up as a base that never uses a whole building.
##
## Walks the straight line rather than the actual path on purpose. The pathfinder
## already routes around walls; shallows are not walls, so the route it picks
## really does go through them, and the straight line is where. Returns the
## middle of the widest run crossed — the middle because a bridge wants both
## banks, the widest because that is the crossing costing the most time.
## `from` / `to` are anything with `x` and `y` (a point or an Entity). Returns
## `{x, y, width}` or null.
static func fordCrossing(world: World, from, to) -> Variant:
	var fx: float = from.x
	var fy: float = from.y
	var tx: float = to.x
	var ty: float = to.y
	var tile: float = world.TILE
	var steps := int(ceil(JS.hypot(tx - fx, ty - fy) / (tile / 2.0)))
	if steps <= 0:
		return null
	var best = null
	var runStart := -1
	for i in range(0, steps + 1):
		var t := float(i) / steps
		var x := fx + (tx - fx) * t
		var y := fy + (ty - fy) * t
		var wading := world.terrainAt(x, y) == SHALLOW and not world.bridgedAt(x, y)
		if wading:
			if runStart < 0:
				runStart = i
		else:
			best = _closeRun(best, runStart, i, steps, fx, fy, tx, ty)
			runStart = -1
	best = _closeRun(best, runStart, steps, steps, fx, fy, tx, ty)
	return best


## fordCrossing's `closeRun`: the widest run so far, given one ending here.
static func _closeRun(best, runStart: int, endStep: int, steps: int, fx: float, fy: float, tx: float, ty: float) -> Variant:
	if runStart < 0:
		return best
	var width := endStep - runStart
	if best == null or width > best.width:
		var mid := float(runStart + endStep) / 2.0 / steps
		return {
			"x": fx + (tx - fx) * mid,
			"y": fy + (ty - fy) * mid,
			"width": width,
		}
	return best


## True when this cell is high ground — used by tests and callouts.
static func isHighGround(world: World, wx: float, wy: float) -> bool:
	return world.terrainAt(wx, wy) == HILL
