package com.gadgets;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The one config packet behind every gadget screen: "set {key} to {value} on
 * the gadget at {pos}". The server validates reach and block type before
 * applying, so the packet can never touch anything but a gadget in front of
 * the player.
 */
public record GadgetConfigPayload(BlockPos pos, String key, int value) implements CustomPayload {
    public static final CustomPayload.Id<GadgetConfigPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Gadgets.MOD_ID, "config"));
    public static final PacketCodec<RegistryByteBuf, GadgetConfigPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, GadgetConfigPayload::pos,
            PacketCodecs.STRING, GadgetConfigPayload::key,
            PacketCodecs.VAR_INT, GadgetConfigPayload::value,
            GadgetConfigPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Server-side application, called on the server thread. */
    public static void apply(ServerPlayerEntity player, GadgetConfigPayload p) {
        World world = player.getWorld();
        BlockPos pos = p.pos();
        if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
            return; // out of reach — ignore
        }
        BlockEntity be = world.getBlockEntity(pos);
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
            default -> {
            }
        }
    }
}
