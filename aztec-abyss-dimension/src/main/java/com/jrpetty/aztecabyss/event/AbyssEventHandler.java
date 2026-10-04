package com.jrpetty.aztecabyss.event;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.registry.ModAttachments;
import com.jrpetty.aztecabyss.round.RoundManager;
import com.jrpetty.aztecabyss.round.RunState;
import com.jrpetty.aztecabyss.worldgen.ArenaGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Ties the combat/lifecycle events into the shared {@link RoundManager} session:
 * driving the per-tick round loop, the dig restriction, crediting wave kills,
 * turning lethal player damage into the downed state, keeping downed players
 * safe while they bleed out, and settling logout/login mid-run.
 */
public final class AbyssEventHandler {

    private boolean inAbyss(net.minecraft.world.level.Level level) {
        return level instanceof ServerLevel sl && sl.dimension().equals(AztecAbyssConstants.ABYSS_LEVEL_KEY);
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && inAbyss(level)) {
            ArenaGenerator.generateIfNeeded(level);
            // Re-read the maps folder per world, not once per process. The list is
            // static, so a singleplayer client hopping between two worlds would
            // otherwise show the first world's published maps on the second one's
            // portal - and send anyone who picked one to an empty slot.
            if (level.getServer() != null) {
                com.jrpetty.aztecabyss.engine.PublishedMaps.load(level.getServer());
            }
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inAbyss(level)) {
            return;
        }
        RoundManager.tickSession(level);
        ArenaGenerator.ambientTick(level);
    }

    /**
     * Nothing gets into the Abyss during a run except through a gate.
     *
     * <p>The horde is supposed to arrive from the four gates and walk in, which is
     * what makes holding a position mean anything. Anything that materialises
     * inside the temple instead - next to you, in a room you cleared, behind the
     * line you were watching - breaks that contract, and the player has no way to
     * tell it apart from a gate spawn that pathed round behind them.
     *
     * <p>Natural spawning is already off: both biomes ship empty spawner lists. The
     * leak was zombie reinforcements. Every wave mob is a zombie by default, and on
     * Hard difficulty a damaged zombie summons help beside itself - so fighting
     * inside the pyramid bred more zombies inside the pyramid. Those reinforcements
     * carried none of our tags, so they never counted toward the round, never died
     * to the end-of-round sweep, and survived into the next round.
     *
     * <p>{@link #onWaveMobSpawn} stops them being summoned. This is the belt to that
     * brace, and it catches anything else that might try - a mod adding spawners, a
     * structure override, a future mechanic nobody has written yet. Scoped to a live
     * run so building and testing outside one is untouched.
     */
    @SubscribeEvent
    public void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!inAbyss(event.getLevel()) || !RoundManager.isRunLocked()) {
            return;
        }
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Monster monster)) {
            return;
        }
        var data = monster.getPersistentData();
        if (data.getBoolean("aztecabyss_wave_mob")
                || data.getBoolean("aztecabyss_boss")
                || data.getBoolean("aztecabyss_engine_mob")) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inAbyss(level)) {
            return;
        }
        if (!isMineable(event.getState().getBlock())) {
            event.setCanceled(true);
        }
    }

    private boolean isMineable(net.minecraft.world.level.block.Block block) {
        return block == Blocks.DIAMOND_ORE
                || block == Blocks.IRON_ORE
                || block == Blocks.GOLD_ORE
                || block == Blocks.COAL_ORE
                || block == Blocks.CHEST;
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dying = event.getEntity();
        if (!(dying.level() instanceof ServerLevel level) || !inAbyss(level)) {
            return;
        }

        // The Warden boss died: end the boss round (its own path, not the adds counter).
        if (dying instanceof Mob boss && boss.getPersistentData().getBoolean("aztecabyss_boss")) {
            ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
            RoundManager.onBossKilled(level, killer, boss.blockPosition());
            return;
        }

        // Any wave mob died (zombie, skeleton, creeper, ...): credit the killing participant.
        if (dying instanceof Mob mob && mob.getPersistentData().getBoolean("aztecabyss_wave_mob")) {
            // Specialists sit on a colour team for their outline glow - take them
            // back off it so the scoreboard doesn't fill with dead UUIDs, and give
            // them a death cry that matches their spawn cry so a kill reads by ear.
            int role = mob.getPersistentData().getInt("aztecabyss_role");
            if (role != 0) {
                level.getScoreboard().removePlayerFromTeam(mob.getStringUUID());
                if (role == 1) { // Breaker - the keen cuts off short
                    level.playSound(null, mob.blockPosition(),
                            net.minecraft.sounds.SoundEvents.RAVAGER_DEATH,
                            net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.8F);
                } else { // Sapper - a heavy collapse
                    level.playSound(null, mob.blockPosition(),
                            net.minecraft.sounds.SoundEvents.RAVAGER_DEATH,
                            net.minecraft.sounds.SoundSource.HOSTILE, 1.3F, 0.45F);
                }
            }
            ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
            RoundManager.onWaveZombieKilled(level, killer);
            return;
        }

        // A participant would die: down them instead of the normal death flow.
        if (dying instanceof ServerPlayer player && RoundManager.game().isParticipant(player.getUUID())) {
            event.setCanceled(true);
            RoundManager.downPlayer(level, player);
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        // Creepers are part of the roster - let them hurt players but never let
        // an explosion carve up the arena (walls, floor, temple).
        if (event.getLevel() instanceof ServerLevel level && inAbyss(level)) {
            event.getAffectedBlocks().clear();
        }
    }

    /**
     * Approximates a headshot: a projectile that strikes the upper quarter of a
     * wave mob's hitbox. Minecraft has no native hit-location model, so this is
     * a height check against the arrow's impact position - good enough to make
     * precise archery feel rewarded and to fill the end-of-run scoreboard.
     */
    @SubscribeEvent
    public void onHeadshotCheck(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !(mob.level() instanceof ServerLevel level) || !inAbyss(level)) {
            return;
        }
        if (!mob.getPersistentData().getBoolean("aztecabyss_wave_mob")) {
            return;
        }
        net.minecraft.world.entity.Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof net.minecraft.world.entity.projectile.Projectile projectile)) {
            return;
        }
        if (!(projectile.getOwner() instanceof ServerPlayer shooter)
                || !RoundManager.game().isParticipant(shooter.getUUID())) {
            return;
        }
        // Hitting a mob pulls it off the objective and onto you.
        RoundManager.provokeMob(mob, shooter, level.getGameTime());

        double headLine = mob.getY() + mob.getBbHeight() * 0.75;
        if (projectile.getY() >= headLine) {
            RunState rs = shooter.getData(ModAttachments.RUN_STATE);
            rs.addHeadshot();
            shooter.setData(ModAttachments.RUN_STATE, rs);
            level.playSound(null, shooter.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.8F);
        }
    }

    /** Any hit from a hunter - melee or otherwise - drags a mob's attention onto them. */
    @SubscribeEvent
    public void onMobProvoked(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !(mob.level() instanceof ServerLevel level) || !inAbyss(level)) {
            return;
        }
        if (!mob.getPersistentData().getBoolean("aztecabyss_wave_mob")) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && RoundManager.game().isParticipant(attacker.getUUID())) {
            RoundManager.provokeMob(mob, attacker, level.getGameTime());
        }
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && inAbyss(player.level())
                && player.getData(ModAttachments.RUN_STATE).isDowned()) {
            // Downed players are invulnerable while they bleed out / wait for a revive.
            event.setCanceled(true);
        }
    }

    /** Right-clicking the Heart with a diamond mends it - at the cost of the diamond. */
    @SubscribeEvent
    public void onRepairHeart(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inAbyss(level)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        net.minecraft.core.BlockPos heart = RoundManager.game().getMap().objective();
        if (heart == null || !event.getPos().equals(heart)) {
            return;
        }
        net.minecraft.world.item.ItemStack held = event.getItemStack();
        if (!held.is(net.minecraft.world.item.Items.DIAMOND)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§bThe Heart hungers for §fdiamond§b."), true);
            event.setCanceled(true);
            return;
        }
        if (RoundManager.repairObjective(level, player) && !player.getAbilities().instabuild) {
            held.shrink(1);
        }
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public void onUseAbility(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inAbyss(level)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = event.getItemStack();
        if (!com.jrpetty.aztecabyss.round.AbyssAbility.is(stack)
                || !RoundManager.game().isParticipant(player.getUUID())) {
            return;
        }
        com.jrpetty.aztecabyss.round.AbyssAbility.trigger(level, player);
        stack.shrink(1);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§d✦ Abyssal Nova unleashed!"), true);
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public void onLeaveAbyss(PlayerEvent.PlayerChangedDimensionEvent event) {
        // The Abyssal Nova never leaves the dimension - strip it the moment a player exits.
        if (event.getEntity() instanceof ServerPlayer player
                && event.getFrom().equals(AztecAbyssConstants.ABYSS_LEVEL_KEY)) {
            com.jrpetty.aztecabyss.round.AbyssAbility.strip(player);
            // And nothing of the Abyss stays on their screen: the round bar,
            // the Heart bar, the engine's bar, the in-run HUD state. Every
            // planned exit already does this; this is the route for exits
            // nobody planned.
            RoundManager.onLeftDimension(player);
            com.jrpetty.aztecabyss.engine.EngineArena.onLeftDimension(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RoundManager.resolveOwedRewardOnLogin(player);
            // Anything the retired Outpost was still holding goes back to its owner.
            com.jrpetty.aztecabyss.round.LegacyVault.returnHeldGear(player);
            // Restore the on-screen re-entry countdown if a lockout is still running.
            com.jrpetty.aztecabyss.network.ModNetworking.sendCooldown(
                    player, player.getData(ModAttachments.RUN_STATE).getCooldownUntil());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && RoundManager.game().isParticipant(player.getUUID())) {
            RoundManager.onParticipantLoggedOut(player);
        }
    }
}
