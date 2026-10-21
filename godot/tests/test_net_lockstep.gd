extends TestCase
# Lockstep determinism (net/lockstep.test.ts), plus the wire: turns travel as
# JSON text like they do over the relay server, and both clients still end on
# the same world.

func freshWorld(seed: int) -> World:
	return SimTestUtil.build({"map": ["open_plains", 7, 2], "seed": seed})


func vills(w: World, team: int) -> Array:
	return w.entitiesOf(team, T.Kind.Unit).filter(func(e): return e.type == "villager").map(func(e): return e.id)


## A perfect ordered "network" that serialises like the real one.
func wire(q: Array) -> Callable:
	return func(turn): q.append(JSON.parse_string(JSON.stringify(turn, "", false, true)))


func run_pair(seed: int, frames: int, script: Callable) -> Array:
	var aToB: Array = []
	var bToA: Array = []
	var a := Lockstep.new(freshWorld(seed), 0, [0, 1], 4, wire(aToB))
	var b := Lockstep.new(freshWorld(seed), 1, [0, 1], 4, wire(bToA))
	for frame in frames:
		script.call(frame, a, b)
		a.authorTurn()
		b.authorTurn()
		while not bToA.is_empty():
			a.receiveTurn(bToA.pop_front())
		while not aToB.is_empty():
			b.receiveTurn(aToB.pop_front())
		while a.step():
			pass
		while b.step():
			pass
	return [a, b]


func test_two_clients_stay_identical() -> void:
	var r := run_pair(4242, 120, func(frame, a, b):
		if frame % 20 == 0:
			var tx: float = 600.0 + (frame % 5) * 90
			a.localCommand({"t": "move", "team": 0, "ids": vills(a.world, 0), "x": tx, "y": 500.0 + frame, "queue": false, "attackMove": false})
			b.localCommand({"t": "move", "team": 1, "ids": vills(b.world, 1), "x": tx + 0.123456789, "y": 600.0 + frame / 7.0, "queue": false, "attackMove": false}))
	var a: Lockstep = r[0]
	var b: Lockstep = r[1]
	check(a.currentTick > 100, "ran")
	eq(a.currentTick, b.currentTick, "same tick")
	eq(Commands.worldChecksum(a.world), Commands.worldChecksum(b.world), "same world")


func test_train_and_move_stay_identical() -> void:
	var start := freshWorld(9001).entitiesOf(0, T.Kind.Unit).size()
	var r := run_pair(9001, 400, func(frame, a, b):
		if frame % 30 == 0:
			a.localCommand({"t": "train", "team": 0, "buildingId": SimTestUtil.tcOf(a.world, 0).id, "unit": "villager"})
			b.localCommand({"t": "train", "team": 1, "buildingId": SimTestUtil.tcOf(b.world, 1).id, "unit": "villager"})
		if frame % 45 == 10:
			a.localCommand({"t": "move", "team": 0, "ids": vills(a.world, 0), "x": 700.0, "y": 640.0, "queue": false, "attackMove": false}))
	var a: Lockstep = r[0]
	var b: Lockstep = r[1]
	eq(Commands.worldChecksum(a.world), Commands.worldChecksum(b.world), "same world")
	check(a.world.entitiesOf(0, T.Kind.Unit).size() > start, "villagers were trained")


func test_a_dropped_player_doesnt_stall_the_match() -> void:
	var a := Lockstep.new(freshWorld(5), 0, [0, 1], 4)
	for i in 30:
		a.authorTurn()
		while a.step():
			pass
	eq(a.currentTick, 4, "waits for the peer after the warm-up")
	a.dropTeam(1)
	while a.step():
		pass
	check(a.currentTick > 20, "keeps going without them")
