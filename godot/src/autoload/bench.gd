## Command-line benchmarks that also run in exported release builds (which
## can't be pointed at a test scene). Does nothing unless asked:
##   BannerAndBlade --headless -- --bench-sim
## (needs a build that includes tests/, as the perf exports do).
extends Node

func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	if not args.has("--bench-sim"):
		return
	var path := "res://tests/test_world_perf.gd"
	if not ResourceLoader.exists(path):
		print("bench: tests/ not in this build")
		get_tree().quit(1)
		return
	var perf = load(path).new()
	perf.test_busy_tick_time()
	if ResourceLoader.exists("res://tests/test_native.gd"):
		var nat = load("res://tests/test_native.gd").new()
		nat.test_flow_field_matches_gdscript()
		nat.test_astar_matches_gdscript()
	print("bench failures: ", perf.failures)
	get_tree().quit()
