package com.masteri.bhe.research;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.FacingBlock;
import com.masteri.bhe.block.entity.ResearchCoreBlockEntity;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** The TUNE step: the Configurator used on the Containment Box inside a Research Chamber. */
@EventBusSubscriber(modid = MasteriBHE.MODID)
public final class ResearchEvents {
    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(ModItems.CONFIGURATOR.get())) return;
        Level level = event.getLevel();
        BlockPos box = event.getPos();
        if (!level.getBlockState(box).is(ModBlocks.CONTAINMENT_BOX.get())) return;
        ResearchCoreBlockEntity core = coreFor(level, box);
        if (core == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!level.isClientSide && core.tune()) {
            level.playSound(null, box, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1F, 1.2F);
        }
    }

    /** The Research Core whose chamber has its center at {@code box}: one block out from it, facing away. */
    private static ResearchCoreBlockEntity coreFor(Level level, BlockPos box) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = box.relative(dir);
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.RESEARCH_CORE.get()) && state.getValue(FacingBlock.FACING) == dir
                    && level.getBlockEntity(pos) instanceof ResearchCoreBlockEntity core && core.isCenter(box)) {
                return core;
            }
        }
        return null;
    }

    private ResearchEvents() {}
}
