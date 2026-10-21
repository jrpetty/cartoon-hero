extends TestCase
# Warband Tactics matches the TypeScript build run for run.
#
# Fixtures come from game/scripts/godot_warband_parity.ts, which played whole
# runs from seeds against the TS engine with a scripted player — shop, bench
# and board decisions, augment and carousel picks, commanders and lobby
# difficulties, a typed-in ground seed, creep rounds and the boss — and
# recorded its decisions as concrete ops. Here they are replayed: after every
# op the return value and a hash of the run's whole state are compared; at
# every round boundary a full snapshot, including every derived read the
# screen makes; and every battle is compared on its verdict and on the
# worldChecksum of the world it was fought in (live battles every 20 ticks).


# ---- the hash (mirrors class H in the generator) ------------------------------

var hh := 0


func _mix(n: int) -> void:
	hh = JS.u32(hh ^ JS.i32(n))
	hh = JS.u32(JS.imul(hh, 16777619))


func _hv(x) -> void:
	if x == null:
		_mix(-7)
	elif x is bool:
		_mix(1 if x else 0)
	elif x is int or x is float:
		_mix(int(JS.round(float(x) * 1000.0)))
	elif x is String or x is StringName:
		var s := String(x)
		_mix(s.length())
		for k in s.length():
			_mix(s.unicode_at(k))
	elif x is Array:
		_mix(x.size())
		for y in x:
			_hv(y)
	elif x is Dictionary:
		_mix(x.size())
		for k in x:
			_hv(x[k])
	else:
		push_error("unhashable %s" % str(x))


func hash_of(x) -> int:
	hh = 2166136261
	_hv(x)
	return hh


# ---- state records (mirror core() / derived() in the generator) -------------

static func _nz(v, fb):
	return fb if v == null else v


static func pieceRec(p) -> Array:
	return [p.type, p.star, p.items.duplicate(), _nz(p.col, -1), _nz(p.row, -1), 1 if p.deployed else 0]


static func arenaRec(u: Dictionary) -> Array:
	var items = u.get("items")
	return [u.type, _nz(u.get("star"), 1), items.duplicate() if items != null else [], _nz(u.get("col"), -1), _nz(u.get("row"), -1)]


static func buffRec(b) -> Array:
	var out: Array = []
	if b != null:
		for k in b:
			out.append([k, b[k]])
	return out


static func _ids(arr: Array) -> Array:
	return arr.map(func(a): return a.id)


func core(run: Warband.WarbandRun) -> Dictionary:
	var lr = run.lastResult
	return {
		"round": run.round, "phase": run.phase, "gold": run.gold, "level": run.level, "xp": run.xp, "streak": run.streak, "life": run.life,
		"outcome": _nz(run.outcome, ""),
		"pieces": run.pieces.map(pieceRec),
		"itemStash": run.itemStash.duplicate(),
		"shop": run.shop.map(func(s): return _nz(s, "")),
		"opponents": run.opponents.map(func(o): return [o.life, 1 if o.alive else 0]),
		"augments": _ids(run.augments),
		"augmentOffer": _ids(run.augmentOffer),
		"commander": run.commander.id if run.commander != null else "",
		"commanderOffer": _ids(run.commanderOffer),
		"freeRerolls": run.freeRerolls,
		"pendingCamp": run.pendingCamp.name if run.pendingCamp != null else "",
		"condition": run.condition.id,
		"terrain": run.terrain.map(func(f): return [f.kind, f.col, f.row]),
		"terrainSeed": run.terrainSeed,
		"shopLocked": 1 if run.shopLocked else 0,
		"carousel": run.carousel.map(func(c): return [c.type, c.star, c.item]),
		"draftAhead": run.draftAhead,
		"history": run.history.map(func(h): return [h.round, h.stage, h.foe, 1 if h.won else 0, 1 if h.creep else 0, h.dmg, h.youLeft, h.foeLeft, h.life]),
		"tally": run.tally.values(),
		"lastResult": [1 if lr.won else 0, lr.foe, lr.youLeft, lr.foeLeft, lr.dmg, lr.relics, lr.gold, 1 if lr.creep else 0] if lr != null else [],
		"lastMerge": [run.lastMerge.type, run.lastMerge.star] if run.lastMerge != null else [],
		"lastFusion": [run.lastFusion.item, run.lastFusion.type] if run.lastFusion != null else [],
		"difficulty": run.difficulty.id,
		"boss": [1 if run.bossPending else 0, 1 if run.bossRound else 0, 1 if run.bossCleared else 0],
		"pendingSeed": run.pendingSeed,
		"pendingOpp": run.pendingOpp.map(arenaRec),
		"pendingFoe": run.pendingFoe.id if run.pendingFoe != null else -1,
		"foes": run.foeBrains.map(func(b): return [b.gold, b.level, b.xp, b.pieces.map(func(p): return [p.type, p.star, p.items.duplicate()]), JS.u32(b.rng.state), b.favored.duplicate(), b.play]),
		"pool": run.pool.keys().map(func(k): return [k, run.pool[k]]),
		"rng": JS.u32(run.rng.state),
	}


func derived(run: Warband.WarbandRun) -> Dictionary:
	var side := run.sideOpts()
	var field := run.fieldOpts()
	var xp := run.xpProgress()
	var out := {}
	out["deployCount"] = run.deployCount()
	out["rerollCost"] = run.rerollCost()
	out["freeRerollsLeft"] = run.freeRerollsLeft()
	out["shopOdds"] = run.shopOdds().duplicate()
	out["xpProgress"] = [xp.xp, xp.need, 1 if xp.max else 0]
	out["stageLabel"] = run.stageLabel()
	out["terrainCode"] = run.terrainCode()
	out["benchCount"] = run.benchCount()
	out["benchFull"] = 1 if run.benchFull() else 0
	out["deployedCount"] = run.deployedCount()
	out["worstCase"] = run.worstCaseDamage()
	out["streakGold"] = run.streakGold()
	out["pendingFoeName"] = run.pendingFoeName()
	out["isCreepRound"] = 1 if run.isCreepRound() else 0
	out["isDraftRound"] = 1 if run.isDraftRound() else 0
	out["placement"] = run.placement()
	var cb: Array = []
	for i in run.shop.size():
		var c := run.canBuy(i)
		cb.append([1 if c.ok else 0, c.get("reason", "")])
	out["canBuy"] = cb
	out["poolCounts"] = run.shop.map(func(t): return run.poolCount(t) if t != null else -1)
	out["sideBuff"] = buffRec(side.buff)
	out["sideTraitBonus"] = buffRec(side.traitBonus)
	out["fieldBuff"] = buffRec(field.buff)
	var ftb: Array = []
	if field.traitBuffs != null:
		for k in field.traitBuffs:
			ftb.append([k, buffRec(field.traitBuffs[k])])
	out["fieldTraitBuffs"] = ftb
	out["boardUnits"] = run.boardUnits().map(arenaRec)
	out["deployment"] = run.deployment().map(func(d): return [d.index, d.type, d.star, d.col, d.row])
	var w = run.weakestDeployed()
	out["weakest"] = pieceRec(w) if w != null else []
	out["boardStacks"] = run.boardStacks().map(func(s): return [s.type, s.count, _nz(s.get("star"), 1)])
	out["activeTraits"] = run.activeTraits().map(func(a): return [a.trait.id, a.count, a.tierIndex])
	out["traitProgress"] = run.traitProgress().map(func(t): return [t.trait.id, t.count, t.tier.label if t.tier != null else "", _nz(t.next, -1)])
	out["standings"] = run.standings().map(func(s): return [s.name, s.life, 1 if s.alive else 0, 1 if s.you else 0, s.id])
	out["scout"] = run.opponents.map(func(o):
		var s = run.scout(o.id)
		return [s.name, s.level, s.life, 1 if s.alive else 0, s.board.map(arenaRec), s.traits.map(func(a): return [a.trait.id, a.count, a.tierIndex])])
	return out


# ---- comparison ---------------------------------------------------------------

static func same(a, b) -> bool:
	if (a is int or a is float) and (b is int or b is float):
		return float(a) == float(b)
	if a is bool or b is bool:
		return a == b
	if a == null or b == null:
		return a == null and b == null
	if (a is String or a is StringName) and (b is String or b is StringName):
		return String(a) == String(b)
	if a is Array and b is Array:
		if a.size() != b.size():
			return false
		for i in a.size():
			if not same(a[i], b[i]):
				return false
		return true
	if a is Dictionary and b is Dictionary:
		if a.size() != b.size():
			return false
		for k in a:
			if not b.has(k) or not same(a[k], b[k]):
				return false
		return true
	return false


## Field-by-field description of how a snapshot differs from the TS one.
func diff(mine: Dictionary, theirs: Dictionary, label: String) -> String:
	var out: Array = []
	for part in ["core", "derived"]:
		for k in theirs[part]:
			var a = mine[part].get(k)
			var b = theirs[part][k]
			if not same(a, b):
				out.append("%s.%s: godot %s vs ts %s" % [part, k, JSON.stringify(a).left(400), JSON.stringify(b).left(400)])
	return "%s — %s" % [label, "; ".join(out.slice(0, 6))]


# ---- the replay ---------------------------------------------------------------

func apply(run: Warband.WarbandRun, op: Dictionary):
	match op.op:
		"buy": return run.buy(int(op.i))
		"sell": return run.sell(int(op.i))
		"pickAugment": return run.pickAugment(int(op.i))
		"takeCarousel": return run.takeCarousel(int(op.i))
		"pickCommander": return run.pickCommander(int(op.i))
		"reroll": return run.reroll()
		"buyXp": return run.buyXp()
		"lock": return run.toggleShopLock()
		"next":
			run.next()
			return null
		"place": return run.place(int(op.i), int(op.col), int(op.row))
		"equip": return run.equipItem(int(op.s), int(op.i))
		"pickDifficulty": return run.pickDifficulty(op.id)
		"setDifficulty":
			run.difficulty = WarbandDifficulty.difficultyById(op.id)
			return null
		"terrain": return run.applyTerrainCode(op.code)
		"beginFight": return run.beginFight()
	push_error("unknown op %s" % op.op)
	return null


func checkBattle(fx: Dictionary, idx: int, world: World, label: String) -> bool:
	var want: Dictionary = fx.battles[idx]
	var w: Dictionary = want.world
	var got := {"checksum": Commands.worldChecksum(world), "tick": world.tickCount, "alive": world.entities.filter(func(e): return e.alive).size()}
	if not same(got, w):
		failures.append("%s battle %d world: godot %s vs ts %s" % [label, idx, JSON.stringify(got), JSON.stringify(w)])
		return false
	return true


func liveFight(run: Warband.WarbandRun, fx: Dictionary, idx: int, label: String) -> bool:
	var want: Dictionary = fx.battles[idx]
	if not run.beginFight():
		failures.append("%s battle %d: beginFight refused" % [label, idx])
		return false
	var lb := Autobattle.LiveBattle.new(run.boardUnits(), run.pendingOpp, run.pendingSeed, 30.0, run.sideOpts(), run.fieldOpts())
	lb.begin()
	var sums: Array = []
	var events := 0
	hh = 2166136261
	var nextAt := 20
	for n in want.batches:
		lb.step(int(n))
		for ev in lb.fxEvents:
			events += 1
			_hv(ev.kind)
			_hv(ev.team)
			_hv(ev.x)
			_hv(ev.y)
		lb.fxEvents.clear()
		while lb.ticks >= nextAt:
			sums.append(Commands.worldChecksum(lb.world))
			nextAt += 20
	var kinds := hh
	var ok := true
	for i in mini(sums.size(), want.sums.size()):
		if not same(sums[i], want.sums[i]):
			failures.append("%s battle %d: live checksum diverges at tick %d" % [label, idx, (i + 1) * 20])
			ok = false
			break
	if ok and sums.size() != want.sums.size():
		failures.append("%s battle %d: %d checkpoints vs %d" % [label, idx, sums.size(), want.sums.size()])
		ok = false
	if ok and (events != int(want.events) or kinds != int(want.kinds)):
		failures.append("%s battle %d: fx events %d/%d vs %d/%d" % [label, idx, events, kinds, int(want.events), int(want.kinds)])
		ok = false
	var res := lb.result()
	if ok and not same(res, want.result):
		failures.append("%s battle %d result: godot %s vs ts %s" % [label, idx, JSON.stringify(res), JSON.stringify(want.result)])
		ok = false
	if ok:
		ok = checkBattle(fx, idx, lb.world, label)
	run.finishFight(res)
	return ok


func play(name: String) -> void:
	var fx = fixture("warband_%s.json" % name)
	if fx == null:
		return
	var cmd = fx.commander # "<ask>", null or an id
	var run := Warband.WarbandRun.new(int(fx.seed), cmd)
	var broken := ""
	var checked := 0
	for si in fx.steps.size():
		var step: Dictionary = fx.steps[si]
		if step.has("snap"):
			var want: Dictionary = fx.snaps[int(step.snap)]
			var mine := {"core": core(run), "derived": derived(run)}
			if not same(mine.core, want.core) or not same(mine.derived, want.derived):
				failures.append("%s: %s%s" % [name, ("after %s: " % broken) if broken != "" else "", diff(mine, want, "snapshot '%s'" % want.label)])
				return
			if broken != "":
				failures.append("%s: %s (snapshot after it matched)" % [name, broken])
				return
			checked += 1
			continue
		var label := "%s step %d (%s, round %d)" % [name, si, step.op, run.round]
		if step.op == "fight":
			Autobattle.lastWorld = null
			run.fight()
			var want_b: Dictionary = fx.battles[int(step.battle)]
			if broken == "" and (want_b.world == null) != (Autobattle.lastWorld == null):
				failures.append("%s: a battle was fought on one side only" % label)
				return
			if broken == "" and want_b.world != null:
				var A := Autobattle.alivePower(Autobattle.lastWorld, T.Team.Player)
				var B := Autobattle.alivePower(Autobattle.lastWorld, T.Team.Enemy)
				var r: Dictionary = want_b.result
				if A.count != int(r.survivorsA) or B.count != int(r.survivorsB) or A.power != int(r.powerA) or B.power != int(r.powerB):
					failures.append("%s: battle verdict godot %s/%s vs ts %s" % [label, JSON.stringify(A), JSON.stringify(B), JSON.stringify(r)])
					return
				if not checkBattle(fx, int(step.battle), Autobattle.lastWorld, label):
					return
		elif step.op == "live":
			if not liveFight(run, fx, int(step.battle), label):
				return
		else:
			var ret = apply(run, step)
			if step.ret != null and not same(ret, step.ret) and broken == "":
				broken = "%s returned %s, ts %s" % [label, str(ret), str(step.ret)]
		if broken == "" and hash_of(core(run)) != int(step.h):
			broken = "%s: state hash differs" % label
	eq(run.round, fx.final.round, "%s final round" % name)
	eq(run.placement(), fx.final.placement, "%s placement" % name)
	eq(_nz(run.outcome, null), fx.final.outcome, "%s outcome" % name)
	check(checked >= 3, "%s: only %d snapshots compared" % [name, checked])
	if broken != "":
		failures.append(broken)


func test_greedy() -> void:
	play("greedy")


func test_idle() -> void:
	play("idle")


func test_squire_live() -> void:
	play("squire_live")


func test_conqueror() -> void:
	play("conqueror")


func test_warlord_qm_live() -> void:
	play("warlord_qm_live")


func test_banneret() -> void:
	play("banneret")


func test_steward_live() -> void:
	play("steward_live")


func test_magnate() -> void:
	play("magnate")


func test_warden_drill() -> void:
	play("warden_drill")


func test_drillmaster() -> void:
	play("drillmaster")


func test_warpriest() -> void:
	play("warpriest")


func test_marshal_live() -> void:
	play("marshal_live")


func test_boss() -> void:
	play("boss")
