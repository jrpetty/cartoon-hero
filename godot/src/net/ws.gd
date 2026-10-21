## net/ws.ts — client transport for the hosted relay server, over Godot's
## WebSocketPeer. Same message protocol as the browser build (JSON text
## frames), so Godot and browser players share the server's lobbies.
##
## Godot sockets are polled: call `poll()` every frame (the main loop does).
class_name WSLink
extends RefCounted

var ws := WebSocketPeer.new()
var onData: Callable
var onOpen: Callable
var onClose: Callable
var _was_open := false
var _closed := false
var _failed := false


func _init(url: String) -> void:
	ws.inbound_buffer_size = 1 << 22
	ws.outbound_buffer_size = 1 << 22
	if ws.connect_to_url(url) != OK:
		_failed = true


func poll() -> void:
	if _closed:
		return
	# A URL that couldn't even be dialled closes on the first poll, as a
	# browser WebSocket fires onerror/onclose for one.
	if _failed:
		_closed = true
		if onClose.is_valid():
			onClose.call()
		return
	ws.poll()
	var st := ws.get_ready_state()
	if st == WebSocketPeer.STATE_OPEN:
		if not _was_open:
			_was_open = true
			if onOpen.is_valid():
				onOpen.call()
		while ws.get_available_packet_count() > 0:
			var text := ws.get_packet().get_string_from_utf8()
			var msg = WireJSON.parse(text)
			if msg != null and onData.is_valid():
				onData.call(msg)
	elif st == WebSocketPeer.STATE_CLOSED:
		_closed = true
		if onClose.is_valid():
			onClose.call()


func send(msg) -> void:
	if ws.get_ready_state() == WebSocketPeer.STATE_OPEN:
		ws.send_text(WireJSON.stringify(msg))


var connected: bool:
	get:
		return ws.get_ready_state() == WebSocketPeer.STATE_OPEN


func close() -> void:
	ws.close()
