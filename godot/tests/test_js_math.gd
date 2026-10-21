extends TestCase
# JSMath.sin / JSMath.cos are V8's results bit for bit (fixture: V8's own
# outputs as float64 bit patterns, from game/scripts/godot_parity_maps.ts).


static func _bits(h: String) -> float:
	var b := PackedByteArray()
	b.resize(8)
	b.encode_u32(0, ("0x" + h.substr(8, 8)).hex_to_int())
	b.encode_u32(4, ("0x" + h.substr(0, 8)).hex_to_int())
	return b.decode_double(0)


static func _same(a: float, b: float) -> bool:
	if is_nan(a) or is_nan(b):
		return is_nan(a) and is_nan(b)
	# Distinguish -0 from 0 too: compare the bits.
	var x := PackedByteArray()
	x.resize(16)
	x.encode_double(0, a)
	x.encode_double(8, b)
	return x.decode_u64(0) == x.decode_u64(8)


func test_sin_cos_match_v8_bit_for_bit() -> void:
	var fx = fixture("js_math.json")
	if fx == null:
		return
	var badS := 0
	var badC := 0
	var libmDiffers := 0
	for row in fx.trig:
		var x := _bits(row[0])
		var s := _bits(row[1])
		var c := _bits(row[2])
		if not _same(JSMath.sin(x), s):
			badS += 1
			if badS <= 3:
				failures.append("sin(%s) = %s, V8 says %s" % [row[0], str(JSMath.sin(x)), str(s)])
		if not _same(JSMath.cos(x), c):
			badC += 1
			if badC <= 3:
				failures.append("cos(%s) = %s, V8 says %s" % [row[0], str(JSMath.cos(x)), str(c)])
		if sin(x) != s or cos(x) != c:
			libmDiffers += 1
	eq(badS, 0, "sin mismatches")
	eq(badC, 0, "cos mismatches")
	print("      (Godot's builtin sin/cos differ from V8 on %d of %d of these)" % [libmDiffers, fx.trig.size()])


func test_special_values() -> void:
	check(is_nan(JSMath.sin(NAN)), "sin NaN")
	check(is_nan(JSMath.cos(INF)), "cos Inf")
	eq(JSMath.cos(0.0), 1.0, "cos 0")
	check(_same(JSMath.sin(-0.0), -0.0), "sin -0 is -0")
