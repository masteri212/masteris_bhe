package com.masteri.bhe.entity;

import com.masteri.bhe.horizon.EventHorizon;
import com.masteri.bhe.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Hawking Wisp: a spark of Hawking radiation drifting through the Event Horizon. Harmless, and slowly
 * evaporating: it shrinks and dims over a few minutes, then is gone with nothing left. Caught early it gives more
 * Hawking Motes (the fuel of the Hawking Cell).
 */
public class HawkingWispEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(HawkingWispEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFESPAN = SynchedEntityData.defineId(HawkingWispEntity.class, EntityDataSerializers.INT);
    private static final int MIN_LIFE = 2400, MAX_LIFE = 6000;

    public HawkingWispEntity(EntityType<? extends HawkingWispEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 10, true);
        setNoGravity(true);
        xpReward = 2;
        int min = MIN_LIFE, max = MAX_LIFE;
        if (!level.isClientSide) { // the client just shows what the server synced
            min = com.masteri.bhe.config.ReactorConfig.WISP_LIFE_MIN.get() * 20;
            max = Math.max(min + 1, com.masteri.bhe.config.ReactorConfig.WISP_LIFE_MAX.get() * 20);
        }
        int lifespan = min + random.nextInt(max - min);
        entityData.set(LIFESPAN, lifespan);
        entityData.set(LIFE, lifespan);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 4)
                .add(Attributes.FLYING_SPEED, 0.25)
                .add(Attributes.MOVEMENT_SPEED, 0.15);
    }

    /** Natural spawning: anywhere in the air above the spaghettification line. */
    public static boolean checkSpawn(EntityType<HawkingWispEntity> type, ServerLevelAccessor level, MobSpawnType reason,
                                     BlockPos pos, RandomSource random) {
        if (reason == MobSpawnType.SPAWNER || reason == MobSpawnType.SPAWN_EGG) return true;
        return pos.getY() > EventHorizon.spaghettificationY() + 10 && level.isEmptyBlock(pos);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LIFE, MAX_LIFE);
        builder.define(LIFESPAN, MAX_LIFE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new WaterAvoidingRandomFlyingGoal(this, 1.0));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanFloat(true);
        return navigation;
    }

    /** 1 when new, falling to 0 as it evaporates. */
    public float lifeFraction() {
        int span = entityData.get(LIFESPAN);
        return span <= 0 ? 0 : Math.max(0, entityData.get(LIFE) / (float) span);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            int life = entityData.get(LIFE) - 1;
            entityData.set(LIFE, life);
            if (life <= 0) {
                server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.25, getZ(), 12, 0.2, 0.2, 0.2, 0.08);
                playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1F, 2F);
                discard(); // evaporated: nothing left to drop
            }
        } else if (random.nextFloat() < 0.3F * lifeFraction() + 0.05F) {
            level().addParticle(ParticleTypes.END_ROD, getRandomX(0.6), getY() + random.nextDouble() * 0.5, getRandomZ(0.6),
                    (random.nextDouble() - 0.5) * 0.02, 0.01, (random.nextDouble() - 0.5) * 0.02);
        }
    }

    /** The fresher it is, the more motes: up to two extra on top of the loot table's. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        int extra = Math.round(2 * lifeFraction());
        if (extra > 0) spawnAtLocation(new ItemStack(ModItems.HAWKING_MOTE.get(), extra));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Life", entityData.get(LIFE));
        tag.putInt("Lifespan", entityData.get(LIFESPAN));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Lifespan")) {
            entityData.set(LIFESPAN, tag.getInt("Lifespan"));
            entityData.set(LIFE, tag.getInt("Life"));
        }
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_CLUSTER_BREAK;
    }

    @Override
    public float getVoicePitch() {
        return 1.6F + random.nextFloat() * 0.4F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }
}
