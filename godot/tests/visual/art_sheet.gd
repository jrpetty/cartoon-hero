## The art contact sheets, drawn by the port. Replays one sheet of
## tests/visual/out/sheet_spec.json — written by game/scripts/godot-visual-
## sheet.mjs alongside the same sheet drawn by the TypeScript — through Draw:
##
##   xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
##     --path godot res://tests/visual/art_sheet.tscn -- units_kingdom tests/visual/out/godot_units_kingdom.png
##
## Sheets: units_kingdom, units_factions, buildings, misc. Add `--cache` before
## the sheet name to draw through the art cache instead of procedurally.
extends "res://tests/visual/sheet_base.gd"

var spec: Dictionary = {}
var use_cache := false


func setup(args: PackedStringArray) -> void:
	var name := "units_kingdom"
	for a in args:
		if a == "--cache":
			use_cache = true
		elif not a.ends_with(".png"):
			name = a
	var all = JSON.parse_string(FileAccess.get_file_as_string("res://tests/visual/out/sheet_spec.json"))
	for s in all:
		if s.name == name:
			spec = s
	if spec.is_empty():
		push_error("no sheet " + name)
		get_tree().quit(1)
		return
	sheet_size = Vector2i(int(spec.W), int(spec.H))
	ArtCache.enabled = use_cache


func paint(c: Ctx) -> void:
	c.fillStyle = spec.bg
	c.fillRect(0, 0, sheet_size.x, sheet_size.y)
	for o in spec.ops:
		replay(c, o)


static func entity_from(d: Dictionary) -> Entity:
	var e := Entity.new()
	for k in d:
		var v = d[k]
		match k:
			"id", "kind", "team", "buildState", "heroLevel", "veterancy", "variantRarity":
				e.set(k, int(v))
			"carryKind":
				e.carryKind = v
			_:
				e.set(k, v)
	return e


static func replay(c: Ctx, o: Dictionary) -> void:
	var faction = o.get("faction", "kingdom")
	Draw.setFactionResolver(func(_t): return faction)
	Draw.setTeamColorResolver(null)
	Draw.setDrawDayPhase(float(o.get("dayPhase", 0.0)))
	c.save()
	c.translate(float(o.get("tx", 0.0)), float(o.get("ty", 0.0)))
	var s := float(o.get("scale", 1.0))
	c.scale(s, s)
	var e: Entity = entity_from(o.ent) if o.has("ent") else null
	match o.op:
		"unit":
			Draw.drawUnit(c, e, float(o.get("time", 0.7)), int(o.get("lod", 0)))
		"building":
			Draw.drawBuilding(c, e, float(o.get("time", 1.0)), 0)
		"resource":
			Draw.drawResource(c, e, float(o.get("time", 1.0)))
		"projectile":
			Draw.drawProjectile(c, e)
		"selection":
			Draw.drawSelectionRing(c, e, bool(o.get("isPlayer", false)))
		"health":
			Draw.drawHealthBar(c, e)
		"text":
			c.fillStyle = o.get("color", "#10210a")
			c.font = o.get("font", "bold 11px 'Trebuchet MS', sans-serif")
			c.textAlign = "left"
			c.textBaseline = "alphabetic"
			c.fillText(o.text, 0, 0)
	c.restore()
