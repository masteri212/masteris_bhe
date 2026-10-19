package com.masteri.bhe.research;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * One step of an experiment's procedure. Some are done with the Research Core's controls (set the field and LOCK,
 * PULSE in the resonance window, VENT, CHARGE), the rest out in the world (feed the core an item, power or unpower
 * it with redstone, tune the containment box with the Configurator). A step is stored as one int:
 * {@code type * 100 + param}.
 */
public final class Step {
    public enum Type { FIELD, PULSE, VENT, CHARGE, FEED, POWER_ON, POWER_OFF, TUNE }

    /** What the FEED step's param picks: the item and how many. */
    public record Feed(Item item, int count) {}

    public static final List<Feed> FEEDS = List.of(
            new Feed(Items.REDSTONE, 8), new Feed(Items.GLOWSTONE_DUST, 4), new Feed(Items.ENDER_PEARL, 1),
            new Feed(Items.AMETHYST_SHARD, 4), new Feed(Items.QUARTZ, 4), new Feed(Items.LAPIS_LAZULI, 6));

    public static int encode(Type type, int param) {
        return type.ordinal() * 100 + param;
    }

    public static Type type(int code) {
        Type[] all = Type.values();
        int i = code / 100;
        return i >= 0 && i < all.length ? all[i] : Type.VENT;
    }

    public static int param(int code) {
        return code % 100;
    }

    public static Feed feed(int code) {
        return FEEDS.get(Math.floorMod(param(code), FEEDS.size()));
    }

    /**
     * A random procedure: never the same kind of step twice in a row, redstone only switched off after it was
     * switched on, and always finished with a PULSE that sets the failure off.
     */
    public static int[] generate(int count, RandomSource random) {
        int[] steps = new int[count];
        boolean powered = false;
        Type previous = null;
        for (int i = 0; i < count - 1; i++) {
            List<Type> options = new ArrayList<>(List.of(Type.FIELD, Type.VENT, Type.CHARGE, Type.FEED, Type.TUNE, Type.PULSE));
            options.add(powered ? Type.POWER_OFF : Type.POWER_ON);
            options.remove(previous);
            if (i == count - 2) options.remove(Type.PULSE); // the last step is a PULSE anyway
            Type type = options.get(random.nextInt(options.size()));
            int param = switch (type) {
                case FIELD -> 10 * (1 + random.nextInt(9)); // 10..90%
                case FEED -> random.nextInt(FEEDS.size());
                default -> 0;
            };
            if (type == Type.POWER_ON) powered = true;
            if (type == Type.POWER_OFF) powered = false;
            steps[i] = encode(type, param);
            previous = type;
        }
        steps[count - 1] = encode(Type.PULSE, 0);
        return steps;
    }

    /** What the procedure list says for this step. */
    public static Component describe(int code) {
        String key = "step.masteris_bhe." + type(code).name().toLowerCase();
        return switch (type(code)) {
            case FIELD -> Component.translatable(key, param(code));
            case FEED -> Component.translatable(key, feed(code).count(), feed(code).item().getDescription());
            default -> Component.translatable(key);
        };
    }

    private Step() {}
}
