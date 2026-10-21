## A single live thing in the world (sim/types.ts `Entity`). Not every field
## applies to every kind. Field names and defaults match `makeEntity()` in
## world.ts exactly.
class_name Entity
extends RefCounted

var id: int = 0
var kind: int = T.Kind.Unit
var team: int = T.Team.Neutral
var type: String = ""  ## def id, e.g. "villager", "knight", "town_center", "tree"

var alive: bool = true
var x: float = 0.0
var y: float = 0.0
## Position at the start of the current sim tick, for render interpolation.
var prevX: float = 0.0
var prevY: float = 0.0
var radius: float = 8.0
var facing: float = 0.0

var hp: float = 1.0
var maxHp: float = 1.0

# Movement
var vx: float = 0.0
var vy: float = 0.0
var speed: float = 0.0

# Combat
var attack: float = 0.0
var range: float = 0.0
var attackCooldown: float = 0.0
var attackInterval: float = 1.0
var armor: float = 0.0
var pierceArmor: float = 0.0
var armorClass: String = T.AC_INFANTRY
var visionRange: float = 100.0

# Orders
var order: Order = Order.idle()
var stance: int = T.Stance.Defensive
## Flattened [x0,y0,x1,y1,...] waypoints in world space, or null.
var path = null
var pathIndex: int = 0
var repathCooldown: float = 0.0

# Worker / gather
var carry: float = 0.0
var carryKind = null  ## String ("food"/"wood"/"gold") or null
var gatherCooldown: float = 0.0

# Resource node
var amount: float = 0.0

# Building
var buildState: int = T.BuildState.Done
var buildProgress: float = 1.0
var popProvided: float = 0.0
var rallyX: float = -1.0
var rallyY: float = -1.0
var productionQueue: Array = []  ## of String
var productionTime: float = 0.0
var garrison: Array = []  ## of int (entity ids)
var gateOpen: bool = false
var gateForce: int = 0
var farmWorker: int = -1

# Projectile
var projTargetId: int = -1
var projSourceId: int = -1
var projDamage: float = 0.0
var projSpeed: float = 0.0
var projSourceTeam: int = T.Team.Neutral
var projArmorClassBonusFrom: String = ""
var projElapsed: float = 0.0
var projDuration: float = 0.0
var projFromX: float = 0.0
var projFromY: float = 0.0

# Active ability
var abilityCooldown: float = 0.0
var abilityActive: float = 0.0
var slowTimer: float = 0.0
var rallyTimer: float = 0.0
var tradeHomeId: int = -1
## Bitmask of teams that can see this unit (all bits = in the open). TS: ~0.
var spottedBy: int = -1
var guardTimer: float = 0.0
var heroLevel: int = 0
var heroKills: int = 0
var veterancy: int = 0
var vetKills: int = 0

# Visual helpers (render only)
var animPhase: float = 0.0
var hitFlash: float = 0.0
var chargeRun: float = 0.0
var lastDamageTime: float = -999.0
var lastAttackerId: int = -1
var selected: bool = false

# Variant/meta
var variantRarity: int = 0
var tier: int = 0
