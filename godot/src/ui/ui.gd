## ui/ui.ts — tiny immediate-mode canvas UI: panels, buttons, bars, tooltips —
## all drawn in the parchment-and-ink fantasy style so HUD and menus feel like
## one piece.
##
## Same API as the TS, on Ctx. The TS module's shared instance (`ui`) is
## `UI.ui`. A frame's input (TS `UIFrameInput`) is a Dictionary:
## {mx, my, clicked, rightClicked, alt?, leftHeld?, rightHeld?, ctrlHeld?,
## shiftHeld?, wheel?}. Widget options are Dictionaries too, with the TS
## option names.
class_name UI
extends RefCounted

static var _shared: UI = null

## The shared instance (TS: `export const ui = new UI()`).
static var ui: UI:
	get:
		if _shared == null:
			_shared = UI.new()
		return _shared

var ctx: Ctx
var mx := 0.0
var my := 0.0
var clicked := false  ## left release this frame
var rightClicked := false
var alt := false  ## Alt held this frame (used for minimap pings)
# Held state and the wheel, for screens that drag and zoom (the map editor).
var leftHeld := false
var rightHeld := false
var ctrlHeld := false
var shiftHeld := false
var wheel := 0.0  ## scroll delta this frame, 0 when still
## Set true by any widget that consumed the pointer this frame.
var pointerConsumed := false
var hoveredTooltip = null  ## {text: Array, x, y} or null
## Interface scale currently in force. 1 = canvas pixels.
var scale := 1.0
var scaleStack: Array = []
var scrollStack: Array = []
## Rects, in canvas pixels, that must act inside the DOM event rather than a
## frame later. Rebuilt every frame by whatever is drawn; read by the click
## handler between frames, so it always reflects what the player was looking
## at when they clicked. See engine/fullscreen.ts for why this exists.
var gestureZones: Array = []  ## of {id, x, y, w, h}


## Draw the next block of UI at `s`× size. Widgets lay out in a *smaller*
## space (pass W/s, H/s) and the transform blows them up, so a scaled HUD is
## genuinely larger rather than a stretched bitmap. Pointer coordinates are
## divided to match, which is what keeps hit-testing honest — every widget
## tests `ui.mx` against its own layout coordinates and neither knows nor
## cares that a transform is in force.
func pushScale(s: float) -> void:
	ctx.save()
	ctx.scale(s, s)
	scaleStack.append(scale)
	mx /= s
	my /= s
	scale = s


func popScale() -> void:
	var prev: float = scaleStack.pop_back() if not scaleStack.is_empty() else 1.0
	mx *= scale
	my *= scale
	scale = prev
	ctx.restore()


## Draw the next block of UI scrolled up by `dy` and clipped to `clip`
## ({x, y, w, h}).
##
## The same bargain as `pushScale`: the context is translated and the pointer
## is moved to match, so every widget keeps testing `ui.my` against its own
## layout coordinates and never learns a scroll is in force.
##
## A screen that lays its panels out by accumulating `y` downward has no idea
## how tall it will end up, and on a short window the things at the bottom
## simply leave the viewport — which is how the Skirmish setup screen ended up
## with its "To Battle!" button below the fold and unclickable.
func pushScroll(dy: float, clip = null) -> void:
	ctx.save()
	if clip != null:
		ctx.beginPath()
		ctx.rect(clip.x, clip.y, clip.w, clip.h)
		ctx.clip()
	ctx.translate(0, -dy)
	scrollStack.append(dy)
	my += dy


func popScroll() -> void:
	var dy: float = scrollStack.pop_back() if not scrollStack.is_empty() else 0.0
	my -= dy
	ctx.restore()


## A slim indicator that there is more above or below. Drawn outside the
## scrolled block, in screen coordinates.
func scrollbar(x: float, y: float, h: float, offset: float, contentH: float) -> void:
	if contentH <= h:
		return
	var frac := h / contentH
	var thumbH := maxf(24, h * frac)
	var t := y + (h - thumbH) * (offset / maxf(1, contentH - h))
	ctx.fillStyle = "rgba(255,255,255,0.06)"
	ctx.beginPath(); ctx.roundRect(x, y, 5, h, 2.5); ctx.fill()
	ctx.fillStyle = "rgba(255,233,176,0.45)"
	ctx.beginPath(); ctx.roundRect(x, t, 5, thumbH, 2.5); ctx.fill()


func begin(c: Ctx, input: Dictionary) -> void:
	ctx = c
	mx = float(input.get("mx", 0.0))
	my = float(input.get("my", 0.0))
	clicked = bool(input.get("clicked", false))
	rightClicked = bool(input.get("rightClicked", false))
	alt = bool(input.get("alt", false))
	leftHeld = bool(input.get("leftHeld", false))
	rightHeld = bool(input.get("rightHeld", false))
	ctrlHeld = bool(input.get("ctrlHeld", false))
	shiftHeld = bool(input.get("shiftHeld", false))
	wheel = float(input.get("wheel", 0.0))
	pointerConsumed = false
	hoveredTooltip = null
	scale = 1.0
	scaleStack.clear()
	scrollStack.clear()
	gestureZones.clear()


## Record that this layout rect should act on the raw click. Converted to
## canvas pixels through the live transform, so it stays exact under the UI
## scale slider and inside a scrolled panel without either knowing about it.
func registerGestureZone(id: String, x: float, y: float, w: float, h: float) -> void:
	var m := ctx.getTransform()
	var p0 := m * Vector2(x, y)
	var p1 := m * Vector2(x + w, y + h)
	gestureZones.append({
		"id": id, "x": minf(p0.x, p1.x), "y": minf(p0.y, p1.y), "w": absf(p1.x - p0.x), "h": absf(p1.y - p0.y),
	})


## Which gesture zone, if any, a raw canvas-space click landed in.
func gestureAt(x: float, y: float) -> Variant:
	# Last drawn wins, matching what is on top.
	for i in range(gestureZones.size() - 1, -1, -1):
		var z: Dictionary = gestureZones[i]
		if x >= z.x and x <= z.x + z.w and y >= z.y and y <= z.y + z.h:
			return z.id
	return null


func hit(x: float, y: float, w: float, h: float) -> bool:
	return mx >= x and mx <= x + w and my >= y and my <= y + h


## Mark a rect as consuming the pointer (e.g. HUD panels over the world).
func blockPointer(x: float, y: float, w: float, h: float) -> void:
	if hit(x, y, w, h):
		pointerConsumed = true


## A framed panel. Same parchment-and-gold as ever, with some depth: a soft
## shadow under it, a gradient that is a shade lighter at the top, a dark
## outer rim, and the gold line inset from it like a picture frame.
## opts: {light}
func panel(x: float, y: float, w: float, h: float, opts: Dictionary = {}) -> void:
	var r := 9.0
	# Drop shadow: an offset dark shape rather than a blur, which is costly.
	ctx.fillStyle = "rgba(0,0,0,0.28)"
	ctx.beginPath(); ctx.roundRect(x + 2, y + 4, w, h, r); ctx.fill()
	var g := ctx.createLinearGradient(0, y, 0, y + h)
	if opts.get("light", false):
		g.addColorStop(0, "rgba(64,51,32,0.96)"); g.addColorStop(1, "rgba(40,31,19,0.96)")
	else:
		g.addColorStop(0, "rgba(36,28,17,0.94)"); g.addColorStop(1, "rgba(20,15,9,0.94)")
	ctx.fillStyle = g
	ctx.beginPath(); ctx.roundRect(x, y, w, h, r); ctx.fill()
	ctx.strokeStyle = "rgba(0,0,0,0.55)"
	ctx.lineWidth = 1.5
	ctx.beginPath(); ctx.roundRect(x + 0.75, y + 0.75, w - 1.5, h - 1.5, r); ctx.stroke()
	ctx.strokeStyle = Palette.withAlpha(C.PAL.uiAccent, 0.5)
	ctx.lineWidth = 1
	ctx.beginPath(); ctx.roundRect(x + 3.5, y + 3.5, w - 7, h - 7, r - 3); ctx.stroke()
	# A faint light along the top edge.
	ctx.strokeStyle = "rgba(255,236,190,0.08)"
	ctx.beginPath(); ctx.moveTo(x + r, y + 5); ctx.lineTo(x + w - r, y + 5); ctx.stroke()
	blockPointer(x, y, w, h)


## opts: {size, color, align, bold, font}
func text(s: String, x: float, y: float, opts: Dictionary = {}) -> void:
	var size = opts.get("size")
	ctx.font = ("bold " if opts.get("bold", false) else "") + JS.num(14.0 if size == null else float(size)) + "px " + str(opts.get("font", "\"Trebuchet MS\", sans-serif"))
	ctx.fillStyle = opts.get("color", C.PAL.uiParchment)
	ctx.textAlign = opts.get("align", "left")
	ctx.textBaseline = "middle"
	ctx.fillText(s, x, y)
	ctx.textAlign = "left"


## opts: {disabled, accent, danger, size, tooltip (Array of lines), badge}
func button(label: String, x: float, y: float, w: float, h: float, opts: Dictionary = {}) -> bool:
	var PAL: Dictionary = C.PAL
	var disabled: bool = opts.get("disabled", false)
	var accent: bool = opts.get("accent", false)
	var danger: bool = opts.get("danger", false)
	var over := hit(x, y, w, h)
	var hover := over and not disabled
	if over:
		pointerConsumed = true

	# Gold-on-dark as ever, drawn with some depth: a gradient body lighter at
	# the top, a highlight along the upper edge, a gold rim that brightens on
	# hover, and a press that sinks it a pixel.
	var pressed := hover and leftHeld
	var base := "#5e2622" if danger else ("#6a5024" if accent else "#3c3427")
	var top = "#2c271f" if disabled else Palette.shade(base, 0.3 if hover else 0.14)
	var bot = "#221e18" if disabled else Palette.shade(base, 0.02 if hover else -0.12)
	var oy := 1.0 if pressed else 0.0
	var rad := minf(7, h / 2.6)
	ctx.fillStyle = "rgba(0,0,0,0.3)"
	ctx.beginPath(); ctx.roundRect(x, y + 2, w, h, rad); ctx.fill()
	var g := ctx.createLinearGradient(0, y + oy, 0, y + oy + h)
	g.addColorStop(0, bot if pressed else top)
	g.addColorStop(1, top if pressed else bot)
	ctx.fillStyle = g
	ctx.beginPath()
	ctx.roundRect(x, y + oy, w, h, rad)
	ctx.fill()
	if not disabled and not pressed:
		ctx.strokeStyle = "rgba(255,240,205,0.13)"
		ctx.lineWidth = 1
		ctx.beginPath(); ctx.moveTo(x + rad, y + 1.5); ctx.lineTo(x + w - rad, y + 1.5); ctx.stroke()
	if disabled:
		ctx.strokeStyle = Palette.withAlpha("#888070", 0.28)
	elif hover:
		ctx.strokeStyle = "#e0786a" if danger else "#e2b24e"
	else:
		ctx.strokeStyle = Palette.withAlpha(PAL.uiAccent, 0.85 if accent else 0.5)
	ctx.lineWidth = 1.6 if hover or accent else 1.1
	ctx.beginPath()
	ctx.roundRect(x + 0.8, y + oy + 0.8, w - 1.6, h - 1.6, rad - 0.5)
	ctx.stroke()

	# A soft shadow under the label keeps it readable on any fill.
	var lsize = opts.get("size", 14)
	if not disabled:
		text(label, x + w / 2, y + h / 2 + 2 + oy, {"align": "center", "size": lsize, "color": "rgba(0,0,0,0.45)", "bold": true})
	text(label, x + w / 2, y + h / 2 + 1 + oy, {
		"align": "center",
		"size": lsize,
		"color": "#7a7264" if disabled else ("#fff0cc" if accent else PAL.uiParchment),
		"bold": true,
	})

	var badge = opts.get("badge")
	if badge != null and str(badge) != "":
		ctx.fillStyle = PAL.uiAccent
		ctx.beginPath()
		ctx.roundRect(x + w - 22, y - 6, 24, 16, 8)
		ctx.fill()
		text(str(badge), x + w - 10, y + 2, {"align": "center", "size": 11, "color": "#1c150c", "bold": true})

	# Show the tooltip even when disabled — so you can read an item's cost and
	# requirements before you can afford or unlock it.
	var tip = opts.get("tooltip")
	if over and tip != null and not (tip as Array).is_empty():
		hoveredTooltip = {"text": tip, "x": mx, "y": y}
	return hover and clicked and not disabled


func bar(x: float, y: float, w: float, h: float, frac: float, color, bg = "rgba(0,0,0,0.55)") -> void:
	ctx.fillStyle = bg
	ctx.beginPath()
	ctx.roundRect(x, y, w, h, h / 2)
	ctx.fill()
	if frac > 0.01:
		ctx.fillStyle = color
		ctx.beginPath()
		ctx.roundRect(x + 1, y + 1, maxf(h - 2, (w - 2) * minf(1, frac)), h - 2, (h - 2) / 2)
		ctx.fill()


## Queue a tooltip for whatever is under the pointer (call when hovering).
func tooltip(lines: Array) -> void:
	if not lines.is_empty():
		hoveredTooltip = {"text": lines, "x": mx, "y": my}


## Draw the queued tooltip last so it sits above everything.
func flushTooltip(canvasW: float, canvasH: float) -> void:
	if hoveredTooltip == null:
		return
	var PAL: Dictionary = C.PAL
	var lines: Array = hoveredTooltip.text
	ctx.font = "13px 'Trebuchet MS', sans-serif"
	var maxW := 0.0
	for l in lines:
		maxW = maxf(maxW, ctx.measureText(str(l)).width)
	var w := maxW + 20
	var h := lines.size() * 18 + 14
	var x := minf(hoveredTooltip.x + 14, canvasW - w - 8)
	var y: float = hoveredTooltip.y - h - 10
	if y < 8:
		y = hoveredTooltip.y + 26
	ctx.fillStyle = "rgba(16, 12, 6, 0.96)"
	ctx.beginPath()
	ctx.roundRect(x, y, w, h, 5)
	ctx.fill()
	ctx.strokeStyle = Palette.withAlpha(PAL.uiAccent, 0.7)
	ctx.lineWidth = 1
	ctx.stroke()
	for i in lines.size():
		text(str(lines[i]), x + 10, y + 16 + i * 18, {
			"size": 13,
			"color": PAL.uiAccent if i == 0 else PAL.uiParchment,
			"bold": i == 0,
		})


## A slider; returns the (possibly updated) value while dragging.
func slider(x: float, y: float, w: float, value: float, dragging: bool) -> float:
	var PAL: Dictionary = C.PAL
	bar(x, y - 3, w, 6, value, PAL.uiAccent)
	var hx := x + value * w
	ctx.fillStyle = PAL.uiParchment
	ctx.beginPath()
	ctx.arc(hx, y, 7, 0, PI * 2)
	ctx.fill()
	if hit(x - 8, y - 12, w + 16, 24):
		pointerConsumed = true
		if dragging:
			return maxf(0, minf(1, (mx - x) / w))
	return value
