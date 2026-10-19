package com.masteri.bhe.reactor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;

/**
 * Asks the server to load (or generate) chunks the crater or a dark energy zone still has to work on, so their
 * destruction doesn't stop at whatever happened to be loaded. The chunks load in the background; the tickets run
 * out on their own after 15 seconds, and requesting a chunk again keeps it loaded longer.
 */
public final class ChunkTickets {
    private static final TicketType<ChunkPos> TYPE = TicketType.create("masteris_bhe_destruction",
            Comparator.comparingLong(ChunkPos::toLong), 300);

    public static void request(ServerLevel level, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        level.getChunkSource().addRegionTicket(TYPE, pos, 1, pos); // its neighbors come up too, for lighting
    }

    private ChunkTickets() {}
}
