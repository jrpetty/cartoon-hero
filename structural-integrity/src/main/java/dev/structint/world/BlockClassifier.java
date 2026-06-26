package dev.structint.world;

import dev.structint.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
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
     * A "full solid block" is our gate for participating in the system at all. Decorations
     * (torches, fences, panes, slabs, …) are not full cubes, so they are invisible to the
     * rules — exactly the non-structural behaviour the design asks for.
     */
    public static boolean isFullSolid(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.is(StructuralTags.EXEMPT)) {
            return false;
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }

    public static boolean isFoundation(BlockState state) {
        return state.is(StructuralTags.FOUNDATIONS);
    }

    /**
     * The span of a block, used both to gate "is this even a structural block we should manage
     * when placed" and to feed the solver. Returns -1 for blocks that should not be managed.
     */
    public static int spanOf(BlockState state) {
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
        // Any other full solid block a player places is generic: weak but still load-bearing.
        return Config.SPAN_GENERIC.get();
    }

    private BlockClassifier() {
    }
}
