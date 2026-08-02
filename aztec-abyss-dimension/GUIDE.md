# The Arena Engine — full guide

From an empty world to a map someone else can play, in order, with nothing
skipped.

Everything here happens in-game. You never edit a file to make a map work, and
you never need the mod changed to add one.

`/arena` needs op (permission level 2). `/arenajoin` deliberately does not, so
players can join a run without being trusted with the editor.

---

## Part 1 — Somewhere to build

You can build a map anywhere: the overworld, a superflat, your survival base.
The engine does not care where the blocks are, only what shape they make.

But there is a dimension built for it:

```
/creator <password>        the first time
/creator                   every time after
```

Map Creator is on the map picker too, under the Maze. It is password-gated
because entering it means creative mode and a tool that rewrites regions of the
world — on a server that is not something to hand to whoever clicks a button.
Type the password once and you are remembered from then on. Operators never need
it, and can run `/arena workshop` directly.

The password lives in the mod config as `creatorPassword`. An operator can make
a player type it again with `/creator lock <player>`.

You arrive in a flat world — bedrock, stone, dirt, grass from `y=0`, so ground is
`y=4` — permanently daylight, no weather, no mobs, no hunger, and you are in
creative. Nothing wanders in and ruins a build, and nothing is dark unless you
made it dark.

To come back out, `/arena stop` if a run is going, then any normal means of
travel — the workshop is a real dimension, not a menu.

**Build a floor first.** The engine spawns mobs on solid ground it can find; a
map floating in void with no floor under the spawn points will look correct and
send nothing.

---

## Part 2 — Setting the location

The engine needs to know where the map *is* — where it stops being your map and
starts being the rest of the world. Two ways.

### The wand — precise, and what you should use

```
/arena wand
```

You get a **Map Wand** (a blaze rod that knows what it is).

- **Left-click** a block — first corner
- **Right-click** a block — second corner

It reports the span as you go: `48 × 24 × 40`. The two corners are opposite ends
of a box, so pick a low corner outside one wall and a high corner outside the
opposite wall — **include the roof and the floor**, not just the footprint. A
selection that clips the ceiling saves a map with no ceiling.

### The radius — quick, and a guess

Most commands take an optional radius instead, measured from where you stand:

```
/arena scan 64
/arena validate 64
/arena play aztecabyss:classic 64
```

Fine for testing. Bad for saving — a radius either clips the far corner of your
build or drags in a hundred blocks of whatever was next to it.

### Checking what it can see

```
/arena scan
```

Lists every marker inside the region and where it is. This is the ground truth:
if a marker is not in this list, the engine cannot see it, and no amount of it
being obviously right on the wall in front of you will change that.

---

## Part 3 — Markers, which are just signs

**A marker is a sign whose first line is a kind in brackets.** That is the whole
system. `[Horde]` on line one and that sign is a spawn point.

Lines 2–4 are `key=value` pairs in any order. A bare word becomes `value`, so
`[Perk] ironhide` and `[Perk] id=ironhide` are the same thing. Which way a
marker faces comes from which way the sign faces.

You do not have to type them:

```
/arena marker horde
```

hands you a sign already written. Place it. That is the step.

Sign text lives in the block entity, and the item carries block-entity data into
the block it places — so the sign arrives pre-written and there is nothing to
spell wrong.

### Editing a marker without breaking it

Look at the sign and:

```
/arena look                 what is this, and what options does it have
/arena set price 1400       change one key on the sign you are looking at
/arena set area cellar
```

`/arena look` is the one to reach for when a marker is not doing what you
expected — it prints what the engine parsed, not what you think you wrote.

---

## Part 4 — The markers, one at a time

### The two that are required

**`[Spawn]`** — where players arrive. One per map; extra ones are ignored, and
validate will tell you so.

**`[Horde]`** — a way in. Mobs come from here. At least one, and honestly at
least four for a map that plays well.

Put horde markers **on walls, facing into the room**. The facing is the
direction mobs walk when they arrive. A horde marker facing a wall spawns mobs
into a wall.

```
[Horde]
area=start
```

### Making them arrive properly

**`[Pen]`** — put one behind a `[Horde]` and mobs materialise at the pen instead
of at the horde marker, then walk in. The difference is enormous: mobs stop
popping into existence in front of you and start *coming from somewhere*. Build
a small dark closet behind each wall, put a pen in it, and the map gains
atmosphere for free.

### Spawning things by hand

**`[Spawner]`** — enemies you place, rather than the round system's.

```
[Spawner]
minecraft:skeleton
count=4 round=6 every=3
health=40 damage=6
```

- `count=` how many
- `round=` first round it fires
- `every=` fire again every N rounds after that
- `health=` `damage=` override that entity's stats

**`[Boss]`** — one big thing on a cycle.

```
[Boss]
minecraft:warden
every=10 health=800
```

**Any entity in the game works, including from other mods.** To find out what
you can write:

```
/arena mobs                 everything available
/arena mobs husk            narrow it
```

Use this rather than guessing. An entity id that does not exist spawns nothing,
silently, forever — which is indistinguishable from a spawner set to a later
round. `/arena validate` now catches it, but `/arena mobs` catches it sooner.

### Money and shops

**`[Dealer]`** — sells an item. **Positional**, because it is a shop front you
read at a glance:

```
[Dealer]
minecraft:crossbow
1750 points
x1
```

- Line 2: the item. Bare names work — `crossbow`, `iron_sword`.
- Line 3: the price, and optionally which currency.
- Line 4: `xN` for a stack.

Buying something you **already own repairs and reloads it** instead of handing
you a duplicate — full durability back, and arrows if it is a bow or crossbow.
That is what keeps the shop worth walking to after round ten.

**`[Box]`** — random weapon for a price. `price=950`.

**`[Perk]`** — a permanent effect for the rest of the run.

```
[Perk]
minecraft:health_boost
price=2500 amp=2
```

Any effect id works. `amp=` is the strength.

**`[Upgrade]`** — climbs the material ladder on what you are holding: stone →
iron → diamond → netherite. `price=5000`.

**`[Loot]`** — a supply cache, once per round. `tier=1`.

### Opening the map up

**`[Door]`** — this is the important one.

```
[Door]
area=cellar
cost=1500 width=3 height=3
```

Pay the cost and the doorway **punches itself open** — you do not build the hole,
the engine removes the blocks. And every `[Horde]` tagged `area=cellar` goes
live at the same moment.

**This pairing is what makes a map a map.** A `[Horde]` with no `area=` is live
from round one. Give one an area name and it sends nothing until the matching
door is bought. So you start in one room you can hold, and every door you buy is
more ground **and** more directions it comes at you from. That tension — open up
or stay small — is the entire genre, and it falls out of two signs agreeing on a
word.

Spell the area the same on both. Different spellings are two different areas and
neither will complain.

### Making the space do something

**`[Zone]`** — an effect while you stand in it.

```
[Zone]
effect=minecraft:slowness
radius=6 amp=1
```

Water you wade through, gas that poisons, a room that slows you down.

**`[Trap]`** — pay to make part of the map lethal for a while.

```
[Trap]
cost=1000 damage=12
radius=4 seconds=8 cooldown=45
```

**`[Teleport]`** — two pads sharing an `id=` link to each other.

```
[Teleport]
id=a
```

You need **two**. One pad on its own is furniture. Put the second one somewhere
far and expensive, and the pair becomes an escape route worth the walk.

### Winning, and having something to do

**`[Extract]`** — stand on it between rounds to bank the run. Without one, the
map has no way to win, only a way to lose. `radius=` how close counts.

**`[Objective]`** — something besides survival.

```
[Objective]
defend hp=600
radius=5 fail=end
```

Three kinds:
- `defend hp=600` — a thing the horde chews on. Mobs stood next to it damage it,
  once a second. If it breaks, you have lost it.
- `hold seconds=60` — someone has to stand in it and stay there. Being driven
  off costs you time, not the whole attempt.
- `collect count=8 item=minecraft:gold_ingot` — bring things to it. Right-click
  the marker to hand in what you are carrying.

`fail=end` makes losing it end the run. Leave it off and losing it just stops
the payout — which lets a map have a side task without turning every map into a
single point of failure.

**`[Powerup]`** — where drops can land.

---

## Part 5 — Play it, now

You do not have to save anything to test.

```
/arena validate             what is missing, in English
/arena test aztecabyss:classic
```

`test` runs it around you and gives creative back when you stop. Build, test,
adjust, test, without leaving the world.

```
/arena stop
```

Run it properly with:

```
/arena play aztecabyss:classic
/arena play aztecabyss:brutal 96
```

Everyone stood inside the map when it starts is in it. Anyone else:

```
/arenajoin
```

### Watching what it thinks

```
/arena status               what the engine currently believes
/arena director             how much pressure it reckons you are under
```

`status` is the answer to almost every "it does not work", because almost every
one of those is really "it is doing something I cannot see". It distinguishes a
stalled round from a horde that cannot path to you from a door that changed
nothing. If nothing has died in a while, it says so.

---

## Part 6 — Saving it

```
/arena create myarena
```

Writes `<world>/generated/abyss_local/structures/myarena.nbt`, validates it, and
gives it a manifest.

**That file is the map.** Not the geometry with a config beside it — everything.
Sign text rides inside structure NBT, so every dealer, every price, every door
cost and every spawner's health override travels *in the file*. Hand it to
someone and the shop layout arrives intact.

Give it a name people will see:

```
/arena meta myarena title The Drowned Chapel
/arena meta myarena author you
/arena meta myarena blurb A flooded dock. The tide decides where you can stand.
/arena meta myarena difficulty HARD
```

Bring one back:

```
/arena load myarena
/arena maps                 everything available
/arena info myarena         what a map says about itself
```

---

## Part 7 — Building faster

```
/arena copy                 the wand selection
/arena paste                at your feet
/arena paste 90             rotated — build one corner, paste it four times
/arena paste 180 z          rotated and mirrored
/arena undo                 put back whatever the last paste covered
```

Rotation is in degrees. `undo` covers the last paste, which is what makes
pasting safe to experiment with.

Keep a piece forever:

```
/arena prefab save kiosk
/arena prefab place kiosk
/arena prefab place kiosk 90
```

Prefabs carry their markers. Build one good shop alcove — dealer, lighting,
the lot — save it, and stamp it into every map you ever make.

Prefabs are kept separate from maps on purpose. A parts library and a map
library get browsed for different reasons.

---

## Part 8 — Rules: how it feels

The map is the *shape*. The **ruleset** is the *feel*, and they are separate so
one build can play three ways.

Three ship with the mod: `aztecabyss:classic`, `aztecabyss:brutal`,
`aztecabyss:scavenger`.

Your own go in a datapack at `data/<you>/abyss_ruleset/<name>.json` inside
`saves/<world>/datapacks/`. **`/reload` applies them without a restart** — you
rebalance between rounds.

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
    { "id": "minecraft:husk", "weight": 20, "from_round": 6, "role": "grunt",
      "attributes": { "max_health": 60, "attack_damage": 7 } }
  ]
}
```

Every field has a default, so a four-line ruleset is valid. Every field is
clamped, so a typo makes a hard map rather than a dead server.

```
/arena rules brutal
```

prints what your curve actually works out to at rounds 1, 10, 25 and 50 — and
**warns about any key it did not recognise**, which is how you catch `basecount`
when you meant `base_count`. Lenient parsing is what lets a short file work; it
is also what silently swallows typos, so unrecognised keys are reported rather
than ignored.

### Mob roles

`role` expresses what numbers cannot — you can already set health and speed
directly, so a role is a behaviour package:

- `runner` closes distance
- `brute` shrugs off hits but lumbers
- `leaper` comes over the thing you were hiding behind
- `armoured` is very hard to hurt

### Downed and revive

```json
"downed": { "enabled": true, "bleedout_seconds": 30, "revive_seconds": 5, "solo": false }
```

You drop instead of dying, glowing so you can be found across a dark room. A
teammate stands near you for five seconds and you are back at half health.
Progress is kept rather than reset, so a rescuer driven off for a moment does
not start again.

Solo death stays final unless you set `"solo": true` — being your own rescue is
not a mechanic, it is a wait.

### Special rounds

```json
"special_rounds": [
  { "every": 5,  "role": "runner", "title": "§cTHEY ARE FAST" },
  { "every": 10, "role": "brute",  "title": "§4HEAVY" },
  { "every": 7,  "no_powerups": true, "title": "§8NO HELP" }
]
```

They filter the mob table you already wrote, so your mobs need `role`s to select
on. **When two apply at once the rarer one wins** — round 10 is heavy, not fast,
because every tenth round is also a fifth. Order in the file does not matter. A
filter matching nothing is ignored rather than stalling the round.

### Currencies

```json
"currencies": [
  { "id": "scrap", "name": "Scrap", "symbol": "⚙",
    "backing": "item", "item": "minecraft:iron_nugget" }
]
```

The backing changes how people play:

- `virtual` — a number. Cannot be dropped or traded, dies with you.
- `item` — a real stack. Takes inventory space, can be thrown to a teammate,
  **and lies on the floor where you died.**
- `experience` — buying a gun competes with enchanting one.

Name a currency on a dealer's price line (`1750 scrap`) or leave it off for the
map's default.

```
/wallet                     what you are carrying
/wallet give scrap 500      op only
```

### Scripts

For anything the numbers cannot say:

```json
"script": [
  { "on": "round_start",
    "when": { "round": { "every": 10 } },
    "do": [ { "title": { "main": "§4SOMETHING ELSE" } },
            { "spawn": { "id": "minecraft:warden", "at": "boss", "health": 600 } } ] }
]
```

**Events:** `run_start`, `round_start`, `round_end`, `mob_killed`, `extracted`,
`objective_complete`, `objective_failed`

**Conditions:** `round` with `equals` / `at_least` / `at_most` / `every`, and
`area_open`

**Actions:** `message`, `actionbar`, `title`, `sound`, `effect`, `give`,
`spawn`, `award`, `open_area`, `set_block`, `end_run`

No loops, no variables, no arithmetic — deliberately. A map you downloaded from
a stranger cannot hang your server.

---

## Part 9 — Sharing it

Copy the `.nbt` into a datapack with a manifest beside it, so it arrives with a
name and its rules rather than as anonymous geometry:

```
MyMapPack/
├── pack.mcmeta
└── data/mypack/
    ├── abyss_arena/blood_harbour.json     ← the manifest
    ├── abyss_ruleset/harbour.json         ← optional, its own rules
    └── structure/blood_harbour.nbt        ← the build
```

```json
{
  "title": "Blood Harbour",
  "author": "you",
  "blurb": "A flooded dock. The tide decides where you can stand.",
  "difficulty": "HARD",
  "ruleset": "mypack:harbour",
  "structure": "mypack:blood_harbour"
}
```

Drop it in `saves/<world>/datapacks/`, `/reload`, and it shows up in
`/arena maps`. `/arena load blood_harbour` places it by name.

---

## Part 10 — When something is wrong

**`/arena validate` first.** It catches a missing spawn, no ways in, a sealed
area whose door does not exist, an entity id that is not a real entity, a dealer
whose price will not parse, an objective naming an item that does not exist, and
a teleport pad with no partner.

**`/arena scan` second** — if a marker is not in that list, the engine cannot see
it. Wrong region, or the sign is outside your selection.

**`/arena look` third** — at the sign itself. It prints what was parsed, which is
often not what you meant.

**`/arena status` fourth** — for anything that starts and then goes wrong.

Common ones:

| Looks like | Usually is |
|---|---|
| Round starts, nothing comes | Every horde is behind an unopened `area=`, or no floor under the spawn points |
| Mobs appear inside walls | Horde marker facing a wall, or a pen with no room |
| A door does nothing | Its `area=` is spelled differently to the hordes' |
| A spawner never fires | Entity id does not exist — run `/arena mobs` |
| The shop hands out duplicates | Working as intended for an undamaged melee weapon |
| Ruleset change did nothing | Typo'd key — run `/arena rules <id>` and read the warnings |

---

## Command reference

Everything under `/arena` needs op. `/arenajoin` and bare `/wallet` do not.

**Getting in** — `/creator [password]` · `/creator lock <player>` *(op)*

**Region** — `wand` · `scan [radius]` · `validate [radius]`

**Authoring** — `workshop` · `marker <kind>` · `look` · `set <key> <value>` ·
`mobs [filter]`

**Building** — `copy` · `paste [rotate] [mirror]` · `undo` ·
`prefab save <name>` · `prefab place <name> [rotate] [mirror]`

**Files** — `create <name>` · `save <name> [radius]` · `load <name>` · `maps` ·
`info <name>` · `meta <name> <field> <value>`

**Playing** — `test [ruleset]` · `play [ruleset] [radius]` · `stop` · `status` ·
`director` · `rules [id]` · `/arenajoin` · `/wallet` · `/wallet give <currency> <amount>`

---

## A first map, start to finish

```
/arena workshop
```

1. Floor, 40×40. Walls around it. Roof.
2. A second room next door, sealed off.
3. `/arena marker spawn` — middle of room one.
4. `/arena marker horde` ×4 — on the four walls of room one, facing in.
5. `/arena marker pen` ×4 — in small closets behind each.
6. `/arena marker door` — on the wall between the rooms. Edit: `area=back cost=1500`.
7. `/arena marker horde` ×2 — in room two. Edit each: `area=back`.
8. `/arena marker dealer` — room one. Lines: `minecraft:iron_sword`, `750 points`.
9. `/arena marker box` — room two, `price=950`. Worth opening the door for.
10. `/arena marker extract` — room two.
11. `/arena wand`, left-click one bottom corner outside, right-click the opposite top corner outside.
12. `/arena validate` — fix what it says.
13. `/arena test aztecabyss:classic`
14. `/arena stop`, adjust, repeat.
15. `/arena create myfirstmap`
16. `/arena meta myfirstmap title My First Map`

That is a complete, playable, shareable map. Everything after it is more of the
same, larger.
