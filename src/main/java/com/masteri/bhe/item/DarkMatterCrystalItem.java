package com.masteri.bhe.item;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.reactor.DarkZones;
import com.masteri.bhe.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Charged Dark Matter Crystal. Use on an Unstable Dark Ore node inside a dark energy zone to stabilize it: the node
 * turns to Corrupted Stone, the crystal is used up, and the zone shrinks. The zone's last node removes it.
 */
public class DarkMatterCrystalItem extends Item {
    public DarkMatterCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!level.getBlockState(pos).is(ModBlocks.UNSTABLE_DARK_ORE.get())) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

        String key = "message." + MasteriBHE.MODID + ".zone.";
        DarkZones.Stabilized result = DarkZones.get(server).stabilize(server, pos);
        if (result == null) {
            if (player != null) player.displayClientMessage(Component.translatable(key + "not_in_zone").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        level.setBlock(pos, ModBlocks.CORRUPTED_STONE.get().defaultBlockState(), Block.UPDATE_ALL);
        if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        Vec3 c = Vec3.atCenterOf(pos);
        server.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.4, 0.4, 0.4, 0.1);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 60, 0.6, 0.6, 0.6, 0.5);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1F, 1.4F);

        Component message = result.zone() == null
                ? Component.translatable(key + "cleared").withStyle(ChatFormatting.AQUA)
                : Component.translatable(key + "stabilized", result.zone().radius(), result.zone().nodes()).withStyle(ChatFormatting.LIGHT_PURPLE);
        if (player != null) player.displayClientMessage(message, false);
        if (result.zone() == null) {
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2F, 0.6F);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + MasteriBHE.MODID + ".charged_dark_matter_crystal").withStyle(ChatFormatting.GRAY));
    }
}
