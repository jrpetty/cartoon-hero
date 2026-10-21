## Shared helpers for the simulation tests: MapData from the TS fixtures, a
## world built the way the TS tests build one, and the parity snapshot.
class_name SimTestUtil

static var _maps = null


## MapData (the TS `MapData` keys) for a generated map recorded in
## tests/fixtures/maps.json by game/scripts/godot_parity.ts.
static func mapFor(preset: String, seed: int, players: int) -> Dictionary:
	if _maps == null:
		var f := FileAccess.open("res://tests/fixtures/maps.json", FileAccess.READ)
		_maps = JSON.parse_string(f.get_as_text())
	for entry in _maps:
		if entry.id == preset and int(entry.seed) == seed and int(entry.n) == players:
			return toMapData(entry.map)
	push_error("no fixture map %s/%d/%d" % [preset, seed, players])
	return {}


## The fixture's JSON map as MapData: terrain as a PackedByteArray, the extra
## `terrainHash` dropped, optional keys only when set.
static func toMapData(m: Dictionary) -> Dictionary:
	var out := {}
	for k in ["name", "worldW", "worldH", "cols", "rows"]:
		out[k] = m[k]
	out.terrain = PackedByteArray(m.terrain)
	var blocked: Array = []
	for c in m.blockedCells:
		blocked.append([int(c[0]), int(c[1])])
	out.blockedCells = blocked
	out.resources = (m.resources as Array).duplicate(true)
	out.starts = (m.starts as Array).duplicate(true)
	out.startResources = (m.startResources as Dictionary).duplicate(true)
	out.seed = m.seed
	out.biome = m.biome
	for k in ["custom", "modes", "nomad"]:
		if m.has(k) and m[k] != null:
			out[k] = m[k]
	return out


## `new World(seed); w.init(map, [{}, {}...], [1, 1...])` on the 2-player
## Open Plains fixture map (seed 7) — the stand-in for the TS tests'
## `generateMap("open_plains", seed)`.
static func makeWorld(seed: int = 7, players: int = 2, mode: String = "conquest") -> World:
	var map := mapFor("open_plains", 7, 2)
	var w := World.new(seed)
	var loadouts: Array = []
	var econ: Array = []
	for i in players:
		loadouts.append({})
		econ.append(1.0)
	var cmds: Array = []
	for i in players:
		cmds.append("")
	w.init(map, loadouts, econ, null, cmds, false, null, mode)
	return w


static func run(w: World, seconds: float) -> void:
	var ticks := int(ceil(seconds * w.SIM_HZ))
	for i in ticks:
		w.tick()


## The same shape godot_parity.ts records.
static func snapshot(w: World) -> Dictionary:
	var ents: Array = []
	for e: Entity in w.entities:
		if not e.alive:
			continue
		ents.append({
			"id": e.id, "type": e.type, "team": e.team, "kind": e.kind, "x": e.x, "y": e.y, "hp": e.hp,
			"order": e.order.kind, "target": e.order.target, "carry": e.carry, "q": e.productionQueue.size(),
		})
	var players: Array = []
	for t in w.numTeams:
		var p = w.player(t)
		players.append({"food": p.resources.food, "wood": p.resources.wood, "gold": p.resources.gold, "pop": p.popUsed, "age": p.age})
	return {"tick": w.tickCount, "entities": ents, "players": players}


## Field-by-field differences between two snapshots (first `limit` lines).
static func diffSnapshots(want: Dictionary, got: Dictionary, limit: int = 30) -> Array:
	var out: Array = []
	var byIdWant := {}
	for e in want.entities:
		byIdWant[int(e.id)] = e
	var byIdGot := {}
	for e in got.entities:
		byIdGot[int(e.id)] = e
	for id in byIdWant:
		if not byIdGot.has(id):
			out.append("entity %d (%s) missing in Godot" % [id, byIdWant[id].type])
			continue
		var a: Dictionary = byIdWant[id]
		var b: Dictionary = byIdGot[id]
		for k in a:
			var va = a[k]
			var vb = b[k]
			var same: bool
			if (va is float or va is int) and (vb is float or vb is int):
				same = absf(float(va) - float(vb)) <= 1e-6 * maxf(1.0, absf(float(va)))
			else:
				same = va == vb
			if not same:
				out.append("entity %d (%s) %s: TS %s, Godot %s" % [id, a.type, k, str(va), str(vb)])
	for id in byIdGot:
		if not byIdWant.has(id):
			out.append("entity %d (%s) extra in Godot" % [id, byIdGot[id].type])
	for t in want.players.size():
		for k in want.players[t]:
			if absf(float(want.players[t][k]) - float(got.players[t][k])) > 1e-6:
				out.append("player %d %s: TS %s, Godot %s" % [t, k, str(want.players[t][k]), str(got.players[t][k])])
	return out.slice(0, limit)
