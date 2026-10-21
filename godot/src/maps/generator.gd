## Seeded, symmetric skirmish map generation. Maps are 180°-rotationally
## symmetric so both players get identical starts: main base resources, a safe
## woodline, gold, berries, a natural expansion, and contested center gold.
##
## Port of game/src/maps/generator.ts. The presets are content: `C.PRESETS`.
##
## MapData (what generateMap returns, and what the world consumes) is a
## Dictionary with the TS keys:
##   name: String, worldW/worldH: float, cols/rows: int,
##   terrain: PackedByteArray (Terrain per cell, row-major),
##   blockedCells: Array of [cx, cy] int pairs (water/cliff cells, impassable),
##   resources: Array of ResourceSpawn {type: String ("tree" | "gold_mine" |
##     "berries"), x: float, y: float, amount: float},
##   starts: Array of {x: float, y: float},
##   startResources: {food, wood, gold} (floats),
##   seed: int, biome: String (BiomeId),
##   and, only where the TS sets them: custom: bool (map came from the editor),
##   modes: Array of GameMode strings (empty = any), nomad: bool (random starts).
##
## Every number the RNG touches follows JS semantics (see PORTING.md): floats
## where the TS has fractions, JS.round for Math.round, JSMath.sin/cos for the
## trigonometry (V8's, not the platform's), and insertion-ordered sets.
class_name MapGen
extends RefCounted

const Terrain = TerrainKinds.Terrain

# Richer nodes than before — more food/wood/gold banked in every patch.
const AMOUNT := {"tree": 160.0, "gold_mine": 1000.0, "berries": 250.0}


## The presets (MapPreset dictionaries: id, name, desc, size, forestry, water,
## and optionally chokes, mountains, hills, woodland, biome).
static func PRESETS() -> Array:
	return C.PRESETS


## `alliances` is an Array of alliance ids per team, or null.
static func generateMap(presetId: String, seed: int, players: int = 2, nomad: bool = false, alliances = null) -> Dictionary:
	return _Gen.new().run(presetId, seed, players, nomad, alliances)


## The generator's working state. The TS keeps it in closures over locals;
## here it is one object so the helpers (inB, mirror, nearStart, addWater,
## placeRes, cluster…) are plain methods with the same names.
class _Gen:
	var rng: RNG
	var cols: int
	var rows: int
	var terrain: PackedByteArray
	# Insertion-ordered like the JS Set it ports (blockedCells comes out in that
	# order, and a cell deleted and re-added moves to the end, as in JS).
	var blockedSet: Dictionary = {}
	var resources: Array = []
	# Only ever asked "has", so a flag per cell rather than a Set.
	var resCells: PackedByteArray
	var startCells: Array = []
	var mirrorRes := true
	var TILE := 32.0 # C.TILE, read in run()
	const startSafeR := 11 # cells kept clear of water/forest around each start

	func idx(cx: int, cy: int) -> int:
		return cy * cols + cx

	func inB(cx: int, cy: int) -> bool:
		return cx >= 1 and cy >= 1 and cx < cols - 1 and cy < rows - 1

	func nearStart(cx: int, cy: int, r: int = startSafeR) -> bool:
		var rr := r * r
		for s in startCells:
			var dx: int = cx - s[0]
			var dy: int = cy - s[1]
			if dx * dx + dy * dy < rr:
				return true
		return false

	# --- Water: lakes or a central river -------------------------------------
	func addWater(cx: int, cy: int) -> void:
		if not inB(cx, cy) or nearStart(cx, cy):
			return
		terrain[idx(cx, cy)] = Terrain.Water
		blockedSet[idx(cx, cy)] = true
		# 180° rotational mirror.
		var mx := cols - 1 - cx
		var my := rows - 1 - cy
		if not nearStart(mx, my):
			terrain[idx(mx, my)] = Terrain.Water
			blockedSet[idx(mx, my)] = true

	# --- Mountains ------------------------------------------------------------
	func addRock(cx: int, cy: int) -> void:
		if not inB(cx, cy) or nearStart(cx, cy):
			return
		var i := idx(cx, cy)
		if terrain[i] == Terrain.Water:
			return # a lake already owns this cell
		terrain[i] = Terrain.Rock
		blockedSet[i] = true
		resCells[i] = 1
		var mx := cols - 1 - cx
		var my := rows - 1 - cy
		if not nearStart(mx, my) and terrain[idx(mx, my)] != Terrain.Water:
			terrain[idx(mx, my)] = Terrain.Rock
			blockedSet[idx(mx, my)] = true
			resCells[idx(mx, my)] = 1

	# --- Land bridges ------------------------------------------------------------
	func carveLand(x0c: int, y0c: int, x1c: int, y1c: int, halfWidth: int) -> void:
		var steps := int(ceil(JS.hypot(x1c - x0c, y1c - y0c)))
		for s in range(0, steps + 1):
			var t := float(s) / steps
			var cx := JS.roundi(x0c + (x1c - x0c) * t)
			var cy := JS.roundi(y0c + (y1c - y0c) * t)
			for w in range(-halfWidth, halfWidth + 1):
				for h in range(-halfWidth, halfWidth + 1):
					var nx := cx + w
					var ny := cy + h
					if not inB(nx, ny):
						continue
					var i := idx(nx, ny)
					if terrain[i] == Terrain.Water:
						terrain[i] = Terrain.Sand # a ford/causeway across the water
						blockedSet.erase(i)
					resCells[i] = 1 # keep the lane clear of resource nodes

	# --- Resource helpers -----------------------------------------------------
	func placeRes(type: String, cx: int, cy: int, mirrored: bool) -> void:
		if not inB(cx, cy):
			return
		var i := idx(cx, cy)
		if blockedSet.has(i) or resCells[i] != 0 or terrain[i] == Terrain.Water:
			return
		resCells[i] = 1
		resources.append({"type": type, "x": cx * TILE + 4.0, "y": cy * TILE + 4.0, "amount": AMOUNT[type]})
		if mirrored:
			var mx := cols - 1 - cx
			var my := rows - 1 - cy
			var mi := idx(mx, my)
			if not blockedSet.has(mi) and resCells[mi] == 0 and terrain[mi] != Terrain.Water:
				resCells[mi] = 1
				resources.append({"type": type, "x": mx * TILE + 4.0, "y": my * TILE + 4.0, "amount": AMOUNT[type]})

	func cluster(type: String, cx: int, cy: int, count: int, spread: int) -> void:
		var placed := 0
		var guard := 0
		# while (placed < count && guard++ < count * 12)
		while placed < count:
			var g := guard
			guard += 1
			if not (g < count * 12):
				break
			var dx := rng.int_(-spread, spread)
			var dy := rng.int_(-spread, spread)
			var tx := cx + dx
			var ty := cy + dy
			var i := idx(tx, ty) if inB(tx, ty) else -1
			if i >= 0 and not blockedSet.has(i) and resCells[i] == 0 and terrain[i] != Terrain.Water:
				placeRes(type, tx, ty, mirrorRes)
				placed += 1

	func run(presetId: String, seed: int, players: int, nomad: bool, alliances) -> Dictionary:
		rng = RNG.new(seed)
		TILE = float(C.TILE)
		var presets: Array = C.PRESETS
		# "random" rolls a real preset from the seed (so the map is still replayable).
		var preset: Dictionary
		if presetId == "random":
			preset = presets[rng.int_(0, presets.size() - 1)]
		else:
			preset = presets[0]
			for p in presets:
				if p.id == presetId:
					preset = p
					break
		# Grow the field with the player count so 8 realms aren't cramped (keeps the
		# per-player area roughly constant beyond 4 players).
		var sizeScale := sqrt(float(players) / 4.0) if players > 4 else 1.0
		var W := JS.round((float(preset.size) * sizeScale) / TILE) * TILE
		var H := W
		cols = int(ceil(W / TILE))
		rows = int(ceil(H / TILE))
		terrain = PackedByteArray()
		terrain.resize(cols * rows)
		resCells = PackedByteArray()
		resCells.resize(cols * rows)

		# --- Base grass with patches of texture ---------------------------------
		terrain.fill(Terrain.Grass)
		var patches := floori(float(cols * rows) / 240.0)
		for i in patches:
			var cx := rng.int_(0, cols - 1)
			var cy := rng.int_(0, rows - 1)
			var r := rng.int_(2, 5)
			var t: int = Terrain.GrassDark if rng.bool_(0.6) else Terrain.Dirt
			for dy in range(-r, r + 1):
				for dx in range(-r, r + 1):
					if dx * dx + dy * dy <= r * r and inB(cx + dx, cy + dy) and rng.bool_(0.8):
						terrain[idx(cx + dx, cy + dy)] = t
						var mx := cols - 1 - (cx + dx)
						var my := rows - 1 - (cy + dy)
						terrain[idx(mx, my)] = t

		# --- Start positions ------------------------------------------------------
		var margin := floori(cols * 0.18)
		var primaries: Array
		# In nomad maps resources surround the actual (random) spawns, not mirrors.
		mirrorRes = true

		if nomad:
			# Spawn anywhere on the map — random points, well spaced, on inland ground.
			var nMargin := floori(cols * 0.14)
			var minSep := cols * (0.3 if players >= 4 else 0.42)
			var pts: Array = []
			var guard := 0
			while pts.size() < players:
				var g := guard
				guard += 1
				if not (g < 4000):
					break
				var cx := rng.int_(nMargin, cols - 1 - nMargin)
				var cy := rng.int_(nMargin, rows - 1 - nMargin)
				var ok := true
				for p in pts:
					if float((p[0] - cx) * (p[0] - cx) + (p[1] - cy) * (p[1] - cy)) <= minSep * minSep:
						ok = false
						break
				if ok:
					pts.append([cx, cy])
			while pts.size() < players:
				var px := rng.int_(nMargin, cols - 1 - nMargin)
				var py := rng.int_(nMargin, rows - 1 - nMargin)
				pts.append([px, py])
			startCells = pts
			primaries = pts # each spawn gets its own (un-mirrored) resource ring
			mirrorRes = false
		else:
			# Evenly space every start on a ring around the centre — inherently fair
			# (each equidistant from the middle and its neighbours). Teammates are
			# seated next to each other so an alliance holds one arc of the map.
			var ringR := mini(cols, rows) * 0.5 - margin
			var ccx := float(cols) / 2.0
			var ccy := float(rows) / 2.0
			var a0 := rng.range(0.0, PI * 2.0)
			# Slot k (clockwise) → a team, ordered so same-alliance teams are adjacent.
			var order: Array = []
			for t in players:
				order.append(t)
			JS.sort_by_cmp(order, func(a: int, b: int) -> float:
				var d := _ally(alliances, a) - _ally(alliances, b)
				return d if d != 0.0 else float(a - b))
			startCells = []
			startCells.resize(players)
			for k in players:
				var ang := a0 + (float(k) / players) * PI * 2.0
				var cx := JS.roundi(ccx + JSMath.cos(ang) * ringR)
				var cy := JS.roundi(ccy + JSMath.sin(ang) * ringR)
				startCells[order[k]] = [
					maxi(margin, mini(cols - 1 - margin, cx)),
					maxi(margin, mini(rows - 1 - margin, cy)),
				]
			# Every realm gets its own identical resource ring → a perfectly even start.
			primaries = startCells
			mirrorRes = false
		var startCellA: Array = startCells[0]
		var starts: Array = []
		for s in startCells:
			starts.append({"x": s[0] * TILE + TILE / 2.0, "y": s[1] * TILE + TILE / 2.0})

		# --- Water: lakes or a central river -------------------------------------
		var water := float(preset.water)
		if preset.id == "islands":
			# Many lakes break the land into holds; corridors (carved below) keep every
			# base reachable so the map is always winnable without ships.
			var lakeCount := 24
			for i in lakeCount:
				var cx := rng.int_(5, cols - 6)
				var cy := rng.int_(5, rows - 6)
				var r := rng.int_(3, 6)
				for dy in range(-r, r + 1):
					for dx in range(-r, r + 1):
						if dx * dx + dy * dy <= r * r:
							addWater(cx + dx, cy + dy)
		elif water > 0.4:
			# Winding river through the middle with 2 crossings.
			var c0 := floori(rows * 0.3) + rng.int_(-3, 3)
			var c1 := floori(rows * 0.62) + rng.int_(-3, 3)
			var crossings: Array = [c0, c1]
			var riverX := float(cols) / 2.0 + rng.range(-6.0, 6.0)
			for cy in rows:
				riverX += rng.range(-1.4, 1.4)
				riverX = maxf(cols * 0.3, minf(cols * 0.7, riverX))
				var isCrossing := false
				for c in crossings:
					if absi(cy - c) < 3:
						isCrossing = true
						break
				if isCrossing:
					# sandy ford
					for w in range(-2, 3):
						var cx := floori(riverX) + w
						if inB(cx, cy):
							terrain[idx(cx, cy)] = Terrain.Sand
					continue
				var width := 2 + floori(rng.range(0.0, 1.6))
				for w in range(-width, width + 1):
					addWater(floori(riverX) + w, cy)
		else:
			var lakes := floori(water * 14.0)
			for i in lakes:
				var cx := rng.int_(6, cols - 7)
				var cy := rng.int_(6, rows - 7)
				var r := rng.int_(2, 4)
				for dy in range(-r, r + 1):
					for dx in range(-r, r + 1):
						if dx * dx + dy * dy <= r * r:
							addWater(cx + dx, cy + dy)

		# --- Mountains: ridges you have to go around -----------------------------
		# Ridges are drawn as walks rather than blobs, because a blob is scenery and
		# a ridge is geography: it has a direction, a length, and two sides. They are
		# always mirrored, always kept off the starts, and always broken by at least
		# one pass, so a ridge shapes the approach without sealing anything off.
		var mountains := float(JS.opt(preset, "mountains", 0.0))
		if mountains > 0.0:
			var ridges := 1 + JS.roundi(mountains * 3.0)
			for ri in ridges:
				# Start somewhere off-centre and walk, drifting, for a good distance.
				var cx := rng.range(cols * 0.15, cols * 0.85)
				var cy := rng.range(rows * 0.15, rows * 0.85)
				var ang := rng.range(0.0, PI * 2.0)
				var len := JS.roundi(rng.range(cols * 0.22, cols * 0.45))
				# One pass per ridge, at a random point along it, so there is always a way
				# through for whoever is willing to fight for it.
				var passAt := rng.int_(floori(len * 0.25), floori(len * 0.75))
				var passWidth := 3
				for stepI in len:
					ang += rng.range(-0.22, 0.22)
					cx += JSMath.cos(ang)
					cy += JSMath.sin(ang)
					if absi(stepI - passAt) < passWidth:
						continue # the gap
					var half := 1 + (1 if rng.bool_(0.35) else 0)
					var rcx := JS.roundi(cx)
					var rcy := JS.roundi(cy)
					for dy in range(-half, half + 1):
						for dx in range(-half, half + 1):
							if dx * dx + dy * dy > half * half + 0.5:
								continue
							addRock(rcx + dx, rcy + dy)

		# --- Hills: high ground worth taking --------------------------------------
		# Passable, slower to climb, and worth 20% more range and sight to whoever
		# holds them — so they are the thing to fight over rather than around.
		var hills := float(JS.opt(preset, "hills", 0.0))
		if hills > 0.0:
			# Scaled by area, not a flat count. This used to be `hills * 10` regardless
			# of map size, which meant the largest maps got the same seven small rises
			# as the smallest — and measured out at 4% high ground even on Highlands,
			# the preset that asks for the most. At that density an army crossing the
			# map will usually not pass within reach of a single hill, so high ground
			# was something the map mentioned rather than something you fought over.
			var areaScale := float(cols * rows) / (100.0 * 100.0)
			var count := maxi(1, JS.roundi(hills * 18.0 * areaScale))
			for i in count:
				var hx := rng.int_(4, cols - 5)
				var hy := rng.int_(4, rows - 5)
				# Wide enough to hold a battle line. A radius-2 rise is smaller than the
				# army standing on it, so only part of the force ever got the bonus.
				var r := rng.int_(3, 6)
				for dy in range(-r, r + 1):
					for dx in range(-r, r + 1):
						var d2 := dx * dx + dy * dy
						if d2 > r * r:
							continue
						# A soft edge, so a hill reads as a slope rather than a stamped disc.
						if d2 > (r - 1) * (r - 1) and rng.bool_(0.45):
							continue
						for k in 2:
							var tx := hx + dx if k == 0 else cols - 1 - (hx + dx)
							var ty := hy + dy if k == 0 else rows - 1 - (hy + dy)
							if not inB(tx, ty) or nearStart(tx, ty, 7):
								continue
							var ti := idx(tx, ty)
							if blockedSet.has(ti):
								continue # never overwrite a barrier
							terrain[ti] = Terrain.Hill

		# --- Land bridges: guarantee every base connects to the centre -----------
		# On water-heavy maps lakes/rivers could otherwise wall a base off entirely
		# (we have no ships). Carving a passable lane from each start to the centre —
		# all lanes meeting there — keeps the whole map mutually reachable. Lane cells
		# are reserved so no tree/gold spawns later and re-blocks them.
		if preset.id == "islands" or water > 0.4:
			var midC := floori(cols / 2.0)
			var midR := floori(rows / 2.0)
			for s in startCells:
				carveLand(s[0], s[1], midC, midR, 1)

		# --- Per-start resources (mirrored on standard maps) ----------------------
		for p in primaries:
			var acx: int = p[0]
			var acy: int = p[1]
			# Main woodline: a fat arc of trees just outside the base.
			var woodAngle := rng.range(0.0, PI * 2.0)
			var wx := acx + JS.roundi(JSMath.cos(woodAngle) * 9.0)
			var wy := acy + JS.roundi(JSMath.sin(woodAngle) * 9.0)
			cluster("tree", wx, wy, 20, 3)
			# A second woodline on the far side so wood never runs dry early.
			cluster("tree", acx - JS.roundi(JSMath.cos(woodAngle) * 9.0), acy - JS.roundi(JSMath.sin(woodAngle) * 9.0), 12, 3)
			# Berries close to the TC — a generous patch so early food isn't starved.
			var berryAngle := woodAngle + rng.range(1.5, 2.5)
			cluster("berries", acx + JS.roundi(JSMath.cos(berryAngle) * 6.0), acy + JS.roundi(JSMath.sin(berryAngle) * 6.0), 11, 2)
			# Gold a bit farther out.
			var goldAngle := woodAngle - rng.range(1.5, 2.5)
			cluster("gold_mine", acx + JS.roundi(JSMath.cos(goldAngle) * 8.0), acy + JS.roundi(JSMath.sin(goldAngle) * 8.0), 6, 1)
			# Natural expansion: berries + gold toward the middle.
			var ecx := JS.roundi(acx + (cols / 2.0 - acx) * 0.45)
			var ecy := JS.roundi(acy + (rows / 2.0 - acy) * 0.45)
			cluster("gold_mine", ecx, ecy, 5, 2)
			var bx := ecx + rng.int_(-4, 4)
			var by := ecy + rng.int_(-4, 4)
			cluster("berries", bx, by, 8, 2)
			var tx := ecx + rng.int_(-6, 6)
			var ty := ecy + rng.int_(-6, 6)
			cluster("tree", tx, ty, 12, 3)

		# --- Contested center gold -------------------------------------------------
		cluster("gold_mine", floori(cols / 2.0), floori(rows / 2.0), 6, 3)

		# --- Scattered forest ------------------------------------------------------
		var forests := floori(float(preset.forestry) * 34.0)
		for i in forests:
			var cx := rng.int_(4, cols - 5)
			var cy := rng.int_(4, rows - 5)
			if nearStart(cx, cy, 13):
				continue
			var n := rng.int_(6, 14)
			var sp := rng.int_(2, 3)
			cluster("tree", cx, cy, n, sp)

		# --- Walkable woodland under and around the tree lines --------------------
		# Tree *nodes* are harvestable obstacles; woodland is the ground they stand
		# on. It is passable but slow and short-sighted, which is what makes a wood a
		# place you can push through when you must and would rather go around — and
		# what makes an ambush in one work. Painting it around the existing forest
		# means the map's woods look like woods instead of a scatter of stumps.
		var woodland := float(JS.opt(preset, "woodland", 0.0))
		if woodland > 0.0:
			var treeCells: Dictionary = {}
			for r in resources:
				if r.type != "tree":
					continue
				treeCells[idx(floori(r.x / TILE), floori(r.y / TILE))] = true
			var reach := 1 + JS.roundi(woodland * 2.0)
			for ti: int in treeCells:
				var tx := ti % cols
				var ty := ti / cols
				for dy in range(-reach, reach + 1):
					for dx in range(-reach, reach + 1):
						if dx * dx + dy * dy > reach * reach:
							continue
						var nx := tx + dx
						var ny := ty + dy
						if not inB(nx, ny) or nearStart(nx, ny, 9):
							continue
						var i := idx(nx, ny)
						if blockedSet.has(i):
							continue          # never soften a barrier
						if terrain[i] == Terrain.Hill:
							continue # nor bury high ground
						# Thin at the edges so a wood has a ragged border, not a stamped one.
						if dx * dx + dy * dy > (reach - 1) * (reach - 1) and rng.bool_(0.5):
							continue
						terrain[i] = Terrain.Forest

		# --- Extra resources strewn across the whole map (richer maps) ------------
		var extraGold := 4 + floori(float(cols * rows) / 2600.0)
		for i in extraGold:
			var cx := rng.int_(5, cols - 6)
			var cy := rng.int_(5, rows - 6)
			if nearStart(cx, cy, 9):
				continue
			var n := rng.int_(2, 4)
			cluster("gold_mine", cx, cy, n, 2)
		var extraBerries := 4 + floori(float(cols * rows) / 3000.0)
		for i in extraBerries:
			var cx := rng.int_(5, cols - 6)
			var cy := rng.int_(5, rows - 6)
			if nearStart(cx, cy, 9):
				continue
			var n := rng.int_(3, 6)
			cluster("berries", cx, cy, n, 2)

		# --- Chokepoints: forest barriers with a central lane ---------------------
		var chokes := float(JS.opt(preset, "chokes", 0.0))
		if chokes > 0.0:
			var acx: int = startCellA[0]
			var acy: int = startCellA[1]
			var midC := cols / 2.0
			var midR := rows / 2.0
			var dx := midC - acx
			var dy := midR - acy
			var dl := JS.hypot(dx, dy)
			if dl == 0.0 or is_nan(dl):
				dl = 1.0
			var ux := dx / dl
			var uy := dy / dl
			var px := -uy # perpendicular to the base→center axis
			var py := ux
			# Barrier sits two-thirds of the way out; mirror() gives the foe's twin.
			var bx := acx + ux * dl * 0.6
			var by := acy + uy * dl * 0.6
			var span := floori(cols * 0.2)
			var gap := 3 + floori(chokes * 2.0) # open lane width (cells)
			for s in range(-span, span + 1):
				if absi(s) <= gap:
					continue # leave the lane clear
				var cx := JS.roundi(bx + px * s)
				var cy := JS.roundi(by + py * s)
				if not inB(cx, cy) or nearStart(cx, cy, 11):
					continue
				cluster("tree", cx, cy, 2, 1)

		var blockedCells: Array = []
		for i: int in blockedSet:
			blockedCells.append([i % cols, i / cols])

		var startResources: Dictionary = C.START_RESOURCES.duplicate()
		return {
			"name": preset.name,
			"worldW": W,
			"worldH": H,
			"cols": cols,
			"rows": rows,
			"terrain": terrain,
			"blockedCells": blockedCells,
			"resources": resources,
			"starts": starts,
			"startResources": startResources,
			"seed": seed,
			"biome": JS.opt(preset, "biome", "temperate"),
			"nomad": nomad,
		}

	# alliances?.[i] ?? i
	static func _ally(alliances, i: int) -> float:
		if alliances == null or i >= alliances.size() or alliances[i] == null:
			return float(i)
		return float(alliances[i])
