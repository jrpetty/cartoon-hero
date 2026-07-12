package com.jrpetty.mobtrumps;

import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MobTrumps.MODID);

    /** Card ids this player has ever collected. Saved with the player and kept on death. */
    public static final Supplier<AttachmentType<List<String>>> COLLECTED =
            ATTACHMENTS.register("collected", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build());

    /** Card ids this player has collected as a holographic foil. */
    public static final Supplier<AttachmentType<List<String>>> COLLECTED_FOIL =
            ATTACHMENTS.register("collected_foil", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build());

    /** How many mob duels this player has won. */
    public static final Supplier<AttachmentType<Integer>> DUEL_WINS =
            ATTACHMENTS.register("duel_wins", () -> AttachmentType.<Integer>builder(() -> 0)
                    .serialize(Codec.INT)
                    .copyOnDeath()
                    .build());

    /** The player's custom battle deck (mob ids). Empty means "use a random deck". */
    public static final Supplier<AttachmentType<List<String>>> DECK =
            ATTACHMENTS.register("deck", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
