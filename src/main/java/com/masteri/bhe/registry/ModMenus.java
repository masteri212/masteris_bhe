package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.menu.ConsoleMenu;
import com.masteri.bhe.menu.ResearchMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MasteriBHE.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ConsoleMenu>> CONSOLE =
            MENUS.register("control_console", () -> IMenuTypeExtension.create(ConsoleMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ResearchMenu>> RESEARCH =
            MENUS.register("research_core", () -> IMenuTypeExtension.create(ResearchMenu::new));

    private ModMenus() {}
}
