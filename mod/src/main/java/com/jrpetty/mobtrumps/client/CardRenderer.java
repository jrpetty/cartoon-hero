package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import com.mojang.math.Axis;
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

    // the card back: a deep indigo-to-plum field under a gold damask lattice,
    // with the creeper-face emblem set in a rotated medallion
    private static final int BACK_TOP = 0xFF241C4A;
    private static final int BACK_BOTTOM = 0xFF431C4C;
    private static final int BACK_GOLD = 0xFFE3C071;
    private static final int BACK_GOLD_DIM = 0xFF8C6F32;
    private static final int BACK_LATTICE = 0x3AE3C071;
    private static final int BACK_WELL = 0xFF130F2C;
    private static final int EMBLEM_GREEN = 0xFF4E9E48;
    private static final int EMBLEM_GREEN_LIT = 0xFF6DBF63;
    private static final int EMBLEM_INK = 0xFF13260F;
    private static final int BACK_TEXT = 0xFFEBD6A2;
    private static final int BOOST_INK = 0xFF1E7A32;   // boosted stat value on the cream face
    private static final int BOOST_GREEN = 0xFF35B34A;  // the "+N" tag

    // the card frame: dark slate edge, ivory border, gold pinline — escalating
    // to silver (Holo), gold (Holo II) and a prismatic frame (Holo III)
    private static final int EDGE_DARK = 0xFF20242C;
    private static final int BORDER_IVORY = 0xFFF2EEE3;
    private static final int PIN_GOLD = 0xFFD9B45B;

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

        drawFrame(g, Math.max(level, foil ? 1 : 0));

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

        // portrait backdrop: a scene themed to the mob's category (the mob
        // itself is drawn later in screen space, on top of this)
        CardBackground.draw(g, card.category(), 12, 38, CARD_W - 12, 116);
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
                    stat.label.toUpperCase(Locale.ROOT), value, value - baseCard.stat(stat),
                    stat.lowerWins);
            rowY += ROW_H;
        }
        drawRow(g, font, rowY, ROW_GOLD, INK, "MOB RATING", total, 0, false);
        rowY += ROW_H;

        // fact file strip
        g.fill(12, rowY + 3, CARD_W - 12, CARD_H - 9, FACT_BG);
        g.renderOutline(11, rowY + 2, CARD_W - 22, CARD_H - 9 - rowY - 3 + 2, KRAFT_DARK);
        String cat = card.category() == null ? "Mob Trumps" : card.category().label();
        String fact = cat + "  ·  " + (MobCards.ordinal(card.id()) + 1) + " / " + MobCards.ALL.size();
        drawCenteredFit(g, font, fact, CARD_W / 2f, rowY + 5, CARD_W - 28, KRAFT_DARK);

        // holographic foil sheen: shifting rainbow bands + sweep, richer per level
        if (foil || level > 0) {
            int lvl = Math.max(1, level);
            // "Foil sheen" off in settings freezes the rainbow instead of
            // dropping it, so a holo still reads as a holo
            long t = ClientPrefs.foilSheen() ? System.currentTimeMillis() : 0L;
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
            // Holo III: little star sparkles twinkling across the face
            if (lvl >= 3) {
                for (int i = 0; i < 8; i++) {
                    int sx = 12 + (i * 37) % (CARD_W - 24);
                    int sy = 12 + (i * 53) % (CARD_H - 24);
                    float tw = (float) Math.sin(t / 260.0 + i * 1.7);
                    if (tw > 0.2f) {
                        int a = (int) (tw * 0xB0) << 24;
                        g.fill(sx - 2, sy, sx + 3, sy + 1, a | 0x00FFFFFF);
                        g.fill(sx, sy - 2, sx + 1, sy + 3, a | 0x00FFFFFF);
                    }
                }
            }
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

    /**
     * The card frame, escalating with upgrade level: a clean ivory border with
     * a gold pinline for base cards, brushed SILVER for Holo, molten GOLD for
     * Holo II, and a hue-cycling PRISMATIC frame for Holo III — the top two
     * tiers add corner gems and a bright glint that laps the border.
     */
    private static void drawFrame(GuiGraphics g, int lvl) {
        long t = ClientPrefs.foilSheen() ? System.currentTimeMillis() : 0L;
        lvl = Math.min(lvl, 3);
        g.fill(-2, -2, CARD_W + 2, CARD_H + 2, EDGE_DARK);
        switch (lvl) {
            case 0 -> g.fill(0, 0, CARD_W, CARD_H, BORDER_IVORY);
            case 1 -> g.fillGradient(0, 0, CARD_W, CARD_H, 0xFFF2F5FA, 0xFFC2CCDA);
            case 2 -> g.fillGradient(0, 0, CARD_W, CARD_H, 0xFFF0CE6E, 0xFFB8892E);
            default -> {
                float h = (t % 4200L) / 4200f;
                int c1 = 0xFF000000 | hsvToRgb(h, 0.50f, 1f);
                int c2 = 0xFF000000 | hsvToRgb((h + 0.33f) % 1f, 0.50f, 0.82f);
                g.fillGradient(0, 0, CARD_W, CARD_H, c1, c2);
            }
        }
        int pin = switch (lvl) {
            case 0 -> PIN_GOLD;
            case 1 -> 0xFF8FA3BC;
            case 2 -> 0xFFFFF2C8;
            default -> 0xFFFFFFFF;
        };
        g.renderOutline(4, 4, CARD_W - 8, CARD_H - 8, pin);
        g.fill(6, 6, CARD_W - 6, CARD_H - 6, FACE);

        if (lvl >= 2) {
            // corner gems set into the border
            for (int[] c : new int[][]{{1, 1}, {CARD_W - 4, 1}, {1, CARD_H - 4}, {CARD_W - 4, CARD_H - 4}}) {
                g.fill(c[0], c[1], c[0] + 3, c[1] + 3, lvl >= 3 ? 0xFFFFFFFF : 0xFFFFE68A);
                g.fill(c[0], c[1], c[0] + 1, c[1] + 1, 0xFFFFFFFF);
            }
            // a bright glint lapping the border
            int per = 2 * (CARD_W + CARD_H);
            int pos = (int) ((t % 1800L) / 1800f * per);
            int gx;
            int gy;
            if (pos < CARD_W) {
                gx = pos;
                gy = 0;
            } else if (pos < CARD_W + CARD_H) {
                gx = CARD_W - 3;
                gy = pos - CARD_W;
            } else if (pos < 2 * CARD_W + CARD_H) {
                gx = 2 * CARD_W + CARD_H - pos;
                gy = CARD_H - 3;
            } else {
                gx = 0;
                gy = CARD_H - (pos - 2 * CARD_W - CARD_H);
            }
            gx = Math.max(0, Math.min(CARD_W - 3, gx));
            gy = Math.max(0, Math.min(CARD_H - 3, gy));
            g.fill(gx, gy, gx + 3, gy + 3, 0xE0FFFFFF);
        }
    }

    /**
     * Draw the back of a card — the face-down card in a duel, and every mob you
     * haven't collected yet. A deep indigo-to-plum field under a gold damask
     * lattice, with the creeper-face emblem set into a rotated medallion and the
     * wordmark on a ruled banner beneath it. The frame matches the fronts, so a
     * mixed spread still reads as one deck.
     */
    public static void renderBack(GuiGraphics g, Font font, int x, int y, float scale) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);

        // same slate-and-ivory frame as the fronts, so the deck reads as one set
        g.fill(-2, -2, CARD_W + 2, CARD_H + 2, EDGE_DARK);
        g.fill(0, 0, CARD_W, CARD_H, BORDER_IVORY);
        g.renderOutline(4, 4, CARD_W - 8, CARD_H - 8, PIN_GOLD);

        // the field, with a double gold rule inset from the border
        g.fillGradient(6, 6, CARD_W - 6, CARD_H - 6, BACK_TOP, BACK_BOTTOM);
        g.renderOutline(10, 10, CARD_W - 20, CARD_H - 20, BACK_GOLD_DIM);
        g.renderOutline(13, 13, CARD_W - 26, CARD_H - 26, 0x55E3C071);

        drawBackLattice(g);
        drawBackMedallion(g);
        drawBackWordmark(g, font);

        // corner pips finish the border like a printed card
        for (int[] c : new int[][]{{10, 10}, {CARD_W - 14, 10}, {10, CARD_H - 14}, {CARD_W - 14, CARD_H - 14}}) {
            g.fill(c[0], c[1], c[0] + 4, c[1] + 4, BACK_GOLD);
            g.fill(c[0] + 1, c[1] + 1, c[0] + 3, c[1] + 3, BACK_WELL);
        }

        pose.popPose();
    }

    /** A staggered damask of small gold diamonds, parting around the medallion. */
    private static void drawBackLattice(GuiGraphics g) {
        final int pitch = 28;
        final int radius = 5;
        for (int row = 0; row < 7; row++) {
            int cy = 28 + row * pitch;
            int offset = (row % 2 == 0) ? 0 : pitch / 2;
            for (int col = 0; col < 5; col++) {
                int cx = 28 + offset + col * pitch;
                if (cx > CARD_W - 20) continue;
                // leave the medallion and the banner their own clean space
                if (Math.abs(cx - CARD_W / 2) < 44 && cy > 66 && cy < 158) continue;
                if (cy > 186) continue;
                diamond(g, cx, cy, radius, BACK_LATTICE);
            }
        }
    }

    /** A filled diamond built from horizontal rows — cheap and crisply pixelly. */
    private static void diamond(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** The emblem: a gold-ringed diamond medallion holding a creeper face. */
    private static void drawBackMedallion(GuiGraphics g) {
        final int cx = CARD_W / 2;
        final int cy = 112;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(45f));
        g.fill(-30, -30, 30, 30, BACK_GOLD_DIM);
        g.fill(-27, -27, 27, 27, BACK_GOLD);
        g.fill(-25, -25, 25, 25, BACK_WELL);
        g.renderOutline(-25, -25, 50, 50, 0x66000000);
        pose.popPose();

        // the creeper plate sits upright inside the tilted medallion
        g.fillGradient(cx - 20, cy - 20, cx + 20, cy + 20, EMBLEM_GREEN_LIT, EMBLEM_GREEN);
        g.renderOutline(cx - 20, cy - 20, 40, 40, 0x66103A0E);

        // classic 8x8 creeper face, 5px to the cell
        final int u = 5;
        final int ox = cx - 20;
        final int oy = cy - 20;
        face(g, ox, oy, u, 1, 1, 2, 2); // left eye
        face(g, ox, oy, u, 5, 1, 2, 2); // right eye
        face(g, ox, oy, u, 3, 3, 2, 1); // bridge
        face(g, ox, oy, u, 2, 4, 4, 2); // jaw
        face(g, ox, oy, u, 2, 6, 1, 1); // left fang
        face(g, ox, oy, u, 5, 6, 1, 1); // right fang
    }

    /** One block of the creeper face, in 8x8 grid cells. */
    private static void face(GuiGraphics g, int ox, int oy, int unit, int col, int row, int w, int h) {
        g.fill(ox + col * unit, oy + row * unit,
                ox + (col + w) * unit, oy + (row + h) * unit, EMBLEM_INK);
    }

    /** The wordmark on a ruled banner, with a strapline beneath it. */
    private static void drawBackWordmark(GuiGraphics g, Font font) {
        final int cx = CARD_W / 2;
        g.fill(26, 186, CARD_W - 26, 187, BACK_GOLD_DIM);
        diamond(g, 20, 186, 3, BACK_GOLD);
        diamond(g, CARD_W - 20, 186, 3, BACK_GOLD);

        String label = "MOB TRUMPS";
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, 194f, 0);
        pose.scale(1.25f, 1.25f, 1f);
        g.drawString(font, label, -font.width(label) / 2, 0, BACK_TEXT, false);
        pose.popPose();

        String strap = "COLLECT · BATTLE · TRADE";
        pose.pushPose();
        pose.translate(cx, 210f, 0);
        pose.scale(0.7f, 0.7f, 1f);
        g.drawString(font, strap, -font.width(strap) / 2, 0, 0xFF9E86C4, false);
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
        if (!ClientPrefs.livePortraits()) {
            return null; // settings: portraits fall back to the scene backdrop
        }
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
                                String label, int value, int delta, boolean lowerWins) {
        g.fill(12, rowY, CARD_W - 12, rowY + ROW_H - 1, bg);
        g.drawString(font, label, 16, rowY + 3, labelColor, false);
        if (lowerWins) {
            // a small down-caret marks the one stat where the LOWER number wins
            lowerCaret(g, 18 + font.width(label), rowY + 4, labelColor);
        }
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

    /**
     * A tiny solid down-caret drawn beside a "lower wins" stat label, so Rarity
     * reads as what it is — the rarer the mob, the smaller the number, and the
     * smaller number takes the round.
     */
    private static void lowerCaret(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 3; i++) {
            g.fill(x + i, y + i, x + 5 - i, y + i + 1, color);
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
