package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.MapSelectPayload;
import com.jrpetty.aztecabyss.worldgen.ArenaMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The portal's arena picker: right-click a lit Abyss portal in the overworld and
 * this opens. Each arena gets a card showing its name, a difficulty tag, a short
 * description and your personal best round there, so the choice is informed and
 * there's always a record on the other map to go and beat.
 *
 * Rewards, rounds and scoring are identical whichever you pick.
 */
public final class MapSelectScreen extends Screen {

    private static final int CARD_W = 260;
    private static final int CARD_H = 58;
    private static final int GAP = 10;

    private final int[] bestRounds;
    private int selected;

    public MapSelectScreen(int currentChoice, int[] bestRounds) {
        super(Component.literal("Choose Your Hunt"));
        this.selected = currentChoice;
        this.bestRounds = bestRounds;
    }

    private int cardTop() {
        ArenaMap[] maps = ArenaMap.values();
        return this.height / 2 - (maps.length * (CARD_H + GAP)) / 2 + 4;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Enter this Hunt"), b -> {
                    PacketDistributor.sendToServer(new MapSelectPayload(selected));
                    onClose();
                })
                .bounds(this.width / 2 - 80, this.height - 42, 160, 20)
                .build());
    }

    /** The whole card is the click target, so selection feels like picking a tile. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = this.width / 2 - CARD_W / 2;
        int top = cardTop();
        ArenaMap[] maps = ArenaMap.values();
        for (int i = 0; i < maps.length; i++) {
            int y = top + i * (CARD_H + GAP);
            if (mouseX >= left && mouseX <= left + CARD_W && mouseY >= y && mouseY <= y + CARD_H) {
                selected = i;
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;

        g.drawCenteredString(this.font, Component.literal("CHOOSE YOUR HUNT").withStyle(s -> s.withBold(true)),
                cx, 26, 0xFFFFD24A);
        g.drawCenteredString(this.font, Component.literal("Same rounds, same rewards — different battlefield."),
                cx, 40, 0xFF888888);

        ArenaMap[] maps = ArenaMap.values();
        int top = cardTop();
        int left = cx - CARD_W / 2;

        for (int i = 0; i < maps.length; i++) {
            ArenaMap map = maps[i];
            int y = top + i * (CARD_H + GAP);
            boolean isSelected = i == selected;
            boolean hovered = mouseX >= left && mouseX <= left + CARD_W && mouseY >= y && mouseY <= y + CARD_H;

            // Card body, brightened when picked or hovered.
            g.fill(left, y, left + CARD_W, y + CARD_H, isSelected ? 0xCC1A1410 : hovered ? 0xAA141414 : 0x99101010);
            int edge = isSelected ? 0xFFFFD24A : hovered ? 0xFF7A6A3A : 0xFF3A3A3A;
            g.fill(left, y, left + CARD_W, y + 1, edge);
            g.fill(left, y + CARD_H - 1, left + CARD_W, y + CARD_H, edge);
            g.fill(left, y, left + 1, y + CARD_H, edge);
            g.fill(left + CARD_W - 1, y, left + CARD_W, y + CARD_H, edge);
            // Selected cards get a thick gold spine down the left.
            if (isSelected) {
                g.fill(left + 1, y + 1, left + 4, y + CARD_H - 1, 0xFFFFD24A);
            }

            // Title.
            g.drawString(this.font, Component.literal(map.title()).withStyle(s -> s.withBold(true)),
                    left + 10, y + 8, isSelected ? 0xFFFFE9A8 : 0xFFDDDDDD, false);

            // Difficulty pill, right-aligned.
            String tag = map.difficulty();
            int tagW = this.font.width(tag) + 8;
            int tagX = left + CARD_W - tagW - 8;
            g.fill(tagX, y + 6, tagX + tagW, y + 18, 0x66000000);
            g.fill(tagX, y + 6, tagX + tagW, y + 7, map.difficultyColor());
            g.drawString(this.font, Component.literal(tag), tagX + 4, y + 9, map.difficultyColor(), false);

            // Blurb, wrapped across the card.
            int by = y + 22;
            for (net.minecraft.util.FormattedCharSequence line
                    : this.font.split(Component.literal(map.blurb()), CARD_W - 20)) {
                g.drawString(this.font, line, left + 10, by, 0xFF9A9A9A, false);
                by += 10;
            }

            // Personal best on this arena - the record to beat.
            int best = (i < bestRounds.length) ? bestRounds[i] : 0;
            String rec = best > 0 ? "Your best: Round " + best : "Never attempted";
            int recW = this.font.width(rec);
            g.drawString(this.font, Component.literal(rec),
                    left + CARD_W - recW - 10, y + CARD_H - 13,
                    best > 0 ? 0xFF6EC8FF : 0xFF666666, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
