## The live relay for the net tests: game/server/server.mjs started under
## node on a free port, with throwaway data files — the same server, with the
## same protocol, that the browser build plays on. Plus the little a test
## needs to drive lobbies and lockstep sessions against it synchronously
## (Godot sockets are polled, so a test can simply loop).
class_name NetTestUtil
extends RefCounted

var pid := -1
var port := 0
var dir := ""
var url: String:
	get:
		return "ws://127.0.0.1:%d" % port


static func repo_root() -> String:
	return ProjectSettings.globalize_path("res://").path_join("..").simplify_path()


static func has_node() -> bool:
	var out: Array = []
	return OS.execute("node", ["--version"], out, true) == 0


static func free_port() -> int:
	var rng := RandomNumberGenerator.new()
	rng.randomize()
	for _i in 50:
		var p := rng.randi_range(20000, 60000)
		var srv := TCPServer.new()
		if srv.listen(p, "127.0.0.1") == OK:
			srv.stop()
			return p
	return 0


## Start the relay. Returns false if node isn't there or it never came up.
func start(env: Dictionary = {}) -> bool:
	if not has_node():
		return false
	port = free_port()
	dir = ProjectSettings.globalize_path("user://net_test_%d_%d" % [OS.get_process_id(), port])
	DirAccess.make_dir_recursive_absolute(dir)
	var vars := {
		"PORT": str(port), "HOST": "127.0.0.1", "NODE_ENV": "test",
		"MAPS_FILE": dir.path_join("maps.json"), "PLAYERS_FILE": dir.path_join("players.json"),
		"MATCHES_FILE": dir.path_join("matches.jsonl"), "SAMPLES_FILE": dir.path_join("samples.jsonl"),
		"GAME_HTML": dir.path_join("no-game.html"),
	}
	vars.merge(env, true)
	for k in vars:
		OS.set_environment(k, vars[k])
	pid = OS.create_process("node", [repo_root().path_join("game/server/server.mjs")])
	if pid <= 0:
		return false
	# Up when it accepts a connection.
	var t0 := Time.get_ticks_msec()
	while Time.get_ticks_msec() - t0 < 8000:
		var tcp := StreamPeerTCP.new()
		tcp.connect_to_host("127.0.0.1", port)
		var t1 := Time.get_ticks_msec()
		while Time.get_ticks_msec() - t1 < 300:
			tcp.poll()
			if tcp.get_status() == StreamPeerTCP.STATUS_CONNECTED:
				tcp.disconnect_from_host()
				return true
			if tcp.get_status() == StreamPeerTCP.STATUS_ERROR:
				break
			OS.delay_msec(10)
		OS.delay_msec(50)
	return false


func stop() -> void:
	if pid > 0:
		OS.kill(pid)
		pid = -1


## Poll everything given (lobbies, links, anything with poll()) for `ms`.
static func pump(things: Array, ms: int) -> void:
	var t0 := Time.get_ticks_msec()
	while true:
		for t in things:
			if t != null:
				t.poll()
		if Time.get_ticks_msec() - t0 >= ms:
			break
		OS.delay_msec(4)


## Poll until `cond.call()` is true or `ms` pass. Returns whether it came true.
static func pump_until(things: Array, cond: Callable, ms: int = 4000) -> bool:
	var t0 := Time.get_ticks_msec()
	while Time.get_ticks_msec() - t0 < ms:
		for t in things:
			if t != null:
				t.poll()
		if cond.call():
			return true
		OS.delay_msec(4)
	return cond.call()


## A raw client: a WSLink that keeps every message it receives.
class Raw:
	var link: WSLink
	var got: Array = []
	var opened := false
	func _init(u: String) -> void:
		link = WSLink.new(u)
		# Methods, not lambdas: a lambda here would hold this object from its
		# own socket, a cycle that's never freed.
		link.onData = _got
		link.onOpen = _opened
	func _got(m) -> void:
		got.append(m)
	func _opened() -> void:
		opened = true
	func poll() -> void:
		link.poll()
	func send(m) -> void:
		link.send(m)
	func last(t: String):
		for i in range(got.size() - 1, -1, -1):
			if got[i] is Dictionary and got[i].get("t") == t:
				return got[i]
		return null


# ------------------------------------------------------------ the lobby UI --

## One frame of a lobby: input, then draw (which polls its socket).
static func frame(lobby: NetLobby, ctx: Ctx, mx: float = -100, my: float = -100, clicked: bool = false) -> void:
	UI.ui.begin(ctx, {"mx": mx, "my": my, "clicked": clicked, "rightClicked": false})
	lobby.draw(1100, 900, Time.get_ticks_msec() / 1000.0)


## Click the drawn button/chip/field whose label contains `label`. Draws a
## frame first so `drawn` is current. Returns false if there's no such thing.
static func click(lobby: NetLobby, ctx: Ctx, label: String, kind := "") -> bool:
	frame(lobby, ctx)
	var d = lobby.find(label, kind)
	if d == null:
		return false
	frame(lobby, ctx, d.x + d.w / 2, d.y + d.h / 2, true)
	return true


## Type into the field showing `label` (its placeholder or text).
static func type_into(lobby: NetLobby, ctx: Ctx, label: String, text: String) -> bool:
	if not click(lobby, ctx, label, "field"):
		return false
	var id = lobby.editing
	if id == null:
		return false
	lobby.fields[id] = ""
	for ch in text:
		lobby.handleKey(ch)
	return true


# ----------------------------------------------------------- the match --

## The world a NetStart describes — what main.ts startNetMatch builds: the
## host's battlefield (a published map travels as its code), fair loadouts
## (all-Common: {} here, as in the TS tests), and each realm's faction, the
## unchosen ones rolled from the seed.
static func worldFor(start: Dictionary) -> World:
	var seed: int = start.seed
	var numTeams: int = start.numTeams
	var alliances: Array = start.alliances
	var mapInfo = start.get("map")
	var custom = null
	if mapInfo is Dictionary and mapInfo.get("code"):
		custom = CustomMaps.deserialiseMap(mapInfo.code)
	var mapData: Dictionary
	if custom != null:
		mapData = CustomMaps.toMapData(custom, seed, numTeams, false)
	else:
		var id: String = mapInfo.get("id", "") if mapInfo is Dictionary else ""
		var known := id == "random" or MapGen.PRESETS.any(func(p): return p.id == id)
		mapData = MapGen.generateMap(id if known else "open_plains", seed, numTeams, false, alliances)
	var world := World.new(seed)
	var loadouts: Array = []
	var econ: Array = []
	var commanders: Array = []
	for t in numTeams:
		loadouts.append({})
		econ.append(1.0)
		commanders.append("")
	var rng := RNG.new(JS.u32(seed ^ 0xfac7105))
	var factions: Array = []
	var ids: Array = C.FACTION_IDS
	for t in numTeams:
		var roll: String = ids[rng.int_(0, ids.size() - 1)]
		var f = start.factions[t] if start.get("factions") is Array and t < start.factions.size() else null
		factions.append(f if f is String and C.FACTIONS.has(f) else roll)
	world.init(mapData, loadouts, econ, alliances, commanders, false, null, "conquest", factions)
	return world


## Scripted orders for one team on one authored tick: its army marches about
## (to fractional coordinates — the wire must carry those exactly), the town
## centre trains, villagers are sent on. A function of the world and the tick
## only, so every client (Godot or browser) issues the same ones.
static func ordersFor(world: World, team: int, tick: int) -> Array:
	if tick % 15 != (team * 3) % 15:
		return []
	var ids: Array = world.entitiesOf(team, T.Kind.Unit).map(func(e): return e.id)
	var out: Array = [{
		"t": "move", "team": team, "ids": ids,
		"x": 500.0 + fmod(tick * 37.0 + team * 211.0, 1400.0) + tick / 7.0 + 0.123456789 * (team + 1),
		"y": 500.0 + fmod(tick * 53.0, 1400.0) + 1.0 / 3.0,
		"queue": false, "attackMove": tick % 2 == 0,
	}]
	if tick % 60 == 0:
		for e in world.entities:
			if e.alive and e.team == team and e.type == "town_center":
				out.append({"t": "train", "team": team, "buildingId": e.id, "unit": "villager"})
				break
	return out


## Drive lockstep sessions until every one has simulated `ticks` ticks (or
## `ms` pass). Each client authors one turn per loop (capped a little ahead,
## as the main loop caps it), the relay delivers, everyone steps.
static func run_match(sessions: Array, ticks: int, ms: int = 60000) -> bool:
	var t0 := Time.get_ticks_msec()
	while Time.get_ticks_msec() - t0 < ms:
		var done := true
		for s in sessions:
			var ns: NetSession = s
			if not ns.observer and ns.authorLead < 12:
				var lock := ns.lock
				for c in ordersFor(lock.world, ns.localTeam, lock.nextAuthorTick):
					lock.localCommand(c)
				ns.authorTick()
			ns.transport.poll()
			ns.stepReady(20)
			if lock_tick(ns) < ticks:
				done = false
		if done:
			return true
		OS.delay_msec(2)
	return false


static func lock_tick(ns: NetSession) -> int:
	return ns.lock.currentTick if ns.lock != null else 0


## A hash of every living entity's exact position (all 64 bits of x and y).
## worldChecksum rounds to hundredths, so an ulp of difference — a coordinate
## read differently off the wire — hides from it for a long time; this
## doesn't. Same as exactHash in game/scripts/netplay-client.ts.
static func exactHash(world: World) -> int:
	var h := 2166136261
	for e in world.entities:
		if not e.alive:
			continue
		var bytes := PackedFloat64Array([float(e.id), e.x, e.y, e.hp]).to_byte_array()
		for b in bytes:
			h = JS.u32(JS.imul(h ^ b, 16777619))
	return h


## Step a session one tick at a time (as stepReady does, up to `max`),
## noting the exact hash on every 40th tick.
static func stepNoting(ns: NetSession, max: int, exact: Dictionary) -> void:
	for _i in max:
		if ns.stepReady(1) == 0:
			return
		var t: int = ns.lock.currentTick
		if t % 40 == 0:
			exact[t] = exactHash(ns.lock.world)
