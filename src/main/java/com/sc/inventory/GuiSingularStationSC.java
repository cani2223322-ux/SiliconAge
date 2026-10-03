package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.sc.manual.Lang;
import com.sc.tileentity.SingularProcessSC;
import com.sc.tileentity.TileEntityArmorStationSC;
import com.sc.tileentity.TileEntitySingularStationSC;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.SingularLevel;
import com.sc.util.SingularScheme;
import com.sc.util.SingularStationMath;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * The Singular Service Station's screen (docs/singular-armor/sstation_R1_420x300*.png, the preview from R2):
 * 420 x 300 - left the four pieces (name, level, points bar) and under them the slots of the open tab
 * (modernisation: the catalyst core; conversion: the six materials and the core; transfer: the donor);
 * centre the tabs «Модерн. · Преобр. · Перенос · Синхр. · Ветки» over one panel - the tab's checklist (have /
 * need), the multipliers, its button (the branch buttons on «Ветки»), the progress bar, «Отменить (50%)» and the
 * status; right the eight tanks and the energy gauge as in the machines (GuiTankGaugeSC.drawCompact - the
 * machine gauge in a narrow well, the x that pours a tank out, GuiEnergyGaugeSC with the power-off shade), the gas
 * switches under the tanks, the modules, the colour scheme ◄ ► and a turning preview of the armour in the slots;
 * under it the inventory and six switches. A screen smaller than 420 x 300 GUI pixels gets the compact
 * 320 x 236 arrangement of the same parts (no preview, the scheme row under the switches). The slots of the
 * other tabs are moved off screen and take nothing (ContainerSingularStationSC.slotInTab); what lies in them
 * stays and a warning says so. Every condition has a tooltip.
 */
public class GuiSingularStationSC extends GuiContainer {

    private static final int R = SingularStationMath.RESOURCES, TABS = ContainerSingularStationSC.TABS;
    private static final int T_MODERN = ContainerSingularStationSC.TAB_MODERN, T_CONVERT = ContainerSingularStationSC.TAB_CONVERT,
            T_TRANSFER = ContainerSingularStationSC.TAB_TRANSFER, T_SYNC = ContainerSingularStationSC.TAB_SYNC,
            T_BRANCH = ContainerSingularStationSC.TAB_BRANCH;
    /** Screen-only button ids (never sent as they are). */
    private static final int ID_TAB = 100, ID_ACTION = 110, ID_CANCEL = 111, ID_ALL_GASES = 112;
    /** Off screen: a slot of another tab. */
    private static final int HIDDEN = -2000;
    /** Colours (the A scheme: base (34,22,48), accent (190,110,255)). */
    private static final int BG = 0xFF140E1C, PANEL = 0xFF1E1629, EDGE = 0xFF4A3466, EDGE_HI = 0xFF7A56A8, ACCENT = 0xFFBE6EFF,
            TITLE = 0xD9A8FF, TEXT = 0xE8E0F4, LABEL = 0xA898C0, DIM = 0x6E6280, OK = 0x5AE66E, BAD = 0xFF5A50, WARN = 0xFFB040,
            YELLOW = 0xFFE14D, BLUE = 0x8CB4FF;
    private static final long ARM_MS = 1500;
    /** The tab last open (this game session). */
    private static int lastTab = T_MODERN;

    // ------------------------------------------------------------------ the two layouts

    /** Every position of one layout, screen-local GUI pixels (slot positions: the item's top left). */
    static final class Lay {
        boolean big;
        int w, h;
        /** Left: the armour rows; the open tab's slots under them. */
        int armLabelY, rowY0, rowStep, slotDY, nameX, lvRight, barW, barDY, ptsDY, leftR;
        int modeLabelY, modeSlotY, coreLabelY, coreX, coreY, extraTextX, extraTextW, warnY, warnX;
        /** Centre: the tabs, the panel. */
        int tabX, tabY, tabW, tabStep, tabH;
        int px, py, pw, ph, headY, rowsY, rowH, mulY, actY, actH, barY, barH, cancelY, cancelW, cancelH;
        /** Right: tanks, energy, modules, scheme, preview (viewW 0: none). */
        int rx, rw, tankLabelY, clearY, gaugeY, gaugeH, gasY, enX, enY, enW, enH, enTextX, enTextY, enTextW;
        int modLabelX, modLabelY, modLabelW, modX, modY, modCols, modInfoX, modInfoY, modInfoW;
        int schemeLabelY, schemeX, schemeY, schemeW, viewX, viewY, viewW, viewH;
        /** Bottom: the inventory, the 2 x 3 switches, the hints. */
        int sepY, invX, invY, hotbarY, gridX, gridY, colW, colGap, btnH, gridStep, hintX, hintY, hintStep, hintW;
    }

    static final Lay BIG = big(), SMALL = small();

    /** 420 x 300 (the approved R1). */
    private static Lay big() {
        Lay l = new Lay();
        l.big = true;
        l.w = 420;
        l.h = 300;
        l.armLabelY = 18; l.rowY0 = 26; l.rowStep = 24; l.slotDY = 2; l.nameX = 27; l.lvRight = 99; l.barW = 72; l.barDY = 11; l.ptsDY = 17;
        l.leftR = 100;
        l.modeLabelY = 125; l.modeSlotY = 134; l.coreLabelY = 134; l.coreX = 73; l.coreY = 143; l.extraTextX = 27; l.extraTextW = 72;
        l.warnY = 174; l.warnX = 7;
        l.tabX = 104; l.tabY = 17; l.tabW = 37; l.tabStep = 39; l.tabH = 13;
        l.px = 104; l.py = 32; l.pw = 194; l.ph = 168; l.headY = 36; l.rowsY = 50; l.rowH = 13; l.mulY = 141;
        l.actY = 151; l.actH = 14; l.barY = 168; l.barH = 11; l.cancelY = 183; l.cancelW = 92; l.cancelH = 13;
        l.rx = 302; l.rw = 112; l.tankLabelY = 18; l.clearY = 25; l.gaugeY = 34; l.gaugeH = 48; l.gasY = 84;
        l.enX = 392; l.enY = 25; l.enW = 22; l.enH = 68; l.enTextX = 302; l.enTextY = 96; l.enTextW = 112;
        l.modLabelX = 302; l.modLabelY = 105; l.modLabelW = 70; l.modX = 303; l.modY = 113; l.modCols = 4;
        l.modInfoX = 377; l.modInfoY = 114; l.modInfoW = 37;
        l.schemeLabelY = 134; l.schemeX = 302; l.schemeY = 141; l.schemeW = 112;
        l.viewX = 302; l.viewY = 157; l.viewW = 112; l.viewH = 44;
        l.sepY = 204; l.invX = 7; l.invY = 210; l.hotbarY = 268;
        l.gridX = 178; l.gridY = 209; l.colW = 116; l.colGap = 4; l.btnH = 15; l.gridStep = 18;
        l.hintX = 178; l.hintY = 266; l.hintStep = 9; l.hintW = 236;
        return l;
    }

    /** 320 x 236 (a small screen, e.g. GUI scale 4 at 1080p = 480 x 270): no preview, the scheme under the switches. */
    private static Lay small() {
        Lay l = new Lay();
        l.big = false;
        l.w = 320;
        l.h = 236;
        l.armLabelY = -1; l.rowY0 = 17; l.rowStep = 22; l.slotDY = 3; l.nameX = 26; l.lvRight = 95; l.barW = 68; l.barDY = 11; l.ptsDY = 16;
        l.leftR = 96;
        l.modeLabelY = 107; l.modeSlotY = 116; l.coreLabelY = 116; l.coreX = 71; l.coreY = 125; l.extraTextX = 26; l.extraTextW = 70;
        l.warnY = -1; l.warnX = 89;
        l.tabX = 98; l.tabY = 17; l.tabW = 24; l.tabStep = 25; l.tabH = 11;
        l.px = 98; l.py = 29; l.pw = 125; l.ph = 121; l.headY = 32; l.rowsY = 42; l.rowH = 9; l.mulY = 106;
        l.actY = 114; l.actH = 12; l.barY = 128; l.barH = 9; l.cancelY = 139; l.cancelW = 56; l.cancelH = 10;
        l.rx = 226; l.rw = 90; l.tankLabelY = 17; l.clearY = 24; l.gaugeY = 32; l.gaugeH = 40; l.gasY = 74;
        l.enX = 226; l.enY = 86; l.enW = 22; l.enH = 54; l.enTextX = 251; l.enTextY = 87; l.enTextW = 65;
        l.modLabelX = 291; l.modLabelY = 100; l.modLabelW = 25; l.modX = 252; l.modY = 100; l.modCols = 2;
        l.modInfoX = 291; l.modInfoY = 110; l.modInfoW = 25;
        l.schemeLabelY = -1; l.schemeX = 172; l.schemeY = 195; l.schemeW = 144;
        l.viewX = 0; l.viewY = 0; l.viewW = 0; l.viewH = 0;
        l.sepY = 152; l.invX = 7; l.invY = 155; l.hotbarY = 213;
        l.gridX = 172; l.gridY = 154; l.colW = 71; l.colGap = 2; l.btnH = 12; l.gridStep = 13;
        l.hintX = 172; l.hintY = 211; l.hintStep = 9; l.hintW = 144;
        return l;
    }

    private static final int GAUGE_W = 10, GAUGE_STEP = 11, GAS_H = 9;

    private Lay L = BIG;
    private final TileEntitySingularStationSC te;
    private final ContainerSingularStationSC box;
    private GuiPowerSC power;
    private Btn action, cancel, powerBtn, charge, fill, redstone, helium, allGases, prev, next;
    private final TabBtn[] tabs = new TabBtn[TABS];
    private final Btn[] branch = new Btn[4];
    private final GasBtn[] gasBtn = new GasBtn[Gas.values().length];
    private final GuiBigSC.ClearButton[] clear = new GuiBigSC.ClearButton[Gas.values().length];
    private int tab = lastTab;
    /** The process kind last seen running (-1: none) - a new one opens its tab. */
    private int seenKind = -1;
    private long cancelArmed;
    /** The preview's stand-in player (client only, never in the world). */
    private EntityOtherPlayerMP dummy;
    private static ItemStack[] sample;

    public GuiSingularStationSC(InventoryPlayer inv, TileEntitySingularStationSC te) {
        super(new ContainerSingularStationSC(inv, te));
        this.te = te;
        this.box = (ContainerSingularStationSC) inventorySlots;
        xSize = BIG.w;
        ySize = BIG.h;
    }

    private static int tabOf(SingularProcessSC p) {
        return p == null ? -1 : ContainerSingularStationSC.tabOf(p.kind);
    }

    // ------------------------------------------------------------------ the plan of the open tab

    /** What the panel shows: the running process (when it is this tab's), or what the tab's action would take now. */
    private static final class Plan {
        String header = "";
        long[] cost = new long[R], have = new long[R];
        int ticks;
        boolean process, can;
        int ready, candidates, pointsFull, taskLevel;
        boolean taskDone, catalystNeeded, catalystOk, setDiscount;
        int[] readyLv = new int[4];
        int convMask;
        boolean matOk;
        int[] matNeed = new int[SingularStationMath.MATERIALS], matHave = new int[SingularStationMath.MATERIALS];
        int donorLevel, target = -1, lagMask, top, procMask;
    }

    private EntityPlayer me() {
        return mc.thePlayer;
    }

    private Plan plan() {
        Plan p = new Plan();
        SingularProcessSC proc = te.getProcess();
        boolean busy = proc != null;
        if (busy && tabOf(proc) == tab) {
            p.process = true;
            p.cost = proc.cost.clone();
            p.have = proc.drawn.clone();
            p.procMask = proc.mask & 15;
            p.ticks = te.ticksLeft();
            if (proc.kind == SingularProcessSC.KIND_SYNC) {
                p.header = Lang.tr("sc.singStation.head.sync", proc.target);
            } else if (proc.kind == SingularProcessSC.KIND_TRANSFER) {
                p.header = Lang.tr("sc.singStation.head.transfer");
            } else if (proc.kind == SingularProcessSC.KIND_CONVERT) {
                p.header = Lang.tr("sc.singStation.head.convert");
                p.convMask = proc.mask & 15;
            } else {
                int lo = 9, hi = 0;
                for (int i = 0; i < 4; i++) {
                    if (proc.locks(i)) {
                        int l = SingularLevel.levelOf(te.getStackInSlot(i));
                        lo = Math.min(lo, l);
                        hi = Math.max(hi, l);
                    }
                }
                p.header = lo > hi ? Lang.tr("sc.singStation.head.none") : levelHeader(lo, hi);
                p.setDiscount = Integer.bitCount(proc.mask & 15) == 4;
            }
            return p;
        }
        p.have[SingularStationMath.R_EU] = te.getEnergyStored();
        for (int r = 1; r < R; r++) {
            p.have[r] = te.tankAmount(SingularStationMath.GAS[r]);
        }
        if (tab == T_CONVERT) {
            int cm = te.convertMask();
            p.convMask = cm;
            p.header = Lang.tr("sc.singStation.head.convert");
            p.candidates = Integer.bitCount(cm);
            if (cm != 0) {
                p.cost = SingularStationMath.convertCost(cm);
                p.ticks = SingularStationMath.duration(SingularStationMath.convertTicks(cm), te.speed());
                p.matNeed = SingularStationMath.convertMaterials(cm);
                p.matHave = te.materialsHave();
                p.matOk = true;
                for (int k = 0; k < p.matNeed.length; k++) {
                    p.matOk &= p.matHave[k] >= p.matNeed[k];
                }
                p.have[SingularStationMath.R_EU] += te.coreChargeFor(cm);
            }
            p.can = !busy && cm != 0 && p.matOk;
        } else if (tab == T_TRANSFER) {
            p.header = Lang.tr("sc.singStation.head.transfer");
            ItemStack d = te.getStackInSlot(TileEntitySingularStationSC.DONOR_SLOT);
            p.donorLevel = SingularLevel.isSingular(d) ? SingularLevel.levelOf(d) : 0;
            p.target = te.transferTarget();
            if (p.target >= 0) {
                p.cost = SingularStationMath.transferCost(p.donorLevel);
                p.ticks = SingularStationMath.duration(SingularStationMath.transferTicks(p.donorLevel), te.speed());
            }
            p.can = !busy && p.target >= 0;
        } else if (tab == T_SYNC) {
            int[] lv = slotLevels();
            p.lagMask = lagging();
            for (int l : lv) {
                p.top = Math.max(p.top, l);
            }
            p.header = p.lagMask != 0 ? Lang.tr("sc.singStation.head.sync", p.top) : Lang.tr("sc.singStation.btn.sync");
            if (p.lagMask != 0) {
                p.cost = SingularStationMath.syncCost(lv);
                p.ticks = SingularStationMath.duration(SingularStationMath.syncTicks(lv), te.speed());
            }
            p.can = !busy && p.lagMask != 0;
        } else if (tab == T_BRANCH) {
            p.header = Lang.tr("sc.singStation.branches.head");
        } else {
            moderniseEstimate(p);
            p.can = !busy && p.ready > 0 && (!p.catalystNeeded || p.catalystOk);
        }
        return p;
    }

    private void moderniseEstimate(Plan p) {
        int[] cand = new int[4];
        int lo = 9, hi = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = te.getStackInSlot(i);
            if (SingularLevel.isSingular(s) && SingularLevel.levelOf(s) < SingularLevel.MAX) {
                cand[i] = SingularLevel.levelOf(s);
                p.candidates++;
                p.pointsFull += SingularLevel.pointsFull(s) ? 1 : 0;
                lo = Math.min(lo, cand[i]);
                hi = Math.max(hi, cand[i]);
            }
        }
        p.readyLv = te.readyLevels(me());
        p.ready = SingularStationMath.pieces(p.readyLv);
        int[] lv = p.ready > 0 ? p.readyLv : cand;
        if (p.ready > 0) {
            lo = 9;
            hi = 0;
            for (int l : lv) {
                if (l > 0) {
                    lo = Math.min(lo, l);
                    hi = Math.max(hi, l);
                }
            }
        }
        p.header = p.candidates == 0 ? Lang.tr("sc.singStation.head.none") : levelHeader(lo, hi);
        p.cost = SingularStationMath.moderniseCost(lv, te.hasResonance());
        p.ticks = SingularStationMath.duration(SingularStationMath.moderniseTicks(lv), te.speed());
        p.setDiscount = SingularStationMath.pieces(lv) >= 4;
        p.taskLevel = p.candidates == 0 ? 0 : lo + 1;
        p.taskDone = p.taskLevel > 0 && SingularLevel.taskDone(me(), p.taskLevel);
        p.catalystNeeded = SingularStationMath.needsCatalyst(lv);
        ItemStack core = te.getStackInSlot(TileEntitySingularStationSC.CATALYST_SLOT);
        p.catalystOk = TileEntitySingularStationSC.isCore(core);
        if (p.catalystNeeded && p.catalystOk) {
            p.have[SingularStationMath.R_EU] += com.sc.item.ItemBatterySC.chargeOf(core);
        }
    }

    private static String levelHeader(int lo, int hi) {
        return lo == hi ? Lang.tr("sc.singStation.head.level", lo, lo + 1) : Lang.tr("sc.singStation.head.levels", lo, hi);
    }

    /** A checklist row: the square's colour, the name left, have / need right; tip - what its tooltip is. */
    private static final class Row {
        final String name, value;
        final int square, color, tip;

        Row(String name, String value, int square, int color, int tip) {
            this.name = name;
            this.value = value;
            this.square = square;
            this.color = color;
            this.tip = tip;
        }
    }

    /** Tooltip codes of the rows (100 + r: resource r). */
    private static final int TIP_POINTS = 0, TIP_TASK = 1, TIP_PIECES = 2, TIP_PROGRESS = 3, TIP_EXO = 4, TIP_MAT = 5,
            TIP_DONOR = 6, TIP_TARGET = 7, TIP_LAG = 8, TIP_TOP = 9, TIP_RES = 100;

    private List<Row> rows(Plan p) {
        List<Row> out = new ArrayList<Row>();
        if (tab == T_BRANCH) {
            return out;
        }
        int acc = ACCENT & 0xFFFFFF;
        if (p.process) {
            SingularProcessSC proc = te.getProcess();
            out.add(new Row(Lang.tr(tab == T_CONVERT ? "sc.singStation.row.exo" : "sc.singStation.row.pieces"),
                    Lang.tr("sc.singStation.row.count", Integer.bitCount(p.procMask)), acc, TEXT, TIP_PIECES));
            out.add(new Row(Lang.tr("sc.singStation.row.done"), Math.round(proc.progress * 100) + "%", acc, TEXT, TIP_PROGRESS));
        } else if (tab == T_CONVERT) {
            int cm = p.convMask;
            out.add(new Row(Lang.tr("sc.singStation.row.exo"), cm == 0 ? Lang.tr("sc.singStation.row.none")
                    : Lang.tr("sc.singStation.row.count", Integer.bitCount(cm)), cm == 0 ? BAD : OK, cm == 0 ? DIM : TEXT, TIP_EXO));
            int missing = 0;
            for (int k = 0; k < p.matNeed.length; k++) {
                missing += p.matHave[k] < p.matNeed[k] ? 1 : 0;
            }
            out.add(new Row(Lang.tr("sc.singStation.row.mat"), cm == 0 ? "-" : p.matOk ? Lang.tr("sc.singStation.row.mat.ok")
                    : Lang.tr("sc.singStation.row.mat.short", missing), cm == 0 ? DIM : p.matOk ? OK : BAD,
                    cm == 0 ? DIM : p.matOk ? TEXT : BAD, TIP_MAT));
        } else if (tab == T_TRANSFER) {
            boolean dOk = p.donorLevel >= 2;
            out.add(new Row(Lang.tr("sc.singStation.row.donor"), p.donorLevel > 0 ? Lang.tr("sc.singStation.lv", p.donorLevel)
                    : Lang.tr("sc.singStation.row.none"), dOk ? OK : BAD, dOk ? TEXT : BAD, TIP_DONOR));
            out.add(new Row(Lang.tr("sc.singStation.row.target"), p.target >= 0 ? Lang.tr("sc.armorhud.piece." + p.target)
                    : Lang.tr("sc.singStation.row.none"), p.target >= 0 ? OK : BAD, p.target >= 0 ? TEXT : BAD, TIP_TARGET));
        } else if (tab == T_SYNC) {
            boolean lag = p.lagMask != 0;
            out.add(new Row(Lang.tr("sc.singStation.row.lagging"), lag ? Lang.tr("sc.singStation.row.count", Integer.bitCount(p.lagMask))
                    : Lang.tr("sc.singStation.row.none"), lag ? OK : DIM, lag ? TEXT : DIM, TIP_LAG));
            out.add(new Row(Lang.tr("sc.singStation.row.top"), p.top > 0 ? Lang.tr("sc.singStation.lv", p.top) : "-",
                    lag ? OK : DIM, lag ? TEXT : DIM, TIP_TOP));
        } else if (p.candidates == 0) {
            out.add(new Row(Lang.tr("sc.singStation.row.points"), Lang.tr("sc.singStation.row.nopieces"), DIM, DIM, TIP_POINTS));
            out.add(new Row(Lang.tr("sc.singStation.row.task"), "-", DIM, DIM, TIP_TASK));
        } else {
            out.add(new Row(Lang.tr("sc.singStation.row.points"), Lang.tr("sc.singStation.row.points.v", p.pointsFull, p.candidates),
                    p.pointsFull > 0 ? OK : BAD, p.pointsFull > 0 ? TEXT : BAD, TIP_POINTS));
            out.add(new Row(Lang.tr("sc.singStation.row.task"), Lang.tr(p.taskDone ? "sc.singStation.row.task.done"
                    : "sc.singStation.row.task.todo", p.taskLevel), p.taskDone ? OK : BAD, p.taskDone ? TEXT : BAD, TIP_TASK));
        }
        for (int r = 0; r < R; r++) {
            boolean none = p.cost[r] <= 0;
            String v = none ? "-" : amount(p.have[r]) + " / " + amount(p.cost[r]) + (r == SingularStationMath.R_EU ? " EU" : "");
            int sq = resColor(p, r);
            boolean lack = !none && (p.process ? (te.getShortMask() & 1 << r) != 0 : p.have[r] < p.cost[r]);
            out.add(new Row(resName(r), v, sq, none ? DIM : lack ? BAD : TEXT, TIP_RES + r));
        }
        return out;
    }

    private int resColor(Plan p, int r) {
        if (p.process) {
            return (te.getShortMask() & 1 << r) != 0 ? BAD : p.have[r] >= p.cost[r] ? OK : BLUE;
        }
        if (p.cost[r] <= 0) {
            return DIM;
        }
        return p.have[r] >= p.cost[r] ? OK : BAD;
    }

    // ------------------------------------------------------------------ buttons, tabs, slots

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        L = width >= BIG.w && height >= BIG.h ? BIG : SMALL;
        xSize = L.w;
        ySize = L.h;
        super.initGui();
        buttonList.clear();
        int x = guiLeft, y = guiTop;
        // the machines' power logic (double click beside a too strong line, the gauge's OFF shade); its own buttons are not used
        power = new GuiPowerSC(te, ContainerSingularStationSC.BTN_POWER, ContainerSingularStationSC.BTN_REDSTONE, L.enX, HIDDEN,
                L.enW, L.enY, L.enH);
        for (int k = 0; k < TABS; k++) {
            tabs[k] = new TabBtn(k, x + L.tabX + k * L.tabStep, y + L.tabY, L.tabW, L.tabH);
            buttonList.add(tabs[k]);
        }
        action = add(new Btn(ID_ACTION, x + L.px + 5, y + L.actY, L.pw - 10, L.actH, ACCENT));
        cancel = add(new Btn(ID_CANCEL, x + L.px + 5, y + L.cancelY, L.cancelW, L.cancelH, 0xFFFF8A80));
        int bw = (L.pw - 14) / 2, bh = L.big ? 13 : 11;
        for (int k = 0; k < 4; k++) {
            branch[k] = add(new Btn(ContainerSingularStationSC.BTN_BRANCH + k, x + L.px + 5 + (k % 2) * (bw + 4), y + branchBtnY(k / 2), bw, bh, ACCENT));
        }
        powerBtn = add(new Btn(ContainerSingularStationSC.BTN_POWER, gridX(0), gridY(0), L.colW, L.btnH, TEXT));
        charge = add(new Btn(ContainerSingularStationSC.BTN_CHARGE, gridX(1), gridY(0), L.colW, L.btnH, OK));
        fill = add(new Btn(ContainerSingularStationSC.BTN_FILL, gridX(0), gridY(1), L.colW, L.btnH, OK));
        redstone = add(new Btn(ContainerSingularStationSC.BTN_REDSTONE, gridX(1), gridY(1), L.colW, L.btnH, TEXT));
        helium = add(new Btn(ContainerSingularStationSC.BTN_HELIUM, gridX(0), gridY(2), L.colW, L.btnH, BLUE));
        allGases = add(new Btn(ID_ALL_GASES, gridX(1), gridY(2), L.colW, L.btnH, BLUE));
        prev = add(new Btn(ContainerSingularStationSC.BTN_SCHEME_PREV, x + L.schemeX, y + L.schemeY, 10, 12, ACCENT));
        next = add(new Btn(ContainerSingularStationSC.BTN_SCHEME_NEXT, x + L.schemeX + L.schemeW - 10, y + L.schemeY, 10, 12, ACCENT));
        for (Gas g : Gas.values()) {
            int gx = x + L.rx + g.ordinal() * GAUGE_STEP;
            gasBtn[g.ordinal()] = new GasBtn(ContainerSingularStationSC.BTN_GAS + g.ordinal(), gx, y + L.gasY, g);
            buttonList.add(gasBtn[g.ordinal()]);
            GuiBigSC.ClearButton c = new GuiBigSC.ClearButton(ContainerSingularStationSC.BTN_CLEAR + g.ordinal());
            c.visible = true;
            c.xPosition = gx + (GAUGE_W - 7) / 2 + 1;
            c.yPosition = y + L.clearY;
            clear[g.ordinal()] = c;
            buttonList.add(c);
        }
        selectTab(tab);
    }

    private int gridX(int col) {
        return guiLeft + L.gridX + col * (L.colW + L.colGap);
    }

    private int gridY(int row) {
        return guiTop + L.gridY + row * L.gridStep;
    }

    /** The «Ветки» tab: level 3's buttons (k 0), level 5's (k 1), screen-local. */
    private int branchBtnY(int k) {
        int bh = L.big ? 13 : 11;
        return L.rowsY + 9 + k * (bh + 3 + 9);
    }

    private int branchTextY(int k) {
        int bh = L.big ? 13 : 11;
        return L.rowsY + k * (bh + 3 + 9);
    }

    @SuppressWarnings("unchecked")
    private Btn add(Btn b) {
        buttonList.add(b);
        return b;
    }

    /** Opens tab t: remembered for the session, told to the server (its slots then take items, the others none). */
    private void selectTab(int t) {
        tab = Math.max(0, Math.min(TABS - 1, t));
        lastTab = tab;
        box.setTab(tab);
        if (mc != null && mc.playerController != null) {
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, ContainerSingularStationSC.BTN_TAB + tab);
        }
        placeSlots();
    }

    /** A newly started process opens its tab (once - the player may look at another one meanwhile). */
    private void autoTab() {
        SingularProcessSC proc = te.getProcess();
        int kind = proc == null ? -1 : proc.kind;
        if (kind != seenKind) {
            seenKind = kind;
            if (proc != null && tabOf(proc) != tab) {
                selectTab(tabOf(proc));
            }
        }
    }

    /** Every slot where this layout and tab put it; the other tabs' slots off screen. */
    private void placeSlots() {
        List<?> slots = inventorySlots.inventorySlots;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = (Slot) slots.get(i);
            int sx = HIDDEN, sy = HIDDEN;
            if (i < TileEntityArmorStationSC.SLOTS) {
                sx = 7;
                sy = rowY(i) + L.slotDY;
            } else if (i < TileEntityArmorStationSC.ALL_SLOTS) {
                int m = i - TileEntityArmorStationSC.FIRST_UPGRADE_SLOT;
                sx = L.modX + (m % L.modCols) * 18;
                sy = L.modY + (m / L.modCols) * 18;
            } else if (i == TileEntitySingularStationSC.DONOR_SLOT) {
                if (tab == T_TRANSFER) {
                    sx = 7;
                    sy = L.modeSlotY;
                }
            } else if (i == TileEntitySingularStationSC.CATALYST_SLOT) {
                if (tab == T_MODERN) {
                    sx = 7;
                    sy = L.modeSlotY;
                } else if (tab == T_CONVERT) {
                    sx = L.coreX;
                    sy = L.coreY;
                }
            } else if (i < TileEntitySingularStationSC.SING_SLOTS) {
                if (tab == T_CONVERT) {
                    int m = i - TileEntitySingularStationSC.MATERIAL_SLOT;
                    sx = matX(m);
                    sy = matY(m);
                }
            } else {
                int k = i - TileEntitySingularStationSC.SING_SLOTS;
                if (k < 27) {
                    sx = L.invX + (k % 9) * 18;
                    sy = L.invY + (k / 9) * 18;
                } else {
                    sx = L.invX + (k - 27) * 18;
                    sy = L.hotbarY;
                }
            }
            s.xDisplayPosition = sx;
            s.yDisplayPosition = sy;
        }
    }

    private int matX(int m) {
        return 7 + (m % ContainerSingularStationSC.MAT_COLS) * 18;
    }

    private int matY(int m) {
        return L.modeSlotY + (m / ContainerSingularStationSC.MAT_COLS) * 18;
    }

    private int rowY(int i) {
        return L.rowY0 + i * L.rowStep;
    }

    /** The station slots of the other tabs that hold something (bit per tab whose slots they are). */
    private int hiddenItems() {
        int tabsBits = 0;
        for (int slot = TileEntitySingularStationSC.DONOR_SLOT; slot < TileEntitySingularStationSC.SING_SLOTS; slot++) {
            if (te.getStackInSlot(slot) != null && !ContainerSingularStationSC.slotInTab(tab, slot)) {
                for (int t = 0; t < TABS; t++) {
                    if (ContainerSingularStationSC.slotInTab(t, slot)) {
                        tabsBits |= 1 << t;
                    }
                }
            }
        }
        return tabsBits;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= ID_TAB && button.id < ID_TAB + TABS) {
            selectTab(button.id - ID_TAB);
            return;
        }
        int win = inventorySlots.windowId;
        if (button.id == ID_ACTION) {
            int id = tab == T_CONVERT ? ContainerSingularStationSC.BTN_CONVERT : tab == T_TRANSFER ? ContainerSingularStationSC.BTN_TRANSFER
                    : tab == T_SYNC ? ContainerSingularStationSC.BTN_SYNC : ContainerSingularStationSC.BTN_MODERNISE;
            if (tab != T_BRANCH) {
                mc.playerController.sendEnchantPacket(win, id);
            }
            return;
        }
        if (button.id == ID_CANCEL) {
            long now = System.currentTimeMillis();
            if (now - cancelArmed > ARM_MS) {              // «Отменить» loses half: a second click within 1.5 s
                cancelArmed = now;
                return;
            }
            cancelArmed = 0;
            mc.playerController.sendEnchantPacket(win, ContainerSingularStationSC.BTN_CANCEL);
            return;
        }
        if (button.id == ID_ALL_GASES) {
            for (Gas g : Gas.values()) {
                if (!te.gasEnabled(g)) {
                    mc.playerController.sendEnchantPacket(win, ContainerSingularStationSC.BTN_GAS + g.ordinal());
                }
            }
            return;
        }
        if (power.allowClick(button)) {
            mc.playerController.sendEnchantPacket(win, button.id);
        }
    }

    private boolean armed() {
        return System.currentTimeMillis() - cancelArmed <= ARM_MS;
    }

    /** Labels, enabled and visible states, every frame. */
    private void refreshButtons(Plan p) {
        SingularProcessSC proc = te.getProcess();
        boolean running = proc != null;
        for (int k = 0; k < TABS; k++) {
            tabs[k].displayString = Lang.tr("sc.singStation.tab." + k + (L.big ? "" : ".short"));
        }
        action.visible = tab != T_BRANCH;
        action.displayString = Lang.tr(tab == T_CONVERT ? "sc.singStation.btn.convert" : tab == T_TRANSFER ? "sc.singStation.btn.dotransfer"
                : tab == T_SYNC ? "sc.singStation.btn.dosync" : "sc.singStation.btn.modernise");
        action.color = tab == T_CONVERT ? YELLOW : ACCENT;
        action.enabled = p.can;
        cancel.displayString = Lang.tr(armed() ? "sc.singStation.btn.cancel.sure" : "sc.singStation.btn.cancel");
        cancel.enabled = running;
        ItemStack chest = te.getStackInSlot(com.sc.util.ArmorGasSC.CHEST);
        for (int k = 0; k < 4; k++) {
            int level = k < 2 ? 3 : 5, side = k % 2 + 1;
            ArmorFeature f = SingularLevel.branchFeature(level, side);
            branch[k].visible = tab == T_BRANCH;
            branch[k].displayString = f == null ? "?" : Lang.tr("sc.armorfn." + f.name().toLowerCase(java.util.Locale.ROOT));
            boolean current = SingularLevel.branchChoice(chest, level) == side;
            branch[k].selected = current;
            branch[k].enabled = SingularLevel.isSingular(chest) && SingularLevel.levelOf(chest) >= level && !current
                    && !te.isLocked(com.sc.util.ArmorGasSC.CHEST) && te.tankAmount(Gas.SINGULAR_MATTER) >= SingularStationMath.BRANCH_SM;
        }
        boolean danger = !te.isPowerOn() && te.lineTooStrong();
        powerBtn.displayString = Lang.tr("sc.singStation.btn.power", Lang.tr(te.isPowerOn() ? "sc.singStation.on" : "sc.singStation.off"));
        powerBtn.color = te.isPowerOn() ? OK : danger ? BAD : TEXT;
        powerBtn.selected = danger && power.isArmed();
        charge.displayString = Lang.tr("sc.singStation.btn.charge", Lang.tr(te.isChargeOn() ? "sc.singStation.yes" : "sc.singStation.no"));
        charge.color = te.isChargeOn() ? OK : DIM;
        fill.displayString = Lang.tr("sc.singStation.btn.fill", Lang.tr(te.isFillGases() ? "sc.singStation.yes" : "sc.singStation.no"));
        fill.color = te.isFillGases() ? OK : DIM;
        redstone.displayString = Lang.tr("sc.singStation.btn.redstone", Lang.tr("sc.singStation.rs." + Math.max(0, Math.min(2, te.getRedstoneMode()))));
        boolean heOnly = te.getGasMask() == 1 << Gas.HELIUM.ordinal();
        helium.displayString = Lang.tr("sc.armorStation.helium");
        helium.selected = heOnly;
        allGases.displayString = Lang.tr("sc.singStation.btn.allgases");
        allGases.enabled = te.getGasMask() != TileEntityArmorStationSC.ALL_GASES;
        prev.displayString = "<";
        next.displayString = ">";
        prev.enabled = next.enabled = te.shownScheme() != null;
        for (Gas g : Gas.values()) {
            clear[g.ordinal()].enabled = te.tankAmount(g) > 0 && te.getEnergyStored() >= te.clearCost(g);
        }
    }

    /** Armour slots with a Singular piece below the highest level there (bit per slot). */
    private int lagging() {
        int top = 0, bits = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = te.getStackInSlot(i);
            top = Math.max(top, SingularLevel.isSingular(s) ? SingularLevel.levelOf(s) : 0);
        }
        for (int i = 0; i < 4; i++) {
            ItemStack s = te.getStackInSlot(i);
            if (SingularLevel.isSingular(s) && SingularLevel.levelOf(s) < top) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    private int[] slotLevels() {
        int[] lv = new int[4];
        for (int i = 0; i < 4; i++) {
            ItemStack s = te.getStackInSlot(i);
            lv[i] = SingularLevel.isSingular(s) ? SingularLevel.levelOf(s) : 0;
        }
        return lv;
    }

    // ------------------------------------------------------------------ drawing

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            drawRect(x, y, x + w, y + h, c);
        }
    }

    private static void frame(int x, int y, int w, int h, int fill, int edge) {
        rect(x, y, w, h, edge);
        rect(x + 1, y + 1, w - 2, h - 2, fill);
    }

    private static void pocket(int x, int y, int edge) {
        rect(x - 1, y - 1, 18, 18, edge);
        rect(x, y, 16, 16, 0xFF0C0812);
        rect(x, y, 16, 1, 0xFF06040A);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        rect(x, y, L.w, L.h, 0xFF06040A);
        rect(x + 1, y + 1, L.w - 2, L.h - 2, EDGE);
        rect(x + 2, y + 2, L.w - 4, L.h - 4, BG);
        rect(x + 2, y + 2, L.w - 4, 12, 0xFF22163A);
        rect(x + 2, y + 14, L.w - 4, 1, EDGE_HI);
        rect(x + 4, y + L.sepY, L.w - 8, 1, EDGE);
        SingularProcessSC proc = te.getProcess();
        // the armour column
        for (int i = 0; i < 4; i++) {
            ItemStack s = te.getStackInSlot(i);
            boolean ready = s != null && SingularLevel.readyToUpgrade(me(), s);
            pocket(x + 7, y + rowY(i) + L.slotDY, te.isLocked(i) ? ACCENT : ready ? 0xFF3C9A4A : EDGE);
            if (SingularLevel.isSingular(s)) {
                int need = SingularLevel.threshold(SingularLevel.levelOf(s));
                float f = need <= 0 ? 1F : Math.min(1F, SingularLevel.points(s) / (float) need);
                int bx = x + L.nameX, by = y + rowY(i) + L.barDY, bw = L.barW;
                rect(bx - 1, by - 1, bw + 2, 6, 0xFF06040A);
                rect(bx, by, bw, 4, 0xFF2A2036);
                rect(bx, by, Math.round(bw * f), 4, ready ? 0xFF5AE66E : f >= 1F ? 0xFFC88CFF : 0xFF9A5AE0);
            }
        }
        // the open tab's slots
        if (tab == T_TRANSFER) {
            pocket(x + 7, y + L.modeSlotY, te.isLocked(TileEntitySingularStationSC.DONOR_SLOT) ? ACCENT : EDGE);
        } else if (tab == T_MODERN) {
            pocket(x + 7, y + L.modeSlotY, te.isLocked(TileEntitySingularStationSC.CATALYST_SLOT) ? ACCENT : 0xFF8A7020);
        } else if (tab == T_CONVERT) {
            for (int i = 0; i < TileEntitySingularStationSC.MATERIAL_SLOTS; i++) {
                pocket(x + matX(i), y + matY(i), 0xFF8A7020);
            }
            pocket(x + L.coreX, y + L.coreY, te.isLocked(TileEntitySingularStationSC.CATALYST_SLOT) ? ACCENT : 0xFF8A7020);
        }
        // the centre panel
        frame(x + L.px, y + L.py, L.pw, L.ph, PANEL, proc != null && tabOf(proc) == tab ? ACCENT : EDGE_HI);
        Plan p = plan();
        List<Row> rows = rows(p);
        for (int r = 0; r < rows.size(); r++) {
            rect(x + L.px + 5, y + L.rowsY + r * L.rowH + 1, 5, 5, rows.get(r).square | 0xFF000000);
        }
        float prog = proc == null ? 0F : (float) proc.progress;
        int bx = x + L.px + 5, bw = L.pw - 10;
        rect(bx, y + L.barY, bw, L.barH, 0xFF06040A);
        rect(bx + 1, y + L.barY + 1, bw - 2, L.barH - 2, 0xFF120C1A);
        rect(bx + 1, y + L.barY + 1, Math.round((bw - 2) * prog), L.barH - 2, te.getShortMask() != 0 || te.isPausedOff() ? 0xFFB07030 : 0xFF9A5AE0);
        // the right column: the machines' tank gauges, their switches, the machines' energy gauge
        for (Gas g : Gas.values()) {
            int gx = x + L.rx + g.ordinal() * GAUGE_STEP;
            GuiTankGaugeSC.drawCompact(mc, gx, y + L.gaugeY, GAUGE_W, L.gaugeH, te.getTank(g).getFluid(), te.tankCapacity(g), false);
            if (!te.isFillGases() || !te.gasEnabled(g)) {
                rect(gx, y + L.gaugeY, GAUGE_W, L.gaugeH, 0x80060410);
            }
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GuiEnergyGaugeSC.draw(x + L.enX, y + L.enY, L.enW, L.enH, te.getEnergyStored() / (float) Math.max(1, te.getMaxEnergyStored()));
        for (int i = 0; i < TileEntityArmorStationSC.UPGRADE_SLOTS; i++) {
            pocket(x + L.modX + (i % L.modCols) * 18, y + L.modY + (i / L.modCols) * 18, EDGE);
        }
        // the scheme's swatch, the preview
        SingularScheme sc = te.shownScheme();
        int swX = x + L.schemeX + 12, swY = y + L.schemeY;
        rect(swX, swY, 8, 12, sc == null ? EDGE : 0xFF06040A);
        rect(swX + 1, swY + 1, 6, 10, sc == null ? 0xFF2A2036 : 0xFF000000 | sc.accent);
        if (sc != null) {
            rect(swX + 1, swY + 8, 6, 3, 0xFF000000 | sc.base);
        }
        if (L.viewW > 0) {
            frame(x + L.viewX, y + L.viewY, L.viewW, L.viewH, 0xFF100A18, EDGE);
            drawPreview(x + L.viewX, y + L.viewY, L.viewW, L.viewH);
        }
        // the inventory
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                pocket(x + L.invX + c * 18, y + L.invY + r * 18, 0xFF3A2A50);
            }
        }
        for (int c = 0; c < 9; c++) {
            pocket(x + L.invX + c * 18, y + L.hotbarY, 0xFF3A2A50);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    // ------------------------------------------------------------------ the preview

    /** The armour in the slots (else a sample set, dimmed) on a slowly turning stand-in of the player. */
    private void drawPreview(int x, int y, int w, int h) {
        ItemStack[] set = new ItemStack[4];
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            ItemStack s = te.getStackInSlot(i);
            if (SingularLevel.isSingular(s)) {
                set[i] = s;
                any = true;
            }
        }
        if (!any) {
            set = sample();
        }
        EntityOtherPlayerMP e = dummy();
        if (e != null) {
            for (int i = 0; i < 4; i++) {
                e.inventory.armorInventory[3 - i] = set[i];      // the player's armour: 3 the helmet .. 0 the boots
            }
            int scale = Math.max(8, Math.round((h - 8) / 2.1F));
            float yaw = (Minecraft.getSystemTime() % 12000L) / 12000F * 360F;
            try {
                renderModel(x + w / 2, y + h - 4, scale, yaw, e);
            } catch (RuntimeException ex) {
                dummy = null;                                // a renderer that does not like the stand-in: no preview
            } finally {
                restoreGl();
            }
            for (int i = 0; i < 4; i++) {
                e.inventory.armorInventory[i] = null;
            }
        }
        if (!any) {
            rect(x + 1, y + 1, w - 2, h - 2, 0x90140E1C);
        }
    }

    private EntityOtherPlayerMP dummy() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }
        if (dummy == null || dummy.worldObj != mc.theWorld) {
            dummy = new EntityOtherPlayerMP(mc.theWorld, mc.thePlayer.getGameProfile()) {
                @Override
                public boolean isInvisibleToPlayer(EntityPlayer p) {
                    return true;                             // no name tag over the preview
                }

                @Override
                public boolean getAlwaysRenderNameTagForRender() {
                    return false;
                }
            };
        }
        return dummy;
    }

    /** A full Singular set in the default scheme, charged - the preview while no Singular piece lies in the slots. */
    private static ItemStack[] sample() {
        if (sample == null) {
            com.sc.item.ItemArmorSC[] a = com.sc.init.ModItems.ARMOR.get(com.sc.util.ArmorSuit.SINGULAR);
            ItemStack[] out = new ItemStack[4];
            for (int i = 0; a != null && i < 4; i++) {
                ItemStack s = new ItemStack(a[i]);
                SingularScheme.setScheme(s, SingularScheme.DEFAULT);
                com.sc.item.ItemArmorSC.setCharge(s, com.sc.item.ItemArmorSC.capacityOf(s));
                out[i] = s;
            }
            sample = out;
        }
        return sample;
    }

    /** GuiInventory.func_147046_a with its own yaw (the model turns, the mouse does not steer it). */
    private static void renderModel(int x, int y, int scale, float yaw, EntityOtherPlayerMP e) {
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glPushMatrix();
        float viewY = RenderManager.instance.playerViewY;
        try {
            GL11.glTranslatef(x, y, 50F);
            GL11.glScalef(-scale, scale, scale);
            GL11.glRotatef(180F, 0F, 0F, 1F);
            GL11.glRotatef(135F, 0F, 1F, 0F);
            RenderHelper.enableStandardItemLighting();
            GL11.glRotatef(-135F, 0F, 1F, 0F);
            GL11.glRotatef(-10F, 1F, 0F, 0F);
            e.renderYawOffset = e.prevRenderYawOffset = yaw;
            e.rotationYaw = e.prevRotationYaw = yaw;
            e.rotationYawHead = e.prevRotationYawHead = yaw;
            e.rotationPitch = e.prevRotationPitch = 0F;
            e.limbSwing = e.limbSwingAmount = e.prevLimbSwingAmount = 0F;
            GL11.glTranslatef(0F, e.yOffset, 0F);
            RenderManager.instance.playerViewY = 180F;
            RenderManager.instance.renderEntityWithPosYaw(e, 0D, 0D, 0D, 0F, 1F);
        } finally {
            RenderManager.instance.playerViewY = viewY;
            GL11.glPopMatrix();
        }
    }

    /** What the GUI expects after a model: no lighting, no depth test, the lightmap off, white. */
    private static void restoreGl() {
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    // ------------------------------------------------------------------ texts

    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    /** Right-aligned at `right` in at most maxW (shrinks, or is cut with a tooltip). */
    private void rightFit(String text, int right, int y, int maxW, int color) {
        int w = fontRendererObj.getStringWidth(text);
        if (w <= maxW) {
            fontRendererObj.drawString(text, right - w, y, color);
            return;
        }
        float k = Math.max(TextFitSC.MIN_SCALE, maxW / (float) w);
        int shown = Math.min(maxW, (int) Math.ceil(w * k));
        fit(text, right - shown, y, maxW, color);
    }

    /** Small text that shrinks (75% down to 60%) to fit maxW before it is cut (a short label in a narrow gap). */
    private void smallFit(String text, int x, int y, int maxW, int color) {
        int w = fontRendererObj.getStringWidth(text) - 1;
        float k = w * 0.75F <= maxW ? 0.75F : Math.max(0.6F, maxW / (float) Math.max(1, w));
        if (w * k > maxW + 0.01F) {
            small(text, x, y, maxW, color);
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y + (0.75F - k) * 4F, 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    /** Small (75%) text, cut to maxW GUI pixels; the full text as a tooltip when cut. */
    private void small(String text, int x, int y, int maxW, int color) {
        float k = 0.75F;
        String shown = text;
        if (fontRendererObj.getStringWidth(text) * k > maxW) {
            shown = fontRendererObj.trimStringToWidth(text, (int) (maxW / k) - fontRendererObj.getStringWidth("...")) + "...";
            TextFitSC.hover(guiLeft + x, guiTop + y, maxW, 7, text);
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(shown, 0, 0, color);
        GL11.glPopMatrix();
    }

    private int smallW(String text) {
        return (int) Math.ceil(fontRendererObj.getStringWidth(text) * 0.75F);
    }

    private void smallCentered(String text, int x, int y, int w, int color) {
        small(text, x + Math.max(0, (w - smallW(text)) / 2), y, w, color);
    }

    private void smallRight(String text, int right, int y, int maxW, int color) {
        small(text, right - Math.min(maxW, smallW(text)), y, maxW, color);
    }

    static String amount(long v) {
        return SingularStationMath.shortAmount(v, Lang.tr("sc.singStation.unit.k"), Lang.tr("sc.singStation.unit.m"), Lang.tr("sc.singStation.unit.b"));
    }

    static String time(int ticks) {
        return TileEntitySingularStationSC.timeText(ticks);
    }

    /** A resource's name in the checklist: Energy, SM, then the gases' full names. */
    private static String resName(int r) {
        if (r == SingularStationMath.R_EU) {
            return Lang.tr("sc.singStation.res.eu");
        }
        Gas g = SingularStationMath.GAS[r];
        return g == Gas.SINGULAR_MATTER ? com.sc.client.GasUiSC.shortName(g) : GuiArmorStationSC.gasName(g);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fit(Lang.tr("tile.siliconage.singularStation.name"), 6, 4, L.w - 50, TITLE);
        GuiGaugeSC.drawTierBadge(fontRendererObj, te.getTier(), L.w - 5, 3);
        Plan p = plan();
        drawLeft(p);
        drawPanel(p);
        drawRight();
        drawBottom();
    }

    private void drawLeft(Plan p) {
        if (L.armLabelY >= 0) {
            small(Lang.tr("sc.singStation.armour"), 7, L.armLabelY, L.leftR - 7, LABEL);
        }
        for (int i = 0; i < 4; i++) {
            ItemStack s = te.getStackInSlot(i);
            int ry = rowY(i);
            boolean sing = SingularLevel.isSingular(s);
            String lv = sing ? Lang.tr("sc.singStation.lv", SingularLevel.levelOf(s)) : "";
            int lw = fontRendererObj.getStringWidth(lv);
            fit(Lang.tr("sc.armorhud.piece." + i), L.nameX, ry + 1, L.lvRight - L.nameX - lw - 2, s != null ? TEXT : DIM);
            if (sing) {
                fontRendererObj.drawString(lv, L.lvRight - lw, ry + 1, ACCENT & 0xFFFFFF);
                int need = SingularLevel.threshold(SingularLevel.levelOf(s)), pts = SingularLevel.points(s);
                String line = need <= 0 ? Lang.tr("sc.singStation.maxlevel") : SingularLevel.readyToUpgrade(me(), s)
                        ? Lang.tr("sc.singStation.ready") : pts + " / " + need;
                small(line, L.nameX, ry + L.ptsDY, L.lvRight - L.nameX, need > 0 && pts >= need ? OK : LABEL);
            } else if (TileEntitySingularStationSC.isExo(s)) {
                small(Lang.tr("sc.singStation.exo"), L.nameX, ry + L.barDY, L.lvRight - L.nameX, YELLOW);
            } else if (s != null) {
                small(Lang.tr("sc.singStation.notsingular"), L.nameX, ry + L.barDY, L.lvRight - L.nameX, DIM);
            }
        }
        int tw = L.extraTextW, labelW = (L.warnY >= 0 ? L.leftR : L.warnX - 2) - 7;   // compact: room for the "!" marker
        if (tab == T_MODERN) {
            small(Lang.tr("sc.singStation.catalyst.label"), 7, L.modeLabelY, labelW, LABEL);
            String t = p.process ? Lang.tr("sc.singStation.locked.short") : !p.catalystNeeded ? Lang.tr("sc.singStation.catalyst.for")
                    : p.catalystOk ? Lang.tr("sc.singStation.catalyst.ok") : Lang.tr("sc.singStation.catalyst.need");
            small(t, L.extraTextX, L.modeSlotY + 4, tw, p.catalystNeeded && !p.catalystOk && !p.process ? WARN : p.catalystOk ? OK : DIM);
        } else if (tab == T_CONVERT) {
            small(Lang.tr("sc.singStation.materials"), 7, L.modeLabelY, L.coreX - 10, !p.process && p.convMask != 0 && !p.matOk ? WARN : LABEL);
            small(Lang.tr("sc.singStation.catalyst.short"), L.coreX - 1, L.coreLabelY, L.leftR - L.coreX + 1, LABEL);
        } else if (tab == T_TRANSFER) {
            small(Lang.tr("sc.singStation.donor"), 7, L.modeLabelY, labelW, LABEL);
            ItemStack d = te.getStackInSlot(TileEntitySingularStationSC.DONOR_SLOT);
            int t = te.transferTarget();
            if (SingularLevel.isSingular(d)) {
                small(Lang.tr("sc.singStation.lv", SingularLevel.levelOf(d)), L.extraTextX, L.modeSlotY + 1, tw, TEXT);
                small(t >= 0 ? "→ " + Lang.tr("sc.armorhud.piece." + t) : Lang.tr("sc.singStation.donor.notarget"), L.extraTextX, L.modeSlotY + 9, tw,
                        t >= 0 ? OK : WARN);
            } else {
                small(Lang.tr("sc.singStation.donor.empty"), L.extraTextX, L.modeSlotY + 4, tw, DIM);
            }
        }
        if (hiddenItems() != 0) {
            if (L.warnY >= 0) {
                List<String> lines = wrapSmall(Lang.tr("sc.singStation.hidden"), L.leftR - L.warnX);
                for (int k = 0; k < lines.size() && k < 2; k++) {
                    small(lines.get(k), L.warnX, L.warnY + k * 7, L.leftR - L.warnX, YELLOW);
                }
            } else {
                rect(L.warnX, L.modeLabelY - 1, 7, 8, 0xFFB08A20);
                fontRendererObj.drawString("!", L.warnX + 2, L.modeLabelY - 1, 0x281400);
            }
        }
    }

    /** Small text broken into lines of at most maxW GUI pixels. */
    @SuppressWarnings("unchecked")
    private List<String> wrapSmall(String text, int maxW) {
        return fontRendererObj.listFormattedStringToWidth(text, (int) (maxW / 0.75F));
    }

    private void drawPanel(Plan p) {
        SingularProcessSC proc = te.getProcess();
        TextFitSC.drawCentered(fontRendererObj, p.header, L.px + 3, L.headY, L.pw - 6, TEXT, false, guiLeft, guiTop);
        if (tab == T_BRANCH) {
            drawBranches();
        } else {
            List<Row> rows = rows(p);
            int nameX = L.px + 14, right = L.px + L.pw - 6;
            for (int r = 0; r < rows.size(); r++) {
                Row row = rows.get(r);
                int ry = L.rowsY + r * L.rowH;
                int nw = TextFitSC.draw(fontRendererObj, row.name, nameX, ry, (right - nameX) / 2, row.color == BAD ? BAD : row.color == DIM ? DIM : TEXT,
                        false, guiLeft, guiTop);
                rightFit(row.value, right, ry, right - (nameX + nw + 6), row.color);
            }
        }
        // the multipliers (on «Ветки»: what a change costs)
        smallFit(mulLine(p), L.px + 5, L.mulY, L.pw - 10, tab == T_BRANCH ? LABEL : BLUE);
        // the progress bar's text
        String bar;
        int barColor = TEXT;
        if (proc != null) {
            int pct = (int) Math.round(proc.progress * 100);
            bar = (tabOf(proc) != tab ? Lang.tr("sc.singStation.proc." + proc.kind, pct) : pct + "%") + " · "
                    + Lang.tr("sc.singStation.bar.left", time(te.ticksLeft()));
            String why = te.isPausedOff() ? Lang.tr("sc.singStation.pause.off") : te.getShortMask() != 0 ? Lang.tr("sc.singStation.pause.res", shortNames()) : null;
            if (why != null) {
                bar += " · " + why;
                barColor = YELLOW;
            }
        } else {
            bar = p.ticks > 0 && tab != T_BRANCH ? Lang.tr("sc.singStation.willtake", time(p.ticks)) : Lang.tr("sc.singStation.proc.none");
            barColor = LABEL;
        }
        int textY = L.barY + (L.barH - 6) / 2;
        smallCentered(bar, L.px + 6, textY, L.pw - 12, barColor);
        // the status, right of «Отменить»
        int st = te.getStatus();
        String status = Lang.tr("sc.armorStation.status.label", Lang.tr("sc.armorStation.status." + st))
                + (te.getPlayers() > 0 ? ", " + Lang.tr("sc.armorStation.onpad", te.getPlayers()) : "");
        int sx = L.px + 5 + L.cancelW + 4, sw = L.px + L.pw - 5 - sx;
        smallRight(status, L.px + L.pw - 5, L.cancelY + (L.cancelH - 6) / 2, sw,
                st == TileEntityArmorStationSC.ST_WORKING ? OK : st == TileEntityArmorStationSC.ST_NO_ENERGY || st == TileEntityArmorStationSC.ST_NO_GAS ? YELLOW : LABEL);
    }

    private String mulLine(Plan p) {
        if (tab == T_BRANCH) {
            return Lang.tr("sc.singStation.branches.cost", SingularStationMath.BRANCH_SM);
        }
        String res = Lang.tr(te.hasResonance() ? "sc.singStation.yes" : "sc.singStation.no");
        int stab = te.getStabilisers();
        String rest = Lang.tr("sc.singStation.mulline", res, stab, SingularStationMath.MAX_STABILISERS, stab * SingularStationMath.STABILISER_PERCENT);
        if (tab != T_MODERN) {
            return rest;
        }
        SingularProcessSC proc = te.getProcess();
        int k = p.process ? Integer.bitCount(proc.mask & 15) : p.ready > 0 ? p.ready : p.candidates;
        String set = p.setDiscount ? Lang.tr("sc.singStation.mul.setshort", 100 - SingularStationMath.SET_PERCENT)
                : Lang.tr("sc.singStation.mul.partsshort", k);
        return set + " · " + rest;
    }

    private void drawBranches() {
        ItemStack chest = te.getStackInSlot(com.sc.util.ArmorGasSC.CHEST);
        for (int k = 0; k < 2; k++) {
            int level = k == 0 ? 3 : 5;
            int c = SingularLevel.branchChoice(chest, level);
            boolean open = SingularLevel.isSingular(chest) && SingularLevel.levelOf(chest) >= level;
            String now = !SingularLevel.isSingular(chest) ? Lang.tr("sc.singStation.branches.nochest") : !open ? Lang.tr("sc.singStation.branches.closed")
                    : c == SingularLevel.BRANCH_NONE ? Lang.tr("sc.singStation.branches.notchosen")
                    : Lang.tr("sc.armorfn." + SingularLevel.branchFeature(level, c).name().toLowerCase(java.util.Locale.ROOT));
            small(Lang.tr("sc.singStation.branches.level", level, now), L.px + 6, branchTextY(k) + 1, L.pw - 12, open ? TEXT : DIM);
        }
        int smY = branchBtnY(1) + (L.big ? 13 : 11) + 4;
        small(Lang.tr("sc.singStation.branches.sm", te.tankAmount(Gas.SINGULAR_MATTER)), L.px + 6, smY, L.pw - 12,
                te.tankAmount(Gas.SINGULAR_MATTER) >= SingularStationMath.BRANCH_SM ? OK : WARN);
    }

    private void drawRight() {
        small(Lang.tr("sc.singStation.tanks"), L.rx, L.tankLabelY, L.big ? 70 : 60, LABEL);
        power.drawGaugeOff(fontRendererObj);
        small(amount(te.getEnergyStored()) + " / " + amount(te.getMaxEnergyStored()) + " EU", L.enTextX, L.enTextY, L.enTextW, TEXT);
        smallFit(Lang.tr("sc.singStation.modules"), L.modLabelX, L.modLabelY, L.modLabelW, LABEL);
        String in = te.acceptsAnyVoltage() ? Lang.tr("sc.armorStation.modules.any") : te.inputTier().name();
        smallFit(Lang.tr("sc.singStation.modules.in", in), L.modInfoX, L.modInfoY, L.modInfoW, DIM);
        smallFit(Lang.tr("sc.singStation.modules.tank", amount(te.tankCapacity())), L.modInfoX, L.modInfoY + 7, L.modInfoW, DIM);
        // the scheme: ◄ swatch, the chestplate in it, the name ►
        if (L.schemeLabelY >= 0) {
            small(Lang.tr("sc.singStation.scheme.label"), L.schemeX, L.schemeLabelY, L.schemeW, LABEL);
        }
        SingularScheme sc = te.shownScheme();
        ItemStack icon = schemeIcon(sc);
        int ix = L.schemeX + 22;
        if (icon != null) {
            drawItem(icon, ix, L.schemeY, 0.75F);
        }
        int nx = ix + 14, nw = L.schemeX + L.schemeW - 12 - nx;
        fit(sc == null ? Lang.tr("sc.singStation.scheme.none") : Lang.tr(sc.langKey()), nx, L.schemeY + 2, nw, sc == null ? DIM : (sc.accent & 0xFFFFFF));
        if (L.viewW > 0) {
            boolean any = false;
            for (int i = 0; i < 4; i++) {
                any |= SingularLevel.isSingular(te.getStackInSlot(i));
            }
            small(Lang.tr("sc.singStation.preview"), L.viewX + 3, L.viewY + 3, 40, DIM);
            if (!any) {
                smallCentered(Lang.tr("sc.singStation.preview.sample"), L.viewX + 2, L.viewY + L.viewH - 9, L.viewW - 4, LABEL);
            }
        }
    }

    /** The chestplate in the scheme shown (the one in the slot, else a sample one). */
    private ItemStack schemeIcon(SingularScheme sc) {
        ItemStack chest = te.getStackInSlot(com.sc.util.ArmorGasSC.CHEST);
        if (SingularLevel.isSingular(chest)) {
            return chest;
        }
        ItemStack[] s = sample();
        if (s[1] == null) {
            return null;
        }
        ItemStack c = s[1].copy();
        SingularScheme.setScheme(c, sc == null ? SingularScheme.DEFAULT : sc);
        return c;
    }

    private void drawItem(ItemStack s, int x, int y, float k) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(k, k, 1F);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), s, 0, 0);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private void drawBottom() {
        boolean lag = lagging() != 0 && te.getProcess() == null;
        small(Lang.tr(lag ? "sc.singStation.lagging" : "sc.singStation.hint"), L.hintX, L.hintY, L.hintW, lag ? YELLOW : DIM);
        small(Lang.tr("sc.singStation.hint.gas"), L.hintX, L.hintY + L.hintStep, L.hintW, DIM);
    }

    private String shortNames() {
        StringBuilder b = new StringBuilder();
        for (int r = 0; r < R; r++) {
            if ((te.getShortMask() & 1 << r) != 0) {
                b.append(b.length() > 0 ? ", " : "").append(resName(r));
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        autoTab();
        placeSlots();
        TextFitSC.beginFrame();
        refreshButtons(plan());
        super.drawScreen(mouseX, mouseY, partialTicks);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        List<String> tip = tipAt(mouseX, mouseY, mx, my);
        if (tip == null) {
            tip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tip != null && !tip.isEmpty()) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
    }

    private static boolean over(int x, int y, int w, int h, int mx, int my) {
        return GuiGaugeSC.isOver(x, y, w, h, mx, my);
    }

    private static boolean on(GuiButton b) {
        return b.visible && b.func_146115_a();
    }

    private List<String> tipAt(int mouseX, int mouseY, int mx, int my) {
        Plan p = plan();
        List<String> tip = new ArrayList<String>();
        // the tabs
        for (int k = 0; k < TABS; k++) {
            if (on(tabs[k])) {
                tip.add(Lang.tr("sc.singStation.tab." + k + ".name"));
                tip.add("§7" + Lang.tr("sc.singStation.tab." + k + ".hint"));
                SingularProcessSC proc = te.getProcess();
                if (proc != null && tabOf(proc) == k) {
                    tip.add("§d" + Lang.tr("sc.singStation.proc." + proc.kind, Math.round(proc.progress * 100)));
                }
                return tip;
            }
        }
        // the armour rows (not over the slot itself: the item's own tooltip)
        for (int i = 0; i < 4; i++) {
            if (over(L.nameX - 1, rowY(i), L.lvRight - L.nameX + 2, L.rowStep, mx, my)) {
                return pieceTip(i);
            }
        }
        if (hiddenItems() != 0 && (L.warnY >= 0 ? over(L.warnX, L.warnY, L.leftR - L.warnX, 14, mx, my)
                : over(L.warnX, L.modeLabelY - 1, 7, 8, mx, my))) {
            return hiddenTip();
        }
        List<String> mode = modeTip(p, mx, my);
        if (mode != null) {
            return mode;
        }
        // the checklist, the multipliers, the bar
        if (tab != T_BRANCH) {
            List<Row> rows = rows(p);
            for (int r = 0; r < rows.size(); r++) {
                if (over(L.px + 3, L.rowsY + r * L.rowH - 2, L.pw - 6, L.rowH, mx, my)) {
                    return rowTip(p, rows.get(r).tip);
                }
            }
        }
        if (over(L.px + 3, L.mulY - 1, L.pw - 6, 8, mx, my)) {
            if (tab == T_BRANCH) {
                tip.add(Lang.tr("sc.singStation.btn.branches"));
                tip.add(Lang.tr("sc.singStation.branches.hint", SingularStationMath.BRANCH_SM));
                return tip;
            }
            if (tab == T_MODERN) {
                tip.addAll(mulTip(0));
            }
            tip.addAll(mulTip(1));
            tip.addAll(mulTip(2));
            return tip;
        }
        if (over(L.px + 3, L.barY - 1, L.pw - 6, L.barH + 2, mx, my)) {
            tip.add(Lang.tr("sc.singStation.progress"));
            SingularProcessSC proc = te.getProcess();
            if (proc != null) {
                tip.add(Lang.tr("sc.singStation.proc." + proc.kind, Math.round(proc.progress * 100)));
                tip.add(Lang.tr("sc.singStation.left", time(te.ticksLeft()), Math.round(proc.progress * 100)));
                if (te.isPausedOff()) {
                    tip.add("§e" + Lang.tr("sc.singStation.pause.off"));
                } else if (te.getShortMask() != 0) {
                    tip.add("§e" + Lang.tr("sc.singStation.pause.res", shortNames()));
                }
            }
            tip.add("§7" + Lang.tr("sc.singStation.progress.hint"));
            return tip;
        }
        if (on(action)) {
            return actionTip(p);
        }
        if (on(cancel)) {
            return cancelTip();
        }
        for (int k = 0; k < 4; k++) {
            if (on(branch[k])) {
                ArmorFeature f = SingularLevel.branchFeature(k < 2 ? 3 : 5, k % 2 + 1);
                tip.add(branch[k].displayString);
                if (f != null) {
                    tip.add("§7" + Lang.tr("sc.armorfn." + f.name().toLowerCase(java.util.Locale.ROOT) + ".desc"));
                }
                tip.add(branch[k].selected ? "§a" + Lang.tr("sc.singStation.branches.current")
                        : Lang.tr("sc.singStation.branches.pick", SingularStationMath.BRANCH_SM));
                return tip;
            }
        }
        if (over(L.px + 5 + L.cancelW + 2, L.cancelY, L.pw - 10 - L.cancelW - 2, L.cancelH, mx, my)) {
            tip.add(Lang.tr("sc.armorStation.status.label", Lang.tr("sc.armorStation.status." + te.getStatus())));
            SingularProcessSC proc = te.getProcess();
            tip.add(proc == null ? Lang.tr("sc.singStation.proc.none") : Lang.tr("sc.singStation.proc." + proc.kind, Math.round(proc.progress * 100)));
            tip.add(Lang.tr("sc.singStation.speedline", te.getStabilisers(), SingularStationMath.MAX_STABILISERS,
                    Lang.tr(te.hasResonance() ? "sc.singStation.yes" : "sc.singStation.no"), oneDecimal(te.speed())));
            return tip;
        }
        // the tanks: the x, the gauge, the gas switch
        for (Gas g : Gas.values()) {
            int gx = L.rx + g.ordinal() * GAUGE_STEP;
            if (clear[g.ordinal()].over(mouseX, mouseY)) {
                return GuiBigSC.clearTip(te.tankAmount(g), te.clearCost(g), te.getEnergyStored());
            }
            if (over(gx, L.gaugeY, GAUGE_W, L.gaugeH, mx, my)) {
                return tankTip(g);
            }
            if (over(gx, L.gasY, GAUGE_W, GAS_H, mx, my)) {
                tip.add(GuiArmorStationSC.gasName(g));
                tip.add(Lang.tr("sc.armorStation.gasuse." + g.key()));
                tip.add(Lang.tr(te.gasEnabled(g) ? "sc.armorStation.gas.on" : "sc.armorStation.gas.off"));
                if (!te.isFillGases()) {
                    tip.add("§e" + Lang.tr("sc.singStation.fill.offnote"));
                }
                return tip;
            }
        }
        if (over(L.enX, L.enY, L.enW, L.enH, mx, my) || over(L.enTextX, L.enTextY - 1, L.enTextW, 8, mx, my)) {
            tip.add(Lang.tr("sc.gui.energy"));
            tip.add(te.getEnergyStored() + " / " + te.getMaxEnergyStored() + " EU");
            tip.add(te.acceptsAnyVoltage() ? Lang.tr("sc.armorStation.modules.anyinput")
                    : Lang.tr("sc.gui.input", te.inputTier().name(), te.inputTier().getVoltage()));
            if (!te.isPowerOn()) {
                tip.add("§c" + Lang.tr("sc.gui.power.off"));
            }
            tip.add("§7" + Lang.tr("sc.singStation.energy.hint"));
            return tip;
        }
        if (over(L.modLabelX, L.modLabelY - 1, L.modLabelW, 8, mx, my) || over(L.modInfoX, L.modInfoY - 1, L.modInfoW, 15, mx, my)) {
            tip.add(Lang.tr("sc.gui.upgrades"));
            tip.add(Lang.tr("sc.armorStation.modules.hint"));
            String in = te.acceptsAnyVoltage() ? Lang.tr("sc.armorStation.modules.any") : te.inputTier().name();
            tip.add(Lang.tr("sc.singStation.modules.line", in, te.tankCapacity()));
            tip.add(Lang.tr("sc.armorStation.modules.charge", te.chargePerRound() / TileEntityArmorStationSC.EVERY));
            tip.add(Lang.tr("sc.singStation.modules.tanks", te.tankCapacity(), te.tankCapacity(Gas.SINGULAR_MATTER)));
            return tip;
        }
        if (on(prev) || on(next) || over(L.schemeX + 10, L.schemeY, L.schemeW - 20, 12, mx, my)
                || L.schemeLabelY >= 0 && over(L.schemeX, L.schemeLabelY - 1, L.schemeW, 8, mx, my)) {
            SingularScheme sc = te.shownScheme();
            tip.add(Lang.tr("sc.singStation.scheme"));
            tip.add(sc == null ? "§7" + Lang.tr("sc.singStation.scheme.none") : Lang.tr(sc.langKey()));
            tip.add("§7" + Lang.tr("sc.singStation.scheme.hint"));
            return tip;
        }
        if (L.viewW > 0 && over(L.viewX, L.viewY, L.viewW, L.viewH, mx, my)) {
            tip.add(Lang.tr("sc.singStation.preview"));
            tip.add("§7" + Lang.tr("sc.singStation.preview.hint"));
            return tip;
        }
        // the switches
        if (on(powerBtn)) {
            return power.powerTip();
        }
        if (on(redstone)) {
            return power.redstoneTip();
        }
        if (on(charge)) {
            tip.add(charge.displayString);
            tip.add(Lang.tr("sc.singStation.charge.hint"));
            return tip;
        }
        if (on(fill)) {
            tip.add(fill.displayString);
            tip.add(Lang.tr("sc.armorStation.fill.hint"));
            return tip;
        }
        if (on(helium)) {
            tip.add(Lang.tr("sc.armorStation.helium"));
            tip.add(Lang.tr("sc.armorStation.helium.hint"));
            return tip;
        }
        if (on(allGases)) {
            tip.add(Lang.tr("sc.singStation.btn.allgases"));
            tip.add(Lang.tr("sc.singStation.allgases.hint"));
            return tip;
        }
        if (over(L.hintX, L.hintY - 1, L.hintW, 8, mx, my) && lagging() != 0 && te.getProcess() == null) {
            tip.add(Lang.tr("sc.singStation.lagging"));
            tip.add(Lang.tr("sc.singStation.lagging.hint"));
            return tip;
        }
        return null;
    }

    /** The open tab's slot labels (and an empty slot of it: the item tooltip is not there). */
    private List<String> modeTip(Plan p, int mx, int my) {
        List<String> tip = new ArrayList<String>();
        boolean emptyMode = false;
        if (tab == T_CONVERT) {
            for (int i = 0; i < TileEntitySingularStationSC.MATERIAL_SLOTS; i++) {
                emptyMode |= te.getStackInSlot(TileEntitySingularStationSC.MATERIAL_SLOT + i) == null
                        && over(matX(i) - 1, matY(i) - 1, 18, 18, mx, my);
            }
            if (emptyMode || over(6, L.modeLabelY - 1, L.coreX - 10, 8, mx, my)) {
                return materialsTip(p);
            }
            if (over(L.coreX - 1, L.coreLabelY - 1, L.leftR - L.coreX + 1, 8, mx, my)
                    || te.getStackInSlot(TileEntitySingularStationSC.CATALYST_SLOT) == null && over(L.coreX - 1, L.coreY - 1, 18, 18, mx, my)) {
                tip.add(Lang.tr("sc.singStation.catalyst"));
                tip.add("§7" + Lang.tr("sc.singStation.catalyst.convert"));
                return tip;
            }
            return null;
        }
        boolean emptySlot = over(6, L.modeSlotY - 1, 18, 18, mx, my);
        boolean text = over(6, L.modeLabelY - 1, L.leftR - 6, 8, mx, my) || over(L.extraTextX, L.modeSlotY - 1, L.extraTextW, 18, mx, my);
        if (tab == T_MODERN && (text || emptySlot && te.getStackInSlot(TileEntitySingularStationSC.CATALYST_SLOT) == null)) {
            tip.add(Lang.tr("sc.singStation.catalyst"));
            tip.add(Lang.tr("sc.singStation.catalyst.hint"));
            if (p.catalystNeeded) {
                tip.add((p.catalystOk ? "§a" : "§c") + Lang.tr(p.catalystOk ? "sc.singStation.tip.core" : "sc.singStation.tip.nocore"));
            }
            return tip;
        }
        if (tab == T_TRANSFER && (text || emptySlot && te.getStackInSlot(TileEntitySingularStationSC.DONOR_SLOT) == null)) {
            tip.add(Lang.tr("sc.singStation.donor"));
            tip.add(Lang.tr("sc.singStation.donor.hint"));
            return tip;
        }
        return null;
    }

    private List<String> hiddenTip() {
        List<String> tip = new ArrayList<String>();
        tip.add(Lang.tr("sc.singStation.hidden"));
        int bits = hiddenItems();
        StringBuilder b = new StringBuilder();
        for (int t = 0; t < TABS; t++) {
            if ((bits & 1 << t) != 0) {
                b.append(b.length() > 0 ? ", " : "").append(Lang.tr("sc.singStation.tab." + t + ".name"));
            }
        }
        tip.add("§7" + Lang.tr("sc.singStation.hidden.hint", b.toString()));
        return tip;
    }

    private List<String> tankTip(Gas g) {
        List<String> tip = new ArrayList<String>();
        int a = te.tankAmount(g), cap = te.tankCapacity(g);
        tip.add(GuiArmorStationSC.gasName(g));
        tip.add(Lang.tr("sc.armorStation.tank.amount", a, cap) + " " + GuiTankGaugeSC.percentLine(te.getTank(g).getFluid(), cap));
        if (a > cap) {
            tip.add("§6" + Lang.tr("sc.armorStation.tank.over"));
        }
        tip.add(Lang.tr(te.gasEnabled(g) ? "sc.armorStation.tank.on" : "sc.armorStation.tank.off"));
        if (a > 0) {
            tip.add("§7" + Lang.tr("sc.armorStation.tank.clearhint", te.clearCost(g)));
        }
        return tip;
    }

    private List<String> actionTip(Plan p) {
        List<String> tip = new ArrayList<String>();
        boolean busy = te.getProcess() != null;
        if (tab == T_TRANSFER) {
            tip.addAll(transferTip());
        } else if (tab == T_SYNC) {
            tip.addAll(syncTip());
        } else if (tab == T_CONVERT) {
            tip.add(Lang.tr("sc.singStation.btn.convert"));
            tip.add(Lang.tr("sc.singStation.convert.hint"));
            if (p.convMask != 0 && !p.process) {
                costLines(tip, p.cost, SingularStationMath.convertTicks(p.convMask));
                if (!p.matOk) {
                    tip.add("§c" + Lang.tr("sc.singStation.err.nomaterials"));
                }
            } else if (!p.process) {
                tip.add("§c" + Lang.tr("sc.singStation.err.noexo"));
            }
        } else {
            tip.add(Lang.tr("sc.singStation.btn.modernise"));
            tip.add(Lang.tr("sc.singStation.modernise.hint"));
            if (!p.process) {
                if (p.ready > 0) {
                    costLines(tip, p.cost, SingularStationMath.moderniseTicks(p.readyLv));
                } else {
                    tip.add("§c" + Lang.tr("sc.singStation.err.noready"));
                }
                if (p.catalystNeeded && !p.catalystOk) {
                    tip.add("§c" + Lang.tr("sc.singStation.err.nocatalyst"));
                }
            }
        }
        if (busy) {
            tip.add("§c" + Lang.tr("sc.singStation.err.busy"));
        }
        return tip;
    }

    private List<String> cancelTip() {
        List<String> tip = new ArrayList<String>();
        tip.add(Lang.tr("sc.singStation.btn.cancel"));
        SingularProcessSC proc = te.getProcess();
        if (proc == null) {
            tip.add("§7" + Lang.tr("sc.singStation.proc.none"));
            return tip;
        }
        tip.add(Lang.tr("sc.singStation.proc." + proc.kind, Math.round(proc.progress * 100)));
        tip.add(Lang.tr("sc.singStation.cancel.hint"));
        for (int r = 0; r < R; r++) {
            long back = SingularStationMath.refund(proc.drawn[r] - (r == 0 ? proc.catalystEu : 0));
            if (back > 0) {
                tip.add("§7" + resName(r) + ": +" + amount(back));
            }
        }
        if (proc.kind == SingularProcessSC.KIND_CONVERT) {
            tip.add("§7" + Lang.tr("sc.singStation.cancel.materials"));
        } else if (proc.catalystEu > 0) {
            tip.add("§7" + Lang.tr("sc.singStation.cancel.core", amount(SingularStationMath.refund(proc.catalystEu))));
        }
        tip.add("§e" + Lang.tr("sc.singStation.cancel.twice"));
        return tip;
    }

    private static String oneDecimal(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v).replace('.', ',').replaceAll(",?0+$", "");
    }

    private List<String> pieceTip(int i) {
        List<String> tip = new ArrayList<String>();
        ItemStack s = te.getStackInSlot(i);
        tip.add(Lang.tr("sc.armorhud.piece." + i));
        if (s == null) {
            tip.add("§7" + Lang.tr("sc.armorStation.part.empty"));
            return tip;
        }
        tip.add("§7" + s.getDisplayName());
        if (TileEntitySingularStationSC.isExo(s)) {
            tip.add("§e" + Lang.tr("sc.singStation.exo.hint"));
            if (te.isLocked(i)) {
                tip.add("§d" + Lang.tr("sc.singStation.locked"));
            }
            return tip;
        }
        if (!SingularLevel.isSingular(s)) {
            tip.add("§7" + Lang.tr("sc.singStation.notsingular.hint"));
            return tip;
        }
        int lvl = SingularLevel.levelOf(s), need = SingularLevel.threshold(lvl);
        tip.add(Lang.tr("sc.singStation.piece.level", lvl, SingularLevel.MAX));
        if (need > 0) {
            tip.add(Lang.tr("sc.singStation.piece.points", SingularLevel.points(s), need));
            boolean done = SingularLevel.taskDone(me(), lvl + 1);
            tip.add((done ? "§a" : "§c") + Lang.tr(done ? "sc.singStation.line.task.done" : "sc.singStation.line.task.todo", lvl + 1));
            tip.add(SingularLevel.readyToUpgrade(me(), s) ? "§a" + Lang.tr("sc.singStation.piece.ready") : "§7" + Lang.tr("sc.singStation.piece.notready"));
        } else {
            tip.add("§a" + Lang.tr("sc.singStation.maxlevel"));
        }
        tip.add("§7" + Lang.tr("sc.singStation.scheme") + ": " + Lang.tr(SingularScheme.of(s).langKey()));
        if (te.isLocked(i)) {
            tip.add("§d" + Lang.tr("sc.singStation.locked"));
        }
        return tip;
    }

    /** Б-1: what the conversion of the Exo pieces in the slots takes, have / need per material. */
    private List<String> materialsTip(Plan p) {
        List<String> tip = new ArrayList<String>();
        tip.add(Lang.tr("sc.singStation.materials"));
        tip.add("§7" + Lang.tr("sc.singStation.materials.hint"));
        if (p.convMask == 0 || p.process) {
            tip.add("§7" + Lang.tr("sc.singStation.materials.none"));
            return tip;
        }
        for (int k = 0; k < SingularStationMath.MATERIALS; k++) {
            if (p.matNeed[k] > 0) {
                tip.add((p.matHave[k] >= p.matNeed[k] ? "§a" : "§c") + TileEntitySingularStationSC.materialStack(k, 1).getDisplayName()
                        + ": " + Math.min(p.matHave[k], 999) + " / " + p.matNeed[k]);
            }
        }
        return tip;
    }

    private static String piecesOfMask(int mask) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if ((mask & 1 << i) != 0) {
                b.append(b.length() > 0 ? ", " : "").append(Lang.tr("sc.armorhud.piece." + i));
            }
        }
        return b.toString();
    }

    private List<String> rowTip(Plan p, int code) {
        List<String> tip = new ArrayList<String>();
        switch (code) {
            case TIP_PIECES:
                tip.add(Lang.tr("sc.singStation.line.pieces", piecesOfMask(p.procMask)));
                tip.add("§7" + Lang.tr("sc.singStation.locked"));
                return tip;
            case TIP_PROGRESS:
                SingularProcessSC proc = te.getProcess();
                tip.add(Lang.tr("sc.singStation.progress"));
                if (proc != null) {
                    tip.add(Lang.tr("sc.singStation.left", time(te.ticksLeft()), Math.round(proc.progress * 100)));
                }
                tip.add("§7" + Lang.tr("sc.singStation.progress.hint"));
                return tip;
            case TIP_EXO:
                tip.add(Lang.tr("sc.singStation.head.convert"));
                if (p.convMask != 0) {
                    tip.add(Lang.tr("sc.singStation.line.pieces", piecesOfMask(p.convMask)));
                }
                tip.add("§7" + Lang.tr("sc.singStation.convert.hint"));
                return tip;
            case TIP_MAT:
                return materialsTip(p);
            case TIP_DONOR:
                tip.add(Lang.tr("sc.singStation.donor"));
                tip.add(Lang.tr("sc.singStation.donor.hint"));
                return tip;
            case TIP_TARGET:
                return transferTip();
            case TIP_LAG:
                tip.add(Lang.tr("sc.singStation.lagging"));
                if (p.lagMask != 0) {
                    tip.add(Lang.tr("sc.singStation.line.pieces", piecesOfMask(p.lagMask)));
                }
                tip.add("§7" + Lang.tr("sc.singStation.lagging.hint"));
                return tip;
            case TIP_TOP:
                return syncTip();
            case TIP_POINTS:
                tip.add(Lang.tr("sc.singStation.tip.points"));
                for (int i = 0; i < 4; i++) {
                    ItemStack s = te.getStackInSlot(i);
                    if (SingularLevel.isSingular(s) && SingularLevel.levelOf(s) < SingularLevel.MAX) {
                        int need = SingularLevel.threshold(SingularLevel.levelOf(s));
                        tip.add((SingularLevel.pointsFull(s) ? "§a" : "§7") + Lang.tr("sc.armorhud.piece." + i) + ": " + SingularLevel.points(s) + " / " + need);
                    }
                }
                tip.add("§7" + Lang.tr("sc.singStation.tip.points.hint"));
                return tip;
            case TIP_TASK:
                tip.add(Lang.tr("sc.singStation.tip.task"));
                java.util.Set<Integer> targets = new java.util.TreeSet<Integer>();
                for (int i = 0; i < 4; i++) {
                    ItemStack s = te.getStackInSlot(i);
                    if (SingularLevel.isSingular(s) && SingularLevel.levelOf(s) < SingularLevel.MAX) {
                        targets.add(SingularLevel.levelOf(s) + 1);
                    }
                }
                for (int t : targets) {
                    tip.add(Lang.tr("sc.tooltip.armor.singular.tasks", t));
                    for (int k = 0; k < SingularLevel.TASKS; k++) {
                        int[] pr = SingularLevel.taskProgress(me(), t, k);
                        tip.add((pr[0] >= pr[1] ? "§a+ " : "§7- ") + Lang.tr("sc.tooltip.armor.singular.task." + t + "." + k, pr[0], pr[1]));
                    }
                }
                tip.add("§7" + Lang.tr("sc.singStation.tip.task.hint"));
                return tip;
            default:
                break;
        }
        int r = code - TIP_RES;
        tip.add(r == 0 ? Lang.tr("sc.singStation.res.eu.full") : GuiArmorStationSC.gasName(SingularStationMath.GAS[r]));
        if (p.process) {
            tip.add(Lang.tr("sc.singStation.tip.drawn", amount(p.have[r]), amount(p.cost[r])));
            if ((te.getShortMask() & 1 << r) != 0) {
                tip.add("§c" + Lang.tr(r == 0 ? "sc.singStation.tip.short.eu" : "sc.singStation.tip.short.gas"));
            }
        } else {
            tip.add(Lang.tr("sc.singStation.tip.need", amount(p.cost[r]), amount(p.have[r])));
            tip.add("§7" + Lang.tr("sc.singStation.tip.gradual"));
            if (r == 0 && tab == T_CONVERT) {
                tip.add("§7" + Lang.tr("sc.singStation.tip.cores"));
            }
            if (r == 0 && p.catalystNeeded) {
                tip.add((p.catalystOk ? "§a" : "§c") + Lang.tr(p.catalystOk ? "sc.singStation.tip.core" : "sc.singStation.tip.nocore"));
            }
        }
        return tip;
    }

    private List<String> mulTip(int m) {
        List<String> tip = new ArrayList<String>();
        if (m == 0) {
            tip.add(Lang.tr("sc.singStation.mul.set.head"));
            tip.add("§7" + Lang.tr("sc.singStation.mul.set.hint", 100 - SingularStationMath.SET_PERCENT));
        } else if (m == 1) {
            tip.add(Lang.tr("sc.singStation.mul.res.head"));
            tip.add("§7" + Lang.tr("sc.singStation.mul.res.hint", TileEntitySingularStationSC.RES_RADIUS, SingularStationMath.RESONANCE_SPEED_PERCENT,
                    100 - SingularStationMath.RESONANCE_EU_PERCENT));
        } else {
            tip.add(Lang.tr("sc.singStation.mul.stab.head"));
            tip.add("§7" + Lang.tr("sc.singStation.mul.stab.hint", TileEntitySingularStationSC.STAB_RADIUS, SingularStationMath.STABILISER_PERCENT,
                    SingularStationMath.MAX_STABILISERS));
            tip.add(Lang.tr("sc.singStation.mul.speed", oneDecimal(te.speed())));
        }
        return tip;
    }

    private void costLines(List<String> tip, long[] cost, int ticks) {
        StringBuilder b = new StringBuilder();
        for (int r = 0; r < R; r++) {
            if (cost[r] > 0) {
                b.append(b.length() > 0 ? " · " : "").append(resName(r)).append(' ').append(amount(cost[r]));
            }
        }
        tip.add(Lang.tr("sc.singStation.tip.cost", b.toString()));
        tip.add(Lang.tr("sc.singStation.willtake", time(SingularStationMath.duration(ticks, te.speed()))));
    }

    private List<String> transferTip() {
        List<String> tip = new ArrayList<String>();
        tip.add(Lang.tr("sc.singStation.btn.transfer"));
        tip.add(Lang.tr("sc.singStation.transfer.hint", SingularStationMath.TRANSFER_PERCENT));
        int t = te.transferTarget();
        if (t >= 0) {
            int dl = SingularLevel.levelOf(te.getStackInSlot(TileEntitySingularStationSC.DONOR_SLOT));
            tip.add("§a" + Lang.tr("sc.singStation.transfer.pair", dl, Lang.tr("sc.armorhud.piece." + t)));
            costLines(tip, SingularStationMath.transferCost(dl), SingularStationMath.transferTicks(dl));
        } else {
            tip.add("§c" + Lang.tr("sc.singStation.err.notransfer"));
        }
        return tip;
    }

    private List<String> syncTip() {
        List<String> tip = new ArrayList<String>();
        tip.add(Lang.tr("sc.singStation.btn.sync"));
        tip.add(Lang.tr("sc.singStation.sync.hint"));
        if (lagging() != 0) {
            int[] lv = slotLevels();
            costLines(tip, SingularStationMath.syncCost(lv), SingularStationMath.syncTicks(lv));
        } else {
            tip.add("§7" + Lang.tr("sc.singStation.err.nosync"));
        }
        return tip;
    }

    // ------------------------------------------------------------------ the buttons' look

    /** A violet bevel button: the label in its colour, dimmed when disabled; `selected` - a lit frame. */
    private class Btn extends GuiButton {
        int color;
        boolean selected;

        Btn(int id, int x, int y, int w, int h, int color) {
            super(id, x, y, w, h, "");
            this.color = color;
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            field_146123_n = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            boolean over = field_146123_n && enabled;
            rect(xPosition, yPosition, width, height, selected ? ACCENT : over ? 0xFFB48CE6 : 0xFF0A0610);
            rect(xPosition + 1, yPosition + 1, width - 2, height - 2, !enabled ? 0xFF221A2C : over ? 0xFF4A3466 : 0xFF33264A);
            rect(xPosition + 1, yPosition + 1, width - 2, 1, enabled ? 0xFF6A4E92 : 0xFF2C2238);
            int c = !enabled && !selected ? 0x5A5068 : color & 0xFFFFFF;
            TextFitSC.drawCentered(mc.fontRenderer, displayString, xPosition + 2, yPosition + (height - 8) / 2 + (height >= 13 ? 1 : 0),
                    width - 4, c, false, 0, 0);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /** A tab over the panel: the open one joins the panel; the one whose process runs has a lit dot. */
    private class TabBtn extends GuiButton {
        private final int index;

        TabBtn(int index, int x, int y, int w, int h) {
            super(ID_TAB + index, x, y, w, h, "");
            this.index = index;
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            field_146123_n = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            boolean open = index == tab;
            rect(xPosition, yPosition, width, height, open ? EDGE_HI : field_146123_n ? 0xFFB48CE6 : 0xFF0A0610);
            rect(xPosition + 1, yPosition + 1, width - 2, height - 1, open ? PANEL : field_146123_n ? 0xFF4A3466 : 0xFF33264A);
            if (!open) {
                rect(xPosition + 1, yPosition + 1, width - 2, 1, 0xFF6A4E92);
            }
            SingularProcessSC proc = te.getProcess();
            boolean running = proc != null && tabOf(proc) == index;
            int room = width - (running ? 7 : 4);
            TextFitSC.drawCentered(mc.fontRenderer, displayString, xPosition + 2, yPosition + (height - 8) / 2 + 1, room,
                    open ? TITLE : TEXT, false, 0, 0);
            if (running) {
                rect(xPosition + width - 4, yPosition + 2, 2, 2, (System.currentTimeMillis() / 500) % 2 == 0 ? ACCENT : 0xFF6A4E92);
            }
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /** A gas's switch under its gauge: the short name small, lit in the gas's colour while it is filled with, crossed out when off. */
    private class GasBtn extends GuiButton {
        private final Gas gas;

        GasBtn(int id, int x, int y, Gas gas) {
            super(id, x, y, GAUGE_W, GAS_H, "");
            this.gas = gas;
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            boolean on = te.gasEnabled(gas);
            rect(xPosition, yPosition, width, height, over ? 0xFFB48CE6 : on ? 0xFF000000 | gas.color : 0xFF3A2E48);
            rect(xPosition + 1, yPosition + 1, width - 2, height - 2, 0xFF100A18);
            String n = com.sc.client.GasUiSC.shortName(gas);
            float k = 0.5F;
            int tw = (int) (mc.fontRenderer.getStringWidth(n) * k);
            GL11.glPushMatrix();
            GL11.glTranslatef(xPosition + Math.max(1, (width - tw) / 2F), yPosition + 3F, 0F);
            GL11.glScalef(k, k, 1F);
            mc.fontRenderer.drawString(n, 0, 0, on && te.isFillGases() ? gas.color : 0x6E6280);
            GL11.glPopMatrix();
            if (!on) {                                       // off: crossed out
                for (int i = 0; i < width - 2; i++) {
                    int yy = yPosition + height - 2 - i * (height - 3) / Math.max(1, width - 3);
                    rect(xPosition + 1 + i, yy, 1, 1, 0xC0FF5A50);
                }
            }
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }
}
