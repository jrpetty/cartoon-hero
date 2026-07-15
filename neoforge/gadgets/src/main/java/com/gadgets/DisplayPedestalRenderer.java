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

/** Draws the displayed item floating above the base, centred over the whole group. */
public class DisplayPedestalRenderer implements BlockEntityRenderer<DisplayPedestalBlockEntity> {
    /** Degrees per tick for spin settings Off/Slow/Medium/Fast. */
    private static final float[] SPIN_SPEED = {0.0F, 1.5F, 4.0F, 8.0F};

    private final ItemRenderer itemRenderer;

    public DisplayPedestalRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(DisplayPedestalBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = be.getDisplayed();
        if (stack.isEmpty()) {
            return; // only the block that holds the item draws it
        }
        Level level = be.getLevel();
        double time = (level != null ? level.getGameTime() : 0L) + partialTick;

        // Centre the item over the whole connected group and grow it to match.
        double cx = 0.5, cz = 0.5;
        float sizeMul = 1.0F;
        if (level != null) {
            int[] g = DisplayPedestalBlock.groupBounds(level, be.getBlockPos());
            cx = (g[0] + g[2] + 1) / 2.0 - be.getBlockPos().getX();
            cz = (g[1] + g[3] + 1) / 2.0 - be.getBlockPos().getZ();
            sizeMul = 1.0F + 0.6F * (Math.max(g[2] - g[0], g[3] - g[1]));
        }

        int spin = Math.min(Math.max(be.getSpin(), 0), SPIN_SPEED.length - 1);
        float bob = (float) (Math.sin(time * 0.08) * 0.04);
        float angle = spin == 0 ? 0.0F : (float) ((time * SPIN_SPEED[spin]) % 360.0);
        float scale = (0.45F + be.getScale() * 0.25F) * sizeMul;

        pose.pushPose();
        pose.translate(cx, 1.15 + bob, cz);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.scale(scale, scale, scale);
        itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, level, 0);
        pose.popPose();
    }
}
