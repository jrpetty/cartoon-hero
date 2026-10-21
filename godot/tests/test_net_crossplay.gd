extends TestCase
# Cross-play: a Godot client and a browser client in one lockstep match.
#
# The browser client is the real TS — src/ui/net_lobby.ts on a fake DOM and
# src/net/session.ts — run by node (game/scripts/netplay-client.ts). The
# Godot player hosts a room on the live relay through NetLobby; the TS player
# joins it through its own lobby's "Join a Server" form, picks a faction and
# readies; the host starts; both simulate the match, each ordering its own
# army about (to fractional coordinates, which is where the wire's numbers
# matter), and compare: the session's own checksum exchange, the checksums
# side by side, and an exact hash of every unit's position (all 64 bits).
#
# Second case: the same match with Godot's own JSON reader on the wire
# (WireJSON.exact = false), to show what reading numbers inexactly does.

const U := preload("res://tests/net_test_util.gd")
const TICKS := 360


func _init() -> void:
	Storage.use_memory_only()


static func tsx() -> String:
	return NetTestUtil.repo_root().path_join("game/node_modules/.bin/tsx")


func _play(exactWire: bool) -> Dictionary:
	var out := {}
	var srv := NetTestUtil.new()
	if not srv.start() or not FileAccess.file_exists(tsx()):
		print("      (node or game/node_modules not available — skipped)")
		return out
	WireJSON.exact = exactWire
	WireJSON.misreads = 0
	var ctx := Ctx.new()
	var starts := {}
	Storage.setItem("bb_pid", "1".repeat(32))
	var lobby := NetLobby.new()
	lobby.open(func(s): starts.g = s, {"name": "Godot"})
	check(U.click(lobby, ctx, "Join a Server"), "server form")
	lobby.fields.url = srv.url
	lobby.fields.name = "Godot"
	lobby.fields.room = "crossplay"
	check(U.click(lobby, ctx, "Connect"), "connect")
	check(NetTestUtil.pump_until([lobby], func(): return lobby.page == "room"), "Godot hosts the room")
	check(U.click(lobby, ctx, "Kingdom", "chip"), "Godot plays the Kingdom")
	# The browser player.
	var result := srv.dir.path_join("ts_result.json")
	var pid := OS.create_process(tsx(), [NetTestUtil.repo_root().path_join("game/scripts/netplay-client.ts"), srv.url, "crossplay", "Browser", str(TICKS), result, "norse"])
	check(pid > 0, "started the TS client")
	check(NetTestUtil.pump_until([lobby], func(): return lobby.players.size() == 2 and lobby.players.any(func(p): return p.name == "Browser" and p.ready and p.faction == "norse"), 30000), "the TS player joined, chose the Jarls and readied")
	U.frame(lobby, ctx)
	check(U.click(lobby, ctx, "Start Match"), "start")
	check(NetTestUtil.pump_until([lobby], func(): return starts.has("g"), 5000), "Godot got the start")
	if not starts.has("g"):
		OS.kill(pid)
		srv.stop()
		WireJSON.exact = true
		return out
	var start: Dictionary = starts.g
	eq(start.factions, ["kingdom", "norse"], "both factions travel")
	var world := NetTestUtil.worldFor(start)
	var s := NetSession.new(start.transport, start.localTeam, start.teams, false)
	s.attach(world, 5)
	var exactH := {}
	var t0 := Time.get_ticks_msec()
	# Play until we're through and the TS client has written its notes.
	while Time.get_ticks_msec() - t0 < 90000:
		if s.authorLead < 12:
			for c in NetTestUtil.ordersFor(world, s.localTeam, s.lock.nextAuthorTick):
				s.lock.localCommand(c)
			s.authorTick()
		s.transport.poll()
		NetTestUtil.stepNoting(s, 20, exactH)
		if s.lock.currentTick >= TICKS and FileAccess.file_exists(result):
			break
		OS.delay_msec(2)
	var ts = JSON.parse_string(FileAccess.get_file_as_string(result)) if FileAccess.file_exists(result) else null
	check(ts != null and ts.get("ok", false), "the TS client finished: %s" % str(ts))
	if ts != null and ts.get("ok", false):
		out = {"godot": s, "ts": ts, "exact": exactH, "tick": s.lock.currentTick, "misreads": WireJSON.misreads}
	s.transport.close()
	OS.kill(pid)
	srv.stop()
	WireJSON.exact = true
	return out


## Compare the two sides' notes: [checksums compared, checksums differing,
## exact hashes compared, exact hashes differing, first differing tick].
func _compare(r: Dictionary) -> Array:
	var s: NetSession = r.godot
	var ts: Dictionary = r.ts
	var n := 0
	var bad := 0
	var en := 0
	var ebad := 0
	var first := -1
	for t in s.localSums:
		var k := str(t)
		if ts.sums.has(k):
			n += 1
			if int(ts.sums[k]) != int(s.localSums[t]):
				bad += 1
	var ticks: Array = r.exact.keys()
	ticks.sort()
	for t in ticks:
		var k := str(t)
		if ts.exact.has(k):
			en += 1
			if int(ts.exact[k]) != int(r.exact[t]):
				ebad += 1
				if first < 0:
					first = t
	return [n, bad, en, ebad, first]


func test_a_godot_and_a_browser_player_stay_in_sync() -> void:
	var r := _play(true)
	if r.is_empty():
		return
	var c := _compare(r)
	print("      (Godot at tick %d, TS at %d; checksums compared %d, differing %d; exact position hashes compared %d, differing %d)" % [r.tick, int(r.ts.tick), c[0], c[1], c[2], c[3]])
	check(c[0] >= TICKS / 40 - 1, "compared %d checksums" % c[0])
	eq(c[1], 0, "checksums that differ")
	eq(c[3], 0, "exact hashes that differ (first at tick %d)" % c[4])
	check(not r.godot.desynced, "the Godot session saw no desync")
	check(not r.ts.desynced, "the TS session saw no desync")


func test_godots_own_json_reader_misreads_live_traffic() -> void:
	# Why WireJSON exists, shown on a live match: with Godot's own JSON reader
	# on the wire, a share of the numbers arriving (the browser's coordinates,
	# and our own turns read back) come out an ulp off. A move target an ulp
	# out is usually absorbed — a unit's step toward it rounds the same — so
	# a short match often still agrees; but nothing guarantees it (an arrival
	# test or a cell boundary on the knife edge forks the game), which is why
	# the port reads them exactly.
	var r := _play(false)
	if r.is_empty():
		return
	var c := _compare(r)
	print("      (Godot's own JSON reader: %d numbers off the wire misread; checksums differing %d of %d; exact position hashes differing %d of %d%s; desync flagged: Godot %s, TS %s)" % [
		r.misreads, c[1], c[0], c[3], c[2], (", first at tick %d" % c[4]) if c[4] >= 0 else "", str(r.godot.desynced), str(r.ts.desynced)])
	check(r.misreads > 0, "Godot's JSON reader read every number exactly this time")


