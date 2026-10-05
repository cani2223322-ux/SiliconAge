package com.sc.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.sc.energy.ForeignEnergySC;
import com.sc.energy.ForeignEnergySC.Kind;
import com.sc.item.ItemConverterModuleSC;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityEnergyConverterSC;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The Energy Converter's screen (docs/energy-converter/conv2_RF.png, conv2_J_balance.png), 340 x 262:
 * left the six expansion slots, the charge slot and a summary (tier, packets, loss); top the pair
 * buttons «EU-RF / EU-J / EU-gJ» and the direction «EU → X / X → EU / Баланс»; centre the faces' table
 * (Сторона / Режим / Буфер - or the input filter - / Сосед / Поток); right two gauges - EU yellow, the
 * pair's energy in its colour - with the conversion's animated arrow and its loss between them; under the
 * table what is converted now, the throughput and the buffers, the flow over 30 seconds; at the bottom
 * the inventory, «Приоритет выхода», «Фильтр входа…», «Компаратор» and hints. A screen lower than 262
 * GUI pixels gets the compact 340 x 222 arrangement (no graph, tighter rows). Every part has a tooltip.
 */
public class GuiEnergyConverterSC extends GuiContainer {

    private static final int BG = 0xFF22262D, EDGE = 0xFF4A5260, EDGE_HI = 0xFF5E6672, LINE = 0xFF3A414C, TITLE = 0xFFD84A,
            LABEL = 0xC8CCD2, DIM = 0x8A9099, TEXT = 0xE6E8EC, GREEN = 0x5AE66E, ORANGE = 0xFFB040, RED = 0xFF6A6A,
            EU_COL = 0xFFE0C040, GREY = 0x9AA0A8;
    /** Screen-only ids: the filter view switch. */
    private static final int ID_FILTER_VIEW = 71;
    private static final String[] SIDE_KEYS = {"down", "up", "north", "south", "west", "east"};
    /** The table's rows top to bottom: Верх, Низ, Север, Юг, Запад, Восток. */
    private static final int[] ROW_SIDE = {1, 0, 2, 3, 4, 5};

    /** One layout (screen-local GUI pixels). */
    static final class Lay {
        int h, rowY0, rowStep, btnH, nowY, thrY, graphY, graphH, sepY, invY, hotbarY, prioY, filtY, hintY, hintStep, swapY;
        int gaugeY, gaugeH, chargeLabelY, chargeY, sumY;
    }

    static final Lay BIG = big(), SMALL = small();
    public static final int W = 340;

    private static Lay big() {
        Lay l = new Lay();
        l.h = 262;
        l.rowY0 = 55; l.rowStep = 13; l.btnH = 10;
        l.nowY = 138; l.thrY = 147; l.graphY = 155; l.graphH = 20;
        l.sepY = 178; l.invY = 183; l.hotbarY = 241;
        l.prioY = 182; l.filtY = 198; l.hintY = 215; l.hintStep = 9; l.swapY = 238;
        l.gaugeY = 29; l.gaugeH = 70; l.chargeLabelY = 93; l.chargeY = 102; l.sumY = 124;
        return l;
    }

    private static Lay small() {
        Lay l = new Lay();
        l.h = 222;
        l.rowY0 = 54; l.rowStep = 11; l.btnH = 9;
        l.nowY = 122; l.thrY = 131; l.graphY = 0; l.graphH = 0;
        l.sepY = 140; l.invY = 144; l.hotbarY = 202;
        l.prioY = 144; l.filtY = 159; l.hintY = 176; l.hintStep = 9; l.swapY = 198;
        l.gaugeY = 29; l.gaugeH = 56; l.chargeLabelY = 88; l.chargeY = 96; l.sumY = 116;
        return l;
    }

    private static final int GAUGE_EU_X = 270, GAUGE_X_X = 308, GAUGE_W = 23, TABLE_X = 54, MODE_X = 86, MODE_W = 33, BUF_X = 122, BUF_W = 31,
            NB_X = 158, NB_W = 74, FLOW_R = 258, RIGHT_X = 176, RIGHT_W = 156;

    private Lay L = BIG;
    private final TileEntityEnergyConverterSC te;
    private GuiPowerSC power;
    private boolean filterView;
    private final Btn[] pairBtn = new Btn[3], dirBtn = new Btn[3], modeBtn = new Btn[6], bufBtn = new Btn[6];
    private Btn prioBtn, filterBtn, compBtn;

    public GuiEnergyConverterSC(InventoryPlayer inv, TileEntityEnergyConverterSC te) {
        super(new ContainerEnergyConverterSC(inv, te));
        this.te = te;
        xSize = W;
        ySize = BIG.h;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        L = height >= BIG.h ? BIG : SMALL;
        xSize = W;
        ySize = L.h;
        super.initGui();
        buttonList.clear();
        int x = guiLeft, y = guiTop;
        power = new GuiPowerSC(te, ContainerEnergyConverterSC.BTN_POWER, ContainerEnergyConverterSC.BTN_REDSTONE, 294, 4, 37, L.gaugeY, L.gaugeH);
        power.addButtons(buttonList, x, y);
        int[] px = {54, 84, 114};
        for (int k = 0; k < 3; k++) {
            pairBtn[k] = add(new Btn(ContainerEnergyConverterSC.BTN_PAIR + k, x + px[k], y + 29, 28, 11));
        }
        int[] dx = {150, 184, 218}, dw = {31, 31, 32};
        for (int k = 0; k < 3; k++) {
            dirBtn[k] = add(new Btn(ContainerEnergyConverterSC.BTN_DIR + k, x + dx[k], y + 29, dw[k], 11));
        }
        for (int r = 0; r < 6; r++) {
            int s = ROW_SIDE[r], ry = y + L.rowY0 + r * L.rowStep;
            modeBtn[s] = add(new Btn(ContainerEnergyConverterSC.BTN_MODE + s, x + MODE_X, ry, MODE_W, L.btnH));
            bufBtn[s] = add(new Btn(ContainerEnergyConverterSC.BTN_BUF + s, x + BUF_X, ry, BUF_W, L.btnH));
        }
        prioBtn = add(new Btn(ContainerEnergyConverterSC.BTN_PRIORITY, x + RIGHT_X, y + L.prioY, RIGHT_W, 13));
        filterBtn = add(new Btn(ID_FILTER_VIEW, x + RIGHT_X, y + L.filtY, 75, 13));
        compBtn = add(new Btn(ContainerEnergyConverterSC.BTN_COMPARATOR, x + RIGHT_X + 81, y + L.filtY, RIGHT_W - 81, 13));
        placeSlots();
    }

    @SuppressWarnings("unchecked")
    private Btn add(Btn b) {
        buttonList.add(b);
        return b;
    }

    /** The slots where this layout puts them (the container has the large layout's places). */
    private void placeSlots() {
        List<?> slots = inventorySlots.inventorySlots;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = (Slot) slots.get(i);
            if (s.inventory == te) {
                if (s.getSlotIndex() == TileEntityEnergyConverterSC.SLOT_CHARGE) {
                    s.yDisplayPosition = L.chargeY;
                }
            } else {
                int index = s.getSlotIndex();
                s.yDisplayPosition = index < 9 ? L.hotbarY : L.invY + (index / 9 - 1) * 18;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_FILTER_VIEW) {
            filterView = !filterView;
            return;
        }
        if (button.id >= ContainerEnergyConverterSC.BTN_BUF && button.id < ContainerEnergyConverterSC.BTN_BUF + 6 && filterView) {
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, ContainerEnergyConverterSC.BTN_FILTER + button.id - ContainerEnergyConverterSC.BTN_BUF);
            return;
        }
        if (button.id == ContainerEnergyConverterSC.BTN_POWER || button.id == ContainerEnergyConverterSC.BTN_REDSTONE) {
            if (!power.allowClick(button)) {
                return;
            }
        }
        mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
    }

    // ------------------------------------------------------------------ helpers

    private Kind kind() {
        return te.pairKind();
    }

    private String unit() {
        Kind k = kind();
        return k == null ? "-" : k.unit;
    }

    private int xColour() {
        Kind k = kind();
        return k == null ? 0xFF6A6E74 : k.colour;
    }

    /** 1234567 -> "1 234 567". */
    static String num(long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n).replace(',', ' ');
    }

    /** "1 млн", "4 млн", "2,56 млрд", smaller numbers spaced. */
    static String big(double n) {
        if (n >= 1e9) {
            return Lang.tr("sc.storage.gui.bn", trim(String.format(java.util.Locale.ROOT, "%.2f", n / 1e9)));
        }
        if (n >= 1e6) {
            return Lang.tr("sc.storage.gui.mn", trim(String.format(java.util.Locale.ROOT, "%.2f", n / 1e6)));
        }
        return num(Math.round(n));
    }

    private static String trim(String v) {
        if (v.indexOf('.') >= 0) {
            v = v.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return v.replace('.', ',');
    }

    static String rate(double r) {
        return com.sc.block.ItemBlockEnergyConverterSC.trimRate(r);
    }

    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    private void small(String text, int x, int y, int maxW, int color) {
        float k = Math.min(0.75F, maxW / (float) Math.max(1, fontRendererObj.getStringWidth(text)));
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            drawRect(x, y, x + w, y + h, c);
        }
    }

    private static void pocket(int x, int y) {
        rect(x - 1, y - 1, 18, 18, 0xFF15181C);
        rect(x, y, 17, 17, 0xFF9A9EA4);
        rect(x, y, 16, 16, 0xFF7C8086);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        rect(x, y, W, L.h, 0xFF101216);
        rect(x + 1, y + 1, W - 2, L.h - 2, EDGE);
        rect(x + 2, y + 2, W - 4, L.h - 4, BG);
        rect(x + 6, y + L.sepY, W - 12, 1, EDGE_HI);
        for (int i = 0; i < TileEntityEnergyConverterSC.MODULE_SLOTS; i++) {
            pocket(x + ContainerEnergyConverterSC.modX(i), y + ContainerEnergyConverterSC.modY(i));
        }
        pocket(x + ContainerEnergyConverterSC.CHARGE_X, y + L.chargeY);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                pocket(x + ContainerEnergyConverterSC.INV_X + c * 18, y + L.invY + r * 18);
            }
        }
        for (int c = 0; c < 9; c++) {
            pocket(x + ContainerEnergyConverterSC.INV_X + c * 18, y + L.hotbarY);
        }
        // the gauges
        double euCap = te.getMaxEnergyStored(), xCap = te.foreignCapacity();
        gauge(x + GAUGE_EU_X, y + L.gaugeY, GAUGE_W, L.gaugeH, euCap <= 0 ? 0 : te.getEnergyStored() / euCap, EU_COL);
        gauge(x + GAUGE_X_X, y + L.gaugeY, GAUGE_W, L.gaugeH, xCap <= 0 ? 0 : te.getForeign() / xCap, xColour());
        arrow(x + 294, y + L.gaugeY + L.gaugeH / 2 - 5, partialTicks);
        // the graph
        if (L.graphH > 0) {
            graph(x + TABLE_X, y + L.graphY, 204, L.graphH);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** A machine-style vertical gauge: a frame, the fill in `col`, a light level line, ticks on the right. */
    private static void gauge(int x, int y, int w, int h, double f, int col) {
        f = Math.max(0, Math.min(1, f));
        rect(x, y, w, h, 0xFF101216);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF5A606A);
        rect(x + 2, y + 2, w - 4, h - 4, 0xFF0A0C10);
        int ih = h - 4, fill = (int) Math.round(ih * f);
        if (fill > 0) {
            int top = y + 2 + ih - fill;
            GuiEnergyGaugeSC.gradient(x + 2, top, w - 4, fill, shade(col, 1.0F), shade(col, 0.55F));
            rect(x + 2, top, w - 4, 1, 0xFFF4F4F4);
            rect(x + 2, top + 1, w - 4, 1, 0x60FFFFFF);
        }
        for (int i = 1; i < 10; i++) {
            int ty = y + 2 + ih - Math.round(ih * i / 10F);
            rect(x + w - (i == 5 ? 7 : 5), ty, i == 5 ? 5 : 3, 1, 0xFF6A707A);
        }
    }

    private static int shade(int c, float k) {
        int r = (int) (((c >> 16) & 255) * k), g = (int) (((c >> 8) & 255) * k), b = (int) ((c & 255) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Which way the conversion goes now: 1 EU -> X, -1 X -> EU, 0 still (the direction button's way, dimmed). */
    private int flowSign() {
        int c = te.statConvEu();
        if (c != 0) {
            return c > 0 ? 1 : -1;
        }
        return te.getDirection() == TileEntityEnergyConverterSC.DIR_X_TO_EU ? -1 : te.getDirection() == TileEntityEnergyConverterSC.DIR_EU_TO_X ? 1 : 0;
    }

    /** The arrow between the gauges, its chevron running the way the energy goes (still when nothing moves). */
    private void arrow(int x, int y, float pt) {
        int sign = flowSign();
        boolean moving = te.statConvEu() != 0 && te.isPowerOn();
        int col = moving ? 0xFFF0F0F0 : 0xFF6A6E74;
        int barCol = moving ? (sign > 0 ? EU_COL : xColour()) : 0xFF3A3E44;
        rect(x, y + 3, 12, 4, barCol);
        if (sign == 0) {
            rect(x + 3, y, 6, 1, col);
            rect(x + 3, y + 9, 6, 1, col);
            return;
        }
        float t = mc.theWorld == null ? 0 : (mc.theWorld.getTotalWorldTime() % 20 + pt) / 20F;
        int off = moving ? (int) (t * 6) : 3;
        for (int i = 0; i < 5; i++) {
            int len = 5 - Math.abs(2 - i) * 2;
            int cx = sign > 0 ? x + off + (2 - Math.abs(2 - i)) : x + 11 - off - (2 - Math.abs(2 - i));
            rect(Math.max(x, Math.min(x + 11, cx)), y + 2 + i, 1, 1, col);
            if (len > 0) {
                rect(Math.max(x, Math.min(x + 11, cx - sign)), y + 2 + i, 1, 1, col);
            }
        }
    }

    /** The flow over the last 30 seconds: EU (yellow) and the other energy in EU worth (its colour). */
    private void graph(int x, int y, int w, int h) {
        rect(x, y, w, h, 0xFF4A5260);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF0B0D10);
        int n = TileEntityEnergyConverterSC.GRAPH;
        long max = Math.max(1, te.throughput());
        for (int i = 0; i < n; i++) {
            max = Math.max(max, Math.max(te.graphEu(i), te.graphX(i)));
        }
        line(x + 2, y + 2, w - 4, h - 4, max, true, EU_COL);
        line(x + 2, y + 2, w - 4, h - 4, max, false, xColour());
    }

    private void line(int x, int y, int w, int h, long max, boolean eu, int col) {
        int n = TileEntityEnergyConverterSC.GRAPH, head = te.graphHead();
        int prev = -1;
        for (int k = 0; k < n; k++) {
            int i = (head + 1 + k) % n;
            int v = eu ? te.graphEu(i) : te.graphX(i);
            int py = y + h - 1 - (int) Math.round((h - 1) * Math.min(1.0, v / (double) max));
            int px = x + k * (w - 1) / (n - 1), nx = x + (k + 1) * (w - 1) / (n - 1);
            if (prev >= 0) {
                rect(px, Math.min(prev, py), 1, Math.abs(prev - py) + 1, 0xFF000000 | col);
            }
            if (k < n - 1) {
                rect(px, py, Math.max(1, nx - px), 1, 0xFF000000 | col);
            }
            prev = py;
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        Kind k = kind();
        fit(Lang.tr("tile.siliconage.energyConverter.name"), 8, 6, 280, TITLE);
        fontRendererObj.drawString(Lang.tr("sc.conv.gui.modules"), 8, 20, LABEL);
        fontRendererObj.drawString(Lang.tr("sc.conv.gui.pair"), TABLE_X, 20, LABEL);
        fontRendererObj.drawString(Lang.tr("sc.conv.gui.direction"), 150, 20, LABEL);
        // the pair and direction captions
        Kind[] kinds = Kind.values();
        for (int i = 0; i < 3; i++) {
            Kind ki = kinds[i];
            boolean sel = k == ki, can = te.pairAvailable(ki);
            pairBtn[i].set("EU-" + ki.unit, sel ? (ki.colour & 0xFFFFFF) : can ? TEXT : 0x6A6E74, sel, !can && !sel);
        }
        String u = unit();
        String[] dirs = {"EU → " + u, u + " → EU", Lang.tr("sc.conv.gui.balance")};
        for (int i = 0; i < 3; i++) {
            boolean sel = te.getDirection() == i;
            dirBtn[i].set(dirs[i], sel ? GREEN : TEXT, sel, k == null);
        }
        // the table
        int hy = L.rowY0 - 9;
        fontRendererObj.drawString(Lang.tr("sc.conv.gui.side"), TABLE_X, hy, LABEL);
        fontRendererObj.drawString(Lang.tr("sc.conv.gui.mode"), MODE_X + 2, hy, LABEL);
        fontRendererObj.drawString(Lang.tr(filterView ? "sc.conv.gui.filter" : "sc.conv.gui.buffer"), BUF_X + 2, hy, filterView ? ORANGE : LABEL);
        fontRendererObj.drawString(Lang.tr("sc.conv.gui.neighbour"), NB_X, hy, LABEL);
        String flowHead = Lang.tr("sc.conv.gui.flow");
        fontRendererObj.drawString(flowHead, FLOW_R - fontRendererObj.getStringWidth(flowHead), hy, LABEL);
        for (int r = 0; r < 6; r++) {
            int s = ROW_SIDE[r], ry = L.rowY0 + r * L.rowStep;
            int ty = ry + (L.btnH - 8) / 2 + 1;
            fit(Lang.tr("sc.conv.side." + SIDE_KEYS[s]), TABLE_X, ty, MODE_X - TABLE_X - 2, TEXT);
            int m = te.getMode(s);
            modeBtn[s].set(Lang.tr("sc.conv.mode." + m), m == TileEntityEnergyConverterSC.MODE_IN ? GREEN
                    : m == TileEntityEnergyConverterSC.MODE_OUT ? ORANGE : GREY, false, false);
            if (filterView) {
                bufBtn[s].set(filterText(s), ORANGE, false, false);
            } else if (m == TileEntityEnergyConverterSC.MODE_OFF) {
                bufBtn[s].set("—", GREY, false, true);
            } else {
                int b = te.getBuf(s);
                String t = b == TileEntityEnergyConverterSC.BUF_AUTO ? Lang.tr("sc.conv.buf.auto") : b == TileEntityEnergyConverterSC.BUF_EU ? "EU" : u;
                int c = b == TileEntityEnergyConverterSC.BUF_AUTO ? TITLE : b == TileEntityEnergyConverterSC.BUF_EU ? (EU_COL & 0xFFFFFF) : (xColour() & 0xFFFFFF);
                bufBtn[s].set(t, c, false, false);
            }
            fit(neighbour(s), NB_X, ty, NB_W, LABEL);
            String flow = m == TileEntityEnergyConverterSC.MODE_OFF ? "—" : sideFlow(s);
            int fw = Math.min(fontRendererObj.getStringWidth(flow), FLOW_R - NB_X - NB_W - 2);
            fit(flow, FLOW_R - fw, ty, FLOW_R - NB_X - NB_W - 2, te.statSide(s) > 0 ? GREEN : te.statSide(s) < 0 ? ORANGE : TEXT);
        }
        // now, throughput, buffers
        double rate = te.rate();
        int loss = te.lossPercent();
        String rateText = rate(rate);
        String now;
        if (k == null) {
            now = Lang.tr("sc.conv.gui.nopair");
        } else if (te.statConvEu() > 0) {
            now = Lang.tr("sc.conv.gui.now", num(te.statConvEu()) + " EU/t", num(Math.abs(te.statConvX())) + " " + u + "/t", rateText, String.valueOf(loss));
        } else if (te.statConvEu() < 0) {
            now = Lang.tr("sc.conv.gui.now", num(Math.abs(te.statConvX())) + " " + u + "/t", num(-te.statConvEu()) + " EU/t", rateText, String.valueOf(loss));
        } else {
            now = Lang.tr("sc.conv.gui.idle", rateText, String.valueOf(loss));
        }
        fit(now, TABLE_X, L.nowY, GAUGE_EU_X - TABLE_X - 4, TEXT);
        int thr = te.throughput();
        String thrText = k == null ? Lang.tr("sc.conv.gui.thr.eu", num(thr), big(te.getMaxEnergyStored()))
                : Lang.tr("sc.conv.gui.thr", num(thr), num(Math.round(thr * rate)) + " " + u, big(te.getMaxEnergyStored()), big(te.foreignCapacity()) + " " + u);
        fit(thrText, TABLE_X, L.thrY, GAUGE_EU_X - TABLE_X - 4, DIM);
        if (L.graphH > 0) {
            small(Lang.tr("sc.conv.gui.graph"), TABLE_X + 3, L.graphY + 2, 80, DIM);
        }
        // the gauges' captions
        centred("EU", GAUGE_EU_X, 21, GAUGE_W, EU_COL & 0xFFFFFF);
        centred(u, GAUGE_X_X, 21, GAUGE_W, xColour() & 0xFFFFFF);
        gaugeText(GAUGE_EU_X, te.getEnergyStored(), te.getMaxEnergyStored(), "EU", te.statEuPlus(), te.statEuMinus());
        if (k != null) {
            gaugeText(GAUGE_X_X, te.getForeign(), te.foreignCapacity(), u, te.statXPlus(), te.statXMinus());
        } else {
            centred("—", GAUGE_X_X, L.gaugeY + L.gaugeH + 2, GAUGE_W, GREY);
        }
        if (k != null) {
            String lossText = "−" + loss + "%";
            small(lossText, 297, L.gaugeY + L.gaugeH / 2 + 8, 14, RED);
        }
        if (!te.isPowerOn()) {
            rect(GAUGE_EU_X + 2, L.gaugeY + 2, GAUGE_X_X + GAUGE_W - GAUGE_EU_X - 4, L.gaugeH - 4, 0xA0000000);
            centred(Lang.tr("sc.gui.power.label"), GAUGE_EU_X, L.gaugeY + L.gaugeH / 2 - 4, GAUGE_X_X + GAUGE_W - GAUGE_EU_X, 0xB0B8C4);
        }
        // left: the charge slot, the summary
        fontRendererObj.drawString(Lang.tr("sc.conv.gui.charge"), 8, L.chargeLabelY, LABEL);
        small(Lang.tr("sc.conv.gui.sum.tier", te.workTier().name()), 8, L.sumY, 44, LABEL);
        small(Lang.tr("sc.conv.gui.sum.packets", te.packets()), 8, L.sumY + 7, 44, LABEL);
        small(Lang.tr("sc.conv.gui.sum.loss", loss), 8, L.sumY + 14, 44, LABEL);
        // the inert cards: red, crossed out
        for (int i = 0; i < TileEntityEnergyConverterSC.MODULE_SLOTS; i++) {
            ItemStack s = te.getStackInSlot(TileEntityEnergyConverterSC.FIRST_MODULE + i);
            if (s != null && s.getItem() instanceof ItemConverterModuleSC && ItemConverterModuleSC.kindOf(s).inert()) {
                int sx = ContainerEnergyConverterSC.modX(i), sy = ContainerEnergyConverterSC.modY(i);
                GL11.glPushMatrix();
                GL11.glTranslatef(0F, 0F, 300F);
                rect(sx, sy, 16, 16, 0x70FF2020);
                for (int d = 0; d < 16; d++) {
                    rect(sx + d, sy + d, 1, 1, 0xFFFF3030);
                    rect(sx + 15 - d, sy + d, 1, 1, 0xFFFF3030);
                }
                GL11.glPopMatrix();
            }
        }
        // bottom right
        String first = te.getOutPriority() == TileEntityEnergyConverterSC.PRIO_EU ? "EU → " + u : u + " → EU";
        prioBtn.set(Lang.tr("sc.conv.gui.priority", first), TEXT, false, false);
        filterBtn.set(Lang.tr("sc.conv.gui.filterbtn"), filterView ? ORANGE : TEXT, filterView, false);
        compBtn.set(Lang.tr("sc.conv.gui.comparator", te.getComparatorOf() == TileEntityEnergyConverterSC.COMP_EU ? "EU" : u), TEXT, false, false);
        List<String> hint = fontRendererObj.listFormattedStringToWidth(Lang.tr("sc.conv.hint.dir." + te.getDirection(), u), RIGHT_W);
        for (int i = 0; i < Math.min(2, hint.size()); i++) {
            fontRendererObj.drawString(hint.get(i), RIGHT_X, L.hintY + i * L.hintStep, DIM);
        }
        if (k != null) {
            fit(Lang.tr("sc.conv.hint.swap", u, String.valueOf(loss)), RIGHT_X, L.swapY, RIGHT_W, DIM);
        }
    }

    private void centred(String t, int x, int y, int w, int col) {
        TextFitSC.drawCentered(fontRendererObj, t, x, y, w, col, false, guiLeft, guiTop);
    }

    /** Under a gauge: %, the amount, of the capacity, + in, - out a tick. */
    private void gaugeText(int gx, double v, double cap, String unit, int plus, int minus) {
        int y = L.gaugeY + L.gaugeH + 2, cx = gx + GAUGE_W / 2, w = 36;
        String pct = (cap <= 0 ? 0 : Math.round(100 * v / cap)) + "%";
        centred(pct, cx - w / 2, y, w, 0xFFFFFF);
        smallCentred(big(v), cx, y + 9, w, TEXT);
        smallCentred("/ " + big(cap) + " " + unit, cx, y + 16, w, DIM);
        smallCentred("+" + num(plus) + "/t", cx, y + 23, w, GREEN);
        smallCentred("−" + num(minus) + "/t", cx, y + 30, w, ORANGE);
    }

    private void smallCentred(String t, int cx, int y, int maxW, int col) {
        float k = Math.min(0.75F, maxW / (float) Math.max(1, fontRendererObj.getStringWidth(t)));
        int w = (int) (fontRendererObj.getStringWidth(t) * k);
        GL11.glPushMatrix();
        GL11.glTranslatef(cx - w / 2F, y, 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(t, 0, 0, col);
        GL11.glPopMatrix();
    }

    private String filterText(int s) {
        int f = te.getFilter(s);
        Kind k = kind();
        boolean eu = (f & TileEntityEnergyConverterSC.F_EU) != 0, x = k != null && (f & TileEntityEnergyConverterSC.filterBit(k)) != 0;
        if (eu && x) {
            return "EU+" + k.unit;
        }
        return eu ? "EU" : x ? k.unit : "—";
    }

    /** A face's flow a tick in what it carries: "+512", "-3 891" (EU or the pair's units). */
    private String sideFlow(int s) {
        int v = te.statSide(s);
        return v == 0 ? "0" : (v > 0 ? "+" : "−") + num(Math.abs(v));
    }

    // ------------------------------------------------------------------ the neighbour column

    private static final Map<Class<?>, Integer> KINDS = new HashMap<Class<?>, Integer>();
    private static final int K_EU = 1, K_RF = 2, K_J = 4, K_GJ = 8, K_IC2_CABLE = 16;

    /** What the neighbour beside face `s` is: the mod's cable and its tier, an IC2 cable, or the mod and the energies it speaks. */
    private String neighbour(int s) {
        ForgeDirection d = ForgeDirection.getOrientation(s);
        int nx = te.xCoord + d.offsetX, ny = te.yCoord + d.offsetY, nz = te.zCoord + d.offsetZ;
        if (te.getWorldObj() == null || !te.getWorldObj().blockExists(nx, ny, nz)) {
            return "—";
        }
        TileEntity n = te.getWorldObj().getTileEntity(nx, ny, nz);
        if (n == null) {
            return "—";
        }
        if (n instanceof com.sc.tileentity.TileEntityConduitBundleSC) {
            com.sc.energy.CableType c = ((com.sc.tileentity.TileEntityConduitBundleSC) n).getCable();
            return c == null ? Lang.tr("sc.conv.n.noenergy", "Silicon Age") : Lang.tr("sc.conv.n.cable", c.tier.name());
        }
        if (n instanceof com.sc.tileentity.TileEntityCableSC && ((com.sc.tileentity.TileEntityCableSC) n).getCableType() != null) {
            return Lang.tr("sc.conv.n.cable", ((com.sc.tileentity.TileEntityCableSC) n).getCableType().tier.name());
        }
        if (n instanceof com.sc.energy.TileEntityEnergyBase) {
            return Lang.tr("sc.conv.n.ours", n.getBlockType() == null ? "?" : new ItemStack(n.getBlockType(), 1,
                    n.getBlockType().damageDropped(te.getWorldObj().getBlockMetadata(nx, ny, nz))).getDisplayName());
        }
        int kinds = kindsOf(n.getClass());
        String mod = modName(n);
        if ((kinds & K_IC2_CABLE) != 0) {
            return Lang.tr("sc.conv.n.ic2cable", mod);
        }
        if ((kinds & 15) == 0) {
            return Lang.tr("sc.conv.n.noenergy", mod);
        }
        StringBuilder sb = new StringBuilder();
        String[] names = {"EU", "RF", "J", "gJ"};
        for (int i = 0; i < 4; i++) {
            if ((kinds & 1 << i) != 0) {
                sb.append(sb.length() == 0 ? "" : "/").append(names[i]);
            }
        }
        return mod + " · " + sb;
    }

    /** The energies a tile class speaks, by its interfaces' names (no other mod's class is loaded for it). */
    private static int kindsOf(Class<?> c) {
        Integer cached = KINDS.get(c);
        if (cached != null) {
            return cached;
        }
        int k = 0;
        List<Class<?>> todo = new ArrayList<Class<?>>();
        for (Class<?> x = c; x != null; x = x.getSuperclass()) {
            todo.add(x);
        }
        for (int i = 0; i < todo.size(); i++) {
            Class<?> x = todo.get(i);
            String n = x.getName();
            if (n.startsWith("ic2.api.energy.tile.")) {
                k |= K_EU;
                if (n.endsWith("IEnergyConductor")) {
                    k |= K_IC2_CABLE;
                }
            } else if (n.startsWith("cofh.api.energy.IEnergy") && !n.endsWith("ContainerItem")) {
                k |= K_RF;
            } else if (n.startsWith("mekanism.api.energy.")) {
                k |= K_J;
            } else if (n.startsWith("micdoodle8.mods.galacticraft.api.transmission.tile.IElectrical")
                    || n.startsWith("micdoodle8.mods.galacticraft.api.transmission.tile.IConductor")
                    || n.startsWith("micdoodle8.mods.galacticraft.api.power.IEnergyHandlerGC")) {
                k |= K_GJ;
            }
            try {
                for (Class<?> in : x.getInterfaces()) {
                    if (!todo.contains(in)) {
                        todo.add(in);
                    }
                }
            } catch (Throwable t) {
                // an interface that won't load: skipped
            }
        }
        KINDS.put(c, k);
        return k;
    }

    /** The neighbour's mod, short: IC2, EnderIO, Thermal, Mekanism, Galacticraft... */
    private static String modName(TileEntity n) {
        String id = null;
        try {
            GameRegistry.UniqueIdentifier u = n.getBlockType() == null ? null : GameRegistry.findUniqueIdentifierFor(n.getBlockType());
            id = u == null ? null : u.modId;
        } catch (Throwable t) {
            id = null;
        }
        if (id == null) {
            return "?";
        }
        if (id.startsWith("Thermal")) {
            return "Thermal";
        }
        if (id.startsWith("Mekanism")) {
            return "Mekanism";
        }
        if (id.startsWith("Galacticraft")) {
            return "Galacticraft";
        }
        if (id.equalsIgnoreCase("industrialupgrade")) {
            return "IU";
        }
        return id;
    }

    // ------------------------------------------------------------------ tooltips

    private List<String> tooltipAt(int mx, int my) {
        List<String> p = power.tooltip(mx, my);
        if (p != null) {
            return p;
        }
        List<String> l = new ArrayList<String>();
        Kind k = kind();
        String u = unit();
        for (int i = 0; i < 3; i++) {
            if (over(pairBtn[i], mx, my)) {
                Kind ki = Kind.values()[i];
                l.add(Lang.tr("sc.conv.tip.pair", ki.unit, rate(ki.perEu())));
                if (!ki.modPresent()) {
                    l.add("§c" + Lang.tr("sc.conv.tip.pair.nomod." + ki.name().toLowerCase(java.util.Locale.ROOT)));
                } else if (!te.pairAvailable(ki)) {
                    l.add("§e" + Lang.tr("sc.conv.tip.pair.card." + ki.name().toLowerCase(java.util.Locale.ROOT)));
                }
                if (k != null && ki != k && te.getForeign() > 0) {
                    l.add("§7" + Lang.tr("sc.conv.tip.pair.refund", num(Math.round(te.getForeign())) + " " + u, num(te.refundPreview())));
                }
                return l;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (over(dirBtn[i], mx, my)) {
                l.add(Lang.tr("sc.conv.hint.dir." + i, u));
                return l;
            }
        }
        for (int s = 0; s < 6; s++) {
            if (over(modeBtn[s], mx, my)) {
                l.add(Lang.tr("sc.conv.tip.mode"));
                l.add("§7" + Lang.tr("sc.conv.tip.mode.2"));
                return l;
            }
            if (over(bufBtn[s], mx, my)) {
                if (filterView) {
                    l.add(Lang.tr("sc.conv.tip.filter", u));
                } else {
                    l.add(Lang.tr("sc.conv.tip.buf", u));
                    l.add("§7" + Lang.tr("sc.conv.tip.buf.2"));
                    int ok = te.getOutKind(s);
                    if (te.getMode(s) == TileEntityEnergyConverterSC.MODE_OUT) {
                        l.add("§e" + Lang.tr("sc.conv.tip.buf.now", ok == TileEntityEnergyConverterSC.OUT_X ? u : ok == TileEntityEnergyConverterSC.OUT_EU ? "EU" : "—"));
                    }
                }
                return l;
            }
        }
        if (GuiGaugeSC.isOver(GAUGE_EU_X, L.gaugeY, GAUGE_W, L.gaugeH, mx, my)) {
            l.add("EU: " + num(te.getEnergyStored()) + " / " + num(te.getMaxEnergyStored()));
            l.add("§a+" + num(te.statEuPlus()) + " EU/t  §6−" + num(te.statEuMinus()) + " EU/t");
            return l;
        }
        if (GuiGaugeSC.isOver(GAUGE_X_X, L.gaugeY, GAUGE_W, L.gaugeH, mx, my)) {
            if (k == null) {
                l.add(Lang.tr("sc.conv.gui.nopair"));
            } else {
                l.add(u + ": " + num(Math.round(te.getForeign())) + " / " + num(Math.round(te.foreignCapacity())));
                l.add("§a+" + num(te.statXPlus()) + " " + u + "/t  §6−" + num(te.statXMinus()) + " " + u + "/t");
                if (!te.pairActive()) {
                    l.add("§c" + Lang.tr("sc.conv.tip.blocked"));
                }
            }
            return l;
        }
        if (GuiGaugeSC.isOver(293, L.gaugeY + L.gaugeH / 2 - 6, 14, 22, mx, my)) {
            l.add(Lang.tr("sc.conv.tip.arrow", rate(te.rate()), u, te.lossPercent()));
            return l;
        }
        if (over(prioBtn, mx, my)) {
            l.add(Lang.tr("sc.conv.tip.priority"));
            return l;
        }
        if (over(filterBtn, mx, my)) {
            l.add(Lang.tr("sc.conv.tip.filterbtn"));
            return l;
        }
        if (over(compBtn, mx, my)) {
            l.add(Lang.tr("sc.conv.tip.comparator"));
            l.add("§7" + Lang.tr("sc.storage.gui.c.level", te.comparatorLevel()));
            return l;
        }
        if (GuiGaugeSC.isOver(4, 26, 46, 62, mx, my)) {
            boolean empty = true;
            for (int i = 0; i < TileEntityEnergyConverterSC.MODULE_SLOTS; i++) {
                if (GuiGaugeSC.isOver(ContainerEnergyConverterSC.modX(i), ContainerEnergyConverterSC.modY(i), 16, 16, mx, my)) {
                    empty = te.getStackInSlot(TileEntityEnergyConverterSC.FIRST_MODULE + i) == null;
                }
            }
            if (empty) {
                for (int i = 1; i <= 7; i++) {
                    l.add((i == 1 ? "" : "§7") + Lang.tr("sc.conv.tip.modules." + i));
                }
                return l;
            }
        }
        if (te.getStackInSlot(TileEntityEnergyConverterSC.SLOT_CHARGE) == null
                && GuiGaugeSC.isOver(ContainerEnergyConverterSC.CHARGE_X, L.chargeY, 16, 16, mx, my)) {
            l.add(Lang.tr("sc.conv.tip.charge"));
            return l;
        }
        if (GuiGaugeSC.isOver(4, L.sumY - 1, 46, 22, mx, my)) {
            l.add(Lang.tr("sc.conv.tip.summary", te.workTier().name(), num(te.workTier().getVoltage()), te.packets(), num(te.throughput()),
                    te.euTier().name()));
            return l;
        }
        return null;
    }

    private boolean over(GuiButton b, int mx, int my) {
        return b != null && GuiGaugeSC.isOver(b.xPosition - guiLeft, b.yPosition - guiTop, b.width, b.height, mx, my);
    }

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

    /** A flat button as on the mock-ups: grey, darker when chosen, its caption in a colour, fitted. */
    private final class Btn extends GuiButton {
        int colour = TEXT;
        boolean selected, dim;

        Btn(int id, int x, int y, int w, int h) {
            super(id, x, y, w, h, "");
        }

        void set(String text, int colour, boolean selected, boolean dim) {
            this.displayString = text;
            this.colour = colour;
            this.selected = selected;
            this.dim = dim;
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            rect(xPosition, yPosition, width, height, 0xFF15181C);
            int fill = selected ? 0xFF353A42 : over ? 0xFF7E838B : 0xFF62676F;
            rect(xPosition + 1, yPosition + 1, width - 2, height - 2, fill);
            rect(xPosition + 1, yPosition + 1, width - 2, 1, selected ? 0xFF2A2E34 : 0xFF8C9199);
            int c = dim ? 0x5A5E66 : colour;
            int tw = mc.fontRenderer.getStringWidth(displayString);
            float k = Math.min(1F, (width - 4) / (float) Math.max(1, tw));
            GL11.glPushMatrix();
            GL11.glTranslatef(xPosition + (width - tw * k) / 2F, yPosition + (height - 8 * k) / 2F + (k < 1 ? 0.5F : 0F), 0F);
            GL11.glScalef(k, k, 1F);
            mc.fontRenderer.drawString(displayString, 0, 0, c, !dim);
            GL11.glPopMatrix();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }
}
