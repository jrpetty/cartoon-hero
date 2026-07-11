package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Full-size card view in the style of a classic Top Trumps card:
 * kraft border, mob name up top, the live 3D mob as the portrait,
 * alternating blue/green stat rows and a total rating row.
 * Opened by right-clicking a Mob Card.
 */
public class MobCardScreen extends Screen {

    private static final int KRAFT = 0xFF9A7B54;
    private static final int KRAFT_DARK = 0xFF5F4A32;
    private static final int FACE = 0xFFFAF6EC;
    private static final int INK = 0xFF35281A;
    private static final int ROW_BLUE = 0xFFCFE9F6;
    private static final int ROW_GREEN = 0xFFD8EECD;
    private static final int ROW_GOLD = 0xFFF3E2A7;
    private static final int LABEL_BLUE = 0xFF1C4B6B;
    private static final int LABEL_GREEN = 0xFF2C5E2E;
    private static final int PORTRAIT_TOP = 0xFFCFE4F2;
    private static final int PORTRAIT_BOTTOM = 0xFFE8F2D9;
    private static final int FACT_BG = 0xFFEDE3CE;

    private static final int CARD_W = 170;
    private static final int CARD_H = 236;
    private static final int ROW_H = 13;

    private final MobCard card;
    private final Screen parent;
    private LivingEntity portrait;
    private boolean portraitResolved;

    public MobCardScreen(MobCard card) {
        this(card, null);
    }

    public MobCardScreen(MobCard card, Screen parent) {
        super(Component.literal(card.displayName()));
        this.card = card;
        this.parent = parent;
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = (width - CARD_W) / 2;
        int y = (height - CARD_H) / 2;

        // kraft card with a dark outline and cream face
        g.fill(x - 2, y - 2, x + CARD_W + 2, y + CARD_H + 2, KRAFT_DARK);
        g.fill(x, y, x + CARD_W, y + CARD_H, KRAFT);
        g.fill(x + 6, y + 6, x + CARD_W - 6, y + CARD_H - 6, FACE);

        // name, 1.5x, centred
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + CARD_W / 2f, y + 11f, 0);
        pose.scale(1.5f, 1.5f, 1f);
        String name = card.displayName();
        g.drawString(font, name, -font.width(name) / 2, 0, INK, false);
        pose.popPose();

        // tier line
        String tier = "★ " + card.tier().label() + " ★";
        g.drawString(font, tier, x + (CARD_W - font.width(tier)) / 2, y + 27, tierPrintColor(), false);

        // portrait: the live mob, rendered into a sky-to-grass box
        int px1 = x + 12, py1 = y + 38, px2 = x + CARD_W - 12, py2 = y + 116;
        g.fillGradient(px1, py1, px2, py2, PORTRAIT_TOP, PORTRAIT_BOTTOM);
        g.renderOutline(px1 - 1, py1 - 1, px2 - px1 + 2, py2 - py1 + 2, KRAFT_DARK);
        LivingEntity mob = portraitEntity();
        if (mob != null) {
            int scale = (int) Math.max(8, Math.min(34, 46 / Math.max(1.4f, mob.getBbHeight())));
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        g, px1, py1, px2, py2, scale, 0.0625F, mouseX, mouseY, mob);
            } catch (Exception ignored) {
                // a misbehaving renderer must not crash the card view
            }
        }

        // stat table: alternating blue/green rows, values right-aligned
        int rowY = y + 121;
        int total = 0;
        Stat[] stats = Stat.values();
        for (int i = 0; i < stats.length; i++) {
            Stat stat = stats[i];
            total += card.stat(stat);
            boolean blue = i % 2 == 0;
            drawRow(g, x, rowY, blue ? ROW_BLUE : ROW_GREEN, blue ? LABEL_BLUE : LABEL_GREEN,
                    stat.label.toUpperCase(Locale.ROOT), card.stat(stat));
            rowY += ROW_H;
        }
        drawRow(g, x, rowY, ROW_GOLD, INK, "MOB RATING", total);
        rowY += ROW_H;

        // fact file strip
        int fy1 = rowY + 4;
        int fy2 = y + CARD_H - 10;
        g.fill(x + 12, fy1, x + CARD_W - 12, fy2, FACT_BG);
        g.renderOutline(x + 11, fy1 - 1, CARD_W - 22 + 2, fy2 - fy1 + 2, KRAFT_DARK);
        String fact = "Card " + (MobCards.ALL.indexOf(card) + 1) + " of " + MobCards.ALL.size()
                + " · Mob Trumps";
        g.drawString(font, fact, x + (CARD_W - font.width(fact)) / 2, fy1 + 4, KRAFT_DARK, false);

        String hint = "Press ESC to close";
        g.drawString(font, hint, (width - font.width(hint)) / 2, y + CARD_H + 8, 0xFFAAAAAA, true);
    }

    private void drawRow(GuiGraphics g, int cardX, int rowY, int bg, int labelColor,
                         String label, int value) {
        g.fill(cardX + 12, rowY, cardX + CARD_W - 12, rowY + ROW_H - 1, bg);
        g.drawString(font, label, cardX + 16, rowY + 3, labelColor, false);
        String v = String.valueOf(value);
        g.drawString(font, v, cardX + CARD_W - 16 - font.width(v), rowY + 3, INK, false);
    }

    /** Chat colours glow on dark backgrounds but wash out on the cream card
     *  face, so the printed tier line uses darker ink versions of them. */
    private int tierPrintColor() {
        return switch (card.tier()) {
            case COMMON -> 0xFF6B6B6B;
            case UNCOMMON -> 0xFF3D8B3D;
            case RARE -> 0xFF1C7FA8;
            case EPIC -> 0xFF8746C9;
            case LEGENDARY -> 0xFFA67C00;
        };
    }

    /** Lazily spawn a client-side copy of the mob to pose for the portrait. */
    private LivingEntity portraitEntity() {
        if (portraitResolved) {
            return portrait;
        }
        portraitResolved = true;
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        // sulfur cube has no vanilla entity; borrow its magma cousin for the photo
        String entityId = card.id().equals("sulfur_cube") ? "magma_cube" : card.id();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                .getOptional(ResourceLocation.withDefaultNamespace(entityId))
                .orElse(null);
        if (type == null) {
            return null;
        }
        try {
            Entity entity = type.create(minecraft.level);
            if (entity instanceof LivingEntity living) {
                portrait = living;
            }
        } catch (Exception ignored) {
            // no portrait is better than no card view
        }
        return portrait;
    }
}
