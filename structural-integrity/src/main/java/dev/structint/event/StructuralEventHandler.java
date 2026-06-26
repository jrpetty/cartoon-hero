package dev.structint.event;

import dev.structint.StructuralIntegrityMod;
import dev.structint.world.ModAttachments;
import dev.structint.world.StructuralManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Bridges vanilla/NeoForge game events to the {@link StructuralManager}. All work is
 * server-side only; the client never runs the structural rules.
 *
 * <p>Hooks used:
 * <ul>
 *   <li>{@link BlockEvent.EntityPlaceEvent} — a block was placed → record + schedule a check;</li>
 *   <li>{@link BlockEvent.BreakEvent} — a player is breaking a block → schedule a check of what
 *       it was holding up;</li>
 *   <li>{@link LevelTickEvent.Post} — drain the per-level work queues under budget;</li>
 *   <li>{@link LevelEvent.Unload} — drop per-level state.</li>
 * </ul>
 */
@EventBusSubscriber(modid = StructuralIntegrityMod.MODID)
public final class StructuralEventHandler {

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            StructuralManager.onBlockPlaced(level, event.getPos(), event.getPlacedBlock());
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            // The block is still present here; the manager defers the actual re-check to the
            // next tick (after vanilla has removed it), so we just record the position.
            StructuralManager.onBlockRemoved(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            StructuralManager.tick(level);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Explosions remove blocks without firing BreakEvent. Re-check every affected position so
        // a support blown out by TNT/a creeper collapses what it was holding, just like mining it.
        for (BlockPos pos : event.getAffectedBlocks()) {
            StructuralManager.onBlockRemoved(level, pos);
        }
    }

    @SubscribeEvent
    public static void onFallingBlockLanded(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof FallingBlockEntity falling)) {
            return;
        }
        if (!falling.getData(ModAttachments.FROM_COLLAPSE.get())) {
            return; // not one of ours (a normal sand/gravel fall)
        }
        // The entity removes itself in the same tick it places its block, so the block is present
        // now. Re-mark it as player-managed at its resting position.
        StructuralManager.onCollapsedBlockLanded(level, falling.blockPosition(), falling.getBlockState());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            StructuralManager.forget(level);
        }
    }

    private StructuralEventHandler() {
    }
}
