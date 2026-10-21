## sim/savegame.ts — save and resume, built on the command layer rather than
## on a state dump.
##
## A saved game here is **the seed, the setup, and every order you gave** — not
## a snapshot of the world. The sim is a deterministic fixed-tick machine
## driven by a seeded RNG, and the AI is deterministic too, so replaying the
## setup and the order log reproduces the match exactly, tick for tick. A
## command log has one failure mode: if the replay ever diverges, the checksum
## says so at load time, loudly, before you have played an hour on top of it.
##
## A SaveGame is a Dictionary with the TS keys:
##   {version, savedAt, label, setup, mapCode?, tick, commands, checksum,
##    summary: {mapName, mode, players, difficulty, elapsed}}
## and a LoggedCommand is {"t": tick, "c": Command}.
class_name Savegame

## Bumped whenever the rules change under a saved command log — a save replays
## its orders against the current rules, so a different rulebook plays out a
## different match. 2: the Empire Age and Oaths.
const SAVE_FORMAT_VERSION := 2
const STORAGE_KEY := "banner_and_blade_saves_v1"
## How many saves we keep. Enough for a few games in flight, bounded for storage.
const SAVE_LIMIT := 6


## Records the orders a match has been given.
##
## Only the *player's* commands go in. The AI drives the world directly and
## deterministically off its own seeded RNG, so its decisions are reproduced by
## re-running it rather than by recording them.
class CommandLog extends RefCounted:
	var entries: Array = []

	func clear() -> void:
		entries.clear()

	func record(tick: int, cmd: Dictionary) -> void:
		entries.append({"t": tick, "c": cmd})

	## Restore a log from a save, so play can continue and be saved again.
	func load(from: Array) -> void:
		entries = []
		for e in from:
			entries.append({"t": e.t, "c": e.c})


## Fast-forward a freshly-built world to the saved tick. Returns
## {faithful, expected, actual, ticks}.
##
## The order inside the loop has to match the live match loop exactly — world
## tick, then every AI in roster order — because "deterministic" only means
## anything relative to a fixed order of operations. `ais` are objects with an
## `update(dt)` method.
static func replayTo(world: World, ais: Array, log: Array, targetTick: int, expectedChecksum = null) -> Dictionary:
	# Bucket by tick so each step is a lookup rather than a scan of the whole log.
	var byTick := {}
	for e in log:
		var t := int(e.t)
		if byTick.has(t):
			byTick[t].append(e.c)
		else:
			byTick[t] = [e.c]
	for tick in targetTick:
		var due = byTick.get(tick)
		if due != null:
			for c in due:
				Commands.applyCommand(world, c)
		world.tick()
		for ai in ais:
			ai.update(world.SIM_DT)
		# Events would otherwise pile up to the cap and the first live frame would
		# fire an hour of alerts and sounds at once.
		world.drainEvents()
	var actual := Commands.worldChecksum(world)
	return {
		"faithful": expectedChecksum == null or actual == int(expectedChecksum),
		"expected": int(expectedChecksum) if expectedChecksum != null else actual,
		"actual": actual,
		"ticks": targetTick,
	}


# ------------------------------------------------------------------ storage --

## Newest first.
static func listSaves() -> Array:
	var raw = Storage.getItem(STORAGE_KEY)
	if raw == null or raw == "":
		return []
	var parsed = JSON.parse_string(raw)
	if not (parsed is Array):
		return []
	var out: Array = parsed.filter(func(x): return x is Dictionary and float(x.get("version", -1)) == SAVE_FORMAT_VERSION \
		and x.get("commands") is Array and (x.get("tick") is float or x.get("tick") is int))
	JS.sort_by_cmp(out, func(a, b): return float(b.savedAt) - float(a.savedAt))
	return out.slice(0, SAVE_LIMIT)


## Write a save, evicting the oldest once the shelf is full.
static func writeSave(save: Dictionary) -> bool:
	var all: Array = [save]
	for x in listSaves():
		if x.label != save.label:
			all.append(x)
	Storage.setItem(STORAGE_KEY, JSON.stringify(all.slice(0, SAVE_LIMIT)))
	return true


static func deleteSave(label: String) -> void:
	var keep := listSaves().filter(func(x): return x.label != label)
	Storage.setItem(STORAGE_KEY, JSON.stringify(keep))
