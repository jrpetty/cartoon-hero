package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The face of a fill gauge, read like a television: the percentage large across
 * the top, the exact amount under it, a fill bar, and the gauge's name at the
 * bottom.
 *
 * <p>This is the layout the Item Counter and Stock Monitor follow too — see
 * {@link FaceText}, which owns the drawing so all three stay in step.
 */
public class GaugeRenderer<T extends BlockEntity & HubGauge> implements BlockEntityRenderer<T> {
    private static final int OK_COLOR = 0xFFC864;
    private static final int LOW_COLOR = 0xFF6B6B;
    private static final int LABEL_COLOR = 0xBCA98C;
    private static final int IDLE_COLOR = 0x8A8F96;
    /** The unfilled part of the bar: dark, but lighter than the glass. */
    private static final int TRACK_COLOR = 0xFF2E2A25;

    /** Width of the model's screen opening, in block pixels. */
    private static final int GLASS_PX = 14;
    /**
     * Where the glass sits along the panel's own axis, measured from the block
     * centre. The model's body is 2px deep and the bezel stands 1px proud of it.
     */
    private static final float GLASS_Z = -0.5F + 2.0F / 16.0F;
    /**
     * How far the readout floats in front of the glass: enough that the two do
     * not z-fight and strobe at range, while staying inside the recess.
     */
    private static final float TEXT_Z = GLASS_Z + 0.02F;
    private static final float SCALE = 0.01F;

    private final FaceText face;

    public GaugeRenderer(BlockEntityRendererProvider.Context ctx) {
        this.face = new FaceText(ctx.getFont(), GLASS_PX, SCALE);
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

        if (!be.hasSource()) {
            // Nothing readable in front of the panel. Say so, rather than sitting
            // at a confident-looking 0% that reads as an empty tank.
            face.line(pose, buffers, "NO TANK", -8.0F, IDLE_COLOR);
            face.line(pose, buffers, "nothing to read", 6.0F, IDLE_COLOR);
            pose.popPose();
            return;
        }

        int colour = be.isLow() ? LOW_COLOR : OK_COLOR;
        // The percentage is what you read from across the room, so it gets its
        // own size rather than being the first of four equal rows.
        face.line(pose, buffers, be.faceValue(), -34.0F, 2.0F, colour);
        face.line(pose, buffers, be.amountText(), -10.0F, LABEL_COLOR);
        face.bar(pose, buffers, 6.0F, face.room() - 12.0F, be.percent(), colour, TRACK_COLOR);
        face.line(pose, buffers, be.faceLabel(), 22.0F, LABEL_COLOR);
        pose.popPose();
    }
}
