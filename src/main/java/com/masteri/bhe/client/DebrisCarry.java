package com.masteri.bhe.client;

import com.masteri.bhe.entity.DebrisPlatformEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of riding a Debris Ring platform: the player's own client moves them along with the platform under
 * their feet (the server trusts the client's position for players, so it has to be done here).
 */
public final class DebrisCarry {
    public static void carry(DebrisPlatformEntity platform, AABB top, Vec3 delta) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isPassenger() || player.getAbilities().flying) return;
        if (!player.getBoundingBox().intersects(top)) return;
        player.setPos(player.getX() + delta.x, player.getY() + delta.y, player.getZ() + delta.z);
    }

    private DebrisCarry() {}
}
