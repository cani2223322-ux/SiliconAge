package com.sc.inventory;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.Gui;

/**
 * Animated process scenes for the large machine screen's picture window, drawn from rectangles.
 * The Crusher: a hopper grid on top, two toothed jaws that close and open while it works, an ore
 * lump dropping between them, chips falling and a tray below filling with dust as the recipe
 * goes on. Still (and without chips) while the machine waits.
 * The Ore Washer: a steel tub whose water level is the machine's water tank, nozzles spraying,
 * ore bobbing on the water, bubbles rising, silt on the bottom, level marks on the side.
 */
@SideOnly(Side.CLIENT)
public final class GuiSceneSC {

    private GuiSceneSC() {
    }

    /** The picture window's frame and dark fill. */
    public static void frame(int x, int y, int w, int h) {
        rect(x, y, w, h, 0xFF1E3444);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF0A1218);
    }

    /** @param t world time in ticks (with the partial tick), @param progress 0..1 of the current recipe */
    public static void crusher(int x, int y, int w, int h, float t, boolean running, float progress) {
        frame(x, y, w, h);
        for (int k = 0; k + 3 < w - 4; k += 5) {
            rect(x + 2 + k, y + 3, 3, 1, 0xFF2A3A48);                      // the hopper grid
        }
        int open = running ? (int) Math.round(1.5 + 1.5 * Math.sin(t * 0.45)) : 2;
        int cx = x + w / 2;
        for (int side = 0; side < 2; side++) {                              // the jaws: a funnel, narrowing downwards
            for (int j = 0; j < 20; j++) {
                int jw = 10 + j / 2;
                int x0 = side == 0 ? cx - 27 - open : cx + 27 + open - jw;
                rect(x0, y + 7 + j, jw, 1, j % 4 != 0 ? 0xFF6A707A : 0xFF8A909A);
                if (j % 4 == 1) {
                    int tx = side == 0 ? x0 + jw : x0 - 2;                   // teeth on the inner edge
                    rect(tx, y + 7 + j, 2, 2, 0xFFB8BEC8);
                }
            }
        }
        if (running) {
            int fall = (int) (t * 0.8F) % 18;                               // an ore lump dropping into the jaws
            int oy = y + 5 + fall;
            rect(cx - 3, oy, 6, 5, 0xFF7A7E88);
            rect(cx - 2, oy + 1, 2, 2, 0xFFB07050);
            rect(cx + 1, oy + 2, 1, 1, 0xFFB07050);
            for (int i = 0; i < 6; i++) {                                   // chips under the jaws
                int cy = y + 28 + (int) ((t * 0.9F + i * 3.7F) % 8);
                int chx = cx - 5 + (i * 7 % 11);
                rect(chx, cy, 1, 1, 0xFFD8C8A8);
            }
        }
        int trayW = w - 60, tx0 = x + 30;                                   // the tray, filling with dust
        rect(tx0 - 2, y + h - 3, trayW + 4, 1, 0xFF3A2E24);
        int dust = Math.max(1, Math.round(3 * Math.max(0F, Math.min(1F, progress))));
        rect(tx0, y + h - 3 - dust, trayW, dust, 0xFF9A8A7A);
        rect(tx0, y + h - 3 - dust, trayW, 1, 0xFFB8A898);
    }

    /** @param level the water tank's fill, 0..1 - the tub's water line */
    public static void washer(int x, int y, int w, int h, float t, boolean running, float level) {
        frame(x, y, w, h);
        int tx0 = x + 6, tx1 = x + w - 6, ty0 = y + 12, ty1 = y + h - 4;
        rect(tx0 - 2, ty0, 2, ty1 - ty0 + 2, 0xFF8A909A);                   // the tub
        rect(tx1, ty0, 2, ty1 - ty0 + 2, 0xFF6A707A);
        rect(tx0 - 2, ty1, tx1 - tx0 + 4, 2, 0xFF5A606A);
        level = Math.max(0F, Math.min(1F, level));
        int wl = ty1 - Math.round((ty1 - ty0) * level);
        if (wl < ty1) {                                                     // water, gently rippling rows
            for (int yy = wl; yy < ty1; yy++) {
                int shade = ((yy - wl) + (int) (t * 0.3F)) % 5 == 0 ? 0xFF3C78E6 : (yy - wl) > (ty1 - wl) / 2 ? 0xFF2452B8 : 0xFF2E64D0;
                rect(tx0, yy, tx1 - tx0, 1, shade);
            }
            rect(tx0, wl, tx1 - tx0, 1, 0xFFA8D8FF);
        }
        rect(tx0, ty1 - 2, tx1 - tx0, 2, 0xFF4A3A2A);                       // silt
        for (int k = 0; k < 3; k++) {                                       // nozzles and drops
            int nx = tx0 + 6 + k * (tx1 - tx0 - 12) / 2;
            rect(nx - 2, y + 3, 5, 3, 0xFF8A909A);
            rect(nx - 1, y + 6, 3, 1, 0xFF6A707A);
            if (running) {
                for (int d = 0; d < 3; d++) {
                    int dy = y + 8 + (int) ((t * 1.3F + d * 4 + k * 2) % 10);
                    if (dy < wl) {
                        rect(nx, dy, 1, 2, 0xFF7AB8FF);
                    }
                }
            }
        }
        if (running && wl < ty1 - 3) {
            float[][] lumps = {{0.3F, 0F}, {0.55F, 1.7F}, {0.75F, 3.1F}};
            for (float[] lp : lumps) {                                      // ore bobbing on the water
                int lx = tx0 + (int) ((tx1 - tx0) * lp[0]) - 3;
                int ly = Math.max(ty0 + 2, wl - 2 + (int) Math.round(Math.sin(t * 0.3F + lp[1])));
                rect(lx, ly, 7, 5, 0xFF7A6E62);
                rect(lx + 1, ly + 1, 2, 2, 0xFFB07050);
            }
            for (int b = 0; b < 6; b++) {                                   // bubbles
                int bx = tx0 + 4 + (b * 17) % Math.max(1, tx1 - tx0 - 8);
                int by = ty1 - 3 - (int) ((t * 0.9F + b * 5) % Math.max(1, ty1 - wl - 3));
                if (by > wl) {
                    rect(bx, by, 1, 1, 0xFFE0F4FF);
                }
            }
        }
        for (int k = 0; k <= 4; k++) {                                      // level marks
            int my = ty0 + Math.round((ty1 - ty0) * k / 4F);
            rect(tx1 + 2, my, 2, 1, k % 2 == 0 ? 0xFFF0C450 : 0xFF3C4048);
        }
    }

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            Gui.drawRect(x, y, x + w, y + h, c);
        }
    }
}
