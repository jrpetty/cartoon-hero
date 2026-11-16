package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> client: the run panel for a map played on the Arena Engine - a
 * player's map from the portal, or a play-test in the Workshop.
 *
 * <p>Already worded on the server, because the engine is the only side that
 * knows what a ruleset calls its rounds, its currency and its objective. The
 * client only lays it out.
 *
 * <ul>
 *   <li>{@code title} - the map's name; empty takes the panel down;</li>
 *   <li>{@code headline} - the big line, e.g. "ROUND 3" or "WAITING";</li>
 *   <li>{@code right} - beside it, e.g. "12 left" or "next in 7s";</li>
 *   <li>{@code progress} - the bar under it, in thousandths;</li>
 *   <li>{@code accent} - the panel's colour, ARGB;</li>
 *   <li>{@code lines} - the rest, one per row, with colour codes: your
 *       points, your kills, the squad, the objective.</li>
 * </ul>
 */
public record EngineHudPayload(String title, String headline, String right, int progress, int accent,
                               List<String> lines) implements CustomPacketPayload {

    public static final Type<EngineHudPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "engine_hud"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EngineHudPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, EngineHudPayload::title,
                    ByteBufCodecs.STRING_UTF8, EngineHudPayload::headline,
                    ByteBufCodecs.STRING_UTF8, EngineHudPayload::right,
                    ByteBufCodecs.VAR_INT, EngineHudPayload::progress,
                    ByteBufCodecs.INT, EngineHudPayload::accent,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), EngineHudPayload::lines,
                    EngineHudPayload::new);

    /** The message that takes the panel down. */
    public static EngineHudPayload cleared() {
        return new EngineHudPayload("", "", "", 0, 0, List.of());
    }

    @Override
    public Type<EngineHudPayload> type() {
        return TYPE;
    }
}
