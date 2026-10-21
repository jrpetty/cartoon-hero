## ui/match_report.ts — the end-of-match report.
##
## A duel is drawn as a set of *contests*: both numbers at the ends of a single
## bar whose split is the ratio between them, so who was ahead — and by how
## much — reads without arithmetic.
##
## Anything bigger is drawn as *standings*: one row per realm, grouped by team
## in a team game and ranked in a free-for-all. The report used to fold every
## match into "you against them", which is right for a duel and wrong for
## everything else: in a four-way free-for-all it added three opponents who
## were fighting *each other* into one "Opponent" and called it 1v3, and in a
## 2v2 it hid whether you or your ally carried the game.
##
## Four tabs either way, because thirty rows on one screen is a spreadsheet.
##
## Reports are the Dictionaries sim/metrics.gd builds (TS MatchReport /
## PlayerReport / SideReport shapes). A ReportTab is its id string.
class_name MatchReport
extends RefCounted

const REPORT_TABS := [
	{"id": "overview", "label": "Overview"},
	{"id": "feedback", "label": "Feedback"},
	{"id": "economy", "label": "Economy"},
	{"id": "military", "label": "Military"},
	{"id": "units", "label": "Armies"},
]

# Type colours. Everything the eye has to *read* is at least 11.5px and well
# clear of the panel in contrast; the first version put column headers at
# 9.5px in a grey barely lighter than the background.
const INK := "#f1e8d2"
const LABEL := "#cfc4a8"
const MUTED := "#aaa08a"
const FAINT := "#6f6a5c"
const BEST := "#ffd86a"
const LOST := "#e39a8e"

const YOU := "#7fb0e8"
const FOE := "#e0786a"
# The duel's two colours. Set from the realms' own colours when the report
# knows them, so the bars match what you saw on the battlefield.
static var youCol: String = YOU
static var foeCol: String = FOE

const FOOD := "#e59a5c"
const WOOD := "#b98a55"
const GOLD := "#f0cf5a"
const ARMY := "#e0786a"
const BUILT := "#c8a86a"
const TECH := "#9b7cf0"
const BANKED := "rgba(255,255,255,0.18)"

const BEST_NOTE := "Gold marks the best in each column — for losses, damage taken and idle time, that is the lowest."


static func num(n: float) -> String:
	return JS.locale_int(n)


## Building-seconds read better as minutes once they get big.
static func secs(n: float) -> String:
	if n >= 120:
		return "%sm %ss" % [JS.num(floorf(n / 60.0)), JS.num(JS.round(fmod(n, 60.0)))]
	return "%ss" % JS.num(JS.round(n))


## Idle time as a share of the time the thing existed at all.
static func idleShare(idle: float, standing: float) -> String:
	return ("%s%%" % JS.num(JS.round((idle / standing) * 100.0))) if standing > 0 else "—"


static func mmss(s: float) -> String:
	return "%s:%s" % [JS.num(floorf(s / 60.0)), JS.num(floorf(fmod(s, 60.0))).lpad(2, "0")]


static func ordinal(n: int) -> String:
	var t := n % 100
	if t >= 11 and t <= 13:
		return "%dth" % n
	var suf := ["th", "st", "nd", "rd"]
	return "%d%s" % [n, suf[n % 10] if n % 10 < 4 else "th"]


static func _n(d: Dictionary, k: String) -> float:
	var v = d.get(k)
	return float(v) if v != null else 0.0


# ------------------------------------------------------------ who played --

## A realm's name, as the scoreboard and the battlefield colours give it.
static func playerName(p: Dictionary) -> String:
	return "The Horde" if p.get("horde", false) else Palette.teamColor(int(p.team)).name


## Colour a realm is drawn in: its own, lightened a touch for text on dark.
static func nameColour(p: Dictionary):
	return "#c9c2b0" if p.get("horde", false) else Palette.teamColor(int(p.team)).light


static func barColour(p: Dictionary):
	return "#8d8779" if p.get("horde", false) else Palette.teamColor(int(p.team)).main


## True when the report should be drawn as standings rather than a duel: more
## than two realms, or anyone fighting alongside you.
static func isStandings(r: Dictionary) -> bool:
	var ps = r.get("players")
	return ps != null and ((ps as Array).size() > 2 or (ps as Array).any(func(p): return p.relation == "ally"))


## Realms in the order the report lists them. Winners first; then whoever was
## still standing, by score; then the fallen, the last to fall first — which in
## a free-for-all is the finishing order. Returns { groups: [{ id, members,
## won }], ffa }.
static func standings(players: Array) -> Dictionary:
	var byId := {}
	for p in players:
		var gid := int(p.group)
		var g: Array = byId.get(gid, [])
		g.append(p)
		byId[gid] = g
	var groups: Array = []
	for id in byId:
		var members: Array = (byId[id] as Array).duplicate()
		JS.sort_by_cmp(members, func(a, b): return (float(b.score) - float(a.score)) if b.score != a.score else float(a.team) - float(b.team))
		groups.append({"id": id, "members": members, "won": members.any(func(m): return m.won)})
	var standing := func(g): return g.members.any(func(m): return not m.defeated)
	var fell := func(g):
		var mx := -INF
		for m in g.members:
			mx = maxf(mx, float(m.defeatedAt))
		return mx
	var total := func(g):
		var s := 0.0
		for m in g.members:
			s += float(m.score)
		return s
	JS.sort_by_cmp(groups, func(a, b):
		if a.won != b.won:
			return -1 if a.won else 1
		if standing.call(a) != standing.call(b):
			return -1 if standing.call(a) else 1
		if not standing.call(a) and fell.call(a) != fell.call(b):
			return fell.call(b) - fell.call(a)
		var d: float = total.call(b) - total.call(a)
		return d if d != 0 else float(a.id - b.id))
	return {"groups": groups, "ffa": groups.all(func(g): return g.members.size() == 1)}


# ---------------------------------------------------------------- duels --

## One contested row: label in the middle, values at the ends, and a split bar
## underneath. `higherIsBetter` only decides the colour of the winning number —
## for something like "resources left unspent" the bigger number is the worse
## one. opts: { format?: Callable(n) -> String, higherIsBetter?, hint? }
static func compareRow(x: float, y: float, w: float, label: String, a: float, b: float, opts: Dictionary = {}) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var fmt: Callable = opts.get("format", func(n): return num(n))
	var better: bool = opts.get("higherIsBetter", true)
	var aWins = null if a == b else ((a > b) if better else (a < b))
	ui.text(fmt.call(a), x, y, {"size": 16, "bold": true, "color": BEST if aWins == true else INK})
	ui.text(fmt.call(b), x + w, y, {"align": "right", "size": 16, "bold": true, "color": BEST if aWins == false else INK})
	ui.text(label, x + w / 2, y, {"align": "center", "size": 13.5, "color": LABEL})
	var hint = opts.get("hint")
	if hint:
		ui.text(hint, x + w / 2, y + 15, {"align": "center", "size": 11, "color": MUTED})

	# The split bar. A zero-zero row draws an even split rather than dividing by
	# nothing, which is what "neither of you did any of this" should look like.
	var total := a + b
	var frac := a / total if total > 0 else 0.5
	var by := y + (23 if hint else 11)
	ctx.fillStyle = "rgba(0,0,0,0.45)"
	ctx.beginPath(); ctx.roundRect(x, by, w, 7, 3.5); ctx.fill()
	ctx.save()
	ctx.beginPath(); ctx.roundRect(x, by, w, 7, 3.5); ctx.clip()
	ctx.fillStyle = youCol; ctx.fillRect(x, by, w * frac, 7)
	ctx.fillStyle = foeCol; ctx.fillRect(x + w * frac, by, w * (1 - frac), 7)
	ctx.restore()
	# A tick at the midpoint, so "just ahead" is distinguishable from "even".
	ctx.fillStyle = "rgba(255,255,255,0.45)"
	ctx.fillRect(x + w / 2 - 0.5, by - 2, 1, 11)


## Section heading inside a report tab.
static func heading(x: float, y: float, w: float, text: String, lineEnd = null) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var le: float = x + w if lineEnd == null else float(lineEnd)
	ui.text(text, x, y, {"size": 13, "bold": true, "color": C.PAL.uiAccent})
	ctx.font = "bold 13px \"Trebuchet MS\", sans-serif"
	var tw := ctx.measureText(text).width
	if le <= x + tw + 24:
		return
	ctx.strokeStyle = Palette.withAlpha(C.PAL.uiAccent, 0.25)
	ctx.lineWidth = 1
	ctx.beginPath()
	ctx.moveTo(x + tw + 12, y); ctx.lineTo(le, y)
	ctx.stroke()


static func _unitName(t: String) -> String:
	var d = C.UNITS.get(t)
	return d.name if d != null else t


static func _buildingName(t: String) -> String:
	var d = C.BUILDINGS.get(t)
	return d.name if d != null else t


## A per-type table: every unit either side trained, lost or killed. Sorted by
## how much of the match each type actually accounted for, so the comp that
## decided the game is at the top rather than whatever is first alphabetically.
static func unitTable(x: float, y: float, w: float, you: Dictionary, foe: Dictionary, rowsMax: int) -> float:
	var ui := UI.ui
	var ctx := ui.ctx
	var types := {}
	for src in [you.trainedByType, foe.trainedByType, you.lostByType, foe.lostByType]:
		for k in src:
			types[k] = true
	var weight := func(t): return _n(you.trainedByType, t) + _n(foe.trainedByType, t) + _n(you.lostByType, t) + _n(foe.lostByType, t)
	var rows: Array = types.keys().filter(func(t): return weight.call(t) > 0)
	JS.sort_by_cmp(rows, func(p, q): return weight.call(q) - weight.call(p))
	rows = rows.slice(0, rowsMax)

	var cols := [0, 0.32, 0.44, 0.56, 0.72, 0.84, 0.96]
	var cx := func(i: int) -> float: return x + w * cols[i]
	ui.text("UNIT", cx.call(0), y, {"size": 11.5, "bold": true, "color": LABEL})
	var heads := ["built", "lost", "killed"]
	for i in heads.size():
		ui.text(heads[i], cx.call(1 + i), y, {"align": "right", "size": 11.5, "bold": true, "color": youCol})
		ui.text(heads[i], cx.call(4 + i), y, {"align": "right", "size": 11.5, "bold": true, "color": foeCol})
	var ry := y + 20
	if rows.is_empty():
		ui.text("— no units were trained —", x, ry + 6, {"size": 12.5, "color": MUTED})
		return ry + 22
	for i in rows.size():
		var t: String = rows[i]
		if i % 2 == 0:
			ctx.fillStyle = "rgba(255,255,255,0.045)"
			ctx.fillRect(x - 6, ry - 11, w + 12, 22)
		ui.text(_unitName(t), cx.call(0), ry, {"size": 13, "color": INK})
		var cells := [_n(you.trainedByType, t), _n(you.lostByType, t), _n(you.get("killedByType", {}), t),
			_n(foe.trainedByType, t), _n(foe.lostByType, t), _n(foe.get("killedByType", {}), t)]
		for j in cells.size():
			var v: float = cells[j]
			ui.text(JS.num(v) if v else "·", cx.call(j + 1), ry, {"align": "right", "size": 13, "bold": v != 0, "color": INK if v else FAINT})
		ry += 22
	return ry


## How a side spent its match, as one stacked bar.
static func spendBar(x: float, y: float, w: float, side: Dictionary, label: String, colour) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var total := maxf(1, side.spentOn.units + side.spentOn.buildings + side.spentOn.tech + side.banked)
	var parts := [
		[float(side.spentOn.units), ARMY, "army"],
		[float(side.spentOn.buildings), BUILT, "buildings"],
		[float(side.spentOn.tech), TECH, "tech"],
		[float(side.banked), BANKED, "unspent"],
	]
	ui.text(label, x, y, {"size": 13, "bold": true, "color": colour})
	ui.text("%s gathered" % num(side.gathered), x + w, y, {"align": "right", "size": 12, "color": MUTED})
	var bx := x
	var by := y + 10
	ctx.save()
	ctx.beginPath(); ctx.roundRect(x, by, w, 14, 4); ctx.clip()
	ctx.fillStyle = "rgba(0,0,0,0.4)"; ctx.fillRect(x, by, w, 14)
	for p in parts:
		var pw: float = (p[0] / total) * w
		ctx.fillStyle = p[1]; ctx.fillRect(bx, by, pw, 14)
		bx += pw
	ctx.restore()
	# Legend under the bar, only naming the slices big enough to see.
	var lx := x
	ctx.font = "11.5px \"Trebuchet MS\", sans-serif"
	for p in parts:
		if p[0] / total < 0.04:
			continue
		var s := "%s %s" % [p[2], num(p[0])]
		ctx.fillStyle = p[1]
		ctx.fillRect(lx, y + 31, 8, 8)
		ui.text(s, lx + 12, y + 35, {"size": 11.5, "color": MUTED})
		ctx.font = "11.5px \"Trebuchet MS\", sans-serif"
		lx += 26 + ctx.measureText(s).width


static func _find(arr, pred: Callable):
	if arr == null:
		return null
	for v in arr:
		if pred.call(v):
			return v
	return null


static func drawDuelTab(tab: String, x: float, y: float, w: float, h: float, r: Dictionary) -> void:
	var you: Dictionary = r.you
	var foe: Dictionary = r.foe
	var ps = r.get("players")
	var duel = ps if ps != null and (ps as Array).size() == 2 else null
	var youP = _find(duel, func(p): return p.relation == "you")
	var foeP = _find(duel, func(p): return p.relation != "you")
	youCol = _hex(barColour(youP)) if youP != null else YOU
	foeCol = _hex(barColour(foeP)) if foeP != null else FOE
	var withOaths := func(name: String, p) -> String:
		var os: Array = oathsOf(p).map(func(o): return o.short)
		return ("%s · %s" % [name, ", ".join(PackedStringArray(os))]) if os.size() > 0 else name
	var facOf := func(p) -> String:
		if p != null and p.get("faction"):
			var f = C.FACTIONS.get(p.faction)
			return " · " + (_strip_the(f.name) if f != null else "")
		return ""
	var youName: String = ("%s (you)%s" % [playerName(youP), facOf.call(youP)]) if youP != null else "You"
	var foeName: String = ("%s%s" % [playerName(foeP), facOf.call(foeP)]) if foeP != null else "Opponent"
	# Rows breathe when there is room and close up when there isn't: ten of
	# them plus the spending bars have to fit a 680px-tall window too.
	var rowH := maxf(32, minf(42, floorf((h - 110) / 11.0)))
	var colGap := 32.0
	var half := (w - colGap) / 2

	if tab == "overview":
		heading(x, y, w, "THE MATCH")
		var ry := y + 30
		compareRow(x, ry, w, "Score", you.score, foe.score); ry += rowH
		compareRow(x, ry, w, "Resources gathered", you.gathered, foe.gathered); ry += rowH
		compareRow(x, ry, w, "Resources spent", you.spent, foe.spent); ry += rowH
		compareRow(x, ry, w, "Units killed", you.unitsKilled, foe.unitsKilled); ry += rowH
		compareRow(x, ry, w, "Units lost", you.unitsLost, foe.unitsLost, {"higherIsBetter": false}); ry += rowH
		compareRow(x, ry, w, "Buildings razed", you.buildingsRazed, foe.buildingsRazed); ry += rowH
		compareRow(x, ry, w, "Damage dealt", you.damageDealt, foe.damageDealt); ry += rowH
		compareRow(x, ry, w, "Peak army", you.peakArmy, foe.peakArmy); ry += rowH
		compareRow(x, ry, w, "Peak villagers", you.peakVillagers, foe.peakVillagers); ry += rowH
		compareRow(x, ry, w, "Technologies", you.upgrades, foe.upgrades); ry += rowH + 6
		heading(x, ry, w, "WHERE IT WENT")
		ry += 24
		spendBar(x, ry, half, you, withOaths.call(youName, youP), youCol)
		spendBar(x + half + colGap, ry, half, foe, withOaths.call(foeName, foeP), foeCol)
		return

	if tab == "economy":
		heading(x, y, w, "GATHERED")
		var ry := y + 30
		compareRow(x, ry, w, "Food", you.gatheredBy.food, foe.gatheredBy.food); ry += rowH
		compareRow(x, ry, w, "Wood", you.gatheredBy.wood, foe.gatheredBy.wood); ry += rowH
		compareRow(x, ry, w, "Gold", you.gatheredBy.gold, foe.gatheredBy.gold); ry += rowH + 6
		heading(x, ry, w, "SPENT")
		ry += 30
		compareRow(x, ry, w, "On the army", you.spentOn.units, foe.spentOn.units); ry += rowH
		compareRow(x, ry, w, "On buildings", you.spentOn.buildings, foe.spentOn.buildings); ry += rowH
		compareRow(x, ry, w, "On ages & tech", you.spentOn.tech, foe.spentOn.tech); ry += rowH
		compareRow(x, ry, w, "Left unspent", you.banked, foe.banked, {
			"higherIsBetter": false, "hint": "resources that sat in the bank doing nothing",
		}); ry += rowH + 14
		heading(x, ry, w, "WHAT STOOD STILL")
		ry += 30
		var secsFn := func(n): return secs(n)
		compareRow(x, ry, w, "Villager idle time", you.idleVillagerTime, foe.idleVillagerTime, {
			"higherIsBetter": false, "format": secsFn, "hint": "villager-seconds spent standing around",
		}); ry += rowH + 12
		# Idle production, as a share of the time those buildings were standing.
		# A raw total is unreadable: sixty seconds idle means one thing with a
		# single Town Centre and something much worse with three.
		compareRow(x, ry, w, "Town Centre idle", _n(you, "idleTcTime"), _n(foe, "idleTcTime"), {
			"higherIsBetter": false, "format": secsFn,
			"hint": "%s of the time it stood · %s for them" % [idleShare(_n(you, "idleTcTime"), _n(you, "tcSeconds")), idleShare(_n(foe, "idleTcTime"), _n(foe, "tcSeconds"))],
		}); ry += rowH + 12
		compareRow(x, ry, w, "Production idle", _n(you, "idleProductionTime"), _n(foe, "idleProductionTime"), {
			"higherIsBetter": false, "format": secsFn,
			"hint": "barracks, ranges, stables and the rest — %s vs %s" % [idleShare(_n(you, "idleProductionTime"), _n(you, "productionSeconds")), idleShare(_n(foe, "idleProductionTime"), _n(foe, "productionSeconds"))],
		})
		return

	if tab == "military":
		heading(x, y, w, "THE FIGHTING")
		var ry := y + 30
		compareRow(x, ry, w, "Units killed", you.unitsKilled, foe.unitsKilled); ry += rowH
		compareRow(x, ry, w, "Units lost", you.unitsLost, foe.unitsLost, {"higherIsBetter": false}); ry += rowH
		compareRow(x, ry, w, "Kill / loss ratio", kd(you), kd(foe), {"format": func(n): return JS.to_fixed(n, 2)}); ry += rowH
		compareRow(x, ry, w, "Damage dealt", you.damageDealt, foe.damageDealt); ry += rowH
		compareRow(x, ry, w, "Damage taken", you.damageTaken, foe.damageTaken, {"higherIsBetter": false}); ry += rowH + 6
		heading(x, ry, w, "THE WAR")
		ry += 30
		compareRow(x, ry, w, "Buildings razed", you.buildingsRazed, foe.buildingsRazed); ry += rowH
		compareRow(x, ry, w, "Buildings lost", you.buildingsLost, foe.buildingsLost, {"higherIsBetter": false}); ry += rowH
		compareRow(x, ry, w, "Peak army size", you.peakArmy, foe.peakArmy); ry += rowH
		compareRow(x, ry, w, "Technologies", you.upgrades, foe.upgrades); ry += rowH
		compareRow(x, ry, w, "Age reached", float(you.age) + 1, float(foe.age) + 1, {"format": func(n): return Tech.ageShort(int(n) - 1)})
		return

	# ---- armies ----
	var ui := UI.ui
	heading(x, y, w, "EVERY UNIT, BOTH SIDES")
	ui.text(youName.to_upper(), x + w * 0.56, y + 18, {"align": "right", "size": 12, "bold": true, "color": youCol})
	ui.text(foeName.to_upper(), x + w * 0.96, y + 18, {"align": "right", "size": 12, "bold": true, "color": foeCol})
	var after := unitTable(x, y + 40, w, you, foe, maxi(4, floori((h - 130) / 22.0)))
	heading(x, after + 16, w, "BUILT")
	builtLine(x, after + 42, w, you.builtByType, foe.builtByType)


## Buildings put up, as one compact line: "House 6/5" is yours / theirs.
static func builtLine(x: float, y: float, w: float, a: Dictionary, b: Dictionary) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var set := {}
	for k in a:
		set[k] = true
	for k in b:
		set[k] = true
	var types: Array = set.keys()
	JS.sort_by_cmp(types, func(p, q): return (_n(a, q) + _n(b, q)) - (_n(a, p) + _n(b, p)))
	var bx := x
	ctx.font = "12px \"Trebuchet MS\", sans-serif"
	for t in types:
		var label := "%s  %s/%s" % [_buildingName(t), JS.num(_n(a, t)), JS.num(_n(b, t))]
		var tw := ctx.measureText(label).width
		if bx + tw > x + w:
			break
		ui.text(label, bx, y, {"size": 12, "color": LABEL})
		ctx.font = "12px \"Trebuchet MS\", sans-serif"
		bx += tw + 22


## A realm's sworn Oaths, resolved; reports from before Oaths have none.
static func oathsOf(p) -> Array:
	if p == null:
		return []
	var out: Array = []
	for id in JS.nz(p.get("oaths"), []):
		var o = C.OATHS.get(id)
		if o != null:
			out.append(o)
	return out


static func kd(s: Dictionary) -> float:
	return float(s.unitsKilled) if float(s.unitsLost) == 0 else float(s.unitsKilled) / float(s.unitsLost)


static func _strip_the(s: String) -> String:
	return s.substr(4) if s.begins_with("The ") else s


## A palette colour as the TS keeps it (a "#rrggbb" string), for the static
## colour slots that are typed String.
static func _hex(c) -> String:
	return c if c is String else "#" + (c as Color).to_html(false)


# ------------------------------------------------------------ standings --

## A standings column (TS `Col`): { label, value: Callable(p) -> float,
## format?: Callable(n, p) -> String, higherIsBetter? (default true; false
## marks the *lowest* as the best in the column), stack?: Callable(p) ->
## [[v, colour]] (stacked breakdown drawn as the bar, instead of a single
## share), drop (dropped first when the table is narrow: higher goes first),
## minW?, noTotal? (leave the column out of team totals — a sum of ages or
## ratios is noise), noBar? (no bar: for things like age, where a share of the
## best says nothing) }.

## The standings table: a row per realm, a column per measure, and under every
## number a bar showing it against the best in that column, in the realm's own
## colour. The best in each column is picked out in gold, so the answer to "who
## was best at what" is on the screen without reading a single number.
static func standingsTable(x: float, y: float, w: float, h: float, players: Array, cols: Array, opts: Dictionary = {}) -> float:
	var ui := UI.ui
	var ctx := ui.ctx
	var st := standings(players)
	var groups: Array = st.groups
	var ffa: bool = st.ffa
	var team := not ffa

	# Name block, then the columns that fit.
	var placeW := 38.0 if ffa else 0.0
	var nameW := maxf(150, minf(230, JS.round(w * 0.25))) + placeW
	var shown: Array = cols.duplicate()
	var minW := func(c) -> float: return float(c.get("minW", 78))
	var sumMin := func(arr: Array) -> float:
		var s := 0.0
		for c in arr:
			s += minW.call(c)
		return s
	while shown.size() > 2 and sumMin.call(shown) > w - nameW:
		var worst = shown[0]
		for c in shown:
			if c.drop > worst.drop:
				worst = c
		shown = shown.filter(func(c): return c != worst)
	var spare := maxf(0, w - nameW - sumMin.call(shown))
	var widths: Array = shown.map(func(c): return minW.call(c) + spare / shown.size())

	# Rows size to the room: roomy for four realms, tight for sixteen. An 8v8
	# has to fit a 760px-tall window, so past ten realms they may close right up.
	var n := players.size()
	var many := n > 10
	var headerH := 28.0
	var groupH := (24.0 if many else 30.0) if team else 0.0
	var footH := 24.0 if opts.get("footnote") else 0.0
	var avail := h - headerH - footH - groups.size() * groupH
	var withTotals := floorf(avail / maxf(1, n + groups.size() * 0.8))
	var totals := team and withTotals >= 34
	var rowH := maxf(22.0 if many else 28.0, minf(54, withTotals if totals else floorf(avail / maxf(1, n))))
	var compact := rowH < 40

	# Column headers.
	var cx := x + nameW
	for i in shown.size():
		ui.text(String(shown[i].label).to_upper(), cx + widths[i] - 10, y, {"align": "right", "size": 11.5, "bold": true, "color": LABEL})
		cx += widths[i]
	ui.text("REALM" if ffa else "TEAM / REALM", x + 12, y, {"size": 11.5, "bold": true, "color": LABEL})

	# The best in every column, across everyone — "best at what" is the point.
	var best: Array = []
	var mx: Array = []
	for c in shown:
		var vals: Array = players.filter(func(p): return not p.get("horde", false)).map(func(p): return float(c.value.call(p)))
		if vals.is_empty():
			best.append(NAN)
		else:
			var b: float = vals.max() if c.get("higherIsBetter", true) else vals.min()
			# Nothing to celebrate when everyone is on zero (or all are equal).
			best.append(NAN if vals.all(func(v): return v == b) else b)
		var m := 1e-9
		for p in players:
			m = maxf(m, float(c.value.call(p)))
		mx.append(m)

	var ry := y + headerH - 6
	var place := 0
	for gi in groups.size():
		var g: Dictionary = groups[gi]
		if team:
			# A band naming the side: which team, how it ended, what it scored.
			var gy := ry + 6
			ctx.fillStyle = Palette.withAlpha(C.PAL.uiAccent, 0.1)
			ctx.fillRect(x - 6, gy, w + 12, groupH - 6)
			var mine: bool = g.members.any(func(m): return m.relation == "you")
			var label: String = "THE HORDE" if g.members.all(func(m): return m.get("horde", false)) else "TEAM %d%s" % [gi + 1, " — YOUR SIDE" if mine else ""]
			ui.text(label, x + 12, gy + (groupH - 6) / 2, {"size": 12.5, "bold": true, "color": "#ffe9b0" if mine else LABEL})
			var status := "Victors" if g.won else ("Defeated" if g.members.all(func(m): return m.defeated) else "")
			if status != "":
				ctx.font = "bold 12.5px \"Trebuchet MS\", sans-serif"
				var lw := ctx.measureText(label).width
				ui.text("·  %s" % status, x + 20 + lw, gy + (groupH - 6) / 2, {
					"size": 12.5, "bold": true, "color": BEST if g.won else LOST,
				})
			ry += groupH
		for p in g.members:
			place += 1
			var top := ry
			var mid := top + rowH / 2
			var me: bool = p.relation == "you"
			var dim: bool = p.defeated and not p.won
			# Row ground; yours outlined so you can find yourself in eight rows.
			ctx.fillStyle = Palette.withAlpha(C.PAL.uiAccent, 0.16) if me else ("rgba(255,255,255,0.05)" if place % 2 else "rgba(255,255,255,0.025)")
			ctx.fillRect(x - 6, top + 1, w + 12, rowH - 2)
			if me:
				ctx.strokeStyle = Palette.withAlpha(C.PAL.uiAccent, 0.65)
				ctx.lineWidth = 1.5
				ctx.strokeRect(x - 6 + 0.75, top + 1.75, w + 12 - 1.5, rowH - 3.5)
			ctx.fillStyle = barColour(p)
			ctx.fillRect(x - 6, top + 1, 5, rowH - 2)

			var nx := x + 10
			if ffa:
				ui.text(ordinal(place), nx, mid, {"size": 13 if compact else 15, "bold": true, "color": BEST if place == 1 else LABEL})
				nx += placeW
			# Name, a tag saying whose it is, and how it ended.
			var nameSize := 14.0 if compact else 15.5
			var nameY := mid if compact else mid - 8
			ui.text(playerName(p), nx, nameY, {"size": nameSize, "bold": true, "color": nameColour(p)})
			ctx.font = "bold %spx \"Trebuchet MS\", sans-serif" % JS.num(nameSize)
			var tx := nx + ctx.measureText(playerName(p)).width + 8
			tx += relationTag(tx, nameY, p)
			var fac := ""
			if p.get("faction"):
				var fd = C.FACTIONS.get(p.faction)
				fac = _strip_the(fd.name) if fd != null else ""
			var ending: String = "Victor" if p.won else (("Out at %s" % mmss(p.defeatedAt) if float(p.defeatedAt) >= 0 else "Defeated") if p.defeated else "Still standing")
			var status := ("%s · %s" % [fac, ending]) if fac != "" else ending
			var statusCol := BEST if p.won else (LOST if p.defeated else MUTED)
			if compact:
				if tx < x + nameW - 40:
					ui.text(status, tx, nameY, {"size": 11, "color": statusCol})
			else:
				ui.text(status, nx, mid + 10, {"size": 12, "color": statusCol})
				# Then the Oaths it swore — who the realm became.
				ctx.font = "12px \"Trebuchet MS\", sans-serif"
				var ox := nx + ctx.measureText(status).width + 10
				for o in oathsOf(p):
					ctx.font = "bold 11.5px \"Trebuchet MS\", sans-serif"
					var ow := ctx.measureText(o.short).width
					if ox + ow > x + nameW - 6:
						break
					ui.text(o.short, ox, mid + 10, {"size": 11.5, "bold": true, "color": o.color})
					ox += ow + 7

			# The numbers.
			var cx2 := x + nameW
			for i in shown.size():
				var c: Dictionary = shown[i]
				var cw: float = widths[i]
				var v := float(c.value.call(p))
				var isBest: bool = not p.get("horde", false) and not is_nan(best[i]) and v == best[i]
				var text: String = c.format.call(v, p) if c.has("format") else num(v)
				ui.text(text, cx2 + cw - 10, mid - 3 if compact else mid - 5, {
					"align": "right", "size": 13.5 if compact else 15, "bold": true,
					"color": BEST if isBest else (Palette.withAlpha(INK, 0.6) if dim else INK),
				})
				if c.get("noBar", false):
					cx2 += cw
					continue
				# The bar: this realm's share of the column's best, in its colour.
				var bx := cx2 + 12
				var bw := cw - 22
				var by := mid + 7 if compact else mid + 9
				var bh := 3.0 if compact else 4.0
				ctx.fillStyle = "rgba(255,255,255,0.07)"
				ctx.fillRect(bx, by, bw, bh)
				if c.has("stack"):
					var parts: Array = c.stack.call(p)
					var tot := 0.0
					for pp in parts:
						tot += float(pp[0])
					var sx := bx
					var full: float = bw * minf(1, tot / mx[i])
					for pp in parts:
						var pw: float = (float(pp[0]) / tot) * full if tot > 0 else 0.0
						ctx.fillStyle = pp[1]; ctx.fillRect(sx, by, pw, bh)
						sx += pw
				else:
					ctx.fillStyle = Palette.withAlpha(barColour(p), 0.5 if dim else 0.95)
					ctx.fillRect(bx, by, bw * maxf(0, minf(1, v / mx[i])), bh)
				cx2 += cw
			ry += rowH

		# Team totals, where there is room: what each side did as a side.
		if totals:
			var mid := ry + 12
			ui.text("Team total", x + 12 + placeW, mid, {"size": 12, "bold": true, "color": MUTED})
			var cx3 := x + nameW
			for i in shown.size():
				var c: Dictionary = shown[i]
				if not c.get("noTotal", false):
					var v := 0.0
					for m in g.members:
						v += float(c.value.call(m))
					ui.text(num(v), cx3 + widths[i] - 10, mid, {"align": "right", "size": 12.5, "bold": true, "color": MUTED})
				cx3 += widths[i]
			ry += 26

	if opts.get("footnote"):
		ui.text(opts.footnote, x, ry + 16, {"size": 11.5, "color": MUTED})
		ry += footH
	return ry + 6


## How wide `key` will draw the same items.
static func keyWidth(items: Array) -> float:
	var ctx := UI.ui.ctx
	ctx.font = "12px \"Trebuchet MS\", sans-serif"
	var s := 0.0
	for it in items:
		s += 14 + ctx.measureText(it[0]).width + 16
	return s - 16


## The little "YOU" / "ALLY" badge after a name. Returns the width it took.
static func relationTag(x: float, y: float, p: Dictionary) -> float:
	var ui := UI.ui
	var ctx := ui.ctx
	var me: bool = p.relation == "you"
	var tag := "YOU" if me else ("ALLY" if p.relation == "ally" else "")
	if tag == "":
		return 0
	ctx.font = "bold 10.5px \"Trebuchet MS\", sans-serif"
	var tw := ctx.measureText(tag).width + 10
	ctx.fillStyle = Palette.withAlpha(C.PAL.uiAccent, 0.85) if me else Palette.withAlpha("#2fb39a", 0.75)
	ctx.beginPath(); ctx.roundRect(x, y - 8, tw, 16, 3); ctx.fill()
	ui.text(tag, x + tw / 2, y + 0.5, {"align": "center", "size": 10.5, "bold": true, "color": "#1a1408"})
	return tw + 8


## A row of colour keys: "■ Food  ■ Wood  ■ Gold".
static func key(x: float, y: float, items: Array, title = null) -> float:
	var ui := UI.ui
	var ctx := ui.ctx
	var kx := x
	if title:
		ui.text(title, kx, y, {"size": 12, "bold": true, "color": LABEL})
		ctx.font = "bold 12px \"Trebuchet MS\", sans-serif"
		kx += ctx.measureText(title).width + 10
	for it in items:
		ctx.fillStyle = it[1]
		ctx.fillRect(kx, y - 5, 10, 10)
		ui.text(it[0], kx + 14, y, {"size": 12, "color": LABEL})
		ctx.font = "12px \"Trebuchet MS\", sans-serif"
		kx += 14 + ctx.measureText(it[0]).width + 16
	return kx


static func drawStandingsTab(tab: String, x: float, y: float, w: float, h: float, ps: Array) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	ctx.save()
	ctx.beginPath(); ctx.rect(x - 12, y - 16, w + 24, h + 24); ctx.clip()
	_standingsBody(tab, x, y, w, h, ps)
	ctx.restore()


static func _standingsBody(tab: String, x: float, y: float, w: float, h: float, ps: Array) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	if tab == "overview":
		heading(x, y, w, "STANDINGS")
		var tableH := minf(h - 40, 60 + ps.size() * 54 + 90)
		var after := standingsTable(x, y + 30, w, tableH, ps, [
			{"label": "Score", "value": func(p): return p.score, "drop": 0},
			{"label": "Killed", "value": func(p): return p.unitsKilled, "drop": 1},
			{"label": "Lost", "value": func(p): return p.unitsLost, "higherIsBetter": false, "drop": 3},
			{"label": "Gathered", "value": func(p): return p.gathered, "drop": 2, "minW": 88},
			{"label": "Razed", "value": func(p): return p.buildingsRazed, "drop": 5},
			{"label": "Peak army", "value": func(p): return p.peakArmy, "drop": 6, "minW": 86},
			{"label": "Age", "value": func(p): return p.age, "format": func(n, _p): return Tech.ageShort(int(n)), "drop": 4, "noTotal": true, "noBar": true},
		], {"footnote": BEST_NOTE})
		# Where each realm's resources went, if there is room for it: a share,
		# not a size, so a small economy that spent everything on its army reads
		# as clearly as a big one.
		var lineH := 26.0
		var need := 34 + ps.size() * lineH
		if y + h - after >= need:
			var items := [["army", ARMY], ["buildings", BUILT], ["tech", TECH], ["unspent", BANKED]]
			var kw := keyWidth(items)
			heading(x, after + 14, w, "WHERE IT WENT", x + w - kw - 16)
			key(x + w - kw, after + 14, items)
			var ly := after + 42
			var groups: Array = standings(ps).groups
			var labelW := maxf(150, minf(230, JS.round(w * 0.25)))
			for g in groups:
				for p in g.members:
					ui.text(playerName(p), x, ly, {"size": 13, "bold": true, "color": nameColour(p)})
					var parts := [
						[float(p.spentOn.units), ARMY], [float(p.spentOn.buildings), BUILT], [float(p.spentOn.tech), TECH], [float(p.banked), BANKED],
					]
					var tot := 0.0
					for pp in parts:
						tot += pp[0]
					tot = maxf(1, tot)
					var bx := x + labelW
					var bw := w - labelW - 130
					ctx.save()
					ctx.beginPath(); ctx.roundRect(bx, ly - 7, bw, 14, 4); ctx.clip()
					ctx.fillStyle = "rgba(0,0,0,0.4)"; ctx.fillRect(bx, ly - 7, bw, 14)
					var sx := bx
					for pp in parts:
						var pw: float = (pp[0] / tot) * bw
						ctx.fillStyle = pp[1]; ctx.fillRect(sx, ly - 7, pw, 14)
						sx += pw
					ctx.restore()
					ui.text("%s gathered" % num(p.gathered), x + w, ly, {"align": "right", "size": 12, "color": MUTED})
					ly += lineH
		return

	if tab == "economy":
		heading(x, y, w, "ECONOMY")
		var kx := key(x, y + 28, [["food", FOOD], ["wood", WOOD], ["gold", GOLD]], "Gathered:")
		key(kx + 14, y + 28, [["army", ARMY], ["buildings", BUILT], ["tech", TECH]], "Spent:")
		standingsTable(x, y + 58, w, h - 58, ps, [
			{
				"label": "Gathered", "value": func(p): return p.gathered, "drop": 0, "minW": 118,
				"stack": func(p): return [[p.gatheredBy.food, FOOD], [p.gatheredBy.wood, WOOD], [p.gatheredBy.gold, GOLD]],
			},
			{
				"label": "Spent", "value": func(p): return p.spent, "drop": 1, "minW": 118,
				"stack": func(p): return [[p.spentOn.units, ARMY], [p.spentOn.buildings, BUILT], [p.spentOn.tech, TECH]],
			},
			{"label": "Unspent", "value": func(p): return p.banked, "higherIsBetter": false, "drop": 4},
			{"label": "Peak vils", "value": func(p): return p.peakVillagers, "drop": 2},
			{"label": "Vils idle", "value": func(p): return p.idleVillagerTime, "higherIsBetter": false, "format": func(n, _p): return secs(n), "drop": 3, "minW": 86, "noTotal": true},
			{
				"label": "TC idle", "value": func(p): return _n(p, "idleTcTime") / _n(p, "tcSeconds") if _n(p, "tcSeconds") > 0 else 0.0, "higherIsBetter": false,
				"format": func(_n2, p): return idleShare(_n(p, "idleTcTime"), _n(p, "tcSeconds")), "drop": 5, "noTotal": true,
			},
		], {"footnote": BEST_NOTE})
		return

	if tab == "military":
		heading(x, y, w, "THE FIGHTING")
		standingsTable(x, y + 30, w, h - 30, ps, [
			{"label": "Killed", "value": func(p): return p.unitsKilled, "drop": 0},
			{"label": "Lost", "value": func(p): return p.unitsLost, "higherIsBetter": false, "drop": 1},
			{"label": "K / L", "value": func(p): return kd(p), "format": func(n, _p): return JS.to_fixed(n, 2), "drop": 2, "noTotal": true, "noBar": true},
			{"label": "Dmg dealt", "value": func(p): return p.damageDealt, "drop": 3, "minW": 88},
			{"label": "Dmg taken", "value": func(p): return p.damageTaken, "higherIsBetter": false, "drop": 6, "minW": 88},
			{"label": "Razed", "value": func(p): return p.buildingsRazed, "drop": 4},
			{"label": "Bldgs lost", "value": func(p): return p.buildingsLost, "higherIsBetter": false, "drop": 7, "minW": 84},
			{"label": "Techs", "value": func(p): return p.upgrades, "drop": 5},
		], {"footnote": BEST_NOTE})
		return

	armiesTab(x, y, w, h, ps)


## What each realm fielded, as chips — "12 Man-at-Arms · 9 Archer" — biggest
## first, then what it built. A unit-by-player grid would be eight columns of
## mostly dots; this reads as each realm's style at a glance.
static func armiesTab(x: float, y: float, w: float, h: float, ps: Array) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	heading(x, y, w, "WHAT EACH REALM FIELDED")
	var groups: Array = standings(ps).groups
	var nameW := maxf(150, minf(210, JS.round(w * 0.22)))
	# Sixteen realms in an 8v8 have to fit a 760px window too, so rows close
	# right up when there are many — one line of chips, nothing under the name.
	var rowH := maxf(25.0 if ps.size() > 10 else 40.0, minf(66, floorf((h - 40) / maxf(1, ps.size()))))
	var twoLines := rowH >= 52
	var summary := rowH >= 40
	var ry := y + 26
	var i := 0
	for g in groups:
		for p in g.members:
			i += 1
			var me: bool = p.relation == "you"
			ctx.fillStyle = Palette.withAlpha(C.PAL.uiAccent, 0.16) if me else ("rgba(255,255,255,0.05)" if i % 2 else "rgba(255,255,255,0.025)")
			ctx.fillRect(x - 6, ry + 1, w + 12, rowH - 2)
			ctx.fillStyle = barColour(p)
			ctx.fillRect(x - 6, ry + 1, 5, rowH - 2)
			var l1 := ry + rowH / 2 - 9 if twoLines else ry + rowH / 2
			ui.text(playerName(p), x + 10, l1, {"size": 14.5, "bold": true, "color": nameColour(p)})
			ctx.font = "bold 14.5px \"Trebuchet MS\", sans-serif"
			relationTag(x + 18 + ctx.measureText(playerName(p)).width, l1, p)
			if summary:
				var trained := 0.0
				for v in p.trainedByType.values():
					trained += float(v)
				ui.text("%s trained · %s lost" % [num(trained), num(p.unitsLost)], x + 10, l1 + 19 if twoLines else l1 + 15, {
					"size": 11.5, "color": MUTED,
				})

			# Unit chips, biggest first. Villagers last and muted — every realm
			# trains them and they say nothing about a style.
			var units: Array = []
			for t in p.trainedByType:
				if float(p.trainedByType[t]) > 0:
					units.append([t, float(p.trainedByType[t])])
			JS.sort_by_cmp(units, func(a, b):
				var va := (1 if a[0] == "villager" else 0) - (1 if b[0] == "villager" else 0)
				return va if va != 0 else b[1] - a[1])
			var cx := x + nameW
			var right := x + w
			ctx.font = "bold 12.5px \"Trebuchet MS\", sans-serif"
			var shownN := 0
			for u in units:
				var t: String = u[0]
				var label := "%s %s" % [JS.num(u[1]), _unitName(t)]
				var cw := ctx.measureText(label).width + 16
				var left := units.size() - shownN
				if cx + cw > right - (70 if left > 1 else 0):
					ui.text("+%d more" % left, cx + 2, l1, {"size": 12, "color": MUTED})
					break
				var vil := t == "villager"
				ctx.fillStyle = "rgba(255,255,255,0.06)" if vil else Palette.withAlpha(barColour(p), 0.22)
				ctx.beginPath(); ctx.roundRect(cx, l1 - 10, cw, 20, 4); ctx.fill()
				ctx.strokeStyle = "rgba(255,255,255,0.12)" if vil else Palette.withAlpha(barColour(p), 0.55)
				ctx.lineWidth = 1
				ctx.stroke()
				ui.text(label, cx + 8, l1 + 0.5, {"size": 12.5, "bold": true, "color": MUTED if vil else INK})
				ctx.font = "bold 12.5px \"Trebuchet MS\", sans-serif"
				cx += cw + 6
				shownN += 1
			if units.is_empty():
				ui.text("— no units trained —", cx, l1, {"size": 12, "color": MUTED})

			if twoLines:
				var built: Array = []
				for t in p.builtByType:
					if float(p.builtByType[t]) > 0:
						built.append([t, float(p.builtByType[t])])
				JS.sort_by_cmp(built, func(a, b): return b[1] - a[1])
				var line := "  ·  ".join(PackedStringArray(built.map(func(e): return "%s %s" % [JS.num(e[1]), _buildingName(e[0])])))
				if line != "":
					ctx.font = "12px \"Trebuchet MS\", sans-serif"
					var s := "Built: %s" % line
					var maxW := w - nameW
					while s.length() > 12 and ctx.measureText(s).width > maxW:
						var cut := s.rfind("  ·  ")
						s = (s.substr(0, cut) if cut >= 0 else s.substr(0, s.length() - 1)) + "  …"
					ui.text(s, x + nameW, l1 + 20, {"size": 12, "color": LABEL})
			ry += rowH


# ------------------------------------------------------------------ entry --

## Draw one tab's body inside the given rect. Returns nothing — the caller owns
## the frame, the tabs and the buttons.
static func drawReportTab(tab: String, x: float, y: float, w: float, h: float, r: Dictionary) -> void:
	if tab == "feedback":
		drawFeedbackTab(x, y, w, h, r)
	elif isStandings(r):
		drawStandingsTab(tab, x, y, w, h, r.players)
	else:
		drawDuelTab(tab, x, y, w, h, r)


## The key beside the tabs. A duel needs one — two colours stand for two
## realms on every row. Standings name each realm on its own row, so it says
## nothing there. `right` is the right edge to align against.
static func drawReportKey(right: float, y: float, r: Dictionary) -> void:
	if isStandings(r):
		return
	var ui := UI.ui
	var ctx := ui.ctx
	var ps = r.get("players")
	var youP = _find(ps, func(p): return p.relation == "you")
	var foeP = _find(ps, func(p): return p.relation != "you")
	var items := [
		["%s (you)" % playerName(youP) if youP != null else "You", barColour(youP) if youP != null else YOU],
		[playerName(foeP) if foeP != null else "Opponent", barColour(foeP) if foeP != null else FOE],
	]
	ctx.font = "12.5px \"Trebuchet MS\", sans-serif"
	var width := 0.0
	for it in items:
		width += 16 + ctx.measureText(it[0]).width + 16
	var kx := right - width
	for it in items:
		ctx.fillStyle = it[1]
		ctx.fillRect(kx, y - 6, 12, 12)
		ui.text(it[0], kx + 17, y, {"size": 12.5, "color": LABEL})
		ctx.font = "12.5px \"Trebuchet MS\", sans-serif"
		kx += 16 + ctx.measureText(it[0]).width + 16


## "2v2", "1v1", "4-player free-for-all" — how the match was set up.
static func matchFormat(r: Dictionary) -> String:
	var ps = r.get("players")
	if ps == null:
		return "%dv%d" % [r.you.teams.size(), r.foe.teams.size()]
	if (ps as Array).any(func(p): return p.get("horde", false)):
		var defenders: int = (ps as Array).filter(func(p): return not p.get("horde", false)).size()
		return "Survival · %d defenders" % defenders if defenders > 1 else "Survival"
	var sizes := {}
	for p in ps:
		sizes[int(p.group)] = int(sizes.get(int(p.group), 0)) + 1
	if sizes.size() > 2 and sizes.values().all(func(s): return s == 1):
		return "%d-player free-for-all" % (ps as Array).size()
	# Your side first, the way you'd say it.
	var mineP = _find(ps, func(p): return p.relation == "you")
	var mine = int(mineP.group) if mineP != null else null
	var order: Array = []
	for g in sizes:
		order.append([g, sizes[g]])
	JS.sort_by_cmp(order, func(a, b):
		if a[0] == mine:
			return -1
		if b[0] == mine:
			return 1
		return b[1] - a[1])
	return "v".join(PackedStringArray(order.map(func(e): return str(e[1]))))


## The line under the title: how long, on what, and who was in it.
static func reportSubtitle(r: Dictionary) -> String:
	return "%s  ·  %s  ·  %s" % [r.mapName, matchFormat(r), mmss(r.durationSec)]


## What went well and what cost you, in sentences. Two columns when there's
## room, stacked when there isn't; every line wraps inside its column.
static func drawFeedbackTab(x: float, y: float, w: float, h: float, r: Dictionary) -> void:
	var ui := UI.ui
	var fb := Feedback.matchFeedback(r)
	var two := w >= 620
	var colW := (w - 32) / 2 if two else w
	var col := func(cx: float, cy: float, title: String, colour, mark: String, lines: Array, empty: String) -> float:
		heading(cx, cy, colW, title)
		var ly := cy + 30
		if lines.is_empty():
			ui.text(empty, cx, ly, {"size": 12.5, "color": MUTED})
			return ly + 24
		for line in lines:
			ui.text(mark, cx, ly, {"size": 14, "bold": true, "color": colour})
			var rows: Array = wrap_(line, colW - 26, 13)
			for i in rows.size():
				ui.text(rows[i], cx + 22, ly + i * 18, {"size": 13, "color": INK})
			ly += rows.size() * 18 + 12
		return ly
	var endL: float = col.call(x, y, "WENT WELL", "#8fd07a", "+", fb.good, "Nothing stood out in your favour this time.")
	if two:
		col.call(x + colW + 32, y, "COST YOU", LOST, "−", fb.bad, "No clear mistakes — well played.")
	else:
		col.call(x, endL + 10, "COST YOU", LOST, "−", fb.bad, "No clear mistakes — well played.")


static func wrap_(text: String, width: float, size: float) -> Array:
	var ctx := UI.ui.ctx
	ctx.save()
	ctx.font = "%spx \"Trebuchet MS\", sans-serif" % JS.num(size)
	var out: Array = []
	var line := ""
	for word in text.split(" "):
		var next := ("%s %s" % [line, word]) if line != "" else word
		if ctx.measureText(next).width > width and line != "":
			out.append(line)
			line = word
		else:
			line = next
	if line != "":
		out.append(line)
	ctx.restore()
	return out
