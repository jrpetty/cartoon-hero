## Shared bits for the screen tests (not a test file itself: the runner only
## picks up test_*.gd). The TS tests swap `ui.text` / `ui.button` for
## recording wrappers; GDScript can't patch a method, so the shared `UI.ui` is
## replaced with a subclass that records, and put back afterwards.
extends RefCounted


## A UI that remembers every string and button it drew this frame, with the
## y the canvas transform put them at (scroll included), as the TS tests read
## `ui.ctx.getTransform().f + y`.
class RecUI extends UI:
	var texts: Array = []  ## of { s, x, y }
	var buttons: Array = []  ## of { label, x, y, w, h, opts }

	func text(s: String, x: float, y: float, opts: Dictionary = {}) -> void:
		var f := ctx.getTransform()
		texts.append({"s": s, "x": f.origin.x + x * f.x.x, "y": f.origin.y + y * f.y.y})
		super.text(s, x, y, opts)

	func button(label: String, x: float, y: float, w: float, h: float, opts: Dictionary = {}) -> bool:
		var f := ctx.getTransform()
		buttons.append({"label": label, "x": f.origin.x + x * f.x.x, "y": f.origin.y + y * f.y.y, "w": w * f.x.x, "h": h * f.y.y, "opts": opts})
		return super.button(label, x, y, w, h, opts)

	func clear() -> void:
		texts.clear()
		buttons.clear()

	## The first recorded string equal to `s`, or null.
	func find_text(s: String):
		for t in texts:
			if t.s == s:
				return t
		return null

	## The first recorded button whose label (or tooltip title) is `label`.
	func find_button(label: String):
		for b in buttons:
			if b.label == label:
				return b
			var tip = b.opts.get("tooltip")
			if tip is Array and not tip.is_empty() and tip[0] == label and b.label == "":
				return b
		return null


static func install() -> RecUI:
	var r := RecUI.new()
	UI._shared = r
	return r


static func uninstall() -> void:
	UI._shared = null


## A context that measures but draws nothing — the screens' logic, layout and
## text metrics without a renderer.
static func ctx(w: int = 1600, h: int = 900) -> Ctx:
	var c := Ctx.new()
	c.measure_only = true
	c.canvas.width = w
	c.canvas.height = h
	return c


## A frame's input, the TS `UIFrameInput`.
static func input(mx: float = -99, my: float = -99, clicked: bool = false, wheel: float = 0.0, extra: Dictionary = {}) -> Dictionary:
	var d := {"mx": mx, "my": my, "clicked": clicked, "rightClicked": false, "alt": false,
		"leftHeld": false, "rightHeld": false, "ctrlHeld": false, "shiftHeld": false, "wheel": wheel}
	d.merge(extra, true)
	return d


## Begin a frame on the shared UI.
static func begin(c: Ctx, mx: float = -99, my: float = -99, clicked: bool = false, wheel: float = 0.0) -> void:
	UI.ui.begin(c, input(mx, my, clicked, wheel))


## A fresh in-memory store, with every meta module reading the Storage autoload.
static func fresh_store() -> void:
	Storage.use_memory_only()
	Career.storage_override = null
	History.storage_override = null
	Rival.storage_override = null
	World.nextId = 1
	audio.muted = true


## A career match (meta/career.ts CareerMatch) with plausible numbers, as the
## TS tests' `m()` builds it.
static var _t0 := 1_790_000_000_000.0


static func career_match(over: Dictionary = {}) -> Dictionary:
	_t0 += 60_000.0
	var foe := {"gathered": 12000.0, "razed": 3.0, "kills": 40.0, "losses": 40.0, "peakArmy": 30.0, "peakVillagers": 38.0, "villagerKills": 4.0, "upgrades": 6.0, "defenses": 2.0, "age": 2.0}
	var m := {
		"at": _t0, "kind": "skirmish", "ranked": false, "mode": "conquest", "difficulty": "knight", "won": true,
		"faction": "legion", "commander": "marshal", "oaths": [], "map": "Highlands", "players": 2.0, "format": "1 v 1", "allies": [], "foes": ["norse"],
		"durationSec": 900.0, "defeatedAt": -1.0, "age": 2.0, "ageTimes": [0.0, 300.0, 600.0], "score": 5000.0, "gathered": 8000.0,
		"gatheredBy": {"food": 4000.0, "wood": 3000.0, "gold": 1000.0}, "spent": 7000.0, "kills": 40.0, "losses": 20.0, "razed": 5.0, "buildingsLost": 1.0,
		"damageDealt": 9000.0, "damageTaken": 5000.0, "peakArmy": 30.0, "peakVillagers": 40.0, "idleVillagerTime": 60.0, "tcIdleShare": 0.1, "upgrades": 6.0,
		"trained": {"villager": 40.0, "legionary": 20.0, "archer": 5.0}, "killed": {"militia": 30.0, "archer": 10.0}, "lost": {"legionary": 15.0, "archer": 5.0},
		"built": {"house": 8.0, "barracks": 2.0}, "firstHitAt": 600.0, "spentOn": {"units": 6000.0, "buildings": 3000.0, "tech": 2000.0}, "foe": foe,
	}
	m.merge(over, true)
	return m
