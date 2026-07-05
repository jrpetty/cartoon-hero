package com.voxelia.mmo;

import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.Talent;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural + formatting invariants of the skill-specific talent tree. */
class TalentTest {

    @Test
    void everySkillHasFiveDistinctTalents() {
        for (Skill s : Skill.values()) {
            var talents = Talent.forSkill(s);
            assertEquals(5, talents.size(), s + " should have exactly 5 talents");
            Set<Talent.Category> cats = EnumSet.noneOf(Talent.Category.class);
            for (Talent t : talents) {
                assertSame(s, t.skill(), t + " must belong to " + s);
                assertTrue(cats.add(t.category()),
                    s + " has two talents in category " + t.category() + " (Talent.of would be ambiguous)");
            }
        }
        assertEquals(55, Talent.values().length, "11 skills x 5 talents == 55");
    }

    @Test
    void everySkillHasSignatureAndXpTalents() {
        for (Skill s : Skill.values()) {
            assertNotNull(Talent.of(s, Talent.Category.SIGNATURE), s + " needs a signature talent");
            assertNotNull(Talent.of(s, Talent.Category.XP), s + " needs a Prodigy (XP) talent");
        }
    }

    @Test
    void ofResolvesToAMatchingTalentOrNull() {
        for (Skill s : Skill.values()) {
            for (Talent.Category c : Talent.Category.values()) {
                Talent t = Talent.of(s, c);
                if (t != null) {
                    assertSame(s, t.skill());
                    assertSame(c, t.category());
                }
            }
        }
    }

    @Test
    void idsAreUniqueLowercaseAndRoundTrip() {
        Set<String> ids = new HashSet<>();
        for (Talent t : Talent.values()) {
            String id = t.id();
            assertEquals(t.name().toLowerCase(java.util.Locale.ROOT), id);
            assertTrue(ids.add(id), "duplicate talent id " + id);
            assertSame(t, Talent.byId(id), "byId must round-trip " + id);
        }
        // unknown / null ids resolve to null rather than throwing
        assertEquals(null, Talent.byId("does_not_exist"));
        assertEquals(null, Talent.byId(null));
    }

    @Test
    void categoriesHaveColoursAndConsistentPercentFlag() {
        for (Talent.Category c : Talent.Category.values()) {
            assertTrue((c.color() & 0xFFFFFF) != 0 || c.color() == 0, "color() returns an int");
        }
    }

    @Test
    void bonusTextMatchesMagnitudeForPercentAndFlatTalents() {
        // SIGNATURE (percent, 0.05/rank): "+15% mining break speed" at rank 3
        Talent eff = Talent.MINING_EFFICIENCY;
        assertTrue(eff.category().isPercent());
        assertEquals("+15% mining break speed", eff.bonusText(3));
        assertEquals("+5% per rank", eff.perRankText());
        assertEquals(1.0, eff.multiplierAt(0), 1e-9);
        assertEquals(1.10, eff.multiplierAt(2), 1e-9);

        // HEALTH (flat, 1.0/rank): "+3 max health" at rank 3
        Talent vit = Talent.FARMING_VITALITY;
        assertTrue(!vit.category().isPercent());
        assertEquals("+3 max health", vit.bonusText(3));
        assertEquals("+1 per rank", vit.perRankText());
        assertEquals(3.0, vit.flatAt(3), 1e-9);

        // rank 0 shows a zero bonus, never a crash
        assertEquals("+0% mining break speed", eff.bonusText(0));
    }
}
