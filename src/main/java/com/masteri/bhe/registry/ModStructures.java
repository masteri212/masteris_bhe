package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.horizon.ruins.RuinPiece;
import com.masteri.bhe.horizon.ruins.RuinStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Event Horizon's ruins: one structure type for all of them (which one is data), and their one piece type. */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, MasteriBHE.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, MasteriBHE.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<RuinStructure>> RUIN = TYPES.register("ruin", () -> () -> RuinStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> RUIN_PIECE = PIECES.register("ruin",
            () -> (StructurePieceType.ContextlessType) RuinPiece::new);

    private ModStructures() {}
}
