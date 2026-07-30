package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wears cards out as they are handled.
 *
 * <p>The rule is deliberately narrow: a card loses condition when it
 * <em>transitions</em> into a player's active hand, never for merely existing
 * there. So we remember the authenticity id of whatever each player last had
 * held and only act when that changes — which costs one field read per online
 * player per tick and no allocation at all in the common case.
 *
 * <p>The hard part is everything that must NOT count. Logging in, respawning
 * and changing dimension all re-send the inventory, which would look exactly
 * like picking the card up again; each of those {@linkplain #seed seeds} the
 * remembered id instead, so the card the player was already holding is not
 * charged for being held. Comparing authenticity ids rather than item types
 * also means swapping between two different Creeper cards is correctly seen as
 * two separate cards, while jiggling the same one is not seen at all.
 */
public final class ConditionTracker {

    /** player -> authenticity id of the card they currently hold, or "" for none. */
    private static final Map<UUID, String> HELD = new HashMap<>();

    private ConditionTracker() {
    }

    /**
     * Remember what a player is holding WITHOUT charging for it. Used at every
     * moment the inventory is resent for reasons that are not the player
     * picking the card up: login, respawn, dimension change.
     */
    public static void seed(ServerPlayer player) {
        HELD.put(player.getUUID(), uidOf(player.getMainHandItem()));
    }

    public static void forget(UUID player) {
        HELD.remove(player);
    }

    /** Called every server tick for every player. Kept deliberately cheap. */
    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack held = player.getMainHandItem();
            String uid = uidOf(held);
            String previous = HELD.get(player.getUUID());
            if (uid.equals(previous)) {
                continue; // same card (or still nothing) — nothing has happened
            }
            HELD.put(player.getUUID(), uid);
            if (uid.isEmpty()) {
                continue; // they put a card away; putting away is free
            }
            onEnteredHand(server, player, held);
        }
    }

    /** A card has genuinely just become the held item. */
    private static void onEnteredHand(MinecraftServer server, ServerPlayer player, ItemStack stack) {
        // a card from before this system joins it the first time it is picked up
        CardIdentityService.ensureRegistered(server, stack);

        CardWear before = CardIdentityService.wearOf(stack);
        CardWear after = before.handled(CardIdentityService.wearPoints());
        if (after.equals(before)) {
            return; // sleeved, or already ruined
        }
        CardIdentityService.setWear(stack, after);
        // re-seed: setWear replaces the component but not the identity, so the
        // uid is unchanged and this will not re-trigger
        HELD.put(player.getUUID(), CardIdentityService.identityOf(stack) == null
                ? "" : CardIdentityService.identityOf(stack).uid());

        if (!before.firstHandUsed()) {
            player.displayClientMessage(Component.literal("First handling — still Mint. "
                            + "Sleeve it to keep it that way.")
                    .withStyle(ChatFormatting.DARK_GREEN), true);
        } else if (after.condition() != before.condition()) {
            player.displayClientMessage(Component.literal(CardIdentityService.serialText(stack)
                            + "  ·  " + after.condition() + "% " + after.label())
                    .withStyle(after.condition() >= 55 ? ChatFormatting.GRAY : ChatFormatting.RED), true);
        }
    }

    private static String uidOf(ItemStack stack) {
        if (stack.isEmpty() || MobCardItem.cardOf(stack) == null) {
            return "";
        }
        CardIdentity id = CardIdentityService.identityOf(stack);
        // an unregistered card still needs to be distinguishable from no card at
        // all, so it reports a stable placeholder until it is issued an id
        return id == null || !id.valid() ? "unregistered" : id.uid();
    }
}
