package com.masteri.bhe.reactor;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.registry.ModAttachments;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Corrupts the terrain inside dark energy zones: the exposed natural ground (the {@code zone_convertible} block tag:
 * stone, dirt, sand, gravel...) turns into Corrupted Stone a few blocks deep, with Unstable Dark Ore nodes scattered
 * on top, denser on the crater floor. A 500-block zone is far too big to do at once, and most of it isn't loaded,
 * so it goes a few chunks per tick: the loaded chunks right after the crater is carved, then the zone loads (or
 * generates) the rest itself, nearest first, so all of it is converted (see loadZones). Each converted chunk is marked (a chunk attachment) so it's only done once. Off when the server config
 * disables collapse block damage.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID)
public final class ZoneTerrain {
    public static final TagKey<Block> CONVERTIBLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "zone_convertible"));
    private static final int CHUNKS_PER_TICK = 4;
    private static final int DEPTH = 3;
    private static final int CRATER_ORE_BONUS = 5;
    private static final int BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE; // no neighbor updates

    /** Collapses waiting for their crater to finish carving. */
    private static final List<Collapse> COLLAPSES = new ArrayList<>();
    /** Chunks that just loaded; filled by the chunk load event, drained on the server tick. */
    private static final Queue<ChunkRef> LOADED = new ConcurrentLinkedQueue<>();
    private static final Deque<ChunkRef> QUEUE = new ArrayDeque<>();

    private record Collapse(ServerLevel level, BlockPos center, int craterRadius) {}

    /** {@code craterRadius} is 0 except for the chunks queued right after a collapse. */
    private record ChunkRef(ServerLevel level, int x, int z, int craterRadius) {}

    /**
     * Called right after a collapse has made its crater and zone: once the crater is fully carved, leaves the crater
     * loot near the bottom and starts converting the zone's loaded chunks, nearest first.
     */
    public static void collapse(ServerLevel level, BlockPos center, int craterRadius) {
        COLLAPSES.add(new Collapse(level, center.immutable(), craterRadius));
    }

    /** Queues the zone's currently loaded chunks for conversion, nearest the center first. */
    public static void queueLoadedChunks(ServerLevel level, DarkZones.Zone zone, int craterRadius) {
        if (!ReactorConfig.DESTROY_BLOCKS.get()) return;
        int cx = zone.center().getX() >> 4, cz = zone.center().getZ() >> 4, r = (zone.falloutRadius() >> 4) + 1;
        List<ChunkRef> refs = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (zone.falloutTouchesChunk(cx + dx, cz + dz) && level.hasChunk(cx + dx, cz + dz)) {
                    refs.add(new ChunkRef(level, cx + dx, cz + dz, craterRadius));
                }
            }
        }
        refs.sort(Comparator.comparingInt(ref -> (ref.x - cx) * (ref.x - cx) + (ref.z - cz) * (ref.z - cz)));
        QUEUE.addAll(refs);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            LOADED.add(new ChunkRef(level, chunk.getPos().x, chunk.getPos().z, 0));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Iterator<Collapse> it = COLLAPSES.iterator();
        while (it.hasNext()) {
            Collapse c = it.next();
            if (c.level.getServer() != server) {
                it.remove();
            } else if (!Crater.isCarving(c.level, c.center)) {
                it.remove();
                craterLoot(c);
                DarkZones.Zone zone = DarkZones.get(c.level).at(c.center);
                if (zone != null) queueLoadedChunks(c.level, zone, c.craterRadius);
            }
        }

        boolean enabled = ReactorConfig.DESTROY_BLOCKS.get();
        if (enabled) {
            for (ServerLevel level : server.getAllLevels()) loadZones(level);
        }
        ChunkRef loaded;
        while ((loaded = LOADED.poll()) != null) {
            // Just loaded: to the front, so it's converted before its load ticket (if it was ours) runs out.
            if (enabled && loaded.level.getServer() == server && touchesZone(loaded)) QUEUE.addFirst(loaded);
        }
        if (!enabled) {
            QUEUE.clear();
            return;
        }
        for (int done = 0; done < CHUNKS_PER_TICK && !QUEUE.isEmpty(); ) {
            ChunkRef ref = QUEUE.poll();
            if (ref.level.getServer() == server && convert(ref)) done++;
        }
    }

    // ------------------------------------------------------------------ loading the whole zone

    /** Chunks looked at per tick that turn out to be loaded already (those cost no load, just a check). */
    private static final int CHECKS_PER_TICK = 16;

    /**
     * Works through each zone's chunks, nearest the center first, loading (or generating) the ones that aren't
     * loaded so they get converted too, a few per tick (see the config). Already loaded ones are queued directly.
     * A zone whose crater is still being carved waits; the progress is saved with the zone.
     */
    private static void loadZones(ServerLevel level) {
        int loads = ReactorConfig.ZONE_CHUNKS_PER_TICK.get();
        if (loads <= 0) return;
        DarkZones data = DarkZones.get(level);
        if (data.zones().isEmpty()) return;
        int checks = CHECKS_PER_TICK;
        for (DarkZones.Zone zone : data.zones()) {
            if (loads <= 0 || checks <= 0) return;
            if (waitingForCrater(level, zone.center())) continue;
            int cursor = data.terrainCursor(zone.center());
            int reach = (zone.falloutRadius() >> 4) + 2; // in chunks: only the fallout is converted
            int[] offsets = chunkOffsets(reach);
            int cx = zone.center().getX() >> 4, cz = zone.center().getZ() >> 4;
            int start = cursor;
            while (cursor < offsets.length && loads > 0 && checks-- > 0) {
                int packed = offsets[cursor];
                int x = cx + (short) (packed >> 16), z = cz + (short) packed;
                if (chunkDist2[cursor] > reach * reach) {
                    cursor = Integer.MAX_VALUE; // past the edge: done
                    break;
                }
                cursor++;
                if (!zone.falloutTouchesChunk(x, z)) continue;
                if (level.hasChunk(x, z)) {
                    QUEUE.add(new ChunkRef(level, x, z, 0)); // skipped there if it's already converted
                } else {
                    ChunkTickets.request(level, x, z); // the load event queues it
                    loads--;
                }
            }
            if (cursor != start) data.setTerrainCursor(zone.center(), cursor);
        }
    }

    /** Whether the crater over this zone (same column: the zone sits on its floor) is still being carved. */
    private static boolean waitingForCrater(ServerLevel level, BlockPos center) {
        for (Collapse c : COLLAPSES) {
            if (c.level == level && c.center.getX() == center.getX() && c.center.getZ() == center.getZ()) return true;
        }
        return false;
    }

    // Chunk offsets around a zone's center, nearest first (packed dx << 16 | dz as shorts), and their squared
    // distances. Shared by every zone, grown when a bigger zone needs it.
    private static int[] chunkOffsets = new int[0], chunkDist2 = new int[0];
    private static int chunkReach = -1;

    private static int[] chunkOffsets(int reach) {
        if (reach <= chunkReach) return chunkOffsets;
        int r = Math.min(reach, 4000);
        long[] keys = new long[(2 * r + 1) * (2 * r + 1)];
        int n = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long d2 = (long) dx * dx + (long) dz * dz;
                if (d2 > (long) r * r) continue;
                keys[n++] = d2 << 32 | ((dx & 0xFFFFL) << 16) | (dz & 0xFFFFL);
            }
        }
        long[] sorted = java.util.Arrays.copyOf(keys, n);
        java.util.Arrays.sort(sorted);
        int[] offsets = new int[n], dist2 = new int[n];
        for (int i = 0; i < n; i++) {
            offsets[i] = (int) (sorted[i] & 0xFFFFFFFFL);
            dist2[i] = (int) (sorted[i] >>> 32);
        }
        chunkDist2 = dist2;
        chunkOffsets = offsets;
        chunkReach = r;
        return offsets;
    }

    private static boolean touchesZone(ChunkRef ref) {
        for (DarkZones.Zone zone : DarkZones.get(ref.level).zones()) {
            if (zone.falloutTouchesChunk(ref.x, ref.z)) return true;
        }
        return false;
    }

    /** Converts one chunk's surface; returns false if there was nothing to do (unloaded or already converted). */
    private static boolean convert(ChunkRef ref) {
        ServerLevel level = ref.level;
        if (!level.hasChunk(ref.x, ref.z)) return false; // unloaded again; it's queued once more when it loads
        LevelChunk chunk = level.getChunk(ref.x, ref.z);
        if (chunk.getData(ModAttachments.ZONE_CONVERTED)) return false;
        DarkZones zones = DarkZones.get(level);
        RandomSource random = level.random;
        double oreChance = ReactorConfig.ORE_CHANCE.get();
        BlockState stone = ModBlocks.CORRUPTED_STONE.get().defaultBlockState();
        BlockState ore = ModBlocks.UNSTABLE_DARK_ORE.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int crater2 = (ref.craterRadius + 2) * (ref.craterRadius + 2);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = chunk.getPos().getMinBlockX() + lx, z = chunk.getPos().getMinBlockZ() + lz;
                int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
                DarkZones.Zone zone = zones.at(x + 0.5, top + 0.5, z + 0.5);
                if (zone == null || !zone.inFallout(x + 0.5, top + 0.5, z + 0.5)) continue; // past it: just low gravity
                int dx = x - zone.center().getX(), dz = z - zone.center().getZ();
                boolean crater = ref.craterRadius > 0 && dx * dx + dz * dz <= crater2;
                boolean placeOre = random.nextDouble() < (crater ? oreChance * CRATER_ORE_BONUS : oreChance);

                for (int y = top; y > top - DEPTH && y >= level.getMinBuildHeight(); y--) {
                    pos.set(x, y, z);
                    // Water, trees, ores and anything built stop the column; only bare ground turns.
                    if (!chunk.getBlockState(pos).is(CONVERTIBLE)) break;
                    level.setBlock(pos, placeOre && y == top ? ore : stone, BLOCK_FLAGS);
                }
                // Grass, flowers and the like can't stay on corrupted stone (two blocks up for tall plants).
                for (int y = top + 1; y <= top + 2; y++) {
                    pos.set(x, y, z);
                    BlockState above = chunk.getBlockState(pos);
                    if (above.isAir() || !above.getFluidState().isEmpty() || above.canSurvive(level, pos)) break;
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), BLOCK_FLAGS);
                }
            }
        }
        chunk.setData(ModAttachments.ZONE_CONVERTED, true);
        return true;
    }

    /** A few Singularity Glass and Event Horizon Fragments at the bottom of the crater. They never despawn. */
    private static void craterLoot(Collapse c) {
        RandomSource random = c.level.random;
        int spread = Mth.clamp(c.craterRadius / 4, 2, 8);
        drop(c, ModItems.SINGULARITY_GLASS.get(), 3 + random.nextInt(3), spread);
        drop(c, ModItems.EVENT_HORIZON_FRAGMENT.get(), 1 + random.nextInt(2), spread);
    }

    private static void drop(Collapse c, Item item, int count, int spread) {
        RandomSource random = c.level.random;
        for (int i = 0; i < count; i++) {
            int x = c.center.getX() + random.nextInt(spread * 2 + 1) - spread;
            int z = c.center.getZ() + random.nextInt(spread * 2 + 1) - spread;
            int y = c.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            ItemEntity entity = new ItemEntity(c.level, x + 0.5, y + 0.2, z + 0.5, new ItemStack(item), 0, 0.1, 0);
            entity.setUnlimitedLifetime();
            entity.setGlowingTag(true);
            c.level.addFreshEntity(entity);
        }
    }

    private ZoneTerrain() {}
}
