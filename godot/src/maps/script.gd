## Random map scripting.
##
## Port of game/src/maps/script.ts (class_name MapScript: `Script` is taken by
## Godot). SCRIPT_COMMANDS and EXAMPLE_SCRIPT are data, in `C`.
##
## The editor makes one map. A script makes a *family* of them — same rules,
## different seed — which is how competitive map pools actually work: nobody
## ships "Arabia, the map", they ship Arabia the generator and roll it fresh
## each game.
##
## The generator in generator.ts is already exactly this, with the rules
## hard-coded and a preset list standing in for parameters. This exposes the
## same primitives as a small text format, so an author can write down what they
## mean and get a different map every seed.
##
## Design constraints, in order:
##
##   • **Everything is optional.** An empty script is a valid map. Every command
##     has a default, because a format where you must say twelve things before
##     you can say the one thing you care about is a format nobody uses.
##   • **Errors name the line and say what was expected.** A script language
##     with a silent failure mode is worse than no script language: you get a
##     map, it just isn't the one you wrote.
##   • **Deterministic.** Same script and same seed give the same map, always.
##     Otherwise a share code for a script is meaningless.
##   • **It produces a CustomMap.** Not a MapData — a scripted map lands in the
##     editor, where it can be tweaked, validated, saved and shared like any
##     other. A script is a starting point, not a walled garden.
##
## ScriptError is {line: int (1-based, so it matches what an author sees in the
## box), text: String}. ScriptResult is {map: CustomMap Dictionary or null,
## errors: Array[ScriptError], warnings: Array[ScriptError]} — warnings are
## non-fatal remarks: the script ran, but something may not be what you meant.
class_name MapScript
extends RefCounted

const Terrain = TerrainKinds.Terrain

## Words an author is more likely to reach for than the palette's own names.
const TERRAIN_WORDS := {
	"grass": Terrain.Grass, "meadow": Terrain.GrassDark, "grass_dark": Terrain.GrassDark,
	"dirt": Terrain.Dirt, "road": Terrain.Dirt, "sand": Terrain.Sand, "snow": Terrain.Snow,
	"hill": Terrain.Hill, "hills": Terrain.Hill, "highground": Terrain.Hill,
	"forest": Terrain.Forest, "wood": Terrain.Forest, "woods": Terrain.Forest,
	"marsh": Terrain.Marsh, "bog": Terrain.Marsh, "swamp": Terrain.Marsh,
	"shallow": Terrain.Shallow, "shallows": Terrain.Shallow, "ford": Terrain.Shallow,
	"water": Terrain.Water, "lake": Terrain.Water, "sea": Terrain.Water,
	"rock": Terrain.Rock, "mountain": Terrain.Rock, "mountains": Terrain.Rock, "cliff": Terrain.Rock,
}

static var _terrainByWord: Dictionary = {}


## Terrain by the name an author would write.
static func TERRAIN_BY_WORD() -> Dictionary:
	if _terrainByWord.is_empty():
		var out := {}
		# The palette's own display names, so anything shown in the editor's ground
		# list can be typed here verbatim.
		for d in C.TERRAIN_DEFS:
			out[String(d.name).to_lower()] = int(d.id)
		out.merge(TERRAIN_WORDS, true)
		_terrainByWord = out
	return _terrainByWord


const RESOURCE_BY_WORD := {
	"tree": "tree", "trees": "tree", "wood": "tree", "forest": "tree",
	"gold": "gold_mine", "gold_mine": "gold_mine", "mine": "gold_mine",
	"berries": "berries", "berry": "berries", "food": "berries",
}

const RES_AMOUNT := {"tree": 160.0, "gold_mine": 1000.0, "berries": 250.0}

const KEYS := ["count", "radius", "nodes", "width", "near", "at"]


# ------------------------------------------------------------------ parsing --

## Split a line into a command and its named arguments:
## {line: int, word: String, args: Array[String], named: Dictionary — `key
## value` pairs after the first word, e.g. `count 6`}.
##
## The grammar is deliberately "a word, then some words, then key/value pairs
## in any order" rather than positional. `blob rock count 8 radius 3-7` and
## `blob rock radius 3-7 count 8` are the same thing, because remembering an
## argument order is exactly the kind of friction that stops people writing one.
static func tokenise(raw: String, line: int):
	var hash := raw.find("#") # # starts a comment
	var stripped := (raw if hash < 0 else raw.substr(0, hash)).strip_edges()
	if stripped == "":
		return null
	var parts: Array = []
	for m in _wsRe().search_all(stripped):
		parts.append(m.get_string())
	var word := String(parts[0]).to_lower()
	var rest := parts.slice(1)
	var named := {}
	var args: Array = []
	var i := 0
	while i < rest.size():
		var k := String(rest[i]).to_lower()
		if KEYS.has(k) and i + 1 < rest.size():
			named[k] = rest[i + 1]
			i += 1
		else:
			args.append(rest[i])
		i += 1
	return {"line": line, "word": word, "args": args, "named": named}


static var _ws: RegEx = null
static var _range: RegEx = null


static func _wsRe() -> RegEx:
	if _ws == null:
		_ws = RegEx.create_from_string("\\S+")
	return _ws


## `3-7` or `5` → [3,7] / [5,5]. Returns null when it isn't a range at all.
static func parseRange(s):
	if s == null or s == "":
		return null
	if _range == null:
		_range = RegEx.create_from_string("^(\\d+(?:\\.\\d+)?)(?:\\s*-\\s*(\\d+(?:\\.\\d+)?))?$")
	var m := _range.search(s)
	if m == null:
		return null
	var a := CustomMaps._Number(m.get_string(1))
	var b := a if m.get_start(2) < 0 else CustomMaps._Number(m.get_string(2))
	if not is_finite(a) or not is_finite(b):
		return null
	return [minf(a, b), maxf(a, b)]


# ----------------------------------------------------------------- running --

## Run a script and hand back an editable map.
##
## `seed` is what makes this a family rather than a map: the same script with a
## different seed is a different battlefield with the same character, which is
## the entire point of the format.
static func runMapScript(src: String, seed: int, name: String = "Scripted map") -> Dictionary:
	return _Run.new().run(src, seed, name)


class _Run:
	var errors: Array = []
	var warnings: Array = []
	var map: Dictionary
	var cols: int
	var rows: int
	var rng: RNG
	var sym := "rot180"
	var occupied: PackedByteArray
	var TILE := 32.0 # C.TILE, read in run()

	func idx(cx: int, cy: int) -> int:
		return cy * cols + cx

	func inB(cx: int, cy: int) -> bool:
		return cx >= 0 and cy >= 0 and cx < cols and cy < rows

	func paintBlob(cx: int, cy: int, r: float, t: int) -> void:
		var terrain: PackedByteArray = map.terrain
		var rc := int(ceil(r))
		for a in Symmetry.symmetryCells(sym, cx, cy, cols, rows):
			var ax: int = a[0]
			var ay: int = a[1]
			for dy in range(-rc, rc + 1):
				for dx in range(-rc, rc + 1):
					var d2 := dx * dx + dy * dy
					if d2 > r * r:
						continue
					# A soft edge, so a blob reads as a landform rather than a stamped disc.
					if d2 > (r - 1.0) * (r - 1.0) and rng.bool_(0.45):
						continue
					var nx := ax + dx
					var ny := ay + dy
					if inB(nx, ny):
						terrain[idx(nx, ny)] = t

	## How close a resource may sit to a seat, in cells.
	##
	## A Town Centre is three cells across and its villagers need room to turn
	## around. Without this the script scattered trees *onto* the start — measured
	## on the worked example: two nodes within two cells of both seats, and nine
	## within six cells of one seat against four at the other. A base packed with
	## blocking nodes gathers at half the rate of an open one, which is how a map
	## that is symmetric in every measurable way still played 160 against 430 in
	## the first two minutes.
	##
	## Four cells clears the Town Centre and a working ring around it while leaving
	## the deliberately-close starting clusters (usually authored at six to ten)
	## exactly where the author put them.
	const SPAWN_CLEARANCE := 4

	func tooCloseToSeat(cx: int, cy: int) -> bool:
		for s in map.spawns:
			var sx := floori(s.x / TILE)
			var sy := floori(s.y / TILE)
			if absi(sx - cx) <= SPAWN_CLEARANCE and absi(sy - cy) <= SPAWN_CLEARANCE:
				return true
		return false

	func addNode(cx: int, cy: int, type: String) -> void:
		if not inB(cx, cy):
			return
		var i := idx(cx, cy)
		# Never two nodes on a cell, and never a node inside a wall — a tree in a
		# lake is a resource no villager can ever reach.
		if occupied[i]:
			return
		if TerrainKinds.TERRAIN_BLOCKS[map.terrain[i]]:
			return
		if tooCloseToSeat(cx, cy):
			return
		occupied[i] = 1
		map.resources.append({
			"type": type, "x": cx * TILE + TILE / 2.0, "y": cy * TILE + TILE / 2.0, "amount": RES_AMOUNT[type],
		})

	func terrainArg(c: Dictionary, what: String):
		var word := _arg0(c).to_lower()
		var t = MapScript.TERRAIN_BY_WORD().get(word)
		if t == null:
			errors.append({"line": c.line, "text": "%s wants a terrain, got \"%s\"" % [what, _arg0(c)]})
			return null
		return t

	func countArg(c: Dictionary, dflt: int, max: int = 4000) -> int:
		if not c.named.has("count"):
			return dflt
		var n := CustomMaps._Number(c.named.count)
		if not is_finite(n) or n < 0.0:
			errors.append({"line": c.line, "text": "count wants a number, got \"%s\"" % c.named.count})
			return dflt
		if n > max:
			warnings.append({"line": c.line, "text": "count %s capped at %d" % [CustomMaps._numStr(n), max]})
			return max
		return JS.roundi(n)

	## `c.args[0] ?? ""`
	static func _arg0(c: Dictionary) -> String:
		return String(c.args[0]) if c.args.size() > 0 else ""

	## Number(c.named[key]) — NaN when the key is absent, as for undefined.
	static func _named(c: Dictionary, key: String) -> float:
		return CustomMaps._Number(c.named[key]) if c.named.has(key) else NAN

	## rng.int(min, max) with fractional bounds: the TS passes `cols * 0.25`, and
	## RNG.int is `min + Math.floor(next() * (max - min + 1))`, so the result is
	## fractional too. RNG.int_ takes ints, hence this.
	func rngInt(lo: float, hi: float) -> float:
		return lo + floor(rng.next() * (hi - lo + 1.0))

	func run(src: String, seed: int, name: String) -> Dictionary:
		var MAX_SEATS := int(C.MAX_SEATS)
		TILE = float(C.TILE)
		var cmds: Array = []
		var lines := src.split("\n")
		for i in lines.size():
			var raw := lines[i]
			if raw.ends_with("\r"):
				raw = raw.substr(0, raw.length() - 1)
			var c = MapScript.tokenise(raw, i + 1)
			if c != null:
				cmds.append(c)

		# --- header pass: everything that decides the shape of the array ---------
		var size := 128
		var biome := "temperate"
		var seats := 2
		sym = "rot180"
		for c in cmds:
			if c.word == "size":
				var n := CustomMaps._Number(c.args[0]) if c.args.size() > 0 else NAN
				if not is_finite(n) or n < 40.0 or n > 320.0:
					errors.append({"line": c.line, "text": "size wants a number from 40 to 320, got \"%s\"" % _arg0(c)})
				else:
					size = JS.roundi(n)
			elif c.word == "biome":
				var found = null
				for x in C.BIOMES:
					if x.id == _arg0(c).to_lower():
						found = x
						break
				if found == null:
					errors.append({"line": c.line, "text": "unknown biome \"%s\" — try %s" % [_arg0(c), ", ".join(C.BIOMES.map(func(x): return x.id))]})
				else:
					biome = found.id
			elif c.word == "seats":
				var n := CustomMaps._Number(c.args[0]) if c.args.size() > 0 else NAN
				if not CustomMaps._isInteger(n) or n < 1.0 or n > MAX_SEATS:
					errors.append({"line": c.line, "text": "seats wants a whole number from 1 to %d" % MAX_SEATS})
				else:
					seats = int(n)
			elif c.word == "symmetry":
				var id := _arg0(c).to_lower()
				var found = null
				for x in C.SYMMETRIES:
					if String(x.id).to_lower() == id:
						found = x
						break
				if found == null:
					errors.append({"line": c.line, "text": "unknown symmetry \"%s\" — try %s" % [_arg0(c), ", ".join(C.SYMMETRIES.map(func(x): return x.id))]})
				else:
					sym = found.id
		if errors.size():
			return {"map": null, "errors": errors, "warnings": warnings}

		map = CustomMaps.newCustomMap(name, size, biome)
		cols = map.cols
		rows = map.rows
		rng = RNG.new(seed)
		occupied = PackedByteArray()
		occupied.resize(cols * rows)

		# --- body pass ------------------------------------------------------------
		for c in cmds:
			match c.word:
				"size", "biome", "seats", "symmetry":
					pass # handled above

				"base":
					var t = terrainArg(c, "base")
					if t != null:
						map.terrain.fill(t)

				# `lake water …` and `range mountain …` read better than `blob` for the
				# two things authors reach for most; they are the same operation.
				"blob", "patch", "lake", "range":
					var t = terrainArg(c, c.word)
					if t == null:
						continue
					var n := countArg(c, 8, 600)
					var r = MapScript.parseRange(c.named.get("radius"))
					if r == null:
						r = [2.0, 5.0]
					for i in n:
						var bx := rng.int_(0, cols - 1)
						var by := rng.int_(0, rows - 1)
						var br := rng.range(r[0], r[1])
						paintBlob(bx, by, br, t)

				"ring":
					var t = terrainArg(c, "ring")
					if t == null:
						continue
					var pr = MapScript.parseRange(c.named.get("radius"))
					var frac: float = pr[0] if pr != null else 0.35
					var wn := _named(c, "width")
					var width := maxf(1.0, wn if not is_nan(wn) and wn != 0.0 else 3.0)
					var ox := float(cols - 1) / 2.0
					var oy := float(rows - 1) / 2.0
					var rad := frac * mini(cols, rows) / 2.0
					var terrain: PackedByteArray = map.terrain
					for cy in rows:
						for cx in cols:
							var d := JS.hypot(cx - ox, cy - oy)
							if absf(d - rad) <= width / 2.0:
								terrain[idx(cx, cy)] = t

				"river":
					var t = terrainArg(c, "river")
					if t == null:
						continue
					var wn := _named(c, "width")
					var width := maxf(1.0, wn if not is_nan(wn) and wn != 0.0 else 3.0)
					var n := countArg(c, 1, 12)
					for k in n:
						# A drunkard's walk from one edge to the other: straight enough to be
						# a river, wobbly enough not to be a canal.
						var vertical := rng.bool_(0.5)
						var x := rngInt(cols * 0.25, cols * 0.75) if vertical else 0.0
						var y := 0.0 if vertical else rngInt(rows * 0.25, rows * 0.75)
						var steps := rows if vertical else cols
						for s in steps:
							paintBlob(JS.roundi(x), JS.roundi(y), width / 2.0, t)
							if vertical:
								y += 1.0
								x += rng.range(-0.8, 0.8)
							else:
								x += 1.0
								y += rng.range(-0.8, 0.8)
							x = maxf(1.0, minf(cols - 2.0, x))
							y = maxf(1.0, minf(rows - 2.0, y))

				"spawns":
					# `spawns ring radius 0.36` — the common case, and the one that makes a
					# symmetric map fair without the author placing anything by hand.
					var pr = MapScript.parseRange(c.named.get("radius"))
					var frac: float = pr[0] if pr != null else 0.36
					var ox := float(cols - 1) / 2.0
					var oy := float(rows - 1) / 2.0
					var rad := frac * mini(cols, rows)
					map.spawns = []
					for i in seats:
						var a := (float(i) / seats) * PI * 2.0 - PI / 2.0
						var cx := JS.roundi(ox + JSMath.cos(a) * rad)
						var cy := JS.roundi(oy + JSMath.sin(a) * rad)
						map.spawns.append({"x": cx * TILE + TILE / 2.0, "y": cy * TILE + TILE / 2.0})

				"spawn":
					var src_at: String = c.named.at if c.named.has("at") else _arg0(c)
					var at := src_at.split(",")
					var cx := CustomMaps._Number(at[0])
					var cy := CustomMaps._Number(at[1]) if at.size() > 1 else NAN
					if not is_finite(cx) or not is_finite(cy):
						errors.append({"line": c.line, "text": "spawn wants \"at <cx>,<cy>\""})
						continue
					for p in Symmetry.symmetryCells(sym, JS.roundi(cx), JS.roundi(cy), cols, rows):
						if map.spawns.size() >= MAX_SEATS:
							break
						map.spawns.append({"x": p[0] * TILE + TILE / 2.0, "y": p[1] * TILE + TILE / 2.0})

				"cluster":
					var word := _arg0(c).to_lower()
					var type = MapScript.RESOURCE_BY_WORD.get(word)
					if type == null:
						errors.append({"line": c.line, "text": "cluster wants a resource (tree, gold, berries), got \"%s\"" % _arg0(c)})
						continue
					if map.spawns.is_empty():
						warnings.append({"line": c.line, "text": "cluster … near start, but no spawns have been placed yet — put a spawns line above this one"})
						continue
					var n := countArg(c, 2, 200)
					var rr = MapScript.parseRange(c.named.get("radius"))
					if rr == null:
						rr = [12.0, 28.0]
					var nn := _named(c, "nodes")
					var nodes := maxi(1, JS.roundi(nn if not is_nan(nn) and nn != 0.0 else 5.0))
					for s in map.spawns:
						var scx := floori(s.x / TILE)
						var scy := floori(s.y / TILE)
						for i in n:
							var a := rng.range(0.0, PI * 2.0)
							var d := rng.range(rr[0], rr[1])
							var bx := JS.roundi(scx + JSMath.cos(a) * d)
							var by := JS.roundi(scy + JSMath.sin(a) * d)
							for k in nodes:
								var ox := rng.int_(-1, 1)
								var oy := rng.int_(-1, 1)
								addNode(bx + ox, by + oy, type)

				"scatter":
					var word := _arg0(c).to_lower()
					var type = MapScript.RESOURCE_BY_WORD.get(word)
					if type == null:
						errors.append({"line": c.line, "text": "scatter wants a resource (tree, gold, berries), got \"%s\"" % _arg0(c)})
						continue
					var n := countArg(c, 200, 4000)
					for i in n:
						var cx := rng.int_(0, cols - 1)
						var cy := rng.int_(0, rows - 1)
						for p in Symmetry.symmetryCells(sym, cx, cy, cols, rows):
							addNode(p[0], p[1], type)

				_:
					errors.append({
						"line": c.line,
						"text": "unknown command \"%s\" — try %s" % [c.word, ", ".join(C.SCRIPT_COMMANDS.map(func(x): return x.name))],
					})

		if errors.size():
			return {"map": null, "errors": errors, "warnings": warnings}

		# Clear the seats again at the end, order-independently. `addNode` already
		# refuses to crowd a seat, but only against the seats that existed when it
		# ran — a script that scatters before it seats its players would otherwise
		# slip through, and nothing in the format forces that order.
		if map.spawns.size():
			var before: int = map.resources.size()
			map.resources = map.resources.filter(func(r): return not tooCloseToSeat(floori(r.x / TILE), floori(r.y / TILE)))
			var cleared: int = before - map.resources.size()
			if cleared > 0:
				warnings.append({
					"line": cmds.size(),
					"text": "cleared %d resource%s sitting on top of a spawn — put the spawns line above the resource lines to place them deliberately" % [cleared, "" if cleared == 1 else "s"],
				})

		var nSpawns: int = map.spawns.size()
		map.minPlayers = mini(2, maxi(1, nSpawns if nSpawns else 1))
		map.maxPlayers = maxi(1, nSpawns if nSpawns else MAX_SEATS)
		if nSpawns == 0:
			# A map with no seats is a valid thing to script — a nomad map — but it is
			# much more often a forgotten `spawns` line, so say so.
			warnings.append({"line": cmds.size(), "text": "no spawn points were placed; the map will only work as a nomad map"})
			map.nomad = "forced"
			map.minPlayers = 1
			map.maxPlayers = MAX_SEATS
		map.desc = "Scripted, seed %d." % seed
		return {"map": map, "errors": errors, "warnings": warnings}
