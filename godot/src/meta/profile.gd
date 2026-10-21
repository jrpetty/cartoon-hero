## Persistent player profile, stored in localStorage. Tracks XP, Renown, the
## owned variant collection, the equipped loadout and lifetime stats. All
## client-side — no backend, no real currency.
##
## ProfileData (a Dictionary, saved as JSON exactly as the TS build saves it):
##   name, totalXp, renown,
##   owned: variant keys,
##   loadout: unitId -> equipped rarity,
##   stats: { wins, losses, played, bestStreak, streak },
##   openedChests,
##   commanders: owned commander ids, commander: selected commander id,
##   commanderReveal: first-launch reveal — the just-granted commander to show, or "",
##   valor: combat-earned currency for boon caches,
##   boons: owned boon keys `boonId:rarity`,
##   equippedBoons: category -> equipped boonId ("" none),
##   boonOrder: which category unlocks at age slot 0/1/2,
##   achievements: achievement ids already paid out, ever,
##   challengesDone: `week:challengeId` for weekly challenges already paid out,
##   faction?: the faction last played, factionCommanders?: the commander last led with each,
##   unlockedFactions?: factions this player owns. Empty until the first-launch
##     choice: one is free, every other costs FACTION_PRICE renown.
class_name Profile
extends RefCounted

## What each faction after the free first one costs, in renown (the War Chest
## currency). (Also exported as C.FACTION_PRICE; a test keeps the two equal.)
const FACTION_PRICE := 1500.0

## Default age order for the battle plan (which category unlocks at which age).
const DEFAULT_BOON_ORDER := ["offensive", "defensive", "supportive"]

const STORAGE_KEY := "banner_and_blade_profile_v1"

var data: Dictionary
var ownedSet: Dictionary
var boonSet: Dictionary

var needsFirstFaction: bool:
	get = _get_needsFirstFaction

var level: float:
	get = _get_level


static func defaultProfile() -> Dictionary:
	var owned: Array = []
	var loadout: Dictionary = {}
	# Everyone starts owning every Common variant, equipped by default.
	for id in C.COLLECTIBLE_UNIT_IDS:
		owned.append(Catalog.variantKey(id, 0))
		loadout[id] = 0.0
	return {
		"name": "Commander",
		"totalXp": 0.0,
		"renown": 300.0,
		"owned": owned,
		"loadout": loadout,
		"stats": {"wins": 0.0, "losses": 0.0, "played": 0.0, "bestStreak": 0.0, "streak": 0.0},
		"openedChests": 0.0,
		"commanders": [],
		"commander": "",
		"commanderReveal": "",
		"valor": 150.0,
		# Everyone owns every boon at base (Common) rarity; higher tiers unlock.
		"boons": C.BOON_IDS.map(func(id): return BoonCache.boonKey(id, 0)),
		"equippedBoons": {"offensive": "", "defensive": "", "supportive": ""},
		"boonOrder": DEFAULT_BOON_ORDER.duplicate(),
		"achievements": [],
		"challengesDone": [],
	}


func _init(d = null) -> void:
	data = d if d != null else defaultProfile()
	ownedSet = _set_of(data.owned)
	boonSet = _set_of(data.boons if data.get("boons") != null else [])
	migrate()


## JS truthiness (an empty object or array is truthy, unlike in GDScript).
static func _truthy(v) -> bool:
	if v == null:
		return false
	if v is bool:
		return v
	if v is float or v is int:
		return v != 0 and not is_nan(float(v))
	if v is String:
		return v != ""
	return true


static func _set_of(arr) -> Dictionary:
	var s := {}
	if arr is Array:
		for k in arr:
			s[k] = true
	return s


## Ensure new content (units added later) still grants its Common variant.
func migrate() -> void:
	var changed := false
	for id in C.COLLECTIBLE_UNIT_IDS:
		var k := Catalog.variantKey(id, 0)
		if not ownedSet.has(k):
			ownedSet[k] = true
			data.owned.append(k)
			changed = true
		if not data.loadout.has(id):
			data.loadout[id] = 0.0
			changed = true
	# Older saves predate commanders; back-fill the fields.
	if not (data.get("commanders") is Array):
		data.commanders = []
		changed = true
	if not data.has("commander"):
		data.commander = ""
		changed = true
	if not data.has("commanderReveal"):
		data.commanderReveal = ""
		changed = true
	# First launch: grant a random starter commander ("your first chest").
	if data.commanders.size() == 0:
		var starter: String = C.COMMANDER_IDS[int(floor(JS.random() * C.COMMANDER_IDS.size()))]
		data.commanders.append(starter)
		data.commander = starter
		data.commanderReveal = starter
		changed = true
	if not data.commander or not data.commanders.has(data.commander):
		data.commander = data.commanders[0] if data.commanders.size() > 0 else ""
		changed = true
	# Boons / Valor — back-fill for older saves.
	if not (data.get("valor") is float or data.get("valor") is int):
		data.valor = 150.0
		changed = true
	# Added after launch: a profile saved before achievements existed has
	# neither list, and every screen that reads them would otherwise have to
	# guard for undefined.
	if not (data.get("achievements") is Array):
		data.achievements = []
		changed = true
	if not (data.get("challengesDone") is Array):
		data.challengesDone = []
		changed = true
	if not (data.get("boons") is Array):
		data.boons = []
		boonSet = {}
		changed = true
	# Grant every boon's base (Common) tier to everyone — only the higher
	# rarities are earned. Back-fills older saves and any boons added later.
	for id in C.BOON_IDS:
		var k := BoonCache.boonKey(id, 0)
		if not boonSet.has(k):
			boonSet[k] = true
			data.boons.append(k)
			changed = true
	if not _truthy(data.get("equippedBoons")):
		data.equippedBoons = {"offensive": "", "defensive": "", "supportive": ""}
		changed = true
	if not (data.get("boonOrder") is Array) or data.boonOrder.size() != 3:
		data.boonOrder = DEFAULT_BOON_ORDER.duplicate()
		changed = true
	if not (data.get("unlockedFactions") is Array):
		data.unlockedFactions = []
		changed = true
	if changed:
		save()


# --- Boons & Valor --------------------------------------------------------
func addValor(n: float) -> void:
	data.valor = maxf(0.0, JS.round(data.valor + n))


func spendValor(n: float) -> bool:
	if data.valor < n:
		return false
	data.valor -= n
	save()
	return true


func ownsBoon(key: String) -> bool:
	return boonSet.has(key)


func boonSetSnapshot() -> Dictionary:
	return boonSet.duplicate()


## Add a boon to the collection. Returns true if newly owned.
func grantBoon(key: String) -> bool:
	if boonSet.has(key):
		return false
	boonSet[key] = true
	data.boons.append(key)
	return true


## Highest owned rarity of a boon, or -1 if unowned.
func bestBoonRarity(boonId: String) -> int:
	for r in range(5, -1, -1):
		if boonSet.has(BoonCache.boonKey(boonId, r)):
			return r
	return -1


## The age slot (0=start, 1=2nd age, 2=3rd age) a category unlocks at.
func boonAgeFor(cat: String) -> int:
	var i: int = data.boonOrder.find(cat)
	return 0 if i < 0 else i


## Put `cat` at age slot `age`, swapping with whatever category was there
## (the order stays a permutation — one category per age).
func setBoonAge(cat: String, age: int) -> void:
	var order: Array = data.boonOrder
	var cur := order.find(cat)
	if cur < 0 or age < 0 or age >= order.size() or age == cur:
		return
	var tmp = order[cur]
	order[cur] = order[age]
	order[age] = tmp
	save()


## Equip an owned boon for its category (any category, any rarity owned).
func equipBoon(boonId: String, on: bool) -> void:
	var def = C.BOONS_BY_ID.get(boonId)
	if def == null:
		return
	if bestBoonRarity(boonId) < 0:
		return  # must own at least one tier
	data.equippedBoons[def.category] = boonId if on else ""
	save()


## The pre-match battle plan: each equipped boon + the age it unlocks at.
## [{ id, rarity, age }]
func equippedBoonPlan() -> Array:
	var out: Array = []
	for cat in C.BOON_CATEGORIES:
		var id = data.equippedBoons.get(cat)
		if not id:
			continue
		var r := bestBoonRarity(id)
		if r >= 0:
			out.append({"id": id, "rarity": r, "age": boonAgeFor(cat)})
	return out


# --- Factions -------------------------------------------------------------
# Choose the faction to play. Each faction remembers the commander you last
# led it with, so switching back brings your pairing back with it.

func _unlocked() -> Array:
	var u = data.get("unlockedFactions")
	return u if u is Array else []


## Owned factions, in the book's order.
func ownedFactions() -> Array:
	var own := _unlocked()
	return C.FACTION_IDS.filter(func(id): return own.has(id))


func ownsFaction(id: String) -> bool:
	return _unlocked().has(id)


## True until the player has picked their free first faction.
func _get_needsFirstFaction() -> bool:
	return _unlocked().size() == 0


## The free first pick. Only works once; after that factions are bought.
func chooseFirstFaction(id: String) -> bool:
	if not needsFirstFaction or not C.FACTIONS.has(id):
		return false
	data.unlockedFactions = [id]
	data.faction = id
	save()
	return true


## Buy a faction for FACTION_PRICE renown. False if owned, unknown or unaffordable.
func unlockFaction(id: String) -> bool:
	if not C.FACTIONS.has(id) or ownsFaction(id) or needsFirstFaction:
		return false
	if data.renown < FACTION_PRICE:
		return false
	data.renown -= FACTION_PRICE
	var next := _unlocked().duplicate()
	next.append(id)
	data.unlockedFactions = next
	save()
	return true


## The faction to play: the chosen one if owned, else the first owned (null if none).
func playableFaction():
	var f = data.get("faction")
	if f and ownsFaction(f):
		return f
	var own := ownedFactions()
	return own[0] if own.size() > 0 else null


func selectFaction(id: String) -> void:
	if not needsFirstFaction and not ownsFaction(id):
		return  # locked
	data.faction = id
	var fc = data.get("factionCommanders")
	var cmdr = fc.get(id) if fc is Dictionary else null
	if cmdr and ownsCommander(cmdr):
		data.commander = cmdr
	save()


## Remember `commander` as the one this faction is led by.
func pairCommander(faction: String, commander: String) -> void:
	if not _truthy(data.get("factionCommanders")):
		data.factionCommanders = {}
	data.factionCommanders[faction] = commander
	save()


# --- Commanders -----------------------------------------------------------
func ownsCommander(id: String) -> bool:
	return data.commanders.has(id)


func selectCommander(id: String) -> void:
	if ownsCommander(id):
		data.commander = id
		save()


## Recruit a random un-owned commander for Renown. Returns its id, or "".
func recruitCommander(cost: float) -> String:
	var locked: Array = C.COMMANDER_IDS.filter(func(id): return not ownsCommander(id))
	if locked.size() == 0 or not spendRenown(cost):
		return ""
	var id: String = locked[int(floor(JS.random() * locked.size()))]
	data.commanders.append(id)
	data.commanderReveal = id
	save()
	return id


func clearCommanderReveal() -> void:
	if data.commanderReveal:
		data.commanderReveal = ""
		save()


static func load() -> Profile:
	var raw = Storage.getItem(STORAGE_KEY)
	if raw:
		var d = LocalJSON.parse(raw)
		# Corrupt storage is ignored. (TS: a parsed non-object would throw in
		# the constructor and land in the same fallback.)
		if d is Dictionary and d.get("owned") is Array and d.get("loadout") is Dictionary:
			return Profile.new(d)
	return Profile.new()


func save() -> void:
	data.owned = ownedSet.keys()
	data.boons = boonSet.keys()
	Storage.setItem(STORAGE_KEY, LocalJSON.stringify(data))


func owns(key: String) -> bool:
	return ownedSet.has(key)


func ownedSetSnapshot() -> Dictionary:
	return ownedSet.duplicate()


## Add a variant to the collection. Returns true if newly owned.
func grant(key: String) -> bool:
	if ownedSet.has(key):
		return false
	ownedSet[key] = true
	data.owned.append(key)
	return true


## Highest owned rarity for a unit (for "auto-equip best").
func bestOwnedRarity(unitId: String) -> int:
	var best := 0
	for r in range(5, -1, -1):
		if ownedSet.has(Catalog.variantKey(unitId, r)):
			best = r
			break
	return best


func equip(unitId: String, rarity: int) -> void:
	if ownedSet.has(Catalog.variantKey(unitId, rarity)):
		data.loadout[unitId] = float(rarity)
		save()


func equipBestAll() -> void:
	for id in C.COLLECTIBLE_UNIT_IDS:
		data.loadout[id] = float(bestOwnedRarity(id))
	save()


## Loadout used in a match: unitId -> equipped rarity (0 if fair mode).
func matchLoadout(fairMode: bool) -> Dictionary:
	if fairMode:
		var flat := {}
		for id in C.COLLECTIBLE_UNIT_IDS:
			flat[id] = 0.0
		return flat
	return data.loadout.duplicate()


func addRenown(n: float) -> void:
	data.renown = maxf(0.0, JS.round(data.renown + n))


func spendRenown(n: float) -> bool:
	if data.renown < n:
		return false
	data.renown -= n
	save()
	return true


func _get_level() -> float:
	return Progression.levelFromXp(data.totalXp).level


func levelInfo() -> Dictionary:
	return Progression.levelFromXp(data.totalXp)


## Apply XP, return number of levels gained and total level-up Renown granted.
## { levelsGained, renownFromLevels, newLevel }
func addXp(xp: float) -> Dictionary:
	var before := level
	data.totalXp += maxf(0.0, JS.round(xp))
	var after := level
	var renownFromLevels := 0.0
	var l := before
	while l < after:
		renownFromLevels += Progression.levelUpRenown(l)
		l += 1.0
	addRenown(renownFromLevels)
	return {"levelsGained": after - before, "renownFromLevels": renownFromLevels, "newLevel": after}


## Bank whatever a finished match earned, and pay the Valor.
##
## Marks each award as taken before paying, so a double call — a re-render, a
## retry — cannot pay twice. Returns what was actually new, which is what the
## post-match screen announces.
func claimAwards(awards: Array) -> Array:
	var done := _set_of(data.achievements)
	var weekly := _set_of(data.challengesDone)
	var fresh: Array = []
	for a in awards:
		var s: Dictionary = weekly if a.weekly else done
		if s.has(a.id):
			continue
		s[a.id] = true
		fresh.append(a)
	if fresh.is_empty():
		return []
	data.achievements = done.keys()
	data.challengesDone = weekly.keys()
	addValor(fresh.reduce(func(n, a): return n + float(a.valor), 0.0))
	return fresh


## AwardState: { earned, challengesDone }.
func awardState() -> Dictionary:
	return {"earned": data.achievements, "challengesDone": data.challengesDone}


func recordResult(win: bool) -> void:
	var st: Dictionary = data.stats
	st.played += 1
	if win:
		st.wins += 1
		st.streak += 1
		st.bestStreak = maxf(st.bestStreak, st.streak)
	else:
		st.losses += 1
		st.streak = 0.0
