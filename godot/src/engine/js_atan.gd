## Math.atan / Math.atan2 exactly as V8 computes them.
##
## The companion of JSMath (sin/cos): V8 computes atan/atan2 with its own port
## of fdlibm (src/base/ieee754.cc, from FreeBSD's s_atan.c / e_atan2.c), while
## Godot's atan2() goes to the platform libm. The two disagree in the last bit
## for about 18% of arguments (measured: 35,249 of 200,000 random (y, x)
## pairs). The simulation turns atan2 into positions (a formation's heading, a
## skirmisher's line of retreat), so it calls `JSAtan.atan2`: fdlibm's
## algorithm transcribed line by line, every constant built from its exact bit
## pattern (GDScript cannot parse a 17-digit literal reliably).
##
## Note: an unqualified `atan(x)` / `atan2(y, x)` inside this file is Godot's
## builtin (utility functions win over methods); callers write `JSAtan.atan2`.
class_name JSAtan
extends RefCounted

static var _buf := _mkbuf()


static func _mkbuf() -> PackedByteArray:
	var b := PackedByteArray()
	b.resize(8)
	return b


## High 32 bits of a double, signed (fdlibm's GET_HIGH_WORD into an int32).
static func _hi(x: float) -> int:
	_buf.encode_double(0, x)
	return _buf.decode_s32(4)


## Low 32 bits of a double, unsigned (GET_LOW_WORD).
static func _lo(x: float) -> int:
	_buf.encode_double(0, x)
	return _buf.decode_u32(0)


## A double from its two 32-bit words (INSERT_WORDS).
static func _words(hi: int, lo: int) -> float:
	_buf.encode_u32(0, lo & 0xFFFFFFFF)
	_buf.encode_u32(4, hi & 0xFFFFFFFF)
	return _buf.decode_double(0)


static var ATANHI := [
	_words(0x3FDDAC67, 0x0561BB4F), # atan(0.5)hi
	_words(0x3FE921FB, 0x54442D18), # atan(1.0)hi
	_words(0x3FEF730B, 0xD281F69B), # atan(1.5)hi
	_words(0x3FF921FB, 0x54442D18), # atan(inf)hi
]
static var ATANLO := [
	_words(0x3C7A2B7F, 0x222F65E2), # atan(0.5)lo
	_words(0x3C81A626, 0x33145C07), # atan(1.0)lo
	_words(0x3C700788, 0x7AF0CBBD), # atan(1.5)lo
	_words(0x3C91A626, 0x33145C07), # atan(inf)lo
]
static var AT0 := _words(0x3FD55555, 0x5555550D)
static var AT1 := _words(0xBFC99999, 0x9998EBC4)
static var AT2 := _words(0x3FC24924, 0x920083FF)
static var AT3 := _words(0xBFBC71C6, 0xFE231671)
static var AT4 := _words(0x3FB745CD, 0xC54C206E)
static var AT5 := _words(0xBFB3B0F2, 0xAF749A6D)
static var AT6 := _words(0x3FB10D66, 0xA0D03D51)
static var AT7 := _words(0xBFADDE2D, 0x52DEFD9A)
static var AT8 := _words(0x3FA97B4B, 0x24760DEB)
static var AT9 := _words(0xBFA2B444, 0x2C6A6C2F)
static var AT10 := _words(0x3F90AD3A, 0xE322DA11)

static var PI_O_4 := _words(0x3FE921FB, 0x54442D18)
static var PI_O_2 := _words(0x3FF921FB, 0x54442D18)
static var PI_ := _words(0x400921FB, 0x54442D18)
static var PI_LO := _words(0x3CA1A626, 0x33145C07)
## -0.0 (a `-0.0` literal folds to +0.0 in GDScript).
static var NEG_ZERO := _words(0x80000000, 0)
const TINY := 1.0e-300
const HUGE := 1.0e300


## Math.atan
static func atan(x: float) -> float:
	var hx := _hi(x)
	var ix := hx & 0x7FFFFFFF
	var id := -1
	if ix >= 0x44100000: # if |x| >= 2^66
		var low := _lo(x)
		if ix > 0x7FF00000 or (ix == 0x7FF00000 and low != 0):
			return x + x # NaN
		if hx > 0:
			return ATANHI[3] + ATANLO[3]
		return -ATANHI[3] - ATANLO[3]
	if ix < 0x3FDC0000: # |x| < 0.4375
		if ix < 0x3E400000: # |x| < 2^-27
			if HUGE + x > 1.0:
				return x # raise inexact
		id = -1
	else:
		x = absf(x)
		if ix < 0x3FF30000: # |x| < 1.1875
			if ix < 0x3FE60000: # 7/16 <= |x| < 11/16
				id = 0
				x = (2.0 * x - 1.0) / (2.0 + x)
			else: # 11/16 <= |x| < 19/16
				id = 1
				x = (x - 1.0) / (x + 1.0)
		else:
			if ix < 0x40038000: # |x| < 2.4375
				id = 2
				x = (x - 1.5) / (1.0 + 1.5 * x)
			else: # 2.4375 <= |x| < 2^66
				id = 3
				x = -1.0 / x
	# end of argument reduction
	var z := x * x
	var w := z * z
	# break sum from i=0 to 10 aT[i]z**(i+1) into odd and even poly
	var s1 := z * (AT0 + w * (AT2 + w * (AT4 + w * (AT6 + w * (AT8 + w * AT10)))))
	var s2 := w * (AT1 + w * (AT3 + w * (AT5 + w * (AT7 + w * AT9))))
	if id < 0:
		return x - x * (s1 + s2)
	var zz: float = ATANHI[id] - ((x * (s1 + s2) - ATANLO[id]) - x)
	return -zz if hx < 0 else zz


## Math.atan2
static func atan2(y: float, x: float) -> float:
	var hx := _hi(x)
	var lx := _lo(x)
	var ix := hx & 0x7FFFFFFF
	var hy := _hi(y)
	var ly := _lo(y)
	var iy := hy & 0x7FFFFFFF
	if is_nan(x) or is_nan(y): # x or y is NaN
		return x + y
	if hx == 0x3FF00000 and lx == 0:
		return JSAtan.atan(y) # x = 1.0
	var m := ((hy >> 31) & 1) | ((hx >> 30) & 2) # 2*sign(x)+sign(y)

	# when y = 0
	if (iy | ly) == 0:
		match m:
			0, 1:
				return y # atan(+-0,+anything)=+-0
			2:
				return PI_ + TINY # atan(+0,-anything) = pi
			_:
				return -PI_ - TINY # atan(-0,-anything) =-pi
	# when x = 0
	if (ix | lx) == 0:
		return -PI_O_2 - TINY if hy < 0 else PI_O_2 + TINY

	# when x is INF
	if ix == 0x7FF00000:
		if iy == 0x7FF00000:
			match m:
				0:
					return PI_O_4 + TINY # atan(+INF,+INF)
				1:
					return -PI_O_4 - TINY # atan(-INF,+INF)
				2:
					return 3.0 * PI_O_4 + TINY # atan(+INF,-INF)
				_:
					return -3.0 * PI_O_4 - TINY # atan(-INF,-INF)
		else:
			match m:
				0:
					return 0.0 # atan(+...,+INF)
				1:
					return NEG_ZERO # atan(-...,+INF)
				2:
					return PI_ + TINY # atan(+...,-INF)
				_:
					return -PI_ - TINY # atan(-...,-INF)
	# when y is INF
	if iy == 0x7FF00000:
		return -PI_O_2 - TINY if hy < 0 else PI_O_2 + TINY

	# compute y/x
	var k := (iy - ix) >> 20
	var z: float
	if k > 60: # |y/x| > 2**60
		z = PI_O_2 + 0.5 * PI_LO
		m &= 1
	elif hx < 0 and k < -60: # 0 > |y|/x > -2**-60
		z = 0.0
	else: # safe to do y/x
		z = JSAtan.atan(absf(y / x))
	match m:
		0:
			return z # atan(+,+)
		1:
			return -z # atan(-,+)
		2:
			return PI_ - (z - PI_LO) # atan(+,-)
		_:
			return (z - PI_LO) - PI_ # atan(-,-)
