package com.jrpetty.mobtrumps.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** Fires when a player wins a duel; grants once their win total reaches count. */
public class DuelWinTrigger extends SimpleCriterionTrigger<DuelWinTrigger.Instance> {

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, int wins) {
        this.trigger(player, instance -> wins >= instance.count());
    }

    public record Instance(Optional<ContextAwarePredicate> player, int count)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Instance::count)
        ).apply(inst, Instance::new));

        @Override
        public Optional<ContextAwarePredicate> player() {
            return player;
        }
    }
}
