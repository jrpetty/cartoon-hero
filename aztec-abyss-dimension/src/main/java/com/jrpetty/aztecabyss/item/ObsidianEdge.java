package com.jrpetty.aztecabyss.item;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The Obsidian Edge: armour is a suggestion.
 *
 * <p>The Aztecs fought with the <em>macuahuitl</em> - a wooden blade set with
 * teeth of knapped obsidian, which takes an edge sharper than surgical steel
 * and opens armour a sword would skate off. That is the whole item, and the
 * whole of what it does: seven tenths of whatever the target is wearing simply
 * does not count.
 *
 * <p>Against a bare zombie it is a heavy club and not much more. Against
 * anything armoured - and against the things the Temple sends at the end - it
 * is the difference between chipping at a wall and getting through it, which
 * is precisely the fight that map asks you to win.
 *
 * <h2>How the armour is taken off</h2>
 *
 * <p>The damage event fires before Minecraft applies armour, so rather than
 * inventing a damage type that bypasses it - which would cost the hit its
 * knockback, its sweep and its enchantments - this works out what armour is
 * <em>about</em> to remove and scales the swing up to cancel most of it. The
 * hit stays an ordinary hit in every other respect; it just lands like the
 * plate was not there.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID)
public final class ObsidianEdge {

    private ObsidianEdge() {
    }

    /** How much of the target's armour is ignored. */
    private static final double IGNORED = 0.7;

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.OBSIDIAN_EDGE.get());
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        // Melee only. An obsidian club is not a thing you fire at people, and
        // reading it off the hand at range would give thrown damage an edge
        // the weapon never touched.
        if (event.getSource().getDirectEntity() != attacker || !is(attacker.getMainHandItem())) {
            return;
        }
        LivingEntity target = event.getEntity();
        double armour = target.getAttributeValue(Attributes.ARMOR);
        if (armour <= 0.0) {
            return; // nothing to cut through; it is just a heavy club today
        }
        double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float amount = event.getAmount();

        // Vanilla's own sum, run ahead of vanilla: how much of this swing the
        // armour is going to eat.
        double f = 2.0 + toughness / 4.0;
        double effective = Mth.clamp(armour - amount / f, armour * 0.2, 20.0);
        double eaten = effective / 25.0;
        if (eaten <= 0.0) {
            return;
        }
        // Scale the swing so that after vanilla takes its bite, what lands is
        // what would have landed against three tenths of that armour. Capped
        // at four fifths by the clamp above, so the divisor is never near zero.
        double wanted = 1.0 - eaten * (1.0 - IGNORED);
        event.setAmount((float) (amount * wanted / (1.0 - eaten)));

        if (attacker.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    6, 0.25, 0.25, 0.25, 0.05);
            level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK,
                    SoundSource.PLAYERS, 0.45F, 1.6F);
        }
    }
}
