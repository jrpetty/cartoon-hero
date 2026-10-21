extends TestCase
# ai/economy.test.ts — the AI's economy, and the deadlock that used to stop it
# playing at all.
#
# Measured before this: on several Highlands seeds a `knight` AI issued four to
# six move orders in twelve sim-minutes, never attacked once, and sat at eleven
# villagers with 1680 wood and 315 food. The cause was a loop: `savingForAge`
# pauses villager production *and* caps the army at four, so the economy stops
# growing, so the age-up it is saving for never becomes affordable, so it keeps
# saving. Forever. These tests exist so it cannot come back quietly.
#
# The TS plays a fresh match per test; here the three twelve-minute Highlands
# duels (seeds 77, 909, 5) are played once and shared, since the Godot build
# plays exactly the same match from the same seed. Tests the TS gives a longer
# match (twenty minutes for "never holds production back" and "builds a
# Market", thirty for "reaches a decisive game") say below how they are
# shortened without weakening what they assert.


## Counts attack-move orders — the TS test swaps `world.issueMove` for a
## wrapper; a subclass override is the GDScript way.
class CountingWorld extends World:
	var attackMoves := 0

	func _init(seed: int) -> void:
		super(seed)

	func issueMove(ids: Array, tx: float, ty: float, queue := false, attackMove := false) -> void:
		if attackMove:
			attackMoves += 1
		super.issueMove(ids, tx, ty, queue, attackMove)


static var _matches := {}


## Play a real two-AI Highlands match and report what each side managed:
## {world, ais, attackMoves, kills}. `until` (optional) ends it early.
static func playMatch(seed: int, minutes: int, until = null) -> Dictionary:
	var map := AITestUtil.mapOf("highlands", seed, 2)
	var world := CountingWorld.new(seed)
	world.init(map, [{}, {}], [1.0, 1.0], [0, 1])
	var ais := [
		SkirmishAI.new(world, T.Team.Player, C.DIFFICULTIES.knight),
		SkirmishAI.new(world, T.Team.Enemy, C.DIFFICULTIES.knight),
	]
	for i in 20 * 60 * minutes:
		world.tick()
		for ai: SkirmishAI in ais:
			ai.update(1.0 / 20.0)
		world.drainEvents()
		if world.winner != null:
			break
		if until != null and until.call(world):
			break
	var kills: float = world.player(T.Team.Player).stats.unitsKilled + world.player(T.Team.Enemy).stats.unitsKilled
	return {"world": world, "ais": ais, "attackMoves": world.attackMoves, "kills": kills}


## The twelve-minute match for `seed`, played once per run.
static func match12(seed: int) -> Dictionary:
	if not _matches.has(seed):
		_matches[seed] = playMatch(seed, 12)
	return _matches[seed]


static func trainedVillagers(w: World, t: int) -> float:
	return float(w.player(t).stats.trainedByType.get("villager", 0.0))


static func countAlive(w: World, type: String) -> int:
	var n := 0
	for e: Entity in w.entities:
		if e.alive and e.kind == T.Kind.Building and e.type == type:
			n += 1
	return n


# ------------------------------------------------------ The AI actually plays the game --

func test_attacks_on_every_seed_not_just_the_lucky_ones() -> void:
	# Seeds 77, 909 and 5 all produced *zero* attack orders and at most one kill
	# across twelve minutes before the deadlock was fixed.
	for seed in [77, 909, 5]:
		var r := match12(seed)
		check(r.attackMoves > 5, "seed %d never launched an attack (%d attack moves)" % [seed, r.attackMoves])
		check(r.kills > 10, "seed %d: the armies never met (%d kills)" % [seed, r.kills])


func test_keeps_training_villagers_all_match_instead_of_pausing_to_bank() -> void:
	# Villagers *trained*, not peak or live population: what the deadlock did
	# was pause production, so the count produced over the match is what moves.
	for seed in [5, 77, 909]:
		var r := match12(seed)
		var w: World = r.world
		var trained := [trainedVillagers(w, T.Team.Player), trainedVillagers(w, T.Team.Enemy)]
		check(maxf(trained[0], trained[1]) > 15, "seed %d: trained %s and %s" % [seed, trained[0], trained[1]])
		# Both sides should have run an economy, not just the winner.
		check(minf(trained[0], trained[1]) > 8, "seed %d: trained %s and %s" % [seed, trained[0], trained[1]])


func test_never_holds_production_back_for_an_age_indefinitely() -> void:
	# The time box: whatever it is doing late in the match, it is not still
	# banking for an age-up it cannot afford. The TS asks at minute twenty;
	# an age once reached is never lost, so asking at minute twelve (the shared
	# seed-5 match) is the stronger form of the same assertion.
	var r := match12(5)
	var w: World = r.world
	var ages := [w.player(T.Team.Player).age, w.player(T.Team.Enemy).age]
	check(maxi(ages[0], ages[1]) > 0, "neither side ever advanced an age")


func test_reaches_a_decisive_game_rather_than_grinding_forever() -> void:
	# Not every match should end, but a whole sample of them ending in nothing
	# means nobody is fighting. The TS plays seeds 77, 5 and 1 for thirty minutes
	# each and asks that at least one ended; one decided match answers that, so
	# the seeds are tried quickest-first (in the TS build seed 1 is decided at
	# 15.5 minutes, 77 at 21.4, and 5 never) and the sample stops at the first.
	var decided := 0
	for seed in [1, 77, 5]:
		var r := playMatch(seed, 30)
		if r.world.winner != null:
			decided += 1
			break
	check(decided > 0, "no match reached a conclusion in half an hour")


# ------------------------------------------------------ The AI can fix a lopsided bank --

func test_builds_a_market() -> void:
	# It used to sit on 1600 wood and 300 food with a building in the game that
	# converts one into the other. The TS asks after twenty minutes of seed 909;
	# a Market standing at twelve (the shared match) is the same answer, sooner.
	var r := match12(909)
	check(countAlive(r.world, "market") > 0, "nobody built a Market")


func test_trades_a_surplus_rather_than_hoarding_it() -> void:
	# Directly: a Market, a mountain of wood and nothing to eat.
	var world := World.new(3)
	world.init(AITestUtil.mapOf("open_plains", 3, 2), [{}, {}], [1.0, 1.0], [0, 1])
	var ai := SkirmishAI.new(world, T.Team.Player, C.DIFFICULTIES.knight)
	var p := world.player(T.Team.Player)
	p.age = 1
	var base := SimTestUtil.tcOf(world, T.Team.Player)
	world.spawnBuilding(T.Team.Player, "market", base.x + 300, base.y, true)
	p.resources = {"food": 40.0, "wood": 2000.0, "gold": 0.0}
	var woodBefore: float = p.resources.wood
	for i in 20 * 90:
		world.tick()
		ai.update(1.0 / 20.0)
		world.drainEvents()
	check(p.resources.wood < woodBefore, "the surplus was never traded")


func test_puts_farms_down_when_short_of_food_without_waiting_for_crown_age() -> void:
	# Reaching the age that used to unlock farming *needs* the food farming
	# provides, which is the other half of the same deadlock.
	var r := match12(5)
	var w: World = r.world
	check(countAlive(w, "farm") > 0, "no farms in twelve minutes")
	var anyBanner := w.player(T.Team.Player).age <= 1 or w.player(T.Team.Enemy).age <= 1
	check(anyBanner, "this seed no longer exercises the pre-Crown case")


# ------------------------------------------------------------ Trade is not player-only --

func test_runs_carts_once_it_has_two_markets() -> void:
	# Trade Carts shipped as player-only economy. The first attempt gated them on
	# `diff.counters`, which is false for Knight: zero carts across six matches.
	var world := World.new(4)
	world.init(AITestUtil.mapOf("open_plains", 4, 2), [{}, {}], [1.0, 1.0], [0, 1])
	var ai := SkirmishAI.new(world, T.Team.Player, C.DIFFICULTIES.knight)
	var p := world.player(T.Team.Player)
	p.age = 1
	var base := SimTestUtil.tcOf(world, T.Team.Player)
	world.spawnBuilding(T.Team.Player, "market", base.x + 200, base.y, true)
	world.spawnBuilding(T.Team.Player, "market", base.x + 900, base.y, true)
	p.resources = {"food": 2000.0, "wood": 2000.0, "gold": 2000.0}
	p.popCap = 80.0
	for i in 20 * 120:
		world.tick()
		ai.update(1.0 / 20.0)
		world.drainEvents()
		p.resources.wood = maxf(p.resources.wood, 800.0) # keep it solvent
		p.resources.gold = maxf(p.resources.gold, 400.0)
	var carts := 0
	for e: Entity in world.entities:
		if e.alive and e.type == "trade_cart":
			carts += 1
	check(carts > 0, "never trained a Trade Cart with two Markets standing")
