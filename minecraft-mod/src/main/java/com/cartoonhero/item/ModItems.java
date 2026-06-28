package com.cartoonhero.item;

import com.cartoonhero.CartoonHero;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Holds and registers every item this mod adds.
 */
public class ModItems {

    public static final Item HERO_EMBLEM = register(
            "hero_emblem",
            new HeroEmblemItem(new Item.Settings().maxCount(1)));

    private static Item register(String name, Item item) {
        Identifier id = Identifier.of(CartoonHero.MOD_ID, name);
        return Registry.register(Registries.ITEM, id, item);
    }

    /**
     * Touching this method forces the class to load, which runs the static
     * field initializers above and performs the actual registration.
     */
    public static void registerModItems() {
        CartoonHero.LOGGER.info("Registering items for {}", CartoonHero.MOD_ID);
    }
}
