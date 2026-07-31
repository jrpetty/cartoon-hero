package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.CardCondition;
import com.jrpetty.mobtrumps.game.CardEdition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Stamps and reads the identity on physical cards. Everything here is
 * server-side: the client may display a serial or a condition, but it never
 * decides one.
 */
public final class CardIdentityService {

    private CardIdentityService() {
    }

    // --- reading -------------------------------------------------------------

    /** The identity on a card, or null if it has none (an unregistered card). */
    public static CardIdentity identityOf(ItemStack stack) {
        return stack.get(ModItems.CARD_IDENTITY.get());
    }

    /** The wear on a card; a card without the component is treated as pristine. */
    public static CardWear wearOf(ItemStack stack) {
        CardWear wear = stack.get(ModItems.CARD_WEAR.get());
        return wear == null ? CardWear.PRISTINE : wear;
    }

    public static boolean isSleeved(ItemStack stack) {
        return wearOf(stack).sleeved();
    }

    public static int conditionOf(ItemStack stack) {
        return wearOf(stack).condition();
    }

    /** True only for a card carrying a server-issued identity. */
    public static boolean isAuthentic(ItemStack stack) {
        CardIdentity id = identityOf(stack);
        return id != null && id.valid();
    }

    public static void setWear(ItemStack stack, CardWear wear) {
        stack.set(ModItems.CARD_WEAR.get(), wear);
    }

    // --- issuing -------------------------------------------------------------

    /**
     * Give a freshly created card its permanent identity: the next serial for
     * that mob, a unique authenticity id, and the player whose kill earned it.
     * Called once, at the moment the card comes into existence.
     */
    public static ItemStack issue(MinecraftServer server, ItemStack stack,
                                  ServerPlayer unlocker, CardEdition edition) {
        String mobId = stack.get(ModItems.MOB_ID.get());
        if (server == null || mobId == null || mobId.isEmpty()) {
            return stack;
        }
        if (isAuthentic(stack)) {
            return stack; // already issued; never renumber a card
        }
        int serial = SerialRegistry.get(server).next(mobId);
        stack.set(ModItems.CARD_IDENTITY.get(), new CardIdentity(
                UUID.randomUUID().toString(),
                serial,
                unlocker == null ? "" : unlocker.getUUID().toString(),
                unlocker == null ? "" : unlocker.getGameProfile().getName(),
                System.currentTimeMillis(),
                (edition == null ? CardEdition.STANDARD : edition).name(),
                false));
        stack.set(ModItems.CARD_WEAR.get(), CardWear.PRISTINE);
        // 000001 is a fact about the server, not about anyone's inventory: it
        // stays true after the card is traded, and after it is shredded
        if (serial == 1 && unlocker != null) {
            HallOfFame.get(server).recordFirstPrint(mobId, unlocker);
        }
        return stack;
    }

    /**
     * Bring a card that predates this system into it. It gets a real serial from
     * the same counter, so it can never collide with a newly issued one, but is
     * flagged {@code migrated} and displays an M — its true place in the mob's
     * history cannot be proven, and pretending otherwise would devalue the cards
     * that really were first.
     *
     * <p>No unlocker is invented: an unknown one stays unknown.
     */
    public static ItemStack migrate(MinecraftServer server, ItemStack stack) {
        String mobId = stack.get(ModItems.MOB_ID.get());
        if (server == null || mobId == null || mobId.isEmpty() || isAuthentic(stack)) {
            return stack;
        }
        int serial = SerialRegistry.get(server).next(mobId);
        boolean foil = MobCardItem.isFoilCard(stack);
        stack.set(ModItems.CARD_IDENTITY.get(), new CardIdentity(
                UUID.randomUUID().toString(), serial, "", "", 0L,
                (foil ? CardEdition.FOIL : CardEdition.STANDARD).name(), true));
        if (stack.get(ModItems.CARD_WEAR.get()) == null) {
            stack.set(ModItems.CARD_WEAR.get(), CardWear.PRISTINE);
        }
        return stack;
    }

    /** Issue OR migrate as appropriate — safe to call on any card, any time. */
    public static void ensureRegistered(MinecraftServer server, ItemStack stack) {
        if (!isAuthentic(stack) && MobCardItem.cardOf(stack) != null) {
            migrate(server, stack);
        }
    }

    // --- config --------------------------------------------------------------

    public static int wearPoints() {
        try {
            return Config.CARD_WEAR_PER_HANDLING.get();
        } catch (IllegalStateException notLoaded) {
            return CardCondition.DEFAULT_WEAR;
        }
    }

    public static int serialDigits() {
        try {
            return Config.SERIAL_DIGITS.get();
        } catch (IllegalStateException notLoaded) {
            return 6;
        }
    }

    /** Whether a 0% card may still be played, or is display-only. */
    public static boolean ruinedPlayable() {
        try {
            return Config.RUINED_CARDS_PLAYABLE.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** The formatted serial for a card, e.g. "CREEPER-000001". */
    public static String serialText(ItemStack stack) {
        CardIdentity id = identityOf(stack);
        if (id == null || !id.valid()) {
            return "";
        }
        return id.formatSerial(stack.get(ModItems.MOB_ID.get()), serialDigits());
    }
}
