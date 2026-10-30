package com.masteri.bhe.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Lensing Stalker: a tall, thin figure that bends light around itself. While any player is looking its way it
 * can't move or strike; the moment nobody is, it closes in fast. Looking straight at it, you barely see it: it shows
 * best at the edge of your vision (that part is the renderer's, per player). Lives in a zone's fallout.
 */
public class LensingStalkerEntity extends Monster {
    /** How directly a player must look its way to hold it still (the cosine of about 35 degrees). */
    private static final double WATCHED_DOT = 0.82;
    private static final double WATCH_RANGE = 64;
    private static final EntityDataAccessor<Boolean> WATCHED =
            SynchedEntityData.defineId(LensingStalkerEntity.class, EntityDataSerializers.BOOLEAN);

    private int lastLurkSound;

    public LensingStalkerEntity(EntityType<? extends LensingStalkerEntity> type, Level level) {
        super(type, level);
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.MOVEMENT_SPEED, 0.42)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WATCHED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** Whether some player is looking its way (synced, so the client can stop its walk too). */
    public boolean isWatched() {
        return entityData.get(WATCHED);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ZoneBound.tick(this);
        if (isRemoved()) return;
        boolean watched = watchedByAnyone();
        if (watched != isWatched()) entityData.set(WATCHED, watched);
        if (watched) {
            getNavigation().stop();
        } else if (getTarget() != null && distanceToSqr(getTarget()) < 12 * 12 && tickCount - lastLurkSound > 80
                && level() instanceof ServerLevel server) {
            lastLurkSound = tickCount;
            server.playSound(null, blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.HOSTILE, 1F, 0.5F);
        }
    }

    private boolean watchedByAnyone() {
        Vec3 middle = position().add(0, getBbHeight() * 0.6, 0);
        for (Player player : level().players()) {
            if (player.isSpectator() || !player.isAlive() || player.distanceToSqr(this) > WATCH_RANGE * WATCH_RANGE) continue;
            Vec3 toMe = middle.subtract(player.getEyePosition()).normalize();
            if (player.getViewVector(1F).dot(toMe) >= WATCHED_DOT && player.hasLineOfSight(this)) return true;
        }
        return false;
    }

    /** Held still while watched: no steps, no turning toward its prey. */
    @Override
    public void travel(Vec3 input) {
        if (isWatched()) {
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            super.travel(Vec3.ZERO);
            return;
        }
        super.travel(input);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isWatched()) return false;
        return super.doHurtTarget(target);
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return isWatched() ? null : SoundEvents.AMETHYST_BLOCK_RESONATE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GLASS_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GLASS_BREAK;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F + random.nextFloat() * 0.2F;
    }
}
