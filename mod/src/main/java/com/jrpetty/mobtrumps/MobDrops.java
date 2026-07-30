package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Tier;
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

        var tier = card.tier();
        int kills = bumpKills(killer, id);

        // the card is a prize, not a certainty: commons land 1 in 20 and the
        // odds climb with rarity until a legendary always drops
        if (rollForCard(killer, id, tier)) {
            event.getDrops().add(cardDrop(dead, MobCardItem.issued(killer, card, false)));
            CollectionTracker.record(killer, id, false);
            cardFanfare(killer, tier);
        }

        int prevLevel = tier.upgradeLevel(kills - 1);
        int level = tier.upgradeLevel(kills);
        boolean alreadyFoil = killer.getData(ModAttachments.COLLECTED_FOIL.get()).contains(id);

        if (level > prevLevel) {
            // a milestone was just crossed — celebrate the upgrade
            MobCard from = card.upgraded(prevLevel);
            MobCard to = card.upgraded(level);
            if (level == 1 && !alreadyFoil) {
                // first milestone: the holographic drops as a physical card
                event.getDrops().add(cardDrop(dead, MobCardItem.issued(killer, card, true)));
                CollectionTracker.record(killer, id, true);
                killer.sendSystemMessage(Component.literal("✦ HOLOGRAPHIC UNLOCKED: " + card.displayName()
                                + "! ✦").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            } else if (level >= 2) {
                // higher tiers upgrade the card you already own, in-place
                killer.sendSystemMessage(Component.literal("★ CARD UPGRADED: " + card.displayName()
                                + " reached " + holoLabel(level) + "! ★")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
            if (level >= 1) {
                killer.sendSystemMessage(Component.literal("Now boosted: " + boostSummary(from, to) + ".")
                        .withStyle(ChatFormatting.GRAY));
                celebrate(killer, level);
            }
        } else if (level < tier.maxLevel() && ClientPrefsPayload.huntCounter(killer)) {
            // quiet progress nudge on the action bar toward the next milestone,
            // unless this player switched the hunt counter off in their book
            int next = tier.nextMilestone(kills);
            String label = level == 0 ? "holo" : holoLabel(level + 1);
            killer.displayClientMessage(Component.literal(card.displayName() + " " + label + ": "
                            + kills + " / " + next + " kills")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
        // push the updated kill count so the book and scanner show it live
        CollectionTracker.sync(killer);
        // hunting drives several awards (mobs hunted, Holo III), so re-check them
        AchievementManager.refresh(killer);
    }

    /** "Holo" / "Holo II" / "Holo III" for upgrade levels 1-3. */
    private static String holoLabel(int level) {
        return switch (level) {
            case 1 -> "Holo";
            case 2 -> "Holo II";
            case 3 -> "Holo III";
            default -> "Holo +" + level;
        };
    }

    /** Sound + particle burst for a milestone, richer at higher tiers. */
    private static void celebrate(ServerPlayer killer, int level) {
        float pitch = 1.0F + level * 0.15F;
        killer.serverLevel().playSound(null, killer.getX(), killer.getY(), killer.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, pitch);
        killer.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                killer.getX(), killer.getY() + 1.0, killer.getZ(), 30 + level * 20, 0.5, 0.6, 0.5, 0.2);
        if (level >= 2) {
            killer.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    killer.getX(), killer.getY() + 1.0, killer.getZ(), level * 15, 0.5, 0.7, 0.5, 0.05);
        }
    }

    /** "+2 Attack, +1 Health, +1 Speed, +1 Size" — the real diff between two card levels. */
    private static String boostSummary(com.jrpetty.mobtrumps.game.MobCard from,
                                       com.jrpetty.mobtrumps.game.MobCard to) {
        StringBuilder out = new StringBuilder();
        // biggest gains first so the speciality leads the sentence
        var stats = new java.util.ArrayList<>(java.util.List.of(com.jrpetty.mobtrumps.game.Stat.values()));
        stats.sort((a, b) -> Integer.compare(
                to.stat(b) - from.stat(b), to.stat(a) - from.stat(a)));
        for (var stat : stats) {
            int gain = to.stat(stat) - from.stat(stat);
            if (gain <= 0) continue;
            if (out.length() > 0) out.append(", ");
            out.append("+").append(gain).append(" ").append(stat.label);
        }
        return out.length() == 0 ? "already maxed out" : out.toString();
    }

    /**
     * Decide whether this kill yields its card. Rolls the tier's chance, and
     * guarantees it once the player has gone {@link Tier#pityKills()} kills of
     * that mob empty-handed — the streak counter resets on every card, so the
     * pity only ever rescues a genuinely cold run.
     */
    private static boolean rollForCard(ServerPlayer player, String id, Tier tier) {
        float chance = tier.cardDropChance() * (float) dropMultiplier();
        Map<String, Integer> drought = new HashMap<>(player.getData(ModAttachments.DROUGHT.get()));
        int dry = drought.getOrDefault(id, 0) + 1;

        boolean won = chance >= 1.0f
                || player.getRandom().nextFloat() < chance
                || dry >= tier.pityKills();

        drought.put(id, won ? 0 : dry);
        // the HashMap above is already a private copy; Map.copyOf would copy it again
        player.setData(ModAttachments.DROUGHT.get(), drought);
        return won;
    }

    private static double dropMultiplier() {
        try {
            return Config.CARD_DROP_MULTIPLIER.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0; // config not up yet: ship defaults
        }
    }

    /** A card is an event now, so it gets a chime that sharpens with rarity. */
    private static void cardFanfare(ServerPlayer player, Tier tier) {
        float pitch = switch (tier) {
            case COMMON -> 1.0F;
            case UNCOMMON -> 1.15F;
            case RARE -> 1.3F;
            case EPIC -> 1.5F;
            case LEGENDARY -> 1.8F;
        };
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7F, pitch);
        if (tier.ordinal() >= Tier.RARE.ordinal()) {
            player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    6 + tier.ordinal() * 6, 0.4, 0.5, 0.4, 0.03);
        }
    }

    private static int bumpKills(ServerPlayer player, String id) {
        Map<String, Integer> counts = new HashMap<>(player.getData(ModAttachments.KILLS.get()));
        int next = counts.getOrDefault(id, 0) + 1;
        counts.put(id, next);
        player.setData(ModAttachments.KILLS.get(), counts);
        return next;
    }

    private static ItemEntity cardDrop(LivingEntity dead, ItemStack stack) {
        ItemEntity item = new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.3, dead.getZ(), stack);
        item.setDefaultPickUpDelay();
        return item;
    }
}
