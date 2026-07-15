package com.jrpetty.mobtrumps;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Projection storage for the Holo Projector. Reuses everything from
 * {@link CardDisplayBlockEntity} (mob/foil/owner, sync, the picker, the
 * anti-theft rules) via subclassing, so only the block entity type and the
 * taller render box differ — the projector shows a floating 3D mob above it.
 */
public class HoloProjectorBlockEntity extends CardDisplayBlockEntity {

    public HoloProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.HOLO_PROJECTOR_BE.get(), pos, state);
    }

    /** Expand the render box upward so the floating hologram never culls. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0.0, 2.5, 0.0);
    }
}
