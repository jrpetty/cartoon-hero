## ui/production_panel.ts — production overview (toggle with V): every
## production building at a glance — what it's training, how deep the queue is
## and current progress. Returns the id of a clicked row so the caller can
## select + jump to that building.
class_name ProductionPanel


static func itemLabel(item: String) -> String:
	if item.begins_with("u:"):
		var u = C.UNITS.get(item.substr(2))
		return u.name if u != null else item.substr(2)
	if item.begins_with("t:"):
		var up = C.UPGRADES.get(item.substr(2))
		return up.name if up != null else item.substr(2)
	if item == "a:age":
		return "Advance Age"
	return item


## The clicked building's id, or null.
static func drawProductionPanel(W: float, H: float, world: World, me: int):
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	var p := world.player(me)
	var prod: Array = []
	for e: Entity in world.entities:
		if not e.alive or e.team != me or e.kind != T.Kind.Building:
			continue
		var bd = C.BUILDINGS.get(e.type)
		var trains: int = (bd.trains as Array).size() if bd != null and bd.has("trains") else 0
		if trains > 0 or e.productionQueue.size() > 0:
			prod.append(e)
	# Active producers first, then grouped by type for a stable order.
	JS.sort_by_cmp(prod, func(a: Entity, b: Entity) -> float:
		var d := (1 if b.productionQueue.size() > 0 else 0) - (1 if a.productionQueue.size() > 0 else 0)
		if d != 0:
			return d
		if a.type != b.type:
			return -1 if a.type < b.type else 1
		return a.id - b.id)

	var w := 250.0
	var rowH := 30.0
	var headH := 30.0
	var x := 12.0
	var maxRows := maxi(1, int(floor((H * 0.52) / rowH)))
	var rows: Array = prod.slice(0, maxRows)
	var h := headH + maxi(1, rows.size()) * rowH + 8
	var y := maxf(70, H / 2 - h / 2)
	var ctx := ui.ctx

	ctx.fillStyle = "rgba(10,8,4,0.88)"
	ctx.fillRect(x, y, w, h)
	ctx.strokeStyle = Palette.withAlpha(PAL.uiAccent, 0.5)
	ctx.strokeRect(x + 0.5, y + 0.5, w, h)
	ui.text("Production", x + 12, y + 20, {"size": 14, "bold": true, "color": PAL.uiAccent})
	var active := 0
	for e: Entity in prod:
		if e.productionQueue.size() > 0:
			active += 1
	ui.text("%d active" % active, x + w - 12, y + 20, {"size": 11, "align": "right", "color": "#9a917b"})

	if rows.is_empty():
		ui.text("No production buildings.", x + 12, y + headH + 18, {"size": 12, "color": "#9a917b"})
		return null

	var clicked = null
	var ry := y + headH
	for e: Entity in rows:
		var hover := ui.mx >= x and ui.mx <= x + w and ui.my >= ry and ui.my <= ry + rowH
		if hover:
			ctx.fillStyle = Palette.withAlpha(PAL.uiAccent, 0.12)
			ctx.fillRect(x + 2, ry, w - 4, rowH)
			if ui.clicked and not ui.pointerConsumed:
				ui.pointerConsumed = true
				clicked = e.id
		var bd = C.BUILDINGS.get(e.type)
		ui.text(bd.name if bd != null else e.type, x + 12, ry + 13, {"size": 12, "color": "#e7ddc4"})
		if e.productionQueue.size() > 0:
			var item: String = e.productionQueue[0]
			var total := world.itemTime(item, p.age) * (p.boon.trainSpeedMult if item.begins_with("u:") else 1.0)
			var prog := clampf(1 - e.productionTime / total, 0, 1) if total > 0 else 0.0
			var qn := e.productionQueue.size()
			ui.text(itemLabel(item) + (" ×%d" % qn if qn > 1 else ""), x + 12, ry + 26, {"size": 10, "color": "#cabfa4"})
			var bw := 70.0
			var bx := x + w - bw - 12
			ctx.fillStyle = "rgba(0,0,0,0.35)"
			ctx.fillRect(bx, ry + 17, bw, 7)
			ctx.fillStyle = PAL.uiAccent
			ctx.fillRect(bx, ry + 17, bw * prog, 7)
		else:
			ui.text("idle", x + 12, ry + 26, {"size": 10, "color": "#6f6a5c"})
		ry += rowH
	ui.text("V to close", x + w / 2, y + h - 5, {"align": "center", "size": 9, "color": "#6f6a5c"})
	return clicked
