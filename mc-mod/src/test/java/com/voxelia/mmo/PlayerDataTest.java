package com.voxelia.mmo;

import com.voxelia.mmo.skill.PlayerPrestige;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.PlayerTalents;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import com.voxelia.mmo.skill.Talent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plain per-player data holders behave correctly (no game runtime needed). */
class PlayerDataTest {

    @Test
    void deathPenaltyDropsLevelsNotJustXp() {
        PlayerSkills ps = new PlayerSkills();
        ps.addXp(Skill.MINING, SkillCurve.xpForLevel(20)); // sit exactly at level 20
        assertEquals(20, ps.getLevel(Skill.MINING));

        ps.loseFraction(0.20); // the 20% death penalty

        assertTrue(ps.getXp(Skill.MINING) < SkillCurve.xpForLevel(20), "xp must drop");
        assertTrue(ps.getLevel(Skill.MINING) < 20,
            "losing 20% xp must actually drop the level, not just the bar");
    }

    @Test
    void freshSkillsStartAtLevelOne() {
        PlayerSkills ps = new PlayerSkills();
        for (Skill s : Skill.values()) {
            assertEquals(0, ps.getXp(s));
            assertEquals(1, ps.getLevel(s));
        }
        assertEquals(Skill.values().length, ps.totalLevels());
        assertEquals(1, ps.characterLevel());
    }

    @Test
    void addingXpRaisesLevelPerTheCurve() {
        PlayerSkills ps = new PlayerSkills();
        int need = SkillCurve.xpForLevel(10);
        ps.addXp(Skill.MINING, need);
        assertEquals(need, ps.getXp(Skill.MINING));
        assertEquals(10, ps.getLevel(Skill.MINING));
        assertEquals(Skill.MINING, ps.highest());
    }

    @Test
    void negativeXpIsClampedAndDeathPenaltyReduces() {
        PlayerSkills ps = new PlayerSkills();
        ps.addXp(Skill.COMBAT, -100);
        assertEquals(0, ps.getXp(Skill.COMBAT), "negative gains are ignored");
        ps.addXp(Skill.COMBAT, 1000);
        ps.loseFraction(0.10);
        assertEquals(900, ps.getXp(Skill.COMBAT), "10% death penalty removes 10% of xp");
    }

    @Test
    void talentRanksStoreAndSumPerSkill() {
        PlayerTalents pt = new PlayerTalents();
        for (Talent t : Talent.values()) assertEquals(0, pt.getRank(t));

        pt.setRank(Talent.MINING_EFFICIENCY, 3);
        pt.setRank(Talent.MINING_PROSPECTOR, 2);
        pt.setRank(Talent.COMBAT_BRUTALITY, 4);
        assertEquals(3, pt.getRank(Talent.MINING_EFFICIENCY));
        assertEquals(5, pt.spentIn(Skill.MINING), "3 + 2 spent in Mining");
        assertEquals(4, pt.spentIn(Skill.COMBAT));
        assertEquals(0, pt.spentIn(Skill.FISHING));

        pt.setRank(Talent.MINING_EFFICIENCY, -1);
        assertEquals(0, pt.getRank(Talent.MINING_EFFICIENCY), "ranks never go negative");

        pt.clear();
        assertEquals(0, pt.spentIn(Skill.COMBAT), "clear() wipes all ranks");
    }

    @Test
    void prestigeResetsOnlyThatSkillAndItsTalents() {
        PlayerPrestige pp = new PlayerPrestige();
        assertEquals(0, pp.get(Skill.COMBAT));
        pp.set(Skill.COMBAT, 2);
        pp.set(Skill.MINING, 1);
        assertEquals(2, pp.get(Skill.COMBAT));
        assertEquals(3, pp.total());

        // Prestiging a skill sends it back to level 1 but leaves other skills alone.
        PlayerSkills ps = new PlayerSkills();
        ps.addXp(Skill.COMBAT, SkillCurve.xpForLevel(SkillCurve.MAX_LEVEL));
        ps.addXp(Skill.MINING, 500);
        assertEquals(SkillCurve.MAX_LEVEL, ps.getLevel(Skill.COMBAT));
        ps.resetSkill(Skill.COMBAT);
        assertEquals(0, ps.getXp(Skill.COMBAT));
        assertEquals(1, ps.getLevel(Skill.COMBAT), "prestige drops the skill to level 1");
        assertEquals(500, ps.getXp(Skill.MINING), "other skills are untouched");

        // Only the prestiged skill's talents are refunded.
        PlayerTalents pt = new PlayerTalents();
        pt.setRank(Talent.COMBAT_BRUTALITY, 3);
        pt.setRank(Talent.MINING_EFFICIENCY, 2);
        pt.clearSkill(Skill.COMBAT);
        assertEquals(0, pt.spentIn(Skill.COMBAT), "prestiged skill's talents cleared");
        assertEquals(2, pt.spentIn(Skill.MINING), "other skills' talents kept");
    }
}
