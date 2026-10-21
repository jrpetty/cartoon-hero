## sim/traits.ts — Warband Tactics synergies. Each unit belongs to one or more
## traits; deploying enough *distinct* unit types of a trait activates an
## escalating buff applied to the units carrying it. Overlapping traits (e.g. a
## Spearman is both a Footman and a Pike) make drafting a real puzzle. Pure
## data + helpers, shared by the battle resolver and the screen.
##
## The data (`TRAITS`) is in `C`. A Trait is a Dictionary {id, name, color,
## types, tiers: [{at, label, buff}]}; a Buff is a Dictionary with any of
## {armor, atk, atkPct, hp, hpPct, speedPct}. An ActiveTrait is a Dictionary
## {trait, count, tier (or null), tierIndex}.
class_name Traits

## The Buff fields, in the order the TS merges them.
const BUFF_KEYS := ["armor", "atk", "atkPct", "hp", "hpPct", "speedPct"]

static var _typeTraits = null


static var TRAITS: Array:
	get:
		return C.TRAITS


## type → traits it belongs to.
static func _byType() -> Dictionary:
	if _typeTraits == null:
		var m := {}
		for t in C.TRAITS:
			for type in t.types:
				if not m.has(type):
					m[type] = []
				m[type].append(t)
		_typeTraits = m
	return _typeTraits


static func traitsOf(type: String) -> Array:
	return _byType().get(type, [])


## Given the distinct deployed unit types, which traits are active and at what
## tier. `bonus` adds virtual counts per trait id — that's how a warband banner
## (augment) makes a synergy read as two extra units.
static func activeTraits(distinctTypes: Array, bonus = null) -> Array:
	var set := {}
	for t in distinctTypes:
		set[t] = true
	var out: Array = []
	for tr in C.TRAITS:
		var count := 0.0
		for t in tr.types:
			if set.has(t):
				count += 1.0
		if bonus != null:
			count += float(bonus.get(tr.id, 0.0))
		if count < float(tr.tiers[0].at):
			continue
		var tier = null
		var tierIndex := -1
		for i in tr.tiers.size():
			if count >= float(tr.tiers[i].at):
				tier = tr.tiers[i]
				tierIndex = i
		out.append({"trait": tr, "count": count, "tier": tier, "tierIndex": tierIndex})
	return out


## Mutably apply a buff to a spawned unit (after star scaling). `u` is an
## Entity or a Dictionary with {maxHp, hp, attack, armor, speed}.
static func applyBuff(u, b: Dictionary) -> void:
	var v = b.get("armor")
	if v:
		u.armor += v
	v = b.get("atk")
	if v:
		u.attack += v
	v = b.get("atkPct")
	if v:
		u.attack = JS.round(u.attack * (1.0 + v / 100.0))
	v = b.get("hp")
	if v:
		u.maxHp += v
		u.hp += v
	v = b.get("hpPct")
	if v:
		u.maxHp = JS.round(u.maxHp * (1.0 + v / 100.0))
		u.hp = u.maxHp
	v = b.get("speedPct")
	if v:
		u.speed = JS.round(u.speed * (1.0 + v / 100.0))
