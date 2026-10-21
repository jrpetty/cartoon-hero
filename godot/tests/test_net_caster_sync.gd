extends TestCase
# net/caster_sync.test.ts — casting and replays rest on one promise: the same
# orders on the same opening give the same match. A caster watching on a
# broadcast delay stays in sync with the players through a disconnect; the
# lockstep's own record of applied orders rebuilds the match exactly (that
# record is an online match's replay); and a dropped player's orders that did
# arrive still count, for everyone.

const FACTIONS := ["legion", "khanate", "norse"]


static func freshWorld(n := 3) -> World:
	var teams: Array = []
	var loadouts: Array = []
	var econ: Array = []
	var commanders: Array = []
	for i in n:
		teams.append(i)
		loadouts.append({})
		econ.append(1.0)
		commanders.append("")
	var w := World.new(77)
	w.init(MapGen.generateMap("open_plains", 77, n), loadouts, econ, teams, commanders, false, null, "conquest", FACTIONS.slice(0, n))
	return w


## Deterministic busywork: each team keeps sending its units somewhere and training.
static func ordersFor(world: World, team: int, tick: int) -> Array:
	if tick % 15 != team * 3:
		return []
	var ids: Array = world.entitiesOf(team, T.Kind.Unit).map(func(e): return e.id)
	var out: Array = [{"t": "move", "team": team, "ids": ids, "x": 500.0 + (tick * 37 + team * 211) % 1400, "y": 500.0 + (tick * 53) % 1400, "queue": false, "attackMove": tick % 2 == 0}]
	if tick % 60 == 0:
		for e in world.entities:
			if e.alive and e.team == team and e.type == "town_center":
				out.append({"t": "train", "team": team, "buildingId": e.id, "unit": "villager"})
				break
	return out


func test_a_caster_on_a_delay_stays_in_sync_through_a_disconnect() -> void:
	var N := 3
	var TICKS := 600
	var DELAY := 120
	var DROP_AT := 300
	var outbox: Array = []
	var send := func(turn): outbox.append(WireJSON.parse(WireJSON.stringify(turn)))
	var players: Array = []
	for t in N:
		var world := freshWorld(N)
		players.append({"world": world, "lock": Lockstep.new(world, t, [0, 1, 2], 4, send)})
	var casterWorld := freshWorld(N)
	var caster := Lockstep.new(casterWorld, 0, [0, 1, 2], 4)
	caster.record = []
	var dropped := false
	for step in TICKS:
		for t in N:
			if dropped and t == 2:
				continue
			var p: Dictionary = players[t]
			for c in ordersFor(p.world, t, p.lock.nextAuthorTick):
				p.lock.localCommand(c)
			p.lock.authorTurn()
		for turn in outbox:
			for t in N:
				if t != int(turn.team):
					players[t].lock.receiveTurn(turn)
			caster.receiveTurn(turn)
		outbox.clear()
		if step == DROP_AT and not dropped:
			dropped = true
			for t in 2:
				players[t].lock.dropTeam(2)
			caster.dropTeam(2)  # arrives while the caster is still DELAY ticks behind
		for t in N:
			if not (dropped and t == 2):
				while players[t].lock.step():
					pass
		while caster.currentTick <= caster.readyThrough() - DELAY and caster.step():
			pass
	check(players[0].lock.currentTick - caster.currentTick >= DELAY - 5, "the caster really was behind")
	while caster.step():
		pass
	eq(caster.currentTick, players[0].lock.currentTick, "caught up")
	eq(Commands.worldChecksum(casterWorld), Commands.worldChecksum(players[0].world), "the caster agrees")
	eq(Commands.worldChecksum(players[1].world), Commands.worldChecksum(players[0].world), "the players agree")
	# And the caster's record is the match: replay it onto a fresh world.
	var replayWorld := freshWorld(N)
	var due := Replay.byTick(caster.record)
	while replayWorld.tickCount < caster.currentTick:
		for c in due.get(replayWorld.tickCount, []):
			Commands.applyCommand(replayWorld, c)
		replayWorld.tick()
	eq(Commands.worldChecksum(replayWorld), Commands.worldChecksum(players[0].world), "a replay rebuilds the match exactly")
