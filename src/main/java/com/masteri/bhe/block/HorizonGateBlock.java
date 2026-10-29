package com.masteri.bhe.block;

import com.masteri.bhe.block.entity.HorizonGateBlockEntity;
import com.masteri.bhe.horizon.EventHorizon;
import com.masteri.bhe.horizon.HorizonRing;
import com.masteri.bhe.reactor.MassValues;
import com.masteri.bhe.registry.ModBlockEntities;
import com.masteri.bhe.util.Tickers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Horizon Gate, the base of a Horizon Ring. Use it with mass to charge it (sneak to place blocks against it
 * instead); use it empty-handed, or give it a redstone pulse, to open the ring. See HorizonGateBlockEntity.
 */
public class HorizonGateBlock extends Block implements EntityBlock {
    public HorizonGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HorizonGateBlockEntity gate) || !MassValues.isMass(stack)
                || EventHorizon.isHorizon(level)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            gate.addMass(MassValues.of(stack));
            if (!player.getAbilities().instabuild) stack.setCount(0);
            level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6F, 0.5F);
            player.displayClientMessage(Component.translatable(EventHorizon.key("gate_status"), String.format("%,d", gate.getMass()),
                    String.format("%,d", HorizonGateBlockEntity.tripCost())).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HorizonGateBlockEntity gate) gate.activate(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HorizonGateBlockEntity gate) gate.neighborSignal(level.hasNeighborSignal(pos));
    }

    /** While it's spinning up or open, light gets dragged in toward the middle of the ring. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof HorizonGateBlockEntity gate) || gate.getAxis() == null
                || gate.getPhase() == HorizonGateBlockEntity.Phase.IDLE) return;
        Vec3 c = HorizonRing.center(pos);
        boolean x = gate.getAxis() == Direction.Axis.X;
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = HorizonRing.INNER_RADIUS + 0.5 + random.nextDouble() * 1.5;
            double along = Math.cos(a) * r, up = Math.sin(a) * r, through = (random.nextDouble() - 0.5) * 1.5;
            level.addParticle(ParticleTypes.REVERSE_PORTAL, c.x + (x ? along : through), c.y + up, c.z + (x ? through : along),
                    -(x ? along : 0) * 0.05, -up * 0.05, -(x ? 0 : along) * 0.05);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HorizonGateBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? Tickers.of(type, ModBlockEntities.HORIZON_GATE.get(), (l, p, s, gate) -> gate.clientTick())
                : Tickers.of(type, ModBlockEntities.HORIZON_GATE.get(), (l, p, s, gate) -> gate.serverTick());
    }
}
