## Custom maps: the format the editor writes, and everything the game needs to
## read one back.
##
## Port of game/src/maps/custom.ts. Data (MAP_FORMAT_VERSION, NOMAD_RULES,
## MAP_SIZES, MAX_SEATS) is in `C`.
##
## A custom map is deliberately *not* a MapData. MapData is what the sim
## consumes for one particular match — it already knows how many players there
## are, where they start and which cells are walls. A custom map is the authored
## thing that outlives any single match: the ground, the resources, the spawn
## points, and the rules about who may play on it. `toMapData` is where the two
## meet, and it is the only place that has to know about player counts, nomad
## or team seating.
##
## CustomMap is a Dictionary with the TS keys, in the TS order:
##   id: String, name: String,
##   desc: String — a line or two about what the map is for, shown on its card
##     in the lobby. The difference between a map people scroll past and one
##     they pick.
##   version: int, biome: String (BiomeId), cols: int, rows: int,
##   terrain: PackedByteArray (one Terrain per cell, row-major),
##   resources: Array of ResourceSpawn {type, x, y, amount},
##   spawns: Array of {x, y} — authored spawn points in world coordinates, in
##     seat order,
##   modes: Array of GameMode strings — which game modes this map is for;
##     empty means "any",
##   minPlayers: int, maxPlayers: int,
##   nomad: String (NomadRule: "off" | "optional" | "forced"),
##   startResources: {food, wood, gold},
##   updated: float — epoch millis of the last save, for sorting the library,
##   published (optional): float — when the author published it to the map
##     pool, or 0 for a draft. Published maps are what the Skirmish list, the
##     Random roll and online lobbies offer; a draft lives in the editor until it
##     is ready. Kept in the library entry, not the map code — importing
##     someone's map gives you a draft of your own.
##   author (optional): String — who made it, carried in the code so a shared
##     map keeps its credit.
##
## MapIssue is {level: "error" | "warning", text: String}.
class_name CustomMaps
extends RefCounted

const Terrain = TerrainKinds.Terrain


## A blank map, painted with its biome's primary ground.
static func newCustomMap(name: String, cols: int, biome: String = "temperate") -> Dictionary:
	var b := TerrainKinds.biomeById(biome)
	var terrain := PackedByteArray()
	terrain.resize(cols * cols)
	terrain.fill(int(b.ground[0]))
	return {
		"id": _newId(),
		"name": name,
		"desc": "",
		"version": int(C.MAP_FORMAT_VERSION),
		"biome": biome,
		"cols": cols,
		"rows": cols,
		"terrain": terrain,
		"resources": [],
		"spawns": [],
		"modes": [],
		# Wide open by default: the creator narrows it if they mean to, rather than
		# discovering later that the editor quietly decided for them.
		"minPlayers": 1,
		"maxPlayers": int(C.MAX_SEATS),
		"nomad": "off",
		"startResources": C.START_RESOURCES.duplicate(),
		"updated": JS.now_ms(),
	}


# `custom_${Date.now().toString(36)}_${Math.floor(Math.random() * 1e6).toString(36)}`
static func _newId() -> String:
	return "custom_%s_%s" % [_toString36(JS.now_ms()), _toString36(floor(JS.random() * 1e6))]


# ------------------------------------------------------------ serialising --
#
# The whole game ships as one HTML file, so the only realistic way to move a
# map between two people is a string they can paste. Terrain is run-length
# encoded first — a map is mostly large areas of one ground, so RLE takes a
# 25,000-cell field down to a few hundred runs — and the result is base64'd so
# it survives a chat window.

static func rleEncode(a: PackedByteArray) -> String:
	var parts := PackedStringArray()
	var i := 0
	var n_all := a.size()
	while i < n_all:
		var v := a[i]
		var n := 1
		while i + n < n_all and a[i + n] == v and n < 65535:
			n += 1
		parts.append(str(v) if n == 1 else "%dx%d" % [v, n])
		i += n
	return ".".join(parts)


## Returns a PackedByteArray, or null when the string doesn't decode to exactly
## `expect` cells.
static func rleDecode(s: String, expect: int):
	var out := PackedByteArray()
	out.resize(expect)
	var at := 0.0
	for part in s.split("."):
		if part == "":
			continue
		var xi := part.find("x")
		var v := _Number(part if xi < 0 else part.substr(0, xi))
		var n := 1.0 if xi < 0 else _Number(part.substr(xi + 1))
		if not is_finite(v) or not is_finite(n) or n < 1.0:
			return null
		if at + n > expect:
			return null
		# Uint8Array.fill(v, at, at + n): v wraps to a byte, the bounds truncate.
		var byte := posmod(int(JS.trunc(v)), 256) if absf(v) < 9.0e18 else 0
		var from := int(JS.trunc(at))
		var to := mini(int(JS.trunc(at + n)), expect)
		for k in range(from, to):
			out[k] = byte
		at += n
	return out if at == expect else null


# btoa/atob exist in every browser and in Node 16+, which covers the game and
# its headless tests alike; the byte dance around them is what lets a map name
# contain a character outside Latin-1 without corrupting the string.
static func b64encode(s: String) -> String:
	return Marshalls.utf8_to_base64(s)


## atob + UTF-8 decode. Returns null where the browser would throw (bad
## base64, bytes that aren't UTF-8).
static func b64decode(s: String):
	# atob's forgiving-base64: drop ASCII whitespace, allow one or two '=' of
	# padding, refuse anything outside the alphabet or a length that can't be.
	var t := s.replace(" ", "").replace("\t", "").replace("\n", "").replace("\r", "").replace("\f", "")
	if t.length() % 4 == 0:
		if t.ends_with("=="):
			t = t.substr(0, t.length() - 2)
		elif t.ends_with("="):
			t = t.substr(0, t.length() - 1)
	if t.length() % 4 == 1:
		return null
	if _b64Re == null:
		_b64Re = RegEx.create_from_string("^[A-Za-z0-9+/]*$")
	if _b64Re.search(t) == null:
		return null
	while t.length() % 4 != 0:
		t += "="
	var bytes := Marshalls.base64_to_raw(t) if t != "" else PackedByteArray()
	if not _validUtf8(bytes):
		return null # decodeURIComponent throws on a malformed sequence
	return bytes.get_string_from_utf8()


static var _b64Re: RegEx = null
static var _numRe: RegEx = null


static func _validUtf8(b: PackedByteArray) -> bool:
	var i := 0
	var n := b.size()
	while i < n:
		var c := b[i]
		if c < 0x80:
			i += 1
			continue
		var need := 0
		var cp := 0
		var lo := 0
		if c >= 0xC2 and c <= 0xDF:
			need = 1
			cp = c & 0x1F
			lo = 0x80
		elif c >= 0xE0 and c <= 0xEF:
			need = 2
			cp = c & 0x0F
			lo = 0x800
		elif c >= 0xF0 and c <= 0xF4:
			need = 3
			cp = c & 0x07
			lo = 0x10000
		else:
			return false
		if i + need >= n:
			return false
		for k in range(1, need + 1):
			var cc := b[i + k]
			if (cc & 0xC0) != 0x80:
				return false
			cp = (cp << 6) | (cc & 0x3F)
		if cp < lo or cp > 0x10FFFF or (cp >= 0xD800 and cp <= 0xDFFF):
			return false
		i += need + 1
	return true


## A map as one pasteable string.
static func serialiseMap(m: Dictionary) -> String:
	var TILE := float(C.TILE)
	# Resources and spawns go to cell coordinates: they are always placed on a
	# cell, and integers survive a round trip where floats invite drift. It has
	# to be floor, not round — a cell centre is cx*TILE + TILE/2, so rounding
	# sends every coordinate one cell down and to the right.
	var rs: Array = []
	for x in m.resources:
		rs.append([String(x.type).substr(0, 1), floor(x.x / TILE), floor(x.y / TILE), x.amount])
	var sp: Array = []
	for s in m.spawns:
		sp.append([floor(s.x / TILE), floor(s.y / TILE)])
	var payload := {
		"v": m.version, "n": m.name, "d": m.desc, "b": m.biome, "c": m.cols, "r": m.rows,
		"t": rleEncode(m.terrain),
		"rs": rs,
		"sp": sp,
		"md": m.modes, "mn": m.minPlayers, "mx": m.maxPlayers, "nm": m.nomad,
		"sr": m.startResources,
	}
	if _truthy(m.get("author")):
		payload["au"] = m.author
	return "BBMAP1:" + b64encode(jsonStringify(payload))


const RES_BY_LETTER := {"t": "tree", "g": "gold_mine", "b": "berries"}


## Parse a pasted map string. Returns null for anything it doesn't trust.
static func deserialiseMap(code: String):
	var TILE := float(C.TILE)
	var MAX_SEATS := float(C.MAX_SEATS)
	var trimmed := code.strip_edges()
	var at := trimmed.find(":")
	if at < 0 or not trimmed.substr(0, at).begins_with("BBMAP"):
		return null
	var text = b64decode(trimmed.substr(at + 1))
	if text == null:
		return null
	var json := JSON.new()
	if json.parse(text) != OK:
		return null
	var p = json.data
	# Anything but an object has no fields: p.c is undefined (or, for null, a
	# TypeError) and the map is refused either way.
	if not (p is Dictionary):
		return null
	var cols := _Number(p.c) if p.has("c") else NAN
	var rows := _Number(p.r) if p.has("r") else NAN
	if not _isInteger(cols) or not _isInteger(rows) or cols < 16 or cols > 400 or rows < 16 or rows > 400:
		return null
	var terrain = rleDecode(_String(p.t) if p.has("t") else "undefined", int(cols * rows))
	if terrain == null:
		return null
	# Anything outside the terrain table would index off the end of the lookup
	# arrays on the movement path, so clamp rather than trust the string.
	for i in terrain.size():
		if terrain[i] > 10:
			terrain[i] = 0
	var resources: Array = []
	if p.get("rs") is Array:
		for r in p.rs:
			if r == null:
				return null # r[0] on null throws
			var type = RES_BY_LETTER.get(_String(_at(r, 0)))
			if type == null:
				continue
			var amt := _Number(_at(r, 3))
			if is_nan(amt) or amt == 0.0:
				amt = 100.0
			resources.append({"type": type, "x": _cell(_at(r, 1), TILE), "y": _cell(_at(r, 2), TILE), "amount": maxf(1.0, amt)})
	var spawns: Array = []
	if p.get("sp") is Array:
		for s in p.sp:
			if s == null:
				return null
			spawns.append({"x": _cell(_at(s, 0), TILE), "y": _cell(_at(s, 1), TILE)})
	var modes: Array = []
	if p.get("md") is Array:
		for md in p.md:
			if md is String and ["conquest", "survival", "koth", "regicide"].has(md):
				modes.append(md)
	var version := _Number(p.v) if p.has("v") else NAN
	if is_nan(version) or version == 0.0:
		version = float(C.MAP_FORMAT_VERSION)
	var mn := _Number(p.mn) if p.has("mn") else NAN
	if is_nan(mn) or mn == 0.0:
		mn = 2.0
	var mx := _Number(p.mx) if p.has("mx") else NAN
	if is_nan(mx) or mx == 0.0:
		mx = MAX_SEATS
	var sr = p.get("sr")
	var startResources := {}
	for k in ["food", "wood", "gold"]:
		var v := _Number(sr[k]) if sr is Dictionary and sr.has(k) else NAN
		if is_nan(v) or v == 0.0:
			v = float(C.START_RESOURCES[k])
		startResources[k] = maxf(0.0, v)
	var biome = p.get("b")
	var nomad = p.get("nm")
	var out := {
		"id": _newId(),
		"name": (_String(p.n) if p.has("n") and p.n != null else "Imported map").substr(0, 40),
		"desc": (_String(p.d) if p.has("d") and p.d != null else "").substr(0, 160),
		"version": _numOut(version),
		"biome": biome if biome is String and ["temperate", "arid", "alpine", "wetland"].has(biome) else "temperate",
		"cols": int(cols),
		"rows": int(rows),
		"terrain": terrain,
		"resources": resources,
		"spawns": spawns,
		"modes": modes,
		"minPlayers": _numOut(maxf(1.0, minf(MAX_SEATS, mn))),
		"maxPlayers": _numOut(maxf(1.0, minf(MAX_SEATS, mx))),
		"nomad": nomad if nomad is String and ["off", "optional", "forced"].has(nomad) else "off",
		"startResources": startResources,
		"updated": JS.now_ms(),
		"published": 0.0,
	}
	if _truthy(p.get("au")):
		out["author"] = _String(p.au).substr(0, 24)
	return out


static func _cell(v, TILE: float) -> float:
	return floor(_Number(v)) * TILE + TILE / 2.0


# -------------------------------------------------------------- validation --

## How far from a spawn each resource has to be to count as "at your base".
const NEAR_BASE := 14 # cells


## Everything wrong with a map, split into what stops it being playable and
## what merely makes it a bad map. Errors block playing; warnings do not,
## because an author is allowed to make something deliberately strange.
static func validateMap(m: Dictionary) -> Array:
	var TILE := float(C.TILE)
	var BLOCKS := TerrainKinds.TERRAIN_BLOCKS
	var BUILDABLE := TerrainKinds.TERRAIN_BUILDABLE
	var issues: Array = []
	var err := func(text: String) -> void: issues.append({"level": "error", "text": text})
	var warn := func(text: String) -> void: issues.append({"level": "warning", "text": text})
	var cols: int = m.cols
	var rows: int = m.rows
	var terrain: PackedByteArray = m.terrain
	var spawns: Array = m.spawns
	var modes: Array = m.modes

	if String(m.name).strip_edges() == "":
		err.call("The map has no name.")
	if m.maxPlayers < m.minPlayers:
		err.call("Maximum players is below the minimum.")

	# ---- spawns ----
	var needSpawns = 0 if m.nomad == "forced" else m.maxPlayers
	if m.nomad == "forced" and spawns.size():
		warn.call("This map is always nomad, so its spawn points are ignored.")
	if spawns.size() < needSpawns:
		err.call("%d spawn point%s for up to %s players. Place %s more, or lower the maximum to %d." % [
			spawns.size(), "" if spawns.size() == 1 else "s", JS.num(m.maxPlayers),
			JS.num(needSpawns - spawns.size()), spawns.size()])
	for i in spawns.size():
		var s: Dictionary = spawns[i]
		var cx := floori(s.x / TILE)
		var cy := floori(s.y / TILE)
		if not _inB(cx, cy, cols, rows):
			err.call("Spawn %d is off the map." % (i + 1))
			continue
		# A Town Centre is 3 tiles; leave a ring around it so the first villagers
		# are not born inside a mountain.
		var room := true
		for dy in range(-2, 3):
			for dx in range(-2, 3):
				if not _inB(cx + dx, cy + dy, cols, rows) or not BUILDABLE[terrain[(cy + dy) * cols + cx + dx]]:
					room = false
					break
			if not room:
				break
		if not room:
			err.call("Spawn %d has no room for a Town Centre — clear the ground around it." % (i + 1))

	# ---- reachability ----
	# Component labels make this exact and instant: if two spawns are in
	# different pockets, no route between them exists at all.
	if spawns.size() > 1:
		var grid := NavRegions.new(cols, rows)
		for cy in rows:
			for cx in cols:
				if BLOCKS[terrain[cy * cols + cx]]:
					grid.setBlocked(cx, cy, true)
		var ax := floori(spawns[0].x / TILE)
		var ay := floori(spawns[0].y / TILE)
		for i in range(1, spawns.size()):
			var bx := floori(spawns[i].x / TILE)
			var by := floori(spawns[i].y / TILE)
			if grid.provablyUnreachable(ax, ay, bx, by):
				err.call("Spawn %d is walled off from spawn 1 — no army could ever reach it." % (i + 1))
		# King of the Hill is fought over the centre, so the centre has to be
		# somewhere everybody can actually get to.
		if modes.has("koth"):
			var mc := floori(cols / 2.0)
			var mr := floori(rows / 2.0)
			if BLOCKS[terrain[mr * cols + mc]]:
				err.call("King of the Hill: the centre of the map is impassable.")
			elif grid.provablyUnreachable(ax, ay, mc, mr):
				err.call("King of the Hill: the centre is unreachable from spawn 1.")

	# ---- resources ----
	var counts := {"tree": 0, "gold_mine": 0, "berries": 0}
	for r in m.resources:
		counts[r.type] += 1
	if not counts.tree:
		err.call("There is no wood anywhere on the map.")
	if not counts.gold_mine:
		warn.call("There is no gold on the map — nothing past the Banner Age can be trained.")
	if not counts.berries:
		warn.call("There is no food on the map besides farms.")

	if m.nomad != "forced":
		for i in spawns.size():
			var s: Dictionary = spawns[i]
			var near := {"tree": 0, "gold_mine": 0, "berries": 0}
			for r in m.resources:
				var d := JS.hypot(r.x - s.x, r.y - s.y) / TILE
				if d <= NEAR_BASE:
					near[r.type] += 1
			if not near.tree:
				warn.call("Spawn %d has no wood within reach of its base." % (i + 1))
			if not near.berries and not near.gold_mine:
				warn.call("Spawn %d starts with no food or gold nearby." % (i + 1))
		# Fairness: seats should be roughly as well supplied as each other.
		if spawns.size() > 1:
			var lo := INF
			var hi := -INF
			for s in spawns:
				var score := 0.0
				for r in m.resources:
					score += float(r.amount) if JS.hypot(r.x - s.x, r.y - s.y) / TILE <= NEAR_BASE else 0.0
				lo = minf(lo, score)
				hi = maxf(hi, score)
			if hi > 0.0 and lo < hi * 0.6:
				warn.call("Some starts are much richer than others — the seats are not equal.")

	# ---- mode-specific ----
	if modes.has("survival"):
		# Waves arrive from the edges, so the edges must not be sealed.
		var openEdge := 0
		var edge := 0
		for cx in cols:
			for cy in [0, rows - 1]:
				edge += 1
				if not BLOCKS[terrain[cy * cols + cx]]:
					openEdge += 1
		for cy in rows:
			for cx in [0, cols - 1]:
				edge += 1
				if not BLOCKS[terrain[cy * cols + cx]]:
					openEdge += 1
		if openEdge < edge * 0.25:
			err.call("Survival: the map edges are walled in, so waves would have nowhere to spawn.")
		if m.maxPlayers > 4:
			warn.call("Survival is co-op against the horde — more than four defenders is unusual.")
	if modes.has("regicide") and m.nomad == "forced":
		warn.call("Regicide on a nomad map: Kings land wherever their villagers do.")

	var blocked := 0
	for t in terrain:
		if BLOCKS[t]:
			blocked += 1
	if blocked > terrain.size() * 0.55:
		warn.call("More than half the map is impassable — there may not be room to play.")
	return issues


static func _inB(cx: int, cy: int, cols: int, rows: int) -> bool:
	return cx >= 0 and cy >= 0 and cx < cols and cy < rows


static func hasErrors(issues: Array) -> bool:
	return issues.any(func(i): return i.level == "error")


## Whether a map allows a given match setup, for the skirmish map list.
static func mapSupports(m: Dictionary, mode: String, players: int) -> bool:
	if m.modes.size() and not m.modes.has(mode):
		return false
	return players >= m.minPlayers and players <= m.maxPlayers


## The reachability half of pathfinding/grid.ts's NavGrid — `setBlocked`,
## `regionAt` and `provablyUnreachable` with the same four-way flood fill — so
## validation doesn't wait on the pathfinding port. Swap for NavGrid once it
## lands; the answers are identical by construction.
class NavRegions:
	var cols: int
	var rows: int
	var blocked: PackedByteArray
	var region := PackedInt32Array()
	var regionsDirty := true

	func _init(c: int, r: int) -> void:
		cols = c
		rows = r
		blocked = PackedByteArray()
		blocked.resize(cols * rows)

	func setBlocked(cx: int, cy: int, v: bool) -> void:
		if cx < 0 or cy < 0 or cx >= cols or cy >= rows:
			return
		var i := cy * cols + cx
		var next := 1 if v else 0
		if blocked[i] == next:
			return
		blocked[i] = next
		regionsDirty = true

	## The component a cell belongs to, or -1 if it is blocked or out of bounds.
	func regionAt(cx: int, cy: int) -> int:
		if cx < 0 or cy < 0 or cx >= cols or cy >= rows:
			return -1
		if regionsDirty or region.is_empty():
			rebuildRegions()
		return region[cy * cols + cx]

	## True when the two cells provably have no route between them; "don't
	## know" (false) if either end sits on a blocked cell.
	func provablyUnreachable(ax: int, ay: int, bx: int, by: int) -> bool:
		var ra := regionAt(ax, ay)
		if ra < 0:
			return false
		var rb := regionAt(bx, by)
		if rb < 0:
			return false
		return ra != rb

	func rebuildRegions() -> void:
		var n := cols * rows
		region.resize(n)
		region.fill(-1)
		var queue := PackedInt32Array()
		queue.resize(n)
		var label := 0
		for seed in n:
			if blocked[seed] == 1 or region[seed] != -1:
				continue
			var head := 0
			var tail := 0
			queue[tail] = seed
			tail += 1
			region[seed] = label
			while head < tail:
				var cur := queue[head]
				head += 1
				var cx := cur % cols
				var cy := cur / cols
				# Four-way only: A* may move diagonally, but never *between* two
				# blocked orthogonals, so a diagonal-only link is not a real route.
				if cx > 0:
					var i := cur - 1
					if blocked[i] == 0 and region[i] == -1:
						region[i] = label
						queue[tail] = i
						tail += 1
				if cx < cols - 1:
					var i := cur + 1
					if blocked[i] == 0 and region[i] == -1:
						region[i] = label
						queue[tail] = i
						tail += 1
				if cy > 0:
					var i := cur - cols
					if blocked[i] == 0 and region[i] == -1:
						region[i] = label
						queue[tail] = i
						tail += 1
				if cy < rows - 1:
					var i := cur + cols
					if blocked[i] == 0 and region[i] == -1:
						region[i] = label
						queue[tail] = i
						tail += 1
			label += 1
		regionsDirty = false


# ------------------------------------------------------------- to MapData --

## The other direction: take a generated map and hand it back as something you
## can paint on.
##
## Starting from a blank field means a lot of painting before there is anything
## to react to, and the generator is already good at the boring parts — a
## coastline, a scatter of woods, gold that isn't all in one place. Rolling one
## and then editing it is how most maps actually get made, so it is worth being
## a first-class way in rather than something you fake by exporting a match.
##
## The generator's `starts` become authored spawn points, which is the whole
## reason this is useful: the seats are already fair, and the author is free to
## move them.
static func customFromGenerated(preset: String, seed: int, players: int, name = null) -> Dictionary:
	var g := MapGen.generateMap(preset, seed, players)
	var base := newCustomMap(name if name != null else "%s %d" % [g.name, seed], g.cols, g.biome)
	base.rows = g.rows
	base.terrain = g.terrain.duplicate()
	base.resources = g.resources.map(func(r): return r.duplicate())
	base.spawns = g.starts.map(func(s): return {"x": s.x, "y": s.y})
	base.startResources = g.startResources.duplicate()
	# Seats: exactly the ones the generator laid out. The ceiling has to match
	# what is actually on the map rather than the engine's maximum — a map that
	# claims eight seats and shows two is an error, and arriving in the editor
	# with an error already on the board is a poor greeting. Raising it is one
	# click once the author has placed more.
	base.minPlayers = mini(2, base.spawns.size() if base.spawns.size() else 1)
	base.maxPlayers = maxi(1, base.spawns.size())
	base.desc = "Rolled from %s, seed %d." % [g.name, seed]
	return base


## Turn an authored map into the MapData one match consumes.
##
## Nomad is resolved here and nowhere else: a forced-nomad map has no spawn
## points at all, an optional-nomad map ignores them only when the match asks
## for nomad, and everything else seats players at the points the author placed.
## Random landing sites are drawn from the match seed, so a nomad game on a
## custom map is as replayable as any other.
static func toMapData(m: Dictionary, seed: int, players: int, nomad: bool = false) -> Dictionary:
	var TILE := float(C.TILE)
	var BLOCKS := TerrainKinds.TERRAIN_BLOCKS
	var terrain: PackedByteArray = m.terrain.duplicate() # the match must never edit the author's copy
	var cols: int = m.cols
	var rows: int = m.rows
	var blockedCells: Array = []
	for cy in rows:
		for cx in cols:
			if BLOCKS[terrain[cy * cols + cx]]:
				blockedCells.append([cx, cy])
	var useNomad: bool = m.nomad == "forced" or (nomad and m.nomad != "off")
	var starts: Array
	if useNomad:
		starts = randomStarts(m, seed, players)
	else:
		starts = m.spawns.slice(0, players).map(func(s): return s.duplicate())
		# Fewer authored spawns than players should have been caught in validation;
		# if it happens anyway, put the extras somewhere legal rather than crash.
		while starts.size() < players:
			starts.append_array(randomStarts(m, seed + starts.size(), 1))
	return {
		"name": m.name,
		"worldW": cols * TILE,
		"worldH": rows * TILE,
		"cols": cols,
		"rows": rows,
		"terrain": terrain,
		"blockedCells": blockedCells,
		"resources": m.resources.map(func(r): return r.duplicate()),
		"starts": starts,
		"startResources": m.startResources.duplicate(),
		"seed": seed,
		"biome": m.biome,
		"custom": true,
		"modes": m.modes,
		"nomad": useNomad,
	}


## Well-spaced landing sites on open, buildable ground.
static func randomStarts(m: Dictionary, seed: int, players: int) -> Array:
	var TILE := float(C.TILE)
	var BUILDABLE := TerrainKinds.TERRAIN_BUILDABLE
	var rng := RNG.new(seed)
	var cols: int = m.cols
	var rows: int = m.rows
	var terrain: PackedByteArray = m.terrain
	var margin := maxi(4, floori(cols * 0.1))
	var minSep := cols * (0.26 if players >= 4 else 0.36)
	var roomFor := func(cx: int, cy: int) -> bool:
		for dy in range(-2, 3):
			for dx in range(-2, 3):
				var nx := cx + dx
				var ny := cy + dy
				if nx < 0 or ny < 0 or nx >= cols or ny >= rows:
					return false
				if not BUILDABLE[terrain[ny * cols + nx]]:
					return false
		return true
	var pts: Array = []
	var cells: Array = []
	var guard := 0
	while pts.size() < players:
		var g := guard
		guard += 1
		if not (g < 8000):
			break
		var cx := rng.int_(margin, cols - 1 - margin)
		var cy := rng.int_(margin, rows - 1 - margin)
		if not roomFor.call(cx, cy):
			continue
		var spaced := true
		for c in cells:
			if float((c[0] - cx) * (c[0] - cx) + (c[1] - cy) * (c[1] - cy)) <= minSep * minSep:
				spaced = false
				break
		if not spaced:
			continue
		cells.append([cx, cy])
		pts.append({"x": cx * TILE + TILE / 2.0, "y": cy * TILE + TILE / 2.0})
	# A crowded or hostile map may not have room for the spacing we wanted;
	# relax it rather than hand back fewer starts than there are players.
	guard = 0
	while pts.size() < players:
		var g := guard
		guard += 1
		if not (g < 8000):
			break
		var cx := rng.int_(margin, cols - 1 - margin)
		var cy := rng.int_(margin, rows - 1 - margin)
		if not roomFor.call(cx, cy):
			continue
		pts.append({"x": cx * TILE + TILE / 2.0, "y": cy * TILE + TILE / 2.0})
	while pts.size() < players:
		pts.append({"x": (cols / 2.0) * TILE, "y": (rows / 2.0) * TILE})
	return pts


# ----------------------------------------------------------------- storage --

const STORE_KEY := "bb_custom_maps"


# A library entry is `id|meta|code`, meta being `m<published>.<updated>` in
# base 36. Entries from before publishing existed are `id|code`: those maps
# were already in the Skirmish list, so they come back published rather than
# quietly vanishing from it.
static func entryOf(m: Dictionary) -> String:
	var published = m.get("published")
	return "%s|m%s.%s|%s" % [m.id, _toString36(published if published != null else 0.0), _toString36(m.updated), serialiseMap(m)]


static func parseEntry(entry: String):
	var a := entry.find("|")
	if a < 0:
		return null
	var id := entry.substr(0, a)
	var rest := entry.substr(a + 1)
	var published := JS.now_ms()
	var updated := 0.0
	if not rest.begins_with("BBMAP"):
		var b := rest.find("|")
		if b < 0:
			return null
		var meta := RegEx.create_from_string("^m([0-9a-z]+)\\.([0-9a-z]+)$").search(rest.substr(0, b))
		rest = rest.substr(b + 1)
		published = _parseInt36(meta.get_string(1)) if meta else 0.0
		updated = _parseInt36(meta.get_string(2)) if meta else 0.0
	var m = deserialiseMap(rest)
	if m == null:
		return null
	m.id = id if id != "" else m.id
	m.published = published
	if updated:
		m.updated = updated
	return m


static func writeAll(all: Array) -> void:
	Storage.setItem(STORE_KEY, jsonStringify(all.slice(0, 40).map(func(m): return entryOf(m))))


## Every saved map, newest first. Bad entries are skipped, never thrown.
static func listCustomMaps() -> Array:
	var raw = Storage.getItem(STORE_KEY)
	if raw == null or raw == "":
		return []
	var json := JSON.new()
	if json.parse(raw) != OK or not (json.data is Array):
		return []
	var out: Array = []
	for entry in json.data:
		if not (entry is String):
			return [] # entry.indexOf throws in the TS, which empties the list
		var m = parseEntry(entry)
		if m != null:
			out.append(m)
	return JS.sort_by_cmp(out, func(a, b): return b.updated - a.updated)


static func saveCustomMap(m: Dictionary) -> void:
	m.updated = JS.now_ms()
	# Saving never changes whether a map is published: keep what the library has.
	var all := listCustomMaps()
	var prev = null
	for x in all:
		if x.id == m.id:
			prev = x
			break
	if not m.has("published"):
		m.published = prev.get("published", 0.0) if prev != null and prev.get("published") != null else 0.0
	var rest: Array = [m]
	rest.append_array(all.filter(func(x): return x.id != m.id))
	writeAll(rest)


static func deleteCustomMap(id: String) -> void:
	writeAll(listCustomMaps().filter(func(x): return x.id != id))


static func findCustomMap(id: String):
	for m in listCustomMaps():
		if m.id == id:
			return m
	return null


# ------------------------------------------------------------ the map pool --
#
# Making a map and offering it to the game are two steps. A map is a draft
# while its author works on it (the editor can still test it); publishing puts
# it in the pool beside the built-in battlefields — on the Skirmish list, in
# the Random roll, and in the host's choices for an online match. Only a map
# with no errors can be published: the pool is what other people get handed.

## Put a map in the pool. Returns the errors that stop it, or [] on success.
static func publishCustomMap(m: Dictionary, author = null) -> Array:
	var errors := validateMap(m).filter(func(i): return i.level == "error")
	if errors.size():
		return errors
	if _truthy(author) and not _truthy(m.get("author")):
		m.author = String(author).substr(0, 24)
	m.published = JS.now_ms()
	saveCustomMap(m)
	return []


## Take a map back out of the pool; it stays in the library as a draft.
static func unpublishCustomMap(m: Dictionary) -> void:
	m.published = 0.0
	saveCustomMap(m)


static func isPublished(m: Dictionary) -> bool:
	return _truthy(m.get("published"))


## The published maps that can host this match.
static func mapPool(mode: String, players: int) -> Array:
	return listCustomMaps().filter(func(m): return isPublished(m) and mapSupports(m, mode, players))


## What "Random" lands on. Every built-in battlefield and every published map
## that fits gets one chance each, drawn from the seed. A built-in result stays
## "random" — the generator rolls which preset from the same seed, exactly as it
## always has, so old saves replay the same field — and a published map comes
## back as its own id. `pool` is an Array of anything with an `id`.
static func rollRandomMap(seed: int, pool: Array, presets: int) -> String:
	if pool.is_empty():
		return "random"
	var rng := RNG.new(JS.u32(seed ^ 0x9a9b00c))
	var i := rng.int_(0, presets + pool.size() - 1)
	return "random" if i < presets else String(pool[i - presets].id)


# ------------------------------------------------- JS value semantics --
# The map code is a string other people paste in, so decoding it has to treat
# odd values exactly as the browser build does (Number("12") is 12, a missing
# field is NaN, and so on), and encoding it has to write what JSON.stringify
# writes so a code made here reads the same everywhere.

## Number(v) for a JSON value.
static func _Number(v) -> float:
	if is_same(v, _UNDEFINED):
		return NAN
	match typeof(v):
		TYPE_NIL:
			return 0.0
		TYPE_BOOL:
			return 1.0 if v else 0.0
		TYPE_INT, TYPE_FLOAT:
			return float(v)
		TYPE_STRING, TYPE_STRING_NAME:
			return _stringToNumber(String(v))
		TYPE_ARRAY:
			return _stringToNumber(_String(v))
	return NAN


static func _stringToNumber(s: String) -> float:
	var t := s.strip_edges()
	if t == "":
		return 0.0
	if t == "Infinity" or t == "+Infinity":
		return INF
	if t == "-Infinity":
		return -INF
	var lower := t.to_lower()
	if lower.begins_with("0x") or lower.begins_with("0o") or lower.begins_with("0b"):
		var base := 16 if lower[1] == "x" else (8 if lower[1] == "o" else 2)
		var digits := lower.substr(2)
		if digits == "":
			return NAN
		var val := 0.0
		for ch in digits:
			var d := "0123456789abcdef".find(ch)
			if d < 0 or d >= base:
				return NAN
			val = val * base + d
		return val
	if t.is_valid_int():
		return t.to_float()
	if _numRe == null:
		_numRe = RegEx.create_from_string("^[+-]?(\\d+\\.?\\d*([eE][+-]?\\d+)?|\\.\\d+([eE][+-]?\\d+)?)$")
	if _numRe.search(t) == null:
		return NAN
	return t.to_float()


## String(v) for a JSON value.
static func _String(v) -> String:
	if is_same(v, _UNDEFINED):
		return "undefined"
	match typeof(v):
		TYPE_NIL:
			return "null"
		TYPE_BOOL:
			return "true" if v else "false"
		TYPE_INT, TYPE_FLOAT:
			return _numStr(float(v))
		TYPE_STRING, TYPE_STRING_NAME:
			return String(v)
		TYPE_ARRAY:
			var parts := PackedStringArray()
			for x in v:
				parts.append("" if x == null else _String(x))
			return ",".join(parts)
	return "[object Object]"


## v[i] for a JSON value, or _UNDEFINED where JS would give undefined.
static func _at(v, i: int):
	if v is Array:
		return v[i] if i < v.size() else _UNDEFINED
	if v is String:
		return v[i] if i < v.length() else _UNDEFINED
	if v is Dictionary:
		return v.get(str(i), _UNDEFINED)
	return _UNDEFINED


## Stand-in for `undefined` from _at: String() gives "undefined" (no resource
## letter) and Number() gives NaN, as in JS.
const _UNDEFINED := {"__undefined__": true}


static func _isInteger(x: float) -> bool:
	return is_finite(x) and floor(x) == x


static func _truthy(v) -> bool:
	match typeof(v):
		TYPE_NIL:
			return false
		TYPE_BOOL:
			return v
		TYPE_INT:
			return v != 0
		TYPE_FLOAT:
			return v != 0.0 and not is_nan(v)
		TYPE_STRING, TYPE_STRING_NAME:
			return String(v) != ""
	return true


## A whole number as int, anything else as float (counts that came in from a
## pasted code may be fractional, as they may in the browser build).
static func _numOut(x: float):
	return int(x) if _isInteger(x) and absf(x) < 9.0e15 else x


## Number.prototype.toString() as JSON and template strings write it.
static func _numStr(x: float) -> String:
	if is_nan(x):
		return "NaN"
	if is_inf(x):
		return "Infinity" if x > 0.0 else "-Infinity"
	if _isInteger(x) and absf(x) < 1e21:
		if absf(x) < 9.0e15:
			return str(int(x))
		return String.num(x, 0)
	var s := var_to_str(x)
	# Godot writes 1e-07; JS writes 1e-7 (and 1e+21).
	var e := s.find("e")
	if e >= 0:
		var mant := s.substr(0, e)
		var ex := s.substr(e + 1)
		var neg := ex.begins_with("-")
		ex = ex.trim_prefix("-").trim_prefix("+").lstrip("0")
		s = mant + "e" + ("-" if neg else "+") + (ex if ex != "" else "0")
	return s


## (n).toString(36) for a whole number.
static func _toString36(x: float) -> String:
	if x < 0.0:
		return "-" + String.num_int64(int(-x), 36)
	return String.num_int64(int(x), 36)


## parseInt(s, 36) for a string of [0-9a-z].
static func _parseInt36(s: String) -> float:
	var v := 0.0
	for ch in s:
		var d := "0123456789abcdefghijklmnopqrstuvwxyz".find(ch)
		if d < 0:
			break
		v = v * 36.0 + d
	return v


## JSON.stringify for the values the map format writes (Dictionary keys in
## insertion order, JS number formatting, JS string escaping).
static func jsonStringify(v) -> String:
	match typeof(v):
		TYPE_NIL:
			return "null"
		TYPE_BOOL:
			return "true" if v else "false"
		TYPE_INT:
			return str(v)
		TYPE_FLOAT:
			return "null" if is_nan(v) or is_inf(v) else _numStr(v)
		TYPE_STRING, TYPE_STRING_NAME:
			return _jsonQuote(String(v))
		TYPE_ARRAY, TYPE_PACKED_STRING_ARRAY, TYPE_PACKED_INT32_ARRAY, TYPE_PACKED_FLOAT64_ARRAY:
			var parts := PackedStringArray()
			for x in v:
				parts.append(jsonStringify(x))
			return "[" + ",".join(parts) + "]"
		TYPE_DICTIONARY:
			var parts := PackedStringArray()
			for k in v:
				parts.append(_jsonQuote(String(k)) + ":" + jsonStringify(v[k]))
			return "{" + ",".join(parts) + "}"
	return "null"


static func _jsonQuote(s: String) -> String:
	var out := "\""
	var start := 0
	var n := s.length()
	for i in n:
		var c := s.unicode_at(i)
		if c >= 0x20 and c != 0x22 and c != 0x5C:
			continue
		out += s.substr(start, i - start)
		match c:
			0x22:
				out += "\\\""
			0x5C:
				out += "\\\\"
			0x08:
				out += "\\b"
			0x0C:
				out += "\\f"
			0x0A:
				out += "\\n"
			0x0D:
				out += "\\r"
			0x09:
				out += "\\t"
			_:
				out += "\\u%04x" % c
		start = i + 1
	out += s.substr(start)
	return out + "\""
