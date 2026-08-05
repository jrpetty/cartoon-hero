package com.gadgets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The two halves of a Base Tablet's conversation with a hub.
 *
 * <p>{@link Request} goes up with a code; {@link Reply} comes back with whatever
 * that hub last published, and how long ago it was heard from. The age is not a
 * detail — a tablet that cannot say how stale its reading is would be worse than
 * no tablet, because "all clear" from an hour ago looks exactly like "all clear"
 * from a second ago.
 */
public final class HubReportPayload {

    private HubReportPayload() {
    }

    /** Client -> server: "what is hub 1234 5678 saying?" */
    public record Request(String code) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Request> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Gadgets.MODID, "hub_ask"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Request::code,
                Request::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        /**
         * The shortest gap between two answers to the same player.
         *
         * <p>A reply carries a whole board, and a packet a client can ask for at
         * will should not be one it can ask for as fast as it likes. Deliberately
         * short: a tablet asks once a second, and a player who clicks Link in the
         * tenth of a second after an automatic ask should see their answer, not a
         * screen that appears to have ignored them. Two ticks bounds the traffic
         * without ever being the reason something feels broken — and the worst it
         * can cost is the "Asking…" the screen is already showing.
         */
        private static final long MIN_GAP = 2L;
        private static final String ASKED_KEY = "GadgetsHubAsk";

        /** Answer from the registry. Never touches the world, so it can never load a chunk. */
        public static void apply(ServerPlayer player, Request p) {
            long now = player.serverLevel().getGameTime();
            long last = player.getPersistentData().getLong(ASKED_KEY);
            if (last != 0L && now >= last && now - last < MIN_GAP) {
                return; // the screen re-asks a moment later; nothing is lost
            }
            player.getPersistentData().putLong(ASKED_KEY, now);

            HubRegistry.Report report = HubRegistry.get(p.code());
            if (report == null) {
                Gadgets.sendReply(player, new Reply(false, "", "", 0L, new CompoundTag()));
                return;
            }
            long age = Math.max(0L, player.serverLevel().getGameTime() - report.heard());
            Gadgets.sendReply(player, new Reply(true, report.name(), report.dim(), age, report.board()));
        }
    }

    /**
     * Client -> server: "write this code onto the tablet I am holding".
     *
     * <p>Separate from the request because the stack lives on the server: the
     * client cannot write to it and be believed, so the code is applied here and
     * the answer comes straight back rather than after another round trip.
     */
    public record Link(String code) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Link> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Gadgets.MODID, "tablet_link"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Link> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Link::code,
                Link::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void apply(ServerPlayer player, Link p) {
            String code = p.code();
            if (!code.isEmpty() && !LinkCode.isCode(code)) {
                return;
            }
            ItemStack tablet = player.getMainHandItem().getItem() instanceof BaseTabletItem
                    ? player.getMainHandItem() : player.getOffhandItem();
            if (!(tablet.getItem() instanceof BaseTabletItem)) {
                return; // not holding one any more — nothing to write to
            }
            BaseTabletItem.setCode(tablet, code);
            Request.apply(player, new Request(code));
        }
    }

    /** Server -> client: the board, or a plain "nothing is answering that code". */
    public record Reply(boolean found, String name, String dim, long ageTicks, CompoundTag board)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Reply> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Gadgets.MODID, "hub_report"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Reply::found,
                ByteBufCodecs.STRING_UTF8, Reply::name,
                ByteBufCodecs.STRING_UTF8, Reply::dim,
                ByteBufCodecs.VAR_LONG, Reply::ageTicks,
                ByteBufCodecs.COMPOUND_TAG, Reply::board,
                Reply::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
