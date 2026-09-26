package com.sc.inventory;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;

/**
 * The machines' energy buffer gauge ("glow" design): a bolted steel frame with a brass nameplate
 * and a status lamp, a recessed holo screen whose edge and corner brackets light up in the charge
 * colour (and spill onto the frame), segments with a highlight row and a soft bloom, scanlines, a
 * glass glint, ticks on the frame and the percentage in pixel digits in a window below. Colour by
 * charge: green from 70 %, orange from 30 %, red below. Drawn from rectangles, any size from
 * about 22 x 50 up; the machines use 22 x 54, the quarry and the field generator a taller one.
 */
@SideOnly(Side.CLIENT)
public final class GuiEnergyGaugeSC {

    public static final int GREEN = 0xFF5AE66E, ORANGE = 0xFFFF8C1E, RED = 0xFFE63C3C;
    private static final int OUTLINE = 0xFF1E2024, SCREEN_TOP = 0xFF0C141E, SCREEN_BOTTOM = 0xFF060A10,
            GRID = 0xFF14202C, OFF_TOP = 0xFF20303E, OFF = 0xFF16222E;
    private static final String[] DIGITS = {"111101101101111", "010110010010111", "111001111100111", "111001111001111",
            "101101111001001", "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111"};
    private static final String PERCENT = "101001010100101";

    private GuiEnergyGaugeSC() {
    }

    public static int colour(float level) {
        return level >= 0.7F ? GREEN : level >= 0.3F ? ORANGE : RED;
    }

    public static void draw(int x, int y, int w, int h, float level) {
        level = Math.max(0F, Math.min(1F, level));
        int c = colour(level);
        // ---- the frame: a vertical steel gradient, bevels, outline, rivets
        gradient(x, y, w, h, 0xFFAAB0BA, 0xFF747A84);
        rect(x + 1, y + 1, w - 2, 1, 0xFFD0D6E0);
        rect(x + 1, y + 1, 1, h - 2, 0xFFC4CAD4);
        rect(x + 1, y + h - 2, w - 2, 1, 0xFF565A62);
        rect(x + w - 2, y + 1, 1, h - 2, 0xFF5C6068);
        rect(x, y, w, 1, OUTLINE);
        rect(x, y + h - 1, w, 1, OUTLINE);
        rect(x, y, 1, h, OUTLINE);
        rect(x + w - 1, y, 1, h, OUTLINE);
        // ---- the brass nameplate and the lamp
        int pw = w - 11;
        rect(x + 3, y + 2, pw, 5, 0xFF5A4014);
        rect(x + 4, y + 3, pw - 2, 3, 0xFFD6A432);
        rect(x + 4, y + 3, pw - 2, 1, 0xFFFFD874);
        rect(x + 4, y + 5, pw - 2, 1, 0xFFA07420);
        for (int k = x + 6; k < x + 3 + pw - 4; k += 3) {
            rect(k, y + 4, 2, 1, 0xFF78541A);
        }
        rect(x + w - 7, y + 1, 6, 6, alpha(c, 0x48));
        rect(x + w - 6, y + 2, 4, 4, 0xFF121214);
        rect(x + w - 5, y + 3, 1, 1, hi(c, 130));
        rect(x + w - 4, y + 3, 1, 1, c);
        rect(x + w - 5, y + 4, 1, 1, c);
        rect(x + w - 4, y + 4, 1, 1, lo(c, 0.62F));
        // ---- the screen: recess, light spilling onto the frame, gradient, grid, lit edge
        int sx = x + 3, sy = y + 9, sw = w - 6, sh = h - 20;
        rect(sx - 2, sy - 2, sw + 4, sh + 4, alpha(c, 0x1C));
        rect(sx - 1, sy - 1, sw + 2, sh + 2, alpha(c, 0x40));
        rect(sx, sy, sw, 1, 0xFF3A3E46);
        rect(sx, sy, 1, sh, 0xFF3A3E46);
        rect(sx, sy + sh - 1, sw, 1, 0xFFDCE2EA);
        rect(sx + sw - 1, sy, 1, sh, 0xFFC8CED8);
        int ix = sx + 1, iy = sy + 1, iw = sw - 2, ih = sh - 2;
        gradient(ix, iy, iw, ih, SCREEN_TOP, SCREEN_BOTTOM);
        for (int gy = iy + 2; gy < iy + ih - 1; gy += 4) {
            for (int gx = ix + 2; gx < ix + iw - 1; gx += 4) {
                rect(gx, gy, 1, 1, GRID);
            }
        }
        int edge = lo(c, 0.45F);
        rect(ix, iy, iw, 1, edge);
        rect(ix, iy + ih - 1, iw, 1, edge);
        rect(ix, iy, 1, ih, edge);
        rect(ix + iw - 1, iy, 1, ih, edge);
        bracket(ix, iy, 1, 1, c);
        bracket(ix + iw - 1, iy, -1, 1, c);
        bracket(ix, iy + ih - 1, 1, -1, c);
        bracket(ix + iw - 1, iy + ih - 1, -1, -1, c);
        // ---- scanlines
        for (int ly = iy + 2; ly < iy + ih - 1; ly += 2) {
            rect(ix + 1, ly, iw - 2, 1, 0x30000000);
        }
        // ---- the segments (2 px each, 1 px apart) with a bloom round the lit ones
        int left = ix + 3, right = ix + iw - 3, top = iy + 3, bottom = iy + ih - 3;
        int n = Math.max(4, (bottom - top + 1) / 3);
        int lit = Math.round(level * n);
        if (level > 0F && lit == 0) {
            lit = 1;
        }
        int litTop = bottom - lit * 3 + 1;
        if (lit > 0) {
            rect(left - 2, litTop - 2, right - left + 4, bottom - litTop + 4, alpha(c, 0x2C));
            rect(left - 1, litTop - 1, right - left + 2, bottom - litTop + 2, alpha(c, 0x48));
        }
        for (int i = 0; i < n; i++) {
            int sb = bottom - i * 3, st = sb - 1;
            if (i < lit) {
                rect(left, st, right - left, 1, hi(c, 75));
                rect(left, sb, right - left, 1, c);
                rect(left, sb, 1, 1, hi(c, 25));
                rect(left, st, 1, 1, hi(c, 150));
            } else {
                rect(left, st, right - left, 1, OFF_TOP);
                rect(left, sb, right - left, 1, OFF);
            }
        }
        // ---- the glass glint: a slanted stripe over the upper part of the screen
        for (int ly = iy + 1; ly < iy + ih * 6 / 10; ly++) {
            int gx = ix + 2 + (ly - iy) * 45 / 100;
            if (gx + 2 < ix + iw - 1) {
                rect(gx, ly, 2, 1, 0x1CFFFFFF);
            }
            int gx2 = gx + 3;
            if (ly < iy + ih * 45 / 100 && gx2 < ix + iw - 1) {
                rect(gx2, ly, 1, 1, 0x10FFFFFF);
            }
        }
        // ---- ticks on the frame's right wall: gold at 0 / 50 / 100 %
        for (int i = 0; i <= 10; i++) {
            int ty = Math.round(bottom - (bottom - top) * i / 10F);
            rect(x + w - 3, ty, 1, 1, i % 5 == 0 ? 0xFFF0C450 : 0xFF3C4048);
        }
        // ---- the percentage window
        String digits = String.valueOf(Math.round(level * 100));
        int tw = digits.length() * 4 + 3;
        int bw = Math.min(w - 4, Math.max(tw + 2, 17)), bx = x + (w - bw) / 2, by = y + h - 9;
        rect(bx - 1, by - 1, bw + 2, 9, 0xFFDCE2EA);
        rect(bx - 1, by - 1, bw + 1, 8, 0xFF3A3E46);
        rect(bx, by, bw, 7, 0xFF060A08);
        rect(bx, by, bw, 1, 0xFF020403);
        int dx = bx + (bw - tw) / 2;
        for (int i = 0; i < digits.length(); i++) {
            glyph(DIGITS[digits.charAt(i) - '0'], dx + i * 4, by + 1, c);
        }
        glyph(PERCENT, dx + digits.length() * 4, by + 1, c);
        if (w >= 26) {
            rivet(x + 2, y + h - 4);
            rivet(x + w - 4, y + h - 4);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private static void bracket(int x, int y, int dx, int dy, int c) {
        int[] cols = {hi(c, 70), c, lo(c, 0.55F)};
        for (int k = 0; k < 3; k++) {
            rect(x + k * dx, y, 1, 1, cols[k]);
            rect(x, y + k * dy, 1, 1, cols[k]);
        }
    }

    private static void rivet(int x, int y) {
        rect(x, y, 1, 1, 0xFFE2E8F0);
        rect(x + 1, y, 1, 1, 0xFF969CA6);
        rect(x, y + 1, 1, 1, 0xFF888E98);
        rect(x + 1, y + 1, 1, 1, 0xFF40444C);
    }

    private static void glyph(String bits, int x, int y, int c) {
        for (int j = 0; j < 5; j++) {
            for (int i = 0; i < 3; i++) {
                if (bits.charAt(j * 3 + i) == '1') {
                    rect(x + i, y + j, 1, 1, c);
                }
            }
        }
    }

    private static int hi(int c, int k) {
        int r = Math.min(255, ((c >> 16) & 255) + k), g = Math.min(255, ((c >> 8) & 255) + k), b = Math.min(255, (c & 255) + k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int lo(int c, float k) {
        int r = (int) (((c >> 16) & 255) * k), g = (int) (((c >> 8) & 255) * k), b = (int) ((c & 255) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int alpha(int c, int a) {
        return a << 24 | (c & 0xFFFFFF);
    }

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            Gui.drawRect(x, y, x + w, y + h, c);
        }
    }

    /** A vertical two-colour gradient (like GuiScreen.drawGradientRect, but static). */
    private static void gradient(int x, int y, int w, int h, int top, int bottom) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(top & 0xFFFFFF, top >>> 24);
        t.addVertex(x + w, y, 0);
        t.addVertex(x, y, 0);
        t.setColorRGBA_I(bottom & 0xFFFFFF, bottom >>> 24);
        t.addVertex(x, y + h, 0);
        t.addVertex(x + w, y + h, 0);
        t.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
