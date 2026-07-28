package com.gadgets;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * The linking tool for the monitoring network.
 *
 * <p>Click a Command Hub to select it, then click Item Counters and Stock
 * Monitors to put them on its board (sneak-click a linked one to remove it).
 * Click any container to clip it, then click a Storage Sensor to bind that
 * container to the sensor.
 *
 * <p>The wand is dispatched from a right-click <em>event</em> rather than the
 * normal item hook, because most of the blocks it targets — the hub, the
 * counter, the monitor, and vanilla chests — all open a screen on click and
 * would swallow the interaction before an item ever saw it. See
 * {@link #handle} and its registration in {@link Gadgets}.
 */
public class MonitorWandItem extends Item {
    public MonitorWandItem(Properties properties) {
        super(properties);
    }

    /**
     * Runs the wand's action for a right-click on {@code pos}.
     *
     * @return true when the wand consumed the click (so the block's own
     *         interaction must be suppressed).
     */
    public static boolean handle(Level level, Player player, ItemStack stack, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        boolean isGadget = be instanceof CommandHubBlockEntity || be instanceof ItemCounterBlockEntity
                || be instanceof StockMonitorBlockEntity || be instanceof StorageSensorBlockEntity;
        boolean isContainer = !isGadget && HopperBlockEntity.getContainerAt(level, pos) != null;
        if (!isGadget && !isContainer) {
            return false; // not a wand target — let the block behave normally
        }
        if (level.isClientSide()) {
            return true; // swing the arm; the server does the real work
        }

        String dim = level.dimension().location().toString();
        if (be instanceof CommandHubBlockEntity hub) {
            write(stack, "HubDim", dim, "HubPos", pos.asLong());
            say(level, player, pos, Component.literal("Wand ▸ hub selected (" + hub.nodeCount()
                    + " linked) — now click counters and monitors").withStyle(ChatFormatting.GREEN));
        } else if (be instanceof ItemCounterBlockEntity || be instanceof StockMonitorBlockEntity) {
            if (player.isShiftKeyDown()) {
                unlinkFromHub(level, player, stack, dim, pos);
            } else {
                linkToHub(level, player, stack, be, dim, pos);
            }
        } else if (be instanceof StorageSensorBlockEntity sensor) {
            CompoundTag nbt = read(stack);
            if (!nbt.contains("ContDim")) {
                say(level, player, pos, Component.literal("Wand ▸ clip a container first, then click this sensor")
                        .withStyle(ChatFormatting.RED));
            } else {
                sensor.bind(nbt.getString("ContDim"), BlockPos.of(nbt.getLong("ContPos")));
                say(level, player, pos, Component.literal("Storage Sensor ▸ bound to " + sensor.describeBinding())
                        .withStyle(ChatFormatting.GREEN));
            }
        } else {
            write(stack, "ContDim", dim, "ContPos", pos.asLong());
            say(level, player, pos, Component.literal("Wand ▸ container clipped — now click a Storage Sensor")
                    .withStyle(ChatFormatting.AQUA));
        }
        return true;
    }

    private static void say(Level level, Player player, BlockPos pos, Component message) {
        player.displayClientMessage(message, true);
        level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.7F, 1.5F);
    }

    private static void linkToHub(Level level, Player player, ItemStack stack, BlockEntity node,
                                  String nodeDim, BlockPos nodePos) {
        CommandHubBlockEntity hub = resolveHub(level, stack);
        if (hub == null) {
            say(level, player, nodePos, Component.literal("Wand ▸ select a Command Hub first (click one with the wand)")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        int type = node instanceof ItemCounterBlockEntity
                ? CommandHubBlockEntity.TYPE_COUNTER : CommandHubBlockEntity.TYPE_MONITOR;
        if (hub.addNode(type, nodeDim, nodePos)) {
            say(level, player, nodePos, Component.literal("Wand ▸ linked to hub (" + hub.nodeCount() + "/"
                    + CommandHubBlockEntity.MAX_NODES + ")").withStyle(ChatFormatting.GREEN));
        } else {
            say(level, player, nodePos, Component.literal("Wand ▸ already linked (sneak-click to unlink), or the board is full")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private static void unlinkFromHub(Level level, Player player, ItemStack stack,
                                      String nodeDim, BlockPos nodePos) {
        CommandHubBlockEntity hub = resolveHub(level, stack);
        if (hub == null) {
            say(level, player, nodePos, Component.literal("Wand ▸ select a Command Hub first").withStyle(ChatFormatting.RED));
            return;
        }
        if (hub.removeNode(nodeDim, nodePos)) {
            say(level, player, nodePos, Component.literal("Wand ▸ unlinked from hub (" + hub.nodeCount() + " left)")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            say(level, player, nodePos, Component.literal("Wand ▸ that isn't on the selected hub").withStyle(ChatFormatting.GOLD));
        }
    }

    /** The hub stored on the wand, or null when it is gone, unloaded, or unset. */
    private static CommandHubBlockEntity resolveHub(Level level, ItemStack stack) {
        CompoundTag nbt = read(stack);
        if (!nbt.contains("HubDim")) {
            return null;
        }
        MinecraftServer server = level.getServer();
        ResourceLocation hubDim = ResourceLocation.tryParse(nbt.getString("HubDim"));
        if (server == null || hubDim == null) {
            return null;
        }
        ServerLevel hubLevel = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, hubDim));
        BlockPos hubPos = BlockPos.of(nbt.getLong("HubPos"));
        if (hubLevel == null || !hubLevel.isLoaded(hubPos)) {
            return null;
        }
        return hubLevel.getBlockEntity(hubPos) instanceof CommandHubBlockEntity hub ? hub : null;
    }

    /** Fallback path: only reached for targets the event hook let through. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return handle(ctx.getLevel(), player, ctx.getItemInHand(), ctx.getClickedPos())
                ? InteractionResult.sidedSuccess(ctx.getLevel().isClientSide())
                : InteractionResult.PASS;
    }

    private static CompoundTag read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private static void write(ItemStack stack, String keyDim, String dim, String keyPos, long pos) {
        CompoundTag nbt = read(stack);
        nbt.putString(keyDim, dim);
        nbt.putLong(keyPos, pos);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag nbt = read(stack);
        if (nbt.contains("HubPos")) {
            BlockPos p = BlockPos.of(nbt.getLong("HubPos"));
            tooltip.add(Component.literal("Hub: " + p.getX() + ", " + p.getY() + ", " + p.getZ())
                    .withStyle(ChatFormatting.GREEN));
        }
        if (nbt.contains("ContPos")) {
            BlockPos p = BlockPos.of(nbt.getLong("ContPos"));
            tooltip.add(Component.literal("Container: " + p.getX() + ", " + p.getY() + ", " + p.getZ())
                    .withStyle(ChatFormatting.AQUA));
        }
        Tips.append(tooltip, "tip.gadgets.monitor_wand.1", "tip.gadgets.monitor_wand.2", "tip.gadgets.monitor_wand.3");
    }
}
