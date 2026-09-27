package com.sc.inventory;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.Gui;

/**
 * The holo-screen look of the machines' screens (the energy gauge's style, grown to a panel): a
 * recessed dark screen with a faint grid, scanlines, cyan corner brackets and a glass glint,
 * cyan-framed slot pockets and segmented progress bars with a soft bloom. Drawn from rectangles.
 */
@SideOnly(Side.CLIENT)
public final class GuiHoloSC {

    public static final int CYAN = 0xFF6EE6FF, CYAN_MID = 0xFF3CAADC, CYAN_DIM = 0xFF246E96;
    /** Text on the screen: labels, values, and the status colours. */
    public static final int LABEL = 0x6AA8C8, VALUE = 0xE6F0FA, OK = 0x5AE66E, WARN = 0xFFB040, BAD = 0xFF5A50, IDLE = 0x8898A8;
    private static final int OFF_TOP = 0xFF20303E, OFF = 0xFF16222E;

    private GuiHoloSC() {
    }

    public static void screen(int x, int y, int w, int h) {
        rect(x, y, w, 1, 0xFF3A3E46);
        rect(x, y, 1, h, 0xFF3A3E46);
        rect(x, y + h - 1, w, 1, 0xFFDCE2EA);
        rect(x + w - 1, y, 1, h, 0xFFC8CED8);
        int ix = x + 1, iy = y + 1, iw = w - 2, ih = h - 2;
        GuiEnergyGaugeSC.gradient(ix, iy, iw, ih, 0xFF0C141E, 0xFF060A10);
        for (int gy = iy + 2; gy < iy + ih - 1; gy += 4) {
            for (int gx = ix + 2; gx < ix + iw - 1; gx += 4) {
                rect(gx, gy, 1, 1, 0xFF14202C);
            }
        }
        for (int ly = iy + 1; ly < iy + ih; ly += 2) {
            rect(ix, ly, iw, 1, 0x22000000);
        }
        rect(ix, iy, iw, 1, 0xFF04060A);
        bracket(ix, iy, 1, 1);
        bracket(ix + iw - 1, iy, -1, 1);
        bracket(ix, iy + ih - 1, 1, -1);
        bracket(ix + iw - 1, iy + ih - 1, -1, -1);
    }

    /** The glass glint, drawn over everything else on the screen. */
    public static void glint(int x, int y, int w, int h) {
        int ix = x + 1, iy = y + 1, iw = w - 2, ih = h - 2;
        for (int ly = iy + 1; ly < iy + ih * 7 / 10; ly++) {
            int gx = ix + 3 + (ly - iy) * 45 / 100;
            if (gx + 2 < ix + iw - 1) {
                rect(gx, ly, 2, 1, 0x14FFFFFF);
            }
        }
    }

    private static void bracket(int x, int y, int dx, int dy) {
        int[] cols = {CYAN, CYAN_MID, CYAN_DIM, CYAN_DIM};
        for (int k = 0; k < cols.length; k++) {
            rect(x + k * dx, y, 1, 1, cols[k]);
            rect(x, y + k * dy, 1, 1, cols[k]);
        }
    }

    /** A slot pocket round the 16x16 item area at (x, y); `lit`: a brighter frame (e.g. an output ready). */
    public static void slot(int x, int y, boolean lit) {
        rect(x - 2, y - 2, 20, 20, 0x30000000 | (CYAN_MID & 0xFFFFFF));
        rect(x - 1, y - 1, 18, 18, lit ? CYAN : CYAN_MID);
        rect(x, y, 16, 16, 0xFF0E1A26);
        rect(x, y, 16, 1, 0xFF08101A);
        rect(x, y, 1, 16, 0xFF08101A);
        rect(x - 1, y - 1, 1, 1, CYAN);
        rect(x + 16, y + 16, 1, 1, CYAN_DIM);
    }

    /** A horizontal bar of n segments filled to `frac`, with a bloom round the lit part. */
    public static void bar(int x, int y, int w, int h, float frac, int n, int col) {
        rect(x - 1, y - 1, w + 2, h + 2, 0xFF04080C);
        float seg = (w + 1) / (float) n;
        int lit = Math.round(Math.max(0F, Math.min(1F, frac)) * n);
        if (frac > 0F && lit == 0) {
            lit = 1;
        }
        if (lit > 0) {
            int lw = Math.round(lit * seg) - 1;
            rect(x - 2, y - 2, lw + 4, h + 4, (col & 0xFFFFFF) | 0x30000000);
        }
        for (int i = 0; i < n; i++) {
            int a = x + Math.round(i * seg), b = x + Math.round((i + 1) * seg) - 1;
            boolean on = i < lit;
            rect(a, y, b - a, h, on ? col : OFF);
            rect(a, y, b - a, 1, on ? hi(col, 80) : OFF_TOP);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private static int hi(int c, int k) {
        int r = Math.min(255, ((c >> 16) & 255) + k), g = Math.min(255, ((c >> 8) & 255) + k), b = Math.min(255, (c & 255) + k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            Gui.drawRect(x, y, x + w, y + h, c);
        }
    }
}
