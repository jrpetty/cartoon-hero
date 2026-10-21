## ui/files.ts — saving a file to the player's computer, and opening one from
## it.
##
## The TS game is a single page; files come and go through the browser: a Blob
## and a download link out, a hidden file input in. The Godot build does the
## same on the web (JavaScriptBridge), and on the desktop writes downloads to
## the Downloads folder (falling back to user://downloads) and opens files with
## the platform's file dialog (or Godot's own FileDialog where there is none).
class_name Files

## Where the last downloadText() put its file (desktop), for messages and tests.
static var lastPath := ""
## Tests: write downloads here instead of the Downloads folder.
static var downloadDirOverride := ""


## Offer `text` to the player as a file called `name`. True if it went out.
static func downloadText(name: String, text: String, type: String = "application/json") -> bool:
	if OS.has_feature("web"):
		JavaScriptBridge.download_buffer(text.to_utf8_buffer(), name, type)
		lastPath = name
		return true
	var dirs: Array = []
	if downloadDirOverride != "":
		dirs.append(downloadDirOverride)
	else:
		var dl := OS.get_system_dir(OS.SYSTEM_DIR_DOWNLOADS)
		if dl != "":
			dirs.append(dl)
		dirs.append(ProjectSettings.globalize_path("user://downloads"))
	for d in dirs:
		if not DirAccess.dir_exists_absolute(d) and DirAccess.make_dir_recursive_absolute(d) != OK:
			continue
		var path: String = (d as String).path_join(name)
		var f := FileAccess.open(path, FileAccess.WRITE)
		if f == null:
			continue
		f.store_string(text)
		f.close()
		lastPath = path
		return true
	return false


## A file the player picked: resolves once with {name, text}, or null if they
## cancel (TS: a Promise). Connect to `done`, `await pick.done`, or pass a
## callback to `then`.
class FilePick extends RefCounted:
	signal done(result)
	var settled := false
	var result = null
	var _cbs: Array = []
	var _keep: Array = []

	func then(cb: Callable) -> FilePick:
		if settled:
			cb.call(result)
		else:
			_cbs.append(cb)
		return self

	func resolve(r) -> void:
		if settled:
			return
		settled = true
		result = r
		for cb in _cbs:
			cb.call(r)
		_cbs.clear()
		_keep.clear()
		done.emit(r)


## Ask the player to choose a file; resolves with its name and text (or null
## if they cancel). `accept` is the HTML accept list (".bbreplay,.json").
static func pickTextFile(accept: String, cb: Callable = Callable()) -> FilePick:
	var pick := FilePick.new()
	if cb.is_valid():
		pick.then(cb)
	var exts: Array = []
	for a in accept.split(","):
		var s := a.strip_edges()
		if s.begins_with("."):
			exts.append("*" + s)
	if OS.has_feature("web"):
		_pickWeb(pick, accept)
		return pick
	var filters := PackedStringArray()
	if not exts.is_empty():
		filters.append("%s ; Files" % ", ".join(exts))
	if DisplayServer.has_feature(DisplayServer.FEATURE_NATIVE_DIALOG_FILE):
		var on_pick := func(status: bool, paths: PackedStringArray, _filter: int) -> void:
			if status and paths.size() > 0:
				pick.resolve(_read(paths[0]))
			else:
				pick.resolve(null)
		var err := DisplayServer.file_dialog_show("Open", "", "", false, DisplayServer.FILE_DIALOG_MODE_OPEN_FILE, filters, on_pick)
		if err == OK:
			return pick
	var tree := Engine.get_main_loop() as SceneTree
	if tree == null or tree.root == null:
		pick.resolve(null)
		return pick
	var dlg := FileDialog.new()
	dlg.file_mode = FileDialog.FILE_MODE_OPEN_FILE
	dlg.access = FileDialog.ACCESS_FILESYSTEM
	dlg.filters = filters
	dlg.title = "Open"
	dlg.size = Vector2i(720, 480)
	dlg.file_selected.connect(func(p: String) -> void:
		pick.resolve(_read(p))
		dlg.queue_free())
	dlg.canceled.connect(func() -> void:
		pick.resolve(null)
		dlg.queue_free())
	tree.root.add_child(dlg)
	dlg.popup_centered()
	return pick


static func _read(path: String):
	var f := FileAccess.open(path, FileAccess.READ)
	if f == null:
		return null
	return {"name": path.get_file(), "text": f.get_as_text()}


static func _pickWeb(pick: FilePick, accept: String) -> void:
	var on_text := JavaScriptBridge.create_callback(func(args: Array) -> void:
		if args.size() >= 2 and args[0] != null:
			pick.resolve({"name": str(args[0]), "text": str(args[1])})
		else:
			pick.resolve(null))
	pick._keep.append(on_text)
	var win = JavaScriptBridge.get_interface("window")
	win.__bbPickCb = on_text
	JavaScriptBridge.eval("""
		(function () {
			const input = document.createElement('input');
			input.type = 'file';
			input.accept = %s;
			input.style.display = 'none';
			input.onchange = () => {
				const f = input.files && input.files[0];
				input.remove();
				if (!f) { window.__bbPickCb(null, null); return; }
				f.text().then((t) => window.__bbPickCb(f.name, t), () => window.__bbPickCb(null, null));
			};
			document.body.appendChild(input);
			input.click();
		})();
	""" % JSON.stringify(accept), true)
