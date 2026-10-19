package com.masteri.bhe.client;

import com.masteri.bhe.block.entity.ResearchCoreBlockEntity;
import com.masteri.bhe.menu.ResearchMenu;
import com.masteri.bhe.research.Experiment;
import com.masteri.bhe.research.ResearchChamber;
import com.masteri.bhe.research.Step;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The Research Core's panel. Idle: the chamber's status and a button per experiment. Running: the procedure (done
 * steps ticked off, the current one highlighted with its countdown), the field and resonance lamp, and the controls.
 * Drawn with fills and text like the console, so it needs no texture.
 */
public class ResearchScreen extends AbstractContainerScreen<ResearchMenu> {
    private static final int BG = 0xFF101A1E, PANEL = 0xFF18262B, EDGE = 0xFF2F6A73, TEXT = 0xFFDDF2F5, DIM = 0xFF7F9EA4;
    private final List<Button> startButtons = new ArrayList<>(), controls = new ArrayList<>();

    public ResearchScreen(ResearchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 248;
        imageHeight = 204;
    }

    @Override
    protected void init() {
        super.init();
        startButtons.clear();
        controls.clear();
        Experiment[] all = Experiment.values();
        for (int i = 0; i < all.length; i++) {
            int x = leftPos + 10 + (i % 2) * 116, y = topPos + 70 + (i / 2) * 24;
            startButtons.add(button(i, Component.translatable(all[i].translationKey()), x, y, 112));
        }
        int y = topPos + 152;
        controls.add(button(ResearchCoreBlockEntity.BUTTON_FIELD_DOWN, key("field_down"), leftPos + 8, y, 56));
        controls.add(button(ResearchCoreBlockEntity.BUTTON_FIELD_UP, key("field_up"), leftPos + 66, y, 56));
        controls.add(button(ResearchCoreBlockEntity.BUTTON_LOCK, key("lock"), leftPos + 124, y, 56));
        controls.add(button(ResearchCoreBlockEntity.BUTTON_ABORT, key("abort").copy().withStyle(ChatFormatting.RED), leftPos + 182, y, 58));
        y += 24;
        controls.add(button(ResearchCoreBlockEntity.BUTTON_PULSE, key("pulse"), leftPos + 8, y, 76));
        controls.add(button(ResearchCoreBlockEntity.BUTTON_VENT, key("vent"), leftPos + 86, y, 76));
        controls.add(button(ResearchCoreBlockEntity.BUTTON_CHARGE, key("charge"), leftPos + 164, y, 76));
        updateVisibility();
    }

    private static Component key(String name) {
        return Component.translatable("gui.masteris_bhe.research.button." + name);
    }

    private Button button(int id, Component label, int x, int y, int width) {
        return addRenderableWidget(Button.builder(label,
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id)).bounds(x, y, width, 20).build());
    }

    private void updateVisibility() {
        boolean running = menu.experiment() != null;
        startButtons.forEach(b -> {
            b.visible = !running;
            b.active = menu.status() == ResearchChamber.Status.READY;
        });
        controls.forEach(b -> b.visible = running);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateVisibility();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, EDGE);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, BG);
        g.fill(leftPos + 6, topPos + 18, leftPos + imageWidth - 6, topPos + 146, PANEL);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TEXT, false);
        Experiment experiment = menu.experiment();
        if (experiment == null) {
            ResearchChamber.Status status = menu.status();
            int color = status == ResearchChamber.Status.READY ? 0xFF60E080 : 0xFFFF8040;
            g.drawString(font, Component.translatable("gui.masteris_bhe.research.status." + status.name().toLowerCase()), 12, 24, color, false);
            g.drawWordWrap(font, Component.translatable("gui.masteris_bhe.research.pick"), 12, 40, imageWidth - 24, DIM);
            return;
        }

        g.drawString(font, Component.translatable(experiment.translationKey()).withStyle(ChatFormatting.AQUA), 12, 24, TEXT, false);
        Component timer = Component.translatable("gui.masteris_bhe.timer", menu.seconds());
        g.drawString(font, timer, imageWidth - 12 - font.width(timer), 24, menu.seconds() <= 10 ? 0xFFFF6060 : DIM, false);

        int y = 38;
        for (int i = 0; i < menu.stepCount(); i++) {
            int code = menu.step(i);
            boolean done = i < menu.stepIndex(), current = i == menu.stepIndex();
            String mark = done ? "✔ " : current ? "▶ " : "  ";
            Component text = Component.literal(mark).append(Step.describe(code));
            if (current && Step.type(code) == Step.Type.FEED) {
                text = text.copy().append(Component.literal(" (" + menu.fed() + "/" + Step.feed(code).count() + ")"));
            }
            int color = done ? 0xFF4F8F5F : current ? 0xFFFFD060 : DIM;
            if (current) g.fill(8, y - 2, imageWidth - 8, y + 9, 0x30FFD060);
            g.drawString(font, text, 12, y, color, false);
            y += 12;
        }

        // Field gauge and the resonance lamp.
        int gy = 118;
        g.drawString(font, Component.translatable("gui.masteris_bhe.research.field", menu.field()), 12, gy, TEXT, false);
        int bx = 92, width = 90;
        g.fill(bx, gy, bx + width, gy + 8, 0xFF05090B);
        g.fill(bx, gy, bx + width * menu.field() / 100, gy + 8, 0xFF40B0D0);
        boolean open = menu.pulseOpen();
        g.fill(imageWidth - 52, gy - 1, imageWidth - 12, gy + 9, open ? 0xFF30C060 : 0xFF503030);
        g.drawCenteredString(font, Component.translatable("gui.masteris_bhe.research.resonance"), imageWidth - 32, gy, TEXT);
        g.drawString(font, Component.translatable(menu.powered() ? "gui.masteris_bhe.research.powered" : "gui.masteris_bhe.research.unpowered"),
                12, gy + 12, menu.powered() ? 0xFFFF6060 : DIM, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
