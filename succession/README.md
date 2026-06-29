# Succession

A standalone [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.1**: the world
slowly heals the land you've cleared. Named after *ecological succession* — the way
real abandoned ground recovers from bare soil back to forest.

## How it works

Over time, near where players are active, the mod walks land through natural stages:

```
bare dirt  --grass creeps in from a neighbour-->  grass_block
grass_block  --sunlight-->  short grass / fern / flower / (rarely) a sapling
sapling  --ordinary growth-->  tree
```

- **Grass only spreads outward from existing grass**, so it never greens over a
  desert or badlands — the recovery stays true to the biome it's in.
- Saplings are placed sparsely; from there **vanilla growth** turns them into
  trees, so a cleared patch genuinely reforests itself over many in-game days.
- Everything is gated by **light level (9+)** and randomness, so growth looks
  patchy and organic rather than gridded — and shaded/indoor dirt stays bare.

Leave a strip-mined hillside or a burned clearing alone long enough and you'll
come back to grass, wildflowers, and young trees.

## Tuning

Constants at the top of
[`SuccessionEngine.java`](src/main/java/com/succession/SuccessionEngine.java):

| Constant | Default | Meaning |
| --- | --- | --- |
| `INTERVAL` | `60` | Ticks between sampler runs |
| `SAMPLES_PER_PLAYER` | `30` | Random blocks examined per player per run |
| `RADIUS` / `VERTICAL` | `16` / `6` | Sampling box around each player |
| `LIGHT_MIN` | `9` | Minimum light for grass/plant growth |
| `VEGETATE_CHANCE` | `5` | 1-in-N chance an eligible grass block sprouts a plant |

Raise `SAMPLES_PER_PLAYER` or lower `VEGETATE_CHANCE`'s denominator to speed
recovery; the defaults are deliberately slow so it feels like nature, not magic.

## Build & run

> ⚠️ The first build downloads Minecraft / Yarn / Fabric API from
> `maven.fabricmc.net` and `piston-meta.mojang.com`, so it needs network access to
> those hosts. It was scaffolded in a sandbox where they were blocked, so the
> gradle build was **not** run here — run it on your own machine.

Requirements: **JDK 21**, plus Fabric Loader + [Fabric API](https://modrinth.com/mod/fabric-api).

```bash
cd succession
./gradlew runClient
./gradlew build       # -> build/libs/succession-1.0.0.jar
```

Server-side logic, so on a dedicated server only the server needs the mod.

## Ideas for v2

- Biome-aware saplings (birch in cold forests, spruce in taiga, etc.).
- A slower "reclaim structures" pass: vines and moss creeping over abandoned stone.
- Tie growth speed to a seasons mod so succession slows in winter.
- Persist a per-chunk "wildness" value so long-settled areas resist regrowth.
