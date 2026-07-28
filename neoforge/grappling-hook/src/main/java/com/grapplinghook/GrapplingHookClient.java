package com.grapplinghook;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;

@EventBusSubscriber(modid = GrapplingHookMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class GrapplingHookClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MenuScreens.register(GrapplingHookMod.ENHANCEMENT_MENU.get(), EnhancementScreen::new));
    }
}
