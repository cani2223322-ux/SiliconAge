package com.sc.inventory;

import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.energy.EnergyBankMathSC;
import com.sc.energy.Tier;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityEnergyBankSC;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * ОМ-7 (docs/plan-ideas-2026-10-26.md): the Energy Bank controller's screen - the charge and capacity, the structure (side, cells, ports),
 * the inflow / outflow graph of the last minute. ОМ-7's (in the style of GuiEnergyStorageSC / GuiBigSC: a steel window, one holo
 * screen, TextFitSC for every line). No slots.
 */
public class GuiEnergyBankSC extends GuiContainer {

    private static final int W = 256, H = 176;
    private static final int SCREEN_X = 8, SCREEN_Y = 20, SCREEN_W = W - 16, SCREEN_H = H - 28;
    private static final int TX = SCREEN_X + 6, TW = SCREEN_W - 12;
    private static final int CHARGE_Y = 25, BAR_Y = 35, SIZE_Y = 45, CELLS_Y = 55, PORTS_Y = 65, TIERS_Y = 75, FLOW_Y = 85, GRAPH_HEAD_Y = 97,
            GRAPH_Y = 108, GRAPH_H = 52, STEP = 3, GRAPH_W = EnergyBankMathSC.HISTORY * STEP;
    private static final int IN_COL = 0xFF000000 | GuiHoloSC.OK, OUT_COL = 0xFF000000 | GuiHoloSC.WARN;

    private final TileEntityEnergyBankSC bank;

    public GuiEnergyBankSC(InventoryPlayer playerInv, TileEntityEnergyBankSC bank) {
        super(new ContainerEnergyBankSC(playerInv, bank));
        this.bank = bank;
        xSize = W;
        ySize = H;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        rect(x, y, W, H, 0xFF1E2024);
        rect(x + 1, y + 1, W - 2, H - 2, 0xFFB9C1CC);
        rect(x + 1, y + 1, W - 3, 1, 0xFFECF0F6);
        rect(x + 4, y + 4, W - 8, 11, 0xFF2E3642);
        rect(x + 4, y + 15, W - 8, 1, 0xFFF0F2F6);
        GuiHoloSC.screen(x + SCREEN_X, y + SCREEN_Y, SCREEN_W, SCREEN_H);
        float frac = bank.capacity() <= 0 ? 0F : (float) Math.min(1D, bank.energy() / (double) bank.capacity());
        GuiHoloSC.bar(x + TX, y + BAR_Y, TW, 5, frac, 40, bank.formed() ? GuiHoloSC.CYAN : 0xFF5A6A7A);
        graph(x + TX + (TW - GRAPH_W) / 2, y + GRAPH_Y);
        GuiHoloSC.glint(x + SCREEN_X, y + SCREEN_Y, SCREEN_W, SCREEN_H);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The last minute: the inflow (green) and the outflow (orange) as step lines, the oldest on the left, scaled to the highest. */
    private void graph(int gx, int gy) {
        rect(gx - 1, gy - 1, GRAPH_W + 2, GRAPH_H + 2, 0xFF04080C);
        for (int k = 1; k < 4; k++) {
            rect(gx, gy + GRAPH_H * k / 4, GRAPH_W, 1, 0xFF14222E);
        }
        int max = graphMax();
        line(gx, gy, max, true);
        line(gx, gy, max, false);
    }

    private int graphMax() {
        int max = 1;
        for (int i = 0; i < EnergyBankMathSC.HISTORY; i++) {
            max = Math.max(max, Math.max(bank.histIn(i), bank.histOut(i)));
        }
        return max;
    }

    private void line(int gx, int gy, int max, boolean in) {
        int col = in ? IN_COL : OUT_COL, prev = -1;
        for (int k = 0; k < EnergyBankMathSC.HISTORY; k++) {
            int ago = EnergyBankMathSC.HISTORY - 1 - k;
            int v = in ? bank.histIn(ago) : bank.histOut(ago);
            int h = (int) Math.round((GRAPH_H - 1) * (double) v / max);
            int py = gy + GRAPH_H - 1 - h, px = gx + k * STEP;
            rect(px, py, STEP, 1, col);
            if (prev >= 0 && prev != py) {
                rect(px, Math.min(prev, py), 1, Math.abs(prev - py) + 1, col);
            }
            prev = py;
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fit(Lang.tr("sc.gui.bank.title"), 8, 5, W - 16, GuiGaugeSC.TITLE_COLOR);
        fit(Lang.tr("sc.gui.bank.charge", num(bank.energy()), num(bank.capacity())), TX, CHARGE_Y, TW,
                bank.energy() > bank.capacity() && bank.formed() ? GuiHoloSC.WARN : GuiHoloSC.VALUE);
        if (bank.formed()) {
            int n = bank.size();
            fit(Lang.tr("sc.gui.bank.size", n, n, n), TX, SIZE_Y, TW, GuiHoloSC.OK);
        } else {
            int r = bank.badReason();
            String why = Lang.tr("sc.gui.bank.reason." + EnergyBankMathSC.REASON_KEYS[r > 0 && r < EnergyBankMathSC.REASON_KEYS.length ? r
                    : EnergyBankMathSC.R_SIZE]);
            int[] at = bank.badAt();
            fit(Lang.tr("sc.gui.bank.unformed", why, at[0], at[1], at[2]), TX, SIZE_Y, TW, GuiHoloSC.WARN);
        }
        fit(Lang.tr("sc.gui.bank.cells", bank.cellsSmall(), bank.cellsBig()), TX, CELLS_Y, TW, GuiHoloSC.LABEL);
        fit(Lang.tr("sc.gui.bank.ports", bank.portsIn(), bank.portsOut()), TX, PORTS_Y, TW, GuiHoloSC.LABEL);
        StringBuilder tiers = new StringBuilder();
        for (Tier t : Tier.values()) {
            int v = bank.portTier(t.ordinal()), in = v & 0xFFFF, out = v >>> 16;
            if (in + out > 0) {
                tiers.append(tiers.length() == 0 ? "" : "   ").append(t.name()).append(" +").append(in).append(" -").append(out);
            }
        }
        if (tiers.length() > 0) {
            fit(tiers.toString(), TX, TIERS_Y, TW, GuiHoloSC.IDLE);
        }
        fit(Lang.tr("sc.gui.bank.flow", num(bank.nowIn()), num(bank.nowOut())), TX, FLOW_Y, TW, GuiHoloSC.VALUE);
        fit(Lang.tr("sc.gui.bank.graph", EnergyBankMathSC.HISTORY), TX, GRAPH_HEAD_Y, TW / 2, GuiHoloSC.LABEL);
        String top = num(graphMax()) + " EU/t";
        fit(top, TX + TW - fontRendererObj.getStringWidth(top), GRAPH_HEAD_Y, TW / 2, GuiHoloSC.IDLE);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tip = TextFitSC.hoverAt(mouseX, mouseY);
        if (tip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
    }

    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            drawRect(x, y, x + w, y + h, c);
        }
    }

    /** "4 000 000 000" in Russian, "4,000,000,000" in English (the handbook's way). */
    private static String num(long v) {
        String s = com.sc.bridge.BridgeMathSC.group(v);
        return "ru".equals(Lang.trOr("sc.book.lang", "en")) ? s : s.replace(' ', ',');
    }
}
