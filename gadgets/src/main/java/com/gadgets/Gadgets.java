package com.gadgets;

import net.fabricmc.api.ModInitializer;
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
                    .noCollision().strength(0.2F).sounds(BlockSoundGroup.WOOL).nonOpaque()));

    public static final Block PLAYER_SENSOR = registerBlock("player_sensor",
            new PlayerSensorBlock(AbstractBlock.Settings.create()
                    .strength(1.5F).requiresTool().sounds(BlockSoundGroup.METAL)));
    public static final Block FILTER_HOPPER = registerBlock("filter_hopper",
            new FilterHopperBlock(AbstractBlock.Settings.create()
                    .strength(3.0F).requiresTool().sounds(BlockSoundGroup.METAL)));

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

    public static BlockEntityType<PlayerSensorBlockEntity> PLAYER_SENSOR_BE;
    public static BlockEntityType<FilterHopperBlockEntity> FILTER_HOPPER_BE;

    @Override
    public void onInitialize() {
        PLAYER_SENSOR_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "player_sensor"),
                FabricBlockEntityTypeBuilder.create(PlayerSensorBlockEntity::new, PLAYER_SENSOR).build());
        FILTER_HOPPER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "filter_hopper"),
                FabricBlockEntityTypeBuilder.create(FilterHopperBlockEntity::new, FILTER_HOPPER).build());

        Registry.register(Registries.ITEM_GROUP, GADGETS_GROUP_KEY, GADGETS_GROUP);
        ItemGroupEvents.modifyEntriesEvent(GADGETS_GROUP_KEY).register(entries -> {
            entries.add(ROPE_ARROW);
            entries.add(LIGHT_ARROW);
            entries.add(ROPE);
            entries.add(PLAYER_SENSOR);
            entries.add(FILTER_HOPPER);
        });

        LOGGER.info("Gadgets loaded.");
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), item);
    }

    private static Block registerBlock(String name, Block block) {
        Identifier id = Identifier.of(MOD_ID, name);
        Block registered = Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, new BlockItem(registered, new Item.Settings()));
        return registered;
    }
}
