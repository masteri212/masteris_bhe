package com.masteri.bhe.entity;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.reactor.DarkZones;
import com.masteri.bhe.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Drifters only live in dark energy zones, which vanilla spawning can't be limited to, so they're spawned here: now
 * and then, around each player inside a zone, a Drifter appears in the air nearby if there aren't many about.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID)
public final class DrifterSpawner {
    private static final int INTERVAL = 100, NEARBY = 48, MIN_DIST = 16, MAX_DIST = 40;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % INTERVAL != 0 || player.isSpectator()) return;
        int cap = ReactorConfig.ZONE_DRIFTERS.get();
        if (cap <= 0) return;
        ServerLevel level = player.serverLevel();
        if (!DarkZones.inZone(level, player.getX(), player.getY(), player.getZ())) return;
        int around = level.getEntitiesOfClass(DrifterEntity.class, new AABB(player.blockPosition()).inflate(NEARBY)).size();
        if (around >= cap) return;

        RandomSource random = level.random;
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = MIN_DIST + random.nextDouble() * (MAX_DIST - MIN_DIST);
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist), z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        // In the air, a few blocks over the ground (or over the player's head in a cave).
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int y = Math.min(ground + 4 + random.nextInt(10), (int) player.getY() + 12);
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above()) || !DarkZones.inZone(level, x + 0.5, y + 0.5, z + 0.5)) return;
        DrifterEntity drifter = ModEntities.DRIFTER.get().create(level);
        if (drifter == null) return;
        drifter.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
        drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        level.addFreshEntity(drifter);
    }

    private DrifterSpawner() {}
}
