package com.masteri.bhe.block;

import com.masteri.bhe.client.StabilizerConsoleScreen;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A stabilizer's own terminal. Use it to open a command line; the commands run on the server against the
 * stabilizer it's built into (see StabilizerTerminal). That's how a cracked lens is recalibrated.
 */
public class StabilizerConsoleBlock extends FacingBlock {
    public static final MapCodec<StabilizerConsoleBlock> TERMINAL_CODEC = simpleCodec(StabilizerConsoleBlock::new);

    public StabilizerConsoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return TERMINAL_CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) StabilizerConsoleScreen.open(pos); // only loaded on the client
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
