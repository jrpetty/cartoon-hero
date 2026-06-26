package dev.structint.world;

import dev.structint.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides what each block <em>is</em> to the structural system: whether it can bear load at
 * all, whether it is a foundation anchor, and — if it is a structural block — what its span is.
 *
 * <p>All of this is pure state inspection (tags + collision shape); it holds no world data and
 * is safe to call from the grid adapter on the server thread.
 */
public final class BlockClassifier {

    /**
     * Whether a block bears structural load and so participates in the system. This is the gate
     * for being managed, being an anchor, and being a structural cell.
     *
     * <p>Load-bearing shapes are: full cubes (stone, planks, concrete, double slabs, …) <b>and</b>
     * slabs and stairs — half-blocks still hold a build together, so a slab bridge or a stair
     * staircase obeys the same span rules. Genuine decorations (torches, fences, panes, walls,
     * carpets) are neither full cubes nor slabs/stairs, so they stay invisible to the rules.
     */
    public static boolean isLoadBearing(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) {
            return false;
        }
        if (state.is(StructuralTags.EXEMPT)) {
            return false;
        }
        if (isSlabOrStairs(state)) {
            return true; // slabs/stairs bear load (even when waterlogged)
        }
        if (!state.getFluidState().isEmpty()) {
            return false; // exclude fluid source blocks
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }

    public static boolean isFoundation(BlockState state) {
        return state.is(StructuralTags.FOUNDATIONS);
    }

    private static boolean isSlabOrStairs(BlockState state) {
        return state.getBlock() instanceof SlabBlock || state.getBlock() instanceof StairBlock;
    }

    private static boolean isWoodenShape(BlockState state) {
        return state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.WOODEN_STAIRS);
    }

    /**
     * Resolves the material span for a block. Explicit structural tags win (so a pack can place a
     * block in any tier); otherwise slabs/stairs are classified by material (wooden → wood,
     * anything else → stone); everything else load-bearing is generic.
     */
    public static int spanOf(BlockState state) {
        Integer tagged = taggedSpan(state);
        return tagged != null ? tagged : Config.SPAN_GENERIC.get();
    }

    /**
     * The span of a block <em>if</em> it is a recognized structural material (by tag, or as a
     * slab/stair). Returns {@code null} for unclassified blocks, so tooltips appear for real
     * building materials rather than spamming every full block in the game.
     */
    public static Integer taggedSpan(BlockState state) {
        if (state.is(StructuralTags.STRUCTURAL_METAL)) {
            return Config.SPAN_METAL.get();
        }
        if (state.is(StructuralTags.STRUCTURAL_REINFORCED)) {
            return Config.SPAN_REINFORCED.get();
        }
        if (state.is(StructuralTags.STRUCTURAL_STONE)) {
            return Config.SPAN_STONE.get();
        }
        if (state.is(StructuralTags.STRUCTURAL_WOOD)) {
            return Config.SPAN_WOOD.get();
        }
        if (state.is(StructuralTags.STRUCTURAL_DIRT)) {
            return Config.SPAN_DIRT.get();
        }
        if (isSlabOrStairs(state)) {
            return isWoodenShape(state) ? Config.SPAN_WOOD.get() : Config.SPAN_STONE.get();
        }
        return null;
    }

    private BlockClassifier() {
    }
}
