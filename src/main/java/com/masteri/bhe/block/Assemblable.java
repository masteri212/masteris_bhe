package com.masteri.bhe.block;

import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block of one of the reactor's structures (the ring, a stabilizer, an axial stabilizer, the lance). Once its
 * structure is complete it's "assembled": the block isn't drawn, and the core draws the whole structure as one model
 * instead (see ReactorAssembly and AssembledStructures). It still collides and can be mined as usual.
 */
public interface Assemblable {
    BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    static boolean isAssembled(BlockState state) {
        return state.hasProperty(ASSEMBLED) && state.getValue(ASSEMBLED);
    }

    /** The block as it looks on its own (for copies of it, such as torn blocks in flight). */
    static BlockState plain(BlockState state) {
        return isAssembled(state) ? state.setValue(ASSEMBLED, false) : state;
    }

    static RenderShape renderShape(BlockState state) {
        return isAssembled(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    /** Assembled, it hides nothing behind it (the model drawn in its place isn't a full cube). */
    static VoxelShape occlusionShape(BlockState state, VoxelShape normal) {
        return isAssembled(state) ? Shapes.empty() : normal;
    }
}
