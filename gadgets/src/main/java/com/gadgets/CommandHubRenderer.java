package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/** Live summary on the hub's screen: nodes linked, combined rate, low-stock alerts. */
public class CommandHubRenderer implements BlockEntityRenderer<CommandHubBlockEntity> {
    private static final int HEAD_COLOR = 0xE6AA3C;
    private static final int OK_COLOR = 0xFFC864;
    private static final int LOW_COLOR = 0xFF5555;

    private final TextRenderer textRenderer;

    public CommandHubRenderer(BlockEntityRendererFactory.Context ctx) {
        this.textRenderer = ctx.getTextRenderer();
    }

    @Override
    public void render(CommandHubBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = be.getCachedState();
        if (!(state.getBlock() instanceof CommandHubBlock)) {
            return;
        }
        Direction front = state.get(HorizontalFacingBlock.FACING);

        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-front.asRotation()));
        matrices.translate(0.0, 0.0, 0.505);
        matrices.scale(0.014F, -0.014F, 0.014F);

        int low = be.lowCount();
        String l1 = "◈ " + be.nodeCount() + " linked";
        String l2 = ItemCounterBlockEntity.compact(be.totalRateMin()) + " /min";
        String l3 = low == 0 ? "all stocked" : low + " low!";
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int bright = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        textRenderer.draw(l1, -textRenderer.getWidth(l1) / 2.0F, -14.0F, HEAD_COLOR, false,
                matrix, vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, bright);
        textRenderer.draw(l2, -textRenderer.getWidth(l2) / 2.0F, -3.0F, OK_COLOR, false,
                matrix, vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, bright);
        textRenderer.draw(l3, -textRenderer.getWidth(l3) / 2.0F, 8.0F, low == 0 ? OK_COLOR : LOW_COLOR, false,
                matrix, vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, bright);
        matrices.pop();
    }
}
