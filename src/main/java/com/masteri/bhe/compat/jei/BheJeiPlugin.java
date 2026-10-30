package com.masteri.bhe.compat.jei;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.item.DebugReactorItem;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.List;

/**
 * JEI support (only loaded when JEI is installed). JEI already shows the crafting and smelting recipes; this adds
 * info pages for everything that's made, found or dropped some other way (the research chamber, catching a black
 * hole, zones, mobs, the Event Horizon and its ruins), and hides the creative-only debug tools.
 */
@JeiPlugin
public class BheJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // The reactor and research
        info(registration, "reactor", ModBlocks.REACTOR_CORE.get());
        info(registration, "containment_box", ModBlocks.CONTAINMENT_BOX.get(), ModItems.SINGULARITY_CONTAINER.get());
        info(registration, "recording", ModItems.RECORDING_DISC_STALL.get(), ModItems.RECORDING_OVERSPIN.get(),
                ModItems.RECORDING_STARVATION.get(), ModItems.RECORDING_PRE_MELTDOWN.get(), ModItems.RECORDING_BREACH.get(),
                ModItems.RECORDING_MELTDOWN.get());
        // Collapses and dark energy zones
        info(registration, "crater", ModItems.SINGULARITY_GLASS.get(), ModItems.EVENT_HORIZON_FRAGMENT.get());
        info(registration, "zone_ore", ModBlocks.UNSTABLE_DARK_ORE.get(), ModItems.DARK_MATTER_SHARD.get(),
                ModItems.UNSTABLE_DARK_RESIDUE.get(), ModBlocks.CORRUPTED_STONE.get());
        info(registration, "corrupted_mobs", ModItems.VOID_DUST.get(), ModItems.GRAVITON_CORE.get());
        info(registration, "crystal", ModItems.CHARGED_DARK_MATTER_CRYSTAL.get());
        // The Event Horizon
        info(registration, "horizon_ring", ModBlocks.HORIZON_GATE.get(), ModBlocks.HORIZON_FRAME.get());
        info(registration, "neutronium", ModBlocks.NEUTRONIUM_ORE.get(), ModItems.RAW_NEUTRONIUM.get(),
                ModItems.NEUTRONIUM_INGOT.get(), ModBlocks.NEUTRONIUM_BLOCK.get());
        info(registration, "mass_shard", ModItems.MASS_SHARD.get(), ModBlocks.MASS_SHARD_CLUSTER.get());
        info(registration, "graviton_well", ModBlocks.GRAVITON_WELL.get());
        info(registration, "hawking", ModItems.HAWKING_MOTE.get(), ModBlocks.HAWKING_BLOOM.get(), ModBlocks.HAWKING_CELL.get());
        info(registration, "horizon_recording", ModItems.RECORDING_SPAGHETTIFICATION.get(), ModItems.RECORDING_HAWKING.get());
        info(registration, "escape_recording", ModItems.ESCAPE_RECORDING.get());
        info(registration, "singularity", ModItems.SINGULARITY_LURE.get(), ModItems.SINGULARITY_HEART.get(), ModBlocks.SINGULARITY_ALTAR.get());
        info(registration, "reactor_key", ModItems.REACTOR_KEY_OVERSPUN.get(), ModItems.REACTOR_KEY_STARVED.get(),
                ModItems.REACTOR_KEY_BREACHED.get());
        info(registration, "telescope", ModBlocks.TELESCOPE.get());
        info(registration, "light_trap", ModBlocks.TRAP_CORE.get(), ModBlocks.LIGHT_PAD.get(), ModBlocks.LIGHT_SEAL.get());
        info(registration, "evaporating_rock", ModBlocks.EVAPORATING_ROCK.get());
    }

    /** One info page (lang key jei.masteris_bhe.info.{@code key}) shown on all the given items. */
    private static void info(IRecipeRegistration registration, String key, ItemLike... items) {
        List<ItemStack> stacks = java.util.Arrays.stream(items).map(ItemStack::new).toList();
        registration.addItemStackInfo(stacks, Component.translatable("jei." + MasteriBHE.MODID + ".info." + key));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        // The debug tools are for testing in creative; keep them out of JEI's list.
        List<ItemStack> debug = ModItems.ITEMS.getEntries().stream()
                .filter(item -> item.get() instanceof DebugReactorItem)
                .map(item -> new ItemStack(item.get()))
                .toList();
        if (!debug.isEmpty()) runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, debug);
    }
}
