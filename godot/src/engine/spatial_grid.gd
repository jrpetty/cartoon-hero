## engine/spatialgrid.ts — a uniform spatial hash for neighbour queries,
## rebuilt each tick. Query results come back in the same order as the
## TypeScript build (cells row by row, items in insertion order), which the
## sim relies on for determinism.
class_name SpatialGrid
extends RefCounted

var cells: Dictionary = {}
var cellSize: float


func _init(cell_size: float = 96.0) -> void:
	cellSize = cell_size


func key(cx: int, cy: int) -> int:
	return (cx & 0xffff) | ((cy & 0xffff) << 16)


func clear() -> void:
	cells.clear()


func insert(item) -> void:
	var cx := int(floor(item.x / cellSize))
	var cy := int(floor(item.y / cellSize))
	var k := key(cx, cy)
	var arr = cells.get(k)
	if arr == null:
		arr = []
		cells[k] = arr
	arr.append(item)


## All items within `radius` of (x, y): conservative cell rect, then exact filter.
func query(x: float, y: float, radius: float) -> Array:
	var out: Array = []
	var c0x := int(floor((x - radius) / cellSize))
	var c0y := int(floor((y - radius) / cellSize))
	var c1x := int(floor((x + radius) / cellSize))
	var c1y := int(floor((y + radius) / cellSize))
	var r2 := radius * radius
	for cy in range(c0y, c1y + 1):
		for cx in range(c0x, c1x + 1):
			var arr = cells.get(key(cx, cy))
			if arr == null:
				continue
			for it in arr:
				if not it.alive:
					continue
				var dx: float = it.x - x
				var dy: float = it.y - y
				if dx * dx + dy * dy <= r2:
					out.append(it)
	return out
