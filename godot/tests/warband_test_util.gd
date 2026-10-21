## Helpers shared by the Warband Tactics tests.
class_name TestWarbandUtil


## Deep equality the way vitest's toEqual reads JSON-ish values: numbers by
## value whatever their int/float type, arrays and dictionaries recursively.
static func same(a, b) -> bool:
	if (a is int or a is float) and (b is int or b is float):
		return float(a) == float(b)
	if a is bool or b is bool:
		return a is bool and b is bool and a == b
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
	return a == b


## Number.prototype.toString(36) for a non-negative integer.
static func base36(n: int) -> String:
	return Replay.toBase36(n)
