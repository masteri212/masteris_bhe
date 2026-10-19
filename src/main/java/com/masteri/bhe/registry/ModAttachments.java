package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MasteriBHE.MODID);

    /** On chunks: the dark energy zone has already corrupted this chunk's terrain (see ZoneTerrain). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ZONE_CONVERTED =
            ATTACHMENTS.register("zone_converted", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());

    private ModAttachments() {}
}
