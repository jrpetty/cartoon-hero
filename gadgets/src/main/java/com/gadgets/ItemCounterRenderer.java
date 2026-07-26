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
 * Draws the live readout on the counter's display face (opposite the sensor):
 * a big value line and a small unit label, full-bright like an LED panel.
 */
public class ItemCounterRenderer implements BlockEntityRenderer<ItemCounterBlockEntity> {
    private static final int VALUE_COLOR = 0xFFC864;
    private static final int LABEL_COLOR = 0xC08840;

    private final TextRenderer textRenderer;

    public ItemCounterRenderer(BlockEntityRendererFactory.Context ctx) {
        this.textRenderer = ctx.getTextRenderer();
    }

    @Override
    public void render(ItemCounterBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = be.getCachedState();
        if (!(state.getBlock() instanceof ItemCounterBlock)) {
            return;
        }
        Direction back = state.get(ItemCounterBlock.FACING).getOpposite();

        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);
        if (back.getAxis().isHorizontal()) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-back.asRotation()));
        } else {
            // Screen on top or bottom: lay the text flat, readable from the south.
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(back == Direction.UP ? -90.0F : 90.0F));
        }
        matrices.translate(0.0, 0.0, -0.368);
        matrices.scale(0.018F, -0.018F, 0.018F);

        String value = be.faceValue();
        String label = be.faceLabel();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int fullBright = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        textRenderer.draw(value, -textRenderer.getWidth(value) / 2.0F, -9.0F, VALUE_COLOR, false,
                matrix, vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, fullBright);
        textRenderer.draw(label, -textRenderer.getWidth(label) / 2.0F, 2.0F, LABEL_COLOR, false,
                matrix, vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, fullBright);
        matrices.pop();
    }
}
