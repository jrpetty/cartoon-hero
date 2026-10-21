## Rebindable hotkeys.
##
## Port of game/src/meta/keybinds.ts. The action table is data: `C.ACTIONS`
## (ActionDef dictionaries: id, label, group, def, and optional alt, hint),
## `C.ACTION_BY_ID`, `C.KEYBIND_GROUPS`.
##
## The defaults follow Age of Empires conventions wherever the game has an
## equivalent, because that is the muscle memory most RTS players arrive with:
##
##   .  /  ,        next / previous idle villager
##   H              select the Town Centre and go there
##   Space          jump to the last thing that happened to you
##   Ctrl+1..9      assign a control group   (1..9 selects, twice re-centres)
##   Shift+1..9     add the selection to a group
##   Ctrl+A         select every soldier you own
##   F3             pause
##   Del            delete the selection
##
## Everything here is data: an action has an id, a label, a group and a default
## chord. Bindings live in Settings, so a rebind survives a reload, and any
## action can be cleared or moved without touching the code that fires it.
##
## Keys are named as the browser's KeyboardEvent.key names them ("a", "Tab",
## "ArrowUp", " "); GameInput.domKey turns a Godot key event into that name.
## A binding map (TS `Keybinds`) is a Dictionary ActionId → chord; missing
## entries fall back to the action's default, and "" means unbound.
class_name Keybinds
extends RefCounted


## Canonical name for a key. Single characters upper-case, so "a" and "A" are
## the same binding; everything else keeps its DOM name ("Tab", "F3", "Home").
## The two shifted keys people actually use for speed are folded onto their
## unshifted twins, so "+" works whether or not you reach it with Shift.
static func keyName(key: String) -> String:
	if key == " ":
		return "Space"
	if key == "=":
		return "+"
	if key == "_":
		return "-"
	if key.length() == 1:
		return key.to_upper()
	return key


## Canonical chord: modifiers in a fixed order, then the key. `mods` may hold
## ctrl / shift / alt booleans.
static func chordOf(key: String, mods: Dictionary = {}) -> String:
	var name := keyName(key)
	# A modifier pressed on its own is not a chord.
	if name == "Control" or name == "Shift" or name == "Alt" or name == "Meta":
		return ""
	# A printable symbol already *is* the shifted key: the browser reports "+"
	# for Shift+=, so counting Shift again would produce "Shift++" and never
	# match the "+" the player bound. Letters and digits keep their modifier,
	# because Shift+H and H are genuinely different chords.
	var symbol := name.length() == 1 and not _isUpperAlnum(name)
	var out := ""
	if mods.get("ctrl", false):
		out += "Ctrl+"
	if mods.get("shift", false) and not symbol:
		out += "Shift+"
	if mods.get("alt", false):
		out += "Alt+"
	return out + name


# /[A-Z0-9]/.test(name) for a one-character name.
static func _isUpperAlnum(c: String) -> bool:
	var u := c.unicode_at(0)
	return (u >= 65 and u <= 90) or (u >= 48 and u <= 57)


## Human-readable chord for the settings list and the controls reference.
static func chordLabel(chord: String) -> String:
	if chord == "":
		return "—"
	# String.replace in JS replaces the first occurrence only.
	var s := _replaceFirst(chord, "Delete", "Del")
	s = _replaceFirst(s, "ArrowUp", "↑")
	s = _replaceFirst(s, "ArrowDown", "↓")
	s = _replaceFirst(s, "ArrowLeft", "←")
	s = _replaceFirst(s, "ArrowRight", "→")
	return s


static func _replaceFirst(s: String, what: String, with: String) -> String:
	var i := s.find(what)
	return s if i < 0 else s.substr(0, i) + with + s.substr(i + what.length())


## The chord currently bound to an action.
static func chordFor(binds: Dictionary, id: String) -> String:
	var bound = binds.get(id)
	if bound is String:
		return bound
	var a = C.ACTION_BY_ID.get(id)
	return a.def if a != null else ""


## Which action a chord fires, or null. Built fresh only when the bindings
## change — resolving is on the keyboard path and runs for every keypress.
class KeybindResolver:
	var map: Dictionary = {}
	var signature := ""

	## Rebuild if the bindings changed since last time, then resolve.
	## Returns the ActionId, or null.
	func resolve(binds: Dictionary, chord: String):
		var sig := JSON.stringify(binds, "", false)
		if sig != signature:
			rebuild(binds)
			signature = sig
		return map.get(chord)

	func rebuild(binds: Dictionary) -> void:
		map.clear()
		for a in C.ACTIONS:
			var chord := Keybinds.chordFor(binds, a.id)
			# First writer wins, so a conflicting rebind can never shadow silently:
			# conflicts are surfaced in the settings screen instead.
			if chord != "" and not map.has(chord):
				map[chord] = a.id
			var alt = a.get("alt")
			if alt and not map.has(alt):
				map[alt] = a.id


## Actions bound to the same chord, as chord → Array of ActionIds. Two
## commands on one key is legal — the pan keys deliberately share W/A/S/D with
## Stop and Attack-move, because panning is held rather than tapped — so this
## only reports clashes the player made.
static func conflictsOf(binds: Dictionary) -> Dictionary:
	var byChord := {}
	for a in C.ACTIONS:
		if a.group == "Camera" and String(a.id).begins_with("pan"):
			continue # held, not tapped
		var chord := chordFor(binds, a.id)
		if chord == "":
			continue
		if not byChord.has(chord):
			byChord[chord] = []
		byChord[chord].append(a.id)
	for chord in byChord.keys():
		if byChord[chord].size() < 2:
			byChord.erase(chord)
	return byChord


## Chords the game must keep for itself, whatever the player types.
const RESERVED := {"F5": true, "F11": true, "F12": true, "Ctrl+R": true, "Ctrl+W": true, "Ctrl+T": true, "Ctrl+N": true}


static func isReserved(chord: String) -> bool:
	return RESERVED.has(chord)
