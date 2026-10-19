package com.masteri.bhe.reactor;

import com.masteri.bhe.MasteriBHE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The reactor collapse blast: a sphere of exactly the configured radius, carved a slice at a time over a few
 * seconds so a 50-block crater doesn't freeze the server. Vanilla explosions can't do this at that size (they're
 * ray-based, so big ones are patchy and very slow). Entities inside the radius are hit once, right away.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID)
public final class Crater {
    private static final int BLOCKS_PER_TICK = 40_000;
    private static final int BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE; // no neighbor updates
    private static final float MAX_DAMAGE = 60F;
    /** Ticks to wait for a chunk to load before giving up on it (outside the world border, say). */
    private static final int MAX_CHUNK_WAIT = 400;

    private static final List<Job> JOBS = new ArrayList<>();
    private static final List<Shockwave> SHOCKWAVES = new ArrayList<>();

    /** Damages and throws entities inside the radius, and (if {@code destroyBlocks}) starts carving the crater. */
    public static void blast(ServerLevel level, BlockPos center, int radius, boolean destroyBlocks) {
        Vec3 c = Vec3.atCenterOf(center);
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(center).inflate(radius),
                e -> e instanceof LivingEntity || e instanceof ItemEntity)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 away = e.position().subtract(c);
            double dist = away.length();
            if (dist > radius) continue;
            double strength = 1 - dist / radius;
            if (e instanceof LivingEntity living) {
                living.hurt(level.damageSources().explosion(null, null), (float) (MAX_DAMAGE * strength + 4));
            }
            Vec3 push = (dist < 0.01 ? new Vec3(0, 1, 0) : away.normalize()).scale(3 * strength).add(0, strength, 0);
            e.push(push.x, push.y, push.z);
            e.hurtMarked = true;
        }
        if (destroyBlocks) JOBS.add(new Job(level, center, radius));
        SHOCKWAVES.add(new Shockwave(level, center, radius));
        level.playSound(null, center, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 10F, 0.5F);
        level.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 10F, 0.6F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 3, 2, 2, 2, 0);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
    }

    /** The bottom of a crater of {@code radius} blasted at {@code center} (where a dark energy zone is centered). */
    public static BlockPos floor(ServerLevel level, BlockPos center, int radius) {
        return new BlockPos(center.getX(), Math.max(level.getMinBuildHeight() + 1, center.getY() - radius), center.getZ());
    }

    /** Whether a crater centered at {@code center} is still being carved. */
    public static boolean isCarving(ServerLevel level, BlockPos center) {
        for (Job job : JOBS) {
            if (job.level == level && job.center.equals(center)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            if (job.level.getServer() != event.getServer() || job.run()) it.remove();
        }
        SHOCKWAVES.removeIf(wave -> wave.level.getServer() != event.getServer() || wave.run());
    }

    /** A ring of smoke and blasts rolling outward from the core, a little past the crater's edge. */
    private static final class Shockwave {
        final ServerLevel level;
        final Vec3 center;
        final int radius;
        int age;

        Shockwave(ServerLevel level, BlockPos center, int radius) {
            this.level = level;
            this.center = Vec3.atCenterOf(center);
            this.radius = radius;
        }

        boolean run() {
            double r = ++age * 3.0;
            if (r > radius * 1.3) return true;
            int points = (int) Math.min(160, Mth.TWO_PI * r / 1.5);
            for (int i = 0; i < points; i++) {
                double a = Mth.TWO_PI * i / points;
                double x = center.x + Math.cos(a) * r, z = center.z + Math.sin(a) * r;
                if (i % 6 == 0) level.sendParticles(ParticleTypes.EXPLOSION, x, center.y, z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.CLOUD, x, center.y - 1, z, 1, 0.3, 0.6, 0.3, 0.05);
            }
            return false;
        }
    }

    private static final class Job {
        final ServerLevel level;
        final BlockPos center;
        final int radius;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Cursor through the bounding cube, top layer first so things fall into the hole rather than onto it.
        int dy, dx, dz;
        int age, waited;
        final java.util.Set<Long> givenUp = new java.util.HashSet<>(); // chunks that never loaded

        Job(ServerLevel level, BlockPos center, int radius) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            dy = radius;
            dx = dz = -radius;
            loadChunks();
        }

        /**
         * Keeps every chunk the crater touches loaded while it's carved, so it comes out whole wherever the players
         * are (re-requested every few seconds, as the tickets run out on their own).
         */
        void loadChunks() {
            int x0 = (center.getX() - radius) >> 4, x1 = (center.getX() + radius) >> 4;
            int z0 = (center.getZ() - radius) >> 4, z1 = (center.getZ() + radius) >> 4;
            for (int cx = x0; cx <= x1; cx++) {
                for (int cz = z0; cz <= z1; cz++) ChunkTickets.request(level, cx, cz);
            }
        }

        /** Carves the next slice; returns true when the whole sphere is done. */
        boolean run() {
            int budget = BLOCKS_PER_TICK;
            double r2 = (radius + 0.5) * (radius + 0.5);
            if (++age % 100 == 0) loadChunks();
            while (budget-- > 0) {
                if (dy < -radius) return true;
                double d2 = dx * dx + dy * dy + dz * dz;
                // A ragged edge: the outer 2 blocks are only partly eaten, so it looks blasted, not machined.
                double edge = radius - 2 + 2 * noise(dx, dy, dz);
                if (d2 <= r2 && d2 <= (edge + 0.5) * (edge + 0.5)) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.isInWorldBounds(pos) && !level.isLoaded(pos)) {
                        long chunk = ChunkPos.asLong(pos);
                        if (!givenUp.contains(chunk)) {
                            if (++waited < MAX_CHUNK_WAIT) return false; // still loading: carry on from here next tick
                            givenUp.add(chunk);
                        }
                    }
                    waited = 0;
                    if (level.isInWorldBounds(pos) && level.isLoaded(pos)) {
                        BlockState state = level.getBlockState(pos);
                        if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0) { // bedrock and the like stay
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), BLOCK_FLAGS);
                        }
                    }
                }
                if (++dz > radius) {
                    dz = -radius;
                    if (++dx > radius) {
                        dx = -radius;
                        dy--;
                    }
                }
            }
            return false;
        }

        private static double noise(int x, int y, int z) {
            return (Mth.getSeed(x, y, z) >>> 16 & 0xFFFF) / 65535.0;
        }
    }

    private Crater() {}
}
