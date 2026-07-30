package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.CardEdition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The immutable half of a physical card: who it is, when it was born and who
 * made it. Never changes for the life of the card — trading, selling, sleeving
 * and wearing it out all leave this untouched.
 *
 * @param uid           unforgeable per-card id; a card without one is not authentic
 * @param serial        the per-mob sequence number, 1 for that mob's first ever card
 * @param unlockerId    UUID of the player whose kill created this card
 * @param unlockerName  their name at the time, so it still reads if they rename
 * @param unlockedAt    epoch millis of creation
 * @param edition       {@link CardEdition} name
 * @param migrated      true for a card that predates this system and was
 *                      numbered retrospectively, so its place in history is a
 *                      guess rather than a fact
 */
public record CardIdentity(String uid, int serial, String unlockerId, String unlockerName,
                           long unlockedAt, String edition, boolean migrated) {

    public static final Codec<CardIdentity> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("uid").forGetter(CardIdentity::uid),
            Codec.INT.fieldOf("serial").forGetter(CardIdentity::serial),
            Codec.STRING.optionalFieldOf("unlocker_id", "").forGetter(CardIdentity::unlockerId),
            Codec.STRING.optionalFieldOf("unlocker", "").forGetter(CardIdentity::unlockerName),
            Codec.LONG.optionalFieldOf("unlocked_at", 0L).forGetter(CardIdentity::unlockedAt),
            Codec.STRING.optionalFieldOf("edition", CardEdition.STANDARD.name())
                    .forGetter(CardIdentity::edition),
            Codec.BOOL.optionalFieldOf("migrated", false).forGetter(CardIdentity::migrated)
    ).apply(i, CardIdentity::new));

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withLocale(Locale.UK);

    public CardEdition editionType() {
        return CardEdition.byName(edition);
    }

    /** "CREEPER-000001", padded to {@code digits}. Migrated cards are marked. */
    public String formatSerial(String mobId, int digits) {
        String mob = (mobId == null ? "CARD" : mobId).toUpperCase(Locale.ROOT);
        String num = String.valueOf(Math.max(0, serial));
        while (num.length() < Math.max(1, digits)) {
            num = "0" + num;
        }
        return mob + "-" + (migrated ? "M" : "") + num;
    }

    /** "14 Mar 2026, 21:07", or empty if this card predates timestamps. */
    public String formatDate() {
        if (unlockedAt <= 0L) {
            return "";
        }
        try {
            return DATE.format(Instant.ofEpochMilli(unlockedAt).atZone(ZoneId.systemDefault()));
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** A card is authentic only if it carries a real uid issued by the server. */
    public boolean valid() {
        return uid != null && !uid.isEmpty();
    }
}
