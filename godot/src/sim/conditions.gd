## sim/conditions.ts — battlefield conditions: the weather and ground a
## Warband round is fought on.
##
## A round is fought in driving rain that slackens bowstrings, or deep mud that
## bogs down a cavalry charge. Conditions apply to *both* warbands equally, so
## they're a puzzle rather than a coin flip. Effects are expressed per trait, so
## they read as "rain hurts Marksmen" rather than as an opaque stat tweak.
##
## A Condition is a Dictionary with the TS keys ({id, name, desc, weather,
## color, all?, traits?, weight}); a Buff is a Dictionary of the traits.ts Buff
## fields used here (atkPct, atk, hpPct, hp, armor, speedPct). The table is not
## part of the exported content data, so it is transcribed here verbatim.
class_name Conditions

const CONDITIONS := [
	{
		"id": "clear", "name": "Clear Skies", "desc": "A still, bright field. Nothing favours anyone.",
		"weather": "clear", "color": "#cfe0ff", "weight": 30,
	},
	{
		"id": "rain", "name": "Driving Rain", "desc": "Slack bowstrings — Marksmen shoot 18% weaker.",
		"weather": "rain", "color": "#6fa8d0", "weight": 14,
		"traits": {"marksmen": {"atkPct": -18}},
	},
	{
		"id": "mud", "name": "Deep Mud", "desc": "The field is a bog. Everyone slows; Riders lose their charge.",
		"weather": "overcast", "color": "#9a7a4a", "weight": 12,
		"all": {"speedPct": -10},
		"traits": {"riders": {"speedPct": -25}},
	},
	{
		"id": "frost", "name": "Bitter Frost", "desc": "Numb hands and brittle frames — 8% less HP, siege 15% weaker.",
		"weather": "snow", "color": "#bcd8ea", "weight": 11,
		"all": {"hpPct": -8},
		"traits": {"engines": {"atkPct": -15}},
	},
	{
		"id": "gale", "name": "Howling Gale", "desc": "A wind at your backs: everyone 12% faster, Marksmen 10% weaker.",
		"weather": "rain", "color": "#7fe0d0", "weight": 11,
		"all": {"speedPct": 12},
		"traits": {"marksmen": {"atkPct": -10}},
	},
	{
		"id": "dusk", "name": "Dusk Raid", "desc": "Failing light. Marksmen shoot 22% weaker; Footmen hold 10% harder.",
		"weather": "overcast", "color": "#8a7ab0", "weight": 10,
		"traits": {"marksmen": {"atkPct": -22}, "footmen": {"hpPct": 10}},
	},
	{
		"id": "firm", "name": "Firm Ground", "desc": "Hard, level turf — Riders run 18% faster and hit 8% harder.",
		"weather": "clear", "color": "#e0a23a", "weight": 6,
		"traits": {"riders": {"speedPct": 18, "atkPct": 8}},
	},
	{
		"id": "stillair", "name": "Still Air", "desc": "Not a breath of wind. Siege and Marksmen find their range.",
		"weather": "clear", "color": "#e8c14b", "weight": 6,
		"traits": {"engines": {"atkPct": 25}, "marksmen": {"atkPct": 10}},
	},
]


static func conditionById(id: String):
	for c in CONDITIONS:
		if c.id == id:
			return c
	return null


## The opener is always clear — no reason to punish a first board.
static var CLEAR: Dictionary = CONDITIONS[0]


## Roll a condition by weight. Round 1 is always Clear Skies.
static func rollCondition(rng: RNG, round: int) -> Dictionary:
	if round <= 1:
		return CLEAR
	var total := 0.0
	for c in CONDITIONS:
		total += c.weight
	var pick := rng.range(0.0, total)
	for c in CONDITIONS:
		pick -= c.weight
		if pick < 0:
			return c
	return CLEAR


## True when a condition actually changes anything (Clear Skies doesn't).
static func hasEffect(c: Dictionary) -> bool:
	return (c.get("all") != null and c.all.size() > 0) or (c.get("traits") != null and c.traits.size() > 0)


static func _signed(v) -> String:
	return ("+" if v > 0 else "") + JS.num(v)


## Human-readable effect lines, for the tooltip. `traitName(id) -> String`.
static func conditionLines(c: Dictionary, traitName: Callable) -> Array:
	var out: Array = []
	var fmt := func(b: Dictionary) -> String:
		var parts: Array = []
		if b.get("atkPct", 0):
			parts.append("%s%% attack" % _signed(b.atkPct))
		if b.get("atk", 0):
			parts.append("%s attack" % _signed(b.atk))
		if b.get("hpPct", 0):
			parts.append("%s%% HP" % _signed(b.hpPct))
		if b.get("hp", 0):
			parts.append("%s HP" % _signed(b.hp))
		if b.get("armor", 0):
			parts.append("%s armour" % _signed(b.armor))
		if b.get("speedPct", 0):
			parts.append("%s%% speed" % _signed(b.speedPct))
		return ", ".join(parts)
	if c.get("all") != null:
		out.append("Everyone: %s" % fmt.call(c.all))
	for id in c.get("traits", {}):
		out.append("%s: %s" % [traitName.call(id), fmt.call(c.traits[id])])
	return out
