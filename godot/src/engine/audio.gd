## engine/audio.ts — synthesized sound, no asset files.
##
## The TS build plays every effect live through WebAudio oscillators, noise
## buffers and biquad filters. Godot has no node graph like that, so this
## renders the same recipe (same oscillators, frequency sweeps, envelopes and
## filters) into a sample buffer and plays the buffer. Effects are rendered a
## few variants at a time on a worker thread at startup (the TS varies pitch
## a little on every play; three pre-rendered variations sound the same), and
## the generative music renders each note it needs once and keeps it.
##
## Autoload name `audio`, so call sites read as in TS: `audio.play("ui")`.
extends Node

const RATE := 22050
const VARIANTS := 3

var masterVol := 0.8
var sfxVol := 0.7
var musicVol := 0.35
var muted := false

var _bank: Dictionary = {}  # name -> Array[AudioStreamWAV]
var _music_cache: Dictionary = {}  # key -> AudioStreamWAV
var _music_pending: Dictionary = {}  # key -> true while a worker renders it
var _sfx_players: Array = []
var _music_players: Array = []
var _next_sfx := 0
var _next_music := 0
var _mutex := Mutex.new()
var _rng := RandomNumberGenerator.new()

const SFX_NAMES := ["select", "command", "build", "complete", "sword", "bow", "arrowHit", "siege",
	"collapse", "death", "alert", "ui", "coin", "tick", "reveal", "levelup"]

# --- Generative music state (as in TS) ---
var scale := [0, 3, 5, 7, 10]  # minor pentatonic
var root := 196.0  # G3
var chords := [0, 8, 3, 10]
var chordIdx := 0
var barTimer := 0.0
var lastStep := 0
var drumTimer := 0.0
var musicTimer := 0.0


func _ready() -> void:
	_rng.randomize()
	_ensure_bus("SFX")
	_ensure_bus("Music")
	for i in 12:
		var p := AudioStreamPlayer.new()
		p.bus = "SFX"
		add_child(p)
		_sfx_players.append(p)
	for i in 10:
		var p := AudioStreamPlayer.new()
		p.bus = "Music"
		add_child(p)
		_music_players.append(p)
	applyVolumes()
	# Render the effect bank in the background; play() skips anything not ready.
	if DisplayServer.get_name() != "headless":
		WorkerThreadPool.add_task(_render_bank, false, "audio bank")


func _ensure_bus(bus_name: String) -> void:
	if AudioServer.get_bus_index(bus_name) == -1:
		AudioServer.add_bus()
		var i := AudioServer.bus_count - 1
		AudioServer.set_bus_name(i, bus_name)
		AudioServer.set_bus_send(i, "Master")


## Browser autoplay shims — nothing to do in Godot, kept so call sites port as-is.
func ensure() -> void:
	pass


func resume() -> void:
	pass


func applyVolumes() -> void:
	AudioServer.set_bus_volume_db(0, linear_to_db(0.0001 if muted else maxf(0.0001, masterVol)))
	var s := AudioServer.get_bus_index("SFX")
	if s >= 0:
		AudioServer.set_bus_volume_db(s, linear_to_db(maxf(0.0001, sfxVol)))
	var m := AudioServer.get_bus_index("Music")
	if m >= 0:
		AudioServer.set_bus_volume_db(m, linear_to_db(maxf(0.0001, musicVol)))


func play(sfx_name: String) -> void:
	if muted:
		return
	_mutex.lock()
	var vs: Array = _bank.get(sfx_name, [])
	_mutex.unlock()
	if vs.is_empty():
		return
	var p: AudioStreamPlayer = _sfx_players[_next_sfx]
	_next_sfx = (_next_sfx + 1) % _sfx_players.size()
	p.stream = vs[_rng.randi() % vs.size()]
	p.play()


# --------------------------------------------------------------- synthesis --

## A voice list, like the sequence of tone()/noise() calls in the TS `play`.
## Each voice: {k:"tone", f, type, a, d, peak, fEnd, delay} or {k:"noise", dur, peak, ff, ft, delay}.
func _recipe(sfx_name: String, rng: RandomNumberGenerator) -> Array:
	var vary := func(amt := 0.06) -> float: return 1.0 + (rng.randf() * 2.0 - 1.0) * amt
	var v: float = vary.call()
	var out: Array = []
	var tone := func(f: float, ty: String, a: float, d: float, peak: float, f_end = null, delay := 0.0):
		out.append({"k": "tone", "f": f, "type": ty, "a": a, "d": d, "peak": peak, "fEnd": f_end, "delay": delay})
	var noise := func(dur: float, peak: float, ff: float, ft := "bandpass", delay := 0.0):
		out.append({"k": "noise", "dur": dur, "peak": peak, "ff": ff, "ft": ft, "delay": delay})
	match sfx_name:
		"select": tone.call(620 * v, "triangle", 0.005, 0.07, 0.25)
		"command": tone.call(420 * v, "triangle", 0.005, 0.08, 0.22, 520 * v)
		"build":
			noise.call(0.08, 0.16, 800, "lowpass")
			noise.call(0.1, 0.14, 700, "lowpass", 0.09)
		"complete":
			tone.call(523, "sine", 0.01, 0.18, 0.3, 784)
			tone.call(784, "sine", 0.02, 0.22, 0.16, 1046, 0.06)
		"sword":
			var b: float = vary.call(0.09)
			noise.call(0.05, 0.26, 5200 * b, "highpass")
			noise.call(0.07, 0.18, 3000 * b, "bandpass", 0.004)
			tone.call(2100 * b, "triangle", 0.001, 0.17, 0.11, 1500 * b, 0.004)
			tone.call(3170 * b, "triangle", 0.001, 0.14, 0.07, 2300 * b, 0.004)
			tone.call(4600 * b, "sine", 0.001, 0.11, 0.05, 3600 * b, 0.006)
			tone.call(170 * b, "triangle", 0.002, 0.05, 0.11, 85, 0.001)
		"bow":
			tone.call(900 * v, "sawtooth", 0.004, 0.09, 0.12, 300)
			noise.call(0.04, 0.08, 2400, "bandpass")
		"arrowHit":
			noise.call(0.08, 0.2, 1800 * v)
			noise.call(0.05, 0.1, 600, "lowpass", 0.01)
		"siege":
			tone.call(92 * v, "square", 0.005, 0.32, 0.36, 40)
			noise.call(0.28, 0.22, 520, "lowpass")
			noise.call(0.22, 0.12, 1600, "bandpass", 0.05)
		"collapse":
			tone.call(70, "square", 0.01, 0.5, 0.22, 32)
			noise.call(0.5, 0.34, 300, "lowpass")
			noise.call(0.4, 0.2, 900, "bandpass", 0.12)
		"death":
			tone.call(220 * v, "triangle", 0.005, 0.16, 0.18, 90)
			noise.call(0.12, 0.12, 500, "lowpass", 0.01)
		"alert":
			tone.call(300, "square", 0.02, 0.22, 0.22, 300)
			tone.call(400, "square", 0.02, 0.28, 0.2, 400, 0.18)
		"ui": tone.call(700 * v, "sine", 0.004, 0.05, 0.18)
		"coin": tone.call(1180 * v, "triangle", 0.004, 0.09, 0.2, 1568)
		"tick": tone.call(1000, "square", 0.002, 0.025, 0.08)
		"reveal": tone.call(440, "sine", 0.01, 0.4, 0.3, 1320)
		"levelup":
			tone.call(523, "sine", 0.01, 0.25, 0.3, 1047)
			tone.call(659, "sine", 0.02, 0.3, 0.2, 1318, 0.08)
			tone.call(784, "sine", 0.02, 0.32, 0.16, 1568, 0.16)
	return out


func _render_bank() -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = 1234
	for n in SFX_NAMES:
		var vs: Array = []
		for i in VARIANTS:
			vs.append(_to_stream(_mix(_recipe(n, rng), rng)))
		_mutex.lock()
		_bank[n] = vs
		_mutex.unlock()


func _mix(voices: Array, rng: RandomNumberGenerator) -> PackedFloat32Array:
	var total := 0.0
	for vc in voices:
		var end: float = vc.delay + (vc.a + vc.d + 0.02 if vc.k == "tone" else vc.dur)
		total = maxf(total, end)
	var buf := PackedFloat32Array()
	buf.resize(int(total * RATE) + 1)
	for vc in voices:
		if vc.k == "tone":
			_add_tone(buf, int(vc.delay * RATE), vc.f, vc.type, vc.a, vc.d, vc.peak, vc.fEnd)
		else:
			_add_noise(buf, int(vc.delay * RATE), vc.dur, vc.peak, vc.ff, vc.ft, rng)
	return buf


func _osc(phase: float, ty: String) -> float:
	var p := phase - floorf(phase)
	match ty:
		"sine": return sin(p * TAU)
		"square": return 1.0 if p < 0.5 else -1.0
		"sawtooth": return 2.0 * p - 1.0
		_: return 1.0 - 4.0 * absf(p - 0.5)  # triangle


## WebAudio's envelope: 0.0001 → linear to peak over `a`, exponential to 0.0001 over `d`.
func _env(t: float, a: float, d: float, peak: float) -> float:
	if t < a:
		return 0.0001 + (peak - 0.0001) * (t / a)
	var u := minf(1.0, (t - a) / d)
	return peak * pow(0.0001 / peak, u)


func _add_tone(buf: PackedFloat32Array, start: int, f0: float, ty: String, a: float, d: float, peak: float, f_end) -> void:
	var n := int((a + d + 0.02) * RATE)
	var phase := 0.0
	var f1: float = maxf(20.0, f_end) if f_end != null else f0
	var span := a + d
	for i in n:
		var t := float(i) / RATE
		var f := f0 if f_end == null else f0 * pow(f1 / f0, minf(1.0, t / span))
		phase += f / RATE
		var idx := start + i
		if idx < buf.size():
			buf[idx] += _osc(phase, ty) * (_env(t, a, d, peak) if t <= span else 0.0)


## Noise with a linear fade, through an RBJ biquad (as WebAudio's), with an
## exponential gain from `peak` to silence.
func _add_noise(buf: PackedFloat32Array, start: int, dur: float, peak: float, ff: float, ft: String, rng: RandomNumberGenerator) -> void:
	var n := int(dur * RATE)
	var w0 := TAU * minf(ff, RATE * 0.49) / RATE
	var cw := cos(w0)
	var sw := sin(w0)
	var q := 1.0 if ft == "bandpass" else 0.7071 * pow(10.0, 1.0 / 20.0)
	var alpha := sw / (2.0 * q)
	var b0 := 0.0
	var b1 := 0.0
	var b2 := 0.0
	match ft:
		"lowpass":
			b0 = (1.0 - cw) / 2.0
			b1 = 1.0 - cw
			b2 = b0
		"highpass":
			b0 = (1.0 + cw) / 2.0
			b1 = -(1.0 + cw)
			b2 = b0
		_:
			b0 = alpha
			b1 = 0.0
			b2 = -alpha
	var a0 := 1.0 + alpha
	var a1 := -2.0 * cw
	var a2 := 1.0 - alpha
	b0 /= a0
	b1 /= a0
	b2 /= a0
	a1 /= a0
	a2 /= a0
	var x1 := 0.0
	var x2 := 0.0
	var y1 := 0.0
	var y2 := 0.0
	for i in n:
		var x := (rng.randf() * 2.0 - 1.0) * (1.0 - float(i) / n)
		var y := b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
		x2 = x1
		x1 = x
		y2 = y1
		y1 = y
		var g := peak * pow(0.0001 / peak, float(i) / n)
		var idx := start + i
		if idx < buf.size():
			buf[idx] += y * g


func _to_stream(buf: PackedFloat32Array) -> AudioStreamWAV:
	var bytes := PackedByteArray()
	bytes.resize(buf.size() * 2)
	for i in buf.size():
		bytes.encode_s16(i * 2, int(clampf(buf[i], -1.0, 1.0) * 32767.0))
	var s := AudioStreamWAV.new()
	s.format = AudioStreamWAV.FORMAT_16_BITS
	s.mix_rate = RATE
	s.stereo = false
	s.data = bytes
	return s


# ------------------------------------------------------------------- music --

## `intensity` (0..1) is the battle heat: faster melody, brighter notes, a
## bass pulse and war drums when a big fight is on. Same logic as the TS.
func updateMusic(dt: float, playing: bool, intensity: float = 0.0) -> void:
	if muted or musicVol <= 0.0 or not playing:
		return
	var heat := clampf(intensity, 0.0, 1.0)
	barTimer -= dt
	if barTimer <= 0.0:
		barTimer = 4.4 * (1.0 - heat * 0.3)
		chordIdx = (chordIdx + 1) % chords.size()
		_layChord(chords[chordIdx], heat, barTimer)
	musicTimer -= dt
	if musicTimer <= 0.0:
		musicTimer = (0.95 + _rng.randf() * 0.75) * (1.0 - heat * 0.5)
		if _rng.randf() > 0.16:
			_playMelodyNote(heat)
	if heat > 0.18:
		drumTimer -= dt
		if drumTimer <= 0.0:
			drumTimer = 0.62 - heat * 0.26
			_drumHit(0.10 + heat * 0.14)
	else:
		drumTimer = 0.0


func _play_music(s: AudioStreamWAV) -> void:
	if s == null:
		return # not rendered yet: a rest
	var p: AudioStreamPlayer = _music_players[_next_music]
	_next_music = (_next_music + 1) % _music_players.size()
	p.stream = s
	p.play()


## A sustained "swell" note: linear rise over `rise`, exponential fall to `end`.
func _swell(freq: float, ty: String, peak: float, rise: float, end: float, stop: float) -> PackedFloat32Array:
	var n := int(stop * RATE)
	var buf := PackedFloat32Array()
	buf.resize(n)
	var phase := 0.0
	for i in n:
		var t := float(i) / RATE
		phase += freq / RATE
		var g := 0.0
		if t < rise:
			g = 0.0001 + (peak - 0.0001) * t / rise
		elif t < end:
			g = peak * pow(0.0001 / peak, (t - rise) / (end - rise))
		buf[i] = _osc(phase, ty) * g
	return buf


## A rendered phrase, or null while it is still being rendered. Rendering a
## phrase in GDScript takes up to ~0.2 s, which used to stall the frame every
## time the music reached a chord it hadn't played yet; so a missing phrase is
## rendered on a worker thread and this play is skipped — the music is
## generative, and a note not ready yet is simply a rest.
func _cached(key: String, make: Callable) -> AudioStreamWAV:
	_mutex.lock()
	var s = _music_cache.get(key)
	var start: bool = s == null and not _music_pending.has(key)
	if start:
		_music_pending[key] = true
	_mutex.unlock()
	if start:
		WorkerThreadPool.add_task(func():
			var st := _to_stream(make.call())
			_mutex.lock()
			_music_cache[key] = st
			_music_pending.erase(key)
			_mutex.unlock(), false, "music phrase")
	return s


func _layChord(semis: int, heat: float, dur: float) -> void:
	var base := root * pow(2.0, semis / 12.0)
	var h := snappedf(heat, 0.25)
	var d := snappedf(dur, 0.25)
	var key := "chord:%d:%.2f:%.2f" % [semis, h, d]
	var s := _cached(key, func():
		var a := _swell(base, "triangle", 0.07 + h * 0.03, d * 0.35, d + 0.6, d + 0.7)
		var b := _swell(base * 1.5, "sine", 0.05, d * 0.35, d + 0.6, d + 0.7)
		var c := _swell(base * 2.0, "sine", 0.03, d * 0.35, d + 0.6, d + 0.7)
		var bass := _swell(base * 0.5, "sine", 0.12 + h * 0.06, 0.3, d * 0.9, d + 0.1)
		for i in a.size():
			a[i] += b[i] + c[i] + (bass[i] if i < bass.size() else 0.0)
		return a)
	_play_music(s)


func _playMelodyNote(heat: float) -> void:
	var move := (-1 if _rng.randf() < 0.5 else 1) if _rng.randf() < 0.7 else (-2 if _rng.randf() < 0.5 else 2)
	lastStep = clampi(lastStep + move, 0, scale.size() - 1)
	var semis: int = chords[chordIdx] + scale[lastStep]
	var octv := 2 if _rng.randf() < 0.35 + heat * 0.2 else 1
	var freq := root * pow(2.0, semis / 12.0) * octv
	var h := snappedf(heat, 0.25)
	_play_music(_cached("mel:%d:%d:%.2f" % [semis, octv, h], func(): return _swell(freq, "sine", 0.16 + h * 0.08, 0.35, 2.0, 2.1)))


func _drumHit(peak: float) -> void:
	var p := snappedf(peak, 0.02)
	_play_music(_cached("drum:%.2f" % p, func():
		var buf := PackedFloat32Array()
		buf.resize(int(0.28 * RATE))
		_add_tone_drum(buf, p)
		# Its own noise source: this runs on a worker (see _cached).
		var rng := RandomNumberGenerator.new()
		rng.seed = int(p * 1000.0)
		_add_noise(buf, 0, 0.12, p * 0.5, 220, "lowpass", rng)
		return buf))


func _add_tone_drum(buf: PackedFloat32Array, peak: float) -> void:
	var phase := 0.0
	for i in buf.size():
		var t := float(i) / RATE
		var f := 120.0 * pow(48.0 / 120.0, minf(1.0, t / 0.18))
		phase += f / RATE
		buf[i] += sin(phase * TAU) * peak * pow(0.0001 / peak, minf(1.0, t / 0.26))
