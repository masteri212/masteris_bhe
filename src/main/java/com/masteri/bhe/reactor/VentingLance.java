package com.masteri.bhe.reactor;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import com.masteri.bhe.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The Venting Lance: an optional 3x3, 12-tall column of Lance Emitters centered over the core. Its bottom can be
 * anywhere from 3 to 40 blocks above the core (9, six blocks over the top of the ring, is the standard spot). When the console fires it, it dumps the core's charge straight up as a
 * beam that destroys every block and creature in its path, all the way to the top of the world.
 *
 * A normal vent drops the reactor to offline and burns a share of its mass. An emergency vent (meltdown phase 1)
 * fires at full power, burns all the mass and destroys the lance itself.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID)
public final class VentingLance {
    /** Standard bottom of the lance, relative to the core: the ring's top layer is at +2, then 6 blocks of air. */
    public static final int BOTTOM = 9, HEIGHT = 12;
    /** How far above the core the lance's bottom may be. */
    public static final int MIN_BOTTOM = 3, MAX_BOTTOM = 40;
    public static final int BLOCKS = 9 * HEIGHT;
    /** How long the beam fires, in ticks. */
    public static final int BEAM_TICKS = 100;
    private static final int CARVE_SPEED = 12;          // blocks per tick the beam bores upward
    private static final int BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static List<BlockPos> layout;

    /** Offsets of every lance block from its bottom center. */
    public static List<BlockPos> layout() {
        if (layout == null) {
            List<BlockPos> list = new ArrayList<>();
            for (int dy = 0; dy < HEIGHT; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) list.add(new BlockPos(dx, dy, dz));
                }
            }
            layout = Collections.unmodifiableList(list);
        }
        return layout;
    }

    private static boolean isLance(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.LANCE_EMITTER.get());
    }

    /** Lance blocks in a lance whose bottom is {@code bottom} blocks above the core. */
    private static int count(Level level, BlockPos core, int bottom) {
        int n = 0;
        for (BlockPos offset : layout()) {
            if (isLance(level, core.above(bottom).offset(offset))) n++;
        }
        return n;
    }

    /**
     * Finds a complete lance over the core and returns how far above the core its bottom is, or -1 if there's none.
     * The lowest Lance Emitter in the core's column is taken as the bottom.
     */
    public static int find(Level level, BlockPos core) {
        int bottom = lowest(level, core);
        return bottom >= 0 && count(level, core, bottom) == BLOCKS ? bottom : -1;
    }

    /** The lowest Lance Emitter anywhere in the 3x3 column over the core, as a height above it, or -1. */
    private static int lowest(Level level, BlockPos core) {
        for (int dy = MIN_BOTTOM; dy <= MAX_BOTTOM; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (isLance(level, core.offset(dx, dy, dz))) return dy;
                }
            }
        }
        return -1;
    }

    /** What the Configurator reports about the lance: none, complete, or how much is there and the first gap. */
    public record Report(int bottom, int placed, @org.jetbrains.annotations.Nullable BlockPos firstGap) {
        public boolean none() { return bottom < 0; }
        public boolean complete() { return placed == BLOCKS; }
    }

    public static Report diagnose(Level level, BlockPos core) {
        int bottom = lowest(level, core);
        if (bottom < 0) return new Report(-1, 0, null);
        BlockPos gap = null;
        int placed = 0;
        for (BlockPos offset : layout()) {
            BlockPos pos = core.above(bottom).offset(offset);
            if (isLance(level, pos)) placed++;
            else if (gap == null) gap = pos;
        }
        return new Report(bottom, placed, gap);
    }

    /** Creative/testing helper: places the whole lance at the standard height. Returns how many blocks were placed. */
    public static int build(Level level, BlockPos core) {
        int count = 0;
        for (BlockPos o : layout()) {
            BlockPos offset = o.above(BOTTOM);
            BlockPos pos = core.offset(offset);
            if (!level.getBlockState(pos).is(ModBlocks.LANCE_EMITTER.get())) {
                level.setBlock(pos, ModBlocks.LANCE_EMITTER.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                count++;
            }
        }
        return count;
    }

    /** Removes the lance's blocks (wherever it stands). Returns how many were removed. */
    public static int remove(Level level, BlockPos core, boolean blast) {
        int bottom = lowest(level, core);
        if (bottom < 0) return 0;
        int count = 0;
        for (BlockPos offset : layout()) {
            BlockPos pos = core.above(bottom).offset(offset);
            if (level.getBlockState(pos).is(ModBlocks.LANCE_EMITTER.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                count++;
            }
        }
        if (blast && level instanceof ServerLevel server) {
            double x = core.getX() + 0.5, y = core.getY() + bottom + HEIGHT / 2.0, z = core.getZ() + 0.5;
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 3, 1, HEIGHT / 3.0, 1, 0);
            server.sendParticles(ParticleTypes.LAVA, x, y, z, 40, 1.5, HEIGHT / 3.0, 1.5, 0);
            level.playSound(null, core.above(bottom), ModSounds.REACTOR_BREACH.get(), SoundSource.BLOCKS, 6F, 1.2F);
        }
        return count;
    }

    // ------------------------------------------------------------------ the beam

    private static final List<Beam> BEAMS = new ArrayList<>();

    /**
     * Fires the lance over {@code core}, whose top is {@code top} blocks above it. An emergency vent is twice as wide
     * and destroys the lance when it's done.
     */
    public static void fire(ServerLevel level, BlockPos core, int lanceTop, boolean emergency) {
        BEAMS.add(new Beam(level, core, lanceTop, emergency));
        BlockPos top = core.above(lanceTop + 1);
        level.playSound(null, top, ModSounds.REACTOR_VENT_RELEASE.get(), SoundSource.BLOCKS, 10F, 1.0F);
        level.sendParticles(ParticleTypes.FLASH, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 1, 0, 0, 0, 0);
    }

    /** How long the beam fires: an emergency vent fights the meltdown for longer. */
    public static int beamTicks(boolean emergency) {
        return emergency ? EmergencyVent.BEAM_TICKS : BEAM_TICKS;
    }

    /** Radius of the beam (blocks it destroys around the lance's axis). The renderer draws the same width. */
    public static int beamRadius(boolean emergency) {
        return emergency ? 2 : 1;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Beam> it = BEAMS.iterator();
        while (it.hasNext()) {
            Beam beam = it.next();
            if (beam.level.getServer() != event.getServer() || beam.run()) it.remove();
        }
    }

    private static final class Beam {
        final ServerLevel level;
        final BlockPos core;
        final boolean emergency;
        final int radius, lanceTop;
        int age, carvedTo;

        Beam(ServerLevel level, BlockPos core, int lanceTop, boolean emergency) {
            this.level = level;
            this.core = core;
            this.lanceTop = lanceTop;
            this.emergency = emergency;
            this.radius = beamRadius(emergency);
            this.carvedTo = core.getY() + lanceTop + 1;
        }

        /** One tick of the beam; returns true when it's done. */
        boolean run() {
            age++;
            int top = level.getMaxBuildHeight();
            int bottom = core.getY() + lanceTop + 1;
            // Bore upward through everything in the way.
            if (ReactorConfig.DESTROY_BLOCKS.get() && carvedTo < top) {
                int to = Math.min(top, carvedTo + CARVE_SPEED);
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int y = carvedTo; y < to; y++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (dx * dx + dz * dz > radius * radius + 1) continue;
                            pos.set(core.getX() + dx, y, core.getZ() + dz);
                            if (!level.isLoaded(pos)) continue;
                            BlockState state = level.getBlockState(pos);
                            if (state.isAir() || state.getDestroySpeed(level, pos) < 0) continue;
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), BLOCK_FLAGS);
                            if (level.random.nextInt(6) == 0) {
                                level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0);
                            }
                        }
                    }
                }
                carvedTo = to;
            }
            // Anything caught in the beam is destroyed.
            AABB column = new AABB(core.getX() - radius, bottom, core.getZ() - radius,
                    core.getX() + radius + 1, top, core.getZ() + radius + 1);
            for (Entity e : level.getEntitiesOfClass(Entity.class, column, e -> e instanceof LivingEntity || e instanceof ItemEntity)) {
                if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                if (e instanceof LivingEntity living) {
                    living.hurt(level.damageSources().fellOutOfWorld(), emergency ? 1000F : 60F);
                    living.push(0, 1.5, 0);
                    living.hurtMarked = true;
                } else {
                    e.discard();
                }
            }
            if (age % 4 == 0) {
                double x = core.getX() + 0.5, z = core.getZ() + 0.5;
                level.sendParticles(ParticleTypes.END_ROD, x, bottom + 2, z, 20, radius * 0.6, 1, radius * 0.6, 0.6);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, bottom, z, 15, 1.5, 0.3, 1.5, 0.4);
            }
            if (age % 8 == 0) level.playSound(null, core.above(lanceTop + 1), ModSounds.REACTOR_ARC.get(), SoundSource.BLOCKS, 4F, 0.6F + level.random.nextFloat() * 0.2F);
            if (age >= beamTicks(emergency)) {
                if (emergency) remove(level, core, true); // the emergency vent burns the lance out
                return true;
            }
            return false;
        }
    }

    private VentingLance() {}
}
