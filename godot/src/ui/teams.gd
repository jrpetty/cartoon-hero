## ui/teams.ts — team layouts for a match: who plays with whom.
##
## Teams used to be one switch — "Even Teams" — that split the seats by parity,
## so players 1, 3, 5, 7 fought 2, 4, 6, 8. Nobody could see why they were on
## the team they were on, and nothing but an even two-way split was possible.
## Now every seat has a team of its own choosing: a number (1–8) shared with
## allies, or 0 to fight alone. Presets fill it in organised blocks — seats 1–4
## against 5–8, three teams of three and a pair, two-a-side four ways — and any
## seat can then be moved by hand.
##
## A `Teams` value (TS `type Teams = number[]`) is an Array of ints: a seat's
## team, 0 fights alone; seats sharing 1..8 are allies.
class_name Teams
extends RefCounted

const MAX_TEAMS := 8

## Team colours for rosters and lobbies: index 0 is "on your own".
const TEAM_COLORS := ["#9a917b", "#5b8fe0", "#d8574a", "#4ab86a", "#e0a83a", "#9a6ae0", "#3ac8c0", "#e08a4a", "#e06a9a"]


## Every seat on its own.
static func freeForAll(players: int) -> Array:
	var out: Array = []
	for i in players:
		out.append(0)
	return out


## `count` teams in contiguous blocks, as even as the numbers allow, larger
## teams first: 8 players in 3 teams is 3, 3, 2 — seats 1–3, 4–6, 7–8.
static func blockTeams(players: int, count: int) -> Array:
	var k := maxi(2, mini(count, players))
	var out: Array = []
	var base := floori(float(players) / k)
	var extra := players % k
	for t in k:
		var size := base + (1 if t < extra else 0)
		for i in size:
			out.append(t + 1)
	return out


## You and `allies` more seats on team 1, everyone else on team 2 (co-op).
static func coopTeams(players: int, allies: int) -> Array:
	var out: Array = []
	for i in players:
		out.append(1 if i <= allies else 2)
	return out


## The sim's alliance id per seat. Seats on the same numbered team share one;
## each solo seat gets one of its own, numbered after the teams.
static func alliancesFor(teams: Array) -> Array:
	var next := MAX_TEAMS
	var out: Array = []
	for t in teams:
		if int(t) >= 1:
			out.append(int(t) - 1)
		else:
			out.append(next)
			next += 1
	return out


## The sides, as seat indices, largest first.
static func sides(teams: Array) -> Array:
	var groups := {}
	for seat in teams.size():
		var t := int(teams[seat])
		var key := ("t%d" % t) if t >= 1 else ("s%d" % seat)
		if not groups.has(key):
			groups[key] = []
		groups[key].append(seat)
	return groups.values()


## "4 v 4", "2 v 2 v 2 v 2", "3 v 1 v 1", or "Free-for-all".
static func formatLabel(teams: Array) -> String:
	var s := sides(teams)
	if s.all(func(g): return g.size() == 1):
		return "1 v 1" if teams.size() == 2 else "%d-player free-for-all" % teams.size()
	var sizes: Array = s.map(func(g): return g.size())
	sizes = JS.sort_by_cmp(sizes, func(a, b): return b - a)
	return " v ".join(sizes.map(func(n): return str(n)))


## A match needs at least two sides.
static func teamsValid(teams: Array) -> bool:
	return sides(teams).size() >= 2


## Resize to `players` seats, keeping what can be kept.
static func resizeTeams(teams: Array, players: int) -> Array:
	var out := teams.slice(0, players)
	while out.size() < players:
		out.append(0)
	return out
