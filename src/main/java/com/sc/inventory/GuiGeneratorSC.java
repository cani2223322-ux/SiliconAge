package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.energy.GeneratorStatus;
import com.sc.energy.GeneratorType;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityGeneratorSC;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidTank;

/**
 * Generic GUI for every generator (§15). Shows the energy buffer plus whatever that generator
 * type actually has: a fuel tank for the fluid-burning ones, and the ignition charge bar for
 * the Fusion Reactor (§18.2), which used to only exist as a percentage buried in a status
 * string. The background sheet is only the frame: slot pockets and wells are drawn here per
 * type (ContainerGeneratorSC parks the unused slots off-screen), so a turbine doesn't show an
 * item slot it can't take and a solar panel shows a sun that's lit while it has light.
 */
public class GuiGeneratorSC extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.ASSETS, "textures/gui/guiGenerator.png");

    private static final int SLOT_FUEL_X = 26, SLOT_BLANKET_X = 44, SLOT_Y = 33;
    private static final int FUEL_X = 67, FUEL_Y = 17, FUEL_W = 8, FUEL_H = 44;
    private static final int SUN_X = 60, SUN_Y = 22;
    private static final int IGNITION_X = 84, IGNITION_Y = 33, IGNITION_W = 52, IGNITION_H = 8;
    private static final int ENERGY_X = 152, ENERGY_Y = 17, ENERGY_W = 10, ENERGY_H = 44;
    // Below the fuel gauge (which now ends at y 61): the longest status is 120 px wide.
    private static final int STATUS_X = 8, STATUS_Y = 66;

    private final TileEntityGeneratorSC generator;

    public GuiGeneratorSC(InventoryPlayer playerInv, TileEntityGeneratorSC generator) {
        super(new ContainerGeneratorSC(playerInv, generator));
        this.generator = generator;
        xSize = 176;
        ySize = 166;
    }

    private boolean hasFuelTank() {
        return generator.getGeneratorType().kind == GeneratorType.Kind.FLUID_FUEL;
    }

    private boolean isFusion() {
        return generator.getGeneratorType() == GeneratorType.FUSION_REACTOR;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiGaugeSC.bind(mc, TEXTURE);
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        drawTexturedModalRect(x, y, 0, 0, xSize, ySize);

        GeneratorType type = generator.getGeneratorType();
        if (type == GeneratorType.COMBUSTION || isFusion()) {
            pocket(x + SLOT_FUEL_X, y + SLOT_Y);
        }
        if (isFusion()) {
            pocket(x + SLOT_BLANKET_X, y + SLOT_Y);
            GuiGaugeSC.drawWell(x + IGNITION_X, y + IGNITION_Y, IGNITION_W, IGNITION_H);
            float charge = generator.isIgnited() ? 1f
                    : (float) generator.getIgnitionEU() / TileEntityGeneratorSC.getIgnitionThreshold();
            GuiGaugeSC.drawSpriteHorizontal(this, x + IGNITION_X, y + IGNITION_Y,
                    GuiGaugeSC.SPR_IGNITION_U, GuiGaugeSC.SPR_IGNITION_V, IGNITION_W, IGNITION_H, charge);
        }
        if (type.kind == GeneratorType.Kind.PASSIVE) {
            boolean lit = generator.getStatus() != GeneratorStatus.NO_SUNLIGHT;
            drawTexturedModalRect(x + SUN_X, y + SUN_Y, lit ? GuiGaugeSC.SPR_SUN_U : GuiGaugeSC.SPR_SUN_OFF_U,
                    GuiGaugeSC.SPR_SUN_V, 32, 32);
        }

        GuiGaugeSC.drawSpriteVertical(this, x + ENERGY_X, y + ENERGY_Y, GuiGaugeSC.SPR_ENERGY_U, GuiGaugeSC.SPR_ENERGY_V,
                ENERGY_W, ENERGY_H, (float) generator.getEnergyStored() / Math.max(1, generator.getMaxEnergyStored()));

        if (hasFuelTank()) {                    // last: drawFluid() leaves the blocks atlas bound
            FluidTank tank = generator.getFuelTank();
            GuiGaugeSC.drawWell(x + FUEL_X, y + FUEL_Y, FUEL_W, FUEL_H);
            GuiGaugeSC.drawFluid(mc, x + FUEL_X, y + FUEL_Y, FUEL_W, FUEL_H, tank.getFluid(), tank.getCapacity());
            GuiGaugeSC.bind(mc, TEXTURE);
            GuiGaugeSC.drawBlended(this, x + FUEL_X, y + FUEL_Y, GuiGaugeSC.SPR_GLASS_U, GuiGaugeSC.SPR_GLASS_V, FUEL_W, FUEL_H);
        }
    }

    /** 18x18 slot pocket whose 16x16 interior starts at (x, y), like a baked-in one would. */
    private void pocket(int x, int y) {
        drawTexturedModalRect(x - 1, y - 1, GuiGaugeSC.SPR_SLOT_U, GuiGaugeSC.SPR_SLOT_V, 18, 18);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(generator.getGeneratorType().localizedName(), 8, 6, 0x404040);
        GuiGaugeSC.drawTierBadge(fontRendererObj, generator.getGeneratorType().tier, xSize - 8, 4);
        GeneratorStatus status = generator.getStatus();
        fontRendererObj.drawString(status.localized(), STATUS_X, STATUS_Y, statusColor(status));
    }

    /**
     * Gauge tooltips are drawn after everything else, in screen space. GuiContainer hands
     * drawGuiContainerForegroundLayer the RAW screen mouse position (while GL is translated to
     * the panel), so hover tests there were off by (guiLeft, guiTop): the real gauges never
     * showed a tooltip and the panel's top-left corner did. Drawing here also avoids the
     * item-lighting drawHoveringText leaves behind for the cursor stack.
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tooltip = tooltipAt(mouseX - guiLeft, mouseY - guiTop);
        if (tooltip != null) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
    }

    private static int statusColor(GeneratorStatus status) {
        switch (status) {
            case GENERATING: return 0x2E7D32;
            case IGNITING: return 0x2A62A8;
            case BUFFER_FULL: return 0x9A6200;
            case IDLE: return 0x606060;
            default: return 0xB02418;          // every "no ..." / depleted state
        }
    }

    private List<String> tooltipAt(int mouseX, int mouseY) {
        List<String> lines = new ArrayList<String>();

        if (GuiGaugeSC.isOver(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(generator.getEnergyStored() + " / " + generator.getMaxEnergyStored() + " EU");
            lines.add(Lang.tr("sc.gui.output", generator.getGeneratorType().euPerTick));
            return lines;
        }

        if (hasFuelTank() && GuiGaugeSC.isOver(FUEL_X, FUEL_Y, FUEL_W, FUEL_H, mouseX, mouseY)) {
            FluidTank tank = generator.getFuelTank();
            lines.add(Lang.tr("sc.gui.fuel"));
            lines.add(GuiGaugeSC.fluidLabel(tank.getFluid(), tank.getCapacity()));
            lines.add(Lang.tr("sc.gui.fuel.rate", generator.getGeneratorType().fuelRatePerTick));
            return lines;
        }

        if (isFusion() && !generator.isIgnited()
                && GuiGaugeSC.isOver(IGNITION_X, IGNITION_Y, IGNITION_W, IGNITION_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.ignition"));
            lines.add(generator.getIgnitionEU() + " / " + TileEntityGeneratorSC.getIgnitionThreshold() + " EU");
            return lines;
        }

        // What belongs in each slot while it's still empty - a blank pocket says nothing on its
        // own, and vanilla only draws a tooltip for a slot that holds an item.
        if (generator.getGeneratorType() == GeneratorType.COMBUSTION
                && generator.getStackInSlot(TileEntityGeneratorSC.SLOT_FUEL) == null
                && GuiGaugeSC.isOver(SLOT_FUEL_X, SLOT_Y, 16, 16, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.slot.solidfuel"));
            lines.add(Lang.tr("sc.gui.slot.solidfuel.hint"));
            return lines;
        }
        if (isFusion()) {
            if (generator.getStackInSlot(TileEntityGeneratorSC.SLOT_FUEL) == null
                    && GuiGaugeSC.isOver(SLOT_FUEL_X, SLOT_Y, 16, 16, mouseX, mouseY)) {
                lines.add(Lang.tr("sc.gui.slot.deuterium"));
                return lines;
            }
            if (generator.getStackInSlot(TileEntityGeneratorSC.SLOT_BLANKET) == null
                    && GuiGaugeSC.isOver(SLOT_BLANKET_X, SLOT_Y, 16, 16, mouseX, mouseY)) {
                lines.add(Lang.tr("sc.gui.slot.blanket"));
                lines.add(Lang.tr("sc.gui.slot.blanket.hint"));
                return lines;
            }
        }
        return null;
    }
}
