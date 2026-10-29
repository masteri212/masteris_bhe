package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
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

    /**
     * On players: the Horizon Gate they last went into the Event Horizon through, to come back out at. Only read
     * when set (hasData); kept through death.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GlobalPos>> HORIZON_RETURN =
            ATTACHMENTS.register("horizon_return", () -> AttachmentType.builder(() -> GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO))
                    .serialize(GlobalPos.CODEC).copyOnDeath().build());

    private ModAttachments() {}
}
