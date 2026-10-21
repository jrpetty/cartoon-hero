extends TestCase
# Port of game/src/meta/settings.test.ts, the settings-persistence part. (The
# file's colorblind-palette and particle-thinning tests belong to the render
# and engine ports.)

const H := preload("res://tests/meta_helpers.gd")


func _init() -> void:
	Storage.use_memory_only()


func test_returns_defaults_when_nothing_is_stored() -> void:
	check(H.deep_eq(Settings.loadSettings(), Settings.DEFAULT_SETTINGS), "defaults")


func test_round_trips_saved_settings() -> void:
	var s := H.with(Settings.DEFAULT_SETTINGS, {"musicVol": 0.1, "colorblind": true, "scrollSpeed": 700})
	Settings.saveSettings(s)
	check(H.deep_eq(Settings.loadSettings(), s), "round trip: %s" % str(Settings.loadSettings()))


func test_merges_partial_older_stored_settings_over_current_defaults() -> void:
	Storage.setItem("bb_settings", LocalJSON.stringify({"muted": true}))  # missing newer keys
	var loaded := Settings.loadSettings()
	eq(loaded.muted, true)
	eq(loaded.masterVol, Settings.DEFAULT_SETTINGS.masterVol, "filled from defaults")
	eq(loaded.colorblind, Settings.DEFAULT_SETTINGS.colorblind)


func test_survives_a_corrupt_keybind_map_and_clamps_the_ui_scale() -> void:
	Storage.setItem("bb_settings", '{"keybinds":"oops","uiScale":9,"sfxVol":0.2}')
	var loaded := Settings.loadSettings()
	check(H.deep_eq(loaded.keybinds, {}), "keybinds reset")
	eq(loaded.uiScale, 1.5, "clamped")
	eq(loaded.sfxVol, 0.2)
	Storage.setItem("bb_settings", '{"uiScale":"big"}')
	eq(Settings.loadSettings().uiScale, 1, "NaN -> 1")
	Storage.setItem("bb_settings", "{not json")
	check(H.deep_eq(Settings.loadSettings(), Settings.DEFAULT_SETTINGS), "bad json -> defaults")
