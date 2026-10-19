package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.item.ConfiguratorItem;
import com.masteri.bhe.item.DarkMatterCrystalItem;
import com.masteri.bhe.item.DebugReactorItem;
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
    public static final DeferredItem<DarkMatterCrystalItem> CHARGED_DARK_MATTER_CRYSTAL = ITEMS.registerItem("charged_dark_matter_crystal",
            DarkMatterCrystalItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> VOID_DUST = ITEMS.registerSimpleItem("void_dust");
    public static final DeferredItem<Item> UNSTABLE_DARK_RESIDUE = ITEMS.registerSimpleItem("unstable_dark_residue");
    public static final DeferredItem<Item> SINGULARITY_GLASS = ITEMS.registerSimpleItem("singularity_glass",
            new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> EVENT_HORIZON_FRAGMENT = ITEMS.registerSimpleItem("event_horizon_fragment",
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(16));
    public static final DeferredItem<Item> GRAVITON_CORE = ITEMS.registerSimpleItem("graviton_core",
            new Item.Properties().rarity(Rarity.RARE));

    // Research
    /** Thrown into a running reactor's black hole, it comes back out as a Containment Box holding a small one. */
    public static final DeferredItem<Item> SINGULARITY_CONTAINER = ITEMS.registerSimpleItem("singularity_container",
            new Item.Properties().rarity(Rarity.RARE).stacksTo(16));
    public static final DeferredItem<Item> RECORDING_DISC_STALL = recording("disc_stall");
    public static final DeferredItem<Item> RECORDING_OVERSPIN = recording("overspin");
    public static final DeferredItem<Item> RECORDING_STARVATION = recording("starvation");
    public static final DeferredItem<Item> RECORDING_PRE_MELTDOWN = recording("pre_meltdown");
    public static final DeferredItem<Item> RECORDING_BREACH = recording("breach");
    public static final DeferredItem<Item> RECORDING_MELTDOWN = recording("meltdown");

    /** A failure recreated in the Research Chamber, as a crafting component. */
    private static DeferredItem<Item> recording(String failure) {
        return ITEMS.registerSimpleItem("recording_" + failure, new Item.Properties().rarity(Rarity.UNCOMMON));
    }

    // Debug (creative only)
    public static final DeferredItem<DebugReactorItem> DEBUG_REACTOR_FRAME = debug("debug_reactor_frame", DebugReactorItem.Mode.FRAME);
    public static final DeferredItem<DebugReactorItem> DEBUG_REACTOR_FUELED = debug("debug_reactor_fueled", DebugReactorItem.Mode.FUELED);
    public static final DeferredItem<DebugReactorItem> DEBUG_REACTOR_IGNITED = debug("debug_reactor_ignited", DebugReactorItem.Mode.IGNITED);
    public static final DeferredItem<DebugReactorItem> DEBUG_REACTOR_DISMANTLER = debug("debug_reactor_dismantler", DebugReactorItem.Mode.DISMANTLE);
    public static final DeferredItem<DebugReactorItem> DEBUG_FAILURE_PREVIEW = debug("debug_failure_preview", DebugReactorItem.Mode.FAILURE_PREVIEW);
    public static final DeferredItem<DebugReactorItem> DEBUG_DARK_ZONE = debug("debug_dark_zone", DebugReactorItem.Mode.ZONE);
    public static final DeferredItem<DebugReactorItem> DEBUG_RESEARCH_CHAMBER = debug("debug_research_chamber", DebugReactorItem.Mode.RESEARCH);
    public static final DeferredItem<DebugReactorItem> DEBUG_MASS = debug("debug_mass", DebugReactorItem.Mode.MASS);

    private static DeferredItem<DebugReactorItem> debug(String name, DebugReactorItem.Mode mode) {
        return ITEMS.registerItem(name, props -> new DebugReactorItem(props, mode),
                new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    private ModItems() {}
}
