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
 * A scrollable card-unboxing carousel. The five pulled cards fan out
 * face-down; scroll (wheel / arrows / on-screen buttons) to slide through
 * them. Each card flips up the first time it slides to centre, plays a
 * rarity chime, and legendaries/foils burst sparkles with a glow. You can
 * scroll back and forth to admire the whole pack, then hit Done for a summary.
 */
public class PackRevealScreen extends Screen {

    private static final float CENTER_SCALE = 0.82f;
    private static final float SIDE_SCALE = 0.5f;
    private static final long FLIP_MS = 300L;

    private final List<PackOpenedPayload.Pull> pulls;
    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final List<Spark> sparks = new ArrayList<>();

    private final boolean[] revealed;
    private final long[] flipStart;

    private int index = 0;
    private float visualPos = 0f;      // eased carousel position
    private long lastFrame = 0;
    private boolean summary = false;

    private int leftX, rightX, arrowY, doneX, doneY, doneW;
    private static final int ARROW = 16;

    public PackRevealScreen(List<PackOpenedPayload.Pull> pulls) {
        super(Component.literal("Pack Opening"));
        this.pulls = pulls;
        this.revealed = new boolean[pulls.size()];
        this.flipStart = new long[pulls.size()];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        visualPos = index;
        arrowY = height / 2 - ARROW / 2 - 6;
        leftX = width / 2 - 150;
        rightX = width / 2 + 150 - ARROW;
        doneW = font.width("Done") + 16;
        doneX = (width - doneW) / 2;
        doneY = height / 2 + Math.round(CardRenderer.CARD_H * CENTER_SCALE / 2) + 22;
        beginReveal(0);
    }

    private MobCard cardAt(int i) {
        return MobCards.byId(pulls.get(i).mobId());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fillGradient(0, 0, width, height, 0x11000000, 0x77000000);

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        visualPos += (index - visualPos) * Math.min(1f, dt * 12f);
        if (Math.abs(visualPos - index) < 0.001f) visualPos = index;

        if (summary) {
            renderSummary(g);
            return;
        }

        int centerX = width / 2;
        int centerY = height / 2 - 6;
        float spacing = CardRenderer.CARD_W * CENTER_SCALE * 0.62f;

        String header = "Your pack  ·  card " + (index + 1) + " / " + pulls.size();
        g.drawCenteredString(font, header, centerX,
                centerY - Math.round(CardRenderer.CARD_H * CENTER_SCALE / 2) - 24, 0xFFF3E2A7);

        // draw from outside in so the centre card lands on top
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < pulls.size(); i++) order.add(i);
        order.sort((a, b) -> Float.compare(Math.abs(b - visualPos), Math.abs(a - visualPos)));
        for (int i : order) {
            drawCard(g, i, centerX, centerY, spacing, mouseX, mouseY, now);
        }

        renderSparks(g);

        // centre-card badges
        PackOpenedPayload.Pull cur = pulls.get(index);
        if (revealed[index] && Math.abs(visualPos - index) < 0.1f) {
            int by = centerY + Math.round(CardRenderer.CARD_H * CENTER_SCALE / 2) + 4;
            if (cur.foil()) {
                g.drawCenteredString(font, "✦ HOLOGRAPHIC FOIL ✦", centerX, by, foilShimmerColor());
                by += 10;
            }
            if (cur.isNew()) g.drawCenteredString(font, "NEW!", centerX, by, 0xFFFFD54A);
        }

        // nav arrows
        drawArrow(g, leftX, arrowY, "<", index > 0, mouseX, mouseY);
        drawArrow(g, rightX, arrowY, ">", index < pulls.size() - 1, mouseX, mouseY);

        // pips
        int pipsW = pulls.size() * 12 - 6;
        int pipX = centerX - pipsW / 2;
        int pipY = centerY + Math.round(CardRenderer.CARD_H * CENTER_SCALE / 2) + 26;
        for (int i = 0; i < pulls.size(); i++) {
            int c = i == index ? 0xFFF3E2A7 : revealed[i] ? 0xFF8A755A : 0xFF4A4038;
            g.fill(pipX + i * 12, pipY, pipX + i * 12 + 6, pipY + 4, c);
        }

        boolean allRevealed = allRevealed();
        String hint = allRevealed ? "Scroll to review · click Done to finish"
                : "Scroll or use ← → to reveal your cards";
        g.drawCenteredString(font, hint, centerX, pipY + 10, 0xFFBFB49E);

        if (allRevealed) {
            boolean hover = mouseX >= doneX && mouseX < doneX + doneW
                    && mouseY >= doneY && mouseY < doneY + 14;
            g.fill(doneX, doneY, doneX + doneW, doneY + 14, hover ? 0xFF6FB84A : 0xFF55A82F);
            g.renderOutline(doneX, doneY, doneW, 14, 0xFF2E5E22);
            g.drawCenteredString(font, "Done", centerX, doneY + 3, 0xFFFFFFFF);
        }
    }

    private void drawCard(GuiGraphics g, int i, int centerX, int centerY, float spacing,
                          int mouseX, int mouseY, long now) {
        float offset = i - visualPos;
        if (Math.abs(offset) > 2.4f) return;

        float centered = Mth.clamp(1f - Math.abs(offset), 0f, 1f);
        float scale = SIDE_SCALE + (CENTER_SCALE - SIDE_SCALE) * centered;
        int cw = Math.round(CardRenderer.CARD_W * scale);
        int ch = Math.round(CardRenderer.CARD_H * scale);
        int cx = Math.round(centerX + offset * spacing - cw / 2f);
        int cy = centerY - ch / 2;

        PackOpenedPayload.Pull pull = pulls.get(i);
        MobCard card = cardAt(i);

        // flip state
        boolean showFront = revealed[i];
        float flip = 1f;
        if (flipStart[i] > 0 && !revealed[i]) {
            float p = Mth.clamp((now - flipStart[i]) / (float) FLIP_MS, 0f, 1f);
            flip = Math.abs(1f - 2f * p);
            showFront = p >= 0.5f;
            if (p >= 1f) revealed[i] = true;
        }

        // glow for the centred, revealed card
        if (showFront && centered > 0.6f) {
            int glow = tierGlow(card.tier(), pull.foil());
            if (glow != 0) drawGlow(g, cx + cw / 2, cy + ch / 2, cw, ch, glow, centered);
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx + cw / 2f, cy + ch / 2f, 0);
        pose.scale(Math.max(0.02f, flip), 1f, 1f);
        pose.translate(-(cx + cw / 2f), -(cy + ch / 2f), 0);

        // dim side cards
        g.fill(cx + 3, cy + 4, cx + cw + 5, cy + ch + 6, 0x66000000);
        if (showFront) {
            LivingEntity mob = centered > 0.5f
                    ? CardRenderer.portraitEntity(minecraft, card, entityCache) : null;
            CardRenderer.renderCard(g, font, card, cx, cy, scale, mouseX, mouseY, mob, pull.foil());
        } else {
            CardRenderer.renderBack(g, font, cx, cy, scale);
        }
        if (centered < 0.9f) {
            g.fill(cx, cy, cx + cw, cy + ch, (int) ((1f - centered) * 0x80) << 24);
        }
        pose.popPose();
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
            g.drawCenteredString(font, "✦ " + foilCount + " holographic foil" + (foilCount > 1 ? "s" : ""),
                    width / 2, py + 66, 0xFF7A5AC0);
        }
        int bestC = tierGlow(best, false);
        g.drawCenteredString(font, "Best pull: " + best.label(), width / 2, py + 84,
                bestC == 0 ? CardRenderer.KRAFT_DARK : bestC);
        g.drawCenteredString(font, "click to close", width / 2, py + ph + 8, 0xFFBFB49E);
    }

    // --- navigation ---

    private void go(int dir) {
        int next = Mth.clamp(index + dir, 0, pulls.size() - 1);
        if (next != index) {
            index = next;
            beginReveal(index);
            playClick();
        }
    }

    private void beginReveal(int i) {
        if (!revealed[i] && flipStart[i] == 0) {
            flipStart[i] = System.currentTimeMillis();
            PackOpenedPayload.Pull pull = pulls.get(i);
            MobCard card = cardAt(i);
            playRevealSound(card.tier(), pull.foil());
            if (pull.foil() || card.tier() == Tier.LEGENDARY || card.tier() == Tier.EPIC) {
                spawnSparks(width / 2, height / 2 - 6, pull.foil(), card.tier());
            }
        }
    }

    private boolean allRevealed() {
        for (boolean r : revealed) if (!r) return false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        go(sy > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 263 || key == 65) { go(-1); return true; }       // left / A
        if (key == 262 || key == 68) { go(1); return true; }        // right / D
        if (key == 32 || key == 257) {                              // space / enter
            if (index < pulls.size() - 1) go(1);
            else if (allRevealed()) summary = true;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (summary) { onClose(); return true; }
        if (mx >= leftX && mx < leftX + ARROW && my >= arrowY && my < arrowY + ARROW) { go(-1); return true; }
        if (mx >= rightX && mx < rightX + ARROW && my >= arrowY && my < arrowY + ARROW) { go(1); return true; }
        if (allRevealed() && mx >= doneX && mx < doneX + doneW && my >= doneY && my < doneY + 14) {
            summary = true;
            playClick();
            return true;
        }
        // click a side card to bring it to centre
        float spacing = CardRenderer.CARD_W * CENTER_SCALE * 0.62f;
        int rel = Math.round((float) ((mx - width / 2.0) / spacing) + visualPos);
        if (rel >= 0 && rel < pulls.size() && rel != index) { index = rel; beginReveal(rel); playClick(); return true; }
        // clicking the centre advances
        go(1);
        return true;
    }

    // --- sparks & effects ---

    private static final class Spark {
        final float ox, oy, vx, vy, ttl;
        final long born;
        final int color;
        Spark(float ox, float oy, float vx, float vy, float ttl, int color) {
            this.ox = ox; this.oy = oy; this.vx = vx; this.vy = vy;
            this.ttl = ttl; this.color = color; this.born = System.currentTimeMillis();
        }
    }

    private void spawnSparks(int cx, int cy, boolean foil, Tier tier) {
        int base = foil ? 70 : tier == Tier.LEGENDARY ? 55 : 32;
        var rnd = new java.util.Random();
        for (int i = 0; i < base; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            float speed = 45 + rnd.nextFloat() * 140;
            int color = foil ? foilShimmerColor() : sparkColor(tier, rnd);
            sparks.add(new Spark(cx, cy, (float) Math.cos(a) * speed,
                    (float) Math.sin(a) * speed - 35, 0.7f + rnd.nextFloat() * 0.8f, color));
        }
    }

    private int sparkColor(Tier tier, java.util.Random rnd) {
        if (tier == Tier.LEGENDARY) return rnd.nextBoolean() ? 0xFFFFE082 : 0xFFFFD54A;
        int[] r = {0xFFFF6B6B, 0xFFFFD54A, 0xFF7CE38B, 0xFF55D0F0, 0xFFC77BFF};
        return r[rnd.nextInt(r.length)];
    }

    private void renderSparks(GuiGraphics g) {
        long now = System.currentTimeMillis();
        sparks.removeIf(s -> (now - s.born) / 1000f >= s.ttl);
        for (Spark s : sparks) {
            float age = (now - s.born) / 1000f;
            float x = s.ox + s.vx * age;
            float y = s.oy + s.vy * age + 150f * age * age;
            float life = 1f - age / s.ttl;
            int col = (s.color & 0x00FFFFFF) | ((int) (Mth.clamp(life, 0f, 1f) * 255) << 24);
            int sz = life > 0.5f ? 2 : 1;
            g.fill((int) x - sz, (int) y - sz, (int) x + sz, (int) y + sz, col);
        }
    }

    private void drawGlow(GuiGraphics g, int cx, int cy, int cw, int ch, int color, float strength) {
        for (int i = 6; i >= 1; i--) {
            int spread = i * 6;
            int alpha = (int) (0x30 * strength * (1f - (i - 1) / 6f)) << 24;
            g.fill(cx - cw / 2 - spread, cy - ch / 2 - spread,
                    cx + cw / 2 + spread, cy + ch / 2 + spread, (color & 0x00FFFFFF) | alpha);
        }
    }

    private int foilShimmerColor() {
        float h = (System.currentTimeMillis() % 1500L) / 1500f;
        int[] r = {0xFFFF6B6B, 0xFFFFD54A, 0xFF7CE38B, 0xFF55D0F0, 0xFFC77BFF};
        return r[(int) (h * r.length) % r.length];
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

    private void drawArrow(GuiGraphics g, int x, int y, String glyph, boolean enabled,
                           int mouseX, int mouseY) {
        boolean hover = enabled && mouseX >= x && mouseX < x + ARROW && mouseY >= y && mouseY < y + ARROW;
        g.fill(x, y, x + ARROW, y + ARROW, enabled ? (hover ? 0xCCB99465 : 0x99000000) : 0x44000000);
        g.renderOutline(x, y, ARROW, ARROW, enabled ? 0xFFF3E2A7 : 0x44FFFFFF);
        g.drawCenteredString(font, glyph, x + ARROW / 2, y + 4, enabled ? 0xFFFFFFFF : 0xFF777777);
    }

    private void playRevealSound(Tier tier, boolean foil) {
        if (minecraft == null) return;
        float pitch = switch (tier) {
            case COMMON -> 0.9f; case UNCOMMON -> 1.05f; case RARE -> 1.2f;
            case EPIC -> 1.4f; case LEGENDARY -> 1.7f;
        };
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), pitch));
        if (foil || tier == Tier.LEGENDARY) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4f));
        }
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f));
        }
    }
}
