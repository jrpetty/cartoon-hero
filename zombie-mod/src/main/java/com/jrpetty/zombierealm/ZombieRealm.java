package com.jrpetty.zombierealm;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Zombie Dimension — adds "The Rotten Realm", a data-driven eternal-night
 * dimension crawling with the undead, reached with the Rotten Heart item.
 */
@Mod(ZombieRealm.MODID)
public class ZombieRealm {

    public static final String MODID = "zombierealm";

    /** Dimension key for the Rotten Realm (matches data/zombierealm/dimension/rotten_realm.json). */
    public static final ResourceKey<Level> ROTTEN_REALM = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(MODID, "rotten_realm"));

    public ZombieRealm(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_MODE_TABS.register(modEventBus);
    }
}
