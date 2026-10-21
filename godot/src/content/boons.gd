## content/boons.ts — the functions half of the boons module. The data (BOONS,
## BOONS_BY_ID, BOON_RARITY_SCALE, …) lives in `C`; each boon's `apply` and
## `detail` are ported here by id (`Boons.applyBoon(id, …)`, `Boons.detail(id, …)`).
##
## Each boon comes in rarity tiers (Common → One-of-a-Kind); a higher tier is a
## STRONGER version of the same effect. Effects are folded into per-team bonus
## fields at match start.
class_name Boons


## All per-team modifiers a boon loadout can contribute (neutral defaults).
class BoonEffect extends RefCounted:
	var atkMultInfantry := 1.0
	var atkMultArcher := 1.0
	var atkMultCavalry := 1.0
	var archerRangeBonus := 0.0 ## px
	var armySpeedMult := 1.0 ## all units
	var cavSpeedMult := 1.0 ## cavalry only (charge feel)
	var raiderMult := 1.0 ## bonus damage vs villagers & buildings
	var hpMult := 1.0 ## all units
	var armorBonus := 0.0 ## flat, all units
	var buildingHpMult := 1.0
	var buildCostMult := 1.0 ## <1 cheaper (non-wall buildings)
	var towerCdMult := 1.0 ## <1 faster defensive fire
	var towerRangeBonus := 0.0 ## px
	var wallHpMult := 1.0
	var wallCostMult := 1.0 ## <1 cheaper walls/gates
	var foodGatherMult := 1.0
	var regenRate := 0.0 ## hp/sec out of combat
	var unitCostMult := 1.0 ## <1 cheaper units
	var trainSpeedMult := 1.0 ## <1 faster training
	var villCarryMult := 1.0
	var villSpeedMult := 1.0
	var visionMult := 1.0

	## The fields as a Dictionary, in the TS declaration order (for saving/inspection).
	func to_dict() -> Dictionary:
		var out := {}
		for k in FIELDS:
			out[k] = get(k)
		return out

	const FIELDS := [
		"atkMultInfantry", "atkMultArcher", "atkMultCavalry", "archerRangeBonus",
		"armySpeedMult", "cavSpeedMult", "raiderMult", "hpMult", "armorBonus",
		"buildingHpMult", "buildCostMult", "towerCdMult", "towerRangeBonus",
		"wallHpMult", "wallCostMult", "foodGatherMult", "regenRate",
		"unitCostMult", "trainSpeedMult", "villCarryMult", "villSpeedMult", "visionMult",
	]


static func emptyBoonEffect() -> BoonEffect:
	return BoonEffect.new()


## For tooltips.
static func pct(base: float, scale: float) -> float:
	return JS.round(base * scale * 100.0)


static func S(rarity: int) -> float:
	var sc: Array = C.BOON_RARITY_SCALE
	return sc[rarity] if rarity >= 0 and rarity < sc.size() else 1.0


# Flat per-rarity curves for non-percentage effects.
const ARMOR_STEPS := [1, 1, 2, 2, 3, 3]


static func _armorStep(r: int) -> float:
	return float(ARMOR_STEPS[r]) if r >= 0 and r < ARMOR_STEPS.size() else 1.0


## Fold boon `id`'s effect (at the given rarity) into the aggregate — each
## BoonDef's `apply` in the TS.
static func applyBoon(id: String, e: BoonEffect, r: int) -> void:
	match id:
		"whetstones":
			e.atkMultInfantry *= 1.0 + 0.08 * S(r)
		"bodkin_points":
			e.atkMultArcher *= 1.0 + 0.08 * S(r)
			e.archerRangeBonus += 6.0 * S(r)
		"destrier":
			e.atkMultCavalry *= 1.0 + 0.08 * S(r)
			e.cavSpeedMult *= 1.0 + 0.04 * S(r)
		"warpath":
			e.armySpeedMult *= 1.0 + 0.05 * S(r)
		"reaver":
			e.raiderMult *= 1.0 + 0.1 * S(r)
		"iron_discipline":
			e.hpMult *= 1.0 + 0.08 * S(r)
		"pavise":
			e.armorBonus += _armorStep(r)
		"master_masons":
			e.buildingHpMult *= 1.0 + 0.1 * S(r)
			e.buildCostMult *= 1.0 - 0.05 * S(r)
		"vigilant_watch":
			e.towerCdMult *= 1.0 - 0.06 * S(r)
			e.towerRangeBonus += 8.0 * S(r)
		"bulwark":
			e.wallHpMult *= 1.0 + 0.15 * S(r)
			e.wallCostMult *= 1.0 - 0.08 * S(r)
		"bountiful_fields":
			e.foodGatherMult *= 1.0 + 0.08 * S(r)
		"field_medic":
			e.regenRate += 0.6 * S(r)
		"quartermaster":
			e.unitCostMult *= 1.0 - 0.05 * S(r)
			e.trainSpeedMult *= 1.0 - 0.05 * S(r)
		"wayfarers":
			e.villCarryMult *= 1.0 + 0.1 * S(r)
			e.villSpeedMult *= 1.0 + 0.04 * S(r)
		"far_sight":
			e.visionMult *= 1.0 + 0.1 * S(r)


## Human-readable magnitude at a given rarity (for tooltips) — each BoonDef's `detail`.
static func detail(id: String, r: int) -> String:
	match id:
		"whetstones":
			return "+%s%% infantry attack" % JS.num(pct(0.08, S(r)))
		"bodkin_points":
			return "+%s%% archer attack, +%s range" % [JS.num(pct(0.08, S(r))), JS.num(JS.round(6.0 * S(r)))]
		"destrier":
			return "+%s%% cavalry attack, +%s%% cavalry speed" % [JS.num(pct(0.08, S(r))), JS.num(pct(0.04, S(r)))]
		"warpath":
			return "+%s%% army move speed" % JS.num(pct(0.05, S(r)))
		"reaver":
			return "+%s%% damage to villagers & buildings" % JS.num(pct(0.1, S(r)))
		"iron_discipline":
			return "+%s%% unit HP" % JS.num(pct(0.08, S(r)))
		"pavise":
			return "+%s armor to all units" % JS.num(_armorStep(r))
		"master_masons":
			return "+%s%% building HP, -%s%% build cost" % [JS.num(pct(0.1, S(r))), JS.num(pct(0.05, S(r)))]
		"vigilant_watch":
			return "+%s%% defensive fire rate, +%s range" % [JS.num(pct(0.06, S(r))), JS.num(JS.round(8.0 * S(r)))]
		"bulwark":
			return "+%s%% wall/gate HP, -%s%% cost" % [JS.num(pct(0.15, S(r))), JS.num(pct(0.08, S(r)))]
		"bountiful_fields":
			return "+%s%% food gathering" % JS.num(pct(0.08, S(r)))
		"field_medic":
			return "%s HP/sec out of combat" % JS.to_fixed(0.6 * S(r), 1)
		"quartermaster":
			return "-%s%% unit cost & train time" % JS.num(pct(0.05, S(r)))
		"wayfarers":
			return "+%s%% villager carry, +%s%% villager speed" % [JS.num(pct(0.1, S(r))), JS.num(pct(0.04, S(r)))]
		"far_sight":
			return "+%s%% vision range" % JS.num(pct(0.1, S(r)))
	return ""


## Aggregate a set of equipped boons ({"id", "rarity"} Dictionaries) into one effect.
static func aggregateBoons(equipped: Array) -> BoonEffect:
	var eff := emptyBoonEffect()
	for bn in equipped:
		var id: String = bn.get("id", "")
		if C.BOONS_BY_ID.has(id):
			applyBoon(id, eff, int(bn.get("rarity", 0)))
	return eff
