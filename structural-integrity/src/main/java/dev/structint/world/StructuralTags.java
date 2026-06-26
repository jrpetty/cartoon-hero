package dev.structint.world;

import dev.structint.StructuralIntegrityMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tags that drive classification. Populating these via data packs (rather than hard-coding
 * block lists) is what makes the material assignment fully data-driven and server-overridable.
 * Default contents ship in {@code resources/data/structint/tags/block}.
 */
public final class StructuralTags {

    public static final TagKey<Block> FOUNDATIONS = tag("foundations");
    public static final TagKey<Block> STRUCTURAL_DIRT = tag("structural_dirt");
    public static final TagKey<Block> STRUCTURAL_WOOD = tag("structural_wood");
    public static final TagKey<Block> STRUCTURAL_STONE = tag("structural_stone");
    public static final TagKey<Block> STRUCTURAL_REINFORCED = tag("structural_reinforced");
    public static final TagKey<Block> STRUCTURAL_METAL = tag("structural_metal");

    /** Blocks explicitly excluded from the system even if they are full solid blocks. */
    public static final TagKey<Block> EXEMPT = tag("exempt");

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(StructuralIntegrityMod.MODID, path));
    }

    private StructuralTags() {
    }
}
