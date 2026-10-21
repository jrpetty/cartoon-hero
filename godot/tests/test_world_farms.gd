extends TestCase
# Ported from game/src/sim/farms.test.ts — farming the way a player does it:
# build a farm and the builder works it, send several villagers at one farm and
# they spread over the free ones, a spent farm is replanted and worked again,
# and nobody wanders off across the map because their farm was taken.

const P := 0


func setup(seed: int = 8) -> World:
	var w := SimTestUtil.build({"seed": seed, "commanders": ["", ""], "mode": "conquest"})
	w.player(P).resources = {"food": 5000.0, "wood": 5000.0, "gold": 5000.0}
	return w


func farmNear(w: World, i: int, done: bool = true) -> Entity:
	var c := SimTestUtil.tcOf(w)
	var r := 140 + i * 10
	while r < 700:
		for a in 16:
			var f = w.placeBuilding(P, "farm", c.x + cos(a / 2.5 + i) * r, c.y + sin(a / 2.5 + i) * r)
			if f != null:
				if done:
					f.buildState = T.BuildState.Done
					f.buildProgress = 1.0
					f.hp = f.maxHp
				return f
		r += 36
	failures.append("no farm spot")
	return null


func villagers(w: World) -> Array:
	return w.entitiesOf(P, T.Kind.Unit).filter(func(e): return e.type == "villager")


func run(w: World, sec: float) -> void:
	for i in int(sec * 20):
		w.tick()


func farming(v: Entity, f: Entity) -> bool:
	if v.order.target == f.id:
		return true
	if v.order.kind == T.OrderKind.Return:
		for o: Order in v.order.queue:
			if o.target == f.id:
				return true
	return false


func test_a_villager_sent_to_a_farm_keeps_farming_it() -> void:
	var w := setup()
	var f := farmNear(w, 0)
	var v: Entity = villagers(w)[0]
	var food0: float = w.player(P).resources.food
	w.issueGather([v.id], f.id)
	run(w, 90)
	check(farming(v, f), "still farming")
	check(w.player(P).resources.food > food0 + 20, "food banked")


func test_several_villagers_on_one_farm_spread_over_the_free_farms() -> void:
	var w := setup()
	var farms := [farmNear(w, 0), farmNear(w, 1), farmNear(w, 2)]
	var vs := villagers(w).slice(0, 3)
	w.issueGather(vs.map(func(v): return v.id), farms[0].id)
	run(w, 30)
	var worked := {}
	for v in vs:
		var idx := -1
		for k in farms.size():
			if farming(v, farms[k]):
				idx = k
				break
		worked[idx] = true
	check(not worked.has(-1), "someone isn't on a farm")
	eq(worked.size(), 3, "two share a farm")


func test_a_builder_farms_the_farm_it_just_built() -> void:
	var w := setup()
	var f := farmNear(w, 0, false)
	var v: Entity = villagers(w)[0]
	w.issueBuildRepair([v.id], f.id)
	run(w, 60)
	eq(f.buildState, T.BuildState.Done)
	check(farming(v, f), "after building: order %d" % v.order.kind)


func test_two_builders_one_farms_it_the_other_takes_a_free_farm() -> void:
	var w := setup()
	var f := farmNear(w, 0, false)
	var spare := farmNear(w, 1)
	var vs := villagers(w)
	var a: Entity = vs[0]
	var b: Entity = vs[1]
	w.issueBuildRepair([a.id, b.id], f.id)
	run(w, 60)
	var onF := [a, b].filter(func(v): return farming(v, f)).size()
	var onSpare := [a, b].filter(func(v): return farming(v, spare)).size()
	eq([onF, onSpare], [1, 1])


func test_the_villager_sent_to_build_it_keeps_it_even_if_a_helper_finishes_it() -> void:
	var w := setup()
	var f := farmNear(w, 0, false)
	var spare := farmNear(w, 1)
	var vs := villagers(w)
	var helper: Entity = vs[0]
	var builder: Entity = vs[1]
	w.issueBuildRepair([builder.id], f.id)
	run(w, 3)
	w.issueBuildRepair([helper.id], f.id)
	run(w, 60)
	check(farming(builder, f), "the builder lost its farm")
	check(farming(helper, spare), "the helper should take the free farm")


func test_a_villager_sent_to_farm_an_unfinished_farm_helps_build_it() -> void:
	var w := setup()
	var f := farmNear(w, 0, false)
	var v: Entity = villagers(w)[0]
	w.issueGather([v.id], f.id)
	run(w, 60)
	eq(f.buildState, T.BuildState.Done)
	check(farming(v, f), "order %d" % v.order.kind)


func test_a_farm_queued_after_another_job_is_farmed_once_built() -> void:
	var w := setup()
	var f := farmNear(w, 0, false)
	var g := farmNear(w, 1, false)
	var v: Entity = villagers(w)[0]
	w.issueBuildRepair([v.id], g.id)
	w.issueBuildRepair([v.id], f.id, true)
	run(w, 120)
	check(farming(v, f), "farms the last farm it built")


func test_a_builder_taken_off_the_job_gives_up_the_farm() -> void:
	var w := setup()
	var f := farmNear(w, 0, false)
	var vs := villagers(w)
	var a: Entity = vs[0]
	var b: Entity = vs[1]
	w.issueBuildRepair([a.id], f.id)
	run(w, 3)
	w.issueMove([a.id], a.x - 200, a.y)
	w.issueBuildRepair([b.id], f.id)
	run(w, 60)
	check(farming(b, f), "b farms it")
	check(not farming(a, f), "a gave it up")


func test_the_farmer_is_never_bumped_by_others_sent_to_its_farm() -> void:
	var w := setup()
	var f := farmNear(w, 0, false)
	var vs := villagers(w)
	w.issueBuildRepair([vs[0].id], f.id)
	run(w, 60)
	w.issueGather([vs[1].id, vs[2].id], f.id)
	run(w, 60)
	check(farming(vs[0], f), "the farmer kept it")
	check(not (farming(vs[1], f) or farming(vs[2], f)), "someone else took it")


func test_a_villager_bumped_from_a_taken_farm_doesnt_wander_across_the_map() -> void:
	var w := setup()
	var f := farmNear(w, 0)
	var vs := villagers(w)
	var a: Entity = vs[0]
	var b: Entity = vs[1]
	w.issueGather([a.id], f.id)
	run(w, 20)
	w.issueGather([b.id], f.id)
	run(w, 5)
	var target = w.byId.get(b.order.target)
	if target != null:
		check(JS.hypot(target.x - f.x, target.y - f.y) < 420, "went to a %s" % target.type)


func test_a_spent_farm_is_replanted_and_farmed_again() -> void:
	var w := setup()
	var f := farmNear(w, 0)
	var v: Entity = villagers(w)[0]
	w.player(P).autoReseed = true
	w.issueGather([v.id], f.id)
	run(w, 10)
	f.amount = 3 # almost spent
	run(w, 80)
	var now = SimTestUtil.find(w, func(e): return e.alive and e.type == "farm" and JS.hypot(e.x - f.x, e.y - f.y) < 5)
	check(now != null, "no farm on the old ground")
	if now != null:
		eq(now.buildState, T.BuildState.Done)
		check(farming(v, now), "after replanting: order %d" % v.order.kind)


func test_moving_a_farmer_to_another_farm_frees_the_first() -> void:
	var w := setup()
	var f1 := farmNear(w, 0)
	var f2 := farmNear(w, 1)
	var vs := villagers(w)
	var a: Entity = vs[0]
	var b: Entity = vs[1]
	w.issueGather([a.id], f1.id)
	run(w, 20)
	w.issueGather([a.id], f2.id)
	w.issueGather([b.id], f1.id)
	run(w, 30)
	check(farming(a, f2), "a moved")
	check(farming(b, f1), "b took the first")


# ---- Clicking a farm ----

func test_anywhere_on_the_field_counts_corners_included() -> void:
	var w := setup()
	var f := farmNear(w, 0)
	for i in 5:
		w.tick()
	var h := f.radius - 2
	for d in [[0, 0], [h, h], [-h, h], [h, -h], [-h, -h]]:
		var at = w.resourceAt(f.x + d[0], f.y + d[1], P)
		eq(at.id if at != null else -1, f.id, "at %s,%s" % [d[0], d[1]])


func test_knows_which_farms_have_nobody_on_them() -> void:
	var w := setup()
	var f := farmNear(w, 0)
	eq(w.farmWorked(f), false)
	w.issueGather([villagers(w)[0].id], f.id)
	w.tick()
	eq(w.farmWorked(f), true)
