package com.jrpetty.aztecabyss.event;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.config.AbyssConfig;
import com.jrpetty.aztecabyss.registry.ModSounds;
import com.jrpetty.aztecabyss.round.AbyssGame;
import com.jrpetty.aztecabyss.round.RoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The signature Easter-egg ritual. Hidden, undocumented in-game, and worth
 * chasing:
 *
 *   1. Light the four altar-chamber braziers in the exact order
 *      {@link AztecAbyssConstants#RITUAL_ORDER} by right-clicking each unlit
 *      brazier with flint &amp; steel. A wrong brazier resets the sequence.
 *   2. With all four lit, present an offering - a Golden Apple - on the altar
 *      pedestal (right-click the lodestone with it).
 *   3. The sealed vault beneath the altar dissolves and its grand-prize chest
 *      fills. Completing the ritual also boosts everyone's end-of-run reward.
 *
 * All progress is tracked on the shared {@link AbyssGame}, so a team can work it
 * together.
 */
public final class RitualHandler {

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!AbyssConfig.ENABLE_RITUAL.get()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(AztecAbyssConstants.ABYSS_LEVEL_KEY)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AbyssGame game = RoundManager.game();
        if (game.isRitualComplete()) {
            return;
        }
        BlockPos clicked = event.getPos();
        ItemStack held = event.getItemStack();

        // Feeding the altar.
        if (clicked.equals(AztecAbyssConstants.ALTAR_OFFERING_POS)) {
            tryOffering(level, player, game, held, event);
            return;
        }

        // Lighting a brazier.
        for (int i = 0; i < AztecAbyssConstants.BRAZIER_POSITIONS.length; i++) {
            if (clicked.equals(AztecAbyssConstants.BRAZIER_POSITIONS[i]) && held.getItem() == Items.FLINT_AND_STEEL) {
                tryLightBrazier(level, player, game, i, event);
                return;
            }
        }
    }

    private void tryLightBrazier(ServerLevel level, ServerPlayer player, AbyssGame game, int index, PlayerInteractEvent.RightClickBlock event) {
        if (game.isBrazierLit(index)) {
            return;
        }
        int step = game.getRitualSequence().size();
        boolean correct = AztecAbyssConstants.RITUAL_ORDER[step] == index;

        if (!correct) {
            // Wrong order - snuff every brazier and reset.
            for (int lit : game.getRitualSequence()) {
                level.setBlock(AztecAbyssConstants.BRAZIER_POSITIONS[lit].above(), Blocks.AIR.defaultBlockState(), 3);
            }
            game.resetRitualSequence();
            level.playSound(null, event.getPos(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.6F);
            player.displayClientMessage(Component.literal("§8The flames sputter and die. The order was wrong."), true);
            event.setCanceled(true);
            return;
        }

        game.lightBrazier(index);
        level.setBlock(AztecAbyssConstants.BRAZIER_POSITIONS[index].above(), Blocks.SOUL_FIRE.defaultBlockState(), 3);
        level.playSound(null, event.getPos(), ModSounds.RITUAL_PROGRESS.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        int lit = game.getRitualSequence().size();
        player.displayClientMessage(Component.literal("§5A brazier flares to life. §d(" + lit + "/4)"), true);
        event.setCanceled(true);
    }

    private void tryOffering(ServerLevel level, ServerPlayer player, AbyssGame game, ItemStack held, PlayerInteractEvent.RightClickBlock event) {
        boolean allLit = game.getRitualSequence().size() == AztecAbyssConstants.BRAZIER_POSITIONS.length;
        if (!allLit) {
            player.displayClientMessage(Component.literal("§8The altar is cold. Something must be lit first."), true);
            return;
        }
        if (held.getItem() != Items.GOLDEN_APPLE) {
            player.displayClientMessage(Component.literal("§8The altar hungers for gold and life..."), true);
            return;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        game.setAltarFed(true);
        completeRitual(level);
        event.setCanceled(true);
    }

    private void completeRitual(ServerLevel level) {
        RoundManager.onRitualComplete(level);

        // Dissolve the vault door.
        BlockPos door = AztecAbyssConstants.VAULT_DOOR_POS;
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                level.setBlock(door.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Fill the vault chest with a modest, randomised reward - a mystery, not a fixed hoard.
        BlockEntity be = level.getBlockEntity(AztecAbyssConstants.VAULT_CHEST_POS);
        if (be instanceof ChestBlockEntity chest) {
            int slot = 0;
            for (ItemStack s : com.jrpetty.aztecabyss.round.RitualReward.rollVault()) {
                if (slot < chest.getContainerSize()) {
                    chest.setItem(slot++, s);
                }
            }
        }
    }
}
