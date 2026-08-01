# Making a map

Everything here happens in-game. You never edit a file to make a map, and you
never need me to add anything for a map to work.

---

## The five-minute version

```
/arena workshop            an empty, permanently lit world, you in creative
```

Build a floor and some walls. Then:

```
/arena marker spawn        place it where players start
/arena marker horde        place three or four, on the walls, facing inward
/arena marker extract      place it somewhere you have to walk to
/arena marker dealer       place it, then edit lines 2 and 3
```

Mark out the map and play it:

```
/arena wand                left-click one corner, right-click the other
/arena validate            tells you what is missing, in English
/arena test aztecabyss:classic
```

`/arena stop` ends it and gives you creative back.

When you like it:

```
/arena create myarena      writes the .nbt, validates, gives it a manifest
/arena meta myarena title The Drowned Chapel
```

The file lands in `<world>/generated/abyss_local/structures/myarena.nbt`. That
file *is* the map — signs, shops and all. Copy it into a datapack to share it.

---

## Markers

A marker is a sign whose first line is a kind in brackets. Lines 2–4 are
`key=value` pairs in any order; a bare word becomes `value`, so `[Perk] ironhide`
and `[Perk] id=ironhide` mean the same thing. Facing comes from the sign.

`/arena marker <kind>` hands you one already written.

| Marker | Options | Does |
|---|---|---|
| `[Spawn]` | — | where players arrive. **Required** |
| `[Horde]` | `area=` | a way in. **At least one required** |
| `[Extract]` | `radius=` | stand on it between rounds to bank the run |
| `[Door]` | `area=` `cost=` `currency=` `width=` `height=` | buys an area open, punches the doorway |
| `[Dealer]` | *(positional — see below)* | sells an item |
| `[Box]` | `price=` | random weapon |
| `[Perk]` | *any effect id* `price=` `amp=` | that effect for the rest of the run |
| `[Upgrade]` | `price=` | climbs the material ladder |
| `[Loot]` | `tier=` | supply cache, once per round |
| `[Zone]` | `effect=` `radius=` `amp=` | effect while you stand in it |
| `[Spawner]` | *entity id* `count=` `round=` `every=` `health=` | hand-placed enemies |
| `[Boss]` | *entity id* `every=` `health=` | boss on a cycle |

**Dealers are positional**, because they are a shop front you read at a glance:

```
[Dealer]
minecraft:crossbow
1750 points
x1
```

Line 2 is the item (bare names work — `crossbow`). Line 3 is the price, and
optionally the currency. Line 4 takes `xN` for a stack.

### Areas

A `[Horde]` with no `area=` is live from round one. Give one an area name and it
sends nothing until a `[Door]` with the same `area=` has been bought open. That
is the shape of a good map: you start in one room you can hold, and every room
you buy is more ground **and** more directions it comes from.

---

## Tuning without retyping

```
/arena look                what is this marker, and what are its options
/arena set price 1400      changes whatever marker you are looking at
/arena set area cellar
```

---

## Building faster

```
/arena copy                the wand selection
/arena paste               at your feet
/arena paste 90            rotated — build one corner, paste it four times
/arena paste 180 z         rotated and mirrored
/arena undo                put back whatever the last paste covered

/arena prefab save kiosk   keep a piece forever
/arena prefab place kiosk  stamp it into any map, markers included
```

---

## Rules

Three ship with the mod: `aztecabyss:classic`, `aztecabyss:brutal`,
`aztecabyss:scavenger`. Copy one. Your own go in a datapack at
`data/<you>/abyss_ruleset/<name>.json`, and `/reload` applies them **without a
restart**.

```json
{
  "rounds": {
    "mode": "endless",
    "base_count": 8,
    "health":   { "per_round": 0.22, "soften_after": 18, "exponent": 1.05 },
    "breather": { "start_ticks": 160, "min_ticks": 40 }
  },
  "director": { "enabled": true, "target_pressure": 0.6 },
  "powerups": { "one_in": 45 },
  "economy":  { "enabled": true, "currency": "points", "kill": 60 },
  "mobs": [
    { "id": "minecraft:husk", "weight": 20, "from_round": 6,
      "attributes": { "max_health": 60, "attack_damage": 7 } }
  ]
}
```

Every field has a default, so a four-line ruleset is valid. Every field is
clamped, so a typo makes a hard map rather than a dead server.

`/arena rules <id>` prints what a curve actually works out to at rounds 1, 10, 25
and 50 — and warns about any key it did not recognise, which is how you catch
`basecount` when you meant `base_count`.

### Currencies

```json
"currencies": [
  { "id": "scrap", "name": "Scrap", "symbol": "⚙",
    "backing": "item", "item": "minecraft:iron_nugget" }
]
```

- `virtual` — a number. Cannot be dropped or traded, dies with you.
- `item` — a real stack. Takes inventory space, can be thrown to a teammate, and
  lies on the floor where you died.
- `experience` — buying a weapon competes with enchanting one.

Name a currency on a dealer's price line (`1750 scrap`) or leave it off for the
map's default.

### Scripts

```json
"script": [
  { "on": "round_start",
    "when": { "round": { "every": 10 } },
    "do": [ { "title": { "main": "§4SOMETHING ELSE" } },
            { "spawn": { "id": "minecraft:warden", "at": "boss", "health": 600 } } ] }
]
```

**Events:** `run_start`, `round_start`, `round_end`, `mob_killed`, `extracted`.
**Conditions:** `round` with `equals` / `at_least` / `at_most` / `every`, and `area_open`.
**Actions:** `message`, `actionbar`, `title`, `sound`, `effect`, `give`, `spawn`,
`award`, `open_area`, `set_block`, `end_run`.

There are no loops, variables or arithmetic, on purpose — a map you download
cannot hang your server.

---

## Playing

```
/arena play <ruleset>      runs on the build around you
/arenajoin                 join a run in progress (no op needed)
/arena status              what the engine currently believes
/arena director            how much pressure it thinks you are under
/arena stop
```

Everyone stood inside the map when it starts is in it.

---

## When something is wrong

`/arena validate` first — it catches a missing spawn, no ways in, a sealed area
with no door, and a dealer whose price will not parse.

`/arena status` second. Almost every "it does not work" is really "it is doing
something I cannot see", and status distinguishes a stalled round from a horde
that cannot reach you from a door that changed nothing. If nothing has died for
a while it says so.
