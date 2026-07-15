package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;
import java.util.Map;

/**
 * Draws a full Top Trumps style mob card at any position and scale, so the
 * single-card screen and the collection book pages stay pixel-identical.
 * All card-local coordinates live in a 170x236 space.
 */
public final class CardRenderer {

    public static final int CARD_W = 170;
    public static final int CARD_H = 236;

    public static final int KRAFT = 0xFF9A7B54;
    public static final int KRAFT_DARK = 0xFF5F4A32;
    public static final int KRAFT_BACK = 0xFF7A5F3E;
    public static final int FACE = 0xFFFAF6EC;
    public static final int INK = 0xFF35281A;
    private static final int ROW_BLUE = 0xFFCFE9F6;
    private static final int ROW_GREEN = 0xFFD8EECD;
    private static final int ROW_GOLD = 0xFFF3E2A7;
    private static final int LABEL_BLUE = 0xFF1C4B6B;
    private static final int LABEL_GREEN = 0xFF2C5E2E;
    private static final int PORTRAIT_TOP = 0xFFCFE4F2;
    private static final int PORTRAIT_BOTTOM = 0xFFE8F2D9;
    private static final int FACT_BG = 0xFFEDE3CE;
    private static final int BACK_DOT = 0xFF6A5236;
    private static final int BACK_TEXT = 0xFFD8C9A8;
    private static final int BOOST_INK = 0xFF1E7A32;   // boosted stat value on the cream face
    private static final int BOOST_GREEN = 0xFF35B34A;  // the "+N" tag

    private static final int ROW_H = 13;

    private CardRenderer() {
    }

    /** Draw the front of a card. Pass the mob for the live portrait, or null. */
    public static void renderCard(GuiGraphics g, Font font, MobCard card, int x, int y,
                                  float scale, int mouseX, int mouseY, LivingEntity mob) {
        renderCard(g, font, card, x, y, scale, mouseX, mouseY, mob, false);
    }

    /** Draw the front of a card, optionally with the holographic foil sheen. */
    public static void renderCard(GuiGraphics g, Font font, MobCard card, int x, int y,
                                  float scale, int mouseX, int mouseY, LivingEntity mob, boolean foil) {
        renderCard(g, font, card, x, y, scale, mouseX, mouseY, mob, foil, true);
    }

    /**
     * Draw the front of a card. When {@code followMouse} is false the portrait
     * mob poses straight ahead instead of tracking the cursor — used for book
     * grid cards so only the hovered card comes alive.
     */
    public static void renderCard(GuiGraphics g, Font font, MobCard card, int x, int y,
                                  float scale, int mouseX, int mouseY, LivingEntity mob,
                                  boolean foil, boolean followMouse) {
        renderCard(g, font, card, foil ? 1 : 0, x, y, scale, mouseX, mouseY, mob, foil, followMouse);
    }

    /**
     * Draw the front of a card at a given upgrade {@code level} (0 = base card,
     * 1 = holographic, up to 3). The stat table shows the boosted values with a
     * green "+N" tag on every stat the upgrade lifted, a HOLO badge with a pip
     * per level, and a foil sheen whose richness scales with the level.
     */
    public static void renderCard(GuiGraphics g, Font font, MobCard baseCard, int level, int x, int y,
                                  float scale, int mouseX, int mouseY, LivingEntity mob,
                                  boolean foil, boolean followMouse) {
        MobCard card = baseCard.upgraded(level);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);

        g.fill(-2, -2, CARD_W + 2, CARD_H + 2, KRAFT_DARK);
        g.fill(0, 0, CARD_W, CARD_H, KRAFT);
        g.fill(6, 6, CARD_W - 6, CARD_H - 6, FACE);

        // name, 1.5x, centred
        pose.pushPose();
        pose.translate(CARD_W / 2f, 11f, 0);
        pose.scale(1.5f, 1.5f, 1f);
        String name = card.displayName();
        g.drawString(font, name, -font.width(name) / 2, 0, INK, false);
        pose.popPose();

        // tier line in print-friendly ink
        String tier = "★ " + card.tier().label() + " ★";
        g.drawString(font, tier, (CARD_W - font.width(tier)) / 2, 27, tierPrintColor(card), false);

        // portrait backdrop (the mob itself is drawn later in screen space)
        g.fillGradient(12, 38, CARD_W - 12, 116, PORTRAIT_TOP, PORTRAIT_BOTTOM);
        g.renderOutline(11, 37, CARD_W - 22, 116 - 38 + 2, KRAFT_DARK);

        // stat table — boosted values with a green +N tag on upgraded stats
        int rowY = 121;
        int total = 0;
        Stat[] stats = Stat.values();
        for (int i = 0; i < stats.length; i++) {
            Stat stat = stats[i];
            int value = card.stat(stat);
            total += value;
            boolean blue = i % 2 == 0;
            drawRow(g, font, rowY, blue ? ROW_BLUE : ROW_GREEN, blue ? LABEL_BLUE : LABEL_GREEN,
                    stat.label.toUpperCase(Locale.ROOT), value, value - baseCard.stat(stat));
            rowY += ROW_H;
        }
        drawRow(g, font, rowY, ROW_GOLD, INK, "MOB RATING", total, 0);
        rowY += ROW_H;

        // fact file strip
        g.fill(12, rowY + 3, CARD_W - 12, CARD_H - 9, FACT_BG);
        g.renderOutline(11, rowY + 2, CARD_W - 22, CARD_H - 9 - rowY - 3 + 2, KRAFT_DARK);
        String fact = "Card " + (MobCards.ordinal(card.id()) + 1) + " of " + MobCards.ALL.size()
                + " · Mob Trumps";
        drawCenteredFit(g, font, fact, CARD_W / 2f, rowY + 5, CARD_W - 28, KRAFT_DARK);

        // holographic foil sheen: shifting rainbow bands + sweep, richer per level
        if (foil || level > 0) {
            int lvl = Math.max(1, level);
            long t = System.currentTimeMillis();
            long period = Math.max(1400L, 3200L - (lvl - 1) * 700L);
            float phase = (t % period) / (float) period;
            int bandAlpha = 0x1C + lvl * 0x10; // 0x2C / 0x3C / 0x4C
            float sat = Math.min(1f, 0.55f + lvl * 0.15f);
            for (int by = 8; by < CARD_H - 8; by += 3) {
                float h = ((by / (float) CARD_H) + phase) % 1f;
                int band = (bandAlpha << 24) | (hsvToRgb(h, sat, 1f) & 0x00FFFFFF);
                g.fill(8, by, CARD_W - 8, by + 2, band);
            }
            // bright sweeping highlight, brighter at higher levels
            int sweep = 8 + (int) (phase * (CARD_W - 16));
            int sweepA = (0x2A + lvl * 0x10) << 24;
            int half = 2 + lvl;
            g.fill(Math.max(8, sweep - half), 8, Math.min(CARD_W - 8, sweep + half), CARD_H - 8,
                    0x00FFFFFF | sweepA);
        }

        // HOLO badge with a pip per level, top-left corner
        if (level > 0) {
            drawLevelBadge(g, font, level);
        }

        pose.popPose();

        // the live mob, rendered in screen space so its scissor box stays true
        if (mob != null) {
            int px1 = x + Math.round(13 * scale);
            int py1 = y + Math.round(39 * scale);
            int px2 = x + Math.round((CARD_W - 13) * scale);
            int py2 = y + Math.round(115 * scale);
            int entityScale = Math.max(4, Math.round(
                    Math.min(34f, 46f / Math.max(1.4f, mob.getBbHeight())) * scale));
            // an idle mob gazes at its own portrait centre instead of the cursor
            int lookX = followMouse ? mouseX : (px1 + px2) / 2;
            int lookY = followMouse ? mouseY : (py1 + py2) / 2 - Math.round(10 * scale);
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        g, px1, py1, px2, py2, entityScale, 0.0625F, lookX, lookY, mob);
            } catch (Exception ignored) {
                // a misbehaving renderer must not crash the UI
            }
        }
    }

    /** Draw the back of a card — used for mobs not yet collected. */
    public static void renderBack(GuiGraphics g, Font font, int x, int y, float scale) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);

        g.fill(-2, -2, CARD_W + 2, CARD_H + 2, KRAFT_DARK);
        g.fill(0, 0, CARD_W, CARD_H, KRAFT);
        g.fill(6, 6, CARD_W - 6, CARD_H - 6, KRAFT_BACK);
        g.renderOutline(10, 10, CARD_W - 20, CARD_H - 20, KRAFT_DARK);

        // quilted dot pattern
        for (int py = 18; py < CARD_H - 20; py += 14) {
            for (int px = 18 + ((py / 14) % 2) * 7; px < CARD_W - 20; px += 14) {
                g.fill(px, py, px + 3, py + 3, BACK_DOT);
            }
        }

        // central medallion with a big question mark
        g.fill(60, 86, CARD_W - 60, 136, KRAFT_DARK);
        g.fill(63, 89, CARD_W - 63, 133, FACE);
        pose.pushPose();
        pose.translate(CARD_W / 2f, 98f, 0);
        pose.scale(3f, 3f, 1f);
        g.drawString(font, "?", -font.width("?") / 2, 0, KRAFT_DARK, false);
        pose.popPose();

        String label = "MOB TRUMPS";
        g.drawString(font, label, (CARD_W - font.width(label)) / 2, 210, BACK_TEXT, false);

        pose.popPose();
    }

    /** Chat colours glow on dark backgrounds but wash out on the cream card
     *  face, so the printed tier line uses darker ink versions of them. */
    public static int tierPrintColor(MobCard card) {
        return switch (card.tier()) {
            case COMMON -> 0xFF6B6B6B;
            case UNCOMMON -> 0xFF3D8B3D;
            case RARE -> 0xFF1C7FA8;
            case EPIC -> 0xFF8746C9;
            case LEGENDARY -> 0xFFA67C00;
        };
    }

    /** Fetch (and cache) a client-side mob to pose for a card's portrait. */
    public static LivingEntity portraitEntity(Minecraft minecraft, MobCard card,
                                              Map<String, LivingEntity> cache) {
        if (cache.containsKey(card.id())) {
            return cache.get(card.id());
        }
        LivingEntity result = null;
        if (minecraft != null && minecraft.level != null) {
            // sulfur cube has no vanilla entity; borrow its magma cousin
            String entityId = card.id().equals("sulfur_cube") ? "magma_cube" : card.id();
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                    .getOptional(ResourceLocation.withDefaultNamespace(entityId))
                    .orElse(null);
            if (type != null) {
                try {
                    Entity entity = type.create(minecraft.level);
                    if (entity instanceof LivingEntity living) {
                        result = living;
                    }
                } catch (Exception ignored) {
                    // no portrait is better than no card
                }
            }
        }
        cache.put(card.id(), result);
        return result;
    }

    /** Minimal HSV→packed-RGB (0xRRGGBB) for the foil rainbow. */
    private static int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6f);
        float f = h * 6f - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    /** Draw text centred on centerX, shrinking it to fit maxWidth if needed. */
    private static void drawCenteredFit(GuiGraphics g, Font font, String text, float centerX,
                                        int y, int maxWidth, int color) {
        int w = font.width(text);
        if (w <= maxWidth) {
            g.drawString(font, text, Math.round(centerX - w / 2f), y, color, false);
            return;
        }
        float scale = maxWidth / (float) w;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(centerX, y + (font.lineHeight * (1 - scale)) / 2f, 0);
        pose.scale(scale, scale, 1f);
        g.drawString(font, text, -w / 2, 0, color, false);
        pose.popPose();
    }

    private static void drawRow(GuiGraphics g, Font font, int rowY, int bg, int labelColor,
                                String label, int value, int delta) {
        g.fill(12, rowY, CARD_W - 12, rowY + ROW_H - 1, bg);
        g.drawString(font, label, 16, rowY + 3, labelColor, false);
        String v = String.valueOf(value);
        int vx = CARD_W - 16 - font.width(v);
        g.drawString(font, v, vx, rowY + 3, delta > 0 ? BOOST_INK : INK, false);
        if (delta > 0) {
            // a small green "+N" tag just left of the boosted value
            String d = "+" + delta;
            float ds = 0.75f;
            int dw = Math.round(font.width(d) * ds);
            var pose = g.pose();
            pose.pushPose();
            pose.translate(vx - dw - 3f, rowY + 3.5f, 0);
            pose.scale(ds, ds, 1f);
            g.drawString(font, d, 0, 0, BOOST_GREEN, false);
            pose.popPose();
        }
    }

    /** A compact "HOLO" pill with a gold pip per upgrade level, drawn top-left. */
    private static void drawLevelBadge(GuiGraphics g, Font font, int level) {
        String label = "HOLO";
        int pad = 3;
        int lw = font.width(label);
        int bw = pad * 2 + lw + 3 + level * 4;
        int bx = 9, by = 9, bh = 11;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF1B0E24);
        g.fillGradient(bx, by, bx + bw, by + bh, 0xFF5B2C87, 0xFF33184D);
        g.fill(bx, by, bx + bw, by + 1, 0xFFB98BF0);
        g.drawString(font, label, bx + pad, by + 2, 0xFFF1DEFF, false);
        for (int i = 0; i < level; i++) {
            int dx = bx + pad + lw + 2 + i * 4;
            g.fill(dx, by + 3, dx + 3, by + bh - 3, 0xFFFFD54A);
            g.fill(dx, by + 3, dx + 3, by + 4, 0xFFFFF0B0);
        }
    }
}
