package com.jrpetty.mobtrumps.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The card list is load-bearing in a way that is easy to forget: a card's
 * position in {@link MobCards#ALL} is its {@code mob_index}, which is the item
 * model property that decides which picture the card shows. Reorder the list,
 * insert a mob in the middle, and every card after that point silently renders
 * as the wrong mob — the game does not complain, it just draws a Zombie on the
 * Blaze card.
 *
 * <p>{@link MobCards#ORDER_FINGERPRINT} exists to catch that, and this pins it.
 * If a change to the list is deliberate, regenerate the models with
 * {@code tools/genwear.py} and update the fingerprint in the same commit.
 */
class MobCardsTest {

    @Test
    void theOrderIsStillTheOrderTheModelsWereGeneratedFor() {
        assertTrue(MobCards.orderIntact(),
                "the card list has changed (fingerprint " + MobCards.fingerprint()
                        + ", expected " + MobCards.ORDER_FINGERPRINT + ").\n"
                        + "Every card's artwork is chosen by its index in this list, so the "
                        + "item models are now wrong. Rerun tools/genwear.py and update "
                        + "MobCards.ORDER_FINGERPRINT in the same commit.");
    }

    @Test
    void thereAreEightyOneDistinctMobs() {
        assertEquals(81, MobCards.ALL.size());
        Set<String> ids = new HashSet<>();
        for (MobCard card : MobCards.ALL) {
            assertTrue(ids.add(card.id()), "duplicate card id: " + card.id());
        }
    }

    @Test
    void everyCardIsLookUpAbleAndKnowsItsOwnIndex() {
        for (int i = 0; i < MobCards.ALL.size(); i++) {
            MobCard card = MobCards.ALL.get(i);
            assertEquals(i, MobCards.ordinal(card.id()),
                    card.id() + " reports the wrong index");
            assertEquals(card, MobCards.byId(card.id()));
        }
    }

    @Test
    void anUnknownIdIsHandledRatherThanThrowing() {
        assertEquals(null, MobCards.byId("mobtrumps:not_a_mob"));
        assertEquals(null, MobCards.byId(null));
        // an unknown ordinal must not land on a real card's artwork
        int stray = MobCards.ordinal("not_a_mob");
        assertTrue(stray < 0 || stray >= MobCards.ALL.size(),
                "an unknown id resolved to the valid index " + stray);
    }

    @Test
    void everyStatIsOnTheAdvertisedScale() {
        for (MobCard card : MobCards.ALL) {
            assertNotNull(card.displayName(), card.id() + " has no name");
            assertTrue(card.displayName().length() > 0, card.id() + " has an empty name");
            for (Stat stat : Stat.values()) {
                int v = card.stat(stat);
                assertTrue(v >= 0 && v <= 10,
                        card.id() + " has " + stat + " = " + v + ", outside 0-10");
            }
        }
    }

    @Test
    void upgradingNeverBreaksTheScaleAndIsPurelyDerived() {
        for (MobCard card : MobCards.ALL) {
            for (int level = 0; level <= 4; level++) {
                MobCard up = card.upgraded(level);
                assertEquals(card.id(), up.id(), "upgrading changed the mob");
                for (Stat stat : Stat.values()) {
                    int v = up.stat(stat);
                    assertTrue(v >= 0 && v <= 10,
                            card.id() + " at level " + level + " has " + stat + " = " + v);
                }
                // same input, same output, for everyone who ever reaches this level
                assertEquals(up, card.upgraded(level));
            }
        }
    }

    @Test
    void upgradingOnlyEverHelps() {
        for (MobCard card : MobCards.ALL) {
            MobCard up = card.upgraded(1);
            for (Stat stat : Stat.values()) {
                if (stat == Stat.RARITY) {
                    assertEquals(card.rarity(), up.rarity(),
                            card.id() + ": an upgrade moved rarity, which sets the tier");
                } else {
                    assertTrue(up.stat(stat) >= card.stat(stat),
                            card.id() + ": upgrading lowered " + stat);
                }
            }
        }
    }

    @Test
    void everyMobBelongsToExactlyOneCategory() {
        Set<String> placed = new HashSet<>();
        for (Category category : Category.values()) {
            for (String id : MobCategories.members(category)) {
                assertNotNull(MobCards.byId(id),
                        category + " lists '" + id + "', which is not a card");
                assertTrue(placed.add(id), id + " appears in more than one category");
            }
        }
        for (MobCard card : MobCards.ALL) {
            assertTrue(placed.contains(card.id()),
                    card.id() + " is in no category, so it can never complete a set");
        }
    }
}
