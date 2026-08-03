package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * What a Builder's hands are worth.
 *
 * <p>The Builder had one perk and it was about marking corridors, which is a
 * strange thing for a job called Builder. The name promises somebody who makes
 * things that are better than the things other people make, and nothing in the
 * game let them.
 *
 * <p>So: anything a Builder crafts comes out of their hands harder wearing than
 * the same recipe in anybody else's, and anything they forge that swings hits
 * harder. Not a bonus they carry - a bonus the <em>object</em> carries. A sword
 * a Builder made is a better sword after they hand it to somebody else, and
 * after they are dead, which is the difference between a craft and a buff.
 *
 * <h2>Why the item is marked rather than the player checked</h2>
 *
 * <p>Checking "is the person swinging this a Builder" would make the sword worse
 * the moment it changes hands, and would make every Builder's sword identical
 * whoever made it. Stamping the item at the moment of crafting means the Glade
 * ends up with objects that have histories, and a Runner going out with the
 * Builder's axe is carrying somebody else's work - which is the same idea the
 * larder is built on.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID)
public final class MazeForge {

    /** Extra durability on anything a Builder makes. */
    private static final double DURABILITY_BONUS = 0.40;
    /** Extra damage from anything a Builder forged that swings. */
    private static final float DAMAGE_BONUS = 0.20F;

    /** The stamp itself. */
    private static final String FORGED = "AztecForged";
    /** How much extra damage this particular object was forged with, in percent. */
    private static final String EDGE = "AztecEdge";

    private MazeForge() {
    }

    private static boolean isMaze(Level level) {
        return level instanceof ServerLevel sl
                && sl.dimension().equals(AztecAbyssConstants.MAZE_LEVEL_KEY);
    }

    public static boolean isForged(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean(FORGED);
    }

    /** The edge that was put on it, as a fraction. Zero if it was not forged. */
    private static float edgeOf(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getBoolean(FORGED)) {
            return 0.0F;
        }
        // Older forged gear predates the stamp and gets the base bonus.
        return tag.contains(EDGE) ? tag.getInt(EDGE) / 100.0F : DAMAGE_BONUS;
    }

    /**
     * A Builder finishes something at a bench.
     *
     * <p>Durability is written into the item rather than applied on use, because
     * the number wants to be visible: a pick that says 1836 uses where everyone
     * else's says 1312 is the perk explaining itself without a tooltip nobody
     * reads.
     */
    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isMaze(player.level())) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!MazeJobs.get(level).is(player.getUUID(), MazeJobs.BUILDER)) {
            return;
        }
        ItemStack out = event.getCrafting();
        if (out.isEmpty() || !out.isDamageableItem() || isForged(out)) {
            return;
        }
        int base = out.getMaxDamage();
        if (base <= 0) {
            return;
        }
        // Skill is read once, here, and written into the object. A sword does not
        // get sharper because the person who made it studied afterwards, and it
        // does not get blunter when they hand it to somebody untrained.
        int forge = MazeSkills.rankOf(level, player.getUUID(), "forgemaster");
        int sharp = MazeSkills.rankOf(level, player.getUUID(), "sharpening");
        double durability = DURABILITY_BONUS + 0.15 * forge;
        int edge = Math.round((DAMAGE_BONUS + 0.05F * sharp) * 100.0F);

        out.set(DataComponents.MAX_DAMAGE, (int) Math.round(base * (1.0 + durability)));
        CustomData.update(DataComponents.CUSTOM_DATA, out, tag -> {
            tag.putBoolean(FORGED, true);
            tag.putInt(EDGE, edge);
        });
        out.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("§6Forged in the Glade"),
                Component.literal("§8by " + player.getGameProfile().getName()),
                Component.literal("§8+" + Math.round(durability * 100) + "% wear  §8+" + edge + "% bite"))));
        player.displayClientMessage(Component.literal(
                "§6⚒ Your hands made it better. §7+" + Math.round(durability * 100)
                        + "% durability, +" + edge + "% damage"), true);
    }

    /**
     * A forged weapon landing a hit.
     *
     * <p>Done at the moment of damage rather than as an attribute modifier on the
     * item. Modifiers would have to be rebuilt from whatever the item already
     * carried, and getting that wrong silently deletes an item's own damage - a
     * sword that does less than a stick, with nothing to point at. Twenty percent
     * on the way out is the same number with none of that.
     *
     * <p>Only in the maze, and only against something that is not a player. A
     * Builder's axe should be a better answer to a Griever, not a better answer
     * to a Glader.
     */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || !isMaze(attacker.level())) {
            return;
        }
        float edge = edgeOf(attacker.getMainHandItem());
        if (edge <= 0.0F) {
            return;
        }
        event.setAmount(event.getAmount() * (1.0F + edge));
    }
}
