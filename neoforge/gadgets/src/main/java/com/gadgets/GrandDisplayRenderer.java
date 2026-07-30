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
 * The Grand Display's face: one row per linked gadget, name on the left and
 * reading on the right, alarmed rows in red so a glance is enough.
 *
 * <p>The text is drawn {@value #BLOCKS_WIDE} blocks wide, overflowing onto the
 * wall either side of the block itself. A name and a number will not fit
 * legibly across a single 16-pixel face at six rows — the arithmetic simply
 * does not work — so the board is designed to be built into a flat wall with
 * its neighbours left clear, and its tooltip says so.
 */
public class GrandDisplayRenderer implements BlockEntityRenderer<GrandDisplayBlockEntity> {
    private static final int NAME = 0xE6AA3C;
    private static final int VALUE = 0xFFC864;
    private static final int LOW = 0xFF5555;
    private static final int OK = 0x7CE87C;
    private static final int IDLE = 0x8E96A4;

    /** How far the board's text spreads, in blocks. */
    public static final int BLOCKS_WIDE = 3;
    /** Text units per block, so rows can be laid out in readable numbers. */
    private static final float UNITS_PER_BLOCK = 90.0F;
    private static final float WIDTH = BLOCKS_WIDE * UNITS_PER_BLOCK;
    private static final float PAD = 6.0F;

    private final Font font;

    public GrandDisplayRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(GrandDisplayBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof GrandDisplayBlock)) {
            return;
        }
        Direction front = state.getValue(HorizontalDirectionalBlock.FACING);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-front.toYRot()));
        pose.translate(0.0, 0.0, 0.505);
        float scale = 1.0F / UNITS_PER_BLOCK;
        pose.scale(scale, -scale, scale);
        Matrix4f matrix = pose.last().pose();

        float leftEdge = -WIDTH / 2.0F + PAD;
        float rightEdge = WIDTH / 2.0F - PAD;

        if (!be.isHubLinked()) {
            draw(matrix, buffers, "NO HUB — link with the Monitor Wand", leftEdge, -5.0F, IDLE, 1.4F);
            pose.popPose();
            return;
        }
        if (be.getRows().isEmpty()) {
            draw(matrix, buffers, "Hub has nothing linked yet", leftEdge, -5.0F, IDLE, 1.4F);
            pose.popPose();
            return;
        }

        int capacity = be.rowCapacity();
        boolean large = be.isLarge();
        float rowScale = large ? 2.2F : 1.3F;
        float rowH = 11.0F * rowScale;
        float top = -(capacity * rowH) / 2.0F;

        int row = 0;
        for (CommandHubBlockEntity.Node n : be.getRows()) {
            float y = top + row * rowH;
            boolean alarmed = n.alarmed();
            String name = n.label.isBlank()
                    ? (n.type == CommandHubBlockEntity.TYPE_COUNTER ? "(unnamed counter)" : "(unnamed stock)")
                    : n.label;
            String value = reading(n);

            int nameColour = !n.online ? IDLE : alarmed ? LOW : NAME;
            int valueColour = !n.online ? IDLE
                    : alarmed ? LOW
                    : n.type == CommandHubBlockEntity.TYPE_COUNTER ? VALUE : OK;

            float valueW = font.width(value) * rowScale;
            // Trim the name to whatever space the reading leaves on its row.
            float nameRoom = (rightEdge - leftEdge) - valueW - 8.0F;
            draw(matrix, buffers, fit(name, nameRoom, rowScale), leftEdge, y, nameColour, rowScale);
            draw(matrix, buffers, value, rightEdge - valueW, y, valueColour, rowScale);
            row++;
        }

        int hidden = be.hiddenRows();
        if (hidden > 0) {
            String more = "+" + hidden + " more on the hub";
            draw(matrix, buffers, more, leftEdge, top + capacity * rowH + 2.0F, IDLE, 1.0F);
        }
        pose.popPose();
    }

    /** The compact right-hand reading for a board row. */
    private static String reading(CommandHubBlockEntity.Node n) {
        if (!n.online) {
            return "offline";
        }
        if (n.type == CommandHubBlockEntity.TYPE_COUNTER) {
            return n.alarmed() ? "STALLED" : ItemCounterBlockEntity.compact(n.a) + "/m";
        }
        return n.alarmed()
                ? ItemCounterBlockEntity.compact(n.a) + " LOW"
                : ItemCounterBlockEntity.compact(n.a);
    }

    /** Shorten {@code text} until it fits {@code room} units at {@code scale}. */
    private String fit(String text, float room, float scale) {
        if (font.width(text) * scale <= room) {
            return text;
        }
        String out = text;
        while (out.length() > 1 && font.width(out + "…") * scale > room) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    private void draw(Matrix4f matrix, MultiBufferSource buffers, String text,
                      float x, float y, int colour, float scale) {
        Matrix4f placed = new Matrix4f(matrix).translate(x, y, 0.0F).scale(scale, scale, 1.0F);
        font.drawInBatch(text, 0.0F, 0.0F, colour, false, placed, buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }
}
