package com.gadgets;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
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
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final Supplier<EntityType<RopeArrowEntity>> ROPE_ARROW_ENTITY = ENTITIES.register("rope_arrow",
            () -> EntityType.Builder.<RopeArrowEntity>of(RopeArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20).build("rope_arrow"));

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
    public static final DeferredBlock<Block> REDSTONE_TRANSMITTER = BLOCKS.register("redstone_transmitter",
            () -> new RedstoneTransmitterBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
    public static final DeferredBlock<Block> REDSTONE_RECEIVER = BLOCKS.register("redstone_receiver",
            () -> new RedstoneReceiverBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL)));

    public static final DeferredItem<?> PLAYER_SENSOR_ITEM = ITEMS.registerSimpleBlockItem("player_sensor", PLAYER_SENSOR);
    public static final DeferredItem<?> FILTER_HOPPER_ITEM = ITEMS.registerSimpleBlockItem("filter_hopper", FILTER_HOPPER);
    public static final DeferredItem<?> REDSTONE_TRANSMITTER_ITEM = ITEMS.registerSimpleBlockItem("redstone_transmitter", REDSTONE_TRANSMITTER);
    public static final DeferredItem<?> REDSTONE_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("redstone_receiver", REDSTONE_RECEIVER);
    public static final DeferredItem<Item> REDSTONE_LINKER =
            ITEMS.register("redstone_linker", () -> new RedstoneLinkerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredBlock<Block> DISPLAY_PEDESTAL = BLOCKS.register("display_pedestal",
            () -> new DisplayPedestalBlock(BlockBehaviour.Properties.of()
                    .strength(1.0F).sound(SoundType.STONE).noOcclusion()));
    public static final DeferredBlock<Block> ITEM_SENDER = BLOCKS.register("item_sender",
            () -> new ItemSenderBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
    public static final DeferredBlock<Block> ITEM_RECEIVER = BLOCKS.register("item_receiver",
            () -> new ItemReceiverBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL)));

    public static final DeferredBlock<Block> DRAIN = BLOCKS.register("drain",
            () -> new DrainBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));

    public static final DeferredItem<?> DISPLAY_PEDESTAL_ITEM = ITEMS.registerSimpleBlockItem("display_pedestal", DISPLAY_PEDESTAL);
    public static final DeferredItem<?> ITEM_SENDER_ITEM = ITEMS.registerSimpleBlockItem("item_sender", ITEM_SENDER);
    public static final DeferredItem<?> ITEM_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("item_receiver", ITEM_RECEIVER);
    public static final DeferredItem<?> DRAIN_ITEM = ITEMS.registerSimpleBlockItem("drain", DRAIN);

    public static final Supplier<BlockEntityType<PlayerSensorBlockEntity>> PLAYER_SENSOR_BE =
            BLOCK_ENTITIES.register("player_sensor",
                    () -> BlockEntityType.Builder.of(PlayerSensorBlockEntity::new, PLAYER_SENSOR.get()).build(null));
    public static final Supplier<BlockEntityType<FilterHopperBlockEntity>> FILTER_HOPPER_BE =
            BLOCK_ENTITIES.register("filter_hopper",
                    () -> BlockEntityType.Builder.of(FilterHopperBlockEntity::new, FILTER_HOPPER.get()).build(null));
    public static final Supplier<BlockEntityType<RedstoneTransmitterBlockEntity>> REDSTONE_TRANSMITTER_BE =
            BLOCK_ENTITIES.register("redstone_transmitter",
                    () -> BlockEntityType.Builder.of(RedstoneTransmitterBlockEntity::new, REDSTONE_TRANSMITTER.get()).build(null));
    public static final Supplier<BlockEntityType<RedstoneReceiverBlockEntity>> REDSTONE_RECEIVER_BE =
            BLOCK_ENTITIES.register("redstone_receiver",
                    () -> BlockEntityType.Builder.of(RedstoneReceiverBlockEntity::new, REDSTONE_RECEIVER.get()).build(null));
    public static final Supplier<BlockEntityType<DisplayPedestalBlockEntity>> DISPLAY_PEDESTAL_BE =
            BLOCK_ENTITIES.register("display_pedestal",
                    () -> BlockEntityType.Builder.of(DisplayPedestalBlockEntity::new, DISPLAY_PEDESTAL.get()).build(null));
    public static final Supplier<BlockEntityType<ItemSenderBlockEntity>> ITEM_SENDER_BE =
            BLOCK_ENTITIES.register("item_sender",
                    () -> BlockEntityType.Builder.of(ItemSenderBlockEntity::new, ITEM_SENDER.get()).build(null));
    public static final Supplier<BlockEntityType<ItemReceiverBlockEntity>> ITEM_RECEIVER_BE =
            BLOCK_ENTITIES.register("item_receiver",
                    () -> BlockEntityType.Builder.of(ItemReceiverBlockEntity::new, ITEM_RECEIVER.get()).build(null));
    public static final Supplier<BlockEntityType<DrainBlockEntity>> DRAIN_BE =
            BLOCK_ENTITIES.register("drain",
                    () -> BlockEntityType.Builder.of(DrainBlockEntity::new, DRAIN.get()).build(null));

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
                        output.accept(REDSTONE_TRANSMITTER_ITEM.get());
                        output.accept(REDSTONE_RECEIVER_ITEM.get());
                        output.accept(REDSTONE_LINKER.get());
                        output.accept(DISPLAY_PEDESTAL_ITEM.get());
                        output.accept(ITEM_SENDER_ITEM.get());
                        output.accept(ITEM_RECEIVER_ITEM.get());
                        output.accept(DRAIN_ITEM.get());
                    })
                    .build());

    public Gadgets(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        CREATIVE_TABS.register(modBus);
        ENTITIES.register(modBus);

        // Wireless redstone signals are transient — drop them when the server stops
        // so they never leak into the next world loaded in the same session.
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            WirelessNetwork.clear();
            ItemNetwork.clear();
        });
    }
}
