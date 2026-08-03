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
/arena marker pen          optional: behind a horde marker, so they walk in unseen
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
file *is* the map — signs, shops and all.

### Sharing it

Copy the `.nbt` into a datapack and add a manifest beside it so it arrives with
its name and its rules rather than as anonymous geometry:

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

Drop it in `saves/<world>/datapacks/`, `/reload`, and it appears in
`/arena maps`. `/arena load blood_harbour` places it by name.

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
| `[Pen]` | — | put one behind a `[Horde]` and the horde arrives there, out of sight, and walks in |
| `[Extract]` | `radius=` | stand on it between rounds to bank the run |
| `[Door]` | `area=` `cost=` `currency=` `width=` `height=` | buys an area open, punches the doorway |
| `[Dealer]` | *(positional — see below)* | sells an item |
| `[Box]` | `price=` | random weapon |
| `[Perk]` | *any effect id* `price=` `amp=` | that effect for the rest of the run |
| `[Upgrade]` | `price=` | climbs the material ladder |
| `[Loot]` | `tier=` | supply cache, once per round |
| `[Zone]` | `effect=` `radius=` `amp=` | effect while you stand in it |
| `[Spawner]` | *entity id* `count=` `round=` `every=` `health=` `damage=` | hand-placed enemies |
| `[Boss]` | *entity id* `every=` `health=` | boss on a cycle |
| `[Trap]` | `cost=` `damage=` `radius=` `seconds=` `cooldown=` | pay to make a piece of the map lethal for a while |
| `[Teleport]` | `id=` | two pads sharing an id link to each other |
| `[Objective]` | `defend hp=` / `hold seconds=` / `collect count= item=` `radius=` `fail=end` | something to do besides survive |

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
/arena mobs                every entity a spawner or mob table can use
/arena mobs husk           narrow it
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

### Downed, and special rounds

```json
"downed": { "enabled": true, "bleedout_seconds": 30, "revive_seconds": 5 },
"special_rounds": [
  { "every": 5,  "role": "runner", "title": "§cTHEY ARE FAST" },
  { "every": 10, "role": "brute",  "title": "§4HEAVY" },
  { "every": 7,  "no_powerups": true, "title": "§8NO HELP" }
]
```

Downed needs someone able to reach you — solo death stays final unless you set
`"solo": true`. Special rounds filter the mob table you already wrote, so give
your mobs `role`s for them to select on.

When two apply at once the **rarer one wins** — round 10 above is heavy, not
fast, because every tenth round is also a fifth. Order in the file does not
matter. A filter that matches nothing in your mob table is ignored rather than
stalling the round.

### Pools — what the machines hand out

```json
"pools": {
  "box": [
    { "id": "minecraft:trident", "weight": 1 },
    { "id": "minecraft:crossbow", "weight": 4, "enchant": 2 },
    { "id": "minecraft:arrow", "weight": 8, "count": "16-32" }
  ],
  "loot_1": [ "minecraft:bread", "minecraft:arrow" ]
}
```

Weights are relative and need not add up to anything. `count` is a number or a
`low-high` range. `enchant` is a vanilla enchanting level applied on the way out.
A bare string is a weight-1 entry, so a simple pool is a list of names.

The Box reads `box`, a `[Loot] tier=2` cache reads `loot_2`, and both fall back to
the built-in list if you have not defined one. Point a machine somewhere else with
`pool=`, or write it inline for a one-off:

```
[Box]
price=950 pool=late_game
[Box]
price=600 items=arrow*8,cooked_beef*4,iron_sword
```

Most specific wins: `items=` beats `pool=` beats the default.

**This is the single biggest lever on how a map feels.** A flooded dock where the
Box only gives tridents, a siege map where it only gives arrows and bread, a
low-tech map with no netherite in it at all — none of that was expressible before,
and all of it changes a map more than the round curve does.

### Dealers, in full

A dealer has **eight** lines: the four on the front, then the four on the back.
The front stays the shop front a player reads across a room; the back is where the
detail goes.

```
front:                      back:
[Dealer]                    enchant=3
minecraft:crossbow          round=8
1750 points                 limit=2
x1
```

- `enchant=` a vanilla enchanting level, applied when sold
- `round=` sealed until that round — somewhere for a map to get to
- `limit=` how many the whole squad may buy this run

Limits are per run and per sign, not per player: one netherite sword means the
squad gets one, which is a decision they have to make together.

### Free mode — a game that is not an arena

```json
"rounds": { "mode": "free" }
```

No rounds. No horde. Nothing spawns on its own and no wave ever begins — the map
is driven entirely by regions, variables and script, and it ends when the script
says so.

The engine used to **refuse** a map with no `[Horde]` markers, which quietly made
round-survival not one mode but the only thing that could exist. A race, an escape
room, a heist and a puzzle all have no horde by definition.

Everything else still works — shops, doors, traps, teleports, perks — because none
of it was ever really about rounds.

`tick` (once a second) and `set_bar` work in **both** modes. They were briefly
free-mode only, which made round mode a second-class citizen of its own engine for
no reason but the order the two were built in. In round mode `set_bar` keeps your
text and appends the round number.

```json
{ "on": "tick", "when": { "seconds": { "at_least": 300 } },
  "do": [ { "lose": { "title": "§4§lTOO SLOW" } } ] }
```

`aztecabyss:heist` ships as a worked example: three idols, one way out, five
minutes. It is about thirty lines and it is a complete game.

### Respawning, and kits

```json
"respawn": { "enabled": true, "seconds": 5 },
"kit": "loadout",
"pools": { "loadout": [
  { "id": "minecraft:iron_sword", "enchant": 1 },
  { "id": "minecraft:arrow", "count": "32" }
] }
```

**Off by default.** In a survival arena death being final is the whole tension.
It is equally the wrong answer for anything competitive — capture the flag where
the first death removes a player is not capture the flag, it is attrition with a
flag in it. Both are correct; the map says which.

There is no death screen. You are healed, sent to your side's spawn, and given a
few seconds of resistance and slowness so a spawn camp is not a strategy.

`kit` names a pool and is handed out on **every** spawn, first and after. A kit
gives you every entry in the pool rather than drawing from it — a loadout is a
list of what you get, and rolling it would start two players on the same team with
different equipment for reasons invisible to both.

This was the one thing about a map an author could not touch at all.

### Teams — sides

The engine had exactly one relationship between players: everybody on the same
side, permanently. That is not a limitation of the arena mode, it is the absence
of a concept — and it ruled out every game where the answer to "who is against
you" is anything but the horde.

```
[Spawn]
team=red
```

A `[Spawn]` carrying `team=` is that side's spawn. A map that declares teams but
marks only one spawn still plays — it is just symmetrical.

**Actions:** `join_team`, `balance_teams`, `team_message`, `add_team_var`,
`set_team_var`, `teleport_to_spawn`
**Conditions:** `team` (which side triggered this), `team_var`

```json
{ "on": "region_enter", "when": { "region": "blue_flag", "team": "red" },
  "do": [ { "set_my_var": { "name": "carrying", "to": 1 } },
          { "team_message": { "team": "blue", "text": "§c⚑ They have your flag." } } ] }
```

Membership rides vanilla's scoreboard teams, which buys name colouring, glow, and
**friendly fire off** without reimplementing any of them. `balance_teams` always
fills the smallest side rather than round-robin, so someone joining a run already
under way lands where they are needed.

A team score is a normal variable under a prefixed name — `team:red:score` — so
everything that already reads variables keeps working.

`aztecabyss:capture` ships as a worked example: capture the flag, three to win.

### Variables — what a run remembers

The script layer could do a lot and could not **count**. Every rule was a reflex,
so the only state a map had was the round number and which doors were open. That
is enough for a hundred arenas and exactly one game.

```json
"script": [
  { "on": "region_enter", "when": { "region": "vault" },
    "do": [ { "add_var": { "name": "loot", "by": 1 } },
            { "actionbar": "You have the idol" } ] },

  { "on": "region_enter", "when": { "region": "exit", "var": { "name": "loot", "at_least": 3 } },
    "do": [ { "win": { "title": "§6§lOUT WITH ALL THREE" } } ] }
]
```

- `set_var` / `add_var` — the squad's, e.g. how many flags the team holds
- `set_my_var` / `add_my_var` — one person's, e.g. how many keys *you* carry
- `var` / `my_var` in a `when`, with `equals` / `at_least` / `at_most`
- `"total": true` on a `var` sums a per-player name across everybody

Integers only, no expressions — on purpose. That covers counting, flags, timers
and scores, and it cannot be used to hang a server. A map you downloaded from a
stranger stays safe to run.

### Later — delays and repeating timers

The script layer could say *"when this happens, do that"* and had no way to say
*"in thirty seconds, do that"*. Every countdown, delayed gate, staged reveal and
timed penalty had to be faked by polling `tick` against a variable you incremented
yourself — a counter in every map that merely wanted a pause.

```json
{ "delay": { "seconds": 30, "do": [ { "open_area": "vault" } ] } }

{ "every": { "seconds": 10, "times": 5,
             "do": [ { "spawn": { "id": "minecraft:husk", "at": "boss" } } ] } }
```

`delay` fires once. `every` repeats — leave `times` off and it runs until the run
ends, which is what a heartbeat wants and is safe because ending a run clears the
queue with it.

Both work in **both** modes. A countdown is not a free-mode idea.

`/arena status` shows how many are queued, so a map that has scheduled something
into the far future is visible rather than mysterious.

### Blocks — reacting to what somebody touched

Rounds answer *when*. Regions answer *where somebody is standing*. Neither answers
*what did they just pull* — so a map could ask you to reach a place but never to
operate anything.

```json
{ "on": "use_block", "when": { "block": "minecraft:lever", "region": "lever_a" },
  "do": [ { "set_var": { "name": "lever_a", "to": 1 } } ] }
```

**Events:** `use_block`, `break_block`
**Condition:** `block` — the block id, `minecraft:` optional

The `region` here is resolved from the **block's** position, not the player's,
which is the difference between "the lever in the vault" and "a lever, pulled by
someone who happens to be standing in the vault".

Interactions are **not** swallowed — the lever still flips, the door still opens.
A trigger that ate the interaction would mean every switch needing a rule just to
behave like a switch.

`aztecabyss:vault` ships as a worked example: three levers, then the door.

### Regions — reacting to *where*, not just *when*

```
[Region]
id=vault radius=5 height=4
```

Fires `region_enter` and `region_leave`, filtered with `"region": "vault"`.
**Edge-triggered**: entering fires once, leaving fires once, standing still fires
nothing — otherwise "give them a diamond at the vault" means a diamond every tick.

This is the other half of being a game rather than an arena. Checkpoints, capture
points, finish lines, the room you are not supposed to be in yet — all of them are
a region and a variable.

### Winning on your own terms

```json
{ "win":  { "title": "§6§lYOU MADE IT" } }
{ "lose": { "title": "§4§lTHE VAULT IS EMPTY" } }
```

`end_run` still exists and still says nothing about whether that was a good thing.
A game needs to be winnable on its own terms.

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

Mobs can take a `role` as well as attributes, for behaviour numbers cannot
express: `runner` closes distance, `brute` shrugs off hits but lumbers, `leaper`
comes over what you were hiding behind, `armoured` is very hard to hurt.

Buying from a `[Dealer]` you already own **repairs and reloads** it instead of
handing you a duplicate — which is what keeps the shop worth visiting after
round ten.

**Events:** `run_start`, `round_start`, `round_end`, `mob_killed`, `extracted`,
`objective_complete`, `objective_failed`.
**Conditions:** `round` with `equals` / `at_least` / `at_most` / `every`, and `area_open`.
**Actions:** `message`, `actionbar`, `title`, `sound`, `effect`, `give`, `spawn`,
`award`, `open_area`, `set_block`, `end_run`.

There are no loops, variables or arithmetic, on purpose — a map you download
cannot hang your server.

---

## Seeing what your script is doing

A map with forty rules is unbuildable blind, and everything the script layer does
is invisible when it works and identical to nothing at all when it does not.

```
/arena trace               every rule that fires or is skipped, live
/arena vars                everything the run currently remembers
/arena teams               who is on which side
/arena rules <id>          unknown events, conditions and actions
```

`/arena rules` is the one to run first after writing a script. A mistyped action
hits a default that does nothing, and a mistyped condition is ignored — both by
design, so a map written for a later engine still runs on an earlier one. Both
mean a rule that loads perfectly and never does anything. They are now reported:

```
⚠ script: rule 7: unknown action "add_vars" — does nothing
⚠ script: rule 12: no event called "region_entered" — it will never fire
```

`/arena trace` is the one to run second. It prints, as it happens:

```
[script] fire region_enter (blue_flag) for Steve
[script] skip tick — conditions not met
```

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
