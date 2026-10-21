## Playstyle: how a player wins, read from what they actually did.
##
## Two levels, both built only from numbers the game already records:
##
##  • Each match gets a style — Rush, Raid, Demolition, Turtle, Boom, Fast Age,
##    Late Game or Standard — from that game alone: when you first attacked,
##    what you built, what you broke, how big your economy grew, measured
##    against the average enemy in that same game.
##
##  • Across a career, eight strategy archetypes are scored 0–1 from the
##    player's whole record, the strongest becomes their style (with a name
##    that says how: "Man-at-Arms Rush", "Siege Grinder"), and a set of traits
##    adds the rest (Cavalry Commander, Faction Specialist, Efficient Trader…).
##    Every score comes with the evidence behind it, so a player can see *why*
##    they're a Boomer, and what the Rusher threshold would need.
##
## "More than usual" always means more than the opponents you actually faced
## (each match stores the average enemy's numbers), not a guessed norm.
##
## Matches are CareerMatch Dictionaries (see Career); everything returned is a
## Dictionary with the TS field names.
class_name Playstyle

## (Also exported as C.MIN_GAMES; a test keeps the two equal.)
const MIN_GAMES := 5

# ------------------------------------------------------------------ helpers --
static func mean(xs: Array) -> float:
	if xs.is_empty():
		return 0.0
	var s := 0.0
	for x in xs:
		s += float(x)
	return s / xs.size()


static func median(xs: Array) -> float:
	if xs.is_empty():
		return 0.0
	var a := xs.duplicate()
	JS.sort_by_cmp(a, func(p, q): return float(p) - float(q))
	var m := a.size() >> 1
	return float(a[m]) if a.size() % 2 else (float(a[m - 1]) + float(a[m])) / 2.0


## 0 at or below `lo`, 1 at or above `hi`, linear between.
static func ramp(x: float, lo: float, hi: float) -> float:
	return maxf(0.0, minf(1.0, (x - lo) / (hi - lo)))


static func ratio(a: float, b: float) -> float:
	return a / b if b > 0 else (2.0 if a > 0 else 1.0)


static func mmss(s: float) -> String:
	return JS.num(floor(s / 60.0)) + ":" + JS.num(JS.round(fmod(s, 60.0))).lpad(2, "0")


static func pct(x: float) -> String:
	return JS.num(JS.round(x * 100.0)) + "%"


static func unitName(id: String) -> String:
	var d = C.UNITS.get(id)
	return d.name if d != null else id


static func facName(id: String) -> String:
	var d = C.FACTIONS.get(id)
	return d.name if d != null else id


## JS truthiness (an empty object or array is truthy, unlike in GDScript).
static func _truthy(v) -> bool:
	if v == null:
		return false
	if v is bool:
		return v
	if v is float or v is int:
		return v != 0 and not is_nan(float(v))
	if v is String:
		return v != ""
	return true


## `arr[i]`, or null where JS would give undefined.
static func _at(arr, i: int):
	return arr[i] if arr is Array and i >= 0 and i < arr.size() else null


## `m.foe?.[key] ?? fallback`
static func _foe(m: Dictionary, key: String, fallback := 0.0) -> float:
	var foe = m.get("foe")
	if foe == null:
		return fallback
	return float(JS.nz(foe.get(key), fallback))


## Object.entries(rec) as [key, value] pairs.
static func _entries(rec: Dictionary) -> Array:
	var out: Array = []
	for k in rec:
		out.append([k, rec[k]])
	return out


## Sort [key, value] pairs by value, highest first (stable, as JS sorts).
static func _by_value_desc(pairs: Array) -> Array:
	return JS.sort_by_cmp(pairs, func(a, b): return float(b[1]) - float(a[1]))


## UnitClass: "infantry" | "archer" | "cavalry" | "siege" | "support".
## A military unit's class (villagers and unknowns → null).
static func unitClass(id: String):
	var d = C.UNITS.get(id)
	if d == null or id == "villager" or d.armorClass == T.AC_VILLAGER:
		return null
	if _truthy(d.get("healer")):
		return "support"
	match d.armorClass:
		T.AC_INFANTRY:
			return "infantry"
		T.AC_ARCHER:
			return "archer"
		T.AC_CAVALRY:
			return "cavalry"
		T.AC_SIEGE:
			return "siege"
		_:
			return null


## Military units trained, by type (villagers left out).
static func military(m: Dictionary) -> Dictionary:
	var out := {}
	for u in m.trained:
		if unitClass(u):
			out[u] = m.trained[u]
	return out


## When you first attacked: the first blow on the enemy's own ground. Records
## from before attack and defence were told apart only have "first hit on an
## enemy anywhere", which also counts beating off their attack at home.
static func attackAt(m: Dictionary) -> float:
	return float(JS.nz(m.get("firstAttackAt"), JS.nz(m.get("firstHitAt"), -1.0)))


const CLASS_WORD := {"infantry": "Infantry", "archer": "Archer", "cavalry": "Cavalry", "siege": "Siege", "support": "Monk"}


## What your early attacks are made of, as one word: the unit if one dominates
## (Man-at-Arms), else the class (Archer, Cavalry), else "Mixed". Read from the
## damage each unit type did away from home in the first ten minutes, so it is
## what actually fought, not what sat at home.
## Returns { word, unit, share }.
static func openerWord(opener: Dictionary) -> Dictionary:
	var mil := _entries(opener).filter(func(e): return unitClass(e[0]))
	var total := 0.0
	for e in mil:
		total += float(e[1])
	if total <= 0:
		return {"word": "", "unit": "", "share": 0.0}
	_by_value_desc(mil)
	var unit: String = mil[0][0]
	var top := float(mil[0][1])
	if top / total >= 0.5:
		return {"word": unitName(unit), "unit": unit, "share": top / total}
	var cls := {}
	for e in mil:
		var c: String = unitClass(e[0])
		cls[c] = float(cls.get(c, 0.0)) + float(e[1])
	var best: Array = _by_value_desc(_entries(cls))[0]
	var cv := float(best[1])
	if cv / total >= 0.55:
		return {"word": CLASS_WORD[best[0]], "unit": "", "share": cv / total}
	return {"word": "Mixed", "unit": "", "share": top / total}


static func sum(r: Dictionary) -> float:
	var s := 0.0
	for k in r:
		s += float(r[k])
	return s


static func villagerKills(m: Dictionary) -> float:
	return float(m.killed.get("villager", 0.0))


static func gatherPerMin(m: Dictionary) -> float:
	return float(m.gathered) / (m.durationSec / 60.0) if m.durationSec > 0 else 0.0


static func foeGatherPerMin(m: Dictionary) -> float:
	return float(m.foe.gathered) / (m.durationSec / 60.0) if m.get("foe") != null and m.durationSec > 0 else 0.0


# ------------------------------------------------------- one match's style --
## MatchStyle: "rush" | "raid" | "demolition" | "turtle" | "boom" | "fastage" | "lategame" | "standard".
## The names, colours and written rules are data: C.MATCH_STYLES.
static var MATCH_STYLES: Dictionary:
	get:
		return C.MATCH_STYLES


## Every style this match fits, strongest signal first.
static func matchStyles(m: Dictionary) -> Array:
	var out: Array = []
	var hit := attackAt(m)
	if hit >= 0 and hit <= 420:
		out.append("rush")
	var vk := villagerKills(m)
	if vk >= 8 and vk >= _foe(m, "villagerKills") * 1.5:
		out.append("raid")
	if m.razed >= 5 and m.razed >= _foe(m, "razed") * 1.5:
		out.append("demolition")
	var def := Career.defensesBuilt(m.built)
	var quietStart := hit < 0 or hit >= 600
	if def >= 6 and def >= _foe(m, "defenses") * 1.5 and quietStart:
		out.append("turtle")
	if m.peakVillagers >= 35 and m.peakVillagers >= _foe(m, "peakVillagers") * 1.2 and quietStart:
		out.append("boom")
	var crown = _at(m.ageTimes, 2)
	if (crown != null and crown > 0 and crown <= 840) or (m.upgrades >= 6 and m.upgrades >= _foe(m, "upgrades") * 1.5):
		out.append("fastage")
	if m.durationSec >= 2100:
		out.append("lategame")
	return out if out.size() > 0 else ["standard"]


static func matchStyle(m: Dictionary) -> String:
	return matchStyles(m)[0]


# --------------------------------------------------------- career features --
## StyleFeatures: games, wins, winRate,
##   winsBefore15 / wins15to30 / wins30to40 / winsAfter40: share of wins by length,
##   avgWinSec,
##   rushRate: games with a first attack before 7:00, of games that recorded it,
##   medianFirstHit,
##   rushUnit, rushUnitShare, openerWord: what your early attacks are made of
##     ("Man-at-Arms", "Archer", "Mixed"),
##   lateWins: wins that went past 30 minutes, as a share of wins,
##   tcIdle: average share of the match your Town Center sat idle,
##   heldRushRate: of games where the enemy attacked before 8:00, the share you won,
##   rushedGames, classShare, topUnit, topUnitShare,
##   diversity: effective number of unit types you field (1 = one unit only),
##   razedPerGame, razedRatio, raidPerGame, raidRatio, defensesPerGame,
##   defenseRatio, peakVillagers, villagerRatio, gatherPerMin, gatherRatio,
##   upgradesPerGame, upgradeRatio, crownAvgSec, crownRate, empireRate, kd,
##   foeKd, combatPerMin, combatRatio, lateShare, lateWinRate, earlyLossShare,
##   spendUnits, spendBuildings, spendTech, topFaction, topFactionShare,
##   factionsPlayed, topOath, topOathShare, topMap, topMapShare, topMapWinRate.
static func features(ms: Array) -> Dictionary:
	var n := ms.size()
	var wins := ms.filter(func(m): return m.won)
	var losses := ms.filter(func(m): return not m.won)
	var winSecs := wins.map(func(m): return m.durationSec)
	var share := func(xs: Array, f: Callable) -> float:
		return float(xs.filter(f).size()) / xs.size() if xs.size() > 0 else 0.0
	var withHit := ms.filter(func(m): return m.has("firstAttackAt") or m.has("firstHitAt"))
	# What the early attacks were made of: the damage each unit did away from
	# home in the first ten minutes of games where you attacked early. Older
	# records without that fall back to what was trained in fast wins.
	var opener := {}
	for m in ms:
		var a := attackAt(m)
		if a < 0 or a > 600:
			continue
		if _truthy(m.get("opener")):
			var t := sum(m.opener)
			if t == 0.0:
				t = 1.0
			for u in m.opener:
				opener[u] = float(opener.get(u, 0.0)) + m.opener[u] / t
		elif m.won and m.durationSec <= 900:
			var mil := military(m)
			var t := sum(mil)
			if t == 0.0:
				t = 1.0
			for u in mil:
				opener[u] = float(opener.get(u, 0.0)) + mil[u] / t
	var op := openerWord(opener)
	var rushUnit: String = op.unit
	var rushed := ms.filter(func(m):
		var foe = m.get("foe")
		return foe != null and foe.get("firstAttackAt") != null and foe.firstAttackAt >= 0 and foe.firstAttackAt <= 480)
	# Army make-up.
	var allMil := {}
	for m in ms:
		var mil := military(m)
		for u in mil:
			allMil[u] = float(allMil.get(u, 0.0)) + mil[u]
	var milTotal := maxf(1.0, sum(allMil))
	var classShare := {"infantry": 0.0, "archer": 0.0, "cavalry": 0.0, "siege": 0.0, "support": 0.0}
	for u in allMil:
		var c = unitClass(u)
		if c:
			classShare[c] += allMil[u] / milTotal
	var topUnitEntry = _at(_by_value_desc(_entries(allMil)), 0)
	var entropy := 0.0
	for u in allMil:
		var p: float = allMil[u] / milTotal
		entropy += p * log(p) if p > 0 else 0.0
	entropy = -entropy
	# Against the opponents faced.
	var withFoe := ms.filter(func(m): return m.get("foe") != null)
	var foeMean := func(f: Callable) -> float: return mean(withFoe.map(f))
	var youMean := func(f: Callable) -> float: return mean(withFoe.map(f))
	var crowns := ms.map(func(m): return _at(m.ageTimes, 2)).filter(func(t): return (t is float or t is int) and t > 0)
	var kills := 0.0
	var lost := 0.0
	var minutes := 0.0
	for m in ms:
		kills += m.kills
		lost += m.losses
		minutes += m.durationSec / 60.0
	var foeKills := 0.0
	var foeLost := 0.0
	for m in withFoe:
		foeKills += m.foe.kills
		foeLost += m.foe.losses
	var late := ms.filter(func(m): return m.durationSec >= 1800)
	var spent := {"u": 0.0, "b": 0.0, "t": 0.0}
	for m in ms:
		var s = m.get("spentOn")
		if _truthy(s):
			spent.u += s.units
			spent.b += s.buildings
			spent.t += s.tech
	var spentAll := maxf(1.0, spent.u + spent.b + spent.t)
	var count := func(key: Callable) -> Array:
		var c := {}
		for m in ms:
			var ks = key.call(m)
			for k in (ks if ks is Array else [ks]):
				if _truthy(k):
					c[k] = int(c.get(k, 0)) + 1
		return _by_value_desc(_entries(c))
	var facs: Array = count.call(func(m): return m.faction)
	var oaths: Array = count.call(func(m): return m.oaths)
	var sworn := ms.filter(func(m): return m.oaths.size() > 0).size()
	var maps: Array = count.call(func(m): return m.map)
	var topMap: String = maps[0][0] if maps.size() > 0 else ""
	var onTopMap := ms.filter(func(m): return m.map == topMap)
	return {
		"games": n,
		"wins": wins.size(),
		"winRate": float(wins.size()) / n if n else 0.0,
		"winsBefore15": share.call(wins, func(m): return m.durationSec < 900),
		"wins15to30": share.call(wins, func(m): return m.durationSec >= 900 and m.durationSec < 1800),
		"wins30to40": share.call(wins, func(m): return m.durationSec >= 1800 and m.durationSec < 2400),
		"winsAfter40": share.call(wins, func(m): return m.durationSec >= 2400),
		"avgWinSec": mean(winSecs),
		"rushRate": share.call(withHit, func(m): return attackAt(m) >= 0 and attackAt(m) <= 420),
		"medianFirstHit": median(withHit.map(func(m): return attackAt(m) if attackAt(m) >= 0 else m.durationSec)),
		"rushUnit": rushUnit,
		"rushUnitShare": op.share,
		"openerWord": op.word,
		"lateWins": share.call(wins, func(m): return m.durationSec >= 1800),
		"tcIdle": mean(ms.map(func(m): return m.tcIdleShare)),
		"heldRushRate": share.call(rushed, func(m): return m.won),
		"rushedGames": rushed.size(),
		"classShare": classShare,
		"topUnit": topUnitEntry[0] if topUnitEntry != null else "",
		"topUnitShare": topUnitEntry[1] / milTotal if topUnitEntry != null else 0.0,
		"diversity": exp(entropy) if allMil.size() > 0 else 0.0,
		"razedPerGame": mean(ms.map(func(m): return m.razed)),
		"razedRatio": ratio(youMean.call(func(m): return m.razed), foeMean.call(func(m): return m.foe.razed)),
		"raidPerGame": mean(ms.map(func(m): return villagerKills(m))),
		"raidRatio": ratio(youMean.call(func(m): return villagerKills(m)), foeMean.call(func(m): return m.foe.villagerKills)),
		"defensesPerGame": mean(ms.map(func(m): return Career.defensesBuilt(m.built))),
		"defenseRatio": ratio(youMean.call(func(m): return Career.defensesBuilt(m.built)), foeMean.call(func(m): return m.foe.defenses)),
		"peakVillagers": mean(ms.map(func(m): return m.peakVillagers)),
		"villagerRatio": ratio(youMean.call(func(m): return m.peakVillagers), foeMean.call(func(m): return m.foe.peakVillagers)),
		"gatherPerMin": mean(ms.map(func(m): return gatherPerMin(m))),
		"gatherRatio": ratio(youMean.call(func(m): return gatherPerMin(m)), foeMean.call(func(m): return foeGatherPerMin(m))),
		"upgradesPerGame": mean(ms.map(func(m): return m.upgrades)),
		"upgradeRatio": ratio(youMean.call(func(m): return m.upgrades), foeMean.call(func(m): return m.foe.upgrades)),
		"crownAvgSec": mean(crowns),
		"crownRate": share.call(ms, func(m):
			var c = _at(m.ageTimes, 2)
			return (c is float or c is int) and c > 0),
		"empireRate": share.call(ms, func(m): return m.age >= 3),
		"kd": kills / lost if lost else kills,
		"foeKd": foeKills / foeLost if foeLost else foeKills,
		"combatPerMin": (kills + lost) / minutes if minutes else 0.0,
		"combatRatio": ratio(
			youMean.call(func(m): return (m.kills + m.losses) / maxf(1.0, m.durationSec / 60.0)),
			foeMean.call(func(m): return (m.foe.kills + m.foe.losses) / maxf(1.0, m.durationSec / 60.0))),
		"lateShare": share.call(ms, func(m): return m.durationSec >= 2100),
		"lateWinRate": share.call(late, func(m): return m.won),
		"earlyLossShare": share.call(losses, func(m): return m.durationSec < 900),
		"spendUnits": spent.u / spentAll,
		"spendBuildings": spent.b / spentAll,
		"spendTech": spent.t / spentAll,
		"topFaction": facs[0][0] if facs.size() > 0 else "",
		"topFactionShare": (float(facs[0][1]) if facs.size() > 0 else 0.0) / n if n else 0.0,
		"factionsPlayed": facs.filter(func(e): return e[1] >= 2).size(),
		"topOath": oaths[0][0] if oaths.size() > 0 else "",
		"topOathShare": (float(oaths[0][1]) if oaths.size() > 0 else 0.0) / sworn if sworn else 0.0,
		"topMap": topMap,
		"topMapShare": float(onTopMap.size()) / n if n else 0.0,
		"topMapWinRate": float(onTopMap.filter(func(m): return m.won).size()) / onTopMap.size() if onTopMap.size() > 0 else 0.0,
	}


# -------------------------------------------------------------- archetypes --
## Evidence: { label, value, weight (how far toward the style's threshold, 0–1) }.
static func ev(label: String, value: String, weight: float) -> Dictionary:
	return {"label": label, "value": value, "weight": maxf(0.0, minf(1.0, weight))}


## Archetype: { id, name, emblem, short (one line: what the style is),
##   criteria (the rule, in plain words, for the "every style" list),
##   score: Callable(f) -> float,
##   title: Callable(f) -> String or null (a specific name for this player,
##     e.g. "Man-at-Arms Rush"),
##   describe: Callable(f) -> String (how this player plays it, written from
##     their numbers),
##   evidence: Callable(f) -> Evidence[],
##   strengths, risks, tip }
## The text is data (C.ARCHETYPES); the functions are here, matched up by id.
static var _archetypes: Array = []

static var ARCHETYPES: Array:
	get:
		if _archetypes.is_empty():
			var fns := _archetype_fns()
			for row in C.ARCHETYPES:
				var a: Dictionary = row.duplicate()
				assert(fns.has(a.id), "archetype %s has no functions ported" % a.id)
				var fn: Dictionary = fns[a.id]
				a["score"] = fn.score
				a["title"] = fn.get("title")
				a["describe"] = fn.describe
				a["evidence"] = fn.evidence
				_archetypes.append(a)
		return _archetypes


static func _archetype_fns() -> Dictionary:
	return {
		"rush": {
			"score": func(f): return 0.5 * ramp(f.rushRate, 0.25, 0.65) + 0.5 * ramp(f.winsBefore15, 0.25, 0.65),
			"title": func(f): return ("%s Rush" % f.openerWord) if f.openerWord else "Rusher",
			"describe": func(f):
				var opens := ""
				if f.openerWord:
					var how: String
					if f.openerWord == "Mixed":
						how = "a mixed force"
					elif f.rushUnit:
						how = "the %s — %s of your early damage" % [f.openerWord, pct(f.rushUnitShare)]
					else:
						how = "%s units — %s of your early damage" % [f.openerWord.to_lower(), pct(f.rushUnitShare)]
					opens = " You open with %s." % how
				return "You go straight for the throat. Your first attack on their base lands before 7:00 in %s of your games (median %s), and %s of your wins are over inside 15 minutes.%s You win by denying your opponent the time to build anything worth defending." % [pct(f.rushRate), mmss(f.medianFirstHit), pct(f.winsBefore15), opens],
			"evidence": func(f): return [
				ev("Games you attack before 7:00", pct(f.rushRate), ramp(f.rushRate, 0.25, 0.65)),
				ev("Wins inside 15 minutes", pct(f.winsBefore15), ramp(f.winsBefore15, 0.25, 0.65)),
				ev("Median first attack", mmss(f.medianFirstHit), 1 - ramp(f.medianFirstHit, 300, 720)),
			],
		},
		"pressure": {
			"score": func(f): return 0.45 * ramp(f.rushRate, 0.25, 0.65) + 0.55 * ramp(maxf(f.lateShare, f.lateWins), 0.25, 0.6),
			"title": func(f): return ("%s Harass, Late Closer" % f.openerWord) if f.openerWord else "Early Pressure, Late Finisher",
			"describe": func(f):
				var with_ := ""
				if f.openerWord:
					with_ = ", usually with " + ("a mixed force" if f.openerWord == "Mixed" else f.openerWord.to_lower() + ("s" if f.rushUnit else " units"))
				return "You attack early but you don't need the rush to end it. Your first attack on their base lands before 7:00 in %s of your games%s, yet %s of your wins come after 30 minutes. The early hits cost them villagers and time; you cash that in with the stronger late army." % [pct(f.rushRate), with_, pct(f.lateWins)],
			"evidence": func(f): return [
				ev("Games you attack before 7:00", pct(f.rushRate), ramp(f.rushRate, 0.25, 0.65)),
				ev("Wins after 30 minutes", pct(f.lateWins), ramp(f.lateWins, 0.25, 0.6)),
				ev("Games past 35 minutes", pct(f.lateShare), ramp(f.lateShare, 0.25, 0.6)),
			],
		},
		"turtle": {
			"score": func(f): return 0.5 * ramp(f.winsAfter40 + 0.5 * f.wins30to40, 0.2, 0.55) + 0.5 * (ramp(f.defenseRatio, 1.2, 2.5) if f.defensesPerGame >= 3 else 0.0),
			"title": func(f): return "Turtle — plays for the late game" if f.winsAfter40 >= 0.4 else "Turtle",
			"describe": func(f): return "You make yourself hard to kill. You build %s tower-equivalents of defence a game — %s× your opponents — and your wins come late: %s after 40 minutes, %s between 30 and 40. You let the other side break itself on your walls, then win with the stronger late army." % [JS.to_fixed(f.defensesPerGame, 1), JS.to_fixed(f.defenseRatio, 1), pct(f.winsAfter40), pct(f.wins30to40)],
			"evidence": func(f): return [
				ev("Defence built vs opponents", "%s×" % JS.to_fixed(f.defenseRatio, 1), ramp(f.defenseRatio, 1.2, 2.5)),
				ev("Defence per game", JS.to_fixed(f.defensesPerGame, 1), ramp(f.defensesPerGame, 3, 10)),
				ev("Wins after 40 minutes", pct(f.winsAfter40), ramp(f.winsAfter40, 0.2, 0.55)),
			],
		},
		"late": {
			"score": func(f): return 0.35 * ramp(f.lateShare, 0.25, 0.6) + 0.35 * ramp(f.empireRate, 0.15, 0.5) + 0.3 * ramp(f.lateWinRate, 0.5, 0.75),
			"describe": func(f): return "Your games run long — %s go past 35 minutes — and you reach the Empire Age in %s of them. In games that last, you win %s. You play the full tech tree and win with the army nobody can field in the Banner Age." % [pct(f.lateShare), pct(f.empireRate), pct(f.lateWinRate)],
			"evidence": func(f): return [
				ev("Games past 35 minutes", pct(f.lateShare), ramp(f.lateShare, 0.25, 0.6)),
				ev("Reach the Empire Age", pct(f.empireRate), ramp(f.empireRate, 0.15, 0.5)),
				ev("Win rate in 30+ minute games", pct(f.lateWinRate), ramp(f.lateWinRate, 0.5, 0.75)),
			],
		},
		"boom": {
			"score": func(f): return (0.5 * ramp(f.villagerRatio, 1.05, 1.35) + 0.5 * ramp(f.gatherRatio, 1.05, 1.4)) * (0.4 + 0.6 * ramp(f.medianFirstHit, 360, 720)),
			"describe": func(f): return "You win the economy first. You peak at %s villagers — %s× your opponents — and gather %s resources a minute, %s× theirs. Your first attack comes around %s: by then you can afford to lose fights and keep coming." % [JS.num(JS.round(f.peakVillagers)), JS.to_fixed(f.villagerRatio, 2), JS.num(JS.round(f.gatherPerMin)), JS.to_fixed(f.gatherRatio, 2), mmss(f.medianFirstHit)],
			"evidence": func(f): return [
				ev("Peak villagers vs opponents", "%s×" % JS.to_fixed(f.villagerRatio, 2), ramp(f.villagerRatio, 1.05, 1.35)),
				ev("Income vs opponents", "%s×" % JS.to_fixed(f.gatherRatio, 2), ramp(f.gatherRatio, 1.05, 1.4)),
				ev("Median first attack", mmss(f.medianFirstHit), ramp(f.medianFirstHit, 360, 720)),
			],
		},
		"demolition": {
			"score": func(f): return 0.6 * ramp(f.razedRatio, 1.2, 2.2) + 0.4 * ramp(f.razedPerGame, 3, 10),
			"title": func(f):
				if f.lateShare >= 0.35 or f.winsAfter40 + f.wins30to40 >= 0.45:
					return "Siege Grinder — longevity & demolition"
				return "Burner — raze and run" if f.winsBefore15 >= 0.4 else "Demolisher",
			"describe": func(f):
				var siege := (", with siege making up %s of your army" % pct(f.classShare.siege)) if f.classShare.siege >= 0.1 else ""
				return "You win by what you break. You raze %s buildings a game — %s× the opponents you've faced%s. You don't need to win every fight: once the production buildings and the Town Center are gone, the fights stop mattering." % [JS.to_fixed(f.razedPerGame, 1), JS.to_fixed(f.razedRatio, 1), siege],
			"evidence": func(f): return [
				ev("Buildings razed vs opponents", "%s×" % JS.to_fixed(f.razedRatio, 1), ramp(f.razedRatio, 1.2, 2.2)),
				ev("Buildings razed per game", JS.to_fixed(f.razedPerGame, 1), ramp(f.razedPerGame, 3, 10)),
				ev("Siege in your army", pct(f.classShare.siege), ramp(f.classShare.siege, 0.05, 0.2)),
			],
		},
		"raid": {
			"score": func(f): return 0.6 * ramp(f.raidRatio, 1.2, 2.5) + 0.4 * ramp(f.classShare.cavalry + f.classShare.archer * 0.5, 0.25, 0.6),
			"describe": func(f): return "You fight the economy, not the army. You kill %s enemy villagers a game — %s× what your opponents manage against you — with an army that's %s cavalry and %s archers. Every villager you catch is income they never get back." % [JS.to_fixed(f.raidPerGame, 1), JS.to_fixed(f.raidRatio, 1), pct(f.classShare.cavalry), pct(f.classShare.archer)],
			"evidence": func(f): return [
				ev("Villager kills vs opponents", "%s×" % JS.to_fixed(f.raidRatio, 1), ramp(f.raidRatio, 1.2, 2.5)),
				ev("Villagers killed per game", JS.to_fixed(f.raidPerGame, 1), ramp(f.raidPerGame, 4, 15)),
				ev("Cavalry in your army", pct(f.classShare.cavalry), ramp(f.classShare.cavalry, 0.2, 0.5)),
			],
		},
		"tech": {
			"score": func(f): return 0.5 * ramp(f.upgradeRatio, 1.1, 1.6) + 0.5 * ((1 - ramp(f.crownAvgSec, 780, 1200)) if f.crownRate > 0.3 else 0.0),
			"title": func(f): return "Fast Crown" if f.crownAvgSec > 0 and f.crownAvgSec <= 840 else "Tech Master",
			"describe": func(f):
				var crown := ("at %s on average" % mmss(f.crownAvgSec)) if f.crownAvgSec else "often"
				return "You win with quality. You research %s technologies a game — %s× your opponents — and reach the Crown Age %s. Every soldier you field is better than the one it meets." % [JS.to_fixed(f.upgradesPerGame, 1), JS.to_fixed(f.upgradeRatio, 2), crown],
			"evidence": func(f): return [
				ev("Research vs opponents", "%s×" % JS.to_fixed(f.upgradeRatio, 2), ramp(f.upgradeRatio, 1.1, 1.6)),
				ev("Average time to the Crown Age", mmss(f.crownAvgSec) if f.crownAvgSec else "—", (1 - ramp(f.crownAvgSec, 780, 1200)) if f.crownAvgSec else 0.0),
				ev("Spent on technology", pct(f.spendTech), ramp(f.spendTech, 0.12, 0.3)),
			],
		},
		"brawler": {
			"score": func(f): return 0.5 * ramp(f.combatRatio, 1.1, 1.7) + 0.5 * ramp(f.kd, 1.0, 1.8),
			"title": func(f): return "Efficient Brawler" if f.kd >= 1.8 else "Brawler",
			"describe": func(f): return "You want the fight, and you win it. There are %s kills-plus-losses a minute in your games — %s× your opponents — and you trade at %s kills per loss (they manage %s). You grind armies down rather than going around them." % [JS.to_fixed(f.combatPerMin, 1), JS.to_fixed(f.combatRatio, 2), JS.to_fixed(f.kd, 2), JS.to_fixed(f.foeKd, 2)],
			"evidence": func(f): return [
				ev("Combat per minute vs opponents", "%s×" % JS.to_fixed(f.combatRatio, 2), ramp(f.combatRatio, 1.1, 1.7)),
				ev("Kills per loss", JS.to_fixed(f.kd, 2), ramp(f.kd, 1.0, 1.8)),
				ev("Opponents' kills per loss", JS.to_fixed(f.foeKd, 2), 1 - ramp(f.foeKd, 0.6, 1.2)),
			],
		},
	}


# ------------------------------------------------------------------ traits --
## Trait: { id, name, detail }.
static func traitsOf(f: Dictionary) -> Array:
	var t: Array = []
	var c: Dictionary = f.classShare
	if c.cavalry >= 0.45:
		t.append({"id": "cav", "name": "Cavalry Commander", "detail": "%s of your army is mounted." % pct(c.cavalry)})
	if c.archer >= 0.45:
		t.append({"id": "archer", "name": "Archer General", "detail": "%s of your army shoots." % pct(c.archer)})
	if c.infantry >= 0.55:
		t.append({"id": "inf", "name": "Shield Wall", "detail": "%s of your army is infantry." % pct(c.infantry)})
	if c.siege >= 0.15:
		t.append({"id": "siege", "name": "Siege Engineer", "detail": "%s of your army is siege." % pct(c.siege)})
	if f.diversity >= 4:
		t.append({"id": "combined", "name": "Combined Arms", "detail": "You field %s unit types' worth of variety." % JS.to_fixed(f.diversity, 1)})
	elif f.topUnit and f.topUnitShare >= 0.6:
		t.append({"id": "onetrick", "name": "One-Trick: %s" % unitName(f.topUnit), "detail": "%s of everything you train is the %s." % [pct(f.topUnitShare), unitName(f.topUnit)]})
	if f.games >= 5 and f.topFactionShare >= 0.7:
		var fac := facName(f.topFaction)
		t.append({"id": "specialist", "name": "%s Specialist" % (fac.substr(4) if fac.begins_with("The ") else fac), "detail": "%s of your games are as %s." % [pct(f.topFactionShare), fac]})
	elif f.factionsPlayed >= 4 and f.topFactionShare <= 0.4:
		t.append({"id": "chameleon", "name": "Chameleon", "detail": "You play %s factions regularly and none dominates." % JS.num(f.factionsPlayed)})
	if f.topOath and f.topOathShare >= 0.75:
		var oath = C.OATHS.get(f.topOath)
		t.append({"id": "oath", "name": "Oathbound: %s" % (oath.short if oath != null else f.topOath), "detail": "You swear the %s in %s of games you swear at all." % [oath.name if oath != null else f.topOath, pct(f.topOathShare)]})
	if f.games >= 6 and f.topMapShare >= 0.4 and f.topMapWinRate >= f.winRate + 0.1:
		t.append({"id": "map", "name": "At home on %s" % f.topMap, "detail": "%s wins there, against %s overall." % [pct(f.topMapWinRate), pct(f.winRate)]})
	if f.kd >= 1.8:
		t.append({"id": "trader", "name": "Efficient Trader", "detail": "%s kills for every unit lost." % JS.to_fixed(f.kd, 2)})
	if f.lateShare >= 0.2 and f.lateWinRate >= 0.65:
		t.append({"id": "closer", "name": "Closer", "detail": "You win %s of games that go past 35 minutes." % pct(f.lateWinRate)})
	if f.earlyLossShare >= 0.5 and f.games - f.wins >= 3:
		t.append({"id": "vulnerable", "name": "Rush-vulnerable", "detail": "%s of your losses come inside 15 minutes." % pct(f.earlyLossShare)})
	if f.spendBuildings >= 0.45:
		t.append({"id": "builder", "name": "Master Builder", "detail": "%s of your spending goes on buildings." % pct(f.spendBuildings)})
	return t


# ---------------------------------------------------------------- the read --
## StyleScore: { archetype, score }.
## WinKey: { label, win, loss, change, sentence }.
##
## PlaystyleProfile: { enough, games, confidence ("none" | "low" | "medium" | "high"),
##   primary: StyleScore or null,
##   title: the primary's specific name ("Man-at-Arms Rush", "Siege Grinder"),
##   description, secondary: StyleScore or null, scores, traits, features,
##   matchMix: how your matches go, game by game — [{ style, share, wins, games }],
##   winKeys: what's different about the games you win,
##   byFaction: per faction, the style you most often play it with —
##     [{ faction, games, style, share, winRate }],
##   keywords: short words that sum up how you play, built from your numbers,
##   good, bad: what you do well, and what's costing you games }
static func analyse(ms: Array) -> Dictionary:
	var f := features(ms)
	var scores: Array = ARCHETYPES.map(func(a): return {"archetype": a, "score": float(a.score.call(f)) if f.games else 0.0})
	JS.sort_by_cmp(scores, func(a, b): return b.score - a.score)
	var enough: bool = f.games >= MIN_GAMES
	var top = _at(scores, 0)
	var primary = top if enough and top != null and top.score >= 0.35 else null
	var second = scores[1] if primary != null and scores.size() > 1 and scores[1].score >= 0.35 else null
	var title: String
	if primary != null:
		var tf = primary.archetype.title
		title = tf.call(f) if tf != null else primary.archetype.name
	else:
		title = "All-Rounder" if enough else "Still finding out"
	# Two strong styles together have their own names.
	if primary != null and second != null:
		var ids := [primary.archetype.id, second.archetype.id]
		ids.sort()
		var pair := "+".join(PackedStringArray(ids))
		var combo := {
			"raid+rush": "Raiding Rush", "boom+turtle": "Fortress Boom", "demolition+late": "Siege Grinder — longevity & demolition",
			"demolition+turtle": "Siege Grinder — longevity & demolition", "brawler+rush": "Relentless Aggressor", "boom+tech": "Economic Powerhouse",
			"late+turtle": "Turtle — plays for the late game", "boom+late": "Late-Game Boomer", "raid+tech": "Tech Raider",
		}
		if combo.has(pair):
			title = combo[pair]
	var description: String
	if primary != null:
		description = primary.archetype.describe.call(f)
		if second != null:
			var sh: String = second.archetype.short
			description += " There's a strong %s streak too: %s%s" % [second.archetype.name.to_lower(), sh.substr(0, 1).to_lower(), sh.substr(1)]
	elif enough:
		description = "No single approach dominates your %s games — you adapt: rushing, booming and defending as the game asks. Your wins average %s." % [JS.num(f.games), mmss(f.avgWinSec)]
	else:
		var left: int = MIN_GAMES - int(f.games)
		description = "Play %d more game%s and your style will show up here." % [left, "" if left == 1 else "s"]

	# Match-by-match mix.
	var mix := {}
	for m in ms:
		var s := matchStyle(m)
		var x: Dictionary = mix.get(s, {"games": 0, "wins": 0})
		x.games += 1
		if m.won:
			x.wins += 1
		mix[s] = x
	var matchMix: Array = []
	for style in mix:
		var x: Dictionary = mix[style]
		matchMix.append({"style": style, "share": float(x.games) / maxi(1, ms.size()), "wins": x.wins, "games": x.games})
	JS.sort_by_cmp(matchMix, func(a, b): return b.games - a.games)

	# What your wins have that your losses don't.
	var wins := ms.filter(func(m): return m.won)
	var losses := ms.filter(func(m): return not m.won)
	var winKeys: Array = []
	if wins.size() >= 3 and losses.size() >= 3:
		var round_str := func(x): return JS.num(JS.round(x))
		var fixed1 := func(x): return JS.to_fixed(x, 1)
		var cand := [
			{"label": "Peak villagers", "f": func(m): return m.peakVillagers, "fmt": round_str, "higher": "more villagers at your peak", "lower": "fewer villagers at your peak"},
			{"label": "Income per minute", "f": func(m): return gatherPerMin(m), "fmt": round_str, "higher": "more income a minute", "lower": "less income a minute"},
			{"label": "Peak army", "f": func(m): return m.peakArmy, "fmt": round_str, "higher": "a bigger army at its peak", "lower": "a smaller army at its peak"},
			{"label": "Technologies", "f": func(m): return m.upgrades, "fmt": fixed1, "higher": "more research", "lower": "less research"},
			{"label": "First attack", "f": func(m): return attackAt(m) if attackAt(m) >= 0 else null, "fmt": func(x): return mmss(x), "higher": "a later first attack", "lower": "an earlier first attack"},
			{"label": "Crown Age at", "f": func(m):
				var c = _at(m.ageTimes, 2)
				return c if (c is float or c is int) and c > 0 else null, "fmt": func(x): return mmss(x), "higher": "a later Crown Age", "lower": "an earlier Crown Age"},
			{"label": "Buildings razed", "f": func(m): return m.razed, "fmt": fixed1, "higher": "more buildings razed", "lower": "fewer buildings razed"},
			{"label": "Defence built", "f": func(m): return Career.defensesBuilt(m.built), "fmt": fixed1, "higher": "more defences built", "lower": "fewer defences built"},
			{"label": "Town Center idle", "f": func(m): return m.tcIdleShare, "fmt": func(x): return pct(x), "higher": "your Town Center idle more", "lower": "your Town Center idle less"},
		]
		for c in cand:
			var w := wins.map(c.f).filter(func(x): return x != null)
			var l := losses.map(c.f).filter(func(x): return x != null)
			if w.size() < 3 or l.size() < 3:
				continue
			var wm := mean(w)
			var lm := mean(l)
			var change := (wm - lm) / absf(lm) if lm != 0 else (1.0 if wm > 0 else 0.0)
			if absf(change) < 0.1:
				continue
			var amount := "" if c.label == "Town Center idle" else "%s%% " % JS.num(JS.round(absf(change) * 100.0))
			winKeys.append({"label": c.label, "win": c.fmt.call(wm), "loss": c.fmt.call(lm), "change": change,
				"sentence": "In your wins you have %s%s (%s vs %s)." % [amount, c.higher if change > 0 else c.lower, c.fmt.call(wm), c.fmt.call(lm)]})
		JS.sort_by_cmp(winKeys, func(a, b): return absf(b.change) - absf(a.change))

	# Style by faction.
	var byFaction: Array = []
	var facs := {}
	for m in ms:
		if _truthy(m.faction):
			facs[m.faction] = true
	for fac in facs:
		var games := ms.filter(func(m): return m.faction == fac)
		if games.size() < 3:
			continue
		var c := {}
		for m in games:
			c[matchStyle(m)] = int(c.get(matchStyle(m), 0)) + 1
		var best: Array = _by_value_desc(_entries(c))[0]
		byFaction.append({"faction": fac, "games": games.size(), "style": best[0], "share": float(best[1]) / games.size(), "winRate": float(games.filter(func(m): return m.won).size()) / games.size()})
	JS.sort_by_cmp(byFaction, func(a, b): return b.games - a.games)

	var fb := careerFeedback(f) if enough else {"good": [], "bad": []}
	return {
		"enough": enough, "games": f.games,
		"confidence": "none" if f.games < MIN_GAMES else ("low" if f.games < 10 else ("medium" if f.games < 25 else "high")),
		"primary": primary, "title": title, "description": description, "secondary": second, "scores": scores,
		"traits": traitsOf(f) if enough else [], "features": f,
		"matchMix": matchMix, "winKeys": winKeys.slice(0, 4), "byFaction": byFaction,
		"keywords": keywordsOf(f) if enough else [], "good": fb.good, "bad": fb.bad,
	}


# ---------------------------------------------------------------- keywords --
## The words the style is built from. Tempo (when you attack), make-up (what
## with), length (when your games end) and the things you stand out at — so
## "Early attacker · Archer · Long games" is possible, not only the stock titles.
static func keywordsOf(f: Dictionary) -> Array:
	var k: Array = []
	if f.rushRate >= 0.5:
		k.append("Early attacker")
	elif f.medianFirstHit >= 900:
		k.append("Slow starter")
	else:
		k.append("Mid-game attacker")
	if f.openerWord and f.rushRate >= 0.25:
		k.append("%s opener" % f.openerWord)
	var top = _at(_by_value_desc(_entries(f.classShare)), 0)
	if top != null and top[1] >= 0.45:
		k.append("%s army" % CLASS_WORD[top[0]])
	elif f.diversity >= 3.5:
		k.append("Combined arms")
	if f.winsBefore15 >= 0.5:
		k.append("Short games")
	elif f.lateWins >= 0.5 or f.lateShare >= 0.4:
		k.append("Long games")
	if f.razedRatio >= 1.4 and f.razedPerGame >= 3:
		k.append("Demolition")
	if f.raidRatio >= 1.4 and f.raidPerGame >= 4:
		k.append("Villager hunter")
	if f.defenseRatio >= 1.4 and f.defensesPerGame >= 3:
		k.append("Fortifier")
	if f.gatherRatio >= 1.15:
		k.append("Strong economy")
	if f.upgradeRatio >= 1.3:
		k.append("Tech-heavy")
	if f.kd >= 1.5:
		k.append("Efficient fighter")
	return k


# ---------------------------------------------------------------- feedback --
## What the record says you do well, and what's losing you games. { good, bad }
static func careerFeedback(f: Dictionary) -> Dictionary:
	var good: Array = []
	var bad: Array = []
	if f.gatherRatio >= 1.1:
		good.append("Your economy out-gathers your opponents by %s." % pct(f.gatherRatio - 1))
	elif f.gatherRatio <= 0.9:
		bad.append("You gather %s less than your opponents — more villagers, fewer idle ones." % pct(1 - f.gatherRatio))
	if f.tcIdle <= 0.12:
		good.append("Your Town Center is almost never idle (%s)." % pct(f.tcIdle))
	elif f.tcIdle >= 0.3:
		bad.append("Your Town Center sits idle %s of the time — keep villagers queued." % pct(f.tcIdle))
	if f.kd >= 1.3:
		good.append("You trade well: %s kills per loss." % JS.to_fixed(f.kd, 2))
	elif f.kd <= 0.8 and f.games >= 5:
		bad.append("You lose more units than you kill (%s per loss) — fight near your towers or with the counter unit." % JS.to_fixed(f.kd, 2))
	if f.rushedGames >= 3:
		if f.heldRushRate >= 0.6:
			good.append("You hold early attacks: %s wins when rushed." % pct(f.heldRushRate))
		elif f.heldRushRate <= 0.35:
			bad.append("Early attacks beat you — %s wins in %s games where you were rushed." % [pct(f.heldRushRate), JS.num(f.rushedGames)])
	if f.earlyLossShare >= 0.5 and f.games - f.wins >= 3:
		bad.append("%s of your losses are over inside 15 minutes." % pct(f.earlyLossShare))
	if f.lateShare >= 0.2 and f.lateWinRate >= 0.6:
		good.append("You close long games: %s wins past 30 minutes." % pct(f.lateWinRate))
	elif f.lateShare >= 0.2 and f.lateWinRate <= 0.35:
		bad.append("Long games slip away — %s wins past 30 minutes." % pct(f.lateWinRate))
	if f.upgradeRatio >= 1.25:
		good.append("You out-research your opponents (%s×)." % JS.to_fixed(f.upgradeRatio, 1))
	elif f.upgradeRatio <= 0.75:
		bad.append("You research less than your opponents (%s×) — blacksmith upgrades win even fights." % JS.to_fixed(f.upgradeRatio, 1))
	if f.crownRate >= 0.3 and f.crownAvgSec > 0 and f.crownAvgSec >= 1260:
		bad.append("You reach the Crown Age late (%s on average)." % mmss(f.crownAvgSec))
	return {"good": good.slice(0, 4), "bad": bad.slice(0, 4)}
