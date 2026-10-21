extends TestCase
# The relay's protocol, spoken by Godot clients (WSLink, and NetSession for
# matches): net/server.test.ts, hub.test.ts and lobby_teams.test.ts, against
# the real game/server/server.mjs under node. If these pass, a Godot client
# and a browser client are saying the same things to the same server.

const R := NetTestUtil.Raw


func _init() -> void:
	Storage.use_memory_only()


var srv: NetTestUtil


func _up() -> bool:
	srv = NetTestUtil.new()
	if srv.start():
		return true
	print("      (node not available — skipped)")
	return false


var _pidN := 0


## A raw client; with `room`, it says hello into that room once open.
func client(name: String, room := "", extra: Dictionary = {}) -> R:
	var c := R.new(srv.url)
	NetTestUtil.pump_until([c], func(): return c.opened)
	if room != "":
		var m := {"t": "hello", "name": name, "room": room}
		m.merge(extra, true)
		c.send(m)
	return c


## A hub client: identified with its own player id and the protocol version.
func hubClient(name: String) -> R:
	_pidN += 1
	var c := client(name)
	c.set_meta("pid", ("%x" % _pidN).lpad(32, "a"))
	c.set_meta("name", name)
	hsend(c, {"t": "hub"})
	return c


func hsend(c: R, m: Dictionary) -> void:
	var o := {"name": c.get_meta("name"), "pid": c.get_meta("pid"), "v": NetLobby.PROTOCOL}
	o.merge(m, true)
	c.send(o)


func flush(cs: Array, ms := 120) -> void:
	NetTestUtil.pump(cs, ms)


func closeAll(cs: Array) -> void:
	for c in cs:
		c.link.close()
	srv.stop()


## The NetStart a raw client's start message describes, for building its world.
func startOf(c: R) -> Dictionary:
	var st = c.last("start")
	var me = c.last("welcome")
	var team := 0
	var observer := true
	for x in st.slotTeams:
		if int(x.slot) == int(me.slot):
			team = int(x.team)
			observer = false
	var teams: Array = []
	for i in int(st.numTeams):
		teams.append(i)
	return {"transport": c.link, "seed": int(st.seed), "numTeams": int(st.numTeams), "teams": teams, "localTeam": team, "observer": observer or me.get("observer", false),
		"alliances": st.alliances.map(func(a): return int(a)), "map": st.get("map"), "factions": st.get("factions")}


func sessionOf(c: R) -> NetSession:
	var s := startOf(c)
	var w := NetTestUtil.worldFor(s)
	var ns := NetSession.new(c.link, s.localTeam, s.teams, s.observer)
	ns.attach(w, 4)
	return ns


# ------------------------------------------------------- server.test.ts --

func test_relays_a_2v2_lockstep_match_keeping_all_four_in_sync() -> void:
	if not _up():
		return
	var cs: Array = []
	for n in ["A", "B", "C", "D"]:
		cs.append(client(n, "test"))
	check(NetTestUtil.pump_until(cs, func(): return cs.all(func(c): return c.last("welcome") != null)), "all joined")
	# Two per side, the old way (side A/B).
	for i in 4:
		cs[i].send({"t": "side", "side": i % 2})
	flush(cs)
	var host: R = cs[0]
	for c in cs:
		if int(c.last("welcome").slot) < int(host.last("welcome").slot):
			host = c
	host.send({"t": "start"})
	check(NetTestUtil.pump_until(cs, func(): return cs.all(func(c): return c.last("start") != null)), "all started")
	var ss: Array = cs.map(func(c): return sessionOf(c))
	# Allies share an alliance, enemies don't.
	var a: NetSession = ss[0]
	check(ss.any(func(s): return s != a and a.lock.world.areAllied(a.localTeam, s.localTeam)), "A has an ally")
	check(NetTestUtil.run_match(ss, 120), "ran")
	for s in ss:
		check(not s.desynced, "no desync")
		check(s.lock.currentTick > 60, "advanced")
	for t in a.localSums:
		for s in ss:
			if s.localSums.has(t):
				eq(s.localSums[t], a.localSums[t], "tick %d" % t)
	closeAll(cs)


func test_keeps_simulating_when_a_player_drops_mid_match() -> void:
	if not _up():
		return
	var cs: Array = []
	for n in ["X", "Y", "Z"]:
		cs.append(client(n, "dropper"))
	check(NetTestUtil.pump_until(cs, func(): return cs.all(func(c): return c.last("welcome") != null)), "joined")
	cs[0].send({"t": "start"})
	check(NetTestUtil.pump_until(cs, func(): return cs.all(func(c): return c.last("start") != null)), "started")
	var ss: Array = cs.map(func(c): return sessionOf(c))
	NetTestUtil.run_match(ss, 30)
	var survivors := ss.slice(0, 2)
	var before: Array = survivors.map(func(s): return s.lock.currentTick)
	cs[2].link.close()  # a player rage-quits
	NetTestUtil.pump([cs[0], cs[1]], 150)
	check(NetTestUtil.run_match(survivors, before.max() + 30, 20000), "the survivors kept going")
	for i in survivors.size():
		check(survivors[i].lock.currentTick > before[i], "advanced past the drop")
	closeAll(cs.slice(0, 2))


func test_an_observer_simulates_in_sync_without_authoring() -> void:
	if not _up():
		return
	var a := client("P1", "obsroom")
	var b := client("P2", "obsroom")
	var o := client("Caster", "obsroom", {"observer": true})
	var cs := [a, b, o]
	check(NetTestUtil.pump_until(cs, func(): return cs.all(func(c): return c.last("welcome") != null)), "joined")
	a.send({"t": "start"})
	check(NetTestUtil.pump_until(cs, func(): return cs.all(func(c): return c.last("start") != null)), "started")
	var ss: Array = cs.map(func(c): return sessionOf(c))
	check(ss[2].observer, "the caster observes")
	check(NetTestUtil.run_match(ss, 100), "ran")
	check(not ss[2].desynced, "the observer is in sync")
	var common := 0
	for t in ss[0].localSums:
		if ss[2].localSums.has(t):
			common += 1
			eq(ss[2].localSums[t], ss[0].localSums[t], "tick %d" % t)
	check(common > 0, "compared")
	closeAll(cs)


func test_relays_chat_and_map_pings() -> void:
	if not _up():
		return
	var a := client("A", "chatroom")
	var b := client("B", "chatroom")
	flush([a, b])
	a.send({"t": "chat", "name": "A", "text": "glhf", "team": 0})
	check(NetTestUtil.pump_until([a, b], func(): var m = b.last("chat"); return m != null and m.text == "glhf"), "chat relayed")
	a.send({"t": "ping", "x": 123, "y": 456, "team": 0})
	check(NetTestUtil.pump_until([a, b], func(): var m = b.last("ping"); return m != null and m.x == 123.0), "ping relayed")
	closeAll([a, b])


func test_enforces_room_passwords() -> void:
	if not _up():
		return
	var a := client("A", "locked", {"pass": "secret"})
	NetTestUtil.pump_until([a], func(): return a.last("welcome") != null)
	check(a.last("welcome") != null, "the creator gets in and sets it")
	var b := client("B", "locked", {"pass": "nope"})
	NetTestUtil.pump_until([b], func(): return b.last("error") != null)
	check(b.last("error") != null and b.last("welcome") == null, "wrong password refused")
	var c := client("C", "locked", {"pass": "secret"})
	check(NetTestUtil.pump_until([c], func(): return c.last("welcome") != null), "right password gets in")
	var d := client("D", "openroom")
	check(NetTestUtil.pump_until([d], func(): return d.last("welcome") != null), "an open room stays open")
	closeAll([a, b, c, d])


func test_supports_a_16_team_world_with_two_alliances() -> void:
	var n := 16
	var alliances: Array = []
	var teams: Array = []
	for t in n:
		alliances.append(t % 2)
		teams.append(t)
	var w := NetTestUtil.worldFor({"seed": 777, "numTeams": n, "alliances": alliances, "map": {"id": "open_plains"}, "factions": null})
	eq(w.numTeams, 16, "16 teams")
	check(w.areAllied(0, 2) and not w.areAllied(0, 1), "same parity allied")
	for t in n:
		check(w.entitiesOf(t, T.Kind.Unit).size() > 0, "team %d has units" % t)


# --------------------------------------------------------- hub.test.ts --

func test_room_list_shows_rooms_as_they_open_and_close() -> void:
	if not _up():
		return
	var browser := hubClient("Browser")
	flush([browser])
	eq(browser.last("rooms").rooms.size(), 0, "empty")
	var host := hubClient("Hostess")
	flush([browser, host])
	hsend(host, {"t": "create", "room": "Friday Night", "max": 4})
	check(NetTestUtil.pump_until([browser, host], func(): return browser.last("rooms").rooms.size() == 1), "listed")
	var r = browser.last("rooms").rooms[0]
	eq([r.name, int(r.players), int(r.max), r.host, r.locked], ["Friday Night", 1, 4, "Hostess", false], "the summary")
	host.send({"t": "leave"})
	check(NetTestUtil.pump_until([browser, host], func(): return browser.last("rooms").rooms.size() == 0), "gone")
	closeAll([browser, host])


func test_a_locked_room_stays_locked() -> void:
	if not _up():
		return
	var host := hubClient("Keeper")
	flush([host])
	hsend(host, {"t": "create", "room": "Vault", "pass": "hunter2"})
	var guest := hubClient("Guest")
	flush([host, guest])
	hsend(guest, {"t": "join", "room": "Vault", "pass": "wrong"})
	check(NetTestUtil.pump_until([host, guest], func(): return guest.last("error") != null), "refused")
	eq(guest.last("error").code, "password")
	hsend(guest, {"t": "join", "room": "Vault", "pass": "hunter2"})
	check(NetTestUtil.pump_until([host, guest], func(): return guest.last("welcome") != null), "in")
	eq(guest.last("welcome").room, "Vault")
	closeAll([host, guest])


func test_no_overfill_and_same_named_rooms_get_new_names() -> void:
	if not _up():
		return
	var a := hubClient("A")
	var b := hubClient("B")
	var c := hubClient("C")
	var cs := [a, b, c]
	flush(cs)
	hsend(a, {"t": "create", "room": "Duel", "max": 2})
	flush(cs)
	hsend(b, {"t": "join", "room": "Duel"})
	flush(cs)
	hsend(c, {"t": "join", "room": "Duel"})
	check(NetTestUtil.pump_until(cs, func(): return c.last("error") != null), "refused")
	eq(c.last("error").code, "full")
	hsend(c, {"t": "create", "room": "Duel"})
	check(NetTestUtil.pump_until(cs, func(): return c.last("welcome") != null), "made another")
	eq(c.last("welcome").room, "Duel 2")
	closeAll(cs)


func test_the_host_can_remove_someone() -> void:
	if not _up():
		return
	var host := hubClient("Boss")
	var pest := hubClient("Pest")
	var cs := [host, pest]
	flush(cs)
	hsend(host, {"t": "create", "room": "Tidy"})
	flush(cs)
	hsend(pest, {"t": "join", "room": "Tidy"})
	check(NetTestUtil.pump_until(cs, func(): return host.last("lobby") != null and host.last("lobby").players.size() == 2), "two")
	var slot := -1
	for p in host.last("lobby").players:
		if p.name == "Pest":
			slot = int(p.slot)
	pest.send({"t": "kick", "slot": 0})  # not the host: ignored
	host.send({"t": "kick", "slot": slot})
	check(NetTestUtil.pump_until(cs, func(): return pest.last("left") != null), "removed")
	eq(pest.last("left").why, "kicked")
	check(NetTestUtil.pump_until(cs, func(): return host.last("lobby").players.size() == 1), "one left")
	closeAll(cs)


func test_quick_match_can_be_cancelled_while_waiting() -> void:
	if not _up():
		return
	var a := hubClient("Solo")
	flush([a])
	hsend(a, {"t": "quick", "mode": "2v2"})
	flush([a])
	a.send({"t": "unquick"})
	flush([a])
	var b := hubClient("Late")
	flush([a, b])
	hsend(b, {"t": "quick", "mode": "2v2"})
	check(NetTestUtil.pump_until([a, b], func(): var q = b.last("queue"); return q != null and q.mode == "2v2"), "queued")
	eq(int(b.last("queue").waiting), 1, "alone in the queue")
	closeAll([a, b])


func _ranked() -> Array:
	var a := hubClient("Winner")
	var b := hubClient("Loser")
	flush([a, b])
	hsend(a, {"t": "quick", "mode": "1v1"})
	flush([a, b])
	hsend(b, {"t": "quick", "mode": "1v1"})
	check(NetTestUtil.pump_until([a, b], func(): return a.last("start") != null and b.last("start") != null, 8000), "matched and started")
	return [a, b, startOf(a).localTeam, startOf(b).localTeam]


func test_ranked_rates_nothing_when_they_disagree() -> void:
	if not _up():
		return
	var r := _ranked()
	r[0].send({"t": "result", "winner": r[2]})
	r[1].send({"t": "result", "winner": r[3]})  # somebody is lying
	flush([r[0], r[1]], 250)
	eq(r[0].last("rated"), null, "not rated")
	closeAll([r[0], r[1]])


func test_ranked_counts_the_win_for_whoever_stays() -> void:
	if not _up():
		return
	var r := _ranked()
	r[1].link.close()
	flush([r[0]], 150)
	r[0].send({"t": "result", "winner": r[2]})
	check(NetTestUtil.pump_until([r[0]], func(): return r[0].last("rated") != null), "rated")
	closeAll([r[0]])


# -------------------------------------------------- lobby_teams.test.ts --

func room(name: String, n: int) -> Array:
	var cs: Array = []
	for i in n:
		cs.append(client("P%d" % (i + 1), name))
		NetTestUtil.pump(cs, 20)
	NetTestUtil.pump_until(cs, func(): return cs.all(func(c): return c.last("welcome") != null))
	flush(cs, 60)
	return cs


## Start-message seats grouped into their alliances, in seat order.
static func blocks(start: Dictionary) -> Array:
	var st: Array = start.slotTeams.duplicate()
	st.sort_custom(func(a, b): return a.slot < b.slot)
	var by := {}
	for x in st:
		var al := int(start.alliances[int(x.team)])
		if not by.has(al):
			by[al] = []
		by[al].append(int(x.slot) + 1)
	return by.values()


func test_layout_puts_1_4_against_5_8() -> void:
	if not _up():
		return
	var cs := room("blocks2", 8)
	cs[0].send({"t": "layout", "teams": 2})
	flush(cs)
	cs[0].send({"t": "start"})
	check(NetTestUtil.pump_until(cs, func(): return cs[3].last("start") != null), "started")
	eq(blocks(cs[3].last("start")), [[1, 2, 3, 4], [5, 6, 7, 8]])
	closeAll(cs)


func test_layout_four_pairs_or_everyone_alone() -> void:
	if not _up():
		return
	var cs := room("blocks4", 8)
	cs[0].send({"t": "layout", "teams": 4})
	check(NetTestUtil.pump_until(cs, func(): var l = cs[5].last("lobby"); return l != null and int(l.layout) == 4), "laid out")
	eq(cs[5].last("lobby").players.map(func(p): return int(p.team)), [1, 1, 2, 2, 3, 3, 4, 4])
	cs[0].send({"t": "layout", "teams": 0})
	flush(cs)
	cs[0].send({"t": "start"})
	check(NetTestUtil.pump_until(cs, func(): return cs[0].last("start") != null), "started")
	eq(blocks(cs[0].last("start")).size(), 8, "eight sides")
	closeAll(cs)


func test_a_player_picks_a_team_and_the_host_moves_anyone() -> void:
	if not _up():
		return
	var cs := room("moves", 4)
	cs[0].send({"t": "layout", "teams": 2})
	flush(cs)
	cs[3].send({"t": "team", "team": 3})
	cs[0].send({"t": "team", "slot": 1, "team": 3})
	cs[2].send({"t": "team", "slot": 0, "team": 2})  # not the host — ignored
	flush(cs, 200)
	eq(cs[1].last("lobby").players.map(func(p): return int(p.team)), [1, 3, 2, 3])
	closeAll(cs)


func test_wont_start_with_everyone_on_one_side() -> void:
	if not _up():
		return
	var cs := room("oneside", 3)
	for c in cs:
		c.send({"t": "team", "team": 1})
	flush(cs)
	cs[0].send({"t": "start"})
	flush(cs, 200)
	eq(cs[1].last("start"), null, "no start")
	closeAll(cs)


func test_the_battlefield_and_factions_travel_with_the_start() -> void:
	if not _up():
		return
	var host := client("Host", "carry", {"faction": "legion"})
	NetTestUtil.pump([host], 30)
	var guest := client("Guest", "carry")
	var cs := [host, guest]
	flush(cs)
	guest.send({"t": "faction", "faction": "khanate"})
	var code := CustomMaps.serialiseMap(CustomMaps.newCustomMap("Ford", 64))
	host.send({"t": "map", "map": {"id": "custom_x", "name": "Ford", "author": "Host", "code": code}})
	guest.send({"t": "map", "map": {"id": "highlands", "name": "Highlands"}})  # only the host chooses
	flush(cs, 200)
	eq(guest.last("lobby").map.name, "Ford")
	host.send({"t": "start"})
	check(NetTestUtil.pump_until(cs, func(): return guest.last("start") != null), "started")
	var st = guest.last("start")
	eq(st.map.code, code, "the code")
	eq(st.factions, ["legion", "khanate"], "the factions")
	closeAll(cs)


func test_the_community_pool_takes_lists_hands_back_and_keeps() -> void:
	if not _up():
		return
	var a := client("Maker", "pool")
	flush([a])
	var code := CustomMaps.serialiseMap(CustomMaps.newCustomMap("Twin Rivers", 96))
	a.send({"t": "publish", "map": {"code": code, "name": "Twin Rivers", "author": "Maker", "minPlayers": 2, "maxPlayers": 4, "cols": 96}})
	check(NetTestUtil.pump_until([a], func(): return a.last("maps") != null), "listed")
	var listed: Array = a.last("maps").maps
	var entry = null
	for m in listed:
		if m.name == "Twin Rivers":
			entry = m
	check(entry != null and entry.author == "Maker", "by Maker")
	check(not JSON.stringify(listed).contains(code), "the list is light; codes on request")
	var b := client("Player", "elsewhere")
	flush([a, b])
	b.send({"t": "getmap", "id": entry.id if entry != null else ""})
	check(NetTestUtil.pump_until([b], func(): return b.last("mapcode") != null), "fetched")
	eq(b.last("mapcode").code, code, "the code back")
	a.send({"t": "publish", "map": {"code": code, "name": "Twin Rivers", "author": "Maker"}})
	flush([a], 200)
	eq(a.last("maps").maps.size(), listed.size(), "no duplicate")
	# It survives a restart (same data files).
	a.link.close()
	b.link.close()
	srv.stop()
	var dir := srv.dir
	OS.delay_msec(300)
	srv = NetTestUtil.new()
	check(srv.start({"MAPS_FILE": dir.path_join("maps.json")}), "restarted")
	var c := client("Later", "again")
	flush([c])
	c.send({"t": "maps"})
	check(NetTestUtil.pump_until([c], func(): return c.last("maps") != null), "asked")
	if c.last("maps") != null:
		check(c.last("maps").maps.any(func(m): return m.name == "Twin Rivers"), "still there")
	closeAll([c])


func test_the_pool_refuses_things_that_arent_maps() -> void:
	if not _up():
		return
	var a := client("Troll", "junk")
	flush([a])
	a.send({"t": "publish", "map": {"code": "<script>", "name": "x"}})
	check(NetTestUtil.pump_until([a], func(): return a.last("error") != null), "refused")
	closeAll([a])
