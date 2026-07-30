package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.LinkDisplayPayload;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picker for a holo projector: choose any card you own to project onto it.
 * Click to display, shift-click for the holographic version, or clear it.
 * The card never leaves your collection — this only links which card is shown.
 *
 * <p>Typographically this is a quiet, gallery-ish screen: a letter-spaced
 * eyebrow over a sentence-case headline, a hairline rule, and everything else
 * set small and dim so the cards themselves carry the page. The footer is laid
 * out in fixed left/right slots, so the hint line and the Clear button can
 * never collide the way a centred hint over a centred button did.
 */
public class ProjectorCardScreen extends Screen {

    private static final float SCALE = 0.34f;
    private static final int GAP = 8;
    private static final int HEADER_H = 84;
    private static final int FOOTER_H = 44;

    private static final int BG = 0xF00B0D11;
    private static final int PANEL = 0xFF14171D;
    private static final int PANEL_HI = 0xFF1D2229;
    private static final int HAIRLINE = 0xFF262B34;
    private static final int ACCENT = 0xFF4CC2F1;
    private static final int FOIL_ACCENT = 0xFFB57BFF;
    private static final int TEXT = 0xFFE8EDF4;
    private static final int TEXT_DIM = 0xFF8C95A3;
    private static final int TEXT_FAINT = 0xFF5C6472;

    private final BlockPos pos;
    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final List<MobCard> owned = new ArrayList<>();

    private EditBox search;
    private int scrollRow = 0;
    private int cols = 7;
    private int cellW, cellH, gridX, gridY, gridH, rowsVisible;
    private int clearX, clearY, clearW;

    public ProjectorCardScreen(BlockPos pos) {
        super(Component.literal("Card Display"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        cellW = Math.round(CardRenderer.CARD_W * SCALE);
        cellH = Math.round(CardRenderer.CARD_H * SCALE);

        // as many columns as comfortably fit, so the grid never runs off-screen
        cols = Math.max(1, Math.min(9, (width - 80 + GAP) / (cellW + GAP)));
        int gridW = cols * (cellW + GAP) - GAP;
        gridX = (width - gridW) / 2;
        gridY = HEADER_H;
        gridH = height - gridY - FOOTER_H - 8;
        rowsVisible = Math.max(1, (gridH + GAP) / (cellH + GAP));

        int sbW = 180;
        search = new EditBox(font, (width - sbW) / 2, 56, sbW, 16, Component.literal("Search"));
        search.setHint(Component.literal("search your cards"));
        search.setResponder(s -> { scrollRow = 0; rebuild(); });
        addWidget(search);

        clearW = font.width("Clear display") + 20;
        clearX = width - clearW - 16;
        clearY = height - 29;

        rebuild();
    }

    private void rebuild() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        owned.clear();
        for (MobCard c : MobCards.ALL) {
            if (!ClientCollection.has(c.id())) continue;
            if (!q.isEmpty() && !c.displayName().toLowerCase(Locale.ROOT).contains(q)) continue;
            owned.add(c);
        }
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll()));
    }

    private int rowCount() {
        return Math.max(1, (owned.size() + cols - 1) / cols);
    }

    private int maxScroll() {
        return Math.max(0, rowCount() - rowsVisible);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, BG);

        drawHeader(g);
        search.render(g, mouseX, mouseY, partialTick);

        MobCard hovered = drawGrid(g, mouseX, mouseY);
        drawScrollbar(g);
        drawFooter(g, hovered, mouseX, mouseY);
    }

    /** Eyebrow, headline, hairline rule — the whole typographic hierarchy. */
    private void drawHeader(GuiGraphics g) {
        g.fill(0, 0, width, HEADER_H - 10, PANEL);
        g.fill(0, HEADER_H - 10, width, HEADER_H - 9, HAIRLINE);

        var pose = g.pose();
        // eyebrow: small, wide-tracked, dim
        pose.pushPose();
        pose.translate(width / 2f, 14f, 0);
        pose.scale(0.85f, 0.85f, 1f);
        tracked(g, "CARD DISPLAY", 0, 0, ACCENT, 3);
        pose.popPose();

        // headline: large, sentence case, calm
        String title = "Choose a card to project";
        pose.pushPose();
        pose.translate(width / 2f, 26f, 0);
        pose.scale(1.7f, 1.7f, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, TEXT, false);
        pose.popPose();

        // a short accent rule under the headline, not a full-width band
        int ruleW = Math.min(120, width / 4);
        g.fill(width / 2 - ruleW / 2, 46, width / 2 + ruleW / 2, 47, ACCENT);
    }

    /** The owned-card grid. Returns the card under the cursor, or null. */
    private MobCard drawGrid(GuiGraphics g, int mouseX, int mouseY) {
        if (owned.isEmpty()) {
            String empty = search != null && !search.getValue().isEmpty()
                    ? "No cards match that search."
                    : "You haven't collected any cards yet — go hunt some mobs.";
            g.drawCenteredString(font, empty, width / 2, gridY + 40, TEXT_DIM);
            return null;
        }
        MobCard hovered = null;
        int start = scrollRow * cols;
        for (int i = 0; i < rowsVisible * cols; i++) {
            int idx = start + i;
            if (idx >= owned.size()) break;
            MobCard card = owned.get(idx);
            int col = i % cols, row = i / cols;
            int cx = gridX + col * (cellW + GAP);
            int cy = gridY + row * (cellH + GAP);
            boolean hover = mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + cellH;
            if (hover) hovered = card;

            g.fill(cx - 3, cy - 3, cx + cellW + 3, cy + cellH + 3, hover ? PANEL_HI : PANEL);
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
            boolean foilPreview = hasShiftDown() && ClientCollection.hasFoil(card.id());
            int level = ClientCollection.displayLevel(card.id(), foilPreview);
            CardRenderer.renderCard(g, font, card, level, cx, cy, SCALE, mouseX, mouseY, mob, foilPreview, false);
            if (ClientCollection.hasFoil(card.id())) {
                g.fill(cx + cellW - 6, cy + 1, cx + cellW - 1, cy + 6, FOIL_ACCENT);
            }
            if (hover) {
                g.renderOutline(cx - 3, cy - 3, cellW + 6, cellH + 6, foilPreview ? FOIL_ACCENT : ACCENT);
            }
        }
        return hovered;
    }

    /** A slim track on the right of the grid, only while there is more to see. */
    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;
        int x = gridX + cols * (cellW + GAP) - GAP + 10;
        int y0 = gridY;
        int y1 = gridY + rowsVisible * (cellH + GAP) - GAP;
        g.fill(x, y0, x + 3, y1, HAIRLINE);
        int span = y1 - y0;
        int thumb = Math.max(18, span * rowsVisible / rowCount());
        int top = y0 + (span - thumb) * scrollRow / max;
        g.fill(x, top, x + 3, top + thumb, ACCENT);
    }

    /**
     * Left slot: what a click does, plus the collection count. Right slot: the
     * Clear button. Fixed slots, so nothing can ever overlap.
     */
    private void drawFooter(GuiGraphics g, MobCard hovered, int mouseX, int mouseY) {
        int top = height - FOOTER_H;
        g.fill(0, top, width, height, PANEL);
        g.fill(0, top, width, top + 1, HAIRLINE);

        if (hovered != null) {
            g.drawString(font, hovered.displayName(), 16, top + 10, TEXT, false);
            String sub = hovered.tier().label()
                    + (ClientCollection.hasFoil(hovered.id()) ? "  ·  holographic owned" : "");
            g.drawString(font, sub, 16, top + 24, TEXT_DIM, false);
        } else {
            g.drawString(font, "Click to display  ·  Shift-click for the holographic version",
                    16, top + 10, TEXT_DIM, false);
            g.drawString(font, owned.size() + " of " + MobCards.ALL.size()
                            + " cards collected  ·  ESC to cancel",
                    16, top + 24, TEXT_FAINT, false);
        }

        boolean hover = mouseX >= clearX && mouseX < clearX + clearW
                && mouseY >= clearY && mouseY < clearY + 18;
        g.fill(clearX, clearY, clearX + clearW, clearY + 18, hover ? 0xFF5A2530 : 0xFF23272F);
        g.renderOutline(clearX, clearY, clearW, 18, hover ? 0xFFE06B78 : HAIRLINE);
        g.drawString(font, "Clear display", clearX + 10, clearY + 5,
                hover ? 0xFFFFD9DD : 0xFFC98F98, false);
    }

    /** Draw text with extra tracking between glyphs, centred on x. */
    private void tracked(GuiGraphics g, String text, int x, int y, int color, int gap) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += font.width(String.valueOf(text.charAt(i))) + gap;
        }
        total -= gap;
        int cx = x - total / 2;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            g.drawString(font, ch, cx, y, color, false);
            cx += font.width(ch) + gap;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (search.mouseClicked(mouseX, mouseY, button)) {
            setFocused(search);
            return true;
        }
        if (button == 0 && mouseX >= clearX && mouseX < clearX + clearW
                && mouseY >= clearY && mouseY < clearY + 18) {
            link("", false);
            return true;
        }
        if (button == 0) {
            int start = scrollRow * cols;
            for (int i = 0; i < rowsVisible * cols; i++) {
                int idx = start + i;
                if (idx >= owned.size()) break;
                int col = i % cols, row = i / cols;
                int cx = gridX + col * (cellW + GAP);
                int cy = gridY + row * (cellH + GAP);
                if (mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + cellH) {
                    MobCard card = owned.get(idx);
                    boolean foil = hasShiftDown() && ClientCollection.hasFoil(card.id());
                    link(card.id(), foil);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void link(String mobId, boolean foil) {
        PacketDistributor.sendToServer(new LinkDisplayPayload(pos, mobId, foil));
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), foil ? 1.3f : 1.0f));
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (dy < 0) scrollRow = Math.min(maxScroll(), scrollRow + 1);
        else if (dy > 0) scrollRow = Math.max(0, scrollRow - 1);
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (search.isFocused()) return search.charTyped(c, modifiers);
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (search.isFocused() && key != 256) {
            return search.keyPressed(key, scan, mods) || super.keyPressed(key, scan, mods);
        }
        return super.keyPressed(key, scan, mods);
    }
}
