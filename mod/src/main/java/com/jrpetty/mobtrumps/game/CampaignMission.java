package com.jrpetty.mobtrumps.game;

/**
 * One mission in the campaign.
 *
 * <p>The opponent fields a {@link CampaignDecks#DECK_SIZE}-card deck against
 * the same number of the player's own. The deck is the mission's identity:
 * {@code anchorCards} members of its {@code anchor} category — always
 * including the trophy mob, the face of the mission — with the rest brought
 * in from outside the category, from the mission's tier band. Early missions
 * are padded with mild strays; late ones with legendaries.
 *
 * <p>The deck is fixed and seeded on the mission id, so it is the same six
 * cards every attempt and can be learned.
 *
 * @param index       1-20, and the unlock order
 * @param id          stable key used for saving progress and seeding the deck
 * @param name        the mission's title
 * @param tagline     one line of identity, shown on the briefing
 * @param anchor      the themed set the deck is built around
 * @param anchorCards how many of the deck's cards come from the anchor set
 * @param minTier     lowest tier preferred, for anchor picks and padding alike
 * @param maxTier     highest tier preferred, for anchor picks and padding alike
 * @param brain       how the opponent picks its stats
 * @param counting    the opponent tracks the deck and plays the remaining odds
 * @param cpuLevel    holo level the opponent's deck is fielded at, 0-3
 * @param trophyMob   the mob whose Trophy-edition card a first clear awards
 */
public record CampaignMission(int index, String id, String name, String tagline,
                              Category anchor, int anchorCards, Tier minTier, Tier maxTier,
                              Difficulty brain, boolean counting,
                              int cpuLevel, String trophyMob) {

    /**
     * How many of the deck's cards come from the anchor set: the mission's
     * own figure, capped by the deck and by how many mobs the set holds.
     */
    public int anchorCount() {
        return Math.max(1, Math.min(Math.min(anchorCards, deckSize()), MobCategories.size(anchor)));
    }

    /** How many cards the padding has to supply for this mission. */
    public int subsidyCount() {
        return deckSize() - anchorCount();
    }

    /** Always the hand size. Both sides field a full hand; nobody gets extra cards. */
    public int deckSize() {
        return CampaignDecks.DECK_SIZE;
    }

    /** "Hard · counts the deck · Holo II prints" — the opponent, in one line. */
    public String opponentLabel() {
        StringBuilder sb = new StringBuilder(brain.label());
        if (counting) {
            sb.append("  ·  counts the deck");
        }
        if (cpuLevel > 0) {
            sb.append("  ·  Holo ").append("I".repeat(Math.min(3, cpuLevel))).append(" prints");
        }
        return sb.toString();
    }

    /** A 1-5 threat rating, for drawing skulls on the briefing. */
    public int threat() {
        return Math.max(1, Math.min(5, 1 + (index - 1) / 4));
    }
}
