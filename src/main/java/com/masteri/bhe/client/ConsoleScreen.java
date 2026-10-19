package com.masteri.bhe.client;

import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.menu.ConsoleMenu;
import com.masteri.bhe.reactor.ReactorState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Drawn entirely with fills and text so it needs no texture yet. */
public class ConsoleScreen extends AbstractContainerScreen<ConsoleMenu> {
    private static final int BG = 0xFF151A24, PANEL = 0xFF1E2533, EDGE = 0xFF3A4D73, TEXT = 0xFFDDE6F5, DIM = 0xFF8090AA;

    public ConsoleScreen(ConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 240;
        imageHeight = 196;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 8, y = topPos + 144;
        button(ReactorCoreBlockEntity.BUTTON_IGNITE, "ignite", x, y, 54);
        button(ReactorCoreBlockEntity.BUTTON_SHUTDOWN, "shutdown", x + 56, y, 58);
        button(ReactorCoreBlockEntity.BUTTON_SCRAM, "scram", x + 116, y, 52);
        jumpButton = button(ReactorCoreBlockEntity.BUTTON_JUMP_START, "jump", x + 170, y, 54);
        y += 22;
        button(ReactorCoreBlockEntity.BUTTON_OFFSET_DOWN, "offset_down", x, y, 54);
        button(ReactorCoreBlockEntity.BUTTON_OFFSET_UP, "offset_up", x + 56, y, 58);
        button(ReactorCoreBlockEntity.BUTTON_LASER_DOWN, "laser_down", x + 116, y, 52);
        button(ReactorCoreBlockEntity.BUTTON_LASER_UP, "laser_up", x + 170, y, 54);
        ventButton = button(ReactorCoreBlockEntity.BUTTON_VENT, "vent", leftPos + 164, topPos + 114, 68);
        ventButton.visible = menu.lanceFormed();
    }

    private Button ventButton, jumpButton;

    private Button button(int id, String key, int x, int y, int width) {
        return addRenderableWidget(Button.builder(Component.translatable("gui.masteris_bhe.button." + key),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id)).bounds(x, y, width, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Vent only shows with a lance linked; once armed it asks for the second press.
        // During a fault the jump-start button is RESET: grey until the cause is fixed, then green.
        if (menu.state().isFault()) {
            jumpButton.setMessage(Component.translatable("gui.masteris_bhe.button.reset")
                    .withStyle(menu.faultFixed() ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.DARK_GRAY));
        } else {
            jumpButton.setMessage(Component.translatable("gui.masteris_bhe.button.jump"));
        }
        ventButton.visible = menu.lanceFormed();
        ventButton.setMessage(Component.translatable(menu.ventArmed() ? "gui.masteris_bhe.button.vent_confirm" : "gui.masteris_bhe.button.vent")
                .withStyle(menu.ventArmed() ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GOLD));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, EDGE);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, BG);
        g.fill(leftPos + 6, topPos + 18, leftPos + imageWidth - 6, topPos + 138, PANEL);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TEXT, false);
        ReactorState state = menu.state();
        Component stateText = Component.translatable(state.translationKey()).withStyle(state.color);
        g.drawString(font, Component.translatable("gui.masteris_bhe.state", stateText), 12, 24, TEXT, false);
        if (state.isAlarm() || state == ReactorState.DISC_STALL) {
            g.drawString(font, Component.translatable("gui.masteris_bhe.timer", menu.timerSeconds()), 160, 24, 0xFFFF6060, false);
        }
        if (!menu.formed()) {
            g.drawString(font, Component.translatable("gui.masteris_bhe.not_formed"), 12, 36, 0xFFFF8040, false);
        } else if (menu.fault() != ReactorCoreBlockEntity.Fault.NONE) {
            // What went wrong and how to fix it, in one (slightly smaller) line.
            ReactorCoreBlockEntity.Fault fault = menu.fault();
            Object arg = fault == ReactorCoreBlockEntity.Fault.SINGULARITY_REBOUND ? "" : sides(menu.faultMask());
            Component text = menu.faultFixed()
                    ? Component.translatable("gui.masteris_bhe.fault.fixed")
                    : Component.translatable("gui.masteris_bhe.fault." + fault.name().toLowerCase(), arg);
            g.pose().pushPose();
            g.pose().translate(12, 37, 0);
            g.pose().scale(0.8F, 0.8F, 1);
            g.drawString(font, text, 0, 0, menu.faultFixed() ? 0xFF60E080 : 0xFFFFB040, false);
            g.pose().popPose();
        }

        bar(g, 12, 50, "stability", menu.stability(), menu.stability() < 20 ? 0xFFE04040 : menu.stability() < 50 ? 0xFFE0B040 : 0xFF40D080);
        bar(g, 12, 66, "stress", menu.stress(), menu.stress() > 80 ? 0xFFE04040 : 0xFF4090E0);

        int y = 84;
        line(g, 12, y, Component.translatable("gui.masteris_bhe.spin", menu.rpm()));
        line(g, 124, y, Component.translatable("gui.masteris_bhe.offset", menu.offset(), menu.targetOffset()));
        line(g, 12, y + 12, Component.translatable("gui.masteris_bhe.mass", String.format("%,d", menu.mass())));
        line(g, 124, y + 12, Component.translatable("gui.masteris_bhe.laser", menu.laserPower()));
        line(g, 12, y + 24, Component.translatable("gui.masteris_bhe.energy", String.format("%,d", menu.energy())));
        line(g, 12, y + 36, Component.translatable("gui.masteris_bhe.output", String.format("%,d", menu.output())));

        if (state == ReactorState.DISC_STALL) {
            // Under the countdown, clear of the VENT button in the bottom right of the panel.
            int breakers = Integer.bitCount(menu.breakerMask() & 0b1111);
            g.fill(176, 34, 230, 46, breakers == 4 ? 0xFF30C060 : 0xFF503030);
            g.drawCenteredString(font, Component.translatable("gui.masteris_bhe.breakers", breakers), 203, 36, TEXT);
        }
    }

    private static Component sides(int mask) {
        net.minecraft.network.chat.MutableComponent text = Component.empty();
        boolean first = true;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if ((mask & (1 << dir.get2DDataValue())) == 0) continue;
            if (!first) text.append(", ");
            text.append(Component.translatable("direction.masteris_bhe." + dir.getName()));
            first = false;
        }
        return text;
    }

    private void line(GuiGraphics g, int x, int y, Component text) {
        g.drawString(font, text, x, y, DIM, false);
    }

    private void bar(GuiGraphics g, int x, int y, String key, double percent, int color) {
        g.drawString(font, Component.translatable("gui.masteris_bhe." + key, String.format("%.1f", percent)), x, y, TEXT, false);
        int bx = x + 100, width = 112;
        g.fill(bx, y, bx + width, y + 8, 0xFF0B0E14);
        g.fill(bx, y, bx + (int) (width * Math.max(0, Math.min(100, percent)) / 100), y + 8, color);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
