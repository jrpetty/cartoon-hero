## The menus and full-screen UI drawn by the port, state for state the same as
## game/scripts/godot-visual-screens.mjs: replays tests/visual/screens/
## spec.json (the localStorage fixtures and every screen state) through the
## ported screens, and writes godot_<state>.png beside the TS ts_<state>.png.
## Needs a real renderer:
##
##   xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
##     --path godot res://tests/visual/screens_sheet.tscn [-- state-prefix ...]
##
## The art is drawn procedurally (ArtCache off) unless `--cache` is passed, so
## it is the same art the TS draws.
extends Node

const DIR := "res://tests/visual/screens/"

var spec: Dictionary
var states: Array = []
var ctx := Ctx.new()
var _vp: SubViewport
var _canvas: Node2D
var _i := -1
var _wait := 0


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	var prefixes: Array = []
	ArtCache.enabled = false
	for a in args:
		if a == "--cache":
			ArtCache.enabled = true
			ArtCache.unlimited = true
		else:
			prefixes.append(a)
	spec = JSON.parse_string(FileAccess.get_file_as_string(DIR + "spec.json"))
	for s in spec.states:
		if prefixes.is_empty() or prefixes.any(func(p): return String(s.name).begins_with(p)):
			states.append(s)
	Storage.use_memory_only()
	audio.muted = true
	_vp = SubViewport.new()
	_vp.transparent_bg = false
	_vp.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	add_child(_vp)
	_canvas = Node2D.new()
	_vp.add_child(_canvas)


func _process(_dt: float) -> void:
	if _wait > 0:
		_wait -= 1
		if _wait == 0:
			_save()
		return
	_i += 1
	if _i >= states.size():
		get_tree().quit()
		return
	_paint(states[_i])
	_wait = 3


func _save() -> void:
	var s: Dictionary = states[_i]
	var img := _vp.get_texture().get_image()
	var out := ProjectSettings.globalize_path(DIR + "godot_%s.png" % s.name)
	img.save_png(out)
	print("godot ", s.name)


## JSON gives every number as a float; the screens' own literals are ints.
static func _ints(v):
	if v is float and v == floorf(v) and absf(v) < 1e15:
		return int(v)
	if v is Array:
		return v.map(func(x): return _ints(x))
	if v is Dictionary:
		var d := {}
		for k in v:
			d[k] = _ints(v[k])
		return d
	return v


static func _set_fields(obj: Object, fields) -> void:
	if fields == null:
		return
	for k in fields:
		var v = fields[k]
		if k == "teamPreset":
			v = _ints(v)
		obj.set(k, v.duplicate(true) if (v is Array or v is Dictionary) else v)


func _make_screen(s: Dictionary, profile: Profile) -> Object:
	match s.screen:
		"menu":
			return Screens.MenuScreen.new()
		"setup":
			var scr := Screens.SetupScreen.new()
			var cfg = s.get("config")
			if cfg != null:
				for k in cfg:
					var v = cfg[k]
					scr.config[k] = v.duplicate(true) if (v is Array or v is Dictionary) else v
			return scr
		"armory":
			var scr := Screens.ArmoryScreen.new()
			var rv = s.get("reveal")
			if rv != null:
				scr.opening = C.CHESTS[int(rv.chest)]
				var v = Catalog.VARIANT_BY_KEY[Catalog.variantKey(rv.unit, rv.rarity)]
				scr.result = {"variant": v, "rarity": int(rv.rarity), "duplicate": rv.duplicate, "refund": 0.0}
				scr.revealed = true
			return scr
		"postmatch":
			return Screens.PostMatchScreen.new()
		"career":
			return CareerScreen.new()
		"codex":
			return CodexScreen.new()
		"factions":
			var b := FactionBook.new()
			b.focus(s.get("focus"))
			return b
		"settings":
			return SettingsScreen.new()
		"nemesis":
			return RivalScreen.new()
	push_error("no screen " + str(s.screen))
	return null


func _draw_screen(s: Dictionary, scr: Object, profile: Profile, args: Dictionary) -> void:
	var W: float = s.W
	var H: float = s.H
	var time: float = s.time
	var dt: float = s.dt
	match s.screen:
		"menu", "setup", "career", "codex":
			scr.draw(W, H, time, profile)
		"armory":
			scr.draw(W, H, time, dt, profile)
		"postmatch":
			var graph = spec.graphs.get(args.graph) if args.get("graph") != null else null
			var awards: Array = spec.awards if args.get("awards") != null else []
			scr.draw(W, H, time, dt, args.won, spec.reports[args.report], spec.rewards, profile, args.xpBefore, int(args.levelsGained), graph, awards)
		"factions":
			scr.draw(W, H, time, profile, dt)
		"settings":
			scr.draw(W, H, time, args.settings, false)
		"nemesis":
			scr.draw(W, H, time, args.playerFaction)


func _paint(s: Dictionary) -> void:
	var W := int(s.W)
	var H := int(s.H)
	_vp.size = Vector2i(W, H)
	Storage._data = (spec.fixtures[s.fixture] as Dictionary).duplicate()
	World.nextId = 1
	Palette.setColorblindTeams(false)
	var profile := Profile.load()
	var scr := _make_screen(s, profile)
	_set_fields(scr, s.get("set"))
	if s.get("openReport") != null:
		# The history row opened back up as its report, as clicking "Report" does.
		scr.openReport = History.listHistory()[int(s.openReport)]
	var args: Dictionary = (s.get("args", {}) as Dictionary).duplicate(true)
	Screens.setMouseDown(false)
	var frames := int(s.frames)
	for f in frames:
		var last := f == frames - 1
		# No reset between frames: a canvas keeps its attributes (the TS draws
		# its title with whatever textBaseline the last frame left).
		ctx.begin(_canvas, W, H)
		ctx.fillStyle = "#000"
		ctx.fillRect(0, 0, W, H)
		var mouse: Dictionary = s.mouse if last and s.get("mouse") != null else {"mx": -99, "my": -99}
		UI.ui.begin(ctx, {"mx": mouse.mx, "my": mouse.my, "clicked": false, "rightClicked": false, "alt": false})
		_draw_screen(s, scr, profile, args)
		UI.ui.flushTooltip(W, H)
		ctx.end()
		if f == 0:
			_set_fields(scr, s.get("after"))
