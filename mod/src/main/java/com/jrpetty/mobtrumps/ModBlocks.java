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

/** Blocks: the dueling table, the holo projector, and the two recycler machines. */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MobTrumps.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MobTrumps.MODID);

    public static final DeferredBlock<DuelingTableBlock> DUELING_TABLE =
            BLOCKS.register("dueling_table", () -> new DuelingTableBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).noOcclusion()));


    public static final DeferredBlock<HoloProjectorBlock> HOLO_PROJECTOR =
            BLOCKS.register("holo_projector", () -> new HoloProjectorBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F)
                            .lightLevel(s -> 9).noOcclusion()));


    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HoloProjectorBlockEntity>>
            HOLO_PROJECTOR_BE = BLOCK_ENTITIES.register("holo_projector",
            () -> BlockEntityType.Builder.of(HoloProjectorBlockEntity::new, HOLO_PROJECTOR.get()).build(null));

    public static final DeferredBlock<CardShredderBlock> CARD_SHREDDER =
            BLOCKS.register("card_shredder", () -> new CardShredderBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F)
                            .noOcclusion()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CardShredderBlockEntity>>
            CARD_SHREDDER_BE = BLOCK_ENTITIES.register("card_shredder",
            () -> BlockEntityType.Builder.of(CardShredderBlockEntity::new, CARD_SHREDDER.get()).build(null));

    public static final DeferredBlock<PrintingPressBlock> PRINTING_PRESS =
            BLOCKS.register("printing_press", () -> new PrintingPressBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(3.0F)
                            .noOcclusion()));

    public static final DeferredItem<BlockItem> CARD_SHREDDER_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("card_shredder", CARD_SHREDDER);
    public static final DeferredItem<BlockItem> PRINTING_PRESS_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("printing_press", PRINTING_PRESS);

    public static final DeferredItem<BlockItem> DUELING_TABLE_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("dueling_table", DUELING_TABLE);
    public static final DeferredItem<BlockItem> HOLO_PROJECTOR_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("holo_projector", HOLO_PROJECTOR);

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private ModBlocks() {
    }
}
