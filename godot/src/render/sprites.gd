## render/sprites.ts — optional baked-sprite registry.
##
## When scripts/meshy.ts has generated real models, their preview sprites are
## loaded here and override the procedural unit and building art. When no
## sprites are present, every lookup returns null and the engine keeps drawing
## the procedural art — so the game always renders.
##
## A SpriteEntry is a Dictionary {img: Texture2D, scale, anchorY, ready}.
class_name Sprites

static var sprites := {}
static var loaded := false


## Returns a ready sprite for a type, or null to use procedural art.
static func spriteFor(type: String) -> Variant:
	var s = sprites.get(type)
	return s if s != null and s.ready else null


## True once at least one baked sprite is ready (lets callers fast-path).
static func spritesActive() -> bool:
	return loaded and sprites.size() > 0


## Load the sprite manifest written by the Meshy pipeline (res://sprite-
## manifest.json, rows {sprite, scale, anchorY}) and each image. Safe to call
## always — returns 0 when there is no manifest (the common case).
static func loadSprites() -> int:
	var path := "res://sprite-manifest.json"
	if not FileAccess.file_exists(path):
		return 0
	var manifest = JSON.parse_string(FileAccess.get_file_as_string(path))
	if not (manifest is Dictionary):
		return 0
	var count := 0
	for id in manifest:
		var row = manifest[id]
		if not (row is Dictionary) or not row.get("sprite"):
			continue
		var entry := {"img": null, "scale": float(row.get("scale", 1.0)), "anchorY": float(row.get("anchorY", 0.5)), "ready": false}
		var src: String = row.sprite
		if not src.begins_with("res://") and not src.begins_with("user://"):
			src = "res://" + src.trim_prefix("/")
		var img := Image.load_from_file(src) if FileAccess.file_exists(src) else null
		if img != null:
			entry.img = ImageTexture.create_from_image(img)
			entry.ready = true
		sprites[id] = entry
		count += 1
	loaded = true
	return count
