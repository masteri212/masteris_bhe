package com.masteri.bhe.research;

import com.masteri.bhe.block.FacingBlock;
import com.masteri.bhe.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Research Chamber: a 3x3x3 cube of Research Casing with the Research Core in the middle of its front face
 * (facing out) and a Containment Box holding a captured black hole in the very center.
 */
public final class ResearchChamber {
    public enum Status { INCOMPLETE, NO_SINGULARITY, READY }

    /** The cube's center (where the Containment Box goes), behind the core. */
    public static BlockPos center(BlockPos core, Direction facing) {
        return core.relative(facing.getOpposite());
    }

    public static Status check(Level level, BlockPos core, Direction facing) {
        BlockPos center = center(core, facing);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (pos.equals(core) || pos.equals(center)) continue;
                    if (!level.getBlockState(pos).is(ModBlocks.RESEARCH_CASING.get())) return Status.INCOMPLETE;
                }
            }
        }
        return level.getBlockState(center).is(ModBlocks.CONTAINMENT_BOX.get()) ? Status.READY : Status.NO_SINGULARITY;
    }

    /** Creative/testing helper: builds the chamber behind a core facing {@code facing}, singularity included. */
    public static int build(Level level, BlockPos core, Direction facing) {
        BlockPos center = center(core, facing);
        int placed = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = pos.equals(core)
                            ? ModBlocks.RESEARCH_CORE.get().defaultBlockState().setValue(FacingBlock.FACING, facing)
                            : pos.equals(center) ? ModBlocks.CONTAINMENT_BOX.get().defaultBlockState()
                            : ModBlocks.RESEARCH_CASING.get().defaultBlockState();
                    if (!level.getBlockState(pos).equals(state)) {
                        level.setBlock(pos, state, Block.UPDATE_ALL);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    private ResearchChamber() {}
}
