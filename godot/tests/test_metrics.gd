extends TestCase
# Ported from game/src/sim/metrics.test.ts, plus the MatchReport contract the
# meta/career port reads: exactly the TS keys, in the TS order.

const SIDE_KEYS := [
	"teams", "score", "gathered", "gatheredBy", "spent", "spentOn", "banked",
	"unitsKilled", "unitsLost", "buildingsRazed", "buildingsLost",
	"damageDealt", "damageTaken", "peakArmy", "peakVillagers", "idleVillagerTime",
	"idleTcTime", "idleProductionTime", "tcSeconds", "productionSeconds",
	"age", "upgrades", "trainedByType", "lostByType", "killedByType", "builtByType",
	"killsByUnit", "damageByUnit", "razedByUnit",
	"firstHitAt", "firstKillAt", "firstRazeAt",
	"firstAttackAt", "firstDefendAt", "firstFieldAt", "damageAttacking", "damageDefending", "damageField", "killsAttacking", "killsDefending", "openerByUnit",
]
const PLAYER_EXTRA := ["team", "group", "relation", "horde", "won", "defeated", "defeatedAt", "oaths", "faction", "ageTimes"]


func test_counts_villagers_army_strength_and_a_composite_score() -> void:
	var w := SimTestUtil.build({"seed": 1})
	var m0 := Metrics.teamMetrics(w, 0)
	check(m0.villagers > 0)
	eq(m0.army, 0)
	eq(m0.military, 0)
	w.spawnUnit(0, "knight", 800, 800)
	var m1 := Metrics.teamMetrics(w, 0)
	eq(m1.army, 1)
	check(m1.military > 0)
	check(m1.score > m0.score, "fielding an army raises score")


func test_reflects_kills_gathering_and_age_in_the_score() -> void:
	var w := SimTestUtil.build({"seed": 2})
	var before: float = Metrics.teamMetrics(w, 0).score
	var p = w.player(0)
	p.stats.unitsKilled += 12
	p.stats.gathered += 800
	p.age = 2
	var after := Metrics.teamMetrics(w, 0)
	eq(after.killed, 12)
	eq(after.age, 2)
	check(after.score > before)


func test_snapshots_every_team_at_8_player_scale() -> void:
	var w := SimTestUtil.build({"seed": 3, "map": ["highlands", 80808, 8]})
	var snap := Metrics.snapshotMetrics(w)
	eq(snap.size(), 8)
	eq(snap.map(func(s): return s.team), [0, 1, 2, 3, 4, 5, 6, 7])


func test_match_report_has_exactly_the_ts_shape() -> void:
	var w := SimTestUtil.build({"seed": 4, "map": ["riverlands", 23, 4], "alliances": [0, 1, 0, 1], "factions": ["legion", "norse", "kingdom", "khanate"]})
	var r := Metrics.matchReport(w, 0, "Riverlands")
	eq(r.keys(), ["you", "foe", "durationSec", "mapName", "players"], "MatchReport keys")
	eq(r.you.keys(), SIDE_KEYS, "SideReport keys")
	eq(r.foe.keys(), SIDE_KEYS, "SideReport keys (foe)")
	eq(r.mapName, "Riverlands")
	eq(r.you.teams, [0, 2], "your alliance")
	eq(r.foe.teams, [1, 3], "theirs")
	eq(r.players.size(), 4)
	eq(r.players[0].keys(), SIDE_KEYS + PLAYER_EXTRA, "PlayerReport keys")
	eq(r.players[0].relation, "you")
	eq(r.players[2].relation, "ally")
	eq(r.players[1].relation, "enemy")
	eq(r.players[3].faction, "khanate")
	eq(r.players[1].group, 1)
	eq(r.players[0].won, false)
	eq(r.players[0].defeatedAt, -1)
	eq(r.players[0].ageTimes, [0.0])


func test_match_report_reads_the_ledger_and_the_winner() -> void:
	var w := SimTestUtil.build({"seed": 5})
	for e in w.entitiesOf(1):
		if e.kind == T.Kind.Building or (e.kind == T.Kind.Unit and C.UNITS[e.type].get("canBuild", false)):
			w.dealDamage(0, e, 999999, "knight")
	SimTestUtil.run(w, 2)
	eq(w.winner, 0)
	var r := Metrics.matchReport(w, 0, "Open Plains")
	eq(r.you.unitsKilled, 3, "three villagers")
	eq(r.you.buildingsRazed, 1, "a town centre")
	eq(r.foe.unitsLost, 3)
	eq(r.you.killsByUnit.get("knight", 0), 3)
	eq(r.players[0].won, true)
	eq(r.players[1].won, false)
	eq(r.players[1].defeated, true)
	check(r.players[1].defeatedAt > 0)
	near(r.durationSec, w.time)
	eq(r.you.banked, JS.round(w.player(0).resources.food + w.player(0).resources.wood + w.player(0).resources.gold))
	var empty := Metrics.emptyMatchReport()
	eq(empty.keys(), ["you", "foe", "durationSec", "mapName"])
	eq(empty.you.firstHitAt, -1)
