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
        this.solar = type == GeneratorType.SOLAR_SI;
        xSize = GuiBigSC.W;
        ySize = GuiBigSC.H;
    }

    private GuiPowerSC power;

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        power = new GuiPowerSC(generator, ContainerGeneratorSC.BTN_POWER, ContainerGeneratorSC.BTN_REDSTONE);
        power.addButtons(buttonList, guiLeft, guiTop);
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
        return comb ? COMB_TANK_X : RIGHT_X + i * TANK_GAP;
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
                GuiHoloSC.slot(x + (slot == 0 ? ContainerGeneratorSC.SLOT_FUEL_X : ContainerGeneratorSC.SLOT_BLANKET_X),
                        y + ContainerGeneratorSC.SLOT_Y, false);
            }
        }
        if (isReactor()) {
            float charge = generator.isIgnited() ? 1f : (float) generator.getIgnitionEU() / type.ignitionThreshold();
            GuiHoloSC.bar(x + LEFT_X, y + IGNITION_Y, LEFT_W, IGNITION_H, charge, 14, 0xFF6EE6FF);
            GuiHoloSC.bar(x + LEFT_X, y + HEAT_Y, LEFT_W, HEAT_H, (float) generator.getHeat() / TileEntityGeneratorSC.HEAT_LIMIT,
                    14, 0xFFFF5A3C);
        }
        if (comb) {
            drawCombBackground(x, y, partialTicks);
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

        for (int i = 0; i < tankCount(); i++) {      // last: drawing a fluid leaves the blocks atlas bound
            FluidTank t = tank(i);
            GuiTankGaugeSC.draw(mc, x + tankX(i), y + (comb ? 40 : TANK_Y), t.getFluid(), t.getCapacity(), null, false);
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
            int tr = GuiBigSC.W - 8 - GuiBigSC.UPG_TEXT_X;
            fit(Lang.tr("sc.gui.big.upgrades.count", used, TileEntityGeneratorSC.UPGRADE_SLOTS), GuiBigSC.UPG_TEXT_X, GuiBigSC.UPG_Y, tr, 0x505864);
        }
    }

    // ---- the Silicon Solar Panel ----

    /** The time of day in ticks, 0 sunrise .. 12000 sunset .. 24000. */
    private long dayTime() {
        return mc.theWorld == null ? 0 : (mc.theWorld.getWorldTime() % 24000 + 24000) % 24000;
    }

    private boolean solarSky() {
        return generator.getStatus() != GeneratorStatus.NO_SUNLIGHT;
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
        GuiSceneSC.sky(x + SOL_SKY_X, y + SOL_SKY_Y, SOL_SKY_W, SOL_SKY_H, t, day, rain, arc, !solarSky());
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
            fontRendererObj.drawString(values[i], cx + (w - vw) / 2, 46, sky ? cols[i] : 0x465A6E);
            fontRendererObj.drawString(i < 2 ? "×" : "=", cx + w + 2, 42, GuiHoloSC.VALUE);
        }
        int now = sky ? Math.max(1, (int) Math.round(rated * f[0] * f[1])) : 0;
        fit(Lang.tr("sc.gui.sol.now", now), 80, 60, GuiBigSC.SCREEN_RIGHT - 82, GuiHoloSC.VALUE);
        GeneratorStatus status = generator.getStatus();
        String st = !sky ? Lang.tr("sc.gui.sol.nosky") : status == GeneratorStatus.BUFFER_FULL ? status.localized()
                : Lang.tr(day ? (rain ? "sc.gui.sol.dayrain" : "sc.gui.sol.dayclear") : (rain ? "sc.gui.sol.nightrain" : "sc.gui.sol.nightclear"));
        smallFit(st, 80, 71, GuiBigSC.SCREEN_RIGHT - 82, !sky ? GuiHoloSC.BAD : status == GeneratorStatus.BUFFER_FULL ? GuiHoloSC.WARN
                : day ? 0xFFDC64 : 0x8CAAFF);
        smallFit(Lang.tr("sc.gui.sol.dayout", rated), SOL_SKY_X, SOL_STRIP_Y + 13, 90, 0xE8C850);
        smallFit(Lang.tr("sc.gui.sol.nightout", Math.max(1, (int) Math.round(rated * 0.5))), SOL_SKY_X + SOL_STRIP_W / 2, SOL_STRIP_Y + 13, 90, 0x7896DC);
        long time = dayTime();
        long left = day ? Math.max(0, 12000 - time) : time >= 12000 ? 24000 - time : 0;
        int min = (int) Math.max(1, (left / 20 + 59) / 60);
        smallFit(Lang.tr(day ? "sc.gui.sol.tosunset" : "sc.gui.sol.tosunrise", min) + " · "
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
        if (GuiGaugeSC.isOver(GuiBigSC.GAUGE_X, GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(generator.getEnergyStored() + " / " + generator.getMaxEnergyStored() + " EU");
            lines.add(Lang.tr("sc.gui.output", type == GeneratorType.CREATIVE ? generator.getCreativeTier().getVoltage() : generator.ratedOutput()));
            lines.add(Lang.tr("sc.gui.gen.packet", generator.outputTier().name(), generator.outputTier().getVoltage()));
            return lines;
        }

        for (int i = 0; i < tankCount(); i++) {
            if (GuiGaugeSC.isOver(tankX(i), comb ? 40 : TANK_Y, GuiTankGaugeSC.WIDTH, GuiTankGaugeSC.HEIGHT, mouseX, mouseY)) {
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
        if (isReactor()) {
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

    private static String fluidName(String name) {
        net.minecraftforge.fluids.Fluid f = name == null ? null : net.minecraftforge.fluids.FluidRegistry.getFluid(name);
        return f == null ? String.valueOf(name) : f.getLocalizedName(new net.minecraftforge.fluids.FluidStack(f, 1));
    }
}
