package dev.structint.world;

import com.mojang.serialization.Codec;
import dev.structint.StructuralIntegrityMod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Registration of the chunk data attachment that tracks player-managed structural blocks. */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, StructuralIntegrityMod.MODID);

    public static final Supplier<AttachmentType<ManagedBlocks>> MANAGED_BLOCKS =
            ATTACHMENT_TYPES.register("managed_blocks", () ->
                    AttachmentType.builder(ManagedBlocks::new)
                            .serialize(ManagedBlocks.CODEC)
                            .build());

    /**
     * Transient flag on a {@code FallingBlockEntity} that the structural system spawned. When
     * such an entity lands, the block it places is re-marked as player-managed so collapse piles
     * stay part of the structural system instead of becoming free natural anchors. Not
     * serialized — falling blocks are short-lived and resolve well within a tick or two.
     */
    public static final Supplier<AttachmentType<Boolean>> FROM_COLLAPSE =
            ATTACHMENT_TYPES.register("from_collapse", () ->
                    AttachmentType.builder(() -> Boolean.FALSE)
                            .serialize(Codec.BOOL) // survive save/unload so airborne piles re-mark
                            .build());

    private ModAttachments() {
    }
}
