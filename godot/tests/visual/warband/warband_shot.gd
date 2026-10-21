## One shot of the Warband Tactics screen, drawn by the port. Replays a shot
## of tests/visual/warband/out/shots.json — written by
## game/scripts/godot-visual-warband.mjs alongside the same shot drawn by the
## TypeScript — through WarbandScreen: the same seed, the same ops played on
## the run, and the same frames (pointer and clock) fed to `draw`:
##
##   xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
##     --path godot res://tests/visual/warband/warband_shot.tscn -- shop tests/visual/warband/out/godot_shop.png
##
## Add `--cache` before the shot name to draw units through the art cache (as
## the game does) instead of procedurally.
extends "res://tests/visual/sheet_base.gd"

var spec: Dictionary = {}
var use_cache := false


func setup(args: PackedStringArray) -> void:
	var name := "shop"
	for a in args:
		if a == "--cache":
			use_cache = true
		elif not a.ends_with(".png"):
			name = a
	var path := ProjectSettings.globalize_path("res://tests/visual/warband/out/shots.json")
	var all = JSON.parse_string(FileAccess.get_file_as_string(path))
	for s in all:
		if s.name == name:
			spec = s
	if spec.is_empty():
		push_error("no shot " + name)
		get_tree().quit(1)
		return
	sheet_size = Vector2i(int(spec.W), int(spec.H))
	ArtCache.enabled = use_cache
	ArtCache.unlimited = true


static func apply(run: Warband.WarbandRun, op: Dictionary) -> void:
	match op.op:
		"buy": run.buy(int(op.i))
		"sell": run.sell(int(op.i))
		"pickAugment": run.pickAugment(int(op.i))
		"takeCarousel": run.takeCarousel(int(op.i))
		"pickCommander": run.pickCommander(int(op.i))
		"reroll": run.reroll()
		"buyXp": run.buyXp()
		"lock": run.toggleShopLock()
		"next": run.next()
		"place": run.place(int(op.i), int(op.col), int(op.row))
		"equip": run.equipItem(int(op.s), int(op.i))
		"pickDifficulty": run.pickDifficulty(op.id)
		"setDifficulty": run.difficulty = WarbandDifficulty.difficultyById(op.id)
		"fight": run.fight()
		"gold": run.gold = int(op.n)
		"level": run.level = int(op.n)
		"shop": run.shop = op.types.duplicate()
		"stash": run.itemStash = op.items.duplicate()
		_: push_error("unknown op " + str(op.op))


func paint(c: Ctx) -> void:
	var W := float(spec.W)
	var H := float(spec.H)
	var run := Warband.WarbandRun.new(int(spec.seed), spec.commander)
	for op in spec.ops:
		apply(run, op)
	var screen := WarbandScreen.new()
	if spec.battle:
		# Into the arena the way the FIGHT button does it.
		run.beginFight()
		screen.battle = Autobattle.LiveBattle.new(run.boardUnits(), run.pendingOpp, run.pendingSeed, 30.0, run.sideOpts(), run.fieldOpts())
		screen.battle.begin()
	var t := 0.0
	var ui := UI.ui
	for f in spec.frames:
		t += 1.0 / 30.0
		c.begin(_canvas, sheet_size.x, sheet_size.y) # each frame replaces the last
		c.reset()
		ui.begin(c, {"mx": f.mx, "my": f.my, "clicked": f.clicked, "rightClicked": false, "alt": false, "leftHeld": f.down})
		c.save()
		screen.draw(W, H, t, run)
		c.restore()
	if run.phase != spec.phase or run.round != int(spec.round):
		push_error("shot %s: reached %s round %d, the TS reached %s round %d" % [spec.name, run.phase, run.round, spec.phase, int(spec.round)])
