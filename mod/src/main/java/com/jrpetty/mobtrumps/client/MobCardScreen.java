package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.CardLore;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Full-size card view in the style of a classic Top Trumps card,
 * opened by right-clicking a Mob Card or from the collection book.
 * Cards ease in on open, wear a rarity halo, and catch a shine sweep.
 */
public class MobCardScreen extends Screen {

    private static final long ENTER_MS = 240L;
    private static final long SHINE_MS = 900L;

    private final MobCard card;
    private final Screen parent;
    private final boolean foil;
    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private long openedAt = -1;

    public MobCardScreen(MobCard card) {
        this(card, null, false);
    }

    public MobCardScreen(MobCard card, Screen parent) {
        this(card, parent, false);
    }

    public MobCardScreen(MobCard card, Screen parent, boolean foil) {
        super(Component.literal(card.displayName()));
        this.card = card;
        this.parent = parent;
        this.foil = foil;
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (openedAt < 0) {
            openedAt = System.currentTimeMillis();
        }
        long elapsed = System.currentTimeMillis() - openedAt;

        int w = CardRenderer.CARD_W;
        int h = CardRenderer.CARD_H;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        int cx = x + w / 2;
        int cy = y + h / 2;

        // rarity halo behind the card
        int glow = tierGlow(card.tier(), foil);
        if (glow != 0) {
            float pulse = 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 500.0);
            for (int i = 6; i >= 1; i--) {
                int spread = i * 6;
                int alpha = (int) (0x2A * pulse * (1f - (i - 1) / 6f)) << 24;
                g.fill(cx - w / 2 - spread, cy - h / 2 - spread,
                        cx + w / 2 + spread, cy + h / 2 + spread, (glow & 0x00FFFFFF) | alpha);
            }
        }

        // ease-in: scale up with a slight overshoot
        float t = Mth.clamp(elapsed / (float) ENTER_MS, 0f, 1f);
        float ease = 1f - (1f - t) * (1f - t);
        float scale = 0.82f + 0.18f * ease + 0.03f * (float) Math.sin(t * Math.PI);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(scale, scale, 1f);
        pose.translate(-cx, -cy, 0);

        g.fill(x + 4, y + 6, x + w + 8, y + h + 10, 0x66000000); // shadow
        LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
        int level = ClientCollection.displayLevel(card.id(), foil);
        CardRenderer.renderCard(g, font, card, level, x, y, 1f, mouseX, mouseY, mob, foil, true);

        // one-time shine sweep across the card on open
        if (elapsed < SHINE_MS) {
            float p = elapsed / (float) SHINE_MS;
            int sweep = x - 20 + (int) (p * (w + 40));
            for (int b = -6; b <= 6; b++) {
                int sx = sweep + b;
                if (sx < x + 6 || sx > x + w - 6) continue;
                int a = (int) ((1f - Math.abs(b) / 6f) * 0x40 * (1f - p)) << 24;
                g.fill(sx, y + 6, sx + 1, y + h - 6, 0x00FFFFFF | a);
            }
        }
        pose.popPose();

        // how many of this mob you've collected + milestone / upgrade progress
        int have = ClientCollection.killCount(card.id());
        Tier tier = card.tier();
        int lvl = tier.upgradeLevel(have);
        int next = tier.nextMilestone(have);
        String progress;
        if (have == 0) {
            progress = "Not collected yet";
        } else if (lvl == 0) {
            progress = "Collected x" + have + "  ·  holo at " + next + " (" + have + "/" + next + ")";
        } else if (next > 0) {
            progress = "Collected x" + have + "  ·  " + holoLabel(lvl)
                    + "  ·  next tier at " + next + " (" + have + "/" + next + ")";
        } else {
            progress = "Collected x" + have + "  ·  " + holoLabel(lvl) + "  ·  fully upgraded ★";
        }
        int collectedColor = lvl > 0 ? 0xFFC77BFF : have > 0 ? 0xFF7BE38A : 0xFFB9BFC9;
        g.drawCenteredString(font, progress, cx, y + h + 6, collectedColor);

        // lore: a flavour line and a real Minecraft fact, wrapped under the card
        CardLore.Lore lore = CardLore.of(card.id());
        int loreW = w + 60;
        int ly = y + h + 20;
        for (var line : font.split(Component.literal(lore.flavor())
                .withStyle(ChatFormatting.ITALIC), loreW)) {
            g.drawCenteredString(font, line, cx, ly, 0xFFE8C56A);
            ly += 10;
        }
        ly += 3;
        for (var line : font.split(Component.literal("Did you know? " + lore.fact()), loreW)) {
            g.drawCenteredString(font, line, cx, ly, 0xFF9AA0A6);
            ly += 10;
        }

        String hint = parent != null ? "Press ESC to return to the book" : "Press ESC to close";
        g.drawString(font, hint, (width - font.width(hint)) / 2, ly + 4, 0xFF787878, true);
    }

    /** "Holo" / "Holo II" / "Holo III" for upgrade levels 1-3. */
    static String holoLabel(int level) {
        return switch (level) {
            case 1 -> "Holo";
            case 2 -> "Holo II";
            case 3 -> "Holo III";
            default -> "Holo +" + level;
        };
    }

    private int tierGlow(Tier tier, boolean isFoil) {
        if (isFoil) return 0xFFFFFFFF;
        return switch (tier) {
            case LEGENDARY -> 0xFFFFD54A;
            case EPIC -> 0xFFC77BFF;
            case RARE -> 0xFF55D0F0;
            default -> 0;
        };
    }
}
