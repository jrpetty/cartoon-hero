## Math.sin / Math.cos exactly as V8 computes them.
##
## V8 does not use the C library for trigonometry: it carries its own port of
## fdlibm (src/base/ieee754.cc). Godot's sin()/cos() go to the platform libm,
## and the two disagree in the last bit for roughly 3% of arguments (measured:
## 6,500 of 200,000 random angles). One ulp is harmless for drawing but not for
## a lockstep simulation or a seeded map generator, where it can flip a
## Math.round or a comparison and then everything downstream differs. So
## deterministic code calls `JSMath.sin` / `JSMath.cos`, which are fdlibm's
## algorithms transcribed line by line, with every constant built from its
## exact bit pattern (GDScript cannot parse a 17-digit literal reliably).
##
## Covered exactly: |x| up to 2^19·π/2 (≈ 823,550 rad) — far beyond any angle
## the game computes. Larger arguments need fdlibm's multi-precision
## __kernel_rem_pio2 and fall back to the platform libm (not bit-exact).
##
## Note: an unqualified `sin(x)` inside this file is Godot's builtin (utility
## functions win over methods); callers elsewhere write `JSMath.sin(x)`.
class_name JSMath
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


## A double from its two 32-bit words (fdlibm's INSERT_WORDS).
static func _words(hi: int, lo: int) -> float:
	_buf.encode_u32(0, lo & 0xFFFFFFFF)
	_buf.encode_u32(4, hi & 0xFFFFFFFF)
	return _buf.decode_double(0)


# __kernel_sin coefficients.
static var S1 := _words(0xBFC55555, 0x55555549)
static var S2 := _words(0x3F811111, 0x1110F8A6)
static var S3 := _words(0xBF2A01A0, 0x19C161D5)
static var S4 := _words(0x3EC71DE3, 0x57B1FE7D)
static var S5 := _words(0xBE5AE5E6, 0x8A2B9CEB)
static var S6 := _words(0x3DE5D93A, 0x5ACFD57C)
# __kernel_cos coefficients.
static var C1 := _words(0x3FA55555, 0x5555554C)
static var C2 := _words(0xBF56C16C, 0x16C15177)
static var C3 := _words(0x3EFA01A0, 0x19CB1590)
static var C4 := _words(0xBE927E4F, 0x809C52AD)
static var C5 := _words(0x3E21EE9E, 0xBDB4B1C4)
static var C6 := _words(0xBDA8FAE9, 0xBE8838D4)
# __ieee754_rem_pio2 constants.
static var invpio2 := _words(0x3FE45F30, 0x6DC9C883) # 53 bits of 2/pi
static var pio2_1 := _words(0x3FF921FB, 0x54400000)  # first 33 bits of pi/2
static var pio2_1t := _words(0x3DD0B461, 0x1A626331) # pi/2 - pio2_1
static var pio2_2 := _words(0x3DD0B461, 0x1A600000)  # second 33 bits of pi/2
static var pio2_2t := _words(0x3BA3198A, 0x2E037073) # pi/2 - (pio2_1+pio2_2)
static var pio2_3 := _words(0x3BA3198A, 0x2E000000)  # third 33 bits of pi/2
static var pio2_3t := _words(0x397B839A, 0x252049C1) # pi/2 - (pio2_1+pio2_2+pio2_3)

const NPIO2_HW := [
	0x3FF921FB, 0x400921FB, 0x4012D97C, 0x401921FB, 0x401F6A7A, 0x4022D97C,
	0x4025FDBB, 0x402921FB, 0x402C463A, 0x402F6A7A, 0x4031475C, 0x4032D97C,
	0x40346B9C, 0x4035FDBB, 0x40378FDB, 0x403921FB, 0x403AB41B, 0x403C463A,
	0x403DD85A, 0x403F6A7A, 0x40407E4C, 0x4041475C, 0x4042106C, 0x4042D97C,
	0x4043A28C, 0x40446B9C, 0x404534AC, 0x4045FDBB, 0x4046C6CB, 0x40478FDB,
	0x404858EB, 0x404921FB,
]

# rem_pio2's two-part result (y[0], y[1] in fdlibm).
static var _y0 := 0.0
static var _y1 := 0.0


## Math.sin
static func sin(x: float) -> float:
	var ix := _hi(x) & 0x7FFFFFFF
	if ix <= 0x3FE921FB: # |x| ~< pi/4
		return _kernel_sin(x, 0.0, 0)
	if ix >= 0x7FF00000: # sin(Inf or NaN) is NaN
		return x - x
	if ix > 0x413921FB: # beyond the exact range: platform libm
		return sin(x)
	var n := _rem_pio2(x)
	match n & 3:
		0:
			return _kernel_sin(_y0, _y1, 1)
		1:
			return _kernel_cos(_y0, _y1)
		2:
			return -_kernel_sin(_y0, _y1, 1)
		_:
			return -_kernel_cos(_y0, _y1)


## Math.cos
static func cos(x: float) -> float:
	var ix := _hi(x) & 0x7FFFFFFF
	if ix <= 0x3FE921FB: # |x| ~< pi/4
		return _kernel_cos(x, 0.0)
	if ix >= 0x7FF00000: # cos(Inf or NaN) is NaN
		return x - x
	if ix > 0x413921FB: # beyond the exact range: platform libm
		return cos(x)
	var n := _rem_pio2(x)
	match n & 3:
		0:
			return _kernel_cos(_y0, _y1)
		1:
			return -_kernel_sin(_y0, _y1, 1)
		2:
			return -_kernel_cos(_y0, _y1)
		_:
			return _kernel_sin(_y0, _y1, 1)


## __kernel_sin(x, y, iy): sin on [-pi/4, pi/4]; y is the tail of x.
static func _kernel_sin(x: float, y: float, iy: int) -> float:
	var ix := _hi(x) & 0x7FFFFFFF
	if ix < 0x3E400000: # |x| < 2**-27
		if int(x) == 0:
			return x
	var z := x * x
	var v := z * x
	var r := S2 + z * (S3 + z * (S4 + z * (S5 + z * S6)))
	if iy == 0:
		return x + v * (S1 + z * r)
	return x - ((z * (0.5 * y - v * r) - y) - v * S1)


## __kernel_cos(x, y): cos on [-pi/4, pi/4]; y is the tail of x.
static func _kernel_cos(x: float, y: float) -> float:
	var ix := _hi(x) & 0x7FFFFFFF
	if ix < 0x3E400000: # |x| < 2**-27
		if int(x) == 0:
			return 1.0
	var z := x * x
	var r := z * (C1 + z * (C2 + z * (C3 + z * (C4 + z * (C5 + z * C6)))))
	if ix < 0x3FD33333: # |x| < 0.3
		return 1.0 - (0.5 * z - (z * r - x * y))
	var qx: float
	if ix > 0x3FE90000: # x > 0.78125
		qx = 0.28125
	else:
		qx = _words(ix - 0x00200000, 0) # x/4
	var iz := 0.5 * z - qx
	var a := 1.0 - qx
	return a - (iz - (z * r - x * y))


## __ieee754_rem_pio2(x, y): x reduced by multiples of pi/2. Returns n and
## leaves the remainder in _y0 + _y1. Only called for pi/4 < |x| <= 2^19·pi/2.
static func _rem_pio2(x: float) -> int:
	var hx := _hi(x)
	var ix := hx & 0x7FFFFFFF
	if ix <= 0x3FE921FB: # |x| ~<= pi/4, no need for reduction
		_y0 = x
		_y1 = 0.0
		return 0
	if ix < 0x4002D97C: # |x| < 3pi/4, special case with n=+-1
		if hx > 0:
			var z := x - pio2_1
			if ix != 0x3FF921FB: # 33+53 bit pi is good enough
				_y0 = z - pio2_1t
				_y1 = (z - _y0) - pio2_1t
			else: # near pi/2, use 33+33+53 bit pi
				z -= pio2_2
				_y0 = z - pio2_2t
				_y1 = (z - _y0) - pio2_2t
			return 1
		else: # negative x
			var z := x + pio2_1
			if ix != 0x3FF921FB:
				_y0 = z + pio2_1t
				_y1 = (z - _y0) + pio2_1t
			else:
				z += pio2_2
				_y0 = z + pio2_2t
				_y1 = (z - _y0) + pio2_2t
			return -1
	# |x| ~<= 2^19*(pi/2), medium size
	var t := absf(x)
	var n := int(t * invpio2 + 0.5)
	var fn := float(n)
	var r := t - fn * pio2_1
	var w := fn * pio2_1t # 1st round good to 85 bit
	if n < 32 and ix != NPIO2_HW[n - 1]:
		_y0 = r - w # quick check no cancellation
	else:
		var j := ix >> 20
		_y0 = r - w
		var high := _hi(_y0)
		var i := j - ((high >> 20) & 0x7FF)
		if i > 16: # 2nd iteration needed, good to 118
			t = r
			w = fn * pio2_2
			r = t - w
			w = fn * pio2_2t - ((t - r) - w)
			_y0 = r - w
			high = _hi(_y0)
			i = j - ((high >> 20) & 0x7FF)
			if i > 49: # 3rd iteration need, 151 bits acc
				t = r
				w = fn * pio2_3
				r = t - w
				w = fn * pio2_3t - ((t - r) - w)
				_y0 = r - w
	_y1 = (r - _y0) - w
	if hx < 0:
		_y0 = -_y0
		_y1 = -_y1
		return -n
	return n
