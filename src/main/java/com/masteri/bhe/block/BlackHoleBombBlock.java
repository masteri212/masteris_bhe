package com.masteri.bhe.block;

import com.masteri.bhe.block.entity.BlackHoleBombBlockEntity;
import com.masteri.bhe.block.entity.MassInputPortBlockEntity;
import com.masteri.bhe.registry.ModBlockEntities;
import com.masteri.bhe.registry.ModItems;
import com.masteri.bhe.util.Tickers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Use it with any block to feed it mass (sneak to place blocks against it instead), with Flint and Steel (or a
 * redstone signal) to arm it, and with the Configurator to defuse it. Use it empty-handed to read it. Breaking an
 * armed bomb, or blowing it up, sets it off at once.
 */
public class BlackHoleBombBlock extends Block implements EntityBlock {
    public BlackHoleBombBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BlackHoleBombBlockEntity bomb)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
            if (!level.isClientSide && !bomb.isArmed()) {
                bomb.arm();
                if (stack.is(Items.FIRE_CHARGE)) stack.consume(1, player);
                else stack.hurtAndBreak(1, player, Player.getSlotForHand(hand));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(ModItems.CONFIGURATOR.get())) {
            if (!level.isClientSide) {
                if (bomb.isArmed()) bomb.disarm(player);
                else status(player, bomb);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (MassInputPortBlockEntity.isMass(stack)) {
            if (!level.isClientSide) {
                bomb.addMass(stack.getCount());
                if (!player.getAbilities().instabuild) stack.setCount(0);
                level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6F, 0.5F);
                status(player, bomb);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BlackHoleBombBlockEntity bomb) status(player, bomb);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void status(Player player, BlackHoleBombBlockEntity bomb) {
        Component text = bomb.isArmed()
                ? Component.translatable("message.masteris_bhe.bomb.status_armed", String.format("%,d", bomb.getMass()), bomb.radius(), bomb.getFuse() / 20)
                        .withStyle(ChatFormatting.RED)
                : Component.translatable("message.masteris_bhe.bomb.status", String.format("%,d", bomb.getMass()), bomb.radius())
                        .withStyle(ChatFormatting.LIGHT_PURPLE);
        player.displayClientMessage(text, true);
    }

    // ------------------------------------------------------------------ arming by redstone, going off when broken

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide && level.hasNeighborSignal(pos) && level.getBlockEntity(pos) instanceof BlackHoleBombBlockEntity bomb) bomb.arm();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && level.hasNeighborSignal(pos) && level.getBlockEntity(pos) instanceof BlackHoleBombBlockEntity bomb) bomb.arm();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BlackHoleBombBlockEntity bomb && bomb.isArmed()) {
            bomb.detonate(); // you don't pull an armed black hole out of its casing
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BlackHoleBombBlockEntity bomb && bomb.isArmed()) {
            bomb.detonate();
            return;
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    // ------------------------------------------------------------------ block entity

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlackHoleBombBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? Tickers.of(type, ModBlockEntities.BLACK_HOLE_BOMB.get(), (l, p, s, bomb) -> bomb.clientTick())
                : Tickers.of(type, ModBlockEntities.BLACK_HOLE_BOMB.get(), (l, p, s, bomb) -> bomb.serverTick());
    }
}
