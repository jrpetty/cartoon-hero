## render/palette.ts — the painterly fantasy palette's helpers. The palette
## itself (`PAL`) is data: `C.PAL`.
##
## `withAlpha` and `shade` return a Color rather than the TS's "rgba(...)" /
## "rgb(...)" string: every place the game uses them assigns the result to a
## style or a gradient stop (or shades it again), and Ctx takes a Color as
## readily as a string — without formatting and re-parsing one per call. Both
## accept a CSS string or a Color.
class_name Palette


# High-contrast team palette for the colorblind option. Hues are spread for
# luminance + warm/cool separation so adjacent teams stay distinguishable for
# the common deuter/protan/tritan cases. light/dark are derived at load.
const CB_MAIN: Array = [
	{"main": "#1f6fe0", "name": "Blue"},
	{"main": "#ff7a18", "name": "Orange"},
	{"main": "#f5d800", "name": "Yellow"},
	{"main": "#00b3b3", "name": "Cyan"},
	{"main": "#e0188a", "name": "Pink"},
	{"main": "#36c25a", "name": "Green"},
	{"main": "#9b6bff", "name": "Purple"},
	{"main": "#b05a18", "name": "Brown"},
	{"main": "#6fd0ff", "name": "Sky"},
	{"main": "#d83a2f", "name": "Red"},
	{"main": "#a6e022", "name": "Lime"},
	{"main": "#ff9ecb", "name": "Rose"},
	{"main": "#2f9e7a", "name": "Teal"},
	{"main": "#c0a8ff", "name": "Lilac"},
	{"main": "#e8e8e8", "name": "White"},
	{"main": "#7a7f88", "name": "Grey"},
]


static func teamFromMain(m: Dictionary) -> Dictionary:
	return {"main": m.main, "light": shade(m.main, 0.42), "dark": shade(m.main, -0.42), "name": m.name}


# Built lazily on first use so `shade` is defined; swapped by setColorblindTeams.
static var cbTeams = null
static var activeTeams = null


static func setColorblindTeams(on: bool) -> void:
	if on and cbTeams == null:
		cbTeams = CB_MAIN.map(teamFromMain)
	activeTeams = cbTeams if on else C.PAL.teams


static func teamColor(team: int) -> Dictionary:
	var t: Array = activeTeams if activeTeams != null else C.PAL.teams
	return t[team] if team >= 0 and team < t.size() else t[0]


## Quick alpha helper for hex colors.
static func withAlpha(hex, a: float) -> Color:
	var c := _rgb(hex)
	c.a = clampf(a, 0.0, 1.0) if not is_nan(a) else 0.0
	return c


## Parse "#rrggbb" or "rgb(r,g,b)" (or a Color) into an opaque colour.
static func _rgb(col) -> Color:
	if col is Color:
		var k: Color = col
		k.a = 1.0
		return k
	var p = Ctx.parse_color(col)
	if p == null:
		return Color8(128, 128, 128)
	var c: Color = p
	c.a = 1.0
	return c


## Darken/lighten a hex or rgb() color by a factor (-1..1).
static func shade(hex, f: float) -> Color:
	var c := _rgb(hex)
	return Color8(_adj(c.r8, f), _adj(c.g8, f), _adj(c.b8, f))


static func _adj(c: int, f: float) -> int:
	var v := c + (255 - c) * f if f > 0.0 else c * (1.0 + f)
	return clampi(int(JS.round(v)), 0, 255)
