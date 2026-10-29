package com.masteri.bhe.item;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.SingularityEntity;
import com.masteri.bhe.horizon.EventHorizon;
import com.masteri.bhe.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Singularity Lure: used deep in the Event Horizon (in the tidal depths), it calls the Singularity up out of the
 * dark a little way off. One Singularity at a time near you; the lure is used up.
 */
public class SingularityLureItem extends Item {
    private static final double DISTANCE = 22, ALREADY = 160;

    public SingularityLureItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer)) return InteractionResultHolder.success(stack);
        if (!EventHorizon.isHorizon(level) || player.getY() > EventHorizon.DEEP_START) {
            player.displayClientMessage(Component.translatable(EventHorizon.key("lure_too_high")).withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!server.getEntitiesOfClass(SingularityEntity.class, new AABB(player.blockPosition()).inflate(ALREADY)).isEmpty()) {
            player.displayClientMessage(Component.translatable(EventHorizon.key("lure_already")).withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        // Out in front of the player, roughly level with them, in a clear pocket of air (never in an island, where it
        // couldn't be seen, and never down at the spaghettification line).
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = new Vec3(1, 0, 0);
        Vec3 preferred = player.position().add(look.normalize().scale(DISTANCE));
        preferred = new Vec3(preferred.x, Math.max(EventHorizon.spaghettificationY() + 14, player.getY() - 2), preferred.z);
        Vec3 open = SingularityEntity.findOpenSpot(server, player.position(), preferred);
        Vec3 at = new Vec3(open.x, open.y - 2, open.z); // the entity's feet: its middle is 2 up
        double y = at.y;
        SingularityEntity singularity = ModEntities.SINGULARITY.get().create(server);
        if (singularity == null) return InteractionResultHolder.fail(stack);
        singularity.moveTo(at.x, y, at.z, 0, 0);
        singularity.setHome(BlockPos.containing(at.x, y, at.z));
        singularity.finalizeSpawn(server, server.getCurrentDifficultyAt(singularity.blockPosition()), MobSpawnType.EVENT, null);
        server.addFreshEntity(singularity);
        server.sendParticles(ParticleTypes.FLASH, at.x, y + 2, at.z, 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, y + 2, at.z, 300, 4, 4, 4, 0.6);
        server.playSound(null, singularity.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4F, 0.4F);
        Component text = Component.translatable(EventHorizon.key("singularity_risen")).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : server.players()) {
            if (p.distanceToSqr(singularity) < 128 * 128) p.sendSystemMessage(text);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + MasteriBHE.MODID + ".singularity_lure").withStyle(ChatFormatting.GRAY));
    }
}
