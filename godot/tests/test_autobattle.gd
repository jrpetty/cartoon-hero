extends TestCase
# Ported from game/src/sim/autobattle.test.ts and styles.test.ts: the
# auto-battle resolver, the watchable LiveBattle, and the battle styles each
# unit opens a fight with.

const GRID_CELL := Autobattle.GRID_CELL


# ---- the resolver ---------------------------------------------------------------

func test_stronger_composition_wins_deterministically() -> void:
	var knights := [{"type": "knight", "count": 8}]
	var fewMilitia := [{"type": "militia", "count": 4}]
	var r1 := Autobattle.resolveBattle(knights, fewMilitia, 42)
	var r2 := Autobattle.resolveBattle(knights, fewMilitia, 42)
	eq(r1.winner, "A")
	check(r1.survivorsA > 0)
	eq(r1.survivorsB, 0)
	check(TestWarbandUtil.same(r1, r2), "same seed → identical result")


func test_resolves_to_a_winner_and_reports_survivors() -> void:
	var r := Autobattle.resolveBattle([{"type": "archer", "count": 10}], [{"type": "militia", "count": 10}], 7)
	check(["A", "B", "draw"].has(r.winner))
	check(r.ticks > 0)
	check(r.survivorsA + r.survivorsB >= 0)


func test_star_ups_make_a_stack_stronger() -> void:
	var w := 0
	for seed in [1, 2, 3, 4, 5]:
		var r := Autobattle.resolveBattle([{"type": "knight", "count": 3, "star": 2}], [{"type": "knight", "count": 4, "star": 1}], seed)
		if r.winner == "A":
			w += 1
	check(w >= 3, "2-star knights won %d/5" % w)


func test_spearmen_counter_cavalry() -> void:
	var spearWins := 0
	for seed in [1, 2, 3, 4, 5, 6]:
		var r := Autobattle.resolveBattle([{"type": "spearman", "count": 8}], [{"type": "knight", "count": 6}], seed)
		if r.winner == "A":
			spearWins += 1
	check(spearWins >= 4, "spearmen won %d/6" % spearWins)


# ---- LiveBattle -------------------------------------------------------------------

func runToEnd(seed: int, batch: int) -> Autobattle.LiveBattle:
	var lb := Autobattle.LiveBattle.new([{"type": "knight", "count": 5}], [{"type": "militia", "count": 6}], seed)
	lb.begin()
	var guard := 0
	while not lb.done and guard < 5000:
		guard += 1
		lb.step(batch)
	return lb


func test_live_battle_plays_out_to_a_verdict() -> void:
	var lb := runToEnd(42, 1)
	check(lb.done)
	check(["A", "B", "draw"].has(lb.result().winner))
	check(lb.ticks > 0)


func test_live_battle_is_batch_independent() -> void:
	var r1 := runToEnd(7, 1).result()
	var r2 := runToEnd(7, 3).result()
	var r3 := runToEnd(7, 11).result()
	check(TestWarbandUtil.same(r1, r2), "%s vs %s" % [r1, r2])
	check(TestWarbandUtil.same(r2, r3), "%s vs %s" % [r2, r3])


func test_units_cast_abilities_in_the_live_battle() -> void:
	var army := [{"type": "knight", "star": 2}, {"type": "knight", "star": 1}, {"type": "archer", "star": 1}, {"type": "militia", "star": 1}]
	var foe := [{"type": "militia", "star": 1}, {"type": "spearman", "star": 1}, {"type": "archer", "star": 1}, {"type": "knight", "star": 1}]
	var lb := Autobattle.LiveBattle.new(army, foe, 42)
	lb.begin()
	var casts := 0
	var guard := 0
	while not lb.done and guard < 4000:
		guard += 1
		lb.step(1)
		for ev in lb.fxEvents:
			if ev.kind == "ability":
				casts += 1
		lb.fxEvents.clear()
	check(casts > 0)


func test_live_battle_waits_for_begin() -> void:
	var lb := Autobattle.LiveBattle.new([{"type": "knight", "count": 5}], [{"type": "militia", "count": 6}], 1)
	var before := lb.world.entities.filter(func(e): return e.alive).size()
	lb.step(20)
	eq(lb.ticks, 0)
	eq(lb.world.entities.filter(func(e): return e.alive).size(), before)


# ---- battle styles ------------------------------------------------------------------

## Every living unit of a team in a live battle, sorted by id (= spawn order).
func units(b: Autobattle.LiveBattle, team: int) -> Array:
	var out: Array = b.world.entities.filter(func(e): return e.alive and e.kind == T.Kind.Unit and e.type != "villager" and e.team == team)
	JS.sort_by_cmp(out, func(p, q): return p.id - q.id)
	return out


func test_every_shop_unit_has_a_described_style() -> void:
	for type in C.UNIT_STYLE:
		var style: String = C.UNIT_STYLE[type]
		check(C.BATTLE_STYLES.has(style), "%s → %s" % [type, style])
		check(C.UNITS.has(type), "unknown unit %s" % type)
	for id in C.BATTLE_STYLES:
		var def: Dictionary = C.BATTLE_STYLES[id]
		check(String(def.name).length() > 0)
		check(String(def.desc).length() > 0)
		check(String(def.color).begins_with("#"))
	eq(BattleStyles.styleOf("not_a_unit"), "line")
	eq(BattleStyles.styleDef("scout").id, "infiltrator")


func test_all_five_styles_are_used() -> void:
	var used := {}
	for type in C.UNIT_STYLE:
		used[C.UNIT_STYLE[type]] = true
	for id in C.BATTLE_STYLES:
		check(used.has(id), "no unit uses %s" % id)


func test_infiltrator_opens_behind_the_enemy() -> void:
	var mine := [{"type": "scout", "star": 1, "col": 0, "row": 4}]
	var theirs := [{"type": "crossbow", "star": 1, "col": 9, "row": 4}, {"type": "spearman", "star": 1, "col": 5, "row": 4}]
	var b := Autobattle.LiveBattle.new(mine, theirs, 5)
	var before: float = units(b, T.Team.Player)[0].x
	var enemyFront: float = units(b, T.Team.Enemy).filter(func(e): return e.type == "spearman")[0].x
	b.begin()
	var after: float = units(b, T.Team.Player)[0].x
	check(after > before + GRID_CELL * 2)
	check(after > enemyFront)
	var leaps := b.fxEvents.filter(func(e): return e.kind == "leap")
	check(leaps.size() > 0)
	if leaps.size():
		eq(leaps[0].team, T.Team.Player)
		check(RegEx.create_from_string("^-?[\\d.]+,-?[\\d.]+$").search(leaps[0].data) != null, leaps[0].data)


func test_mirrors_the_jump_for_the_enemy() -> void:
	var b := Autobattle.LiveBattle.new([{"type": "spearman", "star": 1, "col": 4, "row": 4}], [{"type": "raider", "star": 1, "col": 9, "row": 4}], 6)
	var before: float = units(b, T.Team.Enemy)[0].x
	b.begin()
	check(units(b, T.Team.Enemy)[0].x < before - GRID_CELL * 2)


func test_artillery_holds_while_line_advances() -> void:
	var b := Autobattle.LiveBattle.new(
		[{"type": "trebuchet", "star": 1, "col": 0, "row": 3}, {"type": "archer", "star": 1, "col": 0, "row": 6}],
		[{"type": "spearman", "star": 1, "col": 9, "row": 4}], 7)
	var treb0: float = units(b, T.Team.Player).filter(func(e): return e.type == "trebuchet")[0].x
	var arch0: float = units(b, T.Team.Player).filter(func(e): return e.type == "archer")[0].x
	b.begin()
	b.step(60)
	var treb1 := units(b, T.Team.Player).filter(func(e): return e.type == "trebuchet")
	var arch1 := units(b, T.Team.Player).filter(func(e): return e.type == "archer")
	if treb1.size():
		check(absf(treb1[0].x - treb0) < GRID_CELL, "trebuchet stayed put")
	if arch1.size():
		check(arch1[0].x > arch0, "archer walked in")


func test_flanker_leaves_its_lane() -> void:
	var b := Autobattle.LiveBattle.new([{"type": "knight", "star": 1, "col": 2, "row": 4}], [{"type": "spearman", "star": 1, "col": 7, "row": 4}], 8)
	var y0: float = units(b, T.Team.Player)[0].y
	b.begin()
	b.step(40)
	var k := units(b, T.Team.Player)
	if k.size():
		check(absf(k[0].y - y0) > GRID_CELL * 0.5)


func test_styled_fight_is_deterministic() -> void:
	var a := [{"type": "scout", "star": 1, "col": 0, "row": 4}, {"type": "knight", "star": 1, "col": 3, "row": 5}]
	var c := [{"type": "crossbow", "star": 1, "col": 9, "row": 4}, {"type": "spearman", "star": 1, "col": 5, "row": 5}]
	check(TestWarbandUtil.same(Autobattle.resolveBattle(a, c, 21), Autobattle.resolveBattle(a, c, 21)))


func test_role_placement_pays_off_with_a_back_line() -> void:
	var ROW_ORDER := [4, 5, 3, 6, 2, 7, 1, 8, 0, 9]
	var seeds := [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
	for types in [
		["trebuchet", "catapult", "crossbow", "spearman", "militia", "twohand"],
		["archer", "crossbow", "handcannon", "militia", "spearman", "knight"],
	]:
		var comp: Array = types.map(func(t): return {"type": t, "star": 1, "items": []})
		var sorted := comp.duplicate()
		JS.sort_by_cmp(sorted, func(p, q): return float(C.UNITS[q.type].hp) - float(C.UNITS[p.type].hp))
		var byStrength: Array = []
		for i in sorted.size():
			byStrength.append({"type": sorted[i].type, "star": 1, "items": [], "col": 5 + floori(i / float(ROW_ORDER.size())), "row": ROW_ORDER[i % ROW_ORDER.size()]})
		var byRole := Warband.placeByStyle(comp, -1)
		var roleWins := 0
		for seed in seeds:
			if Autobattle.resolveBattle(byRole, byStrength, seed).winner == "A":
				roleWins += 1
		check(roleWins > seeds.size() / 2.0, "%s: %d/%d" % ["+".join(types), roleWins, seeds.size()])


func test_infiltrators_punish_an_open_backline() -> void:
	var scouts := [{"type": "scout", "star": 2, "col": 0, "row": 4}, {"type": "scout", "star": 2, "col": 0, "row": 5}]
	var bare := [{"type": "crossbow", "star": 2, "col": 9, "row": 4}, {"type": "crossbow", "star": 2, "col": 9, "row": 5}]
	var guarded := [
		{"type": "crossbow", "star": 2, "col": 9, "row": 4}, {"type": "crossbow", "star": 2, "col": 9, "row": 5},
		{"type": "spearman", "star": 2, "col": 8, "row": 4}, {"type": "spearman", "star": 2, "col": 8, "row": 5},
	]
	var bareWins := 0
	var guardedWins := 0
	for seed in [1, 2, 3, 4, 5]:
		if Autobattle.resolveBattle(scouts, bare, seed).winner == "A":
			bareWins += 1
		if Autobattle.resolveBattle(scouts, guarded, seed).winner == "A":
			guardedWins += 1
	check(guardedWins <= bareWins)


# ---- helpers ---------------------------------------------------------------------

func test_cell_to_world_centres_the_board() -> void:
	var p := Autobattle.cellToWorld(1000.0, 800.0, 0, 0)
	eq(p.x, 1000.0 - 200.0 + 20.0)
	eq(p.y, 800.0 - 200.0 + 20.0)
	var q := Autobattle.cellToWorld(1000.0, 800.0, 9, 9)
	eq(q.x, 1000.0 + 180.0)
	eq(q.y, 800.0 + 180.0)


func test_normalize_expands_stacks() -> void:
	var out := Autobattle.normalize([{"type": "knight", "count": 3, "star": 2}, {"type": "archer", "items": ["blade"], "col": 1, "row": 2}])
	eq(out.size(), 4)
	eq(out[0].star, 2)
	eq(out[3].items, ["blade"])
	eq(out[3].col, 1)
