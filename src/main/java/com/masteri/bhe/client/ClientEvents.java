package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.registry.ModBlockEntities;
import com.masteri.bhe.registry.ModEntities;
import com.masteri.bhe.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.resources.ResourceLocation;
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
        event.registerBlockEntityRenderer(ModBlockEntities.BLACK_HOLE_BOMB.get(), BlackHoleBombRenderer::new);
        event.registerEntityRenderer(ModEntities.DRIFTER.get(), DrifterRenderer::new);
        event.registerEntityRenderer(ModEntities.LENSING_STALKER.get(), StalkerRenderer::new);
        event.registerEntityRenderer(ModEntities.ECHO_OF_THE_CORE.get(), EchoRenderer::new);
        event.registerEntityRenderer(ModEntities.SPAGHETTIFIER.get(), SpaghettifierRenderer::new);
        event.registerEntityRenderer(ModEntities.HAWKING_WISP.get(), HawkingWispRenderer::new);
        event.registerEntityRenderer(ModEntities.SINGULARITY.get(), SingularityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HORIZON_GATE.get(), HorizonGateRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TRAP_CORE.get(), TrapCoreRenderer::new);
        event.registerEntityRenderer(ModEntities.LIGHT_ECHO.get(), LightEchoRenderer::new);
        event.registerEntityRenderer(ModEntities.DEBRIS_PLATFORM.get(), DebrisPlatformRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DrifterModel.LAYER, DrifterModel::createBodyLayer);
        event.registerLayerDefinition(StalkerModel.LAYER, StalkerModel::createBodyLayer);
        event.registerLayerDefinition(EchoModel.LAYER, EchoModel::createBodyLayer);
        event.registerLayerDefinition(SpaghettifierModel.LAYER, SpaghettifierModel::createBodyLayer);
    }

    /** The Event Horizon's sky (its dimension type names these effects). */
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "event_horizon"), new EventHorizonEffects());
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> DarkZoneShader.reset());
    }

    private ClientEvents() {}
}
