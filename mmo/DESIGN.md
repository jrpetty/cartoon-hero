# Voxelia — A Browser-Based Minecraft MMO

> A from-scratch, multiplayer voxel MMORPG that runs in the browser.
> Three.js client + authoritative Node.js WebSocket server.

This document is the design backbone for the project. It is grounded in how the
most successful real Minecraft MMOs are built and what makes them stick, then
distilled into a scoped, buildable MVP.

---

## 1. What we are copying, and why

The brief was: *"look at similar things online and create the same thing."*
The two reference points that define the genre are **Wynncraft** and
**Hypixel SkyBlock** — the two largest Minecraft MMO experiences.

| Server | Core loop | What we steal |
| --- | --- | --- |
| **Wynncraft** | A huge hand-built fantasy world. ~200 quests, classes, dungeons, raids, and a deep gathering/profession skill tree (mining, woodcutting, farming, fishing). Combat is class + ability based. | Skill/profession progression, quests, mob combat, a persistent shared world, classes. |
| **Hypixel SkyBlock** | Spawn, gather resources, level *skills* (Mining, Foraging, Combat, Farming), upgrade gear, fight bigger bosses. Everything feeds a number-go-up skill economy. | The skill-XP economy: every action grants XP in a discipline; levels unlock things. Shared hubs + leaderboards. |

Both share the same DNA, which becomes our pillars:

1. **A persistent, shared voxel world** other players can see you in.
2. **Skill-based progression** — every action (mining, chopping, fighting) grants
   XP toward a skill; skills level up and gate content.
3. **Quests** that direct the early game and teach the loop.
4. **Mob combat** with loot.
5. **Social presence** — see other players move, build, and chat in real time.

A full MMO is thousands of hours of content. The MVP delivers the *systems* that
make it an MMO — the loops above — in a single coherent vertical slice that
actually runs and is actually multiplayer.

Reference projects studied for the technical architecture (Three.js client +
WebSocket server, chunked procedural world, binary-ish protocol): `mc.js`,
BurnyCoder's `minecraft-clone`, BlockCraft, and BLK.

---

## 2. Architecture

```
                 ┌────────────────────────────────────────────┐
   Browser       │                Node.js server               │
 ┌──────────┐    │  ┌──────────┐  ┌──────────┐  ┌────────────┐  │
 │ Three.js │◄──►│  │  World   │  │  Game    │  │  Persist   │  │
 │  client  │ ws │  │  (gen +  │  │  loop    │  │  (JSON     │  │
 │          │    │  │  chunks) │  │ (20 tps) │  │  snapshot) │  │
 └──────────┘    │  └──────────┘  └──────────┘  └────────────┘  │
   render +      │     authoritative state: players, mobs,      │
   input only    │     block edits, skills, quests, chat        │
                 └────────────────────────────────────────────┘
```

**Server-authoritative.** The server owns the world, mob AI, combat resolution,
XP, and the canonical block grid. Clients send *intents* (move to here, break
this block, attack that mob) and render the state the server broadcasts. This is
the only honest way to build an MMO — it keeps cheating and desync in check.

- **Transport:** one `ws` WebSocket server, sharing the same HTTP port that
  serves the static client. Single origin, zero CORS pain, one `npm start`.
- **Protocol:** newline-free JSON frames with a one-letter `t` (type) tag.
  Chosen over a packed binary protocol for MVP legibility; the message shapes
  are documented in `server/protocol.js` and are trivially swappable for a
  binary encoding later (the reference projects show 21-byte position frames).
- **Tick:** 20 ticks/second authoritative simulation (mob AI, regen,
  persistence). Movement is broadcast at the tick rate.
- **World:** deterministic procedural generation from a single seed using value
  noise. The world is identical for every client given the seed; only *edits*
  (placed/broken blocks) are stored and replayed on top.

---

## 3. The world

- **Chunks:** 16 × 16 columns, world height 64. Chunks stream to a client as it
  moves; only chunks within view distance are sent.
- **Generation:** multi-octave value noise drives a heightmap. Layered biomes
  (plains, desert, mountains) selected by a low-frequency noise field decide
  surface block + tree density. Below the surface: dirt then stone; water fills
  to sea level; sand around water and in deserts.
- **Block palette (MVP):** air, grass, dirt, stone, sand, water, log, leaves,
  planks, cobblestone, glass, ore (coal/iron), torch-glow.
- **Edits:** every place/break is stored sparsely (`"x,y,z" -> blockId`) and
  persisted, so the world is genuinely shared and durable across sessions.

---

## 4. Progression — the MMO core

Lifted straight from SkyBlock's skill economy and Wynncraft's professions.

**Skills** (each has its own XP and level, 1–50):

| Skill | XP source | Unlocks |
| --- | --- | --- |
| **Mining** | Breaking stone/ore | Faster mining, access to ores |
| **Foraging** | Chopping logs/leaves | Wood yield, tree-related recipes |
| **Combat** | Killing mobs | Max health, damage |
| **Building** | Placing blocks | Cosmetic blocks, build score |

**Character level** is the sum of skill progress; it gates quests and is the
headline number on the HUD. Health scales with Combat level. The XP curve is the
classic exponential `xpForLevel(n) = round(50 * n^1.5)`.

**Inventory:** a 9-slot hotbar. Gathered resources stack; placing consumes.

---

## 5. Quests

A lightweight, data-driven quest chain teaches the loop (the Wynncraft pattern of
"quests as a guided tour"):

1. **First Cut** — chop 5 logs. → reward Foraging XP + planks.
2. **Pick of the Litter** — mine 10 stone. → Mining XP + cobblestone.
3. **Pest Control** — defeat 3 mobs. → Combat XP + health boost.
4. **Homestead** — place 20 blocks. → Building XP.

Quests are objects with `{id, title, type, target, reward}`; progress is tracked
server-side per player and surfaced in the HUD quest tracker. New quests are
added by appending to `server/quests.js`.

---

## 6. Mobs & combat

- Server spawns wandering mobs (a "slime") near players up to a cap.
- Simple AI: idle wander; when a player is close, drift toward them.
- Combat: client raycasts on attack; if a mob is in range and in the cursor,
  send `attack`. Server resolves damage, death, loot, and Combat XP.
- Mobs respawn over time to keep the world populated.

---

## 7. Social presence

- Other players render as labeled avatars that interpolate between server
  snapshots.
- Real-time chat with a scrollback log.
- Join/leave system messages.
- A live online-player count and a Combat leaderboard (the Hypixel touch).

---

## 8. MVP scope vs. future

**In the MVP (this repo):** shared procedural world, real multiplayer movement,
block place/break sync + persistence, 4 skills with XP/levels, inventory/hotbar,
a 4-quest starter chain, wandering mobs with combat & loot, chat, leaderboard,
HUD (health/XP/skills/quests/minimap).

**Deliberately deferred:** classes & abilities, dungeons/raids, crafting trees,
trading/economy, an account/auth system, a packed binary protocol, and a DB
(swap the JSON snapshot for SQLite/Postgres). Each has a clear seam in the code.

---

## 9. Running it

See `mmo/README.md`. In short: `cd mmo && npm install && npm start`, then open
`http://localhost:8080`. Open a second tab to see multiplayer.
