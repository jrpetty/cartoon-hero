## sim/creeps.ts — Warband Tactics monster camps, the PvE rounds. Like TFT's
## minion/krug/wolf rounds, these break up the PvP ladder: no player life is on
## the line, the camp is visible while you set up, and beating it drops relics
## and gold. They are also the run's opening round, so you always start with a
## fight you can see and a relic to build around.
##
## A CreepCamp is a Dictionary {id, name, blurb, relics, gold, bite,
## units: [{type, star, count}]}. The scripted list is not exported by the TS
## module (it is private there), so it is transcribed here verbatim.
class_name Creeps

## The scripted camps, in the order they appear.
const CAMPS := [
	{
		"id": "wolves", "name": "Wolf Pack", "blurb": "A lean opening scrap — win it for your first relic.",
		"relics": 1, "gold": 2, "bite": 2,
		"units": [{"type": "militia", "star": 1, "count": 2}],
	},
	{
		"id": "bandits", "name": "Bandit Camp", "blurb": "Raiders with a bowman covering them.",
		"relics": 1, "gold": 3, "bite": 3,
		"units": [{"type": "raider", "star": 1, "count": 3}, {"type": "archer", "star": 1, "count": 1}],
	},
	{
		"id": "mercs", "name": "Mercenary Band", "blurb": "Seasoned swords — bring a real board.",
		"relics": 2, "gold": 4, "bite": 4,
		"units": [{"type": "knight", "star": 2, "count": 2}, {"type": "crossbow", "star": 1, "count": 2}],
	},
	{
		"id": "guard", "name": "Warlord's Guard", "blurb": "Elites in full harness. The drop is worth it.",
		"relics": 2, "gold": 6, "bite": 5,
		"units": [{"type": "twohand", "star": 2, "count": 2}, {"type": "handcannon", "star": 2, "count": 1}, {"type": "knight", "star": 2, "count": 1}],
	},
	{
		"id": "legion", "name": "Ancient Legion", "blurb": "A dead empire's honour guard, still holding the line.",
		"relics": 3, "gold": 8, "bite": 6,
		"units": [{"type": "hero", "star": 2, "count": 1}, {"type": "twohand", "star": 3, "count": 1}, {"type": "pikeman", "star": 2, "count": 3}],
	},
]

# Camps stand on the enemy half, front rank first, rows from the middle out.
const ROW_ORDER := [4, 5, 3, 6, 2, 7, 1, 8, 0, 9]
static var _cells = null


static func _CELLS() -> Array:
	if _cells == null:
		var out: Array = []
		for c in [5, 6, 7, 8, 9]:
			for r in ROW_ORDER:
				out.append({"c": c, "r": r})
		_cells = out
	return _cells


## The final challenge. Outlasting the lobby used to end the run mid-sentence:
## the last rival fell to off-screen attrition and a banner appeared. Now the
## arena's own champions ride out, and the crown has to be taken from them.
##
## It scales with the round you reached, because a run that wins in stage 4 has
## a very different board from one that grinds to stage 8, and a fixed board
## would be a wall to the first and a formality to the second.
static func bossCamp(round: int, damageScale: float = 1.0) -> Dictionary:
	var step := maxi(0, floori((round - 12) / 4.0)) # 0 up to about stage 4
	return {
		"id": "crown",
		"name": "The Iron Crown",
		"blurb": "The arena's own champions. Beat them and the crown is yours.",
		"relics": 0, "gold": 0,
		"bite": int(JS.round(45.0 * damageScale)),
		"units": [
			{"type": "hero", "star": 2, "count": 1},
			{"type": "twohand", "star": 2, "count": 1},
			{"type": "cataphract", "star": 2, "count": 1 + mini(1, step)},
			{"type": "handcannon", "star": 1, "count": 1},
			{"type": "shieldbearer", "star": 2, "count": 1},
			{"type": "longbow", "star": 2, "count": 1 + mini(1, step)},
			{"type": "battlemage", "star": 1, "count": 1},
		],
	}


## Camp rounds: the opener, then one every fifth round.
static func isCreepRound(round: int) -> bool:
	return round == 1 or round % 5 == 1


## Which camp a round faces (later rounds repeat the last camp, scaled up).
static func campForRound(round: int) -> Dictionary:
	var idx := maxi(0, floori((round - 1) / 5.0))
	if idx < CAMPS.size():
		return CAMPS[idx]
	# Past the scripted list, keep the toughest camp and grow it.
	var base: Dictionary = CAMPS[CAMPS.size() - 1]
	var extra := idx - CAMPS.size() + 1
	var units: Array = []
	for u in base.units:
		units.append({"type": u.type, "star": u.star, "count": u.count + floori(extra / 2.0)})
	return {
		"id": base.id,
		"name": "%s +%d" % [base.name, extra],
		"blurb": base.blurb,
		"relics": base.relics + floori(extra / 2.0),
		"gold": base.gold + extra * 2,
		"bite": base.bite + extra,
		"units": units,
	}


## Expand a camp into placed arena units on the enemy half.
static func campBoard(camp: Dictionary) -> Array:
	var cells := _CELLS()
	var out: Array = []
	for u in camp.units:
		for k in int(u.count):
			var cell: Dictionary = cells[mini(out.size(), cells.size() - 1)]
			out.append({"type": u.type, "star": int(u.star), "items": [], "col": cell.c, "row": cell.r})
	return out
