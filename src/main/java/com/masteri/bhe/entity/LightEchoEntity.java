package com.masteri.bhe.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A Light Trap's echo of a player: light from five seconds ago still circling the trap's tiny black hole. It does
 * exactly what its player did, {@link #DELAY} ticks late, and it counts for the trap's pads (see TrapCoreBlockEntity),
 * so you can stand on one pad while your past self stands on the other. Made by the trap core; never saved.
 */
public class LightEchoEntity extends Entity {
    public static final int DELAY = 100;
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(LightEchoEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> SHOWN = SynchedEntityData.defineId(LightEchoEntity.class, EntityDataSerializers.BOOLEAN);

    /** Where its player was, one entry per tick: x, y, z, yaw, pitch. A ring buffer {@link #DELAY} long. */
    private final double[][] past = new double[DELAY][];
    private int recorded;
    /** Client: how far it has walked, for the leg swing. */
    public float walked, walkedBefore;

    public LightEchoEntity(EntityType<? extends LightEchoEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setOwner(UUID owner) {
        entityData.set(OWNER, Optional.of(owner));
    }

    @Nullable
    public UUID getOwner() {
        return entityData.get(OWNER).orElse(null);
    }

    /** Whether it's caught up enough to be seen (and to count): it needs five seconds of its player first. */
    public boolean isShown() {
        return entityData.get(SHOWN);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(SHOWN, false);
    }

    @Override
    public void tick() {
        Vec3 before = position();
        super.tick();
        if (level().isClientSide) {
            walkedBefore = walked;
            walked += (float) position().subtract(before).horizontalDistance();
            return;
        }
        UUID id = getOwner();
        Player owner = id == null ? null : level().getPlayerByUUID(id);
        if (owner == null || owner.isSpectator()) {
            discard();
            return;
        }
        int slot = recorded % DELAY;
        double[] then = past[slot];
        past[slot] = new double[] {owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot()};
        recorded++;
        if (then != null) {
            moveTo(then[0], then[1], then[2], (float) then[3], (float) then[4]);
            if (!isShown()) entityData.set(SHOWN, true);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) setOwner(tag.getUUID("Owner"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = getOwner();
        if (owner != null) tag.putUUID("Owner", owner);
    }
}
