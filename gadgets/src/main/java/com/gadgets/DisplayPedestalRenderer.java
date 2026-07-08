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

/** Draws the pedestal's item floating above it, bobbing and rotating. */
public class DisplayPedestalRenderer implements BlockEntityRenderer<DisplayPedestalBlockEntity> {
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

        float bob = (float) (Math.sin(time * 0.1) * 0.05) + 0.05F;
        float angle = (float) ((time * (2.0 + be.getSpin() * 3.0)) % 360.0);
        float scale = 0.5F + be.getScale() * 0.35F;

        matrices.push();
        matrices.translate(0.5, 1.2 + bob, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
        matrices.scale(scale, scale, scale);
        itemRenderer.renderItem(stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
                matrices, vertexConsumers, world, 0);
        matrices.pop();
    }
}
