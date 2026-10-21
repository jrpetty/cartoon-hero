## sim/replay.ts — replays: every match, kept so it can be watched again.
##
## The simulation is deterministic, so a match is its starting setup plus the
## orders given, tick by tick. A skirmish needs only the human's orders — the
## AIs make the same decisions again from the same seed. An online match has
## no AIs, so its record is every order the lockstep applied, from every
## player, in the order it applied them.
##
## A ReplayRecord is a Dictionary with the TS keys:
##   {version, id, savedAt, kind ("skirmish"|"watch"|"online"), setup?, mapCode?,
##    net?: {seed, numTeams, alliances, factions, map: {id, name, code?}},
##    names, pov, commands (LoggedCommand[]), endTick,
##    summary: {map, players, durationSec, result, factions}, imported?, sim?}
class_name Replay

const REPLAY_VERSION := 1
const KEY := "bb_replays_v1"
## How many replays are kept; the oldest go first.
const REPLAY_LIMIT := 12


static func listReplays() -> Array:
	var raw = Storage.getItem(KEY)
	var arr = JSON.parse_string(raw) if raw != null and raw != "" else []
	if not (arr is Array):
		return []
	var out: Array = arr.filter(func(r): return r is Dictionary and float(r.get("version", -1)) == REPLAY_VERSION)
	JS.sort_by_cmp(out, func(a, b): return float(b.savedAt) - float(a.savedAt))
	return out


## Keep a replay. The oldest go once there are more than REPLAY_LIMIT.
static func saveReplay(r: Dictionary) -> bool:
	var all: Array = [r]
	for x in listReplays():
		if x.id != r.id:
			all.append(x)
	Storage.setItem(KEY, JSON.stringify(all.slice(0, REPLAY_LIMIT)))
	return true


static func deleteReplay(id: String) -> void:
	var keep := listReplays().filter(func(r): return r.id != id)
	Storage.setItem(KEY, JSON.stringify(keep))


## Orders bucketed by tick, for playback: {tick: [Command, ...]} in log order.
static func byTick(commands: Array) -> Dictionary:
	var m := {}
	for e in commands:
		var t := int(e.t)
		if m.has(t):
			m[t].append(e.c)
		else:
			m[t] = [e.c]
	return m


## `n.toString(36)` for a non-negative integer.
static func toBase36(n: int) -> String:
	if n == 0:
		return "0"
	var digits := "0123456789abcdefghijklmnopqrstuvwxyz"
	var s := ""
	while n > 0:
		s = digits[n % 36] + s
		n /= 36
	return s


static func replayId() -> String:
	return "r" + toBase36(int(JS.now_ms())) + toBase36(int(floor(JS.random() * 1e6)))


# ------------------------------------------------------------ replay files --
#
# A replay is small (the setup plus the orders), so it travels as a plain JSON
# file. Anything opened is checked field by field before it's trusted: a replay
# file is someone else's data.

const REPLAY_FILE_FORMAT := "banner-and-blade-replay"
const REPLAY_FILE_EXT := ".bbreplay"
## Big enough for hours of 8-player orders; small enough to refuse junk.
const MAX_COMMANDS := 2000000


## The file name and text for downloading a replay: {name, text}.
static func replayFile(r: Dictionary) -> Dictionary:
	# Local time, as `new Date(savedAt)` reads it.
	var bias: int = int(Time.get_time_zone_from_system().get("bias", 0))
	var d := Time.get_datetime_dict_from_unix_time(int(floor(float(r.savedAt) / 1000.0)) + bias * 60)
	var stamp := "%d-%02d-%02d_%02d%02d" % [d.year, d.month, d.day, d.hour, d.minute]
	var mapName := str(r.get("summary", {}).get("map", ""))
	if mapName == "":
		mapName = "match"
	var re := RegEx.create_from_string("[^A-Za-z0-9]+")
	var map := re.sub(mapName, "-", true)
	map = RegEx.create_from_string("^-|-$").sub(map, "", true).left(40)
	if map == "":
		map = "match"
	var rest := r.duplicate()
	rest.erase("imported")
	var sim = r.sim if r.get("sim") != null else Savegame.SAVE_FORMAT_VERSION
	var body := {"format": REPLAY_FILE_FORMAT, "fileVersion": 1, "sim": sim, "replay": rest}
	return {"name": "banner-and-blade_%s_%s%s" % [map, stamp, REPLAY_FILE_EXT], "text": JSON.stringify(body)}


static func _isNum(v) -> bool:
	return (v is float or v is int) and is_finite(float(v))


static func _isStr(v) -> bool:
	return v is String


## Read a replay file. Refuses anything that isn't one; warns if it's from
## another version. Returns {ok: true, replay, warning?} or {ok: false, error}.
static func parseReplayFile(text: String) -> Dictionary:
	var json := JSON.new()
	if json.parse(text) != OK:
		return {"ok": false, "error": "That file isn't a Banner & Blade replay (it isn't valid JSON)."}
	var b = json.data
	if not (b is Dictionary) or b.get("format") != REPLAY_FILE_FORMAT or not (b.get("replay") is Dictionary):
		return {"ok": false, "error": "That file isn't a Banner & Blade replay."}
	var r: Dictionary = b.replay
	var kind = r.get("kind")
	if not (kind == "skirmish" or kind == "watch" or kind == "online"):
		return {"ok": false, "error": "The replay's match type is unknown."}
	var endTick = r.get("endTick")
	if not _isNum(endTick) or float(endTick) < 1 or float(endTick) > 20 * 60 * 60 * 6:
		return {"ok": false, "error": "The replay's length is invalid."}
	if not (r.get("commands") is Array) or r.commands.size() > MAX_COMMANDS:
		return {"ok": false, "error": "The replay's orders are missing or too many."}
	for e in r.commands:
		var ok: bool = e is Dictionary and _isNum(e.get("t")) and e.get("c") is Dictionary \
			and _isStr(e.c.get("t")) and _isNum(e.c.get("team"))
		if not ok:
			return {"ok": false, "error": "The replay contains an order it can't read."}
	if kind == "online":
		var n = r.get("net")
		var good: bool = n is Dictionary and _isNum(n.get("seed")) and _isNum(n.get("numTeams")) and n.get("alliances") is Array \
			and n.get("factions") is Array and n.get("map") is Dictionary and _isStr(n.map.get("id"))
		if not good:
			return {"ok": false, "error": "The online replay's setup is incomplete."}
	elif not (r.get("setup") is Dictionary) or not _isNum(r.setup.get("seed")):
		return {"ok": false, "error": "The replay's match setup is incomplete."}
	var names: Array = []
	if r.get("names") is Array:
		for x in r.names:
			names.append(("" if x == null else str(x)).left(32))
		names = names.slice(0, 16)
	var sm: Dictionary = r.get("summary") if r.get("summary") is Dictionary else {}
	var factions: Array = []
	if sm.get("factions") is Array:
		for f in sm.factions:
			factions.append(str(f))
		factions = factions.slice(0, 16)
	var replay := {
		"version": REPLAY_VERSION,
		"id": replayId(),
		"savedAt": r.savedAt if _isNum(r.get("savedAt")) else JS.now_ms(),
		"kind": kind,
		"setup": r.get("setup"),
		"mapCode": r.mapCode if _isStr(r.get("mapCode")) else null,
		"net": r.get("net"),
		"names": names,
		"pov": r.pov if _isNum(r.get("pov")) else -1,
		"commands": r.commands,
		"endTick": endTick,
		"summary": {
			"map": str(sm.get("map", "Battlefield") if sm.get("map") != null else "Battlefield").left(40),
			"players": sm.players if _isNum(sm.get("players")) else names.size(),
			"durationSec": sm.durationSec if _isNum(sm.get("durationSec")) else JS.round(float(endTick) / 20.0),
			"result": str(sm.get("result", "") if sm.get("result") != null else "").left(60),
			"factions": factions,
		},
		"imported": true,
		"sim": b.sim if _isNum(b.get("sim")) else null,
	}
	var sim = replay.sim
	# What TS leaves undefined (and JSON.stringify drops) is absent here too.
	for k in ["setup", "mapCode", "net", "sim"]:
		if replay[k] == null:
			replay.erase(k)
	var out := {"ok": true, "replay": replay}
	if sim != null and float(sim) != Savegame.SAVE_FORMAT_VERSION:
		out["warning"] = "This replay was recorded with a different version of the game — it may play out differently."
	return out
