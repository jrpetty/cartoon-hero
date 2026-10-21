extends TestCase
# The foundation matches the TypeScript build bit for bit.

func test_content_loads() -> void:
	eq(C.TILE, 32, "TILE")
	eq(C.UNITS["villager"]["hp"], 40, "villager hp")
	check(C.BUILDINGS.has("town_center"), "town center defined")

func test_rng_matches_ts() -> void:
	# Compared as integers: GDScript can't parse every 17-digit literal exactly.
	var r := RNG.new(12345)
	var want := [4207900869, 1317490944, 2079646450, 3513001552, 2187978186]
	for w in want:
		eq(int(r.next() * 4294967296.0), w, "rng")
	eq(RNG.new(7).int_(1, 100), 2, "int_")
	eq(int(RNG.new(99).fork(3).next() * 4294967296.0), int(0.11283569410443306 * 4294967296.0), "fork")

func test_hypot_matches_v8() -> void:
	eq(JS.hypot(3.0, 4.0), 5.0)
	eq(JS.hypot(0.1, 0.2), 0.223606797749979)

func test_stable_sort() -> void:
	var a := [[3, "a"], [1, "b"], [3, "c"], [1, "d"], [2, "e"]]
	for i in 20:
		a.append([i % 3, str(i)])
	JS.stable_sort(a, func(p, q): return p[0] < q[0])
	for i in range(1, a.size()):
		check(a[i - 1][0] <= a[i][0], "sorted")
	# equal keys keep their order
	var ones := a.filter(func(p): return p[0] == 1).map(func(p): return p[1])
	eq(ones[0], "b")
	eq(ones[1], "d")

func test_js_round() -> void:
	eq(JS.round(2.5), 3)
	eq(JS.round(-2.5), -2)
	eq(JS.round(-2.6), -3)
