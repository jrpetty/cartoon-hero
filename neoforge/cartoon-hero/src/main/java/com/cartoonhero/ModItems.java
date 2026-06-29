package com.cartoonhero;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CartoonHero.MODID);

    public static final DeferredItem<Item> HERO_EMBLEM = ITEMS.register("hero_emblem",
            () -> new HeroEmblemItem(new Item.Properties().stacksTo(1)));
}
