# Changelog

Every change to the Arena Engine and the modes around it, newest first.

**This file is updated with every change.** If a commit touches the engine and is
not in here, that is a bug in the process, not an omission worth living with — the
whole point is that one file answers "what is different now".

Legend: **feat** new capability · **fix** something that did not work · **change**
behaviour that was already there · **docs**

---

## Unreleased

### The portal screen was unreadable

- **fix** Every string on the picker was drawn with `dropShadow = false`.
  Minecraft's font is designed around that shadow; without it, small text on a
  dark ground goes thin and shimmers. That was the "fizzy", and the blur was only
  half the story.
- **fix** Card bodies were `0x99`–`0xCC` alpha, so each one was a slightly
  different muddy grey depending on what happened to be behind it. Opaque now — a
  card is a surface, not a tint.
- **change** Contrast raised throughout: blurbs `0x9A9A9A` → `0xC8C4BA`, titles to
  near-white, "Never attempted" off near-black. The difficulty pill is solid
  rather than `0x66` alpha.
- **fix** Same shadow and contrast problems fixed on the Records screen, which had
  inherited them.

### Griever gets a face

- **feat** `GrieverLayer` — dark segmented plate over the whole model and eight
  burning eyes drawn with `RenderType.eyes`, so it is full-bright in a dark
  corridor. A Griever no longer reads as a large spider.
- **change** Done as a render layer on the shared spider renderer rather than a
  new entity: a spider already moves the way this should move, and the animation
  is the half that sells it. Ordinary spiders are untouched — the layer checks the
  tag and returns.

### `5a3b0ba` — Reaching the maze exit crashed the game

- **fix** `tickRunners` walked the live player list and called `changeDimension`
  from inside it, which removes the player from the list being iterated.
  `ConcurrentModificationException` out of the server tick, taking the world down.
  The hardest thing in the mod ended in a crash **every time**. Escapees are now
  collected and sent home after the loop, and the loop walks a copy.
- **change** Griever darkness removed. Being blinded near one took the corridor
  away exactly when you most needed to read it.

### `632bfdb` — Maze caches, a real payout, records for every map

- **feat** 28 chests per night, re-scattered with each reshape, weighted toward
  dead ends and richer further from the Glade. Tier 3 can hold an enchanted golden
  apple or the serum.
- **change** Escape payout raised hard — diamonds, netherite, enchanted apples,
  1500 XP × tier. A netherite block and an enchanted book are reserved for a clean
  sub-three-minute route.
- **feat** `Leaderboards` — records keyed by **string**, so the Maze (scored in
  seconds) and published maps (files, no enum index) can have them. Previously
  keyed by an `int` index into `ArenaMap`, so **a map you made recorded nothing**.
- **feat** Solo and group kept as separate boards. Four people reaching round
  thirty is a different result from one person doing it, not a better one.
- **feat** **Records** button on the portal picker. Requested on press rather than
  pushed with the picker, so opening a portal costs no packet.
- **change** Dying still counts — the board is how far you got.

### `a19c732` — Pools, dealer back face, blur fix

- **feat** `ItemPool` — weighted lists with count ranges and enchant levels, one
  mechanism for the Box, supply caches and (next) loadouts. The Box was thirteen
  items hard-coded in a Java array.
- **feat** Three levels of override, most specific wins: `items=` on the marker →
  `pool=` naming a ruleset pool → the built-in list.
- **feat** Dealers read **both faces** of the sign — eight lines. The four-line
  cap was never the format, it was the front of the sign.
- **feat** Dealer options `enchant=`, `round=` (sealed until then), `limit=` (per
  run, per sign — the squad shares one stock).
- **fix** Picker was blurry: it painted at `0xE0` over vanilla's blur pass, so 12%
  of a blurred moving world came through behind the cards. Opaque now, and the
  blur hook is overridden away rather than painted over.
- **change** An empty pool is reported by `/arena rules` — it is almost always a
  misspelled item id.

### `58e258d` — Publishing: a map becomes a place on the portal

- **feat** `/arena publish <name>` stamps a map into a permanent slot in the Abyss
  and puts it on the picker. `/arena unpublish`, `/arena published`.
- **feat** `<world>/aztecabyss/` — a folder that exists to be opened. A readable
  JSON per map beside its `.nbt`, an index of what is on the portal, and a README.
  Hand-editable, then `/arena reloadmaps`, no restart.
- **change** Published maps run on the **engine**, not the round manager.
  `ArenaMap` is an enum wired into bosses and scoring; a published map cannot be an
  enum constant, and the engine already does all of this.
- **change** Picking a published map **joins** a run in progress rather than
  starting a second one.

### `cbfc7ae` — Marker Blocks

- **feat** A marker block holding **8 lines × 256 characters**, same grammar as
  the signs. `/arena line <n> <text>` — a command argument has no character cap.
- **feat** Invisible in survival, no collision, and its outline exists only for a
  creative player — otherwise a map is full of invisible things swallowing clicks
  meant for the wall behind them. Shows an ember to anyone in creative.
- **change** Settles the sign-era tension: markers no longer have to be either
  left visible on every wall or deleted so the author cannot see their own map.
- **change** Dealers stay signs — a shop front is meant to be walked up to.

### `6c1fe33` — Map Creator opens with a password

- **change** Was operator-only, which handed out far more than Creator or kept out
  exactly the people it exists for. `/creator <password>`, remembered per player;
  operators skip it. `/creator lock <player>` revokes.
- **feat** `creatorPassword` in the mod config.

### `263e08a` — The maze: real boundaries and a real deadline

- **fix** A ring of corridor ran the whole way round outside the Glade wall, so
  **all four doors opened onto the same lane**. Every ring-to-ring edge sealed.
- **change** Nothing outside the Glade breaks. The floor breaks nowhere — one hole
  is a way under a wall.
- **feat** Barrier lid four blocks above the wall tops. Grievers still walk the
  wall tops beneath it; the Glade keeps its open sky.
- **change** Signs and torches are the only placeable blocks. Charting the maze is
  the job, and the section colours exist to be written down.
- **change** The Changing is two phases: 90 seconds of symptoms with the clock on
  screen, **then** lethal. It used to kill you across the same 90 seconds it spent
  telling you to run somewhere.
- **change** Grievers thrown out of the Glade rather than deleted; recalled to the
  corners at dawn.
- **change** Grievers 150 HP, 12 damage, 2.4× scale, soot and soul particles.
- **fix** Day rollover reset nothing — run clocks counted through it and sting
  tallies survived a night's sleep.
- **feat** Exit portal hums and throws light for 60 blocks.
- **feat** Glade wall dressed: plinth, banded course, buttresses, toothed skyline,
  lanterns on the inward face only.
- **feat** Geometry version marker — existing worlds restamp themselves.
- **change** The Workshop is a genuine flat world from `y=0`, not a void with a
  platform in it.

### `4684acc` — Nothing enters the Abyss except through a gate

- **fix** Mobs appearing inside the temple were **zombie reinforcements** — on
  Hard a damaged zombie summons another beside itself, so fighting in the pyramid
  bred zombies in the pyramid. Untagged, so they counted toward no round and
  survived the end-of-round sweep. `SPAWN_REINFORCEMENTS_CHANCE` zeroed.
- **feat** Join guard: during a live run, any monster without a mod tag is refused
  entry to the Abyss.
- **feat** Map Creator on the picker as its own mode.

### `cedd749` — Special rounds and two mute markers

- **fix** Specials took the first match in file order, so with `every: 5` above
  `every: 10` the ten-round special **could never fire** — every multiple of ten
  is also a multiple of five. Shipped `brutal.json` had exactly that. Rarest wins.
- **feat** `/arena validate` now catches an objective with a bad kind or a
  nonexistent `item=`, and a teleport pad with no partner.

### `c3592df` — Bleed-out and defend objectives

- **fix** Bleed-out never killed. The killing blow was struck while the victim was
  still in the `downed` map, and the damage interception that keeps a downed player
  alive cancelled it — marked dead, stuck at 1 HP, unrevivable and unkillable.
- **fix** Defend objectives took damage **per tick**, not per second. Ten mobs =
  50 HP/s, so `hp=600` lasted twelve seconds and every defend objective in every
  map was unwinnable.

---

## Earlier engine work

Condensed; see `git log` for the full sequence.

| Commit | What |
|---|---|
| `d20a00c` | Datapack map manifests — shared maps arrive with name and rules |
| `49cf703` | Dealer repair-and-reload; maps get a verb |
| `68ab57d` | Downed & revive; special rounds |
| `34dd381` | Every entity usable by a spawner, and findable via `/arena mobs` |
| `57c826c` | Traps, teleporters, mob roles that do something |
| `0c8773e` | A map is a fixture, not a consumable — the engine restores what it overwrites |
| `e9db2b5` | Power-ups; the round bar survives a relog |
| `12e326b` | More than one person can play |
| `b0deaa9` | Extraction — a map can be won, not only lost |
| `11dea89` | Three worked rulesets |
| `8e05b32` | `/arena set` — retune a marker without retyping it |
| `fab23c8` | Prefabs |
| `3a0dece` | Copy, paste, rotate, mirror, undo |
| `2440905` | Map identity — title, author, blurb, difficulty |
| `23de3d9` | The Director — pressure-based pacing |
| `9e8f496` | The script layer — triggers and actions |
| `85f6c6f` | The Workshop, the selection wand, marker signs |
| `f714943` | **The engine first plays a map nobody wrote code for** |
| `e2557c2` | Rulesets from datapacks, reloadable live |
| `e879cc0` | Save a build to a file and place it back |
| `50a4b1b` | Markers, world scanning, validation |
| `57063fc` | **Engine begins** — currencies and dealer signs |

---

## Recurring failure modes

Kept here because the same shapes keep coming back.

**Modify-while-iterating.** Three separate bugs: the bleed-out kill, the maze exit
crash, and the downed-map removal. Any "act on a player from inside a loop over
players" is suspect — collect, then act after the loop.

**Features that accept bad input, do nothing, and say nothing.** Ruleset typos,
dead markers, nonexistent entity and item ids, empty pools, lone teleport pads.
Every one of these now reports. The rule: *every documented marker does something,
and anything that cannot do its job says so.*

**Compiling green proves nothing.** Every correctness bug in this project was
found by tracing what a person does. Roughly thirty green builds have caught zero
design faults.
