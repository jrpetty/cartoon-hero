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
 * The linking tool for the monitoring network. Click a Command Hub to select
 * it, then click Item Counters / Stock Monitors to put them on its board.
 * Click any container to clip it, then click a Storage Sensor to bind that
 * container to the sensor. Sneak-click a hub to clear its board.
 */
public class MonitorWandItem extends Item {
    public MonitorWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        ItemStack stack = ctx.getItemInHand();
        String dim = level.dimension().location().toString();

        if (be instanceof CommandHubBlockEntity hub) {
            if (!level.isClientSide()) {
                if (player.isShiftKeyDown()) {
                    hub.clearNodes();
                    player.displayClientMessage(Component.literal("Command Hub ▸ board cleared").withStyle(ChatFormatting.RED), true);
                } else {
                    write(stack, "HubDim", dim, "HubPos", pos.asLong());
                    player.displayClientMessage(Component.literal("Wand ▸ hub selected — now click counters and monitors")
                            .withStyle(ChatFormatting.GREEN), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (be instanceof ItemCounterBlockEntity || be instanceof StockMonitorBlockEntity) {
            if (!level.isClientSide()) {
                linkToHub(level, player, stack, be, dim, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (be instanceof StorageSensorBlockEntity sensor) {
            if (!level.isClientSide()) {
                CompoundTag nbt = read(stack);
                if (!nbt.contains("ContDim")) {
                    player.displayClientMessage(Component.literal("Wand ▸ clip a container first (right-click it)")
                            .withStyle(ChatFormatting.RED), true);
                } else {
                    sensor.bind(nbt.getString("ContDim"), BlockPos.of(nbt.getLong("ContPos")));
                    player.displayClientMessage(Component.literal("Storage Sensor ▸ bound to " + sensor.describeBinding())
                            .withStyle(ChatFormatting.GREEN), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (HopperBlockEntity.getContainerAt(level, pos) != null) {
            if (!level.isClientSide()) {
                write(stack, "ContDim", dim, "ContPos", pos.asLong());
                player.displayClientMessage(Component.literal("Wand ▸ container clipped — now click a Storage Sensor")
                        .withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    private static void linkToHub(Level level, Player player, ItemStack stack, BlockEntity node,
                                  String nodeDim, BlockPos nodePos) {
        CompoundTag nbt = read(stack);
        if (!nbt.contains("HubDim")) {
            player.displayClientMessage(Component.literal("Wand ▸ select a Command Hub first (right-click it)")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        MinecraftServer server = level.getServer();
        ResourceLocation hubDim = ResourceLocation.tryParse(nbt.getString("HubDim"));
        BlockPos hubPos = BlockPos.of(nbt.getLong("HubPos"));
        ServerLevel hubWorld = server == null || hubDim == null
                ? null : server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, hubDim));
        if (hubWorld == null || !hubWorld.isLoaded(hubPos)
                || !(hubWorld.getBlockEntity(hubPos) instanceof CommandHubBlockEntity hub)) {
            player.displayClientMessage(Component.literal("Wand ▸ that hub is gone or unloaded").withStyle(ChatFormatting.RED), true);
            return;
        }
        int type = node instanceof ItemCounterBlockEntity
                ? CommandHubBlockEntity.TYPE_COUNTER : CommandHubBlockEntity.TYPE_MONITOR;
        if (hub.addNode(type, nodeDim, nodePos)) {
            player.displayClientMessage(Component.literal("Wand ▸ linked to hub (" + hub.nodeCount() + "/"
                    + CommandHubBlockEntity.MAX_NODES + ")").withStyle(ChatFormatting.GREEN), true);
        } else {
            player.displayClientMessage(Component.literal("Wand ▸ already linked, or the board is full")
                    .withStyle(ChatFormatting.GOLD), true);
        }
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
        Tips.append(tooltip, "tip.gadgets.monitor_wand.1", "tip.gadgets.monitor_wand.2", "tip.gadgets.monitor_wand.3");
    }
}
