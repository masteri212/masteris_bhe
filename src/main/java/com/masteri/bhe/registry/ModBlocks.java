package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.AssemblyFacingBlock;
import com.masteri.bhe.block.AssemblyPartBlock;
import com.masteri.bhe.block.BendingLaserBlock;
import com.masteri.bhe.block.BlackHoleBombBlock;
import com.masteri.bhe.block.BreakerPanelBlock;
import com.masteri.bhe.block.ControlConsoleBlock;
import com.masteri.bhe.block.FacingBlock;
import com.masteri.bhe.block.GravitonWellBlock;
import com.masteri.bhe.block.HawkingCellBlock;
import com.masteri.bhe.block.HorizonGateBlock;
import com.masteri.bhe.block.NeutroniumBlock;
import com.masteri.bhe.block.MassInputPortBlock;
import com.masteri.bhe.block.ReactorPortBlock;
import com.masteri.bhe.block.ReactorCoreBlock;
import com.masteri.bhe.block.ResearchCoreBlock;
import com.masteri.bhe.block.StabilizerConsoleBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
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
    public static final DeferredBlock<AssemblyPartBlock> RING_FRAME = BLOCKS.registerBlock("ring_frame", AssemblyPartBlock::new, machine(MapColor.COLOR_BLUE));
    public static final DeferredBlock<AssemblyPartBlock> RING_BEAM_PORT = BLOCKS.registerBlock("ring_beam_port", AssemblyPartBlock::new, machine(MapColor.COLOR_BLUE).lightLevel(s -> 6));
    public static final DeferredBlock<ReactorPortBlock> REACTOR_PORT = BLOCKS.registerBlock("reactor_port",
            ReactorPortBlock::new, machine(MapColor.COLOR_BLUE));

    // Stabilizers
    public static final DeferredBlock<AssemblyPartBlock> STABILIZER_CASING = BLOCKS.registerBlock("stabilizer_casing", AssemblyPartBlock::new, machine(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<AssemblyFacingBlock> STABILIZER_EMITTER = BLOCKS.registerBlock("stabilizer_emitter",
            AssemblyFacingBlock::new, machine(MapColor.COLOR_PURPLE).lightLevel(s -> 8));
    public static final DeferredBlock<AssemblyPartBlock> STABILIZER_COIL = BLOCKS.registerBlock("stabilizer_coil", AssemblyPartBlock::new, machine(MapColor.COLOR_PURPLE).lightLevel(s -> 7));
    public static final DeferredBlock<MassInputPortBlock> MASS_INPUT_PORT = BLOCKS.registerBlock("mass_input_port",
            MassInputPortBlock::new, machine(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<StabilizerConsoleBlock> STABILIZER_CONSOLE = BLOCKS.registerBlock("stabilizer_console",
            StabilizerConsoleBlock::new, machine(MapColor.COLOR_PURPLE).lightLevel(s -> 5));
    public static final DeferredBlock<BreakerPanelBlock> BREAKER_PANEL = BLOCKS.registerBlock("breaker_panel",
            BreakerPanelBlock::new, machine(MapColor.COLOR_PURPLE).lightLevel(s -> s.getValue(BreakerPanelBlock.OVERCHARGED) ? 9 : 3));
    /** The emitter of an axial stabilizer (the two raised above the ring that let spin make power): fires down. */
    public static final DeferredBlock<AssemblyPartBlock> AXIAL_EMITTER = BLOCKS.registerBlock("axial_emitter", AssemblyPartBlock::new,
            machine(MapColor.COLOR_PURPLE).lightLevel(s -> 8));

    // Laser, lance, control
    public static final DeferredBlock<BendingLaserBlock> BENDING_LASER = BLOCKS.registerBlock("bending_laser",
            BendingLaserBlock::new, machine(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<AssemblyPartBlock> LANCE_EMITTER = BLOCKS.registerBlock("lance_emitter", AssemblyPartBlock::new, machine(MapColor.COLOR_ORANGE).lightLevel(s -> 9));
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
    public static final DeferredBlock<BlackHoleBombBlock> BLACK_HOLE_BOMB = BLOCKS.registerBlock("black_hole_bomb",
            BlackHoleBombBlock::new, machine(MapColor.COLOR_RED));

    // Dark energy zone
    public static final DeferredBlock<Block> CORRUPTED_STONE = BLOCKS.registerSimpleBlock("corrupted_stone",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> UNSTABLE_DARK_ORE = BLOCKS.registerSimpleBlock("unstable_dark_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(4.0F, 6.0F).requiresCorrectToolForDrops().lightLevel(s -> 4));

    // Event Horizon
    /** The base of a Horizon Ring: burns mass to open the ring into the Event Horizon (in there, opens it back out). */
    public static final DeferredBlock<HorizonGateBlock> HORIZON_GATE = BLOCKS.registerBlock("horizon_gate",
            HorizonGateBlock::new, machine(MapColor.COLOR_BLACK).lightLevel(s -> 10));
    /** The 23 blocks of a Horizon Ring around the gate (see HorizonRing). */
    public static final DeferredBlock<Block> HORIZON_FRAME = BLOCKS.registerSimpleBlock("horizon_frame",
            machine(MapColor.COLOR_BLACK).lightLevel(s -> 4));
    public static final DeferredBlock<Block> NEUTRONIUM_ORE = BLOCKS.registerSimpleBlock("neutronium_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(6.0F, 12.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE).lightLevel(s -> 3));
    public static final DeferredBlock<NeutroniumBlock> NEUTRONIUM_BLOCK = BLOCKS.registerBlock("neutronium_block",
            NeutroniumBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(8.0F, 1200.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK));
    public static final DeferredBlock<AmethystClusterBlock> MASS_SHARD_CLUSTER = BLOCKS.registerBlock("mass_shard_cluster",
            props -> new AmethystClusterBlock(7, 3, props), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA)
                    .noOcclusion().strength(1.5F).sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 5)
                    .pushReaction(PushReaction.DESTROY).requiresCorrectToolForDrops());
    public static final DeferredBlock<GravitonWellBlock> GRAVITON_WELL = BLOCKS.registerBlock("graviton_well",
            GravitonWellBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(4.0F, 6.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE).lightLevel(s -> 7));
    public static final DeferredBlock<HawkingCellBlock> HAWKING_CELL = BLOCKS.registerBlock("hawking_cell",
            HawkingCellBlock::new, machine(MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 12));

    /** In the Observatory ruins: points the way to Frozen Reactors (and the Citadel). */
    public static final DeferredBlock<com.masteri.bhe.block.TelescopeBlock> TELESCOPE = BLOCKS.registerBlock("telescope",
            com.masteri.bhe.block.TelescopeBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.COPPER).noOcclusion());
    /** In the Citadel: takes the three Reactor Keys and calls up the Singularity. */
    public static final DeferredBlock<com.masteri.bhe.block.SingularityAltarBlock> SINGULARITY_ALTAR = BLOCKS.registerBlock("singularity_altar",
            com.masteri.bhe.block.SingularityAltarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3600000.0F).noLootTable().lightLevel(s -> 9).sound(SoundType.LODESTONE));

    // Light Trap: unbreakable seals round its vault, pads to stand on, and the core that runs it.
    public static final DeferredBlock<Block> LIGHT_SEAL = BLOCKS.registerSimpleBlock("light_seal",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0F, 3600000.0F).noLootTable()
                    .lightLevel(s -> 12).sound(SoundType.GLASS));
    public static final DeferredBlock<com.masteri.bhe.block.LightPadBlock> LIGHT_PAD = BLOCKS.registerBlock("light_pad",
            com.masteri.bhe.block.LightPadBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(-1.0F, 3600000.0F).noLootTable().lightLevel(s -> s.getValue(com.masteri.bhe.block.LightPadBlock.LIT) ? 14 : 5)
                    .sound(SoundType.AMETHYST));
    public static final DeferredBlock<com.masteri.bhe.block.TrapCoreBlock> TRAP_CORE = BLOCKS.registerBlock("trap_core",
            com.masteri.bhe.block.TrapCoreBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3600000.0F).noLootTable().lightLevel(s -> 10).noOcclusion());
    // Hawking Garden
    public static final DeferredBlock<com.masteri.bhe.block.EvaporatingRockBlock> EVAPORATING_ROCK = BLOCKS.registerBlock("evaporating_rock",
            com.masteri.bhe.block.EvaporatingRockBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5F, 3.0F).randomTicks().sound(SoundType.CALCITE)
                    .lightLevel(s -> s.getValue(com.masteri.bhe.block.EvaporatingRockBlock.FADING) ? 9 : 2));
    public static final DeferredBlock<com.masteri.bhe.block.HawkingBloomBlock> HAWKING_BLOOM = BLOCKS.registerBlock("hawking_bloom",
            com.masteri.bhe.block.HawkingBloomBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .noCollission().instabreak().randomTicks().sound(SoundType.SPORE_BLOSSOM).lightLevel(s -> 9)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));

    private ModBlocks() {}
}
