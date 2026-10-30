package com.masteri.bhe.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Evaporating Rock, what the Hawking Garden's islands are made of: matter leaking away as Hawking radiation. Step
 * on it, or just stay near, and it starts to glow (fading), then a few seconds later it's gone. Linger and the
 * island evaporates out from under you.
 */
public class EvaporatingRockBlock extends Block {
    public static final BooleanProperty FADING = BooleanProperty.create("fading");
    private static final double NEAR = 10;

    public EvaporatingRockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FADING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FADING);
    }

    /** Starts it fading: gone a few seconds later (rockFadeMinSeconds..rockFadeMaxSeconds). */
    private void fade(Level level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(FADING)) return;
        level.setBlock(pos, state.setValue(FADING, true), Block.UPDATE_ALL);
        int min = com.masteri.bhe.config.ReactorConfig.ROCK_FADE_MIN.get() * 20, max = Math.max(min, com.masteri.bhe.config.ReactorConfig.ROCK_FADE_MAX.get() * 20);
        level.scheduleTick(pos, this, min + random.nextInt(max - min + 1));
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof Player player && !player.isCreative() && !player.isSpectator()) fade(level, pos, state, level.random);
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0 && level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, NEAR, p -> !((Player) p).isCreative()) != null) {
            fade(level, pos, state, random);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FADING)) return;
        level.removeBlock(pos, false);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.04);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.6F + random.nextFloat() * 0.4F);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(FADING) && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, 0.03, 0);
        }
    }
}
