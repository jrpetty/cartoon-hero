## ui/faction_book.ts — the Factions book: an encyclopedia of the six peoples,
## and where they are unlocked.
##
## Two ways in. From the main menu (or a locked card on the Skirmish screen) it
## is a place to read about each faction and buy one for FACTION_PRICE renown.
## On a player's very first launch it is the choice itself: one faction is
## theirs free, and they cannot leave until they have picked it — so the page
## has to tell them enough to pick well, not just list numbers.
class_name FactionBook
extends RefCounted

const DIFFICULTY := ["", "Forgiving", "Needs a plan", "Punishing"]
const CURVE := {"early": "Strongest early", "mid": "Strongest mid-game", "late": "Strongest late", "steady": "Steady throughout"}


## Word-wrap `text` into `maxW`, drawing it; returns the height used.
static func wrap_(text: String, x: float, y: float, maxW: float, size: float, color, bold: bool = false) -> float:
	var ui := UI.ui
	var ctx := ui.ctx
	ctx.font = "%s%spx 'Trebuchet MS', sans-serif" % ["bold " if bold else "", JS.num(size)]
	var line := ""
	var yy := y
	for w in text.split(" "):
		var test := line + " " + w if line != "" else w
		if ctx.measureText(test).width > maxW and line != "":
			ui.text(line, x, yy, {"size": size, "color": color, "bold": bold})
			line = w
			yy += size + 5
		else:
			line = test
	if line != "":
		ui.text(line, x, yy, {"size": size, "color": color, "bold": bold})
	return yy - y + size + 5


var selected: String = C.FACTION_IDS[0]
var scroll := 0.0
var contentH := 0.0
var flash := ""
var flashT := 0.0


## Open on a particular faction (e.g. a locked card that was clicked).
func focus(id) -> void:
	if id != null and C.FACTIONS.has(id):
		selected = id
	scroll = 0


## Returns "back" or null.
func draw(W: float, H: float, time: float, profile: Profile, dt: float = 1.0 / 60.0) -> Variant:
	var ui := UI.ui
	var ctx := ui.ctx
	var first := profile.needsFirstFaction
	flashT = maxf(0, flashT - dt)
	var PRICE := Profile.FACTION_PRICE

	# Backdrop: dark parchment with a warm light from above.
	var bg := ctx.createLinearGradient(0, 0, 0, H)
	bg.addColorStop(0, "#241b10")
	bg.addColorStop(1, "#110c06")
	ctx.fillStyle = bg
	ctx.fillRect(0, 0, W, H)
	var glow := ctx.createRadialGradient(W / 2, -H * 0.2, 10, W / 2, -H * 0.2, H * 1.1)
	glow.addColorStop(0, "rgba(255,200,120,0.14)")
	glow.addColorStop(1, "rgba(255,200,120,0)")
	ctx.fillStyle = glow
	ctx.fillRect(0, 0, W, H)

	var outer := minf(W - 48, 1320)
	var x0 := JS.round(W / 2 - outer / 2)
	var narrow := W < 1000
	var listW := outer if narrow else 300.0

	# Header.
	ui.text("Choose your first faction" if first else "The Factions", x0, 50, {"size": 32, "bold": true, "color": "#ffe9b0", "font": "Georgia, serif"})
	ui.text(("One is yours, free. The others cost %s ✦ renown each — earned in battle, the same renown that opens War Chests." % JS.num(PRICE)) if first
		else ("Six peoples from six eras. Read how each plays, then unlock the ones that suit you for %s ✦ each." % JS.num(PRICE)),
		x0 + 2, 76, {"size": 13, "color": "#c9bea3"})
	ui.text("%s ✦" % JS.num(profile.data.renown), x0 + outer, 50, {"align": "right", "size": 22, "bold": true, "color": "#e8c060"})
	ui.text("renown · %d of %d owned" % [profile.ownedFactions().size(), C.FACTION_IDS.size()], x0 + outer, 70, {"align": "right", "size": 11.5, "color": "#a89f88"})

	var top := 96.0
	var FOOT := 72.0

	# ---- the six, as a list (or a row of chips when narrow) ----
	if narrow:
		var cw := (outer - 5 * 6) / 6
		for i in C.FACTION_IDS.size():
			var id: String = C.FACTION_IDS[i]
			var f: Dictionary = C.FACTIONS[id]
			if ui.button(_strip_the(f.name), x0 + i * (cw + 6), top, cw, 34, {"accent": selected == id, "size": 11.5}):
				pick(id)
	else:
		for i in C.FACTION_IDS.size():
			listCard(C.FACTIONS[C.FACTION_IDS[i]], x0, top + i * 86, listW, 78, profile, first, time)

	# ---- the selected faction's page, scrollable ----
	var px := x0 if narrow else x0 + listW + 20
	var pw := outer if narrow else outer - listW - 20
	var py := top + 44 if narrow else top
	var viewH := H - py - FOOT
	var maxScroll := maxf(0, contentH - viewH)
	if ui.wheel and ui.mx > px and ui.my > py and ui.my < py + viewH:
		scroll = maxf(0, minf(maxScroll, scroll + ui.wheel * 0.6))
	scroll = minf(scroll, maxScroll)
	ui.pushScroll(scroll, {"x": px - 4, "y": py, "w": pw + 8, "h": viewH})
	var end := page(C.FACTIONS[selected], px, py, pw, profile, time)
	contentH = end - py + 12
	ui.popScroll()
	ui.scrollbar(px + pw + 6, py, viewH, scroll, contentH)

	# ---- action bar ----
	var fy := H - FOOT
	ctx.fillStyle = "rgba(10,7,3,0.92)"
	ctx.fillRect(0, fy, W, FOOT)
	ctx.fillStyle = Palette.withAlpha(C.PAL.uiAccent, 0.3)
	ctx.fillRect(0, fy, W, 1)
	var by := fy + 14
	var action = null
	if not first and ui.button("⟵  Back", x0, by, 130, 44, {"size": 15}):
		action = "back"
	var f: Dictionary = C.FACTIONS[selected]
	var owned := profile.ownsFaction(f.id)
	var bw := 300.0
	var bx := x0 + outer - bw
	if first:
		if ui.button("Take %s — free" % f.name, bx, by, bw, 44, {"accent": true, "size": 16}):
			profile.chooseFirstFaction(f.id)
			audio.play("levelup")
			action = "back"
		ui.text("You can unlock the others later with renown.", bx - 16, by + 27, {"align": "right", "size": 12, "color": "#a89f88"})
	elif owned:
		var playing: bool = profile.playableFaction() == f.id
		if ui.button(("✓ Playing as %s" % f.name) if playing else ("Play as %s" % f.name), bx, by, bw, 44, {"accent": not playing, "size": 15, "disabled": playing}):
			profile.selectFaction(f.id)
			say("You'll play as %s." % f.name)
			audio.play("ui")
	else:
		var short: float = PRICE - float(profile.data.renown)
		if ui.button("🔓  Unlock for %s ✦" % JS.num(PRICE), bx, by, bw, 44, {"accent": short <= 0, "size": 16, "disabled": short > 0,
			"tooltip": ["Not enough renown yet", "You need %s more ✦. Every battle earns renown — wins and harder AIs earn more." % JS.num(short)] if short > 0 else ["Unlock %s" % f.name, "Spend %s ✦. It's yours for good — Skirmish and online." % JS.num(PRICE)]}):
			if profile.unlockFaction(f.id):
				profile.selectFaction(f.id)
				say("%s unlocked!" % f.name)
				audio.play("levelup")
		if short > 0:
			ui.text("%s ✦ more to unlock" % JS.num(short), bx - 16, by + 27, {"align": "right", "size": 12.5, "color": "#e0a070"})
	if flashT > 0:
		ui.text(flash, W / 2, by + 27, {"align": "center", "size": 14, "bold": true, "color": "#9fd08a"})
	return action


func say(msg: String) -> void:
	flash = msg
	flashT = 3


func pick(id: String) -> void:
	if selected != id:
		selected = id
		scroll = 0
		audio.play("ui")


static func _strip_the(s: String) -> String:
	return s.substr(4) if s.begins_with("The ") else s


func listCard(f: Dictionary, x: float, y: float, w: float, h: float, profile: Profile, first: bool, time: float) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var sel: bool = selected == f.id
	if ui.button("", x, y, w, h, {"accent": sel, "tooltip": [f.name, f.era, f.tagline]}):
		pick(f.id)
	ctx.fillStyle = f.color
	ctx.fillRect(x + 1, y + 8, 4, h - 16)
	soldier(f, x + 36, y + h * 0.62, 1.7, time)
	ui.text(f.name, x + 70, y + 26, {"size": 16, "bold": true, "color": "#fff0cc" if sel else f.color, "font": "Georgia, serif"})
	ui.text(f.era, x + 70, y + 44, {"size": 11.5, "color": "#c9bea3"})
	ui.text(CURVE[f.curve], x + 70, y + 62, {"size": 11, "color": "#8f8770"})
	var owned := profile.ownsFaction(f.id)
	var tag := "FREE" if first else ("OWNED ✓" if owned else "🔒 %s ✦" % JS.num(Profile.FACTION_PRICE))
	ui.text(tag, x + w - 12, y + 24, {"align": "right", "size": 10.5, "bold": true, "color": "#9fd08a" if first else ("#9fd08a" if owned else "#e8c060")})
	if not owned and not first:
		ctx.fillStyle = "rgba(0,0,0,0.18)"
		ctx.fillRect(x + 6, y + 2, w - 8, h - 4)


## One faction's page. Returns the y it ends at.
func page(f: Dictionary, x: float, y: float, w: float, profile: Profile, time: float) -> float:
	var ui := UI.ui
	var ctx := ui.ctx
	var g: Dictionary = f.guide
	var PAL: Dictionary = C.PAL

	# Hero panel: name and story on the left, a diorama of its town and army on the right.
	var heroH := 190.0
	ui.panel(x, y, w, heroH)
	var dioW := minf(360, w * 0.42)
	var dx := x + w - dioW - 10
	var dg := ctx.createLinearGradient(0, y + 10, 0, y + heroH - 10)
	dg.addColorStop(0, Palette.shade(PAL.grass, 0.08))
	dg.addColorStop(1, Palette.shade(PAL.grassDark, -0.1))
	ctx.fillStyle = dg
	ctx.beginPath()
	ctx.roundRect(dx, y + 10, dioW, heroH - 20, 8)
	ctx.fill()
	ctx.save()
	ctx.beginPath()
	ctx.roundRect(dx, y + 10, dioW, heroH - 20, 8)
	ctx.clip()
	townCentre(f, dx + dioW * 0.34, y + heroH * 0.62, time)
	soldier(f, dx + dioW * 0.72, y + heroH * 0.74, 2.4, time)
	var own: Array = (f.replaces as Dictionary).values() + (f.extra as Array)
	if own.size() > 0 and own[0]:
		unit(f, own[0], dx + dioW * 0.88, y + heroH * 0.66, 2.1, time)
	ctx.restore()
	var tw := w - dioW - 44
	ui.text(f.name, x + 22, y + 44, {"size": 32, "bold": true, "color": f.color, "font": "Georgia, serif"})
	ui.text(f.era, x + 24, y + 68, {"size": 14, "color": "#d8cdb4"})
	var ty := y + 96
	ty += wrap_("“%s”" % f.tagline, x + 24, ty, tw, 14, "#e9dcc0")
	# Difficulty and power curve.
	var my := y + heroH - 44
	var difficulty := int(g.difficulty)
	ui.text("DIFFICULTY", x + 24, my, {"size": 10, "bold": true, "color": "#a89f88"})
	for i in 3:
		ctx.fillStyle = "#e8c060" if i < difficulty else "rgba(255,255,255,0.12)"
		ctx.beginPath(); ctx.arc(x + 30 + i * 16, my + 16, 5.5, 0, PI * 2); ctx.fill()
	ui.text(DIFFICULTY[difficulty], x + 80, my + 20, {"size": 12.5, "bold": true, "color": "#e9dcc0"})
	var cx := x + 200
	ui.text("POWER THROUGH THE MATCH", cx, my, {"size": 10, "bold": true, "color": "#a89f88"})
	var labs := ["Early", "Mid", "Late"]
	for i in labs.size():
		var bx := cx + i * 62
		for k in 5:
			ctx.fillStyle = f.color if k < float(g.power[i]) else "rgba(255,255,255,0.1)"
			ctx.fillRect(bx + k * 9, my + 10, 7, 12)
		ui.text(labs[i], bx, my + 36, {"size": 10.5, "color": "#bdb49a"})
	y += heroH + 14

	# How it plays.
	var yb := [y]  # boxed: `section` moves it
	var section := func(title: String) -> void:
		ui.text(title.to_upper(), x + 4, yb[0] + 14, {"size": 11, "bold": true, "color": PAL.uiAccent})
		ctx.fillStyle = Palette.withAlpha(PAL.uiAccent, 0.25)
		ctx.fillRect(x + 4, yb[0] + 22, w - 8, 1)
		yb[0] += 34
	section.call("How it plays")
	yb[0] += wrap_(g.playstyle, x + 4, yb[0], w - 8, 14, "#e2d6ba") + 8

	# Strengths and price side by side; then who it suits and how to win.
	var colW := (w - 20) / 2
	var list := func(items: Array, lx: float, ly: float, mark: String, color) -> float:
		var yy := ly
		for it in items:
			ui.text(mark, lx, yy, {"size": 13, "bold": true, "color": color})
			yy += wrap_(it, lx + 20, yy, colW - 26, 13, "#e2d6ba") + 2
		return yy
	section.call("Bonuses and their price")
	var a: float = list.call(f.strengths, x + 4, yb[0], "✓", "#8fd07a")
	var b: float = list.call(f.weaknesses, x + 4 + colW + 20, yb[0], "✗", "#e0786a")
	yb[0] = maxf(a, b) + 8
	section.call("Pick it if you…")
	var suits: Array = g.suits
	var halfN := ceili(suits.size() / 2.0)
	var c: float = list.call(suits.slice(0, halfN), x + 4, yb[0], "•", f.color)
	var d: float = list.call(suits.slice(halfN), x + 4 + colW + 20, yb[0], "•", f.color)
	yb[0] = maxf(c, d) + 8

	# Its own units, drawn, with their numbers.
	section.call("Its own soldiers")
	var units: Array = []
	for base in f.replaces:
		var bdef = C.UNITS.get(base)
		units.append({"id": f.replaces[base], "note": "Replaces the %s" % (bdef.name if bdef != null else base)})
	for u in f.extra:
		var at = C.BUILDINGS.get(C.UNITS[u].trainedAt)
		units.append({"id": u, "note": "Extra, at the %s" % (at.name if at != null else "Barracks")})
	var uw := minf(300, (w - 12 * (units.size() - 1)) / maxf(1, units.size()))
	for i in units.size():
		var u: Dictionary = units[i]
		var def = C.UNITS.get(u.id)
		if def == null:
			continue
		var ux := x + i * (uw + 12)
		var uy: float = yb[0]
		ui.panel(ux, uy, uw, 150)
		unit(f, u.id, ux + 44, uy + 92, 2.6, time)
		ui.text(def.name, ux + 88, uy + 26, {"size": 15, "bold": true, "color": "#ffe9b0"})
		ui.text(u.note, ux + 88, uy + 44, {"size": 11, "color": "#a89f88"})
		var age := int(def.get("age", 0))
		ui.text(C.AGES[age].name if age < C.AGES.size() else "", ux + 88, uy + 60, {"size": 11, "color": "#a89f88"})
		var costParts: Array = []
		for k in def.cost:
			if def.cost[k]:
				costParts.append("%s%s" % [JS.num(def.cost[k]), String(k).substr(0, 1)])
		var cost := " ".join(PackedStringArray(costParts))
		var stat := func(label: String, v: String, sy: float) -> void:
			ui.text(label, ux + 88, sy, {"size": 12, "color": "#bdb49a"})
			ui.text(v, ux + uw - 12, sy, {"align": "right", "size": 12, "bold": true, "color": "#e9dcc0"})
		stat.call("HP", JS.num(def.hp), uy + 82)
		stat.call("Attack", JS.num(def.attack), uy + 99)
		stat.call("Range", JS.num(def.range) if def.get("ranged") else "melee", uy + 116)
		stat.call("Cost", cost, uy + 133)
	yb[0] += 150 + 12

	section.call("How to win with it")
	for t in g.tips:
		ui.text("›", x + 4, yb[0], {"size": 14, "bold": true, "color": "#e8c060"})
		yb[0] += wrap_(t, x + 22, yb[0], w - 30, 13.5, "#e2d6ba") + 3
	yb[0] += 6
	section.call("What gives it trouble")
	yb[0] += wrap_(g.struggles, x + 4, yb[0], w - 8, 13.5, "#e0b8a8") + 6
	return yb[0]


func withFaction(f: Dictionary, fn: Callable) -> void:
	var id: String = f.id
	Draw.setFactionResolver(func(_t): return id)
	fn.call()  # a picture is never worth a crash
	Draw.setFactionResolver(null)


func scaled(x: float, y: float, s: float, fn: Callable) -> void:
	var ctx := UI.ui.ctx
	ctx.save()
	ctx.translate(x, y)
	ctx.scale(s, s)
	ctx.translate(-x, -y)
	fn.call()
	ctx.restore()


func unit(f: Dictionary, id: String, x: float, y: float, s: float, time: float) -> void:
	var def = C.UNITS.get(id)
	if def == null:
		return
	var e := World.makeEntity()
	e.kind = T.Kind.Unit; e.type = id; e.team = T.Team.Player; e.x = x; e.y = y; e.radius = def.radius
	e.hp = def.hp; e.maxHp = def.hp; e.facing = -PI / 6; e.attackInterval = def.attackInterval; e.animPhase = time
	withFaction(f, func(): scaled(x, y, s, func(): Draw.drawUnit(UI.ui.ctx, e, time)))


## Its line infantryman: the faction's own if it replaces the Man-at-Arms.
func soldier(f: Dictionary, x: float, y: float, s: float, time: float) -> void:
	var id = f.replaces.get("militia")
	if id == null:
		id = "militia"
		for u in f.extra:
			var ud = C.UNITS.get(u)
			if ud != null and ud.trainedAt == "barracks":
				id = u
				break
	unit(f, id, x, y, s, time)


func townCentre(f: Dictionary, x: float, y: float, time: float) -> void:
	var def: Dictionary = C.BUILDINGS.town_center
	var e := World.makeEntity()
	e.kind = T.Kind.Building; e.type = "town_center"; e.team = T.Team.Player; e.x = x; e.y = y
	e.radius = (def.tiles * C.TILE) / 2; e.hp = def.hp; e.maxHp = def.hp
	e.buildState = T.BuildState.Done; e.buildProgress = 1
	withFaction(f, func(): scaled(x, y, 1.15, func(): Draw.drawBuilding(UI.ui.ctx, e, time, T.Team.Player)))
