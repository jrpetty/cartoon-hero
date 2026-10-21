extends TestCase
# The in-match interface, ported from the TS suite: ui/production_panel.test.ts,
# ui/scoreboard.test.ts, ui/chat.test.ts, ui/oath_picker.test.ts,
# ui/caster.test.ts and engine/fullscreen.test.ts — plus the HUD (no TS test
# of its own: its command card, minimap and selection panel through clicks).

const SoftCanvas := preload("res://tests/soft_canvas.gd")
const W := 1280.0
const H := 760.0


## A UI that records every button it draws (the TS tests wrap ui.button).
class RecordingUI extends UI:
	var buttons: Array = []

	func button(label: String, x: float, y: float, w: float, h: float, opts: Dictionary = {}) -> bool:
		buttons.append({"label": label, "x": x, "y": y, "w": w, "h": h, "disabled": opts.get("disabled", false)})
		return super.button(label, x, y, w, h, opts)


static func begin(mx: float = 0, my: float = 0, clicked: bool = false, right: bool = false) -> void:
	UI.ui.begin(Ctx.new(), {"mx": mx, "my": my, "clicked": clicked, "rightClicked": right, "alt": false})


static func world(seed: int, players: int = 2, alliances = null) -> World:
	var w := World.new(seed)
	var lo: Array = []
	var ec: Array = []
	for i in players:
		lo.append({})
		ec.append(1.0)
	w.init(MapGen.generateMap("open_plains", seed, players), lo, ec, alliances)
	return w


static func tcOf(w: World, team: int) -> Entity:
	for e: Entity in w.entities:
		if e.alive and e.team == team and e.type == "town_center":
			return e
	return null


# --------------------------------------------------------- production panel --

func test_production_panel_renders_and_reports_clicked_rows() -> void:
	var w := world(5)
	var p := w.player(0)
	p.age = 2
	p.resources.food = 9999.0
	p.resources.wood = 9999.0
	p.resources.gold = 9999.0
	var barracks := w.spawnBuilding(0, "barracks", 800, 800, true)
	var stable := w.spawnBuilding(0, "stable", 880, 800, true)
	w.trainUnit(0, barracks.id, "militia") # an active producer
	begin()
	eq(ProductionPanel.drawProductionPanel(W, H, w, 0), null, "no click, no jump")
	# Scan a click down the panel; both production buildings should be hittable.
	var seen := {}
	for my in range(300, 480, 2):
		begin(24, my, true)
		var r = ProductionPanel.drawProductionPanel(W, H, w, 0)
		if r != null:
			seen[r] = true
	check(seen.has(barracks.id), "the barracks row is clickable")
	check(seen.has(stable.id), "the stable row is clickable")


func test_production_panel_is_safe_with_no_production_buildings() -> void:
	var w := world(6)
	for e in w.entitiesOf(0):
		if e.type == "town_center":
			w.dealDamage(1, e, 1e9, "test")
	begin()
	eq(ProductionPanel.drawProductionPanel(W, H, w, 0), null, "nothing to click")


# --------------------------------------------------------------- scoreboard --

func test_scoreboard_draws_a_2v2() -> void:
	var w := world(7, 4, [0, 0, 1, 1])
	w.spawnUnit(0, "knight", 800, 800)
	w.player(1).stats.unitsKilled = 5.0
	w.player(2).defeated = true # a fallen realm renders dimmed
	UI.ui.begin(SoftCanvas.new(320, 200), {"mx": 0, "my": 0, "clicked": false, "rightClicked": false})
	Scoreboard.drawScoreboard(W, H, w, 0)
	check(true, "drew")


# --------------------------------------------------------------------- chat --

func test_chat_renders_log_and_draft() -> void:
	begin()
	var lines := [
		{"name": "Azure", "text": "glhf", "team": 0, "t": 0.0},
		{"name": "Crimson", "text": "good luck", "team": 1, "t": 4.0},
	]
	Chat.drawChat(W, H, lines, null, 10, 600)
	Chat.drawChat(W, H, lines, "typing a message", 10, 600)
	Chat.drawChat(W, H, [], "", 0, 600)
	var old := [{"name": "X", "text": "old", "team": 0, "t": 0.0}]
	Chat.drawChat(W, H, old, null, 100, 600)
	Chat.drawChat(W, H, old, "x", 100, 600)
	check(true, "drew")


func test_chat_labels_an_author_by_team() -> void:
	check(Chat.authorLabel(0).length() > 0, "a name")
	eq(Chat.authorLabel(0), C.PAL.teams[0].name, "the team's colour name")


# -------------------------------------------------------------- oath picker --

func oathSetup() -> Array:
	var w := world(3)
	var p := w.player(0)
	p.resources = {"food": 9999.0, "wood": 9999.0, "gold": 9999.0}
	var tc := tcOf(w, 0)
	var start = w.map.starts[0]
	for t in ["barracks", "mill"]:
		var placed := false
		var r := 160
		while r < 600 and not placed:
			for a in 12:
				if placed:
					break
				var b = w.placeBuilding(0, t, start.x + JSMath.cos(a) * r, start.y + JSMath.sin(a) * r)
				if b != null:
					b.buildState = T.BuildState.Done
					placed = true
			r += 40
	return [w, tc]


func oathFrame(pk: OathPicker, w: World, click, swear: Callable) -> Array:
	var rec := RecordingUI.new()
	var keep = UI._shared
	UI._shared = rec
	rec.begin(Ctx.new(), {"mx": click.x if click != null else 0.0, "my": click.y if click != null else 0.0,
		"clicked": click != null, "rightClicked": false, "alt": false})
	pk.draw(W, H, w, 0, 0, swear)
	UI._shared = keep
	return rec.buttons


func test_oath_picker_offers_three_and_swears_the_one_clicked() -> void:
	var s := oathSetup()
	var w: World = s[0]
	var pk := OathPicker.new()
	pk.open(s[1])
	var buttons := oathFrame(pk, w, null, func(_b, _id): pass)
	var swearButtons := buttons.filter(func(b): return b.label == "Swear this Oath")
	eq(swearButtons.size(), 3, "three cards")
	var sworn: Array = []
	var second: Dictionary = swearButtons[1]
	oathFrame(pk, w, {"x": second.x + second.w / 2, "y": second.y + second.h / 2}, func(_b, id): sworn.append(id))
	eq(sworn, ["age:%s" % Oaths.oathsForAge(1)[1].id], "the second Oath sworn")
	check(not pk.isOpen, "closed after the choice")


func test_oath_picker_closes_once_an_advance_is_under_way() -> void:
	var s := oathSetup()
	var w: World = s[0]
	var tc: Entity = s[1]
	var pk := OathPicker.new()
	pk.open(tc)
	w.research(0, tc.id, "age:plough")
	oathFrame(pk, w, null, func(_b, _id): pass)
	check(not pk.isOpen, "closed itself")


func test_oath_picker_wont_swear_what_the_realm_cant_pay_for() -> void:
	var s := oathSetup()
	var w: World = s[0]
	w.player(0).resources = {"food": 0.0, "wood": 0.0, "gold": 0.0}
	var pk := OathPicker.new()
	pk.open(s[1])
	var buttons := oathFrame(pk, w, null, func(_b, _id): pass)
	var broke := buttons.filter(func(b): return b.label == "Not enough resources")
	eq(broke.size(), 3, "every card says why")
	var first: Dictionary = broke[0]
	var sworn: Array = []
	oathFrame(pk, w, {"x": first.x + first.w / 2, "y": first.y + first.h / 2}, func(_b, id): sworn.append(id))
	eq(sworn, [], "nothing sworn")
	check(not w.entities.any(func(e): return e.kind == T.Kind.Building and e.productionQueue.has("a:age")), "no advance started")


# ---------------------------------------------------------------------- HUD --

## A controller that records what the HUD asked for.
class FakeController extends RefCounted:
	var calls: Array = []

	func trainUnit(b, type): calls.append(["trainUnit", type])
	func research(b, techId): calls.append(["research", techId])
	func startPlacement(type): calls.append(["startPlacement", type])
	func ungarrison(b): calls.append(["ungarrison"])
	func toggleGate(b): calls.append(["toggleGate"])
	func setAutoReseed(on): calls.append(["setAutoReseed", on])
	func trade(action): calls.append(["trade", action])
	func stopSelection(): calls.append(["stop"])
	func holdSelection(): calls.append(["hold"])
	func setStance(st): calls.append(["setStance", st])
	func setAttackMoveMode(): calls.append(["attackMove"])
	func garrisonSelection(): calls.append(["garrison"])
	func useAbility(): calls.append(["ability"])
	func minimapNavigate(x, y): calls.append(["navigate", x, y])
	func minimapCommand(x, y): calls.append(["command", x, y])
	func minimapPing(x, y): calls.append(["ping", x, y])
	func openMenu(): calls.append(["openMenu"])
	func cancelProduction(b, i): calls.append(["cancel", i])
	func jumpTo(x, y): calls.append(["jumpTo", x, y])
	func narrowSelection(type, remove): calls.append(["narrow", type, remove])


func hudFrame(hud: HUD, w: World, sel: Array, ctrl, mx: float, my: float, clicked: bool, right: bool = false) -> Array:
	var rec := RecordingUI.new()
	var keep = UI._shared
	UI._shared = rec
	rec.begin(Ctx.new(), {"mx": mx, "my": my, "clicked": clicked, "rightClicked": right, "alt": false})
	var cam := Camera.new()
	cam.setViewport(W, H)
	cam.setWorld(w.worldW, w.worldH)
	hud.draw(W, H, w, cam, 0, sel, 0.016, ctrl, false)
	UI._shared = keep
	return rec.buttons


func test_hud_command_card_trains_from_the_town_centre() -> void:
	var w := world(9)
	var hud := HUD.new()
	hud.prepare(w.map)
	var ctrl := FakeController.new()
	var tc := tcOf(w, 0)
	var buttons := hudFrame(hud, w, [tc], ctrl, 0, 0, false)
	var vil = buttons.filter(func(b): return b.label == "Village").front()
	check(vil != null, "a Villager button on the Town Centre's card")
	check(buttons.any(func(b): return (b.label as String).ends_with("Age")), "and the age advance")
	hudFrame(hud, w, [tc], ctrl, vil.x + 5, vil.y + 5, true)
	eq(ctrl.calls, [["trainUnit", "villager"]], "clicking it asks for a villager")


func test_hud_build_menu_from_villagers() -> void:
	var w := world(10)
	var hud := HUD.new()
	hud.prepare(w.map)
	var ctrl := FakeController.new()
	var vils := w.entitiesOf(0, T.Kind.Unit)
	var buttons := hudFrame(hud, w, vils, ctrl, 0, 0, false)
	var build = buttons.filter(func(b): return b.label == "Build").front()
	hudFrame(hud, w, vils, ctrl, build.x + 5, build.y + 5, true)
	check(hud.buildMenuOpen, "Build opens the menu")
	# (The top bar's Menu button is drawn first; the card's come after.)
	buttons = hudFrame(hud, w, vils, ctrl, 0, 0, false).filter(func(b): return b.label != "Menu")
	eq(buttons.slice(0, 4).map(func(b): return b.label), ["Economy", "Military", "Defense", "Close"], "the categories")
	hudFrame(hud, w, vils, ctrl, buttons[0].x + 5, buttons[0].y + 5, true)
	buttons = hudFrame(hud, w, vils, ctrl, 0, 0, false).filter(func(b): return b.label != "Menu")
	eq(buttons[0].label, "House", "Economy lists the House first")
	hudFrame(hud, w, vils, ctrl, buttons[0].x + 5, buttons[0].y + 5, true)
	eq(ctrl.calls, [["startPlacement", "house"]], "and picking it starts placement")
	check(not hud.buildMenuOpen, "the menu closes")


func test_hud_minimap_navigates_commands_and_pings() -> void:
	var w := world(11)
	var hud := HUD.new()
	hud.prepare(w.map)
	var ctrl := FakeController.new()
	var mmX := 10.0
	var mmY := H - HUD.MINIMAP_SIZE - 10
	hudFrame(hud, w, [], ctrl, mmX + 98, mmY + 98, true)
	eq(ctrl.calls[0][0], "navigate", "a click on the minimap moves the camera")
	near(ctrl.calls[0][1], w.worldW / 2, 20, "to the middle of the map")
	hudFrame(hud, w, [], ctrl, mmX + 20, mmY + 20, false, true)
	eq(ctrl.calls[-1][0], "command", "a right-click is an order")


func test_hud_alerts_link_to_where_they_happened() -> void:
	var w := world(12)
	var hud := HUD.new()
	hud.prepare(w.map)
	var ctrl := FakeController.new()
	hud.addAlert("Something happened", 500.0, 600.0)
	hud.addAlert("Something happened", 500.0, 600.0)
	eq(hud.alerts.size(), 1, "the same alert refreshes rather than stacks")
	hudFrame(hud, w, [], ctrl, W / 2, 76, true)
	eq(ctrl.calls, [["jumpTo", 500.0, 600.0]], "clicking it jumps there")


func test_hud_over_hud_matches_the_panels() -> void:
	var hud := HUD.new()
	check(hud.overHud(W, H, 600, 10), "the top bar")
	check(hud.overHud(W, H, 50, H - 50), "the minimap")
	check(hud.overHud(W, H, W - 50, H - 50), "the command card")
	check(not hud.overHud(W, H, W / 2, H / 2), "the battlefield")


func test_hud_selection_chips_narrow_the_selection() -> void:
	var w := world(13)
	var hud := HUD.new()
	hud.prepare(w.map)
	var ctrl := FakeController.new()
	var sel := w.entitiesOf(0, T.Kind.Unit)
	sel.append(tcOf(w, 0))
	# Two kinds selected: the chips are clickable. Find the Town Center chip by scanning.
	var hit := false
	for x in range(240, 900, 6):
		ctrl.calls.clear()
		hudFrame(hud, w, sel, ctrl, x, H - 104 - 10 + 8 + 34, true)
		if not ctrl.calls.is_empty() and ctrl.calls[0][0] == "narrow":
			hit = true
			break
	check(hit, "a selection chip narrows the selection")


# ------------------------------------------------------------------- caster --

func test_caster_totals_realms_and_groups_sides() -> void:
	var w := world(21, 4, [0, 0, 1, 1])
	var r := Caster.snapshotRealms(w)
	eq(r.size(), 4, "four realms")
	for x in r:
		eq(x.villagers, 3, "villagers counted")
		check(x.buildings > 0, "buildings counted")
	eq(Caster.sidesOf(w), [[0, 1], [2, 3]], "two sides")


func test_caster_director_cuts_to_a_fight_and_backs_off() -> void:
	var w := world(3)
	var c := Caster.new()
	var cam := {"x": 100.0, "y": 100.0, "zoom": 1.0}
	var deaths: Array = []
	for i in 10:
		deaths.append({"kind": "death", "x": 1800.0 + i * 5, "y": 1600.0, "team": i % 2, "data": "militia"})
	c.onEvents(w, deaths, [])
	for i in 90:
		c.direct(w, cam, 1.0 / 30)
	check(JS.hypot(cam.x - 1820, cam.y - 1600) < 40, "the camera went to the fight (%s, %s)" % [cam.x, cam.y])
	check(c.feed.any(func(f): return (f.text as String).begins_with("Battle") or (f.text as String).begins_with("First blood")), "and the feed said so")
	cam.x = 300.0
	cam.y = 300.0
	c.direct(w, cam, 1.0 / 30)
	for i in 30:
		c.direct(w, cam, 1.0 / 30)
	eq(cam.x, 300.0, "the director waits once the caster takes the camera")


func test_caster_tours_the_bases_when_quiet() -> void:
	var w := world(4)
	var c := Caster.new()
	var cam := {"x": w.worldW / 2, "y": w.worldH / 2, "zoom": 1.0}
	for i in 120:
		c.direct(w, cam, 1.0 / 30)
	var near := false
	for t in 2:
		if JS.hypot(cam.x - w.map.starts[t].x, cam.y - w.map.starts[t].y) < 200:
			near = true
	check(near, "the camera visited a base")


func test_caster_feed_reports_the_story() -> void:
	var w := world(5)
	var c := Caster.new()
	c.onEvents(w, [
		{"kind": "age", "x": 0.0, "y": 0.0, "team": 0, "data": "1"},
		{"kind": "oath", "x": 0.0, "y": 0.0, "team": 0, "data": "sword"},
		{"kind": "collapse", "x": 0.0, "y": 0.0, "team": 1, "data": "town_center"},
	], ["Aldric", "Brenna"])
	w.player(1).defeated = true
	c.onEvents(w, [], ["Aldric", "Brenna"])
	var lines: Array = c.feed.map(func(f): return f.text)
	check(lines.has("Aldric reaches the Banner Age"), "age")
	check(lines.has("Aldric swears the Oath of the Sword"), "oath")
	check(lines.has("Brenna's Town Center has fallen"), "fallen TC")
	check(lines.has("Brenna has been eliminated"), "elimination")


func test_caster_keys() -> void:
	var w := world(6)
	var c := Caster.new()
	check(c.key("2", w), "a caster key")
	eq(c.vision, 1, "2 = the second realm's vision")
	c.key("2", w)
	eq(c.vision, -1, "again: back to everything")
	c.key("7", w)
	eq(c.vision, -1, "no seventh player here")
	c.key("w", w)
	eq(c.panel, "army", "W = army")
	c.key("Tab", w)
	eq(c.panel, "economy", "Tab cycles")
	var auto := c.auto
	c.key("a", w)
	eq(c.auto, not auto, "A toggles the director")
	c.key("h", w)
	check(c.clean, "H = clean feed")
	c.key("g", w)
	check(c.graphs, "G = graphs")
	check(not c.key("x", w), "not a caster key")


func test_caster_draws_every_panel_at_any_size() -> void:
	var w := world(21, 4, [0, 0, 1, 1])
	for i in 400:
		w.tick()
	var history: Array = []
	history.append({"t": 0.0, "m": Metrics.snapshotMetrics(w)})
	history.append({"t": w.time, "m": Metrics.snapshotMetrics(w)})
	var c := Caster.new()
	var names := ["Aldric", "Brenna", "Cato", "Dagny"]
	for p in Caster.CASTER_PANELS:
		c.panel = p[0]
		for sz in [[1600, 900], [1280, 720], [1024, 600]]:
			begin(-1, -1)
			c.draw(sz[0], sz[1], {"world": w, "names": names, "history": history, "speed": 1.0, "paused": false, "source": "live", "delaySec": 0.0})
	c.graphs = true
	c.help = true
	begin(-1, -1)
	c.draw(1600, 900, {"world": w, "names": names, "history": history, "speed": 4.0, "paused": true, "source": "replay", "delaySec": 0.0, "replay": {"tick": 2000, "endTick": 4000}})
	c.clean = true
	begin(-1, -1)
	c.draw(1600, 900, {"world": w, "names": names, "history": history, "speed": 1.0, "paused": false, "source": "delay", "delaySec": 60.0})
	check(true, "drew")


func test_caster_timeline_click_asks_to_jump() -> void:
	var w := world(2)
	for i in 100:
		w.tick()
	var c := Caster.new()
	c.auto = false
	var ctx := {"world": w, "names": [], "history": [], "speed": 1.0, "paused": false, "source": "replay", "delaySec": 0.0, "replay": {"tick": 0, "endTick": 20 * 600}}
	var cw := 1600.0
	var ch := 900.0
	var y := ch - 176 - 40
	var x0 := 196.0 + 30
	var bx := x0 + 36 + 6 * 38 + 10
	var bw := cw - 20 - bx - 70
	begin(bx + bw / 2, y + 12, true)
	var req := c.draw(cw, ch, ctx)
	near(float(req.get("seekTo", -1)), 20 * 300, 100, "jump to the middle")


# --------------------------------------------------------------- fullscreen --

## A stand-in window (the TS tests' fake document).
class FakeWindow extends RefCounted:
	var allowed := true
	var on := false
	var calls: Array = []

	func enabled() -> bool:
		return allowed

	func active() -> bool:
		return on

	func request(want: bool) -> void:
		calls.append("request" if want else "exit")
		on = want


func test_fullscreen_enters_and_leaves() -> void:
	var f := FakeWindow.new()
	Fullscreen.backend = f
	check(not Fullscreen.isFullscreen(), "starts windowed")
	Fullscreen.toggleFullscreen()
	eq(f.calls, ["request"], "entered")
	check(Fullscreen.isFullscreen(), "is fullscreen")
	Fullscreen.toggleFullscreen()
	eq(f.calls, ["request", "exit"], "left")
	Fullscreen.backend = null


func test_fullscreen_does_nothing_where_not_allowed() -> void:
	var f := FakeWindow.new()
	f.allowed = false
	Fullscreen.backend = f
	check(not Fullscreen.fullscreenSupported(), "not supported")
	Fullscreen.toggleFullscreen()
	eq(f.calls, [], "no request")
	Fullscreen.backend = null


func test_fullscreen_is_harmless_headless() -> void:
	Fullscreen.backend = null
	check(not Fullscreen.fullscreenSupported(), "headless: unsupported")
	check(not Fullscreen.isFullscreen(), "headless: not fullscreen")
	Fullscreen.toggleFullscreen()


func test_fullscreen_button_registers_its_zone_only_when_possible() -> void:
	Fullscreen.backend = FakeWindow.new()
	begin(-99, -99)
	FullscreenButton.fullscreenButton(600, 20, 160, 36)
	eq(UI.ui.gestureAt(680, 38), FullscreenButton.FULLSCREEN_ZONE, "registered")
	var no := FakeWindow.new()
	no.allowed = false
	Fullscreen.backend = no
	begin(-99, -99)
	FullscreenButton.fullscreenButton(600, 20, 160, 36)
	eq(UI.ui.gestureAt(680, 38), null, "nothing registered when impossible")
	Fullscreen.backend = null


func test_fullscreen_hotkey_defaults_to_alt_enter() -> void:
	eq(Keybinds.chordFor({}, "fullscreen"), "Alt+Enter", "Alt+Enter")
	eq(Keybinds.chordFor({}, "chat"), "Enter", "chat stays on Enter")
	var clash = Keybinds.conflictsOf({}).get("Alt+Enter")
	check(clash == null or clash.size() <= 1, "no clash")


# -------------------------------------------------------------------- files --

func test_files_download_writes_the_file() -> void:
	var dir := ProjectSettings.globalize_path("user://test_downloads")
	Files.downloadDirOverride = dir
	check(Files.downloadText("replay.bbreplay", "{\"a\":1}"), "downloaded")
	var f := FileAccess.open(dir.path_join("replay.bbreplay"), FileAccess.READ)
	check(f != null, "the file exists")
	if f != null:
		eq(f.get_as_text(), "{\"a\":1}", "with the text")
	Files.downloadDirOverride = ""
