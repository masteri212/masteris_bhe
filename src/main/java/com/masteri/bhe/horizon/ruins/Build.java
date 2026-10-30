package com.masteri.bhe.horizon.ruins;

import com.masteri.bhe.MasteriBHE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.function.Consumer;

/**
 * Builds one ruin, clipped to one chunk. A ruin crosses several chunks, and each chunk runs the whole builder again
 * with the same seed: the random rolls come out the same every time, and each run only keeps the blocks (and the
 * entities) that fall inside its own chunk. So builders must never read the world to decide anything.
 *
 * Coordinates are relative to the ruin's origin and turned by the ruin's rotation.
 */
public final class Build {
    public final RandomSource random;
    private final WorldGenLevel level;
    private final BoundingBox box;
    private final BlockPos origin;
    private final Rotation rotation;
    private final long seed;

    Build(WorldGenLevel level, BoundingBox box, BlockPos origin, long seed) {
        this.level = level;
        this.box = box;
        this.origin = origin;
        this.seed = seed;
        this.random = new LegacyRandomSource(seed);
        this.rotation = Rotation.values()[(int) Math.floorMod(seed, 4L)];
    }

    // ------------------------------------------------------------------ positions

    public BlockPos pos(int x, int y, int z) {
        return switch (rotation) {
            case NONE -> origin.offset(x, y, z);
            case CLOCKWISE_90 -> origin.offset(-z, y, x);
            case CLOCKWISE_180 -> origin.offset(-x, y, -z);
            case COUNTERCLOCKWISE_90 -> origin.offset(z, y, -x);
        };
    }

    /** Smooth-ish value noise in 0..1 from the ruin's seed (the same in every chunk). */
    public double noise(double x, double z, double scale) {
        double fx = x / scale, fz = z / scale;
        int ix = Mth.floor(fx), iz = Mth.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        double a = hash(ix, iz), b = hash(ix + 1, iz), c = hash(ix, iz + 1), d = hash(ix + 1, iz + 1);
        double sx = tx * tx * (3 - 2 * tx), sz = tz * tz * (3 - 2 * tz);
        return Mth.lerp(sz, Mth.lerp(sx, a, b), Mth.lerp(sx, c, d));
    }

    private double hash(int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    // ------------------------------------------------------------------ blocks

    public void set(int x, int y, int z, BlockState state) {
        BlockPos p = pos(x, y, z);
        if (box.isInside(p)) level.setBlock(p, state.rotate(level, p, rotation), Block.UPDATE_CLIENTS);
    }

    public void set(int x, int y, int z, Block block) {
        set(x, y, z, block.defaultBlockState());
    }

    public void air(int x, int y, int z) {
        set(x, y, z, Blocks.AIR.defaultBlockState());
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, state);
            }
        }
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        fill(x0, y0, z0, x1, y1, z1, block.defaultBlockState());
    }

    /** Hollow box: walls, floor and ceiling of {@code wall}, air inside. */
    public void room(int x0, int y0, int z0, int x1, int y1, int z1, BlockState wall) {
        fill(x0, y0, z0, x1, y1, z1, wall);
        fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, Blocks.AIR.defaultBlockState());
    }

    /**
     * Clears an upright cylinder of anything the world put there (the islands), so the ruin stands in the open
     * rather than buried in rock. Done first; the ruin is built into the space after.
     */
    public void clear(double radius, int yMin, int yMax) {
        int ri = Mth.ceil(radius);
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                if (x * x + z * z > radius * radius) continue;
                for (int y = yMin; y <= yMax; y++) air(x, y, z);
            }
        }
    }

    public void sphere(int cx, int cy, int cz, double r, BlockState state) {
        int ri = Mth.ceil(r);
        for (int x = -ri; x <= ri; x++) {
            for (int y = -ri; y <= ri; y++) {
                for (int z = -ri; z <= ri; z++) {
                    if (x * x + y * y + z * z <= r * r) set(cx + x, cy + y, cz + z, state);
                }
            }
        }
    }

    /**
     * A floating rock: a flat top at {@code top} spreading {@code radius} out (ragged at the edge), tapering below
     * to a point about {@code depth} down. {@code surface} on top, {@code body} under it.
     */
    public void island(int cx, int top, int cz, double radius, double depth, BlockState surface, BlockState body) {
        int ri = Mth.ceil(radius * 1.25);
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double edge = radius * (0.8 + 0.4 * noise(cx + x, cz + z, 4));
                double d = Math.sqrt(x * x + z * z);
                if (d > edge) continue;
                double t = d / edge;
                int down = (int) Math.round(depth * (1 - t * t) * (0.6 + 0.6 * noise(cx + x + 100, cz + z, 3))) + 1;
                for (int y = 0; y < down; y++) set(cx + x, top - y, cz + z, y == 0 ? surface : body);
            }
        }
    }

    /** A flat ring of blocks (a torus around the vertical axis): major radius {@code r}, tube {@code tube}. */
    public void torus(int cx, int cy, int cz, double r, double tube, BlockState state, AngleFilter keep) {
        int ri = Mth.ceil(r + tube), ti = Mth.ceil(tube);
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double d = Math.sqrt(x * x + z * z);
                for (int y = -ti; y <= ti; y++) {
                    if ((d - r) * (d - r) + y * y <= tube * tube && keep.keep(Math.atan2(z, x))) set(cx + x, cy + y, cz + z, state);
                }
            }
        }
    }

    public interface AngleFilter {
        boolean keep(double angle);
    }

    /** A chest filled from one of this mod's chest loot tables when it's first opened. */
    public void chest(int x, int y, int z, Direction facing, String lootTable) {
        BlockPos p = pos(x, y, z);
        if (!box.isInside(p)) return;
        level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, rotation.rotate(facing)), Block.UPDATE_CLIENTS);
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                lootTable.contains(":") ? ResourceLocation.parse(lootTable) : ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "chests/" + lootTable));
        // Its own random, from its spot: rolling the shared one here (only in this chunk) would throw the others off.
        RandomizableContainer.setBlockEntityLootTable(level, new LegacyRandomSource(seed ^ p.asLong()), p, key);
    }

    /** A mob spawner for {@code type}. */
    public void spawner(int x, int y, int z, EntityType<?> type) {
        BlockPos p = pos(x, y, z);
        if (!box.isInside(p)) return;
        level.setBlock(p, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) {
            spawner.setEntityId(type, new LegacyRandomSource(seed ^ p.asLong()));
        }
    }

    /** An entity, placed once (by the chunk its spot is in). Mobs stay put (persistent). */
    public <E extends Entity> void entity(int x, int y, int z, EntityType<E> type, Consumer<E> setup) {
        BlockPos p = pos(x, y, z);
        if (!box.isInside(p)) return;
        E entity = type.create(level.getLevel());
        if (entity == null) return;
        entity.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, (float) hash(p.getX(), p.getZ()) * 360, 0);
        if (entity instanceof Mob mob) mob.setPersistenceRequired();
        setup.accept(entity);
        level.addFreshEntityWithPassengers(entity);
    }
}
