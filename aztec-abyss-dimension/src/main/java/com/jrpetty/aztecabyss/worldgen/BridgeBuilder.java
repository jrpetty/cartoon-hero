package com.jrpetty.aztecabyss.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

import java.util.Random;

/**
 * "The Long Bridge": a night-black last stand high over the void. Players hold
 * a fortified island at the south end while the entire horde funnels down a
 * single 20x100 bridge from a gate at the far north end. Sheer walls stand 40
 * blocks off each flank, and there is nothing below but the drop.
 *
 * Built far along +X from the temple arena so both maps share one dimension.
 * Fully deterministic, and a no-op once the fort's sentinel block exists.
 */
public final class BridgeBuilder {

    private static final long SEED = 20260726L;

    // ------------------------------------------------------------------
    // Layout - all fixed so the arena is identical on every server.
    // ------------------------------------------------------------------

    public static final int CENTER_X = 2000;
    /** Deck height; everything below this is open void. */
    public static final int DECK_Y = 70;

    /** Bridge is 20 wide: x in [CENTER_X-10, CENTER_X+9]. */
    public static final int HALF_WIDTH = 10;
    /** Bridge is 100 long, running north (mobs) to south (players). */
    public static final int NORTH_END = -50;
    public static final int SOUTH_END = 49;

    /** Circular island at the south end - 40 blocks across. */
    public static final int ISLAND_CENTER_Z = 68;
    public static final int ISLAND_RADIUS = 20;

    /** Rough middle of the playable span, used to centre the world border. */
    public static final int CENTER_Z = 15;

    /** Where players spawn in - behind the fort, on the island. */
    public static final BlockPos ARRIVAL = new BlockPos(CENTER_X, DECK_Y + 1, ISLAND_CENTER_Z + 14);

    /** Single horde gate at the far end of the bridge. */
    public static final BlockPos[] GATES = new BlockPos[]{
            new BlockPos(CENTER_X, DECK_Y + 1, NORTH_END - 3),
    };

    /** Extraction glyph, tucked inside the fort courtyard. */
    public static final BlockPos EXTRACTION = new BlockPos(CENTER_X, DECK_Y, ISLAND_CENTER_Z + 6);

    /**
     * The Heart: the thing you're actually defending. Sits on its dais in the
     * middle of the fort courtyard - the horde makes straight for it, and if it
     * falls, the run is over.
     */
    public static final BlockPos HEART = new BlockPos(CENTER_X, DECK_Y + 3, ISLAND_CENTER_Z + 4);

    /** Sentinel: if this is already the fort's beacon block, the map is built. */
    private static final BlockPos SENTINEL = new BlockPos(CENTER_X, DECK_Y + 1, ISLAND_CENTER_Z);

    private BridgeBuilder() {
    }

    public static void generateIfNeeded(ServerLevel level) {
        if (level.getBlockState(SENTINEL).is(Blocks.GILDED_BLACKSTONE)) {
            return;
        }
        Random rng = new Random(SEED);
        buildIsland(level, rng);
        buildBridge(level, rng);
        buildGate(level);
        buildFort(level, rng);
        buildArrivalFrame(level);
        placeChests(level, rng);
        buildEasterEgg(level);
        level.setBlock(SENTINEL, Blocks.GILDED_BLACKSTONE.defaultBlockState(), 3);
    }

    // ------------------------------------------------------------------
    // The island: circular, but eroded so it never reads as man-made.
    // ------------------------------------------------------------------

    private static void buildIsland(ServerLevel level, Random rng) {
        for (int dx = -ISLAND_RADIUS - 2; dx <= ISLAND_RADIUS + 2; dx++) {
            for (int dz = -ISLAND_RADIUS - 2; dz <= ISLAND_RADIUS + 2; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                // A wobbling edge so the rim isn't a perfect circle.
                double wobble = Math.sin(Math.atan2(dz, dx) * 3.0) * 1.6 + rng.nextDouble() * 1.2;
                double edge = ISLAND_RADIUS + wobble;
                if (dist > edge) {
                    continue;
                }
                int x = CENTER_X + dx;
                int z = ISLAND_CENTER_Z + dz;

                // Depth tapers toward the rim, giving the underside a rough cone.
                double t = 1.0 - (dist / edge);
                int depth = (int) Math.round(2 + t * 14 + rng.nextDouble() * 2);
                level.setBlock(new BlockPos(x, DECK_Y, z), surfaceBlock(rng), 2);
                for (int d = 1; d <= depth; d++) {
                    level.setBlock(new BlockPos(x, DECK_Y - d, z), underBlock(rng), 2);
                }
                // Occasional stalactite hanging off the underside.
                if (dist > edge * 0.5 && rng.nextInt(14) == 0) {
                    int hang = 2 + rng.nextInt(5);
                    for (int d = 1; d <= hang; d++) {
                        level.setBlock(new BlockPos(x, DECK_Y - depth - d, z), Blocks.BASALT.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static BlockState surfaceBlock(Random rng) {
        return switch (rng.nextInt(6)) {
            case 0 -> Blocks.COARSE_DIRT.defaultBlockState();
            case 1 -> Blocks.PODZOL.defaultBlockState();
            case 2 -> Blocks.ROOTED_DIRT.defaultBlockState();
            case 3 -> Blocks.ANDESITE.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    private static BlockState underBlock(Random rng) {
        return switch (rng.nextInt(5)) {
            case 0 -> Blocks.DEEPSLATE.defaultBlockState();
            case 1 -> Blocks.TUFF.defaultBlockState();
            case 2 -> Blocks.BASALT.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    // ------------------------------------------------------------------
    // The bridge: a detailed span with rails, ribs and lanterns.
    // ------------------------------------------------------------------

    private static void buildBridge(ServerLevel level, Random rng) {
        for (int z = NORTH_END; z <= SOUTH_END; z++) {
            for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH - 1; dx++) {
                int x = CENTER_X + dx;
                boolean edge = dx == -HALF_WIDTH || dx == HALF_WIDTH - 1;
                boolean inlay = dx == -1 || dx == 0;
                BlockState deck = edge ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                        : inlay ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                        : deckBlock(rng);
                level.setBlock(new BlockPos(x, DECK_Y, z), deck, 2);
                // Thin structural underside so it doesn't look like a floating sheet.
                level.setBlock(new BlockPos(x, DECK_Y - 1, z),
                        edge ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState(), 2);
            }

            // Parapet walls along both flanks.
            for (int side : new int[]{-HALF_WIDTH, HALF_WIDTH - 1}) {
                int x = CENTER_X + side;
                level.setBlock(new BlockPos(x, DECK_Y + 1, z), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), 2);
            }

            // Glowing rune inlay running the length of the centre line.
            if (Math.floorMod(z - NORTH_END, 5) == 0) {
                level.setBlock(new BlockPos(CENTER_X - 1, DECK_Y, z), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
                level.setBlock(new BlockPos(CENTER_X, DECK_Y, z), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
            }

            // Every 8 blocks: buttress ribs below and lantern posts above.
            if (Math.floorMod(z - NORTH_END, 8) == 0) {
                buildRib(level, z);
                for (int side : new int[]{-HALF_WIDTH, HALF_WIDTH - 1}) {
                    int x = CENTER_X + side;
                    level.setBlock(new BlockPos(x, DECK_Y + 2, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x, DECK_Y + 3, z), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
                }
            }

            // Every 25 blocks: colossal pylons straddling the span.
            if (Math.floorMod(z - NORTH_END, 25) == 12) {
                buildPylonPair(level, z);
            }
        }
    }

    /**
     * A pair of towering pylons flanking the bridge, linked overhead by a
     * lantern-hung crossbeam. These are the silhouette of the map - you see them
     * marching off into the dark long before you see what's coming down the span.
     */
    private static void buildPylonPair(ServerLevel level, int z) {
        int height = 16;
        for (int side : new int[]{-HALF_WIDTH, HALF_WIDTH - 1}) {
            int x = CENTER_X + side;
            for (int dy = 1; dy <= height; dy++) {
                BlockState body = (dy % 4 == 0)
                        ? Blocks.DEEPSLATE_BRICKS.defaultBlockState()
                        : Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                level.setBlock(new BlockPos(x, DECK_Y + dy, z), body, 2);
                // Widen the foot into a buttress.
                if (dy <= 3) {
                    int out = side < 0 ? -1 : 1;
                    level.setBlock(new BlockPos(x + out, DECK_Y + dy, z),
                            Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
                }
            }
            // Crown brazier.
            level.setBlock(new BlockPos(x, DECK_Y + height + 1, z), Blocks.SOUL_SOIL.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, DECK_Y + height + 2, z), Blocks.SOUL_FIRE.defaultBlockState(), 2);
            // Banner cloth hanging down the inner face.
            int inward = side < 0 ? 1 : -1;
            for (int dy = height - 10; dy <= height - 4; dy++) {
                level.setBlock(new BlockPos(x + inward, DECK_Y + dy, z),
                        Blocks.RED_WOOL.defaultBlockState(), 2);
            }
            level.setBlock(new BlockPos(x + inward, DECK_Y + height - 3, z),
                    Blocks.GILDED_BLACKSTONE.defaultBlockState(), 2);
        }

        // Crossbeam spanning the top, with chained lanterns dangling over the deck.
        for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH - 1; dx++) {
            level.setBlock(new BlockPos(CENTER_X + dx, DECK_Y + height, z),
                    Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
        }
        for (int dx : new int[]{-6, -2, 2, 5}) {
            for (int c = 1; c <= 3; c++) {
                level.setBlock(new BlockPos(CENTER_X + dx, DECK_Y + height - c, z),
                        Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y), 2);
            }
            level.setBlock(new BlockPos(CENTER_X + dx, DECK_Y + height - 4, z),
                    Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), 2);
        }
    }

    private static BlockState deckBlock(Random rng) {
        return switch (rng.nextInt(6)) {
            case 0 -> Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
            case 1 -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case 2 -> Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
            default -> Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        };
    }

    /** A hanging arch rib under the span - pure silhouette detail from below. */
    private static void buildRib(ServerLevel level, int z) {
        for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH - 1; dx++) {
            int x = CENTER_X + dx;
            // Deeper toward the middle, forming a shallow arch.
            double t = 1.0 - Math.abs(dx + 0.5) / (double) HALF_WIDTH;
            int depth = (int) Math.round(1 + t * 4);
            for (int d = 2; d <= depth + 1; d++) {
                level.setBlock(new BlockPos(x, DECK_Y - d, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
            }
        }
        // Chains dangling from the rib ends.
        for (int side : new int[]{-HALF_WIDTH, HALF_WIDTH - 1}) {
            int x = CENTER_X + side;
            for (int d = 2; d <= 5; d++) {
                level.setBlock(new BlockPos(x, DECK_Y - d, z),
                        Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y), 2);
            }
        }
    }

    // ------------------------------------------------------------------
    // The horde gate
    // ------------------------------------------------------------------

    private static void buildGate(ServerLevel level) {
        BlockPos gate = GATES[0];
        // A short landing so mobs have somewhere to step out onto.
        for (int dx = -4; dx <= 3; dx++) {
            for (int dz = -4; dz <= 2; dz++) {
                level.setBlock(new BlockPos(gate.getX() + dx, DECK_Y, gate.getZ() + dz),
                        Blocks.BLACKSTONE.defaultBlockState(), 2);
            }
        }
        // Crying-obsidian arch, matching the temple gates.
        for (int dx = -3; dx <= 3; dx++) {
            boolean pillar = dx == -3 || dx == 3;
            for (int dy = 1; dy <= 6; dy++) {
                BlockPos p = new BlockPos(gate.getX() + dx, DECK_Y + dy, gate.getZ());
                if (pillar || dy == 6) {
                    level.setBlock(p, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
                } else {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            if (pillar) {
                level.setBlock(new BlockPos(gate.getX() + dx, DECK_Y + 7, gate.getZ()),
                        Blocks.SOUL_LANTERN.defaultBlockState(), 3);
            }
        }
    }

    // ------------------------------------------------------------------
    // The fort: a battlemented keep facing down the bridge.
    // ------------------------------------------------------------------

    private static void buildFort(ServerLevel level, Random rng) {
        int halfX = 12;   // 25 wide
        int halfZ = 8;    // 17 deep
        int cz = ISLAND_CENTER_Z;
        int wallTop = DECK_Y + 5;

        for (int dx = -halfX; dx <= halfX; dx++) {
            for (int dz = -halfZ; dz <= halfZ; dz++) {
                int x = CENTER_X + dx;
                int z = cz + dz;
                boolean shell = dx == -halfX || dx == halfX || dz == -halfZ || dz == halfZ;

                // Flagstone courtyard inside.
                level.setBlock(new BlockPos(x, DECK_Y, z),
                        shell ? Blocks.POLISHED_ANDESITE.defaultBlockState() : courtyardBlock(rng), 2);
                if (!shell) {
                    continue;
                }
                // The north face has a gateway so defenders can sally onto the bridge.
                boolean gateway = dz == -halfZ && Math.abs(dx) <= 2;
                if (gateway) {
                    for (int y = DECK_Y + 1; y <= DECK_Y + 3; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                    level.setBlock(new BlockPos(x, DECK_Y + 4, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
                    continue;
                }
                for (int y = DECK_Y + 1; y <= wallTop; y++) {
                    level.setBlock(new BlockPos(x, y, z), fortBlock(rng), 2);
                }
                // Battlements: alternating merlons along the top.
                boolean merlon = Math.floorMod(dx + dz, 2) == 0;
                if (merlon) {
                    level.setBlock(new BlockPos(x, wallTop + 1, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
                }
                // Arrow slits at eye level and a lantern every few blocks.
                if (!merlon && Math.floorMod(dx + dz, 6) == 0) {
                    level.setBlock(new BlockPos(x, DECK_Y + 3, z), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), 2);
                }
                if (Math.floorMod(dx + dz, 9) == 0) {
                    level.setBlock(new BlockPos(x, wallTop + 1, z), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
                }
            }
        }

        // Corner towers, a little taller than the curtain wall.
        for (int[] c : new int[][]{{-halfX, -halfZ}, {-halfX, halfZ}, {halfX, -halfZ}, {halfX, halfZ}}) {
            int x = CENTER_X + c[0];
            int z = cz + c[1];
            for (int y = DECK_Y + 1; y <= wallTop + 3; y++) {
                level.setBlock(new BlockPos(x, y, z), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2);
            }
            level.setBlock(new BlockPos(x, wallTop + 4, z), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
        }

        // Courtyard centrepiece: a raised fire bowl on a gilded dais, with
        // banner-draped standards at its corners.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(new BlockPos(CENTER_X + dx, DECK_Y + 1, cz + dz + 4),
                        Blocks.GILDED_BLACKSTONE.defaultBlockState(), 2);
            }
        }
        // The Heart itself, raised on the dais: a glowing crystal the horde wants.
        level.setBlock(new BlockPos(CENTER_X, DECK_Y + 2, cz + 4), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
        level.setBlock(HEART, Blocks.SEA_LANTERN.defaultBlockState(), 2);
        level.setBlock(new BlockPos(CENTER_X, DECK_Y + 4, cz + 4), Blocks.SOUL_FIRE.defaultBlockState(), 2);
        for (int[] c : new int[][]{{-2, 2}, {2, 2}, {-2, -2}, {2, -2}}) {
            int sx = CENTER_X + c[0];
            int sz = cz + 4 + c[1];
            for (int dy = 2; dy <= 5; dy++) {
                level.setBlock(new BlockPos(sx, DECK_Y + dy, sz), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
            }
            level.setBlock(new BlockPos(sx, DECK_Y + 6, sz), Blocks.LANTERN.defaultBlockState(), 2);
        }

        // Banners hung down the inner face of the north wall, facing the bridge.
        for (int dx = -halfX + 3; dx <= halfX - 3; dx += 5) {
            for (int dy = 2; dy <= 4; dy++) {
                level.setBlock(new BlockPos(CENTER_X + dx, DECK_Y + dy, cz - halfZ + 1),
                        Blocks.RED_WOOL.defaultBlockState(), 2);
            }
            level.setBlock(new BlockPos(CENTER_X + dx, DECK_Y + 5, cz - halfZ + 1),
                    Blocks.GILDED_BLACKSTONE.defaultBlockState(), 2);
        }

        // Stairs up onto the north rampart so players can fight from the wall.
        BlockState step = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);
        for (int i = 0; i < 5; i++) {
            for (int dx = 5; dx <= 7; dx++) {
                level.setBlock(new BlockPos(CENTER_X + dx, DECK_Y + 1 + i, cz - halfZ + 2 + i), step, 2);
            }
        }
    }

    private static BlockState courtyardBlock(Random rng) {
        return switch (rng.nextInt(4)) {
            case 0 -> Blocks.COBBLESTONE.defaultBlockState();
            case 1 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case 2 -> Blocks.STONE_BRICKS.defaultBlockState();
            default -> Blocks.POLISHED_ANDESITE.defaultBlockState();
        };
    }

    private static BlockState fortBlock(Random rng) {
        return switch (rng.nextInt(5)) {
            case 0 -> Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
            case 1 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            case 2 -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            default -> Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        };
    }

    /**
     * The diamond frame players materialise in front of. The portal surface
     * itself is placed and pulled by the round manager as runs start and end.
     */
    private static void buildArrivalFrame(ServerLevel level) {
        int cx = ARRIVAL.getX();
        int cz = ARRIVAL.getZ();
        int floorY = ARRIVAL.getY() - 1;
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                boolean frameEdge = dx == -1 || dx == 2 || dy == 0 || dy == 4;
                if (frameEdge) {
                    level.setBlock(new BlockPos(cx + dx, floorY + dy, cz),
                            Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Loot + the bridge's own hidden ritual
    // ------------------------------------------------------------------

    private static void placeChests(ServerLevel level, Random rng) {
        BlockPos[] spots = {
                new BlockPos(CENTER_X - 9, DECK_Y + 1, ISLAND_CENTER_Z + 4),
                new BlockPos(CENTER_X + 9, DECK_Y + 1, ISLAND_CENTER_Z + 4),
                new BlockPos(CENTER_X - 6, DECK_Y + 1, ISLAND_CENTER_Z - 5),
                new BlockPos(CENTER_X + 6, DECK_Y + 1, ISLAND_CENTER_Z - 5),
                new BlockPos(CENTER_X, DECK_Y + 1, ISLAND_CENTER_Z + 12),
        };
        for (BlockPos p : spots) {
            level.setBlock(p, Blocks.CHEST.defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof ChestBlockEntity chest) {
                net.minecraft.world.item.Item[] pool = {
                        net.minecraft.world.item.Items.IRON_INGOT,
                        net.minecraft.world.item.Items.GOLD_INGOT,
                        net.minecraft.world.item.Items.ARROW,
                        net.minecraft.world.item.Items.COOKED_BEEF,
                        net.minecraft.world.item.Items.TORCH,
                        net.minecraft.world.item.Items.GOLDEN_APPLE,
                        net.minecraft.world.item.Items.DIAMOND,
                };
                int rolls = 2 + rng.nextInt(3);
                for (int i = 0; i < rolls; i++) {
                    net.minecraft.world.item.Item item = pool[rng.nextInt(pool.length)];
                    int count = (item == net.minecraft.world.item.Items.DIAMOND
                            || item == net.minecraft.world.item.Items.GOLDEN_APPLE)
                            ? 1 + rng.nextInt(2) : 2 + rng.nextInt(6);
                    chest.setItem(rng.nextInt(chest.getContainerSize()),
                            new net.minecraft.world.item.ItemStack(item, count));
                }
            }
        }
    }

    /** Positions of the four lanterns that make up the bridge's hidden ritual. */
    public static final BlockPos[] BEACONS = new BlockPos[]{
            new BlockPos(CENTER_X - HALF_WIDTH, DECK_Y + 3, NORTH_END + 8),
            new BlockPos(CENTER_X + HALF_WIDTH - 1, DECK_Y + 3, NORTH_END + 24),
            new BlockPos(CENTER_X - HALF_WIDTH, DECK_Y + 3, NORTH_END + 40),
            new BlockPos(CENTER_X + HALF_WIDTH - 1, DECK_Y + 3, NORTH_END + 56),
    };

    /** The sealed cache the bridge ritual opens, under the fort courtyard. */
    public static final BlockPos VAULT_CHEST = new BlockPos(CENTER_X, DECK_Y - 4, ISLAND_CENTER_Z + 2);
    public static final BlockPos VAULT_SEAL = new BlockPos(CENTER_X, DECK_Y, ISLAND_CENTER_Z + 2);

    /**
     * The bridge's Easter egg: four lantern posts along the span burn a
     * different colour. Douse them from far to near (the order they were lit)
     * and the sealed cache under the courtyard opens. Findable if you're paying
     * attention, not obvious if you're not.
     */
    private static void buildEasterEgg(ServerLevel level) {
        for (BlockPos b : BEACONS) {
            level.setBlock(b, Blocks.LANTERN.defaultBlockState(), 3);
            level.setBlock(b.below(), Blocks.GILDED_BLACKSTONE.defaultBlockState(), 3);
        }
        // Hollow the cache and seal its lid flush with the courtyard floor.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos p = VAULT_CHEST.offset(dx, dy, dz);
                    boolean shell = dx == -2 || dx == 2 || dz == -2 || dz == 2 || dy == 0 || dy == 3;
                    level.setBlock(p, shell ? Blocks.DEEPSLATE_BRICKS.defaultBlockState()
                            : Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(VAULT_CHEST, Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(VAULT_SEAL, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 3);
    }
}
