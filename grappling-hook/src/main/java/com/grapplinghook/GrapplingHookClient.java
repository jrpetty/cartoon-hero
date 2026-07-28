package com.grapplinghook;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class GrapplingHookClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(GrapplingHookMod.ENHANCEMENT_SCREEN_HANDLER, EnhancementScreen::new);
    }
}
