package com.masteri.bhe.block;

import com.mojang.serialization.MapCodec;
import com.masteri.bhe.multiblock.ReactorAssembly;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A facing structure block (the stabilizer emitter) that can be assembled (see Assemblable). */
public class AssemblyFacingBlock extends FacingBlock implements Assemblable {
    public static final MapCodec<AssemblyFacingBlock> ASSEMBLY_CODEC = simpleCodec(AssemblyFacingBlock::new);

    public AssemblyFacingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ASSEMBLED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return ASSEMBLY_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ASSEMBLED);
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
