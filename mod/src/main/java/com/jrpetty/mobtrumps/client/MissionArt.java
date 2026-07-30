package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.CampaignMission;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

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
