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
        if (!com.masteri.bhe.config.ReactorConfig.LURE_ENABLED.get()) {
            player.displayClientMessage(Component.translatable(EventHorizon.key("lure_disabled")).withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
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
        if (SingularityEntity.rise(server, new Vec3(open.x, open.y - 2, open.z)) == null) return InteractionResultHolder.fail(stack);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + MasteriBHE.MODID + ".singularity_lure").withStyle(ChatFormatting.GRAY));
    }
}
