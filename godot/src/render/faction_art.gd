## render/faction_art.ts — how each faction is drawn.
##
## The procedural art is built from a handful of shared parts — a stone wall,
## a timber wall, a roof, a tower cap, a door, a window, a standard, a helmet,
## a shield, a breastplate. Each faction redraws those parts in its own style,
## so every building and soldier it owns changes character at once: terracotta
## and bronze for the Legion, stave-built halls and nasal helms for the Jarls,
## curved eaves and lacquer for the Shogunate, felt yurts for the Khanate, and
## white composite with a cold glow for the Ascendancy.
##
## The team colour always stays on something big — a roof, a crest, a shield,
## a band round a dome — because reading who owns what matters more than any
## amount of style. Nothing here is magic; the Ascendancy's glow is engineering.
##
## `look` is a faction's look (C.FACTIONS[id].look); `tc` a team colour
## {main, light, dark}.
class_name FactionArt


static func grad(ctx: Ctx, x0: float, y0: float, x1: float, y1: float, a, b) -> Ctx.CanvasGradient:
	var g := ctx.createLinearGradient(x0, y0, x1, y1)
	g.addColorStop(0, a)
	g.addColorStop(1, b)
	return g


static func outline(ctx: Ctx, w: float = 1.4) -> void:
	ctx.strokeStyle = "rgba(20,16,10,0.42)"
	ctx.lineWidth = w
	ctx.stroke()


## A soft glow line, for the Ascendancy's light strips.
static func glowLine(ctx: Ctx, x0: float, y0: float, x1: float, y1: float, col, w: float = 1.6) -> void:
	ctx.save()
	ctx.strokeStyle = Palette.withAlpha(col, 0.35)
	ctx.lineWidth = w * 3
	ctx.lineCap = "round"
	ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke()
	ctx.strokeStyle = col
	ctx.lineWidth = w
	ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke()
	ctx.restore()


# ------------------------------------------------------------------ walls --

## A masonry block in the faction's style.
static func factionStone(ctx: Ctx, look: Dictionary, x: float, y: float, w: float, h: float) -> void:
	match look.roof:
		"tile":  # dressed travertine, ashlar courses with staggered joints
			ctx.fillStyle = grad(ctx, x, y, x, y + h, Palette.shade(look.stone, 0.12), Palette.shade(look.stone, -0.16))
			ctx.beginPath(); ctx.roundRect(x, y, w, h, 2); ctx.fill(); outline(ctx)
			ctx.strokeStyle = "rgba(120,96,64,0.28)"
			ctx.lineWidth = 1
			var course := maxf(5, h / 4)
			var row := 0
			var yy := y + course
			while yy < y + h - 1:
				ctx.beginPath(); ctx.moveTo(x + 1.5, yy); ctx.lineTo(x + w - 1.5, yy); ctx.stroke()
				yy += course
				row += 1
			row = 0
			yy = y
			while yy < y + h - 1:
				var xx := x + (w / 6 if row % 2 else w / 3)
				while xx < x + w - 2:
					ctx.beginPath(); ctx.moveTo(xx, yy); ctx.lineTo(xx, minf(y + h, yy + course)); ctx.stroke()
					xx += w / 3
				yy += course
				row += 1
			# A carved cornice along the top.
			ctx.fillStyle = Palette.shade(look.stone, 0.2)
			ctx.fillRect(x - 1, y - 1, w + 2, 2.5)
			return
		"shingle":  # rough fieldstone, irregular
			ctx.fillStyle = grad(ctx, x, y, x, y + h, Palette.shade(look.stone, 0.1), Palette.shade(look.stone, -0.22))
			ctx.beginPath(); ctx.roundRect(x, y, w, h, 3); ctx.fill(); outline(ctx)
			ctx.fillStyle = "rgba(0,0,0,0.16)"
			var k := int(absf(JS.round(x * 7 + y * 13))) % 97
			var yy := y + 3
			while yy < y + h - 3:
				var xx := x + 3 + (k % 4)
				while xx < x + w - 4:
					k = (k * 31 + 17) % 97
					ctx.beginPath(); ctx.ellipse(xx, yy, 2.4 + (k % 3) * 0.4, 1.8, 0, 0, PI * 2); ctx.fill()
					xx += 7 + (k % 3)
				yy += 6
			return
		"pagoda":  # dark stone plinth, white plaster above, timber frame
			var base := h * 0.38
			ctx.fillStyle = grad(ctx, x, y + h - base, x, y + h, Palette.shade(look.stone, 0.1), Palette.shade(look.stone, -0.2))
			ctx.fillRect(x, y + h - base, w, base)
			ctx.fillStyle = grad(ctx, x, y, x, y + h - base, look.plaster, Palette.shade(look.plaster, -0.12))
			ctx.fillRect(x, y, w, h - base)
			ctx.beginPath(); ctx.rect(x, y, w, h); outline(ctx)
			ctx.fillStyle = look.frame
			ctx.fillRect(x, y, w, 2.5)
			for fx in [0.0, 0.5, 1.0]:
				ctx.fillRect(x + fx * (w - 3), y, 3, h - base)
			return
		"yurt":  # felt panels over a lattice
			feltWall(ctx, look, x, y, w, h)
			return
		"dome":  # composite panels with a light strip
			panelWall(ctx, look, x, y, w, h)
			return
		_:
			ctx.fillStyle = grad(ctx, x, y, x, y + h, Palette.shade(look.stone, 0.18), Palette.shade(look.stone, -0.18))
			ctx.beginPath(); ctx.roundRect(x, y, w, h, 3); ctx.fill(); outline(ctx)


## A lighter-built wall (houses, camps) in the faction's style.
static func factionTimber(ctx: Ctx, look: Dictionary, x: float, y: float, w: float, h: float) -> void:
	match look.roof:
		"tile":  # plastered brick with a painted dado
			ctx.fillStyle = grad(ctx, x, y, x, y + h, look.plaster, Palette.shade(look.plaster, -0.14))
			ctx.beginPath(); ctx.roundRect(x, y, w, h, 2); ctx.fill(); outline(ctx)
			ctx.fillStyle = Palette.shade("#b0503a", -0.05)
			ctx.fillRect(x, y + h * 0.72, w, h * 0.28)
			ctx.fillStyle = "rgba(255,255,255,0.18)"
			ctx.fillRect(x, y + h * 0.72, w, 1.2)
			return
		"shingle":  # stave wall: upright planks
			ctx.fillStyle = grad(ctx, x, y, x, y + h, Palette.shade(look.plaster, 0.08), Palette.shade(look.plaster, -0.24))
			ctx.beginPath(); ctx.roundRect(x, y, w, h, 2); ctx.fill(); outline(ctx)
			ctx.strokeStyle = "rgba(30,20,10,0.4)"
			ctx.lineWidth = 1
			var xx := x + 4
			while xx < x + w - 2:
				ctx.beginPath(); ctx.moveTo(xx, y + 1); ctx.lineTo(xx, y + h - 1); ctx.stroke()
				xx += 4.5
			ctx.fillStyle = look.frame
			ctx.fillRect(x, y, w, 3)
			return
		"pagoda":  # shoji: paper panels in a dark frame
			ctx.fillStyle = grad(ctx, x, y, x, y + h, look.plaster, Palette.shade(look.plaster, -0.1))
			ctx.fillRect(x, y, w, h)
			ctx.beginPath(); ctx.rect(x, y, w, h); outline(ctx)
			ctx.strokeStyle = look.frame
			ctx.lineWidth = 1.2
			var cols := maxi(2, int(JS.round(w / 9)))
			for i in cols + 1:
				var xx := x + (float(i) / cols) * w
				ctx.beginPath(); ctx.moveTo(xx, y); ctx.lineTo(xx, y + h); ctx.stroke()
			ctx.beginPath(); ctx.moveTo(x, y + h * 0.5); ctx.lineTo(x + w, y + h * 0.5); ctx.stroke()
			ctx.fillStyle = look.frame
			ctx.fillRect(x, y, w, 2.5)
			return
		"yurt":
			feltWall(ctx, look, x, y, w, h)
			return
		"dome":
			panelWall(ctx, look, x, y, w, h)
			return
		_:
			ctx.fillStyle = grad(ctx, x, y, x, y + h, look.plaster, Palette.shade(look.plaster, -0.16))
			ctx.beginPath(); ctx.roundRect(x, y, w, h, 3); ctx.fill(); outline(ctx)
			ctx.fillStyle = look.frame
			ctx.fillRect(x, y, 4, h); ctx.fillRect(x + w - 4, y, 4, h); ctx.fillRect(x, y, w, 3.5)


static func feltWall(ctx: Ctx, look: Dictionary, x: float, y: float, w: float, h: float) -> void:
	ctx.fillStyle = grad(ctx, x, y, x, y + h, look.plaster, Palette.shade(look.plaster, -0.14))
	ctx.beginPath(); ctx.roundRect(x, y, w, h, h * 0.25); ctx.fill(); outline(ctx)
	# Lattice showing through at the hem, and a rope round the middle.
	ctx.strokeStyle = Palette.withAlpha(look.frame, 0.55)
	ctx.lineWidth = 0.9
	var hem := y + h * 0.62
	var xx := x + 2
	while xx < x + w - 2:
		ctx.beginPath(); ctx.moveTo(xx, hem); ctx.lineTo(xx + 4, y + h - 1); ctx.stroke()
		ctx.beginPath(); ctx.moveTo(xx + 4, hem); ctx.lineTo(xx, y + h - 1); ctx.stroke()
		xx += 5
	ctx.strokeStyle = Palette.shade(look.frame, -0.1)
	ctx.lineWidth = 1.4
	ctx.beginPath(); ctx.moveTo(x + 1, y + h * 0.4); ctx.lineTo(x + w - 1, y + h * 0.4); ctx.stroke()


static func panelWall(ctx: Ctx, look: Dictionary, x: float, y: float, w: float, h: float) -> void:
	ctx.fillStyle = grad(ctx, x, y, x, y + h, "#f6f8fa", Palette.shade(look.stone, -0.12))
	ctx.beginPath(); ctx.roundRect(x, y, w, h, 5); ctx.fill(); outline(ctx, 1.2)
	ctx.strokeStyle = "rgba(60,70,90,0.28)"
	ctx.lineWidth = 0.9
	var xx := x + w / 3
	while xx < x + w - 2:
		ctx.beginPath(); ctx.moveTo(xx, y + 2); ctx.lineTo(xx, y + h - 2); ctx.stroke()
		xx += w / 3
	glowLine(ctx, x + 3, y + h * 0.3, x + w - 3, y + h * 0.3, look.accent, 1.3)


# ------------------------------------------------------------------ roofs --

## A hall or house roof. The team colour always rides on it somewhere big.
static func factionRoof(ctx: Ctx, look: Dictionary, cx: float, eaveY: float, halfW: float, peakY: float, tc: Dictionary) -> void:
	var rise := eaveY - peakY
	match look.roof:
		"tile":  # low Roman pitch, terracotta barrel tiles, team pediment
			var py := eaveY - rise * 0.6
			var hw := halfW * 1.04
			ctx.fillStyle = grad(ctx, cx, py, cx, eaveY, Palette.shade(look.roofMaterial, 0.14), Palette.shade(look.roofMaterial, -0.18))
			ctx.beginPath()
			ctx.moveTo(cx - hw, eaveY); ctx.lineTo(cx - hw * 0.52, py); ctx.lineTo(cx + hw * 0.52, py); ctx.lineTo(cx + hw, eaveY)
			ctx.closePath(); ctx.fill(); outline(ctx, 1.5)
			ctx.strokeStyle = "rgba(90,30,10,0.35)"
			ctx.lineWidth = 1
			for i in range(-6, 7):
				var t := i / 6.0
				ctx.beginPath(); ctx.moveTo(cx + t * hw * 0.52, py); ctx.lineTo(cx + t * hw, eaveY); ctx.stroke()
			# Team-colour pediment and fascia: the colour reads from across the map.
			ctx.fillStyle = tc.main
			ctx.beginPath()
			ctx.moveTo(cx - hw * 0.36, eaveY); ctx.lineTo(cx, eaveY - rise * 0.34); ctx.lineTo(cx + hw * 0.36, eaveY)
			ctx.closePath(); ctx.fill(); outline(ctx, 1)
			ctx.fillStyle = Palette.shade(tc.main, -0.15)
			ctx.fillRect(cx - hw, eaveY - 1.5, hw * 2, 3)
			ctx.fillStyle = look.accent
			ctx.beginPath(); ctx.arc(cx, eaveY - rise * 0.14, 1.8, 0, PI * 2); ctx.fill()
			return
		"shingle":  # steep hall, team-colour shingles, crossed gable heads
			var py := eaveY - rise * 1.22
			var hw := halfW * 0.98
			ctx.fillStyle = grad(ctx, cx, py, cx, eaveY, Palette.shade(tc.main, 0.05), Palette.shade(tc.dark, -0.12))
			ctx.beginPath()
			ctx.moveTo(cx - hw, eaveY); ctx.lineTo(cx - hw * 0.12, py); ctx.lineTo(cx + hw * 0.12, py); ctx.lineTo(cx + hw, eaveY)
			ctx.closePath(); ctx.fill(); outline(ctx, 1.6)
			ctx.save()
			ctx.clip()
			ctx.strokeStyle = "rgba(0,0,0,0.22)"
			ctx.lineWidth = 1
			var yy := py + 4
			while yy < eaveY:
				var xx := cx - hw
				while xx < cx + hw:
					ctx.beginPath(); ctx.arc(xx + fmod(yy / 4, 2) * 2, yy, 2, 0, PI); ctx.stroke()
					xx += 4
				yy += 4
			ctx.restore()
			# Crossed gable boards at each end of the ridge.
			ctx.strokeStyle = look.frame
			ctx.lineWidth = 2.2
			ctx.lineCap = "round"
			for sx in [-1, 1]:
				var ex: float = cx + sx * hw * 0.12
				ctx.beginPath()
				ctx.moveTo(ex - 4, py + 5); ctx.lineTo(ex + 3.5, py - 5)
				ctx.moveTo(ex + 4, py + 5); ctx.lineTo(ex - 3.5, py - 5)
				ctx.stroke()
			return
		"pagoda":  # dark tiles, curved eaves that sweep up, team ridge
			var py := eaveY - rise * 0.95
			var hw := halfW * 1.12
			ctx.fillStyle = grad(ctx, cx, py, cx, eaveY, Palette.shade(look.roofMaterial, 0.18), Palette.shade(look.roofMaterial, -0.2))
			ctx.beginPath()
			ctx.moveTo(cx - hw - 3, eaveY - 5)
			ctx.quadraticCurveTo(cx - hw * 0.72, eaveY + 1, cx - hw * 0.5, py + rise * 0.18)
			ctx.lineTo(cx - hw * 0.34, py)
			ctx.lineTo(cx + hw * 0.34, py)
			ctx.lineTo(cx + hw * 0.5, py + rise * 0.18)
			ctx.quadraticCurveTo(cx + hw * 0.72, eaveY + 1, cx + hw + 3, eaveY - 5)
			ctx.quadraticCurveTo(cx, eaveY + 3, cx - hw - 3, eaveY - 5)
			ctx.closePath(); ctx.fill(); outline(ctx, 1.5)
			ctx.strokeStyle = "rgba(255,255,255,0.1)"
			ctx.lineWidth = 1
			for i in range(-5, 6):
				var t := i / 5.0
				ctx.beginPath(); ctx.moveTo(cx + t * hw * 0.34, py); ctx.lineTo(cx + t * hw * 0.9, eaveY - 1); ctx.stroke()
			# Team-colour ridge with upturned ends, and a lacquer eave line.
			ctx.strokeStyle = tc.main
			ctx.lineWidth = 3.4
			ctx.lineCap = "round"
			ctx.beginPath()
			ctx.moveTo(cx - hw * 0.4, py - 3); ctx.lineTo(cx - hw * 0.34, py); ctx.lineTo(cx + hw * 0.34, py); ctx.lineTo(cx + hw * 0.4, py - 3)
			ctx.stroke()
			ctx.strokeStyle = look.accent
			ctx.lineWidth = 1.6
			ctx.beginPath()
			ctx.moveTo(cx - hw - 3, eaveY - 5); ctx.quadraticCurveTo(cx, eaveY + 3, cx + hw + 3, eaveY - 5)
			ctx.stroke()
			return
		"yurt":  # felt dome, team bands, the smoke-ring crown on top
			var py := eaveY - rise * 0.8
			var hw := halfW * 0.95
			ctx.fillStyle = grad(ctx, cx - hw, py, cx + hw, eaveY, Palette.shade(look.roofMaterial, 0.1), Palette.shade(look.roofMaterial, -0.16))
			ctx.beginPath()
			ctx.moveTo(cx - hw, eaveY)
			ctx.bezierCurveTo(cx - hw, py + rise * 0.1, cx - hw * 0.3, py, cx, py)
			ctx.bezierCurveTo(cx + hw * 0.3, py, cx + hw, py + rise * 0.1, cx + hw, eaveY)
			ctx.closePath(); ctx.fill(); outline(ctx, 1.5)
			ctx.save(); ctx.clip()
			ctx.fillStyle = tc.main
			ctx.fillRect(cx - hw, py + (eaveY - py) * 0.42, hw * 2, 4)
			ctx.fillRect(cx - hw, eaveY - 4, hw * 2, 4)
			ctx.strokeStyle = Palette.withAlpha(look.frame, 0.6)
			ctx.lineWidth = 1
			for t in [-0.6, -0.2, 0.2, 0.6]:
				ctx.beginPath(); ctx.moveTo(cx, py); ctx.lineTo(cx + t * hw * 1.2, eaveY); ctx.stroke()
			ctx.restore()
			ctx.fillStyle = look.frame
			ctx.beginPath(); ctx.ellipse(cx, py + 1.5, hw * 0.18, 2.4, 0, 0, PI * 2); ctx.fill()
			return
		"dome":  # white dome, glowing team ring
			var py := eaveY - rise * 0.9
			var hw := halfW * 0.96
			ctx.fillStyle = grad(ctx, cx - hw * 0.6, py, cx + hw * 0.4, eaveY, "#ffffff", Palette.shade(look.roofMaterial, -0.2))
			ctx.beginPath()
			ctx.moveTo(cx - hw, eaveY)
			ctx.bezierCurveTo(cx - hw, py, cx + hw, py, cx + hw, eaveY)
			ctx.closePath(); ctx.fill(); outline(ctx, 1.2)
			# Specular highlight.
			ctx.fillStyle = "rgba(255,255,255,0.55)"
			ctx.beginPath(); ctx.ellipse(cx - hw * 0.35, py + (eaveY - py) * 0.35, hw * 0.16, (eaveY - py) * 0.12, -0.5, 0, PI * 2); ctx.fill()
			# The team's colour as a broad lit band round the dome — white
			# buildings would otherwise all look alike across the field.
			var ry := py + (eaveY - py) * 0.6
			var rw := hw * 0.92
			ctx.save()
			ctx.shadowColor = tc.main
			ctx.shadowBlur = 8
			ctx.strokeStyle = tc.main
			ctx.lineWidth = maxf(4, (eaveY - py) * 0.2)
			ctx.beginPath(); ctx.moveTo(cx - rw, ry); ctx.quadraticCurveTo(cx, ry + 5, cx + rw, ry); ctx.stroke()
			ctx.strokeStyle = tc.light
			ctx.lineWidth = 1.2
			ctx.beginPath(); ctx.moveTo(cx - rw * 0.9, ry - 1); ctx.quadraticCurveTo(cx, ry + 3.5, cx + rw * 0.9, ry - 1); ctx.stroke()
			ctx.restore()
			glowLine(ctx, cx - 3, py + 2, cx + 3, py + 2, look.accent, 1.4)
			return


## A tower's cap.
static func factionCap(ctx: Ctx, look: Dictionary, cx: float, baseY: float, halfW: float, peakY: float, tc: Dictionary) -> void:
	var rise := baseY - peakY
	match look.roof:
		"tile":  # flat, crenellated, a team band
			ctx.fillStyle = grad(ctx, cx, baseY - 8, cx, baseY, Palette.shade(look.stone, 0.15), Palette.shade(look.stone, -0.15))
			ctx.fillRect(cx - halfW, baseY - 6, halfW * 2, 6)
			var x := cx - halfW
			while x < cx + halfW - 1:
				ctx.fillRect(x, baseY - 11, halfW / 3.2, 5)
				x += halfW / 2
			ctx.beginPath(); ctx.rect(cx - halfW, baseY - 6, halfW * 2, 6); outline(ctx, 1)
			ctx.fillStyle = tc.main
			ctx.fillRect(cx - halfW, baseY - 2.5, halfW * 2, 2.5)
			return
		"pagoda":  # two curved tiers
			factionRoof(ctx, look, cx, baseY, halfW * 1.1, baseY - rise * 0.55, tc)
			factionRoof(ctx, look, cx, baseY - rise * 0.5, halfW * 0.7, peakY, tc)
			return
		"yurt", "shingle", "dome":  # a tall tent / a steep shingled cone / a spire
			if look.roof == "dome":
				# A slim spire with a lit tip.
				ctx.fillStyle = grad(ctx, cx - halfW * 0.5, peakY, cx + halfW * 0.5, baseY, "#ffffff", Palette.shade(look.stone, -0.2))
				ctx.beginPath()
				ctx.moveTo(cx - halfW * 0.8, baseY); ctx.quadraticCurveTo(cx - halfW * 0.2, peakY + rise * 0.3, cx, peakY - rise * 0.2)
				ctx.quadraticCurveTo(cx + halfW * 0.2, peakY + rise * 0.3, cx + halfW * 0.8, baseY)
				ctx.closePath(); ctx.fill(); outline(ctx, 1.1)
				ctx.fillStyle = tc.main
				ctx.fillRect(cx - halfW * 0.7, baseY - 4, halfW * 1.4, 3)
				ctx.save(); ctx.shadowColor = look.accent; ctx.shadowBlur = 8
				ctx.fillStyle = look.accent
				ctx.beginPath(); ctx.arc(cx, peakY - rise * 0.2, 2.2, 0, PI * 2); ctx.fill()
				ctx.restore()
				return
			var tall := 1.25 if look.roof == "shingle" else 1.1
			var py := baseY - rise * tall
			var body = look.roofMaterial if look.roof == "yurt" else tc.main
			ctx.fillStyle = grad(ctx, cx, py, cx, baseY, Palette.shade(body, 0.16), Palette.shade(body, -0.2))
			ctx.beginPath(); ctx.moveTo(cx - halfW, baseY); ctx.lineTo(cx, py); ctx.lineTo(cx + halfW, baseY); ctx.closePath()
			ctx.fill(); outline(ctx, 1.4)
			if look.roof == "yurt":
				ctx.fillStyle = tc.main
				ctx.beginPath()
				ctx.moveTo(cx - halfW * 0.62, baseY - rise * 0.36); ctx.lineTo(cx + halfW * 0.62, baseY - rise * 0.36)
				ctx.lineTo(cx + halfW * 0.7, baseY - rise * 0.28); ctx.lineTo(cx - halfW * 0.7, baseY - rise * 0.28)
				ctx.closePath(); ctx.fill()
				ctx.strokeStyle = look.frame; ctx.lineWidth = 1.5
				ctx.beginPath(); ctx.moveTo(cx, py); ctx.lineTo(cx, py - 6); ctx.stroke()
			else:
				ctx.strokeStyle = "rgba(0,0,0,0.25)"; ctx.lineWidth = 1
				var t := 0.3
				while t < 1:
					ctx.beginPath(); ctx.moveTo(cx - halfW * t, py + (baseY - py) * t); ctx.lineTo(cx + halfW * t, py + (baseY - py) * t); ctx.stroke()
					t += 0.22
			return


# ------------------------------------------------------- doors, lights, flags --

static func factionDoor(ctx: Ctx, look: Dictionary, cx: float, baseY: float, w: float, h: float) -> void:
	match look.roof:
		"tile":  # square door under a lintel
			ctx.fillStyle = grad(ctx, cx, baseY - h, cx, baseY, "#7a5530", "#4a3018")
			ctx.fillRect(cx - w / 2, baseY - h * 0.8, w, h * 0.8)
			ctx.beginPath(); ctx.rect(cx - w / 2, baseY - h * 0.8, w, h * 0.8); outline(ctx, 1)
			ctx.fillStyle = Palette.shade(look.stone, 0.15)
			ctx.fillRect(cx - w / 2 - 2, baseY - h * 0.8 - 3, w + 4, 3)
			return
		"pagoda":  # a sliding screen
			ctx.fillStyle = "#f4ecd8"
			ctx.fillRect(cx - w / 2, baseY - h * 0.85, w, h * 0.85)
			ctx.strokeStyle = look.frame; ctx.lineWidth = 1
			ctx.strokeRect(cx - w / 2, baseY - h * 0.85, w, h * 0.85)
			ctx.beginPath(); ctx.moveTo(cx, baseY - h * 0.85); ctx.lineTo(cx, baseY)
			ctx.moveTo(cx - w / 2, baseY - h * 0.45); ctx.lineTo(cx + w / 2, baseY - h * 0.45); ctx.stroke()
			return
		"yurt":  # the painted door of a ger
			ctx.fillStyle = grad(ctx, cx, baseY - h, cx, baseY, "#e0782a", "#a04a18")
			ctx.fillRect(cx - w / 2, baseY - h * 0.75, w, h * 0.75)
			ctx.strokeStyle = "#f0c060"; ctx.lineWidth = 0.9
			ctx.strokeRect(cx - w / 2 + 1.5, baseY - h * 0.75 + 1.5, w - 3, h * 0.75 - 3)
			return
		"dome":  # a lit slot
			ctx.fillStyle = "#2a3040"
			ctx.beginPath(); ctx.roundRect(cx - w / 2, baseY - h * 0.8, w, h * 0.8, [w / 2, w / 2, 0, 0]); ctx.fill()
			glowLine(ctx, cx, baseY - h * 0.7, cx, baseY - 2, look.accent, 1.4)
			return
		_:  # the Jarls: a dark arched door
			ctx.fillStyle = grad(ctx, cx, baseY - h, cx, baseY, "#5a3c20", "#2e1e10")
			ctx.beginPath()
			ctx.moveTo(cx - w / 2, baseY); ctx.lineTo(cx - w / 2, baseY - h * 0.55)
			ctx.quadraticCurveTo(cx, baseY - h, cx + w / 2, baseY - h * 0.55); ctx.lineTo(cx + w / 2, baseY)
			ctx.closePath(); ctx.fill(); outline(ctx, 1.2)


static func factionWindow(ctx: Ctx, look: Dictionary, cx: float, cy: float, s: float) -> void:
	if look.roof == "dome":
		ctx.save()
		ctx.shadowColor = look.light
		ctx.shadowBlur = 5
		ctx.fillStyle = look.light
		ctx.beginPath(); ctx.roundRect(cx - s * 1.3, cy - s * 0.5, s * 2.6, s, s * 0.5); ctx.fill()
		ctx.restore()
		return
	if look.roof == "pagoda":
		ctx.fillStyle = look.light
		ctx.beginPath(); ctx.arc(cx, cy, s, 0, PI * 2); ctx.fill()
		ctx.strokeStyle = look.frame; ctx.lineWidth = 0.8
		ctx.beginPath(); ctx.moveTo(cx - s, cy); ctx.lineTo(cx + s, cy); ctx.moveTo(cx, cy - s); ctx.lineTo(cx, cy + s); ctx.stroke()
		return
	ctx.fillStyle = "rgba(20,16,10,0.65)"
	ctx.fillRect(cx - s, cy - s, s * 2, s * 2)
	ctx.fillStyle = look.light
	ctx.fillRect(cx - s + 1, cy - s + 1, s * 2 - 2, s * 2 - 2)


## A building's standard, in the faction's form.
static func factionBanner(ctx: Ctx, look: Dictionary, x: float, y: float, tc: Dictionary, time: float, id: int) -> void:
	var wave := sin(time * 3 + id) * 1.5
	ctx.strokeStyle = "#c8d0dc" if look.banner == "holo" else "#4a3620"
	ctx.lineWidth = 1.5
	ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x, y - (22 if look.banner == "nobori" else 17)); ctx.stroke()
	match look.banner:
		"vexillum":  # square cloth hung from a crossbar, gold finial
			ctx.beginPath(); ctx.moveTo(x - 6, y - 15); ctx.lineTo(x + 6, y - 15); ctx.stroke()
			ctx.fillStyle = tc.main
			ctx.beginPath()
			ctx.moveTo(x - 5.5, y - 15); ctx.lineTo(x + 5.5, y - 15); ctx.lineTo(x + 5.5 + wave * 0.3, y - 5); ctx.lineTo(x - 5.5 + wave * 0.3, y - 5)
			ctx.closePath(); ctx.fill(); outline(ctx, 0.8)
			ctx.fillStyle = look.accent
			ctx.beginPath(); ctx.arc(x, y - 18.5, 1.8, 0, PI * 2); ctx.fill()
			ctx.fillRect(x - 5.5, y - 6.2, 11, 1.2)
			return
		"raven":  # a triangular war-flag with a ragged edge
			ctx.fillStyle = tc.main
			ctx.beginPath()
			ctx.moveTo(x, y - 17); ctx.lineTo(x + 12, y - 13 + wave); ctx.lineTo(x + 9, y - 11 + wave)
			ctx.lineTo(x + 11, y - 9 + wave); ctx.lineTo(x, y - 8)
			ctx.closePath(); ctx.fill(); outline(ctx, 0.8)
			ctx.fillStyle = "#1a1a1a"
			ctx.beginPath(); ctx.arc(x + 4, y - 12.5, 1.5, 0, PI * 2); ctx.fill()
			return
		"nobori":  # a tall vertical banner on an L-pole
			ctx.beginPath(); ctx.moveTo(x, y - 22); ctx.lineTo(x + 6, y - 22); ctx.stroke()
			ctx.fillStyle = tc.main
			ctx.fillRect(x + 0.5, y - 21.5, 5.5 + wave * 0.1, 15)
			ctx.fillStyle = "#f4ecd8"
			ctx.beginPath(); ctx.arc(x + 3.2, y - 17, 1.8, 0, PI * 2); ctx.fill()
			return
		"tug":  # horsehair standard: a trident finial over streaming hair
			ctx.fillStyle = "#e8e0d0"
			for i in 5:
				ctx.beginPath()
				ctx.moveTo(x - 2 + i, y - 15)
				ctx.quadraticCurveTo(x - 3 + i + wave, y - 10, x - 4 + i * 1.6 + wave * 1.4, y - 5)
				ctx.lineTo(x - 3 + i * 1.6 + wave * 1.4, y - 5)
				ctx.quadraticCurveTo(x - 2 + i + wave, y - 10, x - 1 + i, y - 15)
				ctx.fill()
			ctx.fillStyle = tc.main
			ctx.fillRect(x - 3.5, y - 16.5, 7, 2.5)
			ctx.strokeStyle = look.accent; ctx.lineWidth = 1.2
			ctx.beginPath(); ctx.moveTo(x - 3, y - 20); ctx.lineTo(x - 3, y - 17); ctx.moveTo(x, y - 22); ctx.lineTo(x, y - 17); ctx.moveTo(x + 3, y - 20); ctx.lineTo(x + 3, y - 17); ctx.stroke()
			return
		"holo":  # a projected flag: team colour, translucent, scan lines
			ctx.save()
			ctx.shadowColor = tc.main; ctx.shadowBlur = 6
			ctx.fillStyle = Palette.withAlpha(tc.light, 0.6)
			ctx.fillRect(x + 1, y - 16, 11, 7)
			ctx.restore()
			ctx.strokeStyle = Palette.withAlpha("#ffffff", 0.4); ctx.lineWidth = 0.6
			var yy := y - 15
			while yy < y - 9:
				ctx.beginPath(); ctx.moveTo(x + 1, yy); ctx.lineTo(x + 12, yy); ctx.stroke()
				yy += 1.6
			glowLine(ctx, x, y - 17, x, y - 8, look.accent, 1)
			return
		_:  # pennant — the Kingdom's own is drawn by the caller
			return


# ---------------------------------------------------------------- soldiers --

## A faction helmet over a head of radius `hr` centred at (cx, cy). `kind` is
## what the unit asked for: a "hood" archer and a "full" knight wear different
## things even within one faction.
static func factionHelm(ctx: Ctx, look: Dictionary, cx: float, cy: float, hr: float, kind: String, tc: Dictionary) -> void:
	match look.helm:
		"galea":  # bronze bowl, cheek guards, a crest in the team colour
			if kind == "bare":
				return
			ctx.fillStyle = grad(ctx, cx, cy - hr, cx, cy + hr * 0.3, Palette.shade(look.armour, 0.22), Palette.shade(look.armour, -0.2))
			ctx.beginPath(); ctx.arc(cx, cy - hr * 0.1, hr * 1.04, PI * 0.92, PI * 2.08); ctx.fill(); outline(ctx, 1)
			ctx.fillRect(cx - hr * 1.02, cy - hr * 0.2, hr * 0.28, hr * 0.8)
			ctx.fillRect(cx + hr * 0.74, cy - hr * 0.2, hr * 0.28, hr * 0.8)
			if kind != "hood":
				ctx.fillStyle = tc.main
				ctx.beginPath()
				ctx.ellipse(cx, cy - hr * 1.1, hr * 1.05, hr * 0.42, 0, PI, PI * 2)
				ctx.fill(); outline(ctx, 0.8)
			return
		"nasal":  # a conical iron helm with a nose guard
			if kind == "bare":
				return
			if kind == "hood":  # leather cap with fur
				ctx.fillStyle = "#6a4a2c"
				ctx.beginPath(); ctx.arc(cx, cy - hr * 0.05, hr * 1.05, PI * 0.95, PI * 2.05); ctx.fill(); outline(ctx, 1)
				return
			ctx.fillStyle = grad(ctx, cx, cy - hr * 1.5, cx, cy + hr * 0.2, Palette.shade(look.armour, 0.2), Palette.shade(look.armour, -0.25))
			ctx.beginPath()
			ctx.moveTo(cx - hr * 1.02, cy - hr * 0.05)
			ctx.quadraticCurveTo(cx - hr * 0.9, cy - hr * 1.2, cx, cy - hr * 1.55)
			ctx.quadraticCurveTo(cx + hr * 0.9, cy - hr * 1.2, cx + hr * 1.02, cy - hr * 0.05)
			ctx.closePath(); ctx.fill(); outline(ctx, 1)
			ctx.strokeStyle = Palette.shade(look.armour, -0.35); ctx.lineWidth = 1.4
			ctx.beginPath(); ctx.moveTo(cx, cy - hr * 0.1); ctx.lineTo(cx, cy + hr * 0.5); ctx.stroke()
			ctx.fillStyle = look.accent
			ctx.fillRect(cx - hr, cy - hr * 0.18, hr * 2, hr * 0.18)
			return
		"kabuto":  # lacquered bowl, flared neck guard, gold crest
			if kind == "bare" or kind == "hood":  # a conical straw hat
				ctx.fillStyle = grad(ctx, cx, cy - hr * 1.3, cx, cy, "#e8d09a", "#b8985a")
				ctx.beginPath()
				ctx.moveTo(cx - hr * 1.7, cy - hr * 0.1); ctx.lineTo(cx, cy - hr * 1.2); ctx.lineTo(cx + hr * 1.7, cy - hr * 0.1)
				ctx.closePath(); ctx.fill(); outline(ctx, 1)
				return
			ctx.fillStyle = grad(ctx, cx, cy - hr, cx, cy + hr * 0.5, Palette.shade(look.armour, 0.2), Palette.shade(look.armour, -0.2))
			ctx.beginPath()
			ctx.moveTo(cx - hr * 1.5, cy + hr * 0.35)
			ctx.quadraticCurveTo(cx - hr * 1.2, cy - hr * 0.1, cx - hr * 0.95, cy - hr * 0.25)
			ctx.arc(cx, cy - hr * 0.2, hr * 0.98, PI, PI * 2)
			ctx.quadraticCurveTo(cx + hr * 1.2, cy - hr * 0.1, cx + hr * 1.5, cy + hr * 0.35)
			ctx.closePath(); ctx.fill(); outline(ctx, 1)
			ctx.strokeStyle = tc.main; ctx.lineWidth = 1.2  # team lacing on the neck guard
			ctx.beginPath(); ctx.moveTo(cx - hr * 1.3, cy + hr * 0.15); ctx.lineTo(cx + hr * 1.3, cy + hr * 0.15); ctx.stroke()
			ctx.strokeStyle = "#e8c050"; ctx.lineWidth = 1.6  # maedate crest
			ctx.beginPath()
			ctx.moveTo(cx - hr * 0.9, cy - hr * 1.5); ctx.quadraticCurveTo(cx, cy - hr * 0.7, cx + hr * 0.9, cy - hr * 1.5)
			ctx.stroke()
			return
		"steppe":  # fur-brimmed pointed helm
			ctx.fillStyle = grad(ctx, cx, cy - hr * 1.6, cx, cy, "#c8a060" if kind == "bare" else Palette.shade(look.armour, 0.25), "#8a6a3a" if kind == "bare" else Palette.shade(look.armour, -0.2))
			ctx.beginPath()
			ctx.moveTo(cx - hr * 0.9, cy - hr * 0.3)
			ctx.lineTo(cx, cy - hr * (1.2 if kind == "bare" else 1.7))
			ctx.lineTo(cx + hr * 0.9, cy - hr * 0.3)
			ctx.closePath(); ctx.fill(); outline(ctx, 1)
			ctx.fillStyle = "#5a3e24"  # fur brim
			ctx.beginPath(); ctx.ellipse(cx, cy - hr * 0.3, hr * 1.12, hr * 0.34, 0, 0, PI * 2); ctx.fill()
			if kind != "bare":
				ctx.fillStyle = tc.main
				ctx.beginPath(); ctx.arc(cx, cy - hr * 1.72, hr * 0.2, 0, PI * 2); ctx.fill()
			return
		"visor":  # smooth white helm with a lit visor band
			ctx.fillStyle = grad(ctx, cx - hr * 0.6, cy - hr, cx + hr * 0.4, cy + hr * 0.6, "#ffffff", Palette.shade(look.armour, -0.22))
			ctx.beginPath(); ctx.arc(cx, cy - hr * 0.05, hr * (1.0 if kind == "bare" else 1.12), 0, PI * 2); ctx.fill(); outline(ctx, 1)
			if kind == "bare":  # a work cap with goggles
				ctx.fillStyle = "#3a4250"
				ctx.fillRect(cx - hr * 0.8, cy - hr * 0.2, hr * 1.6, hr * 0.32)
				return
			ctx.save()
			ctx.shadowColor = look.accent; ctx.shadowBlur = 5
			ctx.fillStyle = look.accent
			ctx.beginPath(); ctx.roundRect(cx - hr * 0.85, cy - hr * 0.22, hr * 1.7, hr * 0.38, hr * 0.19); ctx.fill()
			ctx.restore()
			ctx.fillStyle = tc.main
			ctx.fillRect(cx - hr * 0.16, cy - hr * 1.14, hr * 0.32, hr * 0.6)
			return


## Faction armour over a soldier's surcoat.
static func factionArmour(ctx: Ctx, look: Dictionary, r: float, tc: Dictionary, heavy: bool) -> void:
	match look.helm:
		"galea":  # lorica: bronze bands across the chest
			if not heavy:
				return
			ctx.strokeStyle = Palette.shade(look.armour, -0.05)
			ctx.lineWidth = 1.6
			for yy in [-0.3, -0.12, 0.06]:
				ctx.beginPath(); ctx.moveTo(-r * 0.42, r * yy); ctx.quadraticCurveTo(0, r * (yy + 0.08), r * 0.42, r * yy); ctx.stroke()
			return
		"nasal":  # a fur mantle across the shoulders
			ctx.fillStyle = "#5a4230"
			ctx.beginPath(); ctx.ellipse(0, -r * 0.46, r * 0.5, r * 0.16, 0, 0, PI * 2); ctx.fill()
			ctx.fillStyle = "rgba(255,255,255,0.12)"
			ctx.beginPath(); ctx.ellipse(-r * 0.1, -r * 0.5, r * 0.3, r * 0.06, 0, 0, PI * 2); ctx.fill()
			return
		"kabuto":  # lacquered lamellar rows, team-colour lacing
			ctx.fillStyle = look.armour
			ctx.beginPath(); ctx.roundRect(-r * 0.4, -r * 0.4, r * 0.8, r * 0.62, r * 0.12); ctx.fill(); outline(ctx, 1)
			ctx.strokeStyle = tc.main; ctx.lineWidth = 1
			for yy in [-0.26, -0.1, 0.06]:
				ctx.beginPath(); ctx.moveTo(-r * 0.38, r * yy); ctx.lineTo(r * 0.38, r * yy); ctx.stroke()
			return
		"steppe":  # leather lamellar and a fur collar
			ctx.fillStyle = Palette.shade(look.armour, 0.05)
			ctx.beginPath(); ctx.roundRect(-r * 0.38, -r * 0.36, r * 0.76, r * 0.56, r * 0.1); ctx.fill()
			ctx.fillStyle = "rgba(0,0,0,0.18)"
			var yy := -0.3
			while yy < 0.18:
				var xx := -0.32
				while xx < 0.34:
					ctx.fillRect(r * xx, r * yy, r * 0.08, r * 0.06)
					xx += 0.13
				yy += 0.12
			ctx.fillStyle = "#6a4a30"
			ctx.beginPath(); ctx.ellipse(0, -r * 0.48, r * 0.36, r * 0.12, 0, 0, PI * 2); ctx.fill()
			return
		"visor":  # a white plastron with a lit seam and core
			ctx.fillStyle = grad(ctx, 0, -r * 0.4, 0, r * 0.3, "#ffffff", Palette.shade(look.armour, -0.18))
			ctx.beginPath(); ctx.roundRect(-r * 0.36, -r * 0.42, r * 0.72, r * 0.6, r * 0.18); ctx.fill(); outline(ctx, 1)
			glowLine(ctx, 0, -r * 0.36, 0, r * 0.12, look.accent, 1)
			ctx.fillStyle = tc.main
			ctx.beginPath(); ctx.arc(0, -r * 0.14, r * 0.09, 0, PI * 2); ctx.fill()
			return


## A shield on the off arm (sx, sy is its centre).
static func factionShield(ctx: Ctx, look: Dictionary, sx: float, sy: float, r: float, tc: Dictionary) -> void:
	match look.shield:
		"scutum":  # tall curved rectangle, team colour, gold boss and rim
			ctx.fillStyle = grad(ctx, sx - r * 0.3, sy, sx + r * 0.3, sy, Palette.shade(tc.main, 0.15), Palette.shade(tc.main, -0.2))
			ctx.beginPath(); ctx.roundRect(sx - r * 0.3, sy - r * 0.55, r * 0.6, r * 1.1, r * 0.1); ctx.fill(); outline(ctx, 1.3)
			ctx.strokeStyle = look.accent; ctx.lineWidth = 1.1
			ctx.strokeRect(sx - r * 0.26, sy - r * 0.51, r * 0.52, r * 1.02)
			ctx.fillStyle = look.accent
			ctx.beginPath(); ctx.arc(sx, sy, r * 0.12, 0, PI * 2); ctx.fill()
			return
		"painted":  # big round board, quartered in team colour
			ctx.fillStyle = "#e8dcc4"
			ctx.beginPath(); ctx.arc(sx, sy, r * 0.5, 0, PI * 2); ctx.fill(); outline(ctx, 1.3)
			ctx.fillStyle = tc.main
			for a in [0.0, PI]:
				ctx.beginPath(); ctx.moveTo(sx, sy); ctx.arc(sx, sy, r * 0.5, a, a + PI / 2); ctx.closePath(); ctx.fill()
			ctx.fillStyle = "#8f96a0"
			ctx.beginPath(); ctx.arc(sx, sy, r * 0.13, 0, PI * 2); ctx.fill()
			return
		"buckler":  # a small wicker round
			ctx.fillStyle = "#b08a50"
			ctx.beginPath(); ctx.arc(sx, sy, r * 0.3, 0, PI * 2); ctx.fill(); outline(ctx, 1)
			ctx.strokeStyle = "rgba(80,50,20,0.5)"; ctx.lineWidth = 0.8
			ctx.beginPath(); ctx.arc(sx, sy, r * 0.2, 0, PI * 2); ctx.stroke()
			ctx.fillStyle = tc.main
			ctx.beginPath(); ctx.arc(sx, sy, r * 0.09, 0, PI * 2); ctx.fill()
			return
		"energy":  # a projected barrier: translucent team colour, lit edge
			ctx.save()
			ctx.shadowColor = look.accent; ctx.shadowBlur = 6
			ctx.fillStyle = Palette.withAlpha(tc.light, 0.35)
			ctx.beginPath(); ctx.ellipse(sx, sy, r * 0.34, r * 0.5, 0, 0, PI * 2); ctx.fill()
			ctx.strokeStyle = Palette.withAlpha(look.accent, 0.9); ctx.lineWidth = 1.2
			ctx.stroke()
			ctx.restore()
			return
		"none":
			return
