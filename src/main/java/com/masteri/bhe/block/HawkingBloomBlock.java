package com.masteri.bhe.block;

import com.masteri.bhe.entity.HawkingWispEntity;
import com.masteri.bhe.registry.ModEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Hawking Bloom: a little glowing flower of evaporating light, on the Hawking Garden's rock. Hawking Wisps gather
 * where they grow (now and then one forms over a bloom). Picked, it gives Hawking Motes.
 */
public class HawkingBloomBlock extends BushBlock {
    public static final MapCodec<HawkingBloomBlock> CODEC = simpleCodec(HawkingBloomBlock::new);
    private static final VoxelShape SHAPE = box(4, 0, 4, 12, 10, 12);
    private static final int WISPS_NEAR = 5;

    public HawkingBloomBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.move(state.getOffset(level, pos).x, 0, state.getOffset(level, pos).z);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) != 0) return;
        if (level.getEntitiesOfClass(HawkingWispEntity.class, new AABB(pos).inflate(16)).size() >= WISPS_NEAR) return;
        HawkingWispEntity wisp = ModEntities.HAWKING_WISP.get().create(level);
        if (wisp == null) return;
        wisp.moveTo(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, random.nextFloat() * 360, 0);
        wisp.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        level.addFreshEntity(wisp);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 10, 0.2, 0.3, 0.2, 0.05);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.7, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
        }
    }
}
