# The Abyss Engine

A round-survival engine for Minecraft where **maps and rules are content, not code**.

The point of this document: after it is built, I never touch the mod again to add a
map. You build a map in creative, save it, drop it in a folder, and it is in the
picker. Then you tune every number in it from a text file while the server is
running.

---

## 0. The one idea

Everything the mod currently hardcodes — the Temple, the Bridge, the Outpost, the
Maze — becomes an instance of three data files:

| Layer | What it is | What it answers |
|---|---|---|
| **Arena** | geometry + markers | *what does the place look like, and where are its moving parts* |
| **Ruleset** | numbers | *how hard is it, what spawns, what can you buy* |
| **Script** | triggers → actions | *what happens when* |

They are independent. One arena can be played under three rulesets. One ruleset can
run on twenty arenas. That separation is the whole engine.

---

## 1. A map is a datapack

Not a custom format, not an upload endpoint, not something I have to parse by hand.
Minecraft already has a content-delivery system that players know, that reloads
without a restart, and that is shareable as a zip.

```
BloodHarbour.zip
├── pack.mcmeta
└── data/
    └── bloodharbour/
        ├── abyss_arena/
        │   └── blood_harbour.json          ← the manifest
        ├── abyss_ruleset/
        │   └── harbour_endless.json        ← the numbers
        └── structure/
            └── blood_harbour/
                ├── piece_0.nbt             ← the actual build
                ├── piece_1.nbt
                └── piece_2.nbt
```

Drop it in `saves/<world>/datapacks/`, run `/reload`, and the map is live. No
restart, no code, no me.

**Why structure `.nbt`:** because you already have the tool. Build it in creative,
put a Structure Block down, save. Vanilla writes the file. Structure Blocks cap at
48×48×48, which is why the manifest takes a *list* of pieces with offsets — you
save a big build as a grid of pieces and the engine stitches them.

### The manifest

```json
{
  "title": "Blood Harbour",
  "blurb": "A flooded dock. The tide decides where you can stand.",
  "difficulty": "HARD",
  "colour": "#3080C0",
  "ruleset": "bloodharbour:harbour_endless",

  "pieces": [
    { "structure": "bloodharbour:blood_harbour/piece_0", "offset": [0, 0, 0] },
    { "structure": "bloodharbour:blood_harbour/piece_1", "offset": [48, 0, 0] },
    { "structure": "bloodharbour:blood_harbour/piece_2", "offset": [0, 0, 48] }
  ],

  "bounds": { "size": [96, 40, 96] }
}
```

Note what is **not** in there: coordinates for the spawn, the shops, the horde
gates, the extraction point. Those come from the build itself.

---

## 2. Markers — how a build becomes a game

This is the part that makes the whole thing work without an editor.

You place **Structure Blocks in DATA mode** inside your build, and type a string
into them. When the engine stamps the map it scans for those blocks, turns each one
into a live piece of gameplay, and deletes the marker.

Vanilla already does exactly this — it is how villages decide where the chests go.

**Grammar:** `abyss:<kind> key=value key=value`

| Marker | What it becomes |
|---|---|
| `abyss:spawn` | where players arrive |
| `abyss:extract` | the extraction glyph |
| `abyss:horde facing=north area=hall` | a way in for the horde |
| `abyss:pen` | where mobs materialise out of sight |
| `abyss:wallbuy item=minecraft:crossbow price=1200 facing=east` | a wall buy |
| `abyss:machine type=upgrade` | the weapon-upgrade bench |
| `abyss:machine type=box` | a mystery-box site |
| `abyss:machine type=draught id=ironhide` | a perk machine |
| `abyss:door area=cellar cost=1500` | a sealed area you buy open |
| `abyss:objective type=defend hp=600` | a thing that must survive |
| `abyss:zone id=flooded effect=slowness` | a region with its own rules |
| `abyss:powerup` | somewhere a drop can land |
| `abyss:loot tier=2` | a supply cache |
| `abyss:boss` | where a boss enters |
| `abyss:light` | a light the engine may turn off |

**You never type a coordinate.** You stand in the room, you place the block, you
write what it is. The engine reads the world.

---

## 2b. Signs — authoring with no tools at all

Structure-block markers are precise but invisible and awkward. So the primary
authoring surface is something everyone already knows how to use: **a sign**.

Write on a sign, and it *is* the thing. No compile, no reload, no coordinates.

```
┌──────────────────┐
│    [Dealer]      │   ← what kind of thing this is
│ minecraft:crossbow│   ← what it sells
│ 1750 points      │   ← how much, in which currency
│ quick_charge:2   │   ← optional extras
└──────────────────┘
```

Right-click it: if you can pay, you pay and you get the crossbow. The sign renders
itself in-game as a proper shop front — item name, price in the currency's own
colour and symbol, and greyed out when you cannot afford it.

**Every marker has a sign form:**

| Sign header | Does |
|---|---|
| `[Dealer]` | sells an item for a price in a currency |
| `[Upgrade]` | the weapon-upgrade bench — tiers and prices from the ruleset |
| `[Box]` | a mystery-box site |
| `[Perk]` | a perk machine — `ironhide`, `quickhand`, … |
| `[Door]` | buys open a sealed area |
| `[Spawn]` `[Extract]` `[Horde]` `[Pen]` `[Boss]` `[Loot]` | placement markers |
| `[Zone]` | names a region and gives it rules |

**The rule that makes this coherent:** *marker* signs (`[Spawn]`, `[Horde]`, `[Pen]`)
are consumed when the map is stamped — they were only ever instructions. *Interactive*
signs (`[Dealer]`, `[Door]`, `[Perk]`) stay exactly where you put them, because they
are the shop front the player reads.

And because signs are block entities, **vanilla saves their text inside the structure
`.nbt`**. Your shop layout travels with your build for free. Author it live, save it,
ship it — same object the whole way through.

---

## 2c. Currency — money is content too

Nothing in the engine assumes "points". A currency is declared, and a map may have
several at once.

```json
"currencies": [
  {
    "id": "points",
    "name": "Points",
    "symbol": "✦",
    "colour": "#FFD700",
    "backing": "virtual",
    "start": 500,
    "earn": { "hit": 10, "kill": 50, "headshot": 25, "round_clear": 100 }
  },
  {
    "id": "scrap",
    "name": "Scrap",
    "symbol": "⚙",
    "colour": "#A0A0A0",
    "backing": "item",
    "item": "minecraft:iron_nugget",
    "earn": { "kill": 1 }
  },
  {
    "id": "souls",
    "name": "Souls",
    "symbol": "☠",
    "colour": "#8040C0",
    "backing": "experience"
  }
]
```

**Three backings, and they behave genuinely differently:**

- `virtual` — a number the engine tracks. Cannot be dropped, cannot be traded,
  vanishes on death. This is the CoD-zombies model.
- `item` — a real stack in your inventory *is* the money. It can be dropped, stolen,
  hoarded, and left behind for a teammate. A map that prices everything in an item
  is a different game to one that uses points, and neither needed new code.
- `experience` — your XP. Spending money costs you levels, which puts buying a
  weapon in direct competition with enchanting one.

A dealer sign names its currency on the price line (`1750 scrap`). Leave it off and
it uses the ruleset's default. So one map can sell ammunition for scrap, weapons for
points, and perks for souls, and the only thing that changed was text on signs.

---

## 3. Rulesets — every number, in one file

```json
{
  "rounds": {
    "mode": "endless",
    "final_round": 0,
    "base_count": 6,
    "per_round": 4,
    "concurrent_cap": 120,
    "health":  { "per_round": 0.18, "soften_after": 20, "exponent": 1.045 },
    "damage":  { "per_round": 0.14, "cap": 8.0 },
    "breather": { "start_ticks": 200, "min_ticks": 60, "tighten_by_round": 40 }
  },

  "mobs": [
    {
      "id": "minecraft:zombie",
      "weight": 60,
      "from_round": 1,
      "role": "grunt",
      "attributes": { "max_health": 20, "movement_speed": 0.26, "attack_damage": 3 }
    },
    {
      "id": "minecraft:husk",
      "weight": 15,
      "from_round": 6,
      "role": "breaker",
      "attributes": { "max_health": 60, "movement_speed": 0.30, "attack_damage": 7 },
      "equipment": { "head": "minecraft:iron_helmet", "mainhand": "minecraft:iron_axe" },
      "glow_colour": "dark_red"
    }
  ],

  "economy": {
    "enabled": true,
    "start": 500,
    "hit": 10, "kill": 50, "headshot": 25,
    "strip_inventory_on_entry": true,
    "payout": "materials_only"
  },

  "catalogue": [
    { "item": "minecraft:stone_sword", "price": 500 },
    { "item": "minecraft:crossbow",    "price": 1750, "enchants": { "minecraft:quick_charge": 2 } }
  ],

  "upgrade_tiers": [2500, 5000, 8000, 12000],

  "powerups": [
    { "id": "aegis",         "weight": 1, "duration": 600 },
    { "id": "double_points", "weight": 2, "duration": 600 },
    { "id": "insta_kill",    "weight": 2, "duration": 600 }
  ],

  "player": {
    "starting_items": ["minecraft:wooden_sword"],
    "death": "final",
    "downed_seconds": 30,
    "revive_seconds": 5
  }
}
```

Every one of those is currently a constant in a `.java` file. All of it moves here.

---

## 4. Scripts — the layer that makes it an engine and not a config file

Numbers make a map harder. Scripts make it a *different game*. This is where "full
freedom" actually lives.

```json
"script": [
  {
    "on": "round_start",
    "when": { "round": { "every": 10 } },
    "do": [
      { "title": { "main": "§4THEY SENT SOMETHING", "sub": "Hold the line" } },
      { "spawn": { "id": "minecraft:warden", "at": "marker:boss", "health": 600 } }
    ]
  },
  {
    "on": "objective_damaged",
    "when": { "fraction_below": 0.25 },
    "do": [
      { "sound": "minecraft:entity.warden_heartbeat" },
      { "effect": { "target": "all", "id": "minecraft:darkness", "seconds": 4 } }
    ]
  },
  {
    "on": "timer",
    "when": { "every_seconds": 90 },
    "do": [ { "toggle_zone": { "id": "flooded", "state": "flip" } } ]
  }
]
```

**Events:** `run_start`, `round_start`, `round_end`, `mob_killed`, `mob_spawned`,
`player_hurt`, `player_down`, `player_died`, `objective_damaged`, `area_opened`,
`powerup_taken`, `purchase`, `timer`, `zone_enter`, `zone_exit`.

**Actions:** `spawn`, `message`, `title`, `sound`, `particle`, `effect`, `give`,
`take`, `set_block`, `fill`, `open_area`, `award_points`, `set_rule`, `toggle_zone`,
`teleport`, `end_run`.

`set_rule` is the dangerous, wonderful one: a script can rewrite the ruleset
mid-run. Round 30 doubles horde speed permanently. A boss death halves prices for
the rest of the game.

---

## 5. Tooling

| Command | Does |
|---|---|
| `/abyss maps` | list every loaded arena and where it came from |
| `/abyss validate <map>` | lint it — missing spawn, no horde markers, sealed area with no door, pieces that overlap |
| `/abyss load <map>` | stamp it now, into its slot |
| `/abyss goto <map>` | teleport yourself to it |
| `/abyss reload` | re-read all datapacks |
| `/abyss export <name>` | save the region you are stood in as a map pack, markers and all |

`validate` matters more than it sounds. The single most likely failure for a map
author is a map that loads but is unplayable — no spawn, or a horde marker facing
into solid stone. It should tell you, in chat, in English, before you waste a run.

---

## 6. Slots, so maps cannot collide

Every loaded arena is assigned a cell on an invisible grid in the Abyss dimension,
1024 blocks apart, allocated in load order and remembered in world data. Two map
packs written by two people who both chose x=0 do not overwrite each other.

## 7. Guard rails

The engine clamps what data can ask for, because a typo should not be able to brick
a world:

- attribute values clamped to sane ranges (no 10⁹ HP zombies)
- concurrent-mob cap enforced regardless of what the ruleset says
- a map missing `abyss:spawn` refuses to load, loudly
- scripts get a per-tick action budget; a runaway trigger is disabled and reported
- unknown marker kinds and unknown action names are warnings, not crashes — a map
  written for a newer engine still loads on an older one

---

## 8. What order this gets built in

1. **Loading** — manifests and rulesets off datapacks, `/abyss maps`, `/abyss validate`
2. **Stamping** — structure pieces into slots, marker scan, `/abyss load`
3. **Rules** — the existing round system reading from a ruleset instead of constants
4. **Picker** — the map select screen listing whatever is loaded
5. **Scripts** — triggers and actions
6. **Authoring** — `/abyss export`, marker wand

Stages 1–2 are the foundation: after them, a map you build genuinely appears in the
game without me. Stages 3–5 are what make it tunable. Stage 6 is comfort.

---

## 9. What this costs

Honest notes.

The four existing maps become datapacks shipped inside the mod. They will not play
*identically* — anything the engine cannot express in data has to be either added to
the engine or dropped. I would rather add it to the engine.

The Maze does not fit this model and should not be forced into it. It is not a
round-survival arena; it is generated, not built, and it reshapes itself. It stays
its own thing.
