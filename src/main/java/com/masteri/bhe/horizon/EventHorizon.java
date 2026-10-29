package com.masteri.bhe.horizon;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.registry.ModAttachments;
import com.masteri.bhe.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Event Horizon: the dimension inside a black hole. Debris islands float over the singularity at the bottom;
 * gravity rises and time runs faster the deeper you go, and below the spaghettification line the singularity tears
 * everything apart. The world itself is data (dimension, noise settings, biomes); this is the travel and the rules
 * that change with depth (applied in HorizonEffects).
 */
public final class EventHorizon {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, id("event_horizon"));
    public static final ResourceKey<DamageType> SPAGHETTIFIED = ResourceKey.create(Registries.DAMAGE_TYPE, id("spaghettification"));

    /** Height of the world's top, where gravity is weakest. */
    public static final int TOP = 330;
    /** Where gates set players down: in the debris drift, the band with the most islands. */
    public static final int ARRIVAL_Y = 190;
    /** Time starts running faster below this height, and is fastest from {@link #DEEP_FULL} down. */
    public static final int DEEP_START = 150, DEEP_FULL = 60;

    public static boolean isHorizon(Level level) {
        return level.dimension() == LEVEL;
    }

    public static int spaghettificationY() {
        return ReactorConfig.SPAGHETTIFICATION_Y.get();
    }

    /** Gravity multiplier at a height: weak at the top, rising steeply toward the singularity. */
    public static double gravityAt(double y) {
        double top = ReactorConfig.HORIZON_GRAVITY_TOP.get(), bottom = ReactorConfig.HORIZON_GRAVITY_BOTTOM.get();
        double t = Mth.clamp((TOP - y) / (TOP - spaghettificationY()), 0, 1);
        return Mth.lerp(t * t, top, bottom);
    }

    /** How many times faster time runs at a height (1 above the deep). */
    public static double dilationAt(double y) {
        double t = Mth.clamp((DEEP_START - y) / (DEEP_START - DEEP_FULL), 0, 1);
        return 1 + (ReactorConfig.TIME_DILATION.get() - 1) * t;
    }

    public static DamageSource spaghettified(Level level, @Nullable Entity by) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SPAGHETTIFIED), by);
    }

    // ------------------------------------------------------------------ travel

    /**
     * Sends a player through the ring on {@code gate} into the Event Horizon, onto an arrival platform straight
     * "below" it (same x and z) with a Horizon Ring of its own to get back out. Remembers the gate to come back to.
     */
    public static boolean enter(ServerPlayer player, BlockPos gate) {
        ServerLevel horizon = player.server.getLevel(LEVEL);
        if (horizon == null) {
            player.displayClientMessage(Component.translatable(key("missing")).withStyle(ChatFormatting.RED), true);
            return false;
        }
        player.setData(ModAttachments.HORIZON_RETURN, GlobalPos.of(player.level().dimension(), gate.immutable()));
        BlockPos arrival = new BlockPos(gate.getX(), ARRIVAL_Y, gate.getZ());
        buildArrival(horizon, arrival);
        // Facing the ring (north of the landing spot).
        player.changeDimension(new DimensionTransition(horizon, Vec3.atBottomCenterOf(arrival), Vec3.ZERO, 180F, 0F,
                DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET)));
        player.displayClientMessage(Component.translatable(key("entered")).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    /**
     * Takes a player out of the Event Horizon, back to the ring they came in through (in front of it), or to the
     * world spawn when that's gone or they never used one.
     */
    public static void leave(ServerPlayer player) {
        ServerLevel target = null;
        BlockPos at = null;
        float yaw = player.getYRot();
        if (player.hasData(ModAttachments.HORIZON_RETURN)) {
            GlobalPos back = player.getData(ModAttachments.HORIZON_RETURN);
            target = player.server.getLevel(back.dimension());
            if (target != null && !isHorizon(target)) {
                at = inFront(target, back.pos());
                Direction.Axis axis = HorizonRing.find(target, back.pos());
                if (axis != null) yaw = HorizonRing.normal(axis).getOpposite().toYRot(); // looking back at the ring
            }
        }
        if (at == null) {
            target = player.server.overworld();
            BlockPos spawn = target.getSharedSpawnPos();
            target.getChunk(spawn);
            at = target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        }
        player.changeDimension(new DimensionTransition(target, Vec3.atBottomCenterOf(at), Vec3.ZERO, yaw, 0F,
                DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET)));
    }

    /**
     * A spot to stand by the gate: a few blocks out in front of its ring (either side) with ground under it, else
     * next to the gate, else on top of it.
     */
    private static BlockPos inFront(ServerLevel level, BlockPos gate) {
        level.getChunk(gate);
        Direction.Axis axis = HorizonRing.find(level, gate);
        if (axis != null) {
            Direction n = HorizonRing.normal(axis);
            for (Direction side : new Direction[] {n, n.getOpposite()}) {
                for (int d = 3; d >= 1; d--) {
                    BlockPos p = gate.relative(side, d);
                    if (standable(level, p)) return p;
                }
            }
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = gate.relative(dir);
            if (standable(level, p)) return p;
        }
        return gate.above();
    }

    private static boolean standable(ServerLevel level, BlockPos p) {
        return free(level, p) && free(level, p.above()) && !free(level, p.below());
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The landing spot: a Corrupted Stone floor (only where there's nothing to stand on) with room around it, and
     * a Horizon Ring standing four blocks north, facing it. Built once; later trips through the same gate land on
     * it again.
     */
    private static void buildArrival(ServerLevel level, BlockPos center) {
        BlockPos gate = center.north(4);
        level.getChunk(center);
        if (level.getBlockState(gate).is(ModBlocks.HORIZON_GATE.get()) && HorizonRing.find(level, gate) != null) return;
        BlockState floor = ModBlocks.CORRUPTED_STONE.get().defaultBlockState();
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -6; dz <= 2; dz++) {
                BlockPos below = center.offset(dx, -1, dz);
                if (free(level, below)) level.setBlock(below, floor, Block.UPDATE_ALL);
                for (int dy = 0; dy < 10; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, Block.UPDATE_ALL);
                }
            }
        }
        HorizonRing.build(level, gate, Direction.Axis.X);
    }

    public static String key(String name) {
        return "message." + MasteriBHE.MODID + ".horizon." + name;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, path);
    }

    private EventHorizon() {}
}
