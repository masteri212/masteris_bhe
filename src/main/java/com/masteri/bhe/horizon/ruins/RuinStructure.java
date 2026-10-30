package com.masteri.bhe.horizon.ruins;

import com.masteri.bhe.registry.ModStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * One of the Event Horizon's ruins (which one is {@code kind}), floating at a height between {@code min_y} and
 * {@code max_y} over the middle of its chunk. Data: worldgen/structure, placed by worldgen/structure_set.
 */
public class RuinStructure extends Structure {
    public static final MapCodec<RuinStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Ruin.CODEC.fieldOf("kind").forGetter(s -> s.kind),
            Codec.INT.fieldOf("min_y").forGetter(s -> s.minY),
            Codec.INT.fieldOf("max_y").forGetter(s -> s.maxY)
    ).apply(i, RuinStructure::new));

    private final Ruin kind;
    private final int minY, maxY;

    public RuinStructure(StructureSettings settings, Ruin kind, int minY, int maxY) {
        super(settings);
        this.kind = kind;
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int y = minY + context.random().nextInt(Math.max(1, maxY - minY + 1));
        BlockPos origin = new BlockPos(context.chunkPos().getMiddleBlockX(), y, context.chunkPos().getMiddleBlockZ());
        long seed = context.random().nextLong();
        return Optional.of(new GenerationStub(origin, pieces -> pieces.addPiece(new RuinPiece(kind, origin, seed))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.RUIN.get();
    }
}
