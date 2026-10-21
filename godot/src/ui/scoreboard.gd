## ui/scoreboard.ts — live multi-team scoreboard overlay (toggled with Tab).
## Standalone + draws through the already-begun `ui` context, so it's testable
## against a real canvas.
class_name Scoreboard


static func drawScoreboard(W: float, H: float, world: World, me: int) -> void:
	var ui := UI.ui
	var PAL: Dictionary = C.PAL
	var rows: Array = []
	for t in world.numTeams:
		rows.append(Metrics.teamMetrics(world, t))
	JS.sort_by_cmp(rows, func(a, b): return b.score - a.score)

	var ctx := ui.ctx
	var cols: Array = [
		["Realm", 188], ["Faction", 96], ["Score", 70], ["Army", 64], ["Vils", 50], ["Kills", 56], ["Razed", 56], ["Age", 70], ["Oaths", 170],
	]
	var pad := 16.0
	var rowH := 26.0
	var w := pad * 2
	for c in cols:
		w += c[1]
	var h := 44 + rows.size() * rowH + 14
	var x := (W - w) / 2
	var y := 70.0

	ctx.fillStyle = "rgba(10, 8, 4, 0.9)"
	ctx.fillRect(x, y, w, h)
	ctx.strokeStyle = Palette.withAlpha(PAL.uiAccent, 0.5)
	ctx.strokeRect(x + 0.5, y + 0.5, w, h)
	ui.text("Scoreboard", x + w / 2, y + 24, {"align": "center", "size": 18, "bold": true, "color": PAL.uiAccent})

	var cx := x + pad
	var headY := y + 44
	for c in cols:
		var label: String = c[0]
		var cw: float = c[1]
		var left := label == "Realm" or label == "Oaths" or label == "Faction"
		ui.text(label, (cx + (14.0 if label == "Oaths" else 0.0)) if left else cx + cw - 8, headY, {
			"size": 11, "color": "#9a917b", "align": "left" if left else "right",
		})
		cx += cw

	var ry := headY + 8
	for m in rows:
		ry += rowH
		var team: int = m.team
		var ally := world.areAllied(me, team)
		if team == me:
			ctx.fillStyle = Palette.withAlpha(PAL.uiAccent, 0.12)
			ctx.fillRect(x + 4, ry - rowH + 6, w - 8, rowH)
		var tc: String = Palette.teamColor(team).main
		var dim := 0.45 if m.defeated else 1.0
		cx = x + pad
		ctx.fillStyle = Palette.withAlpha(tc, dim)
		ctx.fillRect(cx, ry - 11, 12, 12)
		var tag := " (You)" if team == me else (" (Ally)" if ally else "")
		var name := Spectator.teamLabel(team) + tag + ("  ☠" if m.defeated else "")
		ui.text(name, cx + 18, ry, {"size": 12, "color": "#9a917b" if m.defeated else "#e7ddc4", "bold": team == me})
		cx += cols[0][1]
		var fac := Factions.factionOf(world.player(team).faction)
		ui.text((fac.name as String).trim_prefix("The "), cx, ry, {"size": 12, "bold": true, "color": Palette.withAlpha(fac.color, dim)})
		cx += cols[1][1]
		var vals := [m.score, m.military, m.villagers, m.killed, m.razed]
		for i in vals.size():
			ui.text(JS.num(vals[i]), cx + cols[i + 2][1] - 8, ry, {"size": 12, "align": "right", "color": Palette.withAlpha("#e7ddc4", dim)})
			cx += cols[i + 2][1]
		ui.text(Tech.ageShort(m.age), cx + cols[7][1] - 8, ry, {"size": 12, "align": "right", "color": Palette.withAlpha("#cabfa4", dim)})
		# Oaths are public — this is where you read what everyone has become.
		cx += cols[7][1] + 14
		for chip in Oaths.oathChips(world.player(team).oaths):
			ui.text(chip.label, cx, ry, {"size": 12, "bold": true, "color": Palette.withAlpha(chip.color, dim)})
			ctx.font = "bold 12px \"Trebuchet MS\", sans-serif"
			cx += ctx.measureText(chip.label).width + 9
	ui.text("Tab to close", x + w / 2, y + h - 6, {"align": "center", "size": 10, "color": "#6f6a5c"})
