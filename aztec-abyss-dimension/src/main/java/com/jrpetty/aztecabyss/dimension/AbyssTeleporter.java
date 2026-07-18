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

    public static DimensionTransition toAbyssArrival(ServerLevel abyss) {
        BlockPos p = AztecAbyssConstants.ABYSS_ARRIVAL_POS;
        float yaw = AztecAbyssConstants.ABYSS_ARRIVAL_FACING.toYRot();
        return new DimensionTransition(
                abyss,
                new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5),
                Vec3.ZERO,
                yaw,
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
