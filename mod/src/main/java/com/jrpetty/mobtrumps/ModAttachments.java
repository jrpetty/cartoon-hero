package com.jrpetty.mobtrumps;

import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Map;
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

    /** Mob ids whose foil variant is chosen as the "top of the pile" in the book. */
    public static final Supplier<AttachmentType<List<String>>> DISPLAY_FOIL =
            ATTACHMENTS.register("display_foil", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build());

    /** Epoch millis when this player last claimed their free daily pack. */
    public static final Supplier<AttachmentType<Long>> LAST_DAILY =
            ATTACHMENTS.register("last_daily", () -> AttachmentType.<Long>builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .copyOnDeath()
                    .build());

    /** Mob ids whose (normal) card is physically stored inside the collection book. */
    public static final Supplier<AttachmentType<List<String>>> STORED =
            ATTACHMENTS.register("stored", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build());

    /** Mob ids whose foil card is physically stored inside the collection book. */
    public static final Supplier<AttachmentType<List<String>>> STORED_FOIL =
            ATTACHMENTS.register("stored_foil", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build());

    /** How many of each mob this player has killed (mob id -> kill count), toward holo unlocks. */
    public static final Supplier<AttachmentType<Map<String, Integer>>> KILLS =
            ATTACHMENTS.register("kills", () -> AttachmentType.<Map<String, Integer>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .copyOnDeath()
                    .build());

    /** Named saved decks (deck name -> mob ids), beyond the active DECK. */
    public static final Supplier<AttachmentType<Map<String, List<String>>>> SAVED_DECKS =
            ATTACHMENTS.register("saved_decks",
                    () -> AttachmentType.<Map<String, List<String>>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()))
                    .copyOnDeath()
                    .build());

    /** Free-form play counters ("pick_attack", "loss_Steve", "battles", ...) for the stats page. */
    public static final Supplier<AttachmentType<Map<String, Integer>>> PLAY_STATS =
            ATTACHMENTS.register("play_stats",
                    () -> AttachmentType.<Map<String, Integer>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
