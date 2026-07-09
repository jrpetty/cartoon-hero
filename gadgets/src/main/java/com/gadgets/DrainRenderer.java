package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

/** Renders the drain's disguise block in place of the grate when one is set. */
public class DrainRenderer implements BlockEntityRenderer<DrainBlockEntity> {
    public DrainRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(DrainBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState disguise = be.getDisguiseState();
        if (disguise == null) {
            return;
        }
        matrices.push();
        MinecraftClient.getInstance().getBlockRenderManager()
                .renderBlockAsEntity(disguise, matrices, vertexConsumers, light, overlay);
        matrices.pop();
    }
}
