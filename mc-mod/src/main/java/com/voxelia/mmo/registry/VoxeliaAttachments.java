package com.voxelia.mmo.registry;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.skill.PlayerSkills;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Registers the per-player skills data attachment. */
public final class VoxeliaAttachments {
    private VoxeliaAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, VoxeliaMMO.MOD_ID);

    public static final Supplier<AttachmentType<PlayerSkills>> PLAYER_SKILLS =
        ATTACHMENTS.register("player_skills", () ->
            AttachmentType.builder(PlayerSkills::new)
                .serialize(PlayerSkills.CODEC)
                .copyOnDeath()
                .build());
}
