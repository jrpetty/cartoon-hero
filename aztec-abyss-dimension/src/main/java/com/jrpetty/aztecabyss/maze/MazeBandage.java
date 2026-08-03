package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;
import java.util.Optional;

/**
 * Bandages: the thing a Med-jack actually does all day.
 *
 * <p>The job could hold off a Changing and, at the top of the trade, cure one -
 * both of which are rare, dramatic and useless on an ordinary Tuesday. A
 * Med-jack whose whole contribution is "be standing next to the right person
 * during the worst ninety seconds of the week" spends most of the game as a
 * Glader with a job title.
 *
 * <p>A bandage is the small version of the same idea, and it is what makes the
 * role a supply line rather than an emergency service. Anybody can make them out
 * of what the Box brings up; a Med-jack gets half again as many from the same
 * materials, and theirs mend more. That is the shape a support role should have:
 * everyone can do it badly, one person does it properly, and the difference is
 * felt by other people rather than by them.
 *
 * <h2>Built like the serum</h2>
 *
 * <p>A potion carrying custom effects rather than a registered item, for exactly
 * the reasons the serum is - a new item would need a model and a texture this mod
 * does not have, and a missing-texture bandage is worse than a real bottle. It
 * drinks, stacks, renders and lists its own effects with no client code at all.
 */
public final class MazeBandage {

    /** What one costs. */
    public static final int STRING_COST = 2;
    public static final int PAPER_COST = 1;
    /** What that buys, and what a Med-jack gets for the same. */
    public static final int YIELD = 2;
    public static final int MEDJACK_YIELD = 3;

    private MazeBandage() {
    }

    /**
     * A bandage of a given quality.
     *
     * <p>Regeneration rather than instant health, because a bandage should be
     * something you take cover to use rather than a button that undoes a mistake
     * mid-fight. Ten seconds of it is roughly four hearts, and every rank of
     * Field Dressing is worth another one.
     */
    public static ItemStack create(int dressingRank) {
        int seconds = 10 + Math.max(0, dressingRank) * 3;
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
                Optional.empty(),
                Optional.of(0xE8DCC8), // linen
                List.of(new MobEffectInstance(MobEffects.REGENERATION, seconds * 20, 0, false, true))));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(
                dressingRank > 0 ? "§f§lBandage §7(" + dressingRank + ")" : "§fBandage"));
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                List.of(Component.literal("§7Mends over " + seconds + " seconds."),
                        Component.literal("§8Linen and clean water."))));
        return stack;
    }

    /** Counts what a player is carrying of something. */
    public static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Takes materials out. Assumes {@link #count} said there were enough. */
    public static void take(ServerPlayer player, net.minecraft.world.item.Item item, int amount) {
        int left = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(item)) {
                continue;
            }
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
        }
    }
}
