## sim/world.ts — the deterministic simulation world. Fixed-tick (SIM_HZ). All
## gameplay rules live here: movement, combat, economy, production,
## construction, vision. Both the human player and the AI act through the same
## public command API, so neither side can do anything the other can't.
##
## Port notes (see PORTING.md): names are the TS names; every number that is a
## JS number is a float here, ids/counts/enums are ints; sorts are stable;
## optional content fields are read with `.get(key, fallback)`. Content data is
## cached from `C` into members named exactly like the TS imports (UNITS,
## BUILDINGS, TILE, SIM_DT, …) so the code reads line for line like the spec.
## Events are Dictionaries `{kind, x, y, team, data}`; MapData is a Dictionary
## with the TS MapData keys (terrain as a PackedByteArray).
class_name World
extends RefCounted

## Kills needed to reach hero levels 1..5 (a touch slower so he doesn't snowball).
const HERO_THRESHOLDS := [5, 12, 20, 30, 42]
## Per-level Champion bonuses (kept modest — he was overtuned).
const HERO_HP_PER_LVL := 24
const HERO_ATK_PER_LVL := 2
## Kills needed to reach veterancy ranks 1..3 (Veteran / Elite / Legendary).
const VET_THRESHOLDS := [2, 5, 9]
## What each veterancy rank is called, indexed by rank (0 = no rank yet).
const VET_RANKS := ["", "Veteran", "Elite", "Legendary"]
const HERO_RESPAWN_SEC := 65.0

## However much armour is stacked, a hit still lands for this share of its
## raw damage. Never reached in a normal match; it only bites the extremes.
const ARMOR_MIN_FRACTION := 0.2

## A skirmisher starts giving ground once a melee unit is this close, and
## backs off in steps of this size. Deliberately short: it should look like
## stepping back to reload, not fleeing the field.
# Sized off a reload, not off arm's length: infantry covers ~110 units while a
# bow reloads, so giving ground any later than that means being caught mid-draw
# and shoved along by the chaser instead of backing off cleanly.
const KITE_GAP := 105.0
const KITE_STEP := 70.0
## Anything with range at or under this has to close to do damage.
const MELEE_RANGE := 12.0

const FOG_UNSEEN := 0
const FOG_EXPLORED := 1
const FOG_VISIBLE := 2

const RES_TYPE_KIND := {
	"tree": "wood",
	"gold_mine": "gold",
	"berries": "food",
}

## Buildings units can walk over (don't block the nav grid).
# Buildings units walk over rather than around. A farm is a crop plot; a bridge
# is decking laid on the shallows — blocking either would defeat its purpose.
const BUILDING_WALKABLE := {"farm": true, "bridge": true}

## Wall-class buildings (Bulwark boon, cheaper/tougher fortifications).
const WALL_TYPES := {"palisade": true, "stone_wall": true, "gate": true}

## Territory cell size, in tiles.
const TERR_CELL := 4


## One realm's state (sim/world.ts `PlayerState`).
class PlayerState extends RefCounted:
	var team: int = 0
	## {food, wood, gold}
	var resources: Dictionary = {}
	var age: int = 0
	## Set<string> as a Dictionary (id -> true), in insertion order.
	var upgrades: Dictionary = {}
	var popUsed: float = 0.0
	var popCap: float = 0.0
	## unitId -> rarity index, resolved from the meta loadout (AI uses all 0).
	var loadout: Dictionary = {}
	## Economy handicap/bonus (AI difficulty; player = 1).
	var econMult: float = 1.0
	## Rebuild a farm where it stood when its soil runs out, whenever the wood is
	## there. On by default: re-seeding is a chore, not a decision.
	var autoReseed: bool = true
	## How far each commodity's Market price has been pushed from par, −1..1.
	var marketPressure: Dictionary = {"wood": 0.0, "food": 0.0}
	var defeated: bool = false
	## Match time (seconds) at which this realm was knocked out; -1 while standing.
	var defeatedAt: float = -1.0
	## Match time (seconds) each age was reached; index 0 is the start. For stats only.
	var ageTimes: Array = [0.0]
	## Who this realm is (content/factions.ts): its look, bonuses and units.
	var faction: String = ""
	## Oaths sworn, one per age advanced past the Hearth (content/oaths.ts).
	var oaths: Array = []
	## The Oath the age advance in progress will swear, if any (String or null).
	var pendingOath = null
	## What the sworn Oaths change that boons don't (Oaths.OathRules).
	var oath: Oaths.OathRules
	## Hero (Champion) lifecycle: "none" = never trained, "alive", or "respawning".
	var heroState: String = "none"
	var heroRespawnTimer: float = 0.0 ## seconds until the Champion rises again
	var heroLevel: int = 0 ## saved level, carried across a respawn
	# Commander (leader) — passive bonuses + an optional banner power.
	var commander: String = ""
	var buildMult: float = 1.0 ## construction speed (×)
	var gatherMultC: float = 1.0 ## commander gather speed (×)
	var unitHpMult: float = 1.0 ## combat unit max HP (×)
	var unitArmorBonus: float = 0.0 ## combat unit armor (flat)
	var vetMult: float = 1.0 ## veterancy gained faster (×)
	var powerCooldown: float = 0.0 ## seconds until the commander power is ready
	## Battle plan: each boon ({id, rarity, age}) unlocks at its assigned age and stacks.
	var boonPlan: Array = []
	## Boons currently active (Boons.BoonEffect).
	var boon: Boons.BoonEffect
	## The match ledger (see World.newStats for the keys, as in the TS).
	var stats: Dictionary = {}


## A fresh stats ledger, keyed and ordered exactly like the TS `stats` literal.
static func newStats() -> Dictionary:
	return {
		"unitsKilled": 0.0, "unitsLost": 0.0, "buildingsRazed": 0.0, "buildingsLost": 0.0, "gathered": 0.0,
		"gatheredBy": {"food": 0.0, "wood": 0.0, "gold": 0.0},
		"spentOn": {"units": 0.0, "buildings": 0.0, "tech": 0.0},
		"trainedByType": {}, "lostByType": {}, "killedByType": {}, "builtByType": {},
		"killsByUnit": {}, "damageByUnit": {}, "razedByUnit": {},
		"firstHitAt": -1.0, "firstKillAt": -1.0, "firstRazeAt": -1.0,
		"firstAttackAt": -1.0, "firstDefendAt": -1.0, "firstFieldAt": -1.0,
		"damageAttacking": 0.0, "damageDefending": 0.0, "damageField": 0.0, "killsAttacking": 0.0, "killsDefending": 0.0, "openerByUnit": {},
		"damageDealt": 0.0, "damageTaken": 0.0,
		"peakArmy": 0.0, "peakVillagers": 0.0, "idleVillagerTime": 0.0, "resourcesSpent": 0.0,
		"idleTcTime": 0.0, "idleProductionTime": 0.0, "tcSeconds": 0.0, "productionSeconds": 0.0,
	}


## Entity ids come from the World that owns them, not from a module global.
## `makeEntity` keeps its no-argument form for the handful of callers that build
## a detached entity; anything belonging to a world goes through `world.spawn*`,
## which stamps the owner's id.
static var nextId := 1


static func makeEntity() -> Entity:
	var e := Entity.new()
	e.id = nextId
	nextId += 1
	return e


# ---- content, cached from C under the TS import names ----
var UNITS: Dictionary
var BUILDINGS: Dictionary
var UPGRADES: Dictionary
var AGES: Array
var MAX_AGE: int
var ABILITIES: Dictionary
var COMMANDERS: Dictionary
var OATHS: Dictionary
var FACTIONS: Dictionary
var DEFAULT_FACTION: String
var CHARGE_RUN: float
var SLOW_MULT: float
var RALLY_ATK_MULT: float
var GUARD_ARMOR: float
var AGE_ARMOR_BONUS: Array
var AGE_ATTACK_BONUS: Array
var GATHER_RATE: float
var GATHER_TICK: float
var FARM_TICK_MULT: float
var FARM_FOOD: float
var TRADE_GOLD_PER_TILE: float
var TRADE_GOLD_EXPONENT: float
var CONCEAL_RANGE: float
var MARKET_RECOVERY: float
var POP_CAP_HARD: float
var PROJECTILE_SPEED: float
var SIM_HZ: int
var SIM_DT: float
var TILE: float
var VILLAGER_CARRY_CAP: float
var REPAIR_RATE: float
var RARITY_ABILITY_CD_MULT: Array
var RARITY_ABILITY_POWER_MULT: Array
var RARITY_ARMOR_BONUS: Array
var RARITY_ATK_MULT: Array
var RARITY_HP_MULT: Array
var RARITY_SPEED_MULT: Array
var MEND_AFTER_SEC: float
var TITHE_MAX_MARKETS: float
## TERRAIN_SPEED / TERRAIN_SIGHT / TERRAIN_BUILDABLE as flat arrays indexed by Terrain.
var TERRAIN_SPEED: PackedFloat64Array
var TERRAIN_SIGHT: PackedFloat64Array
var TERRAIN_BUILDABLE: PackedByteArray
var TERRAIN_FOREST: int
var TERRAIN_SHALLOW: int
## Unit types with a charge (UNITS[type].charge set), looked up every tick.
var CHARGE_TYPES: Dictionary = {}

var entities: Array = [] ## of Entity
var byId: Dictionary = {} ## EntityId -> Entity
var grid: NavGrid = null
## The ground, one Terrain per cell — read on the movement path.
var terrain: PackedByteArray = PackedByteArray()
## How many finished bridges cover each terrain cell. Allocated lazily, since
## most maps never see one (PackedByteArray or null).
var bridged = null
var terrainCols := 0
var terrainRows := 0
var spatial := SpatialGrid.new(96)
var players: Array = [] ## of PlayerState
var rng: RNG
var tickCount := 0
var time := 0.0
var worldW := 0.0
var worldH := 0.0
var events: Array = []
## Per-team fog grids (tile resolution), indexed [team][cell].
var fog: Array = []
var fogCols := 0
var fogRows := 0
## Spectator mode: reveal the entire map to every viewer (no fog).
var revealAll := false
var numTeams := 2 ## number of player teams in this match (2..MAX_TEAMS)
## Alliance id per team; teams sharing an id are allies. Default: all solo.
var alliances: Array = []
var winner = null ## Team (int) or null
var map: Dictionary = {}
var lastAlertTime: Array = []

## This world's id allocator. Per-world so two can coexist without colliding.
var nextEntityId := 1

var banners: Array = [] ## of {team, x, y, expires, power}

var nomad := false

# --- Game mode state ---
var mode := "conquest"
var hordeTeam := -1 ## survival: the team that the waves belong to (no base/economy)
var survivalWave := 0 ## waves spawned so far
var survivalWavesTotal := 12 ## survive this many to win
var survivalTimer := 0.0 ## seconds until the next wave
var survivalWon := false
## King of the Hill: cumulative uncontested hold per team (seconds).
var kothHold: Array = []
var kothGoal := 300.0 ## 5 minutes
var hillX := 0.0
var hillY := 0.0
var hillR := 0.0

# ---- territory: whose ground is this? --------------------------------
# A coarse map (4×4-tile cells) of which realm owns each patch: the realm
# with the nearest building within reach (a Town Center reaches further).
# Rebuilt every two seconds; stats read it to tell attacking from defending.
var territory = null ## PackedInt32Array or null
var territoryCols := 0

## Every skirmish AI brain playing in this world (ai/skirmish_ai.ts keeps them
## in a module-level `WeakMap<World, SkirmishAI[]>`, AI_ROSTER), so teammates
## can pool what they have scouted. Held as WeakRefs, in the order the AIs were
## made: a strong reference would make World <-> SkirmishAI a cycle that
## RefCounted never frees, where the TS WeakMap lets both be collected.
var aiRoster: Array = []


func _init(seed: int) -> void:
	rng = RNG.new(seed)
	UNITS = C.UNITS
	BUILDINGS = C.BUILDINGS
	UPGRADES = C.UPGRADES
	AGES = C.AGES
	MAX_AGE = int(C.MAX_AGE)
	ABILITIES = C.ABILITIES
	COMMANDERS = C.COMMANDERS
	OATHS = C.OATHS
	FACTIONS = C.FACTIONS
	DEFAULT_FACTION = C.DEFAULT_FACTION
	CHARGE_RUN = C.CHARGE_RUN
	SLOW_MULT = C.SLOW_MULT
	RALLY_ATK_MULT = C.RALLY_ATK_MULT
	GUARD_ARMOR = C.GUARD_ARMOR
	AGE_ARMOR_BONUS = C.AGE_ARMOR_BONUS
	AGE_ATTACK_BONUS = C.AGE_ATTACK_BONUS
	GATHER_RATE = C.GATHER_RATE
	GATHER_TICK = C.GATHER_TICK
	FARM_TICK_MULT = C.FARM_TICK_MULT
	FARM_FOOD = C.FARM_FOOD
	TRADE_GOLD_PER_TILE = C.TRADE_GOLD_PER_TILE
	TRADE_GOLD_EXPONENT = C.TRADE_GOLD_EXPONENT
	CONCEAL_RANGE = C.CONCEAL_RANGE
	MARKET_RECOVERY = C.MARKET_RECOVERY
	POP_CAP_HARD = C.POP_CAP_HARD
	PROJECTILE_SPEED = C.PROJECTILE_SPEED
	SIM_HZ = int(C.SIM_HZ)
	SIM_DT = C.SIM_DT
	TILE = C.TILE
	VILLAGER_CARRY_CAP = C.VILLAGER_CARRY_CAP
	REPAIR_RATE = C.REPAIR_RATE
	RARITY_ABILITY_CD_MULT = C.RARITY_ABILITY_CD_MULT
	RARITY_ABILITY_POWER_MULT = C.RARITY_ABILITY_POWER_MULT
	RARITY_ARMOR_BONUS = C.RARITY_ARMOR_BONUS
	RARITY_ATK_MULT = C.RARITY_ATK_MULT
	RARITY_HP_MULT = C.RARITY_HP_MULT
	RARITY_SPEED_MULT = C.RARITY_SPEED_MULT
	MEND_AFTER_SEC = C.MEND_AFTER_SEC
	TITHE_MAX_MARKETS = C.TITHE_MAX_MARKETS
	var n := int(C.TERRAIN_COUNT)
	TERRAIN_SPEED = PackedFloat64Array()
	TERRAIN_SIGHT = PackedFloat64Array()
	TERRAIN_BUILDABLE = PackedByteArray()
	for i in n:
		TERRAIN_SPEED.append(C.TERRAIN_SPEED[str(i)])
		TERRAIN_SIGHT.append(C.TERRAIN_SIGHT[str(i)])
		TERRAIN_BUILDABLE.append(int(C.TERRAIN_BUILDABLE[str(i)]))
	TERRAIN_FOREST = int(C.Terrain["Forest"])
	TERRAIN_SHALLOW = int(C.Terrain["Shallow"])
	for id in UNITS:
		if float(UNITS[id].get("charge", 0.0)) != 0.0:
			CHARGE_TYPES[id] = true


## A blank entity carrying an id from *this* world's sequence.
func newEntity() -> Entity:
	var e := makeEntity()
	e.id = nextEntityId
	nextEntityId += 1
	return e


## `arr?.[i] ?? fb` for an optional Array argument.
static func _at(arr, i: int, fb):
	if arr == null or i < 0 or i >= arr.size():
		return fb
	var v = arr[i]
	return fb if v == null else v


## `ARR[i] ?? fb` for a numeric content array.
static func _num(arr: Array, i: int, fb: float) -> float:
	if i < 0 or i >= arr.size():
		return fb
	var v = arr[i]
	return fb if v == null else float(v)


## `this.players[team]` — null where TS would read undefined.
func _p(team: int) -> PlayerState:
	if team < 0 or team >= players.size():
		return null
	return players[team]


## `UNITS[type]` — null when there is no such unit.
func _udef(type: String):
	return UNITS.get(type)


# ---------------------------------------------------------------- setup --

func init(map: Dictionary, loadouts: Array, econMults: Array, alliances = null, commanders = null, nomad := false, boonLoadouts = null, mode := "conquest", factions = null) -> void:
	self.nomad = nomad
	self.mode = mode
	self.map = map
	worldW = map.worldW
	worldH = map.worldH
	grid = NavGrid.new(map.worldW, map.worldH)
	# The ground itself: read on the movement path, so it is kept as flat
	# arrays sized to the nav grid rather than looked up through the map.
	terrain = map.terrain
	terrainCols = int(map.cols)
	terrainRows = int(map.rows)
	fogCols = grid.cols
	fogRows = grid.rows

	# Terrain blocking (water / cliffs) from map data.
	for c in map.blockedCells:
		grid.setBlocked(int(c[0]), int(c[1]), true)

	# A match has as many player teams as it has loadouts (2 for 1v1, up to 4
	# for a free-for-all). Everything below is sized off numTeams.
	numTeams = mini(loadouts.size(), map.starts.size())
	# Alliances default to everyone-for-themselves (1v1 / FFA). A 2v2 passes
	# e.g. [0,1,0,1] so teams 0&2 ally against 1&3.
	if alliances != null and alliances.size() >= numTeams:
		self.alliances = []
		for i in numTeams:
			self.alliances.append(int(alliances[i]))
	else:
		self.alliances = []
		for t in numTeams:
			self.alliances.append(t)
	lastAlertTime = []
	for t in numTeams:
		lastAlertTime.append(-99.0)
	for t in numTeams:
		var cmdId: String = str(_at(commanders, t, ""))
		var cmd = COMMANDERS.get(cmdId)
		var b: Dictionary = cmd.bonus if cmd != null else {}
		var res: Dictionary = (map.startResources as Dictionary).duplicate(true)
		if b.get("startResources") != null:
			res.food += float(b.startResources.get("food", 0.0))
			res.wood += float(b.startResources.get("wood", 0.0))
			res.gold += float(b.startResources.get("gold", 0.0))
		# Nomad: no Town Center to start, so stake them enough wood to raise one.
		if nomad:
			res.wood += 280.0
			res.food += 50.0
		var np := PlayerState.new()
		np.team = t
		np.resources = res
		np.age = 0
		np.upgrades = {}
		np.popUsed = 0.0
		np.popCap = float(b.get("popBonus", 0.0))
		np.loadout = _at(loadouts, t, {})
		np.econMult = float(_at(econMults, t, 1.0))
		np.autoReseed = true
		np.marketPressure = {"wood": 0.0, "food": 0.0}
		np.defeated = false
		np.defeatedAt = -1.0
		np.ageTimes = [0.0]
		var fid = _at(factions, t, null)
		np.faction = fid if (fid != null and str(fid) != "" and FACTIONS.has(fid)) else DEFAULT_FACTION
		np.oaths = []
		np.pendingOath = null
		np.oath = Oaths.emptyOathRules()
		np.heroState = "none"
		np.heroRespawnTimer = 0.0
		np.heroLevel = 0
		np.commander = cmdId
		np.buildMult = float(b.get("buildMult", 1.0))
		np.gatherMultC = float(b.get("gatherMult", 1.0))
		np.unitHpMult = float(b.get("unitHpMult", 1.0))
		np.unitArmorBonus = float(b.get("unitArmorBonus", 0.0))
		np.vetMult = float(b.get("vetMult", 1.0))
		np.powerCooldown = 0.0
		np.boonPlan = _at(boonLoadouts, t, [])
		np.boon = Boons.aggregateBoons(np.boonPlan.filter(func(bn): return float(bn.age) <= 0.0))
		np.stats = newStats()
		players.append(np)
		var f := PackedByteArray()
		f.resize(fogCols * fogRows)
		f.fill(0)
		fog.append(f)
		# The faction's bonuses are in force from the first tick, so the
		# starting Town Centre and villagers are already its own.
		np.oath = Oaths.applyOaths(np.oaths, np.boon, null, Factions.factionOf(np.faction))

	# Resources.
	for r in map.resources:
		spawnResource(r.type, r.x, r.y, r.amount)

	# Game-mode setup.
	kothHold = []
	for t in numTeams:
		kothHold.append(0.0)
	hillX = map.worldW / 2.0
	hillY = map.worldH / 2.0
	hillR = minf(map.worldW, map.worldH) * 0.085 # central control zone
	if mode == "survival":
		hordeTeam = numTeams - 1 # last team = the horde
	survivalTimer = 45.0 # first wave grace period

	# Starting bases.
	for t in numTeams:
		if t == hordeTeam:
			continue # the horde has no base — it spawns waves
		var start: Dictionary = map.starts[t]
		var cmdT = COMMANDERS.get(str(_at(commanders, t, "")))
		var extra := int(cmdT.bonus.get("startVillagers", 0)) if cmdT != null else 0
		if nomad:
			# No Town Center — villagers dotted across a wide area around the spawn.
			var count := 4 + extra # a touch more than the standard three
			for i in count:
				var a := rng.range(0.0, PI * 2.0)
				var r := rng.range(TILE * 1.5, TILE * 6.0) # wide scatter
				spawnUnit(t, "villager", start.x + JSMath.cos(a) * r, start.y + JSMath.sin(a) * r)
		else:
			spawnBuilding(t, "town_center", start.x, start.y, true)
			var offsets: Array = [[-60.0, 50.0], [0.0, 64.0], [60.0, 50.0]]
			for i in extra:
				offsets.append([-90.0 + i * 36.0, 84.0])
			for o in offsets:
				spawnUnit(t, "villager", start.x + o[0], start.y + o[1])
		# Regicide: every realm fields a King to protect.
		if mode == "regicide":
			var k := spawnUnit(t, "king", start.x, start.y + 90.0)
			k.facing = PI / 2.0
	recomputeVision()
	recomputeConcealment()


func player(team: int) -> PlayerState:
	return _p(team)


## Same team, or teams sharing an alliance. Neutral allies no one.
func areAllied(a: int, b: int) -> bool:
	if a == b:
		return true
	if a == T.Team.Neutral or b == T.Team.Neutral:
		return false
	if a >= numTeams or b >= numTeams:
		return false
	return alliances[a] == alliances[b]


## Opposing player teams (never Neutral, never allies).
func areHostile(a: int, b: int) -> bool:
	if a == T.Team.Neutral or b == T.Team.Neutral:
		return false
	return not areAllied(a, b)


## True when at least two teams share an alliance (i.e. a 2v2 team game).
func hasTeamAlliances() -> bool:
	for a in numTeams:
		for b in range(a + 1, numTeams):
			if alliances[a] == alliances[b]:
				return true
	return false


## Relation of `team` to the viewer, for diplomacy colouring: "self" | "ally" | "enemy".
func relationTo(viewer: int, team: int) -> String:
	if team == viewer:
		return "self"
	return "ally" if areAllied(viewer, team) else "enemy"


## Stable 0-based index of `team` among the viewer's enemies (for colours).
func enemyIndexOf(viewer: int, team: int) -> int:
	var idx := 0
	for t in numTeams:
		if t == team:
			break
		if areHostile(viewer, t):
			idx += 1
	return idx


# ------------------------------------------------------------- spawning --

## Effective stats for a unit owned by `team`, applying rarity + age/tech.
func applyUnitStats(e: Entity, def: Dictionary, team: int) -> void:
	var p := _p(team)
	# Rarity is by role as well as by unit: your unboxed Man-at-Arms makes the
	# Legion's Legionary just as rare. Whichever is higher counts.
	var r1 := 0.0
	var r2 := 0.0
	if p != null:
		r1 = float(p.loadout.get(def.id, 0.0))
		var role = def.get("role")
		if role != null and str(role) != "":
			r2 = float(p.loadout.get(role, 0.0))
	var rarity := int(maxf(r1, r2))
	e.variantRarity = rarity
	var bn: Boons.BoonEffect = p.boon if p != null else Boons.emptyBoonEffect()
	# Commander bonuses apply to soldiers (not villagers).
	var soldier: bool = not def.get("canGather", false)
	var hpMult: float = ((p.unitHpMult if p != null else 1.0) if soldier else 1.0) * bn.hpMult # boon HP applies to all
	var armorPlus: float = ((p.unitArmorBonus if p != null else 0.0) if soldier else 0.0) + bn.armorBonus
	var ac: String = def.armorClass
	# Attack boon by armour class.
	var atkBoon := 1.0
	if ac == T.AC_INFANTRY:
		atkBoon = bn.atkMultInfantry
	elif ac == T.AC_ARCHER:
		atkBoon = bn.atkMultArcher
	elif ac == T.AC_CAVALRY:
		atkBoon = bn.atkMultCavalry
	# Speed boon: army-wide, plus cavalry/villager extras.
	var spdBoon: float = bn.armySpeedMult if soldier else bn.villSpeedMult
	if ac == T.AC_CAVALRY:
		spdBoon *= bn.cavSpeedMult
	# Flat HP from researched "hp" upgrades (e.g. Bloodlines for cavalry).
	var hpFlat := 0.0
	if p != null:
		for upId in p.upgrades:
			var up = UPGRADES.get(upId)
			if up != null and up.kind == "hp" and ((up.appliesTo as Array).has(ac) or (up.get("appliesToUnits", []) as Array).has(def.id)):
				hpFlat += up.amount
	# Oath rules by class: cavalry and siege toughness, infantry armour.
	var ok: Oaths.OathRules = p.oath if p != null else null
	var oathHp := 1.0
	var oathAtk := 1.0
	var oathArmor := 0.0
	if ok != null:
		if ac == T.AC_CAVALRY:
			oathHp *= ok.cavHpMult
		if ac == T.AC_SIEGE:
			oathHp *= ok.siegeHpMult
			oathAtk *= ok.siegeAtkMult
		if ac == T.AC_INFANTRY:
			oathArmor += ok.infantryArmor
			oathHp *= ok.infantryHpMult
		if def.get("canGather", false):
			oathHp *= ok.villagerHpMult
		else:
			oathHp *= ok.soldierHpMult
	e.maxHp = JS.round(def.hp * _num(RARITY_HP_MULT, rarity, 1.0) * hpMult * oathHp) + hpFlat
	e.hp = e.maxHp
	e.attack = JS.round(def.attack * _num(RARITY_ATK_MULT, rarity, 1.0) * atkBoon * oathAtk)
	e.armor = def.armor + _num(RARITY_ARMOR_BONUS, rarity, 0.0) + armorPlus + oathArmor
	# Pierce armor mirrors melee armor unless the unit defines a different value
	# (e.g. the Horseman: 0 melee / 2 pierce). The delta rides on top of bonuses.
	e.pierceArmor = e.armor + (def.pierceArmor - def.armor if def.has("pierceArmor") else 0.0)
	e.range = def.range + (bn.archerRangeBonus if ac == T.AC_ARCHER else 0.0)
	e.attackInterval = def.attackInterval
	e.speed = def.speed * _num(RARITY_SPEED_MULT, rarity, 1.0) * spdBoon * \
		(ok.infantrySpeedMult if ac == T.AC_INFANTRY and ok != null else 1.0)
	e.visionRange = def.visionRange * bn.visionMult * (1.0 + teamUpgradeSum(team, "vision"))
	e.radius = def.radius
	e.armorClass = ac


## Recompute a team's active boons for its current age, then refresh its army
## so a newly-unlocked boon buffs the troops you already have (veterancy and
## hero levels are preserved). Called on age-up.
func recomputeBoons(team: int) -> void:
	var p := _p(team)
	if p == null:
		return
	var beforeBld := p.boon.buildingHpMult
	var beforeWall := p.boon.wallHpMult
	var beforeCd := p.boon.towerCdMult
	var age := p.age
	p.boon = Boons.aggregateBoons(p.boonPlan.filter(func(b): return float(b.age) <= age))
	# Sworn Oaths fold in on top: some of what they do is a boon-shaped
	# modifier, the rest is rules of their own.
	var cmd = COMMANDERS.get(p.commander)
	var favoured = cmd.get("oath") if cmd != null else null
	p.oath = Oaths.applyOaths(p.oaths, p.boon, favoured, Factions.factionOf(p.faction))
	refreshTeamUnits(team)
	# A tougher-buildings Oath should toughen the town that swore it, not only
	# what it builds next.
	if p.boon.buildingHpMult != beforeBld or p.boon.wallHpMult != beforeWall or p.boon.towerCdMult != beforeCd:
		for e: Entity in entities:
			if not e.alive or e.team != team or e.kind != T.Kind.Building:
				continue
			var def = BUILDINGS.get(e.type)
			if def == null:
				continue
			if def.attack > 0:
				e.attackInterval = def.attackInterval * p.boon.towerCdMult
			var mult: float = p.boon.wallHpMult if WALL_TYPES.has(e.type) else p.boon.buildingHpMult
			var frac := e.hp / e.maxHp if e.maxHp > 0 else 1.0
			e.maxHp = JS.round(def.hp * mult)
			e.hp = maxf(1.0, e.maxHp * frac)


## Re-derive every unit's stats for a team (after a boon/HP-upgrade unlock),
## preserving current HP fraction, veterancy and hero level.
func refreshTeamUnits(team: int) -> void:
	for e: Entity in entities:
		if not e.alive or e.kind != T.Kind.Unit or e.team != team:
			continue
		var def = _udef(e.type)
		if def == null:
			continue
		var frac := e.hp / e.maxHp if e.maxHp > 0 else 1.0
		applyUnitStats(e, def, team) # base + rarity + current boon + hp upgrades
		# Re-stack veterancy (compounds, matches creditVeterancy) and hero level.
		for i in e.veterancy:
			e.maxHp += JS.round(e.maxHp * 0.12)
		e.attack += e.veterancy
		e.armor += maxi(0, e.veterancy - 1)
		if def.get("hero", false) and e.heroLevel > 0:
			e.maxHp += e.heroLevel * HERO_HP_PER_LVL
			e.attack += e.heroLevel * HERO_ATK_PER_LVL
			e.armor += floori(e.heroLevel / 2.0)
		e.hp = maxf(1.0, JS.round(e.maxHp * frac))


func spawnUnit(team: int, type: String, x: float, y: float) -> Entity:
	var def: Dictionary = UNITS[type]
	var e := newEntity()
	e.kind = T.Kind.Unit
	e.team = team
	e.type = type
	if grid != null:
		var o := grid.nearestOpenWorld(x, y)
		e.x = o[0]
		e.y = o[1]
	else:
		e.x = x
		e.y = y
	e.prevX = e.x
	e.prevY = e.y
	applyUnitStats(e, def, team)
	e.facing = rng.range(0.0, PI * 2.0)
	entities.append(e)
	byId[e.id] = e
	var p := _p(team)
	if p != null:
		p.popUsed += def.pop
	return e


func spawnBuilding(team: int, type: String, x: float, y: float, completed: bool) -> Entity:
	var def: Dictionary = BUILDINGS[type]
	var e := newEntity()
	e.kind = T.Kind.Building
	e.team = team
	e.type = type
	# Snap to tile grid so footprints align.
	var tiles: float = def.tiles
	e.x = GridSnap.snapBuilding(x, tiles)
	e.y = GridSnap.snapBuilding(y, tiles)
	e.prevX = e.x
	e.prevY = e.y
	e.radius = (tiles * TILE) / 2.0
	var tp := _p(team)
	var bn: Boons.BoonEffect = tp.boon if tp != null else Boons.emptyBoonEffect()
	var hpMult: float = bn.wallHpMult if WALL_TYPES.has(type) else bn.buildingHpMult
	var maxHp := JS.round(def.hp * hpMult)
	e.maxHp = maxHp
	e.armorClass = T.AC_BUILDING
	e.armor = def.get("armor", 1.0)
	e.pierceArmor = def.get("pierceArmor", def.get("armor", 1.0))
	e.visionRange = def.sight
	e.attack = def.attack
	# Vigilant Watch: defensive buildings fire faster and farther.
	e.range = def.range + (bn.towerRangeBonus if def.attack > 0 else 0.0)
	e.attackInterval = def.attackInterval * bn.towerCdMult if def.attack > 0 else def.attackInterval
	# Soil goes in when the plot is laid, not when it finishes. A farm is a
	# food node as well as a building, and stocking it at completion left a
	# state — Done, but empty — that reads as a field worked dry the instant
	# anyone touches it.
	if type == "farm":
		e.amount = JS.round(FARM_FOOD * (tp.oath.farmFoodMult if tp != null else 1.0))
	if completed:
		e.hp = maxHp
		e.buildState = T.BuildState.Done
		e.buildProgress = 1.0
		onBuildingCompleted(e, def, true)
	else:
		e.hp = maxf(1.0, maxHp * 0.08)
		e.buildState = T.BuildState.Foundation
		e.buildProgress = 0.0
	# Farms are crop plots — units freely walk across them, so they never block
	# the nav grid (placement overlap is still guarded in placeBuilding).
	if not BUILDING_WALKABLE.has(type):
		grid.stampFootprint(e.x, e.y, tiles, true)
	entities.append(e)
	byId[e.id] = e
	return e


func spawnResource(type: String, x: float, y: float, amount: float) -> Entity:
	var e := newEntity()
	e.kind = T.Kind.Resource
	e.team = T.Team.Neutral
	e.type = type
	e.x = floor(x / TILE) * TILE + TILE / 2.0
	e.y = floor(y / TILE) * TILE + TILE / 2.0
	e.prevX = e.x
	e.prevY = e.y
	e.radius = TILE / 2.0
	e.amount = amount
	e.maxHp = 1.0
	e.hp = 1.0
	grid.stampFootprint(e.x, e.y, 1, true)
	entities.append(e)
	byId[e.id] = e
	return e


func onBuildingCompleted(e: Entity, def: Dictionary, silent := false) -> void:
	var p := _p(e.team)
	# A faction's houses can hold more (the Khanate's yurts).
	var pop: float = def.popProvided + (p.oath.housePopBonus if def.id == "house" and p != null else 0.0)
	if p != null:
		p.popCap = minf(POP_CAP_HARD, p.popCap + pop)
	e.popProvided = pop
	# Decking only carries traffic once the bridge is actually finished — a
	# half-built crossing is a construction site standing in a river.
	if def.get("onlyOnShallows", false):
		stampBridge(e, true)
	if not silent:
		emit("complete", e.x, e.y, e.team, def.id)


# --------------------------------------------------------------- events --

func emit(kind: String, x: float, y: float, team: int, data = null) -> void:
	if events.size() < 512:
		events.append({"kind": kind, "x": x, "y": y, "team": team, "data": data})


func drainEvents() -> Array:
	var out := events
	events = []
	return out


## AI voice line — surfaced by the UI for allies of the viewing player.
func callout(team: int, x: float, y: float, text: String) -> void:
	emit("callout", x, y, team, text)


# ------------------------------------------------------------- commands --

func resolveOrderQueue(e: Entity, order: Order, queue: bool) -> void:
	if queue and e.order.kind != T.OrderKind.Idle:
		e.order.queue.append(order)
	else:
		if queue:
			order.queue = e.order.queue
		e.order = order
		e.path = null
		e.pathIndex = 0


func _units(ids: Array) -> Array:
	var out: Array = []
	for id in ids:
		var e = byId.get(int(id))
		if e != null and e.alive and e.kind == T.Kind.Unit:
			out.append(e)
	return out


func issueMove(ids: Array, tx: float, ty: float, queue := false, attackMove := false) -> void:
	var movers := _units(ids)
	if movers.size() == 0:
		return
	var kind := T.OrderKind.AttackMove if attackMove else T.OrderKind.Move
	# Groups share one flow field; singles use A*.
	var flow = null
	if movers.size() > 3:
		flow = FlowField.new(grid, tx, ty)
	for e: Entity in movers:
		var order := Order.make(kind, tx, ty, -1)
		order.flow = flow
		resolveOrderQueue(e, order, queue)


## Like issueMove, but spreads the group into a box formation facing the
## direction of travel so units arrive side-by-side instead of stacking on
## one point. Used for player commands; the AI keeps the cheaper issueMove.
func issueFormationMove(ids: Array, tx: float, ty: float, queue := false, attackMove := false) -> void:
	var movers := _units(ids)
	if movers.size() <= 1:
		issueMove(ids, tx, ty, queue, attackMove)
		return
	var kind := T.OrderKind.AttackMove if attackMove else T.OrderKind.Move
	var n := movers.size()

	# Forward = from the group's centre toward the target.
	var ax := 0.0
	var ay := 0.0
	for e: Entity in movers:
		ax += e.x
		ay += e.y
	ax /= n
	ay /= n
	var ang := JSAtan.atan2(ty - ay, tx - ax)
	if is_nan(ang) or is_inf(ang):
		ang = 0.0
	var ux := JSMath.cos(ang)
	var uy := JSMath.sin(ang)
	var px := -uy # rightward across the front
	var py := ux
	var cols := maxi(1, JS.roundi(sqrt(float(n))))
	var spacing := 24.0

	# Slot positions: a grid centred on the target, front rank at the target.
	var slots: Array = []
	for i in n:
		var col := i % cols
		var row := i / cols
		var lx := (col - (cols - 1) / 2.0) * spacing
		var ly := row * spacing
		var s := grid.nearestOpenWorld(tx + px * lx - ux * ly, ty + py * lx - uy * ly)
		slots.append(s)

	var flow = FlowField.new(grid, tx, ty) if n > 3 else null
	var used: Array = []
	used.resize(n)
	used.fill(false)
	for e: Entity in movers:
		var best := -1
		var bestD := INF
		for s in n:
			if used[s]:
				continue
			var d := MathU.dist2(e.x, e.y, slots[s][0], slots[s][1])
			if d < bestD:
				bestD = d
				best = s
		used[best] = true
		var order := Order.make(kind, slots[best][0], slots[best][1], -1)
		order.flow = flow
		order.slot = true
		resolveOrderQueue(e, order, queue)


func issueAttack(ids: Array, targetId: int, queue := false) -> void:
	var target = byId.get(targetId)
	if target == null or not target.alive:
		return
	for id in ids:
		var e = byId.get(int(id))
		if e == null or not e.alive or e.kind != T.Kind.Unit:
			continue
		# No friendly fire: never let a unit force-attack its own or an allied
		# entity. (Neutral targets — huntable/gaia — are still attackable.)
		if areAllied(e.team, target.team):
			continue
		resolveOrderQueue(e, Order.make(T.OrderKind.Attack, target.x, target.y, targetId), queue)


func issueGather(ids: Array, nodeId: int, queue := false) -> void:
	var node = byId.get(nodeId)
	if node == null or not node.alive:
		return
	for id in ids:
		var e = byId.get(int(id))
		if e == null or not e.alive:
			continue
		var d = _udef(e.type)
		if d == null or not d.get("canGather", false):
			continue
		resolveOrderQueue(e, Order.make(T.OrderKind.Gather, node.x, node.y, nodeId), queue)


func issueBuildRepair(ids: Array, buildingId: int, queue := false) -> void:
	var b = byId.get(buildingId)
	if b == null or not b.alive or b.kind != T.Kind.Building:
		return
	var kind := T.OrderKind.Repair if b.buildState == T.BuildState.Done else T.OrderKind.Build
	for id in ids:
		var e = byId.get(int(id))
		if e == null or not e.alive:
			continue
		var d = _udef(e.type)
		if d == null or not d.get("canBuild", false):
			continue
		resolveOrderQueue(e, Order.make(kind, b.x, b.y, buildingId), queue)
	# A new farm belongs to the first villager sent to build it, from the
	# moment it's ordered: helpers who finish it first don't take it, and it
	# stays theirs until they're given something else to do.
	if b.type == "farm" and kind == T.OrderKind.Build:
		var owner = byId.get(b.farmWorker)
		if owner == null or not owner.alive or not worksFarm(owner, b):
			var first = null
			for id in ids:
				var e = byId.get(int(id))
				if e != null and e.alive and e.team == b.team and _udef(e.type) != null and _udef(e.type).get("canBuild", false):
					first = e
					break
			if first != null:
				b.farmWorker = first.id


func issueStop(ids: Array) -> void:
	for id in ids:
		var e = byId.get(int(id))
		if e == null or not e.alive:
			continue
		e.order = Order.make(T.OrderKind.Idle, 0.0, 0.0, -1)
		e.path = null


func issueHold(ids: Array) -> void:
	for id in ids:
		var e = byId.get(int(id))
		if e == null or not e.alive:
			continue
		e.order = Order.make(T.OrderKind.Hold, e.x, e.y, -1)
		e.stance = T.Stance.StandGround
		e.path = null


## Set the combat stance of the given units (Aggressive/Defensive/StandGround/Passive).
func setStance(ids: Array, stance: int) -> void:
	for id in ids:
		var e = byId.get(int(id))
		if e == null or not e.alive or e.kind != T.Kind.Unit:
			continue
		e.stance = stance
		# Switching off Stand Ground/Passive while idle lets a held unit re-engage;
		# switching to Passive drops any auto-acquired fight so it truly holds fire.
		if stance == T.Stance.Passive and e.order.kind == T.OrderKind.Attack and e.order.queue.size() == 0:
			e.order = Order.make(T.OrderKind.Idle, e.x, e.y, -1)


## Trigger each selected unit's signature ability if it has one and it's off
## cooldown. Resolves the ability by kind (self-buff, ally rally, enemy slow,
## burst heal, or an area volley). Returns how many fired (for UI feedback).
func useAbility(ids: Array) -> int:
	var fired := 0
	for id in ids:
		var e = byId.get(int(id))
		if e == null or not e.alive or e.kind != T.Kind.Unit:
			continue
		var ab = ABILITIES.get(e.type)
		if ab == null or e.abilityCooldown > 0:
			continue
		# Higher-rarity units wield a stronger, faster-recharging ability.
		var pm := _num(RARITY_ABILITY_POWER_MULT, e.variantRarity, 1.0)
		var cm := _num(RARITY_ABILITY_CD_MULT, e.variantRarity, 1.0)
		e.abilityActive = ab.duration * pm
		e.abilityCooldown = ab.cooldown * cm

		match ab.kind:
			"rally":
				# Lift the attack of every nearby ally (and yourself).
				var r: float = ab.get("radius", 150.0) * pm
				for n: Entity in spatial.query(e.x, e.y, r):
					if n.alive and n.kind == T.Kind.Unit and areAllied(e.team, n.team) and MathU.dist(e.x, e.y, n.x, n.y) <= r:
						n.rallyTimer = maxf(n.rallyTimer, ab.get("statusDuration", 6.0) * pm)
			"guard":
				# Raise shields over everyone nearby: a defensive rally. "rally" only
				# lifts attack, so before this there was no way for a support unit to
				# make a line harder to kill.
				var r: float = ab.get("radius", 140.0) * pm
				for n: Entity in spatial.query(e.x, e.y, r):
					if n.alive and n.kind == T.Kind.Unit and areAllied(e.team, n.team) and MathU.dist(e.x, e.y, n.x, n.y) <= r:
						n.guardTimer = maxf(n.guardTimer, ab.get("statusDuration", 6.0) * pm)
			"slow":
				# Hobble nearby enemies.
				var r: float = ab.get("radius", 120.0) * pm
				for n: Entity in spatial.query(e.x, e.y, r):
					if n.alive and n.kind == T.Kind.Unit and areHostile(e.team, n.team) and \
							MathU.dist(e.x, e.y, n.x, n.y) <= r:
						n.slowTimer = maxf(n.slowTimer, ab.get("statusDuration", 4.0) * pm)
			"heal":
				# Instantly mend wounded allies around the caster.
				var r: float = ab.get("radius", 160.0) * pm
				var amt: float = ab.get("amount", 40.0) * pm
				for n: Entity in spatial.query(e.x, e.y, r):
					if n.alive and n.kind == T.Kind.Unit and areAllied(e.team, n.team) and n.hp < n.maxHp and \
							MathU.dist(e.x, e.y, n.x, n.y) <= r:
						n.hp = minf(n.maxHp, n.hp + amt)
			"volley":
				# Rain a barrage on the unit's target (or the nearest foe in sight).
				var r: float = ab.get("radius", 84.0) * pm
				var amt: float = ab.get("amount", 16.0) * pm
				var ix: float = e.x
				var iy: float = e.y
				var tgt = byId.get(e.order.target)
				if tgt != null and tgt.alive and areHostile(e.team, tgt.team):
					ix = tgt.x
					iy = tgt.y
				else:
					var foe = findEnemyInRange(e, e.visionRange)
					if foe != null:
						ix = foe.x
						iy = foe.y
				for n: Entity in spatial.query(ix, iy, r):
					if n.alive and areHostile(e.team, n.team) and MathU.dist(ix, iy, n.x, n.y) <= r:
						dealDamage(e.team, n, amt, e.type, e.id)
				emit("ability", ix, iy, e.team, ab.id)
			"cleave":
				# A heroic sweep: damage every foe around the hero, rally every ally.
				var r: float = ab.get("radius", 115.0) * pm
				for n: Entity in spatial.query(e.x, e.y, r):
					if not n.alive or n.kind != T.Kind.Unit or MathU.dist(e.x, e.y, n.x, n.y) > r:
						continue
					if areHostile(e.team, n.team):
						dealDamage(e.team, n, ab.get("amount", 24.0) * pm, e.type, e.id)
					elif areAllied(e.team, n.team):
						n.rallyTimer = maxf(n.rallyTimer, ab.get("statusDuration", 5.0) * pm)

		emit("ability", e.x, e.y - e.radius, e.team, ab.id)
		fired += 1
	return fired


## Apply a hero's level bonuses to a freshly-spawned Champion.
func applyHeroLevel(e: Entity, level: int) -> void:
	e.heroLevel = level
	e.heroKills = HERO_THRESHOLDS[level - 1] if level > 0 else 0
	e.maxHp += level * HERO_HP_PER_LVL
	e.attack += level * HERO_ATK_PER_LVL
	e.armor += floori(level / 2.0) # +1 at lvl 2-3, +2 at lvl 4-5
	e.hp = e.maxHp # respawn at full (not a mid-fight heal)


## Award a kill to the unit that landed it, ranking it up the veterancy ladder
## (Veteran → Elite → Legendary). Villagers, heroes (own leveling) and
## buildings don't earn veterancy.
func creditVeterancy(killerId: int) -> void:
	if killerId < 0:
		return
	var k = byId.get(killerId)
	if k == null or not k.alive or k.kind != T.Kind.Unit:
		return
	var def = _udef(k.type)
	if def == null or def.get("canGather", false) or def.get("hero", false) or def.attack <= 0:
		return
	k.vetKills += 1
	var kp := _p(k.team)
	var vetMult: float = (kp.vetMult if kp != null else 1.0) * (kp.oath.vetMult if kp != null else 1.0)
	while k.veterancy < VET_THRESHOLDS.size() and k.vetKills >= maxf(1.0, JS.round(VET_THRESHOLDS[k.veterancy] / vetMult)):
		k.veterancy += 1
		# Bigger max HP (but NOT a heal — staying alive is the unit's job), plus
		# a sharper edge in attack and, from Elite, armour.
		k.maxHp += JS.round(k.maxHp * 0.12)
		k.attack += 1
		if k.veterancy >= 2:
			k.armor += 1
		if k.team == T.Team.Player:
			emit("ability", k.x, k.y - k.radius, k.team, "veteran")


## Credit the nearest friendly Champion for a kill near (x,y) and level it up.
func creditHeroKill(byTeam: int, x: float, y: float) -> void:
	var hero: Entity = null
	var best := 170.0 * 170.0
	for h: Entity in spatial.query(x, y, 180.0):
		if not h.alive or h.team != byTeam:
			continue
		var hd = _udef(h.type)
		if hd == null or not hd.get("hero", false):
			continue
		var d := MathU.dist2(x, y, h.x, h.y)
		if d < best:
			best = d
			hero = h
	if hero == null:
		return
	hero.heroKills += 1
	var p := _p(byTeam)
	while hero.heroLevel < HERO_THRESHOLDS.size() and hero.heroKills >= HERO_THRESHOLDS[hero.heroLevel]:
		hero.heroLevel += 1
		# Tougher over time, but no mid-fight heal (a kill shouldn't top you up),
		# and a gentler curve so a few kills don't make him unstoppable.
		hero.maxHp += HERO_HP_PER_LVL
		hero.attack += HERO_ATK_PER_LVL
		if hero.heroLevel % 2 == 0:
			hero.armor += 1 # +1 at levels 2 & 4 (max +2)
		if p != null:
			p.heroLevel = hero.heroLevel
		emit("ability", hero.x, hero.y - hero.radius, byTeam, "hero_levelup")


# Commander power -----------------------------------------------------------

## True if `team`'s commander has a power ready to plant.
func powerReady(team: int) -> bool:
	var p := _p(team)
	if p == null or p.powerCooldown > 0:
		return false
	var cmd = COMMANDERS.get(p.commander)
	return cmd != null and cmd.get("power") != null


## Plant the commander's banner at (wx,wy). Returns true if planted.
func placeBanner(team: int, wx: float, wy: float) -> bool:
	var p := _p(team)
	var cmd = COMMANDERS.get(p.commander if p != null else "")
	var power = cmd.get("power") if cmd != null else null
	if p == null or power == null or p.powerCooldown > 0:
		return false
	banners.append({"team": team, "x": wx, "y": wy, "expires": time + power.duration, "power": power})
	p.powerCooldown = power.cooldown
	emit("ability", wx, wy, team, power.id)
	return true


## Disband your own units or raze your own buildings. Credited to Neutral, so
## deleting an army never feeds an opponent's kill count — and the population
## it frees comes back exactly as if they had died.
func deleteUnits(ids: Array) -> void:
	for id in ids:
		var e = byId.get(int(id))
		if e == null or not e.alive:
			continue
		if e.kind != T.Kind.Unit and e.kind != T.Kind.Building:
			continue
		kill(e, T.Team.Neutral)


## The active friendly banner covering (x,y), if any.
func bannerAt(team: int, x: float, y: float):
	for b in banners:
		if not areAllied(team, b.team):
			continue
		var r: float = b.power.radius
		var dx: float = x - b.x
		var dy: float = y - b.y
		if dx * dx + dy * dy <= r * r:
			return b
	return null


## Per-tick: expire banners, heal soldiers standing under one, cool powers.
func tickBanners() -> void:
	for t in numTeams:
		var p := _p(t)
		if p != null and p.powerCooldown > 0:
			p.powerCooldown = maxf(0.0, p.powerCooldown - SIM_DT)
	if banners.size() == 0:
		return
	var now := time
	banners = banners.filter(func(b): return b.expires > now)
	for b in banners:
		if b.power.militaryRegen <= 0:
			continue
		var r: float = b.power.radius
		for e: Entity in spatial.query(b.x, b.y, r):
			if not e.alive or e.kind != T.Kind.Unit or e.hp >= e.maxHp:
				continue
			if not areAllied(b.team, e.team):
				continue
			var ed = _udef(e.type)
			if ed != null and ed.get("canGather", false):
				continue # soldiers only
			if MathU.dist2(b.x, b.y, e.x, e.y) > r * r:
				continue
			e.hp = minf(e.maxHp, e.hp + b.power.militaryRegen * SIM_DT)


## Validate placement + pay cost + spawn foundation. Returns entity or null.
## What a building costs this realm: boons, then its faction's prices.
func buildingCost(p: PlayerState, type: String) -> Dictionary:
	var def: Dictionary = BUILDINGS[type]
	var m: float = (p.boon.wallCostMult if WALL_TYPES.has(type) else p.boon.buildCostMult) * float(p.oath.buildingCostMult.get(type, 1.0))
	return scaledCost(def.cost, m)


## The cost a team would pay to place a building — for the build menu.
func buildingCostFor(team: int, type: String) -> Dictionary:
	var p := _p(team)
	if p == null or not BUILDINGS.has(type):
		return {"food": 0.0, "wood": 0.0, "gold": 0.0}
	return buildingCost(p, type)


## Why a building can't go here, in words a player can act on — or null if it
## can. The one source of truth for placement: `placeBuilding` refuses exactly
## when this has a reason, so the ghost never shows green for a spot the sim
## will then reject.
func placementProblem(team: int, type: String, wx: float, wy: float, ignoreCost := false):
	var def = BUILDINGS.get(type)
	var p := _p(team)
	if def == null or p == null:
		return "Unknown building"
	if p.age < def.age:
		var an = AGES[int(def.age)].name if int(def.age) < AGES.size() else "next age"
		return "Needs the %s" % an
	var req = def.get("requires")
	if req != null and not hasBuilding(team, req):
		var rd = BUILDINGS.get(req)
		return "Needs a %s first" % (rd.name if rd != null else req)
	# Master Masons (buildings) / Bulwark (walls) make construction cheaper.
	var cost := buildingCost(p, type)
	if not ignoreCost and not canAfford(p.resources, cost):
		var short: Array = []
		for k in ["food", "wood", "gold"]:
			if p.resources[k] < cost[k]:
				short.append("%s more %s" % [JS.num(ceil(cost[k] - p.resources[k])), k])
		return "Need %s" % " and ".join(short)
	var tiles: float = def.tiles
	var sx := GridSnap.snapBuilding(wx, tiles)
	var sy := GridSnap.snapBuilding(wy, tiles)
	if not groundBuildable(sx, sy, tiles, type):
		return "Can't build on this ground"
	if not grid.footprintClear(sx, sy, tiles):
		return "Blocked — trees, rocks or a building in the way"
	var half := (tiles * TILE) / 2.0
	# No building on top of units — but only for buildings that would actually
	# trap them. A farm is walkable, and the farmer works standing on the plot,
	# so applying this to farms made it impossible to re-seed a spent field on
	# its own ground: the villager who just worked it dry was in the way.
	if not BUILDING_WALKABLE.has(type):
		var near := spatial.query(sx, sy, tiles * TILE * 0.75)
		for n: Entity in near:
			if n.alive and n.kind == T.Kind.Unit and absf(n.x - sx) < half + n.radius and absf(n.y - sy) < half + n.radius:
				return "Units are standing there"
	# No building on top of a walkable building (farm) — those don't stamp the
	# grid, so footprintClear can't catch them. Scanned directly since the
	# spatial index may not include a farm placed this same tick.
	for ent: Entity in entities:
		if ent.alive and ent.kind == T.Kind.Building and BUILDING_WALKABLE.has(ent.type) and \
				absf(ent.x - sx) < half + ent.radius and absf(ent.y - sy) < half + ent.radius:
			return "Overlaps a farm"
	return null


func placeBuilding(team: int, type: String, wx: float, wy: float) -> Entity:
	if placementProblem(team, type, wx, wy) != null:
		return null
	var p := _p(team)
	var cost := buildingCost(p, type)
	pay(p.resources, cost, team, "buildings")
	p.stats.builtByType[type] = float(p.stats.builtByType.get(type, 0.0)) + 1.0
	# Pass the RAW click coords — spawnBuilding snaps once. Passing the already
	# snapped sx/sy would double-snap (odd footprints round up a whole tile,
	# landing the building one block down-right of the ghost).
	var e := spawnBuilding(team, type, wx, wy, false)
	emit("build", e.x, e.y, team, type)
	return e


func canPlace(team: int, type: String, wx: float, wy: float) -> bool:
	var def = BUILDINGS.get(type)
	if def == null:
		return false
	var tiles: float = def.tiles
	var sx := GridSnap.snapBuilding(wx, tiles)
	var sy := GridSnap.snapBuilding(wy, tiles)
	return grid.footprintClear(sx, sy, tiles) and groundBuildable(sx, sy, tiles, type)


## Whether a footprint's ground will take a building. Mountains and lakes are
## already blocked, so this is really about the passable-but-unbuildable
## ground: you cannot raise a barracks in a marsh, in a wood, or in a ford.
## That is what stops a player from simply paving over the map's geography.
func groundBuildable(sx: float, sy: float, tiles: float, type = null) -> bool:
	# Worlds built without a map (the auto-battler's arena, unit tests) have no
	# ground to consult, and everything on them is buildable.
	if terrainCols == 0:
		return true
	# A bridge is the one building that wants ground nothing else can use, so
	# its test is the inverse: every cell must be shallows. That also stops it
	# being used as a cheap wall on dry land.
	var bridging := false
	if type != null and type != "":
		var bd = BUILDINGS.get(type)
		bridging = bd != null and bd.get("onlyOnShallows", false)
	var c0x := int(floor((sx - (tiles * TILE) / 2.0) / TILE))
	var c0y := int(floor((sy - (tiles * TILE) / 2.0) / TILE))
	var tn := int(tiles)
	for y in tn:
		for x in tn:
			var cx := c0x + x
			var cy := c0y + y
			if cx < 0 or cy < 0 or cx >= terrainCols or cy >= terrainRows:
				return false
			var t: int = terrain[cy * terrainCols + cx]
			if bridging:
				if t != TERRAIN_SHALLOW:
					return false
			elif not (t < TERRAIN_BUILDABLE.size() and TERRAIN_BUILDABLE[t] != 0):
				return false
	return true


## True when a finished bridge is carrying this world point.
func bridgedAt(wx: float, wy: float) -> bool:
	if bridged == null:
		return false
	var cx := int(wx / TILE)
	var cy := int(wy / TILE)
	if cx < 0 or cy < 0 or cx >= terrainCols or cy >= terrainRows:
		return false
	return bridged[cy * terrainCols + cx] > 0


## Lay or lift the decking a bridge puts over the shallows.
##
## Counted rather than a flag, because two bridges can overlap at the tile
## where their footprints meet and a boolean would leave a hole in the crossing
## when only one of them burned down.
func stampBridge(b: Entity, on: bool) -> void:
	if terrainCols == 0:
		return
	if bridged == null:
		bridged = PackedByteArray()
		bridged.resize(terrainCols * terrainRows)
		bridged.fill(0)
	var bd = BUILDINGS.get(b.type)
	var tiles: float = bd.tiles if bd != null else 2.0
	var c0x := int(floor((b.x - (tiles * TILE) / 2.0) / TILE))
	var c0y := int(floor((b.y - (tiles * TILE) / 2.0) / TILE))
	var tn := int(tiles)
	for y in tn:
		for x in tn:
			var cx := c0x + x
			var cy := c0y + y
			if cx < 0 or cy < 0 or cx >= terrainCols or cy >= terrainRows:
				continue
			var i := cy * terrainCols + cx
			if on:
				bridged[i] = mini(255, bridged[i] + 1)
			elif bridged[i] > 0:
				bridged[i] = bridged[i] - 1


## How many distinct qualifying building types `team` has, vs how many an age
## needs: {have, need}.
func ageRequirementProgress(team: int, ageIndex: int) -> Dictionary:
	if ageIndex < 0 or ageIndex >= AGES.size():
		return {"have": 0, "need": 0}
	var age: Dictionary = AGES[ageIndex]
	var have := 0
	# One pass over the entities for all the required types, rather than one
	# hasBuilding() scan per type (Godot-only speed-up, same answer).
	var want: Array = age.requiresAny
	var found := {}
	if not want.is_empty():
		for e: Entity in entities:
			if e.team == team and e.kind == T.Kind.Building and e.alive and e.buildState == T.BuildState.Done and want.has(e.type):
				found[e.type] = true
	for id in want:
		if found.has(id):
			have += 1
	return {"have": mini(have, int(age.requiresCount)), "need": int(age.requiresCount)}


func ageRequirementMet(team: int, ageIndex: int) -> bool:
	var pr := ageRequirementProgress(team, ageIndex)
	return pr.have >= pr.need


func hasBuilding(team: int, type: String) -> bool:
	for e: Entity in entities:
		if e.alive and e.team == team and e.kind == T.Kind.Building and e.type == type and e.buildState == T.BuildState.Done:
			return true
	return false


func trainUnit(team: int, buildingId: int, unitType: String) -> bool:
	var b = byId.get(buildingId)
	var p := _p(team)
	var def = _udef(unitType)
	if b == null or not b.alive or p == null or def == null or b.team != team:
		return false
	if b.buildState != T.BuildState.Done:
		return false
	if not (BUILDINGS[b.type].trains as Array).has(unitType):
		return false
	if p.age < def.age:
		return false
	# A signature unit belongs to the realms that swore its Oath, and a
	# faction's own units to that faction — which trains them *instead of*
	# the shared units they replace.
	var oathId = def.get("oath")
	if oathId != null and str(oathId) != "" and not p.oaths.has(oathId):
		return false
	if not canFieldType(p, unitType):
		return false
	# One Champion per realm: only trainable when none is fielded or pending.
	if def.get("hero", false) and (p.heroState != "none" or b.productionQueue.has("u:hero")):
		return false
	if b.productionQueue.size() >= 8:
		return false
	if p.popUsed + def.pop > p.popCap:
		return false
	var cost := unitCost(p, def)
	if not canAfford(p.resources, cost):
		return false
	pay(p.resources, cost, team, "units")
	p.stats.trainedByType[unitType] = float(p.stats.trainedByType.get(unitType, 0.0)) + 1.0
	b.productionQueue.append("u:" + unitType)
	if b.productionQueue.size() == 1:
		b.productionTime = def.buildTime * trainMult(p, def)
	return true


## What a unit costs this realm: boons, then its faction's prices.
func unitCost(p: PlayerState, def: Dictionary) -> Dictionary:
	var m: float = p.boon.unitCostMult # Quartermaster
	if not def.get("canGather", false):
		m *= p.oath.soldierCostMult
	if def.armorClass == T.AC_CAVALRY:
		m *= p.oath.cavCostMult
	m *= float(p.oath.unitCostMult.get(def.id, 1.0))
	return scaledCost(def.cost, m)


## The cost a team would pay for a unit — for the command card and the AI.
func unitCostFor(team: int, type: String) -> Dictionary:
	var p := _p(team)
	var def = _udef(type)
	if p == null or def == null:
		return {"food": 0.0, "wood": 0.0, "gold": 0.0}
	return unitCost(p, def)


## Whether this realm's faction fields a unit type at all.
func canFieldType(p: PlayerState, type: String) -> bool:
	var f := Factions.factionOf(p.faction)
	if str(f.replaces.get(type, "")) != "":
		return false
	var own = Factions.factionForUnit(type)
	return own == null or own.id == f.id


## Training-time multiplier for a unit: boons, faction, then Oaths.
func trainMult(p: PlayerState, def: Dictionary) -> float:
	var m: float = p.boon.trainSpeedMult
	var o := p.oath
	if def.get("canGather", false):
		m *= o.villagerTrainMult
	else:
		m *= o.soldierTrainMult
		if def.armorClass == T.AC_INFANTRY:
			m *= o.infantryTrainMult
		elif def.armorClass == T.AC_CAVALRY:
			m *= o.cavTrainMult
		elif def.armorClass == T.AC_SIEGE:
			m *= o.siegeTrainMult
	var oathId = def.get("oath")
	if oathId != null and str(oathId) != "":
		m *= o.signatureTrainMult
	return m


## What a technology costs this realm (factions price the Blacksmith).
func techCostFor(team: int, techId: String) -> Dictionary:
	var up = UPGRADES.get(techId)
	var p := _p(team)
	if up == null or p == null:
		return {"food": 0.0, "wood": 0.0, "gold": 0.0}
	return scaledCost(up.cost, p.oath.techCostMult) if up.researchedAt == "blacksmith" else up.cost


## Champion availability for the command card: {trainable, label}.
func heroStatus(team: int) -> Dictionary:
	var p := _p(team)
	if p == null or p.heroState == "none":
		return {"trainable": true, "label": ""}
	if p.heroState == "respawning":
		return {"trainable": false, "label": "Rises in %ss" % JS.num(ceil(p.heroRespawnTimer))}
	return {"trainable": false, "label": "In the field"}


func research(team: int, buildingId: int, techId: String) -> bool:
	var b = byId.get(buildingId)
	var p := _p(team)
	if b == null or not b.alive or p == null or b.team != team or b.buildState != T.BuildState.Done:
		return false
	if techId == "age" or techId.begins_with("age:"):
		var next := p.age + 1
		if next > MAX_AGE or b.type != "town_center":
			return false
		var age: Dictionary = AGES[next]
		# "age:<oath>" swears that Oath as the realm advances. It has to be one
		# offered by the age being reached. A bare "age" advances unsworn.
		var oathId = techId.substr(4) if techId.begins_with("age:") else null
		if oathId != null:
			var od = OATHS.get(oathId)
			if od == null or int(od.age) != next:
				return false
		if not ageRequirementMet(team, next):
			return false
		if not canAfford(p.resources, age.cost):
			return false
		# Only one age advance in flight per realm — checking just this building's
		# queue let a second Town Center double-charge and over-advance.
		for e: Entity in entities:
			if e.alive and e.team == team and e.kind == T.Kind.Building and e.productionQueue.has("a:age"):
				return false
		pay(p.resources, age.cost, team, "tech")
		p.pendingOath = oathId
		b.productionQueue.append("a:age")
		if b.productionQueue.size() == 1:
			b.productionTime = age.advanceTime
		return true
	var up = UPGRADES.get(techId)
	if up == null or p.upgrades.has(techId) or p.age < up.age:
		return false
	if b.type != up.researchedAt:
		return false
	if b.productionQueue.has("t:" + techId):
		return false
	var upCost := techCostFor(team, techId)
	if not canAfford(p.resources, upCost):
		return false
	pay(p.resources, upCost, team, "tech")
	b.productionQueue.append("t:" + techId)
	if b.productionQueue.size() == 1:
		b.productionTime = up.time
	return true


## Take an item out of a building's queue and give back what it cost. The
## item in progress can be cancelled too; the next one then starts fresh.
func cancelProduction(team: int, buildingId: int, index: int) -> bool:
	var b = byId.get(buildingId)
	var p := _p(team)
	if b == null or not b.alive or p == null or b.team != team or index < 0 or index >= b.productionQueue.size():
		return false
	var item: String = b.productionQueue[index]
	var refund: Dictionary = {"food": 0.0, "wood": 0.0, "gold": 0.0}
	if item.begins_with("u:"):
		var d = _udef(item.substr(2))
		if d != null:
			refund = unitCost(p, d)
	elif item.begins_with("t:"):
		refund = techCostFor(team, item.substr(2))
	elif item == "a:age":
		if p.age + 1 < AGES.size():
			refund = (AGES[p.age + 1].cost as Dictionary).duplicate()
		else:
			refund = refund.duplicate()
		p.pendingOath = null
	b.productionQueue.remove_at(index)
	p.resources.food += refund.food
	p.resources.wood += refund.wood
	p.resources.gold += refund.gold
	if index == 0 and b.productionQueue.size() > 0:
		var next: String = b.productionQueue[0]
		var nd = _udef(next.substr(2)) if next.begins_with("u:") else null
		b.productionTime = itemTime(next, p.age) * (trainMult(p, nd) if nd != null else 1.0)
	elif b.productionQueue.size() == 0:
		b.productionTime = 0.0
	return true


func setRally(buildingId: int, x: float, y: float) -> void:
	var b = byId.get(buildingId)
	if b != null and b.alive and b.kind == T.Kind.Building:
		b.rallyX = x
		b.rallyY = y


## What a rally point *means*, as opposed to where it is.
##
## A rally dropped on something is a standing instruction about that thing, not
## a coordinate that happens to sit on top of it: on a resource the villagers
## that come out gather it, on a building site or a damaged wall they go and
## work on it, and on bare ground everyone just walks there. Doing this at
## spawn rather than at drop time is deliberate — the state of the target can
## change between setting the rally and the unit appearing.
func applyRally(u: Entity, b: Entity, type: String) -> void:
	var target = entityAt(b.rallyX, b.rallyY)
	var ud = _udef(type)
	var canGather: bool = ud != null and ud.get("canGather", false)
	if target != null and target.alive and canGather:
		if target.kind == T.Kind.Resource:
			issueGather([u.id], target.id)
			return
		# Ours, and either still going up or hurt. A healthy finished building is
		# just a landmark to stand next to, so that falls through to a move.
		var needsWork: bool = target.kind == T.Kind.Building and target.team == b.team \
			and (target.buildState != T.BuildState.Done or target.hp < target.maxHp)
		if needsWork:
			issueBuildRepair([u.id], target.id)
			return
	issueMove([u.id], b.rallyX, b.rallyY)


func garrison(ids: Array, buildingId: int) -> void:
	var b = byId.get(buildingId)
	if b == null or not b.alive:
		return
	var bd = BUILDINGS.get(b.type)
	var cap: int = int(bd.get("garrisonCap", 0)) if bd != null else 0
	for id in ids:
		if b.garrison.size() >= cap:
			break
		var e = byId.get(int(id))
		if e == null or not e.alive or e.kind != T.Kind.Unit or e.team != b.team:
			continue
		if MathU.dist(e.x, e.y, b.x, b.y) > b.radius + 60:
			# walk there first; garrison via order target re-issue when close
			resolveOrderQueue(e, Order.make(T.OrderKind.Move, b.x, b.y, buildingId), false)
			continue
		e.alive = false # hidden while garrisoned (not dead — kept in garrison list)
		b.garrison.append(e.id)


func ungarrison(buildingId: int) -> void:
	var b = byId.get(buildingId)
	if b == null:
		return
	for id in b.garrison:
		var e = byId.get(id)
		if e == null:
			continue
		var o := grid.nearestOpenWorld(
			b.x + rng.range(-b.radius - 20.0, b.radius + 20.0),
			b.y + b.radius + 24.0,
		)
		e.alive = true
		e.x = o[0]
		e.y = o[1]
		e.order = Order.make(T.OrderKind.Idle, 0.0, 0.0, -1)
	b.garrison.clear()


## Buy or sell 100 of a commodity at the Market.
##
## The rate *moves*. Every sale pushes a commodity's price down and every
## purchase pushes it up, drifting back toward par over the following minutes.
## The band is deliberately narrow (±40%). This is a smoothing tool for a
## lopsided economy, and a market you can break by spamming one key would just
## be a different exploit.
## action: "sell_wood" | "sell_food" | "buy_wood" | "buy_food"
func marketTrade(team: int, action: String) -> bool:
	var p := _p(team)
	if p == null or not hasBuilding(team, "market"):
		return false
	var r := p.resources
	var kind := "wood" if action.ends_with("wood") else "food"
	var selling := action.begins_with("sell")
	var quote := marketQuote(team, kind)
	if selling:
		if r[kind] < 100:
			return false
		r[kind] -= 100.0
		r.gold += quote.sell
	else:
		var cost: float = marketQuote(team, kind).buy
		if r.gold < cost:
			return false
		r.gold -= cost
		r[kind] += 100.0
	# Each trade shifts the price against whoever just made it.
	var at: float = p.marketPressure.get(kind, 0.0)
	p.marketPressure[kind] = maxf(-1.0, minf(1.0, at + (-0.06 if selling else 0.06)))
	return true


## The multiplier on a commodity's Market rate right now, 0.6–1.4.
func marketPrice(team: int, kind: String) -> float:
	var p := _p(team)
	if p == null:
		return 1.0
	return 1.0 + float(p.marketPressure.get(kind, 0.0)) * 0.4


## What 100 of a commodity fetches, and what 100 costs, right now: {sell, buy}.
##
## Both move the *same* way with the price: when wood is dear you are paid more
## for selling it and charged more for buying it. The spread between the two is
## what the house takes, and it is a constant ratio, which is also what
## guarantees a round trip can never make money at any point on the curve.
func marketQuote(team: int, kind: String) -> Dictionary:
	var tp := _p(team)
	var base := 75.0 + teamUpgradeSum(team, "trade") + (tp.oath.tradeRateBonus if tp != null else 0.0)
	var price := marketPrice(team, kind)
	return {
		"sell": JS.round(base * price),
		"buy": maxf(1.0, JS.round((10000.0 / base) * price)),
	}


func canAfford(bag: Dictionary, cost: Dictionary) -> bool:
	return bag.food >= cost.food and bag.wood >= cost.wood and bag.gold >= cost.gold


## on: "units" | "buildings" | "tech" (or null)
func pay(bag: Dictionary, cost: Dictionary, team = null, on = null) -> void:
	bag.food -= cost.food
	bag.wood -= cost.wood
	bag.gold -= cost.gold
	# The other half of the ledger. "Gathered 4000" says nothing on its own;
	# "gathered 4000, spent 3100 of it, 2200 on units" is a read of how someone
	# actually played.
	if team != null and on != null:
		var tp := _p(team)
		if tp != null:
			var st := tp.stats
			var total: float = cost.food + cost.wood + cost.gold
			st.spentOn[on] += total
			st.resourcesSpent += total


## Cost scaled by a multiplier (boons: cheaper units/buildings), rounded.
func scaledCost(cost: Dictionary, mult: float) -> Dictionary:
	if mult == 1.0:
		return cost
	return {"food": JS.round(cost.food * mult), "wood": JS.round(cost.wood * mult), "gold": JS.round(cost.gold * mult)}


# ----------------------------------------------------------------- tick --

func rebuildTerritory() -> void:
	var cell := TERR_CELL * TILE
	var cols := int(ceil(worldW / cell))
	var rows := int(ceil(worldH / cell))
	if territory == null or territory.size() != cols * rows:
		territory = PackedInt32Array()
		territory.resize(cols * rows)
	territoryCols = cols
	var owner: PackedInt32Array = territory
	owner.fill(-1)
	var best := PackedFloat32Array()
	best.resize(cols * rows)
	best.fill(INF)
	for b: Entity in entities:
		if not b.alive or b.kind != T.Kind.Building or b.team < 0 or b.team >= numTeams:
			continue
		var reach := (18.0 if b.type == "town_center" else (14.0 if b.type == "castle" else 9.0)) * TILE
		var c0 := maxi(0, int(floor((b.x - reach) / cell)))
		var c1 := mini(cols - 1, int(floor((b.x + reach) / cell)))
		var r0 := maxi(0, int(floor((b.y - reach) / cell)))
		var r1 := mini(rows - 1, int(floor((b.y + reach) / cell)))
		for r in range(r0, r1 + 1):
			for c in range(c0, c1 + 1):
				var d := JS.hypot((c + 0.5) * cell - b.x, (r + 0.5) * cell - b.y)
				if d > reach:
					continue
				var i := r * cols + c
				if d < best[i]:
					best[i] = d
					owner[i] = b.team


## Where a hit by `team` at (x, y) lands: on the enemy's ground, its own, or
## neither — "attack" | "defend" | "field".
func zoneOf(team: int, x: float, y: float) -> String:
	if territory == null:
		rebuildTerritory()
	var cell := TERR_CELL * TILE
	var c := int(floor(x / cell))
	var r := int(floor(y / cell))
	if c < 0 or r < 0 or c >= territoryCols:
		return "field"
	var i := r * territoryCols + c
	if i >= territory.size():
		return "field"
	var o: int = territory[i]
	if o < 0:
		return "field"
	return "defend" if areAllied(team, o) else "attack"


func tick() -> void:
	tickCount += 1
	time += SIM_DT
	if tickCount % (SIM_HZ * 2) == 1:
		rebuildTerritory()

	# Market prices creep back toward par. Once a second is plenty — the drift
	# is measured in minutes and doing it every tick would be 20x the work for
	# a number nobody can see move.
	if tickCount % SIM_HZ == 0:
		for p: PlayerState in players:
			if p == null:
				continue
			for k in ["wood", "food"]:
				var v: float = p.marketPressure[k]
				if v != 0:
					p.marketPressure[k] = 0.0 if absf(v) < 0.005 else v * MARKET_RECOVERY

	# Oath of the Coin: every Market pays a tithe, up to a few of them. Once a
	# second, for the same reason as above.
	if tickCount % SIM_HZ == 0:
		for p: PlayerState in players:
			if p == null or p.oath.tithePerMarket <= 0 or p.defeated:
				continue
			var markets := 0
			for e: Entity in entities:
				if e.alive and e.team == p.team and e.type == "market" and e.buildState == T.BuildState.Done:
					markets += 1
			var gold := p.oath.tithePerMarket * minf(TITHE_MAX_MARKETS, markets)
			if gold > 0:
				p.resources.gold += gold
				p.stats.gathered += gold
				p.stats.gatheredBy.gold += gold

	# Snapshot positions so the renderer can interpolate between ticks (smooth
	# 60fps motion over the 20Hz sim). Done before anything moves this tick.
	for e: Entity in entities:
		if not e.alive or e.kind == T.Kind.Resource:
			continue # (resource nodes never move, so prevX/prevY already equal x/y)
		# A charger's run-up: distance covered last tick, bled away while it
		# stands, so a Lancer that has been idling isn't still "charging".
		if e.kind == T.Kind.Unit and CHARGE_TYPES.has(e.type):
			var moved := JS.hypot(e.x - e.prevX, e.y - e.prevY)
			e.chargeRun = minf(CHARGE_RUN * 2.0, e.chargeRun + moved) if moved > 0.5 else maxf(0.0, e.chargeRun - 80.0 * SIM_DT)
		e.prevX = e.x
		e.prevY = e.y

	# Rebuild spatial hash.
	spatial.clear()
	for e: Entity in entities:
		if e.alive:
			spatial.insert(e)

	var dt := SIM_DT
	for e: Entity in entities:
		if not e.alive:
			continue
		# max(0, t - dt) leaves a timer that is already 0 at 0, so those (nearly
		# all of them, nearly always) are left alone rather than rewritten.
		if e.attackCooldown != 0.0:
			e.attackCooldown = maxf(0.0, e.attackCooldown - dt)
		if e.abilityCooldown != 0.0:
			e.abilityCooldown = maxf(0.0, e.abilityCooldown - dt)
		if e.abilityActive != 0.0:
			e.abilityActive = maxf(0.0, e.abilityActive - dt)
		if e.slowTimer != 0.0:
			e.slowTimer = maxf(0.0, e.slowTimer - dt)
		if e.rallyTimer != 0.0:
			e.rallyTimer = maxf(0.0, e.rallyTimer - dt)
		if e.guardTimer != 0.0:
			e.guardTimer = maxf(0.0, e.guardTimer - dt)
		if e.hitFlash != 0.0:
			e.hitFlash = maxf(0.0, e.hitFlash - dt * 3.0)
		match e.kind:
			T.Kind.Unit:
				tickUnit(e)
			T.Kind.Building:
				tickBuilding(e)
			T.Kind.Projectile:
				tickProjectile(e)

	Steering.resolveOverlaps(entities, spatial, SIM_DT)

	# Keep units inside the world and off blocked cells.
	for e: Entity in entities:
		if not e.alive or e.kind != T.Kind.Unit:
			continue
		e.x = maxf(8.0, minf(worldW - 8.0, e.x))
		e.y = maxf(8.0, minf(worldH - 8.0, e.y))
		if grid.isBlockedWorld(e.x, e.y):
			var o := grid.nearestOpenWorld(e.x, e.y, 4)
			e.x = o[0]
			e.y = o[1]

	if tickCount % 5 == 0:
		recomputeVision()
		recomputeConcealment()
	if tickCount % SIM_HZ == 0:
		samplePlayerPeaks()
	if mode != "conquest":
		tickGameMode()
	if tickCount % 20 == 0:
		checkVictory()
	if tickCount % 600 == 0:
		compact()
	tickHeroRespawns()
	tickBanners()


## Bring fallen Champions back at their Town Center once the timer elapses.
func tickHeroRespawns() -> void:
	for t in numTeams:
		var p := _p(t)
		if p == null or p.heroState != "respawning":
			continue
		p.heroRespawnTimer -= SIM_DT
		if p.heroRespawnTimer > 0:
			continue
		var tc: Entity = null
		for e: Entity in entities:
			if e.alive and e.team == t and e.kind == T.Kind.Building and \
					e.type == "town_center" and e.buildState == T.BuildState.Done:
				tc = e
				break
		if tc == null:
			p.heroRespawnTimer = 0.0 # wait for a Town Center to exist
			continue
		var s := grid.nearestOpenWorld(tc.x, tc.y + tc.radius + 20.0)
		var u := spawnUnit(t, "hero", s[0], s[1])
		applyHeroLevel(u, p.heroLevel)
		p.heroState = "alive"
		emit("spawn", s[0], s[1], t, "hero")


# Units ------------------------------------------------------------------

func tickUnit(e: Entity) -> void:
	var def: Dictionary = UNITS[e.type]
	e.animPhase += SIM_DT * (1.0 + (2.0 if JS.hypot(e.vx, e.vy) > 4 else 0.0))

	# Field Medic boon: units mend out of combat (5s since last hit).
	var tp := _p(e.team)
	var rgn: float = tp.boon.regenRate if tp != null else 0.0
	if rgn > 0 and e.hp < e.maxHp and time - e.lastDamageTime > 5:
		e.hp = minf(e.maxHp, e.hp + rgn * SIM_DT)

	match e.order.kind:
		T.OrderKind.Idle, T.OrderKind.Hold:
			autoAcquire(e, def, e.order.kind == T.OrderKind.Hold)
			if def.get("healer", false):
				healNearby(e)
			e.vx = 0.0
			e.vy = 0.0
		T.OrderKind.Move:
			stepMove(e, def, false)
		T.OrderKind.AttackMove:
			# Engage anything in sight — and always answer whatever's shooting us
			# (so the march isn't kited by ranged units out past our scan).
			if tickCount % 3 == e.id % 3:
				var enemy = retaliationTarget(e)
				if enemy == null:
					enemy = findEnemyInRange(e, maxf(e.visionRange, e.range + 60.0))
				if enemy != null:
					var am := e.order
					e.order = Order.make(T.OrderKind.Attack, enemy.x, enemy.y, enemy.id, [am.copy()])
					e.path = null
					return
			stepMove(e, def, false)
		T.OrderKind.Attack:
			stepAttack(e, def)
		T.OrderKind.Gather:
			stepGather(e, def)
		T.OrderKind.Return:
			stepReturn(e, def)
		T.OrderKind.Build, T.OrderKind.Repair:
			stepBuildRepair(e, def)
		T.OrderKind.Trade:
			stepTrade(e, def)


func finishOrder(e: Entity) -> void:
	var q: Array = e.order.queue
	if q.size() > 0:
		var next: Order = q.pop_front()
		next.queue = q
		e.order = next
	else:
		e.order = Order.make(T.OrderKind.Idle, 0.0, 0.0, -1)
	e.path = null
	e.pathIndex = 0
	e.vx = 0.0
	e.vy = 0.0


## Move one tick in a direction, with the same separation and wall handling a
## normal move gets, but no goal, path or flow field. Used for giving ground:
## the unit is repositioning within a fight, not travelling anywhere.
func driveAway(e: Entity, dirX: float, dirY: float) -> void:
	var moveSpeed := effectiveSpeed(e)
	var neighbors := spatial.query(e.x, e.y, 40.0)
	var s := Steering.applySeparation(e, neighbors, dirX * moveSpeed, dirY * moveSpeed)
	var sx: float = s[0]
	var sy: float = s[1]
	var sl := JS.hypot(sx, sy)
	if sl == 0.0:
		sl = 1.0
	var spd := minf(moveSpeed, sl)
	e.vx = (sx / sl) * spd
	e.vy = (sy / sl) * spd
	var nx := e.x + e.vx * SIM_DT
	var ny := e.y + e.vy * SIM_DT
	if not grid.isBlockedWorld(nx, ny):
		e.x = nx
		e.y = ny
	elif not grid.isBlockedWorld(nx, e.y):
		e.x = nx
	elif not grid.isBlockedWorld(e.x, ny):
		e.y = ny


## Is this something a skirmisher should back away from? Only units that have
## to reach us to hurt us — there is nothing to gain by retreating from
## another archer, and buildings do not chase.
func closesToMelee(target: Entity) -> bool:
	if target.kind != T.Kind.Unit:
		return false
	var td = _udef(target.type)
	return td != null and td.attack > 0 and td.range <= MELEE_RANGE


## Move toward order.tx/ty using flow field (groups) or A* path (singles).
func stepMove(e: Entity, def: Dictionary, ignoreArrival: bool) -> bool:
	var flow = e.order.flow
	var dx := e.order.tx - e.x
	var dy := e.order.ty - e.y
	var d := JS.hypot(dx, dy)

	# Arrival check: flow-field groups stop when near goal or crowded by
	# arrived allies; singles when close. Formation ("slot") moves settle on
	# their own slot, only honouring the shared goal once they're close to it.
	var isSlot := e.order.slot
	var arriveDist := (15.0 if isSlot else 26.0) if flow != null else 10.0
	var reachedGoal: bool = flow != null and flow.reached(e.x, e.y)
	if d < arriveDist or (reachedGoal and (not isSlot or d < 55)):
		# If this was a "walk to building then garrison" move, complete it.
		if e.order.target != -1:
			var b = byId.get(e.order.target)
			if b != null and b.alive and b.kind == T.Kind.Building and b.team == e.team:
				var bd = BUILDINGS.get(b.type)
				var cap: int = int(bd.get("garrisonCap", 0)) if bd != null else 0
				if b.garrison.size() < cap and MathU.dist(e.x, e.y, b.x, b.y) < b.radius + 70:
					e.alive = false
					b.garrison.append(e.id)
					return true
		finishOrder(e)
		return true

	var desX := 0.0
	var desY := 0.0
	if flow != null:
		var f: Array = flow.sample(e.x, e.y)
		var fx: float = f[0]
		var fy: float = f[1]
		if fx == 0 and fy == 0:
			var dd := d if d != 0.0 else 1.0
			desX = dx / dd
			desY = dy / dd
		else:
			desX = fx
			desY = fy
	else:
		# A* path following.
		e.repathCooldown -= SIM_DT
		if e.path == null and e.repathCooldown <= 0:
			e.path = AStar.findPath(grid, e.x, e.y, e.order.tx, e.order.ty)
			e.pathIndex = 0
			e.repathCooldown = 0.8
			if e.path == null:
				finishOrder(e)
				return true
		if e.path != null:
			var path: Array = e.path
			# advance waypoint
			while e.pathIndex < path.size() and \
					MathU.dist(e.x, e.y, path[e.pathIndex], path[e.pathIndex + 1]) < 14:
				e.pathIndex += 2
			if e.pathIndex >= path.size():
				finishOrder(e)
				return true
			var wx: float = path[e.pathIndex]
			var wy: float = path[e.pathIndex + 1]
			var wd := MathU.dist(e.x, e.y, wx, wy)
			if wd == 0.0:
				wd = 1.0
			desX = (wx - e.x) / wd
			desY = (wy - e.y) / wd

	var moveSpeed := effectiveSpeed(e)
	var neighbors := spatial.query(e.x, e.y, 40.0)
	var s := Steering.applySeparation(e, neighbors, desX * moveSpeed, desY * moveSpeed)
	var sx: float = s[0]
	var sy: float = s[1]
	var sl := JS.hypot(sx, sy)
	if sl == 0.0:
		sl = 1.0
	var spd := minf(moveSpeed, sl)
	e.vx = (sx / sl) * spd
	e.vy = (sy / sl) * spd

	var nx := e.x + e.vx * SIM_DT
	var ny := e.y + e.vy * SIM_DT
	# Don't walk into blocked cells (slide along).
	if not grid.isBlockedWorld(nx, ny):
		e.x = nx
		e.y = ny
	elif not grid.isBlockedWorld(nx, e.y):
		e.x = nx
	elif not grid.isBlockedWorld(e.x, ny):
		e.y = ny
	elif flow == null:
		e.path = null # wedged against a building corner — replan immediately
		e.repathCooldown = 0.0
	if JS.hypot(e.vx, e.vy) > 1:
		e.facing = JSAtan.atan2(e.vy, e.vx)
	return false


func stepAttack(e: Entity, def: Dictionary) -> void:
	var target = byId.get(e.order.target)
	if target == null or not target.alive or target.hp <= 0:
		finishOrder(e)
		return
	e.order.tx = target.x
	e.order.ty = target.y
	# High ground shoots further, a wood shoots shorter. Melee reach is
	# unaffected — a hill doesn't lengthen a sword.
	var ranged: bool = def.ranged
	var reach := e.range * terrainSight(e) if ranged else 6.0
	var contact: float = e.radius + target.radius + reach
	var d := MathU.dist(e.x, e.y, target.x, target.y)

	# Skirmish: give ground rather than let a melee unit close, then plant and
	# shoot again — move, stand, shoot. It cannot fire while moving, so every
	# step backwards is damage not dealt; and a skirmisher only *holds* the gap
	# against infantry and loses it outright to cavalry — the counter triangle
	# enforcing itself through speed rather than through bonus damage.
	if e.stance == T.Stance.Skirmish and ranged and e.range > 0 and closesToMelee(target):
		var tooClose: float = e.radius + target.radius + KITE_GAP
		# Only give ground while reloading. The moment the shot is ready it
		# plants and fires — move, stand, shoot.
		var reloading := e.attackCooldown > 0 or d > contact
		if d < tooClose and reloading:
			# Steer straight away, rather than routing the retreat through
			# stepMove: that follows the flow field the attack order arrived with,
			# which points at the very unit we are backing away from, and its A*
			# fallback crawls on a hop this short.
			var away := JSAtan.atan2(e.y - target.y, e.x - target.x)
			driveAway(e, JSMath.cos(away), JSMath.sin(away))
			e.facing = JSAtan.atan2(target.y - e.y, target.x - e.x) # keep it covered
			return # no shot this tick — the price of giving ground

	if d <= contact:
		e.vx = 0.0
		e.vy = 0.0
		e.facing = JSAtan.atan2(target.y - e.y, target.x - e.x)
		if e.attackCooldown <= 0 and def.attack > 0:
			e.attackCooldown = effectiveInterval(e)
			if ranged:
				fireProjectile(e, target, def)
				emit("siege" if def.id == "catapult" else "bow", e.x, e.y, e.team)
			else:
				var dmg := effectiveAttack(e, def, target)
				var charge: float = def.get("charge", 0.0)
				if charge:
					# The couched lance: a blow at the end of a run lands with the
					# weight of the horse behind it.
					if e.chargeRun >= CHARGE_RUN:
						dmg *= charge
						emit("charge", target.x, target.y, e.team, def.id)
					e.chargeRun = 0.0
				dealDamage(e.team, target, dmg, def.id, e.id)
				emit("sword", target.x, target.y, e.team)
	elif e.stance == T.Stance.StandGround:
		# Hold the line: don't chase a target that has stepped out of reach.
		e.vx = 0.0
		e.vy = 0.0
		finishOrder(e)
	else:
		stepMove(e, def, true)
		# stepMove may finish the order on arrival; re-target next tick.
		if e.order.kind == T.OrderKind.Idle:
			e.order = Order.make(T.OrderKind.Attack, target.x, target.y, target.id)


## Is villager `v` currently the worker bound to `farm` (gathering it or returning from it)?
func worksFarm(v: Entity, farm: Entity) -> bool:
	if v.order.target == farm.id:
		return true
	if v.order.kind == T.OrderKind.Return:
		for o: Order in v.order.queue:
			if o.target == farm.id:
				return true
	# Queued to build it (shift-placed after another job): still theirs.
	if farm.buildState != T.BuildState.Done:
		for o: Order in v.order.queue:
			if o.target == farm.id and o.kind == T.OrderKind.Build:
				return true
	return false


## The soil in a field is spent. Take the plot away, and — if the player has
## asked for it and the wood is there — lay a fresh foundation on the same
## ground with the same villager already assigned to raise it.
##
## Re-seeding on the spot rather than anywhere free matters: a farm's value is
## mostly its walking distance to the drop-off, so a field that reappears
## somewhere else is a worse field. If the wood isn't there the farmer is
## reassigned like any other bumped villager rather than left standing in a
## bare patch of dirt.
func exhaustFarm(farm: Entity, worker: Entity) -> void:
	var p := _p(farm.team)
	var x := farm.x
	var y := farm.y
	var def: Dictionary = BUILDINGS["farm"]
	farm.alive = false
	farm.farmWorker = -1
	grid.stampFootprint(x, y, def.tiles, false)
	emit("exhaust", x, y, farm.team, "farm")
	# Plough-sworn fields replant without the wood: the refund lands before
	# the placement charges it, so the books balance to nothing.
	var free: bool = p != null and p.oath.freeReseed
	if p != null and p.autoReseed and (free or canAfford(p.resources, def.cost)):
		if free:
			p.resources.food += def.cost.food
			p.resources.wood += def.cost.wood
			p.resources.gold += def.cost.gold
		var fresh := placeBuilding(farm.team, "farm", x, y)
		if fresh == null and free:
			p.resources.food -= def.cost.food
			p.resources.wood -= def.cost.wood
			p.resources.gold -= def.cost.gold
		if fresh != null:
			issueBuildRepair([worker.id], fresh.id)
			return
	reassignFarmer(worker, farm, true)


## A villager just finished a building and has nothing queued: put it to the
## obvious work. A farm is farmed by whoever built it (the first to finish
## claims it; other builders take the nearest free farm). A Lumber Camp,
## Mining Camp or Mill sends its builder to the nearest wood, gold or berries.
func afterBuilt(e: Entity, b: Entity) -> void:
	if e.order.kind != T.OrderKind.Idle:
		return
	var ud = _udef(e.type)
	if ud == null or not ud.get("canGather", false):
		return
	if b.type == "farm":
		var owner = byId.get(b.farmWorker)
		if owner == null or not owner.alive or owner.id == e.id or not worksFarm(owner, b):
			b.farmWorker = e.id
			e.order = Order.make(T.OrderKind.Gather, b.x, b.y, b.id)
			e.path = null
		else:
			reassignFarmer(e, b)
		return
	var nodeFor := {"lumber_camp": "tree", "mining_camp": "gold_mine", "mill": "berries"}
	var want = nodeFor.get(b.type)
	if want == null:
		return
	var node = findNearbyResource(b.x, b.y, want, 8.0 * TILE)
	if node != null:
		e.order = Order.make(T.OrderKind.Gather, node.x, node.y, node.id)
		e.path = null


## A villager bumped off a taken farm: send it to the nearest free farm, else
## nearby food, else (if its field is gone) any resource close by, else idle.
func reassignFarmer(e: Entity, fromFarm: Entity, fieldGone := false) -> void:
	var bestFarm: Entity = null
	var bestFd := INF
	for f: Entity in entities:
		if not f.alive or f.kind != T.Kind.Building or f.type != "farm" or f.team != e.team:
			continue
		if f.id == fromFarm.id or f.buildState != T.BuildState.Done:
			continue
		var owner = byId.get(f.farmWorker)
		if owner != null and owner.alive and owner.id != e.id and worksFarm(owner, f):
			continue
		var d := MathU.dist2(e.x, e.y, f.x, f.y)
		if d < bestFd:
			bestFd = d
			bestFarm = f
	if bestFarm != null:
		bestFarm.farmWorker = e.id
		e.order = Order.make(T.OrderKind.Gather, bestFarm.x, bestFarm.y, bestFarm.id)
		e.path = null
		return
	# No free farm — gather food nearby (berries) if there is any; otherwise
	# wait by the farm. It used to take the nearest resource of *any* kind,
	# which could be a gold mine across the map: a farmer should stay a farmer.
	# A field that ran out with nothing to replant it is different: the farmer
	# has no farm to go back to, so any work close by beats standing in the dirt.
	var bestR = findNearbyResource(fromFarm.x, fromFarm.y, "berries", 12.0 * TILE)
	if bestR == null and fieldGone:
		bestR = nearestAnyResource(fromFarm.x, fromFarm.y, 12.0 * TILE)
	if bestR != null:
		e.order = Order.make(T.OrderKind.Gather, bestR.x, bestR.y, bestR.id)
		e.path = null
	else:
		finishOrder(e)


func stepGather(e: Entity, def: Dictionary) -> void:
	var node = byId.get(e.order.target)
	if node == null or not node.alive or (node.kind == T.Kind.Resource and node.amount <= 0):
		# Find another node of the same kind nearby, else go idle.
		var nextNode = findNearbyResource(node.x, node.y, node.type, 200.0) if node != null else null
		if nextNode != null:
			e.order = Order.make(T.OrderKind.Gather, nextNode.x, nextNode.y, nextNode.id, e.order.queue)
		elif e.carry > 0:
			startReturn(e)
		else:
			finishOrder(e)
		return
	var isFarm: bool = node.kind == T.Kind.Building and node.type == "farm"
	if isFarm and node.buildState != T.BuildState.Done:
		# Sent to farm a field that isn't finished: help raise it, then farm it
		# (or, if its builder keeps it, take the nearest free one).
		if def.get("canBuild", false) and node.team == e.team:
			e.order = Order.make(T.OrderKind.Build, node.x, node.y, node.id, e.order.queue)
		else:
			finishOrder(e)
		return
	# One farmer per farm: if another villager already works this farm, this one
	# is bumped to the nearest free farm (or any other resource).
	if isFarm:
		var owner = byId.get(node.farmWorker)
		var taken: bool = owner != null and owner.alive and owner.id != e.id and worksFarm(owner, node)
		if taken:
			reassignFarmer(e, node)
			return
		node.farmWorker = e.id # claim it
	var kind = "food" if isFarm else RES_TYPE_KIND.get(node.type)
	if kind == null:
		finishOrder(e)
		return
	var d := MathU.dist(e.x, e.y, node.x, node.y)
	# Farms are walkable, so the farmer stands *on* the plot (near its centre) —
	# it reads unmistakably as "this villager is farming". Other nodes block the
	# grid, so reach a tile out to work them from any adjacent open cell.
	# For farms the work point is the plot centre; the reach sits just past the
	# mover's arrival distance so the farmer settles on the plot and harvests
	# (too tight and it parks at the edge without ever gathering).
	var reach: float = TILE * 0.6 if isFarm else e.radius + node.radius + TILE * 0.7
	if d > reach:
		e.order.tx = node.x
		e.order.ty = node.y
		stepMove(e, def, true)
		if e.order.kind == T.OrderKind.Idle:
			e.order = Order.make(T.OrderKind.Gather, node.x, node.y, node.id)
		return
	e.vx = 0.0
	e.vy = 0.0
	e.facing = JSAtan.atan2(node.y - e.y, node.x - e.x)
	e.carryKind = kind
	e.gatherCooldown -= SIM_DT
	if e.gatherCooldown <= 0:
		var gp := _p(e.team)
		var econ: float = gp.econMult if gp != null else 1.0
		if gp != null:
			if gp.upgrades.has("wheelbarrow"):
				econ *= 1.0 + UPGRADES.wheelbarrow.amount
			if gp.upgrades.has("hand_cart"):
				econ *= 1.0 + UPGRADES.hand_cart.amount
			# Resource-specific eco upgrades (Horse Collar / Bow Saw / Gold Mining).
			for upId in gp.upgrades:
				var up = UPGRADES.get(upId)
				if up != null and up.kind == "econ" and up.get("resource") == kind:
					econ *= 1.0 + up.amount
			econ *= gp.gatherMultC # commander passive
			if kind == "food":
				econ *= gp.boon.foodGatherMult # Bountiful Fields
			if kind == "gold":
				econ *= gp.oath.goldGatherMult # Oath of the Coin
			if kind == "wood":
				econ *= gp.oath.woodGatherMult # the Jarls
		# Rally banner boosts gathering for villagers standing in its radius.
		var banner = bannerAt(e.team, e.x, e.y)
		if banner != null:
			econ *= banner.power.villagerGatherMult
		e.gatherCooldown = (GATHER_TICK / econ) * (FARM_TICK_MULT if isFarm else 1.0)
		var cap := VILLAGER_CARRY_CAP * (gp.boon.villCarryMult if gp != null else 1.0) # Wayfarers
		var take := minf(GATHER_RATE * GATHER_TICK * 10.0, node.amount) # chunked
		var got := minf(minf(take, cap - e.carry), 1.2)
		e.carry += got
		node.amount -= got
		if node.amount <= 0:
			if isFarm:
				exhaustFarm(node, e)
			else:
				node.alive = false
				grid.stampFootprint(node.x, node.y, 1, false)
		if e.carry >= cap:
			startReturn(e)


func startReturn(e: Entity) -> void:
	var drop = findNearestDropoff(e.team, e.x, e.y, e.carryKind)
	if drop == null:
		finishOrder(e)
		return
	# Remember the gather order so we resume after depositing.
	var back := e.order.copy()
	back.queue = []
	var q: Array = [back]
	q.append_array(e.order.queue)
	e.order = Order.make(T.OrderKind.Return, drop.x, drop.y, drop.id, q)
	e.path = null


func stepReturn(e: Entity, def: Dictionary) -> void:
	var drop = byId.get(e.order.target)
	if drop == null or not drop.alive:
		var nd = findNearestDropoff(e.team, e.x, e.y, e.carryKind if e.carryKind != null else "food")
		if nd == null:
			finishOrder(e)
			return
		e.order.target = nd.id
		e.order.tx = nd.x
		e.order.ty = nd.y
		return
	var d := MathU.dist(e.x, e.y, drop.x, drop.y)
	if d <= e.radius + drop.radius + 10:
		var p := _p(e.team)
		if p != null and e.carryKind != null:
			var amt := JS.round(e.carry)
			p.resources[e.carryKind] += amt
			p.stats.gathered += amt
			p.stats.gatheredBy[e.carryKind] += amt
			emit("deposit", drop.x, drop.y, e.team, e.carryKind)
		e.carry = 0.0
		finishOrder(e) # resumes the queued Gather order
	else:
		stepMove(e, def, true)
		if e.order.kind == T.OrderKind.Idle:
			# re-issue (stepMove finished early)
			var nd = byId.get(drop.id)
			if nd != null:
				e.order = Order.make(T.OrderKind.Return, nd.x, nd.y, nd.id)


## A Trade Cart shuttling between two Markets.
##
## Pays on arrival, by the distance between the two Markets — so trade is a
## *route*, not a building. The cart holds the two ends in `order.target`
## (where it is going) and `order.tx/ty` (the position it is heading for),
## flipping between them. If either Market falls the route is gone and the cart
## goes idle rather than walking to a ruin.
func stepTrade(e: Entity, def: Dictionary) -> void:
	var dest = byId.get(e.order.target)
	var home = byId.get(e.tradeHomeId)
	if dest == null or not dest.alive or home == null or not home.alive:
		finishOrder(e)
		return
	# Keep the walk target on the Market itself; a Market that is razed and
	# rebuilt elsewhere is a different building and the route ends with it.
	e.order.tx = dest.x
	e.order.ty = dest.y
	if MathU.dist(e.x, e.y, dest.x, dest.y) > dest.radius + e.radius + 14:
		stepMove(e, def, true)
		# stepMove finishes the order when it arrives; put the trade order back.
		if e.order.kind != T.OrderKind.Trade:
			e.order = Order.make(T.OrderKind.Trade, dest.x, dest.y, dest.id)
		return
	# Arrived. Pay out and turn around.
	var p := _p(e.team)
	if p != null:
		var legs := MathU.dist(home.x, home.y, dest.x, dest.y)
		var bonus := 1.0 + teamUpgradeSum(e.team, "trade") / 100.0 # Caravan
		var tiles := legs / TILE
		# Math.pow: V8 and the platform pow can differ in the last bit; the payout
		# is rounded to whole gold, so that can only matter on an exact .5 tie.
		var gold := maxf(1.0, JS.round(TRADE_GOLD_PER_TILE * pow(tiles, TRADE_GOLD_EXPONENT) * bonus * p.oath.tradeMult))
		p.resources.gold += gold
		p.stats.gathered += gold
		p.stats.gatheredBy.gold += gold
		emit("deposit", e.x, e.y, e.team, "gold")
	# Swap ends: the place it just left becomes the destination.
	var nextTarget := e.tradeHomeId
	e.tradeHomeId = dest.id
	var back = byId.get(nextTarget)
	e.order = Order.make(T.OrderKind.Trade, back.x if back != null else e.x, back.y if back != null else e.y, nextTarget)
	e.path = null


## Put a cart on the best route it can reach: the furthest Market of ours or an
## ally's that isn't the one it is standing at. Furthest, because the pay is
## the distance — a cart that picks the nearest Market is a cart that earns
## nothing.
func assignTradeRoute(ids: Array) -> bool:
	var any := false
	for id in ids:
		var cart = byId.get(int(id))
		if cart == null or not cart.alive or cart.type != "trade_cart":
			continue
		var markets: Array = entities.filter(func(m): return m.alive and m.kind == T.Kind.Building and m.type == "market" \
			and m.buildState == T.BuildState.Done and areAllied(cart.team, m.team))
		if markets.size() < 2:
			continue
		# Home is the nearest Market; the route runs to the furthest from it.
		JS.sort_by_cmp(markets, func(a, b): return MathU.dist(a.x, a.y, cart.x, cart.y) - MathU.dist(b.x, b.y, cart.x, cart.y))
		var home: Entity = markets[0]
		var far: Entity = markets[1]
		for m: Entity in markets:
			if m.id == home.id:
				continue
			if MathU.dist(m.x, m.y, home.x, home.y) > MathU.dist(far.x, far.y, home.x, home.y):
				far = m
		cart.tradeHomeId = home.id
		cart.order = Order.make(T.OrderKind.Trade, far.x, far.y, far.id)
		cart.path = null
		any = true
	return any


func stepBuildRepair(e: Entity, def: Dictionary) -> void:
	var b = byId.get(e.order.target)
	if b == null or not b.alive or b.kind != T.Kind.Building:
		finishOrder(e)
		return
	var bdef: Dictionary = BUILDINGS[b.type]
	if e.order.kind == T.OrderKind.Repair and b.hp >= b.maxHp:
		finishOrder(e)
		return
	var d := MathU.dist(e.x, e.y, b.x, b.y)
	if d > e.radius + b.radius + 12:
		stepMove(e, def, true)
		if e.order.kind == T.OrderKind.Idle:
			e.order = Order.make(T.OrderKind.Repair if b.buildState == T.BuildState.Done else T.OrderKind.Build, b.x, b.y, b.id)
		return
	e.vx = 0.0
	e.vy = 0.0
	e.facing = JSAtan.atan2(b.y - e.y, b.x - e.x)
	e.animPhase += SIM_DT * 2.0
	if b.buildState != T.BuildState.Done:
		var pp := _p(e.team)
		var mult: float = (pp.econMult if pp != null else 1.0) * (pp.buildMult if pp != null else 1.0) \
			* (1.0 + teamUpgradeSum(b.team, "build")) \
			* (pp.oath.buildSpeedMult if pp != null else 1.0)
		b.buildProgress += (SIM_DT / bdef.buildTime) * mult
		b.hp = minf(b.maxHp, b.hp + (b.maxHp * 0.92 * SIM_DT * mult) / bdef.buildTime)
		b.buildState = T.BuildState.UnderConstruction
		if tickCount % 10 == 0:
			emit("build", b.x, b.y, e.team)
		if b.buildProgress >= 1:
			b.buildState = T.BuildState.Done
			b.buildProgress = 1.0
			b.hp = b.maxHp
			onBuildingCompleted(b, bdef)
			finishOrder(e)
			afterBuilt(e, b)
	else:
		# Repairs go at the same pace as building (the Hearth's masons).
		var rp := _p(e.team)
		var wasBuilding := e.order.kind == T.OrderKind.Build # a helper arriving as it finished
		b.hp = minf(b.maxHp, b.hp + REPAIR_RATE * SIM_DT * (rp.oath.buildSpeedMult if rp != null else 1.0))
		if b.hp >= b.maxHp:
			finishOrder(e)
			if wasBuilding:
				afterBuilt(e, b)


## Whatever just hit us — so units fight back instead of being kited or
## marching obliviously while taking fire. Leashed so they don't chase forever.
func retaliationTarget(e: Entity):
	if e.lastAttackerId < 0 or time - e.lastDamageTime > 3:
		return null
	var a = byId.get(e.lastAttackerId)
	if a == null or not a.alive or a.kind != T.Kind.Unit or not areHostile(e.team, a.team):
		return null
	var leash := e.visionRange * 1.6
	if MathU.dist2(e.x, e.y, a.x, a.y) > leash * leash:
		return null
	return a


func autoAcquire(e: Entity, def: Dictionary, holdOrder: bool) -> void:
	if def.attack <= 0:
		return
	if tickCount % 5 != e.id % 5:
		return
	# Passive units hold fire entirely (only attack on explicit command).
	if e.stance == T.Stance.Passive:
		return
	# Stand Ground (stance or an explicit Hold order): only what's in range, no
	# chase and no retaliation pursuit.
	var standGround := holdOrder or e.stance == T.Stance.StandGround
	var enemy = null if standGround else retaliationTarget(e)
	# An idle villager hits back when struck, but doesn't go looking for a
	# fight: one that finished a bridge used to wander off and spend the next
	# minutes hacking at the enemy's bridge instead of working.
	if enemy == null and not def.get("canGather", false):
		var scanRange := maxf(e.range + 20.0, 60.0) if standGround else e.visionRange * 0.85
		enemy = findEnemyInRange(e, scanRange)
	if enemy != null:
		# Defensive returns to its post after the fight; Aggressive hunts on (no
		# home order, so it re-acquires the next foe); Stand Ground stays put.
		var q: Array = []
		if e.stance == T.Stance.Defensive and not standGround:
			q = [Order.make(T.OrderKind.Move, e.x, e.y, -1)]
		e.order = Order.make(T.OrderKind.Attack, enemy.x, enemy.y, enemy.id, q)


func healNearby(e: Entity) -> void:
	if tickCount % 10 != e.id % 10:
		return
	var near := spatial.query(e.x, e.y, 90.0)
	var best: Entity = null
	var bestFrac := 1.0
	for n: Entity in near:
		if not n.alive or n.team != e.team or n.kind != T.Kind.Unit or n == e:
			continue
		var frac := n.hp / n.maxHp
		if frac < bestFrac:
			bestFrac = frac
			best = n
	if best != null and bestFrac < 1:
		best.hp = minf(best.maxHp, best.hp + 4.0)


func findEnemyInRange(e: Entity, range: float):
	var near := spatial.query(e.x, e.y, range)
	var best: Entity = null
	var bestD := INF
	var et := e.team
	var bit := 1 << et
	var ex := e.x
	var ey := e.y
	var nTeams := numTeams
	# The TS checks, in a cheaper order (each is a pure filter, so the order
	# can't change the answer): kind first, since most of a crowd is trees.
	for n in near:
		var nk: int = n.kind
		if nk != T.Kind.Unit and nk != T.Kind.Building:
			continue
		if not n.alive or n.hp <= 0:
			continue
		# areHostile(et, nt), inlined: neither Neutral, not the same team, and
		# not sharing an alliance.
		var nt: int = n.team
		if nt == T.Team.Neutral or et == T.Team.Neutral or nt == et:
			continue
		if nt < nTeams and et < nTeams and alliances[et] == alliances[nt]:
			continue
		# You cannot pick a fight with something you cannot see. Without this a
		# longbowman would shoot a unit hidden in a wood from two hundred units
		# away, and the concealment would be decoration on the minimap.
		if nk == T.Kind.Unit and (n.spottedBy & bit) == 0:
			continue
		# Prefer units over buildings slightly.
		var dx: float = ex - n.x
		var dy: float = ey - n.y
		var d := dx * dx + dy * dy
		if nk == T.Kind.Building:
			d *= 1.8
		if d < bestD:
			bestD = d
			best = n
	return best


func nearestAnyResource(x: float, y: float, radius: float):
	var best = null
	var bd := radius * radius
	for t in ["tree", "gold_mine", "stone_mine"]:
		var r = findNearbyResource(x, y, t, radius)
		if r != null:
			var d := MathU.dist2(x, y, r.x, r.y)
			if d < bd:
				bd = d
				best = r
	return best


func findNearbyResource(x: float, y: float, type: String, radius: float):
	var best: Entity = null
	var bestD := INF
	var r2 := radius * radius
	for e: Entity in entities:
		if not e.alive:
			continue
		var isFarmFood := type == "farm" and e.type == "farm" and e.kind == T.Kind.Building
		if e.type != type and not isFarmFood:
			continue
		if e.kind == T.Kind.Resource and e.amount <= 0:
			continue
		var d := MathU.dist2(x, y, e.x, e.y)
		if d < r2 and d < bestD:
			bestD = d
			best = e
	return best


func findNearestDropoff(team: int, x: float, y: float, kind: String):
	var best: Entity = null
	var bestD := INF
	for e: Entity in entities:
		if not e.alive or e.team != team or e.kind != T.Kind.Building or e.buildState != T.BuildState.Done:
			continue
		var def: Dictionary = BUILDINGS[e.type]
		if not def.isDropoff or not (def.dropoffKinds as Array).has(kind):
			continue
		var d := MathU.dist2(x, y, e.x, e.y)
		if d < bestD:
			bestD = d
			best = e
	return best


# Buildings ----------------------------------------------------------------

func tickBuilding(e: Entity) -> void:
	if e.buildState != T.BuildState.Done:
		return
	var def: Dictionary = BUILDINGS[e.type]
	var owner := _p(e.team)

	# Oath of the Hearth: a building left alone mends itself.
	if owner != null and owner.oath.buildingRegen > 0 and e.hp < e.maxHp and time - e.lastDamageTime >= MEND_AFTER_SEC:
		e.hp = minf(e.maxHp, e.hp + owner.oath.buildingRegen * SIM_DT)

	# Gates: open for the owner when safe, bar shut when the enemy is near.
	if e.type == "gate" and tickCount % 5 == e.id % 5:
		updateGate(e)

	# Production queue.
	if e.productionQueue.size() > 0:
		var p := _p(e.team)
		var speed: float = p.econMult if p != null else 1.0
		e.productionTime -= SIM_DT * speed
		if e.productionTime <= 0:
			var item: String = e.productionQueue.pop_front()
			completeProduction(e, item)
			if e.productionQueue.size() > 0:
				var next: String = e.productionQueue[0]
				var nd = _udef(next.substr(2)) if next.begins_with("u:") else null
				var tm := trainMult(p, nd) if nd != null and p != null else 1.0 # Quartermaster, Oath of the Sword
				e.productionTime = itemTime(next, p.age if p != null else 0) * tm

	# Defensive fire (towers, TC, castle).
	if def.attack > 0 and e.attackCooldown <= 0:
		var enemy = findEnemyInRange(e, def.range)
		if enemy != null:
			e.attackCooldown = def.attackInterval
			var garrisonBonus := e.garrison.size() * 2
			fireProjectileRaw(e, enemy, def.attack + garrisonBonus, "tower")
			emit("bow", e.x, e.y, e.team)


func updateGate(e: Entity) -> void:
	var open: bool
	if e.gateForce == 1:
		open = true
	elif e.gateForce == 2:
		open = false
	else:
		var near := spatial.query(e.x, e.y, 96.0)
		var friendly := false
		var enemy := false
		for n: Entity in near:
			if not n.alive or n.kind != T.Kind.Unit:
				continue
			if n.team == e.team and MathU.dist(n.x, n.y, e.x, e.y) < 52:
				friendly = true
			elif areHostile(e.team, n.team):
				enemy = true
		open = friendly and not enemy
	if open != e.gateOpen:
		e.gateOpen = open
		# Open => clear the cell so units can pass; shut => block it again.
		grid.stampFootprint(e.x, e.y, 1, not open)


## Cycle a gate between forced-open and forced-shut (player override).
func toggleGate(id: int) -> void:
	var e = byId.get(id)
	if e == null or not e.alive or e.type != "gate":
		return
	e.gateForce = 2 if e.gateOpen else 1
	updateGate(e)


func itemTime(item: String, age := 0) -> float:
	if item.begins_with("u:"):
		var ud = _udef(item.substr(2))
		return ud.buildTime if ud != null else 10.0
	if item.begins_with("t:"):
		var up = UPGRADES.get(item.substr(2))
		return up.time if up != null else 20.0
	if item == "a:age":
		# Advancing to the next age — use that age's real advance time, not a
		# hardcoded value (which gave a free speed-up when queued behind another item).
		return AGES[age + 1].advanceTime if age + 1 < AGES.size() and age + 1 >= 0 else 30.0
	return 10.0


func completeProduction(b: Entity, item: String) -> void:
	var p := _p(b.team)
	if p == null:
		return
	if item.begins_with("u:"):
		var type := item.substr(2)
		var def: Dictionary = UNITS[type]
		if p.popUsed + def.pop > p.popCap:
			# No room: refund exactly what was charged (the boon-scaled cost, not the
			# full sticker price — otherwise cost-reduction boons mint resources).
			var paid := unitCost(p, def)
			p.resources.food += paid.food
			p.resources.wood += paid.wood
			p.resources.gold += paid.gold
			emit("popcap", b.x, b.y, b.team, type)
			return
		var s := grid.nearestOpenWorld(b.x, b.y + b.radius + 20.0)
		var u := spawnUnit(b.team, type, s[0], s[1])
		if def.get("hero", false):
			p.heroState = "alive"
			applyHeroLevel(u, p.heroLevel)
		emit("spawn", s[0], s[1], b.team, type)
		# A Trade Cart puts itself on the longest route it can reach. The pay is
		# the distance, so there is exactly one right answer and making the player
		# click it every time would be busywork, not a decision.
		if type == "trade_cart":
			assignTradeRoute([u.id])
		elif b.rallyX >= 0:
			applyRally(u, b, type)
	elif item.begins_with("t:"):
		var upId := item.substr(2)
		p.upgrades[upId] = true
		# HP upgrades are baked at spawn — refresh the standing army so it benefits.
		var up = UPGRADES.get(upId)
		if up != null and up.kind == "hp":
			refreshTeamUnits(b.team)
		emit("complete", b.x, b.y, b.team, item)
	elif item == "a:age":
		p.age = mini(MAX_AGE, p.age + 1)
		while p.ageTimes.size() <= p.age:
			p.ageTimes.append(null)
		p.ageTimes[p.age] = time
		var sworn = p.pendingOath
		p.pendingOath = null
		if sworn != null and str(sworn) != "" and OATHS.has(sworn) and int(OATHS[sworn].age) == p.age and not p.oaths.has(sworn):
			p.oaths.append(sworn)
		recomputeBoons(b.team) # unlock this age's boon and Oath, and buff the army
		emit("age", b.x, b.y, b.team, str(p.age))
		if sworn != null and str(sworn) != "":
			emit("oath", b.x, b.y, b.team, sworn)


# Projectiles --------------------------------------------------------------

func fireProjectile(from: Entity, target: Entity, def: Dictionary) -> void:
	var dmg := effectiveAttack(from, def, target)
	fireProjectileRaw(from, target, dmg, def.id, def.get("aoeRadius", 0.0))


func fireProjectileRaw(from: Entity, target: Entity, dmg: float, sourceType: String, aoe := 0.0) -> void:
	var e := newEntity()
	e.kind = T.Kind.Projectile
	e.team = from.team
	e.type = "rock" if sourceType == "catapult" else "arrow"
	e.x = from.x
	e.y = from.y
	e.prevX = e.x
	e.prevY = e.y
	e.projFromX = from.x
	e.projFromY = from.y
	e.projTargetId = target.id
	e.projDamage = dmg
	e.projSourceTeam = from.team
	e.projSourceId = from.id if from.kind == T.Kind.Unit else -1
	e.projArmorClassBonusFrom = sourceType
	e.radius = 5.0 if e.type == "rock" else 2.0
	var speed := PROJECTILE_SPEED * 0.55 if e.type == "rock" else PROJECTILE_SPEED
	e.projSpeed = speed
	e.projDuration = maxf(0.12, MathU.dist(from.x, from.y, target.x, target.y) / speed)
	e.projElapsed = 0.0
	e.amount = aoe # reuse: aoe radius
	entities.append(e)
	byId[e.id] = e


func tickProjectile(e: Entity) -> void:
	e.projElapsed += SIM_DT
	var target = byId.get(e.projTargetId)
	var t := minf(1.0, e.projElapsed / e.projDuration)
	# Track target's current position (slight homing keeps hits readable).
	var live: bool = target != null and target.alive
	var tx: float = target.x if live else (e.order.tx if e.order.tx != 0.0 else e.x)
	var ty: float = target.y if live else (e.order.ty if e.order.ty != 0.0 else e.y)
	e.order.tx = tx
	e.order.ty = ty
	e.x = e.projFromX + (tx - e.projFromX) * t
	e.y = e.projFromY + (ty - e.projFromY) * t
	e.facing = JSAtan.atan2(ty - e.projFromY, tx - e.projFromX)
	if t >= 1:
		e.alive = false
		var aoe := e.amount
		if aoe > 0:
			var near := spatial.query(e.x, e.y, aoe)
			for n: Entity in near:
				if not n.alive or not areHostile(e.projSourceTeam, n.team):
					continue
				if n.kind != T.Kind.Unit and n.kind != T.Kind.Building:
					continue
				dealDamage(e.projSourceTeam, n, e.projDamage, e.projArmorClassBonusFrom, e.projSourceId, true)
			emit("siege", e.x, e.y, e.projSourceTeam)
		elif target != null and target.alive:
			dealDamage(e.projSourceTeam, target, e.projDamage, e.projArmorClassBonusFrom, e.projSourceId, true)
			emit("arrowHit", e.x, e.y, e.projSourceTeam)


# Damage -------------------------------------------------------------------

## Total amount from researched upgrades of a team-wide kind (vision, build
## speed, trade rate) — these apply to the whole realm rather than to a unit
## class, so they skip the appliesTo matching the combat techs use.
func teamUpgradeSum(team: int, kind: String) -> float:
	var p := _p(team)
	if p == null:
		return 0.0
	var total := 0.0
	for upId in p.upgrades:
		var up = UPGRADES.get(upId)
		if up != null and up.kind == kind:
			total += up.amount
	return total


## The Terrain under a world point (Grass when off the map).
func terrainAt(wx: float, wy: float) -> int:
	var cx := int(wx / TILE)
	var cy := int(wy / TILE)
	if cx < 0 or cy < 0 or cx >= terrainCols or cy >= terrainRows:
		return 0
	return terrain[cy * terrainCols + cx]


## How far this unit sees and shoots from where it is standing. High ground is
## worth 20% more; a wood costs you a quarter of it. This is what makes a hill
## worth taking and a forest a bad place to be caught by archers.
func terrainSight(e: Entity) -> float:
	var t := terrainAt(e.x, e.y)
	return TERRAIN_SIGHT[t] if t < TERRAIN_SIGHT.size() else 1.0


## Movement speed including ability buffs (Charge / Brace) and Caltrops slow.
func effectiveSpeed(e: Entity) -> float:
	var s := e.speed
	var p := _p(e.team)
	if p != null:
		for upId in p.upgrades:
			var up = UPGRADES.get(upId)
			if up != null and up.kind == "speed" and ((up.appliesTo as Array).has(e.armorClass) or (up.get("appliesToUnits", []) as Array).has(e.type)):
				s += up.amount
	if e.abilityActive > 0:
		var ab = ABILITIES.get(e.type)
		if ab != null and ab.get("speedMult", 0.0) != 0.0:
			s *= ab.speedMult
	if e.slowTimer > 0:
		s *= SLOW_MULT
	# The ground underfoot. Siege engines are already slow and get stuck on
	# everything, so they take half the penalty rather than all of it.
	# Decking counts as ordinary ground: crossing a bridge is the whole point of
	# paying for one, and a bridge you still wade across is just an ornament.
	var ground := 1.0
	if not bridgedAt(e.x, e.y):
		var t := terrainAt(e.x, e.y)
		ground = TERRAIN_SPEED[t] if t < TERRAIN_SPEED.size() else 1.0
	if ground != 1:
		var soften := 0.5 if e.armorClass == T.AC_SIEGE else 1.0
		s *= 1.0 - (1.0 - ground) * soften
	return s


## Seconds between shots, shortened by Volley while active.
func effectiveInterval(e: Entity) -> float:
	var iv := e.attackInterval
	# The Shogunate's swordsmanship: melee soldiers strike faster.
	var def = _udef(e.type)
	if e.kind == T.Kind.Unit and def != null and not def.ranged and not def.get("canGather", false):
		var p := _p(e.team)
		iv *= p.oath.meleeRateMult if p != null else 1.0
	if e.abilityActive > 0:
		var ab = ABILITIES.get(e.type)
		if ab != null and ab.get("atkIntervalMult", 0.0) != 0.0:
			return iv * ab.atkIntervalMult
	return iv


## Attack value including tech/age bonuses and RPS bonus vs the target.
func effectiveAttack(e: Entity, def: Dictionary, target: Entity) -> float:
	var p := _p(e.team)
	var atk := e.attack
	var bonus: float = def.bonus.get(target.armorClass, 0.0)
	if p != null:
		atk += _num(AGE_ATTACK_BONUS, p.age, 0.0)
		for upId in p.upgrades:
			var up = UPGRADES.get(upId)
			if up == null:
				continue
			var hits: bool = (up.appliesTo as Array).has(def.armorClass) or (up.get("appliesToUnits", []) as Array).has(def.id)
			if not hits:
				continue
			if up.kind == "attack":
				atk += up.amount
			elif up.kind == "bonus" and up.get("bonusVs") == target.armorClass:
				bonus += up.amount
	# Active ability buffs (Charge damage, Brace's anti-cavalry bite, …).
	var ab = ABILITIES.get(e.type) if e.abilityActive > 0 else null
	if ab != null:
		if ab.get("attackMult", 0.0) != 0.0:
			atk *= ab.attackMult
		if ab.get("bonusVs") != null:
			bonus += float(ab.bonusVs.get(target.armorClass, 0.0))
	# War Cry rally from a nearby ally.
	if e.rallyTimer > 0:
		atk *= RALLY_ATK_MULT
	return atk + bonus


func effectiveArmor(target: Entity, ranged := false) -> float:
	var p := _p(target.team)
	# Ranged hits bite pierce armor; melee bites melee armor (same for most units).
	var armor := target.pierceArmor if ranged else target.armor
	if p != null and target.kind == T.Kind.Unit:
		armor += _num(AGE_ARMOR_BONUS, p.age, 0.0)
		var def = _udef(target.type)
		for upId in p.upgrades:
			var up = UPGRADES.get(upId)
			if up != null and up.kind == "armor" and def != null and ((up.appliesTo as Array).has(def.armorClass) or (up.get("appliesToUnits", []) as Array).has(target.type)):
				armor += up.amount
	# A guard aura from an ally's ability (Shield Wall).
	if target.guardTimer > 0:
		armor += GUARD_ARMOR
	# Shield Wall / Brace add armour while active.
	if target.abilityActive > 0:
		var ab = ABILITIES.get(target.type)
		if ab != null and ab.get("armorBonus", 0.0) != 0.0:
			armor += ab.armorBonus
	# Rally banner toughens soldiers standing under it.
	if target.kind == T.Kind.Unit:
		var td = _udef(target.type)
		if not (td != null and td.get("canGather", false)):
			var banner = bannerAt(target.team, target.x, target.y)
			if banner != null:
				armor += banner.power.militaryArmor
	return armor


func dealDamage(fromTeam: int, target: Entity, rawDamage: float, sourceType: String, fromId := -1, ranged := false) -> void:
	if not target.alive:
		return
	# Reaver boon: extra damage to villagers and buildings (eco denial / sieging).
	var attacker := _p(fromTeam)
	var raw := rawDamage
	if attacker != null and attacker.boon.raiderMult > 1 and (target.kind == T.Kind.Building or target.type == "villager"):
		raw *= attacker.boon.raiderMult
	# The Jarls' axes: infantry that burn what they reach.
	if attacker != null and target.kind == T.Kind.Building and attacker.oath.infantryBuildingDmgMult != 1:
		var sd = _udef(sourceType)
		if sd != null and sd.armorClass == T.AC_INFANTRY:
			raw *= attacker.oath.infantryBuildingDmgMult
	# Armour subtracts flat, which is fine at the handful of points a unit
	# carries in a normal match — but stacked armour with only a 1-damage floor
	# turns into effective immunity. A blow always lands for at least a fifth of
	# its weight, so armour caps out at heavy mitigation instead of walling.
	var dmg := maxf(maxf(1.0, raw * ARMOR_MIN_FRACTION), raw - effectiveArmor(target, ranged))
	target.hp -= dmg
	# Damage dealt and taken, so a match where nobody quite died still shows
	# who was winning the fights.
	if true:
		var dealt = attacker.stats if attacker != null else null
		if dealt != null and fromTeam != target.team:
			dealt.damageDealt += dmg
			if target.team < numTeams and not areAllied(fromTeam, target.team):
				if dealt.firstHitAt < 0:
					dealt.firstHitAt = time
				var zone := zoneOf(fromTeam, target.x, target.y)
				if zone == "attack":
					dealt.damageAttacking += dmg
					if dealt.firstAttackAt < 0:
						dealt.firstAttackAt = time
				elif zone == "defend":
					dealt.damageDefending += dmg
					if dealt.firstDefendAt < 0:
						dealt.firstDefendAt = time
				else:
					dealt.damageField += dmg
					if dealt.firstFieldAt < 0:
						dealt.firstFieldAt = time
				if zone != "defend" and time <= 600 and sourceType != "":
					dealt.openerByUnit[sourceType] = float(dealt.openerByUnit.get(sourceType, 0.0)) + dmg
			if sourceType != "":
				dealt.damageByUnit[sourceType] = float(dealt.damageByUnit.get(sourceType, 0.0)) + dmg
		var tp := _p(target.team)
		if tp != null:
			tp.stats.damageTaken += dmg
	target.hitFlash = 1.0
	target.lastDamageTime = time
	if fromId >= 0:
		target.lastAttackerId = fromId

	# Floating damage numbers only for fights the human player is part of —
	# keeps the event stream (and the screen) from drowning in big melees.
	if fromTeam == T.Team.Player or target.team == T.Team.Player:
		emit("hit", target.x, target.y - target.radius, fromTeam, JS.num(JS.round(dmg)))

	# Under-attack alert (rate limited).
	var p := _p(target.team)
	if p != null and time - lastAlertTime[target.team] > 12:
		lastAlertTime[target.team] = time
		emit("underattack", target.x, target.y, target.team)

	# Villagers fight back if idle and attacked in melee… keep simple: no.

	if target.hp <= 0:
		creditVeterancy(fromId)
		kill(target, fromTeam, sourceType)


func kill(e: Entity, byTeam: int, byType := "") -> void:
	e.alive = false
	e.hp = 0.0
	var killer := _p(byTeam)
	var owner := _p(e.team)
	if e.kind == T.Kind.Unit:
		var def = _udef(e.type)
		if owner != null:
			owner.popUsed = maxf(0.0, owner.popUsed - (def.pop if def != null else 1.0))
			owner.stats.unitsLost += 1
			owner.stats.lostByType[e.type] = float(owner.stats.lostByType.get(e.type, 0.0)) + 1.0
		if killer != null:
			killer.stats.unitsKilled += 1
			killer.stats.killedByType[e.type] = float(killer.stats.killedByType.get(e.type, 0.0)) + 1.0
			if byType != "" and e.team != byTeam:
				killer.stats.killsByUnit[byType] = float(killer.stats.killsByUnit.get(byType, 0.0)) + 1.0
			if killer.stats.firstKillAt < 0 and e.team != byTeam and e.team < numTeams:
				killer.stats.firstKillAt = time
			if e.team != byTeam and e.team < numTeams:
				var zone := zoneOf(byTeam, e.x, e.y)
				if zone == "attack":
					killer.stats.killsAttacking += 1
				elif zone == "defend":
					killer.stats.killsDefending += 1
		creditHeroKill(byTeam, e.x, e.y)
		# The Champion falls — but rises again at the Town Center after a while.
		if def != null and def.get("hero", false) and owner != null:
			owner.heroState = "respawning"
			owner.heroRespawnTimer = HERO_RESPAWN_SEC * owner.oath.heroRespawnMult
			owner.heroLevel = e.heroLevel
		emit("death", e.x, e.y, e.team, e.type)
	elif e.kind == T.Kind.Building:
		var def = BUILDINGS.get(e.type)
		# Walkable buildings never stamped the grid; don't clear cells they may
		# share with adjacent obstacles.
		if not BUILDING_WALKABLE.has(e.type):
			grid.stampFootprint(e.x, e.y, def.tiles, false)
		# A burned bridge is shallows again.
		if def != null and def.get("onlyOnShallows", false) and e.buildState == T.BuildState.Done:
			stampBridge(e, false)
		if owner != null:
			owner.popCap = maxf(0.0, owner.popCap - e.popProvided)
			owner.stats.buildingsLost += 1
		if killer != null:
			killer.stats.buildingsRazed += 1
			if killer.stats.firstRazeAt < 0 and e.team != byTeam:
				killer.stats.firstRazeAt = time
			if byType != "" and e.team != byTeam:
				killer.stats.razedByUnit[byType] = float(killer.stats.razedByUnit.get(byType, 0.0)) + 1.0
		# Garrisoned units die with the building.
		for id in e.garrison:
			var g = byId.get(id)
			if g != null:
				g.alive = false
				if owner != null:
					var gd = _udef(g.type)
					owner.popUsed = maxf(0.0, owner.popUsed - (gd.pop if gd != null else 1.0))
		emit("collapse", e.x, e.y, e.team, e.type)


# Vision / fog ---------------------------------------------------------------

## Once a second: high-water marks and idle-worker time. Peaks say something
## an average cannot, and idle villager time is the single most honest measure
## of economic sloppiness.
func samplePlayerPeaks() -> void:
	var army: Array = []
	var vills: Array = []
	var idle: Array = []
	var tcs: Array = []
	var tcIdle: Array = []
	var prod: Array = []
	var prodIdle: Array = []
	for a in [army, vills, idle, tcs, tcIdle, prod, prodIdle]:
		a.resize(numTeams)
		a.fill(0)
	for e: Entity in entities:
		if not e.alive or e.team >= numTeams:
			continue
		if e.kind == T.Kind.Unit:
			if e.type == "villager":
				vills[e.team] += 1
				if e.order.kind == T.OrderKind.Idle:
					idle[e.team] += 1
			else:
				army[e.team] += 1
			continue
		if e.kind != T.Kind.Building:
			continue
		# Only a *finished* building can be idle; one under construction is not
		# failing to produce, it does not exist yet.
		if e.buildState != T.BuildState.Done:
			continue
		var def = BUILDINGS.get(e.type)
		if def == null or (def.trains as Array).size() == 0:
			continue
		var empty := 1 if e.productionQueue.size() == 0 else 0
		if e.type == "town_center":
			tcs[e.team] += 1
			tcIdle[e.team] += empty
		else:
			prod[e.team] += 1
			prodIdle[e.team] += empty
	for t in numTeams:
		var p := _p(t)
		if p == null:
			continue
		var st := p.stats
		if army[t] > st.peakArmy:
			st.peakArmy = float(army[t])
		if vills[t] > st.peakVillagers:
			st.peakVillagers = float(vills[t])
		st.idleVillagerTime += idle[t] # villager-seconds of doing nothing
		st.idleTcTime += tcIdle[t]
		st.idleProductionTime += prodIdle[t]
		st.tcSeconds += tcs[t]
		st.productionSeconds += prod[t]


func recomputeVision() -> void:
	for t in numTeams:
		var f: PackedByteArray = fog[t]
		# visible -> explored
		for i in f.size():
			if f[i] == FOG_VISIBLE:
				f[i] = FOG_EXPLORED
	# Night shrinks sight by up to 40%. Buildings (lit by braziers and watch-
	# fires) keep most of theirs, so your base never goes fully blind.
	var phase := DayNight.dayPhase(time)
	var nightMult := DayNight.visionMult(phase)
	var buildingMult := maxf(0.82, nightMult)
	for e: Entity in entities:
		if not e.alive or e.team == T.Team.Neutral:
			continue
		if e.kind != T.Kind.Unit and e.kind != T.Kind.Building:
			continue
		var f: PackedByteArray = fog[e.team]
		var cx := grid.worldToCellX(e.x)
		var cy := grid.worldToCellY(e.y)
		# Watchfires burn through the dark — full sight regardless of the hour.
		var mult := 1.0 if e.type == "watchfire" else (buildingMult if e.kind == T.Kind.Building else nightMult)
		# The ground you stand on decides how far you can see from it.
		var gt := terrainAt(e.x, e.y)
		var ground: float = TERRAIN_SIGHT[gt] if gt < TERRAIN_SIGHT.size() else 1.0
		var r := maxi(2, int(ceil((e.visionRange * mult * ground) / TILE)))
		var r2 := r * r
		for dy in range(-r, r + 1):
			var ny := cy + dy
			if ny < 0 or ny >= fogRows:
				continue
			for dx in range(-r, r + 1):
				if dx * dx + dy * dy > r2:
					continue
				var nx := cx + dx
				if nx < 0 or nx >= fogCols:
					continue
				f[ny * fogCols + nx] = FOG_VISIBLE

	# Shared sight: allies see what each other sees (take the brighter state).
	for a in numTeams:
		for b in range(a + 1, numTeams):
			if alliances[a] != alliances[b]:
				continue
			var fa: PackedByteArray = fog[a]
			var fb: PackedByteArray = fog[b]
			for i in fa.size():
				var m := fa[i] if fa[i] > fb[i] else fb[i]
				fa[i] = m
				fb[i] = m


func fogAt(team: int, wx: float, wy: float) -> int:
	if revealAll:
		return FOG_VISIBLE # spectator sees the whole field
	var cx := grid.worldToCellX(wx)
	var cy := grid.worldToCellY(wy)
	if cx < 0 or cy < 0 or cx >= fogCols or cy >= fogRows:
		return FOG_UNSEEN
	return fog[team][cy * fogCols + cx]


## Who can pick each unit out of the woods.
##
## A unit standing in woodland is invisible to a team with nothing close by,
## which makes a treeline somewhere to wait rather than somewhere to avoid, and
## gives scouting a job beyond lifting fog. Only units, and only those actually
## in forest. Attacking gives you away, because an ambush that stays invisible
## while it kills you is not an ambush, it is a bug.
func recomputeConcealment() -> void:
	var ALL := -1
	var cr2 := CONCEAL_RANGE * CONCEAL_RANGE
	for e: Entity in entities:
		if not e.alive or e.kind != T.Kind.Unit:
			continue
		# attackCooldown is non-zero exactly while a unit is recovering from a
		# swing or a shot, so it is already the "has just attacked" signal.
		# A Ranger keeps its cover while it shoots — that is the Oath of the Bow.
		var shotGivesAway := false
		if e.attackCooldown > 0:
			var ud = _udef(e.type)
			shotGivesAway = not (ud != null and ud.get("stalker", false))
		if terrainAt(e.x, e.y) != TERRAIN_FOREST or shotGivesAway:
			e.spottedBy = ALL
			continue
		# Hidden from everyone except its own side, its allies, and any hostile
		# team with eyes close enough to see through the trees.
		var mask := 0
		for t in numTeams:
			if not areHostile(e.team, t):
				mask |= 1 << t
				continue
		for n: Entity in spatial.query(e.x, e.y, CONCEAL_RANGE):
			if not n.alive or n.team == e.team:
				continue
			if n.kind != T.Kind.Unit and n.kind != T.Kind.Building:
				continue
			if MathU.dist2(n.x, n.y, e.x, e.y) > cr2:
				continue
			mask |= 1 << n.team
		e.spottedBy = mask


## Is this entity visible to `team` right now?
func visibleTo(team: int, e: Entity) -> bool:
	if revealAll or e.team == team:
		return true
	if e.kind == T.Kind.Unit and (e.spottedBy & (1 << team)) == 0:
		return false
	var f := fogAt(team, e.x, e.y)
	if f == FOG_VISIBLE:
		return true
	# Explored buildings remain visible as "last known" — renderer handles ghosting.
	if e.kind == T.Kind.Building and f == FOG_EXPLORED:
		return true
	return false


# ----------------------------------------------------------- game modes --

func tickGameMode() -> void:
	if mode == "survival":
		tickSurvival()
	elif mode == "koth":
		tickKoth()


## Survival: spawn escalating waves from the map edges at the players.
func tickSurvival() -> void:
	if hordeTeam < 0 or survivalWon or winner != null:
		return
	survivalTimer -= SIM_DT
	if survivalTimer > 0:
		return
	if survivalWave >= survivalWavesTotal:
		survivalWon = true
		return
	survivalWave += 1
	survivalTimer = maxf(22.0, 42.0 - survivalWave) # waves quicken
	spawnWave(survivalWave)


func spawnWave(wave: int) -> void:
	var size := 4 + floori(wave * 1.8)
	# Composition escalates with the wave number.
	var pool: Array = ["militia", "spearman"]
	if wave >= 3:
		pool.append("archer")
	if wave >= 5:
		pool.append_array(["knight", "skirmisher"])
	if wave >= 8:
		pool.append_array(["knight", "ram"])
	if wave >= 10:
		pool.append_array(["knight", "catapult"])
	# Aim the wave at a surviving player base — or, if they've not built yet
	# (nomad start), at their villagers, so the horde always finds them.
	var ht := hordeTeam
	var nt := numTeams
	var targets: Array = entities.filter(func(e): return e.alive and e.kind == T.Kind.Building and e.team != ht and e.team < nt)
	if targets.size() == 0:
		targets = entities.filter(func(e): return e.alive and e.kind == T.Kind.Unit and e.team != ht and e.team < nt)
	var aimX := worldW / 2.0
	var aimY := worldH / 2.0
	if targets.size() > 0:
		var aim: Entity = targets[rng.int_(0, targets.size() - 1)]
		aimX = aim.x
		aimY = aim.y
	# Spawn along a random map edge.
	var edge := rng.int_(0, 3)
	var ids: Array = []
	for i in size:
		var x: float
		var y: float
		var j := rng.range(0.1, 0.9)
		if edge == 0:
			x = j * worldW
			y = 30.0
		elif edge == 1:
			x = j * worldW
			y = worldH - 30.0
		elif edge == 2:
			x = 30.0
			y = j * worldH
		else:
			x = worldW - 30.0
			y = j * worldH
		var type: String = pool[rng.int_(0, pool.size() - 1)]
		var u := spawnUnit(hordeTeam, type, x, y)
		# Wave units are a touch tougher each wave so late game stays threatening.
		var tough := 1.0 + wave * 0.04
		u.maxHp = JS.round(u.maxHp * tough)
		u.hp = u.maxHp
		ids.append(u.id)
	issueFormationMove(ids, aimX, aimY, false, true) # attack-move in
	emit("underattack", aimX, aimY, T.Team.Player)


## KotH: accrue uncontested hold time for whichever alliance holds the hill.
func tickKoth() -> void:
	var present := {}
	for e: Entity in entities:
		if not e.alive or e.kind != T.Kind.Unit or e.team >= numTeams:
			continue
		var ud = _udef(e.type)
		if ud != null and ud.get("canGather", false):
			continue # villagers don't contest
		var dx := e.x - hillX
		var dy := e.y - hillY
		if dx * dx + dy * dy <= hillR * hillR:
			present[alliances[e.team]] = true
	# Only an uncontested holder accrues; contested or empty pauses (no reset).
	if present.size() != 1:
		return
	var holder = present.keys()[0]
	for t in numTeams:
		if alliances[t] != holder:
			continue
		kothHold[t] += SIM_DT
		if kothHold[t] >= kothGoal and winner == null:
			winner = t


## Best (max) hold time achieved by each alliance — for the HUD: [{team, hold}].
func kothProgress() -> Array:
	var out: Array = []
	for t in numTeams:
		out.append({"team": t, "hold": kothHold[t]})
	return out


## Knock a realm out, remembering when — the match report says who fell first.
func markDefeated(t: int) -> void:
	var p := _p(t)
	if p.defeated:
		return
	p.defeated = true
	p.defeatedAt = time


func checkVictory() -> void:
	if winner != null:
		return
	if mode == "survival":
		checkVictorySurvival()
		return
	if mode == "regicide":
		checkVictoryRegicide()
		return
	# koth is decided by hold time (in tick); it still uses elimination below too.

	# A team is only out when it has NO buildings AND no villager to rebuild
	# one — so a nomad start (villagers, no Town Center) isn't an instant loss,
	# and a razed base with surviving villagers can still make a comeback.
	var buildings: Array = []
	buildings.resize(numTeams)
	buildings.fill(0)
	var villagers: Array = []
	villagers.resize(numTeams)
	villagers.fill(0)
	for e: Entity in entities:
		if not e.alive or e.team >= numTeams:
			continue
		if e.kind == T.Kind.Building:
			buildings[e.team] += 1
		elif e.kind == T.Kind.Unit:
			var ud = _udef(e.type)
			if ud != null and ud.get("canBuild", false):
				villagers[e.team] += 1
	var alive: Array = []
	var aliveAlliances := {}
	for t in numTeams:
		if buildings[t] == 0 and villagers[t] == 0:
			markDefeated(t)
		elif not _p(t).defeated:
			alive.append(t)
			aliveAlliances[alliances[t]] = true
	# Last alliance standing wins; winner is a surviving member of it.
	if aliveAlliances.size() <= 1:
		winner = alive[0] if alive.size() > 0 else T.Team.Neutral


## Survival: the player alliance wins by clearing every wave; loses if wiped.
func checkVictorySurvival() -> void:
	var playersStanding := false
	for t in numTeams:
		if t == hordeTeam:
			continue
		var hasBase := false
		for e: Entity in entities:
			if e.alive and e.team == t:
				if e.kind == T.Kind.Building:
					hasBase = true
					break
				if e.kind == T.Kind.Unit:
					var ud = _udef(e.type)
					if ud != null and ud.get("canBuild", false):
						hasBase = true
						break
		if hasBase:
			playersStanding = true
		else:
			markDefeated(t)
	if not playersStanding:
		winner = hordeTeam # players wiped out
		return
	var hordeLeft := false
	for e: Entity in entities:
		if e.alive and e.team == hordeTeam and e.kind == T.Kind.Unit:
			hordeLeft = true
			break
	if survivalWon and not hordeLeft:
		# All waves cleared — credit a surviving defender, not always team 0 (which
		# may have been wiped while an ally held on in a co-op survival match).
		var w := T.Team.Player as int
		for t in numTeams:
			if t != hordeTeam and not _p(t).defeated:
				w = t
				break
		winner = w


## Regicide: an alliance is out when it has no living King.
func checkVictoryRegicide() -> void:
	var allianceKings := {}
	for t in numTeams:
		allianceKings[alliances[t]] = 0
	for e: Entity in entities:
		if e.alive and e.type == "king" and e.team < numTeams:
			allianceKings[alliances[e.team]] = int(allianceKings.get(alliances[e.team], 0)) + 1
	var aliveAlliances: Array = []
	for t in numTeams:
		var kings: int = allianceKings.get(alliances[t], 0)
		if kings == 0:
			markDefeated(t)
		elif not aliveAlliances.has(alliances[t]):
			aliveAlliances.append(alliances[t])
	if aliveAlliances.size() <= 1:
		for t in numTeams:
			if not _p(t).defeated:
				winner = t
				break


# Queries for UI/AI -------------------------------------------------------------

## Is someone actually working this farm (walking to it, farming it, or carrying its food)?
func farmWorked(farm: Entity) -> bool:
	var owner = byId.get(farm.farmWorker)
	return owner != null and owner.alive and worksFarm(owner, farm)


## Nearest gatherable node at a point — a resource, or one of `team`'s farms.
## Uses a slightly generous pick radius so a berry/tree tucked against a
## drop-off building is still selectable for gathering instead of the building.
func resourceAt(wx: float, wy: float, team = null):
	var near := spatial.query(wx, wy, 72.0)
	var best: Entity = null
	var bestD := INF
	for e: Entity in near:
		if not e.alive:
			continue
		var isFarm: bool = e.kind == T.Kind.Building and e.type == "farm" and \
			e.buildState == T.BuildState.Done and (team == null or e.team == team)
		if e.kind == T.Kind.Resource:
			if e.amount <= 0:
				continue
		elif not isFarm:
			continue
		var d := MathU.dist2(wx, wy, e.x, e.y)
		# A farm is a square field: anywhere on it counts, corners included.
		var hit: bool
		if isFarm:
			hit = absf(wx - e.x) <= e.radius + 4 and absf(wy - e.y) <= e.radius + 4
		else:
			var rr := maxf(e.radius + 10.0, 16.0)
			hit = d < rr * rr # `rr ** 2`: an exact square either way
		if hit and d < bestD:
			bestD = d
			best = e
	return best


func entityAt(wx: float, wy: float, team = null):
	var near := spatial.query(wx, wy, 48.0)
	var best: Entity = null
	var bestD := INF
	for e: Entity in near:
		if not e.alive:
			continue
		if team != null and e.team != team:
			continue
		var r := e.radius if e.kind == T.Kind.Building else maxf(e.radius + 6.0, 12.0)
		var d := MathU.dist2(wx, wy, e.x, e.y)
		if d < r * r and d < bestD:
			bestD = d
			best = e
	return best


func entitiesOf(team: int, kind = null) -> Array:
	return entities.filter(func(e): return e.alive and e.team == team and (kind == null or e.kind == kind))


func countOf(team: int, type: String) -> int:
	var n := 0
	for e: Entity in entities:
		if e.alive and e.team == team and e.type == type:
			n += 1
	return n


## Periodically drop long-dead entities to keep iteration fast.
func compact() -> void:
	# Who is sitting in a living building's garrison — collected once, rather
	# than scanning every entity for every dead unit as the TS does; the same
	# set either way.
	var inGarrison := {}
	for b: Entity in entities:
		if b.alive:
			for id in b.garrison:
				inGarrison[id] = true
	var keep: Array = []
	for e: Entity in entities:
		# Garrisoned units are alive=false but must be kept.
		var garrisoned := not e.alive and e.kind == T.Kind.Unit and inGarrison.has(e.id)
		if e.alive or garrisoned:
			keep.append(e)
		else:
			byId.erase(e.id)
	entities = keep
