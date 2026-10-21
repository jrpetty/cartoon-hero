## War Chests: buy with Renown, open to roll a unit variant by weighted rarity.
## Duplicates convert to a small Renown refund so progress never fully stalls.
##
## ChestDef: { id, name, desc, cost (Renown), rarityBias (multiplies the base
## rarity weights to bias toward rarer drops; length === RARITIES.length) }.
## The chests are data (C.CHESTS): a "bigger" chest skews rarer.
##
## RollResult: { variant, rarity, duplicate, refund (Renown refunded if duplicate) }.
class_name Chests


static var CHESTS: Array:
	get:
		return C.CHESTS

static var CHEST_BY_ID: Dictionary:
	get:
		return C.CHEST_BY_ID

static var RARITIES: Array:
	get:
		return C.RARITIES


## Refund value of a duplicate, scaling with rarity.
static func dupRefund(rarity: int) -> float:
	var t := [30.0, 60.0, 120.0, 260.0, 600.0, 1500.0]
	return t[rarity] if rarity >= 0 and rarity < t.size() else 30.0


## Roll a chest. Picks a rarity by biased weights, then a random unit of that
## rarity. Pure given the RNG, so it is deterministic and testable.
## `owned` is a set (Dictionary of variant key -> true).
static func rollChest(chest: Dictionary, owned: Dictionary, rng: RNG) -> Dictionary:
	var weights := []
	var bias: Array = chest.rarityBias
	for i in C.RARITY_WEIGHTS.size():
		var b = bias[i] if i < bias.size() and bias[i] != null else 1.0
		weights.append(float(C.RARITY_WEIGHTS[i]) * float(b))
	var rarity := rng.weightedIndex(weights)
	var unitId: String = rng.pick(C.COLLECTIBLE_UNIT_IDS)
	var key := Catalog.variantKey(unitId, rarity)
	var variant = null
	for v in C.CATALOG:
		if v.key == key:
			variant = v
			break
	var duplicate := owned.has(key)
	return {
		"variant": variant,
		"rarity": rarity,
		"duplicate": duplicate,
		"refund": dupRefund(rarity) if duplicate else 0.0,
	}


static func rarityByIndex(i: float) -> Dictionary:
	return Rarity.rarityByIndex(i)
