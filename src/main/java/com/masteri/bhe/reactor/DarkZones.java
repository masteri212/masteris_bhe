package com.masteri.bhe.reactor;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.config.ReactorConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dark energy zones left behind by reactor collapses: a sphere of the configured radius (500 blocks by default)
 * around each crater, saved per dimension and sent to the players in it so the client can draw the zone's
 * "inside the black hole" screen shader. The zone never shrinks on its own: each unstable ore node stabilized
 * inside it (see {@link #stabilize}) takes a slice off the radius, and the last one removes it.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID)
public final class DarkZones extends SavedData {
    private static final String NAME = MasteriBHE.MODID + "_dark_zones";

    /** {@code nodes} is how many more ore nodes must be stabilized before the zone is gone. */
    public record Zone(BlockPos center, int radius, int nodes) {
        public static final StreamCodec<ByteBuf, Zone> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Zone::center, ByteBufCodecs.VAR_INT, Zone::radius, ByteBufCodecs.VAR_INT, Zone::nodes,
                Zone::new);

        public boolean contains(double x, double y, double z) {
            return distance(center, x, y, z) <= radius;
        }

        /** Whether the zone reaches into the 16x16 column of the given chunk (ignoring height). */
        public boolean touchesChunk(int chunkX, int chunkZ) {
            int minX = chunkX << 4, minZ = chunkZ << 4;
            long dx = Math.max(0, Math.max(minX - center.getX(), center.getX() - (minX + 15)));
            long dz = Math.max(0, Math.max(minZ - center.getZ(), center.getZ() - (minZ + 15)));
            return dx * dx + dz * dz <= (long) radius * radius;
        }
    }

    /**
     * How far a point is from a zone's middle. A zone is a sphere, centered on the bottom of the crater that made it
     * (see {@link Crater#floor}), so however small it gets, it still takes in the crater floor, the ground around
     * it and its ore.
     */
    public static double distance(BlockPos center, double x, double y, double z) {
        return Math.sqrt(center.distToCenterSqr(x, y, z));
    }

    /** What stabilizing a node did: the zone shrank to {@code zone}, or (if {@code zone} is null) was removed. */
    public record Stabilized(Zone before, @Nullable Zone zone) {}

    /**
     * Every zone in the player's current dimension. Sent on login, dimension change, respawn and whenever zones change.
     * {@code animate} is true when zones actually changed (born, shrunk, closed), so the client plays the animation;
     * false when the player just arrived and should see things as they are.
     */
    public record SyncPayload(List<Zone> zones, boolean animate) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "dark_zones"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.composite(
                Zone.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncPayload::zones, ByteBufCodecs.BOOL, SyncPayload::animate,
                SyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private final List<Zone> zones = new ArrayList<>();
    /** How far ZoneTerrain's chunk loader has got through each zone, nearest chunk first (by its center). */
    private final java.util.Map<BlockPos, Integer> terrainCursors = new java.util.HashMap<>();

    public static DarkZones get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(DarkZones::new, DarkZones::load), NAME);
    }

    public List<Zone> zones() {
        return Collections.unmodifiableList(zones);
    }

    /**
     * Zone radius for a collapse holding {@code mass}: the configured radius at the ignition minimum (all four
     * stabilizers at their minimum), scaling with the square root of the mass, between the configured limits.
     */
    public static int radiusForMass(long mass) {
        double reference = 4.0 * ReactorConfig.MIN_MASS_PER_STABILIZER.get();
        double scale = reference <= 0 ? 1 : Math.sqrt(Math.max(0, mass) / reference);
        int min = ReactorConfig.ZONE_MIN_RADIUS.get(), max = Math.max(min, ReactorConfig.ZONE_MAX_RADIUS.get());
        return (int) Math.round(Math.max(min, Math.min(max, ReactorConfig.ZONE_RADIUS.get() * scale)));
    }

    public void add(ServerLevel level, BlockPos center, int radius) {
        zones.add(new Zone(center.immutable(), radius, ReactorConfig.ZONE_NODES.get()));
        terrainCursors.remove(center); // a new zone here starts its terrain over
        setDirty();
        sync(level);
    }

    public int terrainCursor(BlockPos center) {
        return terrainCursors.getOrDefault(center, 0);
    }

    public void setTerrainCursor(BlockPos center, int cursor) {
        terrainCursors.put(center.immutable(), cursor);
        setDirty();
    }

    /** The zone whose center is closest to the point, among those containing it, or null outside every zone. */
    @Nullable
    public Zone at(double x, double y, double z) {
        Zone best = null;
        for (Zone zone : zones) {
            if (!zone.contains(x, y, z)) continue;
            if (best == null || distance(zone.center, x, y, z) < distance(best.center, x, y, z)) best = zone;
        }
        return best;
    }

    @Nullable
    public Zone at(BlockPos pos) {
        return at(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** Whether the point is inside any zone in this level. Cheap when there are none. */
    public static boolean inZone(ServerLevel level, double x, double y, double z) {
        DarkZones data = get(level);
        return !data.zones.isEmpty() && data.at(x, y, z) != null;
    }

    /**
     * One ore node at {@code pos} was stabilized: the zone around it loses an equal share of its radius per node
     * left, and is removed with the last one. Returns null if {@code pos} isn't in a zone.
     */
    @Nullable
    public Stabilized stabilize(ServerLevel level, BlockPos pos) {
        Zone zone = at(pos);
        if (zone == null) return null;
        int index = zones.indexOf(zone);
        Zone after = null;
        if (zone.nodes > 1) {
            int radius = Math.max(1, zone.radius - zone.radius / zone.nodes);
            after = new Zone(zone.center, radius, zone.nodes - 1);
            zones.set(index, after);
        } else {
            zones.remove(index);
        }
        setDirty();
        sync(level);
        return new Stabilized(zone, after);
    }

    /** Removes the zone whose center is closest to {@code pos}, if {@code pos} is inside it. Returns the removed zone. */
    @Nullable
    public Zone removeAt(ServerLevel level, BlockPos pos) {
        Zone best = at(pos);
        if (best != null) {
            zones.remove(best);
            setDirty();
            sync(level);
        }
        return best;
    }

    // ------------------------------------------------------------------ syncing

    private static void sync(ServerLevel level) {
        PacketDistributor.sendToPlayersInDimension(level, new SyncPayload(List.copyOf(get(level).zones), true));
    }

    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncPayload(List.copyOf(get(player.serverLevel()).zones), false));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    /** Mod bus: registers the sync packet. The client side just stores the list (see ClientZones). */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientZones.set(payload.zones(), payload.animate())));
    }

    // ------------------------------------------------------------------ saving

    private static DarkZones load(CompoundTag tag, HolderLookup.Provider registries) {
        DarkZones data = new DarkZones();
        for (Tag t : tag.getList("Zones", Tag.TAG_COMPOUND)) {
            CompoundTag z = (CompoundTag) t;
            int nodes = z.contains("Nodes") ? z.getInt("Nodes") : ReactorConfig.ZONE_NODES.get();
            NbtUtils.readBlockPos(z, "Center").ifPresent(c -> {
                data.zones.add(new Zone(c, z.getInt("Radius"), Math.max(1, nodes)));
                if (z.contains("TerrainCursor")) data.terrainCursors.put(c, z.getInt("TerrainCursor"));
            });
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Zone zone : zones) {
            CompoundTag z = new CompoundTag();
            z.put("Center", NbtUtils.writeBlockPos(zone.center));
            z.putInt("Radius", zone.radius);
            z.putInt("Nodes", zone.nodes);
            z.putInt("TerrainCursor", terrainCursors.getOrDefault(zone.center, 0));
            list.add(z);
        }
        tag.put("Zones", list);
        return tag;
    }

    /**
     * The client's copy of the zones in its current dimension. Plain data, safe to load on a dedicated server
     * (the packet handler is registered on both sides but only ever runs on the client).
     */
    public static final class ClientZones {
        /** What a zone is doing on screen right now. */
        public enum Phase { STEADY, BIRTH, RESIZE, CLOSING }

        // Animation lengths (milliseconds). Closing: the zone shrinks for most of it, then implodes at the end.
        public static final long BIRTH_MS = 10_000, RESIZE_MS = 2_500, CLOSING_MS = 7_000;
        public static final float CLOSING_SHRINK = 0.85F;

        /**
         * One zone as it should be drawn: {@code radius} is the animated radius, {@code progress} is 0..1 through
         * {@code phase}, and {@code started} identifies the animation (for one-off sounds and shakes).
         */
        public record View(BlockPos center, float radius, Phase phase, float progress, long started) {
            public boolean contains(double x, double y, double z) {
                return distance(center, x, y, z) <= radius;
            }
        }

        private static final class Entry {
            final BlockPos center;
            float from, to;
            long start, duration;
            Phase phase;

            Entry(BlockPos center, float from, float to, Phase phase, long duration) {
                this.center = center;
                this.from = from;
                this.to = to;
                this.phase = phase;
                this.duration = duration;
                this.start = System.currentTimeMillis();
            }

            float progress(long now) {
                return phase == Phase.STEADY ? 1 : Math.min(1, (now - start) / (float) duration);
            }

            float radius(long now) {
                float p = progress(now);
                float t = switch (phase) {
                    case STEADY -> 1;
                    case BIRTH -> 1 - (1 - p) * (1 - p) * (1 - p);                      // bursts out, then slows
                    case RESIZE -> p * p * (3 - 2 * p);                                 // smooth in and out
                    case CLOSING -> { float q = Math.min(1, p / CLOSING_SHRINK); yield q * q * q; } // slow, then rushing in
                };
                return from + (to - from) * t;
            }
        }

        private static final java.util.Map<BlockPos, Entry> ENTRIES = new java.util.LinkedHashMap<>();
        private static volatile List<Zone> zones = List.of();

        /** The zones as the server has them (no animation). */
        public static List<Zone> get() {
            return zones;
        }

        public static synchronized void set(List<Zone> list, boolean animate) {
            zones = List.copyOf(list);
            long now = System.currentTimeMillis();
            java.util.Set<BlockPos> present = new java.util.HashSet<>();
            for (Zone zone : list) {
                present.add(zone.center);
                Entry e = ENTRIES.get(zone.center);
                if (e == null) {
                    ENTRIES.put(zone.center, animate
                            ? new Entry(zone.center, 0, zone.radius, Phase.BIRTH, BIRTH_MS)
                            : new Entry(zone.center, zone.radius, zone.radius, Phase.STEADY, 0));
                } else if (e.phase == Phase.CLOSING || e.to != zone.radius) {
                    float current = e.radius(now);
                    ENTRIES.put(zone.center, animate
                            ? new Entry(zone.center, current, zone.radius, Phase.RESIZE, RESIZE_MS)
                            : new Entry(zone.center, zone.radius, zone.radius, Phase.STEADY, 0));
                }
            }
            ENTRIES.entrySet().removeIf(en -> {
                if (present.contains(en.getKey())) return false;
                if (!animate) return true;
                Entry e = en.getValue();
                if (e.phase != Phase.CLOSING) en.setValue(new Entry(e.center, e.radius(now), 0, Phase.CLOSING, CLOSING_MS));
                return false;
            });
        }

        /** Every zone to draw, animated. Finished animations settle (or, for a closed zone, disappear). */
        public static synchronized List<View> views() {
            if (ENTRIES.isEmpty()) return List.of();
            long now = System.currentTimeMillis();
            List<View> out = new ArrayList<>(ENTRIES.size());
            java.util.Iterator<Entry> it = ENTRIES.values().iterator();
            while (it.hasNext()) {
                Entry e = it.next();
                float p = e.progress(now);
                if (p >= 1 && e.phase == Phase.CLOSING) {
                    it.remove();
                    continue;
                }
                if (p >= 1 && e.phase != Phase.STEADY) {
                    e.phase = Phase.STEADY;
                    e.from = e.to;
                }
                out.add(new View(e.center, e.radius(now), e.phase, e.progress(now), e.start));
            }
            return out;
        }

        public static synchronized void clear() {
            zones = List.of();
            ENTRIES.clear();
        }

        private ClientZones() {}
    }
}
