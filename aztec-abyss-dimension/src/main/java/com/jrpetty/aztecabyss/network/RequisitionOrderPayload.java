package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One click on the requisition screen.
 *
 * <p>A change rather than a state: {@code +1} on iron, {@code -1} on iron. The
 * client never sends the slate it thinks you have, because a client that sends
 * a whole slate is a client that can be made to send any slate it likes. The
 * server applies the change against its own copy, re-checks the budget exactly
 * as the command always did, and answers with the truth.
 *
 * @param id    a catalogue id, or {@link #CLEAR} to take the whole slate back
 * @param delta how many bundles to add, or a negative number to take off
 */
public record RequisitionOrderPayload(String id, int delta) implements CustomPacketPayload {

    /** Not a catalogue id. Means "take the lot off". */
    public static final String CLEAR = "*";

    public static final Type<RequisitionOrderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "requisition_order"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequisitionOrderPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, RequisitionOrderPayload::id,
                    ByteBufCodecs.VAR_INT, RequisitionOrderPayload::delta,
                    RequisitionOrderPayload::new);

    @Override
    public Type<RequisitionOrderPayload> type() {
        return TYPE;
    }
}
