extends TestCase
# The main menu, the Armory and the Nemesis screen: every tile and tab returns
# the action main.ts acts on, the commander reveal is claimed, a War Chest and
# a boon cache spend, spin and pay out, and the rival screen fights or leaves.

const U := preload("res://tests/ui_test_helpers.gd")
const W := 1600
const H := 900


func veteran() -> Profile:
	var p := Profile.new()
	p.data.commanderReveal = ""
	p.chooseFirstFaction("legion")
	return p


func click_center(b) -> Dictionary:
	return {"x": b.x + b.w / 2, "y": b.y + b.h / 2}


# ------------------------------------------------------------------- menu --

func test_every_menu_tile_returns_its_action() -> void:
	U.fresh_store()
	var rec := U.install()
	var p := veteran()
	var m := Screens.MenuScreen.new()
	var c := U.ctx()
	for size in [[1600, 900], [960, 640]]:
		rec.clear()
		U.begin(c)
		m.draw(size[0], size[1], 0, p)
		var want := {"Skirmish": "skirmish", "Multiplayer": "multiplayer", "Nemesis": "nemesis", "Warband Tactics": "warband",
			"Career": "career", "Factions": "factions", "Armory": "armory", "Map Editor": "editor", "Codex": "codex", "Settings": "settings"}
		var tiles: Array = rec.buttons.duplicate()
		for title in want:
			var b = null
			for t in tiles:
				var tip = t.opts.get("tooltip")
				if t.label == "" and tip is Array and tip[0] == title:
					b = t
			check(b != null, "%s tile at %s" % [title, str(size)])
			if b == null:
				continue
			var at := click_center(b)
			U.begin(c, at.x, at.y, true)
			eq(m.draw(size[0], size[1], 0, p), want[title], "%s at %s" % [title, str(size)])
	U.uninstall()


func test_the_profile_and_realm_cards_open_career_and_factions() -> void:
	U.fresh_store()
	var rec := U.install()
	var p := veteran()
	var m := Screens.MenuScreen.new()
	U.begin(U.ctx())
	m.draw(W, H, 0, p)
	var career = rec.find_button("Your career")
	var realm = rec.find_button("Your realm")
	var at := click_center(career)
	U.begin(U.ctx(), at.x, at.y, true)
	eq(m.draw(W, H, 0, p), "career")
	at = click_center(realm)
	U.begin(U.ctx(), at.x, at.y, true)
	eq(m.draw(W, H, 0, p), "factions")
	U.uninstall()


func test_continue_picks_the_latest_save() -> void:
	U.fresh_store()
	Storage.setItem(Savegame.STORAGE_KEY, JSON.stringify([{
		"version": 2, "label": "Highlands — 12:34", "savedAt": 5, "tick": 15000, "commands": [],
		"summary": {"mapName": "Highlands", "mode": "conquest", "players": 2, "difficulty": "Knight", "elapsed": 754},
	}]))
	var rec := U.install()
	var p := veteran()
	var m := Screens.MenuScreen.new()
	U.begin(U.ctx())
	m.draw(W, H, 0, p)
	check(rec.find_text("Highlands · 12:34") != null, "the save's map and time")
	var cont = rec.find_button("Continue your last save")
	var at := click_center(cont)
	U.begin(U.ctx(), at.x, at.y, true)
	eq(m.draw(W, H, 0, p), "resume")
	eq(m.pickedSave.label, "Highlands — 12:34", "the save the app should load")
	U.uninstall()


func test_a_new_commander_is_revealed_and_claimed() -> void:
	U.fresh_store()
	var rec := U.install()
	var p := Profile.new()
	p.data.commanderReveal = "marshal"
	var m := Screens.MenuScreen.new()
	U.begin(U.ctx())
	eq(m.draw(W, H, 0, p), null, "the reveal holds the menu")
	check(rec.find_text(C.COMMANDERS.marshal.name) != null, "the commander is named")
	var claim = rec.find_button("Claim")
	var at := click_center(claim)
	U.begin(U.ctx(), at.x, at.y, true)
	m.draw(W, H, 0, p)
	eq(p.data.commanderReveal, "", "claimed")
	U.uninstall()


# ----------------------------------------------------------------- armory --

func test_armory_tabs_and_back() -> void:
	U.fresh_store()
	var rec := U.install()
	var p := veteran()
	var a := Screens.ArmoryScreen.new()
	for tab in ["War Chests", "Boons", "Commanders", "Collection"]:
		rec.clear()
		U.begin(U.ctx())
		a.draw(W, H, 0, 0.016, p)
		var b = rec.find_button(tab)
		var at := click_center(b)
		U.begin(U.ctx(), at.x, at.y, true)
		a.draw(W, H, 0, 0.016, p)
	eq(a.tab, "collection")
	rec.clear()
	U.begin(U.ctx())
	a.draw(W, H, 0, 0.016, p)
	var back = rec.find_button("⟵ Back")
	var at := click_center(back)
	U.begin(U.ctx(), at.x, at.y, true)
	eq(a.draw(W, H, 0, 0.016, p), "back")
	U.uninstall()


func test_a_war_chest_spends_renown_spins_and_pays_out() -> void:
	U.fresh_store()
	var rec := U.install()
	var p := veteran()
	p.data.renown = 1000.0
	var a := Screens.ArmoryScreen.new()
	U.begin(U.ctx())
	a.draw(W, H, 0, 0.016, p)
	var chest: Dictionary = C.CHESTS[0]
	var open = rec.find_button("Open — %s ✦" % JS.num(chest.cost))
	check(open != null and not open.opts.get("disabled"), "affordable")
	var at := click_center(open)
	U.begin(U.ctx(), at.x, at.y, true)
	a.draw(W, H, 0, 0.016, p)
	eq(p.data.renown, 1000.0 - chest.cost, "paid")
	check(a.opening != null and a.result != null, "opening")
	eq(a.tickets.size(), 60, "a strip of sixty")
	eq(a.tickets[52].unitName, a.result.variant.unitName, "the winner sits under the needle")
	# A click skips the spin: it lands on the next frame.
	U.begin(U.ctx(), 10, 10, true)
	a.draw(W, H, 0, 0.016, p)
	eq(a.spinT, 1.0, "a click skips to the end")
	U.begin(U.ctx())
	a.draw(W, H, 0, 0.016, p)
	check(a.revealed, "revealed")
	rec.clear()
	U.begin(U.ctx())
	a.draw(W, H, 0, 0.016, p)
	var claim = rec.find_button("Claim")
	var res: Dictionary = a.result
	var renownBefore: float = p.data.renown
	at = click_center(claim)
	U.begin(U.ctx(), at.x, at.y, true)
	a.draw(W, H, 0, 0.016, p)
	eq(a.opening, null, "closed")
	if res.duplicate:
		eq(p.data.renown, renownBefore + res.refund, "a duplicate is refunded")
	else:
		check(p.owns(res.variant.key), "the variant is yours")
	U.uninstall()


func test_a_boon_cache_spends_valor_and_upgrades_a_boon() -> void:
	U.fresh_store()
	var rec := U.install()
	var p := veteran()
	p.data.valor = 500.0
	var a := Screens.ArmoryScreen.new()
	a.tab = "boons"
	U.begin(U.ctx())
	a.draw(W, H, 0, 0.016, p)
	var open = rec.find_button("Open — %s ⚔" % JS.num(BoonCache.BOON_CACHE_COST))
	var at := click_center(open)
	U.begin(U.ctx(), at.x, at.y, true)
	a.draw(W, H, 0, 0.016, p)
	eq(p.data.valor, 500.0 - BoonCache.BOON_CACHE_COST, "paid in valor")
	check(a.boonOpening, "spinning")
	check(a.tickets.all(func(t): return t.rarity >= 1), "caches never roll Common")
	for i in 400:
		U.begin(U.ctx())
		a.draw(W, H, 0, 0.016, p)
		if a.revealed:
			break
	check(a.revealed, "lands by itself")
	rec.clear()
	U.begin(U.ctx())
	a.draw(W, H, 0, 0.016, p)
	var res: Dictionary = a.boonResult
	at = click_center(rec.find_button("Claim"))
	U.begin(U.ctx(), at.x, at.y, true)
	a.draw(W, H, 0, 0.016, p)
	eq(a.boonOpening, false, "closed")
	if not res.duplicate:
		check(p.ownsBoon(res.key), "the boon is yours")
	U.uninstall()


func test_boons_equip_and_set_their_age() -> void:
	U.fresh_store()
	var rec := U.install()
	var p := veteran()
	var a := Screens.ArmoryScreen.new()
	a.tab = "boons"
	U.begin(U.ctx())
	a.draw(W, H, 0, 0.016, p)
	var first: Dictionary = (C.BOONS as Array).filter(func(b): return b.category == C.BOON_CATEGORIES[0])[0]
	var row = null
	for b in rec.buttons:
		if b.label == "" and absf(b.h - 50) < 0.5:
			row = b
			break
	var at := click_center(row)
	U.begin(U.ctx(), at.x, at.y, true)
	a.draw(W, H, 0, 0.016, p)
	eq(p.data.equippedBoons[C.BOON_CATEGORIES[0]], first.id, "equipped")
	rec.clear()
	U.begin(U.ctx())
	a.draw(W, H, 0, 0.016, p)
	var iii: Array = rec.buttons.filter(func(b): return b.label == "III")
	at = click_center(iii[0])
	U.begin(U.ctx(), at.x, at.y, true)
	a.draw(W, H, 0, 0.016, p)
	eq(p.boonAgeFor(C.BOON_CATEGORIES[0]), 2, "unlocks at the third age")
	U.uninstall()


func test_collection_equips_an_owned_variant_and_recruits_a_commander() -> void:
	U.fresh_store()
	var rec := U.install()
	var p := veteran()
	var unit: String = C.COLLECTIBLE_UNIT_IDS[1]
	p.grant(Catalog.variantKey(unit, 2))
	var a := Screens.ArmoryScreen.new()
	a.tab = "collection"
	U.begin(U.ctx())
	a.draw(W, H, 0, 0.016, p)
	var name: String = Catalog.VARIANT_BY_KEY[Catalog.variantKey(unit, 2)].name
	var cell = null
	for b in rec.buttons:
		var tip = b.opts.get("tooltip")
		if tip is Array and tip[0] == name:
			cell = b
	check(cell != null and cell.label == "✓", "an owned, unequipped variant")
	var at := click_center(cell)
	U.begin(U.ctx(), at.x, at.y, true)
	a.draw(W, H, 0, 0.016, p)
	eq(int(p.data.loadout[unit]), 2, "equipped")
	# Commanders: recruiting costs renown and reveals one inline.
	p.data.renown = 600.0
	a.tab = "commanders"
	rec.clear()
	U.begin(U.ctx())
	a.draw(W, H, 0, 0.016, p)
	var owned: int = p.data.commanders.size()
	var recruit = rec.find_button("Recruit a Commander — %d ✦" % Screens.COMMANDER_RECRUIT_COST)
	at = click_center(recruit)
	U.begin(U.ctx(), at.x, at.y, true)
	a.draw(W, H, 0, 0.016, p)
	eq(p.data.commanders.size(), owned + 1, "recruited")
	eq(p.data.renown, 100.0, "paid")
	eq(p.data.commanderReveal, "", "shown inline, not on the menu")
	U.uninstall()


# ---------------------------------------------------------------- nemesis --

func test_the_nemesis_screen_fights_or_goes_back() -> void:
	U.fresh_store()
	var rec := U.install()
	var r := RivalScreen.new()
	U.begin(U.ctx())
	eq(r.draw(W, H, 1, "legion"), null)
	var name: String = r.st.current.name
	check(rec.find_text("You have never met. Yet.") != null, "a fresh rival")
	check(rec.find_text("HOW A NEMESIS WORKS") != null, "room for the explainer")
	var fight = rec.find_button("Fight %s" % name)
	var at := click_center(fight)
	U.begin(U.ctx(), at.x, at.y, true)
	eq(r.draw(W, H, 1, "legion"), "fight")
	at = click_center(rec.find_button("Back"))
	U.begin(U.ctx(), at.x, at.y, true)
	eq(r.draw(W, H, 1, "legion"), "back")
	# A narrow window stacks the panels and still draws.
	U.begin(U.ctx(700, 900))
	r.draw(700, 900, 1, "legion")
	U.uninstall()


func test_the_nemesis_screen_shows_what_the_rival_learned() -> void:
	U.fresh_store()
	var st := Rival.loadRival("legion")
	st.current.history = [{"at": 1_790_000_000_000.0, "rivalWon": true, "note": "Won at Highlands"}]
	st.current.grudge = "archer"
	st.current.rank = 2.0
	Rival.saveRival(st)
	for i in 3:
		Career.recordCareer(U.career_match({"trained": {"villager": 30.0, "archer": 25.0}}))
	var rec := U.install()
	var r := RivalScreen.new()
	r.open("legion")
	U.begin(U.ctx())
	r.draw(W, H, 1, "legion")
	check(rec.find_text("Mostly archers — it brings the counter") != null, "it knows your army")
	check(rec.find_text("Your archers scarred it — it brings their answer") != null, "and its grudge")
	check(rec.find_text("Win: +120 renown") != null, "the reward scales with rank")
	U.uninstall()
