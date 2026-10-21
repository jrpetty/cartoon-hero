## Rarity tiers for the CS-style unit collection. Higher tiers roll less often
## and grant better stat multipliers (see balance.ts RARITY_* arrays) plus a
## more ornate procedural look (trim color + glow used by the renderer).
##
## RarityTier: { index, id, name, color (primary trim/glow), glow (softer glow),
## weight (relative drop weight) } — the table itself is data, in C.RARITIES.
class_name Rarity


static var RARITIES: Array:
	get:
		return C.RARITIES

static var RARITY_WEIGHTS: Array:
	get:
		return C.RARITY_WEIGHTS


static func rarityByIndex(i: float) -> Dictionary:
	return C.RARITIES[int(maxf(0.0, minf(C.RARITIES.size() - 1, i)))]
