extends TestCase
# Every rendering / UI script loads (parses and compiles) cleanly.

const DIRS := ["res://src/render", "res://src/ui"]


func test_scripts_compile() -> void:
	for d in DIRS:
		var dir := DirAccess.open(d)
		if dir == null:
			continue
		for f in dir.get_files():
			if not f.ends_with(".gd"):
				continue
			var s = load(d.path_join(f))
			check(s is GDScript and (s as GDScript).can_instantiate(), "%s/%s compiles" % [d, f])
