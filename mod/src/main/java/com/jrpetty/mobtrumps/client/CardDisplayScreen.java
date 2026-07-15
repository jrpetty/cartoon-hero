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
 * Modern picker for a card display: choose any card you own to project onto it.
 * Click to display, shift-click for the holographic version, or clear it.
 * The card never leaves your collection — this only links which card is shown.
 */
public class CardDisplayScreen extends Screen {

    private static final float SCALE = 0.34f;
    private static final int COLS = 7;
    private static final int GAP = 6;

    private static final int BG = 0xF00E0F12;
    private static final int PANEL = 0xFF16181D;
    private static final int PANEL_EDGE = 0xFF2A2E37;
    private static final int ACCENT = 0xFF3FA9F5;
    private static final int FOIL_ACCENT = 0xFFB57BFF;

    private final BlockPos pos;
    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final List<MobCard> owned = new ArrayList<>();

    private EditBox search;
    private int scrollRow = 0;
    private int cellW, cellH, gridX, gridY, gridW, gridH, rowsVisible;
    private int clearX, clearY, clearW;

    public CardDisplayScreen(BlockPos pos) {
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

        gridW = COLS * (cellW + GAP) - GAP;
        gridX = (width - gridW) / 2;
        gridY = 74;
        gridH = height - gridY - 44;
        rowsVisible = Math.max(1, (gridH + GAP) / (cellH + GAP));

        int sbW = 160;
        search = new EditBox(font, (width - sbW) / 2, 44, sbW, 16, Component.literal("Search"));
        search.setHint(Component.literal("search your cards..."));
        search.setResponder(s -> { scrollRow = 0; rebuild(); });
        addWidget(search);

        clearW = font.width("Clear display") + 16;
        clearX = width / 2 - clearW - 6;
        clearY = height - 30;

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
        int rows = Math.max(1, (owned.size() + COLS - 1) / COLS);
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, rows - rowsVisible)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, BG);

        // header
        g.fill(0, 0, width, 34, PANEL);
        g.fill(0, 34, width, 35, ACCENT);
        String title = "LINK A CARD TO YOUR DISPLAY";
        g.drawString(font, title, (width - font.width(title)) / 2, 13, 0xFFFFFFFF, false);

        search.render(g, mouseX, mouseY, partialTick);

        if (owned.isEmpty()) {
            g.drawCenteredString(font, "You haven't collected any cards yet — hunt some mobs!",
                    width / 2, gridY + 30, 0xFFB9BFC9);
        }

        // grid of owned cards
        int start = scrollRow * COLS;
        for (int i = 0; i < rowsVisible * COLS; i++) {
            int idx = start + i;
            if (idx >= owned.size()) break;
            MobCard card = owned.get(idx);
            int col = i % COLS, row = i / COLS;
            int cx = gridX + col * (cellW + GAP);
            int cy = gridY + row * (cellH + GAP);
            boolean hover = mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + cellH;

            g.fill(cx - 2, cy - 2, cx + cellW + 2, cy + cellH + 2, hover ? 0xFF20242C : PANEL);
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
            boolean foilPreview = hasShiftDown() && ClientCollection.hasFoil(card.id());
            CardRenderer.renderCard(g, font, card, cx, cy, SCALE, mouseX, mouseY, mob, foilPreview, false);
            if (ClientCollection.hasFoil(card.id())) {
                g.fill(cx + cellW - 6, cy + 1, cx + cellW - 1, cy + 6, FOIL_ACCENT);
            }
            if (hover) {
                g.renderOutline(cx - 2, cy - 2, cellW + 4, cellH + 4, foilPreview ? FOIL_ACCENT : ACCENT);
            }
        }

        // footer
        g.fill(0, height - 40, width, height - 39, PANEL_EDGE);
        boolean clearHover = mouseX >= clearX && mouseX < clearX + clearW
                && mouseY >= clearY && mouseY < clearY + 18;
        g.fill(clearX, clearY, clearX + clearW, clearY + 18, clearHover ? 0xFF5A2530 : 0xFF2A2E37);
        g.drawString(font, "Clear display", clearX + 8, clearY + 5, 0xFFF0A0A8, false);

        String hint = "Click to display  ·  Shift-click for the holographic version  ·  ESC to cancel";
        g.drawString(font, hint, width / 2 - font.width(hint) / 2, clearY + 5, 0xFFB9BFC9, false);
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
            int start = scrollRow * COLS;
            for (int i = 0; i < rowsVisible * COLS; i++) {
                int idx = start + i;
                if (idx >= owned.size()) break;
                int col = i % COLS, row = i / COLS;
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
        int rows = Math.max(1, (owned.size() + COLS - 1) / COLS);
        int maxScroll = Math.max(0, rows - rowsVisible);
        if (dy < 0) scrollRow = Math.min(maxScroll, scrollRow + 1);
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
