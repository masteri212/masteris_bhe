package com.masteri.bhe.entity;

import com.masteri.bhe.horizon.EventHorizon;
import com.masteri.bhe.reactor.DarkZones;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

/**
 * The zone's creatures (Drifters, Lensing Stalkers) can't live without its dark energy. Outside every zone (anywhere
 * but the Event Horizon, where they're at home) they come apart in a few seconds, leaving nothing behind.
 */
public final class ZoneBound {
    /** Seconds a zone creature lasts outside a zone. */
    private static final int SECONDS = 3;
    private static final String KEY = "masteris_bhe:outside_zone";

    /** Call every server tick. */
    public static void tick(Mob mob) {
        if (mob.tickCount % 20 != 0 || !(mob.level() instanceof ServerLevel level) || mob.isRemoved() || mob.isDeadOrDying()) return;
        var data = mob.getPersistentData();
        if (EventHorizon.isHorizon(level) || DarkZones.inZone(level, mob.getX(), mob.getY(), mob.getZ())) {
            data.remove(KEY);
            return;
        }
        int outside = data.getInt(KEY) + 1;
        data.putInt(KEY, outside);
        double y = mob.getY() + mob.getBbHeight() / 2;
        if (outside < SECONDS) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), y, mob.getZ(), 15, mob.getBbWidth() / 2, mob.getBbHeight() / 3, mob.getBbWidth() / 2, 0.05);
            return;
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), y, mob.getZ(), 60, mob.getBbWidth() / 2, mob.getBbHeight() / 3, mob.getBbWidth() / 2, 0.2);
        level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), y, mob.getZ(), 20, mob.getBbWidth() / 2, mob.getBbHeight() / 3, mob.getBbWidth() / 2, 0.05);
        level.playSound(null, mob.blockPosition(), SoundEvents.ENDERMAN_DEATH, SoundSource.HOSTILE, 0.8F, 1.6F);
        mob.discard();
    }

    private ZoneBound() {}
}
