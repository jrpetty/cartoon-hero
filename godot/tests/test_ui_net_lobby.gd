extends TestCase
# ui/net_lobby.test.ts — the multiplayer menu, clicked through against the
# real relay (game/server/server.mjs under node): the address form, the hub,
# creating a room, a friend joining, chat, the start. Clicks land on what the
# lobby drew, as a player's would.

const U := preload("res://tests/net_test_util.gd")


func _init() -> void:
	Storage.use_memory_only()


func test_normalizeWsUrl_is_forgiving() -> void:
	eq(NetLobby.normalizeWsUrl("26.13.45.201"), "ws://26.13.45.201:8787")
	eq(NetLobby.normalizeWsUrl("26.13.45.201:9000"), "ws://26.13.45.201:9000")
	eq(NetLobby.normalizeWsUrl("ws://10.0.0.5:8787"), "ws://10.0.0.5:8787")
	eq(NetLobby.normalizeWsUrl("  localhost  "), "ws://localhost:8787")
	eq(NetLobby.normalizeWsUrl("wss://play.example.com:443"), "wss://play.example.com:443")
	eq(NetLobby.normalizeWsUrl("WSS://Play.Example.com/path"), "WSS://Play.Example.com:8787/path")
	eq(NetLobby.normalizeWsUrl(""), "")


func test_player_id_is_kept() -> void:
	var a := NetLobby.playerId()
	check(RegEx.create_from_string("^[a-f0-9]{32}$").search(a) != null, "32 hex chars: " + a)
	eq(NetLobby.playerId(), a, "the same next time")


func test_home_shows_the_ways_in() -> void:
	var lobby := NetLobby.new()
	var ctx := Ctx.new()
	lobby.open(func(_s): pass)
	U.frame(lobby, ctx)
	check(lobby.find("Play Online") != null, "Play Online")
	check(lobby.find("Join a Server") != null, "Join a Server")
	check(lobby.find("Quick 1v1") != null, "Quick 1v1")
	check(lobby.find("Cancel") != null, "Cancel")
	check(U.click(lobby, ctx, "Cancel"), "clicked Cancel")
	eq(lobby.el, false, "closed")


func test_quick_1v1_shows_host_and_join() -> void:
	var lobby := NetLobby.new()
	var ctx := Ctx.new()
	lobby.open(func(_s): pass)
	check(U.click(lobby, ctx, "Quick 1v1"), "clicked")
	U.frame(lobby, ctx)
	check(lobby.find("Host") != null, "Host")
	check(lobby.find("Join") != null, "Join")
	check(lobby.find("Back") != null, "Back")
	# No WebRTC here: Host explains, and offers the relay.
	check(U.click(lobby, ctx, "Host"), "Host")
	U.frame(lobby, ctx)
	check(lobby.find("Play Online") != null, "offers Play Online")


func test_play_online_without_a_site_asks_for_the_address() -> void:
	var lobby := NetLobby.new()
	var ctx := Ctx.new()
	lobby.open(func(_s): pass)
	check(U.click(lobby, ctx, "Play Online"), "clicked")
	eq(lobby.page, "onlineConnect", "asks where the server is")
	check(U.click(lobby, ctx, "Connect"), "Connect with nothing typed")
	eq(lobby.pageStatus, "Enter the server's address.")


func test_typing_into_fields() -> void:
	var lobby := NetLobby.new()
	var ctx := Ctx.new()
	lobby.open(func(_s): pass)
	check(U.click(lobby, ctx, "Join a Server"), "server form")
	check(U.type_into(lobby, ctx, "main", "lads"), "typed the room")
	eq(lobby.fields.room, "lads")
	lobby.handleKey("Backspace")
	eq(lobby.fields.room, "lad")
	check(lobby.handleKey("x"), "a focused field takes keys")
	lobby.handleKey("Escape")
	check(not lobby.handleKey("y"), "no field, no keys")
	lobby.editing = "pass"
	lobby.handlePaste("hunter2")
	eq(lobby.fields.pass, "hunter2", "paste")


func test_join_a_server_connect_host_starts_fires_NetStart() -> void:
	var srv := NetTestUtil.new()
	if not srv.start():
		print("      (node not available — skipped)")
		return
	var started := [null]
	var lobby := NetLobby.new()
	var ctx := Ctx.new()
	lobby.open(func(s): started[0] = s)
	check(U.click(lobby, ctx, "Join a Server"), "server form")
	lobby.fields.url = srv.url
	lobby.fields.room = "menutest"
	check(U.click(lobby, ctx, "Connect"), "Connect")
	check(NetTestUtil.pump_until([lobby], func(): return lobby.page == "room" and lobby.players.size() == 1), "in the room")
	U.frame(lobby, ctx)
	# Alone, and so the host: Start is there but disabled at one player.
	var start = lobby.find("Start Match")
	check(start != null and start.disabled, "Start disabled alone")
	# A second player joins the same room: Start comes on.
	var other := NetTestUtil.Raw.new(srv.url)
	NetTestUtil.pump_until([other], func(): return other.opened)
	other.send({"t": "hello", "name": "Buddy", "room": "menutest"})
	check(NetTestUtil.pump_until([lobby, other], func(): return lobby.players.size() == 2), "two players")
	U.frame(lobby, ctx)
	check(lobby._contentH > 0 and lobby.find("2 players") == null, "(title isn't a button)")
	start = lobby.find("Start Match")
	check(start != null and not start.disabled, "Start enabled at two")
	check(U.click(lobby, ctx, "Start Match"), "clicked Start")
	check(NetTestUtil.pump_until([lobby, other], func(): return started[0] != null), "NetStart fired")
	var s = started[0]
	if s != null:
		eq(s.numTeams, 2, "numTeams")
		eq(s.teams, [0, 1], "teams")
		eq(s.alliances.size(), 2, "alliances")
		check(s.seed is int, "seed")
		check(s.transport is WSLink, "a live WSLink for the NetSession")
		check(s.localTeam in [0, 1], "localTeam")
		eq(lobby.el, false, "the lobby closed")
		s.transport.close()
	other.link.close()
	srv.stop()


func test_play_online_hub_create_a_room_friend_joins_chat() -> void:
	var srv := NetTestUtil.new()
	if not srv.start():
		print("      (node not available — skipped)")
		return
	var started := [null]
	var lobby := NetLobby.new()
	var ctx := Ctx.new()
	Storage.setItem(NetLobby.LS_URL, srv.url)
	lobby.open(func(s): started[0] = s, {"name": "Aldric", "factions": ["legion"], "faction": "legion"})
	check(U.click(lobby, ctx, "Play Online"), "Play Online")
	check(NetTestUtil.pump_until([lobby], func(): return lobby.page == "hub"), "the hub")
	U.frame(lobby, ctx)
	# The create form: room name first, then the password.
	check(U.type_into(lobby, ctx, "Aldric's room", "Lads Night"), "named the room")
	check(U.click(lobby, ctx, "Create"), "Create")
	check(NetTestUtil.pump_until([lobby], func(): return lobby.page == "room" and lobby.room == "Lads Night"), "in Lads Night")
	# A friend finds it in the list and joins.
	var friend := NetTestUtil.Raw.new(srv.url)
	NetTestUtil.pump_until([friend], func(): return friend.opened)
	friend.send({"t": "hub", "name": "Brenna", "v": 2})
	friend.send({"t": "join", "room": "Lads Night", "name": "Brenna", "v": 2})
	friend.send({"t": "chat", "text": "evening all"})
	check(NetTestUtil.pump_until([lobby, friend], func(): return lobby.players.size() == 2 and not lobby.chat.is_empty()), "two players and a chat line")
	eq(lobby.chat[0].text if not lobby.chat.is_empty() else null, "evening all", "the chat line")
	# Our reply reaches the friend.
	check(U.type_into(lobby, ctx, "Say something", "hi Brenna"), "typed")
	lobby.handleKey("Enter")
	check(NetTestUtil.pump_until([lobby, friend], func(): var c = friend.last("chat"); return c != null and c.text == "hi Brenna"), "chat relayed")
	# Only owned factions can be picked.
	U.frame(lobby, ctx)
	var lockedChip = lobby.find("🔒 Khanate")
	check(lockedChip != null and lockedChip.disabled, "Khanate locked")
	check(U.click(lobby, ctx, "Start Match"), "Start")
	check(NetTestUtil.pump_until([lobby, friend], func(): return started[0] != null), "started")
	if started[0] != null:
		check(String(started[0].online).contains("127.0.0.1"), "remembers the hub")
		started[0].transport.close()
	friend.link.close()
	srv.stop()


func test_a_server_that_is_not_there_says_so() -> void:
	var lobby := NetLobby.new()
	var ctx := Ctx.new()
	lobby.open(func(_s): pass)
	U.click(lobby, ctx, "Join a Server")
	lobby.fields.url = "127.0.0.1:%d" % NetTestUtil.free_port()
	U.click(lobby, ctx, "Connect")
	check(NetTestUtil.pump_until([lobby], func(): return lobby.pageStatus.begins_with("Disconnected")), "status: " + lobby.pageStatus)


## The scenes the visual comparison uses (tests/visual/lobby_editor), each
## drawn a few times: nothing throws, and a frame is cheap.
func test_every_scene_draws_and_a_frame_is_cheap() -> void:
	var spec = JSON.parse_string(FileAccess.get_file_as_string("res://tests/visual/lobby_editor/lobby_scenes.json"))
	for k in spec.storage:
		Storage.setItem(k, spec.storage[k])
	var ctx := Ctx.new()
	var worst := 0.0
	var worstName := ""
	for scene in spec.scenes:
		var lobby := NetLobby.new()
		lobby.open(func(_s): pass, spec.opts)
		var fake := FakeLink.new()
		lobby.ws = fake
		if scene.get("fromHub", false):
			lobby.fromHub = true
			lobby.serverUrl = scene.serverUrl
			lobby.view = "hub"
		for m in scene.messages:
			lobby.onMessage(fake, m, func(_s): pass)
		for k in scene.get("set", {}):
			lobby.set(k, scene.set[k])
		if lobby.view == "room":
			lobby.render()
		U.frame(lobby, ctx)  # warm the caches
		var t0 := Time.get_ticks_usec()
		for i in 10:
			U.frame(lobby, ctx)
		var ms := (Time.get_ticks_usec() - t0) / 10000.0
		if ms > worst:
			worst = ms
			worstName = scene.name
		check(not lobby.drawn.is_empty(), scene.name + " drew its controls")
	print("      (slowest scene: %s, %.2f ms a frame)" % [worstName, worst])
	check(worst < 25.0, "a lobby frame took %.2f ms (%s)" % [worst, worstName])


class FakeLink:
	var sent: Array = []
	var connected := true
	func send(m) -> void:
		sent.append(m)
	func close() -> void:
		connected = false
	func poll() -> void:
		pass
