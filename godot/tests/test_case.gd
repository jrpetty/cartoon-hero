## Base for test files: `extends TestCase`, then `func test_something()`.
class_name TestCase
extends RefCounted

var failures: Array = []


func check(cond: bool, msg: String = "check failed") -> void:
	if not cond:
		failures.append(msg)


func eq(actual, expected, msg: String = "") -> void:
	if typeof(actual) in [TYPE_FLOAT, TYPE_INT] and typeof(expected) in [TYPE_FLOAT, TYPE_INT]:
		if float(actual) == float(expected):
			return
	elif actual == expected:
		return
	failures.append("%s expected %s, got %s" % [msg, str(expected), str(actual)])


func near(actual: float, expected: float, eps: float = 1e-9, msg: String = "") -> void:
	if absf(actual - expected) > eps:
		failures.append("%s expected %s ± %s, got %s" % [msg, str(expected), str(eps), str(actual)])


## Read a JSON fixture written by game/scripts/godot_parity.ts.
func fixture(name: String):
	var f := FileAccess.open("res://tests/fixtures/" + name, FileAccess.READ)
	if f == null:
		failures.append("missing fixture " + name)
		return null
	return JSON.parse_string(f.get_as_text())
