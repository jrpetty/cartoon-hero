## ui/screens.ts — the main menu, the Skirmish setup, the Armory and the
## post-match screen, plus the shared painterly menu backdrop.
##
## The module's classes are inner classes of the same names
## (`Screens.MenuScreen`, `Screens.SetupScreen`, `Screens.ArmoryScreen`,
## `Screens.PostMatchScreen`); its functions are statics (`Screens.
## drawMenuBackground`, `Screens.setMouseDown`, `Screens.isMouseDown`).
##
## SkirmishConfig is a Dictionary of the TS shape — `Screens.defaultSkirmishConfig()`
## makes the one a fresh setup screen starts from:
##   { presetId, seed, difficulty (default AI personality, applied to any unset
##     bot slot), aiDifficulties (per-bot personality by team index),
##     fairMode, players (2 = 1v1, 4 = FFA or 2v2),
##     teams (each seat's team, seat 0 being you: 0 fights alone, seats sharing
##       1..8 are allies — ui/teams.gd),
##     commander, faction (yours), aiFactions (each bot's by team index, "" = a
##       seeded random pick), nomad (no starting Town Center), mode (conquest /
##       survival / koth / regicide),
##     humanLoadout?, humanBoons? (filled in at match start: the human's unit
##       rarities and boon plan, so a save or replay rebuilds exactly the match
##       that was played),
##     rival? ({ name, epithet, rank, adaptation } for a Nemesis match) }
##
## GraphSeries (the post-match chart's data) is a Dictionary or null:
##   { ts, mine: {metric: values}, foe: {metric: values},
##     players?: [{ team, you, horde?, values: {metric: values} }] }
class_name Screens
extends RefCounted

const COMMANDER_RECRUIT_COST := 500


static func defaultSkirmishConfig() -> Dictionary:
	return {
		"presetId": "open_plains",
		"seed": RNG.randomSeed(),
		"difficulty": "knight",
		"aiDifficulties": [],
		"fairMode": false,
		"players": 2,
		"teams": [0, 0],
		"commander": "",
		"faction": "",
		"aiFactions": [],
		"nomad": false,
		"mode": "conquest",
	}


# ------------------------------------------------------------- background --

## Painterly menu backdrop: dusk sky, hills, castle silhouette.
static func drawMenuBackground(W: float, H: float, time: float) -> void:
	var ctx := UI.ui.ctx
	var sky := ctx.createLinearGradient(0, 0, 0, H)
	sky.addColorStop(0, "#2c2440")
	sky.addColorStop(0.55, "#7a4a58")
	sky.addColorStop(0.8, "#c8784a")
	sky.addColorStop(1, "#e8a05a")
	ctx.fillStyle = sky
	ctx.fillRect(0, 0, W, H)

	# The first stars, fading out toward the glow.
	for i in 70:
		var sx := float((i * 7919) % 1000) / 1000.0 * W
		var sy := float((i * 104729) % 1000) / 1000.0 * H * 0.45
		var tw := 0.35 + 0.35 * sin(time * 1.3 + i * 2.1)
		ctx.fillStyle = Palette.withAlpha("#fff4dc", tw * (1 - sy / (H * 0.45)))
		var ss := 2.0 if i % 5 == 0 else 1.2
		ctx.fillRect(sx, sy, ss, ss)
	# Long thin clouds catching the last light, drifting.
	for i in 4:
		var cyy := H * (0.3 + i * 0.07)
		var cxx := (fmod(i * 0.37 + time * 0.004 * (i + 1), 1.4) - 0.2) * W
		var cg := ctx.createLinearGradient(cxx - 220, 0, cxx + 220, 0)
		cg.addColorStop(0, "rgba(255,190,150,0)")
		cg.addColorStop(0.5, "rgba(255,190,150,%s)" % JS.num(0.16 - i * 0.02))
		cg.addColorStop(1, "rgba(255,190,150,0)")
		ctx.fillStyle = cg
		ctx.beginPath()
		ctx.ellipse(cxx, cyy, 220 + i * 40, 7 + i * 2, 0, 0, PI * 2)
		ctx.fill()

	# sun
	ctx.fillStyle = Palette.withAlpha("#ffd9a0", 0.9)
	ctx.beginPath()
	ctx.arc(W * 0.72, H * 0.62, 46, 0, PI * 2)
	ctx.fill()
	ctx.fillStyle = Palette.withAlpha("#ffd9a0", 0.25)
	ctx.beginPath()
	ctx.arc(W * 0.72, H * 0.62, 78, 0, PI * 2)
	ctx.fill()

	# distant hills
	ctx.fillStyle = "#4a3a52"
	ctx.beginPath()
	ctx.moveTo(0, H * 0.78)
	var x := 0.0
	while x <= W:
		ctx.lineTo(x, H * 0.78 - sin(x * 0.004 + 2) * 36 - sin(x * 0.013) * 14)
		x += 40
	ctx.lineTo(W, H)
	ctx.lineTo(0, H)
	ctx.fill()

	# nearer hills with a ragged treeline
	ctx.fillStyle = "#3a2e40"
	ctx.beginPath()
	ctx.moveTo(0, H * 0.84)
	x = 0.0
	while x <= W:
		var hy := H * 0.84 - sin(x * 0.006 + 0.7) * 22 - sin(x * 0.021) * 6
		var xi := int(x)
		ctx.lineTo(x, hy - ((8 + ((xi * 13) % 7)) if (xi * 37) % 11 > 6 else 0))
		x += 14
	ctx.lineTo(W, H)
	ctx.lineTo(0, H)
	ctx.fill()

	# castle silhouette
	ctx.fillStyle = "#241c2c"
	var cx := W * 0.2
	var base := H * 0.82
	ctx.fillRect(cx - 90, base - 110, 180, 110)
	for tx in [-90, 90]:
		ctx.fillRect(cx + tx - 22, base - 170, 44, 170)
		for i in 3:
			ctx.fillRect(cx + tx - 22 + i * 17, base - 184, 10, 16)
	ctx.fillRect(cx - 14, base - 220, 28, 220)
	for i in 2:
		ctx.fillRect(cx - 14 + i * 19, base - 234, 9, 16)
	# lit windows
	ctx.fillStyle = Palette.withAlpha("#ffc870", 0.75 + 0.2 * sin(time * 3.1))
	for wv in [[-60, -80], [-30, -80], [30, -80], [60, -80], [-90, -130], [90, -130], [0, -170]]:
		ctx.fillRect(cx + wv[0] - 3, base + wv[1], 6, 10)
	# banner waving from the keep
	var wave := sin(time * 2.2) * 5
	ctx.fillStyle = "#b8483e"
	ctx.beginPath()
	ctx.moveTo(cx + 14, base - 230)
	ctx.quadraticCurveTo(cx + 40, base - 226 + wave * 0.4, cx + 58, base - 220 + wave)
	ctx.lineTo(cx + 52, base - 208 + wave * 0.7)
	ctx.quadraticCurveTo(cx + 34, base - 212, cx + 14, base - 212)
	ctx.closePath()
	ctx.fill()

	# foreground field
	ctx.fillStyle = "#2c3420"
	ctx.beginPath()
	ctx.moveTo(0, H * 0.92)
	x = 0.0
	while x <= W:
		ctx.lineTo(x, H * 0.92 - sin(x * 0.01 + 9) * 10)
		x += 30
	ctx.lineTo(W, H)
	ctx.lineTo(0, H)
	ctx.fill()

	# A soft vignette pulls the eye to the middle.
	var vg := ctx.createRadialGradient(W / 2, H * 0.45, minf(W, H) * 0.3, W / 2, H * 0.45, maxf(W, H) * 0.75)
	vg.addColorStop(0, "rgba(0,0,0,0)")
	vg.addColorStop(1, "rgba(10,6,14,0.45)")
	ctx.fillStyle = vg
	ctx.fillRect(0, 0, W, H)


# Track mouse-down state for sliders (set from main each frame).
static var mouseDown := false


static func setMouseDown(d: bool) -> void:
	mouseDown = d


static func isMouseDown() -> bool:
	return mouseDown


static func wrapText(text: String, x: float, y: float, maxW: float, size: float, color) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	ctx.font = "%spx 'Trebuchet MS', sans-serif" % JS.num(size)
	var line := ""
	var yy := y
	for w in text.split(" "):
		var test := line + " " + w if line != "" else w
		if ctx.measureText(test).width > maxW and line != "":
			ui.text(line, x, yy, {"size": size, "color": color})
			line = w
			yy += size + 4
		else:
			line = test
	if line != "":
		ui.text(line, x, yy, {"size": size, "color": color})


static func _strip_the(s: String) -> String:
	return s.substr(4) if s.begins_with("The ") else s


static func _cap(s: String) -> String:
	return s.substr(0, 1).to_upper() + s.substr(1) if s != "" else s


## A unit drawn through the current faction resolver; an Entity with the
## fields the TS `Object.assign` sets.
static func _unitEntity(type: String, team: int, x: float, y: float, radius: float, hp: float, facing: float) -> Entity:
	var e := World.makeEntity()
	e.kind = T.Kind.Unit; e.type = type; e.team = team; e.x = x; e.y = y
	e.radius = radius; e.hp = hp; e.maxHp = hp; e.facing = facing
	return e


static func drawChestArt(cx: float, cy: float, scale: float, tier: int) -> void:
	var ctx := UI.ui.ctx
	ctx.save()
	ctx.translate(cx, cy)
	ctx.scale(scale, scale)
	var bodyCol := "#7a5a36" if tier == 0 else ("#6a6f7e" if tier == 1 else "#8a6a2e")
	var trimCol := "#4a3620" if tier == 0 else ("#b8c2d4" if tier == 1 else "#ffd166")
	# body
	ctx.fillStyle = bodyCol
	ctx.beginPath()
	ctx.roundRect(-46, -18, 92, 48, 6)
	ctx.fill()
	# lid
	ctx.fillStyle = Palette.shade(bodyCol, 0.15)
	ctx.beginPath()
	ctx.roundRect(-46, -40, 92, 28, [14, 14, 0, 0])
	ctx.fill()
	# bands
	ctx.fillStyle = trimCol
	ctx.fillRect(-46, -16, 92, 5)
	ctx.fillRect(-14, -40, 8, 70)
	ctx.fillRect(6, -40, 8, 70)
	# lock
	ctx.fillStyle = trimCol
	ctx.beginPath()
	ctx.roundRect(-8, -8, 16, 18, 3)
	ctx.fill()
	ctx.fillStyle = "#241c10"
	ctx.beginPath()
	ctx.arc(0, -1, 3.4, 0, PI * 2)
	ctx.fill()
	if tier == 2:
		# royal glow
		ctx.strokeStyle = Palette.withAlpha("#ffd166", 0.7)
		ctx.lineWidth = 2
		for i in 4:
			var a := -PI / 2 + (i - 1.5) * 0.5
			ctx.beginPath()
			ctx.moveTo(cos(a) * 50, -30 + sin(a) * 22)
			ctx.lineTo(cos(a) * 64, -30 + sin(a) * 34)
			ctx.stroke()
	ctx.restore()


static func num0(n: float) -> String:
	return JS.locale_int(n)


# ------------------------------------------------------------------- menu --

class MenuScreen extends RefCounted:
	## A save the player picked from the continue strip, consumed by the app.
	var pickedSave = null

	## Returns "skirmish" | "multiplayer" | "warband" | "armory" | "codex" |
	## "settings" | "editor" | "resume" | "factions" | "career" | "nemesis" | null.
	func draw(W: float, H: float, time: float, profile: Profile) -> Variant:
		var ui := UI.ui
		var PAL: Dictionary = C.PAL
		Screens.drawMenuBackground(W, H, time)
		var ctx := ui.ctx

		# First-launch (or freshly-recruited) commander reveal overlay.
		var reveal = C.COMMANDERS.get(profile.data.get("commanderReveal", ""))
		if reveal != null:
			ctx.fillStyle = "rgba(8,6,3,0.78)"
			ctx.fillRect(0, 0, W, H)
			ui.text("⚑ A Commander Joins Your Banner ⚑", W / 2, H * 0.26, {
				"align": "center", "size": 26, "bold": true, "color": "#ffe9b0", "font": "Georgia, serif",
			})
			var pw := 460.0
			var px := W / 2 - pw / 2
			var py := H * 0.32
			ui.panel(px, py, pw, 200, {"light": true})
			ui.text(reveal.name, W / 2, py + 40, {"align": "center", "size": 30, "bold": true, "color": reveal.color, "font": "Georgia, serif"})
			ui.text(reveal.title, W / 2, py + 66, {"align": "center", "size": 16, "color": "#d8cdb4"})
			Screens.wrapText(reveal.desc, px + 30, py + 96, pw - 60, 14, "#bdb49a")
			var rry := py + 150
			for perk in Commanders.commanderPerks(reveal):
				ui.text("• " + perk, W / 2, rry, {"align": "center", "size": 13, "color": PAL.uiAccent})
				rry += 18
			if ui.button("Claim", W / 2 - 90, py + 216, 180, 46, {"accent": true, "size": 18}):
				profile.clearCommanderReveal()
				audio.play("levelup")
			return null

		var action = null
		# First launch, once the commander is claimed: choose the free faction
		# before anything else. The book won't let you leave without one.
		if profile.needsFirstFaction:
			return "factions"

		# ---- title ----
		var narrow := W < 980
		var titleY := maxf(66, minf(H * 0.14, 124))
		var tsize := 46.0 if narrow else 64.0
		ctx.save()
		ctx.textAlign = "center"
		ctx.font = "bold %spx Georgia, 'Times New Roman', serif" % JS.num(tsize)
		ctx.fillStyle = "rgba(0,0,0,0.5)"
		ctx.fillText("Banner & Blade", W / 2 + 3, titleY + 3)
		var grad := ctx.createLinearGradient(0, titleY - tsize * 0.65, 0, titleY + 10)
		grad.addColorStop(0, "#fff2c8")
		grad.addColorStop(0.55, "#ffd98a")
		grad.addColorStop(1, "#c8923a")
		ctx.fillStyle = grad
		ctx.fillText("Banner & Blade", W / 2, titleY)
		# A thin gold rule with a diamond, under the title.
		var rw := minf(360, W * 0.3)
		var ry := titleY + 18
		var rule := ctx.createLinearGradient(W / 2 - rw, 0, W / 2 + rw, 0)
		rule.addColorStop(0, "rgba(232,192,96,0)"); rule.addColorStop(0.5, "rgba(232,192,96,0.9)"); rule.addColorStop(1, "rgba(232,192,96,0)")
		ctx.fillStyle = rule
		ctx.fillRect(W / 2 - rw, ry, rw * 2, 1.5)
		ctx.beginPath(); ctx.moveTo(W / 2, ry - 5); ctx.lineTo(W / 2 + 5, ry + 0.75); ctx.lineTo(W / 2, ry + 6.5); ctx.lineTo(W / 2 - 5, ry + 0.75); ctx.closePath()
		ctx.fillStyle = "#e8c060"; ctx.fill()
		ctx.font = "italic 16px Georgia, serif"
		ctx.fillStyle = Palette.withAlpha("#f3e9d2", 0.85)
		ctx.fillText("Raise your banner. Sharpen your blade. Take the field.", W / 2, ry + 26)
		ctx.restore()

		# ---- layout: your realm on the left, where to go on the right ----
		var outer := minf(W - (32 if narrow else 64), 1120)
		var x0 := JS.round(W / 2 - outer / 2)
		# Sit the block a little below the title when there is room to spare.
		var blockH := 380.0
		var top := JS.round(ry + 46 + maxf(0, (H - (ry + 46) - blockH - 90) * 0.3))
		var gap := 14.0
		var leftW := 0.0 if narrow else 320.0
		var rx := x0 if narrow else x0 + leftW + 20
		var rw2 := outer if narrow else outer - leftW - 20
		var saves := Savegame.listSaves()
		var info := profile.levelInfo()
		var stats: Dictionary = profile.data.stats

		if not narrow:
			var ly := top
			# Profile — click it for your Career.
			if ui.button("", x0, ly, leftW, 112, {"tooltip": ["Your career", "Win rates, favourite faction, map and unit, average win time — every game, skirmish and online."]}):
				action = "career"
			ui.text(profile.data.name, x0 + 18, ly + 30, {"size": 20, "bold": true, "color": "#ffe9b0", "font": "Georgia, serif"})
			ui.text("Level %s" % JS.num(info.level), x0 + leftW - 18, ly + 29, {"align": "right", "size": 13, "bold": true, "color": PAL.uiAccent})
			ui.bar(x0 + 18, ly + 44, leftW - 36, 8, float(info.into) / info.need, PAL.uiAccent)
			ui.text("%s / %s XP" % [JS.num(info.into), JS.num(info.need)], x0 + 18, ly + 68, {"size": 11.5, "color": "#bdb49a"})
			ui.text("%s ✦ renown" % JS.num(profile.data.renown), x0 + leftW - 18, ly + 68, {"align": "right", "size": 11.5, "bold": true, "color": "#e8c060"})
			ui.text("%s battles · %s victories · best streak %s" % [JS.num(stats.played), JS.num(stats.wins), JS.num(stats.bestStreak)], x0 + 18, ly + 94, {"size": 11.5, "color": "#a89f88"})
			ly += 112 + gap

			# Your realm: the faction and commander you will take into Skirmish.
			var fac := Factions.factionOf(profile.playableFaction())
			var cmdr = C.COMMANDERS.get(profile.data.commander)
			if ui.button("", x0, ly, leftW, 124, {"tooltip": ["Your realm", "%d of %d factions owned" % [profile.ownedFactions().size(), C.FACTION_IDS.size()], "Open the Factions book — read about each, and unlock more."]}):
				action = "factions"
			ctx.fillStyle = fac.color
			ctx.fillRect(x0 + 1, ly + 10, 4, 104)
			ui.text("YOUR REALM", x0 + 18, ly + 22, {"size": 10.5, "bold": true, "color": "#a89f88"})
			ui.text(fac.name, x0 + 18, ly + 48, {"size": 21, "bold": true, "color": fac.color, "font": "Georgia, serif"})
			ui.text(fac.era, x0 + 18, ly + 67, {"size": 12, "color": "#d8cdb4"})
			ui.text(("Led by %s, %s" % [cmdr.name, cmdr.title]) if cmdr != null else "No commander", x0 + 18, ly + 90, {"size": 12, "color": cmdr.color if cmdr != null else "#a89f88"})
			ui.text("%d/%d owned · " % [profile.ownedFactions().size(), C.FACTION_IDS.size()], x0 + 18, ly + 110, {"size": 11, "color": "#8f8770"})
			var curve: String = fac.curve
			ui.text("Strongest %s" % ("early" if curve == "early" else ("mid-game" if curve == "mid" else ("late" if curve == "late" else "throughout"))), x0 + 92, ly + 110, {"size": 11, "color": "#8f8770"})
			drawRealmSoldier(x0 + leftW - 46, ly + 96, time, profile.playableFaction())
			ly += 124 + gap

			# Continue: the latest save, and the others as chips.
			if saves.size():
				var latest: Dictionary = saves[0]
				var el: float = latest.summary.elapsed
				var mm := floori(el / 60.0)
				var ss := str(floori(fmod(el, 60.0))).lpad(2, "0")
				if ui.button("", x0, ly, leftW, 70, {
					"tooltip": ["Continue your last save", "%s · %s players · %s" % [latest.summary.mode, JS.num(latest.summary.players), latest.summary.difficulty],
						"Loading replays the match from its opening order, so it takes a moment."],
				}):
					pickedSave = latest
					action = "resume"
				MenuIcons.iconResume(ctx, x0 + 34, ly + 35, 44)
				ui.text("Continue", x0 + 64, ly + 28, {"size": 16, "bold": true, "color": "#ffe9b0"})
				ui.text("%s · %d:%s" % [latest.summary.mapName, mm, ss], x0 + 64, ly + 48, {"size": 12, "color": "#bdb49a"})
				ly += 70 + 6
				if saves.size() > 1:
					var n := mini(3, saves.size() - 1)
					var cw := (leftW - (n - 1) * 4) / n
					var others := saves.slice(1, 1 + n)
					for i in others.size():
						var sv: Dictionary = others[i]
						var sm := floori(float(sv.summary.elapsed) / 60.0)
						if ui.button("%s · %dm" % [String(sv.summary.mapName).substr(0, 10), sm], x0 + i * (cw + 4), ly, cw, 26, {
							"size": 10.5, "tooltip": [sv.label, "%s · %s players" % [sv.summary.mode, JS.num(sv.summary.players)]],
						}):
							pickedSave = sv
							action = "resume"

		# ---- tiles ----
		var ty := top
		if narrow:
			# One compact line for the profile, then the tiles.
			ui.panel(x0, ty, outer, 46)
			ui.text("%s · Level %s" % [profile.data.name, JS.num(info.level)], x0 + 14, ty + 28, {"size": 14, "bold": true, "color": "#ffe9b0"})
			ui.bar(x0 + outer * 0.5, ty + 20, outer * 0.5 - 14, 7, float(info.into) / info.need, PAL.uiAccent)
			ty += 46 + gap
			if saves.size():
				var latest: Dictionary = saves[0]
				if ui.button("Continue — %s" % latest.summary.mapName, x0, ty, outer, 38, {"size": 14}):
					pickedSave = latest
					action = "resume"
				ty += 38 + gap
		var pool := CustomMaps.listCustomMaps().filter(func(m): return m.get("published")).size()
		var bigH := 104.0 if narrow else 150.0
		var npre: int = MapGen.PRESETS.size()
		if tile(rx, ty, rw2, bigH, "Skirmish", "Battle the AI — two to eight realms, any teams, any battlefield.",
			("%d battlefields + %d published map%s" % [npre, pool, "" if pool == 1 else "s"]) if pool else ("%d battlefields · %d factions · 4 ages" % [npre, C.FACTION_IDS.size()]),
			func(cx, cy, sz): MenuIcons.iconSkirmish(ctx, cx, cy, sz, "#b8483e"), {"accent": true, "big": true}):
			action = "skirmish"
		ty += bigH + gap
		var midH := 88.0 if narrow else 108.0
		var hw := (rw2 - gap * 2) / 3
		if tile(rx, ty, hw, midH, "Multiplayer", "Online, up to 8 v 8.", "",
			func(cx, cy, sz): MenuIcons.iconMultiplayer(ctx, cx, cy, sz, time)):
			action = "multiplayer"
		var pf = profile.playableFaction()
		var rival: Dictionary = Rival.loadRival(pf if pf != null else "").current
		if tile(rx + hw + gap, ty, hw, midH, "Nemesis", "%s %s" % [rival.name, rival.epithet], "Rank %s · remembers you" % JS.num(rival.rank),
			func(cx, cy, sz): MenuIcons.iconNemesis(ctx, cx, cy, sz)):
			action = "nemesis"
		if tile(rx + (hw + gap) * 2, ty, hw, midH, "Warband Tactics", "Draft, merge and outlast.", "",
			func(cx, cy, sz): MenuIcons.iconWarband(ctx, cx, cy, sz)):
			action = "warband"
		ty += midH + gap
		var smH := 80.0 if narrow else 96.0
		var qw := (rw2 - gap * 5) / 6
		var small := [
			["career", "Career", "%s battles · your stats, skirmish and online" % JS.num(stats.played), func(cx, cy, sz): MenuIcons.iconCareer(ctx, cx, cy, sz)],
			["factions", "Factions", "%d/%d owned · read about each, unlock more" % [profile.ownedFactions().size(), C.FACTION_IDS.size()], func(cx, cy, sz): MenuIcons.iconFactions(ctx, cx, cy, sz)],
			["armory", "Armory", "%s ✦ · War Chests & boons" % JS.num(profile.data.renown), func(cx, cy, sz): MenuIcons.iconArmory(ctx, cx, cy, sz)],
			["editor", "Map Editor", "Make a map, publish it", func(cx, cy, sz): MenuIcons.iconMap(ctx, cx, cy, sz)],
			["codex", "Codex", "Units, ages, factions", func(cx, cy, sz): MenuIcons.iconCodex(ctx, cx, cy, sz)],
			["settings", "Settings", "Sound, display, keys", func(cx, cy, sz): MenuIcons.iconSettings(ctx, cx, cy, sz)],
		]
		for i in small.size():
			var it: Array = small[i]
			var x := rx + i * (qw + gap)
			if ui.button("", x, ty, qw, smH, {"tooltip": [it[1], it[2]]}):
				action = it[0]
			(it[3] as Callable).call(x + qw / 2, ty + smH * 0.38, minf(smH * 0.5, 50))
			ui.text(it[1], x + qw / 2, ty + smH - 16, {"align": "center", "size": 12.5 if narrow else 14.0, "bold": true, "color": PAL.uiParchment})

		ui.text("Banner & Blade", W - 16, H - 14, {"align": "right", "size": 10.5, "color": Palette.withAlpha("#f3e9d2", 0.35)})
		return action

	## A menu tile: a drawn icon on the left, a title and a line or two.
	## o: { accent?, big? }
	func tile(x: float, y: float, w: float, h: float, title: String, sub: String, foot: String, icon: Callable, o: Dictionary = {}) -> bool:
		var ui := UI.ui
		var accent: bool = o.get("accent", false)
		var big: bool = o.get("big", false)
		var clicked := ui.button("", x, y, w, h, {"accent": accent, "tooltip": [title, sub]})
		var isz := minf(h * (0.62 if big else 0.58), 96.0 if big else 60.0)
		var pad := 26.0 if big else 16.0
		icon.call(x + pad + isz / 2, y + h / 2, isz)
		var tx := x + pad * 2 + isz
		var tsize := (30.0 if h > 120 else 24.0) if big else 17.0
		var lines := 3 if foot != "" else 2
		var block := tsize + 8 + (lines - 1) * 18
		var cy := y + h / 2 - block / 2 + tsize * 0.8
		ui.text(title, tx, cy, {"size": tsize, "bold": true, "color": "#fff0cc" if accent else "#ffe9b0", "font": "Georgia, serif"})
		cy += JS.round(tsize * 0.35) + 16
		Screens.wrapText(sub, tx, cy, w - (tx - x) - 14, 13.5 if big else 12.0, "#d8cdb4")
		if foot != "":
			ui.text(foot, tx, cy + 22, {"size": 11.5, "bold": true, "color": "#e8c060"})
		return clicked

	## The soldier of your faction standing beside the realm card.
	func drawRealmSoldier(x: float, y: float, time: float, faction) -> void:
		var f := Factions.factionOf(faction)
		var soldier = f.replaces.get("militia")
		if soldier == null:
			var ex0 = f.extra[0] if f.extra.size() > 0 else null
			soldier = ex0 if ex0 != null and C.UNITS.has(ex0) and C.UNITS[ex0].trainedAt == "barracks" else "militia"
		var e := Screens._unitEntity(soldier, 0, x, y, 10, 1, PI)
		var fid: String = f.id
		Draw.setFactionResolver(func(_t): return fid)
		var ctx := UI.ui.ctx
		ctx.save()
		ctx.translate(x, y)
		ctx.scale(2.2, 2.2)
		ctx.translate(-x, -y)
		Draw.drawUnit(ctx, e, time, 0)  # a preview is never worth a crash
		ctx.restore()
		Draw.setFactionResolver(null)


# ------------------------------------------------------------------ setup --

class SetupScreen extends RefCounted:
	var config: Dictionary = Screens.defaultSkirmishConfig()

	## How far the setup panels are scrolled, and how tall they were last frame.
	var scroll := 0.0
	var contentH := 0.0
	## Which team preset the roster follows, so adding or removing a seat keeps
	## the shape ("2 teams" stays two teams). Editing a seat by hand makes it
	## "custom", and resizing then leaves the teams alone.
	## "ffa" | 2 | 3 | 4 | "coop" | "custom"
	var teamPreset: Variant = "ffa"
	var previews := {}  ## faction id -> { tc: Entity, unit: Entity }

	## The faction a locked card asked about, for the Factions book to open on.
	var bookFocus := ""
	var openBook := false

	## The Skirmish setup, in two organised columns: the battle on the left
	## (where, how, and the options), the people in it on the right (a roster
	## with a row per seat — colour, faction, difficulty, team — and your own
	## realm under it). One scroll region, and an action bar pinned at the
	## bottom so "To Battle!" is always in reach.
	## Returns "start" | "spectate" | "back" | "factions" | null.
	func draw(W: float, H: float, time: float, profile: Profile) -> Variant:
		var ui := UI.ui
		var PAL: Dictionary = C.PAL
		Screens.drawMenuBackground(W, H, time)
		var ctx := ui.ctx
		ctx.fillStyle = "rgba(10, 8, 4, 0.35)"
		ctx.fillRect(0, 0, W, H)
		syncTeams()

		var FOOTER := 76.0
		var top := 104.0
		var viewH := maxf(120, H - top - FOOTER)
		var wide := W >= 1180
		var outer := minf(W - 64, 1440)
		var x0 := JS.round(W / 2 - outer / 2)
		var gap := 20.0
		var leftW := JS.round(outer * 0.42) if wide else outer
		var rightW := outer - leftW - gap if wide else outer
		var rx := x0 + leftW + gap if wide else x0

		# Header: title on the left, the match at a glance on the right.
		ui.text("Skirmish", x0, 56, {"size": 36, "bold": true, "color": "#ffe9b0", "font": "Georgia, serif"})
		ui.text("Set the field, choose your sides, take the field.", x0 + 2, 84, {"size": 13, "color": "#c9bea3"})
		var mapName := ""
		var preset = null
		for pp in MapGen.PRESETS:
			if pp.id == config.presetId:
				preset = pp
				break
		if preset != null:
			mapName = preset.name
		elif config.presetId == "random":
			mapName = "Random battlefield"
		else:
			for m in CustomMaps.listCustomMaps():
				if m.id == config.presetId:
					mapName = m.name
					break
		ui.text("%s  ·  %s  ·  %s" % [mapName, Teams.formatLabel(config.teams), modeName()], x0 + outer, 60, {
			"align": "right", "size": 15, "bold": true, "color": "#e2c889",
		})

		var maxScroll := maxf(0, contentH - viewH)
		if ui.wheel and ui.my > top and ui.my < top + viewH:
			scroll = maxf(0, minf(maxScroll, scroll + ui.wheel * 0.6))
		scroll = minf(scroll, maxScroll)
		ui.pushScroll(scroll, {"x": 0, "y": top, "w": W, "h": viewH})
		var y0 := top + 6

		var leftEnd := drawBattleColumn(x0, y0, leftW)
		var rightTop := y0 if wide else leftEnd + 16
		rightTop = drawRoster(rx, rightTop, rightW, profile)
		var rightEnd := drawFactionPanel(rx, rightTop + 16, rightW, profile)
		contentH = maxf(leftEnd, rightEnd) - top + 16
		ui.popScroll()
		ui.scrollbar(x0 + outer + 8, top, viewH, scroll, contentH)

		# ---- pinned action bar ----
		var fy := H - FOOTER
		var fg := ctx.createLinearGradient(0, fy, 0, H)
		fg.addColorStop(0, "rgba(14, 10, 6, 0.9)")
		fg.addColorStop(1, "rgba(8, 6, 3, 0.96)")
		ctx.fillStyle = fg
		ctx.fillRect(0, fy, W, FOOTER)
		ctx.fillStyle = Palette.withAlpha(PAL.uiAccent, 0.3)
		ctx.fillRect(0, fy, W, 1)
		var by2 := fy + 16
		var action = "factions" if openBook else null
		openBook = false
		if ui.button("⟵  Back", x0, by2, 130, 44, {"size": 15}):
			action = "back"
		var valid := Teams.teamsValid(config.teams)
		if not valid:
			ui.text("Everyone is on one team — split them into at least two sides.", W / 2, by2 + 22, {"align": "center", "size": 13, "color": PAL.uiBad})
		elif contentH > viewH:
			ui.text("scroll for more", x0 + 146, by2 + 22, {"size": 11, "color": "#8f8770"})
		if ui.button("👁  Watch", x0 + outer - 360, by2, 130, 44, {"size": 15, "disabled": not valid, "tooltip": ["Spectate an AI vs AI battle", "Every seat is played by the AI — sit back and watch."]}):
			action = "spectate"
		if ui.button("⚔  To Battle!", x0 + outer - 220, by2, 220, 44, {"accent": true, "size": 18, "disabled": not valid}):
			action = "start"
		return action

	func modeName() -> String:
		return {"conquest": "Conquest", "survival": "Survival", "koth": "King of the Hill", "regicide": "Regicide"}.get(config.mode, "Conquest")

	## Keep the per-seat arrays in step with the player count and mode.
	func syncTeams() -> void:
		var n := int(config.players)
		if config.get("teams") == null or (config.teams as Array).size() != n:
			config.teams = Teams.resizeTeams(JS.nz(config.get("teams"), []), n) if teamPreset is String and teamPreset == "custom" else presetTeams(n)
		# Survival is everyone together against the waves.
		if config.mode == "survival":
			var all: Array = []
			for i in n:
				all.append(1)
			config.teams = all

	## `this.teamPreset === id`, for presets that are a word or a count.
	static func _samePreset(a, b) -> bool:
		if (a is int or a is float) and (b is int or b is float):
			return float(a) == float(b)
		return a is String and b is String and a == b

	func presetTeams(n: int) -> Array:
		var p = teamPreset
		if p is String and (p == "ffa" or p == "custom"):
			return Teams.freeForAll(n)
		if p is String and p == "coop":
			return Teams.coopTeams(n, maxi(0, floori(n / 2.0) - 1))
		return Teams.blockTeams(n, int(p))

	## A section heading with a gold rule, used down both columns.
	func heading(x: float, y: float, w: float, text: String, sub = null) -> void:
		var ui := UI.ui
		var ctx := ui.ctx
		ui.text(text, x, y, {"size": 17, "bold": true, "color": C.PAL.uiAccent, "font": "Georgia, serif"})
		ctx.font = "bold 17px Georgia, serif"
		var tw := ctx.measureText(text).width
		if sub:
			ui.text(sub, x + tw + 12, y + 1, {"size": 12, "color": "#9b927c"})
			ctx.font = "12px 'Trebuchet MS', sans-serif"
		ctx.strokeStyle = Palette.withAlpha(C.PAL.uiAccent, 0.22)
		ctx.lineWidth = 1
		ctx.beginPath(); ctx.moveTo(x, y + 14); ctx.lineTo(x + w, y + 14); ctx.stroke()

	# ------------------------------------------------------------ the battle --

	func drawBattleColumn(x: float, y: float, w: float) -> float:
		var ui := UI.ui
		var ctx := ui.ctx
		var PAL: Dictionary = C.PAL
		# Battlefield: a two-across grid of map cards. Published maps sit alongside
		# the presets, filtered to the ones that allow this match; drafts stay in
		# the editor until their author publishes them.
		var custom := CustomMaps.mapPool(config.mode, int(config.players))
		var drafts := CustomMaps.listCustomMaps().filter(func(m): return not m.get("published")).size()
		var cards: Array = []
		for pp in MapGen.PRESETS:
			cards.append({"id": pp.id, "name": pp.name, "desc": pp.desc, "map": null})
		cards.append({"id": "random", "name": "Random", "desc": "Any battlefield here, published maps included — rolled from the seed." if custom.size() else "A different preset every match, rolled from the seed.", "map": null})
		for m in custom:
			var d: String = String(JS.nz(m.get("desc"), "")).strip_edges()
			if d == "":
				d = "%s×%s · %s–%s players%s" % [JS.num(m.cols), JS.num(m.rows), JS.num(m.minPlayers), JS.num(m.maxPlayers), " · always nomad" if m.get("nomad") == "forced" else ""]
			cards.append({"id": m.id, "name": m.name, "desc": d, "map": m})
		if String(config.presetId).begins_with("custom_") and not custom.any(func(m): return m.id == config.presetId):
			config.presetId = "open_plains"
		var perRow := 2 if w >= 420 else 1
		var cardH := 70.0
		var cgap := 10.0
		var rows := ceili(float(cards.size()) / perRow)
		var panelH := 52 + rows * (cardH + cgap) + (22 if drafts else 0)
		ui.panel(x, y, w, panelH)
		heading(x + 18, y + 24, w - 36, "Battlefield")
		if drafts:
			ui.text("%d draft map%s in the Map Editor — publish %s to play here." % [drafts, "" if drafts == 1 else "s", "it" if drafts == 1 else "them"],
				x + 18, y + panelH - 16, {"size": 11.5, "color": "#a89f88"})
		var cardW := (w - 36 - (perRow - 1) * cgap) / perRow
		for i in cards.size():
			var c: Dictionary = cards[i]
			var cx := x + 18 + (i % perRow) * (cardW + cgap)
			var cy := y + 46 + floori(float(i) / perRow) * (cardH + cgap)
			var sel: bool = config.presetId == c.id
			if ui.button("", cx, cy, cardW, cardH, {"accent": sel, "tooltip": [c.name, c.desc]}):
				config.presetId = c.id
				audio.play("ui")
			var thumb := cardH - 16 if c.map != null else 0.0
			if c.map != null:
				MapThumb.drawMapThumbnail(ctx, cx + 8, cy + 8, thumb, c.map, {"spawns": true, "resources": true})
			var tx := cx + 12 + (thumb + 6 if thumb else 0.0)
			ui.text(c.name, tx, cy + 18, {"size": 14.5, "bold": true, "color": "#ffe9b0" if sel else PAL.uiParchment})
			if c.map != null:
				var author = c.map.get("author")
				ui.text(("BY %s" % String(author).substr(0, 12).to_upper()) if author else "PUBLISHED", cx + cardW - 10, cy + 18, {"align": "right", "size": 9.5, "bold": true, "color": "#7fb0e8"})
			Screens.wrapText(c.desc, tx, cy + 38, cardW - (tx - cx) - 10, 11.5, "#b8ad92")
		y += panelH + 16

		# Mode.
		ui.panel(x, y, w, 96)
		heading(x + 18, y + 24, w - 36, "Mode")
		var modes := [
			["conquest", "Conquest", "Destroy every enemy. The classic skirmish."],
			["survival", "Survival", "Co-op: every seat together against escalating waves."],
			["koth", "King of the Hill", "Hold the centre for 5 cumulative minutes to win."],
			["regicide", "Regicide", "Each side has a King — slay theirs, protect yours."],
		]
		var mw := (w - 36 - 3 * 8) / 4
		for i in modes.size():
			var md: Array = modes[i]
			var label: String = md[1]
			if ui.button("KotH" if mw < 110 and label.length() > 10 else label, x + 18 + i * (mw + 8), y + 46, mw, 34, {
				"accent": config.mode == md[0], "size": 13, "tooltip": [label, md[2]],
			}):
				config.mode = md[0]
				audio.play("ui")
		y += 96 + 16

		# Options: seed, AI default, ranked, nomad.
		var optH := 214.0
		ui.panel(x, y, w, optH)
		heading(x + 18, y + 24, w - 36, "Options")
		ui.text("Seed", x + 18, y + 58, {"size": 13, "bold": true, "color": "#d8cdb4"})
		ui.text(JS.num(config.seed), x + 110, y + 58, {"size": 13.5, "color": "#e9dcc0"})
		if ui.button("New seed", x + w - 18 - 110, y + 45, 110, 26, {"size": 12}):
			config.seed = RNG.randomSeed()
			audio.play("ui")
		ui.text("Every AI", x + 18, y + 96, {"size": 13, "bold": true, "color": "#d8cdb4"})
		var DIDS: Array = C.DIFFICULTY_IDS
		var dw := (w - 36 - 92 - (DIDS.size() - 1) * 6) / DIDS.size()
		for i in DIDS.size():
			var id: String = DIDS[i]
			var d: Dictionary = C.DIFFICULTIES[id]
			if ui.button(d.name, x + 110 + i * (dw + 6), y + 82, dw, 28, {"accent": config.difficulty == id, "size": 12, "tooltip": [d.name, d.desc, "Sets every AI seat; change one seat in the roster."]}):
				config.difficulty = id
				config.aiDifficulties = []
				audio.play("ui")
		toggle(x, y + 124, config.fairMode, "Ranked", "All units Common, +25% rewards — your unboxed variants sit out.", "fairMode")
		toggle(x, y + 166, config.nomad, "Nomad start", "No Town Centre — settle wherever you land.", "nomad")
		return y + optH

	## The TS `toggle` closure: a check box that flips `config[key]`.
	func toggle(x: float, ty: float, on: bool, title: String, sub: String, key: String) -> void:
		var ui := UI.ui
		if ui.button("✓" if on else "", x + 18, ty, 28, 28, {"accent": on, "size": 15}):
			config[key] = not config[key]
			audio.play("ui")
		ui.text(title, x + 58, ty + 9, {"size": 13.5, "bold": true, "color": C.PAL.uiParchment})
		ui.text(sub, x + 58, ty + 25, {"size": 11.5, "color": "#a89f88"})

	# ------------------------------------------------------------- the roster --

	## One row per seat: its colour, who plays it, their faction, their skill,
	## and their team — every team picked here rather than implied. Presets fill
	## the teams in organised blocks; any seat can then be moved.
	func drawRoster(x: float, y: float, w: float, profile: Profile) -> float:
		var ui := UI.ui
		var ctx := ui.ctx
		var PAL: Dictionary = C.PAL
		var n := int(config.players)
		var survival: bool = config.mode == "survival"
		var rowH := 46.0
		var panelH := 124 + n * (rowH + 6) + 8
		var teams: Array = config.teams
		ui.panel(x, y, w, panelH)
		heading(x + 18, y + 24, w - 36, "Players", Teams.formatLabel(teams))

		# Seat count.
		var cx := x + w - 18
		if ui.button("+", cx - 30, y + 10, 30, 28, {"size": 16, "disabled": n >= 8, "tooltip": ["Add a seat"]}):
			config.players = n + 1
			audio.play("ui")
		ui.text("%d" % n, cx - 48, y + 24, {"align": "center", "size": 16, "bold": true, "color": "#ffe9b0"})
		if ui.button("–", cx - 96, y + 10, 30, 28, {"size": 16, "disabled": n <= 2, "tooltip": ["Remove a seat"]}):
			config.players = n - 1
			audio.play("ui")

		# Team presets.
		var presets := [
			["ffa", "Free-for-all", "Every seat for itself."],
			[2, "2 teams", "Seats split into two blocks: 1–4 against 5–8, and so on."],
			[3, "3 teams", "Three blocks, as even as the seats allow."],
			[4, "4 teams", "Four blocks — two-a-side four ways with 8 seats."],
			["coop", "Co-op vs AI", "You and the first half of the seats against the rest."],
		]
		var pw := (w - 36 - (presets.size() - 1) * 6) / presets.size()
		for i in presets.size():
			var pr: Array = presets[i]
			var id = pr[0]
			var can: bool = not survival and (not (id is int) or id <= n) and not (id is String and id == "coop" and n < 3)
			if ui.button(pr[1], x + 18 + i * (pw + 6), y + 46, pw, 28, {"accent": _samePreset(teamPreset, id), "size": 12, "disabled": not can, "tooltip": [pr[1], pr[2]]}):
				teamPreset = id
				config.teams = presetTeams(n)
				teams = config.teams
				audio.play("ui")

		# Column heads.
		var cols := {"seat": x + 18, "name": x + 50, "faction": x + w * 0.36, "skill": x + w * 0.53, "team": x + w * 0.68}
		var hy := y + 96
		for hd in [["PLAYER", cols.name], ["FACTION", cols.faction], ["SKILL", cols.skill], ["TEAM", cols.team]]:
			ui.text(hd[0], hd[1], hy, {"size": 10.5, "bold": true, "color": "#9b927c"})

		var teamColors: Array = Teams.TEAM_COLORS
		for t in n:
			var ry := y + 110 + t * (rowH + 6)
			var me := t == 0
			var seatTeam := int(teams[t]) if t < teams.size() else 0
			# Row ground; yours picked out, and a stripe in its team's colour so the
			# sides read at a glance down the list.
			ctx.fillStyle = Palette.withAlpha(PAL.uiAccent, 0.14) if me else ("rgba(255,255,255,0.035)" if t % 2 else "rgba(255,255,255,0.015)")
			ctx.beginPath(); ctx.roundRect(x + 12, ry, w - 24, rowH, 6); ctx.fill()
			if seatTeam > 0:
				ctx.fillStyle = teamColors[seatTeam]
				ctx.beginPath(); ctx.roundRect(x + 12, ry, 4, rowH, [6, 0, 0, 6]); ctx.fill()
			# Seat colour.
			var col := Palette.teamColor(t)
			ctx.fillStyle = col.main
			ctx.beginPath(); ctx.roundRect(cols.seat, ry + 11, 22, 24, 5); ctx.fill()
			ui.text(str(t + 1), cols.seat + 11, ry + 23.5, {"align": "center", "size": 12, "bold": true, "color": "#fff"})
			# Who.
			if me:
				var cmdr = C.COMMANDERS.get(config.commander if config.commander else profile.data.commander)
				ui.text("You", cols.name, ry + 16, {"size": 14, "bold": true, "color": "#ffe9b0"})
				ui.text(("%s, %s" % [cmdr.name, cmdr.title]) if cmdr != null else "Your realm", cols.name, ry + 33, {"size": 11, "color": "#a89f88"})
			else:
				ui.text("AI %d" % (t + 1), cols.name, ry + 16, {"size": 14, "bold": true, "color": PAL.uiParchment})
				ui.text(col.name, cols.name, ry + 33, {"size": 11, "color": Palette.withAlpha(col.light, 0.9)})
			# Faction: yours is chosen in the panel below; a bot's cycles here.
			var fw: float = cols.skill - cols.faction - 10
			if me:
				var f := Factions.factionOf(config.faction if config.faction else profile.data.get("faction"))
				ui.text(Screens._strip_the(f.name), cols.faction + 6, ry + rowH / 2, {"size": 13, "bold": true, "color": f.color})
			else:
				var aif: Array = config.aiFactions
				var cur: String = aif[t] if t < aif.size() and aif[t] != null else ""
				var f = C.FACTIONS.get(cur) if cur != "" else null
				if ui.button(Screens._strip_the(f.name) if f != null else "Random", cols.faction, ry + 9, fw, 28, {
					"size": 12, "accent": f != null, "tooltip": ["Seat %d's faction" % (t + 1), "Click to cycle. Random is drawn from the match seed."],
				}):
					var order: Array = [""] + C.FACTION_IDS
					while aif.size() <= t:
						aif.append("")
					aif[t] = order[(order.find(cur) + 1) % order.size()]
					audio.play("ui")
			# Skill: a bot's personality.
			var sw: float = cols.team - cols.skill - 10
			if not me:
				var aid: Array = config.aiDifficulties
				var did: String = aid[t] if t < aid.size() and aid[t] != null else config.difficulty
				var d: Dictionary = C.DIFFICULTIES[did]
				if ui.button(d.name, cols.skill, ry + 9, sw, 28, {"size": 12, "accent": did != config.difficulty, "tooltip": ["Seat %d: %s" % [t + 1, d.name], d.desc, "Click to cycle."]}):
					var DIDS: Array = C.DIFFICULTY_IDS
					var next: String = DIDS[(DIDS.find(did) + 1) % DIDS.size()]
					while aid.size() <= t:
						aid.append(config.difficulty)
					aid[t] = next
					audio.play("ui")
			else:
				ui.text("—", cols.skill + sw / 2, ry + rowH / 2, {"align": "center", "size": 13, "color": "#6f6a5c"})
			# Team: "–" alone, or 1..k with allies. Four to start with, and one more
			# than the highest in use — so up to as many teams as there are seats,
			# without nine buttons on every row.
			var used := 0
			for tv in teams:
				used = maxi(used, int(tv))
			var tmax := mini(mini(n, maxi(4, used + 1)), 8)
			var tw := maxf(20, minf(30, (x + w - 20 - cols.team) / (tmax + 1) - 3))
			for k in tmax + 1:
				var bx: float = cols.team + k * (tw + 3)
				var on := seatTeam == k
				var tcol = teamColors[k]
				if ui.button("–" if k == 0 else str(k), bx, ry + 10, tw, 26, {
					"size": 12, "accent": on, "disabled": survival,
					"tooltip": ["Alone", "This seat fights for itself."] if k == 0 else ["Team %d" % k, "Seats on the same team are allies — shared vision, no friendly fire."],
				}):
					teams[t] = k
					seatTeam = k
					teamPreset = "custom"
					audio.play("ui")
				if on and k > 0:
					ctx.fillStyle = tcol
					ctx.fillRect(bx + 3, ry + 32, tw - 6, 3)
		if survival:
			ui.text("Survival: every seat stands together against the waves.", x + 18, y + panelH - 10, {"size": 11.5, "color": "#a89f88"})
		return y + panelH

	## Your realm: six faction cards, each with the faction's own Town Centre
	## and signature soldier drawn live in its style, then what it is good at,
	## what it pays for it, and your commander.
	func drawFactionPanel(x0: float, y: float, colW: float, profile: Profile) -> float:
		var ui := UI.ui
		var PAL: Dictionary = C.PAL
		# Only a faction you own can be yours.
		if not config.faction or not profile.ownsFaction(config.faction):
			var pf = profile.playableFaction()
			config.faction = pf if pf != null else C.DEFAULT_FACTION
		if not profile.ownsCommander(config.commander):
			var cs: Array = profile.data.commanders
			config.commander = profile.data.commander if profile.data.commander else (cs[0] if cs.size() else "")
		var f := Factions.factionOf(config.faction)
		var cardGap := 8.0
		var FIDS: Array = C.FACTION_IDS
		var cardW := floorf((colW - 36 - cardGap * (FIDS.size() - 1)) / FIDS.size())
		var cardH := 124.0
		var panelH := 58 + cardH + 176
		ui.panel(x0, y, colW, panelH)
		heading(x0 + 18, y + 24, colW - 36, "Your realm", "faction and commander")
		var ctx := ui.ctx
		var CURVE := {"early": "Strong early", "mid": "Peaks mid-game", "late": "Strong late", "steady": "Steady"}
		for i in FIDS.size():
			var id: String = FIDS[i]
			var d: Dictionary = C.FACTIONS[id]
			var cx := x0 + 18 + i * (cardW + cardGap)
			var cy := y + 46
			var sel: bool = config.faction == id
			var locked := not profile.ownsFaction(id)
			if ui.button("", cx, cy, cardW, cardH, {"accent": sel, "tooltip": [d.name, "Locked — %s ✦ renown to unlock" % JS.num(Profile.FACTION_PRICE), "Click to read about it in the Factions book."] if locked
				else [d.name, d.era, d.tagline]}):
				if locked:
					bookFocus = id
					openBook = true
				else:
					config.faction = id
					profile.selectFaction(id)
					config.commander = profile.data.commander
				audio.play("ui")
			if sel:
				ctx.strokeStyle = d.color; ctx.lineWidth = 2.5
				ctx.beginPath(); ctx.roundRect(cx + 1, cy + 1, cardW - 2, cardH - 2, 6); ctx.stroke()
			if not previews.has(id):
				previews[id] = {"tc": World.makeEntity(), "unit": World.makeEntity()}
			var pv: Dictionary = previews[id]
			var tcDef: Dictionary = C.BUILDINGS.town_center
			var tc: Entity = pv.tc
			tc.kind = T.Kind.Building; tc.type = "town_center"; tc.team = T.Team.Player; tc.x = 0; tc.y = 0
			tc.radius = (tcDef.tiles * 32) / 2; tc.hp = tcDef.hp; tc.maxHp = tcDef.hp; tc.buildState = 0; tc.buildProgress = 1
			var reps: Array = (d.replaces as Dictionary).values()
			var soldier: String = reps[0] if reps.size() else (d.extra[0] if d.extra.size() else "militia")
			var ud: Dictionary = C.UNITS[soldier]
			var un: Entity = pv.unit
			un.kind = T.Kind.Unit; un.type = soldier; un.team = T.Team.Player; un.x = 0; un.y = 0; un.radius = ud.radius
			un.facing = -0.5; un.hp = ud.hp; un.maxHp = ud.hp; un.animPhase = 0.3; un.attackInterval = ud.attackInterval
			Draw.setFactionResolver(func(_t): return id)
			ctx.save()
			ctx.beginPath(); ctx.rect(cx + 3, cy + 3, cardW - 6, 80); ctx.clip()
			ctx.translate(cx + cardW * 0.44, cy + 58)
			var sc := minf(0.72, cardW / 150)
			ctx.scale(sc, sc)
			Draw.drawBuilding(ctx, tc, 0, T.Team.Player)  # a preview never breaks the menu
			ctx.restore()
			ctx.save()
			ctx.translate(cx + cardW * 0.8, cy + 72)
			ctx.scale(1.6, 1.6)
			Draw.drawUnit(ctx, un, 0, 0)  # ditto
			ctx.restore()
			Draw.setFactionResolver(null)
			if locked:
				# Dimmed behind a lock, with the price.
				ctx.fillStyle = "rgba(12,9,5,0.62)"
				ctx.beginPath(); ctx.roundRect(cx + 2, cy + 2, cardW - 4, cardH - 4, 6); ctx.fill()
				var lx := cx + cardW / 2
				var ly := cy + 40
				ctx.strokeStyle = "#e8c060"; ctx.lineWidth = 3
				ctx.beginPath(); ctx.arc(lx, ly - 6, 8, PI, 0); ctx.stroke()
				ctx.fillStyle = "#e8c060"
				ctx.beginPath(); ctx.roundRect(lx - 12, ly - 6, 24, 18, 3); ctx.fill()
				ui.text("%s ✦" % JS.num(Profile.FACTION_PRICE), lx, ly + 34, {"align": "center", "size": 12, "bold": true, "color": "#e8c060"})
			ui.text(Screens._strip_the(d.name), cx + cardW / 2, cy + 98, {"align": "center", "size": 13, "bold": true, "color": "#8f8770" if locked else ("#ffe9b0" if sel else PAL.uiParchment)})
			ui.text("Locked" if locked else CURVE[d.curve], cx + cardW / 2, cy + 114, {"align": "center", "size": 10.5, "color": "#8f8770" if locked else (d.color if sel else "#9b927c")})

		var dy := y + 46 + cardH + 22
		ui.text("%s — %s" % [f.name, f.era], x0 + 18, dy, {"size": 15, "bold": true, "color": f.color})
		ui.text("“%s”" % f.tagline, x0 + 18, dy + 20, {"size": 12.5, "color": "#d8cdb4", "font": "Georgia, serif"})
		dy += 44
		var half := (colW - 48) / 2
		for i in f.strengths.size():
			ui.text("+  %s" % f.strengths[i], x0 + 18, dy + i * 18, {"size": 12.5, "color": "#9fe0a0"})
		for i in f.weaknesses.size():
			ui.text("–  %s" % f.weaknesses[i], x0 + 32 + half, dy + i * 18, {"size": 12.5, "color": "#e8a898"})

		# Commander, cycling through the ones you own; remembered per faction.
		var cy2 := y + panelH - 42
		var owned: Array = C.COMMANDER_IDS.filter(func(cid): return profile.ownsCommander(cid))
		var cur = C.COMMANDERS.get(config.commander)
		ctx.strokeStyle = Palette.withAlpha(PAL.uiAccent, 0.18)
		ctx.beginPath(); ctx.moveTo(x0 + 18, cy2 - 12); ctx.lineTo(x0 + colW - 18, cy2 - 12); ctx.stroke()
		ui.text("Commander", x0 + 18, cy2 + 14, {"size": 13, "bold": true, "color": PAL.uiAccent})
		if owned.size() > 1:
			if ui.button("‹", x0 + 104, cy2, 28, 28, {}):
				cycle(-1, owned, profile)
			if ui.button("›", x0 + colW - 46, cy2, 28, 28, {}):
				cycle(1, owned, profile)
		if cur != null:
			ui.text("%s — %s" % [cur.name, cur.title], x0 + 142, cy2 + 8, {"size": 14, "bold": true, "color": cur.color})
			var perks := "  •  ".join(PackedStringArray(Commanders.commanderPerks(cur)))
			var oath: String = ("  •  Favours the Oath of the %s" % Screens._cap(cur.oath)) if cur.get("oath") else ""
			ui.text(perks + oath, x0 + 142, cy2 + 26, {"size": 11.5, "color": "#c9bea3"})
		return y + panelH

	## The TS `cycle` closure.
	func cycle(dir: int, owned: Array, profile: Profile) -> void:
		var i := owned.find(config.commander)
		var next: String = owned[(i + dir + owned.size()) % owned.size()]
		config.commander = next
		profile.selectCommander(next)
		profile.pairCommander(config.faction if config.faction else C.DEFAULT_FACTION, next)
		audio.play("ui")


# ----------------------------------------------------------------- armory --

class ArmoryScreen extends RefCounted:
	var tab := "chests"  ## "chests" | "boons" | "collection" | "commanders"
	# Chest-opening overlay state.
	var opening = null  ## a ChestDef, or null
	var result = null  ## a RollResult, or null
	# Boon-cache opening overlay (same lottery spinner, boon payload).
	var boonOpening := false
	var boonResult = null  ## a BoonRoll, or null
	var tickets: Array = []  ## of { rarity, unitName }
	var spinT := 0.0 # 0..1 animation progress
	var spinDur := 4.2
	var revealed := false
	var claimed := false
	var lastTickIndex := -1
	var particles := Particles.new(300)

	## Returns "back" or null.
	func draw(W: float, H: float, time: float, dt: float, profile: Profile) -> Variant:
		var ui := UI.ui
		var PAL: Dictionary = C.PAL
		Screens.drawMenuBackground(W, H, time)
		ui.text("The Armory", W / 2, 56, {
			"align": "center", "size": 34, "bold": true, "color": "#ffe9b0", "font": "Georgia, serif",
		})
		ui.text("✦ %s Renown      ⚔ %s Valor" % [JS.num(profile.data.renown), JS.num(profile.data.valor)], W / 2, 92, {
			"align": "center", "size": 17, "bold": true, "color": PAL.uiAccent,
		})

		var action = null
		if opening == null and not boonOpening:
			# Tabs.
			var tabW := 138.0
			var tabs := [["chests", "War Chests"], ["boons", "Boons"], ["commanders", "Commanders"], ["collection", "Collection"]]
			var tx := W / 2 - (tabs.size() * (tabW + 8) - 8) / 2
			for t in tabs:
				if ui.button(t[1], tx, 112, tabW, 36, {"accent": tab == t[0]}):
					tab = t[0]
					audio.play("ui")
				tx += tabW + 8

			if tab == "chests":
				drawChests(W, H, profile)
			elif tab == "boons":
				drawBoons(W, H, profile)
			elif tab == "commanders":
				drawCommanders(W, H, profile)
			else:
				drawCollection(W, H, profile)

			if ui.button("⟵ Back", 24, H - 68, 130, 44, {"size": 15}):
				action = "back"
		elif opening != null:
			drawOpening(W, H, dt, profile)
		else:
			drawBoonOpening(W, H, dt, profile)
		return action

	static func _weights(chest: Dictionary) -> Array:
		var bias: Array = chest.rarityBias
		var out: Array = []
		var R: Array = C.RARITIES
		for ri in R.size():
			var b = bias[ri] if ri < bias.size() and bias[ri] != null else 1.0
			out.append(float(R[ri].weight) * float(b))
		return out

	func drawChests(W: float, H: float, profile: Profile) -> void:
		var ui := UI.ui
		var PAL: Dictionary = C.PAL
		var CH: Array = C.CHESTS
		var n := CH.size()
		var cw := 250.0
		var gap := 28.0
		var x0 := W / 2 - (n * cw + (n - 1) * gap) / 2
		var y0 := 180.0
		for i in n:
			var chest: Dictionary = CH[i]
			var x := x0 + i * (cw + gap)
			ui.panel(x, y0, cw, 320, {"light": true})
			# chest art
			Screens.drawChestArt(x + cw / 2, y0 + 86, 1 + i * 0.18, i)
			ui.text(chest.name, x + cw / 2, y0 + 170, {"align": "center", "size": 17, "bold": true, "color": PAL.uiAccent})
			Screens.wrapText(chest.desc, x + 18, y0 + 196, cw - 36, 12, "#bdb49a")
			# odds readout
			var oy := y0 + 244
			var weights := _weights(chest)
			var total := 0.0
			for wv in weights:
				total += wv
			var interesting := [2, 3, 4, 5]
			var ox := x + 18
			for ri in interesting:
				var pct: float = (weights[ri] / total) * 100
				var r: Dictionary = C.RARITIES[ri]
				ui.text("%s%%" % (JS.to_fixed(pct, 0) if pct >= 10 else JS.to_fixed(pct, 1)), ox, oy, {"size": 11, "color": r.color, "bold": true})
				ox += 54
			var afford: bool = float(profile.data.renown) >= float(chest.cost)
			if ui.button("Open — %s ✦" % JS.num(chest.cost), x + 24, y0 + 266, cw - 48, 38, {
				"accent": afford,
				"disabled": not afford,
				"size": 15,
			}):
				startOpening(chest, profile)

	func startOpening(chest: Dictionary, profile: Profile) -> void:
		if not profile.spendRenown(chest.cost):
			return
		profile.data.openedChests = float(profile.data.get("openedChests", 0)) + 1
		var rng := RNG.new(RNG.randomSeed())
		result = Chests.rollChest(chest, profile.ownedSetSnapshot(), rng)
		opening = chest
		spinT = 0
		revealed = false
		claimed = false
		lastTickIndex = -1
		# Build the ticker strip: 60 tickets, winner placed at index 52.
		tickets = []
		var weights := _weights(chest)
		for i in 60:
			var rarity := rng.weightedIndex(weights)
			var unit: String = rng.pick(C.COLLECTIBLE_UNIT_IDS)
			tickets.append({"rarity": rarity, "unitName": C.UNITS[unit].name})
		tickets[52] = {"rarity": result.rarity, "unitName": result.variant.unitName}
		audio.play("ui")

	## The ticker strip both overlays spin (the two TS copies, shared).
	func _spinStrip(W: float, H: float, dt: float, title: String, nameSize: float, rarity: int) -> void:
		var ui := UI.ui
		var ctx := ui.ctx
		var PAL: Dictionary = C.PAL
		spinT = minf(1, spinT + dt / spinDur)
		var ease := 1 - pow(1 - spinT, 3.2) # strong deceleration
		var ticketW := 132.0
		# Land ticket 52 dead-center.
		var finalOffset := 52 * ticketW
		var startOffset := finalOffset - ticketW * 34
		var offset := startOffset + (finalOffset - startOffset) * ease

		# tick sound as tickets pass the needle
		var idx := floori(offset / ticketW)
		if idx != lastTickIndex:
			lastTickIndex = idx
			audio.play("tick")

		var cy := H / 2
		ui.text(title, W / 2, cy - 130, {"align": "center", "size": 20, "bold": true, "color": PAL.uiAccent})
		# ticket strip
		ctx.save()
		ctx.beginPath()
		ctx.rect(W / 2 - 420, cy - 70, 840, 140)
		ctx.clip()
		for i in tickets.size():
			var t: Dictionary = tickets[i]
			var x := W / 2 + (i * ticketW - offset)
			if x < W / 2 - 500 or x > W / 2 + 500:
				continue
			var r := Rarity.rarityByIndex(t.rarity)
			ctx.fillStyle = Palette.shade("#2a2218", 0.05)
			ctx.beginPath()
			ctx.roundRect(x - ticketW / 2 + 5, cy - 60, ticketW - 10, 120, 7)
			ctx.fill()
			ctx.strokeStyle = r.color
			ctx.lineWidth = 2.5
			ctx.stroke()
			ctx.fillStyle = Palette.withAlpha(r.color, 0.18)
			ctx.fill()
			ui.text(t.unitName, x, cy - 14, {"align": "center", "size": nameSize, "bold": true})
			ui.text(r.name, x, cy + 16, {"align": "center", "size": 11, "color": r.color, "bold": true})
		ctx.restore()
		# needle
		ctx.strokeStyle = "#ffe9b0"
		ctx.lineWidth = 3
		ctx.beginPath()
		ctx.moveTo(W / 2, cy - 78)
		ctx.lineTo(W / 2, cy + 78)
		ctx.stroke()

		if spinT >= 1:
			revealed = true
			audio.play("reveal")
			if rarity >= 3:
				audio.play("levelup")
			var rr := Rarity.rarityByIndex(rarity)
			particles.burst(W / 2, H / 2, 26 + rarity * 22, rr.color, 260, {
				"maxLife": 1.2, "size": 3.4, "gravity": 160, "glow": rarity >= 4,
			})
		# click to skip
		if ui.clicked:
			spinT = 1

	## The reveal card's frame (both overlays).
	func _revealCard(x: float, y: float, cw: float, chH: float, color, rarity: int) -> void:
		var ctx := UI.ui.ctx
		ctx.save()
		ctx.shadowColor = color
		ctx.shadowBlur = 42 if rarity >= 3 else 18
		ctx.fillStyle = "#241d12"
		ctx.beginPath()
		ctx.roundRect(x, y, cw, chH, 12)
		ctx.fill()
		ctx.restore()
		ctx.strokeStyle = color
		ctx.lineWidth = 3
		ctx.beginPath()
		ctx.roundRect(x, y, cw, chH, 12)
		ctx.stroke()

	func _drawParticles(dt: float) -> void:
		var ctx := UI.ui.ctx
		particles.update(dt)
		for p in particles.pool:
			if not p.active:
				continue
			ctx.globalAlpha = p.life / p.maxLife
			ctx.fillStyle = p.color
			ctx.beginPath()
			ctx.arc(p.x, p.y, p.size * (p.life / p.maxLife), 0, PI * 2)
			ctx.fill()
		ctx.globalAlpha = 1

	func drawOpening(W: float, H: float, dt: float, profile: Profile) -> void:
		var ui := UI.ui
		var ctx := ui.ctx
		var PAL: Dictionary = C.PAL
		var res: Dictionary = result
		ctx.fillStyle = "rgba(8, 6, 3, 0.78)"
		ctx.fillRect(0, 0, W, H)

		if not revealed:
			_spinStrip(W, H, dt, "Opening " + opening.name + "…", 14, int(res.rarity))
		else:
			# Reveal card.
			var r := Rarity.rarityByIndex(res.rarity)
			var cw := 360.0
			var chH := 300.0
			var x := W / 2 - cw / 2
			var y := H / 2 - chH / 2 - 20
			_revealCard(x, y, cw, chH, r.color, int(res.rarity))

			ui.text(String(r.name).to_upper(), W / 2, y + 40, {"align": "center", "size": 16, "bold": true, "color": r.color})
			ui.text(res.variant.name, W / 2, y + 84, {"align": "center", "size": 24, "bold": true, "color": "#ffe9b0"})
			ui.text(res.variant.unitName + " variant", W / 2, y + 116, {"align": "center", "size": 14, "color": "#bdb49a"})

			# stat preview
			var hpMult: int = [100, 106, 113, 122, 134, 150][int(res.rarity)]
			var atkMult: int = [100, 105, 111, 119, 130, 145][int(res.rarity)]
			ui.text("❤ %d%%    ⚔ %d%%" % [hpMult, atkMult], W / 2, y + 156, {"align": "center", "size": 16, "bold": true})

			if res.duplicate:
				ui.text("Duplicate — refunded %s ✦" % JS.num(res.refund), W / 2, y + 196, {
					"align": "center", "size": 14, "color": PAL.uiAccent,
				})

			_drawParticles(dt)

			if not claimed and ui.button("Claim", W / 2 - 80, y + chH - 64, 160, 42, {"accent": true, "size": 17}):
				claimed = true
				if res.duplicate:
					profile.addRenown(res.refund)
					audio.play("coin")
				else:
					profile.grant(res.variant.key)
					audio.play("complete")
				profile.save()
				opening = null
				result = null

	func startBoonOpening(profile: Profile) -> void:
		if not profile.spendValor(BoonCache.BOON_CACHE_COST):
			return
		var rng := RNG.new(RNG.randomSeed())
		boonResult = BoonCache.rollBoonCache(profile.boonSetSnapshot(), rng)
		boonOpening = true
		spinT = 0
		revealed = false
		claimed = false
		lastTickIndex = -1
		# Ticker strip: 60 boon tickets at advanced rarities, winner at index 52.
		tickets = []
		var advW: Array = (C.RARITIES as Array).slice(1).map(func(r): return r.weight) # caches never roll Common
		for i in 60:
			var rarity := rng.weightedIndex(advW) + 1
			var id: String = rng.pick(C.BOON_IDS)
			tickets.append({"rarity": rarity, "unitName": C.BOONS_BY_ID[id].name})
		tickets[52] = {"rarity": boonResult.rarity, "unitName": boonResult.name}
		audio.play("ui")

	func drawBoonOpening(W: float, H: float, dt: float, profile: Profile) -> void:
		var ui := UI.ui
		var ctx := ui.ctx
		var PAL: Dictionary = C.PAL
		var res: Dictionary = boonResult
		ctx.fillStyle = "rgba(8, 6, 3, 0.78)"
		ctx.fillRect(0, 0, W, H)

		if not revealed:
			_spinStrip(W, H, dt, "Opening Warband Cache…", 13, int(res.rarity))
		else:
			var r := Rarity.rarityByIndex(res.rarity)
			var def: Dictionary = C.BOONS_BY_ID[res.boonId]
			var cw := 380.0
			var chH := 300.0
			var x := W / 2 - cw / 2
			var y := H / 2 - chH / 2 - 20
			_revealCard(x, y, cw, chH, r.color, int(res.rarity))

			ui.text(String(r.name).to_upper(), W / 2, y + 40, {"align": "center", "size": 16, "bold": true, "color": r.color})
			ui.text(res.name, W / 2, y + 84, {"align": "center", "size": 24, "bold": true, "color": "#ffe9b0"})
			ui.text("%s boon" % Screens._cap(def.category), W / 2, y + 114, {"align": "center", "size": 14, "color": "#bdb49a"})
			Screens.wrapText(Boons.detail(res.boonId, int(res.rarity)), x + 28, y + 150, cw - 56, 14, "#d8cdb4")

			if res.duplicate:
				ui.text("Duplicate — refunded %s ⚔" % JS.num(res.refund), W / 2, y + 224, {"align": "center", "size": 14, "color": PAL.uiAccent})
			else:
				ui.text("Upgraded! Equip it in your Battle Plan.", W / 2, y + 224, {"align": "center", "size": 13, "color": "#9fd08a"})

			_drawParticles(dt)

			if not claimed and ui.button("Claim", W / 2 - 80, y + chH - 64, 160, 42, {"accent": true, "size": 17}):
				claimed = true
				if res.duplicate:
					profile.addValor(res.refund)
					audio.play("coin")
				else:
					profile.grantBoon(res.key)
					audio.play("complete")
				profile.save()
				boonOpening = false
				boonResult = null

	func drawBoons(W: float, H: float, profile: Profile) -> void:
		var ui := UI.ui
		var PAL: Dictionary = C.PAL
		var colW := minf(960, W - 80)
		var x0 := W / 2 - colW / 2
		var y := 162.0
		var COST := BoonCache.BOON_CACHE_COST

		# Warband Cache — buy a boon roll with Valor.
		ui.panel(x0, y, colW, 70)
		ui.text("Warband Cache", x0 + 16, y + 26, {"size": 17, "bold": true, "color": PAL.uiAccent})
		ui.text("You own every boon at Common. Open caches to roll higher rarities — stronger versions of the same boon.", x0 + 16, y + 48, {"size": 12, "color": "#bdb49a"})
		var canAfford: bool = float(profile.data.valor) >= COST
		var bopts := {"accent": canAfford, "disabled": not canAfford}
		if not canAfford:
			bopts["tooltip"] = ["Not enough Valor", "Earn Valor by fighting — kills, razings and wins."]
		if ui.button("Open — %s ⚔" % JS.num(COST), x0 + colW - 200, y + 18, 184, 36, bopts):
			startBoonOpening(profile)
		y += 84

		ui.text("Battle Plan — equip one Offensive, Defensive & Supportive boon, then set which age each unlocks (they stack as you advance).", x0 + 16, y, {"size": 13, "color": "#d8cdb4"})
		y += 22

		var AGE_LABEL := ["Hearth Age (start)", "Banner Age", "Crown Age"]
		var cw := (colW - 24) / 3
		var CATS: Array = C.BOON_CATEGORIES
		for ci in CATS.size():
			var cat: String = CATS[ci]
			var cx := x0 + ci * (cw + 12)
			var title := Screens._cap(cat)
			ui.text(title, cx + 6, y + 14, {"size": 14, "bold": true, "color": PAL.uiAccent})

			# Age picker: which age this category's boon unlocks at (I / II / III).
			var curAge := profile.boonAgeFor(cat)
			var bw := (cw - 12) / 3
			for a in 3:
				if ui.button(["I", "II", "III"][a], cx + a * (bw + 4), y + 24, bw, 22, {
					"accent": curAge == a, "size": 12, "tooltip": [AGE_LABEL[a], "When this boon activates in a match."],
				}):
					profile.setBoonAge(cat, a)
					audio.play("ui")

			var equipped = profile.data.equippedBoons.get(cat)
			var by := y + 54
			for def in (C.BOONS as Array).filter(func(b): return b.category == cat):
				var best := profile.bestBoonRarity(def.id)
				var owns := best >= 0
				var isEq: bool = equipped == def.id
				var ch := 50.0
				var r = Rarity.rarityByIndex(best) if owns else null
				if ui.button("", cx, by, cw, ch, {"accent": isEq, "disabled": not owns}):
					profile.equipBoon(def.id, not isEq)
					audio.play("ui")
				ui.text(def.name + ("  ✓" if isEq else ""), cx + 10, by + 16, {
					"size": 13, "bold": true, "color": (r.color if r != null else PAL.uiParchment) if owns else "#6f685a",
				})
				var sub := Boons.detail(def.id, best) if owns else "Locked — find it in a Warband Cache"
				Screens.wrapText(sub, cx + 10, by + 32, cw - 20, 11, "#cabfa4" if owns else "#6f685a")
				by += ch + 7

	func drawCommanders(W: float, H: float, profile: Profile) -> void:
		var ui := UI.ui
		var PAL: Dictionary = C.PAL
		var IDS: Array = C.COMMANDER_IDS
		var owned := IDS.filter(func(id): return profile.ownsCommander(id)).size()
		ui.text("%d/%d commanders unlocked" % [owned, IDS.size()], W / 2, 170, {
			"align": "center", "size": 15, "color": "#d8cdb4",
		})
		var COST := Screens.COMMANDER_RECRUIT_COST
		var canRecruit: bool = owned < IDS.size() and float(profile.data.renown) >= COST
		if ui.button("Recruit a Commander — %d ✦" % COST, W / 2 - 180, 186, 360, 40, {
			"accent": canRecruit, "disabled": not canRecruit, "size": 15,
		}):
			var got := profile.recruitCommander(COST)
			if got:
				profile.selectCommander(got)
				profile.clearCommanderReveal() # shown inline here, not on the menu
				audio.play("levelup")

		var cols := 3
		var cw := 320.0
		var gap := 18.0
		var x0 := W / 2 - (cols * cw + (cols - 1) * gap) / 2
		var y0 := 244.0
		for i in IDS.size():
			var id: String = IDS[i]
			var def: Dictionary = C.COMMANDERS[id]
			var has := profile.ownsCommander(id)
			var selected: bool = profile.data.commander == id
			var x := x0 + (i % cols) * (cw + gap)
			var y := y0 + floori(float(i) / cols) * 150
			ui.panel(x, y, cw, 138, {"light": has})
			if not has:
				ui.ctx.fillStyle = "rgba(8,6,3,0.45)"
				ui.ctx.fillRect(x, y, cw, 138)
			ui.text(("%s — %s" % [def.name, def.title]) if has else ("??? — %s" % def.title), x + 16, y + 26, {
				"size": 15, "bold": true, "color": def.color if has else "#7a7263",
			})
			if has:
				Screens.wrapText(def.desc, x + 16, y + 48, cw - 32, 12, "#bdb49a")
				var py := y + 96
				for perk in Commanders.commanderPerks(def):
					ui.text("• " + perk, x + 16, py, {"size": 11, "color": PAL.uiAccent})
					py += 16
				if selected:
					ui.text("✓ Leading", x + cw - 90, y + 26, {"size": 12, "bold": true, "color": PAL.uiGood})
				elif ui.button("Lead", x + cw - 78, y + 104, 62, 24, {"size": 12}):
					profile.selectCommander(id)
					audio.play("ui")
			else:
				ui.text("Locked — recruit to reveal", x + 16, y + 60, {"size": 12, "color": "#9b927c"})

	func drawCollection(W: float, H: float, profile: Profile) -> void:
		var ui := UI.ui
		var IDS: Array = C.COLLECTIBLE_UNIT_IDS
		var R: Array = C.RARITIES
		var rowH := 52.0
		var colW := minf(900, W - 60)
		var x0 := W / 2 - colW / 2
		var y := 172.0

		if ui.button("Equip Best Everywhere", x0 + colW - 210, y - 8, 210, 30, {"accent": true, "size": 13}):
			profile.equipBestAll()
			audio.play("complete")
		y += 32

		var cellW := 92.0
		var gridX := x0 + 200
		# rarity headers
		for ri in R.size():
			var r: Dictionary = R[ri]
			ui.text(String(r.name).replace("Extremely Rare", "Ex. Rare").replace("One-of-a-Kind", "Unique"), gridX + ri * cellW + cellW / 2, y, {
				"align": "center", "size": 10.5, "color": r.color, "bold": true,
			})
		y += 16

		for unitId in IDS:
			var u: Dictionary = C.UNITS[unitId]
			ui.panel(x0, y, colW, rowH - 6, {"light": true})
			ui.text(u.name, x0 + 14, y + (rowH - 6) / 2, {"size": 14, "bold": true})
			var equipped := int(profile.data.loadout.get(unitId, 0))
			for ri in R.size():
				var r: Dictionary = R[ri]
				var key := Catalog.variantKey(unitId, ri)
				var owned := profile.owns(key)
				var isEq := equipped == ri
				var bx := gridX + ri * cellW + 6
				var by := y + 7
				var bw := cellW - 12
				var bh := rowH - 20
				var v: Dictionary = Catalog.VARIANT_BY_KEY[key]
				if ui.button(("★" if isEq else "✓") if owned else "🔒", bx, by, bw, bh, {
					"disabled": not owned,
					"accent": isEq,
					"size": 13,
					"tooltip": [v.name, r.name, "Equipped" if isEq else "Click to equip"] if owned
						else [v.name, r.name, "Locked — found in War Chests"],
				}) and owned and not isEq:
					profile.equip(unitId, ri)
					audio.play("ui")
				# rarity underline
				ui.ctx.fillStyle = r.color if owned else Palette.withAlpha(r.color, 0.25)
				ui.ctx.fillRect(bx + 4, by + bh - 3, bw - 8, 2.5)
			y += rowH
			if y > H - 80:
				break # viewport guard (all rosters currently fit)


# -------------------------------------------------------------- post-match --

class PostMatchScreen extends RefCounted:
	var xpAnim := 0.0
	## What happened with your Nemesis this match, if it was one.
	var rivalLines: Array = []
	var graphMetric := "score"  ## "score" | "military" | "economy"
	var reportTab := "overview"

	func reset() -> void:
		xpAnim = 0
		reportTab = "overview"

	## A time-series chart: a line per realm in its own colour, yours drawn
	## heaviest and last so it is never buried. It used to draw exactly two lines,
	## "your alliance" and "everyone else", which in a free-for-all summed three
	## rivals into one line that meant nothing.
	## lines: [{ vals, color, you }]
	func drawChart(x: float, y: float, w: float, h: float, ts: Array, lines: Array) -> void:
		var ui := UI.ui
		var ctx := ui.ctx
		var n := ts.size()
		var mx := 1.0
		for l in lines:
			for v in l.vals:
				mx = maxf(mx, float(v))
		# Frame + gridlines.
		ctx.strokeStyle = Palette.withAlpha("#ffffff", 0.08)
		ctx.lineWidth = 1
		for g in 5:
			var gy := y + (h * g) / 4
			ctx.beginPath(); ctx.moveTo(x, gy); ctx.lineTo(x + w, gy); ctx.stroke()
		var plot := func(vals: Array, color, width: float) -> void:
			ctx.strokeStyle = color
			ctx.lineWidth = width
			ctx.lineJoin = "round"
			ctx.beginPath()
			for i in n:
				var px := x + (0.0 if n <= 1 else (float(i) / (n - 1)) * w)
				var v: float = float(vals[i]) if i < vals.size() and vals[i] != null else 0.0
				var py := y + h - (v / mx) * h
				if i == 0:
					ctx.moveTo(px, py)
				else:
					ctx.lineTo(px, py)
			ctx.stroke()
		for l in lines:
			if not l.you:
				plot.call(l.vals, Palette.withAlpha(l.color, 0.9), 2)
		for l in lines:
			if l.you:
				plot.call(l.vals, "rgba(0,0,0,0.55)", 5.5)
				plot.call(l.vals, l.color, 3.2)
		ctx.lineWidth = 1
		# Y-axis max + time axis labels.
		ui.text(Screens.num0(mx), x + 4, y + 12, {"size": 11.5, "color": "#b3a98f"})
		var last: float = float(ts[n - 1]) if n > 0 else 0.0
		var endMin := floori(last / 60.0)
		var endSec := floori(fmod(last, 60.0))
		ui.text("0:00", x, y + h + 13, {"size": 11.5, "color": "#b3a98f"})
		ui.text("%d:%s" % [endMin, str(endSec).lpad(2, "0")], x + w, y + h + 13, {"size": 11.5, "align": "right", "color": "#b3a98f"})

	## Returns "continue" or null.
	func draw(W: float, H: float, time: float, dt: float, won: bool, report: Dictionary, rewards: Dictionary,
			profile: Profile, xpBefore: float, levelsGained: float, graph = null, awards: Array = []) -> Variant:
		var ui := UI.ui
		var PAL: Dictionary = C.PAL
		Screens.drawMenuBackground(W, H, time)
		var ctx := ui.ctx
		ctx.fillStyle = "rgba(10, 8, 4, 0.62)"
		ctx.fillRect(0, 0, W, H)

		ui.text("VICTORY" if won else "DEFEAT", W / 2, 62, {
			"align": "center", "size": 46, "bold": true,
			"color": "#ffe9b0" if won else "#c87a72", "font": "Georgia, serif",
		})
		ui.text(MatchReport.reportSubtitle(report), W / 2, 92, {"align": "center", "size": 13, "color": "#9a917b"})

		# Two columns: the report gets the room, the spoils get the corner.
		var pad := maxf(24, (W - 1360) / 2)
		var right := 380.0
		var gap := 24.0
		var x0 := pad
		var leftW := maxf(420, W - pad * 2 - right - gap)
		var x1 := x0 + leftW + gap
		# A Nemesis match says what became of the rival, right under the result.
		var rivalH := 30.0 if rivalLines.size() else 0.0
		var top := 116 + rivalH
		var bottom := H - 84
		var panelH := bottom - top
		if rivalH:
			var text := "  ".join(PackedStringArray(rivalLines))
			ctx.font = "bold 13px 'Trebuchet MS', sans-serif"
			var tw := minf(W - 48, ctx.measureText(text).width + 40)
			ctx.fillStyle = "rgba(200,72,62,0.18)"
			ctx.beginPath(); ctx.roundRect(W / 2 - tw / 2, 106, tw, 28, 14); ctx.fill()
			ui.text(text, W / 2, 125, {"align": "center", "size": 13, "bold": true, "color": "#ffc9b8"})

		# ---- the report ----
		ui.panel(x0, top, leftW, panelH, {"light": true})
		var tx := x0 + 18
		# Five tabs and the colour key share one row; narrow windows shrink the tabs.
		var TABS: Array = MatchReport.REPORT_TABS
		var tabW := maxf(78, minf(108, floorf((leftW - 200) / TABS.size()) - 6))
		for t in TABS:
			if ui.button(t.label, tx, top + 14, tabW, 28, {"accent": reportTab == t.id, "size": 12.5}):
				reportTab = t.id
				audio.play("ui")
			tx += tabW + 6
		# Whose colour is whose, once, rather than on every row.
		MatchReport.drawReportKey(x0 + leftW - 20, top + 28, report)
		MatchReport.drawReportTab(reportTab, x0 + 24, top + 66, leftW - 48, panelH - 82, report)

		# ---- spoils ----
		var breakdown: Array = rewards.breakdown
		var spoilsH := 42 + mini(5, breakdown.size()) * 26 + 62
		ui.panel(x1, top, right, spoilsH, {"light": true})
		ui.text("Spoils of War", x1 + 18, top + 26, {"size": 16, "bold": true, "color": PAL.uiAccent})
		var ly := top + 54
		for b in breakdown.slice(0, 5):
			ui.text(b.label, x1 + 18, ly, {"size": 12.5, "color": "#bdb49a"})
			ui.text("+%s XP  +%s ✦" % [JS.num(b.xp), JS.num(b.renown)], x1 + right - 18, ly, {"size": 12.5, "bold": true, "align": "right"})
			ly += 26
		ctx.strokeStyle = Palette.withAlpha(PAL.uiAccent, 0.4)
		ctx.beginPath(); ctx.moveTo(x1 + 18, ly - 4); ctx.lineTo(x1 + right - 18, ly - 4); ctx.stroke()
		ly += 18
		ui.text("Total", x1 + 18, ly, {"size": 14, "bold": true})
		ui.text("+%s XP  +%s ✦  +%s ⚔" % [JS.num(rewards.xp), JS.num(rewards.renown), JS.num(rewards.valor)], x1 + right - 18, ly, {
			"size": 13.5, "bold": true, "align": "right", "color": PAL.uiAccent,
		})

		# ---- level ----
		xpAnim = minf(1, xpAnim + dt / 1.6)
		var animXp: float = xpBefore + rewards.xp * xpAnim
		var info := Progression.levelFromXp(floorf(animXp))
		var lvlY := top + spoilsH + gap
		ui.panel(x1, lvlY, right, 62, {"light": true})
		ui.text("Level %s" % JS.num(info.level), x1 + 18, lvlY + 22, {"size": 14, "bold": true, "color": PAL.uiAccent})
		if levelsGained > 0 and xpAnim >= 1:
			ui.text("LEVEL UP! +%s" % JS.num(levelsGained), x1 + right - 18, lvlY + 22, {
				"size": 13, "bold": true, "align": "right", "color": "#ffe9b0",
			})
		ui.bar(x1 + 18, lvlY + 38, right - 36, 10, float(info.into) / info.need, PAL.uiAccent)

		# ---- what this match unlocked ----
		# Above the graph rather than below it: an achievement is news, and news
		# that has scrolled off the bottom of a panel is not news.
		var awardsH := 0.0
		if awards.size():
			awardsH = 40 + awards.size() * 24 + 8
			var ay := lvlY + 62 + gap
			ui.panel(x1, ay, right, awardsH, {"light": true})
			ui.text("Earned", x1 + 18, ay + 24, {"size": 15, "bold": true, "color": PAL.uiAccent})
			for i in awards.size():
				var a: Dictionary = awards[i]
				var ry := ay + 48 + i * 24
				var weekly: bool = a.get("weekly", false)
				ui.text("◈" if weekly else "★", x1 + 18, ry, {
					"size": 13, "bold": true, "color": "#7fd0ff" if weekly else "#ffd24a",
				})
				ui.text(a.name, x1 + 36, ry, {"size": 12.5, "color": "#e7ddc4"})
				ui.text("+%s ⚔" % JS.num(a.valor), x1 + right - 18, ry, {
					"size": 12.5, "bold": true, "align": "right", "color": "#ffd24a",
				})

		# ---- progression graph ----
		if graph != null and (graph.ts as Array).size() >= 2:
			var gy := lvlY + 62 + gap + (awardsH + gap if awardsH else 0.0)
			var gh := bottom - gy
			if gh > 120:
				ui.panel(x1, gy, right, gh, {"light": true})
				ui.text("Progression", x1 + 18, gy + 22, {"size": 14, "bold": true, "color": PAL.uiAccent})
				var tabs := [["score", "Score"], ["military", "Army"], ["economy", "Eco"]]
				var gx := x1 + 130
				for t in tabs:
					if ui.button(t[1], gx, gy + 8, 74, 26, {"accent": graphMetric == t[0], "size": 11.5}):
						graphMetric = t[0]
						audio.play("ui")
					gx += 80
				var m := graphMetric
				var lines: Array = []
				var gps = graph.get("players")
				if gps != null and (gps as Array).size():
					for p in gps:
						var horde: bool = p.get("horde", false)
						lines.append({
							"vals": p.values.get(m, []),
							"color": "#8d8779" if horde else Palette.teamColor(int(p.team)).main,
							"you": p.you,
							"name": "Horde" if horde else Palette.teamColor(int(p.team)).name,
						})
				else:
					lines = [
						{"vals": graph.foe[m], "color": "#e0786a", "you": false, "name": "Opponents"},
						{"vals": graph.mine[m], "color": "#7fb0e8", "you": true, "name": "You"},
					]
				# Legend under the chart, wrapping: up to sixteen realms have to fit.
				ctx.font = "12px \"Trebuchet MS\", sans-serif"
				var chipW := func(l) -> float: return 18 + ctx.measureText(l.name + (" (you)" if l.you else "")).width + 12
				var legendRows: Array = [[]]
				var rowW := 0.0
				var legendW := right - 36
				for l in lines:
					var cw: float = chipW.call(l)
					if rowW + cw > legendW and (legendRows[legendRows.size() - 1] as Array).size():
						legendRows.append([])
						rowW = 0
					legendRows[legendRows.size() - 1].append(l)
					rowW += cw
				var legendH := legendRows.size() * 18 + 4
				drawChart(x1 + 34, gy + 48, right - 58, gh - 84 - legendH, graph.ts, lines)
				var ly2 := gy + gh - legendH - 4
				for row in legendRows:
					var lx := x1 + 18
					for l in row:
						ctx.fillStyle = l.color
						ctx.fillRect(lx, ly2 - 5, 12, 4 if l.you else 3)
						var label: String = l.name + (" (you)" if l.you else "")
						ui.text(label, lx + 17, ly2, {"size": 12, "bold": l.you, "color": "#ffe9b0" if l.you else "#cfc4a8"})
						ctx.font = "12px \"Trebuchet MS\", sans-serif"
						lx += chipW.call(l)
					ly2 += 18

		if ui.button("Continue", W / 2 - 110, H - 62, 220, 46, {"accent": true, "size": 17}):
			return "continue"
		return null
