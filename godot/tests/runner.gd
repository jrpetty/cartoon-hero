## Test runner. Runs every `tests/test_*.gd` (or the ones named after `--`):
##
##   godot --headless --path godot res://tests/run.tscn
##   godot --headless --path godot res://tests/run.tscn -- test_rng test_world
##
## A test file extends RefCounted and defines `func test_*()` methods; each
## gets a fresh instance. Assert with the helpers on `TestCase` (extend it).
## Prints a summary and exits non-zero on any failure. A runtime script error
## inside a test (which aborts the test function without touching `failures`)
## also fails it.
extends Node


## Collects runtime script errors, so a test that dies halfway is not reported
## as passing just because it never reached a failing assertion.
class ScriptErrorTrap extends Logger:
	var errors: Array = []

	func _log_error(function: String, file: String, line: int, code: String, rationale: String, editor_notify: bool, error_type: int, script_backtraces: Array) -> void:
		if error_type == ERROR_TYPE_SCRIPT:
			errors.append("script error at %s:%d: %s" % [file, line, code if rationale == "" else rationale])


func _ready() -> void:
	var wanted: Array = OS.get_cmdline_user_args()
	var files: Array = []
	var dir := DirAccess.open("res://tests")
	for f in dir.get_files():
		if f.begins_with("test_") and f.ends_with(".gd"):
			var name := f.get_basename()
			if wanted.is_empty() or wanted.has(name):
				files.append(f)
	files.sort()
	var passed := 0
	var failed := 0
	var t0 := Time.get_ticks_msec()
	var trap := ScriptErrorTrap.new()
	OS.add_logger(trap)
	for f in files:
		var script: GDScript = load("res://tests/" + f)
		if script == null or not script.can_instantiate():
			print("  ✗ %s: failed to load (parse error?)" % f)
			failed += 1
			continue
		var methods: Array = []
		for m in script.get_script_method_list():
			if String(m.name).begins_with("test_") and not methods.has(m.name):
				methods.append(m.name)
		for m in methods:
			var inst = script.new()
			var t1 := Time.get_ticks_msec()
			trap.errors.clear()
			inst.call(m)
			var ms := Time.get_ticks_msec() - t1
			inst.failures.append_array(trap.errors)
			if inst.failures.is_empty():
				passed += 1
				print("  ✓ %s.%s (%d ms)" % [f.get_basename(), m, ms])
			else:
				failed += 1
				print("  ✗ %s.%s" % [f.get_basename(), m])
				for msg in inst.failures:
					print("      " + msg)
	OS.remove_logger(trap)
	print("%d passed, %d failed (%d ms)" % [passed, failed, Time.get_ticks_msec() - t0])
	get_tree().quit(1 if failed > 0 else 0)
