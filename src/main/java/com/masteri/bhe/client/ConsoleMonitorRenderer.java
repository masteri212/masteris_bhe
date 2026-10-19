package com.masteri.bhe.client;

import com.masteri.bhe.block.ControlConsoleBlock;
import com.masteri.bhe.block.entity.ControlConsoleBlockEntity;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.reactor.ReactorReadout;
import com.masteri.bhe.reactor.ReactorState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the control console's screen across the whole 4x3 wall: state, fault, stability and stress bars, the
 * numbers, the breakers during a disc stall, and the buttons (the one you're looking at lights up). Drawn in screen
 * units (1/64 block) from the top-left corner, matching the button layout in ControlConsoleBlock.
 */
public class ConsoleMonitorRenderer implements BlockEntityRenderer<ControlConsoleBlockEntity> {
    private static final int EDGE = 0xFF3A4D73, BG = 0xFF0B0E14, PANEL = 0xFF151A24, TEXT = 0xFFDDE6F5, DIM = 0xFF8090AA;
    private static final int W = ControlConsoleBlock.SCREEN_WIDTH, H = ControlConsoleBlock.SCREEN_HEIGHT;
    private static final float LAYER = 0.5F; // depth step between layers, in screen units

    private final Font font;

    public ConsoleMonitorRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(ControlConsoleBlockEntity console, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = console.getLevel();
        BlockState state = console.getBlockState();
        if (level == null || !(state.getBlock() instanceof ControlConsoleBlock)) return;
        Direction facing = state.getValue(ControlConsoleBlock.FACING);
        // Only a whole wall gets a screen (an old single-block console stays dark).
        BlockPos corner = console.getBlockPos().relative(facing.getCounterClockWise(), ControlConsoleBlock.COLUMNS - 1)
                .above(ControlConsoleBlock.ROWS - 1);
        BlockState cornerState = level.getBlockState(corner);
        if (!cornerState.is(state.getBlock()) || cornerState.getValue(ControlConsoleBlock.FACING) != facing) return;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, ControlConsoleBlock.ROWS - 0.5, 0.501);
        pose.scale(1F / ControlConsoleBlock.UNITS, -1F / ControlConsoleBlock.UNITS, 1F / ControlConsoleBlock.UNITS);
        Matrix4f m = pose.last().pose();

        fill(buffers, m, 0, 0, W, H, EDGE, 0);
        fill(buffers, m, 2, 2, W - 2, H - 2, BG, 1);
        if (!console.isLinked()) {
            Component text = Component.translatable("gui.masteris_bhe.monitor.no_core");
            text(buffers, m, text, (W - font.width(text)) / 2F, H / 2F - 4, 0xFFFF6060);
            pose.popPose();
            return;
        }
        ReactorReadout r = console.readout();
        fill(buffers, m, 6, 30, W - 6, 118, PANEL, 2);
        header(buffers, m, r);
        bars(buffers, m, r);
        resonance(buffers, m, r);
        // During a stall the breaker lamps matter more than the wobble (which is zero then anyway).
        if (r.state() == ReactorState.DISC_STALL) breakers(buffers, m, r.breakerMask());
        else wobble(buffers, m, r);
        numbers(buffers, m, r);
        buttons(buffers, m, r, hovered(console, state, r));
        pose.popPose();
    }

    private void header(MultiBufferSource buffers, Matrix4f m, ReactorReadout r) {
        ReactorState state = r.state();
        Component stateText = Component.translatable(state.translationKey()).withStyle(state.color);
        text(buffers, m, Component.translatable("gui.masteris_bhe.state", stateText), 8, 8, TEXT);
        if (state.isAlarm()) {
            Component timer = Component.translatable("gui.masteris_bhe.timer", r.timerSeconds());
            text(buffers, m, timer, W - 8 - font.width(timer), 8, 0xFFFF6060);
        }
        Component line = null;
        int color = 0xFFFFB040;
        if (!r.formed()) {
            line = Component.translatable("gui.masteris_bhe.not_formed");
            color = 0xFFFF8040;
        } else if (r.fault() != ReactorCoreBlockEntity.Fault.NONE) {
            ReactorCoreBlockEntity.Fault fault = r.fault();
            Object arg = fault == ReactorCoreBlockEntity.Fault.SINGULARITY_REBOUND ? "" : ReactorCoreBlockEntity.sideNames(r.faultMask());
            line = r.faultFixed() ? Component.translatable("gui.masteris_bhe.fault.fixed")
                    : Component.translatable("gui.masteris_bhe.fault." + fault.name().toLowerCase(), arg);
            if (r.faultFixed()) color = 0xFF60E080;
        } else if (r.state() == ReactorState.DISC_STALL) {
            line = Component.translatable("gui.masteris_bhe.monitor.stall_hint");
        } else if (!r.axialFormed()) {
            line = Component.translatable("gui.masteris_bhe.monitor.no_axial");
            color = DIM;
        }
        if (line != null) text(buffers, m, line, 8, 19, color);
    }

    private void bars(MultiBufferSource buffers, Matrix4f m, ReactorReadout r) {
        double stability = r.stability(), stress = r.stress();
        bar(buffers, m, 33, "stability", stability, stability < 20 ? 0xFFE04040 : stability < 50 ? 0xFFE0B040 : 0xFF40D080);
        bar(buffers, m, 44, "stress", stress, stress > 80 ? 0xFFE04040 : 0xFF4090E0);
    }

    /**
     * The resonance band on a spin scale: the band in green, the disc's spin as a white marker. The label is the
     * spin-to-RF efficiency right now.
     */
    private void resonance(MultiBufferSource buffers, Matrix4f m, ReactorReadout r) {
        int y = 55, x0 = 116, x1 = W - 12;
        int efficiency = r.efficiency();
        int color = !r.axialFormed() ? DIM : efficiency >= 90 ? 0xFF60E080 : efficiency >= 50 ? 0xFFE0B040 : 0xFFFF6060;
        text(buffers, m, Component.translatable("gui.masteris_bhe.monitor.resonance", efficiency), 12, y, color);
        fill(buffers, m, x0, y, x1, y + 8, 0xFF05070B, 3);
        double center = r.resonanceRpm(), speed = Math.abs(r.rpm());
        double scale = Math.max(center * 2, speed * 1.1);
        double half = ReactorConfig.RESONANCE_WIDTH.get();
        float b0 = (float) (x0 + (x1 - x0) * Math.max(0, center - half) / scale);
        float b1 = (float) (x0 + (x1 - x0) * Math.min(scale, center + half) / scale);
        fill(buffers, m, b0, y, b1, y + 8, 0xFF1E6A3A, 4);
        float at = (float) (x0 + (x1 - x0) * Math.min(scale, speed) / scale);
        fill(buffers, m, at - 1, y - 1, at + 1, y + 9, 0xFFFFFFFF, 5);
    }

    /** The disc's lean, north-west to the left and south-east to the right, with the axial trim beside it. */
    private void wobble(MultiBufferSource buffers, Matrix4f m, ReactorReadout r) {
        int y = 66, x0 = 116, x1 = W - 12;
        int lean = r.wobble(), size = Math.abs(lean);
        int color = size < 25 ? 0xFF60E080 : size < 60 ? 0xFFE0B040 : 0xFFFF6060;
        text(buffers, m, Component.translatable("gui.masteris_bhe.monitor.wobble", r.axialTrim()), 12, y, r.axialFormed() ? TEXT : DIM);
        fill(buffers, m, x0, y, x1, y + 8, 0xFF05070B, 3);
        float mid = (x0 + x1) / 2F, safe = (x1 - x0) / 2F * 0.25F;
        fill(buffers, m, mid - safe, y, mid + safe, y + 8, 0xFF1E3A2A, 4);
        float at = mid + (x1 - x0) / 2F * Math.max(-100, Math.min(100, lean)) / 100F;
        fill(buffers, m, Math.min(mid, at), y + 2, Math.max(mid, at), y + 6, color, 5);
        fill(buffers, m, at - 1, y - 1, at + 1, y + 9, 0xFFFFFFFF, 5);
    }

    private void bar(MultiBufferSource buffers, Matrix4f m, int y, String key, double percent, int color) {
        text(buffers, m, Component.translatable("gui.masteris_bhe." + key, String.format("%.1f", percent)), 12, y, TEXT);
        int x0 = 116, x1 = W - 12;
        fill(buffers, m, x0, y, x1, y + 8, 0xFF05070B, 3);
        int filled = (int) ((x1 - x0) * Math.max(0, Math.min(100, percent)) / 100);
        if (filled > 0) fill(buffers, m, x0, y, x0 + filled, y + 8, color, 4);
    }

    private void numbers(MultiBufferSource buffers, Matrix4f m, ReactorReadout r) {
        int y = 80, right = 132;
        text(buffers, m, Component.translatable("gui.masteris_bhe.spin", r.rpm()), 12, y, DIM);
        text(buffers, m, Component.translatable("gui.masteris_bhe.offset", r.offset(), r.targetOffset()), right, y, DIM);
        text(buffers, m, Component.translatable("gui.masteris_bhe.mass", String.format("%,d", r.mass())), 12, y + 12, DIM);
        text(buffers, m, Component.translatable("gui.masteris_bhe.laser", r.laserPower()), right, y + 12, DIM);
        text(buffers, m, Component.translatable("gui.masteris_bhe.energy", String.format("%,d", r.energy())), 12, y + 24, DIM);
        text(buffers, m, Component.translatable("gui.masteris_bhe.output", String.format("%,d", r.output())), right, y + 24, DIM);
    }

    /** One lamp per stabilizer: amber once its breakers are overcharged. */
    private void breakers(MultiBufferSource buffers, Matrix4f m, int mask) {
        int y = 65;
        Component label = Component.translatable("gui.masteris_bhe.monitor.breakers");
        text(buffers, m, label, 12, y + 2, TEXT);
        int x = 16 + font.width(label);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            boolean on = (mask & (1 << dir.get2DDataValue())) != 0;
            fill(buffers, m, x, y, x + 26, y + 11, on ? 0xFFE0A030 : 0xFF2A3040, 3);
            Component name = Component.literal(dir.getName().substring(0, 1).toUpperCase());
            text(buffers, m, name, x + 13 - font.width(name) / 2F, y + 2, on ? 0xFF201000 : DIM);
            x += 30;
        }
    }

    private void buttons(MultiBufferSource buffers, Matrix4f m, ReactorReadout r,
                         @Nullable ControlConsoleBlock.Button hovered) {
        for (ControlConsoleBlock.Button b : ControlConsoleBlock.BUTTONS) {
            Component label;
            int face = 0xFF243049;
            if (b.id() == ReactorCoreBlockEntity.BUTTON_VENT) {
                if (!r.lanceFormed()) continue;
                label = Component.translatable(r.ventArmed() ? "gui.masteris_bhe.button.vent_confirm" : "gui.masteris_bhe.button.vent")
                        .withStyle(r.ventArmed() ? ChatFormatting.RED : ChatFormatting.GOLD);
                face = 0xFF3A2410;
            } else if (b.id() == ReactorCoreBlockEntity.BUTTON_JUMP_START && r.state().isFault()) {
                label = Component.translatable("gui.masteris_bhe.button.reset")
                        .withStyle(r.faultFixed() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
            } else {
                label = Component.translatable("gui.masteris_bhe.button." + b.key());
                if (b.id() == ReactorCoreBlockEntity.BUTTON_SCRAM) face = 0xFF4A1A1A;
                if (b.id() == ReactorCoreBlockEntity.BUTTON_JUMP_START && r.state() == ReactorState.DISC_STALL) {
                    face = (r.breakerMask() & 0b1111) == 0b1111 ? 0xFF1E5A30 : 0xFF3A3A1A;
                }
            }
            boolean hot = b == hovered;
            fill(buffers, m, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), hot ? 0xFF8AA4D8 : EDGE, 2);
            fill(buffers, m, b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + b.h() - 1, hot ? brighten(face) : face, 3);
            text(buffers, m, label, b.x() + (b.w() - font.width(label)) / 2F, b.y() + (b.h() - 7) / 2F, TEXT);
        }
    }

    /** The button under the crosshair, if the player is looking at this wall's screen. */
    @Nullable
    private static ControlConsoleBlock.Button hovered(ControlConsoleBlockEntity console, BlockState state, ReactorReadout r) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || console.getLevel() == null) return null;
        BlockState looked = console.getLevel().getBlockState(hit.getBlockPos());
        if (!looked.is(state.getBlock()) || !ControlConsoleBlock.origin(hit.getBlockPos(), looked).equals(console.getBlockPos())) return null;
        double[] at = ControlConsoleBlock.toScreen(hit.getBlockPos(), looked, hit);
        return at == null ? null : ControlConsoleBlock.buttonAt(at[0], at[1], r.lanceFormed());
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 40), g = Math.min(255, ((argb >> 8) & 0xFF) + 40), b = Math.min(255, (argb & 0xFF) + 40);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * The buffer is fetched for every quad: drawing text in between switches render types, which ends (closes) the
     * previous buffer, so holding on to one across text crashes ("Not building!").
     */
    private static void fill(MultiBufferSource buffers, Matrix4f m, float x0, float y0, float x1, float y1, int argb, int layer) {
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        float z = layer * LAYER;
        vc.addVertex(m, x0, y0, z).setColor(argb);
        vc.addVertex(m, x0, y1, z).setColor(argb);
        vc.addVertex(m, x1, y1, z).setColor(argb);
        vc.addVertex(m, x1, y0, z).setColor(argb);
    }

    private void text(MultiBufferSource buffers, Matrix4f m, Component text, float x, float y, int color) {
        Matrix4f lifted = new Matrix4f(m).translate(0, 0, 6 * LAYER);
        font.drawInBatch(text, x, y, color, false, lifted, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    @Override
    public AABB getRenderBoundingBox(ControlConsoleBlockEntity console) {
        return new AABB(console.getBlockPos()).inflate(ControlConsoleBlock.COLUMNS);
    }
}
