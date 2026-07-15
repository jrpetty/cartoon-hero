package com.gadgets;

import java.util.List;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A handheld wireless remote. Right-click anywhere to pulse its channel (a short
 * burst that a Redstone Receiver picks up — a garage-door opener for redstone).
 * Sneak + right-click a wireless block to bind that block to the remote's channel.
 */
public class WirelessRemoteItem extends Item {
    public WirelessRemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS; // let use() pulse instead
        }
        BlockEntity be = level.getBlockEntity(ctx.getClickedPos());
        if (!(be instanceof ChannelBlockEntity channel)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            String c = ensureChannel(ctx.getItemInHand(), level);
            channel.setChannel(c);
            player.displayClientMessage(Component.literal("Bound to remote channel " + c).withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            String c = ensureChannel(stack, level);
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal("Remote channel: " + c).withStyle(ChatFormatting.AQUA), true);
            } else {
                WirelessNetwork.publish(c, "remote|" + player.getUUID(), 15, level.getGameTime());
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.6F, 1.4F);
                player.displayClientMessage(Component.literal("Pulsed " + c).withStyle(ChatFormatting.GOLD), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static String ensureChannel(ItemStack stack, Level level) {
        String c = readChannel(stack);
        if (c.isEmpty()) {
            c = String.format("CH-%04X", level.getRandom().nextInt(0x10000));
            writeChannel(stack, c);
        }
        return c;
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
        Tips.append(tooltip, "tip.gadgets.wireless_remote.1", "tip.gadgets.wireless_remote.2");
    }
}
