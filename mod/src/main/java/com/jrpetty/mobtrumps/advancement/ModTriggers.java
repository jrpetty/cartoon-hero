package com.jrpetty.mobtrumps.advancement;

import com.jrpetty.mobtrumps.MobTrumps;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTriggers {

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(BuiltInRegistries.TRIGGER_TYPES, MobTrumps.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, CollectionTrigger> COLLECTION =
            TRIGGERS.register("collection", CollectionTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, DuelWinTrigger> DUEL_WIN =
            TRIGGERS.register("duel_win", DuelWinTrigger::new);

    private ModTriggers() {
    }
}
