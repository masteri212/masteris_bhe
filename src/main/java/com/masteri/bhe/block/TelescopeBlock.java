package com.masteri.bhe.block;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.horizon.EventHorizon;
import com.masteri.bhe.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Observatory's telescope. Found with its lens cracked: fit a Singularity Glass and it works from then on.
 * Look through it (use it) and it points out the nearest Frozen Reactor: a line of light toward it and the
 * distance. Carrying all three Reactor Keys, it finds the Citadel instead.
 */
public class TelescopeBlock extends Block {
    public static final BooleanProperty FIXED = BooleanProperty.create("fixed");
    public static final TagKey<Structure> FROZEN_REACTORS = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "frozen_reactors"));
    public static final TagKey<Structure> CITADELS = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "citadels"));
    private static final int SEARCH_CHUNKS = 100;

    public TelescopeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FIXED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FIXED);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(FIXED) || !stack.is(ModItems.SINGULARITY_GLASS.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, state.setValue(FIXED, true));
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 1.2F);
            player.displayClientMessage(Component.translatable(EventHorizon.key("telescope_fixed")).withStyle(ChatFormatting.AQUA), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (!state.getValue(FIXED)) {
            player.displayClientMessage(Component.translatable(EventHorizon.key("telescope_cracked")).withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        boolean citadel = SingularityAltarBlock.hasAllKeys(player);
        BlockPos found = server.findNearestMapStructure(citadel ? CITADELS : FROZEN_REACTORS, pos, SEARCH_CHUNKS, false);
        String what = EventHorizon.key(citadel ? "telescope_citadel" : "telescope_reactor");
        if (found == null) {
            player.displayClientMessage(Component.translatable(EventHorizon.key("telescope_nothing"), Component.translatable(what)).withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        double dx = found.getX() + 0.5 - (pos.getX() + 0.5), dz = found.getZ() + 0.5 - (pos.getZ() + 0.5);
        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        player.displayClientMessage(Component.translatable(EventHorizon.key("telescope_found"), Component.translatable(what), distance,
                Component.translatable(EventHorizon.key("dir." + compass(dx, dz)))).withStyle(ChatFormatting.AQUA), false);
        // A line of light pointing the way.
        Vec3 start = Vec3.atCenterOf(pos).add(0, 1.2, 0);
        Vec3 dir = new Vec3(dx, 0, dz).normalize();
        for (int i = 2; i < 40; i++) {
            Vec3 p = start.add(dir.scale(i * 0.75));
            server.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        level.playSound(null, pos, SoundEvents.SPYGLASS_USE, SoundSource.BLOCKS, 1F, 0.8F);
        return InteractionResult.CONSUME;
    }

    /** One of the eight compass directions toward (dx, dz): n, ne, e, se, s, sw, w, nw. */
    static String compass(double dx, double dz) {
        String[] names = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
        double angle = Math.atan2(-dx, dz); // 0 = south, increasing clockwise seen from above
        int i = (int) Math.round(angle / (Math.PI / 4));
        return names[Math.floorMod(i, 8)];
    }
}
