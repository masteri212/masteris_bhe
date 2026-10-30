package com.masteri.bhe.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A Light Trap's pad: lights up while someone (or someone's echo) stands on it. The trap core watches them. */
public class LightPadBlock extends Block {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public LightPadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
