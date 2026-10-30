package com.masteri.bhe.block.entity;

import com.masteri.bhe.entity.LightEchoEntity;
import com.masteri.bhe.horizon.EventHorizon;
import com.masteri.bhe.registry.ModBlockEntities;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModEntities;
import com.masteri.bhe.block.LightPadBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The heart of a Light Trap: a speck of black hole that light orbits for five seconds before it gets out. Every
 * player who comes near gets an echo (LightEchoEntity) replaying them five seconds late. The vault under it is
 * sealed in Light Seals; they fall away once every Light Pad in the trap is stood on at the same moment, by
 * players or their echoes.
 */
public class TrapCoreBlockEntity extends BlockEntity {
    private static final int RANGE = 13;
    private final Map<UUID, Integer> echoes = new HashMap<>();
    private final List<BlockPos> pads = new ArrayList<>();
    private boolean padsFound, opened;

    public TrapCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAP_CORE.get(), pos, state);
    }

    public boolean isOpened() {
        return opened;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        if (!padsFound) findPads(server);
        keepEchoes(server);
        if (server.getGameTime() % 2 == 0) checkPads(server);
    }

    private void findPads(ServerLevel server) {
        padsFound = true;
        pads.clear();
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-RANGE, -RANGE, -RANGE), worldPosition.offset(RANGE, RANGE, RANGE))) {
            if (server.getBlockState(p).is(ModBlocks.LIGHT_PAD.get())) pads.add(p.immutable());
        }
    }

    /** One echo for each player near enough; a new one if theirs went away. */
    private void keepEchoes(ServerLevel server) {
        for (Player player : server.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(RANGE))) {
            if (player.isSpectator()) continue;
            Integer id = echoes.get(player.getUUID());
            Entity existing = id == null ? null : server.getEntity(id);
            if (existing instanceof LightEchoEntity && existing.isAlive()) continue;
            LightEchoEntity echo = ModEntities.LIGHT_ECHO.get().create(server);
            if (echo == null) continue;
            echo.setOwner(player.getUUID());
            echo.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            server.addFreshEntity(echo);
            echoes.put(player.getUUID(), echo.getId());
            if (!opened) player.displayClientMessage(Component.translatable(EventHorizon.key("trap_enter")).withStyle(ChatFormatting.AQUA), true);
        }
        // Echoes whose players left the trap fade out.
        echoes.entrySet().removeIf(e -> {
            Entity echo = server.getEntity(e.getValue());
            Player player = server.getPlayerByUUID(e.getKey());
            boolean gone = player == null || player.distanceToSqr(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()) > (RANGE + 6) * (RANGE + 6);
            if (gone && echo != null) echo.discard();
            return gone || echo == null || !echo.isAlive();
        });
    }

    private void checkPads(ServerLevel server) {
        int lit = 0;
        for (BlockPos pad : pads) {
            BlockState state = server.getBlockState(pad);
            if (!state.is(ModBlocks.LIGHT_PAD.get())) continue;
            AABB above = new AABB(pad.above()).expandTowards(0, 1, 0);
            boolean on = !server.getEntitiesOfClass(Entity.class, above, e -> (e instanceof Player p && !p.isSpectator())
                    || (e instanceof LightEchoEntity echo && echo.isShown())).isEmpty();
            if (on != state.getValue(LightPadBlock.LIT)) {
                server.setBlockAndUpdate(pad, state.setValue(LightPadBlock.LIT, on));
                server.playSound(null, pad, on ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.BLOCKS, 1F, on ? 1.4F : 0.8F);
            }
            if (on) lit++;
        }
        if (!opened && pads.size() >= 2 && lit == pads.size()) open(server);
    }

    /** All the pads at once: the seals round the vault fall away. */
    private void open(ServerLevel server) {
        opened = true;
        setChanged();
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-5, -8, -5), worldPosition.offset(5, 2, 5))) {
            if (server.getBlockState(p).is(ModBlocks.LIGHT_SEAL.get())) {
                server.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                server.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
            }
        }
        server.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2F, 1.6F);
        server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2F, 0.6F);
        Component text = Component.translatable(EventHorizon.key("trap_open")).withStyle(ChatFormatting.AQUA);
        for (Player player : server.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(RANGE))) player.displayClientMessage(text, true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Opened", opened);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        opened = tag.getBoolean("Opened");
    }
}
