package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * The Grand Display's face.
 *
 * <p>Panels placed in a row, all facing the same way, form one screen: the panel
 * at the end of the run draws the whole board across every panel in it, and the
 * rest draw nothing. So one panel is a small board and five in a row are a wide
 * one, which is what putting them side by side looks like it should do.
 *
 * <p>Text never leaves the panels it belongs to. An earlier version sprayed a
 * fixed three blocks wide regardless, so two boards side by side drew straight
 * through each other.
 */
public class GrandDisplayRenderer implements BlockEntityRenderer<GrandDisplayBlockEntity> {
    private static final int NAME = 0xE6AA3C;
    private static final int VALUE = 0xFFC864;
    private static final int LOW = 0xFF5555;
    private static final int OK = 0x7CE87C;
    private static final int IDLE = 0x8E96A4;
    private static final int HEAD = 0xC08840;

    /** Text units per block; rows are then positioned in readable numbers. */
    private static final float UNITS = 90.0F;
    private static final float PAD = 5.0F;

    private final Font font;

    public GrandDisplayRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(GrandDisplayBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof GrandDisplayBlock) || be.getLevel() == null) {
            return;
        }
        Direction front = state.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos pos = be.getBlockPos();
        Direction along = front.getClockWise();

        // Only the panel with no neighbour behind it along the run draws.
        if (isPanel(be.getLevel(), pos.relative(along.getOpposite()), front)) {
            return;
        }
        int extra = 0;
        while (extra < GrandDisplayBlockEntity.MAX_RUN - 1
                && isPanel(be.getLevel(), pos.relative(along, extra + 1), front)) {
            extra++;
        }
        int width = 1 + extra;

        pose.pushPose();
        // Shift to the middle of the whole run before rotating — done in world
        // space, so it holds whichever way `along` happens to point.
        pose.translate(0.5 + along.getStepX() * (extra / 2.0), 0.5, 0.5 + along.getStepZ() * (extra / 2.0));
        pose.mulPose(Axis.YP.rotationDegrees(-front.toYRot()));
        pose.translate(0.0, 0.0, 0.505);
        float scale = 1.0F / UNITS;
        pose.scale(scale, -scale, scale);
        Matrix4f matrix = pose.last().pose();

        float half = width * UNITS / 2.0F;
        float leftEdge = -half + PAD;
        float rightEdge = half - PAD;
        float room = rightEdge - leftEdge;

        if (!be.isHubLinked()) {
            draw(matrix, buffers, fit("NO HUB", room, 2.0F), leftEdge, -10.0F, IDLE, 2.0F);
            draw(matrix, buffers, fit("link it with the Monitor Wand", room, 1.0F), leftEdge, 6.0F, IDLE, 1.0F);
            pose.popPose();
            return;
        }
        if (be.getRows().isEmpty()) {
            draw(matrix, buffers, fit("Hub has nothing linked", room, 1.4F), leftEdge, -4.0F, IDLE, 1.4F);
            pose.popPose();
            return;
        }

        int capacity = be.rowCapacity();
        boolean large = be.isLarge();
        float rowScale = large ? 2.2F : 1.25F;
        float rowH = 11.0F * rowScale;
        float top = -(capacity * rowH) / 2.0F;

        int row = 0;
        for (CommandHubBlockEntity.Node n : be.getRows()) {
            float y = top + row * rowH;
            boolean alarmed = n.alarmed();
            String name = n.label.isBlank()
                    ? CommandHubBlockEntity.kindOf(n.type)
                    : n.label;
            String value = reading(n);

            int nameColour = !n.online ? IDLE : alarmed ? LOW : NAME;
            int valueColour = !n.online ? IDLE
                    : alarmed ? LOW
                    : n.type == CommandHubBlockEntity.TYPE_COUNTER ? VALUE : OK;

            float valueW = font.width(value) * rowScale;
            draw(matrix, buffers, fit(name, room - valueW - 6.0F, rowScale), leftEdge, y, nameColour, rowScale);
            draw(matrix, buffers, value, rightEdge - valueW, y, valueColour, rowScale);
            row++;
        }

        int hidden = be.hiddenRows();
        if (hidden > 0) {
            String more = "+" + hidden + " more";
            draw(matrix, buffers, more, rightEdge - font.width(more), top + capacity * rowH + 1.0F, HEAD, 1.0F);
        }
        pose.popPose();
    }

    /**
     * One panel draws the text for its whole run, so it has to keep drawing when
     * it alone is out of view — otherwise a wide board blanks as soon as its
     * drawing panel leaves the frustum while the rest is still on screen.
     */
    @Override
    public boolean shouldRenderOffScreen(GrandDisplayBlockEntity be) {
        return true;
    }

    /** How far away the board stays legible before it stops drawing. */
    @Override
    public int getViewDistance() {
        return 96;
    }

    /** Another panel of the same board: same block, same facing. */
    private static boolean isPanel(BlockGetter level, BlockPos pos, Direction front) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof GrandDisplayBlock
                && state.getValue(HorizontalDirectionalBlock.FACING) == front;
    }

    /** The compact right-hand reading for a board row. */
    private static String reading(CommandHubBlockEntity.Node n) {
        if (!n.online) {
            return "offline";
        }
        if (n.type == CommandHubBlockEntity.TYPE_COUNTER) {
            return n.alarmed() ? "STALLED" : ItemCounterBlockEntity.compact(n.a) + "/m";
        }
        if (n.type != CommandHubBlockEntity.TYPE_MONITOR) {
            return n.alarmed() ? n.d + "% LOW" : n.d + "%";
        }
        return n.alarmed()
                ? ItemCounterBlockEntity.compact(n.a) + " LOW"
                : ItemCounterBlockEntity.compact(n.a);
    }

    /** Shorten {@code text} until it fits {@code room} units at {@code scale}. */
    private String fit(String text, float room, float scale) {
        if (room <= 0.0F) {
            return "";
        }
        if (font.width(text) * scale <= room) {
            return text;
        }
        String out = text;
        while (!out.isEmpty() && font.width(out + "…") * scale > room) {
            out = out.substring(0, out.length() - 1);
        }
        return out.isEmpty() ? "" : out + "…";
    }

    private void draw(Matrix4f matrix, MultiBufferSource buffers, String text,
                      float x, float y, int colour, float scale) {
        if (text.isEmpty()) {
            return;
        }
        Matrix4f placed = new Matrix4f(matrix).translate(x, y, 0.0F).scale(scale, scale, 1.0F);
        font.drawInBatch(text, 0.0F, 0.0F, colour, false, placed, buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }
}
