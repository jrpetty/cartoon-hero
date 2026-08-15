package com.jrpetty.aztecabyss.registry;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.round.RunState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, AztecAbyssConstants.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RunState>> RUN_STATE =
            ATTACHMENT_TYPES.register(
                    "run_state",
                    () -> AttachmentType.builder(RunState::new)
                            .serialize(RunState.CODEC)
                            .copyOnDeath()
                            .build()
            );

    /**
     * Whether this player has held the Bridge to its last round.
     *
     * <p>Kept apart from {@link RunState} rather than added to it: that
     * record's codec is already at {@code RecordCodecBuilder}'s sixteen-field
     * ceiling, and a seventeenth would not compile. A separate attachment is
     * also the honest shape for this - it is not part of a run, it is
     * something true about the player forever after.
     *
     * <p>Serialized, so it survives a restart, and {@code copyOnDeath}, so it
     * survives dying. The whole point of the Bridge's reward is that nothing
     * takes it back off you.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> BRIDGE_HEART =
            ATTACHMENT_TYPES.register(
                    "bridge_heart",
                    () -> AttachmentType.builder(() -> Boolean.FALSE)
                            .serialize(com.mojang.serialization.Codec.BOOL)
                            .copyOnDeath()
                            .build()
            );

    private ModAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
