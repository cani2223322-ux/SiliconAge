package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.manual.Lang;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.opengl.GL11;

/**
 * The power switch and the redstone mode button above a large screen's energy gauge (machines,
 * generators, storages), the gauge dimmed with OFF on it, the warning plaque beside a line too
 * strong for the input - and, there, switching on only with a double click.
 */
public final class GuiPowerSC {

    public static final int X = GuiBigSC.GAUGE_X, Y = GuiBigSC.GAUGE_Y, POWER_W = 15, H = 11, RS_X = X + 16, RS_W = GuiBigSC.GAUGE_W - 16;
    /** The energy gauge under the buttons. */
    public static final int GAUGE_Y = GuiBigSC.GAUGE_Y + 12, GAUGE_H = GuiBigSC.GAUGE_H - 12;
    private static final long DOUBLE_CLICK_MS = 1500;

    private final TileEntityEnergyBase te;
    private final int powerId, redstoneId;
    private long armedAt;

    public GuiPowerSC(TileEntityEnergyBase te, int powerId, int redstoneId) {
        this.te = te;
        this.powerId = powerId;
        this.redstoneId = redstoneId;
    }

    @SuppressWarnings("unchecked")
    public void addButtons(List buttonList, int guiLeft, int guiTop) {
        buttonList.add(new PowerButton(guiLeft + X, guiTop + Y));
        buttonList.add(new RedstoneButton(guiLeft + RS_X, guiTop + Y));
    }

    /** False for the first click on a dangerous switch-on (it only arms it). */
    public boolean allowClick(GuiButton button) {
        if (button.id == powerId && !te.isPowerOn() && te.lineTooStrong()) {
            long now = System.currentTimeMillis();
            if (now - armedAt > DOUBLE_CLICK_MS) {
                armedAt = now;
                return false;
            }
            armedAt = 0;
        }
        return true;
    }

    private boolean armed() {
        return System.currentTimeMillis() - armedAt <= DOUBLE_CLICK_MS;
    }

    /** The buttons' tooltips (screen-local coordinates), or null. */
    public List<String> tooltip(int mx, int my) {
        List<String> lines = new ArrayList<String>();
        if (GuiGaugeSC.isOver(X, Y, POWER_W, H, mx, my)) {
            boolean on = te.isPowerOn();
            lines.add(Lang.tr(on ? "sc.gui.power.on" : "sc.gui.power.off"));
            lines.add(Lang.tr(on ? "sc.gui.power.hint.on" : "sc.gui.power.hint.off"));
            if (!on && te.lineTooStrong()) {
                lines.add(Lang.tr("sc.gui.power.danger", te.lineTier().name(), te.inputTier().name()));
                lines.add(Lang.tr(armed() ? "sc.gui.power.armed" : "sc.gui.power.double"));
            }
            return lines;
        }
        if (GuiGaugeSC.isOver(RS_X, Y, RS_W, H, mx, my)) {
            lines.add(Lang.tr("sc.gui.redstone." + te.getRedstoneMode()));
            lines.add(Lang.tr("sc.gui.redstone.hint"));
            return lines;
        }
        return null;
    }

    /** Foreground: switched off, the gauge dimmed with OFF on it. */
    public void drawGaugeOff(FontRenderer fr) {
        if (te.isPowerOn()) {
            return;
        }
        int gw = GuiBigSC.GAUGE_W;
        Gui.drawRect(X + 2, GAUGE_Y + 10, X + gw - 2, GAUGE_Y + GAUGE_H - 12, 0xA0000000);
        String off = Lang.tr("sc.gui.power.label");
        int ow = fr.getStringWidth(off) * 5 / 8;
        GL11.glPushMatrix();
        GL11.glTranslatef(X + (gw - ow) / 2F, GAUGE_Y + GAUGE_H / 2F - 3, 0F);
        GL11.glScalef(0.625F, 0.625F, 1F);
        fr.drawString(off, 0, 0, 0xB0B8C4);
        GL11.glPopMatrix();
    }

    /** Foreground: switched off beside a line too strong for the input, the warning plaque over (px, py, pw x 30). */
    public void drawWarning(FontRenderer fr, int px, int py, int pw, int guiLeft, int guiTop) {
        if (te.isPowerOn() || !te.lineTooStrong()) {
            return;
        }
        Tier t = te.lineTier(), in = te.inputTier();
        int ph = 30;
        GL11.glPushMatrix();
        GL11.glTranslatef(0F, 0F, 200F);                                         // over the items and the texts
        Gui.drawRect(px, py, px + pw, py + ph, 0xFF3A1A0A);
        Gui.drawRect(px, py, px + pw, py + 1, 0xFFFF8C1E);
        Gui.drawRect(px, py + ph - 1, px + pw, py + ph, 0xFFFF8C1E);
        Gui.drawRect(px + 3, py + 4, px + 12, py + 13, 0xFFFF8C1E);
        fr.drawString("!", px + 6, py + 5, 0x281400);
        TextFitSC.draw(fr, Lang.tr("sc.gui.power.warn.line", t.name(), t.getVoltage()), px + 15, py + 3, pw - 18, 0xFFC85A, guiLeft, guiTop);
        TextFitSC.draw(fr, Lang.tr("sc.gui.power.warn.input", in.name(), in.getVoltage()), px + 15, py + 12, pw - 18, 0xFFC85A, guiLeft, guiTop);
        String hint = Lang.tr("sc.gui.power.warn.hint");
        float k = Math.min(0.625F, (pw - 6) / (float) Math.max(1, fr.getStringWidth(hint)));
        GL11.glTranslatef(px + 3, py + 22, 0F);
        GL11.glScalef(k, k, 1F);
        fr.drawString(hint, 0, 0, 0xE6C8AA);
        GL11.glPopMatrix();
    }

    /** Green on, grey off, red off beside a line too strong for the input. */
    private class PowerButton extends GuiButton {
        PowerButton(int x, int y) {
            super(powerId, x, y, POWER_W, H, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            boolean on = te.isPowerOn(), danger = !on && te.lineTooStrong();
            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            int edge = on ? 0xFF2A8A3A : danger ? 0xFFB03030 : 0xFF4A505A;
            int fill = on ? 0xFF123A1A : danger ? (armed() ? 0xFF6A1818 : 0xFF3A1010) : 0xFF1A1E24;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, 0xFF0A0C10);
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, over ? GuiSceneSC.mix(edge, 0xFFFFFFFF, 0.25F) : edge);
            drawRect(xPosition + 2, yPosition + 2, xPosition + width - 2, yPosition + height - 2, fill);
            int c = on ? 0xFF5AE66E : danger ? 0xFFFF6A6A : 0xFF8A909A;
            int sx = xPosition + (width - 7) / 2, sy = yPosition + 3;             // the power symbol: a broken ring and a bar
            drawRect(sx + 1, sy + 1, sx + 2, sy + 2, c);
            drawRect(sx + 5, sy + 1, sx + 6, sy + 2, c);
            drawRect(sx, sy + 2, sx + 1, sy + 5, c);
            drawRect(sx + 6, sy + 2, sx + 7, sy + 5, c);
            drawRect(sx + 1, sy + 5, sx + 2, sy + 6, c);
            drawRect(sx + 5, sy + 5, sx + 6, sy + 6, c);
            drawRect(sx + 2, sy + 6, sx + 5, sy + 7, c);
            drawRect(sx + 3, sy - 1, sx + 4, sy + 3, c);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /** A dash (always), a lit torch (with a signal), an unlit one (without). */
    private class RedstoneButton extends GuiButton {
        RedstoneButton(int x, int y) {
            super(redstoneId, x, y, RS_W, H, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, 0xFF0A0C10);
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, over ? 0xFF6A707A : 0xFF4A505A);
            drawRect(xPosition + 2, yPosition + 2, xPosition + width - 2, yPosition + height - 2, 0xFF1A1E24);
            int cx = xPosition + width / 2, mode = te.getRedstoneMode();
            if (mode == 0) {
                drawRect(cx - 2, yPosition + 5, cx + 2, yPosition + 7, 0xFFB0B8C4);
            } else {
                drawRect(cx - 1, yPosition + 5, cx + 1, yPosition + 9, 0xFF6A4020);
                drawRect(cx - 1, yPosition + 3, cx + 1, yPosition + 5, mode == 1 ? 0xFFFF3A2A : 0xFF4A1A14);
            }
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }
}
