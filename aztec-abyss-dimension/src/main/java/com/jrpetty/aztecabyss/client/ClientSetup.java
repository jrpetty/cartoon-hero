package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.particle.BlackPortalSwirlParticle;
import com.jrpetty.aztecabyss.registry.ModParticles;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
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

    /** Opens the Glade hub while in the maze. Default: M. Rebindable. */
    public static final KeyMapping MAZE_HUB = new KeyMapping(
            "key.aztecabyss.maze_hub",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.aztecabyss");

    /**
     * Bolts the Griever's skin onto the shared spider renderer.
     *
     * <p>Added as a layer rather than by replacing the renderer, so every other
     * spider in every other dimension is untouched and no other mod's spider
     * changes are trodden on. The layer itself checks the tag and does nothing at
     * all for an ordinary spider.
     */
    @SubscribeEvent
    public static void onAddLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        net.minecraft.client.renderer.entity.LivingEntityRenderer<
                net.minecraft.world.entity.monster.Spider,
                net.minecraft.client.model.SpiderModel<net.minecraft.world.entity.monster.Spider>> renderer =
                event.getRenderer(net.minecraft.world.entity.EntityType.SPIDER);
        if (renderer != null) {
            renderer.addLayer(new GrieverLayer(renderer));
        }
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BLACK_PORTAL_SWIRL.get(), BlackPortalSwirlParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_HUD);
        event.register(PING);
        event.register(MAZE_HUB);
    }

    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(
                ResourceLocation.fromNamespaceAndPath(com.jrpetty.aztecabyss.AztecAbyssConstants.MOD_ID, "abyss"),
                new AbyssSkyEffects());
    }
}
