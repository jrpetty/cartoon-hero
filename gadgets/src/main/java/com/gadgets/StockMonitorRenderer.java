package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Draws the stock readout on the monitor's display face: the current count in
 * amber (red when below the alert level) with the tracked item's name beneath.
 */
public class StockMonitorRenderer implements BlockEntityRenderer<StockMonitorBlockEntity> {
    private static final int OK_COLOR = 0xFFC864;
    private static final int LOW_COLOR = 0xFF5555;
    private static final int LABEL_COLOR = 0xC0B090;

    private final TextRenderer textRenderer;

    public StockMonitorRenderer(BlockEntityRendererFactory.Context ctx) {
        this.textRenderer = ctx.getTextRenderer();
    }

    @Override
    public void render(StockMonitorBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = be.getCachedState();
        if (!(state.getBlock() instanceof StockMonitorBlock)) {
            return;
        }
        Direction back = state.get(StockMonitorBlock.FACING).getOpposite();

        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);
        if (back.getAxis().isHorizontal()) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-back.asRotation()));
        } else {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(back == Direction.UP ? -90.0F : 90.0F));
        }
        matrices.translate(0.0, 0.0, -0.368);
        matrices.scale(0.018F, -0.018F, 0.018F);

        String value = be.faceValue();
        String label = be.faceLabel();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int bright = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int valueColor = be.isLow() ? LOW_COLOR : OK_COLOR;
        textRenderer.draw(value, -textRenderer.getWidth(value) / 2.0F, -9.0F, valueColor, false,
                matrix, vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, bright);
        textRenderer.draw(label, -textRenderer.getWidth(label) / 2.0F, 2.0F, LABEL_COLOR, false,
                matrix, vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, bright);
        matrices.pop();
    }
}
