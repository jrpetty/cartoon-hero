package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The monitor's face: the source gadget's name across the top, its headline
 * number large in the middle, and its supporting figures underneath.
 *
 * <p>Every gadget is laid out the way the Fluid Monitor is, so a wall of these
 * reads as one instrument panel rather than three different ones. A counter
 * follows whatever readout the counter itself is set to — including its
 * all-at-once mode — so the remote screen and the block always agree.
 */
public class CommandHubMonitorRenderer implements BlockEntityRenderer<CommandHubMonitorBlockEntity> {
    private static final int NAME = 0xE6AA3C;
    private static final int VALUE = 0xFFC864;
    private static final int SUB = 0xC08840;
    private static final int LOW = 0xFF5555;
    private static final int OK = 0x7CE87C;
    private static final int IDLE = 0x8E96A4;

    /** Depth of the model's glass, from the block centre along FACING. */
    private static final float GLASS_Z = 0.5F - 12.0F / 16.0F;
    /** Width of the model's screen opening, in block pixels. */
    private static final int GLASS_PX = 14;
    private static final float SCALE = 0.011F;
    /** The headline, relative to the base size — big enough to read at range. */
    private static final float BIG = 2.35F;
    /** The rows of the all-at-once counter readout. */
    private static final float MEDIUM = 1.45F;

    private final FaceText face;

    public CommandHubMonitorRenderer(BlockEntityRendererProvider.Context ctx) {
        this.face = new FaceText(ctx.getFont(), GLASS_PX, SCALE);
    }

    @Override
    public void render(CommandHubMonitorBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CommandHubMonitorBlock)) {
            return;
        }
        Direction front = state.getValue(HorizontalDirectionalBlock.FACING);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-front.toYRot()));
        // The glass is on the far side of the block from the direction it faces,
        // because the panel hangs off the wall behind it rather than off the
        // face nearest you. The readout goes a hair proud of that glass.
        pose.translate(0.0, 0.0, GLASS_Z + 0.005);
        pose.scale(SCALE, -SCALE, SCALE);

        if (!be.isHubLinked()) {
            face.line(pose, buffers, "NO HUB", 0.0F, IDLE);
        } else if (!be.hasSource()) {
            face.line(pose, buffers, "UNASSIGNED", -6.0F, IDLE);
            face.line(pose, buffers, "right-click", 6.0F, IDLE);
        } else if (!be.isOnline()) {
            face.line(pose, buffers, be.sourceLabel(), -14.0F, NAME);
            face.line(pose, buffers, "OFFLINE", 0.0F, IDLE);
        } else if (be.sourceType() == CommandHubBlockEntity.TYPE_COUNTER) {
            counter(pose, buffers, be);
        } else if (be.sourceType() == CommandHubBlockEntity.TYPE_MONITOR) {
            // The Stock Monitor: the count headline, then the alert state.
            boolean low = be.c() != 0;
            headline(pose, buffers, be.sourceLabel(), ItemCounterBlockEntity.compact(be.a()),
                    low ? "LOW  < " + be.b() : be.d() + (be.d() == 1 ? " type" : " types"),
                    low ? LOW : VALUE, low ? LOW : OK);
        } else {
            // Fluid gauge: fill percentage over the raw amounts.
            boolean low = be.c() != 0;
            headline(pose, buffers, be.sourceLabel(), be.d() + "%",
                    ItemCounterBlockEntity.compact(be.a()) + " / " + ItemCounterBlockEntity.compact(be.b()),
                    low ? LOW : VALUE, low ? LOW : OK);
        }
        pose.popPose();
    }

    /**
     * An Item Counter, shown the way that counter's own face is showing it. A
     * stalled counter overrides the choice: whatever you asked to watch, the
     * thing worth knowing is that nothing is coming through any more.
     */
    private void counter(PoseStack pose, MultiBufferSource buffers, CommandHubMonitorBlockEntity be) {
        if (be.d() != 0) {
            headline(pose, buffers, be.sourceLabel(),
                    ItemCounterBlockEntity.compact(be.a()) + "/m", "STALLED", LOW, LOW);
            return;
        }
        if (be.sourceMode() == ItemCounterBlockEntity.MODE_ALL) {
            // Every rate at once: three even rows under the name, rather than a
            // headline with the rest crammed onto one line beneath it.
            face.line(pose, buffers, be.sourceLabel(), -22.0F, NAME);
            face.line(pose, buffers, ItemCounterBlockEntity.compact(be.a()) + "/m", -10.0F, MEDIUM, VALUE);
            face.line(pose, buffers, ItemCounterBlockEntity.compact(be.b()) + "/h", 4.0F, MEDIUM, VALUE);
            face.line(pose, buffers, ItemCounterBlockEntity.compact(be.c()) + " tot", 18.0F, MEDIUM, SUB);
            return;
        }
        String value = switch (be.sourceMode()) {
            case 1 -> ItemCounterBlockEntity.compact(be.b());
            case 2 -> ItemCounterBlockEntity.compact(be.c());
            case 3 -> be.pulseCount() + "/" + be.pulseSize();
            default -> ItemCounterBlockEntity.compact(be.a());
        };
        String unit = ItemCounterBlockEntity.MODE_LABELS[
                Math.floorMod(be.sourceMode(), ItemCounterBlockEntity.MODE_LABELS.length)];
        headline(pose, buffers, be.sourceLabel(), value, unit, VALUE, SUB);
    }

    /**
     * The house layout: name, one big number, one quiet line under it. The
     * footer sits clear of the headline's full height, which it did not before
     * — the bottoms of the big glyphs ran into it.
     */
    private void headline(PoseStack pose, MultiBufferSource buffers, String name,
                          String value, String footer, int valueColour, int footerColour) {
        face.line(pose, buffers, name, -20.0F, NAME);
        face.line(pose, buffers, value, -4.0F, BIG, valueColour);
        face.line(pose, buffers, footer, 20.0F, footerColour);
    }
}
