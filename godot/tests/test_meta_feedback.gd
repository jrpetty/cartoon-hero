extends TestCase
# matchFeedback (meta/feedback.ts has no TS test file; the parity test covers
# it against the TS build for 70 generated reports). This one feeds it a report
# whose counts are ints, as the sim port's Metrics.matchReport() may build
# them, and checks the sentences the TS build gives for the same report.

const H := preload("res://tests/meta_helpers.gd")


func test_reads_a_won_match_with_an_early_attack_both_ways() -> void:
	var r := H.emptyMatchReport()
	r.durationSec = 1200
	r.you.teams = [0]
	r.foe.teams = [1]
	r.you.merge({"gathered": 24000, "unitsKilled": 60, "unitsLost": 20, "tcSeconds": 1200, "idleTcTime": 60, "peakVillagers": 50, "buildingsRazed": 6, "upgrades": 9,
		"killsByUnit": {"knight": 30, "villager": 9, "archer": 12}, "firstAttackAt": 400, "killsAttacking": 14, "banked": 7000}, true)
	r.foe.merge({"gathered": 12000, "upgrades": 4, "firstAttackAt": 300, "killsAttacking": 6, "killsDefending": 5, "lostByType": {"villager": 11}}, true)
	r.you.lostByType = {"villager": 2}
	r.players = [
		H.with(r.you, {"team": 0, "group": 0, "relation": "you", "horde": false, "won": true, "defeated": false, "defeatedAt": -1, "ageTimes": [0, 300]}),
		H.with(r.foe, {"team": 1, "group": 1, "relation": "enemy", "horde": false, "won": false, "defeated": true, "defeatedAt": 1200, "ageTimes": [0, 420]}),
	]
	var fb := Feedback.matchFeedback(r)
	eq(fb.good, [
		"You won the fights: 60 kills for 20 losses.",
		"You killed 11 of their villagers.",
		"You out-gathered them 2.0× (1,200 a minute).",
		"You razed 6 of their buildings.",
		"Your early attack at 6:40 paid off: 14 kills in their base for 5 losses.",
	])
	eq(fb.bad, [
		"Their attack at 5:00 hurt: 6 of yours died at home.",
		"7,000 resources were never spent — more production buildings.",
	])


func test_an_empty_report_says_nothing() -> void:
	var fb := Feedback.matchFeedback(H.emptyMatchReport())
	eq(fb.good, [])
	eq(fb.bad, [])
