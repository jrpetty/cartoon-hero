# Desire Paths

A standalone [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.1** with one
idea: **the ground remembers where you walk.**

In urban planning, a *desire path* is the worn dirt trail people carve across a
lawn by repeatedly choosing the shortcut over the paved route. This mod brings
that to Minecraft — emergently, with no items and no UI.

## How it works

- Walk across the same grass repeatedly and it compacts on its own:

  ```
  grass_block  →  coarse_dirt  →  dirt_path
  ```

  It takes about **12 footsteps** on a block to advance one stage, so casual
  crossings barely mark the ground while genuine routes wear in over time.

- Abandon a route and **nature reclaims it**. A worn block with no traffic for
  roughly an in-game day steps back toward grass, eventually disappearing
  entirely:

  ```
  dirt_path  →  coarse_dirt  →  grass_block
  ```

The result: every world quietly tells the story of how it's been lived in.
On a multiplayer server, shared shortcuts become real trails that everyone
helps wear in — and overgrown ruins lose their paths as they're forgotten.

## Why it's different

Most mods *add content*. This one adds **memory** to the world itself — a
constant, ambient enhancement to ordinary movement that you feel everywhere
without ever opening a menu.

## Tuning

All knobs live as constants at the top of
[`PathWear.java`](src/main/java/com/desirepaths/PathWear.java):

| Constant | Default | Meaning |
| --- | --- | --- |
| `STEPS_PER_STAGE` | `12` | Footsteps on a block before it advances a stage |
| `RECLAIM_AFTER_TICKS` | `24000` | Ticks of no traffic before a block reclaims one stage (~1 day) |
| `RECLAIM_SWEEP_INTERVAL` | `600` | How often (ticks) the reclaim sweep runs |
| `RECLAIM_MAX_PER_SWEEP` | `64` | Max blocks reclaimed per sweep (bounds CPU) |

## Build & run

> ⚠️ The first build downloads Minecraft, Yarn mappings and the Fabric API from
> `maven.fabricmc.net` / `piston-meta.mojang.com`, so it needs network access to
> those hosts. This mod was scaffolded in a sandbox where those hosts were
> blocked, so the gradle build was **not** run here — run it on your own machine.

Requirements: **JDK 21**, plus the Fabric Loader + [Fabric API](https://modrinth.com/mod/fabric-api).

```bash
cd desire-paths

./gradlew runClient   # launch the game with the mod loaded
./gradlew build       # -> build/libs/desire-paths-1.0.0.jar
```

It's server-side logic, so on a dedicated server only the server needs the mod;
clients see the block changes automatically.

## Project layout

```
desire-paths/
├── build.gradle
├── gradle.properties        # MC / Yarn / Loader / Fabric API versions
├── settings.gradle
└── src/main/
    ├── java/com/desirepaths/
    │   ├── DesirePaths.java  # @ModInitializer — registers the tick handler
    │   └── PathWear.java     # all the wear / reclaim logic
    └── resources/
        └── fabric.mod.json
```

## Ideas for v2

- Persist in-flight step progress to disk (currently only finished block changes survive a restart).
- Widen paths slightly at heavy-traffic junctions.
- Snow/sand/gravel wear variants.
- A config file instead of source constants.
