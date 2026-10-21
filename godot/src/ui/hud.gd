## ui/hud.ts — in-match HUD: top resource bar, minimap, selection panel,
## command card, alerts. Talks back to the match controller through the
## MatchController interface — the HUD never mutates the world directly.
##
## MatchController is duck-typed: any object with the TS interface's methods
## (trainUnit, research, startPlacement, ungarrison, toggleGate, setAutoReseed,
## trade, stopSelection, holdSelection, setStance, setAttackMoveMode,
## garrisonSelection, useAbility, minimapNavigate, minimapCommand, minimapPing,
## openMenu, cancelProduction, jumpTo, narrowSelection). main.gd's
## `Controller` is the real one. An Alert is {text, time, x, y} (x/y null when
## it happened nowhere in particular).
##
## Godot port: the minimap's per-entity dots and per-cell fog are retained
## canvas items spliced into the paint order (LayerCtx.external) — the dots
## redrawn a few times a second, the fog shaded on the GPU from the raw fog
## bytes — rather than a fillRect per entity and per cell every frame, which
## GDScript can't afford. With a plain Ctx (tests) they draw as the TS does.
class_name HUD
extends RefCounted


const BUILD_CATEGORIES := [
	{
		"id": "economy",
		"label": "Economy",
		"hint": "Houses, drop-off camps, farms, bridges, market and expansion Town Centers.",
		"buildings": ["house", "mill", "lumber_camp", "mining_camp", "farm", "bridge", "market", "town_center"],
	},
	{
		"id": "military",
		"label": "Military",
		"hint": "Production and upgrade buildings for your army.",
		"buildings": ["barracks", "archery_range", "stable", "siege_workshop", "blacksmith"],
	},
	{
		"id": "defense",
		"label": "Defense",
		"hint": "Walls, gates, towers, watchfires and the mighty Castle.",
		"buildings": ["palisade", "stone_wall", "gate", "watchfire", "watch_tower", "castle"],
	},
]

const MINIMAP_SIZE := 196
const CARD_W := 4
const CARD_H := 3
const BTN := 56
const GAP := 6
const PING_LIFE := 2.4 ## seconds a minimap ping lingers
## How often (seconds) the minimap's dots are redrawn.
const MINIMAP_DOTS_EVERY := 0.1

var minimapBase: GpuLayer = null
var alerts: Array = []
var pings: Array = []
var buildMenuOpen := false
var buildCategory = null
## Advancing an age opens this, to choose the Oath the realm swears.
var oathPicker := OathPicker.new()
## Dragging across the minimap moves the camera; main skips box-select for it.
var minimapDragging := false
var minimapDragEnded := false
var prevLeftHeld := false
## Shift state from the App, for shift-click on selection chips.
var shiftHeld := false

# Retained minimap layers (Godot).
var dotsItem := RID()
var dotsAge := 99.0
var fogItem := RID()
var fogTex: ImageTexture = null
var fogMat: ShaderMaterial = null
var fogAge := 99.0
var fogTeam := -1


func prepare(map: Dictionary) -> void:
	minimapBase = TerrainArt.buildMinimapBase(map, MINIMAP_SIZE)
	alerts = []
	pings = []
	buildMenuOpen = false
	buildCategory = null
	oathPicker.close()
	dotsAge = 99.0
	fogAge = 99.0
	fogTex = null


func addAlert(text: String, x = null, y = null) -> void:
	# The same message again refreshes the one already showing, rather than
	# stacking copies of it down the screen.
	for a in alerts:
		if a.text == text:
			a.time = 5.0
			a.x = x
			a.y = y
			return
	alerts.push_front({"text": text, "time": 5.0, "x": x, "y": y})
	if alerts.size() > 4:
		alerts.pop_back()


## "⚠ Need 40 more wood" — the reason a button is greyed out, if it's money.
func shortfall(res: Dictionary, cost: Dictionary) -> Array:
	var need: Array = []
	for k in ["food", "wood", "gold"]:
		if res[k] < cost[k]:
			need.append("%s more %s" % [JS.num(ceil(cost[k] - res[k])), k])
	return ["⚠ Need %s" % " and ".join(need)] if not need.is_empty() else []


## Is this screen point over the HUD (top bar, minimap, selection panel, command card)?
func overHud(W: float, H: float, mx: float, my: float) -> bool:
	var cardW := CARD_W * (BTN + GAP) + GAP + 8
	var cardH := CARD_H * (BTN + GAP) + GAP + 8
	if my < 36:
		return true
	if mx < MINIMAP_SIZE + 20 and my > H - MINIMAP_SIZE - 20:
		return true
	if mx > W - cardW - 14 and my > H - cardH - 14:
		return true
	return my > H - 116 and mx > MINIMAP_SIZE + 20 and mx < W - cardW - 14


func addPing(x: float, y: float) -> void:
	pings.append({"x": x, "y": y, "age": 0.0})
	if pings.size() > 8:
		pings.pop_front()


func draw(W: float, H: float, world: World, cam: Camera, team: int, selection: Array, dt: float,
		ctrl, attackMoveArmed: bool, spectating: bool = false, placingType = null) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var PAL: Dictionary = C.PAL
	var p := world.player(team)

	# ---------------------------------------------------------- top bar --
	ui.panel(0, 0, W, 34)
	if not spectating:
		var res: Dictionary = p.resources
		var items := [
			["🍖", "#e89a5a", floori(res.food)],
			["🪵", "#b08a52", floori(res.wood)],
			["🪙", PAL.goldVein, floori(res.gold)],
		]
		var x := 14.0
		for it in items:
			ui.text(it[0], x, 17, {"size": 15})
			ui.text(str(it[2]), x + 22, 17, {"size": 14, "color": it[1], "bold": true})
			x += 92
		var popBlocked := p.popUsed >= p.popCap
		ui.text("Pop %s/%s" % [JS.num(p.popUsed), JS.num(p.popCap)], x, 17, {
			"size": 14,
			"color": PAL.uiBad if popBlocked else PAL.uiParchment,
			"bold": popBlocked,
		})
		x += 110
		var ageName: String = C.AGES[p.age].name
		ui.text(ageName, x, 17, {"size": 14, "color": PAL.uiAccent, "bold": true})
		# The Oaths sworn so far, as coloured chips beside the age: this realm's
		# identity, at a glance.
		ctx.font = "bold 14px \"Trebuchet MS\", sans-serif"
		var ox := x + ctx.measureText(ageName).width + 12
		var chips := Oaths.oathChips(p.oaths)
		ctx.font = "bold 11.5px \"Trebuchet MS\", sans-serif"
		var full := 0.0
		for c in chips:
			full += ctx.measureText(c.label).width + 19
		# The clock sits in the middle of the bar; on a narrow window the chips
		# shrink to lettered badges rather than run into it.
		var roomy := ox + full < W / 2 - 46
		for chip in chips:
			ctx.font = "bold 11.5px \"Trebuchet MS\", sans-serif"
			var label: String = chip.label if roomy else (chip.label as String).substr(0, 1)
			var cw := ctx.measureText(label).width + 14 if roomy else 18.0
			ctx.fillStyle = Palette.withAlpha(chip.color, 0.3)
			ctx.beginPath(); ctx.roundRect(ox, 8, cw, 18, 4 if roomy else 9); ctx.fill()
			ctx.strokeStyle = Palette.withAlpha(chip.color, 0.85)
			ctx.lineWidth = 1
			ctx.stroke()
			ui.text(label, ox + cw / 2, 17.5, {"align": "center", "size": 11.5, "bold": true, "color": "#f4ead2"})
			ox += cw + 5

	var mins := floori(world.time / 60)
	var secs := floori(fmod(world.time, 60))
	ui.text("%d:%02d" % [mins, secs], W / 2, 17, {"align": "center", "size": 14})

	# Day/night dial + label.
	var phase := DayNight.dayPhase(world.time)
	var night := DayNight.isNight(phase)
	var dialX := W / 2 + 52
	if night:
		ctx.fillStyle = "#cdd6ea"
		ctx.beginPath()
		ctx.arc(dialX, 17, 7, 0, PI * 2)
		ctx.fill()
		ctx.fillStyle = PAL.uiPanel
		ctx.beginPath()
		ctx.arc(dialX + 3, 15, 6, 0, PI * 2)
		ctx.fill()
	else:
		var warm := phase < 0.08 or phase >= 0.4
		ctx.fillStyle = "#ffcf6a" if warm else "#ffe9b0"
		ctx.beginPath()
		ctx.arc(dialX, 17, 6, 0, PI * 2)
		ctx.fill()
		ctx.strokeStyle = Palette.withAlpha("#ffd98a", 0.7)
		ctx.lineWidth = 1.5
		for i in 8:
			var a := (i / 8.0) * PI * 2
			ctx.beginPath()
			ctx.moveTo(dialX + cos(a) * 8, 17 + sin(a) * 8)
			ctx.lineTo(dialX + cos(a) * 10.5, 17 + sin(a) * 10.5)
			ctx.stroke()
	ui.text(DayNight.dayLabel(phase), dialX + 16, 17, {
		"size": 12,
		"color": "#9fb0d6" if night else "#e8c98a",
		"bold": night,
	})

	# Casters have the caster bar (and Esc / Leave) instead.
	if not spectating and ui.button("Menu", W - 74, 5, 64, 24, {"size": 13}):
		ctrl.openMenu()

	if attackMoveArmed:
		ui.text("⚔ ATTACK-MOVE: click a target location", W / 2, 52, {
			"align": "center", "size": 15, "color": "#f2a05d", "bold": true,
		})
	# Placement mode is otherwise invisible except for the ghost under the
	# cursor, and walls now *stay* armed between runs — which is only an
	# improvement if you can tell it has happened and how to stop.
	if placingType != null:
		var wall: bool = placingType == "palisade" or placingType == "stone_wall"
		var bname: String = (C.BUILDINGS[placingType].name as String).to_upper()
		ui.text(
			("🧱 %s: drag to lay a run — keep going, right-click when done" if wall else "🔨 %s: click to place — right-click to cancel") % bname,
			W / 2, 72 if attackMoveArmed else 52,
			{"align": "center", "size": 14, "color": "#8fd0ff", "bold": true},
		)

	# ------------------------------------------------------------ alerts --
	var ay := 76.0
	for a in alerts:
		a.time -= dt
		if a.time <= 0:
			continue
		var alpha := minf(1, a.time)
		ctx.globalAlpha = alpha
		# An alert that happened somewhere is a link there: click to look.
		var where: bool = a.x != null and a.y != null
		ctx.font = "bold 15px 'Trebuchet MS', sans-serif"
		var tw := ctx.measureText(a.text).width
		var hov := where and ui.hit(W / 2 - tw / 2 - 8, ay - 15, tw + 16, 21)
		if hov:
			ctx.fillStyle = "rgba(0,0,0,0.35)"
			ctx.beginPath(); ctx.roundRect(W / 2 - tw / 2 - 8, ay - 15, tw + 16, 21, 5); ctx.fill()
			ui.pointerConsumed = true
			if ui.clicked:
				ctrl.jumpTo(a.x, a.y)
				a.time = minf(a.time, 0.6)
		ui.text(a.text + ("  ↗" if hov else ""), W / 2, ay, {"align": "center", "size": 15, "color": "#ffe9b0" if hov else "#f2c45d", "bold": true})
		ctx.globalAlpha = 1
		ay += 22
	alerts = alerts.filter(func(a): return a.time > 0)
	for ping in pings:
		ping.age += dt
	pings = pings.filter(func(ping): return ping.age < PING_LIFE)

	# ----------------------------------------------------------- minimap --
	var mmX := 10.0
	var mmY := H - MINIMAP_SIZE - 10
	ui.panel(mmX - 4, mmY - 4, MINIMAP_SIZE + 8, MINIMAP_SIZE + 8)
	if minimapBase != null:
		ctx.blit(minimapBase.tex, Rect2(0, 0, MINIMAP_SIZE, MINIMAP_SIZE), Rect2(mmX, mmY, MINIMAP_SIZE, MINIMAP_SIZE))

	var sx := MINIMAP_SIZE / world.worldW
	var sy := MINIMAP_SIZE / world.worldH
	var retained := ctx is LayerCtx

	# Fog shading on minimap.
	var fog: PackedByteArray = world.fog[team]
	var cellW := float(MINIMAP_SIZE) / world.fogCols
	var cellH := float(MINIMAP_SIZE) / world.fogRows
	if retained:
		_minimapFog(ctx, fog, world.fogCols, world.fogRows, team, mmX, mmY, cellW, cellH, dt)
	else:
		ctx.fillStyle = "rgba(8,7,4,0.85)"
		for cy in range(0, world.fogRows, 2):
			for cx in range(0, world.fogCols, 2):
				if fog[cy * world.fogCols + cx] == World.FOG_UNSEEN:
					ctx.fillRect(mmX + cx * cellW, mmY + cy * cellH, cellW * 2, cellH * 2)

	# In team games, colour the map by relation to you, not raw team.
	var diplo := world.hasTeamAlliances()

	# Entities as dots.
	if retained:
		_minimapDots(ctx, world, team, diplo, mmX, mmY, sx, sy, dt)
	else:
		for e: Entity in world.entities:
			if not e.alive or e.kind == T.Kind.Projectile or not world.visibleTo(team, e):
				continue
			ctx.fillStyle = _dotColor(world, team, diplo, e)
			var s := 3.0 if e.kind == T.Kind.Building else 1.6
			ctx.fillRect(mmX + e.x * sx - s / 2, mmY + e.y * sy - s / 2, s, s)

	# Alert pings.
	for a in alerts:
		if a.x == null or a.y == null:
			continue
		var pulse := fmod(a.time * 3, 1.0)
		ctx.strokeStyle = Palette.withAlpha("#f25d4a", 1 - pulse)
		ctx.lineWidth = 2
		ctx.beginPath()
		ctx.arc(mmX + a.x * sx, mmY + a.y * sy, 4 + pulse * 8, 0, PI * 2)
		ctx.stroke()

	# Viewport rectangle.
	var vw := (cam.viewW / cam.zoom) * sx
	var vh := (cam.viewH / cam.zoom) * sy
	ctx.strokeStyle = "rgba(243, 233, 210, 0.9)"
	ctx.lineWidth = 1
	ctx.strokeRect(mmX + cam.x * sx - vw / 2, mmY + cam.y * sy - vh / 2, vw, vh)

	# Diplomacy legend (team games only): You / Ally / Enemy swatches.
	if diplo:
		var legend := [
			["You", PAL.diplomacy.self.main],
			["Ally", PAL.diplomacy.ally.main],
			["Enemy", PAL.diplomacy.enemies[0].main],
		]
		var lw := 58.0
		var lh := 12.0 + legend.size() * 14
		var lx := mmX + 4
		var ly := mmY + 4
		ctx.fillStyle = "rgba(12,9,5,0.72)"
		ctx.fillRect(lx, ly, lw, lh)
		ctx.strokeStyle = Palette.withAlpha(PAL.uiAccent, 0.3)
		ctx.lineWidth = 1
		ctx.strokeRect(lx, ly, lw, lh)
		for i in legend.size():
			var ry := ly + 8 + i * 14
			ctx.fillStyle = legend[i][1]
			ctx.fillRect(lx + 7, ry + 2, 9, 9)
			ui.text(legend[i][0], lx + 22, ry + 7, {"size": 11, "color": "#e7ddc4"})

	# Player pings: expanding cyan rings, both on the minimap and (briefly) in
	# the world. Friendly signals — "look here", "rally here".
	for ping in pings:
		var t: float = ping.age / PING_LIFE
		var pulse := fmod(ping.age * 2.2, 1.0)
		ctx.strokeStyle = Palette.withAlpha("#79e0ff", (1 - t) * (1 - pulse))
		ctx.lineWidth = 2
		ctx.beginPath()
		ctx.arc(mmX + ping.x * sx, mmY + ping.y * sy, 3 + pulse * 9, 0, PI * 2)
		ctx.stroke()

	# Minimap interaction. Press and drag to sweep the camera across it.
	var inMinimap := ui.hit(mmX, mmY, MINIMAP_SIZE, MINIMAP_SIZE)
	if ui.leftHeld and not prevLeftHeld and inMinimap and not ui.alt:
		minimapDragging = true
	minimapDragEnded = minimapDragging and not ui.leftHeld
	if not ui.leftHeld:
		minimapDragging = false
	prevLeftHeld = ui.leftHeld
	if minimapDragging:
		ui.pointerConsumed = true
		var wx := clampf((ui.mx - mmX) / sx, 0, world.worldW)
		var wy := clampf((ui.my - mmY) / sy, 0, world.worldH)
		ctrl.minimapNavigate(wx, wy)
	if inMinimap:
		ui.pointerConsumed = true
		var wx := (ui.mx - mmX) / sx
		var wy := (ui.my - mmY) / sy
		if ui.clicked:
			if ui.alt:
				ctrl.minimapPing(wx, wy)
			else:
				ctrl.minimapNavigate(wx, wy)
		if ui.rightClicked:
			ctrl.minimapCommand(wx, wy)

	# ----------------------------------------------------- command card --
	var cardW := CARD_W * (BTN + GAP) + GAP + 8.0
	var cardH := CARD_H * (BTN + GAP) + GAP + 8.0
	var cardX := W - cardW - 10
	var cardY := H - cardH - 10
	if not spectating:
		ui.panel(cardX, cardY, cardW, cardH)
		drawCommandCard(cardX + 8, cardY + 8, world, team, selection, ctrl)

	# -------------------------------------------------- selection panel --
	# Works while spectating too (inspect any unit) — kept clear of the right-
	# hand realm cards, which take one or two columns there.
	var selX := mmX + MINIMAP_SIZE + 18
	var specCols := 2 if world.numTeams > 4 else 1
	var rightEdge := W - specCols * 262 if spectating else cardX
	var selW := rightEdge - selX - 12
	if selection.size() > 0 and selW > 200:
		var selH := 104.0
		var selY := H - selH - 10
		ui.panel(selX, selY, selW, selH)
		drawSelectionPanel(selX + 10, selY + 8, selW - 20, world, selection, team, ctrl)


static func _dotColor(world: World, team: int, diplo: bool, e: Entity):
	var PAL: Dictionary = C.PAL
	if e.kind == T.Kind.Resource:
		return "#2f5b28" if e.type == "tree" else (PAL.goldVein if e.type == "gold_mine" else "#b13a4a")
	if diplo:
		var rel := world.relationTo(team, e.team)
		if rel == "self":
			return PAL.diplomacy.self.main
		if rel == "ally":
			return PAL.diplomacy.ally.main
		return PAL.diplomacy.enemies[world.enemyIndexOf(team, e.team) % PAL.diplomacy.enemies.size()].main
	return Palette.teamColor(e.team).main


## The minimap's entity dots, as a retained item redrawn every
## MINIMAP_DOTS_EVERY seconds (one native rect per dot).
func _minimapDots(ctx: LayerCtx, world: World, team: int, diplo: bool, mmX: float, mmY: float, sx: float, sy: float, dt: float) -> void:
	if not dotsItem.is_valid():
		dotsItem = RenderingServer.canvas_item_create()
	dotsAge += dt
	if dotsAge >= MINIMAP_DOTS_EVERY:
		dotsAge = 0.0
		RenderingServer.canvas_item_clear(dotsItem)
		var cache := {}
		var reveal := world.revealAll
		var bit := 1 << team
		var fog: PackedByteArray = world.fog[team]
		var fc := world.fogCols
		var fr := world.fogRows
		var grid := world.grid
		for e: Entity in world.entities:
			if not e.alive or e.kind == T.Kind.Projectile:
				continue
			# world.visibleTo, inlined (this loop is over every entity).
			if not reveal and e.team != team:
				if e.kind == T.Kind.Unit and (e.spottedBy & bit) == 0:
					continue
				var cx := grid.worldToCellX(e.x)
				var cy := grid.worldToCellY(e.y)
				var f := World.FOG_UNSEEN if cx < 0 or cy < 0 or cx >= fc or cy >= fr else fog[cy * fc + cx]
				if f != World.FOG_VISIBLE and not (e.kind == T.Kind.Building and f == World.FOG_EXPLORED):
					continue
			var key := (e.team * 8 + e.kind) * 4 + (1 if e.type == "tree" else (2 if e.type == "gold_mine" else 0))
			var col = cache.get(key)
			if col == null:
				col = _dotColor(world, team, diplo, e)
				if col is String:
					col = Ctx.parse_color(col)
				cache[key] = col
			var s := 3.0 if e.kind == T.Kind.Building else 1.6
			RenderingServer.canvas_item_add_rect(dotsItem, Rect2(mmX + e.x * sx - s / 2, mmY + e.y * sy - s / 2, s, s), col, true)
	ctx.external(dotsItem)


## The minimap's fog: the raw fog bytes as a texture, shaded on the GPU the
## way the TS fills it (every other cell, as a 2×2 block, where unexplored).
func _minimapFog(ctx: LayerCtx, fog: PackedByteArray, cols: int, rows: int, team: int, mmX: float, mmY: float, cellW: float, cellH: float, dt: float) -> void:
	if not fogItem.is_valid():
		fogItem = RenderingServer.canvas_item_create()
		fogMat = ShaderMaterial.new()
		var sh := Shader.new()
		sh.code = MINIMAP_FOG_SHADER
		fogMat.shader = sh
		RenderingServer.canvas_item_set_material(fogItem, fogMat.get_rid())
	fogAge += dt
	if fogTex == null or fogAge >= 0.12 or fogTeam != team:
		fogAge = 0.0
		fogTeam = team
		var img := Image.create_from_data(cols, rows, false, Image.FORMAT_R8, fog)
		if fogTex == null or fogTex.get_width() != cols or fogTex.get_height() != rows:
			fogTex = ImageTexture.create_from_image(img)
		else:
			fogTex.update(img)
		var bw := int(ceil(cols / 2.0)) * 2
		var bh := int(ceil(rows / 2.0)) * 2
		fogMat.set_shader_parameter("blocks", Vector2(bw, bh))
		fogMat.set_shader_parameter("rect_px", Vector2(bw * cellW, bh * cellH))
		RenderingServer.canvas_item_clear(fogItem)
		RenderingServer.canvas_item_add_texture_rect(fogItem, Rect2(mmX, mmY, bw * cellW, bh * cellH), fogTex.get_rid())
	ctx.external(fogItem)


## The TS fills every other unexplored cell as a 2×2-cell rect, each one
## antialiased on its own: where two meet, the shared pixel is covered by both
## at partial strength, which is the faint grain the minimap fog has. So the
## shader composites, per pixel, every block its footprint touches, at that
## block's coverage — 1 − Π(1 − 0.85·coverage) — exactly what the canvas does.
const MINIMAP_FOG_SHADER := """
shader_type canvas_item;
uniform vec2 blocks;   // the rect's size in cells (even)
uniform vec2 rect_px;  // the rect's size in layout units
void fragment() {
	ivec2 size = textureSize(TEXTURE, 0);
	vec2 p = UV * rect_px;
	vec2 d = max(fwidth(p), vec2(1e-4));
	vec2 bsz = 2.0 * rect_px / blocks;
	vec2 lo = p - 0.5 * d;
	vec2 hi = p + 0.5 * d;
	ivec2 b0 = ivec2(floor(lo / bsz));
	ivec2 b1 = ivec2(floor(hi / bsz));
	float keep = 1.0;
	for (int by = b0.y; by <= b1.y; by++) {
		for (int bx = b0.x; bx <= b1.x; bx++) {
			ivec2 c = ivec2(bx, by) * 2;
			if (c.x < 0 || c.y < 0 || c.x >= size.x || c.y >= size.y) continue;
			if (texelFetch(TEXTURE, c, 0).r > 0.5 / 255.0) continue;
			vec2 a0 = max(lo, vec2(float(bx), float(by)) * bsz);
			vec2 a1 = min(hi, vec2(float(bx + 1), float(by + 1)) * bsz);
			vec2 ov = max(a1 - a0, vec2(0.0));
			keep *= 1.0 - 0.85 * (ov.x * ov.y) / (d.x * d.y);
		}
	}
	COLOR = vec4(8.0 / 255.0, 7.0 / 255.0, 4.0 / 255.0, 1.0 - keep);
}
"""


func cost(c: Dictionary) -> String:
	var parts: Array = []
	if float(c.get("food", 0)) != 0:
		parts.append("%sf" % JS.num(c.food))
	if float(c.get("wood", 0)) != 0:
		parts.append("%sw" % JS.num(c.wood))
	if float(c.get("gold", 0)) != 0:
		parts.append("%sg" % JS.num(c.gold))
	return " ".join(parts) if not parts.is_empty() else "free"


func drawCommandCard(x0: float, y0: float, world: World, team: int, selection: Array, ctrl) -> void:
	var ui := UI.ui
	var p := world.player(team)
	var own: Array = selection.filter(func(e): return e.team == team)
	var slot := [0]
	var place := func(label: String, opts: Dictionary = {}) -> bool:
		var i: int = slot[0]
		slot[0] += 1
		var bx := x0 + (i % CARD_W) * (BTN + GAP)
		var by := y0 + floori(float(i) / CARD_W) * (BTN + GAP)
		var o := opts.duplicate()
		o.size = opts.get("size", 11)
		return ui.button(label, bx, by, BTN, BTN, o)

	var villagers: Array = own.filter(func(e): return e.kind == T.Kind.Unit and C.UNITS.has(e.type) and C.UNITS[e.type].get("canBuild", false))
	var combatUnits: Array = own.filter(func(e): return e.kind == T.Kind.Unit)
	var building: Entity = null
	for e in own:
		if e.kind == T.Kind.Building:
			building = e
			break

	if villagers.size() > 0 and (buildMenuOpen or own.all(func(e): return e.type == "villager")):
		if buildMenuOpen:
			if buildCategory == null:
				# Pick a build category.
				for cat in BUILD_CATEGORIES:
					if place.call(cat.label, {"accent": true, "size": 13, "tooltip": [cat.label, cat.hint]}):
						buildCategory = cat.id
				if place.call("Close", {"danger": true}):
					buildMenuOpen = false
				return
			var cat: Dictionary = {}
			for c in BUILD_CATEGORIES:
				if c.id == buildCategory:
					cat = c
			for id in cat.get("buildings", []):
				var def = C.BUILDINGS.get(id)
				if def == null:
					continue
				var lockedAge: bool = p.age < def.age
				var req = def.get("requires")
				var lockedReq: bool = req != null and req != "" and not world.hasBuilding(team, req)
				var bcost := world.buildingCostFor(team, def.id)
				var cantAfford := not world.canAfford(p.resources, bcost)
				var tip: Array = [def.name, cost(bcost)]
				if lockedAge:
					tip.append("Requires %s" % C.AGES[def.age].name)
				if lockedReq:
					tip.append("Requires %s" % C.BUILDINGS[req].name)
				tip.append_array(shortfall(p.resources, bcost))
				tip.append(def.desc)
				if place.call((def.name as String).split(" ")[0], {"disabled": lockedAge or lockedReq or cantAfford, "tooltip": tip}):
					ctrl.startPlacement(id)
					buildMenuOpen = false
					buildCategory = null
			if place.call("Back", {"danger": true}):
				buildCategory = null
			return
		if place.call("Build", {"accent": true, "tooltip": ["Open the construction menu (B)"]}):
			buildMenuOpen = true
			buildCategory = null

	if combatUnits.size() > 0:
		if place.call("Stop", {"tooltip": ["Stop (S)"]}):
			ctrl.stopSelection()
		if place.call("Atk Move", {"accent": true, "tooltip": ["Attack-move (A)", "March and engage anything hostile on the way."]}):
			ctrl.setAttackMoveMode()
		if place.call("Garrison", {"tooltip": ["Garrison (G)", "Tuck units into the nearest building for cover + extra arrows."]}):
			ctrl.garrisonSelection()
		# Combat stance — highlight the selection's prevailing posture (cycle with Y).
		var counts := [0, 0, 0, 0, 0]
		for u in combatUnits:
			counts[u.stance] += 1
		var cur: int = counts.find(counts.max())
		var stances := [
			["Aggr", T.Stance.Aggressive, "Aggressive — chase down any enemy in sight, then seek the next."],
			["Def", T.Stance.Defensive, "Defensive — engage nearby threats, then return to post."],
			["Stand", T.Stance.StandGround, "Stand Ground — fire on anything in range, never move to chase."],
			["No Atk", T.Stance.Passive, "Passive — hold fire; only fight when you order it."],
			["Skirm", T.Stance.Skirmish, "Skirmish — ranged units give ground while reloading, then plant and shoot. Beats slow infantry; cavalry still runs them down."],
		]
		for s in stances:
			if place.call(s[0], {"accent": cur == s[1], "size": 10, "tooltip": ["%s stance (Y to cycle)" % s[0], s[2]]}):
				ctrl.setStance(s[1])
		# Signature ability for whichever ability-carrying type is selected.
		var abilityUnit = null
		for e in combatUnits:
			if C.ABILITIES.has(e.type):
				abilityUnit = e
				break
		if abilityUnit != null:
			var ab: Dictionary = C.ABILITIES[abilityUnit.type]
			var same: Array = combatUnits.filter(func(e): return C.ABILITIES.has(e.type) and C.ABILITIES[e.type].id == ab.id)
			var ready: Array = same.filter(func(e): return e.abilityCooldown <= 0)
			var minCd := INF
			for e in same:
				minCd = minf(minCd, e.abilityCooldown)
			if place.call((ab.name as String).split(" ")[0], {
				"accent": ready.size() > 0,
				"disabled": ready.size() == 0,
				"badge": JS.num(ceil(minCd)) if ready.size() == 0 and minCd > 0 else "",
				"tooltip": [
					"%s (Q)" % ab.name,
					ab.desc,
					"%d ready" % ready.size() if ready.size() > 0 else "On cooldown — %ss" % JS.num(ceil(minCd)),
				],
			}):
				ctrl.useAbility()
		return

	if building != null and building.buildState == T.BuildState.Done:
		var def: Dictionary = C.BUILDINGS[building.type]
		# When several production buildings of the same type are selected,
		# training queues into all of them — so show the combined queued count
		# per unit.
		var prodSiblings: Array = own.filter(func(e): return e.kind == T.Kind.Building and e.type == building.type and e.buildState == T.BuildState.Done)
		for unitId in def.trains:
			var u: Dictionary = C.UNITS[unitId]
			# Another Oath's signature unit isn't this realm's to train, and a
			# button for it would only be clutter.
			var uo = u.get("oath")
			if uo != null and not p.oaths.has(uo):
				continue
			# Likewise another faction's units, and the shared units this
			# faction trains its own in place of.
			if not world.canFieldType(p, unitId):
				continue
			var ucost := world.unitCostFor(team, unitId)
			var lockedAge: bool = p.age < u.age
			var heroState = world.heroStatus(team) if u.get("hero", false) else null
			var heroLocked: bool = heroState != null and not heroState.trainable
			var tip: Array = [u.name + (" ★" if u.get("hero", false) else "")]
			if uo != null:
				var od = C.OATHS.get(uo)
				tip.append("Signature of the %s" % (od.name if od != null else "Oath"))
			tip.append(cost(ucost) + "  (%s pop)" % JS.num(u.pop))
			if lockedAge:
				tip.append("Requires %s" % C.AGES[u.age].name)
			tip.append_array(shortfall(p.resources, ucost))
			if p.popUsed + u.pop > p.popCap:
				tip.append("⚠ Population capped (%s/%s) — build a House" % [JS.num(p.popUsed), JS.num(p.popCap)])
			if prodSiblings.size() > 1:
				tip.append("Queues in all %d selected %ss" % [prodSiblings.size(), def.name])
			if heroState != null and heroState.label != "":
				tip.append(heroState.label)
			tip.append(u.desc)
			var queued := 0
			for b in prodSiblings:
				queued += b.productionQueue.count("u:%s" % unitId)
			if place.call((u.name as String).split(" ")[0].substr(0, 7), {
				"accent": u.get("hero", false) and not heroLocked,
				"disabled": lockedAge or heroLocked or not world.canAfford(p.resources, ucost) or p.popUsed + u.pop > p.popCap,
				"tooltip": tip,
				"badge": str(queued) if queued > 0 else "",
			}):
				ctrl.trainUnit(building, unitId)
		if building.type == "town_center" and p.age < C.MAX_AGE:
			var next: Dictionary = C.AGES[p.age + 1]
			var prog := world.ageRequirementProgress(team, p.age + 1)
			var reqNames: Array = []
			for id in next.requiresAny:
				var bd = C.BUILDINGS.get(id)
				reqNames.append(bd.name if bd != null else id)
			# Opens the Oath picker rather than researching outright: every
			# advance is also the choice of which Oath the realm swears. The
			# picker says what is missing, so the button only needs to be off
			# while an advance is already under way.
			var aging := own.any(func(e): return e.kind == T.Kind.Building and e.productionQueue.has("a:age"))
			var tip: Array = ["Advance to the %s — and swear an Oath" % next.name, cost(next.cost)]
			if next.requiresCount > 0:
				tip.append("Build any %s (%s/%s): %s" % [JS.num(next.requiresCount), JS.num(prog.have), JS.num(prog.need), ", ".join(reqNames)])
			tip.append("Choose one of three Oaths: a lasting bonus, and for some a unit only you can train.")
			if place.call("%s Age" % next.short, {"accent": true, "disabled": aging, "tooltip": tip}):
				oathPicker.open(building)
		# Research housed in this building (Blacksmith combat techs, eco techs
		# at the Mill / Lumber Camp / Mining Camp, etc.).
		for upId in C.UPGRADES:
			var up: Dictionary = C.UPGRADES[upId]
			if up.researchedAt != building.type or p.upgrades.has(upId):
				continue
			var tc := world.techCostFor(team, upId)
			var tip: Array = [up.name, cost(tc)]
			if p.age < up.age:
				tip.append("Requires %s" % C.AGES[up.age].name)
			tip.append_array(shortfall(p.resources, tc))
			tip.append(up.desc)
			if place.call((up.name as String).split(" ")[0].substr(0, 7), {
				"disabled": p.age < up.age or not world.canAfford(p.resources, tc),
				"tooltip": tip,
			}):
				ctrl.research(building, upId)
		if building.type == "market":
			# Live quotes, because the whole point of a moving price is being
			# able to see it move. A rate below par means the market is glutted.
			var wq := world.marketQuote(team, "wood")
			var fq := world.marketQuote(team, "food")
			var trend := func(v: float) -> String:
				return " ▲" if v > 1.02 else (" ▼" if v < 0.98 else "")
			var wp := world.marketPrice(team, "wood")
			var fp := world.marketPrice(team, "food")
			if place.call("Sell 🪵%s" % trend.call(wp), {"size": 10,
					"tooltip": ["Sell 100 wood → %s gold" % JS.num(wq.sell), "Every sale pushes the price down; it drifts back over a few minutes."]}):
				ctrl.trade("sell_wood")
			if place.call("Sell 🍖%s" % trend.call(fp), {"size": 10,
					"tooltip": ["Sell 100 food → %s gold" % JS.num(fq.sell), "Every sale pushes the price down; it drifts back over a few minutes."]}):
				ctrl.trade("sell_food")
			if place.call("Buy 🪵%s" % trend.call(wp), {"size": 10,
					"tooltip": ["Buy 100 wood ← %s gold" % JS.num(wq.buy), "Every purchase pushes the price up."]}):
				ctrl.trade("buy_wood")
			if place.call("Buy 🍖%s" % trend.call(fp), {"size": 10,
					"tooltip": ["Buy 100 food ← %s gold" % JS.num(fq.buy), "Every purchase pushes the price up."]}):
				ctrl.trade("buy_food")
		if building.type == "gate":
			if place.call("Close Gate" if building.gateOpen else "Open Gate", {
				"accent": true,
				"tooltip": [
					"Bar the gate shut" if building.gateOpen else "Swing the gate open",
					"Otherwise it opens for your troops and shuts when foes draw near.",
				],
			}):
				ctrl.toggleGate(building)
		# Auto-reseed lives on the farm and the Mill because those are the two
		# things you have selected when you are thinking about food at all.
		if building.type == "farm" or building.type == "mill":
			if place.call("Reseed ✓" if p.autoReseed else "Reseed ✗", {
				"accent": p.autoReseed,
				"size": 10,
				"tooltip": [
					"Auto-reseed farms",
					"A spent field is re-sown on the same ground whenever the wood is there." if p.autoReseed \
						else "Spent fields are left bare and the farmer goes to find other work.",
					"Each re-sowing costs %s." % cost(C.BUILDINGS.farm.cost),
				],
			}):
				ctrl.setAutoReseed(not p.autoReseed)
		if building.garrison.size() > 0:
			if place.call("Eject %d" % building.garrison.size(), {"tooltip": ["Ungarrison all units (G)"]}):
				ctrl.ungarrison(building)


func drawSelectionPanel(x: float, y: float, w: float, world: World, selection: Array, team: int, ctrl) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var PAL: Dictionary = C.PAL
	if selection.size() == 1:
		var e: Entity = selection[0]
		var def = C.UNITS.get(e.type) if e.kind == T.Kind.Unit else null
		var bdef = C.BUILDINGS.get(e.type) if e.kind == T.Kind.Building else null
		var name: String = def.name if def != null else (bdef.name if bdef != null else e.type)
		var rarity = Draw.rarityByIndex(e.variantRarity) if e.kind == T.Kind.Unit and e.team == team else null
		ui.text(name, x, y + 10, {"size": 16, "bold": true, "color": PAL.uiAccent})
		if rarity != null and int(rarity.index) > 0:
			ui.text(rarity.name, x + ctx.measureText(name).width + 26, y + 10, {"size": 12, "color": rarity.color, "bold": true})
		ui.bar(x, y + 26, minf(w, 220), 8, e.hp / e.maxHp, PAL.uiGood if e.hp / e.maxHp > 0.4 else PAL.uiBad)
		ui.text("%s / %s" % [JS.num(ceil(e.hp)), JS.num(e.maxHp)], x + minf(w, 220) + 10, y + 30, {"size": 12})
		var line := ""
		if def != null:
			line = "⚔ %s   🛡 %s   %s   👟 %s" % [JS.num(e.attack), JS.num(e.armor), ("🎯 %s" % JS.num(e.range)) if def.get("ranged", false) else "melee", JS.num(JS.round(e.speed))]
		elif bdef != null:
			if e.buildState != T.BuildState.Done:
				line = "Under construction — %s%%" % JS.num(JS.round(e.buildProgress * 100))
			elif float(bdef.attack) > 0:
				line = "⚔ %s   🎯 %s   Garrison %d/%s" % [JS.num(bdef.attack), JS.num(bdef.range), e.garrison.size(), JS.num(bdef.garrisonCap)]
			elif e.garrison.size() > 0:
				line = "Garrison %d/%s" % [e.garrison.size(), JS.num(bdef.garrisonCap)]
		if line != "":
			ui.text(line, x, y + 50, {"size": 13})

		# Veterancy. The chevrons over a unit's head say it has *a* rank; they
		# can't say which, how it was earned, or how close the next one is —
		# and a rank that silently added HP and attack was doing real work
		# nobody could see. Rank ladder ends at Legendary, where there is
		# nothing left to count toward.
		if def != null and e.veterancy > 0 and not def.get("hero", false):
			var rank: String = World.VET_RANKS[e.veterancy] if e.veterancy < World.VET_RANKS.size() else ""
			var col := "#ffe07a" if e.veterancy >= 3 else ("#ffd24a" if e.veterancy >= 2 else "#e8c98a")
			var vet := "%s %s  ·  %d kill%s" % ["⌃".repeat(e.veterancy), rank, e.vetKills, "" if e.vetKills == 1 else "s"]
			if e.veterancy < World.VET_THRESHOLDS.size():
				var nxt: int = World.VET_THRESHOLDS[e.veterancy]
				var pl := world.player(e.team)
				var vetMult := pl.vetMult if pl != null else 1.0
				vet += "  ·  next at %s" % JS.num(maxf(1, JS.round(nxt / vetMult)))
			ui.text(vet, x, y + 68, {"size": 12, "color": col, "bold": true})

		# Production queue.
		if e.kind == T.Kind.Building and e.productionQueue.size() > 0:
			# The whole queue, one chip per item; click one to cancel it (refunded).
			var nameOf := func(item: String) -> String:
				if item.begins_with("u:"):
					var u = C.UNITS.get(item.substr(2))
					return u.name if u != null else item
				if item == "a:age":
					return "Advancing Age"
				var up = C.UPGRADES.get(item.substr(2))
				return up.name if up != null else item
			var own := e.team == team
			var item0: String = e.productionQueue[0]
			var total := world.itemTime(item0)
			var qw := 62.0
			var qh := 22.0
			var maxN := maxi(1, floori((w + 4) / (qw + 4)))
			var shown: Array = e.productionQueue.slice(0, maxN)
			for i in shown.size():
				var item: String = shown[i]
				var qx := x + i * (qw + 4)
				var qy := y + 60
				var full: String = nameOf.call(item)
				var short: String = full.trim_prefix("Advancing ").split(" ")[0].substr(0, 8)
				if own and ui.button(short, qx, qy, qw, qh, {"size": 10.5, "accent": i == 0,
						"tooltip": [full, "In progress" if i == 0 else "Queued (%d)" % (i + 1), "Click to cancel — the cost is refunded."]}):
					ctrl.cancelProduction(e, i)
				elif not own:
					ui.text(short, qx + 4, qy + 15, {"size": 10.5, "color": "#bdb49a"})
				if i == 0:
					ui.bar(qx + 3, qy + qh - 5, qw - 6, 3, 1 - e.productionTime / total, "#ffe9b0", "rgba(0,0,0,0.4)")
			if e.productionQueue.size() > maxN:
				ui.text("+%d" % (e.productionQueue.size() - maxN), x + maxN * (qw + 4), y + 76, {"size": 12, "color": "#bdb49a"})
	else:
		# Multi-select: count chips by type.
		var counts := {}
		for e in selection:
			counts[e.type] = counts.get(e.type, 0) + 1
		ui.text("%d selected" % selection.size(), x, y + 10, {"size": 15, "bold": true, "color": PAL.uiAccent})
		var cx := x
		var cy := y + 34
		for type in counts:
			var n: int = counts[type]
			var ud = C.UNITS.get(type)
			var bd = C.BUILDINGS.get(type)
			var name: String = ud.name if ud != null else (bd.name if bd != null else type)
			var label := "%s ×%d" % [name, n]
			ctx.font = "13px 'Trebuchet MS', sans-serif"
			var cw := ctx.measureText(label).width + 18
			if cx + cw > x + w:
				cx = x
				cy += 26
			var hov := counts.size() > 1 and ui.hit(cx, cy - 11, cw, 22)
			ctx.fillStyle = "rgba(255,233,176,0.18)" if hov else "rgba(255,255,255,0.07)"
			ctx.beginPath()
			ctx.roundRect(cx, cy - 11, cw, 22, 11)
			ctx.fill()
			var o := {"size": 13}
			if hov:
				o.color = "#ffe9b0"
			ui.text(label, cx + 9, cy, o)
			if hov:
				ui.pointerConsumed = true
				ui.tooltip([name, "Click: select only these", "Shift+click: drop these from the selection"])
				if ui.clicked:
					ctrl.narrowSelection(type, shiftHeld)
			cx += cw + 8
