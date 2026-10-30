package com.masteri.bhe.entity;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.config.ReactorConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Applies the config's health and damage to this mod's mobs as they spawn or load. (Attributes are fixed at startup,
 * before any world's config is read, so they're set per mob here instead.) A mob at full health stays at full
 * health; a hurt one keeps its health, capped at the new maximum.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID)
public final class MobStats {
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        if (mob instanceof DrifterEntity) {
            apply(mob, ReactorConfig.DRIFTER_HEALTH.get(), -1);
        } else if (mob instanceof LensingStalkerEntity) {
            apply(mob, ReactorConfig.STALKER_HEALTH.get(), ReactorConfig.STALKER_DAMAGE.get());
        } else if (mob instanceof EchoOfTheCoreEntity) {
            apply(mob, ReactorConfig.ECHO_HEALTH.get(), -1);
        } else if (mob instanceof SpaghettifierEntity) {
            apply(mob, ReactorConfig.SPAGHETTIFIER_HEALTH.get(), ReactorConfig.SPAGHETTIFIER_DAMAGE.get());
        } else if (mob instanceof HawkingWispEntity) {
            apply(mob, ReactorConfig.WISP_HEALTH.get(), -1);
        }
    }

    /** Sets base max health (and attack damage, unless negative). */
    private static void apply(Mob mob, double health, double damage) {
        AttributeInstance max = mob.getAttribute(Attributes.MAX_HEALTH);
        if (max != null && max.getBaseValue() != health) {
            boolean full = mob.getHealth() >= mob.getMaxHealth();
            max.setBaseValue(health);
            mob.setHealth(full ? mob.getMaxHealth() : Math.min(mob.getHealth(), mob.getMaxHealth()));
        }
        if (damage >= 0) {
            AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attack != null) attack.setBaseValue(damage);
        }
    }

    private MobStats() {}
}
