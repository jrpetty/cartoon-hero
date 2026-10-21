extends TestCase
# The native extension gives exactly the GDScript answers, much faster.

func _maps() -> Array:
	return [SimTestUtil.build({"map": ["highlands", 80808, 8], "seed": 80808}),
		SimTestUtil.build({"map": ["black_forest", 11, 2], "seed": 11}),
		SimTestUtil.build({"map": ["riverlands", 23, 4], "seed": 23})]


func test_extension_loads() -> void:
	check(ClassDB.class_exists("BBNative"), "BBNative not loaded")
	check(Native.available(), "Native.available()")


func test_flow_field_matches_gdscript() -> void:
	if not Native.available():
		return
	var rng := RNG.new(5)
	var tn := 0
	var tg := 0
	for w in _maps():
		for k in 4:
			var gx := rng.range(0, w.worldW)
			var gy := rng.range(0, w.worldH)
			var t0 := Time.get_ticks_usec()
			var a := FlowField.new(w.grid, gx, gy)
			tn += Time.get_ticks_usec() - t0
			Native.enabled = false
			t0 = Time.get_ticks_usec()
			var b := FlowField.new(w.grid, gx, gy)
			tg += Time.get_ticks_usec() - t0
			Native.enabled = true
			check(a.cost == b.cost, "cost field differs")
			check(a.dirX == b.dirX and a.dirY == b.dirY, "directions differ")
	print("    flow field: native %.2f ms, GDScript %.2f ms per build" % [tn / 12000.0, tg / 12000.0])


func test_astar_matches_gdscript() -> void:
	if not Native.available():
		return
	var rng := RNG.new(9)
	var tn := 0
	var tg := 0
	var n := 0
	for w in _maps():
		for k in 60:
			var sx := rng.range(0, w.worldW)
			var sy := rng.range(0, w.worldH)
			var tx := rng.range(0, w.worldW)
			var ty := rng.range(0, w.worldH)
			var t0 := Time.get_ticks_usec()
			var a = AStar.findPath(w.grid, sx, sy, tx, ty)
			tn += Time.get_ticks_usec() - t0
			Native.enabled = false
			t0 = Time.get_ticks_usec()
			var b = AStar.findPath(w.grid, sx, sy, tx, ty)
			tg += Time.get_ticks_usec() - t0
			Native.enabled = true
			n += 1
			check(a == b, "path %d differs" % n)
	print("    A*: native %.3f ms, GDScript %.3f ms per search" % [tn / 1000.0 / n, tg / 1000.0 / n])
