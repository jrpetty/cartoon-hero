package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Draws the stock readout on the monitor's display face: the current count in
 * amber (red when below the alert level) with the tracked item's name beneath.
 */
public class StockMonitorRenderer implements BlockEntityRenderer<StockMonitorBlockEntity> {
    private static final int OK_COLOR = 0xFFC864;
    private static final int LOW_COLOR = 0xFF5555;
    private static final int LABEL_COLOR = 0xC0B090;

    private final Font font;

    public StockMonitorRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(StockMonitorBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof StockMonitorBlock)) {
            return;
        }
        Direction back = state.getValue(StockMonitorBlock.FACING).getOpposite();

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (back.getAxis().isHorizontal()) {
            pose.mulPose(Axis.YP.rotationDegrees(-back.toYRot()));
        } else {
            pose.mulPose(Axis.XP.rotationDegrees(back == Direction.UP ? -90.0F : 90.0F));
        }
        pose.translate(0.0, 0.0, -0.368);
        pose.scale(0.018F, -0.018F, 0.018F);

        String value = be.faceValue();
        String label = be.faceLabel();
        Matrix4f matrix = pose.last().pose();
        int bright = LightTexture.FULL_BRIGHT;
        int valueColor = be.isLow() ? LOW_COLOR : OK_COLOR;
        font.drawInBatch(value, -font.width(value) / 2.0F, -9.0F, valueColor, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, bright);
        font.drawInBatch(label, -font.width(label) / 2.0F, 2.0F, LABEL_COLOR, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, bright);
        pose.popPose();
    }
}
