package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.energy.GeneratorStatus;
import com.sc.energy.GeneratorType;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityGeneratorSC;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidTank;

/**
 * GUI for every generator, the large holo-screen layout (GuiBigSC, 248 x 232). Left on the
 * screen: what the generator takes - its item slots, the sun of a solar panel, a reactor's
 * ignition and plasma bars, the Creative Generator's tier button - then its status, what it makes
 * right now and its own readings (wind, water, heat pairs, capsules, plasma). Right: a caption
 * of what it runs on and either its tanks as full-size gauges (fuel, second fuel, the Fuel Cell's
 * water) or its rated output and output voltage. The tall energy gauge stands right of the
 * screen, the upgrade slots are a row inside the window, the player's inventory is centred below.
 */
public class GuiGeneratorSC extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.ASSETS, "textures/gui/guiGenerator.png");

    private static final int LEFT_X = 14, LEFT_W = 88, SUN_X = 16, SUN_Y = 26;
    private static final int IGNITION_Y = 52, IGNITION_H = 6, HEAT_Y = 62, HEAT_H = 4;
    private static final int RIGHT_X = 107, CAPTION_Y = 24, TANK_LABEL_Y = 32, TANK_Y = 42, TANK_GAP = 33, ROWS_Y = 38;

    private final TileEntityGeneratorSC generator;
    private final GeneratorType type;
    /** The Combustion Generator's own screen: a tachometer, the engine, the fuels table, the reserve bar, the tank on the right. */
    private final boolean comb;
    /** The Silicon Solar Panel's own screen: the sky, the multiplier chain, the day strip. */
    private final boolean solar;
    /** The GaAs panel: the Silicon panel's screen with near-black cells and the panel -> buffer -> line flow. */
    private final boolean gaas;

    /** The panel's cells {body, edge}: GaAs near black / violet, Nano teal, Quantum violet, Exo black / gold. */
    private int[] solarCells() {
        switch (type) {
            case SOLAR_NANO: return new int[]{0xFF0E2A2A, 0xFF3AE0C8};
            case SOLAR_QUANTUM: return new int[]{0xFF2A0E3A, 0xFFE05AF0};
            case SOLAR_EXO: return new int[]{0xFF1A1408, 0xFFFFC840};
            default: return new int[]{0xFF1E1830, 0xFF7A5AC8};
        }
    }
    /** GaAs flow: the stored energy a second ago, and whether energy has been leaving the buffer since. */
    private int flowStored = -1, flowTicks;
    private boolean flowOut = true;
    private static final int GA_NOW_Y = 58, GA_ST_Y = 67, GA_FLOW_X = 100, GA_FLOW_Y = 75;
    /** The Steam Turbine's own screen: boilers -> the turbine (side and end-on) -> the power, three dials, supply and reserve. */
    private final boolean turb;
    /** The Gas Turbine: the Steam Turbine's screen, hydrogen from electrolysis into a gas turbine. */
    private final boolean gas;
    /** The Plasma Generator: the turbines' screen with an air separator, an MHD channel and a card where argon comes from. */
    private final boolean plasma;
    /** The Geothermal Generator: the turbines' screen fed by lava buckets into a heat exchanger. */
    private final boolean geo;
    /** The Fusion Reactor: its stages, the torus from above, the ignition charge / the plasma, the cell and blanket timers. */
    private final boolean fus;
    /** The Tokamak: the Fusion Reactor's screen with a "ring" stage first and a map of its eight coils. */
    private final boolean tok;
    /** The Solid Fuel Generator: the firebox, its slot and flame, a table of what each fuel gives. */
    private final boolean solid;
    /** The Wind Turbine: the landscape (height), the multiplier chips, a dial, the rotor with its wear. */
    private final boolean wind;
    /** The Water Wheel: the wheel from above with its four sides, the output in four steps, a row per side. */
    private final boolean water;
    /** The Thermoelectric Generator: its six neighbours round it, the pairs x dT / 8 chain, a row per side. */
    private final boolean thermo;
    /** The Hydrogen Fuel Cell: the reaction, the cell cut through, the 2 : 1 gas bar, the water warning; its three tanks as before. */
    private final boolean fcell;
    /** The RTG: the finned drum with its capsules glowing, the slots under their bays with their life, capsules x 32. */
    private final boolean rtg;
    /** The High-Pressure Plasma Reactor: two feeds into the pressure chamber, the plasma end-on, dials, the 4 : 1 bar. */
    private final boolean preact;
    /** The Exo Reactor: stages, the singularity, four dials, the ignition charge bar, the helium tank. */
    private final boolean exo;
    private static final int EX_BAR_X = 14, EX_BAR_Y = 95, EX_BAR_W = 102;
    private static final int[][] EX_DIALS = {{92, 56}, {132, 56}, {92, 78}, {132, 78}};
    private static final int PR_TANK_X = 142, PR_BAR_X = 40, PR_BAR_Y = 96, PR_BAR_W = 96;
    private static final int FC_X = 14, FC_Y = 39, FC_W = 90, FC_H = 38, FC_BAR_X = 40, FC_BAR_Y = 80, FC_BAR_W = 64;
    /** Display order of the sides (ForgeDirection ordinals): up, down, north, south, west, east. */
    private static final int[] TH_ORDER = {1, 0, 2, 3, 4, 5};
    private static final int TH_C = 14, TH_CX = 39, TH_CY = 56;
    /** Where each side's tile goes, by ForgeDirection ordinal (down, up, north, south, west, east). */
    private static final int[][] TH_POS = {{61, 75}, {17, 37}, {TH_CX, TH_CY - TH_C - 1}, {TH_CX, TH_CY + TH_C + 1},
            {TH_CX - TH_C - 1, TH_CY}, {TH_CX + TH_C + 1, TH_CY}};
    private static final int WW_X = 14, WW_Y = 34, WW_CELL = 18, WW_RIGHT = 76;
    private static final int WD_LAND_X = 14, WD_LAND_Y = 34, WD_LAND_W = 64, WD_LAND_H = 56;
    private static final int[] WD_CHIP_X = {84, 116, 148};
    private static final int WD_CHIP_W = 24;
    private static final int SF_BOX_X = 14, SF_BOX_Y = 34, SF_BOX_W = 62, SF_BOX_H = 44, SF_TAB_X = 102, SF_TAB_Y = 34,
            SF_TAB_W = 70, SF_TAB_H = 52, SF_FLAME_X = 83, SF_FLAME_Y = 56;
    /** The table's rows: coal, planks, a blaze rod, a lava bucket. */
    private static final ItemStack[] SF_FUELS = {new ItemStack(net.minecraft.init.Items.coal), new ItemStack(net.minecraft.init.Blocks.planks),
            new ItemStack(net.minecraft.init.Items.blaze_rod), new ItemStack(net.minecraft.init.Items.lava_bucket)};
    private static final int FU_STEP_Y = 34, FU_TOR_Y = 48, FU_TOR_W = 106, FU_TOR_H = 38, FU_COL_X = 126, FU_STATUS_Y = 107;
    /** Argon an Air Separator yields: 800 mB per 400-tick operation. */
    private static final double SEPARATOR_ARGON_PER_TICK = 2.0;
    private static final int TB_Y = 34, TB_H = 38, TB_DIAL_Y = 83, TB_SUPPLY_Y = 96, TB_RESERVE_Y = 103, TB_BAR_X = 44, TB_BAR_W = 128;
    private static final int SOL_SKY_X = 14, SOL_SKY_Y = 34, SOL_SKY_W = 60, SOL_SKY_H = 44, SOL_STRIP_Y = 84, SOL_STRIP_W = 190;
    private static final int[] SOL_CHIP_X = {80, 118, 154}, SOL_CHIP_W = {28, 26, 26};
    private static final int COMB_TANK_X = 175, COMB_ENGINE_X = 44, COMB_ENGINE_W = 56, COMB_TABLE_X = 103, COMB_TABLE_W = 69,
            COMB_TOP = 24, COMB_TABLE_H = 66, COMB_RESERVE_Y = 93;
    /** The fuels it burns: the fluid names (the first one found counts) and their lang keys, best first. */
    private static final String[][] COMB_FUELS = {{"fuel"}, {"biodiesel"}, {"diesel"}, {"ethanol", "bioethanol"}, {"biofuel"},
            {"crudeoil", "oil"}, {"ic2biogas"}};

    public GuiGeneratorSC(InventoryPlayer playerInv, TileEntityGeneratorSC generator) {
        super(new ContainerGeneratorSC(playerInv, generator));
        this.generator = generator;
        this.type = generator.getGeneratorType();
        this.comb = type == GeneratorType.COMBUSTION;
        this.gaas = type == GeneratorType.SOLAR_GAAS || type == GeneratorType.SOLAR_NANO || type == GeneratorType.SOLAR_QUANTUM
                || type == GeneratorType.SOLAR_EXO;
        this.solar = type == GeneratorType.SOLAR_SI || gaas;
        this.turb = type == GeneratorType.STEAM_TURBINE || type == GeneratorType.GAS_TURBINE || type == GeneratorType.PLASMA_GENERATOR
                || type == GeneratorType.GEOTHERMAL;
        this.geo = type == GeneratorType.GEOTHERMAL;
        this.plasma = type == GeneratorType.PLASMA_GENERATOR;
        this.fus = type == GeneratorType.FUSION_REACTOR || type == GeneratorType.TOKAMAK;
        this.tok = type == GeneratorType.TOKAMAK;
        this.solid = type == GeneratorType.SOLID_FUEL;
        this.wind = type == GeneratorType.WIND_TURBINE;
        this.water = type == GeneratorType.WATER_WHEEL;
        this.thermo = type == GeneratorType.THERMOELECTRIC;
        this.fcell = type == GeneratorType.FUEL_CELL;
        this.rtg = type == GeneratorType.RTG;
        this.preact = type == GeneratorType.PLASMA_REACTOR;
        this.exo = type == GeneratorType.EXO_REACTOR;
        this.gas = type == GeneratorType.GAS_TURBINE;
        xSize = GuiBigSC.W;
        ySize = GuiBigSC.H;
    }

    private GuiPowerSC power;
    /** The battery slot under the gauge: the output charges it. */
    private GuiBatterySlotSC battery;

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        power = new GuiPowerSC(generator, ContainerGeneratorSC.BTN_POWER, ContainerGeneratorSC.BTN_REDSTONE);
        power.addButtons(buttonList, guiLeft, guiTop);
        battery = new GuiBatterySlotSC(generator, ContainerGeneratorSC.BTN_BATTERY_MODE, GuiBigSC.GAUGE_X,
                GuiBigSC.GAUGE_Y + GuiBigSC.GAUGE_H, true);
        battery.addButton(buttonList, guiLeft, guiTop);
        if (type == GeneratorType.CREATIVE) {
            buttonList.add(new TextFitSC.Button(ContainerGeneratorSC.BTN_CREATIVE_TIER, guiLeft + LEFT_X, guiTop + 28, LEFT_W, 16, ""));
        }
        for (int i = 0; i < tankCount(); i++) {
            GuiBigSC.ClearButton b = new GuiBigSC.ClearButton(ContainerGeneratorSC.BTN_CLEAR + i);
            b.visible = true;
            b.xPosition = guiLeft + tankX(i) + GuiTankGaugeSC.WIDTH - 7;
            b.yPosition = guiTop + 31;
            buttonList.add(b);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (gaas && ++flowTicks >= 20) {
            // energy leaves when the buffer grew by less than the panel made this second
            int stored = generator.getEnergyStored();
            if (flowStored >= 0) {
                int made = generator.getStatus() == GeneratorStatus.GENERATING ? solarNow() * 20 : 0;
                flowOut = stored - flowStored < made * 0.9 || stored < flowStored;
            }
            flowStored = stored;
            flowTicks = 0;
        }
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b instanceof GuiBigSC.ClearButton) {
                int i = b.id - ContainerGeneratorSC.BTN_CLEAR;
                b.enabled = tank(i).getFluidAmount() > 0 && generator.getEnergyStored() >= generator.clearCost(i);
            } else if (b.id == ContainerGeneratorSC.BTN_CREATIVE_TIER) {
                b.displayString = Lang.tr("sc.gui.gen.creativetier", generator.getCreativeTier().name(), generator.getCreativeTier().getVoltage());
                b.enabled = mc.thePlayer.capabilities.isCreativeMode;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!power.allowClick(button)) {
            return;
        }
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

    /** Where tank i stands: the Combustion Generator's on the right edge, the others from RIGHT_X. */
    private int tankX(int i) {
        if (preact) {
            return PR_TANK_X + i * TANK_GAP;
        }
        if (exo) {
            return COMB_TANK_X;
        }
        return comb || turb ? COMB_TANK_X : RIGHT_X + i * TANK_GAP;
    }

    private FluidTank tank(int i) {
        return i == 0 ? generator.getFuelTank() : i == 1 ? generator.getFuelTank2() : generator.getOutTank();
    }

    private boolean isReactor() {
        return type.needsIgnition();
    }

    private boolean upgrades() {
        return TileEntityGeneratorSC.hasUpgradeSlots(type);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        GuiBigSC.window(x, y, upgrades(), TileEntityGeneratorSC.UPGRADE_SLOTS);
        GuiHoloSC.screen(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        for (int slot = 0; slot < TileEntityGeneratorSC.FIRST_UPGRADE_SLOT; slot++) {
            if (TileEntityGeneratorSC.usesSlot(type, slot)) {
                GuiHoloSC.slot(x + ContainerGeneratorSC.slotX(type, slot), y + ContainerGeneratorSC.slotY(type), false);
            }
        }
        if (isReactor() && !fus && !exo) {
            float charge = generator.isIgnited() ? 1f : (float) generator.getIgnitionEU() / type.ignitionThreshold();
            GuiHoloSC.bar(x + LEFT_X, y + IGNITION_Y, LEFT_W, IGNITION_H, charge, 14, 0xFF6EE6FF);
            GuiHoloSC.bar(x + LEFT_X, y + HEAT_Y, LEFT_W, HEAT_H, (float) generator.getHeat() / TileEntityGeneratorSC.HEAT_LIMIT,
                    14, 0xFFFF5A3C);
        }
        if (comb) {
            drawCombBackground(x, y, partialTicks);
        } else if (turb) {
            drawTurbBackground(x, y, partialTicks);
        } else if (fus) {
            drawFusBackground(x, y, partialTicks);
        } else if (solid) {
            drawSolidBackground(x, y, partialTicks);
        } else if (wind) {
            drawWindBackground(x, y, partialTicks);
        } else if (water) {
            drawWaterBackground(x, y, partialTicks);
        } else if (thermo) {
            drawThermoBackground(x, y, partialTicks);
        } else if (fcell) {
            drawFcBackground(x, y, partialTicks);
        } else if (rtg) {
            drawRtgBackground(x, y, partialTicks);
        } else if (preact) {
            drawPrBackground(x, y, partialTicks);
        } else if (exo) {
            drawExoBackground(x, y, partialTicks);
        } else if (solar) {
            // the solar screen has no divider
        } else {
            drawRect(x + RIGHT_X - 4, y + GuiBigSC.SCREEN_Y + 6, x + RIGHT_X - 3, y + GuiBigSC.SCREEN_Y + GuiBigSC.SCREEN_H - 6, 0xFF1E3444);
        }
        if (solar) {
            drawSolarBackground(x, y, partialTicks);
        } else if (type.kind == GeneratorType.Kind.PASSIVE) {
            GuiGaugeSC.bind(mc, TEXTURE);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            boolean lit = generator.getStatus() != GeneratorStatus.NO_SUNLIGHT;
            drawTexturedModalRect(x + SUN_X, y + SUN_Y, lit ? GuiGaugeSC.SPR_SUN_U : GuiGaugeSC.SPR_SUN_OFF_U, GuiGaugeSC.SPR_SUN_V, 32, 32);
        }

        GuiEnergyGaugeSC.draw(x + GuiBigSC.GAUGE_X, y + GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H,
                (float) generator.getEnergyStored() / Math.max(1, generator.getMaxEnergyStored()));
        battery.draw(x, y, generator.getStackInSlot(TileEntityGeneratorSC.SLOT_BATTERY));

        for (int i = 0; i < tankCount(); i++) {      // last: drawing a fluid leaves the blocks atlas bound
            FluidTank t = tank(i);
            GuiTankGaugeSC.draw(mc, x + tankX(i), y + (comb || turb || preact || exo ? 40 : TANK_Y), t.getFluid(), t.getCapacity(), null, false);
        }
        GuiHoloSC.glint(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        GuiGaugeSC.bind(mc, TEXTURE);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawForeground();
        power.drawGaugeOff(fontRendererObj);
    }

    private void drawForeground() {
        fit(type.localizedName(), 8, 5, GuiBigSC.titleRoom(fontRendererObj, generator.outputTier()), GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, generator.outputTier(), GuiBigSC.W - 6, 3);
        GuiBigSC.labels(fontRendererObj, upgrades() ? Lang.tr("sc.gui.big.upgrades") : null, Lang.tr("container.inventory"));
        if (comb) {
            drawCombText();
            drawUpgradeCount();
            return;
        }
        if (turb) {
            drawTurbText();
            drawUpgradeCount();
            return;
        }
        if (fus) {
            drawFusText();
            drawUpgradeCount();
            return;
        }
        if (solid) {
            drawSolidText();
            drawUpgradeCount();
            return;
        }
        if (wind) {
            drawWindText();
            drawUpgradeCount();
            return;
        }
        if (water) {
            drawWaterText();
            drawUpgradeCount();
            return;
        }
        if (thermo) {
            drawThermoText();
            drawUpgradeCount();
            return;
        }
        if (fcell) {
            drawFcText();
            drawUpgradeCount();
            return;
        }
        if (rtg) {
            drawRtgText();
            drawUpgradeCount();
            return;
        }
        if (preact) {
            drawPrText();
            drawUpgradeCount();
            return;
        }
        if (exo) {
            drawExoText();
            drawUpgradeCount();
            return;
        }
        if (solar) {
            drawSolarText();
            drawUpgradeCount();
            return;
        }

        // left: status, the output now, the generator's own readings
        GeneratorStatus status = generator.getStatus();
        List<String[]> rows = new ArrayList<String[]>();                 // {text, colour}
        rows.add(new String[]{status.localized(), String.valueOf(statusColor(status))});
        rows.add(new String[]{Lang.tr("sc.gui.gen.now", generator.getLastOutput()), String.valueOf(GuiHoloSC.VALUE)});
        String lab = String.valueOf(GuiHoloSC.LABEL);
        switch (type.kind) {
            case PASSIVE: {
                boolean sky = status != GeneratorStatus.NO_SUNLIGHT;
                // day and rain as the server sees them (the client world always says "day")
                rows.add(new String[]{!sky ? Lang.tr("sc.gui.gen.nosky") : Lang.tr(generator.getInfoA() != 0 ? "sc.gui.gen.day" : "sc.gui.gen.night"), lab});
                if (sky && generator.getInfoB() != 0) {
                    rows.add(new String[]{Lang.tr("sc.gui.gen.rain"), String.valueOf(0x6AA0E8)});
                }
                break;
            }
            case WIND:
                rows.add(new String[]{Lang.tr("sc.gui.gen.height", generator.getInfoA()), lab});
                rows.add(new String[]{Lang.tr("sc.gui.gen.free", generator.getInfoB()), lab});
                break;
            case WATER:
                rows.add(new String[]{Lang.tr("sc.gui.gen.flow", generator.getInfoA()), lab});
                break;
            case THERMO:
                rows.add(new String[]{Lang.tr("sc.gui.gen.pairs", generator.getInfoA()), lab});
                rows.add(new String[]{Lang.tr("sc.gui.gen.dt", generator.getInfoB()), lab});
                break;
            case RTG:
                rows.add(new String[]{Lang.tr("sc.gui.gen.capsules", generator.getInfoA()), lab});
                break;
            default:
        }
        if (isReactor()) {
            rows.add(new String[]{Lang.tr("sc.gui.gen.plasma", generator.getHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT),
                    String.valueOf(0xFF8A60)});
        }
        int ry = isReactor() ? 70 : type.kind == GeneratorType.Kind.PASSIVE || hasItemSlots() || type == GeneratorType.CREATIVE ? 62 : 28;
        for (int i = 0; i < rows.size() && ry + i * 9 + 8 <= 113; i++) {
            fit(rows.get(i)[0], LEFT_X, ry + i * 9, LEFT_W, Integer.parseInt(rows.get(i)[1]));
        }

        // right: what it runs on, then its tanks or its rated output
        int room = GuiBigSC.SCREEN_RIGHT - RIGHT_X;
        fit(Lang.tr("sc.gui.holo.gen." + type.kind.name().toLowerCase(java.util.Locale.ROOT)), RIGHT_X, CAPTION_Y, room, GuiHoloSC.CYAN & 0xFFFFFF);
        if (tankCount() > 0) {
            for (int i = 0; i < tankCount(); i++) {
                TextFitSC.drawCentered(fontRendererObj, tankLabel(i), RIGHT_X + i * TANK_GAP, TANK_LABEL_Y, GuiTankGaugeSC.WIDTH - 7,
                        GuiHoloSC.LABEL, false, guiLeft, guiTop);
            }
        } else {
            int rated = type == GeneratorType.CREATIVE ? generator.getCreativeTier().getVoltage() : generator.ratedOutput();
            fit(Lang.tr("sc.gui.holo.gen.rated", rated), RIGHT_X, ROWS_Y, room, GuiHoloSC.VALUE);
            fit(Lang.tr("sc.gui.holo.gen.out", generator.outputTier().name(), generator.outputTier().getVoltage()), RIGHT_X, ROWS_Y + 11,
                    room, GuiHoloSC.LABEL);
            fit(Lang.tr("sc.gui.holo.gen.buffer", generator.getEnergyStored() * 100L / Math.max(1, generator.getMaxEnergyStored())),
                    RIGHT_X, ROWS_Y + 22, room, GuiHoloSC.LABEL);
        }

        drawUpgradeCount();
    }

    private void drawUpgradeCount() {
        if (upgrades()) {
            int used = 0;
            for (int i = 0; i < TileEntityGeneratorSC.UPGRADE_SLOTS; i++) {
                used += generator.getStackInSlot(TileEntityGeneratorSC.FIRST_UPGRADE_SLOT + i) != null ? 1 : 0;
            }
            int tr = GuiBigSC.UPG_TEXT_W;
            fit(Lang.tr("sc.gui.big.upgrades.count", used, TileEntityGeneratorSC.UPGRADE_SLOTS), GuiBigSC.UPG_TEXT_X, GuiBigSC.UPG_Y, tr, 0x505864);
        }
    }

    // ---- the Steam Turbine ----

    /** mB a tick it burns when running. */
    private double turbNeed() {
        return type.fuelRatePerTick * generator.fuelMultiplier();
    }

    private float turbSupply() {
        return generator.getInflowTenths() / 10F;
    }

    private void drawTurbBackground(int x, int y, float partialTicks) {
        float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
        boolean running = generator.getStatus() == GeneratorStatus.GENERATING, feeding = turbSupply() > 0;
        GuiSceneSC.frame(x + 14, y + TB_Y, 158, TB_H);
        int bx = x + 19, by = y + 40;
        if (geo) {                                                             // the lava bucket slot
            GuiHoloSC.slot(x + ContainerGeneratorSC.GEO_SLOT_X, y + ContainerGeneratorSC.GEO_SLOT_Y, false);
        } else if (plasma) {                                                          // an air separator column
            drawRect(bx + 1, by - 2, bx + 7, by, 0xFF6A707A);
            drawRect(bx - 2, by, bx + 10, by + 20, 0xFF8A909A);
            drawRect(bx - 1, by + 1, bx + 9, by + 19, 0xFF1A2A3A);
            for (int i = 0; i < 4; i++) {
                drawRect(bx, by + 3 + i * 4, bx + 8, by + 4 + i * 4, feeding ? 0xFF6EA0C8 : 0xFF3A4A5A);
            }
        } else if (gas) {                                                             // an electrolysis cell
            drawRect(bx, by, bx + 16, by + 20, 0xFF8A909A);
            drawRect(bx + 1, by + 1, bx + 15, by + 19, 0xFF2A4A6A);
            drawRect(bx + 5, by + 3, bx + 7, by + 17, 0xFF6A707A);
            drawRect(bx + 10, by + 3, bx + 12, by + 17, 0xFFB08A6A);
            if (feeding) {
                drawRect(bx + 11, by + 16 - (int) (t % 12), bx + 12, by + 17 - (int) (t % 12), 0xFFF0F8FF);
            }
        } else {                                                               // a boiler
            drawRect(bx, by, bx + 16, by + 20, 0xFF8A909A);
            drawRect(bx + 1, by + 1, bx + 15, by + 11, 0xFF3A6AB0);
            drawRect(bx + 1, by + 12, bx + 15, by + 19, 0xFF3A2418);
            for (int k = 0; k < 3; k++) {
                drawRect(bx + 3 + k * 4, by + 14, bx + 5 + k * 4, by + 18, feeding ? 0xFFFFB040 : 0xFF5A2A20);
            }
        }
        drawRect(x + 36, y + 50, x + 48, y + 53, 0xFF6A707A);                  // the steam pipe
        if (feeding) {
            for (int i = 0; i < 3; i++) {
                int d = (int) ((t * 1.5F + i * 4) % 12);
                drawRect(x + 36 + d, y + 51, x + 38 + d, y + 52, geo ? 0xFFFF7A20 : plasma ? 0xFFC890FF : 0xFFE0E8F0);
            }
        }
        if (geo) {
            GuiSceneSC.heatExchanger(x + 48, y + 36, 95, 34, t, running);
        } else if (plasma) {
            GuiSceneSC.plasmaChannel(x + 48, y + 36, 64, 34, t, running);
        } else if (gas) {
            GuiSceneSC.gasTurbine(x + 48, y + 36, 64, 34, t, running);
        } else {
            GuiSceneSC.turbineSide(x + 48, y + 36, 64, 34, t, running);
        }
        if (!geo) {
            GuiSceneSC.frame(x + 113, y + 36, 30, 34);
        }
        if (geo) {
            // the exchanger's own turbine drum stands in for the rotor
        } else if (plasma) {
            GuiSceneSC.plasmaRing(x + 128, y + 53, 9, t, running);
        } else {
            GuiSceneSC.rotorFront(x + 128, y + 53, 12, t, running);
        }
        if (running) {
            for (int i = 0; i < 3; i++) {                                      // power going out
                int d = (int) ((t * 2 + i * 8) % 24);
                drawRect(x + 145 + d, y + 61, x + 148 + d, y + 62, 0xFF6EE6FF);
            }
        }
        int rated = Math.max(1, generator.ratedOutput());
        float need = (float) turbNeed();
        GuiSceneSC.dial(x + 26, y + TB_DIAL_Y, 9, Math.min(1F, turbSupply() / Math.max(0.1F, need)));
        if (plasma) {                                                          // the card: where argon comes from
            GuiSceneSC.frame(x + 66, y + TB_DIAL_Y - 9, 106, 19);
        } else {
            GuiSceneSC.dial(x + 78, y + TB_DIAL_Y, 9, geo ? 1F : running ? 1F : 0F);
            GuiSceneSC.dial(x + 130, y + TB_DIAL_Y, 9, Math.min(1F, (float) generator.getLastOutput() / rated));
        }
        int fuelCol = geo ? 0xFFFF7A20 : plasma ? 0xFFC890FF : gas ? 0xFF9AD8FF : 0xFFE0E8F0;
        boolean short_ = turbShort(turbSupply(), need);
        drawRect(x + TB_BAR_X, y + TB_SUPPLY_Y, x + TB_BAR_X + TB_BAR_W, y + TB_SUPPLY_Y + 4, 0xFF04080C);
        int sw = (int) ((TB_BAR_W - 2) * Math.min(1F, turbSupply() / Math.max(0.1F, need)));
        drawRect(x + TB_BAR_X + 1, y + TB_SUPPLY_Y + 1, x + TB_BAR_X + 1 + sw, y + TB_SUPPLY_Y + 3, short_ ? 0xFFE63C3C : fuelCol);
        drawRect(x + TB_BAR_X + TB_BAR_W - 1, y + TB_SUPPLY_Y - 1, x + TB_BAR_X + TB_BAR_W, y + TB_SUPPLY_Y + 5, 0xFF5AE66E);
        FluidTank tank = generator.getFuelTank();
        float left = tank.getCapacity() > 0 ? (float) tank.getFluidAmount() / tank.getCapacity() : 0F;
        drawRect(x + TB_BAR_X, y + TB_RESERVE_Y, x + TB_BAR_X + TB_BAR_W, y + TB_RESERVE_Y + 4, 0xFF04080C);
        for (int i = 0; i < 24; i++) {
            int a = x + TB_BAR_X + 1 + i * (TB_BAR_W - 2) / 24, b = x + TB_BAR_X + 1 + (i + 1) * (TB_BAR_W - 2) / 24 - 1;
            drawRect(a, y + TB_RESERVE_Y + 1, b, y + TB_RESERVE_Y + 3, (i + 0.5F) / 24 < left ? fuelCol : 0xFF2A3038);
        }
    }

    /** The Steam Turbine's key, or the Gas Turbine's own wording of it. */
    private String tk(String key) {
        return (geo ? "sc.gui.geo." : plasma ? "sc.gui.pgen." : gas ? "sc.gui.gturb." : "sc.gui.turb.") + key;
    }

    /**
     * The supply falls short of the need. Lava comes a bucket at a time - 1000 mB in one second, then
     * none - so the Geothermal Generator only counts as short once less than a bucket is left.
     */
    private boolean turbShort(float supply, float need) {
        return supply < need - 0.05F && !(geo && generator.getFuelTank().getFluidAmount() >= 1000);
    }

    private void drawTurbText() {
        fit(Lang.tr(tk("title")), 14, CAPTION_Y, 90, GuiHoloSC.CYAN & 0xFFFFFF);
        TextFitSC.drawCentered(fontRendererObj, tankLabel(0), COMB_TANK_X, 30, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        boolean running = generator.getStatus() == GeneratorStatus.GENERATING;
        float supply = turbSupply(), need = (float) turbNeed();
        boolean short_ = turbShort(supply, need);
        String sup = supply == (int) supply ? String.valueOf((int) supply) : String.format(java.util.Locale.ROOT, "%.1f", supply);
        smallFit(sup, 35, 43, 14, short_ ? GuiHoloSC.BAD : GuiHoloSC.VALUE);
        smallFit(Lang.tr(tk("boilers")), 17, 62, 20, GuiHoloSC.LABEL);
        fontRendererObj.drawString(String.valueOf(generator.getLastOutput()), 146, 43, running ? GuiHoloSC.OK : GuiHoloSC.BAD);
        smallFit("EU/t", 146, 53, 24, GuiHoloSC.LABEL);
        String needS = String.format(java.util.Locale.ROOT, need == (int) need ? "%.0f" : "%.1f", need);
        String[] labels = {Lang.tr(tk("steam")), Lang.tr(geo ? "sc.gui.geo.permb" : "sc.gui.turb.speed"), Lang.tr("sc.gui.turb.out")};
        String perMbS = need <= 0 ? "0" : String.valueOf(Math.round(generator.ratedOutput() / need));
        String[] vals = {(geo ? Math.min(supply, need) == (int) Math.min(supply, need) ? String.valueOf((int) Math.min(supply, need))
                : String.format(java.util.Locale.ROOT, "%.1f", Math.min(supply, need)) : sup) + "/" + needS,
                geo ? perMbS : running ? "100%" : "0%", String.valueOf(generator.getLastOutput())};
        for (int i = 0; i < (plasma ? 1 : 3); i++) {
            int cx = 26 + i * 52;
            smallFit(labels[i], cx + 12, TB_DIAL_Y - 6, 38, GuiHoloSC.LABEL);
            smallFit(vals[i], cx + 12, TB_DIAL_Y, 38, i == 0 && short_ ? GuiHoloSC.BAD : GuiHoloSC.VALUE);
        }
        smallFit(Lang.tr("sc.gui.turb.supply"), 14, TB_SUPPLY_Y, 28, GuiHoloSC.LABEL);
        smallFit(Lang.tr("sc.gui.turb.reserve"), 14, TB_RESERVE_Y, 28, GuiHoloSC.LABEL);
        FluidTank tank = generator.getFuelTank();
        int secs = need <= 0 ? 0 : (int) (tank.getFluidAmount() / need / 20);
        // a source: an LV boiler's 40 mB/t of steam, or an electrolysis run's 500 mB of hydrogen per 15 s
        int boilers = (int) Math.ceil(need / (plasma ? SEPARATOR_ARGON_PER_TICK : gas ? 500.0 / 300.0 : 40.0) - 1e-6);
        if (plasma) {
            int perMb = need <= 0 ? 0 : Math.round(generator.ratedOutput() / need);
            smallFit(Lang.tr("sc.gui.pgen.card"), 68, TB_DIAL_Y - 7, 100, GuiHoloSC.LABEL);
            smallFit(Lang.tr("sc.gui.pgen.card.src", String.format(java.util.Locale.ROOT, "%.0f", SEPARATOR_ARGON_PER_TICK)),
                    68, TB_DIAL_Y - 1, 100, GuiHoloSC.VALUE);
            smallFit(Lang.tr("sc.gui.pgen.card.need", boilers, perMb), 68, TB_DIAL_Y + 5, 100, GuiHoloSC.LABEL);
        }
        String line;
        int col;
        Object src = boilers;
        if (geo) {                                                             // a bucket lasts this long
            double b = need <= 0 ? 0 : 1000.0 / need / 20.0;
            src = b == Math.floor(b) ? String.valueOf((long) b) : String.format(java.util.Locale.ROOT, "%.1f", b).replace('.', ',');
        }
        if (tank.getFluidAmount() <= 0 && supply <= 0) {
            line = Lang.tr(tk("nosteam"), needS, src);
            col = GuiHoloSC.BAD;
        } else if (short_) {
            line = Lang.tr(tk("short"), sup, needS, secs);
            col = GuiHoloSC.BAD;
        } else {
            line = Lang.tr(tk("ok"), secs, src);
            col = GuiHoloSC.LABEL;
        }
        smallFit(line, 14, TB_RESERVE_Y + 6, 158, col);
    }

    // ---- the Fusion Reactor ----

    /** The Tokamak's ring isn't whole (the map shows instead of the torus). */
    private boolean tokRingOpen() {
        return tok && generator.getSideInfo() != 0xFF;
    }

    /** The stage lit: 0 charge, 1 blanket, 2 ignition, 3 ramp-up, 4 running; the Tokamak has "ring" before them all. */
    private int fusStageShown() {
        if (!tok) {
            return fusStage();
        }
        return tokRingOpen() ? 0 : fusStage() + 1;
    }

    /** 0 charge, 1 blanket, 2 ignition, 3 ramp-up, 4 running. */
    private int fusStage() {
        if (generator.isIgnited()) {
            return generator.getRamp() < TileEntityGeneratorSC.RAMP_FULL && generator.getStatus() != GeneratorStatus.BUFFER_FULL ? 3 : 4;
        }
        if (generator.getIgnitionEU() < type.ignitionThreshold()) {
            return 0;
        }
        return generator.getStatus() == GeneratorStatus.NO_BLANKET ? 1 : 2;
    }

    /** The plasma's heat at full power: 600, and 100 more for each Overdrive. */
    private int fusNormHeat() {
        return 600 + 100 * generator.upgradeCount(com.sc.machine.UpgradeType.OVERDRIVE);
    }

    private boolean fusHasBlanket() {
        net.minecraft.item.ItemStack b = generator.getStackInSlot(TileEntityGeneratorSC.SLOT_BLANKET);
        return b != null;
    }

    private void drawFusBackground(int x, int y, float partialTicks) {
        float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
        int stage = fusStageShown(), steps = tok ? 6 : 5, step = tok ? 27 : 32;
        for (int i = 0; i < steps; i++) {                                      // the stages
            int bx = x + 14 + i * step, by = y + FU_STEP_Y;
            drawRect(bx, by, bx + step - 2, by + 11, i == stage ? 0xFF2A6A8A : i < stage ? 0xFF1E4A3A : 0xFF1A3444);
            drawRect(bx + 1, by + 1, bx + step - 3, by + 10, i == stage ? 0xFF0E3A50 : 0xFF0A1218);
        }
        boolean lit = generator.isIgnited();
        float heat = (float) generator.getHeat() / TileEntityGeneratorSC.HEAT_LIMIT;
        if (tokRingOpen()) {
            GuiSceneSC.frame(x + 14, y + FU_TOR_Y, 64, FU_TOR_H);
            GuiSceneSC.tokamakRing(x + 28, y + FU_TOR_Y, 12, generator.getSideInfo());
        } else {
            GuiSceneSC.fusionTorus(x + 14, y + FU_TOR_Y, FU_TOR_W, FU_TOR_H, t, heat, lit);
        }
        int cx = x + FU_COL_X;
        if (tokRingOpen()) {
            // the right column is text only while the ring is open
        } else if (!lit) {                                                            // the ignition charge
            float charge = (float) generator.getIgnitionEU() / type.ignitionThreshold();
            drawRect(cx, y + 56, cx + 78, y + 62, 0xFF04080C);
            for (int i = 0; i < 19; i++) {
                drawRect(cx + 1 + i * 4, y + 57, cx + 4 + i * 4, y + 61, (i + 0.5F) / 19 < charge ? 0xFF6EE6FF : 0xFF2A3038);
            }
        } else {                                                               // the plasma thermometer
            int ty = y + FU_TOR_Y, th = FU_TOR_H, n = (th - 2) / 2;
            drawRect(cx, ty, cx + 7, ty + th, 0xFF04080C);
            for (int i = 0; i < n; i++) {
                float k = i / (float) n;
                int col = k < heat ? (k > 0.85F ? 0xFFE63C3C : GuiSceneSC.mix(0xFF7A3AC8, 0xFFFFFFFF, k)) : 0xFF2A3038;
                drawRect(cx + 1, ty + th - 3 - i * 2, cx + 6, ty + th - 2 - i * 2, col);
            }
            int ny = ty + th - 3 - (int) (n * Math.min(1F, fusNormHeat() / (float) TileEntityGeneratorSC.HEAT_LIMIT)) * 2;
            drawRect(cx - 2, ny, cx + 9, ny + 1, 0xFF5AE66E);
            drawRect(cx - 2, ty + 2, cx + 9, ty + 3, 0xFFE63C3C);
        }
        // the cell and blanket timers
        net.minecraft.item.ItemStack cells = generator.getStackInSlot(TileEntityGeneratorSC.SLOT_FUEL);
        float cellF = (float) Math.min(1.0, generator.getCellBurnRemaining() / TileEntityGeneratorSC.CELL_BURN_TICKS);
        if (cellF <= 0 && cells != null) {
            cellF = 1F;
        }
        float blankF = lit ? (float) generator.getModuleLife() / TileEntityGeneratorSC.MODULE_LIFE_TICKS : fusHasBlanket() ? 1F : 0F;
        int by = y + ContainerGeneratorSC.FUS_SLOT_Y + 6;
        for (int s = 0; s < 2; s++) {
            int bx = x + (s == 0 ? ContainerGeneratorSC.FUS_FUEL_X : ContainerGeneratorSC.FUS_BLANKET_X) + 19;
            float f = s == 0 ? cellF : blankF;
            drawRect(bx, by, bx + 48, by + 4, 0xFF04080C);
            drawRect(bx + 1, by + 1, bx + 1 + (int) (46 * f), by + 3, s == 0 ? 0xFF9AD8FF : 0xFFC8D87A);
        }
    }

    /** "340к" / "1 млн" - the ignition charge, short. */
    private static String fusEu(long eu) {
        if (eu >= 1000000) {
            double m = eu / 1000000.0;
            return Lang.tr("sc.gui.fus.mln", m == Math.floor(m) ? String.valueOf((long) m) : String.format(java.util.Locale.ROOT, "%.1f", m).replace('.', ','));
        }
        return Lang.tr("sc.gui.fus.k", eu / 1000);
    }

    /** "3 мин" / "40 с" / "11 ч". */
    private static String fusTime(double seconds) {
        long s = (long) Math.ceil(seconds);
        if (s >= 3600) {
            return Lang.tr("sc.gui.fus.h", s / 3600);
        }
        return s >= 60 ? Lang.tr("sc.gui.fus.min", s / 60) : Lang.tr("sc.gui.fus.sec", s);
    }

    private void drawFusText() {
        fit(Lang.tr(tok ? "sc.gui.tok.title" : "sc.gui.fus.title"), 14, CAPTION_Y, 90, GuiHoloSC.CYAN & 0xFFFFFF);
        int stage = fusStage(), shown = fusStageShown(), n = tok ? 6 : 5, step = tok ? 27 : 32;
        for (int i = 0; i < n; i++) {
            String s = Lang.tr(tok && i == 0 ? "sc.gui.tok.step.ring" : "sc.gui.fus.step." + (tok ? i - 1 : i));
            int w = Math.min(step - 4, (int) (fontRendererObj.getStringWidth(s) * 0.625F));
            smallFit(s, 14 + i * step + (step - 2) / 2 - w / 2, FU_STEP_Y + 3, step - 4,
                    i == shown ? GuiHoloSC.VALUE : i < shown ? GuiHoloSC.OK : 0x465A6E);
        }
        boolean lit = generator.isIgnited();
        GeneratorStatus status = generator.getStatus();
        int ringMask = generator.getSideInfo(), coils = Integer.bitCount(ringMask & 0xFF);
        if (tokRingOpen()) {
            smallFit(Lang.tr("sc.gui.tok.ring"), 84, 49, 120, GuiHoloSC.LABEL);
            fit(Lang.tr("sc.gui.tok.built", coils), 84, 55, 120, GuiHoloSC.WARN);
            smallFit(Lang.tr("sc.gui.tok.hint.1"), 84, 66, 120, GuiHoloSC.LABEL);
            smallFit(Lang.tr("sc.gui.tok.hint.2"), 84, 72, 120, GuiHoloSC.LABEL);
            smallFit(Lang.tr("sc.gui.tok.hint.3"), 84, 78, 120, GuiHoloSC.LABEL);
        } else if (!lit) {
            long eu = generator.getIgnitionEU(), need = type.ignitionThreshold();
            smallFit(Lang.tr("sc.gui.fus.charge"), FU_COL_X, 49, 78, GuiHoloSC.LABEL);
            smallFit(fusEu(eu) + " / " + fusEu(need) + " EU", FU_COL_X, 65, 78, GuiHoloSC.VALUE);
            String hint = "sc.gui.fus.hint." + (status == GeneratorStatus.OVERHEATED ? "cool" : String.valueOf(Math.min(2, stage)));
            smallFit(Lang.tr(hint + "a"), FU_COL_X, 73, 78, GuiHoloSC.LABEL);
            smallFit(Lang.tr(hint + "b"), FU_COL_X, 79, 78, GuiHoloSC.LABEL);
        } else {
            int tx = FU_COL_X + 11;
            smallFit(Lang.tr("sc.gui.fus.plasma"), tx, 48, 68, GuiHoloSC.LABEL);
            smallFit(Lang.tr("sc.gui.gen.plasma.short", generator.getHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT), tx, 54, 68,
                    generator.getHeat() > fusNormHeat() ? GuiHoloSC.WARN : GuiHoloSC.VALUE);
            smallFit(Lang.tr("sc.gui.fus.power"), tx, 62, 68, GuiHoloSC.LABEL);
            smallFit(generator.getRamp() / 10 + "%", tx, 68, 68, GuiHoloSC.VALUE);
            smallFit(generator.getLastOutput() + " EU/t", tx, 76, 68, generator.getLastOutput() > 0 ? GuiHoloSC.OK : GuiHoloSC.BAD);
        }
        // the cell and the blanket
        net.minecraft.item.ItemStack cells = generator.getStackInSlot(TileEntityGeneratorSC.SLOT_FUEL);
        int count = cells == null ? 0 : cells.stackSize;
        double fm = Math.max(0.01, generator.fuelMultiplier());
        double secs = (generator.getCellBurnRemaining() + (double) count * TileEntityGeneratorSC.CELL_BURN_TICKS) / fm / 20.0;
        int tx = ContainerGeneratorSC.FUS_FUEL_X + 19, ty = ContainerGeneratorSC.FUS_SLOT_Y;
        if (secs <= 0) {
            smallFit(Lang.tr("sc.gui.fus.nocell"), tx, ty, 58, GuiHoloSC.BAD);
        } else {
            smallFit(Lang.tr("sc.gui.fus.cell", fusTime(secs), count), tx, ty, 58, GuiHoloSC.LABEL);
        }
        int bx = ContainerGeneratorSC.FUS_BLANKET_X + 19;
        if (lit) {
            smallFit(Lang.tr("sc.gui.fus.blanket.life", fusTime(generator.getModuleLife() / 20.0)), bx, ty, 58, GuiHoloSC.LABEL);
        } else {
            smallFit(Lang.tr(fusHasBlanket() ? "sc.gui.fus.blanket.in" : "sc.gui.fus.blanket.need"), bx, ty, 58,
                    fusHasBlanket() ? GuiHoloSC.LABEL : GuiHoloSC.BAD);
        }
        // the status line
        String line;
        int col;
        if (status == GeneratorStatus.DISABLED || status == GeneratorStatus.REDSTONE) {
            line = status.localized();
            col = GuiHoloSC.IDLE;
        } else if (tokRingOpen()) {
            StringBuilder where = new StringBuilder();
            for (int k = 0; k < 8; k++) {
                if ((ringMask >> k & 1) == 0) {
                    where.append(where.length() > 0 ? ", " : "").append(Lang.tr("sc.gui.tok.dir." + k));
                }
            }
            line = Lang.tr("sc.gui.tok.st.open", 8 - coils, where.toString());
            col = GuiHoloSC.BAD;
        } else if (!lit) {
            if (status == GeneratorStatus.OVERHEATED) {
                line = Lang.tr("sc.gui.fus.st.overheat", generator.getHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT);
                col = GuiHoloSC.BAD;
            } else if (status == GeneratorStatus.BLANKET_DEPLETED) {
                line = Lang.tr("sc.gui.fus.st.depleted");
                col = GuiHoloSC.BAD;
            } else if (stage == 1) {
                line = Lang.tr("sc.gui.fus.st.noblanket");
                col = GuiHoloSC.WARN;
            } else {
                int pct = (int) (100 * generator.getIgnitionEU() / Math.max(1, type.ignitionThreshold()));
                line = Lang.tr("sc.gui.fus.st.charge", pct,
                        Lang.tr(fusHasBlanket() ? "sc.gui.fus.yes.blanket" : "sc.gui.fus.no.blanket"),
                        Lang.tr(count > 0 ? "sc.gui.fus.yes.cell" : "sc.gui.fus.no.cell"));
                col = GuiHoloSC.CYAN & 0xFFFFFF;
            }
        } else if (status == GeneratorStatus.BUFFER_FULL) {
            line = Lang.tr("sc.gui.fus.st.full");
            col = GuiHoloSC.WARN;
        } else if (status == GeneratorStatus.NO_DEUTERIUM) {
            line = Lang.tr("sc.gui.fus.st.nocell");
            col = GuiHoloSC.BAD;
        } else if (stage == 3) {
            line = Lang.tr("sc.gui.fus.st.ramp", generator.getRamp() / 10);
            col = GuiHoloSC.CYAN & 0xFFFFFF;
        } else {
            line = Lang.tr("sc.gui.fus.st.ok", generator.outputTier().name(), generator.outputTier().getVoltage());
            col = GuiHoloSC.OK;
        }
        smallFit(line, 14, FU_STATUS_Y, 190, col);
    }

    // ---- the Solid Fuel Generator ----

    /** Which table row a stack is (coal / planks / blaze rod / lava bucket), or -1. */
    private static int sfRow(ItemStack st) {
        if (st == null) {
            return -1;
        }
        for (int i = 0; i < SF_FUELS.length; i++) {
            if (st.getItem() == SF_FUELS[i].getItem()) {
                return i;
            }
        }
        return -1;
    }

    /** Output ticks a piece of `st` burns here (furnace burn time / 4). */
    private static int sfTicks(ItemStack st) {
        return st == null ? 0 : Math.max(1, net.minecraft.tileentity.TileEntityFurnace.getItemBurnTime(st) / TileEntityGeneratorSC.SOLID_GEN_DIVISOR);
    }

    private float sfLeft() {
        int total = generator.getSolidBurnTotal();
        return total <= 0 ? 0F : (float) Math.max(0, Math.min(1, generator.getSolidBurn() / total));
    }

    private boolean sfBurning() {
        return generator.getSolidBurn() > 0 && generator.getStatus() != GeneratorStatus.NO_FUEL;
    }

    private void drawSolidBackground(int x, int y, float partialTicks) {
        float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
        boolean burning = sfBurning();
        GuiSceneSC.firebox(x + SF_BOX_X, y + SF_BOX_Y, SF_BOX_W, SF_BOX_H, t,
                burning && generator.getStatus() == GeneratorStatus.GENERATING, sfLeft());
        GuiSceneSC.furnaceFlame(x + SF_FLAME_X, y + SF_FLAME_Y, burning ? sfLeft() : 0F);
        GuiSceneSC.frame(x + SF_TAB_X, y + SF_TAB_Y, SF_TAB_W, SF_TAB_H);
        int cur = burning ? sfRow(generator.getSolidBurnStack()) : -1;
        if (cur >= 0) {
            int ry = y + SF_TAB_Y + 9 + cur * 10;
            drawRect(x + SF_TAB_X + 2, ry - 1, x + SF_TAB_X + SF_TAB_W - 2, ry + 9, 0xFF3A2A14);
        }
    }

    /** "10 мин 40 с" / "1 ч 5 мин" / "12 с". */
    private static String sfTime(double seconds) {
        long s = (long) Math.ceil(seconds);
        if (s >= 3600) {
            return Lang.tr("sc.gui.t.hm", s / 3600, s % 3600 / 60);
        }
        if (s >= 60) {
            return s % 60 == 0 ? Lang.tr("sc.gui.fus.min", s / 60) : Lang.tr("sc.gui.t.ms", s / 60, s % 60);
        }
        return Lang.tr("sc.gui.fus.sec", s);
    }

    /** 204800 -> "204 800". */
    private static String sfEu(long eu) {
        return String.format(java.util.Locale.ROOT, "%,d", eu).replace(',', ' ');
    }

    private void drawSolidText() {
        fit(Lang.tr("sc.gui.sf.title"), 14, CAPTION_Y, 90, GuiHoloSC.CYAN & 0xFFFFFF);
        double fm = Math.max(0.01, generator.fuelMultiplier());
        int rated = generator.ratedOutput();
        boolean burning = sfBurning();
        double leftSecs = generator.getSolidBurn() / fm / 20.0;
        smallFit(burning ? sfTime(leftSecs) : "-", 80, 72, 20, GuiHoloSC.VALUE);
        // the table: what each fuel gives here
        smallFit(Lang.tr("sc.gui.sf.table"), SF_TAB_X + 3, SF_TAB_Y + 2, SF_TAB_W - 6, GuiHoloSC.LABEL);
        int cur = burning ? sfRow(generator.getSolidBurnStack()) : -1;
        for (int i = 0; i < SF_FUELS.length; i++) {
            int ry = SF_TAB_Y + 9 + i * 10;
            int ticks = sfTicks(SF_FUELS[i]);
            smallFit(SF_FUELS[i].getDisplayName(), SF_TAB_X + 13, ry, SF_TAB_W - 15, i == cur ? 0xFFC86E : GuiHoloSC.VALUE);
            smallFit(Lang.tr("sc.gui.sf.row", sfTime(ticks / fm / 20.0), sfEu(Math.round(ticks / fm * rated))),
                    SF_TAB_X + 13, ry + 5, SF_TAB_W - 15, GuiHoloSC.LABEL);
        }
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < SF_FUELS.length; i++) {
            GL11.glPushMatrix();
            GL11.glTranslatef(SF_TAB_X + 3, SF_TAB_Y + 9 + i * 10, 0F);
            GL11.glScalef(0.5F, 0.5F, 1F);
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), SF_FUELS[i], 0, 0);
            GL11.glPopMatrix();
        }
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        // the status and what's left
        GeneratorStatus status = generator.getStatus();
        ItemStack inSlot = generator.getStackInSlot(TileEntityGeneratorSC.SLOT_FUEL);
        boolean fuelIn = inSlot != null && net.minecraft.tileentity.TileEntityFurnace.isItemFuel(inSlot);
        String head;
        int headCol;
        if (status == GeneratorStatus.GENERATING) {
            head = Lang.tr("sc.gui.sf.running", generator.getLastOutput());
            headCol = GuiHoloSC.OK;
        } else if (status == GeneratorStatus.NO_FUEL) {
            head = Lang.tr("sc.gui.sf.nofuel");
            headCol = GuiHoloSC.BAD;
        } else {
            head = status.localized();
            headCol = status == GeneratorStatus.BUFFER_FULL ? GuiHoloSC.WARN : GuiHoloSC.IDLE;
        }
        fit(head, 14, 82, SF_TAB_X - 16 + SF_TAB_W, headCol);
        if (burning) {
            ItemStack piece = generator.getSolidBurnStack();
            String name = piece != null ? piece.getDisplayName() : Lang.tr("sc.gui.sf.piece");
            smallFit(Lang.tr("sc.gui.sf.burns", name, sfTime(leftSecs)), 14, 92, 158, GuiHoloSC.VALUE);
            if (fuelIn) {
                double secs = leftSecs + (double) inSlot.stackSize * sfTicks(inSlot) / fm / 20.0;
                smallFit(Lang.tr("sc.gui.sf.stock", sfTime(secs), sfEu(Math.round(secs * 20 * rated))), 14, 99, 158, GuiHoloSC.LABEL);
            } else {
                smallFit(Lang.tr("sc.gui.sf.last"), 14, 99, 158, GuiHoloSC.WARN);
            }
        } else if (fuelIn) {
            double secs = (double) inSlot.stackSize * sfTicks(inSlot) / fm / 20.0;
            smallFit(Lang.tr("sc.gui.sf.stock", sfTime(secs), sfEu(Math.round(secs * 20 * rated))), 14, 92, 158, GuiHoloSC.VALUE);
        } else {
            smallFit(Lang.tr("sc.gui.sf.put"), 14, 92, 158, GuiHoloSC.VALUE);
            smallFit(Lang.tr("sc.gui.sf.fuels"), 14, 99, 158, GuiHoloSC.LABEL);
        }
        smallFit(Lang.tr("sc.gui.sf.od"), 14, 106, 158, 0x465A6E);
    }

    // ---- the Exo Reactor ----

    /** 0 charge, 1 ignition, 2 ramp-up, 3 running. */
    private int exoStage() {
        if (generator.isIgnited()) {
            return generator.getRamp() < TileEntityGeneratorSC.RAMP_FULL && generator.getStatus() != GeneratorStatus.BUFFER_FULL ? 2 : 3;
        }
        return generator.getIgnitionEU() < type.ignitionThreshold() ? 0 : 1;
    }

    /** Seconds the helium in the tank lasts at 1 mB/t (with the upgrades). */
    private double exoHeliumSecs() {
        return generator.getFuelTank().getFluidAmount() / (type.fuelRatePerTick * Math.max(0.01, generator.fuelMultiplier())) / 20.0;
    }

    private void drawExoBackground(int x, int y, float partialTicks) {
        float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
        int stage = exoStage();
        for (int i = 0; i < 4; i++) {                                          // the stages
            int bx = x + 14 + i * 38, by = y + FU_STEP_Y;
            drawRect(bx, by, bx + 36, by + 11, i == stage ? 0xFF2A6A8A : i < stage ? 0xFF1E4A3A : 0xFF1A3444);
            drawRect(bx + 1, by + 1, bx + 35, by + 10, i == stage ? 0xFF0E3A50 : 0xFF0A1218);
        }
        boolean lit = generator.isIgnited();
        float heat = (float) generator.getHeat() / TileEntityGeneratorSC.HEAT_LIMIT;
        GuiSceneSC.singularity(x + 14, y + 48, 64, 40, t, lit, heat);
        float charge = lit ? 1F : (float) generator.getIgnitionEU() / type.ignitionThreshold();
        FluidTank he = generator.getFuelTank();
        float[] v = {charge, generator.getRamp() / (float) TileEntityGeneratorSC.RAMP_FULL, heat,
                he.getCapacity() > 0 ? (float) he.getFluidAmount() / he.getCapacity() : 0F};
        for (int i = 0; i < 4; i++) {
            GuiSceneSC.dial(x + EX_DIALS[i][0], y + EX_DIALS[i][1], 8, Math.min(1F, v[i]));
        }
        if (!lit) {                                                            // the ignition charge bar
            int bx = x + EX_BAR_X, by = y + EX_BAR_Y;
            drawRect(bx, by, bx + EX_BAR_W, by + 6, 0xFF04080C);
            for (int i = 0; i < 25; i++) {
                drawRect(bx + 1 + i * 4, by + 1, bx + 4 + i * 4, by + 5, (i + 0.5F) / 25 < charge ? 0xFF6EE6FF : 0xFF2A3038);
            }
        }
    }

    private void drawExoText() {
        fit(Lang.tr("sc.gui.ex.title"), 14, CAPTION_Y, 110, GuiHoloSC.CYAN & 0xFFFFFF);
        TextFitSC.drawCentered(fontRendererObj, tankLabel(0), COMB_TANK_X, 30, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        int stage = exoStage();
        for (int i = 0; i < 4; i++) {
            String s = Lang.tr("sc.gui.ex.step." + i);
            int w = Math.min(32, (int) (fontRendererObj.getStringWidth(s) * 0.625F));
            smallFit(s, 14 + i * 38 + 18 - w / 2, FU_STEP_Y + 3, 32, i == stage ? GuiHoloSC.VALUE : i < stage ? GuiHoloSC.OK : 0x465A6E);
        }
        boolean lit = generator.isIgnited();
        GeneratorStatus status = generator.getStatus();
        long ign = generator.getIgnitionEU(), need = type.ignitionThreshold();
        double heSecs = exoHeliumSecs();
        int plasma = generator.getHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT;
        boolean noHe = status == GeneratorStatus.NO_COOLANT;
        String[] labels = {Lang.tr("sc.gui.ex.d.ign"), Lang.tr("sc.gui.fus.power"), Lang.tr("sc.gui.fus.plasma"), Lang.tr("sc.gui.ex.d.he")};
        String[] vals = {lit ? Lang.tr("sc.gui.ex.ready") : (int) (100 * ign / Math.max(1, need)) + "%", generator.getRamp() / 10 + "%",
                Lang.tr("sc.gui.gen.plasma.short", plasma), heSecs <= 0 ? Lang.tr("sc.gui.ex.none") : fusTime(heSecs)};
        int[] cols = {GuiHoloSC.VALUE, GuiHoloSC.VALUE, noHe || generator.getHeat() > 700 ? GuiHoloSC.BAD : GuiHoloSC.VALUE,
                heSecs <= 0 ? GuiHoloSC.BAD : GuiHoloSC.VALUE};
        for (int i = 0; i < 4; i++) {
            smallFit(labels[i], EX_DIALS[i][0] + 11, EX_DIALS[i][1] - 6, 28, GuiHoloSC.LABEL);
            smallFit(vals[i], EX_DIALS[i][0] + 11, EX_DIALS[i][1], 28, cols[i]);
        }
        if (!lit) {
            smallFit(Lang.tr("sc.gui.fus.charge"), EX_BAR_X, 89, 80, GuiHoloSC.LABEL);
            smallFit(fusEu(ign) + " / " + fusEu(need) + " EU", EX_BAR_X + EX_BAR_W + 4, EX_BAR_Y, 54, GuiHoloSC.VALUE);
            String line;
            int col;
            if (status == GeneratorStatus.OVERHEATED) {
                line = Lang.tr("sc.gui.fus.st.overheat", plasma);
                col = GuiHoloSC.BAD;
            } else if (status == GeneratorStatus.DISABLED || status == GeneratorStatus.REDSTONE) {
                line = status.localized();
                col = GuiHoloSC.IDLE;
            } else {
                line = Lang.tr(heSecs > 0 ? "sc.gui.ex.st.charge.he" : "sc.gui.ex.st.charge.nohe");
                col = GuiHoloSC.CYAN & 0xFFFFFF;
            }
            smallFit(line, 14, 104, 190, col);
            return;
        }
        int out = generator.getLastOutput();
        fit(out + " EU/t", 14, 88, 100, noHe ? GuiHoloSC.WARN : GuiHoloSC.OK);
        String line, hint;
        int col, hintCol = GuiHoloSC.WARN;
        int toLimit = (int) Math.max(0, (TileEntityGeneratorSC.HEAT_LIMIT - generator.getHeat()) / 2 / 20);
        if (noHe) {
            line = Lang.tr("sc.gui.ex.st.nohe", toLimit);
            col = GuiHoloSC.BAD;
            hint = Lang.tr("sc.gui.ex.fill");
        } else if (status == GeneratorStatus.BUFFER_FULL) {
            line = Lang.tr("sc.gui.fus.st.full");
            col = GuiHoloSC.WARN;
            hint = Lang.tr("sc.gui.ex.warn", toLimit);
        } else if (status == GeneratorStatus.DISABLED || status == GeneratorStatus.REDSTONE) {
            line = status.localized();
            col = GuiHoloSC.IDLE;
            hint = Lang.tr("sc.gui.ex.warn", toLimit);
        } else if (stage == 2) {
            line = Lang.tr("sc.gui.fus.st.ramp", generator.getRamp() / 10);
            col = GuiHoloSC.CYAN & 0xFFFFFF;
            hint = Lang.tr("sc.gui.ex.warn", toLimit);
        } else {
            line = Lang.tr("sc.gui.ex.st.ok", generator.outputTier().name(), generator.outputTier().getVoltage(), fusTime(heSecs));
            col = GuiHoloSC.OK;
            hint = Lang.tr("sc.gui.ex.warn", toLimit);
        }
        smallFit(line, 14, 98, 190, col);
        smallFit(hint, 14, 105, 190, hintCol);
    }

    // ---- the High-Pressure Plasma Reactor ----

    /** Seconds each gas lasts at the rate it burns: {argon, deuterium}. */
    private double[] prSeconds() {
        double fm = Math.max(0.01, generator.fuelMultiplier());
        return new double[]{generator.getFuelTank().getFluidAmount() / (type.fuelRatePerTick * fm) / 20.0,
                generator.getFuelTank2().getFluidAmount() / (type.fuel2RatePerTick * fm) / 20.0};
    }

    private void drawPrBackground(int x, int y, float partialTicks) {
        float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
        boolean running = generator.getStatus() == GeneratorStatus.GENERATING;
        GuiSceneSC.frame(x + 14, y + 34, 124, 38);
        int sx = x + 19, sy = y + 38;                                          // the air separator
        drawRect(sx + 3, sy - 2, sx + 9, sy, 0xFF6A707A);
        drawRect(sx, sy, sx + 12, sy + 20, 0xFF8A909A);
        drawRect(sx + 1, sy + 1, sx + 11, sy + 19, 0xFF1A2A3A);
        for (int i = 0; i < 4; i++) {
            drawRect(sx + 2, sy + 3 + i * 4, sx + 10, sy + 4 + i * 4, running ? 0xFF6EA0C8 : 0xFF3A4A5A);
        }
        drawRect(x + 20, y + 64, x + 30, y + 70, 0xFF3A4450);                  // the deuterium feed
        drawRect(x + 21, y + 65, x + 29, y + 69, 0xFF6AE0FF);
        drawRect(x + 32, y + 48, x + 40, y + 51, 0xFF6A707A);                  // the pipe
        if (running) {
            for (int i = 0; i < 2; i++) {
                int d = (int) ((t * 1.5F + i * 4) % 8);
                drawRect(x + 32 + d, y + 49, x + 34 + d, y + 50, 0xFFC890FF);
            }
        }
        GuiSceneSC.pressureChamber(x + 40, y + 36, 66, 34, t, running);
        GuiSceneSC.frame(x + 107, y + 36, 30, 34);
        GuiSceneSC.plasmaRing(x + 122, y + 53, 9, t, running);
        // the dials: how long each gas lasts, against a full tank's worth
        FluidTank a = generator.getFuelTank(), d = generator.getFuelTank2();
        GuiSceneSC.dial(x + 22, y + 85, 8, a.getCapacity() > 0 ? (float) a.getFluidAmount() / a.getCapacity() : 0F);
        GuiSceneSC.dial(x + 68, y + 85, 8, d.getCapacity() > 0 ? (float) d.getFluidAmount() / d.getCapacity() : 0F);
        // the gases as the 4 : 1 they burn in, each part filled with how full its tank is
        int bx = x + PR_BAR_X, by = y + PR_BAR_Y, part = (PR_BAR_W - 2) * 4 / 5;
        float af = a.getCapacity() > 0 ? (float) a.getFluidAmount() / a.getCapacity() : 0F;
        float df = d.getCapacity() > 0 ? (float) d.getFluidAmount() / d.getCapacity() : 0F;
        drawRect(bx, by, bx + PR_BAR_W, by + 6, 0xFF04080C);
        drawRect(bx + 1, by + 1, bx + 1 + (int) (part * af), by + 5, 0xFFC890FF);
        drawRect(bx + 1 + part, by + 1, bx + 1 + part + (int) ((PR_BAR_W - 2 - part) * df), by + 5, 0xFF6AE0FF);
        drawRect(bx + part, by - 1, bx + part + 1, by + 7, 0xFFFFFFFF);
    }

    private void drawPrText() {
        fit(Lang.tr("sc.gui.pr.title"), 14, CAPTION_Y, 120, GuiHoloSC.CYAN & 0xFFFFFF);
        for (int i = 0; i < tankCount(); i++) {
            TextFitSC.drawCentered(fontRendererObj, tankLabel(i), tankX(i), 31, GuiTankGaugeSC.WIDTH - 7,
                    GuiHoloSC.LABEL, false, guiLeft, guiTop);
        }
        smallFit(Lang.tr("sc.gui.pr.argon"), 17, 61, 22, GuiHoloSC.LABEL);
        smallFit("D", 31, 65, 8, 0xA0E6FF);
        smallFit(generator.getLastOutput() + " EU/t", 108, 73, 30, generator.getLastOutput() > 0 ? GuiHoloSC.OK : GuiHoloSC.BAD);
        double[] secs = prSeconds();
        String[] labels = {Lang.tr("sc.gui.pr.argon"), Lang.tr("sc.gui.pr.deut")};
        for (int i = 0; i < 2; i++) {
            int cx = i == 0 ? 22 : 68;
            smallFit(labels[i], cx + 11, 79, 34, GuiHoloSC.LABEL);
            smallFit(Lang.tr("sc.gui.pr.for", fusTime(secs[i])), cx + 11, 85, 34, secs[i] <= 0 ? GuiHoloSC.BAD : GuiHoloSC.VALUE);
        }
        smallFit(Lang.tr("sc.gui.pr.ratio"), 14, PR_BAR_Y, 25, GuiHoloSC.LABEL);
        GeneratorStatus status = generator.getStatus();
        String line;
        int col;
        boolean noA = generator.getFuelTank().getFluidAmount() <= 0, noD = generator.getFuelTank2().getFluidAmount() <= 0;
        double need = type.fuelRatePerTick * Math.max(0.01, generator.fuelMultiplier());
        if (status == GeneratorStatus.GENERATING) {
            line = Lang.tr("sc.gui.pr.running", (int) Math.min(secs[0], secs[1]), Math.round(generator.ratedOutput() / need));
            col = GuiHoloSC.OK;
        } else if (status == GeneratorStatus.NO_FUEL) {
            line = Lang.tr(noA && noD ? "sc.gui.pr.nogas" : noA || secs[0] < secs[1] ? "sc.gui.pr.noargon" : "sc.gui.pr.nodeut");
            col = GuiHoloSC.BAD;
        } else {
            line = status.localized();
            col = status == GeneratorStatus.BUFFER_FULL ? GuiHoloSC.WARN : GuiHoloSC.IDLE;
        }
        smallFit(line, 14, 105, 190, col);
    }

    // ---- the RTG ----

    /** The capsule in slot `s` (0 / 1), or null. */
    private ItemStack rtgCapsule(int s) {
        ItemStack c = generator.getStackInSlot(s == 0 ? TileEntityGeneratorSC.SLOT_FUEL : TileEntityGeneratorSC.SLOT_BLANKET);
        return c != null && c.getItem() == com.sc.init.ModItems.isotopeCapsule ? c : null;
    }

    private void drawRtgBackground(int x, int y, float partialTicks) {
        float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
        GuiSceneSC.rtgBody(x + 14, y + 34, 78, 50, t, rtgCapsule(0) != null, rtgCapsule(1) != null);
        for (int s = 0; s < 2; s++) {                                          // each capsule's life
            int bx = x + (s == 0 ? ContainerGeneratorSC.RTG_SLOT_X0 : ContainerGeneratorSC.RTG_SLOT_X1) + 18;
            int by = y + ContainerGeneratorSC.RTG_SLOT_Y + 1;
            drawRect(bx, by, bx + 20, by + 4, 0xFF04080C);
            ItemStack c = rtgCapsule(s);
            if (c != null) {
                float f = 1F - (float) c.getItemDamage() / Math.max(1, c.getMaxDamage());
                drawRect(bx + 1, by + 1, bx + 1 + (int) (18 * f), by + 3, f > 0.5F ? 0xFF5AE66E : f > 0.2F ? 0xFFFFB040 : 0xFFE63C3C);
            }
        }
        for (int i = 0; i < 2; i++) {                                          // the chips: capsules x EU/t
            int cx = x + (i == 0 ? 98 : 130);
            drawRect(cx, y + 36, cx + 24, y + 56, 0xFF2A6A8A);
            drawRect(cx + 1, y + 37, cx + 23, y + 55, 0xFF0E3A50);
        }
    }

    private void drawRtgText() {
        fit(Lang.tr("sc.gui.rtg.title"), 14, CAPTION_Y, 90, GuiHoloSC.CYAN & 0xFFFFFF);
        int caps = (rtgCapsule(0) != null ? 1 : 0) + (rtgCapsule(1) != null ? 1 : 0);
        long first = Long.MAX_VALUE;
        for (int s = 0; s < 2; s++) {
            int tx = (s == 0 ? ContainerGeneratorSC.RTG_SLOT_X0 : ContainerGeneratorSC.RTG_SLOT_X1) + 18;
            int ty = ContainerGeneratorSC.RTG_SLOT_Y + 6;
            ItemStack c = rtgCapsule(s);
            if (c != null) {
                long secs = Math.max(0, c.getMaxDamage() - c.getItemDamage());
                first = Math.min(first, secs);
                smallFit(sfTime(secs), tx, ty, 20, GuiHoloSC.VALUE);
            } else {
                smallFit(Lang.tr("sc.gui.rtg.empty"), tx, ty, 20, 0x465A6E);
            }
        }
        String[] labels = {Lang.tr("sc.gui.rtg.caps"), "EU/t"};
        String[] vals = {String.valueOf(caps), String.valueOf(TileEntityGeneratorSC.CAPSULE_EU)};
        for (int i = 0; i < 2; i++) {
            int cx = i == 0 ? 98 : 130;
            int lw = Math.min(22, (int) (fontRendererObj.getStringWidth(labels[i]) * 0.625F));
            smallFit(labels[i], cx + (24 - lw) / 2, 38, 22, GuiHoloSC.LABEL);
            fontRendererObj.drawString(vals[i], cx + (24 - fontRendererObj.getStringWidth(vals[i])) / 2, 46, GuiHoloSC.VALUE);
        }
        fontRendererObj.drawString("x", 124, 42, GuiHoloSC.VALUE);
        fontRendererObj.drawString("=", 156, 42, GuiHoloSC.VALUE);
        fit(Lang.tr("sc.gui.sol.now", generator.getLastOutput()), 98, 60, GuiBigSC.SCREEN_RIGHT - 100, GuiHoloSC.VALUE);
        smallFit(Lang.tr("sc.gui.holo.gen.out", generator.outputTier().name(), generator.outputTier().getVoltage()), 98, 70, 100, GuiHoloSC.LABEL);
        GeneratorStatus status = generator.getStatus();
        String head;
        int col;
        if (status == GeneratorStatus.GENERATING) {
            head = Lang.tr("sc.gui.rtg.running");
            col = GuiHoloSC.OK;
        } else if (status == GeneratorStatus.NO_CAPSULE) {
            head = Lang.tr("sc.gui.rtg.nocaps");
            col = GuiHoloSC.BAD;
        } else if (status == GeneratorStatus.BUFFER_FULL && caps > 0) {
            head = Lang.tr("sc.gui.rtg.wasted");
            col = GuiHoloSC.WARN;
        } else {
            head = status.localized();
            col = status == GeneratorStatus.BUFFER_FULL ? GuiHoloSC.WARN : GuiHoloSC.IDLE;
        }
        fit(head, 98, 79, GuiBigSC.SCREEN_RIGHT - 100, col);
        smallFit(Lang.tr("sc.gui.rtg.warn.1"), 98, 89, 106, GuiHoloSC.WARN);
        smallFit(Lang.tr("sc.gui.rtg.warn.2"), 98, 95, 106, GuiHoloSC.WARN);
        if (first != Long.MAX_VALUE) {
            smallFit(Lang.tr("sc.gui.rtg.first", sfTime(first)), 98, 104, 106, GuiHoloSC.LABEL);
        }
    }

    // ---- the Hydrogen Fuel Cell ----

    /** Seconds each gas lasts at the rate it burns: {hydrogen, oxygen}. */
    private double[] fcSeconds() {
        double fm = Math.max(0.01, generator.fuelMultiplier());
        double h = generator.getFuelTank().getFluidAmount() / (type.fuelRatePerTick * fm) / 20.0;
        double o = generator.getFuelTank2().getFluidAmount() / (type.fuel2RatePerTick * fm) / 20.0;
        return new double[]{h, o};
    }

    private void drawFcBackground(int x, int y, float partialTicks) {
        float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
        boolean running = generator.getStatus() == GeneratorStatus.GENERATING;
        GuiSceneSC.fuelCellSection(x + FC_X, y + FC_Y, FC_W, FC_H, t, running);
        // the gases as the 2 : 1 they burn in; each part filled with how full its tank is
        int bx = x + FC_BAR_X, by = y + FC_BAR_Y, a = (FC_BAR_W - 2) * 2 / 3;
        FluidTank h = generator.getFuelTank(), o = generator.getFuelTank2();
        float hf = h.getCapacity() > 0 ? (float) h.getFluidAmount() / h.getCapacity() : 0F;
        float of = o.getCapacity() > 0 ? (float) o.getFluidAmount() / o.getCapacity() : 0F;
        drawRect(bx, by, bx + FC_BAR_W, by + 6, 0xFF04080C);
        drawRect(bx + 1, by + 1, bx + 1 + (int) (a * hf), by + 5, 0xFF9AD8FF);
        drawRect(bx + 1 + a, by + 1, bx + 1 + a + (int) ((FC_BAR_W - 2 - a) * of), by + 5, 0xFFFF8A8A);
        drawRect(bx + 1 + a, by - 1, bx + 2 + a, by + 7, 0xFFFFFFFF);
    }

    private void drawFcText() {
        fit(Lang.tr("sc.gui.fc.title"), 14, CAPTION_Y, 90, GuiHoloSC.CYAN & 0xFFFFFF);
        for (int i = 0; i < tankCount(); i++) {
            TextFitSC.drawCentered(fontRendererObj, tankLabel(i), RIGHT_X + i * TANK_GAP, TANK_LABEL_Y, GuiTankGaugeSC.WIDTH - 7,
                    GuiHoloSC.LABEL, false, guiLeft, guiTop);
        }
        smallFit(Lang.tr("sc.gui.fc.eq"), 14, 33, 90, GuiHoloSC.LABEL);
        smallFit("H2", FC_X + 4, FC_Y + 2, 12, 0x96DCFF);
        smallFit("O2", FC_X + FC_W - 14, FC_Y + 2, 12, 0xFF9696);
        smallFit(Lang.tr("sc.gui.fc.ratio"), 14, FC_BAR_Y, 25, GuiHoloSC.LABEL);
        double[] secs = fcSeconds();
        int hs = (int) secs[0], os = (int) secs[1];
        int low = Math.min(hs, os);
        smallFit(Lang.tr("sc.gui.fc.h2", hs), FC_BAR_X, FC_BAR_Y + 7, 40,
                hs <= 0 ? GuiHoloSC.BAD : hs == low && hs < 10 ? GuiHoloSC.WARN : 0x96DCFF);
        smallFit(Lang.tr("sc.gui.fc.o2", os), FC_BAR_X + 42, FC_BAR_Y + 7, 34,
                os <= 0 ? GuiHoloSC.BAD : os == low && os < 10 ? GuiHoloSC.WARN : 0xFF9696);
        double fm = Math.max(0.01, generator.fuelMultiplier());
        String water = String.format(java.util.Locale.ROOT, "%.0f", type.fuel2RatePerTick * fm);
        GeneratorStatus status = generator.getStatus();
        String head, hint;
        int headCol, hintCol = GuiHoloSC.WARN;
        boolean noH = generator.getFuelTank().getFluidAmount() <= 0, noO = generator.getFuelTank2().getFluidAmount() <= 0;
        if (status == GeneratorStatus.GENERATING) {
            head = Lang.tr("sc.gui.fc.running", generator.getLastOutput());
            headCol = GuiHoloSC.OK;
            hint = Lang.tr("sc.gui.fc.water", water);
        } else if (status == GeneratorStatus.WATER_FULL) {
            head = Lang.tr("sc.gui.fc.waterfull");
            headCol = GuiHoloSC.BAD;
            hint = Lang.tr("sc.gui.fc.pump", water);
        } else if (status == GeneratorStatus.NO_FUEL) {
            head = Lang.tr(noH && noO ? "sc.gui.fc.nogas" : noH || secs[0] < secs[1] ? "sc.gui.fc.noh2" : "sc.gui.fc.noo2");
            headCol = GuiHoloSC.BAD;
            hint = Lang.tr("sc.gui.fc.sources");
            hintCol = GuiHoloSC.LABEL;
        } else {
            head = status.localized();
            headCol = status == GeneratorStatus.BUFFER_FULL ? GuiHoloSC.WARN : GuiHoloSC.IDLE;
            hint = Lang.tr("sc.gui.fc.water", water);
        }
        fit(head, 14, 95, 90, headCol);
        smallFit(hint, 14, 104, 190, hintCol);
    }

    // ---- the Thermoelectric Generator ----

    /** thermoKind() of the side with this ForgeDirection ordinal. */
    private int thermoSide(int dir) {
        return generator.getSideInfo() >> (dir * 3) & 7;
    }

    private void drawThermoBackground(int x, int y, float partialTicks) {
        float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
        GuiSceneSC.frame(x + 14, y + 34, 64, 58);
        drawRect(x + TH_CX, y + TH_CY, x + TH_CX + TH_C, y + TH_CY + TH_C, 0xFF8A909A);
        GuiSceneSC.peltier(x + TH_CX + 1, y + TH_CY + 1, TH_C - 2, TH_C - 2, t, generator.getInfoA(), 3);
        for (int d = 0; d < 6; d++) {
            GuiSceneSC.thermoTile(x + TH_POS[d][0], y + TH_POS[d][1], TH_C, thermoSide(d), t);
        }
        for (int i = 0; i < 2; i++) {                                          // the chips: pairs, dT
            int cx = x + (i == 0 ? 84 : 116), w = i == 0 ? 24 : 28;
            drawRect(cx, y + 36, cx + w, y + 56, 0xFF2A6A8A);
            drawRect(cx + 1, y + 37, cx + w - 1, y + 55, 0xFF0E3A50);
        }
    }

    private void drawThermoText() {
        fit(Lang.tr("sc.gui.th.title"), 14, CAPTION_Y, 90, GuiHoloSC.CYAN & 0xFFFFFF);
        String[] shortNames = {"sc.gui.th.s.down", "sc.gui.th.s.up", "sc.gui.ww.n", "sc.gui.ww.s", "sc.gui.ww.w", "sc.gui.ww.e"};
        for (int d = 0; d < 6; d++) {
            smallFit(Lang.tr(shortNames[d]), TH_POS[d][0] + 1, TH_POS[d][1] + 1, TH_C - 1, 0xF0F5FF);
        }
        // pairs x dT / 8
        String[] labels = {Lang.tr("sc.gui.th.pairs"), Lang.tr("sc.gui.th.dt")};
        String[] vals = {String.valueOf(generator.getInfoA()), String.valueOf(generator.getInfoB())};
        for (int i = 0; i < 2; i++) {
            int cx = i == 0 ? 84 : 116, w = i == 0 ? 24 : 28;
            int lw = Math.min(w - 2, (int) (fontRendererObj.getStringWidth(labels[i]) * 0.625F));
            smallFit(labels[i], cx + (w - lw) / 2, 38, w - 2, GuiHoloSC.LABEL);
            int vw = fontRendererObj.getStringWidth(vals[i]);
            fontRendererObj.drawString(vals[i], cx + (w - Math.min(w - 2, vw)) / 2, 46, GuiHoloSC.VALUE);
        }
        fontRendererObj.drawString("x", 110, 42, GuiHoloSC.VALUE);
        fontRendererObj.drawString("/8", 146, 42, GuiHoloSC.VALUE);
        int out = generator.getLastOutput();
        fit(out + " EU/t", 84, 60, 42, out > 0 ? GuiHoloSC.OK : GuiHoloSC.BAD);
        smallFit(Lang.tr("sc.gui.ww.of", type.euPerTick), 128, 62, 44, GuiHoloSC.LABEL);
        // a row per side
        String[] names = {"sc.gui.th.down", "sc.gui.th.up", "sc.gui.ww.north", "sc.gui.ww.south", "sc.gui.ww.west", "sc.gui.ww.east"};
        int hot = 0, cold = 0, hotSum = 0, coldSum = 0;
        for (int i = 0; i < 6; i++) {
            int d = TH_ORDER[i], k = thermoSide(d), kelvin = TileEntityGeneratorSC.THERMO_KELVIN[k];
            int rx = 84 + (i % 2) * 46, ry = 70 + (i / 2) * 7;
            smallFit(Lang.tr(names[d]), rx, ry, 17, GuiHoloSC.LABEL);
            String v = k == 0 ? "-" : Lang.tr("sc.gui.th.kind." + k) + " " + kelvin;
            smallFit(v, rx + 18, ry, 28, k == 0 ? 0x465A6E : k <= 2 ? 0xFF965A : 0x82BEFF);
            if (k == 1 || k == 2) {
                hot++;
                hotSum += kelvin;
            } else if (k >= 3) {
                cold++;
                coldSum += kelvin;
            }
        }
        GeneratorStatus status = generator.getStatus();
        String line;
        int col;
        if (status == GeneratorStatus.GENERATING) {
            line = Lang.tr("sc.gui.th.running", generator.outputTier().name(), generator.outputTier().getVoltage(),
                    hot > 0 ? hotSum / hot : 0, cold > 0 ? coldSum / cold : 0);
            col = GuiHoloSC.OK;
        } else if (status == GeneratorStatus.NO_HEAT) {
            line = Lang.tr(hot == 0 && cold == 0 ? "sc.gui.th.none" : hot == 0 ? "sc.gui.th.nohot" : "sc.gui.th.nocold");
            col = GuiHoloSC.BAD;
        } else {
            line = status.localized();
            col = status == GeneratorStatus.BUFFER_FULL ? GuiHoloSC.WARN : GuiHoloSC.IDLE;
        }
        smallFit(line, 14, 96, 190, col);
        smallFit(Lang.tr("sc.gui.th.best"), 14, 104, 190, GuiHoloSC.LABEL);
    }

    // ---- the Water Wheel ----

    /** Each side (north, south, east, west): 0 no water, 1 flowing, 2 standing - from infoB. */
    private int[] waterSides() {
        int bits = generator.getInfoB();
        return new int[]{bits & 3, bits >> 2 & 3, bits >> 4 & 3, bits >> 6 & 3};
    }

    private void drawWaterBackground(int x, int y, float partialTicks) {
        float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
        int n = generator.getInfoA();
        float spin = generator.getStatus() == GeneratorStatus.GENERATING ? Math.max(0.25F, n / 4F) : 0F;
        GuiSceneSC.waterCross(x + WW_X, y + WW_Y, WW_CELL, t, waterSides(), spin);
        for (int i = 0; i < 4; i++) {                                          // the output in four steps
            int bx = x + WW_RIGHT + i * 24, by = y + 45;
            drawRect(bx, by, bx + 22, by + 6, 0xFF04080C);
            drawRect(bx + 1, by + 1, bx + 21, by + 5, i < n ? 0xFF4A9AE8 : 0xFF2A3038);
        }
    }

    private void drawWaterText() {
        fit(Lang.tr("sc.gui.ww.title"), 14, CAPTION_Y, 90, GuiHoloSC.CYAN & 0xFFFFFF);
        int[] sides = waterSides();
        String[] letters = {"sc.gui.ww.n", "sc.gui.ww.s", "sc.gui.ww.e", "sc.gui.ww.w"};
        int c = WW_CELL, cx = WW_X + 2 + c, cy = WW_Y + 2 + c;
        int[][] pos = {{cx, cy - c}, {cx, cy + c}, {cx + c, cy}, {cx - c, cy}};
        String[] mark = {"sc.gui.ww.mark.none", "sc.gui.ww.mark.flow", "sc.gui.ww.mark.still"};
        int[] markCol = {0x966E5A, GuiHoloSC.VALUE, 0xFF966E};
        for (int s = 0; s < 4; s++) {
            smallFit(Lang.tr(letters[s]), pos[s][0] + 2, pos[s][1] + 1, 6, GuiHoloSC.LABEL);
            String m = Lang.tr(mark[sides[s]]);
            int w = Math.min(c - 2, (int) (fontRendererObj.getStringWidth(m) * 0.625F));
            smallFit(m, pos[s][0] + (c - w) / 2, pos[s][1] + c / 2 - 1, c - 2, markCol[sides[s]]);
        }
        int n = generator.getInfoA(), out = generator.getLastOutput();
        fit(out + " EU/t", WW_RIGHT, 34, 36, out > 0 ? GuiHoloSC.OK : GuiHoloSC.BAD);
        smallFit(Lang.tr("sc.gui.ww.of", type.euPerTick), 112, 36, 40, GuiHoloSC.LABEL);
        String[] names = {"sc.gui.ww.north", "sc.gui.ww.south", "sc.gui.ww.east", "sc.gui.ww.west"};
        String[] vals = {"sc.gui.ww.row.none", "sc.gui.ww.row.flow", "sc.gui.ww.row.still"};
        for (int s = 0; s < 4; s++) {
            int ry = 55 + s * 8;
            smallFit(Lang.tr(names[s]), WW_RIGHT, ry, 24, GuiHoloSC.LABEL);
            smallFit(Lang.tr(vals[sides[s]]), WW_RIGHT + 26, ry, 94, sides[s] == 1 ? GuiHoloSC.VALUE : 0xFF8C6E);
        }
        GeneratorStatus status = generator.getStatus();
        String line;
        int col;
        if (status == GeneratorStatus.GENERATING) {
            line = Lang.tr("sc.gui.ww.running", generator.outputTier().name(), generator.outputTier().getVoltage());
            col = GuiHoloSC.OK;
        } else if (status == GeneratorStatus.NO_WATER) {
            line = Lang.tr("sc.gui.ww.nowater");
            col = GuiHoloSC.BAD;
        } else {
            line = status.localized();
            col = status == GeneratorStatus.BUFFER_FULL ? GuiHoloSC.WARN : GuiHoloSC.IDLE;
        }
        fit(line, 14, 96, 158, col);
        smallFit(Lang.tr("sc.gui.ww.hint"), 14, 105, 190, GuiHoloSC.LABEL);
    }

    // ---- the Wind Turbine ----

    /** {height, free air, weather} factors as the server works them out (the weather from the client's world). */
    private double[] windFactors() {
        double h = Math.max(0, Math.min(1, generator.getInfoA() / 96.0));
        double free = Math.max(0, generator.getInfoB() / 100.0);
        double weather = mc.theWorld != null && mc.theWorld.isThundering() ? 1.5 : mc.theWorld != null && mc.theWorld.isRaining() ? 1.25 : 1;
        return new double[]{h, free, weather};
    }

    private boolean windRotor() {
        ItemStack r = generator.getStackInSlot(TileEntityGeneratorSC.SLOT_FUEL);
        return r != null && r.getItem() == com.sc.init.ModItems.windRotor;
    }

    private void drawWindBackground(int x, int y, float partialTicks) {
        float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
        double[] f = windFactors();
        boolean running = generator.getStatus() == GeneratorStatus.GENERATING;
        float spin = running ? Math.max(0.3F, Math.min(1F, generator.getLastOutput() / (float) type.euPerTick)) : 0F;
        boolean rain = f[2] > 1;
        GuiSceneSC.windLandscape(x + WD_LAND_X, y + WD_LAND_Y, WD_LAND_W, WD_LAND_H, t, (float) f[0], rain, windRotor() ? spin : 0F);
        for (int i = 0; i < 3; i++) {
            int cx = x + WD_CHIP_X[i], cy = y + 36;
            drawRect(cx, cy, cx + WD_CHIP_W, cy + 20, windRotor() ? 0xFF2A6A8A : 0xFF1A2430);
            drawRect(cx + 1, cy + 1, cx + WD_CHIP_W - 1, cy + 19, windRotor() ? 0xFF0E3A50 : 0xFF0A1218);
        }
        GuiSceneSC.dial(x + 95, y + 69, 10, Math.min(1F, generator.getLastOutput() / (float) type.euPerTick));
        // the rotor's wear
        ItemStack r = generator.getStackInSlot(TileEntityGeneratorSC.SLOT_FUEL);
        int bx = x + ContainerGeneratorSC.WD_SLOT_X + 19, by = y + ContainerGeneratorSC.WD_SLOT_Y + 6;
        drawRect(bx, by, bx + 44, by + 4, 0xFF04080C);
        if (windRotor()) {
            float life = 1F - (float) r.getItemDamage() / Math.max(1, r.getMaxDamage());
            int col = life > 0.5F ? 0xFF5AE66E : life > 0.2F ? 0xFFFFB040 : 0xFFE63C3C;
            drawRect(bx + 1, by + 1, bx + 1 + (int) (42 * life), by + 3, col);
        }
    }

    private void drawWindText() {
        fit(Lang.tr("sc.gui.wd.title"), 14, CAPTION_Y, 90, GuiHoloSC.CYAN & 0xFFFFFF);
        double[] f = windFactors();
        boolean rotor = windRotor();
        // the landscape's labels
        int sea = WD_LAND_Y + WD_LAND_H - 8, full = WD_LAND_Y + 5;
        int ny = sea - (int) ((sea - full) * f[0]);
        smallFit(Lang.tr("sc.gui.wd.full"), WD_LAND_X + WD_LAND_W - 22, full + 1, 21, 0x5AE66E);
        smallFit(Lang.tr("sc.gui.wd.sea"), WD_LAND_X + WD_LAND_W - 26, sea - 6, 25, 0xC8E6FF);
        smallFit("+" + generator.getInfoA(), WD_LAND_X + WD_LAND_W / 2 + 10, (ny + sea) / 2 - 3, 20, 0xFFE678);
        // the chips
        String[] labels = {Lang.tr("sc.gui.wd.height"), Lang.tr("sc.gui.wd.free"),
                Lang.tr(f[2] >= 1.5 ? "sc.gui.wd.storm" : f[2] > 1 ? "sc.gui.wd.rain" : "sc.gui.wd.weather")};
        for (int i = 0; i < 3; i++) {
            int cx = WD_CHIP_X[i], w = WD_CHIP_W;
            String v = String.format(java.util.Locale.ROOT, "%.2f", f[i]).replace('.', ',');
            if (v.endsWith(",00")) {
                v = v.substring(0, v.length() - 3);
            }
            int lw = Math.min(w - 2, (int) (fontRendererObj.getStringWidth(labels[i]) * 0.625F));
            smallFit(labels[i], cx + (w - lw) / 2, 38, w - 2, rotor ? GuiHoloSC.LABEL : 0x465A6E);
            int vw = Math.min(w - 2, fontRendererObj.getStringWidth(v));
            if (fontRendererObj.getStringWidth(v) <= w - 2) {
                fontRendererObj.drawString(v, cx + (w - vw) / 2, 46, rotor ? (i == 2 && f[2] > 1 ? 0x8CBEFF : GuiHoloSC.VALUE) : 0x465A6E);
            } else {
                smallFit(v, cx + 2, 47, w - 4, rotor ? GuiHoloSC.VALUE : 0x465A6E);
            }
            if (i < 2) {
                fontRendererObj.drawString("x", cx + w + 2, 42, GuiHoloSC.VALUE);
            }
        }
        // the output and the rotor
        int out = generator.getLastOutput();
        fit(out + " EU/t", 108, 60, 64, out > 0 ? GuiHoloSC.OK : GuiHoloSC.BAD);
        smallFit(Lang.tr("sc.gui.wd.of", type.euPerTick), 108, 70, 64, GuiHoloSC.LABEL);
        int rx = ContainerGeneratorSC.WD_SLOT_X + 19, ry = ContainerGeneratorSC.WD_SLOT_Y;
        smallFit(Lang.tr("sc.gui.wd.rotor"), rx, ry - 1, 44, GuiHoloSC.LABEL);
        ItemStack r = generator.getStackInSlot(TileEntityGeneratorSC.SLOT_FUEL);
        if (rotor) {
            int secs = Math.max(0, r.getMaxDamage() - r.getItemDamage());
            smallFit(sfTime(secs), rx, ry + 11, 44, GuiHoloSC.VALUE);
        } else {
            smallFit(Lang.tr("sc.gui.wd.norotor.short"), rx, ry + 11, 44, GuiHoloSC.BAD);
        }
        // the status
        GeneratorStatus status = generator.getStatus();
        String line;
        int col;
        if (status == GeneratorStatus.GENERATING) {
            line = Lang.tr("sc.gui.wd.running", generator.outputTier().name(), generator.outputTier().getVoltage());
            col = GuiHoloSC.OK;
        } else if (status == GeneratorStatus.NO_ROTOR) {
            line = Lang.tr("sc.gui.wd.norotor");
            col = GuiHoloSC.BAD;
        } else if (status == GeneratorStatus.NO_WIND) {
            line = Lang.tr("sc.gui.wd.nowind");
            col = GuiHoloSC.BAD;
        } else {
            line = status.localized();
            col = status == GeneratorStatus.BUFFER_FULL ? GuiHoloSC.WARN : GuiHoloSC.IDLE;
        }
        fit(line, 14, 96, 158, col);
        smallFit(Lang.tr(generator.getInfoA() <= 0 && rotor ? "sc.gui.wd.hint.sea" : "sc.gui.wd.hint"), 14, 105, 190, GuiHoloSC.LABEL);
    }

    // ---- the Silicon Solar Panel ----

    /** The time of day in ticks, 0 sunrise .. 12000 sunset .. 24000. */
    private long dayTime() {
        return mc.theWorld == null ? 0 : (mc.theWorld.getWorldTime() % 24000 + 24000) % 24000;
    }

    private boolean solarSky() {
        return generator.getStatus() != GeneratorStatus.NO_SUNLIGHT;
    }

    /** What the panel makes now, EU/t (0 with no sky). */
    private int solarNow() {
        double[] f = solarFactors();
        return solarSky() ? Math.max(1, (int) Math.round(generator.ratedOutput() * f[0] * f[1])) : 0;
    }

    /** The multipliers now: {day or night, rain} (the server's view, via infoA / infoB). */
    private double[] solarFactors() {
        return new double[]{generator.getInfoA() != 0 ? 1.0 : 0.5, generator.getInfoB() != 0 ? 0.6 : 1.0};
    }

    private void drawSolarBackground(int x, int y, float partialTicks) {
        float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
        boolean day = generator.getInfoA() != 0, rain = generator.getInfoB() != 0;
        long time = dayTime();
        float arc = day ? Math.min(1F, time / 12000F) : Math.max(0F, (time - 12000) / 12000F);
        if (gaas) {
            int[] cells = solarCells();
            GuiSceneSC.sky(x + SOL_SKY_X, y + SOL_SKY_Y, SOL_SKY_W, SOL_SKY_H, t, day, rain, arc, !solarSky(), cells[0], cells[1]);
            boolean paused = generator.getStatus() == GeneratorStatus.BUFFER_FULL || !solarSky();
            int fx = x + GA_FLOW_X, fy = y + GA_FLOW_Y;
            for (int i = 0; i < 3; i++) {                                          // panel -> buffer -> the line
                int kIn = paused ? i * 4 : (int) ((t * 1.5F + i * 4) % 12), kOut = flowOut ? (int) ((t * 1.5F + i * 4) % 12) : i * 4;
                drawRect(fx + 27 + kIn, fy + 3, fx + 29 + kIn, fy + 5, paused ? 0xFF3A4450 : 0xFFFFE070);
                drawRect(fx + 75 + kOut, fy + 3, fx + 77 + kOut, fy + 5, flowOut ? 0xFF6EE6FF : 0xFF3A4450);
            }
            drawRect(fx + 42, fy - 1, fx + 72, fy + 9, 0xFF1E3444);
            drawRect(fx + 43, fy, fx + 71, fy + 8, 0xFF0A1218);
        } else {
            GuiSceneSC.sky(x + SOL_SKY_X, y + SOL_SKY_Y, SOL_SKY_W, SOL_SKY_H, t, day, rain, arc, !solarSky());
        }
        for (int i = 0; i < 3; i++) {
            int cx = x + SOL_CHIP_X[i], cy = y + 36;
            drawRect(cx, cy, cx + SOL_CHIP_W[i], cy + 20, solarSky() ? 0xFF2A6A8A : 0xFF1A2430);
            drawRect(cx + 1, cy + 1, cx + SOL_CHIP_W[i] - 1, cy + 19, solarSky() ? 0xFF0E3A50 : 0xFF0A1218);
        }
        int sx = x + SOL_SKY_X, sy = y + SOL_STRIP_Y;                          // the day strip: day, then night
        drawRect(sx, sy, sx + SOL_STRIP_W, sy + 10, 0xFF04080C);
        drawRect(sx + 1, sy + 1, sx + 1 + (SOL_STRIP_W - 2) / 2, sy + 9, 0xFFE8C850);
        drawRect(sx + 1 + (SOL_STRIP_W - 2) / 2, sy + 4, sx + SOL_STRIP_W - 1, sy + 9, 0xFF3A5AA8);
        int mx = sx + 1 + (int) ((SOL_STRIP_W - 2) * (time / 24000F));
        drawRect(mx, sy - 2, mx + 1, sy + 12, 0xFFFFFFFF);
    }

    private void drawSolarText() {
        fit(Lang.tr("sc.gui.holo.gen.passive"), SOL_SKY_X, CAPTION_Y, 60, GuiHoloSC.CYAN & 0xFFFFFF);
        boolean sky = solarSky(), day = generator.getInfoA() != 0, rain = generator.getInfoB() != 0;
        double[] f = solarFactors();
        int rated = generator.ratedOutput();
        String[] labels = {Lang.tr("sc.gui.sol.rated"), Lang.tr(day ? "sc.gui.sol.day" : "sc.gui.sol.night"),
                Lang.tr(rain ? "sc.gui.sol.rain" : "sc.gui.sol.dry")};
        String[] values = {String.valueOf(rated), day ? "1" : "0,5", rain ? "0,6" : "1"};
        int[] cols = {GuiHoloSC.VALUE, day ? 0xFFDC64 : 0x8CAAFF, rain ? 0x8CBEFF : GuiHoloSC.VALUE};
        for (int i = 0; i < 3; i++) {
            int w = SOL_CHIP_W[i], cx = SOL_CHIP_X[i];
            int lw = fontRendererObj.getStringWidth(labels[i]) / 2;
            smallFit(labels[i], cx + (w - Math.min(lw, w - 2)) / 2, 38, w - 2, sky ? GuiHoloSC.LABEL : 0x465A6E);
            int vw = fontRendererObj.getStringWidth(values[i]);
            if (vw <= w - 2) {
                fontRendererObj.drawString(values[i], cx + (w - vw) / 2, 46, sky ? cols[i] : 0x465A6E);
            } else {                                                           // 4096, 16384: shrink to fit the chip
                smallFit(values[i], cx + 2, 47, w - 4, sky ? cols[i] : 0x465A6E);
            }
            fontRendererObj.drawString(i < 2 ? "x" : "=", cx + w + 2, 42, GuiHoloSC.VALUE);
        }
        int now = solarNow();
        GeneratorStatus status = generator.getStatus();
        boolean full = status == GeneratorStatus.BUFFER_FULL;
        if (gaas && sky && full) {
            fit(Lang.tr("sc.gui.sol.paused"), 80, GA_NOW_Y, GuiBigSC.SCREEN_RIGHT - 82, GuiHoloSC.WARN);
            smallFit(Lang.tr("sc.gui.sol.waiting", now), 80, GA_ST_Y, GuiBigSC.SCREEN_RIGHT - 82, GuiHoloSC.VALUE);
        } else {
            fit(Lang.tr("sc.gui.sol.now", now), 80, gaas ? GA_NOW_Y : 60, GuiBigSC.SCREEN_RIGHT - 82, GuiHoloSC.VALUE);
        }
        if (gaas) {
            smallFit(Lang.tr("sc.gui.sol.flow.panel"), GA_FLOW_X, GA_FLOW_Y + 1, 26, GuiHoloSC.LABEL);
            String buf = Lang.tr("sc.gui.sol.flow.buffer");
            int bw = Math.min(26, (int) (fontRendererObj.getStringWidth(buf) * 0.625F));
            smallFit(buf, GA_FLOW_X + 57 - bw / 2, GA_FLOW_Y + 2, 26, GuiHoloSC.VALUE);
            fontRendererObj.drawString(generator.outputTier().name(), GA_FLOW_X + 91, GA_FLOW_Y, GuiHoloSC.VALUE);
        }
        String st = !sky ? Lang.tr("sc.gui.sol.nosky") : status == GeneratorStatus.BUFFER_FULL ? status.localized()
                : Lang.tr(day ? (rain ? "sc.gui.sol.dayrain" : "sc.gui.sol.dayclear") : (rain ? "sc.gui.sol.nightrain" : "sc.gui.sol.nightclear"));
        if (!(gaas && sky && full)) smallFit(st, 80, gaas ? GA_ST_Y : 71, GuiBigSC.SCREEN_RIGHT - 82, !sky ? GuiHoloSC.BAD : status == GeneratorStatus.BUFFER_FULL ? GuiHoloSC.WARN
                : day ? 0xFFDC64 : 0x8CAAFF);
        smallFit(Lang.tr("sc.gui.sol.dayout", rated), SOL_SKY_X, SOL_STRIP_Y + 13, 90, 0xE8C850);
        smallFit(Lang.tr("sc.gui.sol.nightout", Math.max(1, (int) Math.round(rated * 0.5))), SOL_SKY_X + SOL_STRIP_W / 2, SOL_STRIP_Y + 13, 90, 0x7896DC);
        long time = dayTime();
        long left = day ? Math.max(0, 12000 - time) : time >= 12000 ? 24000 - time : 0;
        int min = (int) Math.max(1, (left / 20 + 59) / 60);
        if (gaas && sky && full) {
            smallFit(Lang.tr("sc.gui.sol.connect"), SOL_SKY_X, SOL_STRIP_Y + 21, SOL_STRIP_W, GuiHoloSC.WARN);
            return;
        }
        smallFit(Lang.tr(day ? "sc.gui.sol.tosunset" : "sc.gui.sol.tosunrise", min) + " - "
                        + Lang.tr("sc.gui.holo.gen.out", generator.outputTier().name(), generator.outputTier().getVoltage()),
                SOL_SKY_X, SOL_STRIP_Y + 21, SOL_STRIP_W, GuiHoloSC.LABEL);
    }

    // ---- the Combustion Generator ----

    /** The fuel in the tank: its row in COMB_FUELS, or -1. */
    private int combFuel() {
        net.minecraftforge.fluids.FluidStack f = generator.getFuelTank().getFluid();
        String n = f == null || f.getFluid() == null ? null : f.getFluid().getName();
        for (int i = 0; n != null && i < COMB_FUELS.length; i++) {
            for (String s : COMB_FUELS[i]) {
                if (s.equals(n)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** mB a tick the fuel in the tank burns at, 0 for none. */
    private double combRate() {
        net.minecraftforge.fluids.FluidStack f = generator.getFuelTank().getFluid();
        double per = f == null || f.getFluid() == null ? 0 : type.euPerMb(f.getFluid().getName());
        return per <= 0 ? 0 : type.euPerTick / per * generator.fuelMultiplier();
    }

    private void drawCombBackground(int x, int y, float partialTicks) {
        float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
        boolean running = generator.getStatus() == GeneratorStatus.GENERATING;
        int rated = Math.max(1, generator.ratedOutput());
        GuiSceneSC.dial(x + 26, y + 79, 11, Math.min(1F, (float) generator.getLastOutput() / rated));
        GuiSceneSC.engine(x + COMB_ENGINE_X, y + COMB_TOP, COMB_ENGINE_W, 64, t, running);
        GuiSceneSC.frame(x + COMB_TABLE_X, y + COMB_TOP, COMB_TABLE_W, COMB_TABLE_H);
        int cur = combFuel(), w = COMB_TABLE_W - 8;
        for (int i = 0; i < COMB_FUELS.length; i++) {
            int yy = y + COMB_TOP + 10 + i * 8 + 5;
            double per = type.euPerMb(COMB_FUELS[i][0]);
            drawRect(x + COMB_TABLE_X + 3, yy, x + COMB_TABLE_X + 3 + w, yy + 2, 0xFF04080C);
            drawRect(x + COMB_TABLE_X + 3, yy, x + COMB_TABLE_X + 3 + (int) (w * per / 24.0), yy + 2, i == cur ? 0xFFD8A040 : 0xFF3A5A7A);
        }
        FluidTank tank = generator.getFuelTank();
        float left = tank.getCapacity() > 0 ? (float) tank.getFluidAmount() / tank.getCapacity() : 0F;
        int bx = x + 64, bw = 108;
        drawRect(bx, y + COMB_RESERVE_Y, bx + bw, y + COMB_RESERVE_Y + 4, 0xFF04080C);
        for (int i = 0; i < 20; i++) {
            int a = bx + 1 + i * (bw - 2) / 20, b = bx + 1 + (i + 1) * (bw - 2) / 20 - 1;
            drawRect(a, y + COMB_RESERVE_Y + 1, b, y + COMB_RESERVE_Y + 3, (i + 0.5F) / 20 < left ? 0xFFD8A040 : 0xFF2A3038);
        }
    }

    private void drawCombText() {
        GeneratorStatus status = generator.getStatus();
        smallFit(status.localized(), LEFT_X, 52, 28, statusColor(status));
        smallFit(Lang.tr("sc.gui.comb.now", generator.getLastOutput(), generator.ratedOutput()), LEFT_X, 92, 28, GuiHoloSC.VALUE);
        smallFit(Lang.tr("sc.gui.comb.table"), COMB_TABLE_X + 3, COMB_TOP + 2, COMB_TABLE_W - 6, GuiHoloSC.LABEL);
        int cur = combFuel();
        for (int i = 0; i < COMB_FUELS.length; i++) {
            int yy = COMB_TOP + 10 + i * 8;
            String v = String.valueOf((int) type.euPerMb(COMB_FUELS[i][0]));
            int vw = fontRendererObj.getStringWidth(v) / 2;
            smallFit(Lang.tr("sc.gui.comb.fuel." + i), COMB_TABLE_X + 3, yy, COMB_TABLE_W - 12 - vw, i == cur ? GuiHoloSC.VALUE : 0x6A829A);
            smallFit(v, COMB_TABLE_X + COMB_TABLE_W - 4 - vw, yy, vw + 1, i == cur ? GuiHoloSC.VALUE : 0x6A829A);
        }
        TextFitSC.drawCentered(fontRendererObj, tankLabel(0), COMB_TANK_X, 30, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        smallFit(Lang.tr("sc.gui.comb.reserve"), COMB_ENGINE_X - 4, COMB_RESERVE_Y, 22, GuiHoloSC.LABEL);
        FluidTank tank = generator.getFuelTank();
        double rate = combRate();
        String line;
        if (rate <= 0 || tank.getFluidAmount() <= 0) {
            line = Lang.tr("sc.gui.comb.empty");
        } else {
            int secs = (int) (tank.getFluidAmount() / rate / 20);
            String time = secs >= 60 ? Lang.tr("sc.gui.comb.min", secs / 60, secs % 60) : Lang.tr("sc.gui.comb.sec", secs);
            line = Lang.tr("sc.gui.comb.left", time, tank.getFluid().getLocalizedName(), (int) type.euPerMb(tank.getFluid().getFluid().getName()),
                    String.format(java.util.Locale.ROOT, "%.1f", rate));
        }
        smallFit(line, COMB_ENGINE_X - 4, COMB_RESERVE_Y + 7, COMB_TANK_X - COMB_ENGINE_X, GuiHoloSC.VALUE);
    }

    /** Small text (5/8, or smaller to fit maxW), foreground coordinates. */
    private void smallFit(String text, int x, int y, int maxW, int color) {
        float k = Math.min(0.625F, maxW / (float) Math.max(1, fontRendererObj.getStringWidth(text)));
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private boolean hasItemSlots() {
        for (int slot = 0; slot < TileEntityGeneratorSC.FIRST_UPGRADE_SLOT; slot++) {
            if (TileEntityGeneratorSC.usesSlot(type, slot)) {
                return true;
            }
        }
        return false;
    }

    private String tankLabel(int i) {
        if (i == 2) {
            return Lang.tr("sc.gui.holo.gen.tank.water");
        }
        if (type == GeneratorType.COMBUSTION || type.kind == GeneratorType.Kind.EXO) {
            return Lang.tr(type.kind == GeneratorType.Kind.EXO ? "sc.gui.holo.gen.tank.coolant" : "sc.gui.holo.gen.tank.fuel");
        }
        return fluidName(i == 0 ? type.fuelFluidName : type.fuel2FluidName);
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

    /** Status colours for the dark screen. */
    private static int statusColor(GeneratorStatus status) {
        switch (status) {
            case GENERATING: return GuiHoloSC.OK;
            case IGNITING: return 0x6EB4FF;
            case BUFFER_FULL:
            case WATER_FULL: return GuiHoloSC.WARN;
            case IDLE: return GuiHoloSC.IDLE;
            default: return GuiHoloSC.BAD;          // every "no ..." / depleted / overheated state
        }
    }

    private List<String> tooltipAt(int mouseX, int mouseY) {
        List<String> lines = new ArrayList<String>();

        for (Object o : buttonList) {
            if (o instanceof GuiBigSC.ClearButton && ((GuiBigSC.ClearButton) o).over(mouseX + guiLeft, mouseY + guiTop)) {
                int i = ((GuiBigSC.ClearButton) o).id - ContainerGeneratorSC.BTN_CLEAR;
                return GuiBigSC.clearTip(tank(i).getFluidAmount(), generator.clearCost(i), generator.getEnergyStored());
            }
        }

        List<String> powerTip = power.tooltip(mouseX, mouseY);
        if (powerTip != null) {
            return powerTip;
        }
        List<String> batteryTip = battery.tooltip(mouseX, mouseY, generator.getStackInSlot(TileEntityGeneratorSC.SLOT_BATTERY));
        if (batteryTip != null) {
            return batteryTip;
        }
        if (GuiGaugeSC.isOver(GuiBigSC.GAUGE_X, GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(generator.getEnergyStored() + " / " + generator.getMaxEnergyStored() + " EU");
            lines.add(Lang.tr("sc.gui.output", type == GeneratorType.CREATIVE ? generator.getCreativeTier().getVoltage() : generator.ratedOutput()));
            lines.add(Lang.tr("sc.gui.gen.packet", generator.outputTier().name(), generator.outputTier().getVoltage()));
            return lines;
        }

        for (int i = 0; i < tankCount(); i++) {
            if (GuiGaugeSC.isOver(tankX(i), comb || turb || preact || exo ? 40 : TANK_Y, GuiTankGaugeSC.WIDTH, GuiTankGaugeSC.HEIGHT, mouseX, mouseY)) {
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

        if (turb && GuiGaugeSC.isOver(14, TB_Y, 158, TB_H, mouseX, mouseY)) {
            lines.add(Lang.tr(tk("title")));
            lines.add(Lang.tr(tk("hint")));
            return lines;
        }
        if (solar && GuiGaugeSC.isOver(SOL_SKY_X, SOL_SKY_Y, SOL_SKY_W, SOL_SKY_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.sol.ladder"));
            lines.add(Lang.tr("sc.gui.sol.ladder.1"));
            lines.add(Lang.tr("sc.gui.sol.ladder.2"));
            return lines;
        }
        if (solar && GuiGaugeSC.isOver(SOL_SKY_X, SOL_STRIP_Y - 2, SOL_STRIP_W, 14, mouseX, mouseY)) {
            long time = dayTime();
            lines.add(Lang.tr("sc.gui.sol.strip"));
            lines.add(Lang.tr("sc.gui.sol.clock", (int) ((time / 1000 + 6) % 24), (int) (time % 1000 * 60 / 1000)));
            lines.add(Lang.tr("sc.gui.sol.strip.hint"));
            return lines;
        }
        if (comb && GuiGaugeSC.isOver(COMB_TABLE_X, COMB_TOP, COMB_TABLE_W, COMB_TABLE_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.comb.table.title"));
            lines.add(Lang.tr("sc.gui.comb.table.hint"));
            return lines;
        }
        if (preact && GuiGaugeSC.isOver(14, 34, 124, 38, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.pr.tip"));
            lines.add(Lang.tr("sc.gui.pr.tip.1"));
            lines.add(Lang.tr("sc.gui.pr.tip.2"));
            lines.add(Lang.tr("sc.gui.pr.tip.3"));
            return lines;
        }
        if (fcell && GuiGaugeSC.isOver(FC_X, FC_Y, FC_W, FC_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.fc.tip"));
            lines.add(Lang.tr("sc.gui.fc.tip.1"));
            lines.add(Lang.tr("sc.gui.fc.tip.2"));
            lines.add(Lang.tr("sc.gui.fc.tip.3"));
            return lines;
        }
        if (thermo && GuiGaugeSC.isOver(14, 34, 64, 58, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.th.tip"));
            lines.add(Lang.tr("sc.gui.th.tip.1"));
            lines.add(Lang.tr("sc.gui.th.tip.2"));
            lines.add(Lang.tr("sc.gui.th.tip.3"));
            return lines;
        }
        if (water && GuiGaugeSC.isOver(WW_X, WW_Y, WW_CELL * 3 + 4, WW_CELL * 3 + 4, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.ww.tip"));
            lines.add(Lang.tr("sc.gui.ww.tip.1"));
            lines.add(Lang.tr("sc.gui.ww.tip.2"));
            lines.add(Lang.tr("sc.gui.ww.tip.3"));
            return lines;
        }
        if (wind && GuiGaugeSC.isOver(WD_LAND_X, WD_LAND_Y, WD_CHIP_X[2] + WD_CHIP_W - WD_LAND_X, WD_LAND_H - 20, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.wd.tip"));
            lines.add(Lang.tr("sc.gui.wd.tip.1"));
            lines.add(Lang.tr("sc.gui.wd.tip.2"));
            lines.add(Lang.tr("sc.gui.wd.tip.3"));
            return lines;
        }
        if (solid && GuiGaugeSC.isOver(SF_TAB_X, SF_TAB_Y, SF_TAB_W, SF_TAB_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.sf.table"));
            lines.add(Lang.tr("sc.gui.sf.table.hint"));
            return lines;
        }
        if (fus) {
            if (GuiGaugeSC.isOver(14, FU_TOR_Y, FU_TOR_W, FU_TOR_H, mouseX, mouseY)
                    || generator.isIgnited() && GuiGaugeSC.isOver(FU_COL_X, FU_TOR_Y, 80, FU_TOR_H, mouseX, mouseY)) {
                lines.add(Lang.tr("sc.gui.gen.heat"));
                lines.add(Lang.tr("sc.gui.gen.plasma", generator.getHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT)
                        + "  (" + generator.getHeat() / 10 + "%)");
                lines.add(Lang.tr("sc.gui.gen.ramp", generator.getRamp() / 10));
                lines.add(Lang.tr("sc.gui.gen.heat.hint"));
                return lines;
            }
            if (!generator.isIgnited() && GuiGaugeSC.isOver(FU_COL_X, FU_TOR_Y, 80, FU_TOR_H, mouseX, mouseY)) {
                lines.add(Lang.tr("sc.gui.ignition"));
                lines.add(generator.getIgnitionEU() + " / " + type.ignitionThreshold() + " EU");
                lines.add(Lang.tr("sc.gui.fus.charge.hint"));
                return lines;
            }
            if (GuiGaugeSC.isOver(14, FU_STEP_Y, 160, 11, mouseX, mouseY)) {
                lines.add(Lang.tr("sc.gui.fus.steps"));
                lines.add(Lang.tr("sc.gui.fus.steps.hint"));
                return lines;
            }
        }
        if (exo) {
            if (GuiGaugeSC.isOver(14, 48, 64, 40, mouseX, mouseY)) {
                lines.add(Lang.tr("sc.gui.gen.heat"));
                lines.add(Lang.tr("sc.gui.gen.plasma", generator.getHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT)
                        + "  (" + generator.getHeat() / 10 + "%)");
                lines.add(Lang.tr("sc.gui.ex.tip.1"));
                lines.add(Lang.tr("sc.gui.ex.tip.2"));
                return lines;
            }
            if (!generator.isIgnited() && GuiGaugeSC.isOver(EX_BAR_X, EX_BAR_Y - 6, EX_BAR_W + 80, 14, mouseX, mouseY)) {
                lines.add(Lang.tr("sc.gui.ignition"));
                lines.add(generator.getIgnitionEU() + " / " + type.ignitionThreshold() + " EU");
                lines.add(Lang.tr("sc.gui.fus.charge.hint"));
                return lines;
            }
        }
        if (isReactor() && !fus && !exo) {
            if (!generator.isIgnited() && GuiGaugeSC.isOver(LEFT_X - 1, IGNITION_Y - 1, LEFT_W + 2, IGNITION_H + 2, mouseX, mouseY)) {
                lines.add(Lang.tr("sc.gui.ignition"));
                lines.add(generator.getIgnitionEU() + " / " + type.ignitionThreshold() + " EU");
                return lines;
            }
            if (GuiGaugeSC.isOver(LEFT_X - 1, HEAT_Y - 1, LEFT_W + 2, HEAT_H + 2, mouseX, mouseY)) {
                lines.add(Lang.tr("sc.gui.gen.heat"));
                lines.add(Lang.tr("sc.gui.gen.plasma", generator.getHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT)
                        + "  (" + generator.getHeat() / 10 + "%)");
                lines.add(Lang.tr("sc.gui.gen.ramp", generator.getRamp() / 10));
                lines.add(Lang.tr("sc.gui.gen.heat.hint"));
                return lines;
            }
        }

        if (upgrades() && GuiGaugeSC.isOver(GuiBigSC.UPG_LABEL_X, GuiBigSC.UPG_Y - 1, GuiBigSC.W - 16, 18, mouseX, mouseY)
                && !GuiBigSC.overUpgradeSlot(mouseX, mouseY, TileEntityGeneratorSC.UPGRADE_SLOTS)) {
            lines.add(Lang.tr("sc.gui.upgrades"));
            lines.add(Lang.tr("sc.gui.gen.upgrades.hint"));
            return lines;
        }

        // What belongs in each slot while it's still empty.
        for (int slot = 0; slot < TileEntityGeneratorSC.FIRST_UPGRADE_SLOT; slot++) {
            int sx = ContainerGeneratorSC.slotX(type, slot);
            if (TileEntityGeneratorSC.usesSlot(type, slot) && generator.getStackInSlot(slot) == null
                    && GuiGaugeSC.isOver(sx, ContainerGeneratorSC.slotY(type), 16, 16, mouseX, mouseY)) {
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

    private static String fluidName(String name) {
        net.minecraftforge.fluids.Fluid f = name == null ? null : net.minecraftforge.fluids.FluidRegistry.getFluid(name);
        return f == null ? String.valueOf(name) : f.getLocalizedName(new net.minecraftforge.fluids.FluidStack(f, 1));
    }
}
