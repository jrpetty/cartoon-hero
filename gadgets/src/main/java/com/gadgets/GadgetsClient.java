package com.gadgets;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Client-side setup: registers the Rope Arrow projectile renderer.
 */
public class GadgetsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Gadgets.ROPE_ARROW_ENTITY, RopeArrowRenderer::new);
    }
}
