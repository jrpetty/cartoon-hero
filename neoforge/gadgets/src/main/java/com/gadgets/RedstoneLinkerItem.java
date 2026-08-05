package com.gadgets;

import java.util.List;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Links wireless redstone blocks. Right-click a transmitter or receiver to copy
 * its channel onto the tool (a fresh channel is minted if the block has none);
 * sneak-right-click another block to write that stored channel onto it.
 */
public class RedstoneLinkerItem extends Item {
    public RedstoneLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockEntity be = level.getBlockEntity(ctx.getClickedPos());
        Player player = ctx.getPlayer();
        // Item transfer moved to codes: a receiver mints one and a sender is
        // typed into. The linker would appear to work here and quietly do
        // nothing, since a receiver's address is no longer something to paste.
        if (be instanceof TransferNode node) {
            if (!level.isClientSide() && player != null) {
                player.displayClientMessage(Component.literal(
                                "These pair by code — read the receiver's, then type it into the sender.")
                        .withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!(be instanceof ChannelBlockEntity channel)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player != null) {
            ItemStack stack = ctx.getItemInHand();
            String stored = readChannel(stack);
            String blockChannel = channel.getChannel();
            // Natural flow: click the first block to pick up its channel, then click
            // the second to paste it. Sneak forces a fresh copy from the block clicked.
            if (!stored.isEmpty() && !stored.equals(blockChannel) && !player.isShiftKeyDown()) {
                channel.setChannel(stored);
                player.displayClientMessage(Component.literal("Linked to channel " + stored + " ✓").withStyle(ChatFormatting.GREEN), true);
            } else {
                String c = blockChannel;
                if (c.isEmpty()) {
                    c = String.format("CH-%04X", level.getRandom().nextInt(0x10000));
                    channel.setChannel(c);
                }
                writeChannel(stack, c);
                player.displayClientMessage(Component.literal("Copied " + c + " — now click the other block to link it")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static String readChannel(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString("channel");
    }

    private static void writeChannel(ItemStack stack, String channel) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        tag.putString("channel", channel);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String stored = readChannel(stack);
        if (!stored.isEmpty()) {
            tooltip.add(Component.literal("Holding: " + stored).withStyle(ChatFormatting.AQUA));
        }
        Tips.append(tooltip, "tip.gadgets.redstone_linker.1", "tip.gadgets.redstone_linker.2");
    }
}
