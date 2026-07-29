package com.gadgets;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Visual helper for the grappling hook: draws the rope as a line of particles
 * from the player to the grapple point at the moment of firing.
 */
public final class GrappleManager {

    private GrappleManager() {}

    /** A line of particles from the player to the anchor — the visible tether. */
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
