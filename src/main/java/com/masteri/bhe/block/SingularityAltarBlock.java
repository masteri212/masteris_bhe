package com.masteri.bhe.block;

import com.masteri.bhe.entity.SingularityEntity;
import com.masteri.bhe.horizon.EventHorizon;
import com.masteri.bhe.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Singularity Altar, at the heart of the Citadel. It takes one Reactor Key of each kind (from the Echoes of the
 * three kinds of Frozen Reactor); with all three set in, the Singularity rises over the Citadel. The keys are used
 * up and the altar is ready for three more.
 */
public class SingularityAltarBlock extends Block {
    public static final BooleanProperty OVERSPUN = BooleanProperty.create("overspun");
    public static final BooleanProperty STARVED = BooleanProperty.create("starved");
    public static final BooleanProperty BREACHED = BooleanProperty.create("breached");
    private static final int RISE = 12;

    public SingularityAltarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OVERSPUN, false).setValue(STARVED, false).setValue(BREACHED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OVERSPUN, STARVED, BREACHED);
    }

    private static BooleanProperty slotFor(Item item) {
        if (item == ModItems.REACTOR_KEY_OVERSPUN.get()) return OVERSPUN;
        if (item == ModItems.REACTOR_KEY_STARVED.get()) return STARVED;
        if (item == ModItems.REACTOR_KEY_BREACHED.get()) return BREACHED;
        return null;
    }

    public static boolean hasAllKeys(Player player) {
        return player.getInventory().hasAnyMatching(s -> s.is(ModItems.REACTOR_KEY_OVERSPUN.get()))
                && player.getInventory().hasAnyMatching(s -> s.is(ModItems.REACTOR_KEY_STARVED.get()))
                && player.getInventory().hasAnyMatching(s -> s.is(ModItems.REACTOR_KEY_BREACHED.get()));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        BooleanProperty slot = slotFor(stack.getItem());
        if (slot == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (state.getValue(slot)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable(EventHorizon.key("altar_has_key")).withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(level instanceof ServerLevel server)) return ItemInteractionResult.SUCCESS;
        BlockState after = state.setValue(slot, true);
        boolean completes = after.getValue(OVERSPUN) && after.getValue(STARVED) && after.getValue(BREACHED);
        if (completes && !server.getEntitiesOfClass(SingularityEntity.class, new AABB(pos).inflate(160)).isEmpty()) {
            player.displayClientMessage(Component.translatable(EventHorizon.key("lure_already")).withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.CONSUME; // keep the last key until this one's gone
        }
        stack.consume(1, player);
        state = after;
        level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.5F, 0.7F);
        if (completes) {
            level.setBlockAndUpdate(pos, defaultBlockState());
            SingularityEntity.rise(server, Vec3.atBottomCenterOf(pos.above(RISE)));
        } else {
            level.setBlockAndUpdate(pos, state);
            status(player, state);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) status(player, state);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void status(Player player, BlockState state) {
        List<Component> missing = new ArrayList<>();
        if (!state.getValue(OVERSPUN)) missing.add(ModItems.REACTOR_KEY_OVERSPUN.get().getDescription());
        if (!state.getValue(STARVED)) missing.add(ModItems.REACTOR_KEY_STARVED.get().getDescription());
        if (!state.getValue(BREACHED)) missing.add(ModItems.REACTOR_KEY_BREACHED.get().getDescription());
        Component list = Component.empty();
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) list = list.copy().append(", ");
            list = list.copy().append(missing.get(i));
        }
        player.displayClientMessage(Component.translatable(EventHorizon.key("altar_needs"), list).withStyle(ChatFormatting.GOLD), true);
    }
}
