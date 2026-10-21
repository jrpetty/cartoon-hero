extends TestCase
# The synthesizer renders every effect without errors, at sensible levels.

func test_every_effect_renders() -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = 7
	var t0 := Time.get_ticks_usec()
	for n in audio.SFX_NAMES:
		var buf: PackedFloat32Array = audio._mix(audio._recipe(n, rng), rng)
		check(buf.size() > 100, n + " has samples")
		var peak := 0.0
		for v in buf:
			peak = maxf(peak, absf(v))
		check(peak > 0.02 and peak < 1.5, "%s peak %.3f" % [n, peak])
	var ms := (Time.get_ticks_usec() - t0) / 1000.0
	print("    rendered %d effects in %.0f ms" % [audio.SFX_NAMES.size(), ms])

func test_music_note_renders() -> void:
	var buf: PackedFloat32Array = audio._swell(440.0, "sine", 0.16, 0.35, 2.0, 2.1)
	eq(buf.size(), int(2.1 * audio.RATE))
