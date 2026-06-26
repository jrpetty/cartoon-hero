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

**Six skills**, each leveling **1 → 100** with its own XP, stored as a persisted
player data attachment (copied on death):

| Skill | Trains from | Per-level reward | Milestone perk |
| --- | --- | --- | --- |
| **Mining** | ores / stone | block-break speed + **Fortune** on ores | **Haste** (lv 25+, holding a pickaxe), **Telekinesis** auto-pickup (lv 100) |
| **Foraging** | logs / leaves | block-break speed + **Fortune** on wood | — |
| **Combat** | killing mobs | +attack damage (~+10 at 100) | **life steal** on kills |
| **Farming** | harvesting crops | +max health (~doubles HP at 100) | — |
| **Acrobatics** | taking fall damage | **dodge** chance 0.6%/lvl → 60% at 100 | softer landings (fall-damage reduction) |
| **Fishing** | catching with a rod | +luck & faster bites while fishing | **treasure** catches |

Fortune never applies with Silk Touch (no dupes). Combat level also scales nothing
else — health lives on Farming.

| System | What it does |
| --- | --- |
| **Active abilities** | **Frenzy** (Combat, `R`) — a short Strength+Speed burst on a cooldown. **Leap** (Acrobatics, `V`) — a forward/upward dash with a safe landing. Both level-gated + cooldown-balanced. |
| **Level-up feedback** | Chat line, sound, on-screen **title**, and particles. |
| **Skills GUI** | Character level, every skill + XP bars, and ability keybinds — open with **`K`** or `/voxelia menu`. |
| **HUD** | Corner overlay with per-skill XP bars; position is configurable (anchor + offset). |
| **Quests** | A multi-step advancement chain that walks you through the skills. |
| **Leaderboard** | `/voxelia top <skill>` ranks online players. |
| **Config** | Every XP rate and reward coefficient is tunable in `config/voxelia_mmo-common.toml`; HUD prefs in `voxelia_mmo-client.toml`. |

### Commands

| Command | Side | Purpose |
| --- | --- | --- |
| `/voxelia skills` | server | View your levels and XP |
| `/voxelia top <skill>` | server | Leaderboard of online players |
| `/voxelia grant <skill> <amount>` | server (op) | Grant XP |
| `/voxelia menu` | client | Open the Skills GUI |
| `/voxelia hud` | client | Toggle the HUD |
| `/voxelia hudpos <corner>` | client | Move the HUD (`top_left`/`top_right`/`bottom_left`/`bottom_right`) |
| `/voxelia rewards` | client | Print what each skill level grants |

### Keybinds (rebindable in Options → Controls)

| Key | Action |
| --- | --- |
| `K` | Open the Skills menu |
| `R` | **Frenzy** (Combat ability) |
| `V` | **Leap** (Acrobatics ability) |

The server is authoritative: it owns skill XP, combat resolution, and perks, and
pushes a `SkillsSyncPayload` to each client, which only renders the HUD/GUI.

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
