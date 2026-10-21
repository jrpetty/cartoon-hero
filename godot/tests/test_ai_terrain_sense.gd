extends TestCase
# ai/terrain_sense.test.ts — what the AI understands about the ground.
#
# The match-length tests run shorter here where the TS build shows the same
# thing already holds (each says so); the advisor tests are ported whole.

const Terrain := TerrainKinds.Terrain


## Counts the move orders sent onto high ground — the TS test swaps
## `world.issueMove` for a wrapper; a subclass override is the GDScript way.
class HillWatchWorld extends World:
	var hills := 0

	func _init(seed: int) -> void:
		super(seed)

	func issueMove(ids: Array, tx: float, ty: float, queue := false, attackMove := false) -> void:
		if TerrainSense.isHighGround(self, tx, ty):
			hills += 1
		super.issueMove(ids, tx, ty, queue, attackMove)


## A world on flat open ground (`field()` in the TS): open_plains still has
## ponds and shore, and these tests are about what the advisor does with
## ground we control, so the terrain is wiped to grass.
func field(seed: int = 5) -> World:
	var map := AITestUtil.mapOf("open_plains", seed, 2)
	var w := World.new(seed)
	w.init(map, [{}, {}], [1.0, 1.0], [0, 1])
	var t := PackedByteArray()
	t.resize(w.terrain.size())
	t.fill(0)
	w.terrain = t
	return w


func paint(w: World, wx: float, wy: float, t: int, r: int = 3) -> void:
	for dy in range(-r, r + 1):
		for dx in range(-r, r + 1):
			var px := floori(wx / w.TILE) + dx
			var py := floori(wy / w.TILE) + dy
			if px < 0 or py < 0 or px >= w.terrainCols or py >= w.terrainRows:
				continue
			w.terrain[py * w.terrainCols + px] = t


func knightDuel(world: World, readsGround: bool = true) -> Array:
	var ais := [
		SkirmishAI.new(world, T.Team.Player, C.DIFFICULTIES.knight),
		SkirmishAI.new(world, T.Team.Enemy, C.DIFFICULTIES.knight),
	]
	for ai: SkirmishAI in ais:
		ai.readsGround = readsGround
	return ais


## Ticks a match with its brains; `until` (optional) ends it early once true.
func play(world: World, ais: Array, ticks: int, until = null) -> void:
	for i in ticks:
		world.tick()
		for ai: SkirmishAI in ais:
			ai.update(1.0 / 20.0)
		world.drainEvents()
		if world.winner != null:
			break
		if until != null and until.call():
			break


# ---------------------------------------------------------- Reading the ground --

func test_ranks_a_hill_above_open_ground_and_a_wood_or_marsh_below_it() -> void:
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	paint(w, cx - 300, cy, Terrain.Hill)
	paint(w, cx + 300, cy, Terrain.Forest)
	paint(w, cx, cy + 300, Terrain.Marsh)
	var hill := TerrainSense.groundScore(w, cx - 300, cy)
	var open := TerrainSense.groundScore(w, cx, cy)
	var wood := TerrainSense.groundScore(w, cx + 300, cy)
	var marsh := TerrainSense.groundScore(w, cx, cy + 300)
	check(hill > open, "hill > open")
	check(wood < open, "wood < open")
	check(marsh < open, "marsh < open")
	# A wall is not ground at all.
	paint(w, cx, cy - 300, Terrain.Rock)
	eq(TerrainSense.groundScore(w, cx, cy - 300), -INF, "rock")


func test_scores_a_wood_and_a_marsh_far_enough_below_open_ground_to_avoid_them() -> void:
	# The margin is the mechanism: there is no separate "is this fightable"
	# predicate, so woods and marsh are avoided only because the penalty is big
	# enough to outweigh the walk to somewhere else.
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	paint(w, cx + 200, cy, Terrain.Forest)
	paint(w, cx, cy + 200, Terrain.Marsh)
	var open := TerrainSense.groundScore(w, cx, cy)
	check(open - TerrainSense.groundScore(w, cx + 200, cy) > 30, "wood is not punished enough")
	check(open - TerrainSense.groundScore(w, cx, cy + 200) > 30, "marsh is not punished enough")


func test_walks_to_a_nearby_hill_rather_than_standing_where_told() -> void:
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	paint(w, cx + 120, cy, Terrain.Hill, 4)
	var spot := TerrainSense.bestGroundNear(w, cx, cy, {"radius": 260.0})
	check(TerrainSense.isHighGround(w, spot.x, spot.y), "did not find the hill")


func test_will_cross_the_whole_search_radius_for_high_ground_when_worth_it() -> void:
	# The scale guard: the distance penalty and the ground score have to be in
	# the same units, or the search radius is a lie.
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	paint(w, cx + 250, cy, Terrain.Hill, 4)
	var far := TerrainSense.bestGroundNear(w, cx, cy, {"radius": 320.0, "detour": 8.0})
	check(TerrainSense.isHighGround(w, far.x, far.y), "would not walk for a hill")
	# …and the same knob, turned the other way, keeps it home.
	var near := TerrainSense.bestGroundNear(w, cx, cy, {"radius": 320.0, "detour": 40.0})
	check(not TerrainSense.isHighGround(w, near.x, near.y), "wandered off for a distant hill")


func test_stays_put_when_the_ground_nearby_is_no_better() -> void:
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	var spot := TerrainSense.bestGroundNear(w, cx, cy, {"radius": 260.0})
	eq(spot.x, cx, "x")
	eq(spot.y, cy, "y")


func test_will_not_send_an_army_to_a_hill_it_cannot_reach() -> void:
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	# A hill on the far side of a mountain range that crosses the whole map —
	# a short wall would simply be walked around.
	paint(w, cx + 200, cy, Terrain.Hill, 3)
	var wallX := floori((cx + 110) / w.TILE)
	for py in w.terrainRows:
		for dx in 3:
			w.terrain[py * w.terrainCols + wallX + dx] = Terrain.Rock
			w.grid.setBlocked(wallX + dx, py, true)
	var spot := TerrainSense.bestGroundNear(w, cx + 200, cy, {"radius": 260.0, "from": {"x": cx - 100, "y": cy}})
	check(not TerrainSense.isHighGround(w, spot.x, spot.y), "staged on an unreachable hill")


func test_prefers_a_rise_for_a_tower() -> void:
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	paint(w, cx + 60, cy, Terrain.Hill, 2)
	var site := TerrainSense.bestTowerSite(w, cx, cy, 4 * w.TILE)
	check(TerrainSense.isHighGround(w, site.x, site.y), "tower not on the rise")


func test_never_suggests_a_tower_site_on_unbuildable_ground() -> void:
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	paint(w, cx, cy, Terrain.Forest, 2)
	var site := TerrainSense.bestTowerSite(w, cx, cy, 4 * w.TILE)
	check(w.terrainAt(site.x, site.y) != Terrain.Forest, "tower site in a wood")


func test_never_suggests_a_tower_site_inside_a_mountain() -> void:
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	paint(w, cx, cy, Terrain.Rock, 2)
	var site := TerrainSense.bestTowerSite(w, cx, cy, 4 * w.TILE)
	check(w.terrainAt(site.x, site.y) != Terrain.Rock, "tower site in rock")


# ------------------------------------------------------- Staging and flanking --

func test_gathers_short_of_the_target() -> void:
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	var target := {"x": cx + 800, "y": cy}
	var army := {"x": cx - 800, "y": cy}
	var stage := TerrainSense.stagingPoint(w, target, army)
	var toTarget := JS.hypot(stage.x - target.x, stage.y - target.y)
	check(toTarget > 200, "staged on top of the target")
	# …and on the army's side of it, not past it.
	check(stage.x < target.x, "staged past the target")


func test_takes_the_high_ground_on_the_approach() -> void:
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	var target := {"x": cx + 800, "y": cy}
	var army := {"x": cx - 800, "y": cy}
	paint(w, cx + 380, cy, Terrain.Hill, 4)
	var stage := TerrainSense.stagingPoint(w, target, army)
	check(TerrainSense.isHighGround(w, stage.x, stage.y), "ignored the hill on the approach")


func test_does_not_walk_backwards_to_stage() -> void:
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	var target := {"x": cx + 100, "y": cy}
	var army := {"x": cx, "y": cy}
	var stage := TerrainSense.stagingPoint(w, target, army)
	check(JS.hypot(stage.x - army.x, stage.y - army.y) < 260, "walked backwards to stage")


func test_flanks_to_the_side_of_the_approach_on_ground_that_exists() -> void:
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	var target := {"x": cx, "y": cy}
	var army := {"x": cx - 800, "y": cy}
	for side in [1, -1]:
		var fp := TerrainSense.flankPoint(w, target, army, side)
		check(absf(fp.y - cy) > 100, "side %d: not to the side" % side)
		check(fp.x >= 0 and fp.x < w.worldW, "side %d: x off the map" % side)
		check(fp.y >= 0 and fp.y < w.worldH, "side %d: y off the map" % side)


func test_never_picks_a_flank_inside_a_lake() -> void:
	var w := field()
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	var target := {"x": cx, "y": cy}
	var army := {"x": cx - 800, "y": cy}
	# Drown both flanks, wide enough that the whole search radius is water.
	paint(w, cx, cy - 420, Terrain.Water, 14)
	paint(w, cx, cy + 420, Terrain.Water, 14)
	for side in [1, -1]:
		var fp := TerrainSense.flankPoint(w, target, army, side)
		check(w.terrainAt(fp.x, fp.y) != Terrain.Water, "side %d: flank in the lake" % side)


# ------------------------------------------------------- The AI reads the ground --

func test_can_be_switched_off() -> void:
	var w := World.new(3)
	w.init(AITestUtil.mapOf("highlands", 3, 2), [{}, {}], [1.0, 1.0], [0, 1])
	var ai := SkirmishAI.new(w, T.Team.Player, C.DIFFICULTIES.knight)
	eq(ai.readsGround, true, "default")
	ai.readsGround = false
	eq(ai.readsGround, false, "switched off")


func test_actually_orders_armies_onto_high_ground_in_a_real_match() -> void:
	# The advisors work in isolation (above); this proves the AI reaches them.
	# Totalled over several seeds rather than pinned to one. The TS runs seeds
	# 2, 3, 4 and 6 for twelve minutes each, aware and blind. Seeds 3 and 4 over
	# seven minutes already tell the two apart (in the TS build, 8 + 5 hill
	# orders aware against none blind; the twelve-minute totals are 100 and 60),
	# so the port plays that.
	var onHill := func(readsGround: bool) -> int:
		var hills := 0
		for seed in [3, 4]:
			var world := HillWatchWorld.new(seed)
			world.init(AITestUtil.mapOf("highlands", seed, 2), [{}, {}], [1.0, 1.0], [0, 1])
			var ais := knightDuel(world, readsGround)
			play(world, ais, 20 * 60 * 7)
			hills += world.hills
		return hills
	var aware: int = onHill.call(true)
	var blind: int = onHill.call(false)
	check(aware > 0, "never sent anyone to a hill")
	check(aware > blind, "reading the ground changed nothing about where armies went (aware %d, blind %d)" % [aware, blind])


func test_plays_a_full_match_on_terrain_heavy_ground_without_stalling() -> void:
	# If any advisor ever returned somewhere unreachable or off the map, an army
	# would sit still and the economy would be the only thing that moved. The TS
	# plays seeds 1, 2 and 3 for twelve minutes; by minute eight the armies have
	# met on every one of them (13, 35 and 35 kills in the TS build), so the port
	# plays eight.
	var kills := 0.0
	for seed in [1, 2, 3]:
		var world := World.new(seed)
		world.init(AITestUtil.mapOf("highlands", seed, 2), [{}, {}], [1.0, 1.0], [0, 1])
		var ais := knightDuel(world)
		play(world, ais, 20 * 60 * 8)
		kills += world.player(T.Team.Player).stats.unitsKilled + world.player(T.Team.Enemy).stats.unitsKilled
		for t in [T.Team.Player, T.Team.Enemy]:
			check(world.player(t).stats.gathered > 500, "seed %d economy stalled" % seed)
	check(kills > 0, "the armies never met on any seed")


# ------------------------------------------------------- Finding a ford on the way --

## A world with a band of shallows across the middle: {w, fordX, midY}.
func forded(halfWidth: int = 3) -> Dictionary:
	var w := field(31)
	var midC := floori(w.terrainCols / 2.0)
	for cy in w.terrainRows:
		for dx in range(-halfWidth, halfWidth + 1):
			w.terrain[cy * w.terrainCols + midC + dx] = Terrain.Shallow
			w.grid.setBlocked(midC + dx, cy, false)
	return {"w": w, "fordX": midC * w.TILE + w.TILE / 2.0, "midY": floori(w.terrainRows / 2.0) * w.TILE}


func test_finds_the_crossing_on_the_line_between_two_points() -> void:
	var f := forded()
	var cross = TerrainSense.fordCrossing(f.w, {"x": f.fordX - 600, "y": f.midY}, {"x": f.fordX + 600, "y": f.midY})
	check(cross != null, "did not notice the ford at all")
	if cross != null:
		check(absf(cross.x - f.fordX) < f.w.TILE * 3, "the crossing is not where the water is")


func test_finds_nothing_when_the_route_stays_on_dry_land() -> void:
	var f := forded()
	check(TerrainSense.fordCrossing(f.w, {"x": f.fordX - 900, "y": f.midY}, {"x": f.fordX - 400, "y": f.midY}) == null, "found a ford on dry land")


func test_reports_the_width_so_a_puddle_can_be_told_from_a_ford() -> void:
	var narrow := forded(1)
	var wide := forded(6)
	var a = TerrainSense.fordCrossing(narrow.w, {"x": narrow.fordX - 600, "y": narrow.midY}, {"x": narrow.fordX + 600, "y": narrow.midY})
	var b = TerrainSense.fordCrossing(wide.w, {"x": wide.fordX - 600, "y": wide.midY}, {"x": wide.fordX + 600, "y": wide.midY})
	check(b.width > a.width, "wide ford not wider (%d vs %d)" % [b.width, a.width])


func test_stops_seeing_a_ford_once_it_has_been_bridged() -> void:
	# Otherwise the AI would pay for a second crossing beside the first. The band
	# is exactly two cells — one bridge footprint — so a decked crossing really is
	# fully decked.
	var w := field(31)
	var midC := floori(w.terrainCols / 2.0)
	for cy in w.terrainRows:
		for cx in [midC, midC + 1]:
			w.terrain[cy * w.terrainCols + cx] = Terrain.Shallow
			w.grid.setBlocked(cx, cy, false)
	var fordX := midC * w.TILE + w.TILE / 2.0
	var midY := floori(w.terrainRows / 2.0) * w.TILE
	var from := {"x": fordX - 600, "y": midY}
	var to := {"x": fordX + 600, "y": midY}
	check(TerrainSense.fordCrossing(w, from, to) != null, "no ford before bridging")
	w.player(T.Team.Player).resources.wood = 5000.0 # enough to deck the whole span
	for dy in range(-8, 9):
		var b := w.placeBuilding(T.Team.Player, "bridge", fordX, midY + dy * 2 * w.TILE)
		if b != null:
			b.buildState = 0
			w.onBuildingCompleted(b, {"onlyOnShallows": true, "popProvided": 0.0, "id": "bridge"}, true)
	check(TerrainSense.fordCrossing(w, from, to) == null, "kept seeing a ford that is now decked")


func test_picks_the_widest_crossing_when_the_route_wades_more_than_once() -> void:
	var w := field(31)
	var band := func(cx: int, half: int) -> void:
		for cy in w.terrainRows:
			for dx in range(-half, half + 1):
				w.terrain[cy * w.terrainCols + cx + dx] = Terrain.Shallow
	band.call(30, 1)
	band.call(60, 5)
	var midY := floori(w.terrainRows / 2.0) * w.TILE
	var cross = TerrainSense.fordCrossing(w, {"x": 10 * w.TILE, "y": midY}, {"x": 90 * w.TILE, "y": midY})
	# The wide one, not the first one met — that is the crossing costing time.
	check(absf(cross.x - 60 * w.TILE) < w.TILE * 4, "picked the narrow crossing")


# ----------------------------------------------------------- The AI and the water --

## Two bases either side of a ford, so every attack has to cross it.
func acrossAFord(seed: int) -> World:
	var world := World.new(seed)
	world.init(AITestUtil.mapOf("open_plains", seed, 2), [{}, {}], [1.0, 1.0], [0, 1])
	var midC := floori(world.terrainCols / 2.0)
	for cy in world.terrainRows:
		for dx in range(-3, 4):
			world.terrain[cy * world.terrainCols + midC + dx] = Terrain.Shallow
			world.grid.setBlocked(midC + dx, cy, false)
	return world


static func anyBridge(world: World, builtOnly := false) -> bool:
	for e: Entity in world.entities:
		if e.type == "bridge" and (not builtOnly or e.kind == T.Kind.Building):
			return true
	return false


## The knights in acrossAFord(4) lay their first bridge at tick 2257 (under two
## minutes) in the TS build, so the bridging test stops as soon as one stands,
## and the two "never bridges" tests run four minutes — twice that — rather
## than the TS's ten and fourteen: by then a brain that would bridge has.
const BRIDGE_WINDOW_TICKS := 20 * 60 * 4


func test_lays_a_crossing_over_the_ford_its_army_keeps_wading_through() -> void:
	# The regression this whole pair of features exists for: an AI that never
	# builds a bridge is a building the game owns and never uses.
	var world := acrossAFord(4)
	var ais := knightDuel(world)
	play(world, ais, 20 * 60 * 14, func(): return anyBridge(world, true))
	check(anyBridge(world, true), "neither AI ever bridged the ford")


func test_does_not_bridge_when_the_route_never_touches_water() -> void:
	# A hundred wood set on fire. The check is tied to the route it actually
	# takes, not to "is there water anywhere on this map".
	var world := World.new(4)
	world.init(AITestUtil.mapOf("open_plains", 4, 2), [{}, {}], [1.0, 1.0], [0, 1])
	var t := PackedByteArray()
	t.resize(world.terrain.size())
	t.fill(0)
	world.terrain = t # all grass
	var ais := knightDuel(world)
	play(world, ais, BRIDGE_WINDOW_TICKS)
	check(not anyBridge(world), "bridged dry land")


func test_bridging_can_be_switched_off_with_the_rest_of_its_terrain_sense() -> void:
	var world := acrossAFord(4)
	var ais := knightDuel(world, false)
	play(world, ais, BRIDGE_WINDOW_TICKS)
	check(not anyBridge(world), "a blind AI bridged the ford")
