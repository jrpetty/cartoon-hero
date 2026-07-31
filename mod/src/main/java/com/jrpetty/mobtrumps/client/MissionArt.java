package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.CampaignMission;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * The banner scene behind each campaign mission.
 *
 * <p>One 170x60 pixel-art texture per mission, keyed on the mission id and
 * built from its <em>name</em> rather than its category — Shallow Graves and
 * The Long Night are both Undead and look nothing alike, because one is a
 * moonlit churchyard and the other is an army cresting a ridge.
 *
 * <p>Authored at half size and drawn at 2x so every source pixel lands on an
 * exact 2x2 block; the art stays crisp instead of smearing the way an
 * arbitrary stretch would.
 */
public final class MissionArt {

    /** Native size of the art. */
    public static final int ART_W = 170, ART_H = 60;
    /** The scale it is drawn at, and the resulting banner size. */
    public static final int ZOOM = 2;
    public static final int BANNER_W = ART_W * ZOOM, BANNER_H = ART_H * ZOOM;

    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    private MissionArt() {
    }

    public static ResourceLocation of(CampaignMission mission) {
        return CACHE.computeIfAbsent(mission.id(), id ->
                ResourceLocation.fromNamespaceAndPath("mobtrumps", "textures/gui/mission/" + id + ".png"));
    }

    /**
     * Draw the banner into a box, cropping rather than stretching when the box
     * is narrower than the art, and scrimmed at the bottom so text laid over it
     * still reads.
     *
     * @param locked draw it drained of colour, for a mission not yet unlocked
     */
    public static void draw(GuiGraphics g, CampaignMission mission, int x, int y, int w, int h,
                            boolean locked) {
        int drawW = Math.min(w, BANNER_W);
        int drawH = Math.min(h, BANNER_H);
        g.enableScissor(x, y, x + drawW, y + drawH);
        var pose = g.pose();
        pose.pushPose();
        // centre the crop so a narrow panel loses the edges, not the subject
        pose.translate(x - (BANNER_W - drawW) / 2f, y, 0);
        pose.scale(ZOOM, ZOOM, 1f);
        g.blit(of(mission), 0, 0, 0f, 0f, ART_W, ART_H, ART_W, ART_H);
        pose.popPose();
        g.disableScissor();

        if (locked) {
            // a mission you cannot play yet is behind glass
            g.fill(x, y, x + drawW, y + drawH, 0xB0121020);
        }
        // scrim: dark at the foot of the banner so the title sits on it cleanly
        g.fillGradient(x, y + drawH - 26, x + drawW, y + drawH, 0x00120E1E, 0xF0120E1E);
        g.fill(x, y + drawH - 1, x + drawW, y + drawH, mission.anchor().accent());
    }

    /**
     * The banner blown up to fill a whole screen, for a live mission.
     *
     * <p>Scaled to <em>cover</em> — one uniform factor, cropping whatever
     * overhangs — so the scene never stretches out of shape. It is then pushed
     * well back with a scrim and a vignette, because it is the room the game is
     * happening in, not the game: the cards and the felt have to stay the
     * brightest things on screen.
     *
     * @param dim extra darkening 0-255 on top of the standard scrim
     */
    public static void drawBackdrop(GuiGraphics g, CampaignMission mission,
                                    int width, int height, int dim) {
        float scale = Math.max(width / (float) ART_W, height / (float) ART_H);
        int drawW = Math.max(width, Math.round(ART_W * scale));
        int drawH = Math.max(height, Math.round(ART_H * scale));
        int x = (width - drawW) / 2;
        int y = (height - drawH) / 2;
        g.blit(of(mission), x, y, drawW, drawH, 0f, 0f, ART_W, ART_H, ART_W, ART_H);

        g.fill(0, 0, width, height, ((0x66 + Mth.clamp(dim, 0, 0x60)) << 24));
        // vignette: darker at the edges so the middle of the table reads
        g.fillGradient(0, 0, width, height / 3, 0x66000000, 0x00000000);
        g.fillGradient(0, height * 2 / 3, width, height, 0x00000000, 0x77000000);
        // and a tint of the mission's category, so the room has a colour
        g.fill(0, 0, width, height, 0x18000000 | (mission.anchor().accent() & 0xFFFFFF));
    }

    /**
     * A thin horizontal slice of the banner, for a route plate. Takes the band
     * through the middle of the scene, so each plate carries a recognisable
     * sliver of its own mission rather than a flat colour.
     */
    public static void drawStrip(GuiGraphics g, CampaignMission mission, int x, int y, int w, int h,
                                 boolean locked) {
        g.enableScissor(x, y, x + w, y + h);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x - Math.max(0, (BANNER_W - w) / 2f), y - (BANNER_H - h) / 2f, 0);
        pose.scale(ZOOM, ZOOM, 1f);
        g.blit(of(mission), 0, 0, 0f, 0f, ART_W, ART_H, ART_W, ART_H);
        pose.popPose();
        // heavy wash: the plate is a label, not a picture — the text must win
        g.fill(x, y, x + w, y + h, locked ? 0xE0141126 : 0xB81C1730);
        g.disableScissor();
    }
}
