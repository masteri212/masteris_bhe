package com.masteri.bhe.block;

import com.masteri.bhe.block.entity.BendingLaserBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BendingLaserBlock extends FacingBlock implements EntityBlock {
    public static final MapCodec<BendingLaserBlock> LASER_CODEC = simpleCodec(BendingLaserBlock::new);

    public BendingLaserBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return LASER_CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BendingLaserBlockEntity(pos, state);
    }
}
