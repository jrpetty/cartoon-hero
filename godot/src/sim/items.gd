## sim/items.ts — Warband Tactics relics: equip onto a unit to power it up for
## the rest of the run. Reuses the trait Buff system.
##
## Loot arrives as *components*: small parts that are useful on their own but
## which fuse the moment a unit carries two of them. Every pair of the five
## components makes a distinct full relic (15 recipes), so what you build is a
## real decision rather than a random handout — and a unit can hold three of
## them, so a carry can be assembled deliberately.
##
## The data (COMPONENTS, COMPONENT_IDS, ITEMS, ITEM_IDS, MAX_ITEMS, RECIPES) is
## in `C`. An Item is a Dictionary {id, name, short, desc, color, buff,
## component?}.
class_name Items


static var COMPONENTS: Dictionary:
	get:
		return C.COMPONENTS
static var COMPONENT_IDS: Array:
	get:
		return C.COMPONENT_IDS
static var ITEMS: Dictionary:
	get:
		return C.ITEMS
static var ITEM_IDS: Array:
	get:
		return C.ITEM_IDS
static var RECIPES: Dictionary:
	get:
		return C.RECIPES
static var MAX_ITEMS: int:
	get:
		return int(C.MAX_ITEMS)


## Recipe key for a pair of components, order-independent.
static func recipeKey(a: String, b: String) -> String:
	var pair := [a, b]
	pair.sort() # plain strings: JS's default sort is code-unit order, as here
	return "+".join(pair)


## Look up a component or a full relic by id (null when it is neither).
static func itemDef(id: String):
	var it = C.ITEMS.get(id)
	return it if it != null else C.COMPONENTS.get(id)


static func isComponent(id: String) -> bool:
	return C.COMPONENTS.get(id) != null


## The relic two components fuse into (null if either isn't a component).
static func fusionOf(a: String, b: String):
	if not isComponent(a) or not isComponent(b):
		return null
	return C.RECIPES.get(recipeKey(a, b))


## Fuse the first pair of loose components on a unit into their full relic,
## in place. Returns the relic made, or null if there wasn't a pair.
static func fuseComponents(items: Array):
	var idx: Array = []
	var i := 0
	while i < items.size() and idx.size() < 2:
		if isComponent(items[i]):
			idx.append(i)
		i += 1
	if idx.size() < 2:
		return null
	var made = fusionOf(items[idx[0]], items[idx[1]])
	if made == null:
		return null
	items.remove_at(idx[1]) # remove the later index first so the earlier stays valid
	items.remove_at(idx[0])
	items.append(made)
	return made


## Every relic a component can still become, for the "builds into" tooltip.
static func buildsInto(componentId: String) -> Array:
	if not isComponent(componentId):
		return []
	var out: Array = []
	for other in C.COMPONENT_IDS:
		var r = fusionOf(componentId, other)
		if r != null and not out.has(r):
			out.append(r)
	return out


## `u` is an Entity or a Dictionary with {maxHp, hp, attack, armor, speed}.
static func applyItems(u, itemIds: Array) -> void:
	for id in itemIds:
		var it = itemDef(id)
		if it != null:
			Traits.applyBuff(u, it.buff)
