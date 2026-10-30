package com.masteri.bhe.horizon.ruins;

import com.masteri.bhe.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** The single piece of a ruin: what it is, where, and its seed. Each chunk it covers builds its own share. */
public class RuinPiece extends StructurePiece {
    private final Ruin kind;
    private final BlockPos origin;
    private final long seed;

    public RuinPiece(Ruin kind, BlockPos origin, long seed) {
        super(ModStructures.RUIN_PIECE.get(), 0, box(kind, origin));
        this.kind = kind;
        this.origin = origin;
        this.seed = seed;
    }

    public RuinPiece(CompoundTag tag) {
        super(ModStructures.RUIN_PIECE.get(), tag);
        Ruin kind = Ruin.FROZEN_REACTOR;
        for (Ruin r : Ruin.values()) {
            if (r.getSerializedName().equals(tag.getString("Kind"))) kind = r;
        }
        this.kind = kind;
        this.origin = NbtUtils.readBlockPos(tag, "Origin").orElse(BlockPos.ZERO);
        this.seed = tag.getLong("Seed");
    }

    private static BoundingBox box(Ruin kind, BlockPos o) {
        return new BoundingBox(o.getX() - kind.radius, o.getY() - kind.below, o.getZ() - kind.radius,
                o.getX() + kind.radius, o.getY() + kind.above, o.getZ() + kind.radius);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Kind", kind.getSerializedName());
        tag.put("Origin", NbtUtils.writeBlockPos(origin));
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        kind.builder.accept(new Build(level, chunkBox, origin, seed));
    }
}
