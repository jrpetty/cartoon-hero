package com.gadgets;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

/** Draws the case's item floating inside it, gently bobbing and (optionally) rotating. */
public class DisplayPedestalRenderer implements BlockEntityRenderer<DisplayPedestalBlockEntity> {
    /** Degrees per tick for spin settings Off/Slow/Medium/Fast. */
    private static final float[] SPIN_SPEED = {0.0F, 1.5F, 4.0F, 8.0F};

    private final ItemRenderer itemRenderer;

    public DisplayPedestalRenderer(BlockEntityRendererFactory.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(DisplayPedestalBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ItemStack stack = be.getDisplayed();
        if (stack.isEmpty()) {
            return;
        }
        World world = be.getWorld();
        double time = (world != null ? world.getTime() : 0L) + tickDelta;

        int spin = Math.min(Math.max(be.getSpin(), 0), SPIN_SPEED.length - 1);
        float bob = (float) (Math.sin(time * 0.08) * 0.03);
        float angle = spin == 0 ? 0.0F : (float) ((time * SPIN_SPEED[spin]) % 360.0);
        float scale = 0.35F + be.getScale() * 0.225F;

        matrices.push();
        matrices.translate(0.5, 0.5 + bob, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
        matrices.scale(scale, scale, scale);
        itemRenderer.renderItem(stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
                matrices, vertexConsumers, world, 0);
        matrices.pop();
    }
}
