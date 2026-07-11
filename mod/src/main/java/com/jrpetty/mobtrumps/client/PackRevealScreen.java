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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Animated pack-opening reveal: cards flip up one at a time. Each reveal
 * plays a sound whose pitch rises with rarity; foils and legendaries get a
 * chime and a golden burst. Click / Space to flip the next card.
 */
public class PackRevealScreen extends Screen {

    private static final float SCALE = 0.9f;
    private static final long FLIP_MS = 260L;

    private final List<PackOpenedPayload.Pull> pulls;
    private final Map<String, LivingEntity> entityCache = new HashMap<>();

    private int index = 0;          // which card we're on
    private boolean revealed = false;
    private long flipStart = -1;     // when the current flip began (ms), -1 if not flipping

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

        int cw = Math.round(CardRenderer.CARD_W * SCALE);
        int ch = Math.round(CardRenderer.CARD_H * SCALE);
        int cx = (width - cw) / 2;
        int cy = (height - ch) / 2 - 6;

        String header = "Pack  " + (index + 1) + " / " + pulls.size();
        g.drawCenteredString(font, header, width / 2, cy - 22, 0xFFF3E2A7);

        PackOpenedPayload.Pull pull = pulls.get(index);
        MobCard card = current();

        // flip animation: squash horizontally through the halfway point
        float flip = 1f;
        boolean showingFront = revealed;
        if (flipStart >= 0) {
            float p = Mth.clamp((System.currentTimeMillis() - flipStart) / (float) FLIP_MS, 0f, 1f);
            flip = Math.abs(1f - 2f * p);
            showingFront = p >= 0.5f;
            if (p >= 1f) {
                flipStart = -1;
                revealed = true;
            }
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx + cw / 2f, 0, 0);
        pose.scale(Math.max(0.02f, flip), 1f, 1f);
        pose.translate(-(cx + cw / 2f), 0, 0);
        // shadow
        g.fill(cx + 4, cy + 6, cx + cw + 6, cy + ch + 8, 0x88000000);
        if (showingFront) {
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
            CardRenderer.renderCard(g, font, card, cx, cy, SCALE, mouseX, mouseY, mob, pull.foil());
            int glow = tierGlow(card.tier(), pull.foil());
            if (glow != 0) {
                g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, glow);
                g.renderOutline(cx - 3, cy - 3, cw + 6, ch + 6, glow & 0x66FFFFFF);
            }
        } else {
            CardRenderer.renderBack(g, font, cx, cy, SCALE);
        }
        pose.popPose();

        // badges under the card once revealed
        if (revealed && flipStart < 0) {
            int by = cy + ch + 6;
            if (pull.foil()) {
                g.drawCenteredString(font, "✦ HOLOGRAPHIC FOIL ✦", width / 2, by, 0xFFFFFFFF);
                by += 11;
            }
            if (pull.isNew()) {
                g.drawCenteredString(font, "NEW!", width / 2, by, 0xFFFFD54A);
                by += 11;
            }
        }

        String hint = index < pulls.size() - 1
                ? (revealed ? "Click for next card" : "Click to reveal")
                : (revealed ? "Click to finish" : "Click to reveal");
        g.drawCenteredString(font, hint, width / 2, cy + ch + 30, 0xFFBFB49E);

        // progress pips
        int pipsW = pulls.size() * 10 - 4;
        int pipX = (width - pipsW) / 2;
        for (int i = 0; i < pulls.size(); i++) {
            int c = i < index ? 0xFF8A755A : i == index ? 0xFFF3E2A7 : 0xFF4A4038;
            g.fill(pipX + i * 10, cy + ch + 44, pipX + i * 10 + 6, cy + ch + 48, c);
        }
    }

    private void advance() {
        if (flipStart >= 0) {
            // fast-forward the current flip
            flipStart = -1;
            revealed = true;
            return;
        }
        if (!revealed) {
            // start revealing the current card
            flipStart = System.currentTimeMillis();
            PackOpenedPayload.Pull pull = pulls.get(index);
            MobCard card = current();
            playRevealSound(card.tier(), pull.foil());
        } else if (index < pulls.size() - 1) {
            index++;
            revealed = false;
        } else {
            onClose();
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

    private int tierGlow(Tier tier, boolean foil) {
        if (foil) return 0xFFFFFFFF;
        return switch (tier) {
            case LEGENDARY -> 0xFFFFD54A;
            case EPIC -> 0xFFC77BFF;
            case RARE -> 0xFF55D0F0;
            default -> 0;
        };
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
        if (keyCode == 32 || keyCode == 257) { // space or enter
            advance();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
