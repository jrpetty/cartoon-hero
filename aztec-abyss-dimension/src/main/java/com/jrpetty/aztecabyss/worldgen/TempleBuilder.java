package com.jrpetty.aztecabyss.worldgen;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

import java.util.Random;

/**
 * Builds the stepped Aztec temple and its interior: nine terraced tiers, a
 * grand south staircase, corner braziers, a hollow altar chamber lit by a lava
 * basin, four ritual braziers, a trapped approach corridor, and a sealed vault
 * beneath the altar holding the grand prize (opened only by the Easter-egg
 * ritual).
 *
 * Everything is deterministic (fixed seed / fixed layout) so the temple is
 * identical on every server and the ritual landmarks always line up with
 * {@link AztecAbyssConstants}.
 */
final class TempleBuilder {

    private static final BlockState CORE = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL_ALT = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState WEATHERED = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState BAND = Blocks.RED_TERRACOTTA.defaultBlockState();
    private static final BlockState BAND_ALT = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
    private static final BlockState GOLD_ACCENT = Blocks.GILDED_BLACKSTONE.defaultBlockState();
    private static final BlockState GOLD_BLOCK = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final BlockState STAIR_TREAD = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
    private static final BlockState SLAB_EDGE = Blocks.STONE_BRICK_SLAB.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private TempleBuilder() {
    }

    static void build(ServerLevel level, BlockPos center) {
        Random rng = new Random(20260718L);

        int floorY = center.getY();
        int halfWidth = AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH;

        for (int tier = 0; tier < AztecAbyssConstants.TEMPLE_TIERS; tier++) {
            int hw = halfWidth - tier * AztecAbyssConstants.TEMPLE_STEP_IN;
            int yStart = floorY + tier * AztecAbyssConstants.TEMPLE_TIER_HEIGHT;
            int yEnd = yStart + AztecAbyssConstants.TEMPLE_TIER_HEIGHT - 1;
            fillTier(level, center, hw, yStart, yEnd, tier, rng);
            if (tier % 2 == 0) {
                placeCornerBraziers(level, center, hw, yEnd + 1, tier);
            }
        }

        int topY = floorY + AztecAbyssConstants.TEMPLE_TIERS * AztecAbyssConstants.TEMPLE_TIER_HEIGHT;
        buildStaircase(level, center, floorY, topY, halfWidth);
        buildSummitAltar(level, center, topY);

        carveAltarChamber(level, center);
        buildAltar(level);
        placeRitualBraziers(level);
        carveEntranceCorridor(level, center);
        buildVault(level);

        // Sentinel marker at the core - generateIfNeeded() checks this to know it's built.
        level.setBlock(center, GOLD_ACCENT, 3);
    }

    // ------------------------------------------------------------------
    // Exterior pyramid
    // ------------------------------------------------------------------

    private static void fillTier(ServerLevel level, BlockPos center, int hw, int yStart, int yEnd, int tier, Random rng) {
        int cx = center.getX();
        int cz = center.getZ();
        for (int x = -hw; x <= hw; x++) {
            for (int z = -hw; z <= hw; z++) {
                boolean shell = x == -hw || x == hw || z == -hw || z == hw;
                for (int y = yStart; y <= yEnd; y++) {
                    level.setBlock(new BlockPos(cx + x, y, cz + z), material(shell, tier, x, y, z, rng), 2);
                }
            }
        }
        for (int x = -hw; x <= hw; x++) {
            for (int z = -hw; z <= hw; z++) {
                if (x == -hw || x == hw || z == -hw || z == hw) {
                    level.setBlock(new BlockPos(cx + x, yEnd + 1, cz + z), SLAB_EDGE, 2);
                }
            }
        }
    }

    private static BlockState material(boolean shell, int tier, int x, int y, int z, Random rng) {
        if (!shell) {
            return tier % 2 == 0 ? CORE : WALL;
        }
        if (tier % 3 == 1 && Math.floorMod(x + z, 5) == 0) {
            return (x + y) % 2 == 0 ? BAND : BAND_ALT;
        }
        double roll = rng.nextDouble();
        if (roll < 0.06) {
            return CRACKED;
        }
        if (roll < 0.16) {
            return WEATHERED;
        }
        if (roll < 0.30) {
            return WALL_ALT;
        }
        return WALL;
    }

    private static void placeCornerBraziers(ServerLevel level, BlockPos center, int hw, int y, int tier) {
        int cx = center.getX();
        int cz = center.getZ();
        int[][] corners = {{-hw + 1, -hw + 1}, {-hw + 1, hw - 1}, {hw - 1, -hw + 1}, {hw - 1, hw - 1}};
        boolean soulFire = tier % 4 == 0;
        for (int[] corner : corners) {
            BlockPos base = new BlockPos(cx + corner[0], y, cz + corner[1]);
            level.setBlock(base, soulFire ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState(), 3);
            level.setBlock(base.above(), soulFire ? Blocks.SOUL_FIRE.defaultBlockState() : Blocks.FIRE.defaultBlockState(), 3);
        }
    }

    private static void buildStaircase(ServerLevel level, BlockPos center, int floorY, int topY, int baseHalfWidth) {
        int cx = center.getX();
        int cz = center.getZ();
        int totalRise = topY - floorY;
        BlockState tread = STAIR_TREAD.setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);

        for (int i = 0; i < totalRise; i++) {
            int y = floorY + 1 + i;
            int z = baseHalfWidth - i;
            for (int x = -2; x <= 2; x++) {
                level.setBlock(new BlockPos(cx + x, y, cz + z), tread, 3);
                for (int under = floorY; under < y; under++) {
                    level.setBlock(new BlockPos(cx + x, under, cz + z), CORE, 2);
                }
                level.setBlock(new BlockPos(cx + x, y + 1, cz + z), AIR, 2);
                level.setBlock(new BlockPos(cx + x, y + 2, cz + z), AIR, 2);
            }
        }
    }

    private static void buildSummitAltar(ServerLevel level, BlockPos center, int topY) {
        int cx = center.getX();
        int cz = center.getZ();
        int r = 6;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                level.setBlock(new BlockPos(cx + x, topY, cz + z), WALL_ALT, 2);
            }
        }
        int[][] corners = {{-r + 1, -r + 1}, {-r + 1, r - 1}, {r - 1, -r + 1}, {r - 1, r - 1}};
        for (int[] corner : corners) {
            for (int h = 1; h <= 5; h++) {
                level.setBlock(new BlockPos(cx + corner[0], topY + h, cz + corner[1]), CORE, 2);
            }
            level.setBlock(new BlockPos(cx + corner[0], topY + 6, cz + corner[1]), GOLD_ACCENT, 2);
        }
        level.setBlock(new BlockPos(cx, topY + 1, cz), Blocks.SOUL_SOIL.defaultBlockState(), 3);
        level.setBlock(new BlockPos(cx, topY + 2, cz), Blocks.SOUL_FIRE.defaultBlockState(), 3);
    }

    // ------------------------------------------------------------------
    // Interior: altar chamber, altar, ritual braziers
    // ------------------------------------------------------------------

    private static void carveAltarChamber(ServerLevel level, BlockPos center) {
        int cx = center.getX();
        int cz = center.getZ();
        int floorY = center.getY();
        int half = 9;
        int height = 7;

        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                for (int y = 1; y <= height; y++) {
                    level.setBlock(new BlockPos(cx + x, floorY + y, cz + z), AIR, 2);
                }
                // A polished floor with a gold cross inlay.
                boolean inlay = x == 0 || z == 0;
                level.setBlock(new BlockPos(cx + x, floorY, cz + z), inlay ? GOLD_ACCENT : WALL_ALT, 2);
            }
        }
        // Ceiling of the chamber.
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                level.setBlock(new BlockPos(cx + x, floorY + height + 1, cz + z), CORE, 2);
            }
        }
        // Tor-lit pillars at the chamber's inner corners for atmosphere + light.
        int p = half - 2;
        for (int[] c : new int[][]{{-p, -p}, {-p, p}, {p, -p}, {p, p}}) {
            for (int y = 1; y <= height; y++) {
                level.setBlock(new BlockPos(cx + c[0], floorY + y, cz + c[1]), CORE, 2);
            }
        }
    }

    private static void buildAltar(ServerLevel level) {
        BlockPos altar = AztecAbyssConstants.ALTAR_POS;
        // A raised gold dais with a lava heart and an offering block on top.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(altar.offset(x, -1, z), GOLD_BLOCK, 3);
            }
        }
        level.setBlock(altar, Blocks.LAVA.defaultBlockState(), 3);
        level.setBlock(altar.offset(1, 0, 0), GOLD_ACCENT, 3);
        level.setBlock(altar.offset(-1, 0, 0), GOLD_ACCENT, 3);
        level.setBlock(altar.offset(0, 0, 1), GOLD_ACCENT, 3);
        level.setBlock(altar.offset(0, 0, -1), GOLD_ACCENT, 3);
        // The offering pedestal - players right-click here with the offering item.
        level.setBlock(AztecAbyssConstants.ALTAR_OFFERING_POS, Blocks.LODESTONE.defaultBlockState(), 3);
    }

    private static void placeRitualBraziers(ServerLevel level) {
        for (BlockPos b : AztecAbyssConstants.BRAZIER_POSITIONS) {
            // A gilded plinth with an (initially unlit) netherrack bowl.
            level.setBlock(b.below(), GOLD_ACCENT, 3);
            level.setBlock(b, Blocks.NETHERRACK.defaultBlockState(), 3);
            // No fire on top yet - lighting them in the right order is the ritual.
            level.setBlock(b.above(), AIR, 3);
        }
    }

    // ------------------------------------------------------------------
    // Trapped entrance corridor (north face, at floor level)
    // ------------------------------------------------------------------

    private static void carveEntranceCorridor(ServerLevel level, BlockPos center) {
        int cx = center.getX();
        int floorY = center.getY();
        int chamberEdge = 9;
        int baseEdge = AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH;

        // Tunnel runs north (-Z) from the chamber wall out through the pyramid face.
        for (int z = -chamberEdge; z >= -baseEdge - 1; z--) {
            for (int x = -1; x <= 1; x++) {
                for (int y = 1; y <= 3; y++) {
                    level.setBlock(new BlockPos(cx + x, floorY + y, z), AIR, 2);
                }
                level.setBlock(new BlockPos(cx + x, floorY, z), WALL_ALT, 2);
            }
        }

        // Trap tiles: alternating magma-block sections you must hop across, over a
        // shallow lava channel - crossing carelessly hurts.
        for (int z = -chamberEdge - 2; z >= -baseEdge + 2; z -= 3) {
            level.setBlock(new BlockPos(cx, floorY, z), Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
            level.setBlock(new BlockPos(cx - 1, floorY - 1, z), Blocks.LAVA.defaultBlockState(), 3);
            level.setBlock(new BlockPos(cx + 1, floorY - 1, z), Blocks.LAVA.defaultBlockState(), 3);
            level.setBlock(new BlockPos(cx - 1, floorY, z), AIR, 3);
            level.setBlock(new BlockPos(cx + 1, floorY, z), AIR, 3);
        }
    }

    // ------------------------------------------------------------------
    // Sealed vault beneath the altar (opened by the ritual)
    // ------------------------------------------------------------------

    private static void buildVault(ServerLevel level) {
        BlockPos chest = AztecAbyssConstants.VAULT_CHEST_POS;
        int vy = chest.getY();
        int cx = chest.getX();
        int cz = chest.getZ();

        // Hollow the vault room.
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = 0; y <= 4; y++) {
                    BlockPos p = new BlockPos(cx + x, vy + y, cz + z);
                    boolean shell = x == -3 || x == 3 || z == -3 || z == 3 || y == 0 || y == 4;
                    level.setBlock(p, shell ? GOLD_ACCENT : AIR, 3);
                }
            }
        }
        // Grand-prize chest (filled by RitualHandler on completion).
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(chest.below(), GOLD_BLOCK, 3);

        // A short stair from the altar chamber floor down to the vault, but the
        // final doorway is walled off until the ritual dissolves it.
        BlockPos door = AztecAbyssConstants.VAULT_DOOR_POS;
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                level.setBlock(door.offset(x, y, 0), Blocks.SEA_LANTERN.defaultBlockState(), 3);
            }
        }
        // Carve the connecting shaft (behind the door) so once it's open you can descend.
        for (int z = AztecAbyssConstants.VAULT_DOOR_POS.getZ(); z >= cz + 3; z--) {
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 2; y++) {
                    // leave the door plane itself (handled above); open the rest
                    if (z != AztecAbyssConstants.VAULT_DOOR_POS.getZ()) {
                        level.setBlock(new BlockPos(cx + x, vy + y, z), AIR, 3);
                    }
                }
            }
        }
    }
}
