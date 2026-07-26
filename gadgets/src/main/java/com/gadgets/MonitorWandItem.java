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
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The linking tool for the monitoring network. Click a Command Hub to select
 * it, then click Item Counters / Stock Monitors to put them on its board.
 * Click any container to clip it, then click a Storage Sensor to bind that
 * container to the sensor. Sneak-click a hub to clear its board.
 */
public class MonitorWandItem extends Item {
    public MonitorWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        PlayerEntity player = ctx.getPlayer();
        if (player == null) {
            return ActionResult.PASS;
        }
        BlockPos pos = ctx.getBlockPos();
        BlockEntity be = world.getBlockEntity(pos);
        ItemStack stack = ctx.getStack();
        String dim = world.getRegistryKey().getValue().toString();

        if (be instanceof CommandHubBlockEntity hub) {
            if (!world.isClient()) {
                if (player.isSneaking()) {
                    hub.clearNodes();
                    player.sendMessage(Text.literal("Command Hub ▸ board cleared").formatted(Formatting.RED), true);
                } else {
                    write(stack, "HubDim", dim, "HubPos", pos.asLong());
                    player.sendMessage(Text.literal("Wand ▸ hub selected — now click counters and monitors")
                            .formatted(Formatting.GREEN), true);
                }
            }
            return ActionResult.success(world.isClient());
        }

        if (be instanceof ItemCounterBlockEntity || be instanceof StockMonitorBlockEntity) {
            if (!world.isClient()) {
                linkToHub(world, player, stack, be, dim, pos);
            }
            return ActionResult.success(world.isClient());
        }

        if (be instanceof StorageSensorBlockEntity sensor) {
            if (!world.isClient()) {
                NbtCompound nbt = read(stack);
                if (!nbt.contains("ContDim")) {
                    player.sendMessage(Text.literal("Wand ▸ clip a container first (right-click it)")
                            .formatted(Formatting.RED), true);
                } else {
                    sensor.bind(nbt.getString("ContDim"), BlockPos.fromLong(nbt.getLong("ContPos")));
                    player.sendMessage(Text.literal("Storage Sensor ▸ bound to " + sensor.describeBinding())
                            .formatted(Formatting.GREEN), true);
                }
            }
            return ActionResult.success(world.isClient());
        }

        if (HopperBlockEntity.getInventoryAt(world, pos) != null) {
            if (!world.isClient()) {
                write(stack, "ContDim", dim, "ContPos", pos.asLong());
                player.sendMessage(Text.literal("Wand ▸ container clipped — now click a Storage Sensor")
                        .formatted(Formatting.AQUA), true);
            }
            return ActionResult.success(world.isClient());
        }
        return ActionResult.PASS;
    }

    private static void linkToHub(World world, PlayerEntity player, ItemStack stack, BlockEntity node,
                                  String nodeDim, BlockPos nodePos) {
        NbtCompound nbt = read(stack);
        if (!nbt.contains("HubDim")) {
            player.sendMessage(Text.literal("Wand ▸ select a Command Hub first (right-click it)")
                    .formatted(Formatting.RED), true);
            return;
        }
        MinecraftServer server = world.getServer();
        Identifier hubDim = Identifier.tryParse(nbt.getString("HubDim"));
        BlockPos hubPos = BlockPos.fromLong(nbt.getLong("HubPos"));
        ServerWorld hubWorld = server == null || hubDim == null
                ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, hubDim));
        if (hubWorld == null || !hubWorld.isChunkLoaded(hubPos.getX() >> 4, hubPos.getZ() >> 4)
                || !(hubWorld.getBlockEntity(hubPos) instanceof CommandHubBlockEntity hub)) {
            player.sendMessage(Text.literal("Wand ▸ that hub is gone or unloaded").formatted(Formatting.RED), true);
            return;
        }
        int type = node instanceof ItemCounterBlockEntity
                ? CommandHubBlockEntity.TYPE_COUNTER : CommandHubBlockEntity.TYPE_MONITOR;
        if (hub.addNode(type, nodeDim, nodePos)) {
            player.sendMessage(Text.literal("Wand ▸ linked to hub (" + hub.nodeCount() + "/"
                    + CommandHubBlockEntity.MAX_NODES + ")").formatted(Formatting.GREEN), true);
        } else {
            player.sendMessage(Text.literal("Wand ▸ already linked, or the board is full")
                    .formatted(Formatting.GOLD), true);
        }
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
        Tips.append(tooltip, "tip.gadgets.monitor_wand.1", "tip.gadgets.monitor_wand.2", "tip.gadgets.monitor_wand.3");
    }
}
