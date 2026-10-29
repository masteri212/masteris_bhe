package com.masteri.bhe.block;

import com.masteri.bhe.block.entity.GravitonWellBlockEntity;
import com.masteri.bhe.registry.ModBlockEntities;
import com.masteri.bhe.util.Tickers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Found on the Event Horizon's islands: pulls everything near it in. Mined, it gives up a Graviton Core. */
public class GravitonWellBlock extends Block implements EntityBlock {
    public GravitonWellBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GravitonWellBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : Tickers.of(type, ModBlockEntities.GRAVITON_WELL.get(), (l, p, s, well) -> well.serverTick());
    }
}
