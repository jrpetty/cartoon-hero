package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/** Renders the drain's disguise block in place of the grate when one is set. */
public class DrainRenderer implements BlockEntityRenderer<DrainBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public DrainRenderer(BlockEntityRendererProvider.Context ctx) {
        this.blockRenderer = ctx.getBlockRenderDispatcher();
    }

    @Override
    public void render(DrainBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState disguise = be.getDisguiseState();
        if (disguise == null) {
            return;
        }
        pose.pushPose();
        blockRenderer.renderSingleBlock(disguise, pose, buffers, light, overlay);
        pose.popPose();
    }
}
