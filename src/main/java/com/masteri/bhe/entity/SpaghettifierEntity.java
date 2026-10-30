package com.masteri.bhe.entity;

import com.masteri.bhe.horizon.EventHorizon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Spaghettifier: a long eel of stretched matter swimming through the Event Horizon's deep. It circles its
 * prey, then lunges; when it bites it grabs on and drags you down toward the singularity until you hit it (or it
 * lets go). Spawns naturally in the debris drift and the tidal depths.
 */
public class SpaghettifierEntity extends Monster {
    private static final int GRAB_TICKS = 40, LUNGE_COOLDOWN = 70;
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(1.0F, 0.5F, 0.2F), 0.9F);

    private int cooldown = 40;
    private int grabTicks;
    @Nullable private LivingEntity grabbed;

    public SpaghettifierEntity(EntityType<? extends SpaghettifierEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 30, true);
        setNoGravity(true);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.FLYING_SPEED, 0.55)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 40);
    }

    /**
     * Natural spawning: in the air of the deep and lower drift, above the spaghettification line. Only one try in
     * four counts, and never more than two already around: they're hunters, not a swarm.
     */
    public static boolean checkSpawn(EntityType<SpaghettifierEntity> type, ServerLevelAccessor level, MobSpawnType reason,
                                     BlockPos pos, RandomSource random) {
        if (reason == MobSpawnType.SPAWNER || reason == MobSpawnType.SPAWN_EGG) return true;
        return level.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(4) == 0
                && pos.getY() > EventHorizon.spaghettificationY() + 6 && pos.getY() < EventHorizon.DEEP_START + 30
                && level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())
                && level.getEntitiesOfClass(SpaghettifierEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(32)).size() < 2;
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    // ------------------------------------------------------------------ hunting

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (grabTicks > 0) {
            drag();
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            Vec3 drift = position().add(Math.sin(tickCount * 0.02 + getId()) * 6, Math.sin(tickCount * 0.013) * 2, Math.cos(tickCount * 0.02 + getId()) * 6);
            getMoveControl().setWantedPosition(drift.x, drift.y, drift.z, 0.6);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            // Circling, a little above, weaving up and down.
            double angle = tickCount * 0.07 + getId();
            Vec3 want = target.position().add(Math.cos(angle) * 7, 2 + Math.sin(tickCount * 0.11) * 2, Math.sin(angle) * 7);
            getMoveControl().setWantedPosition(want.x, want.y, want.z, 1.0);
            return;
        }
        // The lunge.
        Vec3 at = target.getEyePosition();
        getMoveControl().setWantedPosition(at.x, at.y - 0.5, at.z, 2.0);
        if (distanceToSqr(target) < 2.6 * 2.6) {
            if (doHurtTarget(target)) grab(target);
            cooldown = LUNGE_COOLDOWN + random.nextInt(30);
        } else if (cooldown < -80) {
            cooldown = LUNGE_COOLDOWN; // missed: circle again
        } else {
            cooldown--;
        }
    }

    private void grab(LivingEntity target) {
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        grabbed = target;
        grabTicks = GRAB_TICKS;
        playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.2F, 0.6F);
    }

    /** Holding on: both of them pulled down hard, a bite every half second. */
    private void drag() {
        LivingEntity victim = grabbed;
        if (victim == null || !victim.isAlive() || distanceToSqr(victim) > 6 * 6) {
            release();
            return;
        }
        grabTicks--;
        Vec3 v = victim.getDeltaMovement();
        victim.setDeltaMovement(v.x * 0.5, Math.min(v.y, -0.45), v.z * 0.5);
        victim.hurtMarked = true;
        Vec3 below = victim.position().add(0, -1.2, 0);
        getMoveControl().setWantedPosition(below.x, below.y, below.z, 2.0);
        setDeltaMovement(below.subtract(position()).scale(0.4));
        if (grabTicks % 10 == 0) victim.hurt(damageSources().mobAttack(this), (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.4));
        if (level() instanceof ServerLevel server && grabTicks % 2 == 0) {
            server.sendParticles(TRAIL, victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(), 4, 0.3, 0.6, 0.3, 0);
        }
        if (grabTicks <= 0) release();
    }

    private void release() {
        grabbed = null;
        grabTicks = 0;
        cooldown = LUNGE_COOLDOWN;
    }

    public boolean isDragging() {
        return grabTicks > 0;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        // Hit it while it holds you and it lets go.
        if (hurt && grabbed != null && source.getEntity() == grabbed) {
            release();
            playSound(SoundEvents.SILVERFISH_HURT, 1F, 0.5F);
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        // Face where it's going: the body trails behind (see SpaghettifierModel).
        Vec3 v = getDeltaMovement();
        if (v.horizontalDistanceSqr() > 1e-4) {
            float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90F;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 12));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
        if (level().isClientSide && random.nextInt(3) == 0) {
            Vec3 back = Vec3.directionFromRotation(0, getYRot()).scale(-1.5);
            level().addParticle(TRAIL, getX() + back.x, getY() + 0.4, getZ() + back.z, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ odds and ends

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0; // it lives in the dark; no light rules
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ELDER_GUARDIAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ELDER_GUARDIAN_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.6F + random.nextFloat() * 0.3F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) level().playSound(null, blockPosition(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1F, 1.3F);
        return hit;
    }
}
