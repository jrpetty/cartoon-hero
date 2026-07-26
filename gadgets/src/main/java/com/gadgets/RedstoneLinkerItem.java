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
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;

/**
 * Links wireless redstone blocks. Right-click a transmitter or receiver to copy
 * its channel onto the tool (a fresh channel is minted if the block has none);
 * sneak-right-click another block to write that stored channel onto it.
 */
public class RedstoneLinkerItem extends Item {
    public RedstoneLinkerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        BlockEntity be = world.getBlockEntity(ctx.getBlockPos());
        PlayerEntity player = ctx.getPlayer();
        if (!(be instanceof ChannelBlockEntity channel)) {
            return ActionResult.PASS;
        }
        if (!world.isClient() && player != null) {
            ItemStack stack = ctx.getStack();
            if (player.isSneaking()) {
                String stored = readChannel(stack);
                if (stored.isEmpty()) {
                    player.sendMessage(Text.literal("No channel stored — right-click a block first.").formatted(Formatting.RED), true);
                } else {
                    channel.setChannel(stored);
                    player.sendMessage(Text.literal("Linked to channel " + stored).formatted(Formatting.GREEN), true);
                }
            } else {
                String c = channel.getChannel();
                if (c.isEmpty()) {
                    c = String.format("CH-%04X", world.getRandom().nextInt(0x10000));
                    channel.setChannel(c);
                }
                writeChannel(stack, c);
                player.sendMessage(Text.literal("Copied channel " + c).formatted(Formatting.AQUA), true);
            }
        }
        return ActionResult.success(world.isClient());
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
        Tips.append(tooltip, "tip.gadgets.redstone_linker.1", "tip.gadgets.redstone_linker.2");
    }
}
