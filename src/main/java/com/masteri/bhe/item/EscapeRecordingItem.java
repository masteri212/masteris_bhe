package com.masteri.bhe.item;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.horizon.EventHorizon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;

import java.util.List;

/**
 * Escape Recording: a recording of something getting out. Played (held for two seconds) inside the Event Horizon,
 * it takes you back to the gate you came in through, like a bed would. Used up.
 */
public class EscapeRecordingItem extends Item {
    private static final int USE_TICKS = 40;

    public EscapeRecordingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!EventHorizon.isHorizon(level)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable(EventHorizon.key("escape_outside")).withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 0.6F);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level instanceof ServerLevel server && remaining % 4 == 0) {
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, user.getX(), user.getY() + 1, user.getZ(), 10, 0.4, 0.8, 0.4, 0.2);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (user instanceof ServerPlayer player && EventHorizon.isHorizon(level)) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            EventHorizon.leave(player);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + MasteriBHE.MODID + ".escape_recording").withStyle(ChatFormatting.GRAY));
    }
}
