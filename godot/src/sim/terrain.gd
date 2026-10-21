## sim/terrain.ts — Warband Tactics board terrain: the arena stops being a flat
## 10×10.
##
## Every round rolls its own small set of features from its own seed: a boulder
## that takes a cell out of the board entirely, a rise that lets whoever holds
## it shoot further, a mire that bogs down whatever stands in it. Features are
## **mirrored across the centre line**, so both warbands face exactly the same
## ground, and the seed is shown in the UI so a board can be found again.
##
## TerrainDef and TerrainFeature are Dictionaries with the TS keys
## ({kind, name, desc, color, blocks?, rangePct?, speedPct?} and {kind, col, row}).
## The TERRAIN table is not part of the exported content data (the exporter
## covers content modules), so it is transcribed here verbatim from the TS.
class_name Terrain

const TERRAIN := {
	"boulder": {
		"kind": "boulder", "name": "Boulder", "color": "#8a8478",
		"desc": "Solid rock. Nothing can stand here — it simply takes the cell off the board.",
		"blocks": true,
	},
	"rise": {
		"kind": "rise", "name": "Rise", "color": "#9ac26a",
		"desc": "High ground. Whoever holds it shoots 20% further.",
		"rangePct": 20,
	},
	"mire": {
		"kind": "mire", "name": "Mire", "color": "#6a7a4a",
		"desc": "Boggy ground. Anything standing in it moves 30% slower.",
		"speedPct": -30,
	},
}


## The player owns cols 0-4; a feature there is mirrored to 9-col.
static func MIRROR_COL(col: int) -> int:
	return 9 - col


## Roll a round's terrain. Features are generated on the left half and mirrored,
## so the two halves are identical ground. Round 1 is deliberately clear — the
## opener shouldn't also be a puzzle.
static func rollTerrain(seed: int, round: int) -> Array:
	if round <= 1:
		return []
	var rng := RNG.new(seed)
	var count := rng.int_(2, 4) # per half, so 4-8 on the board
	var kinds := ["boulder", "rise", "mire"]
	var used := {}
	var out: Array = []
	for i in count:
		# Keep the front rank (col 4) clear so a wall always has somewhere to stand.
		var col := rng.int_(0, 3)
		var row := rng.int_(0, 9)
		var key := "%d,%d" % [col, row]
		if used.has(key):
			continue
		used[key] = true
		var kind: String = kinds[rng.int_(0, kinds.size() - 1)]
		out.append({"kind": kind, "col": col, "row": row})
		out.append({"kind": kind, "col": MIRROR_COL(col), "row": row})
	return out


## The feature on a cell, if any (a TERRAIN def or null).
static func terrainAt(features: Array, col: int, row: int):
	for f in features:
		if int(f.col) == col and int(f.row) == row:
			return TERRAIN[f.kind]
	return null


## True when a cell can't hold a unit.
static func isBlocked(features: Array, col: int, row: int) -> bool:
	var t = terrainAt(features, col, row)
	return t != null and t.get("blocks", false) == true


## A short, human-readable code for a round's terrain seed, so a board can be
## noted down and found again.
static func terrainCode(seed: int) -> String:
	return Replay.toBase36(JS.u32(seed)).to_upper().right(6).lpad(6, "0")


## Parse a code back into a seed (returns null if it isn't one).
static func parseTerrainCode(code: String):
	var clean := code.strip_edges().to_upper()
	if not RegEx.create_from_string("^[0-9A-Z]{1,6}$").search(clean):
		return null
	var n := 0
	for ch in clean:
		n = n * 36 + "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".find(ch)
	return n
