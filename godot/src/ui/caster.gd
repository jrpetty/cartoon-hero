## ui/caster.ts — caster mode: everything someone commentating or streaming a
## match needs.
##
## Modelled on the observer tools of the big competitive RTS games:
##   • a player bar across the top — each realm's colour, name, faction, age,
##     population, resources and army value — with tug-of-war bars under it
##     comparing the sides' armies, economies and kills;
##   • a bottom panel with five views: overview, army composition, economy,
##     production (what every building is making, with progress) and tech
##     (ages, Oaths, researched upgrades);
##   • vision switching: the whole map, or exactly what one player can see;
##   • an auto-director camera that finds the biggest fight and cuts to it,
##     tours the bases when it's quiet, and backs off the moment the caster
##     takes the camera;
##   • an event feed (ages, Oaths, battles, fallen Town Centres and castles,
##     eliminations, first blood) — click a line to go there — and fight
##     markers on the minimap;
##   • live graphs of army value and score per player;
##   • a clean feed for streaming: just the game and a slim bar;
##   • for replays, a timeline to scrub and speeds up to 16×; online, a
##     broadcast delay so a caster's stream can't be used to cheat.
##
## Drawing is immediate-mode like the rest of the UI. The App owns the world,
## camera and replay; it hands them in and reads back what the caster asked for.
##
## CasterPanel: "overview" | "army" | "economy" | "production" | "tech".
## CasterRequest (what draw() returns) is a Dictionary with any of: seekTo,
## speed, togglePause, focus {x, y}, exit, download. CasterContext (what
## draw() takes) is a Dictionary: {world, names, history, speed, paused,
## source ("live" | "replay" | "delay"), delaySec, replay {tick, endTick} or
## null, offline}.
class_name Caster
extends RefCounted

const CASTER_PANELS := [
	["overview", "Overview"], ["army", "Army"], ["economy", "Economy"], ["production", "Production"], ["tech", "Tech & Oaths"],
]
const MAJOR_BUILDINGS := {"town_center": true, "castle": true, "wonder": true, "siege_workshop": true}


static func teamCol(t: int) -> String:
	var teams: Array = C.PAL.teams
	return teams[t % teams.size()].main


static func teamLight(t: int) -> Color:
	var teams: Array = C.PAL.teams
	var l = teams[t % teams.size()].light
	return l if l is Color else Ctx.parse_color(l)


static func clock(sec: float) -> String:
	return "%d:%02d" % [floori(sec / 60), floori(fmod(sec, 60))]


static func num(n: float) -> String:
	return JS.to_fixed(n / 1000, 1) + "k" if n >= 10000 else JS.locale_int(JS.round(n))


## performance.now(), replaceable for tests.
static var nowFn := Callable()


static func perfNow() -> float:
	return float(nowFn.call()) if nowFn.is_valid() else Time.get_ticks_usec() / 1000.0


## Per-realm numbers for one frame (TS RealmSnapshot): {team, army, armyValue,
## villagers, idleVillagers, buildings, units (type -> count, insertion order),
## production [{item, label, progress, building}], defeated}.
static func snapshotRealms(world: World) -> Array:
	var out: Array = []
	for t in world.numTeams:
		out.append({
			"team": t, "army": 0, "armyValue": 0.0, "villagers": 0, "idleVillagers": 0, "buildings": 0, "units": {}, "production": [],
			"defeated": world.player(t).defeated,
		})
	for e: Entity in world.entities:
		if not e.alive or e.team < 0 or e.team >= world.numTeams:
			continue
		var r: Dictionary = out[e.team]
		if e.kind == T.Kind.Building:
			r.buildings += 1
			if e.productionQueue.size() > 0:
				var item: String = e.productionQueue[0]
				var parts := item.split(":")
				var k := parts[0]
				var id := parts[1] if parts.size() > 1 else ""
				var total := 1.0
				var label := id
				if k == "u":
					var u = C.UNITS.get(id)
					total = u.buildTime if u != null else 1.0
					label = u.name if u != null else id
				elif k == "t":
					var up = C.UPGRADES.get(id)
					total = up.time if up != null else 1.0
					label = up.name if up != null else id
				elif k == "a":
					var ages: Array = C.AGES
					var ni := world.player(e.team).age + 1
					var nxt = ages[ni] if ni < ages.size() else null
					total = nxt.advanceTime if nxt != null else 1.0
					label = "→ %s" % nxt.short if nxt != null else "Age"
				var bd = C.BUILDINGS.get(e.type)
				r.production.append({"item": item, "label": label, "progress": clampf(1 - e.productionTime / maxf(0.1, total), 0, 1), "building": bd.name if bd != null else e.type})
		elif e.kind == T.Kind.Unit:
			if e.type == "villager":
				r.villagers += 1
				if e.order.kind == T.OrderKind.Idle:
					r.idleVillagers += 1
			elif C.UNITS.has(e.type):
				r.army += 1
				var c: Dictionary = C.UNITS[e.type].cost
				r.armyValue += float(c.get("food", 0)) + float(c.get("wood", 0)) + float(c.get("gold", 0))
			r.units[e.type] = r.units.get(e.type, 0) + 1
	# Ages and research first, then units, longest-running first.
	for r in out:
		JS.sort_by_cmp(r.production, func(a, b):
			var d := (1 if a.item[0] == "u" else 0) - (1 if b.item[0] == "u" else 0)
			return d if d != 0 else b.progress - a.progress)
	return out


## The sides for tug-of-war bars: realms grouped by alliance, in order of first
## appearance. A free-for-all is one side per realm.
static func sidesOf(world: World) -> Array:
	var bySide := {}
	for t in world.numTeams:
		if t == world.hordeTeam:
			continue
		var a = world.alliances[t] if t < world.alliances.size() and world.alliances[t] != null else t
		if not bySide.has(a):
			bySide[a] = []
		bySide[a].append(t)
	return bySide.values()


var panel := "overview"
## -1 = see everything; otherwise the team whose vision is shown.
var vision := -1
var auto := true
var clean := false
var graphs := false
var help := false
## Broadcast delay for online casting, in seconds.
var delaySec := 0.0
var feed: Array = []  ## {at, time, text, color, x, y, weight}
var heat: Array = []  ## {x, y, w, t, team}
var announced: Array = []
var defeated := {}
var firstBlood := false
var lastCut := -99.0
var manualUntil := -1.0
var lastSet = null
var tourIndex := 0
var target = null
var realms: Array = []
var realmsAt := -1.0


func reset() -> void:
	feed = []
	heat = []
	announced = []
	defeated.clear()
	firstBlood = false
	lastCut = -99.0
	manualUntil = -1.0
	lastSet = null
	tourIndex = 0
	target = null
	realmsAt = -1.0
	vision = -1


# ------------------------------------------------------------- events --

## Read the sim's events: they feed the director's heat map and the feed.
func onEvents(world: World, events: Array, names: Array) -> void:
	var now := world.time
	var who := func(t: int) -> String:
		return names[t] if t < names.size() and str(names[t]) != "" else C.PAL.teams[t % C.PAL.teams.size()].name
	for ev in events:
		var data = ev.get("data")
		if ev.kind == "death":
			var def = C.UNITS.get(str(data)) if data != null else null
			heat.append({"x": ev.x, "y": ev.y, "w": 1.0 if def != null and data != "villager" else 0.7, "t": now, "team": ev.team})
			if not firstBlood and data != null and data != "" and data != "sheep" and ev.team >= 0 and ev.team < world.numTeams and ev.team != world.hordeTeam:
				firstBlood = true
				var un = C.UNITS.get(data)
				push(world, "First blood — %s loses a %s" % [who.call(ev.team), un.name if un != null else data], teamLight(ev.team), ev.x, ev.y, 1)
		elif ev.kind == "collapse":
			heat.append({"x": ev.x, "y": ev.y, "w": 3.0, "t": now, "team": ev.team})
			if MAJOR_BUILDINGS.has(str(data)) and ev.team < world.numTeams:
				var bd = C.BUILDINGS.get(data)
				push(world, "%s's %s has fallen" % [who.call(ev.team), bd.name if bd != null else data], teamLight(ev.team), ev.x, ev.y, 3)
		elif ev.kind == "underattack":
			heat.append({"x": ev.x, "y": ev.y, "w": 0.25, "t": now, "team": ev.team})
		elif ev.kind == "age" and ev.team < world.numTeams:
			var ages: Array = C.AGES
			var ai := int(str(data).to_int()) if data != null else -1
			var age = ages[ai] if ai >= 0 and ai < ages.size() else null
			push(world, "%s reaches the %s" % [who.call(ev.team), age.name if age != null else "next age"], teamLight(ev.team), ev.x, ev.y, 2)
		elif ev.kind == "oath" and ev.team < world.numTeams:
			var od = C.OATHS.get(str(data)) if data != null else null
			push(world, "%s swears the %s" % [who.call(ev.team), od.name if od != null else "an Oath"], teamLight(ev.team), ev.x, ev.y, 2)
		elif ev.kind == "charge":
			heat.append({"x": ev.x, "y": ev.y, "w": 0.6, "t": now, "team": ev.team})
	for t in world.numTeams:
		if t == world.hordeTeam or defeated.has(t) or not world.player(t).defeated:
			continue
		defeated[t] = true
		var st = world.map.starts[t] if t < world.map.starts.size() else null
		push(world, "%s has been eliminated" % who.call(t), teamLight(t), st.x if st != null else world.worldW / 2, st.y if st != null else world.worldH / 2, 4)
	if world.winner != null and world.winner != T.Team.Neutral and not feed.any(func(f): return (f.text as String).contains("wins the match")):
		var win: Array = [world.winner]
		for s in sidesOf(world):
			if s.has(world.winner):
				win = s
				break
		var ns: Array = win.map(func(t): return who.call(t))
		push(world, "%s wins the match!" % " & ".join(ns), Ctx.parse_color("#ffe9b0"), world.worldW / 2, world.worldH / 2, 5)


func push(world: World, text: String, color, x: float, y: float, weight: float) -> void:
	feed.append({"at": perfNow(), "time": world.time, "text": text, "color": color, "x": x, "y": y, "weight": weight})
	if feed.size() > 60:
		feed.pop_front()


# ------------------------------------------------------------ director --

## The hottest spot on the map right now, if any fight is on: {x, y, score,
## teams (team -> count)} or null.
func hotspot(world: World):
	var now := world.time
	heat = heat.filter(func(h): return now - h.t < 14)
	if heat.is_empty():
		return null
	var CELL := 360.0
	var cells := {}
	for h in heat:
		var decay := pow(0.5, (now - h.t) / 5)
		var k := "%d,%d" % [floori(h.x / CELL), floori(h.y / CELL)]
		if not cells.has(k):
			cells[k] = {"x": 0.0, "y": 0.0, "w": 0.0, "teams": {}}
		var c: Dictionary = cells[k]
		c.x += h.x * h.w * decay
		c.y += h.y * h.w * decay
		c.w += h.w * decay
		c.teams[h.team] = c.teams.get(h.team, 0) + 1
	var best = null
	for c in cells.values():
		if best == null or c.w > best.score:
			best = {"x": c.x / c.w, "y": c.y / c.w, "score": c.w, "teams": c.teams}
	return best


## Tell the director the caster moved the camera: it waits before taking over again.
func manual(world: World, seconds: float = 10) -> void:
	manualUntil = world.time + seconds


## Move the camera if the director is on. Returns true while it is steering.
## Big fights are announced in the feed once per place. `camera` is anything
## with x, y and zoom (a Camera, or a Dictionary).
func direct(world: World, camera, dt: float) -> bool:
	var now := world.time
	# Spot the caster taking the camera (keys, edge-pan, drag, minimap).
	if lastSet != null and JS.hypot(camera.x - lastSet.x, camera.y - lastSet.y) > 3:
		manual(world)
	var hot = hotspot(world)
	if hot != null and hot.score >= 6 and not announced.any(func(a): return now - a.t < 25 and JS.hypot(a.x - hot.x, a.y - hot.y) < 700):
		announced.append({"x": hot.x, "y": hot.y, "t": now})
		var sides: Array = []
		for t in hot.teams:
			if t >= 0 and t < world.numTeams:
				sides.append([t, hot.teams[t]])
		JS.sort_by_cmp(sides, func(a, b): return b[1] - a[1])
		sides = sides.slice(0, 2)
		var teams: Array = C.PAL.teams
		var label := "Battle — %s vs %s" % [teams[sides[0][0] % teams.size()].name, teams[sides[1][0] % teams.size()].name] if sides.size() == 2 else "A fight breaks out"
		push(world, label, Ctx.parse_color("#f0c070"), hot.x, hot.y, 2)
	if not auto or now < manualUntil:
		lastSet = null
		return false
	# Choose where to look: a real fight beats everything; otherwise tour the bases.
	var far := func(x: float, y: float) -> bool:
		return JS.hypot(camera.x - x, camera.y - y) > 420
	if hot != null and hot.score >= 2.5:
		if target == null or target.reason != "fight" or (now - lastCut > 5 and far.call(hot.x, hot.y)) or not far.call(target.x, target.y):
			if target == null or target.reason != "fight" or now - lastCut > 5:
				target = {"x": hot.x, "y": hot.y, "reason": "fight"}
				lastCut = now
			else:
				# Stay on the fight but follow it as it moves.
				target.x += (hot.x - target.x) * minf(1, dt * 0.8)
				target.y += (hot.y - target.y) * minf(1, dt * 0.8)
	elif target == null or now - lastCut > 12:
		var alive: Array = []
		for t in world.numTeams:
			if t != world.hordeTeam and not world.player(t).defeated:
				alive.append(t)
		if not alive.is_empty():
			var t: int = alive[tourIndex % alive.size()]
			tourIndex += 1
			var st = null
			for e: Entity in world.entities:
				if e.alive and e.team == t and e.type == "town_center":
					st = e
					break
			if st == null and t < world.map.starts.size():
				st = world.map.starts[t]
			if st != null:
				target = {"x": st.x, "y": st.y, "reason": "tour"}
				lastCut = now
	if target == null:
		return false
	var k := minf(1, dt * (2.6 if target.reason == "fight" else 1.4))
	camera.x += (target.x - camera.x) * k
	camera.y += (target.y - camera.y) * k
	lastSet = {"x": camera.x, "y": camera.y}
	return true


# ---------------------------------------------------------------- keys --

## Caster hotkeys. Returns true if the key was the caster's.
func key(k0: String, world: World) -> bool:
	var k := k0.to_lower() if k0.length() == 1 else k0
	if k.length() == 1 and k >= "1" and k <= "8":
		var t := int(k) - 1
		if t < world.numTeams and t != world.hordeTeam:
			vision = -1 if vision == t else t
		return true
	if k == "0" or k == "v":
		vision = -1
		return true
	if k == "a":
		auto = not auto
		if auto:
			manualUntil = -1
		return true
	if k == "h":
		clean = not clean
		return true
	if k == "g":
		graphs = not graphs
		return true
	if k == "?" or k == "/" or k == "F1":
		help = not help
		return true
	if k == "Tab":
		var i := 0
		for j in CASTER_PANELS.size():
			if CASTER_PANELS[j][0] == panel:
				i = j
		panel = CASTER_PANELS[(i + 1) % CASTER_PANELS.size()][0]
		return true
	var m := {"q": "overview", "w": "army", "e": "economy", "r": "production", "t": "tech"}
	if m.has(k):
		panel = m[k]
		return true
	return false


# ---------------------------------------------------------------- draw --

func draw(W: float, H: float, c: Dictionary) -> Dictionary:
	var req := {}
	var world: World = c.world
	# A snapshot a few times a second is plenty for a scoreboard.
	if realmsAt < 0 or absf(world.time - realmsAt) > 0.25:
		realms = snapshotRealms(world)
		realmsAt = world.time
	var teams: Array = []
	for r in realms:
		if r.team != world.hordeTeam:
			teams.append(r.team)
	var names: Array = c.get("names", [])
	var name := func(t: int) -> String:
		return names[t] if t < names.size() and str(names[t]) != "" else C.PAL.teams[t % C.PAL.teams.size()].name

	var barH := 40.0 if clean else 66.0
	topBar(W, barH, c, teams, name, req)
	if not clean:
		tugOfWar(W, barH, c, realms)
	feedDraw(W, H, c, req)
	if clean:
		UI.ui.text("H — show caster view", W - 12, H - 10, {"align": "right", "size": 11, "color": Palette.withAlpha("#ffffff", 0.35)})
		return req
	minimapFights(H, world)
	var panelTop := H - 176
	if c.get("replay") != null:
		timeline(W, panelTop - 40, c, req)
	bottomPanel(W, H, panelTop, c, realms, teams, name)
	statusChips(W, barH + 44, c, req)
	if graphs:
		graphPanel(W, barH + 76, c, teams, name)
	if help:
		helpPanel(W, H)
	return req


func topBar(W: float, h: float, c: Dictionary, teams: Array, name: Callable, req: Dictionary) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var world: World = c.world
	var PAL: Dictionary = C.PAL
	var g := ctx.createLinearGradient(0, 0, 0, h)
	g.addColorStop(0, "rgba(12,9,5,0.94)")
	g.addColorStop(1, "rgba(22,16,9,0.9)")
	ctx.fillStyle = g
	ctx.fillRect(0, 0, W, h)
	ctx.fillStyle = Palette.withAlpha(PAL.uiAccent, 0.45)
	ctx.fillRect(0, h - 1, W, 1)
	# Centre block: clock and source.
	var cw := 150.0
	var cx := W / 2 - cw / 2
	ui.text(clock(world.time), W / 2, 27 if clean else 30, {"align": "center", "size": 20 if clean else 24, "bold": true, "color": "#fff0cc", "font": "Georgia, serif"})
	if not clean:
		var source: String = c.get("source", "live")
		var src := "REPLAY" if source == "replay" else ("LIVE · %ss DELAY" % JS.num(JS.round(c.get("delaySec", 0))) if source == "delay" else "LIVE")
		var col := "#7fb0e8" if source == "replay" else "#e0786a"
		var spd: float = c.get("speed", 1)
		ui.text("%s%s%s" % ["❚❚ " if c.get("paused", false) else "", src, " · %s×" % JS.num(spd) if spd != 1 and source != "delay" else ""], W / 2, 52, {"align": "center", "size": 11, "bold": true, "color": col})
	# Realm cards either side of the clock: first half left, second half right.
	var half := int(ceil(teams.size() / 2.0))
	var left := teams.slice(0, half)
	var right := teams.slice(half)
	_drawSide(left, 0, cx, h, c, name, req)
	_drawSide(right, cx + cw, W, h, c, name, req)


func _drawSide(list: Array, x0: float, x1: float, h: float, c: Dictionary, name: Callable, req: Dictionary) -> void:
	if list.is_empty():
		return
	var w := (x1 - x0) / list.size()
	for i in list.size():
		var t: int = list[i]
		realmCard(x0 + i * w, 0, w, h, t, name.call(t), c, req)


func realmCard(x: float, y: float, w: float, h: float, t: int, nm: String, c: Dictionary, req: Dictionary) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var world: World = c.world
	var p := world.player(t)
	var r = realms[t] if t < realms.size() else null
	var f := Factions.factionOf(p.faction)
	var out := p.defeated
	var hov := ui.hit(x, y, w, h)
	ctx.fillStyle = "rgba(255,255,255,0.05)" if hov else "transparent"
	ctx.fillRect(x, y, w, h)
	ctx.fillStyle = teamCol(t)
	ctx.fillRect(x + 2, y + 6, 4, h - 12)
	if vision == t:
		ctx.strokeStyle = teamLight(t)
		ctx.lineWidth = 2
		ctx.strokeRect(x + 1, y + 1, w - 2, h - 2)
	var tx := x + 12
	var nameSize := 12 if w < 170 else 14
	ui.text(nm.substr(0, 17) + "…" if nm.length() > 18 else nm, tx, y + 18, {"size": nameSize, "bold": true, "color": "#6f6a5c" if out else teamLight(t)})
	var fname: String = (f.name as String).trim_prefix("The ")
	var army: int = r.army if r != null else 0
	if clean:
		ui.text("%s · %d army" % [Tech.ageShort(p.age), army], x + w - 8, y + 18, {"align": "right", "size": 11, "color": "#c9bea3"})
		ui.text(fname, tx, y + 33, {"size": 10.5, "color": f.color})
	else:
		var oaths := ""
		if not p.oaths.is_empty():
			var shorts: Array = p.oaths.map(func(o): return C.OATHS[o].short if C.OATHS.has(o) else o)
			oaths = " · " + "/".join(shorts)
		ui.text("%s · %s%s" % [fname, Tech.ageShort(p.age), oaths], tx, y + 33, {"size": 10.5, "color": f.color})
		if out:
			ui.text("ELIMINATED", tx, y + 53, {"size": 11, "bold": true, "color": "#e0786a"})
		else:
			var res: Dictionary = p.resources
			var line := "🍖%s  🪵%s  🪙%s   👥%s/%s" % [num(res.food), num(res.wood), num(res.gold), JS.num(p.popUsed), JS.num(p.popCap)] if w >= 250 \
				else "👥%s/%s · ⚔%d" % [JS.num(p.popUsed), JS.num(p.popCap), army]
			ui.text(line, tx, y + 53, {"size": 11, "color": "#e2d6ba"})
		if w >= 200 and not out:
			ui.text("⚔ %s" % num(r.armyValue if r != null else 0.0), x + w - 8, y + 18, {"align": "right", "size": 11.5, "bold": true, "color": "#f0c070"})
	if hov:
		ui.pointerConsumed = true
		var an: String = C.AGES[p.age].name if p.age < C.AGES.size() else ""
		ui.tooltip([nm, "%s · %s" % [f.name, an], "Click: look at this realm · 1–8: its vision"])
		if ui.clicked:
			var st = null
			for e: Entity in world.entities:
				if e.alive and e.team == t and e.type == "town_center":
					st = e
					break
			if st == null and t < world.map.starts.size():
				st = world.map.starts[t]
			if st != null:
				req.focus = {"x": st.x, "y": st.y}


func tugOfWar(W: float, top: float, c: Dictionary, rs: Array) -> void:
	var ui := UI.ui
	var world: World = c.world
	var sides := sidesOf(world)
	if sides.size() < 2:
		return
	var metrics := [
		["ARMY", func(t): return rs[t].armyValue if t < rs.size() else 0.0],
		["ECONOMY", func(t): return world.player(t).stats.gathered],
		["KILLS", func(t): return world.player(t).stats.unitsKilled],
	]
	var bw := minf(260, (W - 80) / 3)
	var gap := 14.0
	var x0 := W / 2 - (bw * 3 + gap * 2) / 2
	var ctx := ui.ctx
	ctx.fillStyle = "rgba(10,7,4,0.75)"
	ctx.beginPath(); ctx.roundRect(x0 - 10, top + 4, bw * 3 + gap * 2 + 20, 34, 6); ctx.fill()
	for i in metrics.size():
		var label: String = metrics[i][0]
		var fn: Callable = metrics[i][1]
		var bx := x0 + i * (bw + gap)
		var vals: Array = []
		for s in sides:
			var v := 0.0
			for t in s:
				v += float(fn.call(t))
			vals.append(v)
		var total := 0.0
		for v in vals:
			total += v
		ui.text(label, bx + bw / 2, top + 17, {"align": "center", "size": 9.5, "bold": true, "color": "#a89f88"})
		var x := bx
		for k in sides.size():
			var w: float = (vals[k] / total) * bw if total > 0 else bw / sides.size()
			ctx.fillStyle = teamCol(sides[k][0])
			ctx.fillRect(x, top + 22, maxf(0, w - 1), 9)
			x += w
		ui.text(num(vals[0]), bx, top + 17, {"size": 10, "bold": true, "color": teamLight(sides[0][0])})
		ui.text(num(vals[vals.size() - 1]), bx + bw, top + 17, {"align": "right", "size": 10, "bold": true, "color": teamLight(sides[sides.size() - 1][0])})


func statusChips(W: float, y: float, c: Dictionary, req: Dictionary) -> void:
	var ui := UI.ui
	var names: Array = c.get("names", [])
	var vname := ""
	if vision >= 0:
		vname = names[vision] if vision < names.size() and str(names[vision]) != "" else C.PAL.teams[vision].name
	var chips := [
		["🎥 Auto camera: ON" if auto else "🎥 Auto camera: off", auto, "auto", "A — the director follows the biggest fight, and tours the bases when it's quiet. Move the camera yourself and it waits."],
		["👁 Vision: everything" if vision < 0 else "👁 Vision: %s" % vname, vision >= 0, "vision", "1–8 — see exactly what one player sees (fog of war included). 0 or V — everything."],
		["📈 Graphs", graphs, "graphs", "G — army value and score over time."],
		["▭ Clean feed", false, "clean", "H — hide the caster panels for streaming."],
		["? Keys", help, "help", "All caster hotkeys."],
	]
	var x := 12.0
	for ch in chips:
		var label: String = ch[0]
		var w := maxf(80, label.length() * 6.6 + 18)
		var sp := label.find(" ")
		var short := label.substr(sp + 1) if sp >= 0 else label
		if ui.button(label, x, y, w, 24, {"size": 11, "accent": ch[1], "tooltip": [short, ch[3]]}):
			match ch[2]:
				"auto": auto = not auto
				"vision": vision = -1
				"graphs": graphs = not graphs
				"clean": clean = true
				"help": help = not help
		x += w + 6
	# Speed for a live AI game (a replay has its own on the timeline).
	var paused: bool = c.get("paused", false)
	var speed: float = c.get("speed", 1)
	if c.get("offline", false) and c.get("replay") == null:
		x += 8
		if ui.button("▶" if paused else "❚❚", x, y, 28, 24, {"size": 11, "accent": paused, "tooltip": ["Pause", "Space"]}):
			req.togglePause = true
		x += 32
		for s in [0.5, 1.0, 2.0, 4.0, 8.0, 16.0]:
			if ui.button("%s×" % JS.num(s), x, y, 36, 24, {"size": 10.5, "accent": speed == s and not paused, "tooltip": ["Speed", "+ / −"]}):
				req.speed = s
			x += 40
	if c.get("replay") != null:
		if ui.button("⬇ Save replay file", x + 8, y, 130, 24, {"size": 11, "tooltip": ["Download this replay", "A small file anyone with the game can open and watch — share it, or keep it."]}):
			req.download = true
		x += 138
	if ui.button("Leave", x + 8, y, 60, 24, {"size": 11, "danger": true, "tooltip": ["Stop watching", "Esc. The match is kept as a replay."]}):
		req.exit = true


func feedDraw(W: float, H: float, c: Dictionary, req: Dictionary) -> void:
	var ui := UI.ui
	var now := perfNow()
	var shown: Array = feed.filter(func(f): return now - f.at < 14000)
	shown = shown.slice(maxi(0, shown.size() - 7))
	var x := W - 12
	var w := 340.0
	var y := 56.0 if clean else 120.0
	for f in shown:
		var age: float = (now - f.at) / 14000
		var a := (1 - age) / 0.2 if age > 0.8 else 1.0
		var ctx := ui.ctx
		ctx.fillStyle = Color(10 / 255.0, 7 / 255.0, 4 / 255.0, 0.72 * a)
		ctx.beginPath(); ctx.roundRect(x - w, y - 15, w, 22, 5); ctx.fill()
		ctx.fillStyle = Palette.withAlpha(f.color, a)
		ctx.fillRect(x - w, y - 15, 3, 22)
		ui.text(clock(f.time), x - w + 10, y, {"size": 10.5, "color": Palette.withAlpha("#a89f88", a)})
		var text: String = f.text
		ui.text(text.substr(0, 43) + "…" if text.length() > 44 else text, x - w + 50, y, {"size": 12, "bold": f.weight >= 3, "color": Palette.withAlpha(f.color, a)})
		if ui.hit(x - w, y - 15, w, 22):
			ui.pointerConsumed = true
			if ui.clicked:
				req.focus = {"x": f.x, "y": f.y}
		y += 26


func minimapFights(H: float, world: World) -> void:
	var hot := heat
	if hot.is_empty():
		return
	var ctx := UI.ui.ctx
	var mmX := 10.0
	var mmY := H - HUD.MINIMAP_SIZE - 10
	var sx := HUD.MINIMAP_SIZE / world.worldW
	var sy := HUD.MINIMAP_SIZE / world.worldH
	var pulse := 0.5 + 0.5 * sin(perfNow() / 180)
	var seen: Array = []
	for h in hot:
		if world.time - h.t > 8 or h.w < 0.5:
			continue
		if seen.any(func(s): return JS.hypot(s.x - h.x, s.y - h.y) < 300):
			continue
		seen.append({"x": h.x, "y": h.y})
		ctx.strokeStyle = Color(1, 120 / 255.0, 80 / 255.0, 0.5 + 0.4 * pulse)
		ctx.lineWidth = 1.5
		ctx.beginPath(); ctx.arc(mmX + h.x * sx, mmY + h.y * sy, 4 + pulse * 3, 0, PI * 2); ctx.stroke()


func timeline(W: float, y: float, c: Dictionary, req: Dictionary) -> void:
	var ui := UI.ui
	var rp: Dictionary = c.replay
	var SIM_HZ: float = C.SIM_HZ
	var x0 := HUD.MINIMAP_SIZE + 30.0
	var x1 := W - 20
	var w := x1 - x0
	var paused: bool = c.get("paused", false)
	var speed: float = c.get("speed", 1)
	ui.panel(x0 - 6, y - 4, w + 12, 34)
	if ui.button("▶" if paused else "❚❚", x0, y, 30, 26, {"size": 12, "tooltip": ["Play / pause", "Space"]}):
		req.togglePause = true
	var speeds := [0.5, 1.0, 2.0, 4.0, 8.0, 16.0]
	for i in speeds.size():
		var s: float = speeds[i]
		if ui.button("%s×" % JS.num(s), x0 + 36 + i * 38, y, 34, 26, {"size": 11, "accent": speed == s}):
			req.speed = s
	var bx := x0 + 36 + speeds.size() * 38 + 10
	var bw := x1 - bx - 70
	var frac: float = float(rp.tick) / rp.endTick if float(rp.endTick) != 0 else 0.0
	ui.bar(bx, y + 9, bw, 8, frac, "#7fb0e8")
	ui.text("%s / %s" % [clock(rp.tick / SIM_HZ), clock(rp.endTick / SIM_HZ)], x1, y + 17, {"align": "right", "size": 11.5, "color": "#c9bea3"})
	if ui.hit(bx, y, bw, 26):
		ui.pointerConsumed = true
		var at := clampf((ui.mx - bx) / bw, 0, 1)
		ui.tooltip(["Jump to %s" % clock((at * rp.endTick) / SIM_HZ), "Going back re-runs the match from the start, so it takes a moment."])
		if ui.clicked:
			req.seekTo = int(JS.round(at * rp.endTick))


func bottomPanel(W: float, H: float, top: float, c: Dictionary, rs: Array, teams: Array, name: Callable) -> void:
	var ui := UI.ui
	var world: World = c.world
	var x0 := HUD.MINIMAP_SIZE + 30.0
	var x1 := W - 12
	var w := x1 - x0
	ui.panel(x0 - 6, top - 6, w + 12, H - top)
	for i in CASTER_PANELS.size():
		var id: String = CASTER_PANELS[i][0]
		var label: String = CASTER_PANELS[i][1]
		if ui.button(label, x0 + i * 104, top, 98, 24, {"size": 11.5, "accent": panel == id, "tooltip": [label, "Q W E R T, or Tab"]}):
			panel = id
	var rowTop := top + 34
	var rowH := minf(26, (H - rowTop - 10) / maxf(1, teams.size()))
	var ctx := ui.ctx
	for i in teams.size():
		var t: int = teams[i]
		var y := rowTop + i * rowH
		var p := world.player(t)
		var r: Dictionary = rs[t]
		ctx.fillStyle = "rgba(255,255,255,0.025)" if i % 2 else "transparent"
		ctx.fillRect(x0, y, w, rowH)
		ctx.fillStyle = teamCol(t)
		ctx.fillRect(x0, y + 3, 3, rowH - 6)
		var nm: String = name.call(t)
		ui.text(nm.substr(0, 13) + "…" if nm.length() > 14 else nm, x0 + 10, y + rowH / 2 + 4, {"size": 12, "bold": true, "color": "#6f6a5c" if p.defeated else teamLight(t)})
		var cx := x0 + 130
		var col := "#e2d6ba"
		var dim := "#a89f88"
		var cells := func(vals: Array, cw: float) -> void:
			for j in vals.size():
				ui.text(vals[j][0], cx + j * cw, y + rowH / 2 + 4, {"size": 10.5, "color": dim})
				ui.text(vals[j][1], cx + j * cw + cw - 12, y + rowH / 2 + 4, {"align": "right", "size": 12, "bold": true, "color": col})
		if panel == "overview":
			var s: Dictionary = p.stats
			cells.call([["Age", Tech.ageShort(p.age)], ["Pop", "%s/%s" % [JS.num(p.popUsed), JS.num(p.popCap)]], ["Villagers", str(r.villagers)], ["Army", str(r.army)],
				["Army value", num(r.armyValue)], ["Kills", JS.num(s.unitsKilled)], ["Lost", JS.num(s.unitsLost)], ["Buildings", str(r.buildings)]], minf(120, (w - 140) / 8))
		elif panel == "economy":
			var res: Dictionary = p.resources
			var hist: Array = c.get("history", [])
			var rate := 0.0
			if hist.size() >= 2:
				var a = hist[maxi(0, hist.size() - 16)]
				var b = hist[hist.size() - 1]
				var dt: float = b.t - a.t
				var be: float = b.m[t].economy if t < b.m.size() else 0.0
				var ae: float = a.m[t].economy if t < a.m.size() else 0.0
				rate = (be - ae) / (dt / 60) if dt > 0 else 0.0
			cells.call([["Food", num(res.food)], ["Wood", num(res.wood)], ["Gold", num(res.gold)], ["Gathered", num(p.stats.gathered)],
				["Per min", num(rate)], ["Villagers", str(r.villagers)], ["Idle", str(r.idleVillagers)], ["Spent", num(p.stats.resourcesSpent)]], minf(120, (w - 140) / 8))
		elif panel == "army":
			var list: Array = []
			for u in r.units:
				if u != "villager":
					list.append([u, r.units[u]])
			JS.sort_by_cmp(list, func(a, b): return b[1] - a[1])
			list = list.slice(0, 8)
			var ux := cx
			if list.is_empty():
				ui.text("no army", ux, y + rowH / 2 + 4, {"size": 11.5, "color": dim})
			for un in list:
				var ud = C.UNITS.get(un[0])
				var label := "%s ×%d" % [ud.name if ud != null else un[0], un[1]]
				ui.text(label, ux, y + rowH / 2 + 4, {"size": 11.5, "color": col})
				ctx.font = "11.5px sans-serif"
				ux += ctx.measureText(label).width + 16
				if ux > x1 - 60:
					break
		elif panel == "production":
			var px := cx
			var pw := 118.0
			if r.production.is_empty():
				ui.text("nothing in production", px, y + rowH / 2 + 4, {"size": 11.5, "color": dim})
			var maxN := maxi(0, floori((w - 140) / (pw + 8)))
			for it in r.production.slice(0, maxN):
				var k: String = (it.item as String).substr(0, 1)
				ui.bar(px, y + rowH - 8, pw, 4, it.progress, "#f0c070" if k == "a" else ("#7fb0e8" if k == "t" else teamLight(t)))
				var lbl: String = it.label
				ui.text(lbl.substr(0, 15) + "…" if lbl.length() > 16 else lbl, px, y + rowH / 2 + 1, {"size": 11, "color": "#f0c070" if k == "a" else col})
				px += pw + 8
		elif panel == "tech":
			var ups: Array = p.upgrades.keys()
			var oaths: Array = p.oaths.map(func(o): return (C.OATHS[o].name as String).trim_prefix("Oath of the ") if C.OATHS.has(o) else o)
			ui.text(C.AGES[p.age].name if p.age < C.AGES.size() else "", cx, y + rowH / 2 + 4, {"size": 12, "bold": true, "color": "#f0c070"})
			ui.text("Oaths: %s" % ", ".join(oaths) if not oaths.is_empty() else "No Oath yet", cx + 130, y + rowH / 2 + 4, {"size": 11.5, "color": col})
			var recentL: Array = ups.slice(maxi(0, ups.size() - 4)).map(func(u): return C.UPGRADES[u].name if C.UPGRADES.has(u) else u)
			var recent := ", ".join(recentL)
			ui.text("%d researched%s" % [ups.size(), " — latest: %s" % recent if recent != "" else ""], cx + 380, y + rowH / 2 + 4, {"size": 11.5, "color": dim})


func graphPanel(W: float, top: float, c: Dictionary, teams: Array, name: Callable) -> void:
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	var hist: Array = c.get("history", [])
	var w := minf(560, W * 0.4)
	var h := 220.0
	var x := 12.0
	var y := top
	ui.panel(x, y, w, h)
	ui.text("ARMY VALUE", x + 14, y + 20, {"size": 10.5, "bold": true, "color": PAL.uiAccent})
	ui.text("SCORE", x + w / 2 + 6, y + 20, {"size": 10.5, "bold": true, "color": PAL.uiAccent})
	if hist.size() < 2:
		ui.text("Collecting…", x + w / 2, y + h / 2, {"align": "center", "size": 12, "color": "#a89f88"})
		return
	var ctx := ui.ctx
	var plot := func(px: float, pw: float, key: String) -> void:
		var mx := 1.0
		for s in hist:
			for t in teams:
				if t < s.m.size():
					mx = maxf(mx, float(s.m[t][key]))
		var t0: float = hist[0].t
		var t1: float = hist[hist.size() - 1].t
		if t1 == 0:
			t1 = 1
		for t in teams:
			ctx.strokeStyle = teamCol(t)
			ctx.lineWidth = 2
			ctx.beginPath()
			for i in hist.size():
				var s = hist[i]
				var X: float = px + ((s.t - t0) / maxf(1, t1 - t0)) * pw
				var v := float(s.m[t][key]) if t < s.m.size() else 0.0
				var Y := y + h - 22 - (v / mx) * (h - 56)
				if i:
					ctx.lineTo(X, Y)
				else:
					ctx.moveTo(X, Y)
			ctx.stroke()
	plot.call(x + 14, w / 2 - 24, "military")
	plot.call(x + w / 2 + 6, w / 2 - 20, "score")
	var shown := teams.slice(0, 8)
	for i in shown.size():
		var t: int = shown[i]
		ui.text((name.call(t) as String).substr(0, 10), x + 14 + i * ((w - 28) / mini(8, teams.size())), y + h - 6, {"size": 10, "bold": true, "color": teamLight(t)})


func helpPanel(W: float, H: float) -> void:
	var ui := UI.ui
	var w := 460.0
	var h := 330.0
	var x := W / 2 - w / 2
	var y := H / 2 - h / 2
	ui.panel(x, y, w, h)
	ui.text("Caster keys", x + w / 2, y + 32, {"align": "center", "size": 20, "bold": true, "color": "#ffe9b0", "font": "Georgia, serif"})
	var rows := [
		["1 – 8", "See one player's vision (again: back to all)"], ["0 / V", "See everything"], ["A", "Auto camera on / off"],
		["Q W E R T / Tab", "Overview · Army · Economy · Production · Tech"], ["G", "Graphs"], ["H", "Clean feed (for streaming)"],
		["Space", "Pause (replays and AI games)"], ["+ / −", "Speed"], ["Click a player", "Look at their base"],
		["Click the feed", "Go to where it happened"], ["Esc", "Leave"],
	]
	for i in rows.size():
		ui.text(rows[i][0], x + 30, y + 70 + i * 22, {"size": 12.5, "bold": true, "color": "#f0c070"})
		ui.text(rows[i][1], x + 170, y + 70 + i * 22, {"size": 12.5, "color": "#e2d6ba"})
