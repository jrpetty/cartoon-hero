package com.gadgets;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gadgets — Rope Arrow, Light Arrow, Rope, Player Sensor, Filter Hopper.
 */
public class Gadgets implements ModInitializer {
    public static final String MOD_ID = "gadgets";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Item ROPE_ARROW = register("rope_arrow", new RopeArrowItem(new Item.Settings()));
    public static final Item LIGHT_ARROW = register("light_arrow", new LightArrowItem(new Item.Settings()));

    public static final Block ROPE = registerBlock("rope",
            new RopeBlock(AbstractBlock.Settings.create()
                    .noCollision().strength(0.2F).sounds(BlockSoundGroup.WOOL).nonOpaque()),
            "tip.gadgets.rope.1");

    public static final Block PLAYER_SENSOR = registerBlock("player_sensor",
            new PlayerSensorBlock(AbstractBlock.Settings.create()
                    .strength(1.5F).requiresTool().sounds(BlockSoundGroup.METAL)),
            "tip.gadgets.player_sensor.1", "tip.gadgets.player_sensor.2", "tip.gadgets.player_sensor.3");
    public static final Block FILTER_HOPPER = registerBlock("filter_hopper",
            new FilterHopperBlock(AbstractBlock.Settings.create()
                    .strength(3.0F).requiresTool().sounds(BlockSoundGroup.METAL)),
            "tip.gadgets.filter_hopper.1", "tip.gadgets.filter_hopper.2");

    public static final Block REDSTONE_TRANSMITTER = registerBlock("redstone_transmitter",
            new RedstoneTransmitterBlock(AbstractBlock.Settings.create()
                    .strength(1.5F).requiresTool().sounds(BlockSoundGroup.METAL)),
            "tip.gadgets.redstone_transmitter.1", "tip.gadgets.redstone_transmitter.2");
    public static final Block REDSTONE_RECEIVER = registerBlock("redstone_receiver",
            new RedstoneReceiverBlock(AbstractBlock.Settings.create()
                    .strength(1.5F).requiresTool().sounds(BlockSoundGroup.METAL)),
            "tip.gadgets.redstone_receiver.1", "tip.gadgets.redstone_receiver.2");

    public static final Item REDSTONE_LINKER = register("redstone_linker",
            new RedstoneLinkerItem(new Item.Settings().maxCount(1)));

    public static final Block DISPLAY_PEDESTAL = registerBlock("display_pedestal",
            new DisplayPedestalBlock(AbstractBlock.Settings.create()
                    .strength(1.0F).sounds(BlockSoundGroup.STONE).nonOpaque()),
            "tip.gadgets.display_pedestal.1", "tip.gadgets.display_pedestal.2", "tip.gadgets.display_pedestal.3", "tip.gadgets.display_pedestal.4", "tip.gadgets.display_pedestal.5");
    public static final Block ITEM_SENDER = registerBlock("item_sender",
            new ItemSenderBlock(AbstractBlock.Settings.create()
                    .strength(1.5F).requiresTool().sounds(BlockSoundGroup.METAL)),
            "tip.gadgets.item_sender.1", "tip.gadgets.item_sender.2");
    public static final Block ITEM_RECEIVER = registerBlock("item_receiver",
            new ItemReceiverBlock(AbstractBlock.Settings.create()
                    .strength(1.5F).requiresTool().sounds(BlockSoundGroup.METAL)),
            "tip.gadgets.item_receiver.1", "tip.gadgets.item_receiver.2");

    public static final Block DRAIN = registerBlock("drain",
            new DrainBlock(AbstractBlock.Settings.create()
                    .strength(2.0F).requiresTool().sounds(BlockSoundGroup.METAL).nonOpaque()),
            "tip.gadgets.drain.1", "tip.gadgets.drain.2", "tip.gadgets.drain.3");

    public static final Block LOGIC_GATE = registerBlock("logic_gate",
            new LogicGateBlock(AbstractBlock.Settings.create()
                    .strength(1.5F).requiresTool().sounds(BlockSoundGroup.METAL)),
            "tip.gadgets.logic_gate.1", "tip.gadgets.logic_gate.2", "tip.gadgets.logic_gate.3");
    public static final Block ALARM = registerBlock("alarm",
            new AlarmBlock(AbstractBlock.Settings.create()
                    .strength(1.5F).requiresTool().sounds(BlockSoundGroup.METAL)),
            "tip.gadgets.alarm.1", "tip.gadgets.alarm.2");
    public static final Block ITEM_COUNTER = registerBlock("item_counter",
            new ItemCounterBlock(AbstractBlock.Settings.create()
                    .strength(1.5F).requiresTool().sounds(BlockSoundGroup.METAL)),
            "tip.gadgets.item_counter.1", "tip.gadgets.item_counter.2", "tip.gadgets.item_counter.3");

    public static final Item WIRELESS_REMOTE = register("wireless_remote",
            new WirelessRemoteItem(new Item.Settings().maxCount(1)));

    public static final RegistryKey<ItemGroup> GADGETS_GROUP_KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(MOD_ID, "gadgets"));
    public static final ItemGroup GADGETS_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(PLAYER_SENSOR))
            .displayName(Text.translatable("itemgroup.gadgets.gadgets"))
            .build();

    public static final EntityType<RopeArrowEntity> ROPE_ARROW_ENTITY = Registry.register(
            Registries.ENTITY_TYPE, Identifier.of(MOD_ID, "rope_arrow"),
            EntityType.Builder.<RopeArrowEntity>create(RopeArrowEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5f, 0.5f).maxTrackingRange(64).build("rope_arrow"));
    public static final EntityType<TorchArrowEntity> TORCH_ARROW_ENTITY = Registry.register(
            Registries.ENTITY_TYPE, Identifier.of(MOD_ID, "torch_arrow"),
            EntityType.Builder.<TorchArrowEntity>create(TorchArrowEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5f, 0.5f).maxTrackingRange(64).build("torch_arrow"));

    public static BlockEntityType<PlayerSensorBlockEntity> PLAYER_SENSOR_BE;
    public static BlockEntityType<FilterHopperBlockEntity> FILTER_HOPPER_BE;
    public static BlockEntityType<RedstoneTransmitterBlockEntity> REDSTONE_TRANSMITTER_BE;
    public static BlockEntityType<RedstoneReceiverBlockEntity> REDSTONE_RECEIVER_BE;
    public static BlockEntityType<DisplayPedestalBlockEntity> DISPLAY_PEDESTAL_BE;
    public static BlockEntityType<ItemSenderBlockEntity> ITEM_SENDER_BE;
    public static BlockEntityType<ItemReceiverBlockEntity> ITEM_RECEIVER_BE;
    public static BlockEntityType<DrainBlockEntity> DRAIN_BE;
    public static BlockEntityType<LogicGateBlockEntity> LOGIC_GATE_BE;
    public static BlockEntityType<AlarmBlockEntity> ALARM_BE;
    public static BlockEntityType<ItemCounterBlockEntity> ITEM_COUNTER_BE;

    @Override
    public void onInitialize() {
        PLAYER_SENSOR_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "player_sensor"),
                FabricBlockEntityTypeBuilder.create(PlayerSensorBlockEntity::new, PLAYER_SENSOR).build());
        FILTER_HOPPER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "filter_hopper"),
                FabricBlockEntityTypeBuilder.create(FilterHopperBlockEntity::new, FILTER_HOPPER).build());
        REDSTONE_TRANSMITTER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "redstone_transmitter"),
                FabricBlockEntityTypeBuilder.create(RedstoneTransmitterBlockEntity::new, REDSTONE_TRANSMITTER).build());
        REDSTONE_RECEIVER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "redstone_receiver"),
                FabricBlockEntityTypeBuilder.create(RedstoneReceiverBlockEntity::new, REDSTONE_RECEIVER).build());
        DISPLAY_PEDESTAL_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "display_pedestal"),
                FabricBlockEntityTypeBuilder.create(DisplayPedestalBlockEntity::new, DISPLAY_PEDESTAL).build());
        ITEM_SENDER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "item_sender"),
                FabricBlockEntityTypeBuilder.create(ItemSenderBlockEntity::new, ITEM_SENDER).build());
        ITEM_RECEIVER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "item_receiver"),
                FabricBlockEntityTypeBuilder.create(ItemReceiverBlockEntity::new, ITEM_RECEIVER).build());
        DRAIN_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "drain"),
                FabricBlockEntityTypeBuilder.create(DrainBlockEntity::new, DRAIN).build());
        LOGIC_GATE_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "logic_gate"),
                FabricBlockEntityTypeBuilder.create(LogicGateBlockEntity::new, LOGIC_GATE).build());
        ALARM_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "alarm"),
                FabricBlockEntityTypeBuilder.create(AlarmBlockEntity::new, ALARM).build());
        ITEM_COUNTER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "item_counter"),
                FabricBlockEntityTypeBuilder.create(ItemCounterBlockEntity::new, ITEM_COUNTER).build());

        Registry.register(Registries.ITEM_GROUP, GADGETS_GROUP_KEY, GADGETS_GROUP);
        ItemGroupEvents.modifyEntriesEvent(GADGETS_GROUP_KEY).register(entries -> {
            entries.add(ROPE_ARROW);
            entries.add(LIGHT_ARROW);
            entries.add(ROPE);
            entries.add(PLAYER_SENSOR);
            entries.add(FILTER_HOPPER);
            entries.add(REDSTONE_TRANSMITTER);
            entries.add(REDSTONE_RECEIVER);
            entries.add(REDSTONE_LINKER);
            entries.add(DISPLAY_PEDESTAL);
            entries.add(ITEM_SENDER);
            entries.add(ITEM_RECEIVER);
            entries.add(DRAIN);
            entries.add(LOGIC_GATE);
            entries.add(ALARM);
            entries.add(ITEM_COUNTER);
            entries.add(WIRELESS_REMOTE);
        });

        // Wireless redstone signals are transient — drop them when the server stops
        // so they never leak into the next world loaded in the same session.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            WirelessNetwork.clear();
            ItemNetwork.clear();
        });

        LOGGER.info("Gadgets loaded.");
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), item);
    }

    private static Block registerBlock(String name, Block block, String... tips) {
        Identifier id = Identifier.of(MOD_ID, name);
        Block registered = Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, tips.length == 0
                ? new BlockItem(registered, new Item.Settings())
                : new TooltipBlockItem(registered, new Item.Settings(), tips));
        return registered;
    }
}
