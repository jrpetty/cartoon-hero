# Voxelia MMO — NeoForge mod

A **NeoForge mod for Minecraft Java Edition** that adds an MMO progression layer:
per-skill XP and levels, level-up stat scaling, a skills HUD, a quest/advancement
chain, and commands. Builds to a standard mod **`.jar`** via Gradle.

- **Minecraft:** 1.21.1
- **NeoForge:** 21.1.x (default `21.1.172`)
- **Java:** 21
- **Loader:** `javafml`

It is the "real Minecraft" counterpart to the browser prototype in [`../mmo`](../mmo):
same MMO design (skills, XP economy, quests, combat), implemented natively against
NeoForge instead of a custom engine.

## Features

| System | What it does | How |
| --- | --- | --- |
| **Skills** | Mining, Foraging, Combat, Farming — each with its own XP and level (1–50). | Data attachment on the player (persisted + copied on death). |
| **XP gains** | Breaking logs/leaves → Foraging; crops → Farming; pickaxe blocks/ores → Mining; killing mobs → Combat. | Vanilla event hooks (`BlockEvent.BreakEvent`, `LivingDeathEvent`). |
| **Level-up scaling** | Combat → max health & attack damage; Mining → armor; Foraging → move speed; Farming → luck. | Idempotent attribute modifiers, re-applied on level-up/login/respawn. |
| **Skills HUD** | Top-left overlay showing each skill's level and progress to next. | Client GUI layer (`RegisterGuiLayersEvent`), fed by a synced packet. |
| **Quests** | A 4-step advancement chain (First Cut → Pick of the Litter → Pest Control → Homestead). | Data-pack advancements. |
| **Commands** | `/voxelia skills` (view), `/voxelia grant <skill> <amount>` (op). | Brigadier via `RegisterCommandsEvent`. |
| **Config** | `xpMultiplier`, `showHud`. | `config/voxelia_mmo-common.toml`. |

The server is authoritative: it owns skill XP and combat resolution and pushes a
`SkillsSyncPayload` to each client, which only renders the HUD.

## Build the jar

Requires JDK 21 and network access to the NeoForge + Mojang Maven repositories.

```bash
cd mc-mod
./gradlew build
```

The mod jar is produced at:

```
build/libs/voxelia_mmo-neoforge-1.21.1-0.1.0.jar
```

Drop that into the `mods/` folder of a Minecraft **1.21.1** instance running
**NeoForge 21.1.x** (client and/or server).

### Run it in a dev environment

```bash
./gradlew runClient    # launches a dev client with the mod loaded
./gradlew runServer    # launches a dev server
```

### Download a pre-built jar (CI)

Every push builds the mod on GitHub Actions and uploads the jar. Grab it from the
latest green run of **[Build NeoForge mod](../../actions/workflows/build-mod.yml)**
→ **Artifacts → `voxelia-mmo-jar`** (no local toolchain needed).

> ℹ️ **Build environment note.** This project was authored in a sandbox whose
> network policy **blocks `maven.neoforged.net` and `libraries.minecraft.net`
> (HTTP 403)**, so the final `./gradlew build` could not run *there* (Gradle
> fails at `:createMinecraftArtifacts`). It builds cleanly anywhere with normal
> network access — **verified green on GitHub Actions**, which compiles against
> the real NeoForge 21.1 API and uploads the jar artifact. Build locally with
> `./gradlew build`, or just download the CI artifact above.

## Retargeting another Minecraft / NeoForge version

Edit `gradle.properties` — `minecraft_version`, `neo_version`, and the matching
version ranges — then rebuild. The Java targets the 1.21.x NeoForge API surface;
larger MC jumps may need small source updates.

## Project layout

```
mc-mod/
├── build.gradle / settings.gradle / gradle.properties   ModDevGradle build
├── gradlew / gradlew.bat / gradle/wrapper/              Gradle wrapper
└── src/main/
    ├── java/com/voxelia/mmo/
    │   ├── VoxeliaMMO.java                 @Mod entry point
    │   ├── skill/        Skill, SkillCurve, PlayerSkills
    │   ├── registry/     VoxeliaAttachments (player data attachment)
    │   ├── progression/  Progression (award XP), SkillEffects (stat scaling)
    │   ├── event/        ProgressionEvents (XP from gameplay)
    │   ├── network/      SkillsSyncPayload, VoxeliaNetwork
    │   ├── command/      VoxeliaCommands
    │   ├── config/       VoxeliaConfig
    │   └── client/       ClientSkillData, SkillHudOverlay, VoxeliaClient
    ├── templates/META-INF/neoforge.mods.toml            (expanded at build)
    └── resources/
        ├── pack.mcmeta
        ├── assets/voxelia_mmo/lang/en_us.json
        └── data/voxelia_mmo/advancement/*.json          quest chain
```
