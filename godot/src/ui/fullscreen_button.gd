## ui/fullscreen_button.ts — the fullscreen control, drawn wherever the game
## offers one.
##
## Drawing it does not toggle anything — the click handler does, inside the
## gesture (engine/fullscreen). This only draws the button, labels it for the
## current state, and registers where it is. Where fullscreen is unavailable it
## is drawn disabled with the reason, instead of being a button that silently
## fails.
class_name FullscreenButton

const FULLSCREEN_ZONE := "fullscreen"


## opts: {compact, size, hotkey}
static func fullscreenButton(x: float, y: float, w: float, h: float, opts: Dictionary = {}) -> void:
	var ui := UI.ui
	var supported := Fullscreen.fullscreenSupported()
	var on := Fullscreen.isFullscreen()
	var label: String
	if opts.get("compact", false):
		label = "🗗" if on else "⛶"
	else:
		label = "🗗  Exit fullscreen" if on else "⛶  Fullscreen"
	var hk: String = opts.get("hotkey", "")
	var key := " (%s)" % hk if hk != "" else ""
	var tip: Array
	if supported:
		tip = [("Exit fullscreen" if on else "Fullscreen") + key, "Esc also leaves fullscreen."]
	else:
		tip = [
			"Fullscreen isn't available here",
			"This page is embedded somewhere that doesn't allow it, or the device only allows fullscreen for video. Open the game file directly in a browser tab.",
		]
	ui.button(label, x, y, w, h, {"size": opts.get("size", 14), "disabled": not supported, "tooltip": tip})
	if supported:
		ui.registerGestureZone(FULLSCREEN_ZONE, x, y, w, h)
