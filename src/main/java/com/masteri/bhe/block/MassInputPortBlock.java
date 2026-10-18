package com.masteri.bhe.block;

import com.masteri.bhe.block.entity.MassInputPortBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Accepts any block item as mass: 1 block = 1 mass. Works by hand, hoppers and pipes. */
public class MassInputPortBlock extends BaseEntityBlock {
    public static final MapCodec<MassInputPortBlock> CODEC = simpleCodec(MassInputPortBlock::new);

    public MassInputPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MassInputPortBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MassInputPortBlockEntity port)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!MassInputPortBlockEntity.isMass(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            int count = stack.getCount();
            port.addMass(count);
            if (!player.getAbilities().instabuild) stack.shrink(count);
            player.displayClientMessage(Component.translatable("message.masteris_bhe.mass.added", count, port.getMass())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MassInputPortBlockEntity port) {
            player.displayClientMessage(Component.translatable("message.masteris_bhe.mass.stored", port.getMass())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof MassInputPortBlockEntity port)) return 0;
        // 15 = at least the ignition minimum (10,000 by default)
        return (int) Math.min(15, port.getMass() * 15 / 10_000);
    }
}
