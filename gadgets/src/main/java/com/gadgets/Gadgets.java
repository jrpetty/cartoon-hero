package com.gadgets;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gadgets — Rope Arrow, Light Arrow, Player Sensor, Filter Hopper.
 */
public class Gadgets implements ModInitializer {
    public static final String MOD_ID = "gadgets";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Item ROPE_ARROW = register("rope_arrow", new RopeArrowItem(new Item.Settings()));
    public static final Item LIGHT_ARROW = register("light_arrow", new LightArrowItem(new Item.Settings()));

    public static final Block PLAYER_SENSOR = registerBlock("player_sensor",
            new PlayerSensorBlock(AbstractBlock.Settings.create()
                    .strength(1.5F).requiresTool().sounds(BlockSoundGroup.METAL)));
    public static final Block FILTER_HOPPER = registerBlock("filter_hopper",
            new FilterHopperBlock(AbstractBlock.Settings.create()
                    .strength(3.0F).requiresTool().sounds(BlockSoundGroup.METAL)));

    public static BlockEntityType<PlayerSensorBlockEntity> PLAYER_SENSOR_BE;
    public static BlockEntityType<FilterHopperBlockEntity> FILTER_HOPPER_BE;

    @Override
    public void onInitialize() {
        PLAYER_SENSOR_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "player_sensor"),
                FabricBlockEntityTypeBuilder.create(PlayerSensorBlockEntity::new, PLAYER_SENSOR).build());
        FILTER_HOPPER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "filter_hopper"),
                FabricBlockEntityTypeBuilder.create(FilterHopperBlockEntity::new, FILTER_HOPPER).build());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(ROPE_ARROW);
            entries.add(LIGHT_ARROW);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> {
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
