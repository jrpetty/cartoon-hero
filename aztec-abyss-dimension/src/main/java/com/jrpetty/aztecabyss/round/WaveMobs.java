package com.jrpetty.aztecabyss.round;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * The enemy roster for the Abyss. Every wave is a live mix - each individual
 * spawn is rolled independently from the pool of types unlocked so far, so a
 * round is never a single type.
 *
 * Crucially the mix drifts over time: each type carries a difficulty tier that
 * sets whether its share grows or shrinks per round. Easy mobs (zombies, husks)
 * slowly fade as a fraction of the horde while the dangerous types
 * (wither skeletons, vindicators, phantoms) ramp up the longer you survive - so
 * round 3 is a zombie-heavy swarm speckled with skeletons, and round 20 is a
 * churning mix dominated by the nasty stuff.
 */
public final class WaveMobs {

    public enum Weapon { NONE, BOW, CROSSBOW, SWORD }

    /**
     * @param minRound   first round this type can appear
     * @param baseWeight relative spawn weight at its unlock round
     * @param difficulty 1 (easy, share fades over rounds) .. 5 (deadly, share grows fastest)
     */
    public record Spawn(EntityType<? extends Mob> type, int minRound, int baseWeight, int difficulty, Weapon weapon) {
    }

    private static final List<Spawn> TABLE = List.of(
            new Spawn(EntityType.ZOMBIE, 1, 100, 1, Weapon.NONE),
            new Spawn(EntityType.SKELETON, 2, 40, 2, Weapon.BOW),
            new Spawn(EntityType.HUSK, 3, 45, 1, Weapon.NONE),
            new Spawn(EntityType.SPIDER, 4, 32, 2, Weapon.NONE),
            new Spawn(EntityType.CREEPER, 6, 22, 3, Weapon.NONE),
            new Spawn(EntityType.STRAY, 7, 26, 3, Weapon.BOW),
            new Spawn(EntityType.CAVE_SPIDER, 8, 24, 3, Weapon.NONE),
            new Spawn(EntityType.WITCH, 10, 20, 4, Weapon.NONE),
            new Spawn(EntityType.WITHER_SKELETON, 12, 28, 4, Weapon.SWORD),
            new Spawn(EntityType.PILLAGER, 14, 24, 4, Weapon.CROSSBOW),
            new Spawn(EntityType.VINDICATOR, 16, 24, 5, Weapon.SWORD),
            new Spawn(EntityType.PHANTOM, 18, 20, 5, Weapon.NONE)
    );

    /** Per-round multiplicative drift by difficulty tier: <1 fades, >1 grows. */
    private static double ramp(int difficulty) {
        return switch (difficulty) {
            case 1 -> 0.95;  // fades
            case 2 -> 1.02;
            case 3 -> 1.06;
            case 4 -> 1.10;
            default -> 1.14; // grows fastest
        };
    }

    /** A type's live weight at the given round (0 if not yet unlocked). */
    private static double weightAt(Spawn s, int round) {
        if (round < s.minRound()) {
            return 0.0;
        }
        double w = s.baseWeight() * Math.pow(ramp(s.difficulty()), round - s.minRound());
        return Math.max(1.0, w);
    }

    /** Weighted pick among the types unlocked by the given round, using live weights. */
    public static Spawn pick(RandomSource rng, int round) {
        double total = 0.0;
        for (Spawn s : TABLE) {
            total += weightAt(s, round);
        }
        double roll = rng.nextDouble() * total;
        for (Spawn s : TABLE) {
            roll -= weightAt(s, round);
            if (roll < 0) {
                return s;
            }
        }
        return TABLE.get(0);
    }
}
