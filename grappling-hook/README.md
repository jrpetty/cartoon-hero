# Grappling Hook

A standalone [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.1** that adds
a **charge-and-release** grappling hook.

## What it does

Hold **right-click** to draw the hook like a bow, then release to fire:

- A quick **tap** casts a short line and grapples only a short distance.
- Holding for the full **2-second charge** reaches the maximum range.
- On release it "casts" toward the block you're aiming at — playing the fishing
  bobber throw/retrieve sounds and drawing a particle line to the anchor — then
  yanks you there with a small upward boost (and cancels the fall damage from
  the swing).
- After each grapple there's a short **cooldown**, so it can't be spammed.

If your charge isn't enough to reach the block you're aiming at, the cast falls
short and nothing happens — charge longer for distant anchors.

## Configuration

On first launch the mod writes **`config/grapplinghook.properties`**. Edit it and
restart to tune:

| Key | Default | Meaning |
| --- | --- | --- |
| `chargeTicks` | `40` | Ticks of charge for full power (20 ticks = 1 second) |
| `minRange` | `8.0` | Reach of a quick tap (blocks) |
| `maxRange` | `40.0` | Reach of a full charge (blocks) |
| `pullSpeedFactor` | `0.30` | How hard pull speed scales with distance |
| `maxPullSpeed` | `3.0` | Hard cap on launch speed |
| `upwardBoost` | `0.30` | Extra lift added to every pull |
| `cooldownTicks` | `20` | Cooldown after a grapple (20 ticks = 1 second) |

## Crafting

Iron ingots + string:

```
 II
 IS
S
```

## Build & run

> ⚠️ The first build downloads Minecraft / Yarn / Fabric API, so it needs network
> access to `maven.fabricmc.net` and `piston-meta.mojang.com`.

Requirements: **JDK 21**, Fabric Loader + [Fabric API](https://modrinth.com/mod/fabric-api).

```bash
cd grappling-hook
./gradlew runClient
./gradlew build       # -> build/libs/grappling-hook-1.0.0.jar
```

The hook appears in the **Tools & Utilities** creative tab.

## Ideas for v2

- A fully *rendered* rope between you and the anchor (needs a client renderer +
  a thrown hook entity); the current cast is represented with a particle line.
- Reeling in while held, and detaching mid-swing to preserve momentum.
