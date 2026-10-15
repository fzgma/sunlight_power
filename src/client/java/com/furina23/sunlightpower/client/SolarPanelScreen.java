package com.furina23.sunlightpower.client;

import com.furina23.sunlightpower.SunlightPower;
import com.furina23.sunlightpower.menu.SolarPanelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class SolarPanelScreen extends AbstractContainerScreen<SolarPanelMenu> {
    private static final ResourceLocation BACKGROUND = new ResourceLocation(SunlightPower.MOD_ID, "textures/gui/solar.png");
    private static final ResourceLocation ELEMENTS = new ResourceLocation(SunlightPower.MOD_ID, "textures/gui/elements.png");
    private static final int GAUGE_WIDTH = 18;
    private static final int GAUGE_HEIGHT = 50;
    private static final int GAUGE_INNER_HEIGHT = 48;
    private static final int SUN_X = 120;
    private static final int ENERGY_X = 150;
    private static final int GAUGE_Y = 40;

    public SolarPanelScreen(SolarPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 180;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        drawGauge(graphics, SUN_X, 32, intensity(), mouseX, mouseY);
        drawGauge(graphics, ENERGY_X, 0, menu.capacity() > 0
                ? (double) menu.energy() / menu.capacity() : 0, mouseX, mouseY);
        graphics.blit(ELEMENTS, leftPos + 150, topPos + 8, 18, 18, 18, 18);
    }

    private void drawGauge(GuiGraphics graphics, int x, int sourceX, double fraction, int mouseX, int mouseY) {
        int px = leftPos + x;
        int py = topPos + GAUGE_Y;
        graphics.blit(ELEMENTS, px + 1, py + 1, sourceX + 16, 64, 16, GAUGE_INNER_HEIGHT);
        int filled = (int) Math.round(GAUGE_INNER_HEIGHT * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            graphics.blit(ELEMENTS, px + 1, py + 1 + GAUGE_INNER_HEIGHT - filled,
                    sourceX, 64 + GAUGE_INNER_HEIGHT - filled, 16, filled);
        }
        graphics.blit(ELEMENTS, px, py, 64, 62, GAUGE_WIDTH, GAUGE_HEIGHT);
        if (isGaugeHovered(x, mouseX, mouseY)) {
            graphics.fill(px + 1, py + 1, px + 17, py + 49, 0x88ffffff);
            graphics.blit(ELEMENTS, px, py, 82, 62, GAUGE_WIDTH, GAUGE_HEIGHT);
        }
    }

    private double intensity() {
        return menu.peakGeneration() > 0
                ? (double) menu.generation() / menu.peakGeneration() : 0;
    }

    private boolean isGaugeHovered(int x, int mouseX, int mouseY) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + GAUGE_WIDTH
                && mouseY >= topPos + GAUGE_Y && mouseY < topPos + GAUGE_Y + GAUGE_HEIGHT;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 4, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 94, 0x404040, false);
        graphics.pose().pushPose();
        graphics.pose().translate(8, 14, 0);
        graphics.pose().scale(0.9f, 0.9f, 1);
        graphics.drawString(font, Component.translatable("gui.sunlight_power.stored", menu.energy()), 0, 0, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.sunlight_power.capacity", menu.capacity()), 0, 10, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.sunlight_power.generation", menu.generation()), 0, 20, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.sunlight_power.efficiency",
                Math.round(100 * intensity())), 0, 30, 0x404040, false);
        graphics.pose().popPose();
        graphics.drawString(font, Component.translatable("gui.sunlight_power.charging_slot"),
                8, 60, 0x404040, false);
        graphics.drawString(font, Component.translatable(menu.getSlot(0).hasItem()
                        ? "gui.sunlight_power.charging_occupied" : "gui.sunlight_power.charging_empty"),
                8, 72, 0x606060, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, delta);
        renderTooltip(graphics, mouseX, mouseY);
        if (menu.getCarried().isEmpty()) {
            if (hoveredSlot == menu.getSlot(0) && !hoveredSlot.hasItem()) {
                graphics.renderTooltip(font, Component.translatable("gui.sunlight_power.charging_hint"),
                        mouseX, mouseY);
            } else if (isGaugeHovered(ENERGY_X, mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.translatable("gui.sunlight_power.stored_total",
                        menu.energy(), menu.capacity()), mouseX, mouseY);
            } else if (isGaugeHovered(SUN_X, mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.translatable("gui.sunlight_power.sun_intensity",
                        Math.round(100 * intensity())), mouseX, mouseY);
            }
        }
    }
}
