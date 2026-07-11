package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The collection book: a 9x9 grid of all 81 mob cards. Collected cards show
 * the card item in a tier-coloured slot; missing ones are dark "?" slots.
 * Clicking a collected card opens its full Top Trumps card view.
 */
public class CollectionBookScreen extends Screen {

    private static final int COLS = 9;
    private static final int CELL = 20;
    private static final int GRID_W = COLS * CELL;

    private static final int KRAFT = 0xFF9A7B54;
    private static final int KRAFT_DARK = 0xFF5F4A32;
    private static final int FACE = 0xFFFAF6EC;
    private static final int INK = 0xFF35281A;
    private static final int SLOT_EMPTY = 0xFF4A4038;
    private static final int BAR_BG = 0xFF6B5B43;
    private static final int BAR_FILL = 0xFF55A82F;

    private final List<ItemStack> cardStacks = new ArrayList<>();

    public CollectionBookScreen() {
        super(Component.literal("Mob Collection"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        if (cardStacks.isEmpty()) {
            for (MobCard card : MobCards.ALL) {
                cardStacks.add(MobCardItem.stackOf(card));
            }
        }
    }

    private int panelX() {
        return (width - (GRID_W + 24)) / 2;
    }

    private int panelY() {
        return (height - (COLS * CELL + 52)) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int px = panelX(), py = panelY();
        int panelW = GRID_W + 24, panelH = COLS * CELL + 52;

        g.fill(px - 2, py - 2, px + panelW + 2, py + panelH + 2, KRAFT_DARK);
        g.fill(px, py, px + panelW, py + panelH, KRAFT);
        g.fill(px + 6, py + 6, px + panelW - 6, py + panelH - 6, FACE);

        String titleText = "MOB COLLECTION";
        g.drawString(font, titleText, px + (panelW - font.width(titleText)) / 2, py + 12, INK, false);
        String progress = ClientCollection.count() + " / " + MobCards.ALL.size();
        g.drawString(font, progress, px + (panelW - font.width(progress)) / 2, py + 23, KRAFT_DARK, false);

        // progress bar
        int barX = px + 12, barY = py + 33, barW = panelW - 24;
        g.fill(barX, barY, barX + barW, barY + 3, BAR_BG);
        int fillW = (int) (barW * (ClientCollection.count() / (float) MobCards.ALL.size()));
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + 3, BAR_FILL);
        }

        int gridX = px + 12, gridY = py + 40;
        MobCard hovered = null;
        boolean hoveredCollected = false;

        for (int i = 0; i < MobCards.ALL.size(); i++) {
            MobCard card = MobCards.ALL.get(i);
            int cx = gridX + (i % COLS) * CELL;
            int cy = gridY + (i / COLS) * CELL;
            boolean collected = ClientCollection.has(card.id());

            if (collected) {
                g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, tierColor(card));
                g.renderItem(cardStacks.get(i), cx + 1, cy + 1);
            } else {
                g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, SLOT_EMPTY);
                g.drawString(font, "?", cx + 7, cy + 5, 0xFF8B8074, false);
            }

            if (mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2) {
                hovered = card;
                hoveredCollected = collected;
                g.renderOutline(cx - 1, cy - 1, CELL, CELL, 0xFFFFFFFF);
            }
        }

        if (hovered != null) {
            g.renderComponentTooltip(font, tooltipFor(hovered, hoveredCollected), mouseX, mouseY);
        }
    }

    private List<Component> tooltipFor(MobCard card, boolean collected) {
        List<Component> lines = new ArrayList<>();
        if (!collected) {
            lines.add(Component.literal("???").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.literal("Open packs to find this mob.").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.literal(card.displayName())
                .withStyle(MobCardItem.tierColor(card.tier()), ChatFormatting.BOLD));
        lines.add(Component.literal("★ " + card.tier().label() + " ★")
                .withStyle(MobCardItem.tierColor(card.tier()), ChatFormatting.ITALIC));
        for (Stat stat : Stat.values()) {
            lines.add(Component.literal(stat.label + ": ").withStyle(MobCardItem.statColor(stat))
                    .append(Component.literal(String.valueOf(card.stat(stat)))
                            .withStyle(ChatFormatting.WHITE)));
        }
        lines.add(Component.literal("Click to view the card").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int gridX = panelX() + 12, gridY = panelY() + 40;
            for (int i = 0; i < MobCards.ALL.size(); i++) {
                MobCard card = MobCards.ALL.get(i);
                int cx = gridX + (i % COLS) * CELL;
                int cy = gridY + (i / COLS) * CELL;
                if (mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2
                        && ClientCollection.has(card.id()) && minecraft != null) {
                    minecraft.setScreen(new MobCardScreen(card, this));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int tierColor(MobCard card) {
        return switch (card.tier()) {
            case COMMON -> 0xFFB9B2A6;
            case UNCOMMON -> 0xFFA9D49B;
            case RARE -> 0xFF9CCBE0;
            case EPIC -> 0xFFC7A8E8;
            case LEGENDARY -> 0xFFEBD38A;
        };
    }
}
