package com.gadgets;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the Rope Arrow projectile using the vanilla arrow model/texture.
 */
public class RopeArrowRenderer extends ArrowRenderer<RopeArrowEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");

    public RopeArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(RopeArrowEntity entity) {
        return TEXTURE;
    }
}
