package com.voxelia.mmo.client;

import com.voxelia.mmo.config.VoxeliaClientConfig;
import com.voxelia.mmo.config.VoxeliaClientConfig.Anchor;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * The one entry point for everything that isn't an ability: a "Menu" button in the
 * top-right of every Voxelia panel that drops down the sibling screens (Talents,
 * Profile) and the display toggles (sidebar, HUD, HUD corner). Keeps the mod to a
 * single screen keybind — open the Skills menu, reach everything else from here.
 */
public final class ScreenMenu {
    /** Which screen is hosting this menu (its own row is marked current). */
    public enum Page { SKILLS, TALENTS, PROFILE, LEADERBOARD }

    private enum Item { SKILLS, TALENTS, PROFILE, LEADERBOARD, SIDEBAR, HUD, HUD_CORNER }

    private static final int ROW_H = 12;
    private static final int SEP_H = 4;
    private static final int SEP_BEFORE = 4; // separator sits before the display toggles

    private boolean open;
    private int[] button = new int[4];
    private int[] popup;
    // Panel bounds from the last renderButton, so the dropdown can sit beside it.
    private int panelX;
    private int panelY;
    private int panelW;
    private final List<Item> items = new ArrayList<>();
    private final List<int[]> rows = new ArrayList<>();

    public boolean isOpen() {
        return open;
    }

    /** Closes the dropdown; returns true if it was open (so ESC can consume the press). */
    public boolean close() {
        boolean was = open;
        open = false;
        return was;
    }

    /**
     * Draws the button right-aligned inside a panel's 16px title bar. {@code alert}
     * puts a green dot on it (unspent talent points waiting).
     */
    public void renderButton(GuiGraphics g, Font font, int panelX, int panelY, int panelW,
                             int mouseX, int mouseY, boolean alert) {
        String label = "Menu";
        int textW = font.width(label);
        int dotW = alert ? 6 : 0;
        int tw = 5 + 7 + 4 + textW + dotW + 4 + 5 + 5;
        int x1 = panelX + panelW - 4 - tw;
        boolean hover = mouseX >= x1 && mouseX < x1 + tw && mouseY >= panelY && mouseY < panelY + 16;
        button = new int[]{x1, panelY, x1 + tw, panelY + 16};
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelW = panelW;

        if (open) g.fill(x1, panelY + 2, x1 + tw, panelY + 16, 0x30060B12);
        int col = open ? VoxeliaUi.GOLD : (hover ? 0xFFC8D6E0 : VoxeliaUi.MUTED);

        int ix = x1 + 5, iy = panelY + 5;
        g.fill(ix, iy, ix + 7, iy + 1, col);
        g.fill(ix, iy + 3, ix + 7, iy + 4, col);
        g.fill(ix, iy + 6, ix + 7, iy + 7, col);

        int tx = ix + 7 + 4;
        g.drawString(font, label, tx, panelY + 4, col);
        if (alert) { // breathing green dot — points are waiting to be spent
            int da = 0xA0 + (int) (0x5F * VoxeliaUi.pulse());
            g.fill(tx + textW + 2, panelY + 6, tx + textW + 6, panelY + 10, (da << 24) | 0x6EE86E);
        }

        int cx = tx + textW + dotW + 4, cy = panelY + 7;
        for (int i = 0; i < 3; i++) { // chevron: down when closed, up when open
            if (open) g.fill(cx + i, cy + 2 - i, cx + 5 - i, cy + 3 - i, col);
            else g.fill(cx + i, cy + i, cx + 5 - i, cy + 1 + i, col);
        }
        if (open) g.fill(x1 + 2, panelY + 15, x1 + tw - 2, panelY + 17, VoxeliaUi.GOLD);
        else if (hover) g.fill(x1 + 2, panelY + 14, x1 + tw - 2, panelY + 15, 0x60FFCE54);
    }

    /**
     * Draws the dropdown under the button. Call this last (after the panel's content)
     * so it overlays, and always after {@link #renderButton} in the same frame.
     */
    public void renderDropdown(GuiGraphics g, Font font, Page current, int mouseX, int mouseY) {
        items.clear();
        rows.clear();
        popup = null;
        if (!open) return;

        int unspent = 0;
        for (Skill s : Skill.values()) unspent += ClientTalents.available(s);

        Item[] order = Item.values();
        String[] labels = {
            "Skills" + VoxeliaUi.keyTag(font, VoxeliaKeys.OPEN_MENU), "Talent Tree", "Character Profile",
            "Leaderboards", "Skill Sidebar", "Corner HUD", "HUD Corner",
        };
        String[] values = {
            "", unspent > 0 ? String.valueOf(unspent) : "", "", "",
            VoxeliaClientConfig.showSidebar() ? "On" : "Off",
            VoxeliaClientConfig.showHud() ? "On" : "Off",
            cornerName(VoxeliaClientConfig.anchor()),
        };

        int w = 112;
        for (int i = 0; i < order.length; i++) {
            w = Math.max(w, font.width(labels[i]) + 16 + font.width(values[i]) + 14);
        }
        int h = order.length * ROW_H + SEP_H + 6;

        // Open alongside the panel, never over its content. Prefer the right; fall
        // back to the left, and only clamp to the screen if neither side fits.
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x1 = panelX + panelW + 6;
        if (x1 + w > screenW - 3) {
            int leftX1 = panelX - 6 - w;
            x1 = leftX1 >= 3 ? leftX1 : Math.max(3, screenW - 3 - w);
        }
        int y1 = Math.max(3, Math.min(panelY, screenH - 3 - h));
        int x2 = x1 + w;

        // Lift the whole popup above the panel: Minecraft flushes GUI text in its own
        // batch, so without a z-step the cards' labels would draw straight through it.
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        g.fill(x1 + 2, y1 + h + 1, x2 + 2, y1 + h + 3, 0x50000000);
        g.fill(x2, y1 + 2, x2 + 2, y1 + h + 1, 0x50000000);
        g.fill(x1 - 1, y1 - 1, x2 + 1, y1 + h + 1, 0xFF52667B);
        g.fillGradient(x1, y1, x2, y1 + h, 0xF41A2634, 0xF80D141C);
        g.fill(x1, y1, x2, y1 + 1, 0x30FFFFFF);
        popup = new int[]{x1, y1, x2, y1 + h};

        int ry = y1 + 3;
        for (int i = 0; i < order.length; i++) {
            if (i == SEP_BEFORE) {
                g.fill(x1 + 5, ry + 1, x2 - 5, ry + 2, 0x30FFFFFF);
                ry += SEP_H;
            }
            boolean over = mouseX >= x1 && mouseX < x2 && mouseY >= ry && mouseY < ry + ROW_H;
            boolean isCurrent = (order[i] == Item.SKILLS && current == Page.SKILLS)
                || (order[i] == Item.TALENTS && current == Page.TALENTS)
                || (order[i] == Item.PROFILE && current == Page.PROFILE)
                || (order[i] == Item.LEADERBOARD && current == Page.LEADERBOARD);

            if (over) g.fill(x1 + 1, ry, x2 - 1, ry + ROW_H, 0x2889C7FF);
            if (isCurrent) g.fill(x1 + 1, ry, x1 + 3, ry + ROW_H, VoxeliaUi.GOLD);

            int lc = isCurrent ? VoxeliaUi.GOLD
                : (i < SEP_BEFORE ? (over ? 0xFFFFFFFF : VoxeliaUi.TEXT) : VoxeliaUi.MUTED);
            g.drawString(font, labels[i], x1 + 7, ry + 2, lc);

            if (!values[i].isEmpty()) {
                if (order[i] == Item.TALENTS) {
                    VoxeliaUi.pill(g, font, x2 - 6, ry, values[i], 0x6EE86E, true);
                } else {
                    int vc = order[i] == Item.HUD_CORNER ? VoxeliaUi.LINK
                        : ("On".equals(values[i]) ? VoxeliaUi.GOOD : VoxeliaUi.DISABLED);
                    g.drawString(font, values[i], x2 - 6 - font.width(values[i]), ry + 2, vc);
                }
            }

            items.add(order[i]);
            rows.add(new int[]{x1, ry, x2, ry + ROW_H});
            ry += ROW_H;
        }
        g.pose().popPose();
    }

    /** Handles the button and every dropdown row. Returns true when the click was consumed. */
    public boolean mouseClicked(double mx, double my, Page current) {
        if (in(button, mx, my)) {
            open = !open;
            click();
            return true;
        }
        if (!open) return false;
        for (int i = 0; i < rows.size(); i++) {
            if (in(rows.get(i), mx, my)) {
                activate(items.get(i), current);
                click();
                return true;
            }
        }
        if (popup != null && in(popup, mx, my)) return true; // dead space inside the popup
        open = false;                                        // click anywhere else closes it
        return true;
    }

    private void activate(Item item, Page current) {
        Minecraft mc = Minecraft.getInstance();
        switch (item) {
            case SKILLS -> {
                open = false;
                if (current != Page.SKILLS) mc.setScreen(new SkillsScreen());
            }
            case TALENTS -> {
                open = false;
                if (current != Page.TALENTS) mc.setScreen(new TalentScreen());
            }
            case PROFILE -> {
                open = false;
                if (current != Page.PROFILE) mc.setScreen(new ProfileScreen());
            }
            case LEADERBOARD -> {
                open = false;
                if (current != Page.LEADERBOARD) mc.setScreen(new LeaderboardScreen());
            }
            // Toggles keep the dropdown open so you can see the change and flip another.
            case SIDEBAR -> VoxeliaClientConfig.setShowSidebar(!VoxeliaClientConfig.showSidebar());
            case HUD -> VoxeliaClientConfig.setShowHud(!VoxeliaClientConfig.showHud());
            case HUD_CORNER -> {
                Anchor[] all = Anchor.values();
                VoxeliaClientConfig.setAnchor(all[(VoxeliaClientConfig.anchor().ordinal() + 1) % all.length]);
            }
        }
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private static String cornerName(Anchor a) {
        return switch (a) {
            case TOP_LEFT -> "Top Left";
            case TOP_RIGHT -> "Top Right";
            case BOTTOM_LEFT -> "Bottom Left";
            case BOTTOM_RIGHT -> "Bottom Right";
        };
    }

    private static boolean in(int[] r, double mx, double my) {
        return r != null && mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
    }
}
