## ui/warband_screen.ts — Warband Tactics, the auto-battler screen. Draws a
## WarbandRun: standings, the board (a TFT-style tiled arena where your
## warband and the enemy's stand and then fight with full unit animations),
## your bench, and the shop/fight flow. Presentation only; all rules live in
## sim/warband and the fight is a real, watchable combat sim
## (sim/autobattle → LiveBattle).
##
## Same methods and signatures as the TS: the main loop calls
## `draw(W, H, time, run)` each frame (after `UI.ui.begin(...)`) and gets
## "exit" back when the player leaves; keys go through `handleKey(key)`. The
## TS reads the pointer-held state from screens.ts (`isMouseDown()`); main.ts
## feeds that and the UI frame's `leftHeld` from the same input, so here it is
## read off `UI.ui.leftHeld`.
class_name WarbandScreen
extends RefCounted

const TIER_COLOR := ["#888888", "#9aa8b4", "#4caf50", "#3a78d8", "#9b5cf0", "#e0a020"]
const STAR_MULT := [1.0, 1.0, 1.8, 3.2] # hp/attack multiplier by star (matches the battle sim)
const BATTLE_SPEED := 2.0 # sim-time multiplier while watching a fight
const INTRO_LEN := 1.4 # seconds the VS clash banner holds before the charge

## Left rail (standings, commander, synergies, relics) and bottom bar heights.
const RAIL_W := 232.0
const BENCH_H := 86.0
const SHOP_H := 146.0
## The banner across the top, and the strip of labels above the arena.
const HEADER_H := 62.0
## The stone kerb the board is set into — nothing else may sit inside it.
const BOARD_RIM := 12.0

const GRID_COLS := Autobattle.GRID_COLS
const GRID_ROWS := Autobattle.GRID_ROWS
const GRID_CELL := Autobattle.GRID_CELL


static func shortName(type: String) -> String:
	var def = C.UNITS.get(type)
	return String(def.name if def != null else type).split(" ")[0]


static func stars(n: int) -> String:
	return "★".repeat(n)


static func easeOut(t: float) -> float:
	return 1.0 - pow(1.0 - maxf(0.0, minf(1.0, t)), 3)


static func withAlpha(c, a: float) -> Color:
	return Palette.withAlpha(c, a)


## Rank of each lobby on the ladder, for the pip row under the title.
static func warbandRank(id: String) -> int:
	var all: Array = C.WARBAND_DIFFICULTIES
	for i in all.size():
		if all[i].id == id:
			return i
	return 0


static func AUG_COLOR(tier: String) -> String:
	return C.TIER_COLOR[tier]


## `UNIT_TIER[type] ?? 1`
static func tierOf(type: String) -> int:
	return UnitTiers.tierOf(type, 1)


## The arena rect for a given canvas. The board is a 10×10 grid, so its cells
## are kept close to square and the board is centred in whatever width is left
## — otherwise on a wide monitor the arena smears into a near-empty field of
## very flat rectangles with the units lost in the middle of it. The rect is the
## *playable* area; the kerb and its braziers overhang it by BOARD_RIM, which is
## why every margin here allows for it.
static func boardRect(W: float, H: float) -> Dictionary:
	var y := HEADER_H + 34 + BOARD_RIM
	var availX := RAIL_W + BOARD_RIM
	var availW := W - availX - 16 - BOARD_RIM
	var availH := (H - SHOP_H) - BENCH_H - 10 - BOARD_RIM - y
	var cellH := availH / GRID_ROWS
	# A little horizontal stretch reads fine; a lot looks broken.
	var cellW := minf(availW / GRID_COLS, cellH * 1.18)
	var w := cellW * GRID_COLS
	return {"x": JS.round(availX + (availW - w) / 2), "y": y, "w": w, "h": cellH * GRID_ROWS}


var selectedItem := -1 # index into the run's item stash, or -1
var battle: Autobattle.LiveBattle = null
var battleSig := ""
var stepAccum := 0.0
var lastTime := 0.0
# Drag/click placement state.
var heldPiece := -1 # piece index "picked up" (for placement or selling), or -1
var heldFromBoard := false # true if it was lifted off a board cell (so it can be re-placed)
var wasDown := false # pointer-held last frame (for press-edge detection)
var pressEdge := false # pointer went down this frame
var grabbedThisPress := false
var movedSincePress := false
var pressX := 0.0
var pressY := 0.0
var sellBox := {"x": 0.0, "y": 0.0, "w": 0.0, "h": 0.0} # last frame's sell-box rect
var fx := WarbandFx.new()
var prevPhase := "shop"
var itemTip = null ## {id, x, y} or null
var unitTip = null ## {p, x, y, compare?} or null
var liveTip = null ## {e, x, y} — hovered unit mid-fight
var augTip = null ## {a, x, y} — hovered owned augment
var cmdTip = null ## {c, x, y} — hovered commander plate
var condTip = null ## {c, x, y} — hovered battlefield chip
## One reusable entity per unit type, so cards can draw the real sprite.
var portraits := {}
## Trailing health fraction per entity, for the damage-ghost on health bars.
var hpGhost := {}
## The ground-seed field's contents while it is being typed into, else null.
var seedEdit = null
## A seed submitted with Enter, waiting to be handed to the run next frame.
var seedApply = null
var introT := 0.0 # VS clash banner countdown at the start of a fight
var resultT := -1.0 # victory/defeat flourish clock
var scoutId := -1 # opponent whose warband is being scouted, or -1
var scoutRect := {"x": 0.0, "y": 0.0, "w": 0.0, "h": 0.0} # last frame's panel, to block clicks through it
var augPick := -1 # augment card under the cursor on the picker
var augT := 0.0 # augment picker intro clock (card deal-in)
# A star-up celebration: set when the run reports a merge, fades on its own.
var starUp = null ## {type, star, t}
# A relic-forged flourish when two components fuse on a unit.
var fusion = null ## {item, type, t}

static var _alnum := RegEx.create_from_string("^[0-9a-zA-Z]$")


## Keys, forwarded from the app while this screen is up. Only the ground-seed
## field wants them; everything else here is pointer-driven. Returns true when
## the key was consumed, so the app knows not to treat it as a hotkey.
func handleKey(key: String) -> bool:
	if seedEdit == null:
		return false
	if key == "Escape":
		seedEdit = null
		return true
	if key == "Enter":
		seedApply = seedEdit
		seedEdit = null
		return true
	if key == "Backspace":
		seedEdit = String(seedEdit).substr(0, maxi(0, String(seedEdit).length() - 1))
		return true
	if key.length() == 1 and _alnum.search(key) != null and String(seedEdit).length() < 6:
		seedEdit = String(seedEdit) + key.to_upper()
		return true
	return true # while typing, swallow everything else too


## A frame's stat pod in the header (the TS `pod` closure).
func _pod(ctx: Ctx, ui: UI, x: float, w2: float, label: String, POD_Y: float, POD_H: float) -> void:
	var pg := ctx.createLinearGradient(0, POD_Y, 0, POD_Y + POD_H)
	pg.addColorStop(0, "rgba(0,0,0,0.42)"); pg.addColorStop(1, "rgba(0,0,0,0.22)")
	ctx.fillStyle = pg; roundRect(ctx, x, POD_Y, w2, POD_H, 7); ctx.fill()
	ctx.strokeStyle = "rgba(255,246,224,0.08)"; ctx.lineWidth = 1
	roundRect(ctx, x + 0.5, POD_Y + 0.5, w2 - 1, POD_H - 1, 7); ctx.stroke()
	ui.text(label, x + 9, POD_Y + 14, {"size": 9, "color": "#8f8770"})


func draw(W: float, H: float, time: float, run: Warband.WarbandRun):
	var ui: UI = UI.ui
	var ctx: Ctx = ui.ctx
	var PAL: Dictionary = C.PAL
	ctx.fillStyle = "#15110b"
	ctx.fillRect(0, 0, W, H)

	var dt := minf(0.1, time - lastTime) if lastTime != 0.0 else 0.0
	lastTime = time

	# Pointer press/drag tracking (for grab-and-drop placement + selling).
	var down := ui.leftHeld
	pressEdge = down and not wasDown
	if pressEdge:
		pressX = ui.mx; pressY = ui.my; movedSincePress = false; grabbedThisPress = false
	if down and (absf(ui.mx - pressX) > 5 or absf(ui.my - pressY) > 5):
		movedSincePress = true

	var action = null

	# A ground seed typed in and submitted last frame.
	if seedApply != null:
		var code: String = seedApply
		seedApply = null
		if run.applyTerrainCode(code):
			audio.play("complete"); battleSig = ""
		else:
			audio.play("tick")
	if run.phase != "shop":
		seedEdit = null # never leave a field armed

	# ---- header ----
	drawHeaderPlate(W, time)
	bannerEmblem(ctx, 26, 30, 13, time)
	ui.text("Warband Tactics", 46, 33, {"size": 21, "bold": true, "color": PAL.uiAccent, "font": "Georgia, serif"})
	# Which lobby you chose, under the title — it changes how every round reads.
	if true:
		var d: Dictionary = run.difficulty
		for k in 4:
			ctx.fillStyle = d.color if k <= warbandRank(d.id) else "rgba(255,255,255,0.14)"
			ctx.beginPath(); ctx.arc(50 + k * 7, 46, 2.3, 0, PI * 2); ctx.fill()
		ui.text(d.name, 82, 49, {"size": 10, "bold": true, "color": withAlpha(d.color, 0.9)})
	# Stat pods: a fixed grid, each pod owning its own sub-line, so nothing has
	# to be squeezed into a neighbour's box.
	var POD_Y := 7.0
	var POD_H := 48.0
	var px0 := 250.0
	# TFT-style stage-round ("2-3") with the raw round number beside it.
	if true:
		var x := px0
		_pod(ctx, ui, x, 88, "STAGE", POD_Y, POD_H)
		ui.text(run.stageLabel(), x + 9, POD_Y + 36, {"size": 20, "bold": true, "color": "#e7ddc4"})
		ui.text("rd %d" % run.round, x + 79, POD_Y + 36, {"align": "right", "size": 9.5, "color": "#6f6a5c"})
	px0 += 94
	if true:
		var x := px0
		var w2 := 108.0
		_pod(ctx, ui, x, w2, "GOLD", POD_Y, POD_H)
		ctx.save(); ctx.shadowColor = "#ffd24a"; ctx.shadowBlur = 6
		var cg := ctx.createRadialGradient(x + 15, POD_Y + 26, 1, x + 17, POD_Y + 28, 8)
		cg.addColorStop(0, "#ffe89a"); cg.addColorStop(1, "#e0a52a")
		ctx.fillStyle = cg; ctx.beginPath(); ctx.arc(x + 17, POD_Y + 28, 7, 0, PI * 2); ctx.fill(); ctx.restore()
		ctx.strokeStyle = "#a8771e"; ctx.lineWidth = 1; ctx.beginPath(); ctx.arc(x + 17, POD_Y + 28, 7, 0, PI * 2); ctx.stroke()
		ui.text(str(run.gold), x + 30, POD_Y + 35, {"size": 20, "bold": true, "color": "#ffd24a"})
		# Where the next point of interest lands, so the maths isn't homework.
		var g := run.gold
		var tens := floori(g / 10.0)
		var capped := tens >= 5
		for i in 5:
			ctx.fillStyle = "#ffd24a" if tens > i else "rgba(255,210,74,0.20)"
			ctx.beginPath(); ctx.arc(x + 11 + i * 7.5, POD_Y + 43, 2.3, 0, PI * 2); ctx.fill()
		ui.text("max int." if capped else "+1g @%d" % ((tens + 1) * 10), x + w2 - 8, POD_Y + 46, {"align": "right", "size": 8.5, "color": "#8a8278"})
	px0 += 114
	if true:
		var x := px0
		var w2 := 96.0
		_pod(ctx, ui, x, w2, "LEVEL", POD_Y, POD_H)
		ui.text(str(run.level), x + 9, POD_Y + 36, {"size": 20, "bold": true, "color": "#e7ddc4"})
		var xp := run.xpProgress()
		var bx2 := x + 32
		var by2 := POD_Y + 29
		var bw2 := w2 - 42
		ctx.fillStyle = "rgba(0,0,0,0.6)"; roundRect(ctx, bx2, by2, bw2, 5, 2.5); ctx.fill()
		if xp.max:
			ctx.fillStyle = "#ffd24a"; roundRect(ctx, bx2, by2, bw2, 5, 2.5); ctx.fill()
			ui.text("MAX", x + w2 - 8, POD_Y + 46, {"align": "right", "size": 8.5, "color": "#ffd24a"})
		else:
			var cur: int = maxi(0, xp.xp) # a hand-set level can sit below its own gate
			ctx.fillStyle = "#7fb0e8"
			roundRect(ctx, bx2, by2, maxf(2, bw2 * minf(1, float(cur) / xp.need)), 5, 2.5); ctx.fill()
			ui.text("%d/%d xp" % [cur, xp.need], x + w2 - 8, POD_Y + 46, {"align": "right", "size": 8.5, "color": "#8a8278"})
	px0 += 102
	if true:
		var x := px0
		var w2 := 84.0
		_pod(ctx, ui, x, w2, "LIFE", POD_Y, POD_H)
		var col := "#e0564a" if run.life <= 25 else ("#e0a878" if run.life <= 50 else "#7df2a9")
		ui.text(str(run.life), x + 9, POD_Y + 36, {"size": 20, "bold": true, "color": col})
		ctx.fillStyle = "rgba(0,0,0,0.6)"; roundRect(ctx, x + 9, POD_Y + 41, w2 - 18, 4, 2); ctx.fill()
		ctx.fillStyle = col; roundRect(ctx, x + 9, POD_Y + 41, (w2 - 18) * maxf(0, minf(1, run.life / 100.0)), 4, 2); ctx.fill()
	px0 += 90
	if true:
		var x := px0
		var w2 := 84.0
		_pod(ctx, ui, x, w2, "STREAK", POD_Y, POD_H)
		var s := run.streak
		var col := "#7df2a9" if s > 0 else ("#e0a878" if s < 0 else "#9a917b")
		ui.text(("+" if s > 0 else "") + str(s), x + 9, POD_Y + 36, {"size": 20, "bold": true, "color": col})
		# Pips toward the next streak payout, so a run of wins visibly builds.
		var n := mini(5, absi(s))
		for i in 5:
			ctx.fillStyle = col if i < n else "rgba(255,255,255,0.12)"
			ctx.beginPath(); ctx.arc(x + w2 - 12 - i * 8, POD_Y + 43, 2.3, 0, PI * 2); ctx.fill()
	px0 += 90
	drawRoundTrack(px0 + 10, POD_Y, maxf(0, W - 136 - (px0 + 10)), POD_H, run)
	# While a modal pick is up nothing behind it may be clicked.
	var picking := run.phase == "augment" or run.phase == "commander" or run.phase == "draft" or run.phase == "difficulty"
	if not picking and ui.button("Quit Run", W - 120, 12, 100, 32, {"danger": true, "size": 13}):
		action = "exit"

	# ---- left rail ----
	# Everything in the rail is clipped to the space between the header and the
	# shop counter. Late in a run the relic grid grows past the bottom of the
	# screen, and it used to draw straight through the counter.
	ctx.save()
	ctx.beginPath(); ctx.rect(0, HEADER_H, RAIL_W - 4, H - SHOP_H - HEADER_H - 4); ctx.clip()
	var sx := 12.0
	var sy := 80.0
	ui.text("Standings", sx, sy, {"size": 14, "bold": true, "color": PAL.uiAccent})
	ui.text("click to scout", sx + 74, sy, {"size": 9.5, "color": "#6f6a5c"})
	sy += 18
	for s in run.standings():
		var h := 26.0
		var hov: bool = not s.you and s.alive and ui.mx >= sx and ui.mx <= sx + 200 and ui.my >= sy and ui.my <= sy + h
		var open: bool = s.id == scoutId
		if s.you:
			ctx.fillStyle = withAlpha(PAL.uiAccent, 0.16)
		elif open:
			ctx.fillStyle = withAlpha("#7fb0e8", 0.24)
		else:
			ctx.fillStyle = "rgba(255,255,255,0.09)" if hov else "rgba(0,0,0,0.25)"
		ctx.fillRect(sx, sy, 200, h)
		if open or hov:
			ctx.strokeStyle = withAlpha("#7fb0e8", 0.9 if open else 0.45); ctx.lineWidth = 1; ctx.strokeRect(sx + 0.5, sy + 0.5, 199, h - 1)
		# The warband you face this round gets a marker so you can read the matchup.
		var next: bool = not s.you and not run.isCreepRound() and s.name == run.pendingFoeName() and s.alive
		ui.text(("" if s.alive else "† ") + s.name, sx + 8, sy + 17, {
			"size": 12, "bold": s.you, "color": ("#ffe9b0" if s.you else "#e7ddc4") if s.alive else "#6f6a5c",
		})
		if next:
			roundGlyph(ctx, sx + 100, sy + 12, 5.5, "pvp", "#e0786a")
		ui.bar(sx + 110, sy + 9, 82, 9, maxf(0, s.life) / 100.0, "#7df2a9" if s.alive else "#5a554d")
		# Light text with a dark halo, so it stays legible over both the filled
		# and the empty part of the bar.
		ctx.save()
		ctx.shadowColor = "rgba(0,0,0,0.9)"; ctx.shadowBlur = 3
		ui.text(str(maxi(0, s.life)), sx + 152, sy + 17, {"align": "center", "size": 9.5, "bold": true, "color": "#f2f7ee"})
		ctx.restore()
		if hov and not picking and ui.clicked and not ui.pointerConsumed:
			ui.pointerConsumed = true; scoutId = -1 if open else s.id; audio.play("ui")
		sy += h + 3

	# ---- the commander leading this run ----
	cmdTip = null
	if run.commander != null:
		var id = WarbandCommanders.commanderIdentity(run.commander)
		var col: String = id.color if id != null else "#caa56a"
		sy += 12
		var ch := 40.0
		var cg := ctx.createLinearGradient(sx, 0, sx + 200, 0)
		cg.addColorStop(0, withAlpha(col, 0.26)); cg.addColorStop(1, "rgba(12,9,5,0.6)")
		ctx.fillStyle = cg; roundRect(ctx, sx, sy, 200, ch, 6); ctx.fill()
		ctx.strokeStyle = withAlpha(col, 0.6); ctx.lineWidth = 1
		roundRect(ctx, sx + 0.5, sy + 0.5, 199, ch - 1, 6); ctx.stroke()
		crest(ctx, sx + 22, sy + ch / 2, 14, run.commander, time)
		ui.text(id.name if id != null else run.commander.id, sx + 44, sy + 18, {"size": 13, "bold": true, "color": "#f2e8d0", "font": "Georgia, serif"})
		ui.text(clip(id.title if id != null else "", 22), sx + 44, sy + 32, {"size": 10, "color": col})
		var hovC := ui.mx >= sx and ui.mx <= sx + 200 and ui.my >= sy and ui.my <= sy + ch
		if hovC:
			cmdTip = {"c": run.commander, "x": sx + 208, "y": sy - 6}
		sy += ch

	# ---- augments you've taken this run ----
	augTip = null
	if run.augments.size() > 0:
		sy += 12
		ui.text("Augments", sx, sy, {"size": 14, "bold": true, "color": PAL.uiAccent})
		sy += 8
		for i in run.augments.size():
			var a: Dictionary = run.augments[i]
			var ay := sy + i * 30
			var aw := 200.0
			var ah := 26.0
			var col := AUG_COLOR(a.tier)
			var hov := ui.mx >= sx and ui.mx <= sx + aw and ui.my >= ay and ui.my <= ay + ah
			var g2 := ctx.createLinearGradient(sx, 0, sx + aw, 0)
			g2.addColorStop(0, withAlpha(col, 0.34 if hov else 0.2)); g2.addColorStop(1, "rgba(12,9,5,0.6)")
			ctx.fillStyle = g2; roundRect(ctx, sx, ay, aw, ah, 5); ctx.fill()
			ctx.strokeStyle = withAlpha(col, 0.95 if hov else 0.55); ctx.lineWidth = 1
			roundRect(ctx, sx + 0.5, ay + 0.5, aw - 1, ah - 1, 5); ctx.stroke()
			augSigil(ctx, sx + 15, ay + 13, 8, a, time)
			ui.text(clip(a.name, 22), sx + 30, ay + 17, {"size": 11.5, "bold": true, "color": col})
			if hov:
				augTip = {"a": a, "x": sx + aw + 8, "y": ay - 10}
		sy += run.augments.size() * 30

	# ---- synergies ----
	# Every trait the board touches, not only the ones already switched on:
	# "two away from Vanguard" is the thing you actually shop against.
	sy += 12
	ui.text("Synergies", sx, sy, {"size": 14, "bold": true, "color": PAL.uiAccent})
	sy += 16
	var traits := run.traitProgress()
	if traits.is_empty():
		ui.text("— nothing deployed —", sx, sy + 6, {"size": 11, "color": "#6f6a5c"}); sy += 16
	for at in traits:
		var on: bool = at.tier != null
		var th := 32.0 if on else 24.0
		var col: String = at.trait.color
		var g3 := ctx.createLinearGradient(sx, 0, sx + 200, 0)
		g3.addColorStop(0, withAlpha(col, 0.24 if on else 0.08)); g3.addColorStop(1, "rgba(12,9,5,0.35)")
		ctx.fillStyle = g3; roundRect(ctx, sx, sy, 200, th, 4); ctx.fill()
		# A hexagonal badge carrying the count — TFT's language, in our palette.
		var bx3 := sx + 15
		var by3 := sy + th / 2
		var br := 10.0 if on else 8.0
		ctx.beginPath()
		for i in 6:
			var a := (i / 6.0) * PI * 2 - PI / 2
			var hx := bx3 + cos(a) * br
			var hy := by3 + sin(a) * br
			if i:
				ctx.lineTo(hx, hy)
			else:
				ctx.moveTo(hx, hy)
		ctx.closePath()
		ctx.fillStyle = withAlpha(col, 0.9) if on else "rgba(0,0,0,0.5)"; ctx.fill()
		ctx.strokeStyle = withAlpha(col, 1 if on else 0.5); ctx.lineWidth = 1.2; ctx.stroke()
		ui.text(JS.num(at.count), bx3, by3 + 4, {
			"align": "center", "size": 11 if on else 9.5, "bold": true, "color": "#0e0b06" if on else withAlpha(col, 0.9),
		})
		ui.text(at.trait.name, sx + 31, sy + (14 if on else 15), {
			"size": 12 if on else 11, "bold": on, "color": col if on else withAlpha(col, 0.6),
		})
		if on:
			ui.text(at.tier.label, sx + 31, sy + 26, {"size": 9.5, "color": "#cabfa4"})
		# Threshold pips: the ladder this trait still has to climb.
		var marks: Array = at.trait.tiers
		var mx := sx + 194
		for i in range(marks.size() - 1, -1, -1):
			var hit: bool = at.count >= marks[i].at
			ctx.fillStyle = col if hit else "rgba(255,255,255,0.16)"
			ctx.beginPath(); ctx.arc(mx, sy + th / 2, 2.6, 0, PI * 2); ctx.fill()
			mx -= 8
		if at.next != null:
			ui.text("+%s" % JS.num(at.next - at.count), mx - 2, sy + th / 2 + 4, {"align": "right", "size": 9, "color": "#8a8278"})
		sy += th + 3

	# ---- the ground you're fighting on ----
	# Weather and terrain used to be chips squeezed above the board, where they
	# collided with the arena's kerb. They belong with the other run state.
	condTip = null
	if true:
		sy += 14
		ui.text("Battlefield", sx, sy, {"size": 14, "bold": true, "color": PAL.uiAccent})
		sy += 8
		var cond: Dictionary = run.condition
		var live := Conditions.hasEffect(cond)
		var bh2 := 34.0
		var hovC := ui.mx >= sx and ui.mx <= sx + 200 and ui.my >= sy and ui.my <= sy + bh2
		var bg := ctx.createLinearGradient(sx, 0, sx + 200, 0)
		bg.addColorStop(0, withAlpha(cond.color, 0.34 if hovC else (0.22 if live else 0.12)))
		bg.addColorStop(1, "rgba(12,9,5,0.6)")
		ctx.fillStyle = bg; roundRect(ctx, sx, sy, 200, bh2, 6); ctx.fill()
		ctx.strokeStyle = withAlpha(cond.color, 0.95 if hovC else 0.5); ctx.lineWidth = 1
		roundRect(ctx, sx + 0.5, sy + 0.5, 199, bh2 - 1, 6); ctx.stroke()
		weatherGlyph(ctx, sx + 17, sy + bh2 / 2, 8, cond, time)
		ui.text(cond.name, sx + 34, sy + 15, {"size": 12, "bold": true, "color": cond.color})
		ui.text("hover for what it changes" if live else "no effect this round",
			sx + 34, sy + 27, {"size": 9, "color": "#8a8278"})
		if hovC:
			condTip = {"c": cond, "x": sx + 208, "y": sy}
		sy += bh2 + 5
		# The ground's seed. Readable so a board you liked can be noted down, and
		# editable so it can actually be played again — a code you can only ever
		# read is a code you can only ever describe.
		if run.terrain.size() > 0 or seedEdit != null:
			var editing := seedEdit != null
			var canEdit := run.phase == "shop"
			var hovS := canEdit and ui.mx >= sx and ui.mx <= sx + 200 and ui.my >= sy and ui.my <= sy + 22
			ctx.fillStyle = "rgba(60,50,20,0.6)" if editing else ("rgba(255,255,255,0.08)" if hovS else "rgba(0,0,0,0.32)")
			roundRect(ctx, sx, sy, 200, 22, 4); ctx.fill()
			ctx.strokeStyle = "#ffd24a" if editing else ("rgba(255,210,74,0.5)" if hovS else "rgba(255,255,255,0.06)")
			ctx.lineWidth = 1; roundRect(ctx, sx + 0.5, sy + 0.5, 199, 21, 4); ctx.stroke()
			ui.text("type a seed" if editing else "ground seed", sx + 8, sy + 15, {
				"size": 9.5, "color": "#ffd24a" if editing else ("#cabfa4" if hovS else "#8a8278"),
			})
			var shown: String = (String(seedEdit) + ("_" if floori(time * 2) % 2 else "")) if editing else run.terrainCode()
			ui.text(shown, sx + 192, sy + 15, {
				"align": "right", "size": 11, "bold": true,
				"color": "#ffe9b0" if editing else "#cabfa4", "font": "ui-monospace, Menlo, monospace",
			})
			if hovS and ui.clicked and not ui.pointerConsumed:
				ui.pointerConsumed = true
				seedEdit = null if editing else ""
				audio.play("ui")
			sy += 26
			if editing:
				ui.text("enter to apply · esc to cancel", sx + 8, sy + 8, {"size": 9, "color": "#8a8278"})
				sy += 14

	# ---- relics ----
	itemTip = null
	if run.phase == "shop":
		sy += 14
		ui.text("Relics", sx, sy, {"size": 14, "bold": true, "color": PAL.uiAccent})
		ui.text("hover for details · click then a unit", sx + 56, sy, {"size": 9.5, "color": "#6f6a5c"})
		sy += 12
		if run.itemStash.is_empty():
			ui.text("— none yet —", sx, sy + 12, {"size": 11, "color": "#6f6a5c"}); sy += 22
		var tile := 42.0
		var gap := 6.0
		var per := 4
		for idx in run.itemStash.size():
			var id: String = run.itemStash[idx]
			var it = Items.itemDef(id)
			if it == null:
				continue
			var tilex := sx + (idx % per) * (tile + gap)
			var tiley := sy + floori(idx / float(per)) * (tile + gap)
			var sel := selectedItem == idx
			var hov := ui.mx >= tilex and ui.mx <= tilex + tile and ui.my >= tiley and ui.my <= tiley + tile
			var g := ctx.createLinearGradient(0, tiley, 0, tiley + tile)
			g.addColorStop(0, withAlpha(it.color, 0.5 if sel else (0.36 if hov else 0.22)))
			g.addColorStop(1, "rgba(12,9,5,0.95)")
			if sel or hov:
				ctx.save(); ctx.shadowColor = it.color; ctx.shadowBlur = 12 if sel else 7
			ctx.fillStyle = g; roundRect(ctx, tilex, tiley, tile, tile, 7); ctx.fill()
			if sel or hov:
				ctx.restore()
			ctx.strokeStyle = withAlpha(it.color, 1 if sel else 0.8); ctx.lineWidth = 2.5 if sel else 1.5
			# Components get a dashed edge — a part, not a finished relic.
			if it.get("component", false):
				ctx.setLineDash([4, 3])
			roundRect(ctx, tilex + 1, tiley + 1, tile - 2, tile - 2, 6); ctx.stroke()
			ctx.setLineDash([])
			itemIcon(ctx, tilex + tile / 2, tiley + tile / 2 - 2, tile * 0.42, it)
			if it.get("component", false):
				ui.text(it.short, tilex + tile / 2, tiley + tile - 5, {"align": "center", "size": 8, "bold": true, "color": withAlpha(it.color, 0.9)})
			if hov:
				itemTip = {"id": id, "x": tilex + tile + 8, "y": tiley}
			if hov and ui.clicked and not ui.pointerConsumed:
				ui.pointerConsumed = true; selectedItem = -1 if sel else idx
		if run.itemStash.size() > 0:
			sy += ceili(run.itemStash.size() / float(per)) * (tile + gap)

	ctx.restore() # end of the rail clip

	# ---- board geometry ----
	var board := boardRect(W, H)
	var boardX: float = board.x
	var boardY: float = board.y
	var boardW: float = board.w
	var boardH: float = board.h
	var benchH := BENCH_H
	var shopTop := H - SHOP_H
	var boardBottom := boardY + boardH

	# Keep a battle world around for the current matchup: during shop it's a
	# static preview (both warbands standing on the board); FIGHT begins it.
	if run.phase == "shop" or run.phase == "augment" or run.phase == "draft" or run.phase == "difficulty":
		var sig_ := sig(run)
		if sig_ != battleSig or battle == null:
			battle = Autobattle.LiveBattle.new(run.boardUnits(), run.pendingOpp, run.pendingSeed, 30.0, run.sideOpts(), run.fieldOpts())
			battleSig = sig_
	# A merge happened → celebrate the star-up over the board.
	if run.lastMerge != null:
		starUp = {"type": run.lastMerge.type, "star": run.lastMerge.star, "t": 0.0}
		run.lastMerge = null
		audio.play("levelup")
	if starUp != null:
		starUp.t += dt
		if starUp.t > 1.6:
			starUp = null
	if run.lastFusion != null:
		fusion = {"item": run.lastFusion.item, "type": run.lastFusion.type, "t": 0.0}
		run.lastFusion = null
		audio.play("complete")
	if fusion != null:
		fusion.t += dt
		if fusion.t > 1.7:
			fusion = null
	if picking:
		augT += dt
	else:
		augT = 0.0
	# Advance the live fight in real (scaled) time, then bank the result.
	if run.phase == "battle" and introT > 0:
		introT -= dt # the clash banner holds the fight for a beat
		stepAccum = 0.0
	elif run.phase == "battle" and battle != null:
		if not battle.started:
			battle.begin() # safety: always marching once fighting
		stepAccum += dt * 20 * BATTLE_SPEED # 20 = SIM_HZ
		var n := floori(stepAccum)
		if n > 0:
			stepAccum -= n; battle.step(n)
		if battle.fxEvents.size() > 0:
			fx.ingest(battle.fxEvents); battle.fxEvents.clear()
		if battle.done:
			run.finishFight(battle.result())
	fx.update(dt)
	# Phase-transition stingers.
	if run.phase != prevPhase:
		if run.phase == "battle":
			audio.play("alert")
			introT = INTRO_LEN
			starUp = null; fusion = null # shop flourishes don't follow you in
		elif run.phase == "result" and run.lastResult != null:
			audio.play("complete" if run.lastResult.won else "collapse")
			resultT = 0.0
			starUp = null; fusion = null
		elif run.phase == "shop":
			fx.clear(); resultT = -1.0; hpGhost.clear()
		prevPhase = run.phase
	if resultT >= 0:
		resultT += dt

	# ---- board title + matchup banner ----
	# One clean strip above the kerb: who you are on the left, who you face on
	# the right. Everything else about the round now lives in the rail.
	var stripY := boardY - BOARD_RIM - 8
	if true:
		var deployed := run.deployedCount()
		var cap := run.deployCount()
		ui.text("YOUR WARBAND", boardX - 2, stripY, {"size": 12, "bold": true, "color": PAL.uiAccent, "font": "Georgia, serif"})
		var dx0 := boardX + 112
		ui.text("%d / %d" % [deployed, cap], dx0, stripY, {
			"size": 12, "bold": true, "color": "#7df2a9" if deployed >= cap else ("#e0786a" if deployed == 0 else "#e7ddc4"),
		})
		# Deployment pips — how many of your slots are actually on the field.
		for i in cap:
			var px2 := dx0 + 40 + i * 9
			ctx.fillStyle = "#7df2a9" if i < deployed else "rgba(255,255,255,0.16)"
			ctx.beginPath(); ctx.arc(px2, stripY - 4, 2.8, 0, PI * 2); ctx.fill()
		if run.bossRound:
			# The crown. Nothing about this round is routine, so it doesn't get the
			# routine banner.
			var camp: Dictionary = run.pendingCamp
			var pulse := 0.5 + 0.5 * sin(time * 3)
			ctx.save(); ctx.shadowColor = "#ff6a52"; ctx.shadowBlur = 8 + pulse * 12
			ui.text(String(camp.name).to_upper(), boardX + boardW - 8, stripY, {
				"size": 14, "bold": true, "align": "right", "color": "#ff9a86", "font": "Georgia, serif",
			})
			ctx.restore()
			ui.text("FINAL CHALLENGE", boardX + boardW - 24 - String(camp.name).length() * 8.6, stripY, {
				"size": 10.5, "bold": true, "align": "right", "color": withAlpha("#e0564a", 0.6 + pulse * 0.4),
			})
		elif run.isCreepRound():
			# A PvE camp round reads differently: loot on the line, not your life.
			var camp: Dictionary = run.pendingCamp
			roundGlyph(ctx, boardX + boardW - 8, stripY - 5, 6, "camp", "#c8a86a")
			ui.text(String(camp.name).to_upper(), boardX + boardW - 20, stripY, {"size": 12, "bold": true, "align": "right", "color": "#c8a86a", "font": "Georgia, serif"})
			ui.text("win → %d relic%s + %dg" % [camp.relics, "s" if camp.relics > 1 else "", camp.gold],
				boardX + boardW - 32 - String(camp.name).length() * 7.4, stripY, {"size": 10.5, "align": "right", "color": "#9a917b"})
		else:
			roundGlyph(ctx, boardX + boardW - 8, stripY - 5, 6, "pvp", "#e0786a")
			ui.text(run.pendingFoeName().to_upper(), boardX + boardW - 20, stripY, {"size": 12, "bold": true, "align": "right", "color": "#e0786a", "font": "Georgia, serif"})
			ui.text("vs", boardX + boardW - 26 - run.pendingFoeName().length() * 7.4, stripY, {"size": 10.5, "align": "right", "color": "#8a8278"})

	# The scout panel floats over the left of the board — swallow the pointer
	# there so a click on it never lands on a board cell underneath.
	if scoutId >= 0:
		var r := scoutRect
		if ui.mx >= r.x and ui.mx <= r.x + r.w and ui.my >= r.y and ui.my <= r.y + r.h:
			ui.pointerConsumed = true

	drawBoard(boardX, boardY, boardW, boardH, time, dt, run)

	# ---- bench (your pieces) ----
	# A rack of nine slots, sized to the width the board actually occupies so
	# the bench reads as the board's shelf rather than a loose row of cards.
	var benchUsed := run.benchCount()
	var rackY := boardBottom + BOARD_RIM + 4
	var rackH := benchH - 8
	var railSpan := W - RAIL_W - 24 # the whole field right of the rail
	# Bench = your reserve (non-deployed) pieces. Never fewer slots than there
	# are reserves: a piece you own but cannot see is a piece you cannot sell,
	# and the board can overflow the bench in edge cases.
	var reserve: Array = []
	for i in run.pieces.size():
		if not run.pieces[i].deployed:
			reserve.append(i)
	var slots := maxi(Warband.BENCH_SLOTS, reserve.size())
	var cardW := maxf(52, minf(104, floorf((railSpan - (slots - 1) * 7) / slots)))
	var rackW := slots * cardW + (slots - 1) * 7
	var rackX := JS.round(RAIL_W + 12 + (railSpan - rackW) / 2)
	# The shelf the slots sit in.
	var rg := ctx.createLinearGradient(0, rackY, 0, rackY + rackH)
	rg.addColorStop(0, "rgba(26,20,12,0.7)"); rg.addColorStop(1, "rgba(10,7,4,0.55)")
	ctx.fillStyle = rg; roundRect(ctx, rackX - 10, rackY - 2, rackW + 20, rackH + 4, 8); ctx.fill()
	ctx.strokeStyle = "rgba(202,165,106,0.18)"; ctx.lineWidth = 1
	roundRect(ctx, rackX - 9.5, rackY - 1.5, rackW + 19, rackH + 3, 8); ctx.stroke()
	ui.text("BENCH", rackX - 10, rackY - 8, {"size": 10, "bold": true, "color": "#8f8770"})
	ui.text("%d / %d" % [benchUsed, run.benchSlots()], rackX + 46, rackY - 8, {
		"size": 10, "bold": true, "color": "#e0786a" if run.benchFull() else "#6f6a5c",
	})
	if selectedItem >= 0 and selectedItem < run.itemStash.size():
		ui.text("◆ Relic armed — click a unit to equip it", rackX + rackW, rackY - 8, {
			"align": "right", "size": 11, "bold": true, "color": "#ffd24a",
		})
	elif selectedItem >= run.itemStash.size():
		selectedItem = -1

	# Drag one onto the board to field it, into the Sell box to sell it, or
	# click with a relic armed to equip.
	var cardH := rackH - 12
	for k in slots:
		var cx := rackX + k * (cardW + 7)
		var cy := rackY + 6
		if k >= reserve.size():
			# An empty slot: a recessed well, so "how much room is left" is visible.
			ctx.fillStyle = "rgba(0,0,0,0.34)"
			roundRect(ctx, cx, cy, cardW, cardH, 6); ctx.fill()
			ctx.strokeStyle = "rgba(255,255,255,0.07)"; ctx.lineWidth = 1
			ctx.setLineDash([4, 4])
			roundRect(ctx, cx + 0.5, cy + 0.5, cardW - 1, cardH - 1, 6); ctx.stroke()
			ctx.setLineDash([])
			ui.text(str(k + 1), cx + cardW / 2, cy + cardH / 2 + 5, {"align": "center", "size": 13, "bold": true, "color": "rgba(255,255,255,0.07)"})
			continue
		var i: int = reserve[k]
		pieceCard(cx, cy, cardW, cardH, run.pieces[i], false, time, heldPiece == i)
		var over := ui.mx >= cx and ui.mx <= cx + cardW and ui.my >= cy and ui.my <= cy + cardH
		if run.phase == "shop" and over and not ui.pointerConsumed:
			var equipping := selectedItem >= 0 and selectedItem < run.itemStash.size()
			if equipping and ui.clicked:
				ui.pointerConsumed = true
				if run.equipItem(selectedItem, i):
					selectedItem = -1
			elif not equipping and pressEdge and heldPiece < 0:
				heldPiece = i; heldFromBoard = false; grabbedThisPress = true; ui.pointerConsumed = true; audio.play("select")

	# ---- shop / actions (bottom) ----
	var shopY := shopTop
	if true:
		# A carved counter, not a flat black band: dark leather with a lit lip.
		var bg := ctx.createLinearGradient(0, shopY - 10, 0, H)
		bg.addColorStop(0, "rgba(24,18,10,0.97)"); bg.addColorStop(0.2, "rgba(12,9,5,0.96)"); bg.addColorStop(1, "rgba(6,4,2,0.98)")
		ctx.fillStyle = bg; ctx.fillRect(0, shopY - 10, W, H - shopY + 10)
		ctx.fillStyle = "rgba(202,165,106,0.22)"; ctx.fillRect(0, shopY - 10, W, 1.5)
		var lip := ctx.createLinearGradient(0, shopY - 9, 0, shopY + 4)
		lip.addColorStop(0, "rgba(202,165,106,0.10)"); lip.addColorStop(1, "rgba(202,165,106,0)")
		ctx.fillStyle = lip; ctx.fillRect(0, shopY - 9, W, 13)

	if run.phase == "shop":
		# The counter is one block: five cards, then a column of actions. The
		# whole block is centred under the board so it never drifts off to a side.
		var ACT_W := 236.0
		var sh := SHOP_H - 26
		var span := W - RAIL_W - 24
		var sw := maxf(90, minf(142, floorf((span - ACT_W - 48) / 5)))
		var blockW := 5 * (sw + 8) + ACT_W
		var shopX := JS.round(RAIL_W + 12 + maxf(0, (span - blockW) / 2))
		var cy := shopY + 14
		ui.text("SHOP", shopX, shopY + 6, {"size": 10, "bold": true, "color": "#8f8770"})
		var odds := run.shopOdds()
		var oddsParts: Array = []
		for i in odds.size():
			if odds[i] > 0:
				oddsParts.append("T%d %d%%" % [i + 1, odds[i]])
		ui.text("   ".join(oddsParts), shopX + 44, shopY + 6, {"size": 9.5, "color": "#6f6a5c"})
		# What a bought unit would push off a full board, so a hovered card can
		# be measured against the thing it actually competes with.
		var benchmark = run.weakestDeployed()
		for i in run.shop.size():
			var type = run.shop[i]
			var cx := shopX + i * (sw + 8)
			if type != null:
				var can := run.canBuy(i)
				var slot := i
				shopCard(cx, cy, sw, sh, type, can.ok, run.poolCount(type), can.get("reason"), time, benchmark, func():
					if run.buy(slot):
						audio.play("coin"))
			else:
				# A bought-out slot still holds its place in the row.
				ctx.fillStyle = "rgba(0,0,0,0.3)"; roundRect(ctx, cx, cy, sw, sh, 8); ctx.fill()
				ctx.strokeStyle = "rgba(255,255,255,0.05)"; ctx.lineWidth = 1
				ctx.setLineDash([4, 4]); roundRect(ctx, cx + 0.5, cy + 0.5, sw - 1, sh - 1, 8); ctx.stroke(); ctx.setLineDash([])
				ui.text("bought", cx + sw / 2, cy + sh / 2 + 4, {"align": "center", "size": 10, "color": "rgba(255,255,255,0.14)"})
		# Action column, right of the cards.
		var rxx := shopX + 5 * (sw + 8) + 4
		var rc := run.rerollCost()
		var freeR := run.freeRerollsLeft()
		if ui.button("Reroll — FREE ×%d" % freeR if freeR > 0 else "Reroll  %dg" % rc, rxx, cy, 138, 30, {
			"disabled": run.gold < rc, "size": 12.5, "accent": freeR > 0,
			"tooltip": ["New shop", "Your commander covers this one." if freeR > 0 else "Costs %d gold." % rc],
		}):
			if run.reroll():
				audio.play("ui")
		# Hold this shop through the round — the "one copy off a 3★" button.
		if ui.button("Held" if run.shopLocked else "Lock", rxx + 144, cy, 86, 30, {
			"accent": run.shopLocked, "size": 12,
			"tooltip": ["This shop is held" if run.shopLocked else "Hold this shop",
				"Keeps these five through the coming round instead of rolling them away.",
				"Rerolling releases the hold."],
		}):
			if run.toggleShopLock():
				audio.play("ui")
		# Level-up shows the shop odds it would unlock — the real reason to tech.
		var oddsTip: Array = []
		for i in odds.size():
			if odds[i] > 0:
				oddsTip.append("T%d %d%%" % [i + 1, odds[i]])
		if ui.button("Max Level" if run.level >= 9 else "▲ Level Up  4g", rxx, cy + 36, 138, 30, {
			"disabled": run.gold < 4 or run.level >= 9, "size": 12.5,
			"tooltip": ["+4 XP · bigger board", "Shop odds now: %s" % "  ".join(oddsTip)],
		}):
			if run.buyXp():
				audio.play("levelup")
		# What this fight is actually worth risking, before you commit to it.
		var worst := run.worstCaseDamage()
		var sg := run.streakGold()
		ui.text("risk −%d life" % worst, rxx + 144, cy + 48, {
			"align": "left", "size": 10.5, "bold": true, "color": "#e0564a" if worst >= run.life else "#e0a878",
		})
		ui.text("streak +%dg" % sg if sg > 0 else "no streak", rxx + 144, cy + 62, {
			"align": "left", "size": 10.5, "color": "#7df2a9" if sg > 0 else "#6f6a5c",
		})
		if ui.button("FIGHT", rxx, cy + 72, 138, sh - 72, {"accent": true, "size": 17, "tooltip": ["Send your warband into the arena."]}):
			if run.beginFight():
				battle = Autobattle.LiveBattle.new(run.boardUnits(), run.pendingOpp, run.pendingSeed, 30.0, run.sideOpts(), run.fieldOpts())
				battle.begin()
				stepAccum = 0.0
				heldPiece = -1
	elif run.phase == "battle":
		# A live readout of the fight, so the bar isn't dead air while it plays.
		var alive: Array = []
		if battle != null:
			alive = battle.world.entities.filter(func(e): return e.alive and e.kind == T.Kind.Unit and e.type != "villager")
		var mine := alive.filter(func(e): return e.team == 0).size()
		var theirs := alive.size() - mine
		ui.text("Battle in progress…", W / 2, shopY + 34, {"align": "center", "size": 21, "bold": true, "color": "#ffd24a", "font": "Georgia, serif"})
		var barW := minf(520, W - RAIL_W - 80)
		var bxL := W / 2 - barW / 2
		var byL := shopY + 52
		var total := maxi(1, mine + theirs)
		ctx.fillStyle = "rgba(0,0,0,0.55)"; roundRect(ctx, bxL, byL, barW, 12, 6); ctx.fill()
		ctx.save(); roundRect(ctx, bxL, byL, barW, 12, 6); ctx.clip()
		ctx.fillStyle = "#4a93e8"; ctx.fillRect(bxL, byL, barW * (float(mine) / total), 12)
		ctx.fillStyle = "#e0564a"; ctx.fillRect(bxL + barW * (float(mine) / total), byL, barW * (float(theirs) / total), 12)
		ctx.restore()
		ui.text("%d standing" % mine, bxL - 8, byL + 11, {"align": "right", "size": 12, "bold": true, "color": "#7fb0e8"})
		ui.text("%d standing" % theirs, bxL + barW + 8, byL + 11, {"size": 12, "bold": true, "color": "#e88a7f"})
		ui.text("vs %s" % run.pendingFoeName(), W / 2, shopY + 84, {"align": "center", "size": 13, "color": "#8a8278"})
	elif run.phase == "result" and run.lastResult != null:
		var r: Dictionary = run.lastResult
		var title: String
		if r.creep:
			title = ("%s cleared!" % r.foe) if r.won else ("%s drove you off" % r.foe)
		else:
			title = ("Victory vs %s!" % r.foe) if r.won else ("Defeat vs %s" % r.foe)
		ui.text(title, W / 2, shopY + 30, {
			"align": "center", "size": 24, "bold": true, "color": "#7df2a9" if r.won else "#e0564a", "font": "Georgia, serif",
		})
		if r.creep and r.won:
			# Camp loot is the whole point of the round — spell it out.
			ui.text("Loot:  %d relic%s  ·  %d gold" % [r.relics, "" if r.relics == 1 else "s", r.gold],
				W / 2, shopY + 58, {"align": "center", "size": 15, "bold": true, "color": "#ffd24a"})
		else:
			var line: String
			if r.won:
				line = "%d of your warband survived." % r.youLeft
			elif r.creep:
				line = "The camp holds. You lost %d life." % r.dmg
			else:
				line = "You lost %d life (%d enemies left standing)." % [r.dmg, r.foeLeft]
			ui.text(line, W / 2, shopY + 58, {"align": "center", "size": 14, "color": "#d8cdb4"})
		if ui.button("Continue", W / 2 - 80, shopY + 80, 160, 40, {"accent": true, "size": 16}):
			run.next()

	# ---- run over ----
	if run.phase == "over" and drawRunSummary(W, H, run, time):
		action = "exit"

	# ---- held unit rides the cursor ----
	if run.phase == "shop" and heldPiece >= 0 and heldPiece < run.pieces.size():
		var p: Warband.Piece = run.pieces[heldPiece]
		var cwd := 92.0
		var chd := 28.0
		var cxp := minf(ui.mx + 14, W - cwd - 6) # keep the label on-screen near edges
		var cyp := minf(ui.my + 6, H - chd - 6)
		ctx.fillStyle = "rgba(20,16,9,0.96)"; ctx.fillRect(cxp, cyp, cwd, chd)
		ctx.strokeStyle = "#ffd24a"; ctx.lineWidth = 1.5; ctx.strokeRect(cxp + 0.5, cyp + 0.5, cwd - 1, chd - 1)
		ui.text("%s %s" % [shortName(p.type), stars(p.star)], cxp + cwd / 2, cyp + 18, {"align": "center", "size": 12, "bold": true, "color": "#ffe9b0"})
	if run.phase != "shop":
		heldPiece = -1 # never carry a held unit out of setup

	# ---- overlays, back to front ----
	if run.phase == "battle" and introT > 0:
		drawIntro(boardX, boardY, boardW, boardH, run)
	if run.phase == "result" and run.lastResult != null:
		drawResultFlourish(boardX, boardY, boardW, boardH, run, time)
	if run.phase != "over":
		drawStarUp(boardX, boardY, boardW, boardH)
		drawFusion(boardX, boardY, boardW, boardH)
	if scoutId >= 0 and run.phase != "over":
		drawScoutPanel(W, H, run, time)
	if picking:
		unitTip = null; itemTip = null; augTip = null; cmdTip = null; condTip = null
		if run.phase == "difficulty":
			drawDifficultyPicker(W, H, run, time)
		elif run.phase == "commander":
			drawCommanderPicker(W, H, run, time)
		elif run.phase == "draft":
			drawDraftPicker(W, H, run, time)
		else:
			drawAugmentPicker(W, H, run, time)
	drawItemTip(W, H)
	drawUnitTip(W, H)
	drawLiveTip(W, H)
	drawAugTip(W, H)
	drawCmdTip(W, H)
	drawCondTip(W, H)
	wasDown = down

	return action


## Greedy word-wrap to a character budget per line.
func wrap(text: String, per: int) -> Array:
	var words := text.split(" ")
	var lines: Array = []
	var line := ""
	for w in words:
		if line != "" and (line + " " + w).length() > per:
			lines.append(line); line = w
		else:
			line = (line + " " + w) if line != "" else w
	if line != "":
		lines.append(line)
	return lines


## A distinct procedural sigil per augment, picked from what the augment
## actually does — coins for economy, a blade for attack, a shield for bulk,
## a banner for board slots and synergies, a gem for relics.
func augSigil(ctx: Ctx, cx: float, cy: float, r: float, a: Dictionary, time: float) -> void:
	var col := AUG_COLOR(a.tier)
	ctx.save()
	ctx.translate(cx, cy)
	ctx.lineJoin = "round"; ctx.lineCap = "round"
	ctx.fillStyle = col; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = maxf(1, r * 0.14)
	var h := r
	var buff = a.get("buff")
	if a.get("boardSlots"): # board slots: a rank of cells with a fresh one lighting up
		var cw := h * 0.52
		var gap2 := h * 0.14
		for i in 3:
			var cxx := -cw - gap2 + i * (cw + gap2)
			var fresh := i == 2
			ctx.fillStyle = col if fresh else withAlpha(col, 0.28)
			ctx.strokeStyle = withAlpha(col, 0.9); ctx.lineWidth = maxf(1, r * 0.1)
			ctx.beginPath(); ctx.rect(cxx - cw / 2, -cw / 2, cw, cw); ctx.fill(); ctx.stroke()
			if fresh: # a plus on the new slot
				ctx.strokeStyle = "rgba(20,15,8,0.9)"; ctx.lineWidth = maxf(1.2, r * 0.14)
				ctx.beginPath()
				ctx.moveTo(cxx, -cw * 0.28); ctx.lineTo(cxx, cw * 0.28)
				ctx.moveTo(cxx - cw * 0.28, 0); ctx.lineTo(cxx + cw * 0.28, 0); ctx.stroke()
	elif a.get("traitBonus") != null: # banner on a pole
		ctx.strokeStyle = "#6a4a2a"; ctx.lineWidth = r * 0.2
		ctx.beginPath(); ctx.moveTo(-h * 0.5, -h); ctx.lineTo(-h * 0.5, h); ctx.stroke()
		ctx.fillStyle = col; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = maxf(1, r * 0.11)
		ctx.beginPath()
		ctx.moveTo(-h * 0.5, -h * 0.85); ctx.lineTo(h * 0.85, -h * 0.85); ctx.lineTo(h * 0.5, -h * 0.2)
		ctx.lineTo(h * 0.85, h * 0.45); ctx.lineTo(-h * 0.5, h * 0.45); ctx.closePath(); ctx.fill(); ctx.stroke()
	elif a.get("relicEvery") or a.get("relics"): # gem
		ctx.beginPath()
		ctx.moveTo(0, -h); ctx.lineTo(h * 0.85, -h * 0.2); ctx.lineTo(0, h); ctx.lineTo(-h * 0.85, -h * 0.2)
		ctx.closePath(); ctx.fill(); ctx.stroke()
		ctx.fillStyle = "rgba(255,255,255,0.45)"
		ctx.beginPath(); ctx.moveTo(0, -h); ctx.lineTo(h * 0.4, -h * 0.3); ctx.lineTo(0, 0); ctx.closePath(); ctx.fill()
	elif a.get("gold") or a.get("bounty") or a.get("interestCap") or a.get("rerollCost") != null: # coin stack
		for i in range(2, -1, -1):
			var yy := h * 0.45 - i * h * 0.42
			var g := ctx.createRadialGradient(-r * 0.2, yy - r * 0.2, 1, 0, yy, r * 0.9)
			g.addColorStop(0, "#ffe89a"); g.addColorStop(1, col)
			ctx.fillStyle = g
			ctx.beginPath(); ctx.ellipse(0, yy, h * 0.85, h * 0.36, 0, 0, PI * 2); ctx.fill()
			ctx.strokeStyle = "rgba(0,0,0,0.5)"; ctx.lineWidth = maxf(1, r * 0.1); ctx.stroke()
	elif a.get("xp"): # chevrons rising
		ctx.strokeStyle = col; ctx.lineWidth = maxf(1.5, r * 0.24); ctx.fillStyle = "transparent"
		for i in 3:
			var yy := h * 0.6 - i * h * 0.55
			ctx.beginPath(); ctx.moveTo(-h * 0.7, yy); ctx.lineTo(0, yy - h * 0.45); ctx.lineTo(h * 0.7, yy); ctx.stroke()
	elif a.get("life"): # heart
		ctx.beginPath()
		ctx.moveTo(0, h * 0.85)
		ctx.bezierCurveTo(-h * 1.3, -h * 0.1, -h * 0.55, -h * 1.05, 0, -h * 0.35)
		ctx.bezierCurveTo(h * 0.55, -h * 1.05, h * 1.3, -h * 0.1, 0, h * 0.85)
		ctx.closePath(); ctx.fill(); ctx.stroke()
	elif buff != null and (buff.get("armor") or buff.get("hpPct") or buff.get("hp")): # shield
		ctx.beginPath()
		ctx.moveTo(0, -h); ctx.lineTo(h * 0.82, -h * 0.55); ctx.lineTo(h * 0.82, h * 0.2)
		ctx.quadraticCurveTo(h * 0.82, h * 0.92, 0, h)
		ctx.quadraticCurveTo(-h * 0.82, h * 0.92, -h * 0.82, h * 0.2)
		ctx.lineTo(-h * 0.82, -h * 0.55); ctx.closePath(); ctx.fill(); ctx.stroke()
	else: # blade
		ctx.beginPath()
		ctx.moveTo(0, -h); ctx.lineTo(h * 0.24, -h * 0.15); ctx.lineTo(h * 0.24, h * 0.35)
		ctx.lineTo(-h * 0.24, h * 0.35); ctx.lineTo(-h * 0.24, -h * 0.15); ctx.closePath(); ctx.fill(); ctx.stroke()
		ctx.fillStyle = "#caa56a"
		ctx.beginPath(); ctx.rect(-h * 0.6, h * 0.33, h * 1.2, h * 0.2); ctx.fill(); ctx.stroke()
	# Prismatic augments get a slow sparkle.
	if a.tier == "prismatic":
		var tw := 0.5 + 0.5 * sin(time * 3 + cx * 0.1)
		ctx.globalAlpha = 0.5 + tw * 0.5
		ctx.fillStyle = "#fff"
		ctx.beginPath(); ctx.arc(h * 0.75, -h * 0.75, maxf(0.8, r * 0.13), 0, PI * 2); ctx.fill()
	ctx.restore()


## The augment pick — a full-screen modal with three dealt-in cards. This is
## the run's fork in the road, so it gets the weight: dimmed board behind,
## tier-coloured cards, a sigil, and the exact effect spelled out.
func drawAugmentPicker(W: float, H: float, run: Warband.WarbandRun, time: float) -> void:
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var offer := run.augmentOffer
	if offer.is_empty():
		return
	var tier: String = offer[0].tier
	var tcol := AUG_COLOR(tier)

	# Dim everything behind, with a warm pool of light behind the cards.
	ctx.fillStyle = "rgba(5,4,2,0.82)"; ctx.fillRect(0, 0, W, H)
	var glow := ctx.createRadialGradient(W / 2, H / 2, 40, W / 2, H / 2, maxf(W, H) * 0.55)
	glow.addColorStop(0, withAlpha(tcol, 0.16)); glow.addColorStop(1, "rgba(0,0,0,0)")
	ctx.fillStyle = glow; ctx.fillRect(0, 0, W, H)

	# Slow rotating rays for the prismatic pick — it should feel like an event.
	if tier == "prismatic":
		ctx.save()
		ctx.translate(W / 2, H / 2); ctx.rotate(time * 0.12); ctx.globalAlpha = 0.085
		for i in 12:
			ctx.rotate(PI / 6)
			ctx.fillStyle = tcol
			ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(maxf(W, H) * 0.7, -26); ctx.lineTo(maxf(W, H) * 0.7, 26); ctx.closePath(); ctx.fill()
		ctx.restore()

	var gap := 24.0
	var cardW := minf(258, (W - 100 - gap * 2) / 3)
	# Height follows the longest description, so a short augment doesn't leave
	# a band of empty card between its text and its button.
	var descLines := 0
	for a in offer:
		descLines = maxi(descLines, self.wrap(a.desc, 30).size())
	var cardH := minf(H * 0.62, 190 + descLines * 18 + 66)
	var totalW := cardW * offer.size() + gap * (offer.size() - 1)
	var x0 := (W - totalW) / 2
	var cy := H / 2 - cardH / 2 + 24

	var titleT := minf(1, augT * 3)
	ctx.globalAlpha = titleT
	ui.text("CHOOSE AN AUGMENT", W / 2, cy - 62, {"align": "center", "size": 30, "bold": true, "color": "#f2e8d0", "font": "Georgia, serif"})
	ui.text("%s · shapes the rest of your run" % tier.to_upper(), W / 2, cy - 38, {"align": "center", "size": 13, "bold": true, "color": tcol})
	ctx.globalAlpha = 1

	augPick = -1
	for i in offer.size():
		var a: Dictionary = offer[i]
		# Staggered deal-in: slide up + fade, one card after another.
		var t := maxf(0, minf(1, (augT - i * 0.11) * 3.2))
		var ease := 1 - pow(1 - t, 3)
		if t <= 0:
			continue
		var col := AUG_COLOR(a.tier)
		var x := x0 + i * (cardW + gap)
		var hov := ui.mx >= x and ui.mx <= x + cardW and ui.my >= cy and ui.my <= cy + cardH and t >= 1
		if hov:
			augPick = i
		var lift := (-8.0 if hov else 0.0) + (1 - ease) * 46
		var y := cy + lift

		ctx.save()
		ctx.globalAlpha = ease
		# Card body.
		ctx.save()
		ctx.shadowColor = withAlpha(col, 0.75) if hov else "rgba(0,0,0,0.65)"
		ctx.shadowBlur = 30 if hov else 16; ctx.shadowOffsetY = 6
		var g := ctx.createLinearGradient(0, y, 0, y + cardH)
		g.addColorStop(0, withAlpha(col, 0.4 if hov else 0.24))
		g.addColorStop(0.42, "rgba(24,18,11,0.98)")
		g.addColorStop(1, "rgba(12,9,5,0.99)")
		ctx.fillStyle = g; roundRect(ctx, x, y, cardW, cardH, 13); ctx.fill()
		ctx.restore()
		ctx.strokeStyle = withAlpha(col, 1 if hov else 0.7); ctx.lineWidth = 2.6 if hov else 1.6
		roundRect(ctx, x + 1.5, y + 1.5, cardW - 3, cardH - 3, 12); ctx.stroke()

		# Tier chip.
		var chipW := 74.0
		ctx.fillStyle = withAlpha(col, 0.22)
		roundRect(ctx, x + cardW / 2 - chipW / 2, y + 16, chipW, 17, 8); ctx.fill()
		ui.text(String(a.tier).to_upper(), x + cardW / 2, y + 29, {"align": "center", "size": 9.5, "bold": true, "color": col})

		# Sigil in a ring.
		var sy2 := y + 92
		ctx.save()
		ctx.shadowColor = withAlpha(col, 0.8); ctx.shadowBlur = 20 if hov else 10
		ctx.strokeStyle = withAlpha(col, 0.5); ctx.lineWidth = 1.5
		ctx.beginPath(); ctx.arc(x + cardW / 2, sy2, 36, 0, PI * 2); ctx.stroke()
		ctx.restore()
		ctx.fillStyle = "rgba(0,0,0,0.35)"
		ctx.beginPath(); ctx.arc(x + cardW / 2, sy2, 34, 0, PI * 2); ctx.fill()
		# A ring of runes turning slowly behind the sigil — the card should feel
		# like a charm being offered, not a bullet point.
		ctx.save()
		ctx.translate(x + cardW / 2, sy2); ctx.rotate(time * 0.18 + i)
		ctx.strokeStyle = withAlpha(col, 0.8 if hov else 0.45); ctx.lineWidth = 1.4; ctx.lineCap = "round"
		for k in 12:
			ctx.rotate((PI * 2) / 12)
			var len := 7.0 if k % 3 == 0 else 3.5
			ctx.beginPath(); ctx.moveTo(0, -44); ctx.lineTo(0, -44 - len); ctx.stroke()
		ctx.restore()
		augSigil(ctx, x + cardW / 2, sy2, 21, a, time)

		# Name + description.
		var nm := 15 if String(a.name).length() > 20 else 18
		ui.text(a.name, x + cardW / 2, y + 158, {"align": "center", "size": nm, "bold": true, "color": "#f7efdc", "font": "Georgia, serif"})
		ctx.strokeStyle = withAlpha(col, 0.35); ctx.lineWidth = 1
		ctx.beginPath(); ctx.moveTo(x + 34, y + 172); ctx.lineTo(x + cardW - 34, y + 172); ctx.stroke()
		var ly := y + 196
		for ln in self.wrap(a.desc, 30):
			ui.text(ln, x + cardW / 2, ly, {"align": "center", "size": 12.5, "color": "#d8cdb4"})
			ly += 18

		# Take button.
		var bw := cardW - 44
		var bh := 38.0
		var byy := y + cardH - bh - 18
		var bhov := ui.mx >= x + 22 and ui.mx <= x + 22 + bw and ui.my >= byy and ui.my <= byy + bh and t >= 1
		ctx.fillStyle = withAlpha(col, 0.85) if bhov else withAlpha(col, 0.24)
		roundRect(ctx, x + 22, byy, bw, bh, 8); ctx.fill()
		ctx.strokeStyle = withAlpha(col, 0.95); ctx.lineWidth = 1.5
		roundRect(ctx, x + 22.75, byy + 0.75, bw - 1.5, bh - 1.5, 8); ctx.stroke()
		ui.text("TAKE", x + cardW / 2, byy + 25, {"align": "center", "size": 15, "bold": true, "color": "#1a1207" if bhov else col})
		ctx.restore()

		if (hov or bhov) and ui.clicked and not ui.pointerConsumed:
			ui.pointerConsumed = true
			if run.pickAugment(i):
				audio.play("levelup"); battleSig = ""
	# Swallow any other click while the modal is up.
	if ui.clicked:
		ui.pointerConsumed = true


## The first choice of a run: how hard the other seven warbands play. They run
## the same economy you do, so this is a dial on how well they run it — shown
## as four bars per lobby rather than a vague word, because "Warlord" on its
## own tells you nothing about what changed.
func drawDifficultyPicker(W: float, H: float, run: Warband.WarbandRun, time: float) -> void:
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var offer := run.difficultyOffer()
	if offer.is_empty():
		return

	ctx.fillStyle = "rgba(5,4,2,0.88)"; ctx.fillRect(0, 0, W, H)
	var glow := ctx.createRadialGradient(W / 2, H / 2, 40, W / 2, H / 2, maxf(W, H) * 0.55)
	glow.addColorStop(0, withAlpha("#caa56a", 0.13)); glow.addColorStop(1, "rgba(0,0,0,0)")
	ctx.fillStyle = glow; ctx.fillRect(0, 0, W, H)

	var gap := 18.0
	var cardW := minf(246, (W - 120 - gap * 3) / 4)
	var lines := 0
	for d in offer:
		lines = maxi(lines, self.wrap(d.blurb, 30).size())
	var cardH := minf(H * 0.66, 176 + lines * 17 + 66)
	var totalW := cardW * offer.size() + gap * (offer.size() - 1)
	var x0 := (W - totalW) / 2
	var cy := H / 2 - cardH / 2 + 26

	ctx.globalAlpha = minf(1, augT * 3)
	ui.text("HOW HARD IS THE LOBBY?", W / 2, cy - 62, {"align": "center", "size": 30, "bold": true, "color": "#f2e8d0", "font": "Georgia, serif"})
	ui.text("Seven rival warbands run the same economy you do. This is how well they run it.",
		W / 2, cy - 38, {"align": "center", "size": 13, "bold": true, "color": "#caa56a"})
	ctx.globalAlpha = 1

	for i in offer.size():
		var d: Dictionary = offer[i]
		var t := maxf(0, minf(1, (augT - i * 0.09) * 3.2))
		var ease := 1 - pow(1 - t, 3)
		if t <= 0:
			continue
		var col: String = d.color
		var x := x0 + i * (cardW + gap)
		var hov := ui.mx >= x and ui.mx <= x + cardW and ui.my >= cy and ui.my <= cy + cardH and t >= 1
		var y := cy + (-8.0 if hov else 0.0) + (1 - ease) * 44

		ctx.save()
		ctx.globalAlpha = ease
		ctx.save()
		ctx.shadowColor = withAlpha(col, 0.75) if hov else "rgba(0,0,0,0.65)"
		ctx.shadowBlur = 28 if hov else 15; ctx.shadowOffsetY = 6
		var g := ctx.createLinearGradient(0, y, 0, y + cardH)
		g.addColorStop(0, withAlpha(col, 0.36 if hov else 0.2))
		g.addColorStop(0.4, "rgba(24,18,11,0.98)")
		g.addColorStop(1, "rgba(12,9,5,0.99)")
		ctx.fillStyle = g; roundRect(ctx, x, y, cardW, cardH, 12); ctx.fill()
		ctx.restore()
		ctx.strokeStyle = withAlpha(col, 1 if hov else 0.65); ctx.lineWidth = 2.4 if hov else 1.5
		roundRect(ctx, x + 1.25, y + 1.25, cardW - 2.5, cardH - 2.5, 11); ctx.stroke()

		# A rank of chevrons: one filled per step up the ladder.
		var marks := i + 1
		ctx.save()
		ctx.translate(x + cardW / 2, y + 40)
		ctx.strokeStyle = col; ctx.lineWidth = 2.4; ctx.lineCap = "round"; ctx.lineJoin = "round"
		for k in 4:
			ctx.globalAlpha = 1.0 if k < marks else 0.16
			var cyk := -14 + k * 9
			ctx.beginPath()
			ctx.moveTo(-13, cyk + 5); ctx.lineTo(0, cyk - 3); ctx.lineTo(13, cyk + 5)
			ctx.stroke()
		ctx.restore()

		ui.text(d.name, x + cardW / 2, y + 84, {"align": "center", "size": 21, "bold": true, "color": "#f7efdc", "font": "Georgia, serif"})
		ui.text(d.title, x + cardW / 2, y + 102, {"align": "center", "size": 11, "bold": true, "color": col})
		ctx.strokeStyle = withAlpha(col, 0.3); ctx.lineWidth = 1
		ctx.beginPath(); ctx.moveTo(x + 30, y + 114); ctx.lineTo(x + cardW - 30, y + 114); ctx.stroke()

		# The three dials this actually turns, as bars against Veteran's baseline.
		_dial(ctx, ui, x, cardW, col, "their gold", d.foeIncome, y + 134)
		_dial(ctx, ui, x, cardW, col, "their tech", d.foeLevelPace, y + 150)
		_dial(ctx, ui, x, cardW, col, "defeat hurts", d.damageTaken, y + 166)

		var ly := y + 190
		for ln in self.wrap(d.blurb, 30):
			ui.text(ln, x + cardW / 2, ly, {"align": "center", "size": 11, "color": "#cabfa4"})
			ly += 15

		var bw2 := cardW - 40
		var bh2 := 34.0
		var byy := y + cardH - bh2 - 16
		var bhov := ui.mx >= x + 20 and ui.mx <= x + 20 + bw2 and ui.my >= byy and ui.my <= byy + bh2 and t >= 1
		ctx.fillStyle = withAlpha(col, 0.85) if bhov else withAlpha(col, 0.22)
		roundRect(ctx, x + 20, byy, bw2, bh2, 7); ctx.fill()
		ctx.strokeStyle = withAlpha(col, 0.95); ctx.lineWidth = 1.4
		roundRect(ctx, x + 20.7, byy + 0.7, bw2 - 1.4, bh2 - 1.4, 7); ctx.stroke()
		ui.text("ENTER", x + cardW / 2, byy + 22, {"align": "center", "size": 14, "bold": true, "color": "#1a1207" if bhov else col})
		ctx.restore()

		if (hov or bhov) and ui.clicked and not ui.pointerConsumed:
			ui.pointerConsumed = true
			if run.pickDifficulty(d.id):
				audio.play("ui"); battleSig = ""
	if ui.clicked:
		ui.pointerConsumed = true


## One of the difficulty card's dials (the TS `dial` closure).
func _dial(ctx: Ctx, ui: UI, x: float, cardW: float, col: String, label: String, value: float, dy: float) -> void:
	var bw := cardW - 96
	ui.text(label, x + 16, dy, {"size": 9, "color": "#8a8278"})
	var bxD := x + 74
	var byD := dy - 7
	ctx.fillStyle = "rgba(0,0,0,0.5)"; roundRect(ctx, bxD, byD, bw, 6, 3); ctx.fill()
	# 0.6..1.4 maps across the bar, with the midpoint being "as you play".
	var f := maxf(0.04, minf(1, (value - 0.6) / 0.8))
	ctx.fillStyle = col; roundRect(ctx, bxD, byD, bw * f, 6, 3); ctx.fill()
	ctx.fillStyle = "rgba(255,255,255,0.35)"; ctx.fillRect(bxD + bw * 0.5, byD - 2, 1, 10)


## A commander's heraldic crest: a shield bearing a charge picked from what
## their perk actually does, over their own colour.
func crest(ctx: Ctx, cx: float, cy: float, r: float, c: Dictionary, time: float) -> void:
	var id = WarbandCommanders.commanderIdentity(c)
	var col: String = id.color if id != null else "#caa56a"
	ctx.save()
	ctx.translate(cx, cy)
	ctx.lineJoin = "round"; ctx.lineCap = "round"
	# Shield.
	var h := r
	var g := ctx.createLinearGradient(0, -h, 0, h)
	g.addColorStop(0, withAlpha(col, 0.95)); g.addColorStop(1, withAlpha(col, 0.45))
	ctx.fillStyle = g; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = maxf(1.4, r * 0.09)
	ctx.beginPath()
	ctx.moveTo(-h * 0.78, -h * 0.85); ctx.lineTo(h * 0.78, -h * 0.85); ctx.lineTo(h * 0.78, h * 0.15)
	ctx.quadraticCurveTo(h * 0.78, h * 0.9, 0, h)
	ctx.quadraticCurveTo(-h * 0.78, h * 0.9, -h * 0.78, h * 0.15)
	ctx.closePath(); ctx.fill(); ctx.stroke()
	# Charge — the perk, as a symbol.
	ctx.fillStyle = "rgba(16,11,6,0.86)"; ctx.strokeStyle = "rgba(16,11,6,0.86)"
	ctx.lineWidth = maxf(1.4, r * 0.13)
	var s := h * 0.46
	if c.get("freeRerolls"): # cycling arrows
		ctx.beginPath(); ctx.arc(0, 0, s, 0.6, PI * 1.6); ctx.stroke()
		ctx.beginPath(); ctx.moveTo(-s * 0.1, -s * 1.15); ctx.lineTo(s * 0.45, -s * 0.75); ctx.lineTo(-s * 0.25, -s * 0.4); ctx.closePath(); ctx.fill()
	elif c.get("fullRefund") or c.get("gold") or c.get("startGold"): # coin
		ctx.beginPath(); ctx.arc(0, 0, s * 0.9, 0, PI * 2); ctx.fill()
		ctx.fillStyle = withAlpha(col, 0.95)
		ctx.beginPath(); ctx.arc(0, 0, s * 0.42, 0, PI * 2); ctx.fill()
	elif c.get("boardSlots"): # a rank of cells, one new
		var cw := s * 0.62
		for i in 3:
			ctx.beginPath(); ctx.rect(-cw * 1.6 + i * cw * 1.15, -cw / 2, cw, cw)
			if i == 2:
				ctx.fill()
			else:
				ctx.stroke()
	elif c.get("shopLevelBonus"): # upward chevrons
		for i in 2:
			var yy := s * 0.5 - i * s * 0.75
			ctx.beginPath(); ctx.moveTo(-s * 0.8, yy); ctx.lineTo(0, yy - s * 0.6); ctx.lineTo(s * 0.8, yy); ctx.stroke()
	elif c.get("topTraitBonus"): # a banner
		ctx.beginPath(); ctx.moveTo(-s * 0.5, -s); ctx.lineTo(-s * 0.5, s); ctx.stroke()
		ctx.beginPath()
		ctx.moveTo(-s * 0.5, -s * 0.9); ctx.lineTo(s * 0.85, -s * 0.9); ctx.lineTo(s * 0.5, -s * 0.35)
		ctx.lineTo(s * 0.85, s * 0.2); ctx.lineTo(-s * 0.5, s * 0.2); ctx.closePath(); ctx.fill()
	elif c.get("lossShield"): # a cross
		ctx.beginPath()
		ctx.moveTo(0, -s); ctx.lineTo(0, s); ctx.moveTo(-s * 0.7, -s * 0.25); ctx.lineTo(s * 0.7, -s * 0.25)
		ctx.stroke()
	else: # crossed swords — the drilled warband
		for dir in [-1, 1]:
			ctx.beginPath(); ctx.moveTo(dir * -s * 0.8, s * 0.8); ctx.lineTo(dir * s * 0.8, -s * 0.9); ctx.stroke()
	ctx.restore()
	# A slow sheen across the shield.
	var sheen := 0.5 + 0.5 * sin(time * 1.6 + cx * 0.05)
	ctx.save()
	ctx.globalAlpha = 0.1 + sheen * 0.12
	ctx.fillStyle = "#fff"
	ctx.beginPath(); ctx.ellipse(cx - r * 0.3, cy - r * 0.45, r * 0.28, r * 0.5, -0.5, 0, PI * 2); ctx.fill()
	ctx.restore()


## The opening choice of the run: which commander leads your warband. Same
## card language as the augment pick, but heraldic — these are the realm's
## captains, and the perk is a rule of the run rather than a stat.
func drawCommanderPicker(W: float, H: float, run: Warband.WarbandRun, time: float) -> void:
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var offer := run.commanderOffer
	if offer.is_empty():
		return

	ctx.fillStyle = "rgba(5,4,2,0.86)"; ctx.fillRect(0, 0, W, H)
	var glow := ctx.createRadialGradient(W / 2, H / 2, 40, W / 2, H / 2, maxf(W, H) * 0.55)
	glow.addColorStop(0, withAlpha("#caa56a", 0.15)); glow.addColorStop(1, "rgba(0,0,0,0)")
	ctx.fillStyle = glow; ctx.fillRect(0, 0, W, H)

	var gap := 24.0
	var cardW := minf(266, (W - 100 - gap * 2) / 3)
	var perkLines := 0
	for c in offer:
		perkLines = maxi(perkLines, self.wrap(c.perk, 30).size())
	var cardH := minf(H * 0.62, 200 + perkLines * 18 + 66)
	var totalW := cardW * offer.size() + gap * (offer.size() - 1)
	var x0 := (W - totalW) / 2
	var cy := H / 2 - cardH / 2 + 22

	ctx.globalAlpha = minf(1, augT * 3)
	ui.text("WHO LEADS YOUR WARBAND?", W / 2, cy - 60, {"align": "center", "size": 30, "bold": true, "color": "#f2e8d0", "font": "Georgia, serif"})
	ui.text("Their command changes a rule of the whole run.", W / 2, cy - 36, {"align": "center", "size": 13, "bold": true, "color": "#caa56a"})
	ctx.globalAlpha = 1

	for i in offer.size():
		var c: Dictionary = offer[i]
		var t := maxf(0, minf(1, (augT - i * 0.11) * 3.2))
		var ease := 1 - pow(1 - t, 3)
		if t <= 0:
			continue
		var id = WarbandCommanders.commanderIdentity(c)
		var col: String = id.color if id != null else "#caa56a"
		var x := x0 + i * (cardW + gap)
		var hov := ui.mx >= x and ui.mx <= x + cardW and ui.my >= cy and ui.my <= cy + cardH and t >= 1
		var y := cy + (-8.0 if hov else 0.0) + (1 - ease) * 46

		ctx.save()
		ctx.globalAlpha = ease
		ctx.save()
		ctx.shadowColor = withAlpha(col, 0.75) if hov else "rgba(0,0,0,0.65)"
		ctx.shadowBlur = 30 if hov else 16; ctx.shadowOffsetY = 6
		var g := ctx.createLinearGradient(0, y, 0, y + cardH)
		g.addColorStop(0, withAlpha(col, 0.38 if hov else 0.22))
		g.addColorStop(0.45, "rgba(24,18,11,0.98)")
		g.addColorStop(1, "rgba(12,9,5,0.99)")
		ctx.fillStyle = g; roundRect(ctx, x, y, cardW, cardH, 13); ctx.fill()
		ctx.restore()
		ctx.strokeStyle = withAlpha(col, 1 if hov else 0.7); ctx.lineWidth = 2.6 if hov else 1.6
		roundRect(ctx, x + 1.5, y + 1.5, cardW - 3, cardH - 3, 12); ctx.stroke()

		crest(ctx, x + cardW / 2, y + 74, 40, c, time)

		ui.text(id.name if id != null else c.id, x + cardW / 2, y + 148, {"align": "center", "size": 20, "bold": true, "color": "#f7efdc", "font": "Georgia, serif"})
		ui.text(id.title if id != null else "", x + cardW / 2, y + 168, {"align": "center", "size": 12, "bold": true, "color": col})
		ctx.strokeStyle = withAlpha(col, 0.35); ctx.lineWidth = 1
		ctx.beginPath(); ctx.moveTo(x + 34, y + 182); ctx.lineTo(x + cardW - 34, y + 182); ctx.stroke()
		var ly := y + 206
		for ln in self.wrap(c.perk, 30):
			ui.text(ln, x + cardW / 2, ly, {"align": "center", "size": 12.5, "color": "#d8cdb4"})
			ly += 18

		var bw := cardW - 44
		var bh := 38.0
		var byy := y + cardH - bh - 18
		var bhov := ui.mx >= x + 22 and ui.mx <= x + 22 + bw and ui.my >= byy and ui.my <= byy + bh and t >= 1
		ctx.fillStyle = withAlpha(col, 0.85) if bhov else withAlpha(col, 0.24)
		roundRect(ctx, x + 22, byy, bw, bh, 8); ctx.fill()
		ctx.strokeStyle = withAlpha(col, 0.95); ctx.lineWidth = 1.5
		roundRect(ctx, x + 22.75, byy + 0.75, bw - 1.5, bh - 1.5, 8); ctx.stroke()
		ui.text("LEAD", x + cardW / 2, byy + 25, {"align": "center", "size": 15, "bold": true, "color": "#1a1207" if bhov else col})
		ctx.restore()

		if (hov or bhov) and ui.clicked and not ui.pointerConsumed:
			ui.pointerConsumed = true
			if run.pickCommander(i):
				audio.play("levelup"); battleSig = ""
	if ui.clicked:
		ui.pointerConsumed = true


## The carousel: the shared draft. Units ride out already carrying a
## component, and the warbands below you in the standings have already taken
## theirs — so the ring you see is what the field left behind.
func drawDraftPicker(W: float, H: float, run: Warband.WarbandRun, time: float) -> void:
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var ring := run.carousel
	if ring.is_empty():
		return

	ctx.fillStyle = "rgba(5,4,2,0.85)"; ctx.fillRect(0, 0, W, H)
	var glow := ctx.createRadialGradient(W / 2, H / 2, 40, W / 2, H / 2, maxf(W, H) * 0.55)
	glow.addColorStop(0, withAlpha("#7fb0e8", 0.14)); glow.addColorStop(1, "rgba(0,0,0,0)")
	ctx.fillStyle = glow; ctx.fillRect(0, 0, W, H)

	var gap := 14.0
	var cardW := minf(156, (W - 120 - gap * (ring.size() - 1)) / ring.size())
	var cardH := 320.0
	var totalW := cardW * ring.size() + gap * (ring.size() - 1)
	var x0 := (W - totalW) / 2
	var cy := H / 2 - cardH / 2 + 16

	ctx.globalAlpha = minf(1, augT * 3)
	ui.text("THE CAROUSEL", W / 2, cy - 62, {"align": "center", "size": 30, "bold": true, "color": "#f2e8d0", "font": "Georgia, serif"})
	ui.text(
		("%d warband%s below you picked first — take what's left." % [run.draftAhead, "" if run.draftAhead == 1 else "s"]) if run.draftAhead > 0
		else "You're last in the standings, so you pick first. Take the best of it.",
		W / 2, cy - 38, {"align": "center", "size": 13, "bold": true, "color": "#e0a878" if run.draftAhead > 0 else "#7df2a9"})
	ctx.globalAlpha = 1

	for i in ring.size():
		var p: Dictionary = ring[i]
		var t := maxf(0, minf(1, (augT - i * 0.07) * 3.4))
		var ease := 1 - pow(1 - t, 3)
		if t <= 0:
			continue
		var tier := tierOf(p.type)
		var col: String = TIER_COLOR[tier]
		var it = Items.itemDef(p.item)
		var x := x0 + i * (cardW + gap)
		var hov := ui.mx >= x and ui.mx <= x + cardW and ui.my >= cy and ui.my <= cy + cardH and t >= 1
		# The ring drifts, so it reads as a carousel rather than a row of cards.
		var drift := sin(time * 0.9 + i * 0.7) * 4
		var y := cy + (-10.0 if hov else 0.0) + drift + (1 - ease) * 40
		var pstar: int = int(p.get("star", 1))

		ctx.save()
		ctx.globalAlpha = ease
		ctx.save()
		ctx.shadowColor = withAlpha(col, 0.8) if hov else "rgba(0,0,0,0.65)"
		ctx.shadowBlur = 26 if hov else 14; ctx.shadowOffsetY = 5
		var g := ctx.createLinearGradient(0, y, 0, y + cardH)
		g.addColorStop(0, withAlpha(col, 0.42 if hov else 0.24))
		g.addColorStop(0.5, "rgba(24,18,11,0.98)")
		g.addColorStop(1, "rgba(12,9,5,0.99)")
		ctx.fillStyle = g; roundRect(ctx, x, y, cardW, cardH, 11); ctx.fill()
		ctx.restore()
		ctx.strokeStyle = withAlpha(col, 1 if hov else 0.7); ctx.lineWidth = 2.4 if hov else 1.5
		roundRect(ctx, x + 1.25, y + 1.25, cardW - 2.5, cardH - 2.5, 10); ctx.stroke()

		# The unit itself, riding the ring. A carousel where you can't see the
		# champion is just a list of names — the figure is the whole point.
		var stage := y + 96
		ctx.save()
		ctx.beginPath(); ctx.rect(x + 4, y + 8, cardW - 8, 108); ctx.clip()
		var halo2 := ctx.createRadialGradient(x + cardW / 2, stage - 22, 2, x + cardW / 2, stage - 22, 62)
		halo2.addColorStop(0, withAlpha(col, 0.42 if hov else 0.28)); halo2.addColorStop(1, "rgba(0,0,0,0)")
		ctx.fillStyle = halo2; ctx.fillRect(x + 4, y + 8, cardW - 8, 108)
		plinth(ctx, x + cardW / 2, stage, 28, col)
		portrait(ctx, x + cardW / 2, stage - 22, minf(cardW * 0.62, 96), p.type, pstar, time)
		ctx.restore()
		if pstar >= 2:
			ui.text(stars(pstar), x + cardW / 2, y + 24, {
				"align": "center", "size": 13, "color": "#ffd24a" if pstar >= 3 else "#cfe0ff",
			})

		var nm := shortName(p.type)
		ui.text(nm, x + cardW / 2, y + 130, {"align": "center", "size": 12 if nm.length() > 9 else 14.5, "bold": true, "color": "#f7efdc", "font": "Georgia, serif"})
		ui.text("Tier %d" % tier, x + cardW / 2, y + 146, {"align": "center", "size": 10, "color": col})
		# Its synergies, so you can draft toward a comp.
		var ty := y + 164
		for tr in Traits.traitsOf(p.type).slice(0, 2):
			ui.text(tr.name, x + cardW / 2, ty, {"align": "center", "size": 9.5, "color": tr.color})
			ty += 13

		# The component it rides out carrying, on its own shelf below.
		ctx.strokeStyle = withAlpha(col, 0.28); ctx.lineWidth = 1
		ctx.beginPath(); ctx.moveTo(x + 18, y + 196); ctx.lineTo(x + cardW - 18, y + 196); ctx.stroke()
		if it != null:
			ctx.save()
			ctx.shadowColor = withAlpha(it.color, 0.9); ctx.shadowBlur = 16 if hov else 8
			ctx.fillStyle = "rgba(8,6,3,0.9)"
			ctx.beginPath(); ctx.arc(x + cardW / 2, y + 220, 18, 0, PI * 2); ctx.fill()
			ctx.strokeStyle = withAlpha(it.color, 0.95); ctx.lineWidth = 1.4
			ctx.setLineDash([4, 3]); ctx.stroke(); ctx.setLineDash([])
			ctx.restore()
			itemIcon(ctx, x + cardW / 2, y + 220, 10.5, it)
			ui.text(it.name, x + cardW / 2, y + 252, {"align": "center", "size": 10.5, "bold": true, "color": it.color})
			ui.text(it.desc, x + cardW / 2, y + 265, {"align": "center", "size": 9, "color": "#8a8278"})

		var bw := cardW - 26
		var bh := 30.0
		var byy := y + cardH - bh - 14
		var bhov := ui.mx >= x + 13 and ui.mx <= x + 13 + bw and ui.my >= byy and ui.my <= byy + bh and t >= 1
		ctx.fillStyle = withAlpha(col, 0.85) if bhov else withAlpha(col, 0.22)
		roundRect(ctx, x + 13, byy, bw, bh, 7); ctx.fill()
		ctx.strokeStyle = withAlpha(col, 0.95); ctx.lineWidth = 1.4
		roundRect(ctx, x + 13.7, byy + 0.7, bw - 1.4, bh - 1.4, 7); ctx.stroke()
		ui.text("TAKE", x + cardW / 2, byy + 20, {"align": "center", "size": 13, "bold": true, "color": "#1a1207" if bhov else col})
		ctx.restore()

		if (hov or bhov) and ui.clicked and not ui.pointerConsumed:
			ui.pointerConsumed = true
			if run.takeCarousel(i):
				audio.play("coin"); battleSig = ""
	if ui.clicked:
		ui.pointerConsumed = true


## A unit's battle-style mark: an arrow-and-line glyph saying how it opens the
## fight. Drawn small on the board so a comp's shape reads at a glance.
func styleGlyph(ctx: Ctx, cx: float, cy: float, r: float, def: Dictionary) -> void:
	ctx.save()
	ctx.translate(cx, cy)
	ctx.lineJoin = "round"; ctx.lineCap = "round"
	ctx.strokeStyle = def.color; ctx.fillStyle = def.color; ctx.lineWidth = maxf(1, r * 0.3)
	match def.id:
		"vanguard": # a wall
			ctx.beginPath(); ctx.moveTo(0, -r); ctx.lineTo(0, r); ctx.stroke()
			ctx.beginPath(); ctx.moveTo(-r * 0.7, -r * 0.45); ctx.lineTo(-r * 0.1, 0); ctx.lineTo(-r * 0.7, r * 0.45); ctx.stroke()
		"line": # straight arrow
			ctx.beginPath(); ctx.moveTo(-r, 0); ctx.lineTo(r * 0.55, 0); ctx.stroke()
			ctx.beginPath(); ctx.moveTo(r * 0.15, -r * 0.5); ctx.lineTo(r, 0); ctx.lineTo(r * 0.15, r * 0.5); ctx.closePath(); ctx.fill()
		"flanker": # a hooking arrow
			ctx.beginPath()
			ctx.moveTo(-r, r * 0.5)
			ctx.quadraticCurveTo(-r * 0.1, r * 0.6, r * 0.3, -r * 0.4)
			ctx.stroke()
			ctx.beginPath(); ctx.moveTo(r * 0.65, -r * 0.35); ctx.lineTo(r * 0.2, -r); ctx.lineTo(r * 0.75, -r * 0.85); ctx.closePath(); ctx.fill()
		"artillery": # an anchored mark with an arc over it
			ctx.beginPath(); ctx.arc(0, r * 0.25, r * 0.35, 0, PI * 2); ctx.fill()
			ctx.beginPath(); ctx.arc(0, r * 0.25, r * 0.95, PI * 1.15, PI * 1.85); ctx.stroke()
		"infiltrator": # a dashed jump over the line
			ctx.setLineDash([r * 0.42, r * 0.34])
			ctx.beginPath(); ctx.arc(0, r * 0.6, r, PI * 1.12, PI * 1.88); ctx.stroke()
			ctx.setLineDash([])
			ctx.beginPath(); ctx.moveTo(r * 0.45, -r * 0.5); ctx.lineTo(r * 1.05, -r * 0.05); ctx.lineTo(r * 0.4, r * 0.1); ctx.closePath(); ctx.fill()
	ctx.restore()


## The round's ground: boulders that take a cell off the board, rises that
## extend reach, mires that bog you down. Drawn under the units, and mirrored
## across the centre line by the generator so both halves are the same
## problem.
func drawTerrain(bx: float, by: float, cellW: float, cellH: float, features: Array, time: float) -> void:
	if features.is_empty():
		return
	var ctx := UI.ui.ctx
	for f in features:
		var def: Dictionary = Terrain.TERRAIN[f.kind]
		var cx: float = bx + (f.col + 0.5) * cellW
		var cy: float = by + (f.row + 0.5) * cellH
		var rx := cellW * 0.42
		var ry := cellH * 0.36
		ctx.save()
		if f.kind == "boulder":
			# A solid lump with a lit top and a shadow — reads as "not a cell".
			ctx.fillStyle = "rgba(0,0,0,0.4)"
			ctx.beginPath(); ctx.ellipse(cx, cy + ry * 0.5, rx * 0.9, ry * 0.5, 0, 0, PI * 2); ctx.fill()
			var g := ctx.createLinearGradient(cx, cy - ry, cx, cy + ry)
			g.addColorStop(0, "#a8a294"); g.addColorStop(1, "#4a463e")
			ctx.fillStyle = g
			ctx.beginPath()
			ctx.moveTo(cx - rx * 0.85, cy + ry * 0.45)
			ctx.lineTo(cx - rx * 0.6, cy - ry * 0.5)
			ctx.lineTo(cx - rx * 0.05, cy - ry * 0.8)
			ctx.lineTo(cx + rx * 0.6, cy - ry * 0.35)
			ctx.lineTo(cx + rx * 0.85, cy + ry * 0.45)
			ctx.closePath(); ctx.fill()
			ctx.strokeStyle = "rgba(0,0,0,0.45)"; ctx.lineWidth = 1.2; ctx.stroke()
		elif f.kind == "rise":
			# A shallow terrace: a lit plateau with a contour line around it.
			ctx.fillStyle = withAlpha(def.color, 0.2)
			ctx.beginPath(); ctx.ellipse(cx, cy, rx, ry, 0, 0, PI * 2); ctx.fill()
			ctx.strokeStyle = withAlpha(def.color, 0.7); ctx.lineWidth = 1.4
			ctx.beginPath(); ctx.ellipse(cx, cy, rx, ry, 0, 0, PI * 2); ctx.stroke()
			ctx.strokeStyle = withAlpha(def.color, 0.45); ctx.lineWidth = 1
			ctx.beginPath(); ctx.ellipse(cx, cy + ry * 0.16, rx * 0.6, ry * 0.55, 0, 0, PI * 2); ctx.stroke()
		else:
			# Mire: a dark pool with a slow shimmer so it reads as wet.
			ctx.fillStyle = withAlpha(def.color, 0.4)
			ctx.beginPath(); ctx.ellipse(cx, cy, rx, ry, 0, 0, PI * 2); ctx.fill()
			ctx.strokeStyle = withAlpha("#000", 0.35); ctx.lineWidth = 1
			ctx.beginPath(); ctx.ellipse(cx, cy, rx, ry, 0, 0, PI * 2); ctx.stroke()
			var shimmer := 0.4 + 0.25 * sin(time * 1.4 + f.col * 1.7 + f.row)
			ctx.strokeStyle = withAlpha("#cfe0c0", shimmer * 0.45); ctx.lineWidth = 1
			for k in [-0.3, 0.15]:
				ctx.beginPath()
				ctx.ellipse(cx, cy + ry * k, rx * 0.55, ry * 0.22, 0, 0, PI * 2)
				ctx.stroke()
		ctx.restore()


## A tiny weather mark for the condition chip: sun, rain, snow or cloud.
func weatherGlyph(ctx: Ctx, cx: float, cy: float, r: float, c: Dictionary, time: float) -> void:
	ctx.save()
	ctx.translate(cx, cy)
	ctx.fillStyle = c.color; ctx.strokeStyle = c.color; ctx.lineWidth = 1.2
	if c.weather == "clear":
		ctx.beginPath(); ctx.arc(0, 0, r * 0.5, 0, PI * 2); ctx.fill()
		for i in 8:
			var a := (i / 8.0) * PI * 2 + time * 0.25
			ctx.beginPath()
			ctx.moveTo(cos(a) * r * 0.7, sin(a) * r * 0.7)
			ctx.lineTo(cos(a) * r, sin(a) * r)
			ctx.stroke()
	else:
		# A cloud for everything else, with rain or snow beneath.
		ctx.beginPath()
		ctx.arc(-r * 0.35, -r * 0.1, r * 0.45, 0, PI * 2)
		ctx.arc(r * 0.2, -r * 0.2, r * 0.55, 0, PI * 2)
		ctx.arc(r * 0.55, r * 0.05, r * 0.35, 0, PI * 2)
		ctx.fill()
		if c.weather == "rain":
			for dx in [-0.4, 0.2, 0.7]:
				ctx.beginPath(); ctx.moveTo(dx * r, r * 0.5); ctx.lineTo(dx * r - r * 0.15, r * 1.05); ctx.stroke()
		elif c.weather == "snow":
			for dx in [-0.4, 0.25, 0.75]:
				ctx.beginPath(); ctx.arc(dx * r, r * 0.8, r * 0.16, 0, PI * 2); ctx.fill()
	ctx.restore()


## The battlefield tooltip: what the weather does, to whom, on both sides.
func drawCondTip(W: float, H: float) -> void:
	if condTip == null:
		return
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var c: Dictionary = condTip.c
	var traitName := func(id):
		for t in C.TRAITS:
			if t.id == id:
				return t.name
		return id
	var effects := Conditions.conditionLines(c, traitName)
	var desc := self.wrap(c.desc, 38)
	var pw := 264.0
	var ph := 44 + desc.size() * 15 + (12 + effects.size() * 16 if effects.size() > 0 else 0) + 20
	var px: float = condTip.x
	var py: float = condTip.y
	if px + pw > W - 4:
		px = maxf(4, W - pw - 4)
	py = maxf(4, minf(py, H - ph - 4))
	ctx.save()
	ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 14; ctx.shadowOffsetY = 4
	ctx.fillStyle = "rgba(18,14,9,0.98)"; roundRect(ctx, px, py, pw, ph, 9); ctx.fill()
	ctx.restore()
	ctx.strokeStyle = withAlpha(c.color, 0.95); ctx.lineWidth = 1.5
	roundRect(ctx, px + 0.75, py + 0.75, pw - 1.5, ph - 1.5, 9); ctx.stroke()
	weatherGlyph(ctx, px + 20, py + 22, 9, c, 0)
	ui.text(c.name, px + 38, py + 26, {"size": 14.5, "bold": true, "color": c.color, "font": "Georgia, serif"})
	var ly := py + 46
	for ln in desc:
		ui.text(ln, px + 14, ly, {"size": 11, "color": "#cabfa4"}); ly += 15
	if effects.size() > 0:
		ctx.strokeStyle = "rgba(255,255,255,0.09)"; ctx.lineWidth = 1
		ctx.beginPath(); ctx.moveTo(px + 12, ly); ctx.lineTo(px + pw - 12, ly); ctx.stroke()
		ly += 16
		for ln in effects:
			ui.text("◆ " + ln, px + 14, ly, {"size": 11.5, "bold": true, "color": "#e7ddc4"}); ly += 16
	ui.text("Applies to both warbands.", px + 14, ly + 6, {"size": 9.5, "color": "#8a8278"})


## Tooltip for the run's commander plate — who they are and what they change.
func drawCmdTip(W: float, H: float) -> void:
	if cmdTip == null:
		return
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var c: Dictionary = cmdTip.c
	var id = WarbandCommanders.commanderIdentity(c)
	var col: String = id.color if id != null else "#caa56a"
	var lines := self.wrap(c.perk, 34)
	var pw := 236.0
	var ph := 62 + lines.size() * 16 + 10
	var px: float = cmdTip.x
	var py: float = cmdTip.y
	if px + pw > W - 4:
		px = maxf(4, cmdTip.x - pw - 216)
	py = maxf(4, minf(py, H - ph - 4))
	ctx.save()
	ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 14; ctx.shadowOffsetY = 4
	ctx.fillStyle = "rgba(18,14,9,0.98)"; roundRect(ctx, px, py, pw, ph, 9); ctx.fill()
	ctx.restore()
	ctx.strokeStyle = withAlpha(col, 0.95); ctx.lineWidth = 1.5
	roundRect(ctx, px + 0.75, py + 0.75, pw - 1.5, ph - 1.5, 9); ctx.stroke()
	crest(ctx, px + 26, py + 30, 18, c, 0)
	ui.text(id.name if id != null else c.id, px + 52, py + 24, {"size": 15, "bold": true, "color": "#f2e8d0", "font": "Georgia, serif"})
	ui.text(id.title if id != null else "", px + 52, py + 40, {"size": 10.5, "color": col})
	ctx.strokeStyle = "rgba(255,255,255,0.09)"; ctx.lineWidth = 1
	ctx.beginPath(); ctx.moveTo(px + 12, py + 52); ctx.lineTo(px + pw - 12, py + 52); ctx.stroke()
	var ly := py + 70
	for ln in lines:
		ui.text(ln, px + 14, ly, {"size": 11.5, "color": "#d8cdb4"}); ly += 16


## Tooltip for an owned augment in the sidebar.
func drawAugTip(W: float, H: float) -> void:
	if augTip == null:
		return
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var a: Dictionary = augTip.a
	var col := AUG_COLOR(a.tier)
	var lines := self.wrap(a.desc, 32)
	var pw := 220.0
	var ph := 46 + lines.size() * 16 + 10
	var px: float = augTip.x
	var py: float = augTip.y
	if px + pw > W - 4:
		px = maxf(4, augTip.x - pw - 220)
	py = maxf(4, minf(py, H - ph - 4))
	ctx.save()
	ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 14; ctx.shadowOffsetY = 4
	ctx.fillStyle = "rgba(18,14,9,0.98)"; roundRect(ctx, px, py, pw, ph, 9); ctx.fill()
	ctx.restore()
	ctx.strokeStyle = withAlpha(col, 0.95); ctx.lineWidth = 1.5
	roundRect(ctx, px + 0.75, py + 0.75, pw - 1.5, ph - 1.5, 9); ctx.stroke()
	augSigil(ctx, px + 22, py + 24, 12, a, 0)
	ui.text(a.name, px + 44, py + 22, {"size": 14, "bold": true, "color": col, "font": "Georgia, serif"})
	ui.text("%s augment" % a.tier, px + 44, py + 36, {"size": 9.5, "color": "#8a8278"})
	var ly := py + 58
	for ln in lines:
		ui.text(ln, px + 14, ly, {"size": 11.5, "color": "#d8cdb4"}); ly += 16


## The scout panel: what a rival would field right now, their level and their
## synergies — so you can read the lobby and adapt before spending gold.
func drawScoutPanel(W: float, H: float, run: Warband.WarbandRun, time: float) -> void:
	var s = run.scout(scoutId)
	if s == null:
		scoutId = -1
		return
	var ui: UI = UI.ui
	var ctx := ui.ctx
	# Group their board by type+star so it reads as a comp, not a pile.
	var groups := {}
	for u in s.board:
		var st := int(u.get("star", 1))
		var k := "%s:%d" % [u.type, st]
		if groups.has(k):
			groups[k].n += 1
		else:
			groups[k] = {"type": u.type, "star": st, "n": 1}
	var rows: Array = groups.values()
	JS.sort_by_cmp(rows, func(a, b): return b.star - a.star if b.star != a.star else tierOfOr0(b.type) - tierOfOr0(a.type))
	var pw := 336.0
	var CELL_W := (pw - 40) / 5
	var CELL_H := 21.0
	var gridH := CELL_H * GRID_ROWS
	var traitH := 30.0 if s.traits.size() > 0 else 0.0
	var ph := minf(H - 120, 104 + gridH + traitH + maxi(1, rows.size()) * 22 + 18)
	var px := RAIL_W - 8
	var py := HEADER_H + 26
	scoutRect = {"x": px, "y": py, "w": pw, "h": ph}

	ctx.save()
	ctx.shadowColor = "rgba(0,0,0,0.75)"; ctx.shadowBlur = 28; ctx.shadowOffsetY = 10
	var g := ctx.createLinearGradient(0, py, 0, py + ph)
	g.addColorStop(0, "rgba(28,24,16,0.99)"); g.addColorStop(1, "rgba(11,8,5,0.99)")
	ctx.fillStyle = g; roundRect(ctx, px, py, pw, ph, 11); ctx.fill()
	ctx.restore()
	ctx.strokeStyle = withAlpha("#7fb0e8", 0.85); ctx.lineWidth = 1.8
	roundRect(ctx, px + 1, py + 1, pw - 2, ph - 2, 10); ctx.stroke()
	# A tinted header band, so the panel has a masthead rather than floating text.
	ctx.save()
	ctx.beginPath(); roundRect(ctx, px + 1, py + 1, pw - 2, 58, 10); ctx.clip()
	var hb := ctx.createLinearGradient(px, py, px + pw, py + 58)
	hb.addColorStop(0, "rgba(60,86,124,0.55)"); hb.addColorStop(1, "rgba(24,30,44,0.35)")
	ctx.fillStyle = hb; ctx.fillRect(px, py, pw, 58)
	ctx.restore()

	ui.text("SCOUTING", px + 16, py + 18, {"size": 9, "bold": true, "color": "#8fa8c8"})
	ui.text(s.name, px + 16, py + 38, {"size": 18, "bold": true, "color": "#e8f0ff", "font": "Georgia, serif"})
	ui.text("LV %d" % s.level if s.alive else "eliminated", px + pw - 16, py + 20, {
		"align": "right", "size": 12, "bold": true, "color": "#e7ddc4" if s.alive else "#6f6a5c",
	})
	ui.text("%d fielded" % s.board.size(), px + pw - 16, py + 38, {"align": "right", "size": 10.5, "color": "#9aa8bc"})
	# Life bar.
	ctx.fillStyle = "rgba(0,0,0,0.6)"; roundRect(ctx, px + 16, py + 48, pw - 32, 6, 3); ctx.fill()
	ctx.fillStyle = "#7df2a9" if s.alive else "#5a554d"
	roundRect(ctx, px + 16, py + 48, (pw - 32) * maxf(0, minf(1, s.life / 100.0)), 6, 3); ctx.fill()

	# ---- their formation ----
	# The single most useful thing to know about a rival is where their line
	# stands. A list of names can't tell you they've stacked the back rank.
	var y := py + 74
	ui.text("FORMATION", px + 16, y, {"size": 9, "bold": true, "color": "#8f8770"})
	ui.text("their front rank is nearest you", px + pw - 16, y, {"align": "right", "size": 8.5, "color": "#6f6a5c"})
	y += 8
	var gx := px + 20
	var occupied := {}
	for u in s.board:
		if u.get("col") != null and u.get("row") != null:
			occupied["%d,%d" % [u.col, u.row]] = {"type": u.type, "star": int(u.get("star", 1))}
	for c in range(5, 10):
		for r in GRID_ROWS:
			var cx2 := gx + (c - 5) * CELL_W
			var cy2 := y + r * CELL_H
			ctx.fillStyle = "rgba(224,120,106,0.10)" if c == 5 else "rgba(255,255,255,0.032)"
			roundRect(ctx, cx2 + 1, cy2 + 1, CELL_W - 2, CELL_H - 2, 3); ctx.fill()
			var occ = occupied.get("%d,%d" % [c, r])
			if occ == null:
				continue
			var tier := tierOf(occ.type)
			var tc: String = TIER_COLOR[tier]
			ctx.fillStyle = withAlpha(tc, 0.72)
			roundRect(ctx, cx2 + 1, cy2 + 1, CELL_W - 2, CELL_H - 2, 3); ctx.fill()
			ctx.strokeStyle = withAlpha(tc, 1); ctx.lineWidth = 1
			roundRect(ctx, cx2 + 1.5, cy2 + 1.5, CELL_W - 3, CELL_H - 3, 3); ctx.stroke()
			ui.text(clip(shortName(occ.type), 7), cx2 + CELL_W / 2, cy2 + CELL_H / 2 + 3.5, {
				"align": "center", "size": 8.5, "bold": true, "color": "#120e08",
			})
			if occ.star >= 2:
				ctx.fillStyle = "#ffd24a" if occ.star >= 3 else "#e8f0ff"
				for k in occ.star:
					ctx.beginPath(); ctx.arc(cx2 + CELL_W - 5 - k * 4, cy2 + 4.5, 1.5, 0, PI * 2); ctx.fill()
	y += gridH + 12

	# ---- their synergies, as the same hex badges the rail uses ----
	if s.traits.size() > 0:
		var tx := px + 16
		for at in s.traits:
			var label := "%s %s" % [at.trait.name, JS.num(at.count)]
			var cw3 := 14 + label.length() * 5.6
			if tx + cw3 > px + pw - 14:
				break
			ctx.fillStyle = withAlpha(at.trait.color, 0.22)
			roundRect(ctx, tx, y - 12, cw3, 18, 9); ctx.fill()
			ctx.strokeStyle = withAlpha(at.trait.color, 0.8); ctx.lineWidth = 1
			roundRect(ctx, tx + 0.5, y - 11.5, cw3 - 1, 17, 9); ctx.stroke()
			ui.text(label, tx + cw3 / 2, y + 1, {"align": "center", "size": 9.5, "bold": true, "color": at.trait.color})
			tx += cw3 + 6
		y += 24

	# ---- their roster ----
	ctx.strokeStyle = "rgba(255,255,255,0.08)"; ctx.lineWidth = 1
	ctx.beginPath(); ctx.moveTo(px + 14, y - 6); ctx.lineTo(px + pw - 14, y - 6); ctx.stroke()
	y += 10
	if rows.is_empty():
		ui.text("— no warband fielded yet —", px + 16, y, {"size": 11.5, "color": "#6f6a5c"})
	for r in rows:
		if y + 8 > py + ph - 6:
			break
		var tier := tierOf(r.type)
		ctx.fillStyle = withAlpha(TIER_COLOR[tier], 0.14)
		roundRect(ctx, px + 14, y - 11, pw - 28, 19, 4); ctx.fill()
		ctx.fillStyle = TIER_COLOR[tier]; roundRect(ctx, px + 15, y - 10, 2.5, 17, 1.2); ctx.fill()
		var def = C.UNITS.get(r.type)
		ui.text(def.name if def != null else r.type, px + 24, y + 3, {"size": 11.5, "bold": true, "color": "#e7ddc4"})
		ui.text(stars(r.star), px + pw - 52, y + 3, {"align": "right", "size": 10.5, "color": "#ffd24a" if r.star >= 3 else ("#cfe0ff" if r.star == 2 else "#9a917b")})
		if r.n > 1:
			ui.text("×%d" % r.n, px + pw - 24, y + 3, {"align": "right", "size": 10.5, "bold": true, "color": "#9a917b"})
		y += 22
	# Close.
	var cbx := px + pw - 30
	var cby := py + 8
	var chov := ui.mx >= cbx and ui.mx <= cbx + 22 and ui.my >= cby and ui.my <= cby + 22
	ui.text("✕", cbx + 11, cby + 16, {"align": "center", "size": 15, "bold": true, "color": "#ff9a8a" if chov else "#8a8278"})
	if ui.clicked and not ui.pointerConsumed:
		var inside := ui.mx >= px and ui.mx <= px + pw and ui.my >= py and ui.my <= py + ph
		if chov or not inside:
			ui.pointerConsumed = inside; scoutId = -1


## `UNIT_TIER[type] ?? 0`
static func tierOfOr0(type: String) -> int:
	return UnitTiers.tierOf(type, 0)


## The clash: two plates slam in from opposite edges with a VS between them,
## holding the fight for a beat so a round starts as an event rather than
## units quietly beginning to walk.
func drawIntro(bx: float, by: float, bw: float, bh: float, run: Warband.WarbandRun) -> void:
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var t := 1 - introT / INTRO_LEN # 0 → 1 across the banner
	var slide := easeOut(minf(1, t * 2.2))
	var fade := 1 - (t - 0.82) / 0.18 if t > 0.82 else 1.0 # clear out at the end
	var cy := by + bh * 0.42
	var plateH := 56.0

	ctx.save()
	ctx.globalAlpha = fade
	ctx.beginPath(); ctx.rect(bx, by, bw, bh); ctx.clip()
	# A dark band behind the banner.
	ctx.fillStyle = "rgba(6,4,2,0.72)"
	ctx.fillRect(bx, cy - plateH / 2 - 10, bw, plateH + 20)

	_plate(ctx, ui, bx, bw, cy, plateH, slide, "YOUR WARBAND", "%d deployed · %d synergies" % [run.deployedCount(), run.activeTraits().size()], "#7fb0e8", true)
	_plate(ctx, ui, bx, bw, cy, plateH, slide, run.pendingFoeName(), "monster camp" if run.isCreepRound() else "rival warband", "#e0786a", false)

	# The VS mark, punching in on its own beat.
	var pop := 0.5 + easeOut(minf(1, maxf(0, t - 0.16) * 3.4)) * 0.5
	ctx.save()
	ctx.translate(bx + bw / 2, cy)
	ctx.scale(pop, pop)
	ctx.shadowColor = "#ffd24a"; ctx.shadowBlur = 24
	ui.text("VS", 0, 12, {"align": "center", "size": 40, "bold": true, "color": "#ffd24a", "font": "Georgia, serif"})
	ctx.restore()
	ctx.restore()


## One of the intro's sliding plates (the TS `drawPlate` closure).
func _plate(ctx: Ctx, ui: UI, bx: float, bw: float, cy: float, plateH: float, slide: float, label: String, sub: String, colour: String, fromLeft: bool) -> void:
	var w := bw * 0.42
	var rest := (bx + bw * 0.5 - w - 26) if fromLeft else (bx + bw * 0.5 + 26)
	var off := (1 - slide) * (bw * 0.55) * (-1.0 if fromLeft else 1.0)
	var x := rest + off
	var g := ctx.createLinearGradient(x, 0, x + w, 0)
	if fromLeft:
		g.addColorStop(0, withAlpha(colour, 0.06)); g.addColorStop(1, withAlpha(colour, 0.42))
	else:
		g.addColorStop(0, withAlpha(colour, 0.42)); g.addColorStop(1, withAlpha(colour, 0.06))
	ctx.fillStyle = g
	ctx.fillRect(x, cy - plateH / 2, w, plateH)
	ctx.fillStyle = colour
	ctx.fillRect(x + w - 2.5 if fromLeft else x, cy - plateH / 2, 2.5, plateH)
	ui.text(label, x + w - 16 if fromLeft else x + 16, cy - 2, {
		"align": "right" if fromLeft else "left", "size": 22, "bold": true, "color": "#f7efdc", "font": "Georgia, serif",
	})
	ui.text(sub, x + w - 16 if fromLeft else x + 16, cy + 18, {
		"align": "right" if fromLeft else "left", "size": 11.5, "color": withAlpha(colour, 0.95),
	})


## The result flourish: rays and a scale-popped banner. Winning a round should
## feel like something, not read as a line of text in the shop bar.
func drawResultFlourish(bx: float, by: float, bw: float, bh: float, run: Warband.WarbandRun, time: float) -> void:
	var r = run.lastResult
	if r == null or resultT < 0:
		return
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var t := resultT
	var fade := maxf(0, 1 - (t - 1.5) / 0.6) if t > 1.5 else 1.0
	if fade <= 0:
		return
	var cx := bx + bw / 2
	var cy := by + bh * 0.36
	var col := "#7df2a9" if r.won else "#e0564a"
	var rise := (1 - easeOut(minf(1, t / 0.45))) * 26 # the banner drops in

	ctx.save()
	ctx.globalAlpha = fade
	# Everything is confined to the arena: this is the board's news, and rays
	# spilling over the standings and the shop just made the screen noisy.
	ctx.beginPath(); ctx.rect(bx, by, bw, bh); ctx.clip()
	ctx.fillStyle = "rgba(10,26,16,0.42)" if r.won else "rgba(30,8,6,0.46)"
	ctx.fillRect(bx, by, bw, bh)
	if r.won:
		ctx.save()
		ctx.translate(cx, cy); ctx.rotate(time * 0.18); ctx.globalAlpha = fade * 0.11
		for i in 16:
			ctx.rotate((PI * 2) / 16)
			ctx.fillStyle = "#ffe9b0"
			ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(maxf(bw, bh), -20); ctx.lineTo(maxf(bw, bh), 20); ctx.closePath(); ctx.fill()
		ctx.restore()
	var glow := ctx.createRadialGradient(cx, cy, 10, cx, cy, maxf(bw, bh) * 0.55)
	glow.addColorStop(0, withAlpha(col, 0.22)); glow.addColorStop(1, "rgba(0,0,0,0)")
	ctx.fillStyle = glow; ctx.fillRect(bx, by, bw, bh)

	# Scale-pop banner on a ribbon, so the word has something to sit on.
	var pop := 1 + 0.45 * exp(-t * 5) * cos(t * 13)
	ctx.save()
	ctx.translate(cx, cy - rise); ctx.scale(pop, pop)
	var rw := minf(bw * 0.86, 420)
	var rh := 96.0
	var rg := ctx.createLinearGradient(0, -rh / 2, 0, rh / 2)
	rg.addColorStop(0, "rgba(16,12,7,0.55)"); rg.addColorStop(0.5, "rgba(10,7,4,0.9)"); rg.addColorStop(1, "rgba(16,12,7,0.55)")
	ctx.fillStyle = rg
	ctx.beginPath()
	ctx.moveTo(-rw / 2, -rh / 2); ctx.lineTo(rw / 2, -rh / 2)
	ctx.lineTo(rw / 2 - 18, 0); ctx.lineTo(rw / 2, rh / 2)
	ctx.lineTo(-rw / 2, rh / 2); ctx.lineTo(-rw / 2 + 18, 0)
	ctx.closePath(); ctx.fill()
	ctx.strokeStyle = withAlpha(col, 0.65); ctx.lineWidth = 1.5; ctx.stroke()
	ctx.save()
	ctx.shadowColor = col; ctx.shadowBlur = 26
	ui.text("VICTORY" if r.won else "DEFEAT", 0, 4, {
		"align": "center", "size": 52, "bold": true, "color": "#f7efdc" if r.won else "#e8bfb6", "font": "Georgia, serif",
	})
	ctx.restore()
	var line: String
	if r.creep and r.won:
		line = "%s cleared — %d relic%s + %dg" % [r.foe, r.relics, "" if r.relics == 1 else "s", r.gold]
	elif r.won:
		line = "%s broken · %d still standing" % [r.foe, r.youLeft]
	else:
		line = "%s holds · −%d life" % [r.foe, r.dmg]
	ui.text(line, 0, 34, {"align": "center", "size": 15, "bold": true, "color": col, "font": "Georgia, serif"})
	ctx.restore()
	ctx.restore()


## A forge flourish when two components fuse into a full relic.
func drawFusion(bx: float, by: float, bw: float, bh: float) -> void:
	if fusion == null:
		return
	var it = Items.itemDef(fusion.item)
	if it == null:
		return
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var t: float = fusion.t / 1.7
	var a := maxf(0, 1 - pow(t, 2))
	var cx := bx + bw / 2
	var cy := by + bh * 0.62
	ctx.save()
	ctx.globalAlpha = a
	# Sparks flying outward from the forge point.
	for i in 14:
		var ang := (i / 14.0) * PI * 2 + t * 1.2
		var d := 18 + t * 96
		ctx.fillStyle = withAlpha(it.color, 0.85)
		ctx.beginPath(); ctx.arc(cx + cos(ang) * d, cy + sin(ang) * d * 0.55, maxf(0.6, 3 * (1 - t)), 0, PI * 2); ctx.fill()
	var pop := 1 + 0.35 * exp(-t * 6) * cos(t * 15)
	ctx.translate(cx, cy); ctx.scale(pop, pop); ctx.translate(-cx, -cy)
	ctx.save()
	ctx.shadowColor = it.color; ctx.shadowBlur = 20
	ctx.fillStyle = "rgba(10,7,4,0.85)"
	ctx.beginPath(); ctx.arc(cx, cy - 16, 24, 0, PI * 2); ctx.fill()
	ctx.strokeStyle = it.color; ctx.lineWidth = 2; ctx.stroke()
	itemIcon(ctx, cx, cy - 16, 14, it)
	ui.text("RELIC FORGED", cx, cy + 24, {"align": "center", "size": 13, "bold": true, "color": "#f7efdc", "font": "Georgia, serif"})
	ui.text(it.name, cx, cy + 42, {"align": "center", "size": 16, "bold": true, "color": it.color, "font": "Georgia, serif"})
	ctx.restore()
	ctx.restore()


## A star-up flourish over the board when three pieces merge.
func drawStarUp(bx: float, by: float, bw: float, bh: float) -> void:
	if starUp == null:
		return
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var t: float = starUp.t / 1.6
	var a := maxf(0, 1 - t)
	var cx := bx + bw / 2
	var cy := by + bh * 0.32
	var col := "#ffd24a" if starUp.star >= 3 else "#cfe0ff"
	ctx.save()
	ctx.globalAlpha = a
	# Expanding rings.
	for i in 3:
		var rt := maxf(0, t - i * 0.12)
		var r := 20 + rt * 190
		ctx.strokeStyle = withAlpha(col, 0.5 * (1 - rt))
		ctx.lineWidth = 3 * (1 - rt)
		ctx.beginPath(); ctx.arc(cx, cy, r, 0, PI * 2); ctx.stroke()
	# Scale-pop text.
	var pop := 1 + 0.4 * exp(-t * 6) * cos(t * 16)
	ctx.translate(cx, cy); ctx.scale(pop, pop); ctx.translate(-cx, -cy)
	ctx.save()
	ctx.shadowColor = col; ctx.shadowBlur = 22
	ui.text(stars(starUp.star), cx, cy - 8, {"align": "center", "size": 34, "bold": true, "color": col})
	ui.text("%s UPGRADED" % shortName(starUp.type).to_upper(), cx, cy + 22, {
		"align": "center", "size": 17, "bold": true, "color": "#f7efdc", "font": "Georgia, serif",
	})
	ctx.restore()
	ctx.restore()


## Draw a unit's actual animated sprite as a card portrait. This is the same
## `drawUnit` the arena uses, on a throwaway entity built from the unit
## definition — so a card shows the thing you're buying, idling and breathing,
## rather than a name and a number.
func portrait(ctx: Ctx, cx: float, cy: float, px: float, type: String, star: int, time: float) -> void:
	var def = C.UNITS.get(type)
	if def == null:
		return
	var e: Entity = portraits.get(type)
	if e == null:
		e = World.makeEntity()
		e.type = type
		e.team = T.Team.Player
		e.x = 0; e.y = 0; e.prevX = 0; e.prevY = 0
		portraits[type] = e
	# Refresh the fields the renderer reads (star tier changes the trim).
	e.radius = def.radius
	e.maxHp = def.hp; e.hp = def.hp
	e.attack = def.attack; e.range = def.range
	e.attackInterval = def.attackInterval
	e.armor = def.armor; e.armorClass = def.get("armorClass", T.AC_INFANTRY)
	e.speed = def.speed
	e.variantRarity = clampi(star - 1, 0, 2)
	e.animPhase = time * 0.6 + type.length() # a gentle idle sway, offset per type
	e.facing = -0.35 # three-quarter view, facing the reader
	e.hitFlash = 0
	var scale: float = px / C.TILE
	ctx.save()
	ctx.translate(cx, cy)
	ctx.scale(scale, scale)
	ctx.translate(-e.x, -e.y)
	Draw.drawUnit(ctx, e, time, 0)
	ctx.restore()


## A small stone plinth for a portrait to stand on.
func plinth(ctx: Ctx, cx: float, cy: float, rx: float, color: String) -> void:
	ctx.save()
	ctx.fillStyle = "rgba(0,0,0,0.4)"
	ctx.beginPath(); ctx.ellipse(cx, cy, rx, rx * 0.34, 0, 0, PI * 2); ctx.fill()
	ctx.strokeStyle = withAlpha(color, 0.55); ctx.lineWidth = 1.2
	ctx.beginPath(); ctx.ellipse(cx, cy, rx, rx * 0.34, 0, 0, PI * 2); ctx.stroke()
	ctx.restore()


func roundRect(ctx: Ctx, x: float, y: float, w: float, h: float, r: float) -> void:
	ctx.beginPath()
	ctx.moveTo(x + r, y)
	ctx.arcTo(x + w, y, x + w, y + h, r)
	ctx.arcTo(x + w, y + h, x, y + h, r)
	ctx.arcTo(x, y + h, x, y, r)
	ctx.arcTo(x, y, x + w, y, r)
	ctx.closePath()


## The post-run summary. A run is a story — the board you ended up with, the
## fights that went your way, the round the bleeding started — and none of
## that used to survive past a one-line "you placed #5". Returns true when
## the player asks to leave.
func drawRunSummary(W: float, H: float, run: Warband.WarbandRun, time: float) -> bool:
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var won: bool = run.outcome == "win"
	# Three endings, not two: you can outlast the lobby and still be put down
	# by the crown, and that is a different sentence from either of the others.
	var crowned: bool = won and run.bossCleared
	var uncrowned: bool = won and run.bossRound and not run.bossCleared
	var col := "#ffd24a" if crowned else ("#c8a86a" if won else "#c87a72")
	ctx.fillStyle = "rgba(5,4,2,0.9)"; ctx.fillRect(0, 0, W, H)
	var bloom := ctx.createRadialGradient(W / 2, H * 0.3, 30, W / 2, H * 0.3, maxf(W, H) * 0.6)
	bloom.addColorStop(0, withAlpha(col, 0.14)); bloom.addColorStop(1, "rgba(0,0,0,0)")
	ctx.fillStyle = bloom; ctx.fillRect(0, 0, W, H)
	if crowned:
		ctx.save()
		ctx.translate(W / 2, H * 0.3); ctx.rotate(time * 0.1); ctx.globalAlpha = 0.05
		for i in 18:
			ctx.rotate((PI * 2) / 18)
			ctx.fillStyle = "#ffe9b0"
			ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(maxf(W, H), -22); ctx.lineTo(maxf(W, H), 22); ctx.closePath(); ctx.fill()
		ctx.restore()

	# Height follows the content: a fixed 660 left a lake of empty card under
	# the columns on a board that ended small.
	var cmdH := (44 if run.commander != null else 26) + run.augments.size() * 26
	var chartH := 12 + 116 + 10 + 2 * 38 # chart + two rows of tally chips
	var cw := minf(1080, W - 80)
	var chh := minf(H - 70, 118 + 10 + 108 + 22 + maxi(cmdH, chartH) + 88)
	var cx0 := JS.round((W - cw) / 2)
	var cy0 := JS.round((H - chh) / 2)
	ctx.save()
	ctx.shadowColor = "rgba(0,0,0,0.8)"; ctx.shadowBlur = 34; ctx.shadowOffsetY = 12
	var g := ctx.createLinearGradient(0, cy0, 0, cy0 + chh)
	g.addColorStop(0, "rgba(30,24,15,0.99)"); g.addColorStop(1, "rgba(11,8,5,0.99)")
	ctx.fillStyle = g; roundRect(ctx, cx0, cy0, cw, chh, 14); ctx.fill()
	ctx.restore()
	ctx.strokeStyle = withAlpha(col, 0.75); ctx.lineWidth = 2
	roundRect(ctx, cx0 + 1, cy0 + 1, cw - 2, chh - 2, 13); ctx.stroke()

	# ---- masthead ----
	ctx.save()
	ctx.beginPath(); roundRect(ctx, cx0 + 1, cy0 + 1, cw - 2, 96, 13); ctx.clip()
	var mh := ctx.createLinearGradient(cx0, cy0, cx0 + cw, cy0 + 96)
	mh.addColorStop(0, withAlpha(col, 0.3)); mh.addColorStop(1, "rgba(20,15,9,0.2)")
	ctx.fillStyle = mh; ctx.fillRect(cx0, cy0, cw, 96)
	ctx.restore()
	ctx.save(); ctx.shadowColor = col; ctx.shadowBlur = 22
	var headline := "THE IRON CROWN IS YOURS" if crowned else ("UNCROWNED" if uncrowned else ("WARBAND TRIUMPHANT" if won else "WARBAND BROKEN"))
	ui.text(headline, cx0 + 32, cy0 + 50, {
		"size": 32 if crowned else 34, "bold": true,
		"color": "#ffe9b0" if crowned else ("#f0dfc0" if won else "#e0b0a6"), "font": "Georgia, serif",
	})
	ctx.restore()
	var sub: String
	if crowned:
		sub = "You outlasted the lobby and took the crown from its champions."
	elif uncrowned:
		sub = "You outlasted all seven — then the arena's champions put you down."
	elif won:
		sub = "Last warband standing — the arena is yours."
	else:
		sub = "Beaten in stage %s after %d fight%s." % [run.stageLabel(), run.history.size(), "" if run.history.size() == 1 else "s"]
	ui.text(sub, cx0 + 32, cy0 + 74, {"size": 13, "color": "#bcb299"})
	# Placement medal.
	var px3 := cx0 + cw - 74
	var py3 := cy0 + 48
	ctx.save()
	ctx.shadowColor = col; ctx.shadowBlur = 18
	var mg := ctx.createRadialGradient(px3 - 6, py3 - 8, 2, px3, py3, 34)
	mg.addColorStop(0, withAlpha(col, 0.85)); mg.addColorStop(1, "rgba(20,14,7,0.9)")
	ctx.fillStyle = mg; ctx.beginPath(); ctx.arc(px3, py3, 33, 0, PI * 2); ctx.fill()
	ctx.restore()
	ctx.strokeStyle = withAlpha(col, 0.95); ctx.lineWidth = 2
	ctx.beginPath(); ctx.arc(px3, py3, 33, 0, PI * 2); ctx.stroke()
	ui.text("#%d" % run.placement(), px3, py3 + 4, {"align": "center", "size": 24, "bold": true, "color": "#1a1207", "font": "Georgia, serif"})
	ui.text("of 8", px3, py3 + 20, {"align": "center", "size": 9, "bold": true, "color": "rgba(26,18,7,0.8)"})

	var y := cy0 + 118
	var pad := 32.0

	# ---- the board you died with ----
	ui.text("THE WARBAND YOU ENDED WITH", cx0 + pad, y, {"size": 10, "bold": true, "color": "#8f8770"})
	y += 10
	var deployed: Array = run.deployment().map(func(d): return run.pieces[d.index])
	var slot := minf(104, floorf((cw - pad * 2) / maxi(6, deployed.size())))
	var rowH := 108.0
	if deployed.is_empty():
		ui.text("— nothing was left standing —", cx0 + pad, y + 32, {"size": 12, "color": "#6f6a5c"})
	for i in deployed.size():
		var bx2 := cx0 + pad + i * slot
		if bx2 + slot - 6 > cx0 + cw - pad:
			continue
		pieceCard(bx2, y, slot - 6, rowH, deployed[i], true, time)
	y += rowH + 22

	# ---- what led it ----
	var colW := (cw - pad * 2 - 24) / 2
	var leftX := cx0 + pad
	var rightX := cx0 + pad + colW + 24
	var ly := y
	ui.text("COMMAND", leftX, ly, {"size": 10, "bold": true, "color": "#8f8770"})
	ly += 8
	if run.commander != null:
		var id = WarbandCommanders.commanderIdentity(run.commander)
		var c2: String = id.color if id != null else "#caa56a"
		ctx.fillStyle = withAlpha(c2, 0.16); roundRect(ctx, leftX, ly, colW, 38, 6); ctx.fill()
		ctx.strokeStyle = withAlpha(c2, 0.6); ctx.lineWidth = 1
		roundRect(ctx, leftX + 0.5, ly + 0.5, colW - 1, 37, 6); ctx.stroke()
		crest(ctx, leftX + 22, ly + 19, 13, run.commander, time)
		ui.text(id.name if id != null else run.commander.id, leftX + 44, ly + 17, {"size": 13, "bold": true, "color": "#f2e8d0", "font": "Georgia, serif"})
		ui.text(id.title if id != null else "", leftX + 44, ly + 31, {"size": 10, "color": c2})
		ly += 44
	else:
		ui.text("— led by no one —", leftX, ly + 16, {"size": 11, "color": "#6f6a5c"}); ly += 26
	for i in run.augments.size():
		var a: Dictionary = run.augments[i]
		var ay := ly + i * 26
		var ac := AUG_COLOR(a.tier)
		ctx.fillStyle = withAlpha(ac, 0.14); roundRect(ctx, leftX, ay, colW, 22, 5); ctx.fill()
		augSigil(ctx, leftX + 14, ay + 11, 7, a, time)
		ui.text(a.name, leftX + 28, ay + 15, {"size": 11, "bold": true, "color": ac})
		ui.text(a.tier, leftX + colW - 10, ay + 15, {"align": "right", "size": 9, "color": "#8a8278"})

	# ---- the run's life, round by round ----
	ui.text("HOW IT WENT", rightX, y, {"size": 10, "bold": true, "color": "#8f8770"})
	var chY := y + 12
	var chH := 116.0
	ctx.fillStyle = "rgba(0,0,0,0.35)"; roundRect(ctx, rightX, chY, colW, chH, 6); ctx.fill()
	var hist := run.history
	if hist.size() > 0:
		var step := colW / maxi(1, hist.size())
		var lifeY := func(life: float) -> float: return chY + chH - 10 - (chH - 20) * maxf(0, minf(1, life / 100.0))
		# Grid lines at 25 / 50 / 75 life, so the shape has a scale.
		ctx.strokeStyle = "rgba(255,255,255,0.06)"; ctx.lineWidth = 1
		for l in [25, 50, 75]:
			ctx.beginPath(); ctx.moveTo(rightX + 4, lifeY.call(l)); ctx.lineTo(rightX + colW - 4, lifeY.call(l)); ctx.stroke()
		# The life line, filled underneath.
		ctx.beginPath()
		ctx.moveTo(rightX + 4, lifeY.call(100))
		for i in hist.size():
			ctx.lineTo(rightX + 4 + (i + 1) * step - step / 2, lifeY.call(hist[i].life))
		ctx.lineTo(rightX + 4 + hist.size() * step - step / 2, chY + chH - 4)
		ctx.lineTo(rightX + 4, chY + chH - 4)
		ctx.closePath()
		var lg := ctx.createLinearGradient(0, chY, 0, chY + chH)
		lg.addColorStop(0, "rgba(125,242,169,0.28)"); lg.addColorStop(1, "rgba(125,242,169,0)")
		ctx.fillStyle = lg; ctx.fill()
		ctx.beginPath()
		ctx.moveTo(rightX + 4, lifeY.call(100))
		for i in hist.size():
			ctx.lineTo(rightX + 4 + (i + 1) * step - step / 2, lifeY.call(hist[i].life))
		ctx.strokeStyle = "#7df2a9"; ctx.lineWidth = 1.8; ctx.stroke()
		# A pip per round: green for a win, red for a loss, gold for a camp cleared.
		for i in hist.size():
			var h: Dictionary = hist[i]
			var hx := rightX + 4 + (i + 1) * step - step / 2
			if h.creep:
				ctx.fillStyle = "#ffd24a" if h.won else "#8a6a3a"
			else:
				ctx.fillStyle = "#7df2a9" if h.won else "#e0564a"
			ctx.beginPath(); ctx.arc(hx, lifeY.call(h.life), 2.8, 0, PI * 2); ctx.fill()
		ui.text("100", rightX + colW - 6, chY + 12, {"align": "right", "size": 8.5, "color": "#6f6a5c"})
		ui.text(hist[0].stage, rightX + 6, chY + chH - 4, {"size": 8.5, "color": "#6f6a5c"})
		ui.text(hist[hist.size() - 1].stage, rightX + colW - 6, chY + chH - 4, {"align": "right", "size": 8.5, "color": "#6f6a5c"})
	else:
		ui.text("— no fights were fought —", rightX + 12, chY + chH / 2, {"size": 11, "color": "#6f6a5c"})
	# The tally, in two rows of chips under the chart.
	var t2 := run.tally
	var chips := [
		["record", "%dW · %dL" % [t2.wins, t2.losses], "#7df2a9" if t2.wins >= t2.losses else "#e0786a"],
		["best streak", "+%d" % t2.bestStreak if t2.bestStreak > 0 else "—", "#ffd24a"],
		["camps", "%d cleared" % t2.campsCleared, "#c8a86a"],
		["relics", str(t2.relicsWon), "#9b5cf0"],
		["gold spent", str(t2.goldSpent), "#ffd24a"],
		["lobby", run.difficulty.name, run.difficulty.color],
	]
	var chipW := (colW - 12) / 3
	for i in chips.size():
		var bx2 := rightX + (i % 3) * (chipW + 6)
		var by2 := chY + chH + 10 + floori(i / 3.0) * 38
		ctx.fillStyle = "rgba(0,0,0,0.35)"; roundRect(ctx, bx2, by2, chipW, 34, 5); ctx.fill()
		ui.text(chips[i][0], bx2 + 8, by2 + 13, {"size": 8.5, "color": "#8a8278"})
		ui.text(chips[i][1], bx2 + 8, by2 + 28, {"size": 13, "bold": true, "color": chips[i][2]})

	var bw2 := 220.0
	var bh2 := 46.0
	if ui.button("Back to Menu", cx0 + cw / 2 - bw2 / 2, cy0 + chh - bh2 - 20, bw2, bh2, {"accent": true, "size": 17}):
		return true
	if ui.clicked:
		ui.pointerConsumed = true
	return false


## The header banner: dark leather, a gilded rule, and a slow sheen.
func drawHeaderPlate(W: float, time: float) -> void:
	var ctx := UI.ui.ctx
	var accent: String = C.PAL.uiAccent
	var hg := ctx.createLinearGradient(0, 0, 0, HEADER_H)
	hg.addColorStop(0, "rgba(38,28,15,0.97)"); hg.addColorStop(0.55, "rgba(20,15,8,0.96)"); hg.addColorStop(1, "rgba(8,6,3,0.96)")
	ctx.fillStyle = hg; ctx.fillRect(0, 0, W, HEADER_H)
	# A slow band of light crossing the banner, so the plate feels lacquered.
	var sheenX := fmod(time * 90, W + 520) - 260
	var sh := ctx.createLinearGradient(sheenX - 130, 0, sheenX + 130, HEADER_H)
	sh.addColorStop(0, "rgba(255,235,180,0)"); sh.addColorStop(0.5, "rgba(255,235,180,0.045)"); sh.addColorStop(1, "rgba(255,235,180,0)")
	ctx.fillStyle = sh; ctx.fillRect(0, 0, W, HEADER_H)
	# The rule under it: a bright hairline over a soft accent bloom.
	var bloom := ctx.createLinearGradient(0, HEADER_H - 10, 0, HEADER_H)
	bloom.addColorStop(0, withAlpha(accent, 0)); bloom.addColorStop(1, withAlpha(accent, 0.16))
	ctx.fillStyle = bloom; ctx.fillRect(0, HEADER_H - 10, W, 10)
	ctx.fillStyle = withAlpha(accent, 0.55); ctx.fillRect(0, HEADER_H - 2, W, 2)
	# Diamond studs along the rule.
	ctx.fillStyle = withAlpha(accent, 0.35)
	var sx := 14.0
	while sx < W:
		ctx.beginPath()
		ctx.moveTo(sx, HEADER_H - 6); ctx.lineTo(sx + 3, HEADER_H - 3); ctx.lineTo(sx, HEADER_H); ctx.lineTo(sx - 3, HEADER_H - 3)
		ctx.closePath(); ctx.fill()
		sx += 26


## The mode's mark: a shield with crossed blades, drawn not typed.
func bannerEmblem(ctx: Ctx, cx: float, cy: float, r: float, time: float) -> void:
	ctx.save()
	ctx.translate(cx, cy)
	ctx.lineJoin = "round"; ctx.lineCap = "round"
	# Shield.
	var g := ctx.createLinearGradient(0, -r, 0, r)
	g.addColorStop(0, "#3b2f1c"); g.addColorStop(1, "#170f07")
	ctx.beginPath()
	ctx.moveTo(-r * 0.82, -r * 0.86); ctx.lineTo(r * 0.82, -r * 0.86); ctx.lineTo(r * 0.82, r * 0.16)
	ctx.quadraticCurveTo(r * 0.82, r * 0.92, 0, r * 1.05)
	ctx.quadraticCurveTo(-r * 0.82, r * 0.92, -r * 0.82, r * 0.16)
	ctx.closePath()
	ctx.fillStyle = g; ctx.fill()
	ctx.strokeStyle = withAlpha(C.PAL.uiAccent, 0.85); ctx.lineWidth = 1.4; ctx.stroke()
	# Crossed blades, with a slow glint travelling one of them.
	ctx.strokeStyle = "#d9cdb0"; ctx.lineWidth = 1.8
	ctx.beginPath()
	ctx.moveTo(-r * 0.5, r * 0.5); ctx.lineTo(r * 0.5, -r * 0.55)
	ctx.moveTo(r * 0.5, r * 0.5); ctx.lineTo(-r * 0.5, -r * 0.55)
	ctx.stroke()
	ctx.strokeStyle = withAlpha("#5a4a2e", 0.95); ctx.lineWidth = 1.6
	ctx.beginPath(); ctx.moveTo(-r * 0.52, r * 0.24); ctx.lineTo(-r * 0.18, r * 0.6); ctx.stroke()
	ctx.beginPath(); ctx.moveTo(r * 0.52, r * 0.24); ctx.lineTo(r * 0.18, r * 0.6); ctx.stroke()
	var glint := fmod(time * 0.5, 3) / 3
	if glint < 0.34:
		ctx.save()
		ctx.globalAlpha = 1 - glint / 0.34
		ctx.fillStyle = "#fff6dc"
		var t2 := glint / 0.34
		ctx.beginPath(); ctx.arc(-r * 0.5 + t2 * r, r * 0.5 - t2 * r * 1.05, 1.6, 0, PI * 2); ctx.fill()
		ctx.restore()
	ctx.restore()


## What kind of round a number is, for the track and its glyphs: {key, color, label}.
func roundKind(round: int) -> Dictionary:
	if Creeps.isCreepRound(round):
		return {"key": "camp", "color": "#c8a86a", "label": "Camp"}
	if Augments.tierForRound(round) != null:
		return {"key": "augment", "color": "#9b5cf0", "label": "Augment"}
	if Carousel.isDraftRound(round):
		return {"key": "draft", "color": "#6ac8d8", "label": "Carousel"}
	return {"key": "pvp", "color": "#e0786a", "label": "Duel"}


## The stage track: the five rounds of the stage you're in, each marked with
## what it holds. Knowing a carousel or an augment is two rounds out is half
## of planning an economy, and it shouldn't take counting on your fingers.
func drawRoundTrack(x: float, y: float, w: float, h: float, run: Warband.WarbandRun) -> void:
	if w < 190:
		return
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var stage := floori((run.round - 1) / 5.0)
	var first := stage * 5 + 1
	var gap := 6.0
	var pw := minf(112, (w - gap * 4) / 5)
	ui.text("STAGE %d" % (stage + 1), x, y + 14, {"size": 9, "color": "#8f8770"})
	for i in 5:
		var rd := first + i
		var k := roundKind(rd)
		var px := x + i * (pw + gap)
		var py := y + 19
		var ph := h - 19
		var now := rd == run.round
		var past := rd < run.round
		ctx.globalAlpha = 0.4 if past else 1.0
		var g := ctx.createLinearGradient(0, py, 0, py + ph)
		g.addColorStop(0, withAlpha(k.color, 0.42 if now else 0.16)); g.addColorStop(1, "rgba(10,7,4,0.7)")
		ctx.fillStyle = g; roundRect(ctx, px, py, pw, ph, 5); ctx.fill()
		ctx.strokeStyle = withAlpha(k.color, 1 if now else 0.4); ctx.lineWidth = 1.8 if now else 1.0
		roundRect(ctx, px + 0.9, py + 0.9, pw - 1.8, ph - 1.8, 5); ctx.stroke()
		roundGlyph(ctx, px + 12, py + ph / 2, 6.5, k.key, k.color)
		if pw > 84:
			ui.text("%d-%d" % [stage + 1, i + 1], px + 24, py + ph / 2 - 1, {"size": 10.5, "bold": true, "color": "#f7efdc" if now else "#bcb299"})
			ui.text(k.label, px + 24, py + ph / 2 + 10, {"size": 8.5, "color": withAlpha(k.color, 1 if now else 0.75)})
		else:
			ui.text("%d-%d" % [stage + 1, i + 1], px + 24, py + ph / 2 + 4, {"size": 10.5, "bold": true, "color": "#f7efdc" if now else "#bcb299"})
		ctx.globalAlpha = 1


## A tiny mark per round kind: claw, sigil, ring or crossed blades.
func roundGlyph(ctx: Ctx, cx: float, cy: float, r: float, kind: String, color: String) -> void:
	ctx.save()
	ctx.translate(cx, cy)
	ctx.strokeStyle = color; ctx.fillStyle = color; ctx.lineWidth = 1.4; ctx.lineCap = "round"
	if kind == "camp":
		# Three claw marks.
		for dx in [-r * 0.55, 0.0, r * 0.55]:
			ctx.beginPath(); ctx.moveTo(dx - r * 0.15, -r * 0.8); ctx.quadraticCurveTo(dx + r * 0.2, 0, dx, r * 0.85); ctx.stroke()
	elif kind == "augment":
		# A hex sigil with a bright core.
		ctx.beginPath()
		for i in 6:
			var a := (i / 6.0) * PI * 2 - PI / 2
			var px := cos(a) * r
			var py := sin(a) * r
			if i:
				ctx.lineTo(px, py)
			else:
				ctx.moveTo(px, py)
		ctx.closePath(); ctx.stroke()
		ctx.beginPath(); ctx.arc(0, 0, r * 0.32, 0, PI * 2); ctx.fill()
	elif kind == "draft":
		# A ring of picks turning.
		ctx.beginPath(); ctx.arc(0, 0, r * 0.85, 0, PI * 2); ctx.stroke()
		for i in 4:
			var a := (i / 4.0) * PI * 2
			ctx.beginPath(); ctx.arc(cos(a) * r * 0.85, sin(a) * r * 0.85, r * 0.24, 0, PI * 2); ctx.fill()
	else:
		# Crossed blades.
		ctx.beginPath()
		ctx.moveTo(-r * 0.8, r * 0.8); ctx.lineTo(r * 0.8, -r * 0.8)
		ctx.moveTo(r * 0.8, r * 0.8); ctx.lineTo(-r * 0.8, -r * 0.8)
		ctx.stroke()
	ctx.restore()


func brazier(ctx: Ctx, cx: float, cy: float, time: float, scale: float = 1.0) -> void:
	var f := 0.72 + 0.28 * sin(time * 9 + cx * 0.3)
	var f2 := 0.72 + 0.28 * sin(time * 13 + cy * 0.3 + 1)
	ctx.save()
	ctx.translate(cx, cy); ctx.scale(scale, scale)
	# The bowl: a squat iron cup on a footed base, not a flat smudge.
	ctx.fillStyle = "rgba(0,0,0,0.45)"; ctx.beginPath(); ctx.ellipse(0, 7, 10, 3.6, 0, 0, PI * 2); ctx.fill()
	ctx.fillStyle = "#2a2118"; ctx.beginPath(); ctx.moveTo(-3, 6); ctx.lineTo(3, 6); ctx.lineTo(2, 1); ctx.lineTo(-2, 1); ctx.closePath(); ctx.fill()
	var bowl := ctx.createLinearGradient(0, -4, 0, 4)
	bowl.addColorStop(0, "#4a4136"); bowl.addColorStop(1, "#191410")
	ctx.fillStyle = bowl
	ctx.beginPath(); ctx.moveTo(-8, -3); ctx.lineTo(8, -3); ctx.lineTo(5, 3); ctx.lineTo(-5, 3); ctx.closePath(); ctx.fill()
	ctx.fillStyle = "#5a4f40"; ctx.beginPath(); ctx.ellipse(0, -3, 8, 2.4, 0, 0, PI * 2); ctx.fill()
	ctx.fillStyle = "#120d08"; ctx.beginPath(); ctx.ellipse(0, -3, 6, 1.7, 0, 0, PI * 2); ctx.fill()
	# Flame + the pool of light it throws on the stone.
	var pool := ctx.createRadialGradient(0, -2, 2, 0, -2, 54)
	pool.addColorStop(0, "rgba(255,150,44,0.30)"); pool.addColorStop(1, "rgba(255,150,44,0)")
	ctx.fillStyle = pool; ctx.beginPath(); ctx.arc(0, -2, 54, 0, PI * 2); ctx.fill()
	ctx.shadowColor = "#ff9128"; ctx.shadowBlur = 18 * f; ctx.globalAlpha = 0.94
	ctx.fillStyle = "#ff7a18"; ctx.beginPath(); ctx.ellipse(0, -11 * f, 4.6, 10 * f, 0, 0, PI * 2); ctx.fill()
	ctx.fillStyle = "#ffd862"; ctx.beginPath(); ctx.ellipse(0, -9 * f2, 2.4, 5.6 * f2, 0, 0, PI * 2); ctx.fill()
	ctx.fillStyle = "#fff3c8"; ctx.beginPath(); ctx.ellipse(0, -6 * f2, 1.2, 2.6 * f2, 0, 0, PI * 2); ctx.fill()
	ctx.restore()


## Cheap deterministic 0..1 hash, so stone grain is stable frame to frame.
static func hash(a: float, b: float) -> float:
	var n := JSMath.sin(a * 127.1 + b * 311.7) * 43758.5453
	return n - floorf(n)


## One inlaid stone plate. Each cell is a real object — bevelled, top-lit,
## with its own grain — rather than a line on a flat field. This is what
## makes the arena read as a place rather than a spreadsheet.
func tile(ctx: Ctx, tx: float, ty: float, tw: float, th: float, col: int, row: int, mine: bool, glow: float) -> void:
	var inset := maxf(1.5, minf(tw, th) * 0.05)
	var px := tx + inset
	var py := ty + inset
	var pw := tw - inset * 2
	var ph := th - inset * 2
	var rad := minf(pw, ph) * 0.16
	var grain := WarbandScreen.hash(col, row)
	# Base stone, lighter at the top edge (a single high sun).
	var base := ctx.createLinearGradient(0, py, 0, py + ph)
	var lift := 0.06 + grain * 0.07 + glow * 0.1
	base.addColorStop(0, "rgba(%d,%d,%d,0.30)" % [JS.round(96 + lift * 260), JS.round(104 + lift * 250), JS.round(88 + lift * 230)])
	base.addColorStop(1, "rgba(18,26,21,0.42)")
	ctx.fillStyle = base
	roundRect(ctx, px, py, pw, ph, rad); ctx.fill()
	# Side tint — cool for your ground, warm for theirs — strongest at the line.
	# Blue reads weaker than red at the same alpha, so it gets a little more.
	ctx.fillStyle = withAlpha("#5aa6f0", 0.08 + glow * 0.17) if mine else withAlpha("#e8604a", 0.05 + glow * 0.12)
	roundRect(ctx, px, py, pw, ph, rad); ctx.fill()
	# Bevel: bright top-left lip, dark bottom-right.
	ctx.lineWidth = 1
	ctx.strokeStyle = "rgba(255,246,224,0.09)"
	ctx.beginPath()
	ctx.moveTo(px + rad, py + 0.5); ctx.lineTo(px + pw - rad, py + 0.5)
	ctx.moveTo(px + 0.5, py + rad); ctx.lineTo(px + 0.5, py + ph - rad)
	ctx.stroke()
	ctx.strokeStyle = "rgba(0,0,0,0.40)"
	ctx.beginPath()
	ctx.moveTo(px + rad, py + ph - 0.5); ctx.lineTo(px + pw - rad, py + ph - 0.5)
	ctx.moveTo(px + pw - 0.5, py + rad); ctx.lineTo(px + pw - 0.5, py + ph - rad)
	ctx.stroke()
	# A chipped corner or a crack on some stones, so the field isn't uniform.
	if grain > 0.82:
		ctx.strokeStyle = "rgba(0,0,0,0.22)"; ctx.lineWidth = 1
		ctx.beginPath()
		ctx.moveTo(px + pw * 0.2, py + ph * (0.3 + grain * 0.2))
		ctx.lineTo(px + pw * 0.55, py + ph * 0.62)
		ctx.lineTo(px + pw * 0.78, py + ph * 0.44)
		ctx.stroke()


## The painterly stone arena: platform, inlaid tiles, carved centre channel,
## vignette, ornate frame and flickering corner braziers.
func drawArena(x: float, y: float, w: float, h: float, cellW: float, cellH: float, time: float, cond: Dictionary, setup: bool) -> void:
	var ctx := UI.ui.ctx
	var accent: String = C.PAL.uiAccent
	var RIM := 12.0 # the stone kerb the board is set into
	# Raised stone platform: a deep shadow, a dark kerb, then a lit rim.
	ctx.save()
	ctx.shadowColor = "rgba(0,0,0,0.72)"; ctx.shadowBlur = 28; ctx.shadowOffsetY = 10
	ctx.fillStyle = "#0a0805"; roundRect(ctx, x - RIM, y - RIM, w + RIM * 2, h + RIM * 2, 16); ctx.fill()
	ctx.restore()
	var kerb := ctx.createLinearGradient(0, y - RIM, 0, y + h + RIM)
	kerb.addColorStop(0, "#4d4536"); kerb.addColorStop(0.5, "#2e2a20"); kerb.addColorStop(1, "#16130d")
	ctx.fillStyle = kerb; roundRect(ctx, x - RIM, y - RIM, w + RIM * 2, h + RIM * 2, 16); ctx.fill()
	# Kerb blocks, so the rim reads as cut stone rather than a painted band.
	ctx.save()
	# (roundRect opens with beginPath, so this clips to the inner rect alone and
	# the block lines below — all of them out on the kerb — never show. The TS
	# does exactly this; the port keeps it.)
	ctx.beginPath(); roundRect(ctx, x - RIM, y - RIM, w + RIM * 2, h + RIM * 2, 16)
	roundRect(ctx, x, y, w, h, 4); ctx.clip("evenodd")
	ctx.strokeStyle = "rgba(0,0,0,0.42)"; ctx.lineWidth = 1
	ctx.beginPath()
	var i := 0
	while i * 44 < w + RIM * 2:
		var gx := x - RIM + i * 44
		ctx.moveTo(gx, y - RIM); ctx.lineTo(gx, y); ctx.moveTo(gx, y + h); ctx.lineTo(gx, y + h + RIM)
		i += 1
	i = 0
	while i * 44 < h + RIM * 2:
		var gy := y - RIM + i * 44
		ctx.moveTo(x - RIM, gy); ctx.lineTo(x, gy); ctx.moveTo(x + w, gy); ctx.lineTo(x + w + RIM, gy)
		i += 1
	ctx.stroke()
	ctx.restore()

	# The ground the tiles are bedded in.
	var g := ctx.createLinearGradient(0, y, 0, y + h)
	g.addColorStop(0, "#212d25"); g.addColorStop(0.55, "#161f1a"); g.addColorStop(1, "#0e1512")
	ctx.fillStyle = g; ctx.fillRect(x, y, w, h)

	# Inlaid stone plates, one per cell.
	for c in GRID_COLS:
		var mine := c < GRID_COLS / 2.0
		var glow := maxf(0, 1 - absf(c - (GRID_COLS / 2.0 - 0.5)) / 4.5) # hottest at the line
		for r in GRID_ROWS:
			tile(ctx, x + c * cellW, y + r * cellH, cellW, cellH, c, r, mine, glow)

	# The carved channel down the middle, with a slow glow crawling along it.
	var mid := x + w / 2
	var pulse := 0.5 + 0.5 * sin(time * 1.7)
	var chW := maxf(5, cellW * 0.1)
	var ch := ctx.createLinearGradient(mid - chW, 0, mid + chW, 0)
	ch.addColorStop(0, "rgba(0,0,0,0)"); ch.addColorStop(0.5, "rgba(0,0,0,0.62)"); ch.addColorStop(1, "rgba(0,0,0,0)")
	ctx.fillStyle = ch; ctx.fillRect(mid - chW, y, chW * 2, h)
	ctx.save()
	ctx.shadowColor = withAlpha(accent, 0.9); ctx.shadowBlur = 8 + pulse * 12
	ctx.strokeStyle = withAlpha(accent, 0.34 + pulse * 0.26); ctx.lineWidth = 2
	ctx.beginPath(); ctx.moveTo(mid, y + 6); ctx.lineTo(mid, y + h - 6); ctx.stroke()
	# Rune notches marching down the seam.
	ctx.lineWidth = 1.4
	for r in GRID_ROWS:
		var ry := y + (r + 0.5) * cellH
		var a := 0.2 + 0.5 * (0.5 + 0.5 * sin(time * 2.4 - r * 0.55))
		ctx.strokeStyle = withAlpha(accent, a)
		ctx.beginPath(); ctx.moveTo(mid - chW * 0.7, ry); ctx.lineTo(mid + chW * 0.7, ry); ctx.stroke()
	ctx.restore()

	# During setup, the ground you may actually stand on is marked out.
	if setup:
		var dz := mid - x
		ctx.save()
		var band := ctx.createLinearGradient(x, 0, mid, 0)
		band.addColorStop(0, withAlpha(accent, 0.10)); band.addColorStop(1, withAlpha(accent, 0.02))
		ctx.fillStyle = band; ctx.fillRect(x, y, dz, h)
		ctx.strokeStyle = withAlpha(accent, 0.30); ctx.lineWidth = 1.5
		ctx.setLineDash([7, 6]); ctx.lineDashOffset = -time * 14
		ctx.strokeRect(x + 3, y + 3, dz - 6, h - 6)
		ctx.restore()

	# Motes drifting in the brazier light — the arena should feel like air, not
	# a flat plate. Positions are a fixed cycle, so they never stutter.
	ctx.save()
	ctx.beginPath(); ctx.rect(x, y, w, h); ctx.clip()
	for k in 34:
		var mx2 := x + fmod(k * 173.7 + time * (9 + (k % 5) * 4), w + 40) - 20
		var my2 := y + fmod(k * 97.3 + sin(time * 0.35 + k) * 26 + h, h)
		var a := 0.05 + 0.09 * (0.5 + 0.5 * sin(time * 1.3 + k * 2.1))
		ctx.fillStyle = Color(255 / 255.0, 232 / 255.0, 186 / 255.0, a)
		ctx.beginPath(); ctx.arc(mx2, my2, 0.9 + (k % 3) * 0.6, 0, PI * 2); ctx.fill()
	ctx.restore()

	# Vignette.
	var vg := ctx.createRadialGradient(x + w / 2, y + h / 2, minf(w, h) * 0.22, x + w / 2, y + h / 2, maxf(w, h) * 0.62)
	vg.addColorStop(0, "rgba(0,0,0,0)"); vg.addColorStop(1, "rgba(0,0,0,0.46)")
	ctx.fillStyle = vg; ctx.fillRect(x, y, w, h)

	# ---- the round's weather, inside the arena ----
	if cond.weather != "clear":
		ctx.save()
		ctx.beginPath(); ctx.rect(x, y, w, h); ctx.clip()
		# A colour wash for the sky.
		var wash := "rgba(40,55,80,0.20)" if cond.weather == "rain" else ("rgba(200,215,235,0.13)" if cond.weather == "snow" else "rgba(48,46,52,0.34)")
		ctx.fillStyle = wash; ctx.fillRect(x, y, w, h)
		# A breath of the condition's own colour, so mud reads brown and frost blue.
		ctx.fillStyle = withAlpha(cond.color, 0.09); ctx.fillRect(x, y, w, h)
		if cond.weather == "rain":
			# Slanted streaks, marching on a fixed cycle so it stays smooth.
			ctx.strokeStyle = "rgba(180,205,235,0.34)"; ctx.lineWidth = 1
			ctx.beginPath()
			for k in 90:
				var sx0 := fmod(k * 137.5, w)
				var speed := 420 + (k % 5) * 90
				var sy0 := fmod(k * 61.7 + time * speed, h + 40) - 20
				var len := 12 + (k % 4) * 4
				ctx.moveTo(x + sx0, y + sy0)
				ctx.lineTo(x + sx0 - len * 0.32, y + sy0 + len)
			ctx.stroke()
		elif cond.weather == "snow":
			ctx.fillStyle = "rgba(235,244,255,0.62)"
			for k in 70:
				var drift := sin(time * 0.8 + k) * 14
				var sx0 := fmod(k * 149.3, w) + drift
				var sy0 := fmod(k * 83.1 + time * 52, h + 20) - 10
				var r := 1 + (k % 3) * 0.7
				ctx.beginPath(); ctx.arc(x + sx0, y + sy0, r, 0, PI * 2); ctx.fill()
		else: # overcast — heavy cloud shadows drifting across the field
			for k in 5:
				var cy2 := y + h * (0.12 + k * 0.19)
				var cx2 := x + fmod(k * 337 + time * 20, w + 420) - 210
				var g2 := ctx.createRadialGradient(cx2, cy2, 20, cx2, cy2, 210)
				g2.addColorStop(0, "rgba(0,0,0,0.30)"); g2.addColorStop(1, "rgba(0,0,0,0)")
				ctx.fillStyle = g2; ctx.fillRect(x, y, w, h)
		ctx.restore()

	# Ornate frame: a dark shadow line inside the kerb, then a gilded fillet.
	ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = 5; ctx.strokeRect(x + 2.5, y + 2.5, w - 5, h - 5)
	ctx.strokeStyle = withAlpha("#caa56a", 0.45); ctx.lineWidth = 1.5; ctx.strokeRect(x + 6.5, y + 6.5, w - 13, h - 13)
	# Corner bosses, sitting on the kerb — the braziers are mounted on them.
	var RIM2 := 12.0
	for o in [[x - 1, y - 1], [x + w + 1, y - 1], [x - 1, y + h + 1], [x + w + 1, y + h + 1]]:
		var ox: float = o[0]
		var oy: float = o[1]
		ctx.save()
		var bg := ctx.createRadialGradient(ox - 3, oy - 3, 1, ox, oy, RIM2 + 4)
		bg.addColorStop(0, "#6a5f4a"); bg.addColorStop(1, "#231e15")
		ctx.fillStyle = bg; ctx.beginPath(); ctx.arc(ox, oy, RIM2 + 2, 0, PI * 2); ctx.fill()
		ctx.strokeStyle = withAlpha("#caa56a", 0.55); ctx.lineWidth = 1.5
		ctx.beginPath(); ctx.arc(ox, oy, RIM2 + 2, 0, PI * 2); ctx.stroke()
		ctx.restore()
		brazier(ctx, ox, oy + 1, time, 0.95)


## The tiled arena. In setup it shows your placement (enemy hidden); once the
## fight starts it reveals and renders the live, animated battle.
func drawBoard(x: float, y: float, w: float, h: float, time: float, dt: float, run: Warband.WarbandRun) -> void:
	var ui: UI = UI.ui
	var ctx := ui.ctx
	# "Setup" is how the board *reads* (your side only, no health bars); it also
	# covers the augment pause so the board doesn't flash back to the last fight.
	# Placement itself stays shop-only.
	var setup := run.phase == "shop" or run.phase == "augment" or run.phase == "draft" or run.phase == "difficulty"
	var interactive := run.phase == "shop"
	# Monster camps are never hidden — you get to plan against what you can see.
	var reveal := run.isCreepRound()
	var cellW := w / GRID_COLS
	var cellH := h / GRID_ROWS

	drawArena(x, y, w, h, cellW, cellH, time, run.condition, setup)
	drawTerrain(x, y, cellW, cellH, run.terrain, time)

	if battle == null:
		return
	var b := battle
	# World ↔ screen mapping: the 10×10 world grid maps onto the board rect, so
	# a unit on cell (c,r) sits exactly in that on-screen cell.
	var worldLeft := b.cx - (GRID_COLS / 2.0) * GRID_CELL
	var worldTop := b.cy - (GRID_ROWS / 2.0) * GRID_CELL
	var sxk := w / (GRID_COLS * GRID_CELL)
	var syk := h / (GRID_ROWS * GRID_CELL)
	var mapX := func(wx: float) -> float: return x + (wx - worldLeft) * sxk
	var mapY := func(wy: float) -> float: return y + (wy - worldTop) * syk
	# Fill the cell. A figure at 62% of a tile reads as an ant on a wide board;
	# this fills the square while still leaving air between neighbours.
	var us: float = minf(cellH * 1.3, cellW * 1.24) / C.TILE

	# ---- enemy fog (setup) — camps stand in the open instead ----
	if setup and not reveal:
		ctx.fillStyle = "rgba(8,5,3,0.66)"
		ctx.fillRect(x + w / 2 + 1.5, y + 1.5, w / 2 - 3, h - 3)
		ui.text("Enemy warband hidden", x + w * 0.74, y + 30, {"align": "center", "size": 15, "bold": true, "color": "#caa"})
		ui.text("scout a rival on the left to see theirs", x + w * 0.74, y + 50, {"align": "center", "size": 12, "color": "#8a8278"})
	elif setup and reveal:
		var camp: Dictionary = run.pendingCamp
		ui.text(camp.name, x + w * 0.74, y + 26, {"align": "center", "size": 16, "bold": true, "color": "#c8a86a", "font": "Georgia, serif"})
		ui.text(camp.blurb, x + w * 0.74, y + 44, {"align": "center", "size": 11.5, "color": "#8a8278"})

	# ---- placement + sell interaction (setup only) ----
	var deployment: Array = run.deployment() if setup else []
	unitTip = null
	var heldEntityId := -1
	if interactive:
		var hovC := floori((ui.mx - x) / cellW)
		var hovR := floori((ui.my - y) / cellH)
		var inPlayer := ui.mx >= x and ui.mx < x + w and ui.my >= y and ui.my < y + h and hovC >= 0 and hovC <= 4 and hovR >= 0 and hovR <= 9
		var equipping := selectedItem >= 0 and selectedItem < run.itemStash.size()

		# Sell box, bottom-right corner of the board — a brazier pit you throw a
		# unit into. It only lights up when you're actually carrying something.
		var sbW := 156.0
		var sbH := 66.0
		sellBox = {"x": x + w - sbW - 14, "y": y + h - sbH - 14, "w": sbW, "h": sbH}
		var sb := sellBox
		var overSell: bool = ui.mx >= sb.x and ui.mx <= sb.x + sbW and ui.my >= sb.y and ui.my <= sb.y + sbH
		var heldObj = run.pieces[heldPiece] if heldPiece >= 0 and heldPiece < run.pieces.size() else null
		var hot := overSell and heldPiece >= 0
		var armed := heldPiece >= 0
		ctx.save()
		if hot:
			ctx.shadowColor = "#ff6a52"; ctx.shadowBlur = 18
		var sg2 := ctx.createLinearGradient(0, sb.y, 0, sb.y + sbH)
		sg2.addColorStop(0, "rgba(214,74,58,0.68)" if hot else ("rgba(96,32,24,0.62)" if armed else "rgba(48,18,14,0.42)"))
		sg2.addColorStop(1, "rgba(24,10,7,0.72)")
		ctx.fillStyle = sg2; roundRect(ctx, sb.x, sb.y, sbW, sbH, 8); ctx.fill()
		ctx.restore()
		ctx.strokeStyle = "#ff8a76" if hot else ("#c05a4c" if armed else "#7a3a32"); ctx.lineWidth = 2.4 if hot else 1.6
		ctx.setLineDash([7, 5]); ctx.lineDashOffset = -time * 24 if hot else 0.0
		roundRect(ctx, sb.x + 1.5, sb.y + 1.5, sbW - 3, sbH - 3, 8); ctx.stroke()
		ctx.setLineDash([]); ctx.lineDashOffset = 0
		# A little coin-and-arrow mark: the unit goes in, gold comes out.
		var gx: float = sb.x + 26
		var gy: float = sb.y + 26
		ctx.strokeStyle = "#ffd9cc" if hot else "#c8968c"; ctx.lineWidth = 1.6; ctx.lineCap = "round"
		ctx.beginPath(); ctx.moveTo(gx, gy - 9); ctx.lineTo(gx, gy + 5); ctx.moveTo(gx - 5, gy); ctx.lineTo(gx, gy + 6); ctx.lineTo(gx + 5, gy); ctx.stroke()
		coin(ctx, gx, gy + 15, 0, hot)
		ui.text("SELL", sb.x + sbW / 2 + 18, sb.y + 28, {"align": "center", "size": 16, "bold": true, "color": "#fff0ea" if hot else "#f0c0b6", "font": "Georgia, serif"})
		ui.text("drop for +%dg" % (run.cost(heldObj.type) * heldObj.star) if heldObj != null else "drag a unit here",
			sb.x + sbW / 2 + 18, sb.y + 48, {"align": "center", "size": 11, "bold": heldObj != null, "color": "#ffd24a" if heldObj != null else "#c09a92"})

		# Grab a board unit on press (when nothing held and not equipping a relic).
		if pressEdge and heldPiece < 0 and inPlayer and not equipping and not ui.pointerConsumed:
			var occ = _depAt(deployment, hovC, hovR)
			if occ != null:
				heldPiece = occ.index; heldFromBoard = true; grabbedThisPress = true; ui.pointerConsumed = true; audio.play("select")
		# Equip a selected relic by clicking a board unit.
		if ui.clicked and inPlayer and equipping and not ui.pointerConsumed:
			var occ = _depAt(deployment, hovC, hovR)
			if occ != null:
				ui.pointerConsumed = true
				if run.equipItem(selectedItem, occ.index):
					selectedItem = -1

		# Highlights: the held unit's cell + the hovered target cell.
		var heldDep = null
		var heldK := -1
		for k in deployment.size():
			if deployment[k].index == heldPiece:
				heldDep = deployment[k]
				heldK = k
				break
		if heldDep != null:
			ctx.strokeStyle = "#ffd24a"; ctx.lineWidth = 2.5
			ctx.strokeRect(x + heldDep.col * cellW + 2, y + heldDep.row * cellH + 2, cellW - 4, cellH - 4)
			# Dim the lifted unit on the board (it rides the cursor instead).
			var players := _teamUnits(b.world, 0)
			if heldK >= 0 and heldK < players.size():
				heldEntityId = players[heldK].id
		if inPlayer and heldPiece >= 0:
			ctx.fillStyle = withAlpha("#ffd24a", 0.18)
			ctx.fillRect(x + hovC * cellW + 1, y + hovR * cellH + 1, cellW - 2, cellH - 2)
		# Hovering a deployed unit (and not busy) → show its equipped relics.
		if inPlayer and heldPiece < 0 and not equipping:
			var occ = _depAt(deployment, hovC, hovR)
			if occ != null:
				ctx.strokeStyle = withAlpha("#cfe0ff", 0.6); ctx.lineWidth = 1.5
				ctx.strokeRect(x + hovC * cellW + 2, y + hovR * cellH + 2, cellW - 4, cellH - 4)
				unitTip = {"p": run.pieces[occ.index], "x": x + (hovC + 1) * cellW + 6, "y": y + hovR * cellH, "compare": null}

		# Resolve a release: sell / place / field / swap / keep-armed / cancel.
		if ui.clicked and heldPiece >= 0 and not ui.pointerConsumed:
			if overSell:
				if run.sell(heldPiece):
					audio.play("coin")
				heldPiece = -1; ui.pointerConsumed = true
			elif inPlayer and heldFromBoard:
				ui.pointerConsumed = true
				var sameCell: bool = heldDep != null and heldDep.col == hovC and heldDep.row == hovR
				if sameCell and not movedSincePress and grabbedThisPress:
					pass # just picked up → keep armed
				elif sameCell and not movedSincePress:
					heldPiece = -1 # clicked its own cell again → put down
				else:
					run.place(heldPiece, hovC, hovR); audio.play("command"); heldPiece = -1
			elif inPlayer:
				# A reserve (bench) unit dropped on the board → field it there.
				ui.pointerConsumed = true
				if run.place(heldPiece, hovC, hovR):
					audio.play("command")
				heldPiece = -1
			elif grabbedThisPress and not movedSincePress:
				pass # just clicked to pick it up off the bench → keep it armed for a click-to-place
			else:
				heldPiece = -1 # dropped on nothing → cancel

		# The instruction line sits on its own plate inside the player's half, so
		# it never fights the arena's kerb or the bench below it for space.
		var hint := "Drop on a cell to place · drop on a unit to swap · drop on SELL to sell" if heldPiece >= 0 else "Drag a reserve onto the board · click a unit then a cell to move"
		var hw := hint.length() * 6.1 + 24
		var hx := x + w * 0.25 - hw / 2
		var hy := y + h - 30
		ctx.fillStyle = "rgba(60,44,10,0.82)" if heldPiece >= 0 else "rgba(8,6,3,0.66)"
		roundRect(ctx, hx, hy, hw, 20, 10); ctx.fill()
		ctx.strokeStyle = withAlpha("#ffd24a", 0.6) if heldPiece >= 0 else "rgba(255,255,255,0.07)"
		ctx.lineWidth = 1; roundRect(ctx, hx + 0.5, hy + 0.5, hw - 1, 19, 10); ctx.stroke()
		ui.text(hint, x + w * 0.25, hy + 14, {"align": "center", "size": 11, "color": "#ffd24a" if heldPiece >= 0 else "#9a917b"})

	# ---- units + combat FX ----
	var ents: Array = b.world.entities.filter(func(e): return e.alive and e.kind == T.Kind.Unit and e.type != "villager" and (not setup or reveal or e.team == 0))
	JS.sort_by_cmp(ents, func(p, q): return p.y - q.y)
	# In setup, draw each unit at the EXACT centre of its assigned arena cell.
	# (The sim snaps spawn positions to its 32-unit nav grid, which doesn't line
	#  up with the 40-unit board cells — so we use the cell, not the entity pos.)
	var cellByEnt := {}
	var itemsByEnt := {}
	if setup:
		var players := _teamUnits(b.world, 0)
		for k in players.size():
			if k < deployment.size():
				var d: Dictionary = deployment[k]
				cellByEnt[players[k].id] = Vector2i(d.col, d.row)
				itemsByEnt[players[k].id] = run.pieces[d.index].items
		# A revealed camp gets the same exact-cell treatment on the enemy half.
		if reveal:
			var foes: Array = b.world.entities.filter(func(e): return e.alive and e.kind == T.Kind.Unit and e.type != "villager" and e.team != 0)
			JS.sort_by_cmp(foes, func(p, q): return p.id - q.id)
			for k in foes.size():
				var u = run.pendingOpp[k] if k < run.pendingOpp.size() else null
				if u != null and u.get("col") != null and u.get("row") != null:
					cellByEnt[foes[k].id] = Vector2i(u.col, u.row)
	Draw.setTeamColorResolver(null)
	ctx.save()
	ctx.beginPath(); ctx.rect(x, y, w, h); ctx.clip()
	# Screen-shake the contents (not the frame) during heavy combat.
	var sh := fx.shake
	if sh > 0.1:
		ctx.translate((randf() * 2 - 1) * sh, (randf() * 2 - 1) * sh)
	var starGlow := ["#9aa8b4", "#cfe0ff", "#ffd24a"] # 1★ / 2★ / 3★
	if not setup:
		liveTip = null # recomputed each frame from the hovered unit
	var hoverDist := INF
	for e: Entity in ents:
		var cell = cellByEnt.get(e.id)
		var sx: float = (x + (cell.x + 0.5) * cellW) if cell != null else mapX.call(e.x)
		var sy: float = (y + (cell.y + 0.5) * cellH) if cell != null else mapY.call(e.y)
		var lifted := e.id == heldEntityId
		# Anchor the feet below the cell centre so the unit's BODY sits in the
		# middle of the square (the sprite is feet-anchored and rises upward).
		var footY := sy + cellH * 0.24
		var star := e.variantRarity + 1
		# A team-coloured disc under the feet: contact shadow, then a soft pool of
		# side colour, so at a glance you can read whose half of the field a
		# brawl is happening on without picking out uniforms.
		var dr := cellW * 0.30
		var dry := cellH * 0.15
		ctx.save()
		ctx.globalAlpha = 0.2 if lifted else 1.0
		var pool := ctx.createRadialGradient(sx, footY, 1, sx, footY, dr)
		var side := "#4a93e8" if e.team == 0 else "#e8604a"
		pool.addColorStop(0, withAlpha(side, 0.34)); pool.addColorStop(1, withAlpha(side, 0))
		ctx.save(); ctx.translate(sx, footY); ctx.scale(1, dry / dr); ctx.translate(-sx, -footY)
		ctx.fillStyle = pool; ctx.beginPath(); ctx.arc(sx, footY, dr, 0, PI * 2); ctx.fill()
		ctx.restore()
		ctx.fillStyle = "rgba(0,0,0,0.42)"
		ctx.beginPath(); ctx.ellipse(sx, footY, cellW * 0.19, cellH * 0.085, 0, 0, PI * 2); ctx.fill()
		ctx.strokeStyle = withAlpha(side, 0.55); ctx.lineWidth = 1.2
		ctx.beginPath(); ctx.ellipse(sx, footY, dr * 0.78, dry * 0.78, 0, 0, PI * 2); ctx.stroke()
		ctx.restore()
		# Star-tier ring at the feet for 2★/3★.
		if star >= 2:
			ctx.save()
			ctx.strokeStyle = withAlpha(starGlow[mini(2, star - 1)], 0.9)
			ctx.shadowColor = starGlow[mini(2, star - 1)]; ctx.shadowBlur = 8; ctx.lineWidth = 2
			ctx.beginPath(); ctx.ellipse(sx, footY, cellW * 0.26, cellH * 0.13, 0, 0, PI * 2); ctx.stroke()
			ctx.restore()
		# A soft dark pool behind the figure. Without it the sprites sink into
		# the stone; a per-shape canvas shadow would look slightly better but
		# costs a blur pass for every one of a sprite's ~30 draw calls, and
		# there can be twenty sprites on screen at sixty frames a second.
		if not lifted:
			var back := ctx.createRadialGradient(sx, footY - cellH * 0.22, 2, sx, footY - cellH * 0.22, cellH * 0.42)
			back.addColorStop(0, "rgba(0,0,0,0.42)"); back.addColorStop(1, "rgba(0,0,0,0)")
			ctx.fillStyle = back
			ctx.beginPath(); ctx.arc(sx, footY - cellH * 0.22, cellH * 0.42, 0, PI * 2); ctx.fill()
		ctx.save()
		ctx.globalAlpha = 0.3 if lifted else 1.0 # lifted unit rides the cursor
		ctx.translate(sx, footY) # feet here → body centres on the square
		ctx.scale(us, us)
		ctx.translate(-e.x, -e.y)
		Draw.drawUnit(ctx, e, time, 0)
		ctx.restore()
		# Battle-style mark at the unit's shoulder in setup, so a comp's shape
		# (who holds, who sweeps, who jumps their line) reads at a glance.
		if setup and not lifted and e.team == 0:
			styleGlyph(ctx, sx + cellW * 0.28, sy - cellH * 0.12, minf(6.5, cellW * 0.1), BattleStyles.styleDef(e.type))
		# Star pips above the unit in setup so tiers read at a glance.
		if setup and star >= 2 and not lifted:
			for s in star:
				ctx.fillStyle = starGlow[mini(2, star - 1)]
				ctx.beginPath(); ctx.arc(sx - (star - 1) * 4 + s * 8, sy - cellH * 0.28, 2.4, 0, PI * 2); ctx.fill()
		# Equipped relic icons under the unit so you can see its gear at a glance.
		var eqItems = itemsByEnt.get(e.id) if setup else null
		if eqItems != null and eqItems.size() > 0 and not lifted:
			var r := minf(7, cellW * 0.1)
			var gap := r * 2.2
			var ix: float = sx - (eqItems.size() - 1) * gap / 2
			for id in eqItems:
				var it = Items.itemDef(id)
				if it == null:
					continue
				ctx.fillStyle = "rgba(8,6,3,0.92)"; ctx.beginPath(); ctx.arc(ix, footY + cellH * 0.16, r, 0, PI * 2); ctx.fill()
				ctx.strokeStyle = it.color; ctx.lineWidth = 1; ctx.stroke()
				itemIcon(ctx, ix, footY + cellH * 0.16, r * 0.72, it)
				ix += gap
		var frac := maxf(0, minf(1, e.hp / e.maxHp))
		if not setup:
			# Health bar with a trailing "ghost": the white tail shows the chunk
			# just taken and drains away, so a big hit is legible as a big hit
			# rather than a bar that silently jumps.
			var barW := cellW * 0.56
			var barH := 5.0
			var byy := sy - cellH * 0.3
			var ghostPrev = hpGhost.get(e.id)
			var ghost: float = frac if ghostPrev == null else (frac if frac > ghostPrev else maxf(frac, ghostPrev - dt * 0.55))
			hpGhost[e.id] = ghost
			ctx.fillStyle = "rgba(0,0,0,0.8)"
			roundRect(ctx, sx - barW / 2 - 1.2, byy - 1.2, barW + 2.4, barH + 2.4, 3); ctx.fill()
			ctx.save()
			roundRect(ctx, sx - barW / 2, byy, barW, barH, 2); ctx.clip()
			ctx.fillStyle = "rgba(0,0,0,0.55)"; ctx.fillRect(sx - barW / 2, byy, barW, barH)
			if ghost > frac:
				ctx.fillStyle = "rgba(255,236,210,0.75)"
				ctx.fillRect(sx - barW / 2 + barW * frac, byy, barW * (ghost - frac), barH)
			# Low health reads as danger regardless of side.
			var hpCol := "#ff7a52" if frac < 0.25 else ("#5ad06a" if e.team == 0 else "#e0564a")
			var hg2 := ctx.createLinearGradient(0, byy, 0, byy + barH)
			hg2.addColorStop(0, hpCol); hg2.addColorStop(1, withAlpha(hpCol, 0.55))
			ctx.fillStyle = hg2; ctx.fillRect(sx - barW / 2, byy, barW * frac, barH)
			ctx.fillStyle = "rgba(255,255,255,0.28)"; ctx.fillRect(sx - barW / 2, byy, barW * frac, 1.4)
			ctx.restore()
			# Track the unit nearest the cursor for a live hover readout.
			var d := JS.hypot(ui.mx - sx, ui.my - sy)
			if d < hoverDist and d < cellH * 0.8:
				hoverDist = d; liveTip = {"e": e, "x": sx + cellW * 0.4, "y": sy - cellH * 0.6}
		# Ability charge bar (battle only) for units with a signature ability.
		if not setup and C.ABILITIES.has(e.type) and e.type != "villager":
			var ab: Dictionary = C.ABILITIES[e.type]
			var charge := 1.0 if e.abilityActive > 0 else maxf(0, minf(1, b.chargeOf(e.id)))
			var barW := cellW * 0.5
			var barH := 2.5
			var byy := sy - cellH * 0.26 + 5
			ctx.fillStyle = "rgba(0,0,0,0.7)"; ctx.fillRect(sx - barW / 2 - 1, byy - 1, barW + 2, barH + 2)
			ctx.fillStyle = "#fff" if e.abilityActive > 0 else withAlpha(ab.color, 0.95)
			ctx.fillRect(sx - barW / 2, byy, barW * charge, barH)
	# Combat FX (sparks + floating damage) on top of the units.
	fx.draw(ctx, mapX, mapY, (sxk + syk) / 2)
	ctx.restore()


## The deployment entry standing on a cell, or null.
static func _depAt(deployment: Array, col: int, row: int):
	for d in deployment:
		if d.col == col and d.row == row:
			return d
	return null


## Living (non-villager) units of a team in a battle world, by id (= spawn order).
static func _teamUnits(world: World, team: int) -> Array:
	var out: Array = world.entities.filter(func(e): return e.alive and e.kind == T.Kind.Unit and e.type != "villager" and e.team == team)
	JS.sort_by_cmp(out, func(p, q): return p.id - q.id)
	return out


## A signature of the current matchup — rebuild the preview when it changes.
func sig(run: Warband.WarbandRun) -> String:
	var parts: Array = []
	for u in run.boardUnits():
		parts.append("%s%s@%s,%s:%s" % [u.type, JS.num(u.get("star", 1)), str(u.col), str(u.row), ",".join(u.get("items", []))])
	return "%d:%d:%s" % [run.round, run.pendingSeed, "|".join(parts)]


func pieceCard(x: float, y: float, w: float, h: float, p, deployed: bool, time: float, held: bool = false) -> void:
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var tier := tierOf(p.type)
	var hover := ui.mx >= x and ui.mx <= x + w and ui.my >= y and ui.my <= y + h
	ctx.globalAlpha = 0.4 if held else 1.0 # a lifted unit dims on its bench slot
	var g := ctx.createLinearGradient(0, y, 0, y + h)
	g.addColorStop(0, withAlpha(TIER_COLOR[tier], 0.26 if hover else 0.16))
	g.addColorStop(1, "rgba(14,10,6,0.95)")
	ctx.fillStyle = g; roundRect(ctx, x, y, w, h, 6); ctx.fill()
	ctx.strokeStyle = withAlpha(TIER_COLOR[tier], 0.95 if hover else 0.6)
	ctx.lineWidth = 1.5; roundRect(ctx, x + 0.75, y + 0.75, w - 1.5, h - 1.5, 6); ctx.stroke()
	# A tier stripe down the left edge, so a rack of cards reads as tiers even
	# before you look at any of the art.
	ctx.fillStyle = withAlpha(TIER_COLOR[tier], 1 if hover else 0.7)
	roundRect(ctx, x + 1.5, y + 4, 2.5, h - 8, 1.5); ctx.fill()
	# The unit stands on the card; its name sits on a plate along the bottom.
	var plate := 15.0
	var band := h - plate - 10
	var px2 := minf(w * 0.62, band * 1.2)
	var origin := y + 6 + band * 0.5
	ctx.save()
	ctx.beginPath(); ctx.rect(x + 2, y + 2, w - 4, h - plate - 3); ctx.clip()
	plinth(ctx, x + w / 2, origin + px2 * 0.26, minf(w * 0.24, 22), TIER_COLOR[tier])
	portrait(ctx, x + w / 2, origin, px2, p.type, p.star, time)
	ctx.restore()
	if p.star >= 2:
		ui.text(stars(p.star), x + w - 5, y + 13, {"align": "right", "size": 11, "color": "#ffd24a" if p.star >= 3 else "#cfe0ff"})
	ctx.fillStyle = "rgba(10,7,4,0.86)"
	roundRect(ctx, x + 2, y + h - plate - 2, w - 4, plate, 4); ctx.fill()
	ui.text(clip(shortName(p.type), maxi(5, floori((w - 10) / 6.1))), x + w / 2, y + h - 6, {
		"align": "center", "size": 10.5, "bold": true, "color": "#e7ddc4",
	})
	# Equipped relics as mini icons along the bottom.
	var ix := x + 11
	for id in p.items:
		var it = Items.itemDef(id)
		if it == null:
			continue
		ctx.fillStyle = "rgba(8,6,3,0.9)"; ctx.beginPath(); ctx.arc(ix, y + 13, 6, 0, PI * 2); ctx.fill()
		ctx.strokeStyle = it.color; ctx.lineWidth = 1; ctx.stroke()
		itemIcon(ctx, ix, y + 13, 4.3, it)
		ix += 14
	ctx.globalAlpha = 1
	if hover and heldPiece < 0:
		unitTip = {"p": p, "x": x + w + 6, "y": y - 70, "compare": null}


func shopCard(x: float, y: float, w: float, h: float, type: String, affordable: bool,
		poolLeft: int, blocked, time: float, compare, onClick: Callable) -> void:
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var tier := tierOf(type)
	var col: String = TIER_COLOR[tier]
	var hover := ui.mx >= x and ui.mx <= x + w and ui.my >= y and ui.my <= y + h
	var oy := -3.0 if hover and affordable else 0.0 # lift on hover
	ctx.save()
	ctx.shadowColor = "rgba(0,0,0,0.55)"; ctx.shadowBlur = 12 if hover and affordable else 5; ctx.shadowOffsetY = 4
	var g := ctx.createLinearGradient(0, y + oy, 0, y + oy + h)
	g.addColorStop(0, withAlpha(col, 0.3 if affordable else 0.1))
	g.addColorStop(0.45, "rgba(26,20,12,0.96)")
	g.addColorStop(1, "rgba(13,9,5,0.98)")
	ctx.fillStyle = g; roundRect(ctx, x, y + oy, w, h, 8); ctx.fill()
	ctx.restore()
	ctx.strokeStyle = withAlpha(col, 0.95 if affordable else 0.4); ctx.lineWidth = 2
	roundRect(ctx, x + 1, y + oy + 1, w - 2, h - 2, 7); ctx.stroke()
	# Tier accent bar along the top, glowing when the card is live and hovered.
	if hover and affordable:
		ctx.save(); ctx.shadowColor = col; ctx.shadowBlur = 10
	ctx.fillStyle = withAlpha(col, 1 if affordable else 0.5)
	roundRect(ctx, x + 6, y + oy + 4, w - 12, 3, 1.5); ctx.fill()
	if hover and affordable:
		ctx.restore()
	# Layout rows, derived from the card height so a taller counter gives the
	# figure more room rather than leaving a band of empty card under it.
	var top := y + oy + 11
	var nameY := y + oy + h - 34
	var traitY := y + oy + h - 21
	var footY := y + oy + h - 7
	# `portrait` centres the sprite on its origin, so the figure is placed by
	# its middle and the plinth is drawn where its feet actually land.
	var band := nameY - 8 - top
	var px2 := minf(w * 0.60, band * 1.16)
	var origin := top + band * 0.5
	var sole := origin + px2 * 0.26
	ctx.save()
	ctx.globalAlpha = 1.0 if affordable else 0.4
	# A soft pool of tier light behind the figure.
	var halo := ctx.createRadialGradient(x + w / 2, origin, 2, x + w / 2, origin, band * 0.85)
	halo.addColorStop(0, withAlpha(col, 0.34)); halo.addColorStop(1, "rgba(0,0,0,0)")
	ctx.fillStyle = halo; ctx.fillRect(x + 2, top - 4, w - 4, band + 12)
	plinth(ctx, x + w / 2, sole, minf(w * 0.24, 28), col)
	ctx.save()
	ctx.beginPath(); ctx.rect(x + 3, top - 3, w - 6, band + 10); ctx.clip()
	portrait(ctx, x + w / 2, origin, px2, type, 1, time)
	ctx.restore()
	ctx.globalAlpha = 1
	ctx.restore()
	var nm := clip(shortName(type), maxi(6, floori((w - 12) / 7.2)))
	ui.text(nm, x + w / 2, nameY, {
		"align": "center", "size": 11.5 if nm.length() > 10 else 14, "bold": true,
		"color": "#f2e8d0" if affordable else "#6f6a5c", "font": "Georgia, serif",
	})
	# Synergies, centred under the name.
	var tt := Traits.traitsOf(type).slice(0, 2)
	var label := clip(" · ".join(tt.map(func(t2): return t2.name)), maxi(6, floori((w - 8) / 5.3)))
	if label != "":
		ui.text(label, x + w / 2, traitY, {
			"align": "center", "size": 9, "color": withAlpha(tt[0].color, 0.95) if affordable else withAlpha(tt[0].color, 0.45),
		})
	ui.text("T%d" % tier, x + 9, footY, {"size": 9.5, "bold": true, "color": withAlpha(col, 1 if affordable else 0.5)})
	# How it opens a fight, as a corner mark — the name lives in the tooltip.
	var sd2 := BattleStyles.styleDef(type)
	ctx.globalAlpha = 1.0 if affordable else 0.45
	styleGlyph(ctx, x + w - 15, y + oy + 17, 6, sd2)
	ctx.globalAlpha = 1
	# Copies left in the shared lobby pool — or why this one can't be bought.
	if blocked == "bench":
		ui.text("bench full", x + w / 2, footY, {"align": "center", "size": 9.5, "bold": true, "color": "#e0786a"})
	else:
		ui.text("%d left" % poolLeft, x + w / 2, footY, {"align": "center", "size": 9.5, "color": "#e0786a" if poolLeft <= 3 else "#8a8278"})
	# Gold coin with the cost.
	coin(ctx, x + w - 16, y + oy + h - 12, tier, affordable)
	if hover:
		unitTip = {"p": Warband.Piece.new(type, 1, []), "x": x + w + 6, "y": y - 150, "compare": compare}
	if hover and affordable and ui.clicked and not ui.pointerConsumed:
		ui.pointerConsumed = true; onClick.call()


## A small gold coin glyph with a number on it.
func coin(ctx: Ctx, cx: float, cy: float, n: int, bright: bool = true) -> void:
	ctx.save()
	if bright:
		ctx.shadowColor = "#ffd24a"; ctx.shadowBlur = 6
	var g := ctx.createRadialGradient(cx - 2, cy - 2, 1, cx, cy, 8)
	g.addColorStop(0, "#ffe89a" if bright else "#9a8a55"); g.addColorStop(1, "#e0a52a" if bright else "#6a5a30")
	ctx.fillStyle = g; ctx.beginPath(); ctx.arc(cx, cy, 7.5, 0, PI * 2); ctx.fill()
	ctx.restore()
	ctx.strokeStyle = "#a8771e" if bright else "#544622"; ctx.lineWidth = 1; ctx.beginPath(); ctx.arc(cx, cy, 7.5, 0, PI * 2); ctx.stroke()
	# n = 0 means "a coin", not "zero gold" — used as an icon, so leave it blank.
	if n > 0:
		UI.ui.text(str(n), cx, cy + 4, {"align": "center", "size": 11, "bold": true, "color": "#5a3e08" if bright else "#3a3018"})


## A distinct procedural glyph per relic, drawn centred at (cx,cy), half-size s.
func itemIcon(ctx: Ctx, cx: float, cy: float, s: float, it: Dictionary) -> void:
	ctx.save()
	ctx.translate(cx, cy)
	ctx.lineJoin = "round"; ctx.lineCap = "round"
	ctx.fillStyle = it.color; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = maxf(1, s * 0.12)
	var h := s
	match it.id:
		"whetstone": # sword
			ctx.beginPath()
			ctx.moveTo(0, -h); ctx.lineTo(h * 0.22, -h * 0.2); ctx.lineTo(h * 0.22, h * 0.32)
			ctx.lineTo(-h * 0.22, h * 0.32); ctx.lineTo(-h * 0.22, -h * 0.2); ctx.closePath(); ctx.fill(); ctx.stroke()
			ctx.fillStyle = "#caa56a"; ctx.beginPath(); ctx.rect(-h * 0.55, h * 0.3, h * 1.1, h * 0.18); ctx.fill(); ctx.stroke()
			ctx.fillStyle = "#6a4a2a"; ctx.beginPath(); ctx.rect(-h * 0.12, h * 0.48, h * 0.24, h * 0.42); ctx.fill(); ctx.stroke()
		"greatmail": # shield
			ctx.beginPath()
			ctx.moveTo(0, -h); ctx.lineTo(h * 0.8, -h * 0.55); ctx.lineTo(h * 0.8, h * 0.2)
			ctx.quadraticCurveTo(h * 0.8, h * 0.92, 0, h)
			ctx.quadraticCurveTo(-h * 0.8, h * 0.92, -h * 0.8, h * 0.2)
			ctx.lineTo(-h * 0.8, -h * 0.55); ctx.closePath(); ctx.fill(); ctx.stroke()
			ctx.strokeStyle = "rgba(0,0,0,0.4)"; ctx.lineWidth = s * 0.16
			ctx.beginPath(); ctx.moveTo(0, -h * 0.5); ctx.lineTo(0, h * 0.6); ctx.moveTo(-h * 0.5, -h * 0.05); ctx.lineTo(h * 0.5, -h * 0.05); ctx.stroke()
		"warhorn": # drinking horn (crescent)
			ctx.beginPath()
			ctx.arc(h * 0.35, h * 0.35, h * 1.0, PI * 1.02, PI * 1.72, false)
			ctx.arc(h * 0.35, h * 0.35, h * 0.5, PI * 1.72, PI * 1.02, true)
			ctx.closePath(); ctx.fill(); ctx.stroke()
		"swiftboots": # lightning bolt
			ctx.beginPath()
			ctx.moveTo(h * 0.2, -h); ctx.lineTo(-h * 0.5, h * 0.12); ctx.lineTo(-h * 0.05, h * 0.12)
			ctx.lineTo(-h * 0.25, h); ctx.lineTo(h * 0.55, -h * 0.2); ctx.lineTo(h * 0.05, -h * 0.2)
			ctx.closePath(); ctx.fill(); ctx.stroke()
		"giantsbelt": # belt + buckle
			ctx.beginPath(); ctx.rect(-h, -h * 0.32, h * 2, h * 0.64); ctx.fill(); ctx.stroke()
			ctx.fillStyle = "#caa56a"; ctx.beginPath(); ctx.rect(-h * 0.32, -h * 0.46, h * 0.64, h * 0.92); ctx.fill(); ctx.stroke()
			ctx.strokeStyle = "rgba(0,0,0,0.5)"; ctx.lineWidth = s * 0.12; ctx.beginPath(); ctx.rect(-h * 0.15, -h * 0.24, h * 0.3, h * 0.48); ctx.stroke()
		"warbanner": # banner on a pole
			ctx.strokeStyle = "#6a4a2a"; ctx.lineWidth = s * 0.18
			ctx.beginPath(); ctx.moveTo(-h * 0.55, -h); ctx.lineTo(-h * 0.55, h); ctx.stroke()
			ctx.fillStyle = it.color; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = maxf(1, s * 0.1)
			ctx.beginPath()
			ctx.moveTo(-h * 0.55, -h * 0.9); ctx.lineTo(h * 0.78, -h * 0.9); ctx.lineTo(h * 0.46, -h * 0.3)
			ctx.lineTo(h * 0.78, h * 0.3); ctx.lineTo(-h * 0.55, h * 0.3); ctx.closePath(); ctx.fill(); ctx.stroke()
		"bloodaxe": # battle axe
			ctx.strokeStyle = "#6a4a2a"; ctx.lineWidth = s * 0.18
			ctx.beginPath(); ctx.moveTo(h * 0.1, h); ctx.lineTo(-h * 0.05, -h); ctx.stroke()
			ctx.fillStyle = it.color; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = maxf(1, s * 0.1)
			ctx.beginPath()
			ctx.moveTo(-h * 0.05, -h * 0.85); ctx.quadraticCurveTo(h * 0.95, -h * 0.95, h * 0.7, -h * 0.1)
			ctx.quadraticCurveTo(h * 0.5, -h * 0.4, -h * 0.05, -h * 0.35); ctx.closePath(); ctx.fill(); ctx.stroke()
		"ironhide": # great helm
			ctx.beginPath()
			ctx.moveTo(-h * 0.6, -h * 0.5); ctx.quadraticCurveTo(0, -h, h * 0.6, -h * 0.5)
			ctx.lineTo(h * 0.6, h * 0.65); ctx.quadraticCurveTo(0, h * 0.95, -h * 0.6, h * 0.65); ctx.closePath()
			ctx.fill(); ctx.stroke()
			ctx.strokeStyle = "rgba(0,0,0,0.45)"; ctx.lineWidth = s * 0.16
			ctx.beginPath(); ctx.moveTo(0, -h * 0.5); ctx.lineTo(0, h * 0.6); ctx.moveTo(-h * 0.55, 0); ctx.lineTo(h * 0.55, 0); ctx.stroke()
		"berserkbrew": # potion flask
			ctx.fillStyle = "#5a3a20"; ctx.beginPath(); ctx.rect(-h * 0.18, -h, h * 0.36, h * 0.45); ctx.fill(); ctx.stroke()
			ctx.fillStyle = it.color
			ctx.beginPath()
			ctx.moveTo(-h * 0.18, -h * 0.55); ctx.lineTo(h * 0.18, -h * 0.55)
			ctx.quadraticCurveTo(h * 0.85, h * 0.2, h * 0.45, h * 0.8)
			ctx.quadraticCurveTo(0, h * 1.05, -h * 0.45, h * 0.8)
			ctx.quadraticCurveTo(-h * 0.85, h * 0.2, -h * 0.18, -h * 0.55); ctx.closePath(); ctx.fill(); ctx.stroke()
			ctx.fillStyle = "rgba(255,255,255,0.35)"; ctx.beginPath(); ctx.ellipse(-h * 0.18, h * 0.35, h * 0.16, h * 0.28, -0.4, 0, PI * 2); ctx.fill()
		"towershield": # tall kite/tower shield
			ctx.beginPath()
			ctx.moveTo(-h * 0.6, -h * 0.85); ctx.lineTo(h * 0.6, -h * 0.85); ctx.lineTo(h * 0.6, h * 0.25)
			ctx.quadraticCurveTo(h * 0.6, h * 0.95, 0, h); ctx.quadraticCurveTo(-h * 0.6, h * 0.95, -h * 0.6, h * 0.25)
			ctx.closePath(); ctx.fill(); ctx.stroke()
			ctx.strokeStyle = "rgba(0,0,0,0.4)"; ctx.lineWidth = s * 0.14
			ctx.beginPath(); ctx.moveTo(-h * 0.5, -h * 0.45); ctx.lineTo(h * 0.5, -h * 0.45); ctx.moveTo(0, -h * 0.8); ctx.lineTo(0, h * 0.85); ctx.stroke()
		"warlordcrest": # crown
			ctx.beginPath()
			ctx.moveTo(-h * 0.8, h * 0.5); ctx.lineTo(-h * 0.8, -h * 0.4); ctx.lineTo(-h * 0.4, h * 0.05)
			ctx.lineTo(0, -h * 0.7); ctx.lineTo(h * 0.4, h * 0.05); ctx.lineTo(h * 0.8, -h * 0.4)
			ctx.lineTo(h * 0.8, h * 0.5); ctx.closePath(); ctx.fill(); ctx.stroke()
			ctx.fillStyle = "rgba(255,255,255,0.5)"
			for gx in [-0.8, 0.0, 0.8]:
				ctx.beginPath(); ctx.arc(gx * h, -0.45 * h, h * 0.13, 0, PI * 2); ctx.fill()
		"windcloak": # winged emblem
			ctx.beginPath(); ctx.arc(0, 0, h * 0.28, 0, PI * 2); ctx.fill(); ctx.stroke()
			for dir in [-1, 1]:
				ctx.beginPath()
				ctx.moveTo(dir * h * 0.25, -h * 0.1)
				ctx.quadraticCurveTo(dir * h * 1.0, -h * 0.6, dir * h * 0.95, -h * 0.05)
				ctx.quadraticCurveTo(dir * h * 0.9, h * 0.1, dir * h * 0.25, h * 0.2); ctx.closePath(); ctx.fill(); ctx.stroke()
		# ---- components: deliberately simpler, "raw part" shapes ----
		"blade": # a short blade
			ctx.beginPath()
			ctx.moveTo(0, -h); ctx.lineTo(h * 0.26, -h * 0.35); ctx.lineTo(h * 0.26, h * 0.3)
			ctx.lineTo(-h * 0.26, h * 0.3); ctx.lineTo(-h * 0.26, -h * 0.35); ctx.closePath(); ctx.fill(); ctx.stroke()
			ctx.fillStyle = "#6a4a2a"; ctx.beginPath(); ctx.rect(-h * 0.4, h * 0.28, h * 0.8, h * 0.22); ctx.fill(); ctx.stroke()
		"plate": # a riveted armour plate
			ctx.beginPath()
			ctx.moveTo(-h * 0.7, -h * 0.72); ctx.lineTo(h * 0.7, -h * 0.72)
			ctx.lineTo(h * 0.82, h * 0.5); ctx.quadraticCurveTo(0, h * 0.95, -h * 0.82, h * 0.5)
			ctx.closePath(); ctx.fill(); ctx.stroke()
			ctx.fillStyle = "rgba(0,0,0,0.4)"
			for rx in [-0.45, 0.45]:
				ctx.beginPath(); ctx.arc(rx * h, -h * 0.45, h * 0.12, 0, PI * 2); ctx.fill()
		"hide": # a stretched pelt
			ctx.beginPath()
			ctx.moveTo(0, -h)
			ctx.quadraticCurveTo(h * 0.95, -h * 0.6, h * 0.6, 0)
			ctx.quadraticCurveTo(h * 0.95, h * 0.7, 0, h)
			ctx.quadraticCurveTo(-h * 0.95, h * 0.7, -h * 0.6, 0)
			ctx.quadraticCurveTo(-h * 0.95, -h * 0.6, 0, -h)
			ctx.closePath(); ctx.fill(); ctx.stroke()
		"spur": # a rowel spur
			ctx.beginPath(); ctx.arc(0, 0, h * 0.42, 0, PI * 2); ctx.fill(); ctx.stroke()
			for i in 6:
				var a2 := (i / 6.0) * PI * 2
				ctx.beginPath()
				ctx.moveTo(cos(a2) * h * 0.42, sin(a2) * h * 0.42)
				ctx.lineTo(cos(a2) * h, sin(a2) * h)
				ctx.lineTo(cos(a2 + 0.4) * h * 0.42, sin(a2 + 0.4) * h * 0.42)
				ctx.closePath(); ctx.fill(); ctx.stroke()
		"sigil": # a rune diamond
			ctx.beginPath()
			ctx.moveTo(0, -h); ctx.lineTo(h * 0.72, 0); ctx.lineTo(0, h); ctx.lineTo(-h * 0.72, 0)
			ctx.closePath(); ctx.fill(); ctx.stroke()
			ctx.strokeStyle = "rgba(0,0,0,0.45)"; ctx.lineWidth = s * 0.14
			ctx.beginPath(); ctx.moveTo(0, -h * 0.5); ctx.lineTo(0, h * 0.5); ctx.moveTo(-h * 0.34, 0); ctx.lineTo(h * 0.34, 0); ctx.stroke()
		# ---- the three grid-completing relics ----
		"duelistedge": # slim rapier over a buckler
			ctx.beginPath(); ctx.arc(-h * 0.35, h * 0.3, h * 0.45, 0, PI * 2); ctx.fill(); ctx.stroke()
			ctx.fillStyle = "#e8e0cc"
			ctx.beginPath()
			ctx.moveTo(h * 0.15, -h); ctx.lineTo(h * 0.34, -h * 0.75); ctx.lineTo(h * 0.34, h * 0.55)
			ctx.lineTo(h * 0.1, h * 0.55); ctx.closePath(); ctx.fill(); ctx.stroke()
		"reaverscleaver": # heavy cleaver
			ctx.strokeStyle = "#6a4a2a"; ctx.lineWidth = s * 0.2
			ctx.beginPath(); ctx.moveTo(-h * 0.3, h); ctx.lineTo(h * 0.05, -h * 0.3); ctx.stroke()
			ctx.fillStyle = it.color; ctx.strokeStyle = "rgba(0,0,0,0.55)"; ctx.lineWidth = maxf(1, s * 0.1)
			ctx.beginPath()
			ctx.moveTo(-h * 0.15, -h * 0.25); ctx.lineTo(h * 0.35, -h)
			ctx.quadraticCurveTo(h * 1.0, -h * 0.3, h * 0.5, h * 0.15)
			ctx.closePath(); ctx.fill(); ctx.stroke()
		"dancersmail": # winged mail
			ctx.beginPath()
			ctx.moveTo(-h * 0.45, -h * 0.6); ctx.lineTo(h * 0.45, -h * 0.6); ctx.lineTo(h * 0.45, h * 0.35)
			ctx.quadraticCurveTo(0, h * 0.9, -h * 0.45, h * 0.35); ctx.closePath(); ctx.fill(); ctx.stroke()
			for dir in [-1, 1]:
				ctx.beginPath()
				ctx.moveTo(dir * h * 0.45, -h * 0.45)
				ctx.quadraticCurveTo(dir * h * 1.05, -h * 0.55, dir * h * 0.95, h * 0.05)
				ctx.quadraticCurveTo(dir * h * 0.75, -h * 0.1, dir * h * 0.45, h * 0.05)
				ctx.closePath(); ctx.fill(); ctx.stroke()
		_:
			ctx.beginPath(); ctx.arc(0, 0, h * 0.7, 0, PI * 2); ctx.fill(); ctx.stroke()
	ctx.restore()


## Human-readable benefit lines for a relic's buff.
func buffLines(b: Dictionary) -> Array:
	var out: Array = []
	if b.get("atk"):
		out.append("+%s Attack" % JS.num(b.atk))
	if b.get("atkPct"):
		out.append("+%s%% Attack" % JS.num(b.atkPct))
	if b.get("armor"):
		out.append("+%s Armour" % JS.num(b.armor))
	if b.get("hp"):
		out.append("+%s Max HP" % JS.num(b.hp))
	if b.get("hpPct"):
		out.append("+%s%% Max HP" % JS.num(b.hpPct))
	if b.get("speedPct"):
		out.append("+%s%% Move Speed" % JS.num(b.speedPct))
	return out


## The relic hover tooltip — name, icon and exact benefits. Drawn last, on top.
func drawItemTip(W: float, H: float) -> void:
	if itemTip == null:
		return
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var it = Items.itemDef(itemTip.id)
	if it == null:
		return
	var lines := buffLines(it.buff)
	# A component also lists every relic it can still become.
	var isComp: bool = it.get("component", false)
	var recipes: Array = []
	if isComp:
		for r in Items.buildsInto(it.id):
			var d = Items.itemDef(r)
			if d != null:
				recipes.append(d)
	var pw := 250.0 if isComp else 196.0
	var ph := 58 + lines.size() * 17 + (26 + ceili(recipes.size() / 2.0) * 15 + 14 if recipes.size() > 0 else 20)
	var px: float = itemTip.x
	var py: float = itemTip.y
	if px + pw > W - 4:
		px = maxf(4, itemTip.x - pw - 56)
	if py + ph > H - 4:
		py = H - ph - 4
	ctx.save()
	ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 16; ctx.shadowOffsetY = 4
	ctx.fillStyle = "rgba(18,14,9,0.98)"; roundRect(ctx, px, py, pw, ph, 9); ctx.fill()
	ctx.restore()
	ctx.strokeStyle = withAlpha(it.color, 0.95); ctx.lineWidth = 1.5; roundRect(ctx, px + 0.75, py + 0.75, pw - 1.5, ph - 1.5, 9); ctx.stroke()
	itemIcon(ctx, px + 20, py + 22, 13, it)
	ui.text(it.name, px + 40, py + 20, {"size": 15, "bold": true, "color": it.color, "font": "Georgia, serif"})
	ui.text("Component" if isComp else "Relic", px + 40, py + 34, {"size": 10, "color": "#9a917b"})
	var ly := py + 58
	for ln in lines:
		ui.text("◆ " + ln, px + 14, ly, {"size": 12.5, "bold": true, "color": "#e7ddc4"}); ly += 17
	if recipes.size() > 0:
		ctx.strokeStyle = "rgba(255,255,255,0.09)"; ctx.lineWidth = 1
		ctx.beginPath(); ctx.moveTo(px + 12, ly - 2); ctx.lineTo(px + pw - 12, ly - 2); ctx.stroke()
		ui.text("Builds into", px + 14, ly + 13, {"size": 10, "bold": true, "color": "#9a917b"})
		ly += 26
		for i in recipes.size():
			var r: Dictionary = recipes[i]
			var cx2 := px + 14 + (i % 2) * ((pw - 28) / 2)
			var cy2 := ly + floori(i / 2.0) * 15
			itemIcon(ctx, cx2 + 5, cy2 - 3, 5, r)
			ui.text(clip(r.name, 16), cx2 + 14, cy2, {"size": 9.5, "color": withAlpha(r.color, 0.95)})
		ly += ceili(recipes.size() / 2.0) * 15
		ui.text("Pair two components on one unit to forge it", px + 14, ly + 4, {"size": 9.5, "color": "#8a8278"})
	else:
		ui.text("Equip onto a unit · up to 3 each", px + 14, ly + 4, {"size": 10, "color": "#8a8278"})


## A piece's effective numbers: base scaled by star, then its relics. The same
## maths for the unit you're hovering and the one you'd be measuring it
## against, so a comparison is honest.
func unitStats(p) -> Dictionary:
	var def: Dictionary = C.UNITS[p.type]
	var sm: float = STAR_MULT[p.star] if p.star < STAR_MULT.size() else 1.0
	var st := {"maxHp": JS.round(def.hp * sm), "hp": 0.0, "attack": JS.round(def.attack * sm), "armor": def.armor, "speed": def.speed}
	st.hp = st.maxHp
	Items.applyItems(st, p.items)
	var rate: float = 1.0 / def.attackInterval if def.attackInterval > 0 else 0.0
	st["rate"] = rate
	st["dps"] = JS.round(st.attack * rate)
	st["range"] = def.range
	return st


## Hover inspector for a unit: its tier, star, live combat stats, counter
## bonuses, signature ability and equipped relics.
func drawUnitTip(W: float, H: float) -> void:
	if unitTip == null:
		return
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var p = unitTip.p
	var def = C.UNITS.get(p.type)
	if def == null:
		return
	var tier := tierOf(p.type)
	var relics: Array = []
	for id in p.items:
		var d = Items.itemDef(id)
		if d != null:
			relics.append(d)
	var ab = C.ABILITIES.get(p.type)
	var st := unitStats(p)
	# What this would be measured against — the unit it would push off a full
	# board. Never compare a piece to itself.
	var cmp = unitTip.get("compare")
	var other = cmp if cmp != null and cmp != p else null
	var ost = unitStats(other) if other != null else null
	var bonuses: Array = []
	for cls in def.bonus:
		bonuses.append("+%s vs %s" % [JS.num(def.bonus[cls]), cls])

	var pw := 296.0 if ost != null else 244.0 # deltas need room beside each value
	var ph := 50.0 + 56 # header + stat block (incl. RATE + DPS)
	if ost != null:
		ph += 18 # the "vs …" line
	ph += 52 # battle-style block
	if bonuses.size() > 0:
		ph += 18
	var abLines: Array = self.wrap(ab.desc, 56 if ost != null else 46).slice(0, 3) if ab != null else []
	if ab != null:
		ph += 20 + abLines.size() * 12
	ph += 8 + (relics.size() * 30 if relics.size() > 0 else 16)
	var px: float = unitTip.x
	var py: float = unitTip.y
	if px + pw > W - 4:
		px = maxf(4, px - pw - 90)
	py = maxf(4, minf(py, H - ph - 4))
	ctx.save()
	ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 16; ctx.shadowOffsetY = 4
	ctx.fillStyle = "rgba(18,14,9,0.98)"; roundRect(ctx, px, py, pw, ph, 9); ctx.fill()
	ctx.restore()
	ctx.strokeStyle = withAlpha(TIER_COLOR[tier], 0.95); ctx.lineWidth = 1.5; roundRect(ctx, px + 0.75, py + 0.75, pw - 1.5, ph - 1.5, 9); ctx.stroke()
	# Header.
	ui.text(def.name, px + 14, py + 21, {"size": 15, "bold": true, "color": "#f2e8d0", "font": "Georgia, serif"})
	if p.star >= 2:
		ui.text(stars(p.star), px + 14, py + 38, {"size": 13, "color": "#ffd24a" if p.star >= 3 else "#cfe0ff"})
	else:
		ui.text(" · ".join(Traits.traitsOf(p.type).map(func(t2): return t2.name)), px + 14, py + 38, {"size": 10, "color": "#8a8278"})
	ui.text("Tier %d" % tier, px + pw - 14, py + 21, {"align": "right", "size": 11, "color": TIER_COLOR[tier]})
	var sd := BattleStyles.styleDef(p.type)
	styleGlyph(ctx, px + pw - 24, py + 38, 6.5, sd)
	ui.text(sd.name, px + pw - 36, py + 41, {"align": "right", "size": 10.5, "bold": true, "color": sd.color})
	var y := py + 50
	_div(ctx, px, pw, y)
	# Stat block. RATE = attacks/sec; DPS ≈ attack × rate (before enemy armour).
	var rate: float = 1.0 / def.attackInterval if def.attackInterval > 0 else 0.0
	var dps := JS.round(st.attack * rate)
	var colX: Array = [px + 14, px + 108, px + 202] if ost != null else [px + 16, px + 92, px + 162]
	_statCell(ui, "HP", JS.num(st.maxHp), colX[0], y + 16, "#7df2a9", st.maxHp - ost.maxHp if ost != null else null)
	_statCell(ui, "ATK", JS.num(st.attack), colX[1], y + 16, "#ffce6a", st.attack - ost.attack if ost != null else null)
	_statCell(ui, "RATE", JS.to_fixed(rate, 2) if ost != null else JS.to_fixed(rate, 2) + "/s", colX[2], y + 16, "#e7ddc4",
		float(JS.to_fixed(st.rate - ost.rate, 2)) if ost != null else null)
	_statCell(ui, "ARM", JS.num(st.armor), colX[0], y + 33, "#cfe0ff", st.armor - ost.armor if ost != null else null)
	_statCell(ui, "RNG", JS.num(def.range) if def.range > 0 else "melee", colX[1], y + 33, "#e7ddc4", st.range - ost.range if ost != null else null)
	_statCell(ui, "SPD", JS.num(st.speed), colX[2], y + 33, "#e7ddc4", st.speed - ost.speed if ost != null else null)
	ui.text("DPS ≈ %s" % JS.num(dps), px + 16, y + 50, {"size": 10.5, "bold": true, "color": "#ffb47a"})
	if ost != null:
		var d: float = dps - ost.dps
		ui.text("%s%s vs %s%s" % ["+" if d > 0 else "", JS.num(d), shortName(other.type), (" " + stars(other.star)) if other.star > 1 else ""],
			px + 74, y + 50, {"size": 9.5, "bold": true, "color": "#7df2a9" if d > 0 else ("#e0786a" if d < 0 else "#8a8278")})
	else:
		ui.text("(%s dmg every %ss)" % [JS.num(st.attack), JS.to_fixed(def.attackInterval, 1)], px + 78, y + 50, {"size": 9.5, "color": "#8a8278"})
	y += 56
	if ost != null:
		_div(ctx, px, pw, y)
		var odef = C.UNITS.get(other.type)
		ui.text("compared with your weakest fielded unit — %s" % (odef.name if odef != null else other.type),
			px + 14, y + 13, {"size": 9.5, "color": "#9a917b"})
		y += 18
	# How it enters a fight — as important as its stats for where you place it.
	_div(ctx, px, pw, y)
	styleGlyph(ctx, px + 20, y + 15, 6.5, sd)
	ui.text(sd.name, px + 32, y + 18, {"size": 11.5, "bold": true, "color": sd.color})
	var sdl := self.wrap(sd.desc, 52).slice(0, 2)
	for i in sdl.size():
		ui.text(sdl[i], px + 14, y + 31 + i * 11, {"size": 9, "color": "#cabfa4"})
	y += 52
	if bonuses.size() > 0:
		_div(ctx, px, pw, y); ui.text("+ " + "  ".join(bonuses), px + 14, y + 13, {"size": 11, "bold": true, "color": "#ffb47a"}); y += 18
	if ab != null:
		_div(ctx, px, pw, y)
		ui.text(ab.name, px + 14, y + 13, {"size": 12, "bold": true, "color": ab.color})
		ui.text("ability — charges in battle", px + pw - 14, y + 13, {"align": "right", "size": 9, "color": "#8a8278"})
		for i in abLines.size():
			ui.text(abLines[i], px + 14, y + 26 + i * 12, {"size": 9.5, "color": "#cabfa4"})
		y += 20 + abLines.size() * 12
	_div(ctx, px, pw, y); y += 8
	ui.text("Relics %d/3" % relics.size(), px + 14, y - 0, {"size": 10, "color": "#9a917b"})
	if relics.is_empty():
		ui.text("— none equipped —", px + 70, y, {"size": 10, "color": "#8a8278"})
		return
	y += 6
	for it in relics:
		ctx.fillStyle = "rgba(8,6,3,0.9)"; ctx.beginPath(); ctx.arc(px + 22, y + 11, 10, 0, PI * 2); ctx.fill()
		ctx.strokeStyle = it.color; ctx.lineWidth = 1; ctx.stroke()
		itemIcon(ctx, px + 22, y + 11, 7, it)
		ui.text(it.name, px + 40, y + 8, {"size": 11.5, "bold": true, "color": it.color})
		ui.text(it.desc, px + 40, y + 21, {"size": 10, "color": "#cabfa4"})
		y += 30


## The unit tip's divider rule (the TS `div` closure).
func _div(ctx: Ctx, px: float, pw: float, y: float) -> void:
	ctx.strokeStyle = "rgba(255,255,255,0.08)"; ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(px + 10, y); ctx.lineTo(px + pw - 10, y); ctx.stroke()


## One labelled stat with its delta against the compared unit (the TS
## `statCell` closure).
func _statCell(ui: UI, label: String, val: String, sx: float, sy: float, col: String, delta = null, unit: String = "") -> void:
	ui.text(label, sx, sy, {"size": 9.5, "color": "#8a8278"})
	ui.text(val, sx + 32, sy, {"size": 12.5, "bold": true, "color": col})
	# Green when this unit is ahead, red when behind — read at a glance
	# rather than by subtracting two stat blocks in your head.
	if delta != null and absf(delta) > 0.001:
		var d := float(delta)
		var txt := ("+" if d > 0 else "") + (JS.num(d) if d == floorf(d) else JS.to_fixed(d, 2)) + unit
		ui.text(txt, sx + 88, sy, {"align": "right", "size": 9.5, "bold": true, "color": "#7df2a9" if d > 0 else "#e0786a"})


## Truncate a string to a max length with an ellipsis.
func clip(s: String, n: int) -> String:
	return s.substr(0, n - 1) + "…" if s.length() > n else s


## Live readout for a unit hovered during the fight: current HP, attack,
## armour and ability charge — its real, in-the-moment combat state.
func drawLiveTip(W: float, H: float) -> void:
	if liveTip == null or battle == null:
		return
	var e: Entity = liveTip.e
	if not e.alive:
		liveTip = null
		return
	var ui: UI = UI.ui
	var ctx := ui.ctx
	var def = C.UNITS.get(e.type)
	var name: String = def.name if def != null else e.type
	var ab = C.ABILITIES.get(e.type) if e.type != "villager" else null
	var charge := 1.0 if e.abilityActive > 0 else (maxf(0, minf(1, battle.chargeOf(e.id))) if ab != null else 0.0)
	var pw := 172.0
	var ph := 92.0 if ab != null else 70.0
	var px: float = liveTip.x
	var py: float = liveTip.y
	if px + pw > W - 4:
		px = maxf(4, px - pw - 28)
	py = maxf(4, minf(py, H - ph - 4))
	var teamCol := "#7fb0e8" if e.team == 0 else "#e88a7f"
	ctx.save()
	ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 13; ctx.shadowOffsetY = 3
	ctx.fillStyle = "rgba(18,14,9,0.97)"; roundRect(ctx, px, py, pw, ph, 8); ctx.fill()
	ctx.restore()
	ctx.strokeStyle = teamCol; ctx.lineWidth = 1.5; roundRect(ctx, px + 0.75, py + 0.75, pw - 1.5, ph - 1.5, 8); ctx.stroke()
	ui.text(name, px + 12, py + 17, {"size": 13, "bold": true, "color": "#f2e8d0"})
	ui.text("yours" if e.team == 0 else "enemy", px + pw - 12, py + 17, {"align": "right", "size": 9.5, "color": teamCol})
	var frac := maxf(0, e.hp / e.maxHp)
	var bx := px + 12
	var by := py + 25
	var bw := pw - 24
	var bh := 9.0
	ctx.fillStyle = "rgba(0,0,0,0.6)"; ctx.fillRect(bx, by, bw, bh)
	ctx.fillStyle = "#5ad06a" if frac > 0.5 else ("#ffd24a" if frac > 0.25 else "#e0564a"); ctx.fillRect(bx, by, bw * frac, bh)
	ui.text("%s / %s HP" % [JS.num(maxf(0, JS.round(e.hp))), JS.num(JS.round(e.maxHp))], px + pw / 2, by + bh + 10, {"align": "center", "size": 10, "bold": true, "color": "#e7ddc4"})
	ui.text("ATK %s" % JS.num(JS.round(e.attack)), px + 12, py + 62, {"size": 11, "bold": true, "color": "#ffce6a"})
	ui.text("ARM %s" % JS.num(e.armor), px + 92, py + 62, {"size": 11, "bold": true, "color": "#cfe0ff"})
	if ab != null:
		ui.text(ab.name, px + 12, py + 78, {"size": 10, "color": ab.color})
		ctx.fillStyle = "rgba(0,0,0,0.6)"; ctx.fillRect(px + 12, py + 82, pw - 24, 4)
		ctx.fillStyle = "#fff" if e.abilityActive > 0 else withAlpha(ab.color, 0.9); ctx.fillRect(px + 12, py + 82, (pw - 24) * charge, 4)
