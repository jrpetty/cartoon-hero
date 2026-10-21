## Mouse + keyboard + touch input tracking. The game polls this each frame and
## also subscribes to discrete events (click, drag end, wheel, pan, pinch) via
## callbacks. Touch maps one finger to the left mouse (tap = click, drag = box /
## UI drag), two fingers to camera pan + pinch-zoom, and a long-press to a
## right-click (contextual command) while in a match.
##
## Port of game/src/engine/input.ts (class_name GameInput: `Input` is Godot's
## own singleton). Same fields, callbacks and handlers as the TS. What changes
## is where events come from: instead of DOM listeners, the main loop feeds
## every Godot event in, and ticks the long-press timer once a frame:
##
##   func _input(event):  game_input.handleEvent(event)
##   func _process(dt):   game_input.update()
##   func _notification(what):
##       if what == NOTIFICATION_APPLICATION_FOCUS_OUT: game_input.handleBlur()
##
## handleEvent translates each Godot event into the DOM-shaped dictionary the
## TS handler received (button 0/1/2, clientX/clientY, deltaY, key names as
## KeyboardEvent.key spells them — see domKey), so the handlers below read line
## for line against the TS. Godot synthesises mouse events from touches (and
## touches from the mouse, if that project setting is on); those arrive with
## device == InputEvent.DEVICE_ID_EMULATION and are ignored, because touch is
## handled natively here, as the browser build does.
class_name GameInput
extends RefCounted

var mx := 0.0 # screen mouse position
var my := 0.0
var leftDown := false
var rightDown := false
var middleDown := false
var shift := false
var ctrl := false
var alt := false
var keys: Dictionary = {} # Set<string>: held keys, single characters lower-cased
var wheelDelta := 0.0
var usingTouch := false # true once the player has used touch (suppresses edge-scroll)
var longPressRight := false # set by the game: enable long-press → right-click (match only)

## DragBox: {active: bool, x0, y0, x1, y1: float}.
var drag := {"active": false, "x0": 0.0, "y0": 0.0, "x1": 0.0, "y1": 0.0}
var dragStartX := 0.0
var dragStartY := 0.0
var leftPressTime := 0.0
const DRAG_THRESHOLD := 6.0

# Callbacks set by the game layer (unset = Callable()).
var onLeftClick := Callable() # (sx, sy)
var onLeftDouble := Callable() # (sx, sy)
var onDragEnd := Callable() # (box: Dictionary)
var onRightClick := Callable() # (sx, sy)
var onWheel := Callable() # (sx, sy, delta)
var onKeyDown := Callable() # (key: String)
var onPan := Callable() # (dxScreen, dyScreen) — two-finger drag
var onPinch := Callable() # (cx, cy, factor) — pinch zoom

# Touch state. Insertion-ordered like the JS Map: identifier → {x, y}.
var touches: Dictionary = {}
var pinching := false
var lastPinchDist := 0.0
var lastPinchMid: Array = [0.0, 0.0]
# The TS arms a setTimeout; here the deadline is polled by update().
var longPressAt := -1.0 # performance.now() ms at which it fires, or -1
var longPressX := 0.0
var longPressY := 0.0
var longPressed := false

var lastClickTime := 0.0

## The TS maps client coordinates into the canvas's internal resolution (the
## canvas may be CSS-scaled on small screens). Event positions in Godot are
## already in viewport pixels; set these if the game renders at a different
## virtual resolution than the window.
var canvasOffset := Vector2.ZERO
var canvasScale := Vector2.ONE

## performance.now() in ms. Tests replace it with a fake clock.
var nowFn := Callable()

# Which name each held physical key went down as, so its release removes the
# same entry (Godot doesn't carry the produced character on a release).
var _downNames: Dictionary = {}


func now() -> float:
	return float(nowFn.call()) if nowFn.is_valid() else Time.get_ticks_usec() / 1000.0


## The window lost focus: drop every held key and gesture.
func handleBlur() -> void:
	keys.clear()
	_downNames.clear()
	leftDown = false
	rightDown = false
	middleDown = false
	drag.active = false
	touches.clear()
	pinching = false
	clearLongPress()


## Map a client point to the canvas's internal (virtual) coordinate space.
func toCanvas(clientX: float, clientY: float) -> Array:
	return [(clientX - canvasOffset.x) * canvasScale.x, (clientY - canvasOffset.y) * canvasScale.y]


func rel(e: Dictionary) -> Array:
	return toCanvas(e.clientX, e.clientY)


# ------------------------------------------------------------ Godot events --

## Feed one Godot input event in (from _input or _unhandled_input).
func handleEvent(event: InputEvent) -> void:
	if event is InputEventMouseMotion:
		if event.device == InputEvent.DEVICE_ID_EMULATION:
			return
		handleMove({"clientX": event.position.x, "clientY": event.position.y})
	elif event is InputEventMouseButton:
		if event.device == InputEvent.DEVICE_ID_EMULATION:
			return
		var b: int = event.button_index
		if b == MOUSE_BUTTON_WHEEL_UP or b == MOUSE_BUTTON_WHEEL_DOWN:
			if event.pressed:
				# A wheel notch is deltaY ±100 in the browser (Chrome, pixel mode);
				# `factor` carries the fraction a precise trackpad scrolled.
				var f: float = event.factor if event.factor > 0.0 else 1.0
				handleWheel({"deltaY": (100.0 if b == MOUSE_BUTTON_WHEEL_DOWN else -100.0) * f})
			return
		var dom := 0 if b == MOUSE_BUTTON_LEFT else (2 if b == MOUSE_BUTTON_RIGHT else (1 if b == MOUSE_BUTTON_MIDDLE else -1))
		if dom < 0:
			return
		var e := {
			"button": dom, "clientX": event.position.x, "clientY": event.position.y,
			"shiftKey": event.shift_pressed, "ctrlKey": event.ctrl_pressed, "metaKey": event.meta_pressed,
		}
		if event.pressed:
			handleDown(e)
		else:
			handleUp(e)
	elif event is InputEventKey:
		var e := {
			"key": _keyFor(event), "shiftKey": event.shift_pressed, "ctrlKey": event.ctrl_pressed,
			"metaKey": event.meta_pressed, "altKey": event.alt_pressed,
		}
		if event.pressed:
			handleKeyDown(e)
		else:
			handleKeyUp(e)
	elif event is InputEventScreenTouch:
		if event.device == InputEvent.DEVICE_ID_EMULATION:
			return
		var t := {"identifier": event.index, "clientX": event.position.x, "clientY": event.position.y}
		if event.pressed:
			handleTouchStart([t])
		else:
			handleTouchEnd([t]) # a release or a cancel
	elif event is InputEventScreenDrag:
		if event.device == InputEvent.DEVICE_ID_EMULATION:
			return
		handleTouchMove([{"identifier": event.index, "clientX": event.position.x, "clientY": event.position.y}])
	elif event is InputEventMagnifyGesture:
		# A trackpad pinch: the same gesture as a two-finger touch pinch.
		var p := toCanvas(event.position.x, event.position.y)
		if onPinch.is_valid():
			onPinch.call(p[0], p[1], event.factor)
	elif event is InputEventPanGesture:
		# A trackpad two-finger scroll, which the browser reports as a wheel.
		if event.delta.y != 0.0:
			handleWheel({"deltaY": event.delta.y * 100.0})


## The browser's KeyboardEvent.key for a Godot key event: the character typed
## ("a", "A", "+", " ") or the key's DOM name ("Enter", "ArrowUp", "F3",
## "Shift", "Control").
static func domKey(e: InputEventKey) -> String:
	var k: int = e.keycode
	if k == KEY_NONE:
		k = e.physical_keycode
	if NAMED_KEYS.has(k):
		return NAMED_KEYS[k]
	if k >= KEY_F1 and k <= KEY_F35:
		return "F%d" % (k - KEY_F1 + 1)
	var u: int = e.unicode
	if u >= 32 and u != 127:
		return String.chr(u)
	# No character (Ctrl held, or a release): rebuild it from the key code.
	if k >= KEY_A and k <= KEY_Z:
		return String.chr(k) if e.shift_pressed else String.chr(k + 32)
	if (k >= KEY_0 and k <= KEY_9) or (k >= 32 and k < 127):
		return String.chr(k)
	return "Unidentified"


const NAMED_KEYS := {
	KEY_ESCAPE: "Escape", KEY_TAB: "Tab", KEY_BACKTAB: "Tab", KEY_BACKSPACE: "Backspace",
	KEY_ENTER: "Enter", KEY_KP_ENTER: "Enter", KEY_INSERT: "Insert", KEY_DELETE: "Delete",
	KEY_PAUSE: "Pause", KEY_PRINT: "PrintScreen", KEY_CLEAR: "Clear", KEY_HOME: "Home",
	KEY_END: "End", KEY_LEFT: "ArrowLeft", KEY_UP: "ArrowUp", KEY_RIGHT: "ArrowRight",
	KEY_DOWN: "ArrowDown", KEY_PAGEUP: "PageUp", KEY_PAGEDOWN: "PageDown", KEY_SHIFT: "Shift",
	KEY_CTRL: "Control", KEY_META: "Meta", KEY_ALT: "Alt", KEY_CAPSLOCK: "CapsLock",
	KEY_NUMLOCK: "NumLock", KEY_SCROLLLOCK: "ScrollLock", KEY_MENU: "ContextMenu",
}


func _keyFor(e: InputEventKey) -> String:
	var code: int = e.physical_keycode if e.physical_keycode != KEY_NONE else e.keycode
	if e.pressed:
		var name := domKey(e)
		_downNames[code] = name
		return name
	var name: String = _downNames.get(code, domKey(e))
	_downNames.erase(code)
	return name


# --------------------------------------------------------- the TS handlers --

func handleMove(e: Dictionary) -> void:
	var p := rel(e)
	var x: float = p[0]
	var y: float = p[1]
	mx = x
	my = y
	if leftDown:
		var d := JS.hypot(x - dragStartX, y - dragStartY)
		if d > DRAG_THRESHOLD:
			drag.active = true
		if drag.active:
			drag.x1 = x
			drag.y1 = y


func handleDown(e: Dictionary) -> void:
	var p := rel(e)
	var x: float = p[0]
	var y: float = p[1]
	mx = x
	my = y
	shift = e.get("shiftKey", false)
	ctrl = e.get("ctrlKey", false) or e.get("metaKey", false)
	if e.button == 0:
		leftDown = true
		dragStartX = x
		dragStartY = y
		drag = {"active": false, "x0": x, "y0": y, "x1": x, "y1": y}
		leftPressTime = now()
	elif e.button == 2:
		rightDown = true
	elif e.button == 1:
		middleDown = true


func handleUp(e: Dictionary) -> void:
	if e.button == 0:
		# Up is heard on the whole window (so a drag can end off the canvas),
		# but a click only counts if the press began on the canvas — otherwise
		# clicking a DOM overlay such as the multiplayer lobby also clicks
		# whatever menu button is drawn underneath it.
		if not leftDown:
			return
		leftDown = false
		if drag.active:
			if onDragEnd.is_valid():
				onDragEnd.call(drag.duplicate())
		else:
			var t := now()
			if t - lastClickTime < 280.0:
				if onLeftDouble.is_valid():
					onLeftDouble.call(mx, my)
			else:
				if onLeftClick.is_valid():
					onLeftClick.call(mx, my)
			lastClickTime = t
		drag.active = false
	elif e.button == 2:
		rightDown = false
		if onRightClick.is_valid():
			onRightClick.call(mx, my)
	elif e.button == 1:
		middleDown = false


func handleWheel(e: Dictionary) -> void:
	if onWheel.is_valid():
		onWheel.call(mx, my, e.deltaY)


func handleKeyDown(e: Dictionary) -> void:
	shift = e.get("shiftKey", false)
	ctrl = e.get("ctrlKey", false) or e.get("metaKey", false)
	alt = e.get("altKey", false)
	# (The browser build stops the page from acting on Alt+digit and Tab here;
	# nothing in Godot takes them, so there is nothing to prevent.)
	var key: String = e.key
	var k := key.to_lower() if key.length() == 1 else key
	keys[k] = true
	if onKeyDown.is_valid():
		onKeyDown.call(key)


func handleKeyUp(e: Dictionary) -> void:
	shift = e.get("shiftKey", false)
	ctrl = e.get("ctrlKey", false) or e.get("metaKey", false)
	alt = e.get("altKey", false)
	var key: String = e.key
	var k := key.to_lower() if key.length() == 1 else key
	keys.erase(k)


# ----------------------------------------------------------------- touch --
# Each handler takes the event's changed touches, as the TS does:
# Array of {identifier, clientX, clientY}.

func relT(t: Dictionary) -> Array:
	return toCanvas(t.clientX, t.clientY)


func clearLongPress() -> void:
	longPressAt = -1.0


func armLongPress(x: float, y: float) -> void:
	clearLongPress()
	if not longPressRight:
		return
	longPressAt = now() + 450.0
	longPressX = x
	longPressY = y


## Call once a frame: fires the long-press when its 450 ms are up (the TS's
## setTimeout).
func update() -> void:
	if longPressAt < 0.0 or now() < longPressAt:
		return
	longPressAt = -1.0
	if touches.size() == 1 and not drag.active and not pinching:
		longPressed = true
		if onRightClick.is_valid():
			onRightClick.call(longPressX, longPressY)


func _firstTwo() -> Array:
	return touches.values().slice(0, 2)


func handleTouchStart(changed: Array) -> void:
	usingTouch = true
	for t in changed:
		var p := relT(t)
		touches[t.identifier] = {"x": p[0], "y": p[1]}
	if touches.size() == 1:
		var c: Dictionary = touches.values()[0]
		mx = c.x
		my = c.y
		leftDown = true
		dragStartX = c.x
		dragStartY = c.y
		drag = {"active": false, "x0": c.x, "y0": c.y, "x1": c.x, "y1": c.y}
		leftPressTime = now()
		longPressed = false
		armLongPress(c.x, c.y)
	elif touches.size() >= 2:
		# Two fingers → pinch/pan; abandon any single-finger gesture.
		leftDown = false
		drag.active = false
		clearLongPress()
		var pts := _firstTwo()
		pinching = true
		lastPinchDist = JS.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y)
		lastPinchMid = [(pts[0].x + pts[1].x) / 2.0, (pts[0].y + pts[1].y) / 2.0]


func handleTouchMove(changed: Array) -> void:
	for t in changed:
		var c = touches.get(t.identifier)
		if c != null:
			var p := relT(t)
			c.x = p[0]
			c.y = p[1]
	if touches.size() == 1 and leftDown and not pinching:
		var c: Dictionary = touches.values()[0]
		mx = c.x
		my = c.y
		var d := JS.hypot(c.x - dragStartX, c.y - dragStartY)
		if d > DRAG_THRESHOLD:
			drag.active = true
			clearLongPress()
		if drag.active:
			drag.x1 = c.x
			drag.y1 = c.y
	elif touches.size() >= 2:
		var pts := _firstTwo()
		var dist := JS.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y)
		var mid: Array = [(pts[0].x + pts[1].x) / 2.0, (pts[0].y + pts[1].y) / 2.0]
		mx = mid[0]
		my = mid[1]
		if lastPinchDist > 0.0:
			var factor := dist / lastPinchDist
			if absf(factor - 1.0) > 0.002 and onPinch.is_valid():
				onPinch.call(mid[0], mid[1], factor)
			if onPan.is_valid():
				onPan.call(mid[0] - lastPinchMid[0], mid[1] - lastPinchMid[1])
		lastPinchDist = dist
		lastPinchMid = mid


func handleTouchEnd(changed: Array) -> void:
	for t in changed:
		touches.erase(t.identifier)
	if touches.size() == 0:
		clearLongPress()
		if pinching:
			pinching = false
			lastPinchDist = 0.0
		elif leftDown:
			if drag.active:
				if onDragEnd.is_valid():
					onDragEnd.call(drag.duplicate())
			elif not longPressed:
				var t := now()
				if t - lastClickTime < 280.0:
					if onLeftDouble.is_valid():
						onLeftDouble.call(mx, my)
				elif onLeftClick.is_valid():
					onLeftClick.call(mx, my)
				lastClickTime = t
		leftDown = false
		drag.active = false
		longPressed = false
	elif pinching:
		# A finger lifted mid-pinch — stay neutral until all fingers are up.
		leftDown = false
		lastPinchDist = 0.0


func isDown(key: String) -> bool:
	return keys.has(key)
