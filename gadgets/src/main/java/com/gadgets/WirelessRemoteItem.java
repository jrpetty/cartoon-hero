package com.gadgets;

import java.util.List;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.util.Formatting;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A handheld wireless remote. Right-click anywhere to pulse its channel (a short
 * burst that a Redstone Receiver picks up — a garage-door opener for redstone).
 * Sneak + right-click a wireless block to bind that block to the remote's channel.
 */
public class WirelessRemoteItem extends Item {
    public WirelessRemoteItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        PlayerEntity player = ctx.getPlayer();
        if (player == null || !player.isSneaking()) {
            return ActionResult.PASS; // let use() pulse instead
        }
        BlockEntity be = world.getBlockEntity(ctx.getBlockPos());
        if (!(be instanceof ChannelBlockEntity channel)) {
            return ActionResult.PASS;
        }
        if (!world.isClient()) {
            String c = ensureChannel(ctx.getStack(), world);
            channel.setChannel(c);
            player.sendMessage(Text.literal("Bound to remote channel " + c).formatted(Formatting.GREEN), true);
        }
        return ActionResult.success(world.isClient());
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient()) {
            String c = ensureChannel(stack, world);
            if (player.isSneaking()) {
                player.sendMessage(Text.literal("Remote channel: " + c).formatted(Formatting.AQUA), true);
            } else {
                WirelessNetwork.publish(c, "remote|" + player.getUuid(), 15, world.getTime());
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.6F, 1.4F);
                player.sendMessage(Text.literal("Pulsed " + c).formatted(Formatting.GOLD), true);
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    private static String ensureChannel(ItemStack stack, World world) {
        String c = readChannel(stack);
        if (c.isEmpty()) {
            c = String.format("CH-%04X", world.getRandom().nextInt(0x10000));
            writeChannel(stack, c);
        }
        return c;
    }

    private static String readChannel(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data == null ? "" : data.copyNbt().getString("channel");
    }

    private static void writeChannel(ItemStack stack, String channel) {
        NbtComponent data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound nbt = data.copyNbt();
        nbt.putString("channel", channel);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        Tips.append(tooltip, "tip.gadgets.wireless_remote.1", "tip.gadgets.wireless_remote.2");
    }
}
