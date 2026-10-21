extends TestCase
# Port of game/src/meta/keybinds.test.ts.


# ---- Chords ----

func test_folds_a_key_to_one_canonical_name_whatever_case_it_arrives_in() -> void:
	eq(Keybinds.keyName("a"), "A")
	eq(Keybinds.keyName("A"), "A")
	eq(Keybinds.chordOf("a"), Keybinds.chordOf("A"))
	# Named keys keep their DOM spelling.
	eq(Keybinds.keyName("Tab"), "Tab")
	eq(Keybinds.keyName("ArrowUp"), "ArrowUp")
	eq(Keybinds.keyName("F10"), "F10")


func test_gives_space_and_the_shifted_speed_keys_a_stable_name() -> void:
	eq(Keybinds.keyName(" "), "Space")
	eq(Keybinds.keyName("="), "+")
	eq(Keybinds.keyName("_"), "-")


func test_orders_modifiers_so_the_same_combination_is_always_the_same_string() -> void:
	eq(Keybinds.chordOf("a", {"ctrl": true}), "Ctrl+A")
	eq(Keybinds.chordOf("a", {"shift": true, "ctrl": true}), "Ctrl+Shift+A")
	eq(Keybinds.chordOf("1", {"shift": true}), "Shift+1")


func test_is_not_a_chord_when_only_a_modifier_is_down() -> void:
	eq(Keybinds.chordOf("Control", {"ctrl": true}), "")
	eq(Keybinds.chordOf("Shift", {"shift": true}), "")
	eq(Keybinds.chordOf("Alt", {"alt": true}), "")


func test_prints_arrows_and_delete_in_a_form_that_fits_a_button() -> void:
	eq(Keybinds.chordLabel("ArrowUp"), "↑")
	eq(Keybinds.chordLabel("Delete"), "Del")
	eq(Keybinds.chordLabel(""), "—")
	eq(Keybinds.chordLabel("Ctrl+ArrowLeft"), "Ctrl+←")


# ---- Bindings ----

func test_falls_back_to_an_actions_default_until_it_is_overridden() -> void:
	eq(Keybinds.chordFor({}, "attackMove"), "A")
	eq(Keybinds.chordFor({"attackMove": "Ctrl+Q"}, "attackMove"), "Ctrl+Q")
	# An explicit empty string is "unbound", not "use the default".
	eq(Keybinds.chordFor({"attackMove": ""}, "attackMove"), "")


func test_resolves_a_chord_to_its_action_and_rebuilds_when_a_binding_changes() -> void:
	var r := Keybinds.KeybindResolver.new()
	eq(r.resolve({}, "A"), "attackMove")
	eq(r.resolve({}, "Ctrl+A"), "selectArmy")
	eq(r.resolve({}, "Z"), null)
	# Rebind and ask again — the cached map has to notice.
	eq(r.resolve({"attackMove": "Z"}, "Z"), "attackMove")
	eq(r.resolve({"attackMove": "Z"}, "A"), null)


func test_keeps_an_unbound_action_from_swallowing_every_unrecognised_key() -> void:
	var r := Keybinds.KeybindResolver.new()
	eq(r.resolve({"stop": ""}, ""), null)
	eq(r.resolve({"stop": ""}, "S"), null)


func test_honours_a_second_default_that_isnt_shown_in_the_list() -> void:
	var r := Keybinds.KeybindResolver.new()
	eq(r.resolve({}, "F3"), "pause")
	eq(r.resolve({}, "P"), "pause") # the alt


func test_ships_defaults_with_no_conflicts_of_its_own() -> void:
	eq(Keybinds.conflictsOf({}).keys(), [])


func test_reports_a_conflict_the_player_creates_naming_both_actions() -> void:
	var c := Keybinds.conflictsOf({"stop": "A"}) # A is already Attack-move
	eq(c.keys(), ["A"])
	var ids: Array = c["A"].duplicate()
	ids.sort()
	eq(ids, ["attackMove", "stop"])


func test_does_not_count_an_unbound_action_as_conflicting_with_another() -> void:
	eq(Keybinds.conflictsOf({"stop": "", "hold": ""}).size(), 0)


func test_refuses_the_chords_the_browser_needs_for_itself() -> void:
	eq(Keybinds.isReserved("F5"), true)
	eq(Keybinds.isReserved("Ctrl+W"), true)
	eq(Keybinds.isReserved("A"), false)


func test_follows_age_of_empires_where_the_game_has_an_equivalent() -> void:
	var byId := {}
	for a in C.ACTIONS:
		byId[a.id] = a.def
	eq(byId.idleVillagerNext, ".")
	eq(byId.idleVillagerPrev, ",")
	eq(byId.selectTownCentre, "H")
	eq(byId.cameraLastEvent, "Space")
	eq(byId.selectArmy, "Ctrl+A")
	eq(byId.deleteUnit, "Delete")
	eq(byId.pause, "F3")


func test_never_binds_a_pan_direction_to_a_key_that_also_issues_an_order() -> void:
	var pans: Array = C.ACTIONS.filter(func(a): return String(a.id).begins_with("pan")).map(func(a): return a.def)
	var commands: Array = C.ACTIONS.filter(func(a): return a.group == "Units" or a.group == "Selection").map(func(a): return a.def)
	for p in pans:
		check(not commands.has(p), "%s is also a command" % p)


# ---- Shifted symbols ----

func test_binds_the_symbol_the_browser_reports_not_the_key_you_reached_for() -> void:
	eq(Keybinds.chordOf("+", {"shift": true}), "+")
	eq(Keybinds.chordOf("=", {"shift": true}), "+")
	eq(Keybinds.chordOf(".", {"shift": true}), ".")
	var r := Keybinds.KeybindResolver.new()
	eq(r.resolve({}, Keybinds.chordOf("+", {"shift": true})), "speedUp")
	eq(r.resolve({}, Keybinds.chordOf("=", {})), "speedUp")


func test_keeps_shift_meaningful_on_letters_and_digits() -> void:
	eq(Keybinds.chordOf("h", {"shift": true}), "Shift+H")
	eq(Keybinds.chordOf("1", {"shift": true}), "Shift+1")
	var r := Keybinds.KeybindResolver.new()
	eq(r.resolve({}, "Shift+H"), "hold")
	eq(r.resolve({}, "H"), "selectTownCentre")
