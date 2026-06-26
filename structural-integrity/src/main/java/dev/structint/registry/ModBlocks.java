package dev.structint.registry;

import dev.structint.StructuralIntegrityMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The three blocks the mod adds. They exist to give players explicit structural tools beyond
 * vanilla materials:
 *
 * <ul>
 *   <li>{@link #FOUNDATION_ANCHOR} — a designated anchor. Even when player-placed it counts as
 *       always-stable ground, so you can root a structure anywhere (e.g. start a bridge tower
 *       on the riverbed).</li>
 *   <li>{@link #REINFORCED_BEAM} — reinforced span (default 12).</li>
 *   <li>{@link #HEAVY_GIRDER} — heavy metal span (default 20), for the longest bridges.</li>
 * </ul>
 *
 * <p>Materials/spans are not hard-coded onto the blocks; they are derived from data tags
 * (see {@code resources/data/structint/tags/block}) so server packs can reclassify freely.
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(StructuralIntegrityMod.MODID);

    public static final DeferredBlock<Block> FOUNDATION_ANCHOR = BLOCKS.registerSimpleBlock(
            "foundation_anchor",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(4.0F, 1200.0F)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> REINFORCED_BEAM = BLOCKS.registerSimpleBlock(
            "reinforced_beam",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 9.0F)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> HEAVY_GIRDER = BLOCKS.registerSimpleBlock(
            "heavy_girder",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops());

    private ModBlocks() {
    }
}
