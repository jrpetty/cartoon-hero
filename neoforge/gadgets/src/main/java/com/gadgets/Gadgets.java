package com.gadgets;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
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
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<Block> REDSTONE_TRANSMITTER = BLOCKS.register("redstone_transmitter",
            () -> new RedstoneTransmitterBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<Block> REDSTONE_RECEIVER = BLOCKS.register("redstone_receiver",
            () -> new RedstoneReceiverBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));

    public static final DeferredItem<?> PLAYER_SENSOR_ITEM = ITEMS.register("player_sensor", () -> new TooltipBlockItem(PLAYER_SENSOR.get(), new Item.Properties(),
            "tip.gadgets.player_sensor.1", "tip.gadgets.player_sensor.2", "tip.gadgets.player_sensor.3"));
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
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<Block> ITEM_RECEIVER = BLOCKS.register("item_receiver",
            () -> new ItemReceiverBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<Block> DRAIN = BLOCKS.register("drain",
            () -> new DrainBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<Block> ITEM_COUNTER = BLOCKS.register("item_counter",
            () -> new ItemCounterBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<Block> ITEM_MAGNET = BLOCKS.register("item_magnet",
            () -> new ItemMagnetBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<Block> STOCK_MONITOR = BLOCKS.register("stock_monitor",
            () -> new StockMonitorBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<Block> TRASH_CAN = BLOCKS.register("trash_can",
            () -> new TrashCanBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<Block> STORAGE_SENSOR = BLOCKS.register("storage_sensor",
            () -> new StorageSensorBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<Block> COMMAND_HUB = BLOCKS.register("command_hub",
            () -> new CommandHubBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));

    public static final DeferredItem<?> DISPLAY_PEDESTAL_ITEM = ITEMS.register("display_pedestal", () -> new TooltipBlockItem(DISPLAY_PEDESTAL.get(), new Item.Properties(),
            "tip.gadgets.display_pedestal.1", "tip.gadgets.display_pedestal.2", "tip.gadgets.display_pedestal.3", "tip.gadgets.display_pedestal.4", "tip.gadgets.display_pedestal.5"));
    public static final DeferredItem<?> ITEM_SENDER_ITEM = ITEMS.register("item_sender", () -> new TooltipBlockItem(ITEM_SENDER.get(), new Item.Properties(),
            "tip.gadgets.item_sender.1", "tip.gadgets.item_sender.2"));
    public static final DeferredItem<?> ITEM_RECEIVER_ITEM = ITEMS.register("item_receiver", () -> new TooltipBlockItem(ITEM_RECEIVER.get(), new Item.Properties(),
            "tip.gadgets.item_receiver.1", "tip.gadgets.item_receiver.2"));
    public static final DeferredItem<?> DRAIN_ITEM = ITEMS.register("drain", () -> new TooltipBlockItem(DRAIN.get(), new Item.Properties(),
            "tip.gadgets.drain.1", "tip.gadgets.drain.2", "tip.gadgets.drain.3"));
    public static final DeferredItem<?> ITEM_COUNTER_ITEM = ITEMS.register("item_counter", () -> new TooltipBlockItem(ITEM_COUNTER.get(), new Item.Properties(),
            "tip.gadgets.item_counter.1", "tip.gadgets.item_counter.2", "tip.gadgets.item_counter.3", "tip.gadgets.item_counter.4"));
    public static final DeferredItem<?> ITEM_MAGNET_ITEM = ITEMS.register("item_magnet", () -> new TooltipBlockItem(ITEM_MAGNET.get(), new Item.Properties(),
            "tip.gadgets.item_magnet.1", "tip.gadgets.item_magnet.2", "tip.gadgets.item_magnet.3"));
    public static final DeferredItem<?> STOCK_MONITOR_ITEM = ITEMS.register("stock_monitor", () -> new TooltipBlockItem(STOCK_MONITOR.get(), new Item.Properties(),
            "tip.gadgets.stock_monitor.1", "tip.gadgets.stock_monitor.2", "tip.gadgets.stock_monitor.3", "tip.gadgets.stock_monitor.4"));
    public static final DeferredItem<?> TRASH_CAN_ITEM = ITEMS.register("trash_can", () -> new TooltipBlockItem(TRASH_CAN.get(), new Item.Properties(),
            "tip.gadgets.trash_can.1", "tip.gadgets.trash_can.2", "tip.gadgets.trash_can.3"));
    public static final DeferredItem<?> STORAGE_SENSOR_ITEM = ITEMS.register("storage_sensor", () -> new TooltipBlockItem(STORAGE_SENSOR.get(), new Item.Properties(),
            "tip.gadgets.storage_sensor.1", "tip.gadgets.storage_sensor.2", "tip.gadgets.storage_sensor.3"));
    public static final DeferredItem<?> COMMAND_HUB_ITEM = ITEMS.register("command_hub", () -> new TooltipBlockItem(COMMAND_HUB.get(), new Item.Properties(),
            "tip.gadgets.command_hub.1", "tip.gadgets.command_hub.2", "tip.gadgets.command_hub.3"));
    public static final DeferredItem<Item> MONITOR_WAND =
            ITEMS.register("monitor_wand", () -> new MonitorWandItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> WIRELESS_REMOTE =
            ITEMS.register("wireless_remote", () -> new WirelessRemoteItem(new Item.Properties().stacksTo(1)));

    public static final Supplier<BlockEntityType<PlayerSensorBlockEntity>> PLAYER_SENSOR_BE =
            BLOCK_ENTITIES.register("player_sensor",
                    () -> BlockEntityType.Builder.of(PlayerSensorBlockEntity::new, PLAYER_SENSOR.get()).build(null));
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
    public static final Supplier<BlockEntityType<ItemCounterBlockEntity>> ITEM_COUNTER_BE =
            BLOCK_ENTITIES.register("item_counter",
                    () -> BlockEntityType.Builder.of(ItemCounterBlockEntity::new, ITEM_COUNTER.get()).build(null));
    public static final Supplier<BlockEntityType<ItemMagnetBlockEntity>> ITEM_MAGNET_BE =
            BLOCK_ENTITIES.register("item_magnet",
                    () -> BlockEntityType.Builder.of(ItemMagnetBlockEntity::new, ITEM_MAGNET.get()).build(null));
    public static final Supplier<BlockEntityType<StockMonitorBlockEntity>> STOCK_MONITOR_BE =
            BLOCK_ENTITIES.register("stock_monitor",
                    () -> BlockEntityType.Builder.of(StockMonitorBlockEntity::new, STOCK_MONITOR.get()).build(null));
    public static final Supplier<BlockEntityType<TrashCanBlockEntity>> TRASH_CAN_BE =
            BLOCK_ENTITIES.register("trash_can",
                    () -> BlockEntityType.Builder.of(TrashCanBlockEntity::new, TRASH_CAN.get()).build(null));
    public static final Supplier<BlockEntityType<StorageSensorBlockEntity>> STORAGE_SENSOR_BE =
            BLOCK_ENTITIES.register("storage_sensor",
                    () -> BlockEntityType.Builder.of(StorageSensorBlockEntity::new, STORAGE_SENSOR.get()).build(null));
    public static final Supplier<BlockEntityType<CommandHubBlockEntity>> COMMAND_HUB_BE =
            BLOCK_ENTITIES.register("command_hub",
                    () -> BlockEntityType.Builder.of(CommandHubBlockEntity::new, COMMAND_HUB.get()).build(null));

    public static final Supplier<CreativeModeTab> GADGETS_TAB = CREATIVE_TABS.register("gadgets",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemgroup.gadgets.gadgets"))
                    .icon(() -> new ItemStack(PLAYER_SENSOR.get()))
                    .displayItems((params, output) -> {
                        output.accept(ROPE_ARROW.get());
                        output.accept(LIGHT_ARROW.get());
                        output.accept(ROPE_ITEM.get());
                        output.accept(PLAYER_SENSOR_ITEM.get());
                        output.accept(REDSTONE_TRANSMITTER_ITEM.get());
                        output.accept(REDSTONE_RECEIVER_ITEM.get());
                        output.accept(REDSTONE_LINKER.get());
                        output.accept(DISPLAY_PEDESTAL_ITEM.get());
                        output.accept(ITEM_SENDER_ITEM.get());
                        output.accept(ITEM_RECEIVER_ITEM.get());
                        output.accept(DRAIN_ITEM.get());
                        output.accept(ITEM_COUNTER_ITEM.get());
                        output.accept(ITEM_MAGNET_ITEM.get());
                        output.accept(STOCK_MONITOR_ITEM.get());
                        output.accept(TRASH_CAN_ITEM.get());
                        output.accept(STORAGE_SENSOR_ITEM.get());
                        output.accept(COMMAND_HUB_ITEM.get());
                        output.accept(MONITOR_WAND.get());
                        output.accept(WIRELESS_REMOTE.get());
                    })
                    .build());

    public Gadgets(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        CREATIVE_TABS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(Gadgets::registerPayloads);

        // Wireless redstone signals are transient — drop them when the server stops
        // so they never leak into the next world loaded in the same session.
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            WirelessNetwork.clear();
            ItemNetwork.clear();
        });

        // The wand has to run before the block's own interaction: hubs, counters,
        // monitors and chests all open a screen on click and would eat it first.
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            ItemStack held = event.getItemStack();
            if (held.getItem() instanceof MonitorWandItem
                    && MonitorWandItem.handle(event.getLevel(), event.getEntity(), held, event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(GadgetConfigPayload.TYPE, GadgetConfigPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        GadgetConfigPayload.apply(sp, payload);
                    }
                }));
    }
}
