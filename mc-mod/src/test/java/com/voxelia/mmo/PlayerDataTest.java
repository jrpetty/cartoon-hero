package com.voxelia.mmo;

import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.PlayerTalents;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import com.voxelia.mmo.skill.Talent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The plain per-player data holders behave correctly (no game runtime needed). */
class PlayerDataTest {

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
}
