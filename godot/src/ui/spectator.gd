## ui/spectator.ts — spectator overlay: a detailed live card per realm (army,
## villagers, pop, resources, gathered, K/L, bases, and a military-strength
## bar). Drawing is pure so it can be screenshot-tested; the App layers
## click-to-follow on top of the card rects this returns.
class_name Spectator


## Absolute per-realm colour (matches the in-world spectator colouring).
static func teamColor(t: int) -> String:
	var teams: Array = C.PAL.teams
	return teams[t % teams.size()].main


static func teamLabel(t: int) -> String:
	var teams: Array = C.PAL.teams
	if t >= 0 and t < teams.size():
		return teams[t].name
	return "Realm %d" % (t + 1)


## Draws the spectator overlay and returns the card rectangles so the caller
## can hit-test them for click-to-follow: Array of SpectatorCardRect
## {team, x, y, w, h, focus (a building Entity to focus the camera on, or null)}.
static func drawSpectatorPanels(W: float, H: float, world: World, gameSpeed: float, paused: bool) -> Array:
	var ui := UI.ui
	var ctx := ui.ctx
	var PAL: Dictionary = C.PAL

	# Aggregate live counts + a rough military value per realm.
	var a: Array = []
	for t in world.numTeams:
		a.append({"army": 0, "vills": 0, "bld": 0, "milValue": 0.0, "firstBld": null})
	for e: Entity in world.entities:
		if not e.alive or e.team >= world.numTeams:
			continue
		var g: Dictionary = a[e.team]
		if e.kind == T.Kind.Building:
			g.bld += 1
			if g.firstBld == null:
				g.firstBld = e
		elif e.kind == T.Kind.Unit:
			if e.type == "villager":
				g.vills += 1
			else:
				g.army += 1
				var u = C.UNITS.get(e.type)
				if u != null:
					var c: Dictionary = u.cost
					g.milValue += float(c.get("food", 0)) + float(c.get("wood", 0)) + float(c.get("gold", 0))
	var maxMil := 1.0
	for g in a:
		maxMil = maxf(maxMil, g.milValue)

	# Top-bar spectator label (the player resource block is hidden in watch mode).
	ui.text("👁 SPECTATING", 14, 17, {"size": 14, "bold": true, "color": PAL.uiAccent})
	ui.text("❚❚ Paused" if paused else "▶ %s× speed" % JS.num(gameSpeed), 150, 17, {
		"size": 13, "color": PAL.uiBad if paused else "#d8cdaf",
	})
	ui.text("Space pause · +/- speed · click a realm to follow · Esc to leave", 300, 17, {
		"size": 12, "color": "#9c9379",
	})

	# Up to 4 realms stack in one right-hand column; 5–8 split into two columns.
	var cardW := 250.0
	var cardH := 116.0
	var gap := 8.0
	var cols := 2 if world.numTeams > 4 else 1
	var perCol := int(ceil(float(world.numTeams) / cols))
	var rects: Array = []

	for t in world.numTeams:
		var colIdx := int(floor(float(t) / perCol)) # 0 = rightmost column
		var rowIdx := t % perCol
		var cx := W - cardW - 12 - colIdx * (cardW + 12)
		var cy := 44 + rowIdx * (cardH + gap)
		var p := world.player(t)
		var g: Dictionary = a[t]
		var dead := p.defeated
		var col := teamColor(t)
		var dim := "#7a7060" if dead else "#d8cdaf"

		ui.panel(cx, cy, cardW, cardH, {"light": true})

		# Header: colour swatch + name + age.
		ctx.fillStyle = "#555049" if dead else col
		ctx.fillRect(cx + 12, cy + 10, 12, 12)
		ui.text(teamLabel(t) + ("  ☠" if dead else ""), cx + 32, cy + 17, {
			"size": 14, "bold": true, "color": "#8a8070" if dead else col,
		})
		ui.text("DEFEATED" if dead else Tech.ageShort(p.age), cx + cardW - 12, cy + 17, {
			"align": "right", "size": 12, "bold": true, "color": PAL.uiBad if dead else PAL.uiAccent,
		})
		var cd = C.COMMANDERS.get(p.commander)
		var cmdr = cd.name if cd != null else null
		var fac: String = Factions.factionOf(p.faction).name.trim_prefix("The ")
		ui.text("%s · %s" % [fac, cmdr] if cmdr != null else fac, cx + 32, cy + 33, {"size": 11, "color": "#9c9379"})
		# Sworn Oaths, right-aligned under the age, in their colours.
		var ox := cx + cardW - 12
		var chips := Oaths.oathChips(p.oaths)
		chips.reverse()
		for chip in chips:
			ui.text(chip.label, ox, cy + 33, {"align": "right", "size": 11, "bold": true, "color": chip.color})
			ctx.font = "bold 11px \"Trebuchet MS\", sans-serif"
			ox -= ctx.measureText(chip.label).width + 7

		# Population row.
		ui.text("⚔ %d" % g.army, cx + 14, cy + 52, {"size": 13, "color": dim, "bold": true})
		ui.text("👤 %d" % g.vills, cx + 90, cy + 52, {"size": 13, "color": dim})
		ui.text("Pop %s/%s" % [JS.num(p.popUsed), JS.num(p.popCap)], cx + 160, cy + 52, {"size": 12, "color": dim})

		# Resource stockpile.
		ui.text("🍖%d" % floori(p.resources.food), cx + 14, cy + 70, {"size": 12, "color": "#e89a5a"})
		ui.text("🪵%d" % floori(p.resources.wood), cx + 100, cy + 70, {"size": 12, "color": "#b08a52"})
		ui.text("🪙%d" % floori(p.resources.gold), cx + 186, cy + 70, {"size": 12, "color": PAL.goldVein})

		# Economy + combat record.
		ui.text("⛏ %d gathered" % floori(p.stats.gathered), cx + 14, cy + 87, {"size": 11, "color": "#9c9379"})
		ui.text("K %s / L %s   🏠%d" % [JS.num(p.stats.unitsKilled), JS.num(p.stats.unitsLost), g.bld], cx + cardW - 14, cy + 87, {
			"align": "right", "size": 11, "color": "#9c9379",
		})

		# Military-strength bar (relative to the strongest realm).
		var barW := cardW - 28
		var frac: float = g.milValue / maxMil
		ctx.fillStyle = "rgba(0,0,0,0.35)"
		ctx.fillRect(cx + 14, cy + 98, barW, 8)
		ctx.fillStyle = "#555049" if dead else col
		ctx.fillRect(cx + 14, cy + 98, barW * frac, 8)

		rects.append({"team": t, "x": cx, "y": cy, "w": cardW, "h": cardH, "focus": g.firstBld})
	return rects
