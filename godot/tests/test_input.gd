extends TestCase
# Port of game/src/engine/input.test.ts, plus the Godot-event adapter
# (handleEvent / domKey) that replaces the DOM listeners.


## A GameInput with a monotonic fake clock (+1000 ms per read), as the TS test
## stubs performance.now, so click/double-click timing is deterministic.
func _input() -> GameInput:
	var i := GameInput.new()
	var clock := [1000.0]
	i.nowFn = func() -> float:
		clock[0] += 1000.0
		return clock[0]
	return i


func t(id: int, x: float, y: float) -> Dictionary:
	return {"identifier": id, "clientX": x, "clientY": y}


# ---- touch input ----

func test_a_tap_maps_to_a_left_click() -> void:
	var i := _input()
	var click := []
	i.onLeftClick = func(x, y): click.append_array([x, y])
	i.handleTouchStart([t(1, 100, 120)])
	eq(i.leftDown, true, "leftDown")
	eq(i.mx, 100, "mx")
	eq(i.usingTouch, true, "usingTouch")
	i.handleTouchEnd([t(1, 100, 120)])
	eq(click, [100.0, 120.0], "click")
	eq(i.leftDown, false, "released")


func test_a_one_finger_drag_maps_to_a_drag_box_release() -> void:
	var i := _input()
	var got := {"click": false, "drag": null}
	i.onLeftClick = func(_x, _y): got.click = true
	i.onDragEnd = func(b): got.drag = b
	i.handleTouchStart([t(1, 50, 50)])
	i.handleTouchMove([t(1, 160, 60)])
	eq(i.drag.active, true, "drag active")
	i.handleTouchEnd([t(1, 160, 60)])
	eq(got.drag.x0, 50, "x0")
	eq(got.drag.x1, 160, "x1")
	eq(got.click, false, "no click")


func test_two_fingers_pinch_and_pan() -> void:
	var i := _input()
	var got := {"pinch": null, "panned": false, "clicked": false}
	i.onPinch = func(_cx, _cy, f): got.pinch = f
	i.onPan = func(_dx, _dy): got.panned = true
	i.onLeftClick = func(_x, _y): got.clicked = true
	i.handleTouchStart([t(1, 100, 100)])
	i.handleTouchStart([t(2, 200, 100)]) # now pinching, base dist 100
	i.handleTouchMove([t(1, 80, 100), t(2, 220, 100)]) # dist 140 → factor 1.4
	near(got.pinch, 1.4, 0.05, "pinch factor")
	eq(got.panned, true, "panned")
	i.handleTouchEnd([t(1, 80, 100), t(2, 220, 100)])
	eq(got.clicked, false, "a pinch never fires a click")


func test_long_press_fires_a_right_click_only_when_enabled() -> void:
	var i := GameInput.new()
	var clock := [0.0]
	i.nowFn = func() -> float: return clock[0]
	var right := []
	i.onRightClick = func(x, y): right.append_array([x, y])
	# Disabled (outside a match): nothing.
	i.handleTouchStart([t(1, 70, 80)])
	clock[0] += 500.0
	i.update()
	eq(right, [], "disabled")
	i.handleTouchEnd([t(1, 70, 80)])
	# Enabled.
	i.longPressRight = true
	i.handleTouchStart([t(1, 70, 80)])
	clock[0] += 400.0
	i.update()
	eq(right, [], "not yet at 400 ms")
	clock[0] += 100.0
	i.update()
	eq(right, [70.0, 80.0], "fires at 450+ ms")


# ---- mouse clicks ----

func m(x: float, y: float) -> Dictionary:
	return {"button": 0, "clientX": x, "clientY": y, "shiftKey": false, "ctrlKey": false, "metaKey": false}


func test_a_press_and_release_on_the_canvas_is_a_click() -> void:
	var i := _input()
	var click := []
	i.onLeftClick = func(x, y): click.append_array([x, y])
	i.handleDown(m(40, 50))
	i.handleUp(m(40, 50))
	eq(click, [40.0, 50.0], "click")


func test_a_release_that_began_off_the_canvas_is_not() -> void:
	# Clicking a DOM overlay (the multiplayer lobby) delivers only the window's
	# mouseup: it used to click the menu button drawn under the overlay too.
	var i := _input()
	var got := {"clicked": false}
	i.onLeftClick = func(_x, _y): got.clicked = true
	i.handleUp(m(40, 50))
	eq(got.clicked, false, "no click")


func test_two_quick_clicks_are_a_double() -> void:
	var i := GameInput.new()
	var clock := [10000.0]
	i.nowFn = func() -> float: return clock[0]
	var got := {"single": 0, "double": 0}
	i.onLeftClick = func(_x, _y): got.single += 1
	i.onLeftDouble = func(_x, _y): got.double += 1
	i.handleDown(m(5, 5))
	i.handleUp(m(5, 5))
	clock[0] += 200.0
	i.handleDown(m(5, 5))
	i.handleUp(m(5, 5))
	eq(got.single, 1, "first is a click")
	eq(got.double, 1, "second within 280 ms is a double")


# ---- the Godot adapter ----

func _mouseButton(button: int, pressed: bool, x: float, y: float) -> InputEventMouseButton:
	var e := InputEventMouseButton.new()
	e.button_index = button
	e.pressed = pressed
	e.position = Vector2(x, y)
	return e


func test_godot_mouse_events_drive_the_same_handlers() -> void:
	var i := _input()
	var got := {"click": [], "right": [], "drag": null}
	i.onLeftClick = func(x, y): got.click = [x, y]
	i.onRightClick = func(x, y): got.right = [x, y]
	i.onDragEnd = func(b): got.drag = b
	i.handleEvent(_mouseButton(MOUSE_BUTTON_LEFT, true, 10, 20))
	eq(i.leftDown, true, "left down")
	i.handleEvent(_mouseButton(MOUSE_BUTTON_LEFT, false, 10, 20))
	eq(got.click, [10.0, 20.0], "click")
	i.handleEvent(_mouseButton(MOUSE_BUTTON_RIGHT, true, 30, 40))
	eq(i.rightDown, true, "right down")
	i.handleEvent(_mouseButton(MOUSE_BUTTON_RIGHT, false, 30, 40))
	eq(got.right, [30.0, 40.0], "right click at the mouse")
	# Drag: press, move past the threshold, release.
	i.handleEvent(_mouseButton(MOUSE_BUTTON_LEFT, true, 100, 100))
	var mv := InputEventMouseMotion.new()
	mv.position = Vector2(150, 130)
	i.handleEvent(mv)
	eq(i.mx, 150, "mouse x tracked")
	eq(i.drag.active, true, "dragging")
	i.handleEvent(_mouseButton(MOUSE_BUTTON_LEFT, false, 150, 130))
	eq([got.drag.x0, got.drag.y0, got.drag.x1, got.drag.y1], [100.0, 100.0, 150.0, 130.0], "drag box")
	# Middle button is held state only.
	i.handleEvent(_mouseButton(MOUSE_BUTTON_MIDDLE, true, 0, 0))
	eq(i.middleDown, true, "middle down")
	i.handleEvent(_mouseButton(MOUSE_BUTTON_MIDDLE, false, 0, 0))
	eq(i.middleDown, false, "middle up")


func test_wheel_reports_browser_sized_deltas() -> void:
	var i := _input()
	var deltas := []
	i.onWheel = func(_x, _y, d): deltas.append(d)
	i.handleEvent(_mouseButton(MOUSE_BUTTON_WHEEL_DOWN, true, 0, 0))
	i.handleEvent(_mouseButton(MOUSE_BUTTON_WHEEL_DOWN, false, 0, 0)) # the release is not a second notch
	i.handleEvent(_mouseButton(MOUSE_BUTTON_WHEEL_UP, true, 0, 0))
	eq(deltas, [100.0, -100.0], "one notch each way")


func test_emulated_mouse_from_touch_is_ignored() -> void:
	var i := _input()
	var e := _mouseButton(MOUSE_BUTTON_LEFT, true, 10, 10)
	e.device = InputEvent.DEVICE_ID_EMULATION
	i.handleEvent(e)
	eq(i.leftDown, false, "the touch already handled it")


func test_godot_touch_events_drive_the_touch_handlers() -> void:
	var i := _input()
	var click := []
	i.onLeftClick = func(x, y): click.append_array([x, y])
	var down := InputEventScreenTouch.new()
	down.index = 0
	down.position = Vector2(64, 32)
	down.pressed = true
	i.handleEvent(down)
	eq(i.usingTouch, true, "usingTouch")
	var up := InputEventScreenTouch.new()
	up.index = 0
	up.position = Vector2(64, 32)
	up.pressed = false
	i.handleEvent(up)
	eq(click, [64.0, 32.0], "tap → click")


func _key(keycode: int, unicode: int, pressed: bool, shift: bool = false, ctrl: bool = false) -> InputEventKey:
	var e := InputEventKey.new()
	e.keycode = keycode
	e.unicode = unicode
	e.pressed = pressed
	e.shift_pressed = shift
	e.ctrl_pressed = ctrl
	return e


func test_dom_key_names() -> void:
	eq(GameInput.domKey(_key(KEY_A, 97, true)), "a", "a")
	eq(GameInput.domKey(_key(KEY_A, 65, true, true)), "A", "Shift+a")
	eq(GameInput.domKey(_key(KEY_A, 0, true, false, true)), "a", "Ctrl+a without a character")
	eq(GameInput.domKey(_key(KEY_SPACE, 32, true)), " ", "space")
	eq(GameInput.domKey(_key(KEY_EQUAL, 43, true, true)), "+", "Shift+= types +")
	eq(GameInput.domKey(_key(KEY_TAB, 9, true)), "Tab", "Tab")
	eq(GameInput.domKey(_key(KEY_ESCAPE, 27, true)), "Escape", "Escape")
	eq(GameInput.domKey(_key(KEY_ENTER, 13, true)), "Enter", "Enter")
	eq(GameInput.domKey(_key(KEY_KP_ENTER, 13, true)), "Enter", "keypad Enter")
	eq(GameInput.domKey(_key(KEY_UP, 0, true)), "ArrowUp", "ArrowUp")
	eq(GameInput.domKey(_key(KEY_DELETE, 127, true)), "Delete", "Delete")
	eq(GameInput.domKey(_key(KEY_F3, 0, true)), "F3", "F3")
	eq(GameInput.domKey(_key(KEY_F10, 0, true)), "F10", "F10")
	eq(GameInput.domKey(_key(KEY_SHIFT, 0, true, true)), "Shift", "Shift")
	eq(GameInput.domKey(_key(KEY_CTRL, 0, true, false, true)), "Control", "Control")
	eq(GameInput.domKey(_key(KEY_PERIOD, 46, true)), ".", "period")


func test_keys_are_held_and_released_under_the_same_name() -> void:
	var i := _input()
	var down := []
	i.onKeyDown = func(k): down.append(k)
	i.handleEvent(_key(KEY_A, 65, true, true))
	eq(down, ["A"], "onKeyDown gets the key as typed")
	check(i.isDown("a"), "held keys are lower-cased")
	eq(i.shift, true, "shift")
	# Shift released first, then A (Godot carries no character on a release).
	i.handleEvent(_key(KEY_SHIFT, 0, false))
	i.handleEvent(_key(KEY_A, 0, false))
	check(not i.isDown("a"), "released")
	i.handleEvent(_key(KEY_LEFT, 0, true))
	check(i.isDown("ArrowLeft"), "arrow held")
	i.handleEvent(_key(KEY_LEFT, 0, false))
	check(not i.isDown("ArrowLeft"), "arrow released")
	# Chords line up with the keybind table.
	i.handleEvent(_key(KEY_A, 0, true, false, true))
	eq(Keybinds.chordOf(down[down.size() - 1], {"ctrl": i.ctrl, "shift": i.shift, "alt": i.alt}), "Ctrl+A", "Ctrl+A chord")


func test_losing_focus_drops_everything_held() -> void:
	var i := _input()
	i.handleEvent(_key(KEY_W, 119, true))
	i.handleDown(m(1, 1))
	i.handleTouchStart([t(3, 5, 5)])
	i.handleBlur()
	check(i.keys.is_empty(), "keys")
	check(not i.leftDown, "left")
	check(i.touches.is_empty(), "touches")
	check(not i.drag.active, "drag")
