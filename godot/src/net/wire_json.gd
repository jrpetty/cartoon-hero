## JSON for the wire, with JavaScript's numbers.
##
## Godot's JSON parser (String::to_float underneath) is not correctly rounded:
## about one decimal in ten that a browser writes for a double — the shortest
## text that reads back exactly — comes back from Godot one unit in the last
## place off. Off the wire that is invisible; in a lockstep match it is a
## desync, because a move order to x = 612.3456789 is applied at a different x
## by a browser and by a Godot client, and the two simulations part ways.
##
## So incoming JSON is parsed by Godot (structure, strings) and every number is
## then re-read from its own text with a correctly rounded decimal-to-double
## conversion — exactly what JSON.parse does in a browser. Outgoing JSON uses
## Godot's full-precision writer (17 significant digits), which every correctly
## rounded reader, the browser's and this one, reads back to the same double.
##
## Numbers come back as floats, as in JS (and as Godot's own JSON gives them).
class_name WireJSON
extends RefCounted

static var _tokens: RegEx = null
static var _pow10: PackedFloat64Array = PackedFloat64Array()


## JSON.stringify, the way the wire wants it: insertion order, full precision.
static func stringify(v) -> String:
	return JSON.stringify(v, "", false, true)


## JSON.parse with JS number semantics. Returns null on malformed text (like
## JSON.parse_string).
static func parse(text: String):
	var json := JSON.new()
	if json.parse(text) != OK:
		return null
	var data = json.data
	if _tokens == null:
		_tokens = RegEx.create_from_string("\"(?:[^\"\\\\]|\\\\.)*\"|-?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
	var nums := PackedStringArray()
	for m in _tokens.search_all(text):
		var s := m.get_string()
		if s.unicode_at(0) != 34: # not a string literal
			nums.append(s)
	var at := [0]
	return _refill(data, nums, at)


## Walk the parsed value in document order (Godot's Dictionaries keep the
## order keys were read in), swapping each number for its exact reading.
static func _refill(v, nums: PackedStringArray, at: Array):
	match typeof(v):
		TYPE_FLOAT, TYPE_INT:
			var i: int = at[0]
			at[0] = i + 1
			return toDouble(nums[i]) if i < nums.size() else float(v)
		TYPE_ARRAY:
			var a: Array = v
			for k in a.size():
				a[k] = _refill(a[k], nums, at)
			return a
		TYPE_DICTIONARY:
			var d: Dictionary = v
			for k in d.keys():
				d[k] = _refill(d[k], nums, at)
			return d
	return v


# ------------------------------------------------- decimal to double, exactly --

## A JSON number's text to the nearest double, ties to even — what
## `Number(s)` / JSON.parse give in JavaScript.
static func toDouble(s: String) -> float:
	var neg := s.begins_with("-")
	var i := 1 if neg else 0
	var digits := ""
	var exp10 := 0
	var n := s.length()
	var seen_point := false
	while i < n:
		var ch := s.unicode_at(i)
		if ch >= 48 and ch <= 57:
			if digits != "" or ch != 48:
				digits += s[i]
				if seen_point:
					exp10 -= 1
			elif seen_point:
				exp10 -= 1
		elif ch == 46: # .
			seen_point = true
		elif ch == 101 or ch == 69: # e E
			exp10 += s.substr(i + 1).to_int()
			break
		else:
			break
		i += 1
	# Leading zeros never reach `digits`; trailing ones move into the exponent.
	var trim := digits.length()
	while trim > 0 and digits.unicode_at(trim - 1) == 48:
		trim -= 1
	exp10 += digits.length() - trim
	digits = digits.substr(0, trim)
	if digits == "":
		# (Spelled out: GDScript folds `-0.0 if neg else 0.0` to -0.0.)
		var zero := 0.0
		return -zero if neg else zero
	var v := _toDoubleAbs(digits, exp10, s.trim_prefix("-"))
	return -v if neg else v


static func _p10(k: int) -> float:
	if _pow10.is_empty():
		var p := 1.0
		for j in 23:
			_pow10.append(p) # every power up to 1e22 is a double, exactly
			p *= 10.0
	return _pow10[k]


static func _toDoubleAbs(digits: String, e: int, text: String) -> float:
	var nd := digits.length()
	# Fast path (Clinger): an exact integer times or over an exact power of
	# ten is one correctly rounded IEEE operation.
	if nd <= 15 and e >= -22 and e <= 22:
		var m := float(digits.to_int())
		return m * _p10(e) if e >= 0 else m / _p10(-e)
	if nd <= 15 and e > 22 and e <= 22 + 15 - nd:
		var m2 := float(digits.to_int()) * _p10(e - 22)
		return m2 * _p10(22)
	# Way out of range.
	if nd + e > 310:
		return INF
	if nd + e < -330:
		return 0.0
	# Otherwise: start from a close reading (a few roundings of an exact
	# integer by exact powers of ten — within a few ulps; Godot's own
	# to_float can be a dozen out on small numbers) and settle it with exact
	# big-integer comparisons against the midpoints.
	var c := _approx(digits, e)
	if is_nan(c) or c == 0.0:
		c = text.to_float()
	if is_inf(c):
		c = _from_bits(0x7FEFFFFFFFFFFFFF) # the largest double
	var big_d := _Big.from_digits(digits)
	for _guard in 64:
		var me := _decompose(c)
		var m: int = me[0]
		var f: int = me[1]
		if m == 0:
			# Below the smallest subnormal's half: compare against it.
			var up := _cmp_dec_bin(big_d, e, 1, -1075)
			return 0.0 if up <= 0 else _from_bits(1) # the smallest subnormal
		# Upper midpoint: (2m+1)·2^(f-1).
		var hi := _cmp_dec_bin(big_d, e, 2 * m + 1, f - 1)
		if hi > 0 or (hi == 0 and (m & 1) == 1):
			c = _next_up(c)
			continue
		# Lower midpoint: (2m-1)·2^(f-1), or (4m-1)·2^(f-2) at a power of two
		# (the gap below is half the gap above).
		var lo: int
		if m == (1 << 52) and f > -1074:
			lo = _cmp_dec_bin(big_d, e, 4 * m - 1, f - 2)
		else:
			lo = _cmp_dec_bin(big_d, e, 2 * m - 1, f - 1)
		if lo < 0 or (lo == 0 and (m & 1) == 1):
			c = _next_down(c)
			continue
		return c
	return c


## digits·10^e to within a few ulps: the leading 18 digits as an exact
## integer, then exact powers of ten, 1e22 at a time.
static func _approx(digits: String, e: int) -> float:
	var nd := digits.length()
	var head := digits.substr(0, 18)
	e += nd - head.length()
	var x := float(head.to_int())
	while e > 22:
		x *= _p10(22)
		e -= 22
	while e < -22:
		x /= _p10(22)
		e += 22
	return x * _p10(e) if e >= 0 else x / _p10(-e)


## [m, f] with c = m·2^f, m an integer below 2^53.
static func _decompose(c: float) -> Array:
	var b := PackedFloat64Array([c]).to_byte_array().decode_s64(0)
	var ex := (b >> 52) & 0x7FF
	var frac := b & ((1 << 52) - 1)
	if ex == 0:
		return [frac, -1074]
	return [frac | (1 << 52), ex - 1075]


static func _from_bits(b: int) -> float:
	var pb := PackedByteArray()
	pb.resize(8)
	pb.encode_s64(0, b)
	return pb.decode_double(0)


static func _next_up(c: float) -> float:
	return _from_bits(PackedFloat64Array([c]).to_byte_array().decode_s64(0) + 1)


static func _next_down(c: float) -> float:
	return _from_bits(PackedFloat64Array([c]).to_byte_array().decode_s64(0) - 1)


## Sign of (D·10^e − k·2^f), exactly.
static func _cmp_dec_bin(d: _Big, e: int, k: int, f: int) -> int:
	# Bring both sides to integers: D·5^ep·2^(ep+fn)  vs  k·5^en·2^(fp+en).
	var ep := maxi(e, 0)
	var en := maxi(-e, 0)
	var fp := maxi(f, 0)
	var fn := maxi(-f, 0)
	var lhs := d.copy()
	lhs.mul_pow5(ep)
	var rhs := _Big.from_int(k)
	rhs.mul_pow5(en)
	var p := (ep + fn) - (fp + en)
	if p > 0:
		lhs.shl(p)
	elif p < 0:
		rhs.shl(-p)
	return lhs.cmp(rhs)


## A non-negative big integer: little-endian limbs of 24 bits.
class _Big:
	const BITS := 24
	const BASE := 1 << 24
	const MASK := (1 << 24) - 1
	var limbs: PackedInt64Array = PackedInt64Array()

	static func from_int(v: int) -> _Big:
		var b := _Big.new()
		while v > 0:
			b.limbs.append(v & MASK)
			v >>= BITS
		return b

	static func from_digits(s: String) -> _Big:
		var b := _Big.new()
		var i := 0
		while i < s.length():
			var chunk := s.substr(i, 6)
			var mul := 1
			for _j in chunk.length():
				mul *= 10
			b.mul_small(mul)
			b.add_small(chunk.to_int())
			i += 6
		return b

	func copy() -> _Big:
		var b := _Big.new()
		b.limbs = limbs.duplicate()
		return b

	func mul_small(k: int) -> void:
		var carry := 0
		for i in limbs.size():
			var t := limbs[i] * k + carry
			limbs[i] = t & MASK
			carry = t >> BITS
		while carry > 0:
			limbs.append(carry & MASK)
			carry >>= BITS

	func add_small(k: int) -> void:
		var i := 0
		var carry := k
		while carry > 0:
			if i == limbs.size():
				limbs.append(0)
			var t := limbs[i] + carry
			limbs[i] = t & MASK
			carry = t >> BITS
			i += 1

	func mul_pow5(n: int) -> void:
		# 5^13 = 1220703125 < 2^31: thirteen at a time.
		while n >= 13:
			mul_small(1220703125)
			n -= 13
		if n > 0:
			var k := 1
			for _j in n:
				k *= 5
			mul_small(k)

	func shl(n: int) -> void:
		var whole := n / BITS
		var part := n % BITS
		if part > 0:
			var carry := 0
			for i in limbs.size():
				var t := (limbs[i] << part) | carry
				limbs[i] = t & MASK
				carry = t >> BITS
			if carry > 0:
				limbs.append(carry)
		if whole > 0 and not limbs.is_empty():
			var z := PackedInt64Array()
			z.resize(whole)
			z.fill(0)
			z.append_array(limbs)
			limbs = z

	func _trim() -> void:
		while not limbs.is_empty() and limbs[limbs.size() - 1] == 0:
			limbs.resize(limbs.size() - 1)

	func cmp(o: _Big) -> int:
		_trim()
		o._trim()
		if limbs.size() != o.limbs.size():
			return 1 if limbs.size() > o.limbs.size() else -1
		for i in range(limbs.size() - 1, -1, -1):
			if limbs[i] != o.limbs[i]:
				return 1 if limbs[i] > o.limbs[i] else -1
		return 0
