## ui/chat.ts — in-match chat log + input line, drawn bottom-left over the
## world. Standalone so it can be rendered against a real canvas in tests. The
## App owns the data and the typing state; this just paints them.
##
## A ChatLine is a Dictionary {name, text, team, t}.
class_name Chat

const LINE_LIFE := 12.0 ## seconds a chat line lingers
const MAX_SHOWN := 6


## `draft` is the line being typed, or null when the input is closed.
static func drawChat(W: float, H: float, lines: Array, draft, now: float, baseY: float) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var PAL: Dictionary = C.PAL
	var x := 12.0
	var lineH := 18.0
	# Most-recent-first from the bottom up; only the last few, still-fresh lines.
	var fresh: Array = lines.filter(func(l): return now - l.t < LINE_LIFE or draft != null)
	fresh = fresh.slice(maxi(0, fresh.size() - MAX_SHOWN))
	var y := baseY - (lineH if draft != null else 0.0)

	if draft != null:
		ctx.fillStyle = "rgba(8,6,3,0.8)"
		ctx.fillRect(x - 4, y - 13, 420, 19)
		ui.text("Say:", x, y, {"size": 13, "color": PAL.uiAccent, "bold": true})
		ui.text(str(draft) + "▍", x + 38, y, {"size": 13, "color": "#f3e9d2"})
	for i in range(fresh.size() - 1, -1, -1):
		y -= lineH
		var l: Dictionary = fresh[i]
		var fade := 1.0 if draft != null else maxf(0.15, 1 - (now - l.t) / LINE_LIFE)
		var tag: String = Palette.teamColor(l.team).main
		ctx.fillStyle = Palette.withAlpha("#000000", 0.32 * fade)
		var nl: int = (l.name as String).length()
		var tl: int = (l.text as String).length()
		ctx.fillRect(x - 4, y - 13, minf(560, 12 + (nl + tl) * 7.2), 18)
		ui.text("%s:" % l.name, x, y, {"size": 12, "color": Palette.withAlpha(tag, fade), "bold": true})
		ui.text(l.text, x + 8 + nl * 7.4 + 6, y, {"size": 12, "color": Palette.withAlpha("#e7ddc4", fade)})


## Label for a ping/chat author by team, e.g. "Crimson".
static func authorLabel(team: int) -> String:
	return Spectator.teamLabel(team)
