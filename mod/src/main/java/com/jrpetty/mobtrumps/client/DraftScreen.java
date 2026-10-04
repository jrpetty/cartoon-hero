package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.DraftActionPayload;
import com.jrpetty.mobtrumps.game.DraftLayout;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The draft, played on screen: the shared pool face up in the middle, both
 * players' picks along the bottom, and a clock on every pick. On your turn,
 * click a card to take it; hovering any card shows it full size with its mob.
 *
 * <p>Only the hovered card carries a live mob — sixteen of them a frame is
 * the kind of cost that once made the Guess Who board lag.
 */
public class DraftScreen extends Screen {

    private static final long LEAVE_CONFIRM_MS = 2500L;
    private static final int GOLD = 0xFFF3D68A;
    private static final int GOLD_DIM = 0xFFB89555;
    private static final int TEXT_DIM = 0xFFD5E2DB;
    private static final int TEXT_FAINT = 0xFF8FA89C;
    private static final int YOU_ACCENT = 0xFF55E06A;
    private static final int OPP_ACCENT = 0xFFF0857D;
    private static final int EDGE = 0xFF48836B;

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private long leaveArmedAt = -1;
    /** The card asked for, greyed until the server's answer lands. */
    private String requested = "";
    private long requestedFor = -1;

    public DraftScreen() {
        super(Component.literal("Draft"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private DraftLayout.Layout layout() {
        return DraftLayout.solve(width, height, ClientDraft.pool().size(), Math.max(1, ClientDraft.picksEach()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // vanilla background (and its blur pass) FIRST — calling this at the end
        // of the frame blurs the whole draft instead of the world behind it
        super.render(g, mouseX, mouseY, partialTick);
        DraftLayout.Layout L = layout();
        boolean mine = ClientDraft.yourTurn();
        if (requestedFor != ClientDraft.receivedAt() && !requested.isEmpty()
                && !ClientDraft.pool().contains(requested)) {
            requested = ""; // the server took it, or someone else did
        }

        g.fillGradient(0, 0, width, height, ClientPrefs.lit(0xFF14503C), ClientPrefs.lit(0xFF072A1F));
        for (int i = 0; i < 70; i++) {
            int sx = (i * 97 + 31) % Math.max(1, width);
            int sy = (i * 61 + 17) % Math.max(1, height);
            g.fill(sx, sy, sx + 1, sy + 1, 0x10FFFFFF);
        }

        // --- header: the title, which pick this is, and whose turn ---
        DraftLayout.Rect h = L.header();
        g.fill(0, 0, width, h.bottom(), 0xB4082A1E);
        g.fill(0, h.bottom(), width, h.bottom() + 1, GOLD_DIM);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(10, 8, 0);
        pose.scale(1.5f, 1.5f, 1f);
        g.drawString(font, "DRAFT", 0, 0, GOLD, true);
        pose.popPose();
        String opp = ClientDraft.opponent();
        String pickLine = "Pick " + Math.min(ClientDraft.pickNo(), ClientDraft.picksEach()) + " of "
                + ClientDraft.picksEach() + "  ·  vs " + opp;
        int chipW = Math.min(width / 2, font.width(pickLine) + 14);
        String fittedPick = fit(pickLine, chipW - 14);
        int cx = width / 2 - chipW / 2;
        g.fill(cx, 8, cx + chipW, 22, 0xC0081E16);
        g.renderOutline(cx, 8, chipW, 14, GOLD_DIM);
        g.drawString(font, fittedPick, cx + 7, 11, GOLD, false);
        String turn = mine ? "YOUR PICK  " + ClientDraft.secondsLeft() + "s"
                : opp.toUpperCase(Locale.ROOT) + " IS PICKING";
        turn = fit(turn, width - (cx + chipW) - 16);
        g.drawString(font, turn, width - 10 - font.width(turn), 11, mine ? YOU_ACCENT : OPP_ACCENT, mine);

        // --- the clock on this pick ---
        DraftLayout.Rect c = L.clock();
        float left = ClientDraft.clockLeft();
        g.fill(c.x(), c.y(), c.right(), c.bottom(), 0x80000000);
        int col = mine ? YOU_ACCENT : OPP_ACCENT;
        if (left < 0.25f && System.currentTimeMillis() / 250 % 2 == 0) {
            col = 0xFFF0625A;
        }
        g.fill(c.x(), c.y(), c.x() + Math.round(c.w() * left), c.bottom(), col);

        // --- the pool ---
        List<String> pool = ClientDraft.pool();
        int hovered = hoveredPool(L, mouseX, mouseY);
        for (int i = 0; i < pool.size(); i++) {
            MobCard card = MobCards.byId(pool.get(i));
            if (card == null) {
                continue;
            }
            DraftLayout.Rect r = L.poolCard(i, pool.size());
            boolean taking = pool.get(i).equals(requested);
            if (i == hovered && mine) {
                g.fill(r.x() - 3, r.y() - 3, r.right() + 3, r.bottom() + 3, 0x50FFD54A);
            }
            g.fill(r.x() + 2, r.y() + 3, r.right() + 2, r.bottom() + 3, 0x55000000);
            CardRenderer.renderCard(g, font, card, r.x(), r.y(), L.poolScale(), 0, 0, null, false, false);
            if (taking) {
                g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x88000000);
            } else if (!mine) {
                g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x30000000); // not yours to take yet
            }
            if (i == hovered) {
                g.renderOutline(r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, mine ? 0xFFFFD54A : EDGE);
            }
        }

        // --- the two rows of picks ---
        drawPicks(g, L, L.mine(), "YOUR PICKS", ClientDraft.mine(), YOU_ACCENT);
        drawPicks(g, L, L.theirs(), fit(opp.toUpperCase(Locale.ROOT) + "'S PICKS", L.theirs().w()),
                ClientDraft.theirs(), OPP_ACCENT);

        // --- footer: the hint and Leave ---
        DraftLayout.Rect f = L.footer();
        g.fill(0, f.y(), width, height, 0xB4082A1E);
        g.fill(0, f.y(), width, f.y() + 1, GOLD_DIM);
        String hint = mine ? "Click a card to take it  ·  hover to read it"
                : "Waiting for " + opp + "  ·  hover any card to read it";
        hint = fit(hint, L.leave().x() - 20);
        g.drawString(font, hint, 10, f.y() + 8, TEXT_DIM, false);
        long now = System.currentTimeMillis();
        boolean armed = leaveArmedAt > 0 && now - leaveArmedAt < LEAVE_CONFIRM_MS;
        DraftLayout.Rect lv = L.leave();
        boolean lHover = lv.contains(mouseX, mouseY);
        g.fill(lv.x(), lv.y(), lv.right(), lv.bottom(), lHover ? 0xFF7A3140 : armed ? 0xFF8A2020 : 0xFF5A2530);
        g.renderOutline(lv.x(), lv.y(), lv.w(), lv.h(), armed ? 0xFFF0625A : EDGE);
        String ll = armed ? "Quit?" : "Leave";
        g.drawString(font, ll, lv.x() + (lv.w() - font.width(ll)) / 2, lv.y() + 5, 0xFFFFFFFF, armed);

        // --- the hovered card, full size, with its mob ---
        if (hovered >= 0 && hovered < pool.size()) {
            preview(g, MobCards.byId(pool.get(hovered)), L.poolCard(hovered, pool.size()), mouseX, mouseY);
        } else {
            String picked = hoveredPick(L, mouseX, mouseY);
            if (picked != null) {
                preview(g, MobCards.byId(picked), null, mouseX, mouseY);
            }
        }
    }

    private void drawPicks(GuiGraphics g, DraftLayout.Layout L, DraftLayout.Rect row, String label,
                           List<String> ids, int accent) {
        g.drawString(font, label, row.x(), row.y() + 1, accent, false);
        int picks = Math.max(1, ClientDraft.picksEach());
        for (int i = 0; i < picks; i++) {
            DraftLayout.Rect s = L.slot(row, i);
            MobCard card = i < ids.size() ? MobCards.byId(ids.get(i)) : null;
            if (card == null) {
                g.fill(s.x(), s.y(), s.right(), s.bottom(), 0x40000000);
                g.renderOutline(s.x(), s.y(), s.w(), s.h(), 0x40FFFFFF);
            } else {
                CardRenderer.renderCard(g, font, card, s.x(), s.y(), L.miniScale(), 0, 0, null, false, false);
            }
        }
    }

    /**
     * A pool or pick card at a readable size, beside the cursor and clear of
     * the edges — and below the header, so it never hides whose pick it is or
     * how long is left on the clock.
     */
    private void preview(GuiGraphics g, MobCard card, DraftLayout.Rect beside, int mouseX, int mouseY) {
        if (card == null) {
            return;
        }
        int top = layout().clock().bottom() + 4;
        float scale = Mth.clamp((height - top - 8) / (float) DraftLayout.CARD_H, 0.2f, 0.62f);
        int w = Math.round(DraftLayout.CARD_W * scale);
        int h = Math.round(DraftLayout.CARD_H * scale);
        int x = mouseX + 14;
        if (x + w > width - 4) {
            x = mouseX - 14 - w;
        }
        x = Mth.clamp(x, 4, Math.max(4, width - w - 4));
        int y = Mth.clamp(mouseY - h / 2, top, Math.max(top, height - h - 4));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xC0000000);
        LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
        CardRenderer.renderCard(g, font, card, x, y, scale, mouseX, mouseY, mob, false, true);
        pose.popPose();
    }

    private int hoveredPool(DraftLayout.Layout L, int mouseX, int mouseY) {
        List<String> pool = ClientDraft.pool();
        for (int i = 0; i < pool.size(); i++) {
            if (L.poolCard(i, pool.size()).contains(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    private String hoveredPick(DraftLayout.Layout L, int mouseX, int mouseY) {
        int picks = Math.max(1, ClientDraft.picksEach());
        for (int side = 0; side < 2; side++) {
            DraftLayout.Rect row = side == 0 ? L.mine() : L.theirs();
            List<String> ids = side == 0 ? ClientDraft.mine() : ClientDraft.theirs();
            for (int i = 0; i < Math.min(picks, ids.size()); i++) {
                if (L.slot(row, i).contains(mouseX, mouseY)) {
                    return ids.get(i);
                }
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        DraftLayout.Layout L = layout();
        if (L.leave().contains(mouseX, mouseY)) {
            leave();
            return true;
        }
        int i = hoveredPool(L, (int) mouseX, (int) mouseY);
        if (i >= 0 && ClientDraft.yourTurn() && requested.isEmpty()) {
            String id = ClientDraft.pool().get(i);
            requested = id;
            requestedFor = ClientDraft.receivedAt();
            PacketDistributor.sendToServer(new DraftActionPayload(DraftActionPayload.PICK, MobCards.ordinal(id)));
            click(1.2f);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC asks first, like the Leave button
            leave();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        // a pick that the server answered clears the request with the new state
        if (!requested.isEmpty() && requestedFor != ClientDraft.receivedAt()) {
            requested = "";
        }
        if (!ClientDraft.active() && minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    /** Walking out ends the draft for both players, so it takes a second press. */
    private void leave() {
        long now = System.currentTimeMillis();
        if (leaveArmedAt < 0 || now - leaveArmedAt >= LEAVE_CONFIRM_MS) {
            leaveArmedAt = now;
            click(0.8f);
            return;
        }
        click(1.0f);
        PacketDistributor.sendToServer(new DraftActionPayload(DraftActionPayload.LEAVE, 0));
        ClientDraft.clear();
        onClose();
    }

    @Override
    public void onClose() {
        if (ClientDraft.active()) {
            // closed by any other route than Leave: still tell the server
            PacketDistributor.sendToServer(new DraftActionPayload(DraftActionPayload.LEAVE, 0));
            ClientDraft.clear();
        }
        super.onClose();
    }

    private String fit(String text, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width("…")));
        return cut.isEmpty() ? "" : cut + "…";
    }

    private void click(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch));
        }
    }
}
