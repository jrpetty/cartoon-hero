package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.DeckManager;
import com.jrpetty.mobtrumps.SetDeckPayload;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Build a battle deck from the cards you own. Click owned cards to add or
 * remove them (up to {@link DeckManager#MAX_DECK}); Save stores the deck so
 * "/mobtrumps battle deck" and deck duels use it.
 */
public class DeckBuilderScreen extends Screen {

    private static final float CARD_SCALE = 0.46f;
    private static final int ARROW_W = 18, ARROW_H = 14;

    private final Screen parent;
    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final List<MobCard> owned = new ArrayList<>();
    private final LinkedHashSet<String> deck = new LinkedHashSet<>();

    private int page;
    private int cols, rows, perPage, pageCount;
    private int cellW, cellH, gridX, gridY;
    private int panelX, panelY, panelW, panelH;
    private int prevX, prevY, nextX, nextY, saveX, saveY, saveW;

    public DeckBuilderScreen(Screen parent) {
        super(Component.literal("Deck Builder"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (parent != null && minecraft != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    protected void init() {
        super.init();
        owned.clear();
        for (MobCard c : MobCards.ALL) {
            if (ClientCollection.has(c.id())) owned.add(c);
        }
        if (deck.isEmpty()) {
            deck.addAll(ClientCollection.deck());
        }

        cellW = Math.round(CardRenderer.CARD_W * CARD_SCALE) + 10;
        cellH = Math.round(CardRenderer.CARD_H * CARD_SCALE) + 10;
        cols = Math.max(1, Math.min(5, (width - 70) / cellW));
        rows = Math.max(1, Math.min(3, (height - 120) / cellH));
        perPage = cols * rows;
        pageCount = Math.max(1, (owned.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pageCount - 1));

        panelW = Math.max(cols * cellW + 28, 300);
        panelH = 56 + rows * cellH + 26;
        panelX = (width - panelW) / 2;
        panelY = Math.max(8, (height - panelH) / 2);
        gridX = panelX + (panelW - cols * cellW) / 2;
        gridY = panelY + 50;

        int arrowY = panelY + panelH - 20;
        prevX = panelX + 12;
        prevY = arrowY;
        nextX = panelX + panelW - 12 - ARROW_W;
        nextY = arrowY;
        saveW = font.width("Save Deck") + 12;
        saveX = panelX + (panelW - saveW) / 2;
        saveY = arrowY - 1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, CardRenderer.KRAFT_DARK);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, CardRenderer.KRAFT);
        g.fill(panelX + 6, panelY + 6, panelX + panelW - 6, panelY + panelH - 6, CardRenderer.FACE);

        g.drawCenteredString(font, "BUILD YOUR DECK", width / 2, panelY + 12, CardRenderer.INK);
        String count = "Deck: " + deck.size() + " / " + DeckManager.MAX_DECK
                + (deck.size() < DeckManager.MIN_DECK ? "  (min " + DeckManager.MIN_DECK + ")" : "");
        g.drawCenteredString(font, count, width / 2, panelY + 26,
                deck.size() < DeckManager.MIN_DECK ? 0xFFB5502E : 0xFF3D8B3D);

        if (owned.isEmpty()) {
            g.drawCenteredString(font, "Collect some cards first!", width / 2, gridY + 20,
                    CardRenderer.KRAFT_DARK);
        }

        int start = page * perPage;
        int cw = Math.round(CardRenderer.CARD_W * CARD_SCALE);
        int ch = Math.round(CardRenderer.CARD_H * CARD_SCALE);
        for (int i = start; i < Math.min(start + perPage, owned.size()); i++) {
            MobCard card = owned.get(i);
            int slot = i - start;
            int cx = gridX + (slot % cols) * cellW + 6;
            int cy = gridY + (slot / cols) * cellH + 6;
            boolean inDeck = deck.contains(card.id());
            boolean hovered = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;

            g.fill(cx + 2, cy + 3, cx + cw + 3, cy + ch + 4, 0x44000000);
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
            boolean foil = ClientCollection.hasFoil(card.id());
            int level = ClientCollection.displayLevel(card.id(), foil);
            CardRenderer.renderCard(g, font, card, level, cx, cy, CARD_SCALE, mouseX, mouseY,
                    mob, foil, false);
            if (inDeck) {
                g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0xFF55E06A);
                g.renderOutline(cx - 1, cy - 1, cw + 2, ch + 2, 0xFF55E06A);
                // green corner tab as the "in deck" marker
                g.fill(cx + cw - 10, cy + 1, cx + cw - 1, cy + 5, 0xFF2E8B3A);
                g.fill(cx + cw - 6, cy + 1, cx + cw - 5, cy + 9, 0xFF2E8B3A);
            } else if (hovered) {
                g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0xFFF9D849);
            }
        }

        drawButton(g, prevX, prevY, ARROW_W, "<", page > 0, mouseX, mouseY);
        drawButton(g, nextX, nextY, ARROW_W, ">", page < pageCount - 1, mouseX, mouseY);
        boolean canSave = deck.size() >= DeckManager.MIN_DECK || deck.isEmpty();
        drawButton(g, saveX, saveY, saveW, "Save Deck", canSave, mouseX, mouseY);
        g.drawString(font, "Page " + (page + 1) + " / " + pageCount,
                panelX + panelW - 12 - ARROW_W - 62, panelY + panelH - 17, CardRenderer.KRAFT_DARK, false);

        String hint = "Click cards to add/remove · Save to keep your deck";
        g.drawString(font, hint, (width - font.width(hint)) / 2, panelY + panelH + 8, 0xFFAAAAAA, true);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, String label, boolean enabled,
                            int mouseX, int mouseY) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ARROW_H;
        g.fill(x, y, x + w, y + ARROW_H, enabled ? (hovered ? 0xFF6FB84A : 0xFF55A82F) : 0xFFCEC3AF);
        g.renderOutline(x, y, w, ARROW_H, CardRenderer.KRAFT_DARK);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 3,
                enabled ? 0xFFFFFFFF : 0xFF9A9083, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hit(mouseX, mouseY, prevX, prevY, ARROW_W) && page > 0) { flip(-1); return true; }
            if (hit(mouseX, mouseY, nextX, nextY, ARROW_W) && page < pageCount - 1) { flip(1); return true; }
            if (hit(mouseX, mouseY, saveX, saveY, saveW)) { save(); return true; }

            int start = page * perPage;
            int cw = Math.round(CardRenderer.CARD_W * CARD_SCALE);
            int ch = Math.round(CardRenderer.CARD_H * CARD_SCALE);
            for (int i = start; i < Math.min(start + perPage, owned.size()); i++) {
                MobCard card = owned.get(i);
                int slot = i - start;
                int cx = gridX + (slot % cols) * cellW + 6;
                int cy = gridY + (slot / cols) * cellH + 6;
                if (mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch) {
                    toggle(card.id());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggle(String id) {
        if (deck.contains(id)) {
            deck.remove(id);
            playClick(0.9f);
        } else if (deck.size() < DeckManager.MAX_DECK) {
            deck.add(id);
            playClick(1.2f);
        } else {
            playClick(0.6f);
        }
    }

    private void save() {
        PacketDistributor.sendToServer(new SetDeckPayload(new ArrayList<>(deck)));
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.1f));
        }
        onClose();
    }

    private boolean hit(double mx, double my, int x, int y, int w) {
        return mx >= x && mx < x + w && my >= y && my < y + ARROW_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        if (sy < 0 && page < pageCount - 1) { flip(1); return true; }
        if (sy > 0 && page > 0) { flip(-1); return true; }
        return super.mouseScrolled(mouseX, mouseY, sx, sy);
    }

    private void flip(int dir) {
        page += dir;
        playClick(1.0f);
    }

    private void playClick(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch));
        }
    }
}
