## sim/warband_difficulty.ts — Warband Tactics lobby difficulty.
##
## The seven rival warbands already run the *same* economy the player does —
## income, interest, levelling, rerolls, star-ups, relics. Difficulty is
## therefore not a stat multiplier bolted onto their units: it is how hard they
## play the same game. A Squire lobby banks lazily and techs slowly; a
## Conqueror lobby banks well, techs on curve and rolls hard for its carries.
##
## The only player-facing knob is how much a defeat costs, which is what makes
## the low end genuinely forgiving rather than merely slower.
##
## The data (`WARBAND_DIFFICULTIES`) is in `C`; a WarbandDifficulty is a
## Dictionary {id, name, title, blurb, color, foeIncome, foeLevelPace,
## foeRerolls, foeRelicEvery, damageTaken, startGold}.
class_name WarbandDifficulty


static var WARBAND_DIFFICULTIES: Array:
	get:
		return C.WARBAND_DIFFICULTIES


## The default — the one the rest of the game is balanced around. (The entry in
## WARBAND_DIFFICULTIES itself, as in the TS, rather than C's separate copy.)
static var VETERAN: Dictionary:
	get:
		return _byId("veteran")


static func _byId(id) -> Variant:
	for d in C.WARBAND_DIFFICULTIES:
		if d.id == id:
			return d
	return null


static func difficultyById(id) -> Dictionary:
	if id != null and str(id) != "":
		var d = _byId(str(id))
		if d != null:
			return d
	return VETERAN
