package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.energy.GeneratorStatus;
import com.sc.energy.GeneratorType;
import com.sc.energy.Tier;
import com.sc.machine.UpgradeType;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityGeneratorSC;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * The Tokamak XV's own window (400 x 264): one wide screen in three columns - the plasma (torus,
 * thermometer, stability / heat / power, the last minute's stability) or, before lighting, a
 * readiness checklist; the build (the 7x7 by layer, ports labelled by what they hold); the port
 * tanks (the mod's tank gauges, sized by the tanks in the wall, a cross where a gas has none) and
 * the port storages - with up to three status lines under them. The energy gauge beside it shows
 * the port storages' charge (the reactor itself keeps nothing).
 */
public class GuiTokamakXVSC extends GuiContainer {

    private static final int W = ContainerGeneratorSC.XV_W, H = ContainerGeneratorSC.XV_H;
    private static final int SX = ContainerGeneratorSC.XV_SCREEN_X, SY = ContainerGeneratorSC.XV_SCREEN_Y,
            SW = ContainerGeneratorSC.XV_SCREEN_W, SH = ContainerGeneratorSC.XV_SCREEN_H;
    private static final int GX = ContainerGeneratorSC.XV_GAUGE_X, GY = ContainerGeneratorSC.XV_GAUGE_Y, GH = ContainerGeneratorSC.XV_GAUGE_H;
    /** Columns: the plasma, the build, the ports. */
    private static final int C1 = 11, C2 = 115, C3 = 227, COL_Y = 35;
    private static final int TORUS_Y = 41, TORUS_W = 80, TORUS_H = 32, THERMO_X = 96, THERMO_Y = 35, THERMO_H = 82;
    private static final int GRAPH_Y = 111, GRAPH_H = 9;
    private static final int CELL = 9, SCHEME_Y = 42, LAYER_X = 182;
    private static final int TANK_Y = 48, TANK_STEP = 34, STATUS_Y = 145;
    private static final int LAYER_ID = 900;
    private static final String[] GAS = {"liquidhelium", "hydrogen", "argon", "deuterium"};
    private static final String[] GAS_SHORT = {"He", "H2", "Ar", "D"};
    private static final String[] LABEL_SHORT = {"", "He", "H2", "Ar", "D", "E", "?", "*"};
    private static final int PANEL = 0xFFB9C1CC, TITLE_BAR = 0xFF2E3642;

    private final TileEntityGeneratorSC gen;
    private GuiPowerSC power;
    private GuiBatterySlotSC battery;
    /** The layer the scheme shows: 0 the floor, 1 the middle, 2 the cap. */
    private int layer = 1;

    public GuiTokamakXVSC(InventoryPlayer inv, TileEntityGeneratorSC gen) {
        super(new ContainerGeneratorSC(inv, gen));
        this.gen = gen;
        xSize = W;
        ySize = H;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        buttonList.clear();
        power = new GuiPowerSC(gen, ContainerGeneratorSC.BTN_POWER, ContainerGeneratorSC.BTN_REDSTONE, GX, ContainerGeneratorSC.XV_BUTTONS_Y,
                GuiBatterySlotSC.COLUMN_W, GY, GH);
        power.addButtons(buttonList, guiLeft, guiTop);
        battery = new GuiBatterySlotSC(gen, ContainerGeneratorSC.BTN_BATTERY_MODE, GX, GY + GH, true);
        battery.addButton(buttonList, guiLeft, guiTop);
        buttonList.add(new GuiFieldGeneratorSC.HoloButton(ContainerGeneratorSC.BTN_SOFT_STOP, guiLeft + C2, guiTop + 127, 106, 11, ""));
        String[] names = {Lang.tr("sc.gui.xv.layer.0"), Lang.tr("sc.gui.xv.layer.1"), Lang.tr("sc.gui.xv.layer.2")};
        for (int i = 0; i < 3; i++) {                    // top to bottom: the cap, the middle, the floor
            buttonList.add(new GuiFieldGeneratorSC.HoloButton(LAYER_ID + 2 - i, guiLeft + LAYER_X, guiTop + SCHEME_Y + i * 12, 38, 10, names[2 - i]));
        }
        refreshButtons();
    }

    private void refreshButtons() {
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id == ContainerGeneratorSC.BTN_SOFT_STOP) {
                boolean latched = !gen.isIgnited() && gen.getBigEvent() != 0;
                b.enabled = gen.isIgnited() || latched;
                b.displayString = Lang.tr(latched ? "sc.gui.big.btn.allow" : gen.isIgnited() ? "sc.gui.big.btn.stop"
                        : ready() ? "sc.gui.xv.btn.auto" : "sc.gui.xv.btn.cant");
            } else if (b.id >= LAYER_ID && b.id < LAYER_ID + 3) {
                b.enabled = b.id - LAYER_ID != layer;
            }
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        refreshButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= LAYER_ID && button.id < LAYER_ID + 3) {      // screen-side only
            layer = button.id - LAYER_ID;
            refreshButtons();
            return;
        }
        if (!power.allowClick(button)) {
            return;
        }
        mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
    }

    // ------------------------------------------------------------------ the state

    private int[] scan() {
        return gen.bigScan();                   // coils, walls, ports, caps missing, tanks, storages, weak storages
    }

    private int coils() {
        return Integer.bitCount(scan()[0]);
    }

    private int walls() {
        return Integer.bitCount(scan()[1]);
    }

    private boolean hasBlanket() {
        return gen.getStackInSlot(TileEntityGeneratorSC.SLOT_BLANKET) != null;
    }

    private int cells() {
        ItemStack c = gen.getStackInSlot(TileEntityGeneratorSC.SLOT_FUEL);
        return c == null ? 0 : c.stackSize;
    }

    private boolean hasDeuterium() {
        return gen.getPortFluid(3) > 0 || cells() > 0;
    }

    /** Everything but the charge is there: it lights by itself once the charge is in. */
    private boolean ready() {
        return gen.isBigReady() && gen.getPortFluid(0) >= TileEntityGeneratorSC.BIG_HE_START && hasBlanket() && hasDeuterium()
                && gen.getBigEvent() == 0;
    }

    private int normHeat() {
        return 600 + 100 * gen.upgradeCount(UpgradeType.OVERDRIVE) + (gen.isShielded() ? TileEntityGeneratorSC.SHIELDED_HEAT : 0);
    }

    /** Seconds a port gas lasts at the running rate (argon: how many soft stops). */
    private double lasts(int gas) {
        int mb = gen.getPortFluid(gas);
        switch (gas) {
            case 0: return mb / TileEntityGeneratorSC.BIG_HE_PER_TICK / 20.0;
            case 1: return mb / TileEntityGeneratorSC.BIG_H2_PER_TICK / 20.0;
            case 3: return mb / (TileEntityGeneratorSC.BIG_D_PER_TICK * Math.max(0.01, gen.fuelMultiplier())) / 20.0;
            default: return mb / (double) TileEntityGeneratorSC.BIG_ARGON_STOP;
        }
    }

    private static String time(double s) {
        long t = (long) Math.ceil(s);
        if (t >= 3600) {
            return Lang.tr("sc.gui.fus.h", t / 3600);
        }
        return t >= 60 ? Lang.tr("sc.gui.fus.min", t / 60) : Lang.tr("sc.gui.fus.sec", t);
    }

    private static String k(int mb) {
        return mb >= 1000 ? (mb / 1000) + "k" : String.valueOf(mb);
    }

    private static String eu(long v) {
        if (v >= 1000000) {
            double m = v / 1000000.0;
            return Lang.tr("sc.gui.fus.mln", m == Math.floor(m) ? String.valueOf((long) m)
                    : String.format(java.util.Locale.ROOT, "%.1f", m).replace('.', ','));
        }
        return Lang.tr("sc.gui.fus.k", v / 1000);
    }

    /** World coordinates of the first missing coil, or null. */
    private int[] missingCoil() {
        int mask = scan()[0], ci = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if ((mask >> ci++ & 1) == 0) {
                    return new int[]{gen.xCoord + dx, gen.yCoord, gen.zCoord + dz};
                }
            }
        }
        return null;
    }

    private static int wallIndex(int dx, int dz) {
        int k = 0;
        for (int z = -3; z <= 3; z++) {
            for (int x = -3; x <= 3; x++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                    if (x == dx && z == dz) {
                        return k;
                    }
                    k++;
                }
            }
        }
        return -1;
    }

    private static int coilIndex(int dx, int dz) {
        int k = 0;
        for (int z = -2; z <= 2; z++) {
            for (int x = -2; x <= 2; x++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                if (x == dx && z == dz) {
                    return k;
                }
                k++;
            }
        }
        return -1;
    }

    /** A cell of the scheme: 0 missing, 1 lead, 2 coil, 3 the reactor, 4 a port (then its label). */
    private int cellKind(int dx, int dz) {
        if (layer != 1) {
            return gen.capAt(layer == 2, dx, dz) ? 1 : 0;
        }
        if (dx == 0 && dz == 0) {
            return 3;
        }
        if (Math.abs(dx) < 3 && Math.abs(dz) < 3) {
            return (scan()[0] >> coilIndex(dx, dz) & 1) != 0 ? 2 : 0;
        }
        int w = wallIndex(dx, dz);
        if ((scan()[2] >> w & 1) != 0) {
            return 4;
        }
        return (scan()[1] >> w & 1) != 0 ? 1 : 0;
    }

    private int capCount(boolean top) {
        int n = 0;
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                n += gen.capAt(top, dx, dz) ? 1 : 0;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ background

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        window(x, y);
        GuiHoloSC.screen(x + SX, y + SY, SW, SH);
        for (int s = 0; s < 2; s++) {
            GuiHoloSC.slot(x + ContainerGeneratorSC.slotX(GeneratorType.TOKAMAK_XV, s), y + ContainerGeneratorSC.XV_SLOT_Y, false);
        }
        float t = mc.theWorld == null ? 0F : (mc.theWorld.getTotalWorldTime() % 1000000L) + partialTicks;
        if (gen.isIgnited()) {
            GuiSceneSC.fusionTorus(x + C1, y + TORUS_Y, TORUS_W, TORUS_H, t, (float) gen.getHeat() / TileEntityGeneratorSC.HEAT_LIMIT, true);
            thermometer(x + THERMO_X, y + THERMO_Y);
            float st = gen.getStability() / 100F;
            GuiHoloSC.bar(x + C1, y + 82, TORUS_W, 3, st, 16, st > 0.4F ? 0xFF5AE66E : st > 0.2F ? 0xFFFF9628 : 0xFFE63C3C);
            GuiHoloSC.bar(x + C1, y + 94, TORUS_W, 3, (float) gen.getHeat() / TileEntityGeneratorSC.HEAT_LIMIT, 16,
                    gen.getHeat() > normHeat() ? 0xFFFF9628 : 0xFFB070F0);
            GuiHoloSC.bar(x + C1, y + 106, TORUS_W, 3, (float) gen.getRamp() / TileEntityGeneratorSC.RAMP_FULL, 16, GuiHoloSC.CYAN);
            graph(x + C1, y + GRAPH_Y);
        } else {
            checklistIcons(x, y);
            float charge = Math.min(1F, (float) gen.getIgnitionEU() / Math.max(1L, gen.ignitionNeed()));
            GuiHoloSC.bar(x + C1 + 7, y + 104, 90, 3, charge, 18, GuiHoloSC.CYAN);
        }
        slotBars(x, y);
        scheme(x + C2, y + SCHEME_Y);
        for (int i = 0; i < 4; i++) {
            tank(x + C3 + i * TANK_STEP, y + TANK_Y, i);
        }
        for (int i = 0; i < Math.min(2, scan()[5]); i++) {
            int bx = x + C3 + i * 67, by = y + 137;
            Gui.drawRect(bx, by, bx + 63, by + 2, 0xFF04080C);
            Gui.drawRect(bx, by, bx + 63 * gen.getStorePct(i) / 100, by + 2, 0xFFE6C850);
        }
        Gui.drawRect(x + SX + 3, y + STATUS_Y - 3, x + SX + SW - 3, y + STATUS_Y - 2, 0xFF1E3444);
        GuiEnergyGaugeSC.draw(x + GX, y + GY, GuiBatterySlotSC.COLUMN_W, GH, gen.getStoresLevel());
        battery.draw(x, y, gen.getStackInSlot(TileEntityGeneratorSC.SLOT_BATTERY));
        GuiHoloSC.glint(x + SX, y + SY, SW, SH);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The panel, its title bar, the divider and the pockets (GuiBigSC's look, this window's size). */
    private void window(int x, int y) {
        rect(x, y, W, H, 0xFF1E2024);
        rect(x + 1, y + 1, W - 2, H - 2, PANEL);
        rect(x + 1, y + 1, W - 3, 1, 0xFFECF0F6);
        rect(x + 1, y + 1, 1, H - 3, 0xFFE2E8F0);
        rect(x + 2, y + H - 2, W - 3, 1, 0xFF6E747E);
        rect(x + W - 2, y + 2, 1, H - 3, 0xFF767C86);
        rect(x + 3, y + 3, W - 6, 1, 0xFF6E747E);
        rect(x + 3, y + 3, 1, 13, 0xFF6E747E);
        rect(x + 4, y + 4, W - 8, 11, TITLE_BAR);
        rect(x + 4, y + 15, W - 8, 1, 0xFFF0F2F6);
        int sep = ContainerGeneratorSC.XV_SEPARATOR_Y;
        rect(x + 7, y + sep, W - 14, 1, 0xFF5A606A);
        rect(x + 7, y + sep + 1, W - 14, 1, 0xFFF0F2F6);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                GuiBigSC.pocket(x + ContainerGeneratorSC.XV_INV_X + c * 18, y + ContainerGeneratorSC.XV_INV_Y + r * 18);
            }
        }
        for (int c = 0; c < 9; c++) {
            GuiBigSC.pocket(x + ContainerGeneratorSC.XV_INV_X + c * 18, y + ContainerGeneratorSC.XV_HOTBAR_Y);
        }
        for (int i = 0; i < TileEntityGeneratorSC.UPGRADE_SLOTS; i++) {
            GuiBigSC.pocket(x + ContainerGeneratorSC.XV_UPG_X + i * 18, y + ContainerGeneratorSC.XV_UPG_Y);
        }
    }

    /** The plasma thermometer: zones, the column, the norm (green) and the limit (red, the top). */
    private void thermometer(int x, int y) {
        int w = 7, h = THERMO_H;
        rect(x, y, w, h, 0xFF1E3444);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF04080C);
        int in = h - 4, bottom = y + h - 2;
        float norm = Math.min(1F, normHeat() / (float) TileEntityGeneratorSC.HEAT_LIMIT);
        float heat = Math.min(1F, gen.getHeat() / (float) TileEntityGeneratorSC.HEAT_LIMIT);
        for (int i = 0; i < in; i += 2) {
            float k = i / (float) in;
            int col = k < heat ? (k > 0.85F ? 0xFFE63C3C : GuiSceneSC.mix(0xFF7A3AC8, 0xFFFFFFFF, k))
                    : k < norm ? 0xFF14281C : k < 0.9F ? 0xFF2A2410 : 0xFF2E1010;
            rect(x + 2, bottom - i - 1, w - 4, 1, col);
        }
        int ny = bottom - Math.round(in * norm);
        rect(x - 2, ny, w + 4, 1, 0xFF5AE66E);
        rect(x - 2, y + 1, w + 4, 1, 0xFFE63C3C);
        int hy = bottom - Math.round(in * heat);
        rect(x + w + 1, hy - 1, 1, 3, 0xFFE6F0FA);
        rect(x + w + 2, hy, 1, 1, 0xFFE6F0FA);
    }

    /** Stability over the last minute, a dot a second; the 40% line. */
    private void graph(int x, int y) {
        rect(x, y, TORUS_W, GRAPH_H, 0xFF1E3444);
        rect(x + 1, y + 1, TORUS_W - 2, GRAPH_H - 2, 0xFF04080C);
        int line = y + GRAPH_H - 2 - Math.round((GRAPH_H - 3) * 0.4F);
        rect(x + 1, line, TORUS_W - 2, 1, 0xFF3A300C);
        for (int k = 0; k < 60; k++) {
            int v = gen.stabilityAgo(k);
            if (v < 0) {
                continue;
            }
            int px = x + TORUS_W - 2 - k * (TORUS_W - 3) / 60;
            int py = y + GRAPH_H - 2 - Math.round((GRAPH_H - 3) * v / 100F);
            rect(px, py, 1, 1, v > 40 ? 0xFF5AE66E : v > 20 ? 0xFFFF9628 : 0xFFE63C3C);
        }
    }

    private void checklistIcons(int x, int y) {
        int[] st = checklistStates();
        for (int i = 0; i < st.length; i++) {
            int ix = x + C1, iy = y + COL_Y + 8 + i * 9;
            if (st[i] == 1) {                                  // a tick
                rect(ix, iy + 2, 1, 2, 0xFF5AE66E);
                rect(ix + 1, iy + 3, 1, 2, 0xFF5AE66E);
                rect(ix + 2, iy + 2, 1, 2, 0xFF5AE66E);
                rect(ix + 3, iy + 1, 1, 2, 0xFF5AE66E);
                rect(ix + 4, iy, 1, 2, 0xFF5AE66E);
            } else if (st[i] == 0) {                           // a cross
                for (int k = 0; k < 5; k++) {
                    rect(ix + k, iy + k, 1, 1, 0xFFE63C3C);
                    rect(ix + 4 - k, iy + k, 1, 1, 0xFFE63C3C);
                }
            } else {                                           // in progress
                rect(ix, iy, 5, 5, 0xFF3CAADC);
                rect(ix + 1, iy + 1, 3, 3, 0xFF04080C);
                rect(ix + 2, iy + 1, 2, 2, 0xFF6EE6FF);
            }
        }
    }

    /** The checklist: 1 done, 0 missing, 2 in progress. */
    private int[] checklistStates() {
        int[] s = scan();
        boolean charged = gen.getIgnitionEU() >= gen.ignitionNeed();
        return new int[]{walls() == 24 && s[3] == 0 ? 1 : 0, coils() == 24 ? 1 : 0, s[5] > 0 && s[6] == 0 ? 1 : 0,
                gen.getPortFluid(0) >= TileEntityGeneratorSC.BIG_HE_START ? 1 : 0, hasBlanket() ? 1 : 0, hasDeuterium() ? 1 : 0,
                charged ? 1 : 2};
    }

    private void slotBars(int x, int y) {
        float cellF = (float) Math.min(1.0, gen.getCellBurnRemaining() / TileEntityGeneratorSC.CELL_BURN_TICKS);
        if (cellF <= 0 && cells() > 0) {
            cellF = 1F;
        }
        float blankF = gen.isIgnited() ? (float) gen.getModuleLife() / TileEntityGeneratorSC.MODULE_LIFE_TICKS : hasBlanket() ? 1F : 0F;
        int by = y + ContainerGeneratorSC.XV_SLOT_Y + 13;
        int[] xs = {x + ContainerGeneratorSC.XV_FUEL_X + 19, x + ContainerGeneratorSC.XV_BLANKET_X + 19};
        float[] f = {cellF, blankF};
        for (int s = 0; s < 2; s++) {
            int w = s == 0 ? 24 : 30;
            rect(xs[s], by, w, 3, 0xFF04080C);
            rect(xs[s] + 1, by + 1, Math.round((w - 2) * f[s]), 1, s == 0 ? 0xFF9AD8FF : 0xFFC8D87A);
        }
    }

    /** The 7x7 of the chosen layer: lead grey, coils copper, the reactor violet, ports teal with their gas, missing red. */
    private void scheme(int x, int y) {
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                int cx = x + (dx + 3) * CELL, cy = y + (dz + 3) * CELL, kind = cellKind(dx, dz);
                int col = kind == 0 ? 0xFFE63C3C : kind == 1 ? 0xFF8A909A : kind == 2 ? 0xFFD08040
                        : kind == 3 ? (gen.isIgnited() ? 0xFFE060F8 : 0xFF9050C8) : 0xFF287874;
                rect(cx, cy, CELL - 1, CELL - 1, col);
                if (kind == 4) {
                    int lab = gen.getPortLabel(wallIndex(dx, dz));
                    int edge = lab >= 1 && lab <= 4 ? GuiTankGaugeSC.colourOf(fluid(lab - 1)) : lab == 5 ? 0xFFE6C850 : 0xFF5A6E78;
                    rect(cx, cy, CELL - 1, 1, edge);
                    rect(cx, cy + CELL - 2, CELL - 1, 1, edge);
                    rect(cx, cy, 1, CELL - 1, edge);
                    rect(cx + CELL - 2, cy, 1, CELL - 1, edge);
                }
            }
        }
    }

    private static Fluid fluid(int gas) {
        Fluid f = FluidRegistry.getFluid(GAS[gas]);
        return f != null ? f : FluidRegistry.WATER;
    }

    /** A port gas's gauge: the tank gauge sized by its tanks; remembered but empty - pinned (blue); none - a cross. */
    private void tank(int x, int y, int gas) {
        int cap = gen.getPortCap(gas), mb = gen.getPortFluid(gas);
        Fluid f = FluidRegistry.getFluid(GAS[gas]);
        FluidStack st = f != null && mb > 0 ? new FluidStack(f, mb) : null;
        GuiTankGaugeSC.draw(mc, x, y, st, Math.max(1, cap), cap > 0 && f != null ? GAS[gas] : null, false);
        if (cap > 0) {
            return;
        }
        rect(x + 20, y + 4, 2, 2, 0xFF3C3C3C);                // no tank: the lamp off, a cross, dashes
        int c = gas == 0 ? 0xFFE63C3C : 0xFF8C96A0;
        for (int i = 0; i < 44; i++) {
            int px = 5 + i * 8 / 44;
            rect(x + px, y + 12 + i, 2, 1, c);
            rect(x + 12 - i * 8 / 44, y + 12 + i, 2, 1, c);
        }
        rect(x + 3, y + 63, 20, 7, 0xFF12141A);
        for (int d = 0; d < 3; d++) {
            rect(x + 6 + d * 4, y + 66, 3, 1, 0xFF787E88);
        }
    }

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            Gui.drawRect(x, y, x + w, y + h, c);
        }
    }

    // ------------------------------------------------------------------ foreground

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fit(GeneratorType.TOKAMAK_XV.localizedName(), 8, 5, W - 60, GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, gen.outputTier(), W - 6, 3);
        header();
        if (gen.isIgnited()) {
            plasmaText();
        } else {
            checklistText();
        }
        slotText();
        buildText();
        portsText();
        statusText();
        fontRendererObj.drawString(Lang.tr("sc.gui.big.upgrades"), ContainerGeneratorSC.XV_UPG_X, ContainerGeneratorSC.XV_UPG_Y - 11, 0x404040);
        fontRendererObj.drawString(Lang.tr("container.inventory"), ContainerGeneratorSC.XV_INV_X, ContainerGeneratorSC.XV_INV_Y - 10, 0x404040);
        int used = 0;
        for (int i = 0; i < TileEntityGeneratorSC.UPGRADE_SLOTS; i++) {
            used += gen.getStackInSlot(TileEntityGeneratorSC.FIRST_UPGRADE_SLOT + i) != null ? 1 : 0;
        }
        small(Lang.tr("sc.gui.big.upgrades.count", used, TileEntityGeneratorSC.UPGRADE_SLOTS), ContainerGeneratorSC.XV_UPG_X,
                ContainerGeneratorSC.XV_UPG_Y + 21, 100, 0x505864);
        power.drawGaugeOff(fontRendererObj);
    }

    private void header() {
        int y = 24;
        fit(Lang.tr("sc.gui.big.title"), C1, y, 70, GuiHoloSC.CYAN & 0xFFFFFF);
        String chip;
        int col;
        GeneratorStatus s = gen.getStatus();
        if (s == GeneratorStatus.DISABLED || s == GeneratorStatus.REDSTONE) {
            chip = "off";
            col = GuiHoloSC.IDLE;
        } else if (gen.isIgnited()) {
            chip = "run";
            col = GuiHoloSC.OK;
        } else if (gen.getBigEvent() == TileEntityGeneratorSC.EVENT_BROKE) {
            chip = "broke";
            col = GuiHoloSC.BAD;
        } else if (gen.getBigEvent() == TileEntityGeneratorSC.EVENT_SOFT) {
            chip = "soft";
            col = GuiHoloSC.WARN;
        } else {
            chip = "prep";
            col = GuiHoloSC.CYAN & 0xFFFFFF;
        }
        String text = Lang.tr("sc.gui.xv.state." + chip);
        int tw = (int) (fontRendererObj.getStringWidth(text) * 0.625F) + 6;
        int cx = 84;
        rect(cx, y - 1, tw, 8, 0xFF000000 | col);
        rect(cx + 1, y, tw - 2, 6, 0xFF06101A);
        small(text, cx + 3, y + 1, tw - 4, col);
        small(gen.isIgnited() ? Lang.tr("sc.gui.xv.out", gen.getLastOutput()) : Lang.tr("sc.gui.xv.notlit"), cx + tw + 5, y + 1, 90,
                gen.isIgnited() ? GuiHoloSC.OK : GuiHoloSC.IDLE);
        float rad = gen.radiationLevel();
        String r = rad > 0 ? Lang.tr("sc.gui.big.rad", com.sc.radiation.RadiationSC.fmt(rad), gen.radiationRadiusNow())
                : Lang.tr("sc.gui.xv.norad");
        int rw = (int) (fontRendererObj.getStringWidth(r) * 0.625F);
        small(r, SX + SW - 4 - Math.min(110, rw), y + 1, 110, rad > 0 ? GuiHoloSC.WARN : GuiHoloSC.IDLE);
    }

    private void plasmaText() {
        small(Lang.tr("sc.gui.xv.plasma"), C1, COL_Y, 80, GuiHoloSC.LABEL);
        int temp = gen.getHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT;
        int hy = THERMO_Y + THERMO_H - 2 - Math.round((THERMO_H - 4) * Math.min(1F, gen.getHeat() / (float) TileEntityGeneratorSC.HEAT_LIMIT));
        tiny(String.valueOf(temp), THERMO_X + 10, hy - 2, 12, gen.getHeat() > normHeat() ? GuiHoloSC.WARN : GuiHoloSC.VALUE);
        float st = gen.getStability();
        row(Lang.tr("sc.gui.xv.stab"), Math.round(st) + "%", 76, st > 40 ? GuiHoloSC.OK : st > 20 ? GuiHoloSC.WARN : GuiHoloSC.BAD);
        row(Lang.tr("sc.gui.xv.heat"), Lang.tr("sc.gui.xv.mk", temp), 88, gen.getHeat() > normHeat() ? GuiHoloSC.WARN : GuiHoloSC.VALUE);
        row(Lang.tr("sc.gui.fus.power"), gen.getRamp() / 10 + "%", 100, GuiHoloSC.VALUE);
    }

    private void row(String name, String value, int y, int col) {
        small(name, C1, y, 48, GuiHoloSC.LABEL);
        int vw = (int) (fontRendererObj.getStringWidth(value) * 0.625F);
        small(value, C1 + TORUS_W - Math.min(32, vw), y, 32, col);
    }

    private void checklistText() {
        small(Lang.tr("sc.gui.xv.check"), C1, COL_Y, 96, GuiHoloSC.LABEL);
        int[] s = scan(), st = checklistStates();
        int[] miss = missingCoil();
        String[] lines = {
            st[0] == 1 ? Lang.tr("sc.gui.xv.c.shell") : Lang.tr("sc.gui.xv.c.shell.no", 24 - walls() + s[3]),
            st[1] == 1 ? Lang.tr("sc.gui.xv.c.coils") : Lang.tr("sc.gui.xv.c.coils.no", coils(), miss == null ? "" : miss[0] + " " + miss[1] + " " + miss[2]),
            s[5] == 0 ? Lang.tr("sc.gui.xv.c.store.no") : s[6] > 0 ? Lang.tr("sc.gui.xv.c.store.weak") : Lang.tr("sc.gui.xv.c.store", s[5]),
            st[3] == 1 ? Lang.tr("sc.gui.xv.c.he", k(gen.getPortFluid(0))) : Lang.tr("sc.gui.xv.c.he.no", TileEntityGeneratorSC.BIG_HE_START),
            Lang.tr(hasBlanket() ? "sc.gui.xv.c.blanket" : "sc.gui.xv.c.blanket.no"),
            st[5] == 1 ? Lang.tr("sc.gui.xv.c.d", k(gen.getPortFluid(3)), cells()) : Lang.tr("sc.gui.xv.c.d.no"),
            Lang.tr("sc.gui.xv.c.charge", eu(gen.getIgnitionEU()), eu(gen.ignitionNeed()))};
        for (int i = 0; i < lines.length; i++) {
            small(lines[i], C1 + 7, COL_Y + 8 + i * 9, 94, st[i] == 0 ? GuiHoloSC.BAD : st[i] == 2 ? GuiHoloSC.CYAN & 0xFFFFFF : GuiHoloSC.VALUE);
        }
        boolean h2 = gen.getPortFluid(1) >= TileEntityGeneratorSC.BIG_H2_START;
        tiny(Lang.tr(h2 ? "sc.gui.xv.h2.yes" : "sc.gui.xv.h2.no", TileEntityGeneratorSC.BIG_H2_START), C1 + 7, 109, 96,
                h2 ? GuiHoloSC.OK : GuiHoloSC.LABEL);
        long need = gen.ignitionNeed() - gen.getIgnitionEU();
        if (need > 0 && gen.isBigReady()) {
            tiny(Lang.tr("sc.gui.xv.fromstores", time(need / (double) TileEntityGeneratorSC.PORT_CHARGE_PER_TICK / 20.0)), C1 + 7, 114, 96,
                    GuiHoloSC.LABEL);
        }
    }

    private void slotText() {
        int y = ContainerGeneratorSC.XV_SLOT_Y + 1, cx = ContainerGeneratorSC.XV_FUEL_X + 19, bx = ContainerGeneratorSC.XV_BLANKET_X + 19;
        int n = cells();
        tiny(Lang.tr("sc.gui.xv.cells", n), cx, y, 26, n > 0 ? GuiHoloSC.VALUE : GuiHoloSC.IDLE);
        boolean tankD = gen.getPortFluid(3) > 0;
        tiny(Lang.tr(tankD ? "sc.gui.xv.cells.spare" : n > 0 ? "sc.gui.xv.cells.used" : "sc.gui.xv.cells.none"), cx, y + 6, 26,
                tankD ? GuiHoloSC.IDLE : n > 0 ? GuiHoloSC.WARN : GuiHoloSC.IDLE);
        tiny(Lang.tr("sc.gui.xv.blanket"), bx, y, 32, GuiHoloSC.LABEL);
        String life = gen.isIgnited() ? time(gen.getModuleLife() / 20.0 / TileEntityGeneratorSC.BIG_BLANKET_WEAR)
                : Lang.tr(hasBlanket() ? "sc.gui.xv.blanket.in" : "sc.gui.xv.blanket.need");
        tiny(life, bx, y + 6, 32, hasBlanket() || gen.isIgnited() ? GuiHoloSC.VALUE : GuiHoloSC.BAD);
    }

    private void buildText() {
        small(Lang.tr("sc.gui.xv.build"), C2, COL_Y, 66, GuiHoloSC.LABEL);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (cellKind(dx, dz) == 4) {
                    String l = LABEL_SHORT[gen.getPortLabel(wallIndex(dx, dz))];
                    int lw = (int) (fontRendererObj.getStringWidth(l) * 0.5F);
                    tiny(l, C2 + (dx + 3) * CELL + (CELL - 1 - lw) / 2, SCHEME_Y + (dz + 3) * CELL + 2, CELL, GuiHoloSC.VALUE);
                }
            }
        }
        int c = coils(), w = walls(), top = capCount(true), bottom = capCount(false);
        tiny(Lang.tr("sc.gui.xv.count1", c, w), C2, 109, 106, c == 24 && w == 24 ? GuiHoloSC.OK : GuiHoloSC.WARN);
        tiny(Lang.tr("sc.gui.xv.count2", top, bottom), C2, 115, 106, top == 49 && bottom == 49 ? GuiHoloSC.OK : GuiHoloSC.WARN);
        int[] cols = {0xD08040, 0x8A909A, 0x287874, 0xE63C3C};
        String[] names = {"sc.gui.xv.lg.coil", "sc.gui.xv.lg.lead", "sc.gui.xv.lg.port", "sc.gui.xv.lg.miss"};
        for (int i = 0; i < 4; i++) {
            int lx = C2 + i * 27;
            rect(lx, 121, 3, 3, 0xFF000000 | cols[i]);
            tiny(Lang.tr(names[i]), lx + 5, 121, 21, GuiHoloSC.LABEL);
        }
    }

    private void portsText() {
        small(Lang.tr("sc.gui.xv.ports"), C3, COL_Y, 70, GuiHoloSC.LABEL);
        if (gen.getFreeTanks() > 0) {
            tiny(Lang.tr("sc.gui.xv.free", gen.getFreeTanks()), C3 + 72, COL_Y + 1, 60, GuiHoloSC.LABEL);
        }
        for (int i = 0; i < 4; i++) {
            int gx = C3 + i * TANK_STEP, cap = gen.getPortCap(i), mb = gen.getPortFluid(i);
            int nw = (int) (fontRendererObj.getStringWidth(GAS_SHORT[i]) * 0.625F);
            small(GAS_SHORT[i], gx + 12 - nw / 2, 42, 20, GuiHoloSC.VALUE);
            if (cap <= 0) {
                tiny(Lang.tr("sc.gui.xv.notank"), gx, 119, 31, i == 0 ? GuiHoloSC.BAD : GuiHoloSC.IDLE);
                continue;
            }
            tiny(k(mb) + "/" + k(cap), gx, 119, 31, GuiHoloSC.VALUE);
            String left;
            int col = GuiHoloSC.LABEL;
            if (mb <= 0) {
                left = Lang.tr("sc.gui.xv.empty");
                col = GuiHoloSC.BAD;
            } else if (i == 2) {
                left = Lang.tr("sc.gui.xv.stops", (int) lasts(2));
            } else {
                double s = lasts(i);
                left = "~" + time(s);
                col = i == 0 && s < 60 ? GuiHoloSC.WARN : GuiHoloSC.LABEL;
            }
            tiny(left, gx, 125, 31, col);
        }
        int stores = scan()[5];
        if (stores == 0) {
            tiny(Lang.tr("sc.gui.xv.nostore"), C3, 131, 130, GuiHoloSC.BAD);
        }
        for (int i = 0; i < Math.min(2, stores); i++) {
            tiny(Lang.tr("sc.gui.xv.store", i + 1, Tier.values()[Math.min(Tier.values().length - 1, gen.getStoreTier(i))].name(),
                    gen.getStorePct(i)), C3 + i * 67, 131, 63, GuiHoloSC.VALUE);
        }
    }

    // ------------------------------------------------------------------ the status lines

    private void statusText() {
        List<int[]> kinds = new ArrayList<int[]>();
        List<String> lines = new ArrayList<String>();
        status(kinds, lines);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            int kind = kinds.get(i)[0];
            int col = kind == 0 ? GuiHoloSC.OK : kind == 1 ? GuiHoloSC.WARN : kind == 2 ? GuiHoloSC.BAD : kind == 3 ? GuiHoloSC.CYAN & 0xFFFFFF
                    : GuiHoloSC.IDLE;
            String mark = kind == 0 ? "OK" : kind == 1 ? " !" : kind == 2 ? "!!" : kind == 3 ? " >" : " -";
            small(mark, SX + 5, STATUS_Y + i * 6, 12, col);
            small(lines.get(i), SX + 17, STATUS_Y + i * 6, SW - 24, col);
        }
    }

    private void add(List<int[]> kinds, List<String> lines, int kind, String text) {
        kinds.add(new int[]{kind});
        lines.add(text);
    }

    /** 0 fine, 1 a warning, 2 a problem, 3 information, 4 idle - the worst first. */
    private void status(List<int[]> k, List<String> l) {
        GeneratorStatus s = gen.getStatus();
        int[] sc = scan();
        if (s == GeneratorStatus.DISABLED || s == GeneratorStatus.REDSTONE) {
            add(k, l, 4, s.localized());
            return;
        }
        if (!gen.isIgnited()) {
            if (gen.getBigEvent() == TileEntityGeneratorSC.EVENT_BROKE) {
                add(k, l, 2, Lang.tr("sc.gui.big.st.disrupted"));
            } else if (gen.getBigEvent() == TileEntityGeneratorSC.EVENT_SOFT) {
                add(k, l, 4, Lang.tr("sc.gui.big.st.softstop"));
            }
            int[] miss = missingCoil();
            if (miss != null) {
                add(k, l, 2, Lang.tr("sc.gui.xv.st.coil", 24 - coils(), miss[0], miss[1], miss[2]));
            }
            if (walls() < 24 || sc[3] > 0) {
                add(k, l, 2, Lang.tr("sc.gui.big.st.needshell", 24 - walls() + sc[3]));
            }
            if (sc[5] == 0) {
                add(k, l, 2, Lang.tr("sc.gui.big.st.needstore"));
            } else if (sc[6] > 0) {
                add(k, l, 2, Lang.tr("sc.gui.big.st.weakstore"));
            }
            if (gen.getPortFluid(0) < TileEntityGeneratorSC.BIG_HE_START) {
                add(k, l, 2, Lang.tr(gen.getPortCap(0) > 0 ? "sc.gui.xv.st.he.low" : "sc.gui.xv.st.he.none", TileEntityGeneratorSC.BIG_HE_START));
            }
            if (!hasBlanket()) {
                add(k, l, 1, Lang.tr("sc.gui.big.st.noblanket"));
            }
            if (!hasDeuterium()) {
                add(k, l, 1, Lang.tr("sc.gui.big.st.nofuel"));
            }
            if (s == GeneratorStatus.OVERHEATED) {
                add(k, l, 2, Lang.tr("sc.gui.fus.st.overheat", gen.getHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT));
            }
            if (l.isEmpty()) {
                long need = gen.ignitionNeed() - gen.getIgnitionEU();
                add(k, l, 3, need > 0 ? Lang.tr("sc.gui.xv.st.charging", eu(gen.getIgnitionEU()), eu(gen.ignitionNeed()))
                        : Lang.tr("sc.gui.big.st.lighting"));
            }
            return;
        }
        if (!gen.isBigReady()) {
            add(k, l, 2, Lang.tr("sc.gui.big.st.broken"));
        }
        if (gen.isHeliumShort()) {
            add(k, l, 2, Lang.tr("sc.gui.big.st.nohe"));
        }
        if (s == GeneratorStatus.NO_DEUTERIUM) {
            add(k, l, 2, Lang.tr("sc.gui.fus.st.nocell"));
        }
        if (s == GeneratorStatus.BUFFER_FULL) {
            add(k, l, 1, Lang.tr("sc.gui.big.st.full"));
        }
        if (gen.isHydrogenShort()) {
            add(k, l, 1, Lang.tr("sc.gui.big.st.noh2", gen.getLastOutput()));
        }
        if (!gen.isHeliumShort() && lasts(0) < 60) {
            add(k, l, 1, Lang.tr("sc.gui.xv.st.he.soon", time(lasts(0))));
        }
        if (gen.getPortCap(2) <= 0) {
            add(k, l, 1, Lang.tr("sc.gui.xv.st.noar"));
        }
        if (gen.getPortCap(3) > 0 && gen.getPortFluid(3) <= 0) {
            add(k, l, 1, Lang.tr("sc.gui.xv.st.dcells", cells()));
        }
        boolean bad = false;
        for (int[] kind : k) {
            bad |= kind[0] == 2;
        }
        if (!bad) {
            k.add(0, new int[]{0});
            l.add(0, Lang.tr("sc.gui.big.st.run", gen.getLastOutput()));
        }
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tip = tooltipAt(mouseX - guiLeft, mouseY - guiTop);
        if (tip == null) {
            tip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
    }

    private List<String> tooltipAt(int mx, int my) {
        List<String> t = power.tooltip(mx, my);
        if (t != null) {
            return t;
        }
        t = battery.tooltip(mx, my, gen.getStackInSlot(TileEntityGeneratorSC.SLOT_BATTERY));
        if (t != null) {
            return t;
        }
        t = new ArrayList<String>();
        if (GuiGaugeSC.isOver(GX, GY, GuiBatterySlotSC.COLUMN_W, GH, mx, my)) {
            t.add(Lang.tr("sc.gui.xv.tip.energy"));
            t.add(Lang.tr("sc.gui.xv.tip.energy.1", scan()[5], Math.round(gen.getStoresLevel() * 100)));
            t.add("§7" + Lang.tr("sc.gui.xv.tip.energy.2"));
            return t;
        }
        if (gen.isIgnited() && GuiGaugeSC.isOver(THERMO_X - 2, THERMO_Y, 16, THERMO_H, mx, my)) {
            t.add(Lang.tr("sc.gui.xv.tip.thermo", gen.getHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT));
            t.add("§a" + Lang.tr("sc.gui.xv.tip.norm", normHeat() * 150 / TileEntityGeneratorSC.HEAT_LIMIT));
            t.add("§c" + Lang.tr("sc.gui.xv.tip.limit", 150));
            return t;
        }
        if (gen.isIgnited() && GuiGaugeSC.isOver(C1, GRAPH_Y, TORUS_W, GRAPH_H, mx, my)) {
            t.add(Lang.tr("sc.gui.xv.tip.graph"));
            t.add("§7" + Lang.tr("sc.gui.xv.tip.graph.1"));
            return t;
        }
        if (gen.isIgnited() && GuiGaugeSC.isOver(C1, 76, TORUS_W, 34, mx, my)) {
            t.add(Lang.tr("sc.gui.big.tip.stab") + ": " + Math.round(gen.getStability()) + "%");
            t.add("§7" + Lang.tr("sc.gui.xv.tip.stab.1"));
            t.add(Lang.tr("sc.gui.gen.ramp", gen.getRamp() / 10));
            return t;
        }
        if (!gen.isIgnited() && GuiGaugeSC.isOver(C1, 100, 100, 10, mx, my)) {
            t.add(Lang.tr("sc.gui.ignition"));
            t.add(gen.getIgnitionEU() + " / " + gen.ignitionNeed() + " EU");
            t.add("§7" + Lang.tr("sc.gui.big.tip.charge"));
            return t;
        }
        for (int i = 0; i < 4; i++) {
            if (GuiGaugeSC.isOver(C3 + i * TANK_STEP, TANK_Y, 31, 70, mx, my)) {
                return tankTip(i);
            }
        }
        if (GuiGaugeSC.isOver(C2, SCHEME_Y, 7 * CELL, 7 * CELL, mx, my)) {
            return cellTip((mx - C2) / CELL - 3, (my - SCHEME_Y) / CELL - 3);
        }
        return null;
    }

    private List<String> tankTip(int gas) {
        List<String> t = new ArrayList<String>();
        int cap = gen.getPortCap(gas), mb = gen.getPortFluid(gas);
        Fluid f = FluidRegistry.getFluid(GAS[gas]);
        String name = f == null ? GAS[gas] : new FluidStack(f, 1).getLocalizedName();
        if (cap <= 0) {
            t.add("§c" + Lang.tr("sc.gui.xv.tip.notank", name));
            t.add(Lang.tr("sc.gui.xv.tip.notank.1"));
            t.add("§7" + Lang.tr("sc.gui.xv.tip.why." + gas));
            return t;
        }
        t.add(name + " §7- " + Lang.tr("sc.gui.xv.tip.role." + gas));
        t.add(Lang.tr("sc.gui.xv.tip.amount", mb, cap, gen.getPortTankCount(gas)));
        if (gas == 2) {
            t.add(Lang.tr("sc.gui.xv.tip.ar", TileEntityGeneratorSC.BIG_ARGON_STOP, (int) lasts(2)));
        } else if (mb > 0) {
            t.add(Lang.tr("sc.gui.xv.tip.rate." + gas, time(lasts(gas))));
        } else {
            t.add("§c" + Lang.tr("sc.gui.xv.tip.empty." + gas));
        }
        return t;
    }

    private List<String> cellTip(int dx, int dz) {
        List<String> t = new ArrayList<String>();
        int kind = cellKind(dx, dz), dy = layer - 1;
        String at = (gen.xCoord + dx) + " " + (gen.yCoord + dy) + " " + (gen.zCoord + dz);
        if (kind == 0) {
            boolean coil = layer == 1 && Math.abs(dx) < 3 && Math.abs(dz) < 3;
            t.add("§c" + Lang.tr(coil ? "sc.gui.xv.tip.cell.nocoil" : "sc.gui.xv.tip.cell.nolead"));
        } else if (kind == 1) {
            t.add(Lang.tr("sc.gui.xv.tip.cell.lead"));
        } else if (kind == 2) {
            t.add(Lang.tr("sc.gui.xv.tip.cell.coil"));
        } else if (kind == 3) {
            t.add(GeneratorType.TOKAMAK_XV.localizedName());
        } else {
            int lab = gen.getPortLabel(wallIndex(dx, dz));
            t.add(Lang.tr("sc.gui.xv.tip.port." + lab));
        }
        t.add("§7" + at);
        return t;
    }

    // ------------------------------------------------------------------ text helpers

    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    private void small(String text, int x, int y, int maxW, int color) {
        scaled(text, x, y, maxW, color, 0.625F);
    }

    private void tiny(String text, int x, int y, int maxW, int color) {
        scaled(text, x, y, maxW, color, 0.5F);
    }

    private void scaled(String text, int x, int y, int maxW, int color, float max) {
        float k = Math.min(max, maxW / (float) Math.max(1, fontRendererObj.getStringWidth(text)));
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }
}
