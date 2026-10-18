package com.masteri.bhe.item;

import com.masteri.bhe.MasteriBHE;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import vazkii.patchouli.api.PatchouliAPI;

import java.util.List;

/**
 * The Reactor Manual. Content lives in the Patchouli book "masteris_bhe:reactor_manual"
 * (data/masteris_bhe/patchouli_books and assets/masteris_bhe/patchouli_books).
 */
public class ManualItem extends Item {
    public static final ResourceLocation BOOK_ID = ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "reactor_manual");

    public ManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            PatchouliAPI.get().openBookGUI(serverPlayer, BOOK_ID);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + MasteriBHE.MODID + ".reactor_manual").withStyle(ChatFormatting.GRAY));
    }
}
