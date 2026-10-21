extends TestCase
# Ported from game/src/ui/warband_screen.test.ts: driving the Warband Tactics
# screen through frames of pointer input — drag-to-sell, click-to-place, the
# augment and difficulty pickers, scouting, the ground-seed field — and
# rendering every phase without a script error.

const W := 1280.0
const H := 760.0

var ctx := Ctx.new()
var t := 0.0



# Take the arena rect from the screen itself, so a layout change can't quietly
# leave these clicking empty space.
static func board() -> Dictionary:
	return WarbandScreen.boardRect(W, H)


static func cellCenter(c: int, r: int) -> Vector2:
	var b := board()
	var cellW: float = b.w / 10.0
	var cellH: float = b.h / 10.0
	return Vector2(b.x + (c + 0.5) * cellW, b.y + (r + 0.5) * cellH)


## One frame: the pointer, whether it is held, whether it was released (a
## click), then a draw. (The TS sets screens.ts's mouse-down flag; the port
## reads the frame's leftHeld, which main.ts feeds from the same input.)
func frame(s: WarbandScreen, run: Warband.WarbandRun, mx: float, my: float, down: bool = false, clicked: bool = false, w: float = W, h: float = H) -> void:
	t += 1.0 / 30.0
	UI.ui.begin(ctx, {"mx": mx, "my": my, "clicked": clicked, "rightClicked": false, "alt": false, "leftHeld": down})
	s.draw(w, h, t, run)


func placementRun() -> Warband.WarbandRun:
	var run := Warband.WarbandRun.new(11, null)
	run.gold = 400
	run.level = 6
	for ty in ["knight", "archer", "spearman", "militia", "horseman", "crossbow"]:
		run.shop = [ty, ty, ty, ty, ty]
		run.buy(0)
		run.buy(1)
	return run


func toAugment(run: Warband.WarbandRun) -> void:
	var guard := 0
	while run.phase != "augment" and guard < 50:
		guard += 1
		if run.phase == "draft":
			run.takeCarousel(0)
			continue
		if run.phase == "shop":
			run.fight()
		if run.phase == "result":
			run.next()


# ---- placement -------------------------------------------------------------------

func test_drags_a_board_unit_onto_the_sell_box_to_sell_it() -> void:
	var run := placementRun()
	var s := WarbandScreen.new()
	run.place(run.deployment()[0].index, 2, 3)
	frame(s, run, 0, 0) # settle (resolves auto-placement)
	var before := run.pieces.size()
	var u := cellCenter(2, 3)
	var b := board()
	var sx: float = b.x + b.w - 156 / 2.0 - 14
	var sy: float = b.y + b.h - 66 / 2.0 - 14
	frame(s, run, u.x, u.y, true, false) # press → grab
	frame(s, run, sx, sy, true, false) # drag over the sell box
	frame(s, run, sx, sy, false, true) # release → sell
	eq(run.pieces.size(), before - 1)


func test_click_to_place_moves_a_unit_to_an_empty_cell() -> void:
	var run := placementRun()
	var s := WarbandScreen.new()
	run.place(run.deployment()[0].index, 2, 3)
	frame(s, run, 0, 0)
	var idx := -1
	for d in run.deployment():
		if d.col == 2 and d.row == 3:
			idx = d.index
	check(idx >= 0)
	var u := cellCenter(2, 3)
	var e := cellCenter(0, 9) # an empty cell
	frame(s, run, u.x, u.y, true, false) # press on the unit
	frame(s, run, u.x, u.y, false, true) # release on it → picked up (armed)
	frame(s, run, e.x, e.y, false, true) # click an empty cell → place
	var moved: Warband.Piece = run.pieces[idx]
	eq(moved.col, 0)
	eq(moved.row, 9)


# ---- augments & scouting -------------------------------------------------------------

## Card geometry mirrors drawAugmentPicker's layout maths.
static func cardCenterX(i: int) -> float:
	var gap := 24.0
	var cardW := minf(258, (W - 100 - gap * 2) / 3)
	var x0 := (W - (cardW * 3 + gap * 2)) / 2
	return x0 + i * (cardW + gap) + cardW / 2


func test_clicking_an_augment_card_takes_it_and_resumes_the_shop() -> void:
	var run := Warband.WarbandRun.new(11, null)
	var s := WarbandScreen.new()
	toAugment(run)
	eq(run.phase, "augment")
	var wanted: String = run.augmentOffer[1].id
	for i in 30:
		frame(s, run, 0, 0) # let the cards deal in
	frame(s, run, cardCenterX(1), H / 2, false, true)
	eq(run.phase, "shop")
	eq(run.augments.map(func(a): return a.id), [wanted])


func test_blocks_the_board_and_quit_while_the_augment_pick_is_open() -> void:
	var run := Warband.WarbandRun.new(11, null)
	var s := WarbandScreen.new()
	toAugment(run)
	for i in 30:
		frame(s, run, 0, 0)
	var cells := "|".join(run.deployment().map(func(d): return "%d,%d" % [d.col, d.row]))
	var p := cellCenter(1, 1)
	frame(s, run, p.x, p.y, false, true)
	eq("|".join(run.deployment().map(func(d): return "%d,%d" % [d.col, d.row])), cells)
	eq(run.phase, "augment")
	# Nor does Quit Run answer while the modal is up.
	t += 1.0 / 30.0
	UI.ui.begin(ctx, {"mx": W - 70, "my": 28, "clicked": true, "rightClicked": false, "alt": false})
	eq(s.draw(W, H, t, run), null)


func test_clicking_a_rival_opens_and_closes_the_scout_panel() -> void:
	var run := Warband.WarbandRun.new(7, null)
	var s := WarbandScreen.new()
	run.gold = 60
	frame(s, run, 0, 0)
	eq(s.scoutId, -1)
	# Row 0 is "You"; row 1 is the first rival (rows are 26 high, 3 apart, from y=98).
	var rowY := 98 + 1 * 29 + 13
	var rival: Dictionary = run.standings()[1]
	frame(s, run, 100, rowY, false, true)
	eq(s.scoutId, rival.id)
	frame(s, run, 100, 300) # the panel renders
	frame(s, run, 100, rowY, false, true) # clicking the same row again closes it
	eq(s.scoutId, -1)


func test_renders_a_camp_round_and_the_augment_picker() -> void:
	var run := Warband.WarbandRun.new(3, null)
	var s := WarbandScreen.new()
	check(run.isCreepRound(), "round 1 is always a camp")
	frame(s, run, 0, 0)
	run.fight()
	frame(s, run, 0, 0) # the camp result (loot line)
	var guard := 0
	while run.phase != "augment" and guard < 50:
		guard += 1
		if run.phase == "draft":
			run.takeCarousel(0)
			continue
		if run.phase == "result":
			run.next()
		if run.phase == "shop":
			run.fight()
	eq(run.phase, "augment")
	for i in 5:
		frame(s, run, 640, 400)


# ---- every phase renders ---------------------------------------------------------------

func test_draws_shop_result_and_game_over() -> void:
	var s := WarbandScreen.new()
	var run := Warband.WarbandRun.new(5, null)
	run.gold = 80
	run.shop = ["militia", "archer", "knight", "catapult", "hero"]
	run.buy(0)
	run.buy(1)
	run.buy(2)
	run.level = 3
	frame(s, run, 0, 0) # shop phase
	run.fight()
	frame(s, run, 0, 0) # result (or over)
	run.phase = "over"
	run.outcome = "loss"
	frame(s, run, 0, 0)
	run.outcome = "win"
	frame(s, run, 0, 0)


func test_draws_the_summary_of_a_played_run() -> void:
	var s := WarbandScreen.new()
	var run := Warband.WarbandRun.new(31, null)
	var i := 0
	while i < 40 and run.phase != "over":
		i += 1
		if run.phase == "augment":
			run.pickAugment(0)
		if run.phase == "draft":
			run.takeCarousel(0)
		if run.phase == "shop":
			for k in 5:
				run.buy(k)
			run.fight()
		if run.phase == "result":
			run.next()
	eq(run.phase, "over")
	check(run.history.size() > 0)
	# Several sizes: the card is height-derived, so a short screen must still fit.
	for size in [[1280.0, 760.0], [1600.0, 900.0], [1100.0, 680.0]]:
		frame(s, run, 0, 0, false, false, size[0], size[1])


func test_a_watched_fight_plays_out_and_banks_its_result() -> void:
	# The FIGHT button starts a LiveBattle; the screen steps it in real time,
	# hands the verdict to the run, and the run moves on to its result.
	var s := WarbandScreen.new()
	var run := Warband.WarbandRun.new(5, null)
	run.gold = 80
	run.shop = ["militia", "archer", "knight", "catapult", "hero"]
	run.buy(0)
	run.buy(1)
	run.level = 3
	frame(s, run, 0, 0)
	check(run.beginFight())
	s.battle = Autobattle.LiveBattle.new(run.boardUnits(), run.pendingOpp, run.pendingSeed, 30.0, run.sideOpts(), run.fieldOpts())
	s.battle.begin()
	var guard := 0
	while run.phase == "battle" and guard < 2000:
		guard += 1
		frame(s, run, 0, 0)
	check(run.phase == "result" or run.phase == "over", "phase %s" % run.phase)
	check(run.lastResult != null)


# ---- difficulty & the ground seed --------------------------------------------------------

func test_a_difficulty_card_click_enters_that_lobby() -> void:
	var s := WarbandScreen.new()
	var run := Warband.WarbandRun.new(17) # the real flow: difficulty, then commander
	eq(run.phase, "difficulty")
	for i in 40:
		frame(s, run, 0, 0) # let all four cards deal in
	var gap := 18.0
	var cardW := minf(246, (W - 120 - gap * 3) / 4)
	var x0 := (W - (cardW * 4 + gap * 3)) / 2
	var cardCx := x0 + 3 * (cardW + gap) + cardW / 2 # the fourth card
	frame(s, run, cardCx, H / 2, false, true)
	eq(run.difficulty.id, "conqueror")
	eq(run.phase, "commander")


func test_types_a_ground_seed_and_replays_that_board() -> void:
	var s := WarbandScreen.new()
	var run := Warband.WarbandRun.new(19, null)
	while run.round < 6:
		if run.phase == "augment":
			run.pickAugment(0)
		if run.phase == "draft":
			run.takeCarousel(0)
		if run.phase == "shop":
			run.fight()
		if run.phase == "result":
			run.next()
		else:
			break
	check(run.terrain.size() > 0)
	var wanted := run.terrainCode()
	frame(s, run, -99, -99) # lay the rail out so the field's rect is known
	check(not s.handleKey("Backspace"), "keys ignored until the field is armed")
	# Find the field by clicking down the rail — it is the only thing there
	# that arms on click, so a miss simply does nothing. Backspace is the probe
	# because it is consumed when armed without typing anything.
	var armed := false
	var y := 380
	while y < 720 and not armed:
		frame(s, run, 60, y, false, true)
		armed = s.handleKey("Backspace")
		y += 4
	check(armed)
	for k in wanted:
		s.handleKey(k)
	s.handleKey("Enter")
	frame(s, run, -99, -99)
	eq(run.terrainCode(), wanted)


func test_board_rect_stays_inside_its_margins() -> void:
	for size in [[1280.0, 760.0], [1600.0, 900.0], [1920.0, 1080.0], [2560.0, 1080.0]]:
		var r := WarbandScreen.boardRect(size[0], size[1])
		check(r.x >= WarbandScreen.RAIL_W + WarbandScreen.BOARD_RIM, "left margin at %s" % [size])
		check(r.x + r.w <= size[0] - 16 - WarbandScreen.BOARD_RIM + 0.5, "right margin at %s" % [size])
		check(r.y + r.h <= size[1] - WarbandScreen.SHOP_H - WarbandScreen.BENCH_H, "above the bench at %s" % [size])
		# Cells stay close to square.
		check(r.w / 10.0 <= r.h / 10.0 * 1.18 + 1e-6)
