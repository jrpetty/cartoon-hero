## Where the time in a menu frame goes: the UI widgets, the backdrop, a map
## thumbnail, and whole screens, drawn through Ctx into a canvas item.
##   godot --headless --path godot res://tests/perf/screens_prof.tscn [-- --nocache]
extends Node

var ctx := Ctx.new()
var rid: RID


func _bench(label: String, n: int, fn: Callable) -> void:
	fn.call()
	var t0 := Time.get_ticks_usec()
	for i in n:
		fn.call()
	print("%-44s %8.3f ms" % [label, (Time.get_ticks_usec() - t0) / 1000.0 / n])


func _ready() -> void:
	ArtCache.enabled = not OS.get_cmdline_user_args().has("--nocache")
	ArtCache.unlimited = true
	Storage.use_memory_only()
	audio.muted = true
	rid = RenderingServer.canvas_item_create()
	var ui := UI.ui
	var input := {"mx": -99, "my": -99, "clicked": false, "rightClicked": false}
	var frame := func(): ctx.begin(rid, 1600, 900); ui.begin(ctx, input)
	_bench("100x ui.button", 10, func():
		frame.call()
		for i in 100:
			ui.button("Button %d" % i, 10 + (i % 10) * 150, 10 + (i / 10) * 40, 140, 30, {"size": 13}))
	_bench("100x ui.text", 10, func():
		frame.call()
		for i in 100:
			ui.text("Some text of a usual length %d" % i, 10, 10 + i * 8, {"size": 13}))
	_bench("1000x measureText (same 3 strings)", 5, func():
		ctx.font = "13px 'Trebuchet MS', sans-serif"
		for i in 1000:
			ctx.measureText(["Villager, Man-at-Arms,", "Champion, Scout,", "King"][i % 3]))
	_bench("1000x ctx.font = (two fonts)", 5, func():
		for i in 1000:
			ctx.font = "13px 'Trebuchet MS', sans-serif" if i % 2 else "bold 14px \"Trebuchet MS\", sans-serif")
	_bench("100x fillText", 10, func():
		frame.call()
		ctx.font = "13px 'Trebuchet MS', sans-serif"
		for i in 100:
			ctx.fillText("Some text of a usual length", 10, 10 + i * 8))
	_bench("100x roundRect fill (solid)", 10, func():
		frame.call()
		for i in 100:
			ctx.fillStyle = "#3c3427"; ctx.beginPath(); ctx.roundRect(10, 10 + i * 8, 140, 30, 7); ctx.fill())
	_bench("100x roundRect fill (gradient)", 10, func():
		frame.call()
		for i in 100:
			var g := ctx.createLinearGradient(0, 10, 0, 40)
			g.addColorStop(0, "#4a4030"); g.addColorStop(1, "#2a2418")
			ctx.fillStyle = g; ctx.beginPath(); ctx.roundRect(10, 10 + i * 8, 140, 30, 7); ctx.fill())
	_bench("100x roundRect stroke", 10, func():
		frame.call()
		for i in 100:
			ctx.strokeStyle = "#e2b24e"; ctx.lineWidth = 1.1; ctx.beginPath(); ctx.roundRect(10.8, 10.8 + i * 8, 138.4, 28.4, 6.5); ctx.stroke())
	_bench("1000x fillRect", 5, func():
		frame.call()
		for i in 1000:
			ctx.fillStyle = "#6f9c4a"; ctx.fillRect(i % 50, i / 50, 1, 1))
	_bench("20x ui.panel", 10, func():
		frame.call()
		for i in 20:
			ui.panel(10 + i * 10, 10 + i * 10, 400, 300, {"light": i % 2 == 0}))
	_bench("drawMenuBackground 1600x900", 10, func():
		frame.call()
		Screens.drawMenuBackground(1600, 900, 1.0))
	var m := CustomMaps.customFromGenerated("highlands", 4242, 4, "Thumb")
	_bench("drawMapThumbnail 146 cells at 54px", 10, func():
		frame.call()
		MapThumb.drawMapThumbnail(ctx, 10, 10, 54, m, {"spawns": true, "resources": true}))
	var p := Profile.new()
	p.data.commanderReveal = ""
	p.chooseFirstFaction("legion")
	var screens := {
		"menu": [Screens.MenuScreen.new(), func(s): s.draw(1600, 900, 1, p)],
		"setup 1v1": [Screens.SetupScreen.new(), func(s): s.draw(1600, 900, 1, p)],
		"armory chests": [Screens.ArmoryScreen.new(), func(s): s.draw(1600, 900, 1, 0.016, p)],
		"codex tech": [_codex("tech"), func(s): s.draw(1600, 900, 1, p)],
		"codex units": [_codex("units"), func(s): s.draw(1600, 900, 1, p)],
		"factions book": [FactionBook.new(), func(s): s.draw(1600, 900, 1, p)],
		"career (empty)": [CareerScreen.new(), func(s): s.draw(1600, 900, 1, p)],
		"settings": [SettingsScreen.new(), func(s): s.draw(1600, 900, 1, C.DEFAULT_SETTINGS.duplicate(true), false)],
		"nemesis": [RivalScreen.new(), func(s): s.draw(1600, 900, 1, "legion")],
	}
	var ct := _codex("tech")
	_bench("codex tech, measure only", 5, func():
		ctx.measure_only = true
		frame.call()
		ct.draw(1600, 900, 1, p)
		ctx.measure_only = false)
	_bench("codex drawTech alone", 5, func():
		frame.call()
		ct.drawTech(1600, 900, 124))
	var para := "Every age you advance to, your realm swears one of three Oaths. It is how a realm gets its identity — chosen during the match, with the map in view and the enemy scouted, instead of a civilisation picked before it. Everyone is told what you swear."
	_bench("CodexScreen.wrap_ one paragraph", 20, func():
		frame.call()
		CodexScreen.wrap_(para, 10, 10, 900, 12.5, "#d8cdb4"))
	_bench("50x ui.panel light", 5, func():
		frame.call()
		for i in 50:
			ui.panel(10, 10 + i * 8, 300, 150, {"light": true}))
	_bench("100x measureText distinct", 5, func():
		ctx.font = "12.5px \"Trebuchet MS\", sans-serif"
		for i in 100:
			ctx.measureText("word%d and more %d" % [i, i * 7]))
	_bench("Oaths.lines x9", 20, func():
		for id in C.OATHS:
			Oaths.lines(id, 1))
	var U := preload("res://tests/ui_test_helpers.gd")
	for i in 400:
		Career.recordCareer(U.career_match({"won": i % 3 != 0, "faction": ["legion", "norse", "kingdom"][i % 3], "durationSec": 600.0 + i}))
	_bench("Career.careerFor('all') with 400 matches", 3, func(): Career.careerFor("all"))
	_bench("Playstyle.analyse(400 matches)", 3, func(): Playstyle.analyse(Career.careerLog()))
	var cs := CareerScreen.new()
	_bench("career overview, 400 matches (cached)", 5, func():
		frame.call()
		cs.draw(1600, 900, 1, p))
	var cs2 := CareerScreen.new()
	cs2.tab = "style"
	_bench("career playstyle, 400 matches (cached)", 5, func():
		frame.call()
		cs2.draw(1600, 900, 1, p))
	for name in screens:
		var scr = screens[name][0]
		var draw: Callable = screens[name][1]
		_bench("screen: " + name, 5, func():
			frame.call()
			draw.call(scr))
	get_tree().quit()


func _codex(tab: String) -> CodexScreen:
	var c := CodexScreen.new()
	c.tab = tab
	return c
