# Voxelia — a browser-based Minecraft MMO

A from-scratch, multiplayer voxel MMORPG that runs in the browser. A Three.js
client renders a shared, procedurally-generated voxel world; an authoritative
Node.js WebSocket server owns the world, mobs, combat, and the skill/XP economy.

It is modeled on the two largest real Minecraft MMOs — **Wynncraft** (skills,
quests, mob combat, a persistent shared world) and **Hypixel SkyBlock** (the
"every action grants skill XP" progression loop). See [`DESIGN.md`](./DESIGN.md)
for the full design and how it maps to those references.

![pillars](https://img.shields.io/badge/multiplayer-yes-7cb342) ![pillars](https://img.shields.io/badge/skills-4-ffce54) ![pillars](https://img.shields.io/badge/quests-4-3b6fd6)

## Features (MVP)

- **Shared procedural world** — deterministic value-noise terrain with plains,
  desert and mountain biomes, trees, water, and ores. The world is identical for
  every player from a single seed; only edits are stored.
- **Real multiplayer** — see other players move in real time with interpolated
  avatars + nametags, an online count, and live chat.
- **Build & mine** — break and place blocks; every change is server-authoritative,
  synced to all players, and persisted to disk.
- **Skill progression** — Mining, Foraging, Combat, Building, each with its own
  XP and level (SkyBlock-style). Combat level scales your health.
- **Quests** — a 4-step starter chain that guides the early game.
- **Mobs & combat** — wandering slimes with server-side AI, HP, loot, and respawn.
- **HUD** — health, character level, skill panel, quest tracker, hotbar with
  inventory counts, combat leaderboard, radar minimap, and toasts.

## Run it

```bash
cd mmo
npm install
npm start
```

Then open <http://localhost:8080>. **Open a second browser tab** to see the
multiplayer in action — both tabs share the same world.

To verify the server end-to-end without a browser:

```bash
npm run smoke
```

### Controls

| Action | Input |
| --- | --- |
| Move | `W` `A` `S` `D` |
| Jump | `Space` |
| Look | Mouse (click to lock the cursor) |
| Break block / attack mob | Left click |
| Place block | Right click |
| Select hotbar slot | `1`–`9` or scroll wheel |
| Chat | `Enter` to open, `Enter` to send, `Esc` to cancel |
| Release cursor | `Esc` |

## Configuration

| Env var | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8080` | HTTP + WebSocket port |
| `SEED` | `1337` | World generation seed |

World edits are saved to `mmo/server/world-save.json` every 15s and on exit.
Delete that file to reset the world.

## Architecture

```
mmo/
├── shared/blocks.js        block registry shared by client + server
├── server/
│   ├── index.js            HTTP host + WebSocket + 20 tps game loop
│   ├── world.js            procedural gen, chunks, edit persistence
│   ├── noise.js            deterministic value noise / fBm
│   ├── player.js           player state + skill/XP economy
│   ├── mobs.js             mob spawning + AI + combat
│   ├── quests.js           data-driven quest chain
│   ├── protocol.js         message type registry
│   └── smoke-test.js       headless end-to-end check
└── client/
    ├── index.html          shell + HUD markup + importmap (Three.js via CDN)
    ├── css/style.css       HUD styling
    └── js/
        ├── main.js         scene bootstrap, input, render loop
        ├── network.js      WebSocket wrapper
        ├── world.js        chunk meshing + voxel raycasting
        ├── player.js       first-person controls + collision
        ├── entities.js     other players + mobs
        └── ui.js           HUD / minimap / chat
```

The server is authoritative: clients send intents (move, break, place, attack)
and render the state the server broadcasts. This keeps the world consistent and
cheating in check — the only honest way to build an MMO.

> Note: this lives alongside the original FastAPI league-tracker app at the repo
> root, which is unchanged.
