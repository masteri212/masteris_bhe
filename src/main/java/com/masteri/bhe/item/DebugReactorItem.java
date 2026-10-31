package com.masteri.bhe.item;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.FacingBlock;
import com.masteri.bhe.block.entity.BendingLaserBlockEntity;
import com.masteri.bhe.block.entity.ControlConsoleBlockEntity;
import com.masteri.bhe.block.entity.MassInputPortBlockEntity;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.multiblock.ReactorStructure;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.reactor.DarkZones;
import com.masteri.bhe.reactor.VentingLance;
import com.masteri.bhe.reactor.ZoneTerrain;
import com.masteri.bhe.research.ResearchChamber;
import com.masteri.bhe.registry.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Creative-only debug tools for testing the reactor without building it by hand.
 *
 * Assemblers: use on the ground and a whole reactor appears with the ring's bottom layer on that surface
 * (the core ends up 2 blocks above it, centered on the clicked spot). Higher modes add the bending laser on the
 * NE diagonal, a control console, full stabilizers and ignition charge, and finally ignite it.
 * Dismantler: use on a Reactor Core to remove it and everything the assemblers place.
 */
public class DebugReactorItem extends Item {
    public enum Mode {
        /** Core, containment ring and four stabilizers. */
        FRAME,
        /** Frame plus a charged bending laser, a control console, ignition mass and ignition energy. */
        FUELED,
        /** Fueled, then ignited. */
        IGNITED,
        /** Removes a reactor. */
        DISMANTLE,
        /** Use on a Reactor Core to step through every failure's visuals; sneak-use to stop. Nothing is destroyed. */
        FAILURE_PREVIEW,
        /** Use on a block to put a dark energy zone there (as a collapse would); sneak-use inside one to remove it. */
        ZONE,
        /** Use on a Reactor Core (every stabilizer) or one Mass Input Port: +10,000 mass; sneak-use halves it. */
        MASS,
        /** Use on a block to build a Research Chamber (with a captured black hole) on it, its core facing you. */
        RESEARCH
    }

    /** Bottom-left of the console wall: ground level on the south side, beside the south stabilizer, screen facing south. */
    private static final BlockPos CONSOLE_OFFSET = new BlockPos(4, -2, 19);
    private static final int LASER_CHARGE = 4_000_000;
    private static final long MASS_STEP = 10_000;

    private final Mode mode;

    public DebugReactorItem(Properties properties, Mode mode) {
        super(properties);
        this.mode = mode;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clicked = context.getClickedPos();
        if (player == null) return InteractionResult.PASS;
        if ((mode == Mode.DISMANTLE || mode == Mode.FAILURE_PREVIEW) && !(level.getBlockEntity(clicked) instanceof ReactorCoreBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (mode == Mode.MASS && !(level.getBlockEntity(clicked) instanceof ReactorCoreBlockEntity)
                && !(level.getBlockEntity(clicked) instanceof MassInputPortBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        String key = "message." + MasteriBHE.MODID + ".debug.";
        if (!player.isCreative()) {
            player.displayClientMessage(Component.translatable(key + "creative_only").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (mode == Mode.FAILURE_PREVIEW && level.getBlockEntity(clicked) instanceof ReactorCoreBlockEntity core) {
            if (player.isShiftKeyDown()) {
                core.stopPreview();
                player.displayClientMessage(Component.translatable(key + "preview.stopped").withStyle(ChatFormatting.AQUA), false);
            } else if (!core.isFormed()) {
                player.displayClientMessage(Component.translatable(key + "preview.not_formed").withStyle(ChatFormatting.RED), false);
            } else {
                ReactorCoreBlockEntity.Preview shown = core.previewNext();
                player.displayClientMessage(Component.translatable(key + "preview.showing",
                        Component.translatable(key + "preview." + shown.name().toLowerCase()),
                        shown.ordinal() + 1, ReactorCoreBlockEntity.Preview.values().length).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
            return InteractionResult.SUCCESS;
        }

        if (mode == Mode.MASS) {
            List<MassInputPortBlockEntity> ports = level.getBlockEntity(clicked) instanceof ReactorCoreBlockEntity core
                    ? core.massPorts()
                    : List.of((MassInputPortBlockEntity) level.getBlockEntity(clicked));
            if (ports.isEmpty()) {
                player.displayClientMessage(Component.translatable(key + "mass.no_ports").withStyle(ChatFormatting.RED), false);
                return InteractionResult.FAIL;
            }
            long total = 0;
            StringBuilder each = new StringBuilder();
            for (MassInputPortBlockEntity port : ports) {
                if (player.isShiftKeyDown()) port.drain(port.getMass() - port.getMass() / 2);
                else port.addMass(MASS_STEP);
                total += port.getMass();
                if (!each.isEmpty()) each.append(" / ");
                each.append(String.format("%,d", port.getMass()));
            }
            player.displayClientMessage(Component.translatable(key + (player.isShiftKeyDown() ? "mass.halved" : "mass.added"),
                    String.format("%,d", total), each.toString()).withStyle(ChatFormatting.AQUA), false);
            return InteractionResult.SUCCESS;
        }

        if (mode == Mode.RESEARCH) {
            Direction facing = player.getDirection().getOpposite();
            BlockPos core = clicked.relative(context.getClickedFace()).above();
            int placed = ResearchChamber.build(level, core, facing);
            player.displayClientMessage(Component.translatable(key + "research", placed).withStyle(ChatFormatting.AQUA), false);
            return InteractionResult.SUCCESS;
        }

        if (mode == Mode.ZONE && level instanceof ServerLevel server) {
            DarkZones zones = DarkZones.get(server);
            if (player.isShiftKeyDown()) {
                DarkZones.Zone removed = zones.removeAt(server, player.blockPosition());
                player.displayClientMessage(removed == null
                        ? Component.translatable(key + "zone.none").withStyle(ChatFormatting.RED)
                        : Component.translatable(key + "zone.removed", removed.center().getX(), removed.center().getY(),
                                removed.center().getZ()).withStyle(ChatFormatting.AQUA), false);
            } else {
                BlockPos center = clicked.relative(context.getClickedFace());
                int radius = ReactorConfig.ZONE_RADIUS.get();
                zones.add(server, center, radius);
                DarkZones.Zone zone = zones.at(center);
                if (zone != null) ZoneTerrain.queueLoadedChunks(server, zone, 0);
                player.displayClientMessage(Component.translatable(key + "zone.created", radius,
                        center.getX(), center.getY(), center.getZ()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
            return InteractionResult.SUCCESS;
        }

        if (mode == Mode.DISMANTLE) {
            int removed = dismantle(level, clicked);
            player.displayClientMessage(Component.translatable(key + "dismantled", removed).withStyle(ChatFormatting.AQUA), false);
            return InteractionResult.SUCCESS;
        }

        BlockPos corePos = clicked.relative(context.getClickedFace()).above(2);
        if (level.isOutsideBuildHeight(corePos.below(2)) || level.isOutsideBuildHeight(corePos.above(4))) {
            player.displayClientMessage(Component.translatable(key + "no_room").withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }

        level.setBlock(corePos, ModBlocks.REACTOR_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(corePos) instanceof ReactorCoreBlockEntity core)) return InteractionResult.FAIL;
        int placed = 1 + ReactorStructure.build(level, corePos) + ReactorStructure.buildAxial(level, corePos);

        if (mode != Mode.FRAME) {
            placed += place(level, corePos.offset(ReactorStructure.LASER_OFFSET),
                    ModBlocks.BENDING_LASER.get().defaultBlockState().setValue(FacingBlock.FACING, Direction.WEST));
            if (level.getBlockEntity(corePos.offset(ReactorStructure.LASER_OFFSET)) instanceof BendingLaserBlockEntity laser) {
                laser.getEnergy().receiveEnergy(LASER_CHARGE, false);
                laser.setChanged();
            }
            if (!level.getBlockState(corePos.offset(CONSOLE_OFFSET)).is(ModBlocks.CONTROL_CONSOLE.get())) {
                placed += ModBlocks.CONTROL_CONSOLE.get().placeWall(level, corePos.offset(CONSOLE_OFFSET), Direction.SOUTH);
            }
            if (level.getBlockEntity(corePos.offset(CONSOLE_OFFSET)) instanceof ControlConsoleBlockEntity console) console.link(corePos);
            placed += VentingLance.build(level, corePos);
        }

        ReactorStructure.Result result = core.checkStructure();
        if (mode != Mode.FRAME) core.fillForTesting();
        if (mode == Mode.IGNITED) core.handleButton(player, ReactorCoreBlockEntity.BUTTON_IGNITE);

        player.displayClientMessage(Component.translatable(key + "assembled." + mode.name().toLowerCase(), placed,
                corePos.getX(), corePos.getY(), corePos.getZ()).withStyle(ChatFormatting.AQUA), false);
        if (!result.complete()) {
            player.displayClientMessage(Component.translatable("message." + MasteriBHE.MODID + ".structure.incomplete",
                    result.placed(), result.total()).withStyle(ChatFormatting.YELLOW), false);
        }
        return InteractionResult.SUCCESS;
    }

    /** Removes the core first (so a running reactor can't register a breach), then every structure block. */
    private static int dismantle(Level level, BlockPos core) {
        BlockState air = Blocks.AIR.defaultBlockState();
        level.setBlock(core, air, Block.UPDATE_ALL);
        int removed = 1;
        List<ReactorStructure.Part> parts = new java.util.ArrayList<>(ReactorStructure.layout());
        parts.addAll(ReactorStructure.axialLayout());
        for (ReactorStructure.Part part : parts) {
            BlockPos pos = core.offset(part.offset());
            if (part.matches(level.getBlockState(pos))) {
                level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                removed++;
            }
        }
        removed += removeIf(level, core.offset(ReactorStructure.LASER_OFFSET), ModBlocks.BENDING_LASER.get());
        removed += removeIf(level, core.offset(CONSOLE_OFFSET), ModBlocks.CONTROL_CONSOLE.get()); // takes the whole wall
        removed += VentingLance.remove(level, core, false);
        return removed;
    }

    private static int place(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).is(state.getBlock())) return 0;
        level.setBlock(pos, state, Block.UPDATE_ALL);
        return 1;
    }

    private static int removeIf(Level level, BlockPos pos, Block block) {
        if (!level.getBlockState(pos).is(block)) return 0;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return 1;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + MasteriBHE.MODID + ".debug." + mode.name().toLowerCase())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + MasteriBHE.MODID + ".debug.creative_only")
                .withStyle(ChatFormatting.DARK_RED));
    }
}
