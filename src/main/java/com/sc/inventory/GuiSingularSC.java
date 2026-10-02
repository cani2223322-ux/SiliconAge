package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.energy.GeneratorStatus;
import com.sc.energy.GeneratorType;
import com.sc.energy.Tier;
import com.sc.manual.Lang;
import com.sc.tileentity.SingularReactorSC;
import com.sc.tileentity.TileEntityGeneratorSC;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * The Singular Reactor's window - the Tokamak XV's size and look (420 x 280), three columns on one
 * screen: the hole (a dark sphere in its two coil rings, pulsing by its mass; the mass scale with
 * the 40-70% window, containment, Hawking temperature, the last minute's mass) or, before it is
 * born, the lighting checklist; the build (the 7x7 by layer, five layers, ports labelled) with the
 * buttons under it; the port tanks (helium, deuterium, argon), the capsule slot and the port
 * storages. Three status lines under it; a summary left of the inventory; the energy gauge shows
 * the port storages' charge.
 */
public class GuiSingularSC extends GuiContainer {

    private static final int W = ContainerGeneratorSC.XV_W, H = ContainerGeneratorSC.XV_H;
    private static final int SX = ContainerGeneratorSC.XV_SCREEN_X, SY = ContainerGeneratorSC.XV_SCREEN_Y,
            SW = ContainerGeneratorSC.XV_SCREEN_W, SH = ContainerGeneratorSC.XV_SCREEN_H;
    private static final int GX = ContainerGeneratorSC.XV_GAUGE_X, GY = ContainerGeneratorSC.XV_GAUGE_Y, GH = ContainerGeneratorSC.XV_GAUGE_H;
    private static final int C1 = 11, C2 = 131, C3 = 251, COL_Y = 37;
    private static final int SCENE_Y = 45, SCENE_W = 90, SCENE_H = 40, SCALE_X = 106, SCALE_Y = 37, SCALE_H = 102;
    private static final int ROW_Y = 88, GRAPH_Y = 126, GRAPH_H = 13, FEED_Y = 142;
    private static final int CELL = 11, SCHEME_Y = 45, LAYER_X = 212;
    private static final int TANK_Y = 52, TANK_STEP = 33, STATUS_Y = 170, SUM_Y = ContainerGeneratorSC.XV_INV_Y;
    private static final int SLOT_X = ContainerGeneratorSC.SING_SLOT_X, SLOT_Y = ContainerGeneratorSC.SING_SLOT_Y;
    private static final int BTN_Y1 = 139, BTN_Y2 = 153;
    /** Liquid helium left for less than this (s): a warning. */
    private static final int HE_WARN_SECONDS = 300;
    private static final int LAYER_ID = 900;
    private static final String[] GAS_SHORT = {"He", "D", "Ar"};
    private static final String[] LABEL_SHORT = {"", "He", "D", "Ar", "", "E", "?", "*"};
    private static final int PANEL = 0xFFB9C1CC, TITLE_BAR = 0xFF2E3642;
    /** The SV scarlet. */
    private static final int SV_MAIN = 0xFFFF3C50, SV_DARK = 0xFFB81E32, SV_LIGHT = 0xFFFF9AA4, SV_DEEP = 0xFF7E1222;

    private final TileEntityGeneratorSC gen;
    private final SingularReactorSC sing;
    private GuiPowerSC power;
    private GuiBatterySlotSC battery;
    /** The layer the scheme shows: 0 the floor .. 4 the cap. */
    private int layer = 2;
    private final int[] gasBefore = new int[3], gasRate = new int[3];
    private long storesBefore = -1, storesRate;
    private int tick;

    public GuiSingularSC(InventoryPlayer inv, TileEntityGeneratorSC gen) {
        super(new ContainerGeneratorSC(inv, gen));
        this.gen = gen;
        this.sing = gen.getSingular();
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
        buttonList.add(new GuiFieldGeneratorSC.HoloButton(ContainerGeneratorSC.BTN_SING_LIGHT, guiLeft + C2, guiTop + BTN_Y1, 56, 12, ""));
        buttonList.add(new GuiFieldGeneratorSC.HoloButton(ContainerGeneratorSC.BTN_SING_STOP, guiLeft + C2 + 59, guiTop + BTN_Y1, 56, 12, ""));
        buttonList.add(new GuiFieldGeneratorSC.HoloButton(ContainerGeneratorSC.BTN_SING_FEED, guiLeft + C2, guiTop + BTN_Y2, 72, 12, ""));
        buttonList.add(new GuiFieldGeneratorSC.HoloButton(ContainerGeneratorSC.BTN_SING_AUTO, guiLeft + C2 + 75, guiTop + BTN_Y2, 40, 12, ""));
        for (int i = 0; i < 5; i++) {                    // top to bottom: the cap .. the floor
            buttonList.add(new LayerButton(LAYER_ID + 4 - i, guiLeft + LAYER_X, guiTop + SCHEME_Y + i * 15, 34, 12,
                    Lang.tr("sc.gui.sing.layer." + (4 - i))));
        }
        refreshButtons();
    }

    private void refreshButtons() {
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            int phase = sing.getPhase();
            if (b.id == ContainerGeneratorSC.BTN_SING_LIGHT) {
                b.enabled = sing.canLight();
                b.displayString = Lang.tr(phase == SingularReactorSC.PHASE_CHARGE ? "sc.gui.sing.btn.charging"
                        : phase == SingularReactorSC.PHASE_COMPRESS ? "sc.gui.sing.btn.compress"
                        : sing.hasHole() ? "sc.gui.sing.btn.lit" : "sc.gui.sing.btn.light");
            } else if (b.id == ContainerGeneratorSC.BTN_SING_STOP) {
                boolean latched = phase == SingularReactorSC.PHASE_IDLE && sing.getEvent() != SingularReactorSC.EVENT_NONE;
                b.enabled = latched || phase == SingularReactorSC.PHASE_CHARGE || phase == SingularReactorSC.PHASE_COMPRESS
                        || phase == SingularReactorSC.PHASE_RUN;
                b.displayString = Lang.tr(latched ? "sc.gui.big.btn.allow" : phase == SingularReactorSC.PHASE_CHARGE
                        || phase == SingularReactorSC.PHASE_COMPRESS ? "sc.gui.sing.btn.cancel"
                        : phase == SingularReactorSC.PHASE_DRAIN ? "sc.gui.sing.btn.draining" : "sc.gui.sing.btn.stop");
            } else if (b.id == ContainerGeneratorSC.BTN_SING_FEED) {
                b.displayString = Lang.tr("sc.gui.sing.btn.feed", Lang.tr("sc.gui.sing.feed." + sing.getFeedMode()));
            } else if (b.id == ContainerGeneratorSC.BTN_SING_AUTO) {
                b.displayString = (sing.isAuto() ? "§a" : "§7") + Lang.tr("sc.gui.sing.btn.auto");
            }
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        refreshButtons();
        if (++tick % 20 == 0) {
            for (int i = 0; i < 3; i++) {
                int now = sing.getPortFluid(i);
                gasRate[i] = tick > 20 ? now - gasBefore[i] : 0;
                gasBefore[i] = now;
            }
            long s = sing.getStoresHave();
            storesRate = storesBefore >= 0 ? s - storesBefore : 0;
            storesBefore = s;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= LAYER_ID && button.id < LAYER_ID + 5) {
            layer = button.id - LAYER_ID;
            return;
        }
        if (!power.allowClick(button)) {
            return;
        }
        mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
    }

    /** A layer tab: the chosen one lit scarlet. */
    private class LayerButton extends GuiButton {
        LayerButton(int id, int x, int y, int w, int h, String text) {
            super(id, x, y, w, h, text);
        }

        @Override
        public void drawButton(Minecraft m, int mouseX, int mouseY) {
            boolean sel = id - LAYER_ID == layer;
            boolean over = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
            rect(xPosition, yPosition, width, height, sel ? SV_LIGHT : over ? 0xFF6EB4DC : 0xFF2A6A8A);
            rect(xPosition + 1, yPosition + 1, width - 2, height - 2, sel ? SV_DARK : 0xFF0E3A50);
            float k = Math.min(0.75F, (width - 4) / (float) Math.max(1, m.fontRenderer.getStringWidth(displayString)));
            GL11.glPushMatrix();
            GL11.glTranslatef(xPosition + (width - m.fontRenderer.getStringWidth(displayString) * k) / 2F, yPosition + (height - 8 * k) / 2F + 0.5F, 0F);
            GL11.glScalef(k, k, 1F);
            m.fontRenderer.drawString(displayString, 0, 0, 0xE6F0FA);
            GL11.glPopMatrix();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    // ------------------------------------------------------------------ the state

    private boolean shownHole() {
        int p = sing.getPhase();
        return p == SingularReactorSC.PHASE_COMPRESS || p == SingularReactorSC.PHASE_RUN || p == SingularReactorSC.PHASE_DRAIN
                || p == SingularReactorSC.PHASE_PULL;
    }

    private double mass() {
        return sing.getMass();
    }

    /** Seconds a port gas lasts at its running rate (argon: how many soft stops). */
    private double lasts(int gas) {
        int mb = sing.getPortFluid(gas);
        switch (gas) {
            case 0: return mb / SingularReactorSC.HE_PER_TICK / 20.0;
            case 1: return mb / SingularReactorSC.D_PER_TICK / 20.0;
            default: return mb / (double) SingularReactorSC.ARGON_STOP;
        }
    }

    /** Seconds the capsules (in the slot and the injector) last at the feed now. */
    private double capsulesLast() {
        double rate = sing.hasHole() ? SingularReactorSC.feedRate(sing.getFeedMode(), sing.isAuto(), mass())
                : SingularReactorSC.CAPSULE_MASS / SingularReactorSC.FEED_TICKS[1];
        double m = sing.capsules() * SingularReactorSC.CAPSULE_MASS + sing.getCapsuleLeft();
        return rate <= 0 ? Double.POSITIVE_INFINITY : m / rate / 20.0;
    }

    /** Mass change a minute (% points) from the feed and the evaporation now. */
    private double massTrendPerMinute() {
        if (sing.getPhase() != SingularReactorSC.PHASE_RUN) {
            return 0;
        }
        double feed = sing.isCapsuleShort() ? 0 : SingularReactorSC.feedRate(sing.getFeedMode(), sing.isAuto(), mass());
        return (feed - SingularReactorSC.evaporation(mass())) * 1200 * 100;
    }

    private static String time(double s) {
        if (Double.isInfinite(s)) {
            return "-";
        }
        long t = (long) Math.ceil(s);
        if (t >= 3600) {
            return Lang.tr("sc.gui.fus.h", t / 3600);
        }
        return t >= 60 ? Lang.tr("sc.gui.fus.min", t / 60) : Lang.tr("sc.gui.fus.sec", t);
    }

    private static String k(int mb) {
        return mb >= 1000 ? (mb / 1000) + "k" : String.valueOf(mb);
    }

    private static String num(double v, int digits) {
        return String.format(java.util.Locale.ROOT, "%." + digits + "f", v).replace('.', ',');
    }

    private static String eu(long v) {
        if (v >= 1000000000L) {
            return Lang.tr("sc.gui.xv.bln", num(v / 1e9, 1));
        }
        if (v >= 1000000) {
            double m = v / 1000000.0;
            return Lang.tr("sc.gui.fus.mln", m == Math.floor(m) ? String.valueOf((long) m) : num(m, 1));
        }
        return Lang.tr("sc.gui.fus.k", v / 1000);
    }

    private static String pct(double m) {
        return num(m * 100, 1) + "%";
    }

    /** A cell of the scheme on the shown layer: 0 missing, 1 lead, 2 coil, 3 the reactor, 4 a port, 5 empty chamber, 6 junk in the chamber. */
    private int cellKind(int dx, int dz) {
        int dy = layer - 2, role = SingularReactorSC.cellRole(dx, dy, dz);
        switch (role) {
            case 0: return sing.capAt(dy > 0, dx, dz) ? 1 : 0;
            case 4: return 3;
            case 2: return (sing.getCoils() >> (SingularReactorSC.ringIndex(dx, dz) + (dy > 0 ? 8 : 0)) & 1) != 0 ? 2 : 0;
            case 3: return (sing.getJunk(dy + 1) >> ((dz + 2) * 5 + dx + 2) & 1) != 0 ? 6 : 5;
            default:
                int l = dy + 1, w = SingularReactorSC.wallIndex(dx, dz);
                if ((sing.getPorts(l) >> w & 1) != 0) {
                    return 4;
                }
                return (sing.getWalls(l) >> w & 1) != 0 ? 1 : 0;
        }
    }

    private int capCount(boolean top) {
        int n = 0;
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                n += sing.capAt(top, dx, dz) ? 1 : 0;
            }
        }
        return n;
    }

    /** The first missing coil, world coordinates, or null. */
    private int[] missingCoil() {
        for (int dy = -1; dy <= 1; dy += 2) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if ((dx != 0 || dz != 0) && (sing.getCoils() >> (SingularReactorSC.ringIndex(dx, dz) + (dy > 0 ? 8 : 0)) & 1) == 0) {
                        return new int[]{gen.xCoord + dx, gen.yCoord + dy, gen.zCoord + dz};
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ background

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        window(x, y);
        GuiHoloSC.screen(x + SX, y + SY, SW, SH);
        GuiHoloSC.slot(x + SLOT_X, y + SLOT_Y, sing.hasHole() && !sing.isCapsuleShort());
        float t = mc.theWorld == null ? 0F : (mc.theWorld.getTotalWorldTime() % 1000000L) + partialTicks;
        if (shownHole()) {
            scene(x + C1, y + SCENE_Y, SCENE_W, SCENE_H, t);
            if (sing.hasHole()) {
                massScale(x + SCALE_X, y + SCALE_Y);
                massBar(x + C1, y + ROW_Y + 7, SCENE_W);
                float c = sing.getContainment() / 100F;
                GuiHoloSC.bar(x + C1, y + ROW_Y + 19, SCENE_W, 3, c, 20, c > 0.4F ? 0xFF5AE66E : c > 0.2F ? 0xFFFF9628 : 0xFFE63C3C);
                graph(x + C1, y + GRAPH_Y);
            } else {
                float p = 1F - sing.getPhaseTicks() / (float) SingularReactorSC.COMPRESS_TICKS;
                GuiHoloSC.bar(x + C1, y + ROW_Y + 22, SCENE_W, 3, p, 20, SV_MAIN);
            }
        } else {
            checklistIcons(x, y);
            float charge = (float) Math.min(1.0, gen.getIgnitionEU() / (double) SingularReactorSC.IGNITION_EU);
            GuiHoloSC.bar(x + C1 + 7, y + 117, 100, 3, charge, 20, SV_MAIN);
        }
        scheme(x + C2, y + SCHEME_Y);
        for (int i = 0; i < 3; i++) {
            tank(x + C3 + i * TANK_STEP, y + TANK_Y, i);
        }
        float inj = (float) Math.min(1.0, sing.getCapsuleLeft() / SingularReactorSC.CAPSULE_MASS);
        rect(x + SLOT_X - 1, y + SLOT_Y + 27, 18, 3, 0xFF04080C);
        rect(x + SLOT_X, y + SLOT_Y + 28, Math.round(16 * inj), 1, SV_LIGHT);
        if (sing.getStores() > 0) {
            float lvl = sing.getStoresRoom() <= 0 ? 0F : (float) sing.getStoresHave() / sing.getStoresRoom();
            rect(x + C3, y + 149, 128, 2, 0xFF04080C);
            rect(x + C3, y + 149, Math.round(128 * Math.min(1F, lvl)), 2, 0xFFE6C850);
        }
        rect(x + SX + 3, y + STATUS_Y - 3, SW - 6, 1, 0xFF1E3444);
        GuiHoloSC.screen(x + SX, y + SUM_Y, 104, H - SUM_Y - 6);
        float level = sing.getStoresRoom() <= 0 ? 0F : (float) Math.min(1.0, sing.getStoresHave() / (double) sing.getStoresRoom());
        GuiEnergyGaugeSC.draw(x + GX, y + GY, GuiBatterySlotSC.COLUMN_W, GH, level);
        battery.draw(x, y, gen.getStackInSlot(TileEntityGeneratorSC.SLOT_BATTERY));
        GuiHoloSC.glint(x + SX, y + SY, SW, SH);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The panel, its title bar, the divider and the inventory's pockets (the Tokamak XV's window). */
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
        rect(x + 4, y + 14, W - 8, 1, SV_DARK);
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
    }

    /**
     * The hole: stars, the two coil rings seen from the side (a missing coil red), the accretion disk
     * (whiter as the hole gets lighter and hotter) round a black sphere sized by the mass and pulsing
     * faster the lighter it is. Compression: matter falling in; the pull: streaks inward, flashing.
     */
    private void scene(int x, int y, int w, int h, float t) {
        rect(x, y, w, h, 0xFF1E3444);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF04060A);
        int cx = x + w / 2, cy = y + h / 2;
        for (int i = 0; i < 14; i++) {
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 0.1 + i * 1.7));
            rect(x + 3 + (i * 23) % (w - 6), y + 3 + (i * 11) % (h - 6), 1, 1, GuiSceneSC.mix(0xFF3A4252, 0xFFB0BAC8, tw));
        }
        int phase = sing.getPhase();
        double m = mass();
        float cont = sing.getContainment() / 100F;
        // the coil rings above and below
        for (int ring = 0; ring < 2; ring++) {
            int ry = ring == 0 ? cy - 14 : cy + 14;
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4 + t * 0.01;
                int px = (int) Math.round(cx + 30 * Math.cos(a)), py = (int) Math.round(ry + 4 * Math.sin(a));
                boolean have = (sing.getCoils() >> (k + ring * 8) & 1) != 0;
                int col = !have ? 0xFFE63C3C : GuiSceneSC.mix(SV_DEEP, SV_MAIN, phase == SingularReactorSC.PHASE_IDLE ? 0.2F : cont);
                rect(px - 2, py - 1, 4, 2, col);
                if (have && phase != SingularReactorSC.PHASE_IDLE && Math.sin(t * 0.3 + k) > 0.6) {
                    rect(px - 1, py - 1, 2, 1, SV_LIGHT);
                }
            }
        }
        if (phase == SingularReactorSC.PHASE_COMPRESS) {
            float p = 1F - sing.getPhaseTicks() / (float) SingularReactorSC.COMPRESS_TICKS;
            for (int i = 0; i < 28; i++) {
                double a = i * 2.39 + t * 0.05;
                float r = (float) ((1 - ((t * 0.02 + i * 0.137) % 1.0)) * 26 * (1 - p * 0.6));
                rect((int) (cx + r * Math.cos(a)), (int) (cy + r * 0.5 * Math.sin(a)), 1, 1, GuiSceneSC.mix(SV_LIGHT, 0xFFFFFFFF, p));
            }
            disc(cx, cy, 1 + Math.round(5 * p), 0xFF000000);
            ring(cx, cy, 1 + Math.round(5 * p) + 1, SV_DARK);
            return;
        }
        if (!sing.hasHole()) {
            ring(cx, cy, 6, 0xFF2A1A22);
            return;
        }
        float speed = (float) (0.15 + (1 - m) * 0.5);
        int r = 3 + (int) Math.round(9 * m + Math.sin(t * speed) * (1 - m) * 1.2);
        float hot = (float) Math.min(1.0, Math.max(0.0, (0.7 - m) / 0.6));
        for (int rr = r + 2; rr <= r + 7; rr++) {
            int base = GuiSceneSC.mix(SV_MAIN, 0xFFFFC080, (rr - r - 2) / 5F);
            int col = GuiSceneSC.mix(base, 0xFFFFFFFF, hot * 0.6F);
            for (int kk = 0; kk < 120; kk++) {
                double a = kk * 2 * Math.PI / 120 + t * (0.05 + hot * 0.06);
                if (Math.sin(a * 3 + rr) > -0.2) {
                    rect((int) (cx + rr * 1.6 * Math.cos(a)), (int) (cy + rr * 0.35 * Math.sin(a)), 1, 1, col);
                }
            }
        }
        if (phase == SingularReactorSC.PHASE_PULL) {
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                int len = (int) ((t * 1.5 + i * 5) % 18);
                int dist = 30 - len;
                rect((int) (cx + dist * Math.cos(a)), (int) (cy + dist * 0.5 * Math.sin(a)), 2, 1, 0xFFFF6E6E);
            }
        }
        disc(cx, cy, r, 0xFF000000);
        ring(cx, cy, r, phase == SingularReactorSC.PHASE_PULL && (int) (t / 3) % 2 == 0 ? 0xFFFFFFFF : SV_DARK);
        if (sing.getBurstTicks() > 0) {
            rect(x + 1, y + 1, w - 2, 1, SV_MAIN);
        }
    }

    private static void disc(int cx, int cy, int r, int c) {
        for (int yy = -r; yy <= r; yy++) {
            int half = (int) Math.sqrt(Math.max(0, r * r - yy * yy));
            rect(cx - half, cy + yy, 2 * half + 1, 1, c);
        }
    }

    private static void ring(int cx, int cy, int r, int c) {
        for (int k = 0; k < 64; k++) {
            double a = k * 2 * Math.PI / 64;
            rect((int) Math.round(cx + r * Math.cos(a)), (int) Math.round(cy + r * Math.sin(a)), 1, 1, c);
        }
    }

    /** The mass scale, bottom 0 to top 100%: the light zone red, under 40% orange, the window green, heavy grey; the mark. */
    private void massScale(int x, int y) {
        int w = 7, h = SCALE_H;
        rect(x, y, w, h, 0xFF1E3444);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF04080C);
        int in = h - 4, bottom = y + h - 2;
        for (int i = 0; i < in; i++) {
            float k = i / (float) in;
            int col = k < SingularReactorSC.EVAP_MASS ? 0xFF501010 : k < SingularReactorSC.LIGHT ? 0xFF6A1A1A
                    : k < SingularReactorSC.WINDOW_LO ? 0xFF4A3410 : k <= SingularReactorSC.WINDOW_HI ? 0xFF14402A : 0xFF2A3038;
            rect(x + 2, bottom - i - 1, w - 4, 1, col);
        }
        int lo = bottom - Math.round(in * (float) SingularReactorSC.WINDOW_LO), hi = bottom - Math.round(in * (float) SingularReactorSC.WINDOW_HI);
        rect(x - 2, lo, w + 4, 1, 0xFF5AE66E);
        rect(x - 2, hi, w + 4, 1, 0xFF5AE66E);
        int my = bottom - Math.round(in * (float) Math.min(1.0, mass()));
        rect(x + 1, my, w - 2, 1, 0xFFFFFFFF);
        rect(x + w + 1, my - 1, 1, 3, 0xFFE6F0FA);
        rect(x + w + 2, my, 1, 1, 0xFFE6F0FA);
    }

    /** The horizontal mass bar with the window marked. */
    private void massBar(int x, int y, int w) {
        rect(x, y, w, 3, 0xFF04080C);
        int lo = x + (int) Math.round(w * SingularReactorSC.WINDOW_LO), hi = x + (int) Math.round(w * SingularReactorSC.WINDOW_HI);
        rect(lo, y, hi - lo, 3, 0xFF14402A);
        double m = mass();
        int col = m < SingularReactorSC.LIGHT ? 0xFFE63C3C : m < SingularReactorSC.WINDOW_LO ? 0xFFFF9628
                : m <= SingularReactorSC.WINDOW_HI ? 0xFF5AE66E : 0xFF8A909A;
        rect(x, y + 1, (int) Math.round(w * Math.min(1.0, m)), 1, col);
        rect(lo, y - 1, 1, 5, 0xFF5AE66E);
        rect(hi, y - 1, 1, 5, 0xFF5AE66E);
    }

    /** Mass over the last minute, a dot a second, the window's two lines. */
    private void graph(int x, int y) {
        int w = SCENE_W;
        rect(x, y, w, GRAPH_H, 0xFF1E3444);
        rect(x + 1, y + 1, w - 2, GRAPH_H - 2, 0xFF04080C);
        for (double edge : new double[]{SingularReactorSC.WINDOW_LO, SingularReactorSC.WINDOW_HI}) {
            rect(x + 1, y + GRAPH_H - 2 - (int) Math.round((GRAPH_H - 3) * edge), w - 2, 1, 0xFF143A24);
        }
        for (int k = 0; k < 60; k++) {
            int v = sing.massAgo(k);
            if (v < 0) {
                continue;
            }
            int px = x + w - 2 - k * (w - 3) / 60;
            int py = y + GRAPH_H - 2 - Math.round((GRAPH_H - 3) * v / 100F);
            rect(px, py, 1, 1, v < 20 ? 0xFFE63C3C : v < 40 ? 0xFFFF9628 : v <= 70 ? 0xFF5AE66E : 0xFF8A909A);
        }
    }

    private void checklistIcons(int x, int y) {
        int[] st = checklistStates();
        for (int i = 0; i < st.length; i++) {
            int ix = x + C1, iy = y + COL_Y + 9 + i * 10;
            if (st[i] == 1) {
                rect(ix, iy + 2, 1, 2, 0xFF5AE66E);
                rect(ix + 1, iy + 3, 1, 2, 0xFF5AE66E);
                rect(ix + 2, iy + 2, 1, 2, 0xFF5AE66E);
                rect(ix + 3, iy + 1, 1, 2, 0xFF5AE66E);
                rect(ix + 4, iy, 1, 2, 0xFF5AE66E);
            } else if (st[i] == 0) {
                for (int k = 0; k < 5; k++) {
                    rect(ix + k, iy + k, 1, 1, 0xFFE63C3C);
                    rect(ix + 4 - k, iy + k, 1, 1, 0xFFE63C3C);
                }
            } else {
                rect(ix, iy, 5, 5, SV_MAIN);
                rect(ix + 1, iy + 1, 3, 3, 0xFF04080C);
                rect(ix + 2, iy + 1, 2, 2, SV_LIGHT);
            }
        }
    }

    /** The checklist: 1 done, 0 missing, 2 in progress. */
    private int[] checklistStates() {
        boolean shell = sing.getWallCount() == 72 && sing.getCapMissing() == 0 && sing.getJunkCount() == 0;
        boolean charging = sing.getPhase() == SingularReactorSC.PHASE_CHARGE;
        return new int[]{shell ? 1 : 0, sing.getCoilCount() == 16 ? 1 : 0, sing.getStores() > 0 && sing.getWeakStores() == 0 ? 1 : 0,
                sing.getPortFluid(0) >= SingularReactorSC.HE_START ? 1 : 0, sing.getPortFluid(1) >= SingularReactorSC.D_IGNITION ? 1 : 0,
                sing.hasCapsule() ? 1 : 0, gen.getIgnitionEU() >= SingularReactorSC.IGNITION_EU ? 1 : charging ? 2 : sing.chargeOk() ? 1 : 0};
    }

    /** The 7x7 of the chosen layer. */
    private void scheme(int x, int y) {
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                int cx = x + (dx + 3) * CELL, cy = y + (dz + 3) * CELL, kind = cellKind(dx, dz);
                int col = kind == 0 ? 0xFFE63C3C : kind == 1 ? 0xFF8A909A : kind == 2 ? SV_MAIN
                        : kind == 3 ? (sing.hasHole() ? 0xFF000000 : 0xFF3A2030) : kind == 4 ? 0xFF287874 : kind == 5 ? 0xFF0C141C : 0xFFFF9628;
                rect(cx, cy, CELL - 1, CELL - 1, col);
                if (kind == 3) {
                    rect(cx + 2, cy + 2, CELL - 5, CELL - 5, sing.hasHole() ? SV_DARK : 0xFF5A3A48);
                    rect(cx + 4, cy + 4, CELL - 9, CELL - 9, 0xFF000000);
                }
                if (kind == 4) {
                    int lab = sing.getLabel((layer - 1) * 24 + SingularReactorSC.wallIndex(dx, dz));
                    int edge = lab >= 1 && lab <= 3 ? GuiTankGaugeSC.colourOf(fluid(lab - 1)) : lab == SingularReactorSC.LABEL_STORE ? 0xFFE6C850 : 0xFF5A6E78;
                    rect(cx, cy, CELL - 1, 1, edge);
                    rect(cx, cy + CELL - 2, CELL - 1, 1, edge);
                    rect(cx, cy, 1, CELL - 1, edge);
                    rect(cx + CELL - 2, cy, 1, CELL - 1, edge);
                }
                if (kind == 5 && SingularReactorSC.cellRole(dx, layer - 2, dz) == 3) {
                    rect(cx + 4, cy + 4, 2, 2, 0xFF1A2834);
                }
            }
        }
    }

    private static Fluid fluid(int gas) {
        Fluid f = FluidRegistry.getFluid(SingularReactorSC.GASES[gas]);
        return f != null ? f : FluidRegistry.WATER;
    }

    private void tank(int x, int y, int gas) {
        int cap = sing.getPortCap(gas), mb = sing.getPortFluid(gas);
        Fluid f = FluidRegistry.getFluid(SingularReactorSC.GASES[gas]);
        FluidStack st = f != null && mb > 0 ? new FluidStack(f, mb) : null;
        GuiTankGaugeSC.draw(mc, x, y, st, Math.max(1, cap), cap > 0 && f != null ? SingularReactorSC.GASES[gas] : null, false);
        if (cap > 0) {
            return;
        }
        rect(x + 20, y + 4, 2, 2, 0xFF3C3C3C);
        int c = gas < 2 ? 0xFFE63C3C : 0xFF8C96A0;
        for (int i = 0; i < 44; i++) {
            int px = 5 + i * 8 / 44;
            rect(x + px, y + 12 + i, 2, 1, c);
            rect(x + 12 - i * 8 / 44, y + 12 + i, 2, 1, c);
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
        fit(GeneratorType.SINGULAR_REACTOR.localizedName(), 8, 5, W - 60, GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, Tier.SV, W - 6, 3);
        header();
        if (shownHole()) {
            holeText();
        } else {
            checklistText();
        }
        buildText();
        portsText();
        statusText();
        summary();
        power.drawGaugeOff(fontRendererObj);
    }

    private void header() {
        int y = 25;
        fit(Lang.tr("sc.gui.sing.title"), C1, y - 1, 72, SV_LIGHT & 0xFFFFFF);
        String chip;
        int col;
        GeneratorStatus s = gen.getStatus();
        int phase = sing.getPhase();
        if (s == GeneratorStatus.DISABLED || s == GeneratorStatus.REDSTONE) {
            chip = "off";
            col = GuiHoloSC.IDLE;
        } else if (phase == SingularReactorSC.PHASE_RUN) {
            chip = "run";
            col = GuiHoloSC.OK;
        } else if (phase == SingularReactorSC.PHASE_DRAIN) {
            chip = "drain";
            col = GuiHoloSC.WARN;
        } else if (phase == SingularReactorSC.PHASE_PULL) {
            chip = "pull";
            col = GuiHoloSC.BAD;
        } else if (phase == SingularReactorSC.PHASE_CHARGE || phase == SingularReactorSC.PHASE_COMPRESS) {
            chip = phase == SingularReactorSC.PHASE_CHARGE ? "charge" : "compress";
            col = SV_LIGHT & 0xFFFFFF;
        } else if (sing.getEvent() == SingularReactorSC.EVENT_EJECT) {
            chip = "eject";
            col = GuiHoloSC.BAD;
        } else if (sing.getEvent() == SingularReactorSC.EVENT_EVAP) {
            chip = "evap";
            col = GuiHoloSC.BAD;
        } else if (sing.getEvent() == SingularReactorSC.EVENT_SOFT) {
            chip = "soft";
            col = GuiHoloSC.WARN;
        } else {
            chip = "prep";
            col = GuiHoloSC.CYAN & 0xFFFFFF;
        }
        String text = Lang.tr("sc.gui.sing.state." + chip);
        int tw = (int) (fontRendererObj.getStringWidth(text) * 0.625F) + 6;
        int cx = 90;
        rect(cx, y - 1, tw, 8, 0xFF000000 | col);
        rect(cx + 1, y, tw - 2, 6, 0xFF06101A);
        small(text, cx + 3, y + 1, tw - 4, col);
        small(sing.hasHole() ? Lang.tr("sc.gui.xv.out", gen.getLastOutput()) : Lang.tr("sc.gui.sing.nohole"), cx + tw + 6, y + 1, 90,
                sing.hasHole() ? GuiHoloSC.OK : GuiHoloSC.IDLE);
        float rad = gen.radiationLevel();
        String r = rad > 0 ? Lang.tr(sing.isPiercing() ? "sc.gui.sing.flash" : "sc.gui.big.rad", com.sc.radiation.RadiationSC.fmt(rad),
                gen.radiationRadiusNow()) : Lang.tr("sc.gui.xv.norad");
        int rw = (int) (fontRendererObj.getStringWidth(r) * 0.625F);
        small(r, SX + SW - 5 - Math.min(110, rw), y + 1, 110, sing.isPiercing() ? GuiHoloSC.BAD : rad > 0 ? GuiHoloSC.WARN : GuiHoloSC.IDLE);
    }

    private void holeText() {
        int phase = sing.getPhase();
        small(Lang.tr("sc.gui.sing.hole"), C1, COL_Y, 80, GuiHoloSC.LABEL);
        if (phase == SingularReactorSC.PHASE_COMPRESS) {
            small(Lang.tr("sc.gui.sing.compressing", time(sing.getPhaseTicks() / 20.0)), C1, ROW_Y, 95, SV_LIGHT & 0xFFFFFF);
            tiny(Lang.tr("sc.gui.sing.compress.1"), C1, ROW_Y + 8, 95, GuiHoloSC.LABEL);
            tiny(Lang.tr("sc.gui.sing.compress.2", (int) Math.round(SingularReactorSC.START_MASS * 100)), C1, ROW_Y + 14, 95, GuiHoloSC.LABEL);
            return;
        }
        double m = mass();
        tiny("100", SCALE_X + 10, SCALE_Y, 14, GuiHoloSC.LABEL);
        tiny("70", SCALE_X + 10, SCALE_Y + SCALE_H - 2 - (int) Math.round((SCALE_H - 4) * SingularReactorSC.WINDOW_HI) - 2, 12, GuiHoloSC.OK);
        tiny("40", SCALE_X + 10, SCALE_Y + SCALE_H - 2 - (int) Math.round((SCALE_H - 4) * SingularReactorSC.WINDOW_LO) - 2, 12, GuiHoloSC.OK);
        tiny("0", SCALE_X + 10, SCALE_Y + SCALE_H - 6, 12, GuiHoloSC.LABEL);
        int mcol = m < SingularReactorSC.LIGHT ? GuiHoloSC.BAD : m < SingularReactorSC.WINDOW_LO || m > SingularReactorSC.WINDOW_HI ? GuiHoloSC.WARN : GuiHoloSC.OK;
        row(Lang.tr("sc.gui.sing.mass"), pct(m) + "  x" + num(SingularReactorSC.powerFactor(m), 1), ROW_Y, mcol);
        float c = sing.getContainment();
        row(Lang.tr("sc.gui.sing.cont"), Math.round(c) + "%", ROW_Y + 12, c > 40 ? GuiHoloSC.OK : c > 20 ? GuiHoloSC.WARN : GuiHoloSC.BAD);
        row(Lang.tr("sc.gui.sing.hawking"), Lang.tr("sc.gui.xv.bln", num(SingularReactorSC.hawkingBillionK(Math.max(0.01, m)), 2)) + " K",
                ROW_Y + 24, GuiHoloSC.VALUE);
        tiny(Lang.tr("sc.gui.sing.graph"), C1, GRAPH_Y - 6, 70, GuiHoloSC.LABEL);
        double trend = massTrendPerMinute();
        tiny((trend >= 0 ? "+" : "") + num(trend, 2) + Lang.tr("sc.gui.sing.permin"), C1 + SCENE_W - 30, GRAPH_Y - 6, 30,
                Math.abs(trend) < 0.05 ? GuiHoloSC.IDLE : trend > 0 ? GuiHoloSC.OK : GuiHoloSC.WARN);
        small(Lang.tr("sc.gui.sing.feedline", Lang.tr("sc.gui.sing.feed." + sing.getFeedMode()),
                Lang.tr(sing.isAuto() ? "sc.gui.sing.auto.on" : "sc.gui.sing.auto.off")), C1, FEED_Y, 110, GuiHoloSC.VALUE);
        small(Lang.tr("sc.gui.sing.injector", Math.round(sing.getCapsuleLeft() / SingularReactorSC.CAPSULE_MASS * 100), sing.capsules()),
                C1, FEED_Y + 7, 110, sing.isCapsuleShort() ? GuiHoloSC.BAD : GuiHoloSC.LABEL);
        if (phase == SingularReactorSC.PHASE_DRAIN) {
            small(Lang.tr("sc.gui.sing.draining", time(sing.getPhaseTicks() / 20.0)), C1, FEED_Y + 14, 110, GuiHoloSC.WARN);
        } else if (phase == SingularReactorSC.PHASE_PULL) {
            small(Lang.tr("sc.gui.sing.pulling", time(sing.getPhaseTicks() / 20.0)), C1, FEED_Y + 14, 110, GuiHoloSC.BAD);
        }
    }

    private void row(String name, String value, int y, int col) {
        small(name, C1, y, 50, GuiHoloSC.LABEL);
        int vw = (int) (fontRendererObj.getStringWidth(value) * 0.625F);
        small(value, C1 + SCENE_W - Math.min(50, vw), y, 50, col);
    }

    private void checklistText() {
        small(Lang.tr("sc.gui.sing.check"), C1, COL_Y, 110, GuiHoloSC.LABEL);
        int[] st = checklistStates();
        int[] miss = missingCoil();
        int gaps = 72 - sing.getWallCount() + sing.getCapMissing();
        String[] lines = {
            st[0] == 1 ? Lang.tr("sc.gui.sing.c.shell") : sing.getJunkCount() > 0 && gaps == 0 ? Lang.tr("sc.gui.sing.c.junk", sing.getJunkCount())
                    : Lang.tr("sc.gui.xv.c.shell.no", gaps),
            st[1] == 1 ? Lang.tr("sc.gui.sing.c.coils") : Lang.tr("sc.gui.sing.c.coils.no", sing.getCoilCount(),
                    miss == null ? "" : miss[0] + " " + miss[1] + " " + miss[2]),
            sing.getStores() == 0 ? Lang.tr("sc.gui.xv.c.store.no") : sing.getWeakStores() > 0 ? Lang.tr("sc.gui.xv.c.store.weak")
                    : Lang.tr("sc.gui.xv.c.store", sing.getStores()),
            st[3] == 1 ? Lang.tr("sc.gui.xv.c.he", k(sing.getPortFluid(0))) : Lang.tr("sc.gui.xv.c.he.no", SingularReactorSC.HE_START),
            st[4] == 1 ? Lang.tr("sc.gui.sing.c.d", k(sing.getPortFluid(1))) : Lang.tr("sc.gui.sing.c.d.no", k(SingularReactorSC.D_IGNITION)),
            st[5] == 1 ? Lang.tr("sc.gui.sing.c.capsule", sing.capsules()) : Lang.tr("sc.gui.sing.c.capsule.no"),
            Lang.tr("sc.gui.sing.c.charge", eu(gen.getIgnitionEU() + (sing.getPhase() == SingularReactorSC.PHASE_CHARGE ? 0 : sing.getStoresHave())),
                    eu(SingularReactorSC.IGNITION_EU))};
        for (int i = 0; i < lines.length; i++) {
            small(lines[i], C1 + 7, COL_Y + 9 + i * 10, 106, st[i] == 0 ? GuiHoloSC.BAD : st[i] == 2 ? SV_LIGHT & 0xFFFFFF : GuiHoloSC.VALUE);
        }
        String below;
        if (sing.getPhase() == SingularReactorSC.PHASE_CHARGE) {
            long need = SingularReactorSC.IGNITION_EU - gen.getIgnitionEU();
            below = Lang.tr("sc.gui.sing.charging", eu(gen.getIgnitionEU()), time(need / (double) SingularReactorSC.CHARGE_PER_TICK / 20.0));
        } else if (gen.getIgnitionEU() > 0) {
            below = Lang.tr("sc.gui.sing.chargekept", eu(gen.getIgnitionEU()));
        } else {
            below = Lang.tr("sc.gui.sing.chargehint", time(SingularReactorSC.IGNITION_EU / (double) SingularReactorSC.CHARGE_PER_TICK / 20.0),
                    SingularReactorSC.COMPRESS_TICKS / 20);
        }
        tiny(below, C1 + 7, 122, 106, GuiHoloSC.LABEL);
        if (sing.getEvent() != SingularReactorSC.EVENT_NONE) {
            tiny(Lang.tr("sc.gui.sing.latched"), C1 + 7, 128, 106, GuiHoloSC.WARN);
        }
    }

    private void buildText() {
        small(Lang.tr("sc.gui.sing.build"), C2, COL_Y, 76, GuiHoloSC.LABEL);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (cellKind(dx, dz) == 4) {
                    String l = LABEL_SHORT[sing.getLabel((layer - 1) * 24 + SingularReactorSC.wallIndex(dx, dz))];
                    int lw = (int) (fontRendererObj.getStringWidth(l) * 0.5F);
                    tiny(l, C2 + (dx + 3) * CELL + (CELL - 1 - lw) / 2, SCHEME_Y + (dz + 3) * CELL + 3, CELL, GuiHoloSC.VALUE);
                }
            }
        }
        int c = sing.getCoilCount(), w = sing.getWallCount(), top = capCount(true), bottom = capCount(false), junk = sing.getJunkCount();
        small(Lang.tr("sc.gui.sing.count1", c, w), C2, 124, 115, c == 16 && w == 72 ? GuiHoloSC.OK : GuiHoloSC.WARN);
        small(junk > 0 ? Lang.tr("sc.gui.sing.count2.junk", top, bottom, junk) : Lang.tr("sc.gui.sing.count2", top, bottom), C2, 131, 115,
                top == 49 && bottom == 49 && junk == 0 ? GuiHoloSC.OK : GuiHoloSC.WARN);
    }

    private void portsText() {
        small(Lang.tr("sc.gui.sing.ports"), C3, COL_Y, 70, GuiHoloSC.LABEL);
        if (sing.getFreeTanks() > 0) {
            tiny(Lang.tr("sc.gui.xv.free", sing.getFreeTanks()), C3, COL_Y + 7, 60, GuiHoloSC.LABEL);
        }
        for (int i = 0; i < 3; i++) {
            int gx = C3 + i * TANK_STEP, cap = sing.getPortCap(i), mb = sing.getPortFluid(i);
            int nw = (int) (fontRendererObj.getStringWidth(GAS_SHORT[i]) * 0.75F);
            scaled(GAS_SHORT[i], gx + 12 - nw / 2, 45, 22, GuiHoloSC.VALUE, 0.75F);
            if (cap <= 0) {
                small(Lang.tr("sc.gui.xv.notank"), gx, 123, 31, i < 2 ? GuiHoloSC.BAD : GuiHoloSC.IDLE);
                continue;
            }
            small(k(mb) + "/" + k(cap), gx, 123, 31, GuiHoloSC.VALUE);
            int rate = gasRate[i];
            String arrow = rate > 0 ? "+ " + k(Math.max(1, rate / 20)) + "/t" : rate < 0 ? "- " + rateText(-rate) + "/t" : "= 0";
            small(arrow, gx, 129, 31, rate > 0 ? GuiHoloSC.OK : rate < 0 ? GuiHoloSC.WARN : GuiHoloSC.IDLE);
            String left;
            int col = GuiHoloSC.LABEL;
            if (mb <= 0) {
                left = Lang.tr("sc.gui.xv.empty");
                col = GuiHoloSC.BAD;
            } else if (i == 2) {
                left = Lang.tr("sc.gui.xv.stops", (int) lasts(2));
            } else if (rate > 0) {
                left = Lang.tr("sc.gui.xv.rising");
                col = GuiHoloSC.OK;
            } else {
                double s = lasts(i);
                left = "~" + time(s);
                col = i == 0 && s < HE_WARN_SECONDS ? GuiHoloSC.WARN : GuiHoloSC.LABEL;
            }
            small(left, gx, 135, 31, col);
        }
        small(Lang.tr("sc.gui.sing.capsules"), SLOT_X - 2, SLOT_Y - 10, 26, GuiHoloSC.LABEL);
        int n = sing.capsules();
        small(n > 0 ? "x" + n : Lang.tr("sc.gui.xv.cells.none"), SLOT_X, SLOT_Y + 19, 24, n > 0 ? GuiHoloSC.VALUE : GuiHoloSC.BAD);
        tiny(Lang.tr("sc.gui.sing.injector.short"), SLOT_X - 2, SLOT_Y + 31, 26, GuiHoloSC.LABEL);
        int stores = sing.getStores();
        if (stores == 0) {
            small(Lang.tr("sc.gui.xv.nostore"), C3, 143, 128, GuiHoloSC.BAD);
            return;
        }
        float lvl = sing.getStoresRoom() <= 0 ? 0F : (float) sing.getStoresHave() / sing.getStoresRoom();
        small(Lang.tr("sc.gui.sing.storeline", stores, Math.round(lvl * 100), eu(sing.getStoresHave())), C3, 143, 128, GuiHoloSC.VALUE);
        small(fullIn(), C3, 152, 128, GuiHoloSC.IDLE);
        if (sing.getWeakStores() > 0) {
            small(Lang.tr("sc.gui.xv.c.store.weak"), C3, 158, 128, GuiHoloSC.BAD);
        }
    }

    private static String rateText(int perSecond) {
        return perSecond >= 20 ? k(perSecond / 20) : num(perSecond / 20.0, 1);
    }

    private String fullIn() {
        long room = sing.getStoresRoom() - sing.getStoresHave();
        if (room <= 0) {
            return Lang.tr("sc.gui.xv.full");
        }
        if (storesRate <= 0) {
            return Lang.tr("sc.gui.xv.notfilling");
        }
        return Lang.tr("sc.gui.xv.fullin", time(room / (double) storesRate));
    }

    /** The summary under the screen. */
    private void summary() {
        int x = SX + 4, y = SUM_Y + 4;
        small(Lang.tr("sc.gui.xv.summary"), x, y, 90, GuiHoloSC.LABEL);
        boolean lit = sing.hasHole();
        double cap = capsulesLast(), he = lasts(0);
        String[][] rows = {
            {lit ? Lang.tr("sc.gui.xv.out", gen.getLastOutput()) : Lang.tr("sc.gui.sing.nohole"), lit ? "ok" : "idle"},
            {Lang.tr("sc.gui.xv.perminute", eu((long) gen.getLastOutput() * 1200)), "val"},
            {Lang.tr("sc.gui.xv.instores", eu(sing.getStoresHave())), "val"},
            {Lang.tr("sc.gui.sing.sum.mass", lit ? pct(mass()) : "-", sing.isAuto() ? Lang.tr("sc.gui.sing.sum.auto")
                    : Lang.tr("sc.gui.sing.feed." + sing.getFeedMode())), "val"},
            {Lang.tr("sc.gui.sing.sum.capsules", "~" + time(cap)), lit && cap < 120 ? "warn" : "val"},
            {sing.getPortCap(0) > 0 ? Lang.tr("sc.gui.xv.helasts", "~" + time(he)) : Lang.tr("sc.gui.xv.notank") + " He",
                    sing.getPortCap(0) <= 0 || he < HE_WARN_SECONDS ? "warn" : "val"},
            {Lang.tr("sc.gui.sing.sum.d", "~" + time(lasts(1))), "val"}};
        for (int i = 0; i < rows.length; i++) {
            String kind = rows[i][1];
            int col = "ok".equals(kind) ? GuiHoloSC.OK : "warn".equals(kind) ? GuiHoloSC.WARN : "idle".equals(kind) ? GuiHoloSC.IDLE : GuiHoloSC.VALUE;
            small(rows[i][0], x, y + 9 + i * 9, 96, col);
        }
    }

    // ------------------------------------------------------------------ the status lines

    private void statusText() {
        List<int[]> kinds = new ArrayList<int[]>();
        List<String> lines = new ArrayList<String>();
        status(kinds, lines);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            int kind = kinds.get(i)[0];
            int col = kind == 0 ? GuiHoloSC.OK : kind == 1 ? GuiHoloSC.WARN : kind == 2 ? GuiHoloSC.BAD : kind == 3 ? SV_LIGHT & 0xFFFFFF
                    : GuiHoloSC.IDLE;
            String mark = kind == 0 ? "OK" : kind == 1 ? " !" : kind == 2 ? "!!" : kind == 3 ? " >" : " -";
            small(mark, SX + 5, STATUS_Y + i * 6, 12, col);
            small(lines.get(i), SX + 17, STATUS_Y + i * 6, 355, col);
        }
    }

    private void add(List<int[]> kinds, List<String> lines, int kind, String text) {
        kinds.add(new int[]{kind});
        lines.add(text);
    }

    /** 0 fine, 1 a warning, 2 a problem, 3 information, 4 idle - the worst first. */
    private void status(List<int[]> k, List<String> l) {
        GeneratorStatus s = gen.getStatus();
        int phase = sing.getPhase();
        if ((s == GeneratorStatus.DISABLED || s == GeneratorStatus.REDSTONE) && phase == SingularReactorSC.PHASE_RUN) {
            add(k, l, 1, Lang.tr("sc.gui.sing.st.offrun"));        // switched off doesn't put it out: it still eats fuel
        } else if (s == GeneratorStatus.DISABLED || s == GeneratorStatus.REDSTONE) {
            add(k, l, 4, s.localized() + (sing.hasHole() ? " - " + Lang.tr("sc.gui.sing.st.offhole") : ""));
        }
        if (sing.getBurstTicks() > 0) {
            add(k, l, 2, Lang.tr("sc.gui.sing.st.flash", gen.radiationRadiusNow(), time(sing.getBurstTicks() / 20.0)));
        }
        if (phase == SingularReactorSC.PHASE_PULL) {
            add(k, l, 2, Lang.tr("sc.gui.sing.st.pull", SingularReactorSC.PULL_RADIUS));
            return;
        }
        if (!sing.hasHole() && phase != SingularReactorSC.PHASE_COMPRESS) {
            if (sing.getEvent() == SingularReactorSC.EVENT_EJECT) {
                add(k, l, 2, Lang.tr("sc.gui.sing.st.ejected"));
            } else if (sing.getEvent() == SingularReactorSC.EVENT_EVAP) {
                add(k, l, 2, Lang.tr("sc.gui.sing.st.evaporated"));
            } else if (sing.getEvent() == SingularReactorSC.EVENT_SOFT) {
                add(k, l, 4, Lang.tr("sc.gui.sing.st.soft"));
            }
            int[] miss = missingCoil();
            if (miss != null) {
                add(k, l, 2, Lang.tr("sc.gui.sing.st.coil", 16 - sing.getCoilCount(), miss[0], miss[1], miss[2]));
            }
            if (sing.isPortTaken()) {
                add(k, l, 2, Lang.tr("sc.gui.xv.st.porttaken"));
            }
            int gaps = 72 - sing.getWallCount() + sing.getCapMissing();
            if (gaps > 0) {
                add(k, l, 2, Lang.tr("sc.gui.big.st.needshell", gaps));
            }
            if (sing.getJunkCount() > 0) {
                add(k, l, 2, Lang.tr("sc.gui.sing.st.junk", sing.getJunkCount()));
            }
            if (sing.getStores() == 0) {
                add(k, l, 2, Lang.tr("sc.gui.big.st.needstore"));
            } else if (sing.getWeakStores() > 0) {
                add(k, l, 2, Lang.tr("sc.gui.big.st.weakstore"));
            }
            if (sing.getPortFluid(0) < SingularReactorSC.HE_START) {
                add(k, l, 2, Lang.tr(sing.getPortCap(0) > 0 ? "sc.gui.xv.st.he.low" : "sc.gui.xv.st.he.none", SingularReactorSC.HE_START));
            }
            if (sing.getPortFluid(1) < SingularReactorSC.D_IGNITION) {
                add(k, l, 2, Lang.tr("sc.gui.sing.st.d.low", SingularReactorSC.D_IGNITION));
            }
            if (!sing.hasCapsule()) {
                add(k, l, 1, Lang.tr("sc.gui.sing.st.nocapsule"));
            }
            if (!sing.chargeOk() && phase == SingularReactorSC.PHASE_IDLE) {
                add(k, l, 1, Lang.tr("sc.gui.sing.st.nocharge", eu(SingularReactorSC.IGNITION_EU)));
            }
            if (sing.getPortCap(2) <= 0) {
                add(k, l, 1, Lang.tr("sc.gui.sing.st.noar"));
            }
            if (phase == SingularReactorSC.PHASE_CHARGE) {
                add(k, l, 3, Lang.tr("sc.gui.xv.st.charging", eu(gen.getIgnitionEU()), eu(SingularReactorSC.IGNITION_EU)));
            } else if (l.isEmpty() || sing.canLight()) {
                add(k, l, 3, Lang.tr("sc.gui.sing.st.ready"));
            }
            return;
        }
        if (phase == SingularReactorSC.PHASE_COMPRESS) {
            add(k, l, 3, Lang.tr("sc.gui.sing.st.compress", time(sing.getPhaseTicks() / 20.0)));
        }
        if (!sing.isReady()) {
            add(k, l, 2, Lang.tr("sc.gui.sing.st.broken"));
        }
        if (sing.isHeliumShort()) {
            add(k, l, 2, Lang.tr("sc.gui.sing.st.nohe"));
        }
        if (sing.isDeuteriumShort()) {
            add(k, l, 2, Lang.tr("sc.gui.sing.st.nod"));
        }
        double m = mass();
        if (sing.hasHole() && m < SingularReactorSC.LIGHT) {
            add(k, l, 2, Lang.tr("sc.gui.sing.st.light", pct(m), Math.round(SingularReactorSC.EVAP_MASS * 100)));
        } else if (sing.hasHole() && m < SingularReactorSC.WINDOW_LO) {
            add(k, l, 1, Lang.tr("sc.gui.sing.st.lowmass", pct(m)));
        } else if (sing.hasHole() && m > SingularReactorSC.WINDOW_HI) {
            add(k, l, 1, Lang.tr("sc.gui.sing.st.heavy", pct(m)));
        }
        float c = sing.getContainment();
        if (c < 40F) {
            add(k, l, c < 20F ? 2 : 1, Lang.tr("sc.gui.sing.st.cont", Math.round(c), Math.round(SingularReactorSC.CONT_ARGON)));
        }
        if (sing.isCapsuleShort()) {
            add(k, l, 1, Lang.tr("sc.gui.sing.st.nocapsule.run"));
        }
        if (s == GeneratorStatus.BUFFER_FULL) {
            add(k, l, 1, Lang.tr("sc.gui.sing.st.full"));
        }
        if (!sing.isHeliumShort() && lasts(0) < HE_WARN_SECONDS && gasRate[0] <= 0) {
            add(k, l, 1, Lang.tr("sc.gui.xv.st.he.soon", time(lasts(0)), HE_WARN_SECONDS / 60));
        }
        if (sing.getPortCap(2) <= 0) {
            add(k, l, 1, Lang.tr("sc.gui.sing.st.noar"));
        }
        boolean bad = false;
        for (int[] kind : k) {
            bad |= kind[0] == 2;
        }
        if (!bad && phase == SingularReactorSC.PHASE_RUN) {
            k.add(0, new int[]{0});
            l.add(0, Lang.tr("sc.gui.sing.st.run", gen.getLastOutput(), pct(m)));
        } else if (phase == SingularReactorSC.PHASE_DRAIN) {
            k.add(0, new int[]{1});
            l.add(0, Lang.tr("sc.gui.sing.st.draining", time(sing.getPhaseTicks() / 20.0), Math.round(SingularReactorSC.RETURN_SHARE * 100)));
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
            float lvl = sing.getStoresRoom() <= 0 ? 0F : (float) sing.getStoresHave() / sing.getStoresRoom();
            t.add(Lang.tr("sc.gui.xv.tip.energy.1", sing.getStores(), Math.round(lvl * 100)));
            t.add(eu(sing.getStoresHave()) + " / " + eu(sing.getStoresRoom()) + " EU");
            t.add("§7" + Lang.tr("sc.gui.sing.tip.energy.2"));
            return t;
        }
        if (isOverButton(ContainerGeneratorSC.BTN_SING_FEED, mx, my)) {
            t.add(Lang.tr("sc.gui.sing.tip.feed"));
            for (int i = 0; i < 3; i++) {
                t.add((i == sing.getFeedMode() ? "§a" : "§7") + Lang.tr("sc.gui.sing.tip.feed." + i, SingularReactorSC.FEED_TICKS[i] / 1200.0 == 7.5
                        ? "7,5" : String.valueOf(SingularReactorSC.FEED_TICKS[i] / 1200)));
            }
            t.add("§7" + Lang.tr("sc.gui.sing.tip.feed.note"));
            return t;
        }
        if (isOverButton(ContainerGeneratorSC.BTN_SING_AUTO, mx, my)) {
            t.add(Lang.tr("sc.gui.sing.tip.auto", Math.round(SingularReactorSC.AUTO_TARGET * 100)));
            t.add("§7" + Lang.tr("sc.gui.sing.tip.auto.1"));
            return t;
        }
        if (isOverButton(ContainerGeneratorSC.BTN_SING_LIGHT, mx, my)) {
            t.add(Lang.tr("sc.gui.sing.tip.light", eu(SingularReactorSC.IGNITION_EU), k(SingularReactorSC.D_IGNITION), SingularReactorSC.COMPRESS_TICKS / 20));
            return t;
        }
        if (isOverButton(ContainerGeneratorSC.BTN_SING_STOP, mx, my)) {
            t.add(Lang.tr("sc.gui.sing.tip.stop", SingularReactorSC.DRAIN_TICKS / 20, Math.round(SingularReactorSC.RETURN_SHARE * 100)));
            return t;
        }
        if (sing.hasHole() && GuiGaugeSC.isOver(SCALE_X - 2, SCALE_Y, 20, SCALE_H, mx, my)) {
            t.add(Lang.tr("sc.gui.sing.tip.scale", pct(mass())));
            t.add("§c" + Lang.tr("sc.gui.sing.tip.scale.light"));
            t.add("§6" + Lang.tr("sc.gui.sing.tip.scale.low"));
            t.add("§a" + Lang.tr("sc.gui.sing.tip.scale.window"));
            t.add("§7" + Lang.tr("sc.gui.sing.tip.scale.heavy"));
            return t;
        }
        if (shownHole() && GuiGaugeSC.isOver(C1, SCENE_Y, SCENE_W, SCENE_H, mx, my)) {
            t.add(Lang.tr("sc.gui.sing.tip.scene"));
            t.add("§7" + Lang.tr("sc.gui.sing.tip.scene.1", sing.getCoilCount()));
            return t;
        }
        if (sing.hasHole() && GuiGaugeSC.isOver(C1, GRAPH_Y - 6, SCENE_W, GRAPH_H + 6, mx, my)) {
            t.add(Lang.tr("sc.gui.sing.tip.graph"));
            t.add("§7" + Lang.tr("sc.gui.sing.tip.graph.1"));
            return t;
        }
        if (sing.hasHole() && GuiGaugeSC.isOver(C1, ROW_Y, SCENE_W, 32, mx, my)) {
            double m = mass();
            t.add(Lang.tr("sc.gui.sing.tip.mass", pct(m), num(SingularReactorSC.powerFactor(m), 1), SingularReactorSC.outputFor(m)));
            t.add(Lang.tr("sc.gui.sing.tip.cont", Math.round(sing.getContainment())));
            t.add("§7" + Lang.tr("sc.gui.sing.tip.cont.1"));
            t.add("§7" + Lang.tr("sc.gui.sing.tip.hawking"));
            return t;
        }
        if (!shownHole() && GuiGaugeSC.isOver(C1, 112, 110, 10, mx, my)) {
            t.add(Lang.tr("sc.gui.ignition"));
            t.add(gen.getIgnitionEU() + " / " + SingularReactorSC.IGNITION_EU + " EU");
            t.add("§7" + Lang.tr("sc.gui.big.tip.charge"));
            return t;
        }
        for (int i = 0; i < 3; i++) {
            if (GuiGaugeSC.isOver(C3 + i * TANK_STEP, TANK_Y, 31, 88, mx, my)) {
                return tankTip(i);
            }
        }
        if (GuiGaugeSC.isOver(SLOT_X - 2, SLOT_Y + 18, 24, 18, mx, my)) {
            t.add(Lang.tr("sc.gui.sing.tip.injector", Math.round(sing.getCapsuleLeft() / SingularReactorSC.CAPSULE_MASS * 100)));
            t.add(Lang.tr("sc.gui.sing.tip.injector.1", Math.round(SingularReactorSC.CAPSULE_MASS * 100), "~" + time(capsulesLast())));
            return t;
        }
        if (GuiGaugeSC.isOver(C2, SCHEME_Y, 7 * CELL, 7 * CELL, mx, my)) {
            return cellTip((mx - C2) / CELL - 3, (my - SCHEME_Y) / CELL - 3);
        }
        return null;
    }

    private boolean isOverButton(int id, int mx, int my) {
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id == id) {
                return GuiGaugeSC.isOver(b.xPosition - guiLeft, b.yPosition - guiTop, b.width, b.height, mx, my);
            }
        }
        return false;
    }

    private List<String> tankTip(int gas) {
        List<String> t = new ArrayList<String>();
        int cap = sing.getPortCap(gas), mb = sing.getPortFluid(gas);
        Fluid f = FluidRegistry.getFluid(SingularReactorSC.GASES[gas]);
        String name = f == null ? SingularReactorSC.GASES[gas] : new FluidStack(f, 1).getLocalizedName();
        if (cap <= 0) {
            t.add("§c" + Lang.tr("sc.gui.xv.tip.notank", name));
            t.add(Lang.tr("sc.gui.xv.tip.notank.1"));
            t.add("§7" + Lang.tr("sc.gui.sing.tip.why." + gas));
            return t;
        }
        t.add(name + " §7- " + Lang.tr("sc.gui.sing.tip.role." + gas));
        t.add(Lang.tr("sc.gui.xv.tip.amount", mb, cap, sing.getPortCount(gas)));
        if (gasRate[gas] != 0) {
            t.add((gasRate[gas] > 0 ? "§a" : "§e") + Lang.tr("sc.gui.xv.tip.flow", (gasRate[gas] > 0 ? "+" : "") + gasRate[gas]));
        }
        if (gas == 2) {
            t.add(Lang.tr("sc.gui.xv.tip.ar", SingularReactorSC.ARGON_STOP, (int) lasts(2)));
        } else if (mb > 0) {
            t.add(Lang.tr("sc.gui.sing.tip.rate." + gas, time(lasts(gas))));
        } else {
            t.add("§c" + Lang.tr("sc.gui.sing.tip.empty." + gas));
        }
        return t;
    }

    private List<String> cellTip(int dx, int dz) {
        List<String> t = new ArrayList<String>();
        int kind = cellKind(dx, dz), dy = layer - 2;
        String at = (gen.xCoord + dx) + " " + (gen.yCoord + dy) + " " + (gen.zCoord + dz);
        switch (kind) {
            case 0:
                t.add("§c" + Lang.tr(SingularReactorSC.cellRole(dx, dy, dz) == 2 ? "sc.gui.sing.tip.cell.nocoil" : "sc.gui.xv.tip.cell.nolead"));
                break;
            case 1:
                t.add(Lang.tr("sc.gui.xv.tip.cell.lead"));
                break;
            case 2:
                t.add(Lang.tr("sc.gui.sing.tip.cell.coil"));
                break;
            case 3:
                t.add(GeneratorType.SINGULAR_REACTOR.localizedName());
                break;
            case 5:
                t.add(Lang.tr("sc.gui.sing.tip.cell.empty"));
                break;
            case 6:
                t.add("§6" + Lang.tr("sc.gui.sing.tip.cell.junk"));
                break;
            default:
                int lab = sing.getLabel((layer - 1) * 24 + SingularReactorSC.wallIndex(dx, dz));
                t.add(Lang.tr("sc.gui.sing.tip.port." + lab));
        }
        t.add("§7" + Lang.tr("sc.gui.sing.layer." + layer) + " - " + at);
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
