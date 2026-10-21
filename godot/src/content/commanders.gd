## content/commanders.ts — the functions half of the commanders module. The
## data (COMMANDERS, COMMANDER_IDS) lives in `C`.
class_name Commanders


## A one-line summary of a commander's perks (a COMMANDERS def), for menus.
static func commanderPerks(def: Dictionary) -> Array:
	var b: Dictionary = def.get("bonus", {})
	var out: Array = []
	if b.get("startVillagers", 0):
		out.append("+%s starting villagers" % JS.num(b.startVillagers))
	if b.get("startResources") != null:
		var r: Dictionary = b.startResources
		var parts: Array = []
		if r.get("food", 0):
			parts.append("%s food" % JS.num(r.food))
		if r.get("wood", 0):
			parts.append("%s wood" % JS.num(r.wood))
		if r.get("gold", 0):
			parts.append("%s gold" % JS.num(r.gold))
		out.append("Start +%s" % ", ".join(parts))
	if b.get("gatherMult", 0):
		out.append("+%s%% gather rate" % JS.num(JS.round((b.gatherMult - 1.0) * 100.0)))
	if b.get("buildMult", 0):
		out.append("%s× build & repair speed" % JS.num(b.buildMult))
	if b.get("unitHpMult", 0):
		out.append("+%s%% unit health" % JS.num(JS.round((b.unitHpMult - 1.0) * 100.0)))
	if b.get("unitArmorBonus", 0):
		out.append("+%s unit armor" % JS.num(b.unitArmorBonus))
	if b.get("popBonus", 0):
		out.append("+%s starting population" % JS.num(b.popBonus))
	if b.get("vetMult", 0):
		out.append("+%s%% veterancy gain" % JS.num(JS.round((b.vetMult - 1.0) * 100.0)))
	if def.get("power") != null:
		out.append("Power: %s" % def.power.name)
	return out
