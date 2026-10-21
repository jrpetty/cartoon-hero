## localStorage for the Godot build: string keys to string values, persisted
## in user://storage.json. Same API shape as the browser's (getItem returns
## null when missing), so meta/profile/career code ports unchanged.
extends Node

var _data: Dictionary = {}
var _path := "user://storage.json"
var _dirty := false


func _init() -> void:
	if FileAccess.file_exists(_path):
		var f := FileAccess.open(_path, FileAccess.READ)
		var d = JSON.parse_string(f.get_as_text())
		if d is Dictionary:
			_data = d


func getItem(key: String):
	return _data.get(key)


func setItem(key: String, value: String) -> void:
	_data[key] = value
	_flush()


func removeItem(key: String) -> void:
	_data.erase(key)
	_flush()


func clear() -> void:
	_data.clear()
	_flush()


func keys() -> Array:
	return _data.keys()


## Tests run against a throwaway store so they never touch the player's data.
func use_memory_only() -> void:
	_path = ""
	_data = {}


func _flush() -> void:
	if _path == "":
		return
	var tmp := _path + ".tmp"
	var f := FileAccess.open(tmp, FileAccess.WRITE)
	if f == null:
		return
	f.store_string(JSON.stringify(_data))
	f.close()
	DirAccess.rename_absolute(ProjectSettings.globalize_path(tmp), ProjectSettings.globalize_path(_path))
