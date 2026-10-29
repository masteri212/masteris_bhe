package com.masteri.bhe.horizon;

import com.masteri.bhe.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A scatter of Mass Shard clusters around a spot: each one grows out of whatever rock face it finds (tops,
 * undersides or sides of an island), so they also hang from the bottoms of islands.
 */
public class MassShardsFeature extends Feature<NoneFeatureConfiguration> {
    private static final int TRIES = 40, SPREAD = 5;

    public MassShardsFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int placed = 0;
        for (int i = 0; i < TRIES; i++) {
            BlockPos pos = origin.offset(random.nextInt(SPREAD * 2 + 1) - SPREAD, random.nextInt(SPREAD * 2 + 1) - SPREAD,
                    random.nextInt(SPREAD * 2 + 1) - SPREAD);
            if (!level.isEmptyBlock(pos)) continue;
            Direction first = Direction.getRandom(random);
            for (int d = 0; d < 6; d++) {
                Direction facing = Direction.from3DDataValue((first.get3DDataValue() + d) % 6);
                BlockPos support = pos.relative(facing.getOpposite());
                if (level.getBlockState(support).isFaceSturdy(level, support, facing)) {
                    level.setBlock(pos, ModBlocks.MASS_SHARD_CLUSTER.get().defaultBlockState()
                            .setValue(AmethystClusterBlock.FACING, facing), Block.UPDATE_CLIENTS);
                    placed++;
                    break;
                }
            }
        }
        return placed > 0;
    }
}
