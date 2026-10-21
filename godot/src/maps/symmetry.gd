## Symmetry: every cell a stroke should also touch.
##
## Port of game/src/maps/symmetry.ts. The SYMMETRIES list (id/label/desc) is
## data and lives in `C.SYMMETRIES`.
##
## This lives beside the map data rather than in the editor UI because two
## different things need it — the editor's brush, and the map script, which
## mirrors whole placements rather than single cells. Neither should have to
## import a screen to find out what "rot180" means.
class_name Symmetry
extends RefCounted

## SymmetryId: "none" | "mirrorX" | "mirrorY" | "rot180" | "quad" | "radial3" |
## "radial6" | "radial8" (plain strings).


## Every cell a stroke at (cx,cy) should also touch, as [cx, cy] int pairs.
## Radial symmetry rotates about the map's centre, which is why it needs the
## dimensions.
static func symmetryCells(sym: String, cx: int, cy: int, cols: int, rows: int) -> Array:
	var out: Array = [[cx, cy]]
	var mx := cols - 1 - cx
	var my := rows - 1 - cy
	match sym:
		"mirrorX":
			_push(out, mx, cy, cols, rows)
		"mirrorY":
			_push(out, cx, my, cols, rows)
		"rot180":
			_push(out, mx, my, cols, rows)
		"quad":
			_push(out, mx, cy, cols, rows)
			_push(out, cx, my, cols, rows)
			_push(out, mx, my, cols, rows)
		"radial3", "radial6", "radial8":
			var n := 3 if sym == "radial3" else (6 if sym == "radial6" else 8)
			var ox := float(cols - 1) / 2.0
			var oy := float(rows - 1) / 2.0
			var dx := cx - ox
			var dy := cy - oy
			for k in range(1, n):
				var a := (float(k) / n) * PI * 2.0
				_push(out, ox + dx * JSMath.cos(a) - dy * JSMath.sin(a), oy + dx * JSMath.sin(a) + dy * JSMath.cos(a), cols, rows)
		_:
			pass
	return out


static func _push(out: Array, x: float, y: float, cols: int, rows: int) -> void:
	var rx := JS.roundi(x)
	var ry := JS.roundi(y)
	if rx < 0 or ry < 0 or rx >= cols or ry >= rows:
		return
	for p in out:
		if p[0] == rx and p[1] == ry:
			return
	out.append([rx, ry])
