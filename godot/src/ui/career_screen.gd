## ui/career_screen.ts — the Career screen: a player's own numbers, offline
## and online together.
##
## Five tabs over one Career (meta/career.ts): an overview (record, time,
## favourites, best and worst, form, how the game goes for you), then factions,
## maps, units and the matches themselves. A filter at the top narrows it to
## skirmish, online or ranked games; "All time" reads the lifetime totals, which
## are exact however many games have been played.
##
## The module's exported helpers are statics here: `CareerScreen.fitText`,
## `CareerScreen.mmss`; `CareerScreen.localeDate` stands in for the browser's
## `new Date(ms).toLocaleDateString()` (en-US: M/D/YYYY, local time).
class_name CareerScreen
extends RefCounted

const TABS := [["overview", "Overview"], ["style", "Playstyle"], ["factions", "Factions"], ["maps", "Maps & modes"], ["units", "Units"], ["matches", "Matches"], ["replays", "Replays"]]
const FILTERS := [["all", "All time"], ["skirmish", "Skirmish"], ["online", "Online"], ["ranked", "Ranked"]]

const GOOD := "#8fd07a"
const BAD := "#e0786a"
const DIM := "#a89f88"
const FAINT := "#6f6a5c"
const TEXT := "#e9dcc0"
const GOLD := "#e8c060"


## Draw text so it fits `maxW`: shrink it (down to 75% of its size), and only if
## that isn't enough, cut it with an ellipsis. Nothing on this screen should run
## into the next column or out of its card. o: {size, bold, color, align, font}
static func fitText(text: String, x: float, y: float, maxW: float, o: Dictionary = {}) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var size: float = float(o.get("size", 12))
	var mn := size * 0.75
	var family: String = o.get("font", "'Trebuchet MS', sans-serif")
	var bold: bool = o.get("bold", false)
	var measure := func(t: String, sz: float) -> float:
		ctx.font = "%s%spx %s" % ["bold " if bold else "", JS.num(sz), family]
		return ctx.measureText(t).width
	while size > mn and measure.call(text, size) > maxW:
		size -= 0.5
	var t := text
	if measure.call(t, size) > maxW:
		while t.length() > 1 and measure.call(t + "…", size) > maxW:
			t = t.substr(0, t.length() - 1)
		t = t.strip_edges(false, true) + "…"
	var opts := o.duplicate()
	opts["size"] = size
	ui.text(t, x, y, opts)


## Word-wrap a short line into at most two lines (the second ends in … if cut).
static func wrapLines(text: String, x: float, y: float, maxW: float, size: float, color) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	ctx.font = "%spx 'Trebuchet MS', sans-serif" % JS.num(size)
	var lines: Array = []
	var line := ""
	for word in text.split(" "):
		var test := ("%s %s" % [line, word]) if line != "" else word
		if ctx.measureText(test).width > maxW and line != "":
			lines.append(line)
			line = word
		else:
			line = test
	if line != "":
		lines.append(line)
	var shown := lines.slice(0, 2)
	for i in shown.size():
		ui.text(("%s…" % shown[i]) if i == 1 and lines.size() > 2 else shown[i], x, y + i * (size + 3), {"size": size, "color": color})


# ---------------------------------------------------------------- format --
static func mmss(sec: float) -> String:
	if not sec or is_nan(sec):
		return "—"
	var s := int(JS.round(sec))
	var h := s / 3600
	var m := (s % 3600) / 60
	var x := s % 60
	return ("%dh %sm" % [h, str(m).lpad(2, "0")]) if h else ("%d:%s" % [m, str(x).lpad(2, "0")])


static func hours(sec: float) -> String:
	return ("%s h" % JS.to_fixed(sec / 3600.0, 1)) if sec >= 3600 else ("%s min" % JS.num(JS.round(sec / 60.0)))


static func pct(x: float) -> String:
	return "%s%%" % JS.num(JS.round(x * 100.0))


static func num(n: float) -> String:
	return JS.locale_int(n)


static func facName(id) -> String:
	var f = C.FACTIONS.get(id) if id != null else null
	return f.name if f != null else (str(id) if id else "—")


static func facColor(id):
	var f = C.FACTIONS.get(id) if id != null else null
	return f.color if f != null else "#8f8770"


static func unitName(id) -> String:
	var u = C.UNITS.get(id) if id != null else null
	return u.name if u != null else (str(id) if id else "—")


static func cmdName(id) -> String:
	var c = C.COMMANDERS.get(id) if id != null else null
	return c.name if c != null else (str(id) if id else "—")


static func oathName(id) -> String:
	var o = C.OATHS.get(id) if id != null else null
	return o.short if o != null else (str(id) if id else "—")


static func diffName(id) -> String:
	var d = C.DIFFICULTIES.get(id) if id != null else null
	if d != null:
		return d.name
	var s := str(id) if id else ""
	return (s.substr(0, 1).to_upper() + s.substr(1)) if s != "" else "—"


static func rateColor(r: float, n: float) -> String:
	if not n:
		return FAINT
	return GOOD if r >= 0.55 else (BAD if r <= 0.45 else GOLD)


static func _strip_the(s: String) -> String:
	return s.substr(4) if s.begins_with("The ") else s


## `new Date(ms)` in local time, as a Dictionary {year, month, day, hour,
## minute} (month 1–12).
static func localDate(ms: float) -> Dictionary:
	var bias: int = int(Time.get_time_zone_from_system().get("bias", 0))
	return Time.get_datetime_dict_from_unix_time(int(floor(ms / 1000.0)) + bias * 60)


## `new Date(ms).toLocaleDateString()` as an en-US browser writes it: M/D/YYYY.
static func localeDate(ms: float) -> String:
	var d := localDate(ms)
	return "%d/%d/%d" % [d.month, d.day, d.year]


static func _n(d, k: String) -> float:
	if d == null:
		return 0.0
	var v = d.get(k)
	return float(v) if v != null else 0.0


var tab := "overview"
var filter := "all"
var scroll := 0.0
var contentH := 0.0
var cache = null  ## { key, at, data } or null
var styleCache = null  ## { key, at, p } or null


## The playstyle read for the current filter (recomputed with the data).
func style() -> Dictionary:
	data()
	if styleCache == null or styleCache.key != filter or styleCache.at != cache.at:
		styleCache = {"key": filter, "at": cache.at, "p": Playstyle.analyse(cache.data.matches)}
	return styleCache.p


## Re-read storage at most twice a second (it's parsed JSON).
func data() -> Dictionary:
	var now := JS.now_ms()
	if cache == null or cache.key != filter or now - cache.at > 500:
		cache = {"key": filter, "at": now, "data": Career.careerFor(filter)}
	return cache.data


## Open straight on the replay list (coming back from watching one).
func showReplays() -> void:
	tab = "replays"
	scroll = 0


var watch = null  ## a ReplayRecord the player asked to watch, or null
var confirmDelete := ""


## Returns "back", { watch: ReplayRecord } or null.
func draw(W: float, H: float, time: float, profile: Profile) -> Variant:
	var ui := UI.ui
	var ctx := ui.ctx
	var bg := ctx.createLinearGradient(0, 0, 0, H)
	bg.addColorStop(0, "#221a10")
	bg.addColorStop(1, "#100b06")
	ctx.fillStyle = bg
	ctx.fillRect(0, 0, W, H)

	var outer := minf(W - 48, 1280)
	var x0 := JS.round(W / 2 - outer / 2)
	var d := data()
	var c: Dictionary = d.career
	var matches: Array = d.matches
	var exact: bool = d.exact

	# Header.
	ui.text("Career", x0, 50, {"size": 32, "bold": true, "color": "#ffe9b0", "font": "Georgia, serif"})
	var lvl := profile.levelInfo()
	ui.text("%s · Level %s%s" % [profile.data.name, JS.num(lvl.level), (" · playing since %s" % localeDate(c.since)) if c.since else ""], x0 + 2, 74, {"size": 13, "color": "#c9bea3"})
	for i in FILTERS.size():
		var id: String = FILTERS[i][0]
		var label: String = FILTERS[i][1]
		var bw := 96.0
		var bx := x0 + outer - (FILTERS.size() - i) * (bw + 6)
		if ui.button(label, bx, 28, bw, 30, {"accent": filter == id, "size": 12.5, "tooltip": [label, "Every game you've played, ever." if id == "all" else "Only %s games (from your last matches)." % label.to_lower()]}):
			if filter != id:
				filter = id
				scroll = 0
				audio.play("ui")
	if not exact:
		ui.text("from your most recent matches", x0 + outer, 74, {"align": "right", "size": 11, "color": FAINT})

	# Tabs.
	var ty := 90.0
	for i in TABS.size():
		var id: String = TABS[i][0]
		var label: String = TABS[i][1]
		var tw := minf(132, (outer - 8 * (TABS.size() - 1)) / TABS.size())
		if ui.button(label, x0 + i * (tw + 8), ty, tw, 32, {"accent": tab == id, "size": 13.5}):
			if tab != id:
				tab = id
				scroll = 0
				audio.play("ui")

	# Scrolling content.
	var top := ty + 44
	var FOOT := 64.0
	var viewH := H - top - FOOT
	var maxScroll := maxf(0, contentH - viewH)
	if ui.wheel and ui.my > top and ui.my < top + viewH:
		scroll = maxf(0, minf(maxScroll, scroll + ui.wheel * 0.6))
	scroll = minf(scroll, maxScroll)
	ui.pushScroll(scroll, {"x": 0, "y": top, "w": W, "h": viewH})
	var end := top
	if tab == "replays":
		end = replays(x0, top, outer)
	elif not c.all.played:
		end = empty(x0, top, outer)
	elif tab == "overview":
		end = overview(c, matches, x0, top, outer, time, profile)
	elif tab == "style":
		end = playstyle(style(), x0, top, outer, time)
	elif tab == "factions":
		end = factions(c, x0, top, outer, time)
	elif tab == "maps":
		end = maps(c, x0, top, outer)
	elif tab == "units":
		end = units(c, x0, top, outer, time)
	else:
		end = matchList(matches, x0, top, outer)
	contentH = end - top + 16
	ui.popScroll()
	ui.scrollbar(x0 + outer + 8, top, viewH, scroll, contentH)

	# Footer.
	var fy := H - FOOT
	ctx.fillStyle = "rgba(10,7,3,0.92)"
	ctx.fillRect(0, fy, W, FOOT)
	ctx.fillStyle = Palette.withAlpha(C.PAL.uiAccent, 0.3)
	ctx.fillRect(0, fy, W, 1)
	if ui.button("⟵  Back", x0, fy + 12, 130, 40, {"size": 15}):
		return "back"
	if watch != null:
		var w = watch
		watch = null
		return {"watch": w}
	ui.text("Skirmish and online games both count. Kept on this device.", x0 + outer, fy + 37, {"align": "right", "size": 11.5, "color": FAINT})
	return null


# ---------------------------------------------------------------- pieces --
func heading(x: float, y: float, w: float, t: String, sub: String = "") -> float:
	var ui := UI.ui
	ui.text(t.to_upper(), x, y + 14, {"size": 11, "bold": true, "color": C.PAL.uiAccent})
	if sub != "":
		ui.text(sub, x + w, y + 14, {"align": "right", "size": 11, "color": FAINT})
	ui.ctx.fillStyle = Palette.withAlpha(C.PAL.uiAccent, 0.22)
	ui.ctx.fillRect(x, y + 22, w, 1)
	return y + 32


func tile(x: float, y: float, w: float, h: float, label: String, value: String, sub: String = "", color = "#fff0cc") -> void:
	var ui := UI.ui
	ui.panel(x, y, w, h)
	ui.text(label.to_upper(), x + 14, y + 22, {"size": 10.5, "bold": true, "color": DIM})
	ui.text(value, x + 14, y + 52, {"size": 25, "bold": true, "color": color, "font": "Georgia, serif"})
	if sub != "":
		ui.text(sub, x + 14, y + 72, {"size": 11.5, "color": FAINT})


func rateBar(x: float, y: float, w: float, r: float, n: float) -> void:
	var ui := UI.ui
	ui.bar(x, y - 5, w, 8, r if n else 0.0, rateColor(r, n))
	ui.ctx.fillStyle = "rgba(255,255,255,0.25)"
	ui.ctx.fillRect(x + w / 2, y - 8, 1, 14)
	ui.text(pct(r) if n else "—", x + w + 8, y + 3, {"size": 12.5, "bold": true, "color": rateColor(r, n)})


func dot(x: float, y: float, color, r: float = 5) -> void:
	var ctx := UI.ui.ctx
	ctx.fillStyle = color
	ctx.beginPath(); ctx.arc(x, y, r, 0, PI * 2); ctx.fill()


## A unit portrait fitted inside a box: scaled so the whole figure (horse,
## lance and all) fits, standing on the box's bottom edge, and clipped to it
## so nothing can spill onto the card around it. box: {x, y, w, h}
func unitArt(id: String, box: Dictionary, time: float, faction = null) -> void:
	var def = C.UNITS.get(id)
	if def == null:
		return
	# A figure reaches ~3 radii up from its feet and ~1.6 either side; a
	# lance or bow a little past that.
	var radius: float = def.radius
	var s := minf(3, minf((box.h * 0.92) / (radius * 3.2), (box.w * 0.92) / (radius * 3.6)))
	var x: float = box.x + box.w / 2
	var y: float = box.y + box.h - radius * 0.7 * s - 2
	var e := World.makeEntity()
	e.kind = T.Kind.Unit; e.type = id; e.team = T.Team.Player; e.x = x; e.y = y; e.radius = radius
	e.hp = def.hp; e.maxHp = def.hp; e.facing = -0.5; e.attackInterval = def.attackInterval; e.animPhase = time * 0.3
	var f = faction
	if f == null:
		var fd = Factions.factionForUnit(id)
		f = fd.id if fd != null else null
	if f != null:
		Draw.setFactionResolver(func(_t): return f)
	var ctx := UI.ui.ctx
	ctx.save()
	ctx.beginPath()
	ctx.rect(box.x, box.y, box.w, box.h)
	ctx.clip()
	ctx.translate(x, y); ctx.scale(s, s); ctx.translate(-x, -y)
	Draw.drawUnit(ctx, e, time * 0.3)  # a portrait is never worth a crash
	ctx.restore()
	if f != null:
		Draw.setFactionResolver(null)


func empty(x: float, y: float, w: float) -> float:
	var ui := UI.ui
	ui.panel(x, y + 20, w, 140)
	ui.text("No games yet", x + w / 2, y + 74, {"align": "center", "size": 22, "bold": true, "color": "#ffe9b0", "font": "Georgia, serif"})
	ui.text("Play a Skirmish or an online match — every game you finish lands here." if filter == "all" else "No games of this kind yet. Try another filter.",
		x + w / 2, y + 104, {"align": "center", "size": 13.5, "color": DIM})
	return y + 170


# -------------------------------------------------------------- overview --
func overview(c: Dictionary, matches: Array, x: float, y: float, w: float, time: float, profile: Profile) -> float:
	var ui := UI.ui
	var a: Dictionary = c.all
	var cols := 6 if w >= 1000 else 3
	var gap := 12.0
	var tw := (w - gap * (cols - 1)) / cols
	var streak := float(c.streak)
	var streakText := ("%s win streak" % JS.num(streak)) if streak > 0 else (("%s loss streak" % JS.num(-streak)) if streak < 0 else "—")
	var tiles := [
		["Matches", num(a.played), "%s won · %s lost" % [JS.num(a.won), JS.num(a.played - a.won)]],
		["Win rate", pct(Career.winRate(a)), "%s · best %s" % [streakText, JS.num(c.bestStreak)], rateColor(Career.winRate(a), a.played)],
		["Time played", hours(a.secs), "longest game %s" % mmss(a.longest)],
		["Average game", mmss(Career.avgSecs(a)), "across %s match%s" % [JS.num(a.played), "" if a.played == 1 else "es"]],
		["Average win", mmss(Career.avgWinSecs(a)), "fastest %s" % mmss(a.fastestWin), GOOD],
		["Kills : losses", JS.to_fixed(Career.kd(a), 2), "%s killed · %s lost" % [num(a.kills), num(a.losses)]],
	]
	for i in tiles.size():
		var t: Array = tiles[i]
		tile(x + (i % cols) * (tw + gap), y + floori(float(i) / cols) * 96, tw, 86, t[0], t[1], t[2], t[3] if t.size() > 3 else "#fff0cc")
	y += ceilf(float(tiles.size()) / cols) * 96 + 8

	# Your style, in one line — the Playstyle tab has the why.
	if true:
		var sp := style()
		ui.panel(x, y, w, 64)
		ui.text("YOUR STYLE", x + 16, y + 22, {"size": 10.5, "bold": true, "color": DIM})
		fitText(sp.title, x + 16, y + 48, w * 0.36, {"size": 20, "bold": true, "color": GOLD if sp.primary != null else TEXT, "font": "Georgia, serif"})
		var left: int = Playstyle.MIN_GAMES - int(sp.games)
		var line: String
		if sp.primary != null:
			line = sp.primary.archetype.short
		elif sp.enough:
			line = "You adapt to the game in front of you."
		else:
			line = "Play %d more game%s to find out." % [left, "" if left == 1 else "s"]
		fitText(line, x + w * 0.4, y + 30, w * 0.42, {"size": 13, "color": TEXT})
		fitText(" · ".join(PackedStringArray(sp.traits.slice(0, 3).map(func(t): return t.name))), x + w * 0.4, y + 50, w * 0.42, {"size": 11.5, "color": DIM})
		if ui.button("Why? →", x + w - 116, y + 16, 100, 32, {"size": 13, "accent": true}):
			tab = "style"
			scroll = 0
			audio.play("ui")
		y += 76

	# Favourites.
	y = heading(x, y, w, "Your favourites", "most played")
	var favF := Career.favourite(c.byFaction)
	var favM := Career.favourite(c.byMap)
	var favU := Career.topUnit(c, "trained")
	var favC := Career.favourite(c.byCommander)
	var favO := Career.favourite(c.byOath)
	var cmdColor = C.COMMANDERS[favC].color if C.COMMANDERS.has(favC) else TEXT
	var me := self
	var favs := [
		["Faction", facName(favF), ("%s games · %s won" % [JS.num(c.byFaction[favF].played), pct(Career.winRate(c.byFaction[favF]))]) if favF else "", facColor(favF),
			func(bx, by):
				if favF:
					var fd: Dictionary = C.FACTIONS[favF]
					var u = fd.replaces.get("militia")
					if u == null:
						u = fd.extra[0] if fd.extra.size() > 0 else "militia"
					me.unitArt(u, {"x": bx, "y": by, "w": 64, "h": 80}, time, favF)],
		["Map", favM if favM else "—", ("%s games · %s won" % [JS.num(c.byMap[favM].played), pct(Career.winRate(c.byMap[favM]))]) if favM else "", TEXT, func(_bx, _by): pass],
		["Unit", unitName(favU), ("%s trained · %s lost" % [num(c.units[favU].trained), num(c.units[favU].lost)]) if favU else "", TEXT,
			func(bx, by):
				if favU:
					me.unitArt(favU, {"x": bx, "y": by, "w": 64, "h": 80}, time)],
		["Commander", cmdName(favC), ("%s games · %s won" % [JS.num(c.byCommander[favC].played), pct(Career.winRate(c.byCommander[favC]))]) if favC else "skirmish only", cmdColor, func(_bx, _by): pass],
		["Oath", oathName(favO) if favO else "—", ("sworn in %s games · %s won" % [JS.num(c.byOath[favO].played), pct(Career.winRate(c.byOath[favO]))]) if favO else "reach the Banner Age", TEXT, func(_bx, _by): pass],
	]
	var fcols := 5 if w >= 1000 else 3
	var fw := (w - gap * (fcols - 1)) / fcols
	for i in favs.size():
		var fv: Array = favs[i]
		var fx := x + (i % fcols) * (fw + gap)
		var fy := y + floori(float(i) / fcols) * 106
		ui.panel(fx, fy, fw, 96)
		ui.text(String(fv[0]).to_upper(), fx + 14, fy + 22, {"size": 10.5, "bold": true, "color": DIM})
		fitText(fv[1], fx + 14, fy + 50, fw - 90, {"size": 17, "bold": true, "color": fv[3], "font": "Georgia, serif"})
		fitText(fv[2], fx + 14, fy + 72, fw - 90, {"size": 11, "color": FAINT})
		(fv[4] as Callable).call(fx + fw - 72, fy + 8)
	y += ceilf(float(favs.size()) / fcols) * 106 + 8

	# Strengths and weaknesses.
	y = heading(x, y, w, "Where you're strong, where you're not", "3+ games each")
	var pairs := [
		["Best faction", "Weakest faction", c.byFaction, func(k): return facName(k), false],
		["Best map", "Toughest map", c.byMap, func(k): return k, false],
		["Easiest opponent", "Your nemesis", c.vsFaction, func(k): return facName(k), true],
	]
	var pw := (w - gap * 2) / 3
	for i in pairs.size():
		var pr: Array = pairs[i]
		var rec: Dictionary = pr[2]
		var name: Callable = pr[3]
		var px := x + i * (pw + gap)
		ui.panel(px, y, pw, 104)
		var b := Career.bestBy(rec)
		var wv := Career.worstBy(rec)
		var line := func(label: String, k: String, yy: float, col: String) -> void:
			ui.text(label, px + 14, yy, {"size": 11.5, "color": DIM})
			ui.text(name.call(k) if k else "not enough games", px + 14, yy + 20, {"size": 15, "bold": true, "color": col if k else FAINT})
			if k:
				ui.text("%s of %s" % [pct(Career.winRate(rec[k])), JS.num(rec[k].played)], px + pw - 14, yy + 20, {"align": "right", "size": 12.5, "color": col})
		line.call(pr[0], b, y + 24, GOOD)
		line.call(pr[1], wv if wv and wv != b else "", y + 66, BAD)
	y += 116

	# Recent form.
	y = heading(x, y, w, "Recent form", "last 30 games, newest on the right")
	var recent: Array = matches.slice(0, 30)
	recent.reverse()
	var sq := minf(26, (w - 29 * 4) / 30.0)
	for i in recent.size():
		var m: Dictionary = recent[i]
		var cx := x + i * (sq + 4)
		ui.ctx.fillStyle = "rgba(127,207,122,0.8)" if m.won else "rgba(224,120,106,0.75)"
		ui.ctx.beginPath(); ui.ctx.roundRect(cx, y, sq, sq, 4); ui.ctx.fill()
		ui.text("W" if m.won else "L", cx + sq / 2, y + sq / 2 + 4, {"align": "center", "size": 11, "bold": true, "color": "#1a140c"})
		if ui.hit(cx, y, sq, sq):
			var foes := ", ".join(PackedStringArray(m.foes.map(func(f): return facName(f))))
			ui.tooltip(["%s — %s" % ["Won" if m.won else "Lost", m.map], "%s vs %s" % [facName(m.faction), foes if foes != "" else "—"],
				"%s · %s" % [mmss(m.durationSec), (("ranked online" if m.get("ranked") else "online") if m.kind == "online" else "skirmish, %s" % diffName(m.difficulty))]])
	if recent.is_empty():
		ui.text("—", x, y + 16, {"color": FAINT})
	y += sq + 14
	# Rolling win rate (10 games) as a line.
	if recent.size() >= 4:
		var ch := 90.0
		var cw := w
		ui.panel(x, y, cw, ch + 24)
		var pts: Array = []
		for i in recent.size():
			var win: Array = recent.slice(maxi(0, i - 9), i + 1)
			pts.append(float(win.filter(func(m): return m.won).size()) / win.size())
		var pxf := func(i: int) -> float: return x + 16 + (float(i) / maxi(1, pts.size() - 1)) * (cw - 32)
		var pyf := func(v: float) -> float: return y + 12 + (1 - v) * ch
		var ctx := ui.ctx
		ctx.strokeStyle = "rgba(255,255,255,0.12)"; ctx.lineWidth = 1
		ctx.beginPath(); ctx.moveTo(x + 16, pyf.call(0.5)); ctx.lineTo(x + cw - 16, pyf.call(0.5)); ctx.stroke()
		ctx.strokeStyle = GOLD; ctx.lineWidth = 2.5; ctx.beginPath()
		for i in pts.size():
			if i:
				ctx.lineTo(pxf.call(i), pyf.call(pts[i]))
			else:
				ctx.moveTo(pxf.call(i), pyf.call(pts[i]))
		ctx.stroke()
		ui.text("win rate over your last 10 games, game by game", x + 16, y + ch + 18, {"size": 11, "color": FAINT})
		ui.text("now %s" % pct(pts[pts.size() - 1]), x + cw - 16, y + ch + 18, {"align": "right", "size": 11.5, "bold": true, "color": GOLD})
		y += ch + 36

	# Game by game: how your matches go.
	y = heading(x, y, w, "How your games go", "averages per match")
	var n := maxf(1, a.played)
	var perMin: float = (a.gathered / (a.secs / 60.0)) if a.secs else 0.0
	var rows := [
		["Resources gathered", "%s  (%s a minute)" % [num(a.gathered / n), num(perMin)]],
		["Food · wood · gold", "%s · %s · %s" % [num(c.resources.food / n), num(c.resources.wood / n), num(c.resources.gold / n)]],
		["Enemy buildings razed", "%s  ·  your buildings lost %s" % [JS.to_fixed(c.razed / n, 1), JS.to_fixed(c.buildingsLost / n, 1)]],
		["Damage dealt · taken", "%s · %s" % [num(c.damageDealt / n), num(c.damageTaken / n)]],
		["Technologies researched", JS.to_fixed(c.upgrades / n, 1)],
		["Biggest army ever", "%s soldiers  ·  best score %s" % [num(c.bestArmy), num(c.bestScore)]],
		["Town Centre idle", "%s of the match on average" % pct(c.tcIdleShareSum / n)],
		["Idle villager time", "%s villager-time a game" % mmss(c.idleVillagerTime / n)],
	]
	var half := ceili(rows.size() / 2.0)
	var cw2 := (w - gap) / 2
	ui.panel(x, y, w, half * 26 + 16)
	for i in rows.size():
		var rx := x + (0.0 if i < half else cw2 + gap) + 16
		var ry := y + 26 + (i % half) * 26
		ui.text(rows[i][0], rx, ry, {"size": 12.5, "color": DIM})
		ui.text(rows[i][1], rx + cw2 - 32, ry, {"align": "right", "size": 12.5, "bold": true, "color": TEXT})
	y += half * 26 + 28

	# Ages.
	y = heading(x, y, w, "Advancing", "share of games that reached each age, and how long it took")
	var aw := (w - gap * 3) / 4
	for i in 4:
		var ax := x + i * (aw + gap)
		ui.panel(ax, y, aw, 70)
		var reached: float = a.played if i == 0 else _at(c.ageReached, i)
		ui.text(C.AGES[i].name if i < C.AGES.size() else "Age %d" % i, ax + 14, y + 22, {"size": 13, "bold": true, "color": TEXT})
		ui.text(pct(reached / n), ax + 14, y + 50, {"size": 20, "bold": true, "color": GOLD, "font": "Georgia, serif"})
		ui.text("every game" if i == 0 else (("average %s" % mmss(_at(c.ageTimeSum, i) / reached)) if reached else "not yet"), ax + aw - 14, y + 50, {"align": "right", "size": 12, "color": DIM})
	y += 82

	# Skirmish vs online, and against each AI.
	y = heading(x, y, w, "By kind of game")
	var kinds := [["skirmish", "Skirmish (vs AI)"], ["online", "Online"], ["ranked", "Ranked online"]]
	var kw := (w - gap * 2) / 3
	for i in kinds.size():
		var t = c.byKind.get(kinds[i][0])
		var kx := x + i * (kw + gap)
		ui.panel(kx, y, kw, 74)
		ui.text(kinds[i][1], kx + 14, y + 22, {"size": 13, "bold": true, "color": TEXT})
		ui.text(("%s–%s" % [JS.num(t.won), JS.num(t.played - t.won)]) if t != null else "no games", kx + 14, y + 50, {"size": 18, "bold": true, "color": "#fff0cc" if t != null else FAINT, "font": "Georgia, serif"})
		if t != null:
			rateBar(kx + 110, y + 46, kw - 180, Career.winRate(t), t.played)
	y += 86
	var diffs: Array = []
	for k in c.byDifficulty:
		diffs.append([k, c.byDifficulty[k]])
	var diffKeys: Array = C.DIFFICULTIES.keys()
	var rank := func(k) -> int: return diffKeys.find(k) if C.DIFFICULTIES.has(k) else 99
	JS.sort_by_cmp(diffs, func(p, q): return rank.call(p[0]) - rank.call(q[0]))
	if diffs.size():
		y = heading(x, y, w, "Against each AI")
		ui.panel(x, y, w, diffs.size() * 28 + 14)
		for i in diffs.size():
			var dk: String = diffs[i][0]
			var t: Dictionary = diffs[i][1]
			var ry := y + 26 + i * 28
			ui.text(diffName(dk), x + 16, ry, {"size": 13, "bold": true, "color": TEXT})
			ui.text("%s–%s" % [JS.num(t.won), JS.num(t.played - t.won)], x + 200, ry, {"size": 12.5, "color": DIM})
			rateBar(x + 280, ry - 2, minf(360, w - 560), Career.winRate(t), t.played)
			ui.text("avg %s · win %s" % [mmss(Career.avgSecs(t)), mmss(Career.avgWinSecs(t))], x + w - 16, ry, {"align": "right", "size": 12, "color": DIM})
		y += diffs.size() * 28 + 26
	return y


static func _at(arr, i: int) -> float:
	if arr is Array:
		return float(arr[i]) if i < arr.size() and arr[i] != null else 0.0
	if arr is Dictionary:
		var v = arr.get(str(i), arr.get(i))
		return float(v) if v != null else 0.0
	return 0.0


# ------------------------------------------------------------- factions --
## head: [[label, cx, align?]]
func table(x: float, y: float, w: float, head: Array, rows: int) -> float:
	var ui := UI.ui
	ui.panel(x, y, w, 48 + rows * 34)
	# Each header gets the room up to its neighbour (whichever side it grows
	# towards), so long headers shrink instead of running together.
	var pos: Array = head.map(func(hd): return float(hd[1]))
	JS.sort_by_cmp(pos, func(a, b): return a - b)
	for hd in head:
		var label: String = hd[0]
		var cx: float = hd[1]
		var align: String = hd[2] if hd.size() > 2 else "left"
		var i := pos.find(cx)
		var room: float
		if align == "right":
			room = cx - (pos[i - 1] if i > 0 else x) - 10
		else:
			room = (pos[i + 1] if i < pos.size() - 1 else x + w) - cx - 10
		fitText(label.to_upper(), cx, y + 22, maxf(24, room), {"size": 10.5, "bold": true, "color": DIM, "align": align})
	return y + 40


func factions(c: Dictionary, x: float, y: float, w: float, time: float) -> float:
	var ui := UI.ui
	y = heading(x, y, w, "Your record with each faction")
	var K := {"name": x + 60, "played": x + 290, "wl": x + 330, "rate": x + 400, "avg": x + 700, "win": x + 790, "fast": x + 880, "kd": x + 950, "fav": x + 990}
	var ry := table(x, y, w, [["Faction", K.name], ["Games", K.played, "right"], ["W–L", K.wl], ["Win rate", K.rate], ["Avg game", K.avg, "right"], ["Avg win", K.win, "right"], ["Fastest win", K.fast, "right"], ["K : L", K.kd, "right"], ["Favourite unit", K.fav]], C.FACTION_IDS.size())
	var ids: Array = C.FACTION_IDS.duplicate()
	JS.sort_by_cmp(ids, func(p, q): return _n(c.byFaction.get(q), "played") - _n(c.byFaction.get(p), "played"))
	for id in ids:
		var t = c.byFaction.get(id)
		var f: Dictionary = C.FACTIONS[id]
		var col := TEXT if t != null else FAINT
		var u = f.replaces.get("militia")
		if u == null:
			u = f.extra[0] if f.extra.size() > 0 else "militia"
		unitArt(u, {"x": x + 8, "y": ry, "w": 44, "h": 32}, time, id)
		ui.text(f.name, K.name, ry + 21, {"size": 14, "bold": true, "color": f.color if t != null else FAINT})
		ui.text(JS.num(t.played) if t != null else "0", K.played, ry + 21, {"align": "right", "size": 13, "color": col})
		ui.text(("%s–%s" % [JS.num(t.won), JS.num(t.played - t.won)]) if t != null else "—", K.wl, ry + 21, {"size": 13, "color": col})
		rateBar(K.rate, ry + 19, 150, Career.winRate(t), _n(t, "played"))
		ui.text(mmss(Career.avgSecs(t)) if t != null else "—", K.avg, ry + 21, {"align": "right", "size": 13, "color": col})
		ui.text(mmss(Career.avgWinSecs(t)) if t != null else "—", K.win, ry + 21, {"align": "right", "size": 13, "color": GOOD if _n(t, "won") else FAINT})
		ui.text(mmss(t.fastestWin) if t != null else "—", K.fast, ry + 21, {"align": "right", "size": 13, "color": col})
		ui.text(JS.to_fixed(Career.kd(t), 2) if t != null else "—", K.kd, ry + 21, {"align": "right", "size": 13, "color": col})
		ui.text(unitName(Career.favouriteUnitOf(t)) if t != null else "—", K.fav, ry + 21, {"size": 13, "color": col})
		ry += 34
	y = ry + 32

	# Opponents and allies.
	var half := (w - 12) / 2
	var e1 := vsTable(x, y, half, "Against each enemy faction", c.vsFaction, "matches it was on the other side")
	var e2 := vsTable(x + half + 12, y, half, "Beside each ally faction", c.withFaction, "team games")
	return maxf(e1, e2) + 16


## The TS `vsTable` closure.
func vsTable(tx: float, y: float, half: float, title: String, rec: Dictionary, note: String) -> float:
	var ui := UI.ui
	var yy := heading(tx, y, half, title, note)
	var played: Array = C.FACTION_IDS.filter(func(id): return rec.has(id))
	yy = table(tx, yy, half, [["Faction", tx + 16], ["Games", tx + half * 0.5, "right"], ["Your win rate", tx + half * 0.55]], maxi(1, played.size()))
	if played.is_empty():
		ui.text("No games yet.", tx + 16, yy + 21, {"size": 13, "color": FAINT})
	JS.sort_by_cmp(played, func(p, q): return rec[q].played - rec[p].played)
	for id in played:
		dot(tx + 22, yy + 17, facColor(id))
		ui.text(facName(id), tx + 34, yy + 21, {"size": 13, "color": TEXT})
		ui.text(JS.num(rec[id].played), tx + half * 0.5, yy + 21, {"align": "right", "size": 13, "color": DIM})
		rateBar(tx + half * 0.55, yy + 19, half * 0.3, Career.winRate(rec[id]), rec[id].played)
		yy += 34
	return yy


# ----------------------------------------------------------------- maps --
func tallyTable(x: float, y: float, w: float, title: String, rec: Dictionary, name: Callable, note: String = "") -> float:
	var ui := UI.ui
	y = heading(x, y, w, title, note)
	var keys: Array = rec.keys()
	JS.sort_by_cmp(keys, func(p, q): return rec[q].played - rec[p].played)
	var K := {"name": x + 16, "played": x + w * 0.36, "wl": x + w * 0.4, "rate": x + w * 0.48, "avg": x + w * 0.76, "win": x + w * 0.86, "fast": x + w - 16}
	var ry := table(x, y, w, [["", K.name], ["Games", K.played, "right"], ["W–L", K.wl], ["Win rate", K.rate], ["Avg game", K.avg, "right"], ["Avg win", K.win, "right"], ["Fastest", K.fast, "right"]], maxi(1, keys.size()))
	if keys.is_empty():
		ui.text("No games yet.", K.name, ry + 21, {"size": 13, "color": FAINT})
	for k in keys:
		var t: Dictionary = rec[k]
		ui.text(name.call(k), K.name, ry + 21, {"size": 13.5, "bold": true, "color": TEXT})
		ui.text(JS.num(t.played), K.played, ry + 21, {"align": "right", "size": 13, "color": DIM})
		ui.text("%s–%s" % [JS.num(t.won), JS.num(t.played - t.won)], K.wl, ry + 21, {"size": 13, "color": DIM})
		rateBar(K.rate, ry + 19, w * 0.18, Career.winRate(t), t.played)
		ui.text(mmss(Career.avgSecs(t)), K.avg, ry + 21, {"align": "right", "size": 13, "color": TEXT})
		ui.text(mmss(Career.avgWinSecs(t)), K.win, ry + 21, {"align": "right", "size": 13, "color": GOOD if t.won else FAINT})
		ui.text(mmss(t.fastestWin), K.fast, ry + 21, {"align": "right", "size": 13, "color": TEXT})
		ry += 34
	return ry + 20


func maps(c: Dictionary, x: float, y: float, w: float) -> float:
	y = tallyTable(x, y, w, "Maps", c.byMap, func(k): return k, "most played first")
	y = tallyTable(x, y, w, "Formats", c.byFormat, func(k): return k)
	var MODES := {"conquest": "Conquest", "survival": "Survival", "koth": "King of the Hill", "regicide": "Regicide"}
	y = tallyTable(x, y, w, "Modes", c.byMode, func(k): return MODES.get(k, k))
	y = tallyTable(x, y, w, "Commanders", c.byCommander, func(k): return cmdName(k), "skirmish")
	y = tallyTable(x, y, w, "Oaths sworn", c.byOath, func(k): return C.OATHS[k].name if C.OATHS.has(k) else k, "a game counts for every Oath sworn in it")
	return y


# ---------------------------------------------------------------- units --
func units(c: Dictionary, x: float, y: float, w: float, time: float) -> float:
	var ui := UI.ui
	var cu: Dictionary = c.units
	var ids: Array = cu.keys().filter(func(u): return C.UNITS.has(u))
	JS.sort_by_cmp(ids, func(p, q):
		var d := _n(cu[q], "trained") - _n(cu[p], "trained")
		return d if d != 0 else _n(cu[q], "kills") - _n(cu[p], "kills"))
	var gap := 12.0
	var cols := 4 if w >= 1100 else 2
	var tw := (w - gap * (cols - 1)) / cols
	var best := Career.mostEffectiveUnit(c)
	var topT := Career.topUnit(c, "trained")
	var topK := Career.topUnit(c, "kills")
	var topL := Career.topUnit(c, "lost")
	var tops := [
		["Trained most", topT, ("%s trained" % num(cu[topT].trained)) if cu.has(topT) else "", "Your go-to soldier."],
		["Deadliest", topK, ("%s kills · %s damage" % [num(_n(cu[topK], "kills")), num(_n(cu[topK], "damage"))]) if cu.has(topK) else "", "More enemies fall to it than to anything else you field."],
		["Best trader", best, ("%s kills per loss" % JS.to_fixed(Career.unitKd(cu[best]), 2)) if best else "", "Highest K/D of any unit you've trained 10+ of."],
		["You lose most", topL, ("%s lost" % num(cu[topL].lost)) if cu.has(topL) else "", "Where your army bleeds."],
	]
	for i in tops.size():
		var tp: Array = tops[i]
		var id: String = tp[1]
		var value: String = tp[2]
		var tx := x + (i % cols) * (tw + gap)
		var ty := y + floori(float(i) / cols) * 130
		ui.panel(tx, ty, tw, 120)
		ui.text(String(tp[0]).to_upper(), tx + 14, ty + 22, {"size": 10.5, "bold": true, "color": DIM})
		fitText(unitName(id) if id else "—", tx + 14, ty + 50, tw - 124, {"size": 18, "bold": true, "color": "#fff0cc", "font": "Georgia, serif"})
		fitText(value if value else "not enough games yet", tx + 14, ty + 72, tw - 124, {"size": 12, "color": GOLD if value else FAINT})
		wrapLines(tp[3], tx + 14, ty + 92, tw - 120, 10.5, FAINT)
		if id:
			unitArt(id, {"x": tx + tw - 100, "y": ty + 8, "w": 92, "h": 104}, time)
	y += ceilf(float(tops.size()) / cols) * 130 + 4

	y = heading(x, y, w, "Every unit", "K/D = enemies it killed per one of yours lost")
	var maxT := 1.0
	for u in ids:
		maxT = maxf(maxT, _n(cu[u], "trained"))
	var K := {"name": x + 64, "tr": x + w * 0.24, "lost": x + w * 0.4, "kills": x + w * 0.48, "kd": x + w * 0.56, "dmg": x + w * 0.65, "razed": x + w * 0.73, "win": x + w * 0.8, "killed": x + w - 16}
	var ry := table(x, y, w, [["Unit", K.name], ["Trained", K.tr], ["Lost", K.lost, "right"], ["Kills", K.kills, "right"], ["K/D", K.kd, "right"], ["Damage", K.dmg, "right"], ["Razed", K.razed, "right"], ["Win % when used", K.win], ["Enemy of type killed", K.killed, "right"]], maxi(1, ids.size()))
	if ids.is_empty():
		ui.text("No units yet.", K.name, ry + 21, {"size": 13, "color": FAINT})
	for u in ids:
		var v: Dictionary = cu[u]
		var vill: bool = u == "villager"
		unitArt(u, {"x": x + 8, "y": ry, "w": 48, "h": 32}, time)
		fitText(unitName(u), K.name, ry + 21, K.tr - K.name - 10, {"size": 13.5, "bold": true, "color": TEXT})
		ui.bar(K.tr, ry + 14, w * 0.1, 8, _n(v, "trained") / maxT, GOLD)
		ui.text(num(_n(v, "trained")), K.tr + w * 0.1 + 8, ry + 21, {"size": 12.5, "color": TEXT})
		ui.text(num(_n(v, "lost")), K.lost, ry + 21, {"align": "right", "size": 12.5, "color": BAD if _n(v, "lost") else FAINT})
		ui.text(num(_n(v, "kills")), K.kills, ry + 21, {"align": "right", "size": 12.5, "color": GOOD if _n(v, "kills") else FAINT})
		var k := Career.unitKd(v)
		var noKd: bool = vill or not (_n(v, "kills") or _n(v, "lost"))
		ui.text("—" if noKd else JS.to_fixed(k, 2), K.kd, ry + 21, {"align": "right", "size": 12.5, "bold": true, "color": FAINT if noKd else (GOOD if k >= 1.2 else (BAD if k < 0.8 else GOLD))})
		ui.text(num(_n(v, "damage")) if _n(v, "damage") else "—", K.dmg, ry + 21, {"align": "right", "size": 12.5, "color": DIM})
		ui.text(num(_n(v, "razed")) if _n(v, "razed") else "—", K.razed, ry + 21, {"align": "right", "size": 12.5, "color": DIM})
		if _n(v, "games"):
			rateBar(K.win, ry + 19, w * 0.1, _n(v, "wins") / _n(v, "games"), _n(v, "games"))
		else:
			ui.text("—", K.win, ry + 21, {"size": 12.5, "color": FAINT})
		ui.text(num(_n(v, "killed")), K.killed, ry + 21, {"align": "right", "size": 12.5, "color": DIM})
		ry += 34
	y = ry + 20
	var blds: Array = []
	for b in c.buildings:
		if C.BUILDINGS.has(b):
			blds.append([b, float(c.buildings[b])])
	JS.sort_by_cmp(blds, func(p, q): return q[1] - p[1])
	if blds.size():
		y = heading(x, y, w, "Buildings you raise", "all time")
		var bw := (w - gap * 3) / 4
		for i in blds.size():
			var bx := x + (i % 4) * (bw + gap)
			var by := y + floori(i / 4.0) * 40
			ui.panel(bx, by, bw, 34)
			ui.text(C.BUILDINGS[blds[i][0]].name, bx + 12, by + 22, {"size": 12.5, "color": TEXT})
			ui.text(num(blds[i][1]), bx + bw - 12, by + 22, {"align": "right", "size": 12.5, "bold": true, "color": GOLD})
		y += ceilf(blds.size() / 4.0) * 40 + 12
	return y


# ------------------------------------------------------------- playstyle --
func block(text: String, x: float, y: float, maxW: float, size: float, color, bold: bool = false) -> float:
	var ui := UI.ui
	var ctx := ui.ctx
	ctx.font = "%s%spx 'Trebuchet MS', sans-serif" % ["bold " if bold else "", JS.num(size)]
	var line := ""
	var yy := y
	for word in text.split(" "):
		var test := ("%s %s" % [line, word]) if line != "" else word
		if ctx.measureText(test).width > maxW and line != "":
			ui.text(line, x, yy, {"size": size, "color": color, "bold": bold})
			line = word
			yy += size + 5
		else:
			line = test
	if line != "":
		ui.text(line, x, yy, {"size": size, "color": color, "bold": bold})
		yy += size + 5
	return yy - y


## The height `block` would draw, without drawing.
func blockHeight(text: String, maxW: float, size: float) -> float:
	var ctx := UI.ui.ctx
	ctx.font = "%spx 'Trebuchet MS', sans-serif" % JS.num(size)
	var line := ""
	var rows := 0
	for word in text.split(" "):
		var test := ("%s %s" % [line, word]) if line != "" else word
		if ctx.measureText(test).width > maxW and line != "":
			rows += 1
			line = word
		else:
			line = test
	if line != "":
		rows += 1
	return rows * (size + 5)


## A drawn emblem per style (emoji render differently everywhere).
func medallion(id: String, cx: float, cy: float, r: float, color) -> void:
	var ctx := UI.ui.ctx
	ctx.save()
	ctx.translate(cx, cy)
	ctx.strokeStyle = color; ctx.fillStyle = color; ctx.lineWidth = maxf(2, r * 0.09); ctx.lineCap = "round"; ctx.lineJoin = "round"
	ctx.beginPath(); ctx.arc(0, 0, r, 0, PI * 2); ctx.globalAlpha = 0.14; ctx.fill(); ctx.globalAlpha = 1; ctx.stroke()
	var u := r / 10
	ctx.beginPath()
	match id:
		"rush": # crossed swords
			ctx.moveTo(-6 * u, -6 * u); ctx.lineTo(6 * u, 6 * u); ctx.moveTo(6 * u, -6 * u); ctx.lineTo(-6 * u, 6 * u)
			ctx.moveTo(-6.5 * u, 3 * u); ctx.lineTo(-3 * u, 6.5 * u); ctx.moveTo(6.5 * u, 3 * u); ctx.lineTo(3 * u, 6.5 * u); ctx.stroke()
		"pressure": # a sword, then an hourglass: hit early, finish late
			ctx.moveTo(-7 * u, 2 * u); ctx.lineTo(0, -5 * u); ctx.moveTo(-6 * u, -2 * u); ctx.lineTo(-3 * u, 1 * u)
			ctx.moveTo(1.5 * u, 0); ctx.lineTo(6.5 * u, 0); ctx.lineTo(1.5 * u, 7 * u); ctx.lineTo(6.5 * u, 7 * u); ctx.closePath(); ctx.stroke()
		"turtle": # a tower
			ctx.rect(-4 * u, -3 * u, 8 * u, 9 * u); ctx.moveTo(-5 * u, -3 * u); ctx.lineTo(-5 * u, -6 * u); ctx.lineTo(-2.5 * u, -6 * u); ctx.lineTo(-2.5 * u, -4.5 * u)
			ctx.lineTo(0, -4.5 * u); ctx.lineTo(0, -6 * u); ctx.lineTo(2.5 * u, -6 * u); ctx.lineTo(2.5 * u, -4.5 * u); ctx.lineTo(5 * u, -4.5 * u); ctx.lineTo(5 * u, -3 * u); ctx.stroke()
		"late": # hourglass
			ctx.moveTo(-4 * u, -6 * u); ctx.lineTo(4 * u, -6 * u); ctx.lineTo(-4 * u, 6 * u); ctx.lineTo(4 * u, 6 * u); ctx.closePath(); ctx.stroke()
		"boom": # a wheat sheaf
			for a in [-0.35, 0.0, 0.35]:
				ctx.save(); ctx.rotate(a); ctx.moveTo(0, 6 * u); ctx.lineTo(0, -5 * u); ctx.restore()
			ctx.stroke(); ctx.beginPath(); ctx.ellipse(0, -5 * u, 1.6 * u, 2.6 * u, 0, 0, PI * 2); ctx.fill()
		"demolition": # a flame
			ctx.moveTo(0, 6 * u); ctx.bezierCurveTo(-6 * u, 4 * u, -4 * u, -2 * u, 0, -7 * u); ctx.bezierCurveTo(1 * u, -2 * u, 5 * u, -1 * u, 4 * u, 3 * u); ctx.bezierCurveTo(3.5 * u, 5 * u, 2 * u, 6 * u, 0, 6 * u); ctx.stroke()
		"raid": # a horseshoe
			ctx.arc(0, -1 * u, 5 * u, PI * 0.85, PI * 2.15); ctx.moveTo(-4.3 * u, 2 * u); ctx.lineTo(-4.3 * u, 6 * u); ctx.moveTo(4.3 * u, 2 * u); ctx.lineTo(4.3 * u, 6 * u); ctx.stroke()
		"tech": # a scroll
			ctx.rect(-5 * u, -4 * u, 10 * u, 8 * u); ctx.moveTo(-3 * u, -1.5 * u); ctx.lineTo(3 * u, -1.5 * u); ctx.moveTo(-3 * u, 1.5 * u); ctx.lineTo(2 * u, 1.5 * u); ctx.stroke()
		"brawler": # a shield
			ctx.moveTo(0, -6 * u); ctx.lineTo(5 * u, -4 * u); ctx.lineTo(4 * u, 2 * u); ctx.lineTo(0, 6 * u); ctx.lineTo(-4 * u, 2 * u); ctx.lineTo(-5 * u, -4 * u); ctx.closePath(); ctx.stroke()
		"all": # scales
			ctx.moveTo(0, -6 * u); ctx.lineTo(0, 6 * u); ctx.moveTo(-6 * u, -3 * u); ctx.lineTo(6 * u, -3 * u); ctx.moveTo(-3 * u, 6 * u); ctx.lineTo(3 * u, 6 * u)
			ctx.moveTo(-6 * u, -3 * u); ctx.lineTo(-7.5 * u, 1.5 * u); ctx.lineTo(-4.5 * u, 1.5 * u); ctx.closePath(); ctx.moveTo(6 * u, -3 * u); ctx.lineTo(4.5 * u, 1.5 * u); ctx.lineTo(7.5 * u, 1.5 * u); ctx.closePath(); ctx.stroke()
		_: # a question
			ctx.arc(0, -2 * u, 3.5 * u, PI, PI * 2.4); ctx.lineTo(0, 3 * u); ctx.moveTo(0, 5.5 * u); ctx.lineTo(0, 5.6 * u); ctx.stroke()
	ctx.restore()


func playstyle(p: Dictionary, x: float, y: float, w: float, time: float) -> float:
	var ui := UI.ui
	var ctx := ui.ctx
	var f: Dictionary = p.features
	var gap := 12.0
	# ---- the headline ----
	var heroH := 200.0
	ui.panel(x, y, w, heroH)
	var primary = p.primary.archetype if p.primary != null else null
	var emX := x + w - 110
	var emY := y + heroH / 2
	ctx.fillStyle = "rgba(232,192,96,0.12)"
	ctx.beginPath(); ctx.arc(emX, emY, 70, 0, PI * 2); ctx.fill()
	ctx.strokeStyle = Palette.withAlpha(GOLD, 0.6); ctx.lineWidth = 2
	var pscore: float = p.primary.score if p.primary != null else 0.0
	ctx.beginPath(); ctx.arc(emX, emY, 70, -PI / 2, -PI / 2 + PI * 2 * pscore); ctx.stroke()
	medallion(primary.id if primary != null else ("all" if p.enough else "none"), emX, emY, 44, GOLD)
	ui.text(("%s%% match" % JS.num(JS.round(pscore * 100))) if p.primary != null else "", emX, emY + 62, {"align": "center", "size": 11, "color": DIM})
	ui.text("YOUR STYLE", x + 20, y + 26, {"size": 11, "bold": true, "color": DIM})
	fitText(p.title, x + 20, y + 62, w - 260, {"size": 32, "bold": true, "color": GOLD if p.primary != null else "#ffe9b0", "font": "Georgia, serif"})
	var CONF := {"none": "not enough games yet", "low": "early read", "medium": "solid read", "high": "confident read"}
	var conf: String = CONF[p.confidence]
	ui.text("Based on %s game%s · %s%s" % [JS.num(p.games), "" if p.games == 1 else "s", conf, (" · %s only" % filter) if filter != "all" else ""], x + 20, y + 90, {"size": 12, "color": FAINT})
	block(p.description, x + 20, y + 116, w - 260, 13.5, TEXT)
	y += heroH + gap
	if not p.enough:
		return y

	# ---- keywords: the style in words, built from the numbers ----
	if p.keywords.size():
		y = heading(x, y, w, "In a few words", "tempo · army · game length · what you stand out at")
		var cx := x
		var cy := y
		for k in p.keywords:
			ctx.font = "bold 13px 'Trebuchet MS', sans-serif"
			var cw := ctx.measureText(k).width + 28
			if cx + cw > x + w:
				cx = x
				cy += 36
			ctx.fillStyle = "rgba(127,176,232,0.16)"
			ctx.beginPath(); ctx.roundRect(cx, cy, cw, 30, 15); ctx.fill()
			ctx.strokeStyle = "rgba(127,176,232,0.45)"; ctx.lineWidth = 1
			ctx.beginPath(); ctx.roundRect(cx + 0.5, cy + 0.5, cw - 1, 29, 15); ctx.stroke()
			ui.text(k, cx + 14, cy + 20, {"size": 13, "bold": true, "color": "#cfe2ff"})
			cx += cw + 8
		y = cy + 44

	# ---- the verdict: what you do well, what's costing you ----
	if p.good.size() or p.bad.size():
		y = heading(x, y, w, "Your games, honestly", "compared with the opponents you've faced")
		var hw := (w - gap) / 2
		var measure := func(lines: Array) -> float:
			var a := 0.0
			for l in lines:
				a += blockHeight(l, hw - 52, 13) + 8
			return a
		var ph := maxf(80, 40 + maxf(measure.call(p.good), measure.call(p.bad)))
		var side := func(sx: float, title: String, lines: Array, color: String, mark: String, empty_: String) -> void:
			ui.panel(sx, y, hw, ph)
			ui.text(title, sx + 16, y + 22, {"size": 11, "bold": true, "color": color})
			var ly := y + 44
			if lines.is_empty():
				block(empty_, sx + 16, ly, hw - 32, 12.5, FAINT)
			for l in lines:
				ui.text(mark, sx + 16, ly + 1, {"size": 14, "bold": true, "color": color})
				ly += block(l, sx + 34, ly, hw - 52, 13, TEXT) + 8
		side.call(x, "WHAT YOU DO WELL", p.good, GOOD, "+", "Nothing stands out yet.")
		side.call(x + hw + gap, "WHAT'S COSTING YOU", p.bad, BAD, "−", "No weak spot shows in your numbers.")
		y += ph + gap

	# ---- traits ----
	if p.traits.size():
		y = heading(x, y, w, "Traits", "what else stands out")
		var cx := x
		var cy := y
		for t in p.traits:
			ctx.font = "bold 12.5px 'Trebuchet MS', sans-serif"
			var cw := ctx.measureText(t.name).width + 26
			if cx + cw > x + w:
				cx = x
				cy += 34
			var hov := ui.hit(cx, cy, cw, 28)
			ctx.fillStyle = "rgba(232,192,96,0.28)" if hov else "rgba(232,192,96,0.14)"
			ctx.beginPath(); ctx.roundRect(cx, cy, cw, 28, 14); ctx.fill()
			ui.text(t.name, cx + 13, cy + 19, {"size": 12.5, "bold": true, "color": GOLD})
			if hov:
				ui.tooltip([t.name, t.detail])
			cx += cw + 8
		y = cy + 42

	# ---- the evidence ----
	if primary != null:
		var nm: String = primary.name
		var article := "an" if nm.length() > 0 and "AEIOU".contains(nm.substr(0, 1)) else "a"
		y = heading(x, y, w, "Why you're %s %s" % [article, nm], "the numbers behind it")
		var ev: Array = primary.evidence.call(f)
		var ew := (w - gap * (ev.size() - 1)) / ev.size()
		for i in ev.size():
			var e: Dictionary = ev[i]
			var ex := x + i * (ew + gap)
			ui.panel(ex, y, ew, 78)
			fitText(e.label, ex + 14, y + 24, ew - 28, {"size": 12, "color": DIM})
			ui.text(e.value, ex + 14, y + 52, {"size": 22, "bold": true, "color": "#fff0cc", "font": "Georgia, serif"})
			ui.bar(ex + ew * 0.45, y + 44, ew * 0.55 - 14, 8, e.weight, GOOD if e.weight >= 0.7 else (GOLD if e.weight >= 0.35 else FAINT))
		y += 90
		var cw := (w - gap * 2) / 3
		var col := func(i: int, title: String, lines: Array, color: String) -> float:
			var cx := x + i * (cw + gap)
			var yy := y + 26
			ui.text(title, cx + 14, yy, {"size": 11, "bold": true, "color": color})
			yy += 20
			for l in lines:
				yy += block("• %s" % l, cx + 14, yy, cw - 28, 12.5, TEXT) + 2
			return yy - y
		var measureH := maxf(90, 50 + maxi(primary.strengths.size(), primary.risks.size()) * 36)
		for i in 3:
			ui.panel(x + i * (cw + gap), y, cw, measureH)
		col.call(0, "STRENGTHS", primary.strengths, GOOD)
		col.call(1, "WATCH OUT FOR", primary.risks, BAD)
		col.call(2, "TO GET BETTER AT IT", [primary.tip], GOLD)
		y += measureH + gap

	# ---- how you win ----
	y = heading(x, y, w, "How you win", "%s wins · average %s" % [JS.num(f.wins), mmss(f.avgWinSec)])
	var half := (w - gap) / 2
	ui.panel(x, y, half, 170)
	var buckets := [["< 15 min", f.winsBefore15], ["15–30", f.wins15to30], ["30–40", f.wins30to40], ["40+ min", f.winsAfter40]]
	var bw := (half - 60) / 4
	for i in buckets.size():
		var v: float = buckets[i][1]
		var bx := x + 30 + i * bw
		var bh := 100 * v
		ctx.fillStyle = "#e0786a" if i == 0 else ("#5b8fe0" if i == 3 else GOLD)
		ctx.fillRect(bx + 8, y + 130 - bh, bw - 16, bh)
		ui.text(pct(v), bx + bw / 2, y + 124 - bh, {"align": "center", "size": 12, "bold": true, "color": TEXT})
		ui.text(buckets[i][0], bx + bw / 2, y + 150, {"align": "center", "size": 11, "color": DIM})
	ui.text("WHEN YOUR WINS COME", x + 16, y + 20, {"size": 10.5, "bold": true, "color": DIM})
	# What your wins have that your losses don't.
	ui.panel(x + half + gap, y, half, 170)
	ui.text("WHAT YOUR WINS HAVE THAT YOUR LOSSES DON'T", x + half + gap + 16, y + 20, {"size": 10.5, "bold": true, "color": DIM})
	var ky := y + 44
	if p.winKeys.is_empty():
		block("Needs at least three wins and three losses to compare.", x + half + gap + 16, ky, half - 32, 12.5, FAINT)
	for k in p.winKeys:
		ui.text("▲" if k.change > 0 else "▼", x + half + gap + 16, ky, {"size": 12, "color": GOOD if k.change > 0 else "#7fb0e8"})
		ky += block(k.sentence, x + half + gap + 34, ky, half - 52, 12.5, TEXT) + 4
	y += 182
	var facts := [
		["Median first attack", mmss(f.medianFirstHit)], ["Attack before 7:00", pct(f.rushRate)],
		["Losses inside 15 min", pct(f.earlyLossShare)], ["Win rate in 30+ min games", pct(f.lateWinRate) if f.lateShare else "—"],
	]
	var fw2 := (w - gap * 3) / 4
	for i in facts.size():
		var fx := x + i * (fw2 + gap)
		ui.panel(fx, y, fw2, 56)
		fitText(facts[i][0], fx + 14, y + 22, fw2 - 28, {"size": 11.5, "color": DIM})
		ui.text(facts[i][1], fx + 14, y + 44, {"size": 16, "bold": true, "color": "#fff0cc"})
	y += 68

	# ---- your army ----
	y = heading(x, y, w, "Your army", "what you train, and what you spend on")
	ui.panel(x, y, w, 118)
	var classes := [["infantry", "Infantry", "#c8a060"], ["archer", "Archers", "#8fd07a"], ["cavalry", "Cavalry", "#7fb0e8"], ["siege", "Siege", "#e0786a"], ["support", "Support", "#c8b8e8"]]
	var sx := x + 16
	var barW := w - 32
	for cl in classes:
		var v := _n(f.classShare, cl[0])
		if not v:
			continue
		ctx.fillStyle = cl[2]; ctx.fillRect(sx, y + 18, barW * v, 18)
		sx += barW * v
	var lx := x + 16
	for cl in classes:
		var v := _n(f.classShare, cl[0])
		if not v:
			continue
		ctx.fillStyle = cl[2]; ctx.fillRect(lx, y + 46, 10, 10)
		var t := "%s %s" % [cl[1], pct(v)]
		ui.text(t, lx + 14, y + 55, {"size": 12, "color": TEXT})
		ctx.font = "12px 'Trebuchet MS', sans-serif"
		lx += ctx.measureText(t).width + 30
	fitText("Top unit: %s (%s of your soldiers)  ·  variety: %s unit types' worth" % [unitName(f.topUnit) if f.topUnit else "—", pct(f.topUnitShare), JS.to_fixed(f.diversity, 1)], x + 16, y + 80, w - 32, {"size": 12.5, "color": DIM})
	fitText("Spending: %s army · %s buildings · %s technology" % [pct(f.spendUnits), pct(f.spendBuildings), pct(f.spendTech)], x + 16, y + 102, w - 32, {"size": 12.5, "color": DIM})
	y += 130

	# ---- you vs them ----
	y = heading(x, y, w, "You vs your opponents", "per game, against the average enemy you faced")
	var rows := [
		["Income", f.gatherRatio, JS.num(JS.round(f.gatherPerMin)) + " a minute"],
		["Peak villagers", f.villagerRatio, JS.num(JS.round(f.peakVillagers))],
		["Buildings razed", f.razedRatio, JS.to_fixed(f.razedPerGame, 1)],
		["Villagers killed", f.raidRatio, JS.to_fixed(f.raidPerGame, 1)],
		["Defence built", f.defenseRatio, JS.to_fixed(f.defensesPerGame, 1)],
		["Research", f.upgradeRatio, JS.to_fixed(f.upgradesPerGame, 1)],
		["Kills per loss", f.kd / f.foeKd if f.foeKd > 0 else 1.0, "%s (them %s)" % [JS.to_fixed(f.kd, 2), JS.to_fixed(f.foeKd, 2)]],
	]
	ui.panel(x, y, w, rows.size() * 30 + 20)
	for i in rows.size():
		var label: String = rows[i][0]
		var r: float = rows[i][1]
		var you: String = rows[i][2]
		var ry := y + 26 + i * 30
		fitText(label, x + 16, ry, 150, {"size": 13, "color": TEXT, "bold": true})
		var mid := x + w * 0.45
		var span := w * 0.22
		ctx.fillStyle = "rgba(255,255,255,0.08)"; ctx.fillRect(mid - span, ry - 10, span * 2, 10)
		ctx.fillStyle = "rgba(255,255,255,0.35)"; ctx.fillRect(mid, ry - 13, 1, 16)
		var lg := maxf(-1, minf(1, (log(maxf(0.01, r)) / log(2.0)) / 1.5))
		ctx.fillStyle = GOOD if lg >= 0 else BAD
		ctx.fillRect(mid if lg >= 0 else mid + lg * span, ry - 10, absf(lg) * span, 10)
		ui.text("%s×" % JS.to_fixed(r, 2), mid + span + 12, ry, {"size": 12.5, "bold": true, "color": GOOD if r >= 1.1 else (BAD if r <= 0.9 else GOLD)})
		fitText("you: %s" % you, x + w * 0.76, ry, w * 0.24 - 16, {"size": 12, "color": DIM})
	y += rows.size() * 30 + 32

	# ---- game by game ----
	y = heading(x, y, w, "Game by game", "each match's own style — hover for the rule")
	ui.panel(x, y, w, p.matchMix.size() * 30 + 20)
	for i in p.matchMix.size():
		var mm: Dictionary = p.matchMix[i]
		var ry: float = y + 26 + i * 30
		var st: Dictionary = Playstyle.MATCH_STYLES[mm.style]
		var wr: float = float(mm.wins) / maxf(1, mm.games)
		ui.text(st.name, x + 16, ry, {"size": 13, "bold": true, "color": st.color})
		ui.bar(x + 140, ry - 9, w * 0.4, 10, mm.share, st.color)
		ui.text("%s of games" % pct(mm.share), x + 150 + w * 0.4, ry, {"size": 12, "color": TEXT})
		ui.text("won %s of %s (%s)" % [JS.num(mm.wins), JS.num(mm.games), pct(wr)], x + w - 16, ry, {"align": "right", "size": 12, "color": GOOD if wr >= 0.55 else (BAD if wr <= 0.45 else DIM)})
		if ui.hit(x, ry - 16, w, 26):
			ui.tooltip([st.name, st.rule])
	y += p.matchMix.size() * 30 + 32
	if p.byFaction.size():
		y = heading(x, y, w, "By faction", "how you most often play each one")
		ui.panel(x, y, w, p.byFaction.size() * 30 + 20)
		for i in p.byFaction.size():
			var b: Dictionary = p.byFaction[i]
			var ry: float = y + 26 + i * 30
			dot(x + 22, ry - 4, facColor(b.faction))
			fitText(facName(b.faction), x + 34, ry, 200, {"size": 13, "bold": true, "color": TEXT})
			var st: Dictionary = Playstyle.MATCH_STYLES[b.style]
			ui.text("%s in %s of %s games" % [st.name, pct(b.share), JS.num(b.games)], x + 260, ry, {"size": 12.5, "color": st.color})
			ui.text("%s won" % pct(b.winRate), x + w - 16, ry, {"align": "right", "size": 12.5, "color": GOOD if b.winRate >= 0.55 else (BAD if b.winRate <= 0.45 else DIM)})
		y += p.byFaction.size() * 30 + 32

	# ---- every style ----
	y = heading(x, y, w, "Every style", "how closely you match each one (35% or more counts)")
	for sc in p.scores:
		var a: Dictionary = sc.archetype
		var rowH := 64.0
		var isPrimary: bool = p.primary != null and sc == p.primary
		ui.panel(x, y, w, rowH)
		medallion(a.id, x + 30, y + rowH / 2, 18, GOLD if isPrimary else DIM)
		fitText(a.name, x + 60, y + 26, 190, {"size": 15, "bold": true, "color": GOLD if isPrimary else TEXT})
		ui.bar(x + 60, y + 38, 170, 7, sc.score, GOLD if sc.score >= 0.35 else FAINT)
		ui.text("%s%%" % JS.num(JS.round(sc.score * 100)), x + 238, y + 45, {"size": 11.5, "bold": true, "color": GOLD if sc.score >= 0.35 else FAINT})
		fitText(a.short, x + 290, y + 26, w - 306, {"size": 13, "color": TEXT})
		fitText(a.criteria, x + 290, y + 46, w - 306, {"size": 11.5, "color": DIM})
		y += rowH + 6
	return y + 6


# -------------------------------------------------------------- replays --
var notice := ""
var noticeBad := false


func setNotice(msg: String, bad: bool) -> void:
	notice = msg
	noticeBad = bad


## Open a replay file from the player's computer; it's kept, then played.
func openReplayFile() -> void:
	_pickTextFile("%s,.json,application/json" % Replay.REPLAY_FILE_EXT, func(f):
		if f == null:
			return
		var res := Replay.parseReplayFile(f.text)
		if not res.ok:
			notice = res.error
			noticeBad = true
			return
		Replay.saveReplay(res.replay)
		var warning = res.get("warning")
		notice = warning if warning else "Opened %s." % f.name
		noticeBad = bool(warning)
		watch = res.replay)


func replays(x: float, y: float, w: float) -> float:
	var ui := UI.ui
	var EXT: String = Replay.REPLAY_FILE_EXT
	var list := Replay.listReplays()
	y = heading(x, y, w, "Replays", "your last %d match%s — watch any of them with the full caster view" % [list.size(), "" if list.size() == 1 else "es"])
	# Opening one from a file (or dropping it onto the game).
	ui.panel(x, y, w, 58)
	if ui.button("📂  Open a replay file…", x + 14, y + 11, 220, 36, {"accent": true, "size": 14, "tooltip": ["Open a replay file", "A %s file downloaded from this game — yours, a friend's, a tournament's." % EXT, "You can also drag the file onto the game."]}):
		openReplayFile()
	fitText(notice if notice != "" else "Download any replay below to keep it or share it — anyone with the game can open it and watch it with the caster view. Or drag a %s file onto the game." % EXT,
		x + 250, y + 34, w - 266, {"size": 12.5, "color": (BAD if noticeBad else GOOD) if notice != "" else DIM})
	y += 70
	if list.is_empty():
		ui.panel(x, y, w, 90)
		ui.text("No replays yet — every match you play or watch is recorded here automatically.", x + w / 2, y + 50, {"align": "center", "size": 13.5, "color": DIM})
		return y + 100
	for r in list:
		ui.panel(x, y, w, 64)
		var d := localDate(r.savedAt)
		fitText(r.summary.map, x + 16, y + 26, 300, {"size": 15, "bold": true, "color": "#ffe9b0"})
		if r.get("imported"):
			ui.text("FROM FILE", x + 16 + minf(300, ui.ctx.measureText(r.summary.map).width + 10), y + 25, {"size": 9.5, "bold": true, "color": "#7fb0e8"})
		var kind := "Online" if r.kind == "online" else ("AI game watched" if r.kind == "watch" else "Skirmish")
		fitText("%s · %s players · %s · %s %s:%s" % [kind, JS.num(r.summary.players), mmss(r.summary.durationSec), localeDate(r.savedAt), str(d.hour).lpad(2, "0"), str(d.minute).lpad(2, "0")], x + 16, y + 46, 330, {"size": 12, "color": DIM})
		var facs: Array = r.summary.factions
		for i in facs.size():
			dot(x + 360 + i * 16, y + 22, facColor(facs[i]))
		var names: Array = (r.names as Array).filter(func(s): return s != null and s != "").slice(0, 4)
		fitText(" · ".join(PackedStringArray(names)), x + 360, y + 46, w - 360 - 470, {"size": 11.5, "color": FAINT})
		fitText(r.summary.result, x + w - 350, y + 36, 110, {"align": "right", "size": 13, "bold": true, "color": DIM if r.summary.result == "Unfinished" else GOLD})
		if ui.button("▶ Watch", x + w - 330, y + 14, 100, 36, {"accent": true, "size": 14, "tooltip": ["Watch this match", "With the caster view: any player's vision, graphs, the feed, and a timeline you can scrub."]}):
			watch = r
		if ui.button("⬇ Download", x + w - 222, y + 14, 106, 36, {"size": 13, "tooltip": ["Download the replay", "Saves a %s file — a few kilobytes. Anyone with the game can open it and watch." % EXT]}):
			var file := Replay.replayFile(r)
			notice = ("Downloaded %s" % file.name) if _downloadText(file.name, file.text) else "Your browser blocked the download."
			noticeBad = false
		var armed: bool = confirmDelete == r.id
		if ui.button("Sure?" if armed else "Delete", x + w - 108, y + 14, 94, 36, {"size": 13, "danger": armed}):
			if armed:
				Replay.deleteReplay(r.id)
				confirmDelete = ""
			else:
				confirmDelete = r.id
		y += 72
	return y


# -------------------------------------------------------------- matches --
func matchList(ms: Array, x: float, y: float, w: float) -> float:
	var ui := UI.ui
	y = heading(x, y, w, "Your matches", "%d kept · newest first" % ms.size())
	var list := ms.slice(0, 150)
	var K := {"when": x + 16, "kind": x + 110, "map": x + 240, "you": x + 400, "vs": x + 560, "res": x + w * 0.74, "len": x + w * 0.82, "kl": x + w * 0.9, "age": x + w - 16}
	var ry := table(x, y, w, [["When", K.when], ["Game", K.kind], ["Map", K.map], ["You", K.you], ["Against", K.vs], ["Result", K.res], ["Length", K.len, "right"], ["K : L", K.kl, "right"], ["Age", K.age, "right"]], maxi(1, list.size()))
	for m in list:
		var d := localDate(m.at)
		ui.text("%d/%d %s:%s" % [d.day, d.month, str(d.hour).lpad(2, "0"), str(d.minute).lpad(2, "0")], K.when, ry + 21, {"size": 12, "color": DIM})
		ui.text((("Ranked" if m.get("ranked") else "Online") if m.kind == "online" else diffName(m.difficulty)), K.kind, ry + 21, {"size": 12.5, "bold": true, "color": "#7fb0e8" if m.kind == "online" else TEXT})
		ui.text(m.format, K.kind + 70, ry + 21, {"size": 11, "color": FAINT})
		ui.text(m.map, K.map, ry + 21, {"size": 12.5, "color": TEXT})
		dot(K.you + 6, ry + 17, facColor(m.faction))
		ui.text(_strip_the(facName(m.faction)), K.you + 16, ry + 21, {"size": 12.5, "color": TEXT})
		var foes: Array = m.foes
		if foes.size() == 1:
			dot(K.vs + 6, ry + 17, facColor(foes[0]))
			ui.text(_strip_the(facName(foes[0])), K.vs + 16, ry + 21, {"size": 12.5, "color": DIM})
		else:
			var shownFoes := foes.slice(0, 7)
			for i in shownFoes.size():
				dot(K.vs + 6 + i * 14, ry + 17, facColor(shownFoes[i]))
		var allies: Array = m.allies
		if allies.size():
			ui.text("+%d %s" % [allies.size(), "ally" if allies.size() == 1 else "allies"], K.vs + 120, ry + 21, {"size": 11, "color": FAINT})
		if foes.is_empty():
			ui.text("—", K.vs, ry + 21, {"size": 12, "color": FAINT})
		ui.text("Victory" if m.won else "Defeat", K.res, ry + 21, {"size": 13, "bold": true, "color": GOOD if m.won else BAD})
		ui.text(mmss(m.durationSec), K.len, ry + 21, {"align": "right", "size": 12.5, "color": TEXT})
		ui.text("%s : %s" % [JS.num(m.kills), JS.num(m.losses)], K.kl, ry + 21, {"align": "right", "size": 12.5, "color": DIM})
		var age := int(m.age)
		ui.text(C.AGES[age].short if age >= 0 and age < C.AGES.size() else JS.num(m.age), K.age, ry + 21, {"align": "right", "size": 12.5, "color": DIM})
		if ui.hit(x, ry, w, 34):
			var trained: Array = []
			for u in m.trained:
				if u != "villager":
					trained.append([u, float(m.trained[u])])
			JS.sort_by_cmp(trained, func(p, q): return q[1] - p[1])
			var fav = trained[0] if trained.size() else null
			var oaths: Array = m.oaths
			ui.tooltip(["%s on %s — %s" % ["Victory" if m.won else "Defeat", m.map, Playstyle.MATCH_STYLES[Playstyle.matchStyle(m)].name],
				"%s%s%s" % [facName(m.faction), (" led by %s" % cmdName(m.commander)) if m.get("commander") else "", (" · Oaths: %s" % ", ".join(PackedStringArray(oaths.map(func(o): return oathName(o))))) if oaths.size() else ""],
				"Against: %s%s" % [(", ".join(PackedStringArray(foes.map(func(f): return facName(f))))) if foes.size() else "—", (" · with %s" % ", ".join(PackedStringArray(allies.map(func(f): return facName(f))))) if allies.size() else ""],
				"Gathered %s · peak army %s · razed %s" % [num(m.gathered), JS.num(m.peakArmy), JS.num(m.razed)],
				("Most trained: %s ×%s" % [unitName(fav[0]), JS.num(fav[1])]) if fav != null else "No soldiers trained"])
		ry += 34
	if list.is_empty():
		ui.text("No matches yet.", x + 16, ry + 21, {"size": 13, "color": FAINT})
	return ry + 20


# ----------------------------------------------------------------- files --
# ui/files.ts (a browser download / file picker) belongs to the platform layer;
# if its port exists it is used, otherwise these do the same job in Godot: a
# download on the web, a file in the Downloads folder elsewhere, and a native
# file dialog to open one.

static func _filesModule():
	if ResourceLoader.exists("res://src/ui/files.gd"):
		return load("res://src/ui/files.gd")
	return null


## Offer `text` to the player as a file called `name`.
static func _downloadText(name: String, text: String) -> bool:
	var m = _filesModule()
	if m != null and m.has_method("downloadText"):
		return m.downloadText(name, text)
	if OS.has_feature("web"):
		JavaScriptBridge.download_buffer(text.to_utf8_buffer(), name, "application/json")
		return true
	var dir := OS.get_system_dir(OS.SYSTEM_DIR_DOWNLOADS)
	if dir == "":
		dir = OS.get_user_data_dir()
	var f := FileAccess.open(dir.path_join(name), FileAccess.WRITE)
	if f == null:
		return false
	f.store_string(text)
	f.close()
	return true


## Ask the player to choose a file; `done` is called with { name, text } or
## null if they cancel.
static func _pickTextFile(accept: String, done: Callable) -> void:
	var m = _filesModule()
	if m != null and m.has_method("pickTextFile"):
		m.pickTextFile(accept, done)
		return
	var tree := Engine.get_main_loop() as SceneTree
	if tree == null:
		done.call(null)
		return
	var dlg := FileDialog.new()
	dlg.file_mode = FileDialog.FILE_MODE_OPEN_FILE
	dlg.access = FileDialog.ACCESS_FILESYSTEM
	dlg.use_native_dialog = true
	var filters := PackedStringArray()
	for a in accept.split(","):
		if a.begins_with("."):
			filters.append("*" + a)
	dlg.filters = filters
	var finished := [false]
	dlg.file_selected.connect(func(path: String):
		finished[0] = true
		var text := FileAccess.get_file_as_string(path)
		dlg.queue_free()
		done.call({"name": path.get_file(), "text": text}))
	dlg.canceled.connect(func():
		if not finished[0]:
			finished[0] = true
			dlg.queue_free()
			done.call(null))
	tree.root.add_child(dlg)
	dlg.popup_centered_ratio(0.6)
