package com.masteri.bhe.client;

import com.masteri.bhe.network.StabilizerTerminal;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** The Stabilizer Console's command line: a scrolling green-on-black log and a prompt. Enter runs the command. */
public class StabilizerConsoleScreen extends Screen {
    private static final int WIDTH = 260, HEIGHT = 180, LINE = 10, MAX_LOG = 200;
    private static final int BG = 0xFF0A0F0C, EDGE = 0xFF3A5A44, TEXT = 0xFF60E080;
    /** The log survives closing and reopening the same console. */
    private static final List<Component> LOG = new ArrayList<>();
    private static BlockPos logFor;

    private final BlockPos console;
    private EditBox input;
    private int left, top;

    public static void open(BlockPos console) {
        Minecraft.getInstance().setScreen(new StabilizerConsoleScreen(console));
    }

    /** Output from the server: printed if this console's screen is still open (and kept in the log either way). */
    public static void receive(List<Component> lines) {
        lines.forEach(StabilizerConsoleScreen::print);
    }

    private static void print(Component line) {
        LOG.add(line);
        while (LOG.size() > MAX_LOG) LOG.remove(0);
    }

    private StabilizerConsoleScreen(BlockPos console) {
        super(Component.translatable("container.masteris_bhe.stabilizer_console"));
        this.console = console.immutable();
        if (!console.equals(logFor)) {
            LOG.clear();
            logFor = this.console;
            print(Component.translatable("terminal.masteris_bhe.welcome").withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        input = new EditBox(font, left + 16, top + HEIGHT - 18, WIDTH - 24, 12, Component.empty());
        input.setMaxLength(StabilizerTerminal.MAX_LENGTH);
        input.setBordered(false);
        input.setTextColor(TEXT);
        addRenderableWidget(input);
        setInitialFocus(input);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            String command = input.getValue().trim();
            input.setValue("");
            if (!command.isEmpty()) {
                print(Component.literal("> " + command).withStyle(ChatFormatting.WHITE));
                if (command.equalsIgnoreCase("clear")) LOG.clear();
                else PacketDistributor.sendToServer(new StabilizerTerminal.CommandPayload(console, command));
            }
            return true;
        }
        // Keep the inventory key from closing the screen while typing; Escape still closes it.
        if (input.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) return input.keyPressed(key, scanCode, modifiers) || true;
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + WIDTH, top + HEIGHT, EDGE);
        g.fill(left + 2, top + 2, left + WIDTH - 2, top + HEIGHT - 2, BG);
        g.drawString(font, title, left + 8, top + 6, 0xFF9AD0A8, false);
        g.fill(left + 6, top + 17, left + WIDTH - 6, top + 18, EDGE);

        // Newest at the bottom; long lines wrap.
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : LOG) wrapped.addAll(font.split(line, WIDTH - 16));
        int rows = (HEIGHT - 44) / LINE;
        int first = Math.max(0, wrapped.size() - rows);
        for (int i = first; i < wrapped.size(); i++) {
            g.drawString(font, wrapped.get(i), left + 8, top + 22 + (i - first) * LINE, TEXT, false);
        }

        g.fill(left + 6, top + HEIGHT - 22, left + WIDTH - 6, top + HEIGHT - 21, EDGE);
        g.drawString(font, ">", left + 8, top + HEIGHT - 18, TEXT, false);
        input.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
