package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What a mob <em>does</em>, as opposed to what its numbers are.
 *
 * <h2>The gap this fills</h2>
 *
 * <p>A ruleset could already give a zombie more health, more damage, a helmet
 * and a colour. What it could not do was make it behave differently, and that is
 * the whole difference between two maps and two <em>games</em>: every enemy in
 * every map walked at you in a straight line and hit you, and the only variable
 * was how long that took. The existing {@code role} field is the proof - it sets
 * potion effects and nothing else, so "brute" is a slow zombie and "runner" is a
 * fast one and neither has a tactic.
 *
 * <h2>Ticked, not goals</h2>
 *
 * <p>These are driven off a tick rather than written as {@code Goal} classes
 * injected into a mob's brain. Two reasons, and the second is the real one.
 * Vanilla goal sets differ per entity type, so a goal that works on a zombie can
 * do nothing at all on a spider, and a map author naming a behaviour has every
 * right to expect it on whatever they spawned. And a tick loop is inspectable:
 * it runs where the rest of the engine runs, it can be traced, and it cannot
 * leave a half-registered goal on an entity that outlives the run.
 *
 * <p>This is the same shape the maze's day-stalker already uses, which is the
 * evidence it holds up in play.
 *
 * <h2>The behaviours</h2>
 *
 * <ul>
 *   <li>{@code charger} — closes in bursts, with a wind-up you can hear and see.
 *       Not faster on average; faster <em>suddenly</em>, which is a different
 *       problem to solve.</li>
 *   <li>{@code leaper} — jumps the last few blocks, so cover you are behind
 *       stops being cover.</li>
 *   <li>{@code sniper} — backs off when you close and hurts you from range, so
 *       it has to be chased rather than waited for.</li>
 *   <li>{@code burrower} — goes under a wall it cannot get round, and comes up
 *       behind you. The answer to a map with one good corner.</li>
 *   <li>{@code support} — never attacks; mends and hastes everything near it.
 *       Turns a horde into a target-priority question.</li>
 * </ul>
 */
public final class MobBrains {

    /** Tag on a mob saying which behaviour it carries. */
    public static final String TAG = "aztecabyss_behaviour";

    /** How often the brains run. Five ticks: quick enough to read as reaction. */
    public static final int INTERVAL = 5;

    private MobBrains() {
    }

    /** Marks a freshly spawned mob, if its entry named a behaviour. */
    public static void mark(Mob mob, String behaviour) {
        if (behaviour == null || behaviour.isEmpty()) {
            return;
        }
        mob.getPersistentData().putString(TAG, behaviour.toLowerCase(java.util.Locale.ROOT));
    }

    public static String of(Mob mob) {
        return mob.getPersistentData().getString(TAG);
    }

    /** Which leg of its route a patroller is on. */
    private static final String LEG = "aztecabyss_leg";

    /** Runs every behaviour once. Called on the arena's tick, throttled. */
    public static void tick(ServerLevel level, List<Mob> mobs, EngineArena arena) {
        if (mobs.isEmpty() || level.getGameTime() % INTERVAL != 0L) {
            return;
        }
        long now = level.getGameTime();
        for (Mob mob : mobs) {
            if (!mob.isAlive()) {
                continue;
            }
            String behaviour = of(mob);
            if (behaviour.isEmpty()) {
                continue;
            }
            LivingEntity target = mob.getTarget();
            switch (behaviour) {
                case "charger" -> charger(level, mob, target, now);
                case "leaper" -> leaper(level, mob, target);
                case "sniper" -> sniper(level, mob, target);
                case "burrower" -> burrower(level, mob, target, now);
                case "support" -> support(level, mob, mobs);
                case "patrol" -> patrol(level, mob, target, arena);
                default -> {
                    // A behaviour this engine has never heard of. A map written
                    // for a later one keeps running.
                }
            }
        }
    }

    /**
     * Walks a named route, and stops walking it the moment it sees you.
     *
     * <p>Everything the engine spawns beelines at the nearest player from the
     * instant it exists, which makes every enemy the same enemy and every room
     * the same room. A patroller has somewhere to be, so a map can have a
     * guarded corridor you time rather than a horde you outrun - and, more
     * usefully, it can have quiet that ends.
     *
     * <p>The route is abandoned on sight rather than politely finished, because
     * a guard that walks on past you having noticed is not a guard.
     *
     * <pre>
     *   [Waypoint]
     *   route=east order=1
     * </pre>
     */
    private static void patrol(ServerLevel level, Mob mob, LivingEntity target, EngineArena arena) {
        if (target != null && mob.hasLineOfSight(target) && mob.distanceTo(target) < 16.0) {
            return; // seen you; the route stops mattering
        }
        if (arena == null) {
            return;
        }
        String route = mob.getPersistentData().getString("aztecabyss_route");
        java.util.List<Marker> legs = arena.route(route);
        if (legs.isEmpty()) {
            return;
        }
        int leg = mob.getPersistentData().getInt(LEG) % legs.size();
        net.minecraft.core.BlockPos at = legs.get(leg).pos();
        if (mob.blockPosition().distSqr(at) <= 4.0) {
            mob.getPersistentData().putInt(LEG, (leg + 1) % legs.size());
            return;
        }
        if (mob.getNavigation().isDone() || level.getGameTime() % 40L == 0L) {
            mob.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.9);
        }
    }

    /**
     * Closes the gap in bursts.
     *
     * <p>The wind-up is the point. A mob that is simply fast is a number; a mob
     * that stops, tells you it is about to come, and then comes, is a decision
     * about where to stand. Both the sound and the particles fire on the wind-up
     * rather than the charge, so the warning arrives before the problem.
     */
    private static void charger(ServerLevel level, Mob mob, LivingEntity target, long now) {
        if (target == null) {
            return;
        }
        double dist = mob.distanceTo(target);
        if (dist > 16.0 || dist < 2.0) {
            return;
        }
        // A stable per-mob phase, so a pack of them does not charge in lockstep.
        long phase = Math.floorMod(mob.getId() * 31L, 60L);
        long beat = Math.floorMod(now + phase, 60L);
        if (beat == 0L) {
            level.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR,
                    SoundSource.HOSTILE, 0.7F, 1.4F);
            level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + 1.0, mob.getZ(),
                    10, 0.3, 0.3, 0.3, 0.05);
            return;
        }
        if (beat >= 10L && beat <= 20L) {
            Vec3 to = new Vec3(target.getX() - mob.getX(), 0.0, target.getZ() - mob.getZ());
            if (to.lengthSqr() < 0.01) {
                return;
            }
            Vec3 push = to.normalize().scale(0.55);
            mob.setDeltaMovement(push.x, mob.getDeltaMovement().y, push.z);
            mob.hasImpulse = true;
        }
    }

    /** Jumps the last stretch, so cover stops being cover. */
    private static void leaper(ServerLevel level, Mob mob, LivingEntity target) {
        if (target == null || !mob.onGround()) {
            return;
        }
        double dist = mob.distanceTo(target);
        if (dist < 3.0 || dist > 9.0) {
            return;
        }
        Vec3 to = new Vec3(target.getX() - mob.getX(), 0.0, target.getZ() - mob.getZ());
        if (to.lengthSqr() < 0.01) {
            return;
        }
        Vec3 push = to.normalize().scale(0.62);
        mob.setDeltaMovement(push.x, 0.52, push.z);
        mob.hasImpulse = true;
        level.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_AMBIENT,
                SoundSource.HOSTILE, 0.8F, 1.6F);
    }

    /**
     * Keeps its distance and hurts you from it.
     *
     * <p>Backing away rather than standing and shooting, because a ranged enemy
     * that lets you walk up to it is a melee enemy with extra steps. This one
     * has to be chased, which is the only thing that makes a room's shape
     * matter.
     */
    private static void sniper(ServerLevel level, Mob mob, LivingEntity target) {
        if (target == null) {
            return;
        }
        double dist = mob.distanceTo(target);
        if (dist < 7.0) {
            Vec3 away = new Vec3(mob.getX() - target.getX(), 0.0, mob.getZ() - target.getZ());
            if (away.lengthSqr() > 0.01) {
                Vec3 push = away.normalize().scale(0.28);
                mob.setDeltaMovement(push.x, mob.getDeltaMovement().y, push.z);
                mob.hasImpulse = true;
            }
            return;
        }
        if (dist > 20.0 || !mob.hasLineOfSight(target)) {
            return;
        }
        if (Math.floorMod(mob.getId() * 17L + level.getGameTime(), 40L) != 0L) {
            return;
        }
        target.hurt(level.damageSources().indirectMagic(mob, mob), 3.0F);
        level.sendParticles(ParticleTypes.SMOKE,
                target.getX(), target.getY() + 1.0, target.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
        level.playSound(null, mob.blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE, 0.6F, 1.2F);
    }

    /**
     * Goes under what it cannot get round.
     *
     * <p>Only when it has made no progress for a while, and only to open ground
     * behind the target - so it is the answer to a map with one perfect corner,
     * not a mob that teleports onto your head at random.
     */
    private static void burrower(ServerLevel level, Mob mob, LivingEntity target, long now) {
        if (target == null || Math.floorMod(now + mob.getId() * 13L, 160L) != 0L) {
            return;
        }
        double dist = mob.distanceTo(target);
        if (dist < 6.0 || dist > 40.0 || mob.hasLineOfSight(target)) {
            return;
        }
        Vec3 behind = new Vec3(target.getX() - mob.getX(), 0.0, target.getZ() - mob.getZ());
        if (behind.lengthSqr() < 0.01) {
            return;
        }
        Vec3 spot = target.position().add(behind.normalize().scale(3.0));
        var at = net.minecraft.core.BlockPos.containing(spot);
        if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) {
            return;
        }
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                mob.getX(), mob.getY(), mob.getZ(), 20, 0.4, 0.2, 0.4, 0.02);
        mob.teleportTo(spot.x, at.getY(), spot.z);
        level.playSound(null, at, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 1.0F, 0.7F);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                spot.x, at.getY(), spot.z, 20, 0.4, 0.2, 0.4, 0.02);
    }

    /**
     * Mends and hastens the ones around it, and never swings.
     *
     * <p>The only enemy in the set that is not trying to hurt you, which is what
     * makes it interesting: it turns a horde from a queue into a question about
     * what to shoot first.
     */
    private static void support(ServerLevel level, Mob mob, List<Mob> all) {
        mob.setTarget(null);
        if (Math.floorMod(level.getGameTime() + mob.getId() * 7L, 40L) != 0L) {
            return;
        }
        int helped = 0;
        for (Mob other : all) {
            if (other == mob || !other.isAlive() || other.distanceTo(mob) > 8.0) {
                continue;
            }
            other.heal(2.0F);
            other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, false, false));
            helped++;
            if (helped >= 8) {
                break;
            }
        }
        if (helped > 0) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    mob.getX(), mob.getY() + 1.2, mob.getZ(), 6, 0.6, 0.4, 0.6, 0.01);
        }
    }
}
