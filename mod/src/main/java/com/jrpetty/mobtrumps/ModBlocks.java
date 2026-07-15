package com.jrpetty.mobtrumps;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks: the dueling table (starts a duel in-world) and the card display frame. */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MobTrumps.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MobTrumps.MODID);

    public static final DeferredBlock<DuelingTableBlock> DUELING_TABLE =
            BLOCKS.register("dueling_table", () -> new DuelingTableBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).noOcclusion()));

    public static final DeferredBlock<CardDisplayBlock> CARD_DISPLAY =
            BLOCKS.register("card_display", () -> new CardDisplayBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F)
                            .instabreak().noOcclusion()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CardDisplayBlockEntity>>
            CARD_DISPLAY_BE = BLOCK_ENTITIES.register("card_display",
            () -> BlockEntityType.Builder.of(CardDisplayBlockEntity::new, CARD_DISPLAY.get()).build(null));

    public static final DeferredItem<BlockItem> DUELING_TABLE_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("dueling_table", DUELING_TABLE);
    public static final DeferredItem<BlockItem> CARD_DISPLAY_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("card_display", CARD_DISPLAY);

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private ModBlocks() {
    }
}
