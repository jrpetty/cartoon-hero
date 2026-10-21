## Warband Cache: bought with Valor, rolls an UPGRADED tier of a boon. Everyone
## owns every boon at base (Common) rarity already, so the cache only ever grants
## the advanced rarities (Uncommon and up) — a stronger version of the same boon.
## Duplicates refund a little Valor so the grind never fully stalls. Kept separate
## from the unit-variant chests so the boon economy can be tuned independently.
##
## BoonRoll: { boonId, rarity, key, name, duplicate, refund (Valor refunded if duplicate) }.
class_name BoonCache

## Valor. (Also exported as C.BOON_CACHE_COST; a test keeps the two equal.)
const BOON_CACHE_COST := 200.0

const DUP_REFUND := [25.0, 50.0, 100.0, 220.0, 500.0, 1200.0]


static func boonKey(boonId: String, rarity: float) -> String:
	return "%s:%s" % [boonId, JS.num(rarity)]


static func parseBoonKey(key: String) -> Dictionary:
	var i := key.rfind(":")
	var r := _parseInt(key.substr(i + 1))
	return {"boonId": key.substr(0, i) if i >= 0 else key.substr(0, key.length() - 1), "rarity": 0.0 if is_nan(r) or r == 0.0 else r}


## parseInt(s, 10): leading whitespace, an optional sign, then as many digits
## as there are (NaN if none). GDScript's to_int() skips non-digits anywhere.
static func _parseInt(s: String) -> float:
	s = s.strip_edges(true, false)
	var i := 0
	var neg := false
	if i < s.length() and (s[i] == "+" or s[i] == "-"):
		neg = s[i] == "-"
		i += 1
	var start := i
	var n := 0.0
	while i < s.length() and s[i] >= "0" and s[i] <= "9":
		n = n * 10.0 + float(s.unicode_at(i) - 48)
		i += 1
	if i == start:
		return NAN
	return -n if neg else n


## Roll a Warband Cache. Pure given the RNG, so it's deterministic and testable.
## Rolls an advanced rarity (1..N) only — base Common is owned by everyone.
## `owned` is a set (Dictionary of boon key -> true).
static func rollBoonCache(owned: Dictionary, rng: RNG) -> Dictionary:
	# Drop the Common slot and re-weight across the advanced tiers.
	var rarity := rng.weightedIndex(C.RARITY_WEIGHTS.slice(1)) + 1
	var boonId: String = rng.pick(C.BOON_IDS)
	var key := boonKey(boonId, rarity)
	var duplicate := owned.has(key)
	var def = C.BOONS_BY_ID.get(boonId)
	return {
		"boonId": boonId,
		"rarity": rarity,
		"key": key,
		"name": def.name if def != null else boonId,
		"duplicate": duplicate,
		"refund": (DUP_REFUND[rarity] if rarity < DUP_REFUND.size() else 25.0) if duplicate else 0.0,
	}
