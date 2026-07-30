package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.List;
import java.util.Optional;

/**
 * What each campaign mission pays out, once, the first time it is cleared.
 *
 * <p>It opens on food — a few loaves for beating the farm — and climbs to
 * proper materials by the end. Deliberately restrained: twenty missions of a
 * card game should feel worth doing, not replace mining. The enchanted books
 * are the real prize at the top, and they are rolled exactly like an enchanting
 * table so the results are genuinely random: some will be superb and some will
 * be junk, which is the point of a book.
 *
 * <p>Every book the campaign pays out is an enchanted one. A stack of blank
 * books is not a reward, it is a shopping list.
 *
 * @param stacks    plain item payouts
 * @param books     how many enchanted books
 * @param bookLevel the enchanting level those books are rolled at, table-style
 */
public record CampaignRewards(List<Payout> stacks, int books, int bookLevel) {

    /** One plain stack in a payout. */
    public record Payout(Item item, int count) {
        public String label() {
            String name = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(item).getPath().replace('_', ' ');
            return count + "x " + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
    }

    private static Payout of(Item item, int count) {
        return new Payout(item, count);
    }

    /**
     * The ladder, in five acts.
     *
     * <p><b>1-5 provisions.</b> Food and travelling supplies only — not one
     * ingot. A card game must not hand a new player the materials they were
     * meant to go and mine for, so the early missions pay in the things that
     * <em>let</em> you go mining: meat, torches, a saddle, a few ender pearls.
     * Each payout is themed to the mission that gave it.
     *
     * <p><b>6-9 the first metals.</b> Iron arrives at six, moderately, and gold
     * and the first single diamond follow. Enchanted books start here at low
     * levels, so the reward is a lottery ticket rather than a guarantee.
     *
     * <p><b>10-14 the middle.</b> Diamonds in ones and twos, netherite scrap as
     * a rare treat, experience for the anvil.
     *
     * <p><b>15-20 the payoff.</b> This is where it becomes worth the twenty
     * missions: stacks of diamond, real netherite, and books rolled at the top
     * of the enchanting range. Three books at level 30 is the ceiling until the
     * finale, which is the only mission in the campaign that pays four.
     */
    public static CampaignRewards forMission(int index) {
        return switch (index) {
            // --- provisions: no metals, no gems -----------------------------
            case 1 -> new CampaignRewards(List.of(       // the farm feeds you
                    of(Items.BREAD, 12), of(Items.COOKED_BEEF, 6)), 0, 0);
            case 2 -> new CampaignRewards(List.of(       // spoils of the woods
                    of(Items.COOKED_PORKCHOP, 10), of(Items.APPLE, 8)), 0, 0);
            case 3 -> new CampaignRewards(List.of(       // the day's catch
                    of(Items.COOKED_SALMON, 12), of(Items.COOKED_COD, 8)), 0, 0);
            case 4 -> new CampaignRewards(List.of(       // light for the graves
                    of(Items.TORCH, 32), of(Items.GOLDEN_CARROT, 6)), 0, 0);
            case 5 -> new CampaignRewards(List.of(       // gear up and go
                    of(Items.ENDER_PEARL, 4), of(Items.SADDLE, 1),
                    of(Items.COOKED_BEEF, 12)), 0, 0);
            // --- the first metals -------------------------------------------
            case 6 -> new CampaignRewards(List.of(
                    of(Items.IRON_INGOT, 8), of(Items.GOLDEN_CARROT, 8)), 0, 0);
            case 7 -> new CampaignRewards(List.of(of(Items.IRON_INGOT, 10),
                    of(Items.GOLD_INGOT, 4), of(Items.ENDER_PEARL, 6)), 1, 5);
            case 8 -> new CampaignRewards(List.of(of(Items.EMERALD, 12),
                    of(Items.GOLD_INGOT, 8)), 1, 8);
            case 9 -> new CampaignRewards(List.of(of(Items.DIAMOND, 1),
                    of(Items.IRON_INGOT, 14), of(Items.ENDER_PEARL, 8)), 2, 10);
            // --- the middle ---------------------------------------------------
            case 10 -> new CampaignRewards(List.of(of(Items.DIAMOND, 2),
                    of(Items.GOLD_INGOT, 12)), 2, 12);
            case 11 -> new CampaignRewards(List.of(of(Items.DIAMOND, 2),
                    of(Items.IRON_INGOT, 16), of(Items.EXPERIENCE_BOTTLE, 8)), 2, 15);
            case 12 -> new CampaignRewards(List.of(of(Items.DIAMOND, 3),
                    of(Items.EMERALD, 16)), 2, 16);
            case 13 -> new CampaignRewards(List.of(of(Items.DIAMOND, 3),
                    of(Items.GOLD_INGOT, 16), of(Items.EXPERIENCE_BOTTLE, 10)), 3, 18);
            case 14 -> new CampaignRewards(List.of(of(Items.DIAMOND, 4),
                    of(Items.NETHERITE_SCRAP, 1)), 3, 20);
            // --- the payoff ---------------------------------------------------
            case 15 -> new CampaignRewards(List.of(of(Items.DIAMOND, 5),
                    of(Items.GOLD_BLOCK, 2), of(Items.EXPERIENCE_BOTTLE, 12)), 3, 22);
            case 16 -> new CampaignRewards(List.of(of(Items.DIAMOND, 6),
                    of(Items.NETHERITE_SCRAP, 1), of(Items.ENDER_PEARL, 12)), 3, 24);
            case 17 -> new CampaignRewards(List.of(of(Items.DIAMOND, 7),
                    of(Items.EMERALD_BLOCK, 2), of(Items.EXPERIENCE_BOTTLE, 14)), 3, 26);
            case 18 -> new CampaignRewards(List.of(of(Items.DIAMOND, 8),
                    of(Items.NETHERITE_SCRAP, 2)), 3, 28);
            case 19 -> new CampaignRewards(List.of(of(Items.DIAMOND, 10),
                    of(Items.EXPERIENCE_BOTTLE, 16)), 3, 30);
            case 20 -> new CampaignRewards(List.of(of(Items.DIAMOND, 12),
                    of(Items.NETHERITE_INGOT, 1), of(Items.EXPERIENCE_BOTTLE, 16),
                    of(Items.ENCHANTED_GOLDEN_APPLE, 1)), 4, 30);
            default -> new CampaignRewards(List.of(of(Items.BREAD, 4)), 0, 0);
        };
    }

    /** "12x Diamond · 1x Netherite ingot · 4 enchanted books (level 30)". */
    public String label() {
        StringBuilder sb = new StringBuilder();
        for (Payout p : stacks) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(p.label());
        }
        if (books > 0) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(books).append(" enchanted book").append(books == 1 ? "" : "s")
                    .append(" (level ").append(bookLevel).append(")");
        }
        return sb.toString();
    }

    /** Hand the whole payout over. Called once, on a mission's first clear. */
    public void grant(ServerPlayer player) {
        for (Payout p : stacks) {
            CardActions.give(player, new ItemStack(p.item(), p.count()));
        }
        RandomSource rng = player.getRandom();
        for (int i = 0; i < books; i++) {
            CardActions.give(player, enchantedBook(player, rng));
        }
    }

    /**
     * One book, rolled the way an enchanting table would at {@code bookLevel}.
     * No curation: whatever comes out, comes out.
     */
    private ItemStack enchantedBook(ServerPlayer player, RandomSource rng) {
        ItemStack book = new ItemStack(Items.BOOK);
        try {
            return EnchantmentHelper.enchantItem(rng, book, Math.max(1, bookLevel),
                    player.level().registryAccess(), Optional.empty());
        } catch (Exception e) {
            return book; // a failed roll must never swallow the rest of the payout
        }
    }

    /** Announce the haul in chat. */
    public void announce(ServerPlayer player) {
        String text = label();
        MutableComponent line = Component.literal("Mission reward: ").withStyle(ChatFormatting.GRAY);
        line.append(Component.literal(text.isEmpty() ? "—" : text).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(line);
    }
}
