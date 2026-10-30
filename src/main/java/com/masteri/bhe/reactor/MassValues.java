package com.masteri.bhe.reactor;

import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * How much mass things are worth when fed to a stabilizer, a bomb, a Horizon Gate or a black hole. Every block is
 * blockMass (1 by default); the Event Horizon's dense materials are worth far more (all from the config). Other items
 * aren't mass at all (though a black hole still swallows them, at swallowedItemMass each).
 */
public final class MassValues {
    /** Mass one of this item is worth: 0 if it isn't mass. */
    public static long per(Item item) {
        long neutronium = ReactorConfig.NEUTRONIUM_MASS.get();
        if (item == ModItems.NEUTRONIUM_INGOT.get()) return neutronium;
        if (item == ModBlocks.NEUTRONIUM_BLOCK.get().asItem()) return neutronium * 9;
        if (item == ModItems.RAW_NEUTRONIUM.get()) return Math.max(1, neutronium / 2);
        if (item == ModItems.MASS_SHARD.get()) return ReactorConfig.MASS_SHARD_MASS.get();
        if (item == ModItems.SINGULARITY_HEART.get()) return ReactorConfig.SINGULARITY_HEART_MASS.get();
        return item instanceof BlockItem ? ReactorConfig.BLOCK_MASS.get() : 0;
    }

    public static boolean isMass(ItemStack stack) {
        return !stack.isEmpty() && per(stack.getItem()) > 0;
    }

    /** The whole stack's mass. */
    public static long of(ItemStack stack) {
        return stack.isEmpty() ? 0 : per(stack.getItem()) * stack.getCount();
    }

    private MassValues() {}
}
