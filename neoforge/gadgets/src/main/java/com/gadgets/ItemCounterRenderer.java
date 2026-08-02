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
 * Draws the live readout on the counter's display face (opposite the sensor),
 * full-bright like an LED panel: either one headline with its unit label, or —
 * in the "all" mode — every rate listed at once under the counter's name.
 */
public class ItemCounterRenderer implements BlockEntityRenderer<ItemCounterBlockEntity> {
    private static final int VALUE_COLOR = 0xFFC864;
    private static final int LABEL_COLOR = 0xC08840;

    private static final float SCALE = 0.018F;
    /** Usable width of the display face, in block pixels — a pixel of frame each side. */
    private static final int FACE_PX = 14;
    /** Text units that fit across the face at {@link #SCALE}. */
    private static final float ROOM = FACE_PX / 16.0F / SCALE;

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
        pose.translate(0.0, 0.0, -0.368);
        pose.scale(SCALE, -SCALE, SCALE);

        Matrix4f matrix = pose.last().pose();
        if (be.showsEverything()) {
            // Every statistic at once: the label heads the panel and the three
            // rates stack under it, all on the small grid so they fit together.
            String name = be.getCustomName().isEmpty() ? "counter" : be.getCustomName();
            line(matrix, buffers, name, -18.0F, LABEL_COLOR);
            String[] rows = be.faceRows();
            for (int i = 0; i < rows.length; i++) {
                line(matrix, buffers, rows[i], -6.0F + i * 9.0F, VALUE_COLOR);
            }
        } else {
            line(matrix, buffers, be.faceValue(), -9.0F, VALUE_COLOR);
            line(matrix, buffers, be.faceLabel(), 2.0F, LABEL_COLOR);
        }
        pose.popPose();
    }

    /** One centred row, shortened if it would run past the face. */
    private void line(Matrix4f matrix, MultiBufferSource buffers, String text, float y, int colour) {
        String shown = fit(text);
        font.drawInBatch(shown, -font.width(shown) / 2.0F, y, colour, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    /**
     * Shortens a row that would run past the display face. A counter given a
     * long name used to draw it at full width straight off the block and out
     * into the air on both sides.
     */
    private String fit(String text) {
        if (font.width(text) <= ROOM) {
            return text;
        }
        String out = text;
        while (!out.isEmpty() && font.width(out + "…") > ROOM) {
            out = out.substring(0, out.length() - 1);
        }
        return out.isEmpty() ? "" : out + "…";
    }
}
