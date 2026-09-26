package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.energy.GeneratorStatus;
import com.sc.energy.GeneratorType;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityGeneratorSC;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidTank;

/**
 * GUI for every generator, in the machines' steel style. It shows the energy buffer plus what
 * that generator actually has: its item slots, up to three tanks (fuel, second fuel, the Fuel
 * Cell's water), the sun of a solar panel, wind / water / heat readings, the ignition charge and
 * plasma heat of a reactor, the Creative Generator's tier button - and, for every one, its
 * status and what it makes right now. Upgrades sit in a side panel, like a machine's.
 */
public class GuiGeneratorSC extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.ASSETS, "textures/gui/guiGenerator.png");

    /** Compact tank gauges (GuiTankGaugeSC.drawCompact). */
    private static final int TANK_Y = 16, TANK_W = 11, TANK_H = 40;
    private static final int[] TANK_X = {66, 78, 90};
    private static final int SUN_X = 60, SUN_Y = 20;
    private static final int INFO_X = 84, INFO_Y = 20;
    private static final int IGNITION_X = 84, IGNITION_Y = 20, IGNITION_W = 52, IGNITION_H = 8;
    private static final int HEAT_X = 84, HEAT_Y = 42, HEAT_W = 42, HEAT_H = 5;
    /** Text rooms end at ENERGY_X; the energy gauge (GuiEnergyGaugeSC) sits over the sheet's old well. */
    private static final int ENERGY_X = 152, GAUGE_X = 150, GAUGE_Y = 16, GAUGE_W = 22, GAUGE_H = 54;
    private static final int STATUS_X = 8, STATUS_Y = 58, OUTPUT_Y = 68;
    private static final int PANEL_W = 32, PANEL_H = 86;

    private final TileEntityGeneratorSC generator;
    private final GeneratorType type;

    public GuiGeneratorSC(InventoryPlayer playerInv, TileEntityGeneratorSC generator) {
        super(new ContainerGeneratorSC(playerInv, generator));
        this.generator = generator;
        this.type = generator.getGeneratorType();
        xSize = TileEntityGeneratorSC.hasUpgradeSlots(type) ? ContainerGeneratorSC.PANEL_X + PANEL_W : 176;
        ySize = 166;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        if (type == GeneratorType.CREATIVE) {
            buttonList.add(new TextFitSC.Button(ContainerGeneratorSC.BTN_CREATIVE_TIER, guiLeft + 30, guiTop + 26, 100, 20, ""));
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id == ContainerGeneratorSC.BTN_CREATIVE_TIER) {
                b.displayString = Lang.tr("sc.gui.gen.creativetier", generator.getCreativeTier().name(), generator.getCreativeTier().getVoltage());
                b.enabled = mc.thePlayer.capabilities.isCreativeMode;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
    }

    /** Tanks this generator shows: 1 (fuel / coolant), 2 (two fuels), 3 (+ the Fuel Cell's water). */
    private int tankCount() {
        switch (type.kind) {
            case FLUID_FUEL:
            case EXO:
                return 1;
            case DUAL_FLUID:
                return type == GeneratorType.FUEL_CELL ? 3 : 2;
            default:
                return 0;
        }
    }

    private FluidTank tank(int i) {
        return i == 0 ? generator.getFuelTank() : i == 1 ? generator.getFuelTank2() : generator.getOutTank();
    }

    private boolean isReactor() {
        return type.needsIgnition();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiGaugeSC.bind(mc, TEXTURE);
        int x = guiLeft, y = guiTop;
        drawTexturedModalRect(x, y, 0, 0, 176, ySize);
        if (TileEntityGeneratorSC.hasUpgradeSlots(type)) {
            drawUpgradePanel(x, y);
        }

        for (int slot = 0; slot < TileEntityGeneratorSC.FIRST_UPGRADE_SLOT; slot++) {
            if (TileEntityGeneratorSC.usesSlot(type, slot)) {
                int sx = slot == 0 ? ContainerGeneratorSC.SLOT_FUEL_X : ContainerGeneratorSC.SLOT_BLANKET_X;
                drawTexturedModalRect(x + sx - 1, y + ContainerGeneratorSC.SLOT_Y - 1, GuiGaugeSC.SPR_STEEL_SLOT_U, GuiGaugeSC.SPR_STEEL_SLOT_V, 18, 18);
            }
        }
        if (isReactor()) {
            GuiGaugeSC.drawWell(x + IGNITION_X, y + IGNITION_Y, IGNITION_W, IGNITION_H);
            float charge = generator.isIgnited() ? 1f : (float) generator.getIgnitionEU() / type.ignitionThreshold();
            GuiGaugeSC.drawSpriteHorizontal(this, x + IGNITION_X, y + IGNITION_Y,
                    GuiGaugeSC.SPR_IGNITION_U, GuiGaugeSC.SPR_IGNITION_V, IGNITION_W, IGNITION_H, charge);
            GuiGaugeSC.drawWell(x + HEAT_X, y + HEAT_Y, HEAT_W, HEAT_H);
            GuiGaugeSC.drawSpriteHorizontal(this, x + HEAT_X, y + HEAT_Y, GuiGaugeSC.SPR_HEAT_U, GuiGaugeSC.SPR_HEAT_V,
                    HEAT_W, HEAT_H, (float) generator.getHeat() / TileEntityGeneratorSC.HEAT_LIMIT);
        }
        if (type.kind == GeneratorType.Kind.PASSIVE) {
            boolean lit = generator.getStatus() != GeneratorStatus.NO_SUNLIGHT;
            drawTexturedModalRect(x + SUN_X, y + SUN_Y, lit ? GuiGaugeSC.SPR_SUN_U : GuiGaugeSC.SPR_SUN_OFF_U,
                    GuiGaugeSC.SPR_SUN_V, 32, 32);
        }

        GuiEnergyGaugeSC.draw(x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H,
                (float) generator.getEnergyStored() / Math.max(1, generator.getMaxEnergyStored()));
        GuiGaugeSC.bind(mc, TEXTURE);

        for (int i = 0; i < tankCount(); i++) {      // last: drawFluid() leaves the blocks atlas bound
            FluidTank t = tank(i);
            GuiTankGaugeSC.drawCompact(mc, x + TANK_X[i], y + TANK_Y, TANK_W, TANK_H, t.getFluid(), t.getCapacity(), false);
            GuiGaugeSC.bind(mc, TEXTURE);
        }
        org.lwjgl.opengl.GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The upgrade panel, same as a machine's (GuiMachineSC). */
    private void drawUpgradePanel(int x, int y) {
        int x0 = x + ContainerGeneratorSC.PANEL_X - 1, y0 = y, x1 = x + ContainerGeneratorSC.PANEL_X + PANEL_W, y1 = y0 + PANEL_H;
        drawRect(x0, y0, x1, y1, GuiGaugeSC.OUTLINE);
        drawRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GuiGaugeSC.PANEL);
        drawRect(x0 + 1, y0 + 1, x1 - 2, y0 + 2, GuiGaugeSC.BEVEL_LIGHT);
        drawRect(x0 + 1, y0 + 1, x0 + 2, y1 - 2, GuiGaugeSC.BEVEL_LIGHT);
        drawRect(x1 - 2, y0 + 2, x1 - 1, y1 - 1, GuiGaugeSC.BEVEL_DARK);
        drawRect(x0 + 2, y1 - 2, x1 - 1, y1 - 1, GuiGaugeSC.BEVEL_DARK);
        org.lwjgl.opengl.GL11.glColor4f(1F, 1F, 1F, 1F);
        GuiGaugeSC.bind(mc, TEXTURE);
        for (int i = 0; i < TileEntityGeneratorSC.UPGRADE_SLOTS; i++) {
            drawTexturedModalRect(x + ContainerGeneratorSC.UPGRADE_X - 1, y + ContainerGeneratorSC.UPGRADE_Y - 1 + i * 18,
                    GuiGaugeSC.SPR_STEEL_SLOT_U, GuiGaugeSC.SPR_STEEL_SLOT_V, 18, 18);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fit(type.localizedName(), 8, 5, titleRoom(generator.outputTier()), GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, generator.outputTier(), 176 - 6, 3);
        GeneratorStatus status = generator.getStatus();
        fit(status.localized(), STATUS_X, STATUS_Y, ENERGY_X - 4 - STATUS_X, statusColor(status));
        fit(Lang.tr("sc.gui.gen.now", generator.getLastOutput()), STATUS_X, OUTPUT_Y, ENERGY_X - 4 - STATUS_X, 0x404040);
        if (isReactor()) {                // the plasma heat under its bar
            fit(Lang.tr("sc.gui.gen.plasma.short", generator.getHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT),
                    HEAT_X, HEAT_Y + HEAT_H + 2, ENERGY_X - 4 - HEAT_X, 0x8A3A10);
        }
        int c = 0x404040;
        switch (type.kind) {
            case PASSIVE: {
                boolean sky = status != GeneratorStatus.NO_SUNLIGHT;
                String when = !sky ? Lang.tr("sc.gui.gen.nosky")
                        : Lang.tr(mc.theWorld.isDaytime() ? "sc.gui.gen.day" : "sc.gui.gen.night");
                fit(when, 96, 26, ENERGY_X - 100, c);
                if (sky && mc.theWorld.isRaining()) {
                    fit(Lang.tr("sc.gui.gen.rain"), 96, 36, ENERGY_X - 100, 0x2A62A8);
                }
                break;
            }
            case WIND:
                fit(Lang.tr("sc.gui.gen.height", generator.getInfoA()), INFO_X - 16, INFO_Y + 2, ENERGY_X - 4 - (INFO_X - 16), c);
                fit(Lang.tr("sc.gui.gen.free", generator.getInfoB()), INFO_X - 16, INFO_Y + 12, ENERGY_X - 4 - (INFO_X - 16), c);
                break;
            case WATER:
                fit(Lang.tr("sc.gui.gen.flow", generator.getInfoA()), INFO_X - 16, INFO_Y + 6, ENERGY_X - 4 - (INFO_X - 16), c);
                break;
            case THERMO:
                fit(Lang.tr("sc.gui.gen.pairs", generator.getInfoA()), INFO_X - 16, INFO_Y + 2, ENERGY_X - 4 - (INFO_X - 16), c);
                fit(Lang.tr("sc.gui.gen.dt", generator.getInfoB()), INFO_X - 16, INFO_Y + 12, ENERGY_X - 4 - (INFO_X - 16), c);
                break;
            case RTG:
                fit(Lang.tr("sc.gui.gen.capsules", generator.getInfoA()), INFO_X - 16, INFO_Y + 6, ENERGY_X - 4 - (INFO_X - 16), c);
                break;
            default:
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tooltip = tooltipAt(mouseX - guiLeft, mouseY - guiTop);
        if (tooltip == null) {
            tooltip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tooltip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tooltip, width), mouseX, mouseY, fontRendererObj);
        }
    }

    /** A string that fits its room (smaller, or cut with the full text as a tooltip) - foreground coordinates. */
    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    /** Room for the title: up to the tier plate at the right of the title bar. */
    private int titleRoom(com.sc.energy.Tier tier) {
        return 176 - 6 - (fontRendererObj.getStringWidth(tier.name()) + 4) - 4 - 8;
    }

    private static int statusColor(GeneratorStatus status) {
        switch (status) {
            case GENERATING: return 0x2E7D32;
            case IGNITING: return 0x2A62A8;
            case BUFFER_FULL:
            case WATER_FULL: return 0x9A6200;
            case IDLE: return 0x606060;
            default: return 0xB02418;          // every "no ..." / depleted / overheated state
        }
    }

    private List<String> tooltipAt(int mouseX, int mouseY) {
        List<String> lines = new ArrayList<String>();

        if (GuiGaugeSC.isOver(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(generator.getEnergyStored() + " / " + generator.getMaxEnergyStored() + " EU");
            lines.add(Lang.tr("sc.gui.output", type == GeneratorType.CREATIVE ? generator.getCreativeTier().getVoltage() : generator.ratedOutput()));
            lines.add(Lang.tr("sc.gui.gen.packet", generator.outputTier().name(), generator.outputTier().getVoltage()));
            return lines;
        }

        for (int i = 0; i < tankCount(); i++) {
            if (GuiGaugeSC.isOver(TANK_X[i], TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
                FluidTank t = tank(i);
                lines.add(Lang.tr(i == 2 ? "sc.gui.gen.water" : type.kind == GeneratorType.Kind.EXO ? "sc.gui.gen.coolant" : "sc.gui.fuel"));
                lines.add(GuiGaugeSC.fluidLabel(t.getFluid(), t.getCapacity()));
                lines.add(GuiTankGaugeSC.percentLine(t.getFluid(), t.getCapacity()));
                if (i == 0 && type.kind == GeneratorType.Kind.DUAL_FLUID || i == 0 && type.kind == GeneratorType.Kind.EXO) {
                    lines.add(Lang.tr("sc.gui.gen.needs", fluidName(type.fuelFluidName), type.fuelRatePerTick));
                } else if (i == 1) {
                    lines.add(Lang.tr("sc.gui.gen.needs", fluidName(type.fuel2FluidName), type.fuel2RatePerTick));
                } else if (i == 0 && type == GeneratorType.COMBUSTION) {
                    lines.add(Lang.tr("sc.gui.gen.fuels"));
                } else if (i == 0) {
                    lines.add(Lang.tr("sc.gui.fuel.rate", type.fuelRatePerTick));
                }
                return lines;
            }
        }

        if (isReactor()) {
            if (!generator.isIgnited() && GuiGaugeSC.isOver(IGNITION_X, IGNITION_Y, IGNITION_W, IGNITION_H, mouseX, mouseY)) {
                lines.add(Lang.tr("sc.gui.ignition"));
                lines.add(generator.getIgnitionEU() + " / " + type.ignitionThreshold() + " EU");
                return lines;
            }
            if (GuiGaugeSC.isOver(HEAT_X, HEAT_Y, HEAT_W, HEAT_H, mouseX, mouseY)) {
                lines.add(Lang.tr("sc.gui.gen.heat"));
                lines.add(Lang.tr("sc.gui.gen.plasma", generator.getHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT)
                        + "  (" + generator.getHeat() / 10 + "%)");
                lines.add(Lang.tr("sc.gui.gen.ramp", generator.getRamp() / 10));
                lines.add(Lang.tr("sc.gui.gen.heat.hint"));
                return lines;
            }
        }

        if (TileEntityGeneratorSC.hasUpgradeSlots(type)
                && GuiGaugeSC.isOver(ContainerGeneratorSC.PANEL_X, 0, PANEL_W, PANEL_H, mouseX, mouseY)
                && !overUpgradeSlot(mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.upgrades"));
            lines.add(Lang.tr("sc.gui.gen.upgrades.hint"));
            return lines;
        }

        // What belongs in each slot while it's still empty.
        for (int slot = 0; slot < TileEntityGeneratorSC.FIRST_UPGRADE_SLOT; slot++) {
            int sx = slot == 0 ? ContainerGeneratorSC.SLOT_FUEL_X : ContainerGeneratorSC.SLOT_BLANKET_X;
            if (TileEntityGeneratorSC.usesSlot(type, slot) && generator.getStackInSlot(slot) == null
                    && GuiGaugeSC.isOver(sx, ContainerGeneratorSC.SLOT_Y, 16, 16, mouseX, mouseY)) {
                lines.add(Lang.tr(slotKey(slot)));
                String hint = Lang.trOr(slotKey(slot) + ".hint", null);
                if (hint != null) {
                    lines.add(hint);
                }
                return lines;
            }
        }
        return null;
    }

    private String slotKey(int slot) {
        switch (type) {
            case COMBUSTION:
            case SOLID_FUEL: return "sc.gui.slot.solidfuel";
            case GEOTHERMAL: return "sc.gui.slot.lava";
            case WIND_TURBINE: return "sc.gui.slot.rotor";
            case RTG: return "sc.gui.slot.capsule";
            default: return slot == 0 ? "sc.gui.slot.deuterium" : "sc.gui.slot.blanket";
        }
    }

    private static boolean overUpgradeSlot(int mouseX, int mouseY) {
        for (int i = 0; i < TileEntityGeneratorSC.UPGRADE_SLOTS; i++) {
            if (GuiGaugeSC.isOver(ContainerGeneratorSC.UPGRADE_X - 1, ContainerGeneratorSC.UPGRADE_Y - 1 + i * 18, 18, 18, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private static String fluidName(String name) {
        net.minecraftforge.fluids.Fluid f = name == null ? null : net.minecraftforge.fluids.FluidRegistry.getFluid(name);
        return f == null ? String.valueOf(name) : f.getLocalizedName(new net.minecraftforge.fluids.FluidStack(f, 1));
    }
}
