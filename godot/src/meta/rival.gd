## The Nemesis: one named rival warlord who remembers you.
##
## Borrowed from action RPGs (Shadow of Mordor's Nemesis system) rather than
## from other strategy games: an opponent with a name, a rank and a memory.
## Every time you fight it, it learns — first what your army is made of, then
## when you attack and how you win — and it comes back playing the counter.
## Beat it and it carries a scar (and a grudge against whatever did it); beat it
## three times and it falls, and someone from its ranks rises to take its place.
## Lose to it and it climbs a rank and earns a name for how it beat you.
##
## Everything it knows is read from your own career record (meta/playstyle), so
## the rival is the playstyle analysis turned into an opponent.
##
## Shapes (Dictionaries, stored as the TS build stores them):
##   RivalMemory: { army: SeenComposition or null, armyWord,
##     timing: "early" | "late" | "mid" | null, winsBy } — what it has learned,
##     in the order it learns: army, timing, how you win.
##   Rival: { id, name, epithet, faction, rank (1–5, climbs when it beats you),
##     beatYou, lostToYou, scars,
##     grudge: the unit class that scarred it most recently — it brings the counter ("" none),
##     born, origin (where it came from: "Rose from Grimwald's ranks"),
##     history: [{ at, rivalWon, note }] }
##   Fallen: { name, epithet, rank, beatYou, lostToYou, at }
##   RivalState: { current: Rival, fallen: Fallen[], seq }
##   SeenComposition (ai/skirmish_ai): { infantry, archer, cavalry, siege }
##   RivalAdaptation (ai/skirmish_ai): { expect?: Partial<SeenComposition>,
##     style?: AIStyle, walls?: bool }
##   RivalOutcome: { lines, renown (extra renown for the player), slain, promoted }
class_name Rival

const KEY := "bb_rival_v1"

const NAMES := ["Grimwald", "Sigrun", "Vasca", "Tarkhan", "Oro", "Belisar", "Morwen", "Kaede", "Ulfar", "Temur", "Aurelia", "Drago",
	"Hakon", "Isolde", "Ravik", "Sabine", "Toghrul", "Yorick", "Casimir", "Brannoc", "Akane", "Varus", "Ingrid", "Malach"]
const FIRST_EPITHETS := ["the Upstart", "the Unproven", "the Hungry", "the Watcher", "the Ambitious", "the Young"]

## The difficulty a rival of this rank plays at.
static var RANK_DIFFICULTY: Array:
	get:
		return C.RANK_DIFFICULTY

static var RANK_NAMES: Array:
	get:
		return C.RANK_NAMES

## Tests may put another store here; null means the Storage autoload.
static var storage_override: Object = null


static func store() -> Object:
	return storage_override if storage_override != null else Storage


## A small deterministic hash, so a new rival's name and faction come from its
## number. (`((n * 2654435761) >>> 0) % xs.length`; exact while n * 2654435761
## stays below 2^53, as JS needs it to be too.)
static func pick(xs: Array, n: float):
	return xs[JS.u32(int(n) * 2654435761) % xs.size()]


static func newRival(seq: float, avoidFaction: String, origin: String, rank := 1.0, now = null) -> Dictionary:
	if now == null:
		now = JS.now_ms()
	var facs: Array = C.FACTION_IDS.filter(func(f): return f != avoidFaction)
	return {
		"id": seq, "name": pick(NAMES, seq + 7), "epithet": pick(FIRST_EPITHETS, seq + 3), "faction": pick(facs, seq + 11),
		"rank": rank, "beatYou": 0.0, "lostToYou": 0.0, "scars": [], "grudge": "", "born": now, "origin": origin, "history": [],
	}


static func loadRival(playerFaction := "") -> Dictionary:
	var s := store()
	var raw = s.getItem(KEY) if s != null else null
	if raw:
		var st = LocalJSON.parse(raw)
		if st is Dictionary and st.get("current") is Dictionary and _truthy(st.current.get("name")):
			return st
	# (fall through to a fresh rival)
	var fresh := {"current": newRival(1, playerFaction, "Has heard of you, and wants your name for its own"), "fallen": [], "seq": 1.0}
	saveRival(fresh)
	return fresh


static func saveRival(st: Dictionary) -> void:
	var s := store()
	if s != null:
		s.setItem(KEY, LocalJSON.stringify(st))  # storage full or blocked — the rival just forgets


## JS truthiness (an empty object or array is truthy, unlike in GDScript).
static func _truthy(v) -> bool:
	if v == null:
		return false
	if v is bool:
		return v
	if v is float or v is int:
		return v != 0 and not is_nan(float(v))
	if v is String:
		return v != ""
	return true


# ------------------------------------------------------------------ memory --
const CLASS_WORD := {"infantry": "infantry", "archer": "archers", "cavalry": "cavalry", "siege": "siege", "support": "monks"}


## What the rival knows about you. It learns a layer per meeting: the first
## time it has only heard stories; after one battle it knows your army; after
## two, your timing; after three, how you win. Its rank lets it remember more.
static func memoryOf(r: Dictionary, career: Array) -> Dictionary:
	var met: float = r.history.size() + maxf(0.0, r.rank - 2)
	var recent := career.slice(-15)
	var mem := {"army": null, "armyWord": "", "timing": null, "winsBy": ""}
	if met < 1 or recent.is_empty():
		return mem
	var comp := {"infantry": 0.0, "archer": 0.0, "cavalry": 0.0, "siege": 0.0}
	var total := 0.0
	for m in recent:
		for u in m.trained:
			var c = Playstyle.unitClass(u)
			if c and c != "support":
				comp[c] += m.trained[u]
				total += m.trained[u]
	if total > 0:
		# Scaled to "an army of 12 of these", which is what the AI's counter
		# blend reads its scouting in.
		for k in comp.keys():
			comp[k] = JS.round((comp[k] / total) * 12.0 * 10.0) / 10.0
		mem.army = comp
		var pairs: Array = []
		for k in comp:
			pairs.append([k, comp[k]])
		JS.sort_by_cmp(pairs, func(a, b): return b[1] - a[1])
		var top: Array = pairs[0]
		mem.armyWord = CLASS_WORD[top[0]] if top[1] >= 5 else "a mixed army"
	if met >= 2:
		var early := float(recent.filter(func(m):
			var a := Playstyle.attackAt(m)
			return a >= 0 and a <= 420).size()) / recent.size()
		var late := float(recent.filter(func(m):
			var a := Playstyle.attackAt(m)
			return a < 0 or a >= 780).size()) / recent.size()
		mem.timing = "early" if early >= 0.4 else ("late" if late >= 0.5 else "mid")
	if met >= 3:
		var p := Playstyle.analyse(career)
		mem.winsBy = p.title if p.primary != null else ("adapting" if p.enough else "")
		if not mem.winsBy:
			var merged := {}
			for m in recent:
				var o = m.get("opener")
				if o is Dictionary:
					merged.merge(o, true)
			var op := Playstyle.openerWord(merged)
			mem.winsBy = ("%s attacks" % op.word) if op.word else ""
	return mem


## How it will play you, from what it knows. Stored in the match config so a replay rebuilds it.
static func adaptationFor(r: Dictionary, mem: Dictionary) -> Dictionary:
	var a := {}
	if mem.army != null:
		a.expect = mem.army.duplicate()
	# A grudge: whatever scarred it, it now brings the answer to.
	if _truthy(r.grudge) and r.grudge != "support":
		if a.get("expect") == null:
			a.expect = {}
		a.expect[r.grudge] = float(a.expect.get(r.grudge, 0.0)) + 5
	if mem.timing == "early":
		a.style = "turtle"
		a.walls = true
	elif mem.timing == "late":
		a.style = "rush"
	elif mem.timing == "mid":
		a.style = "boom"
	return a


## What it says before the battle — the proof that it remembers.
static func taunt(r: Dictionary, mem: Dictionary) -> String:
	if r.history.is_empty():
		return "So you're the one they talk about. I am %s %s, and I'll learn everything about you." % [r.name, r.epithet]
	var last: Dictionary = r.history[r.history.size() - 1]
	if _truthy(r.grudge) and not last.rivalWon:
		return "Your %s gave me this scar. I've brought their answer." % CLASS_WORD.get(r.grudge, r.grudge)
	if mem.timing == "early":
		return "You always come before seven minutes. This time my walls will be waiting%s." % ((" for your %s" % mem.armyWord) if mem.armyWord else "")
	if mem.timing == "late":
		return "You like to take your time and build. I won't give you the time."
	if last.rivalWon:
		return "I beat you once. They call me %s for it now." % r.epithet
	if mem.armyWord:
		return "I know your %s now. Every one of them." % mem.armyWord
	return "Again, then."


# ----------------------------------------------------------------- outcome --
## The epithet a rival earns for how it beat you.
static func epithetFor(m: Dictionary) -> String:
	if m.durationSec < 900:
		return "the Swift"
	if m.buildingsLost >= 6:
		return "the Burner"
	if m.durationSec >= 2100:
		return "the Patient"
	if float(m.lost.get("villager", 0.0)) >= 10:
		return "the Butcher"
	return "the Unbroken"


## Settle a match against the rival. `m` is your side of it (a CareerMatch),
## so the rival reads exactly what the career reads.
static func settleRival(st: Dictionary, m: Dictionary, playerFaction := "", now = null) -> Dictionary:
	if now == null:
		now = JS.now_ms()
	var r: Dictionary = st.current
	var out := {"lines": [], "renown": 0.0, "slain": false, "promoted": false}
	if not m.won:
		r.beatYou += 1
		var was: float = r.rank
		r.rank = minf(5.0, r.rank + 1)
		out.promoted = r.rank > was
		r.epithet = epithetFor(m)
		r.history.append({"at": now, "rivalWon": true, "note": "Beat you in %s min" % JS.num(JS.round(m.durationSec / 60.0))})
		out.lines.append("%s beat you — now %s %s%s." % [r.name, r.name, r.epithet, (", risen to %s" % RANK_NAMES[int(r.rank)]) if out.promoted else ""])
		out.lines.append("It will remember how you played.")
	else:
		r.lostToYou += 1
		out.renown = 60.0 * r.rank
		# Scarred by the unit class that did the most killing.
		var byClass := {}
		var src: Dictionary = m.unitKills if m.get("unitKills") != null else m.trained
		for u in src:
			var c = Playstyle.unitClass(u)
			if c:
				byClass[c] = float(byClass.get(c, 0.0)) + src[u]
		var pairs: Array = []
		for k in byClass:
			pairs.append([k, byClass[k]])
		JS.sort_by_cmp(pairs, func(a, b): return b[1] - a[1])
		var top = pairs[0] if pairs.size() > 0 else null
		if top != null:
			r.grudge = top[0]
			r.scars.append("Scarred by your %s" % CLASS_WORD[top[0]])
		r.history.append({"at": now, "rivalWon": false, "note": "Lost to you in %s min" % JS.num(JS.round(m.durationSec / 60.0))})
		if r.lostToYou >= 3:
			out.slain = true
			out.renown += 250
			st.fallen.push_front({"name": r.name, "epithet": r.epithet, "rank": r.rank, "beatYou": r.beatYou, "lostToYou": r.lostToYou, "at": now})
			st.fallen = st.fallen.slice(0, 12)
			st.seq += 1
			var heir := newRival(st.seq, playerFaction, "Rose from %s's ranks after you ended %s" % [r.name, r.name], minf(3.0, 1.0 + floor(st.fallen.size() / 2.0)), now)
			# The heir watched every battle: it keeps its master's grudge.
			heir.grudge = r.grudge
			st.current = heir
			out.lines.append("%s %s has fallen for good. +%s renown." % [r.name, r.epithet, JS.num(out.renown)])
			out.lines.append("%s %s rises from the ranks to take their place — and remembers." % [heir.name, heir.epithet])
		else:
			out.lines.append("You drove off %s%s. +%s renown." % [r.name, (", scarred by your %s" % CLASS_WORD[top[0]]) if top != null else "", JS.num(out.renown)])
			out.lines.append("%s escaped, and will be back (%s more defeat%s to end them)." % [r.name, JS.num(3 - r.lostToYou), "" if r.lostToYou == 2 else "s"])
	r.history = r.history.slice(-20)
	saveRival(st)
	return out
