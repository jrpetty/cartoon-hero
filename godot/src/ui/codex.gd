## ui/codex.ts — the Codex: in-game tech tree & encyclopedia. Tabs: Units,
## Buildings, Ages & Tech and Records. Unit/building art is drawn live with the
## real draw functions on display-only entities, so the codex always matches
## the game.
##
## (The module's one class, CodexScreen, is the file's class.)
class_name CodexScreen
extends RefCounted

const CLASS_LABEL := {
	"infantry": "Infantry",
	"archer": "Ranged",
	"cavalry": "Cavalry",
	"siege": "Siege",
	"villager": "Villager",
	"building": "Building",
}


## Seconds as m:ss — match lengths read as durations, not as numbers.
static func mmssOf(s: float) -> String:
	return "%s:%s" % [JS.num(floorf(s / 60.0)), JS.num(floorf(fmod(s, 60.0))).lpad(2, "0")]


static func fmtCost(c: Dictionary) -> String:
	var parts: Array = []
	if c.get("food", 0):
		parts.append("🍖%s" % JS.num(c.food))
	if c.get("wood", 0):
		parts.append("🪵%s" % JS.num(c.wood))
	if c.get("gold", 0):
		parts.append("🪙%s" % JS.num(c.gold))
	return "  ".join(PackedStringArray(parts)) if parts.size() else "Free"


## Tiny word-wrapper (screens.ts keeps its own private).
static func wrap_(text: String, x: float, y: float, maxW: float, size: float, color) -> float:
	var ctx := UI.ui.ctx
	ctx.font = "%spx \"Trebuchet MS\", sans-serif" % JS.num(size)
	ctx.fillStyle = color
	ctx.textAlign = "left"
	var line := ""
	var yy := y
	for w in text.split(" "):
		var probe := line + " " + w if line != "" else w
		if ctx.measureText(probe).width > maxW and line != "":
			ctx.fillText(line, x, yy)
			yy += size + 4
			line = w
		else:
			line = probe
	if line != "":
		ctx.fillText(line, x, yy)
	return yy + size + 4


static func _classLabel(cls: String) -> String:
	return CLASS_LABEL.get(cls, cls)


var tab := "units"  ## "units" | "buildings" | "tech" | "records"
var selUnit := "militia"
var unitScroll := 0.0
var selBuilding := "town_center"
## Display-only entities for live art, keyed by def id.
var models := {}


func model(id: String, kind: int) -> Entity:
	var e = models.get(id)
	if e != null:
		return e
	e = World.makeEntity()
	e.kind = kind
	e.team = T.Team.Player
	e.type = id
	if kind == T.Kind.Unit:
		var def: Dictionary = C.UNITS[id]
		e.radius = def.radius
		e.attackInterval = def.attackInterval
		e.hp = def.hp
		e.maxHp = def.hp
		e.facing = -PI / 6
		if def.get("hero"):
			e.heroLevel = 3 # show the pips
	else:
		var def: Dictionary = C.BUILDINGS[id]
		e.radius = (def.tiles * C.TILE) / 2
		e.hp = def.hp
		e.maxHp = def.hp
		e.buildState = T.BuildState.Done
	models[id] = e
	return e


## Pedestal + live art, scaled to fit the box.
func drawModel(id: String, kind: int, cx: float, cy: float, box: float, time: float) -> void:
	var ctx := UI.ui.ctx
	var PAL: Dictionary = C.PAL
	var e := model(id, kind)
	e.x = cx
	e.y = cy
	e.animPhase = time
	# grass pedestal
	var g := ctx.createRadialGradient(cx, cy + box * 0.18, 4, cx, cy + box * 0.18, box * 0.52)
	g.addColorStop(0, Palette.shade(PAL.grass, 0.06))
	g.addColorStop(1, Palette.shade(PAL.grassDark, -0.12))
	ctx.fillStyle = g
	ctx.beginPath()
	ctx.ellipse(cx, cy + box * 0.18, box * 0.5, box * 0.26, 0, 0, PI * 2)
	ctx.fill()
	var scale: float
	if kind == T.Kind.Unit:
		scale = maxf(2.2, minf(3.2, box / (e.radius * 6)))
	else:
		scale = maxf(1.1, minf(3, (box * 0.42) / e.radius))
	ctx.save()
	ctx.translate(cx, cy)
	ctx.scale(scale, scale)
	ctx.translate(-cx, -cy)
	# A faction's own unit is shown as its people draw it.
	var fac = Factions.factionForUnit(id) if kind == T.Kind.Unit else null
	if fac != null:
		var fid: String = fac.id
		Draw.setFactionResolver(func(_t): return fid)
	if kind == T.Kind.Unit:
		Draw.drawUnit(ctx, e, time)
	else:
		Draw.drawBuilding(ctx, e, time, T.Team.Player)
	if fac != null:
		Draw.setFactionResolver(null)
	ctx.restore()


func statRow(label: String, value: String, x: float, y: float, w: float) -> void:
	var ui := UI.ui
	ui.text(label, x, y, {"size": 13, "color": "#bdb49a"})
	ui.text(value, x + w, y, {"size": 13, "bold": true, "align": "right"})


func chips(items: Array, x: float, y: float, maxW: float, color) -> float:
	var ctx := UI.ui.ctx
	var cx := x
	var cy := y
	ctx.font = "bold 12px sans-serif"
	for item in items:
		var w := ctx.measureText(item).width + 16
		if cx + w > x + maxW:
			cx = x
			cy += 24
		ctx.fillStyle = Palette.withAlpha(color, 0.16)
		ctx.fillRect(cx, cy - 13, w, 19)
		ctx.strokeStyle = Palette.withAlpha(color, 0.55)
		ctx.lineWidth = 1
		ctx.strokeRect(cx, cy - 13, w, 19)
		ctx.fillStyle = color
		ctx.textAlign = "left"
		ctx.fillText(item, cx + 8, cy + 1)
		cx += w + 6
	return cy + 28


## Returns "back" or null.
func draw(W: float, H: float, time: float, profile: Profile) -> Variant:
	var ui := UI.ui
	Screens.drawMenuBackground(W, H, time)
	var ctx := ui.ctx
	ctx.fillStyle = "rgba(10, 8, 4, 0.45)"
	ctx.fillRect(0, 0, W, H)

	ui.text("Codex — Tech Tree & Encyclopedia", W / 2, 46, {
		"align": "center", "size": 30, "bold": true, "color": "#ffe9b0", "font": "Georgia, serif",
	})

	# Tabs.
	var tabs := [["units", "Units"], ["buildings", "Buildings"], ["tech", "Ages & Tech"], ["records", "Records"]]
	var tw := 150.0
	var tx := W / 2 - (tabs.size() * (tw + 10) - 10) / 2
	for t in tabs:
		if ui.button(t[1], tx, 70, tw, 36, {"accent": tab == t[0], "size": 15}):
			tab = t[0]
		tx += tw + 10

	var top := 124.0
	if tab == "units":
		drawUnits(W, H, top, time)
	elif tab == "buildings":
		drawBuildings(W, H, top, time)
	elif tab == "records":
		drawRecords(W, H, top, profile)
	else:
		drawTech(W, H, top)

	var action = null
	if ui.button("⟵ Back", 24, H - 68, 130, 44, {"size": 15}):
		action = "back"
	return action


# ---------------------------------------------------------------- records --
#
# What the last twenty games looked like, and what is still unearned. Lives
# in the Codex rather than on its own menu entry because it is the same kind
# of thing as the rest of it — a reference you go and look at, not a mode you
# enter.

## The history row currently opened back up as its full report, if any.
var openReport = null
var reportTab := "overview"


func drawRecords(W: float, H: float, top: float, profile: Profile) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var PAL: Dictionary = C.PAL
	# A report opened from the list takes over the tab. The summary row can
	# never say what actually went wrong in a game; the report can, and it was
	# being thrown away at Continue.
	if openReport != null and openReport.get("report") != null:
		var r: Dictionary = openReport
		var colW := minf(1180, W - 60)
		var x0 := JS.round(W / 2 - colW / 2)
		ui.panel(x0, top, colW, H - top - 84, {"light": true})
		ui.text("%s — %s" % ["Victory" if r.won else "Defeat", r.mapName], x0 + 20, top + 28,
			{"size": 17, "bold": true, "color": "#7df2a9" if r.won else "#e0786a"})
		ui.text("%s · %s · %s players · %s · %s" % [CareerScreen.localeDate(r.at), r.mode, JS.num(r.players), r.difficulty, mmssOf(r.durationSec)],
			x0 + 20, top + 48, {"size": 11.5, "color": "#9a917b"})
		var tx := x0 + 20
		for t in MatchReport.REPORT_TABS:
			if ui.button(t.label, tx, top + 62, 106, 26, {"accent": reportTab == t.id, "size": 12}):
				reportTab = t.id
			tx += 112
		if ui.button("‹ Back to records", x0 + colW - 170, top + 62, 150, 26, {"size": 12}):
			openReport = null
		MatchReport.drawReportKey(x0 + colW - 20, top + 36, r.report)
		MatchReport.drawReportTab(reportTab, x0 + 26, top + 108, colW - 52, H - top - 210, r.report)
		return
	var rows := History.listHistory()
	var sum := History.summariseHistory(rows)
	var colW := minf(1180, W - 60)
	var x0 := JS.round(W / 2 - colW / 2)
	var leftW := JS.round(colW * 0.54)
	var rightX := x0 + leftW + 20
	var rightW := colW - leftW - 20
	var panelH := H - top - 84

	# ---- the last twenty ----
	ui.panel(x0, top, leftW, panelH, {"light": true})
	ui.text("Last twenty matches", x0 + 18, top + 26, {"size": 16, "bold": true, "color": PAL.uiAccent})
	if rows.is_empty():
		ui.text("Nothing recorded yet. Play a skirmish.", x0 + 18, top + 56, {"size": 13, "color": "#6f6a5c"})
	else:
		# The summary line first: the whole point of keeping a record is the
		# trend, and a trend belongs above the rows it came from.
		ui.text("%sW %sL  ·  %s%%  ·  %s kills per loss  ·  best streak %s" % [JS.num(sum.won), JS.num(sum.played - sum.won), JS.num(JS.round(sum.winRate * 100)), JS.to_fixed(sum.killRatio, 2), JS.num(sum.bestStreak)],
			x0 + 18, top + 46, {"size": 11.5, "color": "#cabfa4"})
		ui.text("Town Centre idle %s%% of an average match  ·  typical length %s" % [JS.num(JS.round(sum.avgTcIdleShare * 100)), mmssOf(sum.avgDuration)],
			x0 + 18, top + 62, {"size": 11.5, "color": "#8f8770"})
		var ry := top + 84
		var rowH := 30.0
		for r in rows:
			if ry + rowH > top + panelH - 8:
				break
			ctx.fillStyle = "rgba(90,150,90,0.14)" if r.won else "rgba(150,80,70,0.13)"
			ctx.beginPath(); ctx.roundRect(x0 + 12, ry, leftW - 24, rowH - 4, 4); ctx.fill()
			ctx.fillStyle = "#7df2a9" if r.won else "#e0786a"
			ctx.fillRect(x0 + 12, ry, 3, rowH - 4)
			ui.text("WIN" if r.won else "LOSS", x0 + 24, ry + 17,
				{"size": 11, "bold": true, "color": "#7df2a9" if r.won else "#e0786a"})
			ui.text(String(r.mapName).substr(0, 22), x0 + 66, ry + 17, {"size": 11.5, "color": "#e7ddc4"})
			ui.text("%s · %sp" % [r.difficulty, JS.num(r.players)], x0 + 220, ry + 17, {"size": 10.5, "color": "#8f8770"})
			ui.text(mmssOf(r.durationSec), x0 + 320, ry + 17, {"size": 10.5, "color": "#8f8770"})
			ui.text("%s/%s" % [JS.num(r.unitsKilled), JS.num(r.unitsLost)], x0 + leftW - 92, ry + 17,
				{"size": 10.5, "align": "right", "color": "#cabfa4"})
			# Only the newest few kept their full report — the rest are summaries,
			# so say so rather than offering a button that does nothing.
			if r.get("report") != null:
				if ui.button("Report", x0 + leftW - 84, ry + 2, 62, 22, {"size": 10.5}):
					openReport = r
					reportTab = "overview"
			else:
				ui.text("—", x0 + leftW - 53, ry + 17, {"size": 11, "align": "center", "color": "#5a5548"})
			ry += rowH

	# ---- achievements and this week's challenges ----
	ui.panel(rightX, top, rightW, panelH, {"light": true})
	var doneSet := {}
	for id in profile.data.achievements:
		doneSet[id] = true
	var weeklyDone := {}
	for id in profile.data.challengesDone:
		weeklyDone[id] = true
	var week := Achievements.weekIndex(JS.now_ms())
	ui.text("This week", rightX + 18, top + 26, {"size": 16, "bold": true, "color": PAL.uiAccent})
	var ay := top + 52
	for c in Achievements.challengesForWeek(week):
		var got := weeklyDone.has("%s:%s" % [JS.num(week), c.id])
		ui.text("◈" if got else "◇", rightX + 18, ay, {"size": 13, "bold": true, "color": "#7fd0ff" if got else "#5a5548"})
		ui.text(c.name, rightX + 38, ay, {"size": 12.5, "bold": true, "color": "#7fd0ff" if got else "#e7ddc4"})
		ui.text("+%s ⚔" % JS.num(c.valor), rightX + rightW - 18, ay, {
			"size": 11.5, "align": "right", "bold": true, "color": "#5a5548" if got else "#ffd24a",
		})
		ui.text(c.desc, rightX + 38, ay + 14, {"size": 10.5, "color": "#8f8770"})
		ay += 36

	ay += 10
	var ACH: Array = Achievements.ACHIEVEMENTS
	ui.text("Achievements  %d / %d" % [doneSet.size(), ACH.size()], rightX + 18, ay,
		{"size": 16, "bold": true, "color": PAL.uiAccent})
	ay += 24
	for a in ACH:
		if ay + 30 > top + panelH - 8:
			break
		var got := doneSet.has(a.id)
		ui.text("★" if got else "☆", rightX + 18, ay + 12,
			{"size": 13, "bold": true, "color": "#ffd24a" if got else "#5a5548"})
		ui.text(a.name, rightX + 38, ay + 12, {"size": 12, "bold": true, "color": "#ffe9b0" if got else "#9a917b"})
		ui.text("+%s ⚔" % JS.num(a.valor), rightX + rightW - 18, ay + 12, {
			"size": 11, "align": "right", "color": "#5a5548" if got else "#ffd24a",
		})
		ui.text(a.desc, rightX + 38, ay + 25, {"size": 10, "color": "#6f6a5c" if got else "#8f8770"})
		ay += 32


# ------------------------------------------------------------------ units --
func drawUnits(W: float, H: float, top: float, time: float) -> void:
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	var ids: Array = C.UNITS.keys()
	var listW := 330.0
	var x0 := maxf(20, W / 2 - 470)
	var listH := H - top - 84
	ui.panel(x0, top, listW, listH)
	# Thirty-nine units don't fit a screen: the list scrolls.
	var contentH := 12 + ids.size() * 38
	var maxScroll := maxf(0, contentH - listH + 12)
	if ui.wheel and ui.mx > x0 and ui.mx < x0 + listW and ui.my > top and ui.my < top + listH:
		unitScroll = maxf(0, minf(maxScroll, unitScroll + ui.wheel * 0.6))
	unitScroll = minf(unitScroll, maxScroll)
	ui.pushScroll(unitScroll, {"x": x0, "y": top + 4, "w": listW, "h": listH - 8})
	var ly := top + 12
	for id in ids:
		var def: Dictionary = C.UNITS[id]
		var sel: bool = selUnit == id
		if ui.button("", x0 + 10, ly, listW - 20, 34, {"accent": sel}):
			selUnit = id
		ui.text(def.name, x0 + 22, ly + 17, {"size": 14, "bold": true, "color": "#ffe9b0" if sel else PAL.uiParchment})
		# Where it comes from: its age, or the Oath or faction that owns it.
		var fac = Factions.factionForUnit(id)
		var oath = def.get("oath")
		var src: String
		var srcCol
		if oath:
			var o = C.OATHS.get(oath)
			src = o.short if o != null else "Oath"
			srcCol = o.color if o != null else "#bdb49a"
		elif fac != null:
			src = _strip_the(fac.name)
			srcCol = fac.color
		else:
			src = C.AGES[int(def.age)].short
			srcCol = "#bdb49a"
		ui.text("%s · %s" % [_classLabel(def.armorClass), src], x0 + listW - 22, ly + 17, {"size": 11, "align": "right", "color": srcCol})
		ly += 38
	ui.popScroll()
	ui.scrollbar(x0 + listW - 8, top + 6, listH - 12, unitScroll, contentH)

	# Detail panel.
	var dx := x0 + listW + 16
	var dw := minf(580, W - dx - 20)
	ui.panel(dx, top, dw, H - top - 84, {"light": true})
	var def = C.UNITS.get(selUnit)
	if def == null:
		return
	drawModel(selUnit, T.Kind.Unit, dx + 92, top + 86, 130, time)

	ui.text(def.name, dx + 180, top + 38, {"size": 22, "bold": true, "color": PAL.uiAccent, "font": "Georgia, serif"})
	var oathName := ""
	if def.get("oath"):
		var o = C.OATHS.get(def.oath)
		oathName = " · %s" % (o.name if o != null else "")
	ui.text("%s — %s%s  ·  %s  ·  %s pop" % [_classLabel(def.armorClass), C.AGES[int(def.age)].name, oathName, fmtCost(def.cost), JS.num(def.pop)],
		dx + 180, top + 60, {"size": 13, "color": "#d8cdb4"})
	var yy := wrap_(def.desc, dx + 180, top + 84, dw - 200, 13, "#bdb49a")

	# Stat grid (two columns).
	var colW := (dw - 60) / 2
	var sx1 := dx + 24
	var sx2 := dx + 40 + colW
	var sy := top + 170
	var dps := JS.to_fixed(def.attack / def.attackInterval, 1) if def.attack > 0 else "—"
	statRow("Hit points", JS.num(def.hp), sx1, sy, colW)
	statRow("Speed", JS.num(def.speed), sx2, sy, colW)
	sy += 24
	statRow("Attack", ("%s (%s/s)" % [JS.num(def.attack), dps]) if def.attack else "—", sx1, sy, colW)
	statRow("Armor", JS.num(def.armor), sx2, sy, colW)
	sy += 24
	statRow("Range", JS.num(def.range) if def.get("ranged") else "Melee", sx1, sy, colW)
	statRow("Vision", JS.num(def.visionRange), sx2, sy, colW)
	sy += 24
	var at = C.BUILDINGS.get(def.trainedAt)
	statRow("Train time", "%ss" % JS.num(def.buildTime), sx1, sy, colW)
	statRow("Trained at", at.name if at != null else def.trainedAt, sx2, sy, colW)
	sy += 34

	# Counter web.
	var strong: Array = []
	for cls in def.bonus:
		strong.append("+%s vs %s" % [JS.num(def.bonus[cls]), _classLabel(cls)])
	if strong.size():
		ui.text("Bonus damage", sx1, sy, {"size": 13, "bold": true, "color": PAL.uiGood})
		sy = chips(strong, sx1 + 110, sy, dw - 150, PAL.uiGood)
	var counteredBy: Array = []
	for k in C.UNITS:
		if k != selUnit and float(C.UNITS[k].bonus.get(def.armorClass, 0)) >= 4:
			counteredBy.append(C.UNITS[k].name)
	if counteredBy.size():
		ui.text("Countered by", sx1, sy, {"size": 13, "bold": true, "color": PAL.uiBad})
		sy = chips(counteredBy, sx1 + 110, sy, dw - 150, PAL.uiBad)

	# Ability.
	var ab = C.ABILITIES.get(selUnit)
	if ab != null:
		sy += 4
		ui.text("★ %s  (Q · %ss cooldown)" % [ab.name, JS.num(ab.cooldown)], sx1, sy, {"size": 14, "bold": true, "color": "#ffd24a"})
		sy = wrap_(ab.desc, sx1, sy + 22, dw - 60, 13, "#d8cdb4")


static func _strip_the(s: String) -> String:
	return s.substr(4) if s.begins_with("The ") else s


# -------------------------------------------------------------- buildings --
func drawBuildings(W: float, H: float, top: float, time: float) -> void:
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	var ids: Array = C.BUILDINGS.keys()
	var listW := 330.0
	var x0 := maxf(20, W / 2 - 470)
	var rowH := 30.0
	ui.panel(x0, top, listW, H - top - 84)
	var ly := top + 10
	for id in ids:
		var def: Dictionary = C.BUILDINGS[id]
		var sel: bool = selBuilding == id
		if ui.button("", x0 + 10, ly, listW - 20, rowH - 4, {"accent": sel}):
			selBuilding = id
		ui.text(def.name, x0 + 22, ly + 13, {"size": 13, "bold": true, "color": "#ffe9b0" if sel else PAL.uiParchment})
		ui.text(String(C.AGES[int(def.age)].name).split(" ")[0], x0 + listW - 22, ly + 13, {"size": 11, "align": "right", "color": "#bdb49a"})
		ly += rowH

	var dx := x0 + listW + 16
	var dw := minf(580, W - dx - 20)
	ui.panel(dx, top, dw, H - top - 84, {"light": true})
	var def = C.BUILDINGS.get(selBuilding)
	if def == null:
		return
	drawModel(selBuilding, T.Kind.Building, dx + 100, top + 96, 168, time)

	ui.text(def.name, dx + 200, top + 38, {"size": 22, "bold": true, "color": PAL.uiAccent, "font": "Georgia, serif"})
	ui.text("%s  ·  %s  ·  %ss build" % [C.AGES[int(def.age)].name, fmtCost(def.cost), JS.num(def.buildTime)],
		dx + 200, top + 60, {"size": 13, "color": "#d8cdb4"})
	var yy := wrap_(def.desc, dx + 200, top + 84, dw - 220, 13, "#bdb49a")

	var colW := (dw - 60) / 2
	var sx1 := dx + 24
	var sx2 := dx + 40 + colW
	var sy := top + 210
	statRow("Hit points", JS.num(def.hp), sx1, sy, colW)
	statRow("Footprint", "%s×%s tiles" % [JS.num(def.tiles), JS.num(def.tiles)], sx2, sy, colW)
	sy += 24
	if float(def.get("attack", 0)) > 0:
		statRow("Attack", "%s @ %s range" % [JS.num(def.attack), JS.num(def.range)], sx1, sy, colW)
		statRow("Fire rate", "%ss" % JS.num(def.attackInterval), sx2, sy, colW)
		sy += 24
	if def.get("popProvided"):
		statRow("Population", "+%s" % JS.num(def.popProvided), sx1, sy, colW)
		sy += 24
	if def.get("garrisonCap"):
		statRow("Garrison", "%s units" % JS.num(def.garrisonCap), sx1, sy, colW)
		sy += 24
	if def.get("isDropoff"):
		statRow("Drop-off", ", ".join(PackedStringArray(def.dropoffKinds)), sx1, sy, colW)
		sy += 24
	if def.get("requires"):
		statRow("Requires", C.BUILDINGS[def.requires].name, sx1, sy, colW)
		sy += 24
	sy += 10
	var trains: Array = def.get("trains", [])
	if trains.size():
		ui.text("Trains", sx1, sy, {"size": 13, "bold": true, "color": PAL.uiAccent})
		sy = chips(trains.map(func(t): return C.UNITS[t].name if C.UNITS.has(t) else t), sx1 + 70, sy, dw - 110, PAL.uiAccent)
	# Who can break it down fastest.
	var breakers: Array = []
	for k in C.UNITS:
		if float(C.UNITS[k].bonus.get("building", 0)) >= 20:
			breakers.append(C.UNITS[k].name)
	if breakers.size():
		ui.text("Felled by", sx1, sy, {"size": 13, "bold": true, "color": PAL.uiBad})
		chips(breakers, sx1 + 70, sy, dw - 110, PAL.uiBad)


# ------------------------------------------------------------------- tech --
var techScroll := 0.0
var techContentH := 0.0


func drawTech(W: float, H: float, top: float) -> void:
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	var x0 := maxf(20, W / 2 - 470)
	var fullW := minf(940, W - x0 - 20)
	var viewH := maxf(160, H - top - 84)
	# Four ages, nine Oaths and the full research list are more than a screen:
	# scroll, measured from last frame like the setup screen.
	var maxScroll := maxf(0, techContentH - viewH)
	if ui.wheel and ui.my > top and ui.my < top + viewH:
		techScroll = maxf(0, minf(maxScroll, techScroll + ui.wheel * 0.6))
	techScroll = minf(techScroll, maxScroll)
	ui.pushScroll(techScroll, {"x": 0, "y": top, "w": W, "h": viewH})

	# Ages timeline — however many ages there are.
	var AGES: Array = C.AGES
	var n := AGES.size()
	var gap := 20.0
	var aw := (fullW - gap * (n - 1)) / n
	var ageH := 232.0
	for i in n:
		var age: Dictionary = AGES[i]
		var ax := x0 + i * (aw + gap)
		ui.panel(ax, top, aw, ageH, {"light": true})
		ui.text(age.name, ax + 14, top + 26, {"size": 17, "bold": true, "color": PAL.uiAccent, "font": "Georgia, serif"})
		var ty := wrap_(
			"Where every realm begins." if i == 0 else "%s · %ss at the Town Center" % [fmtCost(age.cost), JS.num(age.advanceTime)],
			ax + 14, top + 48, aw - 28, 12, "#bdb49a")
		if float(age.get("requiresCount", 0)) > 0:
			var names := ", ".join(PackedStringArray((age.requiresAny as Array).map(func(id): return C.BUILDINGS[id].name if C.BUILDINGS.has(id) else id)))
			ty = wrap_("Requires any %s of: %s" % [JS.num(age.requiresCount), names], ax + 14, ty + 4, aw - 28, 11.5, "#d8cdb4")
		# Oath units are unlocked by the Oath, not the age — they're listed there.
		var unlock: Array = []
		for u in C.UNITS.values():
			if int(u.age) == i and not u.get("oath"):
				unlock.append(u.name)
		for b in C.BUILDINGS.values():
			if int(b.age) == i:
				unlock.append(b.name)
		ui.text("Unlocks", ax + 14, ty + 12, {"size": 12, "bold": true, "color": PAL.uiGood})
		var ul := ", ".join(PackedStringArray(unlock))
		ty = wrap_(ul if ul != "" else "—", ax + 14, ty + 30, aw - 28, 11, "#d8cdb4")
		var oaths := Oaths.oathsForAge(i)
		if oaths.size():
			ui.text("Swear one Oath", ax + 14, ty + 12, {"size": 12, "bold": true, "color": "#ffe9b0"})
			var ox := ax + 14
			var oy := ty + 30
			for o in oaths:
				ui.ctx.font = "bold 12px \"Trebuchet MS\", sans-serif"
				var w := ui.ctx.measureText(o.short).width
				if ox + w > ax + aw - 14:
					ox = ax + 14
					oy += 17
				ui.text(o.short, ox, oy, {"size": 12, "bold": true, "color": o.color})
				ox += w + 12
		if i < n - 1:
			var cx := ax + aw + gap / 2
			var cy := top + 98
			ui.ctx.strokeStyle = PAL.uiAccent
			ui.ctx.lineWidth = 3
			ui.ctx.beginPath()
			ui.ctx.moveTo(cx - 4, cy - 7)
			ui.ctx.lineTo(cx + 4, cy)
			ui.ctx.lineTo(cx - 4, cy + 7)
			ui.ctx.stroke()

	# The Oaths — this game's answer to civilisations.
	var y := top + ageH + 20
	var oathCols := 3
	var ow := (fullW - (oathCols - 1) * 14) / oathCols
	var oathList: Array = C.OATHS.values()
	ui.text("Oaths", x0, y + 12, {"size": 18, "bold": true, "color": PAL.uiAccent, "font": "Georgia, serif"})
	y = wrap_(
		"Every age you advance to, your realm swears one of three Oaths. It is how a realm gets its identity — chosen during the match, with the map in view and the enemy scouted, instead of a civilisation picked before it. Everyone is told what you swear.",
		x0, y + 34, fullW, 12.5, "#d8cdb4") + 8
	var cardH := 150.0
	for k in oathList.size():
		var o: Dictionary = oathList[k]
		var ox := x0 + (k % oathCols) * (ow + 14)
		var oy := y + floori(float(k) / oathCols) * (cardH + 12)
		ui.panel(ox, oy, ow, cardH, {"light": true})
		ui.ctx.fillStyle = o.color
		ui.ctx.fillRect(ox + 3, oy + 3, ow - 6, 4)
		ui.text(o.name, ox + 12, oy + 22, {"size": 14, "bold": true, "color": "#ffe9b0"})
		var oage := int(o.age)
		ui.text("%s · “%s”" % [AGES[oage].name if oage < AGES.size() else "", o.motto], ox + 12, oy + 40, {"size": 11, "color": "#bdb49a"})
		var ly := oy + 60
		for line in Oaths.lines(o.id, 1):
			ly = wrap_("• %s" % line, ox + 12, ly, ow - 24, 11.5, "#e7ddc4") + 3
	y += ceilf(float(oathList.size()) / oathCols) * (cardH + 12) + 12

	# Factions — who a realm is, before the Oaths make it anything else.
	ui.text("Factions", x0, y + 12, {"size": 18, "bold": true, "color": PAL.uiAccent, "font": "Georgia, serif"})
	y = wrap_(
		"Chosen before the match: a people with its own look, a few bonuses with a price attached, and one or two soldiers of its own. Each is strongest at a different point in a match; none is simply better.",
		x0, y + 34, fullW, 12.5, "#d8cdb4") + 8
	var facCardH := 176.0
	var CURVES := {"early": "Strong early", "mid": "Peaks mid-game", "late": "Strong late", "steady": "Steady"}
	for k in C.FACTION_IDS.size():
		var f: Dictionary = C.FACTIONS[C.FACTION_IDS[k]]
		var fx := x0 + (k % oathCols) * (ow + 14)
		var fy := y + floori(float(k) / oathCols) * (facCardH + 12)
		ui.panel(fx, fy, ow, facCardH, {"light": true})
		ui.ctx.fillStyle = f.color
		ui.ctx.fillRect(fx + 3, fy + 3, ow - 6, 4)
		ui.text(f.name, fx + 12, fy + 22, {"size": 14, "bold": true, "color": "#ffe9b0"})
		ui.text("%s · %s" % [f.era, CURVES.get(f.curve, "")], fx + 12, fy + 40, {"size": 11, "color": f.color})
		var ly := fy + 60
		for line in f.strengths:
			ly = wrap_("+ %s" % line, fx + 12, ly, ow - 24, 11.5, "#b8e0b0") + 2
		for line in f.weaknesses:
			ly = wrap_("– %s" % line, fx + 12, ly, ow - 24, 11.5, "#e8b0a4") + 2
	y += ceilf(float(C.FACTION_IDS.size()) / oathCols) * (facCardH + 12) + 12

	# Research, grouped by age.
	var ups: Array = C.UPGRADES.values().duplicate()
	JS.sort_by_cmp(ups, func(a, b): return float(a.age) - float(b.age))
	var cols := 3
	var uw := (fullW - 32 - (cols - 1) * 14) / cols
	var rows := ceili(float(ups.size()) / cols)
	var panelH := 44 + rows * 78 + 8
	ui.panel(x0, y, fullW, panelH)
	ui.text("Research", x0 + 16, y + 24, {"size": 16, "bold": true, "color": PAL.uiAccent, "font": "Georgia, serif"})
	for i in ups.size():
		var up: Dictionary = ups[i]
		var ux := x0 + 16 + (i % cols) * (uw + 14)
		var uyy := y + 44 + floori(float(i) / cols) * 78
		ui.panel(ux, uyy, uw, 70, {"light": true})
		ui.text(up.name, ux + 12, uyy + 18, {"size": 13, "bold": true})
		ui.text("%s · %s · %ss" % [AGES[int(up.age)].short, fmtCost(up.cost), JS.num(up.time)],
			ux + 12, uyy + 36, {"size": 11, "color": "#bdb49a"})
		wrap_(up.desc, ux + 12, uyy + 54, uw - 24, 11, "#d8cdb4")
	y += panelH
	ui.popScroll()
	techContentH = y - top + 12
	ui.scrollbar(x0 + fullW + 6, top, viewH, techScroll, techContentH)
