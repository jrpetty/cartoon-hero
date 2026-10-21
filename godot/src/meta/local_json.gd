## JSON.stringify / JSON.parse as the browser does them, for what the meta
## modules keep in Storage (the localStorage stand-in).
##
## Saved data has to move between the TypeScript and Godot builds, so it is
## written the way JS writes it: whole numbers without a ".0", every other
## number with enough digits to read back the same double, keys in insertion
## order (Godot sorts them by default, which would change the order a reload
## iterates in), NaN/Infinity as null.
class_name LocalJSON


## JSON.stringify(v).
static func stringify(v) -> String:
	return JSON.stringify(_js_values(v), "", false, true)


## JSON.parse(text), or null when the text isn't JSON (where TS would throw;
## every caller catches that and falls back the same way it does for null).
static func parse(text):
	if not (text is String):
		return null
	var j := JSON.new()
	if j.parse(text) != OK:
		return null
	return j.data


static func _js_values(v):
	if v is float:
		if is_nan(v) or is_inf(v):
			return null
		if v == floor(v) and absf(v) <= JS.MAX_SAFE_INTEGER:
			return int(v)
		return v
	if v is Dictionary:
		var o := {}
		for k in v:
			o[str(k)] = _js_values(v[k])
		return o
	if v is Array:
		var a := []
		for x in v:
			a.append(_js_values(x))
		return a
	return v
