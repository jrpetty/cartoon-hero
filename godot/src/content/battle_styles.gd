## content/battle_styles.ts — the functions half of the battle styles module.
## The data (BATTLE_STYLES, UNIT_STYLE) lives in `C`.
class_name BattleStyles


static func styleOf(type: String) -> String:
	return C.UNIT_STYLE.get(type, "line")


static func styleDef(type: String) -> Dictionary:
	return C.BATTLE_STYLES[styleOf(type)]
