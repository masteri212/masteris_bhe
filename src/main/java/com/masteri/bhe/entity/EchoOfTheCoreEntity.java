package com.masteri.bhe.entity;

import com.masteri.bhe.reactor.DarkZones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Echo of the Core: the ghost of the reactor that made a dark energy zone, rising from the bottom of its crater
 * (see ZoneMobSpawner). It hovers near the crater and replays the reactor's failures as attacks:
 * <ul>
 *     <li>Disc stall: a shockwave rolling out along the ground (jump it).</li>
 *     <li>Meltdown: its black hole swells and drags everyone near toward it, hurting those it reaches.</li>
 *     <li>Stabilizer beam: a warning line toward a player, then a beam down it (step aside).</li>
 * </ul>
 * Below half health it attacks faster. It's the zone's final fight: it only rises once the zone is down to its last
 * node, it is that node (no crystal stabilizes it), and beating it removes the zone. Until then it can't be hurt.
 */
public class EchoOfTheCoreEntity extends Monster {
    public enum Attack { NONE, SHOCKWAVE, PULL, BEAM }

    public static final int SHOCKWAVE_TICKS = 28, PULL_TICKS = 70, BEAM_CHARGE = 30, BEAM_FIRE = 10;
    public static final float SHOCKWAVE_SPEED = 0.7F;
    private static final double PULL_RANGE = 22, LEASH = 28;
    /** How far from its home a player can be and still be fought. Past this it gives up and goes home. */
    private static final double ARENA = 64;

    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(EchoOfTheCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_TICK = SynchedEntityData.defineId(EchoOfTheCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> BEAM_TARGET = SynchedEntityData.defineId(EchoOfTheCoreEntity.class, EntityDataSerializers.VECTOR3);

    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.masteris_bhe.echo_of_the_core"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private final Set<UUID> hitThisAttack = new HashSet<>();
    @Nullable private BlockPos home;
    private int cooldown = 60;
    /** Where the shockwave rolls (the ground under it when it started). */
    private double waveY;

    public EchoOfTheCoreEntity(EntityType<? extends EchoOfTheCoreEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
        xpReward = 150;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.FLYING_SPEED, 0.35)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK, Attack.NONE.ordinal());
        builder.define(ATTACK_TICK, 0);
        builder.define(BEAM_TARGET, new Vector3f());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, this::inArena));
    }

    private boolean inArena(LivingEntity entity) {
        return home == null || entity.distanceToSqr(Vec3.atCenterOf(home)) <= ARENA * ARENA;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    /** The crater floor it rose from and won't stray far from. */
    public void setHome(BlockPos pos) {
        home = pos.immutable();
    }

    public Attack getAttack() {
        Attack[] all = Attack.values();
        int i = entityData.get(ATTACK);
        return i >= 0 && i < all.length ? all[i] : Attack.NONE;
    }

    public int getAttackTick() {
        return entityData.get(ATTACK_TICK);
    }

    public Vec3 getBeamTarget() {
        Vector3f v = entityData.get(BEAM_TARGET);
        return new Vec3(v.x, v.y, v.z);
    }

    /** Where its black hole is (the middle of the cage). */
    public Vec3 core() {
        return position().add(0, getBbHeight() / 2, 0);
    }

    private boolean enraged() {
        return getHealth() < getMaxHealth() / 2;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (home == null) home = blockPosition();
        // A target that left the arena is let go: it drifts back home and waits.
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || !inArena(target))) {
            setTarget(null);
            if (getAttack() != Attack.NONE) endAttack();
        }
        // Knocked or carried far off (it can't fly there itself): back home at once.
        if (distanceToSqr(Vec3.atCenterOf(home)) > (LEASH * 2) * (LEASH * 2)) {
            moveTo(home.getX() + 0.5, home.getY() + 4, home.getZ() + 0.5, getYRot(), getXRot());
            setDeltaMovement(Vec3.ZERO);
        }
        hover();
        Attack attack = getAttack();
        if (attack == Attack.NONE) {
            if (--cooldown <= 0 && getTarget() != null && getTarget().isAlive()) startAttack();
            return;
        }
        int tick = getAttackTick() + 1;
        entityData.set(ATTACK_TICK, tick);
        ServerLevel level = (ServerLevel) level();
        switch (attack) {
            case SHOCKWAVE -> { shockwave(level, tick); if (tick >= SHOCKWAVE_TICKS) endAttack(); }
            case PULL -> { pull(level, tick); if (tick >= PULL_TICKS) endAttack(); }
            case BEAM -> { beam(level, tick); if (tick >= BEAM_CHARGE + BEAM_FIRE) endAttack(); }
            default -> endAttack();
        }
    }

    /** Circles a little above its target, but never far from the crater. */
    private void hover() {
        LivingEntity target = getTarget();
        Vec3 want;
        if (target != null && target.isAlive() && getAttack() != Attack.PULL) {
            double angle = tickCount * 0.02;
            want = target.position().add(Math.cos(angle) * 8, 5, Math.sin(angle) * 8);
        } else {
            want = Vec3.atCenterOf(home).add(0, 4, 0);
        }
        Vec3 homeCenter = Vec3.atCenterOf(home);
        Vec3 off = want.subtract(homeCenter);
        if (off.length() > LEASH) want = homeCenter.add(off.normalize().scale(LEASH));
        getMoveControl().setWantedPosition(want.x, want.y, want.z, 1.0);
    }

    private void startAttack() {
        Attack[] options = {Attack.SHOCKWAVE, Attack.PULL, Attack.BEAM};
        Attack next = options[random.nextInt(options.length)];
        hitThisAttack.clear();
        entityData.set(ATTACK, next.ordinal());
        entityData.set(ATTACK_TICK, 0);
        ServerLevel level = (ServerLevel) level();
        switch (next) {
            case SHOCKWAVE -> {
                waveY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
                level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3F, 0.5F);
            }
            case PULL -> level.playSound(null, blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 4F, 0.5F);
            case BEAM -> {
                LivingEntity target = getTarget();
                Vec3 at = target != null ? target.getEyePosition() : core().add(getLookAngle().scale(10));
                entityData.set(BEAM_TARGET, new Vector3f((float) at.x, (float) at.y, (float) at.z));
                level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3F, 1.6F);
            }
            default -> {}
        }
    }

    private void endAttack() {
        entityData.set(ATTACK, Attack.NONE.ordinal());
        entityData.set(ATTACK_TICK, 0);
        cooldown = enraged() ? 30 + random.nextInt(15) : 55 + random.nextInt(25);
    }

    // ------------------------------------------------------------------ attacks

    /** Disc stall: a ring rolling out along the ground. Anyone on the ground where it passes is hit and thrown. */
    private void shockwave(ServerLevel level, int tick) {
        float radius = tick * SHOCKWAVE_SPEED;
        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(blockPosition()).inflate(radius + 2, 40, radius + 2))) {
            if (player.isCreative() || player.isSpectator() || hitThisAttack.contains(player.getUUID())) continue;
            double dx = player.getX() - getX(), dz = player.getZ() - getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            // Only on the ground near the wave's level: jumping clears it.
            if (Math.abs(dist - radius) > 1 || !player.onGround() || Math.abs(player.getY() - waveY) > 3) continue;
            hitThisAttack.add(player.getUUID());
            player.hurt(damageSources().mobAttack(this), enraged() ? 11 : 8);
            Vec3 away = new Vec3(dx, 0, dz).normalize().scale(1.2);
            player.push(away.x, 0.6, away.z);
            player.hurtMarked = true;
        }
        int points = (int) Math.min(80, 6 * radius + 8);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(ParticleTypes.CLOUD, getX() + Math.cos(a) * radius, waveY + 0.2, getZ() + Math.sin(a) * radius, 1, 0, 0.1, 0, 0.01);
        }
    }

    /** Meltdown: the black hole swells and pulls everyone near toward it; those it reaches are hurt. */
    private void pull(ServerLevel level, int tick) {
        Vec3 center = core();
        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(blockPosition()).inflate(PULL_RANGE))) {
            if (player.isCreative() || player.isSpectator()) continue;
            Vec3 toCore = center.subtract(player.position());
            double dist = toCore.length();
            if (dist > PULL_RANGE || dist < 0.1) continue;
            double strength = (0.03 + 0.09 * (1 - dist / PULL_RANGE)) * (enraged() ? 1.4 : 1);
            player.setDeltaMovement(player.getDeltaMovement().add(toCore.scale(strength / dist)));
            player.hurtMarked = true;
            if (dist < 3 && tick % 10 == 0) player.hurt(damageSources().mobAttack(this), enraged() ? 5 : 3);
        }
        if (tick % 2 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 20, 6, 3, 6, 0.6);
        if (tick % 20 == 0) level.playSound(null, blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 4F, 0.5F);
    }

    /** Stabilizer beam: a warning line, then a beam down it. Anything still on the line is hit. */
    private void beam(ServerLevel level, int tick) {
        Vec3 from = core(), to = getBeamTarget();
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 0.1) return;
        dir = dir.scale(1 / length);
        // Extend the line past the target, so stepping back along it doesn't help.
        Vec3 end = from.add(dir.scale(Math.max(length + 12, 24)));
        if (tick == BEAM_CHARGE) {
            level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3F, 1.2F);
        }
        if (tick < BEAM_CHARGE) return; // the renderer draws the warning line
        AABB box = new AABB(from, end).inflate(1.2);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
            if (living instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (hitThisAttack.contains(living.getUUID())) continue;
            Vec3 c = living.getBoundingBox().getCenter();
            Vec3 rel = c.subtract(from);
            double along = Math.max(0, Math.min(rel.dot(dir), from.distanceTo(end)));
            if (c.distanceTo(from.add(dir.scale(along))) > 1.2) continue;
            hitThisAttack.add(living.getUUID());
            living.hurt(damageSources().indirectMagic(this, this), enraged() ? 14 : 10);
        }
        for (double d = 0; d < from.distanceTo(end); d += 1.5) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    // ------------------------------------------------------------------ the shield

    /**
     * How many of its zone's other nodes still need stabilizing before it can be hurt (0 once it's down to the last
     * one, or outside any zone).
     */
    public int shieldingNodes() {
        if (!(level() instanceof ServerLevel server)) return 0;
        DarkZones.Zone zone = DarkZones.get(server).at(home != null ? home : blockPosition());
        return zone == null ? 0 : Math.max(0, zone.nodes() - 1);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            int left = shieldingNodes();
            if (left > 0) {
                if (source.getEntity() instanceof Player player) {
                    player.displayClientMessage(Component.translatable("message.masteris_bhe.echo.shielded", left)
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
                Vec3 c = core();
                ((ServerLevel) level()).sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 12, 1, 1, 1, 0.2);
                level().playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5F, 0.6F);
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ death: the zone gives a little

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel server)) return;
        BlockPos at = home != null ? home : blockPosition();
        DarkZones.Stabilized result = DarkZones.get(server).stabilize(server, at);
        Component text = result == null
                ? Component.translatable("message.masteris_bhe.echo.defeated").withStyle(ChatFormatting.LIGHT_PURPLE)
                : result.zone() == null
                ? Component.translatable("message.masteris_bhe.echo.defeated_cleared").withStyle(ChatFormatting.AQUA)
                : Component.translatable("message.masteris_bhe.echo.defeated_zone", result.zone().radius(), result.zone().nodes())
                        .withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(this) < 128 * 128) player.sendSystemMessage(text);
        }
        server.sendParticles(ParticleTypes.FLASH, getX(), core().y, getZ(), 2, 0.5, 0.5, 0.5, 0);
        server.playSound(null, blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 4F, 0.6F);
    }

    // ------------------------------------------------------------------ boss bar, saving, odds and ends

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home = NbtUtils.readBlockPos(tag, "Home").orElse(null);
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    /** Its beam and shockwave reach far past its body: keep drawing it while they're on screen. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(32);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** It is the zone's last node: it must never just vanish, not even on Peaceful. */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }
}
