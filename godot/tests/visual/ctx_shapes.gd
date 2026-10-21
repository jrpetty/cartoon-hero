## Ctx primitives, call for call the same as game/scripts/godot-visual-ctx.mjs.
extends "res://tests/visual/sheet_base.gd"


func paint(ctx: Ctx) -> void:
	var W := 640
	var H := 480
	ctx.fillStyle = "#6da944"; ctx.fillRect(0, 0, W, H)

	# row 1: fills
	ctx.fillStyle = "#d8403a"; ctx.beginPath(); ctx.arc(40, 40, 24, 0, PI * 2); ctx.fill()
	ctx.fillStyle = "rgba(20,30,200,0.5)"; ctx.beginPath(); ctx.ellipse(110, 40, 30, 14, 0.5, 0, PI * 2); ctx.fill()
	ctx.fillStyle = "#e8b13c"; ctx.beginPath(); ctx.roundRect(150, 16, 60, 48, 10); ctx.fill()
	ctx.fillStyle = "#3a703a"; ctx.beginPath(); ctx.arc(260, 40, 16, 0, PI * 2); ctx.arc(245, 50, 10, 0, PI * 2); ctx.arc(275, 50, 10, 0, PI * 2); ctx.fill()
	ctx.fillStyle = "hsl(200, 60%, 50%)"; ctx.beginPath(); ctx.moveTo(310, 64); ctx.lineTo(330, 14); ctx.lineTo(350, 50); ctx.lineTo(370, 14); ctx.lineTo(390, 64); ctx.closePath(); ctx.fill()
	ctx.fillStyle = "#241c12"; ctx.beginPath(); ctx.arc(440, 60, 30, PI, 0); ctx.fill()
	ctx.strokeStyle = "#8a6238"; ctx.lineWidth = 3; ctx.beginPath(); ctx.arc(440, 60, 32, PI, 0); ctx.stroke()
	ctx.fillStyle = "#c43b4e"; ctx.beginPath()
	for i in 5:
		var a := -PI / 2 + i * 4 * PI / 5
		ctx.lineTo(540 + cos(a) * 30, 42 + sin(a) * 30)
	ctx.closePath(); ctx.fill()

	# row 2: strokes with caps / joins
	var caps := ["butt", "round", "square"]
	for i in 3:
		ctx.lineCap = caps[i]; ctx.lineWidth = 10; ctx.strokeStyle = "#274a90"
		ctx.beginPath(); ctx.moveTo(30 + i * 60, 110); ctx.lineTo(70 + i * 60, 150); ctx.stroke()
	ctx.lineCap = "butt"
	var joins := ["miter", "round", "bevel"]
	for i in 3:
		ctx.lineJoin = joins[i]; ctx.lineWidth = 9; ctx.strokeStyle = "rgba(240,240,240,0.7)"
		ctx.beginPath(); ctx.moveTo(220 + i * 70, 150); ctx.lineTo(245 + i * 70, 105); ctx.lineTo(270 + i * 70, 150); ctx.stroke()
	ctx.lineJoin = "miter"
	ctx.strokeStyle = "rgba(20,16,10,0.42)"; ctx.lineWidth = 1.6
	ctx.beginPath(); ctx.arc(470, 128, 22, 0, PI * 2); ctx.stroke()
	ctx.lineWidth = 0.8; ctx.strokeStyle = "#ffffff"
	ctx.beginPath(); ctx.moveTo(510, 110); ctx.lineTo(610, 150); ctx.stroke()
	ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(510, 120); ctx.lineTo(610, 160); ctx.stroke()
	ctx.setLineDash([6, 4]); ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(510, 100); ctx.lineTo(610, 100); ctx.stroke(); ctx.setLineDash([])

	# row 3: gradients, transforms, quadratic, bezier
	var g := ctx.createLinearGradient(20, 190, 20, 260); g.addColorStop(0, "#b5afa3"); g.addColorStop(1, "#7d776c")
	ctx.fillStyle = g; ctx.beginPath(); ctx.roundRect(20, 190, 80, 70, 3); ctx.fill()
	ctx.strokeStyle = "rgba(20,16,10,0.42)"; ctx.lineWidth = 1.4; ctx.stroke()
	g = ctx.createLinearGradient(120, 0, 200, 0); g.addColorStop(0, "#b5afa3"); g.addColorStop(0.5, "#9b958a"); g.addColorStop(1, "#5d574c")
	ctx.fillStyle = g; ctx.fillRect(110, 190, 100, 70)
	var rg := ctx.createRadialGradient(270, 225, 1, 270, 225, 40); rg.addColorStop(0, "rgba(255,210,122,0.9)"); rg.addColorStop(0.5, "rgba(255,154,60,0.4)"); rg.addColorStop(1, "rgba(255,154,60,0)")
	ctx.fillStyle = rg; ctx.beginPath(); ctx.arc(270, 225, 40, 0, PI * 2); ctx.fill()
	ctx.save(); ctx.translate(360, 225); ctx.rotate(0.6); ctx.scale(1.5, 1.5)
	ctx.fillStyle = "#3a6fd8"; ctx.fillRect(-15, -10, 30, 20)
	ctx.strokeStyle = "#ffffff"; ctx.lineWidth = 2; ctx.strokeRect(-15, -10, 30, 20)
	ctx.restore()
	g = ctx.createLinearGradient(420, 200, 450, 250); g.addColorStop(0, "#e8b88a"); g.addColorStop(1, "#6d4a2f")
	ctx.strokeStyle = g; ctx.lineCap = "round"; ctx.lineWidth = 8; ctx.beginPath(); ctx.moveTo(420, 200); ctx.lineTo(450, 250); ctx.stroke(); ctx.lineCap = "butt"
	ctx.save(); ctx.translate(530, 225); var r := 30.0
	g = ctx.createLinearGradient(0, -r * 0.55, 0, r * 0.45); g.addColorStop(0, "#7da3ec"); g.addColorStop(1, "#274a90")
	ctx.fillStyle = g; ctx.beginPath(); ctx.moveTo(-r * 0.5, r * 0.42); ctx.quadraticCurveTo(-r * 0.6, -r * 0.5, 0, -r * 0.58); ctx.quadraticCurveTo(r * 0.6, -r * 0.5, r * 0.5, r * 0.42); ctx.quadraticCurveTo(0, r * 0.6, -r * 0.5, r * 0.42); ctx.closePath(); ctx.fill()
	ctx.strokeStyle = "rgba(20,16,10,0.42)"; ctx.lineWidth = 1.8; ctx.stroke()
	ctx.restore()
	ctx.fillStyle = "#e0c060"; ctx.beginPath(); ctx.moveTo(590, 260); ctx.bezierCurveTo(590, 190, 640, 190, 640, 260); ctx.closePath(); ctx.fill()

	# row 4: text
	ctx.fillStyle = "#e9dcc0"; ctx.font = "bold 13px 'Trebuchet MS', sans-serif"; ctx.textAlign = "left"; ctx.textBaseline = "middle"
	ctx.fillText("Banner & Blade — bold 13px", 20, 300)
	ctx.font = "italic 16px Georgia, serif"; ctx.fillText("Italic Georgia 16px", 20, 325)
	ctx.font = "12px 'Trebuchet MS', sans-serif"; ctx.textAlign = "center"; ctx.fillText("centered 12px", 320, 300)
	ctx.textAlign = "right"; ctx.textBaseline = "alphabetic"; ctx.font = "bold 30px Georgia, serif"; ctx.fillText("Right 30px", 620, 330)
	ctx.textAlign = "left"
	ctx.fillStyle = "#000"; ctx.font = "12px sans-serif"; var mw := ctx.measureText("measure me").width
	ctx.fillText("measure me = " + JS.to_fixed(mw, 1), 20, 350)
	ctx.strokeStyle = "#000"; ctx.lineWidth = 1; ctx.strokeRect(20, 340, mw, 14)

	# row 5: clip
	ctx.save(); ctx.beginPath(); ctx.rect(20, 380, 120, 60); ctx.clip()
	ctx.fillStyle = "#d8a83a"; ctx.beginPath(); ctx.arc(80, 410, 50, 0, PI * 2); ctx.fill()
	ctx.restore()
	ctx.save(); ctx.beginPath(); ctx.moveTo(180, 440); ctx.lineTo(220, 380); ctx.lineTo(300, 380); ctx.lineTo(340, 440); ctx.closePath()
	ctx.fillStyle = "#3a6fd8"; ctx.fill(); ctx.clip()
	ctx.strokeStyle = "rgba(0,0,0,0.5)"; ctx.lineWidth = 1
	for yy in range(384, 440, 4):
		for xx in range(180, 340, 4):
			ctx.beginPath(); ctx.arc(xx + ((yy / 4) % 2) * 2, yy, 2, 0, PI); ctx.stroke()
	ctx.restore()
	ctx.save(); ctx.shadowColor = "#3ad8e8"; ctx.shadowBlur = 8; ctx.fillStyle = "#3ad8e8"
	ctx.beginPath(); ctx.arc(400, 410, 4, 0, PI * 2); ctx.fill()
	ctx.fillRect(430, 400, 60, 4)
	ctx.restore()
	ctx.save(); ctx.shadowColor = "rgba(0,0,0,0.6)"; ctx.shadowBlur = 14; ctx.shadowOffsetY = 4; ctx.fillStyle = "#e9dcc0"
	ctx.beginPath(); ctx.roundRect(520, 385, 90, 50, 8); ctx.fill(); ctx.restore()
