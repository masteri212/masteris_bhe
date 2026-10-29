package com.masteri.bhe.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A block of Neutronium: so dense nothing holds it up. It falls like sand and hits like an anvil. */
public class NeutroniumBlock extends FallingBlock {
    public static final MapCodec<NeutroniumBlock> CODEC = simpleCodec(NeutroniumBlock::new);

    public NeutroniumBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FallingBlock> codec() {
        return CODEC;
    }

    @Override
    protected void falling(FallingBlockEntity entity) {
        entity.setHurtsEntities(4.0F, 80);
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return 0x8FD8FF;
    }
}
