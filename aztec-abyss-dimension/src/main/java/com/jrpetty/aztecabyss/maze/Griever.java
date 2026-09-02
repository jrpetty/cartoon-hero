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
     * feels like getting there. A small crew's night also fills slower - see
     * {@link #crewScale}.
     */
    public static int spawnChanceFor(ServerLevel level) {
        int day = (int) Math.max(0, MazeRuntime.dayNumber(level));
        int crew = level.players().size();
        return Math.max(1, 4 - day / 4 + (crew <= 1 ? 2 : crew == 2 ? 1 : 0));
    }

    /**
     * How much of the maze's violence this crew is asked to eat.
     *
     * <p>The maze as built assumed a squad. Every combat number - sixty base
     * health, the bull's 1.8x of that - was tuned for focus fire, and the cap
     * already scales <em>count</em> per runner, so a lone runner met exactly as
     * many Grievers per head as a crew of five and each one took three times as
     * long to kill with nobody to peel it off. Solo was not harder, it was a
     * different, worse game.
     *
     * <p>So the individuals soften for a small crew: solo Grievers carry about
     * three-fifths of the health and pull their blows, a duo's about four-fifths,
     * and from three players the maze is at full strength. Deliberately never
     * below sixty percent - a solo run should still be the hardest way to play
     * this, just no longer a statistical execution. Read at dress time, so a
     * friend logging in mid-night stiffens the next spawn, not the ones already
     * out.
     */
    public static double crewScale(ServerLevel level) {
        int crew = level.players().size();
        return crew <= 1 ? 0.62 : crew == 2 ? 0.8 : 1.0;
    }

    /**
     * The whole maze, floor to lid. Fixed by the map's dimensions.
     *
     * <p>Held rather than rebuilt because {@link #loaded} is asked for twice a
     * second - once by the night pack and once by the day-stalker - and the box
     * it searches has been the same box since the maze was stamped.
     */
    private static final AABB WHOLE_MAZE = new AABB(
            0, MazeData.FLOOR_Y - 4, 0,
            MazeData.SPAN, MazeData.WALL_TOP_Y + 4, MazeData.SPAN);

    /** Every Griever currently loaded in the maze. */
    public static List<Mob> loaded(ServerLevel level) {
        return level.getEntitiesOfClass(Mob.class, WHOLE_MAZE, Griever::isGriever);
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
        perceived(level, mob);
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

    // ------------------------------------------------------------------
    // The Changing
    // ------------------------------------------------------------------

    /** Marks a Griever that used to be a player. */
    public static final String RISEN = "AztecRisen";

    /** The rise, in ticks: three and a half seconds, with a held breath in it. */
    private static final int RISE_TICKS = 70;

    /** Where the crack lands and where the silence starts, inside those ticks. */
    private static final int CRACK_AT = 30;
    private static final int SILENCE_AT = 58;

    /** What a Risen grows into. Fixed, not rolled: this one is a story, not a pack roll. */
    private static final double RISEN_SCALE = 2.6;
    private static final double BORN_SCALE = 0.35;

    /** Risen Grievers still coming up, by entity id, with ticks remaining. */
    private static final java.util.Map<Integer, Integer> RISING = new java.util.HashMap<>();

    /**
     * The fourth sting, paid off.
     *
     * <p>The Changing used to be a status effect that killed you: a number went
     * down, you died, and the most quoted thing about this whole setting never
     * actually happened. What everybody remembers is not that the venom is
     * lethal - it is that the person it took gets up.
     *
     * <p>So they do. The victim's death is untouched: same damage, same record,
     * same trip out of the dimension, same lockout. Nobody controls what stands
     * up, because a player who dies and then gets to play the monster has not
     * lost anything.
     *
     * <h2>Why the rise is animated with the scale attribute</h2>
     *
     * <p>The first version of this froze a full-grown Griever in place for the
     * length of the drama and called it a husk. A statue with particles on it is
     * not a transformation - the transformation IS the size. So what stands up
     * starts at {@link #BORN_SCALE} - something wet and small on the floor where
     * a person just was - and grows into {@link #RISEN_SCALE} over the rise,
     * convulsing as it comes. Every beat of that is visible from down the
     * corridor, which is where your friends are standing.
     *
     * <p>Until the final roar its name is drawn with \u00a7k - the vanilla font's
     * scrambling glyphs - so the tag over the husk reads as static that has not
     * resolved yet. At the roar it snaps to the victim's name. The reveal is the
     * point of the whole feature, so it is timed like one.
     *
     * <p>Deliberately no {@code finalizeSpawn}: vanilla rolls skeleton jockeys
     * there, and a passenger on the husk would turn the worst moment in the maze
     * into a joke. Everything a spawn would set, this sets by hand.
     */
    public static void rise(ServerLevel level, ServerPlayer victim) {
        BlockPos at = victim.blockPosition();
        Spider mob = EntityType.SPIDER.create(level);
        if (mob == null) {
            return;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, victim.getYRot(), 0.0F);
        dress(level, mob);

        String who = victim.getGameProfile().getName();
        mob.getPersistentData().putBoolean(RISEN, true);
        mob.getPersistentData().putString(KIND, "risen");
        mob.getPersistentData().putString("AztecRisenName", who);
        // Scrambled until the roar. Same length as the real name, so the tag
        // does not visibly change width when it resolves.
        mob.setCustomName(Component.literal("\u00a74\u00a7k" + who.toUpperCase(java.util.Locale.ROOT)));
        mob.setCustomNameVisible(true);

        // Fixed stats, not the pack roll dress() made: every Risen is the same
        // monster, because it is the same story every time.
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(AbyssConfig.GRIEVER_HEALTH.get() * dayScale(level) * 1.25
                    * crewScale(level));
        }
        setScale(mob, BORN_SCALE);
        mob.setHealth(mob.getMaxHealth());

        mob.setNoAi(true);
        mob.setInvulnerable(true);
        level.addFreshEntity(mob);
        RISING.put(mob.getId(), RISE_TICKS);

        // The floor gives. Warden emerge under it, a shriek over it, the soul
        // pulled DOWN into the husk - the spiral in tickRisen runs inward first.
        level.playSound(null, at, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.6F, 0.55F);
        level.playSound(null, at, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.8F, 0.7F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 40, 0.5, 0.9, 0.5, 0.05);
        floorBurst(level, mob, 18);

        for (ServerPlayer p : level.players()) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.literal("\u00a74\u00a7lTHE CHANGING")));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    Component.literal("\u00a7f" + who + "\u00a77 is not who they were.")));
            p.displayClientMessage(Component.literal(
                    "\u00a74\u00a7l\u2620 \u00a7f" + who + "\u00a74 has turned. \u00a77Put them down."), false);
        }
    }

    /**
     * Drives the rise. Called every tick, players present.
     *
     * <p>It is scored like a scene, not decorated like a spawn:
     *
     * <ol>
     *   <li><b>Drawing in</b> - the soul spirals inward and down, the floor
     *       spits debris, a heartbeat underneath, quickening.</li>
     *   <li><b>The crack</b> - one groan, a burst, and the light around it dies
     *       for a moment: nearby players take a pulse of Darkness, which is the
     *       warden's own trick and reads as the corridor holding its breath.</li>
     *   <li><b>Growth</b> - the husk swells tick by tick, twitching as it
     *       comes. This is the transformation itself; nothing else on screen
     *       competes with it.</li>
     *   <li><b>The held breath</b> - the last dozen ticks are silent and
     *       still. No particles, no heartbeat. Horror is rhythm, and the rest
     *       before the roar is what makes the roar.</li>
     *   <li><b>The roar</b> - thunder, the name resolves, the shockwave shoves
     *       everyone standing too close, and it picks its first target.</li>
     * </ol>
     */
    public static void tickRisen(ServerLevel level) {
        if (RISING.isEmpty()) {
            return;
        }
        var it = RISING.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            net.minecraft.world.entity.Entity ent = level.getEntity(e.getKey());
            if (!(ent instanceof Mob mob) || !mob.isAlive()) {
                it.remove();
                continue;
            }
            int left = e.getValue() - 1;
            e.setValue(left);
            int done = RISE_TICKS - left;
            double x = mob.getX();
            double y = mob.getY();
            double z = mob.getZ();

            if (left <= 0) {
                it.remove();
                release(level, mob);
                continue;
            }

            // The size is the story: swell from born to full across the rise,
            // easing in so the growth is felt mid-scene rather than spent early.
            double t = done / (double) RISE_TICKS;
            setScale(mob, BORN_SCALE + Math.pow(t, 1.6) * (RISEN_SCALE - BORN_SCALE));

            if (done >= SILENCE_AT) {
                // The held breath. Nothing. The next thing anybody hears is it.
                continue;
            }

            // Convulsing, not idling: the body wrenches a few degrees a tick.
            float jerk = (float) ((level.random.nextDouble() - 0.5) * 24.0);
            mob.setYRot(mob.getYRot() + jerk);
            mob.yBodyRot = mob.getYRot();

            // The soul spirals inward and down - being pulled in, not leaking out.
            double spiral = 2.2 * (1.0 - t) + 0.3;
            for (int i = 0; i < 2; i++) {
                double a = done * 0.55 + i * Math.PI;
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                        x + Math.cos(a) * spiral, y + 2.2 - t * 1.6, z + Math.sin(a) * spiral,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
            if (done % 5 == 0) {
                floorBurst(level, mob, 3);
            }

            // The heartbeat quickens: every 12 ticks at first, every 4 near the
            // end, pitch climbing with it.
            int beat = Math.max(4, 13 - done / 6);
            if (done % beat == 0) {
                level.playSound(null, mob.blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.HOSTILE, 1.5F, 0.45F + (float) t * 0.5F);
            }
            // Something structural giving way, every so often.
            if (done % 9 == 4) {
                level.playSound(null, mob.blockPosition(), SoundEvents.TURTLE_EGG_CRACK,
                        SoundSource.HOSTILE, 1.2F, 0.55F);
            }

            if (done == CRACK_AT) {
                level.playSound(null, mob.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                        SoundSource.HOSTILE, 1.6F, 0.55F);
                level.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_STUNNED,
                        SoundSource.HOSTILE, 1.2F, 0.6F);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                        x, y + 1.2, z, 50, 0.5, 0.8, 0.5, 0.15);
                floorBurst(level, mob, 14);
                // The light around it dies for a moment - the warden's trick.
                darknessPulse(level, mob, 60);
            }
        }
    }

    /** The roar: the name resolves, the corridor is shoved, and it goes to work. */
    private static void release(ServerLevel level, Mob mob) {
        setScale(mob, RISEN_SCALE);
        mob.setNoAi(false);
        mob.setInvulnerable(false);

        String who = mob.getPersistentData().getString("AztecRisenName");
        if (!who.isEmpty()) {
            mob.setCustomName(Component.literal(
                    "\u00a74\u00a7l" + who.toUpperCase(java.util.Locale.ROOT)));
        }

        BlockPos at = mob.blockPosition();
        level.playSound(null, at, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.0F, 0.45F);
        level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.2F, 1.4F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_CHARGE_POP,
                mob.getX(), mob.getY() + 1.0, mob.getZ(), 90, 0.9, 0.9, 0.9, 0.3);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                mob.getX(), mob.getY() + 0.4, mob.getZ(), 50, 1.0, 0.5, 1.0, 0.05);
        floorBurst(level, mob, 24);

        // The roar has force: anyone stood over the husk is put on their heels.
        for (ServerPlayer p : level.players()) {
            double d2 = p.distanceToSqr(mob);
            if (d2 > 36.0 || d2 < 0.01) {
                continue;
            }
            double dx = p.getX() - mob.getX();
            double dz = p.getZ() - mob.getZ();
            double len = Math.max(0.35, Math.sqrt(dx * dx + dz * dz));
            p.setDeltaMovement(dx / len * 0.7, 0.35, dz / len * 0.7);
            p.hurtMarked = true;
        }

        ServerPlayer near = nearestTo(level, mob);
        if (near != null) {
            mob.setTarget(near);
            perceived(level, mob);
        }
    }

    /**
     * Restart insurance. The rise lives in a static map, so a server that stops
     * mid-scene reloads the mob with no AI, invulnerable, and nobody driving it
     * - a statue that can never be finished or killed. Any Risen that is frozen
     * but not being risen gets released on the spot. Called from the Griever
     * sweep with the list it already has, so it costs nothing extra.
     */
    public static void releaseOrphans(ServerLevel level, List<Mob> loaded) {
        for (Mob g : loaded) {
            if (g.getPersistentData().getBoolean(RISEN) && g.isNoAi()
                    && !RISING.containsKey(g.getId())) {
                release(level, g);
            }
        }
    }

    /**
     * What a Risen sounds like between fights: a person, faintly, now and then.
     *
     * <p>One quiet player-hurt sound roughly every ten seconds per Risen. It is
     * the cheapest line in this whole feature and it is the one that gets
     * clipped: the thing hunting you down the corridor occasionally sounds like
     * who it used to be.
     */
    public static void hauntRisen(ServerLevel level, List<Mob> loaded) {
        for (Mob g : loaded) {
            if (g.getPersistentData().getBoolean(RISEN) && !g.isNoAi()
                    && level.random.nextInt(10) == 0) {
                level.playSound(null, g.blockPosition(), SoundEvents.PLAYER_HURT,
                        SoundSource.HOSTILE, 0.35F, 0.75F);
            }
        }
    }

    /** Clears the rising list. Called when a game ends. */
    public static void clearRising() {
        RISING.clear();
    }

    private static void setScale(Mob mob, double value) {
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(value);
        }
    }

    /** Stone-brick debris kicked out of the floor - clawing, not materialising. */
    private static void floorBurst(ServerLevel level, Mob mob, int count) {
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState()),
                mob.getX(), mob.getY() + 0.2, mob.getZ(), count, 0.7, 0.15, 0.7, 0.12);
    }

    /** A beat of Darkness for everyone close enough to wish they were not. */
    private static void darknessPulse(ServerLevel level, Mob mob, int ticks) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mob) <= 20.0 * 20.0) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DARKNESS, ticks, 0, false, false));
            }
        }
    }

    private static ServerPlayer nearestTo(ServerLevel level, Mob mob) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            double d = p.distanceToSqr(mob);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** PersistentData key for which kind of Griever this one is. */
    public static final String KIND = "AztecGrieverKind";

    /** Turns a spider into a Griever. */
    private static void dress(ServerLevel level, Mob mob) {
        mob.getPersistentData().putBoolean(TAG, true);
        mob.setPersistenceRequired();
        mob.setCustomName(Component.literal("§4§lGRIEVER"));
        mob.setCustomNameVisible(false);

        // One pack, three silhouettes. Half are the standard; a quarter are
        // skitterers - fast, fragile, small enough to feel like vermin; a
        // quarter are bulls - slow, armoured, filling the corridor wall to
        // wall. The tunings matter less than the sounds: in a maze you hear
        // before you see, and hearing WHICH one is coming is information.
        int roll = level.random.nextInt(4);
        String kind = roll == 0 ? "skitterer" : roll == 1 ? "bull" : "griever";
        mob.getPersistentData().putString(KIND, kind);
        double healthMul = kind.equals("skitterer") ? 0.55 : kind.equals("bull") ? 1.8 : 1.0;
        double speedMul = kind.equals("skitterer") ? 1.30 : kind.equals("bull") ? 0.78 : 1.0;
        double damageMul = kind.equals("skitterer") ? 0.7 : kind.equals("bull") ? 1.5 : 1.0;
        double size = kind.equals("skitterer") ? 1.6 : kind.equals("bull") ? 3.0 : 2.4;

        // Scaled to the day it was born on. Speed climbs at a quarter of the
        // rate and stops at a quarter over baseline: a Griever that outruns a
        // sprinting runner by a wide margin is not harder, it is unplayable.
        double hard = dayScale(level);
        // The crew multiplier softens health fully and damage by half as much:
        // a solo kill should come sooner, but a hit that stopped hurting would
        // unteach the sting count, which is the mechanic that matters.
        double crew = crewScale(level);
        set(mob, Attributes.MAX_HEALTH, AbyssConfig.GRIEVER_HEALTH.get() * hard * healthMul * crew);
        set(mob, Attributes.MOVEMENT_SPEED,
                AbyssConfig.GRIEVER_SPEED.get() * Math.min(1.25, 1.0 + (hard - 1.0) * 0.25)
                        * speedMul);
        set(mob, Attributes.ATTACK_DAMAGE, AbyssConfig.GRIEVER_DAMAGE.get() * hard * damageMul
                * (0.5 + 0.5 * crew));
        set(mob, Attributes.KNOCKBACK_RESISTANCE, kind.equals("bull") ? 1.0 : 0.7);
        // It must be able to cross the map to reach you; a corridor maze is no
        // place for a mob that loses interest after sixteen blocks.
        set(mob, Attributes.FOLLOW_RANGE, 96.0);
        // Half again as big as it was, and getting on for three times a spider.
        // Size is the only part of "scarier" that survives having no custom model,
        // and it is the part that does the most work: a thing that fills most of a
        // four-wide corridor is read as an obstacle before it is read as an enemy.
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(size);
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
            // Venom in it: it could not follow a marching band right now.
            if (MazeVenom.blinded(g)) {
                continue;
            }
            // Something already has its attention. Noise finds the unoccupied -
            // but noise from the one it is hunting keeps the trail warm through
            // whatever walls are between them; see shepherd().
            if (g.getTarget() != null && g.getTarget().isAlive()) {
                if (g.getTarget() instanceof ServerPlayer tp) {
                    double radius = noiseRadius(tp);
                    if (radius > 0.0 && g.distanceToSqr(tp) <= radius * radius) {
                        perceived(level, g);
                    }
                }
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
                perceived(level, g);
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

    // ------------------------------------------------------------------
    // Sight, memory, and going home
    // ------------------------------------------------------------------

    /** When one last saw or heard the thing it is hunting. */
    private static final String PERCEIVED = "AztecPerceived";

    /** How long it hunts a cold trail: ten seconds, then the lock breaks. */
    private static final int PERCEPTION_MEMORY = 200;

    /** Close enough to the mouth to count as home. It prowls from here. */
    private static final double LURK_RANGE_SQ = 7.0 * 7.0;

    /** Stamps "it can perceive its target right now". */
    private static void perceived(ServerLevel level, Mob g) {
        g.getPersistentData().putLong(PERCEIVED, level.getGameTime());
    }

    /**
     * The pass that gives a Griever object permanence instead of omniscience,
     * and somewhere to be when it has nobody.
     *
     * <p>Two long-standing wrongs, one loop. First: a target, once set, was
     * forever. The forced {@code setTarget} calls here (spawn, noise, the
     * roar) bypass vanilla's target goals, and nothing ever invalidated them -
     * so a Griever that heard you once would pathfind at you through every
     * wall on the map until one of you died. A monster that cannot be shaken
     * is not hunting you, it is billing you. Now the lock is perception:
     * line of sight refreshes it, noise within earshot refreshes it (that is
     * {@code hear}'s job), and {@link #PERCEPTION_MEMORY} quiet, unseen ticks
     * break it. Round a corner, go quiet, and you have actually escaped -
     * which makes the corner worth something. Vanilla's own acquisition
     * already requires line of sight, so nothing re-locks through the wall.
     *
     * <p>Second: a Griever with no target stood exactly where its last chase
     * ended, forever - the night scattered them randomly across the map and
     * called it distribution. Now an idle one walks back to the nearest hole
     * and prowls its mouth. The holes stop being spawn trivia and become the
     * geography of the night: corridors near one stay dangerous at all hours
     * because that is where the unemployed wait, and a route that threads
     * between four of them is a bad route for a reason you can learn.
     *
     * <p>Raiders are exempt (breaking into the Glade is their whole job) and
     * so is anything with no AI (a Risen mid-scene is driven by its own
     * clock). Venom breaks the lock too: blinding a thing mid-chase now sends
     * it home, which is the counter-play the venom always implied.
     */
    public static void shepherd(ServerLevel level, List<Mob> grievers) {
        long now = level.getGameTime();
        for (Mob g : grievers) {
            if (g.isNoAi() || g.getPersistentData().getBoolean(MazeRaid.TAG)) {
                continue;
            }
            net.minecraft.world.entity.LivingEntity target = g.getTarget();
            if (target != null && target.isAlive() && !MazeVenom.blinded(g)
                    && !inGlade(target)) {
                if (g.getSensing().hasLineOfSight(target)) {
                    perceived(level, g);
                    continue;
                }
                if (now - g.getPersistentData().getLong(PERCEIVED) <= PERCEPTION_MEMORY) {
                    continue; // the trail is still warm; it keeps coming
                }
            }
            if (target != null) {
                g.setTarget(null);
                if (target.isAlive()) {
                    // The tell that you shook it: one dry rasp back down the
                    // corridor, and then the scraping gets quieter, not louder.
                    level.playSound(null, g.blockPosition(), SoundEvents.SPIDER_AMBIENT,
                            SoundSource.HOSTILE, 1.1F, 0.4F);
                }
            }
            // Nobody to hunt: back to the nearest hole. Aimed at solid ground
            // beside the mouth, not the mouth itself - walking into the shaft
            // would drop it into the chamber under the floor, outside every
            // sweep that is supposed to find it.
            BlockPos den = GrieverHoles.nearest(g.blockPosition());
            if (g.blockPosition().distSqr(den) <= LURK_RANGE_SQ) {
                continue; // home; vanilla wander prowls the mouth from here
            }
            if (g.getNavigation().isDone()) {
                BlockPos foot = GrieverHoles.mouthSpawn(level, den);
                if (foot != null) {
                    g.getNavigation().moveTo(
                            foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5, 1.0);
                }
            }
        }
    }

    /** Whether something is standing on the one ground Grievers never hunt. */
    private static boolean inGlade(net.minecraft.world.entity.LivingEntity who) {
        BlockPos at = who.blockPosition();
        return MazeData.inGlade(at.getX() / MazeData.CELL, at.getZ() / MazeData.CELL);
    }

    /**
     * A sparse, directional cue so a Griever announces itself before it arrives.
     * Ridden off the runtime's existing once-a-second pass, so it costs nothing.
     */
    // ------------------------------------------------------------------
    // The day-stalker
    // ------------------------------------------------------------------

    /** PersistentData tag marking the one Griever that walks by day. */
    public static final String STALKER = "AztecStalker";

    /** Ticks of silence before a blind hunter loses the thread: six seconds. */
    private static final int STALKER_MEMORY = 120;

    private static int stalkerSpawnedDay = -1;
    private static int stalkerDeadDay = -1;

    /**
     * The day-stalker: one Griever in the corridors while the sun is up.
     *
     * <p>Days used to be threatless - a Runner's only enemy was the clock,
     * which makes a run a route-planning exercise rather than a place you are
     * in. The stalker changes the texture of every daylight step without
     * changing the odds much: it is <em>blind</em>. It acquires you by sound
     * alone - sprint and it turns toward you, walk and it must be close,
     * crouch and you do not exist - and it loses you again after six quiet
     * seconds, because a blind thing chasing silence is just walking.
     *
     * <p>One per day, from the second day, and killing it buys the rest of
     * the day quiet (plus the standing bounty). It does not exist at night -
     * the night has its own population - and the dawn sweep spares it.
     */
    public static void tickStalker(ServerLevel level, MazeClock clock) {
        List<Mob> stalkers = loaded(level);
        stalkers.removeIf(g -> !g.getPersistentData().getBoolean(STALKER));
        if (clock.isNight() || clock.day() < 1 || !AbyssConfig.GRIEVERS_ENABLED.get()) {
            for (Mob s : stalkers) {
                // Dusk: it goes down a hole like the rest of its kind come up.
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                        s.getX(), s.getY() + 0.8, s.getZ(), 20, 0.5, 0.7, 0.5, 0.02);
                s.discard();
            }
            return;
        }
        int day = clock.day();
        if (stalkers.isEmpty()) {
            if (stalkerSpawnedDay == day) {
                // It was here this morning and is not now: somebody killed it.
                // The rest of the day is quiet, and they earned that.
                stalkerDeadDay = day;
            }
            if (stalkerDeadDay == day) {
                return;
            }
            spawnStalker(level, day);
            return;
        }
        Mob stalker = stalkers.get(0);
        for (int i = 1; i < stalkers.size(); i++) {
            stalkers.get(i).discard(); // one per day, however a restart landed
        }
        // The clearing stays safe ground by day too: the stalker is thrown
        // back out if it crosses the line, and drops a target that reaches it.
        keepOut(level, List.of(stalker));
        if (stalker.getTarget() instanceof ServerPlayer fled) {
            BlockPos at = fled.blockPosition();
            if (MazeData.inGlade(at.getX() / MazeData.CELL, at.getZ() / MazeData.CELL)) {
                stalker.setTarget(null);
            }
        }

        // Venom overrides its hearing too - the one counter the stalker has.
        if (MazeVenom.blinded(stalker)) {
            stalker.setTarget(null);
            return;
        }

        // Blind: the only sense is this loop. Nearest noise wins.
        ServerPlayer heard = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) {
                continue;
            }
            BlockPos at = p.blockPosition();
            if (MazeData.inGlade(at.getX() / MazeData.CELL, at.getZ() / MazeData.CELL)) {
                continue;
            }
            double radius = noiseRadius(p);
            double dist = stalker.distanceTo(p);
            if (dist <= radius && dist < best) {
                best = dist;
                heard = p;
            }
        }
        if (heard != null) {
            boolean fresh = stalker.getTarget() != heard;
            stalker.setTarget(heard);
            stalker.getPersistentData().putLong("StalkerHeard", level.getGameTime());
            if (fresh) {
                level.playSound(null, stalker.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                        SoundSource.HOSTILE, 1.8F, 0.8F);
                heard.displayClientMessage(Component.literal(
                        "§8Something turned toward you."), true);
                if (!heard.getPersistentData().getBoolean("aztecabyss_heard_hint")) {
                    // Said once, ever: the mechanic in one line, the first time
                    // it matters instead of in a manual nobody reads.
                    heard.getPersistentData().putBoolean("aztecabyss_heard_hint", true);
                    heard.displayClientMessage(Component.literal(
                            "§8It hunts by sound. §7Walk quietly — or crouch, and you are nothing."),
                            false);
                }
            }
        } else if (stalker.getTarget() != null
                && level.getGameTime() - stalker.getPersistentData().getLong("StalkerHeard")
                        > STALKER_MEMORY) {
            stalker.setTarget(null); // six quiet seconds, and you are lost again
        }
        // Its tell: a low dry rasp, unlike anything the night uses. You learn
        // to stop and listen before a junction, which is the entire feature.
        if (level.random.nextInt(8) == 0) {
            level.playSound(null, stalker.blockPosition(), SoundEvents.SPIDER_AMBIENT,
                    SoundSource.HOSTILE, 1.3F, 0.5F);
        }
    }

    private static void spawnStalker(ServerLevel level, int day) {
        // Out of a hole away from the Glade, like everything else here.
        BlockPos centre = new BlockPos(MazeData.SPAN / 2, MazeData.FLOOR_Y + 1, MazeData.SPAN / 2);
        BlockPos den = GrieverHoles.nearestBeyond(centre, 30);
        BlockPos spot = den == null ? null : GrieverHoles.mouthSpawn(level, den);
        if (spot == null) {
            return; // try again next second
        }
        Spider mob = EntityType.SPIDER.create(level);
        if (mob == null) {
            return;
        }
        mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        dress(level, mob);
        mob.getPersistentData().putBoolean(STALKER, true);
        level.addFreshEntity(mob);
        stalkerSpawnedDay = day;
        level.playSound(null, spot, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.2F, 0.4F);
        if (day == 1) {
            for (ServerPlayer p : level.players()) {
                p.displayClientMessage(Component.literal(
                        "§8⚠ Something walks the maze by day now. §7It is blind. It listens."),
                        false);
            }
        }
    }

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
            // On top of the standard call, each kind adds its own tell - so a
            // listener in the dark knows not just that one is near, but which.
            String kind = g.getPersistentData().getString(KIND);
            if ("skitterer".equals(kind)) {
                level.playSound(null, g.blockPosition(), SoundEvents.SPIDER_AMBIENT,
                        SoundSource.HOSTILE, 1.4F, 1.5F + rng.nextFloat() * 0.3F);
            } else if ("bull".equals(kind)) {
                level.playSound(null, g.blockPosition(), SoundEvents.RAVAGER_AMBIENT,
                        SoundSource.HOSTILE, 1.8F, 0.4F + rng.nextFloat() * 0.1F);
            }
        }
    }

}
