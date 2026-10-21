## Achievements and weekly challenges.
##
## Two different jobs that share one mechanism:
##
##   • **Achievements** are permanent. They are a list of things the game can do
##     that you might not have thought to try — win without losing a villager,
##     take the Iron Crown on Conqueror — and each pays Valor once, ever.
##   • **Challenges** rotate weekly. Same shape, but drawn from a pool by the
##     week number, so there is a reason to come back and a reason to play
##     something other than your favourite opening.
##
## Both are evaluated against the same MatchRecord the history screen stores, so
## a condition is a plain function over one game's summary and nothing has to
## reach into the sim. That is the whole design constraint: if a condition can't
## be read off a finished match, it doesn't belong here.
##
## Achievement: { id, name, desc, valor (paid the first time it is earned),
##   test: Callable(MatchRecord) -> bool (true when this match earned it) }.
## Challenge: an Achievement with weekly = true (can be earned again in a later
## week; achievements cannot). The text and valor are data (C.ACHIEVEMENTS,
## C.CHALLENGE_POOL); the tests are code, here, matched up by id.
class_name Achievements


## Permanent achievements.
##
## Every one of these is a *win* condition as well as its own condition. Paying
## out for a flawless loss would be strange, and it also keeps the list honest:
## these are things to do while winning, not instead of it.
static func _achievement_tests() -> Dictionary:
	return {
		"first_blood": func(m): return m.won,
		"flawless_eco": func(m): return m.won and m.unitsLost == 0 and m.buildingsLost == 0,
		"iron_crown_conqueror": func(m): return m.won and m.difficulty == "conqueror",
		"rush": func(m): return m.won and m.durationSec < 8 * 60,
		"long_haul": func(m): return m.won and m.durationSec > 40 * 60,
		# Unwinnable until the Empire Age existed: it asked for age index 3 when
		# there were only three ages, numbered 0 to 2.
		"imperial": func(m): return m.won and m.age >= 3,
		"butcher": func(m): return m.won and m.unitsKilled >= 100,
		"ten_to_one": func(m): return m.won and m.unitsLost > 0 and m.unitsKilled >= m.unitsLost * 10,
		"busy_hands": func(m): return m.won and m.tcSeconds > 60 and float(m.idleTcTime) / m.tcSeconds < 0.1,
		"full_house": func(m): return m.won and m.players >= 8,
		"razed": func(m): return m.won and m.buildingsRazed >= 20,
		"boomer": func(m): return m.won and m.peakVillagers >= 60,
	}


## The pool three of these are drawn from each week.
##
## Written to pull in different directions on purpose — one rewards rushing, one
## rewards booming, one rewards a map you would not otherwise pick — so a week's
## set is usually not all satisfiable by the same game.
static func _challenge_tests() -> Dictionary:
	return {
		"ch_cavalry": func(m): return m.won and ["knight", "horseman", "raider", "scout"].has(m.favouriteUnit),
		"ch_archers": func(m): return m.won and ["archer", "crossbow", "skirmisher", "javelin", "handcannon"].has(m.favouriteUnit),
		"ch_infantry": func(m): return m.won and ["militia", "spearman", "twohand", "pikeman"].has(m.favouriteUnit),
		"ch_frugal": func(m): return m.won and m.gathered > 1000 and float(m.spent) / m.gathered >= 0.9,
		"ch_quick": func(m): return m.won and m.durationSec < 12 * 60,
		"ch_survivor": func(m): return m.won and m.buildingsLost >= 10,
		"ch_army": func(m): return m.won and m.peakArmy >= 40,
		"ch_hard": func(m): return m.won and ["duke", "conqueror"].has(m.difficulty),
		"ch_crowd": func(m): return m.won and m.players >= 4,
		"ch_efficient": func(m): return m.won and m.unitsLost < 5,
	}


static func _with_tests(rows: Array, tests: Dictionary) -> Array:
	var out: Array = []
	for row in rows:
		var a: Dictionary = row.duplicate()
		assert(tests.has(a.id), "achievement %s has no test ported" % a.id)
		a["test"] = tests[a.id]
		out.append(a)
	return out


static var _achievements: Array = []
static var _by_id: Dictionary = {}
static var _pool: Array = []

static var ACHIEVEMENTS: Array:
	get:
		if _achievements.is_empty():
			_achievements = _with_tests(C.ACHIEVEMENTS, _achievement_tests())
		return _achievements

static var ACHIEVEMENTS_BY_ID: Dictionary:
	get:
		if _by_id.is_empty():
			for a in ACHIEVEMENTS:
				_by_id[a.id] = a
		return _by_id

static var CHALLENGE_POOL: Array:
	get:
		if _pool.is_empty():
			_pool = _with_tests(C.CHALLENGE_POOL, _challenge_tests())
		return _pool

## How many run at once. Three is enough to choose between and few enough to read.
static var CHALLENGES_PER_WEEK: int:
	get:
		return int(C.CHALLENGES_PER_WEEK)


## Which week we are in, counted from the epoch in whole weeks.
##
## Taken from a timestamp passed in rather than read from the clock, so the
## rotation is testable and so a caller that already knows "now" doesn't
## disagree with this by a millisecond.
static func weekIndex(atMillis: float) -> float:
	return floor(atMillis / (7.0 * 24.0 * 60.0 * 60.0 * 1000.0))


## This week's three challenges.
##
## A week takes a consecutive window of the pool and the window advances by its
## own width, so consecutive weeks never share an entry and the whole pool is
## visited before anything comes round again. A rotation that felt random would
## keep re-offering the same two, which is worse than one you can see the shape
## of. (This originally strode by three *within* the week as well as between
## them, which made week 0 and week 1 overlap on two of their three.)
static func challengesForWeek(week: float) -> Array:
	var pool := CHALLENGE_POOL
	var n := pool.size()
	var take := mini(CHALLENGES_PER_WEEK, n)
	var base := week * take
	var out: Array = []
	for i in take:
		out.append(pool[int(fmod(base + i, n))])
	return out


## What this match just earned, given what has already been paid for.
##
## Returns the awards rather than applying them: the caller owns the profile and
## the Valor, and a pure function here is what lets the whole table be tested
## without a profile at all.
##
## state: AwardState { earned: achievement ids already paid out, ever;
##   challengesDone: `week:challengeId` for challenges already paid out that week }.
## Returns EarnedAward[]: { id, name, valor, weekly }.
static func evaluateAwards(m: Dictionary, state: Dictionary) -> Array:
	var done := {}
	for id in state.earned:
		done[id] = true
	var weekDone := {}
	for id in state.challengesDone:
		weekDone[id] = true
	var week := weekIndex(m.at)
	var out: Array = []
	for a in ACHIEVEMENTS:
		if done.has(a.id):
			continue
		if a.test.call(m):
			out.append({"id": a.id, "name": a.name, "valor": a.valor, "weekly": false})
	for c in challengesForWeek(week):
		var key := "%s:%s" % [JS.num(week), c.id]
		if weekDone.has(key):
			continue
		if c.test.call(m):
			out.append({"id": key, "name": c.name, "valor": c.valor, "weekly": true})
	return out
