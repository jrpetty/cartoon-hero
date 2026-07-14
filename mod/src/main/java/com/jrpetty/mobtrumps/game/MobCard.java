package com.jrpetty.mobtrumps.game;

/**
 * One collectable mob card. All six stats are on a 0-10 scale.
 * Rarity is spawn likelihood: 10 = everywhere, 1 = legendary.
 */
public record MobCard(String id, String displayName, int health, int attack,
                      int size, int speed, int farmable, int rarity) {

    public int stat(Stat stat) {
        return switch (stat) {
            case HEALTH -> health;
            case ATTACK -> attack;
            case SIZE -> size;
            case SPEED -> speed;
            case FARMABLE -> farmable;
            case RARITY -> rarity;
        };
    }

    /**
     * The holographic version of this card. Earning a card's holo (by killing
     * enough of that mob) grants a fixed, fair combat boost that is identical
     * for every card in the set: +1 Health, +2 Attack, +1 Speed (each capped
     * at 10). Everyone who unlocks a given holo gets exactly the same card.
     */
    public MobCard foilVersion() {
        return new MobCard(id, displayName,
                Math.min(10, health + 1),
                Math.min(10, attack + 2),
                size,
                Math.min(10, speed + 1),
                farmable, rarity);
    }

    /** Collector tier derived from spawn rarity (low rarity = rarer card). */
    public Tier tier() {
        if (rarity <= 2) return Tier.LEGENDARY;
        if (rarity <= 4) return Tier.EPIC;
        if (rarity <= 6) return Tier.RARE;
        if (rarity <= 8) return Tier.UNCOMMON;
        return Tier.COMMON;
    }

    /** The stat this card is strongest in — the CPU leads with it. */
    public Stat bestStat() {
        Stat best = Stat.HEALTH;
        for (Stat s : Stat.values()) {
            if (stat(s) > stat(best)) best = s;
        }
        return best;
    }
}
