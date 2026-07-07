package com.voxelia.mmo.registry;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.skill.MobMastery;
import com.voxelia.mmo.skill.PlayerPrestige;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.PlayerTalents;
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

    public static final Supplier<AttachmentType<MobMastery>> MOB_MASTERY =
        ATTACHMENTS.register("mob_mastery", () ->
            AttachmentType.builder(MobMastery::new)
                .serialize(MobMastery.CODEC)
                .copyOnDeath()
                .build());

    public static final Supplier<AttachmentType<PlayerTalents>> PLAYER_TALENTS =
        ATTACHMENTS.register("player_talents", () ->
            AttachmentType.builder(PlayerTalents::new)
                .serialize(PlayerTalents.CODEC)
                .copyOnDeath()
                .build());

    public static final Supplier<AttachmentType<PlayerPrestige>> PLAYER_PRESTIGE =
        ATTACHMENTS.register("player_prestige", () ->
            AttachmentType.builder(PlayerPrestige::new)
                .serialize(PlayerPrestige.CODEC)
                .copyOnDeath()
                .build());
}
