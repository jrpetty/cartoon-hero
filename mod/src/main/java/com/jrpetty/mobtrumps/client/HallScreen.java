package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.HallRequestPayload;
import com.jrpetty.mobtrumps.game.Category;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Hall of Fame: what this server did first, and who did it.
 *
 * <p>Three tabs, because they answer three different questions. <b>Firsts</b>
 * is the roll of 000001s — one per mob, permanent, and still true after the
 * card has been traded away or shredded. <b>Sets</b> is who finished each
 * category before anyone else. <b>Finders</b> ranks players by how many
 * 000001s they are the discoverer of, which is the closest thing the server has
 * to a collector's reputation.
 *
 * <p>Mobs nobody has ever unlocked are listed too. On a young server that is
 * the interesting half of the page.
 */
public class HallScreen extends Screen {

    private static final int INK = 0xFFF2ECDD;
    private static final int DIM = 0xFF9A93A8;
    private static final int FAINT = 0xFF7E7590;
    private static final int GOLD = 0xFFE3C071;
    private static final int PLATE = 0xFF241F33;
    private static final int ROW_H = 12;

    private enum Tab { FIRSTS, SETS, FINDERS;
        String label() {
            String n = name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }
    }

    private Tab tab = Tab.FIRSTS;
    private int scroll;
    private int panelX, panelY, panelW, panelH;
    private final int[][] tabRects = new int[Tab.values().length][];

    public HallScreen() {
        super(Component.literal("Hall of Fame"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        panelW = Math.min(360, width - 20);
        panelH = Math.min(226, height - 62);
        panelX = (width - panelW) / 2;
        panelY = Math.max(34, (height - panelH) / 2);
        PacketDistributor.sendToServer(HallRequestPayload.get());
    }

    private int bodyTop() {
        return panelY + 44;
    }

    private int bodyBottom() {
        return panelY + panelH - 8;
    }

    private int rows() {
        return Math.max(1, (bodyBottom() - bodyTop()) / ROW_H);
    }

    /** The lines the current tab wants to show: label, who, and a tint. */
    private List<String[]> lines() {
        List<String[]> out = new ArrayList<>();
        switch (tab) {
            case FIRSTS -> {
                Map<String, String> firsts = ClientHall.firstPrints();
                for (MobCard card : MobCards.ALL) {
                    String who = firsts.get(card.id());
                    out.add(new String[]{card.displayName(),
                            who == null ? "— unclaimed —" : who,
                            who == null ? "0" : "1"});
                }
            }
            case SETS -> {
                Map<String, String> cats = ClientHall.categories();
                for (Category c : Category.values()) {
                    String who = cats.get(c.name().toLowerCase(Locale.ROOT));
                    out.add(new String[]{c.label(),
                            who == null ? "— nobody yet —" : who,
                            who == null ? "0" : "1"});
                }
                for (String who : ClientHall.completions()) {
                    out.add(new String[]{"THE COMPLETE SET", who, "2"});
                }
            }
            case FINDERS -> {
                for (Map.Entry<String, Integer> e : ClientHall.finders()) {
                    out.add(new String[]{e.getKey(),
                            e.getValue() + " first print" + (e.getValue() == 1 ? "" : "s"), "1"});
                }
            }
        }
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fillGradient(0, 0, width, height, 0xF01A1526, 0xF00B0912);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, panelY - 30f, 0);
        pose.scale(1.8f, 1.8f, 1f);
        String title = "HALL OF FAME";
        g.drawString(font, title, -font.width(title) / 2, 0, GOLD, true);
        pose.popPose();

        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, 0xFF15121F);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PLATE);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, GOLD);

        // tabs
        int tw = (panelW - 20) / Tab.values().length;
        for (Tab t : Tab.values()) {
            int tx = panelX + 10 + t.ordinal() * tw;
            boolean on = t == tab;
            boolean hover = mouseX >= tx && mouseX < tx + tw - 3
                    && mouseY >= panelY + 8 && mouseY < panelY + 24;
            g.fill(tx, panelY + 8, tx + tw - 3, panelY + 24,
                    on ? 0xFF4A3E6E : hover ? 0xFF2C2542 : 0xFF1C1830);
            if (on) g.renderOutline(tx, panelY + 8, tw - 3, 16, GOLD);
            String label = t.label();
            g.drawString(font, label, tx + (tw - 3 - font.width(label)) / 2, panelY + 12,
                    on ? INK : DIM, false);
            tabRects[t.ordinal()] = new int[]{tx, panelY + 8, tw - 3, 16};
        }

        List<String[]> lines = lines();
        String sub = switch (tab) {
            case FIRSTS -> ClientHall.firstPrints().size() + " of " + MobCards.ALL.size()
                    + " mobs have a 000001 on this server";
            case SETS -> "First to finish each set. A first is permanent.";
            case FINDERS -> "Ranked by how many 000001s you discovered.";
        };
        g.drawString(font, fit(sub, panelW - 20), panelX + 10, panelY + 30, FAINT, false);
        g.fill(panelX + 10, panelY + 41, panelX + panelW - 10, panelY + 42, 0x30FFFFFF);

        if (!ClientHall.loaded()) {
            g.drawString(font, "Reading the records…", panelX + 10, bodyTop() + 6, DIM, false);
            return;
        }
        if (lines.isEmpty()) {
            g.drawString(font, "Nothing recorded yet — be the first.",
                    panelX + 10, bodyTop() + 6, DIM, false);
        }

        scroll = Mth.clamp(scroll, 0, Math.max(0, lines.size() - rows()));
        int shown = Math.min(rows(), lines.size() - scroll);
        int y = bodyTop();
        for (int r = 0; r < shown; r++) {
            String[] line = lines.get(scroll + r);
            boolean claimed = !"0".equals(line[2]);
            boolean crown = "2".equals(line[2]);
            if (r % 2 == 0) {
                g.fill(panelX + 8, y, panelX + panelW - 8, y + ROW_H - 1, 0x14FFFFFF);
            }
            g.drawString(font, fit(line[0], panelW / 2 - 20), panelX + 12, y + 2,
                    crown ? GOLD : claimed ? INK : FAINT, false);
            String who = line[1];
            g.drawString(font, fit(who, panelW / 2 - 16),
                    panelX + panelW - 12 - font.width(fit(who, panelW / 2 - 16)), y + 2,
                    crown ? GOLD : claimed ? 0xFF9FD98F : FAINT, false);
            y += ROW_H;
        }
        if (lines.size() > rows()) {
            int h = bodyBottom() - bodyTop();
            g.fill(panelX + panelW - 5, bodyTop(), panelX + panelW - 2, bodyTop() + h, 0x30FFFFFF);
            int thumb = Math.max(14, h * rows() / lines.size());
            int ty = bodyTop() + (int) ((long) (h - thumb) * scroll / Math.max(1, lines.size() - rows()));
            g.fill(panelX + panelW - 5, ty, panelX + panelW - 2, ty + thumb, 0xFF4A4066);
        }
        g.drawCenteredString(font, "ESC to close", width / 2, panelY + panelH + 9, 0xFF5F5875);
    }

    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width("…")));
        return cut.isEmpty() ? "" : cut + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Tab t : Tab.values()) {
                int[] r = tabRects[t.ordinal()];
                if (r != null && mouseX >= r[0] && mouseX < r[0] + r[2]
                        && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    tab = t;
                    scroll = 0;
                    if (minecraft != null) {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                                SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        scroll = Math.max(0, scroll - (int) Math.signum(dy));
        return true;
    }
}
