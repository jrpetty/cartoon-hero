package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.PackOpenedPayload;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Tier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Animated pack-opening reveal. Cards flip up one at a time and pop in with
 * a rarity-coloured glow; legendaries and foils burst sparkles. A summary
 * panel caps it off. Click / Space to advance.
 */
public class PackRevealScreen extends Screen {

    private static final float SCALE = 0.9f;
    private static final long FLIP_MS = 260L;
    private static final long POP_MS = 220L;

    private final List<PackOpenedPayload.Pull> pulls;
    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final List<Spark> sparks = new ArrayList<>();

    private int index = 0;
    private boolean revealed = false;
    private long flipStart = -1;
    private long revealedAt = -1;
    private boolean summary = false;

    public PackRevealScreen(List<PackOpenedPayload.Pull> pulls) {
        super(Component.literal("Pack Opening"));
        this.pulls = pulls;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private MobCard current() {
        return MobCards.byId(pulls.get(index).mobId());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        // extra vignette so the card pops
        g.fillGradient(0, 0, width, height, 0x00000000, 0x66000000);

        if (summary) {
            renderSummary(g);
            return;
        }

        int cw = Math.round(CardRenderer.CARD_W * SCALE);
        int ch = Math.round(CardRenderer.CARD_H * SCALE);
        int cx = (width - cw) / 2;
        int cy = (height - ch) / 2 - 6;
        int centerX = cx + cw / 2;
        int centerY = cy + ch / 2;

        PackOpenedPayload.Pull pull = pulls.get(index);
        MobCard card = current();

        String header = "Pack  " + (index + 1) + " / " + pulls.size();
        g.drawCenteredString(font, header, width / 2, cy - 24, 0xFFF3E2A7);

        // flip animation state
        float flip = 1f;
        boolean showingFront = revealed;
        if (flipStart >= 0) {
            float p = Mth.clamp((System.currentTimeMillis() - flipStart) / (float) FLIP_MS, 0f, 1f);
            flip = Math.abs(1f - 2f * p);
            showingFront = p >= 0.5f;
            if (p >= 1f) {
                flipStart = -1;
                revealed = true;
                revealedAt = System.currentTimeMillis();
            }
        }

        // rarity glow halo behind a revealed card
        if (showingFront) {
            int glow = tierGlow(card.tier(), pull.foil());
            if (glow != 0) {
                drawGlow(g, centerX, centerY, cw, ch, glow);
            }
        }

        // pop-in scale (overshoot) once revealed
        float pop = 1f;
        if (revealed && revealedAt >= 0) {
            float p = Mth.clamp((System.currentTimeMillis() - revealedAt) / (float) POP_MS, 0f, 1f);
            pop = 1f + 0.10f * (float) Math.sin(p * Math.PI); // gentle bulge
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);
        pose.scale(Math.max(0.02f, flip) * pop, pop, 1f);
        pose.translate(-centerX, -centerY, 0);
        g.fill(cx + 4, cy + 6, cx + cw + 6, cy + ch + 8, 0x88000000); // shadow
        if (showingFront) {
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
            CardRenderer.renderCard(g, font, card, cx, cy, SCALE, mouseX, mouseY, mob, pull.foil());
        } else {
            CardRenderer.renderBack(g, font, cx, cy, SCALE);
        }
        pose.popPose();

        renderSparks(g);

        // badges
        if (revealed && flipStart < 0) {
            int by = cy + ch + 8;
            if (pull.foil()) {
                g.drawCenteredString(font, "✦ HOLOGRAPHIC FOIL ✦", width / 2, by, foilShimmerColor());
                by += 11;
            }
            if (pull.isNew()) {
                g.drawCenteredString(font, "NEW!", width / 2, by, 0xFFFFD54A);
            }
        }

        String hint = !revealed ? "Click to reveal"
                : index < pulls.size() - 1 ? "Click for next card" : "Click to finish";
        g.drawCenteredString(font, hint, width / 2, cy + ch + 32, 0xFFBFB49E);

        int pipsW = pulls.size() * 10 - 4;
        int pipX = (width - pipsW) / 2;
        for (int i = 0; i < pulls.size(); i++) {
            int c = i < index ? 0xFF8A755A : i == index ? 0xFFF3E2A7 : 0xFF4A4038;
            g.fill(pipX + i * 10, cy + ch + 46, pipX + i * 10 + 6, cy + ch + 50, c);
        }
    }

    private void renderSummary(GuiGraphics g) {
        int newCount = (int) pulls.stream().filter(PackOpenedPayload.Pull::isNew).count();
        int foilCount = (int) pulls.stream().filter(PackOpenedPayload.Pull::foil).count();
        Tier best = pulls.stream().map(p -> MobCards.byId(p.mobId()).tier())
                .max(java.util.Comparator.comparingInt(Enum::ordinal)).orElse(Tier.COMMON);

        int pw = 220, ph = 120;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, CardRenderer.KRAFT_DARK);
        g.fill(px, py, px + pw, py + ph, CardRenderer.KRAFT);
        g.fill(px + 5, py + 5, px + pw - 5, py + ph - 5, CardRenderer.FACE);

        g.drawCenteredString(font, "PACK COMPLETE", width / 2, py + 14, CardRenderer.INK);
        g.drawCenteredString(font, pulls.size() + " cards pulled", width / 2, py + 34, CardRenderer.KRAFT_DARK);
        g.drawCenteredString(font, newCount + " new to your collection", width / 2, py + 50, 0xFF3D8B3D);
        if (foilCount > 0) {
            g.drawCenteredString(font, "✦ " + foilCount + " holographic foil"
                    + (foilCount > 1 ? "s" : ""), width / 2, py + 66, 0xFF7A5AC0);
        }
        g.drawCenteredString(font, "Best pull: " + best.label(), width / 2, py + 84,
                tierGlow(best, false) == 0 ? CardRenderer.KRAFT_DARK : tierGlow(best, false) | 0xFF000000);
        g.drawCenteredString(font, "Click to close", width / 2, py + ph + 8, 0xFFBFB49E);
    }

    // --- sparks ---

    private static final class Spark {
        final float ox, oy, vx, vy;
        final long born;
        final float ttl;
        final int color;

        Spark(float ox, float oy, float vx, float vy, float ttl, int color) {
            this.ox = ox;
            this.oy = oy;
            this.vx = vx;
            this.vy = vy;
            this.born = System.currentTimeMillis();
            this.ttl = ttl;
            this.color = color;
        }
    }

    private void spawnSparks(int cx, int cy, boolean foil, Tier tier) {
        int base = foil ? 60 : tier == Tier.LEGENDARY ? 50 : 30;
        var rnd = new java.util.Random();
        for (int i = 0; i < base; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            float speed = 40 + rnd.nextFloat() * 130;
            int color = foil ? CardRenderer.tierPrintColor(current()) : sparkColor(tier, rnd);
            sparks.add(new Spark(cx, cy, (float) Math.cos(a) * speed,
                    (float) Math.sin(a) * speed - 30, 0.7f + rnd.nextFloat() * 0.7f, color));
        }
    }

    private int sparkColor(Tier tier, java.util.Random rnd) {
        if (tier == Tier.LEGENDARY) {
            return rnd.nextBoolean() ? 0xFFFFE082 : 0xFFFFD54A;
        }
        int[] rainbow = {0xFFFF6B6B, 0xFFFFD54A, 0xFF7CE38B, 0xFF55D0F0, 0xFFC77BFF};
        return rainbow[rnd.nextInt(rainbow.length)];
    }

    private void renderSparks(GuiGraphics g) {
        long now = System.currentTimeMillis();
        sparks.removeIf(s -> (now - s.born) / 1000f >= s.ttl);
        for (Spark s : sparks) {
            float age = (now - s.born) / 1000f;
            float x = s.ox + s.vx * age;
            float y = s.oy + s.vy * age + 150f * age * age; // gravity
            float lifeLeft = 1f - age / s.ttl;
            int alpha = (int) (Mth.clamp(lifeLeft, 0f, 1f) * 255) << 24;
            int col = (s.color & 0x00FFFFFF) | alpha;
            int sz = lifeLeft > 0.5f ? 2 : 1;
            g.fill((int) x - sz, (int) y - sz, (int) x + sz, (int) y + sz, col);
        }
    }

    private void drawGlow(GuiGraphics g, int cx, int cy, int cw, int ch, int color) {
        for (int i = 6; i >= 1; i--) {
            int spread = i * 7;
            int alpha = (int) (0x30 * (1f - (i - 1) / 6f)) << 24;
            int col = (color & 0x00FFFFFF) | alpha;
            g.fill(cx - cw / 2 - spread, cy - ch / 2 - spread,
                    cx + cw / 2 + spread, cy + ch / 2 + spread, col);
        }
    }

    private int foilShimmerColor() {
        float h = (System.currentTimeMillis() % 1500L) / 1500f;
        int[] rainbow = {0xFFFF6B6B, 0xFFFFD54A, 0xFF7CE38B, 0xFF55D0F0, 0xFFC77BFF};
        return rainbow[(int) (h * rainbow.length) % rainbow.length];
    }

    private int tierGlow(Tier tier, boolean foil) {
        if (foil) return 0xFFFFFFFF;
        return switch (tier) {
            case LEGENDARY -> 0xFFFFD54A;
            case EPIC -> 0xFFC77BFF;
            case RARE -> 0xFF55D0F0;
            case UNCOMMON -> 0xFF7CE38B;
            default -> 0;
        };
    }

    private void advance() {
        if (summary) {
            onClose();
            return;
        }
        if (flipStart >= 0) {
            flipStart = -1;
            revealed = true;
            revealedAt = System.currentTimeMillis();
            return;
        }
        if (!revealed) {
            flipStart = System.currentTimeMillis();
            PackOpenedPayload.Pull pull = pulls.get(index);
            MobCard card = current();
            playRevealSound(card.tier(), pull.foil());
            if (pull.foil() || card.tier() == Tier.LEGENDARY || card.tier() == Tier.EPIC) {
                spawnSparks(width / 2, height / 2 - 6, pull.foil(), card.tier());
            }
        } else if (index < pulls.size() - 1) {
            index++;
            revealed = false;
            revealedAt = -1;
        } else {
            summary = true;
        }
    }

    private void playRevealSound(Tier tier, boolean foil) {
        if (minecraft == null) return;
        float pitch = switch (tier) {
            case COMMON -> 0.9f;
            case UNCOMMON -> 1.05f;
            case RARE -> 1.2f;
            case EPIC -> 1.4f;
            case LEGENDARY -> 1.7f;
        };
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), pitch));
        if (foil || tier == Tier.LEGENDARY) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4f));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            advance();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 32 || keyCode == 257) {
            advance();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
