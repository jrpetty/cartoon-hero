extends TestCase
# Godot clients against the real relay (game/server/server.mjs, protocol 2,
# under node): two players and a caster go online through NetLobby — the
# hub, a room created and joined, teams picked, factions, chat — the host
# starts, and every client runs the match under lockstep (NetSession over
# WSLink) for hundreds of ticks of scripted orders, ending on the same world.
# Then the quick-match path: queued, matched, counted down, a ranked match,
# both report the winner and the ratings move. (net/server.test.ts,
# hub.test.ts and lobby_teams.test.ts, through the Godot client.)

const U := preload("res://tests/net_test_util.gd")
const TICKS := 400


func _init() -> void:
	Storage.use_memory_only()


## Bring a lobby to the hub, as a particular player (the player id lives in
## storage, so set it before this client says hello).
func _online(srv: NetTestUtil, name: String, pid: String, onStart: Callable) -> NetLobby:
	Storage.setItem("bb_pid", pid)
	var lobby := NetLobby.new()
	lobby.open(onStart, {"name": name})
	lobby.goOnline(srv.url)
	check(NetTestUtil.pump_until([lobby], func(): return lobby.page == "hub" and lobby.view == "hub"), name + " reached the hub")
	return lobby


func _sessionFor(start: Dictionary) -> NetSession:
	var world := NetTestUtil.worldFor(start)
	var s := NetSession.new(start.transport, start.localTeam, start.teams, start.get("observer", false))
	s.attach(world, 5)
	s.lock.record = []
	return s


func test_two_players_and_a_caster_play_a_room_match_in_sync() -> void:
	var srv := NetTestUtil.new()
	if not srv.start():
		print("      (node not available — skipped)")
		return
	var ctx := Ctx.new()
	var starts := {}
	var a := _online(srv, "Aldric", "a".repeat(32), func(s): starts.a = s)
	var b := _online(srv, "Brenna", "b".repeat(32), func(s): starts.b = s)
	var c := _online(srv, "Caster", "c".repeat(32), func(s): starts.c = s)
	var all := [a, b, c]

	# Aldric creates a room for two, by the form.
	U.frame(a, ctx)
	check(U.type_into(a, ctx, "Aldric's room", "Godot Room"), "named it")
	check(U.click(a, ctx, "2", "chip"), "two seats")
	check(U.click(a, ctx, "Create"), "Create")
	check(NetTestUtil.pump_until(all, func(): return a.page == "room" and a.room == "Godot Room"), "Aldric is in his room")
	eq(a.maxPlayers, 2, "a room for two")
	# Brenna and the caster see it in the room list; one joins, one watches.
	check(NetTestUtil.pump_until(all, func(): return b.rooms.any(func(r): return r.name == "Godot Room") and c.rooms.any(func(r): return r.name == "Godot Room")), "listed in the hub")
	check(U.click(b, ctx, "Join", "chip"), "Brenna clicks Join")
	check(U.click(c, ctx, "Watch", "chip"), "the caster clicks Watch")
	check(NetTestUtil.pump_until(all, func(): return b.page == "room" and c.page == "room" and a.players.size() == 3), "all three in the room")
	check(c.observing, "the caster is observing")
	eq(a.slot, a.hostSlot, "Aldric hosts")

	# Teams: Brenna joins Aldric's team — one side, so no start — then goes
	# back to team 2.
	check(U.click(b, ctx, "Join team 1"), "Brenna joins team 1")
	check(NetTestUtil.pump_until(all, func(): return a.players.all(func(p): return p.observer or int(p.team) == 1)), "everyone on team 1")
	U.frame(a, ctx)
	var start = a.find("Start Match")
	check(start != null and start.disabled, "no start with one side")
	check(U.click(b, ctx, "Join team 2"), "Brenna joins team 2")
	# Factions.
	check(U.click(a, ctx, "Kingdom", "chip"), "Aldric: Kingdom")
	check(U.click(b, ctx, "Jarls", "chip"), "Brenna: Jarls")
	check(U.click(b, ctx, "Ready"), "Brenna readies")
	check(NetTestUtil.pump_until(all, func():
		var pb = a.players.filter(func(p): return p.name == "Brenna")
		var pa = a.players.filter(func(p): return p.name == "Aldric")
		return pb.size() == 1 and int(pb[0].team) == 2 and pb[0].faction == "norse" and pb[0].ready and pa[0].faction == "kingdom"), "teams, factions and ready seen by the host")
	# Chat, both ways.
	check(U.type_into(b, ctx, "Say something", "glhf"), "Brenna types")
	b.handleKey("Enter")
	check(NetTestUtil.pump_until(all, func(): return a.chat.any(func(m): return m.text == "glhf") and c.chat.any(func(m): return m.text == "glhf")), "chat reaches the room")
	# The caster watches live.
	U.frame(c, ctx)
	check(c.find("Live") != null, "caster delay choices")

	U.frame(a, ctx)
	start = a.find("Start Match")
	check(start != null and not start.disabled, "start enabled")
	check(U.click(a, ctx, "Start Match"), "Aldric starts")
	check(NetTestUtil.pump_until(all, func(): return starts.size() == 3), "everyone got the start")
	if starts.size() != 3:
		srv.stop()
		return
	eq(starts.a.seed, starts.b.seed, "one seed")
	eq(starts.a.factions, ["kingdom", "norse"], "the factions travel with the start")
	check(starts.c.get("observer", false), "the caster starts as an observer")
	check(starts.a.localTeam != starts.b.localTeam, "two sides")
	eq(a.el, false, "the lobby handed over")

	var sa := _sessionFor(starts.a)
	var sb := _sessionFor(starts.b)
	var sc := _sessionFor(starts.c)
	var t0 := Time.get_ticks_msec()
	check(NetTestUtil.run_match([sa, sb, sc], TICKS), "ran %d ticks (a %d, b %d, caster %d)" % [TICKS, sa.lock.currentTick, sb.lock.currentTick, sc.lock.currentTick])
	print("      (%d ticks in %d ms, %d orders applied)" % [sa.lock.currentTick, Time.get_ticks_msec() - t0, sa.lock.record.size()])
	check(sa.lock.record.size() > 10, "orders were applied")
	for s in [sa, sb, sc]:
		check(not s.desynced, "no desync (team %d)" % s.localTeam)
	# The verdict, independently: the same checksum at every tick both reached.
	var common := 0
	for t in sa.localSums:
		if sb.localSums.has(t):
			common += 1
			eq(sa.localSums[t], sb.localSums[t], "players agree at tick %d" % t)
		if sc.localSums.has(t):
			eq(sa.localSums[t], sc.localSums[t], "the caster agrees at tick %d" % t)
	check(common >= TICKS / 40 - 1, "compared %d checksums" % common)
	for s in [sa, sb, sc]:
		s.transport.close()
	srv.stop()


func test_quick_match_is_ranked_and_rated() -> void:
	var srv := NetTestUtil.new()
	if not srv.start():
		print("      (node not available — skipped)")
		return
	var ctx := Ctx.new()
	var starts := {}
	var a := _online(srv, "Ana", "d".repeat(32), func(s): starts.a = s)
	var b := _online(srv, "Bo", "e".repeat(32), func(s): starts.b = s)
	var all := [a, b]
	Storage.setItem("bb_pid", "d".repeat(32))
	check(U.click(a, ctx, "1 v 1"), "Ana queues")
	check(NetTestUtil.pump_until(all, func(): return a.queue != null and a.queue.waiting == 1), "waiting for one more")
	U.frame(a, ctx)
	check(a.find("Cancel") != null, "the search can be cancelled")
	Storage.setItem("bb_pid", "e".repeat(32))
	check(U.click(b, ctx, "1 v 1"), "Bo queues")
	check(NetTestUtil.pump_until(all, func(): return a.page == "room" and b.page == "room" and a.countdownAt > 0), "matched, counting down")
	check(a.ranked and a.quickRoom == "1v1", "a ranked quick room")
	U.frame(a, ctx)
	check(a.find("Start Match") == null, "nobody starts a quick match by hand")
	check(NetTestUtil.pump_until(all, func(): return starts.size() == 2, 8000), "it starts on its own")
	if starts.size() != 2:
		srv.stop()
		return
	check(starts.a.ranked, "ranked")
	var sa := _sessionFor(starts.a)
	var sb := _sessionFor(starts.b)
	var rated := {}
	sa.onRated = func(m): rated.a = m
	sb.onRated = func(m): rated.b = m
	check(NetTestUtil.run_match([sa, sb], 160), "played")
	check(not sa.desynced and not sb.desynced, "in sync")
	# Both report the same winner: Ana's side.
	var winner: int = starts.a.localTeam
	for s in [sa, sb]:
		s.transport.send({"t": "result", "winner": winner, "time": 8.0, "factions": s.lock.world.players.map(func(p): return p.faction)})
	check(NetTestUtil.pump_until([sa.transport, sb.transport], func(): return rated.size() == 2), "the server rated it")
	if rated.size() == 2:
		check(float(rated.a.delta) > 0, "ratings moved by %s" % str(rated.a.delta))
		eq(rated.a.winner, rated.b.winner, "one winner")
	sa.transport.close()
	sb.transport.close()
	# Back to the hub, as after a match: the ladder has them now.
	Storage.setItem("bb_pid", "d".repeat(32))
	var again := NetLobby.new()
	again.reopenHub(func(_s): pass, srv.url, {"name": "Ana"})
	check(NetTestUtil.pump_until([again], func(): return again.page == "hub" and not again.top.is_empty() and again.meRec != null), "the hub, with a leaderboard")
	if again.meRec != null:
		check(int(again.meRec.rating) > 1000, "Ana's rating went up: %s" % str(again.meRec.rating))
		eq(int(again.meRec.wins), 1, "one win")
	again.cancel()
	srv.stop()


func test_an_out_of_date_client_is_told_to_update() -> void:
	var srv := NetTestUtil.new()
	if not srv.start():
		print("      (node not available — skipped)")
		return
	var raw := NetTestUtil.Raw.new(srv.url)
	NetTestUtil.pump_until([raw], func(): return raw.opened)
	# What NetLobby does with the server's answer to an old protocol.
	var lobby := NetLobby.new()
	lobby.open(func(_s): pass)
	lobby.ws = raw.link
	raw.send({"t": "hub", "name": "Old", "v": NetLobby.PROTOCOL - 1})
	check(NetTestUtil.pump_until([raw], func(): return raw.last("error") != null), "refused")
	var err = raw.last("error")
	if err != null:
		eq(err.code, "version", "a version error")
		lobby.onMessage(raw.link, err, func(_m): pass)
		eq(lobby.page, "version", "asks to update")
	raw.link.close()
	srv.stop()


func test_the_community_pool_publish_list_use_keep() -> void:
	var srv := NetTestUtil.new()
	if not srv.start():
		print("      (node not available — skipped)")
		return
	var ctx := Ctx.new()
	var starts := {}
	# A map of our own in the pool, to publish to the server from the picker.
	var mine := CustomMaps.customFromGenerated("riverlands", 9, 2, "Ford of Ash")
	CustomMaps.publishCustomMap(mine, "Aldric")
	var a := _online(srv, "Aldric", "f".repeat(32), func(s): starts.a = s)
	U.frame(a, ctx)
	check(U.click(a, ctx, "Create"), "Create")
	check(NetTestUtil.pump_until([a], func(): return a.page == "room"), "in a room")
	check(U.click(a, ctx, "Change…"), "open the picker")
	check(NetTestUtil.pump_until([a], func(): return a.page == "picker"), "the picker")
	check(U.click(a, ctx, "Publish to server"), "publish ours")
	check(NetTestUtil.pump_until([a], func(): return a.community.any(func(m): return m.name == "Ford of Ash")), "it's in the server's pool")
	U.frame(a, ctx)
	check(a.find("On server") != null, "marked as on the server")
	# Use it from the community list: its code comes back and becomes the map.
	var rows := a.drawn.filter(func(d): return d.label == "Use")
	check(not rows.is_empty(), "Use buttons")
	if not rows.is_empty():
		var last = rows[rows.size() - 1]  # the community row is last
		U.frame(a, ctx, last.x + last.w / 2, last.y + last.h / 2, true)
	check(NetTestUtil.pump_until([a], func(): return a.page == "room" and String(a.map.get("name", "")) == "Ford of Ash"), "the battlefield is the community map: %s" % str(a.map))
	# A friend in the room starts on it, and builds the very same world.
	var raw := NetTestUtil.Raw.new(srv.url)
	NetTestUtil.pump_until([raw], func(): return raw.opened)
	raw.send({"t": "hello", "name": "Brenna", "room": a.room, "v": 2})
	check(NetTestUtil.pump_until([a, raw], func(): return a.players.size() == 2), "two players")
	check(U.click(a, ctx, "Start Match"), "start")
	check(NetTestUtil.pump_until([a, raw], func(): return starts.has("a") and raw.last("start") != null), "started")
	if starts.has("a"):
		check(String(starts.a.map.get("code", "")).begins_with("BBMAP"), "the map travels as its code")
		var w := NetTestUtil.worldFor(starts.a)
		check(w.numTeams == 2, "a world on the community map")
		starts.a.transport.close()
	raw.link.close()
	srv.stop()
