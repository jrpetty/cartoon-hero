## Access to the optional BBNative GDExtension (godot/native/): native, bit-
## identical versions of the sim's heaviest pure routines. Desktop builds ship
## the library; the web build (and any platform without it) runs the GDScript
## versions, which give exactly the same answers — the parity tests run both.
##
## `Native.enabled` can be switched off to force the GDScript paths (tests do,
## to compare).
class_name Native

static var enabled := true
static var _checked := false
static var _present := false
static var _obj: Object = null


static func available() -> bool:
	if not enabled:
		return false
	if not _checked:
		_checked = true
		_present = ClassDB.class_exists("BBNative")
		if _present:
			_obj = ClassDB.instantiate("BBNative")
	return _present


static func flow_build(blocked: PackedByteArray, cols: int, rows: int, start: int) -> Array:
	return _obj.call("flow_build", blocked, cols, rows, start)


static func astar_search(blocked: PackedByteArray, cols: int, rows: int, scx: int, scy: int, tcx: int, tcy: int, max_nodes: int) -> PackedInt32Array:
	return _obj.call("astar_search", blocked, cols, rows, scx, scy, tcx, tcy, max_nodes)
