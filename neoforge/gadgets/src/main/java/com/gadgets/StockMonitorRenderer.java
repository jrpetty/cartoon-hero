package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The stock readout, laid out like the Fluid Monitor's: what is being tracked
 * across the top, the count large in the middle, and the alert state under it,
 * over a bar reading the stock against the level it is allowed to fall to.
 *
 * <p>That bar is why the alert level is worth having on the face at all. A bare
 * number tells you how much is there; the bar tells you how close it is to
 * running out, which is the question a stock monitor exists to answer.
 */
public class StockMonitorRenderer implements BlockEntityRenderer<StockMonitorBlockEntity> {
    private static final int OK_COLOR = 0xFFC864;
    private static final int LOW_COLOR = 0xFF6B6B;
    private static final int LABEL_COLOR = 0xC0B090;
    private static final int NAME_COLOR = 0xE6AA3C;
    private static final int TRACK_COLOR = 0xFF2E2A25;

    /** Width of the model's screen opening, in block pixels. */
    private static final int GLASS_PX = 14;
    /** The glass sits two pixels in from the mounting face; the bezel is a third. */
    private static final float GLASS_Z = -0.5F + 2.0F / 16.0F;
    /** Clear of the glass by enough not to z-fight, still inside the recess. */
    private static final float TEXT_Z = GLASS_Z + 0.02F;
    private static final float SCALE = 0.012F;

    private final FaceText face;

    public StockMonitorRenderer(BlockEntityRendererProvider.Context ctx) {
        this.face = new FaceText(ctx.getFont(), GLASS_PX, SCALE);
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
        pose.translate(0.0, 0.0, TEXT_Z);
        pose.scale(SCALE, -SCALE, SCALE);

        boolean low = be.isLow();
        int colour = low ? LOW_COLOR : OK_COLOR;
        face.line(pose, buffers, be.faceLabel(), -30.0F, NAME_COLOR);
        face.line(pose, buffers, be.faceValue(), -18.0F, 2.0F, colour);
        face.line(pose, buffers, low ? "LOW  < " + be.getThreshold() : "alert " + be.getThreshold(),
                4.0F, low ? LOW_COLOR : LABEL_COLOR);

        // Full bar means comfortably stocked; empty means at the alert line. An
        // alert of zero has nothing to measure against, so the bar sits full.
        int threshold = be.getThreshold();
        int percent = threshold <= 0 ? 100 : (int) Math.min(100L, be.getCount() * 100L / threshold);
        face.bar(pose, buffers, 18.0F, face.room() - 12.0F, percent, colour, TRACK_COLOR);
        pose.popPose();
    }
}
