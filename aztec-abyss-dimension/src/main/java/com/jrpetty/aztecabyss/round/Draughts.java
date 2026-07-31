package com.jrpetty.aztecabyss.round;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Draughts: the machines that upgrade <em>you</em>.
 *
 * <p>The Outpost had two of the genre's three pillars - a wall to buy from and a
 * bench to upgrade weapons at - and was missing the one that changes how a
 * player survives rather than how hard they hit. Draughts are that pillar.
 *
 * <p>They are bought once per run, cost a great deal, and are <b>lost the moment
 * you die</b>. That is what stops a long run compounding into invincibility: the
 * points you bank late are spent on things that vanish, so a round-forty player
 * is better equipped than a round-five one but never permanently safer.
 *
 * <p>Ironhide is deliberately the dearest. More health is the strongest thing
 * you can buy in a mode where the horde only ever gets harder to kill, and it
 * should cost like it.
 */
public final class Draughts {

    public enum Draught {
        IRONHIDE("Ironhide", "§7Eight more hearts' worth of punishment.", 2500),
        QUICKHAND("Quickhand", "§7Swing, draw and board noticeably faster.", 2000),
        DOUBLETAP("Doubletap", "§7Every hit lands harder.", 2000),
        SECOND_WIND("Second Wind", "§7Get up once, on your own, for free.", 3000);

        public final String title;
        public final String blurb;
        public final int price;

        Draught(String title, String blurb, int price) {
            this.title = title;
            this.blurb = blurb;
            this.price = price;
        }
    }

    /** What each player has drunk this run. */
    private static final Map<UUID, Set<Draught>> HELD = new HashMap<>();

    private static final ResourceLocation IRONHIDE_ID =
            ResourceLocation.fromNamespaceAndPath("aztecabyss", "ironhide");
    private static final ResourceLocation QUICKHAND_ID =
            ResourceLocation.fromNamespaceAndPath("aztecabyss", "quickhand");

    private Draughts() {
    }

    public static boolean has(UUID id, Draught d) {
        Set<Draught> set = HELD.get(id);
        return set != null && set.contains(d);
    }

    public static Set<Draught> heldBy(UUID id) {
        return HELD.getOrDefault(id, EnumSet.noneOf(Draught.class));
    }

    /**
     * Strips everything a player was carrying. Called on death and on the way
     * out - the attributes have to come off with it, or a bought draught would
     * follow someone back into the overworld.
     */
    public static void clear(ServerPlayer player) {
        HELD.remove(player.getUUID());
        removeModifier(player, Attributes.MAX_HEALTH, IRONHIDE_ID);
        removeModifier(player, Attributes.ATTACK_SPEED, QUICKHAND_ID);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void clearAll() {
        HELD.clear();
    }

    /** Buys one. Refuses politely if they already have it or cannot afford it. */
    public static boolean buy(ServerLevel level, ServerPlayer player, Draught d) {
        if (has(player.getUUID(), d)) {
            player.displayClientMessage(Component.literal(
                    "§7You have already drunk the " + d.title + "."), true);
            return false;
        }
        if (!OutpostEconomy.spend(player, d.price, d.title)) {
            return false;
        }
        HELD.computeIfAbsent(player.getUUID(), k -> EnumSet.noneOf(Draught.class)).add(d);
        apply(player, d);

        player.displayClientMessage(Component.literal(
                "§d✦ " + d.title + " §r" + d.blurb), false);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0F, 0.9F);
        return true;
    }

    /** Puts the standing effects on. The timed ones are read where they matter. */
    private static void apply(ServerPlayer player, Draught d) {
        switch (d) {
            case IRONHIDE -> addModifier(player, Attributes.MAX_HEALTH, IRONHIDE_ID, 16.0);
            case QUICKHAND -> addModifier(player, Attributes.ATTACK_SPEED, QUICKHAND_ID, 0.6);
            default -> {
                // Doubletap is read in the damage hook and Second Wind in the
                // downed handler; neither is an attribute.
            }
        }
        if (d == Draught.IRONHIDE) {
            player.heal(16.0F);
        }
    }

    private static void addModifier(ServerPlayer player,
                                    net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                    ResourceLocation id, double amount) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, amount,
                AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeModifier(ServerPlayer player,
                                       net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                       ResourceLocation id) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) {
            inst.removeModifier(id);
        }
    }

    /** Damage multiplier from Doubletap, or 1. */
    public static float damageMultiplier(UUID id) {
        return has(id, Draught.DOUBLETAP) ? 1.35F : 1.0F;
    }

    /** Whether this player can pick themselves up, and burns the charge if so. */
    public static boolean consumeSecondWind(ServerPlayer player) {
        Set<Draught> set = HELD.get(player.getUUID());
        if (set == null || !set.remove(Draught.SECOND_WIND)) {
            return false;
        }
        player.displayClientMessage(Component.literal(
                "§d✦ Second Wind. §7That was the only one."), false);
        return true;
    }

    /** A compact readout of what someone is carrying, for the round bar. */
    public static String hudFragment(UUID id) {
        Set<Draught> set = HELD.get(id);
        if (set == null || set.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" §8|");
        for (Draught d : set) {
            sb.append(" §d").append(d.title.charAt(0));
        }
        return sb.toString();
    }
}
