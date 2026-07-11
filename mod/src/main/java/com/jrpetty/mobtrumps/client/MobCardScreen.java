package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.MobCard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Full-size card view in the style of a classic Top Trumps card,
 * opened by right-clicking a Mob Card or from the collection book.
 */
public class MobCardScreen extends Screen {

    private final MobCard card;
    private final Screen parent;
    private final boolean foil;
    private final Map<String, LivingEntity> entityCache = new HashMap<>();

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
        int x = (width - CardRenderer.CARD_W) / 2;
        int y = (height - CardRenderer.CARD_H) / 2;

        // soft drop shadow behind the card
        g.fill(x + 4, y + 6, x + CardRenderer.CARD_W + 8, y + CardRenderer.CARD_H + 10, 0x66000000);

        LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
        CardRenderer.renderCard(g, font, card, x, y, 1f, mouseX, mouseY, mob, foil);

        String hint = parent != null ? "Press ESC to return to the book" : "Press ESC to close";
        g.drawString(font, hint, (width - font.width(hint)) / 2,
                y + CardRenderer.CARD_H + 10, 0xFFAAAAAA, true);
    }
}
