package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.registry.ModBlockEntities;
import com.masteri.bhe.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

@EventBusSubscriber(modid = MasteriBHE.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CONSOLE.get(), ConsoleScreen::new);
        event.register(ModMenus.RESEARCH.get(), ResearchScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.REACTOR_CORE.get(), ReactorCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CONTROL_CONSOLE.get(), ConsoleMonitorRenderer::new);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> DarkZoneShader.reset());
    }

    private ClientEvents() {}
}
