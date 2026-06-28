package com.cartoonhero;

import com.cartoonhero.block.ModBlocks;
import com.cartoonhero.item.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for the Cartoon Hero mod.
 *
 * <p>Registration order matters a little: items first (so the item-group icon
 * exists), then blocks (which also register their {@code BlockItem}s), then the
 * creative tab that lists them.
 */
public class CartoonHero implements ModInitializer {
    public static final String MOD_ID = "cartoonhero";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.registerModItems();
        ModBlocks.registerModBlocks();
        ModItemGroups.registerItemGroups();

        LOGGER.info("Cartoon Hero initialized — go save the day!");
    }
}
