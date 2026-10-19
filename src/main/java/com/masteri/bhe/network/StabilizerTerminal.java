package com.masteri.bhe.network;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.client.StabilizerConsoleScreen;
import com.masteri.bhe.multiblock.ReactorStructure;
import com.masteri.bhe.reactor.ReactorLocator;
import com.masteri.bhe.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Stabilizer Console's command line. The client sends what was typed; the server runs it against the
 * stabilizer the console is built into and sends back the lines to print. Commands:
 * {@code help}, {@code status}, {@code lens recalibrate} (fixes a cracked lens) and {@code clear} (client only).
 */
public final class StabilizerTerminal {
    public static final int MAX_LENGTH = 48;
    private static final double MAX_REACH_SQR = 8 * 8;
    private static final String KEY = "terminal." + MasteriBHE.MODID + ".";

    public record CommandPayload(BlockPos console, String command) implements CustomPacketPayload {
        public static final Type<CommandPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "terminal_command"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CommandPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, CommandPayload::console, ByteBufCodecs.stringUtf8(MAX_LENGTH), CommandPayload::command,
                CommandPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OutputPayload(List<Component> lines) implements CustomPacketPayload {
        public static final Type<OutputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "terminal_output"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputPayload> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), OutputPayload::lines, OutputPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Mod bus. The output handler only ever runs on the client. */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(CommandPayload.TYPE, CommandPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) run(player, payload.console(), payload.command());
                        }))
                .playToClient(OutputPayload.TYPE, OutputPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> StabilizerConsoleScreen.receive(payload.lines())));
    }

    private static void run(ServerPlayer player, BlockPos console, String typed) {
        Level level = player.level();
        if (!level.isLoaded(console) || !level.getBlockState(console).is(ModBlocks.STABILIZER_CONSOLE.get())
                || player.distanceToSqr(console.getCenter()) > MAX_REACH_SQR) {
            return;
        }
        List<Component> out = new ArrayList<>();
        ReactorCoreBlockEntity core = ReactorLocator.findCore(level, console,
                ReactorStructure.STABILIZER_DISTANCE + ReactorStructure.STABILIZER_HALF_LENGTH + 3);
        Direction side = core == null ? null : core.stabilizerConsoleSide(console);
        String command = typed.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        if (command.equals("help")) {
            out.add(line("help.header", ChatFormatting.AQUA));
            out.add(line("help.status", ChatFormatting.GRAY));
            out.add(line("help.lens", ChatFormatting.GRAY));
            out.add(line("help.clear", ChatFormatting.GRAY));
        } else if (side == null) {
            out.add(line("no_reactor", ChatFormatting.RED));
        } else if (command.equals("status")) {
            status(core, side, out);
        } else if (command.equals("lens recalibrate")) {
            ReactorCoreBlockEntity.FixResult result = core.recalibrateLens(side);
            switch (result) {
                case FIXED -> out.add(line("lens.fixed", ChatFormatting.GREEN));
                case ALL_FIXED -> {
                    out.add(line("lens.fixed", ChatFormatting.GREEN));
                    out.add(line("lens.all_fixed", ChatFormatting.GREEN));
                }
                case NOT_NEEDED -> out.add(line("lens.not_needed", ChatFormatting.GRAY));
            }
        } else if (command.startsWith("lens")) {
            out.add(Component.translatable(KEY + "lens.usage").withStyle(ChatFormatting.YELLOW));
        } else {
            out.add(Component.translatable(KEY + "unknown", typed.trim()).withStyle(ChatFormatting.RED));
        }
        PacketDistributor.sendToPlayer(player, new OutputPayload(out));
    }

    private static void status(ReactorCoreBlockEntity core, Direction side, List<Component> out) {
        out.add(Component.translatable(KEY + "status.header", Component.translatable("direction." + MasteriBHE.MODID + "." + side.getName()))
                .withStyle(ChatFormatting.AQUA));
        out.add(Component.translatable(KEY + "status.reactor",
                Component.translatable(core.getState().translationKey()).withStyle(core.getState().color)).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable(KEY + "status.mass", String.format("%,d", core.stabilizerMass(side))).withStyle(ChatFormatting.GRAY));
        boolean cracked = core.getFault() == ReactorCoreBlockEntity.Fault.CRACKED_LENS && core.isFaulted(side);
        boolean desync = core.getFault() == ReactorCoreBlockEntity.Fault.STABILIZER_DESYNC && core.isFaulted(side);
        out.add(Component.translatable(KEY + "status.lens", Component.translatable(KEY + (cracked ? "lens.cracked" : "lens.ok"))
                .withStyle(cracked ? ChatFormatting.RED : ChatFormatting.GREEN)).withStyle(ChatFormatting.GRAY));
        if (desync) out.add(line("status.desync", ChatFormatting.RED));
        boolean over = (core.breakerMask() & (1 << side.get2DDataValue())) != 0;
        out.add(Component.translatable(KEY + "status.breakers", Component.translatable(KEY + (over ? "breakers.overcharged" : "breakers.normal"))
                .withStyle(over ? ChatFormatting.GOLD : ChatFormatting.GREEN)).withStyle(ChatFormatting.GRAY));
        if (cracked) out.add(line("status.hint_lens", ChatFormatting.YELLOW));
    }

    private static Component line(String key, ChatFormatting color) {
        return Component.translatable(KEY + key).withStyle(color);
    }

    private StabilizerTerminal() {}
}
