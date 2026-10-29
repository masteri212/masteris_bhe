package com.masteri.bhe.entity;

import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.horizon.EventHorizon;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModEntities;
import com.masteri.bhe.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The Singularity: the Event Horizon's heart, called up with a Singularity Lure. Weapons pass straight through it.
 * The only way to beat it is to feed it unstable matter (throw it in: Neutronium, Unstable Dark Residue, Mass Shards
 * and the like) until it can't hold itself together and collapses. Anything else it swallows does nothing.
 *
 * It pulls on everything near it and burns whatever touches it. Its attacks: rifts that open under you and snap
 * shut (move), a tide that pulls much harder for a few seconds, a Hawking burst that hits everyone it can see
 * (get behind a spire), and Spaghettifiers. Once it's past half-fed it's unstable: faster and harder.
 */
public class SingularityEntity extends Monster {
    public enum Attack { NONE, RIFTS, TIDE, BURST, BROOD }

    public static final int TIDE_TICKS = 70, BURST_CHARGE = 40, RIFT_TICKS = 30;
    private static final double PULL_RANGE = 36, BURST_RANGE = 34;
    private static final DustParticleOptions RIFT = new DustParticleOptions(new Vector3f(1.0F, 0.35F, 0.1F), 1.6F);

    private static final EntityDataAccessor<Float> FED = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_TICK = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.masteris_bhe.singularity"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
    private final List<Rift> rifts = new ArrayList<>();
    @Nullable private BlockPos home;
    @Nullable private UUID lastFeeder;
    private long fed;
    private int cooldown = 80;
    private boolean collapsing;
    private boolean wantOpen = true;

    private record Rift(Vec3 at, int[] timer) {}

    public SingularityEntity(EntityType<? extends SingularityEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 10, true);
        setNoGravity(true);
        noPhysics = true; // drifts through the spires
        xpReward = 500;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000)
                .add(Attributes.FLYING_SPEED, 0.12)
                .add(Attributes.MOVEMENT_SPEED, 0.12)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FED, 0F);
        builder.define(ATTACK, Attack.NONE.ordinal());
        builder.define(ATTACK_TICK, 0);
    }

    @Override
    protected void registerGoals() {}

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    public void setHome(BlockPos pos) {
        home = pos.immutable();
    }

    // ------------------------------------------------------------------ state, for the renderer too

    /** 0..1: how close it is to collapsing. */
    public float fedFraction() {
        return entityData.get(FED);
    }

    public Attack getAttack() {
        Attack[] all = Attack.values();
        int i = entityData.get(ATTACK);
        return i >= 0 && i < all.length ? all[i] : Attack.NONE;
    }

    public int getAttackTick() {
        return entityData.get(ATTACK_TICK);
    }

    /** Radius of its event horizon: it swells as it's fed. */
    public float horizonRadius() {
        return 1.6F + 1.6F * fedFraction();
    }

    public Vec3 core() {
        return position().add(0, getBbHeight() / 2, 0);
    }

    // ------------------------------------------------------------------ keeping out of the rock

    /** Room for it to be seen: no solid block within {@code radius} of {@code center}. */
    public static boolean openAround(Level level, Vec3 center, int radius) {
        BlockPos c = BlockPos.containing(center);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-radius, -radius, -radius), c.offset(radius, radius, radius))) {
            if (p.distSqr(c) > radius * radius) continue;
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
        }
        return true;
    }

    /**
     * Somewhere open to rise in, near {@code preferred}: it passes through blocks, and inside an island nobody would
     * ever see it. Tries further round and up and down until it finds a clear pocket; {@code preferred} if none.
     */
    public static Vec3 findOpenSpot(Level level, Vec3 player, Vec3 preferred) {
        double minY = EventHorizon.spaghettificationY() + 8;
        Vec3 dir = preferred.subtract(player).multiply(1, 0, 1);
        double base = dir.lengthSqr() < 1e-4 ? 0 : Math.atan2(dir.z, dir.x);
        for (double distance : new double[] {22, 18, 28, 14, 34}) {
            for (int turn = 0; turn < 12; turn++) {
                double a = base + (turn % 2 == 0 ? 1 : -1) * ((turn + 1) / 2) * (Math.PI / 6);
                for (double dy : new double[] {0, -6, 6, -12, 12, 18}) {
                    Vec3 at = new Vec3(player.x + Math.cos(a) * distance, Math.max(minY, player.y + dy), player.z + Math.sin(a) * distance);
                    if (openAround(level, at, 5)) return at;
                }
            }
        }
        return preferred;
    }

    private boolean unstable() {
        return fedFraction() >= 0.5F;
    }

    // ------------------------------------------------------------------ what it's fed

    /** How much unstable matter one of this item is worth to it; 0 for anything else. */
    public static int feedValue(Item item) {
        if (item == ModItems.NEUTRONIUM_INGOT.get()) return 60;
        if (item == ModBlocks.NEUTRONIUM_BLOCK.get().asItem()) return 540;
        if (item == ModItems.RAW_NEUTRONIUM.get()) return 30;
        if (item == ModItems.UNSTABLE_DARK_RESIDUE.get()) return 25;
        if (item == ModItems.MASS_SHARD.get()) return 8;
        if (item == ModItems.DARK_MATTER_SHARD.get()) return 10;
        if (item == ModItems.HAWKING_MOTE.get()) return 15;
        if (item == ModItems.CHARGED_DARK_MATTER_CRYSTAL.get()) return 80;
        if (item == ModItems.EVENT_HORIZON_FRAGMENT.get()) return 200;
        if (item == ModItems.VOID_DUST.get()) return 2;
        return 0;
    }

    private void feed(ServerLevel level, ItemEntity item) {
        ItemStack stack = item.getItem();
        long value = (long) feedValue(stack.getItem()) * stack.getCount();
        Entity owner = item.getOwner();
        item.discard();
        Vec3 c = core();
        if (value <= 0) {
            level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 6, 0.5, 0.5, 0.5, 0.02);
            return;
        }
        if (owner instanceof Player) lastFeeder = owner.getUUID();
        fed += value;
        entityData.set(FED, (float) Math.min(1, fed / (double) ReactorConfig.SINGULARITY_COLLAPSE.get()));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20, 1, 1, 1, 0.5);
        level.playSound(null, blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2F, 0.5F + fedFraction());
        if (fedFraction() >= 1) collapse(level);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level) || collapsing) return;
        if (home == null) home = blockPosition();
        bossBar.setProgress(fedFraction());
        drift();
        pull(level);
        tickRifts(level);
        Attack attack = getAttack();
        if (attack == Attack.NONE) {
            if (--cooldown <= 0 && getTarget() != null) startAttack(level);
            return;
        }
        int tick = getAttackTick() + 1;
        entityData.set(ATTACK_TICK, tick);
        switch (attack) {
            case TIDE -> { if (tick >= TIDE_TICKS) endAttack(); }
            case BURST -> { if (tick == BURST_CHARGE) burst(level); if (tick >= BURST_CHARGE + 10) endAttack(); }
            default -> endAttack();
        }
    }

    /** Hangs near home, edging toward the nearest player but keeping its distance. */
    private void drift() {
        Player nearest = level().getNearestPlayer(this, 64);
        if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()) setTarget(nearest);
        else setTarget(null);
        Vec3 h = Vec3.atCenterOf(home);
        Vec3 want = h;
        if (getTarget() != null) {
            Vec3 toward = getTarget().position().subtract(h).multiply(1, 0, 1);
            if (toward.length() > 14) want = h.add(toward.normalize().scale(Math.min(10, toward.length() - 14)));
        }
        want = want.add(Math.sin(tickCount * 0.01) * 2, Math.sin(tickCount * 0.017) * 1.5, Math.cos(tickCount * 0.01) * 2);
        // Only into open air (checked now and then): it shouldn't bury itself in an island.
        if (tickCount % 20 == 0) wantOpen = openAround(level(), want.add(0, getBbHeight() / 2, 0), 4);
        if (!wantOpen) want = h;
        getMoveControl().setWantedPosition(want.x, want.y, want.z, 1.0);
    }

    /** Draws in players and items; burns players that touch it; swallows items that reach it. */
    private void pull(ServerLevel level) {
        Vec3 c = core();
        double r = horizonRadius();
        double force = getAttack() == Attack.TIDE ? 3.0 : 1.0;
        if (unstable()) force *= 1.3;
        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(PULL_RANGE))) {
            if (player.isCreative() || player.isSpectator()) continue;
            Vec3 to = c.subtract(player.position().add(0, 0.9, 0));
            double d = to.length();
            if (d > PULL_RANGE || d < 0.1) continue;
            double strength = (0.012 + 0.05 * (1 - d / PULL_RANGE)) * force;
            player.setDeltaMovement(player.getDeltaMovement().add(to.scale(strength / d)));
            if (tickCount % 2 == 0) player.hurtMarked = true;
            if (d < r + 1.5 && tickCount % 10 == 0) player.hurt(EventHorizon.spaghettified(level, this), unstable() ? 8 : 5);
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(c, c).inflate(PULL_RANGE))) {
            Vec3 to = c.subtract(item.position());
            double d = to.length();
            if (d < r + 1) {
                feed(level, item);
                if (collapsing) return;
                continue;
            }
            item.setDeltaMovement(item.getDeltaMovement().scale(0.9).add(to.scale(0.08 / d)));
        }
        if (tickCount % 3 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 12, r * 2, r * 2, r * 2, 0.4);
    }

    // ------------------------------------------------------------------ attacks

    private void startAttack(ServerLevel level) {
        Attack[] options = {Attack.RIFTS, Attack.RIFTS, Attack.TIDE, Attack.BURST, Attack.BROOD};
        Attack next = options[random.nextInt(options.length)];
        entityData.set(ATTACK, next.ordinal());
        entityData.set(ATTACK_TICK, 0);
        switch (next) {
            case RIFTS -> {
                for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(48))) {
                    if (player.isCreative() || player.isSpectator()) continue;
                    rifts.add(new Rift(player.position(), new int[] {RIFT_TICKS}));
                    if (unstable()) rifts.add(new Rift(player.position().add(random.nextGaussian() * 4, 0, random.nextGaussian() * 4), new int[] {RIFT_TICKS + 10}));
                }
                level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 3F, 0.5F);
            }
            case TIDE -> level.playSound(null, blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 5F, 0.4F);
            case BURST -> level.playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4F, 0.5F);
            case BROOD -> brood(level);
            default -> {}
        }
    }

    private void endAttack() {
        entityData.set(ATTACK, Attack.NONE.ordinal());
        entityData.set(ATTACK_TICK, 0);
        cooldown = unstable() ? 35 + random.nextInt(25) : 60 + random.nextInt(40);
    }

    /** Rifts: a ring opens where you stood; a moment later it snaps shut and hurts anyone still in it. */
    private void tickRifts(ServerLevel level) {
        for (Iterator<Rift> it = rifts.iterator(); it.hasNext(); ) {
            Rift rift = it.next();
            int left = --rift.timer()[0];
            Vec3 at = rift.at();
            if (left > 0) {
                if (left % 2 == 0) {
                    for (int i = 0; i < 16; i++) {
                        double a = Math.PI * 2 * i / 16 + left * 0.2;
                        level.sendParticles(RIFT, at.x + Math.cos(a) * 2.5, at.y + 0.2, at.z + Math.sin(a) * 2.5, 1, 0, 0, 0, 0);
                    }
                }
                continue;
            }
            it.remove();
            level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
            level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5F, 1.4F);
            for (Player player : level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(2.8, 3, 2.8))) {
                if (player.isCreative() || player.isSpectator()) continue;
                player.hurt(EventHorizon.spaghettified(level, this), unstable() ? 11 : 8);
                Vec3 toCore = core().subtract(player.position()).normalize();
                player.setDeltaMovement(player.getDeltaMovement().add(toCore.scale(0.9)));
                player.hurtMarked = true;
            }
        }
    }

    /** Hawking burst: everyone it has a clear line to is hit and thrown back. Cover blocks it. */
    private void burst(ServerLevel level) {
        Vec3 c = core();
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.5, 0.5, 0.5, 0);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 150, 2, 2, 2, 0.8);
        level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 5F, 0.6F);
        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(BURST_RANGE))) {
            if (player.isCreative() || player.isSpectator() || player.distanceToSqr(c) > BURST_RANGE * BURST_RANGE) continue;
            HitResult hit = level.clip(new ClipContext(c, player.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.BLOCK) continue; // in cover
            player.hurt(level.damageSources().indirectMagic(this, this), unstable() ? 14 : 10);
            Vec3 away = player.position().subtract(c).normalize();
            player.setDeltaMovement(player.getDeltaMovement().add(away.scale(1.4)).add(0, 0.3, 0));
            player.hurtMarked = true;
        }
    }

    /** Spaghettifiers wriggle out of it (a few at most around it). */
    private void brood(ServerLevel level) {
        int around = level.getEntitiesOfClass(SpaghettifierEntity.class, getBoundingBox().inflate(48)).size();
        int count = Math.min(unstable() ? 3 : 2, 5 - around);
        for (int i = 0; i < count; i++) {
            SpaghettifierEntity eel = ModEntities.SPAGHETTIFIER.get().create(level);
            if (eel == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 at = core().add(Math.cos(a) * (horizonRadius() + 3), random.nextGaussian(), Math.sin(a) * (horizonRadius() + 3));
            eel.moveTo(at.x, at.y, at.z, random.nextFloat() * 360, 0);
            eel.finalizeSpawn(level, level.getCurrentDifficultyAt(eel.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            level.addFreshEntity(eel);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 30, 0.5, 0.5, 0.5, 0.3);
        }
        level.playSound(null, blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 3F, 0.6F);
    }

    // ------------------------------------------------------------------ weapons pass through; collapsing

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!level().isClientSide && source.getEntity() instanceof Player player) {
            player.displayClientMessage(Component.translatable(EventHorizon.key("singularity_immune")).withStyle(ChatFormatting.GOLD), true);
        }
        return false;
    }

    /** Fed enough: it falls in on itself. The last player to feed it gets the kill (and the loot). */
    private void collapse(ServerLevel level) {
        collapsing = true;
        Vec3 c = core();
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 4, 1, 1, 1, 0);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 400, 3, 3, 3, 1.2);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 400, 6, 6, 6, 1.0);
        level.playSound(null, blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 6F, 0.5F);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 6F, 0.4F);
        Player feeder = lastFeeder == null ? null : level.getPlayerByUUID(lastFeeder);
        if (feeder != null) setLastHurtByPlayer(feeder);
        Component text = Component.translatable(EventHorizon.key("singularity_collapsed")).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 160 * 160) p.sendSystemMessage(text);
        }
        hurt(damageSources().genericKill(), Float.MAX_VALUE);
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
        tag.putLong("Fed", fed);
        if (lastFeeder != null) tag.putUUID("LastFeeder", lastFeeder);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home = NbtUtils.readBlockPos(tag, "Home").orElse(null);
        fed = tag.getLong("Fed");
        entityData.set(FED, (float) Math.min(1, fed / (double) ReactorConfig.SINGULARITY_COLLAPSE.get()));
        lastFeeder = tag.hasUUID("LastFeeder") ? tag.getUUID("LastFeeder") : null;
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(40);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 60;
    }

    @Override
    protected float getSoundVolume() {
        return 3F;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }
}
