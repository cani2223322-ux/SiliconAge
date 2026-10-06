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
 * The Energy Converter's screen (docs/energy-converter/conv3_screen.png), 340 x 262: left the six
 * expansion slots, the charge slot and a summary (tier, packets, loss); top the pair buttons
 * «EU-RF / EU-J / EU-gJ» (an unavailable one says under it what it lacks) and the direction; centre the
 * faces' table (Сторона / Режим / Буфер - or the input filter - / Сосед with a square of its energy's
 * colour / Поток), its columns measured with the font, and under it «Все: Вход / Выход / Авто»; right
 * two gauges drawn as the machines' energy gauge (GuiEnergyGaugeSC) - EU in the charge colours, the
 * pair's energy in its own colour, brighter the fuller (ForeignEnergySC.gaugeColour) - with a thick
 * arrow of the conversion's way and its loss between them; under the table what is converted now and
 * the rate / loss / throughput, the flow over 30 seconds across the whole width; at the bottom the
 * inventory, «Приоритет выхода», «Фильтр входа…», «Компаратор». The long explanations are tooltips.
 * A screen lower than 262 GUI pixels gets the compact 340 x 222 arrangement (no graph, tighter rows,
 * the «Все» buttons beside the inventory).
 */
public class GuiEnergyConverterSC extends GuiContainer {

    private static final int BG = 0xFF22262D, EDGE = 0xFF4A5260, EDGE_HI = 0xFF5E6672, TITLE = 0xFFD84A,
            LABEL = 0xC8CCD2, DIM = 0x8A9099, TEXT = 0xE6E8EC, GREEN = 0x5AE66E, ORANGE = 0xFFB040,
            EU_COL = 0xFFE0C040, GREY = 0x9AA0A8, NONE_COL = 0xFF6A6E74;
    /** Screen-only ids: the filter view switch. */
    private static final int ID_FILTER_VIEW = 71;
    private static final String[] SIDE_KEYS = {"down", "up", "north", "south", "west", "east"};
    /** The table's rows top to bottom: Верх, Низ, Север, Юг, Запад, Восток. */
    private static final int[] ROW_SIDE = {1, 0, 2, 3, 4, 5};

    /** One layout (screen-local GUI pixels). */
    static final class Lay {
        int h, rowY0, rowStep, btnH, allX, allY, allW, allStep, allH, nowY, infoY, graphY, graphH, sepY, invY, hotbarY, prioY, filtY, hintY;
        int gaugeY, gaugeH, chargeLabelY, chargeY, sumY;
    }

    static final Lay BIG = big(), SMALL = small();
    public static final int W = 340;

    private static Lay big() {
        Lay l = new Lay();
        l.h = 262;
        l.rowY0 = 57; l.rowStep = 12; l.btnH = 10;
        l.allX = 54; l.allY = 129; l.allW = 50; l.allStep = 52; l.allH = 10;
        l.nowY = 141; l.infoY = 151; l.graphY = 160; l.graphH = 16;
        l.sepY = 178; l.invY = 183; l.hotbarY = 241;
        l.prioY = 182; l.filtY = 198; l.hintY = 216;
        l.gaugeY = 27; l.gaugeH = 104; l.chargeLabelY = 93; l.chargeY = 102; l.sumY = 124;
        return l;
    }

    private static Lay small() {
        Lay l = new Lay();
        l.h = 222;
        l.rowY0 = 55; l.rowStep = 10; l.btnH = 9;
        l.allX = 176; l.allY = 175; l.allW = 50; l.allStep = 53; l.allH = 11;
        l.nowY = 116; l.infoY = 125; l.graphY = 0; l.graphH = 0;
        l.sepY = 140; l.invY = 144; l.hotbarY = 202;
        l.prioY = 144; l.filtY = 159; l.hintY = 190;
        l.gaugeY = 27; l.gaugeH = 83; l.chargeLabelY = 87; l.chargeY = 96; l.sumY = 116;
        return l;
    }

    /** Fixed places: the left column's width, the top buttons, the gauges and the gap with the arrow between them. */
    static final int LEFT_W = 42, TABLE_X = 54, TABLE_R = 258, PAIR_X = 54, PAIR_W = 32, PAIR_STEP = 34, DIR_X = 158, DIR_W = 32, DIR_STEP = 34,
            TOP_BTN_Y = 29, TOP_BTN_H = 11, CAPTION_Y = 41, GAUGE_EU_X = 262, GAUGE_X_X = 306, GAUGE_W = 28, GAP_X = GAUGE_EU_X + GAUGE_W,
            GAP_W = GAUGE_X_X - GAP_X, GAUGE_TEXT_W = 34, MODE_W = 34, BUF_W = 30, SQUARE = 6, RIGHT_X = 176, RIGHT_W = 156,
            GRAPH_X = 54, GRAPH_R = 332;

    private Lay L = BIG;
    /** The table's columns, measured with the font in initGui (Сторона's width decides where the buttons start). */
    private int modeX = 93, bufX = 129, nbX = 163;
    private final TileEntityEnergyConverterSC te;
    private GuiPowerSC power;
    private boolean filterView;
    private final Btn[] pairBtn = new Btn[3], dirBtn = new Btn[3], modeBtn = new Btn[6], bufBtn = new Btn[6], allBtn = new Btn[3];
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
        columns();
        int x = guiLeft, y = guiTop;
        power = new GuiPowerSC(te, ContainerEnergyConverterSC.BTN_POWER, ContainerEnergyConverterSC.BTN_REDSTONE, 294, 4, 37, L.gaugeY, L.gaugeH);
        power.addButtons(buttonList, x, y);
        for (int k = 0; k < 3; k++) {
            pairBtn[k] = add(new Btn(ContainerEnergyConverterSC.BTN_PAIR + k, x + PAIR_X + k * PAIR_STEP, y + TOP_BTN_Y, PAIR_W, TOP_BTN_H));
            dirBtn[k] = add(new Btn(ContainerEnergyConverterSC.BTN_DIR + k, x + DIR_X + k * DIR_STEP, y + TOP_BTN_Y, DIR_W, TOP_BTN_H));
        }
        for (int r = 0; r < 6; r++) {
            int s = ROW_SIDE[r], ry = y + L.rowY0 + r * L.rowStep;
            modeBtn[s] = add(new Btn(ContainerEnergyConverterSC.BTN_MODE + s, x + modeX, ry, MODE_W, L.btnH));
            bufBtn[s] = add(new Btn(ContainerEnergyConverterSC.BTN_BUF + s, x + bufX, ry, BUF_W, L.btnH));
        }
        for (int k = 0; k < 3; k++) {
            allBtn[k] = add(new Btn(ContainerEnergyConverterSC.BTN_ALL + k, x + L.allX + k * L.allStep, y + L.allY, L.allW, L.allH));
        }
        prioBtn = add(new Btn(ContainerEnergyConverterSC.BTN_PRIORITY, x + RIGHT_X, y + L.prioY, RIGHT_W, 13));
        filterBtn = add(new Btn(ID_FILTER_VIEW, x + RIGHT_X, y + L.filtY, 75, 13));
        compBtn = add(new Btn(ContainerEnergyConverterSC.BTN_COMPARATOR, x + RIGHT_X + 81, y + L.filtY, RIGHT_W - 81, 13));
        placeSlots();
    }

    /**
     * The table's columns from the font: Сторона as wide as its longest name (24..40), then Режим, Буфер,
     * then Сосед up to Поток (whose width is measured every frame from its numbers).
     */
    private void columns() {
        int sideW = 0;
        for (String k : SIDE_KEYS) {
            sideW = Math.max(sideW, fontRendererObj.getStringWidth(Lang.tr("sc.conv.side." + k)));
        }
        sideW = Math.max(24, Math.min(40, sideW));
        modeX = TABLE_X + sideW + 3;
        bufX = modeX + MODE_W + 2;
        nbX = bufX + BUF_W + 3;
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

    /** The pair's energy's own colour (ARGB): labels, the arrow, the graph; grey without a pair. */
    private int xColour() {
        return ForeignEnergySC.labelColour(kind());
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

    /**
     * Small text (at most `scale`), shrunk further to fit `maxW` down to `min`, below that cut with "..." -
     * the whole text then a tooltip. @return the width drawn
     */
    private int small(String text, int x, int y, int maxW, int color, float scale, float min) {
        return small(text, x, y, maxW, color, scale, min, false);
    }

    /** As above; `centre` - the smaller text centred in the 8-pixel line at y. */
    private int small(String text, int x, int y, int maxW, int color, float scale, float min, boolean centre) {
        int tw = Math.max(1, fontRendererObj.getStringWidth(text));
        float k = Math.min(scale, maxW / (float) tw);
        String shown = text;
        if (k < min) {
            k = min;
            shown = fontRendererObj.trimStringToWidth(text, Math.max(0, (int) (maxW / k) - fontRendererObj.getStringWidth("..."))) + "...";
            TextFitSC.hover(guiLeft + x, guiTop + y, maxW, Math.max(6, (int) (9 * k)), text);
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y + (centre ? (1 - k) * 4F : 0F), 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(shown, 0, 0, color);
        GL11.glPopMatrix();
        return (int) Math.ceil(fontRendererObj.getStringWidth(shown) * k);
    }

    private void small(String text, int x, int y, int maxW, int color) {
        small(text, x, y, maxW, color, 0.75F, 0.5F);
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

    private static int shade(int c, float k) {
        int r = (int) (((c >> 16) & 255) * k), g = (int) (((c >> 8) & 255) * k), b = (int) ((c & 255) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static float level(double v, double cap) {
        return cap <= 0 ? 0F : (float) Math.max(0, Math.min(1, v / cap));
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
        // the gauges: the machines' gauge, EU in the charge colours, the other energy in its own
        float euLevel = level(te.getEnergyStored(), te.getMaxEnergyStored());
        GuiEnergyGaugeSC.draw(x + GAUGE_EU_X, y + L.gaugeY, GAUGE_W, L.gaugeH, euLevel);
        Kind k = kind();
        float xLevel = k == null ? 0F : level(te.getForeign(), te.foreignCapacity());
        GuiEnergyGaugeSC.draw(x + GAUGE_X_X, y + L.gaugeY, GAUGE_W, L.gaugeH, xLevel, ForeignEnergySC.gaugeColour(k, xLevel));
        if (k != null) {
            arrow(x + GAP_X, arrowY() + y, partialTicks);
        }
        // the graph
        if (L.graphH > 0) {
            graph(x + GRAPH_X, y + L.graphY, GRAPH_R - GRAPH_X, L.graphH);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The arrow bar's top (screen-local): the gauges' middle. */
    private int arrowY() {
        return L.gaugeY + L.gaugeH / 2 - 1;
    }

    /** Which way the conversion goes now: 1 EU -> X, -1 X -> EU, 0 still (then the direction button's way). */
    private int flowSign() {
        int c = te.statConvEu();
        if (c != 0) {
            return c > 0 ? 1 : -1;
        }
        return te.getDirection() == TileEntityEnergyConverterSC.DIR_X_TO_EU ? -1 : te.getDirection() == TileEntityEnergyConverterSC.DIR_EU_TO_X ? 1 : 0;
    }

    /**
     * The arrow in the gap between the gauges (GAP_W wide, its 3-pixel bar at y): towards the energy being
     * made, in that energy's colour - EU -> X in X's, X -> EU in EU yellow; «Баланс» standing still has both
     * heads, each in the colour of the energy it points at. Dimmed when nothing moves, a light running
     * along it the way the energy goes when it does.
     */
    private void arrow(int x, int y, float pt) {
        boolean moving = te.statConvEu() != 0 && te.isPowerOn();
        int sign = flowSign();
        float dim = moving ? 1F : 0.5F;
        int xc = shade(xColour(), dim), ec = shade(EU_COL, dim);
        int x0 = x + 1, x1 = x + GAP_W - 1;
        if (sign == 0 || (te.getDirection() == TileEntityEnergyConverterSC.DIR_BALANCE && !moving)) {
            int mid = (x0 + x1) / 2;
            rect(x0, y, mid - x0, 3, ec);
            rect(mid, y, x1 - mid, 3, xc);
            head(x0, y, -1, ec);
            head(x1 - 1, y, 1, xc);
            return;
        }
        int col = sign > 0 ? xc : ec;
        rect(x0, y, x1 - x0, 3, col);
        head(sign > 0 ? x1 - 1 : x0, y, sign, col);
        if (moving) {
            float t = mc.theWorld == null ? 0 : (mc.theWorld.getTotalWorldTime() % 20 + pt) / 20F;
            int run = x1 - x0 - 6, p = (int) (t * run);
            rect(sign > 0 ? x0 + p : x1 - 2 - p, y + 1, 2, 1, 0xC0FFFFFF);
        }
    }

    /** An arrowhead 4 columns deep ending at column `tipX`, pointing `dir` (1 right, -1 left): 3, 5, 7, 9 px high round the bar. */
    private static void head(int tipX, int y, int dir, int col) {
        for (int i = 0; i < 4; i++) {
            rect(tipX - dir * i, y - i, 1, 3 + 2 * i, col);
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
        String u = unit();
        fit(Lang.tr("tile.siliconage.energyConverter.name"), 8, 6, 280, TITLE);
        small(Lang.tr("sc.conv.gui.modules"), 8, 20, LEFT_W, LABEL, 1F, 0.7F);
        fit(Lang.tr("sc.conv.gui.pair"), PAIR_X, 20, DIR_X - PAIR_X - 4, LABEL);
        fit(Lang.tr("sc.conv.gui.direction"), DIR_X, 20, TABLE_R - DIR_X, LABEL);
        // the pair and direction buttons, under an unavailable pair what it lacks
        Kind[] kinds = Kind.values();
        for (int i = 0; i < 3; i++) {
            Kind ki = kinds[i];
            boolean sel = k == ki, can = te.pairAvailable(ki);
            pairBtn[i].set("EU-" + ki.unit, sel ? (ForeignEnergySC.labelColour(ki) & 0xFFFFFF) : can ? TEXT : 0x6A6E74, sel, !can && !sel);
            if (!can && !sel) {
                String why = Lang.tr(ki.modPresent() ? "sc.conv.gui.needcard" : "sc.conv.gui.nomod");
                smallCentred(why, PAIR_X + i * PAIR_STEP + PAIR_W / 2, CAPTION_Y, PAIR_W + 2, DIM, 0.6F);
            }
        }
        String[] dirs = {"EU→" + u, u + "→EU", Lang.tr("sc.conv.gui.balance")};
        for (int i = 0; i < 3; i++) {
            boolean sel = te.getDirection() == i;
            dirBtn[i].set(dirs[i], sel ? GREEN : TEXT, sel, k == null);
        }
        table(k, u);
        for (int i = 0; i < 3; i++) {
            allBtn[i].set(Lang.tr("sc.conv.gui.all." + i), TEXT, false, false);
        }
        status(k, u);
        if (L.graphH > 0) {
            small(Lang.tr("sc.conv.gui.graph"), GRAPH_X + 3, L.graphY + 2, 80, DIM, 0.6F, 0.5F);
        }
        // the gauges' captions, numbers under them, the loss under the arrow
        centred("EU", GAUGE_EU_X - 4, 18, GAUGE_W + 8, EU_COL & 0xFFFFFF);
        centred(k == null ? "—" : u, GAUGE_X_X - 4, 18, GAUGE_W + 8, xColour() & 0xFFFFFF);
        gaugeText(GAUGE_EU_X, te.getEnergyStored(), te.getMaxEnergyStored(), "EU", te.statEuPlus(), te.statEuMinus());
        if (k != null) {
            gaugeText(GAUGE_X_X, te.getForeign(), te.foreignCapacity(), u, te.statXPlus(), te.statXMinus());
            smallCentred("−" + te.lossPercent() + "%", GAP_X + GAP_W / 2, arrowY() + 7, GAP_W, 0xFF9696, 0.75F);
        } else {
            centred("—", GAUGE_X_X, L.gaugeY + L.gaugeH + 2, GAUGE_W, GREY);
        }
        if (!te.isPowerOn()) {
            rect(GAUGE_EU_X + 2, L.gaugeY + 2, GAUGE_X_X + GAUGE_W - GAUGE_EU_X - 4, L.gaugeH - 4, 0xA0000000);
            centred(Lang.tr("sc.gui.power.label"), GAUGE_EU_X, L.gaugeY + L.gaugeH / 2 - 4, GAUGE_X_X + GAUGE_W - GAUGE_EU_X, 0xB0B8C4);
        }
        // left: the charge slot, the summary
        int loss = te.lossPercent();
        small(Lang.tr("sc.conv.gui.charge"), 8, L.chargeLabelY, LEFT_W, LABEL, 1F, 0.7F);
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
        small(Lang.tr("sc.conv.gui.hoverhint"), RIGHT_X, L.hintY, RIGHT_W, DIM, 0.75F, 0.6F);
    }

    /** The faces' table: the header (measured columns), six rows, the neighbour's energy square. */
    private void table(Kind k, String u) {
        String[] flows = new String[6];
        String flowHead = Lang.tr("sc.conv.gui.flow");
        int flowW = fontRendererObj.getStringWidth(flowHead);
        for (int s = 0; s < 6; s++) {
            flows[s] = te.getMode(s) == TileEntityEnergyConverterSC.MODE_OFF ? "—" : sideFlow(s);
            flowW = Math.max(flowW, fontRendererObj.getStringWidth(flows[s]));
        }
        flowW = Math.min(36, flowW);
        int nbTextX = nbX + SQUARE + 2, nbTextW = TABLE_R - flowW - 3 - nbTextX;
        int hy = L.rowY0 - 9;
        fit(Lang.tr("sc.conv.gui.side"), TABLE_X, hy, modeX - TABLE_X - 1, LABEL);
        centred(Lang.tr("sc.conv.gui.mode"), modeX, hy, MODE_W, LABEL);
        centred(Lang.tr(filterView ? "sc.conv.gui.filter" : "sc.conv.gui.buffer"), bufX - 1, hy, BUF_W + 3, filterView ? ORANGE : LABEL);
        fit(Lang.tr("sc.conv.gui.neighbour"), nbX, hy, TABLE_R - flowW - 3 - nbX, LABEL);
        int fhw = Math.min(fontRendererObj.getStringWidth(flowHead), flowW);
        fit(flowHead, TABLE_R - fhw, hy, flowW, LABEL);
        for (int r = 0; r < 6; r++) {
            int s = ROW_SIDE[r], ry = L.rowY0 + r * L.rowStep;
            int ty = ry + (L.btnH - 8) / 2 + 1;
            fit(Lang.tr("sc.conv.side." + SIDE_KEYS[s]), TABLE_X, ty, modeX - TABLE_X - 2, TEXT);
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
            Nb nb = neighbour(s);
            if (nb.colour != 0) {
                int sy = ry + (L.btnH - SQUARE) / 2;
                rect(nbX, sy, SQUARE, SQUARE, 0xFF15181C);
                rect(nbX + 1, sy + 1, SQUARE - 2, SQUARE - 2, nb.colour);
                rect(nbX + 1, sy + 1, SQUARE - 2, 1, 0x50FFFFFF);
            }
            small(nb.text, nbTextX, ty, nbTextW, LABEL, 1F, 0.65F, true);
            int fw = Math.min(fontRendererObj.getStringWidth(flows[s]), flowW);
            fit(flows[s], TABLE_R - fw, ty, flowW, te.statSide(s) > 0 ? GREEN : te.statSide(s) < 0 ? ORANGE : TEXT);
        }
    }

    /** Under the table: what is converted now; the rate, the loss and the throughput (smaller, dim). */
    private void status(Kind k, String u) {
        int maxW = GAUGE_EU_X - 4 - TABLE_X;
        String now;
        if (k == null) {
            now = Lang.tr("sc.conv.gui.nopair");
        } else if (te.statConvEu() > 0) {
            now = Lang.tr("sc.conv.gui.now", num(te.statConvEu()) + " EU/t", num(Math.abs(te.statConvX())) + " " + u + "/t");
        } else if (te.statConvEu() < 0) {
            now = Lang.tr("sc.conv.gui.now", num(Math.abs(te.statConvX())) + " " + u + "/t", num(-te.statConvEu()) + " EU/t");
        } else {
            now = Lang.tr("sc.conv.gui.idle");
        }
        fit(now, TABLE_X, L.nowY, maxW, TEXT);
        String info = k == null ? Lang.tr("sc.conv.gui.info.eu", num(te.throughput()))
                : Lang.tr("sc.conv.gui.info", rate(te.rate()), String.valueOf(te.lossPercent()), num(te.throughput()));
        small(info, TABLE_X, L.infoY, maxW, DIM, 0.75F, 0.6F);
    }

    private void centred(String t, int x, int y, int w, int col) {
        TextFitSC.drawCentered(fontRendererObj, t, x, y, w, col, false, guiLeft, guiTop);
    }

    /** Under a gauge: the amount, of the capacity, + in, - out a tick (the % is in the gauge's window). */
    private void gaugeText(int gx, double v, double cap, String unit, int plus, int minus) {
        int y = L.gaugeY + L.gaugeH + 2, cx = gx + GAUGE_W / 2, w = GAUGE_TEXT_W;
        smallCentred(big(v), cx, y, w, TEXT, 0.75F);
        smallCentred("/ " + big(cap), cx, y + 6, w, DIM, 0.75F);
        smallCentred("+" + num(plus) + "/t", cx, y + 12, w, GREEN, 0.75F);
        smallCentred("−" + num(minus) + "/t", cx, y + 18, w, ORANGE, 0.75F);
    }

    private void smallCentred(String t, int cx, int y, int maxW, int col, float scale) {
        float k = Math.min(scale, maxW / (float) Math.max(1, fontRendererObj.getStringWidth(t)));
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

    /** A neighbour cell: its text and the colour of its square (0 - no square: nothing there). */
    private static final class Nb {
        final String text;
        final int colour;

        Nb(String text, int colour) {
            this.text = text;
            this.colour = colour;
        }
    }

    /**
     * The colour of the energy a neighbour speaking `kinds` (K_* bits) gets from this converter: the pair's
     * energy if it speaks it, else EU, else the first other energy it speaks; none - grey.
     */
    private int kindColour(int kinds) {
        Kind pair = kind();
        if (pair != null && (kinds & K_RF << pair.ordinal()) != 0) {
            return ForeignEnergySC.labelColour(pair);
        }
        if ((kinds & K_EU) != 0) {
            return EU_COL;
        }
        for (Kind k : Kind.values()) {
            if ((kinds & K_RF << k.ordinal()) != 0) {
                return ForeignEnergySC.labelColour(k);
            }
        }
        return NONE_COL;
    }

    /** What the neighbour beside face `s` is: the mod's cable and its tier, an IC2 cable, or the mod and the energies it speaks. */
    private Nb neighbour(int s) {
        ForgeDirection d = ForgeDirection.getOrientation(s);
        int nx = te.xCoord + d.offsetX, ny = te.yCoord + d.offsetY, nz = te.zCoord + d.offsetZ;
        if (te.getWorldObj() == null || !te.getWorldObj().blockExists(nx, ny, nz)) {
            return new Nb("—", 0);
        }
        TileEntity n = te.getWorldObj().getTileEntity(nx, ny, nz);
        if (n == null) {
            return new Nb("—", 0);
        }
        if (n instanceof com.sc.tileentity.TileEntityConduitBundleSC) {
            com.sc.energy.CableType c = ((com.sc.tileentity.TileEntityConduitBundleSC) n).getCable();
            return c == null ? new Nb(Lang.tr("sc.conv.n.noenergy", "Silicon Age"), NONE_COL) : new Nb(Lang.tr("sc.conv.n.cable", c.tier.name()), EU_COL);
        }
        if (n instanceof com.sc.tileentity.TileEntityCableSC && ((com.sc.tileentity.TileEntityCableSC) n).getCableType() != null) {
            return new Nb(Lang.tr("sc.conv.n.cable", ((com.sc.tileentity.TileEntityCableSC) n).getCableType().tier.name()), EU_COL);
        }
        if (n instanceof com.sc.energy.TileEntityEnergyBase) {
            return new Nb(Lang.tr("sc.conv.n.ours", n.getBlockType() == null ? "?" : new ItemStack(n.getBlockType(), 1,
                    n.getBlockType().damageDropped(te.getWorldObj().getBlockMetadata(nx, ny, nz))).getDisplayName()), EU_COL);
        }
        int kinds = kindsOf(n.getClass());
        String mod = modName(n);
        if ((kinds & K_IC2_CABLE) != 0) {
            return new Nb(Lang.tr("sc.conv.n.ic2cable", mod), EU_COL);
        }
        if ((kinds & 15) == 0) {
            return new Nb(Lang.tr("sc.conv.n.noenergy", mod), NONE_COL);
        }
        StringBuilder sb = new StringBuilder();
        String[] names = {"EU", "RF", "J", "gJ"};
        for (int i = 0; i < 4; i++) {
            if ((kinds & 1 << i) != 0) {
                sb.append(sb.length() == 0 ? "" : "/").append(names[i]);
            }
        }
        return new Nb(mod + " · " + sb, kindColour(kinds));
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
            boolean caption = GuiGaugeSC.isOver(PAIR_X + i * PAIR_STEP, CAPTION_Y, PAIR_W, 6, mx, my) && !te.pairAvailable(Kind.values()[i]);
            if (over(pairBtn[i], mx, my) || caption) {
                Kind ki = Kind.values()[i];
                l.add(Lang.tr("sc.conv.tip.pair", ki.unit, rate(ki.perEu())));
                if (!ki.modPresent()) {
                    l.add("§c" + Lang.tr("sc.conv.tip.pair.nomod." + ki.name().toLowerCase(java.util.Locale.ROOT)));
                    if (ki != Kind.RF) {
                        l.add("§7" + Lang.tr("sc.conv.tip.pair.card." + ki.name().toLowerCase(java.util.Locale.ROOT)));
                    }
                } else if (!te.pairAvailable(ki)) {
                    l.add("§e" + Lang.tr("sc.conv.tip.pair.card." + ki.name().toLowerCase(java.util.Locale.ROOT)));
                }
                if (k != null && ki != k) {
                    l.add("§7" + (te.getForeign() > 0
                            ? Lang.tr("sc.conv.tip.pair.refund", num(Math.round(te.getForeign())) + " " + u, num(te.refundPreview()))
                            : Lang.tr("sc.conv.hint.swap", u, String.valueOf(te.lossPercent()))));
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
        for (int i = 0; i < 3; i++) {
            if (over(allBtn[i], mx, my)) {
                l.add(Lang.tr("sc.conv.tip.all." + i));
                return l;
            }
        }
        if (GuiGaugeSC.isOver(TABLE_X, L.rowY0 - 10, TABLE_R - TABLE_X, 9, mx, my)) {
            l.add(Lang.tr("sc.conv.tip.table.1"));
            l.add("§7" + Lang.tr("sc.conv.tip.table.2"));
            l.add("§7" + Lang.tr("sc.conv.tip.table.3", u));
            l.add("§7" + Lang.tr("sc.conv.tip.table.4"));
            return l;
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
        int gaugeH = L.gaugeH + 2 + 24;
        if (GuiGaugeSC.isOver(GAUGE_EU_X, L.gaugeY, GAUGE_W, gaugeH, mx, my)) {
            gaugeTip(l, "EU", te.getEnergyStored(), te.getMaxEnergyStored());
            l.add("§a+" + num(te.statEuPlus()) + " EU/t  §6−" + num(te.statEuMinus()) + " EU/t");
            return l;
        }
        if (GuiGaugeSC.isOver(GAUGE_X_X, L.gaugeY, GAUGE_W, gaugeH, mx, my)) {
            if (k == null) {
                l.add(Lang.tr("sc.conv.gui.nopair"));
            } else {
                gaugeTip(l, u, te.getForeign(), te.foreignCapacity());
                l.add("§a+" + num(te.statXPlus()) + " " + u + "/t  §6−" + num(te.statXMinus()) + " " + u + "/t");
                if (!te.pairActive()) {
                    l.add("§c" + Lang.tr("sc.conv.tip.blocked"));
                }
            }
            return l;
        }
        if (k != null && GuiGaugeSC.isOver(GAP_X, arrowY() - 4, GAP_W, 18, mx, my)) {
            l.add(Lang.tr("sc.conv.tip.arrow", rate(te.rate()), u, te.lossPercent()));
            return l;
        }
        if (GuiGaugeSC.isOver(TABLE_X, L.nowY, GAUGE_EU_X - 4 - TABLE_X, L.infoY + 7 - L.nowY, mx, my)) {
            int thr = te.throughput();
            l.add(k == null ? Lang.tr("sc.conv.gui.thr.eu", num(thr), big(te.getMaxEnergyStored()))
                    : Lang.tr("sc.conv.gui.thr", num(thr), num(Math.round(thr * te.rate())) + " " + u, big(te.getMaxEnergyStored()), big(te.foreignCapacity()) + " " + u));
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

    /** A gauge's tooltip head: the buffer, how much of how much, the percentage. */
    private static void gaugeTip(List<String> l, String unit, double v, double cap) {
        l.add(Lang.tr("sc.conv.tip.gauge", unit, num(Math.round(v)) + " / " + num(Math.round(cap)) + " " + unit,
                String.valueOf(Math.round(100 * level(v, cap)))));
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
