package com.masteri.bhe.util;

import com.masteri.bhe.MasteriBHE;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/**
 * Awards the advancements that no vanilla trigger can detect (their only criterion is "done", an impossible
 * trigger): igniting a reactor, saving one from pre-meltdown, being there when one collapses.
 */
public final class Advancements {
    public static final String IGNITION = "main/ignition", CLOSE_CALL = "main/close_call", GROUND_ZERO = "main/ground_zero";

    /** Awards {@code name} to every player within {@code radius} blocks of {@code pos}. */
    public static void awardNearby(ServerLevel level, BlockPos pos, double radius, String name) {
        AdvancementHolder advancement = level.getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, name));
        if (advancement == null) return;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(radius))) {
            player.getAdvancements().award(advancement, "done");
        }
    }

    private Advancements() {}
}
