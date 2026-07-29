package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
            case "counter_reset" -> {
                if (be instanceof ItemCounterBlockEntity counter) {
                    counter.resetStats();
                }
            }
            case "monitor_threshold" -> {
                if (be instanceof StockMonitorBlockEntity monitor) {
                    monitor.setThreshold(p.value());
                }
            }
            case "set_name" -> {
                // One label field, shared by both display gadgets.
                String name = p.text().length() > 24 ? p.text().substring(0, 24) : p.text();
                if (be instanceof ItemCounterBlockEntity counter) {
                    counter.setCustomName(name);
                } else if (be instanceof StockMonitorBlockEntity monitor) {
                    monitor.setCustomName(name);
                }
            }
            case "hub_clear" -> {
                if (be instanceof CommandHubBlockEntity hub) {
                    hub.clearNodes();
                }
            }
            case "hub_unlink" -> {
                // Index is validated against the live board, so a click on a
                // stale row is dropped rather than unlinking the wrong gadget.
                if (be instanceof CommandHubBlockEntity hub) {
                    hub.removeNodeAt(p.value());
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
