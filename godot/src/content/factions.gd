## content/factions.ts — the functions half of the factions module. The data
## (FACTIONS, FACTION_IDS, DEFAULT_FACTION) lives in `C`; each faction's `apply`
## is ported here by id (`Factions.apply(id, boon, rules)`).
##
## Six peoples from different eras, each with a look of its own, a small set of
## passive bonuses with a price attached, and one or two units of its own.
class_name Factions


## Each FactionDef's `apply`: fold the faction's bonuses into a boon effect and
## an Oaths.OathRules block.
static func apply(id: String, b, r) -> void:
	match id:
		"kingdom":
			r.techCostMult *= 0.75
			r.unitCostMult["knight"] = float(r.unitCostMult.get("knight", 1.0)) * 0.9
			r.buildingCostMult["castle"] = float(r.buildingCostMult.get("castle", 1.0)) * 0.8
			r.buildingCostMult["watch_tower"] = float(r.buildingCostMult.get("watch_tower", 1.0)) * 0.8
		"legion":
			r.buildSpeedMult *= 1.1
			r.cavCostMult *= 1.15
		"norse":
			r.infantrySpeedMult *= 1.12
			r.infantryBuildingDmgMult *= 1.25
			r.woodGatherMult *= 1.12
			r.buildingCostMult["castle"] = float(r.buildingCostMult.get("castle", 1.0)) * 1.25
			r.techCostMult *= 1.1
		"shogunate":
			r.meleeRateMult *= 0.88
			r.villagerHpMult *= 1.25
			r.soldierHpMult *= 1.08
			r.soldierTrainMult *= 1.08
		"khanate":
			b.cavSpeedMult *= 1.12
			r.buildingCostMult["stable"] = float(r.buildingCostMult.get("stable", 1.0)) * 0.75
			r.buildingCostMult["archery_range"] = float(r.buildingCostMult.get("archery_range", 1.0)) * 0.75
			r.housePopBonus += 3
			b.buildingHpMult *= 0.8
			b.wallCostMult *= 1.25
		"ascendancy":
			b.villCarryMult *= 1.4
			r.buildingRegen += 2
			r.soldierCostMult *= 1.15


## A FACTIONS def by id, falling back to the default faction.
static func factionOf(id) -> Dictionary:
	if id != null and C.FACTIONS.has(id):
		return C.FACTIONS[id]
	return C.FACTIONS[C.DEFAULT_FACTION]


## Every unit that belongs to one faction, and which (a FACTIONS def or null).
static func factionForUnit(unitId: String):
	for fid in C.FACTIONS:
		var f: Dictionary = C.FACTIONS[fid]
		if (f.extra as Array).has(unitId) or (f.replaces as Dictionary).values().has(unitId):
			return f
	return null


## The roster a faction trains at a building: shared units, with its own swapped in.
static func rosterFor(faction, trains: Array) -> Array:
	var f := factionOf(faction)
	var out: Array = []
	for id in trains:
		var own = factionForUnit(id)
		if own != null and own.id != f.id:
			continue # another people's unit
		if str((f.replaces as Dictionary).get(id, "")) != "":
			continue # replaced by ours, which is on the roster itself
		out.append(id)
	return out
