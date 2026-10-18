package com.masteri.bhe.event;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = MasteriBHE.MODID)
public final class CommonEvents {
    private static final String PERSISTED = "PlayerPersisted"; // survives death
    private static final String GOT_MANUAL = MasteriBHE.MODID + ":got_manual";

    /** Gives every player one Reactor Manual the first time they join a world. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(PERSISTED);
        if (persisted.getBoolean(GOT_MANUAL)) return;
        ItemStack manual = new ItemStack(ModItems.REACTOR_MANUAL.get());
        if (!player.getInventory().add(manual)) player.drop(manual, false);
        persisted.putBoolean(GOT_MANUAL, true);
        data.put(PERSISTED, persisted);
    }

    private CommonEvents() {}
}
