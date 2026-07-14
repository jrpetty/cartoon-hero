package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Cards are earned by hunting. Killing a mob always drops its Mob Card
 * alongside its normal loot; killing enough of one mob unlocks that card's
 * holographic (foil) version, which carries a fixed combat boost.
 */
public final class MobDrops {

    private MobDrops() {
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        LivingEntity dead = event.getEntity();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).getPath();
        MobCard card = MobCards.byId(id);
        if (card == null) {
            return; // not a mob we have a card for
        }

        // the base card drops 100% of the time, with the mob's normal loot
        event.getDrops().add(cardDrop(dead, MobCardItem.stackOf(card, false)));
        CollectionTracker.record(killer, id, false);

        int kills = bumpKills(killer, id);
        int threshold = card.tier().foilKillThreshold();
        boolean alreadyFoil = killer.getData(ModAttachments.COLLECTED_FOIL.get()).contains(id);

        if (!alreadyFoil && kills >= threshold) {
            // holographic unlocked — drop the foil and celebrate
            event.getDrops().add(cardDrop(dead, MobCardItem.stackOf(card, true)));
            CollectionTracker.record(killer, id, true);
            killer.sendSystemMessage(Component.literal("✦ HOLOGRAPHIC UNLOCKED: " + card.displayName()
                            + "! ✦").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            killer.sendSystemMessage(Component.literal("Its holo card is boosted: " + boostSummary(card) + ".")
                    .withStyle(ChatFormatting.GRAY));
            killer.serverLevel().playSound(null, killer.getX(), killer.getY(), killer.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.2F);
            killer.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    killer.getX(), killer.getY() + 1.0, killer.getZ(), 40, 0.5, 0.6, 0.5, 0.2);
        } else if (!alreadyFoil) {
            // quiet progress nudge on the action bar
            killer.displayClientMessage(Component.literal(card.displayName() + " holo: "
                            + kills + " / " + threshold + " kills")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    /** Increment and persist this player's kill count for a mob; returns the new total. */
    /** "+2 Attack, +1 Health, +1 Speed, +1 Size" — the real diff for this card's holo. */
    private static String boostSummary(com.jrpetty.mobtrumps.game.MobCard card) {
        var foil = card.foilVersion();
        StringBuilder out = new StringBuilder();
        // biggest gains first so the speciality leads the sentence
        var stats = new java.util.ArrayList<>(java.util.List.of(com.jrpetty.mobtrumps.game.Stat.values()));
        stats.sort((a, b) -> Integer.compare(
                foil.stat(b) - card.stat(b), foil.stat(a) - card.stat(a)));
        for (var stat : stats) {
            int gain = foil.stat(stat) - card.stat(stat);
            if (gain <= 0) continue;
            if (out.length() > 0) out.append(", ");
            out.append("+").append(gain).append(" ").append(stat.label);
        }
        return out.length() == 0 ? "already maxed out" : out.toString();
    }

    private static int bumpKills(ServerPlayer player, String id) {
        Map<String, Integer> counts = new HashMap<>(player.getData(ModAttachments.KILLS.get()));
        int next = counts.getOrDefault(id, 0) + 1;
        counts.put(id, next);
        player.setData(ModAttachments.KILLS.get(), Map.copyOf(counts));
        return next;
    }

    private static ItemEntity cardDrop(LivingEntity dead, ItemStack stack) {
        ItemEntity item = new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.3, dead.getZ(), stack);
        item.setDefaultPickUpDelay();
        return item;
    }
}
