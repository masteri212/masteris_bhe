package com.masteri.bhe.item;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.multiblock.ReactorStructure;
import com.masteri.bhe.reactor.ReactorLocator;
import com.masteri.bhe.reactor.VentingLance;
import com.masteri.bhe.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Use on a Reactor Core: check the structure and report what is missing.
 * Sneak + use: highlight missing positions with particles.
 * Sneak + use in creative: build the whole structure (for testing); once it stands, again for the axial stabilizers.
 */
public class ConfiguratorItem extends Item {
    private static final int MAX_HIGHLIGHTS = 64;
    private static final int MAX_LISTED_TYPES = 6;

    public ConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (level.getBlockState(pos).is(ModBlocks.STABILIZER_EMITTER.get())) return realign(level, pos, player);
        if (!(level.getBlockEntity(pos) instanceof ReactorCoreBlockEntity core)) return InteractionResult.PASS;
        if (level.isClientSide || player == null) return InteractionResult.SUCCESS;

        String key = "message." + MasteriBHE.MODID + ".structure.";

        if (player.isShiftKeyDown() && player.isCreative()) {
            // The axial stabilizers only once the rest of the reactor stands: a second sneak-use adds them.
            if (ReactorStructure.check(level, pos).complete()) {
                int placed = ReactorStructure.buildAxial(level, pos);
                player.displayClientMessage(Component.translatable(key + (placed > 0 ? "built_axial" : "axial_complete"), placed)
                        .withStyle(ChatFormatting.AQUA), false);
                return InteractionResult.SUCCESS;
            }
            int placed = ReactorStructure.build(level, pos);
            core.checkStructure();
            core.fillForTesting();
            player.displayClientMessage(Component.translatable(key + "built", placed).withStyle(ChatFormatting.AQUA), false);
            return InteractionResult.SUCCESS;
        }

        ReactorStructure.Result result = core.checkStructure();
        if (result.complete()) {
            player.displayClientMessage(Component.translatable(key + "complete", result.total()).withStyle(ChatFormatting.GREEN), false);
            reportLance(level, pos, player);
            return InteractionResult.SUCCESS;
        }

        player.displayClientMessage(Component.translatable(key + "incomplete", result.placed(), result.total())
                .withStyle(ChatFormatting.YELLOW), false);
        result.missingByBlock().entrySet().stream().limit(MAX_LISTED_TYPES).forEach(entry ->
                player.displayClientMessage(Component.translatable(key + "missing", entry.getValue(), entry.getKey().getName())
                        .withStyle(ChatFormatting.GRAY), false));
        BlockPos first = result.missing().get(0);
        player.displayClientMessage(Component.translatable(key + "first_missing", first.getX(), first.getY(), first.getZ())
                .withStyle(ChatFormatting.GRAY), false);

        if (player.isShiftKeyDown() && level instanceof ServerLevel server) {
            int shown = Math.min(MAX_HIGHLIGHTS, result.missing().size());
            for (int i = 0; i < shown; i++) {
                BlockPos p = result.missing().get(i);
                server.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.0);
            }
            player.displayClientMessage(Component.translatable(key + "highlighted", shown).withStyle(ChatFormatting.AQUA), false);
        }
        return InteractionResult.SUCCESS;
    }

    /** The Venting Lance's status: linked, incomplete (with the first gap), or not there at all. */
    private static void reportLance(Level level, BlockPos core, Player player) {
        String key = "message." + MasteriBHE.MODID + ".lance.";
        VentingLance.Report report = VentingLance.diagnose(level, core);
        if (report.none()) {
            player.displayClientMessage(Component.translatable(key + "none", VentingLance.MIN_BOTTOM, VentingLance.MAX_BOTTOM)
                    .withStyle(ChatFormatting.GRAY), false);
        } else if (report.complete()) {
            player.displayClientMessage(Component.translatable(key + "linked", core.getY() + report.bottom(),
                    core.getY() + report.bottom() + VentingLance.HEIGHT - 1).withStyle(ChatFormatting.GREEN), false);
        } else {
            BlockPos gap = report.firstGap();
            player.displayClientMessage(Component.translatable(key + "incomplete", report.placed(), VentingLance.BLOCKS,
                    gap.getX(), gap.getY(), gap.getZ()).withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /** On a Stabilizer Emitter: realigns it after a stabilizer desync (an ignition fault). */
    private static InteractionResult realign(Level level, BlockPos pos, Player player) {
        if (level.isClientSide || player == null) return InteractionResult.SUCCESS;
        ReactorCoreBlockEntity core = ReactorLocator.findCore(level, pos, ReactorStructure.STABILIZER_DISTANCE + 4);
        ReactorCoreBlockEntity.FixResult result = core == null ? ReactorCoreBlockEntity.FixResult.NOT_NEEDED : core.realignEmitter(pos);
        String key = switch (result) {
            case FIXED -> "realigned";
            case ALL_FIXED -> "realigned_all";
            case NOT_NEEDED -> "not_needed";
        };
        player.displayClientMessage(Component.translatable("message." + MasteriBHE.MODID + ".fault." + key)
                .withStyle(result == ReactorCoreBlockEntity.FixResult.NOT_NEEDED ? ChatFormatting.GRAY : ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + MasteriBHE.MODID + ".configurator").withStyle(ChatFormatting.GRAY));
    }
}
