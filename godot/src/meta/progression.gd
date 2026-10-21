## Commander XP/level curve and per-match reward calculation (XP + Renown).
## Renown is intentionally a slow grind — distinct from in-game gold.
##
## MatchOutcome: { win, durationSec, unitsKilled, buildingsRazed,
##   difficulty ("squire" | "knight" | "lord" | "warlord"), fairMode (ranked/all-common match) }
## MatchRewards: { xp, renown, valor (combat-earned currency, spent on boon caches),
##   breakdown: [{ label, xp, renown, valor }] }
class_name Progression

const DIFF_MULT := {
	"squire": 0.7,
	"knight": 1.0,
	"lord": 1.35,
	"warlord": 1.8,
}


## XP required to go from `level` to `level+1`. Gentle quadratic curve.
static func xpForLevel(level: float) -> float:
	return floor(100.0 + level * 75.0 + level * level * 12.0)


## Total XP at the start of a level (cumulative).
static func totalXpForLevel(level: float) -> float:
	var t := 0.0
	var l := 1.0
	while l < level:
		t += xpForLevel(l)
		l += 1.0
	return t


## Given total xp, return {level, into, need}. Levels start at 1.
static func levelFromXp(totalXp: float) -> Dictionary:
	var level := 1.0
	var remaining := totalXp
	while remaining >= xpForLevel(level):
		remaining -= xpForLevel(level)
		level += 1.0
	return {"level": level, "into": remaining, "need": xpForLevel(level)}


static func computeRewards(o: Dictionary) -> Dictionary:
	var mult: float = float(DIFF_MULT.get(o.difficulty, 1.0)) * (1.25 if o.fairMode else 1.0)
	var breakdown: Array = []
	var add := func(label: String, xp: float, renown: float, valor: float) -> void:
		breakdown.append({"label": label, "xp": JS.round(xp), "renown": JS.round(renown), "valor": JS.round(valor)})

	# Valor is combat-weighted (kills/razings/wins) — boons are earned by fighting.
	add.call("Match played", 40.0, 12.0, 5.0)
	if o.win:
		add.call("Victory", 120.0 * mult, 45.0 * mult, 25.0 * mult)
	else:
		add.call("Defeat (still learning)", 25.0, 8.0, 6.0)

	var mins := float(o.durationSec) / 60.0
	add.call("Time on field", minf(mins, 30.0) * 6.0, minf(mins, 30.0) * 1.5, 0.0)
	var k := float(o.unitsKilled)
	add.call("Enemies slain", k * 1.5, k * 0.4, k * 0.9)
	var b := float(o.buildingsRazed)
	add.call("Buildings razed", b * 8.0 * mult, b * 2.0 * mult, b * 3.0 * mult)

	var xp := 0.0
	var renown := 0.0
	var valor := 0.0
	for row in breakdown:
		xp += row.xp
		renown += row.renown
		valor += row.valor
	return {"xp": JS.round(xp), "renown": JS.round(renown), "valor": JS.round(valor), "breakdown": breakdown}


## Reward granted on each level-up.
static func levelUpRenown(level: float) -> float:
	return 50.0 + level * 10.0
