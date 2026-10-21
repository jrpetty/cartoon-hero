extends TestCase
# Port of game/src/meta/rival.test.ts.
#
# The Nemesis: a named rival who learns how you play a layer at a time, climbs
# when it beats you, carries a scar when you beat it, and falls on the third
# defeat — with an heir who keeps the grudge.
#
# Not ported here: "The rival's brain" tests drive SkirmishAI.adapt in a real
# World (AI and sim ports). adaptationFor's output — the RivalAdaptation those
# tests feed the AI — is checked below.

const H := preload("res://tests/meta_helpers.gd")

static var t := 1_700_000_000_000.0


func _init() -> void:
	Storage.use_memory_only()
	Rival.storage_override = null


static func m(over := {}) -> Dictionary:
	t += 60_000
	return H.with({
		"at": t, "kind": "skirmish", "ranked": false, "mode": "conquest", "difficulty": "knight", "won": true, "faction": "legion", "commander": "", "oaths": [],
		"map": "Highlands", "players": 2, "format": "1 v 1", "allies": [], "foes": ["norse"], "durationSec": 1500, "defeatedAt": -1, "age": 2, "ageTimes": [0, 400, 1000],
		"score": 5000, "gathered": 12000, "gatheredBy": {"food": 5000, "wood": 5000, "gold": 2000}, "spent": 11000, "kills": 40, "losses": 40, "razed": 3, "buildingsLost": 3,
		"damageDealt": 9000, "damageTaken": 9000, "peakArmy": 30, "peakVillagers": 38, "idleVillagerTime": 60, "tcIdleShare": 0.1, "upgrades": 6,
		"trained": {"villager": 40, "archer": 30, "militia": 4}, "killed": {}, "lost": {}, "built": {}, "unitKills": {"archer": 35},
		"firstHitAt": 300, "firstAttackAt": 300, "opener": {"archer": 900},
	}, over)


static func times(n: int, over := {}) -> Array:
	var out := []
	for i in n:
		out.append(m(over))
	return out


func test_starts_as_a_stranger_who_knows_nothing() -> void:
	var st := Rival.loadRival("legion")
	check(st.current.name != "", "has a name")
	eq(st.current.rank, 1)
	check(st.current.faction != "legion", "not your faction")
	var mem := Rival.memoryOf(st.current, [m(), m()])
	eq(mem.army, null)
	check(Rival.taunt(st.current, mem).contains("learn everything"), Rival.taunt(st.current, mem))
	eq(Rival.loadRival().current.name, st.current.name, "it persists")


func test_beats_you_rises_a_rank_earns_a_name_and_learns_your_army() -> void:
	var st := Rival.loadRival()
	var out := Rival.settleRival(st, m({"won": false, "durationSec": 700}))
	eq(out.promoted, true)
	eq(st.current.rank, 2)
	eq(st.current.epithet, "the Swift")
	var mem := Rival.memoryOf(st.current, [m(), m(), m()])
	eq(mem.armyWord, "archers")
	check(Rival.adaptationFor(st.current, mem).expect.archer > 8, "expects archers")


func test_learns_your_timing_next_an_early_attacker_finds_it_walled_up() -> void:
	var st := Rival.loadRival()
	Rival.settleRival(st, m({"won": true}))
	Rival.settleRival(st, m({"won": true}))
	var mem := Rival.memoryOf(st.current, times(6))
	eq(mem.timing, "early")
	var a := Rival.adaptationFor(st.current, mem)
	eq(a.style, "turtle")
	eq(a.walls, true)
	check(Rival.taunt(st.current, mem).contains("archers gave me this scar"), Rival.taunt(st.current, mem))


func test_a_slow_builder_gets_rushed() -> void:
	var st := Rival.loadRival()
	Rival.settleRival(st, m({"won": false}))
	Rival.settleRival(st, m({"won": false}))
	var late := times(6, {"firstAttackAt": 1100, "firstHitAt": 1100})
	eq(Rival.adaptationFor(st.current, Rival.memoryOf(st.current, late)).style, "rush")


func test_carries_a_scar_when_beaten_and_falls_on_the_third_defeat_its_heir_keeps_the_grudge() -> void:
	var st := Rival.loadRival()
	var first: String = st.current.name
	var a := Rival.settleRival(st, m({"won": true}))
	eq(a.renown, 60)
	check(st.current.scars[0].contains("archers"), str(st.current.scars))
	eq(st.current.grudge, "archer")
	Rival.settleRival(st, m({"won": true}))
	var last := Rival.settleRival(st, m({"won": true}))
	eq(last.slain, true)
	eq(last.renown, 60 + 250)
	eq(st.fallen[0].name, first)
	check(st.current.name != first, "a new rival")
	eq(st.current.grudge, "archer")
	check(st.current.origin.contains(first), st.current.origin)
	eq(Rival.loadRival().fallen.size(), 1)


# ---- Beyond the TS file ----

func test_learns_how_you_win_after_three_meetings() -> void:
	var st := Rival.loadRival()
	for i in 3:
		Rival.settleRival(st, m({"won": false}))
	var career := times(12, {"durationSec": 700, "firstHitAt": 290, "firstAttackAt": 290, "opener": {"militia": 900}, "trained": {"villager": 22, "militia": 26, "archer": 3}})
	var mem := Rival.memoryOf(st.current, career)
	eq(mem.winsBy, "Man-at-Arms Rush")
	eq(mem.armyWord, "infantry")


func test_reads_a_rival_saved_by_the_ts_build() -> void:
	Storage.setItem(Rival.KEY, '{"current":{"id":3,"name":"Morwen","epithet":"the Burner","faction":"norse","rank":4,"beatYou":3,"lostToYou":1,"scars":["Scarred by your cavalry"],"grudge":"cavalry","born":1700000000000,"origin":"Rose from Oro\'s ranks after you ended Oro","history":[{"at":1700000001000,"rivalWon":false,"note":"Lost to you in 20 min"}]},"fallen":[{"name":"Oro","epithet":"the Young","rank":2,"beatYou":0,"lostToYou":3,"at":1699999999000}],"seq":3}')
	var st := Rival.loadRival()
	eq(st.current.name, "Morwen")
	eq(Rival.taunt(st.current, Rival.memoryOf(st.current, [])), "Your cavalry gave me this scar. I've brought their answer.")
	var out := Rival.settleRival(st, m({"won": false, "durationSec": 1000, "buildingsLost": 7}), "", 1_700_000_002_000.0)
	eq(st.current.rank, 5)
	eq(out.lines[0], "Morwen beat you — now Morwen the Burner, risen to Tyrant.")
	var raw: String = Storage.getItem(Rival.KEY)
	check(raw.begins_with('{"current":{"id":3,"name":"Morwen"'), raw.substr(0, 60))
	check(raw.contains('"born":1700000000000,'), "whole numbers stay whole")


func test_names_come_from_the_rivals_number() -> void:
	# pick(): ((n * 2654435761) >>> 0) % length — the same rival in both builds.
	var r := Rival.newRival(1, "", "x", 1, 0)
	eq(r.name, Rival.NAMES[JS.u32(8 * 2654435761) % Rival.NAMES.size()])
	eq(r.epithet, Rival.FIRST_EPITHETS[JS.u32(4 * 2654435761) % Rival.FIRST_EPITHETS.size()])
	eq(Rival.RANK_DIFFICULTY[int(r.rank)], "knight")
