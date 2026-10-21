## Player-wide options, persisted to localStorage independently of the match
## profile. Loaded once at boot and applied through App.applySettings().
##
## Settings: { masterVol, sfxVol, musicVol, muted, damageNumbers, edgeScroll,
##   scrollSpeed (base camera pan speed, px/s at zoom 1),
##   reduceEffects (thin out particles on weaker machines / big 8v8s),
##   colorblind (high-contrast team palette),
##   weather (atmospheric rain/snow/overcast overlay),
##   showFps (live FPS counter overlay),
##   perfOverlay (frame/tick/entity readout, for diagnosing a chugging match),
##   autoLod (drop detail on its own when frames start slipping),
##   uiScale (0.8..1.5 — interface only; the world keeps its apparent size),
##   keybinds (overrides; anything missing uses the action's default) }
## The defaults are data: C.DEFAULT_SETTINGS.
class_name Settings

const KEY := "bb_settings"

static var DEFAULT_SETTINGS: Dictionary:
	get:
		return C.DEFAULT_SETTINGS


static func loadSettings() -> Dictionary:
	var raw = Storage.getItem(KEY)
	if raw:
		var saved = LocalJSON.parse(raw)
		# JSON.parse throwing, or `saved.keybinds` on a null, lands in the TS
		# catch: defaults.
		if saved != null:
			# Merge rather than replace: a settings file written before an option
			# existed must still get that option's default, and a corrupt keybind
			# map must not take the whole hotkey system down with it.
			var out: Dictionary = C.DEFAULT_SETTINGS.duplicate()
			if saved is Dictionary:
				for k in saved:
					out[k] = saved[k]
			var kb = saved.get("keybinds") if saved is Dictionary else null
			out["keybinds"] = kb if (kb is Dictionary or kb is Array) else {}
			var ui := _number(saved.get("uiScale")) if saved is Dictionary and saved.has("uiScale") else NAN  # Number(undefined) is NaN
			out["uiScale"] = maxf(0.8, minf(1.5, 1.0 if (is_nan(ui) or ui == 0.0) else ui))
			return out
	return C.DEFAULT_SETTINGS.duplicate()


static func saveSettings(s: Dictionary) -> void:
	Storage.setItem(KEY, LocalJSON.stringify(s))


## Number(v), for what JSON can hold.
static func _number(v) -> float:
	if v == null:
		return 0.0
	if v is bool:
		return 1.0 if v else 0.0
	if v is float or v is int:
		return float(v)
	if v is String:
		var s: String = v.strip_edges()
		if s == "":
			return 0.0
		return float(s) if s.is_valid_float() else NAN
	return NAN
