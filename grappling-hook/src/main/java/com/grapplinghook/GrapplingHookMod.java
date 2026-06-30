package com.grapplinghook;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Grappling Hook — right-click a block in range and get yanked to it.
 */
public class GrapplingHookMod implements ModInitializer {
    public static final String MOD_ID = "grapplinghook";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Item GRAPPLING_HOOK = register(
            "grappling_hook",
            new GrapplingHookItem(new Item.Settings().maxCount(1)));

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name), item);
    }

    @Override
    public void onInitialize() {
        GrappleConfig.load();

        // Drive the per-tick reel/swing for active grapples.
        ServerTickEvents.END_SERVER_TICK.register(GrappleManager::tick);

        // Show up in the vanilla Tools & Utilities creative tab.
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(GRAPPLING_HOOK));

        LOGGER.info("Grappling Hook ready — charge up and aim high.");
    }
}
