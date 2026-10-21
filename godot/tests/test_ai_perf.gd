extends TestCase
# What the skirmish AI costs, in the match game/scripts/perf.ts plays: eight
# Knights on Highlands seed 80808 for twelve minutes, headless. Reports the
# average, median, p95, p99 and worst per-tick cost of world.tick() and of the
# eight AIs' update() — the same figures game/scripts/ai_perf.ts prints for
# the TS build — and checks, every tick, that it is the TS match being timed
# (world checksum and AI digest against ai_highlands8.json, recorded by
# game/scripts/godot_ai_parity.ts). Asserts only a generous ceiling on time,
# so a slow machine doesn't fail it.


static func _stats(us: PackedInt64Array) -> Dictionary:
	var sorted := us.duplicate()
	sorted.sort()
	var n := sorted.size()
	var sum := 0
	for v in sorted:
		sum += v
	var q := func(p: float) -> float:
		return sorted[mini(n - 1, floori(n * p))] / 1000.0
	return {
		"avg": sum / 1000.0 / maxi(1, n), "median": q.call(0.5), "p95": q.call(0.95),
		"p99": q.call(0.99), "worst": sorted[n - 1] / 1000.0 if n > 0 else 0.0,
	}


static func _line(name: String, s: Dictionary) -> String:
	return "    %-11s avg %.3f  median %.3f  p95 %.3f  p99 %.3f  worst %.2f  (ms)" % [
		name, s.avg, s.median, s.p95, s.p99, s.worst]


func test_eight_knights_twelve_minutes_on_highlands() -> void:
	var fx = fixture("ai_highlands8.json")
	if fx == null:
		return
	var r := AITestUtil.runScenario(fx, "highlands8", failures, true)
	eq(r.get("matched", -1), (fx.checksums as Array).size() - 1, "highlands8: last matching tick")
	if not r.has("tickUs"):
		return
	var tickUs: PackedInt64Array = r.tickUs
	var aiUs: PackedInt64Array = r.aiUs
	var both := PackedInt64Array()
	both.resize(tickUs.size())
	for i in tickUs.size():
		both[i] = tickUs[i] + aiUs[i]
	var st := _stats(tickUs)
	var sa := _stats(aiUs)
	print(_line("world.tick", st))
	print(_line("AI update", sa))
	print(_line("both", _stats(both)))
	check(st.avg + sa.avg < 200.0, "far too slow")
