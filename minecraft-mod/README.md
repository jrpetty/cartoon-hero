# Cartoon Hero — a Minecraft mod

A small but complete [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.1**,
built from scratch. It's a real, growable starting point — not just a skeleton.

## What it adds

| Content | Description |
| --- | --- |
| **Hero Emblem** (item) | Right-click to transform into a cartoon hero: **Speed II + Jump Boost II** for 30s and a burst of **Regeneration**, with a triumphant level-up chime and a 45s cooldown. |
| **Cartoon Bricks** (block) | A softly glowing (light level 7) decorative block with glassy break sounds. Drops itself when mined with a pickaxe. |
| **Cartoon Hero** (creative tab) | A dedicated creative-inventory group holding the mod's content. |

## Requirements

- **JDK 21** (Temurin/Adoptium recommended)
- The [Fabric Loader](https://fabricmc.net/use/installer/) + [Fabric API](https://modrinth.com/mod/fabric-api) when running the built jar in a real instance

## Build & run

> ⚠️ The first build downloads Minecraft, Yarn mappings and the Fabric API from
> `maven.fabricmc.net`, `piston-meta.mojang.com` and friends, so it needs
> network access to those hosts. (This mod was scaffolded in a sandbox where
> those hosts were blocked, so the build was **not** run here — run it on your
> own machine.)

```bash
cd minecraft-mod

# Launch the game with the mod loaded (dev environment):
./gradlew runClient

# Or just produce the distributable jar:
./gradlew build
# -> build/libs/cartoon-hero-1.0.0.jar
```

Drop the built jar (plus Fabric API) into your `.minecraft/mods` folder to play
with it in a normal installation.

## Project layout

```
minecraft-mod/
├── build.gradle                # Loom + dependencies
├── gradle.properties           # Minecraft / Yarn / Loader / Fabric API versions
├── settings.gradle             # Fabric maven for the loom plugin
├── gen_textures.py             # Regenerates the placeholder textures (no Pillow needed)
└── src/main/
    ├── java/com/cartoonhero/
    │   ├── CartoonHero.java         # @ModInitializer entrypoint
    │   ├── ModItemGroups.java       # Creative tab
    │   ├── item/ModItems.java       # Item registry
    │   ├── item/HeroEmblemItem.java # The right-click super-power behavior
    │   └── block/ModBlocks.java     # Block + BlockItem registry
    └── resources/
        ├── fabric.mod.json
        ├── assets/cartoonhero/...   # lang, models, blockstates, textures, icon
        └── data/cartoonhero/...     # loot table for Cartoon Bricks
```

## Where to go next

- **New items**: add a field in `ModItems`, a `models/item/<name>.json`, a
  `textures/item/<name>.png`, a lang entry, and add it to the creative tab.
- **New blocks**: same idea via `ModBlocks`, plus a blockstate, block model,
  item model and loot table.
- **Custom behavior**: copy `HeroEmblemItem` as a template for items that do
  something on use.
- **Real art**: replace the generated PNGs in `textures/`; rerun
  `python3 gen_textures.py` only if you want the placeholders back.

Versions are pinned in `gradle.properties`; bump them together using the
matched set from <https://fabricmc.net/develop/>.
