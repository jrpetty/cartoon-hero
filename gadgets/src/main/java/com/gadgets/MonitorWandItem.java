package com.gadgets;

import java.util.List;

import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.util.Formatting;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
    public MonitorWandItem(Settings settings) {
        super(settings);
    }

    /**
     * Runs the wand's action for a right-click on {@code pos}.
     *
     * @return true when the wand consumed the click (so the block's own
     *         interaction must be suppressed).
     */
    public static boolean handle(World world, PlayerEntity player, ItemStack stack, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        boolean isGadget = be instanceof CommandHubBlockEntity || be instanceof ItemCounterBlockEntity
                || be instanceof StockMonitorBlockEntity || be instanceof StorageSensorBlockEntity;
        boolean isContainer = !isGadget && HopperBlockEntity.getInventoryAt(world, pos) != null;
        if (!isGadget && !isContainer) {
            return false; // not a wand target — let the block behave normally
        }
        if (world.isClient()) {
            return true; // swing the arm; the server does the real work
        }

        String dim = world.getRegistryKey().getValue().toString();
        if (be instanceof CommandHubBlockEntity hub) {
            write(stack, "HubDim", dim, "HubPos", pos.asLong());
            say(world, player, pos, Text.literal("Wand ▸ hub selected (" + hub.nodeCount()
                    + " linked) — now click counters and monitors").formatted(Formatting.GREEN));
        } else if (be instanceof ItemCounterBlockEntity || be instanceof StockMonitorBlockEntity) {
            if (player.isSneaking()) {
                unlinkFromHub(world, player, stack, dim, pos);
            } else {
                linkToHub(world, player, stack, be, dim, pos);
            }
        } else if (be instanceof StorageSensorBlockEntity sensor) {
            NbtCompound nbt = read(stack);
            if (!nbt.contains("ContDim")) {
                say(world, player, pos, Text.literal("Wand ▸ clip a container first, then click this sensor")
                        .formatted(Formatting.RED));
            } else {
                sensor.bind(nbt.getString("ContDim"), BlockPos.fromLong(nbt.getLong("ContPos")));
                say(world, player, pos, Text.literal("Storage Sensor ▸ bound to " + sensor.describeBinding())
                        .formatted(Formatting.GREEN));
            }
        } else {
            write(stack, "ContDim", dim, "ContPos", pos.asLong());
            say(world, player, pos, Text.literal("Wand ▸ container clipped — now click a Storage Sensor")
                    .formatted(Formatting.AQUA));
        }
        return true;
    }

    private static void say(World world, PlayerEntity player, BlockPos pos, Text message) {
        player.sendMessage(message, true);
        world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.7F, 1.5F);
    }

    private static void linkToHub(World world, PlayerEntity player, ItemStack stack, BlockEntity node,
                                  String nodeDim, BlockPos nodePos) {
        CommandHubBlockEntity hub = resolveHub(world, stack);
        if (hub == null) {
            say(world, player, nodePos, Text.literal("Wand ▸ select a Command Hub first (click one with the wand)")
                    .formatted(Formatting.RED));
            return;
        }
        int type = node instanceof ItemCounterBlockEntity
                ? CommandHubBlockEntity.TYPE_COUNTER : CommandHubBlockEntity.TYPE_MONITOR;
        if (hub.addNode(type, nodeDim, nodePos)) {
            say(world, player, nodePos, Text.literal("Wand ▸ linked to hub (" + hub.nodeCount() + "/"
                    + CommandHubBlockEntity.MAX_NODES + ")").formatted(Formatting.GREEN));
        } else {
            say(world, player, nodePos, Text.literal("Wand ▸ already linked (sneak-click to unlink), or the board is full")
                    .formatted(Formatting.GOLD));
        }
    }

    private static void unlinkFromHub(World world, PlayerEntity player, ItemStack stack,
                                      String nodeDim, BlockPos nodePos) {
        CommandHubBlockEntity hub = resolveHub(world, stack);
        if (hub == null) {
            say(world, player, nodePos, Text.literal("Wand ▸ select a Command Hub first").formatted(Formatting.RED));
            return;
        }
        if (hub.removeNode(nodeDim, nodePos)) {
            say(world, player, nodePos, Text.literal("Wand ▸ unlinked from hub (" + hub.nodeCount() + " left)")
                    .formatted(Formatting.GOLD));
        } else {
            say(world, player, nodePos, Text.literal("Wand ▸ that isn't on the selected hub").formatted(Formatting.GOLD));
        }
    }

    /** The hub stored on the wand, or null when it is gone, unloaded, or unset. */
    private static CommandHubBlockEntity resolveHub(World world, ItemStack stack) {
        NbtCompound nbt = read(stack);
        if (!nbt.contains("HubDim")) {
            return null;
        }
        MinecraftServer server = world.getServer();
        Identifier hubDim = Identifier.tryParse(nbt.getString("HubDim"));
        if (server == null || hubDim == null) {
            return null;
        }
        ServerWorld hubWorld = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, hubDim));
        BlockPos hubPos = BlockPos.fromLong(nbt.getLong("HubPos"));
        if (hubWorld == null || !hubWorld.isChunkLoaded(hubPos.getX() >> 4, hubPos.getZ() >> 4)) {
            return null;
        }
        return hubWorld.getBlockEntity(hubPos) instanceof CommandHubBlockEntity hub ? hub : null;
    }

    /** Fallback path: only reached for targets the event hook let through. */
    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        PlayerEntity player = ctx.getPlayer();
        if (player == null) {
            return ActionResult.PASS;
        }
        return handle(ctx.getWorld(), player, ctx.getStack(), ctx.getBlockPos())
                ? ActionResult.success(ctx.getWorld().isClient())
                : ActionResult.PASS;
    }

    private static NbtCompound read(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data == null ? new NbtCompound() : data.copyNbt();
    }

    private static void write(ItemStack stack, String keyDim, String dim, String keyPos, long pos) {
        NbtCompound nbt = read(stack);
        nbt.putString(keyDim, dim);
        nbt.putLong(keyPos, pos);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        NbtCompound nbt = read(stack);
        if (nbt.contains("HubPos")) {
            BlockPos p = BlockPos.fromLong(nbt.getLong("HubPos"));
            tooltip.add(Text.literal("Hub: " + p.getX() + ", " + p.getY() + ", " + p.getZ())
                    .formatted(Formatting.GREEN));
        }
        if (nbt.contains("ContPos")) {
            BlockPos p = BlockPos.fromLong(nbt.getLong("ContPos"));
            tooltip.add(Text.literal("Container: " + p.getX() + ", " + p.getY() + ", " + p.getZ())
                    .formatted(Formatting.AQUA));
        }
        Tips.append(tooltip, "tip.gadgets.monitor_wand.1", "tip.gadgets.monitor_wand.2", "tip.gadgets.monitor_wand.3");
    }
}
