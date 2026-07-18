package com.jrpetty.aztecabyss.registry;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

/**
 * Custom sound events for the Abyss. Each maps to an .ogg under
 * {@code assets/aztecabyss/sounds/} declared in {@code sounds.json}. The mod
 * ships with these entries pointed at placeholder/vanilla-style cues; drop your
 * own .ogg files in with the same names to use real audio without any code
 * changes.
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, AztecAbyssConstants.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ROUND_START = register("round_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROUND_CLEAR = register("round_clear");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_DREAD = register("ambient_dread");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT = register("heartbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> RITUAL_PROGRESS = register("ritual_progress");
    public static final DeferredHolder<SoundEvent, SoundEvent> RITUAL_COMPLETE = register("ritual_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_DOWNED = register("player_downed");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_REVIVED = register("player_revived");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () ->
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, name)));
    }

    private ModSounds() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
