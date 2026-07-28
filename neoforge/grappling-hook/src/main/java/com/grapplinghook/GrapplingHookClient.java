package com.grapplinghook;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = GrapplingHookMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class GrapplingHookClient {
    // NeoForge keeps MenuScreens.register private; screens are bound here instead.
    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(GrapplingHookMod.ENHANCEMENT_MENU.get(), EnhancementScreen::new);
    }
}
