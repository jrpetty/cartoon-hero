package com.jrpetty.mobtrumps.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Fires when a player's collection progress changes. The advancement passes
 * a required count and whether it must be foils; the player is granted once
 * their collected total reaches that threshold.
 */
public class CollectionTrigger extends SimpleCriterionTrigger<CollectionTrigger.Instance> {

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /**
     * Call after a player's collection changes.
     *
     * <p>Named {@code fire} rather than overloading {@code trigger}: with the
     * inherited {@code trigger(player, predicate)} invisible offline, a newer
     * javac resolved the call below against this method and reported a lambda
     * passed as an int, failing the offline syntax gate on code that compiles.
     */
    public void fire(ServerPlayer player, int collected, int foils) {
        this.trigger(player, instance -> instance.matches(collected, foils));
    }

    public record Instance(Optional<net.minecraft.advancements.critereon.ContextAwarePredicate> player,
                           int count, boolean foil)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                net.minecraft.advancements.critereon.EntityPredicate.ADVANCEMENT_CODEC
                        .optionalFieldOf("player").forGetter(Instance::player),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Instance::count),
                Codec.BOOL.optionalFieldOf("foil", false).forGetter(Instance::foil)
        ).apply(inst, Instance::new));

        public boolean matches(int collected, int foils) {
            return (foil ? foils : collected) >= count;
        }

        @Override
        public Optional<net.minecraft.advancements.critereon.ContextAwarePredicate> player() {
            return player;
        }
    }
}
