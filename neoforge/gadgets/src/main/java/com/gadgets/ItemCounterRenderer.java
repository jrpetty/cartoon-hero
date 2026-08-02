package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The counter's display face, laid out like the Fluid Monitor's: the counter's
 * name across the top, its headline figure large in the middle, and the unit
 * underneath — with a fill bar in pulse mode, where there is a fraction worth
 * drawing.
 *
 * <p>In the all-at-once mode the headline gives way to the three rates listed
 * together, which is what the counter's own screen and any hub monitor pointed
 * at it will be showing too.
 */
public class ItemCounterRenderer implements BlockEntityRenderer<ItemCounterBlockEntity> {
    private static final int VALUE_COLOR = 0xFFC864;
    private static final int LABEL_COLOR = 0xC08840;
    private static final int NAME_COLOR = 0xE6AA3C;
    private static final int LOW_COLOR = 0xFF6B6B;
    private static final int TRACK_COLOR = 0xFF2E2A25;

    /** Width of the model's screen opening, in block pixels. */
    private static final int GLASS_PX = 14;
    /** The glass sits two pixels in from the mounting face; the bezel is a third. */
    private static final float GLASS_Z = -0.5F + 2.0F / 16.0F;
    /** Clear of the glass by enough not to z-fight, still inside the recess. */
    private static final float TEXT_Z = GLASS_Z + 0.02F;
    private static final float SCALE = 0.012F;
    /** The pulse readout's own mode index in {@link ItemCounterBlockEntity#MODE_LABELS}. */
    private static final int PULSE_MODE = 3;

    private final FaceText face;

    public ItemCounterRenderer(BlockEntityRendererProvider.Context ctx) {
        this.face = new FaceText(ctx.getFont(), GLASS_PX, SCALE);
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
        pose.translate(0.0, 0.0, TEXT_Z);
        pose.scale(SCALE, -SCALE, SCALE);

        String name = be.getCustomName().isEmpty() ? "counter" : be.getCustomName();
        boolean stalled = be.isStalled();
        int colour = stalled ? LOW_COLOR : VALUE_COLOR;
        face.line(pose, buffers, name, -30.0F, NAME_COLOR);

        if (be.showsEverything()) {
            String[] rows = be.faceRows();
            for (int i = 0; i < rows.length; i++) {
                face.line(pose, buffers, rows[i], -16.0F + i * 14.0F, 1.35F, colour);
            }
        } else {
            face.line(pose, buffers, be.faceValue(), -18.0F, 2.0F, colour);
            face.line(pose, buffers, stalled ? "STALLED" : be.faceLabel(), 4.0F,
                    stalled ? LOW_COLOR : LABEL_COLOR);
            if (be.getDisplayMode() == PULSE_MODE && !stalled) {
                // Pulse mode has a real fraction: how close the next pulse is.
                int percent = be.getThreshold() <= 0 ? 0
                        : (int) (be.getCount() * 100L / be.getThreshold());
                face.bar(pose, buffers, 18.0F, face.room() - 12.0F, percent, VALUE_COLOR, TRACK_COLOR);
            }
        }
        pose.popPose();
    }
}
