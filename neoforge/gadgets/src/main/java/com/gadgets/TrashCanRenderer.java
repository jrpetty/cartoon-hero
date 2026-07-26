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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Shows the can's filter item slowly spinning above the lid, so the setting is visible at a glance. */
public class TrashCanRenderer implements BlockEntityRenderer<TrashCanBlockEntity> {
    private final ItemRenderer itemRenderer;

    public TrashCanRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(TrashCanBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (be.getFilter() == Items.AIR) {
            return;
        }
        Level level = be.getLevel();
        double time = (level != null ? level.getGameTime() : 0L) + partialTick;

        pose.pushPose();
        pose.translate(0.5, 1.25 + Math.sin(time * 0.06) * 0.03, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees((float) ((time * 1.2) % 360.0)));
        pose.scale(0.35F, 0.35F, 0.35F);
        itemRenderer.renderStatic(new ItemStack(be.getFilter()), ItemDisplayContext.GROUND, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        pose.popPose();
    }
}
