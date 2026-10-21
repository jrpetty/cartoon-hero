## sim/warband.ts — "Warband Tactics", the auto-battler run engine (logic only;
## the screen draws it). A run is: shop for units → they auto-merge into
## star-ups → fight an opponent's warband (resolved by
## Autobattle.resolveBattle) → lose life if you lose → last warband standing
## wins. Deterministic via a seeded RNG so it's testable and replay-safe.
##
## The TS module's class WarbandRun is `Warband.WarbandRun`; a Piece is
## `Warband.Piece` (a class, so identity behaves as with the TS objects); an
## Opponent is `Warband.Opponent`. ArenaUnits, augments, commanders, camps and
## conditions are Dictionaries with the TS keys.
class_name Warband

## How a WarbandRun opens when no commander argument is passed at all (the TS
## `undefined`): on the difficulty and commander choices.
const ASK := "<ask>"

## Copies of each unit type in the shared lobby pool, by tier (1..5). Cheaper
## units are plentiful; carries are scarce — so contesting a comp is real.
const POOL_SIZE := [0, 29, 22, 16, 12, 10]

## Per-shop appearance odds (%) of each tier, indexed by player level (1..9).
const TIER_ODDS := [
	[100, 0, 0, 0, 0],
	[100, 0, 0, 0, 0],
	[70, 30, 0, 0, 0],
	[55, 30, 15, 0, 0],
	[45, 33, 20, 2, 0],
	[35, 35, 25, 5, 0],
	[22, 33, 33, 10, 2],
	[16, 24, 35, 20, 5],
	[10, 16, 30, 30, 14],
]
# Cumulative xp needed for level i, indexed by level (1..9). The level-9 entry
# matters twice over: without it the climb stops at 8 (the loop compares
# against XP_TO_LEVEL[level+1], which was undefined) and the header's xp
# readout printed "NaN".
const XP_TO_LEVEL := [0, 0, 2, 6, 12, 22, 38, 60, 90, 130]
const SHOP_SIZE := 5
const REROLL_COST := 2
const XP_BUY := 4 # gold → +4 xp
const MAX_LEVEL := 9

# Default fill order for auto-placing un-positioned units: front rank first
# (the column nearest the centre line), rows from the middle outward.
const ROW_ORDER := [4, 5, 3, 6, 2, 7, 1, 8, 0, 9]
static var _playerCells = null


static func PLAYER_CELLS() -> Array:
	if _playerCells == null:
		var out: Array = []
		for c in [4, 3, 2, 1, 0]:
			for r in ROW_ORDER:
				out.append(Vector2i(c, r))
		_playerCells = out
	return _playerCells


const NAMES := ["Ironclad", "Crimson", "Verdant", "Stormcrow", "Ashveil", "Goldhand", "Nightfall"]
const PLAYS := ["economist", "aggressor", "roller"]

## Life lost when a warband is beaten, TFT-style: a flat toll that grows with
## the stage, plus one point per surviving enemy unit weighted by its star
## (a 3★ hurts three times as much as a 1★).
##
## The stage toll is what stops the early game from being decided by a couple
## of unlucky rounds, and what stops the late game from dragging.
const STAGE_DAMAGE := [0, 0, 2, 5, 8, 11, 15, 20]

## Gold paid for a run of wins *or* losses. Losing streaks pay too — that's the
## comeback lever: a warband being beaten every round is quietly banking for a
## level spike, so the lobby doesn't simply run away from whoever falls behind.
const STREAK_GOLD := [0, 0, 1, 1, 2, 3]

## Star scaling used when weighing a board we don't simulate (matches the sim).
const STAR_POWER := [0.0, 1.0, 1.8, 3.2]

## Bench slots — reserves beyond your deployed board, exactly like TFT's nine.
const BENCH_SLOTS := 9


## Re-exported: the screen and tests read it from here.
static var UNIT_TIER: Dictionary:
	get:
		return C.UNIT_TIER


## A unit you own: on the board if `deployed`, else on the bench.
class Piece extends RefCounted:
	var type: String
	var star: int
	var items: Array
	var col = null ## int or null
	var row = null ## int or null
	var deployed := false

	func _init(type_: String = "", star_: int = 1, items_: Array = []) -> void:
		type = type_
		star = star_
		items = items_


class Opponent extends RefCounted:
	var id: int
	var name: String
	var life: int = 100
	var alive := true

	func _init(id_: int, name_: String) -> void:
		id = id_
		name = name_


## An AI opponent's hidden economy — identical rules to the player's.
class FoeBrain extends RefCounted:
	var gold := 2
	var level := 1
	var xp := 0
	var pieces: Array = [] ## of Piece
	var rng: RNG
	var favored: Array = []
	## How this warband plays: bank for interest, rush levels, or roll for stars.
	var play := "economist"


static func _tier(type: String) -> int:
	var t = C.UNIT_TIER.get(type)
	return 0 if t == null else int(t)


static func _tier1(type: String) -> int:
	var t = C.UNIT_TIER.get(type)
	return 1 if t == null else int(t)


## `[...new Set(arr)]`
static func _uniq(arr: Array) -> Array:
	var out: Array = []
	var seen := {}
	for v in arr:
		if not seen.has(v):
			seen[v] = true
			out.append(v)
	return out


## An opponent's "comp identity": a few core types it drafts toward, so its
## board concentrates into star-ups and synergies instead of a random spread.
static func pickFavored(rng: RNG) -> Array:
	var out: Array = []
	for tier in [1, 1, 2, 3]: # two cheap cores to spam-merge + a couple of carries
		var pool: Array = C.TIER_UNITS[tier]
		if pool.size() > 0:
			out.append(pool[rng.int_(0, pool.size() - 1)])
	return _uniq(out)


## Combine any 3 same type+star into one of the next star (shared by all
## warbands). Returns each star-up made ({type, star}), so the screen can
## celebrate it.
static func mergeBoard(pieces: Array, stash: Array) -> Array:
	var made: Array = []
	var maxItems := int(C.MAX_ITEMS)
	for star in [1, 2]:
		var changed := true
		while changed:
			changed = false
			var counts := {}
			for p in pieces:
				if p.star == star:
					counts[p.type] = counts.get(p.type, 0) + 1
			for type in counts:
				if counts[type] >= 3:
					var removed := 0
					var carried: Array = []
					var i := pieces.size() - 1
					while i >= 0 and removed < 3:
						if pieces[i].type == type and pieces[i].star == star:
							carried.append_array(pieces[i].items) # the upgrade inherits the components' relics
							pieces.remove_at(i)
							removed += 1
						i -= 1
					pieces.append(Piece.new(type, star + 1, carried.slice(0, maxItems)))
					stash.append_array(carried.slice(maxItems)) # overflow relics go back to the stash
					made.append({"type": type, "star": star + 1})
					changed = true
	return made


static func stageOf(round: int) -> int:
	return floori((round - 1) / 5.0) + 1


static func stageBaseDamage(stage: int) -> int:
	return STAGE_DAMAGE[maxi(0, mini(stage, STAGE_DAMAGE.size() - 1))]


## Total life a defeat costs, before any commander shield.
static func roundDamage(round: int, survivorPower: int) -> int:
	return stageBaseDamage(stageOf(round)) + survivorPower


static func streakBonus(streak: int) -> int:
	return STREAK_GOLD[mini(absi(streak), STREAK_GOLD.size() - 1)]


## Lay a warband out by what each unit *does* in a fight rather than by how
## strong it is. Vanguards and Line troops take the front rank, Flankers sit
## just behind them on the outside rows, Artillery goes to the very back, and
## Infiltrators start at the back too (they leap regardless, so the back rank is
## the safest place to stage them).
##
## `side` is +1 for the enemy half (cols 5..9) and −1 for the player's (0..4).
## A board that puts a trebuchet on the front line is the single most obvious
## tell of a dumb opponent, and this is what removes it. `pieces` are Pieces or
## Dictionaries with {type, star, items}.
static func placeByStyle(pieces: Array, side: int, terrain: Array = []) -> Array:
	# Depth 0 = closest to the centre line, 4 = furthest back.
	var depthFor := {"vanguard": 0, "line": 1, "flanker": 1, "artillery": 3, "infiltrator": 4}
	# Rows fill from the middle outward, except Flankers which want the edges.
	var midRows := [4, 5, 3, 6, 2, 7, 1, 8, 0, 9]
	var edgeRows := [0, 9, 1, 8, 2, 7, 3, 6, 4, 5]
	var used := {}
	var out: Array = []
	# Front ranks first so the wall is placed before anything behind it.
	var ordered := pieces.duplicate()
	JS.sort_by_cmp(ordered, func(p, q):
		var dp: int = depthFor.get(BattleStyles.styleOf(p.type), 1)
		var dq: int = depthFor.get(BattleStyles.styleOf(q.type), 1)
		return dp - dq if dp - dq != 0 else q.star - p.star)
	for p in ordered:
		var style := BattleStyles.styleOf(p.type)
		var wantDepth: int = depthFor.get(style, 1)
		var rows: Array = edgeRows if style == "flanker" else midRows
		var placed := false
		# Try the preferred depth, then step backwards, then forwards.
		var depths: Array = [wantDepth]
		for d in range(1, 5):
			if wantDepth + d < 5:
				depths.append(wantDepth + d)
			if wantDepth - d >= 0:
				depths.append(wantDepth - d)
		for d in depths:
			var col: int = 5 + d if side == 1 else 4 - d
			for row in rows:
				var key: int = col * 10 + row
				if used.has(key) or Terrain.isBlocked(terrain, col, row):
					continue
				used[key] = true
				out.append({"type": p.type, "star": p.star, "items": p.items, "col": col, "row": row})
				placed = true
				break
			if placed:
				break
	return out


class WarbandRun extends RefCounted:
	var rng: RNG
	var round := 0
	var gold := 2
	var level := 1
	var xp := 0
	var streak := 0 # +win streak / −loss streak
	var life := 100
	var pieces: Array = [] ## of Piece — bench + board combined; the top `level` deploy
	var itemStash: Array = [] ## unequipped relics
	var shop: Array = [] ## of String or null
	var opponents: Array = [] ## of Opponent
	## "difficulty" | "commander" | "augment" | "draft" | "shop" | "battle" | "result" | "over"
	var phase := "shop"
	var outcome = null ## "win" | "loss" | null
	## {won, foe, youLeft, foeLeft, dmg, relics, gold, creep} or null
	var lastResult = null
	# ---- augments (TFT-style run-defining picks) ----
	var augments: Array = []
	var augmentOffer: Array = []
	# ---- the commander leading this warband ----
	var commander = null ## a WARBAND_COMMANDERS entry, or null
	var commanderOffer: Array = []
	var freeRerolls := 0 # reset each round by the commander's perk
	# ---- the round's PvE camp, when this is a monster round ----
	var pendingCamp = null
	## The weather and ground this round is fought on — it hits both warbands.
	var condition: Dictionary = Conditions.CLEAR
	## This round's board terrain, mirrored across the centre line.
	var terrain: Array = []
	## The seed that produced it, so a board can be noted down and found again.
	var terrainSeed := 0
	## Shop held through the round — the classic "one copy off a 3★" hold.
	var shopLocked := false
	# ---- the shared draft, on carousel rounds ----
	## What's still on the ring when your turn comes (rivals pick before you).
	var carousel: Array = []
	## How many rivals took their pick ahead of you — you're that far down.
	var draftAhead := 0
	## One entry per fought round, and the tallies behind it. A run that ends is
	## a story — which fights you won, when the bleeding started, what you spent
	## it all on — and none of that survived past the "you placed #5" line.
	## Entries: {round, stage, foe, won, creep, dmg, youLeft, foeLeft, life}.
	var history: Array = []
	var tally := {
		"wins": 0, "losses": 0, "campsCleared": 0, "campsLost": 0,
		"bestStreak": 0, "worstStreak": 0,
		"goldSpent": 0, "goldEarned": 0, "rerolls": 0, "levelUps": 0,
		"relicsWon": 0, "lifeLost": 0, "unitsBought": 0, "unitsSold": 0,
	}
	## Set whenever pieces merge into a star-up, for the screen's celebration.
	var lastMerge = null ## {type, star} or null
	## Set whenever two components fuse into a relic, for the screen's flourish.
	var lastFusion = null ## {item, type} or null
	## How hard the lobby plays. Veteran unless the run opened on the choice.
	var difficulty: Dictionary = WarbandDifficulty.VETERAN
	## The final challenge: set when the lobby empties, cleared when it is fought.
	var bossPending := false
	## True while the boss round is the one on the table.
	var bossRound := false
	## Whether the crown was actually taken, once the run is over.
	var bossCleared := false
	# The round's matchup, fixed when the shop opens so the on-board preview shows
	# the real upcoming enemy and the live fight uses the same deterministic seed.
	var pendingSeed := 0
	var pendingOpp: Array = []
	var pendingFoe = null ## Opponent or null
	var foeBrains: Array = [] # each opponent's hidden economy
	var pool := {} # shared lobby unit pool (you + foes draw from it)

	## `commander` picks how the run opens:
	##   omitted (Warband.ASK) → the run starts in the "commander" phase and you choose one
	##   an id                 → that commander leads, no pick
	##   null                  → no commander at all (used by tests that want bare rules)
	func _init(seed = null, commander_ = "<ask>") -> void:
		if seed == null:
			seed = int(randf() * 1e9)
		rng = RNG.new(int(seed))
		for type in C.UNIT_TIER:
			if C.UNITS.has(type):
				var tier := int(C.UNIT_TIER[type])
				pool[type] = Warband.POOL_SIZE[tier] if tier < Warband.POOL_SIZE.size() else 12
		for id in Warband.NAMES.size():
			opponents.append(Warband.Opponent.new(id, Warband.NAMES[id]))
		# Every opponent runs the exact same economy as the player, from the same
		# starting point (2 gold, level 1, empty board), on its own seeded stream.
		for o in opponents:
			var b := Warband.FoeBrain.new()
			b.rng = rng.fork(o.id + 1)
			b.favored = Warband.pickFavored(b.rng)
			b.play = Warband.PLAYS[o.id % Warband.PLAYS.size()] # a spread of styles across the lobby
			foeBrains.append(b)
		var ask: bool = commander_ is String and commander_ == Warband.ASK
		if not ask and commander_ != null:
			applyCommander(WarbandCommanders.warbandCommander(commander_))
		startRound()
		# With no commander named, the run opens on its two choices: how hard the
		# lobby plays, then who leads. A named (or explicitly null) commander means
		# a scripted or tested run, which takes the defaults and starts shopping.
		if ask:
			commanderOffer = WarbandCommanders.offerCommanders(rng)
			phase = "difficulty"

	## The lobby difficulties on offer at the start of a run.
	func difficultyOffer() -> Array:
		return C.WARBAND_DIFFICULTIES

	## Set how hard the lobby plays, and move on to the commander choice.
	func pickDifficulty(id: String) -> bool:
		if phase != "difficulty":
			return false
		difficulty = WarbandDifficulty.difficultyById(id)
		gold += int(difficulty.startGold)
		phase = "commander" if commanderOffer.size() > 0 else "shop"
		return true

	## `commander?.key ?? fallback`
	func _cmd(key: String, fallback = 0):
		if commander == null:
			return fallback
		var v = commander.get(key)
		return fallback if v == null else v

	## Take a commander for the run, applying its one-off effects.
	func applyCommander(c) -> void:
		commander = c
		if c == null:
			return
		if c.get("startGold"):
			gold += int(c.startGold)
		freeRerolls = int(c.get("freeRerolls", 0))
		reconcile() # an extra board slot takes effect at once

	## Choose one of the offered commanders and begin the run properly.
	func pickCommander(index: int) -> bool:
		if phase != "commander":
			return false
		if index < 0 or index >= commanderOffer.size():
			return false
		var c = commanderOffer[index]
		applyCommander(c)
		commanderOffer = []
		phase = "shop"
		return true

	# ---- economy / shop -------------------------------------------------------
	# You field exactly your level — levelling up is what unlocks another board
	# slot, so teching is a real tradeoff against buying/rerolling.
	func boardCap() -> int:
		return level + int(augSum("boardSlots")) + int(_cmd("boardSlots"))

	## How many pieces deploy this round (for the screen's "deploying top N").
	func deployCount() -> int:
		return boardCap()

	# ---- augment-modified economy knobs --------------------------------------
	## Sum a numeric field across every owned augment.
	func augSum(key: String) -> float:
		var n := 0.0
		for a in augments:
			n += float(a.get(key, 0.0))
		return n

	## What a reroll costs right now — free while the commander's rerolls last.
	func rerollCost() -> int:
		if freeRerolls > 0:
			return 0
		var c := Warband.REROLL_COST
		for a in augments:
			if a.get("rerollCost") != null:
				c = mini(c, int(a.rerollCost))
		return c

	## Hold this shop through the coming round instead of rerolling it away.
	func toggleShopLock() -> bool:
		if phase != "shop":
			return false
		shopLocked = not shopLocked
		return true

	## A short code for this round's ground, so a board can be found again.
	func terrainCode() -> String:
		return Terrain.terrainCode(terrainSeed)

	## Re-roll this round's ground from a code you typed in. Showing the seed but
	## giving you no way to enter one meant a board you liked was a board you
	## could only ever describe.
	##
	## Anything standing on a cell the new ground blocks is lifted back off the
	## board, and the opponent's placement is re-derived, so the fight you set up
	## is the fight you'll get.
	func applyTerrainCode(code: String) -> bool:
		if phase != "shop":
			return false
		var seed = Terrain.parseTerrainCode(code)
		if seed == null:
			return false
		terrainSeed = seed
		terrain = Terrain.rollTerrain(seed, round)
		for p in pieces:
			if p.col == null or p.row == null:
				continue
			if Terrain.isBlocked(terrain, p.col, p.row):
				p.col = null
				p.row = null
		if pendingFoe != null:
			pendingOpp = foeBoard(foeBrains[pendingFoe.id])
		reconcile()
		return true

	## Free rerolls left this round (for the shop button's label).
	func freeRerollsLeft() -> int:
		return freeRerolls

	## The interest cap — 5 gold unless an augment or commander raises it.
	func interestCap() -> int:
		var cap := 5
		for a in augments:
			if a.get("interestCap") != null:
				cap = maxi(cap, int(a.interestCap))
		if commander != null and commander.get("interestCap") != null:
			cap = maxi(cap, int(commander.interestCap))
		return cap

	## The level the shop rolls at — a commander can read one level higher.
	func shopLevel() -> int:
		return mini(Warband.MAX_LEVEL, level + int(_cmd("shopLevelBonus")))

	## How often a relic is earned (every N rounds; augments speed this up).
	func relicEvery() -> int:
		var n := 3
		for a in augments:
			if a.get("relicEvery") != null:
				n = mini(n, int(a.relicEvery))
		return n

	## This round's shop tier odds (%), for the level-up tooltip.
	func shopOdds() -> Array:
		return Warband.TIER_ODDS[shopLevel() - 1]

	## XP progress toward the next level, for the header bar: {xp, need, max}.
	func xpProgress() -> Dictionary:
		if level >= Warband.MAX_LEVEL:
			return {"xp": 0, "need": 0, "max": true}
		var from: int = Warband.XP_TO_LEVEL[level]
		var to: int = Warband.XP_TO_LEVEL[level + 1]
		return {"xp": xp - from, "need": to - from, "max": false}

	## A TFT-style "stage-round" label, e.g. round 7 → "2-2".
	func stageLabel() -> String:
		var stage := floori((round - 1) / 5.0) + 1
		var step := ((round - 1) % 5) + 1
		return "%d-%d" % [stage, step]

	func rollShop() -> void:
		var odds: Array = Warband.TIER_ODDS[shopLevel() - 1]
		shop = []
		for i in Warband.SHOP_SIZE:
			shop.append(rollFromPool(odds, rng))

	## Roll a shop unit from the shared pool: pick a tier by odds, then a type
	## weighted by how many copies remain (depleted types appear less).
	func rollFromPool(odds: Array, r: RNG):
		var roll := r.range(0.0, 100.0)
		var tier := 1
		for t in 5:
			if roll < odds[t]:
				tier = t + 1
				break
			roll -= odds[t]
		var avail: Array = []
		for type in C.TIER_UNITS[tier]:
			if pool.get(type, 0) > 0:
				avail.append(type)
		if avail.is_empty():
			return null
		var total := 0.0
		for type in avail:
			total += pool[type]
		var pick := r.range(0.0, total)
		for type in avail:
			pick -= pool[type]
			if pick < 0:
				return type
		return avail[avail.size() - 1]

	## Copies of a unit type left in the shared pool (for the shop UI / reads).
	func poolCount(type: String) -> int:
		return pool.get(type, 0)

	## Take a copy from the pool when acquired; refund (star-weighted) on sale.
	func takeFromPool(type: String) -> void:
		pool[type] = maxi(0, pool.get(type, 0) - 1)

	func refundToPool(type: String, star: int) -> void:
		pool[type] = pool.get(type, 0) + int(pow(3, star - 1))

	func reroll() -> bool:
		var cost_ := rerollCost()
		if phase != "shop" or gold < cost_:
			return false
		if freeRerolls > 0:
			freeRerolls -= 1
		else:
			gold -= cost_
			tally.goldSpent += cost_
		tally.rerolls += 1
		shopLocked = false # you asked for a new shop; the hold is spent
		rollShop()
		return true

	func buyXp() -> bool:
		if phase != "shop" or gold < Warband.XP_BUY or level >= Warband.MAX_LEVEL:
			return false
		gold -= Warband.XP_BUY
		tally.goldSpent += Warband.XP_BUY
		tally.levelUps += 1
		xp += Warband.XP_BUY
		while level < Warband.MAX_LEVEL and xp >= Warband.XP_TO_LEVEL[level + 1]:
			level += 1
		reconcile() # bigger board → pull up reserves
		return true

	func cost(type: String) -> int:
		return Warband._tier1(type)

	## Reserves currently on the bench (everything not deployed).
	func benchCount() -> int:
		var n := 0
		for p in pieces:
			if not p.deployed:
				n += 1
		return n

	## Bench capacity — fixed, like TFT's nine slots.
	func benchSlots() -> int:
		return Warband.BENCH_SLOTS

	## True when the bench is full and no room remains for a plain buy.
	func benchFull() -> bool:
		return benchCount() >= Warband.BENCH_SLOTS

	## Would buying this type immediately merge? Two 1★ copies already held means
	## the third combines rather than taking a slot — so a full bench must never
	## block the buy that would have freed it.
	func wouldMerge(type: String) -> bool:
		var n := 0
		for p in pieces:
			if p.type == type and p.star == 1:
				n += 1
		return n >= 2

	## Whether a shop slot can be bought right now, and why not if it can't:
	## {ok, reason?: "gold" | "bench"}.
	func canBuy(slot: int) -> Dictionary:
		if phase != "shop":
			return {"ok": false}
		var type = shop[slot] if slot >= 0 and slot < shop.size() else null
		if type == null:
			return {"ok": false}
		if gold < cost(type):
			return {"ok": false, "reason": "gold"}
		# A full board *and* a full bench leaves nowhere to put it.
		if benchFull() and deployedCount() >= boardCap() and not wouldMerge(type):
			return {"ok": false, "reason": "bench"}
		return {"ok": true}

	func buy(slot: int) -> bool:
		if phase != "shop":
			return false
		var type = shop[slot] if slot >= 0 and slot < shop.size() else null
		if type == null:
			return false
		var c := cost(type)
		if gold < c:
			return false
		if not canBuy(slot).ok:
			return false
		gold -= c
		tally.goldSpent += c
		tally.unitsBought += 1
		shop[slot] = null
		takeFromPool(type) # claim the copy from the shared pool
		pieces.append(Warband.Piece.new(type, 1, []))
		merge()
		reconcile() # auto-field it if the board has a free slot
		return true

	## Sell a piece back for (tier × star) gold.
	func sell(index: int) -> bool:
		if phase != "shop" or index < 0 or index >= pieces.size():
			return false
		var p: Warband.Piece = pieces[index]
		# Normally a flat star price; the Quartermaster refunds every copy sunk in.
		var refund: int = cost(p.type) * int(pow(3, p.star - 1)) if _cmd("fullRefund", false) else cost(p.type) * p.star
		gold += refund
		tally.goldEarned += refund
		tally.unitsSold += 1
		refundToPool(p.type, p.star) # its copies return to the shared pool
		pieces.remove_at(index)
		reconcile() # selling a board unit pulls up a reserve
		return true

	## Combine any 3 same type+star into one of the next star.
	func merge() -> void:
		var made := Warband.mergeBoard(pieces, itemStash)
		if made.size() > 0:
			lastMerge = made[made.size() - 1] # the screen clears it after celebrating

	## Equip a stashed component or relic onto a piece (max 3 per unit). Landing a
	## second loose component on the same unit fuses the pair into its full relic
	## and frees the slot — that's how carries get built.
	func equipItem(stashIndex: int, pieceIndex: int) -> bool:
		if phase != "shop":
			return false
		if stashIndex < 0 or stashIndex >= itemStash.size():
			return false
		if pieceIndex < 0 or pieceIndex >= pieces.size():
			return false
		var p: Warband.Piece = pieces[pieceIndex]
		if p.items.size() >= int(C.MAX_ITEMS):
			return false
		p.items.append(itemStash[stashIndex])
		itemStash.remove_at(stashIndex)
		var made = Items.fuseComponents(p.items)
		if made != null:
			lastFusion = {"item": made, "type": p.type}
		return true

	## Sort key: strongest pieces first (star, then tier, then relics).
	func stronger(a: int, b: int) -> int:
		var d: int = pieces[b].star - pieces[a].star
		if d != 0:
			return d
		d = Warband._tier(pieces[b].type) - Warband._tier(pieces[a].type)
		if d != 0:
			return d
		return pieces[b].items.size() - pieces[a].items.size()

	## Indices of the pieces currently on the board, strongest first.
	func deployedIndices() -> Array:
		var out: Array = []
		for i in pieces.size():
			if pieces[i].deployed:
				out.append(i)
		JS.sort_by_cmp(out, stronger)
		return out

	## Keep the board legal & full: trim past the cap (weakest benched), auto-fill
	## empty slots with your strongest reserves, and give every deployed piece a
	## cell. Manual placements stay put — auto-fill only ever fills empty slots, so
	## benching a unit by swapping a reserve in is never undone.
	func reconcile() -> void:
		var cap := boardCap()
		var deployed: Array = []
		for i in pieces.size():
			if pieces[i].deployed:
				deployed.append(i)
		# Over cap (e.g. after a level is somehow lower): bench the weakest.
		if deployed.size() > cap:
			JS.sort_by_cmp(deployed, func(a, b): return stronger(b, a)) # weakest first
			for i in deployed.slice(0, deployed.size() - cap):
				var p: Warband.Piece = pieces[i]
				p.deployed = false
				p.col = null
				p.row = null
		# Auto-fill empty slots with the strongest reserves.
		var count := 0
		for p in pieces:
			if p.deployed:
				count += 1
		if count < cap:
			var bench: Array = []
			for i in pieces.size():
				if not pieces[i].deployed:
					bench.append(i)
			JS.sort_by_cmp(bench, stronger)
			for i in bench:
				if count >= cap:
					break
				pieces[i].deployed = true
				count += 1
		# A new round's terrain can land on top of last round's placement.
		for p in pieces:
			if p.deployed and p.col != null and p.row != null and Terrain.isBlocked(terrain, p.col, p.row):
				p.col = null
				p.row = null
		# Assign a cell to every deployed piece that lacks one (front rank first).
		var used := {}
		for p in pieces:
			if p.deployed and p.col != null and p.row != null:
				used[p.col * 10 + p.row] = true
		var cells := Warband.PLAYER_CELLS()
		var ai := 0
		for i in deployedIndices():
			var p: Warband.Piece = pieces[i]
			if p.col == null or p.row == null:
				while ai < cells.size() and (used.has(cells[ai].x * 10 + cells[ai].y) or Terrain.isBlocked(terrain, cells[ai].x, cells[ai].y)):
					ai += 1
				var cell: Vector2i = cells[mini(ai, cells.size() - 1)]
				p.col = cell.x
				p.row = cell.y
				used[cell.x * 10 + cell.y] = true
				ai += 1

	## Deployed units (with relics + board cell) for the battle resolver.
	func boardUnits() -> Array:
		reconcile()
		var out: Array = []
		for i in deployedIndices():
			var p: Warband.Piece = pieces[i]
			out.append({"type": p.type, "star": p.star, "items": p.items, "col": p.col, "row": p.row})
		return out

	## The deployed pieces with their board cells (for the placement UI):
	## [{index, type, star, col, row}].
	func deployment() -> Array:
		reconcile()
		var out: Array = []
		for i in deployedIndices():
			var p: Warband.Piece = pieces[i]
			out.append({"index": i, "type": p.type, "star": p.star, "col": p.col, "row": p.row})
		return out

	## The piece a newly-fielded unit would push off a full board — the weakest
	## thing you have deployed. What a shop unit is really competing with.
	func weakestDeployed():
		var idx: Array = deployedIndices() # strongest-first, so last is weakest
		return null if idx.is_empty() else pieces[idx[idx.size() - 1]]

	## How many pieces are on the board right now (≤ deployCount()).
	func deployedCount() -> int:
		var n := 0
		for p in pieces:
			if p.deployed:
				n += 1
		return n

	## Move/field a piece onto a board cell on your half (cols 0..4, rows 0..9).
	## A deployed piece repositions (swapping cells with any occupant). A bench
	## piece is fielded: it swaps in for the occupant, fills a free slot, or — if
	## the board is already full — bumps your weakest deployed unit to the bench.
	func place(index: int, col: int, row: int) -> bool:
		if phase != "shop":
			return false
		if col < 0 or col > 4 or row < 0 or row > 9:
			return false
		if Terrain.isBlocked(terrain, col, row):
			return false # solid rock
		if index < 0 or index >= pieces.size():
			return false
		var p: Warband.Piece = pieces[index]
		var occ: Warband.Piece = null
		for qi in pieces.size():
			var q: Warband.Piece = pieces[qi]
			if qi != index and q.deployed and q.col == col and q.row == row:
				occ = q
				break
		if p.deployed:
			if occ != null: # swap cells
				occ.col = p.col
				occ.row = p.row
			p.col = col
			p.row = row
			return true
		# Fielding a bench unit.
		if occ != null:
			occ.deployed = false
			occ.col = null
			occ.row = null
		elif deployedCount() >= boardCap():
			var di := deployedIndices() # strongest-first → last is weakest
			var weakest = null if di.is_empty() else di[di.size() - 1]
			if weakest == null or weakest == index:
				return false
			var w: Warband.Piece = pieces[weakest]
			w.deployed = false
			w.col = null
			w.row = null
		p.deployed = true
		p.col = col
		p.row = row
		return true

	## The units that actually deploy this round (strongest `level` pieces), as
	## UnitStacks.
	func boardStacks() -> Array:
		var deployed := pieces.duplicate()
		JS.sort_by_cmp(deployed, func(a, b):
			return b.star - a.star if b.star != a.star else Warband._tier(b.type) - Warband._tier(a.type))
		deployed = deployed.slice(0, boardCap())
		var byKey := {}
		for p in deployed:
			var k := "%s:%d" % [p.type, p.star]
			if byKey.has(k):
				byKey[k].count += 1
			else:
				byKey[k] = {"type": p.type, "count": 1, "star": p.star}
		return byKey.values()

	# ---- round / combat -------------------------------------------------------
	func startRound() -> void:
		round += 1
		# Income: base + interest (1 per 10 gold, capped) + win/loss-streak bonus,
		# plus whatever the run's augments add.
		var interest := mini(interestCap(), floori(gold / 10.0))
		var streakG := Warband.streakBonus(streak)
		var income := 5 + interest + streakG + int(augSum("gold")) + int(_cmd("gold"))
		gold += income
		tally.goldEarned += income
		freeRerolls = int(_cmd("freeRerolls")) # the perk refreshes every round
		# Augment-granted free XP (a levelling engine).
		var freeXp := int(augSum("xp"))
		if freeXp > 0:
			xp += freeXp
			while level < Warband.MAX_LEVEL and xp >= Warband.XP_TO_LEVEL[level + 1]:
				level += 1
		# A relic every few rounds — equip it to build a carry.
		if round % relicEvery() == 2 % relicEvery():
			grantRelic()
		# A locked shop survives the round, then releases — you hold it for one
		# roll, not forever.
		if shopLocked:
			shopLocked = false
		else:
			rollShop()
		# Every living opponent runs its economy for the round (same rules as you).
		for o in opponents:
			if o.alive:
				stepFoe(foeBrains[o.id])
		# Lock in this round's matchup + battle seed now, so the board preview and
		# the live fight face the same warband (or camp).
		pendingSeed = rng.int_(1, 1000000000)
		condition = Conditions.rollCondition(rng, round)
		terrainSeed = rng.int_(1, 1000000000)
		terrain = Terrain.rollTerrain(terrainSeed, round)
		if bossPending:
			# The lobby is empty: the arena's own champions ride out. This is the
			# run's last round either way, so nothing else about it is rolled.
			bossPending = false
			bossRound = true
			pendingCamp = Creeps.bossCamp(round, difficulty.damageTaken)
			pendingFoe = null
			pendingOpp = Creeps.campBoard(pendingCamp)
			reconcile()
			phase = "shop"
			return
		if Creeps.isCreepRound(round):
			# A PvE monster camp: no player life at stake, relics on the table.
			pendingCamp = Creeps.campForRound(round)
			pendingFoe = null
			pendingOpp = Creeps.campBoard(pendingCamp)
		else:
			pendingCamp = null
			var foes := livingFoes()
			pendingFoe = foes[round % foes.size()] if foes.size() > 0 else null
			pendingOpp = foeBoard(foeBrains[pendingFoe.id]) if pendingFoe != null else []
		reconcile() # a levelled board pulls up reserves before you see it
		# An augment round pauses everything for the pick; so does a draft round.
		var tier = Augments.tierForRound(round)
		if tier != null and augmentOffer.size() == 0:
			var owned: Array = []
			for a in augments:
				owned.append(a.id)
			augmentOffer = Augments.offerAugments(rng, tier, owned)
			phase = "augment"
		elif Carousel.isDraftRound(round):
			openDraft()
		else:
			phase = "shop"

	## Open the carousel. Everyone weaker than you takes their pick first — the
	## greedy best each time — so a leading warband chooses from the remainder.
	func openDraft() -> void:
		var ring := Carousel.rollCarousel(rng, round)
		var weaker: Array = livingFoes().filter(func(o): return o.life < life)
		draftAhead = mini(weaker.size(), maxi(0, ring.size() - 1)) # always leave one choice
		# The warbands ahead of you take the strongest options. (Sorted as
		# indices into the ring so "taken" is by identity, as the TS Set is.)
		var ordered: Array = range(ring.size())
		JS.sort_by_cmp(ordered, func(a, b): return Carousel.pickValue(ring[b]) - Carousel.pickValue(ring[a]))
		var taken := {}
		for i in draftAhead:
			var pick: Dictionary = ring[ordered[i]]
			taken[ordered[i]] = true
			# Whoever took it actually gets it, so the lobby scales off the draft too.
			var foe = weaker[i] if i < weaker.size() else null
			if foe != null:
				var b: Warband.FoeBrain = foeBrains[foe.id]
				takeFromPool(pick.type)
				b.pieces.append(Warband.Piece.new(pick.type, pick.star, [pick.item]))
				Warband.mergeBoard(b.pieces, [])
				equipFoeSpare(b)
		carousel = []
		for i in ring.size():
			if not taken.has(i):
				carousel.append(ring[i])
		phase = "draft"

	## Fuse any loose pair an opponent is now holding after a draft pick.
	func equipFoeSpare(b: Warband.FoeBrain) -> void:
		for p in b.pieces:
			Items.fuseComponents(p.items)

	## Take one of the units left on the carousel. It joins your warband with its
	## component already equipped, then the rest of the field clears the ring.
	func takeCarousel(index: int) -> bool:
		if phase != "draft":
			return false
		if index < 0 or index >= carousel.size():
			return false
		var pick: Dictionary = carousel[index]
		takeFromPool(pick.type)
		pieces.append(Warband.Piece.new(pick.type, pick.star, [pick.item]))
		merge()
		for p in pieces:
			if Items.fuseComponents(p.items) != null:
				lastFusion = {"item": p.items[p.items.size() - 1], "type": p.type}
		reconcile()
		# Everyone stronger than you sweeps up what's left.
		var rest: Array = []
		for i in carousel.size():
			if i != index:
				rest.append(carousel[i])
		var strongerFoes: Array = livingFoes().filter(func(o): return o.life >= life)
		JS.sort_by_cmp(rest, func(a, b): return Carousel.pickValue(b) - Carousel.pickValue(a))
		var picks := strongerFoes.slice(0, rest.size())
		for i in picks.size():
			var b: Warband.FoeBrain = foeBrains[picks[i].id]
			takeFromPool(rest[i].type)
			b.pieces.append(Warband.Piece.new(rest[i].type, rest[i].star, [rest[i].item]))
			Warband.mergeBoard(b.pieces, [])
			equipFoeSpare(b)
		carousel = []
		draftAhead = 0
		phase = "shop"
		return true

	## True when the carousel is waiting on your pick.
	func isDraftRound() -> bool:
		return Carousel.isDraftRound(round)

	## Add a random component to the stash — two on one unit fuse into a relic.
	func grantRelic() -> void:
		itemStash.append(C.COMPONENT_IDS[rng.int_(0, C.COMPONENT_IDS.size() - 1)])

	## Take one of the three offered augments. Its one-off effects (bounty, life,
	## relics) land immediately; the per-round and combat effects are read live.
	func pickAugment(index: int) -> bool:
		if phase != "augment":
			return false
		if index < 0 or index >= augmentOffer.size():
			return false
		var a: Dictionary = augmentOffer[index]
		augments.append(a)
		augmentOffer = []
		if a.get("bounty"):
			gold += int(a.bounty)
		if a.get("life"):
			life = mini(100, life + int(a.life))
		for i in int(a.get("relics", 0)):
			grantRelic()
		reconcile() # +board slots take effect at once
		phase = "shop"
		return true

	## Name of the warband (or camp) you'll face this round.
	func pendingFoeName() -> String:
		if pendingCamp != null:
			return pendingCamp.name
		if pendingFoe != null:
			return pendingFoe.name
		return "—"

	## True when this round is a PvE monster camp rather than a player.
	func isCreepRound() -> bool:
		return pendingCamp != null

	## Warband-wide augment modifiers, for the battle resolver.
	func sideOpts() -> Dictionary:
		var buff = warbandBuff()
		return {"buff": buff, "traitBonus": traitBonusMap()}

	## The worst a defeat this round could cost: the stage toll plus every enemy
	## unit surviving. Shown before you commit, so the fight is an informed bet.
	func worstCaseDamage() -> int:
		var power := 0
		for u in pendingOpp:
			power += int(u.star) if u.get("star") != null else 1
		return maxi(1, Warband.roundDamage(round, power) - int(_cmd("lossShield")))

	## Gold your current streak pays at the start of next round.
	func streakGold() -> int:
		return Warband.streakBonus(streak)

	## This round's battlefield modifiers — applied identically to both sides.
	func fieldOpts() -> Dictionary:
		return {"buff": condition.get("all"), "traitBuffs": condition.get("traits"), "terrain": terrain}

	## Every run-wide buff (augments + commander) merged into one.
	func warbandBuff():
		var aug = Augments.combinedBuff(augments)
		var cmd = _cmd("buff", null)
		if aug == null:
			return cmd
		if cmd == null:
			return aug
		var out: Dictionary = aug.duplicate()
		for k in Traits.BUFF_KEYS:
			if cmd.get(k):
				out[k] = float(out.get(k, 0.0)) + cmd[k]
		return out

	## Synergy count bonuses: augment banners, plus the Banneret's perk, which
	## finds whichever synergy is currently largest and lifts that one. Resolved
	## live, so it follows your comp as it changes.
	func traitBonusMap():
		var base = Augments.combinedTraitBonus(augments)
		if base == null:
			base = {}
		var top := float(_cmd("topTraitBonus"))
		if not top:
			return base if base.size() > 0 else null
		var out: Dictionary = base.duplicate()
		var types: Array = []
		for u in boardUnits():
			if not types.has(u.type):
				types.append(u.type)
		var best = null
		for at in Traits.activeTraits(types, base):
			if best == null or at.count > best.count:
				best = at
		if best != null:
			out[best.trait.id] = float(out.get(best.trait.id, 0.0)) + top
		return out if out.size() > 0 else null

	## Advance one opponent's economy a round, by the same rules the player
	## plays: identical income + interest, the same shop odds, level/buy/merge —
	## but as a *competent* drafter. It banks gold for interest (the real economy
	## engine), levels toward a curve, concentrates buys into its favoured comp so
	## it hits 2★/3★ star-ups and synergies, rerolls to dig for copies, and equips
	## the relics it earns onto its carry. That lets it scale roughly as fast as
	## you.
	func stepFoe(b: Warband.FoeBrain) -> void:
		var d := difficulty
		# Base + interest, the same formula you run on, scaled by how hard the lobby plays.
		b.gold += int(JS.round((5 + mini(5, floori(b.gold / 10.0))) * float(d.foeIncome)))
		if round % int(d.foeRelicEvery) == 2:
			equipFoeRelic(b) # a relic every few rounds, like you
		# Personality shapes the three levers every warband pulls: how hard it banks
		# for interest, how fast it techs, and how much it rolls for star-ups.
		var play := b.play
		var levelPace := (0.95 if play == "aggressor" else (0.68 if play == "roller" else 0.8)) * float(d.foeLevelPace)
		var targetLevel := mini(Warband.MAX_LEVEL, 1 + floori(round * levelPace))
		# Banking is right early and wrong late. Past the mid-game a warband that
		# is still sitting on 50 gold is a warband fielding a board it could have
		# upgraded, so the reserve collapses and they spend down.
		var late := round > 11
		var reserveCap := 8 if late else (50 if play == "economist" else (20 if play == "roller" else 30))
		var reserve := mini(reserveCap, maxi(0, (round - 2) * 8))
		var rerolls := maxi(0, (5 if play == "roller" else 2) + int(d.foeRerolls)) + floori(round / 2.0) + (6 if late else 0)
		var guard := 0
		while guard < 160:
			guard += 1
			# Tech toward the curve when it can afford to and stay above reserve.
			if b.level < targetLevel and b.gold - Warband.XP_BUY >= reserve:
				b.gold -= Warband.XP_BUY
				b.xp += Warband.XP_BUY
				while b.level < Warband.MAX_LEVEL and b.xp >= Warband.XP_TO_LEVEL[b.level + 1]:
					b.level += 1
				continue
			var odds: Array = Warband.TIER_ODDS[mini(b.level, Warband.MAX_LEVEL) - 1]
			var needBodies := b.pieces.size() < b.level + 1 # fill a board first
			var bought := false
			for s in Warband.SHOP_SIZE:
				var type = rollFromPool(odds, b.rng) # draws from the shared lobby pool
				if type == null:
					continue
				var c := int(C.UNIT_TIER[type])
				if b.gold - c < reserve:
					continue
				if buyValue(b, type) <= 0 and not needBodies:
					continue
				b.gold -= c
				takeFromPool(type)
				b.pieces.append(Warband.Piece.new(type, 1, []))
				Warband.mergeBoard(b.pieces, [])
				bought = true
			if not bought:
				if rerolls > 0 and b.gold - Warband.REROLL_COST >= reserve:
					b.gold -= Warband.REROLL_COST
					rerolls -= 1
					continue
				break # nothing worth buying and out of rerolls → bank the rest for interest
		trimFoeBench(b)

	## How much a warband wants a given unit right now. This is what turns the
	## opponents from "buy anything on the list" into drafters: completing a
	## merge is worth the most, then advancing a synergy toward its next
	## breakpoint, then sticking to the comp identity they opened with.
	func buyValue(b: Warband.FoeBrain, type: String) -> int:
		var value := 0
		var copies := 0
		for p in b.pieces:
			if p.type == type and p.star < 3:
				copies += 1
		if copies >= 2:
			value += 10 # this buy stars something up
		elif copies == 1:
			value += 4 # halfway to a star-up
		if b.favored.has(type):
			value += 3
		# Does it push a synergy over its next breakpoint?
		var owned: Array = []
		for p in b.pieces:
			if not owned.has(p.type):
				owned.append(p.type)
		if not owned.has(type):
			var before := Traits.activeTraits(owned)
			var withType := owned.duplicate()
			withType.append(type)
			var after := Traits.activeTraits(withType)
			for at in after:
				var was = null
				for x in before:
					if x.trait.id == at.trait.id:
						was = x
						break
				if was == null:
					value += 5 # switched a synergy on
				elif at.tierIndex > was.tierIndex:
					value += 6 # pushed it up a tier
		# Late on, a scarce high tier is worth taking on principle.
		if round > 8 and Warband._tier1(type) >= 4:
			value += 2
		return value

	## Sell what a warband will never field. Without this an opponent hoards
	## 1★ chaff it drafted early and never converts it back into gold.
	func trimFoeBench(b: Warband.FoeBrain) -> void:
		if round < 6:
			return # early on, everything is still a merge in progress
		var keep := b.level + 4 # its board plus a working bench
		if b.pieces.size() <= keep:
			return
		var ranked := b.pieces.duplicate()
		JS.sort_by_cmp(ranked, func(p, q):
			if q.star != p.star:
				return q.star - p.star
			var dt := Warband._tier(q.type) - Warband._tier(p.type)
			if dt != 0:
				return dt
			return q.items.size() - p.items.size())
		for dead in ranked.slice(keep):
			# Never sell a copy that is one buy away from a star-up — that was the
			# difference between trimming chaff and dismantling your own board.
			var sameKind := 0
			for p in b.pieces:
				if p.type == dead.type and p.star == dead.star:
					sameKind += 1
			if sameKind >= 2:
				continue
			if dead.items.size() > 0:
				continue # nor anything carrying relics
			var i := b.pieces.find(dead)
			if i < 0:
				continue
			b.pieces.remove_at(i)
			b.gold += Warband._tier1(dead.type) * dead.star
			refundToPool(dead.type, dead.star) # its copies go back to the lobby

	## Pieces strongest first (star, then tier) — a sorted copy.
	static func _byStrength(src: Array) -> Array:
		var out := src.duplicate()
		JS.sort_by_cmp(out, func(p, q):
			return q.star - p.star if q.star != p.star else Warband._tier(q.type) - Warband._tier(p.type))
		return out

	## Earn + equip a component onto an opponent's strongest unit, fusing pairs
	## into full relics the same way you do — so foes build carries too.
	func equipFoeRelic(b: Warband.FoeBrain) -> void:
		var id: String = C.COMPONENT_IDS[b.rng.int_(0, C.COMPONENT_IDS.size() - 1)]
		var target = null
		for p in _byStrength(b.pieces):
			if p.items.size() < int(C.MAX_ITEMS):
				target = p
				break
		if target != null:
			target.items.append(id)
			Items.fuseComponents(target.items)

	## An opponent's deployed warband — its strongest pieces (with relics) on its half.
	func foeBoard(b: Warband.FoeBrain) -> Array:
		var cap := b.level # same rule the player plays by
		var fielded := _byStrength(b.pieces).slice(0, cap)
		return Warband.placeByStyle(fielded, 1, terrain)

	func livingFoes() -> Array:
		return opponents.filter(func(o): return o.alive)

	## Resolve this round's fight instantly (headless — tests, AI, auto-play).
	func fight() -> void:
		if phase != "shop":
			return
		if pendingFoe == null and pendingCamp == null:
			outcome = "win"
			phase = "over"
			return
		var units := boardUnits()
		var opts := sideOpts()
		applyOutcome(Autobattle.resolveBattle(units, pendingOpp, pendingSeed, 40.0, opts, fieldOpts()))

	## Begin the watchable version of the fight (the screen renders + steps it).
	func beginFight() -> bool:
		if phase != "shop":
			return false
		if pendingFoe == null and pendingCamp == null:
			outcome = "win"
			phase = "over"
			return false
		phase = "battle"
		return true

	## Hand back the live battle's verdict to apply life damage + eliminations.
	func finishFight(res: Dictionary) -> void:
		if phase != "battle":
			return
		applyOutcome(res)

	## Apply a battle result: damage, streak, off-screen thinning, win/lose check.
	func applyOutcome(res: Dictionary) -> void:
		# A PvE camp round: loot on a win, a small bite on a loss, no streak and no
		# player eliminated either way.
		var camp = pendingCamp
		if camp != null:
			var wonCamp: bool = res.winner == "A"
			var relics := 0
			if wonCamp:
				relics = int(camp.relics)
				for i in relics:
					grantRelic()
				gold += int(camp.gold)
			else:
				life -= int(camp.bite)
			thinTheHerd()
			lastResult = {
				"won": wonCamp, "foe": camp.name, "youLeft": res.survivorsA, "foeLeft": res.survivorsB,
				"dmg": 0 if wonCamp else int(camp.bite), "relics": relics, "gold": int(camp.gold) if wonCamp else 0, "creep": true,
			}
			if wonCamp:
				tally.campsCleared += 1
				tally.relicsWon += relics
				tally.goldEarned += int(camp.gold)
			else:
				tally.campsLost += 1
				tally.lifeLost += int(camp.bite)
			recordRound()
			# The final challenge ends the run either way — there is no second run at
			# the crown, so it can't loop, and outlasting the lobby is still a win
			# even if the champions put you down.
			if bossRound:
				bossCleared = wonCamp
				if life <= 0:
					life = 0
					outcome = "loss"
				else:
					outcome = "win"
				phase = "over"
				return
			if life <= 0:
				life = 0
				outcome = "loss"
				phase = "over"
				return
			if livingFoes().size() == 0:
				bossPending = true
				phase = "result"
				return
			phase = "result"
			return
		var foe = pendingFoe
		# No opponent and no camp means the lobby emptied off-screen; go to the crown.
		if foe == null:
			bossPending = true
			phase = "result"
			return
		var won: bool = res.winner == "A"
		# Stage toll + star-weighted survivors; a commander can blunt what you take.
		var raw := int(JS.round(Warband.roundDamage(round, res.powerB) * float(difficulty.damageTaken)))
		var dmg := 0 if won else maxi(1, raw - int(_cmd("lossShield")))
		if won:
			foe.life -= Warband.roundDamage(round, res.powerA)
			if foe.life <= 0:
				foe.life = 0
				foe.alive = false
			streak = maxi(1, streak + 1)
		else:
			life -= dmg
			streak = mini(-1, streak - 1)
		# The other living foes skirmish among themselves so the lobby thins out too.
		thinTheHerd()
		lastResult = {"won": won, "foe": foe.name, "youLeft": res.survivorsA, "foeLeft": res.survivorsB, "dmg": dmg, "relics": 0, "gold": 0, "creep": false}
		if won:
			tally.wins += 1
		else:
			tally.losses += 1
			tally.lifeLost += dmg
		tally.bestStreak = maxi(tally.bestStreak, streak)
		tally.worstStreak = mini(tally.worstStreak, streak)
		recordRound()

		if life <= 0:
			life = 0
			outcome = "loss"
			phase = "over"
			return
		if livingFoes().size() == 0:
			bossPending = true
			phase = "result"
			return
		phase = "result"

	## Append the round that just resolved to the run's history.
	func recordRound() -> void:
		var r = lastResult
		if r == null:
			return
		history.append({
			"round": round, "stage": stageLabel(), "foe": r.foe, "won": r.won, "creep": r.creep,
			"dmg": r.dmg, "youLeft": r.youLeft, "foeLeft": r.foeLeft, "life": maxi(0, life),
		})

	## Off-screen attrition: the rest of the lobby fights its own round.
	func thinTheHerd() -> void:
		# The rest of the lobby fights its own round. This used to be random
		# attrition, which meant rivals died to dice rather than to bad drafting —
		# and a player who simply survived inherited the win. Now the warbands that
		# last are the ones that actually built the better board, so the field you
		# face late is the strong half of it.
		var others: Array = livingFoes().filter(func(o): return o != pendingFoe)
		var i := others.size() - 1
		while i > 0: # deterministic shuffle
			var j := rng.int_(0, i)
			var tmp = others[i]
			others[i] = others[j]
			others[j] = tmp
			i -= 1
		i = 0
		while i + 1 < others.size():
			var a: Warband.Opponent = others[i]
			var b: Warband.Opponent = others[i + 1]
			i += 2
			var sa := foeStrength(foeBrains[a.id])
			var sb := foeStrength(foeBrains[b.id])
			if sa == sb:
				continue # a true stalemate costs neither of them
			var winner := a if sa > sb else b
			var loser := b if sa > sb else a
			var hi := maxf(sa, sb)
			var lo := minf(sa, sb)
			# Survivors scale with how lopsided it was, so a narrow win barely stings.
			var power := maxi(1, int(JS.round(foeBrains[winner.id].level * (1.0 - lo / hi))))
			loser.life -= Warband.roundDamage(round, power)
			if loser.life <= 0:
				# The last warband standing has to be beaten in the arena, not
				# eliminated off-screen — otherwise the lobby can empty on the same
				# round you die, leaving nobody alive at all.
				if livingFoes().size() <= 1:
					loser.life = 1
					continue
				loser.life = 0
				loser.alive = false

	## How hard a warband hits, for the fights we resolve off-screen: star-weighted
	## tier, plus relics, plus whatever its synergies are worth. Cheap on purpose
	## — simulating three extra battles a round would stall the frame the result
	## lands on.
	func foeStrength(b: Warband.FoeBrain) -> float:
		var board := _byStrength(b.pieces).slice(0, b.level)
		var total := 0.0
		var types: Array = []
		for p in board:
			total += float(Warband._tier1(p.type)) * (Warband.STAR_POWER[p.star] if p.star < Warband.STAR_POWER.size() else 1.0)
			total += p.items.size() * 1.5
			if not types.has(p.type):
				types.append(p.type)
		for at in Traits.activeTraits(types):
			if at.tier != null:
				total += 2 * (at.tierIndex + 1)
		return total

	## Advance from the result screen into the next shop phase.
	func next() -> void:
		if phase == "result":
			startRound()

	## Standings: every contestant (you + foes) by life, for the sidebar:
	## [{name, life, alive, you, id}].
	func standings() -> Array:
		var all: Array = [{"name": "You", "life": life, "alive": life > 0, "you": true, "id": -1}]
		for o in opponents:
			all.append({"name": o.name, "life": o.life, "alive": o.alive, "you": false, "id": o.id})
		JS.sort_by_cmp(all, func(a, b):
			var d := int(b.alive) - int(a.alive)
			return d if d != 0 else b.life - a.life)
		return all

	## Active synergies on your currently-deployed warband (for the screen).
	func activeTraits() -> Array:
		var types: Array = []
		for u in boardUnits():
			if not types.has(u.type):
				types.append(u.type)
		return Traits.activeTraits(types, traitBonusMap())

	## Every synergy your board touches at all, active or not, with how many
	## distinct types it has and what the next threshold is. "Vanguard 2/3" is
	## the single most useful thing a board can tell you while you shop, and it
	## was invisible while only *active* traits were listed.
	## [{trait, count, tier (or null), next (or null)}].
	func traitProgress() -> Array:
		var set := {}
		for u in boardUnits():
			set[u.type] = true
		var bonus = traitBonusMap()
		var out: Array = []
		for tr in C.TRAITS:
			var count := 0.0
			for t in tr.types:
				if set.has(t):
					count += 1.0
			if bonus != null:
				count += float(bonus.get(tr.id, 0.0))
			if count <= 0:
				continue
			var tier = null
			for t in tr.tiers:
				if count >= t.at:
					tier = t
			var next_ = null
			for t in tr.tiers:
				if t.at > count:
					next_ = t.at
					break
			out.append({"trait": tr, "count": count, "tier": tier, "next": next_})
		# Active first, then whoever is closest to switching on.
		JS.sort_by_cmp(out, func(a, b):
			var d := int(b.tier != null) - int(a.tier != null)
			if d != 0:
				return d
			var an: float = a.next if a.next != null else 0.0
			var bn: float = b.next if b.next != null else 0.0
			var e: float = an - a.count - (bn - b.count)
			if e != 0:
				return e
			return b.count - a.count)
		return out

	## Scout a rival: the warband they'd field right now, plus their level and
	## synergies. Lets you read the lobby and adapt — you can see who's building
	## what before you commit your own gold.
	## {name, level, life, alive, board, traits} or null.
	func scout(id: int):
		if id < 0 or id >= opponents.size() or id >= foeBrains.size():
			return null
		var o: Warband.Opponent = opponents[id]
		var b: Warband.FoeBrain = foeBrains[id]
		var board := foeBoard(b)
		var types: Array = []
		for u in board:
			if not types.has(u.type):
				types.append(u.type)
		return {
			"name": o.name, "level": b.level, "life": o.life, "alive": o.alive, "board": board,
			"traits": Traits.activeTraits(types),
		}

	## Placement (1 = winner) once the run is over.
	func placement() -> int:
		return 1 + livingFoes().size()
