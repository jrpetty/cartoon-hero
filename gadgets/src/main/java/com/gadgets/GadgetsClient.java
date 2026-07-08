package com.gadgets;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

/**
 * Client-side setup: registers the Rope Arrow projectile renderer and the
 * Display Pedestal block-entity renderer.
 */
public class GadgetsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Gadgets.ROPE_ARROW_ENTITY, RopeArrowRenderer::new);
        BlockEntityRendererFactories.register(Gadgets.DISPLAY_PEDESTAL_BE, DisplayPedestalRenderer::new);
    }
}
