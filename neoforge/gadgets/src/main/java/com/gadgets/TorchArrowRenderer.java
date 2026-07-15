package com.gadgets;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the Torch Arrow projectile using the vanilla arrow model/texture.
 */
public class TorchArrowRenderer extends ArrowRenderer<TorchArrowEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");

    public TorchArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TorchArrowEntity entity) {
        return TEXTURE;
    }
}
