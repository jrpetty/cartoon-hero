## JavaScript semantics the port depends on, in one place.
##
## The TypeScript game is the specification, and it was written against
## JavaScript's number rules. Where GDScript differs in a way that would change
## the game (rounding, sorting, 32-bit integer maths, Math.hypot), the port
## calls these instead, so both versions compute the same results.
class_name JS

const PI := 3.141592653589793
const TAU := 6.283185307179586
const MAX_SAFE_INTEGER := 9007199254740991.0


## Math.round: halves round toward +infinity (GDScript's round() goes away from zero).
static func round(x: float) -> float:
	return floor(x + 0.5)


static func roundi(x: float) -> int:
	return int(floor(x + 0.5))


## Math.sign
static func sign(x: float) -> float:
	return 1.0 if x > 0.0 else (-1.0 if x < 0.0 else 0.0)


## Math.trunc
static func trunc(x: float) -> float:
	return floor(x) if x >= 0.0 else ceil(x)


## The `%` operator on numbers (sign follows the dividend), for floats.
static func mod(a: float, b: float) -> float:
	return fmod(a, b)


## Math.hypot, exactly as V8 computes it (scaled Kahan sum), so distances come
## out bit-identical to the browser build rather than one ulp off.
static func hypot(a: float, b: float) -> float:
	var x := absf(a)
	var y := absf(b)
	if is_nan(a) or is_nan(b):
		if is_inf(a) or is_inf(b):
			return INF
		return NAN
	var m := maxf(x, y)
	if is_inf(m):
		return INF
	if m == 0.0:
		return 0.0
	var s := 0.0
	var comp := 0.0
	var n := x / m
	var summand := n * n - comp
	var prelim := s + summand
	comp = (prelim - s) - summand
	s = prelim
	n = y / m
	summand = n * n - comp
	prelim = s + summand
	comp = (prelim - s) - summand
	s = prelim
	return sqrt(s) * m


## ToInt32: wrap any integer to a signed 32-bit value.
static func i32(v: int) -> int:
	v = v & 0xFFFFFFFF
	return v - 0x100000000 if v >= 0x80000000 else v


## ToUint32 (`x >>> 0`).
static func u32(v: int) -> int:
	return v & 0xFFFFFFFF


## Math.imul: 32-bit integer multiply, done in 16-bit halves so it can't
## overflow a 64-bit int on the way.
static func imul(a: int, b: int) -> int:
	a = a & 0xFFFFFFFF
	b = b & 0xFFFFFFFF
	var ah := (a >> 16) & 0xFFFF
	var al := a & 0xFFFF
	var bh := (b >> 16) & 0xFFFF
	var bl := b & 0xFFFF
	var lo := al * bl
	var mid := ((ah * bl + al * bh) << 16) & 0xFFFFFFFF
	return i32(lo + mid)


## `x >>> n` on a 32-bit value.
static func ushr(x: int, n: int) -> int:
	return (x & 0xFFFFFFFF) >> n


## A stable sort, like Array.prototype.sort. Godot's sort_custom is not stable,
## and equal elements landing in a different order changes which unit acts
## first — a different match. `less(a, b)` must return true when a goes first.
## Sorts in place and returns the array.
static func stable_sort(arr: Array, less: Callable) -> Array:
	var n := arr.size()
	if n < 2:
		return arr
	# Insertion sort for small arrays, merge sort above.
	if n <= 12:
		for i in range(1, n):
			var v = arr[i]
			var j := i - 1
			while j >= 0 and less.call(v, arr[j]):
				arr[j + 1] = arr[j]
				j -= 1
			arr[j + 1] = v
		return arr
	var tmp := arr.duplicate()
	_merge_sort(arr, tmp, 0, n, less)
	return arr


static func _merge_sort(a: Array, tmp: Array, lo: int, hi: int, less: Callable) -> void:
	if hi - lo <= 12:
		for i in range(lo + 1, hi):
			var v = a[i]
			var j := i - 1
			while j >= lo and less.call(v, a[j]):
				a[j + 1] = a[j]
				j -= 1
			a[j + 1] = v
		return
	var mid := (lo + hi) >> 1
	_merge_sort(a, tmp, lo, mid, less)
	_merge_sort(a, tmp, mid, hi, less)
	if not less.call(a[mid], a[mid - 1]):
		return
	for i in range(lo, hi):
		tmp[i] = a[i]
	var i := lo
	var j := mid
	var k := lo
	while i < mid and j < hi:
		if less.call(tmp[j], tmp[i]):
			a[k] = tmp[j]
			j += 1
		else:
			a[k] = tmp[i]
			i += 1
		k += 1
	while i < mid:
		a[k] = tmp[i]
		i += 1
		k += 1
	while j < hi:
		a[k] = tmp[j]
		j += 1
		k += 1


## Array.prototype.sort with a numeric comparator `cmp(a, b) -> number`
## (negative = a first), stable, as JS does it.
static func sort_by_cmp(arr: Array, cmp: Callable) -> Array:
	return stable_sort(arr, func(a, b): return cmp.call(a, b) < 0)


## Number.prototype.toFixed(digits).
static func to_fixed(x: float, digits: int) -> String:
	var p := pow(10.0, digits)
	var r := JS.round(absf(x) * p) / p
	var s := ("%." + str(digits) + "f") % r
	return ("-" + s) if x < 0.0 and r != 0.0 else s


## `${n}` / String(n): integers print without a decimal point.
static func num(x: float) -> String:
	if is_nan(x):
		return "NaN"
	if is_inf(x):
		return "Infinity" if x > 0.0 else "-Infinity"
	if x == floor(x) and absf(x) < 1e21:
		return str(int(x))
	return str(x)


## toLocaleString("en-GB") for whole numbers: 12345 -> "12,345".
static func locale_int(x: float) -> String:
	var n := int(JS.round(x))
	var neg := n < 0
	var s := str(absi(n))
	var out := ""
	while s.length() > 3:
		out = "," + s.substr(s.length() - 3) + out
		s = s.substr(0, s.length() - 3)
	return ("-" if neg else "") + s + out


## Array.prototype.indexOf / includes on any Array.
static func includes(arr: Array, v) -> bool:
	return arr.has(v)


## Object.keys for a Dictionary used as a JS object.
static func keys(d: Dictionary) -> Array:
	return d.keys()


## `a ?? b` for values that may be null.
static func nz(a, b):
	return b if a == null else a


## Read an optional field from a JSON-backed definition: `def.key ?? fallback`.
static func opt(d: Dictionary, key: String, fallback = null):
	var v = d.get(key)
	return fallback if v == null else v


## Date.now(): milliseconds since the Unix epoch.
static func now_ms() -> float:
	return floor(Time.get_unix_time_from_system() * 1000.0)


## Math.random() — NOT for the simulation (which must use RNG).
static func random() -> float:
	return randf()
