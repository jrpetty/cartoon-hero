package com.grapplinghook;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Visual helper for the grappling hook: draws the rope as a line of particles
 * from the player to the grapple point at the moment of firing.
 */
public final class GrappleManager {

    private GrappleManager() {}

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
