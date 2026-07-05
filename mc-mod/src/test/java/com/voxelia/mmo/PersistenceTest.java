package com.voxelia.mmo;

import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.PlayerTalents;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.Talent;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The data attachments persist through their codecs. This is exactly the path the
 * game uses to save/load per-player progression, so a round-trip here proves the
 * data actually survives a save/quit/rejoin.
 */
class PersistenceTest {

    @BeforeAll
    static void bootstrap() {
        // Idempotent; makes the vanilla serialization machinery available to the codecs.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void playerSkillsRoundTripThroughCodec() {
        PlayerSkills original = new PlayerSkills();
        original.addXp(Skill.MINING, 12345);
        original.addXp(Skill.COMBAT, 6789);
        original.addXp(Skill.FISHING, 42);

        Tag encoded = PlayerSkills.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        PlayerSkills restored = PlayerSkills.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        for (Skill s : Skill.values()) {
            assertEquals(original.getXp(s), restored.getXp(s), "xp for " + s + " must survive the round-trip");
        }
    }

    @Test
    void playerTalentsRoundTripThroughCodec() {
        PlayerTalents original = new PlayerTalents();
        original.setRank(Talent.MINING_EFFICIENCY, 5);
        original.setRank(Talent.FARMING_VITALITY, 3);
        original.setRank(Talent.FISHING_GILLS, 2);

        Tag encoded = PlayerTalents.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        PlayerTalents restored = PlayerTalents.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        for (Talent t : Talent.values()) {
            assertEquals(original.getRank(t), restored.getRank(t), "rank for " + t + " must survive the round-trip");
        }
        assertEquals(5, restored.spentIn(Skill.MINING));
        assertEquals(3, restored.spentIn(Skill.FARMING));
    }
}
