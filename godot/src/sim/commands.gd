## sim/commands.ts — networked-play foundation: a serializable representation
## of every player action, a deterministic applier, and a state checksum for
## desync detection.
##
## In lockstep multiplayer each client runs the same deterministic sim and only
## exchanges these Commands (not state). A command is scheduled to run on an
## agreed future tick and applied identically on every client before that tick
## is simulated — so all clients stay in perfect sync from the same seed.
##
## A Command is a Dictionary with the same keys as the TS `Command` union, e.g.
##   {"t": "move", "team": 0, "ids": [..], "x": .., "y": .., "queue": false, "attackMove": false}
##   {"t": "attack", "team", "ids", "target", "queue"}
##   {"t": "gather", "team", "ids", "node", "queue"}
##   {"t": "build", "team", "ids", "building", "queue"}
##   {"t": "stop" | "hold" | "ability" | "delete" | "trade_route", "team", "ids"}
##   {"t": "stance", "team", "ids", "stance"}
##   {"t": "place", "team", "building", "x", "y", "builders"}
##   {"t": "train", "team", "buildingId", "unit"}
##   {"t": "cancel", "team", "buildingId", "index"}
##   {"t": "rally", "team", "buildingId", "x", "y"}
##   {"t": "garrison", "team", "ids", "buildingId"}
##   {"t": "ungarrison" | "gate", "team", "buildingId"}
##   {"t": "research", "team", "buildingId", "tech"}
##   {"t": "trade", "team", "action"}
##   {"t": "banner", "team", "x", "y"}
##   {"t": "autoreseed", "team", "on"}
## Numbers may arrive as floats (from JSON); ids are cast to int here.
class_name Commands


## Keep only ids the issuing team actually owns — a client can never command
## another player's units, and it keeps application order-independent.
static func owned(world: World, team: int, ids: Array) -> Array:
	var out: Array = []
	for raw in ids:
		var id := int(raw)
		var e = world.byId.get(id)
		if e != null and e.alive and e.team == team:
			out.append(id)
	return out


static func ownsBuilding(world: World, team: int, id: int) -> bool:
	var e = world.byId.get(id)
	return e != null and e.alive and e.team == team


## Apply one command to the world. Pure w.r.t. determinism: same world + same
## command => same result on every client. Ownership is enforced here.
static func applyCommand(world: World, c: Dictionary) -> void:
	var team := int(c.team)
	match c.t:
		"move":
			var ids := owned(world, team, c.ids)
			if c.get("formation", false):
				world.issueFormationMove(ids, c.x, c.y, c.get("queue", false), c.get("attackMove", false))
			else:
				world.issueMove(ids, c.x, c.y, c.get("queue", false), c.get("attackMove", false))
		"attack":
			world.issueAttack(owned(world, team, c.ids), int(c.target), c.get("queue", false))
		"gather":
			world.issueGather(owned(world, team, c.ids), int(c.node), c.get("queue", false))
		"build":
			world.issueBuildRepair(owned(world, team, c.ids), int(c.building), c.get("queue", false))
		"stop":
			world.issueStop(owned(world, team, c.ids))
		"hold":
			world.issueHold(owned(world, team, c.ids))
		"stance":
			world.setStance(owned(world, team, c.ids), int(c.stance))
		"ability":
			world.useAbility(owned(world, team, c.ids))
		"place":
			# Atomic: place the foundation and assign the chosen builders in one step,
			# since under lockstep the building doesn't exist locally until this runs.
			var b = world.placeBuilding(team, c.building, c.x, c.y)
			if b != null:
				world.issueBuildRepair(owned(world, team, c.builders), b.id, false)
		"train":
			if ownsBuilding(world, team, int(c.buildingId)):
				world.trainUnit(team, int(c.buildingId), c.unit)
		"cancel":
			if ownsBuilding(world, team, int(c.buildingId)):
				world.cancelProduction(team, int(c.buildingId), int(c.index))
		"rally":
			if ownsBuilding(world, team, int(c.buildingId)):
				world.setRally(int(c.buildingId), c.x, c.y)
		"garrison":
			world.garrison(owned(world, team, c.ids), int(c.buildingId))
		"ungarrison":
			if ownsBuilding(world, team, int(c.buildingId)):
				world.ungarrison(int(c.buildingId))
		"research":
			if ownsBuilding(world, team, int(c.buildingId)):
				world.research(team, int(c.buildingId), c.tech)
		"gate":
			if ownsBuilding(world, team, int(c.buildingId)):
				world.toggleGate(int(c.buildingId))
		"trade":
			world.marketTrade(team, c.action)
		"banner":
			world.placeBanner(team, c.x, c.y)
		# Disbanding your own troops. Goes through the command layer like anything
		# else so it stays lockstep-safe and shows up in a replay.
		"delete":
			world.deleteUnits(owned(world, team, c.ids))
		# Put carts on the longest route they can reach. The choice of route is the
		# sim's, not the player's — the pay is the distance, so there is exactly one
		# right answer and asking the player to click it is busywork.
		"trade_route":
			world.assignTradeRoute(owned(world, team, c.ids))
		# A standing preference rather than an order, but it still changes what the
		# sim does, so it goes through the command layer like everything else.
		"autoreseed":
			var p = world.player(team)
			if p != null:
				p.autoReseed = bool(c.on)


## Deterministic order so a set of commands applies identically everywhere:
## by team, then by stable arrival index within the team. Takes
## [{cmd, seq}, ...] and returns the commands.
static func sortCommands(cmds: Array) -> Array:
	var arr := cmds.duplicate()
	JS.sort_by_cmp(arr, func(a, b):
		var d: float = float(a.cmd.team) - float(b.cmd.team)
		return d if d != 0.0 else float(a.seq) - float(b.seq))
	return arr.map(func(x): return x.cmd)


# --- State checksum --------------------------------------------------------
# A cheap FNV-1a-style rolling hash over the salient sim state. Floats are
# quantized to 0.01 so identical same-engine sims hash equal while real
# divergence (units in different places) still trips it.

## `n | 0` for a JS number.
static func _toInt32(n) -> int:
	if n is int:
		return JS.i32(n)
	var f: float = n
	if is_nan(f) or is_inf(f):
		return 0
	# ToInt32: truncate, then wrap modulo 2^32.
	var t := JS.trunc(f)
	if absf(t) < 9.0e18:
		return JS.i32(int(t))
	return JS.i32(int(fmod(t, 4294967296.0)))


static func mix(acc: int, n) -> int:
	acc = (acc ^ _toInt32(n)) & 0xFFFFFFFF
	return JS.u32(JS.imul(acc, 16777619))


static func worldChecksum(world: World) -> int:
	var h := 2166136261
	h = mix(h, world.tickCount)
	# Entities in id order (entities array is append-only, ids monotonic).
	for e: Entity in world.entities:
		if not e.alive:
			continue
		h = mix(h, e.id)
		h = mix(h, e.team)
		h = mix(h, JS.round(e.x * 100.0))
		h = mix(h, JS.round(e.y * 100.0))
		h = mix(h, JS.round(e.hp * 100.0))
		h = mix(h, e.order.kind)
		h = mix(h, e.order.target)
		# Also hash state that can diverge before it shows up in position/hp, so a
		# desync is caught early: shift-queue depth, carried resources, attack
		# cooldown, and production progress.
		h = mix(h, e.order.queue.size())
		h = mix(h, JS.round(e.carry * 100.0))
		h = mix(h, JS.round(e.attackCooldown * 100.0))
		h = mix(h, e.productionQueue.size())
		h = mix(h, JS.round(e.productionTime * 100.0))
	# Per-player economy/pop/age.
	for t in world.numTeams:
		var p = world.player(t)
		if p == null:
			continue
		h = mix(h, JS.round(p.resources.food))
		h = mix(h, JS.round(p.resources.wood))
		h = mix(h, JS.round(p.resources.gold))
		h = mix(h, p.popUsed)
		h = mix(h, p.age)
	return JS.u32(h)
