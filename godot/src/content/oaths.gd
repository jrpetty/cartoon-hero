## content/oaths.ts — the functions half of the Oaths module. The data (OATHS,
## AFFINITY, TITHE_MAX_MARKETS, …) lives in `C`; each Oath's `apply` and `lines`
## are ported here by id (`Oaths.apply(id, …)`, `Oaths.lines(id, s)`).
##
## Every time a realm advances an age it swears one Oath of three, and what it
## swore shapes it from then on:
##
##   Banner Age — how will the realm grow?        Plough · Sword · Hearth
##   Crown Age  — what will its army be?          Lance · Bow · Shield
##   Empire Age — what will it be remembered for? Coin · Engine · Crown
class_name Oaths


## Rules a realm's faction and Oaths can change that no boon touches. Neutral
## by default. The faction's are folded in first, then each Oath's, into the
## one block the simulation reads.
class OathRules extends RefCounted:
	## Faction: Blacksmith research cost (×).
	var techCostMult := 1.0
	## Faction: cost (×) by building type.
	var buildingCostMult: Dictionary = {}
	## Faction: construction speed (×).
	var buildSpeedMult := 1.0
	## Faction: soldier and cavalry cost (×), and cost (×) by unit type.
	var soldierCostMult := 1.0
	var unitCostMult: Dictionary = {}
	var cavCostMult := 1.0
	## Faction: infantry speed (×) and damage to buildings (×).
	var infantrySpeedMult := 1.0
	var infantryBuildingDmgMult := 1.0
	## Faction: wood gathering (×).
	var woodGatherMult := 1.0
	## Faction: melee attack interval (×, <1 faster).
	var meleeRateMult := 1.0
	## Faction: villager max HP (×), and every soldier's (×).
	var villagerHpMult := 1.0
	var soldierHpMult := 1.0
	## Faction: soldier training time (×).
	var soldierTrainMult := 1.0
	## Faction: extra population per House.
	var housePopBonus := 0.0
	## Food a new field holds (×).
	var farmFoodMult := 1.0
	## Spent fields replant without the wood.
	var freeReseed := false
	## HP per second a building mends once it has gone ten seconds unhurt.
	var buildingRegen := 0.0
	## Training time (×, <1 faster) by kind of unit.
	var villagerTrainMult := 1.0
	var infantryTrainMult := 1.0
	var cavTrainMult := 1.0
	var siegeTrainMult := 1.0
	## Training time of the Oath's own signature unit (commander affinity).
	var signatureTrainMult := 1.0
	## Flat armour and max HP (×) for infantry.
	var infantryArmor := 0.0
	var infantryHpMult := 1.0
	## Cavalry max HP (×).
	var cavHpMult := 1.0
	## Trade Cart pay (×), and extra gold per Market exchange.
	var tradeMult := 1.0
	var tradeRateBonus := 0.0
	## Gold gathering speed (×).
	var goldGatherMult := 1.0
	## Gold per second paid by each Market, for up to three of them.
	var tithePerMarket := 0.0
	## Siege max HP and attack (×).
	var siegeHpMult := 1.0
	var siegeAtkMult := 1.0
	## Veterancy earned faster (×).
	var vetMult := 1.0
	## The Champion's time to rise again (×, <1 faster).
	var heroRespawnMult := 1.0


static func emptyOathRules() -> OathRules:
	return OathRules.new()


## "+50%" for a 0.5 bonus at strength s.
static func pc(v: float, s: float) -> String:
	return "%s%%" % JS.num(JS.round(v * s * 100.0))


static func up(m: float, v: float, s: float) -> float:
	return m * (1.0 + v * s)


static func down(m: float, v: float, s: float) -> float:
	return m * maxf(0.2, 1.0 - v * s)


## What it does, one plain line each, at a given strength (1, or AFFINITY when
## sworn under its commander) — each OathDef's `lines`.
static func lines(id: String, s: float) -> Array:
	match id:
		"plough":
			return [
				"Villagers train %s faster" % pc(0.1, s),
				"Fields hold %s more food, and replant for free" % pc(0.3, s),
				"Food gathered %s faster" % pc(0.08, s),
			]
		"sword":
			return [
				"Infantry train %s faster and march %s faster" % [pc(0.3, s), pc(0.06, s)],
				"Infantry hit %s harder" % pc(0.12, s),
				"Unlocks the Sworn Blade at the Barracks",
			]
		"hearth":
			return [
				"Buildings have %s more HP and mend when left unharmed" % pc(0.3, s),
				"Villagers build and repair %s faster, and have %s more HP" % [pc(0.25, s), pc(0.2, s)],
				"Towers and Town Centres shoot %s faster; walls cost %s less" % [pc(0.3, s), pc(0.35, s)],
			]
		"lance":
			return [
				"Cavalry have %s more HP, ride %s faster" % [pc(0.15, s), pc(0.08, s)],
				"Cavalry train %s faster" % pc(0.2, s),
				"Unlocks the Lancer at the Stable — its charge hits three times as hard",
			]
		"bow":
			return [
				"Ranged units shoot %s further and %s harder" % [JS.num(JS.round(24.0 * s)), pc(0.1, s)],
				"Unlocks the Ranger at the Archery Range — stays hidden in woods even while shooting",
			]
		"shield":
			return [
				"Infantry +%s armour and %s more HP" % [JS.num(JS.round(2.0 * s)), pc(0.15, s)],
				"Unlocks the Halberdier at the Barracks — ruin for cavalry and rams",
			]
		"coin":
			return [
				"Trade Carts earn %s more; the Market pays %s more per trade" % [pc(0.5, s), JS.num(JS.round(12.0 * s))],
				"Gold mined %s faster" % pc(0.2, s),
				"Tithe: each Market pays %s gold a minute (up to 3)" % JS.num(JS.round(30.0 * s)),
			]
		"engine":
			return [
				"Siege weapons have %s more HP and hit %s harder" % [pc(0.3, s), pc(0.25, s)],
				"Siege weapons build %s faster" % pc(0.3, s),
				"Unlocks the Great Bombard at the Siege Workshop — outranges towers and castles",
			]
		"crown":
			return [
				"Every unit has %s more HP" % pc(0.12, s),
				"Veterancy comes %s faster; the Champion rises %s sooner" % [pc(0.6, s), pc(0.5, s)],
				"Unlocks the Royal Guard at the Castle",
			]
	return []


## Each OathDef's `apply`: fold the Oath into the boon effect and rules at strength s.
static func apply(id: String, b, r: OathRules, s: float) -> void:
	match id:
		"plough":
			r.villagerTrainMult = down(r.villagerTrainMult, 0.1, s)
			r.farmFoodMult = up(r.farmFoodMult, 0.3, s)
			r.freeReseed = true
			b.foodGatherMult = up(b.foodGatherMult, 0.08, s)
		"sword":
			r.infantryTrainMult = down(r.infantryTrainMult, 0.3, s)
			r.infantrySpeedMult = up(r.infantrySpeedMult, 0.06, s)
			b.atkMultInfantry = up(b.atkMultInfantry, 0.12, s)
		"hearth":
			b.buildingHpMult = up(b.buildingHpMult, 0.3, s)
			b.wallHpMult = up(b.wallHpMult, 0.3, s)
			r.buildingRegen += 4.0 * s
			r.buildSpeedMult = up(r.buildSpeedMult, 0.25, s)
			r.villagerHpMult = up(r.villagerHpMult, 0.2, s)
			b.towerCdMult = down(b.towerCdMult, 0.3, s)
			b.wallCostMult = down(b.wallCostMult, 0.35, s)
		"lance":
			r.cavHpMult = up(r.cavHpMult, 0.15, s)
			b.cavSpeedMult = up(b.cavSpeedMult, 0.08, s)
			r.cavTrainMult = down(r.cavTrainMult, 0.2, s)
		"bow":
			b.archerRangeBonus += 24.0 * s
			b.atkMultArcher = up(b.atkMultArcher, 0.1, s)
		"shield":
			r.infantryArmor += JS.round(2.0 * s)
			r.infantryHpMult = up(r.infantryHpMult, 0.15, s)
		"coin":
			r.tradeMult = up(r.tradeMult, 0.5, s)
			r.tradeRateBonus += JS.round(12.0 * s)
			r.goldGatherMult = up(r.goldGatherMult, 0.2, s)
			r.tithePerMarket += 0.5 * s
		"engine":
			r.siegeHpMult = up(r.siegeHpMult, 0.3, s)
			r.siegeAtkMult = up(r.siegeAtkMult, 0.25, s)
			r.siegeTrainMult = down(r.siegeTrainMult, 0.3, s)
		"crown":
			b.hpMult = up(b.hpMult, 0.12, s)
			r.vetMult = up(r.vetMult, 0.6, s)
			r.heroRespawnMult = down(r.heroRespawnMult, 0.5, s)


## The three Oaths offered on reaching `age`, in a stable order (OATHS defs).
static func oathsForAge(age: int) -> Array:
	var out: Array = []
	for id in C.OATHS:
		var o: Dictionary = C.OATHS[id]
		if int(o.age) == age:
			out.append(o)
	return out


## The Oath whose signature unit this is, if any (an OATHS def, or null).
static func oathForUnit(unitId: String):
	for id in C.OATHS:
		var o: Dictionary = C.OATHS[id]
		if o.get("unit") == unitId:
			return o
	return null


## Fold a realm's sworn Oaths into its boon effect and a fresh rules block.
## `favoured` is its commander's Oath, sworn at AFFINITY strength. `faction` is
## a FACTIONS def (its `apply` is Factions.apply), or null.
static func applyOaths(ids: Array, boon, favoured = null, faction = null) -> OathRules:
	var rules := emptyOathRules()
	if faction != null:
		Factions.apply(faction.id, boon, rules)
	for id in ids:
		if not C.OATHS.has(id):
			continue
		var o: Dictionary = C.OATHS[id]
		var strong: bool = favoured != null and id == favoured
		apply(id, boon, rules, C.AFFINITY if strong else 1.0)
		if strong and o.get("unit") != null:
			rules.signatureTrainMult *= C.AFFINITY_TRAIN_MULT
	return rules


## Oath chips for tight spaces (HUD, scoreboard): the short names, coloured.
static func oathChips(ids: Array) -> Array:
	var out: Array = []
	for id in ids:
		var o = C.OATHS.get(id)
		if o == null:
			continue
		out.append({"label": o.short, "color": o.color})
	return out
