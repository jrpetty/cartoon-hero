## ui/oath_picker.ts — the Oath picker: what advancing an age looks like now.
##
## Advancing used to be one button. It is still one decision, but now it is the
## decision of who the realm becomes, so it gets a proper screen: three cards
## side by side, each saying in plain lines what the Oath does, with its
## signature unit drawn on the card so you know what you are buying before you
## meet it in a fight. The match keeps running behind it — it is a choice, not
## a pause.
class_name OathPicker
extends RefCounted

## The question each advance asks — the three Oaths are its answers.
const AGE_QUESTION := {
	1: "How will your realm grow?",
	2: "What will your army be?",
	3: "What will your realm be remembered for?",
}

const KIND_LABEL := {"growth": "Growth", "war": "War", "guard": "Guard"}

## The Town Centre the advance will be researched at, while open.
var building: Entity = null
var portrait: Entity = null

var isOpen: bool:
	get:
		return building != null


func open(b: Entity) -> void:
	building = b


func close() -> void:
	building = null


## Draw the picker if open. Calls `swear(building, techId)` with the research
## id ("age:<oath>") when a card is chosen, and closes itself.
func draw(W: float, H: float, world: World, team: int, time: float, swear: Callable) -> void:
	var b := building
	if b == null:
		return
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	# Closed underneath us: the Town Centre fell, or the advance is already
	# under way (or done) from somewhere else.
	var p := world.player(team)
	var next := p.age + 1
	var ages: Array = C.AGES
	var aging := false
	for e: Entity in world.entities:
		if e.alive and e.team == team and e.kind == T.Kind.Building and e.productionQueue.has("a:age"):
			aging = true
			break
	if not b.alive or next >= ages.size() or aging:
		close()
		return
	var age: Dictionary = ages[next]
	var oaths := Oaths.oathsForAge(next)
	var ctx := ui.ctx

	ctx.fillStyle = "rgba(8, 6, 3, 0.66)"
	ctx.fillRect(0, 0, W, H)

	var cardW := minf(330, floor((W - 80) / 3) - 16)
	var cardH := minf(430, H - 210)
	var gap := 18.0
	var totalW := cardW * 3 + gap * 2
	var x0 := JS.round((W - totalW) / 2)
	var y0 := JS.round(maxf(120, (H - cardH) / 2 + 20))

	ui.text("Advance to the %s" % age.name, W / 2, y0 - 72, {
		"align": "center", "size": 28, "bold": true, "color": "#ffe9b0", "font": "Georgia, serif",
	})
	ui.text("Swear an Oath — %s" % AGE_QUESTION.get(next, ""), W / 2, y0 - 42, {"align": "center", "size": 15, "color": "#d8cdb0"})

	var req := world.ageRequirementProgress(team, next)
	var reqOk: bool = req.have >= req.need
	var afford := world.canAfford(p.resources, age.cost)
	var costLine := "Costs %s · %ss at the Town Centre" % [fmtCost(age.cost), JS.num(age.advanceTime)]
	var reqLine := ""
	if not reqOk:
		var names: Array = []
		for id in age.requiresAny:
			var bd = C.BUILDINGS.get(id)
			names.append(bd.name if bd != null else id)
		reqLine = "  ·  Needs %s more of: %s" % [JS.num(req.need - req.have), ", ".join(names)]
	ui.text(costLine + reqLine, W / 2, y0 - 18, {
		"align": "center", "size": 13, "color": PAL.uiBad if not afford or not reqOk else "#b8ad92",
	})

	var cmdr = C.COMMANDERS.get(p.commander)
	for i in oaths.size():
		var o: Dictionary = oaths[i]
		var cx := x0 + i * (cardW + gap)
		var favoured = cmdr if cmdr != null and cmdr.get("oath") == o.id else null
		var rarity := 0
		if o.get("unit") != null:
			rarity = int(p.loadout.get(o.unit, 0))
		var why := "Missing a required building" if not reqOk else ("Not enough resources" if not afford else "")
		var chose := card(o, cx, y0, cardW, cardH, time, favoured.name if favoured != null else null, rarity, afford and reqOk, why)
		if chose:
			swear.call(b, "age:%s" % o.id)
			close()

	if ui.button("Not yet", W / 2 - 80, y0 + cardH + 18, 160, 38, {"size": 14}):
		close()
	ui.text("Esc to close", W / 2, y0 + cardH + 70, {"align": "center", "size": 11.5, "color": "#8f866f"})


## One Oath's card. True when it was sworn this frame (TS: calls onSwear).
func card(o: Dictionary, x: float, y: float, w: float, h: float, time: float,
		favouredBy, rarity: int, enabled: bool, why: String) -> bool:
	var ui := UI.ui
	var ctx := ui.ctx
	ui.panel(x, y, w, h, {"light": true})
	# Your commander's own Oath: a gold frame and the stronger numbers.
	if favouredBy != null:
		ctx.strokeStyle = "#ffd86a"
		ctx.lineWidth = 2.5
		ctx.beginPath(); ctx.roundRect(x + 1.5, y + 1.5, w - 3, h - 3, 6); ctx.stroke()
		ctx.font = "bold 11px \"Trebuchet MS\", sans-serif"
		var label := "★ %s's Oath · +%s%%" % [favouredBy, JS.num(JS.round((C.AFFINITY - 1) * 100))]
		var lw := ctx.measureText(label).width + 16
		ctx.fillStyle = "#ffd86a"
		ctx.beginPath(); ctx.roundRect(x + w - lw - 12, y + 16, lw, 20, 4); ctx.fill()
		ui.text(label, x + w - lw / 2 - 12, y + 26.5, {"align": "center", "size": 11, "bold": true, "color": "#2a1f08"})
	# The Oath's colour across the top, so the three read apart at a glance.
	ctx.fillStyle = o.color
	ctx.fillRect(x + 3, y + 3, w - 6, 6)
	ui.text((KIND_LABEL[o.kind] as String).to_upper(), x + 18, y + 26, {"size": 11.5, "bold": true, "color": Palette.shade(o.color, 0.2)})
	ui.text(o.name, x + 18, y + 50, {"size": 19, "bold": true, "color": "#ffe9b0", "font": "Georgia, serif"})
	ui.text("“%s”" % o.motto, x + 18, y + 74, {"size": 13, "color": "#c9bea3", "font": "Georgia, serif"})

	# What it does, a line each, wrapped to the card.
	var ly := y + 106
	ctx.font = "13.5px \"Trebuchet MS\", sans-serif"
	for line in Oaths.lines(o.id, C.AFFINITY if favouredBy != null else 1.0):
		var rows := wrap_(ctx, line, w - 52)
		ctx.fillStyle = o.color
		ctx.beginPath(); ctx.arc(x + 24, ly, 3.2, 0, PI * 2); ctx.fill()
		for r in rows:
			ui.text(r, x + 36, ly, {"size": 13.5, "color": "#efe6cf"})
			ly += 19
		ly += 7

	# The signature unit, drawn on the card — you should know what you are
	# swearing for before you meet it on the field.
	var unitTop := y + h - 150
	var unitId = o.get("unit")
	if unitId != null and C.UNITS.has(unitId) and unitTop > ly:
		var def: Dictionary = C.UNITS[unitId]
		ctx.fillStyle = Palette.withAlpha(o.color, 0.1)
		ctx.beginPath(); ctx.roundRect(x + 14, unitTop, w - 28, 88, 8); ctx.fill()
		if portrait == null:
			portrait = World.makeEntity()
		var e := portrait
		e.kind = T.Kind.Unit
		e.type = def.id
		e.team = T.Team.Player
		e.radius = def.radius
		e.facing = -0.35
		e.hp = def.hp
		e.maxHp = def.hp
		e.animPhase = time
		e.vx = 0
		e.vy = 0
		e.selected = false
		e.veterancy = 0
		e.variantRarity = 0
		e.abilityActive = 0
		e.rallyTimer = 0
		e.slowTimer = 0
		e.hitFlash = 0
		e.chargeRun = 0
		e.attackCooldown = 0
		e.attackInterval = def.attackInterval
		var scale := 2.1 if float(def.radius) > 12 else 2.8
		ctx.save()
		# Kept to its own box: a lance or a halberd would otherwise reach
		# across the unit's name.
		ctx.beginPath(); ctx.rect(x + 14, unitTop, 96, 88); ctx.clip()
		ctx.translate(x + 62, unitTop + 52)
		ctx.scale(scale, scale)
		e.x = 0
		e.y = 0
		Draw.drawUnit(ctx, e, time, 0)
		ctx.restore()
		ui.text(def.name, x + 118, unitTop + 28, {"size": 14.5, "bold": true, "color": "#ffe9b0"})
		var tb = C.BUILDINGS.get(def.trainedAt)
		ui.text("Trained at the %s" % (tb.name if tb != null else def.trainedAt), x + 118, unitTop + 48, {"size": 12, "color": "#b8ad92"})
		# The variant you own, from your chests — the collection feeds the choice.
		var rt := Draw.rarityByIndex(rarity)
		ui.text("Yours: %s" % rt.name if rarity > 0 else fmtCost(def.cost), x + 118, unitTop + 66, {
			"size": 12, "bold": rarity > 0, "color": rt.color if rarity > 0 else "#b8ad92",
		})
	elif o.get("signature") != null and unitTop > ly:
		# No unit: the signature rule gets the same box, with an emblem.
		ctx.fillStyle = Palette.withAlpha(o.color, 0.1)
		ctx.beginPath(); ctx.roundRect(x + 14, unitTop, w - 28, 88, 8); ctx.fill()
		emblem(ctx, o.id, x + 62, unitTop + 44, o.color)
		ui.text("SIGNATURE", x + 118, unitTop + 22, {"size": 10.5, "bold": true, "color": Palette.shade(o.color, 0.15)})
		ui.text(o.signature.title, x + 118, unitTop + 42, {"size": 14.5, "bold": true, "color": "#ffe9b0"})
		var rows := wrap_(ctx, o.signature.text, w - 150)
		for k in mini(2, rows.size()):
			ui.text(rows[k], x + 118, unitTop + 62 + k * 16, {"size": 12, "color": "#b8ad92"})

	var clicked := ui.button("Swear this Oath" if enabled else (why if why != "" else "Unavailable"), x + 16, y + h - 54, w - 32, 40, {
		"accent": enabled, "disabled": not enabled, "size": 15,
	})
	return clicked and enabled


## A small drawn emblem for an Oath whose signature is a rule, not a unit.
static func emblem(ctx: Ctx, id: String, cx: float, cy: float, color) -> void:
	ctx.save()
	ctx.translate(cx, cy)
	ctx.lineCap = "round"
	ctx.lineJoin = "round"
	if id == "plough":
		# A sheaf: three stalks bound, grain at the tips.
		ctx.strokeStyle = "#c9a24a"
		ctx.lineWidth = 3
		for a in [-0.35, 0.0, 0.35]:
			ctx.beginPath(); ctx.moveTo(0, 26); ctx.lineTo(sin(a) * 30, -20); ctx.stroke()
			ctx.fillStyle = color
			for k in 4:
				ctx.beginPath()
				ctx.ellipse(sin(a) * (30 - k * 6), -20 + k * 7, 3.2, 5, a, 0, PI * 2)
				ctx.fill()
		ctx.strokeStyle = "#7a4f22"
		ctx.lineWidth = 4
		ctx.beginPath(); ctx.moveTo(-9, 10); ctx.lineTo(9, 10); ctx.stroke()
	elif id == "hearth":
		# A keep with a mending glow.
		ctx.fillStyle = Palette.withAlpha(color, 0.25)
		ctx.beginPath(); ctx.arc(0, 2, 30, 0, PI * 2); ctx.fill()
		ctx.fillStyle = "#8a8f98"
		ctx.fillRect(-18, -12, 36, 34)
		for k in 4:
			ctx.fillRect(-18 + k * 10, -20, 6, 9)
		ctx.fillStyle = "#3a2a1c"
		ctx.beginPath(); ctx.roundRect(-6, 6, 12, 16, [6, 6, 0, 0]); ctx.fill()
		ctx.strokeStyle = "#e8f0ff"
		ctx.lineWidth = 3
		ctx.beginPath(); ctx.moveTo(18, -26); ctx.lineTo(18, -14); ctx.moveTo(12, -20); ctx.lineTo(24, -20); ctx.stroke()
	else:
		# A stack of gold coins.
		for k in range(3, -1, -1):
			ctx.fillStyle = "#c9a232" if k % 2 else color
			ctx.beginPath(); ctx.ellipse(-4 + (k % 2) * 3, 16 - k * 9, 22, 8, 0, 0, PI * 2); ctx.fill()
			ctx.strokeStyle = "#7a5a14"
			ctx.lineWidth = 1.2
			ctx.stroke()
		ctx.fillStyle = "#7a5a14"
		ctx.font = "bold 12px Georgia, serif"
		ctx.textAlign = "center"
		ctx.textBaseline = "middle"
		ctx.fillText("✦", -1, -11)
	ctx.restore()


static func fmtCost(c: Dictionary) -> String:
	var parts: Array = []
	if float(c.get("food", 0)) != 0:
		parts.append("%s food" % JS.num(c.food))
	if float(c.get("wood", 0)) != 0:
		parts.append("%s wood" % JS.num(c.wood))
	if float(c.get("gold", 0)) != 0:
		parts.append("%s gold" % JS.num(c.gold))
	return ", ".join(parts) if not parts.is_empty() else "free"


static func wrap_(ctx: Ctx, text: String, maxW: float) -> Array:
	var words := text.split(" ")
	var rows: Array = []
	var cur := ""
	for w in words:
		var t := "%s %s" % [cur, w] if cur != "" else w
		if ctx.measureText(t).width > maxW and cur != "":
			rows.append(cur)
			cur = w
		else:
			cur = t
	if cur != "":
		rows.append(cur)
	return rows
