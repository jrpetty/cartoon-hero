## Converts between world space and screen space, with pan + zoom.
## Port of game/src/engine/camera.ts. Scalars, not Vector2: the camera feeds
## world coordinates into commands, which the simulation must see in 64 bits.
class_name Camera
extends RefCounted

var x := 0.0 # world coord at screen center
var y := 0.0
var zoom := 1.0
var minZoom := 0.4
var maxZoom := 1.8
var viewW := 1280.0
var viewH := 720.0
var worldW := 4000.0
var worldH := 4000.0


func setViewport(w: float, h: float) -> void:
	viewW = w
	viewH = h
	clampToWorld()


func setWorld(w: float, h: float) -> void:
	worldW = w
	worldH = h


func worldToScreenX(wx: float) -> float:
	return (wx - x) * zoom + viewW / 2.0


func worldToScreenY(wy: float) -> float:
	return (wy - y) * zoom + viewH / 2.0


func screenToWorldX(sx: float) -> float:
	return (sx - viewW / 2.0) / zoom + x


func screenToWorldY(sy: float) -> float:
	return (sy - viewH / 2.0) / zoom + y


func pan(dxWorld: float, dyWorld: float) -> void:
	x += dxWorld
	y += dyWorld
	clampToWorld()


## Zoom toward a screen anchor point so the world point under the cursor stays put.
func zoomAt(sx: float, sy: float, factor: float) -> void:
	var wx := screenToWorldX(sx)
	var wy := screenToWorldY(sy)
	zoom = MathU.clamp(zoom * factor, minZoom, maxZoom)
	# Re-center so (wx,wy) stays under (sx,sy)
	x = wx - (sx - viewW / 2.0) / zoom
	y = wy - (sy - viewH / 2.0) / zoom
	clampToWorld()


func centerOn(wx: float, wy: float) -> void:
	x = wx
	y = wy
	clampToWorld()


func clampToWorld() -> void:
	# Allow a little overscroll margin so edges feel natural.
	var margin := 200.0
	x = MathU.clamp(x, -margin, worldW + margin)
	y = MathU.clamp(y, -margin, worldH + margin)
