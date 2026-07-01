package com.gadgets;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Gadgets.MODID)
public class Gadgets {
    public static final String MODID = "gadgets";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredItem<Item> ROPE_ARROW =
            ITEMS.register("rope_arrow", () -> new RopeArrowItem(new Item.Properties()));
    public static final DeferredItem<Item> LIGHT_ARROW =
            ITEMS.register("light_arrow", () -> new LightArrowItem(new Item.Properties()));

    public static final DeferredBlock<Block> ROPE = BLOCKS.register("rope",
            () -> new RopeBlock(BlockBehaviour.Properties.of()
                    .noCollission().strength(0.2F).sound(SoundType.WOOL).noOcclusion()));
    public static final DeferredItem<?> ROPE_ITEM = ITEMS.registerSimpleBlockItem("rope", ROPE);

    public static final DeferredBlock<Block> PLAYER_SENSOR = BLOCKS.register("player_sensor",
            () -> new PlayerSensorBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
    public static final DeferredBlock<Block> FILTER_HOPPER = BLOCKS.register("filter_hopper",
            () -> new FilterHopperBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));

    public static final DeferredItem<?> PLAYER_SENSOR_ITEM = ITEMS.registerSimpleBlockItem("player_sensor", PLAYER_SENSOR);
    public static final DeferredItem<?> FILTER_HOPPER_ITEM = ITEMS.registerSimpleBlockItem("filter_hopper", FILTER_HOPPER);

    public static final Supplier<BlockEntityType<PlayerSensorBlockEntity>> PLAYER_SENSOR_BE =
            BLOCK_ENTITIES.register("player_sensor",
                    () -> BlockEntityType.Builder.of(PlayerSensorBlockEntity::new, PLAYER_SENSOR.get()).build(null));
    public static final Supplier<BlockEntityType<FilterHopperBlockEntity>> FILTER_HOPPER_BE =
            BLOCK_ENTITIES.register("filter_hopper",
                    () -> BlockEntityType.Builder.of(FilterHopperBlockEntity::new, FILTER_HOPPER.get()).build(null));

    public static final Supplier<CreativeModeTab> GADGETS_TAB = CREATIVE_TABS.register("gadgets",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemgroup.gadgets.gadgets"))
                    .icon(() -> new ItemStack(PLAYER_SENSOR.get()))
                    .displayItems((params, output) -> {
                        output.accept(ROPE_ARROW.get());
                        output.accept(LIGHT_ARROW.get());
                        output.accept(ROPE_ITEM.get());
                        output.accept(PLAYER_SENSOR_ITEM.get());
                        output.accept(FILTER_HOPPER_ITEM.get());
                    })
                    .build());

    public Gadgets(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
}
