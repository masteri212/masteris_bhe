package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.BendingLaserBlock;
import com.masteri.bhe.block.BreakerPanelBlock;
import com.masteri.bhe.block.ControlConsoleBlock;
import com.masteri.bhe.block.FacingBlock;
import com.masteri.bhe.block.MassInputPortBlock;
import com.masteri.bhe.block.ReactorPortBlock;
import com.masteri.bhe.block.ReactorCoreBlock;
import com.masteri.bhe.block.ResearchCoreBlock;
import com.masteri.bhe.block.StabilizerConsoleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MasteriBHE.MODID);

    private static BlockBehaviour.Properties machine(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(5.0F, 12.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.NETHERITE_BLOCK);
    }

    // Reactor
    public static final DeferredBlock<ReactorCoreBlock> REACTOR_CORE = BLOCKS.registerBlock("reactor_core",
            ReactorCoreBlock::new, machine(MapColor.COLOR_BLACK).noOcclusion().lightLevel(s -> 12));
    public static final DeferredBlock<Block> RING_FRAME = BLOCKS.registerSimpleBlock("ring_frame", machine(MapColor.COLOR_BLUE));
    public static final DeferredBlock<Block> RING_BEAM_PORT = BLOCKS.registerSimpleBlock("ring_beam_port", machine(MapColor.COLOR_BLUE).lightLevel(s -> 6));
    public static final DeferredBlock<ReactorPortBlock> REACTOR_PORT = BLOCKS.registerBlock("reactor_port",
            ReactorPortBlock::new, machine(MapColor.COLOR_BLUE));

    // Stabilizers
    public static final DeferredBlock<Block> STABILIZER_CASING = BLOCKS.registerSimpleBlock("stabilizer_casing", machine(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<FacingBlock> STABILIZER_EMITTER = BLOCKS.registerBlock("stabilizer_emitter",
            FacingBlock::new, machine(MapColor.COLOR_PURPLE).lightLevel(s -> 8));
    public static final DeferredBlock<Block> STABILIZER_COIL = BLOCKS.registerSimpleBlock("stabilizer_coil", machine(MapColor.COLOR_PURPLE).lightLevel(s -> 7));
    public static final DeferredBlock<MassInputPortBlock> MASS_INPUT_PORT = BLOCKS.registerBlock("mass_input_port",
            MassInputPortBlock::new, machine(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<StabilizerConsoleBlock> STABILIZER_CONSOLE = BLOCKS.registerBlock("stabilizer_console",
            StabilizerConsoleBlock::new, machine(MapColor.COLOR_PURPLE).lightLevel(s -> 5));
    public static final DeferredBlock<BreakerPanelBlock> BREAKER_PANEL = BLOCKS.registerBlock("breaker_panel",
            BreakerPanelBlock::new, machine(MapColor.COLOR_PURPLE).lightLevel(s -> s.getValue(BreakerPanelBlock.OVERCHARGED) ? 9 : 3));
    /** The emitter of an axial stabilizer (the two raised above the ring that let spin make power): fires down. */
    public static final DeferredBlock<Block> AXIAL_EMITTER = BLOCKS.registerSimpleBlock("axial_emitter",
            machine(MapColor.COLOR_PURPLE).lightLevel(s -> 8));

    // Laser, lance, control
    public static final DeferredBlock<BendingLaserBlock> BENDING_LASER = BLOCKS.registerBlock("bending_laser",
            BendingLaserBlock::new, machine(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<Block> LANCE_EMITTER = BLOCKS.registerSimpleBlock("lance_emitter", machine(MapColor.COLOR_ORANGE).lightLevel(s -> 9));
    public static final DeferredBlock<ControlConsoleBlock> CONTROL_CONSOLE = BLOCKS.registerBlock("control_console",
            ControlConsoleBlock::new, machine(MapColor.COLOR_GRAY).noOcclusion().lightLevel(s -> 5));
    public static final DeferredBlock<Block> ALARM_LIGHT = BLOCKS.registerSimpleBlock("alarm_light",
            machine(MapColor.COLOR_YELLOW).noOcclusion().lightLevel(s -> 10).strength(2.0F, 6.0F));

    // Research
    public static final DeferredBlock<Block> RESEARCH_CASING = BLOCKS.registerSimpleBlock("research_casing", machine(MapColor.COLOR_CYAN));
    public static final DeferredBlock<ResearchCoreBlock> RESEARCH_CORE = BLOCKS.registerBlock("research_core",
            ResearchCoreBlock::new, machine(MapColor.COLOR_CYAN).lightLevel(s -> 6));
    public static final DeferredBlock<Block> CONTAINMENT_BOX = BLOCKS.registerSimpleBlock("containment_box",
            machine(MapColor.NONE).noOcclusion().sound(SoundType.GLASS).lightLevel(s -> 4));
    // TODO: explosion logic. For now it is a decorative block.
    public static final DeferredBlock<Block> BLACK_HOLE_BOMB = BLOCKS.registerSimpleBlock("black_hole_bomb", machine(MapColor.COLOR_RED));

    // Dark energy zone
    public static final DeferredBlock<Block> CORRUPTED_STONE = BLOCKS.registerSimpleBlock("corrupted_stone",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> UNSTABLE_DARK_ORE = BLOCKS.registerSimpleBlock("unstable_dark_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(4.0F, 6.0F).requiresCorrectToolForDrops().lightLevel(s -> 4));

    private ModBlocks() {}
}
