package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * What else is out there after dark.
 *
 * <p>The maze at night had exactly one inhabitant and it was unkillable in
 * practice. That is the right shape for a Griever and the wrong shape for a
 * night: with nothing else moving, a night you survive is a night in which
 * nothing happened, and the thirty minutes between the doors sealing and dawn
 * were dead time for anybody who made it back.
 *
 * <p>So the corridors get a population. Deliberately ordinary - zombies,
 * skeletons, spiders - because the point is not another thing that kills you. It
 * is that the maze has a night life you can go out into and come back from, and
 * that going out is now a decision with an upside rather than a mistake.
 *
 * <h2>The upside is the drops</h2>
 *
 * <p>String, bone, arrows, gunpowder and rotten flesh do not exist in this
 * dimension. There are no animals, the floor does not break, and the Box only
 * brings so much. Every one of those comes off something that walks at night, so
 * a Glade that wants bows, beds, fletching or bonemeal has to send somebody out
 * after dark - which is a far better reason to be in the maze at night than
 * "you failed to get back".
 *
 * <h2>Bad nights</h2>
 *
 * <p>Twenty to thirty-five most nights, and once in a while eighty. The rare one
 * matters more than the common one: a night that is usually survivable and
 * occasionally is not means every dusk is a real question, and the Glade starts
 * keeping a wall in good repair for a night that has not happened yet.
 */
public final class MazeNight {

    /** A normal night. */
    private static final int NORMAL_MIN = 20;
    private static final int NORMAL_MAX = 35;
    /** A bad one. */
    private static final int SWARM = 80;
    /** One night in this many is a bad one. */
    private static final int SWARM_ODDS = 9;

    private static final String TAG = "aztecabyss_nightlife";

    private MazeNight() {
    }

    public static boolean isNightlife(Mob mob) {
        return mob.getPersistentData().getBoolean(TAG);
    }

    public static List<Mob> loaded(ServerLevel level) {
        return level.getEntitiesOfClass(Mob.class,
                new AABB(0, MazeData.FLOOR_Y - 12, 0,
                        MazeData.SPAN, MazeData.WALL_TOP_Y + 4, MazeData.SPAN),
                MazeNight::isNightlife);
    }

    /**
     * Dusk. Fills the maze.
     *
     * <p>Seeded off the day so the same night is the same night for everybody,
     * and so a restart at dusk does not roll a second, worse one on top of the
     * first.
     */
    public static void fall(ServerLevel level, int day) {
        RandomSource rng = RandomSource.create(0x9147 + day * 31L);
        boolean swarm = rng.nextInt(SWARM_ODDS) == 0;
        int count = swarm ? SWARM : NORMAL_MIN + rng.nextInt(NORMAL_MAX - NORMAL_MIN + 1);
        double scale = 1.0 + (Griever.dayScale(level) - 1.0) * 0.5;

        int made = 0;
        for (int attempt = 0; attempt < count * 12 && made < count; attempt++) {
            BlockPos spot = corridor(level, rng);
            if (spot == null) {
                continue;
            }
            Mob mob = make(level, rng, swarm);
            if (mob == null) {
                continue;
            }
            mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                    rng.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
            mob.getPersistentData().putBoolean(TAG, true);
            mob.setPersistenceRequired();
            // Mildly scaled, and only half as fast as the Grievers scale. These
            // are the loot, and loot that outgrows the players stops being loot.
            bump(mob, Attributes.MAX_HEALTH, scale);
            bump(mob, Attributes.ATTACK_DAMAGE, scale);
            mob.setHealth(mob.getMaxHealth());
            level.addFreshEntity(mob);
            made++;
        }
        announce(level, swarm, made);
    }

    private static void bump(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                             double scale) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(inst.getBaseValue() * scale);
        }
    }

    /**
     * The mix.
     *
     * <p>Skeletons and spiders are over-represented relative to what a normal
     * night would produce, because they are the two that drop things this
     * dimension cannot otherwise make. A swarm is nearly all zombies - a bad
     * night should feel like one thing arriving in numbers rather than a
     * livelier version of a normal one.
     */
    private static Mob make(ServerLevel level, RandomSource rng, boolean swarm) {
        if (swarm) {
            return rng.nextInt(10) == 0
                    ? EntityType.HUSK.create(level)
                    : EntityType.ZOMBIE.create(level);
        }
        int roll = rng.nextInt(100);
        if (roll < 45) {
            return EntityType.ZOMBIE.create(level);
        }
        if (roll < 70) {
            return EntityType.SKELETON.create(level);
        }
        if (roll < 90) {
            return EntityType.SPIDER.create(level);
        }
        return EntityType.HUSK.create(level);
    }

    /** Somewhere in the corridors with headroom, never in the clearing. */
    private static BlockPos corridor(ServerLevel level, RandomSource rng) {
        int cellX = rng.nextInt(MazeData.GRID - 4) + 2;
        int cellZ = rng.nextInt(MazeData.GRID - 4) + 2;
        if (MazeData.inGlade(cellX, cellZ)) {
            return null;
        }
        BlockPos at = new BlockPos(
                MazeData.corridorMin(cellX) + rng.nextInt(2),
                MazeData.FLOOR_Y + 1,
                MazeData.corridorMin(cellZ) + rng.nextInt(2));
        return level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() ? at : null;
    }

    private static void announce(ServerLevel level, boolean swarm, int made) {
        for (ServerPlayer p : level.players()) {
            if (swarm) {
                p.connection.send(new ClientboundSetTitleTextPacket(
                        Component.literal("§4§lA BAD NIGHT")));
                p.connection.send(new ClientboundSetSubtitleTextPacket(
                        Component.literal("§7Something has come up with the dark")));
                level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_ROAR,
                        SoundSource.HOSTILE, 2.4F, 0.45F);
            } else {
                p.displayClientMessage(Component.literal(
                        "§8Things are moving in the corridors. §7" + made + " of them."), false);
            }
        }
    }

    /**
     * Dawn. Clears the night off.
     *
     * <p>Discarded rather than left to burn, because the lid over the corridors
     * means nothing out there ever sees the sun - so without this the maze would
     * simply accumulate a night's worth of mobs every day forever.
     */
    public static void lift(ServerLevel level) {
        List<Mob> mobs = loaded(level);
        if (mobs.isEmpty()) {
            return;
        }
        for (Mob m : mobs) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    m.getX(), m.getY() + 0.8, m.getZ(), 8, 0.3, 0.5, 0.3, 0.01);
            m.discard();
        }
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(
                    "§8The corridors empty out with the light."), true);
        }
    }
}
