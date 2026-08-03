# Every marker, and what it is for

The reference. `MAPMAKING.md` teaches; this one is the full option list for each
marker and, more usefully, **what people actually build with it**.

A marker is `[Kind]` on line one and `key=value` on every line after. A bare word
becomes `value`, so `[Perk] ironhide` and `[Perk] id=ironhide` are the same thing.
`/arena marker <kind>` hands you one already written.

Marker Blocks give you **8 lines × 256 characters**; write past a sign's limits
with `/arena line <n> <text>`.

---

## The horde

### `[Horde]` — a way in

| Option | Default | Does |
|---|---|---|
| `area=` | *(always live)* | stays shut until a `[Door]` with this area is bought |
| `weight=` | `1` | how often this gate is chosen relative to the others |
| `burst=` | `1` | how many come out at once (1–8) |
| `mobs=` | *(the whole table)* | restrict to named entities, e.g. `mobs=husk,drowned` |
| `health=` | `100` | percent, **on top of** the round curve — `200` is twice as tough as this round |
| `damage=` | `100` | percent, same |
| `from_round=` | `0` | gate stays shut until this round |
| `until_round=` | *(never)* | gate closes after this round |

**Percentages, not absolutes, on purpose.** `health=200` means "twice as tough as
whatever this round is", which still means something at round 40. An absolute
number stops meaning anything by round 10.

**What people build with it**
- *Front door* — `weight=4`, everything else `weight=1`. Most of it comes one way,
  so the map has a shape.
- *The bad corridor* — `health=200 damage=150 weight=1`. Rare and horrible.
- *A pack* — `burst=4 weight=1`. Four at once is a different problem from four
  in a row.
- *Escalation* — a gate with `from_round=15`. Somewhere for the map to go.
- *Themed rooms* — `mobs=drowned` in the flooded half, `mobs=husk` in the dry half.
- *Early pressure that stops* — `until_round=10` on a starter gate, so the opening
  is busy and the map moves on.

### `[Pen]` — arrive unseen

No options. Put one behind a `[Horde]` and mobs materialise in the pen and *walk
in*. Best atmosphere-per-effort in the whole system: build a small dark closet
behind each wall and mobs stop popping into existence in front of you.

### `[Spawner]` — enemies you place

`<entity id>` · `count=` `round=` `every=` `health=` `damage=`

Any entity, mods included. `/arena mobs <filter>` lists them — a bad id spawns
nothing, silently, forever.

**Built with it:** a fixed guard on a vault; a wave that only happens once at
round 12; ambient wildlife in a non-combat map.

### `[Boss]` — one big thing on a cycle

`<entity id>` · `every=` `health=`

---

## Money and the shop

### `[Dealer]` — sells one thing

**Positional**, across both faces of the sign — eight lines:

```
[Dealer]              (back face)
minecraft:crossbow    enchant=3
1750 points           round=8
x1                    limit=2
```

| Option | Does |
|---|---|
| line 2 | the item, bare names fine |
| line 3 | price, optionally a currency |
| `xN` | stack size |
| `enchant=` | vanilla enchanting level, applied on sale |
| `round=` | sealed until then |
| `limit=` | how many the **whole squad** may buy this run |

Buying something you already own **repairs and reloads it**. `limit=` is per run
and per sign, not per player — one netherite sword means the squad gets one and
has to decide who carries it.

**Built with it:** a wall-buy that unlocks at round 8 so the map has somewhere to
get to; a single sniper rifle the team argues over; ammunition that stays cheap
while everything else inflates.

### `[Box]` — random weapon

`price=` · `pool=` · `items=`

`items=arrow*8,iron_sword` inline beats `pool=late_game` beats the built-in list.

**Built with it:** a flooded map whose Box only gives tridents; a siege map where
it only gives arrows and bread; a low-tech map with no netherite in it at all.

### `[Perk]` · `[Upgrade]` · `[Loot]`

`[Perk] <effect id> price= amp=` — any effect, for the rest of the run
`[Upgrade] price=` — climbs stone → iron → diamond → netherite on what you hold
`[Loot] tier= pool= items=` — supply cache, once per round

---

## Shaping the map

### `[Door]` — the important one

`area=` `cost=` `currency=` `width=` `height=`

Pay, and the doorway **punches itself open** — you don't build the hole. Every
`[Horde]` tagged with that area goes live at the same moment.

This pairing is what makes a map a map: you start in one room you can hold, and
every door is more ground **and** more directions it comes from. Spell the area
identically on both.

### `[Region]` — reacting to where people are

`id=` `radius=` `height=`

Fires `region_enter` / `region_leave`, **edge-triggered**. The substrate of
checkpoints, capture points, finish lines, and the room you're not meant to be in.

### `[Zone]` — an effect while you stand in it

`effect=` `radius=` `amp=`

### `[Trap]` — pay to make a corridor lethal

`cost=` `damage=` `radius=` `seconds=` `cooldown=`

### `[Teleport]` — two pads sharing an `id=`

You need **two**. One alone is furniture.

---

## Starting, winning, and doing

### `[Spawn]` — where you arrive. Required.

`team=` makes it that side's spawn. A map with teams but one spawn still plays,
symmetrically.

### `[Extract]` — bank the run

`radius=`. Without one a map has no way to win, only a way to lose.

### `[Objective]` — something besides survival

`defend hp=` · `hold seconds=` · `collect count= item=` · `radius=` · `fail=end`

`fail=end` makes losing it end the run; leave it off and it's a side task rather
than a single point of failure.

### `[Powerup]` — where drops can land

---

## Saying what you know

Every text the script layer produced was a literal. An author could count keys,
captures, lives and flags — the entire point of variables — and had no way to
**show a number to anybody**. State was tracked perfectly and communicated not
at all.

Any `message`, `actionbar`, `title`, `set_bar` or `team_message` may now contain:

| Placeholder | Is |
|---|---|
| `{var:name}` | a run variable |
| `{my_var:name}` | that player's own copy |
| `{total_var:name}` | everybody's copies added up |
| `{team_var:red:score}` | a team's variable — drop the team for the viewer's own |
| `{round}` `{seconds}` `{time}` | the round, and the run clock raw or as `m:ss` |
| `{phase}` `{players}` | which part of the game, and how many are in |
| `{player}` `{team}` | the viewer's name and side |

Rendered **per recipient**, so `{my_var}` and `{player}` mean the right thing to
each person — a message resolved once would show the whole squad the first
player's numbers. `set_bar` has no viewer, so per-player placeholders come out
blank there rather than lying.

Anything unrecognised is left exactly as written: a map that puts `{foo}` in a
message meant to.

---

## Reading the player

The script layer could always read the run. It could not read the person playing
it, which is why no map here has ever had a key.

| Clause | Kind | Does |
|---|---|---|
| `has_item` | condition | `{ "id": "...", "count": 1, "slot": "any" }` — searches the whole inventory unless you narrow it to `mainhand`, `offhand` or `armor` |
| `take` | action | removes N of an item. The counterpart `give` never had |
| `killed` | condition | which entity died. `zombie` and `minecraft:zombie` both work |
| `chance` | condition | a percentage roll on any rule |

**Why `take` matters more than it looks.** Without it an item can be *required*
but never *spent*, so every key opens every door forever. A toll you pay once and
a check you pass are different mechanics, and only the second one existed.

**What these four unlock together:** keys and doors, fetch quests, deliveries,
tolls, trades, escort payoffs, boss-specific triggers, rare drops and random
events. `aztecabyss:keyhunt` is a shipped map built entirely out of them.

---

## Which markers a given game needs

| Game | Needs |
|---|---|
| Survival arena | `[Spawn]` `[Horde]` `[Pen]` `[Door]` `[Dealer]` `[Box]` `[Extract]` |
| Heist / escape room | `[Spawn]` `[Region]` + `mode: free` + variables |
| Puzzle | `[Spawn]` `[Region]` + `use_block` rules |
| Capture the flag | `[Spawn] team=` ×2 `[Region]` + teams + respawn + kit |
| Race | `[Spawn]` `[Region]` checkpoints + `mode: free` + the run clock |
| Boss rush | `[Spawn]` `[Boss] every=1` `[Dealer]` |
| Horde defence | `[Spawn]` `[Horde]` `[Objective] defend` `[Trap]` |
| Hunger Games / battle royale | many `[Spawn]` + `spawns: scattered` + `border` + no respawn |

Nothing in the right-hand column is a mode the engine knows about. They are all
the same marker set, arranged differently.
