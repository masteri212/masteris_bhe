package com.masteri.bhe.block;

import com.masteri.bhe.multiblock.ReactorAssembly;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A plain structure block (ring frame, casing, coil...) that can be assembled (see Assemblable). */
public class AssemblyPartBlock extends Block implements Assemblable {
    public AssemblyPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ASSEMBLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASSEMBLED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return Assemblable.renderShape(state);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Assemblable.occlusionShape(state, super.getOcclusionShape(state, level, pos));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) ReactorAssembly.changed(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && Assemblable.isAssembled(state)) ReactorAssembly.changed(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
