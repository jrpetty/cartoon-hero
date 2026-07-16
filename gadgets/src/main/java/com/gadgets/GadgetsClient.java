package com.gadgets;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

/**
 * Client-side setup: projectile/block-entity renderers and render layers.
 */
public class GadgetsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Gadgets.ROPE_ARROW_ENTITY, RopeArrowRenderer::new);
        EntityRendererRegistry.register(Gadgets.TORCH_ARROW_ENTITY, TorchArrowRenderer::new);
        BlockEntityRendererFactories.register(Gadgets.DISPLAY_PEDESTAL_BE, DisplayPedestalRenderer::new);
        BlockEntityRendererFactories.register(Gadgets.DRAIN_BE, DrainRenderer::new);
        BlockEntityRendererFactories.register(Gadgets.ITEM_COUNTER_BE, ItemCounterRenderer::new);
    }
}
