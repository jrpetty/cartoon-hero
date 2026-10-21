## ui/rival_screen.ts — the Nemesis screen: your rival, what it knows about
## you, and the button to fight it. See meta/rival.ts for how it learns.
class_name RivalScreen
extends RefCounted

const GOLD := "#e8c060"
const TEXT := "#e7ddc4"
const DIM := "#a89f88"
const FAINT := "#6f6a5c"
const BLOOD := "#c8483e"

var st = null  ## RivalState (Dictionary) or null


## Re-read on open, so a match just played shows.
func open(playerFaction: String) -> void:
	st = Rival.loadRival(playerFaction)


## Returns "back", "fight" or null.
func draw(W: float, H: float, time: float, playerFaction: String) -> Variant:
	if st == null:
		open(playerFaction)
	var r: Dictionary = st.current
	var ui := UI.ui
	var ctx := ui.ctx
	var bg := ctx.createRadialGradient(W / 2, H * 0.3, 40, W / 2, H * 0.4, maxf(W, H))
	bg.addColorStop(0, "#2a130f"); bg.addColorStop(1, "#0c0706")
	ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H)

	var outer := minf(W - 32, 1100)
	var x0 := JS.round(W / 2 - outer / 2)
	var action = null
	ui.text("NEMESIS", x0, 44, {"size": 13, "bold": true, "color": BLOOD})
	ui.text("A rival who remembers how you play", x0, 66, {"size": 13, "color": DIM})

	var career := Career.careerLog()
	var mem := Rival.memoryOf(r, career)
	var fac = C.FACTIONS.get(r.faction)
	var rank := int(r.rank)

	# ---- the rival ----
	var narrow := outer < 760
	var cardH := 300.0 if narrow else 280.0
	ui.panel(x0, 84, outer, cardH)
	var px := x0 + outer - 90 if narrow else x0 + 130
	var py := 84 + cardH * 0.7
	ctx.save()
	ctx.fillStyle = Palette.withAlpha(BLOOD, 0.14)
	ctx.beginPath(); ctx.arc(px, 84 + cardH / 2, 70 if narrow else 110, 0, PI * 2); ctx.fill()
	ctx.restore()
	ctx.save()
	ctx.beginPath(); ctx.rect(x0 + 2, 86, outer - 4, cardH - 4); ctx.clip()
	portrait(px, py, 2.6 if narrow else 4.2, r.faction, time)
	ctx.restore()
	var tx := x0 + 20 if narrow else x0 + 280
	var tw := outer - (tx - x0) - (130 if narrow else 24)
	ui.text("%s · RANK %d OF 5" % [String(Rival.RANK_NAMES[rank]).to_upper(), rank], tx, 118, {"size": 11.5, "bold": true, "color": BLOOD})
	CareerScreen.fitText("%s %s" % [r.name, r.epithet], tx, 158, tw, {"size": 34, "bold": true, "color": "#ffe2c8", "font": "Georgia, serif"})
	var diff = C.DIFFICULTIES.get(Rival.RANK_DIFFICULTY[rank]) if rank < Rival.RANK_DIFFICULTY.size() else null
	ui.text("%s · plays at %s strength" % [fac.name if fac != null else r.faction, diff.name if diff != null else "Knight"], tx, 184, {"size": 13, "color": fac.color if fac != null else DIM})
	# Rank pips.
	for i in 5:
		ctx.fillStyle = BLOOD if i < rank else "rgba(255,255,255,0.1)"
		ctx.beginPath(); ctx.moveTo(tx + i * 22 + 8, 196); ctx.lineTo(tx + i * 22 + 16, 204); ctx.lineTo(tx + i * 22 + 8, 212); ctx.lineTo(tx + i * 22, 204); ctx.closePath(); ctx.fill()
	var lost := int(r.lostToYou)
	ui.text("Beat you %s× · lost to you %s× · %d more defeat%s ends them" % [JS.num(r.beatYou), JS.num(r.lostToYou), 3 - lost, "" if lost == 2 else "s"], tx + 124, 209, {"size": 12, "color": DIM})
	ui.text(r.origin, tx, 236, {"size": 12, "color": FAINT})
	# The taunt.
	ctx.fillStyle = "rgba(200,72,62,0.12)"
	ctx.fillRect(tx, 250, tw, 80 if narrow else 60)
	ctx.fillStyle = BLOOD; ctx.fillRect(tx, 250, 3, 80 if narrow else 60)
	wrap_("“%s”" % Rival.taunt(r, mem), tx + 14, 272, tw - 24, 14, "#ffd9c8", "italic 14px Georgia, serif")

	var y := 84 + cardH + 14
	# ---- what it knows ----
	var colW := outer if narrow else (outer - 14) / 2
	var knowH := 190.0
	ui.panel(x0, y, colW, knowH)
	ui.text("WHAT IT KNOWS ABOUT YOU", x0 + 16, y + 24, {"size": 11, "bold": true, "color": GOLD})
	var timing = mem.timing
	var timingText = null
	if timing == "early":
		timingText = "You attack early — it walls up first"
	elif timing == "late":
		timingText = "You attack late — it will rush you"
	elif timing == "mid":
		timingText = "You hit mid-game — it out-builds you"
	var grudge: String = r.get("grudge", "")
	var rows := [
		["Your army", ("Mostly %s — it brings the counter" % mem.armyWord) if mem.armyWord else null, "Learned after one battle"],
		["Your timing", timingText, "Learned after two battles"],
		["How you win", mem.winsBy if mem.winsBy else null, "Learned after three battles"],
		["Its grudge", ("Your %s scarred it — it brings their answer" % ("archers" if grudge == "archer" else grudge)) if grudge != "" else null, "Scar it to find out"],
	]
	for i in rows.size():
		var k: String = rows[i][0]
		var v = rows[i][1]
		var ry := y + 52 + i * 34
		ui.text(k, x0 + 16, ry, {"size": 12, "color": DIM})
		CareerScreen.fitText(v if v != null else "??? · %s" % rows[i][2], x0 + 120, ry, colW - 136, {"size": 13, "bold": v != null, "color": TEXT if v != null else FAINT})
	# ---- scars and history ----
	var hx := x0 if narrow else x0 + colW + 14
	var hy := y + knowH + 14 if narrow else y
	ui.panel(hx, hy, colW, knowH)
	ui.text("SCARS & BATTLES", hx + 16, hy + 24, {"size": 11, "bold": true, "color": GOLD})
	var ly := hy + 50
	var scars: Array = r.scars
	var history: Array = r.history
	if scars.is_empty() and history.is_empty():
		ui.text("You have never met. Yet.", hx + 16, ly, {"size": 12.5, "color": FAINT})
	for s in scars.slice(-2):
		ui.text("×", hx + 16, ly, {"size": 15, "bold": true, "color": BLOOD})
		CareerScreen.fitText(s, hx + 34, ly, colW - 50, {"size": 12.5, "color": TEXT})
		ly += 22
	var recentH: Array = history.slice(-4)
	recentH.reverse()
	for h in recentH:
		ui.text("▲" if h.rivalWon else "▼", hx + 16, ly, {"size": 11, "color": BLOOD if h.rivalWon else "#8fd07a"})
		CareerScreen.fitText("%s · %s" % [h.note, CareerScreen.localeDate(h.at)], hx + 34, ly, colW - 50, {"size": 12.5, "color": DIM})
		ly += 22
	y = (hy if narrow else y) + knowH + 14

	# ---- the fallen ----
	var fallen: Array = st.fallen
	if fallen.size() and y + 60 < H - 70:
		ui.text("FALLEN RIVALS", x0, y + 12, {"size": 11, "bold": true, "color": DIM})
		var names := fallen.slice(0, 5).map(func(f): return "%s %s (%s)" % [f.name, f.epithet, Rival.RANK_NAMES[int(f.rank)]])
		CareerScreen.fitText("  ·  ".join(PackedStringArray(names)), x0, y + 34, outer, {"size": 12.5, "color": TEXT})
		y += 52
	# ---- how it works, while there's room ----
	if y + 120 < H - 76:
		ui.panel(x0, y, outer, 104)
		ui.text("HOW A NEMESIS WORKS", x0 + 16, y + 24, {"size": 11, "bold": true, "color": DIM})
		var how := [
			"It learns a layer each time you meet — your army, then your timing, then how you win — and plays the counter.",
			"Beat it and it flees with a scar and a grudge against what did it. Three defeats end it; one of its captains rises to replace it.",
			"Lose and it climbs a rank, plays stronger, and earns a name for how it beat you.",
		]
		for i in how.size():
			CareerScreen.fitText("•  %s" % how[i], x0 + 16, y + 48 + i * 20, outer - 32, {"size": 12.5, "color": TEXT})

	# ---- actions ----
	var by := H - 62
	if ui.button("Back", x0, by, 130, 42, {"size": 15}):
		action = "back"
	if ui.button("Fight %s" % r.name, x0 + outer - 240, by, 240, 42, {"accent": true, "size": 16, "tooltip": ["A 1 v 1 against your rival", "Beat them: renown, and they carry a scar. Three defeats ends them.", "Lose: they rise a rank — and learn."]}):
		action = "fight"
	ui.text("Win: +%s renown%s" % [JS.num(60 * rank), " (+250 if it's the last)" if lost == 2 else ""], x0 + outer - 250, by + 26, {"size": 12, "color": GOLD, "align": "right"})
	return action


func portrait(x: float, y: float, s: float, faction: String, time: float) -> void:
	var hero := C.UNITS.has("hero")
	var def: Dictionary = C.UNITS.hero if hero else C.UNITS.knight
	var e := World.makeEntity()
	e.kind = T.Kind.Unit; e.type = "hero" if hero else "knight"; e.team = T.Team.Enemy; e.x = x; e.y = y
	e.radius = def.radius; e.hp = def.hp; e.maxHp = def.hp; e.facing = PI - 0.4; e.attackInterval = def.attackInterval
	e.animPhase = time * 0.3
	Draw.setFactionResolver(func(_t): return faction)
	var ctx := UI.ui.ctx
	ctx.save()
	ctx.translate(x, y); ctx.scale(s, s); ctx.translate(-x, -y)
	Draw.drawUnit(ctx, e, time * 0.3)  # a portrait is never worth a crash
	ctx.restore()
	Draw.setFactionResolver(null)


func wrap_(text: String, x: float, y: float, maxW: float, size: float, color, font: String) -> void:
	var ctx := UI.ui.ctx
	ctx.save()
	ctx.font = font; ctx.fillStyle = color
	var line := ""
	var yy := y
	for w in text.split(" "):
		var t := ("%s %s" % [line, w]) if line != "" else w
		if ctx.measureText(t).width > maxW and line != "":
			ctx.fillText(line, x, yy)
			line = w
			yy += size + 6
		else:
			line = t
	if line != "":
		ctx.fillText(line, x, yy)
	ctx.restore()
