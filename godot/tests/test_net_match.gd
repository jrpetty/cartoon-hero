extends TestCase
# main.gd's net match hooks: two clients, each its own App, joined by an
# in-memory relay, play one lockstep match — an order given on one lands on
# both worlds on the same tick, and they stay identical.

const Loop := preload("res://tests/test_main_loop.gd")


## One end of an in-memory relay: what one client sends, the other receives
## (through JSON, as the wire does).
class FakeTransport extends RefCounted:
	var onData: Callable
	var onClose: Callable
	var peer: FakeTransport = null
	var sent: Array = []
	var closed := false

	func send(msg) -> void:
		sent.append(msg)
		if peer != null and peer.onData.is_valid():
			peer.onData.call(JSON.parse_string(JSON.stringify(msg)))

	func close() -> void:
		closed = true


func test_two_clients_play_one_lockstep_match() -> void:
	var loop = Loop.new()
	var a = loop.makeApp()
	var b = loop.makeApp()
	var ta := FakeTransport.new()
	var tb := FakeTransport.new()
	ta.peer = tb
	tb.peer = ta
	var start := {"teams": [0, 1], "alliances": [0, 1], "seed": 777, "numTeams": 2,
		"map": {"id": "open_plains"}, "factions": ["kingdom", "legion"], "names": ["Ann", "Bo"]}
	var sa := start.duplicate(true)
	sa.transport = ta
	sa.localTeam = 0
	var sb := start.duplicate(true)
	sb.transport = tb
	sb.localTeam = 1
	a.startNetMatch(sa)
	b.startNetMatch(sb)
	check(a.net != null and b.net != null, "both in a net session")
	eq(b.me, 1, "the joiner plays team 1")
	eq(a.playerNames, ["Ann", "Bo"], "names from the lobby")
	check(ta.sent.any(func(m): return m.t == "factions"), "the factions played were reported")
	for i in 30:
		Loop.frames(a, 1)
		Loop.frames(b, 1)
	# An order from client A lands on both worlds on the same tick.
	var vils: Array = a.world.entitiesOf(0, T.Kind.Unit).map(func(e): return e.id)
	var st = a.world.map.starts[0]
	a.dispatch({"t": "move", "team": 0, "ids": vils, "x": st.x + 200, "y": st.y, "queue": false, "attackMove": false, "formation": true})
	for i in 150:
		Loop.frames(a, 1)
		Loop.frames(b, 1)
	check(a.world.tickCount > 40, "the match advanced (%d ticks)" % a.world.tickCount)
	check(absi(a.world.tickCount - b.world.tickCount) <= 12, "the clients stay within the lockstep window")
	# Step the one behind up to the other, then compare.
	while a.world.tickCount < b.world.tickCount and a.net.lock.step():
		pass
	while b.world.tickCount < a.world.tickCount and b.net.lock.step():
		pass
	eq(a.world.tickCount, b.world.tickCount, "same tick")
	eq(Commands.worldChecksum(a.world), Commands.worldChecksum(b.world), "the same world on both clients")
	check(not a.net.desynced and not b.net.desynced, "no desync detected")
	var moved := vils.any(func(id): return JS.hypot(b.world.byId[id].x - st.x, b.world.byId[id].y - st.y) > 60)
	check(moved, "client A's order moved its villagers in client B's world")
	# A chat line travels too.
	a.chatOpen = true
	a.chatDraft = "glhf"
	a.handleChatKey("Enter")
	check(b.chatLog.any(func(l): return l.text == "glhf"), "chat reached the other client")
	loop.freeApp(a)
	loop.freeApp(b)
