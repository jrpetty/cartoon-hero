package com.gadgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Item Sender and Receiver share this: a name, which way the block moves
 * items, what it is carrying, what level it has reached and what the next one
 * costs.
 *
 * <p>Two things are drawn rather than written, because both were the actual
 * complaints. The flow strip shows the chest, the block and the portal in the
 * order items pass through them, so which end is which stops being something
 * you have to remember. And the upgrade cost is drawn as the three item icons
 * with what you are carrying against what it takes, so a refused upgrade says
 * which ingredient you are short of before you press anything.
 */
public class TransferScreen extends GadgetScreen {
    private static final int NAME_Y = 18;
    /** The flow strip's box; its caption sits {@value #FLOW_CAPTION} below the top. */
    private static final int FLOW_Y = 40;
    private static final int FLOW_H = 30;
    private static final int FLOW_CAPTION = 34;
    private static final int CHANNEL_Y = 88;
    private static final int CARGO_Y = 102;
    private static final int LEVEL_Y = 126;
    private static final int PIPS_Y = 140;
    private static final int COST_Y = 156;
    private static final int COST_ROW = 170;
    private static final int BUTTON_Y = 194;

    private static final int SLOT_BG = 0xFF20262E;
    private static final int PORTAL = 0xFF9660DC;

    private final TransferNode node;
    private final boolean sender;
    private EditBox nameField;
    private Button upgrade;
    private String shownName = "";

    public TransferScreen(TransferNode node, boolean sender) {
        super(Component.literal(sender ? "Item Sender" : "Item Receiver"), 240, BUTTON_Y + 28);
        this.node = node;
        this.sender = sender;
    }

    @Override
    protected void init() {
        super.init();
        shownName = node.getCustomName();
        nameField = new EditBox(font, left + 52, top + NAME_Y, 116, 14, Component.literal("Name"));
        nameField.setMaxLength(24);
        nameField.setHint(Component.literal(sender ? "name this sender" : "name this receiver"));
        nameField.setValue(shownName);
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.literal("Save"), b ->
                        sendText(node.getBlockPos(), "set_name", nameField.getValue()))
                .bounds(left + 172, top + NAME_Y, 56, 14).build());

        boolean maxed = node.getTier() >= TransferNode.MAX_TIER;
        upgrade = addRenderableWidget(Button.builder(
                        Component.literal(maxed
                                ? "Fully upgraded"
                                : "Upgrade to level " + (node.getTier() + 1)),
                        b -> {
                            send(node.getBlockPos(), "transfer_upgrade", 0);
                            rebuildWidgets();
                        })
                .bounds(left + 12, top + BUTTON_Y, 216, 18).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        String current = node.getCustomName();
        if (!current.equals(shownName) && !nameField.isFocused()) {
            shownName = current;
            nameField.setValue(current);
        }

        // Re-checked every frame: dropping the pearls into a chest while the
        // screen is open should grey the button out, not fail on the press.
        if (upgrade != null) {
            upgrade.active = node.getTier() < TransferNode.MAX_TIER && affordable();
        }

        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;
        gfx.drawString(font, "Name", x, top + NAME_Y + 4, DIM, false);

        flowStrip(gfx, x);

        String channel = node.getChannel();
        gfx.drawString(font, channel.isEmpty()
                        ? "No channel — pair it with a Redstone Linker"
                        : "Channel  " + channel,
                x, top + CHANNEL_Y, channel.isEmpty() ? RED : GREEN, false);

        cargo(gfx, x);
        level(gfx, x);
        cost(gfx, x, mouseX, mouseY);
    }

    /**
     * Chest → block → portal, in the order an item actually passes through
     * them, mirrored for a receiver. The arrow between the chest and the block
     * is the whole point: it is the only thing in the game that says which end
     * of these cubes the items use.
     */
    private void flowStrip(GuiGraphics gfx, int x) {
        int y = top + FLOW_Y;
        gfx.fill(x, y, left + panelW - 12, y + FLOW_H, SLOT_BG);
        gfx.renderOutline(x, y, panelW - 24, FLOW_H, FRAME);

        String a = sender ? "CHEST ABOVE" : "PORTAL";
        String b = sender ? "PORTAL" : "CHEST BELOW";
        gfx.drawString(font, a, x + 8, y + 11, sender ? AMBER : PORTAL, false);
        int mid = left + panelW / 2;
        gfx.drawString(font, "▶", mid - 4, y + 11, GRAY, false);
        int bw = font.width(b);
        gfx.drawString(font, b, left + panelW - 20 - bw, y + 11, sender ? PORTAL : AMBER, false);

        gfx.drawString(font, sender
                        ? "Takes one item at a time from the inventory ABOVE"
                        : "Drops what arrives into the inventory BELOW",
                x, y + FLOW_CAPTION, GRAY, false);
    }

    /** What the block is carrying, drawn as the item itself. */
    private void cargo(GuiGraphics gfx, int x) {
        ItemStack stack = node.flowing();
        gfx.fill(x, top + CARGO_Y, x + 18, top + CARGO_Y + 18, SLOT_BG);
        gfx.renderOutline(x, top + CARGO_Y, 18, 18, FRAME);
        if (!stack.isEmpty()) {
            gfx.renderItem(stack, x + 1, top + CARGO_Y + 1);
        }
        gfx.drawString(font, sender ? "Next through the portal" : "Last through the portal",
                x + 24, top + CARGO_Y + 1, DIM, false);
        gfx.drawString(font, stack.isEmpty()
                        ? (sender ? "nothing to send" : "nothing yet")
                        : stack.getHoverName().getString(),
                x + 24, top + CARGO_Y + 11, stack.isEmpty() ? GRAY : AMBER, false);
    }

    /** The level, its rate, and a chevron per level so progress reads at a glance. */
    private void level(GuiGraphics gfx, int x) {
        int tier = node.getTier();
        gfx.drawString(font, "LEVEL " + tier + " / " + TransferNode.MAX_TIER,
                x, top + LEVEL_Y, AMBER, false);
        String rate = TransferNode.ratePerMinute(tier) + " items/min";
        gfx.drawString(font, rate, left + panelW - 12 - font.width(rate), top + LEVEL_Y, GREEN, false);

        int gap = 3;
        int pip = (panelW - 24 - gap * (TransferNode.MAX_TIER - 1)) / TransferNode.MAX_TIER;
        for (int i = 1; i <= TransferNode.MAX_TIER; i++) {
            int px = x + (i - 1) * (pip + gap);
            gfx.fill(px, top + PIPS_Y, px + pip, top + PIPS_Y + 8, i <= tier ? AMBER : FRAME);
            // A lit pip gets a darker underline, so the lit run reads as a bar
            // rather than as a row of identical blocks.
            if (i <= tier) {
                gfx.fill(px, top + PIPS_Y + 6, px + pip, top + PIPS_Y + 8, DIM);
            }
        }
    }

    /** The upgrade price, as icons with carried-against-needed under each. */
    private void cost(GuiGraphics gfx, int x, int mouseX, int mouseY) {
        if (node.getTier() >= TransferNode.MAX_TIER) {
            gfx.drawString(font, "This end is as fast as it goes.", x, top + COST_Y, GREEN, false);
            gfx.drawString(font, "A link still runs at its slower end.", x, top + COST_ROW, GRAY, false);
            return;
        }
        gfx.drawString(font, "Next level costs", x, top + COST_Y, DIM, false);

        Item[] items = {Items.IRON_BLOCK, Items.ENDER_PEARL, Items.HOPPER};
        int[] needs = {TransferNode.UPGRADE_IRON_BLOCKS, TransferNode.UPGRADE_ENDER_PEARLS,
                TransferNode.UPGRADE_HOPPERS};
        for (int i = 0; i < items.length; i++) {
            int ix = x + i * 74;
            int iy = top + COST_ROW;
            gfx.fill(ix, iy, ix + 18, iy + 18, SLOT_BG);
            gfx.renderOutline(ix, iy, 18, 18, FRAME);
            ItemStack icon = new ItemStack(items[i]);
            gfx.renderItem(icon, ix + 1, iy + 1);
            int have = carried(items[i]);
            boolean enough = have >= needs[i];
            gfx.drawString(font, have + " / " + needs[i], ix + 22, iy + 5, enough ? GREEN : RED, false);
            if (mouseX >= ix && mouseX < ix + 18 && mouseY >= iy && mouseY < iy + 18) {
                gfx.renderTooltip(font, icon, mouseX, mouseY);
            }
        }
    }

    private boolean affordable() {
        return minecraft != null && minecraft.player != null
                && (minecraft.player.getAbilities().instabuild
                || (carried(Items.IRON_BLOCK) >= TransferNode.UPGRADE_IRON_BLOCKS
                && carried(Items.ENDER_PEARL) >= TransferNode.UPGRADE_ENDER_PEARLS
                && carried(Items.HOPPER) >= TransferNode.UPGRADE_HOPPERS));
    }

    private int carried(Item item) {
        if (minecraft == null || minecraft.player == null) {
            return 0;
        }
        int found = 0;
        var inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        return found;
    }
}
