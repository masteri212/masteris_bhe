package com.masteri.bhe.block;

import com.masteri.bhe.block.entity.TrapCoreBlockEntity;
import com.masteri.bhe.registry.ModBlockEntities;
import com.masteri.bhe.util.Tickers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The Light Trap's core (see TrapCoreBlockEntity): drawn as a tiny black hole with light circling it. */
public class TrapCoreBlock extends Block implements EntityBlock {
    public TrapCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED; // the renderer draws it
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 1.5 + random.nextDouble() * 2;
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 0.5 + (random.nextDouble() - 0.5),
                    pos.getZ() + 0.5 + Math.sin(a) * r, -Math.sin(a) * 0.05, 0, Math.cos(a) * 0.05);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrapCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : Tickers.of(type, ModBlockEntities.TRAP_CORE.get(), (l, p, s, core) -> core.serverTick());
    }
}
