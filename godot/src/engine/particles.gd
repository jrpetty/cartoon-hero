## Pooled particle system for combat FX, dust, smoke and the meta case-opening
## flourish. Particles live in world space and are drawn by the renderer.
##
## Port of game/src/engine/particles.ts. Cosmetic only — never the simulation —
## so it is free to use the unseeded JS.random() (Math.random) and the
## platform's sin/cos.
class_name Particles
extends RefCounted


## One pooled particle (the TS `Particle` interface).
class Particle:
	var active := false
	var x := 0.0
	var y := 0.0
	var vx := 0.0
	var vy := 0.0
	var life := 0.0
	var maxLife := 1.0
	var size := 2.0
	var color := "#fff"
	var drag := 0.92
	var gravity := 0.0
	var fade := true
	var shrink := false
	var glow := false


var pool: Array[Particle] = []
var cursor := 0
## 0..1 spawn fraction — lowered by the "reduce effects" setting.
var density := 1.0


func _init(max: int = 2000) -> void:
	for i in max:
		pool.append(Particle.new())


func acquire() -> Particle:
	# Round-robin over the pool; oldest gets reused if exhausted.
	var n := pool.size()
	for i in n:
		var idx := (cursor + i) % n
		if not pool[idx].active:
			cursor = (idx + 1) % n
			return pool[idx]
	var p := pool[cursor]
	cursor = (cursor + 1) % n
	return p


## `opts` is a Dictionary with x and y and any other Particle fields.
func spawn(opts: Dictionary) -> void:
	# Cosmetic only (never sim), so a probabilistic skip is safe and keeps the
	# thinning uniform across every effect.
	if density < 1.0 and JS.random() > density:
		return
	var p := acquire()
	p.active = true
	p.x = opts.x
	p.y = opts.y
	p.vx = opts.get("vx", 0.0)
	p.vy = opts.get("vy", 0.0)
	p.maxLife = opts.get("maxLife", 0.6)
	p.life = p.maxLife
	p.size = opts.get("size", 3.0)
	p.color = opts.get("color", "#ffffff")
	p.drag = opts.get("drag", 0.9)
	p.gravity = opts.get("gravity", 0.0)
	p.fade = opts.get("fade", true)
	p.shrink = opts.get("shrink", false)
	p.glow = opts.get("glow", false)


func burst(x: float, y: float, count: int, color: String, speed: float, opts: Dictionary = {}) -> void:
	for i in count:
		var a := JS.random() * PI * 2.0
		var s := speed * (0.4 + JS.random() * 0.6)
		var o := {"x": x, "y": y, "vx": cos(a) * s, "vy": sin(a) * s, "color": color}
		o.merge(opts, true) # ...opts overrides, as the spread does
		spawn(o)


func update(dt: float) -> void:
	for p in pool:
		if not p.active:
			continue
		p.life -= dt
		if p.life <= 0.0:
			p.active = false
			continue
		p.vx *= pow(p.drag, dt * 60.0)
		p.vy *= pow(p.drag, dt * 60.0)
		p.vy += p.gravity * dt
		p.x += p.vx * dt
		p.y += p.vy * dt


func clear() -> void:
	for p in pool:
		p.active = false
