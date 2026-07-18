# Aztec Abyss

A NeoForge mod for Minecraft 1.21.1: a self-contained, bounded dimension reached
through a black-flamed portal, built around a stepped Aztec temple, running a
Call of Duty Zombies-style round survival mode up to Round 20.

## Why you're getting source, not a `.jar`

This was built in a sandboxed environment whose network policy blocks every
Minecraft-related host (Mojang's piston-meta, `maven.neoforged.net`,
`maven.fabricmc.net`, `files.minecraftforge.net`). NeoGradle needs those to
download the Minecraft/NeoForge toolchain before it can compile anything, so
`./gradlew build` could not be run here. Everything below is real, complete
source — running the one command in **Build it** on a machine with normal
internet access will produce the `.jar`.

## Build it

Requires **only JDK 21** — the Gradle wrapper is bundled, so you don't need
Gradle installed. From the project root:

- **macOS / Linux:** `./gradlew build`
- **Windows:** `gradlew.bat build`

On first run the wrapper downloads Gradle 8.14.3, then Gradle downloads the
NeoForge/Minecraft toolchain (a few minutes, one time). The mod jar lands in
`build/libs/aztecabyss-1.0.0.jar`. Drop it in a NeoForge 1.21.1
(`neo_version` in `gradle.properties`, currently `21.1.234`) server or client's
`mods` folder.

> The wrapper jar, `gradlew`, `gradlew.bat`, and `gradle-wrapper.properties`
> are all included and verified to launch correctly. The *only* reason the
> jar wasn't pre-built for you is that the environment this was authored in
> firewalls every Minecraft/NeoForge/Gradle-distribution host (confirmed:
> hard HTTP 403 from `maven.neoforged.net`, `piston-data.mojang.com`,
> `services.gradle.org`, et al.). Any machine with normal internet builds it
> in one command.

To test locally without a full server:

```
./gradlew runClient
```

## What's new in v6 (leaderboard monument)

A physical **stone monument** stands beside the arrival spawn (`MONUMENT_POS`) —
a polished-blackstone slab with gold trim whose front is carved, on Warped wall
signs, with the **top 3 survivors in Solo and Co-op**, ranked by survival time
(name + time + best round). It's written when the arena generates and **rewritten
live every time a run ends**, so the standings are always current. You read it
the moment you step through the portal.

Placed at the arrival spawn (mod-owned ground) rather than auto-building at
players' overworld portals, so it can never overwrite someone's base. Built in
`worldgen/MonumentBuilder`.

## What's new in v5 (extraction gambit)

After **every** cleared round (never during a wave), a glowing extraction glyph —
a lantern platform with an end-rod beacon beam — appears on the south approach
(`AztecAbyssConstants.EXTRACTION_POS`). Stand on it for a short channel and you
**bail out with your rewards banked and no cooldown**, instead of risking the
next wave for a bigger prize. The tension: cash out at round 12, or gamble for
round 20's grand prize and eat the 20-hour cooldown if you fall?

- **Between-rounds only.** The glyph is placed on round-clear and removed the
  instant the next round starts.
- **Holds the round.** While anyone is channelling on the glyph, the next wave
  won't start — so you're never forced off it by the timer.
- **Per-player in co-op.** Each teammate decides independently; one can extract
  while the others push on. When the last player leaves, the session resets.
- **Its own recap.** Extracting shows a blue "YOU ESCAPED" recap (vs. gold
  victory / red death).
- Tunable in `config` → `enableExtraction`, `extractionChannelTicks`.

Implemented in `RoundManager` (`tickExtraction`, `setExtractionGlyph`,
`extractPlayer`).

## What's new in v4 (enemy variety)

The wave is no longer just zombies. A weighted roster (`round/WaveMobs`) widens as
the rounds climb, so the threat keeps changing shape — all sharing the same
per-round stat scaling and the brute modifier:

| Unlocks at round | Enemy | Role |
|---:|---|---|
| 1 | Zombie | melee backbone |
| 2 | Skeleton | ranged, forces movement |
| 3 | Husk | tougher zombie |
| 4 | Spider | fast, climbs |
| 6 | Creeper | explosive (block damage disabled in the arena — hurts you, not the walls) |
| 7 | Stray | ranged + slowness arrows |
| 8 | Cave Spider | fast + poison |
| 10 | Witch | throws potions |
| 12 | Wither Skeleton | tanky melee |
| 14 | Pillager | crossbow |
| 16 | Vindicator | heavy melee |
| 18 | Phantom | attacks from above |

Every round is a **live mix** — each individual spawn is rolled independently, so a
wave is never a single type. The composition also **drifts by round**: each type
has a difficulty tier that makes its share fade (easy mobs) or grow (deadly mobs)
over time. Sample shares: round 3 is ~51% zombie / husk / skeleton; round 10 is a
broad 8-type spread (zombie down to ~22%); round 20 is a 12-type churn led by
wither skeletons, vindicators and phantoms, with zombies down to ~7%. Ranged mobs
are handed the right weapon (bow/crossbow/sword) with drops disabled so they don't
spam loot. Every 5th round still mixes in armored **brutes** (any type can be one).
The tiers, unlock rounds and drift rates live in `WaveMobs` (`TABLE` + `ramp()`).

## What's new in v3 (immersion pass)

- **Living, reacting temple.** Each round start erupts the altar into a taller
  lava/ember fountain, flares the braziers and summit beacon, and thickens the
  mist — escalation implemented as particles (via `ArenaGenerator.escalateTemple`)
  so the play floor never floods. The client fog also reddens toward the finale.
- **Creepy "Upside Down" perimeter.** The inner wall is dressed with creeping
  sculk, glowing shriekers, hanging vine tendrils, and half-buried skulls staring
  inward (`ArenaGenerator.decoratePerimeter`). Skittish **bats** drift the arena
  (capped population), and client-side **wisps** scatter away as you approach.
- **Arrival cinematic.** Stepping through swings the view to settle on the temple,
  crawls a staged title, and swells a low drone before the first round horn —
  `client/AbyssClientEffects.playArrivalCinematic`, triggered by the state sync.
- **Death/victory recap screen.** When a run ends you get a styled summary —
  round reached, survival time, kills, revives, and a "new personal best" callout
  — instead of just a chat line (`network/RunRecapPayload` → `client/RunRecapScreen`).
- **Separate solo & co-op leaderboards.** `AbyssStats` now tracks each category
  independently, ranked by **longest survival** (then best round). See
  `/abyss leaderboard solo` and `/abyss leaderboard multiplayer`, and `/abyss stats`
  shows both plus lifetime kills/revives/clears. A run counts as co-op if it ever
  had more than one participant.

## What's new in v2 (temple / co-op / progression / Easter egg)

- **World-class temple interior.** The pyramid is now hollow: a lava-lit altar
  chamber with a gold-inlay floor, four ritual braziers, a trapped approach
  corridor (magma tiles over a lava channel), and a **sealed vault beneath the
  altar** holding a grand hoard. Exterior verticality (the grand staircase and
  summit altar) is unchanged. See `worldgen/TempleBuilder.java`; all landmark
  positions are fixed in `AztecAbyssConstants`.
- **True co-op.** One shared `AbyssGame` per arena — shared round counter,
  shared zombie pool, kills credited to whoever lands them. Instead of instant
  death, a downed player **bleeds out** (glowing, immobilised, invulnerable) and
  a teammate revives them by standing close; if everyone is down, it's a wipe.
  Solo play still works — you're just a party of one. (`round/AbyssGame.java`,
  `round/RoundManager.java`, `event/AbyssEventHandler.java`.)
- **"Upside Down" atmosphere.** Dark blood-red fog that closes in as the round
  climbs, drifting spore/ash motes, occasional red lightning flashes with
  thunder, and a low-health red vignette + quickening heartbeat. The client
  learns the round via a small sync packet (`network/`), and the effects live in
  `client/AbyssClientEffects.java`.
- **Audio.** Custom sound events (`registry/ModSounds`) for round start/clear,
  ambient dread, heartbeat, ritual, downed/revived — mapped in `sounds.json` to
  fitting vanilla cues so they work immediately. Drop your own `.ogg` files into
  `assets/aztecabyss/sounds/` with matching names and flip the entry `type` from
  `event` to `sound` to use real audio.
- **Progression & QoL.** A starting loadout on entry, a full config file
  (`config/AbyssConfig` → `config/aztecabyss-common.toml`) exposing every
  tunable, a persistent leaderboard (`data/AbyssStats`) shown via `/abyss stats`
  and `/abyss leaderboard`, an entry-confirmation prompt (step out and back in
  to commit), and a live cooldown display.
- **Signature Easter egg.** Light the four altar braziers in the correct hidden
  order, then present a Golden Apple on the altar pedestal — the vault dissolves
  open and the ritual boosts everyone's end-of-run reward. Fully hidden in-game;
  the "spoiler" walkthrough is in `event/RitualHandler.java`.

## What's implemented (base mode)

- **Custom portal** — build a rectangle of *Abyssal Obsidian* (a new block,
  not vanilla obsidian) and light it with flint & steel. It ignites with a
  black flame and a swirling black-particle portal surface instead of the
  nether's purple. Frame detection/fill logic lives in `AbyssPortalShape`
  (an from-scratch adaptation of vanilla's nether portal algorithm, since it's
  hardcoded to obsidian and can't be reused directly).
- **The Abyss dimension** — small, dark, always-night, bounded by a genuine
  bedrock wall backed by a matching `WorldBorder` (belt-and-suspenders: you
  cannot dig, push, glide, or piston your way past it). Sky effects borrow the
  End's void-black rendering for the "Upside Down" feel. Digging is disabled
  everywhere except ore veins, chests, and the temple's fire blocks
  (`AbyssEventHandler#onBlockBreak`) — the world is otherwise as solid as
  bedrock.
- **The temple** — a 9-tier stepped Aztec pyramid, ~27 blocks tall, built
  procedurally (`TempleBuilder`) with a mix of chiseled/mossy/cracked/polished
  blackstone brick facing, terracotta glyph banding, alternating soul-fire and
  fire corner braziers on every other tier, a single grand staircase facing
  the fixed arrival point, and a roofed summit altar. It's deterministic (a
  fixed seed) — every world gets the identical temple. A forest ring of dark
  oak and spruce surrounds it, thinned out directly south of the arrival
  portal so the temple is visible through the trees the moment you step in,
  per the brief.
- **Fixed arrival point** — every portal, anywhere, leads to the same spot in
  the Abyss (`AztecAbyssConstants.ABYSS_ARRIVAL_POS`), always facing the
  temple. Loot: diamond/iron/gold/coal ore veins scattered through the
  forest/temple perimeter, plus a handful of randomized loot chests.
- **Round-based Zombies mode** (`RoundManager`) — enter and a 5-second
  breather starts Round 1. Each round's zombie count, health, damage, speed
  and armor scale up (round 20 is close to unbeatable solo, by design — see
  the tuning constants at the top of `RoundManager` if you want it easier).
  Every 5th round includes an armored "brute" zombie (bigger, tougher,
  damage-resistant). Progress shows on a boss bar plus round-start/round-clear
  titles and sounds.
- **Win/lose resolution**:
  - **Die** in the Abyss: no death screen loop — it's caught and resolved as
    a "run over" event. You're sent home, a reward chest scaled to the round
    you reached spawns next to the portal you left from, and re-entry is
    **sealed for 20 real-world hours** (`RunState.cooldownUntil`, persisted
    through logout/restart).
  - **Clear Round 20**: an intentionally generous reward (full netherite gear,
    multiple totems, an elytra, nether stars — see `RewardTable.grandPrize()`).
  - **Retreat** (walk back into the arrival portal without dying): sends you
    home with no reward and, deliberately, no cooldown — a "chicken out"
    option distinct from death.
- **Persistence** — round progress, cooldown, and which portal is "home" are
  stored per-player via a NeoForge data attachment (`RunState`, survives
  logout and death).

## Known simplifications (things a real playtest pass would refine)

- **Multiplayer is now genuine co-op** (this was the old solo limitation, now
  addressed): everyone in the arena shares one round, one zombie pool, and the
  downed/revive loop. The remaining design constraint is that there's still a
  *single* arena — a second party can't run their own separate game at the same
  time. Per-party instanced arenas would be the next step.
- **No custom portal shader**: vanilla's nether portal has a hardcoded
  "swirl distortion" render effect tied specifically to `Blocks.NETHER_PORTAL`
  that isn't reachable for a custom block without mixins. The Abyss portal
  still looks distinct (black animated texture + custom black particle), just
  without that specific fisheye effect.
- **No enchantments on reward gear**: items in `RewardTable` are deliberately
  strong vanilla items (netherite gear, totems, nether stars) rather than
  specifically-enchanted ones, to avoid depending on 1.21's data-driven
  enchantment registry lookups sight-unseen. Trivial to extend once you're
  building against the real API in an IDE with autocomplete.
- **Verified against a known-good build.** The core API surface was
  cross-checked, method-by-method, against the compiled bytecode of another
  working NeoForge 21.1.234 mod. Confirmed matching: the whole registry layer
  (`DeferredRegister.createBlocks`/`createItems`, `register(String, Supplier)`,
  block-items, creative tabs, `DeferredBlock`/`DeferredHolder`), the
  `BlockBehaviour.Properties` builder chain, the portal block shape
  (`extends Block` + `animateTick` override), zombie spawning
  (`EntityType.create(Level)`, the 4-arg `finalizeSpawn(..., MobSpawnType, ...)`,
  `addFreshEntity`, `setPersistenceRequired`), `LevelTickEvent.Post`, and
  `ServerLevel.playSound`. This pass also **caught and fixed** one genuine
  API break: dimension transfer originally used NeoForge's old `ITeleporter`,
  which 1.21.1 replaced with vanilla's `Entity#changeDimension(DimensionTransition)`
  — now corrected.
- **Still unverified** (stable vanilla/NeoForge API, but not exercised by the
  reference mod I cross-checked against — if any needs a nudge on first compile
  it'll be a one-liner, not a redesign). Grouped by area so they're easy to
  check in an IDE:
  - *Packets/UI:* `ClientboundSetTitleTextPacket` / `…SubtitleTextPacket` /
    `…ActionBarTextPacket`, and `DimensionTransition.PLAY_PORTAL_SOUND`.
  - *Events:* `PlayerTickEvent.Post`, `LivingIncomingDamageEvent`,
    `RegisterCommandsEvent`.
  - *Client FX (new):* `ViewportEvent.RenderFog` /
    `ViewportEvent.ComputeFogColor` setters, `RenderGuiEvent.Post` +
    `GuiGraphics.fill/fillGradient`, `ClientTickEvent.Post`,
    `ParticleTypes.WARPED_SPORE`.
  - *Networking (new):* the payload API — `RegisterPayloadHandlersEvent`,
    `event.registrar(...)`, `registrar.playToClient(...)`,
    `StreamCodec.composite(...)`, `PacketDistributor.sendToPlayer(...)`. This is
    the single most version-sensitive area; it follows the documented NeoForge
    1.21.1 shape but is worth a look first if the client atmosphere doesn't sync.
  - *SavedData (new):* `SavedData.Factory` + `getDataStorage().computeIfAbsent`
    and the `save/load(CompoundTag, HolderLookup.Provider)` signatures in
    `data/AbyssStats`.
  - *Signs (v6):* `SignBlockEntity.updateText(UnaryOperator<SignText>, boolean)`,
    `SignText.setMessage`, and `Blocks.WARPED_WALL_SIGN` in `MonumentBuilder`.
  - *Client screen / cinematic (v3):* the `Screen` + `GuiGraphics`
    (`drawCenteredString`/`drawString`/`pose()`) API in `RunRecapScreen`, and
    `Minecraft.gui.setTitle/setSubtitle/setTimes` plus `player.setYRot/setXRot`
    in the arrival cinematic. Stable, but not exercised by the reference mod.
  - *Note:* `StreamCodec.composite` caps at 6 field-pairs in 1.21.1 — the recap
    payload packs its two booleans into one `flags` int to stay within that. If
    you add fields to a payload, keep it ≤6 or nest a sub-codec.

## Tuning

Most balance now lives in **`config/aztecabyss-common.toml`** (generated from
`config/AbyssConfig` on first run): round count, zombie counts, the global
`roundSizeMultiplier` (default **1.6** = +60% zombies per round), the
`maxConcurrentAlive` ceiling (default **120**), health/damage scaling, round
pacing, cooldown hours, entry confirmation, bleedout/revive timings, starting
loadout, and the Easter-egg toggle. With the current defaults a solo round holds
`round((6 + 4×round) × 1.6)` zombies — ~16 at round 1, ~74 at round 10, ~138 at
round 20 — and up to 120 can be alive at once. **Party scaling compounds**: each
extra player multiplies the wave by `perPlayerScaling` (default 1.6 = +60% per
head), so at round 20 a 2-player lobby faces ~221, 3-player ~353, 4-player ~565.
Deeper knobs still in code:

- Reward tiers / grand prize / ritual bonus: `RewardTable` and
  `RitualHandler.completeRitual()`.
- Brute cadence and per-zombie stats: `RoundManager.applyRoundScaling()`.
- Arena size / temple size / ritual landmark positions / arrival point: top of
  `AztecAbyssConstants`.
- Ritual brazier order: `AztecAbyssConstants.RITUAL_ORDER`.

## Project layout

```
src/main/java/com/jrpetty/aztecabyss/
  AztecAbyssMod.java          mod entry point
  AztecAbyssConstants.java    all the fixed numbers in one place
  block/                      portal frame + portal block + frame-detection logic
  dimension/                  fixed-position teleporter
  worldgen/                   arena/temple/forest procedural generation
  round/                      round loop, per-player run state, rewards
  event/                      portal ignition/travel, dig restriction, death handling
  particle/, client/          the black portal particle
  registry/                   DeferredRegister glue

src/main/resources/
  data/aztecabyss/            dimension, dimension_type, biome, loot table
  assets/aztecabyss/          textures (generated placeholders), models, lang
```

## Textures

The block/particle textures included are simple generated placeholders (dark
noise-veined stone for Abyssal Obsidian, an animated black/purple strip for
the portal surface, a soft dark blob for the particle) so the mod isn't
missing any art and won't show purple/black "missing texture" checkers. Swap
them for real art in `src/main/resources/assets/aztecabyss/textures/` — same
filenames, any 16x16 (or 16x64 for the animated portal strip) PNG will drop
in without touching any Java or model JSON.
