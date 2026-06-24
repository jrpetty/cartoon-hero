# Automata — a Minecraft server that reimagines how the game is played

A [Fabric](https://fabricmc.net/) Minecraft server whose only mod is one written
from scratch by Claude: **Automata**. The natural blocks you know — wood, stone,
ores, coal — stay exactly the same. What changes is the core loop:

> **The crafting table and furnace are gone.** In their place are two
> self-running machines you automate with hoppers. You start by feeding them by
> hand; you end up building factories that run themselves.

- **Minecraft version:** 1.21.1
- **Mod loader:** Fabric (loader 0.16.5)
- **Java required:** 21+

```
minecraft/
├── mods/automata/        # The Claude-made mod (Fabric + Gradle project)
│   ├── src/main/java/    # Mod source code
│   ├── src/main/resources/  # fabric.mod.json, models, textures, recipes, loot
│   ├── build.gradle      # Fabric Loom build
│   └── gradlew           # Gradle wrapper (no system Gradle needed)
└── server/
    ├── setup.sh          # Builds the mod + provisions the server
    └── start.sh          # Launches the server
```

## The machines

All three are block entities. They accept items from **hoppers** on the top and
sides and push finished goods to a **hopper** on the bottom — so once you have
hoppers and chests, production is hands-free. Before that, you interact by hand:

- **Right-click with an item** → load it into the machine.
- **Right-click empty-handed** → pull the finished output.

| Machine | Replaces | What it does |
|---------|----------|--------------|
| **Combustion Dynamo** | — (new) | Burns fuel (coal, logs, blaze rods…) from its **intake** to fill an energy buffer, and emits **Ash** from its **outtake** as a byproduct. Powers adjacent machines. |
| **Thermal Generator** | — (new) | Burns **lava buckets** for a large, long-lasting power supply, returning the empty bucket. A late-game alternative to the Dynamo. |
| **Solar Array** | — (new) | Passive, fuel-free power in **daylight** with a clear view of the sky. Output drops in rain and stops at night. |
| **Capacitor Bank** | — (new) | Stores a large energy buffer, smoothing supply through fuel gaps. It pulls from adjacent sources and supplies adjacent machines, so chaining them also **relays power** one block further. |
| **Forge Core** | Furnace | Auto-smelts ores, raw metals, sand, stone, food and logs — **but only while an adjacent Dynamo is supplying power**. No power, no smelting. |
| **Fabricator** | Crafting table | Auto-assembles components, machines, the items the disabled crafting table used to make (chest, hopper, bucket), and the Pulsar Multi-Tool. Runs unpowered, but ~5× faster next to a powered Dynamo. |
| **Crusher** | — (new) | Grinds ores and raw metals into **two dust** each; a Forge Core then smelts dust back into ingots, so a Crusher → Forge line **doubles every ore**. Also cobblestone→gravel→sand. Slow by hand, ~5× faster powered. |
| **Sawmill** | — (new) | Turns one log into **six planks** (vs four by hand). Slow by hand, ~5× faster powered. |
| **Recycler** | — (new) | Melts unwanted iron/gold tools and armor back into **nuggets** (at a loss). Slow by hand, faster powered. |
| **Fluid Pump** | — (new) | Fills empty buckets from a water/lava **source directly below it**, bridging fluids into the item network. Water is left in place; lava is consumed. Feeds farms and the Thermal Generator. |
| **Auto-Miner** | — (new) | Drills the column straight beneath it, collecting ore/stone drops into its output (then onward via hoppers). Stops at bedrock. Slow by hand, ~5× faster powered. |
| **Item Collector** | — (new) | Vacuums up dropped items in a radius into its output slots — the glue for tree/mob farms and Auto-Miner overflow. Radius 2 by hand, 5 when powered. |
| **Logistics Router** | — (new) | Pulls items from an adjacent inventory and sends them to linked inventories **anywhere**, with an item filter and round-robin balancing. Replaces long hopper chains. Slow by hand, faster powered. |
| **Item Sorter** | — (new) | Items in the **top**; matched items out the **bottom** (sorted), everything else out the **sides** (reject). Right-click with an item to add it to the sort list. Chain sorters to split a mixed stream into labeled chests. Slow by hand, faster powered. |

The processing and gathering machines share Automata's signature rhythm: they
work unpowered but slowly, and run several times faster (or reach further) when
an adjacent Dynamo supplies power.

### Automating the world

The endgame is a base that runs itself. A self-feeding resource line looks like:

```
Auto-Miner ─(hopper)→ Crusher ─(hopper)→ Forge Core ─(hopper)→ chest
     │                   │                   │
     └───────────── Combustion Dynamos power each machine ─────────┘
```

Drop an **Auto-Miner** on a column, hopper its output into a **Crusher**, hopper
that into a **Forge Core**, and hopper the ingots into a chest — every machine
flanked by a fuel-fed **Dynamo**. Add **Item Collectors** under tree farms or mob
grinders to pull drops into the same network. Once it's wired, you only ever
refill fuel.

For longer distances, skip the hopper chains and use the **Logistics Router**
with the **Logistics Wrench**:

1. Place a Router touching the inventory you want to pull *from*.
2. Hold the Wrench and right-click the Router to select it.
3. Right-click any chests/machines (anywhere) with the Wrench to add them as
   destinations.
4. Right-click the Router with an item to add it to the filter (only those
   items are sent; empty filter = send everything). Sneak-right-click empty-
   handed to clear; right-click empty-handed to see its status.

The Router distributes round-robin across destinations and, like every machine,
moves more per tick when an adjacent Dynamo powers it.

### Power

Power comes from any **power source** — the **Combustion Dynamo** (burns fuel,
emits Ash), the **Thermal Generator** (burns lava buckets), or the **Solar
Array** (free daytime power). Any machine placed **directly adjacent** to a
source draws from its buffer. A **Capacitor Bank** both stores energy and
relays it, so you can chain capacitors to reach machines a bit further away (and
ride out the night on solar). This is why fuel matters: smelting is no longer
free — it runs on the power your sources make.

The vanilla crafting table and furnace recipes are **disabled** (via a datapack
override that points them at an unobtainable item), so the machines are the way
to play. Your personal 2×2 inventory grid still works for planks, sticks and
torches — and for the one bootstrap recipe below.

## How a game starts (the manual twist)

1. Punch a tree for **logs**, mine **stone** for cobblestone, grab some **coal**
   and a little **redstone**.
2. In your 2×2 inventory grid, craft a **Fabricator**: two logs on top, two
   cobblestone on the bottom.
3. Place the Fabricator (it runs unpowered, just slowly). Hand-feed it to build a
   **Combustion Dynamo** (8 cobblestone + 1 redstone) and a **Forge Core**
   (8 cobblestone + 1 coal).
4. Place the Forge Core **next to** the Dynamo. Feed the Dynamo some coal — it
   starts generating power, and the Forge Core can now smelt your raw iron.
5. Feed iron into the Fabricator to make **Iron Gears → Machine Frames**, then
   **Chests** and **Hoppers**. Park a Dynamo next to the Fabricator too for a
   big speed boost.
6. Wire hoppers + chests into the machines. Congratulations — you now have
   powered, automated smelting and assembly, and the game becomes about
   designing flows (and keeping the Dynamos fed).

### The Fabricator recipe book

Recipes are shapeless: load the exact ingredients into the input slots and it
assembles on a timer. (One Fabricator per recipe is the intended factory
pattern.)

| Output | Ingredients |
|--------|-------------|
| Iron Gear | 4 iron ingots |
| Machine Frame | 4 iron ingots + 2 iron gears |
| Forge Core | 8 cobblestone + 1 coal |
| Combustion Dynamo | 8 cobblestone + 1 redstone |
| Fabricator | 1 machine frame + 4 cobblestone |
| Crusher | 1 machine frame + 2 iron gears |
| Sawmill | 1 machine frame + 1 iron gear + 1 iron ingot |
| Chest | 8 oak planks |
| Hopper | 5 iron ingots + 1 chest |
| Bucket | 3 iron ingots |
| Black Dye | 4 ash |
| **Pulsar Multi-Tool** | 1 machine frame + 2 iron ingots |

**Ore doubling:** crush raw iron (or an iron ore block) into 2 Iron Dust, then
smelt the dust in a Forge Core for 2 iron ingots — double what direct smelting
gives. Same for gold and copper.

The **Pulsar Multi-Tool** is one tool that mines like a pickaxe, axe, shovel and
hoe at once (iron tier) — a deliberate simplification of vanilla's tool zoo.

## Quick start (on a machine with internet)

The build and the server need to reach Mojang and Fabric servers, so run these
on your own machine (not a restricted sandbox).

```bash
cd minecraft/server
./setup.sh                    # builds the mod + fetches Fabric server/API
echo 'eula=true' > eula.txt   # accept the Minecraft EULA
./start.sh
```

Connect from the Minecraft 1.21.1 Java Edition client (with the same Fabric
loader + Fabric API installed) to `localhost` (or your machine's IP).

## Building the mod by itself

```bash
cd minecraft/mods/automata
./gradlew build
# Output: build/libs/automata-1.0.0.jar
```

Drop that jar (plus Fabric API) into any Fabric 1.21.1 server's or client's
`mods/` folder.

## Notes

- **Versions** are defined in `mods/automata/gradle.properties` and mirrored in
  `server/setup.sh` — change them together if you upgrade Minecraft.
- **Textures** are simple generated placeholders. Re-run
  `mods/automata/gen_textures.py` to tweak them, or replace the PNGs under
  `src/main/resources/assets/automata/textures/` with real art.
- The first `./gradlew build` downloads Minecraft, mappings and Fabric
  libraries, so it takes a few minutes; later builds are fast.
- **Extending it:** new machines are cheap to add — subclass `MachineBlockEntity`
  for the inventory/hopper plumbing and add a recipe map like
  `FabricatorRecipes`. The base class already handles SidedInventory, NBT and
  hand interaction.
