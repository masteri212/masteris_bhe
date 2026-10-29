package com.masteri.bhe.block;

import com.masteri.bhe.block.entity.HawkingCellBlockEntity;
import com.masteri.bhe.registry.ModBlockEntities;
import com.masteri.bhe.util.Tickers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Makes RF on its own (see HawkingCellBlockEntity). */
public class HawkingCellBlock extends Block implements EntityBlock {
    public HawkingCellBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.2, pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 1.2,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.2, 0, 0.01, 0);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HawkingCellBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : Tickers.of(type, ModBlockEntities.HAWKING_CELL.get(), (l, p, s, cell) -> cell.serverTick());
    }
}
