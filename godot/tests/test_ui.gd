extends TestCase
# ui/ui.ts: "Interface scale" from ui/uiscale.test.ts (the settings-screen
# cases wait for that screen's port), plus scrolling, gesture zones, button
# clicks and the tooltip.

const SoftCanvas := preload("res://tests/soft_canvas.gd")


static func frame(ctx: Ctx, mx: float, my: float, clicked: bool = true) -> void:
	UI.ui.begin(ctx, {"mx": mx, "my": my, "clicked": clicked, "rightClicked": false, "alt": false})


func test_hit_tests_a_scaled_widget_where_it_is_drawn() -> void:
	# A button laid out at (100,100) in a 1.5× block appears at (150,150) on
	# screen. Both must agree, or every click lands in the wrong place — which
	# is the one bug an interface scale can introduce.
	var ui := UI.ui
	frame(Ctx.new(), 150, 150)
	ui.pushScale(1.5)
	check(ui.hit(100, 100, 40, 40), "hit where drawn")
	check(not ui.hit(160, 160, 40, 40), "no hit where it would be unscaled")
	ui.popScale()
	# Outside the block, the pointer is back in canvas pixels.
	eq(ui.mx, 150, "mx restored")
	eq(ui.my, 150, "my restored")


func test_restores_the_pointer_exactly() -> void:
	var ui := UI.ui
	frame(Ctx.new(), 640, 400)
	for s in [0.8, 1.0, 1.25, 1.5]:
		ui.pushScale(s)
		ui.popScale()
	near(ui.mx, 640, 1e-6, "mx")
	near(ui.my, 400, 1e-6, "my")


func test_is_reset_by_begin() -> void:
	var ui := UI.ui
	var ctx := Ctx.new()
	frame(ctx, 100, 100)
	ui.pushScale(1.5)
	frame(ctx, 100, 100)  # a new frame, mid-scale
	eq(ui.scale, 1.0, "scale reset")
	eq(ui.mx, 100, "pointer reset")


func test_scroll_moves_the_pointer_with_the_content() -> void:
	var ui := UI.ui
	var ctx := Ctx.new()
	frame(ctx, 50, 60)
	ui.pushScroll(100, {"x": 0, "y": 0, "w": 200, "h": 200})
	check(ui.hit(40, 150, 20, 20), "a widget scrolled up by 100 is hit at its layout position")
	eq(ui.my, 160, "pointer in layout space")
	ui.popScroll()
	eq(ui.my, 60, "pointer back")
	check(not ctx._clip_on, "clip popped")


func test_gesture_zones_follow_the_transform() -> void:
	var ui := UI.ui
	var ctx := Ctx.new()
	frame(ctx, 0, 0)
	ui.pushScale(2)
	ui.registerGestureZone("fullscreen", 10, 10, 20, 10)
	ui.popScale()
	ui.registerGestureZone("other", 100, 100, 10, 10)
	eq(ui.gestureAt(30, 30), "fullscreen", "zone in canvas pixels (scaled ×2)")
	eq(ui.gestureAt(15, 15), null, "outside the scaled zone")
	eq(ui.gestureAt(105, 105), "other", "unscaled zone")


func test_buttons_click_hover_and_disable() -> void:
	var ui := UI.ui
	var c = SoftCanvas.new(200, 100)
	frame(c, 50, 30, true)
	check(ui.button("Go", 20, 20, 80, 30), "clicked")
	check(ui.pointerConsumed, "the button took the pointer")
	frame(c, 50, 30, true)
	check(not ui.button("Go", 20, 20, 80, 30, {"disabled": true}), "disabled buttons don't click")
	frame(c, 150, 90, true)
	check(not ui.button("Go", 20, 20, 80, 30), "a click elsewhere doesn't count")
	frame(c, 50, 30, false)
	ui.button("Buy", 20, 20, 80, 30, {"tooltip": ["Knight", "Cost: 60 food"]})
	check(ui.hoveredTooltip != null and ui.hoveredTooltip.text[0] == "Knight", "hover queues the tooltip")
	ui.flushTooltip(200, 100)  # draws without error


func test_panel_blocks_the_pointer_and_bars_draw() -> void:
	var ui := UI.ui
	var c = SoftCanvas.new(120, 80)
	frame(c, 30, 30, false)
	ui.panel(10, 10, 100, 60)
	check(ui.pointerConsumed, "a panel under the pointer consumes it")
	ui.bar(20, 40, 80, 10, 0.5, "#5bd66b")
	var p: Color = c.img.get_pixel(30, 45)
	check(p.g8 > p.r8 + 60, "the filled half of the bar is green")
	var q: Color = c.img.get_pixel(95, 45)
	check(q.g8 < 120, "the empty half is dark")
	eq(ui.slider(20, 60, 80, 0.25, false), 0.25, "slider holds its value when not dragged")
	frame(c, 60, 60, false)
	near(ui.slider(20, 60, 80, 0.25, true), 0.5, 1e-9, "slider follows the pointer while dragged")
