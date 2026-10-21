## content/daynight.ts — shared day/night model. The phase is derived from
## deterministic sim time, so both the simulation (vision) and the renderer
## (sky tint) agree exactly and stay replay-safe. Night shrinks everyone's
## sight — the core night mechanic. (DAY_CYCLE lives in `C`.)
class_name DayNight


static func dayPhase(time: float) -> float:
	var cycle: float = C.DAY_CYCLE
	return fmod(time, cycle) / cycle


static func isNight(phase: float) -> bool:
	return phase > 0.55 and phase < 0.95


## Interpolate a scalar across sorted (phase, value) keyframes.
static func keyLerp(phase: float, keys: Array) -> float:
	for i in range(0, keys.size() - 1):
		var p0: float = keys[i][0]
		var v0: float = keys[i][1]
		var p1: float = keys[i + 1][0]
		var v1: float = keys[i + 1][1]
		if phase >= p0 and phase <= p1:
			var span := p1 - p0
			if span == 0.0:
				span = 1.0
			return MathU.lerp(v0, v1, (phase - p0) / span)
	return keys[keys.size() - 1][1]


const VISION_KEYS := [
	[0.0, 0.85],
	[0.22, 1.0],
	[0.46, 0.8],
	[0.6, 0.6],
	[0.9, 0.6],
	[1.0, 0.85],
]

const NIGHT_KEYS := [
	[0.0, 0.0],
	[0.46, 0.0],
	[0.6, 1.0],
	[0.9, 1.0],
	[1.0, 0.0],
]


## Vision multiplier by phase. ~1.0 at midday down to 0.6 at deep night
## (40% less sight in the dark). Dawn/dusk sit in between.
static func visionMult(phase: float) -> float:
	return keyLerp(phase, VISION_KEYS)


## How far into the night it is, 0 (day) to 1 (full dark).
static func nightAmount(phase: float) -> float:
	return keyLerp(phase, NIGHT_KEYS)


## Short label for the HUD.
static func dayLabel(phase: float) -> String:
	if phase < 0.05 or phase >= 0.96:
		return "Dawn"
	if phase < 0.4:
		return "Day"
	if phase < 0.55:
		return "Dusk"
	return "Night"


const SKY_KEYS := [
	[0.0, [255.0, 168.0, 96.0, 0.16]], # dawn
	[0.22, [255.0, 255.0, 255.0, 0.0]], # bright day
	[0.46, [255.0, 120.0, 60.0, 0.2]], # dusk
	[0.6, [22.0, 32.0, 74.0, 0.5]], # night
	[0.9, [18.0, 28.0, 68.0, 0.46]], # deep night
	[1.0, [255.0, 168.0, 96.0, 0.16]], # back to dawn
]


## Day/night sky tint by phase (0..1). Returns rgba overlay components [r, g, b, a].
static func skyTint(phase: float) -> Array:
	for i in range(0, SKY_KEYS.size() - 1):
		var p0: float = SKY_KEYS[i][0]
		var c0: Array = SKY_KEYS[i][1]
		var p1: float = SKY_KEYS[i + 1][0]
		var c1: Array = SKY_KEYS[i + 1][1]
		if phase >= p0 and phase <= p1:
			var span := p1 - p0
			if span == 0.0:
				span = 1.0
			var t := (phase - p0) / span
			return [MathU.lerp(c0[0], c1[0], t), MathU.lerp(c0[1], c1[1], t), MathU.lerp(c0[2], c1[2], t), MathU.lerp(c0[3], c1[3], t)]
	return [0.0, 0.0, 0.0, 0.0]
