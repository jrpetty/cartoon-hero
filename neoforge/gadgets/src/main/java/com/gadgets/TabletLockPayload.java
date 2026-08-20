package com.gadgets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The tablet lock's conversation with the server.
 *
 * <p>The passcode travels up in plaintext and only its hash ever comes back or
 * touches the item, and every decision is made here rather than on a screen:
 * a client that draws no lock screen at all is still answered with nothing,
 * because {@link HubReportPayload} refuses a locked, unproven tablet.
 */
public final class TabletLockPayload {

    private TabletLockPayload() {
    }

    /** The tablet in the player's hands, or empty when they are not holding one. */
    static ItemStack heldTablet(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof BaseTabletItem) {
            return player.getMainHandItem();
        }
        return player.getOffhandItem().getItem() instanceof BaseTabletItem
                ? player.getOffhandItem() : ItemStack.EMPTY;
    }

    /**
     * True when the player's held tablet is locked and this session has not
     * proven its passcode — the one condition under which report requests are
     * ignored. A player holding no tablet is not locked out: the eight-digit
     * hub code is itself the credential for asking about a base.
     */
    static boolean lockedOut(ServerPlayer player) {
        ItemStack tablet = heldTablet(player);
        if (tablet.isEmpty()) {
            return false;
        }
        String lock = BaseTabletItem.lockOf(tablet);
        return !lock.isEmpty() && !TabletAuth.proven(player.getUUID(), lock);
    }

    /**
     * Client -> server: "lock the tablet I am holding with this passcode".
     * Setting a fresh tablet's first passcode and changing one already proven
     * are the same request; re-keying somebody else's locked tablet is not —
     * a thief who cannot open a tablet must not be able to make it theirs.
     */
    public record SetLock(String passcode) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetLock> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Gadgets.MODID, "tablet_lock"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetLock> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SetLock::passcode,
                SetLock::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void apply(ServerPlayer player, SetLock p) {
            ItemStack tablet = heldTablet(player);
            if (tablet.isEmpty() || !BaseTabletItem.isPasscode(p.passcode())) {
                return;
            }
            String existing = BaseTabletItem.lockOf(tablet);
            if (!existing.isEmpty() && !TabletAuth.proven(player.getUUID(), existing)) {
                return;
            }
            String hash = BaseTabletItem.hash(p.passcode());
            BaseTabletItem.setLock(tablet, hash);
            TabletAuth.grant(player.getUUID(), hash);
            Gadgets.sendLockResult(player, new Result(true, hash));
        }
    }

    /** Client -> server: "here are the four digits for the tablet I am holding". */
    public record Unlock(String passcode) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Unlock> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Gadgets.MODID, "tablet_unlock"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Unlock> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Unlock::passcode,
                Unlock::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void apply(ServerPlayer player, Unlock p) {
            ItemStack tablet = heldTablet(player);
            if (tablet.isEmpty()) {
                return;
            }
            String lock = BaseTabletItem.lockOf(tablet);
            if (!lock.isEmpty() && BaseTabletItem.isPasscode(p.passcode())
                    && lock.equals(BaseTabletItem.hash(p.passcode()))) {
                TabletAuth.grant(player.getUUID(), lock);
                Gadgets.sendLockResult(player, new Result(true, lock));
            } else {
                Gadgets.sendLockResult(player, new Result(false, ""));
            }
        }
    }

    /**
     * Server -> client: granted or not. Carries the proven hash so the client
     * can remember it without re-reading the stack — the item update racing
     * this packet was the alternative, and races make flaky locks.
     */
    public record Result(boolean ok, String hash) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Result> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Gadgets.MODID, "tablet_lock_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Result::ok,
                ByteBufCodecs.STRING_UTF8, Result::hash,
                Result::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
