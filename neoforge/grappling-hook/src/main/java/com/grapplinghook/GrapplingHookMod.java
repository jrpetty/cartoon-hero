package com.grapplinghook;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(GrapplingHookMod.MODID)
public class GrapplingHookMod {
    public static final String MODID = "grapplinghook";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<Item> GRAPPLING_HOOK = ITEMS.register("grappling_hook",
            () -> new GrapplingHookItem(new Item.Properties().stacksTo(1)));

    public GrapplingHookMod(IEventBus modBus) {
        GrappleConfig.load();
        ITEMS.register(modBus);
        modBus.addListener(this::addCreative);
        // Drive the per-tick reel/swing for active grapples.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> GrappleManager.tick(event.getServer()));
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(GRAPPLING_HOOK);
        }
    }
}
