package com.masteri.bhe.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * The Drifter: a jellyfish of dark matter that floats through dark energy zones. Neutral and slow, it never attacks;
 * hit it and it lets out a pulse that sends everything near it floating up for a moment. Drops Void Dust.
 * Spawned around players in zones by DrifterSpawner (vanilla spawning can't be limited to a zone).
 */
public class DrifterEntity extends PathfinderMob {
    private static final double PULSE_RANGE = 6;
    private static final int PULSE_COOLDOWN = 40;
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.65F, 0.35F, 1.0F), 1.0F);

    private int pulseCooldown;

    public DrifterEntity(EntityType<? extends DrifterEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 10, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12)
                .add(Attributes.FLYING_SPEED, 0.18)
                .add(Attributes.MOVEMENT_SPEED, 0.12)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    public void tick() {
        super.tick();
        if (pulseCooldown > 0) pulseCooldown--;
        // A slow bob, and a faint trail of violet glow.
        if (!level().isClientSide) {
            setDeltaMovement(getDeltaMovement().add(0, Math.sin(tickCount * 0.05) * 0.003, 0));
        } else if (random.nextInt(4) == 0) {
            level().addParticle(GLOW, getRandomX(0.5), getY() + random.nextDouble() * 0.6, getRandomZ(0.5), 0, -0.02, 0);
        }
    }

    // ------------------------------------------------------------------ the pulse

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && level() instanceof ServerLevel server && source.getEntity() instanceof LivingEntity && pulseCooldown <= 0) {
            pulse(server);
        }
        return hurt;
    }

    /** A burst that lifts everything nearby off its feet (Levitation for a couple of seconds). */
    private void pulse(ServerLevel server) {
        pulseCooldown = PULSE_COOLDOWN;
        for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(PULSE_RANGE),
                e -> e != this && e.isAlive() && e.distanceToSqr(this) <= PULSE_RANGE * PULSE_RANGE)) {
            if (living instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 2), this);
        }
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.6, getZ(), 50, PULSE_RANGE / 3, 0.5, PULSE_RANGE / 3, 0.1);
        server.sendParticles(GLOW, getX(), getY() + 0.6, getZ(), 40, 1.5, 0.8, 1.5, 0);
        server.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.5F, 0.6F);
    }

    // ------------------------------------------------------------------ floating

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true; // ambient: it comes and goes
    }

    // ------------------------------------------------------------------ sounds

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
        return 0.5F + random.nextFloat() * 0.2F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }
}
