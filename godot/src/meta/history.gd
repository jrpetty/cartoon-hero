## Match history: the last twenty games you played, kept.
##
## The deep end-of-match report already exists and is thrown away the moment you
## press Continue. That is a shame twice over — you can't compare a game against
## the one before it, and you can't see whether the thing you have been working
## on is actually working. A record over time says more than any single screen.
##
## What is stored is a *summary*, not the full report: twenty complete
## MatchReports with their per-unit-type maps would be a few hundred kilobytes of
## localStorage, and nothing on the history screen needs that resolution. The
## fields here are the ones a trend is made of.
##
## MatchRecord (a Dictionary, stored as the TS build stores it):
##   at: epoch millis — the only clock the history screen has,
##   won, mapName, mode, difficulty, players, durationSec,
##   score, foeScore: final score, yours and theirs,
##   gathered, spent, unitsKilled, unitsLost, buildingsRazed, buildingsLost,
##   peakArmy, peakVillagers, age,
##   idleVillagerTime, idleTcTime, tcSeconds: villager-seconds idle, and how bad
##     that was as a share of the match,
##   favouriteUnit: the unit you trained most of, which is the shortest honest
##     read of a style,
##   report?: the whole end-of-match report, kept for the most recent few games
##     only. The summary above is what a *trend* needs; this is what re-reading
##     one game needs, and the two are different sizes. A full report carries
##     four per-type maps per side, so twenty of them is a few hundred kilobytes
##     of localStorage for something nobody scrolls back to. Three is enough to
##     answer "what went wrong in that one" while it is still the question you
##     are asking.
class_name History

const STORAGE_KEY := "banner_and_blade_history_v1"

## How many games we keep. Twenty is roughly an evening, and bounds the store.
## (Also exported as C.HISTORY_LIMIT; a test keeps the two equal.)
const HISTORY_LIMIT := 20

## How many of the stored games keep their full report. (C.FULL_REPORT_KEEP.)
const FULL_REPORT_KEEP := 3

## Tests put a store here whose setItem can fail, as a full localStorage does;
## null means the Storage autoload. A store's setItem returning `false` is a
## failed write (the TS setItem throws).
static var storage_override: Object = null


## Boil a full report down to what a trend actually needs.
## meta: { won, mode, difficulty, players, at }
static func summarise(report: Dictionary, meta: Dictionary) -> Dictionary:
	var you: Dictionary = report.you
	var favouriteUnit := ""
	var most := 0.0
	for type in you.trainedByType:
		# Villagers are trained most in almost every game, and "you built villagers"
		# is not a read on anyone's style.
		if type == "villager":
			continue
		var n := float(you.trainedByType[type])
		if n > most:
			most = n
			favouriteUnit = type
	return {
		"report": report,
		"at": meta.at,
		"won": meta.won,
		"mapName": report.mapName,
		"mode": meta.mode,
		"difficulty": meta.difficulty,
		"players": meta.players,
		"durationSec": report.durationSec,
		"score": you.score,
		"foeScore": report.foe.score,
		"gathered": you.gathered,
		"spent": you.spent,
		"unitsKilled": you.unitsKilled,
		"unitsLost": you.unitsLost,
		"buildingsRazed": you.buildingsRazed,
		"buildingsLost": you.buildingsLost,
		"peakArmy": you.peakArmy,
		"peakVillagers": you.peakVillagers,
		"age": you.age,
		"idleVillagerTime": you.idleVillagerTime,
		"idleTcTime": you.idleTcTime,
		"tcSeconds": you.tcSeconds,
		"favouriteUnit": favouriteUnit,
	}


static func store() -> Object:
	# A browser with storage disabled has none — history is a nicety, not a need.
	return storage_override if storage_override != null else Storage


static func _write(s: Object, key: String, value: String) -> bool:
	var r = s.setItem(key, value)
	return not (r is bool and r == false)


## Newest first.
static func listHistory() -> Array:
	var s := store()
	if s == null:
		return []
	var raw = s.getItem(STORAGE_KEY)
	if not raw:
		return []
	var parsed = LocalJSON.parse(raw)
	if not (parsed is Array):
		return []
	# Anything malformed is dropped rather than crashing the menu it appears on.
	var rows: Array = parsed.filter(func(r): return r is Dictionary and (r.get("at") is float or r.get("at") is int) and r.get("won") is bool)
	JS.sort_by_cmp(rows, func(a, b): return float(b.at) - float(a.at))
	return rows.slice(0, HISTORY_LIMIT)


static func recordMatch(rec: Dictionary) -> void:
	var s := store()
	if s == null:
		return
	var all: Array = [rec]
	all.append_array(listHistory())
	all = all.slice(0, HISTORY_LIMIT)
	# Only the newest few carry their full report; older rows are thinned to the
	# summary as they age out of the window rather than all at once.
	for i in range(FULL_REPORT_KEEP, all.size()):
		if all[i].get("report") != null:
			all[i] = _without_report(all[i])
	if not _write(s, STORAGE_KEY, LocalJSON.stringify(all)):
		# Storage is full. A history entry is a nicety, but losing the *report* is
		# what blew the budget, so retry without them before giving up entirely.
		_write(s, STORAGE_KEY, LocalJSON.stringify(all.map(func(r): return _without_report(r))))
		# (nothing left to try)


## `{ ...r, report: undefined }` — JSON leaves an undefined field out.
static func _without_report(r: Dictionary) -> Dictionary:
	var c := r.duplicate()
	c.erase("report")
	return c


static func clearHistory() -> void:
	var s := store()
	if s != null:
		s.removeItem(STORAGE_KEY)


## The numbers across the whole record. Averages over what is stored rather than
## over all time — the profile already keeps lifetime wins and losses, and this
## is deliberately "how have the last twenty gone", which is the question you
## actually ask after a session.
##
## HistorySummary: { played, won, winRate, avgDuration, totalKills, totalLost,
##   killRatio (kill:loss over every recorded game — the one number that trends
##   usefully), avgTcIdleShare (average share of the match a Town Centre sat with
##   nothing queued), bestStreak }
static func summariseHistory(rows: Array) -> Dictionary:
	if rows.is_empty():
		return {
			"played": 0.0, "won": 0.0, "winRate": 0.0, "avgDuration": 0.0, "totalKills": 0.0,
			"totalLost": 0.0, "killRatio": 0.0, "avgTcIdleShare": 0.0, "bestStreak": 0.0,
		}
	var won := float(rows.filter(func(r): return r.won).size())
	var totalKills: float = rows.reduce(func(a, r): return a + float(r.unitsKilled), 0.0)
	var totalLost: float = rows.reduce(func(a, r): return a + float(r.unitsLost), 0.0)
	var idleShares: Array = rows.filter(func(r): return r.tcSeconds > 0).map(func(r): return float(r.idleTcTime) / r.tcSeconds)
	# Oldest to newest, so a streak means what it sounds like.
	var best := 0.0
	var run := 0.0
	var ordered := rows.duplicate()
	JS.sort_by_cmp(ordered, func(a, b): return float(a.at) - float(b.at))
	for r in ordered:
		run = run + 1.0 if r.won else 0.0
		if run > best:
			best = run
	return {
		"played": float(rows.size()),
		"won": won,
		"winRate": won / rows.size(),
		"avgDuration": rows.reduce(func(a, r): return a + float(r.durationSec), 0.0) / rows.size(),
		"totalKills": totalKills,
		"totalLost": totalLost,
		"killRatio": totalKills / totalLost if totalLost > 0 else totalKills,
		"avgTcIdleShare": idleShares.reduce(func(a, b): return a + b, 0.0) / idleShares.size() if idleShares.size() > 0 else 0.0,
		"bestStreak": best,
	}
