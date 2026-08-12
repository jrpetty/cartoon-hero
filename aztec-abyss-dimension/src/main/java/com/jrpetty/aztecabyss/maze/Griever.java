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
     * How hard tonight is, as a multiplier on everything.
     *
     * <p>Day twelve used to play exactly like day three. The only thing that
     * moved with the calendar was the Griever cap, and it moved once a <em>week</em>
     * - so a run that lasted a fortnight got two extra spiders and was otherwise
     * the same run. The maze had a day counter and no difficulty curve, which
     * made the number decorative.
     *
     * <p>Now the day is the difficulty. Health, damage, reach and how fast they
     * come all ride on it, and the curve is one config number so a server can
     * make a week survivable or make it a fortnight of hell.
     *
     * <p>Capped, because the point is escalation rather than a wall. At the
     * default twelve percent, day ten is roughly double day one and day twenty
     * hits the ceiling - which is about as long as a game should ever get.
     */
    public static double dayScale(ServerLevel level) {
        int day = (int) Math.max(0, MazeRuntime.dayNumber(level));
        double per = AbyssConfig.MAZE_DAY_SCALING.get() / 100.0;
        return Math.min(3.0, 1.0 + day * per);
    }

    /**
     * How many Grievers the maze is allowed right now.
     *
     * <p>Climbing every other day rather than every week. A week was long enough
     * that most games ended before the cap ever moved.
     */
    public static int capFor(ServerLevel level, int runners) {
        int day = (int) Math.max(0, MazeRuntime.dayNumber(level));
        int per = Math.min(AbyssConfig.GRIEVER_BASE_CAP.get() + day / 2,
                AbyssConfig.GRIEVER_MAX_CAP.get());
        return Math.max(0, per) * Math.max(1, runners);
    }

    /**
     * How often one is allowed to arrive, as a one-in-N chance per second.
     *
     * <p>A flat one-in-three meant the night filled up at the same rate on every
     * day of every game. Later nights fill faster, which is felt long before the
     * cap is reached - the cap is where a night <em>ends up</em>, this is what it
     * feels like getting there.
     */
    public static int spawnChanceFor(ServerLevel level) {
        int day = (int) Math.max(0, MazeRuntime.dayNumber(level));
        return Math.max(1, 4 - day / 4);
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
        // Out of a hole, if there is one within reach. They used to materialise
        // in whatever corridor the dice picked, which meant the map had no
        // geography of danger at all - every corridor was equally likely to
        // produce one, so no corridor was frightening for a reason.
        BlockPos den = GrieverHoles.nearestBeyond(target.blockPosition(), 20);
        BlockPos spot = den != null && den.distSqr(target.blockPosition()) < 96 * 96
                ? GrieverHoles.mouthSpawn(level, den) : null;
        boolean fromHole = spot != null;
        if (!fromHole) {
            spot = findCorridor(level, target.blockPosition(), rng);
        }
        if (spot == null) {
            return;
        }
        if (fromHole) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 40, 0.8, 0.6, 0.8, 0.05);
            level.playSound(null, spot, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.0F, 0.5F);
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

    /**
     * Spawns a raider for the night raid: a full Griever, plus the raid tag
     * that exempts it from being thrown back out of the Glade - on raid
     * nights, getting inside is the whole point.
     */
    public static Mob raiderAt(ServerLevel level, BlockPos spot) {
        Spider mob = EntityType.SPIDER.create(level);
        if (mob == null) {
            return null;
        }
        mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        dress(level, mob);
        mob.getPersistentData().putBoolean(MazeRaid.TAG, true);
        level.addFreshEntity(mob);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 30, 0.7, 0.6, 0.7, 0.04);
        return mob;
    }

    /** Turns a spider into a Griever. */
    private static void dress(ServerLevel level, Mob mob) {
        mob.getPersistentData().putBoolean(TAG, true);
        mob.setPersistenceRequired();
        mob.setCustomName(Component.literal("§4§lGRIEVER"));
        mob.setCustomNameVisible(false);

        // Scaled to the day it was born on. Speed climbs at a quarter of the
        // rate and stops at a quarter over baseline: a Griever that outruns a
        // sprinting runner by a wide margin is not harder, it is unplayable.
        double hard = dayScale(level);
        set(mob, Attributes.MAX_HEALTH, AbyssConfig.GRIEVER_HEALTH.get() * hard);
        set(mob, Attributes.MOVEMENT_SPEED,
                AbyssConfig.GRIEVER_SPEED.get() * Math.min(1.25, 1.0 + (hard - 1.0) * 0.25));
        set(mob, Attributes.ATTACK_DAMAGE, AbyssConfig.GRIEVER_DAMAGE.get() * hard);
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
            // Raiders are the exception to the rule: on a raid night, a Griever
            // that broke through the wall is inside legitimately.
            if (g.getPersistentData().getBoolean(MazeRaid.TAG)) {
                continue;
            }
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
        for (Mob g : grievers) {
            // Down the nearest hole rather than to a map corner. Where they went
            // used to be somewhere nobody would ever stand, which made dawn a
            // thing that happened offscreen; the holes are places players walk
            // past, so dawn is now something you can be standing next to.
            BlockPos den = GrieverHoles.nearest(g.blockPosition());
            g.setTarget(null);
            g.teleportTo(den.getX() + 0.5, den.getY() - 6, den.getZ() + 0.5);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    den.getX() + 0.5, den.getY() + 0.5, den.getZ() + 0.5, 30, 0.7, 0.9, 0.7, 0.03);
            level.playSound(null, den, SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.6F, 0.5F);
        }
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(
                    "§7The scraping fades. §8They have gone back down the holes."), false);
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
     * What they can hear.
     *
     * <p>Movement was free. A runner sprinting flat out through a junction was in
     * exactly as much danger as one edging round a corner, which means a corridor
     * asked nothing of you moment to moment - the only decision in the maze was
     * which way to turn, and the walking between turns was dead input.
     *
     * <p>Now speed is loud. Sprinting carries a long way, walking carries a
     * little, and crouching carries nothing at all, so every corridor is a
     * standing trade between the clock and the thing in the dark. It pairs
     * pointedly with Stride: the Runner's own skill makes them faster and
     * therefore easier to hear, which is the right kind of cost for a perk.
     *
     * <p>Nothing here changes how a Griever sounds. They already announce
     * themselves well; this only changes what makes them turn round.
     */
    public static void hear(ServerLevel level, List<Mob> grievers, List<ServerPlayer> runners) {
        for (Mob g : grievers) {
            // Something already has its attention. Noise finds the unoccupied.
            if (g.getTarget() != null && g.getTarget().isAlive()) {
                continue;
            }
            for (ServerPlayer p : runners) {
                double radius = noiseRadius(p);
                if (radius <= 0.0) {
                    continue;
                }
                if (g.distanceToSqr(p) > radius * radius) {
                    continue;
                }
                g.setTarget(p);
                // Told, because a rule nobody can perceive is not a rule. This is
                // the only feedback that teaches the mechanic, so it is worth the
                // one line of action bar.
                p.displayClientMessage(Component.literal(
                        "§8Something heard you."), true);
                level.playSound(null, g.blockPosition(), SoundEvents.WARDEN_ANGRY,
                        SoundSource.HOSTILE, 1.0F, 0.7F);
                break;
            }
        }
    }

    /**
     * How far a person carries.
     *
     * <p>Crouching is silent rather than merely quiet, because a maze with no
     * way to be safe is a maze with no tactics in it - there has to be a speed
     * at which you cannot be found, or the mechanic is a tax rather than a
     * choice.
     */
    private static double noiseRadius(ServerPlayer p) {
        if (p.isCrouching()) {
            return 0.0;
        }
        if (p.isSprinting()) {
            return 30.0;
        }
        return 9.0;
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
            // Rain takes the one warning this place gives you. Silencing the
            // approach here rather than deafening the player is the same
            // mechanic without a client mod - and it is honest, because on a wet
            // night the thing really is quieter, not your ears.
            if (MazeNight.weather() == MazeNight.Weather.RAIN || rng.nextInt(6) != 0) {
                continue;
            }
            level.playSound(null, g.blockPosition(),
                    rng.nextBoolean() ? SoundEvents.WARDEN_ANGRY : SoundEvents.WARDEN_EMERGE,
                    SoundSource.HOSTILE, 1.6F, 0.6F + rng.nextFloat() * 0.2F);
        }
    }

}
