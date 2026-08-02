package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * The monitor's face: the source gadget's name across the top, its headline
 * number large in the middle, and the same supporting figures the gadget shows
 * on its own display underneath.
 */
public class CommandHubMonitorRenderer implements BlockEntityRenderer<CommandHubMonitorBlockEntity> {
    private static final int NAME = 0xE6AA3C;
    private static final int VALUE = 0xFFC864;
    private static final int SUB = 0xC08840;
    private static final int LOW = 0xFF5555;
    private static final int OK = 0x7CE87C;
    private static final int IDLE = 0x8E96A4;

    /** Text scale for the name and footer rows. */
    private static final float SMALL = 0.011F;
    /** Text scale for the headline number — deliberately large to read at range. */
    private static final float BIG = 0.026F;

    /** Depth of the model's glass, from the block centre along FACING. */
    private static final float GLASS_Z = 0.5F - 12.0F / 16.0F;
    /** Width of the model's screen opening, in block pixels. */
    private static final int GLASS_PX = 14;

    private final Font font;

    public CommandHubMonitorRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
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
        // The glass sits twelve pixels back from the front of the block space,
        // because the panel hangs off the wall behind it rather than off the
        // face nearest you. The readout goes a hair proud of that glass.
        pose.translate(0.0, 0.0, GLASS_Z + 0.005);

        if (!be.isHubLinked()) {
            line(pose, buffers, "NO HUB", 0.0F, SMALL, IDLE);
        } else if (!be.hasSource()) {
            line(pose, buffers, "UNASSIGNED", -6.0F, SMALL, IDLE);
            line(pose, buffers, "right-click", 6.0F, SMALL, IDLE);
        } else if (!be.isOnline()) {
            line(pose, buffers, trim(be.sourceLabel()), -14.0F, SMALL, NAME);
            line(pose, buffers, "OFFLINE", 0.0F, SMALL, IDLE);
        } else if (be.sourceType() == CommandHubBlockEntity.TYPE_COUNTER) {
            // Mirrors the Item Counter's own face: rate headline, then /h and
            // total — or, once it goes quiet, a STALLED flag in place of both.
            boolean stalled = be.d() != 0;
            line(pose, buffers, trim(be.sourceLabel()), -18.0F, SMALL, NAME);
            line(pose, buffers, ItemCounterBlockEntity.compact(be.a()) + "/m", -4.0F, BIG, stalled ? LOW : VALUE);
            line(pose, buffers, stalled ? "STALLED" : ItemCounterBlockEntity.compact(be.b()) + "/h  ·  "
                    + ItemCounterBlockEntity.compact(be.c()) + " tot", 16.0F, SMALL, stalled ? LOW : SUB);
        } else if (be.sourceType() == CommandHubBlockEntity.TYPE_MONITOR) {
            // Mirrors the Stock Monitor: the count headline, then the alert state.
            boolean low = be.c() != 0;
            line(pose, buffers, trim(be.sourceLabel()), -18.0F, SMALL, NAME);
            line(pose, buffers, ItemCounterBlockEntity.compact(be.a()), -4.0F, BIG, low ? LOW : VALUE);
            line(pose, buffers, low ? "LOW  <" + be.b() : be.d() + " types", 16.0F, SMALL, low ? LOW : OK);
        } else {
            // Fluid and energy gauges: fill percentage over the raw amounts.
            boolean low = be.c() != 0;
            line(pose, buffers, trim(be.sourceLabel()), -18.0F, SMALL, NAME);
            line(pose, buffers, be.d() + "%", -4.0F, BIG, low ? LOW : VALUE);
            line(pose, buffers, ItemCounterBlockEntity.compact(be.a()) + " / "
                    + ItemCounterBlockEntity.compact(be.b()), 16.0F, SMALL, low ? LOW : OK);
        }
        pose.popPose();
    }

    /**
     * Draws one centred row. {@code y} is always expressed on the small-text
     * grid, so rows keep their spacing whatever scale they are drawn at: a pose
     * scaled by {@code scale} turns a font offset of {@code f} into {@code
     * f * scale} of face, so the offset needed is {@code y * SMALL / scale}.
     */
    private void line(PoseStack pose, MultiBufferSource buffers, String text, float y, float scale, int colour) {
        pose.pushPose();
        pose.scale(scale, -scale, scale);
        String shown = fit(text, scale);
        Matrix4f matrix = pose.last().pose();
        font.drawInBatch(shown, -font.width(shown) / 2.0F, y * (SMALL / scale), colour, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * Shortens a row that would run past the glass.
     *
     * <p>Measured in pixels at the scale the row is actually drawn at, not in
     * characters: a footer of narrow digits fits where the same count of wide
     * letters does not, and the headline is drawn more than twice the size of
     * everything else. A fixed character limit got both of those wrong and let
     * text spill over the bezel and out into the air beside the panel.
     */
    private String fit(String text, float scale) {
        float room = GLASS_PX / 16.0F / scale;
        if (font.width(text) <= room) {
            return text;
        }
        String out = text;
        while (!out.isEmpty() && font.width(out + "…") > room) {
            out = out.substring(0, out.length() - 1);
        }
        return out.isEmpty() ? "" : out + "…";
    }

    private static String trim(String s) {
        return s.length() > 18 ? s.substring(0, 17) + "…" : s;
    }
}
