package com.masteri.bhe.block.entity;

import com.masteri.bhe.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A Graviton Well: a small gravity sink. It pulls in everything within a few blocks, harder the closer it is. */
public class GravitonWellBlockEntity extends BlockEntity {
    public static final double RANGE = 7;
    private static final double STRENGTH = 0.06;

    public GravitonWellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVITON_WELL.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 c = Vec3.atCenterOf(worldPosition).add(0, 0.6, 0);
        boolean sendPlayers = server.getGameTime() % 3 == 0; // pushing players means a packet: not every tick
        for (Entity e : server.getEntities((Entity) null, new AABB(worldPosition).inflate(RANGE), Entity::isAlive)) {
            if (e instanceof Player p && (p.isSpectator() || p.getAbilities().flying)) continue;
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
            double d = to.length();
            if (d > RANGE || d < 0.6) continue;
            double pull = STRENGTH * (1 - d / RANGE) + 0.01;
            e.setDeltaMovement(e.getDeltaMovement().add(to.scale(pull / d)));
            if (e instanceof Player && sendPlayers) e.hurtMarked = true;
        }
        if (server.getGameTime() % 4 == 0) {
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 3, RANGE / 3, 1, RANGE / 3, 0.2);
        }
    }
}
