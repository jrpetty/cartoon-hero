## render/weather.ts — screen-space weather & atmosphere overlay (rain / snow /
## overcast). Purely cosmetic — never touches the sim — so it's safe to drive
## with Math.random and to skip entirely when the setting is off. The *type* is
## chosen from the map seed so it's stable for a match (and identical on both
## clients in a net game).
##
## WeatherType: "clear" | "rain" | "snow" | "overcast".
class_name Weather
extends RefCounted


## A raindrop or snowflake (TS `Drop`).
class Drop:
	var x := 0.0
	var y := 0.0
	var v := 0.0
	var len := 0.0
	var phase := 0.0


static func pickWeather(seed: int, mapName: String) -> String:
	var r := fmod(fmod(float(seed), 1000.0) / 1000.0 + 1.0, 1.0) # 0..1, stable
	var n := mapName.to_lower()
	if n.contains("highland"):
		return "snow" if r < 0.6 else "overcast"
	if n.contains("river") or n.contains("island"):
		return "rain" if r < 0.55 else "clear"
	if n.contains("forest"):
		return "overcast" if r < 0.5 else "rain"
	# General maps: mostly clear, with the occasional weather day.
	if r < 0.55:
		return "clear"
	if r < 0.74:
		return "rain"
	if r < 0.88:
		return "overcast"
	return "snow"


const TINT := {
	"clear": null,
	"rain": "rgba(40, 55, 80, 0.16)",
	"snow": "rgba(200, 215, 235, 0.12)",
	"overcast": "rgba(70, 72, 80, 0.18)",
}

var type := "clear"
var drops: Array = []
var t := 0.0
var w := 0.0
var h := 0.0


## Choose the weather for a match from its map (deterministic & replayable).
func configure(seed: int, mapName: String) -> void:
	type = pickWeather(seed, mapName)
	drops = []
	w = 0
	h = 0


func targetCount(density: float) -> int:
	if type == "rain":
		return int(JS.round(240 * density))
	if type == "snow":
		return int(JS.round(170 * density))
	return 0


func reseed(W: float, H: float, density: float) -> void:
	w = W
	h = H
	var n := targetCount(density)
	drops = []
	for i in n:
		var d := Drop.new()
		d.x = randf() * W
		d.y = randf() * H
		if type == "rain":
			d.v = 900 + randf() * 500
			d.len = 10 + randf() * 10
		else:
			d.v = 45 + randf() * 65
			d.len = 1.4 + randf() * 1.8
			d.phase = randf() * PI * 2
		drops.append(d)


## Update + draw in one pass, in screen space. Call after the world render and
## before the HUD. `density` (0..1) thins particles for the reduce-effects setting.
func render(ctx: Ctx, W: float, H: float, dt: float, density: float = 1.0) -> void:
	if type == "clear":
		return
	t += dt
	var tint = TINT[type]

	ctx.save()
	if tint != null:
		ctx.fillStyle = tint
		ctx.fillRect(0, 0, W, H)

	if type == "overcast":
		ctx.restore()
		return
	if drops.size() != targetCount(density) or w != W or h != H:
		reseed(W, H, density)

	if type == "rain":
		var wind := 140.0
		ctx.strokeStyle = "rgba(190, 210, 235, 0.32)"
		ctx.lineWidth = 1.1
		ctx.beginPath()
		for d: Drop in drops:
			d.y += d.v * dt
			d.x += wind * dt
			if d.y > H:
				d.y = -d.len
				d.x = randf() * W
			elif d.x > W:
				d.x -= W
			var dxl := (wind / d.v) * d.len
			ctx.moveTo(d.x, d.y)
			ctx.lineTo(d.x - dxl, d.y - d.len)
		ctx.stroke()
	else: # snow
		ctx.fillStyle = "rgba(255, 255, 255, 0.85)"
		for d: Drop in drops:
			d.y += d.v * dt
			var sway := sin(t * 1.5 + d.phase) * 14
			if d.y > H:
				d.y = -4
				d.x = randf() * W
			ctx.beginPath()
			ctx.arc(d.x + sway, d.y, d.len, 0, PI * 2)
			ctx.fill()
	ctx.restore()
