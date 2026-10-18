package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.item.ConfiguratorItem;
import com.masteri.bhe.item.ManualItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MasteriBHE.MODID);

    static {
        // A block item for every block, in registration order.
        ModBlocks.BLOCKS.getEntries().forEach(ITEMS::registerSimpleBlockItem);
    }

    public static final DeferredItem<ManualItem> REACTOR_MANUAL = ITEMS.registerItem("reactor_manual",
            ManualItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<ConfiguratorItem> CONFIGURATOR = ITEMS.registerItem("configurator",
            ConfiguratorItem::new, new Item.Properties().stacksTo(1));

    // Zone materials
    public static final DeferredItem<Item> DARK_MATTER_SHARD = ITEMS.registerSimpleItem("dark_matter_shard");
    public static final DeferredItem<Item> CHARGED_DARK_MATTER_CRYSTAL = ITEMS.registerSimpleItem("charged_dark_matter_crystal",
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> VOID_DUST = ITEMS.registerSimpleItem("void_dust");
    public static final DeferredItem<Item> UNSTABLE_DARK_RESIDUE = ITEMS.registerSimpleItem("unstable_dark_residue");
    public static final DeferredItem<Item> SINGULARITY_GLASS = ITEMS.registerSimpleItem("singularity_glass",
            new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> EVENT_HORIZON_FRAGMENT = ITEMS.registerSimpleItem("event_horizon_fragment",
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(16));
    public static final DeferredItem<Item> GRAVITON_CORE = ITEMS.registerSimpleItem("graviton_core",
            new Item.Properties().rarity(Rarity.RARE));

    private ModItems() {}
}
