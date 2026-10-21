## ui/net_lobby.ts — the multiplayer lobby.
##
## Two ways in:
##   • Server (2–16 players, up to 8v8): connect to a relay you host (e.g. on a
##     VPN), pick a side, ready up, host starts. Scales far better than a mesh.
##   • Quick 1v1 (no server): serverless WebRTC with copy/paste codes — browser
##     only (see below).
## Nothing here runs until opened, so headless boot is unaffected.
##
## The TS lobby is a DOM overlay (canvas UI can't host editable text). Godot
## has no DOM, so this draws the same overlay immediate-mode, every frame, on
## Ctx: the same panel, headings, buttons, chips, fields and rows, laid out the
## way the browser lays out the TS's HTML (block flow with collapsing margins,
## flex rows, the team grid), and its text fields type through `handleKey`.
## The protocol half — every message to and from game/server/server.mjs — is
## the TS line for line, so Godot and browser players share lobbies.
##
## How the main loop drives it (as main.ts does, plus what a DOM did for free):
##   lobby.open(onStart, options) / lobby.reopenHub(onStart, url, options)
##   each frame while `lobby.el`: draw the screen under it with no pointer
##     input, then `lobby.draw(W, H, time)` (it polls its socket there);
##   keys: `if lobby.el and lobby.handleKey(key): consumed`;
##     Ctrl+V: `lobby.handlePaste(DisplayServer.clipboard_get())`.
## `onStart` receives a NetStart Dictionary (TS keys: transport, localTeam,
## teams, alliances, seed, numTeams, observer?, factions?, map?, ranked?,
## online?, names?, delaySec?). The transport is a WSLink the match now owns:
## the main loop must keep calling `transport.poll()` every frame.
##
## Serverless 1v1 (net/webrtc.ts, PeerLink) is not ported: Godot's desktop
## builds have no WebRTC without a GDExtension. In the TS it is a separate,
## manual path (swap invite codes); every online game already goes through
## the relay (WSLink), which carries the very same lockstep match — so here
## the Quick 1v1 screens explain that and offer Play Online instead.
class_name NetLobby
extends RefCounted

## Must match the server's PROTOCOL; a mismatch asks the player to reload.
const PROTOCOL := 2

const QUICK_LABELS := {"1v1": "1 v 1", "2v2": "2 v 2", "ffa4": "4-player free-for-all"}

const LS_URL := "bb_server_url"
const LS_NAME := "bb_player_name"

const SANS := "system-ui, sans-serif"
const SERIF := "Georgia, serif"
const MONO := "monospace"


## The server to use when the game is being played from a website: the site
## itself. The same server that sent the page runs the multiplayer, so there
## is nothing to type. A desktop build has no such server.
static func defaultServerUrl() -> String:
	if not OS.has_feature("web"):
		return ""
	var proto = JavaScriptBridge.eval("location.protocol", true)
	var host = JavaScriptBridge.eval("location.host", true)
	if not (proto is String) or not (host is String) or host == "" or not (proto == "http:" or proto == "https:"):
		return ""
	return "%s://%s" % ["wss" if proto == "https:" else "ws", host]


## This player's id: random, kept, and the only "account" there is.
static func playerId() -> String:
	var KEY := "bb_pid"
	var have = Storage.getItem(KEY)
	if have is String and RegEx.create_from_string("^[a-f0-9]{32}$").search(have) != null:
		return have
	var id := Crypto.new().generate_random_bytes(16).hex_encode()
	Storage.setItem(KEY, id)
	return id


## Forgiving normalization of the server address a player types. Accepts a bare
## IP/host, adds ws://, and supplies the default port — so "26.13.45.201",
## "26.13.45.201:8787" and "ws://26.13.45.201:8787" all work.
static func normalizeWsUrl(raw) -> String:
	var s := String(raw if raw != null else "").strip_edges()
	if s == "":
		return ""
	if RegEx.create_from_string("(?i)^wss?://").search(s) == null:
		s = "ws://" + s
	# Add the default port if none was given (after the host, before any path).
	return RegEx.create_from_string("(?i)^(wss?://[^/:]+)(/|$)").sub(s, "$1:8787$2")


# ------------------------------------------------------------------ state --

## TS `el`: the overlay. True while the lobby is open.
var el := false
var link = null  # PeerLink in the TS (serverless 1v1); never set here
var ws = null  # WSLink
var slot := -1
var hostSlot := -1
var players: Array = []  # of LobbyPlayer {slot, name, side, team?, faction?, ready, observer?}
var layout := 2
var map: Dictionary = {"id": "open_plains", "name": "Open Plains"}  # LobbyMap
var community: Array = []  # of PoolMap {id, name, author, desc, minPlayers, maxPlayers, cols, published}
var picking := false
var faction := ""
var statusLine := ""
var onStart: Callable

var opts: Dictionary = {}  # LobbyOptions {name?, factions?, faction?}

var view := "home"  # "home" | "hub" | "room" | "picker" | "other"
var serverUrl := ""
var fromHub := false
var rooms: Array = []  # of RoomSummary
var online := 0
var meRec = null  # PlayerSummary or null
var queue = null  # {mode, waiting, need} or null
var top: Array = []  # of PlayerSummary
var joiningLocked := ""
var chat: Array = []  # of {name, text, mine?}
var chatDraft := ""
var countdownAt := 0.0
var ranked := false
var quickRoom := ""
var locked := false
var maxPlayers := 16

var observing := false
var room := "main"
## A caster's broadcast delay (seconds): the stream trails the match so it can't be used to cheat.
var delaySec := 0

var pendingUse := ""
var pendingKeep := ""

# What the TS keeps in its DOM: which screen is up, the text in its fields,
# the status line under a connect form, and the create-room size picker.
## The screen on show: "home" | "onlineConnect" | "connecting" | "onlineFailed"
## | "hub" | "serverConnect" | "room" | "picker" | "disconnected" | "version"
## | "p2pHome" | "p2pUnavailable".
var page := "home"
var fields: Dictionary = {}  # field id -> text
var editing = null  # the focused field's id, or null
var pageStatus := ""  # the status line of a connect form
var pageMsg := ""  # the message on a failure screen
var createMax := 8  # TS: `let max = 8` in renderHub
var _status: Callable  # the status callback handed to openSocket
## Every button, chip and field drawn last frame, in screen pixels:
## {kind, label, x, y, w, h, disabled}. What a click lands on, and what the
## tests click.
var drawn: Array = []
var _scroll := 0.0
var _contentH := 0.0
var _live := false  # false on the measuring pass: draw nothing, click nothing
var _clicked := false  # a click was used this frame
var _time := 0.0
var _clipY0 := 0.0
var _clipY1 := 0.0


## Open the lobby. `onStart(start: Dictionary)` is called with a NetStart.
func open(onStart_: Callable, opts_: Dictionary = {}) -> void:
	onStart = onStart_
	opts = opts_
	var f = opts.get("faction")
	if f is String and f != "" and (opts.get("factions") == null or (opts.factions as Array).has(f)):
		faction = f
	if el:
		return  # already open — never stack a second overlay
	build()
	showHome()


## After an online match: straight back to that server's hub.
func reopenHub(onStart_: Callable, url: String, opts_: Dictionary = {}) -> void:
	open(onStart_, opts_)
	goOnline(url)


func close() -> void:
	el = false
	editing = null


# --- DOM helpers ---------------------------------------------------------
func build() -> void:
	el = true
	_scroll = 0.0


func ls(key: String, fallback: String) -> String:
	var v = Storage.getItem(key)
	return v if v is String and v != "" else fallback


func save(key: String, v: String) -> void:
	Storage.setItem(key, v)


func _show(p: String, newFields: Dictionary = {}) -> void:
	page = p
	fields = newFields
	editing = null
	pageStatus = ""


# --- Home ----------------------------------------------------------------
func showHome() -> void:
	view = "home"
	_show("home")


## No site to talk to (the game was opened from a file): ask where the server is.
func showOnlineConnect() -> void:
	_show("onlineConnect", {"url": ls(LS_URL, "")})


# --- The online hub --------------------------------------------------------

func myName() -> String:
	return ls(LS_NAME, String(opts.get("name", "Player")) if opts.get("name") else "Player").substr(0, 24)


## Connect to a server's hub (or reuse the connection we have).
func goOnline(url: String) -> void:
	fromHub = true
	serverUrl = url
	view = "hub"
	if ws != null and ws.connected:
		ws.send({"t": "hub", "name": myName(), "pid": playerId(), "v": PROTOCOL})
		renderHub()
		return
	_show("connecting")
	pageMsg = "Connecting to %s…" % _stripScheme(url)
	openSocket(url, _sendHub, _onlineFailed)


func _sendHub() -> void:
	ws.send({"t": "hub", "name": myName(), "pid": playerId(), "v": PROTOCOL})


## goOnline's status: the connection failed — say so, offer another go.
func _onlineFailed(msg: String) -> void:
	_show("onlineFailed")
	pageMsg = msg


static func _stripScheme(url: String) -> String:
	return RegEx.create_from_string("^wss?://").sub(url, "")


func renderHub() -> void:
	view = "hub"
	if page != "hub":
		_show("hub", {"name": myName(), "room": "", "pass": "", "joinPass": ""})
		createMax = 8


# --- Server flow (by address) ----------------------------------------------
func showServerConnect() -> void:
	view = "other"
	_show("serverConnect", {"url": ls(LS_URL, "ws://localhost:8787"), "name": myName(), "room": "main", "pass": ""})


## The Connect / Observe buttons on the server form (TS: `go` in showServerConnect).
func _serverGo(observer: bool) -> void:
	var wsUrl := normalizeWsUrl(fields.get("url", ""))
	if wsUrl == "":
		pageStatus = "Enter the server address (e.g. ws://26.13.45.201:8787)."
		return
	save(LS_URL, fields.get("url", ""))
	save(LS_NAME, fields.get("name", ""))
	pageStatus = "Connecting%s to %s…" % [" as observer" if observer else "", wsUrl]
	var nm: String = String(fields.get("name", "")).strip_edges()
	var rm: String = String(fields.get("room", "")).strip_edges()
	connectServer(wsUrl, nm if nm != "" else ("Observer" if observer else "Player"), rm if rm != "" else "main", fields.get("pass", ""), observer, _setPageStatus)


func _setPageStatus(msg: String) -> void:
	pageStatus = msg


var _hello: Dictionary = {}


func connectServer(url: String, name: String, room_: String, pass_: String, observer: bool, status: Callable) -> void:
	fromHub = false
	observing = observer
	room = room_
	_hello = {"t": "hello", "name": name, "room": room_, "pass": pass_, "observer": observer, "faction": faction, "pid": playerId()}
	openSocket(url, _sendHello, status)


func _sendHello() -> void:
	ws.send(_hello)


## One socket, one message handler, whichever way in.
##
## The socket's callbacks are this lobby's own methods (a method Callable
## holds no reference; a lambda would, and lobby → socket → lambda → lobby
## is a cycle reference counting never frees). Only the socket in `ws` is
## ever polled, so its events are always the current socket's — the TS's
## `if (this.ws !== ws) return` guard comes for free.
func openSocket(url: String, onOpen: Callable, status: Callable) -> void:
	if ws != null:
		ws.close()
	var link_ := WSLink.new(url)
	ws = link_
	_status = status
	link_.onOpen = onOpen
	link_.onClose = _onSocketClose
	link_.onData = _onSocketData


func _onSocketClose() -> void:
	_status.call("Disconnected. Check the address and that the server is running.")
	if view == "room" or view == "hub" or view == "picker":
		_show("disconnected")
		view = "other"


func _onSocketData(raw) -> void:
	onMessage(ws, raw, _status)


## Read the socket (WebSocketPeer is polled). `draw` calls this every frame.
func poll() -> void:
	if ws != null:
		ws.poll()


func onMessage(ws_, raw, status: Callable) -> void:
	if not (raw is Dictionary):
		return
	var m: Dictionary = raw
	match m.get("t"):
		"rooms":
			rooms = m.get("rooms", []) if m.get("rooms") != null else []
			if m.has("online"):
				online = int(m.online)
			if m.has("me"):
				meRec = m.me
			if top.is_empty():
				ws_.send({"t": "leaderboard", "n": 10})
			if view == "hub" or view == "home" or view == "other":
				if fromHub:
					renderHub()
		"leaderboard":
			top = m.get("top", []) if m.get("top") != null else []
			if m.get("me"):
				meRec = m.me
			if view == "hub":
				renderHub()
		"queue":
			queue = {"mode": m.mode, "waiting": int(m.get("waiting", 0)), "need": int(m.get("need", 0))} if m.get("mode") else null
			if view == "hub":
				renderHub()
		"welcome":
			slot = int(m.slot)
			room = m.get("room", room) if m.get("room") != null else room
			observing = bool(m.get("observer", false))
			chat = []
			queue = null
			joiningLocked = ""
			statusLine = ""
			view = "room"
			ws_.send({"t": "maps"})
		"lobby":
			hostSlot = int(m.host)
			players = m.players
			if m.has("layout"):
				layout = int(m.layout)
			if m.get("map"):
				map = m.map
			ranked = bool(m.get("ranked", false))
			quickRoom = String(m.get("quick", "")) if m.get("quick") else ""
			locked = bool(m.get("locked", false))
			if m.get("max"):
				maxPlayers = int(m.max)
			if view == "room" or view == "picker":
				render()
		"left":
			view = "hub"
			var why = m.get("why")
			statusLine = "The host removed you from that room." if why == "kicked" else ("That room was closed by the server." if why == "closed" else "")
			renderHub()
		"countdown":
			countdownAt = JS.now_ms() + float(m.get("ms", 4000))
			render()
		"cancelled":
			countdownAt = 0.0
			statusLine = String(m.get("msg", "The match was called off.")) if m.get("msg") != null else "The match was called off."
			ws_.send({"t": "leave"})
		"chat":
			chat.append({"name": String(m.get("name", "?")) if m.get("name") != null else "?", "text": String(m.get("text", "")) if m.get("text") != null else ""})
			if chat.size() > 60:
				chat.pop_front()
		"announce":
			statusLine = "📣 %s" % (String(m.get("text", "")) if m.get("text") != null else "")
			if view == "hub":
				renderHub()
			elif view == "room" or view == "picker":
				render()
		"maps":
			community = m.get("maps", []) if m.get("maps") != null else []
			if picking:
				render()
		"published":
			statusLine = "Published \"%s\" to this server." % (m.map.name if m.get("map") is Dictionary else "undefined")
			render()
		"mapcode":
			onMapCode(String(m.id), String(m.code))
		"error":
			var msg: String = String(m.get("msg", "")) if m.get("msg") else ""
			status.call(msg if msg != "" else "Server refused the connection.")
			statusLine = msg
			if m.get("code") == "version":
				_show("version")
				pageMsg = msg
				view = "other"
			elif view == "room" or view == "picker":
				render()
			elif view == "hub":
				renderHub()
		"start":
			var teams: Array = []
			for i in int(m.numTeams):
				teams.append(i)
			var mine = null
			for st in m.slotTeams:
				if int(st.slot) == slot:
					mine = st
					break
			var common := {
				"transport": ws_, "teams": teams, "alliances": (m.alliances as Array).map(func(a): return int(a)), "seed": int(m.seed),
				"numTeams": int(m.numTeams), "factions": m.get("factions"), "map": m.get("map"), "ranked": bool(m.get("ranked", false)),
				"online": serverUrl if fromHub else null, "names": m.get("names"), "delaySec": delaySec if observing else 0,
			}
			countdownAt = 0.0
			# Not assigned a team → we joined to observe.
			if mine == null:
				common.localTeam = 0
				common.observer = true
			else:
				common.localTeam = int(mine.team)
			finish(common)


func render() -> void:
	if picking:
		renderMapPicker()
	else:
		renderRoom()


func renderRoom() -> void:
	if page != "room":
		_show("room", {"chat": chatDraft})


func renderMapPicker() -> void:
	if page != "picker":
		_show("picker")


## A community map's code arrived: use it for the match, or keep a copy.
func onMapCode(id: String, code: String) -> void:
	var m = CustomMaps.deserialiseMap(code)
	var meta = null
	for c in community:
		if c.id == id:
			meta = c
			break
	if m == null:
		statusLine = "That map didn't load."
		render()
		return
	if pendingUse == id:
		var author: String = m.get("author", "") if m.get("author") else (meta.author if meta != null else "")
		ws.send({"t": "map", "map": {"id": id, "name": m.name, "author": author, "minPlayers": m.minPlayers, "maxPlayers": m.maxPlayers, "code": code}})
		picking = false
	if pendingKeep == id:
		# A kept community map goes straight into your own pool: it was already
		# published by its author.
		if not m.get("author") and meta != null and meta.get("author"):
			m.author = meta.author
		CustomMaps.publishCustomMap(m)
		if not CustomMaps.isPublished(m):
			CustomMaps.saveCustomMap(m)
		statusLine = "Saved \"%s\" to your maps." % m.name
	pendingUse = ""
	pendingKeep = ""
	render()


func publishable(m: Dictionary, code: String) -> Dictionary:
	var author: String = m.author if m.get("author") else ls(LS_NAME, "Player")
	return {"code": code, "name": m.name, "author": author, "desc": m.get("desc", ""), "minPlayers": m.minPlayers, "maxPlayers": m.maxPlayers, "cols": m.cols}


# --- P2P (serverless 1v1) flow ------------------------------------------
func showP2PHome() -> void:
	_show("p2pHome")


func p2pHost() -> void:
	_show("p2pUnavailable")


func p2pJoin() -> void:
	_show("p2pUnavailable")


# --- exit ----------------------------------------------------------------
func cancel() -> void:
	if link != null:
		link.close()
	if ws != null:
		ws.close()
	link = null
	ws = null
	close()


func finish(s: Dictionary) -> void:
	var cb := onStart
	ws = null  # the match owns the connection now
	view = "home"
	close()
	if cb.is_valid():
		cb.call(s)


# ================================================================ keys ==

## Typing into the focused field. True when the lobby consumed the key —
## while it is open, a focused field takes every key (the DOM input did).
func handleKey(key: String) -> bool:
	if not el:
		return false
	if editing == null:
		return false
	if key == "Escape":
		editing = null
		return true
	if key == "Enter":
		if editing == "chat":
			_sendChat()
		return true
	if key == "Tab":
		return true
	var v: String = fields.get(editing, "")
	if key == "Backspace":
		fields[editing] = v.substr(0, maxi(0, v.length() - 1))
	elif key.length() == 1:
		var cap := 240 if editing == "chat" else 512
		if v.length() < cap:
			fields[editing] = v + key
	if editing == "chat":
		chatDraft = fields.get("chat", "")
	return true


## Ctrl+V into the focused field.
func handlePaste(text: String) -> bool:
	if not el or editing == null:
		return false
	for ch in text:
		if ch != "\n" and ch != "\r":
			handleKey(ch)
	return true


func _sendChat() -> void:
	var me = _me()
	var text := String(fields.get("chat", "")).strip_edges()
	if text == "":
		return
	var nm: String = me.name if me != null else myName()
	if ws != null:
		ws.send({"t": "chat", "text": text, "name": nm})
	chat.append({"name": nm, "text": text, "mine": true})
	chatDraft = ""
	fields["chat"] = ""


func _me():
	for pl in players:
		if int(pl.slot) == slot:
			return pl
	return null


# ============================================================== drawing ==
#
# The DOM, by hand. Sizes are the browser's: line boxes from the font's own
# metrics (Liberation Sans/Serif, what the port's fonts fall back to), the
# TS's paddings, margins and borders, margins collapsing between blocks.

var ctx: Ctx
var _x0 := 0.0  # content box left, screen
var _top := 0.0  # content box top, screen (already scrolled)
var _cw := 760.0  # content width
var _y := 0.0  # block cursor, relative to the content top
var _pend := 0.0  # a collapsed margin waiting for the next block


## The line box of a font size: [above baseline, below]. Chromium's "normal"
## line height: rounded ascent + descent + line gap, the gap split around.
static func _lm(size: float, serif := false) -> Array:
	var A := roundi((0.891 if serif else 0.905) * size)
	var D := roundi((0.216 if serif else 0.212) * size)
	var G := roundi((0.042 if serif else 0.0327) * size)
	var h := G / 2
	return [A + h, D + G - h]


static func _lh(size: float, serif := false) -> int:
	var m := _lm(size, serif)
	return m[0] + m[1]


## How much taller a symbol from a fallback font makes a line (measured in
## the browser for the ones the lobby uses).
static func _bump(s: String) -> int:
	if s.contains("＋"):
		return 3
	if s.contains("🔒") or s.contains("✅") or s.contains("⬜") or s.contains("⚔") or s.contains("✕") or s.contains("▸") or s.contains("🖧") or s.contains("⚡"):
		return 1
	return 0


## How much of that is above the baseline: colour emoji stand taller, the
## symbols from DejaVu hang lower.
static func _bumpAbove(s: String) -> int:
	if s.contains("＋"):
		return 2
	if s.contains("🔒") or s.contains("✅") or s.contains("⬜"):
		return 1
	return 0


func _font(size: float, bold := false, italic := false, family := SANS) -> String:
	return "%s%s%spx %s" % ["italic " if italic else "", "bold " if bold else "", JS.num(size), family]


static var _widths := {}


func _tw(s: String, size: float, bold := false, italic := false, family := SANS) -> float:
	var f := _font(size, bold, italic, family)
	var key := f + "|" + s
	var w = _widths.get(key)
	if w != null:
		return w
	ctx.font = f
	w = ctx.measureText(s).width
	if _widths.size() > 4096:
		_widths.clear()
	_widths[key] = w
	return w


## Whitespace collapses in HTML: a label's double space shows as one.
static func _collapse(s: String) -> String:
	while s.contains("  "):
		s = s.replace("  ", " ")
	return s


func _text(s: String, x: float, baseline: float, size: float, color, bold := false, italic := false, family := SANS) -> float:
	var w := _tw(s, size, bold, italic, family)
	if _live:
		ctx.font = _font(size, bold, italic, family)
		ctx.fillStyle = color
		ctx.textAlign = "left"
		ctx.textBaseline = "alphabetic"
		ctx.fillText(s, x, baseline)
	return w


## A run of styled text: {t, c, b?, i?, s?, f?}.
static func R(t: String, c = null, b := false, i := false, s = null, f = null) -> Dictionary:
	return {"t": t, "c": c, "b": b, "i": i, "s": s, "f": f}


func _runW(r: Dictionary, size: float) -> float:
	return _tw(r.t, r.s if r.s != null else size, r.b, r.i, r.f if r.f != null else SANS)


func _drawRuns(runs: Array, x: float, baseline: float, size: float, color) -> float:
	var cx := x
	for r in runs:
		cx += _text(r.t, cx, baseline, r.s if r.s != null else size, r.c if r.c != null else color, r.b, r.i, r.f if r.f != null else SANS)
	return cx - x


## Greedy word wrap of runs into lines no wider than maxW.
func _wrap(runs: Array, size: float, maxW: float) -> Array:
	var total := 0.0
	for r in runs:
		total += _runW(r, size)
	if total <= maxW:
		return [runs]
	var lines: Array = [[]]
	var w := 0.0
	for r in runs:
		var parts: Array = []
		var cur := ""
		for ch in String(r.t):
			cur += ch
			if ch == " ":
				parts.append(cur)
				cur = ""
		if cur != "":
			parts.append(cur)
		for p in parts:
			var piece: Dictionary = r.duplicate()
			piece.t = p
			var pw := _runW(piece, size)
			if w + pw - (_runW(R(" "), size) if p.ends_with(" ") else 0.0) > maxW and w > 0:
				lines.append([])
				w = 0.0
			(lines[lines.size() - 1] as Array).append(piece)
			w += pw
	# Pieces of one run that share a line are drawn as one string (kerning,
	# and the browser's spacing, come with it).
	var out: Array = []
	for line in lines:
		var merged: Array = []
		for piece in line:
			if not merged.is_empty() and _sameStyle(merged[merged.size() - 1], piece):
				merged[merged.size() - 1].t += piece.t
			else:
				merged.append(piece)
		out.append(merged)
	return out


static func _sameStyle(a: Dictionary, b: Dictionary) -> bool:
	return a.c == b.c and a.b == b.b and a.i == b.i and a.s == b.s and a.f == b.f


func _on_screen(y: float, h: float) -> bool:
	return y + h > _clipY0 and y < _clipY1


func _hover(x: float, y: float, w: float, h: float) -> bool:
	var ui := UI.ui
	return _live and ui.mx >= x and ui.mx < x + w and ui.my >= y and ui.my < y + h and ui.my >= _clipY0 and ui.my < _clipY1


func _press(x: float, y: float, w: float, h: float) -> bool:
	if _clicked or not UI.ui.clicked or not _hover(x, y, w, h):
		return false
	_clicked = true
	return true


func _record(kind: String, label: String, x: float, y: float, w: float, h: float, disabled := false) -> void:
	if _live:
		drawn.append({"kind": kind, "label": label, "x": x, "y": y, "w": w, "h": h, "disabled": disabled})


func _rrect(x: float, y: float, w: float, h: float, r: float) -> void:
	ctx.beginPath()
	ctx.roundRect(x, y, w, h, r)


# --- the TS's widgets ---

## btn(): a button. Returns its box {w, h} and whether it was clicked.
func _btn(label: String, x: float, y: float, primary := true, disabled := false, alpha := 1.0) -> Dictionary:
	label = _collapse(label)
	var tw := _tw(label, 15, true)
	var w := tw + 34
	var bump := _bump(label)
	var h := float(_lh(15) + bump + 22)
	if _live and _on_screen(y, h):
		ctx.save()
		ctx.globalAlpha = alpha * (0.5 if disabled else 1.0)
		# box-shadow: 0 2px 6px rgba(0,0,0,0.4)
		ctx.shadowColor = "rgba(0,0,0,0.4)"
		ctx.shadowBlur = 6
		ctx.shadowOffsetY = 2
		var g := ctx.createLinearGradient(0, y, 0, y + h)
		if primary:
			g.addColorStop(0, "#e0bc62"); g.addColorStop(1, "#b08a36")
		else:
			g.addColorStop(0, "#3a3020"); g.addColorStop(1, "#2a2216")
		ctx.fillStyle = g
		_rrect(x, y, w, h, 8); ctx.fill()
		ctx.shadowColor = "rgba(0,0,0,0)"
		ctx.shadowBlur = 0
		ctx.shadowOffsetY = 0
		# inset 0 1px 0 rgba(255,240,200,0.18): a light line under the top border.
		ctx.strokeStyle = "rgba(255,240,200,0.18)"
		ctx.lineWidth = 1
		ctx.beginPath(); ctx.moveTo(x + 7, y + 1.5); ctx.lineTo(x + w - 7, y + 1.5); ctx.stroke()
		ctx.strokeStyle = "#e0bc62" if primary else "#5a4a2a"
		_rrect(x + 0.5, y + 0.5, w - 1, h - 1, 7.5); ctx.stroke()
		var m := _lm(15)
		_text(label, x + 17, y + 11 + m[0] + _bumpAbove(label), 15, "#1e170c" if primary else "#e8ddc4", true)
		ctx.restore()
	_record("button", label, x, y, w, h, disabled)
	return {"w": w, "h": h, "clicked": not disabled and _press(x, y, w, h)}


## chip(): a small rounded button, for teams, factions and layouts.
func _chip(label: String, x: float, y: float, on: bool, color := "#c9a24a", disabled := false, alpha := 1.0) -> Dictionary:
	var tw := _tw(label, 12.5, true)
	var w := tw + 22
	var bump := _bump(label)
	var h := float(_lh(12.5) + bump + 12)
	if _live and _on_screen(y, h):
		ctx.save()
		ctx.globalAlpha = alpha
		ctx.fillStyle = color if on else "#241d12"
		_rrect(x, y, w, h, 14); ctx.fill()
		ctx.strokeStyle = color if on else "#4a3c22"
		ctx.lineWidth = 1
		_rrect(x + 0.5, y + 0.5, w - 1, h - 1, 13.5); ctx.stroke()
		var m := _lm(12.5)
		_text(label, x + 11, y + 6 + m[0] + _bumpAbove(label), 12.5, "#15110a" if on else "#d8cdb4", true)
		ctx.restore()
	_record("chip", label, x, y, w, h, disabled)
	return {"w": w, "h": h, "clicked": not disabled and _press(x, y, w, h)}


## field(): a text input, 36 tall. Click to type into it.
func _field(id: String, x: float, y: float, w: float, placeholder := "") -> void:
	var h := 36.0
	var value: String = fields.get(id, "")
	if _live and _on_screen(y, h):
		ctx.fillStyle = "#15110a"
		_rrect(x, y, w, h, 6); ctx.fill()
		ctx.strokeStyle = "#c9a24a" if editing == id else "#5a4a2a"
		ctx.lineWidth = 1
		_rrect(x + 0.5, y + 0.5, w - 1, h - 1, 5.5); ctx.stroke()
		var base: float = y + 10 + _lm(14)[0]
		ctx.save()
		ctx.beginPath(); ctx.rect(x + 10, y + 1, w - 20, h - 2); ctx.clip()
		var tw := _tw(value, 14)
		var shift := maxf(0, tw + 2 - (w - 20)) if editing == id else 0.0
		if value == "":
			_text(placeholder, x + 10, base, 14, "#757575")
		else:
			_text(value, x + 10 - shift, base, 14, "#e8ddc4")
		if editing == id and fmod(_time, 1.0) < 0.55:
			ctx.fillStyle = "#e8ddc4"
			ctx.fillRect(x + 10 - shift + tw + 0.5, y + 9, 1, h - 18)
		ctx.restore()
	_record("field", placeholder if value == "" else value, x, y, w, h)
	if _live and UI.ui.clicked and not _clicked and _hover(x, y, w, h):
		_clicked = true
		editing = id


## heading(): small caps with a rule under. A block: 19 tall, margins 14/6.
func _heading(t: String) -> void:
	var y := _blk(14)
	var sy := _top + y
	if _live and _on_screen(sy, 19):
		var base: float = sy + _lm(12)[0]
		# letter-spacing: 1.5px — each letter where the run's own advances put
		# it, plus 1.5 per letter before it (measuring letters one by one
		# would round each advance and drift).
		var up := t.to_upper()
		for i in up.length():
			_text(up[i], _x0 + _tw(up.substr(0, i), 12, true) + 1.5 * i, base, 12, "#c9a24a", true)
		ctx.fillStyle = "#4a3c22"
		ctx.fillRect(_x0, sy + 18, _cw, 1)
	_end(19, 6)


func _title(t: String) -> void:
	var y := _blk(0)
	var sy := _top + y
	if _live and _on_screen(sy, 30):
		ctx.save()
		ctx.shadowColor = "rgba(0,0,0,0.6)"
		ctx.shadowBlur = 6
		ctx.shadowOffsetY = 2
		_text(t, _x0, sy + _lm(26, true)[0], 26, "#ffe9b0", true, false, SERIF)
		ctx.restore()
	_end(30, 4)


## note(): a paragraph of runs, 13px, margin-bottom 12.
func _note(runs: Array) -> void:
	_para(runs, 13, "#bdb49a", 0, 12)


## A block of wrapped runs at `size`, with its margins.
func _para(runs: Array, size: float, color, mt := 0.0, mb := 0.0, strut := 0.0) -> void:
	var y := _blk(mt)
	var lines := _wrap(runs, size, _cw)
	var m := _lm(maxf(size, strut))
	var lh: int = m[0] + m[1]
	for i in lines.size():
		var text := ""
		for r in lines[i]:
			text += r.t
		var b := 0
		var sy := _top + y
		if _live and _on_screen(sy, lh + b):
			_drawRuns(lines[i], _x0, sy + m[0] + ceili(b / 2.0), size, color)
		y += lh + b
	_end(y - _y, mb)


func _blk(mt: float) -> float:
	_y += maxf(_pend, mt)
	_pend = 0.0
	return _y


func _end(h: float, mb := 0.0) -> void:
	_y += h
	_pend = mb


## A line of inline-block buttons (an anonymous block), left to right.
## items: [{label, primary?, disabled?, chip?, on?, color?}]. Returns which
## was clicked (its index) or -1; the line advances the block cursor.
func _btnLine(items: Array, mt := 0.0) -> int:
	var y := _blk(mt)
	var x := _x0
	var hit := -1
	var lineH := 0.0
	var upMax := 0
	for it in items:
		upMax = maxi(upMax, _bumpAbove(it.label))
	for it in items:
		var up := upMax - _bumpAbove(it.label)
		lineH = maxf(lineH, up + ((_lh(15) + _bump(it.label) + 22 + 12) if not it.get("chip", false) else (_lh(12.5) + _bump(it.label) + 12 + 6)))
	for i in items.size():
		var it: Dictionary = items[i]
		var r: Dictionary
		if it.get("chip", false):
			r = _chip(it.label, x, _top + y + 3 + upMax - _bumpAbove(it.label), it.get("on", false), it.get("color", "#c9a24a"), it.get("disabled", false))
			x += r.w + 6
		else:
			r = _btn(it.label, x, _top + y + 6 + upMax - _bumpAbove(it.label), it.get("primary", true), it.get("disabled", false))
			x += r.w + 8
		if r.clicked:
			hit = i
	_end(lineH)
	return hit


# ------------------------------------------------------------- the frame --

## Draw the lobby over whatever is on screen, and act on this frame's input.
func draw(W: float, H: float, time: float) -> void:
	poll()
	if not el:
		return
	var ui := UI.ui
	ctx = ui.ctx
	_time = time
	# The overlay: a warm radial wash over the game.
	ctx.drawImage(_overlay(W, H), 0, 0, W, H)
	ui.pointerConsumed = true
	# The panel: 760 wide (at most 94% of the window), as tall as its content
	# up to 90% of the window, then it scrolls.
	_cw = minf(760, W * 0.94)
	var maxH := H * 0.9
	# Measure first (nothing drawn, nothing clicked), then draw for real.
	var y0 := _layoutPass(false, 0, 0, maxH)
	var contentH := _contentH
	var innerH := minf(contentH, maxH)
	var pw := _cw + 58
	var ph := innerH + 54
	var px := JS.round((W - pw) / 2)  # the browser centres, then snaps half pixels down the page
	var py := JS.round((H - ph) / 2)
	if ui.wheel != 0 and ui.mx >= px and ui.mx < px + pw and ui.my >= py and ui.my < py + ph:
		_scroll += 60.0 if ui.wheel > 0 else -60.0
	_scroll = clampf(_scroll, 0, maxf(0, contentH - innerH))
	_drawPanel(px, py, pw, ph)
	ctx.save()
	ctx.beginPath(); ctx.rect(px + 1, py + 1, pw - 2, ph - 2); ctx.clip()
	_clipY0 = py + 1
	_clipY1 = py + ph - 1
	drawn = []
	_clicked = false
	_layoutPass(true, px + 29, py + 27, maxH)
	ctx.restore()
	ctx = null
	# A click anywhere else in the panel takes the focus off a field.
	if ui.clicked and not _clicked:
		editing = null


static var _overlayTex: ImageTexture = null
static var _overlayKey := Vector2.ZERO


## The overlay's gradient, as a small texture stretched over the window (the
## filtering makes it smooth). CSS: radial-gradient(ellipse at 50% 30%,
## rgba(40,30,16,0.86), rgba(6,4,2,0.94)) — farthest-corner, so the radii are
## √2 × (W/2, 0.7H) and it reaches every corner; interpolated premultiplied,
## as the browser does.
static func _overlay(W: float, H: float) -> ImageTexture:
	var key := Vector2(W, H)
	if _overlayTex != null and _overlayKey == key:
		return _overlayTex
	var n := 96
	var img := Image.create(n, n, false, Image.FORMAT_RGBA8)
	var c0 := Color(40 / 255.0, 30 / 255.0, 16 / 255.0, 0.86)
	var c1 := Color(6 / 255.0, 4 / 255.0, 2 / 255.0, 0.94)
	var p0 := Color(c0.r * c0.a, c0.g * c0.a, c0.b * c0.a, c0.a)
	var p1 := Color(c1.r * c1.a, c1.g * c1.a, c1.b * c1.a, c1.a)
	var rx := 0.5 * W * sqrt(2.0)
	var ry := 0.7 * H * sqrt(2.0)
	for v in n:
		for u in n:
			# Texel centres, as the stretched texture samples them.
			var x := (u + 0.5) / n * W
			var y := (v + 0.5) / n * H
			var t := minf(1.0, sqrt(pow((x - W * 0.5) / rx, 2) + pow((y - H * 0.3) / ry, 2)))
			var pc := p0.lerp(p1, t)
			img.set_pixel(u, v, Color(pc.r / pc.a, pc.g / pc.a, pc.b / pc.a, pc.a))
	_overlayTex = ImageTexture.create_from_image(img)
	_overlayKey = key
	return _overlayTex


func _layoutPass(live: bool, x0: float, top: float, maxH: float) -> float:
	_live = live
	var saved := ctx.measure_only
	ctx.measure_only = not live
	_x0 = x0
	_top = top - (_scroll if live else 0.0)
	_y = 0.0
	_pend = 0.0
	var p := page
	match p:
		"home": _drawHome()
		"onlineConnect": _drawOnlineConnect()
		"connecting": _drawConnecting()
		"onlineFailed": _drawOnlineFailed()
		"hub": _drawHub()
		"serverConnect": _drawServerConnect()
		"room": _drawRoom()
		"picker": _drawMapPicker()
		"disconnected": _drawDisconnected()
		"version": _drawVersion()
		"p2pHome": _drawP2PHome()
		"p2pUnavailable": _drawP2PUnavailable()
	ctx.measure_only = saved
	if page == p and el:
		_contentH = _y + _pend
	_live = false
	return _contentH


func _drawPanel(x: float, y: float, w: float, h: float) -> void:
	# box-shadow: 0 18px 60px rgba(0,0,0,0.7) — a soft dark shadow, below.
	ctx.save()
	ctx.shadowColor = "rgba(0,0,0,0.7)"
	ctx.shadowBlur = 60
	ctx.shadowOffsetY = 18
	var g := ctx.createLinearGradient(0, y, 0, y + h)
	g.addColorStop(0, "#2c2316")
	g.addColorStop(1, "#1c160d")
	ctx.fillStyle = g
	_rrect(x, y, w, h, 12); ctx.fill()
	ctx.restore()
	# inset 0 1px 0 rgba(255,230,170,0.12)
	ctx.strokeStyle = "rgba(255,230,170,0.12)"
	ctx.lineWidth = 1
	ctx.beginPath(); ctx.moveTo(x + 11, y + 1.5); ctx.lineTo(x + w - 11, y + 1.5); ctx.stroke()
	ctx.strokeStyle = "#6a5428"
	_rrect(x + 0.5, y + 0.5, w - 1, h - 1, 11.5); ctx.stroke()
	# outline: 1px rgba(201,162,74,0.35), offset -6px.
	ctx.strokeStyle = "rgba(201,162,74,0.35)"
	_rrect(x + 5.5, y + 5.5, w - 11, h - 11, 6.5); ctx.stroke()


# --- Home ---

func _drawHome() -> void:
	_title("Multiplayer")
	_note([R("Play against real people — quick matches, custom rooms up to 8 v 8, and a ranked ladder.")])
	var labels := ["⚔  Play Online", "🖧  Join a Server by address  (2–16 players)", "⚡  Quick 1v1  (no server)", "Cancel"]
	for i in labels.size():
		var hit := _btnLine([{"label": labels[i], "primary": i == 0}])
		if hit == 0:
			match i:
				0:
					var saved := ls(LS_URL, "")
					var url := defaultServerUrl()
					if url == "" and saved != "":
						url = normalizeWsUrl(saved)
					if url != "":
						goOnline(url)
					else:
						showOnlineConnect()
				1: showServerConnect()
				2: showP2PHome()
				3: cancel()
			return
	if defaultServerUrl() == "":
		_para([R("Playing from a downloaded copy: "), R("Play Online", null, true), R(" asks for the server's address the first time.")], 12, "#8f8770", 6)


func _drawOnlineConnect() -> void:
	_title("Play Online")
	_note([R("Enter the address of a Banner & Blade server — the website you play on, e.g. "), R("wss://play.example.com", null, false, false, 13 * 13 / 16.0, MONO), R(".")])
	_inlineText("Server address")
	_fieldLine("url", "wss://play.example.com")
	var hit := _btnLine([{"label": "Connect"}, {"label": "Back", "primary": false}])
	if hit == 0:
		var u := normalizeWsUrl(fields.get("url", ""))
		if u == "":
			pageStatus = "Enter the server's address."
			return
		save(LS_URL, fields.get("url", ""))
		goOnline(u)
		return
	if hit == 1:
		showHome()
		return
	_statusDiv(pageStatus, "#e0a05a", 8)


func _drawConnecting() -> void:
	_title("Play Online")
	_note([R(pageMsg)])


func _drawOnlineFailed() -> void:
	_title("Play Online")
	_note([R(pageMsg)])
	var hit := _btnLine([{"label": "Try again"}, {"label": "Another server", "primary": false}, {"label": "Back", "primary": false}])
	if hit == 0:
		goOnline(serverUrl)
	elif hit == 1:
		showOnlineConnect()
	elif hit == 2:
		showHome()


func _drawDisconnected() -> void:
	_title("Disconnected")
	_note([R("The connection to the server was lost.")])
	var hit := _btnLine([{"label": "Reconnect"}, {"label": "Back", "primary": false}])
	if hit == 0:
		if fromHub:
			goOnline(serverUrl)
		else:
			showServerConnect()
	elif hit == 1:
		showHome()


func _drawVersion() -> void:
	_title("Update available")
	_note([R(pageMsg)])
	if _btnLine([{"label": "Reload"}]) == 0:
		# The browser reloads the page; a desktop build has to be updated by
		# hand, so all this can do is step back.
		if OS.has_feature("web"):
			JavaScriptBridge.eval("location.reload()")
		cancel()


func _drawP2PHome() -> void:
	_title("Quick 1v1 — no server")
	_note([R("One of you hosts and shares a code; the other joins and sends a code back. Use any chat to swap them.")])
	var hit := _btnLine([{"label": "Host"}, {"label": "Join", "primary": false}, {"label": "Back", "primary": false}])
	if hit == 0:
		p2pHost()
	elif hit == 1:
		p2pJoin()
	elif hit == 2:
		showHome()


func _drawP2PUnavailable() -> void:
	_title("Quick 1v1 — no server")
	_note([R("Serverless 1v1 runs over WebRTC, which only the browser version of the game has. Play Online instead — the server carries exactly the same match.")])
	var hit := _btnLine([{"label": "⚔  Play Online"}, {"label": "Back", "primary": false}])
	if hit == 0:
		var url := defaultServerUrl()
		var saved := ls(LS_URL, "")
		if url == "" and saved != "":
			url = normalizeWsUrl(saved)
		if url != "":
			goOnline(url)
		else:
			showOnlineConnect()
	elif hit == 1:
		showP2PHome()


## A bare text node in the panel's flow (16px, its own line here).
func _inlineText(t: String) -> void:
	var y := _blk(0)
	var m := _lm(16)
	_text(t, _x0, _top + y + m[0], 16, "#e8ddc4")
	_end(m[0] + m[1])


## A full-width field on its own line (margin 6 0).
func _fieldLine(id: String, placeholder: String) -> void:
	var y := _blk(0)
	_field(id, _x0, _top + y + 6, _cw, placeholder)
	_end(48)


func _statusDiv(t: String, color: String, mt: float) -> void:
	if t == "":
		_blk(mt)
		_end(0)
		return
	_para([R(t)], 13, color, mt)


func _drawServerConnect() -> void:
	_title("Join a Server")
	_note([R("Enter the address of a server (run "), R("node server/server.mjs", null, false, false, 13 * 13 / 16.0, MONO), R(" on a box your players can reach — e.g. its VPN address). Everyone uses the same room name.")])
	_inlineText("Server address")
	_fieldLine("url", "ws://10.0.0.5:8787")
	_inlineText("Display name")
	_fieldLine("name", "Your name")
	_inlineText("Room")
	_fieldLine("room", "Room name")
	_inlineText("Password (first to join sets it)")
	_fieldLine("pass", "Room password (optional)")
	var hit := _btnLine([{"label": "Connect"}, {"label": "Observe", "primary": false}, {"label": "Back", "primary": false}])
	if hit == 0:
		_serverGo(false)
	elif hit == 1:
		_serverGo(true)
	elif hit == 2:
		showHome()
		return
	_statusDiv(pageStatus, "#e0a05a", 8)


# --- The hub ---

func _drawHub() -> void:
	var host := RegEx.create_from_string(":\\d+$").sub(_stripScheme(serverUrl), "")
	_title("Play Online")
	var runs := [R("● %d online" % online, "#9fd08a", true), R(" · %s" % host)]
	if meRec != null:
		runs.append_array([R(" · your rating "), R(str(int(meRec.rating)), "#e8c060", true), R(" (%d–%d)" % [int(meRec.wins), int(meRec.losses)])])
	_note(runs)

	# Your name, kept between visits.
	if true:
		var y := _blk(0)
		var sy := _top + y
		var m := _lm(16)
		var nw := _text("Name", _x0, sy + 15 + m[0], 16, "#e8ddc4")
		var fx := _x0 + nw + 8
		_field("name", fx, sy + 6, 240, "Your name")
		var c := _chip("Save name", fx + 248, sy + 11, false)
		if c.clicked:
			var n := String(fields.get("name", "")).strip_edges().substr(0, 24)
			if n != "":
				save(LS_NAME, n)
				if ws != null:
					ws.send({"t": "rename", "name": n})
		_end(48)

	# Quick match: ranked, matched by the server.
	_heading("Quick match — ranked")
	if queue != null and queue.get("mode"):
		var q: String = QUICK_LABELS.get(queue.mode, queue.mode)
		var y := _blk(0)
		var sy := _top + y
		var rowH := 73.0
		if _live and _on_screen(sy, rowH):
			ctx.fillStyle = "#15110a"
			_rrect(_x0, sy, _cw, rowH, 8); ctx.fill()
			ctx.strokeStyle = "#6a5428"; ctx.lineWidth = 1
			_rrect(_x0 + 0.5, sy + 0.5, _cw - 1, rowH - 1, 7.5); ctx.stroke()
		var ty := sy + 11 + (51 - 36) / 2.0
		_text("Searching for a %s…" % q, _x0 + 15, ty + 14, 16, "#ffe9b0", true)
		_text("%d of %d players waiting" % [int(queue.waiting), int(queue.need)], _x0 + 15, ty + 18 + 14, 12, "#a89f88")
		var bw := _tw("Cancel", 15, true) + 34
		var b := _btn("Cancel", _x0 + _cw - 15 - 8 - bw, sy + 11 + 6, false)
		_end(rowH)
		if b.clicked and ws != null:
			ws.send({"t": "unquick"})
	else:
		var items: Array = []
		var modes: Array = QUICK_LABELS.keys()
		for mode in modes:
			items.append({"label": QUICK_LABELS[mode], "primary": mode == "1v1"})
		var hit := _btnLine(items)
		if hit >= 0 and ws != null:
			ws.send({"t": "quick", "mode": modes[hit], "name": myName(), "pid": playerId(), "faction": faction, "v": PROTOCOL})
		_para([R("Matched with whoever is waiting, on a random battlefield. Wins and losses move your rating.")], 12, "#8f8770")

	# Open rooms.
	_heading("Rooms (%d)" % rooms.size())
	if rooms.is_empty():
		_para([R("No rooms yet — create one below and invite your friends.")], 13, "#8f8770", 4, 8)
	for r in rooms:
		_roomRow(r)

	# Create a room.
	_heading("Create a room")
	if true:
		var y := _blk(0)
		var sy := _top + y
		_field("room", _x0, sy, 220, "%s's room" % myName())
		_field("pass", _x0 + 228, sy, 170, "Password (optional)")
		var x := _x0 + 228 + 170 + 8
		var m := _lm(16)
		x += _text("Players:", x, sy + 9 + m[0], 16, "#e8ddc4") + 8
		for n in [2, 4, 6, 8, 16]:
			var c := _chip("16 (8v8)" if n == 16 else str(n), x, sy + 5, createMax == n)
			x += c.w + 6
			if c.clicked:
				createMax = n
		x += 8
		var cw := _tw("Create", 15, true) + 34
		var cy := sy
		var lineH := 36.0
		if x + cw + 8 > _x0 + _cw:
			x = _x0
			cy = sy + 36 + 8 + 6
			lineH = 36 + 8 + 51
		else:
			cy = sy + (36 - 51) / 2.0 + 6
		var b := _btn("Create", x, cy, true)
		if b.clicked and ws != null:
			ws.send({"t": "create", "room": String(fields.get("room", "")).strip_edges(), "pass": fields.get("pass", ""), "max": createMax, "name": myName(), "pid": playerId(), "faction": faction, "v": PROTOCOL})
		_end(lineH)

	# The ladder.
	if not top.is_empty():
		_heading("Leaderboard")
		var y := _blk(0)
		var list := top.slice(0, 10)
		var perCol := ceili(list.size() / 2.0)
		var colW := (_cw - 24) / 2
		for i in list.size():
			var pl: Dictionary = list[i]
			var col := i / perCol
			var cx := _x0 + col * (colW + 24)
			var ry := _top + y + (i % perCol) * 19
			var base: float = ry + 2 + _lm(13)[0]
			_text("%d. %s" % [i + 1, pl.name], cx, base, 13, "#e8ddc4")
			var rw := _tw("%d " % int(pl.rating), 13) + _tw("%d–%d" % [int(pl.wins), int(pl.losses)], 13)
			var rx := cx + colW - rw
			rx += _text("%d " % int(pl.rating), rx, base, 13, "#e8c060")
			_text("%d–%d" % [int(pl.wins), int(pl.losses)], rx, base, 13, "#8f8770")
		_end(perCol * 19)

	_blk(12)
	_end(0)
	if _btnLine([{"label": "Back", "primary": false}]) == 0:
		if ws != null:
			ws.close()
		ws = null
		showHome()
		return
	if statusLine != "":
		_para([R(statusLine)], 13, "#e0a070", 6)


func _roomRow(r: Dictionary) -> void:
	var y := _blk(4)
	var sy := _top + y
	var nameT := ("🔒 " if r.locked else "") + String(r.name)
	var b1 := _bump(nameT)
	var contentH := 36.0 + b1
	var rowH := contentH + 16
	if _live and _on_screen(sy, rowH):
		ctx.fillStyle = "#15110a"
		_rrect(_x0, sy, _cw, rowH, 6); ctx.fill()
		ctx.strokeStyle = "#3a2f1c"; ctx.lineWidth = 1
		_rrect(_x0 + 0.5, sy + 0.5, _cw - 1, rowH - 1, 5.5); ctx.stroke()
	var lx := _x0 + 11
	var ly := sy + 8
	var base1 := ly + 14 + ceili(b1 / 2.0)
	var nw := _text(nameT, lx, base1, 16, "#f2e8d0", true)
	if r.get("ranked"):
		_text(" RANKED", lx + nw, base1, 11, "#e8c060")
	var sub := "host %s · %s%s" % [r.host, r.map, (" · %s" % r.format) if String(r.get("format", "")) != "" else ""]
	_text(sub, lx, ly + 18 + b1 + 14, 11.5, "#a89f88")
	# The right-hand side, laid out from the right edge.
	var started: bool = r.get("started", false)
	var statusT := "In battle" if started else "%d/%d" % [int(r.players), int(r.max)]
	var statusC := "#e0a070" if started else "#9fd08a"
	var items: Array = []  # [kind, label, on]
	var joinable: bool = not started and int(r.players) < int(r.max)
	var withPw: bool = joinable and r.locked and joiningLocked == r.name
	if joinable:
		if withPw:
			items = [["field"], ["chip", "Join", true]]
		else:
			items = [["chip", "Join", false]]
			if not r.locked:
				items.append(["chip", "Watch", false])
	var widths: Array = []
	var total := _tw(statusT, 13)
	for it in items:
		var w: float = 130.0 if it[0] == "field" else _tw(it[1], 12.5, true) + 22 + 6
		widths.append(w)
		total += 10 + w
	var rightH := 36.0 if withPw else (32.0 if not items.is_empty() else 15.0)
	var cy := ly + (contentH - rightH) / 2
	var x := _x0 + _cw - 11 - total
	x += _text(statusT, x, ly + (contentH - 15) / 2 + _lm(13)[0], 13, statusC) + 10
	for i in items.size():
		var it: Array = items[i]
		if it[0] == "field":
			_field("joinPass", x, cy, 130, "Password")
		else:
			var c := _chip(it[1], x, cy + (rightH - 26) / 2.0, it[2])
			if c.clicked and ws != null:
				if withPw:
					ws.send({"t": "join", "room": r.name, "pass": fields.get("joinPass", ""), "name": myName(), "pid": playerId(), "faction": faction, "v": PROTOCOL})
				elif it[1] == "Join":
					if r.locked:
						joiningLocked = r.name
						fields["joinPass"] = ""
					else:
						ws.send({"t": "join", "room": r.name, "name": myName(), "pid": playerId(), "faction": faction, "v": PROTOCOL})
				else:
					ws.send({"t": "join", "room": r.name, "observer": true, "pass": "", "name": myName(), "pid": playerId(), "v": PROTOCOL})
		x += widths[i] + 10
	_end(rowH, 4)


# --- A room ---

func _teamOf(p: Dictionary) -> int:
	return int(p.team) if p.get("team") != null else int(p.side) + 1


func _drawRoom() -> void:
	var isHost := slot == hostSlot
	var plist: Array = players.filter(func(p): return not p.get("observer", false))
	var obsCount := players.size() - plist.size()
	var tl: Array = plist.map(func(p): return _teamOf(p))
	var format := Teams.formatLabel(tl) if plist.size() >= 2 else "waiting for players"
	var sideSet := {}
	for p in plist:
		sideSet[("t%d" % _teamOf(p)) if _teamOf(p) > 0 else ("s%d" % int(p.slot))] = true
	var sides := sideSet.size()
	var quick := quickRoom != ""
	_title("Lobby — %d player%s%s" % [plist.size(), "" if plist.size() == 1 else "s", (" · %d 👁" % obsCount) if obsCount else ""])
	var tail: String
	if quick:
		tail = "Matched by the server — the battle starts on its own."
	elif observing:
		tail = "You're observing — the match starts when the host begins."
	elif isHost:
		tail = "You're the host ⭐ — set the teams and the battlefield, then start."
	else:
		tail = "Waiting for the host to start…"
	var runs := [R("Room "), R(room, null, true), R(" 🔒" if locked else "")]
	if ranked:
		runs.append_array([R(" · "), R("RANKED", "#e8c060", true)])
	runs.append_array([R(" · "), R(format, "#e2c889", true), R(" · " + tail)])
	_note(runs)
	var me = _me()
	if countdownAt:
		var y := _blk(0)
		var sy := _top + y
		var h := float(_lh(22, true) + 22)
		if _live and _on_screen(sy, h):
			ctx.fillStyle = "#15110a"
			_rrect(_x0, sy, _cw, h, 8); ctx.fill()
			ctx.strokeStyle = "#6a5428"; ctx.lineWidth = 1
			_rrect(_x0 + 0.5, sy + 0.5, _cw - 1, h - 1, 7.5); ctx.stroke()
			var left := maxi(0, ceili((countdownAt - JS.now_ms()) / 1000.0))
			var t := ("The battle begins in %d…" % left) if left > 0 else "To battle!"
			var tw := _tw(t, 22, false, false, SERIF)
			_text(t, _x0 + (_cw - tw) / 2, sy + 11 + _lm(22, true)[0], 22, "#ffe9b0", false, false, SERIF)
		_end(h, 8)

	# Battlefield.
	_heading("Battlefield")
	if true:
		var y := _blk(0)
		var sy := _top + y
		var withBtn := isHost and not quick
		var contentH := 51.0 if withBtn else 32.0
		var h := contentH + 22
		if _live and _on_screen(sy, h):
			ctx.fillStyle = "#15110a"
			_rrect(_x0, sy, _cw, h, 8); ctx.fill()
			ctx.strokeStyle = "#4a3c22"; ctx.lineWidth = 1
			_rrect(_x0 + 0.5, sy + 0.5, _cw - 1, h - 1, 7.5); ctx.stroke()
		var ly := sy + 11 + (contentH - 32) / 2
		_text(String(map.get("name", "")), _x0 + 15, ly + 14, 16, "#ffe9b0", true)
		var sub := ("by %s" % map.author) if map.get("author") else "built-in battlefield"
		if map.get("maxPlayers"):
			sub += " · %d–%d players" % [int(map.get("minPlayers", 0)), int(map.maxPlayers)]
		_text(sub, _x0 + 15, ly + 18 + _lm(12)[0], 12, "#a89f88")
		if withBtn:
			var bw := _tw("Change…", 15, true) + 34
			var b := _btn("Change…", _x0 + _cw - 15 - 8 - bw, sy + 11 + 6, false)
			if b.clicked:
				picking = true
				if ws != null:
					ws.send({"t": "maps"})
				render()
				_end(h)
				return
		_end(h)

	# Teams: a card per team, in order, with its players.
	_heading("Teams")
	if isHost and not quick:
		var y := _blk(0)
		var sy := _top + y
		var x := _x0 + _text("Layout: ", _x0, sy + 3 + 6 + _lm(12.5)[0], 16, "#e8ddc4")
		for kl in [[0, "Free-for-all"], [2, "2 teams"], [3, "3 teams"], [4, "4 teams"]]:
			var c := _chip(kl[1], x, sy + 3, layout == kl[0])
			x += c.w + 6
			if c.clicked and ws != null:
				ws.send({"t": "layout", "teams": kl[0]})
		_end(32, 6)
	var usedSet := {}
	for p in plist:
		if _teamOf(p) > 0:
			usedSet[_teamOf(p)] = true
	var used: Array = usedSet.keys()
	used.sort()
	var shownSet := {}
	if layout > 0:
		for i in layout:
			shownSet[i + 1] = true
		for t in used:
			shownSet[t] = true
	var shown: Array
	if layout > 0:
		shown = shownSet.keys()
		shown.sort()
	else:
		shown = used.duplicate()
	if plist.any(func(pl): return _teamOf(pl) == 0) or layout == 0:
		shown.append(0)
	var cards: Array = []  # [kind, t]
	for t in shown:
		cards.append(["team", t])
	var addNext := -1
	if not observing and not quick and me != null and shown.size() < Teams.MAX_TEAMS + 1:
		var mx := 0
		for t in shown:
			mx = maxi(mx, t)
		var next := mini(Teams.MAX_TEAMS, mx + 1)
		if not shown.has(next):
			addNext = next
			cards.append(["add", next])
	# The grid: repeat(auto-fill, minmax(210px, 1fr)), gap 10.
	var cols := maxi(1, floori((_cw + 10) / 220.0))
	var colW := (_cw - (cols - 1) * 10) / cols
	var gy := _blk(0)
	var maxT := 2
	maxT = maxi(maxT, layout)
	for t in used:
		maxT = maxi(maxT, t)
	var r := 0
	while r * cols < cards.size():
		var rowCards := cards.slice(r * cols, mini(cards.size(), (r + 1) * cols))
		var rowH := 0.0
		for c in rowCards:
			rowH = maxf(rowH, _cardH(c, plist, me, quick))
		for i in rowCards.size():
			var c: Array = rowCards[i]
			var cx := _x0 + i * (colW + 10)
			var cy := _top + gy
			if c[0] == "team":
				if _teamCard(c[1], cx, cy, colW, rowH, plist, me, isHost, quick, maxT):
					return
			else:
				if _addCard(c[1], cx, cy, colW, rowH):
					return
		gy += rowH + (10 if (r + 1) * cols < cards.size() else 0)
		r += 1
	_end(gy - _y)

	# Casting: how far behind the live match to watch.
	if observing:
		_heading("Caster delay")
		var items: Array = []
		var secs := [0, 30, 60, 120, 300]
		var labels := ["Live", "30 s", "1 min", "2 min", "5 min"]
		for i in secs.size():
			items.append({"label": labels[i], "chip": true, "on": delaySec == secs[i]})
		var hit := _btnLine(items)
		if hit >= 0:
			delaySec = secs[hit]
		_para([R("Streaming a match? A delay stops anyone watching your stream from seeing their opponent's army. You still get the full caster view.")], 12, "#8f8770", 4)

	# Your realm.
	if not observing:
		_heading("Your faction")
		var mineF: String = me.get("faction", "") if me != null and me.get("faction") != null else faction
		var y := _blk(0)
		var lineH := 32.0
		var owns := func(id): return opts.get("factions") == null or (opts.factions as Array).has(id)
		for id in C.FACTION_IDS:
			if not owns.call(id):
				lineH = 33.0
		var x := _x0
		var rowY := 0.0
		var c := _chip("Random", x, _top + y + rowY + 3 + (lineH - 32), mineF == "")
		x += c.w + 6
		if c.clicked:
			faction = ""
			if ws != null:
				ws.send({"t": "faction", "faction": ""})
		for id in C.FACTION_IDS:
			var owned: bool = owns.call(id)
			var fdef: Dictionary = C.FACTIONS[id]
			var label := ("" if owned else "🔒 ") + String(fdef.name).trim_prefix("The ")
			var w := _tw(label, 12.5, true) + 22
			if x + w > _x0 + _cw:
				x = _x0
				rowY += lineH
			var bump := _bump(label)
			var ch := _chip(label, x, _top + y + rowY + 3 + (lineH - 32) - bump, mineF == id, fdef.color, not owned, 1.0 if owned else 0.45)
			if not owned and _hover(x, _top + y + rowY + 3, ch.w, ch.h):
				UI.ui.hoveredTooltip = {"text": ["Unlock it in the Factions book"], "x": UI.ui.mx, "y": _top + y + rowY}
			x += ch.w + 6
			if ch.clicked:
				faction = id
				if ws != null:
					ws.send({"t": "faction", "faction": id})
		_end(rowY + lineH)

	# Actions.
	if true:
		var y := _blk(16)
		var sy := _top + y
		if _live and _on_screen(sy, 1):
			ctx.fillStyle = "#4a3c22"
			ctx.fillRect(_x0, sy, _cw, 1)
		_end(11)
		var items: Array = []
		var acts: Array = []
		if not observing and not quick:
			var ready: bool = me != null and me.get("ready", false)
			items.append({"label": "Unready" if ready else "Ready", "primary": not ready})
			acts.append("ready")
		if isHost and not quick:
			items.append({"label": "⚔ Start Match", "disabled": plist.size() < 2 or sides < 2})
			acts.append("start")
		items.append({"label": "Leave room" if fromHub else "Leave", "primary": false})
		acts.append("leave")
		var hit := _btnLine(items)
		if hit >= 0:
			match acts[hit]:
				"ready":
					if ws != null:
						ws.send({"t": "ready", "ready": not (me != null and me.get("ready", false))})
				"start":
					if ws != null:
						ws.send({"t": "start"})
				"leave":
					if fromHub:
						if ws != null:
							ws.send({"t": "leave"})
					else:
						cancel()
						return
		if isHost and plist.size() >= 2 and sides < 2:
			_para([R("Everyone is on one team — split into at least two sides.")], 13, "#e0786a", 6)
		if statusLine != "":
			_para([R(statusLine)], 13, "#9fd08a", 6)

	# Room chat.
	_heading("Chat")
	_chatLog()
	if true:
		var y := _blk(0)
		var sy := _top + y
		var sendW := _tw("Send", 12.5, true) + 22
		_field("chat", _x0, sy + 6, _cw - 8 - sendW - 6, "Say something to the room…")
		chatDraft = fields.get("chat", "")
		var c := _chip("Send", _x0 + _cw - sendW - 6, sy + 11, false)
		if c.clicked:
			_sendChat()
		_end(48)


## A player's row on a team card: padding 4, the name block, a 1px rule —
## taller by the chips' height when the host's controls are on it.
static func _memberH(controls: bool) -> float:
	return 42.0 if controls else 40.0


## A card's height in the team grid (for the row's stretch).
func _cardH(c: Array, plist: Array, me, quick: bool) -> float:
	if c[0] == "add":
		return 2.0 + 16 + _lh(12.5) + _bump("＋") + 12
	var t: int = c[1]
	var members := plist.filter(func(pl): return _teamOf(pl) == t)
	var h := 3.0 + 8 + 18 + members.size() * _memberH(slot == hostSlot and not quick) + 8 + 1
	if not observing and not quick and me != null and _teamOf(me) != t:
		h += 32
	return h


func _teamCard(t: int, x: float, y: float, w: float, h: float, plist: Array, me, isHost: bool, quick: bool, maxT: int) -> bool:
	var col: String = Teams.TEAM_COLORS[t] if t < Teams.TEAM_COLORS.size() else Teams.TEAM_COLORS[0]
	var members := plist.filter(func(pl): return _teamOf(pl) == t)
	if _live and _on_screen(y, h):
		ctx.fillStyle = "#1a140c"
		_rrect(x, y, w, h, 8); ctx.fill()
		ctx.strokeStyle = "#4a3c22"; ctx.lineWidth = 1
		_rrect(x + 0.5, y + 0.5, w - 1, h - 1, 7.5); ctx.stroke()
		# border-top: 3px in the team colour, following the rounded corners.
		ctx.save()
		ctx.beginPath(); ctx.rect(x, y, w, 3); ctx.clip()
		ctx.fillStyle = col
		_rrect(x, y, w, 12, 8); ctx.fill()
		ctx.restore()
	var ix := x + 11
	var iw := w - 22
	var iy := y + 3 + 8
	_text("On their own" if t == 0 else "Team %d" % t, ix, iy + 14, 16, col, true)
	var cnt := str(members.size())
	_text(cnt, ix + iw - _tw(cnt, 11), iy + 3 + _lm(11)[0], 11, "#8f8770")
	iy += 18
	var controls := isHost and not quick
	var rowH := _memberH(controls)
	# The name block (two lines, 31 tall) is centred against the chips (33).
	var pad := 5.0 if controls else 4.0
	for pl in members:
		var fdef = C.FACTIONS.get(pl.get("faction", "")) if pl.get("faction") else null
		var rowTop := iy
		# The name: ready mark, name, (you), ⭐; under it the faction.
		var lx := ix
		var b1 := rowTop + pad + 13
		lx += _text("✅ " if pl.get("ready", false) else "⬜ ", lx, b1, 13.5, "#e8ddc4")
		lx += _text(String(pl.name), lx, b1, 13.5, "#e8ddc4")
		if int(pl.slot) == slot:
			lx += _text(" ", lx, b1, 13.5, "#e8ddc4")
			lx += _text("(you)", lx, b1, 13.5, "#a89f88", false, true)
		if int(pl.slot) == hostSlot:
			lx += _text(" ⭐", lx, b1, 13.5, "#e8ddc4")
		var leftW := lx - ix
		var fName: String = fdef.name if fdef != null else "Random faction"
		leftW = maxf(leftW, _text(fName, ix, rowTop + pad + 16 + 12, 11, fdef.color if fdef != null else "#8f8770"))
		# The host's controls, spread across the row (flex: space-between).
		var chips: Array = []
		if isHost and not quick and int(pl.slot) != slot:
			chips.append("✕")
		if isHost and not quick:
			chips.append("Move ▸")
		if not chips.is_empty():
			var widths: Array = chips.map(func(l): return _tw(l, 12.5, true) + 22 + 6)
			var sum := leftW
			for cw in widths:
				sum += cw
			var gap := (iw - sum) / chips.size()
			var cx := ix + leftW + gap
			for i in chips.size():
				var c := _chip(chips[i], cx, rowTop + 4 + 3, false, "#e0786a" if chips[i] == "✕" else "#c9a24a")
				if chips[i] == "✕" and _hover(cx, rowTop + 7, c.w, c.h):
					UI.ui.hoveredTooltip = {"text": ["Remove %s from the room" % pl.name], "x": UI.ui.mx, "y": rowTop}
				if c.clicked and ws != null:
					if chips[i] == "✕":
						ws.send({"t": "kick", "slot": int(pl.slot)})
					else:
						var top_ := mini(Teams.MAX_TEAMS, maxT + (1 if maxT < Teams.MAX_TEAMS else 0))
						ws.send({"t": "team", "slot": int(pl.slot), "team": 0 if t >= top_ else t + 1})
				cx += widths[i] + gap
		if _live and _on_screen(rowTop + rowH - 1, 1):
			ctx.fillStyle = "#2e2618"
			ctx.fillRect(ix, rowTop + rowH - 1, iw, 1)
		iy += rowH
	if not observing and not quick and me != null and _teamOf(me) != t:
		var c := _chip("Go alone" if t == 0 else "Join team %d" % t, ix, iy + 3, false, col)
		if c.clicked and ws != null:
			ws.send({"t": "team", "team": t})
	return false


func _addCard(next: int, x: float, y: float, w: float, h: float) -> bool:
	if _live and _on_screen(y, h):
		ctx.strokeStyle = "#4a3c22"; ctx.lineWidth = 1
		ctx.setLineDash([3, 3])
		_rrect(x + 0.5, y + 0.5, w - 1, h - 1, 7.5); ctx.stroke()
		ctx.setLineDash([])
	var label := "＋ New team %d" % next
	var cw := _tw(label, 12.5, true) + 22
	var ch := float(_lh(12.5) + _bump(label) + 12)
	var c := _chip(label, x + (w - cw - 6) / 2, y + (h - ch) / 2, false)
	if c.clicked and ws != null:
		ws.send({"t": "team", "team": next})
	return false


func _chatLog() -> void:
	var y := _blk(0)
	var sy := _top + y
	var h := 134.0
	if _live and _on_screen(sy, h):
		ctx.fillStyle = "#120e08"
		_rrect(_x0, sy, _cw, h, 6); ctx.fill()
		ctx.strokeStyle = "#3a2f1c"; ctx.lineWidth = 1
		_rrect(_x0 + 0.5, sy + 0.5, _cw - 1, h - 1, 5.5); ctx.stroke()
		var lines: Array = []
		if chat.is_empty():
			lines = [[R("Nobody has said anything yet.", "#6f6a5c")]]
		else:
			for c in chat:
				lines.append_array(_wrap([R(String(c.name) + ":", "#9fd08a" if c.get("mine", false) else "#e8c060", true), R(" " + String(c.text))], 13, _cw - 22))
		# Scrolled to the bottom: the last lines that fit.
		var lh := 15.0
		var fit := floori(120 / lh)
		var first := maxi(0, lines.size() - fit)
		var off := maxf(0, lines.size() * lh - 120) - first * lh
		ctx.save()
		ctx.beginPath(); ctx.rect(_x0 + 1, sy + 1, _cw - 2, h - 2); ctx.clip()
		for i in range(first, lines.size()):
			_drawRuns(lines[i], _x0 + 11, sy + 7 + (i - first) * lh - off + _lm(13)[0], 13, "#e8ddc4")
		ctx.restore()
	_end(h)


# --- The battlefield picker ---

## The host's battlefield picker: built-in battlefields, the maps you have
## published, and the community pool on this server. Your published maps can
## be published to the server from here, and a community map can be kept.
func _drawMapPicker() -> void:
	var n := players.filter(func(pl): return not pl.get("observer", false)).size()
	_title("Choose the battlefield")
	_note([R("%d player%s in the room. Maps that can't seat everyone are marked." % [n, "" if n == 1 else "s"])])
	var fits := func(mn, mx): return (mn == null or n >= int(mn)) and (mx == null or n <= int(mx))

	_heading("Built-in battlefields")
	if _pickRow("Random", "A different built-in battlefield every match, from the seed.", true, [_useChip({"id": "random", "name": "Random battlefield"})]):
		return
	for pr in MapGen.PRESETS:
		if _pickRow(pr.name, pr.desc, true, [_useChip({"id": pr.id, "name": pr.name})]):
			return

	var mine := CustomMaps.listCustomMaps().filter(func(m): return CustomMaps.isPublished(m))
	_heading("Your published maps (%d)" % mine.size())
	if mine.is_empty():
		_para([R("Publish a map from the Map Editor and it appears here.")], 12.5, "#8f8770")
	for m in mine:
		var code := CustomMaps.serialiseMap(m)
		var author: String = m.author if m.get("author") else ls(LS_NAME, "Player")
		var onServer := community.any(func(c): return c.name == m.name and c.author == author)
		var sub := "%d×%d · %d–%d players%s" % [int(m.cols), int(m.rows), int(m.minPlayers), int(m.maxPlayers), (" · by %s" % m.author) if m.get("author") else ""]
		var use := _useChip({"id": m.id, "name": m.name, "author": m.get("author"), "minPlayers": m.minPlayers, "maxPlayers": m.maxPlayers, "code": code})
		var pub := {"label": "On server" if onServer else "Publish to server", "on": false, "disabled": onServer, "act": "publish", "m": m, "code": code}
		if _pickRow(m.name, sub, fits.call(m.minPlayers, m.maxPlayers), [use, pub]):
			return

	_heading("Community maps on this server (%d)" % community.size())
	if community.is_empty():
		_para([R("Nobody has published a map here yet — be the first.")], 12.5, "#8f8770")
	for c in community:
		var pick := {"label": "✓ Chosen" if map.get("id") == c.id else "Use", "on": map.get("id") == c.id, "act": "getuse", "id": c.id}
		var keep := {"label": "Keep a copy", "on": false, "act": "getkeep", "id": c.id}
		var sub := "by %s · %d×%d · %d–%d players%s" % [c.author, int(c.cols), int(c.cols), int(c.minPlayers), int(c.maxPlayers), (" · %s" % c.desc) if String(c.get("desc", "")) != "" else ""]
		if _pickRow(c.name, sub, fits.call(c.minPlayers, c.maxPlayers), [pick, keep]):
			return

	_blk(12)
	_end(0)
	if _btnLine([{"label": "Back to the lobby", "primary": false}]) == 0:
		picking = false
		render()
		return
	if statusLine != "":
		_para([R(statusLine)], 13, "#9fd08a", 6)


func _useChip(m: Dictionary) -> Dictionary:
	var chosen: bool = map.get("id") == m.id
	return {"label": "✓ Chosen" if chosen else "Use", "on": chosen, "act": "use", "map": m}


## One row of the picker; returns true when a click changed the screen.
func _pickRow(name: String, sub: String, ok: bool, actions: Array) -> bool:
	var y := _blk(4)
	var sy := _top + y
	var h := 50.0
	var alpha := 1.0 if ok else 0.55
	if _live and _on_screen(sy, h):
		ctx.save()
		ctx.globalAlpha = alpha
		ctx.fillStyle = "#15110a"
		_rrect(_x0, sy, _cw, h, 6); ctx.fill()
		ctx.strokeStyle = "#3a2f1c"; ctx.lineWidth = 1
		_rrect(_x0 + 0.5, sy + 0.5, _cw - 1, h - 1, 5.5); ctx.stroke()
		ctx.restore()
		ctx.save()
		ctx.globalAlpha = alpha
	_text(name, _x0 + 11, sy + 7 + 14, 16, "#f2e8d0", true)
	_text(sub, _x0 + 11, sy + 7 + 18 + 14, 11.5, "#a89f88")
	if _live and _on_screen(sy, h):
		ctx.restore()
	var widths: Array = actions.map(func(a): return _tw(a.label, 12.5, true) + 22 + 6)
	var total := 0.0
	for w in widths:
		total += w
	var x := _x0 + _cw - 11 - total
	var changed := false
	for i in actions.size():
		var a: Dictionary = actions[i]
		var c := _chip(a.label, x, sy + 12, a.get("on", false), "#c9a24a", a.get("disabled", false), alpha)
		x += widths[i]
		if c.clicked and ws != null:
			match a.act:
				"use":
					ws.send({"t": "map", "map": a.map})
					picking = false
					render()
					changed = true
				"publish":
					ws.send({"t": "publish", "map": publishable(a.m, a.code)})
				"getuse":
					pendingUse = a.id
					ws.send({"t": "getmap", "id": a.id})
				"getkeep":
					pendingKeep = a.id
					ws.send({"t": "getmap", "id": a.id})
	_end(h, 4)
	return changed


# ============================================================ for tests ==

## The last-drawn button/chip/field whose label contains `label`, or null.
func find(label: String, kind := ""):
	for d in drawn:
		if String(d.label).contains(label) and (kind == "" or d.kind == kind):
			return d
	return null
