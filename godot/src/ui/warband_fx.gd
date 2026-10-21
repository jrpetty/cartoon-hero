## ui/warband_fx.ts — Warband Tactics combat FX: hit sparks, floating damage
## numbers, death bursts and screen-shake — driven by the live battle's world
## events, plus throttled synth SFX. All effects live in arena world-space and
## are mapped to the board when drawn, so they track the units exactly.
##
## Cosmetic only, so it keeps the TS's Math.random (randf) and the builtin trig.
class_name WarbandFx
extends RefCounted

## Spark: {x, y, vx, vy, life, max, size, color, grav, glow}
var sparks: Array = []
## DmgText: {x, y, vy, life, max, text, color, size}
var dmg: Array = []
## The streak an Infiltrator leaves behind when it jumps the line:
## {x0, y0, x1, y1, life, max, color}
var trails: Array = []
var shake := 0.0
var lastSound := {}
var clock := 0.0


func sound(name: String, gap: float = 0.06) -> void:
	var last: float = lastSound.get(name, -1.0)
	if clock - last < gap:
		return
	lastSound[name] = clock
	audio.play(name)


## Spawn FX + play SFX for a batch of combat events.
func ingest(events: Array) -> void:
	for ev in events:
		match ev.kind:
			"hit":
				var n := float(str(ev.data)) if ev.data != null else 0.0
				if n > 0:
					dmg.append({"x": ev.x + (randf() * 8 - 4), "y": ev.y - 6, "vy": -34.0, "life": 0.85, "max": 0.85, "text": JS.num(n),
						"color": "#ffe9b0" if ev.team == T.Team.Player else "#ffb4a8", "size": 13 + minf(10, n / 6)})
				burst(ev.x, ev.y, 5, "#ffe6a0", 60, 0.5, 2)
				sound("sword", 0.05)
			"sword":
				burst(ev.x, ev.y, 4, "#fff0c0", 55, 0.45, 2); sound("sword", 0.05)
			"bow":
				sound("bow", 0.06)
			"arrowHit":
				burst(ev.x, ev.y, 4, "#d8e4f0", 70, 0.4, 1.6); sound("arrowHit", 0.05)
			"siege":
				burst(ev.x, ev.y, 14, "#ffb060", 130, 0.7, 3, 60); shake = minf(10, shake + 6); sound("siege", 0.12)
			"death":
				var col := "#7fb0e8" if ev.team == T.Team.Player else "#e88a7f"
				burst(ev.x, ev.y - 6, 16, col, 95, 0.7, 2.6, 40)
				burst(ev.x, ev.y - 6, 8, "#3a2f22", 60, 0.8, 3, 80)
				shake = minf(9, shake + 2.4)
				sound("death", 0.08)
			"leap":
				# An Infiltrator opening the fight behind the enemy: a scatter of
				# dust where it was, a hard violet burst where it lands, and a streak
				# between the two so the eye can follow the jump.
				var parts := str(ev.data if ev.data != null else "0,0").split(",")
				var tx := float(parts[0])
				var ty := float(parts[1]) if parts.size() > 1 else 0.0
				burst(ev.x, ev.y, 10, "#6a5a44", 70, 0.5, 2.4, 60)
				burst(tx, ty, 16, "#cf5fd8", 120, 0.65, 2.8, 20)
				trails.append({"x0": ev.x, "y0": ev.y, "x1": tx, "y1": ty, "life": 0.45, "max": 0.45, "color": "#cf5fd8"})
				shake = minf(8, shake + 2)
				sound("select", 0.05)
			"ability":
				# A bright signature-ability cast: a ring of sparks in the ability colour.
				var col := "#ffe9a8"
				for k in C.ABILITIES:
					if C.ABILITIES[k].id == ev.data:
						col = C.ABILITIES[k].color
						break
				burst(ev.x, ev.y - 8, 18, col, 120, 0.8, 3, 30)
				shake = minf(7, shake + 1.5)
				sound("complete", 0.12)


func burst(x: float, y: float, count: int, color: String, speed: float, life: float, size: float, grav: float = 0.0) -> void:
	for i in count:
		var a := randf() * PI * 2
		var s := speed * (0.35 + randf() * 0.65)
		sparks.append({"x": x, "y": y, "vx": cos(a) * s, "vy": sin(a) * s - 10, "life": life * (0.6 + randf() * 0.5), "max": life,
			"size": size * (0.6 + randf() * 0.7), "color": color, "grav": grav, "glow": true})


func update(dt: float) -> void:
	clock += dt
	for p in sparks:
		p.x += p.vx * dt; p.y += p.vy * dt; p.vy += p.grav * dt; p.vx *= 0.9; p.vy *= 0.9; p.life -= dt
	sparks = sparks.filter(func(p): return p.life > 0)
	for d in dmg:
		d.y += d.vy * dt; d.vy += 26 * dt; d.life -= dt
	dmg = dmg.filter(func(d): return d.life > 0)
	for t in trails:
		t.life -= dt
	trails = trails.filter(func(t): return t.life > 0)
	shake *= maxf(0, 1 - dt * 7)
	if shake < 0.15:
		shake = 0


func clear() -> void:
	sparks.clear(); dmg.clear(); trails.clear(); shake = 0


## Draw sparks + damage numbers, mapping arena world-space to screen.
func draw(ctx: Ctx, mapX: Callable, mapY: Callable, scale: float) -> void:
	ctx.save()
	# Leap streaks sit under the sparks.
	for t in trails:
		var a := maxf(0, minf(1, t.life / t.max))
		ctx.globalAlpha = a * 0.75
		ctx.strokeStyle = t.color; ctx.lineWidth = maxf(1, 4 * a)
		ctx.shadowColor = t.color; ctx.shadowBlur = 10
		ctx.beginPath(); ctx.moveTo(mapX.call(t.x0), mapY.call(t.y0)); ctx.lineTo(mapX.call(t.x1), mapY.call(t.y1)); ctx.stroke()
	ctx.shadowBlur = 0
	for p in sparks:
		var a := maxf(0, minf(1, p.life / p.max))
		var sx: float = mapX.call(p.x)
		var sy: float = mapY.call(p.y)
		var r := maxf(0.6, p.size * scale * (0.4 + a * 0.6))
		ctx.globalAlpha = a
		if p.glow:
			ctx.shadowColor = p.color; ctx.shadowBlur = 8
		ctx.fillStyle = p.color
		ctx.beginPath(); ctx.arc(sx, sy, r, 0, PI * 2); ctx.fill()
	ctx.shadowBlur = 0
	ctx.textAlign = "center"
	for d in dmg:
		var a := maxf(0, minf(1, d.life / d.max))
		ctx.globalAlpha = a
		ctx.font = "bold %dpx Georgia, serif" % JS.round(d.size)
		ctx.fillStyle = "rgba(0,0,0,0.55)"
		ctx.fillText(d.text, mapX.call(d.x) + 1, mapY.call(d.y) + 1)
		ctx.fillStyle = d.color
		ctx.fillText(d.text, mapX.call(d.x), mapY.call(d.y))
	ctx.restore()
