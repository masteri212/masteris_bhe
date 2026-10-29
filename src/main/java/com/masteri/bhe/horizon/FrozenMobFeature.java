package com.masteri.bhe.horizon;

import com.masteri.bhe.MasteriBHE;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.List;

/**
 * A Frozen Mob: something swallowed from the overworld, stuck mid-fall forever in the air near an island. It
 * doesn't move, fall or despawn until it's hit (HorizonEffects thaws it); then it falls toward the singularity.
 */
public class FrozenMobFeature extends Feature<NoneFeatureConfiguration> {
    private static final String FROZEN = MasteriBHE.MODID + ":frozen";
    private static final List<EntityType<?>> KINDS = List.of(
            EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.VILLAGER,
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER, EntityType.ENDERMAN);

    public FrozenMobFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    public static boolean isFrozen(Entity entity) {
        return entity.getPersistentData().getBoolean(FROZEN);
    }

    public static void thaw(LivingEntity living) {
        living.getPersistentData().remove(FROZEN);
        living.setNoGravity(false);
        living.setSilent(false);
        if (living instanceof Mob mob) mob.setNoAi(false);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos pos = context.origin().above(3 + random.nextInt(8));
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above()) || !level.isEmptyBlock(pos.above(2))) return false;
        Entity entity = KINDS.get(random.nextInt(KINDS.size())).create(level.getLevel());
        if (!(entity instanceof Mob mob)) return false;
        // Tumbling: some odd angle, as if caught mid-fall.
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, random.nextFloat() * 60 - 30);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setSilent(true);
        mob.setPersistenceRequired();
        mob.getPersistentData().putBoolean(FROZEN, true);
        level.addFreshEntityWithPassengers(mob);
        return true;
    }
}
