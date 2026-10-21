extends TestCase
# The wire's numbers: a Godot client must read every double a browser writes
# back to the same double, and write doubles a browser reads back exactly —
# lockstep cross-play (and Godot-to-Godot through the Node relay, which
# re-serialises every turn) depends on it.


static func bits(x: float) -> String:
	return PackedFloat64Array([x]).to_byte_array().hex_encode()


func test_reads_every_browser_double_exactly() -> void:
	var fx = fixture("net_floats.json")
	if fx == null:
		return
	var bad := 0
	var godot_bad := 0
	for row in fx:
		var text: String = row[1]
		var got = WireJSON.parse("{\"x\":%s}" % text)
		if bits(float(got.x)) != row[0]:
			bad += 1
			if bad <= 5:
				check(false, "%s read as %s, want %s" % [text, bits(float(got.x)), row[0]])
		var plain = JSON.parse_string("{\"x\":%s}" % text)
		if bits(float(plain.x)) != row[0]:
			godot_bad += 1
	eq(bad, 0, "WireJSON misread doubles")
	# The reason this exists: Godot's own reader gets a good share of them wrong.
	check(godot_bad > 0, "Godot's JSON reader is exact now (%d wrong) — WireJSON could go" % godot_bad)
	print("      (Godot's JSON.parse misreads %d of %d browser doubles; WireJSON 0)" % [godot_bad, fx.size()])


func test_toDouble_agrees_on_the_awkward_ones() -> void:
	eq(bits(WireJSON.toDouble("0.1")), bits(0.1), "0.1")
	eq(WireJSON.toDouble("0"), 0.0, "0")
	check(is_inf(WireJSON.toDouble("1e400")), "overflow is Infinity")
	eq(WireJSON.toDouble("1e-400"), 0.0, "underflow is 0")
	eq(WireJSON.toDouble("-2.5"), -2.5, "negative")
	eq(WireJSON.toDouble("1E3"), 1000.0, "capital E")
	eq(WireJSON.toDouble("12.50e-1"), 1.25, "trailing zeros, exponent")
	eq(WireJSON.toDouble("0.000"), 0.0, "zeros")


func test_keeps_structure_strings_and_order() -> void:
	var text := "{\"t\":\"turn\",\"turn\":{\"tick\":12,\"team\":1,\"cmds\":[{\"t\":\"move\",\"ids\":[3,4,5],\"x\":612.3456789123,\"y\":-0.0032921791076660155,\"queue\":false,\"name\":\"a \\\"quoted\\\" 1.5e3 string\"}]}}"
	var a = WireJSON.parse(text)
	var b = JSON.parse_string(text)
	eq(a.keys(), b.keys(), "keys in order")
	eq(a.turn.cmds[0].keys(), b.turn.cmds[0].keys(), "nested keys in order")
	eq(a.turn.cmds[0].name, "a \"quoted\" 1.5e3 string", "strings untouched")
	eq(a.turn.cmds[0].ids, [3.0, 4.0, 5.0], "ints are floats, as in JS")
	eq(a.turn.tick, 12.0, "tick")
	check(a.turn.tick is float, "numbers are floats")
	eq(bits(a.turn.cmds[0].y), "3333333333f86abf", "the exact double")
	eq(WireJSON.parse("not json"), null, "malformed is null")


func test_writes_doubles_a_browser_reads_back_exactly() -> void:
	# Godot's full-precision writer (17 significant digits) round-trips through
	# any correctly rounded reader: ours, and — checked with node — the browser's.
	var rng := RandomNumberGenerator.new()
	rng.seed = 99
	var vals: Array = []
	for i in 400:
		var x := rng.randf_range(-5000, 5000) if i % 2 == 0 else rng.randf() * pow(10.0, rng.randi_range(-20, 20))
		vals.append(x)
		eq(bits(WireJSON.parse(WireJSON.stringify({"x": x})).x), bits(x), "own round trip %s" % var_to_str(x))
	var out: Array = []
	var lines := PackedStringArray()
	for x in vals:
		lines.append(bits(x) + " " + WireJSON.stringify({"x": x}))
	var path := OS.get_cache_dir().path_join("bb_wire_floats.txt")
	var f := FileAccess.open(path, FileAccess.WRITE)
	f.store_string("\n".join(lines))
	f.close()
	var js := "const fs=require('fs');let bad=0;for(const l of fs.readFileSync(process.argv[1],'utf8').split('\\n')){const i=l.indexOf(' ');const v=JSON.parse(l.slice(i+1)).x;if(Buffer.from(new Float64Array([v]).buffer).toString('hex')!==l.slice(0,i))bad++;}console.log(bad)"
	var code := OS.execute("node", ["-e", js, path], out, true)
	if code != 0:
		print("      (node not available — skipped the browser half)")
		return
	eq(String(out[0]).strip_edges(), "0", "doubles the browser misread")


func test_a_big_message_parses_quickly() -> void:
	# A start message carries a published map's code: up to a few hundred KB.
	var code := "BBMAP1." + "AbCdEf0123".repeat(25000)
	var text := WireJSON.stringify({"t": "start", "seed": 123456789, "map": {"id": "custom_x", "name": "Big", "code": code}, "alliances": [0, 1]})
	var t0 := Time.get_ticks_msec()
	var m = WireJSON.parse(text)
	var ms := Time.get_ticks_msec() - t0
	eq(m.map.code, code, "the code survives")
	eq(m.seed, 123456789.0, "the seed")
	check(ms < 500, "took %d ms" % ms)
