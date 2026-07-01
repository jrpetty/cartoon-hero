package com.gadgets;

import net.minecraft.client.render.entity.ArrowEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/**
 * Renders the Rope Arrow projectile using the vanilla arrow model/texture.
 */
public class RopeArrowRenderer extends ArrowEntityRenderer<RopeArrowEntity> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/projectiles/arrow.png");

    public RopeArrowRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(RopeArrowEntity entity) {
        return TEXTURE;
    }
}
