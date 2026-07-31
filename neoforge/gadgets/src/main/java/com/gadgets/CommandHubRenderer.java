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
 * Live summary on the hub's screen: nodes linked, combined rate, and the alert
 * count.
 *
 * <p>The console's glass is recessed behind a raised bezel, so the text is
 * placed against that inner surface rather than the block's outer face, and it
 * is sized and clipped to the {@value #GLASS_PX}-pixel opening — a line wider
 * than the glass would otherwise spill onto the bezel and out into the air.
 */
public class CommandHubRenderer implements BlockEntityRenderer<CommandHubBlockEntity> {
    private static final int HEAD_COLOR = 0xE6AA3C;
    private static final int OK_COLOR = 0xFFC864;
    private static final int LOW_COLOR = 0xFF7070;
    private static final int ALARM_HEAD = 0xFF9C9C;

    /** Width of the screen opening in the model, in block pixels. */
    private static final int GLASS_PX = 10;
    /** Depth of the glass below the block's front face, in block pixels. */
    private static final float GLASS_DEPTH_PX = 2.0F;
    private static final float SCALE = 0.0095F;
    /** Text units that fit across the glass at {@link #SCALE}. */
    private static final float ROOM = GLASS_PX / 16.0F / SCALE;

    private final Font font;

    public CommandHubRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(CommandHubBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CommandHubBlock)) {
            return;
        }
        Direction front = state.getValue(HorizontalDirectionalBlock.FACING);
        boolean alarmed = state.getValue(CommandHubBlock.ALARM);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-front.toYRot()));
        // Sit just proud of the recessed glass, still well inside the bezel.
        pose.translate(0.0, 0.0, 0.5 - GLASS_DEPTH_PX / 16.0F + 0.01F);
        pose.scale(SCALE, -SCALE, SCALE);

        int alarms = be.alarmCount();
        String l1 = be.nodeCount() + " linked";
        String l2 = ItemCounterBlockEntity.compact(be.totalRateMin()) + "/min";
        String l3 = alarms == 0 ? "all clear" : alarms + (alarms == 1 ? " alert" : " alerts");

        Matrix4f matrix = pose.last().pose();
        int bright = LightTexture.FULL_BRIGHT;
        line(matrix, buffers, l1, -16.0F, alarmed ? ALARM_HEAD : HEAD_COLOR, bright);
        line(matrix, buffers, l2, -1.0F, alarmed ? ALARM_HEAD : OK_COLOR, bright);
        line(matrix, buffers, l3, 14.0F, alarms == 0 ? OK_COLOR : LOW_COLOR, bright);
        pose.popPose();
    }

    /** Draws one centred row, shortened if it would run past the glass. */
    private void line(Matrix4f matrix, MultiBufferSource buffers, String text, float y, int colour, int light) {
        String shown = fit(text);
        font.drawInBatch(shown, -font.width(shown) / 2.0F, y, colour, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
    }

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
