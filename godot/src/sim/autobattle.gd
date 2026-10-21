## sim/autobattle.ts — the deterministic heart of the auto-battler ("Warband
## Tactics") mode: take two army compositions, simulate the fight headlessly on
## a small arena, and report who won and what survived. The existing combat sim
## does all the work — units auto-acquire and fight on their own — so a
## TFT-style round is just two stacks dropped in and ticked to a conclusion.
## Pure + deterministic (seeded), so it's fully testable and replay-safe.
##
## Shapes (Dictionaries, with the TS keys):
## - UnitStack {type, count, star?} — star 1..3, a TFT-style upgrade.
## - ArenaUnit {type, star?, items?, col?, row?} — a single placed unit.
## - BattleResult {winner: "A"|"B"|"draw", survivorsA, survivorsB, powerA,
##   powerB, ticks}.
## - SideOpts {buff?, traitBonus?, traitBuffs?, terrain?} — see mergeSideOpts.
## The TS class LiveBattle is `Autobattle.LiveBattle`.
class_name Autobattle

# The placement board is a 10×10 grid; each side owns a 5×10 half.
const GRID_COLS := 10
const GRID_ROWS := 10
const GRID_CELL := 40.0 # world units per cell

const STAR_MULT := [1.0, 1.0, 1.8, 3.2] # index by star (1..3)

# ---- opening orders: how each battle style enters the fight -----------------
#
# Depths are world-x offsets from the centre line, measured *toward* the enemy.
# The board is 10 cells of GRID_CELL, so ±180 is the far back rank.
const VANGUARD_DEPTH := 10.0 # plant on the line
const LINE_DEPTH := 44.0 # push a cell into them
const FLANK_DEPTH := 130.0 # come in well past their front
const INFILTRATE_X := 150.0 # land among their back ranks

# Lays a warband out in a clean 2-D grid on its own half of the arena (a
# TFT-style board): one unit per cell, generously spaced so nothing overlaps,
# the front rank nearest the centre line. Side −1 (player) sits left, side +1
# (enemy) right, both facing the middle.
const ROW_GAP := 48.0
const COL_GAP := 48.0
const FRONT_GAP := 66.0 # centre line → front rank


## Centre of board cell (col,row) in world space, around the arena centre.
static func cellToWorld(cx: float, cy: float, col: float, row: float) -> Dictionary:
	var left := cx - (GRID_COLS / 2.0) * GRID_CELL
	var top := cy - (GRID_ROWS / 2.0) * GRID_CELL
	return {"x": left + (col + 0.5) * GRID_CELL, "y": top + (row + 0.5) * GRID_CELL}


## Expand stacks (count>1) and pass through single units into a flat unit list.
static func normalize(list: Array) -> Array:
	var out: Array = []
	for u in list:
		var n := int(u.get("count", 1))
		for k in n:
			out.append({"type": u.type, "star": u.get("star"), "items": u.get("items"), "col": u.get("col"), "row": u.get("row")})
	return out


## Merge a side's own modifiers with the battlefield ones that hit everybody.
static func mergeSideOpts(own, field):
	if field == null:
		return own
	if own == null:
		return field
	var buff: Dictionary = (own.get("buff") if own.get("buff") != null else {}).duplicate()
	var fb = field.get("buff")
	for k in Traits.BUFF_KEYS:
		if fb != null and fb.get(k):
			buff[k] = float(buff.get(k, 0.0)) + fb[k]
	var traitBuffs: Dictionary = (own.get("traitBuffs") if own.get("traitBuffs") != null else {}).duplicate()
	var ftb = field.get("traitBuffs")
	if ftb != null:
		for id in ftb:
			var b: Dictionary = ftb[id]
			var cur: Dictionary = (traitBuffs[id] if traitBuffs.get(id) != null else {}).duplicate()
			for k in Traits.BUFF_KEYS:
				if b.get(k):
					cur[k] = float(cur.get(k, 0.0)) + b[k]
			traitBuffs[id] = cur
	var tbonus = own.get("traitBonus")
	if tbonus == null:
		tbonus = field.get("traitBonus")
	var terr = own.get("terrain")
	if terr == null:
		terr = field.get("terrain")
	return {
		"buff": buff if buff.size() > 0 else null,
		"traitBonus": tbonus,
		"traitBuffs": traitBuffs if traitBuffs.size() > 0 else null,
		"terrain": terr,
	}


## `opts?.key` for an optional SideOpts.
static func _opt(opts, key: String):
	return null if opts == null else opts.get(key)


static func spawnArmy(w: World, units: Array, team: int, side: float, posFn = null, opts = null) -> Array:
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0
	var n := units.size()
	var ids: Array = []
	# Active synergies for this side's composition (by distinct deployed types).
	var distinct: Array = []
	for u in units:
		if not distinct.has(u.type):
			distinct.append(u.type)
	var traits := Traits.activeTraits(distinct, _opt(opts, "traitBonus"))
	var buffByType := {}
	var optBuff = _opt(opts, "buff")
	var optTraitBuffs = _opt(opts, "traitBuffs")
	var optTerrain = _opt(opts, "terrain")
	for i in n:
		var au: Dictionary = units[i]
		if not C.UNITS.has(au.type):
			continue
		var star := clampi(int(au.star) if au.get("star") != null else 1, 1, 3)
		var mult: float = STAR_MULT[star]
		var myTraits = buffByType.get(au.type)
		if myTraits == null:
			var mine := {}
			for t in Traits.traitsOf(au.type):
				mine[t.id] = true
			myTraits = traits.filter(func(at): return mine.has(at.trait.id) and at.tier != null)
			buffByType[au.type] = myTraits
		var pos: Dictionary
		if au.get("col") != null and au.get("row") != null:
			pos = cellToWorld(cx, cy, float(au.col), float(au.row)) # explicit board placement wins
		elif posFn != null:
			pos = posFn.call(i, n, cx, cy, side)
		else:
			pos = {"x": cx + side * 170.0 + float(i % 4) * 16.0 * side, "y": cy - 50.0 + floorf(i / 4.0) * 18.0}
		var u := w.spawnUnit(team, au.type, pos.x, pos.y)
		if mult != 1.0:
			u.maxHp = JS.round(u.maxHp * mult)
			u.hp = u.maxHp
			u.attack = JS.round(u.attack * mult)
		for at in myTraits:
			if at.tier != null:
				Traits.applyBuff(u, at.tier.buff)
		var items = au.get("items")
		if items != null and items.size() > 0:
			Items.applyItems(u, items)
		if optBuff != null:
			Traits.applyBuff(u, optBuff) # run-wide augment buffs, last
		if optTraitBuffs != null: # battlefield conditions, by trait membership
			for t in Traits.traitsOf(au.type):
				var b = optTraitBuffs.get(t.id)
				if b != null:
					Traits.applyBuff(u, b)
		# Ground effects, from whichever cell this unit was placed on.
		if optTerrain != null and au.get("col") != null and au.get("row") != null:
			var ground = Terrain.terrainAt(optTerrain, int(au.col), int(au.row))
			if ground != null and ground.get("rangePct"):
				u.range = JS.round(u.range * (1.0 + ground.rangePct / 100.0))
			if ground != null and ground.get("speedPct"):
				u.speed = JS.round(u.speed * (1.0 + ground.speedPct / 100.0))
		u.stance = T.Stance.Aggressive # hunt — no economy, just fight
		u.variantRarity = star - 1 # a visual tier glow if rendered
		ids.append(u.id)
	return ids


## Send a side into the fight according to each unit's battle style. Shared by
## the headless resolver and the watchable battle so both agree exactly.
##
## `side` is −1 for the left (player) army and +1 for the right, so the enemy
## always lies in the `-side` direction.
##
## Returns the Infiltrators' jumps as (from → to) pairs so a UI can draw them:
## [{x0, y0, x1, y1, team}].
static func openingOrders(w: World, ids: Array, side: float, cx: float, cy: float) -> Array:
	var dir := -side # +1 when the enemy is to the right
	var leaps: Array = []
	var halfH := (GRID_ROWS / 2.0) * GRID_CELL
	for id in ids:
		var e = w.byId.get(id)
		if e == null or not e.alive:
			continue
		match BattleStyles.styleOf(e.type):
			"artillery":
				# Stands its ground and shoots from where you placed it. Deliberately
				# no creep-into-range: the enemy closes the distance for you, and
				# stepping forward only walks the crew into archer fire.
				pass
			"vanguard":
				w.issueMove([id], cx + dir * VANGUARD_DEPTH, e.y, false, true)
			"flanker":
				# Sweep to the nearer edge, then drive in behind their front rank.
				var edgeY := (cy - halfH + GRID_CELL * 0.5) if e.y < cy else (cy + halfH - GRID_CELL * 0.5)
				w.issueMove([id], e.x + dir * GRID_CELL, edgeY, false, true)
				w.issueMove([id], cx + dir * FLANK_DEPTH, edgeY, true, true) # queued second leg
			"infiltrator":
				# Not a march — they're simply behind you when it starts.
				var x0: float = e.x
				var y0: float = e.y
				e.x = cx + dir * INFILTRATE_X
				e.y = maxf(cy - halfH + 8.0, minf(cy + halfH - 8.0, y0))
				leaps.append({"x0": x0, "y0": y0, "x1": e.x, "y1": e.y, "team": e.team})
				# Then fight their way forward, into the backline they landed on.
				w.issueMove([id], cx + dir * 70.0, e.y, false, true)
			_:
				w.issueMove([id], cx + dir * LINE_DEPTH, e.y, false, true)
	return leaps


static func alivePower(w: World, team: int) -> Dictionary:
	var count := 0
	var power := 0
	for e: Entity in w.entities:
		if not e.alive or e.team != team or e.kind != T.Kind.Unit or e.type == "villager":
			continue
		count += 1
		power += 1 + e.variantRarity # star-weighted
	return {"count": count, "power": power}


## The auto-battler borrows a generated map purely as a substrate — it needs a
## world with dimensions, not a landscape. Now that ground has gameplay meaning
## (hills see further, woods slow you down), that scenery would leak into every
## Warband round as invisible modifiers nobody chose. Warband has its own
## terrain system, mirrored across the centre line so both sides face identical
## ground; the arena floor underneath it is flat.
static func flattenArena(w: World) -> void:
	var t := PackedByteArray()
	t.resize(w.terrain.size()) # all Grass
	t.fill(0)
	w.terrain = t


static func _verdict(A: Dictionary, B: Dictionary) -> String:
	if A.count > 0 and B.count == 0:
		return "A"
	if B.count > 0 and A.count == 0:
		return "B"
	if A.power == B.power:
		return "draw"
	return "A" if A.power > B.power else "B"


## The world the last headless battle was fought in. Not in the TS (which
## never hands it out): kept so the parity tests can checksum it, the way the
## TS fixture generator catches it by wrapping World.init.
static var lastWorld: World = null


static func resolveBattle(a: Array, b: Array, seed: int = 1, maxSeconds: float = 40.0, optsA = null, field = null) -> Dictionary:
	var map := MapGen.generateMap("open_plains", seed, 2)
	var w := World.new(seed)
	lastWorld = w
	w.init(map, [{}, {}], [1.0, 1.0], [0, 1]) # two hostile sides
	flattenArena(w)
	var cx := w.worldW / 2.0
	var cy := w.worldH / 2.0

	var idsA := spawnArmy(w, normalize(a), T.Team.Player, -1.0, null, mergeSideOpts(optsA, field))
	var idsB := spawnArmy(w, normalize(b), T.Team.Enemy, 1.0, null, field)
	# Each side enters by its units' battle styles — the same orders the watched
	# fight issues, so headless and live resolve identically.
	openingOrders(w, idsA, -1.0, cx, cy)
	openingOrders(w, idsB, 1.0, cx, cy)

	var maxTicks := int(C.SIM_HZ * maxSeconds)
	var mana := {}
	var t := 0
	while t < maxTicks:
		w.tick()
		chargeAbilities(w, mana)
		w.drainEvents()
		if t % 10 == 0:
			if alivePower(w, T.Team.Player).count == 0 or alivePower(w, T.Team.Enemy).count == 0:
				break
		t += 1

	var A := alivePower(w, T.Team.Player)
	var B := alivePower(w, T.Team.Enemy)
	return {"winner": _verdict(A, B), "survivorsA": A.count, "survivorsB": B.count, "powerA": A.power, "powerB": B.power, "ticks": t}


static func BOARD_LAYOUT(i: int, n: int, cx: float, cy: float, side: float) -> Dictionary:
	var rows := mini(4, maxi(1, ceili(n / 3.0))) # up to 4 ranks deep
	var row := i % rows
	var col := floori(float(i) / rows)
	return {
		"x": cx + side * (FRONT_GAP + col * COL_GAP),
		"y": cy - ((rows - 1) / 2.0) * ROW_GAP + row * ROW_GAP,
	}


## Units with a signature ability build "mana" as the fight goes and cast it
## when full — faster for higher-star units. After a cast the long sim cooldown
## is cleared so the charge meter (not the cooldown) paces repeat casts.
##
## Shared by both resolution paths on purpose. This used to live only on
## LiveBattle, which meant the headless resolver never cast a single ability:
## the very same matchup came out differently depending on whether you watched
## it, and every balance measurement taken through resolveBattle silently
## excluded abilities altogether.
static func chargeAbilities(world: World, mana: Dictionary) -> void:
	var ABILITIES: Dictionary = C.ABILITIES
	var dt: float = C.SIM_DT
	for e: Entity in world.entities:
		if not e.alive or e.kind != T.Kind.Unit or not ABILITIES.has(e.type) or e.type == "villager":
			continue
		if e.abilityActive > 0:
			continue # mid-cast: let the buff run before recharging
		var star := e.variantRarity + 1
		var chargeTime := maxf(2.4, 5.0 - (star - 1) * 1.2) # 1★ 5s · 2★ 3.8s · 3★ 2.6s
		var m: float = mana.get(e.id, 0.0) + dt / chargeTime
		if m >= 1.0:
			world.useAbility([e.id])
			e.abilityCooldown = 0.0 # let mana, not the 20s+ cooldown, gate the next cast
			m = 0.0
		mana[e.id] = m


## A watchable version of resolveBattle: same deterministic setup, but the
## world is held so a UI can render it and advance it tick-by-tick. The outcome
## is identical regardless of how the steps are batched (tick() is pure).
class LiveBattle extends RefCounted:
	var world: World
	var cx: float
	var cy: float
	var ticks := 0
	var started := false
	var done := false
	## Combat events from the last step(s) — drained by the UI for hit/death FX.
	var fxEvents: Array = []
	var idsA: Array
	var idsB: Array
	var maxTicks: int
	var _result = null
	var mana := {} # 0..1 ability charge per unit

	func _init(a: Array, b: Array, seed: int = 1, maxSeconds: float = 30.0, optsA = null, field = null) -> void:
		var map := MapGen.generateMap("open_plains", seed, 2)
		world = World.new(seed)
		world.init(map, [{}, {}], [1.0, 1.0], [0, 1])
		Autobattle.flattenArena(world)
		cx = world.worldW / 2.0
		cy = world.worldH / 2.0
		idsA = Autobattle.spawnArmy(world, Autobattle.normalize(a), T.Team.Player, -1.0, Autobattle.BOARD_LAYOUT, Autobattle.mergeSideOpts(optsA, field))
		idsB = Autobattle.spawnArmy(world, Autobattle.normalize(b), T.Team.Enemy, 1.0, Autobattle.BOARD_LAYOUT, field)
		maxTicks = int(C.SIM_HZ * maxSeconds)

	## The fight begins: each unit attack-moves straight across to the centre
	## line at its own rank height, so opposing ranks meet head-on in a spread
	## front line instead of funnelling into one overlapping pile.
	func begin() -> void:
		if started:
			return
		started = true
		var leaps: Array = []
		leaps.append_array(Autobattle.openingOrders(world, idsA, -1.0, cx, cy))
		leaps.append_array(Autobattle.openingOrders(world, idsB, 1.0, cx, cy))
		# Surface each Infiltrator's jump so the screen can draw the pounce.
		for l in leaps:
			fxEvents.append({"kind": "leap", "x": l.x0, "y": l.y0, "team": l.team, "data": "%s,%s" % [JS.num(l.x1), JS.num(l.y1)]})

	## Advance the sim a few ticks; stops automatically when one side is wiped.
	func step(n: int = 1) -> void:
		if not started or done:
			return
		var k := 0
		while k < n and not done:
			world.tick()
			tickAbilities() # charge + auto-cast signature abilities (per tick → deterministic)
			for ev in world.drainEvents():
				if fxEvents.size() < 256:
					fxEvents.append(ev)
			ticks += 1
			if ticks % 5 == 0 or ticks >= maxTicks:
				var a: int = Autobattle.alivePower(world, T.Team.Player).count
				var bb: int = Autobattle.alivePower(world, T.Team.Enemy).count
				if a == 0 or bb == 0 or ticks >= maxTicks:
					finish()
			k += 1

	func tickAbilities() -> void:
		Autobattle.chargeAbilities(world, mana)

	## Current ability charge (0..1) for a unit, for the charge-bar render.
	func chargeOf(id: int) -> float:
		return mana.get(id, 0.0)

	func finish() -> void:
		if _result != null:
			return
		var A := Autobattle.alivePower(world, T.Team.Player)
		var B := Autobattle.alivePower(world, T.Team.Enemy)
		_result = {"winner": Autobattle._verdict(A, B), "survivorsA": A.count, "survivorsB": B.count, "powerA": A.power, "powerB": B.power, "ticks": ticks}
		done = true

	## The battle result, forcing a verdict if it hasn't ended on its own yet.
	func result() -> Dictionary:
		if _result == null:
			finish()
		return _result
