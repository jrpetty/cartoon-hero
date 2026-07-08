package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Draws the pedestal's item floating above it, bobbing and rotating. */
public class DisplayPedestalRenderer implements BlockEntityRenderer<DisplayPedestalBlockEntity> {
    private final ItemRenderer itemRenderer;

    public DisplayPedestalRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(DisplayPedestalBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = be.getDisplayed();
        if (stack.isEmpty()) {
            return;
        }
        Level level = be.getLevel();
        double time = (level != null ? level.getGameTime() : 0L) + partialTick;

        float bob = (float) (Math.sin(time * 0.1) * 0.05) + 0.05F;
        float angle = (float) ((time * (2.0 + be.getSpin() * 3.0)) % 360.0);
        float scale = 0.5F + be.getScale() * 0.35F;

        pose.pushPose();
        pose.translate(0.5, 1.2 + bob, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.scale(scale, scale, scale);
        itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, level, 0);
        pose.popPose();
    }
}
