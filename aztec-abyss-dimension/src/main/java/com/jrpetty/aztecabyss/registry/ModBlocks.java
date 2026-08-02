package com.jrpetty.aztecabyss.registry;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.block.AbyssPortalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(AztecAbyssConstants.MOD_ID);

    // The portal frame is built from vanilla diamond/iron blocks, so no custom
    // frame block is registered. Only the lit portal surface below is custom.

    /** The lit portal block itself - burns with a black flame rather than nether-purple. */
    public static final DeferredBlock<AbyssPortalBlock> ABYSS_PORTAL = BLOCKS.register(
            "abyss_portal",
            () -> new AbyssPortalBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .noCollission()
                            .randomTicks()
                            .lightLevel(state -> 8)
                            .strength(-1.0f)
                            .sound(SoundType.GLASS)
                            .noLootTable()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
            )
    );

    /**
     * The Marker Block - what an author places instead of a sign.
     *
     * <p>Unbreakable strength would be wrong: an author has to be able to take one
     * back off a wall. Instant-break instead, and no loot table only because the
     * item is handed out by command rather than mined.
     */
    public static final DeferredBlock<com.jrpetty.aztecabyss.block.MarkerBlock> MARKER =
            BLOCKS.register("marker",
                    () -> new com.jrpetty.aztecabyss.block.MarkerBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.NONE)
                                    .noCollission()
                                    .noOcclusion()
                                    .instabreak()
                                    .sound(SoundType.WOOD)
                                    .noLootTable()));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
