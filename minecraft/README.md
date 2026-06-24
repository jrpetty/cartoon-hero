# ClaudeCraft — a Minecraft server running only mods made in Claude

This directory contains everything needed to run a [Fabric](https://fabricmc.net/)
Minecraft server whose **only** mod is one written from scratch by Claude:
**ClaudeCraft**. No third-party content mods are installed — the only other jar
on the server is Fabric API, the standard runtime library every Fabric mod
depends on.

- **Minecraft version:** 1.21.1
- **Mod loader:** Fabric (loader 0.16.5)
- **Java required:** 21+

```
minecraft/
├── mods/claudecraft/     # The Claude-made mod (Fabric + Gradle project)
│   ├── src/main/java/    # Mod source code
│   ├── src/main/resources/  # fabric.mod.json, models, textures, recipes, loot
│   ├── build.gradle      # Fabric Loom build
│   └── gradlew           # Gradle wrapper (no system Gradle needed)
└── server/
    ├── setup.sh          # Builds the mod + provisions the server
    └── start.sh          # Launches the server
```

## What the mod adds

| Type | Name | Notes |
|------|------|-------|
| Item | **Claude Core** | Crafting material; craft 9 into a Claude Block. |
| Item | **Anthropic Apple** | Enchanted-apple-style food: Regeneration II + Resistance, always edible. |
| Block | **Claude Block** | Decorative metal-sounding block (needs a pickaxe). |
| Block | **Glowing Claude Lamp** | Full-bright (light level 15) decorative lamp. |
| Tab | **ClaudeCraft** creative tab | Collects all of the above. |
| Command | `/claude`, `/claude credits` | Prints info about the server/mod. |
| Event | Join message | Greets every player who connects. |

All items and blocks are craftable in survival:

- **Claude Block** = 3×3 Claude Core (reversible: 1 block → 9 cores).
- **Glowing Claude Lamp** = Claude Core surrounded by 4 Glowstone Dust (+).

## Quick start (on a machine with internet)

The build and the server need to reach Mojang and Fabric servers, so run these
on your own machine (not a restricted CI sandbox).

```bash
cd minecraft/server

# 1. Build the mod and download the Fabric server + Fabric API
./setup.sh

# 2. Accept the Minecraft EULA (https://aka.ms/MinecraftEULA)
echo 'eula=true' > eula.txt

# 3. Start the server
./start.sh
```

Then connect from the Minecraft 1.21.1 Java Edition client (with the same
Fabric loader + Fabric API installed) to `localhost` (or your machine's IP).

## Building the mod by itself

```bash
cd minecraft/mods/claudecraft
./gradlew build
# Output: build/libs/claudecraft-1.0.0.jar
```

Drop that jar (plus Fabric API) into any Fabric 1.21.1 server's or client's
`mods/` folder.

## Notes

- **Versions** are defined in `mods/claudecraft/gradle.properties` and mirrored
  in `server/setup.sh` — change them together if you upgrade Minecraft.
- **Textures** are simple generated placeholders. Re-run
  `mods/claudecraft/gen_textures.py` to tweak them, or replace the PNGs under
  `src/main/resources/assets/claudecraft/textures/` with real art.
- The first `./gradlew build` downloads Minecraft, mappings, and Fabric
  libraries, so it takes a few minutes; later builds are fast.
