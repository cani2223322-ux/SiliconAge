package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

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
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/**
 * The Singular Service Station's screen (docs/singular-armor/sing_2_station_gui.png), compact to fit
 * a 320 x 240 screen: left the four pieces with their level and points bar, the donor and catalyst
 * slots; centre the modernisation panel (target level, the checklist - points, task, EU and each
 * gas, have / need in colour -, the multipliers: set discount, resonance, stabilisers; the button,
 * the progress, the time left, «Отменить (50%)», «Ветки» - the branch panel in its place); right
 * the eight tank gauges with their gas switches, the SV energy bar, the modules; under it the
 * inventory and the service switches: power, charge, fill, level transfer, sync, the colour scheme.
 * Every condition has a tooltip.
 */
public class GuiSingularStationSC extends GuiContainer {

    private static final int W = ContainerSingularStationSC.W, H = ContainerSingularStationSC.H, R = SingularStationMath.RESOURCES;
    /** The centre panel and its rows. */
    private static final int PX = 98, PY = 17, PW = 125, PH = 131, HEAD_Y = 20, LINE_Y = 31, LINE = 9, MUL_Y = 95, MUL_STEP = 7,
            BTN_Y = 117, BAR_Y = 133, TIME_Y = 139;
    /** The right column: gauges, their switches, the energy bar, the modules. */
    private static final int RX = 226, GAUGE_Y = 27, GAUGE_W = 10, GAUGE_H = 45, GAUGE_STEP = 11, GAS_BTN_Y = 73,
            EN_LABEL_Y = 84, EN_Y = 92, EN_W = 89, EN_TEXT_Y = 100, MOD_LABEL_Y = 108, MOD_TEXT_Y = 137;
    /** The service area right of the inventory. */
    private static final int SX = 172, SW = 144, ROW1 = 153, ROW2 = 167, ROW3 = 182, STATUS_Y = 197;
    private static final int SEPARATOR_Y = 150;
    /** Colours (the A scheme: base (34,22,48), accent (190,110,255)). */
    private static final int BG = 0xFF140E1C, PANEL = 0xFF1E1629, EDGE = 0xFF4A3466, EDGE_HI = 0xFF7A56A8, ACCENT = 0xFFBE6EFF,
            TITLE = 0xD9A8FF, TEXT = 0xE8E0F4, LABEL = 0xA898C0, DIM = 0x6E6280, OK = 0x5AE66E, BAD = 0xFF5A50, WARN = 0xFFB040,
            YELLOW = 0xFFE14D, BLUE = 0x8CB4FF;
    private static final int TOGGLE_BRANCHES = 99;
    private static final long ARM_MS = 1500;

    private final TileEntitySingularStationSC te;
    private GuiPowerSC power;
    private Btn modernise, branches, charge, fill, transfer, sync, prev, next;
    private final Btn[] branch = new Btn[4];
    private final GasBtn[] gasBtn = new GasBtn[Gas.values().length];
    private boolean branchView;
    private long cancelArmed;

    public GuiSingularStationSC(InventoryPlayer inv, TileEntitySingularStationSC te) {
        super(new ContainerSingularStationSC(inv, te));
        this.te = te;
        xSize = W;
        ySize = H;
    }

    // ------------------------------------------------------------------ the plan the panel shows

    /** What the panel shows: a running process, or what the modernisation would take now. */
    private static final class Plan {
        String header;
        long[] cost = new long[R], have = new long[R];
        int ticks, ready, candidates, pointsFull, taskLevel;
        boolean taskDone, process, catalystNeeded, catalystOk, setDiscount;
        int[] readyLv = new int[4];
    }

    private EntityPlayer me() {
        return mc.thePlayer;
    }

    private Plan plan() {
        Plan p = new Plan();
        SingularProcessSC proc = te.getProcess();
        if (proc != null) {
            p.process = true;
            p.cost = proc.cost.clone();
            p.have = proc.drawn.clone();
            p.ticks = te.ticksLeft();
            if (proc.kind == SingularProcessSC.KIND_SYNC) {
                p.header = Lang.tr("sc.singStation.head.sync", proc.target);
            } else if (proc.kind == SingularProcessSC.KIND_TRANSFER) {
                p.header = Lang.tr("sc.singStation.head.transfer");
            } else {
                int lo = 9, hi = 0;
                for (int i = 0; i < 4; i++) {
                    if (proc.locks(i)) {
                        ItemStack s = te.getStackInSlot(i);
                        int l = SingularLevel.levelOf(s);
                        lo = Math.min(lo, l);
                        hi = Math.max(hi, l);
                    }
                }
                p.header = levelHeader(lo, hi);
            }
            p.setDiscount = proc.kind == SingularProcessSC.KIND_MODERNISE && Integer.bitCount(proc.mask & 15) == 4;
            return p;
        }
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
        p.have[SingularStationMath.R_EU] = te.getEnergyStored()
                + (p.catalystNeeded && p.catalystOk ? com.sc.item.ItemBatterySC.chargeOf(core) : 0L);
        for (int r = 1; r < R; r++) {
            p.have[r] = te.tankAmount(SingularStationMath.GAS[r]);
        }
        return p;
    }

    private static String levelHeader(int lo, int hi) {
        return lo == hi ? Lang.tr("sc.singStation.head.level", lo, lo + 1) : Lang.tr("sc.singStation.head.levels", lo, hi);
    }

    // ------------------------------------------------------------------ buttons

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        buttonList.clear();
        int x = guiLeft, y = guiTop;
        power = new GuiPowerSC(te, ContainerSingularStationSC.BTN_POWER, ContainerSingularStationSC.BTN_REDSTONE, SX, ROW1, 34, 0, 0);
        power.addButtons(buttonList, x, y);
        modernise = add(new Btn(ContainerSingularStationSC.BTN_MODERNISE, x + PX + 4, y + BTN_Y, 72, 13, ACCENT));
        branches = add(new Btn(TOGGLE_BRANCHES, x + PX + 78, y + BTN_Y, PW - 82, 13, YELLOW));
        for (int k = 0; k < 4; k++) {
            branch[k] = add(new Btn(ContainerSingularStationSC.BTN_BRANCH + k, x + PX + 6 + (k % 2) * 58, y + (k < 2 ? 54 : 80), 55, 12, ACCENT));
        }
        charge = add(new Btn(ContainerSingularStationSC.BTN_CHARGE, x + SX + 36, y + ROW1, 53, 11, OK));
        fill = add(new Btn(ContainerSingularStationSC.BTN_FILL, x + SX + 91, y + ROW1, 53, 11, OK));
        transfer = add(new Btn(ContainerSingularStationSC.BTN_TRANSFER, x + SX, y + ROW2, 71, 12, YELLOW));
        sync = add(new Btn(ContainerSingularStationSC.BTN_SYNC, x + SX + 73, y + ROW2, 71, 12, YELLOW));
        prev = add(new Btn(ContainerSingularStationSC.BTN_SCHEME_PREV, x + SX, y + ROW3, 14, 12, ACCENT));
        next = add(new Btn(ContainerSingularStationSC.BTN_SCHEME_NEXT, x + SX + SW - 14, y + ROW3, 14, 12, ACCENT));
        for (Gas g : Gas.values()) {
            gasBtn[g.ordinal()] = new GasBtn(ContainerSingularStationSC.BTN_GAS + g.ordinal(), x + RX + g.ordinal() * GAUGE_STEP, y + GAS_BTN_Y, g);
            buttonList.add(gasBtn[g.ordinal()]);
        }
    }

    @SuppressWarnings("unchecked")
    private Btn add(Btn b) {
        buttonList.add(b);
        return b;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == TOGGLE_BRANCHES) {
            branchView = !branchView;
            return;
        }
        if (button == modernise && te.getProcess() != null) {
            long now = System.currentTimeMillis();
            if (now - cancelArmed > ARM_MS) {              // «Отменить» loses half: a second click within 1.5 s
                cancelArmed = now;
                return;
            }
            cancelArmed = 0;
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, ContainerSingularStationSC.BTN_CANCEL);
            return;
        }
        if (power.allowClick(button)) {
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
        }
    }

    private boolean armed() {
        return System.currentTimeMillis() - cancelArmed <= ARM_MS;
    }

    /** Labels, enabled and visible states, every frame. */
    private void refreshButtons(Plan p) {
        boolean running = te.getProcess() != null;
        modernise.displayString = running ? Lang.tr(armed() ? "sc.singStation.btn.cancel.sure" : "sc.singStation.btn.cancel")
                : Lang.tr("sc.singStation.btn.modernise");
        modernise.color = running ? 0xFFFF8A80 : ACCENT;
        modernise.enabled = running || (p.ready > 0 && (!p.catalystNeeded || p.catalystOk));
        branches.displayString = Lang.tr(branchView ? "sc.singStation.btn.back" : "sc.singStation.btn.branches");
        ItemStack chest = te.getStackInSlot(com.sc.util.ArmorGasSC.CHEST);
        for (int k = 0; k < 4; k++) {
            int level = k < 2 ? 3 : 5, side = k % 2 + 1;
            ArmorFeature f = SingularLevel.branchFeature(level, side);
            branch[k].visible = branchView;
            branch[k].displayString = f == null ? "?" : Lang.tr("sc.armorfn." + f.name().toLowerCase(java.util.Locale.ROOT));
            boolean current = SingularLevel.branchChoice(chest, level) == side;
            branch[k].selected = current;
            branch[k].enabled = SingularLevel.isSingular(chest) && SingularLevel.levelOf(chest) >= level && !current
                    && !te.isLocked(com.sc.util.ArmorGasSC.CHEST) && te.tankAmount(Gas.SINGULAR_MATTER) >= SingularStationMath.BRANCH_SM;
        }
        charge.displayString = Lang.tr("sc.singStation.btn.charge", Lang.tr(te.isChargeOn() ? "sc.singStation.yes" : "sc.singStation.no"));
        charge.color = te.isChargeOn() ? OK : DIM;
        fill.displayString = Lang.tr("sc.singStation.btn.fill", Lang.tr(te.isFillGases() ? "sc.singStation.yes" : "sc.singStation.no"));
        fill.color = te.isFillGases() ? OK : DIM;
        transfer.displayString = Lang.tr("sc.singStation.btn.transfer");
        transfer.enabled = !running && te.transferTarget() >= 0;
        sync.displayString = Lang.tr("sc.singStation.btn.sync");
        sync.enabled = !running && lagging() != 0;
        prev.displayString = "<";
        next.displayString = ">";
        prev.enabled = next.enabled = te.shownScheme() != null;
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

    private static int rowY(int i) {
        return ContainerSingularStationSC.ROW_Y + i * ContainerSingularStationSC.ROW_STEP;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        rect(x, y, W, H, 0xFF06040A);
        rect(x + 1, y + 1, W - 2, H - 2, EDGE);
        rect(x + 2, y + 2, W - 4, H - 4, BG);
        rect(x + 2, y + 2, W - 4, 12, 0xFF22163A);
        rect(x + 2, y + 14, W - 4, 1, EDGE_HI);
        rect(x + 4, y + SEPARATOR_Y, W - 8, 1, EDGE);
        SingularProcessSC proc = te.getProcess();
        // the armour column
        for (int i = 0; i < 4; i++) {
            ItemStack s = te.getStackInSlot(i);
            boolean locked = te.isLocked(i);
            boolean ready = s != null && SingularLevel.readyToUpgrade(me(), s);
            pocket(x + ContainerSingularStationSC.PIECE_X, y + rowY(i) + 3, locked ? ACCENT : ready ? 0xFF3C9A4A : EDGE);
            if (SingularLevel.isSingular(s)) {
                int need = SingularLevel.threshold(SingularLevel.levelOf(s));
                float f = need <= 0 ? 1F : Math.min(1F, SingularLevel.points(s) / (float) need);
                int bx = x + 26, by = y + rowY(i) + 11, bw = 68;
                rect(bx - 1, by - 1, bw + 2, 6, 0xFF06040A);
                rect(bx, by, bw, 4, 0xFF2A2036);
                rect(bx, by, Math.round(bw * f), 4, f >= 1F ? 0xFFC88CFF : 0xFF9A5AE0);
            }
        }
        pocket(x + ContainerSingularStationSC.DONOR_X, y + ContainerSingularStationSC.EXTRA_Y, te.isLocked(TileEntitySingularStationSC.DONOR_SLOT) ? ACCENT : EDGE);
        pocket(x + ContainerSingularStationSC.CATALYST_X, y + ContainerSingularStationSC.EXTRA_Y,
                te.isLocked(TileEntitySingularStationSC.CATALYST_SLOT) ? ACCENT : 0xFF6A4A20);
        // the centre panel
        frame(x + PX, y + PY, PW, PH, PANEL, proc != null ? ACCENT : EDGE_HI);
        Plan p = plan();
        if (!branchView) {
            for (int r = 0; r < 7; r++) {
                rect(x + PX + 4, y + LINE_Y + r * LINE + 1, 5, 5, lineColor(p, r) | 0xFF000000);
            }
        }
        float prog = proc == null ? 0F : (float) proc.progress;
        rect(x + PX + 4, y + BAR_Y, PW - 8, 4, 0xFF06040A);
        rect(x + PX + 5, y + BAR_Y + 1, Math.round((PW - 10) * prog), 2, te.getShortMask() != 0 || te.isPausedOff() ? 0xFFB07030 : ACCENT);
        // the right column: tanks, energy, modules
        for (Gas g : Gas.values()) {
            int gx = x + RX + g.ordinal() * GAUGE_STEP;
            GuiTankGaugeSC.drawCompact(mc, gx, y + GAUGE_Y, GAUGE_W, GAUGE_H, te.getTank(g).getFluid(), te.tankCapacity(g), false);
            if (!te.isFillGases() || !te.gasEnabled(g)) {
                rect(gx, y + GAUGE_Y, GAUGE_W, GAUGE_H, 0x80060410);
            }
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
        float e = te.getEnergyStored() / (float) Math.max(1, te.getMaxEnergyStored());
        rect(x + RX - 1, y + EN_Y - 1, EN_W + 2, 8, 0xFF06040A);
        rect(x + RX, y + EN_Y, EN_W, 6, 0xFF2A2036);
        rect(x + RX, y + EN_Y, Math.round(EN_W * e), 6, GuiEnergyGaugeSC.colour(e));
        for (int i = 0; i < TileEntityArmorStationSC.UPGRADE_SLOTS; i++) {
            pocket(x + ContainerSingularStationSC.UPG_X + i * 18, y + ContainerSingularStationSC.UPG_Y, EDGE);
        }
        // the inventory
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                pocket(x + ContainerSingularStationSC.INV_X + c * 18, y + ContainerSingularStationSC.INV_Y + r * 18, 0xFF3A2A50);
            }
        }
        for (int c = 0; c < 9; c++) {
            pocket(x + ContainerSingularStationSC.INV_X + c * 18, y + ContainerSingularStationSC.HOTBAR_Y, 0xFF3A2A50);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** A checklist line's colour: 0 points, 1 task, 2.. the resources. */
    private int lineColor(Plan p, int line) {
        if (line < 2) {
            if (p.process) {
                return ACCENT & 0xFFFFFF;
            }
            if (p.candidates == 0) {
                return DIM;
            }
            return line == 0 ? (p.pointsFull > 0 ? OK : BAD) : (p.taskDone ? OK : BAD);
        }
        int r = line - 2;
        if (p.process) {
            return (te.getShortMask() & 1 << r) != 0 ? BAD : p.have[r] >= p.cost[r] ? OK : BLUE;
        }
        if (p.cost[r] <= 0) {
            return DIM;
        }
        return p.have[r] >= p.cost[r] ? OK : WARN;
    }

    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
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

    private void smallCentered(String text, int x, int y, int w, int color) {
        int tw = (int) (fontRendererObj.getStringWidth(text) * 0.75F);
        small(text, x + Math.max(0, (w - tw) / 2), y, w, color);
    }

    static String amount(long v) {
        return SingularStationMath.shortAmount(v, Lang.tr("sc.singStation.unit.k"), Lang.tr("sc.singStation.unit.m"), Lang.tr("sc.singStation.unit.b"));
    }

    static String time(int ticks) {
        return TileEntitySingularStationSC.timeText(ticks);
    }

    private static String resName(int r) {
        return r == SingularStationMath.R_EU ? Lang.tr("sc.singStation.res.eu") : com.sc.client.GasUiSC.shortName(SingularStationMath.GAS[r]);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fit(Lang.tr("tile.siliconage.singularStation.name"), 6, 4, W - 50, TITLE);
        GuiGaugeSC.drawTierBadge(fontRendererObj, te.getTier(), W - 5, 3);
        Plan p = plan();
        // the armour column
        for (int i = 0; i < 4; i++) {
            ItemStack s = te.getStackInSlot(i);
            int ry = rowY(i);
            boolean sing = SingularLevel.isSingular(s);
            fit(Lang.tr("sc.armorhud.piece." + i), 26, ry + 1, 44, s != null ? TEXT : DIM);
            if (sing) {
                String lv = Lang.tr("sc.singStation.lv", SingularLevel.levelOf(s));
                int lw = fontRendererObj.getStringWidth(lv);
                fontRendererObj.drawString(lv, 95 - lw, ry + 1, ACCENT & 0xFFFFFF);
                int need = SingularLevel.threshold(SingularLevel.levelOf(s));
                small(need > 0 ? SingularLevel.points(s) + " / " + need : Lang.tr("sc.singStation.maxlevel"), 26, ry + 17, 70,
                        need > 0 && SingularLevel.points(s) >= need ? OK : LABEL);
            } else if (s != null) {
                small(Lang.tr("sc.singStation.notsingular"), 26, ry + 11, 70, DIM);
            }
        }
        small(Lang.tr("sc.singStation.donor"), 6, ContainerSingularStationSC.EXTRA_Y - 9, 44, LABEL);
        small(Lang.tr("sc.singStation.catalyst"), 50, ContainerSingularStationSC.EXTRA_Y - 9, 46, LABEL);
        // the centre panel
        TextFitSC.drawCentered(fontRendererObj, branchView ? Lang.tr("sc.singStation.branches.head") : p.header, PX + 3, HEAD_Y, PW - 6, TEXT, false, guiLeft, guiTop);
        if (branchView) {
            drawBranches();
        } else {
            drawChecklist(p);
        }
        SingularProcessSC proc = te.getProcess();
        String timeLine;
        if (proc != null) {
            String why = te.isPausedOff() ? Lang.tr("sc.singStation.pause.off") : te.getShortMask() != 0 ? Lang.tr("sc.singStation.pause.res", shortNames()) : null;
            timeLine = Lang.tr("sc.singStation.left", time(te.ticksLeft()), Math.round(proc.progress * 100)) + (why != null ? " · " + why : "");
        } else {
            timeLine = p.ready > 0 || p.candidates > 0 ? Lang.tr("sc.singStation.willtake", time(p.ticks)) : "";
        }
        small(timeLine, PX + 4, TIME_Y, PW - 8, proc != null && (te.isPausedOff() || te.getShortMask() != 0) ? WARN : LABEL);
        // the right column
        small(Lang.tr("sc.singStation.tanks"), RX, 19, 88, LABEL);
        small(Lang.tr("sc.singStation.energy", te.getTier().name()), RX, EN_LABEL_Y, 88, LABEL);
        small(amount(te.getEnergyStored()) + " / " + amount(te.getMaxEnergyStored()) + " EU", RX, EN_TEXT_Y, 89, TEXT);
        small(Lang.tr("sc.singStation.modules"), RX, MOD_LABEL_Y, 88, LABEL);
        String in = te.acceptsAnyVoltage() ? Lang.tr("sc.armorStation.modules.any") : te.inputTier().name();
        small(Lang.tr("sc.singStation.modules.line", in, te.tankCapacity()), RX, MOD_TEXT_Y, 89, DIM);
        // the service area
        SingularScheme sc = te.shownScheme();
        TextFitSC.drawCentered(fontRendererObj, sc == null ? Lang.tr("sc.singStation.scheme.none") : Lang.tr(sc.langKey()),
                SX + 16, ROW3 + 2, SW - 32, sc == null ? DIM : (sc.accent & 0xFFFFFF), false, guiLeft, guiTop);
        int st = te.getStatus();
        small(Lang.tr("sc.armorStation.status.label", Lang.tr("sc.armorStation.status." + st))
                + (te.getPlayers() > 0 ? ", " + Lang.tr("sc.armorStation.onpad", te.getPlayers()) : ""), SX, STATUS_Y, SW,
                st == TileEntityArmorStationSC.ST_WORKING ? OK : st == TileEntityArmorStationSC.ST_NO_ENERGY || st == TileEntityArmorStationSC.ST_NO_GAS ? WARN : LABEL);
        small(processLine(), SX, STATUS_Y + 9, SW, proc != null ? ACCENT & 0xFFFFFF : LABEL);
        small(Lang.tr("sc.singStation.speedline", te.getStabilisers(), SingularStationMath.MAX_STABILISERS,
                Lang.tr(te.hasResonance() ? "sc.singStation.yes" : "sc.singStation.no"), oneDecimal(te.speed())), SX, STATUS_Y + 18, SW, LABEL);
        boolean lag = lagging() != 0 && te.getProcess() == null;
        small(Lang.tr(lag ? "sc.singStation.lagging" : "sc.singStation.hint"), SX, STATUS_Y + 27, SW, lag ? YELLOW : DIM);
    }

    private static String oneDecimal(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v).replace('.', ',').replaceAll(",?0+$", "");
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

    private String processLine() {
        SingularProcessSC proc = te.getProcess();
        if (proc == null) {
            return Lang.tr("sc.singStation.proc.none");
        }
        return Lang.tr("sc.singStation.proc." + proc.kind, Math.round(proc.progress * 100));
    }

    private void drawChecklist(Plan p) {
        int x = PX + 11, w = PW - 15;
        SingularProcessSC proc = te.getProcess();
        if (p.process) {
            fit(Lang.tr("sc.singStation.line.pieces", piecesOf(proc)), x, LINE_Y, w, TEXT);
            fit(Lang.tr("sc.singStation.line.progress", Math.round(proc.progress * 100)), x, LINE_Y + LINE, w, TEXT);
        } else if (p.candidates == 0) {
            fit(Lang.tr("sc.singStation.line.nopieces"), x, LINE_Y, w, DIM);
            fit(Lang.tr("sc.singStation.line.task.none"), x, LINE_Y + LINE, w, DIM);
        } else {
            fit(Lang.tr("sc.singStation.line.points", p.pointsFull, p.candidates), x, LINE_Y, w, TEXT);
            fit(Lang.tr(p.taskDone ? "sc.singStation.line.task.done" : "sc.singStation.line.task.todo", p.taskLevel), x, LINE_Y + LINE, w, TEXT);
        }
        for (int r = 0; r < R; r++) {
            String line = resName(r) + ": " + (p.cost[r] <= 0 ? "-" : amount(p.have[r]) + " / " + amount(p.cost[r]) + (r == 0 ? " EU" : " mB"));
            fit(line, x, LINE_Y + (2 + r) * LINE, w, p.cost[r] <= 0 ? DIM : TEXT);
        }
        // the multipliers
        int k = p.process ? Integer.bitCount(proc.mask & 15) : p.ready > 0 ? p.ready : p.candidates;
        boolean modern = !p.process || proc.kind == SingularProcessSC.KIND_MODERNISE;
        small(modern ? (p.setDiscount ? Lang.tr("sc.singStation.mul.set") : Lang.tr("sc.singStation.mul.noset", k)) : Lang.tr("sc.singStation.mul.other"),
                PX + 4, MUL_Y, PW - 8, p.setDiscount ? BLUE : DIM);
        small(te.hasResonance() ? Lang.tr("sc.singStation.mul.res") : Lang.tr("sc.singStation.mul.nores"), PX + 4, MUL_Y + MUL_STEP, PW - 8,
                te.hasResonance() ? BLUE : DIM);
        small(Lang.tr("sc.singStation.mul.stab", te.getStabilisers(), SingularStationMath.MAX_STABILISERS,
                te.getStabilisers() * SingularStationMath.STABILISER_PERCENT), PX + 4, MUL_Y + 2 * MUL_STEP, PW - 8, te.getStabilisers() > 0 ? BLUE : DIM);
    }

    private String piecesOf(SingularProcessSC proc) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (proc.locks(i)) {
                b.append(b.length() > 0 ? ", " : "").append(Lang.tr("sc.armorhud.piece." + i));
            }
        }
        return b.toString();
    }

    private void drawBranches() {
        ItemStack chest = te.getStackInSlot(com.sc.util.ArmorGasSC.CHEST);
        small(Lang.tr("sc.singStation.branches.cost", SingularStationMath.BRANCH_SM), PX + 4, 31, PW - 8, LABEL);
        if (!SingularLevel.isSingular(chest)) {
            fit(Lang.tr("sc.singStation.branches.nochest"), PX + 6, 40, PW - 12, DIM);
        }
        for (int level : new int[]{3, 5}) {
            int y = level == 3 ? 44 : 70;
            int c = SingularLevel.branchChoice(chest, level);
            String now = c == SingularLevel.BRANCH_NONE ? Lang.tr("sc.singStation.branches.notchosen")
                    : Lang.tr("sc.armorfn." + SingularLevel.branchFeature(level, c).name().toLowerCase(java.util.Locale.ROOT));
            boolean open = SingularLevel.isSingular(chest) && SingularLevel.levelOf(chest) >= level;
            small(Lang.tr("sc.singStation.branches.level", level, open ? now : Lang.tr("sc.singStation.branches.closed")), PX + 6, y, PW - 12,
                    open ? TEXT : DIM);
        }
        small(Lang.tr("sc.singStation.branches.sm", te.tankAmount(Gas.SINGULAR_MATTER)), PX + 6, 98, PW - 12,
                te.tankAmount(Gas.SINGULAR_MATTER) >= SingularStationMath.BRANCH_SM ? OK : WARN);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TextFitSC.beginFrame();
        refreshButtons(plan());
        super.drawScreen(mouseX, mouseY, partialTicks);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        List<String> tip = power.tooltip(mx, my);
        if (tip == null) {
            tip = tipAt(mx, my);
        }
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

    private List<String> tipAt(int mx, int my) {
        Plan p = plan();
        List<String> tip = new ArrayList<String>();
        // the armour rows (not over the slot itself: the item's own tooltip)
        for (int i = 0; i < 4; i++) {
            if (over(25, rowY(i), 72, 24, mx, my)) {
                return pieceTip(i);
            }
        }
        if (over(4, ContainerSingularStationSC.EXTRA_Y - 10, 44, 9, mx, my)) {
            tip.add(Lang.tr("sc.singStation.donor"));
            tip.add(Lang.tr("sc.singStation.donor.hint"));
            return tip;
        }
        if (over(48, ContainerSingularStationSC.EXTRA_Y - 10, 48, 9, mx, my)) {
            tip.add(Lang.tr("sc.singStation.catalyst"));
            tip.add(Lang.tr("sc.singStation.catalyst.hint"));
            return tip;
        }
        if (over(SX, STATUS_Y + 26, SW, 8, mx, my) && lagging() != 0) {
            tip.add(Lang.tr("sc.singStation.lagging"));
            tip.add(Lang.tr("sc.singStation.lagging.hint"));
            return tip;
        }
        // the checklist
        if (!branchView) {
            for (int line = 0; line < 7; line++) {
                if (over(PX + 3, LINE_Y + line * LINE - 1, PW - 6, LINE, mx, my)) {
                    return lineTip(p, line);
                }
            }
            for (int m = 0; m < 3; m++) {
                if (over(PX + 3, MUL_Y + m * MUL_STEP - 1, PW - 6, MUL_STEP, mx, my)) {
                    return mulTip(m);
                }
            }
        }
        if (over(PX + 3, BAR_Y - 2, PW - 6, TIME_Y + 8 - BAR_Y + 2, mx, my)) {
            tip.add(Lang.tr("sc.singStation.progress"));
            SingularProcessSC proc = te.getProcess();
            if (proc != null) {
                tip.add(Lang.tr("sc.singStation.left", time(te.ticksLeft()), Math.round(proc.progress * 100)));
            }
            tip.add("§7" + Lang.tr("sc.singStation.progress.hint"));
            return tip;
        }
        if (modernise.func_146115_a()) {
            if (te.getProcess() != null) {
                tip.add(Lang.tr("sc.singStation.btn.cancel"));
                tip.add(Lang.tr("sc.singStation.cancel.hint"));
                SingularProcessSC proc = te.getProcess();
                for (int r = 0; r < R; r++) {
                    long back = SingularStationMath.refund(proc.drawn[r] - (r == 0 ? proc.catalystEu : 0));
                    if (back > 0) {
                        tip.add("§7" + resName(r) + ": +" + amount(back));
                    }
                }
                if (proc.catalystEu > 0) {
                    tip.add("§7" + Lang.tr("sc.singStation.cancel.core", amount(SingularStationMath.refund(proc.catalystEu))));
                }
                tip.add("§e" + Lang.tr("sc.singStation.cancel.twice"));
            } else {
                tip.add(Lang.tr("sc.singStation.btn.modernise"));
                tip.add(Lang.tr("sc.singStation.modernise.hint"));
                if (p.ready == 0) {
                    tip.add("§c" + Lang.tr("sc.singStation.err.noready"));
                }
                if (p.catalystNeeded && !p.catalystOk) {
                    tip.add("§c" + Lang.tr("sc.singStation.err.nocatalyst"));
                }
            }
            return tip;
        }
        if (branches.func_146115_a()) {
            tip.add(Lang.tr("sc.singStation.btn.branches"));
            tip.add(Lang.tr("sc.singStation.branches.hint", SingularStationMath.BRANCH_SM));
            return tip;
        }
        for (int k = 0; k < 4; k++) {
            if (branch[k].visible && branch[k].func_146115_a()) {
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
        // the tanks and their switches
        for (Gas g : Gas.values()) {
            int gx = RX + g.ordinal() * GAUGE_STEP;
            if (over(gx, GAUGE_Y, GAUGE_W, GAUGE_H, mx, my)) {
                tip.add(GuiArmorStationSC.gasName(g));
                tip.add(Lang.tr("sc.armorStation.tank.amount", te.tankAmount(g), te.tankCapacity(g)) + " "
                        + GuiTankGaugeSC.percentLine(te.getTank(g).getFluid(), te.tankCapacity(g)));
                tip.add(Lang.tr(te.gasEnabled(g) ? "sc.armorStation.tank.on" : "sc.armorStation.tank.off"));
                return tip;
            }
            if (over(gx, GAS_BTN_Y, GAUGE_W, 9, mx, my)) {
                tip.add(GuiArmorStationSC.gasName(g));
                tip.add(Lang.tr("sc.armorStation.gasuse." + g.key()));
                tip.add(Lang.tr(te.gasEnabled(g) ? "sc.armorStation.gas.on" : "sc.armorStation.gas.off"));
                return tip;
            }
        }
        if (over(RX - 1, EN_LABEL_Y, EN_W + 2, EN_TEXT_Y + 7 - EN_LABEL_Y, mx, my)) {
            tip.add(Lang.tr("sc.gui.energy"));
            tip.add(te.getEnergyStored() + " / " + te.getMaxEnergyStored() + " EU");
            tip.add(te.acceptsAnyVoltage() ? Lang.tr("sc.armorStation.modules.anyinput")
                    : Lang.tr("sc.gui.input", te.inputTier().name(), te.inputTier().getVoltage()));
            tip.add("§7" + Lang.tr("sc.singStation.energy.hint"));
            return tip;
        }
        if (over(RX - 1, MOD_LABEL_Y, EN_W + 2, 8, mx, my) || over(RX - 1, MOD_TEXT_Y - 1, EN_W + 2, 8, mx, my)) {
            tip.add(Lang.tr("sc.gui.upgrades"));
            tip.add(Lang.tr("sc.armorStation.modules.hint"));
            tip.add(Lang.tr("sc.armorStation.modules.charge", te.chargePerRound() / TileEntityArmorStationSC.EVERY));
            tip.add(Lang.tr("sc.singStation.modules.tanks", te.tankCapacity(), te.tankCapacity(Gas.SINGULAR_MATTER)));
            return tip;
        }
        // the service buttons
        if (charge.func_146115_a()) {
            tip.add(charge.displayString);
            tip.add(Lang.tr("sc.singStation.charge.hint"));
            return tip;
        }
        if (fill.func_146115_a()) {
            tip.add(fill.displayString);
            tip.add(Lang.tr("sc.armorStation.fill.hint"));
            return tip;
        }
        if (transfer.func_146115_a()) {
            return transferTip();
        }
        if (sync.func_146115_a()) {
            return syncTip();
        }
        if (prev.func_146115_a() || next.func_146115_a() || over(SX + 16, ROW3, SW - 32, 12, mx, my)) {
            tip.add(Lang.tr("sc.singStation.scheme"));
            tip.add(Lang.tr("sc.singStation.scheme.hint"));
            return tip;
        }
        if (over(SX, STATUS_Y + 17, SW, 8, mx, my)) {
            return mulTip(2);
        }
        return null;
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

    private List<String> lineTip(Plan p, int line) {
        List<String> tip = new ArrayList<String>();
        if (line == 0) {
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
        }
        if (line == 1) {
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
        }
        int r = line - 2;
        tip.add(r == 0 ? Lang.tr("sc.singStation.res.eu.full") : GuiArmorStationSC.gasName(SingularStationMath.GAS[r]));
        if (p.process) {
            tip.add(Lang.tr("sc.singStation.tip.drawn", amount(p.have[r]), amount(p.cost[r])));
            if ((te.getShortMask() & 1 << r) != 0) {
                tip.add("§c" + Lang.tr(r == 0 ? "sc.singStation.tip.short.eu" : "sc.singStation.tip.short.gas"));
            }
        } else {
            tip.add(Lang.tr("sc.singStation.tip.need", amount(p.cost[r]), amount(p.have[r])));
            tip.add("§7" + Lang.tr("sc.singStation.tip.gradual"));
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
            tip.add(Lang.tr("sc.singStation.mul.set.hint", 100 - SingularStationMath.SET_PERCENT));
        } else if (m == 1) {
            tip.add(Lang.tr("sc.singStation.mul.res.head"));
            tip.add(Lang.tr("sc.singStation.mul.res.hint", TileEntitySingularStationSC.RES_RADIUS, SingularStationMath.RESONANCE_SPEED_PERCENT,
                    100 - SingularStationMath.RESONANCE_EU_PERCENT));
        } else {
            tip.add(Lang.tr("sc.singStation.mul.stab.head"));
            tip.add(Lang.tr("sc.singStation.mul.stab.hint", TileEntitySingularStationSC.STAB_RADIUS, SingularStationMath.STABILISER_PERCENT,
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
            TextFitSC.drawCentered(mc.fontRenderer, displayString, xPosition + 2, yPosition + (height - 8) / 2, width - 4, c, false, 0, 0);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /** A gas's switch under its gauge: the short name small, lit in the gas's colour while it is filled with. */
    private class GasBtn extends GuiButton {
        private final Gas gas;

        GasBtn(int id, int x, int y, Gas gas) {
            super(id, x, y, GAUGE_W, 9, "");
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
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }
}
