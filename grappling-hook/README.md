# Grappling Hook

A standalone [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.1** that adds
one classic item: a **Grappling Hook**.

## What it does

Hold the hook and **right-click** while aiming at a block. The mod casts a ray from
your eyes; if it hits a block within **32 blocks**, you're flung toward the impact
point. Pull speed scales with distance (capped so long shots stay sane), with a
small upward boost so you arc *over* ledges instead of face-planting the wall —
and fall damage from the swing is cancelled.

Great for crossing ravines, scaling cliffs, and bailing out of a bad situation.

## Tuning

Constants live at the top of
[`GrapplingHookItem.java`](src/main/java/com/grapplinghook/GrapplingHookItem.java):

| Constant | Default | Meaning |
| --- | --- | --- |
| `MAX_RANGE` | `32.0` | Reach of the hook, in blocks |
| `PULL_FACTOR` | `0.28` | How hard pull speed scales with distance |
| `MAX_PULL_SPEED` | `2.6` | Speed cap so long shots don't over-fling |
| `UPWARD_BOOST` | `0.30` | Extra lift added to every pull |
| `COOLDOWN_TICKS` | `8` | Ticks between uses |

## Build & run

> ⚠️ The first build downloads Minecraft / Yarn / Fabric API from
> `maven.fabricmc.net` and `piston-meta.mojang.com`, so it needs network access to
> those hosts. It was scaffolded in a sandbox where they were blocked, so the
> gradle build was **not** run here — run it on your own machine.

Requirements: **JDK 21**, plus Fabric Loader + [Fabric API](https://modrinth.com/mod/fabric-api).

```bash
cd grappling-hook
./gradlew runClient   # launch the game with the mod loaded
./gradlew build       # -> build/libs/grappling-hook-1.0.0.jar
```

The hook appears in the **Tools & Utilities** creative tab.

## Ideas for v2

- A visible rope/chain rendered between you and the anchor (needs client rendering).
- Continuous reel-in while held, instead of a single yank.
- Durability and a crafting recipe.
- A max-swing arc that preserves momentum for true Spider-Man swinging.
