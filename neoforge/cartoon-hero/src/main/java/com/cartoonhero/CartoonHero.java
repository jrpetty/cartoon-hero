package com.cartoonhero;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge entrypoint for the Cartoon Hero mod (Minecraft 1.21.1).
 */
@Mod(CartoonHero.MODID)
public class CartoonHero {
    public static final String MODID = "cartoonhero";

    public CartoonHero(IEventBus modBus) {
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
    }
}
