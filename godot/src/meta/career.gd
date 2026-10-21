## Career: every match you play, offline or online, summed up for good.
##
## The match history (history.ts) keeps the last twenty games for trends. This
## is the other half — the numbers a player looks up about themselves: how often
## each faction wins for them, how fast, on which maps, which unit they lean on,
## which enemy faction gives them trouble.
##
## Two stores, both in this browser:
##   • a *log* of the last LOG_LIMIT matches, each a compact CareerMatch — what
##     the filtered views (skirmish only, online only, ranked only) and the
##     recent-matches list are built from;
##   • lifetime *totals*, updated match by match and never trimmed, so "all
##     time" stays exact however long someone plays.
## Both are the same shape of numbers (a Career), so every screen reads one
## thing: `fold` builds it from a list, `absorb` adds one match to it.
##
## Everything here is a Dictionary with the TS field names, and what is stored
## is the JSON the TS build stores.
##
## CareerMatch — one match, from your side of it:
##   at, kind ("skirmish" | "online"), ranked, mode,
##   difficulty: AI difficulty for skirmish; "" online,
##   won, faction, commander, oaths, map, players,
##   format: "1 v 1", "2 v 2", "4-player FFA"…,
##   allies, foes, durationSec,
##   defeatedAt: when you were knocked out, or -1,
##   age, ageTimes: seconds each age was reached (index 0 = start),
##   score, gathered, gatheredBy {food, wood, gold}, spent, kills, losses, razed,
##   buildingsLost, damageDealt, damageTaken, peakArmy, peakVillagers,
##   idleVillagerTime, tcIdleShare: share of the match your Town Centres sat idle (0–1),
##   upgrades, trained, killed, lost, built,
##   unitKills?, unitDamage?, unitRazed?: by your unit type — enemies it killed,
##     damage it dealt, buildings it razed. Absent on older records.
##   firstHitAt?, firstKillAt?, firstRazeAt?: seconds to your side's first hit on
##     an enemy, first kill, first razed building (-1 never). Absent on older records.
##   firstAttackAt?, firstDefendAt?, damageAttacking?, damageDefending?,
##   killsAttacking?, killsDefending?: attacking vs defending, by where the blow
##     landed: on the enemy's ground, on yours, or in between. firstAttackAt is
##     what "rushed" means. Absent on older records.
##   opener?: damage by unit type away from home in the first 10 minutes — what
##     you opened with.
##   spentOn?: { units, buildings, tech } — what you spent it on.
##   foe?: the average enemy realm in this match — what "more than usual" is
##     measured against: the people you actually played, not a made-up norm.
##     { gathered, razed, kills, losses, peakArmy, peakVillagers, villagerKills,
##       upgrades, defenses, age, firstAttackAt? }
##
## UnitTally — one unit type across a career: trained, lost (your units of this
## type that died), killed (enemy units of this type you killed, whatever did
## it), kills (enemy units this type killed), damage, razed, games (matches you
## trained at least one), wins.
##
## Tally — played / won and the numbers that go with them, for any slice:
## played, won, secs, winSecs, fastestWin (seconds, 0 = no win yet), longest,
## kills, losses, gathered, units (units trained with this slice, for
## "favourite unit with the Legion").
##
## Career: since, last, all, byKind, byFaction, byMap, byMode, byDifficulty,
## byCommander, byOath, byFormat, vsFaction (your record against each enemy
## faction, per match it appeared in), withFaction (with each ally faction beside
## you), units, buildings, resources {food, wood, gold}, damageDealt,
## damageTaken, razed, buildingsLost, upgrades, bestArmy, bestScore,
## ageReached / ageTimeSum (how many matches reached each age, and the summed
## time it took), idleVillagerTime, tcIdleShareSum, streak, bestStreak,
## worstStreak, losingRun.
class_name Career

const LOG_KEY := "bb_career_log_v1"
const TOTALS_KEY := "bb_career_totals_v1"
## (Also exported as C.LOG_LIMIT; a test keeps the two equal.)
const LOG_LIMIT := 400

## Tests put a store here whose setItem can fail, as a full localStorage does;
## null means the Storage autoload. A store's setItem returning `false` is a
## failed write (the TS setItem throws).
static var storage_override: Object = null

## How much defence a realm built, in "tower equivalents": a wall run is many
## cheap segments, so segments count a quarter each; a castle counts four.
static var DEFENSE_WEIGHT: Dictionary:
	get:
		return C.DEFENSE_WEIGHT


static func defensesBuilt(built: Dictionary) -> float:
	var n := 0.0
	for b in C.DEFENSE_WEIGHT:
		n += float(built.get(b, 0.0)) * C.DEFENSE_WEIGHT[b]
	return JS.round(n * 10.0) / 10.0


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


static func tally() -> Dictionary:
	return {"played": 0.0, "won": 0.0, "secs": 0.0, "winSecs": 0.0, "fastestWin": 0.0, "longest": 0.0, "kills": 0.0, "losses": 0.0, "gathered": 0.0, "units": {}}


static func emptyCareer() -> Dictionary:
	return {
		"since": 0.0, "last": 0.0, "all": tally(), "byKind": {}, "byFaction": {}, "byMap": {}, "byMode": {}, "byDifficulty": {}, "byCommander": {},
		"byOath": {}, "byFormat": {}, "vsFaction": {}, "withFaction": {}, "units": {}, "buildings": {}, "resources": {"food": 0.0, "wood": 0.0, "gold": 0.0},
		"damageDealt": 0.0, "damageTaken": 0.0, "razed": 0.0, "buildingsLost": 0.0, "upgrades": 0.0, "bestArmy": 0.0, "bestScore": 0.0,
		"ageReached": [0.0, 0.0, 0.0, 0.0], "ageTimeSum": [0.0, 0.0, 0.0, 0.0], "idleVillagerTime": 0.0, "tcIdleShareSum": 0.0,
		"streak": 0.0, "bestStreak": 0.0, "worstStreak": 0.0, "losingRun": 0.0,
	}


static func addTo(t: Dictionary, m: Dictionary) -> void:
	t.played += 1
	t.secs += m.durationSec
	t.kills += m.kills
	t.losses += m.losses
	t.gathered += m.gathered
	t.longest = maxf(t.longest, m.durationSec)
	if m.won:
		t.won += 1
		t.winSecs += m.durationSec
		t.fastestWin = minf(t.fastestWin, m.durationSec) if _truthy(t.fastestWin) else float(m.durationSec)
	for u in m.trained:
		if u != "villager":
			t.units[u] = float(t.units.get(u, 0.0)) + m.trained[u]


static func slot(rec: Dictionary, key: String) -> Dictionary:
	if rec.get(key) == null:
		rec[key] = tally()
	return rec[key]


static func _unique(arr) -> Array:
	var seen := {}
	if arr is Array:
		for x in arr:
			seen[x] = true
	return seen.keys()


static func _unit(c: Dictionary, u: String) -> Dictionary:
	if c.units.get(u) == null:
		c.units[u] = {"trained": 0.0, "killed": 0.0, "lost": 0.0, "kills": 0.0, "damage": 0.0, "razed": 0.0, "games": 0.0, "wins": 0.0}
	var t: Dictionary = c.units[u]
	# Older saved totals predate the per-unit fields.
	for k in ["kills", "damage", "razed", "games", "wins"]:
		if t.get(k) == null:
			t[k] = 0.0
	return t


## Add one match to a career.
static func absorb(c: Dictionary, m: Dictionary) -> Dictionary:
	c.since = minf(c.since, m.at) if _truthy(c.since) else float(m.at)
	c.last = maxf(c.last, m.at)
	addTo(c.all, m)
	addTo(slot(c.byKind, m.kind), m)
	if m.ranked:
		addTo(slot(c.byKind, "ranked"), m)
	if _truthy(m.faction):
		addTo(slot(c.byFaction, m.faction), m)
	if _truthy(m.map):
		addTo(slot(c.byMap, m.map), m)
	if _truthy(m.mode):
		addTo(slot(c.byMode, m.mode), m)
	if _truthy(m.difficulty):
		addTo(slot(c.byDifficulty, m.difficulty), m)
	if _truthy(m.commander):
		addTo(slot(c.byCommander, m.commander), m)
	if _truthy(m.format):
		addTo(slot(c.byFormat, m.format), m)
	for o in _unique(m.oaths):
		addTo(slot(c.byOath, o), m)
	for f in _unique(m.foes):
		addTo(slot(c.vsFaction, f), m)
	for f in _unique(m.allies):
		addTo(slot(c.withFaction, f), m)
	for u in m.trained:
		var n: float = m.trained[u]
		var t := _unit(c, u)
		t.trained += n
		if n > 0:
			t.games += 1
			if m.won:
				t.wins += 1
	for u in m.killed:
		_unit(c, u).killed += m.killed[u]
	for u in m.lost:
		_unit(c, u).lost += m.lost[u]
	var uk = m.get("unitKills")
	if uk != null:
		for u in uk:
			_unit(c, u).kills += uk[u]
	var ud = m.get("unitDamage")
	if ud != null:
		for u in ud:
			_unit(c, u).damage += ud[u]
	var ur = m.get("unitRazed")
	if ur != null:
		for u in ur:
			_unit(c, u).razed += ur[u]
	for b in m.built:
		c.buildings[b] = float(c.buildings.get(b, 0.0)) + m.built[b]
	c.resources.food += m.gatheredBy.food
	c.resources.wood += m.gatheredBy.wood
	c.resources.gold += m.gatheredBy.gold
	c.damageDealt += m.damageDealt
	c.damageTaken += m.damageTaken
	c.razed += m.razed
	c.buildingsLost += m.buildingsLost
	c.upgrades += m.upgrades
	c.bestArmy = maxf(c.bestArmy, m.peakArmy)
	c.bestScore = maxf(c.bestScore, m.score)
	var a := 1
	while a < m.ageTimes.size() and a < c.ageReached.size():
		if m.ageTimes[a] != null:
			c.ageReached[a] += 1
			c.ageTimeSum[a] += m.ageTimes[a]
		a += 1
	c.ageReached[0] += 1
	c.idleVillagerTime += m.idleVillagerTime
	c.tcIdleShareSum += m.tcIdleShare
	# Streaks run in the order matches are absorbed (oldest first).
	if m.won:
		c.streak = c.streak + 1 if c.streak > 0 else 1.0
		c.losingRun = 0.0
	else:
		c.streak = c.streak - 1 if c.streak < 0 else -1.0
		c.losingRun += 1
	c.bestStreak = maxf(c.bestStreak, c.streak)
	c.worstStreak = maxf(c.worstStreak, -minf(0.0, c.streak))
	return c


## A career from a list of matches (any order).
static func fold(ms: Array) -> Dictionary:
	var c := emptyCareer()
	var sorted := ms.duplicate()
	JS.sort_by_cmp(sorted, func(a, b): return float(a.at) - float(b.at))
	for m in sorted:
		absorb(c, m)
	return c


# ------------------------------------------------------------ reading it --

static func winRate(t) -> float:
	return float(t.won) / t.played if t != null and _truthy(t.played) else 0.0


static func avgSecs(t) -> float:
	return float(t.secs) / t.played if t != null and _truthy(t.played) else 0.0


static func avgWinSecs(t) -> float:
	return float(t.winSecs) / t.won if t != null and _truthy(t.won) else 0.0


static func kd(t) -> float:
	if t == null:
		return 0.0
	return float(t.kills) / t.losses if _truthy(t.losses) else float(t.kills)


## The key played most (ties: more wins, then alphabetical), or "".
static func favourite(rec: Dictionary) -> String:
	var best := ""
	var bp := -1.0
	var bw := -1.0
	for k in rec:
		var t: Dictionary = rec[k]
		if t.played > bp or (t.played == bp and t.won > bw):
			best = k
			bp = t.played
			bw = t.won
	return best


## The unit trained most in a tally (villagers excluded), or "".
static func favouriteUnitOf(t) -> String:
	var best := ""
	var n := 0.0
	var units: Dictionary = t.units if t != null and t.get("units") != null else {}
	for u in units:
		if units[u] > n:
			best = u
			n = units[u]
	return best


## The unit with the most of `field` across the career.
## field: "trained" | "killed" | "lost" | "kills" | "damage" | "razed"
static func topUnit(c: Dictionary, field: String, skipVillager := true) -> String:
	var best := ""
	var n := 0.0
	for u in c.units:
		if skipVillager and u == "villager":
			continue
		var v: Dictionary = c.units[u]
		var x := float(JS.nz(v.get(field), 0.0))
		if x > n:
			best = u
			n = x
	return best


## Kills per unit lost for a unit type (kills if it never died).
static func unitKd(v) -> float:
	if v == null:
		return 0.0
	var kills := float(JS.nz(v.get("kills"), 0.0))
	return kills / v.lost if _truthy(v.lost) else kills


## The unit that trades best: highest K/D among the ones trained enough to
## count (villagers excluded — they're not meant to fight).
static func mostEffectiveUnit(c: Dictionary, minTrained := 10.0) -> String:
	var best := ""
	var r := -1.0
	for u in c.units:
		var v: Dictionary = c.units[u]
		if u == "villager" or v.trained < minTrained or not _truthy(JS.nz(v.get("kills"), 0.0)):
			continue
		var k := unitKd(v)
		if k > r:
			best = u
			r = k
	return best


## Best win rate among slices with at least `min` games (a "best faction"/"best map").
static func bestBy(rec: Dictionary, min_ := 3.0) -> String:
	var best := ""
	var r := -1.0
	for k in rec:
		var t: Dictionary = rec[k]
		if t.played >= min_ and winRate(t) > r:
			best = k
			r = winRate(t)
	return best


static func worstBy(rec: Dictionary, min_ := 3.0) -> String:
	var worst := ""
	var r := 2.0
	for k in rec:
		var t: Dictionary = rec[k]
		if t.played >= min_ and winRate(t) < r:
			worst = k
			r = winRate(t)
	return worst


# ---------------------------------------------------------- making one --

static func formatOf(players: Array) -> String:
	var groups := {}
	for p in players:
		if not _truthy(p.get("horde")):
			var g := float(p.group)
			groups[g] = int(groups.get(g, 0)) + 1
	var sizes: Array = groups.values()
	JS.sort_by_cmp(sizes, func(a, b): return b - a)
	if sizes.is_empty():
		return ""
	if sizes.all(func(n): return n == 1):
		return "1 v 1" if sizes.size() == 2 else "%d-player FFA" % sizes.size()
	return " v ".join(PackedStringArray(sizes.map(func(n): return str(n))))


## The average enemy realm, from their per-realm reports (null when there are none).
static func foeAverage(foes: Array):
	if foes.is_empty():
		return null
	var avg := func(f: Callable) -> float:
		var s := 0.0
		for p in foes:
			s += float(f.call(p))
		return JS.round((s / foes.size()) * 10.0) / 10.0
	# Earliest enemy attack on you (-1 = none): the rush you had to hold.
	var attacks: Array = foes.map(func(p): return float(JS.nz(p.get("firstAttackAt"), -1.0))).filter(func(t): return t >= 0)
	JS.sort_by_cmp(attacks, func(a, b): return a - b)
	return {
		"gathered": avg.call(func(p): return p.gathered), "razed": avg.call(func(p): return p.buildingsRazed),
		"kills": avg.call(func(p): return p.unitsKilled), "losses": avg.call(func(p): return p.unitsLost),
		"peakArmy": avg.call(func(p): return p.peakArmy), "peakVillagers": avg.call(func(p): return p.peakVillagers),
		"villagerKills": avg.call(func(p): return JS.nz(p.killedByType.get("villager"), 0.0)),
		"upgrades": avg.call(func(p): return p.upgrades), "defenses": avg.call(func(p): return defensesBuilt(p.builtByType)),
		"age": avg.call(func(p): return p.age),
		"firstAttackAt": JS.round(attacks[0] if attacks.size() > 0 else -1.0),
	}


static func _rounded(rec) -> Dictionary:
	var out := {}
	if rec is Dictionary:
		for k in rec:
			out[k] = JS.round(rec[k])
	return out


## Your side of a finished match, from its end-of-match report.
## meta: { at, won, kind, ranked?, mode, difficulty, commander }
static func careerMatch(report: Dictionary, meta: Dictionary) -> Dictionary:
	var players: Array = JS.nz(report.get("players"), [])
	var me = null
	for p in players:
		if p.relation == "you":
			me = p
			break
	var side: Dictionary = me if me != null else report.you
	var trained: Dictionary = side.trainedByType.duplicate()
	var online: bool = meta.kind == "online"
	var notHorde := players.filter(func(p): return not _truthy(p.get("horde")))
	var ageTimes: Array = JS.nz(me.get("ageTimes") if me != null else null, [0.0])
	var cm := {
		"at": meta.at,
		"kind": meta.kind,
		"ranked": _truthy(meta.get("ranked")),
		"mode": meta.mode,
		"difficulty": "" if online else meta.difficulty,
		"won": meta.won,
		"faction": JS.nz(me.get("faction") if me != null else null, ""),
		"commander": "" if online else meta.commander,
		"oaths": (JS.nz(me.get("oaths") if me != null else null, []) as Array).duplicate(),
		"map": report.mapName,
		"players": notHorde.size() if notHorde.size() > 0 else 2,
		"format": formatOf(players),
		"allies": players.filter(func(p): return p.relation == "ally" and _truthy(p.get("faction"))).map(func(p): return p.faction),
		"foes": players.filter(func(p): return p.relation == "enemy" and not _truthy(p.get("horde")) and _truthy(p.get("faction"))).map(func(p): return p.faction),
		"durationSec": JS.round(report.durationSec),
		"defeatedAt": JS.nz(me.get("defeatedAt") if me != null else null, -1.0),
		"age": side.age,
		"ageTimes": ageTimes.map(func(t): return JS.round(float(t)) if t != null else 0.0),
		"score": JS.round(side.score),
		"gathered": JS.round(side.gathered),
		"gatheredBy": {"food": JS.round(side.gatheredBy.food), "wood": JS.round(side.gatheredBy.wood), "gold": JS.round(side.gatheredBy.gold)},
		"spent": JS.round(side.spent),
		"kills": side.unitsKilled,
		"losses": side.unitsLost,
		"razed": side.buildingsRazed,
		"buildingsLost": side.buildingsLost,
		"damageDealt": JS.round(side.damageDealt),
		"damageTaken": JS.round(side.damageTaken),
		"peakArmy": side.peakArmy,
		"peakVillagers": side.peakVillagers,
		"idleVillagerTime": JS.round(side.idleVillagerTime),
		"tcIdleShare": minf(1.0, float(side.idleTcTime) / side.tcSeconds) if side.tcSeconds > 0 else 0.0,
		"upgrades": side.upgrades,
		"trained": trained,
		"killed": side.killedByType.duplicate(),
		"lost": side.lostByType.duplicate(),
		"built": side.builtByType.duplicate(),
		"unitKills": (JS.nz(side.get("killsByUnit"), {}) as Dictionary).duplicate(),
		"unitDamage": _rounded(side.get("damageByUnit")),
		"unitRazed": (JS.nz(side.get("razedByUnit"), {}) as Dictionary).duplicate(),
		"firstHitAt": JS.round(JS.nz(side.get("firstHitAt"), -1.0)),
		"firstKillAt": JS.round(JS.nz(side.get("firstKillAt"), -1.0)),
		"firstRazeAt": JS.round(JS.nz(side.get("firstRazeAt"), -1.0)),
		"firstAttackAt": JS.round(JS.nz(side.get("firstAttackAt"), -1.0)),
		"firstDefendAt": JS.round(JS.nz(side.get("firstDefendAt"), -1.0)),
		"damageAttacking": JS.round(JS.nz(side.get("damageAttacking"), 0.0)),
		"damageDefending": JS.round(JS.nz(side.get("damageDefending"), 0.0)),
		"killsAttacking": JS.nz(side.get("killsAttacking"), 0.0),
		"killsDefending": JS.nz(side.get("killsDefending"), 0.0),
		"opener": _rounded(side.get("openerByUnit")),
		"spentOn": {"units": JS.round(side.spentOn.units), "buildings": JS.round(side.spentOn.buildings), "tech": JS.round(side.spentOn.tech)},
	}
	# `foe: undefined` leaves the field out of the record (and its JSON).
	var foe = foeAverage(players.filter(func(p): return p.relation == "enemy" and not _truthy(p.get("horde"))))
	if foe != null:
		cm["foe"] = foe
	return cm


# ---------------------------------------------------------------- storage --

static func store() -> Object:
	return storage_override if storage_override != null else Storage


static func _write(s: Object, key: String, value: String) -> bool:
	var r = s.setItem(key, value)
	return not (r is bool and r == false)


## The recent matches, newest first.
static func careerLog() -> Array:
	var s := store()
	var raw = s.getItem(LOG_KEY) if s != null else null
	var arr = LocalJSON.parse(raw) if raw else []
	if not (arr is Array):
		return []
	var ms: Array = arr.filter(func(m): return m is Dictionary and (m.get("at") is float or m.get("at") is int))
	JS.sort_by_cmp(ms, func(a, b): return float(b.at) - float(a.at))
	return ms


## Lifetime totals (exact, never trimmed).
static func careerTotals() -> Dictionary:
	var s := store()
	var raw = s.getItem(TOTALS_KEY) if s != null else null
	if not raw:
		return fold(careerLog())
	var saved = LocalJSON.parse(raw)
	if saved == null:
		return fold(careerLog())
	var c := emptyCareer()
	if saved is Dictionary:
		for k in saved:
			c[k] = saved[k]
	return c


## Keep a finished match: on the log, and in the lifetime totals.
static func recordCareer(m: Dictionary) -> void:
	var s := store()
	if s == null:
		return
	var totals := absorb(careerTotals(), m)
	var log: Array = [m]
	log.append_array(careerLog())
	log = log.slice(0, LOG_LIMIT)
	_write(s, TOTALS_KEY, LocalJSON.stringify(totals))  # storage full — totals are small; nothing else to drop
	var keep := log.size()
	while keep > 0:
		if _write(s, LOG_KEY, LocalJSON.stringify(log.slice(0, keep))):
			return
		keep = int(floor(keep * 0.7))  # full: keep fewer


static func clearCareer() -> void:
	var s := store()
	if s != null:
		s.removeItem(LOG_KEY)
		s.removeItem(TOTALS_KEY)


## The career for a filter: lifetime totals for "all", else folded from the log.
## filter: "all" | "skirmish" | "online" | "ranked". Returns { career, matches, exact }.
static func careerFor(filter: String) -> Dictionary:
	var log := careerLog()
	if filter == "all":
		return {"career": careerTotals(), "matches": log, "exact": true}
	var ms := log.filter(func(m): return _truthy(m.ranked) if filter == "ranked" else m.kind == filter)
	return {"career": fold(ms), "matches": ms, "exact": log.size() < LOG_LIMIT}
