package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A player's requisition slate, on its way to the screen that draws it.
 *
 * <p>Flat strings for the same reason every other sheet in this mod uses them:
 * the catalogue is a server-side list, the prices live beside it, and what
 * somebody has already committed is in a SavedData no client has ever seen. The
 * server knows, so the server says.
 *
 * <p>Each row is {@code group|id|display|count|cost|ordered}. One packet carries
 * the whole catalogue and the whole slate, so opening the screen never waits on
 * a second round trip and adding a line is a single message each way.
 *
 * @param day     which day of the run this slate is for, for the header
 * @param budget  everything the player has to spend today, bounties included
 * @param bonus   how much of that budget came from Griever bounties
 * @param spent   what is already committed
 */
public record RequisitionPayload(int day, int budget, int bonus, int spent, List<String> rows)
        implements CustomPacketPayload {

    public static final Type<RequisitionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "requisition"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequisitionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RequisitionPayload::day,
                    ByteBufCodecs.VAR_INT, RequisitionPayload::budget,
                    ByteBufCodecs.VAR_INT, RequisitionPayload::bonus,
                    ByteBufCodecs.VAR_INT, RequisitionPayload::spent,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RequisitionPayload::rows,
                    RequisitionPayload::new);

    /** Field {@code index} of a packed row, or empty. */
    public static String field(String packed, int index) {
        String[] parts = packed.split("\\|", -1);
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    public static int number(String packed, int index) {
        try {
            return Integer.parseInt(field(packed, index));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public Type<RequisitionPayload> type() {
        return TYPE;
    }
}
