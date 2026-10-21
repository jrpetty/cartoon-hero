extends TestCase
# main.gd and the engine scripts load (parse and compile) cleanly.


func test_main_compiles() -> void:
	for path in ["res://src/main.gd", "res://src/engine/fullscreen.gd"]:
		var s = load(path)
		check(s is GDScript and (s as GDScript).can_instantiate(), "%s compiles" % path)
