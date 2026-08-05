package com.gadgets;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The one config packet behind every gadget screen: "set {key} to {value} on
 * the gadget at {pos}". The server validates reach and block type before
 * applying, so the packet can never touch anything but a gadget in front of
 * the player.
 */
public record GadgetConfigPayload(BlockPos pos, String key, int value, String text) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GadgetConfigPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Gadgets.MODID, "config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GadgetConfigPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, GadgetConfigPayload::pos,
            ByteBufCodecs.STRING_UTF8, GadgetConfigPayload::key,
            ByteBufCodecs.VAR_INT, GadgetConfigPayload::value,
            ByteBufCodecs.STRING_UTF8, GadgetConfigPayload::text,
            GadgetConfigPayload::new);

    public GadgetConfigPayload(BlockPos pos, String key, int value) {
        this(pos, key, value, "");
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server-side application, called on the server thread. */
    public static void apply(ServerPlayer player, GadgetConfigPayload p) {
        Level level = player.level();
        BlockPos pos = p.pos();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
            return; // out of reach — ignore
        }
        BlockEntity be = level.getBlockEntity(pos);
        switch (p.key()) {
            case "sensor_radius" -> {
                if (be instanceof PlayerSensorBlockEntity sensor) {
                    sensor.setRadius(p.value());
                }
            }
            case "sensor_mode" -> {
                if (be instanceof PlayerSensorBlockEntity sensor) {
                    sensor.setModeIndex(p.value());
                }
            }
            case "counter_threshold" -> {
                if (be instanceof ItemCounterBlockEntity counter) {
                    counter.setThreshold(p.value());
                }
            }
            case "counter_mode" -> {
                if (be instanceof ItemCounterBlockEntity counter) {
                    counter.setDisplayMode(p.value());
                }
            }
            case "counter_direction" -> {
                if (be instanceof ItemCounterBlockEntity counter) {
                    counter.setCountMode(p.value());
                }
            }
            case "counter_unfilter" -> {
                if (be instanceof ItemCounterBlockEntity counter) {
                    ResourceLocation id = ResourceLocation.tryParse(p.text());
                    if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                        counter.removeFilter(BuiltInRegistries.ITEM.get(id));
                    }
                }
            }
            case "counter_unfilter_all" -> {
                if (be instanceof ItemCounterBlockEntity counter) {
                    counter.clearFilter();
                }
            }
            case "counter_reset" -> {
                if (be instanceof ItemCounterBlockEntity counter) {
                    counter.resetStats();
                }
            }
            case "gauge_threshold" -> {
                if (be instanceof HubGauge gauge) {
                    gauge.setThreshold(p.value());
                }
            }
            case "monitor_threshold" -> {
                if (be instanceof StockMonitorBlockEntity monitor) {
                    monitor.setThreshold(p.value());
                }
            }
            case "monitor_untrack" -> {
                if (be instanceof StockMonitorBlockEntity monitor) {
                    ResourceLocation id = ResourceLocation.tryParse(p.text());
                    if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                        monitor.untrack(BuiltInRegistries.ITEM.get(id));
                    }
                }
            }
            case "monitor_clear" -> {
                if (be instanceof StockMonitorBlockEntity monitor) {
                    monitor.clearTracked();
                }
            }
            case "set_name" -> {
                // One label field, shared by both display gadgets.
                String name = p.text().length() > 24 ? p.text().substring(0, 24) : p.text();
                if (be instanceof ItemCounterBlockEntity counter) {
                    counter.setCustomName(name);
                } else if (be instanceof StockMonitorBlockEntity monitor) {
                    monitor.setCustomName(name);
                } else if (be instanceof HubGauge gauge) {
                    gauge.setCustomName(name);
                } else if (be instanceof TransferNode node) {
                    node.setCustomName(name);
                }
            }
            case "hub_clear" -> {
                if (be instanceof CommandHubBlockEntity hub) {
                    hub.clearNodes();
                }
            }
            case "hub_log_clear" -> {
                if (be instanceof CommandHubBlockEntity hub) {
                    hub.clearEvents();
                }
            }
            case "hub_unlink" -> {
                // Addressed as dimension@packedPos, so it unlinks the gadget the
                // player actually clicked even if the board reordered in flight.
                if (be instanceof CommandHubBlockEntity hub) {
                    int at = p.text().lastIndexOf('@');
                    if (at > 0) {
                        try {
                            hub.removeNode(p.text().substring(0, at),
                                    BlockPos.of(Long.parseLong(p.text().substring(at + 1))));
                        } catch (NumberFormatException ignored) {
                            // malformed target — ignore
                        }
                    }
                }
            }
            case "transfer_upgrade" -> {
                if (be instanceof TransferNode node) {
                    upgradeTransfer(player, node);
                }
            }
            case "hubmon_pick" -> {
                if (be instanceof CommandHubMonitorBlockEntity monitor) {
                    pickMonitorSource(player, monitor, p.text());
                }
            }
            default -> {
            }
        }
    }

    /**
     * Spends the upgrade cost out of the player's inventory and moves the link
     * up a level.
     *
     * <p>Everything is counted before anything is taken, so a player who is one
     * pearl short keeps their iron. The cost is charged per end: a fast line
     * needs both its sender and its receiver paid for.
     */
    private static void upgradeTransfer(ServerPlayer player, TransferNode node) {
        if (node.getTier() >= TransferNode.MAX_TIER) {
            say(player, "That end is already at level " + TransferNode.MAX_TIER + ".", ChatFormatting.GRAY);
            return;
        }
        if (player.getAbilities().instabuild) {
            node.setTier(node.getTier() + 1);
            announce(player, node);
            return;
        }
        if (!has(player, Items.IRON_BLOCK, TransferNode.UPGRADE_IRON_BLOCKS)
                || !has(player, Items.ENDER_PEARL, TransferNode.UPGRADE_ENDER_PEARLS)
                || !has(player, Items.HOPPER, TransferNode.UPGRADE_HOPPERS)) {
            say(player, "Not enough: an upgrade takes " + TransferNode.UPGRADE_IRON_BLOCKS
                    + " blocks of iron, " + TransferNode.UPGRADE_ENDER_PEARLS + " ender pearls and a hopper.",
                    ChatFormatting.RED);
            return;
        }
        take(player, Items.IRON_BLOCK, TransferNode.UPGRADE_IRON_BLOCKS);
        take(player, Items.ENDER_PEARL, TransferNode.UPGRADE_ENDER_PEARLS);
        take(player, Items.HOPPER, TransferNode.UPGRADE_HOPPERS);
        node.setTier(node.getTier() + 1);
        announce(player, node);
    }

    private static void announce(ServerPlayer player, TransferNode node) {
        say(player, "Level " + node.getTier() + " — " + TransferNode.ratePerMinute(node.getTier())
                + " items a minute (a link runs at its slower end).", ChatFormatting.GREEN);
        player.level().playSound(null, node.getBlockPos(), SoundEvents.ANVIL_USE,
                SoundSource.BLOCKS, 0.6F, 1.4F);
    }

    private static void say(ServerPlayer player, String message, ChatFormatting colour) {
        player.displayClientMessage(Component.literal(message).withStyle(colour), true);
    }

    /** How many of an item the player is carrying, across every stack. */
    private static boolean has(ServerPlayer player, Item item, int needed) {
        int found = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                found += stack.getCount();
                if (found >= needed) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void take(ServerPlayer player, Item item, int count) {
        int left = count;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    /**
     * Applies a monitor's chosen line, encoded as {@code dimension@packedPos}.
     * An empty selection blanks the screen. The monitor itself checks the line
     * is really on its hub's board before accepting it.
     */
    private static void pickMonitorSource(ServerPlayer player, CommandHubMonitorBlockEntity monitor, String text) {
        if (text.isEmpty()) {
            monitor.clearChosenSource();
            return;
        }
        int at = text.lastIndexOf('@');
        if (at <= 0 || player.level().getServer() == null) {
            return;
        }
        try {
            monitor.chooseSource(player.level().getServer(), text.substring(0, at),
                    Long.parseLong(text.substring(at + 1)));
        } catch (NumberFormatException ignored) {
            // malformed selection — ignore
        }
    }
}
