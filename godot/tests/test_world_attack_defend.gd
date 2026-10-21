extends TestCase
# Ported from game/src/sim/attack_defend.test.ts — "rushed before 7:00" has to
# mean you attacked *their base*, not that you beat off their attack in yours.
# Every blow is classed by whose ground it landed on: attack (theirs), defend
# (yours) or field (no one's). (The TS test also runs the career/playstyle
# readers over the report; those live in the meta port, so here the report's
# own fields are checked instead.)

const P := 0
const E := 1


func setup() -> World:
	var w := SimTestUtil.build({"seed": 8, "commanders": ["", ""], "mode": "conquest"})
	w.tick()
	return w


func run(w: World, sec: float) -> void:
	for i in int(sec * 20):
		w.tick()


## A fight between one of each side at a point, with the attacker told to attack.
func fightAt(w: World, attacker: int, x: float, y: float) -> void:
	var a := w.spawnUnit(attacker, "militia", x - 12, y)
	var d := w.spawnUnit(E if attacker == P else P, "spearman", x + 12, y)
	w.issueAttack([a.id], d.id)
	w.issueAttack([d.id], a.id)
	run(w, 3)


func test_a_fight_at_the_enemy_town_center_is_an_attack_for_you() -> void:
	var w := setup()
	var home := SimTestUtil.tcOf(w, E)
	fightAt(w, P, home.x, home.y + 120)
	var you: Dictionary = w.player(P).stats
	var them: Dictionary = w.player(E).stats
	check(you.firstAttackAt >= 0, "you attacked")
	eq(you.firstDefendAt, -1, "you did not defend")
	check(you.damageAttacking > 0)
	check(them.firstDefendAt >= 0, "they defended")
	eq(them.firstAttackAt, -1, "defending at home is not an attack")
	check(them.damageDefending > 0)


func test_a_fight_at_your_own_base_is_a_defence_for_you() -> void:
	var w := setup()
	var home := SimTestUtil.tcOf(w, P)
	fightAt(w, E, home.x, home.y + 120)
	eq(w.player(P).stats.firstAttackAt, -1)
	check(w.player(P).stats.firstDefendAt >= 0)
	check(w.player(E).stats.firstAttackAt >= 0)


func test_a_fight_in_the_middle_of_the_map_is_neither() -> void:
	var w := setup()
	var a := SimTestUtil.tcOf(w, P)
	var b := SimTestUtil.tcOf(w, E)
	fightAt(w, P, (a.x + b.x) / 2.0, (a.y + b.y) / 2.0)
	for t in [P, E]:
		var s: Dictionary = w.player(t).stats
		eq(s.firstAttackAt, -1, "team %d attack" % t)
		eq(s.firstDefendAt, -1, "team %d defend" % t)
		check(s.firstFieldAt >= 0, "team %d field" % t)


func test_kills_are_split_the_same_way_and_only_an_attack_counts_as_a_rush() -> void:
	var w := setup()
	var home := SimTestUtil.tcOf(w, E)
	for i in 4:
		var v := w.spawnUnit(E, "villager", home.x + 60 + i * 10, home.y + 90)
		v.hp = 1
		var a := w.spawnUnit(P, "archer", home.x + 60 + i * 10, home.y + 170)
		w.issueAttack([a.id], v.id)
	run(w, 6)
	var s: Dictionary = w.player(P).stats
	check(s.killsAttacking >= 4, "killsAttacking %s" % s.killsAttacking)
	eq(s.killsDefending, 0)
	check(s.openerByUnit.get("archer", 0) > 0, "the opener is what did the damage")
	# The report carries the same reading, for the career readers.
	var mine := Metrics.matchReport(w, P, "Test")
	check(mine.you.firstAttackAt >= 0, "report: first attack")
	check(mine.you.openerByUnit.get("archer", 0) > 0, "report: opener")
	var theirs := Metrics.matchReport(w, E, "Test")
	eq(theirs.you.firstAttackAt, -1, "the defender never attacked")


func test_a_hit_on_the_enemy_is_dated_when_it_lands() -> void:
	var w := setup()
	run(w, 30)
	var home := SimTestUtil.tcOf(w, E)
	fightAt(w, P, home.x, home.y + 120)
	var s: Dictionary = w.player(P).stats
	check(s.firstAttackAt >= 30, "after 30s")
	check(s.firstAttackAt < 34, "before 34s")
	eq(s.firstHitAt, s.firstAttackAt)


func test_a_razed_building_and_a_kill_are_credited_to_the_unit_that_did_it() -> void:
	var w := setup()
	var home := SimTestUtil.tcOf(w, E)
	var house = SimTestUtil.find(w, func(e): return e.alive and e.team == E and e.kind == T.Kind.Building and e.type != "town_center")
	if house == null:
		house = w.placeBuilding(E, "house", home.x + 150, home.y)
	if house == null:
		house = SimTestUtil.placeNear(w, E, "house", home.x, home.y, 150, 400, 32, 16, false)
	house.hp = 5
	var a := w.spawnUnit(P, "militia", house.x, house.y + 60)
	w.issueAttack([a.id], house.id)
	run(w, 8)
	var s: Dictionary = w.player(P).stats
	eq(house.alive, false)
	eq(s.buildingsRazed, 1)
	eq(s.razedByUnit.get("militia", 0), 1)
	check(s.firstRazeAt > 0)
