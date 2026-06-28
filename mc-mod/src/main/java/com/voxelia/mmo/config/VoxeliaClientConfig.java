package com.voxelia.mmo.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only config: the skills HUD's visibility and placement, so each player
 * can move it to whichever corner they like. Lives in config/voxelia_mmo-client.toml.
 */
public final class VoxeliaClientConfig {
    private VoxeliaClientConfig() {}

    public enum Anchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue SHOW_HUD;
    private static final ModConfigSpec.BooleanValue HUD_PANEL;
    private static final ModConfigSpec.EnumValue<Anchor> ANCHOR;
    private static final ModConfigSpec.IntValue OFFSET_X;
    private static final ModConfigSpec.IntValue OFFSET_Y;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Voxelia MMO — skills HUD (client only).").push("hud");
        SHOW_HUD = b.comment("Show the skills HUD overlay (toggle in-game with /voxelia hud).")
            .define("showHud", true);
        HUD_PANEL = b.comment("Draw a scoreboard-style background panel behind the HUD.")
            .define("hudPanel", true);
        ANCHOR = b.comment("Which screen corner the HUD anchors to.")
            .defineEnum("anchor", Anchor.TOP_LEFT);
        OFFSET_X = b.comment("Horizontal offset in pixels from the anchored corner.")
            .defineInRange("offsetX", 4, 0, 8000);
        OFFSET_Y = b.comment("Vertical offset in pixels from the anchored corner.")
            .defineInRange("offsetY", 4, 0, 8000);
        b.pop();
        SPEC = b.build();
    }

    public static boolean showHud() { return SHOW_HUD.get(); }
    public static void setShowHud(boolean v) { SHOW_HUD.set(v); }
    public static boolean hudPanel() { return HUD_PANEL.get(); }
    public static Anchor anchor() { return ANCHOR.get(); }
    public static void setAnchor(Anchor a) { ANCHOR.set(a); }
    public static int offsetX() { return OFFSET_X.get(); }
    public static int offsetY() { return OFFSET_Y.get(); }
}
