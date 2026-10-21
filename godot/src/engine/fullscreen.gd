## engine/fullscreen.ts — fullscreen, and the one rule that decides how it has
## to be wired.
##
## Browsers only honour requestFullscreen() while a user gesture is in flight —
## synchronously inside the click or keydown handler. This UI is immediate-mode:
## a click is recorded by the input handler and only *interpreted* when the next
## frame draws the buttons. So the toggle is never called from the frame loop.
## Buttons register where they are (see ui.registerGestureZone) and the click
## handler checks those rects and calls toggleFullscreen() itself.
##
## Godot port: the window's mode through DisplayServer (which, in a web export,
## is the same requestFullscreen with the same gesture rule — so the wiring is
## kept). Tests (and anything else) can swap the window for a stand-in by
## setting `backend` to an object with `enabled() -> bool`, `active() -> bool`
## and `request(on: bool)`.
class_name Fullscreen

static var backend: Object = null


## Whether this game is allowed to go fullscreen at all: not headless, and on
## the web only where the page allows it.
static func fullscreenSupported() -> bool:
	if backend != null:
		return bool(backend.call("enabled"))
	if DisplayServer.get_name() == "headless":
		return false
	if OS.has_feature("web"):
		var ok = JavaScriptBridge.eval("!!(document.fullscreenEnabled || document.webkitFullscreenEnabled)", true)
		return ok == true
	return true


static func isFullscreen() -> bool:
	if backend != null:
		return bool(backend.call("active"))
	if DisplayServer.get_name() == "headless":
		return false
	var m := DisplayServer.window_get_mode()
	return m == DisplayServer.WINDOW_MODE_FULLSCREEN or m == DisplayServer.WINDOW_MODE_EXCLUSIVE_FULLSCREEN


## Enter or leave fullscreen. Call from inside the input handler (the gesture).
##
## Never fails loudly: a refused request (blocked by the embedding page, the
## user said no) leaves the game exactly as it was, which is the only sane
## outcome for a convenience toggle.
static func toggleFullscreen() -> void:
	if not fullscreenSupported():
		return
	var on := not isFullscreen()
	if backend != null:
		backend.call("request", on)
		return
	DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_FULLSCREEN if on else DisplayServer.WINDOW_MODE_WINDOWED)
	if on:
		lockLandscape()


## On a phone, fullscreen is also the moment to hold the screen sideways: an
## RTS in portrait is a letterbox with a minimap. Only ever *attempted*.
static func lockLandscape() -> void:
	if OS.has_feature("mobile"):
		DisplayServer.screen_set_orientation(DisplayServer.SCREEN_SENSOR_LANDSCAPE)
