## sim/warband_commanders.ts — Warband Tactics commanders: the run's opening
## decision, and the part of the mode that is most ours rather than borrowed.
## You lead a warband under one of the realm's commanders, the same people who
## lead your armies in skirmish, and their perk is *structural*: it changes a
## rule of the run rather than handing you a stat. Free rerolls, better shop
## odds, full-value sales, an extra board slot from round one.
##
## Identity (name, title, colour) is reused from content/commanders so the
## roster stays consistent across modes; only the perk is mode-specific. The
## data (`WARBAND_COMMANDERS`) is in `C`; a WarbandCommander is a Dictionary
## {id, perk, gold?, startGold?, interestCap?, freeRerolls?, boardSlots?,
## shopLevelBonus?, fullRefund?, topTraitBonus?, lossShield?, buff?}.
class_name WarbandCommanders


static var WARBAND_COMMANDERS: Array:
	get:
		return C.WARBAND_COMMANDERS


## The commander with this id, or null.
static func warbandCommander(id: String):
	for c in C.WARBAND_COMMANDERS:
		if c.id == id:
			return c
	return null


## The shared roster entry (name, title, colour) behind a warband commander.
static func commanderIdentity(c: Dictionary):
	return C.COMMANDERS.get(c.id)


## Roll a set of distinct commanders to choose between at the start of a run.
static func offerCommanders(rng: RNG, count: int = 3) -> Array:
	var pool: Array = C.WARBAND_COMMANDERS.duplicate()
	var out: Array = []
	while out.size() < count and pool.size() > 0:
		var i := rng.int_(0, pool.size() - 1)
		out.append(pool[i])
		pool.remove_at(i)
	return out
