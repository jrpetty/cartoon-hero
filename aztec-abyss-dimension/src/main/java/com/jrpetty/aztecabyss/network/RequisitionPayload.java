package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The Glade's requisition slate, on its way to the screen that draws it.
 *
 * <p>Flat strings for the same reason every other sheet in this mod uses them:
 * the catalogue is a server-side list, the prices live beside it, and what the
 * Glade has committed is in a SavedData no client has ever seen. The server
 * knows, so the server says.
 *
 * <p>{@code summary} is packed rather than spread across fields because
 * {@code StreamCodec.composite} stops at six components and the pool now has
 * more parts than that. It is
 * {@code heads|fromHeads|fromWork|fromBounty|yourUnits|yourQuota|yourCredits|maxCredits|jobDisplay|unitName}.
 *
 * <p>Each row is {@code group|id|display|count|cost|yours|glade} - the last two
 * being how many of that line you put on the slate and how many the whole Glade
 * did, because with one shared pot the second number is the one that matters.
 *
 * @param day   which day of the run this slate is for
 * @param pool  everything the Glade has to spend today
 * @param spent what the Glade has already committed, across everybody
 */
public record RequisitionPayload(int day, int pool, int spent, String summary, List<String> rows)
        implements CustomPacketPayload {

    public static final Type<RequisitionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "requisition"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequisitionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RequisitionPayload::day,
                    ByteBufCodecs.VAR_INT, RequisitionPayload::pool,
                    ByteBufCodecs.VAR_INT, RequisitionPayload::spent,
                    ByteBufCodecs.STRING_UTF8, RequisitionPayload::summary,
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

    // --- named accessors into the packed summary, so the screen never counts ---
    public int heads() {
        return number(summary, 0);
    }

    public int fromHeads() {
        return number(summary, 1);
    }

    public int fromWork() {
        return number(summary, 2);
    }

    public int fromBounty() {
        return number(summary, 3);
    }

    public int yourUnits() {
        return number(summary, 4);
    }

    public int yourQuota() {
        return number(summary, 5);
    }

    public int yourCredits() {
        return number(summary, 6);
    }

    public int maxCredits() {
        return number(summary, 7);
    }

    public String jobDisplay() {
        return field(summary, 8);
    }

    public String unitName() {
        return field(summary, 9);
    }

    @Override
    public Type<RequisitionPayload> type() {
        return TYPE;
    }
}
