package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.DeckManager;
import com.jrpetty.mobtrumps.DuelTables;
import com.jrpetty.mobtrumps.TableActionPayload;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The dueling table's home screen — the casino floor of Mob Trumps. Opened by
 * right-clicking the table: pick VS AI (easy / normal / hard) or VS PLAYER
 * (best of 1/3/5, or draft), and choose whether you battle with your own deck
 * or a random deal. Draft always uses the full card pool for that game only.
 */
public class TableMenuScreen extends Screen {

    // felt & trim palette
    private static final int FELT_LIGHT = 0xFF14503C;
    private static final int FELT_DARK = 0xFF072A1F;
    private static final int PANEL = 0xC0081E16;
    private static final int PANEL_EDGE = 0xFF2E5C48;
    private static final int GOLD = 0xFFE9C46A;
    private static final int GOLD_DIM = 0xFF9A7F45;
    private static final int TEXT_DIM = 0xFFB9C8C0;

    private final BlockPos pos;
    private final String seatedName;
    private final int seatedMode;
    private final boolean selfSeated;

    /** Remembered for the session so re-opening the table keeps your choice. */
    private static boolean useMyDeck = true;

    private record Btn(String key, int x, int y, int w, int h, boolean enabled) {
        boolean hit(double mx, double my) {
            return enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> buttons = new ArrayList<>();
    private final long openedAt = System.currentTimeMillis();

    public TableMenuScreen(BlockPos pos, String seatedName, int seatedMode, boolean selfSeated) {
        super(Component.literal("Dueling Table"));
        this.pos = pos;
        this.seatedName = seatedName == null ? "" : seatedName;
        this.seatedMode = seatedMode;
        this.selfSeated = selfSeated;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean deckReady() {
        return ClientCollection.deck().size() >= DeckManager.MIN_DECK;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long t = System.currentTimeMillis() - openedAt;
        buttons.clear();

        // --- the table: layered felt with a vignette and gold framing ---
        g.fillGradient(0, 0, width, height, FELT_LIGHT, FELT_DARK);
        g.fillGradient(0, 0, width, height / 3, 0x66000000, 0x00000000);
        g.fillGradient(0, height * 2 / 3, width, height, 0x00000000, 0x88000000);
        g.fill(0, 0, width, 2, GOLD_DIM);
        g.fill(0, height - 2, width, height, GOLD_DIM);

        // decorative tilted card backs behind the panels
        decoCard(g, 30, height - 46, -14f + 2f * (float) Math.sin(t / 900.0));
        decoCard(g, width - 64, height - 42, 11f + 2f * (float) Math.sin(t / 760.0 + 1.7));

        // --- title with a slow shine sweep ---
        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, 16f, 0);
        pose.scale(2.1f, 2.1f, 1f);
        String title = "MOB TRUMPS";
        g.drawString(font, title, -font.width(title) / 2, 0, GOLD, true);
        pose.popPose();
        float sweep = (t % 2600L) / 2600f;
        int sweepX = (int) (width / 2f - 90 + sweep * 180);
        g.fillGradient(sweepX - 8, 14, sweepX + 8, 36, 0x00FFFFFF, 0x2AFFFFFF);
        g.drawCenteredString(font, "— DUELING TABLE —", width / 2, 38, TEXT_DIM);

        int panelW = Math.min(420, width - 20);
        int colW = (panelW - 12) / 2;
        int leftX = (width - panelW) / 2;
        int rightX = leftX + colW + 12;
        int panelTop = 52;
        int panelH = height - panelTop - 64;

        // --- VS AI panel ---
        panel(g, leftX, panelTop, colW, panelH, "VS  AI");
        int by = panelTop + 22;
        by = modeButton(g, "cpu_0", leftX + 8, by, colW - 16, "EASY",
                "Plays random stats", 0xFF2E7D46, mouseX, mouseY, true);
        by = modeButton(g, "cpu_1", leftX + 8, by, colW - 16, "NORMAL",
                "Leads its best stat", 0xFF2A5F8A, mouseX, mouseY, true);
        by = modeButton(g, "cpu_2", leftX + 8, by, colW - 16, "HARD",
                "Reads the odds & bluffs", 0xFF8A3A2E, mouseX, mouseY, true);
        g.drawCenteredString(font, "CPU deck: same size as yours,", leftX + colW / 2, by + 2, TEXT_DIM);
        g.drawCenteredString(font, "mostly commons, one legendary", leftX + colW / 2, by + 12, TEXT_DIM);

        // --- VS PLAYER panel ---
        panel(g, rightX, panelTop, colW, panelH, "VS  PLAYER");
        int py = panelTop + 22;
        if (!seatedName.isEmpty() && !selfSeated) {
            // someone is waiting: one big pulsing challenge button
            String modeLabel = DuelTables.Mode.values()[
                    Mth.clamp(seatedMode, 0, DuelTables.Mode.values().length - 1)].label;
            float pulse = 0.75f + 0.25f * (float) Math.sin(t / 300.0);
            int glow = ((int) (0x50 * pulse) << 24) | 0x00FFD54A;
            int chY = py + 14;
            g.fill(rightX + 6, chY - 4, rightX + colW - 6, chY + 40, glow);
            bigButton(g, "challenge", rightX + 10, chY, colW - 20, 26,
                    "CHALLENGE " + seatedName.toUpperCase(java.util.Locale.ROOT),
                    0xFF9A6A18, 0xFFC08A28, mouseX, mouseY, true);
            g.drawCenteredString(font, "They chose: " + modeLabel, rightX + colW / 2, chY + 32, GOLD);
            g.drawCenteredString(font, "Winner takes the match!", rightX + colW / 2, chY + 46, TEXT_DIM);
        } else if (selfSeated) {
            String modeLabel = DuelTables.Mode.values()[
                    Mth.clamp(seatedMode, 0, DuelTables.Mode.values().length - 1)].label;
            g.drawCenteredString(font, "You're seated — " + modeLabel, rightX + colW / 2, py + 8, GOLD);
            g.drawCenteredString(font, "Waiting for a challenger...", rightX + colW / 2, py + 20, TEXT_DIM);
            bigButton(g, "stand", rightX + 14, py + 34, colW - 28, 18, "Stand up",
                    0xFF5A2530, 0xFF7A3140, mouseX, mouseY, true);
        } else {
            py = modeButton(g, "seat_0", rightX + 8, py, colW - 16, "BEST OF 1",
                    "One game, sudden death", 0xFF3A5E2C, mouseX, mouseY, true);
            py = modeButton(g, "seat_1", rightX + 8, py, colW - 16, "BEST OF 3",
                    "First to two wins", 0xFF3A5E2C, mouseX, mouseY, true);
            py = modeButton(g, "seat_2", rightX + 8, py, colW - 16, "BEST OF 5",
                    "First to three wins", 0xFF3A5E2C, mouseX, mouseY, true);
            py = modeButton(g, "seat_3", rightX + 8, py, colW - 16, "DRAFT",
                    "Draft from ALL cards, this game only", 0xFF5E4A8A, mouseX, mouseY, true);
            g.drawCenteredString(font, "You'll wait at the table until", rightX + colW / 2, py + 2, TEXT_DIM);
            g.drawCenteredString(font, "another player clicks it", rightX + colW / 2, py + 12, TEXT_DIM);
        }

        // --- deck bar ---
        int barY = height - 58;
        g.fill(leftX, barY, leftX + panelW, barY + 44, PANEL);
        g.renderOutline(leftX, barY, panelW, 44, PANEL_EDGE);
        g.drawString(font, "BATTLE DECK", leftX + 8, barY + 5, GOLD, false);
        boolean ready = deckReady();
        int deckN = ClientCollection.deck().size();
        pill(g, "deck_my", leftX + 8, barY + 18, "My Deck (" + deckN + ")",
                useMyDeck && ready, ready, mouseX, mouseY);
        pill(g, "deck_rand", leftX + 106, barY + 18, "Random deal",
                !useMyDeck || !ready, true, mouseX, mouseY);
        bigButton(g, "deck_edit", leftX + 196, barY + 17, 62, 14, "Edit Deck",
                0xFF2A5F8A, 0xFF3A7FB4, mouseX, mouseY, true);
        if (!ready) {
            g.drawString(font, "Build a deck of " + DeckManager.MIN_DECK + "+ in the book",
                    leftX + 8, barY + 34, 0xFFCB8A8A, false);
        } else {
            g.drawString(font, "Applies to AI battles — duels & draft deal their own",
                    leftX + 8, barY + 34, TEXT_DIM, false);
        }

        // mini fan of your deck's first cards on the right of the bar
        fan(g, leftX + panelW - 46, barY + 40, deckN);

        g.drawCenteredString(font, "ESC to close", width / 2, height - 11, 0xFF6E8278);
        super.render(g, mouseX, mouseY, partialTick);
    }

    // --- widgets ---

    private void panel(GuiGraphics g, int x, int y, int w, int h, String head) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.renderOutline(x, y, w, h, PANEL_EDGE);
        g.fill(x, y, x + w, y + 14, 0x66000000);
        g.drawCenteredString(font, head, x + w / 2, y + 3, GOLD);
    }

    /** A mode row: bold label + a one-line description. Returns the next y. */
    private int modeButton(GuiGraphics g, String key, int x, int y, int w, String label,
                           String desc, int base, int mouseX, int mouseY, boolean enabled) {
        int h = 26;
        Btn btn = new Btn(key, x, y, w, h, enabled);
        buttons.add(btn);
        boolean hover = btn.hit(mouseX, mouseY);
        g.fill(x, y, x + w, y + h, hover ? brighten(base) : base);
        g.renderOutline(x, y, w, h, hover ? GOLD : 0x66FFFFFF);
        g.drawString(font, label, x + 7, y + 4, 0xFFFFFFFF, true);
        g.drawString(font, desc, x + 7, y + 15, hover ? 0xFFEFE8D0 : TEXT_DIM, false);
        if (hover) {
            g.drawString(font, "▶", x + w - 12, y + 9, GOLD, false);
        }
        return y + h + 5;
    }

    private void bigButton(GuiGraphics g, String key, int x, int y, int w, int h, String label,
                           int base, int hoverCol, int mouseX, int mouseY, boolean enabled) {
        Btn btn = new Btn(key, x, y, w, h, enabled);
        buttons.add(btn);
        boolean hover = btn.hit(mouseX, mouseY);
        g.fill(x, y, x + w, y + h, hover ? hoverCol : base);
        g.renderOutline(x, y, w, h, hover ? GOLD : 0x66FFFFFF);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2 + 1, 0xFFFFFFFF, true);
    }

    private void pill(GuiGraphics g, String key, int x, int y, String label,
                      boolean active, boolean enabled, int mouseX, int mouseY) {
        int w = font.width(label) + 14;
        Btn btn = new Btn(key, x, y, w, 13, enabled);
        buttons.add(btn);
        boolean hover = btn.hit(mouseX, mouseY);
        int bg = !enabled ? 0xFF23312B : active ? 0xFF2E7D46 : hover ? 0xFF20463A : 0xFF16352A;
        g.fill(x, y, x + w, y + 13, bg);
        g.renderOutline(x, y, w, 13, active ? 0xFF55E06A : PANEL_EDGE);
        g.drawString(font, label, x + 7, y + 3,
                !enabled ? 0xFF5E6E66 : active ? 0xFFFFFFFF : TEXT_DIM, false);
    }

    /** A tilted decorative card back on the felt. */
    private void decoCard(GuiGraphics g, int x, int y, float deg) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));
        pose.scale(0.32f, 0.32f, 1f);
        pose.translate(-CardRenderer.CARD_W / 2f, -CardRenderer.CARD_H / 2f, 0);
        CardRenderer.renderBack(g, font, 0, 0, 1f);
        pose.popPose();
    }

    /** A little fan of face-down mini cards showing the deck is ready. */
    private void fan(GuiGraphics g, int cx, int baseY, int deckN) {
        int cards = deckN > 0 ? 3 : 1;
        for (int i = 0; i < cards; i++) {
            float deg = (i - (cards - 1) / 2f) * 14f;
            var pose = g.pose();
            pose.pushPose();
            pose.translate(cx + i * 6 - (cards - 1) * 3, baseY, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(deg));
            pose.scale(0.16f, 0.16f, 1f);
            pose.translate(-CardRenderer.CARD_W / 2f, -CardRenderer.CARD_H, 0);
            CardRenderer.renderBack(g, font, 0, 0, 1f);
            pose.popPose();
        }
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 28);
        int gg = Math.min(255, ((argb >> 8) & 0xFF) + 28);
        int b = Math.min(255, (argb & 0xFF) + 28);
        return (argb & 0xFF000000) | (r << 16) | (gg << 8) | b;
    }

    // --- interaction ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (Btn btn : buttons) {
            if (!btn.hit(mouseX, mouseY)) {
                continue;
            }
            click();
            String key = btn.key();
            if (key.startsWith("cpu_")) {
                int difficulty = key.charAt(4) - '0';
                send(TableActionPayload.CPU, difficulty, useMyDeck && deckReady());
                // the battle screen replaces this menu when the server deals
            } else if (key.startsWith("seat_")) {
                send(TableActionPayload.SEAT, key.charAt(5) - '0', false);
                onClose();
            } else if (key.equals("challenge")) {
                send(TableActionPayload.CHALLENGE, 0, false);
                onClose();
            } else if (key.equals("stand")) {
                send(TableActionPayload.STAND, 0, false);
                onClose();
            } else if (key.equals("deck_my")) {
                useMyDeck = true;
            } else if (key.equals("deck_rand")) {
                useMyDeck = false;
            } else if (key.equals("deck_edit") && minecraft != null) {
                minecraft.setScreen(new DeckBuilderScreen(this));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(int action, int arg, boolean useDeck) {
        PacketDistributor.sendToServer(new TableActionPayload(pos, action, arg, useDeck));
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
    }
}
