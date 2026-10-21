## sim/types.ts — entity kinds, orders, teams and the other core enums.
## (The Entity record itself is entity.gd; an order is order.gd.)
##
## TypeScript string enums (ArmorClass, ResourceKind) are plain strings here,
## exactly the values the TS enum holds ("infantry", "food", ...).
class_name T

enum Team {
	Player = 0, Enemy = 1, Team3 = 2, Team4 = 3, Team5 = 4, Team6 = 5, Team7 = 6, Team8 = 7,
	Team9 = 8, Team10 = 9, Team11 = 10, Team12 = 11, Team13 = 12, Team14 = 13, Team15 = 14, Team16 = 15,
	## Neutral sits above any player slot so player teams can occupy 0..N-1.
	Neutral = 16,
}

## Highest number of player teams supported in one match (e.g. 8v8).
const MAX_TEAMS := 16

enum Kind { Unit = 0, Building = 1, Resource = 2, Projectile = 3 }

enum OrderKind {
	Idle = 0, Move = 1, AttackMove = 2, Attack = 3, Gather = 4, Return = 5, Build = 6, Repair = 7, Hold = 8,
	## A Trade Cart running a route between two Markets.
	Trade = 9,
}

## Combat posture — how a unit reacts to enemies on its own.
enum Stance { Aggressive = 0, Defensive = 1, StandGround = 2, Passive = 3, Skirmish = 4 }

enum BuildState { Done = 0, UnderConstruction = 1, Foundation = 2 }

# ArmorClass (string values, as in TS)
const AC_INFANTRY := "infantry"
const AC_ARCHER := "archer"
const AC_CAVALRY := "cavalry"
const AC_SIEGE := "siege"
const AC_BUILDING := "building"
const AC_VILLAGER := "villager"

# ResourceKind (string values, as in TS)
const RK_FOOD := "food"
const RK_WOOD := "wood"
const RK_GOLD := "gold"

## GameMode: "conquest" | "survival" | "koth" | "regicide" (plain strings).
