package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.DuelReplyPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

import static com.jrpetty.mobtrumps.client.TableArt.BAD;
import static com.jrpetty.mobtrumps.client.TableArt.BRASS;
import static com.jrpetty.mobtrumps.client.TableArt.BRASS_DARK;
import static com.jrpetty.mobtrumps.client.TableArt.BRASS_DIM;
import static com.jrpetty.mobtrumps.client.TableArt.DIM;
import static com.jrpetty.mobtrumps.client.TableArt.FAINT;
import static com.jrpetty.mobtrumps.client.TableArt.INK;

/**
 * Somebody has challenged you: accept or decline, on screen.
 *
 * <p>The duel it leads to was always played on screen. This is the one part of
 * it that was not — being challenged meant reading a line of chat and typing a
 * command back, which is a poor way to be offered a game and a worse way to
 * notice you have been.
 */
public class DuelInviteScreen extends Screen {

    private int[] acceptRect;
    private int[] declineRect;

    public DuelInviteScreen() {
        super(Component.literal("Duel Challenge"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (!ClientDuelInvite.pending()) {
            onClose();
            return;
        }
        g.fill(0, 0, width, height, 0xB4000000);

        int pw = Math.min(300, width - 24);
        int ph = 116;
        int px = (width - pw) / 2;
        int py = Math.max(6, (height - ph) / 2);

        TableArt.pool(g, width / 2, py + ph / 2, pw, BRASS, 0x22);
        TableArt.plate(g, px, py, pw, ph, BRASS_DIM);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, py + 10f, 0);
        pose.scale(1.5f, 1.5f, 1f);
        String head = "DUEL CHALLENGE";
        g.drawString(font, head, -font.width(head) / 2, 0, BRASS, true);
        pose.popPose();
        g.fill(px + 18, py + 28, px + pw - 18, py + 29, BRASS_DARK);

        String who = ClientDuelInvite.from().toUpperCase(Locale.ROOT) + " WANTS A GAME";
        g.drawCenteredString(font, fit(who, pw - 16), width / 2, py + 36, INK);
        String terms = ClientDuelInvite.terms();
        if (!terms.isEmpty()) {
            g.drawCenteredString(font, fit(terms, pw - 16), width / 2, py + 48, DIM);
        }

        // the clock, because an invite that quietly expires is worse than none
        int left = ClientDuelInvite.secondsLeft();
        g.drawCenteredString(font, left + "s to answer", width / 2, py + 62,
                left <= 5 ? BAD : FAINT);

        int bw = Math.min((pw - 34) / 2, 110);
        int by = py + ph - 30;
        int ax = width / 2 - bw - 3;
        int dx = width / 2 + 3;
        boolean aHot = inRect(mouseX, mouseY, new int[]{ax, by, bw, 20});
        boolean dHot = inRect(mouseX, mouseY, new int[]{dx, by, bw, 20});
        TableArt.button(g, ax, by, bw, 20, 0xFF2C6E49, aHot, true);
        g.drawCenteredString(font, "ACCEPT", ax + bw / 2, by + 6, INK);
        TableArt.button(g, dx, by, bw, 20, 0xFF6A2A26, dHot, true);
        g.drawCenteredString(font, "Decline", dx + bw / 2, by + 6, INK);
        acceptRect = new int[]{ax, by, bw, 20};
        declineRect = new int[]{dx, by, bw, 20};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = (int) mouseX;
            int my = (int) mouseY;
            if (inRect(mx, my, acceptRect)) {
                reply(true);
                return true;
            }
            if (inRect(mx, my, declineRect)) {
                reply(false);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {   // enter
            reply(true);
            return true;
        }
        if (keyCode == 256) {                      // escape declines rather than lingers
            reply(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void reply(boolean accept) {
        PacketDistributor.sendToServer(new DuelReplyPayload(accept));
        ClientDuelInvite.dismiss();
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.UI_BUTTON_CLICK.value(), accept ? 1.3f : 0.8f));
            // accepting hands straight over to the battle screen the duel opens
            minecraft.setScreen(null);
        }
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(8, maxWidth - font.width("…"))) + "…";
    }

    private static boolean inRect(int mx, int my, int[] rect) {
        return rect != null && mx >= rect[0] && mx < rect[0] + rect[2]
                && my >= rect[1] && my < rect[1] + rect[3];
    }
}
