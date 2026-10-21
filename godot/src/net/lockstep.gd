## net/lockstep.ts — deterministic lockstep driver. Every client runs its own
## World and advances it in step: a tick is only simulated once *all* players'
## commands for that tick have arrived. Local actions are scheduled
## `inputDelay` ticks in the future to hide latency.
##
## Transport-agnostic: `onSend` ships a finalized turn to peers, and
## `receiveTurn` feeds peers' turns back in.
class_name Lockstep
extends RefCounted

var currentTick := 0
var teams: Array = []
var buffer: Dictionary = {}  # tick -> { team -> Array[command] }
var pending: Array = []
var nextAuthorTick := 0
## Teams whose player has left: past the last turn they sent, their turns are
## treated as empty, so the rest of the lobby keeps simulating in sync.
var dropped: Dictionary = {}
var dropAfter: Dictionary = {}
## The highest tick each team's turn has arrived for.
var latest: Dictionary = {}
## Every command applied, with its tick, in the order applied — the replay.
## null unless asked for.
var record = null

var world
var localTeam: int
var inputDelay: int
var onSend: Callable


func _init(world_, local_team: int, teams_: Array, input_delay: int = 6, on_send: Callable = Callable()) -> void:
	world = world_
	localTeam = local_team
	inputDelay = input_delay
	onSend = on_send
	teams = teams_.duplicate()
	teams.sort()  # identical order on every client (ints: sort is fine)
	for t in inputDelay:
		for tm in teams:
			submit(t, tm, [])


func slot(tick: int) -> Dictionary:
	if not buffer.has(tick):
		buffer[tick] = {}
	return buffer[tick]


func submit(tick: int, team: int, cmds: Array) -> void:
	slot(tick)[team] = cmds
	if tick > int(latest.get(team, -1)):
		latest[team] = tick


## Is this team's turn needed before `tick` can run?
func gates(team: int, tick: int) -> bool:
	return not dropped.has(team) or tick <= int(dropAfter.get(team, -1))


## The last tick every still-playing team has sent its turn for.
func readyThrough() -> int:
	var t := 1 << 62
	var any := false
	for tm in teams:
		if dropped.has(tm):
			continue
		any = true
		t = mini(t, int(latest.get(tm, -1)))
	if not any:
		var m := -1
		for v in dropAfter.values():
			m = maxi(m, int(v))
		return m
	return t


## Queue a local action; it executes on currentTick + inputDelay.
func localCommand(cmd: Dictionary) -> void:
	pending.append(cmd)


## Finalize this client's turn for the next authored tick and ship it.
func authorTurn() -> Dictionary:
	var tick := nextAuthorTick + inputDelay
	# Apply locally exactly what peers will apply: the command as it reads back
	# off the wire (a JSON round trip), not the in-memory original. WireJSON
	# reads numbers the way a browser does, so a float survives this exactly
	# (as it does in the TS, which applies the original) — what changes is
	# only the shape: ints become floats, then normalizeCommand puts back the
	# ones the sim indexes with.
	var cmds: Array = []
	for c in pending:
		cmds.append(normalizeCommand(WireJSON.parse(WireJSON.stringify(c))))
	pending = []
	submit(tick, localTeam, cmds)
	nextAuthorTick += 1
	var turn := {"tick": tick, "team": localTeam, "cmds": cmds}
	if onSend.is_valid():
		onSend.call(turn)
	return turn


## Ingest a peer's finalized turn (as it arrives off the wire: JSON numbers are
## floats, so the integer fields are normalised here).
func receiveTurn(turn: Dictionary) -> void:
	var cmds: Array = []
	for c in turn.get("cmds", []):
		cmds.append(normalizeCommand(c))
	submit(int(turn.tick), int(turn.team), cmds)


## Mark a team as departed; its turns no longer gate progress.
func dropTeam(team: int) -> void:
	if dropped.has(team):
		return
	dropped[team] = true
	dropAfter[team] = int(latest.get(team, -1))


## True when every still-present player's commands for the current tick are in.
func canStep() -> bool:
	var m = buffer.get(currentTick)
	for tm in teams:
		if not gates(tm, currentTick):
			continue
		if m == null or not m.has(tm):
			return false
	return true


## Advance exactly one tick if ready. Returns false if still waiting on input.
func step() -> bool:
	if not canStep():
		return false
	var m: Dictionary = buffer.get(currentTick, {})
	var tagged: Array = []
	var seq := 0
	for tm in teams:
		for c in m.get(tm, []):
			tagged.append({"cmd": c, "seq": seq})
			seq += 1
	for cmd in Commands.sortCommands(tagged):
		Commands.applyCommand(world, cmd)
		if record != null:
			record.append({"t": currentTick, "c": cmd})
	world.tick()
	buffer.erase(currentTick)
	currentTick += 1
	return true


const _INT_KEYS := ["team", "target", "node", "buildingId", "index", "stance"]

## Commands from JSON carry floats where the sim expects ints.
static func normalizeCommand(c: Dictionary) -> Dictionary:
	var o := c.duplicate()
	for k in _INT_KEYS:
		if o.has(k) and o[k] is float:
			o[k] = int(o[k])
	if o.has("building") and o["building"] is float:
		o["building"] = int(o["building"])
	for k in ["ids", "builders"]:
		if o.has(k):
			var a: Array = []
			for v in o[k]:
				a.append(int(v))
			o[k] = a
	return o
