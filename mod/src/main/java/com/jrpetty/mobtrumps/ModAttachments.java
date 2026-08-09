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

    /** Permanent ranked season badges earned, e.g. "S1:Gold I". Kept on death. */
    public static final Supplier<AttachmentType<List<String>>> RANKED_BADGES =
            ATTACHMENTS.register("ranked_badges", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build());

    /** Mob ids whose (normal) card is physically stored inside the collection book. */
    public static final Supplier<AttachmentType<List<String>>> STORED =
            ATTACHMENTS.register("stored", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build());

    /**
     * The actual card items filed in the book. The id lists above say WHICH
     * mobs are filed (which is all the UI needs); this holds the real stacks,
     * so a card keeps its serial, condition and history while it is in there.
     */
    public static final Supplier<AttachmentType<List<net.minecraft.world.item.ItemStack>>> STORED_CARDS =
            ATTACHMENTS.register("stored_cards",
                    () -> AttachmentType.<List<net.minecraft.world.item.ItemStack>>builder(() -> List.of())
                    .serialize(net.minecraft.world.item.ItemStack.CODEC.listOf())
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

    /**
     * Kills of each mob since its card last dropped. Drives the pity guarantee
     * in {@link MobDrops}, so an unlucky streak always ends. Server-side only —
     * the client never needs to know how cold a streak has been.
     */
    public static final Supplier<AttachmentType<Map<String, Integer>>> DROUGHT =
            ATTACHMENTS.register("drought",
                    () -> AttachmentType.<Map<String, Integer>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .copyOnDeath()
                    .build());

    /** Category names whose completion reward this player has already claimed. */
    public static final Supplier<AttachmentType<List<String>>> CLAIMED_CATEGORIES =
            ATTACHMENTS.register("claimed_categories", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build());

    /**
     * Award state: achievement id -> {@code 1} unlocked (and announced) or
     * {@code 2} claimed. Ids missing from the map are still in progress.
     */
    public static final Supplier<AttachmentType<Map<String, Integer>>> ACHIEVEMENTS =
            ATTACHMENTS.register("achievements",
                    () -> AttachmentType.<Map<String, Integer>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .copyOnDeath()
                    .build());

    /** Categories completed whose free spawn egg is still waiting to be chosen. */
    public static final Supplier<AttachmentType<List<String>>> EGG_PENDING =
            ATTACHMENTS.register("egg_pending", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build());

    /** Set spawn eggs already taken, as "CATEGORY:mob_id" — one per set, ever. */
    public static final Supplier<AttachmentType<List<String>>> EGG_CLAIMED =
            ATTACHMENTS.register("egg_claimed", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build());

    /** Campaign progress: mission id -> 1 cleared, 2 cleared without losing a round. */
    public static final Supplier<AttachmentType<Map<String, Integer>>> CAMPAIGN =
            ATTACHMENTS.register("campaign",
                    () -> AttachmentType.<Map<String, Integer>>builder(() -> Map.of())
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

    /** Daily quest state: day stamp, rolled quest ids, metric baselines, claim flags. */
    public static final Supplier<AttachmentType<Map<String, Integer>>> QUEST_STATE =
            ATTACHMENTS.register("quest_state",
                    () -> AttachmentType.<Map<String, Integer>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .copyOnDeath()
                    .build());

    /** Free-form play counters ("pick_attack", "loss_Steve", "battles", ...) for the stats page. */
    public static final Supplier<AttachmentType<Map<String, Integer>>> PLAY_STATS =
            ATTACHMENTS.register("play_stats",
                    () -> AttachmentType.<Map<String, Integer>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .copyOnDeath()
                    .build());

    /**
     * The last few duels, newest first, one packed line each.
     *
     * <p>A list rather than more counters in {@link #PLAY_STATS}, because a
     * record is "7-2 against Steve" but a history is "and here is what
     * happened, in order" — and order is exactly what a map of counters throws
     * away. {@link MatchHistory} owns the format and the cap on its length.
     */
    public static final Supplier<AttachmentType<List<String>>> MATCH_LOG =
            ATTACHMENTS.register("match_log",
                    () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
