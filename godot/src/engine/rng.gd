## Seeded, deterministic RNG (mulberry32) — a bit-exact port of engine/rng.ts,
## so the same seed gives the same sequence in both builds.
class_name RNG
extends RefCounted

var state: int


func _init(seed: int) -> void:
	var s := JS.u32(seed)
	state = s if s != 0 else 0x9e3779b9


## Float in [0, 1).
func next() -> float:
	state = JS.i32(state)
	state = JS.i32(state + 0x6d2b79f5)
	var t := JS.imul(state ^ JS.ushr(state, 15), 1 | state)
	t = JS.i32(JS.i32(t + JS.imul(t ^ JS.ushr(t, 7), 61 | t)) ^ t)
	return float(JS.u32(t ^ JS.ushr(t, 14))) / 4294967296.0


## Integer in [min, max] inclusive.
func int_(lo: int, hi: int) -> int:
	return lo + int(floor(next() * float(hi - lo + 1)))


## Float in [min, max).
func range(lo: float, hi: float) -> float:
	return lo + next() * (hi - lo)


func bool_(p: float = 0.5) -> bool:
	return next() < p


func pick(arr: Array):
	return arr[int(floor(next() * arr.size()))]


## Pick an index given parallel weights.
func weightedIndex(weights: Array) -> int:
	var total := 0.0
	for w in weights:
		total += float(w)
	var r := next() * total
	for i in weights.size():
		r -= float(weights[i])
		if r < 0.0:
			return i
	return weights.size() - 1


## Fork a child generator deterministically (useful for sub-systems).
func fork(salt: int) -> RNG:
	return RNG.new(JS.u32(state ^ JS.imul(salt + 1, 0x85ebca6b)))


static func randomSeed() -> int:
	var s := JS.u32(int(floor(randf() * 4294967295.0)))
	return s if s != 0 else 1
