package com.jrpetty.mobtrumps;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Projection storage for the Holo Projector. Reuses everything from
 * {@link CardDisplayBlockEntity} (mob/foil/owner, sync, the picker, the
 * anti-theft rules) via subclassing, so only the block entity type differs —
 * the projector shows a floating 3D mob above it.
 */
public class HoloProjectorBlockEntity extends CardDisplayBlockEntity {

    public HoloProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.HOLO_PROJECTOR_BE.get(), pos, state);
    }
}
