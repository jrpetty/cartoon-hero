## engine/math.ts — small math utilities shared by the sim and renderer.
## Scalar floats only: Godot's Vector2 is 32-bit and would drift from the
## browser build's 64-bit numbers.
class_name MathU

const TAU := 6.283185307179586


static func clamp(v: float, lo: float, hi: float) -> float:
	return lo if v < lo else (hi if v > hi else v)


static func lerp(a: float, b: float, t: float) -> float:
	return a + (b - a) * t


static func dist(ax: float, ay: float, bx: float, by: float) -> float:
	return JS.hypot(ax - bx, ay - by)


static func dist2(ax: float, ay: float, bx: float, by: float) -> float:
	var dx := ax - bx
	var dy := ay - by
	return dx * dx + dy * dy


static func len(x: float, y: float) -> float:
	return JS.hypot(x, y)


## The angle from (ax,ay) to (bx,by).
static func angleTo(ax: float, ay: float, bx: float, by: float) -> float:
	return atan2(by - ay, bx - ax)


## Smallest signed difference between two angles, in (-PI, PI].
static func angleDiff(a: float, b: float) -> float:
	var d := fmod(b - a, TAU)
	if d < -PI:
		d += TAU
	if d > PI:
		d -= TAU
	return d


## Rotate `from` toward `to` by at most `maxStep` radians.
static func rotateToward(from: float, to: float, maxStep: float) -> float:
	var d := angleDiff(from, to)
	if absf(d) <= maxStep:
		return to
	return from + JS.sign(d) * maxStep


static func approach(current: float, target: float, maxDelta: float) -> float:
	if current < target:
		return minf(current + maxDelta, target)
	return maxf(current - maxDelta, target)


static func smoothstep(t: float) -> float:
	t = clamp(t, 0.0, 1.0)
	return t * t * (3.0 - 2.0 * t)


static func easeOutCubic(t: float) -> float:
	var p := 1.0 - t
	return 1.0 - p * p * p
