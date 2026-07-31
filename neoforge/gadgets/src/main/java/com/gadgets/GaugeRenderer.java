package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;

/**
 * The face of a fill gauge, read like a television: the percentage large across
 * the top, the exact amount under it, a fill bar, and the gauge's name at the
 * bottom. Shared by the Fluid and Energy Monitors, which differ only in what
 * they read.
 *
 * <p>Everything is drawn onto the recessed glass of the model and clipped to
 * that opening, so a long name is shortened rather than spilling over the bezel
 * and hanging in the air beside the block.
 */
public class GaugeRenderer<T extends BlockEntity & HubGauge> implements BlockEntityRenderer<T> {
    private static final int OK_COLOR = 0xFFC864;
    private static final int LOW_COLOR = 0xFF6B6B;
    private static final int LABEL_COLOR = 0xBCA98C;
    private static final int IDLE_COLOR = 0x8A8F96;
    /** The unfilled part of the bar: dark, but lighter than the glass. */
    private static final int TRACK_COLOR = 0xFF2E2A25;

    /** Width and height of the screen opening in the model, in block pixels. */
    private static final int GLASS_PX = 14;
    /**
     * Where the glass sits along the panel's own axis, measured from the block
     * centre. The model's body is 2px deep and the bezel stands 1px proud of it,
     * so the readout goes a hair in front of the glass and stays inside the
     * recess.
     */
    private static final float GLASS_Z = -0.5F + 2.0F / 16.0F;
    private static final float TEXT_Z = GLASS_Z + 0.007F;

    private static final float SCALE = 0.01F;
    /** Text units that fit across the glass at {@link #SCALE}. */
    private static final float ROOM = GLASS_PX / 16.0F / SCALE;
    private static final float BAR_W = ROOM - 12.0F;

    private final Font font;

    public GaugeRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(T be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return;
        }
        Direction back = state.getValue(BlockStateProperties.FACING).getOpposite();

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (back.getAxis().isHorizontal()) {
            pose.mulPose(Axis.YP.rotationDegrees(-back.toYRot()));
        } else {
            pose.mulPose(Axis.XP.rotationDegrees(back == Direction.UP ? -90.0F : 90.0F));
        }
        pose.translate(0.0, 0.0, TEXT_Z);
        pose.scale(SCALE, -SCALE, SCALE);

        int bright = LightTexture.FULL_BRIGHT;
        if (!be.hasSource()) {
            // Nothing readable in front of the panel. Say so, rather than sitting
            // at a confident-looking 0% that reads as an empty tank.
            String what = be.gaugeType() == CommandHubBlockEntity.TYPE_FLUID ? "NO TANK" : "NO CELL";
            line(pose, buffers, what, -8.0F, IDLE_COLOR, bright);
            line(pose, buffers, "nothing to read", 6.0F, IDLE_COLOR, bright);
            pose.popPose();
            return;
        }

        int colour = be.isLow() ? LOW_COLOR : OK_COLOR;

        // The percentage is what you read from across the room, so it gets its
        // own scale rather than being the first of four equal lines.
        pose.pushPose();
        pose.scale(2.0F, 2.0F, 1.0F);
        line(pose, buffers, be.faceValue(), -17.0F, colour, bright);
        pose.popPose();

        line(pose, buffers, fit(be.amountText()), -10.0F, LABEL_COLOR, bright);
        bar(pose, buffers, 6.0F, be.percent(), colour, bright);
        line(pose, buffers, fit(be.faceLabel()), 22.0F, LABEL_COLOR, bright);
        pose.popPose();
    }

    /** One centred row at the current scale. */
    private void line(PoseStack pose, MultiBufferSource buffers, String text, float y, int colour, int light) {
        font.drawInBatch(text, -font.width(text) / 2.0F, y, colour, false,
                pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
    }

    /**
     * The fill bar: a dark track with the filled part drawn over it.
     *
     * <p>Both are solid rectangles, which a block entity renderer gets most
     * safely out of the font itself — {@link Font#drawInBatch}'s background
     * colour fills a rectangle behind the string, and a string of spaces is that
     * rectangle with no glyphs in it. Squashing x to a quarter makes one space
     * exactly one unit wide, so the fill lands on the percentage asked for
     * rather than the nearest four.
     */
    private void bar(PoseStack pose, MultiBufferSource buffers, float y, int percent, int colour, int light) {
        pose.pushPose();
        pose.scale(0.25F, 1.0F, 1.0F);
        Matrix4f matrix = pose.last().pose();
        int width = (int) BAR_W;
        rect(matrix, buffers, -BAR_W / 2.0F, y, width, TRACK_COLOR, light);
        int filled = Math.round(width * Mth.clamp(percent, 0, 100) / 100.0F);
        if (filled > 0) {
            rect(matrix, buffers, -BAR_W / 2.0F, y, filled, 0xFF000000 | colour, light);
        }
        pose.popPose();
    }

    private void rect(Matrix4f matrix, MultiBufferSource buffers, float x, float y, int width, int argb, int light) {
        font.drawInBatch(" ".repeat(width), x * 4.0F, y, 0, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, argb, light);
    }

    /** Shortens a line that would run past the glass. */
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
