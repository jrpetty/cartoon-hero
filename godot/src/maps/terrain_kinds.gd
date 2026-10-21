## The ground itself, and what it does to whoever stands on it.
##
## Port of game/src/maps/terrain_kinds.ts. The data (TERRAIN_DEFS, BIOMES,
## TERRAIN_COUNT) is content and comes from `C`; this file holds the enum and
## the flat lookup tables built from it.
##
## Before this there were five cosmetic ground types and one rule: water is a
## wall. Everything else was decoration. A map was therefore an open field with
## some scenery, and there was nothing to manoeuvre around — no reason to take
## the long way, no ground worth holding, no line you could not simply walk
## through.
##
## Now the ground has four jobs:
##   • **Mountains** and **lakes** are hard barriers. They shape where armies can
##     go at all, which is what makes a map have geography rather than texture.
##   • **Forests** can be pushed through, slowly. They are the *soft* barrier:
##     passable, so nothing is ever truly walled off, but slow enough that going
##     around is usually right — and slow enough that being caught in one is a
##     real cost.
##   • **Hills** are passable and worth holding: standing on one sees further and
##     shoots further. High ground is the thing worth fighting over.
##   • **Marsh** and **shallows** are pure friction — slow ground with nothing to
##     recommend it, so crossing at the wrong place is punished.
class_name TerrainKinds
extends RefCounted

enum Terrain {
	Grass = 0,
	GrassDark = 1,
	Dirt = 2,
	Water = 3,   # lake / river — impassable
	Sand = 4,
	Forest = 5,  # passable, slow — the soft barrier
	Rock = 6,    # mountain — impassable
	Hill = 7,    # passable, high ground: sees and shoots further
	Marsh = 8,   # passable, slow
	Snow = 9,
	Shallow = 10, # fordable water: slow but crossable
}

# Flat lookup tables, built once from C.TERRAIN_DEFS on first use — these are
# read on the movement hot path (cache the table in a local in a tight loop).
# Same types as the TS: Uint8Array → PackedByteArray, Float32Array →
# PackedFloat32Array (so speeds read back float32-rounded, exactly as in JS).
static var _blocks := PackedByteArray()
static var _speed := PackedFloat32Array()
static var _sight := PackedFloat32Array()
static var _buildable := PackedByteArray()

static var TERRAIN_BLOCKS: PackedByteArray:
	get:
		if _blocks.is_empty():
			_build()
		return _blocks
static var TERRAIN_SPEED: PackedFloat32Array:
	get:
		if _speed.is_empty():
			_build()
		return _speed
static var TERRAIN_SIGHT: PackedFloat32Array:
	get:
		if _sight.is_empty():
			_build()
		return _sight
static var TERRAIN_BUILDABLE: PackedByteArray:
	get:
		if _buildable.is_empty():
			_build()
		return _buildable


static func _build() -> void:
	var n := int(C.TERRAIN_COUNT)
	var blocks := PackedByteArray()
	blocks.resize(n)
	var speed := PackedFloat32Array()
	speed.resize(n)
	speed.fill(1.0)
	var sight := PackedFloat32Array()
	sight.resize(n)
	sight.fill(1.0)
	var buildable := PackedByteArray()
	buildable.resize(n)
	for d in C.TERRAIN_DEFS:
		var id := int(d.id)
		blocks[id] = 1 if d.blocks else 0
		speed[id] = 0.0 if d.blocks else float(d.speed)
		sight[id] = float(d.sight)
		buildable[id] = 1 if d.buildable else 0
	_blocks = blocks
	_speed = speed
	_sight = sight
	_buildable = buildable


## The definition for a terrain id (a Dictionary with the TS TerrainDef keys).
static func terrainDef(t: int) -> Dictionary:
	var defs: Array = C.TERRAIN_DEFS
	if t >= 0 and t < defs.size():
		return defs[t]
	return defs[0]


# ---------------------------------------------------------------- biomes --

## BiomeId: "temperate" | "arid" | "alpine" | "wetland" (plain strings).
static func biomeById(id: String) -> Dictionary:
	for b in C.BIOMES:
		if b.id == id:
			return b
	return C.BIOMES[0]
