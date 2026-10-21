## pathfinding/steering.ts — local steering: separation from nearby units +
## arrival damping. Keeps units from stacking and makes group movement look
## organic. Operates on top of the path/flow-field desired direction.
class_name Steering


## Returns [x, y]: the desired velocity plus a push away from crowding units.
static func applySeparation(e: Entity, neighbors: Array, desiredX: float, desiredY: float, strength: float = 60.0) -> Array:
	var sepX := 0.0
	var sepY := 0.0
	var ex := e.x
	var ey := e.y
	var er := e.radius
	# (Untyped loop variables: measurably faster in GDScript than `n: Entity`.)
	for n in neighbors:
		if n == e or not n.alive:
			continue
		if n.kind != T.Kind.Unit:
			continue
		var dx: float = ex - n.x
		var dy: float = ey - n.y
		var d2 := dx * dx + dy * dy
		var minDist: float = er + n.radius + 2.0
		if d2 < minDist * minDist and d2 > 0.0001:
			var d := sqrt(d2)
			var push := (minDist - d) / minDist
			sepX += (dx / d) * push
			sepY += (dy / d) * push
	return [desiredX + sepX * strength, desiredY + sepY * strength]


## Hard de-overlap pass so idle units never permanently stack.
static func resolveOverlaps(units: Array, spatial: SpatialGrid, dt: float) -> void:
	var k := minf(1.0, dt * 12.0)
	for e in units:
		if not e.alive or e.kind != T.Kind.Unit:
			continue
		var near := spatial.query(e.x, e.y, e.radius + 16.0)
		for n in near:
			if n == e or not n.alive or n.kind != T.Kind.Unit:
				continue
			var dx: float = e.x - n.x
			var dy: float = e.y - n.y
			var d2 := dx * dx + dy * dy
			var minDist: float = e.radius + n.radius
			if d2 < minDist * minDist:
				var d := sqrt(d2)
				if d == 0.0:
					d = 0.01
				var overlap := (minDist - d) * 0.5
				var px: float = (dx / d) * overlap * k
				var py: float = (dy / d) * overlap * k
				e.x += px
				e.y += py
				n.x -= px
				n.y -= py
