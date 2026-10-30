package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.entity.BendingLaserBlockEntity;
import com.masteri.bhe.block.entity.BlackHoleBombBlockEntity;
import com.masteri.bhe.block.entity.ControlConsoleBlockEntity;
import com.masteri.bhe.block.entity.GravitonWellBlockEntity;
import com.masteri.bhe.block.entity.HawkingCellBlockEntity;
import com.masteri.bhe.block.entity.HorizonGateBlockEntity;
import com.masteri.bhe.block.entity.ResearchCoreBlockEntity;
import com.masteri.bhe.block.entity.MassInputPortBlockEntity;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.block.entity.ReactorPortBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MasteriBHE.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorCoreBlockEntity>> REACTOR_CORE =
            BLOCK_ENTITIES.register("reactor_core", () ->
                    BlockEntityType.Builder.of(ReactorCoreBlockEntity::new, ModBlocks.REACTOR_CORE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MassInputPortBlockEntity>> MASS_INPUT_PORT =
            BLOCK_ENTITIES.register("mass_input_port", () ->
                    BlockEntityType.Builder.of(MassInputPortBlockEntity::new, ModBlocks.MASS_INPUT_PORT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorPortBlockEntity>> REACTOR_PORT =
            BLOCK_ENTITIES.register("reactor_port", () ->
                    BlockEntityType.Builder.of(ReactorPortBlockEntity::new, ModBlocks.REACTOR_PORT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BendingLaserBlockEntity>> BENDING_LASER =
            BLOCK_ENTITIES.register("bending_laser", () ->
                    BlockEntityType.Builder.of(BendingLaserBlockEntity::new, ModBlocks.BENDING_LASER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControlConsoleBlockEntity>> CONTROL_CONSOLE =
            BLOCK_ENTITIES.register("control_console", () ->
                    BlockEntityType.Builder.of(ControlConsoleBlockEntity::new, ModBlocks.CONTROL_CONSOLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchCoreBlockEntity>> RESEARCH_CORE =
            BLOCK_ENTITIES.register("research_core", () ->
                    BlockEntityType.Builder.of(ResearchCoreBlockEntity::new, ModBlocks.RESEARCH_CORE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlackHoleBombBlockEntity>> BLACK_HOLE_BOMB =
            BLOCK_ENTITIES.register("black_hole_bomb", () ->
                    BlockEntityType.Builder.of(BlackHoleBombBlockEntity::new, ModBlocks.BLACK_HOLE_BOMB.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HorizonGateBlockEntity>> HORIZON_GATE =
            BLOCK_ENTITIES.register("horizon_gate", () ->
                    BlockEntityType.Builder.of(HorizonGateBlockEntity::new, ModBlocks.HORIZON_GATE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GravitonWellBlockEntity>> GRAVITON_WELL =
            BLOCK_ENTITIES.register("graviton_well", () ->
                    BlockEntityType.Builder.of(GravitonWellBlockEntity::new, ModBlocks.GRAVITON_WELL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HawkingCellBlockEntity>> HAWKING_CELL =
            BLOCK_ENTITIES.register("hawking_cell", () ->
                    BlockEntityType.Builder.of(HawkingCellBlockEntity::new, ModBlocks.HAWKING_CELL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.masteri.bhe.block.entity.TrapCoreBlockEntity>> TRAP_CORE =
            BLOCK_ENTITIES.register("trap_core", () ->
                    BlockEntityType.Builder.of(com.masteri.bhe.block.entity.TrapCoreBlockEntity::new, ModBlocks.TRAP_CORE.get()).build(null));

    private ModBlockEntities() {}
}
