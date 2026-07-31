package com.jrpetty.aztecabyss.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The texturing toolkit the maps share.
 *
 * <p>This exists because of one specific mistake that was making every build in
 * the mod look like noise: material variation was being drawn from a shared
 * {@code RandomSource}, one roll per block. That gives salt-and-pepper static -
 * every block independently different from its neighbours - which is the single
 * most reliable way to make masonry read as television snow rather than stone.
 *
 * <p>Real stonework varies in <b>patches</b>. A damp corner is mossy for a metre
 * and a half; a blast crack runs across several courses. So variation here is
 * driven by position instead of by a die: a hash sampled on a coarse grid, so
 * neighbouring blocks usually agree and the seams between patches land in
 * irregular places. The same coordinates always give the same block, which also
 * means a map looks identical on every server and a rebuild never reshuffles it.
 */
public final class Deco {

    private Deco() {
    }

    // ------------------------------------------------------------------
    // Position hashing
    // ------------------------------------------------------------------

    /** A well-mixed hash of a coordinate triple. Deterministic across servers. */
    public static int hash(int x, int y, int z, int salt) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + salt * 1013904223;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    /** Hash as a fraction in [0,1). */
    public static float hashUnit(int x, int y, int z, int salt) {
        return (hash(x, y, z, salt) >>> 8) / (float) (1 << 24);
    }

    /**
     * Clustered noise in [0,1).
     *
     * <p>Samples the hash on a grid {@code scale} blocks across so that a whole
     * patch shares a value, then blends in a weaker per-block term to rough up
     * the patch edges. The result is blotchy rather than static - which is what
     * weathering actually looks like.
     */
    public static float patch(int x, int y, int z, int scale, int salt) {
        int cx = Math.floorDiv(x, scale);
        int cy = Math.floorDiv(y, Math.max(1, scale / 2));
        int cz = Math.floorDiv(z, scale);
        float coarse = hashUnit(cx, cy, cz, salt);
        float fine = hashUnit(x, y, z, salt + 77);
        return coarse * 0.78f + fine * 0.22f;
    }

    /**
     * Picks from a weighted palette using clustered noise.
     *
     * <p>Weights are cumulative fractions of 1. The first entry whose running
     * total exceeds the noise value wins.
     */
    public static BlockState pick(int x, int y, int z, int scale, int salt,
                                  BlockState[] palette, float[] weights) {
        float n = patch(x, y, z, scale, salt);
        float running = 0f;
        for (int i = 0; i < palette.length && i < weights.length; i++) {
            running += weights[i];
            if (n < running) {
                return palette[i];
            }
        }
        return palette[palette.length - 1];
    }

    /** True for roughly {@code chance} of positions, clustered rather than scattered. */
    public static boolean chance(int x, int y, int z, int salt, float chanceValue) {
        return hashUnit(x, y, z, salt) < chanceValue;
    }

    // ------------------------------------------------------------------
    // Placement helpers
    // ------------------------------------------------------------------

    /** Places only into air, so detailing can never eat structure. */
    public static void soft(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state, 2);
        }
    }

    /** Places only over a solid floor and into air - for clutter that must sit down. */
    public static void onFloor(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()
                && !level.getBlockState(pos.below()).isAir()) {
            level.setBlock(pos, state, 2);
        }
    }

    public static BlockState stairs(net.minecraft.world.level.block.Block block, Direction facing) {
        return block.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    /** An upside-down stair - the workhorse of cornices and under-eave trim. */
    public static BlockState stairsTop(net.minecraft.world.level.block.Block block, Direction facing) {
        return block.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.HALF, Half.TOP);
    }

    public static BlockState slab(net.minecraft.world.level.block.Block block, boolean top) {
        return block.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    public static BlockState wallTorch(Direction facing) {
        return Blocks.WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    public static BlockState soulWallTorch(Direction facing) {
        return Blocks.SOUL_WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    public static BlockState hangingLantern(boolean soul) {
        return (soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true);
    }

    public static BlockState chain(Direction.Axis axis) {
        return Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
    }

    public static BlockState log(net.minecraft.world.level.block.Block block, Direction.Axis axis) {
        return block.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
    }

    /**
     * Cobwebs in the corner where two surfaces meet, only where there is room.
     *
     * <p>Webs read as neglect far better than scattered clutter does, but only if
     * they are in corners - a web floating in the middle of a room looks like a
     * mistake, because that is not where spiders build.
     */
    public static void web(ServerLevel level, BlockPos pos, int salt, float chanceValue) {
        if (chance(pos.getX(), pos.getY(), pos.getZ(), salt, chanceValue)) {
            soft(level, pos, Blocks.COBWEB.defaultBlockState());
        }
    }
}
