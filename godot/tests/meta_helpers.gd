## Shared bits for the meta tests (not a test file itself: the runner only
## picks up test_*.gd).
extends RefCounted


## A localStorage stand-in whose setItem can refuse, as a full browser store
## throws QuotaExceededError. `limit` is the longest value it accepts (-1 = no
## limit); `fail_next` refuses that many writes regardless of size.
class FakeStore:
	extends RefCounted
	var data := {}
	var limit := -1
	var fail_next := 0
	var writes := 0

	func getItem(k: String):
		return data.get(k)

	func setItem(k: String, v: String) -> bool:
		writes += 1
		if fail_next > 0:
			fail_next -= 1
			return false
		if limit >= 0 and v.length() > limit:
			return false
		data[k] = v
		return true

	func removeItem(k: String) -> void:
		data.erase(k)


## Deep equality the way the TS tests' toEqual reads it: numbers compare by
## value whether they are int or float (JSON gives floats), everything else
## structurally.
static func deep_eq(a, b) -> bool:
	if (a is int or a is float) and (b is int or b is float):
		return float(a) == float(b)
	if a is Array and b is Array:
		if a.size() != b.size():
			return false
		for i in a.size():
			if not deep_eq(a[i], b[i]):
				return false
		return true
	if a is Dictionary and b is Dictionary:
		if a.size() != b.size():
			return false
		for k in a:
			if not b.has(k) or not deep_eq(a[k], b[k]):
				return false
		return true
	return typeof(a) == typeof(b) and a == b


## toMatchObject: every field of `want` is in `got` with that value.
static func matches(got: Dictionary, want: Dictionary) -> bool:
	for k in want:
		if not got.has(k) or not deep_eq(got[k], want[k]):
			return false
	return true


## `{ ...base, ...over }`, where an `over` value of UNDEFINED removes the field
## (TS `{ field: undefined }`, which JSON and `!== undefined` both treat as absent).
const UNDEFINED := "<undefined>"

static func with(base: Dictionary, over: Dictionary) -> Dictionary:
	var out := base.duplicate(true)
	for k in over:
		if over[k] is String and over[k] == UNDEFINED:
			out.erase(k)
		else:
			out[k] = over[k].duplicate(true) if (over[k] is Dictionary or over[k] is Array) else over[k]
	return out


## sim/metrics emptyMatchReport(), for the history tests.
## TODO(port): use Metrics.emptyMatchReport() once the sim port is merged.
static func emptyMatchReport() -> Dictionary:
	return {"you": emptySide(), "foe": emptySide(), "durationSec": 0.0, "mapName": ""}


static func emptySide() -> Dictionary:
	return {
		"teams": [], "score": 0.0, "gathered": 0.0, "gatheredBy": {"food": 0.0, "wood": 0.0, "gold": 0.0},
		"spent": 0.0, "spentOn": {"units": 0.0, "buildings": 0.0, "tech": 0.0}, "banked": 0.0,
		"unitsKilled": 0.0, "unitsLost": 0.0, "buildingsRazed": 0.0, "buildingsLost": 0.0,
		"damageDealt": 0.0, "damageTaken": 0.0, "peakArmy": 0.0, "peakVillagers": 0.0, "idleVillagerTime": 0.0,
		"idleTcTime": 0.0, "idleProductionTime": 0.0, "tcSeconds": 0.0, "productionSeconds": 0.0,
		"age": 0.0, "upgrades": 0.0, "trainedByType": {}, "lostByType": {}, "killedByType": {}, "builtByType": {},
		"killsByUnit": {}, "damageByUnit": {}, "razedByUnit": {},
		"firstHitAt": -1.0, "firstKillAt": -1.0, "firstRazeAt": -1.0,
		"firstAttackAt": -1.0, "firstDefendAt": -1.0, "firstFieldAt": -1.0, "damageAttacking": 0.0, "damageDefending": 0.0, "damageField": 0.0, "killsAttacking": 0.0, "killsDefending": 0.0, "openerByUnit": {},
	}
