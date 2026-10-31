package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.item.DebugReactorItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MasteriBHE.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MasteriBHE.MODID))
                    .icon(() -> new ItemStack(ModBlocks.REACTOR_CORE.get()))
                    .displayItems((params, output) -> ModItems.ITEMS.getEntries().forEach(item -> {
                        if (!(item.get() instanceof DebugReactorItem)) output.accept(item.get());
                    }))
                    .build());

    /** The debug items (building, fueling or breaking a reactor instantly, previews...), kept apart from the real ones. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DEBUG = TABS.register("debug", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MasteriBHE.MODID + ".debug"))
                    .icon(() -> new ItemStack(ModItems.DEBUG_REACTOR_IGNITED.get()))
                    .withTabsBefore(MAIN.getKey())
                    .displayItems((params, output) -> ModItems.ITEMS.getEntries().forEach(item -> {
                        if (item.get() instanceof DebugReactorItem) output.accept(item.get());
                    }))
                    .build());

    private ModCreativeTabs() {}
}
