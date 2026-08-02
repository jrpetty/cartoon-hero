package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.config.AbyssConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Grievers: the things that own the maze after dark.
 *
 * <p>Built on a heavily reworked spider rather than a bespoke entity type. The
 * source mod ships its own entity and renderer; without that code, registering a
 * new entity here would mean inventing a model and a renderer too, and a made-up
 * Griever model is worse than a real spider that has been made genuinely
 * frightening. It is also exactly how this mod already handles its Breakers and
 * Sappers - real entity types, reshaped by attributes, glow and particles.
 *
 * <p>The numbers come straight from the handoff's server config: 60 health
 * against a spider's 16, speed 0.33 against 0.3 - fractionally faster than a
 * sprinting runner, so you cannot simply outrun one in a straight corridor - and
 * 7 damage. All of it is config-driven, because the handoff is blunt that this
 * balance is a guess and the most likely thing to need changing.
 */
public final class Griever {

    private static final String TAG = "aztecabyss_griever";

    private Griever() {
    }

    public static boolean isGriever(Mob mob) {
        return mob.getPersistentData().getBoolean(TAG);
    }

    /**
     * How many Grievers the maze is allowed right now. Two per runner in week
     * one, climbing by one a week to the ceiling - so a server that keeps a world
     * alive gets steadily less forgiving.
     */
    public static int capFor(ServerLevel level, int runners) {
        int week = (int) (MazeRuntime.dayNumber(level) / 7L);
        int per = Math.min(AbyssConfig.GRIEVER_BASE_CAP.get() + week,
                AbyssConfig.GRIEVER_MAX_CAP.get());
        return Math.max(0, per) * Math.max(1, runners);
    }

    /** Every Griever currently loaded in the maze. */
    public static List<Mob> loaded(ServerLevel level) {
        return level.getEntitiesOfClass(Mob.class,
                new AABB(0, MazeData.FLOOR_Y - 4, 0, MazeData.SPAN, MazeData.WALL_TOP_Y + 4, MazeData.SPAN),
                Griever::isGriever);
    }

    /**
     * Spawns one in a corridor near a runner but out of sight - far enough that
     * it has to be heard coming, close enough that it will actually find them.
     */
    public static void spawnNear(ServerLevel level, ServerPlayer target, RandomSource rng) {
        BlockPos spot = findCorridor(level, target.blockPosition(), rng);
        if (spot == null) {
            return;
        }
        Spider mob = EntityType.SPIDER.create(level);
        if (mob == null) {
            return;
        }
        mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, rng.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        dress(level, mob);
        mob.setTarget(target);
        level.addFreshEntity(mob);

        level.playSound(null, spot, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.4F, 0.55F);
    }

    /** Turns a spider into a Griever. */
    private static void dress(ServerLevel level, Mob mob) {
        mob.getPersistentData().putBoolean(TAG, true);
        mob.setPersistenceRequired();
        mob.setCustomName(Component.literal("§4§lGRIEVER"));
        mob.setCustomNameVisible(false);

        set(mob, Attributes.MAX_HEALTH, AbyssConfig.GRIEVER_HEALTH.get());
        set(mob, Attributes.MOVEMENT_SPEED, AbyssConfig.GRIEVER_SPEED.get());
        set(mob, Attributes.ATTACK_DAMAGE, AbyssConfig.GRIEVER_DAMAGE.get());
        set(mob, Attributes.KNOCKBACK_RESISTANCE, 0.7);
        // It must be able to cross the map to reach you; a corridor maze is no
        // place for a mob that loses interest after sixteen blocks.
        set(mob, Attributes.FOLLOW_RANGE, 96.0);
        // Half again as big as it was, and getting on for three times a spider.
        // Size is the only part of "scarier" that survives having no custom model,
        // and it is the part that does the most work: a thing that fills most of a
        // four-wide corridor is read as an obstacle before it is read as an enemy.
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(2.4);
        }
        // It does not stagger and it does not drown, and it steps up a full block
        // without slowing - a chase that ends because the thing chasing you caught
        // on a corner is not a chase.
        set(mob, Attributes.STEP_HEIGHT, 1.5);
        mob.setHealth(mob.getMaxHealth());

        // Deliberately not glowing. It used to carry permanent Glowing so it read
        // as a countdown rather than a surprise - but an outline drawn through
        // solid stone tells you exactly where it is and which way it is going,
        // and a maze whose monster you can track through the walls is a map, not
        // a maze. You find it now the way it finds you: by sound.
        tint(level, mob);
    }

    private static void set(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                            double value) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    /** Puts the glow on a dark-red team so it reads as a Griever at a glance. */
    private static void tint(ServerLevel level, Mob mob) {
        var board = level.getScoreboard();
        var team = board.getPlayerTeam("aztecabyss_griever");
        if (team == null) {
            team = board.addPlayerTeam("aztecabyss_griever");
            team.setColor(ChatFormatting.DARK_RED);
        }
        board.addPlayerToTeam(mob.getStringUUID(), team);
    }

    /** Cleans a dead Griever off the colour team so the scoreboard stays tidy. */
    public static void onDeath(ServerLevel level, Mob mob) {
        level.getScoreboard().removePlayerFromTeam(mob.getStringUUID());
    }

    /**
     * The Glade is the one place they cannot follow you.
     *
     * <p>The clearing only works as safety if it is unconditional. A Griever that
     * can be lured through a door on a lucky pathfind turns the whole map into the
     * maze, and there is then nowhere to stand and think - which is the thing the
     * Glade exists to give you.
     *
     * <p>Anything that gets in is thrown back out through the nearest wall rather
     * than deleted, because a Griever vanishing at the doorway reads as a bug and a
     * Griever recoiling off the boundary reads as a rule.
     */
    public static void keepOut(ServerLevel level, List<Mob> grievers) {
        for (Mob g : grievers) {
            BlockPos at = g.blockPosition();
            if (!MazeData.inGlade(at.getX() / MazeData.CELL, at.getZ() / MazeData.CELL)) {
                continue;
            }
            int lo = MazeData.gladeMinBlock() - 3;
            int hi = MazeData.gladeMaxBlock() + 4;
            // Out by whichever side it is nearest, so it leaves the way it came.
            int dxLo = at.getX() - MazeData.gladeMinBlock();
            int dxHi = MazeData.gladeMaxBlock() - at.getX();
            int dzLo = at.getZ() - MazeData.gladeMinBlock();
            int dzHi = MazeData.gladeMaxBlock() - at.getZ();
            int best = Math.min(Math.min(dxLo, dxHi), Math.min(dzLo, dzHi));
            double x = at.getX();
            double z = at.getZ();
            if (best == dxLo) {
                x = lo;
            } else if (best == dxHi) {
                x = hi;
            } else if (best == dzLo) {
                z = lo;
            } else {
                z = hi;
            }
            g.teleportTo(x + 0.5, MazeData.FLOOR_Y + 1, z + 0.5);
            g.setTarget(null);
            level.playSound(null, g.blockPosition(), SoundEvents.WARDEN_ANGRY,
                    SoundSource.HOSTILE, 1.2F, 0.4F);
        }
    }

    /**
     * Dawn: everything still out there goes home.
     *
     * <p>They used to be deleted where they stood, which is tidy and reads as
     * nothing at all - the night simply stopped having monsters in it. Sending
     * them back to the four corners instead makes dawn an event you can watch
     * happen, and it means a runner caught out at first light sees the thing
     * chasing them turn round and leave.
     */
    public static void recall(ServerLevel level, List<Mob> grievers) {
        if (grievers.isEmpty()) {
            return;
        }
        int lo = 4 * MazeData.CELL;
        int hi = (MazeData.GRID - 5) * MazeData.CELL;
        int[][] dens = {{lo, lo}, {hi, lo}, {lo, hi}, {hi, hi}};
        int i = 0;
        for (Mob g : grievers) {
            int[] den = dens[i++ % dens.length];
            g.setTarget(null);
            g.teleportTo(den[0] + 0.5, MazeData.FLOOR_Y + 1, den[1] + 0.5);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    g.getX(), g.getY() + 1.0, g.getZ(), 20, 0.6, 0.8, 0.6, 0.02);
        }
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(
                    "§7The scraping fades. §8They have gone back into the walls."), false);
        }
    }

    /**
     * Finds a corridor block to drop one into: 24-48 blocks from the runner,
     * never inside the Glade, and only somewhere with actual headroom.
     */
    private static BlockPos findCorridor(ServerLevel level, BlockPos near, RandomSource rng) {
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            int dist = 24 + rng.nextInt(25);
            int x = near.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = near.getZ() + (int) Math.round(Math.sin(angle) * dist);
            if (x < 2 || z < 2 || x >= MazeData.SPAN - 2 || z >= MazeData.SPAN - 2) {
                continue;
            }
            int cellX = x / MazeData.CELL;
            int cellZ = z / MazeData.CELL;
            if (MazeData.inGlade(cellX, cellZ)) {
                continue; // the Glade is the one safe ground
            }
            BlockPos at = new BlockPos(x, MazeData.FLOOR_Y + 1, z);
            if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
                return at;
            }
        }
        return null;
    }

    /**
     * A sparse, directional cue so a Griever announces itself before it arrives.
     * Ridden off the runtime's existing once-a-second pass, so it costs nothing.
     */
    public static void ambience(ServerLevel level, List<Mob> grievers, RandomSource rng) {
        for (Mob g : grievers) {
            // Always the smoke, sometimes the sound. Without a custom model this
            // is what a Griever looks like: something the size of a horse boiling
            // with soot, seen for half a second at the end of a corridor. It reads
            // at distance, which is where the fear has to happen.
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    g.getX(), g.getY() + 0.9, g.getZ(), 6, 0.5, 0.5, 0.5, 0.01);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                    g.getX(), g.getY() + 0.4, g.getZ(), 2, 0.4, 0.2, 0.4, 0.01);
            if (rng.nextInt(6) != 0) {
                continue;
            }
            level.playSound(null, g.blockPosition(),
                    rng.nextBoolean() ? SoundEvents.WARDEN_ANGRY : SoundEvents.WARDEN_EMERGE,
                    SoundSource.HOSTILE, 1.6F, 0.6F + rng.nextFloat() * 0.2F);
        }
    }

}
