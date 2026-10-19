package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.entity.BendingLaserBlockEntity;
import com.masteri.bhe.block.entity.BlackHoleBombBlockEntity;
import com.masteri.bhe.block.entity.ControlConsoleBlockEntity;
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

    private ModBlockEntities() {}
}
