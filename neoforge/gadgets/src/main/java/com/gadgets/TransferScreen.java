package com.gadgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * The Item Sender and Receiver share this: which way the block moves items,
 * what channel it is on, what level it has reached, and what the next level
 * costs.
 *
 * <p>The direction line is the point of the top half. Both blocks are cubes
 * that look the same from every angle, and which end of them items go in and
 * out of was previously something you had to know rather than something you
 * could see.
 */
public class TransferScreen extends GadgetScreen {
    private static final int FLOW_Y = 20;
    private static final int CHANNEL_Y = 56;
    private static final int LEVEL_Y = 76;
    private static final int BAR_Y = 90;
    private static final int COST_Y = 108;
    private static final int BUTTON_Y = 148;

    private final TransferNode node;
    private final boolean sender;

    public TransferScreen(TransferNode node, boolean sender) {
        super(Component.literal(sender ? "Item Sender" : "Item Receiver"), 220, BUTTON_Y + 26);
        this.node = node;
        this.sender = sender;
    }

    @Override
    protected void init() {
        super.init();
        boolean maxed = node.getTier() >= TransferNode.MAX_TIER;
        addRenderableWidget(Button.builder(
                        Component.literal(maxed ? "Fully upgraded" : "Upgrade to level " + (node.getTier() + 1)),
                        b -> {
                            send(node.getBlockPos(), "transfer_upgrade", 0);
                            rebuildWidgets();
                        })
                .bounds(left + 12, top + BUTTON_Y, 196, 18).build())
                .active = !maxed;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;

        // Which way the items actually travel, spelled out.
        gfx.drawString(font, sender ? "▲  Takes from the inventory ABOVE" : "Delivers into the inventory BELOW  ▼",
                x, top + FLOW_Y, AMBER, false);
        gfx.drawString(font, sender ? "then teleports it to a Receiver" : "after a Sender teleports it here",
                x, top + FLOW_Y + 12, GRAY, false);

        String channel = node.getChannel();
        gfx.drawString(font, channel.isEmpty() ? "No channel — pair it with a Redstone Linker" : "Channel: " + channel,
                x, top + CHANNEL_Y, channel.isEmpty() ? RED : GREEN, false);

        int tier = node.getTier();
        gfx.drawString(font, "Level " + tier + " of " + TransferNode.MAX_TIER
                + "   ·   " + TransferNode.ratePerMinute(tier) + " items/min", x, top + LEVEL_Y, AMBER, false);

        // One pip per level, so progress is legible at a glance.
        int pip = 30;
        for (int i = 1; i <= TransferNode.MAX_TIER; i++) {
            int px = x + (i - 1) * (pip + 2);
            gfx.fill(px, top + BAR_Y, px + pip, top + BAR_Y + 8, i <= tier ? AMBER : FRAME);
        }

        if (tier >= TransferNode.MAX_TIER) {
            gfx.drawString(font, "This end is as fast as it goes.", x, top + COST_Y, GREEN, false);
        } else {
            gfx.drawString(font, "Next level costs, from your inventory:", x, top + COST_Y, GRAY, false);
            gfx.drawString(font, "· " + TransferNode.UPGRADE_IRON_BLOCKS + " Blocks of Iron",
                    x, top + COST_Y + 12, DIM, false);
            gfx.drawString(font, "· " + TransferNode.UPGRADE_ENDER_PEARLS + " Ender Pearls",
                    x, top + COST_Y + 22, DIM, false);
            gfx.drawString(font, "· " + TransferNode.UPGRADE_HOPPERS + " Hopper", x, top + COST_Y + 32, DIM, false);
        }
    }
}
