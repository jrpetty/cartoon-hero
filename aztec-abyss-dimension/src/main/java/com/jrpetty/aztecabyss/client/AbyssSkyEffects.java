package com.jrpetty.aztecabyss.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * The Abyss sky: a true void with no sun or moon. It borrows the End's
 * starfield render (a dark, speckled void) but washes the atmosphere in a deep,
 * constant blood-red haze so it reads as a bleeding void rather than the End's
 * cold teal. No clouds, no ground horizon.
 *
 * Registered client-side against the dimension_type's {@code effects} id.
 */
public class AbyssSkyEffects extends DimensionSpecialEffects {

    public AbyssSkyEffects() {
        // cloudLevel=NaN (no clouds), hasGround=false (full void sky),
        // SkyType.END (starfield backdrop, no sun/moon).
        super(Float.NaN, false, DimensionSpecialEffects.SkyType.END, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        // Deep blood red, independent of brightness, for an oppressive constant haze.
        return new Vec3(0.10, 0.01, 0.02);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return true;
    }
}
