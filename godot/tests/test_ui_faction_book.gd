extends TestCase
# Ported from "The first launch" in game/src/meta/faction_unlocks.test.ts, plus
# the book's list, chips and scrolling.

const U := preload("res://tests/ui_test_helpers.gd")
const W := 1600
const H := 900


func frame(fn: Callable, click = null, w: int = W, h: int = H):
	U.begin(U.ctx(w, h), click.x if click != null else -1, click.y if click != null else -1, click != null)
	return fn.call()


func test_sends_a_new_player_to_choose_a_faction_before_anything_else() -> void:
	U.fresh_store()
	var p := Profile.new()
	p.data.commanderReveal = ""
	eq(frame(func(): return Screens.MenuScreen.new().draw(W, H, 0, p)), "factions")
	p.chooseFirstFaction("legion")
	eq(frame(func(): return Screens.MenuScreen.new().draw(W, H, 0, p)), null)


func test_gives_the_chosen_faction_free_from_the_book_and_only_then_lets_you_leave() -> void:
	U.fresh_store()
	var p := Profile.new()
	var book := FactionBook.new()
	book.focus("ascendancy")
	# The take button sits at the right of the action bar.
	var outer := minf(W - 48, 1320)
	var x0 := JS.round(W / 2.0 - outer / 2)
	var take := {"x": x0 + outer - 150, "y": H - 72 + 14 + 22}
	var back := {"x": x0 + 65, "y": take.y}
	eq(frame(func(): return book.draw(W, H, 0, p), back), null, "left without choosing")
	eq(frame(func(): return book.draw(W, H, 0, p), take), "back")
	eq(p.ownedFactions(), ["ascendancy"])
	eq(p.data.renown, Profile.new().data.renown, "the first one is free")


func test_sells_the_rest_from_the_book() -> void:
	U.fresh_store()
	var p := Profile.new()
	p.chooseFirstFaction("kingdom")
	p.data.renown = 1500.0
	var book := FactionBook.new()
	book.focus("norse")
	var outer := minf(W - 48, 1320)
	var x0 := JS.round(W / 2.0 - outer / 2)
	frame(func(): return book.draw(W, H, 0, p), {"x": x0 + outer - 150, "y": H - 72 + 36})
	eq(p.ownsFaction("norse"), true)
	eq(p.data.renown, 0)
	eq(p.playableFaction(), "norse", "and plays it")
	eq(book.flash, "The Norse unlocked!" if C.FACTIONS.norse.name == "The Norse" else "%s unlocked!" % C.FACTIONS.norse.name)


func test_wont_sell_what_you_cant_afford() -> void:
	U.fresh_store()
	var rec := U.install()
	var p := Profile.new()
	p.chooseFirstFaction("kingdom")
	p.data.renown = 400.0
	var book := FactionBook.new()
	book.focus("khanate")
	frame(func(): return book.draw(W, H, 0, p))
	var unlock = rec.buttons.filter(func(b): return String(b.label).contains("Unlock for"))[0]
	eq(unlock.opts.get("disabled"), true, "disabled")
	check(rec.find_text("1100 ✦ more to unlock") != null, "says how much more")
	frame(func(): return book.draw(W, H, 0, p), {"x": unlock.x + 10, "y": unlock.y + 10})
	eq(p.ownsFaction("khanate"), false)
	U.uninstall()


func test_draws_every_factions_page_at_any_size() -> void:
	U.fresh_store()
	var p := Profile.new()
	p.chooseFirstFaction("kingdom")
	var book := FactionBook.new()
	for id in C.FACTION_IDS:
		book.focus(id)
		for size in [[1600, 900], [900, 600]]:
			frame(func(): return book.draw(size[0], size[1], 1, p), null, size[0], size[1])  # a script error fails the test


func test_picks_from_the_list_or_the_chips_and_scrolls_the_page() -> void:
	U.fresh_store()
	var rec := U.install()
	var p := Profile.new()
	p.chooseFirstFaction("kingdom")
	var book := FactionBook.new()
	frame(func(): return book.draw(W, H, 0, p))
	var card = rec.find_button(C.FACTIONS.shogunate.name)
	frame(func(): return book.draw(W, H, 0, p), {"x": card.x + 20, "y": card.y + 20})
	eq(book.selected, "shogunate", "a list card")
	# A narrow window turns the list into a row of chips.
	rec.clear()
	frame(func(): return book.draw(900, 600, 0, p), null, 900, 600)
	var chip = rec.find_button("Legion")
	check(chip != null, "chips drop 'The'")
	frame(func(): return book.draw(900, 600, 0, p), {"x": chip.x + 5, "y": chip.y + 5}, 900, 600)
	eq(book.selected, "legion", "a chip")
	eq(book.scroll, 0, "picking resets the scroll")
	for i in 30:
		U.begin(U.ctx(), 900, 400, false, 400)
		book.draw(W, H, 0, p)
	check(book.scroll > 0 and book.scroll <= book.contentH, "the page scrolls")
	U.uninstall()
