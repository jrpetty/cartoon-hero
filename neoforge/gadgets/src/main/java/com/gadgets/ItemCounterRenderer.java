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
 * Draws the live readout on the counter's display face (opposite the sensor):
 * a big value line and a small unit label, full-bright like an LED panel.
 */
public class ItemCounterRenderer implements BlockEntityRenderer<ItemCounterBlockEntity> {
    private static final int VALUE_COLOR = 0xFFC864;
    private static final int LABEL_COLOR = 0xC08840;

    private final Font font;

    public ItemCounterRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(ItemCounterBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ItemCounterBlock)) {
            return;
        }
        Direction back = state.getValue(ItemCounterBlock.FACING).getOpposite();

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (back.getAxis().isHorizontal()) {
            pose.mulPose(Axis.YP.rotationDegrees(-back.toYRot()));
        } else {
            // Screen on top or bottom: lay the text flat, readable from the south.
            pose.mulPose(Axis.XP.rotationDegrees(back == Direction.UP ? -90.0F : 90.0F));
        }
        pose.translate(0.0, 0.0, 0.505);
        pose.scale(0.018F, -0.018F, 0.018F);

        String value = be.faceValue();
        String label = be.faceLabel();
        Matrix4f matrix = pose.last().pose();
        int fullBright = LightTexture.FULL_BRIGHT;
        font.drawInBatch(value, -font.width(value) / 2.0F, -9.0F, VALUE_COLOR, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, fullBright);
        font.drawInBatch(label, -font.width(label) / 2.0F, 2.0F, LABEL_COLOR, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, fullBright);
        pose.popPose();
    }
}
