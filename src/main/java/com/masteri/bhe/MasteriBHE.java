package com.masteri.bhe;

import com.masteri.bhe.compat.cc.CCCompat;
import com.masteri.bhe.compat.sable.SableCompat;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.network.StabilizerTerminal;
import com.masteri.bhe.reactor.DarkZones;
import com.masteri.bhe.registry.ModAttachments;
import com.masteri.bhe.registry.ModBlockEntities;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModCapabilities;
import com.masteri.bhe.registry.ModCreativeTabs;
import com.masteri.bhe.registry.ModItems;
import com.masteri.bhe.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(MasteriBHE.MODID)
public class MasteriBHE {
    public static final String MODID = "masteris_bhe";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MasteriBHE(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(ModCapabilities::register);
        modEventBus.addListener(DarkZones::registerPayloads);
        modEventBus.addListener(StabilizerTerminal::registerPayloads);
        if (ModList.get().isLoaded(CCCompat.MOD_ID)) modEventBus.addListener(CCCompat::register); // optional
        if (ModList.get().isLoaded(SableCompat.MOD_ID)) SableCompat.init(); // optional
        modContainer.registerConfig(ModConfig.Type.SERVER, ReactorConfig.SPEC);
        LOGGER.info("Masteri's Black Hole Energy loaded. Don't aim the lance at the sun.");
    }
}
