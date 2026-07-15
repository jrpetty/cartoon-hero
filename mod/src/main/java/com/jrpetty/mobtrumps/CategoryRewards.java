package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Category;
import com.jrpetty.mobtrumps.game.CategoryReward;
import com.jrpetty.mobtrumps.game.MobCategories;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Grants the one-time haul for completing a mob category: difficulty-scaled
 * diamonds/iron/gold plus a single randomly-enchanted armour piece. The armour
 * is rolled like an enchanting table, so its enchants range from junk to great.
 */
public final class CategoryRewards {

    private CategoryRewards() {
    }

    /** True if the player has collected every mob in {@code category}. */
    public static boolean isComplete(ServerPlayer player, Category category) {
        List<String> collected = player.getData(ModAttachments.COLLECTED.get());
        for (String id : MobCategories.members(category)) {
            if (!collected.contains(id)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isClaimed(ServerPlayer player, Category category) {
        return player.getData(ModAttachments.CLAIMED_CATEGORIES.get()).contains(category.name());
    }

    /**
     * Check every category the player has completed and pay out any not yet
     * claimed. Called after a card is newly collected.
     */
    public static void checkAndAward(ServerPlayer player) {
        try {
            if (!Config.CATEGORY_REWARDS.get()) {
                return;
            }
        } catch (IllegalStateException notLoaded) {
            return;
        }
        for (Category category : Category.values()) {
            if (!isClaimed(player, category) && isComplete(player, category)) {
                award(player, category);
            }
        }
    }

    private static void award(ServerPlayer player, Category category) {
        // mark claimed first so a re-entrant collection event can't double-pay
        List<String> claimed = new ArrayList<>(player.getData(ModAttachments.CLAIMED_CATEGORIES.get()));
        claimed.add(category.name());
        player.setData(ModAttachments.CLAIMED_CATEGORIES.get(), List.copyOf(claimed));

        CategoryReward reward = CategoryReward.of(category);
        double mult = multiplier();
        int iron = scale(reward.iron(), mult);
        int gold = scale(reward.gold(), mult);
        int diamond = scale(reward.diamond(), mult);

        give(player, new ItemStack(Items.IRON_INGOT, iron));
        give(player, new ItemStack(Items.GOLD_INGOT, gold));
        give(player, new ItemStack(Items.DIAMOND, diamond));
        ItemStack armor = randomEnchantedArmor(player, reward.armor());
        give(player, armor);

        announce(player, category, iron, gold, diamond, armor);
    }

    private static double multiplier() {
        try {
            return Config.CATEGORY_REWARD_MULTIPLIER.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0;
        }
    }

    private static int scale(int base, double mult) {
        if (base <= 0) return 0;
        return Math.max(0, (int) Math.round(base * mult));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        player.getInventory().placeItemBackInInventory(stack);
    }

    /** A random armour piece of the reward's tier, enchanted like an enchant table. */
    private static ItemStack randomEnchantedArmor(ServerPlayer player, CategoryReward.ArmorTier tier) {
        RandomSource rng = player.getRandom();
        var pieces = armorPieces(tier);
        ItemStack stack = new ItemStack(pieces[rng.nextInt(pieces.length)]);
        // table-style roll: low levels are often duds, high levels can be superb
        int cost = 1 + rng.nextInt(30);
        try {
            return EnchantmentHelper.enchantItem(rng, stack, cost,
                    player.level().registryAccess(), Optional.empty());
        } catch (Exception e) {
            // never let a reward roll fail the whole payout
            return stack;
        }
    }

    private static net.minecraft.world.item.Item[] armorPieces(CategoryReward.ArmorTier tier) {
        return switch (tier) {
            case IRON -> new net.minecraft.world.item.Item[]{
                    Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
            case DIAMOND -> new net.minecraft.world.item.Item[]{
                    Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
            case NETHERITE -> new net.minecraft.world.item.Item[]{
                    Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
        };
    }

    private static void announce(ServerPlayer player, Category category,
                                 int iron, int gold, int diamond, ItemStack armor) {
        Style accent = Style.EMPTY.withColor(TextColor.fromRgb(category.accent() & 0xFFFFFF)).withBold(true);
        player.sendSystemMessage(Component.literal("✦ CATEGORY COMPLETE: " + category.label() + "! ✦")
                .withStyle(accent));
        MutableComponent haul = Component.literal("Reward: ").withStyle(ChatFormatting.GRAY);
        haul.append(part(diamond, "Diamond", ChatFormatting.AQUA));
        haul.append(part(iron, "Iron Ingot", ChatFormatting.WHITE));
        haul.append(part(gold, "Gold Ingot", ChatFormatting.GOLD));
        haul.append(Component.literal("+ ").withStyle(ChatFormatting.GRAY))
                .append(armor.getHoverName().copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        player.sendSystemMessage(haul);

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
        player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.6, 0.7, 0.6, 0.25);
    }

    private static Component part(int count, String name, ChatFormatting color) {
        if (count <= 0) return Component.empty();
        return Component.literal(count + "x " + name + (count == 1 ? "" : "s") + " ")
                .withStyle(color);
    }
}
