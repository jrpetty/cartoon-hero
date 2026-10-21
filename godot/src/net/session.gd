## net/session.ts — glues the Lockstep driver to a transport and adds periodic
## checksum exchange, so a desync is caught loudly instead of players silently
## drifting into different games. Any number of teams.
class_name NetSession
extends RefCounted

var lock: Lockstep = null
var desynced := false
var localSums: Dictionary = {}
var remoteSums: Dictionary = {}  # tick -> { team -> sum }
const sumEvery := 40  # compare state every ~2s of sim time

var transport
var localTeam: int
var teams: Array
var observer: bool

## App-level message hooks (chat & map pings) — set by the match shell.
var onChat: Callable
var onPing: Callable
var onRated: Callable
var onAnnounce: Callable


func _init(transport_, local_team: int, teams_: Array, observer_: bool = false) -> void:
	transport = transport_
	localTeam = local_team
	teams = teams_
	observer = observer_


## Seconds this client's simulation trails the live match (a caster's delay).
func behindSeconds(simHz: float) -> float:
	return maxf(0.0, float(lock.readyThrough() - lock.currentTick) / simHz) if lock else 0.0


## Build the lockstep driver over a freshly-inited world and wire the wire.
func attach(world, inputDelay: int = 5) -> void:
	lock = Lockstep.new(world, localTeam, teams, inputDelay, func(turn): transport.send({"t": "turn", "turn": turn}))
	transport.onData = _on_data


func _on_data(m) -> void:
	if not (m is Dictionary):
		return
	var t = m.get("t")
	if t == "turn" and m.get("turn") is Dictionary:
		if lock:
			lock.receiveTurn(m.turn)
	elif t == "sum" and m.get("tick") is float and m.get("sum") is float and m.get("team") is float:
		compare(int(m.tick), int(m.team), int(m.sum))
	elif t == "drop" and m.get("team") is float:
		if lock:
			lock.dropTeam(int(m.team))
	elif t == "chat":
		if onChat.is_valid(): onChat.call(m)
	elif t == "ping":
		if onPing.is_valid(): onPing.call(m)
	elif t == "rated":
		if onRated.is_valid(): onRated.call(m)
	elif t == "announce" and m.get("text") is String:
		if onAnnounce.is_valid(): onAnnounce.call(m.text)


## A team's player left mid-match — keep simulating without them.
func dropTeam(team: int) -> void:
	if lock:
		lock.dropTeam(team)


## Author + send the local team's turn for one tick, at the real-time sim rate.
func authorTick() -> void:
	if not observer and lock:
		lock.authorTurn()


## How far the local author cursor is ahead of the simulated tick.
var authorLead: int:
	get:
		return lock.nextAuthorTick + lock.inputDelay - lock.currentTick if lock else 0


## Step the sim as far as the received remote input allows (bounded), emitting
## a checksum periodically for desync detection. Returns ticks simulated.
func stepReady(maxSteps: int, holdBackTicks: int = 0) -> int:
	if lock == null:
		return 0
	var n := 0
	while n < maxSteps and (holdBackTicks <= 0 or lock.currentTick <= lock.readyThrough() - holdBackTicks) and lock.step():
		var t := lock.currentTick
		if t % sumEvery == 0:
			var sum: int = Commands.worldChecksum(lock.world)
			localSums[t] = sum
			if not observer:
				transport.send({"t": "sum", "tick": t, "sum": sum, "team": localTeam})
			compare(t)
		n += 1
	return n


## Remote checksums keyed by tick AND author team, so with 3+ players a matching
## peer can't mask another peer's divergence.
func compare(tick: int, team = null, remote = null) -> void:
	if team != null and remote != null:
		if not remoteSums.has(tick):
			remoteSums[tick] = {}
		remoteSums[tick][team] = remote
	var l = localSums.get(tick)
	var m = remoteSums.get(tick)
	if l == null or m == null:
		return
	for v in m.values():
		if int(v) != int(l):
			desynced = true
			return
