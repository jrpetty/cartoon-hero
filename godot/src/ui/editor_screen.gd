## ui/editor_screen.ts — the map editor.
##
## The shape of it: a library you arrive at, a canvas you paint on, a properties
## rail that decides what the map *is*, and a validation list that tells you the
## truth about it while you work rather than when you try to play it.
##
## Three things drive most of the design:
##
##   • **Painting has to be cheap.** The map is redrawn every frame at whatever
##     zoom you are at, so the canvas draws cells, not sprites, and only the
##     cells actually on screen.
##   • **Symmetry is the whole job on a competitive map.** Painting one corner
##     of a four-player map by hand and hoping is not authoring. Every stroke is
##     mirrored through the chosen symmetry, so the map is fair by construction.
##   • **You should never be able to save something unplayable by accident.**
##     Validation runs on every change and says what is wrong in words, and
##     Test Map is disabled while any of it is an error.
##
## EditorAction (TS) is null, {kind: "back"} or {kind: "test", map}.
## `symmetryCells` and SYMMETRIES live in Symmetry / C.SYMMETRIES (the TS
## re-exports them from here).
class_name EditorScreen
extends RefCounted

const Terrain = TerrainKinds.Terrain

# ------------------------------------------------------------------- tools --

## Tool: "terrain" | "path" | "scatter" | "region" | "resource" | "spawn" | "erase"
## ResourceKind: "tree" | "gold_mine" | "berries"

const PALETTE := [
	Terrain.Grass, Terrain.GrassDark, Terrain.Dirt, Terrain.Sand, Terrain.Snow,
	Terrain.Hill, Terrain.Forest, Terrain.Marsh, Terrain.Shallow, Terrain.Water, Terrain.Rock,
]

const SWATCH := {
	Terrain.Grass: "#6f9c4a", Terrain.GrassDark: "#557f3c", Terrain.Dirt: "#8a6f47",
	Terrain.Sand: "#cbb383", Terrain.Snow: "#dfe7f0", Terrain.Hill: "#7f9c5b",
	Terrain.Forest: "#2f4429", Terrain.Marsh: "#4d5a3c", Terrain.Shallow: "#4a7f96",
	Terrain.Water: "#2f6a86", Terrain.Rock: "#6a6f76",
}

const RES_COLOUR := {"tree": "#2f6b32", "gold_mine": "#e0b83a", "berries": "#c8425a"}
const RES_LABEL := {"tree": "Trees", "gold_mine": "Gold", "berries": "Berries"}
const RES_AMOUNT := {"tree": 160.0, "gold_mine": 1000.0, "berries": 250.0}

const MODES := [
	{"id": "conquest", "label": "Conquest"},
	{"id": "survival", "label": "Survival"},
	{"id": "koth", "label": "King of the Hill"},
	{"id": "regicide", "label": "Regicide"},
]

const RAIL := 214      # the tool rail on the left
const PROPS := 300     # the properties rail on the right
const UNDO_DEPTH := 40

const MONO := "ui-monospace, monospace"

## Credited on maps this player publishes (their profile name).
var author := ""
## null = the library; otherwise the map being edited.
var map = null
var tool := "terrain"
var paint: int = Terrain.Grass
var resource := "tree"
var brush := 3
## Where a drag started, in cells ({cx, cy} or null). Shared by the path and
## region tools: both are "press here, release there" gestures rather than
## continuous painting, so both need the anchor and neither wants the other's.
var dragFrom = null
## How thickly the scatter brush sprinkles, as a chance per candidate cell.
var scatterDensity := 0.25
## A lifted rectangle of map, waiting to be stamped down somewhere:
## {w, h, terrain: PackedByteArray, resources: [{type, dx, dy, amount}]} or null.
var clip = null
var clipFlipX := false
var clipFlipY := false
## Deterministic sprinkle: an author's scatter should not reshuffle per frame.
var scatterSeed := 1
var sym := "rot180"
var camX := 0.0
var camY := 0.0
var zoom := 4.0 # screen pixels per cell
var undo: Array = []
var redo: Array = []
var strokeOpen := false
var issues: Array = []
var dirty := true
var status := ""
var statusT := 0.0
var lastPan = null
## Which text field is being typed into, if any: "name" | "desc" | "import" | "script" | null.
var editing = null
var importBuf := ""
var newSize := 128
var newBiome := "temperate"
## The "roll one and edit it" controls in the library.
var rollPreset := "highlands"
var rollSeed := 1 + floori(JS.random() * 99999)
var rollPlayers := 2
## The random-map script, its seed, and whatever the last run had to say.
var scriptBuf: String = C.EXAMPLE_SCRIPT
var scriptSeed := 1 + floori(JS.random() * 99999)
var scriptMsgs: Array = [] # of {line, text, bad}
var showScript := false
var confirmDelete := ""
## Set when a map is opened: the first draw frames the whole thing.
var needsFit := true


## Keys, forwarded by the app. True when the editor consumed one.
func handleKey(key: String) -> bool:
	var ui := UI.ui
	if editing != null:
		if key == "Escape":
			editing = null
			return true
		if key == "Enter":
			# A script is the one field where Enter means "new line" rather than
			# "done" — a one-line script language would be a poor one.
			if editing == "script":
				scriptBuf += "\n"
				return true
			editing = null
			return true
		if key == "Backspace":
			if editing == "name" and map != null:
				map.name = map.name.substr(0, maxi(0, map.name.length() - 1))
			elif editing == "desc" and map != null:
				map.desc = map.desc.substr(0, maxi(0, map.desc.length() - 1))
			elif editing == "script":
				scriptBuf = scriptBuf.substr(0, maxi(0, scriptBuf.length() - 1))
			else:
				importBuf = importBuf.substr(0, maxi(0, importBuf.length() - 1))
			return true
		if key.length() == 1:
			if editing == "name" and map != null:
				map.name = (map.name + key).substr(0, 40)
			elif editing == "desc" and map != null:
				map.desc = (map.desc + key).substr(0, 160)
			elif editing == "script":
				scriptBuf += key
			else:
				importBuf += key
		return true
	if map == null:
		return false
	# Undo/redo are the two shortcuts an editor cannot be without.
	if (key == "z" or key == "Z") and ui.ctrlHeld:
		undoStep()
		return true
	if (key == "y" or key == "Y") and ui.ctrlHeld:
		redoStep()
		return true
	if key.length() == 1 and key >= "1" and key <= "9":
		var n := key.to_int()
		if n <= PALETTE.size():
			tool = "terrain"
			paint = PALETTE[n - 1]
			return true
	if key == "[":
		brush = maxi(1, brush - 1)
		return true
	if key == "]":
		brush = mini(24, brush + 1)
		return true
	return false


## Text pasted while a field is focused (the app forwards the clipboard on
## Ctrl+V). The browser build types a paste in as keystrokes; this is the same
## thing in one call.
func handlePaste(text: String) -> bool:
	if editing == null:
		return false
	for ch in text:
		if ch == "\n" or ch == "\r":
			if editing == "script" and ch == "\n":
				scriptBuf += "\n"
			continue
		handleKey(ch)
	return true


func say(msg: String) -> void:
	status = msg
	statusT = 3


# ------------------------------------------------------------ undo/redo --

func snapshot() -> Dictionary:
	var m: Dictionary = map
	return {
		"terrain": (m.terrain as PackedByteArray).duplicate(),
		"resources": m.resources.map(func(r): return r.duplicate()),
		"spawns": m.spawns.map(func(s): return s.duplicate()),
	}


func beginStroke() -> void:
	if strokeOpen or map == null:
		return
	strokeOpen = true
	undo.append(snapshot())
	if undo.size() > UNDO_DEPTH:
		undo.pop_front()
	redo.clear()


func applySnapshot(s: Dictionary) -> void:
	var m: Dictionary = map
	m.terrain = (s.terrain as PackedByteArray).duplicate()
	m.resources = s.resources.map(func(r): return r.duplicate())
	m.spawns = s.spawns.map(func(p): return p.duplicate())
	dirty = true


func undoStep() -> void:
	if map == null or undo.is_empty():
		say("Nothing to undo")
		return
	redo.append(snapshot())
	applySnapshot(undo.pop_back())
	say("Undone")


func redoStep() -> void:
	if map == null or redo.is_empty():
		say("Nothing to redo")
		return
	undo.append(snapshot())
	applySnapshot(redo.pop_back())
	say("Redone")


# --------------------------------------------------------------- editing --

func paintAt(cx: int, cy: int) -> void:
	var m: Dictionary = map
	var r := brush - 1
	var cols := int(m.cols)
	var rows := int(m.rows)
	var terrain: PackedByteArray = m.terrain
	for c in Symmetry.symmetryCells(sym, cx, cy, cols, rows):
		var sx: int = c[0]
		var sy: int = c[1]
		for dy in range(-r, r + 1):
			for dx in range(-r, r + 1):
				if dx * dx + dy * dy > r * r + r * 0.6:
					continue # round brush
				var nx := sx + dx
				var ny := sy + dy
				if nx < 0 or ny < 0 or nx >= cols or ny >= rows:
					continue
				terrain[ny * cols + nx] = paint
	m.terrain = terrain
	dirty = true


## Stroke a straight run of ground from one cell to another.
##
## Rivers, ridges and roads are lines, and a round brush dragged by hand makes
## a wobbly sausage rather than a river. Walking the line and stamping the
## brush at every step gives an even-width run with no gaps, however steep the
## diagonal — the same reason the wall tool in the match uses a line walk
## rather than sampling along the distance.
func strokePath(x0: int, y0: int, x1: int, y1: int) -> void:
	var steps := maxi(absi(x1 - x0), absi(y1 - y0))
	if steps == 0:
		paintAt(x0, y0)
		return
	for i in steps + 1:
		var t := float(i) / steps
		paintAt(JS.roundi(x0 + (x1 - x0) * t), JS.roundi(y0 + (y1 - y0) * t))


## `x | 0` on a double of any size: truncate, wrap modulo 2^32, sign it.
static func _toInt32(x: float) -> int:
	var m := fmod(JS.trunc(x), 4294967296.0)
	if m < 0:
		m += 4294967296.0
	return JS.i32(int(m))


## A cheap deterministic hash of a cell, for the scatter brush.
##
## It has to be a function of the *cell*, not a running random: an author
## dragging back over ground they already covered should not keep thickening
## it, and the same drag twice should look the same both times.
func scatterRoll(cx: int, cy: int) -> float:
	var h := _toInt32(float(cx) * 374761393.0 + float(cy) * 668265263.0 + float(scatterSeed) * 2246822519.0)
	h = JS.imul(h ^ JS.ushr(h, 13), 1274126177)
	return float(JS.u32(h ^ JS.ushr(h, 16))) / 4294967296.0


## Sprinkle resource nodes across the brush at the chosen density, rather than
## one node per click. A woodline is fifty trees; placing those by hand is the
## single most tedious thing in the editor.
func scatterAt(cx: int, cy: int) -> void:
	var m: Dictionary = map
	var r := brush - 1
	var TILE := float(C.TILE)
	var cols := int(m.cols)
	var rows := int(m.rows)
	var occupied := {}
	for res in m.resources:
		occupied["%d,%d" % [floori(res.x / TILE), floori(res.y / TILE)]] = true
	for c in Symmetry.symmetryCells(sym, cx, cy, cols, rows):
		var ax: int = c[0]
		var ay: int = c[1]
		for dy in range(-r, r + 1):
			for dx in range(-r, r + 1):
				if dx * dx + dy * dy > r * r + r * 0.6:
					continue
				var nx := ax + dx
				var ny := ay + dy
				if nx < 0 or ny < 0 or nx >= cols or ny >= rows:
					continue
				if scatterRoll(nx, ny) > scatterDensity:
					continue
				var key := "%d,%d" % [nx, ny]
				if occupied.has(key):
					continue
				occupied[key] = true
				m.resources.append({
					"type": resource,
					"x": nx * TILE + TILE / 2,
					"y": ny * TILE + TILE / 2,
					"amount": RES_AMOUNT[resource],
				})
	dirty = true


## Lift a rectangle of ground and everything standing on it.
func copyRegion(x0: int, y0: int, x1: int, y1: int) -> void:
	var m: Dictionary = map
	var TILE := float(C.TILE)
	var cols := int(m.cols)
	var ax := maxi(0, mini(x0, x1))
	var ay := maxi(0, mini(y0, y1))
	var bx := mini(cols - 1, maxi(x0, x1))
	var by := mini(int(m.rows) - 1, maxi(y0, y1))
	var w := bx - ax + 1
	var h := by - ay + 1
	if w < 1 or h < 1:
		return
	var src: PackedByteArray = m.terrain
	var terrain := PackedByteArray()
	terrain.resize(w * h)
	for cy in h:
		for cx in w:
			terrain[cy * w + cx] = src[(ay + cy) * cols + (ax + cx)]
	var resources: Array = []
	for r in m.resources:
		var e := {
			"type": r.type,
			"dx": floori(r.x / TILE) - ax,
			"dy": floori(r.y / TILE) - ay,
			"amount": r.amount,
		}
		if e.dx >= 0 and e.dy >= 0 and e.dx < w and e.dy < h:
			resources.append(e)
	clip = {"w": w, "h": h, "terrain": terrain, "resources": resources}
	say("Copied %d×%d — click to stamp it" % [w, h])


## Stamp the lifted region with its top-left at the cursor.
##
## Deliberately *not* mirrored through the symmetry setting. Symmetry exists so
## a stroke lands in every quadrant at once; a stamp is a considered placement
## of something already built, and having it also fire into three other corners
## is almost never what an author means. Flip X/Y are offered instead, which is
## what you actually want when facing a base at the opposite seat.
func pasteRegion(cx: int, cy: int) -> void:
	var m: Dictionary = map
	var c = clip
	if c == null:
		return
	var TILE := float(C.TILE)
	var cols := int(m.cols)
	var rows := int(m.rows)
	var terrain: PackedByteArray = m.terrain
	var ct: PackedByteArray = c.terrain
	var cw: int = c.w
	var ch: int = c.h
	for dy in ch:
		for dx in cw:
			var sx := cw - 1 - dx if clipFlipX else dx
			var sy := ch - 1 - dy if clipFlipY else dy
			var nx := cx + dx
			var ny := cy + dy
			if nx < 0 or ny < 0 or nx >= cols or ny >= rows:
				continue
			terrain[ny * cols + nx] = ct[sy * cw + sx]
	m.terrain = terrain
	# Anything already standing inside the stamped area goes, or the paste
	# would layer new trees on top of old ones.
	var ax := cx
	var ay := cy
	m.resources = m.resources.filter(func(r):
		var rx := floori(r.x / TILE) - ax
		var ry := floori(r.y / TILE) - ay
		return rx < 0 or ry < 0 or rx >= cw or ry >= ch)
	for r in c.resources:
		var dx: int = cw - 1 - r.dx if clipFlipX else r.dx
		var dy: int = ch - 1 - r.dy if clipFlipY else r.dy
		var nx := cx + dx
		var ny := cy + dy
		if nx < 0 or ny < 0 or nx >= cols or ny >= rows:
			continue
		m.resources.append({"type": r.type, "x": nx * TILE + TILE / 2, "y": ny * TILE + TILE / 2, "amount": r.amount})
	dirty = true
	say("Stamped")


func placeResource(cx: int, cy: int) -> void:
	var m: Dictionary = map
	var TILE := float(C.TILE)
	var cols := int(m.cols)
	var rows := int(m.rows)
	for c in Symmetry.symmetryCells(sym, cx, cy, cols, rows):
		var sx: int = c[0]
		var sy: int = c[1]
		# Never stack two nodes on one cell — the sim would spawn them on top of
		# each other and a villager could only ever reach one.
		if m.resources.any(func(res): return floori(res.x / TILE) == sx and floori(res.y / TILE) == sy):
			continue
		if sx < 0 or sy < 0 or sx >= cols or sy >= rows:
			continue
		m.resources.append({
			"type": resource,
			"x": sx * TILE + TILE / 2,
			"y": sy * TILE + TILE / 2,
			"amount": RES_AMOUNT[resource],
		})
	dirty = true


func placeSpawn(cx: int, cy: int) -> void:
	var m: Dictionary = map
	if m.nomad == "forced":
		say("This map is always nomad — spawn points are disabled")
		return
	var TILE := float(C.TILE)
	var cells := Symmetry.symmetryCells(sym, cx, cy, int(m.cols), int(m.rows))
	for c in cells:
		if m.spawns.size() >= 8:
			break
		var wx: float = c[0] * TILE + TILE / 2
		var wy: float = c[1] * TILE + TILE / 2
		if m.spawns.any(func(s): return JS.hypot(s.x - wx, s.y - wy) < TILE * 4):
			continue
		m.spawns.append({"x": wx, "y": wy})
	dirty = true


func eraseAt(cx: int, cy: int) -> void:
	var m: Dictionary = map
	var TILE := float(C.TILE)
	var rad := brush * TILE
	for c in Symmetry.symmetryCells(sym, cx, cy, int(m.cols), int(m.rows)):
		var wx: float = c[0] * TILE + TILE / 2
		var wy: float = c[1] * TILE + TILE / 2
		m.resources = m.resources.filter(func(r): return JS.hypot(r.x - wx, r.y - wy) > rad)
		m.spawns = m.spawns.filter(func(s): return JS.hypot(s.x - wx, s.y - wy) > rad)
	dirty = true


## Flatten every cell back to the biome's base ground, keeping resources.
func levelAll() -> void:
	var m: Dictionary = map
	undo.append(snapshot())
	var t: PackedByteArray = m.terrain
	t.fill(int(TerrainKinds.biomeById(m.biome).ground[0]))
	m.terrain = t
	dirty = true
	say("Land levelled")


# ---------------------------------------------------------------- drawing --

func draw(W: float, H: float, time: float, mouseDown: bool):
	if statusT > 0:
		statusT -= 1.0 / 60
	if map == null:
		return drawLibrary(W, H)
	return drawEditor(W, H, time, mouseDown)


# ---- library ----

func drawLibrary(W: float, H: float):
	var ui := UI.ui
	var ctx := ui.ctx
	var PAL: Dictionary = C.PAL
	ctx.fillStyle = "#171208"; ctx.fillRect(0, 0, W, H)
	ui.text("Map Editor", W / 2, 58, {"align": "center", "size": 34, "bold": true, "color": PAL.uiAccent, "font": "Georgia, serif"})
	ui.text("Build a battlefield. Paint the ground, seat the players, decide what it is for.",
		W / 2, 86, {"align": "center", "size": 13, "color": "#9a917b"})

	var colW := minf(980, W - 80)
	var x0 := JS.round(W / 2 - colW / 2)
	var y := 122.0

	# ---- new map ----
	var MAP_SIZES: Array = C.MAP_SIZES
	var perRow := 5
	var sizeRows := ceili(float(MAP_SIZES.size()) / perRow)
	var panelH := 92 + sizeRows * 30 + 42
	ui.panel(x0, y, colW, panelH, {"light": true})
	ui.text("New map", x0 + 20, y + 26, {"size": 16, "bold": true, "color": PAL.uiAccent})
	var chosen = null
	for s in MAP_SIZES:
		if int(s.cols) == newSize:
			chosen = s
			break
	if chosen != null:
		ui.text("%s — %d × %d cells" % [chosen.label, int(chosen.cols), int(chosen.cols)], x0 + 110, y + 26,
			{"size": 12, "bold": true, "color": "#e7ddc4"})
		ui.text(chosen.desc, x0 + 110, y + 42, {"size": 11, "color": "#9a917b"})
	ui.text("Size", x0 + 20, y + 68, {"size": 12, "color": "#9a917b"})
	var sw := floorf((colW - 220) / perRow) - 6
	for i in MAP_SIZES.size():
		var s: Dictionary = MAP_SIZES[i]
		var bx2 := x0 + 66 + (i % perRow) * (sw + 6)
		var by2 := y + 56 + floori(float(i) / perRow) * 30
		if ui.button(s.label, bx2, by2, sw, 26, {
			"accent": newSize == int(s.cols), "size": 11,
			"tooltip": [s.label, "%d × %d cells" % [int(s.cols), int(s.cols)], s.desc],
		}):
			newSize = int(s.cols)
	var by3 := y + 56 + sizeRows * 30 + 6
	ui.text("Biome", x0 + 20, by3 + 13, {"size": 12, "color": "#9a917b"})
	var BIOMES: Array = C.BIOMES
	for i in BIOMES.size():
		var b: Dictionary = BIOMES[i]
		if ui.button(b.name, x0 + 66 + i * 110, by3, 104, 26,
			{"accent": newBiome == b.id, "size": 11.5, "tooltip": [b.name, b.desc]}):
			newBiome = b.id
	if ui.button("Create", x0 + colW - 140, y + 56, 120, 44, {"accent": true, "size": 15}):
		open(CustomMaps.newCustomMap("New Map", newSize, newBiome))
	y += panelH + 16

	# ---- roll from the generator ----
	ui.panel(x0, y, colW, 78, {"light": true})
	ui.text("Start from a generated map", x0 + 20, y + 24, {"size": 14, "bold": true, "color": PAL.uiAccent})
	ui.text("Rolls a real map with its spawn points already placed, then hands it to you to edit.",
		x0 + 20, y + 40, {"size": 11, "color": "#9a917b"})
	if true:
		var PRESETS: Array = MapGen.PRESETS
		var pw := minf(96, floorf((colW - 300) / PRESETS.size()) - 4)
		for i in PRESETS.size():
			var pp: Dictionary = PRESETS[i]
			if ui.button(String(pp.name).split(" ")[0], x0 + 20 + i * (pw + 4), y + 48, pw, 22, {
				"accent": rollPreset == pp.id, "size": 10.5, "tooltip": [pp.name, pp.desc],
			}):
				rollPreset = pp.id
		var rx := x0 + colW - 260
		ui.text("seed %d" % rollSeed, rx, y + 30, {"size": 11, "color": "#cabfa4", "font": MONO})
		if ui.button("New seed", rx, y + 40, 84, 24, {"size": 11}):
			rollSeed = 1 + floori(JS.random() * 99999)
		ui.text("%d seats" % rollPlayers, rx + 96, y + 30, {"size": 11, "color": "#cabfa4"})
		if ui.button("−", rx + 96, y + 40, 26, 24, {"size": 13, "disabled": rollPlayers <= 2}):
			rollPlayers -= 1
		if ui.button("+", rx + 126, y + 40, 26, 24, {"size": 13, "disabled": rollPlayers >= int(C.MAX_SEATS)}):
			rollPlayers += 1
		if ui.button("Roll & edit", rx + 160, y + 40, 96, 24, {"accent": true, "size": 12}):
			open(CustomMaps.customFromGenerated(rollPreset, rollSeed, rollPlayers))
			say("Rolled — edit it however you like")
	y += 94

	# ---- import ----
	ui.panel(x0, y, colW, 78, {"light": true})
	ui.text("Paste a map code", x0 + 20, y + 26, {"size": 14, "bold": true, "color": PAL.uiAccent})
	var fw := colW - 200
	var fhov := ui.hit(x0 + 20, y + 38, fw, 26)
	ctx.fillStyle = "rgba(60,50,20,0.6)" if editing == "import" else ("rgba(255,255,255,0.08)" if fhov else "rgba(0,0,0,0.32)")
	ctx.beginPath(); ctx.roundRect(x0 + 20, y + 38, fw, 26, 4); ctx.fill()
	ctx.strokeStyle = "#ffd24a" if editing == "import" else "rgba(255,255,255,0.1)"
	ctx.lineWidth = 1; ctx.stroke()
	var shown := ("…" + importBuf.substr(importBuf.length() - 58)) if importBuf.length() > 60 else importBuf
	var hint := "paste with Ctrl+V, then Enter" if editing == "import" else "click, then paste a BBMAP code"
	ui.text(shown if shown != "" else hint,
		x0 + 28, y + 52, {"size": 11, "color": "#e7ddc4" if importBuf != "" else "#6f6a5c", "font": MONO})
	if fhov and ui.clicked:
		editing = null if editing == "import" else "import"
	if ui.button("Import", x0 + colW - 160, y + 36, 140, 30, {"size": 13, "disabled": importBuf.strip_edges() == ""}):
		var m = CustomMaps.deserialiseMap(importBuf)
		if m != null:
			CustomMaps.saveCustomMap(m)
			importBuf = ""
			editing = null
			say("Imported \"%s\"" % m.name)
		else:
			say("That isn't a map code")
	y += 94

	# ---- random map script ----
	# Folded away by default. It is the most powerful thing on this screen and
	# also the one most people will never touch, and an editor that greets you
	# with a code box has told you it is for programmers.
	if showScript:
		var shH := minf(360, H - y - 120)
		ui.panel(x0, y, colW, shH, {"light": true})
		ui.text("Random map script", x0 + 20, y + 24, {"size": 14, "bold": true, "color": PAL.uiAccent})
		ui.text("One script, a different map every seed — which is how map pools actually work.",
			x0 + 150, y + 24, {"size": 11, "color": "#9a917b"})
		if ui.button("Hide", x0 + colW - 90, y + 12, 70, 24, {"size": 12}):
			showScript = false

		var boxW := colW - 320
		var boxH := shH - 96
		var bhov := ui.hit(x0 + 20, y + 38, boxW, boxH)
		ctx.fillStyle = "rgba(40,34,14,0.85)" if editing == "script" else "rgba(0,0,0,0.42)"
		ctx.beginPath(); ctx.roundRect(x0 + 20, y + 38, boxW, boxH, 4); ctx.fill()
		ctx.strokeStyle = "#ffd24a" if editing == "script" else "rgba(255,255,255,0.1)"
		ctx.lineWidth = 1; ctx.stroke()
		# Line numbers, because every error the runner reports names one.
		var lines := scriptBuf.split("\n")
		var lh := 12
		var visible := floori((boxH - 12) / lh)
		var bad := {}
		for m2 in scriptMsgs:
			if m2.bad:
				bad[int(m2.line)] = true
		for i in mini(visible, lines.size()):
			var ln: String = lines[i]
			ui.text(str(i + 1).lpad(3), x0 + 28, y + 52 + i * lh,
				{"size": 9.5, "color": "#e0786a" if bad.has(i + 1) else "#5a5548", "font": MONO})
			ui.text(ln.substr(0, 78), x0 + 56, y + 52 + i * lh,
				{"size": 10, "color": "#ffb0a4" if bad.has(i + 1) else "#cabfa4", "font": MONO})
		if lines.size() > visible:
			ui.text("… %d more lines" % (lines.size() - visible), x0 + 56, y + 52 + visible * lh,
				{"size": 9.5, "color": "#6f6a5c"})
		if bhov and ui.clicked:
			editing = null if editing == "script" else "script"
			ui.pointerConsumed = true

		# The command reference sits beside the box rather than in a manual —
		# this is a language nobody has seen before and there is nowhere else to
		# look it up.
		var rx := x0 + boxW + 36
		ui.text("COMMANDS", rx, y + 50, {"size": 9.5, "bold": true, "color": "#8f8770"})
		var cmds: Array = C.SCRIPT_COMMANDS
		for i in mini(cmds.size(), maxi(0, floori((boxH - 30) / 22))):
			var cmd: Dictionary = cmds[i]
			ui.text(cmd.name, rx, y + 66 + i * 22, {"size": 10.5, "bold": true, "color": "#e7ddc4", "font": MONO})
			ui.text(String(cmd.args).substr(0, 34), rx + 62, y + 66 + i * 22, {"size": 9.5, "color": "#8f8770", "font": MONO})

		var by4 := y + shH - 46
		ui.text("seed %d" % scriptSeed, x0 + 20, by4 + 18, {"size": 11, "color": "#cabfa4", "font": MONO})
		if ui.button("New seed", x0 + 110, by4, 84, 28, {"size": 11}):
			scriptSeed = 1 + floori(JS.random() * 99999)
		if ui.button("Reset to example", x0 + 202, by4, 130, 28, {"size": 11}):
			scriptBuf = C.EXAMPLE_SCRIPT
			scriptMsgs = []
		# Whatever the last run said, good or bad.
		var firstBad = null
		for m2 in scriptMsgs:
			if m2.bad:
				firstBad = m2
				break
		if not scriptMsgs.is_empty():
			ui.text(("line %d: %s" % [int(firstBad.line), firstBad.text]) if firstBad != null else String(scriptMsgs[0].text),
				x0 + 344, by4 + 18, {"size": 11, "color": "#e0786a" if firstBad != null else "#c3b98f"})
		if ui.button("Generate & edit", x0 + colW - 170, by4, 150, 28, {"accent": true, "size": 13}):
			var res := MapScript.runMapScript(scriptBuf, scriptSeed, "Scripted map")
			scriptMsgs = []
			for e in res.errors:
				var d: Dictionary = e.duplicate()
				d.bad = true
				scriptMsgs.append(d)
			for e in res.warnings:
				var d: Dictionary = e.duplicate()
				d.bad = false
				scriptMsgs.append(d)
			if res.map != null:
				open(res.map)
				say("Generated, with notes" if res.warnings.size() else "Generated")
			else:
				say("%d error%s in the script" % [res.errors.size(), "" if res.errors.size() == 1 else "s"])
		y += shH + 16
	else:
		ui.panel(x0, y, colW, 52, {"light": true})
		ui.text("Random map script", x0 + 20, y + 24, {"size": 13, "bold": true, "color": PAL.uiAccent})
		ui.text("Write rules instead of a map, and roll a different battlefield every seed.",
			x0 + 168, y + 24, {"size": 11, "color": "#9a917b"})
		if ui.button("Open", x0 + colW - 110, y + 12, 90, 28, {"size": 12}):
			showScript = true
		y += 68

	# ---- library ----
	var maps := CustomMaps.listCustomMaps()
	ui.text("Your maps  (%d)" % maps.size(), x0, y, {"size": 15, "bold": true, "color": PAL.uiAccent})
	y += 14
	var rowH := 54
	var listH := H - y - 90
	ui.panel(x0, y, colW, maxf(70, listH), {"light": true})
	if maps.is_empty():
		ui.text("Nothing saved yet — make one above.", x0 + 20, y + 40, {"size": 13, "color": "#6f6a5c"})
	var ry := y + 12
	for m in maps:
		if ry + rowH > y + listH:
			break
		var hov := ui.hit(x0 + 10, ry, colW - 20, rowH - 6)
		ctx.fillStyle = "rgba(255,255,255,0.06)" if hov else "rgba(0,0,0,0.18)"
		ctx.beginPath(); ctx.roundRect(x0 + 10, ry, colW - 20, rowH - 6, 5); ctx.fill()
		thumbnail(ctx, x0 + 18, ry + 5, 38, m)
		ui.text(m.name, x0 + 66, ry + 20, {"size": 14, "bold": true, "color": "#f2e8d0"})
		ctx.font = "bold 14px system-ui, sans-serif"
		var tagX: float = x0 + 66 + ctx.measureText(m.name).width + 10
		var published := CustomMaps.isPublished(m)
		ui.text("IN THE POOL" if published else "DRAFT", tagX, ry + 19,
			{"size": 9.5, "bold": true, "color": "#8fd07a" if published else "#a89f88"})
		var modeText := "any mode"
		if not m.modes.is_empty():
			var labels: Array = []
			for x in m.modes:
				var label = x
				for y2 in MODES:
					if y2.id == x:
						label = y2.label
						break
				labels.append(label)
			modeText = ", ".join(labels)
		var sizeLabel := "%d×%d" % [int(m.cols), int(m.rows)]
		for z in MAP_SIZES:
			if int(z.cols) == int(m.cols):
				sizeLabel = z.label
				break
		ui.text("%s (%d×%d) · %s · %s · %d–%d players%s" % [sizeLabel, int(m.cols), int(m.rows), TerrainKinds.biomeById(m.biome).name, modeText,
			int(m.minPlayers), int(m.maxPlayers), " · nomad" if m.nomad == "forced" else ""],
			x0 + 66, ry + 38, {"size": 11, "color": "#9a917b"})
		var errs := not published and hasErrorsCached(m)
		var tip: Array
		if errs:
			tip = ["Cannot publish", "Open it and fix the errors first."]
		elif published:
			tip = ["Take out of the pool", "It stays here as a draft."]
		else:
			tip = ["Publish", "Put it in the map pool: Skirmish, Random and online lobbies."]
		if ui.button("Unpublish" if published else "Publish", x0 + colW - 336, ry + 12, 90, 26, {
			"size": 12, "accent": not published and not errs, "disabled": errs, "tooltip": tip,
		}):
			if published:
				CustomMaps.unpublishCustomMap(m)
				say("\"%s\" is a draft again" % m.name)
			else:
				CustomMaps.publishCustomMap(m, author)
				say("Published \"%s\"" % m.name)
		if ui.button("Edit", x0 + colW - 240, ry + 12, 68, 26, {"size": 12}):
			open(m)
		if ui.button("Copy code", x0 + colW - 166, ry + 12, 84, 26, {"size": 12,
			"tooltip": ["Export", "Puts the map's share code in the box above so you can copy it."]}):
			importBuf = CustomMaps.serialiseMap(m)
			say("Code placed in the box above — select and copy it")
		var armed: bool = confirmDelete == m.id
		if ui.button("Sure?" if armed else "Delete", x0 + colW - 76, ry + 12, 62, 26, {"size": 12, "danger": armed}):
			if armed:
				CustomMaps.deleteCustomMap(m.id)
				confirmDelete = ""
				say("Deleted")
			else:
				confirmDelete = m.id
		ry += rowH

	if ui.button("‹  Back", x0, H - 66, 140, 44, {"size": 15}):
		return {"kind": "back"}
	if statusT > 0:
		ui.text(status, x0 + 160, H - 44, {"size": 12.5, "color": "#ffd24a"})
	return null


# Validation walks the whole map; the library asks every frame for every row.
var errCache := {} # id -> {at, errs}
func hasErrorsCached(m: Dictionary) -> bool:
	var hit = errCache.get(m.id)
	if hit != null and hit.at == m.updated:
		return hit.errs
	var errs := CustomMaps.hasErrors(CustomMaps.validateMap(m))
	errCache[m.id] = {"at": m.updated, "errs": errs}
	return errs


func open(m: Dictionary) -> void:
	map = m
	undo.clear()
	redo.clear()
	dirty = true
	confirmDelete = ""
	editing = null
	# Frame the whole map to begin with — you cannot edit what you cannot see.
	# The canvas size is only known once we draw, so the fit happens there.
	needsFit = true
	camX = m.cols / 2.0
	camY = m.rows / 2.0


## A tiny picture of a map, for the library rows.
func thumbnail(ctx: Ctx, x: float, y: float, size: float, m: Dictionary) -> void:
	MapThumb.drawMapThumbnail(ctx, x, y, size, m, {"spawns": true})


# ---- editor ----

func drawEditor(W: float, H: float, time: float, mouseDown: bool):
	var ui := UI.ui
	var m: Dictionary = map
	var ctx := ui.ctx
	var PAL: Dictionary = C.PAL
	ctx.fillStyle = "#12100a"; ctx.fillRect(0, 0, W, H)

	if dirty:
		issues = CustomMaps.validateMap(m)
		dirty = false

	var canvasX := RAIL
	var canvasY := 44
	var canvasW := W - RAIL - PROPS
	var canvasH := H - canvasY - 52

	drawCanvas(canvasX, canvasY, canvasW, canvasH, m, mouseDown, time)
	drawToolRail(0, canvasY, RAIL, H - canvasY - 52, m)
	var action = drawProperties(W - PROPS, canvasY, PROPS, H - canvasY - 52, m, time)
	if action != null:
		return action

	# ---- top bar ----
	ctx.fillStyle = "rgba(24,18,10,0.96)"; ctx.fillRect(0, 0, W, canvasY)
	ctx.fillStyle = Palette.withAlpha(PAL.uiAccent, 0.4); ctx.fillRect(0, canvasY - 1.5, W, 1.5)
	ui.text("Editing", 16, 27, {"size": 12, "color": "#9a917b"})
	# The name is editable in place — a map you cannot name is a map you cannot find.
	var nameW := 260
	var nhov := ui.hit(70, 10, nameW, 24)
	ctx.fillStyle = "rgba(60,50,20,0.7)" if editing == "name" else ("rgba(255,255,255,0.07)" if nhov else "transparent")
	ctx.beginPath(); ctx.roundRect(70, 10, nameW, 24, 4); ctx.fill()
	ui.text(m.name + ("_" if editing == "name" and floori(time * 2) % 2 else ""), 78, 27,
		{"size": 15, "bold": true, "color": "#f2e8d0"})
	if nhov and ui.clicked:
		editing = null if editing == "name" else "name"
		ui.pointerConsumed = true

	if ui.button("Undo", 350, 8, 62, 28, {"size": 12, "disabled": undo.is_empty(), "tooltip": ["Undo", "Ctrl+Z"]}):
		undoStep()
	if ui.button("Redo", 416, 8, 62, 28, {"size": 12, "disabled": redo.is_empty(), "tooltip": ["Redo", "Ctrl+Y"]}):
		redoStep()
	if ui.button("Fit", 486, 8, 48, 28, {"size": 12, "tooltip": ["Fit to view", "Frame the whole map again."]}):
		needsFit = true
	if ui.button("Level land", 538, 8, 96, 28, {"size": 12,
		"tooltip": ["Level the land", "Flattens every cell back to this biome's base ground.", "Resources and spawns are kept."]}):
		levelAll()

	var errs := CustomMaps.hasErrors(issues)
	# Publishing is the second step after saving: it puts the map in the pool
	# beside the built-in battlefields, for Skirmish, Random and online games.
	var pub := CustomMaps.isPublished(m)
	var pubTip: Array
	if pub:
		pubTip = ["Published", "This map is in the map pool: on the Skirmish list, in the Random roll, and offered to online lobbies.", "Click to take it back out (it stays here as a draft)."]
	elif errs:
		pubTip = ["Cannot publish", "Fix the errors listed on the right first — the pool is what other players get handed."]
	else:
		pubTip = ["Publish to the map pool", "Save it and put it on the Skirmish list, in the Random roll, and in online lobbies."]
	if ui.button("✓ In the pool" if pub else "Publish", W - 426, 8, 118, 28, {
		"size": 12.5, "accent": not pub and not errs, "disabled": not pub and errs, "tooltip": pubTip,
	}):
		if pub:
			CustomMaps.unpublishCustomMap(m)
			say("\"%s\" taken out of the pool — it's a draft again" % m.name)
		else:
			var why := CustomMaps.publishCustomMap(m, author)
			say(("Can't publish: %s" % why[0].text) if why.size() else ("Published \"%s\" — it's in the map pool now" % m.name))
	if ui.button("Save", W - 300, 8, 78, 28, {"size": 12.5, "accent": pub}):
		CustomMaps.saveCustomMap(m)
		say("Saved \"%s\"" % m.name)
	if ui.button("Test map", W - 216, 8, 92, 28, {
		"size": 12.5, "disabled": errs,
		"tooltip": ["Cannot test", "Fix the errors listed on the right first."] if errs else ["Test", "Save and play a match on this map right now."],
	}):
		CustomMaps.saveCustomMap(m)
		return {"kind": "test", "map": m}
	if ui.button("Close", W - 118, 8, 78, 28, {"size": 12.5}):
		CustomMaps.saveCustomMap(m)
		map = null
		say("Saved")
	if statusT > 0:
		ui.text(status, W / 2, H - 26, {"align": "center", "size": 13, "bold": true, "color": "#ffd24a"})
	return null


# ---- canvas ----

func drawCanvas(x: float, y: float, w: float, h: float, m: Dictionary, mouseDown: bool, time: float) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var PAL: Dictionary = C.PAL
	var TILE := float(C.TILE)
	var cols := int(m.cols)
	var rows := int(m.rows)
	ctx.save()
	ctx.beginPath(); ctx.rect(x, y, w, h); ctx.clip()
	ctx.fillStyle = "#0b0906"; ctx.fillRect(x, y, w, h)

	if needsFit:
		needsFit = false
		zoom = maxf(1.5, minf(24, minf(w / cols, h / rows) * 0.92))
		camX = cols / 2.0
		camY = rows / 2.0
	var z := zoom
	var toScreenX := func(cx: float) -> float: return x + w / 2 + (cx - camX) * z
	var toScreenY := func(cy: float) -> float: return y + h / 2 + (cy - camY) * z
	var toCellX := func(sx: float) -> float: return (sx - x - w / 2) / z + camX
	var toCellY := func(sy: float) -> float: return (sy - y - h / 2) / z + camY

	# Only the cells actually on screen — a 200×200 map is 40,000 of them and
	# the editor redraws every frame.
	var terrain: PackedByteArray = m.terrain
	var c0 := maxi(0, floori(toCellX.call(x)))
	var c1 := mini(cols - 1, ceili(toCellX.call(x + w)))
	var r0 := maxi(0, floori(toCellY.call(y)))
	var r1 := mini(rows - 1, ceili(toCellY.call(y + h)))
	# The cells and the resource nodes: the TS fills a rect per cell and an
	# arc per node, thousands a frame, which a canvas does in a blink and Ctx
	# does not — so they're filled into an image the size of the view (the
	# same rects and dots, natively) and that is drawn, rebuilt only when the
	# ground, the nodes or the view change. The seats, the border and every
	# overlay are drawn as ever.
	ctx.drawImage(_bakedCanvas(x, y, w, h, m, c0, c1, r0, r1), x, y, w, h)
	# Spawn points, numbered by seat.
	var teams: Array = PAL.teams
	for i in m.spawns.size():
		var s: Dictionary = m.spawns[i]
		var sx: float = toScreenX.call(s.x / TILE) + z / 2
		var sy: float = toScreenY.call(s.y / TILE) + z / 2
		var r := maxf(7, z * 2)
		ctx.fillStyle = Palette.withAlpha(teams[i % teams.size()].main, 0.4)
		ctx.beginPath(); ctx.arc(sx, sy, r, 0, PI * 2); ctx.fill()
		ctx.strokeStyle = teams[i % teams.size()].main; ctx.lineWidth = 2
		ctx.beginPath(); ctx.arc(sx, sy, r, 0, PI * 2); ctx.stroke()
		ui.text(str(i + 1), sx, sy + 4, {"align": "center", "size": 12, "bold": true, "color": "#fff"})
	# Map border.
	ctx.strokeStyle = Palette.withAlpha(PAL.uiAccent, 0.5); ctx.lineWidth = 1.5
	ctx.strokeRect(toScreenX.call(0), toScreenY.call(0), cols * z, rows * z)

	# ---- interaction ----
	var over := ui.hit(x, y, w, h) and not ui.pointerConsumed
	var hx := floori(toCellX.call(ui.mx))
	var hy := floori(toCellY.call(ui.my))
	if over:
		# Symmetry preview: show every cell this stroke would touch.
		var cells := Symmetry.symmetryCells(sym, hx, hy, cols, rows)
		var rr := brush if tool == "terrain" else 1
		for c in cells:
			var sx: int = c[0]
			var sy: int = c[1]
			ctx.strokeStyle = Palette.withAlpha("#ffd24a", 0.95 if sx == hx and sy == hy else 0.5)
			ctx.lineWidth = 1.5
			ctx.beginPath()
			ctx.arc(toScreenX.call(sx) + z / 2, toScreenY.call(sy) + z / 2, maxf(3, rr * z), 0, PI * 2)
			ctx.stroke()
		if mouseDown and ui.rightHeld:
			# Right-drag pans, which is the only way to work on a big map.
			if lastPan != null:
				camX -= (ui.mx - lastPan.x) / z
				camY -= (ui.my - lastPan.y) / z
			lastPan = {"x": ui.mx, "y": ui.my}
		elif mouseDown:
			beginStroke()
			if hx >= 0 and hy >= 0 and hx < cols and hy < rows:
				if tool == "terrain":
					paintAt(hx, hy)
				elif tool == "scatter":
					scatterAt(hx, hy)
				elif tool == "resource":
					placeResource(hx, hy)
				elif tool == "spawn":
					placeSpawn(hx, hy)
				elif tool == "path" or tool == "region":
					# Both are press-here-release-there gestures. Remember where the
					# press landed; the work happens on release, below.
					if dragFrom == null:
						dragFrom = {"cx": hx, "cy": hy}
				else:
					eraseAt(hx, hy)
		if ui.wheel:
			var before := [toCellX.call(ui.mx), toCellY.call(ui.my)]
			zoom = maxf(1.5, minf(24, zoom * (1.15 if ui.wheel < 0 else 1 / 1.15)))
			# Keep the cell under the cursor under the cursor. (As in the TS, the
			# helpers use this frame's `z`, so this nets out to nothing and the
			# zoom pivots on the view's centre; kept verbatim.)
			camX += before[0] - toCellX.call(ui.mx)
			camY += before[1] - toCellY.call(ui.my)
		ui.text("%d, %d" % [hx, hy], x + w - 10, y + h - 10, {"align": "right", "size": 11, "color": "#8a8278", "font": MONO})
	# Preview for the two gesture tools, drawn while the button is still down so
	# an author can see the run or the rectangle before committing to it.
	if dragFrom != null and mouseDown:
		var a: Dictionary = dragFrom
		ctx.setLineDash([4, 3])
		ctx.strokeStyle = "#ffd24a"
		ctx.lineWidth = 1.5
		if tool == "path":
			ctx.beginPath()
			ctx.moveTo(toScreenX.call(a.cx) + z / 2, toScreenY.call(a.cy) + z / 2)
			ctx.lineTo(toScreenX.call(hx) + z / 2, toScreenY.call(hy) + z / 2)
			ctx.stroke()
		else:
			var sx: float = toScreenX.call(mini(a.cx, hx))
			var sy: float = toScreenY.call(mini(a.cy, hy))
			ctx.strokeRect(sx, sy, (absi(hx - a.cx) + 1) * z, (absi(hy - a.cy) + 1) * z)
		ctx.setLineDash([])
	# A copied region follows the cursor, so a stamp lands where you expect.
	if over and tool == "region" and clip != null and dragFrom == null:
		ctx.strokeStyle = Palette.withAlpha("#7fd0ff", 0.9)
		ctx.lineWidth = 1.5
		ctx.strokeRect(toScreenX.call(hx), toScreenY.call(hy), clip.w * z, clip.h * z)

	if not mouseDown:
		# Release: this is where the gesture tools actually do their work, so the
		# author sees the whole shape before it is committed.
		if dragFrom != null:
			var a: Dictionary = dragFrom
			dragFrom = null
			var inside := hx >= 0 and hy >= 0 and hx < cols and hy < rows
			if tool == "path" and inside:
				strokePath(a.cx, a.cy, hx, hy)
			elif tool == "region":
				# A drag lifts a region; a click (start and end on one cell) stamps
				# the one already held. One tool, because copy and paste are two
				# halves of the same act and splitting them across two buttons means
				# more trips to the rail than to the map.
				if a.cx == hx and a.cy == hy and clip != null:
					pasteRegion(hx, hy)
				else:
					copyRegion(a.cx, a.cy, hx, hy)
		strokeOpen = false
		lastPan = null
	# Clamp so the map can never be lost off-screen entirely.
	camX = maxf(-w / z / 2, minf(cols + w / z / 2, camX))
	camY = maxf(-h / z / 2, minf(rows + h / z / 2, camY))

	ui.text("right-drag to pan · wheel to zoom · 1–9 pick ground · [ ] brush size · Ctrl+Z undo",
		x + 10, y + h - 10, {"size": 10.5, "color": "#6f6a5c"})
	ctx.restore()


var _canvasImg: Image = null
var _canvasTex: ImageTexture = null
var _canvasKey := ""
static var _stamps := {}


## The view's cells and resource nodes as a texture (see drawCanvas). The
## rects are the TS's — run by run along each row, z+1 wide so neighbours
## overlap by a pixel — and each node is the TS's arc, as an antialiased dot.
func _bakedCanvas(x: float, y: float, w: float, h: float, m: Dictionary, c0: int, c1: int, r0: int, r1: int) -> ImageTexture:
	var ds := UI.ui.ctx.device_scale()
	var iw := maxi(1, ceili(w * ds))
	var ih := maxi(1, ceili(h * ds))
	var key := "%d|%d|%s|%s|%s|%d|%d" % [iw, ih, var_to_str(camX), var_to_str(camY), var_to_str(zoom), hash(m.terrain), hash(m.resources)]
	if key == _canvasKey and _canvasTex != null:
		return _canvasTex
	if _canvasImg == null or _canvasImg.get_width() != iw or _canvasImg.get_height() != ih:
		_canvasImg = Image.create(iw, ih, false, Image.FORMAT_RGBA8)
		_canvasTex = null
	var img := _canvasImg
	img.fill(Color("#0b0906"))
	var z := zoom * ds
	var cols := int(m.cols)
	var terrain: PackedByteArray = m.terrain
	var colors := {}
	for t in SWATCH:
		colors[t] = Color(SWATCH[t])
	var fallback := Color("#6f9c4a")
	# Cell (cx, cy) spans [ox + cx·z, ox + cx·z + z + 1) in image pixels.
	var ox := w * ds / 2 - camX * z
	var oy := h * ds / 2 - camY * z
	for cy in range(r0, r1 + 1):
		var py0 := roundi(oy + cy * z)
		var py1 := roundi(oy + cy * z + z + ds)
		var cx := c0
		while cx <= c1:
			var t := terrain[cy * cols + cx]
			var run := 1
			while cx + run <= c1 and terrain[cy * cols + cx + run] == t:
				run += 1
			var px0 := roundi(ox + cx * z)
			var px1 := roundi(ox + cx * z + run * z + ds)
			img.fill_rect(Rect2i(px0, py0, px1 - px0, py1 - py0), colors.get(t, fallback))
			cx += run
	# Resources: the TS's arc at the cell's centre, radius max(1.6, z·0.42).
	var TILE := float(C.TILE)
	var rad := maxf(1.6, zoom * 0.42) * ds
	for r in m.resources:
		var cxp: float = ox + r.x / TILE * z + z / 2
		var cyp: float = oy + r.y / TILE * z + z / 2
		if cxp < -rad or cyp < -rad or cxp > iw + rad or cyp > ih + rad:
			continue
		var stamp := _stamp(RES_COLOUR.get(r.type, "#2f6b32"), rad, fposmod(cxp, 1.0), fposmod(cyp, 1.0))
		var half := stamp.get_width() / 2
		img.blend_rect(stamp, Rect2i(Vector2i.ZERO, stamp.get_size()), Vector2i(floori(cxp) - half, floori(cyp) - half))
	if _canvasTex == null:
		_canvasTex = ImageTexture.create_from_image(img)
	else:
		_canvasTex.update(img)
	_canvasKey = key
	return _canvasTex


## An antialiased dot of radius `r`, its centre at (fx, fy) within the
## middle pixel (quantised to quarters): what a canvas arc fill covers.
static func _stamp(color: String, r: float, fx: float, fy: float) -> Image:
	var qx := floori(fx * 4)
	var qy := floori(fy * 4)
	var key := "%s|%s|%d|%d" % [color, var_to_str(r), qx, qy]
	var img: Image = _stamps.get(key)
	if img != null:
		return img
	var half := ceili(r) + 1
	var n := half * 2 + 1
	img = Image.create(n, n, false, Image.FORMAT_RGBA8)
	var c := Color(color)
	var cx := half + (qx + 0.5) / 4.0
	var cy := half + (qy + 0.5) / 4.0
	for py in n:
		for px in n:
			# Coverage by 4×4 supersampling.
			var cov := 0
			for sy in 4:
				for sx in 4:
					var dx := px + (sx + 0.5) / 4.0 - cx
					var dy := py + (sy + 0.5) / 4.0 - cy
					if dx * dx + dy * dy <= r * r:
						cov += 1
			if cov > 0:
				img.set_pixel(px, py, Color(c.r, c.g, c.b, cov / 16.0))
	if _stamps.size() > 512:
		_stamps.clear()
	_stamps[key] = img
	return img


# ---- tool rail ----

func drawToolRail(x: float, y: float, w: float, h: float, m: Dictionary) -> void:
	var ui := UI.ui
	var ctx := ui.ctx
	var PAL: Dictionary = C.PAL
	ctx.fillStyle = "rgba(20,16,9,0.96)"; ctx.fillRect(x, y, w, h)
	ctx.fillStyle = Palette.withAlpha(PAL.uiAccent, 0.25); ctx.fillRect(x + w - 1, y, 1, h)
	ui.blockPointer(x, y, w, h)
	var ry := y + 14

	var tools := [
		["terrain", "Ground", "Paint terrain with the selected brush."],
		["path", "Path", "Drag a straight run — rivers, ridges and roads are lines, and a round brush makes a wobble."],
		["scatter", "Scatter", "Drag to sprinkle resources at a density, instead of placing each node by hand."],
		["region", "Region", "Drag to copy a rectangle, then click to stamp it. Build one base properly and repeat it at every seat."],
		["resource", "Resources", "Place trees, gold and berries one at a time."],
		["spawn", "Spawns", "Seat a player. Numbered in the order you place them."],
		["erase", "Erase", "Remove resources and spawn points under the brush."],
	]
	for i in tools.size():
		var id: String = tools[i][0]
		var label: String = tools[i][1]
		var desc: String = tools[i][2]
		var bx := x + 10 + (i % 2) * 98
		var by := ry + floori(i / 2.0) * 32
		var disabled: bool = id == "spawn" and m.nomad == "forced"
		if ui.button(label, bx, by, 92, 28, {
			"accent": tool == id, "size": 12, "disabled": disabled,
			"tooltip": ["Spawns disabled", "This map is set to always nomad."] if disabled else [label, desc],
		}):
			tool = id
	ry += ceili(tools.size() / 2.0) * 32 + 10

	if tool == "terrain" or tool == "path":
		ui.text("GROUND", x + 12, ry, {"size": 10, "bold": true, "color": "#8f8770"})
		ry += 10
		for i in PALETTE.size():
			var t: int = PALETTE[i]
			var bx := x + 10 + (i % 2) * 98
			var by := ry + floori(i / 2.0) * 30
			var def := TerrainKinds.terrainDef(t)
			var sel := paint == t
			ctx.fillStyle = Palette.withAlpha("#ffd24a", 0.22) if sel else ("rgba(255,255,255,0.07)" if ui.hit(bx, by, 92, 26) else "rgba(0,0,0,0.25)")
			ctx.beginPath(); ctx.roundRect(bx, by, 92, 26, 4); ctx.fill()
			if sel:
				ctx.strokeStyle = "#ffd24a"; ctx.lineWidth = 1.4; ctx.stroke()
			ctx.fillStyle = SWATCH.get(t, "#6f9c4a")
			ctx.beginPath(); ctx.roundRect(bx + 5, by + 5, 16, 16, 3); ctx.fill()
			ui.text(def.name, bx + 27, by + 14, {"size": 10.5, "color": "#ffe9b0" if sel else "#cabfa4"})
			if ui.hit(bx, by, 92, 26):
				ui.pointerConsumed = true
				ui.hoveredTooltip = {"text": [def.name, def.desc, str(i + 1)], "x": ui.mx, "y": by}
				if ui.clicked:
					paint = t
		ry += ceili(PALETTE.size() / 2.0) * 30 + 10
		if tool == "path":
			ui.text("Drag from one point to another.", x + 12, ry, {"size": 10.5, "color": "#9a917b"})
			ui.text("Brush size sets how wide the run is.", x + 12, ry + 14, {"size": 10.5, "color": "#9a917b"})
			ry += 34
	elif tool == "scatter":
		ui.text("SCATTER", x + 12, ry, {"size": 10, "bold": true, "color": "#8f8770"})
		ry += 12
		for k in ["tree", "gold_mine", "berries"]:
			if ui.button(RES_LABEL[k], x + 10, ry, 190, 28, {"accent": resource == k, "size": 12}):
				resource = k
			ctx.fillStyle = RES_COLOUR[k]
			ctx.beginPath(); ctx.arc(x + 24, ry + 14, 5, 0, PI * 2); ctx.fill()
			ry += 32
		ry += 6
		ui.text("DENSITY  %d%%" % JS.roundi(scatterDensity * 100), x + 12, ry, {"size": 10, "bold": true, "color": "#8f8770"})
		var dv := ui.slider(x + 12, ry + 20, w - 34, (scatterDensity - 0.05) / 0.75, ui.clicked or ui.leftHeld)
		scatterDensity = maxf(0.05, minf(0.8, 0.05 + dv * 0.75))
		ry += 40
		# Re-rolling matters: the sprinkle is a function of the cell, so without
		# this an author who dislikes a clump can only move somewhere else.
		if ui.button("Re-roll pattern", x + 10, ry, 190, 26, {"size": 12,
			"tooltip": ["Shuffle the sprinkle", "The pattern is fixed per cell so dragging back over ground doesn't thicken it. This picks a different one."]}):
			scatterSeed = JS.u32(scatterSeed * 1664525 + 1013904223)
			say("New scatter pattern")
		ry += 36
	elif tool == "region":
		ui.text("REGION", x + 12, ry, {"size": 10, "bold": true, "color": "#8f8770"})
		ry += 14
		if clip != null:
			ui.text("Holding %d × %d cells" % [clip.w, clip.h], x + 12, ry, {"size": 11, "color": "#e7ddc4"})
			ry += 16
			ui.text("Click the map to stamp it.", x + 12, ry, {"size": 10.5, "color": "#9a917b"})
			ry += 20
			if ui.button("Flip ↔ on" if clipFlipX else "Flip ↔ off", x + 10, ry, 92, 26,
				{"size": 11, "accent": clipFlipX}):
				clipFlipX = not clipFlipX
			if ui.button("Flip ↕ on" if clipFlipY else "Flip ↕ off", x + 108, ry, 92, 26,
				{"size": 11, "accent": clipFlipY}):
				clipFlipY = not clipFlipY
			ry += 32
			if ui.button("Drop it", x + 10, ry, 190, 26, {"size": 12}):
				clip = null
				say("Region dropped")
			ry += 36
		else:
			ui.text("Drag a rectangle to copy the", x + 12, ry, {"size": 10.5, "color": "#9a917b"})
			ui.text("ground and everything on it.", x + 12, ry + 14, {"size": 10.5, "color": "#9a917b"})
			ry += 40
	elif tool == "resource":
		ui.text("RESOURCE", x + 12, ry, {"size": 10, "bold": true, "color": "#8f8770"})
		ry += 12
		for k in ["tree", "gold_mine", "berries"]:
			if ui.button(RES_LABEL[k], x + 10, ry, 190, 28, {"accent": resource == k, "size": 12}):
				resource = k
			ctx.fillStyle = RES_COLOUR[k]
			ctx.beginPath(); ctx.arc(x + 24, ry + 14, 5, 0, PI * 2); ctx.fill()
			ry += 32
		ry += 6
	elif tool == "spawn":
		ui.text("SPAWNS  %d / 8" % m.spawns.size(), x + 12, ry, {"size": 10, "bold": true, "color": "#8f8770"})
		ry += 14
		ui.text("Click the map to seat a player.", x + 12, ry, {"size": 10.5, "color": "#9a917b"})
		ry += 14
		ui.text("Symmetry seats them all at once.", x + 12, ry, {"size": 10.5, "color": "#9a917b"})
		ry += 20
		if ui.button("Clear all spawns", x + 10, ry, 190, 26, {"size": 12, "disabled": m.spawns.is_empty()}):
			undo.append(snapshot())
			m.spawns = []
			dirty = true
		ry += 36
	else:
		ui.text("Drag over resources or spawn", x + 12, ry, {"size": 10.5, "color": "#9a917b"})
		ui.text("points to remove them.", x + 12, ry + 14, {"size": 10.5, "color": "#9a917b"})
		ry += 40

	# Brush size applies to ground and erase.
	if tool == "terrain" or tool == "path" or tool == "scatter" or tool == "erase" or tool == "resource":
		ui.text("BRUSH  %d" % brush, x + 12, ry, {"size": 10, "bold": true, "color": "#8f8770"})
		var nv := ui.slider(x + 12, ry + 20, w - 34, (brush - 1) / 23.0, ui.clicked or ui.leftHeld)
		brush = maxi(1, JS.roundi(1 + nv * 23))
		ry += 40

	ui.text("SYMMETRY", x + 12, ry, {"size": 10, "bold": true, "color": "#8f8770"})
	ry += 10
	var SYMMETRIES: Array = C.SYMMETRIES
	for i in SYMMETRIES.size():
		var s: Dictionary = SYMMETRIES[i]
		var bx := x + 10 + (i % 2) * 98
		var by := ry + floori(i / 2.0) * 28
		if ui.button(s.label, bx, by, 92, 24, {"accent": sym == s.id, "size": 10.5, "tooltip": [s.label, s.desc]}):
			sym = s.id


# ---- properties + validation ----

func drawProperties(x: float, y: float, w: float, h: float, m: Dictionary, time: float):
	var ui := UI.ui
	var ctx := ui.ctx
	var PAL: Dictionary = C.PAL
	var MAX_SEATS := int(C.MAX_SEATS)
	ctx.fillStyle = "rgba(20,16,9,0.96)"; ctx.fillRect(x, y, w, h)
	ctx.fillStyle = Palette.withAlpha(PAL.uiAccent, 0.25); ctx.fillRect(x, y, 1, h)
	ui.blockPointer(x, y, w, h)
	var ry := y + 18
	var lx := x + 14

	ui.text("MAP", lx, ry, {"size": 10, "bold": true, "color": "#8f8770"})
	ry += 14
	if true:
		var sz = null
		for z in C.MAP_SIZES:
			if int(z.cols) == int(m.cols):
				sz = z
				break
		ui.text("%s%d × %d cells" % [(sz.label + " · ") if sz != null else "", int(m.cols), int(m.rows)], lx, ry, {"size": 11.5, "color": "#cabfa4"})
		ry += 20

	# What the map is *for*. A line here is the difference between a map people
	# scroll past in the lobby and one they pick.
	ui.text("DESCRIPTION", lx, ry, {"size": 10, "bold": true, "color": "#8f8770"})
	ry += 8
	if true:
		var dh := 46
		var dhov := ui.hit(lx, ry, w - 28, dh)
		ctx.fillStyle = "rgba(60,50,20,0.7)" if editing == "desc" else ("rgba(255,255,255,0.07)" if dhov else "rgba(0,0,0,0.28)")
		ctx.beginPath(); ctx.roundRect(lx, ry, w - 28, dh, 4); ctx.fill()
		ctx.strokeStyle = "#ffd24a" if editing == "desc" else "rgba(255,255,255,0.1)"
		ctx.lineWidth = 1; ctx.stroke()
		var desc: String = m.get("desc", "")
		var shown := desc if desc != "" else ("" if editing == "desc" else "click to describe this map")
		# Wrapped by hand at a character count rather than measured: the rail is a
		# fixed width and the font is fixed, so this is exact enough and costs
		# nothing per frame.
		var lines: Array = []
		for word in shown.split(" "):
			if lines.is_empty() or (lines[lines.size() - 1] + " " + word).length() > 34:
				lines.append(word)
			else:
				lines[lines.size() - 1] += " " + word
		var n := mini(lines.size(), 3)
		for i in n:
			var cursor: bool = editing == "desc" and i == mini(lines.size(), 3) - 1 and floori(time * 2) % 2 == 1
			ui.text(lines[i] + ("_" if cursor else ""), lx + 7, ry + 15 + i * 13,
				{"size": 10.5, "color": "#e7ddc4" if desc != "" else "#6f6a5c"})
		if dhov and ui.clicked:
			editing = null if editing == "desc" else "desc"
			ui.pointerConsumed = true
		ry += dh + 14

	# Biome — changing it repaints only the untouched base ground, so the work
	# you have already done survives a change of mind about the setting.
	ui.text("BIOME", lx, ry, {"size": 10, "bold": true, "color": "#8f8770"})
	ry += 10
	var BIOMES: Array = C.BIOMES
	for i in BIOMES.size():
		var b: Dictionary = BIOMES[i]
		var bx := lx + (i % 2) * 138
		var by := ry + floori(i / 2.0) * 28
		if ui.button(b.name, bx, by, 132, 24, {"accent": m.biome == b.id, "size": 11, "tooltip": [b.name, b.desc]}):
			m.biome = b.id
			dirty = true
	ry += 64

	ui.text("GAME MODES", lx, ry, {"size": 10, "bold": true, "color": "#8f8770"})
	ui.text("" if not m.modes.is_empty() else "any", x + w - 14, ry, {"align": "right", "size": 10, "color": "#6f6a5c"})
	ry += 10
	for i in MODES.size():
		var md: Dictionary = MODES[i]
		var bx := lx + (i % 2) * 138
		var by := ry + floori(i / 2.0) * 28
		var on: bool = m.modes.has(md.id)
		if ui.button(md.label, bx, by, 132, 24, {"accent": on, "size": 10.5,
			"tooltip": [md.label, "Click to remove" if on else "Click to allow this mode"]}):
			if on:
				m.modes = m.modes.filter(func(z): return z != md.id)
			else:
				var nm: Array = m.modes.duplicate()
				nm.append(md.id)
				m.modes = nm
			dirty = true
	ry += 62
	ui.text("None selected means any mode.", lx, ry, {"size": 10, "color": "#6f6a5c"})
	ry += 18

	ui.text("PLAYERS", lx, ry, {"size": 10, "bold": true, "color": "#8f8770"})
	ry += 12
	# Nothing here is derived from the map's size — a Duel map may seat eight
	# if that is the fight you want, and a Colossal one may seat two.
	var v = _stepper("Minimum", int(m.minPlayers), lx, ry, MAX_SEATS)
	if v != null:
		m.minPlayers = v
		if int(m.maxPlayers) < v:
			m.maxPlayers = v
		dirty = true
	ry += 30
	v = _stepper("Maximum", int(m.maxPlayers), lx, ry, MAX_SEATS)
	if v != null:
		m.maxPlayers = v
		if int(m.minPlayers) > v:
			m.minPlayers = v
		dirty = true
	ry += 30
	if ui.button("Match spawns", lx, ry, 110, 24, {
		"size": 11, "disabled": m.spawns.is_empty() or m.nomad == "forced",
		"tooltip": ["Match the seats to the map", "Set the maximum to the %d spawn point%s you have placed." % [m.spawns.size(), "" if m.spawns.size() == 1 else "s"]],
	}):
		m.maxPlayers = maxi(1, m.spawns.size())
		if int(m.minPlayers) > int(m.maxPlayers):
			m.minPlayers = m.maxPlayers
		dirty = true
	if ui.button("Any", lx + 118, ry, 58, 24, {"size": 11,
		"tooltip": ["Open it up", "One to %d players, any mode." % MAX_SEATS]}):
		m.minPlayers = 1
		m.maxPlayers = MAX_SEATS
		m.modes = []
		dirty = true
	ry += 30
	ui.text("The engine seats %d realms at most." % MAX_SEATS, lx, ry, {"size": 10, "color": "#6f6a5c"})
	ry += 22

	ui.text("NOMAD", lx, ry, {"size": 10, "bold": true, "color": "#8f8770"})
	ry += 10
	var NOMAD_RULES: Array = C.NOMAD_RULES
	for i in NOMAD_RULES.size():
		var n: Dictionary = NOMAD_RULES[i]
		var bx := lx + (i % 3) * 92
		if ui.button(n.label, bx, ry, 86, 24, {"accent": m.nomad == n.id, "size": 10.5, "tooltip": [n.label, n.desc]}):
			m.nomad = n.id
			# Turning nomad on for good takes the spawn tool away with it, so the
			# author is never painting seats that will be ignored.
			if n.id == "forced" and tool == "spawn":
				tool = "terrain"
			dirty = true
	ry += 32
	if m.nomad == "forced":
		ui.text("Spawn points are disabled on this map.", lx, ry, {"size": 10, "color": "#e0a878"})
		ry += 16
	ry += 6

	# ---- validation ----
	var errs := issues.filter(func(i): return i.level == "error")
	var warns := issues.filter(func(i): return i.level == "warning")
	ctx.strokeStyle = "rgba(255,255,255,0.08)"; ctx.lineWidth = 1
	ctx.beginPath(); ctx.moveTo(lx, ry); ctx.lineTo(x + w - 14, ry); ctx.stroke()
	ry += 16
	if issues.is_empty():
		ui.text("✔ Ready to play", lx, ry, {"size": 12.5, "bold": true, "color": "#7df2a9"})
		ry += 20
	else:
		ui.text("%d error%s · %d warning%s" % [errs.size(), "" if errs.size() == 1 else "s", warns.size(), "" if warns.size() == 1 else "s"],
			lx, ry, {"size": 11.5, "bold": true, "color": "#e0564a" if errs.size() else "#e0a878"})
		ry += 16
	for issue in errs + warns:
		if ry > y + h - 20:
			ui.text("…", lx, ry, {"size": 11, "color": "#6f6a5c"})
			break
		var col := "#e0564a" if issue.level == "error" else "#e0a878"
		ctx.fillStyle = col
		ctx.beginPath(); ctx.arc(lx + 3, ry - 3, 2.6, 0, PI * 2); ctx.fill()
		for line in wrapText(issue.text, 36):
			ui.text(line, lx + 12, ry, {"size": 10.5, "color": "#f0b0a6" if issue.level == "error" else "#d8c39a"})
			ry += 13
		ry += 5
	return null


## One "− value +" row of the players stepper. Returns the new value when a
## button was pressed, or null. (The TS closure assigns the caller's `ry`; a
## GDScript lambda can't, so the caller advances it.)
func _stepper(label: String, value: int, lx: float, ry: float, maxSeats: int):
	var ui := UI.ui
	ui.text(label, lx, ry + 13, {"size": 11.5, "color": "#cabfa4"})
	var out = null
	if ui.button("−", lx + 96, ry, 26, 24, {"size": 13, "disabled": value <= 1}):
		out = value - 1
	ui.text(str(value), lx + 136, ry + 13, {"align": "center", "size": 13, "bold": true, "color": "#f2e8d0"})
	if ui.button("+", lx + 150, ry, 26, 24, {"size": 13, "disabled": value >= maxSeats}):
		out = value + 1
	return out


## Greedy wrap to a character budget — the rail is narrow and text must fit.
static func wrapText(text: String, per: int) -> Array:
	var words := text.split(" ")
	var out: Array = []
	var line := ""
	for w in words:
		if line != "" and (line + " " + w).length() > per:
			out.append(line)
			line = w
		else:
			line = ("%s %s" % [line, w]) if line != "" else w
	if line != "":
		out.append(line)
	return out
