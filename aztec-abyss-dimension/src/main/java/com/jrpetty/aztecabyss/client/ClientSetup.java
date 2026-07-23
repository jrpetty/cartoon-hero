package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.particle.BlackPortalSwirlParticle;
import com.jrpetty.aztecabyss.registry.ModParticles;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = com.jrpetty.aztecabyss.AztecAbyssConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    /** Toggles the live run HUD. Default: H. Rebindable in Controls. */
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.aztecabyss.toggle_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.aztecabyss");

    /** Pings the spot the player is looking at for the squad. Default: B. Rebindable. */
    public static final KeyMapping PING = new KeyMapping(
            "key.aztecabyss.ping",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.aztecabyss");

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BLACK_PORTAL_SWIRL.get(), BlackPortalSwirlParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_HUD);
        event.register(PING);
    }
}
