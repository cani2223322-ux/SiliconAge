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

    /** The Blast Furnace: brick walls, a glowing mouth (brighter the hotter), flames while it works, a crucible, shimmer. */
    public static void furnace(int x, int y, int w, int h, float t, boolean running, float heat) {
        frame(x, y, w, h);
        int bx0 = x + 5, bx1 = x + w - 5, by0 = y + 3, by1 = y + h - 3;
        for (int yy = by0, row = 0; yy + 3 <= by1; yy += 4, row++) {         // bricks
            int off = row % 2 == 0 ? 0 : 4;
            for (int xx = bx0 - off; xx < bx1; xx += 8) {
                int a = Math.max(bx0, xx), e = Math.min(bx1, xx + 7);
                rect(a, yy, e - a, 3, 0xFF6A3A2A);
                rect(a, yy, e - a, 1, 0xFF8A4A34);
            }
        }
        int mx0 = bx0 + 9, mx1 = bx1 - 9, my0 = by0 + 9, my1 = by1 - 2;       // the mouth
        rect(mx0 - 1, my0 - 1, mx1 - mx0 + 2, my1 - my0 + 1, 0xFF2A1810);
        int[] glow = {0xFF3A1206, 0xFF7A2A08, 0xFFC04A10, 0xFFFF8C1E, 0xFFFFD050};
        float bright = 0.35F + 0.65F * Math.max(0F, Math.min(1F, heat));
        for (int yy = my0; yy < my1; yy++) {
            float k = (float) (yy - my0) / Math.max(1, my1 - my0);
            rect(mx0, yy, mx1 - mx0, 1, glow[Math.min(4, (int) (k * 5 * bright))]);
        }
        if (running) {
            for (int i = 0; i < 6; i++) {                                     // flames
                int fx = mx0 + 2 + i * (mx1 - mx0 - 4) / 5;
                int fh = 3 + (int) Math.round(2.5 + 2.5 * Math.sin(t * 0.7F + i * 1.3F));
                rect(fx, my1 - fh, 2, fh, 0xFFFFB040);
                rect(fx, my1 - fh, 2, 1, 0xFFFFF0A0);
            }
            for (int i = 0; i < 4; i++) {                                     // shimmer over the mouth
                rect(mx0 + 3 + i * 6, by0 + 1 + (int) ((t * 0.5F + i) % 4), 1, 1, 0xFFFFD890);
            }
        }
        int cx = (mx0 + mx1) / 2;                                              // the crucible
        rect(cx - 7, my0 + 3, 14, 6, 0xFF5A606A);
        rect(cx - 6, my0 + 3, 12, 2, heat > 0.05F ? 0xFFFFC060 : 0xFF6A5A4A);
        rect(cx - 6, my0 + 5, 12, 1, heat > 0.05F ? 0xFFFF8C1E : 0xFF4A3A2A);
    }

    /**
     * The Czochralski puller: a crucible of melt over heater coils, the seed rod pulling a boule up out
     * of it (longer as the run goes on, striped as it turns), argon coming down.
     */
    public static void puller(int x, int y, int w, int h, float t, boolean running, float progress, float heat) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        boolean hot = heat > 0.05F || running;
        int cx = x + w / 2;
        rect(x + 5, y + 3, w - 10, 2, 0xFF6A707A);                            // the pull head
        rect(x + 5, y + 3, w - 10, 1, 0xFF9AA0AA);
        int cy0 = y + h - 14, cy1 = y + h - 5;                                 // the crucible
        rect(cx - 16, cy0, 32, cy1 - cy0, 0xFF8A909A);
        rect(cx - 15, cy0, 30, cy1 - cy0 - 1, 0xFF2A1810);
        int[] melt = hot ? new int[]{0xFFFFD050, 0xFFFF9A30, 0xFFC04A10} : new int[]{0xFF8A8A90, 0xFF6A6A72, 0xFF4A4A52};
        for (int i = 0; i < melt.length; i++) {
            rect(cx - 15, cy0 + 1 + i * 2, 30, 2, melt[i]);
        }
        for (int i = 0; i < 5; i++) {                                          // heater coils
            rect(cx - 18 + i * 8, cy1 + 1, 5, 2, hot ? 0xFFFF5A3C : 0xFF5A2A20);
        }
        int rodTop = y + 5, room = cy0 - (y + 9);
        int length = Math.round(room * progress), top = cy0 - length;
        rect(cx - 1, rodTop, 2, top - rodTop, 0xFFB0B8C4);                    // the seed rod
        if (length > 0) {                                                      // the boule
            int turn = running ? (int) (t * 0.8F) : 0;
            for (int yy = top; yy < cy0; yy++) {
                float k = (float) (yy - top) / Math.max(1, cy0 - top);
                int half = k < 0.9F ? 2 + Math.round(6 * Math.min(1F, k * 3)) : 2 + Math.round(6 * (1 - (k - 0.9F) * 5));
                rect(cx - half, yy, 2 * half, 1, (yy + turn) % 4 == 0 ? 0xFF8A94A8 : 0xFF6A7488);
                rect(cx - half, yy, 1, 1, 0xFFB8C4D8);
            }
            if (hot) {
                rect(cx - 6, cy0 - 1, 12, 1, 0xFFFFE08A);                      // the glowing meniscus
            }
        }
        if (running) {
            for (int i = 0; i < 6; i++) {                                      // argon flowing down
                int ax = x + 5 + (i * 7) % Math.max(1, w - 10);
                int ay = y + 6 + (int) ((t * 0.6F + i * 5) % Math.max(1, cy0 - y - 8));
                rect(ax, ay, 1, 1, 0xFF8AB0E8);
            }
        }
    }

    /**
     * The wire saw: two grooved pulleys, a web of diamond wires running between them and going down
     * through the ingot on its holder (cuts behind it), water jets on the cut, sparks where it bites.
     */
    public static void wireSaw(int x, int y, int w, int h, float t, boolean running, float progress) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        int ix0 = x + w / 2 - 16, ix1 = x + w / 2 + 16, top = y + 14, bot = y + h - 8;
        int wy = top - 2 + Math.round((bot - top + 2) * progress);
        int px0 = x + 6, px1 = x + w - 14, spin = running ? (int) t % 2 : 0;
        for (int px : new int[]{px0, px1}) {                                   // the pulleys
            rect(px, wy - 5, 8, 10, 0xFF6A707A);
            rect(px + 1, wy - 4, 6, 8, 0xFF3A4048);
            for (int k = 0; k < 4; k++) {
                rect(px + 1, wy - 4 + k * 2 + spin, 6, 1, 0xFF9AA0AA);
            }
        }
        rect(ix0, top, ix1 - ix0, bot - top, 0xFF6A7488);                     // the ingot
        rect(ix0, top, ix1 - ix0, 1, 0xFFB8C4D8);
        for (int i = 1; i < 8; i++) {                                          // the cuts so far
            rect(ix0 + i * (ix1 - ix0) / 8, top, 1, wy - top, 0xFF0A1218);
        }
        rect(ix0 - 3, bot, ix1 - ix0 + 6, 3, 0xFF8A909A);                     // the holder
        for (int k = 0; k < 3; k++) {                                          // the wire web
            rect(px0 + 8, wy - 2 + k * 2, px1 - px0 - 8, 1, (k + spin) % 2 == 1 ? 0xFFD8E8F8 : 0xFFA8C8E8);
        }
        for (int i = 0; i < 2; i++) {                                          // the water nozzles
            rect(ix0 - 6 + i * (ix1 - ix0 + 10), y + 4, 3, 3, 0xFF5A8AC8);
        }
        if (running) {
            for (int i = 0; i < 8; i++) {                                      // sparks where it cuts
                rect(ix0 + 2 + (i * 5 + (int) (t * 2)) % (ix1 - ix0 - 4), wy - 1, 1, 1, 0xFFFFFFFF);
            }
            for (int i = 0; i < 2; i++) {                                      // water jets
                int jx = ix0 - 5 + i * (ix1 - ix0 + 10);
                for (int k = 0; k < 5; k++) {
                    rect(jx, y + 7 + (int) ((t * 1.5F + k * 4) % Math.max(1, wy - y - 7)), 1, 1, 0xFF6AB4F0);
                }
            }
        }
    }

    /** A thermometer beside the furnace: bulb, fill in the zone colour, marks at 0 / 50 / resume / 100. */
    public static void thermometer(int x, int y, int h, float heat, float resume) {
        heat = Math.max(0F, Math.min(1F, heat));
        rect(x + 2, y, 7, h - 8, 0xFF8A909A);
        rect(x + 3, y + 1, 5, h - 9, 0xFF101418);
        rect(x, y + h - 10, 11, 10, 0xFF8A909A);
        rect(x + 1, y + h - 9, 9, 8, heat > 0F ? 0xFFE63C3C : 0xFF3A2020);
        rect(x + 2, y + h - 8, 2, 2, 0xFFFF9090);
        int col = heat < resume ? 0xFF5AE66E : heat < 0.9F ? 0xFFFF8C1E : 0xFFE63C3C;
        int top = y + 1 + Math.round((h - 10) * (1 - heat));
        rect(x + 4, top, 3, y + h - 9 - top, col);
        float[] marks = {0F, 0.5F, resume, 1F};
        int[] cols = {0xFF3C4048, 0xFF3C4048, 0xFFF0C450, 0xFFE63C3C};
        for (int i = 0; i < marks.length; i++) {
            rect(x + 9, y + 1 + Math.round((h - 10) * (1 - marks[i])), 3, 1, cols[i]);
        }
    }

    /** The heat as 20 segments in zone colours (green to resume, orange, red), marks at resume and 100. */
    public static void heatBar(int x, int y, int w, int h, float heat, float resume) {
        rect(x - 1, y - 1, w + 2, h + 2, 0xFF04080C);
        int n = 20;
        for (int i = 0; i < n; i++) {
            int a = x + i * w / n, b = x + (i + 1) * w / n - 1;
            float v = (i + 0.5F) / n;
            int col = v < resume ? 0xFF5AE66E : v < 0.9F ? 0xFFFF8C1E : 0xFFE63C3C;
            boolean on = v <= heat;
            rect(a, y, b - a, h, on ? col : 0xFF16222E);
            rect(a, y, b - a, 1, on ? (col | 0xFF505050) : 0xFF20303E);
        }
        rect(x + Math.round(w * resume), y - 3, 1, h + 5, 0xFFF0C450);
        rect(x + w - 1, y - 3, 1, h + 5, 0xFFE63C3C);
    }

    /**
     * The Chemical Reactor's flask: a round-bottom flask fed from both sides at the top (in the
     * two input fluids' colours), draining at the bottom, the mix in the product's colour, bubbling
     * while it works; the mix rises with the recipe's progress.
     */
    public static void flask(int x, int y, int w, int h, float t, boolean running, int colA, int colB, int colOut, float progress) {
        frame(x, y, w, h);
        int cx = x + w / 2, r = Math.min(w / 2 - 3, 11), cy = y + h - r - 5;
        int mix = colOut != 0 ? colOut : 0xFF7ABCA8;
        float level = running ? 0.35F + 0.4F * Math.max(0F, Math.min(1F, progress)) : 0.3F;
        int wl = cy + r - Math.round(2 * r * level);
        for (int yy = cy - r; yy <= cy + r; yy++) {
            for (int xx = cx - r; xx <= cx + r; xx++) {
                double d = Math.hypot(xx + 0.5 - cx, yy + 0.5 - cy);
                if (d > r) {
                    continue;
                }
                if (d >= r - 1) {
                    rect(xx, yy, 1, 1, 0xFFB0C8D8);                            // the glass
                } else if (yy >= wl) {
                    rect(xx, yy, 1, 1, (xx + yy) % 3 != 0 ? mix : lighter(mix));
                }
            }
        }
        rect(cx - 3, y + 7, 6, cy - r - y - 6, 0xFFB0C8D8);                 // the neck
        rect(cx - 2, y + 7, 4, cy - r - y - 6, 0xFF0A1218);
        rect(x + 3, y + 5, cx - 3 - x - 3, 2, colA != 0 ? colA : 0xFF3A4450);  // the feeds
        rect(cx + 3, y + 5, x + w - 3 - cx - 3, 2, colB != 0 ? colB : 0xFF3A4450);
        rect(cx - 3, y + 5, 6, 2, 0xFF8A909A);
        rect(cx - 1, cy + r, 2, y + h - 2 - cy - r, mix);                    // the drain
        if (running) {
            for (int i = 0; i < 5; i++) {                                     // bubbles
                int bx = cx - r + 3 + (i * 5) % Math.max(1, 2 * r - 4);
                int by = cy + r - 2 - (int) ((t * 0.8F + i * 3) % Math.max(1, cy + r - wl - 1));
                if (by > wl) {
                    rect(bx, by, 1, 1, 0xFFE8FFF8);
                }
            }
            int drop = y + 8 + (int) ((t * 1.2F) % Math.max(1, cy - r - y - 7));    // a drop running down the neck
            rect(cx - 1, drop, 2, 2, mix);
        }
    }

    /**
     * The CVD Chamber: a Siemens bell jar - a glass dome over two glowing silicon rods and their
     * bridge on a base plate; the rods thicken as silicon deposits (with the recipe's progress),
     * gas swirls inside while it works, the two gas inlets in their fluids' colours.
     */
    public static void bell(int x, int y, int w, int h, float t, boolean running, float progress, int colA, int colB) {
        frame(x, y, w, h);
        int cx = x + w / 2, base = y + h - 8;
        rect(x + 4, base, w - 8, 3, 0xFF6A707A);                               // the base plate
        rect(x + 4, base, w - 8, 1, 0xFF9AA0AA);
        int rw = (w - 16) / 2, rh = h - 20;
        for (int yy = base - rh; yy < base; yy++) {                           // the dome
            double k = (double) (yy - (base - rh)) / rh;
            int half = (int) (rw * Math.sqrt(Math.max(0.0, 1 - (1 - k) * (1 - k))));
            rect(cx - half - 1, yy, 1, 1, 0xFF9AB8D0);
            rect(cx + half, yy, 1, 1, 0xFF9AB8D0);
            if (half > 0) {
                rect(cx - half, yy, 2 * half, 1, 0xFF0E1C28);
            }
        }
        rect(cx - 2, base - rh - 1, 4, 1, 0xFF9AB8D0);
        int thick = 1 + Math.round(3 * Math.max(0F, Math.min(1F, progress)));
        int glow = running ? 0xFFFF9A40 : 0xFF8A5A30, hot = running ? 0xFFFFD8A0 : 0xFFA07850;
        for (int rx0 : new int[]{cx - 9, cx + 6}) {                           // the rods
            rect(rx0 - (thick - 1) / 2, base - rh + 8, thick + 1, rh - 8, glow);
            rect(rx0 - (thick - 1) / 2, base - rh + 8, 1, rh - 8, hot);
        }
        rect(cx - 9, base - rh + 7, 16, thick, glow);                          // the bridge
        if (running) {
            for (int i = 0; i < 8; i++) {                                     // swirling gas
                double a = t * 0.3F + i * 0.8;
                int px = cx + (int) ((rw - 4) * Math.cos(a) * 0.8);
                int py = base - 6 - (int) ((rh - 10) * ((i * 0.13 + t * 0.02) % 1.0));
                rect(px, py, 1, 1, i % 2 == 0 ? (colA != 0 ? colA : 0xFFB8D8A0) : (colB != 0 ? colB : 0xFFD8E8F8));
            }
        }
        rect(x + 3, base + 3, 8, 2, colA != 0 ? colA : 0xFF3A4450);           // the gas inlets
        rect(x + w - 11, base + 3, 8, 2, colB != 0 ? colB : 0xFF3A4450);
    }

    private static int lighter(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 50), g = Math.min(255, ((c >> 8) & 255) + 50), b = Math.min(255, (c & 255) + 50);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            Gui.drawRect(x, y, x + w, y + h, c);
        }
    }
}
