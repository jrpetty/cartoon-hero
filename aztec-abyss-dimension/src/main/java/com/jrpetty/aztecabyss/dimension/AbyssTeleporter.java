package com.jrpetty.aztecabyss.dimension;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Builds {@link DimensionTransition}s that drop the player at a fixed,
 * precomputed position with no vanilla portal search - the Abyss (and every
 * "home" return point) is always a known, deterministic location, so there's
 * nothing to search for.
 *
 * 1.21.1 replaced NeoForge's old {@code ITeleporter} / {@code changeDimension(level, teleporter)}
 * path with vanilla's {@code Entity#changeDimension(DimensionTransition)}; this
 * class produces the transition object for that call.
 */
public final class AbyssTeleporter {

    private AbyssTeleporter() {
    }

    /** Drops the player at the arrival point of whichever arena they picked. */
    public static DimensionTransition toAbyssArrival(ServerLevel abyss, com.jrpetty.aztecabyss.worldgen.ArenaMap map) {
        BlockPos p = map.arrival();
        // Temple arrivals look north at the pyramid; on the bridge you face
        // north too, over the fort and down the span at whatever is coming.
        net.minecraft.core.Direction facing = map == com.jrpetty.aztecabyss.worldgen.ArenaMap.BRIDGE
                ? net.minecraft.core.Direction.NORTH
                : AztecAbyssConstants.ABYSS_ARRIVAL_FACING;
        // One step out of the frame, towards where they face, and centred on
        // its two-wide opening. Arriving inside the frame put its diamond side
        // a hand's width from the left eye: a cyan wall down the edge of the
        // screen for the opening seconds of every run.
        Vec3 at = new Vec3(p.getX() + 1.0 + facing.getStepX(), p.getY(), p.getZ() + 0.5 + facing.getStepZ());
        return new DimensionTransition(
                abyss,
                at,
                Vec3.ZERO,
                facing.toYRot(),
                0.0F,
                DimensionTransition.PLAY_PORTAL_SOUND);
    }

    public static DimensionTransition toFixedHome(ServerLevel home, BlockPos pos) {
        return new DimensionTransition(
                home,
                new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                Vec3.ZERO,
                0.0F,
                0.0F,
                DimensionTransition.PLAY_PORTAL_SOUND);
    }
}
