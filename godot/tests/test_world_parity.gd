extends TestCase
# The simulation matches the TypeScript build across the rest of world.ts:
# combat and projectiles, formations and flow fields, abilities, farms and
# re-seeding, ages and Oaths, techs, the market, factions/commanders/boons,
# banners, survival/regicide/koth, bridges, gates, trade, a nomad start.
#
# Fixtures come from game/scripts/godot_world_parity.ts, which ran each
# scenario's script against the TS world and recorded its decisions as
# concrete ops. Here they are replayed and, after every tick, compared on
# worldChecksum and on `xhash` — a hash over every entity field, each realm's
# full ledger, the fog and the events drained that tick. On a divergence the
# next full snapshot is diffed field by field.

const SCENARIOS := ["battle", "economy", "alliance", "survival", "regicide", "koth", "misc", "nomad"]


# ---- the harness ops (mirrors applyOp in the generator) ----------------

func applyOp(w: World, op: Dictionary) -> void:
	match op.t:
		"_spawn":
			w.spawnUnit(int(op.team), op.type, op.x, op.y)
		"_building":
			w.spawnBuilding(int(op.team), op.type, op.x, op.y, op.done)
		"_res":
			w.player(int(op.team)).resources = {"food": float(op.food), "wood": float(op.wood), "gold": float(op.gold)}
		"_set":
			var e = w.byId.get(int(op.id))
			if e != null:
				_setField(e, op.field, op.value)
		"_player":
			_setField(w.player(int(op.team)), op.field, op.value)
		"_world":
			_setField(w, op.field, op.value)
		"_terrain":
			for c in op.cells:
				var cx := int(c[0])
				var cy := int(c[1])
				var v := int(c[2])
				w.terrain[cy * w.terrainCols + cx] = v
				w.grid.setBlocked(cx, cy, int(C.TERRAIN_BLOCKS[str(v)]) == 1)
		"_damage":
			var e = w.byId.get(int(op.target))
			if e != null:
				w.dealDamage(int(op.team), e, op.amount, op.source)
		_:
			Commands.applyCommand(w, op)


func _setField(obj: Object, field: String, value) -> void:
	var cur = obj.get(field)
	obj.set(field, int(value) if cur is int else float(value))


# ---- the extended hash (mirrors class H / xhash in the generator) -------

var h := 0


func _mix(v: int) -> void:
	h = (((h ^ v) & 0xFFFFFFFF) * 16777619) & 0xFFFFFFFF


## h.i: `v | 0`
func hi(v) -> void:
	_mix(Commands._toInt32(v))


## h.n: Math.round(v * 1000) | 0
func hn(v: float) -> void:
	_mix(Commands._toInt32(JS.round(v * 1000.0)))


func hb(v: bool) -> void:
	_mix(1 if v else 0)


func hs(v: String) -> void:
	_mix(v.length())
	for k in v.length():
		_mix(v.unicode_at(k))


func hv(v) -> void:
	if v == null:
		_mix(-7 & 0xFFFFFFFF)
	elif v is bool:
		hb(v)
	elif v is int or v is float:
		hn(v)
	elif v is String or v is StringName:
		hs(str(v))
	elif v is Array:
		_mix(v.size())
		for x in v:
			hv(x)
	elif v is Dictionary:
		_mix(v.size())
		for k in v:
			hs(str(k))
			hv(v[k])


func _fogHash(f: PackedByteArray) -> int:
	var fh := 2166136261
	for i in f.size():
		fh = (((fh ^ f[i]) & 0xFFFFFFFF) * 16777619) & 0xFFFFFFFF
	return fh


func playerRecord(w: World, t: int) -> Dictionary:
	var p = w.player(t)
	return {
		"food": p.resources.food, "wood": p.resources.wood, "gold": p.resources.gold, "popUsed": p.popUsed, "popCap": p.popCap, "age": p.age,
		"upgrades": p.upgrades.keys(), "oaths": p.oaths.duplicate(), "pendingOath": p.pendingOath, "heroState": p.heroState,
		"heroRespawnTimer": p.heroRespawnTimer, "heroLevel": p.heroLevel, "powerCooldown": p.powerCooldown,
		"marketWood": p.marketPressure.wood, "marketFood": p.marketPressure.food, "defeated": p.defeated, "defeatedAt": p.defeatedAt,
		"ageTimes": p.ageTimes.duplicate(), "autoReseed": p.autoReseed, "stats": p.stats,
	}


func worldRecord(w: World) -> Dictionary:
	var banners: Array = []
	for b in w.banners:
		banners.append([b.team, b.x, b.y, b.expires])
	return {
		"tick": w.tickCount, "time": w.time, "winner": w.winner if w.winner != null else -1, "count": w.entities.size(),
		"kothHold": w.kothHold.duplicate(), "survivalWave": w.survivalWave, "survivalTimer": w.survivalTimer,
		"survivalWon": w.survivalWon, "banners": banners,
	}


func xhash(w: World, events: Array) -> int:
	h = 2166136261
	var wr := worldRecord(w)
	hi(wr.tick); hn(wr.time); hi(wr.winner); hi(wr.count)
	hv(wr.kothHold); hi(wr.survivalWave); hn(wr.survivalTimer); hb(wr.survivalWon); hv(wr.banners)
	for t in w.numTeams:
		var pr := playerRecord(w, t)
		for k in pr:
			hs(k)
			hv(pr[k])
		if w.tickCount % 5 == 0:
			hi(_fogHash(w.fog[t]))
	for e: Entity in w.entities:
		hi(e.id)
		hb(e.alive)
		if e.kind == T.Kind.Resource:
			hn(e.amount)
			continue
		hs(e.type); hi(e.team); hi(e.kind)
		hn(e.x); hn(e.y); hn(e.hp); hn(e.maxHp); hn(e.vx); hn(e.vy); hn(e.speed); hn(e.attack); hn(e.range)
		hn(e.armor); hn(e.pierceArmor); hn(e.visionRange); hn(e.attackInterval); hn(e.radius)
		hi(e.order.kind); hi(e.order.target); hn(e.order.tx); hn(e.order.ty)
		_mix(e.order.queue.size())
		for q: Order in e.order.queue:
			_mix(4); hn(q.kind); hn(q.target); hn(q.tx); hn(q.ty)
		hi(e.stance); hi(e.path.size() if e.path != null else -1); hi(e.pathIndex); hn(e.repathCooldown)
		hn(e.carry); hv(e.carryKind); hn(e.gatherCooldown); hn(e.amount)
		hi(e.buildState); hn(e.buildProgress); hn(e.popProvided); hn(e.rallyX); hn(e.rallyY)
		hv(e.productionQueue); hn(e.productionTime); hv(e.garrison)
		hb(e.gateOpen); hi(e.gateForce); hi(e.farmWorker)
		hi(e.projTargetId); hi(e.projSourceId); hn(e.projDamage); hn(e.projElapsed); hn(e.projDuration)
		hn(e.attackCooldown); hn(e.abilityCooldown); hn(e.abilityActive)
		hn(e.slowTimer); hn(e.rallyTimer); hn(e.guardTimer); hi(e.tradeHomeId)
		hi(e.spottedBy); hi(e.heroLevel); hi(e.heroKills); hi(e.veterancy); hi(e.vetKills)
		hn(e.chargeRun); hn(e.lastDamageTime); hi(e.lastAttackerId); hi(e.variantRarity)
		hn(e.facing); hn(e.animPhase); hn(e.hitFlash)
	for ev in events:
		hs(ev.kind); hn(ev.x); hn(ev.y); hi(ev.team); hv(ev.data)
	return h


# ---- full snapshots, for saying what differs ----------------------------

func entityRecord(e: Entity) -> Dictionary:
	if e.kind == T.Kind.Resource:
		return {"id": e.id, "alive": e.alive, "amount": e.amount}
	var q: Array = []
	for o: Order in e.order.queue:
		q.append([o.kind, o.target, o.tx, o.ty])
	return {
		"id": e.id, "alive": e.alive, "type": e.type, "team": e.team, "kind": e.kind,
		"x": e.x, "y": e.y, "hp": e.hp, "maxHp": e.maxHp, "vx": e.vx, "vy": e.vy, "speed": e.speed, "attack": e.attack, "range": e.range,
		"armor": e.armor, "pierceArmor": e.pierceArmor, "visionRange": e.visionRange, "attackInterval": e.attackInterval, "radius": e.radius,
		"order": e.order.kind, "target": e.order.target, "tx": e.order.tx, "ty": e.order.ty, "queue": q,
		"stance": e.stance, "path": e.path.size() if e.path != null else -1, "pathIndex": e.pathIndex, "repathCooldown": e.repathCooldown,
		"carry": e.carry, "carryKind": e.carryKind, "gatherCooldown": e.gatherCooldown, "amount": e.amount,
		"buildState": e.buildState, "buildProgress": e.buildProgress, "popProvided": e.popProvided, "rallyX": e.rallyX, "rallyY": e.rallyY,
		"productionQueue": e.productionQueue.duplicate(), "productionTime": e.productionTime, "garrison": e.garrison.duplicate(),
		"gateOpen": e.gateOpen, "gateForce": e.gateForce, "farmWorker": e.farmWorker,
		"projTargetId": e.projTargetId, "projSourceId": e.projSourceId, "projDamage": e.projDamage, "projElapsed": e.projElapsed, "projDuration": e.projDuration,
		"attackCooldown": e.attackCooldown, "abilityCooldown": e.abilityCooldown, "abilityActive": e.abilityActive,
		"slowTimer": e.slowTimer, "rallyTimer": e.rallyTimer, "guardTimer": e.guardTimer, "tradeHomeId": e.tradeHomeId,
		"spottedBy": e.spottedBy, "heroLevel": e.heroLevel, "heroKills": e.heroKills, "veterancy": e.veterancy, "vetKills": e.vetKills,
		"chargeRun": e.chargeRun, "lastDamageTime": e.lastDamageTime, "lastAttackerId": e.lastAttackerId, "variantRarity": e.variantRarity,
		"facing": e.facing, "animPhase": e.animPhase, "hitFlash": e.hitFlash,
	}


func snapshot(w: World, events: Array) -> Dictionary:
	var players: Array = []
	for t in w.numTeams:
		var pr := playerRecord(w, t)
		pr["fog"] = _fogHash(w.fog[t])
		players.append(pr)
	var ents: Array = []
	for e: Entity in w.entities:
		ents.append(entityRecord(e))
	var evs: Array = []
	for ev in events:
		evs.append([ev.kind, ev.x, ev.y, ev.team, ev.data])
	return {"world": worldRecord(w), "players": players, "entities": ents, "events": evs}


func _same(a, b) -> bool:
	if (a is float or a is int) and (b is float or b is int):
		return absf(float(a) - float(b)) <= 1e-9 * maxf(1.0, absf(float(a)))
	if a is Array and b is Array:
		if a.size() != b.size():
			return false
		for i in a.size():
			if not _same(a[i], b[i]):
				return false
		return true
	if a is Dictionary and b is Dictionary:
		if a.size() != b.size():
			return false
		for k in a:
			if not b.has(k) or not _same(a[k], b[k]):
				return false
		return true
	return a == b


func diffSnapshot(want: Dictionary, got: Dictionary, limit := 25) -> Array:
	var out: Array = []
	for k in want.world:
		if not _same(want.world[k], got.world[k]):
			out.append("world.%s: TS %s, Godot %s" % [k, str(want.world[k]), str(got.world[k])])
	for t in want.players.size():
		for k in want.players[t]:
			if not _same(want.players[t][k], got.players[t].get(k)):
				out.append("player %d %s: TS %s, Godot %s" % [t, k, str(want.players[t][k]).left(300), str(got.players[t].get(k)).left(300)])
	if want.entities.size() != got.entities.size():
		out.append("entity count: TS %d, Godot %d" % [want.entities.size(), got.entities.size()])
	for i in mini(want.entities.size(), got.entities.size()):
		var a: Dictionary = want.entities[i]
		var b: Dictionary = got.entities[i]
		for k in a:
			if not _same(a[k], b.get(k)):
				out.append("entity %d (%s) %s: TS %s, Godot %s" % [int(a.id), str(b.get("type", "resource")), k, str(a[k]), str(b.get(k))])
		if out.size() >= limit:
			break
	if not _same(want.events, got.events):
		out.append("events: TS %s\n      Godot %s" % [str(want.events).left(600), str(got.events).left(600)])
	return out.slice(0, limit)


# ---- the run -----------------------------------------------------------

func runScenario(name: String) -> Dictionary:
	var fx = fixture("world_%s.json" % name)
	if fx == null:
		return {}
	var players := int(fx.players)
	var map := SimTestUtil.mapFor(fx.preset, int(fx.seed), players)
	var w := World.new(int(fx.seed))
	var loadouts: Array = []
	var econ: Array = []
	for i in players:
		loadouts.append({})
		econ.append(1.0)
	var i0: Dictionary = fx.get("init", {})
	w.init(map, loadouts, econ, i0.get("alliances"), i0.get("commanders"), i0.get("nomad", false), i0.get("boons"),
		i0.get("mode", "conquest"), i0.get("factions"))
	var sums: Array = fx.checksums
	var xh: Array = fx.xhash
	var snapEvery := int(fx.snapEvery)
	var result := {"matched": -1, "ticks": sums.size() - 1, "ms": 0.0}
	if Commands.worldChecksum(w) != int(sums[0]) or xhash(w, []) != int(xh[0]):
		failures.append("%s: differs after init" % name)
		for line in diffSnapshot(fx.snapshots[0], snapshot(w, [])):
			failures.append("    " + line)
		return result
	result.matched = 0
	var ops: Array = fx.ops
	var oi := 0
	var tickUs := 0
	var ticks := sums.size() - 1
	for i in ticks:
		while oi < ops.size() and int(ops[oi][0]) == i:
			applyOp(w, ops[oi][1])
			oi += 1
		var t0 := Time.get_ticks_usec()
		w.tick()
		tickUs += Time.get_ticks_usec() - t0
		var ev := w.drainEvents()
		var cs := Commands.worldChecksum(w)
		var xs := xhash(w, ev)
		if cs != int(sums[i + 1]) or xs != int(xh[i + 1]):
			failures.append("%s: diverged at tick %d (%s)" % [name, i + 1, "checksum" if cs != int(sums[i + 1]) else "extended state"])
			# Run on to the next full snapshot and say what differs there.
			var next := ((i + 1 + snapEvery - 1) / snapEvery) * snapEvery
			var si := next / snapEvery
			if si < fx.snapshots.size():
				var j := i + 1
				while j < next:
					while oi < ops.size() and int(ops[oi][0]) == j:
						applyOp(w, ops[oi][1])
						oi += 1
					w.tick()
					ev = w.drainEvents()
					j += 1
				failures.append("    differences at snapshot tick %d:" % next)
				for line in diffSnapshot(fx.snapshots[si], snapshot(w, ev)):
					failures.append("    " + line)
			break
		result.matched = i + 1
	result.ms = tickUs / 1000.0
	print("    %s: matched %d/%d ticks, tick() %.3f ms/tick (%d entities)" % [
		name, result.matched, ticks, result.ms / maxi(1, result.matched), w.entities.size()])
	return result


func _check(name: String) -> void:
	var r := runScenario(name)
	eq(r.get("matched", -1), r.get("ticks", -2), "%s: ticks matched" % name)


func test_battle() -> void:
	_check("battle")


func test_economy() -> void:
	_check("economy")


func test_alliance() -> void:
	_check("alliance")


func test_survival() -> void:
	_check("survival")


func test_regicide() -> void:
	_check("regicide")


func test_koth() -> void:
	_check("koth")


func test_misc() -> void:
	_check("misc")


func test_nomad() -> void:
	_check("nomad")
