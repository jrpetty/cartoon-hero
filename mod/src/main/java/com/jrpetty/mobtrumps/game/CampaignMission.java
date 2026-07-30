package com.jrpetty.mobtrumps.game;

/**
 * One mission in the campaign.
 *
 * <p>A mission is a single deck of {@link CampaignDecks#DECK_SIZE} cards dealt
 * between the player and the opponent — the way Top Trumps actually works. The
 * deck is the mission's identity: every member of its {@code anchor} category,
 * padded out from outside that category with cards in the mission's tier band.
 * Early missions are padded with mild strays; late ones with legendaries.
 *
 * <p>The deck is fixed and seeded on the mission id, so it is the same sixteen
 * cards every attempt and can be learned.
 *
 * @param index      1-20, and the unlock order
 * @param id         stable key used for saving progress and seeding the deck
 * @param name       the mission's title
 * @param tagline    one line of identity, shown on the briefing
 * @param anchor     the themed set, used whole
 * @param minTier    lowest tier the padding may be drawn from
 * @param maxTier    highest tier the padding may be drawn from
 * @param brain      how the opponent picks its stats
 * @param counting   the opponent tracks the deck and plays the remaining odds
 * @param cpuExtra   extra cards dealt to the opponent (an uneven 9/7 deal)
 * @param trophyMob  the mob whose Trophy-edition card a first clear awards
 */
public record CampaignMission(int index, String id, String name, String tagline,
                              Category anchor, Tier minTier, Tier maxTier,
                              Difficulty brain, boolean counting, int cpuExtra,
                              String trophyMob) {

    /** How many cards the padding has to supply for this mission. */
    public int subsidyCount() {
        return Math.max(0, CampaignDecks.DECK_SIZE - MobCategories.size(anchor));
    }

    /** "Hard · counts the deck · +1 card" — the opponent, in one line. */
    public String opponentLabel() {
        StringBuilder sb = new StringBuilder(brain.label());
        if (counting) {
            sb.append("  ·  counts the deck");
        }
        if (cpuExtra > 0) {
            sb.append("  ·  +").append(cpuExtra).append(" card").append(cpuExtra == 1 ? "" : "s");
        }
        return sb.toString();
    }

    /** A 1-5 threat rating, for drawing skulls on the briefing. */
    public int threat() {
        return Math.max(1, Math.min(5, 1 + (index - 1) / 4));
    }
}
