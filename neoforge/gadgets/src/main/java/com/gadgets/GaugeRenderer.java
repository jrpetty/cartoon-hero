package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;

/**
 * The face of a fill gauge: percentage large, a fill bar under it, and the
 * label beneath that. Shared by the Fluid and Energy Monitors, which differ
 * only in what they read.
 */
public class GaugeRenderer<T extends BlockEntity & HubGauge> implements BlockEntityRenderer<T> {
    private static final int OK_COLOR = 0xFFC864;
    private static final int LOW_COLOR = 0xFF5555;
    private static final int LABEL_COLOR = 0xC0B090;
    private static final int BAR_BG = 0xFF3A322A;
    /** Cells in the fill bar; 20 gives 5% resolution. */
    private static final int BAR_CELLS = 20;

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
        pose.translate(0.0, 0.0, -0.368);
        pose.scale(0.018F, -0.018F, 0.018F);

        String value = be.faceValue();
        String label = be.faceLabel();
        int colour = be.isLow() ? LOW_COLOR : OK_COLOR;
        Matrix4f matrix = pose.last().pose();
        int bright = LightTexture.FULL_BRIGHT;

        font.drawInBatch(value, -font.width(value) / 2.0F, -13.0F, colour, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, bright);

        // A bar makes a glance enough; the number is for when you care exactly.
        // Built from '|' rather than block-drawing characters: those are not
        // guaranteed to be in the player's font, and a missing glyph would show
        // as a row of boxes. Every cell is the same width, so drawing the filled
        // part over the empty one from the same origin lines up exactly.
        if (be.hasSource()) {
            String track = "|".repeat(BAR_CELLS);
            int filled = Math.round(BAR_CELLS * Math.max(0, Math.min(100, be.percent())) / 100.0F);
            float barX = -font.width(track) / 2.0F;
            font.drawInBatch(track, barX, -1.0F, BAR_BG, false,
                    matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, bright);
            if (filled > 0) {
                font.drawInBatch("|".repeat(filled), barX, -1.0F, colour, false,
                        matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, bright);
            }
        }

        font.drawInBatch(label, -font.width(label) / 2.0F, 6.0F, LABEL_COLOR, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, bright);
        pose.popPose();
    }

}
