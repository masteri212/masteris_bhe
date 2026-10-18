package com.masteri.bhe.reactor;

import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Finds the nearest Reactor Core by scanning block entities in nearby loaded chunks (cheap, no block scan). */
public final class ReactorLocator {
    @Nullable
    public static ReactorCoreBlockEntity findCore(Level level, BlockPos from, int radius) {
        ReactorCoreBlockEntity best = null;
        double bestDist = (double) radius * radius;
        int minX = SectionPos.blockToSectionCoord(from.getX() - radius), maxX = SectionPos.blockToSectionCoord(from.getX() + radius);
        int minZ = SectionPos.blockToSectionCoord(from.getZ() - radius), maxZ = SectionPos.blockToSectionCoord(from.getZ() + radius);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof ReactorCoreBlockEntity core) {
                        double d = be.getBlockPos().distSqr(from);
                        if (d <= bestDist) {
                            bestDist = d;
                            best = core;
                        }
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    public static ReactorCoreBlockEntity coreAt(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof ReactorCoreBlockEntity core ? core : null;
    }

    private ReactorLocator() {}
}
