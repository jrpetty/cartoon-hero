// Native speed-ups for the Godot build of Banner & Blade.
//
// Each function here is a line-for-line port of a GDScript routine (itself a
// port of the TypeScript game), doing the same floating-point operations in
// the same order, so it returns bit-identical results. The GDScript versions
// stay as the fallback (the web build, or if the library fails to load), and
// the parity tests run both.
#pragma once

#include <godot_cpp/classes/ref_counted.hpp>
#include <godot_cpp/variant/packed_byte_array.hpp>
#include <godot_cpp/variant/packed_float32_array.hpp>
#include <godot_cpp/variant/packed_float64_array.hpp>
#include <godot_cpp/variant/packed_int32_array.hpp>
#include <godot_cpp/variant/array.hpp>

namespace godot {

class BBNative : public RefCounted {
	GDCLASS(BBNative, RefCounted)

protected:
	static void _bind_methods();

public:
	// pathfinding/flow_field.gd build(): Dijkstra integration field + directions.
	// Returns [cost: PackedFloat64Array, dirX: PackedFloat32Array, dirY: PackedFloat32Array].
	static Array flow_build(const PackedByteArray &blocked, int cols, int rows, int start);

	// pathfinding/astar.gd findPath() search loop. Returns the cell indices from
	// start to goal, or an empty array when the search fails (no path within
	// maxNodes) — the caller then returns null, as the GDScript does.
	static PackedInt32Array astar_search(const PackedByteArray &blocked, int cols, int rows,
			int scx, int scy, int tcx, int tcy, int max_nodes);

	static int version();
};

} // namespace godot
