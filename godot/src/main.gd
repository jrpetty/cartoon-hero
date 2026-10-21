## main.ts — Banner & Blade's application shell. Owns the canvas, the app state
## machine (menu → setup → match → post-match, plus the armory and the other
## screens), the fixed-timestep sim loop, input routing and the
## meta-progression loop around each match.
##
## Port notes
## ----------
## The App is this node (scenes/main.tscn). It draws through three LayerCtx
## layers, bottom to top: the world (camera space), the screen-space overlays
## over it (night, vignette, drag box, weather), and the interface. Godot
## events are fed into GameInput (engine/input.gd), which calls back into the
## same handlers the TS wires to it. Everything else reads line for line
## against main.ts; differences are noted where they are.
##
## Screens owned elsewhere (screens.ts, career_screen.ts, codex.ts, …) are
## reached dynamically (`_newScreen`), so this file runs before they exist; a
## missing one is stood in for by a minimal placeholder (see "Placeholders").
## The skirmish AI is loaded the same way.
##
## Threading
## ---------
## The simulation is ~10–20× slower in GDScript than in V8 (a busy tick is
## ~8 ms, the worst ~30–45 ms), so in a single-player match it runs on a
## WorkerThreadPool task while the main thread draws. The handoff:
##
## - The World (and the AIs, and the replay being played) is touched by
##   exactly one thread at a time. A frame either *owns* it — no task in
##   flight: the previous task was joined at the top of the frame — or it
##   doesn't.
## - An owning frame does everything the TS frame does that reads or changes
##   the world: input (every command is applied right then, between ticks,
##   stamped with the tick it lands on, exactly as in the TS), events, the
##   HUD, the placement preview, victory — and takes the renderer's snapshot
##   (Renderer.snapshot: the drawing fields of what is on screen). At its end
##   it hands the ticks that are due to a task: world.tick(), then every AI's
##   update, then the next — the TS loop, unchanged, so the same commands on
##   the same ticks give the same world.
## - While the task runs, the main thread draws the world from the snapshot
##   (render never reads the World). If the next frame comes round before
##   the task is done, that frame draws the world again from the snapshot —
##   camera, particles, weather and decals all keep moving — and leaves the
##   interface layer as it was; input events wait in a queue until the next
##   owning frame. A slow tick therefore never stalls the screen.
## - Net matches stay on the main thread (lockstep owns its own pacing).
##   `threadSim = false` (or `--sim-sync` on the command line) runs every
##   match the TS way, for comparison; tests/test_main_loop.gd checks that
##   both end a replay on the same Commands.worldChecksum.
extends Node2D

const SIM_DT := 1.0 / 20.0

## Buildings you can drag-paint into a continuous run.
## Dragged out as one gap-free run. A wall is never one segment, which is why
## these also stay armed between runs (see paintWallLine).
const LINE_BUILDABLE := {"palisade": true, "stone_wall": true}
## Dragged out as a spaced grid with walking room between. Only the buildings
## you genuinely want several of at once — a drag over anything else places
## one, because nine Town Centres in a grid is a misclick, not a feature.
const BLOCK_BUILDABLE := {"house": true, "farm": true}

## Small drawn cursors (SVG), each with a fallback (TS CURSORS).
const CURSORS := {
	"attack": ["<svg xmlns='http://www.w3.org/2000/svg' width='28' height='28'><g stroke='#1a0d08' stroke-width='3' stroke-linecap='round'><path d='M4 4 L20 20 M20 14 L14 20'/></g><g stroke='#ff6a55' stroke-width='2' stroke-linecap='round'><path d='M4 4 L20 20 M20 14 L14 20'/></g><circle cx='4' cy='4' r='2' fill='#ffd2c8'/></svg>", 3, 3, "crosshair"],
	"gather": ["<svg xmlns='http://www.w3.org/2000/svg' width='28' height='28'><path d='M5 23 L17 11' stroke='#2a1a0c' stroke-width='5' stroke-linecap='round'/><path d='M5 23 L17 11' stroke='#b88a52' stroke-width='3' stroke-linecap='round'/><path d='M10 6 Q18 4 24 12' fill='none' stroke='#2a1a0c' stroke-width='5' stroke-linecap='round'/><path d='M10 6 Q18 4 24 12' fill='none' stroke='#d8dde2' stroke-width='3' stroke-linecap='round'/></svg>", 22, 10, "pointer"],
	"build": ["<svg xmlns='http://www.w3.org/2000/svg' width='28' height='28'><path d='M6 22 L16 12' stroke='#2a1a0c' stroke-width='5' stroke-linecap='round'/><path d='M6 22 L16 12' stroke='#b88a52' stroke-width='3' stroke-linecap='round'/><rect x='13' y='3' width='12' height='8' rx='1.5' transform='rotate(45 19 7)' fill='#9aa4b0' stroke='#2a1a0c' stroke-width='2'/></svg>", 20, 5, "pointer"],
}

const SCREENS := "res://src/ui/screens.gd"

## AppState: "menu" | "setup" | "armory" | "match" | "postmatch" | "codex" |
## "settings" | "warband" | "editor" | "factions" | "career" | "nemesis"

# ------------------------------------------------------------ the canvas --
var worldLayer: Node2D
var overlayLayer: Node2D
var uiLayer: Node2D
var ctx: LayerCtx  ## the interface layer (TS: renderer.ctx after the world pass)
var renderer: Renderer
var input := GameInput.new()
var camera := Camera.new()
var particles := Particles.new(2400)
var hud := HUD.new()
var profile: Profile
## The canvas size the game lays out in (TS canvas.width/height; a virtual
## resolution on small screens).
var canvasW := 1600
var canvasH := 900

var state := "menu"
var menu = null
var setup = null
var armory = null
var postmatch = null
var editorScreen = null
var codexScreen = null
var factionBook = null
var careerScreen = null
var rivalScreen = null
## The caster view, used whenever this client is watching rather than playing.
var caster := Caster.new()
## Display names per team for the caster (online names, or realm + AI level).
var playerNames: Array = []
## A replay being played back: {rec, byTick, endTick, ended}, or null.
var replay = null
## How an online match was set up, kept for its replay.
var netSetup = null
## Broadcast delay for an online caster, in ticks.
var casterDelayTicks := 0
## Where the Factions book returns to.
var factionsReturn := "menu"
var settingsScreen = null
var warbandScreen = null
var warband = null
var settings: Dictionary
var settingsReturn := "menu" # where Back from settings goes

# Match state
var world: World = null
var ais: Array = []
var config = null ## SkirmishConfig (Dictionary)
var selection: Array = []
var controlGroups: Array = []
var lastGroupTap := {"idx": -1, "time": 0.0}
var keybinds := Keybinds.KeybindResolver.new()
## Rolling frame/tick costs behind the performance overlay and auto-LOD.
var perf := {
	"frameMs": 16.7, "worstFrameMs": 0.0, "tickMs": 0.0, "worstTickMs": 0.0,
	"ticksThisFrame": 0, "ticksLastFrame": 1, "slipT": 0.0, "autoLod": false, "worstReset": 0.0,
	# Godot: the main thread's own CPU time per frame, and its parts.
	"cpuMs": 0.0, "renderMs": 0.0, "uiMs": 0.0, "skipped": 0,
}
## Where the last thing that happened to you happened (AoE's Space key).
var lastEvent = null
var markers: Array = []
var placing = null
var attackMoveArmed := false
var powerArmed := false # commander power placement mode
var ingameMenu := false
var spectating := false # watch mode: all teams are AI, no player commands
var showScoreboard := false # Tab — live multi-team scoreboard overlay
var showProduction := false # V — production overview panel
var weather := Weather.new()
# Multiplayer chat (Enter to open) + log.
var chatOpen := false
var chatDraft := ""
var chatLog: Array = []
## Time-series of per-team metrics, sampled through the match, for the graphs.
var matchHistory: Array = []
var nextSampleT := 0.0
## Aggregated (your alliance vs enemies) series, built at match end.
var endGraph = null
## The team this client controls/views. 0 for single-player & the net host;
## the net joiner sets it to their team.
var me := 0
var net: NetSession = null # active lockstep session in multiplayer
var netAccumulator := 0.0
var netDesyncAlerted := false
## This online match is ranked: report the winner to the server.
var netRanked := false
var resultSent := false
var netFactions: Array = []
## The hub an online match came from; the lobby reopens there after it.
var netHub := ""
var lobby = null
var paused := false
var gameSpeed := 1.0 # 0.5 / 1 / 2 / 3
var idleVillIndex := 0
var matchOverTimer := -1.0
var playerWon := false
var combatHeat := 0.0 # 0..1 battle intensity, drives combat music
var matchRewards = null
var matchWon := false
var xpBefore := 0.0
var levelsGained := 0
## The whole match, both sides, for the end-of-match report.
var endReport: Dictionary = {}
## Achievements and challenges this match earned, for the post-match screen.
var matchAwards: Array = []
## The map this match is being fought on, for the end-of-match report.
var mapName := ""
## Wheel delta this frame, for out-of-match screens that zoom.
var frameWheel := 0.0
## True when this match was launched from the editor, so we go back there.
var editorReturn := false
var endDuration := 0.0

# Frame-level input flags consumed by UI/world each frame.
var frameClick = null
var frameDouble = null
var frameRight = null
var frameDragEnd = null

var sfxCooldown := {}
var time := 0.0
var showDamageNumbers := true
var fps := 60.0 # smoothed frames-per-second for the optional overlay
var smokeTimer := 0.0
var accumulator := 0.0
## Every order this match has been given — half of what a save is.
var cmdLog := Savegame.CommandLog.new()
## Set while a resume is fast-forwarding, so the match loop stays paused.
var loadingSave := false

## What the cursor panel says while placing a building.
var placementInfo = null
var builderLines: Array = []
var middleLast = null
var renderHovered := -1
var cursorNow := ""
var popcapAlertAt := -99.0
## Where a replay is jumping to, run a slice at a time so the screen never freezes.
var replaySeek = null

# ---- threading (see the header) ----
## Step single-player simulation on a worker thread.
var threadSim := true
var _task := -1  ## WorkerThreadPool task id, or -1
var _taskSteps := 0
var _taskSeek := false
var _taskReplayEnded := false
var _taskTickCount := 0
var _taskWorstMs := 0.0
var _taskSumMs := 0.0
var _queued: Array = []  ## input events that arrived while the world was busy
var _rallyFrom: Array = []
var _lastGhost = null
var _lastDrag := {"active": false, "x0": 0.0, "y0": 0.0, "x1": 0.0, "y1": 0.0}
var _aiClass = null
var _aiWarned := false
var _cursorTex := {}
## Frame log for benchmarks: each frame's {dt, cpu, render, ui, tick, skipped}.
var frameLog: Array = []
var logFrames := false


# ================================================================== setup ==

func _ready() -> void:
	profile = Profile.load()
	settings = Settings.loadSettings()
	if OS.get_cmdline_user_args().has("--sim-sync"):
		threadSim = false
	worldLayer = Node2D.new()
	worldLayer.name = "WorldLayer"
	overlayLayer = Node2D.new()
	overlayLayer.name = "OverlayLayer"
	uiLayer = Node2D.new()
	uiLayer.name = "UILayer"
	add_child(worldLayer)
	add_child(overlayLayer)
	add_child(uiLayer)
	ctx = LayerCtx.new()
	renderer = Renderer.new(LayerCtx.new(), LayerCtx.new())
	for i in 10:
		controlGroups.append([])
	if ResourceLoader.exists("res://src/ai/skirmish_ai.gd"):
		_aiClass = load("res://src/ai/skirmish_ai.gd")
	_makeScreens()
	resize()
	get_viewport().size_changed.connect(resize)
	wireInput()
	applySettings()
	# Load any baked sprites (no-op if none generated yet); procedural art fills
	# in until each image is ready, so we don't block startup on it.
	var n := Sprites.loadSprites()
	if n > 0:
		print("[sprites] loaded %d baked models" % n)


## The other screens, each if it exists in this build (see the header).
func _makeScreens() -> void:
	menu = _newScreen(SCREENS, "MenuScreen")
	setup = _newScreen(SCREENS, "SetupScreen")
	armory = _newScreen(SCREENS, "ArmoryScreen")
	postmatch = _newScreen(SCREENS, "PostMatchScreen")
	editorScreen = _newScreen("res://src/ui/editor_screen.gd", "EditorScreen")
	codexScreen = _newScreen("res://src/ui/codex.gd", "CodexScreen")
	factionBook = _newScreen("res://src/ui/faction_book.gd", "FactionBook")
	careerScreen = _newScreen("res://src/ui/career_screen.gd", "CareerScreen")
	rivalScreen = _newScreen("res://src/ui/rival_screen.gd", "RivalScreen")
	settingsScreen = _newScreen("res://src/ui/settings_screen.gd", "SettingsScreen")
	warbandScreen = _newScreen("res://src/ui/warband_screen.gd", "WarbandScreen")
	lobby = _newScreen("res://src/ui/net_lobby.gd", "NetLobby")


## A module's class by its TS name: an inner class of that name if the file
## has one (screens.gd's MenuScreen), else the file's own class (codex.gd is
## CodexScreen). Null when the file isn't in this build.
static func _cls(path: String, name: String):
	if not ResourceLoader.exists(path):
		return null
	var s = load(path)
	if s == null or not (s is Script):
		return null
	var consts: Dictionary = s.get_script_constant_map()
	if consts.has(name):
		return consts[name]
	return s if (s as Script).can_instantiate() else null


static func _newScreen(path: String, name: String):
	var k = _cls(path, name)
	return k.new() if k != null else null


## A static function of the screens module, if it exists (setMouseDown).
static func _screensCall(fn: String, args: Array = []):
	var s = _module(SCREENS)
	if s == null or not s.has_method(fn):
		return null
	return s.callv(fn, args)


func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_FOCUS_OUT:
		input.handleBlur()
	elif what == NOTIFICATION_WM_CLOSE_REQUEST or what == NOTIFICATION_PREDELETE:
		_joinTask()


func resize() -> void:
	var vs := get_viewport().get_visible_rect().size
	var vw := maxf(1, vs.x)
	var vh := maxf(1, vs.y)
	var aspect := vw / vh
	var DESIGN := 760.0 # the UI's design space on its short axis
	var cw := vw
	var ch := vh
	# On small / mobile screens the fixed-layout UI needs more room than the
	# device gives it, so render at a virtual resolution (short side = DESIGN)
	# and scale the canvas down to fit. Input is mapped back accordingly.
	if minf(vw, vh) < DESIGN:
		if aspect >= 1:
			ch = DESIGN
			cw = JS.round(DESIGN * aspect)
		else:
			cw = DESIGN
			ch = JS.round(DESIGN / aspect)
	canvasW = int(cw)
	canvasH = int(ch)
	var k := Vector2(vw / cw, vh / ch)
	for l in [worldLayer, overlayLayer, uiLayer]:
		if l != null:
			l.scale = k
	input.canvasScale = Vector2(cw / vw, ch / vh)
	camera.setViewport(cw, ch)


# ============================================================ input wiring ==

func _input(event: InputEvent) -> void:
	# The world is busy on the sim thread: hold every event until it is ours
	# again (the next owning frame), in order.
	if _taskBusy():
		_queued.append(event)
		return
	_flushQueue()
	input.handleEvent(event)


func _flushQueue() -> void:
	if _queued.is_empty():
		return
	var q := _queued
	_queued = []
	for ev in q:
		input.handleEvent(ev)


func wireInput() -> void:
	input.onLeftClick = func(x: float, y: float) -> void:
		audio.resume()
		# Fullscreen has to be requested *here*, inside the click — by the time
		# the next frame interprets frameClick the gesture is over and a browser
		# refuses. The button drew its rect last frame; if the click is on it,
		# act now. The frame still sees the click land on the button, which is
		# what stops it falling through to the world underneath.
		if UI.ui.gestureAt(x, y) == FullscreenButton.FULLSCREEN_ZONE:
			Fullscreen.toggleFullscreen()
		frameClick = {"x": x, "y": y}
	input.onLeftDouble = func(x: float, y: float) -> void:
		frameDouble = {"x": x, "y": y}
		frameClick = {"x": x, "y": y} # double also counts as a click for UI
	input.onRightClick = func(x: float, y: float) -> void:
		frameRight = {"x": x, "y": y}
	input.onDragEnd = func(box: Dictionary) -> void:
		frameDragEnd = {"x0": box.x0, "y0": box.y0, "x1": box.x1, "y1": box.y1}
		# Outside a match, screens are tap/drag UIs (no box-select), so a drag's
		# release should also count as a click at the end point — that's what
		# makes touch (and mouse) drag-and-drop in Warband Tactics resolve on
		# release.
		if state != "match":
			frameClick = {"x": box.x1, "y": box.y1}
	input.onWheel = func(x: float, y: float, delta: float) -> void:
		if state == "match":
			camera.zoomAt(x, y, 0.88 if delta > 0 else 1.14)
		else:
			# Screens that zoom (the map editor) read this off the frame input.
			frameWheel = delta
	# Two-finger gestures: pan + pinch-zoom the match camera.
	input.onPan = func(dx: float, dy: float) -> void:
		if state == "match":
			camera.pan(-dx / camera.zoom, -dy / camera.zoom)
	input.onPinch = func(cx: float, cy: float, factor: float) -> void:
		if state == "match":
			camera.zoomAt(cx, cy, factor)
	input.onKeyDown = func(key: String) -> void: handleKey(key)


func _mods() -> Dictionary:
	return {"ctrl": input.ctrl, "shift": input.shift, "alt": input.alt}


func handleKey(key: String) -> void:
	# Fullscreen works on every screen, so it is checked before any screen
	# gets the key — except a rebind in progress, which owns every chord.
	if not (state == "settings" and settingsScreen != null and settingsScreen.isListening()):
		var fsChord := Keybinds.chordOf(key, _mods())
		if fsChord != "" and keybinds.resolve(settings.keybinds, fsChord) == "fullscreen":
			Fullscreen.toggleFullscreen()
			return
	# Warband Tactics has one typed field (the ground seed); give it first
	# refusal on every key while that screen is up.
	if state == "warband":
		if warbandScreen != null:
			warbandScreen.handleKey(key)
		return
	if state == "editor" and editorScreen != null and editorScreen.handleKey(key):
		return
	# The settings screen swallows everything while it is listening for a
	# rebind, so a player can bind Escape or Tab without triggering them.
	if state == "settings" and settingsScreen != null and settingsScreen.captureKey(key, input):
		return
	if state != "match" or world == null:
		return
	# Chat capture takes precedence over every game hotkey while typing.
	if chatOpen:
		handleChatKey(key)
		return
	# The caster's keys come first while watching (digits pick a player's vision).
	if spectating and not ingameMenu and caster.key(key, world):
		return

	var chord := Keybinds.chordOf(key, _mods())
	if chord == "":
		return # a bare modifier
	var action = keybinds.resolve(settings.keybinds, chord)

	# Control groups are positional rather than bound, exactly as they are in
	# Age of Empires: Ctrl sets, Shift adds, a bare digit selects, and tapping
	# the same digit twice takes the camera to the group.
	var digit := RegEx.create_from_string("^(?:Ctrl\\+|Shift\\+|Alt\\+)*([0-9])$").search(chord)
	if digit != null and not ingameMenu and not spectating:
		controlGroupKey(int(digit.get_string(1)))
		return

	if action == "menu":
		if hud.oathPicker.isOpen:
			hud.oathPicker.close()
			return
		if spectating:
			exitToMenu()
		elif placing != null:
			placing = null
		elif hud.buildMenuOpen:
			hud.buildMenuOpen = false
			hud.buildCategory = null
		elif powerArmed:
			powerArmed = false
		elif attackMoveArmed:
			attackMoveArmed = false
		else:
			ingameMenu = not ingameMenu
		return
	if action == "chat" and net != null:
		chatOpen = true
		return
	if ingameMenu:
		return

	# These work in every mode, spectating included.
	match action:
		"pause":
			togglePause()
			return
		"speedUp":
			cycleSpeed(1)
			return
		"speedDown":
			cycleSpeed(-1)
			return
		"scoreboard":
			showScoreboard = not showScoreboard
			return
		"productionPanel":
			showProduction = not showProduction
			return
		"perfOverlay":
			settings.perfOverlay = not settings.perfOverlay
			hud.addAlert("Performance overlay on" if settings.perfOverlay else "Performance overlay off")
			Settings.saveSettings(settings)
			return
		"cameraLastEvent":
			goToLastEvent()
			return
	if spectating:
		return # spectators only watch — no army commands

	match action:
		"attackMove": attackMoveArmed = true
		"stop": dispatch({"t": "stop", "team": me, "ids": _ids(playerSelection())})
		"hold": dispatch({"t": "hold", "team": me, "ids": _ids(playerSelection())})
		"ability": controller.useAbility()
		"buildMenu":
			if not playerSelection().any(func(e): return _canBuild(e)):
				hud.addAlert("Select a villager to build.")
			else:
				hud.buildMenuOpen = true
				hud.buildCategory = null
		"garrison": garrisonSelected()
		"cycleStance": cycleStance()
		"commanderPower": armCommanderPower()
		"deleteUnit": deleteSelection()
		"idleVillagerNext": selectIdleVillager(1)
		"idleVillagerPrev": selectIdleVillager(-1)
		"selectArmy": selectAllArmy()
		"selectTownCentre": selectTownCentre(true)
		"cameraTownCentre": selectTownCentre(false)


static func _ids(ents: Array) -> Array:
	return ents.map(func(e): return e.id)


static func _canBuild(e) -> bool:
	var u = C.UNITS.get(e.type)
	return u != null and u.get("canBuild", false)


static func _canGather(e) -> bool:
	var u = C.UNITS.get(e.type)
	return u != null and u.get("canGather", false)


## Ctrl sets, Shift adds, a bare digit selects; twice re-centres.
func controlGroupKey(idx: int) -> void:
	if world == null:
		return
	var live := func(ids: Array) -> Array:
		return ids.filter(func(id): return world.byId.has(id) and world.byId[id].alive)
	if input.ctrl or input.alt:
		controlGroups[idx] = _ids(playerSelection())
		hud.addAlert("Group %d set (%d)" % [idx, controlGroups[idx].size()])
		return
	if input.shift:
		var merged := {}
		for id in live.call(controlGroups[idx]):
			merged[id] = true
		for e in playerSelection():
			merged[e.id] = true
		controlGroups[idx] = merged.keys()
		hud.addAlert("Group %d now %d" % [idx, controlGroups[idx].size()])
		return
	var ids: Array = live.call(controlGroups[idx])
	if ids.is_empty():
		return
	var now := Time.get_ticks_msec() * 1.0
	select(ids)
	if lastGroupTap.idx == idx and now - lastGroupTap.time < 350:
		var first: Entity = world.byId[ids[0]]
		camera.centerOn(first.x, first.y)
	lastGroupTap = {"idx": idx, "time": now}


## Disband the selection — your own units and buildings only.
func deleteSelection() -> void:
	var ids := _ids(playerSelection())
	if ids.is_empty():
		return
	dispatch({"t": "delete", "team": me, "ids": ids})
	hud.addAlert("Disbanded %d" % ids.size())
	audio.play("command")


## H — the Town Centre, selected and centred (AoE's most-worn key).
func selectTownCentre(alsoSelect: bool) -> void:
	if world == null:
		return
	var tc = null
	for b in world.entitiesOf(me, T.Kind.Building):
		if b.type == "town_center":
			tc = b
			break
	if tc == null:
		hud.addAlert("No Town Centre")
		return
	if alsoSelect:
		select([tc.id])
	camera.centerOn(tc.x, tc.y)


## Space — jump to whatever last happened to you.
func goToLastEvent() -> void:
	if lastEvent == null:
		hud.addAlert("Nothing has happened yet")
		return
	camera.centerOn(lastEvent.x, lastEvent.y)


## Y — cycle the selected units' combat stance.
func cycleStance() -> void:
	var units := playerSelection().filter(func(e): return e.kind == T.Kind.Unit)
	if units.is_empty():
		return
	var nxt: int = (units[0].stance + 1) % 5
	dispatch({"t": "stance", "team": me, "ids": _ids(units), "stance": nxt})
	hud.addAlert("Stance: %s" % ["⚔ Aggressive", "🛡 Defensive", "⚑ Stand Ground", "✋ Passive", "🏹 Skirmish"][nxt])
	audio.play("command")


## Garrison (G): tuck the selected units into a building, or eject if only a
## building is selected. Targets the selected building when it can hold
## troops, otherwise the nearest friendly building with free space. Any unit
## can garrison — villagers included — so they can take cover from a raid.
func garrisonSelected() -> void:
	if world == null:
		return
	var sel := playerSelection()
	var units := sel.filter(func(e): return e.kind == T.Kind.Unit and e.team == me)
	var selBuilding = null
	for e in sel:
		if e.kind == T.Kind.Building and e.team == me:
			selBuilding = e
			break
	var cap := func(e: Entity) -> float:
		var b = C.BUILDINGS.get(e.type)
		return float(b.get("garrisonCap", 0)) if b != null else 0.0

	# Just a building selected (no troops to load) → eject its garrison.
	if selBuilding != null and units.is_empty():
		if cap.call(selBuilding) > 0 and selBuilding.garrison.size() > 0:
			world.ungarrison(selBuilding.id)
			hud.addAlert("Garrison ejected.")
			audio.play("command")
		return
	if units.is_empty():
		return

	# Prefer the selected building; else the nearest friendly one with room.
	var target = selBuilding if selBuilding != null and cap.call(selBuilding) > 0 else null
	if target == null:
		var cx := 0.0
		var cy := 0.0
		for u in units:
			cx += u.x
			cy += u.y
		cx /= units.size()
		cy /= units.size()
		var bestD := INF
		for e: Entity in world.entities:
			if not e.alive or e.kind != T.Kind.Building or e.team != me:
				continue
			if e.buildState != T.BuildState.Done or cap.call(e) <= 0 or e.garrison.size() >= cap.call(e):
				continue
			var d := JS.hypot(e.x - cx, e.y - cy)
			if d < bestD:
				bestD = d
				target = e
	if target == null:
		hud.addAlert("No garrison-capable building nearby.")
		return
	dispatch({"t": "garrison", "team": me, "ids": _ids(units), "buildingId": target.id})
	var tb = C.BUILDINGS.get(target.type)
	hud.addAlert("Garrisoning into %s…" % (tb.name if tb != null else "building"))
	audio.play("command")


# =============================================================== selection ==

func playerSelection() -> Array:
	if world == null:
		return []
	var out: Array = []
	for id in selection:
		var e = world.byId.get(id)
		if e != null and e.alive and e.team == me:
			out.append(e)
	return out


## Everything currently selected, any team — so you can inspect (e.g. click
## an enemy unit to read its health/stats). Commands still use playerSelection.
func selectedEntities() -> Array:
	if world == null:
		return []
	var out: Array = []
	for id in selection:
		var e = world.byId.get(id)
		if e != null and e.alive:
			out.append(e)
	return out


func select(ids: Array) -> void:
	if world == null:
		return
	for id in selection:
		var e = world.byId.get(id)
		if e != null:
			e.selected = false
	selection = ids
	for id in ids:
		var e = world.byId.get(id)
		if e != null:
			e.selected = true
	if not ids.is_empty():
		audio.play("select")
	renderer.refreshSelection(world)


# ============================================================= match setup ==

func _difficulty(id) -> Dictionary:
	var d = C.DIFFICULTIES.get(str(id)) if id != null else null
	return d if d != null else C.DIFFICULTIES.get("knight", {})


func _newAI(w: World, team: int, diff: Dictionary):
	if _aiClass == null:
		if not _aiWarned:
			_aiWarned = true
			push_warning("SkirmishAI isn't in this build: computer players stand idle.")
		return null
	return _aiClass.new(w, team, diff)


## A 1 v 1 against your Nemesis, on a random battlefield, at the strength of
## its rank, playing what it has learned about you. Everything it needs goes
## into the config, so a save or a replay rebuilds the same rival.
func startRivalMatch() -> void:
	var pf = profile.playableFaction()
	var mine: String = pf if pf != null else C.DEFAULT_FACTION
	var r: Dictionary = Rival.loadRival(mine).current
	var mem := Rival.memoryOf(r, Career.careerLog())
	var seed := randi() & 0x7fffffff
	var maps: Array = C.PRESETS.filter(func(p): return not ["islands", "survival_arena"].has(p.id))
	var rd: Array = Rival.RANK_DIFFICULTY
	var rank := int(r.rank)
	var diff: String = rd[rank] if rank >= 0 and rank < rd.size() and rd[rank] != null else "knight"
	startMatch({
		"presetId": maps[seed % maps.size()].id, "seed": seed, "difficulty": diff, "aiDifficulties": ["", diff], "fairMode": false,
		"players": 2, "teams": [0, 0], "commander": "", "faction": mine, "aiFactions": ["", r.faction], "nomad": false, "mode": "conquest",
		"rival": {"name": r.name, "epithet": r.epithet, "rank": r.rank, "adaptation": Rival.adaptationFor(r, mem)},
	})
	# It speaks first — the proof that it remembers.
	addChatLine("%s %s" % [r.name, r.epithet], Rival.taunt(r, mem), 1)


## The defaults the setup screen starts from (screens.ts SetupScreen.config).
static func defaultConfig() -> Dictionary:
	return {
		"presetId": "open_plains", "seed": RNG.randomSeed(), "difficulty": "knight", "aiDifficulties": [],
		"fairMode": false, "players": 2, "teams": [0, 0], "commander": "", "faction": "", "aiFactions": [],
		"nomad": false, "mode": "conquest",
	}


func _alliancesFor(cfg: Dictionary, numPlayers: int, mode: String, hordeTeam: int):
	# Alliances: survival = all players vs the horde; teams = as chosen on the
	# roster, seat by seat (ui/teams.ts); else FFA.
	if mode == "survival":
		var a: Array = []
		for t in numPlayers:
			a.append(1 if t == hordeTeam else 0)
		return a
	var teams: Array = cfg.get("teams", [])
	if teams.any(func(t): return int(t) > 0):
		return alliancesFor(resizeTeams(teams, numPlayers))
	return null


## ui/teams.ts alliancesFor: the sim's alliance id per seat. Seats on the same
## numbered team share one; each solo seat gets one of its own, numbered after
## the teams. (Through ui/teams.gd when it is in this build.)
static func alliancesFor(teams: Array) -> Array:
	var tm = _module("res://src/ui/teams.gd")
	if tm != null and tm.has_method("alliancesFor"):
		return tm.alliancesFor(teams)
	var nxt := 8 # teams.ts MAX_TEAMS
	var out: Array = []
	for t in teams:
		if int(t) >= 1:
			out.append(int(t) - 1)
		else:
			out.append(nxt)
			nxt += 1
	return out


## ui/teams.ts resizeTeams: resize to `players` seats, keeping what can be kept.
static func resizeTeams(teams: Array, players: int) -> Array:
	var tm = _module("res://src/ui/teams.gd")
	if tm != null and tm.has_method("resizeTeams"):
		return tm.resizeTeams(teams, players)
	var out := teams.slice(0, players)
	while out.size() < players:
		out.append(0)
	return out


static func _module(path: String):
	return load(path) if ResourceLoader.exists(path) else null


func startMatch(cfg: Dictionary) -> void:
	_joinTask()
	config = cfg
	# Each bot can run a different personality; fall back to the default.
	var aid: Array = cfg.get("aiDifficulties", [])
	var diffFor := func(t: int) -> Dictionary:
		var id = aid[t] if t < aid.size() and str(aid[t]) != "" else cfg.difficulty
		var d = C.DIFFICULTIES.get(id)
		return d if d != null else _difficulty(cfg.difficulty)
	var diff := _difficulty(cfg.difficulty)
	var mode: String = cfg.get("mode", "conquest")
	# Survival is co-op: the chosen player count is your side (all allied),
	# and one extra "horde" team is appended for the waves.
	var side := clampi(int(cfg.get("players", 2)), 2, T.MAX_TEAMS - 1 if mode == "survival" else T.MAX_TEAMS)
	var numPlayers := side + 1 if mode == "survival" else side
	var hordeTeam := side if mode == "survival" else -1
	var alliances = _alliancesFor(cfg, numPlayers, mode, hordeTeam)
	var map := resolveMap(cfg.presetId, int(cfg.seed), numPlayers, cfg.get("nomad", false), alliances)
	var w := World.new(int(cfg.seed))
	# Team 0 is the human; the rest are AI (allies or opponents), plus the
	# horde. The human's unit rarities and boons are part of the match:
	# written into the config, so a save or a replay rebuilds exactly what was
	# played even after the collection changes.
	var replaying: bool = loadingSave and cfg.get("humanLoadout") != null
	var loadouts: Array = [(cfg.humanLoadout as Dictionary).duplicate() if replaying else profile.matchLoadout(cfg.get("fairMode", false))]
	var econMults: Array = [1.0]
	var commanders: Array = [cfg.commander if str(cfg.get("commander", "")) != "" else profile.data.commander]
	var boonLoadouts: Array = [(cfg.get("humanBoons", []) as Array).duplicate() if replaying else ([] if cfg.get("fairMode", false) else profile.equippedBoonPlan())]
	cfg.humanLoadout = (loadouts[0] as Dictionary).duplicate()
	cfg.humanBoons = (boonLoadouts[0] as Array).duplicate()
	cfg.commander = commanders[0]
	var setupRng := RNG.new(int(cfg.seed) ^ 0x5eed)
	var cids: Array = C.COMMANDER_IDS
	for t in range(1, numPlayers):
		loadouts.append(profile.matchLoadout(true))
		econMults.append(float(diff.econMult) if t == hordeTeam else float(diffFor.call(t).econMult))
		# Seeded, not Math.random(). A match has to be reproducible from its
		# config alone — that is what makes a save a seed plus a command log
		# rather than a dump of every entity — and an AI that drew a different
		# commander on reload would fight a different battle.
		commanders.append(cids[setupRng.int_(0, cids.size() - 1)])
		boonLoadouts.append([])
	# Resolved once and written back, so a save (which stores this config)
	# rebuilds exactly these factions even if the profile's pick changes.
	var factions := factionsFor(cfg, numPlayers, true)
	cfg.faction = factions[0]
	var aif: Array = []
	for t in factions.size():
		aif.append("" if t == 0 else factions[t])
	cfg.aiFactions = aif
	w.init(map, loadouts, econMults, alliances, commanders, cfg.get("nomad", false), boonLoadouts, mode, factions)
	world = w
	ais = []
	for t in range(1, numPlayers):
		if t == hordeTeam:
			continue # the horde has no brain — the sim spawns its waves
		var ai = _newAI(w, t, diffFor.call(t))
		if ai == null:
			continue
		# Your Nemesis plays what it has learned about you.
		if cfg.get("rival") != null and t == 1 and ai.has_method("adapt"):
			ai.adapt(cfg.rival.adaptation)
		ais.append(ai)
	playerNames = []
	for t in numPlayers:
		if t == 0:
			playerNames.append(profile.data.name)
		elif t == hordeTeam:
			playerNames.append("The Horde")
		elif cfg.get("rival") != null and t == 1:
			playerNames.append("%s %s" % [cfg.rival.name, cfg.rival.epithet])
		else:
			playerNames.append("%s · %s" % [C.PAL.teams[t % C.PAL.teams.size()].name, diffFor.call(t).name])
	replay = null
	_prepareMatchView(map)
	selection = []
	controlGroups = []
	for i in 10:
		controlGroups.append([])
	markers = []
	placing = null
	attackMoveArmed = false
	ingameMenu = false
	matchOverTimer = -1
	matchRewards = null
	camera.setWorld(map.worldW, map.worldH)
	camera.zoom = 1
	camera.centerOn(map.starts[me].x, map.starts[me].y)
	accumulator = 0
	spectating = false
	cmdLog.clear()
	resetMatchTelemetry()
	endNet()
	me = T.Team.Player
	state = "match"
	_prewarm()
	if cfg.get("rival") != null:
		hud.addAlert("%s — your Nemesis, %s %s, awaits." % [map.name, cfg.rival.name, cfg.rival.epithet])
	else:
		hud.addAlert("%s — vs %s. Your villagers await orders!" % [map.name, diff.get("name", "")])
	audio.play("complete")


## Everything a new match's view needs: the renderer's map, weather, HUD.
func _prepareMatchView(map: Dictionary) -> void:
	renderer.prepare(map)
	mapName = map.name
	weather.configure(int(map.seed), map.name)
	renderer.clearFx()
	hud.prepare(map)
	particles.clear()


## Draw the standing poses of every unit the realms can field ahead of time
## (Godot: the art cache's warm-up), so the first look at an army doesn't
## draw it.
func _prewarm() -> void:
	if world == null:
		return
	var teams: Array = []
	for t in world.numTeams:
		teams.append(t)
	renderer.snap.factions.resize(world.numTeams)
	renderer.snap.numTeams = world.numTeams
	for t in world.numTeams:
		renderer.snap.factions[t] = world.player(t).faction
	Draw.setFactionResolver(renderer._factionResolver)
	ArtCache.prewarmUnits(["villager", "militia", "spearman", "archer", "scout"], teams, camera.zoom)


## Write the match out: its setup, every order given, and a hash of where the
## sim actually is. Custom maps travel as a share code rather than an id,
## since the map library can be edited or emptied between save and load.
func saveMatch(label = null) -> bool:
	var w := world
	if w == null or config == null:
		return false
	if net != null:
		# Resuming one side of a lockstep match would desync everyone else.
		hud.addAlert("Multiplayer matches can't be saved.")
		return false
	var custom = CustomMaps.findCustomMap(config.presetId) if (config.presetId as String).begins_with("custom_") else null
	var save := {
		"version": Savegame.SAVE_FORMAT_VERSION,
		"savedAt": JS.now_ms(),
		"label": label if label != null else "%s — %s" % [mapName if mapName != "" else "Skirmish", mmss(w.time)],
		"setup": config.duplicate(true),
		"tick": w.tickCount,
		"commands": cmdLog.entries.duplicate(true),
		"checksum": Commands.worldChecksum(w),
		"summary": {
			"mapName": mapName if mapName != "" else "Skirmish",
			"mode": config.get("mode", "conquest"),
			"players": config.get("players", 2),
			"difficulty": config.get("difficulty", "knight"),
			"elapsed": w.time,
		},
	}
	if custom != null:
		save.mapCode = CustomMaps.serialiseMap(custom)
	var ok := Savegame.writeSave(save)
	hud.addAlert("Saved — %s" % save.label if ok else "Couldn't save (storage full?)")
	audio.play("complete" if ok else "ui")
	return ok


## Seconds as m:ss — a match clock reads as a duration, not a number.
static func mmss(s: float) -> String:
	return "%d:%02d" % [floori(s / 60), floori(fmod(s, 60))]


## Rebuild a saved match by replaying it.
##
## The setup is re-run through the normal startMatch path — same seed, same
## world, same AI — and then the order log is fast-forwarded through it. If
## the result doesn't hash to what was saved, say so *now*: a divergence is
## only recoverable while the player still knows the game isn't the one they
## left, and playing an hour on top of a wrong resume is the bad outcome.
func loadMatch(save: Dictionary) -> bool:
	# A custom map may have been edited or deleted since; the save carries its
	# own copy, so restore that first and point the config at it.
	var stp: Dictionary = (save.setup as Dictionary).duplicate(true)
	if save.get("mapCode") != null:
		var m = CustomMaps.deserialiseMap(save.mapCode)
		if m == null:
			hud.addAlert("That save's map couldn't be read.")
			return false
		m.id = stp.presetId # keep the id the save was taken against
		m.erase("published") # keep whether the library had it published
		CustomMaps.saveCustomMap(m)
	loadingSave = true
	startMatch(stp)
	var w := world
	if w == null:
		loadingSave = false
		return false
	var res := Savegame.replayTo(w, ais, save.commands, int(save.tick), save.get("checksum"))
	cmdLog.load(save.commands)
	loadingSave = false
	accumulator = 0
	camera.centerOn(w.map.starts[me].x, w.map.starts[me].y)
	if not res.faithful:
		hud.addAlert("This save was made by a different version — the match may differ.")
	else:
		hud.addAlert("Resumed at %s." % mmss(w.time))
	return true


## Tear down any active net session/link (called when leaving a net match).
func endNet() -> void:
	if net != null:
		if net.transport != null and net.transport.has_method("close"):
			net.transport.close()
		net = null
	netDesyncAlerted = false


## Start a networked match (server-relayed 2–16 players, or serverless 1v1)
## once the lobby has connected. Every client builds the identical world from
## the shared seed (fair all-Common, no commanders/boons, given alliances) and
## drives it under lockstep. `start` is the lobby's NetStart Dictionary.
func startNetMatch(start: Dictionary) -> void:
	_joinTask()
	var transport = start.transport
	var localTeam: int = start.localTeam
	var teams: Array = start.teams
	var alliances: Array = start.alliances
	var seed: int = int(start.seed)
	var numTeams: int = int(start.numTeams)
	var smap = start.get("map")
	# The host's battlefield: a published map travels as its code, so every
	# client builds it identically even if nobody else has it saved.
	var custom = CustomMaps.deserialiseMap(smap.code) if smap != null and smap.get("code") != null else null
	var map: Dictionary
	if custom != null:
		map = CustomMaps.toMapData(custom, seed, numTeams, false)
	else:
		var id: String = smap.id if smap != null and smap.get("id") != null else ""
		var known: bool = C.PRESETS.any(func(p): return p.id == id) or id == "random"
		map = MapGen.generateMap(id if known else "open_plains", seed, numTeams, false, alliances)
	var w := World.new(seed)
	var loadouts: Array = teams.map(func(_t): return profile.matchLoadout(true))
	var econMults: Array = teams.map(func(_t): return 1.0)
	var commanders: Array = teams.map(func(_t): return "")
	# Each realm's chosen faction; anyone who didn't choose gets one from the seed.
	var rng := RNG.new(JS.u32(seed ^ 0xfac7105))
	var fids: Array = C.FACTION_IDS
	var sf: Array = start.get("factions", [])
	var factions: Array = []
	for t in teams.size():
		var roll: String = fids[rng.int_(0, fids.size() - 1)]
		var f = sf[t] if t < sf.size() else null
		factions.append(f if f != null and C.FACTIONS.has(f) else roll)
	w.init(map, loadouts, econMults, alliances, commanders, false, null, "conquest", factions)
	# Tell the server which factions were really played ("Random" resolved), for its stats.
	netFactions = factions
	transport.send({"t": "factions", "list": factions})
	var observer: bool = start.get("observer", false)
	if observer:
		w.revealAll = true # casters see the whole board
	world = w
	ais = []
	net = NetSession.new(transport, localTeam, teams, observer)
	me = localTeam
	spectating = observer # no commands, full vision, caster HUD
	net.attach(w, 5)
	# Every order the lockstep applies is kept: that is the match's replay.
	if net.lock != null:
		net.lock.record = []
	var sm := {"id": smap.id if smap != null and smap.get("id") != null else "open_plains", "name": map.name}
	if smap != null and smap.get("code") != null:
		sm.code = smap.code
	netSetup = {"seed": seed, "numTeams": numTeams, "alliances": alliances.duplicate(), "factions": factions.duplicate(), "map": sm}
	var names: Array = start.get("names", [])
	playerNames = []
	for t in teams.size():
		playerNames.append(names[t] if t < names.size() and str(names[t]) != "" else C.PAL.teams[t % C.PAL.teams.size()].name)
	casterDelayTicks = int(JS.round(float(start.get("delaySec", 0)) * C.SIM_HZ)) if observer else 0
	caster.reset()
	caster.delaySec = float(start.get("delaySec", 0)) if observer else 0.0
	replay = null
	net.onChat = func(m) -> void:
		if str(m.get("text", "")) != "":
			var tm := int(m.get("team", 0))
			addChatLine(m.get("name", "") if str(m.get("name", "")) != "" else Spectator.teamLabel(tm), m.text, tm)
	net.onPing = func(m) -> void: remotePing(float(m.get("x", 0)), float(m.get("y", 0)), int(m.get("team", 0)))
	net.onAnnounce = func(text) -> void: hud.addAlert("📣 %s" % text)
	net.onRated = func(m) -> void:
		var won: bool = m.get("winner") != null and w.alliances[me] == m.winner
		hud.addAlert("⚖ Ranked result recorded — rating %s%s" % ["+" if won else "−", JS.num(float(m.get("delta", 0)))])
	netRanked = start.get("ranked", false)
	netHub = str(start.get("online", ""))
	resultSent = false
	transport.onClose = func() -> void: hud.addAlert("⚠ Connection lost.")
	_prepareMatchView(map)
	selection = []
	controlGroups = []
	for i in 10:
		controlGroups.append([])
	markers = []
	placing = null
	attackMoveArmed = false
	powerArmed = false
	paused = false
	gameSpeed = 1
	ingameMenu = false
	matchOverTimer = -1
	matchRewards = null
	netAccumulator = 0
	netDesyncAlerted = false
	resetMatchTelemetry()
	camera.setWorld(map.worldW, map.worldH)
	camera.zoom = 1
	camera.centerOn(map.worldW / 2 if observer else map.starts[me].x, map.worldH / 2 if observer else map.starts[me].y)
	state = "match"
	_prewarm()
	hud.addAlert("👁 Observing — the match is underway." if observer else "🔗 Connected — good luck!")
	audio.play("complete")


## Watch mode: every team is AI-controlled and the whole map is revealed. We
## reuse the setup config (map, players, difficulty, alliances) but give team
## 0 a brain too and award no rewards — it's purely for watching.
func startSpectate(cfg: Dictionary) -> void:
	_joinTask()
	config = cfg
	var aid: Array = cfg.get("aiDifficulties", [])
	var diffFor := func(t: int) -> Dictionary:
		var id = aid[t] if t < aid.size() and str(aid[t]) != "" else cfg.difficulty
		var d = C.DIFFICULTIES.get(id)
		return d if d != null else _difficulty(cfg.difficulty)
	var mode: String = cfg.get("mode", "conquest")
	# Mirror startMatch's mode setup so Watch mode honours KotH / Regicide /
	# Survival (previously spectate always fell back to Conquest).
	var side := clampi(int(cfg.get("players", 2)), 2, T.MAX_TEAMS - 1 if mode == "survival" else T.MAX_TEAMS)
	var numPlayers := side + 1 if mode == "survival" else side
	var hordeTeam := side if mode == "survival" else -1
	var alliances = _alliancesFor(cfg, numPlayers, mode, hordeTeam)
	var map := resolveMap(cfg.presetId, int(cfg.seed), numPlayers, cfg.get("nomad", false), alliances)
	var w := World.new(int(cfg.seed))
	var loadouts: Array = []
	var econMults: Array = []
	var commanders: Array = []
	var spectateRng := RNG.new(int(cfg.seed) ^ 0x5eed)
	var cids: Array = C.COMMANDER_IDS
	for t in numPlayers:
		loadouts.append(profile.matchLoadout(true)) # fair, all-Common loadouts
		econMults.append(1.0 if t == hordeTeam else float(diffFor.call(t).econMult))
		# Seeded, so a watched game can be replayed exactly.
		commanders.append(cids[spectateRng.int_(0, cids.size() - 1)])
	var factions := factionsFor(cfg, commanders.size(), false)
	cfg.aiFactions = factions
	w.init(map, loadouts, econMults, alliances, commanders, cfg.get("nomad", false), null, mode, factions)
	w.revealAll = true # spectators see the entire battlefield
	world = w
	ais = []
	for t in numPlayers:
		if t == hordeTeam:
			continue # the horde is sim-driven, no brain
		var ai = _newAI(w, t, diffFor.call(t))
		if ai != null:
			ais.append(ai)
	_prepareMatchView(map)
	selection = []
	controlGroups = []
	for i in 10:
		controlGroups.append([])
	markers = []
	placing = null
	attackMoveArmed = false
	ingameMenu = false
	matchOverTimer = -1
	matchRewards = null
	camera.setWorld(map.worldW, map.worldH)
	camera.zoom = 0.85
	camera.centerOn(map.worldW / 2, map.worldH / 2)
	accumulator = 0
	spectating = true
	resetMatchTelemetry()
	endNet()
	me = T.Team.Player
	state = "match"
	playerNames = []
	for t in numPlayers:
		playerNames.append("The Horde" if t == hordeTeam else "%s · %s" % [C.PAL.teams[t % C.PAL.teams.size()].name, diffFor.call(t).name])
	caster.reset()
	_prewarm()
	hud.addAlert("👁 Casting — %s, %d AI realms. Press ? for caster keys." % [map.name, numPlayers])
	audio.play("complete")


## The HUD acts through this controller (TS MatchController).
class Controller extends RefCounted:
	var app

	func _init(a) -> void:
		app = a

	func trainUnit(b: Entity, type: String) -> void:
		# Queue the unit in every selected production building of this type
		# (so double-click-select all your stables, then mass-produce in one
		# click).
		var sameType: Array = app.playerSelection().filter(func(e): return e.kind == T.Kind.Building and e.type == b.type)
		var targets: Array = sameType if not sameType.is_empty() else [b]
		for bld in targets:
			app.dispatch({"t": "train", "team": app.me, "buildingId": bld.id, "unit": type})
		app.audio_play("ui")

	func research(b: Entity, techId: String) -> void:
		app.dispatch({"t": "research", "team": app.me, "buildingId": b.id, "tech": techId})
		app.audio_play("ui")

	func startPlacement(type: String) -> void:
		app.placing = type
		app.audio_play("ui")

	func ungarrison(b: Entity) -> void:
		app.dispatch({"t": "ungarrison", "team": app.me, "buildingId": b.id})
		app.audio_play("command")

	func toggleGate(b: Entity) -> void:
		app.dispatch({"t": "gate", "team": app.me, "buildingId": b.id})
		app.audio_play("build")

	func setAutoReseed(on: bool) -> void:
		app.dispatch({"t": "autoreseed", "team": app.me, "on": on})
		app.audio_play("ui")

	func trade(action: String) -> void:
		app.dispatch({"t": "trade", "team": app.me, "action": action})
		app.audio_play("coin")

	func stopSelection() -> void:
		app.dispatch({"t": "stop", "team": app.me, "ids": app._ids(app.playerSelection())})
		app.audio_play("command")

	func holdSelection() -> void:
		app.dispatch({"t": "hold", "team": app.me, "ids": app._ids(app.playerSelection())})
		app.audio_play("command")

	func setStance(stance: int) -> void:
		var ids: Array = app._ids(app.playerSelection().filter(func(e): return e.kind == T.Kind.Unit))
		if ids.is_empty():
			return
		app.dispatch({"t": "stance", "team": app.me, "ids": ids, "stance": stance})
		app.audio_play("command")

	func setAttackMoveMode() -> void:
		app.attackMoveArmed = true

	func garrisonSelection() -> void:
		app.garrisonSelected()

	func useAbility() -> void:
		var ids: Array = app._ids(app.playerSelection().filter(func(e): return e.kind == T.Kind.Unit))
		if not ids.is_empty():
			app.dispatch({"t": "ability", "team": app.me, "ids": ids})
		app.audio_play("command")

	func minimapNavigate(wx: float, wy: float) -> void:
		app.camera.centerOn(wx, wy)

	func minimapCommand(wx: float, wy: float) -> void:
		app.issueContextCommand(wx, wy, null)

	func minimapPing(wx: float, wy: float) -> void:
		app.dropPing(wx, wy)

	func cancelProduction(b: Entity, index: int) -> void:
		app.dispatch({"t": "cancel", "team": app.me, "buildingId": b.id, "index": index})
		app.audio_play("ui")

	func jumpTo(x: float, y: float) -> void:
		app.camera.centerOn(x, y)
		app.audio_play("ui")

	func narrowSelection(type: String, remove: bool) -> void:
		var keep: Array = app.selectedEntities().filter(func(e): return e.type != type if remove else e.type == type)
		if not keep.is_empty():
			app.select(app._ids(keep))
		app.audio_play("select")

	func openMenu() -> void:
		app.ingameMenu = true


var controller := Controller.new(self)


func audio_play(n: String) -> void:
	audio.play(n)


# ======================================================= world interaction ==

## Snapped, de-duplicated tile centres along a drag, for wall painting/preview.
func wallLinePoints(wx0: float, wy0: float, wx1: float, wy1: float) -> Array:
	return WallLine.wallLinePoints(wx0, wy0, wx1, wy1, C.TILE)


## Where a drag would put buildings, whichever kind is being placed: a
## gap-free run for walls, a spaced grid for everything else. One function so
## the ghost preview and the actual placement can never disagree.
func dragPlacements(type: String, wx0: float, wy0: float, wx1: float, wy1: float) -> Array:
	if LINE_BUILDABLE.has(type):
		return WallLine.wallLinePoints(wx0, wy0, wx1, wy1, C.TILE)
	if BLOCK_BUILDABLE.has(type):
		var bd = C.BUILDINGS.get(type)
		return WallLine.blockPoints(wx0, wy0, wx1, wy1, C.TILE, float(bd.tiles) if bd != null else 2.0)
	# Everything else: a drag is just a click that wandered. Place one, where
	# the button came up.
	return [{"x": wx1, "y": wy1}]


## The fullscreen hotkey as the player has it bound, for tooltips.
func fullscreenKeyLabel() -> String:
	return Keybinds.chordLabel(Keybinds.chordFor(settings.keybinds, "fullscreen"))


## Funnel every player action through here: queued for lockstep in a net
## game, applied immediately in single-player. Keeps both paths in one place.
## (Only ever called while this thread owns the world — see the header.)
func dispatch(cmd: Dictionary) -> void:
	if net != null and net.lock != null:
		net.lock.localCommand(cmd)
	elif world != null:
		# Log before applying, stamped with the tick it will land on. A save is
		# the seed plus this log, so anything that reaches the world and isn't
		# here is a divergence waiting to happen on reload.
		cmdLog.record(world.tickCount, cmd)
		Commands.applyCommand(world, cmd)


## Drag-release while a building is selected: lay the whole run at once — a
## line of wall, or a block of houses/farms.
func paintWallLine(box: Dictionary) -> void:
	if world == null or placing == null:
		return
	var pts := dragPlacements(placing,
		camera.screenToWorldX(box.x0), camera.screenToWorldY(box.y0),
		camera.screenToWorldX(box.x1), camera.screenToWorldY(box.y1))
	var villagers := playerSelection().filter(func(e): return _canBuild(e))
	var w := world
	var cost := w.buildingCostFor(me, placing)
	var budget: Dictionary = w.player(me).resources.duplicate()
	var placed := 0
	var lastProblem := ""
	for pt in pts:
		var problem = w.placementProblem(me, placing, pt.x, pt.y, true)
		if problem == null and not w.canAfford(budget, cost):
			problem = "Out of resources"
		if problem != null:
			lastProblem = problem
			continue
		var v = villagers[placed % maxi(1, villagers.size())] if not villagers.is_empty() else null
		dispatch({"t": "place", "team": me, "building": placing, "x": pt.x, "y": pt.y, "builders": [v.id] if v != null else []})
		budget = {"food": budget.food - cost.food, "wood": budget.wood - cost.wood, "gold": budget.gold - cost.gold}
		placed += 1
	if placed > 0:
		audio.play("build")
		if placed < pts.size():
			hud.addAlert("Placed %d of %d — %s." % [placed, pts.size(), lastProblem.to_lower()])
	else:
		hud.addAlert("Can't build there — %s." % (lastProblem.to_lower() if lastProblem != "" else "nothing fits"))
		audio.play("tick")
	# Walls stay armed. Laying a wall means laying several runs — around a
	# corner, along a ridge, across a gap — and having to reopen Build →
	# Defense → Palisade between every one of them was the whole reason this
	# felt like work. Right-click or Escape puts the cursor down.
	if not LINE_BUILDABLE.has(placing) and not input.shift:
		placing = null


## A clickable row of control-group chips above the minimap (number + live count).
func drawControlGroups(W: float, H: float) -> void:
	if world == null:
		return
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	var chipW := 34.0
	var chipH := 22.0
	var y := H - HUD.MINIMAP_SIZE - 10 - chipH - 6
	var x := 12.0
	var selSet := {}
	for id in selection:
		selSet[id] = true
	for g in range(1, 10):
		var live: Array = controlGroups[g].filter(func(id): return world.byId.has(id) and world.byId[id].alive)
		if live.is_empty():
			continue
		var hover := ui.mx >= x and ui.mx <= x + chipW and ui.my >= y and ui.my <= y + chipH
		# A chip is "active" if its members are exactly the current selection.
		var active := live.size() == selSet.size() and live.all(func(id): return selSet.has(id))
		ctx.fillStyle = Palette.withAlpha(PAL.uiAccent, 0.85) if active else ("rgba(40,32,20,0.95)" if hover else "rgba(20,16,10,0.85)")
		ctx.strokeStyle = Palette.withAlpha(PAL.uiAccent, 1.0 if active else 0.4)
		ctx.lineWidth = 1
		ctx.fillRect(x, y, chipW, chipH)
		ctx.strokeRect(x, y, chipW, chipH)
		ctx.fillStyle = "#1a1208" if active else PAL.uiAccent
		ctx.font = "bold 12px sans-serif"
		ctx.textAlign = "left"
		ctx.fillText(str(g), x + 5, y + 15)
		ctx.fillStyle = "#1a1208" if active else "#e7ddc4"
		ctx.font = "11px sans-serif"
		ctx.textAlign = "right"
		ctx.fillText(str(live.size()), x + chipW - 5, y + 15)
		if hover and frameClick != null and not ui.pointerConsumed:
			ui.pointerConsumed = true
			select(live)
		x += chipW + 4
	ctx.textAlign = "left"


## Interface scale, clamped. 1 = the canvas's own pixels.
func uiScale() -> float:
	var s := float(settings.get("uiScale", 1))
	return clampf(s if s != 0 else 1.0, 0.8, 1.5)


## Push the current settings into the live engine systems. Called at boot and
## every frame the settings screen is open (for instant preview).
func applySettings() -> void:
	var s := settings
	audio.masterVol = s.masterVol
	audio.sfxVol = s.sfxVol
	audio.musicVol = s.musicVol
	audio.muted = s.muted
	audio.applyVolumes()
	Palette.setColorblindTeams(s.colorblind)
	particles.density = 0.4 if s.reduceEffects else 1.0
	renderer.aggressiveLod = s.reduceEffects or perf.autoLod
	showDamageNumbers = s.damageNumbers


func resetMatchTelemetry() -> void:
	matchHistory = []
	nextSampleT = 0
	endGraph = null
	showScoreboard = false
	showProduction = false
	chatOpen = false
	chatDraft = ""
	chatLog = []


## Sample every team's metrics at a fixed game-time cadence for the graphs.
func sampleHistory(w: World) -> void:
	if w.time < nextSampleT:
		return
	nextSampleT = w.time + 4 # every 4s of sim time
	matchHistory.append({"t": w.time, "m": Metrics.snapshotMetrics(w)})


func openSettings(returnTo: String) -> void:
	settingsReturn = returnTo
	state = "settings"
	audio.play("ui")


# ------------------------------------------------------------ QoL controls --
func togglePause() -> void:
	paused = not paused
	hud.addAlert("⏸ Paused" if paused else "▶ Resumed")


func setSpeed(v: float) -> void:
	gameSpeed = v
	paused = false
	if not spectating:
		hud.addAlert("Speed %s×" % JS.num(v)) # the caster bar shows it


func cycleSpeed(dir: int) -> void:
	# Replays and AI games can run far faster than a game you're playing.
	var speeds: Array = [0.5, 1.0, 2.0, 4.0, 8.0, 16.0] if spectating and net == null else [0.5, 1.0, 2.0, 3.0]
	if paused:
		paused = false
		return
	var i := clampi(speeds.find(gameSpeed) + dir, 0, speeds.size() - 1)
	setSpeed(speeds[i])


## Cycle the camera through idle villagers, selecting each in turn. `dir` is
## +1 for "." and −1 for "," — the same pair Age of Empires uses, and the
## reason a backwards step exists at all is that overshooting the one you
## wanted is the whole reason people press it twice.
func selectIdleVillager(dir: int = 1) -> void:
	if world == null:
		return
	var idle := world.entitiesOf(me, T.Kind.Unit).filter(func(e): return e.type == "villager" and e.order.kind == T.OrderKind.Idle)
	if idle.is_empty():
		hud.addAlert("No idle villagers")
		return
	var n := idle.size()
	idleVillIndex = ((idleVillIndex % n) + n) % n
	var v: Entity = idle[idleVillIndex]
	idleVillIndex = (idleVillIndex + dir + n) % n
	select([v.id])
	camera.centerOn(v.x, v.y)
	hud.addAlert("Idle villager (%d idle)" % n)


## Select every military unit you own (everything that isn't a villager).
func selectAllArmy() -> void:
	if world == null:
		return
	var army := world.entitiesOf(me, T.Kind.Unit).filter(func(e): return not _canGather(e))
	if army.is_empty():
		hud.addAlert("No army units")
		return
	select(_ids(army))


## One chip of the QoL bar: true when clicked this frame.
func _chip(label: String, x: float, y: float, w: float, h: float, active: bool, danger: bool = false) -> bool:
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	var hover := ui.mx >= x and ui.mx <= x + w and ui.my >= y and ui.my <= y + h
	ctx.fillStyle = Palette.withAlpha(PAL.uiAccent, 0.9) if active else ("rgba(54,42,24,0.96)" if hover else "rgba(18,14,9,0.72)")
	ctx.fillRect(x, y, w, h)
	ctx.strokeStyle = Palette.withAlpha(PAL.uiAccent, 1.0 if active else 0.35)
	ctx.lineWidth = 1
	ctx.strokeRect(x, y, w, h)
	ctx.fillStyle = "#1a1208" if active else ("#f0a878" if danger else PAL.uiParchment)
	ctx.font = "bold 12px sans-serif"
	ctx.textAlign = "center"
	ctx.fillText(label, x + w / 2, y + h / 2 + 4)
	var clicked := false
	if hover and frameClick != null and not ui.pointerConsumed:
		ui.pointerConsumed = true
		clicked = true
	# A right-click or a drag that ends on a chip is for the chip, not the map under it.
	if hover:
		ui.pointerConsumed = true
	return clicked


## Pause/speed chips in the top bar + idle-villager & army buttons by the minimap.
func drawQoLBar(W: float, H: float) -> void:
	if world == null:
		return
	# Pause + speed, right of the day/night clock.
	var x := W / 2 + 150
	if _chip("▶" if paused else "❚❚", x, 4, 26, 26, false, paused):
		togglePause()
	x += 29
	for s in [0.5, 1.0, 2.0, 3.0]:
		if _chip("½×" if s == 0.5 else "%s×" % JS.num(s), x, 4, 28, 26, gameSpeed == s and not paused):
			setSpeed(s)
		x += 31
	ctx.textAlign = "left"

	# Idle-villager + army, above the minimap.
	var idleCount := world.entitiesOf(me, T.Kind.Unit).filter(func(e): return e.type == "villager" and e.order.kind == T.OrderKind.Idle).size()
	var by := H - HUD.MINIMAP_SIZE - 10 - 22 - 6 - 26 - 4
	if _chip("Idle %d" % idleCount, 12, by, 70, 24, idleCount > 0):
		selectIdleVillager()
	if _chip("Army", 86, by, 46, 24, false):
		selectAllArmy()
	if _chip("⚒", 136, by, 30, 24, showProduction):
		showProduction = not showProduction

	# Commander power button (only if your commander has one).
	var p := world.player(me)
	var cd = C.COMMANDERS.get(p.commander)
	var power = cd.get("power") if cd != null else null
	if power != null:
		var ready := p.powerCooldown <= 0
		var label := "Place ⚑" if powerArmed else ("⚑ %s" % (power.name as String).split(" ")[0] if ready else "%ss" % JS.num(ceil(p.powerCooldown)))
		if _chip(label, 170, by, 96, 24, powerArmed or ready):
			armCommanderPower()
	ctx.textAlign = "left"


func armCommanderPower() -> void:
	if world == null:
		return
	if world.powerReady(me):
		powerArmed = true
		placing = null
		attackMoveArmed = false
	else:
		hud.addAlert("Commander power not ready.")


func dropPing(wx: float, wy: float) -> void:
	hud.addPing(wx, wy)
	markers.append({"x": wx, "y": wy, "age": 0.0, "kind": "rally"}) # quick in-world flare
	audio.play("ui")
	if net != null:
		net.transport.send({"t": "ping", "x": wx, "y": wy, "team": me}) # share with the lobby


## Show an incoming network ping from another player.
func remotePing(x: float, y: float, team: int) -> void:
	hud.addPing(x, y)
	markers.append({"x": x, "y": y, "age": 0.0, "kind": "rally"})
	hud.addAlert("📍 %s pinged the map" % Spectator.teamLabel(team))
	audio.play("ui")


func myName() -> String:
	var n = Storage.getItem("bb_player_name")
	return n if n != null and str(n) != "" else "You"


func handleChatKey(key: String) -> void:
	if key == "Enter":
		var text := chatDraft.strip_edges()
		if text != "" and net != null:
			net.transport.send({"t": "chat", "name": myName(), "text": text, "team": me})
			addChatLine(myName(), text, me)
		chatOpen = false
		chatDraft = ""
	elif key == "Escape":
		chatOpen = false
		chatDraft = ""
	elif key == "Backspace":
		chatDraft = chatDraft.substr(0, maxi(0, chatDraft.length() - 1))
	elif key.length() == 1 and chatDraft.length() < 120:
		chatDraft += key


func addChatLine(name: String, text: String, team: int) -> void:
	chatLog.append({"name": name.substr(0, 24), "text": text.substr(0, 120), "team": team, "t": time})
	if chatLog.size() > 30:
		chatLog.pop_front()


func worldClick(sx: float, sy: float) -> void:
	if world == null:
		return
	var wx := camera.screenToWorldX(sx)
	var wy := camera.screenToWorldY(sy)

	# Planting the commander banner.
	if powerArmed:
		if world.powerReady(me):
			dispatch({"t": "banner", "team": me, "x": wx, "y": wy})
			var cd = C.COMMANDERS.get(world.player(me).commander)
			var power = cd.get("power") if cd != null else null
			markers.append({"x": wx, "y": wy, "age": 0.0, "kind": "rally"})
			hud.addAlert("⚑ %s planted!" % (power.name if power != null else "Banner"))
			audio.play("command")
		powerArmed = false
		return

	# Alt+click drops a ping (look-here signal) instead of selecting.
	if input.alt:
		dropPing(wx, wy)
		return

	if placing != null:
		var villagers := playerSelection().filter(func(e): return _canBuild(e))
		# The same checks the sim will make, so a click never looks like it
		# worked when it didn't — and when it can't, say why.
		var problem = world.placementProblem(me, placing, wx, wy)
		if problem == null:
			dispatch({"t": "place", "team": me, "building": placing, "x": wx, "y": wy, "builders": _ids(villagers)})
			audio.play("build")
			flashBuilders(villagers, wx, wy)
			if villagers.is_empty():
				hud.addAlert("Placed — no villager selected, so it waits for a builder.", wx, wy)
			# Walls stay armed either way (click or drag); other buildings with Shift.
			if not input.shift and not LINE_BUILDABLE.has(placing):
				placing = null
		else:
			var pr: String = problem
			hud.addAlert("Can't build there — %s%s." % [pr.substr(0, 1).to_lower(), pr.substr(1)], wx, wy)
			audio.play("tick")
		return

	if attackMoveArmed:
		var units := playerSelection().filter(func(e): return e.kind == T.Kind.Unit)
		dispatch({"t": "move", "team": me, "ids": _ids(units), "x": wx, "y": wy, "queue": input.shift, "attackMove": true, "formation": true})
		markers.append({"x": wx, "y": wy, "age": 0.0, "kind": "attack"})
		attackMoveArmed = false
		audio.play("command")
		return

	# Plain selection click.
	var e = world.entityAt(wx, wy)
	if e != null and e.kind != T.Kind.Projectile and world.visibleTo(me, e):
		if input.shift and e.team == me:
			var cur := selection.duplicate()
			if cur.has(e.id):
				cur.erase(e.id)
			else:
				cur.append(e.id)
			select(cur)
		else:
			select([e.id])
	elif not input.shift:
		select([])


func worldDoubleClick(sx: float, sy: float) -> void:
	if world == null:
		return
	var wx := camera.screenToWorldX(sx)
	var wy := camera.screenToWorldY(sy)
	var e = world.entityAt(wx, wy, me)
	if e == null or (e.kind != T.Kind.Unit and e.kind != T.Kind.Building):
		return
	# Select every entity of this kind+type currently on screen — units (an
	# army of one type) or buildings (e.g. all your stables, to mass-train at
	# once).
	var ids: Array = []
	for o in world.entitiesOf(me, e.kind):
		if o.type != e.type:
			continue
		var ox := camera.worldToScreenX(o.x)
		var oy := camera.worldToScreenY(o.y)
		if ox >= 0 and oy >= 0 and ox <= canvasW and oy <= canvasH:
			ids.append(o.id)
	select(ids)


func worldDragSelect(box: Dictionary) -> void:
	if world == null:
		return
	var wx0 := minf(camera.screenToWorldX(box.x0), camera.screenToWorldX(box.x1))
	var wx1 := maxf(camera.screenToWorldX(box.x0), camera.screenToWorldX(box.x1))
	var wy0 := minf(camera.screenToWorldY(box.y0), camera.screenToWorldY(box.y1))
	var wy1 := maxf(camera.screenToWorldY(box.y0), camera.screenToWorldY(box.y1))
	var units: Array = []
	var buildings: Array = []
	for e in world.entitiesOf(me):
		if e.x < wx0 or e.x > wx1 or e.y < wy0 or e.y > wy1:
			continue
		if e.kind == T.Kind.Unit:
			units.append(e.id)
		elif e.kind == T.Kind.Building:
			buildings.append(e.id)
	var picked: Array = units if not units.is_empty() else buildings.slice(0, 1)
	if input.shift:
		var merged := {}
		for id in selection:
			merged[id] = true
		for id in picked:
			merged[id] = true
		picked = merged.keys()
	select(picked)


func issueContextCommand(wx: float, wy: float, screen) -> void:
	if world == null:
		return
	var sel := playerSelection()
	if sel.is_empty():
		return
	var units := sel.filter(func(e): return e.kind == T.Kind.Unit)
	var buildingsSel := sel.filter(func(e): return e.kind == T.Kind.Building)
	var target = world.entityAt(wx, wy)
	var shift := input.shift

	var mv := func(mids: Array, formation: bool) -> void:
		dispatch({"t": "move", "team": me, "ids": mids, "x": wx, "y": wy, "queue": shift, "attackMove": false, "formation": formation})

	# Production buildings: right-click sets rally.
	if units.is_empty() and not buildingsSel.is_empty():
		for b in buildingsSel:
			var bd = C.BUILDINGS.get(b.type)
			if bd != null and (bd.trains as Array).size() > 0:
				dispatch({"t": "rally", "team": me, "buildingId": b.id, "x": wx, "y": wy})
				markers.append({"x": wx, "y": wy, "age": 0.0, "kind": "rally"})
		audio.play("command")
		return
	if units.is_empty():
		return

	var ids := _ids(units)

	# Gatherers prefer a nearby resource even if a building (e.g. a mill built
	# right next to berries) sits closer to the click point.
	var gatherers0 := units.filter(func(e): return _canGather(e))
	if not gatherers0.is_empty():
		var node = world.resourceAt(wx, wy, me)
		if node != null and world.visibleTo(me, node) and not (target != null and target.team != me and target.kind != T.Kind.Resource):
			dispatch({"t": "gather", "team": me, "ids": _ids(gatherers0), "node": node.id, "queue": shift})
			var rest := units.filter(func(e): return not _canGather(e))
			if not rest.is_empty():
				mv.call(_ids(rest), false)
			markers.append({"x": wx, "y": wy, "age": 0.0, "kind": "move"})
			audio.play("command")
			return

	if target != null and target.alive and world.visibleTo(me, target):
		if world.areHostile(me, target.team) and target.kind != T.Kind.Resource:
			dispatch({"t": "attack", "team": me, "ids": ids, "target": target.id, "queue": shift})
			markers.append({"x": wx, "y": wy, "age": 0.0, "kind": "attack"})
			audio.play("command")
			return
		if target.kind == T.Kind.Resource or (target.team == me and target.type == "farm" and target.buildState == T.BuildState.Done):
			var gatherers := units.filter(func(e): return _canGather(e))
			if not gatherers.is_empty():
				dispatch({"t": "gather", "team": me, "ids": _ids(gatherers), "node": target.id, "queue": shift})
				var rest := units.filter(func(e): return not _canGather(e))
				if not rest.is_empty():
					mv.call(_ids(rest), false)
				markers.append({"x": wx, "y": wy, "age": 0.0, "kind": "move"})
				audio.play("command")
				return
		if target.team == me and target.kind == T.Kind.Building:
			# Villagers repair/finish construction; combat units garrison.
			var builders := units.filter(func(e): return _canBuild(e))
			var fighters := units.filter(func(e): return not _canBuild(e))
			if not builders.is_empty() and (target.buildState != T.BuildState.Done or target.hp < target.maxHp):
				dispatch({"t": "build", "team": me, "ids": _ids(builders), "building": target.id, "queue": shift})
			var tb = C.BUILDINGS.get(target.type)
			var cap := float(tb.get("garrisonCap", 0)) if tb != null else 0.0
			if not fighters.is_empty() and cap > 0:
				dispatch({"t": "garrison", "team": me, "ids": _ids(fighters), "buildingId": target.id})
			elif not fighters.is_empty():
				mv.call(_ids(fighters), false)
			markers.append({"x": wx, "y": wy, "age": 0.0, "kind": "move"})
			audio.play("command")
			return
	# Default: move.
	mv.call(ids, true)
	markers.append({"x": wx, "y": wy, "age": 0.0, "kind": "move"})
	audio.play("command")


# ============================================================ world events ==

func handleEvents(events: Array) -> void:
	var PAL: Dictionary = C.PAL
	var sfx := func(name: String, key: String, cd: float = 0.08) -> void:
		var last: float = sfxCooldown.get(key, -99.0)
		if time - last > cd:
			sfxCooldown[key] = time
			audio.play(name)
	var heat := {"sword": 0.06, "bow": 0.03, "siege": 0.12, "death": 0.1, "collapse": 0.15}
	for ev in events:
		var kind: String = ev.kind
		var data = ev.get("data")
		if heat.has(kind):
			combatHeat = minf(1, combatHeat + heat[kind])
		match kind:
			"sword":
				sfx.call("sword", "sword")
				# dust puff + a couple of bright metallic glints for a punchy clash
				particles.burst(ev.x, ev.y, 5, "#ffd9a0", 95, {"maxLife": 0.22, "size": 1.8})
				particles.burst(ev.x, ev.y, 3, "#fff6e0", 150, {"maxLife": 0.16, "size": 1.4, "glow": true})
			"bow":
				sfx.call("bow", "bow")
			"arrowHit":
				sfx.call("arrowHit", "arrowHit")
				particles.burst(ev.x, ev.y, 3, "#d9cfb4", 70, {"maxLife": 0.2, "size": 1.5})
				particles.burst(ev.x, ev.y, 2, "#fff6e0", 130, {"maxLife": 0.14, "size": 1.2, "glow": true})
				if randf() < 0.6:
					renderer.addStuckArrow(ev.x, ev.y, randf() * PI * 2)
			"hit":
				if showDamageNumbers and data != null and str(data) != "":
					var friendly: bool = ev.team == me
					renderer.addFloater(ev.x, ev.y, str(data), "#fff0c0" if friendly else "#ff9a8a", 13)
			"siege":
				sfx.call("siege", "siege", 0.2)
				particles.burst(ev.x, ev.y, 22, PAL.dust, 170, {"maxLife": 0.7, "size": 3.4, "gravity": 60})
				particles.burst(ev.x, ev.y, 10, PAL.fire, 220, {"maxLife": 0.35, "size": 2.6, "glow": true})
				renderer.addShake(4)
			"death":
				sfx.call("death", "death", 0.14)
				particles.burst(ev.x, ev.y, 9, PAL.blood, 110, {"maxLife": 0.5, "size": 2.2, "gravity": 140})
				particles.burst(ev.x, ev.y, 5, "#ffffff", 80, {"maxLife": 0.18, "size": 2.4, "glow": true})
				renderer.addCorpse(ev.x, ev.y, ev.team, false)
			"collapse":
				sfx.call("collapse", "collapse", 0.3)
				particles.burst(ev.x, ev.y, 36, PAL.dust, 200, {"maxLife": 1.1, "size": 4.2, "gravity": 50})
				particles.burst(ev.x, ev.y, 16, PAL.smoke, 90, {"maxLife": 1.6, "size": 5, "gravity": -30})
				particles.burst(ev.x, ev.y, 12, PAL.fire, 140, {"maxLife": 0.6, "size": 3, "glow": true})
				renderer.addScorch(ev.x, ev.y, 30)
				renderer.addShake(7)
			"build":
				sfx.call("build", "build", 0.4)
			"complete":
				if ev.team == me and not spectating:
					sfx.call("complete", "complete", 0.5)
					var bd = C.BUILDINGS.get(str(data)) if data != null else null
					if bd != null:
						hud.addAlert("%s completed." % bd.name, ev.x, ev.y)
					lastEvent = {"x": ev.x, "y": ev.y}
			"popcap":
				if ev.team == me and not spectating and time - popcapAlertAt > 6:
					popcapAlertAt = time
					var ud = C.UNITS.get(str(data)) if data != null else null
					hud.addAlert("Population limit — a %s couldn't join (refunded). Build a House." % (ud.name if ud != null else "unit"), ev.x, ev.y)
					sfx.call("alert", "alert", 3)
			"underattack":
				if ev.team == me and not spectating:
					sfx.call("alert", "alert", 4)
					hud.addAlert("⚠ Your forces are under attack!", ev.x, ev.y)
					# Space jumps here, which is what makes an "under attack"
					# warning actionable rather than a message you then have to
					# go hunting for.
					lastEvent = {"x": ev.x, "y": ev.y}
			"callout":
				# Surface an allied AI's voice line (not our own) so team games
				# feel like a coordinated front rather than silent co-op.
				if not spectating and ev.team != me and world != null and world.areAllied(me, ev.team) and data != null and str(data) != "":
					hud.addAlert("🗣 Ally: %s" % data, ev.x, ev.y)
			"oath":
				# Oaths are public: everyone hears what a realm swears, so its
				# identity is something to answer rather than a surprise.
				var o = C.OATHS.get(str(data)) if data != null else null
				if o == null:
					continue
				if ev.team == me:
					var u = C.UNITS.get(o.unit) if o.get("unit") != null else null
					hud.addAlert("You swore the %s%s" % [o.name, " — %ss can now be trained" % u.name if u != null else ""])
				else:
					# No map ping: the event sits on their Town Centre, and the
					# announcement mustn't double as a free scout of it.
					hud.addAlert("%s swore the %s" % [teamLabel(ev.team), o.name])
			"age":
				var who := "You have" if ev.team == me else "The enemy has"
				var ai := int(str(data).to_int()) if data != null else 0
				var ages: Array = C.AGES
				hud.addAlert("%s advanced to the %s!" % [who, ages[ai].name if ai >= 0 and ai < ages.size() else "next age"])
				if ev.team == me:
					sfx.call("levelup", "age", 1)
			"ability":
				var col := "#ffe98a"
				for k in C.ABILITIES:
					if C.ABILITIES[k].id == data:
						col = C.ABILITIES[k].get("color", col)
						break
				particles.burst(ev.x, ev.y, 14, col, 130, {"maxLife": 0.5, "size": 2.4, "glow": true, "gravity": -40})
				if ev.team == me:
					sfx.call("command", "ability", 0.1)
			"deposit":
				pass
			"spawn":
				if ev.team == me:
					sfx.call("complete", "spawn", 2)


# ================================================================== frame ==

func _process(delta: float) -> void:
	var t0 := Time.get_ticks_usec()
	input.update()
	var dt := minf(delta, 0.1)
	if _taskBusy():
		# The simulation is still stepping on its thread: draw the world again
		# from the snapshot (it moves — the camera, particles, decals, weather)
		# and leave the interface as it is. See the header.
		_skipFrame(dt)
		_logFrame(dt, t0, true)
		return
	_joinTask()
	_flushQueue()
	frameBody(dt)
	_logFrame(dt, t0, false)


func _logFrame(dt: float, t0: int, skipped: bool) -> void:
	var cpu := (Time.get_ticks_usec() - t0) / 1000.0
	perf.cpuMs += (cpu - perf.cpuMs) * 0.1
	if skipped:
		perf.skipped += 1
	if logFrames:
		frameLog.append({"dt": dt * 1000.0, "cpu": cpu, "render": renderer.lastRenderMs, "snapshot": renderer.lastSnapshotMs,
			"ui": perf.uiMs, "skipped": skipped, "tick": perf.tickMs, "worstTick": _taskWorstMs})


func frameBody(dt: float) -> void:
	time += dt
	# Smoothed FPS (EMA) for the optional on-screen counter.
	if dt > 0:
		fps += (1 / dt - fps) * 0.1
	trackPerf(dt)

	var W := float(canvasW)
	var H := float(canvasH)
	var ui := UI.ui
	ctx.begin(uiLayer, canvasW, canvasH)
	renderer.ctx.begin(worldLayer, canvasW, canvasH)
	renderer.octx.begin(overlayLayer, canvasW, canvasH)

	_screensCall("setMouseDown", [input.leftDown])
	input.longPressRight = state == "match" # long-press → right-click only in a match
	ui.begin(ctx, {
		"mx": input.mx,
		"my": input.my,
		"clicked": frameClick != null,
		"rightClicked": frameRight != null,
		"alt": input.alt,
		"leftHeld": input.leftDown,
		"rightHeld": input.rightDown,
		"ctrlHeld": input.ctrl,
		"shiftHeld": input.shift,
		"wheel": frameWheel,
	})

	if state == "match" and world != null:
		matchFrame(dt, W, H)
	else:
		# Out-of-match screens are pure interface, so the whole block draws
		# through the interface scale.
		audio.updateMusic(dt, true)
		var uis := uiScale()
		var UW := W / uis
		var UH := H / uis
		ui.pushScale(uis)
		_screenFrame(UW, UH, dt)
		ui.flushTooltip(UW, UH)
		ui.popScale()

	# Optional FPS overlay, drawn on top of everything in every state.
	# The diagnostics scale too — they are the readouts a player squinting at
	# a chugging match most needs to be able to read.
	var uis2 := uiScale()
	ui.pushScale(uis2)
	if settings.showFps:
		drawFps(W / uis2)
	if settings.perfOverlay:
		drawPerfOverlay(W / uis2, H / uis2)
	ui.popScale()
	# On a phone held in portrait, nudge to landscape — the UI is landscape-first.
	if input.usingTouch and H > W * 1.05:
		drawRotatePrompt(W, H)

	# Clear frame input flags.
	frameClick = null
	frameDouble = null
	frameRight = null
	frameDragEnd = null
	frameWheel = 0

	# Hand the next ticks to the sim thread (owning frames only; see header).
	_launchTicks()


## Every out-of-match state (TS frameBody's big else-branch).
func _screenFrame(W: float, H: float, dt: float) -> void:
	if state == "menu":
		var action = menu.draw(W, H, time, profile) if menu != null else _placeholderMenu(W, H)
		FullscreenButton.fullscreenButton(W - 186, 16, 170, 36, {"size": 14, "hotkey": fullscreenKeyLabel()})
		if action == "skirmish":
			if setup != null:
				state = "setup"
			else:
				startMatch(defaultConfig())
			audio.play("ui")
		elif action == "watch":
			startSpectate(defaultConfig())
		elif action == "resume" and menu != null and menu.pickedSave != null:
			var save = menu.pickedSave
			menu.pickedSave = null
			audio.play("ui")
			loadMatch(save)
		elif action == "resume_latest":
			var saves := Savegame.listSaves()
			if not saves.is_empty():
				audio.play("ui")
				loadMatch(saves[0])
		elif action == "multiplayer":
			audio.play("ui")
			if lobby != null:
				lobby.open(func(start): startNetMatch(start), lobbyOptions())
		elif action == "warband":
			var WR = _cls("res://src/sim/warband.gd", "WarbandRun")
			if WR != null and warbandScreen != null:
				warband = WR.new()
				state = "warband"
				audio.play("ui")
		elif action == "armory":
			state = "armory"
			audio.play("ui")
		elif action == "codex":
			state = "codex"
			audio.play("ui")
		elif action == "career":
			state = "career"
			audio.play("ui")
		elif action == "nemesis":
			var pf = profile.playableFaction()
			if rivalScreen != null:
				rivalScreen.open(pf if pf != null else "")
			state = "nemesis"
			audio.play("ui")
		elif action == "factions":
			openFactions("menu", profile.playableFaction())
		elif action == "editor":
			state = "editor"
			if editorScreen != null:
				editorScreen.author = profile.data.name
			audio.play("ui")
		elif action == "settings":
			openSettings("menu")
	elif state == "warband" and warband != null and warbandScreen != null:
		if warbandScreen.draw(W, H, time, warband) == "exit":
			warband = null
			state = "menu"
			audio.play("ui")
	elif state == "settings":
		var a = settingsScreen.draw(W, H, time, settings, input.leftDown) if settingsScreen != null else _placeholderScreen(W, H, "Settings")
		applySettings() # live preview every frame
		if a == "back":
			Settings.saveSettings(settings)
			state = settingsReturn
			audio.play("ui")
	elif state == "editor":
		var a = editorScreen.draw(W, H, time, input.leftDown) if editorScreen != null else ({"kind": "back"} if _placeholderScreen(W, H, "Map editor") == "back" else null)
		if a != null and a.get("kind") == "back":
			state = "menu"
			audio.play("ui")
		elif a != null and a.get("kind") == "test":
			testCustomMap(a.map)
	elif state == "nemesis":
		var pf = profile.playableFaction()
		var a = rivalScreen.draw(W, H, time, pf if pf != null else "") if rivalScreen != null else _placeholderScreen(W, H, "Nemesis", "fight")
		if a == "back":
			state = "menu"
			audio.play("ui")
		elif a == "fight":
			audio.play("ui")
			startRivalMatch()
	elif state == "career":
		var a = careerScreen.draw(W, H, time, profile) if careerScreen != null else _placeholderCareer(W, H)
		if a is String and a == "back":
			state = "menu"
			audio.play("ui")
		elif a is Dictionary and a.has("watch"):
			audio.play("ui")
			startReplay(a.watch)
	elif state == "factions":
		var a = factionBook.draw(W, H, time, profile, dt) if factionBook != null else _placeholderScreen(W, H, "Factions")
		if a == "back":
			state = factionsReturn
			if state == "setup" and setup != null:
				var pf = profile.playableFaction()
				setup.config.faction = pf if pf != null else C.DEFAULT_FACTION
			audio.play("ui")
	elif state == "codex":
		var a = codexScreen.draw(W, H, time, profile) if codexScreen != null else _placeholderScreen(W, H, "Codex")
		if a == "back":
			state = "menu"
			audio.play("ui")
	elif state == "setup":
		if setup == null:
			state = "menu"
			return
		var action = setup.draw(W, H, time, profile)
		if action == "back":
			state = "menu"
		elif action == "factions":
			openFactions("setup", setup.bookFocus)
		elif action == "start":
			startMatch(setup.config.duplicate(true))
		elif action == "spectate":
			startSpectate(setup.config.duplicate(true))
	elif state == "armory":
		var action = armory.draw(W, H, time, dt, profile) if armory != null else _placeholderScreen(W, H, "Armory")
		if action == "back":
			profile.save()
			state = "menu"
	elif state == "postmatch" and matchRewards != null:
		var action
		if postmatch != null:
			action = postmatch.draw(W, H, time, dt, matchWon, endReport, matchRewards, profile, xpBefore, levelsGained, endGraph, matchAwards)
		else:
			action = _placeholderPostMatch(W, H)
		if action == "continue":
			# A match launched from the editor goes back to the editor, so
			# "test, tweak, test again" is a loop rather than a round trip
			# through the main menu every time.
			state = "editor" if editorReturn else "menu"
			editorReturn = false
			audio.play("ui")
			# An online match goes back to the server's hub, not just the menu.
			if netHub != "" and lobby != null:
				var hub := netHub
				netHub = ""
				lobby.reopenHub(func(start): startNetMatch(start), hub, lobbyOptions())


## Frame/tick bookkeeping, plus adaptive detail.
##
## The rule is deliberately slow on the way in and slower on the way out: a
## single stuttery frame is normal (a building finishing, a map reveal), and
## detail that flickers on and off is worse than either setting. So the
## budget has to be blown for most of a second before detail drops, and the
## frame rate has to be comfortably back for two before it returns.
func trackPerf(dt: float) -> void:
	var ms := dt * 1000
	perf.frameMs += (ms - perf.frameMs) * 0.1
	perf.ticksLastFrame = perf.ticksThisFrame
	perf.ticksThisFrame = 0
	if ms > perf.worstFrameMs:
		perf.worstFrameMs = ms
	# The "worst" readouts are a rolling three-second window, so the overlay
	# shows what is happening now rather than the worst thing that ever did.
	perf.worstReset += dt
	if perf.worstReset > 3:
		perf.worstReset = 0
		perf.worstFrameMs = ms
		perf.worstTickMs = perf.tickMs
	if not settings.autoLod or settings.reduceEffects:
		if perf.autoLod:
			perf.autoLod = false
			applySettings()
		perf.slipT = 0
		return
	var SLIPPING := 1000.0 / 45 # below ~45fps counts as slipping
	var RECOVERED := 1000.0 / 55
	if not perf.autoLod and perf.frameMs > SLIPPING:
		perf.slipT += dt
		if perf.slipT > 0.8:
			perf.autoLod = true
			perf.slipT = 0
			applySettings()
			hud.addAlert("Detail reduced to keep up")
	elif perf.autoLod and perf.frameMs < RECOVERED:
		perf.slipT += dt
		if perf.slipT > 2:
			perf.autoLod = false
			perf.slipT = 0
			applySettings()
	else:
		perf.slipT = 0


## The performance overlay. Its job is to answer "why is this chugging?"
## without a profiler: whether the cost is the simulation or the drawing, how
## much of the map is alive, and whether detail has already been dropped to
## cope. (Godot: the simulation runs on its own thread, so "draw" is the main
## thread's measured CPU time, not frame minus sim.)
func drawPerfOverlay(W: float, H: float) -> void:
	var p := perf
	var f := maxi(0, int(JS.round(fps)))
	var ents := world.entities.size() if world != null else 0
	var budget := 1000.0 / 60
	var rows := [
		["fps", str(f), "#7df2a9" if f >= 55 else ("#ffd24a" if f >= 30 else "#e0564a")],
		["frame", "%s ms  (worst %s)" % [JS.to_fixed(p.frameMs, 1), JS.to_fixed(p.worstFrameMs, 0)],
			"#7df2a9" if p.frameMs <= budget else ("#ffd24a" if p.frameMs <= budget * 2 else "#e0564a")],
		["sim", "%s ms/tick  (worst %s)%s" % [JS.to_fixed(p.tickMs, 2), JS.to_fixed(p.worstTickMs, 1), " ⇄" if threadSim and net == null else ""],
			"#7df2a9" if p.tickMs <= 8 else ("#ffd24a" if p.tickMs <= 20 else "#e0564a")],
		["draw", "%s ms" % JS.to_fixed(maxf(0, p.cpuMs), 1), "#cfe0ff"],
		["entities", str(ents), "#cabfa4" if ents < 900 else ("#ffd24a" if ents < 1600 else "#e0564a")],
		["detail", "reduced (setting)" if settings.reduceEffects else ("reduced (auto)" if p.autoLod else "full"),
			"#ffd24a" if p.autoLod or settings.reduceEffects else "#cabfa4"],
	]
	var w := 220.0
	var rowH := 17.0
	var h := 12 + rows.size() * rowH + 6
	var x := W - w - 12
	var y := 12.0
	ctx.save()
	ctx.fillStyle = "rgba(8,6,3,0.82)"
	ctx.beginPath(); ctx.roundRect(x, y, w, h, 6); ctx.fill()
	ctx.strokeStyle = "rgba(255,255,255,0.14)"
	ctx.lineWidth = 1
	ctx.beginPath(); ctx.roundRect(x + 0.5, y + 0.5, w - 1, h - 1, 6); ctx.stroke()
	ctx.font = "11px ui-monospace, SFMono-Regular, monospace"
	ctx.textBaseline = "middle"
	for i in rows.size():
		var ry := y + 14 + i * rowH
		ctx.textAlign = "left"
		ctx.fillStyle = "#8a8278"
		ctx.fillText(rows[i][0], x + 10, ry)
		ctx.textAlign = "right"
		ctx.fillStyle = rows[i][2]
		ctx.fillText(rows[i][1], x + w - 10, ry)
	ctx.restore()


## Small live FPS readout, top-centre, colour-coded by smoothness.
func drawFps(W: float) -> void:
	var f := maxi(0, int(JS.round(fps)))
	var col := "#7df2a9" if f >= 55 else ("#ffd24a" if f >= 30 else "#e0564a")
	var txt := "%d FPS" % f
	ctx.save()
	ctx.font = "bold 13px ui-monospace, SFMono-Regular, monospace"
	var tw := ctx.measureText(txt).width
	var w := tw + 16
	var h := 20.0
	var x := W / 2 - w / 2
	var y := 4.0
	ctx.fillStyle = "rgba(8,6,3,0.72)"
	ctx.fillRect(x, y, w, h)
	ctx.strokeStyle = "rgba(255,255,255,0.12)"
	ctx.lineWidth = 1
	ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1)
	ctx.fillStyle = col
	ctx.textBaseline = "middle"
	ctx.textAlign = "center"
	ctx.fillText(txt, W / 2, y + h / 2 + 1)
	ctx.restore()


## Full-screen "rotate to landscape" overlay for portrait phones.
func drawRotatePrompt(W: float, H: float) -> void:
	ctx.save()
	ctx.fillStyle = "rgba(8,6,3,0.94)"
	ctx.fillRect(0, 0, W, H)
	var cx := W / 2
	var cy := H / 2 - 30
	var t := time * 1.6
	var wob := sin(t) * 0.35 - 0.4 # gentle rotate wobble
	ctx.translate(cx, cy)
	ctx.rotate(wob)
	var pw := 78.0
	var ph := 140.0
	ctx.fillStyle = "#1c150e"
	ctx.strokeStyle = "#caa56a"
	ctx.lineWidth = 4
	ctx.beginPath()
	ctx.roundRect(-pw / 2, -ph / 2, pw, ph, 12)
	ctx.fill()
	ctx.stroke()
	ctx.fillStyle = "#3a78d8"
	ctx.fillRect(-pw / 2 + 8, -ph / 2 + 16, pw - 16, ph - 32)
	ctx.restore()
	ctx.fillStyle = "#ffe9b0"
	ctx.font = "bold 30px Georgia, serif"
	ctx.textAlign = "center"
	ctx.textBaseline = "middle"
	ctx.fillText("↻ Rotate your device", cx, cy + 96)
	ctx.fillStyle = "#d8cdb4"
	ctx.font = "16px system-ui, sans-serif"
	ctx.fillText("Banner & Blade plays in landscape.", cx, cy + 126)


## Simulation is stepping on the main thread this match (the TS way).
func _syncSim() -> bool:
	return not threadSim or net != null


func matchFrame(dt: float, W: float, H: float) -> void:
	var w := world

	# ---- simulate ----
	if net != null:
		if net.transport != null and net.transport.has_method("poll"):
			net.transport.poll()
		# Lockstep: author local input at the fixed sim rate (no game-speed, and
		# never pausing — a local pause would just stall the peer), capped so we
		# don't run far ahead of a lagging opponent; step as far as the received
		# remote input allows. AIs don't run (both teams are human).
		if w.winner == null:
			if net.observer:
				# Observers don't author input — just simulate as relayed turns
				# arrive, staying the broadcast delay behind the live match.
				net.stepReady(20, casterDelayTicks)
			else:
				netAccumulator += dt
				var ahead := net.lock.inputDelay + 12
				var guard := 0
				while netAccumulator >= SIM_DT and net.authorLead < ahead and guard < 30:
					guard += 1
					net.authorTick()
					netAccumulator -= SIM_DT
				net.stepReady(10)
			if net.desynced and not netDesyncAlerted:
				netDesyncAlerted = true
				hud.addAlert("⚠ Connection desynced — the match is out of sync.")
				net.transport.send({"t": "desync", "tick": w.tickCount})
	elif replaySeek != null:
		if _syncSim():
			advanceSeek()
		# (threaded: the seek runs as the frame's task — see _launchTicks)
	elif _simRunning():
		accumulator += dt * gameSpeed
		if _syncSim():
			var steps := 0
			var maxSteps := 5 + int(ceil(gameSpeed)) * 2 # allow catch-up at high speed
			while accumulator >= SIM_DT and steps < maxSteps:
				if replay != null and replayStep(w):
					accumulator = 0
					break
				var t0 := Time.get_ticks_usec()
				w.tick()
				for ai in ais:
					ai.update(SIM_DT)
				var cost := (Time.get_ticks_usec() - t0) / 1000.0
				perf.tickMs += (cost - perf.tickMs) * 0.12
				if cost > perf.worstTickMs:
					perf.worstTickMs = cost
				perf.ticksThisFrame += 1
				accumulator -= SIM_DT
				steps += 1
			if steps == maxSteps:
				accumulator = 0 # drop time if we can't keep up
	var events := w.drainEvents()
	if spectating:
		caster.onEvents(w, events, playerNames)
	handleEvents(events)
	sampleHistory(w)
	if spectating:
		# Vision: everything, or exactly one player's fog of war.
		var v := caster.vision
		w.revealAll = v < 0 or v >= w.numTeams
		me = v if v >= 0 and v < w.numTeams else 0
		if not ingameMenu:
			caster.direct(w, camera, dt)
	particles.update(dt)
	# Combat heat fades over a few seconds; it drives the music's intensity.
	combatHeat *= pow(0.5, dt / 3)
	audio.updateMusic(dt, not ingameMenu, 0.0 if ingameMenu else combatHeat)

	# Day/night cycle (deterministic off sim time; drives sky tint + vision).
	renderer.dayPhase = DayNight.dayPhase(w.time)

	# Damaged buildings smoke and burn.
	smokeTimer -= dt
	if smokeTimer <= 0:
		smokeTimer = 0.09
		var PAL: Dictionary = C.PAL
		for e: Entity in w.entities:
			if not e.alive or e.kind != T.Kind.Building or e.buildState != T.BuildState.Done:
				continue
			var frac := e.hp / e.maxHp
			if frac >= 0.5:
				continue
			var sx := e.x + (randf() - 0.5) * e.radius
			var sy := e.y - e.radius * 0.3 + (randf() - 0.5) * e.radius * 0.4
			particles.spawn({"x": sx, "y": sy, "vx": (randf() - 0.5) * 8, "vy": -22 - randf() * 14, "color": PAL.smoke, "maxLife": 1.4 + randf(), "size": 3 + randf() * 2.5, "gravity": -8, "drag": 0.96})
			if frac < 0.28 and randf() < 0.5:
				particles.spawn({"x": sx, "y": sy, "vx": (randf() - 0.5) * 10, "vy": -30 - randf() * 14, "color": PAL.fire if randf() < 0.5 else PAL.fireBright, "maxLife": 0.45, "size": 2.4 + randf() * 2, "gravity": -30, "glow": true})

	_moveCamera(dt, W, H)

	# ---- markers age ----
	for m in markers:
		m.age += dt
	markers = markers.filter(func(m): return m.age < 0.7)
	for l in builderLines:
		l.age += dt
	builderLines = builderLines.filter(func(l): return l.age < 1.6)
	renderer.builderLines = builderLines

	# ---- ghost placement validity ----
	var ghost = null
	var suppressDragBox := false
	placementInfo = null
	# Over the HUD, the cursor is for the HUD: no ghost, no panel.
	if placing != null and not hud.overHud(W / uiScale(), H / uiScale(), input.mx / uiScale(), input.my / uiScale()):
		var wx := camera.screenToWorldX(input.mx)
		var wy := camera.screenToWorldY(input.my)
		var def: Dictionary = C.BUILDINGS[placing]
		var p := w.player(me)
		# The sim's own checks and its real (discounted) price — the preview and
		# the placement can't disagree.
		var cost := w.buildingCostFor(me, placing)
		var problem = w.placementProblem(me, placing, wx, wy)
		ghost = {"type": placing, "x": wx, "y": wy, "valid": problem == null, "team": me}
		var builders := playerSelection().filter(func(e): return _canBuild(e))
		ghost.builders = builders.slice(0, 12).map(func(b): return {"x": b.x, "y": b.y})
		# Drop-off buildings: which resources would this one serve?
		var coverage := ""
		var kinds: Array = (def.get("dropoffKinds", []) as Array).filter(func(_k): return placing != "town_center")
		if not kinds.is_empty():
			var R := 8.0 * C.TILE
			var nodeKind := {"tree": "wood", "gold_mine": "gold", "berries": "food", "farm": "food"}
			var nodes: Array = []
			for e: Entity in w.entities:
				if not e.alive or not (e.kind == T.Kind.Resource or e.type == "farm") or not kinds.has(nodeKind.get(e.type, "")):
					continue
				if JS.hypot(e.x - wx, e.y - wy) >= R:
					continue
				if e.kind == T.Kind.Resource and w.fogAt(me, e.x, e.y) == 0:
					continue
				nodes.append(e)
			ghost.coverage = {"radius": R, "nodes": nodes.map(func(nd): return {"x": nd.x, "y": nd.y, "r": nd.radius})}
			var label := {"tree": ["tree", "trees"], "gold_mine": ["gold mine", "gold mines"], "berries": ["berry bush", "berry bushes"], "farm": ["farm", "farms"]}
			var counts := {}
			for nd in nodes:
				counts[nd.type] = counts.get(nd.type, 0) + 1
			if not counts.is_empty():
				var parts: Array = []
				for t in counts:
					var n: int = counts[t]
					parts.append("%d %s" % [n, label[t][0 if n == 1 else 1] if label.has(t) else t])
				coverage = ", ".join(parts) + " in reach"
			else:
				coverage = "No %s within reach" % "/".join(kinds)
		placementInfo = {"type": placing, "cost": cost, "problem": problem, "builders": builders.size(), "coverage": coverage, "short": not w.canAfford(p.resources, cost)}
		# Drag-painting: preview the whole snapped run instead of a selection box.
		if input.drag.active:
			var d: Dictionary = input.drag
			var pts := dragPlacements(placing,
				camera.screenToWorldX(d.x0), camera.screenToWorldY(d.y0),
				camera.screenToWorldX(d.x1), camera.screenToWorldY(d.y1))
			# Mark them invalid once the treasury runs out rather than only once
			# the ground does, so a twelve-house drag shows you where the wood stops.
			var budget: Dictionary = p.resources.duplicate()
			var line: Array = []
			for pt in pts:
				var ok: bool = w.placementProblem(me, placing, pt.x, pt.y, true) == null and w.canAfford(budget, cost)
				if ok:
					budget = {"food": budget.food - cost.food, "wood": budget.wood - cost.wood, "gold": budget.gold - cost.gold}
				line.append({"x": pt.x, "y": pt.y, "valid": ok})
			ghost.line = line
			# What you are about to spend, before you spend it. Counting only
			# the placements that will actually land, since the greyed-out tail
			# costs nothing and reporting it would be a lie.
			var n := line.filter(func(pt): return pt.valid).size()
			var parts: Array = []
			if cost.wood:
				parts.append("%s wood" % JS.num(cost.wood * n))
			if cost.gold:
				parts.append("%s gold" % JS.num(cost.gold * n))
			if cost.food:
				parts.append("%s food" % JS.num(cost.food * n))
			var unit := "" if n == 1 else "s"
			if n > 0:
				placementInfo.drag = "%d %s%s%s" % [n, ("segment" + unit) if LINE_BUILDABLE.has(placing) else ("building" + unit),
					"  ·  %s" % ", ".join(parts) if not parts.is_empty() else "",
					"  (%d won't fit)" % (pts.size() - n) if n < pts.size() else ""]
			else:
				placementInfo.drag = "Nothing fits there"
			suppressDragBox = true

	# ---- render world ----
	var selected := playerSelection()
	_rallyFrom = []
	for e in selected:
		var bd = C.BUILDINGS.get(e.type)
		if e.kind == T.Kind.Building and bd != null and (bd.trains as Array).size() > 0 and e.rallyX >= 0:
			_rallyFrom.append({"x": e.x, "y": e.y, "rallyX": e.rallyX, "rallyY": e.rallyY})
	# Pop a health bar above whatever the cursor is over (no click needed).
	var hoveredId := -1
	if placing == null and input.mx >= 0 and input.my >= 0:
		var hw := camera.screenToWorldX(input.mx)
		var hh := camera.screenToWorldY(input.my)
		var he = w.entityAt(hw, hh)
		if he != null and he.kind != T.Kind.Projectile and w.visibleTo(me, he):
			hoveredId = he.id
	_lastGhost = ghost
	_lastDrag = {"active": false, "x0": 0.0, "y0": 0.0, "x1": 0.0, "y1": 0.0} if suppressDragBox or hud.minimapDragging else input.drag
	renderer.snapshot(w, camera, me, W, H)
	renderHovered = hoveredId
	_renderWorld(dt, W, H, _alpha())

	# ---- HUD (consumes pointer if clicked over panels) ----
	# Pass the full selection (any team) so the info panel can show a clicked
	# enemy/neutral unit's health; the command card filters to own units.
	# Everything from here down is interface, so it draws through the scale.
	var tUi := Time.get_ticks_usec()
	var ui := UI.ui
	var uis := uiScale()
	var UW := W / uis
	var UH := H / uis
	ui.pushScale(uis)
	# The Oath picker is modal: while it is up, nothing under it takes a click.
	var oathModal := hud.oathPicker.isOpen
	var frameClicked := ui.clicked
	if oathModal:
		ui.clicked = false
	if not (spectating and caster.clean):
		hud.draw(UW, UH, w, camera, me, selectedEntities(), dt, controller, attackMoveArmed, spectating, placing)
	# Beside the HUD's Menu button. Drawn here rather than in the HUD because
	# it needs the player's current binding for its tooltip, and the HUD
	# deliberately knows nothing about settings.
	if not spectating:
		FullscreenButton.fullscreenButton(UW - 108, 5, 30, 24, {"compact": true, "size": 13, "hotkey": fullscreenKeyLabel()})
	if spectating:
		drawCaster(UW, UH, w)
	if placing != null and not spectating and not ingameMenu:
		drawPlacementPanel(UW, UH)
	if w.mode != "conquest":
		drawModeStatus(UW, UH, w)
	drawControlGroups(UW, UH)
	if not spectating:
		drawQoLBar(UW, UH) # the caster view has its own controls
	if showProduction and not spectating:
		var jump = ProductionPanel.drawProductionPanel(UW, UH, w, me)
		if jump != null:
			var b = w.byId.get(jump)
			if b != null:
				select([jump])
				camera.centerOn(b.x, b.y)
	if showScoreboard:
		Scoreboard.drawScoreboard(UW, UH, w, me)
	if net != null or not chatLog.is_empty():
		Chat.drawChat(UW, UH, chatLog, chatDraft if net != null and chatOpen else null, time, UH - HUD.MINIMAP_SIZE - 70)
	if hud.oathPicker.isOpen and not spectating:
		# Only a click from a frame where it was already open counts — the
		# click that opened it must not also choose an Oath.
		ui.clicked = frameClicked if oathModal else false
		hud.oathPicker.draw(UW, UH, w, me, time, func(b, techId): controller.research(b, techId))
	ui.flushTooltip(UW, UH)

	# ---- in-game menu overlay ----
	if ingameMenu:
		ctx.fillStyle = "rgba(8, 6, 3, 0.6)"
		ctx.fillRect(0, 0, UW, UH)
		ui.panel(UW / 2 - 150, UH / 2 - 130, 300, 374, {"light": true})
		ui.text("Paused", UW / 2, UH / 2 - 100, {"align": "center", "size": 22, "bold": true, "color": C.PAL.uiAccent})
		if ui.button("Resume", UW / 2 - 110, UH / 2 - 64, 220, 44, {"accent": true, "size": 16}):
			ingameMenu = false
		if ui.button("⚙  Settings", UW / 2 - 110, UH / 2 - 12, 220, 44, {"size": 15}):
			openSettings("match") # returns to the (still-paused) match
		if ui.button("💾  Save Game", UW / 2 - 110, UH / 2 + 40, 220, 44, {
			"size": 15,
			"disabled": net != null,
			"tooltip": ["Not in multiplayer", "Resuming one side of a lockstep match would desync the others."] if net != null \
				else ["Save this match", "Stores the setup and every order you gave — reloading replays them."],
		}):
			saveMatch()
		FullscreenButton.fullscreenButton(UW / 2 - 110, UH / 2 + 92, 220, 44, {"size": 15, "hotkey": fullscreenKeyLabel()})
		if ui.button("Concede & Quit", UW / 2 - 110, UH / 2 + 144, 220, 44, {"danger": true, "size": 15}):
			finishMatch(false)
			ui.popScale()
			return
	ui.popScale()
	perf.uiMs = (Time.get_ticks_usec() - tUi) / 1000.0

	updateCursor(w, renderHovered)
	hud.shiftHeld = input.shift

	# ---- route unconsumed pointer input to the world ----
	# Spectators can still left-click/drag to select-and-inspect units, but
	# issue no commands.
	if not ingameMenu and not oathModal and not hud.oathPicker.isOpen:
		if frameDragEnd != null and not ui.pointerConsumed and not hud.minimapDragEnded:
			if not spectating and placing != null:
				paintWallLine(frameDragEnd)
			else:
				worldDragSelect(frameDragEnd)
		if frameDouble != null and not ui.pointerConsumed:
			worldDoubleClick(frameDouble.x, frameDouble.y)
		elif frameClick != null and not ui.pointerConsumed:
			worldClick(frameClick.x, frameClick.y)
		if not spectating and frameRight != null and not ui.pointerConsumed:
			if placing != null:
				placing = null
			else:
				var wx := camera.screenToWorldX(frameRight.x)
				var wy := camera.screenToWorldY(frameRight.y)
				issueContextCommand(wx, wy, frameRight)

	# ---- victory / defeat ----
	if spectating:
		# No stake in the fight — just announce the victor and bow out to the
		# menu. A replay stays open: the caster may want to scrub back.
		if w.winner != null and matchOverTimer < 0 and replay == null:
			matchOverTimer = 8.0
			hud.addAlert("🏆 %s wins the battle!" % teamLabel(w.winner))
			audio.play("levelup")
		if matchOverTimer > 0:
			matchOverTimer -= dt
			if matchOverTimer <= 0:
				exitToMenu()
	else:
		# The match ends for the human when a winner is decided, or the moment
		# their own realm is wiped out (the AIs may fight on, but the human is
		# out).
		var playerOut := w.player(me).defeated
		# Every client simulates the same match, so each reports the same
		# winner; the server rates it when the reports agree.
		if net != null and w.winner != null and not resultSent:
			resultSent = true
			net.transport.send({"t": "result", "winner": w.winner, "time": JS.round(w.time), "factions": netFactions})
		if (w.winner != null or playerOut) and matchOverTimer < 0:
			matchOverTimer = 1.8
			# Win if your alliance is the last standing (and you're still in it).
			playerWon = w.winner != null and w.areAllied(w.winner, me) and not playerOut
			hud.addAlert("🏆 The last enemy is broken!" if playerWon else "💀 Your last building has fallen…")
			if playerWon:
				audio.play("levelup")
			else:
				audio.play("collapse")
		if matchOverTimer > 0:
			matchOverTimer -= dt
			if matchOverTimer <= 0:
				finishMatch(playerWon)


## The simulation advances this frame (TS: not in the menu, not paused, and
## nobody has won — or it's a replay).
func _simRunning() -> bool:
	return world != null and not ingameMenu and not paused and (world.winner == null or replay != null)


## Interpolation factor: how far we are into the next sim tick (0..1).
func _alpha() -> float:
	var acc := netAccumulator if net != null else accumulator
	if net == null and (ingameMenu or paused):
		return 1.0
	return clampf(acc / SIM_DT, 0.0, 1.0)


## Camera movement from keys, edge scroll and the middle button (TS
## matchFrame's "camera movement"; also run while a tick is in flight).
func _moveCamera(dt: float, W: float, H: float) -> void:
	if ingameMenu or chatOpen:
		return
	var camSpeed := float(settings.scrollSpeed) / camera.zoom
	var edge := 16.0
	var dx := 0.0
	var dy := 0.0
	# Panning is held rather than tapped, so it reads the bound key's state
	# directly. `isDown` stores single characters lower-cased.
	var held := func(id: String) -> bool:
		var chord := Keybinds.chordFor(settings.keybinds, id)
		if chord == "" or chord.contains("+"):
			return false # a modifier chord can't be held sensibly
		return input.isDown(chord.to_lower() if chord.length() == 1 else chord)
	if held.call("panUp"):
		dy -= 1
	if held.call("panDown"):
		dy += 1
	if held.call("panLeft"):
		dx -= 1
	if held.call("panRight"):
		dx += 1
	# edge scroll (only with a real mouse, when enabled and inside the window)
	if settings.edgeScroll and not input.usingTouch and input.mx >= 0 and input.my >= 0:
		if input.mx < edge:
			dx -= 1
		if input.mx > W - edge:
			dx += 1
		if input.my < edge:
			dy -= 1
		if input.my > H - edge:
			dy += 1
	if dx != 0 or dy != 0:
		var l := JS.hypot(dx, dy)
		if l == 0:
			l = 1
		camera.pan((dx / l) * camSpeed * dt, (dy / l) * camSpeed * dt)
	# Hold the middle button and drag to grab the map.
	if input.middleDown and middleLast != null:
		camera.pan(-(input.mx - middleLast.x) / camera.zoom, -(input.my - middleLast.y) / camera.zoom)
	middleLast = {"x": input.mx, "y": input.my} if input.middleDown else null


## Draw the world and its overlays from the renderer's snapshot.
func _renderWorld(dt: float, W: float, H: float, alpha: float) -> void:
	renderer.render(renderer.snap, camera, particles, dt, time, me, markers, _lastGhost, _lastDrag, _rallyFrom, renderHovered, alpha)
	# ---- weather overlay (cosmetic, screen-space, over world & under HUD) ----
	if settings.weather:
		weather.render(renderer.octx, W, H, dt, 0.45 if settings.reduceEffects else 1.0)


## A frame drawn while the simulation is still stepping (see the header):
## everything that doesn't touch the World.
func _skipFrame(dt: float) -> void:
	time += dt
	if dt > 0:
		fps += (1 / dt - fps) * 0.1
	trackPerf(dt)
	if state != "match" or world == null:
		return
	if _simRunning():
		accumulator += dt * gameSpeed
	particles.update(dt)
	combatHeat *= pow(0.5, dt / 3)
	for m in markers:
		m.age += dt
	markers = markers.filter(func(m): return m.age < 0.7)
	var W := float(canvasW)
	var H := float(canvasH)
	_moveCamera(dt, W, H)
	renderer.ctx.begin(worldLayer, canvasW, canvasH)
	renderer.octx.begin(overlayLayer, canvasW, canvasH)
	# The snapshot is the tick before the ones in flight: show it at its end.
	_renderWorld(dt, W, H, 1.0)


# =========================================================== sim threading ==

func _taskBusy() -> bool:
	return _task >= 0 and not WorkerThreadPool.is_task_completed(_task)


## Wait for the task in flight (if any) and take its results.
func _joinTask() -> void:
	if _task < 0:
		return
	WorkerThreadPool.wait_for_task_completion(_task)
	_task = -1
	perf.ticksThisFrame += _taskTickCount
	if _taskTickCount > 0:
		var avg := _taskSumMs / _taskTickCount
		perf.tickMs += (avg - perf.tickMs) * minf(1.0, 0.12 * _taskTickCount)
		if _taskWorstMs > perf.worstTickMs:
			perf.worstTickMs = _taskWorstMs
	if _taskReplayEnded and replay != null:
		accumulator = 0
		if not replay.ended:
			replay.ended = true
			paused = true
			hud.addAlert("⏹ End of the recording — scrub the timeline to watch again, or Esc to leave.")
	if _taskSeek and world != null and replay != null and replaySeek != null:
		world.drainEvents() # a jump's worth of events would be noise
		if world.tickCount >= replaySeek:
			replaySeek = null
			accumulator = 0
			replay.ended = false


## End of an owning frame: hand the ticks that are due to the sim thread.
func _launchTicks() -> void:
	if _syncSim() or state != "match" or world == null or _task >= 0:
		return
	_taskTickCount = 0
	_taskSumMs = 0.0
	_taskWorstMs = 0.0
	_taskReplayEnded = false
	_taskSeek = false
	if replaySeek != null:
		_taskSeek = true
		_task = WorkerThreadPool.add_task(_seekTask, true, "sim seek")
		return
	if not _simRunning():
		return
	# Run the ticks the *next* frame will show, so its interpolation sits
	# between the right two ticks: what is due now plus about one more frame.
	var est := clampf(perf.frameMs / 1000.0, 1.0 / 240.0, 0.1)
	var predicted := accumulator + est * gameSpeed
	var maxSteps := 5 + int(ceil(gameSpeed)) * 2 # allow catch-up at high speed
	var steps := mini(maxSteps, int(floor(predicted / SIM_DT)))
	if steps <= 0:
		return
	accumulator -= steps * SIM_DT
	if steps == maxSteps and predicted - steps * SIM_DT >= SIM_DT:
		accumulator = 0 # drop time if we can't keep up
	_taskSteps = steps
	_task = WorkerThreadPool.add_task(_tickTask, true, "sim ticks")


## The sim thread: the TS match loop's inner step, `_taskSteps` times.
func _tickTask() -> void:
	var w := world
	for i in _taskSteps:
		if replay != null and _replayStepTask(w):
			_taskReplayEnded = true
			break
		var t0 := Time.get_ticks_usec()
		w.tick()
		for ai in ais:
			ai.update(SIM_DT)
		var cost := (Time.get_ticks_usec() - t0) / 1000.0
		_taskSumMs += cost
		_taskWorstMs = maxf(_taskWorstMs, cost)
		_taskTickCount += 1


## replayStep without the main-thread side effects (applied at the join).
func _replayStepTask(w: World) -> bool:
	var rp: Dictionary = replay
	if w.tickCount >= rp.endTick:
		return true
	var due = rp.byTick.get(w.tickCount)
	if due != null:
		for c in due:
			Commands.applyCommand(w, c)
	return false


## The sim thread, seeking a replay: a slice of the jump.
func _seekTask() -> void:
	_advanceSeekSlice()


# ======================================================== match overlays ==

## A readable name for a team, used in spectator callouts.
func teamLabel(t: int) -> String:
	return Spectator.teamLabel(t)


## Top-centre objective readout for the non-Conquest game modes.
func drawModeStatus(W: float, H: float, w: World) -> void:
	var ui := UI.ui
	var fmt := func(s: float) -> String: return "%d:%02d" % [floori(s / 60), floori(fmod(s, 60))]
	var line := ""
	if w.mode == "survival":
		var wave := w.survivalWave
		var total := w.survivalWavesTotal
		var hordeLeft := w.entitiesOf(w.hordeTeam, T.Kind.Unit).size() if w.hordeTeam >= 0 else 0
		if w.survivalWon:
			line = "Final wave! Clear %d remaining to win" % hordeLeft
		else:
			line = "🌊 Wave %d / %d" % [wave, total] + ("   ·   %d enemies left" % hordeLeft if hordeLeft > 0 else "   ·   brace for the next wave")
	elif w.mode == "koth":
		# Show the player's alliance hold vs the best enemy hold.
		var prog := w.kothProgress()
		var mine := 0.0
		var foe := 0.0
		for pr in prog:
			if w.areAllied(me, pr.team):
				mine = maxf(mine, pr.hold)
			else:
				foe = maxf(foe, pr.hold)
		line = "👑 Hold the Hill — You %s / %s   ·   Enemy %s" % [fmt.call(mine), fmt.call(w.kothGoal), fmt.call(foe)]
	elif w.mode == "regicide":
		var myKing := w.entities.any(func(e): return e.alive and e.type == "king" and w.areAllied(me, e.team))
		var foeKings := w.entities.filter(func(e): return e.alive and e.type == "king" and not w.areAllied(me, e.team)).size()
		line = "♚ Regicide — Your King: %s   ·   Enemy Kings: %d" % ["alive" if myKing else "FALLEN", foeKings]
	if line == "":
		return
	var pw := 460.0
	ui.panel(W / 2 - pw / 2, 38, pw, 28, {"light": true})
	ui.text(line, W / 2, 56, {"align": "center", "size": 14, "bold": true, "color": C.PAL.uiAccent})


## Rich live spectator HUD: one detailed card per realm (army, villagers, pop,
## resources, gathered, K/L, bases + a military-strength bar). Drawing lives in
## ui/spectator; here we layer click-to-follow onto the returned card rects.
func drawSpectatorHud(W: float, H: float, w: World) -> void:
	var ui := UI.ui
	var rects := Spectator.drawSpectatorPanels(W, H, w, gameSpeed, paused)
	for r in rects:
		if not ui.hit(r.x, r.y, r.w, r.h):
			continue
		ui.pointerConsumed = true
		if ui.clicked:
			var st = w.map.starts[r.team] if r.team < w.map.starts.size() else null
			var fx: float = r.focus.x if r.focus != null else (st.x if st != null else w.worldW / 2)
			var fy: float = r.focus.y if r.focus != null else (st.y if st != null else w.worldH / 2)
			camera.centerOn(fx, fy)
			audio.play("ui")


# ------------------------------------------------------------------ caster --
func drawCaster(W: float, H: float, w: World) -> void:
	var ui := UI.ui
	var SIM_HZ: float = C.SIM_HZ
	var req := caster.draw(W, H, {
		"world": w,
		"names": playerNames,
		"history": matchHistory,
		"speed": gameSpeed,
		"paused": paused,
		"source": "replay" if replay != null else ("delay" if net != null and casterDelayTicks > 0 else "live"),
		"delaySec": net.behindSeconds(SIM_HZ) if net != null else 0.0,
		"replay": {"tick": w.tickCount, "endTick": replay.endTick} if replay != null else null,
		"offline": net == null,
	})
	if req.get("exit", false):
		exitToMenu()
		return
	if req.get("download", false) and replay != null:
		var f := Replay.replayFile(replay.rec)
		hud.addAlert("⬇ Saved %s" % f.name if Files.downloadText(f.name, f.text) else "Your browser blocked the download.")
	if req.get("focus") != null:
		camera.centerOn(req.focus.x, req.focus.y)
		caster.manual(w)
		audio.play("ui")
	if req.has("speed") and net == null:
		setSpeed(req.speed)
	if req.get("togglePause", false) and net == null:
		paused = not paused
		if replay != null and replay.ended and not paused:
			paused = true
	if req.has("seekTo") and replay != null:
		seekReplay(req.seekTo)
	if replaySeek != null:
		var pct := int(JS.round((float(w.tickCount) / maxf(1, replaySeek)) * 100)) if replay != null else 0
		ui.panel(W / 2 - 150, H / 2 - 30, 300, 60)
		ui.text("Jumping to %d:%02d… %d%%" % [floori(replaySeek / SIM_HZ / 60), floori(fmod(replaySeek / SIM_HZ, 60)), pct], W / 2, H / 2 + 5, {"align": "center", "size": 15, "bold": true, "color": "#ffe9b0"})


## A replay's orders for this tick, applied before it runs. True once it has ended.
func replayStep(w: World) -> bool:
	var rp: Dictionary = replay
	if replaySeek != null:
		return true
	if w.tickCount >= rp.endTick:
		if not rp.ended:
			rp.ended = true
			paused = true
			hud.addAlert("⏹ End of the recording — scrub the timeline to watch again, or Esc to leave.")
		return true
	var due = rp.byTick.get(w.tickCount)
	if due != null:
		for c in due:
			Commands.applyCommand(w, c)
	return false


## A brief line from each assigned builder to the site, so you see who's going.
func flashBuilders(builders: Array, x: float, y: float) -> void:
	for b in builders.slice(0, 12):
		builderLines.append({"x0": b.x, "y0": b.y, "x1": x, "y1": y, "age": 0.0})


## The pointer says what a right-click would do: attack over an enemy, gather
## over a resource, build/repair over your own unfinished or damaged building,
## place while placing.
func updateCursor(w: World, hoveredId: int) -> void:
	var c := "default"
	if state == "match" and not spectating and not ingameMenu and not UI.ui.pointerConsumed:
		var sel := playerSelection()
		var he = w.byId.get(hoveredId) if hoveredId >= 0 else null
		var units := sel.filter(func(e): return e.kind == T.Kind.Unit)
		var vills := units.filter(func(e): return _canBuild(e))
		if placing != null:
			c = "crosshair"
		elif attackMoveArmed:
			c = "attack"
		elif he != null and not units.is_empty():
			var tb = C.BUILDINGS.get(he.type)
			if he.team != me and he.kind != T.Kind.Resource and w.areHostile(me, he.team):
				c = "attack"
			elif not vills.is_empty() and (he.kind == T.Kind.Resource or he.type == "farm"):
				c = "gather"
			elif not vills.is_empty() and he.team == me and he.kind == T.Kind.Building and (he.buildState != T.BuildState.Done or he.hp < he.maxHp):
				c = "build"
			elif he.team == me and he.kind == T.Kind.Building and tb != null and float(tb.get("garrisonCap", 0)) > 0:
				c = "pointer"
	if c != cursorNow:
		cursorNow = c
		_setCursor(c)


func _setCursor(c: String) -> void:
	if DisplayServer.get_name() == "headless":
		return
	if CURSORS.has(c):
		var tex = _cursorTex.get(c)
		if tex == null:
			var img := Image.new()
			if img.load_svg_from_string(CURSORS[c][0], 1.0) == OK:
				tex = ImageTexture.create_from_image(img)
				_cursorTex[c] = tex
		if tex != null:
			Input.set_custom_mouse_cursor(tex, Input.CURSOR_ARROW, Vector2(CURSORS[c][1], CURSORS[c][2]))
			return
		c = CURSORS[c][3]
	Input.set_custom_mouse_cursor(null)
	match c:
		"crosshair": Input.set_default_cursor_shape(Input.CURSOR_CROSS)
		"pointer": Input.set_default_cursor_shape(Input.CURSOR_POINTING_HAND)
		_: Input.set_default_cursor_shape(Input.CURSOR_ARROW)


## The panel beside the cursor while placing: name, real cost, why not, what it serves.
func drawPlacementPanel(W: float, H: float) -> void:
	var info = placementInfo
	if info == null or world == null:
		return
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	var def = C.BUILDINGS.get(info.type)
	var res: Dictionary = world.player(me).resources
	var rows: Array = []
	var costParts: Array = []
	for k in ["food", "wood", "gold"]:
		if info.cost[k]:
			costParts.append("%s %s" % [JS.num(info.cost[k]), k])
	rows.append({"text": "%s%s" % [def.name if def != null else info.type, "  ·  %s" % ", ".join(costParts) if not costParts.is_empty() else ""], "color": PAL.uiBad if info.short else "#ffe9b0", "bold": true, "size": 13.5})
	if info.get("drag") != null:
		rows.append({"text": info.drag, "color": "#e2d6ba"})
	elif info.problem != null:
		rows.append({"text": info.problem, "color": PAL.uiBad, "bold": true})
	elif info.short:
		var need: Array = []
		for k in ["food", "wood", "gold"]:
			if res[k] < info.cost[k]:
				need.append("%s more %s" % [JS.num(ceil(info.cost[k] - res[k])), k])
		rows.append({"text": "Need %s" % " and ".join(need), "color": PAL.uiBad, "bold": true})
	if info.coverage != "":
		rows.append({"text": info.coverage, "color": "#e0a070" if (info.coverage as String).begins_with("No ") else "#9fd08a"})
	if info.builders == 0:
		rows.append({"text": "No villager selected — it will wait for a builder", "color": "#e0a070"})
	else:
		rows.append({"text": "%d villager%s will build it" % [info.builders, "" if info.builders == 1 else "s"], "color": "#a89f88"})
	rows.append({"text": "Drag to lay a line · right-click to stop" if LINE_BUILDABLE.has(info.type) else \
		("Drag for a block · Shift: keep placing · right-click: cancel" if BLOCK_BUILDABLE.has(info.type) else "Shift: keep placing · right-click: cancel"),
		"color": "#8f8770", "size": 11})
	var w := 0.0
	for r in rows:
		ctx.font = "%s%spx 'Trebuchet MS', sans-serif" % ["bold " if r.get("bold", false) else "", JS.num(r.get("size", 12))]
		w = maxf(w, ctx.measureText(r.text).width)
	w += 20
	var h := rows.size() * 18.0 + 10
	var x := input.mx / uiScale() + 22
	var y := input.my / uiScale() + 18
	if x + w > W - 8:
		x = input.mx / uiScale() - w - 16
	if y + h > H - 8:
		y = input.my / uiScale() - h - 12
	ctx.fillStyle = "rgba(12,9,5,0.86)"
	ctx.beginPath(); ctx.roundRect(x, y, w, h, 6); ctx.fill()
	ctx.strokeStyle = Palette.withAlpha(PAL.uiBad, 0.6) if info.problem != null else Palette.withAlpha(PAL.uiAccent, 0.4)
	ctx.lineWidth = 1
	ctx.stroke()
	for i in rows.size():
		var r: Dictionary = rows[i]
		ui.text(r.text, x + 10, y + 20 + i * 18, {"size": r.get("size", 12), "bold": r.get("bold", false), "color": r.color})


func seekReplay(tick: int) -> void:
	var rp = replay
	if rp == null or world == null:
		return
	var target := clampi(tick, 0, int(rp.endTick))
	# The sim only runs forwards: going back means starting again.
	if target < world.tickCount:
		startReplay(rp.rec, true)
	replaySeek = target
	paused = false


## Run part of a pending seek; called once per frame (TS advanceSeek).
func advanceSeek() -> void:
	_advanceSeekSlice()
	if world == null or replay == null or replaySeek == null:
		return
	world.drainEvents() # a jump's worth of events would be noise
	if world.tickCount >= replaySeek:
		replaySeek = null
		accumulator = 0
		replay.ended = false


## Up to ~28 ms of a seek's ticks.
func _advanceSeekSlice() -> void:
	var w := world
	var rp = replay
	if w == null or rp == null or replaySeek == null:
		return
	var t0 := Time.get_ticks_msec()
	var SIM_HZ := int(C.SIM_HZ)
	while w.tickCount < replaySeek and Time.get_ticks_msec() - t0 < 28:
		var due = rp.byTick.get(w.tickCount)
		if due != null:
			for c in due:
				Commands.applyCommand(w, c)
		w.tick()
		for ai in ais:
			ai.update(SIM_DT)
		if w.tickCount % (SIM_HZ * 4) == 0:
			sampleHistory(w)


## A replay file's text (dropped, or opened): keep it and play it.
func openReplayText(name: String, text: String) -> void:
	var res := Replay.parseReplayFile(text)
	if not res.ok:
		state = "career"
		if careerScreen != null:
			careerScreen.showReplays()
			careerScreen.setNotice("%s: %s" % [name, res.error], true)
		return
	Replay.saveReplay(res.replay)
	if careerScreen != null:
		careerScreen.setNotice(res.warning if res.get("warning") != null else "Opened %s." % name, res.get("warning") != null)
	startReplay(res.replay)
	if res.get("warning") != null:
		hud.addAlert("⚠ %s" % res.warning)


## Godot: a file dropped on the window (TS: a drop on the page).
func _onFilesDropped(files: PackedStringArray) -> void:
	if files.is_empty():
		return
	if state == "match" and replay == null:
		hud.addAlert("Finish or leave this match to open a replay.")
		return
	var f := FileAccess.open(files[0], FileAccess.READ)
	if f != null:
		openReplayText(files[0].get_file(), f.get_as_text())


func _enter_tree() -> void:
	get_window().files_dropped.connect(_onFilesDropped)


## Watch a recorded match with the caster view.
func startReplay(rec: Dictionary, quiet: bool = false) -> void:
	_joinTask()
	var stp = rec.get("setup")
	if rec.get("mapCode") != null and stp != null and str(stp.get("presetId", "")).begins_with("custom_"):
		var m = CustomMaps.deserialiseMap(rec.mapCode)
		if m != null:
			m.id = stp.presetId
			m.erase("published")
			CustomMaps.saveCustomMap(m)
	loadingSave = true
	if rec.get("kind") == "online" and rec.get("net") != null:
		buildReplayNetWorld(rec.net)
	elif rec.get("kind") == "watch" and stp != null:
		startSpectate((stp as Dictionary).duplicate(true))
	elif stp != null:
		startMatch((stp as Dictionary).duplicate(true))
	loadingSave = false
	var w := world
	if w == null:
		return
	spectating = true
	w.revealAll = true
	replay = {"rec": rec, "byTick": Replay.byTick(rec.commands), "endTick": int(rec.endTick), "ended": false}
	replaySeek = null
	playerNames = (rec.get("names", []) as Array).duplicate()
	if not quiet:
		caster.reset()
		gameSpeed = 1
		camera.zoom = 0.85
		camera.centerOn(w.worldW / 2, w.worldH / 2)
		hud.alerts = []
		var s: Dictionary = rec.get("summary", {})
		hud.addAlert("▶ Replay — %s, %s players. Press ? for caster keys." % [s.get("map", ""), JS.num(float(s.get("players", 0)))])
	paused = false


## Rebuild an online match's opening exactly as startNetMatch did, without a network.
func buildReplayNetWorld(nt: Dictionary) -> void:
	var seed := int(nt.seed)
	var numTeams := int(nt.numTeams)
	var custom = CustomMaps.deserialiseMap(nt.map.code) if nt.map.get("code") != null else null
	var map: Dictionary
	if custom != null:
		map = CustomMaps.toMapData(custom, seed, numTeams, false)
	else:
		var id: String = nt.map.get("id", "open_plains")
		var known: bool = C.PRESETS.any(func(p): return p.id == id) or id == "random"
		map = MapGen.generateMap(id if known else "open_plains", seed, numTeams, false, nt.alliances)
	var w := World.new(seed)
	var teams: Array = []
	for i in numTeams:
		teams.append(i)
	w.init(map, teams.map(func(_t): return profile.matchLoadout(true)), teams.map(func(_t): return 1.0), nt.alliances, teams.map(func(_t): return ""), false, null, "conquest", nt.factions)
	endNet()
	world = w
	ais = []
	_prepareMatchView(map)
	selection = []
	markers = []
	placing = null
	ingameMenu = false
	matchOverTimer = -1
	camera.setWorld(map.worldW, map.worldH)
	accumulator = 0
	resetMatchTelemetry()
	me = T.Team.Player
	state = "match"


## Keep this match as a replay (called as it ends).
func recordReplay(w: World, kind: String) -> void:
	if replay != null or w.tickCount < int(C.SIM_HZ) * 10:
		return # a replay of a replay, or a false start
	var players: Array = []
	for t in w.numTeams:
		if t != w.hordeTeam:
			players.append(t)
	var winner = w.winner if w.winner != null and w.winner != T.Team.Neutral else null
	var stp = config.duplicate(true) if config != null else null
	var mapCode = null
	if stp != null and str(stp.get("presetId", "")).begins_with("custom_"):
		var m = CustomMaps.findCustomMap(stp.presetId)
		if m != null:
			mapCode = CustomMaps.serialiseMap(m)
	var commands: Array
	if kind == "online":
		var rec = net.lock.record if net != null and net.lock != null and net.lock.record != null else []
		commands = rec.map(func(e): return {"t": e.t, "c": e.c})
	elif kind == "watch":
		commands = []
	else:
		commands = cmdLog.entries.duplicate(true)
	var r := {
		"version": 1, "id": Replay.replayId(), "savedAt": JS.now_ms(), "kind": kind, "sim": Savegame.SAVE_FORMAT_VERSION,
		"names": playerNames.duplicate(), "pov": -1 if spectating else me, "commands": commands, "endTick": w.tickCount,
		"summary": {
			"map": mapName if mapName != "" else "Battlefield", "players": players.size(), "durationSec": JS.round(w.time),
			"result": "Unfinished" if winner == null else "%s won" % (playerNames[winner] if winner < playerNames.size() and str(playerNames[winner]) != "" else C.PAL.teams[winner].name),
			"factions": players.map(func(t): return w.player(t).faction),
		},
	}
	if kind != "online" and stp != null:
		r.setup = stp
	if mapCode != null:
		r.mapCode = mapCode
	if kind == "online" and netSetup != null:
		r.net = netSetup
	Replay.saveReplay(r)


## Tear down the current match and return to the main menu (spectator exit).
func exitToMenu() -> void:
	_joinTask()
	if world != null and spectating and replay == null:
		recordReplay(world, "online" if net != null else "watch")
	var wasReplay := replay != null
	replay = null
	replaySeek = null
	if world != null:
		world.revealAll = false
	world = null
	ais = []
	spectating = false
	ingameMenu = false
	matchOverTimer = -1
	endNet()
	me = T.Team.Player
	state = "career" if wasReplay else "menu"
	if wasReplay and careerScreen != null:
		careerScreen.showReplays()
	# Watching an online match: back to that server's hub.
	if netHub != "" and lobby != null:
		var hub := netHub
		netHub = ""
		lobby.reopenHub(func(start): startNetMatch(start), hub, lobbyOptions())


## The single place a match decides what it is being played on. A preset id
## is generated; a custom map id is looked up and converted. Everything else —
## skirmish, spectate, testing from the editor — goes through this, so a
## custom map is never a second-class citizen with its own code path.
func resolveMap(presetId: String, seed: int, players: int, nomad: bool, alliances = null) -> Dictionary:
	# Random draws from the whole pool, published maps included. The result is
	# written back into the config so a save records the field it was on.
	if presetId == "random" and config != null:
		var mode: String = config.get("mode", "conquest")
		presetId = CustomMaps.rollRandomMap(seed, CustomMaps.mapPool(mode, players - 1 if mode == "survival" else players), C.PRESETS.size())
		config.presetId = presetId
	if presetId.begins_with("custom_"):
		var m = CustomMaps.findCustomMap(presetId)
		if m != null:
			return CustomMaps.toMapData(m, seed, players, nomad)
		hud.addAlert("That custom map is missing — falling back to a generated one.")
	return MapGen.generateMap(presetId, seed, players, nomad, alliances)


## What the lobby needs to know about this player.
func lobbyOptions() -> Dictionary:
	return {"name": profile.data.name, "factions": profile.ownedFactions(), "faction": profile.playableFaction()}


## Open the Factions book on a faction, remembering where to come back to.
func openFactions(from: String, focus = null) -> void:
	factionsReturn = from
	if factionBook != null:
		factionBook.focus(focus)
	state = "factions"
	audio.play("ui")


## Play the map currently open in the editor, and come back to it after.
func testCustomMap(m: Dictionary) -> void:
	CustomMaps.saveCustomMap(m) # so resolveMap can find it by id
	var base: Dictionary = setup.config if setup != null else defaultConfig()
	var cfg := base.duplicate(true)
	cfg.presetId = m.id
	cfg.players = clampi(2, int(m.minPlayers), int(m.maxPlayers))
	cfg.mode = m.modes[0] if (m.modes as Array).size() > 0 else "conquest"
	cfg.nomad = m.get("nomad") == "forced"
	cfg.seed = RNG.randomSeed()
	if setup != null:
		setup.config = cfg
	editorReturn = true
	startMatch(cfg.duplicate(true))


## Each realm's faction: yours as picked, each bot's as set on the setup
## screen or else drawn from the match seed. Its own random stream, so the
## picks don't disturb anything else the seed decides — and so a save, which
## is the config plus the orders, rebuilds the same match.
func factionsFor(cfg: Dictionary, n: int, humanFirst: bool) -> Array:
	var rng := RNG.new(JS.u32(int(cfg.seed) ^ 0xfac7105))
	var fids: Array = C.FACTION_IDS
	var valid := func(id) -> bool: return id != null and str(id) != "" and C.FACTIONS.has(id)
	var aif: Array = cfg.get("aiFactions", [])
	var out: Array = []
	for t in n:
		var roll: String = fids[rng.int_(0, fids.size() - 1)]
		# Yours has to be one you own (a save replays as it was played).
		if t == 0 and humanFirst:
			var f = cfg.get("faction")
			if valid.call(f) and (loadingSave or profile.ownsFaction(f)):
				out.append(f)
			else:
				var pf = profile.playableFaction()
				out.append(pf if pf != null else C.DEFAULT_FACTION)
		else:
			out.append(aif[t] if t < aif.size() and valid.call(aif[t]) else roll)
	return out


func finishMatch(won: bool) -> void:
	_joinTask()
	var w := world
	var p := w.player(me)
	matchWon = won
	endReport = Metrics.matchReport(w, me, mapName if mapName != "" else "Skirmish")
	endDuration = w.time
	# Aggregate the time-series into your-alliance vs enemies for the graphs.
	if matchHistory.size() >= 2:
		var keys := ["score", "military", "economy"]
		var mine := {}
		var foeS := {}
		for k in keys:
			mine[k] = matchHistory.map(func(s): return s.m.reduce(func(a, tm): return a + (tm[k] if w.areAllied(me, tm.team) else 0.0), 0.0))
			foeS[k] = matchHistory.map(func(s): return s.m.reduce(func(a, tm): return a + (tm[k] if w.areHostile(me, tm.team) else 0.0), 0.0))
		# And a line per realm, so a free-for-all isn't drawn as you against
		# the sum of everyone else.
		var players: Array = []
		for t in w.numTeams:
			var values := {}
			for k in keys:
				values[k] = matchHistory.map(func(s): return s.m[t][k] if t < s.m.size() else 0.0)
			players.append({"team": t, "you": t == me, "horde": t == w.hordeTeam, "values": values})
		endGraph = {"ts": matchHistory.map(func(s): return s.t), "mine": mine, "foe": foeS, "players": players}
	else:
		endGraph = null
	xpBefore = profile.data.totalXp
	var rewards := Progression.computeRewards({
		"win": won,
		"durationSec": w.time,
		"unitsKilled": p.stats.unitsKilled,
		"buildingsRazed": p.stats.buildingsRazed,
		"difficulty": config.get("difficulty", "knight") if config != null else "knight",
		"fairMode": config.get("fairMode", false) if config != null else false,
	})
	matchRewards = rewards
	var lvl: Dictionary = profile.addXp(rewards.xp)
	levelsGained = int(lvl.levelsGained)
	profile.addRenown(rewards.renown)
	profile.addValor(rewards.valor)
	profile.recordResult(won)
	# Keep the game. The deep report is thrown away at Continue, so a summary
	# goes to the history store before it is; awards are then evaluated off
	# that same record, which keeps a condition to something that can actually
	# be read from a finished match.
	var record := History.summarise(endReport, {
		"won": won,
		"mode": config.get("mode", "conquest") if config != null else "conquest",
		"difficulty": config.get("difficulty", "knight") if config != null else "knight",
		"players": config.get("players", 2) if config != null else 2,
		"at": JS.now_ms(),
	})
	History.recordMatch(record)
	# The career: every match, skirmish or online, for the player's own stats.
	var online := net != null
	recordReplay(w, "online" if online else "skirmish")
	Career.recordCareer(Career.careerMatch(endReport, {
		"at": JS.now_ms(),
		"won": won,
		"kind": "online" if online else "skirmish",
		"ranked": online and netRanked,
		"mode": "conquest" if online else (config.get("mode", "conquest") if config != null else "conquest"),
		"difficulty": config.get("difficulty", "knight") if config != null else "knight",
		"commander": config.commander if config != null and str(config.get("commander", "")) != "" else profile.data.commander,
	}))
	# A Nemesis match: the rival learns, rises, scars or falls.
	if postmatch != null:
		postmatch.rivalLines = []
	if config != null and config.get("rival") != null and not online and replay == null:
		var pf = profile.playableFaction()
		var st := Rival.loadRival(pf if pf != null else "")
		var out := Rival.settleRival(st, Career.careerMatch(endReport, {"at": JS.now_ms(), "won": won, "kind": "skirmish", "mode": "conquest", "difficulty": config.difficulty, "commander": ""}), pf if pf != null else "")
		if out.get("renown", 0):
			profile.addRenown(out.renown)
		if postmatch != null:
			postmatch.rivalLines = out.lines
	matchAwards = profile.claimAwards(Achievements.evaluateAwards(record, profile.awardState()))
	profile.save()
	if postmatch != null:
		postmatch.reset()
	world = null
	ais = []
	ingameMenu = false
	endNet() # close any net link and clear the session
	me = T.Team.Player # back to the default perspective for the next match
	state = "postmatch"


# ============================================================ Placeholders ==
# Minimal stand-ins for screens ported elsewhere, used only while that module
# isn't in this build: enough to play end to end.

func _title(W: float, H: float, title: String, sub: String = "") -> void:
	var ui := UI.ui
	ctx.fillStyle = "#14100a"
	ctx.fillRect(0, 0, W, H)
	var g := ctx.createLinearGradient(0, 0, 0, H)
	g.addColorStop(0, "#2a1f30")
	g.addColorStop(1, "#0d0a07")
	ctx.fillStyle = g
	ctx.fillRect(0, 0, W, H)
	ui.text(title, W / 2, H * 0.2, {"align": "center", "size": 44, "bold": true, "color": "#ffe9b0", "font": "Georgia, serif"})
	if sub != "":
		ui.text(sub, W / 2, H * 0.2 + 44, {"align": "center", "size": 15, "color": "#cabfa4"})


func _placeholderMenu(W: float, H: float):
	var ui := UI.ui
	_title(W, H, "Banner & Blade", "A deterministic real-time strategy game")
	var x := W / 2 - 150
	var y := H * 0.36
	var action = null
	if ui.button("⚔  Skirmish (1 v 1 vs AI)", x, y, 300, 48, {"accent": true, "size": 17}):
		action = "skirmish"
	y += 60
	if ui.button("👁  Watch an AI battle", x, y, 300, 44, {"size": 15}):
		action = "watch"
	y += 56
	var saves := Savegame.listSaves()
	if ui.button("💾  Resume — %s" % saves[0].label if not saves.is_empty() else "💾  No saved game", x, y, 300, 44, {"size": 14, "disabled": saves.is_empty()}):
		action = "resume_latest"
	y += 56
	if ui.button("📜  Replays", x, y, 300, 44, {"size": 15}):
		action = "career"
	y += 56
	if settingsScreen != null and ui.button("⚙  Settings", x, y, 300, 44, {"size": 15}):
		action = "settings"
	ui.text("The full menu arrives with the screens module.", W / 2, H - 30, {"align": "center", "size": 12, "color": "#8f866f"})
	return action


func _placeholderScreen(W: float, H: float, name: String, extra: String = ""):
	var ui := UI.ui
	_title(W, H, name, "This screen isn't in this build yet.")
	if extra == "fight" and ui.button("Fight your Nemesis", W / 2 - 120, H * 0.45, 240, 46, {"accent": true, "size": 16}):
		return "fight"
	if ui.button("Back", W / 2 - 90, H * 0.55, 180, 44, {"size": 15}):
		return "back"
	return null


func _placeholderCareer(W: float, H: float):
	var ui := UI.ui
	_title(W, H, "Replays")
	var reps := Replay.listReplays()
	var y := H * 0.3
	if reps.is_empty():
		ui.text("No replays yet — finish a match to record one.", W / 2, y, {"align": "center", "size": 15, "color": "#cabfa4"})
	var out = null
	for r in reps.slice(0, 8):
		var s: Dictionary = r.get("summary", {})
		ui.text("%s · %s players · %s" % [s.get("map", "?"), JS.num(float(s.get("players", 0))), s.get("result", "")], W / 2 - 260, y, {"size": 14})
		if ui.button("Watch", W / 2 + 180, y - 16, 90, 32, {"size": 13, "accent": true}):
			out = {"watch": r}
		y += 44
	if ui.button("Back", W / 2 - 90, H - 90, 180, 44, {"size": 15}):
		out = "back"
	return out


func _placeholderPostMatch(W: float, H: float):
	var ui := UI.ui
	_title(W, H, "Victory!" if matchWon else "Defeat", "%s · %s" % [mapName, mmss(endDuration)])
	var r: Dictionary = matchRewards
	var y := H * 0.38
	for row in r.get("breakdown", []):
		ui.text(row.label, W / 2 - 200, y, {"size": 15})
		ui.text("+%s XP   +%s renown   +%s valor" % [JS.num(row.xp), JS.num(row.renown), JS.num(row.valor)], W / 2 + 200, y, {"align": "right", "size": 14, "color": "#e8c98a"})
		y += 26
	ui.text("Total: +%s XP" % JS.num(r.xp), W / 2, y + 16, {"align": "center", "size": 18, "bold": true, "color": "#ffe9b0"})
	if ui.button("Continue", W / 2 - 100, H - 110, 200, 48, {"accent": true, "size": 17}):
		return "continue"
	return null
