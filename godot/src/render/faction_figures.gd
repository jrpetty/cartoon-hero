## render/faction_figures.ts — faction soldiers, drawn whole.
##
## A faction that only changes its helmet reads as a colour swap. What makes a
## Roman a Roman at thirty pixels tall is the silhouette — the cape and the bare
## legs under a strip skirt; a Jarl is a fur cloak, a beard and an axe; a
## samurai is the flag rising off his back and the flare of the hakama; a
## steppe rider is a long belted robe, a fur hat and a quiver; the Ascendancy
## are slim white hardsuits with light at the joints, riding on sleds instead of
## horses. So each faction redraws the whole figure — back gear, legs, body,
## face, weapon, bow and mount — and the shared unit art calls in here.
##
## Every part keeps the team colour somewhere large: the cape, the tunic, the
## flag, the robe, the stripe. Nothing here is magic; the Ascendancy's glow is
## engineering.
##
## `FigureInfo` (TS interface) is a Dictionary here: {heavy, soldier, step, seed}.
class_name FactionFigures

const SKIN := "#e8b98a"


static func grad(ctx: Ctx, x0: float, y0: float, x1: float, y1: float, a, b) -> Ctx.CanvasGradient:
	var g := ctx.createLinearGradient(x0, y0, x1, y1)
	g.addColorStop(0, a)
	g.addColorStop(1, b)
	return g


static func outline(ctx: Ctx, w: float = 1.2) -> void:
	ctx.strokeStyle = "rgba(20,16,10,0.45)"
	ctx.lineWidth = w
	ctx.stroke()


static func limb(ctx: Ctx, x0: float, y0: float, x1: float, y1: float, w: float, c0, c1) -> void:
	ctx.strokeStyle = grad(ctx, x0, y0, x1, y1, c0, c1)
	ctx.lineCap = "round"
	ctx.lineWidth = w
	ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke()


static func glow(ctx: Ctx, col, blur: float, draw: Callable) -> void:
	ctx.save()
	ctx.shadowColor = col
	ctx.shadowBlur = blur
	draw.call()
	ctx.restore()


## The standard torso shape every figure starts from, widened or narrowed.
static func torsoPath(ctx: Ctx, r: float, wide: float = 1.0, hem: float = 0.42) -> void:
	ctx.beginPath()
	ctx.moveTo(-r * 0.5 * wide, r * hem)
	ctx.quadraticCurveTo(-r * 0.6 * wide, -r * 0.5, 0, -r * 0.58)
	ctx.quadraticCurveTo(r * 0.6 * wide, -r * 0.5, r * 0.5 * wide, r * hem)
	ctx.quadraticCurveTo(0, r * (hem + 0.18), -r * 0.5 * wide, r * hem)
	ctx.closePath()


# ================================================================= bodies ==

## The whole figure below the head, in the faction's style. Returns false for
## the Kingdom, whose original art stands.
static func factionBody(ctx: Ctx, look: Dictionary, r: float, cloth, clothDark, info: Dictionary, tc: Dictionary) -> bool:
	match look.helm:
		"galea":
			legionBody(ctx, look, r, cloth, info, tc); return true
		"nasal":
			jarlBody(ctx, look, r, cloth, info, tc); return true
		"kabuto":
			shogunBody(ctx, look, r, cloth, info, tc); return true
		"steppe":
			steppeBody(ctx, look, r, cloth, clothDark, info, tc); return true
		"visor":
			ascendBody(ctx, look, r, info, tc); return true
		_:
			return false


static func legionBody(ctx: Ctx, look: Dictionary, r: float, cloth, info: Dictionary, tc: Dictionary) -> void:
	var step: float = info.step
	# Cape in the team's colour, peeking out at the sides and hanging to the calf.
	if info.soldier:
		ctx.fillStyle = grad(ctx, 0, -r * 0.5, 0, r * 0.75, Palette.shade(tc.main, -0.05), Palette.shade(tc.main, -0.38))
		ctx.beginPath()
		ctx.moveTo(-r * 0.44, -r * 0.5)
		ctx.quadraticCurveTo(-r * 0.78, r * 0.15, -r * 0.62 + step * r * 0.06, r * 0.74)
		ctx.lineTo(r * 0.62 + step * r * 0.06, r * 0.74)
		ctx.quadraticCurveTo(r * 0.78, r * 0.15, r * 0.44, -r * 0.5)
		ctx.closePath(); ctx.fill(); outline(ctx, 1)
	# Bare legs, sandals laced up the shin.
	for side in [1, -1]:
		var sw: float = step * side
		var hipX: float = side * r * 0.2
		var footX: float = hipX + sw * r * 0.4
		var lift := maxf(0, sw) * r * 0.16
		limb(ctx, hipX, r * 0.32, footX, r * 0.8 - lift, r * 0.19, SKIN, Palette.shade(SKIN, -0.2))
		ctx.strokeStyle = "#5a3a1c"; ctx.lineWidth = 0.9
		for t in [0.55, 0.68]:
			var x: float = hipX + (footX - hipX) * t
			var y: float = r * 0.32 + (r * 0.8 - lift - r * 0.32) * t
			ctx.beginPath(); ctx.moveTo(x - r * 0.1, y - r * 0.03); ctx.lineTo(x + r * 0.1, y + r * 0.03); ctx.stroke()
		ctx.fillStyle = "#6a4424"
		ctx.beginPath(); ctx.ellipse(footX + r * 0.04, r * 0.8 - lift, r * 0.14, r * 0.07, 0, 0, PI * 2); ctx.fill()
	# Tunic.
	ctx.fillStyle = grad(ctx, 0, -r * 0.55, 0, r * 0.45, Palette.shade(tc.main if info.soldier else "#e8dcc0", 0.12), Palette.shade(tc.dark if info.soldier else "#b8a888", -0.05))
	torsoPath(ctx, r, 1, 0.3); ctx.fill(); outline(ctx, 1.4)
	if not info.soldier:
		ctx.fillStyle = "#8a6a44"; ctx.fillRect(-r * 0.44, r * 0.14, r * 0.88, r * 0.09)
		return
	# Pteruges: a skirt of leather strips at the waist.
	for i in 6:
		var x := -r * 0.42 + i * r * 0.15
		ctx.fillStyle = "#6a4424" if i % 2 else "#8a5a30"
		ctx.fillRect(x, r * 0.2, r * 0.13, r * 0.28)
		ctx.fillStyle = look.accent
		ctx.fillRect(x + r * 0.03, r * 0.43, r * 0.07, r * 0.05)
	if info.heavy:
		# Lorica segmentata: bronze bands wrapping the chest, and over the shoulders.
		ctx.fillStyle = grad(ctx, 0, -r * 0.5, 0, r * 0.2, Palette.shade(look.armour, 0.18), Palette.shade(look.armour, -0.22))
		ctx.beginPath(); ctx.roundRect(-r * 0.42, -r * 0.46, r * 0.84, r * 0.66, r * 0.14); ctx.fill(); outline(ctx, 1)
		ctx.strokeStyle = Palette.shade(look.armour, -0.4); ctx.lineWidth = 1
		for y in [-0.26, -0.1, 0.06]:
			ctx.beginPath(); ctx.moveTo(-r * 0.4, r * y); ctx.quadraticCurveTo(0, r * (y + 0.06), r * 0.4, r * y); ctx.stroke()
		for sx in [-1, 1]:
			ctx.fillStyle = Palette.shade(look.armour, 0.1)
			ctx.beginPath(); ctx.ellipse(sx * r * 0.42, -r * 0.38, r * 0.2, r * 0.13, sx * 0.5, 0, PI * 2); ctx.fill(); outline(ctx, 0.9)
	else:
		# A leather cuirass for the lighter troops.
		ctx.fillStyle = grad(ctx, 0, -r * 0.45, 0, r * 0.2, "#9a6a3c", "#6a4424")
		ctx.beginPath(); ctx.roundRect(-r * 0.36, -r * 0.42, r * 0.72, r * 0.58, r * 0.16); ctx.fill(); outline(ctx, 1)
		ctx.strokeStyle = "rgba(0,0,0,0.25)"; ctx.lineWidth = 0.9
		ctx.beginPath(); ctx.moveTo(0, -r * 0.4); ctx.lineTo(0, r * 0.12); ctx.stroke()
	# Cingulum: the belt, with its studded apron.
	ctx.fillStyle = look.accent
	ctx.fillRect(-r * 0.44, r * 0.14, r * 0.88, r * 0.08)
	ctx.strokeStyle = "#5a3a1c"; ctx.lineWidth = 1.1
	for x in [-0.08, 0, 0.08]:
		ctx.beginPath(); ctx.moveTo(r * x, r * 0.22); ctx.lineTo(r * x, r * 0.44); ctx.stroke()


static func jarlBody(ctx: Ctx, look: Dictionary, r: float, cloth, info: Dictionary, tc: Dictionary) -> void:
	var step: float = info.step
	# A heavy fur cloak with a shaggy hem, wider than the man.
	var fur := "#6a5a48" if int(info.seed) % 2 else "#5a4a3a"
	ctx.fillStyle = grad(ctx, 0, -r * 0.5, 0, r * 0.7, Palette.shade(fur, 0.12), Palette.shade(fur, -0.2))
	ctx.beginPath()
	ctx.moveTo(-r * 0.5, -r * 0.52)
	ctx.quadraticCurveTo(-r * 0.86, r * 0.1, -r * 0.7, r * 0.62)
	for i in 8:
		ctx.lineTo(-r * 0.7 + i * r * 0.2, r * (0.62 + (i % 2) * 0.1))
	ctx.quadraticCurveTo(r * 0.86, r * 0.1, r * 0.5, -r * 0.52)
	ctx.closePath(); ctx.fill(); outline(ctx, 1)
	# Baggy trousers with wrapped shins and fur boots.
	for side in [1, -1]:
		var sw: float = step * side
		var hipX: float = side * r * 0.25
		var footX: float = hipX + sw * r * 0.38
		var lift := maxf(0, sw) * r * 0.14
		limb(ctx, hipX, r * 0.3, footX, r * 0.78 - lift, r * 0.3, "#6a6254", "#4a4438")
		ctx.strokeStyle = "#c8b890"; ctx.lineWidth = 1
		for t in [0.5, 0.64, 0.78]:
			var x: float = hipX + (footX - hipX) * t
			var y: float = r * 0.3 + (r * 0.78 - lift - r * 0.3) * t
			ctx.beginPath(); ctx.moveTo(x - r * 0.13, y - r * 0.05); ctx.lineTo(x + r * 0.13, y + r * 0.02); ctx.stroke()
		ctx.fillStyle = "#7a5a3a"
		ctx.beginPath(); ctx.ellipse(footX + r * 0.04, r * 0.8 - lift, r * 0.18, r * 0.1, 0, 0, PI * 2); ctx.fill()
	# Tunic, broad in the shoulder, in the team's colour.
	ctx.fillStyle = grad(ctx, 0, -r * 0.55, 0, r * 0.5, Palette.shade(tc.main if info.soldier else cloth, 0.1), Palette.shade(tc.dark if info.soldier else cloth, -0.2))
	torsoPath(ctx, r, 1.14, 0.46); ctx.fill(); outline(ctx, 1.4)
	if info.soldier and info.heavy:
		# A mail shirt: iron rings over the tunic.
		ctx.fillStyle = grad(ctx, 0, -r * 0.5, 0, r * 0.4, Palette.shade(look.armour, 0.12), Palette.shade(look.armour, -0.25))
		torsoPath(ctx, r, 1.04, 0.36); ctx.fill(); outline(ctx, 1)
		ctx.fillStyle = "rgba(30,34,40,0.35)"
		var y := -0.4
		while y < 0.34:
			var x := -0.44
			while x < 0.46:
				# (y * 10) % 2 — truthy unless exactly 0 (JS numbers).
				ctx.beginPath(); ctx.arc(r * (x + (0.05 if fmod(y * 10, 2) != 0.0 else 0.0)), r * y, r * 0.025, 0, PI * 2); ctx.fill()
				x += 0.11
			y += 0.11
	# Belt with a heavy bronze buckle.
	ctx.fillStyle = "#3e2a18"; ctx.fillRect(-r * 0.5, r * 0.16, r, r * 0.1)
	ctx.fillStyle = look.accent; ctx.fillRect(-r * 0.08, r * 0.14, r * 0.16, r * 0.14)
	# Fur collar over the shoulders.
	ctx.fillStyle = Palette.shade(fur, 0.22)
	ctx.beginPath(); ctx.ellipse(0, -r * 0.5, r * 0.46, r * 0.14, 0, 0, PI * 2); ctx.fill()


static func shogunBody(ctx: Ctx, look: Dictionary, r: float, cloth, info: Dictionary, tc: Dictionary) -> void:
	var step: float = info.step
	# Sashimono: a banner on a pole rising off the back — the silhouette.
	if info.soldier:
		ctx.strokeStyle = "#2a2018"; ctx.lineWidth = r * 0.07
		ctx.beginPath(); ctx.moveTo(r * 0.18, -r * 0.3); ctx.lineTo(r * 0.22, -r * 2.15); ctx.stroke()
		ctx.beginPath(); ctx.moveTo(r * 0.22, -r * 2.1); ctx.lineTo(r * 0.62, -r * 2.1); ctx.stroke()
		var flutter := sin(float(info.seed) + step * 2) * r * 0.03
		ctx.fillStyle = grad(ctx, r * 0.22, -r * 2.1, r * 0.62, -r * 1.3, Palette.shade(tc.main, 0.1), Palette.shade(tc.main, -0.15))
		ctx.beginPath()
		ctx.moveTo(r * 0.24, -r * 2.08); ctx.lineTo(r * 0.6, -r * 2.08)
		ctx.lineTo(r * 0.6 + flutter, -r * 1.3); ctx.lineTo(r * 0.24 + flutter, -r * 1.3)
		ctx.closePath(); ctx.fill(); outline(ctx, 0.9)
		ctx.fillStyle = "#f4ecd8"  # mon
		ctx.beginPath(); ctx.arc(r * 0.42 + flutter * 0.5, -r * 1.72, r * 0.1, 0, PI * 2); ctx.fill()
	# Hakama: wide pleated trousers that read as a skirt.
	var kick := step * r * 0.08
	ctx.fillStyle = grad(ctx, 0, r * 0.1, 0, r * 0.85, "#34344a" if info.soldier else "#4a4238", "#1e1e2c" if info.soldier else "#2e2820")
	ctx.beginPath()
	ctx.moveTo(-r * 0.38, r * 0.16); ctx.lineTo(r * 0.38, r * 0.16)
	ctx.lineTo(r * 0.6 + kick, r * 0.8); ctx.lineTo(-r * 0.6 - kick, r * 0.8)
	ctx.closePath(); ctx.fill(); outline(ctx, 1)
	ctx.strokeStyle = "rgba(255,255,255,0.12)"; ctx.lineWidth = 0.9
	for x in [-0.22, 0, 0.22]:
		ctx.beginPath(); ctx.moveTo(r * x, r * 0.22); ctx.lineTo(r * x * 1.5, r * 0.78); ctx.stroke()
	ctx.fillStyle = "#f4f0e6"  # tabi
	for sx in [-1, 1]:
		ctx.beginPath(); ctx.ellipse(sx * r * 0.34 + (kick if sx > 0 else -kick), r * 0.82, r * 0.12, r * 0.06, 0, 0, PI * 2); ctx.fill()
	if not info.soldier:
		# Farmers wear a short kimono and an obi.
		ctx.fillStyle = grad(ctx, 0, -r * 0.55, 0, r * 0.3, Palette.shade(cloth, 0.1), Palette.shade(cloth, -0.2))
		torsoPath(ctx, r, 1, 0.26); ctx.fill(); outline(ctx, 1.2)
		ctx.strokeStyle = "#f4ecd8"; ctx.lineWidth = 1.2
		ctx.beginPath(); ctx.moveTo(-r * 0.2, -r * 0.54); ctx.lineTo(r * 0.06, -r * 0.1); ctx.moveTo(r * 0.2, -r * 0.54); ctx.lineTo(-r * 0.06, -r * 0.1); ctx.stroke()
		ctx.fillStyle = tc.main; ctx.fillRect(-r * 0.44, r * 0.06, r * 0.88, r * 0.12)
		return
	if info.heavy:
		# Kusazuri: plated tassets over the hakama.
		for i in 4:
			ctx.fillStyle = Palette.shade(look.armour, -0.05 if i % 2 else 0.08)
			ctx.fillRect(-r * 0.44 + i * r * 0.22, r * 0.16, r * 0.2, r * 0.26)
			ctx.strokeStyle = tc.main; ctx.lineWidth = 0.8
			ctx.beginPath(); ctx.moveTo(-r * 0.44 + i * r * 0.22, r * 0.26); ctx.lineTo(-r * 0.24 + i * r * 0.22, r * 0.26); ctx.stroke()
		# Do: the lacquered cuirass, laced in rows.
		ctx.fillStyle = grad(ctx, 0, -r * 0.55, 0, r * 0.2, Palette.shade(look.armour, 0.16), Palette.shade(look.armour, -0.2))
		ctx.beginPath(); ctx.roundRect(-r * 0.44, -r * 0.52, r * 0.88, r * 0.72, r * 0.1); ctx.fill(); outline(ctx, 1.2)
		ctx.strokeStyle = tc.main; ctx.lineWidth = 1.1
		for y in [-0.34, -0.18, -0.02, 0.12]:
			ctx.beginPath(); ctx.moveTo(-r * 0.42, r * y); ctx.lineTo(r * 0.42, r * y); ctx.stroke()
		ctx.fillStyle = "#f4ecd8"
		for x in [-0.24, 0, 0.24]:
			ctx.beginPath(); ctx.arc(r * x, -r * 0.34, r * 0.03, 0, PI * 2); ctx.fill()
		# Sode: the great square shoulder plates.
		for sx in [-1, 1]:
			ctx.save()
			ctx.translate(sx * r * 0.52, -r * 0.34)
			ctx.rotate(sx * 0.25)
			ctx.fillStyle = Palette.shade(look.armour, 0.05)
			ctx.fillRect(-r * 0.17, -r * 0.14, r * 0.34, r * 0.36)
			ctx.strokeStyle = tc.main; ctx.lineWidth = 0.9
			for y in [-0.04, 0.06, 0.16]:
				ctx.beginPath(); ctx.moveTo(-r * 0.17, r * y); ctx.lineTo(r * 0.17, r * y); ctx.stroke()
			ctx.strokeStyle = "rgba(20,16,10,0.5)"; ctx.lineWidth = 1
			ctx.strokeRect(-r * 0.17, -r * 0.14, r * 0.34, r * 0.36)
			ctx.restore()
	else:
		# Ashigaru: a simple lacquered jacket in the team colour.
		ctx.fillStyle = grad(ctx, 0, -r * 0.55, 0, r * 0.3, Palette.shade(tc.main, 0.1), Palette.shade(tc.dark, -0.1))
		torsoPath(ctx, r, 0.96, 0.24); ctx.fill(); outline(ctx, 1.2)
		ctx.fillStyle = look.armour
		ctx.beginPath(); ctx.roundRect(-r * 0.32, -r * 0.4, r * 0.64, r * 0.4, r * 0.08); ctx.fill()
		ctx.fillStyle = "#1c1410"; ctx.fillRect(-r * 0.44, r * 0.06, r * 0.88, r * 0.1)


static func steppeBody(ctx: Ctx, look: Dictionary, r: float, cloth, clothDark, info: Dictionary, tc: Dictionary) -> void:
	var step: float = info.step
	# A quiver slung across the back, fletchings showing over the shoulder.
	if info.soldier:
		ctx.save()
		ctx.translate(r * 0.3, -r * 0.2)
		ctx.rotate(0.45)
		ctx.fillStyle = "#6a4424"
		ctx.beginPath(); ctx.roundRect(-r * 0.1, -r * 0.62, r * 0.2, r * 0.8, r * 0.06); ctx.fill(); outline(ctx, 0.9)
		ctx.fillStyle = look.accent; ctx.fillRect(-r * 0.1, -r * 0.3, r * 0.2, r * 0.05)
		for pair in [[-0.05, "#f0e8d8"], [0.03, "#c8403a"], [0.09, "#f0e8d8"]]:
			var dx: float = pair[0]
			ctx.fillStyle = pair[1]
			ctx.beginPath(); ctx.moveTo(r * dx, -r * 0.62); ctx.lineTo(r * (dx - 0.04), -r * 0.82); ctx.lineTo(r * (dx + 0.04), -r * 0.82); ctx.closePath(); ctx.fill()
		ctx.restore()
	# Tall riding boots, toes turned up.
	for side in [1, -1]:
		var sw: float = step * side
		var x: float = side * r * 0.2 + sw * r * 0.3
		var lift := maxf(0, sw) * r * 0.12
		limb(ctx, side * r * 0.2, r * 0.5, x, r * 0.78 - lift, r * 0.22, "#4a3220", "#2a1c10")
		ctx.fillStyle = "#3a2618"
		ctx.beginPath(); ctx.moveTo(x - r * 0.12, r * 0.8 - lift); ctx.quadraticCurveTo(x + r * 0.1, r * 0.86 - lift, x + r * 0.2, r * 0.72 - lift); ctx.lineTo(x + r * 0.08, r * 0.76 - lift); ctx.closePath(); ctx.fill()
	# The deel: a long coat, flared from the belt to the knee.
	var col = tc.main if info.soldier else cloth
	var colD = tc.dark if info.soldier else clothDark
	ctx.fillStyle = grad(ctx, 0, -r * 0.55, 0, r * 0.65, Palette.shade(col, 0.12), Palette.shade(colD, -0.1))
	ctx.beginPath()
	ctx.moveTo(-r * 0.46, -r * 0.5)
	ctx.quadraticCurveTo(0, -r * 0.64, r * 0.46, -r * 0.5)
	ctx.lineTo(r * 0.44, r * 0.14)
	ctx.lineTo(r * 0.64 + step * r * 0.05, r * 0.62)
	ctx.quadraticCurveTo(0, r * 0.72, -r * 0.64 + step * r * 0.05, r * 0.62)
	ctx.lineTo(-r * 0.44, r * 0.14)
	ctx.closePath(); ctx.fill(); outline(ctx, 1.3)
	# Its lapel crossing to the right, trimmed.
	ctx.strokeStyle = look.accent; ctx.lineWidth = 1.5
	ctx.beginPath(); ctx.moveTo(-r * 0.14, -r * 0.54); ctx.quadraticCurveTo(r * 0.3, -r * 0.3, r * 0.34, r * 0.1); ctx.stroke()
	ctx.beginPath(); ctx.moveTo(-r * 0.6, r * 0.6); ctx.quadraticCurveTo(0, r * 0.7, r * 0.6, r * 0.6); ctx.stroke()
	if info.soldier and info.heavy:
		# Leather lamellar over the chest.
		ctx.fillStyle = grad(ctx, 0, -r * 0.45, 0, r * 0.1, Palette.shade(look.armour, 0.12), Palette.shade(look.armour, -0.2))
		ctx.beginPath(); ctx.roundRect(-r * 0.38, -r * 0.46, r * 0.76, r * 0.56, r * 0.1); ctx.fill(); outline(ctx, 1)
		ctx.fillStyle = "rgba(0,0,0,0.2)"
		var y := -0.4
		while y < 0.06:
			var x := -0.33
			while x < 0.34:
				ctx.fillRect(r * x, r * y, r * 0.08, r * 0.07)
				x += 0.11
			y += 0.1
	# Wide sash belt with its tails.
	ctx.fillStyle = "#e0922a"
	ctx.fillRect(-r * 0.46, r * 0.08, r * 0.92, r * 0.12)
	ctx.beginPath(); ctx.moveTo(r * 0.3, r * 0.18); ctx.lineTo(r * 0.44, r * 0.46); ctx.lineTo(r * 0.34, r * 0.46); ctx.closePath(); ctx.fill()
	# Fur collar.
	ctx.fillStyle = "#6a4a30"
	ctx.beginPath(); ctx.ellipse(0, -r * 0.52, r * 0.3, r * 0.1, 0, 0, PI * 2); ctx.fill()


static func ascendBody(ctx: Ctx, look: Dictionary, r: float, info: Dictionary, tc: Dictionary) -> void:
	var step: float = info.step
	# Power pack across the shoulder blades, cells lit.
	ctx.fillStyle = grad(ctx, 0, -r * 0.7, 0, -r * 0.05, "#5a6272", "#2e3440")
	ctx.beginPath(); ctx.roundRect(-r * 0.4, -r * 0.7, r * 0.8, r * 0.62, r * 0.14); ctx.fill(); outline(ctx, 1)
	glow(ctx, look.accent, 5, func():
		ctx.fillStyle = look.accent
		for x in [-0.2, 0.2]:
			ctx.beginPath(); ctx.roundRect(r * (x - 0.08), -r * 0.66, r * 0.16, r * 0.22, r * 0.05); ctx.fill()
	)
	# Armoured legs, joints lit.
	for side in [1, -1]:
		var sw: float = step * side
		var hipX: float = side * r * 0.2
		var footX: float = hipX + sw * r * 0.4
		var lift := maxf(0, sw) * r * 0.16
		limb(ctx, hipX, r * 0.3, footX, r * 0.8 - lift, r * 0.2, "#f4f6f8", "#a8b0bc")
		var kx: float = hipX + (footX - hipX) * 0.5
		var ky: float = r * 0.3 + (r * 0.8 - lift - r * 0.3) * 0.5
		glow(ctx, look.accent, 4, func():
			ctx.fillStyle = look.accent; ctx.beginPath(); ctx.arc(kx, ky, r * 0.05, 0, PI * 2); ctx.fill()
		)
		ctx.fillStyle = "#3a4250"
		ctx.beginPath(); ctx.ellipse(footX + r * 0.04, r * 0.8 - lift, r * 0.14, r * 0.08, 0, 0, PI * 2); ctx.fill()
	# A slim white hardsuit.
	ctx.fillStyle = grad(ctx, -r * 0.3, -r * 0.55, r * 0.3, r * 0.4, "#ffffff", "#aab2be" if info.soldier else "#c8ccd4")
	torsoPath(ctx, r, 0.88, 0.36); ctx.fill(); outline(ctx, 1.2)
	ctx.strokeStyle = "rgba(60,70,90,0.3)"; ctx.lineWidth = 0.9
	for y in [0, 0.12, 0.24]:
		ctx.beginPath(); ctx.moveTo(-r * 0.3, r * y); ctx.lineTo(r * 0.3, r * y); ctx.stroke()
	# Team stripe and a lit core.
	ctx.fillStyle = tc.main
	ctx.fillRect(-r * 0.06, -r * 0.5, r * 0.12, r * 0.84)
	if info.soldier:
		glow(ctx, look.accent, 6, func():
			ctx.fillStyle = "#e8fbff"; ctx.beginPath(); ctx.arc(0, -r * 0.24, r * 0.09, 0, PI * 2); ctx.fill()
		)
		for sx in [-1, 1]:
			ctx.fillStyle = grad(ctx, sx * r * 0.44, -r * 0.5, sx * r * 0.44, -r * 0.2, "#ffffff", "#b8c0cc")
			ctx.beginPath(); ctx.ellipse(sx * r * 0.44, -r * 0.36, r * (0.22 if info.heavy else 0.16), r * 0.14, sx * 0.3, 0, PI * 2); ctx.fill(); outline(ctx, 0.9)
	ctx.fillStyle = "#3a4250"; ctx.fillRect(-r * 0.4, r * 0.16, r * 0.8, r * 0.07)


# ================================================================== faces ==

## Beards, masks and hats that go under or over the helmet. Drawn before it.
static func factionFace(ctx: Ctx, look: Dictionary, cx: float, cy: float, hr: float, info: Dictionary) -> void:
	if look.helm == "nasal":
		# Every Jarl has a beard; its colour varies man to man.
		var beard: String = ["#c8903a", "#a0582a", "#e0c070", "#6a4a2a"][int(info.seed) % 4]
		ctx.fillStyle = beard
		ctx.beginPath()
		ctx.moveTo(cx - hr * 0.85, cy - hr * 0.05)
		ctx.quadraticCurveTo(cx - hr * 0.95, cy + hr * 1.2, cx, cy + hr * 1.5)
		ctx.quadraticCurveTo(cx + hr * 0.95, cy + hr * 1.2, cx + hr * 0.85, cy - hr * 0.05)
		ctx.quadraticCurveTo(cx, cy + hr * 0.5, cx - hr * 0.85, cy - hr * 0.05)
		ctx.fill()
		ctx.strokeStyle = Palette.shade(beard, -0.3); ctx.lineWidth = 0.8
		ctx.beginPath(); ctx.moveTo(cx - hr * 0.3, cy + hr * 0.9); ctx.lineTo(cx - hr * 0.1, cy + hr * 1.3); ctx.moveTo(cx + hr * 0.3, cy + hr * 0.9); ctx.lineTo(cx + hr * 0.1, cy + hr * 1.3); ctx.stroke()
	elif look.helm == "kabuto" and info.soldier and info.heavy:
		# Mempo: a lacquered half-mask with a moustache.
		ctx.fillStyle = "#8a2a24"
		ctx.beginPath(); ctx.ellipse(cx, cy + hr * 0.35, hr * 0.78, hr * 0.55, 0, 0, PI); ctx.fill(); outline(ctx, 0.8)
		ctx.strokeStyle = "#f4ecd8"; ctx.lineWidth = 0.9
		ctx.beginPath(); ctx.moveTo(cx - hr * 0.4, cy + hr * 0.45); ctx.quadraticCurveTo(cx, cy + hr * 0.3, cx + hr * 0.4, cy + hr * 0.45); ctx.stroke()
	elif look.helm == "galea" and info.soldier:
		# Stubble and a strong jaw under the cheek guards: nothing, the helm does it.
		pass


# ================================================================ weapons ==

## A hand weapon along (dx, dy): a gladius, a bearded axe, a katana, a sabre,
## or an energy blade. Returns false for the Kingdom's straight sword.
static func factionBlade(ctx: Ctx, look: Dictionary, r: float, dx: float, dy: float, len: float, len0: float) -> bool:
	var x0 := dx * r * len0
	var y0 := dy * r * len0
	var x1 := dx * r * len
	var y1 := dy * r * len
	var px := -dy
	var py := dx
	ctx.lineCap = "round"
	match look.helm:
		"galea":  # gladius: short, broad, leaf-tipped, a bronze pommel
			var l := len0 + (len - len0) * 0.72
			var bx := dx * r * l
			var by := dy * r * l
			ctx.fillStyle = "#c89a52"
			ctx.beginPath(); ctx.arc(x0 - dx * r * 0.18, y0 - dy * r * 0.18, r * 0.09, 0, PI * 2); ctx.fill()
			ctx.strokeStyle = "#c89a52"; ctx.lineWidth = r * 0.12
			ctx.beginPath(); ctx.moveTo(x0 - px * r * 0.16, y0 - py * r * 0.16); ctx.lineTo(x0 + px * r * 0.16, y0 + py * r * 0.16); ctx.stroke()
			ctx.fillStyle = grad(ctx, x0, y0, bx, by, "#f2f4f8", "#9aa2ae")
			ctx.beginPath()
			ctx.moveTo(x0 - px * r * 0.1, y0 - py * r * 0.1)
			ctx.quadraticCurveTo(bx * 0.7 + x0 * 0.3 - px * r * 0.14, by * 0.7 + y0 * 0.3 - py * r * 0.14, bx, by)
			ctx.quadraticCurveTo(bx * 0.7 + x0 * 0.3 + px * r * 0.14, by * 0.7 + y0 * 0.3 + py * r * 0.14, x0 + px * r * 0.1, y0 + py * r * 0.1)
			ctx.closePath(); ctx.fill(); outline(ctx, 0.8)
			return true
		"nasal":  # bearded axe: a long haft, the head hooked below the edge
			ctx.strokeStyle = grad(ctx, x0, y0, x1, y1, "#b08a5a", "#6a4a2a"); ctx.lineWidth = r * 0.11
			ctx.beginPath(); ctx.moveTo(-dx * r * 0.2, -dy * r * 0.2); ctx.lineTo(x1, y1); ctx.stroke()
			var hx := x1 - dx * r * 0.12
			var hy := y1 - dy * r * 0.12
			ctx.fillStyle = grad(ctx, hx, hy, hx + px * r * 0.5, hy + py * r * 0.5, "#e8ecf0", "#7a828e")
			ctx.beginPath()
			ctx.moveTo(hx + dx * r * 0.14, hy + dy * r * 0.14)
			ctx.lineTo(hx + px * r * 0.2 + dx * r * 0.2, hy + py * r * 0.2 + dy * r * 0.2)
			ctx.quadraticCurveTo(hx + px * r * 0.62, hy + py * r * 0.62, hx + px * r * 0.3 - dx * r * 0.42, hy + py * r * 0.3 - dy * r * 0.42)
			ctx.lineTo(hx + px * r * 0.1 - dx * r * 0.12, hy + py * r * 0.1 - dy * r * 0.12)
			ctx.lineTo(hx - dx * r * 0.14, hy - dy * r * 0.14)
			ctx.closePath(); ctx.fill(); outline(ctx, 0.9)
			return true
		"kabuto", "steppe":  # katana / sabre: curved, a round or brass guard
			var bend := 0.24 if look.helm == "steppe" else 0.13
			var mx := (x0 + x1) / 2 - dy * r * bend * len * 0.5
			var my := (y0 + y1) / 2 + dx * r * bend * len * 0.5
			ctx.strokeStyle = "#1c1410" if look.helm == "kabuto" else "#5a3a1c"; ctx.lineWidth = r * 0.12
			ctx.beginPath(); ctx.moveTo(-dx * r * 0.18, -dy * r * 0.18); ctx.lineTo(x0, y0); ctx.stroke()
			ctx.fillStyle = "#c8a040" if look.helm == "kabuto" else "#d8b050"
			ctx.beginPath(); ctx.ellipse(x0, y0, r * 0.1, r * 0.1, 0, 0, PI * 2); ctx.fill()
			ctx.strokeStyle = "#dfe6ee"; ctx.lineWidth = r * 0.09
			ctx.beginPath(); ctx.moveTo(x0, y0); ctx.quadraticCurveTo(mx, my, x1, y1); ctx.stroke()
			ctx.strokeStyle = "rgba(255,255,255,0.9)"; ctx.lineWidth = 0.9
			ctx.beginPath(); ctx.moveTo(x0 + dx * r * 0.2, y0 + dy * r * 0.2); ctx.quadraticCurveTo(mx, my, x1, y1); ctx.stroke()
			return true
		"visor":  # an energy blade: a hard white core in a cyan glow
			ctx.strokeStyle = "#2e3440"; ctx.lineWidth = r * 0.14
			ctx.beginPath(); ctx.moveTo(-dx * r * 0.15, -dy * r * 0.15); ctx.lineTo(x0, y0); ctx.stroke()
			glow(ctx, look.accent, 8, func():
				ctx.strokeStyle = Palette.withAlpha(look.accent, 0.8); ctx.lineWidth = r * 0.16
				ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke()
			)
			ctx.strokeStyle = "#f4feff"; ctx.lineWidth = r * 0.06
			ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke()
			return true
		_:
			return false


## A spear's head at (tx, ty) pointing along (fx, fy). False for the Kingdom's.
static func factionSpearHead(ctx: Ctx, look: Dictionary, tx: float, ty: float, fx: float, fy: float, r: float) -> bool:
	var px := -fy
	var py := fx
	match look.helm:
		"galea":  # pilum: a long thin iron shank and a small pyramid point
			ctx.strokeStyle = "#5a5e66"; ctx.lineWidth = r * 0.06
			ctx.beginPath(); ctx.moveTo(tx - fx * r * 0.2, ty - fy * r * 0.2); ctx.lineTo(tx + fx * r * 0.6, ty + fy * r * 0.6); ctx.stroke()
			ctx.fillStyle = "#c8ccd4"
			ctx.beginPath(); ctx.moveTo(tx + fx * r * 0.82, ty + fy * r * 0.82); ctx.lineTo(tx + fx * r * 0.58 + px * r * 0.08, ty + fy * r * 0.58 + py * r * 0.08); ctx.lineTo(tx + fx * r * 0.58 - px * r * 0.08, ty + fy * r * 0.58 - py * r * 0.08); ctx.closePath(); ctx.fill()
			return true
		"nasal":  # a broad winged spear, lugs below the blade
			ctx.fillStyle = "#c8ccd4"
			ctx.beginPath()
			ctx.moveTo(tx + fx * r * 0.6, ty + fy * r * 0.6)
			ctx.quadraticCurveTo(tx + fx * r * 0.2 + px * r * 0.26, ty + fy * r * 0.2 + py * r * 0.26, tx, ty)
			ctx.quadraticCurveTo(tx + fx * r * 0.2 - px * r * 0.26, ty + fy * r * 0.2 - py * r * 0.26, tx + fx * r * 0.6, ty + fy * r * 0.6)
			ctx.fill(); outline(ctx, 0.7)
			ctx.strokeStyle = "#8a929e"; ctx.lineWidth = r * 0.07
			ctx.beginPath(); ctx.moveTo(tx - px * r * 0.16 - fx * r * 0.06, ty - py * r * 0.16 - fy * r * 0.06); ctx.lineTo(tx + px * r * 0.16 - fx * r * 0.06, ty + py * r * 0.16 - fy * r * 0.06); ctx.stroke()
			return true
		"kabuto":  # yari: a long, narrow, straight double edge
			ctx.fillStyle = "#e8eef4"
			ctx.beginPath()
			ctx.moveTo(tx + fx * r * 0.75, ty + fy * r * 0.75)
			ctx.lineTo(tx + px * r * 0.07, ty + py * r * 0.07)
			ctx.lineTo(tx - px * r * 0.07, ty - py * r * 0.07)
			ctx.closePath(); ctx.fill(); outline(ctx, 0.6)
			ctx.fillStyle = "#1c1410"
			ctx.fillRect(tx - fx * r * 0.12 - r * 0.06, ty - fy * r * 0.12 - r * 0.06, r * 0.12, r * 0.12)
			return true
		"steppe":  # a lance with a horsehair tassel below the head
			ctx.fillStyle = "#c8403a"
			for i in 5:
				var a := (i - 2) * 0.35
				ctx.beginPath()
				ctx.ellipse(tx - fx * r * 0.18 + px * sin(a) * r * 0.14, ty - fy * r * 0.18 + py * sin(a) * r * 0.14, r * 0.07, r * 0.16, atan2(fy, fx) + PI / 2 + a, 0, PI * 2)
				ctx.fill()
			ctx.fillStyle = "#dfe4ea"
			ctx.beginPath(); ctx.moveTo(tx + fx * r * 0.5, ty + fy * r * 0.5); ctx.lineTo(tx + px * r * 0.1, ty + py * r * 0.1); ctx.lineTo(tx - px * r * 0.1, ty - py * r * 0.1); ctx.closePath(); ctx.fill()
			return true
		"visor":  # an energy prong: two tines with light between them
			ctx.strokeStyle = "#c8d0dc"; ctx.lineWidth = r * 0.06
			ctx.beginPath()
			ctx.moveTo(tx + px * r * 0.12, ty + py * r * 0.12); ctx.lineTo(tx + fx * r * 0.5 + px * r * 0.12, ty + fy * r * 0.5 + py * r * 0.12)
			ctx.moveTo(tx - px * r * 0.12, ty - py * r * 0.12); ctx.lineTo(tx + fx * r * 0.5 - px * r * 0.12, ty + fy * r * 0.5 - py * r * 0.12)
			ctx.stroke()
			glow(ctx, look.accent, 6, func():
				ctx.strokeStyle = look.accent; ctx.lineWidth = r * 0.07
				ctx.beginPath(); ctx.moveTo(tx, ty); ctx.lineTo(tx + fx * r * 0.62, ty + fy * r * 0.62); ctx.stroke()
			)
			return true
		_:
			return false


## A bow, or for the Ascendancy a rifle, held forward along (fx, fy), drawn
## back by `draw` (0..1). `big` for longbows. False for the Kingdom's.
static func factionBow(ctx: Ctx, look: Dictionary, r: float, fx: float, fy: float, facing: float, draw: float, big: bool) -> bool:
	var px := -fy
	var py := fx
	ctx.lineCap = "round"
	if look.helm == "visor":
		# A pulse carbine: a stock, a long barrel with a lit coil, a flash on the shot.
		var len := r * (2.1 if big else 1.7)
		var sx := -fy * r * 0.2
		var sy := fx * r * 0.2
		ctx.strokeStyle = "#3a4250"; ctx.lineWidth = r * 0.2
		ctx.beginPath(); ctx.moveTo(sx - fx * r * 0.3, sy - fy * r * 0.3); ctx.lineTo(sx + fx * len, sy + fy * len); ctx.stroke()
		ctx.strokeStyle = "#dfe4ea"; ctx.lineWidth = r * 0.09
		ctx.beginPath(); ctx.moveTo(sx, sy); ctx.lineTo(sx + fx * len * 0.66, sy + fy * len * 0.66); ctx.stroke()
		glow(ctx, look.accent, 5, func():
			ctx.strokeStyle = look.accent; ctx.lineWidth = r * 0.06
			ctx.beginPath(); ctx.moveTo(sx + fx * len * 0.35, sy + fy * len * 0.35); ctx.lineTo(sx + fx * len, sy + fy * len); ctx.stroke()
			if draw > 0.6:
				ctx.fillStyle = Palette.withAlpha("#dffcff", draw)
				ctx.beginPath(); ctx.arc(sx + fx * (len + 3), sy + fy * (len + 3), r * 0.3 * draw, 0, PI * 2); ctx.fill()
		)
		return true
	if look.helm == "kingdom":
		return false
	var reach := 0.85
	var spread := 1.25
	var bowCol := "#5a3a1c"
	var ox := 0.7
	var grip := 0.0
	if look.helm == "kabuto":  # yumi: tall, gripped low
		reach = 1.25; spread = 1.2; bowCol = "#2a2018"; ox = 0.55; grip = 0.25
	elif look.helm == "nasal":  # a long self bow
		reach = 1.15 if big else 1.0; spread = 1.1; bowCol = "#7a5430"
	elif look.helm == "galea":
		reach = 0.8; spread = 1.3; bowCol = "#6a4424"
	elif look.helm == "steppe":  # short recurve
		reach = 0.72; spread = 1.35; bowCol = "#4a2c14"
	var cx := fx * r * ox - px * r * grip
	var cy := fy * r * ox - py * r * grip
	ctx.strokeStyle = bowCol; ctx.lineWidth = 1.6 if look.helm == "kabuto" else 2.0
	ctx.beginPath(); ctx.arc(cx, cy, r * reach, facing - spread, facing + spread); ctx.stroke()
	var ax := cx + cos(facing - spread) * r * reach
	var ay := cy + sin(facing - spread) * r * reach
	var bx := cx + cos(facing + spread) * r * reach
	var by := cy + sin(facing + spread) * r * reach
	if look.helm == "steppe" or look.helm == "galea":
		# Recurve: the tips flick forward.
		ctx.beginPath()
		ctx.moveTo(ax, ay); ctx.lineTo(ax + fx * r * 0.18, ay + fy * r * 0.18)
		ctx.moveTo(bx, by); ctx.lineTo(bx + fx * r * 0.18, by + fy * r * 0.18)
		ctx.stroke()
	ctx.strokeStyle = "#e8e0cc"; ctx.lineWidth = 0.8
	ctx.beginPath()
	ctx.moveTo(ax, ay)
	ctx.lineTo(fx * r * (ox - draw * 0.8), fy * r * (ox - draw * 0.8))
	ctx.lineTo(bx, by)
	ctx.stroke()
	return true


# ================================================================= mounts ==

## The Ascendancy's cavalry ride hover-sleds. Drawn in the rotated frame the
## horse would have been (x along facing). True when it replaced the horse.
static func factionMount(ctx: Ctx, look: Dictionary, r: float, sx: float, time: float, tc: Dictionary, moving: bool) -> bool:
	if look.helm != "visor":
		return false
	# Underglow first, on the ground.
	ctx.fillStyle = Palette.withAlpha(look.accent, 0.3 if moving else 0.18)
	ctx.beginPath(); ctx.ellipse(0, r * 0.5, r * sx * 1.05, r * 0.4, 0, 0, PI * 2); ctx.fill()
	var bob := sin(time * 4) * r * 0.06
	ctx.save()
	ctx.translate(0, bob)
	ctx.fillStyle = grad(ctx, 0, -r * 0.5, 0, r * 0.5, "#ffffff", "#a4acb8")
	ctx.beginPath()
	ctx.moveTo(r * sx * 1.15, 0)
	ctx.quadraticCurveTo(r * sx * 0.5, -r * 0.55, -r * sx * 0.9, -r * 0.42)
	ctx.lineTo(-r * sx, 0)
	ctx.lineTo(-r * sx * 0.9, r * 0.42)
	ctx.quadraticCurveTo(r * sx * 0.5, r * 0.55, r * sx * 1.15, 0)
	ctx.closePath(); ctx.fill(); outline(ctx, 1.2)
	ctx.fillStyle = tc.main
	ctx.beginPath(); ctx.moveTo(r * sx * 0.95, 0); ctx.lineTo(-r * sx * 0.8, -r * 0.12); ctx.lineTo(-r * sx * 0.8, r * 0.12); ctx.closePath(); ctx.fill()
	glow(ctx, look.accent, 6, func():
		ctx.fillStyle = look.accent
		for y in [-0.26, 0.26]:
			ctx.beginPath(); ctx.arc(-r * sx * 0.92, r * y, r * 0.1, 0, PI * 2); ctx.fill()
	)
	ctx.restore()
	return true


## Horse furniture over the barrel, in the rotated horse frame.
static func factionTack(ctx: Ctx, look: Dictionary, r: float, sx: float, sy: float, tc: Dictionary) -> void:
	match look.helm:
		"galea":  # a team saddle cloth with a gold fringe, bronze discs on the breast
			ctx.fillStyle = tc.main
			ctx.beginPath(); ctx.roundRect(-r * sx * 0.45, -r * sy * 0.9, r * sx * 0.8, r * sy * 1.8, r * 0.1); ctx.fill()
			ctx.fillStyle = look.accent
			ctx.fillRect(-r * sx * 0.45, r * sy * 0.8, r * sx * 0.8, r * 0.06)
			ctx.fillRect(-r * sx * 0.45, -r * sy * 0.86, r * sx * 0.8, r * 0.06)
			for y in [-0.3, 0, 0.3]:
				ctx.beginPath(); ctx.arc(r * sx * 0.62, r * y, r * 0.07, 0, PI * 2); ctx.fill()
			return
		"nasal":  # a sheepskin saddle
			ctx.fillStyle = "#d8ccb0"
			ctx.beginPath(); ctx.ellipse(-r * 0.1, 0, r * sx * 0.38, r * sy * 0.8, 0, 0, PI * 2); ctx.fill()
			ctx.fillStyle = "rgba(0,0,0,0.12)"
			for i in 6:
				ctx.beginPath(); ctx.arc(-r * 0.3 + i * r * 0.1, (1 if i % 2 else -1) * r * 0.1, r * 0.05, 0, PI * 2); ctx.fill()
			return
		"kabuto":  # lacquered barding laced in the team colour, and tall tassels
			ctx.fillStyle = look.armour
			ctx.beginPath(); ctx.roundRect(-r * sx * 0.6, -r * sy * 0.95, r * sx * 1.1, r * sy * 1.9, r * 0.12); ctx.fill()
			ctx.strokeStyle = tc.main; ctx.lineWidth = 1
			var x := -0.5
			while x < 0.5:
				ctx.beginPath(); ctx.moveTo(r * sx * x, -r * sy * 0.9); ctx.lineTo(r * sx * x, r * sy * 0.9); ctx.stroke()
				x += 0.2
			ctx.fillStyle = "#c8403a"
			for y in [-1, 1]:
				ctx.beginPath(); ctx.ellipse(r * sx * 0.2, y * r * sy * 0.95, r * 0.08, r * 0.14, 0, 0, PI * 2); ctx.fill()
			return
		"steppe":  # a felt blanket in a bold border pattern
			ctx.fillStyle = tc.main
			ctx.beginPath(); ctx.roundRect(-r * sx * 0.5, -r * sy * 0.85, r * sx * 0.85, r * sy * 1.7, r * 0.14); ctx.fill()
			ctx.strokeStyle = "#f0e6d0"; ctx.lineWidth = 1.2
			ctx.beginPath(); ctx.roundRect(-r * sx * 0.44, -r * sy * 0.75, r * sx * 0.73, r * sy * 1.5, r * 0.1); ctx.stroke()
			ctx.fillStyle = "#e0922a"
			ctx.beginPath(); ctx.arc(-r * 0.08, 0, r * 0.1, 0, PI * 2); ctx.fill()
			return


## Coat and build of a faction's horses.
static func factionHorse(look: Dictionary, coat, sx: float) -> Dictionary:
	match look.helm:
		"steppe":
			return {"coat": "#6a4a30", "sx": sx * 0.92}  # shaggy, short-coupled ponies
		"nasal":
			return {"coat": "#a0804a", "sx": sx * 0.98}  # stocky duns
		"kabuto":
			return {"coat": "#3a2a22", "sx": sx}
		"galea":
			return {"coat": "#e8e0d0", "sx": sx}  # greys
		_:
			return {"coat": coat, "sx": sx}


## What a rider wears on his back and over his torso.
static func factionRider(ctx: Ctx, look: Dictionary, r: float, tc: Dictionary, seed: int, back: bool) -> void:
	var bx := -r * 0.08
	var by := -r * 0.4
	match look.helm:
		"galea":
			if back:
				ctx.fillStyle = Palette.shade(tc.main, -0.25)
				ctx.beginPath(); ctx.moveTo(bx - r * 0.3, by - r * 0.3); ctx.quadraticCurveTo(bx - r * 0.7, by + r * 0.5, bx - r * 0.2, by + r * 0.62); ctx.lineTo(bx + r * 0.3, by - r * 0.3); ctx.closePath(); ctx.fill()
			else:
				ctx.fillStyle = Palette.shade(look.armour, 0.05)
				ctx.beginPath(); ctx.ellipse(bx, by, r * 0.28, r * 0.34, 0, 0, PI * 2); ctx.fill()
				ctx.strokeStyle = Palette.shade(look.armour, -0.4); ctx.lineWidth = 0.9
				for y in [-0.12, 0, 0.12]:
					ctx.beginPath(); ctx.moveTo(bx - r * 0.26, by + r * y); ctx.lineTo(bx + r * 0.26, by + r * y); ctx.stroke()
			return
		"nasal":
			if back:
				ctx.fillStyle = "#6a5a48" if seed % 2 else "#5a4a3a"
				ctx.beginPath(); ctx.ellipse(bx - r * 0.1, by + r * 0.1, r * 0.42, r * 0.5, 0, 0, PI * 2); ctx.fill()
			else:
				ctx.fillStyle = "rgba(40,44,50,0.35)"
				var y := -0.2
				while y < 0.22:
					var x := -0.2
					while x < 0.22:
						ctx.beginPath(); ctx.arc(bx + r * x, by + r * y, r * 0.025, 0, PI * 2); ctx.fill()
						x += 0.1
					y += 0.1
			return
		"kabuto":
			if back:
				ctx.strokeStyle = "#2a2018"; ctx.lineWidth = r * 0.06
				ctx.beginPath(); ctx.moveTo(bx + r * 0.1, by); ctx.lineTo(bx + r * 0.14, by - r * 1.7); ctx.stroke()
				ctx.fillStyle = tc.main
				ctx.fillRect(bx + r * 0.15, by - r * 1.68, r * 0.34, r * 0.66)
				ctx.fillStyle = "#f4ecd8"
				ctx.beginPath(); ctx.arc(bx + r * 0.32, by - r * 1.35, r * 0.08, 0, PI * 2); ctx.fill()
			else:
				ctx.fillStyle = look.armour
				ctx.beginPath(); ctx.ellipse(bx, by, r * 0.3, r * 0.36, 0, 0, PI * 2); ctx.fill()
				ctx.strokeStyle = tc.main; ctx.lineWidth = 0.9
				for y in [-0.12, 0, 0.12]:
					ctx.beginPath(); ctx.moveTo(bx - r * 0.28, by + r * y); ctx.lineTo(bx + r * 0.28, by + r * y); ctx.stroke()
			return
		"steppe":
			if back:
				ctx.save(); ctx.translate(bx + r * 0.2, by); ctx.rotate(0.5)
				ctx.fillStyle = "#6a4424"; ctx.fillRect(-r * 0.08, -r * 0.5, r * 0.16, r * 0.62)
				ctx.fillStyle = "#f0e8d8"; ctx.fillRect(-r * 0.08, -r * 0.62, r * 0.16, r * 0.12)
				ctx.restore()
			else:
				ctx.strokeStyle = look.accent; ctx.lineWidth = 1.2
				ctx.beginPath(); ctx.moveTo(bx - r * 0.1, by - r * 0.3); ctx.quadraticCurveTo(bx + r * 0.2, by - r * 0.1, bx + r * 0.22, by + r * 0.25); ctx.stroke()
				ctx.fillStyle = "#e0922a"; ctx.fillRect(bx - r * 0.3, by + r * 0.14, r * 0.6, r * 0.08)
			return
		"visor":
			if back:
				ctx.fillStyle = "#3a4250"
				ctx.beginPath(); ctx.roundRect(bx - r * 0.26, by - r * 0.36, r * 0.52, r * 0.42, r * 0.1); ctx.fill()
				glow(ctx, look.accent, 5, func():
					ctx.fillStyle = look.accent; ctx.fillRect(bx - r * 0.16, by - r * 0.32, r * 0.32, r * 0.08)
				)
			else:
				ctx.fillStyle = grad(ctx, bx, by - r * 0.35, bx, by + r * 0.35, "#ffffff", "#aab2be")
				ctx.beginPath(); ctx.ellipse(bx, by, r * 0.3, r * 0.38, 0, 0, PI * 2); ctx.fill()
				ctx.fillStyle = tc.main; ctx.fillRect(bx - r * 0.05, by - r * 0.32, r * 0.1, r * 0.64)
			return
