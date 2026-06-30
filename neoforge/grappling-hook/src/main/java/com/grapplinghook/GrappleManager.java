package com.grapplinghook;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Tracks active block grapples and reels each player toward their anchor a bit
 * every tick, preserving momentum so you swing. Ends on arrival, timeout, sneak
 * (detach, keep momentum), death, or disconnect.
 */
public final class GrappleManager {

    private static final class Reel {
        final Vec3 anchor;
        int ticks;

        Reel(Vec3 anchor, int ticks) {
            this.anchor = anchor;
            this.ticks = ticks;
        }
    }

    private static final Map<UUID, Reel> REELS = new HashMap<>();

    private GrappleManager() {}

    public static void start(ServerPlayer player, Vec3 anchor) {
        REELS.put(player.getUUID(), new Reel(anchor, GrappleConfig.reelDurationTicks));
    }

    public static void tick(MinecraftServer server) {
        if (REELS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Reel>> it = REELS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Reel> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Reel reel = entry.getValue();

            if (player == null || !player.isAlive() || player.isRemoved() || player.isShiftKeyDown()) {
                it.remove();
                continue;
            }

            Vec3 toAnchor = reel.anchor.subtract(player.position());
            double distance = toAnchor.length();
            if (distance < GrappleConfig.arriveDistance || reel.ticks-- <= 0) {
                it.remove();
                continue;
            }

            Vec3 dir = toAnchor.normalize();
            Vec3 velocity = player.getDeltaMovement()
                    .add(dir.scale(GrappleConfig.reelAcceleration))
                    .add(0.0, 0.05, 0.0);
            double speed = velocity.length();
            if (speed > GrappleConfig.reelMaxSpeed) {
                velocity = velocity.scale(GrappleConfig.reelMaxSpeed / speed);
            }

            player.setDeltaMovement(velocity);
            player.hurtMarked = true;
            player.hasImpulse = true;
            player.fallDistance = 0.0F;

            if (player.level() instanceof ServerLevel level) {
                drawRope(level, player.getEyePosition(1.0F), reel.anchor);
            }
        }
    }

    public static void drawRope(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int points = (int) Mth.clamp(delta.length() * 2.0, 4, 48);
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            Vec3 p = from.add(delta.scale(t));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
