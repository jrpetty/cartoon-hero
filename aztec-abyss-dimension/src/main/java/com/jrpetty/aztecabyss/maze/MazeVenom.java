package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.config.AbyssConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Griever Venom: what an escapee brings back out with them.
 *
 * <p>The reward for beating the maze is not a trophy in a chest, it is a
 * <em>verb</em> - a book you take to an anvil and put on whatever you already
 * fight with. Sword, axe, bow, crossbow, trident: the venom does not care what
 * carried it, which is the whole point of it being an enchantment rather than
 * a weapon. Somebody who got out of the maze fights differently forever after,
 * with the gear they chose.
 *
 * <h2>What it does</h2>
 *
 * <p>A hit envenoms: seven damage a second for five seconds, straight through
 * armour, and for the first three of those the thing loses you completely - it
 * cannot hold a target while its blood is burning. That is the fantasy the
 * maze earns you: not a bigger number, but the ability to make something stop
 * hunting you and die on its own time while you leave.
 *
 * <p>Bosses take the venom and shrug off the blindness. A finale that can be
 * switched off with one arrow is not a finale.
 *
 * <p>It refreshes rather than stacks - a second hit resets the clock, it does
 * not double the bite - and it never touches players, so it can never become a
 * way to grief the Glade.
 *
 * <h2>Who gets the kill</h2>
 *
 * <p>The ticks are dealt as indirect magic <em>from the person who applied
 * it</em>, so a Griever that dies of venom pays their bounty, counts on their
 * tally, and reads in chat as their kill. Poison that steals your kills would
 * be a punishment for using it.
 */
public final class MazeVenom {

    private MazeVenom() {
    }

    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(
            Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "griever_venom"));

    /** Stamped on an arrow at the moment it leaves an envenomed bow. */
    private static final String ARROW_TAG = "aztecabyss_venom_arrow";

    /** One envenomed mob. Times are absolute game ticks. */
    private record Bite(long until, long blindUntil, long nextTick, UUID owner) {
    }

    private static final Map<UUID, Bite> BITTEN = new HashMap<>();

    // ------------------------------------------------------------------
    // Reading the enchantment
    // ------------------------------------------------------------------

    /** Whether this stack carries the venom. */
    public static boolean on(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Holder<Enchantment> holder = holder(level);
        return holder != null && EnchantmentHelper.getItemEnchantmentLevel(holder, stack) > 0;
    }

    private static Holder<Enchantment> holder(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(KEY).orElse(null);
    }

    /** The book itself, as handed out for getting out. */
    public static ItemStack book(ServerLevel level) {
        Holder<Enchantment> holder = holder(level);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        if (holder != null) {
            var stored = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                    net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            stored.set(holder, 1);
            book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                    stored.toImmutable());
        }
        book.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("§2§lGriever Venom"));
        book.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        net.minecraft.network.chat.Component.literal("§8Drawn from the thing that hunted you."),
                        net.minecraft.network.chat.Component.literal("§8An anvil, and any weapon you like."))));
        return book;
    }

    // ------------------------------------------------------------------
    // Applying it
    // ------------------------------------------------------------------

    /**
     * An arrow leaving an envenomed bow carries the venom with it.
     *
     * <p>Stamped at the moment of firing rather than read back off the shooter
     * later, because by the time the arrow lands the bow may be in the other
     * hand, in a chest, or on the floor beside its owner's body.
     */
    public static void stampArrow(ServerLevel level, AbstractArrow arrow) {
        if (!(arrow.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }
        if (on(level, shooter.getMainHandItem()) || on(level, shooter.getOffhandItem())) {
            arrow.getPersistentData().putBoolean(ARROW_TAG, true);
        }
    }

    /**
     * A hit landed. Returns true if it envenomed the target.
     *
     * <p>Melee reads the attacker's hand; a shot reads the stamp the arrow has
     * been carrying since it was loosed.
     */
    public static boolean onHit(ServerLevel level, Entity target, Entity direct, Entity cause) {
        if (!(target instanceof Mob mob) || !(cause instanceof ServerPlayer attacker)) {
            return false; // never players, never anything without a person behind it
        }
        // The Fang carries it in the blade itself - it IS a Griever's barb -
        // so it needs no enchantment. Anything else has to be taught.
        ItemStack hand = attacker.getMainHandItem();
        boolean venomous = direct instanceof AbstractArrow arrow
                ? arrow.getPersistentData().getBoolean(ARROW_TAG)
                : on(level, hand)
                        || hand.is(com.jrpetty.aztecabyss.registry.ModItems.GRIEVER_FANG.get());
        if (!venomous) {
            return false;
        }
        bite(level, mob, attacker);
        return true;
    }

    private static void bite(ServerLevel level, Mob mob, ServerPlayer owner) {
        long now = level.getGameTime();
        int seconds = AbyssConfig.MAZE_VENOM_SECONDS.get();
        int blind = AbyssConfig.MAZE_VENOM_BLIND_SECONDS.get();
        // Bosses take the venom and keep their wits. Everything else forgets
        // who it was chasing.
        boolean boss = mob.getPersistentData().getBoolean("aztecabyss_boss");
        BITTEN.put(mob.getUUID(), new Bite(
                now + seconds * 20L,
                boss ? 0L : now + blind * 20L,
                now + 20L,
                owner.getUUID()));
        if (!boss) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blind * 20, 0, false, true));
        }
        level.sendParticles(ParticleTypes.SNEEZE,
                mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(),
                18, 0.4, 0.4, 0.4, 0.02);
        level.playSound(null, mob.blockPosition(), SoundEvents.BEE_STING,
                SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    /** Whether this mob is currently blind drunk on venom and cannot hold a target. */
    public static boolean blinded(Mob mob) {
        Bite bite = BITTEN.get(mob.getUUID());
        return bite != null && mob.level().getGameTime() < bite.blindUntil();
    }

    // ------------------------------------------------------------------
    // Burning it off
    // ------------------------------------------------------------------

    /**
     * Runs the venom. Called twice a second from whichever mode is ticking -
     * often enough that a blinded mob never gets a whole second of clarity,
     * cheap enough that it costs nothing when nothing is bitten.
     */
    public static void tick(ServerLevel level) {
        if (BITTEN.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        float damage = (float) (double) AbyssConfig.MAZE_VENOM_DAMAGE.get();
        Iterator<Map.Entry<UUID, Bite>> it = BITTEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Bite> e = it.next();
            Entity entity = level.getEntity(e.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                it.remove();
                continue;
            }
            Bite bite = e.getValue();
            if (now >= bite.until()) {
                it.remove();
                continue;
            }
            if (now < bite.blindUntil()) {
                // Kept clear every pass rather than once: the arena's retarget
                // sweep and the Grievers' own hearing both hand out targets,
                // and a mob that re-acquires half a second in was never
                // actually lost.
                mob.setTarget(null);
            }
            if (now >= bite.nextTick()) {
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bite.owner());
                // Indirect magic, so armour does not soften it and the kill
                // still belongs to whoever landed the hit.
                mob.hurt(owner != null
                        ? level.damageSources().indirectMagic(owner, owner)
                        : level.damageSources().magic(), damage);
                level.sendParticles(ParticleTypes.SNEEZE,
                        mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                        6, 0.3, 0.3, 0.3, 0.01);
                BITTEN.put(e.getKey(), new Bite(bite.until(), bite.blindUntil(),
                        now + 20L, bite.owner()));
            }
        }
    }

    /** A new game, or a mode shutting down. */
    public static void clearAll() {
        BITTEN.clear();
    }
}
