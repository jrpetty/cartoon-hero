## ai/skirmish_ai.ts — the skirmish AI brain. Acts only through the same public
## World command API the human player uses. Behavior scales with difficulty:
## scouting, counter composition, expansion, harassment and attack pacing (see
## difficulty.ts; the tiers are `C.DIFFICULTIES`).
##
## Port notes (see PORTING.md): every TS name is kept; `diff` is the difficulty
## Dictionary from `C.DIFFICULTIES`; points are `{x, y}` Dictionaries; the
## SeenComposition is a Dictionary `{infantry, archer, cavalry, siege}`;
## compositions are Dictionaries whose key insertion order matches the TS
## objects (the trainer walks them in that order). The TS getters `armySize`,
## `cadence`, `villagerGoal`, `turtles`, `enemyTeam` and `focusEnemy` are
## methods here, so they are recomputed on every read as in the TS. The match
## roster (TS `AI_ROSTER`, a WeakMap keyed by World) is `World.aiRoster`.
##
## A Nemesis rival's adaptation (meta/rival.ts `RivalAdaptation`) is a
## Dictionary: `{style?: AIStyle, expect?: Partial<SeenComposition>, walls?}` —
## the approach that beats how the player plays, the army it expects to face
## before it has seen a single soldier, and whether to wall up before the first
## attack. AIStyle: "rush" | "boom" | "turtle" | "balanced".
class_name SkirmishAI
extends RefCounted

## How long the AI will hold production back to bank an age-up before giving up.
const AGE_SAVE_MAX_SEC := 60.0
## …and how long it then grows before it is allowed to try banking again.
const AGE_SAVE_COOLDOWN := 45.0
## How long the next age can sit ready — requirements built, villagers enough —
## before the AI starts saving for it even though the bank isn't close.
const AGE_READY_WAIT_SEC := 90.0

## High-value structures an assault should drive toward (the kill, not the wall).
const PRODUCTION := {
	"town_center": true, "barracks": true, "archery_range": true, "stable": true,
	"siege_workshop": true, "blacksmith": true, "mill": true, "market": true,
}

const RES_KINDS := ["food", "wood", "gold"]
const SEEN_KEYS := ["infantry", "archer", "cavalry", "siege"]

const STYLE_ARMY := {"rush": 0.62, "boom": 1.35, "turtle": 1.5, "balanced": 1.0}
const STYLE_CADENCE := {"rush": 0.58, "boom": 1.18, "turtle": 1.3, "balanced": 1.0}
const STYLE_VILLAGERS := {"rush": 0.82, "boom": 1.25, "turtle": 1.05, "balanced": 1.0}

const REBUILDABLE := [
	"barracks", "archery_range", "stable", "blacksmith", "siege_workshop",
	"mill", "market", "lumber_camp", "mining_camp",
]

const NODE_FOR := {
	"food": ["berries", "farm"],
	"wood": ["tree"],
	"gold": ["gold_mine"],
}

const COMPASS := ["east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"]

const FORT_TYPES := ["stone_wall", "gate", "palisade", "watch_tower", "castle"]


## Kill-order value of an enemy unit in an army clash — delete what hurts us
## most or is about to die first. Exported for testing.
static func armyTargetPriority(type: String) -> float:
	var u = C.UNITS.get(type)
	if u == null:
		return 40.0
	if u.get("hero", false):
		return 115.0
	if u.armorClass == T.AC_SIEGE:
		return 120.0 # splash/rams wreck a clumped army
	if type == "monk":
		return 95.0 # heals/converts
	if u.range >= 60:
		return 80.0 # archers/crossbow/handcannon/skirmisher/javelin
	if u.armorClass == T.AC_CAVALRY:
		return 60.0
	return 45.0 # infantry


var timer := 0.0
var rng: RNG
var seen: Dictionary = {"infantry": 0.0, "archer": 0.0, "cavalry": 0.0, "siege": 0.0}
var lastWaveTime := 0.0
var lastHarassTime := 0.0
var lastBridgeTime := -999.0
var lastTradeTime := -999.0
var lastScoutTime := -999.0
var scoutId := -1
var wallsPlanned := false
var attacking := false
var gameTime := 0.0
var seenWalls := 0
var lastRetreatTime := -999.0
var lastAllyHelpTime := -999.0
var lastPressTime := -999.0
var lastCalloutTime := -999.0
var lastFocusId := -1 ## current focus-fire target (avoids re-issuing every tick)
var savingForAge := false
var saveStartedAt := 0.0
var saveGaveUpAt := -999.0
## When the next age first became ready (requirements and villagers), or -1.
var ageReadySince := -1.0
## Whether this AI reads the ground it is fighting on. Public and mutable for
## one reason: it is the only way to measure whether terrain awareness is
## worth anything, by running an aware AI against a blind one on the same map
## with the same seed. A feature that cannot be measured cannot be defended.
var readsGround := true
## Whether a wave forms up on good ground before charging. Mutable for the same
## reason `readsGround` is: it is the only way to tell whether the extra
## marching leg pays for itself.
var stagesOnGoodGround := true
## Building types we've finished at least once — drives prompt rebuilds (a set).
var everBuilt: Dictionary = {}
## Shared roster of all AI brains in this match (for ally intel pooling):
## World.aiRoster, WeakRefs in creation order.
var roster: Array
## Personality — biases army timing, eco focus and turtling.
var style := "balanced"
## A rival's memory of the player's army — a floor under what it has scouted
## (a SeenComposition Dictionary, or null).
var expected = null
var rivalWalls := false
## The Oath chosen for each age advance (age -> oath id), cached so the same
## decision is made however many times it is asked.
var oathChoice: Dictionary = {}

var world: World
var team: int
var diff: Dictionary
var TILE: float
var UNITS: Dictionary
var BUILDINGS: Dictionary

# Caches the TS does without (V8 can afford to filter every entity in the
# world for each of the few dozen questions a think step asks; GDScript
# cannot). They change no answer:
#  * our own units and buildings, in entity order, also grouped by type —
#    taken once per tick, then extended with whatever has been appended to
#    `world.entities` since (a building we just placed). Every filter the TS
#    applies (alive, kind, type, buildState) is still applied when asked; an
#    entity's team, kind and type never change after it spawns.
#  * which teams are hostile to us, per tick (alliances are fixed at init).
var _idxTick := -1
var _idxSrc: Array = []
var _idxSize := 0
var _own: Array = []
var _ownByType: Dictionary = {}
var _hostileTick := -1
var _hostile := PackedByteArray()


func _init(world_: World, team_: int, diff_: Dictionary) -> void:
	world = world_
	team = team_
	diff = diff_
	TILE = C.TILE
	UNITS = C.UNITS
	BUILDINGS = C.BUILDINGS
	rng = world.rng.fork(100 + team)
	# Squire stays vanilla; mid tiers get the full personality spread; the top
	# tiers stay aggressive (no turtling) so they keep the pressure on.
	var pools := {
		"squire": ["balanced"],
		"knight": ["rush", "boom", "turtle", "balanced", "balanced"],
		"lord": ["rush", "balanced", "balanced", "boom"],
		"warlord": ["rush", "rush", "balanced", "balanced", "boom"],
		"conqueror": ["rush", "rush", "balanced", "boom"],
	}
	var styles: Array = pools.get(diff.id, ["balanced"])
	style = styles[rng.int_(0, styles.size() - 1)]
	# First wave shouldn't come absurdly early on slow tiers.
	lastWaveTime = -cadence() * 0.4

	# Join the match roster so allies can pool intel.
	world.aiRoster.append(weakref(self))
	roster = world.aiRoster


## Play as a rival who has fought this player before (see RivalAdaptation).
func adapt(a: Dictionary) -> void:
	if a.get("style") != null and str(a.style) != "":
		style = a.style
		lastWaveTime = -cadence() * 0.4
	if a.get("expect") != null:
		var e := {"infantry": 0.0, "archer": 0.0, "cavalry": 0.0, "siege": 0.0}
		for k in a.expect:
			e[k] = a.expect[k]
		expected = e
	if a.get("walls", false):
		rivalWalls = true


## Living allied AI brains (excluding self) for intel pooling.
func alliedAIs() -> Array:
	var out: Array = []
	for wr in roster:
		var a = wr.get_ref()
		if a == null or a == self:
			continue
		if world.areAllied(team, a.team) and not world.player(a.team).defeated:
			out.append(a)
	return out


## A small chance to fumble an optional action — see DifficultyDef.
func flub() -> bool:
	return rng.range(0.0, 1.0) < diff.executionError


## Compass direction of (ax,ay) relative to (ox,oy), e.g. "north".
func compass(ox: float, oy: float, ax: float, ay: float) -> String:
	var deg := (JSAtan.atan2(ay - oy, ax - ox) * 180.0) / PI # y grows downward
	var i := int(JS.round(fmod(deg + 360.0, 360.0) / 45.0)) % 8
	return COMPASS[i]


## Emit an AI voice line (team games only), rate-limited against spam.
func say(text: String, x: float, y: float, minGap: float = 14.0) -> void:
	if not hasAlly():
		return
	if gameTime - lastCalloutTime < minGap:
		return
	lastCalloutTime = gameTime
	world.callout(team, x, y, text)


# Personality-scaled tunables ------------------------------------------------

func armySize() -> float:
	var m: float = STYLE_ARMY[style]
	return maxf(6.0, JS.round(diff.attackArmySize * m))


func cadence() -> float:
	var m: float = STYLE_CADENCE[style]
	return diff.attackEverySec * m


func villagerGoal() -> float:
	var m: float = STYLE_VILLAGERS[style]
	var base: float = diff.villagerTarget * m
	# Once at the top age, real opponents (Knight+) keep growing the economy so a
	# full-pop army is affordable (otherwise they plateau ~60). Gated to max age
	# so early-age progression thresholds stay stable.
	var p := world.player(team)
	# (Crown, not the top age: Empire is a late destination, and waiting for it
	# to start growing would starve the army that has to get there.)
	var grow := 16.0 + gameTime / 60.0 if diff.maxAge >= 2 and p.age >= 2 else 0.0
	return JS.round(minf(base + grow, base * 2.2))


func turtles() -> bool:
	return style == "turtle"


## Rough combat strength of own (mine=true) or hostile units near a point.
func strengthNear(x: float, y: float, r: float, mine: bool) -> float:
	var s := 0.0
	var r2 := r * r
	var hostile := _hostileMask()
	for e: Entity in world.entities:
		if not e.alive or e.kind != T.Kind.Unit:
			continue
		if (e.team != team) if mine else (hostile[e.team] == 0):
			continue
		if e.type == "villager":
			continue
		var dx := e.x - x
		var dy := e.y - y
		if dx * dx + dy * dy > r2:
			continue
		var def = UNITS.get(e.type)
		s += (def.attack if def != null else 4.0) + e.hp * 0.08
	return s


func update(dt: float) -> void:
	gameTime += dt
	timer -= dt
	if timer > 0.0:
		return
	timer = diff.reactionSec
	if world.winner != null:
		return
	var p := world.player(team)
	if p == null or p.defeated:
		return

	updateIntel()
	economy()
	military()


# ------------------------------------------------------------- helpers --

## Bring the own-entity cache up to date (see the note on `_idxTick`).
func _index() -> void:
	var ents: Array = world.entities
	if world.tickCount != _idxTick or not is_same(ents, _idxSrc) or ents.size() < _idxSize:
		_idxTick = world.tickCount
		_idxSrc = ents
		_idxSize = 0
		_own = []
		_ownByType = {}
	var n := ents.size()
	if _idxSize == n:
		return
	for i in range(_idxSize, n):
		var e: Entity = ents[i]
		if e.team != team or (e.kind != T.Kind.Unit and e.kind != T.Kind.Building):
			continue
		_own.append(e)
		var list = _ownByType.get(e.type)
		if list == null:
			_ownByType[e.type] = [e]
		else:
			list.append(e)
	_idxSize = n


## Our units and buildings (alive or not) of `type`, or all of them, in entity order.
func _ownOf(type: String) -> Array:
	_index()
	if type == "":
		return _own
	return _ownByType.get(type, [])


func myUnits(type: String = "") -> Array:
	var out: Array = []
	for e: Entity in _ownOf(type):
		if e.alive and e.kind == T.Kind.Unit:
			out.append(e)
	return out


func myBuildings(type: String = "", doneOnly: bool = true) -> Array:
	var out: Array = []
	for e: Entity in _ownOf(type):
		if e.alive and e.kind == T.Kind.Building and (not doneOnly or e.buildState == T.BuildState.Done):
			out.append(e)
	return out


## `myBuildings(type, doneOnly).length` without building the list.
func _countBuildings(type: String, doneOnly: bool) -> int:
	var n := 0
	for e: Entity in _ownOf(type):
		if e.alive and e.kind == T.Kind.Building and (not doneOnly or e.buildState == T.BuildState.Done):
			n += 1
	return n


## world.countOf(team, type) for a unit or building type: how many of ours
## are alive (nothing else of ours — a projectile — shares those type names).
func _countOwn(type: String) -> int:
	var n := 0
	for e: Entity in _ownOf(type):
		if e.alive:
			n += 1
	return n


## world.ageRequirementMet(team, ageIndex), answered from our own buildings:
## the age needs `requiresCount` distinct types from `requiresAny` standing
## finished (World.hasBuilding: alive, ours, a Building, Done).
func _ageRequirementMet(ageIndex: int) -> bool:
	var AGES: Array = world.AGES
	if ageIndex < 0 or ageIndex >= AGES.size():
		return true # {have: 0, need: 0}
	var age: Dictionary = AGES[ageIndex]
	var have := 0
	for id: String in age.requiresAny:
		if _countBuildings(id, true) > 0:
			have += 1
	var need := int(age.requiresCount)
	return mini(have, need) >= need


## Hostility to us by team id (0..Neutral), refreshed each tick.
func _hostileMask() -> PackedByteArray:
	if _hostileTick != world.tickCount or _hostile.is_empty():
		_hostileTick = world.tickCount
		_hostile.resize(T.Team.Neutral + 1)
		for t in T.Team.Neutral + 1:
			_hostile[t] = 1 if world.areHostile(team, t) else 0
	return _hostile


## A foe is any hostile team (respects 2v2 alliances).
func isHostile(t: int) -> bool:
	if t < 0 or t > T.Team.Neutral:
		return world.areHostile(team, t)
	return _hostileMask()[t] == 1


## Entities bucketed on a grid a little coarser than `r`, to answer "is any
## of them closer than `r` to (x, y)?" — the TS `list.some((b) => dist(x, y,
## b.x, b.y) < r)` — by looking only in the 3×3 cells around the point (with
## cells wider than `r`, nothing closer can be further away than that).
class _Buckets extends RefCounted:
	var r: float
	var cell: float
	var cells := {}

	func _init(ents: Array, r_: float) -> void:
		r = r_
		cell = r_ * 1.01
		for b: Entity in ents:
			var key := _key(floori(b.x / cell), floori(b.y / cell))
			var list = cells.get(key)
			if list == null:
				cells[key] = [b]
			else:
				list.append(b)

	static func _key(cx: int, cy: int) -> int:
		return (cx + 1048576) * 2097152 + (cy + 1048576)

	func anyCloser(x: float, y: float) -> bool:
		if cells.is_empty():
			return false
		var cx := floori(x / cell)
		var cy := floori(y / cell)
		for dy in range(-1, 2):
			for dx in range(-1, 2):
				var list = cells.get(_key(cx + dx, cy + dy))
				if list == null:
					continue
				for b: Entity in list:
					if SkirmishAI._closer(x, y, b.x, b.y, r):
						return true
		return false


## `dist(ax, ay, bx, by) < r`, bit for bit — the squared distance settles
## every case that isn't within a hair of `r` without the (slow, in GDScript)
## exact Math.hypot.
static func _closer(ax: float, ay: float, bx: float, by: float, r: float) -> bool:
	var dx := ax - bx
	var dy := ay - by
	var d2 := dx * dx + dy * dy
	var r2 := r * r
	if d2 < r2 * 0.999999999:
		return true
	if d2 > r2 * 1.000000001:
		return false
	return JS.hypot(dx, dy) < r


## `dist(ax, ay, bx, by)` when it might be below `bound`, else INF — for
## nearest-of searches, which only need the exact distance of a contender.
static func _distIfBelow(ax: float, ay: float, bx: float, by: float, bound: float) -> float:
	var dx := ax - bx
	var dy := ay - by
	if is_finite(bound) and dx * dx + dy * dy > bound * bound * 1.000000001:
		return INF
	return JS.hypot(dx, dy)


## Stable sort of entities by distance to (x, y) — the TS
## `.sort((a, b) => dist(a.x, a.y, x, y) - dist(b.x, b.y, x, y))`, with each
## distance worked out once.
static func _sortByDist(arr: Array, x: float, y: float) -> Array:
	var keyed: Array = []
	for e: Entity in arr:
		keyed.append([JS.hypot(e.x - x, e.y - y), e])
	JS.stable_sort(keyed, func(a, b): return a[0] < b[0])
	var out: Array = []
	for k in keyed:
		out.append(k[1])
	return out


## The rival we're focusing: the nearest non-defeated enemy by base distance.
func enemyTeam() -> int:
	var myBase = base()
	var best := (team + 1) % world.numTeams
	var bestD := INF
	for t in world.numTeams:
		if not isHostile(t):
			continue
		var p := world.player(t)
		if p == null or p.defeated:
			continue
		var s = world.map.starts[t] if t < world.map.starts.size() else null
		var d := MathU.dist(myBase.x, myBase.y, s.x, s.y) if myBase != null and s != null else 0.0
		if d < bestD:
			bestD = d
			best = t
	return best


func base() -> Entity:
	for e: Entity in _ownOf("town_center"):
		if e.alive and e.kind == T.Kind.Building and e.buildState == T.BuildState.Done:
			return e
	for e: Entity in _ownOf(""):
		if e.alive and e.kind == T.Kind.Building and e.buildState == T.BuildState.Done:
			return e
	return null


# --------------------------------------------------- team coordination --

## True when this AI has at least one living allied teammate (a team game).
func hasAlly() -> bool:
	for t in world.numTeams:
		if t == team:
			continue
		if not world.areAllied(team, t):
			continue
		var p := world.player(t)
		if p != null and not p.defeated:
			return true
	return false


## Rough total strength of a team (buildings + standing army).
func teamStrength(t: int) -> float:
	var s := 0.0
	for e: Entity in world.entities:
		if not e.alive or e.team != t:
			continue
		if e.kind == T.Kind.Building:
			s += 12.0
		elif e.kind == T.Kind.Unit and e.type != "villager":
			var def = UNITS.get(e.type)
			s += (def.attack if def != null else 4.0) + e.hp * 0.05
	return s


## The foe the whole alliance should concentrate on. Every allied AI computes
## this the same way (weakest standing enemy, tie-break by team index), so they
## converge on one target instead of splitting their pushes — concentration of
## force is what actually wins team games. Falls back to the nearest enemy when
## we have no teammate (1v1 / FFA are unchanged).
func focusEnemy() -> int:
	if not hasAlly():
		return enemyTeam()
	var best := enemyTeam()
	var bestScore := INF
	for t in world.numTeams:
		if not isHostile(t):
			continue
		var p := world.player(t)
		if p == null or p.defeated:
			continue
		var score := teamStrength(t)
		if score < bestScore - 0.001:
			bestScore = score
			best = t
	return best


## True if a living ally is currently assaulting (army units mid-attack).
func allyAssaulting() -> bool:
	var assaulting := 0
	for e: Entity in world.entities:
		if not e.alive or e.kind != T.Kind.Unit or e.team == team:
			continue
		if not world.areAllied(team, e.team):
			continue
		if e.type == "villager":
			continue
		if e.order.kind == T.OrderKind.Attack or e.order.kind == T.OrderKind.AttackMove:
			assaulting += 1
	return assaulting >= 5


## Plant a Town Center. Picks the best nearby spot with access to wood, gold
## AND food (the nomad-settling aim) rather than just the villagers' centroid.
func foundTownCenter(p: World.PlayerState) -> void:
	if _countBuildings("town_center", false) > 0:
		return # already founding
	var vills := myUnits("villager")
	if vills.size() == 0 or not world.canAfford(p.resources, BUILDINGS.town_center.cost):
		return
	var cx := 0.0
	var cy := 0.0
	for v: Entity in vills:
		cx += v.x
		cy += v.y
	cx /= vills.size()
	cy /= vills.size()
	var spot := bestSettleSpot(cx, cy)
	var tc := placeNear("town_center", spot.x, spot.y, 0.0, 3.0)
	if tc == null:
		tc = placeNear("town_center", cx, cy, 0.0, 5.0)
	if tc != null:
		assignBuilder(tc, mini(3, vills.size()))


## nearest() inside bestSettleSpot: distance to the closest of `arr`, capped.
static func _nearestOf(arr: Array, x: float, y: float, cap: float) -> float:
	var m := cap
	for e: Entity in arr:
		var d := _distIfBelow(e.x, e.y, x, y, m)
		if d < m:
			m = d
	return m


## Scout for the best base site near (cx,cy): score candidate spots by how close
## the nearest wood, gold and food are — rewarding a site that reaches all three
## (and minimising its worst resource), lightly penalising distance travelled.
func bestSettleSpot(cx: float, cy: float) -> Dictionary:
	var trees: Array = []
	var golds: Array = []
	var foods: Array = []
	for e: Entity in world.entities:
		if not e.alive or e.kind != T.Kind.Resource or e.amount <= 0.0:
			continue
		if e.type == "tree":
			trees.append(e)
		elif e.type == "gold_mine":
			golds.append(e)
		elif e.type == "berries":
			foods.append(e)
	var CAP := 60.0 * TILE # treat "no resource of this kind nearby" as this far
	# Candidates: the centroid, a coarse grid around it, and points beside each
	# gold vein (gold is the scarce anchor — settle by it if wood/food are close).
	var cands: Array = [{"x": cx, "y": cy}]
	var gx := -6
	while gx <= 6:
		var gy := -6
		while gy <= 6:
			cands.append({"x": cx + gx * 5 * TILE, "y": cy + gy * 5 * TILE})
			gy += 2
		gx += 2
	for g: Entity in golds:
		if JS.hypot(g.x - cx, g.y - cy) > 44.0 * TILE:
			continue
		for a in [0.0, PI / 2.0, PI, -PI / 2.0]:
			cands.append({"x": g.x + JSMath.cos(a) * 5.0 * TILE, "y": g.y + JSMath.sin(a) * 5.0 * TILE})
	var margin := 6.0 * TILE
	var best: Dictionary = cands[0]
	var bestScore := -INF
	for c: Dictionary in cands:
		var x: float = c.x
		var y: float = c.y
		if x < margin or y < margin or x > world.worldW - margin or y > world.worldH - margin:
			continue
		var dW := _nearestOf(trees, x, y, CAP)
		var dG := _nearestOf(golds, x, y, CAP)
		var dF := _nearestOf(foods, x, y, CAP)
		var distHome := JS.hypot(x - cx, y - cy)
		# Lower is better → negate. Penalise the worst resource hardest so a site
		# that reaches all three beats one hugging a single resource.
		var s := -(dW + dG * 1.15 + dF) - 0.7 * maxf(maxf(dW, dG), dF) - 0.4 * distHome
		if s > bestScore:
			bestScore = s
			best = c
	return best


## Cost of the next age the AI is ready to research, or null if not ready.
func ageUpCost(p: World.PlayerState) -> Variant:
	var vills := myUnits("villager").size()
	if p.age == 0 and diff.maxAge >= 1 and _ageRequirementMet(1) and \
			vills >= floor(villagerGoal() * 0.55):
		return world.AGES[1].cost
	if p.age == 1 and diff.maxAge >= 2 and _ageRequirementMet(2) and \
			vills >= floor(villagerGoal() * 0.6):
		return world.AGES[2].cost
	if empireReady(p, vills):
		return world.AGES[3].cost
	return null


## Ready to go for the Empire Age: the building that qualifies, a grown
## economy, and enough match behind us that the army has had its Crown-age
## fights. Going up any earlier spends 1500 resources an army needs more.
func empireReady(p: World.PlayerState, vills: int) -> bool:
	return p.age == 2 and diff.maxAge >= 3 and _ageRequirementMet(3) and \
		vills >= floor(villagerGoal() * 0.7) and gameTime >= 11.0 * 60.0


## Try to place a building somewhere in a ring around (cx, cy).
func placeNear(type: String, cx: float, cy: float, minR: float = 2.0, maxR: float = 9.0) -> Entity:
	for attempt in 24:
		var r := rng.range(minR, maxR) * TILE
		var a := rng.range(0.0, PI * 2.0)
		var x := cx + JSMath.cos(a) * r
		var y := cy + JSMath.sin(a) * r
		var placed := world.placeBuilding(team, type, x, y)
		if placed != null:
			return placed
	return null


## Send the nearest idle (or gathering) villager to construct `b`.
func assignBuilder(b: Entity, count: int = 1) -> void:
	var villagers: Array = []
	for v: Entity in myUnits("villager"):
		if v.order.kind != T.OrderKind.Build:
			villagers.append(v)
	var bx := b.x
	var by := b.y
	villagers = _sortByDist(villagers, bx, by)
	var picked := villagers.slice(0, count)
	if picked.size() > 0:
		var ids: Array = []
		for v: Entity in picked:
			ids.append(v.id)
		world.issueBuildRepair(ids, b.id)


## Where a villager sent for food should go. The nearest berries *anywhere*
## used to win outright, farms only once the last bush on the map was gone —
## so food-gatherers walked across the map (and into the enemy) while fields
## by the base stood empty, and a Banner-age AI made 130 food a minute from
## twenty villagers. Close berries still beat a farm; far ones don't. And a
## farm only one villager can work is only offered to one.
func foodNode(v: Entity, claimed: Dictionary) -> Entity:
	var farm: Entity = null
	var fd := INF
	var berry: Entity = null
	var bd := INF
	for e: Entity in world.entities:
		if not e.alive:
			continue
		if e.kind == T.Kind.Building and e.type == "farm" and e.team == team and \
				e.buildState == T.BuildState.Done and e.amount > 0.0 and not claimed.has(e.id):
			var w = world.byId.get(e.farmWorker) if e.farmWorker >= 0 else null
			if w != null and w.alive and w.id != v.id:
				continue # someone is working it
			var d := _distIfBelow(v.x, v.y, e.x, e.y, fd)
			if d < fd:
				fd = d
				farm = e
		elif e.kind == T.Kind.Resource and e.type == "berries" and e.amount > 0.0:
			var d := _distIfBelow(v.x, v.y, e.x, e.y, bd)
			if d < bd:
				bd = d
				berry = e
	if berry != null and (bd < TILE * 12.0 or farm == null):
		return berry
	return farm if farm != null else berry


func nearestResource(type: String, x: float, y: float) -> Entity:
	var best: Entity = null
	var bestD := INF
	for e: Entity in world.entities:
		if not e.alive:
			continue
		var isFarm := type == "farm" and e.kind == T.Kind.Building and e.type == "farm" and \
			e.team == team and e.buildState == T.BuildState.Done
		if not isFarm and (e.kind != T.Kind.Resource or e.type != type or e.amount <= 0.0):
			continue
		var d := _distIfBelow(x, y, e.x, e.y, bestD)
		if d < bestD:
			bestD = d
			best = e
	return best


# --------------------------------------------------------------- intel --

func updateIntel() -> void:
	var hostile := _hostileMask()
	# Decay memory, then add what's currently visible to our fog.
	var decay := 0.92
	seen.infantry = seen.infantry * decay
	seen.archer = seen.archer * decay
	seen.cavalry = seen.cavalry * decay
	seen.siege = seen.siege * decay
	var nInf := 0
	var nArc := 0
	var nCav := 0
	var nSie := 0
	for e: Entity in world.entities:
		if not e.alive or hostile[e.team] == 0 or e.kind != T.Kind.Unit:
			continue
		# visibleTo, not fogAt: a unit hidden in a wood is not intelligence.
		# Reading the raw fog here would let the AI count an ambush it cannot
		# actually see, which is the "only one side understands it" trap this
		# project keeps falling into.
		if not world.visibleTo(team, e):
			continue
		var def = UNITS.get(e.type)
		if def == null:
			continue
		if def.armorClass == "cavalry":
			nCav += 1
		elif def.armorClass == "archer":
			nArc += 1
		elif def.armorClass == "siege":
			nSie += 1
		elif e.type != "villager":
			nInf += 1
	seen.infantry = maxf(seen.infantry, nInf)
	seen.archer = maxf(seen.archer, nArc)
	seen.cavalry = maxf(seen.cavalry, nCav)
	seen.siege = maxf(seen.siege, nSie)

	# Count the enemy's fortifications we've laid eyes on — drives siege.
	var walls := 0
	for e: Entity in world.entities:
		if not e.alive or hostile[e.team] == 0 or e.kind != T.Kind.Building:
			continue
		if world.fogAt(team, e.x, e.y) == 0:
			continue
		if e.type == "stone_wall" or e.type == "gate" or e.type == "palisade" or e.type == "castle":
			walls += 1
	seenWalls = walls


# ------------------------------------------------------------- economy --

func economy() -> void:
	var p := world.player(team)
	var base := base()
	# No Town Center (nomad start, or ours was razed): raise one near our
	# villagers before anything else — without it nothing can be produced.
	if base == null:
		foundTownCenter(p)
		return

	# If we're ready to advance an age but can't afford it, stop spending food
	# (villagers + army) so the cost can actually be banked — otherwise the AI
	# dribbles every scrap into units and never reaches the next age.
	#
	# Time-boxed and progress-aware, because the naive version deadlocks. Saving
	# pauses villager production *and* caps the army at four; if the age-up is
	# not actually within reach, the economy stops growing, the cost never
	# becomes affordable, and the AI saves forever. Measured on highlands seed
	# 5: still Banner at twelve minutes with eleven villagers, an army of seven,
	# 1680 wood and 315 food, having banked toward Crown since minute four.
	#
	# So: only hold back when most of the cost is already in hand, and never for
	# more than a minute at a stretch. Falling out of it re-opens production for
	# long enough to actually earn the difference.
	#
	# "Close" alone left the AI in the Banner Age for whole matches: food was
	# spent the moment it arrived, so the bank never got to 55% of the Crown
	# cost and saving never began — measured at 30 minutes in Banner with a
	# thousand unspent gold. Having been *ready* for a while also counts now.
	var gate = ageUpCost(p)
	var affordable: bool = gate != null and world.canAfford(p.resources, gate)
	if gate == null:
		ageReadySince = -1.0
	elif ageReadySince < 0.0:
		ageReadySince = gameTime
	if gate == null or affordable:
		savingForAge = false
		saveGaveUpAt = -999.0
	else:
		var need: float = gate.food + gate.wood + gate.gold
		var have: float = minf(p.resources.food, gate.food) + minf(p.resources.wood, gate.wood) \
			+ minf(p.resources.gold, gate.gold)
		var close := need > 0.0 and (have / need >= 0.55 or gameTime - ageReadySince > AGE_READY_WAIT_SEC)
		var cooling := gameTime - saveGaveUpAt < AGE_SAVE_COOLDOWN
		if not savingForAge:
			if close and not cooling:
				savingForAge = true
				saveStartedAt = gameTime
		elif gameTime - saveStartedAt > AGE_SAVE_MAX_SEC:
			# A minute of holding back and still short: the bank is not filling from
			# an economy this size. Go and grow one.
			savingForAge = false
			saveGaveUpAt = gameTime

	# 0. The instant the age-up is affordable, research it — before villager or
	#    army production can nibble the banked cost back below it. (Otherwise the
	#    AI trains a villager the moment it can afford one and never quite banks
	#    the full age cost, stalling in an age forever.)
	if gate != null and not savingForAge:
		var tcs0 := myBuildings("town_center")
		if tcs0.size() > 0:
			var tc0: Entity = tcs0[0]
			world.research(team, tc0.id, ageTech())

	# 1. Keep villager production rolling (paused while saving for an age).
	var villagers := myUnits("villager")
	for tc: Entity in myBuildings("town_center"):
		if not savingForAge and villagers.size() < villagerGoal() and tc.productionQueue.size() < 2:
			world.trainUnit(team, tc.id, "villager")

	# 2. Houses well ahead of the pop block. Build several at once and start
	#    early — the old one-at-a-time, nearly-capped trigger left the AI
	#    perpetually pop-blocked with a tiny army.
	var housesBuilding := 0
	for h: Entity in myBuildings("house", false):
		if h.buildState != T.BuildState.Done:
			housesBuilding += 1
	var headroom := p.popCap - p.popUsed
	var wantAhead := 12 + _countBuildings("town_center", true) * 6 # grow faster with more bases
	if headroom < wantAhead and housesBuilding < 2 and p.popCap < world.POP_CAP_HARD:
		var h := placeNear("house", base.x, base.y, 3.0, 8.0)
		if h != null:
			assignBuilder(h)

	# 3. Gather balance.
	balanceGatherers(villagers)

	# 4. Drop-off camps near distant clusters.
	maybeBuildCamps(base)

	# 5. Farms once berries thin out.
	maybeBuildFarms(base, villagers.size())

	# 5a. Trade: fix a lopsided bank, and run carts if there is a route.
	marketStep(base, villagers.size())

	# 5b. Replace anything a raid has razed, ahead of normal build gates.
	rebuildStep(base)

	# 6. Military/tech buildings + age advances per the build order.
	buildOrder(p, base, villagers.size())

	# 6b. Put ranged units on Skirmish so they give ground while reloading
	#     instead of standing to be cut down. Doing this only for the player
	#     would hand one side free micro — the same trap the Blacksmith-only
	#     research below used to be.
	if world.tickCount % 40 == 0:
		var skirmishers: Array = []
		for e: Entity in _ownOf(""):
			if e.alive and e.kind == T.Kind.Unit and e.stance != T.Stance.Skirmish:
				var def = UNITS.get(e.type)
				if (def.range if def != null else 0.0) > 40.0:
					skirmishers.append(e.id)
		if skirmishers.size() > 0:
			world.setStance(skirmishers, T.Stance.Skirmish)

	# 7. Research, wherever it lives. This used to look only at the Blacksmith,
	#    which meant the AI never took a single economy tech — the Mill, Lumber
	#    Camp, Mining Camp, Town Centre, Stable, Archery Range and Market lines
	#    were all player-only. Now it researches from any building it owns,
	#    keeping enough in hand that teching never starves unit production.
	var UPGRADES: Dictionary = world.UPGRADES
	for id: String in UPGRADES:
		var up: Dictionary = UPGRADES[id]
		if p.upgrades.has(id) or p.age < up.age:
			continue
		if not world.canAfford(p.resources, up.cost):
			continue
		# A working float, so it doesn't spend its army money on research.
		if p.resources.gold < up.cost.gold + 200.0:
			continue
		if p.resources.food < up.cost.food + 150.0:
			continue
		if p.resources.wood < up.cost.wood + 150.0:
			continue
		var host: Entity = null
		for b: Entity in myBuildings(up.researchedAt):
			if b.productionQueue.size() == 0:
				host = b
				break
		if host == null:
			continue
		if world.research(team, host.id, id):
			break

	# 8. Expansion town center.
	if diff.expands and p.age >= 2 and _countBuildings("town_center", true) < 2 and \
			p.resources.wood > 500.0:
		var start: Dictionary = world.map.starts[team]
		var cX := world.worldW / 2.0
		var cY := world.worldH / 2.0
		var ex: float = start.x + (cX - start.x) * 0.45
		var ey: float = start.y + (cY - start.y) * 0.45
		var tc := placeNear("town_center", ex, ey, 0.0, 5.0)
		if tc != null:
			assignBuilder(tc, 3)

	# 8b. Fortify the front with a wall + gate once established.
	buildDefenses(base)

	# 9. Castle for late-game defense/elite production.
	if diff.buildsCastle and p.age >= 2 and _countBuildings("castle", true) == 0 and \
			world.canAfford(p.resources, BUILDINGS.castle.cost):
		var start: Dictionary = world.map.starts[team]
		var cX := world.worldW / 2.0
		var cY := world.worldH / 2.0
		var fx: float = start.x + (cX - start.x) * 0.3
		var fy: float = start.y + (cY - start.y) * 0.3
		var castle := placeNear("castle", fx, fy, 0.0, 6.0)
		if castle != null:
			assignBuilder(castle, 3)


## Lay a defensive wall line with a central gate across the approach from the
## base toward the map centre, backed by a watch tower. Built once, when the
## economy can spare a few builders. Kept short so it screens the front
## without sealing the AI's own resource lines.
func buildDefenses(base: Entity) -> void:
	if (not diff.buildsWalls and not turtles() and not rivalWalls) or wallsPlanned:
		return
	var p := world.player(team)
	if p.age < 1 or p.resources.wood < 160.0:
		return
	if myUnits("villager").size() < 16:
		return

	var cx := world.worldW / 2.0
	var cy := world.worldH / 2.0
	var dx := cx - base.x
	var dy := cy - base.y
	var dl := JS.hypot(dx, dy)
	if dl == 0.0 or is_nan(dl):
		dl = 1.0
	var ux := dx / dl
	var uy := dy / dl
	var px := -uy
	var py := ux
	var front := 4.5 * TILE
	var fx := base.x + ux * front
	var fy := base.y + uy * front

	var builders: Array = []
	var span := 3 # segments either side of the gate
	for i in range(-span, span + 1):
		var wx := fx + px * i * TILE
		var wy := fy + py * i * TILE
		var type := "gate" if i == 0 else "stone_wall"
		var b := world.placeBuilding(team, type, wx, wy)
		if b != null:
			builders.append(b.id)
	# A tower on a hill sees and shoots 20% further, which over a whole game is
	# the cheapest defensive upgrade available — so look before placing.
	var site := TerrainSense.bestTowerSite(world, fx - ux * TILE, fy - uy * TILE) if readsGround \
		else {"x": fx - ux * TILE, "y": fy - uy * TILE}
	var tower := world.placeBuilding(team, "watch_tower", site.x, site.y)
	if tower == null:
		tower = world.placeBuilding(team, "watch_tower", fx - ux * TILE, fy - uy * TILE)
	if tower != null:
		builders.append(tower.id)

	# Send a handful of villagers to raise it all (queued), then resume eco.
	if builders.size() > 0:
		var vills: Array = []
		for v: Entity in myUnits("villager"):
			if v.order.kind != T.OrderKind.Build:
				vills.append(v)
		vills = _sortByDist(vills, fx, fy)
		vills = vills.slice(0, 4)
		for i in builders.size():
			# vills[i % 0] is undefined in the TS: nobody to send.
			if vills.size() == 0:
				break
			var v: Entity = vills[i % vills.size()]
			world.issueBuildRepair([v.id], builders[i], true)
		# Only consider walls "planned" once something actually went down; if every
		# segment hit blocked tiles, retry next cycle instead of giving up forever.
		wallsPlanned = true


## The age's standard split, bent by what is actually in the bank: a pile of
## something means fewer hands on it, a shortage means more — and whatever
## the next age costs gets a pull of its own. Fixed shares were how an AI
## sat on a thousand gold with no food for ten minutes.
func bankAwareTargets(p: World.PlayerState) -> Dictionary:
	var base := gatherTargets(p.age)
	var gate = ageUpCost(p)
	var out := base.duplicate()
	var sum := 0.0
	for k: String in RES_KINDS:
		var bank: float = p.resources[k]
		var m := 0.45 if bank > 900.0 else (0.7 if bank > 600.0 else (1.3 if bank < 120.0 else 1.0))
		if gate != null:
			var want: float = gate[k]
			if want > 0.0 and bank < want:
				m *= 1.35
		out[k] = base[k] * m
		sum += out[k]
	for k in out:
		out[k] = out[k] / sum
	return out


func gatherTargets(age: int) -> Dictionary:
	if age == 0:
		return {"food": 0.5, "wood": 0.4, "gold": 0.1}
	if age == 1:
		return {"food": 0.42, "wood": 0.33, "gold": 0.25}
	return {"food": 0.36, "wood": 0.29, "gold": 0.35}


## "food" / "wood" / "gold", or "busy" / "idle".
func villagerTask(v: Entity) -> String:
	if v.order.kind == T.OrderKind.Gather or v.order.kind == T.OrderKind.Return:
		if v.carryKind != null and str(v.carryKind) != "":
			return v.carryKind
		var node = world.byId.get(v.order.target)
		if node != null:
			if node.type == "tree":
				return "wood"
			if node.type == "gold_mine":
				return "gold"
			return "food"
		return "food"
	if v.order.kind == T.OrderKind.Build or v.order.kind == T.OrderKind.Repair:
		return "busy"
	if v.order.kind == T.OrderKind.Idle:
		return "idle"
	return "busy"


## wantKind() inside balanceGatherers: the resource furthest below its target
## share, given the live counts.
func _wantKind(targets: Dictionary, counts: Dictionary, working: int) -> String:
	var total := float(working + 1)
	var deficits: Array = []
	for k: String in RES_KINDS:
		deficits.append([k, targets[k] - counts[k] / total])
	JS.sort_by_cmp(deficits, func(a, b): return b[1] - a[1])
	return deficits[0][0]


func balanceGatherers(villagers: Array) -> void:
	var p := world.player(team)
	var base := base()
	if base == null:
		return
	var counts := {"food": 0.0, "wood": 0.0, "gold": 0.0}
	var idle: Array = []
	var working := 0
	for v: Entity in villagers:
		var t := villagerTask(v)
		if t == "idle":
			idle.append(v)
		elif t != "busy":
			counts[t] += 1.0
			working += 1
	var targets := bankAwareTargets(p)

	# Put every idle villager to work; nudge one worker per cycle for balance.
	var toAssign := idle.slice(0, 6)
	if toAssign.size() == 0 and villagers.size() > 6:
		# Rebalance: find the kind with the largest surplus and move one worker.
		var total := float(maxi(1, working))
		var surplusKind := ""
		var surplus := 0.08
		for k: String in RES_KINDS:
			var s: float = counts[k] / total - targets[k]
			if s > surplus:
				surplus = s
				surplusKind = k
		if surplusKind != "":
			for u: Entity in villagers:
				if villagerTask(u) == surplusKind:
					toAssign.append(u)
					break

	var claimed := {}
	for v: Entity in toAssign:
		var kind := _wantKind(targets, counts, working)
		var node: Entity = null
		if kind == "food":
			node = foodNode(v, claimed)
		else:
			for nt: String in NODE_FOR[kind]:
				node = nearestResource(nt, v.x, v.y)
				if node != null:
					break
		if node != null and node.type == "farm":
			claimed[node.id] = true
		if node != null:
			world.issueGather([v.id], node.id)
			counts[kind] += 1.0
			working += 1


## Promptly replace a core building that's been destroyed. We only rebuild
## types we'd actually completed before (so it reacts to razes, not to a build
## order we haven't reached yet) and place near the base with two builders so
## the hole is filled fast. One rebuild per cycle keeps it from over-spending.
func rebuildStep(base: Entity) -> void:
	var p := world.player(team)
	# Remember everything currently standing so we know what "lost" means.
	for b: Entity in myBuildings():
		everBuilt[b.type] = true
	for type: String in REBUILDABLE:
		if not everBuilt.has(type):
			continue
		if _countBuildings(type, false) > 0:
			continue # still have one (or building)
		var def = BUILDINGS.get(type)
		if def == null or not world.canAfford(p.resources, def.cost):
			continue
		var b := placeNear(type, base.x, base.y, 3.0, 8.0)
		if b != null:
			assignBuilder(b, 2)
			say("rebuilding our %s." % str(def.name).to_lower(), b.x, b.y, 20.0)
			return # one per cycle


func maybeBuildCamps(base: Entity) -> void:
	var p := world.player(team)
	if p.resources.wood < 100.0:
		return
	var vil := myUnits("villager").size()
	var camps: Array = [
		["tree", "lumber_camp", "wood", mini(5, 2 + floori(vil / 12.0))],
		["gold_mine", "mining_camp", "gold", mini(3, 1 + floori(vil / 16.0))],
	]
	for c: Array in camps:
		var resType: String = c[0]
		var campType: String = c[1]
		var kind: String = c[2]
		var cap: int = c[3]
		if _countBuildings(campType, false) >= cap:
			continue # don't blanket the map
		# Go out onto the map: find the nearest resource node of this type that
		# isn't yet served by a drop-off, and plant a camp there. As patches near
		# base deplete the AI keeps pushing camps out to secure new ground.
		var best: Entity = null
		var bestD := INF
		for e: Entity in world.entities:
			if not e.alive or e.kind != T.Kind.Resource or e.type != resType or e.amount <= 0.0:
				continue
			var d := _distIfBelow(e.x, e.y, base.x, base.y, minf(bestD, TILE * 42.0))
			if d >= bestD or d > TILE * 42.0:
				continue # keep expansion within reach
			var drop = world.findNearestDropoff(team, e.x, e.y, kind)
			var dropDist := MathU.dist(drop.x, drop.y, e.x, e.y) if drop != null else INF
			if dropDist > TILE * 6.0:
				best = e
				bestD = d
		if best != null:
			var camp := placeNear(campType, best.x, best.y, 1.5, 3.5)
			if camp != null:
				assignBuilder(camp)
				return # one camp per cycle


## The Market: the answer to an economy that has too much of one thing.
##
## This is the other half of the deadlock the age-saving fix addresses. The AI
## regularly sat on 1600 wood and 300 food, or 1900 gold and 25 food, with a
## building in the game that converts one into the other and no idea it
## existed — measured, on the same seeds where it never attacked. Trading a
## surplus is not a clever play here, it is first aid.
##
## It also runs Trade Carts, which arrived as player-only economy: a route is
## gold for nothing but the wood to buy the cart and ground you can hold, and
## an opponent who never builds one is quietly playing a smaller game.
func marketStep(base: Entity, villagerCount: int) -> void:
	var p := world.player(team)
	if p.age < 1:
		return
	var markets := myBuildings("market")
	var r := p.resources
	var surplus := maxf(r.wood, r.food)
	var starving := minf(r.food, r.wood)

	# Build one when the bank is lopsided enough to be worth fixing, or simply
	# once the economy is big enough that trade routes start paying.
	if markets.size() == 0 and _countBuildings("market", false) == 0 \
			and r.wood > 260.0 and (surplus > 700.0 or villagerCount >= 16):
		var m := placeNear("market", base.x, base.y, 4.0, 8.0)
		if m != null:
			assignBuilder(m)
			return
	if markets.size() == 0:
		return

	# Rate-limited: the price moves against you with every trade, so dumping a
	# thousand wood in one think step is the worst way to sell it.
	if gameTime - lastTradeTime > 12.0 and surplus > 900.0 and starving < 260.0:
		lastTradeTime = gameTime
		if r.wood > 900.0 and r.food < 260.0:
			# Wood to gold to food. Two trades, but it is the only bridge there is.
			world.marketTrade(team, "sell_wood")
			if r.gold > 200.0:
				world.marketTrade(team, "buy_food")
		elif r.food > 900.0 and r.wood < 260.0:
			world.marketTrade(team, "sell_food")
			if r.gold > 200.0:
				world.marketTrade(team, "buy_wood")
	# Saving for an age and short of food, with gold to spare: buy it. Gold is
	# what piles up when an AI's army is idle, and food is what every age
	# advance needs most of.
	var gate = ageUpCost(p)
	if gate != null and gameTime - lastTradeTime > 8.0 and r.food < gate.food and \
			r.gold > gate.gold + 260.0:
		lastTradeTime = gameTime
		world.marketTrade(team, "buy_food")
		return

	# Gold is the one thing the Market cannot make from nothing, so a fat bank
	# of both raw materials and no gold is worth converting outright.
	if gameTime - lastTradeTime > 12.0 and r.gold < 180.0 and r.wood > 700.0:
		lastTradeTime = gameTime
		world.marketTrade(team, "sell_wood")

	# Trade carts, once there are two Markets to run between. A second Market
	# placed away from the first *is* the route, so build it deliberately.
	#
	# Gated on the size of the economy rather than on a difficulty flag. The
	# first version required `diff.counters`, which is about picking counter
	# units and is false for Knight — so the whole trade economy was switched
	# off for the tier most games are played at, and measured at zero carts
	# across six full-length matches. A weak AI is kept out of this by never
	# reaching the villager count, which is the honest gate.
	if markets.size() == 1 and r.wood > 400.0 and villagerCount >= 14:
		var away: Dictionary = world.map.starts[team]
		var m2 := placeNear("market", away.x, away.y, 12.0, 20.0)
		if m2 != null:
			assignBuilder(m2)
			return
	if markets.size() >= 2:
		var carts := myUnits("trade_cart").size()
		if carts < 4 and r.wood > 200.0 and r.gold > 100.0 and p.popUsed < p.popCap - 4.0:
			world.trainUnit(team, markets[0].id, "trade_cart")


func maybeBuildFarms(base: Entity, villagerCount: int) -> void:
	var p := world.player(team)
	# How much berry food is still within reach of base.
	var berryFood := 0.0
	for e: Entity in world.entities:
		if e.alive and e.kind == T.Kind.Resource and e.type == "berries" and e.amount > 0.0 and \
				_closer(e.x, e.y, base.x, base.y, TILE * 18.0):
			berryFood += e.amount
	var lowBerries := berryFood < 700.0
	# Food pressure: plenty of wood and no food is the signature of an economy
	# that has run out of things to eat and has nowhere to put the villagers.
	# Waiting for the berries to be provably gone, or for Crown Age, is too
	# late — reaching Crown Age *needs* the food.
	var foodStarved: bool = p.resources.food < 220.0 and p.resources.wood > 400.0

	# A mill anchors a compact farm economy — build it once the eco is rolling
	# or the berries are thinning, so the transition is ready.
	if _countBuildings("mill", false) == 0 and p.age >= 1 and p.resources.wood > 150.0 and \
			(lowBerries or foodStarved or villagerCount >= 12):
		var newMill := placeNear("mill", base.x, base.y, 3.0, 6.0)
		if newMill != null:
			assignBuilder(newMill)
			return

	# Farm transition: farms are the only food that doesn't run out for good, so
	# the AI must move onto them for the late game. Ramp toward ~one farm per 3
	# villagers once the berries thin out or we hit Crown Age — building a
	# couple at a time so the transition actually keeps up with a growing
	# population. Individual fields do wear out now, but auto-reseed puts them
	# back for the wood, so this only has to cover *growth*, not replacement.
	var transitioning := lowBerries or foodStarved or p.age >= 2
	# Enough fields for the share of villagers the economy puts on food — one
	# per three villagers left food-gatherers queueing for fields.
	var farmTarget := mini(28, maxi(3, int(ceil(villagerCount * 0.45)))) if transitioning else 0
	var mills := myBuildings("mill")
	var mill: Entity = mills[0] if mills.size() > 0 else null
	var cx := mill.x if mill != null else base.x
	var cy := mill.y if mill != null else base.y
	for n in 2:
		if _countBuildings("farm", false) >= farmTarget or p.resources.wood <= 80.0:
			break
		var farm := placeNear("farm", cx, cy, 2.0, 6.0)
		if farm != null:
			assignBuilder(farm)
		else:
			break


## `have(t)` in buildOrder.
func _have(t: String) -> bool:
	return _countBuildings(t, false) > 0


func buildOrder(p: World.PlayerState, base: Entity, villagerCount: int) -> void:
	# Hearth Age: barracks once the eco can carry it.
	if not _have("barracks") and villagerCount >= 6 and p.resources.wood >= 170.0:
		var b := placeNear("barracks", base.x, base.y, 4.0, 9.0)
		if b != null:
			assignBuilder(b)
	# Second Banner qualifier (advancing now needs any 2 of a set) — a Mill is a
	# cheap, always-useful drop-off that satisfies it.
	if p.age == 0 and _have("barracks") and not _have("mill") and \
			not _ageRequirementMet(1) and p.resources.wood >= 110.0:
		var b := placeNear("mill", base.x, base.y, 3.0, 7.0)
		if b != null:
			assignBuilder(b)

	# Advance to Banner.
	if p.age == 0 and diff.maxAge >= 1 and _ageRequirementMet(1) and \
			villagerCount >= floor(villagerGoal() * 0.55):
		var tcs := myBuildings("town_center")
		if tcs.size() > 0:
			world.research(team, tcs[0].id, ageTech())

	if p.age >= 1:
		if not _have("archery_range") and p.resources.wood >= 185.0:
			var b := placeNear("archery_range", base.x, base.y, 4.0, 9.0)
			if b != null:
				assignBuilder(b)
		if not _have("blacksmith") and p.resources.wood >= 160.0:
			var b := placeNear("blacksmith", base.x, base.y, 3.0, 8.0)
			if b != null:
				assignBuilder(b)
		# Stable is a Banner building now — gets cavalry (Horsemen/Raiders) going.
		if not _have("stable") and p.resources.wood >= 185.0:
			var b := placeNear("stable", base.x, base.y, 4.0, 9.0)
			if b != null:
				assignBuilder(b)
		# A defensive tower at the front.
		if _countBuildings("watch_tower", false) < (0 if diff.id == "squire" else 2) and \
				p.resources.wood > 220.0:
			var cX := world.worldW / 2.0
			var cY := world.worldH / 2.0
			var fx := base.x + (cX - base.x) * 0.22
			var fy := base.y + (cY - base.y) * 0.22
			var t := placeNear("watch_tower", fx, fy, 0.0, 4.0)
			if t != null:
				assignBuilder(t)

	# Advance to Crown (needs any 2 Banner buildings — we build several).
	if p.age == 1 and diff.maxAge >= 2 and _ageRequirementMet(2) and \
			villagerCount >= floor(villagerGoal() * 0.6):
		var tcs := myBuildings("town_center")
		if tcs.size() > 0:
			world.research(team, tcs[0].id, ageTech())

	if p.age >= 2:
		if not _have("siege_workshop") and p.resources.wood >= 210.0:
			var b := placeNear("siege_workshop", base.x, base.y, 4.0, 9.0)
			if b != null:
				assignBuilder(b)

	# Advance to Empire (a Siege Workshop or a Castle qualifies).
	if empireReady(p, villagerCount):
		var tcs := myBuildings("town_center")
		if tcs.size() > 0:
			world.research(team, tcs[0].id, ageTech())

	# Extra military production so a big economy can actually spend into a
	# full-pop army — one of each building can't fill the cap. Scales with the
	# villager count, Knight+ only (weaker tiers stay on a single line).
	if diff.maxAge >= 2 and p.age >= 1 and p.resources.wood >= 240.0:
		var milTypes: Array = []
		for t: String in ["barracks", "archery_range", "stable"]:
			if _have(t):
				milTypes.append(t)
		var milCount := 0
		for t: String in milTypes:
			milCount += _countBuildings(t, false)
		var target := mini(8, 2 + floori(villagerCount / 11.0))
		if milTypes.size() > 0 and milCount < target:
			JS.sort_by_cmp(milTypes, func(a, b): return _countBuildings(a, false) - _countBuildings(b, false))
			var b := placeNear(milTypes[0], base.x, base.y, 4.0, 12.0)
			if b != null:
				assignBuilder(b)


# ------------------------------------------------------------ military --

func armyUnits() -> Array:
	# Scouts are utility (kept out of battle lines); villagers/monks aren't army.
	var out: Array = []
	for e: Entity in _ownOf(""):
		if e.alive and e.kind == T.Kind.Unit and \
				e.type != "villager" and e.type != "monk" and e.type != "scout":
			out.append(e)
	return out


## Regicide: our own King, if he still lives.
func myKing() -> Entity:
	for e: Entity in _ownOf("king"):
		if e.alive:
			return e
	return null


## Regicide: the nearest enemy King we could march on.
func enemyKing() -> Entity:
	var hostile := _hostileMask()
	var b := base()
	var ox: float = b.x if b != null else world.map.starts[team].x
	var oy: float = b.y if b != null else world.map.starts[team].y
	var best: Entity = null
	var bestD := INF
	for e: Entity in world.entities:
		if not e.alive or e.type != "king" or hostile[e.team] == 0:
			continue
		var d := _distIfBelow(ox, oy, e.x, e.y, bestD)
		if d < bestD:
			bestD = d
			best = e
	return best


## A harassing AI keeps a couple of Raiders for eco raids (they're a Banner
## unit it would otherwise rush past).
func ensureRaiders() -> void:
	if (not diff.harasses and style != "rush") or savingForAge:
		return
	var p := world.player(team)
	if p.age < 1 or _countOwn("raider") >= 2:
		return
	if p.popUsed >= p.popCap or not world.canAfford(p.resources, UNITS.raider.cost):
		return
	for b: Entity in myBuildings("stable"):
		if b.productionQueue.size() < 2:
			world.trainUnit(team, b.id, "raider")
			return


## Keep one cheap Scout around for early map vision.
func trainScout() -> void:
	if not diff.scouts or gameTime > 360.0:
		return
	if _countOwn("scout") > 0:
		return
	var p := world.player(team)
	if p.popUsed >= p.popCap:
		return
	var tcs := myBuildings("town_center")
	if tcs.size() > 0 and tcs[0].productionQueue.size() < 2:
		world.trainUnit(team, tcs[0].id, "scout")


func military() -> void:
	trainScout()
	ensureRaiders()
	trainArmy()
	heroStep()
	scoutStep()

	# Regicide: guarding our own King pre-empts everything else when he's in
	# danger. Otherwise it just posts a light bodyguard and lets us play on.
	var kingEmergency := world.mode == "regicide" and kingDefense()
	var defended := not kingEmergency and defendStep()
	if not kingEmergency and not defended:
		bridgeStep()
		attackStep()
		harassStep()
	abilityStep()
	commanderPowerStep()


static func _ids(units: Array) -> Array:
	var out: Array = []
	for u: Entity in units:
		out.append(u.id)
	return out


## Regicide bodyguard. Keeps a small standing escort on our King and, when an
## enemy force closes on him, recalls the army and walks the King back into the
## safety of our base. Returns true only for a genuine emergency, so on a quiet
## front we still raid eco and grind the enemy army as normal.
func kingDefense() -> bool:
	var king := myKing()
	if king == null:
		return false # already lost — nothing to guard
	var threat := strengthNear(king.x, king.y, 400.0, false)
	var army := armyUnits()

	if threat > 50.0:
		attacking = false
		say("protect the King!", king.x, king.y, 16.0)
		# Walk the King home, behind whatever defences and bodies we have.
		var b := base()
		var hx: float = b.x if b != null else world.map.starts[team].x
		var hy: float = b.y if b != null else world.map.starts[team].y
		if MathU.dist(king.x, king.y, hx, hy) > 120.0:
			world.issueMove([king.id], hx, hy)
		# Collapse onto the King — everyone for a heavy push, else the spare line.
		var heavy := threat > 140.0
		var recall: Array = army
		if not heavy:
			recall = []
			for u: Entity in army:
				if u.order.kind != T.OrderKind.Attack:
					recall.append(u)
		if recall.size() > 0:
			world.issueMove(_ids(recall), king.x, king.y, false, true)
		return true

	# Quiet: keep a token escort posted with the King (second-line units only,
	# so we don't strip a wave that's already out the door).
	var guards := 0
	for u: Entity in army:
		if _closer(u.x, u.y, king.x, king.y, 220.0):
			guards += 1
	if guards < 3:
		var spare: Array = []
		for u: Entity in army:
			if u.order.kind != T.OrderKind.Attack and not _closer(u.x, u.y, king.x, king.y, 220.0):
				spare.append(u)
		spare = spare.slice(0, 3 - guards)
		if spare.size() > 0:
			var mx := king.x + rng.range(-40.0, 40.0)
			var my := king.y + rng.range(-40.0, 40.0)
			world.issueMove(_ids(spare), mx, my)
	return false


## Plant the commander banner amid a real fight when it's ready.
func commanderPowerStep() -> void:
	if not world.powerReady(team):
		return
	var fighters: Array = []
	for u: Entity in armyUnits():
		if u.order.kind == T.OrderKind.Attack or u.order.kind == T.OrderKind.AttackMove:
			fighters.append(u)
	if fighters.size() < 5:
		return
	var x := 0.0
	var y := 0.0
	for u: Entity in fighters:
		x += u.x
		y += u.y
	world.placeBanner(team, x / fighters.size(), y / fighters.size())


## Field the one Champion once the economy can spare the cost.
func heroStep() -> void:
	if not diff.usesAbilities or savingForAge:
		return
	var p := world.player(team)
	if p.heroState != "none":
		return
	if myUnits("villager").size() < 12:
		return
	if p.resources.gold < 120.0 or p.resources.food < 150.0:
		return
	var tcs := myBuildings("town_center")
	if tcs.size() > 0:
		world.trainUnit(team, tcs[0].id, "hero")


## Fire signature abilities on a knot of army units that are mid-fight.
func abilityStep() -> void:
	if not diff.usesAbilities:
		return
	if flub():
		return # fumbled the ability timing
	var fighters: Array = []
	for e: Entity in armyUnits():
		if world.ABILITIES.has(e.type) and e.abilityCooldown <= 0.0 and e.order.kind == T.OrderKind.Attack:
			fighters.append(e)
	# Fire in a real melee (2+), or whenever the Champion is in the thick of it.
	var hero: Entity = null
	for f: Entity in fighters:
		var def = UNITS.get(f.type)
		if def != null and def.get("hero", false):
			hero = f
			break
	if fighters.size() >= 2 or hero != null:
		world.useAbility(_ids(fighters))


## The research id for advancing: every advance swears an Oath, so the AI
## chooses one the way a player would — from its plan (its style) and from
## what it has scouted of the enemy. The choice is cached per age so the
## same decision is made however many times this is asked.
func ageTech() -> String:
	var next := world.player(team).age + 1
	if Oaths.oathsForAge(next).size() == 0:
		return "age"
	if str(oathChoice.get(next, "")) == "":
		oathChoice[next] = chooseOath(next)
	return "age:%s" % oathChoice[next]


func chooseOath(age: int) -> String:
	var options: Array = []
	for o in Oaths.oathsForAge(age):
		options.append(o.id)
	var s := seen
	var total: float = s.infantry + s.archer + s.cavalry + s.siege
	var st := style
	var score := {}
	for id in options:
		score[id] = 1.0
	var bump := func(id: String, v: float) -> void:
		if score.has(id):
			score[id] += v
	var share := func(n: float) -> float:
		return n / total if total >= 4.0 else 0.0
	if age == 1:
		# How to grow: a boomer farms, a rusher drills, a turtle digs in.
		bump.call("plough", 1.2 if st == "boom" else (0.5 if st == "balanced" else 0.0))
		bump.call("sword", 1.2 if st == "rush" else (0.35 if st == "balanced" else 0.0))
		bump.call("hearth", 1.2 if st == "turtle" else 0.0)
		bump.call("hearth", minf(0.8, seenWalls * 0.05) + share.call(s.cavalry) * 0.4)
	elif age == 2:
		# What the army should be: an answer to the army it has seen.
		bump.call("shield", share.call(s.cavalry) * 2.2 + share.call(s.siege) * 0.8 + (0.6 if st == "turtle" else 0.0))
		bump.call("bow", share.call(s.infantry) * 2.0 + (0.4 if st == "boom" else 0.0))
		bump.call("lance", share.call(s.archer) * 2.0 + share.call(s.siege) * 0.6 + (0.6 if st == "rush" else 0.0))
	elif age == 3:
		# What to be remembered for: breaking walls, wealth, or the realm itself.
		bump.call("engine", minf(1.6, seenWalls / 5.0) + (0.6 if st == "rush" else 0.0))
		bump.call("coin", (0.9 if st == "boom" else 0.0) + minf(0.6, myUnits("trade_cart").size() * 0.15))
		bump.call("crown", (0.8 if st == "turtle" else 0.0) + (0.45 if st == "balanced" else 0.0))
	# A little seeded variety, so a lobby of identical AIs doesn't swear alike.
	for id in options:
		score[id] += rng.range(0.0, 0.35)
	var pick: String = options[0]
	for i in range(1, options.size()):
		if score[options[i]] > score[pick]:
			pick = options[i]
	return pick


## shift() in withOathUnits.
static func _shift(out: Dictionary, from: String, to: String, frac: float, floor_: float = 0.0) -> void:
	var v: float = out.get(from, 0.0)
	out[from] = v * (1.0 - frac)
	out[to] = float(out.get(to, 0.0)) + v * frac + floor_


## Swap in the signature unit of each Oath sworn: it takes over most of the
## role it shares with a stock unit (the Lancer most of the Knight's, the
## Halberdier the pikes'). Applied after countering, so a counter that asks
## for pikes gets halberds from a Shield-sworn realm.
func withOathUnits(comp: Dictionary) -> Dictionary:
	var p := world.player(team)
	var out := comp.duplicate()
	for id in p.oaths:
		if id == "sword" and p.age >= 1:
			_shift(out, "militia", "sworn_blade", 0.7, 0.08)
		if id == "lance":
			_shift(out, "knight", "lancer", 0.65, 0.06)
		if id == "bow":
			_shift(out, "crossbow", "ranger", 0.5, 0.05)
			_shift(out, "archer", "ranger", 0.5)
		if id == "shield":
			_shift(out, "pikeman", "halberdier", 1.0, 0.06)
			_shift(out, "spearman", "halberdier", 0.8)
		if id == "engine":
			_shift(out, "trebuchet", "great_bombard", 0.6, 0.04)
			if seenWalls >= 4:
				out["great_bombard"] = float(out.get("great_bombard", 0.0)) + 0.06
		if id == "crown":
			_shift(out, "twohand", "royal_guard", 0.7, 0.08)
	return out


## Composition weights, optionally countering what we've seen.
func composition() -> Dictionary:
	return withFactionUnits(withOathUnits(baseComposition()))


## share() in withFactionUnits.
static func _factionShare(p: World.PlayerState, out: Dictionary, from: Array, to: String, frac: float, age: int) -> void:
	if p.age < age:
		return
	for k: String in from:
		var v: float = out.get(k, 0.0)
		if v == 0.0:
			continue
		out[k] = v * (1.0 - frac)
		out[to] = float(out.get(to, 0.0)) + v * frac


## Field the faction's own units: a replacement takes all of the role it
## replaces (the shared unit can't be trained at all), and an extra takes a
## share of the role it plays best. Applied after the Oath swaps, so a Legion
## that swore the Sword splits its swordsmen between the two.
func withFactionUnits(comp: Dictionary) -> Dictionary:
	var p := world.player(team)
	var f := Factions.factionOf(p.faction)
	var out := comp.duplicate()
	var replaces: Dictionary = f.replaces
	for base: String in replaces:
		var own: String = replaces[base]
		if float(out.get(base, 0.0)) == 0.0:
			continue
		out[own] = float(out.get(own, 0.0)) + out[base]
		out.erase(base)
	for extra: String in f.extra:
		if extra == "yeoman":
			_factionShare(p, out, ["crossbow", "archer"], "yeoman", 0.5, 2)
		elif extra == "shieldbearer":
			_factionShare(p, out, ["militia", "legionary", "spearman"], "shieldbearer", 0.25, 1)
		elif extra == "berserker":
			_factionShare(p, out, ["militia", "twohand", "sworn_blade"], "berserker", 0.45, 1)
		elif extra == "horse_archer":
			_factionShare(p, out, ["archer", "skirmisher", "javelin"], "horse_archer", 0.55, 1)
		elif extra == "cataphract":
			_factionShare(p, out, ["knight", "lancer"], "cataphract", 0.45, 2)
	return out


func baseComposition() -> Dictionary:
	var p := world.player(team)
	var base := {}
	if p.age == 0:
		base.militia = 0.7
		base.spearman = 0.3
	elif p.age == 1:
		base.militia = 0.32
		base.archer = 0.26
		base.spearman = 0.12
		base.skirmisher = 0.08
		base.horseman = 0.1 # fast melee cavalry
		base.javelin = 0.07 # anti-cavalry ranged
		base.raider = 0.1 # eco harass
	elif p.age == 2:
		base.knight = 0.28
		base.crossbow = 0.22
		base.twohand = 0.13 # heavy infantry top end
		base.pikeman = 0.09 # anti-cavalry
		base.catapult = 0.1
		base.ram = 0.06
		base.horseman = 0.05 # anti-siege melee cav
		if seenWalls >= 4 and _countBuildings("siege_workshop", true) > 0:
			base.ram += 0.14
			base.catapult += 0.12
	else:
		# Empire: gunpowder and the great engines join the Crown line.
		base.knight = 0.24
		base.crossbow = 0.14
		base.handcannon = 0.1 # gunpowder line
		base.twohand = 0.12
		base.pikeman = 0.08
		base.catapult = 0.06
		base.ram = 0.05
		base.trebuchet = 0.05
		base.horseman = 0.05
		# The enemy is walling up — bring the wall-breakers.
		if seenWalls >= 4 and _countBuildings("siege_workshop", true) > 0:
			base.ram += 0.14
			base.trebuchet += 0.1
			base.catapult += 0.08
	if not diff.counters and expected == null:
		return base

	# Pool scouted intel across the alliance: counter the strongest read of the
	# enemy army that any teammate has, not just what we personally can see.
	# A rival also counters what it remembers, before it has seen anything.
	var s := seen.duplicate()
	if expected != null:
		for k: String in SEEN_KEYS:
			s[k] = maxf(s[k], expected[k])
	for ally in alliedAIs():
		s.infantry = maxf(s.infantry, ally.seen.infantry)
		s.archer = maxf(s.archer, ally.seen.archer)
		s.cavalry = maxf(s.cavalry, ally.seen.cavalry)
		s.siege = maxf(s.siege, ally.seen.siege)
	var total: float = s.infantry + s.archer + s.cavalry + s.siege
	if total < 3.0:
		return base
	# Blend the base comp with counters to the observed army.
	var counter := {}
	_add(counter, "archer", (s.infantry / total) * 0.5)
	_add(counter, "handcannon", (s.infantry / total) * 0.4) # gunpowder shreds infantry (Empire)
	_add(counter, "skirmisher", (s.archer / total) * 0.4)
	_add(counter, "javelin", (s.archer / total) * 0.2)
	_add(counter, "knight", (s.archer / total) * 0.3 + (s.siege / total) * 0.4)
	_add(counter, "horseman", (s.siege / total) * 0.4) # melee anti-siege cav
	_add(counter, "spearman", (s.cavalry / total) * 0.45)
	_add(counter, "pikeman", (s.cavalry / total) * 0.55) # best anti-cav (Crown)
	_add(counter, "militia", (s.archer / total) * 0.2 + (s.siege / total) * 0.2)
	var out := {}
	var keys: Array = base.keys()
	for k in counter:
		if not base.has(k):
			keys.append(k)
	for k: String in keys:
		out[k] = float(base.get(k, 0.0)) * 0.45 + float(counter.get(k, 0.0)) * 0.55
	return out


## add() in baseComposition.
static func _add(counter: Dictionary, k: String, v: float) -> void:
	counter[k] = float(counter.get(k, 0.0)) + v


func trainArmy() -> void:
	# Hold army production too while banking for an age — get to the next tier
	# first; a Hearth-Age army just feeds a Banner one. (Keep a token defence.)
	if savingForAge and armyUnits().size() >= 4:
		return
	var p := world.player(team)
	var comp := composition()
	var army := armyUnits()
	# Grow the army steadily; the greedy trainer below only spends what's
	# affordable, so this naturally scales with the economy (toward the pop cap
	# late game) without starving villager/age production.
	var want := maxf(armySize() * 1.4, army.size() + 5)

	var countByType := {}
	for u: Entity in army:
		countByType[u.type] = int(countByType.get(u.type, 0)) + 1

	# Greedy: train the unit type with the biggest deficit that we can afford.
	var deficits: Array = []
	for type: String in comp:
		var d: float = comp[type] * want - int(countByType.get(type, 0))
		if d > 0.5 and UNITS.has(type) and p.age >= UNITS[type].age and world.canFieldType(p, type):
			deficits.append([type, d])
	JS.sort_by_cmp(deficits, func(a, b): return b[1] - a[1])

	for entry: Array in deficits:
		var type: String = entry[0]
		var def: Dictionary = UNITS[type]
		var producers: Array = []
		for b: Entity in myBuildings(def.trainedAt):
			if b.productionQueue.size() < 2:
				producers.append(b)
		for b: Entity in producers:
			if not world.trainUnit(team, b.id, type):
				break


func scoutStep() -> void:
	if not diff.scouts:
		return
	if gameTime - lastScoutTime < 120.0:
		return
	if flub():
		return # forgot to send the scout this time
	var scout = world.byId.get(scoutId)
	if scout != null and scout.alive and scout.order.kind != T.OrderKind.Idle:
		return
	# Prefer the dedicated Scout, then a spare soldier, then an early villager.
	var army := armyUnits()
	var unit: Entity = null
	for u: Entity in myUnits("scout"):
		if u.order.kind == T.OrderKind.Idle:
			unit = u
			break
	if unit == null:
		for u: Entity in army:
			if u.order.kind == T.OrderKind.Idle:
				unit = u
				break
	if unit == null and gameTime < 180.0:
		var vills := myUnits("villager")
		if vills.size() > 0:
			unit = vills[0]
	if unit == null:
		return
	scoutId = unit.id
	lastScoutTime = gameTime
	var enemyStart: Dictionary = world.map.starts[enemyTeam()]
	var home: Dictionary = world.map.starts[team]
	# Arc to the enemy base edge, then home.
	var sx: float = enemyStart.x + rng.range(-200.0, 200.0)
	var sy: float = enemyStart.y + rng.range(-200.0, 200.0)
	world.issueMove([unit.id], sx, sy)
	world.issueMove([unit.id], home.x, home.y, true)


## Returns true if we are busy defending.
func defendStep() -> bool:
	var hostile := _hostileMask()
	var buildings := _Buckets.new(myBuildings("", false), 320.0)
	var threatX := 0.0
	var threatY := 0.0
	var threats := 0
	for e: Entity in world.entities:
		if not e.alive or hostile[e.team] == 0 or e.kind != T.Kind.Unit:
			continue
		if e.type == "villager":
			continue
		# (TS: any of our buildings within 320 — `buildings.some(...)`.)
		if buildings.anyCloser(e.x, e.y):
			threatX += e.x
			threatY += e.y
			threats += 1
	if threats < 2:
		return allyDefendStep()
	threatX /= threats
	threatY /= threats
	# A serious raid — call for the cavalry.
	if threats >= 3:
		say("my base is under attack — I need help!", threatX, threatY, 18.0)
	# Villagers caught in the raid take cover and scatter.
	var panicked: Array = []
	for v: Entity in myUnits("villager"):
		if v.abilityCooldown <= 0.0 and _closer(v.x, v.y, threatX, threatY, 360.0):
			panicked.append(v.id)
	if panicked.size() > 0:
		world.useAbility(panicked)
	attacking = false
	var defenders: Array = []
	for u: Entity in armyUnits():
		if u.order.kind != T.OrderKind.Attack:
			defenders.append(u)
	if defenders.size() > 0:
		# Meet them on ground worth standing on — but only just. Defence is about
		# arriving, not about standing somewhere nice: a raid is eating villagers
		# while the relief force admires a ridge, so this is a tight search (five
		# tiles) that steps onto the rise you were nearly on anyway and never
		# trades interception for elevation.
		var dx2 := 0.0
		var dy2 := 0.0
		for u: Entity in defenders:
			dx2 += u.x
			dy2 += u.y
		dx2 /= defenders.size()
		dy2 /= defenders.size()
		var meet := TerrainSense.bestGroundNear(world, threatX, threatY, {"radius": 160.0, "from": {"x": dx2, "y": dy2}, "detour": 9.0}) \
			if readsGround else {"x": threatX, "y": threatY}
		world.issueMove(_ids(defenders), meet.x, meet.y, false, true)
	return true


## Our own base is safe — if a teammate is being overrun, march a relief force
## to the raid. We commit our spare army (idle/defending units, not those
## already mid-attack) so we don't abandon an assault we've already launched,
## and rate-limit so the relief column doesn't jitter in place.
func allyDefendStep() -> bool:
	var hostile := _hostileMask()
	if not hasAlly():
		return false
	if gameTime - lastAllyHelpTime < 8.0:
		return false

	# Find the worst raid on an allied (non-self) base.
	var bestX := 0.0
	var bestY := 0.0
	var bestThreats := 0
	for t in world.numTeams:
		if t == team or not world.areAllied(team, t):
			continue
		var p := world.player(t)
		if p == null or p.defeated:
			continue
		var allyList: Array = []
		for e: Entity in world.entities:
			if e.alive and e.team == t and e.kind == T.Kind.Building:
				allyList.append(e)
		var allyBuildings := _Buckets.new(allyList, 340.0)
		var tx := 0.0
		var ty := 0.0
		var n := 0
		for e: Entity in world.entities:
			if not e.alive or hostile[e.team] == 0 or e.kind != T.Kind.Unit or e.type == "villager":
				continue
			if allyBuildings.anyCloser(e.x, e.y):
				tx += e.x
				ty += e.y
				n += 1
		if n > bestThreats:
			bestThreats = n
			bestX = tx / n
			bestY = ty / n
	# Only bother for a real raid (3+ attackers) and if we have a force to send.
	if bestThreats < 3:
		return false
	var relief: Array = []
	for u: Entity in armyUnits():
		if u.order.kind != T.OrderKind.Attack:
			relief.append(u)
	if relief.size() < 4:
		return false

	lastAllyHelpTime = gameTime
	attacking = false
	say("hold on — sending help your way!", bestX, bestY, 18.0)
	world.issueMove(_ids(relief), bestX, bestY, false, true)
	return true


## Mid-assault: keep stalled units advancing toward the highest-value enemy we
## can see — fleeing villagers first (the eco kill), then the Town Center and
## production, then anything. If we can't see a thing, march into their start
## to scout and commit rather than idling at the edge. Units already swinging
## are left on their target, so this presses the attack without thrashing.
func pressAttack() -> void:
	var hostile := _hostileMask()
	var army := armyUnits()
	if army.size() == 0:
		return
	var idle: Array = []
	for u: Entity in army:
		if u.order.kind != T.OrderKind.Attack:
			idle.append(u)
	if idle.size() == 0:
		return # everyone's already fighting — good
	var cx := 0.0
	var cy := 0.0
	for u: Entity in army:
		cx += u.x
		cy += u.y
	cx /= army.size()
	cy /= army.size()
	var ids := _ids(idle)

	# 0. Regicide: take the King only when he's there for the taking. If his
	#    escort is light next to our striking force, cut him down and end it;
	#    otherwise fall through and keep wearing down their eco/army — as that
	#    fight is won his guard thins out and this opens up on its own.
	if world.mode == "regicide":
		var ek := enemyKing()
		if ek != null and world.fogAt(team, ek.x, ek.y) != 0:
			var guards := strengthNear(ek.x, ek.y, 320.0, false)
			var ours := strengthNear(cx, cy, 360.0, true)
			if guards < 45.0 or guards < ours * 0.5:
				world.issueAttack(ids, ek.id)
				return

	# 1. Smash whatever we're piled against (wall, gate, tower, any building) so
	#    a defended base can't simply wall us out — we breach and pour in.
	var blocker: Entity = null
	var blockD := 150.0
	for e: Entity in world.entities:
		if not e.alive or hostile[e.team] == 0 or e.kind != T.Kind.Building:
			continue
		if world.fogAt(team, e.x, e.y) == 0:
			continue
		var d := _distIfBelow(cx, cy, e.x, e.y, blockD)
		if d < blockD:
			blockD = d
			blocker = e
	if blocker != null:
		world.issueAttack(ids, blocker.id)
		return

	# 2. Otherwise drive at the highest-value target we can see: fleeing
	#    villagers (eco kill), then Town Centre / production, then anything.
	var best: Entity = null
	var bestScore := -INF
	for e: Entity in world.entities:
		if not e.alive or e.team == team or hostile[e.team] == 0:
			continue
		if e.kind != T.Kind.Unit and e.kind != T.Kind.Building:
			continue
		if not world.visibleTo(team, e):
			continue # unseen, or hiding
		var pri: float
		if e.kind == T.Kind.Unit:
			pri = 130.0 if e.type == "villager" else (90.0 if e.type == "monk" else 55.0)
		else:
			pri = 100.0 if e.type == "town_center" else (70.0 if PRODUCTION.has(e.type) else 35.0)
		var score := pri - MathU.dist(cx, cy, e.x, e.y) * 0.04
		if score > bestScore:
			bestScore = score
			best = e
	if best != null:
		world.issueAttack(ids, best.id)
		return

	# 3. Blind — march into their start to scout and commit.
	var s: Dictionary = world.map.starts[focusEnemy()]
	world.issueMove(ids, s.x, s.y, false, true)


## Focus fire: when our army is locked in a fight, drive the melee line onto the
## single most valuable enemy unit nearby (siege > champion > monk > ranged,
## with a bonus for the wounded) so threats die one at a time instead of damage
## spreading. Ranged units keep auto-firing/kiting. Re-issues only when the
## target changes, so it's cheap to call every cycle. Returns true when it took
## over a fight (so the caller skips the generic base-press this cycle).
func focusFireStep() -> bool:
	var hostile := _hostileMask()
	if not diff.focusFire:
		return false
	var army := armyUnits()
	if army.size() < 4:
		return false
	var cx := 0.0
	var cy := 0.0
	for u: Entity in army:
		cx += u.x
		cy += u.y
	cx /= army.size()
	cy /= army.size()

	var best: Entity = null
	var bestScore := -INF
	for e: Entity in world.entities:
		if not e.alive or e.kind != T.Kind.Unit or hostile[e.team] == 0 or e.type == "villager":
			continue
		var d := _distIfBelow(cx, cy, e.x, e.y, 260.0)
		if d > 260.0:
			continue # only what we're actually fighting
		if not world.visibleTo(team, e):
			continue
		var wounded := (1.0 - e.hp / maxf(1.0, e.maxHp)) * 45.0 # finish low-HP targets
		var score := armyTargetPriority(e.type) + wounded - d * 0.05
		if score > bestScore:
			bestScore = score
			best = e
	if best == null:
		lastFocusId = -1
		return false

	var melee: Array = []
	for u: Entity in army:
		var def = UNITS.get(u.type)
		if (def.range if def != null else 0.0) < 60.0:
			melee.append(u)
	var ids := _ids(melee if melee.size() >= 3 else army)
	if best.id != lastFocusId:
		world.issueAttack(ids, best.id)
		lastFocusId = best.id
	return true


func attackStep() -> void:
	var hostile := _hostileMask()
	var army := armyUnits()
	var armyPop := 0.0
	for u: Entity in army:
		var def = UNITS.get(u.type)
		armyPop += def.pop if def != null else 1.0
	if attacking:
		# Wave over only when the force is genuinely spent.
		if armyPop < armySize() * 0.3:
			attacking = false
			return
		# Smart retreat: bail only from a genuinely overwhelming force (keep the
		# army alive) — never from a garrison we should be steamrolling.
		var fighting: Array = []
		for u: Entity in army:
			if u.order.kind == T.OrderKind.Attack or u.order.kind == T.OrderKind.AttackMove:
				fighting.append(u)
		if fighting.size() >= 5 and gameTime - lastRetreatTime > 14.0:
			var fx := 0.0
			var fy := 0.0
			for u: Entity in fighting:
				fx += u.x
				fy += u.y
			fx /= fighting.size()
			fy /= fighting.size()
			var mine := strengthNear(fx, fy, 700.0, true) # our force + reinforcements
			var foe := strengthNear(fx, fy, 440.0, false)
			if foe > 120.0 and mine < foe * 0.4:
				var home: Dictionary = world.map.starts[team]
				world.issueMove(_ids(army), home.x, home.y, false, false)
				attacking = false
				lastRetreatTime = gameTime
				lastWaveTime = gameTime - cadence() * 0.55 # regroup, then come again
				return
		# Keep driving for the kill: re-command any stalled units toward the
		# enemy's villagers / Town Center / production instead of loitering at the
		# wall. This is what makes the AI actually finish a base.
		# Concentrate fire on the deadliest/weakest enemy in the melee (high tiers
		# only); fall back to driving at the base when no defenders are close.
		var focusing := focusFireStep()
		if not focusing and gameTime - lastPressTime > 1.5:
			lastPressTime = gameTime
			pressAttack()
		return
	# Pile onto a teammate's attack even a little under-strength: a combined
	# two-army hit lands far harder than two solo waves arriving apart.
	var joiningAlly := allyAssaulting() and armyPop >= armySize() * 0.6 and \
		gameTime - lastWaveTime > cadence() * 0.4
	if armyPop < armySize() and not joiningAlly:
		return
	if not joiningAlly and gameTime - lastWaveTime < cadence():
		return

	# Fumbled the timing — dither a few seconds before committing the push.
	if flub():
		lastWaveTime = gameTime - cadence() * 0.7
		return

	lastWaveTime = gameTime
	attacking = true

	# Concentrate on the alliance's shared focus enemy (the nearest foe in 1v1).
	var target := focusEnemy()
	# Primary target: nearest known enemy building, else their start position.
	var enemyBuildings: Array = []
	for e: Entity in world.entities:
		if e.alive and e.team == target and e.kind == T.Kind.Building and \
				world.fogAt(team, e.x, e.y) != 0:
			enemyBuildings.append(e)
	var start: Dictionary = world.map.starts[target]
	var tx: float = start.x
	var ty: float = start.y
	var base := base()
	if enemyBuildings.size() > 0 and base != null:
		var bx := base.x
		var by := base.y
		enemyBuildings = _sortByDist(enemyBuildings, bx, by)
		tx = enemyBuildings[0].x
		ty = enemyBuildings[0].y

	# Sloppier tiers mis-stage their push (fat fingers); top tiers hit clean.
	var jit: float = diff.executionError * 320.0
	if jit > 1.0:
		tx += rng.range(-jit, jit)
		ty += rng.range(-jit, jit)

	# Call the assault so the player hears a teammate's attack coming in.
	if army.size() > 0:
		var cx := 0.0
		var cy := 0.0
		for u: Entity in army:
			cx += u.x
			cy += u.y
		cx /= army.size()
		cy /= army.size()
		say("pressing the attack from the %s!" % compass(tx, ty, cx, cy), cx, cy, 16.0)

	# Siege engines peel off to smash the nearest fortification on the approach
	# so the melee isn't left chipping a stone wall with swords.
	var siege: Array = []
	for u: Entity in army:
		var def = UNITS.get(u.type)
		if def != null and def.armorClass == "siege":
			siege.append(u)
	var melee: Array = army
	if siege.size() > 0:
		var forts: Array = []
		for e: Entity in world.entities:
			if e.alive and e.kind == T.Kind.Building and hostile[e.team] == 1 and \
					world.fogAt(team, e.x, e.y) != 0 and FORT_TYPES.has(e.type):
				forts.append(e)
		if forts.size() > 0:
			var ttx := tx
			var tty := ty
			forts = _sortByDist(forts, ttx, tty)
			world.issueAttack(_ids(siege), forts[0].id)
			melee = []
			for u: Entity in army:
				var def = UNITS.get(u.type)
				if def == null or def.armorClass != "siege":
					melee.append(u)

	var ids := _ids(melee)
	# Where the army is now, so the ground advice knows which way it is coming
	# from and what it can actually reach.
	var ax := 0.0
	var ay := 0.0
	for u: Entity in melee:
		ax += u.x
		ay += u.y
	if melee.size() > 0:
		ax /= melee.size()
		ay /= melee.size()
	var from := {"x": ax, "y": ay}
	# Read the ground. A wave that walks straight at a Town Centre arrives one
	# unit at a time; one that gathers on the ridge outside first arrives as an
	# army — and if there is high ground on the approach it takes it, because
	# that is 20% more range for the price of walking there.
	var useGround: bool = readsGround and (diff.counters or style != "rush")
	var candidate = TerrainSense.stagingPoint(world, {"x": tx, "y": ty}, from) \
		if useGround and melee.size() > 0 else null
	var stagedOnHill: bool = TerrainSense.isHighGround(world, candidate.x, candidate.y) if candidate != null else false
	# Stage only when the ground is worth stopping for. Staging adds a whole
	# extra marching leg to every wave, and a wave that stops to form up on an
	# ordinary flat patch has merely arrived late; on a hill the stop buys 20%
	# range for the fight that follows. Head-to-head this made no measurable
	# difference either way (see ROADMAP), so it is chosen for being the
	# cheaper of two indistinguishable options rather than for winning more.
	var stage = candidate if stagedOnHill else null

	if diff.counters and melee.size() >= 10:
		# Two-prong: main force + flank.
		var cut := floori(ids.size() * 0.7)
		var main := ids.slice(0, cut)
		var flank := ids.slice(cut)
		if stage != null:
			world.issueMove(main, stage.x, stage.y, false, true)
		world.issueMove(main, tx, ty, stage != null, true)
		var side := 1 if rng.bool_() else -1
		var fp := TerrainSense.flankPoint(world, {"x": tx, "y": ty}, from, side) if readsGround \
			else {"x": tx + side * 420.0, "y": ty - side * 420.0}
		world.issueMove(flank, fp.x, fp.y, false, true)
		world.issueMove(flank, tx, ty, true, true)
	elif ids.size() > 0:
		if stage != null:
			world.issueMove(ids, stage.x, stage.y, false, true)
		world.issueMove(ids, tx, ty, stage != null, true)
	if stagedOnHill:
		say("forming up on the high ground.", stage.x, stage.y, 24.0)


## Bridge the ford the army keeps wading through.
##
## Bridges shipped as something only the player understood: the AI would march
## every wave through shallows at half speed, past a crossing it could have
## bought once for a hundred wood. That is the same failure as not knowing
## hills exist and a more embarrassing one, because a bridge is a building the
## AI owns and simply never used.
##
## Deliberately tied to the route it actually takes — the line from its base
## toward the enemy — rather than "bridge every ford". A crossing nowhere near
## the fighting is a hundred wood set on fire.
func bridgeStep() -> void:
	if not readsGround:
		return
	var p := world.player(team)
	var def = BUILDINGS.get("bridge")
	if def == null or not world.canAfford(p.resources, def.cost):
		return
	# Rate-limited hard: this walks a line and scans buildings, and a ford does
	# not appear halfway through a match.
	if gameTime - lastBridgeTime < 45.0:
		return
	var base := base()
	if base == null:
		return
	# Don't start a second one while the first is still going up — an AI that
	# queues four bridges at a ford has spent four hundred wood on one crossing.
	for e: Entity in _ownOf("bridge"):
		if e.alive and e.buildState != T.BuildState.Done:
			return

	var target: Dictionary = world.map.starts[focusEnemy()]
	var ford = TerrainSense.fordCrossing(world, base, target)
	# A puddle is not worth a hundred wood; a real ford is several cells of
	# half-speed ground with an army walking through it every wave.
	if ford == null or ford.width < 4:
		return

	lastBridgeTime = gameTime
	# Nudge around the crossing point: the exact midpoint may be one cell short
	# of a full water footprint, and a bridge needs every cell to be shallows.
	for attempt in 10:
		var jx := 0.0 if attempt == 0 else rng.range(-TILE * 2.0, TILE * 2.0)
		var jy := 0.0 if attempt == 0 else rng.range(-TILE * 2.0, TILE * 2.0)
		var b := world.placeBuilding(team, "bridge", ford.x + jx, ford.y + jy)
		if b != null:
			assignBuilder(b, 2)
			say("bridging the ford.", b.x, b.y, 20.0)
			return


## idle(t) in harassStep: our idle units of type `t`.
func _idleOf(t: String) -> Array:
	var out: Array = []
	for u: Entity in myUnits(t):
		if u.order.kind == T.OrderKind.Idle:
			out.append(u)
	return out


## Raid: send fast units to butcher the enemy's villagers / eco.
func harassStep() -> void:
	var hostile := _hostileMask()
	if not diff.harasses and style != "rush":
		return
	var cool := 55.0 if style == "rush" else 85.0
	if gameTime - lastHarassTime < cool:
		return
	if flub():
		return # skipped the raid
	# Dedicated raiders first (Raider/Horseman), then Knights, then any spare Man-at-Arms.
	var raiders: Array = _idleOf("raider") + _idleOf("horseman") + _idleOf("knight")
	if raiders.size() < 3:
		raiders = raiders + _idleOf("militia")
	if raiders.size() < 3:
		return
	lastHarassTime = gameTime
	var ids := _ids(raiders.slice(0, 4))
	# A crossing the enemy paid for is the cheapest thing on the map to take
	# away from them: it cost a hundred wood and a builder's time, it is barely
	# defended by construction because it stands in water, and burning it puts
	# their next wave back into the shallows at half speed. Raiders are exactly
	# the wrong unit to besiege a Castle with and exactly the right one for this.
	if readsGround:
		var spans: Array = []
		for e: Entity in world.entities:
			if e.alive and e.kind == T.Kind.Building and e.type == "bridge" and hostile[e.team] == 1 and \
					world.fogAt(team, e.x, e.y) != 0:
				spans.append(e)
		var home := base()
		if spans.size() > 0 and home != null:
			var hx := home.x
			var hy := home.y
			spans = _sortByDist(spans, hx, hy)
			world.issueAttack(ids, spans[0].id)
			say("burn their bridge.", spans[0].x, spans[0].y, 20.0)
			return
	# Prefer a visible enemy villager; otherwise sweep their base eco.
	var vills: Array = []
	for e: Entity in world.entities:
		if e.alive and e.kind == T.Kind.Unit and hostile[e.team] == 1 and e.type == "villager" and \
				world.visibleTo(team, e):
			vills.append(e)
	var base := base()
	if vills.size() > 0 and base != null:
		var bx := base.x
		var by := base.y
		vills = _sortByDist(vills, bx, by)
		world.issueAttack(ids, vills[0].id)
		return
	var start: Dictionary = world.map.starts[enemyTeam()]
	var sx: float = start.x + rng.range(-300.0, 300.0)
	var sy: float = start.y + rng.range(-300.0, 300.0)
	world.issueMove(ids, sx, sy, false, true)
