## The collectible catalog: every trainable unit has a variant per rarity tier.
## A variant is identified by `${unitId}:${rarity}`. Common variants are owned by
## default; higher rarities are unlocked from chests and grant stat multipliers.
##
## VariantDef: { key (`${unitId}:${rarity}`), unitId, unitName, rarity,
## name (flavorful display name) }.
##
## The catalog is built in TS at module load from UNITS, the rarity tiers and
## the flavour tables (PREFIX, MYTHIC_TITLE); the result is plain data, so it
## travels through the exporter as C.CATALOG / C.VARIANT_BY_KEY rather than
## being rebuilt here (rebuilding could only drift from it).
class_name Catalog


static var CATALOG: Array:
	get:
		return C.CATALOG

static var VARIANT_BY_KEY: Dictionary:
	get:
		return C.VARIANT_BY_KEY

## All unit ids that can appear in the collection / loadout.
static var COLLECTIBLE_UNIT_IDS: Array:
	get:
		return C.COLLECTIBLE_UNIT_IDS


static func variantKey(unitId: String, rarity: float) -> String:
	return "%s:%s" % [unitId, JS.num(rarity)]
