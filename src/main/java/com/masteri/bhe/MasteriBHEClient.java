package com.masteri.bhe;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point: gives the mod a config screen in the Mods list (for the server config's settings). */
@Mod(value = MasteriBHE.MODID, dist = Dist.CLIENT)
public class MasteriBHEClient {
    public MasteriBHEClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
