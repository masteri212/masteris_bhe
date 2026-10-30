package com.masteri.bhe.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A stabilizer's Breaker Panel. Use it to throw the breakers over to OVERCHARGED (and back). During a disc stall,
 * all four stabilizers' breakers must be overcharged before the console's jump-start can kick the disc back into
 * spin; the reactor resets them to normal once it does.
 */
public class BreakerPanelBlock extends FacingBlock {
    public static final MapCodec<BreakerPanelBlock> PANEL_CODEC = simpleCodec(BreakerPanelBlock::new);
    public static final BooleanProperty OVERCHARGED = BooleanProperty.create("overcharged");

    public BreakerPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OVERCHARGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return PANEL_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OVERCHARGED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean on = !state.getValue(OVERCHARGED);
            level.setBlock(pos, state.setValue(OVERCHARGED, on), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1F, on ? 0.6F : 0.5F);
            if (on) level.playSound(null, pos, com.masteri.bhe.registry.ModSounds.REACTOR_BREAKER_CHARGE.get(), SoundSource.BLOCKS, 1F, 1F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean isOvercharged(BlockState state) {
        return state.hasProperty(OVERCHARGED) && state.getValue(OVERCHARGED);
    }
}
