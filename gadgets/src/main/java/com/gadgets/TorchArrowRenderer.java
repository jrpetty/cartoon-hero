package com.gadgets;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ProjectileEntityRenderer;
import net.minecraft.util.Identifier;

/**
 * Renders the Torch Arrow projectile using the vanilla arrow model/texture.
 */
public class TorchArrowRenderer extends ProjectileEntityRenderer<TorchArrowEntity> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/projectiles/arrow.png");

    public TorchArrowRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(TorchArrowEntity entity) {
        return TEXTURE;
    }
}
