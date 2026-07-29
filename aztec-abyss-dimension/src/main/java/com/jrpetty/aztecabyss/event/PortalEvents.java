package com.jrpetty.aztecabyss.event;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.block.AbyssPortalShape;
import com.jrpetty.aztecabyss.config.AbyssConfig;
import com.jrpetty.aztecabyss.dimension.AbyssTeleporter;
import com.jrpetty.aztecabyss.registry.ModAttachments;
import com.jrpetty.aztecabyss.registry.ModBlocks;
import com.jrpetty.aztecabyss.round.RoundManager;
import com.jrpetty.aztecabyss.round.RunState;
import com.jrpetty.aztecabyss.worldgen.ArenaGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Optional;

/**
 * Two responsibilities:
 *   1. Igniting the frame (flint &amp; steel on abyssal obsidian instead of vanilla obsidian).
 *   2. Watching every player each tick for "standing in the portal long enough" to
 *      trigger travel, since the Abyss doesn't use vanilla's nether/end portal plumbing.
 */
public final class PortalEvents {

    private static final int TELEPORT_TICKS = 24; // ~1.2s standing in the portal

    @SubscribeEvent
    public void onRightClickFrame(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        BlockPos clicked = event.getPos();
        BlockState clickedState = level.getBlockState(clicked);
        ItemStack stack = event.getItemStack();

        if (!AbyssPortalShape.isFrameBlock(clickedState) || !(stack.getItem() instanceof FlintAndSteelItem)) {
            return;
        }

        Direction face = event.getFace();
        BlockPos interior = clicked.relative(face);
        BlockState interiorState = level.getBlockState(interior);
        // The opening must be clickable through - air, or fire from a prior attempt.
        if (!interiorState.isAir()
                && !interiorState.is(net.minecraft.world.level.block.Blocks.FIRE)
                && !interiorState.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)) {
            return;
        }

        Optional<AbyssPortalShape> shape = AbyssPortalShape.findEmptyPortalShape(level, interior, Direction.Axis.X);
        if (shape.isEmpty()) {
            return;
        }

        shape.get().createPortalBlocks();
        level.playSound(null, clicked, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 0.9F);
        Player player = event.getEntity();
        if (!player.getAbilities().instabuild && stack.isDamageableItem()) {
            // Damage the item by hand rather than relying on hurtAndBreak()'s exact overload,
            // which has shifted across Minecraft versions.
            int newDamage = stack.getDamageValue() + 1;
            if (newDamage >= stack.getMaxDamage()) {
                stack.shrink(1);
            } else {
                stack.setDamageValue(newDamage);
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        boolean inPortal = player.level().getBlockState(pos).is(ModBlocks.ABYSS_PORTAL.get());

        CompoundTag data = player.getPersistentData();

        // Grace window right after a dimension change: you always spawn standing
        // in/near a portal, and without this you'd be yanked straight back.
        long grace = data.getLong("aztecabyss_portal_grace");
        if (grace > 0L) {
            if (player.level().getGameTime() < grace) {
                data.putInt("aztecabyss_portal_ticks", 0);
                return;
            }
            data.putLong("aztecabyss_portal_grace", 0L);
        }

        int ticks = data.getInt("aztecabyss_portal_ticks");

        if (!inPortal) {
            if (ticks != 0) {
                data.putInt("aztecabyss_portal_ticks", 0);
            }
            return;
        }

        ticks++;
        data.putInt("aztecabyss_portal_ticks", ticks);
        if (ticks >= TELEPORT_TICKS) {
            data.putInt("aztecabyss_portal_ticks", 0);
            handleTravel(player);
        }
    }

    private void handleTravel(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ResourceKey<Level> currentDim = player.level().dimension();

        if (currentDim.equals(AztecAbyssConstants.ABYSS_LEVEL_KEY)) {
            // There is no walking away from a live run - the only ways out are
            // death, extraction, or clearing the final round.
            if (RoundManager.game().isParticipant(player.getUUID())) {
                player.displayClientMessage(Component.literal(
                        "§4The way back is sealed. §7Survive, extract between rounds — or die trying."), true);
            }
            return;
        }

        // A run is already under way: the Abyss is closed to newcomers.
        if (RoundManager.isRunLocked()) {
            player.displayClientMessage(Component.literal(
                    "§5The Abyss is sealed — a hunt is already under way. §7Wait for it to end."), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.3F, 0.2F);
            return;
        }

        RunState state = player.getData(ModAttachments.RUN_STATE);
        long now = System.currentTimeMillis();
        if (state.isOnCooldown(now)) {
            sendCooldownMessage(player, state);
            return;
        }

        // Entry confirmation: the first time a player stands in the portal we warn
        // them (a run is a real commitment - dying costs a 20h cooldown) and make
        // them step out and back in to actually commit.
        if (AbyssConfig.REQUIRE_ENTRY_CONFIRMATION.get()) {
            CompoundTag data = player.getPersistentData();
            long confirmDeadline = data.getLong("aztecabyss_entry_confirm_until");
            if (now > confirmDeadline) {
                data.putLong("aztecabyss_entry_confirm_until", now + 15_000L);
                player.displayClientMessage(Component.literal(
                        "§6⚠ Enter the Aztec Abyss? §7Dying here locks you out for "
                                + AbyssConfig.REENTRY_COOLDOWN_HOURS.get()
                                + "h. §eStep out, then back into the portal to confirm."), false);
                player.level().playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.5F, 0.6F);
                return;
            }
            data.putLong("aztecabyss_entry_confirm_until", 0L);
        }

        ServerLevel abyss = server.getLevel(AztecAbyssConstants.ABYSS_LEVEL_KEY);
        if (abyss == null) {
            player.displayClientMessage(Component.literal("§cThe Abyss dimension failed to load."), true);
            return;
        }

        ArenaGenerator.generateIfNeeded(abyss);
        state.setHome(player.blockPosition(), currentDim);
        player.setData(ModAttachments.RUN_STATE, state);

        player.changeDimension(AbyssTeleporter.toAbyssArrival(abyss));
        // Don't let the arrival portal immediately fling them home again.
        player.getPersistentData().putLong("aztecabyss_portal_grace", abyss.getGameTime() + 100L);
        RoundManager.onPlayerEnter(player);
    }

    private void sendCooldownMessage(ServerPlayer player, RunState state) {
        long remainingMs = state.getCooldownUntil() - System.currentTimeMillis();
        long hours = remainingMs / (1000 * 60 * 60);
        long minutes = (remainingMs / (1000 * 60)) % 60;
        player.displayClientMessage(
                Component.literal("§5The Abyss is sealed to you. It reopens in §d" + hours + "h " + minutes + "m§5."),
                true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.3F, 0.2F);
    }
}
