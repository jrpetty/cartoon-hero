package com.grapplinghook;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Tracks active block grapples and reels each player toward their anchor a bit
 * every tick, preserving momentum so you swing. Ends on arrival, timeout, sneak
 * (detach, keep momentum), death, or disconnect.
 */
public final class GrappleManager {

    private static final class Reel {
        final Vec3d anchor;
        int ticks;

        Reel(Vec3d anchor, int ticks) {
            this.anchor = anchor;
            this.ticks = ticks;
        }
    }

    private static final Map<UUID, Reel> REELS = new HashMap<>();

    private GrappleManager() {}

    /** Begin reeling a player toward a block anchor. */
    public static void start(ServerPlayerEntity player, Vec3d anchor) {
        REELS.put(player.getUuid(), new Reel(anchor, GrappleConfig.reelDurationTicks));
    }

    public static void tick(MinecraftServer server) {
        if (REELS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Reel>> it = REELS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Reel> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            Reel reel = entry.getValue();

            if (player == null || !player.isAlive() || player.isRemoved() || player.isSneaking()) {
                it.remove();
                continue; // detach (sneak/death/leave) — momentum is preserved
            }

            Vec3d toAnchor = reel.anchor.subtract(player.getPos());
            double distance = toAnchor.length();
            if (distance < GrappleConfig.arriveDistance || reel.ticks-- <= 0) {
                it.remove();
                continue;
            }

            Vec3d dir = toAnchor.normalize();
            Vec3d velocity = player.getVelocity()
                    .add(dir.multiply(GrappleConfig.reelAcceleration))
                    .add(0.0, 0.05, 0.0); // gently fight gravity so the swing holds
            double speed = velocity.length();
            if (speed > GrappleConfig.reelMaxSpeed) {
                velocity = velocity.multiply(GrappleConfig.reelMaxSpeed / speed);
            }

            player.setVelocity(velocity);
            player.velocityModified = true;
            player.fallDistance = 0.0F;

            if (player.getWorld() instanceof ServerWorld world) {
                drawRope(world, player.getEyePos(), reel.anchor);
            }
        }
    }

    /** A line of particles from the player to the anchor — the visible tether. */
    public static void drawRope(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        int points = (int) MathHelper.clamp(delta.length() * 2.0, 4, 48);
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            Vec3d p = from.add(delta.multiply(t));
            world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
