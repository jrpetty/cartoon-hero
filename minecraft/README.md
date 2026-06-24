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

## The two machines

Both are block entities. They accept items from **hoppers** on the top and
sides and push finished goods to a **hopper** on the bottom — so once you have
hoppers and chests, production is hands-free. Before that, you interact by hand:

- **Right-click with an item** → load it into the machine.
- **Right-click empty-handed** → pull the finished output.

| Machine | Replaces | What it does |
|---------|----------|--------------|
| **Forge Core** | Furnace | Auto-smelts ores, raw metals, sand, stone, food and logs. No fuel — it builds heat passively and smelts one item every ~10s. |
| **Fabricator** | Crafting table | Auto-assembles components, machines, the items the disabled crafting table used to make (chest, hopper, bucket), and the Pulsar Multi-Tool. |

The vanilla crafting table and furnace recipes are **disabled** (via a datapack
override that points them at an unobtainable item), so the machines are the way
to play. Your personal 2×2 inventory grid still works for planks, sticks and
torches — and for the one bootstrap recipe below.

## How a game starts (the manual twist)

1. Punch a tree for **logs**, mine **stone** for cobblestone, grab some **coal**.
2. In your 2×2 inventory grid, craft a **Fabricator**: two logs on top, two
   cobblestone on the bottom.
3. Place the Fabricator. Hand-feed it **8 cobblestone + 1 coal** → it builds a
   **Forge Core**. Now you can smelt.
4. Smelt raw iron in the Forge Core. Feed iron into the Fabricator to make
   **Iron Gears → Machine Frames**, and then **Chests** and **Hoppers**.
5. Wire hoppers + chests into the machines. Congratulations — you now have
   automated smelting and assembly, and the game becomes about designing flows.

### The Fabricator recipe book

Recipes are shapeless: load the exact ingredients into the input slots and it
assembles on a timer. (One Fabricator per recipe is the intended factory
pattern.)

| Output | Ingredients |
|--------|-------------|
| Iron Gear | 4 iron ingots |
| Machine Frame | 4 iron ingots + 2 iron gears |
| Forge Core | 8 cobblestone + 1 coal |
| Fabricator | 1 machine frame + 4 cobblestone |
| Chest | 8 oak planks |
| Hopper | 5 iron ingots + 1 chest |
| Bucket | 3 iron ingots |
| **Pulsar Multi-Tool** | 1 machine frame + 2 iron ingots |

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
