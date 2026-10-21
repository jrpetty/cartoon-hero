extends TestCase
# render/draw.ts, from the TS suite: "Each faction looks like itself"
# (sim/factions.test.ts) and "Building sites" (render/nightlight.test.ts) —
# pixel checks, rasterised on the CPU by tests/soft_canvas.gd so they run
# headless. Plus: every unit and building draws in every faction.

const SoftCanvas := preload("res://tests/soft_canvas.gd")

static var _tc_shots := {}


static func building(type: String, over: Dictionary = {}) -> Entity:
	var e := Entity.new()
	e.kind = T.Kind.Building
	e.type = type
	e.team = T.Team.Player
	e.x = 100
	e.y = 100
	e.radius = 32
	e.hp = 100
	e.maxHp = 100
	e.buildState = T.BuildState.Done
	e.buildProgress = 1
	for k in over:
		e.set(k, over[k])
	return e


## One Town Centre per faction, same team colour, drawn to a small canvas.
static func render_tc(f: String) -> Image:
	if _tc_shots.has(f):
		return _tc_shots[f]
	var keep := ArtCache.enabled
	ArtCache.enabled = false
	var c = SoftCanvas.new(160, 160)
	c.fillStyle = "#6da944"
	c.fillRect(0, 0, 160, 160)
	var e := building("town_center", {"team": 0, "x": 80, "y": 100, "radius": 48, "hp": 1, "maxHp": 1})
	Draw.setFactionResolver(func(_t): return f)
	Draw.drawBuilding(c, e, 0, 0)
	Draw.setFactionResolver(null)
	ArtCache.enabled = keep
	_tc_shots[f] = c.img
	return c.img


func test_draws_six_different_town_centres() -> void:
	var ids: Array = C.FACTION_IDS
	var shots := ids.map(func(f): return render_tc(f))
	for i in shots.size():
		for j in range(i + 1, shots.size()):
			var a: Image = shots[i]
			var b: Image = shots[j]
			var diff := 0
			for y in 160:
				for x in 160:
					var p := a.get_pixel(x, y)
					var q := b.get_pixel(x, y)
					if absi(p.r8 - q.r8) + absi(p.g8 - q.g8) > 40:
						diff += 1
			check(diff > 1500, "%s and %s look the same (%d px differ)" % [ids[i], ids[j], diff])


func test_keeps_the_team_colour_big_enough_to_read() -> void:
	var main = Ctx.parse_color(Palette.teamColor(0).main)
	for f in C.FACTION_IDS:
		var d: Image = render_tc(f)
		var team := 0
		for y in 160:
			for x in 160:
				var p := d.get_pixel(x, y)
				if absi(p.r8 - main.r8) + absi(p.g8 - main.g8) + absi(p.b8 - main.b8) < 70:
					team += 1
		check(team > 250, "%s's Town Centre barely shows whose it is (%d px)" % [f, team])


## How much of the finished building's own art shows at full strength: the
## team-blue roof is the most saturated thing in it.
static func roof(state: int, progress: float) -> int:
	var keep := ArtCache.enabled
	ArtCache.enabled = false
	Draw.setFactionResolver(null)
	var c = SoftCanvas.new(200, 200)
	c.fillStyle = "#6da944"
	c.fillRect(0, 0, 200, 200)
	Draw.drawBuilding(c, building("house", {"buildState": state, "buildProgress": progress}), 0, T.Team.Player)
	ArtCache.enabled = keep
	var n := 0
	for y in 200:
		for x in 200:
			var p: Color = c.img.get_pixel(x, y)
			if p.b8 > p.r8 + 80 and p.b8 > p.g8 + 40:
				n += 1
	return n


func test_building_sites_show_what_they_will_become_and_rise() -> void:
	var planned := roof(T.BuildState.Foundation, 0)
	var half := roof(T.BuildState.UnderConstruction, 0.5)
	var nearly := roof(T.BuildState.UnderConstruction, 0.95)
	var done := roof(T.BuildState.Done, 1)
	check(done > 0, "the finished roof shows")
	check(planned < done * 0.2, "a foundation shows the finished building at full strength (%d vs %d)" % [planned, done])
	check(nearly > half, "the building didn't rise as it was built (%d vs %d)" % [nearly, half])
	check(nearly > done * 0.6, "nearly done shows most of the building (%d vs %d)" % [nearly, done])


func test_every_unit_and_building_draws_in_every_faction() -> void:
	# Through a recording Ctx: each must put something on the canvas.
	var keep := ArtCache.enabled
	ArtCache.enabled = false
	var c := Ctx.new()
	c.measure_only = true
	for f in C.FACTION_IDS:
		Draw.setFactionResolver(func(_t): return f)
		for type in C.UNITS:
			var e := Entity.new()
			e.type = type
			e.radius = float(C.UNITS[type].radius)
			e.facing = 0.7
			e.attackCooldown = 0.9
			c.begin(RID())
			c.bounds_valid = false
			Draw.drawUnit(c, e, 1.0, 0)
			check(c.bounds_valid and c.bounds.size.x > e.radius, "%s %s drew" % [f, type])
		for type in C.BUILDINGS:
			var b := building(type)
			c.begin(RID())
			c.bounds_valid = false
			Draw.drawBuilding(c, b, 1.0, 0)
			check(c.bounds_valid and c.bounds.size.x > 10, "%s %s drew" % [f, type])
	Draw.setFactionResolver(null)
	ArtCache.enabled = keep
