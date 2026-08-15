package com.jrpetty.aztecabyss.item;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Heart Core: ten hearts, carried anywhere, at the price of your speed.
 *
 * <h2>Why it is not held</h2>
 *
 * <p>It started as an off-hand item, which was a mistake that would have
 * killed it. The arenas hand out Totems of Undying - three of them in the
 * grand prize alone - and a totem beats ten hearts in that slot every single
 * time: hearts delay a death, a totem cancels one. The Core would have gone
 * in a chest the first time anybody won anything, which is a miserable fate
 * for the reward of a whole mode.
 *
 * <p>So it does not want a slot. It works from anywhere in your inventory,
 * and it never competes with a totem again - you carry both, and the totem
 * catches the death that the extra hearts did not prevent.
 *
 * <h2>What it costs instead</h2>
 *
 * <p>Weight. It is a heart of stone the size of your chest, and carrying it
 * takes fifteen per cent off your movement. That price lands very differently
 * depending on where you are, which is the interesting part: on the Bridge and
 * in the Temple, where the whole job is holding ground, it is nearly free. In
 * the maze, where the job is covering distance before the doors shut, it is a
 * genuine sacrifice - a Runner who carries it will not make the run.
 *
 * <p>So the three relics end up wanting different homes: the Fang for the
 * maze, the Edge for anything armoured, and the Core for the two maps that
 * ask you to stand still and not die.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID)
public final class HeartCore {

    private HeartCore() {
    }

    private static final ResourceLocation HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "heart_core_health");
    private static final ResourceLocation WEIGHT_ID =
            ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "heart_core_weight");

    /** Twenty points of health: ten hearts, exactly doubling a player. */
    private static final double HEARTS = 20.0;
    /** And fifteen per cent off the top of your speed for the privilege. */
    private static final double WEIGHT = -0.15;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Once a second is plenty for something that changes when an item
        // moves between two slots.
        if (player.tickCount % 20 != 0) {
            return;
        }
        boolean carried = carrying(player);
        set(player, Attributes.MAX_HEALTH, HEALTH_ID, HEARTS,
                AttributeModifier.Operation.ADD_VALUE, carried);
        set(player, Attributes.MOVEMENT_SPEED, WEIGHT_ID, WEIGHT,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, carried);
    }

    /** Anywhere at all - main hand, off hand, or the bottom of the pack. */
    private static boolean carrying(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(ModItems.HEART_CORE.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds or removes one modifier, and only when it actually has to.
     *
     * <p>Re-applying a max-health modifier every second would churn the
     * player's health bar for no reason, so the state is checked first.
     */
    private static void set(ServerPlayer player, Holder<Attribute> attribute,
                            ResourceLocation id, double amount,
                            AttributeModifier.Operation op, boolean wanted) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        boolean has = instance.getModifier(id) != null;
        if (wanted && !has) {
            instance.addPermanentModifier(new AttributeModifier(id, amount, op));
        } else if (!wanted && has) {
            instance.removeModifier(id);
            // Dropping max health leaves the bar over its own top until
            // something clamps it; do it here rather than wait for a hit.
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }
}
