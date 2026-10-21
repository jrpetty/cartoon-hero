## sim/unit_tiers.ts — shop tiers for Warband Tactics, kept in their own module
## so both the run engine and the things it pulls in (the carousel, the
## screen) can read them without importing each other in a cycle.
##
## The data is in `C`: `C.UNIT_TIER` (type → shop tier 1..5; buying costs the
## tier in gold) and `C.TIER_UNITS` (the unit types at each tier, index 1..5,
## skipping anything undefined — computed by the TS at export time). These
## accessors only exist so the port reads like the TS imports.
class_name UnitTiers


## Shop tier (1 cheap/weak … 5 rare/strong); buying costs the tier in gold.
static var UNIT_TIER: Dictionary:
	get:
		return C.UNIT_TIER


## Unit types available at each tier (index 1..5).
static var TIER_UNITS: Array:
	get:
		return C.TIER_UNITS


## `UNIT_TIER[type] ?? fallback`, as an int.
static func tierOf(type: String, fallback: int = 1) -> int:
	var t = C.UNIT_TIER.get(type)
	return fallback if t == null else int(t)
