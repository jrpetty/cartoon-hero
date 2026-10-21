## sim/augments.ts — Warband Tactics augments, the run-defining choices, in
## the spirit of TFT's Hextech Augments. Three times a run you're offered three
## of these and keep one, and that pick bends the whole run: an economy engine,
## a stat spike across your whole warband, an extra board slot, or a banner
## that fakes two extra units of a synergy.
##
## Pure data + a picker. The run engine (sim/warband) reads the fields it cares
## about; the screen just draws them. The data (AUGMENTS, AUGMENT_ROUNDS,
## TIER_COLOR) is in `C`; an Augment is a Dictionary with the TS fields
## {id, name, desc, tier, gold?, interestCap?, rerollCost?, xp?, boardSlots?,
## buff?, traitBonus?: {trait, count}, relicEvery?, life?, bounty?, relics?}.
class_name Augments

const ROUND_TIER := ["silver", "gold", "prismatic"]


static var AUGMENTS: Array:
	get:
		return C.AUGMENTS
static var AUGMENT_ROUNDS: Array:
	get:
		return C.AUGMENT_ROUNDS
static var TIER_COLOR: Dictionary:
	get:
		return C.TIER_COLOR


static func augmentById(id: String):
	for a in C.AUGMENTS:
		if a.id == id:
			return a
	return null


## Which tier is offered on a given round (null if it isn't an augment round).
static func tierForRound(round: int):
	var i := -1
	for k in C.AUGMENT_ROUNDS.size():
		if int(C.AUGMENT_ROUNDS[k]) == round:
			i = k
			break
	return null if i < 0 else ROUND_TIER[i]


## Roll `count` distinct augment choices of a tier, skipping any already owned.
## Falls back to other tiers if a tier somehow runs dry, so the offer is never
## short.
static func offerAugments(rng: RNG, tier: String, owned: Array, count: int = 3) -> Array:
	var have := {}
	for id in owned:
		have[id] = true
	var pool: Array = C.AUGMENTS.filter(func(a): return a.tier == tier and not have.has(a.id))
	var spare: Array = C.AUGMENTS.filter(func(a): return a.tier != tier and not have.has(a.id))
	var out: Array = []
	for from in [pool, spare]:
		while out.size() < count and from.size() > 0:
			var i := rng.int_(0, from.size() - 1)
			out.append(from[i])
			from.remove_at(i)
	return out


## Merge every owned augment's stat buff into one buff for the battle (null
## when none carries a buff).
static func combinedBuff(owned: Array):
	var out := {}
	var any := false
	for a in owned:
		var b = a.get("buff")
		if b == null:
			continue
		any = true
		for k in Traits.BUFF_KEYS:
			if b.get(k):
				out[k] = float(out.get(k, 0.0)) + b[k]
	return out if any else null


## Merge every owned banner into a trait-id → bonus-count map (or null).
static func combinedTraitBonus(owned: Array):
	var out := {}
	var any := false
	for a in owned:
		var tb = a.get("traitBonus")
		if tb == null:
			continue
		any = true
		out[tb.trait] = float(out.get(tb.trait, 0.0)) + tb.count
	return out if any else null
