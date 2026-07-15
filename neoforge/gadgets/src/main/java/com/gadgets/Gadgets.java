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
    public static final Supplier<EntityType<TorchArrowEntity>> TORCH_ARROW_ENTITY = ENTITIES.register("torch_arrow",
            () -> EntityType.Builder.<TorchArrowEntity>of(TorchArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20).build("torch_arrow"));

    public static final DeferredItem<Item> ROPE_ARROW =
            ITEMS.register("rope_arrow", () -> new RopeArrowItem(new Item.Properties()));
    public static final DeferredItem<Item> LIGHT_ARROW =
            ITEMS.register("light_arrow", () -> new LightArrowItem(new Item.Properties()));

    public static final DeferredBlock<Block> ROPE = BLOCKS.register("rope",
            () -> new RopeBlock(BlockBehaviour.Properties.of()
                    .noCollission().strength(0.2F).sound(SoundType.WOOL).noOcclusion()));
    public static final DeferredItem<?> ROPE_ITEM = ITEMS.register("rope", () -> new TooltipBlockItem(ROPE.get(), new Item.Properties(),
            "tip.gadgets.rope.1"));

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

    public static final DeferredItem<?> PLAYER_SENSOR_ITEM = ITEMS.register("player_sensor", () -> new TooltipBlockItem(PLAYER_SENSOR.get(), new Item.Properties(),
            "tip.gadgets.player_sensor.1", "tip.gadgets.player_sensor.2", "tip.gadgets.player_sensor.3"));
    public static final DeferredItem<?> FILTER_HOPPER_ITEM = ITEMS.register("filter_hopper", () -> new TooltipBlockItem(FILTER_HOPPER.get(), new Item.Properties(),
            "tip.gadgets.filter_hopper.1", "tip.gadgets.filter_hopper.2"));
    public static final DeferredItem<?> REDSTONE_TRANSMITTER_ITEM = ITEMS.register("redstone_transmitter", () -> new TooltipBlockItem(REDSTONE_TRANSMITTER.get(), new Item.Properties(),
            "tip.gadgets.redstone_transmitter.1", "tip.gadgets.redstone_transmitter.2"));
    public static final DeferredItem<?> REDSTONE_RECEIVER_ITEM = ITEMS.register("redstone_receiver", () -> new TooltipBlockItem(REDSTONE_RECEIVER.get(), new Item.Properties(),
            "tip.gadgets.redstone_receiver.1", "tip.gadgets.redstone_receiver.2"));
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
    public static final DeferredBlock<Block> LOGIC_GATE = BLOCKS.register("logic_gate",
            () -> new LogicGateBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
    public static final DeferredBlock<Block> ALARM = BLOCKS.register("alarm",
            () -> new AlarmBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL)));

    public static final DeferredItem<?> DISPLAY_PEDESTAL_ITEM = ITEMS.register("display_pedestal", () -> new TooltipBlockItem(DISPLAY_PEDESTAL.get(), new Item.Properties(),
            "tip.gadgets.display_pedestal.1", "tip.gadgets.display_pedestal.2", "tip.gadgets.display_pedestal.3", "tip.gadgets.display_pedestal.4", "tip.gadgets.display_pedestal.5"));
    public static final DeferredItem<?> ITEM_SENDER_ITEM = ITEMS.register("item_sender", () -> new TooltipBlockItem(ITEM_SENDER.get(), new Item.Properties(),
            "tip.gadgets.item_sender.1", "tip.gadgets.item_sender.2"));
    public static final DeferredItem<?> ITEM_RECEIVER_ITEM = ITEMS.register("item_receiver", () -> new TooltipBlockItem(ITEM_RECEIVER.get(), new Item.Properties(),
            "tip.gadgets.item_receiver.1", "tip.gadgets.item_receiver.2"));
    public static final DeferredItem<?> DRAIN_ITEM = ITEMS.register("drain", () -> new TooltipBlockItem(DRAIN.get(), new Item.Properties(),
            "tip.gadgets.drain.1", "tip.gadgets.drain.2", "tip.gadgets.drain.3"));
    public static final DeferredItem<?> LOGIC_GATE_ITEM = ITEMS.register("logic_gate", () -> new TooltipBlockItem(LOGIC_GATE.get(), new Item.Properties(),
            "tip.gadgets.logic_gate.1", "tip.gadgets.logic_gate.2", "tip.gadgets.logic_gate.3"));
    public static final DeferredItem<?> ALARM_ITEM = ITEMS.register("alarm", () -> new TooltipBlockItem(ALARM.get(), new Item.Properties(),
            "tip.gadgets.alarm.1", "tip.gadgets.alarm.2"));
    public static final DeferredItem<Item> WIRELESS_REMOTE =
            ITEMS.register("wireless_remote", () -> new WirelessRemoteItem(new Item.Properties().stacksTo(1)));

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
    public static final Supplier<BlockEntityType<LogicGateBlockEntity>> LOGIC_GATE_BE =
            BLOCK_ENTITIES.register("logic_gate",
                    () -> BlockEntityType.Builder.of(LogicGateBlockEntity::new, LOGIC_GATE.get()).build(null));
    public static final Supplier<BlockEntityType<AlarmBlockEntity>> ALARM_BE =
            BLOCK_ENTITIES.register("alarm",
                    () -> BlockEntityType.Builder.of(AlarmBlockEntity::new, ALARM.get()).build(null));

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
                        output.accept(LOGIC_GATE_ITEM.get());
                        output.accept(ALARM_ITEM.get());
                        output.accept(WIRELESS_REMOTE.get());
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
