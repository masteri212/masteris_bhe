package com.masteri.bhe.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A chunk of rubble orbiting a Debris Ring. Its place is worked out from the time and its orbit (center, radius,
 * starting angle, speed), the same on the server and on every client, so it glides smoothly without the server
 * having to send it. It's solid to stand on, and carries what stands on it: each client moves its own player along
 * (DebrisCarry), the server moves everything else.
 */
public class DebrisPlatformEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> CENTER = SynchedEntityData.defineId(DebrisPlatformEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(DebrisPlatformEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PHASE = SynchedEntityData.defineId(DebrisPlatformEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(DebrisPlatformEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> WIDTH = SynchedEntityData.defineId(DebrisPlatformEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(DebrisPlatformEntity.class, EntityDataSerializers.INT);

    public DebrisPlatformEntity(EntityType<? extends DebrisPlatformEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Sets its orbit: round {@code center} (its top level), {@code radius} out, starting at {@code phase}, turning {@code speed} radians a tick. */
    public void setOrbit(BlockPos center, float radius, float phase, float speed, int width, int style, boolean place) {
        entityData.set(CENTER, center);
        entityData.set(RADIUS, radius);
        entityData.set(PHASE, phase);
        entityData.set(SPEED, speed);
        entityData.set(WIDTH, Mth.clamp(width, 2, 5));
        entityData.set(STYLE, style);
        refreshDimensions();
        if (!place) return;
        Vec3 at = orbitAt(level().getGameTime());
        setPos(at.x, at.y, at.z);
    }

    public int getWidth() {
        return entityData.get(WIDTH);
    }

    public int getStyle() {
        return entityData.get(STYLE);
    }

    /** Where it is at {@code time} (a game tick, may be fractional). */
    public Vec3 orbitAt(double time) {
        BlockPos c = entityData.get(CENTER);
        double a = entityData.get(PHASE) + entityData.get(SPEED) * time;
        double r = entityData.get(RADIUS);
        return new Vec3(c.getX() + 0.5 + Math.cos(a) * r, c.getY() - 1, c.getZ() + 0.5 + Math.sin(a) * r);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CENTER, BlockPos.ZERO);
        builder.define(RADIUS, 0F);
        builder.define(PHASE, 0F);
        builder.define(SPEED, 0F);
        builder.define(WIDTH, 3);
        builder.define(STYLE, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (WIDTH.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(getWidth(), 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (entityData.get(RADIUS) <= 0) return;
        Vec3 before = position();
        Vec3 now = orbitAt(level().getGameTime());
        Vec3 delta = now.subtract(before);
        // What stands on it, before it moves out from under them.
        AABB top = getBoundingBox().move(0, 0.5, 0).setMinY(getBoundingBox().maxY - 0.05).setMaxY(getBoundingBox().maxY + 0.5);
        setPos(now.x, now.y, now.z);
        if (delta.lengthSqr() > 4) return; // first tick after loading: it jumped to where it should be
        if (level().isClientSide) {
            com.masteri.bhe.client.DebrisCarry.carry(this, top, delta);
        } else {
            for (Entity e : level().getEntities(this, top, e -> !(e instanceof Player) && !(e instanceof DebrisPlatformEntity) && !e.isPassenger())) {
                e.setPos(e.position().add(delta));
            }
        }
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setOrbit(NbtUtils.readBlockPos(tag, "Center").orElse(BlockPos.ZERO), tag.getFloat("Radius"), tag.getFloat("Phase"),
                tag.getFloat("Speed"), tag.getInt("Width"), tag.getInt("Style"), true);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Center", NbtUtils.writeBlockPos(entityData.get(CENTER)));
        tag.putFloat("Radius", entityData.get(RADIUS));
        tag.putFloat("Phase", entityData.get(PHASE));
        tag.putFloat("Speed", entityData.get(SPEED));
        tag.putInt("Width", getWidth());
        tag.putInt("Style", getStyle());
    }
}
