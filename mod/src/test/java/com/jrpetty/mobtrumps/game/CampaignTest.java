package com.jrpetty.mobtrumps.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The campaign's promise is that both sides bring sixteen cards, so a mission
 * is never lost to a card-count advantage — the difficulty is in the cards and
 * in how the opponent plays them. A mission that quietly built a deck of
 * fifteen would hand the player a free win and nothing would report it.
 *
 * <p>The other promise is that a mission's deck is fixed: the same sixteen
 * cards every attempt, so a mission can be learned and beaten deliberately
 * rather than rerolled until the draw is kind.
 */
class CampaignTest {

    @Test
    void thereAreTwentyMissionsIndexedOneToTwenty() {
        assertEquals(20, CampaignDecks.count());
        for (int i = 1; i <= 20; i++) {
            CampaignMission mission = CampaignDecks.byIndex(i);
            assertNotNull(mission, "no mission at index " + i);
            assertEquals(i, mission.index(), "mission " + mission.id() + " has the wrong index");
        }
        assertNotNull(CampaignDecks.byIndex(1));
        assertEquals(null, CampaignDecks.byIndex(0));
        assertEquals(null, CampaignDecks.byIndex(21));
    }

    @Test
    void everyMissionHasAUniqueIdThatLooksItselfUp() {
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (CampaignMission mission : CampaignDecks.ALL) {
            assertTrue(ids.add(mission.id()), "duplicate mission id: " + mission.id());
            assertTrue(names.add(mission.name()), "duplicate mission name: " + mission.name());
            assertEquals(mission, CampaignDecks.byId(mission.id()));
            assertTrue(mission.name() != null && !mission.name().isEmpty());
            assertTrue(mission.tagline() != null && !mission.tagline().isEmpty());
        }
    }

    @Test
    void bothSidesAlwaysBringExactlySixteenCards() {
        for (CampaignMission mission : CampaignDecks.ALL) {
            List<MobCard> cpu = CampaignDecks.cpuDeck(mission);
            assertEquals(CampaignDecks.DECK_SIZE, cpu.size(),
                    "mission " + mission.index() + " (" + mission.name()
                            + ") fielded " + cpu.size() + " cards");
            assertEquals(CampaignDecks.DECK_SIZE, mission.deckSize());
            for (MobCard card : cpu) {
                assertNotNull(card, "mission " + mission.index() + " has a null card");
                assertNotNull(MobCards.byId(card.id()),
                        "mission " + mission.index() + " holds an unknown card " + card.id());
            }
        }
    }

    @Test
    void aMissionDeckNeverRepeatsACard() {
        for (CampaignMission mission : CampaignDecks.ALL) {
            Set<String> seen = new HashSet<>();
            for (MobCard card : CampaignDecks.cpuDeck(mission)) {
                assertTrue(seen.add(card.id()),
                        "mission " + mission.index() + " holds two copies of " + card.id());
            }
        }
    }

    @Test
    void aMissionDeckIsTheSameEveryTime() {
        for (CampaignMission mission : CampaignDecks.ALL) {
            List<String> first = ids(CampaignDecks.cpuDeck(mission));
            for (int attempt = 0; attempt < 5; attempt++) {
                assertEquals(first, ids(CampaignDecks.cpuDeck(mission)),
                        "mission " + mission.index() + " built a different deck on attempt "
                                + attempt + " — it is supposed to be learnable");
            }
        }
    }

    private static List<String> ids(List<MobCard> cards) {
        List<String> out = new ArrayList<>();
        for (MobCard card : cards) {
            out.add(card.id());
        }
        return out;
    }

    @Test
    void everyMissionUsesItsWholeAnchorCategory() {
        for (CampaignMission mission : CampaignDecks.ALL) {
            List<String> deck = ids(CampaignDecks.cpuDeck(mission));
            for (String id : MobCategories.members(mission.anchor())) {
                if (MobCards.byId(id) != null) {
                    assertTrue(deck.contains(id),
                            "mission " + mission.index() + " is anchored on "
                                    + mission.anchor() + " but leaves out " + id);
                }
            }
        }
    }

    @Test
    void theOpponentsUpgradeLevelIsWithinWhatACardCanTake() {
        for (CampaignMission mission : CampaignDecks.ALL) {
            assertTrue(mission.cpuLevel() >= 0 && mission.cpuLevel() <= 3,
                    "mission " + mission.index() + " fields level " + mission.cpuLevel());
            assertNotNull(mission.brain());
            assertNotNull(mission.anchor());
        }
    }

    @Test
    void everyTrophyRewardIsARealMob() {
        for (CampaignMission mission : CampaignDecks.ALL) {
            String trophy = mission.trophyMob();
            if (trophy != null && !trophy.isEmpty()) {
                assertNotNull(MobCards.byId(trophy),
                        "mission " + mission.index() + " awards a Trophy for '"
                                + trophy + "', which is not a mob");
            }
        }
    }

    @Test
    void theCampaignGetsHarderRatherThanEasier() {
        // Not strictly monotonic — the ramp is designed, with dips after spikes —
        // but the back half must not be softer than the front half.
        int earlyCounting = 0;
        int lateCounting = 0;
        for (CampaignMission mission : CampaignDecks.ALL) {
            if (mission.counting()) {
                if (mission.index() <= 10) {
                    earlyCounting++;
                } else {
                    lateCounting++;
                }
            }
        }
        assertTrue(lateCounting >= earlyCounting,
                "card counting appears more in the first half (" + earlyCounting
                        + ") than the second (" + lateCounting + ")");
        assertTrue(CampaignDecks.byIndex(20).brain() == Difficulty.HARD,
                "the last mission should be played by the hardest brain");
    }
}
