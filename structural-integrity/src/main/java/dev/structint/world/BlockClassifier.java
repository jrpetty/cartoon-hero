package dev.structint.world;

import dev.structint.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
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
     * Whether a block participates in the system. This is the gate for being managed, being an
     * anchor, and being a structural cell.
     *
     * <p><b>Every</b> block participates — full cubes, slabs, stairs, walls, fences, panes, bars,
     * chains, trapdoors, torches, carpets, plants, redstone, signs, … — so any player-placed block
     * must trace a valid load path or it falls. The only things excluded are air, fluids (water
     * and lava), and an explicit {@code structint:exempt} tag (the small escape hatch for special
     * blocks like scaffolding). The {@code level}/{@code pos} parameters are retained for API
     * stability and possible future shape-aware rules.
     */
    @SuppressWarnings("unused")
    public static boolean isLoadBearing(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            return false; // water/lava are not structural
        }
        return !state.is(StructuralTags.EXEMPT);
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
