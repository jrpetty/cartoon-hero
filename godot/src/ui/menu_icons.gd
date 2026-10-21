## ui/menu_icons.ts — drawn icons for the main menu's tiles.
##
## Emoji in a label render differently on every platform (and as boxes on
## some), and they never matched the game's painted look. These are the same
## kind of shapes the game draws its world with: flat fills, a darker outline,
## one highlight. Each is drawn centred on (x, y) at roughly `s` pixels across.
class_name MenuIcons

const INK := "#1e160c"
const GOLD := "#e8c060"
const STEEL := "#cfd6de"


static func stroke(ctx: Ctx, w: float) -> void:
	ctx.strokeStyle = INK
	ctx.lineWidth = w
	ctx.lineJoin = "round"
	ctx.lineCap = "round"
	ctx.stroke()


static func sword(ctx: Ctx, angle: float, u: float) -> void:
	ctx.save()
	ctx.rotate(angle)
	# blade
	ctx.beginPath()
	ctx.moveTo(-u * 0.07, u * 0.18)
	ctx.lineTo(-u * 0.07, -u * 0.62)
	ctx.lineTo(0, -u * 0.76)
	ctx.lineTo(u * 0.07, -u * 0.62)
	ctx.lineTo(u * 0.07, u * 0.18)
	ctx.closePath()
	ctx.fillStyle = STEEL
	ctx.fill()
	stroke(ctx, u * 0.035)
	ctx.fillStyle = Palette.withAlpha("#ffffff", 0.55)
	ctx.fillRect(-u * 0.02, -u * 0.6, u * 0.03, u * 0.74)
	# guard, grip, pommel
	ctx.beginPath()
	ctx.roundRect(-u * 0.24, u * 0.16, u * 0.48, u * 0.09, u * 0.04)
	ctx.fillStyle = GOLD
	ctx.fill()
	stroke(ctx, u * 0.03)
	ctx.beginPath()
	ctx.rect(-u * 0.045, u * 0.25, u * 0.09, u * 0.26)
	ctx.fillStyle = "#6a3e22"
	ctx.fill()
	stroke(ctx, u * 0.03)
	ctx.beginPath()
	ctx.arc(0, u * 0.56, u * 0.07, 0, PI * 2)
	ctx.fillStyle = GOLD
	ctx.fill()
	stroke(ctx, u * 0.03)
	ctx.restore()


## Crossed swords over a round shield: Skirmish.
static func iconSkirmish(ctx: Ctx, x: float, y: float, s: float, accent) -> void:
	var u := s / 1.6
	ctx.save()
	ctx.translate(x, y)
	ctx.beginPath()
	ctx.arc(0, 0, u * 0.5, 0, PI * 2)
	ctx.fillStyle = accent
	ctx.fill()
	stroke(ctx, u * 0.04)
	ctx.beginPath()
	ctx.arc(0, 0, u * 0.36, 0, PI * 2)
	ctx.fillStyle = Palette.shade(accent, -0.18)
	ctx.fill()
	ctx.beginPath()
	ctx.arc(0, 0, u * 0.1, 0, PI * 2)
	ctx.fillStyle = GOLD
	ctx.fill()
	stroke(ctx, u * 0.03)
	sword(ctx, -0.72, u)
	sword(ctx, 0.72, u)
	ctx.restore()


static func flag(ctx: Ctx, x: float, y: float, u: float, col, flip: float, wave: float) -> void:
	ctx.save()
	ctx.translate(x, y)
	ctx.scale(flip, 1)
	ctx.beginPath()
	ctx.rect(-u * 0.03, -u * 0.5, u * 0.06, u * 1.0)
	ctx.fillStyle = "#6a4a2a"
	ctx.fill()
	stroke(ctx, u * 0.025)
	ctx.beginPath()
	ctx.moveTo(u * 0.03, -u * 0.46)
	ctx.quadraticCurveTo(u * 0.22, -u * 0.5 + wave, u * 0.44, -u * 0.44)
	ctx.lineTo(u * 0.36, -u * 0.28)
	ctx.lineTo(u * 0.44, -u * 0.1)
	ctx.quadraticCurveTo(u * 0.22, -u * 0.16 + wave, u * 0.03, -u * 0.12)
	ctx.closePath()
	ctx.fillStyle = col
	ctx.fill()
	stroke(ctx, u * 0.03)
	ctx.beginPath()
	ctx.arc(0, -u * 0.52, u * 0.04, 0, PI * 2)
	ctx.fillStyle = GOLD
	ctx.fill()
	ctx.restore()


## Two banners leaning together, allies of different colours: Multiplayer.
static func iconMultiplayer(ctx: Ctx, x: float, y: float, s: float, time: float) -> void:
	var u := s
	var w := sin(time * 2.4) * u * 0.03
	flag(ctx, x - u * 0.12, y + u * 0.08, u, "#5b8fe0", -1, w)
	flag(ctx, x + u * 0.12, y + u * 0.08, u, "#d8574a", 1, -w)
	# the handshake between them: a small gold ring
	ctx.beginPath()
	ctx.arc(x, y + u * 0.3, u * 0.1, 0, PI * 2)
	ctx.strokeStyle = GOLD
	ctx.lineWidth = u * 0.05
	ctx.stroke()


## A hex tile with three pips: Warband Tactics.
static func iconWarband(ctx: Ctx, x: float, y: float, s: float) -> void:
	var r := s * 0.42
	ctx.save()
	ctx.translate(x, y)
	ctx.beginPath()
	for i in 6:
		var a := PI / 6 + (i * PI) / 3
		ctx.lineTo(cos(a) * r, sin(a) * r)
	ctx.closePath()
	ctx.fillStyle = "#8a5a9e"
	ctx.fill()
	stroke(ctx, s * 0.035)
	ctx.beginPath()
	for i in 6:
		var a := PI / 6 + (i * PI) / 3
		ctx.lineTo(cos(a) * r * 0.72, sin(a) * r * 0.72)
	ctx.closePath()
	ctx.fillStyle = "#6a3e7e"
	ctx.fill()
	for pp in [[-0.16, -0.14], [0.16, -0.14], [0, 0.16]]:
		ctx.beginPath()
		ctx.arc(pp[0] * s, pp[1] * s, s * 0.07, 0, PI * 2)
		ctx.fillStyle = GOLD
		ctx.fill()
		stroke(ctx, s * 0.02)
	ctx.restore()


## A banded chest: the Armory.
static func iconArmory(ctx: Ctx, x: float, y: float, s: float) -> void:
	var u := s / 100
	ctx.save()
	ctx.translate(x, y + 6 * u)
	ctx.beginPath()
	ctx.roundRect(-40 * u, -14 * u, 80 * u, 40 * u, 5 * u)
	ctx.fillStyle = "#8a5a2e"
	ctx.fill()
	stroke(ctx, 3 * u)
	ctx.beginPath()
	ctx.roundRect(-40 * u, -36 * u, 80 * u, 24 * u, [14 * u, 14 * u, 0, 0])
	ctx.fillStyle = "#a06a36"
	ctx.fill()
	stroke(ctx, 3 * u)
	ctx.fillStyle = GOLD
	ctx.fillRect(-12 * u, -36 * u, 7 * u, 62 * u)
	ctx.fillRect(5 * u, -36 * u, 7 * u, 62 * u)
	ctx.beginPath()
	ctx.roundRect(-7 * u, -6 * u, 14 * u, 16 * u, 3 * u)
	ctx.fill()
	stroke(ctx, 2 * u)
	# a glint of what's inside
	ctx.fillStyle = Palette.withAlpha("#ffe9b0", 0.8)
	for gg in [[-26, -44], [22, -48], [0, -52]]:
		var gx: float = gg[0]
		var gy: float = gg[1]
		ctx.beginPath()
		ctx.moveTo(gx * u, (gy - 5) * u)
		ctx.lineTo((gx + 2) * u, gy * u)
		ctx.lineTo(gx * u, (gy + 5) * u)
		ctx.lineTo((gx - 2) * u, gy * u)
		ctx.closePath()
		ctx.fill()
	ctx.restore()


## A folded map with a route marked on it: the Map Editor.
static func iconMap(ctx: Ctx, x: float, y: float, s: float) -> void:
	var u := s / 100
	ctx.save()
	ctx.translate(x, y)
	var xs := [-42, -14, 14, 42]
	for i in 3:
		ctx.beginPath()
		var up := i % 2 == 0
		ctx.moveTo(xs[i] * u, (-30 if up else -24) * u)
		ctx.lineTo(xs[i + 1] * u, (-24 if up else -30) * u)
		ctx.lineTo(xs[i + 1] * u, (30 if up else 36) * u)
		ctx.lineTo(xs[i] * u, (36 if up else 30) * u)
		ctx.closePath()
		ctx.fillStyle = "#d8c49a" if i == 1 else "#e8d8b0"
		ctx.fill()
		stroke(ctx, 2.5 * u)
	# land, water and a route
	ctx.fillStyle = "#7aa860"
	ctx.beginPath(); ctx.ellipse(-26 * u, -4 * u, 10 * u, 14 * u, 0.3, 0, PI * 2); ctx.fill()
	ctx.fillStyle = "#6a9ac8"
	ctx.beginPath(); ctx.ellipse(24 * u, 12 * u, 11 * u, 8 * u, -0.2, 0, PI * 2); ctx.fill()
	ctx.setLineDash([4 * u, 4 * u])
	ctx.strokeStyle = "#b8483e"
	ctx.lineWidth = 3 * u
	ctx.beginPath()
	ctx.moveTo(-28 * u, 20 * u)
	ctx.quadraticCurveTo(0, -20 * u, 26 * u, -14 * u)
	ctx.stroke()
	ctx.setLineDash([])
	ctx.fillStyle = "#b8483e"
	ctx.font = "bold %spx Georgia, serif" % JS.num(16 * u)
	ctx.textAlign = "center"
	ctx.fillText("✕", 27 * u, -8 * u)
	ctx.restore()


## An open book: the Codex.
static func iconCodex(ctx: Ctx, x: float, y: float, s: float) -> void:
	var u := s / 100
	ctx.save()
	ctx.translate(x, y)
	for side in [-1, 1]:
		ctx.beginPath()
		ctx.moveTo(0, -24 * u)
		ctx.quadraticCurveTo(side * 22 * u, -34 * u, side * 44 * u, -28 * u)
		ctx.lineTo(side * 44 * u, 26 * u)
		ctx.quadraticCurveTo(side * 22 * u, 20 * u, 0, 30 * u)
		ctx.closePath()
		ctx.fillStyle = "#efe2c4"
		ctx.fill()
		stroke(ctx, 2.5 * u)
		ctx.strokeStyle = Palette.withAlpha(INK, 0.35)
		ctx.lineWidth = 2 * u
		for i in 4:
			ctx.beginPath()
			ctx.moveTo(side * 8 * u, (-14 + i * 10) * u)
			ctx.lineTo(side * 36 * u, (-16 + i * 10) * u)
			ctx.stroke()
	ctx.fillStyle = "#b8483e"
	ctx.fillRect(-3 * u, -26 * u, 6 * u, 62 * u)
	ctx.restore()


## A gear: Settings.
static func iconSettings(ctx: Ctx, x: float, y: float, s: float) -> void:
	var u := s / 100
	ctx.save()
	ctx.translate(x, y)
	ctx.beginPath()
	var teeth := 8
	for i in teeth * 2:
		var a := (float(i) / (teeth * 2)) * PI * 2
		var r := (38 if i % 2 == 0 else 30) * u
		ctx.lineTo(cos(a - 0.12) * r, sin(a - 0.12) * r)
		ctx.lineTo(cos(a + 0.12) * r, sin(a + 0.12) * r)
	ctx.closePath()
	ctx.fillStyle = "#9aa4b0"
	ctx.fill()
	stroke(ctx, 2.5 * u)
	ctx.beginPath()
	ctx.arc(0, 0, 12 * u, 0, PI * 2)
	ctx.fillStyle = "#3a3226"
	ctx.fill()
	stroke(ctx, 2 * u)
	ctx.restore()


## A curved arrow over an hourglass: Resume.
static func iconResume(ctx: Ctx, x: float, y: float, s: float) -> void:
	var u := s / 100
	ctx.save()
	ctx.translate(x, y)
	ctx.beginPath()
	ctx.moveTo(-18 * u, -30 * u); ctx.lineTo(18 * u, -30 * u); ctx.lineTo(0, 0)
	ctx.lineTo(18 * u, 30 * u); ctx.lineTo(-18 * u, 30 * u); ctx.lineTo(0, 0)
	ctx.closePath()
	ctx.fillStyle = Palette.withAlpha("#cfe6f0", 0.85)
	ctx.fill()
	stroke(ctx, 3 * u)
	ctx.fillStyle = GOLD
	ctx.beginPath(); ctx.moveTo(-10 * u, 26 * u); ctx.lineTo(10 * u, 26 * u); ctx.lineTo(0, 12 * u); ctx.closePath(); ctx.fill()
	ctx.fillStyle = "#6a4a2a"
	ctx.fillRect(-24 * u, -36 * u, 48 * u, 7 * u)
	ctx.fillRect(-24 * u, 29 * u, 48 * u, 7 * u)
	ctx.restore()


## Three standards in different colours, crossed: the Factions book.
static func iconFactions(ctx: Ctx, x: float, y: float, s: float) -> void:
	var u := s / 100
	ctx.save()
	ctx.translate(x, y + 6 * u)
	var cols := ["#5b8fe0", "#d8a83a", "#d8574a"]
	var angles := [-0.35, 0.0, 0.35]
	for i in 3:
		ctx.save()
		ctx.rotate(angles[i])
		ctx.beginPath()
		ctx.rect(-2.5 * u, -46 * u, 5 * u, 80 * u)
		ctx.fillStyle = "#6a4a2a"
		ctx.fill()
		stroke(ctx, 2 * u)
		ctx.beginPath()
		ctx.moveTo(2.5 * u, -44 * u)
		ctx.lineTo(26 * u, -40 * u)
		ctx.lineTo(18 * u, -30 * u)
		ctx.lineTo(26 * u, -20 * u)
		ctx.lineTo(2.5 * u, -18 * u)
		ctx.closePath()
		ctx.fillStyle = cols[i]
		ctx.fill()
		stroke(ctx, 2 * u)
		ctx.restore()
	ctx.beginPath()
	ctx.arc(0, 30 * u, 7 * u, 0, PI * 2)
	ctx.fillStyle = GOLD
	ctx.fill()
	stroke(ctx, 2 * u)
	ctx.restore()


## A laurel-edged scroll with a rising bar chart on it: the Career screen.
static func iconCareer(ctx: Ctx, x: float, y: float, s: float) -> void:
	var u := s / 100
	ctx.save()
	ctx.translate(x, y)
	ctx.beginPath()
	ctx.roundRect(-34 * u, -34 * u, 68 * u, 64 * u, 6 * u)
	ctx.fillStyle = "#efe2c4"
	ctx.fill()
	stroke(ctx, 2.5 * u)
	var bars := [14, 24, 20, 36]
	var cols := ["#b8a888", "#b8a888", "#b8a888", GOLD]
	for i in bars.size():
		var h: float = bars[i]
		ctx.beginPath()
		ctx.rect((-24 + i * 13) * u, (22 - h) * u, 9 * u, h * u)
		ctx.fillStyle = cols[i]
		ctx.fill()
		stroke(ctx, 1.6 * u)
	# A little laurel at the corner: two leaves.
	ctx.fillStyle = "#7aa860"
	for a in [-0.6, 0.6]:
		ctx.save()
		ctx.translate(26 * u, -26 * u)
		ctx.rotate(a)
		ctx.beginPath()
		ctx.ellipse(0, -8 * u, 4 * u, 9 * u, 0, 0, PI * 2)
		ctx.fill()
		stroke(ctx, 1.5 * u)
		ctx.restore()
	ctx.restore()


## Nemesis: a horned war-helm with a red eye-slit — the rival who remembers.
static func iconNemesis(ctx: Ctx, x: float, y: float, s: float) -> void:
	var r := s * 0.34
	ctx.save()
	ctx.translate(x, y + s * 0.04)
	# Horns.
	for d in [-1, 1]:
		ctx.beginPath()
		ctx.moveTo(d * r * 0.7, -r * 0.35)
		ctx.quadraticCurveTo(d * r * 1.55, -r * 0.55, d * r * 1.35, -r * 1.35)
		ctx.quadraticCurveTo(d * r * 1.15, -r * 0.8, d * r * 0.55, -r * 0.7)
		ctx.closePath()
		ctx.fillStyle = "#e7dcc0"
		ctx.fill()
		stroke(ctx, s * 0.03)
	# The helm.
	ctx.beginPath()
	ctx.moveTo(-r, r * 0.9)
	ctx.lineTo(-r, -r * 0.2)
	ctx.quadraticCurveTo(-r, -r * 1.05, 0, -r * 1.05)
	ctx.quadraticCurveTo(r, -r * 1.05, r, -r * 0.2)
	ctx.lineTo(r, r * 0.9)
	ctx.lineTo(r * 0.25, r * 1.1)
	ctx.lineTo(-r * 0.25, r * 1.1)
	ctx.closePath()
	ctx.fillStyle = "#6e6a70"
	ctx.fill()
	stroke(ctx, s * 0.035)
	# Eye slit, glowing.
	ctx.fillStyle = "#1a0d0b"
	ctx.fillRect(-r * 0.72, -r * 0.12, r * 1.44, r * 0.26)
	ctx.fillStyle = "#ff5a3c"
	ctx.shadowColor = "#ff5a3c"
	ctx.shadowBlur = s * 0.12
	ctx.fillRect(-r * 0.5, -r * 0.05, r * 0.3, r * 0.12)
	ctx.fillRect(r * 0.2, -r * 0.05, r * 0.3, r * 0.12)
	ctx.shadowBlur = 0
	# Nose guard.
	ctx.fillStyle = "#8a868c"
	ctx.fillRect(-r * 0.08, -r * 0.12, r * 0.16, r * 0.8)
	ctx.restore()
