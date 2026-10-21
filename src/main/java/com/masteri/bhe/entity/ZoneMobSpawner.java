package com.masteri.bhe.entity;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.reactor.DarkZones;
import com.masteri.bhe.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The zone's own mobs, which vanilla spawning can't confine to a zone, spawned around the players inside one:
 * <ul>
 *     <li>Drifters, floating anywhere in the zone.</li>
 *     <li>Lensing Stalkers, on the ground in the fallout (never right next to you).</li>
 *     <li>The Echo of the Core, rising from the crater floor when someone comes near it once the zone is down to its
 *     last node (it is that node: see EchoOfTheCoreEntity), then not again for a while.</li>
 * </ul>
 */
@EventBusSubscriber(modid = MasteriBHE.MODID)
public final class ZoneMobSpawner {
    private static final int INTERVAL = 100, NEARBY = 48;
    private static final double ECHO_TRIGGER = 48, ECHO_PRESENT = 96;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % INTERVAL != 0 || player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        DarkZones zones = DarkZones.get(level);
        if (zones.zones().isEmpty()) return;
        DarkZones.Zone zone = zones.at(player.getX(), player.getY(), player.getZ());
        if (zone == null) return;
        spawnDrifter(level, player);
        if (zone.inFallout(player.getX(), player.getY(), player.getZ())) spawnStalker(level, player, zone);
        spawnEcho(level, player, zones, zone);
    }

    // ------------------------------------------------------------------ drifters

    private static void spawnDrifter(ServerLevel level, ServerPlayer player) {
        int cap = ReactorConfig.ZONE_DRIFTERS.get();
        if (cap <= 0 || count(level, player, DrifterEntity.class) >= cap) return;
        RandomSource random = level.random;
        BlockPos column = around(player, random, 16, 40);
        if (!level.hasChunk(column.getX() >> 4, column.getZ() >> 4)) return;
        // In the air, a few blocks over the ground (or over the player's head in a cave).
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
        int y = Math.min(ground + 4 + random.nextInt(10), (int) player.getY() + 12);
        BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) return;
        if (!DarkZones.inZone(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) return;
        spawn(level, ModEntities.DRIFTER.get(), pos, random);
    }

    // ------------------------------------------------------------------ stalkers

    private static void spawnStalker(ServerLevel level, ServerPlayer player, DarkZones.Zone zone) {
        int cap = ReactorConfig.ZONE_STALKERS.get();
        if (cap <= 0 || count(level, player, LensingStalkerEntity.class) >= cap) return;
        RandomSource random = level.random;
        if (random.nextInt(3) != 0) return; // rarer than drifters
        BlockPos column = around(player, random, 16, 36);
        if (!level.hasChunk(column.getX() >> 4, column.getZ() >> 4)) return;
        // On the ground, with room for something three blocks tall.
        BlockPos pos = new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), column.getZ());
        for (int i = 0; i < 3; i++) {
            if (!level.isEmptyBlock(pos.above(i))) return;
        }
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) return;
        if (!zone.inFallout(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) return;
        spawn(level, ModEntities.LENSING_STALKER.get(), pos, random);
    }

    // ------------------------------------------------------------------ the echo of the core

    private static void spawnEcho(ServerLevel level, ServerPlayer player, DarkZones zones, DarkZones.Zone zone) {
        int minutes = ReactorConfig.ECHO_COOLDOWN_MINUTES.get();
        if (minutes <= 0 || zone.nodes() > 1) return; // the final fight: only once every other node is stabilized
        Vec3 floor = Vec3.atCenterOf(zone.center());
        if (player.position().distanceTo(floor) > ECHO_TRIGGER) return;
        if (level.getGameTime() < zones.echoReadyAt(zone.center())) return;
        if (!level.getEntitiesOfClass(EchoOfTheCoreEntity.class, new AABB(zone.center()).inflate(ECHO_PRESENT)).isEmpty()) return;
        BlockPos pos = zone.center().above(3);
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) return;
        EchoOfTheCoreEntity echo = ModEntities.ECHO_OF_THE_CORE.get().create(level);
        if (echo == null) return;
        echo.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        echo.setHome(pos);
        echo.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        level.addFreshEntity(echo);
        zones.setEchoReadyAt(zone.center(), level.getGameTime() + minutes * 1200L);
        level.sendParticles(ParticleTypes.FLASH, floor.x, floor.y + 3, floor.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, floor.x, floor.y + 2, floor.z, 200, 3, 2, 3, 0.4);
        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3F, 0.6F);
        Component text = Component.translatable("message.masteris_bhe.echo.risen").withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(floor) < 160) p.sendSystemMessage(text);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static int count(ServerLevel level, ServerPlayer player, Class<? extends Mob> type) {
        return level.getEntitiesOfClass(type, new AABB(player.blockPosition()).inflate(NEARBY)).size();
    }

    /** A random column {@code min}..{@code max} blocks away from the player. */
    private static BlockPos around(ServerPlayer player, RandomSource random, int min, int max) {
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = min + random.nextDouble() * (max - min);
        return BlockPos.containing(player.getX() + Math.cos(angle) * dist, player.getY(), player.getZ() + Math.sin(angle) * dist);
    }

    private static <T extends Mob> void spawn(ServerLevel level, EntityType<T> type, BlockPos pos, RandomSource random) {
        T mob = type.create(level);
        if (mob == null) return;
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        level.addFreshEntity(mob);
    }

    private ZoneMobSpawner() {}
}
