package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Drawing for the glass faces the gadgets wear.
 *
 * <p>Every panel is the same instrument: a name across the top, one number big
 * enough to read from across the room, and a quieter line under it. Keeping
 * that in one place is what stops a wall of monitors looking like it came from
 * three different mods.
 *
 * <p>The rule that matters most here is that a row is <em>shrunk</em> to fit
 * its glass rather than cut short. A rate reading a little smaller is still the
 * rate; one reading "297 …" is not a number at all. Only a row that would have
 * to shrink past legibility is cut, and then only as a last resort.
 *
 * <p>Callers set up the pose once — centred on the glass, scaled to a base text
 * size — and then position rows in base units, asking for a multiple of the
 * base size where a row wants to be bigger. Shrinking happens against that
 * multiple, so it never disturbs where the other rows sit.
 */
public final class FaceText {
    /** How far a row may shrink before it is cut short instead. */
    private static final float MIN_SHRINK = 0.55F;
    /** Space advance in the vanilla font, used to size the bar's quads. */
    private static final float SPACE = 4.0F;

    private final Font font;
    /** Width of the glass, in base text units. */
    private final float room;

    /**
     * @param glassPx width of the model's screen opening, in block pixels
     * @param scale   the base text scale the caller has applied to the pose
     */
    public FaceText(Font font, int glassPx, float scale) {
        this.font = font;
        this.room = glassPx / 16.0F / scale;
    }

    /** Text units across the glass, at the base scale. */
    public float room() {
        return room;
    }

    /** One centred row at the base size. */
    public void line(PoseStack pose, MultiBufferSource buffers, String text, float y, int colour) {
        line(pose, buffers, text, y, 1.0F, colour);
    }

    /**
     * One centred row drawn at {@code size} times the base size, shrunk if it
     * would otherwise run over the bezel and out into the air beside the block.
     */
    public void line(PoseStack pose, MultiBufferSource buffers, String text, float y, float size, int colour) {
        int width = font.width(text);
        float used = size;
        if (width > 0) {
            used = Mth.clamp(room / width, size * MIN_SHRINK, size);
        }
        String shown = clip(text, used);
        pose.pushPose();
        // Scaled about the row's own left-to-right centre, so a shrunk row stays
        // centred on the glass instead of drifting toward one edge.
        pose.scale(used, used, 1.0F);
        font.drawInBatch(shown, -font.width(shown) / 2.0F, y / used, colour, false,
                pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * A fill bar: a dark track with the filled part drawn over it.
     *
     * <p>Both are solid rectangles, which a block entity renderer gets most
     * safely out of the font itself — {@link Font#drawInBatch}'s background
     * colour fills a rectangle behind the string, and a string of spaces is
     * that rectangle with no glyphs in it. Squashing x to a quarter makes one
     * space exactly one unit wide, so the fill lands on the percentage asked
     * for rather than the nearest four.
     *
     * <p>The fill is lifted a sliver off the track. They cover the same pixels,
     * and two quads contesting one depth is a flicker.
     */
    public void bar(PoseStack pose, MultiBufferSource buffers, float y, float width,
                    int percent, int fill, int track) {
        pose.pushPose();
        pose.scale(1.0F / SPACE, 1.0F, 1.0F);
        int cells = (int) width;
        rect(pose.last().pose(), buffers, -width / 2.0F, y, cells, track);
        int filled = Math.round(cells * Mth.clamp(percent, 0, 100) / 100.0F);
        if (filled > 0) {
            pose.translate(0.0F, 0.0F, 0.5F);
            rect(pose.last().pose(), buffers, -width / 2.0F, y, filled, 0xFF000000 | fill);
        }
        pose.popPose();
    }

    private void rect(Matrix4f matrix, MultiBufferSource buffers, float x, float y, int cells, int argb) {
        font.drawInBatch(" ".repeat(cells), x * SPACE, y, 0, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, argb, LightTexture.FULL_BRIGHT);
    }

    /** Last resort for a row still too wide once it has shrunk as far as it may. */
    private String clip(String text, float size) {
        float fits = room / size;
        if (font.width(text) <= fits) {
            return text;
        }
        String out = text;
        while (!out.isEmpty() && font.width(out + "…") > fits) {
            out = out.substring(0, out.length() - 1);
        }
        return out.isEmpty() ? "" : out + "…";
    }
}
