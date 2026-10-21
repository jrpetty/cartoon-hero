## An order (sim/types.ts `Order`). TS builds these as object literals; here
## `Order.make(kind, tx, ty, target)` does the same. `queue` is the shift-queue
## (empty = none, which TS writes as `undefined`); `slot` marks a formation move.
class_name Order
extends RefCounted

var kind: int = T.OrderKind.Idle
var tx: float = 0.0
var ty: float = 0.0
var target: int = -1
var queue: Array = []
var slot: bool = false


static func make(k: int, x: float = 0.0, y: float = 0.0, tgt: int = -1, q: Array = []) -> Order:
	var o := Order.new()
	o.kind = k
	o.tx = x
	o.ty = y
	o.target = tgt
	o.queue = q if not q.is_empty() else []
	return o


static func idle() -> Order:
	return Order.make(T.OrderKind.Idle)


## `{ ...o }` — a shallow copy (the queue array is shared, as in JS).
func copy() -> Order:
	var o := Order.make(kind, tx, ty, target, queue)
	o.slot = slot
	return o
