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

    /** Roughly how many particles make up a block of rope. */
    private static final double DENSITY = 2.0;
    /** Rope is drawn in this many chunks, however long it is. */
    private static final int SEGMENTS = 6;

    /**
     * A line of particles from the player to the anchor — the visible tether.
     *
     * <p>The rope is sent as a handful of bursts rather than one packet per
     * particle. Each burst asks the client to scatter its share of the particles
     * along one stretch of the line, so the rope looks the same as before while a
     * long shot costs six packets to every nearby player instead of fifty.
     */
    public static void drawRope(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int points = (int) Mth.clamp(delta.length() * DENSITY, 4, 48);
        Vec3 step = delta.scale(1.0 / SEGMENTS);
        // Half a segment in each axis: the spread the client scatters a burst over.
        double sx = Math.abs(step.x) / 2.0;
        double sy = Math.abs(step.y) / 2.0;
        double sz = Math.abs(step.z) / 2.0;
        for (int i = 0; i < SEGMENTS; i++) {
            Vec3 mid = from.add(step.scale(i + 0.5));
            int share = points / SEGMENTS + (i < points % SEGMENTS ? 1 : 0);
            if (share > 0) {
                level.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, share, sx, sy, sz, 0.0);
            }
        }
    }
}
