extends TestCase
# engine/camera.ts and engine/particles.ts have no TS tests; these pin down the
# behaviour the renderer and main loop rely on.


func test_camera_round_trips_world_and_screen() -> void:
	var c := Camera.new()
	c.setWorld(3200, 3200)
	c.setViewport(1600, 900)
	c.centerOn(1000, 700)
	c.zoom = 1.25
	var sx := c.worldToScreenX(1234.5)
	var sy := c.worldToScreenY(456.25)
	near(c.screenToWorldX(sx), 1234.5, 1e-9, "x")
	near(c.screenToWorldY(sy), 456.25, 1e-9, "y")
	eq(c.worldToScreenX(1000), 800, "the centre is mid-screen")


func test_camera_zooms_about_the_cursor_and_clamps() -> void:
	var c := Camera.new()
	c.setWorld(3200, 3200)
	c.setViewport(1280, 720)
	c.centerOn(1600, 1600)
	var wx := c.screenToWorldX(300)
	var wy := c.screenToWorldY(200)
	c.zoomAt(300, 200, 1.14)
	near(c.screenToWorldX(300), wx, 1e-9, "the point under the cursor stays put (x)")
	near(c.screenToWorldY(200), wy, 1e-9, "(y)")
	for i in 40:
		c.zoomAt(640, 360, 1.14)
	eq(c.zoom, c.maxZoom, "zoom clamps at maxZoom")
	for i in 80:
		c.zoomAt(640, 360, 0.88)
	eq(c.zoom, c.minZoom, "zoom clamps at minZoom")
	c.pan(-99999, 99999)
	eq(c.x, -200, "overscroll margin, left")
	eq(c.y, 3400, "overscroll margin, bottom")


func test_particles_spawn_move_and_expire() -> void:
	var p := Particles.new(4)
	p.spawn({"x": 10.0, "y": 20.0, "vx": 60.0, "maxLife": 0.5, "color": "#f00"})
	var a: Particles.Particle = p.pool[0]
	eq(a.active, true, "active")
	eq(a.color, "#f00", "color")
	eq(a.drag, 0.9, "default drag")
	p.update(0.1)
	check(a.x > 10.0, "moved")
	near(a.vx, 60.0 * pow(0.9, 6.0), 1e-9, "drag per 60th of a second")
	p.update(0.5)
	eq(a.active, false, "expired")


func test_particles_reuse_the_oldest_when_the_pool_is_full() -> void:
	var p := Particles.new(3)
	for i in 3:
		p.spawn({"x": float(i), "y": 0.0})
	p.spawn({"x": 99.0, "y": 0.0}) # pool exhausted: the cursor's slot is reused
	eq(p.pool.filter(func(q): return q.active).size(), 3, "still three live")
	eq(p.pool[0].x, 99.0, "slot 0 reused")
	p.burst(0, 0, 10, "#fff", 100.0, {"gravity": 5.0})
	check(p.pool.all(func(q): return q.gravity == 5.0), "burst opts override")
	p.clear()
	check(p.pool.all(func(q): return not q.active), "cleared")


func test_particles_density_thins_spawns() -> void:
	var p := Particles.new(500)
	p.density = 0.0
	for i in 100:
		p.spawn({"x": 0.0, "y": 0.0})
	eq(p.pool.filter(func(q): return q.active).size(), 0, "density 0 spawns nothing")
