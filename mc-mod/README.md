# Voxelia MMO — NeoForge mod

A **NeoForge mod for Minecraft Java Edition** that adds a complete MMO
progression layer: **11 skills** with XP and levels, per-skill **talent trees**,
**active abilities** with cooldowns, **prestige** at level 100, a **death XP
penalty**, and a full set of UI surfaces (skills screen, talent tree, character
profile, corner HUD, scoreboard sidebar). Builds to a standard mod **`.jar`**
via Gradle.

- **Minecraft:** 1.21.1
- **NeoForge:** 21.1.x (default `21.1.172`)
- **Java:** 21
- **Loader:** `javafml`

## The 11 skills

Every skill levels **1 → 100** on its own XP curve, stored as a persisted player
data attachment (kept on death). Each has passive per-level rewards **and** a
signature active ability (select with `G`, fire with `R`).

| Skill | Trains from | Per-level rewards | Ability (cooldown) |
| --- | --- | --- | --- |
| **Mining** | stone & ores | break speed, Fortune on ores | Miner's Focus — Haste + Night Vision (60s) |
| **Foraging** | logs & leaves | break speed, Fortune on wood | Overgrowth — bonemeal burst around you (45s) |
| **Combat** | killing mobs | attack damage, life steal on kills | Frenzy — Strength + Speed (50s) |
| **Farming** | harvesting crops | max health | Hearty Meal — Regen + Saturation (60s) |
| **Acrobatics** | fall damage | higher jumps, softer landings | Leap — dash (6s) |
| **Fishing** | catching fish | luck, treasure catches | Maelstrom — whirlpool that drags mobs in (90s) |
| **Excavation** | shovel blocks | dig speed, Fortune on shovel blocks | Excavate — mass-dig burst (180s) |
| **Defense** | taking damage | armor + toughness, Last Stand at low HP | Bulwark — deflect damage for a few seconds (300s) |
| **Cooking** | eating & cooking | saturation, Well Fed regen | Feast — party-wide feast buff (600s) |
| **Alchemy** | brewing | potion duration | Panacea — cleanse + resist (180s) |
| **Archery** | bow & crossbow hits | Power Shot damage on full draws | Volley — arrow storm (150s) |

Fortune never applies with Silk Touch (no dupes). Abilities are deliberately
**powerful but long-cooldown** — ultimates, not spam buttons.

## Progression systems

| System | What it does |
| --- | --- |
| **Talents** | Each skill has its own 5-talent tree (max rank 5 each). You earn **1 talent point per 8 levels** in that skill — 12 points by level 100. Spend them on the Talent screen (Menu ▸ Talent Tree). `/voxelia talent reset` refunds everything. |
| **Prestige** | At level 100 a skill can be **prestiged** (button on the Talent screen — takes **two clicks**, no accidents — or `/voxelia prestige <skill>`): it resets to level 1, its talents refund, and you gain **+2 permanent talent points** for that skill, granted immediately (14/16/18 possible points at Prestige 1/2/3, cap 3). Prestige stars (✦) show on every surface and in your chat title, a full-screen celebration + particles/sound plays, and the server gets one gold announcement line. |
| **Death penalty** | Dying costs **20% of every skill's XP** (config). Levels genuinely drop — the respawn message tells you what you lost. |
| **Character level & titles** | Your character level is the average of all skills. Chat shows `[Lv 42 • Master Miner ✦✦] Name`, ranked Novice → Grandmaster. |
| **Level-up feedback** | Chat line, sound, particles, and a "+XP" flash on the HUD — no full-screen popup. Earning a talent point announces itself in chat and on the action bar, so points don't sit unspent. |
| **Live perk readout** | Hover any skill card on the Skills screen to see exactly what it's granting you right now — "+12.5% break speed, +25% Fortune on ores, Haste w/ pickaxe" — with the server's own numbers, talent ranks folded in. No chat needed. |
| **Perk unlocks** | Every level-gated perk announces itself with a slim toast at the top of the screen and a chat line: each skill's **signature ability** (Leap at Acrobatics 15, Volley at Archery 30, and so on) plus the four passives — **Haste** (Mining 25), **Telekinesis** (Mining 100), **Last Stand** (Defense 20) and **Well Fed** (Cooking 20). The toast names the perk and tells you how to use it, and reads the real levels from config. |
| **Leaderboards** | The server records everyone who has ever played to `voxelia_leaderboard.json` in the world folder, so boards rank **offline players too**. Read them in-game at Menu ▸ Leaderboards, or with `/voxelia top <skill\|character>`. |

The server is authoritative for everything: XP, talents, prestige, abilities and
cooldowns are validated server-side; clients only render.

## UI surfaces

The mod claims **one screen keybind**. `K` opens the Skills screen — the hub — and
the **Menu** button in its top-right drops down everything else:

```
Menu ▾
  Skills [K]            ← you are here
  Talent Tree      (3)  ← unspent points show as a green pill
  Character Profile
  Leaderboards
  ───────────────
  Skill Sidebar   On/Off
  Corner HUD      On/Off
  HUD Corner      Top Left →  (click to cycle the four corners)
```

The same Menu button sits on every Voxelia panel, so you can hop between the three
screens and flip the display toggles without ever leaving the UI. ESC closes the
dropdown; ESC again closes the screen.

| Surface | Open with | Shows |
| --- | --- | --- |
| **Skills screen** | `K` / `/voxelia menu` | Card per skill with XP bars + tooltips; click a card to select its ability; Character card opens the profile |
| **Talent screen** | Menu ▸ Talent Tree | Skill list (with prestige stars + unspent-point pills), the selected skill's 5 talents, and the Prestige button when eligible |
| **Character profile** | Menu ▸ Character Profile, or `/voxelia profile` | Best skill, total prestiges, XP earned, playtime, deaths, mob kills |
| **Corner HUD** | Menu ▸ Corner HUD / HUD Corner (or `/voxelia hud`, `/voxelia hudpos`) | Per-skill levels + XP bars, prestige stars, selected ability with live cooldown |
| **Sidebar** | Menu ▸ Skill Sidebar (or `/voxelia sidebar`; off by default) | Vanilla-scoreboard-style list of all skill levels + Character line |
| **Leaderboards** | Menu ▸ Leaderboards, or `/voxelia leaderboards` | Server top ten per skill (or overall), your own rank in the footer |

New players (zero XP) get a one-line pointer to `K` on first login.

## Commands

| Command | Side | Purpose |
| --- | --- | --- |
| `/voxelia skills` | server | Your levels and XP |
| `/voxelia stats` | server | Combat/progression stats |
| `/voxelia bestiary` | server | Mob-mastery kill tallies |
| `/voxelia talents` | server | Your talent ranks |
| `/voxelia talent reset` | server | Refund all talent points |
| `/voxelia prestige <skill>` | server | Prestige a level-100 skill |
| `/voxelia top <skill\|character>` | server | Top ten, including offline players |
| `/voxelia grant <skill> <amount>` | server (op) | Grant XP |
| `/voxelia menu` / `profile` / `leaderboards` | client | Open the Skills / Profile / Leaderboard screens |
| `/voxelia hud` / `sidebar` | client | Toggle the HUD / sidebar (also in the Menu dropdown) |
| `/voxelia hudpos <corner>` | client | Move the HUD (`top_left`/`top_right`/`bottom_left`/`bottom_right`) |
| `/voxelia rewards` | client | Print what each skill grants |

## Keybinds (rebindable in Options → Controls)

Only three — everything that isn't an ability lives in the Skills screen's Menu.

| Key | Action |
| --- | --- |
| `K` | Skills menu (the hub) |
| `R` | Use selected ability |
| `G` | Cycle selected ability |

## Config

- **`config/voxelia_mmo-common.toml`** — every XP rate and reward coefficient,
  talent rules (`levelsPerPoint`, `maxRank`), prestige rules (`enabled`,
  `maxPrestige`, `pointsPerPrestige`), every ability cooldown, and the death
  penalty (`deathXpLossPercent`).
- **`config/voxelia_mmo-client.toml`** — HUD visibility/panel/corner/offsets and
  the sidebar toggle.

## Build the jar

Requires JDK 21 and network access to the NeoForge + Mojang Maven repositories.

```bash
cd mc-mod
./gradlew build
```

`./gradlew build` also runs the JUnit test suite (game-bootstrapped unit tests).
The mod jar is produced at:

```
build/libs/voxelia_mmo-neoforge-1.21.1-1.0.0.jar
```

Drop it into the `mods/` folder of a Minecraft **1.21.1** instance running
**NeoForge 21.1.x** (client and/or server).

### Run it in a dev environment

```bash
./gradlew runClient    # launches a dev client with the mod loaded
./gradlew runServer    # launches a dev server
```

### Download a pre-built jar (CI)

Every push builds and tests the mod on GitHub Actions and publishes the jar to
the rolling **`voxelia-mod-latest`** release, plus an artifact on the run page
of **[Build NeoForge mod](../../actions/workflows/build-mod.yml)**.

> ℹ️ **Build environment note.** This project was authored in a sandbox whose
> network policy **blocks `maven.neoforged.net` and `libraries.minecraft.net`
> (HTTP 403)**, so `./gradlew build` could not run *there*. It builds cleanly
> anywhere with normal network access — **verified green on GitHub Actions**,
> which compiles against the real NeoForge 21.1 API.

## Retargeting another Minecraft / NeoForge version

Edit `gradle.properties` — `minecraft_version`, `neo_version`, and the matching
version ranges — then rebuild. The Java targets the 1.21.x NeoForge API surface;
larger MC jumps may need small source updates.

## Project layout

```
mc-mod/
├── build.gradle / settings.gradle / gradle.properties   ModDevGradle build
├── gradlew / gradlew.bat / gradle/wrapper/              Gradle wrapper
└── src/
    ├── main/java/com/voxelia/mmo/
    │   ├── VoxeliaMMO.java                 @Mod entry point
    │   ├── skill/        Skill, SkillCurve, PlayerSkills, PlayerTalents, PlayerPrestige, Talent
    │   ├── registry/     VoxeliaAttachments, VoxeliaEntityAttributes
    │   ├── progression/  Progression, SkillEffects, TalentLogic, PrestigeLogic, Abilities
    │   ├── event/        ProgressionEvents, ChatTitleEvents, BonusDropEvents, ...
    │   ├── network/      payloads + VoxeliaNetwork (server-authoritative sync)
    │   ├── command/      VoxeliaCommands
    │   ├── config/       VoxeliaConfig, VoxeliaClientConfig
    │   └── client/       screens (Skills/Talent/Profile), HUD, sidebar,
    │                     prestige celebration, keybinds, client caches
    ├── main/templates/META-INF/neoforge.mods.toml       (expanded at build)
    ├── main/resources/   pack.mcmeta, lang, advancements
    └── test/java/        JUnit tests (skill curve, talents, prestige, death penalty)
```
